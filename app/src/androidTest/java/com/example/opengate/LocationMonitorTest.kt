package com.example.opengate

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LocationMonitorTest {
    private lateinit var context: Context
    private lateinit var gateSettings: GateSettings
    private lateinit var locationMonitor: LocationMonitor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        gateSettings = GateSettings(context)
        locationMonitor = LocationMonitor(context, gateSettings)
    }

    @Test
    fun shouldDetectEntryIntoRadius() = runBlocking {
        // Given
        val homeLocation = Location("home").apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        gateSettings.setHomeLocation(homeLocation)
        gateSettings.setRadius(100f) // 100 meters radius

        // When
        val locationOutside = Location("test").apply {
            latitude = 37.7750 // Slightly north of home
            longitude = -122.4194
        }
        val outsideResult = locationMonitor.isWithinRadius(locationOutside)

        val locationInside = Location("test").apply {
            latitude = 37.7749
            longitude = -122.4194 // Same as home
        }
        val insideResult = locationMonitor.isWithinRadius(locationInside)

        // Then
        assertFalse("Location should be outside radius", outsideResult)
        assertTrue("Location should be inside radius", insideResult)
    }

    @Test
    fun shouldHandleMissingHomeLocation() = runBlocking {
        // Given
        val testLocation = Location("test").apply {
            latitude = 37.7749
            longitude = -122.4194
        }

        // When
        val result = locationMonitor.isWithinRadius(testLocation)

        // Then
        assertFalse("Should return false when home location is not set", result)
    }
} 