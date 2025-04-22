package com.example.front_end

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import io.flutter.plugin.common.MethodChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import java.util.concurrent.TimeUnit
import android.content.ComponentName
import android.app.PendingIntent

class CameraBackgroundService : Service() {
    private val FOREGROUND_SERVICE_ID = 1001
    private val NOTIFICATION_CHANNEL_ID = "air_touch_background"
    private val TAG = "CameraBackgroundService"
    private var targetPackage = "com.instagram.android"
    private val handler = Handler(Looper.getMainLooper())
    private val LAUNCH_ACTION = "com.example.front_end.LAUNCH_INSTAGRAM"
    private val GESTURE_DETECTED_ACTION = "com.example.front_end.GESTURE_DETECTED"
    private lateinit var receiver: BroadcastReceiver
    private lateinit var gestureReceiver: BroadcastReceiver
    private var lastLaunchTime: Long = 0
    private val LAUNCH_COOLDOWN_MS = 5000 // 5 seconds cooldown
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Background service created")
        
        // CRITICAL: Create notification first to prevent ForegroundServiceDidNotStartInTimeException
        val notification = createNotification()
        startForeground(FOREGROUND_SERVICE_ID, notification)
        Log.d(TAG, "Foreground service started with notification")
        
        // Get wake lock to prevent the service from being killed
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CameraBackgroundService::WakeLock"
            )
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            Log.d(TAG, "Acquired wake lock for background processing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
        
        // Register broadcast receiver for launching Instagram
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Received broadcast to launch Instagram")
                if (intent?.action == LAUNCH_ACTION) {
                    launchInstagram()
                }
            }
        }
        
        // Register broadcast receiver for gesture detection
        try {
            registerReceiver(receiver, IntentFilter(LAUNCH_ACTION))
            Log.d(TAG, "Broadcast receiver registered for $LAUNCH_ACTION")
            
            // Also register the gesture detection receiver
            gestureReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == GESTURE_DETECTED_ACTION) {
                        val gesture = intent.getStringExtra("gesture") ?: ""
                        val confidence = intent.getDoubleExtra("confidence", 0.0)
                        Log.d(TAG, "Gesture broadcast received: $gesture ($confidence)")
                        
                        // Check if we should launch Instagram based on this gesture
                        if (gesture == "open_app" && confidence > 0.7) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastLaunchTime > LAUNCH_COOLDOWN_MS) {
                                Log.d(TAG, "Launching Instagram in response to broadcast gesture")
                                lastLaunchTime = currentTime
                                launchInstagram(true)
                            } else {
                                Log.d(TAG, "Instagram launch on cooldown, ignoring broadcast")
                            }
                        }
                    }
                }
            }
            registerReceiver(gestureReceiver, IntentFilter(GESTURE_DETECTED_ACTION))
            Log.d(TAG, "Gesture receiver registered for $GESTURE_DETECTED_ACTION")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receivers: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting foreground service")
        
        // CRITICAL: Ensure foreground notification is shown immediately
        // This must happen within 5 seconds of startForegroundService() on modern Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = createNotification()
            startForeground(FOREGROUND_SERVICE_ID, notification)
            Log.d(TAG, "Foreground notification updated on start command")
        }
        
        // Get target package name from intent if available
        intent?.getStringExtra("targetPackage")?.let {
            targetPackage = it
            Log.d(TAG, "Target package set to: $targetPackage")
        }
        
        // Check if high priority mode is requested
        val highPriority = intent?.getBooleanExtra("highPriority", false) ?: false
        if (highPriority) {
            Log.d(TAG, "⚡ HIGH PRIORITY mode requested for service")
        }
        
        // Check if proactive tests should be disabled
        val disableProactiveTests = intent?.getBooleanExtra("disableProactiveTests", false) ?: false
        
        // Ensure we have wake lock if in high priority mode
        if (highPriority && (wakeLock == null || wakeLock?.isHeld != true)) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CameraBackgroundService::HighPriorityWakeLock"
                )
                wakeLock?.acquire(20*60*1000L /*20 minutes*/)
                Log.d(TAG, "⚡ HIGH PRIORITY wake lock acquired")
            } catch (e: Exception) {
                Log.e(TAG, "⚡ Failed to acquire high priority wake lock: ${e.message}")
            }
        }
        
        // Check for app state updates
        val isAppStateUpdate = intent?.getBooleanExtra("appStateUpdate", false) ?: false
        if (isAppStateUpdate) {
            val inBackground = intent?.getBooleanExtra("inBackground", false) ?: false
            val enableDetection = intent?.getBooleanExtra("enableDetection", false) ?: false
            
            Log.d(TAG, "💡 App state update received: inBackground=$inBackground, enableDetection=$enableDetection")
            
            // Update service behavior based on app state
            if (inBackground && enableDetection) {
                Log.d(TAG, "💡 App in background with detection enabled - service will actively monitor for gestures")
                
                // REMOVED: Proactive test launch code to prevent unwanted launches
                // Only launch Instagram when a genuine gesture is detected
            } else if (!inBackground) {
                Log.d(TAG, "💡 App returned to foreground - service will operate in reduced capacity")
            }
        }
        
        // Check if we should immediately launch Instagram - BUT ONLY if it's a response to a genuine gesture
        val shouldLaunchInstagram = intent?.getBooleanExtra("launchInstagram", false) ?: false
        val isGenuineGesture = intent?.getBooleanExtra("genuineGestureDetected", false) ?: false
        val forceForeground = intent?.getBooleanExtra("forceForeground", false) ?: false
        
        if (shouldLaunchInstagram && isGenuineGesture) {
            Log.d(TAG, "Service received direct request to launch Instagram from GENUINE GESTURE, forceForeground=$forceForeground")
            handler.postDelayed({
                launchInstagram(forceForeground)
            }, 300) // Short delay to ensure service is fully started
        } else if (shouldLaunchInstagram) {
            Log.d(TAG, "⚠️ Launch request received but NOT from a genuine gesture - IGNORING to prevent unwanted launches")
        }
        
        // Check if this is a gesture detection result
        val isGestureDetection = intent?.getBooleanExtra("gestureDetection", false) ?: false
        if (isGestureDetection) {
            val gesture = intent?.getStringExtra("gesture") ?: ""
            val confidence = intent?.getDoubleExtra("confidence", 0.0) ?: 0.0
            val gestureHighPriority = intent?.getBooleanExtra("highPriority", false) ?: false
            
            val logPrefix = if (gestureHighPriority) "⚡ SERVICE" else "SERVICE"
            Log.d(TAG, "$logPrefix: Received gesture detection result: $gesture ($confidence)" + 
                if (gestureHighPriority) " with HIGH PRIORITY" else "")
            
            if (gesture == "open_app" && confidence > 0.70) { // Higher threshold of 0.70 (70%) to match Flutter code
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLaunchTime > LAUNCH_COOLDOWN_MS) {
                    Log.d(TAG, "$logPrefix: Open app gesture detected, launching Instagram" +
                        if (gestureHighPriority) " with HIGH PRIORITY" else "")
                    lastLaunchTime = currentTime
                    
                    // Show notification as the primary approach (works better in background)
                    try {
                        Log.d(TAG, "$logPrefix: Showing notification for user to launch Instagram")
                        showLaunchNotification("com.instagram.android")
                    } catch (e: Exception) {
                        Log.e(TAG, "$logPrefix: Error showing notification: ${e.message}")
                    }
                    
                    // Use different launch strategy based on priority
                    if (gestureHighPriority) {
                        // For high priority, use direct approach
                        handler.post {
                            Log.d(TAG, "⚡ Using forced foreground launch for HIGH PRIORITY gesture")
                            launchInstagram(forceForeground = true)
                        }
                        
                        // Also try broadcasting for redundancy
                        try {
                            val broadcastIntent = Intent("com.example.front_end.LAUNCH_INSTAGRAM")
                            broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            broadcastIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            sendBroadcast(broadcastIntent)
                            Log.d(TAG, "⚡ Sent HIGH PRIORITY broadcast")
                        } catch (e: Exception) {
                            Log.e(TAG, "⚡ Error sending HIGH PRIORITY broadcast: ${e.message}")
                        }
                    } else {
                        // Regular priority
                        launchInstagram(forceForeground)
                    }
                } else {
                    Log.d(TAG, "$logPrefix: Launch on cooldown, ignoring")
                }
            } else if (gesture == "open_app") {
                Log.d(TAG, "$logPrefix: Open app gesture detected but confidence too low: $confidence < 0.70")
            }
        }
        
        // The service will continue running until explicitly stopped
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not used for this service
    }
    
    private fun createNotification(): Notification {
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
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create the notification
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Air Touch Active")
            .setContentText("Gesture detection is running in the background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    // Method to launch Instagram from the background service
    fun launchInstagram(forceForeground: Boolean = false) {
        Log.d(TAG, "SERVICE: Attempting to launch Instagram from background service, forceForeground=$forceForeground")
        
        // Ensure device is awake for forced foreground launch
        if (forceForeground) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val tempWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or 
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                    PowerManager.ON_AFTER_RELEASE,
                    "CameraBackgroundService::LaunchWakeLock"
                )
                tempWakeLock.acquire(10000) // 10 seconds
                Log.d(TAG, "⚡ Acquired full wake lock for forced foreground launch")
                
                // Release after delay in background thread
                Thread {
                    try {
                        Thread.sleep(5000)
                        if (tempWakeLock.isHeld) {
                            tempWakeLock.release()
                            Log.d(TAG, "⚡ Released temporary wake lock after delay")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "⚡ Error in wake lock release thread: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "⚡ Failed to acquire wake lock for launch: ${e.message}")
            }
        }
        
        // Try all methods in parallel to increase chances of success
        handler.post {
            // Method 1: Standard launchApp approach with multiple methods
            val success = launchApp(targetPackage, forceForeground)
            Log.d(TAG, "SERVICE: Instagram launch result (standard method): $success")
            
            // Method 2: Direct activity start
            try {
                Log.d(TAG, "SERVICE: Trying direct activity component start")
                val intent = Intent()
                intent.component = ComponentName(
                    "com.instagram.android", 
                    "com.instagram.android.activity.MainTabActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                if (forceForeground) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                }
                startActivity(intent)
                Log.d(TAG, "SERVICE: Direct activity component method completed")
            } catch (e: Exception) {
                Log.e(TAG, "SERVICE: Failed to launch with direct component: ${e.message}")
            }
            
            // Method 3: Try with special flag to bring activity to front
            try {
                Log.d(TAG, "SERVICE: Trying with bring-to-front flags")
                val bringToFrontIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (bringToFrontIntent != null) {
                    bringToFrontIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    bringToFrontIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    if (forceForeground) {
                        bringToFrontIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        bringToFrontIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(bringToFrontIntent)
                    Log.d(TAG, "SERVICE: Bring-to-front method completed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "SERVICE: Failed to launch with bring-to-front: ${e.message}")
            }
            
            // If forced foreground, try additional methods
            if (forceForeground) {
                try {
                    Log.d(TAG, "⚡ Trying URI launch with foreground flags")
                    val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://app"))
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) 
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(uriIntent)
                    Log.d(TAG, "⚡ URI launch with foreground flags completed")
                } catch (e: Exception) {
                    Log.e(TAG, "⚡ Failed URI launch with foreground flags: ${e.message}")
                }
            }
        }
    }
    
    private fun launchApp(packageName: String, forceForeground: Boolean = false): Boolean {
        Log.d(TAG, "SERVICE: Attempting to launch app with package: $packageName, forceForeground=$forceForeground")
        
        // Check if the app is installed first
        val isAppInstalled = isAppInstalled(packageName)
        Log.d(TAG, "SERVICE: Is Instagram installed? $isAppInstalled")
        
        if (!isAppInstalled) {
            // Try to open Play Store if not installed
            Log.d(TAG, "SERVICE: Instagram not installed, opening Play Store")
            return openPlayStore(packageName)
        }
        
        // Try multiple approaches to launch Instagram
        val methods = listOf(
            { tryLaunchWithMainIntent(packageName, forceForeground) },
            { tryLaunchWithViewIntent(packageName, forceForeground) },
            { tryLaunchWithCustomUri(forceForeground) }
        )
        
        for (method in methods) {
            try {
                val result = method.invoke()
                if (result) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "SERVICE: Launch method failed: ${e.message}")
                // Continue to next method
            }
        }
        
        // If all methods failed, open Play Store as fallback
        return openPlayStore(packageName)
    }
    
    private fun tryLaunchWithMainIntent(packageName: String, forceForeground: Boolean = false): Boolean {
        Log.d(TAG, "SERVICE: Trying to launch with main intent, forceForeground=$forceForeground")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        
        if (launchIntent != null) {
            Log.d(TAG, "SERVICE: Found launch intent, launching app")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (forceForeground) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            }
            try {
                startActivity(launchIntent)
                Log.d(TAG, "SERVICE: Main intent launched successfully")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "SERVICE: Error launching main intent: ${e.message}")
                return false
            }
        }
        
        Log.d(TAG, "SERVICE: No main launch intent found")
        return false
    }
    
    private fun tryLaunchWithViewIntent(packageName: String, forceForeground: Boolean = false): Boolean {
        Log.d(TAG, "SERVICE: Trying to launch with VIEW intent, forceForeground=$forceForeground")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage(packageName)
        intent.data = Uri.parse("https://instagram.com")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (forceForeground) {
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        
        return try {
            startActivity(intent)
            Log.d(TAG, "SERVICE: Successfully launched with VIEW intent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SERVICE: Failed to launch with VIEW intent: ${e.message}")
            false
        }
    }
    
    private fun tryLaunchWithCustomUri(forceForeground: Boolean = false): Boolean {
        Log.d(TAG, "SERVICE: Trying to launch with custom URI, forceForeground=$forceForeground")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (forceForeground) {
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        
        return try {
            startActivity(intent)
            Log.d(TAG, "SERVICE: Successfully launched with custom URI")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SERVICE: Failed to launch with custom URI: ${e.message}")
            false
        }
    }
    
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun openPlayStore(packageName: String): Boolean {
        Log.d(TAG, "SERVICE: Opening Play Store for $packageName")
        val intent = Intent(Intent.ACTION_VIEW, 
            Uri.parse("market://details?id=$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            // If Play Store app is not installed, open in browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(webIntent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "SERVICE: Could not open Play Store: ${e2.message}")
                false
            }
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Background service destroyed")
        
        // Release the wake lock
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}")
            }
        }
        
        // Unregister the broadcast receivers
        try {
            unregisterReceiver(receiver)
            unregisterReceiver(gestureReceiver)
            Log.d(TAG, "Broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }
        
        super.onDestroy()
    }

    private fun showLaunchNotification(packageName: String) {
        Log.d(TAG, "SERVICE: Showing launch notification for $packageName")
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

        // Create an intent to open MainActivity which will handle launching Instagram
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "LAUNCH_INSTAGRAM"
            putExtra("targetPackage", packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Create a pending intent that will be triggered when user taps the notification
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create direct launch intent as action button
        val directLaunchIntent = Intent().apply {
            component = ComponentName(packageName, "com.instagram.android.activity.MainTabActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val directLaunchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            directLaunchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with high visibility
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gesture Detected!")
            .setContentText("Tap to open Instagram")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_camera, "Open Instagram", directLaunchPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Use a different ID than the foreground service notification
        notificationManager.notify(1003, notification)
        Log.d(TAG, "SERVICE: Launch notification shown")
    }
}