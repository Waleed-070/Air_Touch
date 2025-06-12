package com.example.front_end

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class ScrollGestureService : AccessibilityService() {
    private val TAG = "ScrollGestureService"
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    
    // Create our own GestureResultCallback class
    @RequiresApi(Build.VERSION_CODES.N)
    private open inner class GestureResultCallback : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            super.onCompleted(gestureDescription)
            Log.d(TAG, "Gesture completed successfully")
        }
        
        override fun onCancelled(gestureDescription: GestureDescription) {
            super.onCancelled(gestureDescription)
            Log.e(TAG, "Gesture cancelled")
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        
        // Register receiver for gesture scroll broadcasts
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.front_end.GESTURE_SCROLL") {
                    // Get coordinates from intent
                    val startX = intent.getFloatExtra("startX", 500f)
                    val startY = intent.getFloatExtra("startY", 1000f)
                    val endX = intent.getFloatExtra("endX", 500f)
                    val endY = intent.getFloatExtra("endY", 500f)
                    val duration = intent.getLongExtra("duration", 300L)
                    val direction = intent.getStringExtra("direction") ?: "up"
                    
                    Log.d(TAG, "Received gesture scroll broadcast: direction=$direction, ($startX,$startY) to ($endX,$endY)")
                    
                    // Determine if this is a horizontal or vertical gesture
                    val isHorizontal = Math.abs(endX - startX) > Math.abs(endY - startY)
                    
                    if (isHorizontal) {
                        // Horizontal swipe (left/right)
                        Log.d(TAG, "Performing HORIZONTAL swipe: $direction")
                        performHorizontalSwipe(startX, startY, endX, endY, duration)
                    } else {
                        // Vertical scroll (up/down)
                        // Perform appropriate scroll based on direction
                        if (direction == "up") {
                            // Scroll up - swipe from bottom to top
                            performScroll(500f, 1000f, 500f, 500f, duration)
                            Log.d(TAG, "Performing UP scroll gesture (bottom to top)")
                        } else {
                            // Scroll down - swipe from top to bottom
                            performScroll(500f, 500f, 500f, 1000f, duration)
                            Log.d(TAG, "Performing DOWN scroll gesture (top to bottom)")
                        }
                    }
                } else if (intent?.action == "com.example.front_end.PERFORM_GLOBAL_ACTION") {
                    val action = intent.getStringExtra("action") ?: ""
                    val direction = intent.getStringExtra("direction") ?: "down"
                    
                    if (action == "scroll") {
                        Log.d(TAG, "Received global action broadcast: scroll $direction")
                        performGlobalScrollAction(direction)
                    } else if (action == "home") {
                        Log.d(TAG, "Received global action broadcast: press home button")
                        performHomeButtonPress()
                    } else if (action == "swipe") {
                        Log.d(TAG, "Received global action broadcast: swipe $direction")
                        
                        // Determine coordinates based on direction
                        if (direction == "left") {
                            // For swipe left, start on right side and move to left
                            performHorizontalSwipe(900f, 600f, 300f, 600f, 300L)
                        } else {
                            // For swipe right, start on left side and move to right
                            performHorizontalSwipe(300f, 600f, 900f, 600f, 300L)
                        }
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter().apply {
            addAction("com.example.front_end.GESTURE_SCROLL")
            addAction("com.example.front_end.PERFORM_GLOBAL_ACTION")
        }
        registerReceiver(receiver, filter)
        
        Log.d(TAG, "Broadcast receiver registered for gesture events")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for our purposes, but required by the AccessibilityService
    }
    
    override fun onInterrupt() {
        // Not needed for our purposes, but required by the AccessibilityService
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister the receiver
        if (receiver != null) {
            try {
                unregisterReceiver(receiver)
                Log.d(TAG, "Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }
    
    private fun performGlobalScrollAction(direction: String) {
        try {
            // For scroll DOWN (content moves down), use GLOBAL_ACTION_SCROLL_BACKWARD (8192)
            // For scroll UP (content moves up), use GLOBAL_ACTION_SCROLL_FORWARD (4098)
            val scrollAction = when (direction.lowercase()) {
                "up" -> 4098 // GLOBAL_ACTION_SCROLL_FORWARD - Content moves UP
                "down" -> 8192 // GLOBAL_ACTION_SCROLL_BACKWARD - Content moves DOWN
                else -> 4098 // Default to scroll up
            }
            
            val directionName = if (direction.lowercase() == "up") "UP (forward)" else "DOWN (backward)"
            Log.d(TAG, "Performing global scroll action: $directionName, action code: $scrollAction")
            val success = performGlobalAction(scrollAction)
            Log.d(TAG, "Global scroll action performed: $success (direction: $direction)")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing global scroll: ${e.message}")
        }
    }
    
    private fun performHomeButtonPress() {
        try {
            Log.d(TAG, "Performing home button press via accessibility service")
            val success = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "Home button press performed: $success")
            
            // After successful home button press, send a broadcast to restart camera
            if (success) {
                Log.d(TAG, "Scheduling camera restart broadcast after home button press")
                
                // Use a handler to delay the broadcast to allow app state to settle
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Send broadcast intent to restart camera
                        val restartIntent = Intent("com.example.front_end.RESTART_CAMERA")
                        restartIntent.putExtra("source", "home_button_press")
                        restartIntent.putExtra("timestamp", System.currentTimeMillis())
                        restartIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        sendBroadcast(restartIntent)
                        Log.d(TAG, "Camera restart broadcast sent successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending camera restart broadcast: ${e.message}")
                    }
                }, 500) // 500ms delay
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing home button press: ${e.message}")
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        try {
            // Create a path that starts at (startX, startY) and moves to (endX, endY)
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            
            // Create a stroke with the path
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(stroke)
            
            // Dispatch the gesture
            val callback = GestureResultCallback()
            
            val result = dispatchGesture(gestureBuilder.build(), callback, null)
            Log.d(TAG, "Dispatch gesture result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing scroll gesture: ${e.message}")
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun performHorizontalSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        try {
            Log.d(TAG, "Performing horizontal swipe from ($startX,$startY) to ($endX,$endY)")
            
            // Create a path for the swipe
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            
            // Create a stroke with the path
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(stroke)
            
            // Dispatch the gesture
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Horizontal swipe gesture completed successfully")
                    
                    // Send broadcast to confirm completion
                    val resultIntent = Intent("com.example.front_end.GESTURE_COMPLETED")
                    resultIntent.putExtra("type", "swipe")
                    resultIntent.putExtra("success", true)
                    sendBroadcast(resultIntent)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Horizontal swipe gesture cancelled")
                    
                    // Send broadcast to confirm failure
                    val resultIntent = Intent("com.example.front_end.GESTURE_COMPLETED")
                    resultIntent.putExtra("type", "swipe")
                    resultIntent.putExtra("success", false)
                    resultIntent.putExtra("reason", "cancelled")
                    sendBroadcast(resultIntent)
                }
            }
            
            val result = dispatchGesture(gestureBuilder.build(), callback, null)
            Log.d(TAG, "Horizontal swipe dispatch result: $result")
            
            if (!result) {
                Log.e(TAG, "Failed to dispatch horizontal swipe gesture")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing horizontal swipe gesture: ${e.message}")
        }
    }
} 