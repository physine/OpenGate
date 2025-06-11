package com.example.opengate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

class GateCaller(
    private val context: Context,
    private val gateSettings: GateSettings
) {
    companion object {
        private const val TAG = "GateCaller"
    }

    fun callGate() {
        Log.d(TAG, "Attempting to call gate")
        val gateNumber = gateSettings.getGateNumber()
        Log.d(TAG, "Gate number: $gateNumber")
        
        if (gateNumber.isBlank()) {
            Log.w(TAG, "Gate number is blank")
            Toast.makeText(context, "Gate number not set", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$gateNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Log.d(TAG, "Created call intent: $intent")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (telecomManager.isInCall) {
                    Log.w(TAG, "Already in a call")
                    Toast.makeText(context, "Already in a call", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Starting call activity")
                context.startActivity(intent)
                Toast.makeText(context, "Calling gate...", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Call permission not granted")
                Toast.makeText(context, "Call permission not granted", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call gate", e)
            Toast.makeText(context, "Failed to call gate: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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