package com.example.front_end

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver for handling device boot completed events.
 * This allows our service to restart on device reboot if it was running before.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    private val TAG = "BootCompletedReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device boot completed, checking if service should be started")
            
            // Start our foreground service
            try {
                val serviceIntent = Intent(context, CameraBackgroundService::class.java)
                serviceIntent.putExtra("targetPackage", "com.instagram.android")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "Starting foreground service after boot (Android 8.0+)")
                    context.startForegroundService(serviceIntent)
                } else {
                    Log.d(TAG, "Starting service after boot (pre-Android 8.0)")
                    context.startService(serviceIntent)
                }
                
                Log.d(TAG, "Service started after boot completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service after boot: ${e.message}")
            }
        }
    }
} 