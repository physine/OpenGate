package com.example.opengate

import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await

class LocationUpdates(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) {
    companion object {
        private const val UPDATE_INTERVAL = 30000L // 30 seconds
        private const val FASTEST_INTERVAL = 15000L // 15 seconds
        private const val SMALLEST_DISPLACEMENT = 10f // 10 meters
    }

    suspend fun startLocationUpdates(callback: LocationCallback) {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                UPDATE_INTERVAL
            )
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .setMinUpdateDistanceMeters(SMALLEST_DISPLACEMENT)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                null
            ).await()
        } catch (e: SecurityException) {
            // Handle permission not granted
        }
    }

    fun stopLocationUpdates(callback: LocationCallback) {
        fusedLocationClient.removeLocationUpdates(callback)
    }

    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        }
    }
} 