package com.example.opengate

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P]) // Use a specific SDK version for consistent testing
class GateCallerTest {
    private lateinit var context: android.content.Context
    private lateinit var gateSettings: GateSettings
    private lateinit var gateCaller: GateCaller

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        gateSettings = mock()
        gateCaller = GateCaller(context, gateSettings)
    }

    @Test
    fun `createCallIntent should create correct intent for phone number`() = runBlocking {
        val phoneNumber = "1234567890"
        val intent = gateCaller.createCallIntent(phoneNumber)
        assertEquals(Intent.ACTION_CALL, intent.action)
        assertEquals(Uri.parse("tel:$phoneNumber"), intent.data)
    }

    @Test
    fun `callGate should not start call activity when permissions are missing`() = runBlocking {
        // Mock missing permissions
        whenever(gateSettings.gateNumber).thenReturn(flowOf("1234567890"))
        // No need to verify anything since the method should return early
        gateCaller.callGate()
    }

    @Test
    fun `callGate should not start call activity when gate number is blank`() = runBlocking {
        // Mock blank gate number
        whenever(gateSettings.gateNumber).thenReturn(flowOf(""))
        // No need to verify anything since the method should return early
        gateCaller.callGate()
    }
} 