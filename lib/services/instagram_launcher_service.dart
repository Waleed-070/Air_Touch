import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'dart:io';
import 'package:uuid/uuid.dart';
import 'dart:async';
import 'package:front_end/services/background_service.dart';

class InstagramLauncherService {
  DateTime? _lastInstagramLaunch;
  bool isLaunching = false;
  bool _instagramRunning = false;
  final Duration launchCooldown = const Duration(seconds: 5);
  final MethodChannel _instagramChannel = const MethodChannel('com.example.app/instagram');
  final MethodChannel _fallbackChannel = const MethodChannel('com.example.app/fallback');
  final BackgroundService _backgroundService = BackgroundService();
  
  // Track if Instagram was recently launched, used to detect return from Instagram
  bool wasRecentlyLaunched() {
    if (_lastInstagramLaunch == null) return false;
    
    final timeSinceLaunch = DateTime.now().difference(_lastInstagramLaunch!);
    
    // If more than 5 minutes passed, assume Instagram is no longer running
    if (timeSinceLaunch.inMinutes > 5) {
      _instagramRunning = false;
    }
    
    // Consider "recently launched" if within the last minute
    return timeSinceLaunch.inSeconds < 60;
  }
  
  // Reset the Instagram running state
  void resetInstagramRunningState() {
    _instagramRunning = false;
  }
  
  // Function to check cooldown and launch Instagram if appropriate
  Future<bool> tryLaunchInstagram({
    bool fromGesture = false, 
    bool inBackground = false,
    Function? onLaunchStart,
    Function? onLaunchComplete,
    Function(String)? onLaunchError,
    Function(String)? showFeedback
  }) async {
    final now = DateTime.now();
    final String launchId = const Uuid().v4().substring(0, 8);
    
    try {
      // Skip cooldown check if this wasn't triggered by a gesture (test button)
      if (!fromGesture) {
        print('Test button pressed - bypassing cooldown check');
      } else {
        // Check cooldown period
        if (_lastInstagramLaunch != null) {
          final difference = now.difference(_lastInstagramLaunch!);
          
          // If the cooldown period hasn't elapsed, don't launch Instagram
          if (difference < launchCooldown) {
            final remainingSeconds = launchCooldown.inSeconds - difference.inSeconds;
            print('[$launchId] Instagram launch on cooldown. Try again in $remainingSeconds seconds.');
            
            if (showFeedback != null) {
              showFeedback('Please wait ${remainingSeconds}s before trying again');
            }
            
            return false;
          }
        }
      }
      
      // If Instagram is already running and this is from a gesture, press home button instead
      if (_instagramRunning && fromGesture) {
        print('[$launchId] Instagram is already running, pressing home button instead');
        
        if (showFeedback != null) {
          showFeedback('Returning to home screen...');
        }
        
        // Press home button with explicit camera restart callback
        await _backgroundService.pressHomeButton(onPressed: () {
          print('[$launchId] Home button pressed callback - scheduling camera restart');
          
          // Add a delay before restarting camera to ensure app lifecycle has updated
          Future.delayed(const Duration(milliseconds: 500), () {
            // Force app back to foreground mode
            _backgroundService.setAppBackground(false, true);
            
            // Send an intent to restart the camera explicitly via platform channel
            try {
              final MethodChannel channel = const MethodChannel('com.example.app/system');
              channel.invokeMethod('restartCamera', {
                'forceForeground': true,
                'highPriority': true
              });
              print('[$launchId] Explicit camera restart requested via platform');
            } catch (e) {
              print('[$launchId] Error requesting camera restart: $e');
            }
          });
        });
        
        return true;
      }
      
      // If we're in background, use the direct method instead
      if (inBackground) {
        print('App in background, using direct launch method');
        final launched = await _launchInstagramFromBackground(highPriority: true, fromGesture: fromGesture);
        _instagramRunning = launched;
        return launched;
      }
      
      // Update the last launch time before launching
      _lastInstagramLaunch = now;
      isLaunching = true;
      
      if (onLaunchStart != null) {
        onLaunchStart();
      }
      
      // Show feedback to user
      if (showFeedback != null) {
        showFeedback('Launching Instagram...');
      }
      
      // Log the launch attempt
      print('[$launchId] Launching Instagram (fromGesture: $fromGesture, inBackground: $inBackground)');
      
      // Add a slight delay before launching to show the animation
      await Future.delayed(const Duration(milliseconds: 800));
      
      // Launch Instagram
      await _launchInstagram(fromGesture: fromGesture);
      
      print('[$launchId] Instagram launch request completed');
      _instagramRunning = true;
      
      isLaunching = false;
      if (onLaunchComplete != null) {
        onLaunchComplete();
      }
      
      return true;
    } catch (error) {
      print('[$launchId] Error in Instagram launch process: $error');
      
      isLaunching = false;
      if (onLaunchError != null) {
        onLaunchError('Failed to launch Instagram: $error');
      }
      
      return false;
    }
  }

  // Function to launch Instagram app
  Future<void> _launchInstagram({bool fromGesture = false}) async {
    try {
      print('Starting Instagram launch process... (fromGesture: $fromGesture)');
      
      // Platform-specific approach
      if (Platform.isAndroid) {
        // Try Android intent method first (most reliable)
        try {
          print('Trying to launch Instagram using platform-specific method...');
          await _instagramChannel.invokeMethod('launchInstagram', {
            'packageName': 'com.instagram.android',
            'isFromGesture': fromGesture  // Pass the gesture flag
          });
          print('Instagram launch request sent via platform channel');
          return; // If successful, exit early
        } catch (e) {
          print('Platform-specific method failed: $e');
          // Continue with other methods if this fails
        }
      }
      
      print('Attempting to launch Instagram using URL launcher...');
      // Try common URI schemes first
      final uris = [
        'instagram://',
        'instagram://app',
        'com.instagram.android',
        'android-app://com.instagram.android/https/instagram.com',
      ];
      
      bool launched = false;
      for (final uri in uris) {
        if (launched) break;
        
        try {
          print('Trying URI: $uri');
          launched = await launchUrl(
            Uri.parse(uri),
            mode: LaunchMode.externalNonBrowserApplication,
          );
          if (launched) {
            print('Successfully launched Instagram with URI: $uri');
            break;
          }
        } catch (e) {
          print('Failed with URI $uri: $e');
        }
      }
      
      // Try fallback method - direct intent via channel
      if (!launched && Platform.isAndroid) {
        try {
          print('Trying direct intent via custom channel...');
          final success = await _fallbackChannel.invokeMethod('openApp', {
            'package': 'com.instagram.android'
          });
          if (success == true) {
            print('Successfully launched Instagram via fallback channel');
            return;
          }
        } catch (e) {
          print('Fallback channel failed: $e');
        }
      }
      
      if (!launched) {
        print('Could not launch Instagram, trying Play Store/App Store...');
        // Fallback to Instagram in Play Store/App Store if app is not installed
        final Uri storeUri = Platform.isAndroid
            ? Uri.parse('https://play.google.com/store/apps/details?id=com.instagram.android')
            : Uri.parse('https://apps.apple.com/app/instagram/id389801252');
        
        await launchUrl(storeUri, mode: LaunchMode.externalApplication);
      }
    } catch (e) {
      print('Error launching Instagram: $e');
      
      // As a final fallback, try to open Instagram website
      try {
        print('Trying to open Instagram website instead...');
        final Uri webUri = Uri.parse('https://www.instagram.com/');
        await launchUrl(webUri, mode: LaunchMode.externalApplication);
      } catch (webError) {
        print('Failed to open Instagram website: $webError');
        throw e; // Re-throw the original error
      }
    }
  }

  // Special method for launching Instagram from background
  Future<bool> _launchInstagramFromBackground({bool highPriority = false, bool fromGesture = false}) async {
    try {
      print('BACKGROUND: Direct Instagram launch attempt (fromGesture: $fromGesture)');
      final now = DateTime.now();
      
      // Check cooldown only for genuine gesture detections
      if (fromGesture && _lastInstagramLaunch != null && 
          now.difference(_lastInstagramLaunch!) <= launchCooldown) {
        print('BACKGROUND: Instagram launch on cooldown, skipping');
        return false;
      }
      
      _lastInstagramLaunch = now;
      bool launched = false;
      
      // For Android, use platform channel directly
      if (Platform.isAndroid) {
        print('BACKGROUND: Using ALL POSSIBLE METHODS for direct Instagram launch');
        
        final String packageName = 'com.instagram.android';
        
        // Track which methods we've tried
        List<String> attemptedMethods = [];
        final MethodChannel backgroundChannel = const MethodChannel('com.example.app/background');
        
        // NEW HIGH PRIORITY METHOD FIRST (if requested)
        if (highPriority) {
          try {
            print('BACKGROUND: HIGH PRIORITY Direct Launch Method');
            await backgroundChannel.invokeMethod('highPriorityLaunch', {
              'packageName': packageName,
              'activityName': 'com.instagram.android.activity.MainTabActivity',
              'forceForeground': true,
              'wakeLockDuration': 10000,  // 10 seconds wake lock
              'launchFlags': ['FLAG_ACTIVITY_NEW_TASK', 'FLAG_ACTIVITY_RESET_TASK_IF_NEEDED'],
              'isFromGesture': fromGesture  // Pass the gesture flag
            });
            print('BACKGROUND: HIGH PRIORITY Launch invoked');
            attemptedMethods.add('high_priority_launch');
            launched = true;
          } catch (e) {
            print('BACKGROUND: HIGH PRIORITY method failed: $e');
          }
        }
        
        // Method 1: Try direct background launch via background method channel
        if (!launched) {
          try {
            print('BACKGROUND: Method 1 - Using dedicated background launch channel');
            await backgroundChannel.invokeMethod('launchInstagramFromBackground', {
              'packageName': packageName,
              'forceForeground': true,  // Add this flag to force foreground
              'isFromGesture': fromGesture  // Pass the gesture flag
            });
            print('BACKGROUND: Instagram launch request sent via background channel');
            attemptedMethods.add('background_channel');
            launched = true;
          } catch (e) {
            print('BACKGROUND: Method 1 failed: $e');
          }
        }
        
        // Method 2: Try with system channel
        if (!launched) {
          try {
            print('BACKGROUND: Method 2 - Using system service channel');
            final systemChannel = const MethodChannel('com.example.app/system');
            final success = await systemChannel.invokeMethod('launchAppFromBackground', {
              'packageName': packageName,
              'activityName': 'com.instagram.android.activity.MainTabActivity',
              'forceForeground': true
            });
            if (success == true) {
              print('BACKGROUND: Successfully launched Instagram via system service');
              attemptedMethods.add('system_channel');
              launched = true;
            }
          } catch (e) {
            print('BACKGROUND: Method 2 failed: $e');
          }
        }
        
        // Method 3: Try regular launch intent
        if (!launched) {
          try {
            print('BACKGROUND: Method 3 - Using regular launch intent');
            await _instagramChannel.invokeMethod('launchInstagram', {
              'packageName': packageName,
              'forceForeground': true
            });
            print('BACKGROUND: Instagram launch via regular channel succeeded');
            attemptedMethods.add('regular_channel');
            launched = true;
          } catch (e) {
            print('BACKGROUND: Method 3 failed: $e');
          }
        }
        
        // Method 4: Try URL launcher with different modes
        if (!launched) {
          final uris = [
            'instagram://', 
            'instagram://app',
            'com.instagram.android',
          ];
          
          for (final uri in uris) {
            if (launched) break;
            
            // Try with external non-browser mode
            try {
              print('BACKGROUND: Method 4a - Using URL launcher with URI: $uri (non-browser)');
              launched = await launchUrl(
                Uri.parse(uri),
                mode: LaunchMode.externalNonBrowserApplication,
              );
              if (launched) {
                print('BACKGROUND: Successfully launched Instagram with URI: $uri (non-browser)');
                attemptedMethods.add('url_launcher_non_browser');
                break;
              }
            } catch (e) {
              print('BACKGROUND: Method 4a failed with URI $uri: $e');
            }
            
            // Try with external application mode
            try {
              print('BACKGROUND: Method 4b - Using URL launcher with URI: $uri (external)');
              launched = await launchUrl(
                Uri.parse(uri),
                mode: LaunchMode.externalApplication,
              );
              if (launched) {
                print('BACKGROUND: Successfully launched Instagram with URI: $uri (external)');
                attemptedMethods.add('url_launcher_external');
                break;
              }
            } catch (e) {
              print('BACKGROUND: Method 4b failed with URI $uri: $e');
            }
          }
        }
        
        // Retry using the most promising methods after a short delay
        if (!launched || attemptedMethods.isEmpty) {
          print('BACKGROUND: First attempt failed, trying again after delay');
          
          // Wait a short moment before trying again
          await Future.delayed(const Duration(milliseconds: 500));
          
          // Retry background channel method
          try {
            print('BACKGROUND: Retry - Using background channel');
            await backgroundChannel.invokeMethod('launchInstagramFromBackground', {
              'packageName': packageName,
              'forceForeground': true,
              'retry': true
            });
            print('BACKGROUND: Retry successful with background channel');
            launched = true;
          } catch (e) {
            print('BACKGROUND: Retry with background channel failed: $e');
          }
        }
        
        // Final verification
        if (launched) {
          print('BACKGROUND: Successfully launched Instagram using one of the methods: $attemptedMethods');
        } else {
          print('BACKGROUND: All methods failed to launch Instagram');
        }
        
        return launched;
      }
      
      return false;
    } catch (e) {
      print('BACKGROUND: Error in Instagram background launch: $e');
      return false;
    }
  }
} 