package com.example.front_end

import android.content.Context
import android.media.AudioManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class VolumeControlPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        channel = MethodChannel(binding.binaryMessenger, "com.example.app/volume_control")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "adjustVolume" -> {
                val volumeChange = call.argument<Int>("volumeChange") ?: 0
                adjustVolumeByAmount(volumeChange)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun adjustVolumeByAmount(volumeChange: Int) {
        try {
            // Get the maximum volume level
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            // Get current volume
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            // Convert current volume to 0-100 scale
            val currentVolume100 = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()
            
            // Calculate new volume on 0-100 scale
            var newVolume100 = currentVolume100 + volumeChange
            // Clamp the value between 0 and 100
            newVolume100 = newVolume100.coerceIn(0, 100)
            
            // Convert back to system scale
            val newVolume = (newVolume100.toFloat() / 100 * maxVolume).toInt()
            
            // Set the new volume
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                newVolume,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            println("Error adjusting volume: ${e.message}")
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
} 