package com.example.opengate

import org.junit.Test
import org.junit.Assert.*
import android.util.Log

class DebugTest {
    @Test
    fun testEnvironment() {
        Log.d("DebugTest", "Starting test environment check")
        
        // Check JVM version
        val jvmVersion = System.getProperty("java.version")
        Log.d("DebugTest", "JVM Version: $jvmVersion")
        
        // Check Android SDK version
        val sdkVersion = android.os.Build.VERSION.SDK_INT
        Log.d("DebugTest", "Android SDK Version: $sdkVersion")
        
        // Check if we can access Android classes
        try {
            val context = android.content.Context::class.java
            Log.d("DebugTest", "Can access Android Context class: ${context != null}")
        } catch (e: Exception) {
            Log.e("DebugTest", "Error accessing Android classes", e)
        }
        
        // Basic assertion to ensure test runs
        assertEquals(4, 2 + 2)
        
        Log.d("DebugTest", "Test environment check completed")
    }
} 