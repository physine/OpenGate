package com.example.opengate

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gate_settings")

class GateSettings(private val context: Context) {
    companion object {
        private const val TAG = "GateSettings"
        private val GATE_NUMBER = stringPreferencesKey("gate_number")
        private val HOME_LATITUDE = doublePreferencesKey("home_latitude")
        private val HOME_LONGITUDE = doublePreferencesKey("home_longitude")
        private val RADIUS = floatPreferencesKey("radius")
        private val HAS_CALLED_GATE = booleanPreferencesKey("has_called_gate")
        
        const val MIN_RADIUS = 10f // meters
        const val MAX_RADIUS = 1000f // meters
        private const val DEFAULT_RADIUS = 5f  // Changed from 10f to 5f
    }

    val gateNumber: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[GATE_NUMBER] ?: ""
        }

    val homeLocation: Flow<Location?> = context.dataStore.data
        .map { preferences ->
            val latitude = preferences[HOME_LATITUDE]
            val longitude = preferences[HOME_LONGITUDE]
            
            if (latitude != null && longitude != null) {
                Location("home").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                }
            } else {
                null
            }
        }

    val radius: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[RADIUS] ?: DEFAULT_RADIUS
        }

    val hasCalledGate: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_CALLED_GATE] ?: false
        }

    // Direct getter methods for non-Flow access
    fun getGateNumber(): String = runBlocking {
        Log.d(TAG, "Getting gate number")
        gateNumber.first()
    }

    fun getHomeLocation(): Location? = runBlocking {
        Log.d(TAG, "Getting home location")
        homeLocation.first()
    }

    fun getRadius(): Float = runBlocking {
        Log.d(TAG, "Getting radius")
        radius.first()
    }

    fun getHasCalledGate(): Boolean = runBlocking {
        Log.d(TAG, "Getting hasCalledGate state")
        hasCalledGate.first()
    }

    suspend fun setGateNumber(number: String) {
        Log.d(TAG, "Setting gate number to: $number")
        context.dataStore.edit { preferences ->
            preferences[GATE_NUMBER] = number
        }
    }

    suspend fun setHomeLocation(location: Location) {
        Log.d(TAG, "Setting home location to: lat=${location.latitude}, lon=${location.longitude}")
        context.dataStore.edit { preferences ->
            preferences[HOME_LATITUDE] = location.latitude
            preferences[HOME_LONGITUDE] = location.longitude
        }
    }

    suspend fun setRadius(radius: Float) {
        val clampedRadius = radius.coerceIn(MIN_RADIUS, MAX_RADIUS)
        Log.d(TAG, "Setting radius to: $clampedRadius")
        context.dataStore.edit { preferences ->
            preferences[RADIUS] = clampedRadius
        }
    }

    suspend fun setHasCalledGate(hasCalled: Boolean) {
        Log.d(TAG, "Setting hasCalledGate to: $hasCalled")
        context.dataStore.edit { preferences ->
            preferences[HAS_CALLED_GATE] = hasCalled
        }
    }

    suspend fun clearAllSettings() {
        Log.d(TAG, "Clearing all settings")
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun isNearGate(location: Location): Boolean {
        val homeLocation = getHomeLocation()
        if (homeLocation == null) {
            Log.d(TAG, "Home location is not set")
            return false
        }

        val distance = location.distanceTo(homeLocation)
        val currentRadius = getRadius()
        val isNear = distance <= currentRadius
        Log.d(TAG, "Distance to gate: $distance meters, radius: $currentRadius meters, isNear: $isNear")
        return isNear
    }
} 