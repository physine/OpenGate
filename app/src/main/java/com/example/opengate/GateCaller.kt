package com.example.opengate

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

class GateCaller(private val context: Context, private val gateSettings: GateSettings) {
    private val TAG = "GateCaller"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "gate_call_channel"
    private val CHANNEL_NAME = "Gate Calls"

    fun callGate() {
        Log.d(TAG, "Attempting to call gate")
        val gateNumber = gateSettings.getGateNumber()
        Log.d(TAG, "Gate number: $gateNumber")

        if (gateNumber.isBlank()) {
            Log.e(TAG, "Gate number is blank")
            showToast("Gate number not set")
            return
        }

        try {
            // Check if we have permission to make calls
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "CALL_PHONE permission not granted")
                showToast("Phone call permission not granted")
                return
            }

            // Check if already in a call (Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (telecomManager.isInCall) {
                    Log.d(TAG, "Already in a call, not making another call")
                    showToast("Already in a call")
                    return
                }
            }

            // Create the call intent
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$gateNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Try to place the call automatically when in background (Android O+)
            var autoCallPlaced = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecomManager.placeCall(Uri.parse("tel:$gateNumber"), null)
                    Log.d(TAG, "Call placed automatically via TelecomManager")
                    autoCallPlaced = true
                } catch (se: SecurityException) {
                    Log.w(TAG, "placeCall() security exception, falling back to startActivity", se)
                } catch (ex: Exception) {
                    Log.w(TAG, "placeCall() failed, falling back to startActivity", ex)
                }
            }

            if (!autoCallPlaced) {
                try {
                    // Attempt to start the call activity directly even if the app is backgrounded
                    context.startActivity(callIntent)
                    Log.d(TAG, "Call activity started from background (fallback)")
                } catch (ex: Exception) {
                    Log.e(TAG, "Automatic background call failed", ex)
                    showToast("Unable to place call automatically. Please open the app.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            showToast("Error making call: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming gate calls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName
            ) {
                return true
            }
        }
        return false
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    internal fun createCallIntent(phoneNumber: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Use TelecomManager for Android 10 and above
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (telecomManager.isInCall) {
                    // If already in a call, use the standard call intent
                    Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                } else {
                    // Use TelecomManager for a more reliable call
                    telecomManager.placeCall(
                        Uri.parse("tel:$phoneNumber"),
                        null
                    )
                    // Return a dummy intent since the call is handled by TelecomManager
                    Intent()
                }
            } catch (e: SecurityException) {
                // Fallback to standard call intent if we don't have permission
                Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
        } else {
            // For older Android versions, use the standard call intent
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}