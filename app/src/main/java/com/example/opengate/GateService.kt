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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import android.os.Looper

class GateService : Service() {
    private lateinit var gateSettings: GateSettings
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var gateCaller: GateCaller
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var locationCallback: LocationCallback? = null
    internal var isInRadius = false
    private var lastLocation: Location? = null
    private var isInitialized = false
    private var lastCallTime = 0L
    private val MIN_CALL_INTERVAL = 60000L // 1 minute between calls

    companion object {
        private const val TAG = "GateService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "GateServiceChannel"
        private const val CHANNEL_NAME = "Gate Service"
        private const val MIN_DISTANCE_CHANGE = 5f // 5 meters
        const val ACTION_LOCATION_UPDATE = "LOCATION_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // Initialize dependencies
        gateSettings = GateSettings(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gateCaller = GateCaller(this, gateSettings)
        
        createNotificationChannel()
        acquireWakeLock()
        startLocationUpdates()
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors location for automatic gate opening"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        Log.d(TAG, "Acquiring wake lock")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenGate::WakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()  // Acquire indefinitely until explicitly released
        }
    }

    private fun startLocationUpdates() {
        Log.d(TAG, "Starting location updates")
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(5000)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Received location update: $location")
                        handleLocationUpdate(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while starting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: action=${intent?.action}")
        
        // Create and start foreground notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Handle location update if present (from activity)
        if (intent?.action == ACTION_LOCATION_UPDATE) {
            val location = intent.getParcelableExtra<Location>("location")
            Log.d(TAG, "Received location update from activity: $location")
            location?.let { handleLocationUpdate(it) }
        } else if (!isInitialized) {
            Log.d(TAG, "Initializing service state")
            serviceScope.launch {
                initializeServiceState()
            }
        }

        // If the service is killed, restart it
        return START_STICKY
    }

    private suspend fun initializeServiceState() {
        Log.d(TAG, "Initializing service state")
        try {
            // Get the current location
            val location = fusedLocationClient.lastLocation.await()
            Log.d(TAG, "Initial location: $location")
            if (location != null) {
                // Set the initial state without triggering a gate call
                lastLocation = location
                isInRadius = isWithinRadius(location)
                Log.d(TAG, "Initial state: isInRadius=$isInRadius")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service state", e)
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
        .setOngoing(true)
        .build()

    private suspend fun isWithinRadius(location: Location): Boolean {
        val homeLocation = gateSettings.homeLocation.first() ?: return false
        val radius = gateSettings.radius.first()
        val distance = location.distanceTo(homeLocation)
        return distance <= radius
    }

    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Handling location update: $location")
        serviceScope.launch {
            try {
                // Check if we have a valid gate number and location
                val gateNumber = gateSettings.getGateNumber()
                val gateLocation = gateSettings.getHomeLocation()
                Log.d(TAG, "Current gate number: $gateNumber, gate location: $gateLocation")
                
                if (gateNumber.isBlank() || gateLocation == null) {
                    Log.d(TAG, "Missing gate number or location, skipping update")
                    return@launch
                }

                // Check if we have moved enough to consider a new location update
                val hasMovedEnough = lastLocation?.let { lastLoc ->
                    val distance = location.distanceTo(lastLoc)
                    Log.d(TAG, "Distance from last location: $distance meters")
                    distance > MIN_DISTANCE_CHANGE
                } ?: true

                if (!hasMovedEnough) {
                    Log.d(TAG, "Not moved enough, skipping update")
                    return@launch
                }

                lastLocation = location
                val wasInRadius = isInRadius
                isInRadius = isWithinRadius(location)
                Log.d(TAG, "Location state: wasInRadius=$wasInRadius, isInRadius=$isInRadius")

                // Get the current hasCalledGate state
                val hasCalledGate = gateSettings.getHasCalledGate()
                val currentTime = System.currentTimeMillis()
                val timeSinceLastCall = currentTime - lastCallTime
                Log.d(TAG, "Call state: hasCalledGate=$hasCalledGate, timeSinceLastCall=$timeSinceLastCall ms")

                // Only call the gate if:
                // 1. We just entered the radius
                // 2. We haven't called the gate yet
                // 3. We have moved enough since the last update
                // 4. The service has been properly initialized
                // 5. Enough time has passed since the last call
                if (isInRadius && !wasInRadius && !hasCalledGate && isInitialized && 
                    timeSinceLastCall > MIN_CALL_INTERVAL) {
                    Log.d(TAG, "Calling gate")
                    gateCaller.callGate()
                    gateSettings.setHasCalledGate(true)
                    lastCallTime = currentTime
                } else if (!isInRadius) {
                    Log.d(TAG, "Outside radius, resetting call state")
                    // Reset the call state when we leave the radius
                    gateSettings.setHasCalledGate(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling location update", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
        
        // Stop location updates
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                locationCallback = null
                Log.d(TAG, "Location updates stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location updates", e)
            }
        }
        
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 