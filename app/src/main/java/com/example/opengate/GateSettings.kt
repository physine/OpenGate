package com.example.opengate

import android.content.Context
import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gate_settings")

class GateSettings(private val context: Context) {
    companion object {
        private val GATE_NUMBER = stringPreferencesKey("gate_number")
        private val HOME_LATITUDE = doublePreferencesKey("home_latitude")
        private val HOME_LONGITUDE = doublePreferencesKey("home_longitude")
        private val RADIUS = floatPreferencesKey("radius")
        private val HAS_CALLED_GATE = booleanPreferencesKey("has_called_gate")
        
        const val MIN_RADIUS = 10f // meters
        const val MAX_RADIUS = 1000f // meters
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
            preferences[RADIUS] ?: MIN_RADIUS
        }

    val hasCalledGate: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_CALLED_GATE] ?: false
        }

    suspend fun setGateNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[GATE_NUMBER] = number
        }
    }

    suspend fun setHomeLocation(location: Location) {
        context.dataStore.edit { preferences ->
            preferences[HOME_LATITUDE] = location.latitude
            preferences[HOME_LONGITUDE] = location.longitude
        }
    }

    suspend fun setRadius(radius: Float) {
        val clampedRadius = radius.coerceIn(MIN_RADIUS, MAX_RADIUS)
        context.dataStore.edit { preferences ->
            preferences[RADIUS] = clampedRadius
        }
    }

    suspend fun setHasCalledGate(hasCalled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_CALLED_GATE] = hasCalled
        }
    }
} 