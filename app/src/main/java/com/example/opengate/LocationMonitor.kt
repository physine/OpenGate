package com.example.opengate

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class LocationMonitor(
    private val context: Context,
    private val gateSettings: GateSettings
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    companion object {
        private const val TAG = "LocationMonitor"
        private const val UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_INTERVAL = 2000L // 2 seconds
    }

    suspend fun isWithinRadius(location: Location): Boolean {
        val homeLocation = gateSettings.homeLocation.first() ?: return false
        val radius = gateSettings.radius.first()

        val distance = location.distanceTo(homeLocation)
        return distance <= radius
    }

    fun calculateDistanceToHome(location: Location): Float {
        return runBlocking {
            val homeLocation = gateSettings.homeLocation.first() ?: return@runBlocking Float.MAX_VALUE
            location.distanceTo(homeLocation)
        }
    }

    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        }
    }

    fun startLocationUpdates(callback: (Location) -> Unit) {
        Log.d(TAG, "Starting location updates")
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(UPDATE_INTERVAL)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Received location update: $location")
                        callback(location)
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

    fun stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates")
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                locationCallback = null
                Log.d(TAG, "Location updates stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location updates", e)
            }
        }
    }
} 