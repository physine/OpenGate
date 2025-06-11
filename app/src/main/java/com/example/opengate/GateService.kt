package com.example.opengate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class GateService : Service() {
    private lateinit var gateSettings: GateSettings
    private lateinit var locationMonitor: LocationMonitor
    private lateinit var gateCaller: GateCaller
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    internal var isInRadius = false
    private var lastLocation: Location? = null
    private var isInitialized = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "GateServiceChannel"
        private const val CHANNEL_NAME = "Gate Service"
        private const val MIN_DISTANCE_CHANGE = 5f // 5 meters
        const val ACTION_LOCATION_UPDATE = "LOCATION_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize dependencies
        gateSettings = GateSettings(this)
        locationMonitor = LocationMonitor(this, gateSettings)
        gateCaller = GateCaller(this, gateSettings)
        
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors location for automatic gate opening"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenGate::WakeLock"
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Handle location update if present
        if (intent?.action == ACTION_LOCATION_UPDATE) {
            val location = intent.getParcelableExtra<Location>("location")
            location?.let { handleLocationUpdate(it) }
        } else if (!isInitialized) {
            // If this is the first start and we don't have a location update,
            // initialize the service state
            serviceScope.launch {
                initializeServiceState()
            }
        }

        return START_STICKY
    }

    private suspend fun initializeServiceState() {
        // Get the current location
        val location = locationMonitor.getLastLocation()
        if (location != null) {
            // Set the initial state without triggering a gate call
            lastLocation = location
            isInRadius = locationMonitor.isWithinRadius(location)
            isInitialized = true
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Gate Service Active")
        .setContentText("Monitoring location for automatic gate opening")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun handleLocationUpdate(location: Location) {
        serviceScope.launch {
            // Check if we have a valid gate number
            val gateNumber = gateSettings.gateNumber.first()
            if (gateNumber.isBlank()) {
                return@launch
            }

            // Check if we have moved enough to consider a new location update
            val hasMovedEnough = lastLocation?.let { lastLoc ->
                location.distanceTo(lastLoc) > MIN_DISTANCE_CHANGE
            } ?: true

            if (!hasMovedEnough) {
                return@launch
            }

            lastLocation = location
            val wasInRadius = isInRadius
            isInRadius = locationMonitor.isWithinRadius(location)

            // Get the current hasCalledGate state
            val hasCalledGate = gateSettings.hasCalledGate.first()

            // Only call the gate if:
            // 1. We just entered the radius
            // 2. We haven't called the gate yet
            // 3. We have moved enough since the last update
            // 4. The service has been properly initialized
            if (isInRadius && !wasInRadius && !hasCalledGate && isInitialized) {
                gateCaller.callGate()
                gateSettings.setHasCalledGate(true)
            } else if (!isInRadius) {
                // Reset the call state when we leave the radius
                gateSettings.setHasCalledGate(false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 