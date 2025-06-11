package com.example.opengate

import android.content.Context
import android.content.Intent
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
@SmallTest
class GateServiceTest {
    private lateinit var context: Context
    private lateinit var gateService: GateService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        gateService = GateService()
    }

    @Test
    fun shouldCallGateWhenEnteringRadius() = runBlocking {
        // Given
        val homeLocation = Location("home").apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        val gateSettings = GateSettings(context)
        gateSettings.setHomeLocation(homeLocation)
        gateSettings.setRadius(100f)

        val locationInside = Location("test").apply {
            latitude = 37.7749
            longitude = -122.4194
        }

        // When
        val intent = Intent(context, GateService::class.java).apply {
            action = GateService.ACTION_LOCATION_UPDATE
            putExtra("location", locationInside)
        }
        gateService.onStartCommand(intent, 0, 0)

        // Then
        // We can't directly verify the call since we're using the real GateCaller
        // Instead, we can verify that the location was processed
        assertTrue(gateService.isInRadius)
    }

    @Test
    fun shouldNotCallGateWhenOutsideRadius() = runBlocking {
        // Given
        val homeLocation = Location("home").apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        val gateSettings = GateSettings(context)
        gateSettings.setHomeLocation(homeLocation)
        gateSettings.setRadius(100f)

        val locationOutside = Location("test").apply {
            latitude = 37.7750 // Slightly north of home
            longitude = -122.4194
        }

        // When
        val intent = Intent(context, GateService::class.java).apply {
            action = GateService.ACTION_LOCATION_UPDATE
            putExtra("location", locationOutside)
        }
        gateService.onStartCommand(intent, 0, 0)

        // Then
        assertFalse(gateService.isInRadius)
    }

    @Test
    fun shouldNotCallGateWhenMissingSettings() = runBlocking {
        // Given
        val testLocation = Location("test").apply {
            latitude = 37.7749
            longitude = -122.4194
        }

        // When
        val intent = Intent(context, GateService::class.java).apply {
            action = GateService.ACTION_LOCATION_UPDATE
            putExtra("location", testLocation)
        }
        gateService.onStartCommand(intent, 0, 0)

        // Then
        // No call should be made since there's no gate number set
        assertFalse(gateService.isInRadius)
    }
} 