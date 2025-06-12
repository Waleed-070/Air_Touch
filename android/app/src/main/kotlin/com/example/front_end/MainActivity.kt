package com.example.front_end

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.PowerManager
import io.flutter.plugin.common.BinaryMessenger
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.provider.Settings
import android.content.DialogInterface
import android.media.AudioManager
import android.view.KeyEvent
import android.os.SystemClock
import io.flutter.embedding.engine.plugins.FlutterPlugin

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.app/instagram"
    private val BACKGROUND_CHANNEL = "com.example.app/background"
    private val SYSTEM_CHANNEL = "com.example.app/system"
    private val MEDIA_CONTROL_CHANNEL = "com.example.app/media_control"
    private val TAG = "InstagramLauncher"
    private val FOREGROUND_SERVICE_ID = 1001
    private val NOTIFICATION_CHANNEL_ID = "air_touch_background"
    private var backgroundServiceRunning = false
    private val LAUNCH_ACTION = "com.example.front_end.LAUNCH_INSTAGRAM"
    private var launchReceiver: BroadcastReceiver? = null
    private var cameraRestartReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register our own broadcast receiver
        registerLaunchReceiver()
        
        // Register camera restart receiver
        registerCameraRestartReceiver()
        
        // Handle notification intent if app was launched from notification
        handleNotificationIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == "LAUNCH_INSTAGRAM") {
            val packageName = intent.getStringExtra("targetPackage") ?: "com.instagram.android"
            Log.d(TAG, "Activity launched from notification, launching: $packageName")
            launchApp(packageName)
        }
    }
    
    private fun createLaunchPendingIntent(packageName: String): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = "LAUNCH_INSTAGRAM"
            putExtra("targetPackage", packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun showLaunchNotification(packageName: String) {
        Log.d(TAG, "Showing high-priority launch notification for $packageName")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = "gesture_launch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Gesture Launch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for gesture detection"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    
        // Create a full-screen intent for maximum visibility
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = "LAUNCH_INSTAGRAM"
            putExtra("targetPackage", packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    
        // Create direct launch intent as action button
        val directLaunchIntent = Intent().apply {
            component = ComponentName(packageName, "com.instagram.android.activity.MainTabActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val directLaunchPendingIntent = PendingIntent.getActivity(
            this,
            2,
            directLaunchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    
        // Build notification with multiple launch options
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gesture Detected!")
            .setContentText("Tap to open Instagram")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(createLaunchPendingIntent(packageName))
            .addAction(android.R.drawable.ic_menu_camera, "Open Instagram", directLaunchPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    
        // Use a unique ID for this notification
        notificationManager.notify(1002, notification)
        Log.d(TAG, "High priority notification shown to launch Instagram")
        
        // Also try direct launch as backup
        try {
            launchInstagramFromBackground(packageName, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error during backup launch attempt: ${e.message}")
        }
    }
    
    private fun registerLaunchReceiver() {
        try {
            // Create a broadcast receiver for launch requests
            launchReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == LAUNCH_ACTION) {
                        Log.d(TAG, "ACTIVITY: Received broadcast to launch Instagram")
                        launchApp("com.instagram.android")
                    }
                }
            }
            
            // Register the receiver
            val filter = IntentFilter(LAUNCH_ACTION)
            registerReceiver(launchReceiver, filter)
            Log.d(TAG, "ACTIVITY: Registered launch receiver")
        } catch (e: Exception) {
            Log.e(TAG, "ACTIVITY: Failed to register launch receiver: ${e.message}")
        }
    }
    
    private fun registerCameraRestartReceiver() {
        try {
            // Create a broadcast receiver for camera restart requests
            cameraRestartReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.example.front_end.RESTART_CAMERA") {
                        Log.d(TAG, "ACTIVITY: Received broadcast to restart camera")
                        val source = intent.getStringExtra("source") ?: "unknown"
                        Log.d(TAG, "Camera restart requested from source: $source")
                        
                        // Force app to foreground
                        bringAppToForeground()
                        
                        // Wait a short while, then send message to Flutter to restart camera
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                // Send message through system channel to restart camera
                                val systemChannel = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, SYSTEM_CHANNEL)
                                systemChannel.invokeMethod("restartCamera", mapOf(
                                    "source" to source,
                                    "timestamp" to System.currentTimeMillis()
                                ))
                                Log.d(TAG, "Camera restart method invoked on Flutter side")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking camera restart: ${e.message}")
                            }
                        }, 300) // 300ms delay
                    }
                }
            }
            
            // Register the receiver
            val filter = IntentFilter("com.example.front_end.RESTART_CAMERA")
            registerReceiver(cameraRestartReceiver, filter)
            Log.d(TAG, "ACTIVITY: Registered camera restart receiver")
        } catch (e: Exception) {
            Log.e(TAG, "ACTIVITY: Failed to register camera restart receiver: ${e.message}")
        }
    }
    
    private fun bringAppToForeground() {
        try {
            Log.d(TAG, "Attempting to bring app to foreground")
            
            // Create an intent to launch the main activity
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            
            // Start the activity to bring it to foreground
            startActivity(intent)
            Log.d(TAG, "App brought to foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing app to foreground: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister the receiver
        if (launchReceiver != null) {
            try {
                unregisterReceiver(launchReceiver)
                Log.d(TAG, "ACTIVITY: Unregistered launch receiver")
            } catch (e: Exception) {
                Log.e(TAG, "ACTIVITY: Error unregistering receiver: ${e.message}")
            }
        }
        
        // Unregister camera restart receiver
        if (cameraRestartReceiver != null) {
            try {
                unregisterReceiver(cameraRestartReceiver)
                Log.d(TAG, "ACTIVITY: Unregistered camera restart receiver")
            } catch (e: Exception) {
                Log.e(TAG, "ACTIVITY: Error unregistering camera restart receiver: ${e.message}")
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "launchInstagram") {
                val packageName = call.argument<String>("packageName") ?: "com.instagram.android"
                Log.d(TAG, "Flutter requested to launch Instagram with package: $packageName")
                val success = launchApp(packageName)
                result.success(success)
            } else {
                result.notImplemented()
            }
        }
        
        // Add call detection method channel
        val callChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.example.app/call_detection")
        registerCallDetectionPlugin(flutterEngine.dartExecutor.binaryMessenger, callChannel)
        
        // Add a new method channel for background processing
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BACKGROUND_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startBackgroundService" -> {
                    Log.d(TAG, "Starting foreground service for background camera")
                    val packageName = call.argument<String>("targetPackage") ?: "com.instagram.android"
                    val highPriority = call.argument<Boolean>("highPriority") ?: false
                    val keepAlive = call.argument<Boolean>("keepAlive") ?: false
                    
                    if (highPriority) {
                        Log.d(TAG, "Starting service with HIGH PRIORITY flag")
                    }
                    
                    startForegroundService(packageName, highPriority)
                    result.success(true)
                }
                "stopBackgroundService" -> {
                    Log.d(TAG, "Stopping foreground service")
                    stopForegroundService()
                    result.success(true)
                }
                "launchInstagramFromBackground" -> {
                    val packageName = call.argument<String>("packageName") ?: "com.instagram.android"
                    val forceForeground = call.argument<Boolean>("forceForeground") ?: false
                    val isFromGesture = call.argument<Boolean>("isFromGesture") ?: false
                    
                    if (!isFromGesture) {
                        Log.d(TAG, "Background launch request IGNORED - not from a genuine gesture")
                        result.success(false)
                        return@setMethodCallHandler
                    }
                    
                    val success = launchInstagramFromBackground(packageName, forceForeground)
                    result.success(success)
                }
                "highPriorityLaunch" -> {
                    Log.d(TAG, "⚡⚡ HIGH PRIORITY APP LAUNCH REQUESTED ⚡⚡")
                    try {
                        val packageName = call.argument<String>("packageName") ?: "com.instagram.android"
                        val activityName = call.argument<String>("activityName") ?: "com.instagram.android.activity.MainTabActivity"
                        val forceForeground = call.argument<Boolean>("forceForeground") ?: true
                        val wakeLockDuration = call.argument<Int>("wakeLockDuration") ?: 10000
                        val isFromGesture = call.argument<Boolean>("isFromGesture") ?: false
                        
                        // Only proceed if this is from a gesture or we're explicitly overriding the check
                        if (!isFromGesture) {
                            Log.d(TAG, "⚡⚡ HIGH PRIORITY LAUNCH REJECTED - Not triggered by a genuine gesture")
                            result.success(false)
                            return@setMethodCallHandler
                        }
                        
                        // Acquire a full wake lock to force screen on
                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        val wakeLock = powerManager.newWakeLock(
                            PowerManager.FULL_WAKE_LOCK or 
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                            PowerManager.ON_AFTER_RELEASE, "AirTouch:LaunchWakeLock")
                        
                        wakeLock.acquire(wakeLockDuration.toLong())
                        Log.d(TAG, "⚡⚡ FULL WAKE LOCK ACQUIRED FOR HIGH PRIORITY LAUNCH")
                        
                        // Launch Instagram using multiple mechanisms
                        
                        // 1. Direct component name approach
                        try {
                            val intent = Intent()
                            intent.component = ComponentName(packageName, activityName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            startActivity(intent)
                            Log.d(TAG, "⚡⚡ Direct component intent launched successfully")
                            result.success(true)
                            
                            // Release wake lock after success
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (wakeLock.isHeld) {
                                    wakeLock.release()
                                    Log.d(TAG, "⚡⚡ Wake lock released after successful launch")
                                }
                            }, 3000)
                            return@setMethodCallHandler
                        } catch (e: Exception) {
                            Log.e(TAG, "⚡⚡ Direct component launch failed: ${e.message}")
                        }
                        
                        // Release wake lock on failure
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                            Log.d(TAG, "⚡⚡ Wake lock released after launch failure")
                        }
                        
                        result.success(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "⚡⚡ High priority launch error: ${e.message}")
                        result.success(false)
                    }
                }
                "sendGestureToBackgroundWithPower" -> {
                    try {
                        val gesture = call.argument<String>("gesture") ?: ""
                        val confidence = call.argument<Double>("confidence") ?: 0.0
                        val priorityValue = call.argument<String>("priority") ?: "normal"
                        val isHighPriority = priorityValue == "high"
                        
                        Log.d(TAG, "Sending gesture to background service: $gesture with confidence $confidence")
                        
                        // Only proceed if this is a genuine "open_app" gesture with sufficient confidence
                        if (gesture != "open_app" || confidence < 0.70) {
                            Log.d(TAG, "Gesture rejected for app launch: wrong gesture or low confidence")
                            result.success(false)
                            return@setMethodCallHandler
                        }
                        
                        // Start the service with the gesture detection info
                        val intent = Intent(this, CameraBackgroundService::class.java).apply {
                            putExtra("gestureDetection", true)
                            putExtra("gesture", gesture)
                            putExtra("confidence", confidence)
                            putExtra("highPriority", isHighPriority)
                            putExtra("genuineGestureDetected", true)  // Mark as a genuine detection
                        }
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        
                        Log.d(TAG, "Gesture sent to background service")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending gesture to background: ${e.message}")
                        result.success(false)
                    }
                }
                "setAppInBackground" -> {
                    try {
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        val enableDetection = call.argument<Boolean>("enableDetection") ?: false
                        val highPriority = call.argument<Boolean>("highPriority") ?: false
                        
                        Log.d(TAG, "App state update: inBackground=$inBackground, enableDetection=$enableDetection, highPriority=$highPriority")
                        
                        // Tell the service about the app state change
                        val serviceIntent = Intent(this, CameraBackgroundService::class.java)
                        serviceIntent.putExtra("appStateUpdate", true)
                        serviceIntent.putExtra("inBackground", inBackground)
                        serviceIntent.putExtra("enableDetection", enableDetection)
                        serviceIntent.putExtra("highPriority", highPriority)
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating app state: ${e.message}")
                        result.error("APP_STATE_ERROR", "Could not update app state", e.message)
                    }
                }
                "sendBroadcastToAll" -> {
                    try {
                        val action = call.argument<String>("action") ?: ""
                        val flags = call.argument<List<String>>("flags") ?: listOf()
                        
                        Log.d(TAG, "Sending broadcast to all: action=$action, flags=$flags")
                        
                        val intent = Intent(action)
                        
                        // Add requested flags
                        if (flags.contains("FLAG_INCLUDE_STOPPED_PACKAGES")) {
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                        if (flags.contains("FLAG_ACTIVITY_NEW_TASK")) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        // Send broadcast
                        sendBroadcast(intent)
                        Log.d(TAG, "Broadcast sent to all receivers")
                        
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error broadcasting: ${e.message}")
                        result.error("BROADCAST_ERROR", "Could not send broadcast", e.message)
                    }
                }
                "mediaPlayPause" -> {
                    try {
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        Log.d(TAG, "Background channel: Media play/pause requested (inBackground=$inBackground)")
                        
                        // Use the enhanced media key event helper
                        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        
                        Log.d(TAG, "Media play/pause result: $success")
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error with media control: ${e.message}")
                        e.printStackTrace()
                        result.error("MEDIA_CONTROL_ERROR", "Failed to control media", e.message)
                    }
                }
                "mediaSkipNext" -> {
                    try {
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        Log.d(TAG, "Background channel: Media skip next requested (inBackground=$inBackground)")
                        
                        // Use the enhanced media key event helper
                        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                        
                        Log.d(TAG, "Media next result: $success")
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error with next track control: ${e.message}")
                        e.printStackTrace()
                        result.error("MEDIA_CONTROL_ERROR", "Failed to skip to next track", e.message)
                    }
                }
                "mediaSkipPrevious" -> {
                    try {
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        Log.d(TAG, "Background channel: Media skip previous requested (inBackground=$inBackground)")
                        
                        // Try multiple approaches to maximize compatibility
                        // 1. First try with our robust media key event handler
                        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        
                        // 2. If that didn't work, try with direct MediaControlPlugin approach
                        if (!success) {
                            Log.d(TAG, "Primary method failed, trying backup direct approach")
                            
                            // Create and initialize the plugin manually
                            val mediaPlugin = MediaControlPlugin()
                            
                            // Initialize the plugin with context
                            mediaPlugin.onAttachedToEngine(context, flutterEngine.dartExecutor.binaryMessenger)
                            
                            // Call the public method to skip to previous track
                            val backupSuccess = mediaPlugin.simulateMediaPreviousButtonPress()
                            
                            // Report success from either method
                            result.success(success || backupSuccess)
                        } else {
                            result.success(true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error with previous track control: ${e.message}")
                        e.printStackTrace()
                        result.error("MEDIA_CONTROL_ERROR", "Failed to skip to previous track", e.message)
                    }
                }
                "directMediaPrevious" -> {
                    try {
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        val forceDoublePress = call.argument<Boolean>("forceDoublePress") ?: false
                        val highPriority = call.argument<Boolean>("highPriority") ?: false
                        
                        Log.d(TAG, "Background channel: Direct media previous requested (inBackground=$inBackground, forceDoublePress=$forceDoublePress)")
                        
                        var success = false
                        
                        // Method 1: Using AudioManager with even stronger timing
                        try {
                            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            
                            if (forceDoublePress) {
                                // Double press method for better compatibility with many music apps
                                // First press
                                val now1 = SystemClock.uptimeMillis()
                                val eventDown1 = KeyEvent(now1, now1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                audioManager.dispatchMediaKeyEvent(eventDown1)
                                Thread.sleep(300)
                                
                                val eventUp1 = KeyEvent(now1, now1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                audioManager.dispatchMediaKeyEvent(eventUp1)
                                
                                // Delay between presses
                                Thread.sleep(400)
                                
                                // Second press
                                val now2 = SystemClock.uptimeMillis()
                                val eventDown2 = KeyEvent(now2, now2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                audioManager.dispatchMediaKeyEvent(eventDown2)
                                Thread.sleep(300)
                                
                                val eventUp2 = KeyEvent(now2, now2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                audioManager.dispatchMediaKeyEvent(eventUp2)
                                
                                Log.d(TAG, "Double-press method completed for media previous")
                            } else {
                                // Standard single press with long delay
                                val now = SystemClock.uptimeMillis()
                                val eventDown = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                val eventUp = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                                
                                // Send DOWN event
                                audioManager.dispatchMediaKeyEvent(eventDown)
                                
                                // Long press to help device recognize the key event
                                Thread.sleep(500)
                                
                                // Send UP event
                                audioManager.dispatchMediaKeyEvent(eventUp)
                                
                                Log.d(TAG, "Single long-press method completed for media previous")
                            }
                            success = true
                        } catch (e: Exception) {
                            Log.e(TAG, "AudioManager method failed: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        // Method 2: Legacy Intent-based method
                        try {
                            // Send media button intent first
                            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
                            sendOrderedBroadcast(intent, null)
                            
                            Thread.sleep(300)
                            
                            // Then send direct music service command
                            val musicIntent = Intent("com.android.music.musicservicecommand")
                            musicIntent.putExtra("command", "previous")
                            sendBroadcast(musicIntent)
                            
                            // Send UP event
                            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent)
                            sendOrderedBroadcast(upIntent, null)
                            
                            Log.d(TAG, "Intent-based method completed for media previous")
                            success = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Intent method failed: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        // Method 3: Service-based approach
                        try {
                            // Send the gesture to background service for redundancy
                            val serviceIntent = Intent(this, CameraBackgroundService::class.java)
                            serviceIntent.putExtra("gestureDetection", true)
                            serviceIntent.putExtra("gesture", "backward")
                            serviceIntent.putExtra("confidence", 0.95) // High confidence
                            serviceIntent.putExtra("highPriority", highPriority)
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                            
                            Log.d(TAG, "Service-based command sent for media previous")
                            success = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Service method failed: ${e.message}")
                            e.printStackTrace()
                        }
                        
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error with direct media previous control: ${e.message}")
                        e.printStackTrace()
                        result.error("MEDIA_CONTROL_ERROR", "Failed to execute direct previous command", e.message)
                    }
                }
                "sendGestureToService" -> {
                    val gesture = call.argument<String>("gesture") ?: ""
                    val confidence = call.argument<Double>("confidence") ?: 0.0
                    Log.d(TAG, "Sending gesture '$gesture' ($confidence) to background service")
                    
                    // Send to service via intent
                    try {
                        val serviceIntent = Intent(this, CameraBackgroundService::class.java)
                        serviceIntent.putExtra("gestureDetection", true)
                        serviceIntent.putExtra("gesture", gesture)
                        serviceIntent.putExtra("confidence", confidence)
                        
                        // Also send as broadcast for redundancy
                        val broadcastIntent = Intent("com.example.front_end.GESTURE_DETECTED")
                        broadcastIntent.putExtra("gesture", gesture)
                        broadcastIntent.putExtra("confidence", confidence)
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        
                        // Send the broadcast to any receivers
                        sendBroadcast(broadcastIntent)
                        
                        Log.d(TAG, "Gesture successfully sent to background service")
                        
                        // If it's an open_app gesture, also try to launch directly
                        if (gesture == "open_app" && confidence > 0.65) {
                            Log.d(TAG, "Direct gesture detection in activity - launching Instagram immediately")
                            launchInstagramFromBackground("com.instagram.android")
                        }
                        
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending gesture to service: ${e.message}")
                        result.error("SERVICE_ERROR", "Could not send gesture to service", e.message)
                    }
                }
                "requestIgnoreBatteryOptimizations" -> {
                    Log.d(TAG, "Request to ignore battery optimizations")
                    try {
                        val packageName = applicationContext.packageName
                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                                Log.d(TAG, "Requesting battery optimization exemption")
                                val intent = Intent()
                                intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                intent.data = Uri.parse("package:$packageName")
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } else {
                                Log.d(TAG, "Already ignoring battery optimizations")
                            }
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting battery optimization ignore: ${e.message}")
                        result.error("BATTERY_OPT_ERROR", "Failed to request battery optimization", e.message)
                    }
                }
                "showLaunchNotification" -> {
                    val packageName = call.argument<String>("packageName") ?: "com.instagram.android"
                    val isFromGesture = call.argument<Boolean>("isFromGesture") ?: false
                    
                    if (!isFromGesture) {
                        Log.d(TAG, "Notification request IGNORED - not from a genuine gesture")
                        result.success(false)
                        return@setMethodCallHandler
                    }
                    
                    showLaunchNotification(packageName)
                    result.success(true)
                }
                "simulateScroll" -> {
                    try {
                        val direction = call.argument<String>("direction") ?: "up"
                        val distance = call.argument<Double>("distance")?.toFloat() ?: 300f
                        val success = sendScrollBroadcast(direction, distance)
                        result.success(success)
                    } catch (e: Exception) {
                        logError("Error simulating scroll: ${e.message}")
                        result.error("ERROR", "Failed to simulate scroll", e.toString())
                    }
                }
                "simulateSwipe" -> {
                    try {
                        val direction = call.argument<String>("direction") ?: "left"
                        val distance = call.argument<Double>("distance")?.toFloat() ?: 300f
                        val fromGesture = call.argument<Boolean>("fromGesture") ?: true
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        
                        logInfo("Simulating horizontal swipe: direction=$direction, distance=$distance")
                        val success = sendHorizontalSwipeBroadcast(direction, distance)
                        result.success(success)
                    } catch (e: Exception) {
                        logError("Error simulating horizontal swipe: ${e.message}")
                        result.error("ERROR", "Failed to simulate horizontal swipe", e.toString())
                    }
                }
                "simulateTouchSequence" -> {
                    try {
                        val actions = call.argument<List<Map<String, Any>>>("actions") ?: emptyList()
                        val duration = call.argument<Int>("duration") ?: 200
                        val inBackground = call.argument<Boolean>("inBackground") ?: false
                        
                        logInfo("Simulating touch sequence with ${actions.size} actions, duration=$duration ms")
                        val success = simulateTouchSequence(actions, duration, inBackground)
                        result.success(success)
                    } catch (e: Exception) {
                        logError("Error simulating touch sequence: ${e.message}")
                        result.error("ERROR", "Failed to simulate touch sequence", e.toString())
                    }
                }
                "pressHomeButton" -> {
                    try {
                        val success = pressHomeButton()
                        result.success(success)
                    } catch (e: Exception) {
                        logError("Error pressing home button: ${e.message}")
                        result.error("ERROR", "Failed to press home button", e.toString())
                    }
                }
                "performAccessibilityAction" -> {
                    try {
                        val action = call.argument<String>("action") ?: "scrollUp"
                        val amount = call.argument<Double>("amount")?.toFloat() ?: 150f
                        val fromGesture = call.argument<Boolean>("fromGesture") ?: false
                        
                        logInfo("Performing accessibility action: $action, amount: $amount")
                        
                        // Try sending an accessibility action
                        val intent = Intent("com.example.front_end.ACCESSIBILITY_ACTION")
                        intent.putExtra("action", action)
                        intent.putExtra("amount", amount)
                        intent.putExtra("fromGesture", fromGesture)
                        context.sendBroadcast(intent)
                        
                        result.success(true)
                    } catch (e: Exception) {
                        logError("Error performing accessibility action: ${e.message}")
                        result.error("ERROR", "Failed to perform accessibility action", e.toString())
                    }
                }
                "launchAppFromBackground" -> {
                    val packageName = call.argument<String>("packageName")
                    logInfo("Attempting to launch app from background: $packageName")
                    val success = launchAppFromBackground(packageName)
                    result.success(success)
                }
                "launchInstagramFromBackground" -> {
                    logInfo("Method call: launchInstagramFromBackground")
                    val success = launchInstagramFromBackground()
                    logInfo("Launch result: $success")
                    result.success(success)
                }
                "requestIgnoreBatteryOptimizations" -> {
                    try {
                        logInfo("Requesting to ignore battery optimizations")
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        logError("Error requesting battery optimization exemption: ${e.message}")
                        result.error("ERROR", "Failed to request battery optimization exemption", e.toString())
                    }
                }
                "checkAccessibilityPermissions" -> {
                    try {
                        val showDialog = call.argument<Boolean>("showDialog") ?: false
                        val hasPermission = checkAccessibilityPermissions(showDialog)
                        result.success(hasPermission)
                    } catch (e: Exception) {
                        logError("Error checking accessibility permissions: ${e.message}")
                        result.error("ERROR", "Failed to check accessibility permissions", e.toString())
                    }
                }
                "startTouchInjectionService" -> {
                    try {
                        val success = startTouchInjectionService()
                        result.success(success)
                    } catch (e: Exception) {
                        logError("Error starting TouchInjectionService: ${e.message}")
                        result.error("ERROR", "Failed to start TouchInjectionService", e.toString())
                    }
                }
                "rejectCall" -> {
                    try {
                        val success = rejectIncomingCall()
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("CallDetection", "Failed to reject call: ${e.message}")
                        result.error("CALL_REJECTION_ERROR", "Failed to reject call: ${e.message}", null)
                    }
                }
                "endOngoingCall" -> {
                    try {
                        val success = endOngoingCall()
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("CallDetection", "Failed to end ongoing call: ${e.message}")
                        result.error("CALL_END_ERROR", "Failed to end ongoing call: ${e.message}", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
        
        // Add system channel for additional system-level operations
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SYSTEM_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "launchAppFromBackground" -> {
                    val packageName = call.argument<String>("packageName") ?: "com.instagram.android"
                    Log.d(TAG, "System channel: Launching app from background: $packageName")
                    val success = launchInstagramFromBackground(packageName)
                    result.success(success)
                }
                "restartCamera" -> {
                    Log.d(TAG, "System channel: Received request to restart camera")
                    
                    // Force app to foreground
                    bringAppToForeground()
                    
                    // Also broadcast to ensure app is fully in foreground
                    try {
                        val intent = Intent("com.example.front_end.RESTART_CAMERA")
                        intent.putExtra("source", "system_channel")
                        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        sendBroadcast(intent)
                        Log.d(TAG, "Camera restart broadcast sent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending restart broadcast: ${e.message}")
                    }
                    
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
        
        flutterEngine.plugins.add(VolumeControlPlugin())
        flutterEngine.plugins.add(MediaControlPlugin())
    }
    
    private fun registerCallDetectionPlugin(messenger: BinaryMessenger, channel: MethodChannel) {
        // Create and initialize the CallDetectionService using our Kotlin implementation
        val callDetectionService = com.example.front_end.call.CallDetectionService(applicationContext, channel)
        
        // Set up method call handler
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startCallDetection" -> {
                    try {
                        callDetectionService.startListening()
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("CALL_DETECTION_ERROR", "Failed to start call detection: ${e.message}", null)
                    }
                }
                "stopCallDetection" -> {
                    try {
                        callDetectionService.stopListening()
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("CALL_DETECTION_ERROR", "Failed to stop call detection: ${e.message}", null)
                    }
                }
                "acceptCall" -> {
                    try {
                        val success = acceptIncomingCall()
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("CallDetection", "Failed to accept call: ${e.message}")
                        result.error("CALL_ACCEPTANCE_ERROR", "Failed to accept call: ${e.message}", null)
                    }
                }
                "rejectCall" -> {
                    try {
                        val success = rejectIncomingCall()
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("CallDetection", "Failed to reject call: ${e.message}")
                        result.error("CALL_REJECTION_ERROR", "Failed to reject call: ${e.message}", null)
                    }
                }
                "endOngoingCall" -> {
                    try {
                        val success = endOngoingCall()
                        result.success(success)
                    } catch (e: Exception) {
                        Log.e("CallDetection", "Failed to end ongoing call: ${e.message}")
                        result.error("CALL_END_ERROR", "Failed to end ongoing call: ${e.message}", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
    
    private fun acceptIncomingCall(): Boolean {
        try {
            Log.d("CallDetection", "Attempting to accept incoming call")
            
            // Access the TelecomManager service
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            
            if (telecomManager == null) {
                Log.e("CallDetection", "TelecomManager not available")
                return false
            }
            
            // Check for the required permission
            if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("CallDetection", "ANSWER_PHONE_CALLS permission not granted")
                return false
            }
            
            // Try to accept the call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.acceptRingingCall()
                Log.d("CallDetection", "Accepted incoming call")
                return true
            } else {
                Log.e("CallDetection", "Android version too low for acceptRingingCall")
                return false
            }
        } catch (e: Exception) {
            Log.e("CallDetection", "Error accepting call: ${e.message}")
            return false
        }
    }
    
    private fun rejectIncomingCall(): Boolean {
        try {
            Log.d("CallDetection", "Attempting to reject incoming call")
            
            // Access the TelecomManager service
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            
            if (telecomManager == null) {
                Log.e("CallDetection", "TelecomManager not available")
                return false
            }
            
            // Check for the required permission
            if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("CallDetection", "ANSWER_PHONE_CALLS permission not granted")
                return false
            }
            
            // Try to reject the call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                Log.d("CallDetection", "Rejected incoming call")
                return true
            } else {
                Log.e("CallDetection", "Android version too low for endCall")
                return false
            }
        } catch (e: Exception) {
            Log.e("CallDetection", "Error rejecting call: ${e.message}")
            return false
        }
    }
    
    private fun endOngoingCall(): Boolean {
        try {
            Log.d("CallDetection", "Attempting to end ongoing call")
            
            // Access the TelecomManager service
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            
            if (telecomManager == null) {
                Log.e("CallDetection", "TelecomManager not available")
                return false
            }
            
            // Check for the required permission
            if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("CallDetection", "ANSWER_PHONE_CALLS permission not granted")
                return false
            }
            
            // Try to end the call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                Log.d("CallDetection", "Ended ongoing call")
                return true
            } else {
                Log.e("CallDetection", "Android version too low for endCall")
                return false
            }
        } catch (e: Exception) {
            Log.e("CallDetection", "Error ending call: ${e.message}")
            return false
        }
    }
    
    private fun launchInstagramFromBackground(packageName: String, forceForeground: Boolean = false): Boolean {
        Log.d(TAG, "Attempting to launch Instagram from background, forceForeground=$forceForeground")
        
        // Wake up the device first if needed
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                PowerManager.ON_AFTER_RELEASE, "AirTouch:WakeLock")
            wakeLock.acquire(10000) // 10 seconds
            Log.d(TAG, "Wake lock acquired for launching")
            
            // Release the wake lock in a separate thread after a delay
            Thread {
                try {
                    Thread.sleep(5000)
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        Log.d(TAG, "Wake lock released after delay")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in wake lock release thread: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
        
        var success = false
        
        // Try multiple methods to maximize chances of success
        
        // Method 1: Use direct app launch from within MainActivity
        try {
            success = launchApp(packageName)
            Log.d(TAG, "Direct launchApp result: $success")
        } catch (e: Exception) {
            Log.e(TAG, "Error in direct launch: ${e.message}")
        }
        
        // Method 2: Send broadcast intent to the service to launch Instagram
        try {
            Log.d(TAG, "Sending broadcast intent for Instagram launch")
            Intent().also { intent ->
                intent.action = LAUNCH_ACTION
                intent.setPackage(packageName)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                if (forceForeground) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast: ${e.message}")
        }
        
        // Method 3: Reset and restart the service with specific launch intent
        try {
            Log.d(TAG, "Starting service with launch flag")
            val serviceIntent = Intent(this, CameraBackgroundService::class.java)
            serviceIntent.putExtra("launchInstagram", true)
            serviceIntent.putExtra("forceForeground", forceForeground)
            serviceIntent.putExtra("targetPackage", packageName)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service with launch flag: ${e.message}")
        }
        
        // Method 4: Try with component name directly
        try {
            Log.d(TAG, "Trying direct component name launch")
            val intent = Intent()
            intent.component = ComponentName(
                packageName, 
                "com.instagram.android.activity.MainTabActivity"
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (forceForeground) {
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
            success = true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching with component name: ${e.message}")
        }
        
        // Method 5: Try with ACTION_VIEW and URI
        if (!success) {
            try {
                Log.d(TAG, "Trying ACTION_VIEW with URI")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://app"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (forceForeground) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                startActivity(intent)
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "Error launching with URI: ${e.message}")
            }
        }
        
        return success
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        
        return false
    }
    
    private fun startForegroundService(packageName: String = "com.instagram.android", highPriority: Boolean = false) {
        try {
            Log.d(TAG, "Creating notification channel for background service")
            // Create notification channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Air Touch Background Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Air Touch running in background"
                    setShowBadge(false)
                }
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created: $NOTIFICATION_CHANNEL_ID")
            }
            
            // Create and start the foreground service with notification created in service
            Intent(this, CameraBackgroundService::class.java).also { intent ->
                intent.putExtra("targetPackage", packageName)
                intent.putExtra("highPriority", highPriority)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "Starting foreground service (Android 8.0+)")
                    startForegroundService(intent)
                } else {
                    Log.d(TAG, "Starting service (pre-Android 8.0)")
                    startService(intent)
                }
                
                backgroundServiceRunning = true
            }
            
            Log.d(TAG, "Background service started successfully" + if (highPriority) " with high priority" else "")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
        }
    }
    
    private fun stopForegroundService() {
        try {
            Intent(this, CameraBackgroundService::class.java).also { intent ->
                stopService(intent)
                backgroundServiceRunning = false
            }
            Log.d(TAG, "Background service stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}")
        }
    }
    
    private fun launchApp(packageName: String): Boolean {
        Log.d(TAG, "Attempting to launch app: $packageName")
        try {
            // First try to get the launch intent for the app
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // Add flags to ensure it launches in foreground
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                Log.d(TAG, "Successfully launched app using launch intent")
                return true
            }
            
            // If no launch intent found, try alternative methods
            // Try to launch specific activity for Instagram
            if (packageName == "com.instagram.android") {
                val specificIntent = Intent().apply {
                    component = ComponentName(packageName, "com.instagram.android.activity.MainTabActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(specificIntent)
                Log.d(TAG, "Successfully launched Instagram using specific activity")
                return true
            }
            
            Log.e(TAG, "Failed to launch app - no suitable intent found")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${e.message}")
            return false
        }
    }
    
    private fun simulateScroll(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        try {
            // Method 1: Use Instrumentation API (requires more permissions but works better)
            try {
                Log.d(TAG, "Attempting scroll using accessibility actions")
                
                // For scrolling, we need to use the Accessibility API or Global Actions
                // First, try with Android's Global Action for scrolling
                val direction = if (endY < startY) "up" else "down"
                val broadcast = Intent("com.example.front_end.PERFORM_GLOBAL_ACTION")
                broadcast.putExtra("action", "scroll")
                broadcast.putExtra("direction", direction)
                sendBroadcast(broadcast)
                
                Log.d(TAG, "Sent scroll broadcast with direction: $direction")
                
                // Wait a bit to let the broadcast be processed
                Thread.sleep(100)
            } catch (e: Exception) {
                Log.e(TAG, "Error with accessibility scroll: ${e.message}")
            }
            
            // Method 2: Try with gesture injection API (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    Log.d(TAG, "Using GestureDescription API for scroll")
                    
                    // Create a new Handler tied to the main thread
                    val handler = Handler(Looper.getMainLooper())
                    
                    // Use a broadcast to notify our accessibility service
                    handler.post {
                        try {
                            // Create a broadcast to be received by our custom accessibility service
                            val intent = Intent("com.example.front_end.GESTURE_SCROLL")
                            intent.putExtra("startX", startX)
                            intent.putExtra("startY", startY)
                            intent.putExtra("endX", endX)
                            intent.putExtra("endY", endY)
                            intent.putExtra("duration", duration)
                            sendBroadcast(intent)
                            
                            Log.d(TAG, "Sent gesture scroll broadcast")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending scroll broadcast: ${e.message}")
                        }
                    }
                    
                    // Wait for animation to complete
                    Thread.sleep(duration + 100)
                } catch (e: Exception) {
                    Log.e(TAG, "Error with gesture API: ${e.message}")
                }
            }
            
            // Method 3: As a fallback, use input injection service if available
            try {
                Log.d(TAG, "Using Input Manager service method for scroll")
                
                // Create a broadcast to ask our custom input service to perform the scroll
                val intent = Intent("com.example.front_end.SIMULATE_GESTURE")
                intent.putExtra("gestureType", "scroll")
                intent.putExtra("startX", startX)
                intent.putExtra("startY", startY)
                intent.putExtra("endX", endX)
                intent.putExtra("endY", endY)
                intent.putExtra("duration", duration)
                
                // Send the broadcast
                sendBroadcast(intent)
                
                Log.d(TAG, "Sent input service scroll broadcast")
            } catch (e: Exception) {
                Log.e(TAG, "Error with input service: ${e.message}")
            }
            
            Log.d(TAG, "Scroll simulation complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error in scroll simulation: ${e.message}")
        }
    }

    // Method to start the touch injection service
    private fun startTouchInjectionService(): Boolean {
        logInfo("Starting TouchInjectionService")
        try {
            val intent = Intent(context, TouchInjectionService::class.java)
            context.startService(intent)
            logInfo("TouchInjectionService started successfully")
            return true
        } catch (e: Exception) {
            logError("Failed to start TouchInjectionService: ${e.message}")
            return false
        }
    }
    
    // Method to send a broadcast to simulate scrolling
    private fun sendScrollBroadcast(direction: String, distance: Float): Boolean {
        logInfo("Sending scroll broadcast: direction=$direction, distance=$distance")
        try {
            val intent = Intent("com.example.front_end.SIMULATE_GESTURE")
            intent.putExtra("gestureType", "scroll")
            intent.putExtra("direction", direction)
            intent.putExtra("distance", distance)
            
            // Log clearly what kind of scroll we're performing
            if (direction == "up") {
                logInfo("Performing SCROLL UP - Content should move UP (showing new content below)")
            } else {
                logInfo("Performing SCROLL DOWN - Content should move DOWN (showing previous content)")
            }
            
            context.sendBroadcast(intent)
            logInfo("Scroll broadcast sent successfully for $direction scroll")
            return true
        } catch (e: Exception) {
            logError("Failed to send scroll broadcast: ${e.message}")
            return false
        }
    }
    
    // Method to send a broadcast to simulate horizontal swipe
    private fun sendHorizontalSwipeBroadcast(direction: String, distance: Float): Boolean {
        logInfo("Sending horizontal swipe broadcast: direction=$direction, distance=$distance")
        try {
            // Create intent for accessibility service
            val accessIntent = Intent("com.example.front_end.PERFORM_GLOBAL_ACTION")
            accessIntent.putExtra("action", "swipe")
            accessIntent.putExtra("direction", direction)
            accessIntent.putExtra("distance", distance)
            context.sendBroadcast(accessIntent)
            
            // Create a secondary intent for TouchInjectionService as fallback
            val touchIntent = Intent("com.example.front_end.SIMULATE_GESTURE")
            touchIntent.putExtra("gestureType", "swipe")
            touchIntent.putExtra("direction", direction)
            touchIntent.putExtra("distance", distance)
            context.sendBroadcast(touchIntent)
            
            // Also use scroll gesture service directly for swipe
            val scrollIntent = Intent("com.example.front_end.GESTURE_SCROLL")
            
            // Set up swipe coordinates based on direction
            // For swipe left, we move finger from right to left on screen
            // For swipe right, we move finger from left to right
            if (direction == "left") {
                // For swipe left, start on right side and move to left
                scrollIntent.putExtra("startX", 900f)
                scrollIntent.putExtra("startY", 600f)
                scrollIntent.putExtra("endX", 300f)
                scrollIntent.putExtra("endY", 600f)
                scrollIntent.putExtra("direction", "left")
            } else {
                // For swipe right, start on left side and move to right
                scrollIntent.putExtra("startX", 300f)
                scrollIntent.putExtra("startY", 600f)
                scrollIntent.putExtra("endX", 900f)
                scrollIntent.putExtra("endY", 600f)
                scrollIntent.putExtra("direction", "right")
            }
            scrollIntent.putExtra("duration", 300L)
            context.sendBroadcast(scrollIntent)
            
            logInfo("Horizontal swipe broadcasts sent successfully for $direction swipe")
            return true
        } catch (e: Exception) {
            logError("Failed to send horizontal swipe broadcast: ${e.message}")
            return false
        }
    }
    
    // Method to simulate a touch sequence
    private fun simulateTouchSequence(actions: List<Map<String, Any>>, duration: Int, inBackground: Boolean): Boolean {
        logInfo("Simulating touch sequence with ${actions.size} actions")
        
        try {
            // Extract coordinates from first and last actions for a simplified gesture
            if (actions.size >= 2) {
                val firstAction = actions.first()
                val lastAction = actions.last()
                
                val startX = (firstAction["x"] as? Number)?.toFloat() ?: 0f
                val startY = (firstAction["y"] as? Number)?.toFloat() ?: 0f
                val endX = (lastAction["x"] as? Number)?.toFloat() ?: 0f
                val endY = (lastAction["y"] as? Number)?.toFloat() ?: 0f
                
                logInfo("Touch sequence from ($startX,$startY) to ($endX,$endY)")
                
                // Determine if this is a horizontal or vertical gesture
                val isHorizontal = Math.abs(endX - startX) > Math.abs(endY - startY)
                
                if (isHorizontal) {
                    // Horizontal swipe - determine direction
                    val direction = if (endX > startX) "right" else "left"
                    logInfo("Detected horizontal swipe: $direction")
                    return sendHorizontalSwipeBroadcast(direction, Math.abs(endX - startX))
                } else {
                    // Vertical scroll - determine direction
                    val direction = if (endY > startY) "down" else "up"
                    logInfo("Detected vertical scroll: $direction")
                    return sendScrollBroadcast(direction, Math.abs(endY - startY))
                }
            } else {
                logError("Not enough actions for a touch sequence")
                return false
            }
        } catch (e: Exception) {
            logError("Error in touch sequence simulation: ${e.message}")
            return false
        }
    }
    
    // Press home button via accessibility service
    private fun pressHomeButton(): Boolean {
        logInfo("Sending home button press broadcast")
        try {
            val intent = Intent("com.example.front_end.PERFORM_GLOBAL_ACTION")
            intent.putExtra("action", "home")
            context.sendBroadcast(intent)
            logInfo("Home button press broadcast sent successfully")
            return true
        } catch (e: Exception) {
            logError("Failed to send home button press broadcast: ${e.message}")
            return false
        }
    }
    
    // Check if the accessibility service is enabled
    private fun checkAccessibilityPermissions(showDialog: Boolean): Boolean {
        logInfo("Checking accessibility permissions")
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        logInfo("Accessibility service enabled: $accessibilityEnabled")
        
        if (!accessibilityEnabled && showDialog) {
            showAccessibilityPermissionDialog()
        }
        
        return accessibilityEnabled
    }
    
    // Check if our accessibility service is enabled
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServiceId = "${context.packageName}/.ScrollGestureService"
        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Exception) {
            logError("Error getting accessibility enabled setting: ${e.message}")
            0
        }
        
        if (accessibilityEnabled == 1) {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            logInfo("Enabled accessibility services: $enabledServices")
            return enabledServices.contains(accessibilityServiceId)
        }
        
        return false
    }
    
    // Show a dialog to guide the user to enable accessibility permissions
    private fun showAccessibilityPermissionDialog() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Accessibility Permission Required")
                .setMessage("To use gesture control for scrolling, please enable the accessibility service in Settings.")
                .setPositiveButton("Go to Settings") { _: DialogInterface, _: Int ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
        }
    }

    // Logging methods
    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }
    
    private fun logError(message: String) {
        Log.e(TAG, message)
    }

    private fun launchAppFromBackground(packageName: String?): Boolean {
        Log.d(TAG, "Attempting to launch app from background: $packageName")
        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "Package name is null or empty")
            return false
        }
        
        try {
            // Check if the app is installed
            val isInstalled = isAppInstalled(packageName)
            if (!isInstalled) {
                Log.e(TAG, "App not installed: $packageName")
                return false
            }
            
            // Wake up the device if needed
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GestureControl:LaunchWakeLock"
            )
            wakeLock.acquire(10000) // Acquire wake lock for 10 seconds
            
            // Try to launch the app
            val launched = launchApp(packageName)
            
            // Release wake lock when done
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            
            return launched
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app from background: ${e.message}")
            return false
        }
    }
    
    // Method to launch Instagram from background
    private fun launchInstagramFromBackground(): Boolean {
        Log.d(TAG, "Attempting to launch Instagram from background")
        return launchAppFromBackground("com.instagram.android")
    }

    // Check if app is installed
    private fun isAppInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    // Enhanced media key event sending with multiple fallback methods
    private fun sendMediaKeyEvent(keyCode: Int): Boolean {
        try {
            Log.d(TAG, "Sending media key event for keyCode: $keyCode")
            
            // Create key events
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            
            var success = false
            
            // Method 1: Using AudioManager with strong events
            try {
                // Create stronger key event with system timestamp and repeat count = 0
                val now = SystemClock.uptimeMillis()
                val strongEventDown = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
                val strongEventUp = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
                
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                
                // Send DOWN event
                Log.d(TAG, "Sending STRONG key DOWN event via AudioManager")
                audioManager.dispatchMediaKeyEvent(strongEventDown)
                
                // Longer delay between DOWN and UP - critical for reliability
                Thread.sleep(300)
                
                // Send UP event
                Log.d(TAG, "Sending STRONG key UP event via AudioManager")
                audioManager.dispatchMediaKeyEvent(strongEventUp)
                
                Log.d(TAG, "STRONG Media key event sent successfully via AudioManager")
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "STRONG AudioManager method failed: ${e.message}")
                e.printStackTrace()
            }
            
            // Method 2: Regular AudioManager as fallback
            if (!success) {
                try {
                    Log.d(TAG, "Trying regular AudioManager method")
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    
                    // Send DOWN event
                    audioManager.dispatchMediaKeyEvent(eventDown)
                    
                    // Small delay between DOWN and UP
                    Thread.sleep(300)
                    
                    // Send UP event
                    audioManager.dispatchMediaKeyEvent(eventUp)
                    
                    Log.d(TAG, "Regular AudioManager method succeeded")
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Regular AudioManager method failed: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Method 3: Intent filter method
            if (!success) {
                try {
                    Log.d(TAG, "Trying broadcast intent for media key")
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                    
                    // Send DOWN event
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, eventDown)
                    sendOrderedBroadcast(intent, null)
                    
                    // Small delay
                    Thread.sleep(300)
                    
                    // Send UP event
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, eventUp)
                    sendOrderedBroadcast(intent, null)
                    
                    Log.d(TAG, "Media key event sent via broadcast intent")
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast intent method failed: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Method 4: Try the deprecated MUSIC_PLAYER intent approach
            if (!success && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                try {
                    Log.d(TAG, "Trying MUSIC_PLAYER next intent")
                    val i = Intent("android.intent.action.MEDIA_BUTTON")
                    i.putExtra(Intent.EXTRA_KEY_EVENT, eventDown)
                    sendOrderedBroadcast(i, null)
                    
                    Thread.sleep(300)
                    
                    val musicIntent = Intent("com.android.music.musicservicecommand")
                    musicIntent.putExtra("command", "next")
                    sendBroadcast(musicIntent)
                    
                    Log.d(TAG, "MUSIC_PLAYER next intent sent")
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "MUSIC_PLAYER next intent failed: ${e.message}")
                    e.printStackTrace()
                }
            } else if (!success && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                try {
                    Log.d(TAG, "Trying MUSIC_PLAYER previous intent")
                    val i = Intent("android.intent.action.MEDIA_BUTTON")
                    i.putExtra(Intent.EXTRA_KEY_EVENT, eventDown)
                    sendOrderedBroadcast(i, null)
                    
                    Thread.sleep(300)
                    
                    val musicIntent = Intent("com.android.music.musicservicecommand")
                    musicIntent.putExtra("command", "previous")
                    sendBroadcast(musicIntent)
                    
                    Log.d(TAG, "MUSIC_PLAYER previous intent sent")
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "MUSIC_PLAYER previous intent failed: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Method 5: Try using Instrumentation
            if (!success) {
                try {
                    Log.d(TAG, "Trying Instrumentation method for media key")
                    val inst = android.app.Instrumentation()
                    inst.sendKeyDownUpSync(keyCode)
                    Log.d(TAG, "Media key sent via Instrumentation")
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Instrumentation method failed: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending media key event: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}