package com.example.front_end.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import io.flutter.plugin.common.MethodChannel

class CallDetectionService(private val context: Context, private val methodChannel: MethodChannel) {
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null
    private var callReceiver: PhoneCallReceiver? = null
    private val TAG = "CallDetectionService"
    
    fun startListening() {
        Log.d(TAG, "Starting call detection service")
        
        // Set up phone state listener
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                
                Log.d(TAG, "Phone state changed: $state")
                
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // Phone is ringing
                        Log.d(TAG, "Phone is ringing")
                        methodChannel.invokeMethod("onIncomingCall", true)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        // Call ended or no call
                        Log.d(TAG, "Call ended or no call")
                        methodChannel.invokeMethod("onIncomingCall", false)
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // Call answered or outgoing call
                        Log.d(TAG, "Call answered or outgoing call")
                        methodChannel.invokeMethod("onIncomingCall", false)
                    }
                }
            }
        }
        
        // Register phone state listener
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d(TAG, "Registered phone state listener")
        
        // Register broadcast receiver for additional call detection
        callReceiver = PhoneCallReceiver()
        val filter = IntentFilter().apply {
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Intent.ACTION_NEW_OUTGOING_CALL)
        }
        context.registerReceiver(callReceiver, filter)
        Log.d(TAG, "Registered broadcast receiver")
    }
    
    fun stopListening() {
        Log.d(TAG, "Stopping call detection service")
        
        // Unregister phone state listener
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
            Log.d(TAG, "Unregistered phone state listener")
        }
        
        // Unregister broadcast receiver
        if (callReceiver != null) {
            try {
                context.unregisterReceiver(callReceiver)
                callReceiver = null
                Log.d(TAG, "Unregistered broadcast receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }
    
    inner class PhoneCallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            
            if (action != null && action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Broadcast received: $action, state: $state")
                
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        Log.d(TAG, "Broadcast: Phone is ringing")
                        methodChannel.invokeMethod("onIncomingCall", true)
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        Log.d(TAG, "Broadcast: Call ended or no call")
                        methodChannel.invokeMethod("onIncomingCall", false)
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        Log.d(TAG, "Broadcast: Call answered or outgoing call")
                        methodChannel.invokeMethod("onIncomingCall", false)
                    }
                }
            }
        }
    }
} 