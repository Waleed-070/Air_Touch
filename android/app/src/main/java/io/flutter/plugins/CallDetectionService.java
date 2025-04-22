package io.flutter.plugins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import io.flutter.plugin.common.MethodChannel;

public class CallDetectionService {
    private Context context;
    private MethodChannel methodChannel;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    private PhoneCallReceiver callReceiver;
    
    public CallDetectionService(Context context, MethodChannel methodChannel) {
        this.context = context;
        this.methodChannel = methodChannel;
    }
    
    public void startListening() {
        // Set up phone state listener
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);
                
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        // Phone is ringing
                        methodChannel.invokeMethod("onIncomingCall", true);
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Call ended or no call
                        methodChannel.invokeMethod("onIncomingCall", false);
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        // Call answered or outgoing call
                        methodChannel.invokeMethod("onIncomingCall", false);
                        break;
                }
            }
        };
        
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        
        // Register broadcast receiver for additional call detection
        callReceiver = new PhoneCallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        context.registerReceiver(callReceiver, filter);
    }
    
    public void stopListening() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        
        if (callReceiver != null) {
            try {
                context.unregisterReceiver(callReceiver);
            } catch (Exception e) {
                // Receiver not registered
            }
        }
    }
    
    private class PhoneCallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (action != null && action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                
                if (state != null && state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                    methodChannel.invokeMethod("onIncomingCall", true);
                } else if (state != null && (state.equals(TelephonyManager.EXTRA_STATE_IDLE) || 
                           state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))) {
                    methodChannel.invokeMethod("onIncomingCall", false);
                }
            }
        }
    }
} 