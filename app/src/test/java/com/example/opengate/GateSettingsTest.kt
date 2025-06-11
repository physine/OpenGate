package com.example.opengate

import android.location.Location
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GateSettingsTest {
    private lateinit var context: android.content.Context
    private lateinit var gateSettings: GateSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        gateSettings = GateSettings(context)
    }

    @Test
    fun `set and get gate number`() = runBlocking {
        val gateNumber = "1234567890"
        gateSettings.setGateNumber(gateNumber)
        val loadedNumber = gateSettings.gateNumber.first()
        assertEquals(gateNumber, loadedNumber)
    }

    @Test
    fun `set and get home location`() = runBlocking {
        val latitude = 37.7749
        val longitude = -122.4194
        val location = Location("test").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        gateSettings.setHomeLocation(location)
        val loadedLocation = gateSettings.homeLocation.first()
        assertEquals(latitude, loadedLocation?.latitude ?: 0.0, 0.0001)
        assertEquals(longitude, loadedLocation?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun `set and get radius`() = runBlocking {
        val radius = 100f
        gateSettings.setRadius(radius)
        val loadedRadius = gateSettings.radius.first()
        assertEquals(radius, loadedRadius, 0.0001f)
    }

    @Test
    fun `radius should be clamped between min and max`() = runBlocking {
        // Test below minimum
        gateSettings.setRadius(GateSettings.MIN_RADIUS - 10f)
        assertEquals(GateSettings.MIN_RADIUS, gateSettings.radius.first(), 0.0001f)

        // Test above maximum
        gateSettings.setRadius(GateSettings.MAX_RADIUS + 10f)
        assertEquals(GateSettings.MAX_RADIUS, gateSettings.radius.first(), 0.0001f)

        // Test within range
        val validRadius = (GateSettings.MIN_RADIUS + GateSettings.MAX_RADIUS) / 2
        gateSettings.setRadius(validRadius)
        assertEquals(validRadius, gateSettings.radius.first(), 0.0001f)
    }
} 