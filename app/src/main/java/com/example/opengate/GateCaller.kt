package com.example.opengate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

class GateCaller(
    private val context: Context,
    private val gateSettings: GateSettings
) {
    suspend fun callGate() {
        // Check for required permissions
        if (!hasRequiredPermissions()) {
            return
        }

        val gateNumber = gateSettings.gateNumber.first()
        if (gateNumber.isNotBlank()) {
            val intent = createCallIntent(gateNumber)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
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