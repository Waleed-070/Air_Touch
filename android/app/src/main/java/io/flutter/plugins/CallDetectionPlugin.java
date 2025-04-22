package io.flutter.plugins;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class CallDetectionPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel methodChannel;
    private CallDetectionService callDetectionService;
    private Context applicationContext;
    private Activity activity;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        setupChannel(binding.getBinaryMessenger(), binding.getApplicationContext());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        teardownChannel();
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        if (callDetectionService != null) {
            callDetectionService.stopListening();
        }
        callDetectionService = new CallDetectionService(activity, methodChannel);
    }

    @Override
    public void onDetachedFromActivity() {
        if (callDetectionService != null) {
            callDetectionService.stopListening();
        }
        activity = null;
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    private void setupChannel(BinaryMessenger messenger, Context context) {
        applicationContext = context;
        methodChannel = new MethodChannel(messenger, "com.example.app/call_detection");
        methodChannel.setMethodCallHandler(this);
    }

    private void teardownChannel() {
        if (callDetectionService != null) {
            callDetectionService.stopListening();
            callDetectionService = null;
        }
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (callDetectionService == null) {
            callDetectionService = new CallDetectionService(activity != null ? activity : applicationContext, methodChannel);
        }

        switch (call.method) {
            case "startCallDetection":
                callDetectionService.startListening();
                result.success(null);
                break;
            case "stopCallDetection":
                callDetectionService.stopListening();
                result.success(null);
                break;
            default:
                result.notImplemented();
                break;
        }
    }
} 