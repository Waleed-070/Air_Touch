import 'package:flutter/services.dart';
import 'dart:io';

class BackgroundService {
  final MethodChannel _backgroundChannel = const MethodChannel('com.example.app/background');
  bool isInBackground = false;
  bool isBackgroundProcessingEnabled = true;

  // Method to request necessary permissions and start accessibility services
  Future<void> requestPermissions() async {
    if (Platform.isAndroid) {
      print('Running on Android, background tasks require permissions');
      
      try {
        // Request battery optimization exemption if needed
        await _backgroundChannel.invokeMethod('requestIgnoreBatteryOptimizations');
        print('Requested to ignore battery optimizations');
        
        // Request accessibility permissions if needed
        await _backgroundChannel.invokeMethod('checkAccessibilityPermissions', {
          'showDialog': true, // Show a dialog explaining why we need accessibility permissions
        });
        print('Checked accessibility permissions');
      } catch (e) {
        print('Error requesting permissions: $e');
      }
    }
  }
  
  // Method to start the background service
  Future<void> startBackgroundService() async {
    if (Platform.isAndroid) {
      try {
        print('Starting background service...');
        
        // First, ensure battery optimization is disabled
        try {
          await _backgroundChannel.invokeMethod('requestIgnoreBatteryOptimizations');
          print('Requested to ignore battery optimizations');
        } catch (e) {
          print('Error requesting battery optimization ignore: $e');
        }
        
        // Let the service know we want foreground processing and possible app launches
        await _backgroundChannel.invokeMethod('startBackgroundService', {
          'enableProcessing': true,
          'enableAppLaunch': true,
          'highPriority': true,  // Request high priority service
          'keepAlive': true,     // Keep the service alive
          'targetPackage': 'com.instagram.android',
          'targetActivity': 'com.instagram.android.activity.MainTabActivity',
          'disableProactiveTests': true, // IMPORTANT: Disable any test launches that aren't triggered by actual gestures
          'server': {
            'url': 'http://192.168.1.18:8000/upload_frame',
            'gestures': ['open_app', 'scroll_up', 'scroll_down'],
            'threshold': 0.70  // Increased threshold to 70% for more deliberate detection
          }
        });
        print('Background service started with Instagram launch capability');
        
        // CRITICAL FIX: Add a longer delay to ensure the foreground service has time to start properly
        // This prevents "ForegroundServiceDidNotStartInTimeException"
        print('Waiting for foreground service to start properly...');
        await Future.delayed(const Duration(milliseconds: 1000));
        
        // CRITICAL FIX: Explicitly tell service we're in background mode AFTER it's had time to start
        await _backgroundChannel.invokeMethod('setAppInBackground', {
          'inBackground': true,
          'enableDetection': true,
          'highPriority': true
        });
        print('CRITICAL: Explicitly informed service about background state');
      } catch (e) {
        print('Error starting background service: $e');
      }
    }
  }
  
  // Method to stop the background service
  Future<void> stopBackgroundService() async {
    if (Platform.isAndroid) {
      try {
        print('Stopping background service...');
        // Add a small delay before stopping to avoid race conditions
        await Future.delayed(const Duration(milliseconds: 100));
        await _backgroundChannel.invokeMethod('stopBackgroundService');
        print('Background service stopped');
      } catch (e) {
        print('Error stopping background service: $e');
      }
    }
  }

  // Method to notify background/foreground state change
  Future<void> setAppBackground(bool inBackground, bool detectionEnabled) async {
    // Force set the local state to match what we're sending
    isInBackground = inBackground;
    
    if (Platform.isAndroid) {
      try {
        // Ensure we stop the service first when going to foreground
        if (!inBackground) {
          try {
            print('Foreground transition: Explicitly stopping background service first');
            await _backgroundChannel.invokeMethod('stopBackgroundService');
            await Future.delayed(const Duration(milliseconds: 50));
          } catch (e) {
            print('Error pre-stopping background service: $e');
          }
        }
        
        // Then update the state
        print('Setting app state: inBackground=$inBackground, detectionEnabled=$detectionEnabled');
        await _backgroundChannel.invokeMethod('setAppInBackground', {
          'inBackground': inBackground,
          'enableDetection': detectionEnabled,
          'highPriority': true,
          'appStateUpdate': true  // Flag to indicate this is a state update
        });
        print('Successfully updated app background state');
        
        // If going to foreground, force stop again to be sure
        if (!inBackground) {
          try {
            print('Double-checking background service is stopped');
            await Future.delayed(const Duration(milliseconds: 50));
            await _backgroundChannel.invokeMethod('stopBackgroundService');
          } catch (e) {
            print('Error in post-stop of background service: $e');
          }
        }
      } catch (e) {
        print('Error setting app background state: $e');
      }
    }
  }

  // Start the accessibility and touch services
  Future<void> startAccessibilityServices() async {
    if (Platform.isAndroid) {
      try {
        // Start the touch injection service if available
        await _backgroundChannel.invokeMethod('startTouchInjectionService');
        print('Started touch injection service');
      } catch (e) {
        print('Error starting touch service: $e');
      }
    }
  }

  // Simulate a scroll gesture
  Future<void> simulateScroll({required bool scrollUp, required bool inBackground}) async {
    print('Simulating ${scrollUp ? "up" : "down"} scroll (DIRECTION FIX - INVERSION)');
    
    // Create a feedback for user
    if (!inBackground) {
      HapticFeedback.lightImpact();
    }
    
    String direction = scrollUp ? 'down' : 'up';  // INVERTED mapping for correct behavior
    
    try {
      // First try using the TouchInjectionService for direct touch injection
      print('Injecting touch with finger movement direction=$direction for ${scrollUp ? "up" : "down"} gesture');
      await _backgroundChannel.invokeMethod('simulateScroll', {
        'direction': direction,
        'distance': 300.0, // Always use positive distance
        'fromGesture': true,
        'inBackground': inBackground,
      }).then((success) {
        print('Touch injection successful: $success with direction=$direction for ${scrollUp ? "up" : "down"} gesture');
      }).catchError((e) {
        print('Error using touch injection service: $e');
        
        // Fall back to accessibility service if touch injection fails
        try {
          // For accessibility actions, we need to invert as well
          String accessibilityAction = scrollUp ? "scrollDown" : "scrollUp";
          print('Fallback: Using accessibility action: $accessibilityAction');
          
          _backgroundChannel.invokeMethod('performAccessibilityAction', {
            'action': accessibilityAction,
            'amount': 150.0  // Always positive, action determines direction
          });
          
          // Also try synthetic event as last resort with inverted delta
          final delta = scrollUp ? 150.0 : -150.0;  // Inverted delta
          _sendScrollEvent(delta);
          
          print('Fallback accessibility methods attempted with inverted directions');
        } catch (e2) {
          print('Error with fallback scroll method: $e2');
        }
      });
    } catch (e) {
      print('Error simulating scroll: $e');
    }
  }
  
  // Press home button
  Future<void> pressHomeButton({Function? onPressed}) async {
    print('Pressing home button via accessibility service');
    
    try {
      // Use haptic feedback to give user feedback
      HapticFeedback.mediumImpact();
      
      // Invoke the platform method to press home button
      final success = await _backgroundChannel.invokeMethod('pressHomeButton');
      print('Home button press result: $success');
      
      // After home button is pressed, schedule camera restart
      if (success == true) {
        // Add a small delay before trying to restart camera
        await Future.delayed(const Duration(milliseconds: 300));
        
        // Explicitly force foreground state
        try {
          await _backgroundChannel.invokeMethod('setAppInBackground', {
            'inBackground': false,
            'enableDetection': true,
            'highPriority': true,
            'forceRestart': true
          });
          print('Set foreground state after home button press');
        } catch (e) {
          print('Error setting foreground state: $e');
        }
        
        // Try to request camera restart via system channel
        try {
          final systemChannel = const MethodChannel('com.example.app/system');
          await systemChannel.invokeMethod('restartCamera', {
            'forceForeground': true,
            'afterHomePress': true
          });
          print('Requested camera restart after home button press');
        } catch (e) {
          print('Error requesting camera restart: $e');
        }
      }
      
      // Notify the callback if provided
      if (onPressed != null) {
        onPressed();
      }
    } catch (e) {
      print('Error pressing home button: $e');
    }
  }
  
  // Send a synthetic scroll event
  Future<void> _sendScrollEvent(double delta) async {
    // Use the touch injection service through the background channel
    try {
      // We're now using INVERTED delta values:
      // delta > 0 (positive) = scroll up gesture = need DOWN direction
      // delta < 0 (negative) = scroll down gesture = need UP direction
      
      // So direction is OPPOSITE of delta sign
      final direction = delta > 0 ? 'down' : 'up';  // Inverted relationship
      final distance = delta.abs();
      
      print('Sending synthetic event: finger direction=$direction, distance=$distance (inverted delta=$delta)');
      
      await _backgroundChannel.invokeMethod('simulateScroll', {
        'direction': direction,
        'distance': distance,
      });
    } catch (e) {
      print('Error sending scroll event: $e');
    }
  }

  // Setup method channel handlers for background service
  void setupMethodChannelHandler({
    Function(String, double)? onGestureDetected, 
    Function()? onInstagramLaunch,
    Function(bool, bool)? onScrollDetected
  }) {
    // Set up handler for background service channel
    _backgroundChannel.setMethodCallHandler((call) async {
      print('Received method call from background: ${call.method}');
      
      if (call.method == 'gestureDetectedInBackground') {
        final String gesture = call.arguments['gesture'] ?? '';
        final double confidence = call.arguments['confidence'] ?? 0.0;
        
        print('BACKGROUND HANDLER: Gesture detected: $gesture, confidence: ${(confidence * 100).toStringAsFixed(1)}%');
        
        if (onGestureDetected != null) {
          onGestureDetected(gesture, confidence);
        }
        
        if (gesture == 'open_app' && confidence > 0.65) {
          print('BACKGROUND HANDLER: Launching Instagram from background handler');
          if (onInstagramLaunch != null) {
            onInstagramLaunch();
          }
        } else if ((gesture == 'scroll_up' || gesture == 'scroll_down') && confidence > 0.70) {
          final bool isScrollUp = gesture == 'scroll_up';
          print('BACKGROUND HANDLER: Detected ${isScrollUp ? "UP" : "DOWN"} scroll gesture (${(confidence * 100).toStringAsFixed(1)}%)');
          
          if (onScrollDetected != null) {
            onScrollDetected(isScrollUp, true);  // true for inBackground
          }
          
          try {
            // Use INVERTED direction for correct scroll behavior
            // scrollUp = true → need 'down' direction to scroll content up
            // scrollUp = false → need 'up' direction to scroll content down
            final String direction = isScrollUp ? 'down' : 'up';  // INVERTED
            
            print('BACKGROUND HANDLER: Sending INVERTED direction=$direction for ${isScrollUp ? "up" : "down"} gesture');
            
            await _backgroundChannel.invokeMethod('simulateScroll', {
              'direction': direction,
              'distance': 300.0,
              'fromGesture': true,
              'inBackground': true,
            });
            print('BACKGROUND HANDLER: Scroll command sent successfully');
          } catch (e) {
            print('BACKGROUND HANDLER: Error during scroll: $e');
          }
        }
      }
      
      return null;
    });
  }
} 