package com.example.front_end

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import java.lang.reflect.InvocationTargetException

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Color

class TouchInjectionService : Service() {
    private val TAG = "TouchInjectionService"
    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Touch Injection Service created")
        
        // Register a broadcast receiver to handle gesture simulation requests
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.front_end.SIMULATE_GESTURE") {
                    val gestureType = intent.getStringExtra("gestureType") ?: "scroll"
                    
                    if (gestureType == "scroll") {
                        val direction = intent.getStringExtra("direction") ?: "up"
                        Log.d(TAG, "Received request to simulate $direction scroll")
                        
                        // Use different coordinates based on direction
                        val startX = 500f
                        val endX = 500f
                        var startY: Float
                        var endY: Float
                        
                        if (direction == "up") {
                            // For scroll up (content moves up), swipe from bottom to top
                            startY = 1000f
                            endY = 500f
                            Log.d(TAG, "Scroll UP - Will swipe from bottom to top: ($startX,$startY) to ($endX,$endY)")
                        } else {
                            // For scroll down (content moves down), swipe from top to bottom
                            startY = 500f
                            endY = 1000f
                            Log.d(TAG, "Scroll DOWN - Will swipe from top to bottom: ($startX,$startY) to ($endX,$endY)")
                        }
                        
                        val duration = intent.getLongExtra("duration", 300L)
                        simulateSwipe(startX, startY, endX, endY, duration)
                    } else if (gestureType == "tap") {
                        val x = intent.getFloatExtra("x", 500f)
                        val y = intent.getFloatExtra("y", 500f)
                        
                        Log.d(TAG, "Received request to simulate tap at ($x,$y)")
                        simulateTap(x, y)
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter("com.example.front_end.SIMULATE_GESTURE")
        registerReceiver(receiver, filter)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
        
        Log.d(TAG, "Touch Injection Service initialized")
    }
    
    private fun createOverlayView() {
        try {
            // Use a transparent overlay to capture and forward touches
            overlayView = FrameLayout(this).apply {
                // Make it completely transparent
                alpha = 0f
            }
            
            // We don't need to actually add the view to the window manager
            // This is just a placeholder for touch event listeners if needed
            
            Log.d(TAG, "Created overlay view for touch handling")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay: ${e.message}")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This service is not intended to be bound
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
        
        Log.d(TAG, "Touch Injection Service destroyed")
    }
    
    private fun simulateSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        try {
            Log.d(TAG, "Simulating swipe using simplified approach")
            
            // Using key events instead for newer Android versions
            // This is a workaround for cases where direct touch injection is restricted
            Log.d(TAG, "Using key event approach for scrolling")
            
            // Extract direction from parameters
            // For scroll up, endY < startY (swiping upward)
            // For scroll down, we need opposite direction
            val isScrollUp = startY > endY
            
            // Set proper coordinates based on direction
            var actualStartX = 500f
            var actualStartY: Float
            var actualEndX = 500f
            var actualEndY: Float

            if (isScrollUp) {
                // For scroll up, swipe from bottom to top
                actualStartY = 1000f
                actualEndY = 500f  // End higher (lower Y value)
                Log.d(TAG, "Scroll UP - Swiping from bottom to top: ($actualStartX,$actualStartY) to ($actualEndX,$actualEndY)")
            } else {
                // For scroll down, swipe from top to bottom
                actualStartY = 500f
                actualEndY = 1000f  // End lower (higher Y value)
                Log.d(TAG, "Scroll DOWN - Swiping from top to bottom: ($actualStartX,$actualStartY) to ($actualEndX,$actualEndY)")
            }
            
            handler.post {
                try {
                    // Determine direction and send appropriate key event
                    val keyCode = if (isScrollUp) {
                        KeyEvent.KEYCODE_DPAD_UP
                    } else {
                        KeyEvent.KEYCODE_DPAD_DOWN
                    }
                    
                    // Create and dispatch key events
                    val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                    
                    // For API 28+, we need to use an input manager or accessibility service
                    // So we'll send a broadcast for ScrollGestureService to handle
                    val intent = Intent("com.example.front_end.GESTURE_SCROLL")
                    intent.putExtra("startX", actualStartX)
                    intent.putExtra("startY", actualStartY)
                    intent.putExtra("endX", actualEndX)
                    intent.putExtra("endY", actualEndY)
                    intent.putExtra("duration", duration)
                    intent.putExtra("direction", if (isScrollUp) "up" else "down")
                    sendBroadcast(intent)
                    
                    Log.d(TAG, "Sent scroll broadcast to accessibility service for ${if (isScrollUp) "up" else "down"} scroll")
                } catch (e: Exception) {
                    Log.e(TAG, "Error simulating key events: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while simulating swipe: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating swipe: ${e.message}")
        }
    }
    
    private fun simulateTap(x: Float, y: Float) {
        try {
            Log.d(TAG, "Simulating tap at ($x, $y)")
            
            handler.post {
                try {
                    // For API 28+, we need to use an input manager or accessibility service
                    // So we'll send a broadcast for ScrollGestureService to handle
                    val intent = Intent("com.example.front_end.GESTURE_TAP")
                    intent.putExtra("x", x)
                    intent.putExtra("y", y)
                    sendBroadcast(intent)
                    
                    Log.d(TAG, "Sent tap broadcast to accessibility service")
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching tap: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating tap: ${e.message}")
        }
    }
} 