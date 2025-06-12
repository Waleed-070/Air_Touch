package com.example.front_end

import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.os.SystemClock
import io.flutter.plugin.common.BinaryMessenger

class MediaControlPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private val TAG = "MediaControlPlugin"

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        channel = MethodChannel(binding.binaryMessenger, "com.example.app/media_control")
        channel.setMethodCallHandler(this)
    }

    // Alternative initialization method for direct usage without FlutterPlugin binding
    fun onAttachedToEngine(context: Context, messenger: BinaryMessenger) {
        this.context = context
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        channel = MethodChannel(messenger, "com.example.app/media_control")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "playPause" -> {
                val success = simulateMediaButtonPress()
                result.success(success)
            }
            "skipNext" -> {
                val success = simulateMediaNextButtonPress()
                result.success(success)
            }
            "skipPrevious" -> {
                val success = simulateMediaPreviousButtonPress()
                result.success(success)
            }
            "isPlaying" -> {
                // This is a bit of a hack since we can't directly query if media is playing
                // We'd need to implement an AudioPlaybackCallback for API level 26+ for better detection
                result.success(true) // Default to assuming media is playing
            }
            else -> result.notImplemented()
        }
    }

    private fun simulateMediaButtonPress(): Boolean {
        return sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun simulateMediaNextButtonPress(): Boolean {
        return sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
    }
    
    public fun simulateMediaPreviousButtonPress(): Boolean {
        try {
            Log.d(TAG, "Simulating media PREVIOUS button press - using multiple approaches")
            
            var success = false
            
            // Method 1: Primary approach with enhanced timing
            try {
                // Create stronger key event with system timestamp
                val now = SystemClock.uptimeMillis()
                val strongEventDown = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                val strongEventUp = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                
                // Send DOWN event
                Log.d(TAG, "Sending STRONG media PREVIOUS key DOWN event")
                audioManager.dispatchMediaKeyEvent(strongEventDown)
                
                // Longer delay between DOWN and UP - critical for reliability with PREVIOUS
                Thread.sleep(500)  // Even longer delay for previous track
                
                // Send UP event
                Log.d(TAG, "Sending STRONG media PREVIOUS key UP event")
                audioManager.dispatchMediaKeyEvent(strongEventUp)
                
                Log.d(TAG, "STRONG Media previous button press simulated successfully")
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "STRONG AudioManager method failed: ${e.message}")
            }
            
            // Method 2: Double press approach (many media players need this for PREVIOUS)
            try {
                Log.d(TAG, "Trying double-press approach for PREVIOUS button")
                
                // First press
                val now1 = SystemClock.uptimeMillis()
                val firstDown = KeyEvent(now1, now1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                audioManager.dispatchMediaKeyEvent(firstDown)
                Thread.sleep(200)
                
                val firstUp = KeyEvent(now1, now1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                audioManager.dispatchMediaKeyEvent(firstUp)
                
                // Delay between presses
                Thread.sleep(250)
                
                // Second press
                val now2 = SystemClock.uptimeMillis()
                val secondDown = KeyEvent(now2, now2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                audioManager.dispatchMediaKeyEvent(secondDown)
                Thread.sleep(200)
                
                val secondUp = KeyEvent(now2, now2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0)
                audioManager.dispatchMediaKeyEvent(secondUp)
                
                Log.d(TAG, "Double-press for PREVIOUS track completed")
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "Double-press method failed: ${e.message}")
            }
            
            // Method 3: Standard intent approach
            try {
                Log.d(TAG, "Trying MUSIC_PLAYER previous intent")
                
                // First send a media button event
                val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                i.putExtra(Intent.EXTRA_KEY_EVENT, eventDown)
                context.sendOrderedBroadcast(i, null)
                
                Thread.sleep(400)
                
                // Then send the direct music service command
                val musicIntent = Intent("com.android.music.musicservicecommand")
                musicIntent.putExtra("command", "previous")
                context.sendBroadcast(musicIntent)
                
                // Also send UP event
                val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                i.putExtra(Intent.EXTRA_KEY_EVENT, eventUp)
                context.sendOrderedBroadcast(i, null)
                
                Log.d(TAG, "MUSIC_PLAYER previous intent sent")
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "MUSIC_PLAYER previous intent failed: ${e.message}")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating media previous button press: ${e.message}")
            return false
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
            
            // Method 1: AudioManager with strong events
            try {
                // Create stronger key event with system timestamp and repeat count = 0
                val now = SystemClock.uptimeMillis()
                val strongEventDown = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
                val strongEventUp = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
                
                // Send DOWN event
                Log.d(TAG, "Sending STRONG key DOWN event via AudioManager")
                audioManager.dispatchMediaKeyEvent(strongEventDown)
                
                // Longer delay between DOWN and UP - critical for reliability
                Thread.sleep(300)  // Increased delay for even better reliability
                
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
                    context.sendOrderedBroadcast(intent, null)
                    
                    // Small delay
                    Thread.sleep(300)
                    
                    // Send UP event
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, eventUp)
                    context.sendOrderedBroadcast(intent, null)
                    
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
                    context.sendOrderedBroadcast(i, null)
                    
                    Thread.sleep(300)
                    
                    val musicIntent = Intent("com.android.music.musicservicecommand")
                    musicIntent.putExtra("command", "next")
                    context.sendBroadcast(musicIntent)
                    
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
                    context.sendOrderedBroadcast(i, null)
                    
                    Thread.sleep(300)
                    
                    val musicIntent = Intent("com.android.music.musicservicecommand")
                    musicIntent.putExtra("command", "previous")
                    context.sendBroadcast(musicIntent)
                    
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

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
} 