package com.example.opengate

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class LocationMonitor(
    private val context: Context,
    private val gateSettings: GateSettings
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

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
} 