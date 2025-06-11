package com.example.opengate

import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LocationUpdatesTest {
    private lateinit var context: android.content.Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationUpdates: LocationUpdates
    private lateinit var locationCallback: LocationCallback

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        fusedLocationClient = mock()
        locationCallback = mock()
        locationUpdates = LocationUpdates(context, fusedLocationClient)
    }

    @Test
    fun `startLocationUpdates should request location updates`() = runBlocking {
        val task: Task<Void> = mock()
        whenever(fusedLocationClient.requestLocationUpdates(
            any<LocationRequest>(),
            locationCallback,
            null
        )).thenReturn(task)
        whenever(task.await()).thenReturn(null)
        
        locationUpdates.startLocationUpdates(locationCallback)
        
        verify(fusedLocationClient).requestLocationUpdates(
            any<LocationRequest>(),
            locationCallback,
            null
        )
    }

    @Test
    fun `stopLocationUpdates should remove location updates`() = runBlocking {
        locationUpdates.stopLocationUpdates(locationCallback)
        verify(fusedLocationClient).removeLocationUpdates(locationCallback)
    }

    @Test
    fun `getLastLocation should return last known location`() = runBlocking {
        val mockLocation = mock<Location>()
        val task: Task<Location> = mock()
        whenever(fusedLocationClient.lastLocation).thenReturn(task)
        whenever(task.await()).thenReturn(mockLocation)
        
        val result = locationUpdates.getLastLocation()
        
        verify(fusedLocationClient).lastLocation
        assertEquals(mockLocation, result)
    }
} 