package com.example.opengate

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class GateMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var gateCaller: GateCaller
    private lateinit var gateSettings: GateSettings
    private var wakeLock: PowerManager.WakeLock? = null
    private var isInRadius = false
    private var lastLocation: Location? = null
    private var isInitialized = false
    private var lastCallTime = 0L
    private val MIN_CALL_INTERVAL = 60000L // 1 minute between calls

    companion object {
        private const val TAG = "GateMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "GateMonitorChannel"
        private const val CHANNEL_NAME = "Gate Monitor"
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val LOCATION_UPDATE_FASTEST_INTERVAL = 5000L // 5 seconds
        private const val MIN_DISTANCE_CHANGE = 5f // 5 meters
        const val ACTION_LOCATION_UPDATE = "LOCATION_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gateSettings = GateSettings(this)
        gateCaller = GateCaller(this, gateSettings)
        createLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        
        // Create and start foreground notification first
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
                try {
                    // Add a small delay to ensure permissions are fully processed
                    delay(1000)
                    
                    // Check permissions before proceeding
                    if (checkLocationPermissions()) {
                        acquireWakeLock()
                        initializeServiceState()
                        // Only start location updates after initialization
                        startLocationUpdates()
                    } else {
                        Log.e(TAG, "Missing required permissions")
                        stopSelf()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during service initialization", e)
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun checkLocationPermissions(): Boolean {
        val hasForegroundLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        Log.d(TAG, "Permission check - Foreground: $hasForegroundLocation, Background: $hasBackgroundLocation")
        return hasForegroundLocation && hasBackgroundLocation
    }

    private fun acquireWakeLock() {
        Log.d(TAG, "Acquiring wake lock")
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenGate::WakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()  // Acquire indefinitely until explicitly released
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors location for gate calling"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gate Monitor Active")
            .setContentText("Monitoring location for gate calling")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            // Double check permissions before starting updates
            if (!checkLocationPermissions()) {
                Log.e(TAG, "Cannot start location updates: missing permissions")
                stopSelf()
                return
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_UPDATE_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while requesting location updates", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            stopSelf()
        }
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
            } else {
                Log.w(TAG, "No initial location available")
                // Don't stop the service, just wait for the first location update
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during initialization", e)
            throw e // Propagate to stop the service
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service state", e)
            throw e // Propagate to stop the service
        }
    }

    private suspend fun isWithinRadius(location: Location): Boolean {
        val homeLocation = gateSettings.getHomeLocation() ?: return false
        val radius = gateSettings.getRadius()
        val distance = location.distanceTo(homeLocation)
        return distance <= radius
    }

    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Handling location update: $location")
        serviceScope.launch {
            try {
                // Check permissions before processing update
                if (!checkLocationPermissions()) {
                    Log.e(TAG, "Lost permissions during location update")
                    stopSelf()
                    return@launch
                }

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
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception handling location update", e)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling location update", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 