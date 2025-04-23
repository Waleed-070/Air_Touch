import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'dart:convert';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image/image.dart' as img;
import 'dart:typed_data';
import 'package:url_launcher/url_launcher.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'dart:io';
import 'package:uuid/uuid.dart';
import 'package:flutter/gestures.dart';
import 'package:permission_handler/permission_handler.dart';

// Import services
import '../services/camera_service.dart';
import '../services/gesture_service.dart';
import '../services/instagram_launcher_service.dart';
import '../services/background_service.dart';
import '../services/call_detection_service.dart';

// Import widgets
import '../widgets/gesture_detection_panel.dart';
import '../widgets/settings_panel.dart';
import '../widgets/instagram_launch_overlay.dart';
import '../widgets/camera_preview_widget.dart';
import '../widgets/touch_indicator.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  // State variables
  bool isSpatialTouchEnabled = false;
  bool gestureDetected = false;
  bool _showTouchIndicator = false;
  Offset _fingerPosition = Offset.zero;  // New position state
  bool _hasIncomingCall = false;
  bool _hasOngoingCall = false;
  
  String? detectedGesture;
  double? confidence;
  bool _isLaunchingInstagram = false;
  bool _appInBackground = false;
  bool _enableBackgroundProcessing = true;
  
  // Services
  late CameraService _cameraService;
  final GestureService _gestureService = GestureService();
  final InstagramLauncherService _instagramLauncherService = InstagramLauncherService();
  final BackgroundService _backgroundService = BackgroundService();
  final CallDetectionService _callDetectionService = CallDetectionService();

  // Add a timer variable at the class level
  Timer? _touchIndicatorTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    
    // Initialize services
    _cameraService = CameraService();
    
    // Enable background processing by default
    _enableBackgroundProcessing = true;
    
    // Setup platform method channel for camera restart requests
    _setupCameraRestartChannel();
    
    // Setup services in correct order
    _initializeServices();
  }
  
  Future<void> _initializeServices() async {
    // Start with background service
    _setupBackgroundService();
    
    // Then request permissions
    await _requestPermissions();
    
    // Finally setup call detection (after permissions)
    _setupCallDetection();
  }

  void _setupBackgroundService() {
    _backgroundService.setupMethodChannelHandler(
      onGestureDetected: (gesture, confidence) {
        // Update UI if in foreground
        if (!_appInBackground && mounted) {
          setState(() {
            gestureDetected = true;
            detectedGesture = gesture;
            this.confidence = confidence;
          });
        }
      },
      onInstagramLaunch: () {
        _instagramLauncherService.tryLaunchInstagram(
          fromGesture: true, 
          inBackground: true
        );
      },
      onScrollDetected: (isScrollUp, inBackground) {
        _backgroundService.simulateScroll(
          scrollUp: isScrollUp,
          inBackground: inBackground
        );
      }
    );
    
    // Start accessibility services
    _backgroundService.startAccessibilityServices();
  }

  Future<void> _requestPermissions() async {
    await _backgroundService.requestPermissions();
  }

//permission checking  in Android apps
  void _setupCallDetection() async {
    print('Setting up call detection...');
    
    // Request phone permission first
    if (Platform.isAndroid) {
      final status = await Permission.phone.request();
      print('Phone permission status: $status');
      
      // Request ANSWER_PHONE_CALLS permission for Android 8.0+
      if (Platform.isAndroid) {
        final phoneCallsStatus = await Permission.phone.request();
        print('Answer phone calls permission status: $phoneCallsStatus');
        
        if (phoneCallsStatus.isDenied || phoneCallsStatus.isPermanentlyDenied) {
          print('Answer phone calls permission denied, call control may not work');
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Phone permissions are required to accept/reject calls'),
                backgroundColor: Colors.red,
                duration: Duration(seconds: 5),
              ),
            );
          }
        }
      }
      
      if (status.isDenied || status.isPermanentlyDenied) {
        print('Phone permission denied, call detection may not work');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Phone permission is required for call detection'),
              backgroundColor: Colors.red,
              duration: Duration(seconds: 5),
            ),
          );
        }
        return;
      }
    }
    
    await _callDetectionService.initialize();
    await _callDetectionService.startListening();
    
    // Listen for call status changes
    _callDetectionService.callStream.listen((callStateChanged) {
      print('Received call state change notification');
      if (mounted) {
        setState(() {
          _hasIncomingCall = _callDetectionService.isIncomingCall;
          _hasOngoingCall = _callDetectionService.isOngoingCall;
        });
      }
    });
  }

  // Add a new method for explicit camera restart
  void _forceRestartCamera() {
    print('EXPLICIT CAMERA RESTART: Force restarting camera after home button press');
    
    // Stop any existing camera stream
    _cameraService.stopImageStream();
    
    // Dispose of old camera resources
    _cameraService.dispose();
    
    // Reset services
    _restartServices();
    
    // Force foreground mode
    _appInBackground = false;
    if (Platform.isAndroid) {
      _backgroundService.setAppBackground(false, true);
    }
    
    // Initialize camera with short delay to ensure Android has updated lifecycle state
    Future.delayed(const Duration(milliseconds: 200), () {
      print('EXPLICIT CAMERA RESTART: Starting camera initialization');
      _initializeCamera();
      
      // Schedule a second attempt for reliability
      Future.delayed(const Duration(milliseconds: 1000), () {
        if (mounted && isSpatialTouchEnabled && 
            (_cameraService.controller == null || 
             !_cameraService.controller!.value.isInitialized || 
             !_cameraService.controller!.value.isStreamingImages)) {
          print('EXPLICIT CAMERA RESTART: Second attempt at camera initialization');
          _initializeCamera();
        }
      });
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Handle app lifecycle changes
    print('App lifecycle state changed to: $state');
    
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      // App is in background
      print('App going to background, background processing: $_enableBackgroundProcessing');
      _appInBackground = true;
      
      if (isSpatialTouchEnabled && _enableBackgroundProcessing && Platform.isAndroid) {
        // Keep camera processing in background if enabled
        print('Starting background service with active camera processing');
        _backgroundService.startBackgroundService();
        
        // Don't stop the image stream in this case, let it continue processing
        if (_cameraService.controller != null && 
            _cameraService.controller!.value.isInitialized && 
            !_cameraService.controller!.value.isStreamingImages) {
          print('Ensuring image stream is active in background');
          _startImageProcessing();
        }
        
        // Notify native code immediately that we're in background
        _backgroundService.setAppBackground(true, true);
      } else {
        // Otherwise, stop the camera when going to background
        print('Stopping camera when going to background (background processing disabled)');
        _cameraService.stopImageStream();
      }
    } else if (state == AppLifecycleState.resumed) {
      // App is in foreground again
      print('App resumed to foreground from background: $_appInBackground');
      
      // CRITICAL FIX: Always reset background flag first thing when resumed
      bool wasInBackground = _appInBackground;
      _appInBackground = false;
      
      // Reset Instagram running state when app comes to foreground
      _instagramLauncherService.resetInstagramRunningState();
      
      // Update native code that we're back in foreground with high priority
      if (Platform.isAndroid) {
        print('CRITICAL: Telling native code we are now in foreground');
        _backgroundService.setAppBackground(false, isSpatialTouchEnabled);
      }
      
      // Stop the background service since we're back in foreground
      if (Platform.isAndroid) {
        print('Stopping background service immediately');
        _backgroundService.stopBackgroundService();
      }
      
      // FORCE CAMERA RESTART when app resumes - critical for home button press recovery
        if (isSpatialTouchEnabled) {
        print('LIFECYCLE: Forcing camera restart on resume for reliable recovery');
        _forceRestartCamera();
      }
    }
  }

  Future<void> _initializeCamera() async {
    print('Initializing camera, isSpatialTouchEnabled: $isSpatialTouchEnabled, inBackground: $_appInBackground');
    
    if (!isSpatialTouchEnabled) {
      print('Skipping camera initialization - feature disabled');
      return;
    }
    
    // CRITICAL FIX: Force foreground mode when returning from Instagram
    if (_appInBackground && _instagramLauncherService.wasRecentlyLaunched()) {
      print('OVERRIDE: Forcing foreground mode because we detected return from Instagram');
      _appInBackground = false;
    }
    
    // Double check background state before initializing
    if (_appInBackground) {
      print('Skipping camera initialization - app is in background');
      return;
    }
    
    print('Starting camera initialization...');
    await _cameraService.initialize();
        
    if (mounted) {
      setState(() {});
    }

    if (isSpatialTouchEnabled && !_appInBackground) {
      _startImageProcessing();
    }
  }

  void _startImageProcessing() {
    _cameraService.startImageStream((image) {
      if (!_instagramLauncherService.isLaunching && !_cameraService.isProcessing) {
        _cameraService.isProcessing = true;
        _processFrame(image).whenComplete(() => _cameraService.isProcessing = false);
      }
    });
  }

  Future<void> _processFrame(CameraImage image) async {
    try {
      await _gestureService.processAndSendFrame(
        image: image,
        imageConverter: (img) => _cameraService.convertYUV420toJpeg(img),
        onGestureDetected: (detected, gesture, confidenceValue, positionData) {
          if (mounted && !_appInBackground) {
            // Only update UI when there's an actual change in gesture detection
            final bool shouldUpdateState = 
                gestureDetected != detected || 
                detectedGesture != gesture || 
                confidence != confidenceValue;
                
            if (gesture == 'move' && detected) {
              // Cancel any pending timer
              _touchIndicatorTimer?.cancel();
              
              if (positionData != null) {
                final newPosition = _convertServerPosition(
                  positionData['x']!, 
                  positionData['y']!,
                  MediaQuery.of(context).size
                );
                
                // Update finger position if it has moved significantly
                if ((_fingerPosition - newPosition).distance > 5.0) {
                  setState(() {
                    _showTouchIndicator = true;
                    _fingerPosition = newPosition;
                  });
                } else if (!_showTouchIndicator) {
                  setState(() {
                    _showTouchIndicator = true;
                  });
                }
              }
            } else if (_showTouchIndicator) {
              // Start timer to hide indicator after 250ms
              _touchIndicatorTimer?.cancel();
              _touchIndicatorTimer = Timer(const Duration(milliseconds: 250), () {
                if (mounted) {
                  setState(() {
                    _showTouchIndicator = false;
                  });
                }
              });
            }
            
            // Only update UI state if there's an actual change
            if (shouldUpdateState) {
              setState(() {
                gestureDetected = detected;
                detectedGesture = gesture;
                confidence = confidenceValue;
              });
            }
          }
        },
        onGestureAction: (gesture, confidenceValue) {
          _handleGestureAction(gesture, confidenceValue);
        },
        inBackground: _appInBackground,
      );
    } catch (e) {
      print('Error during frame processing: $e');
      
      // Check for buffer timeout errors and restart camera if needed
      if (e.toString().contains('timeout') || e.toString().contains('TIMED_OUT')) {
        print('Buffer timeout detected in processFrame - restarting camera');
        await _cameraService.restartCameraStream(_processFrame);
      }
    } finally {
      // Always ensure processing flag is reset
      _cameraService.isProcessing = false;
    }
  }
  
  Offset _convertServerPosition(double serverX, double serverY, Size screenSize) {
    // Convert server coordinates (typically from image) to screen coordinates
    // Applying amplification factor of 2 to make indicator move twice as much as finger
    final double amplificationFactor = 2.0;
    
    // Get screen dimensions
    final double screenWidth = screenSize.width;
    final double screenHeight = screenSize.height;
    
    // Calculate the center of the screen
    final double centerX = screenWidth / 2;
    final double centerY = screenHeight / 2;
    
    // Convert normalized coordinates (0-1) to screen space
    final double normalizedX = serverX * screenWidth;
    final double normalizedY = serverY * screenHeight;
    
    // Calculate offset from center
    // Invert the X-axis direction by multiplying by -1
    final double offsetX = (normalizedX - centerX) * -1; // Invert horizontal direction
    final double offsetY = normalizedY - centerY;
    
    // Apply amplification to the offset and add back to center
    double amplifiedX = centerX + (offsetX * amplificationFactor);
    double amplifiedY = centerY + (offsetY * amplificationFactor);
    
    // Add padding for the indicator size (30px on each side)
    const double padding = 30.0;
    
    // Clamp the values to keep indicator within screen bounds
    amplifiedX = amplifiedX.clamp(padding, screenWidth - padding);
    amplifiedY = amplifiedY.clamp(padding, screenHeight - padding);
    
    return Offset(amplifiedX, amplifiedY);
  }
  
  void _handleGestureAction(String gesture, double confidenceValue) {
    // Handle ongoing call termination
    if (_hasOngoingCall && gesture == 'thumbs_down' && confidenceValue > 0.70) {
      print('Thumbs down gesture detected during ongoing call - ending call');
      _endOngoingCallWithGesture();
      return;
    }
    
    // Check for call-related gestures first when there's an incoming call
    if (_hasIncomingCall) {
      // Handle thumbs up gesture to accept call
      if (gesture == 'thumbs_up' && confidenceValue > 0.70) {
        print('Thumbs up gesture detected during incoming call - accepting call');
        _acceptCallWithGesture();
        return;
      }
      
      // Handle thumbs down gesture to reject call
      if (gesture == 'thumbs_down' && confidenceValue > 0.70) {
        print('Thumbs down gesture detected during incoming call - rejecting call');
        _rejectCallWithGesture();
        return;
      }
    }
    
    // Handle regular gestures (existing functionality)
    if (gesture == 'open_app') {
      if (_appInBackground) {
        // Show notification or use background launch for Instagram
        _backgroundService.setAppBackground(true, true);
        _instagramLauncherService.tryLaunchInstagram(
          fromGesture: true,
          inBackground: true
        );
      } else {
        // Regular foreground launch
        _tryLaunchInstagram(fromGesture: true);
      }
    } else if (gesture == 'scroll_up' || gesture == 'scroll_down') {
      final bool isScrollUp = gesture == 'scroll_up';
      if (_appInBackground) {
        _backgroundService.simulateScroll(
          scrollUp: isScrollUp,
          inBackground: true
        );
      } else {
        _backgroundService.simulateScroll(
          scrollUp: isScrollUp,
          inBackground: false
        );
      }
    } else if (gesture == 'home') {
      // Press home button and restart camera when app resumes
      _backgroundService.pressHomeButton(onPressed: () {
        // Schedule camera restart after a delay to ensure app is back in foreground
        Future.delayed(const Duration(milliseconds: 500), () {
          if (mounted && isSpatialTouchEnabled) {
            print('Restarting camera after home button press');
            _initializeCamera();
          }
        });
      });
    }
  }
  
  // Helper method to accept call with gesture
  Future<void> _acceptCallWithGesture() async {
    if (!_hasIncomingCall) return;
    
    // Show visual feedback
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('👍 Accepting call with thumbs up gesture'),
          backgroundColor: Colors.green,
          duration: Duration(seconds: 2),
        ),
      );
    }
    
    // Attempt to accept the call
    final bool success = await _callDetectionService.acceptCall();
    
    if (success) {
      print('Call accepted successfully via gesture');
    } else {
      print('Failed to accept call via gesture, falling back to simulation');
      _callDetectionService.simulateIncomingCall(isIncoming: false);
    }
  }
  
  // Helper method to reject call with gesture
  Future<void> _rejectCallWithGesture() async {
    if (!_hasIncomingCall) return;
    
    // Show visual feedback
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('👎 Rejecting call with thumbs down gesture'),
          backgroundColor: Colors.red,
          duration: Duration(seconds: 2),
        ),
      );
    }
    
    // Attempt to reject the call
    final bool success = await _callDetectionService.rejectCall();
    
    if (success) {
      print('Call rejected successfully via gesture');
    } else {
      print('Failed to reject call via gesture, falling back to simulation');
      _callDetectionService.simulateIncomingCall(isIncoming: false);
    }
  }
  
  // Helper method to end ongoing call with gesture
  Future<void> _endOngoingCallWithGesture() async {
    if (!_hasOngoingCall) return;
    
    // Show visual feedback
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('👎 Ending ongoing call with thumbs down gesture'),
          backgroundColor: Colors.red,
          duration: Duration(seconds: 2),
        ),
      );
    }
    
    // Attempt to end the call
    final bool success = await _callDetectionService.endOngoingCall();
    
    if (success) {
      print('Ongoing call ended successfully via gesture');
    } else {
      print('Failed to end ongoing call via gesture');
      _callDetectionService.simulateOngoingCall(isOngoing: false);
    }
  }
  
  // Function to check cooldown and launch Instagram if appropriate
  void _tryLaunchInstagram({bool fromGesture = false}) {
    print('Preparing to launch Instagram...');
    
    // Stop camera stream before launching
    _cameraService.stopImageStream();
    
    setState(() {
      _isLaunchingInstagram = true;
    });
    
    _instagramLauncherService.tryLaunchInstagram(
      fromGesture: fromGesture,
      inBackground: _appInBackground,
      onLaunchStart: () {
        // Already handled by setting _isLaunchingInstagram = true
      },
      onLaunchComplete: () {
        if (mounted) {
          setState(() {
            _isLaunchingInstagram = false;
          });
          
          // Force complete cleanup
          print('Disposing camera after Instagram launch');
          _cameraService.stopImageStream();
          _cameraService.dispose();
          
          // CRITICAL FIX: Force foreground mode to ensure camera restarts can work
          _appInBackground = false;
          if (Platform.isAndroid) {
            _backgroundService.setAppBackground(false, true);
          }
          
          // Restart with multiple attempts at different delays
          _scheduleMultipleCameraRestarts();
        }
      },
      onLaunchError: (error) {
        if (mounted) {
          setState(() {
            _isLaunchingInstagram = false;
          });
          
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to launch Instagram: $error'),
              backgroundColor: Colors.red,
            ),
          );
          
          // Reset background state to ensure we're in foreground mode
          _appInBackground = false;
          
          // Restart camera after error
          _restartServices();
          _initializeCamera();
        }
      },
      showFeedback: (message) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(message),
            duration: const Duration(seconds: 2),
          )
        );
      }
    );
  }

  // New method to schedule multiple camera restart attempts
  void _scheduleMultipleCameraRestarts() {
    print('Scheduling multiple camera restart attempts');
    
    // Reset background flag immediately to ensure camera can initialize
    _appInBackground = false;
    
    // Try at 1 second - quick first attempt
    Future.delayed(const Duration(seconds: 1), () {
      if (mounted && isSpatialTouchEnabled) {
        print('First camera restart attempt (1s)');
        // Force foreground mode
        _appInBackground = false;
        _backgroundService.setAppBackground(false, true);
        _restartServices();
        _initializeCamera();
      }
    });
    
    // Try again at 2.5 seconds - medium delay
    Future.delayed(const Duration(milliseconds: 2500), () {
      if (mounted && isSpatialTouchEnabled) {
        print('Second camera restart attempt (2.5s)');
        // Force foreground mode
        _appInBackground = false;
        _backgroundService.setAppBackground(false, true);
        _restartServices();
        _initializeCamera();
      }
    });
    
    // Final attempt at 5 seconds
    Future.delayed(const Duration(seconds: 5), () {
      if (mounted && isSpatialTouchEnabled) {
        print('Final camera restart attempt (5s)');
        // Force foreground mode
        _appInBackground = false;
        _backgroundService.setAppBackground(false, true);
        _restartServices();
        _initializeCamera();
      }
    });
  }

  // Add a method to restart services
  void _restartServices() {
    // Clean up existing services
    _cameraService.dispose();
    
    // Re-create services
    _cameraService = CameraService();
  }

  // Setup channel to receive camera restart requests
  void _setupCameraRestartChannel() {
    const MethodChannel systemChannel = MethodChannel('com.example.app/system');
    systemChannel.setMethodCallHandler((call) async {
      if (call.method == 'restartCamera') {
        print('🔄 EXPLICIT CAMERA RESTART: Received native request to restart camera');
        
        Map<String, dynamic> args = Map<String, dynamic>.from(call.arguments ?? {});
        String source = args['source'] ?? 'unknown';
        print('🔄 Camera restart requested from: $source');
        
        // Always force to foreground mode
        _appInBackground = false;
        
        if (mounted) {
          // Use a faster restart for home button press sources
          if (source == 'home_button_press') {
            print('🏠 HOME BUTTON RESTART: Performing fast camera restart');
            // Restart camera with high priority
            _quickRestartCamera();
          } else {
            // For other sources, do a full restart
            print('🔄 REGULAR RESTART: Performing full camera restart');
            _forceRestartCamera();
          }
          
          // Show a toast to provide user feedback
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Restarting camera...'),
              duration: Duration(milliseconds: 1000),
              backgroundColor: Colors.blue,
            ),
          );
        }
      }
      return null;
    });
  }
  
  // Quick restart for camera (less thorough but faster)
  void _quickRestartCamera() {
    print('⚡ QUICK RESTART: Fast camera restart after home button');
    
    // Reset app state
    _appInBackground = false;
    
    // Force foreground mode in native code
    if (Platform.isAndroid) {
      _backgroundService.setAppBackground(false, true);
    }
    
    // Restart camera stream without full dispose
    if (_cameraService.controller != null) {
      if (!_cameraService.controller!.value.isStreamingImages) {
        print('⚡ QUICK RESTART: Starting image stream');
        _startImageProcessing();
      } else {
        print('⚡ QUICK RESTART: Camera already streaming');
      }
    } else {
      print('⚡ QUICK RESTART: Controller null, initializing');
      _initializeCamera();
    }
    
    // Schedule a full restart if quick restart doesn't work
    Future.delayed(const Duration(seconds: 1), () {
      if (_cameraService.controller == null || 
          !_cameraService.controller!.value.isInitialized || 
          !_cameraService.controller!.value.isStreamingImages) {
        print('⚡ QUICK RESTART: Fast restart failed, trying full restart');
        _forceRestartCamera();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color.fromARGB(245, 38, 126, 117),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text('Air Touch'),
        backgroundColor: _hasIncomingCall 
            ? Colors.red.shade700 
            : (_hasOngoingCall ? Colors.green.shade700 : null),
        foregroundColor: _hasIncomingCall || _hasOngoingCall ? Colors.white : null,
        actions: [
          // Only show call buttons during incoming call
          if (_hasIncomingCall) ...[
            // Accept call button (green)
            IconButton(
              icon: const Icon(
                Icons.call,
                color: Colors.green,
              ),
              tooltip: 'Accept call',
              onPressed: () async {
                // Handle accepting the call
                print('Attempting to accept call');
                final bool success = await _callDetectionService.acceptCall();
                if (success) {
                  print('Call accepted successfully');
                } else {
                  print('Failed to accept call');
                  // Fall back to simulation if the actual call acceptance fails
                  _callDetectionService.simulateIncomingCall(isIncoming: false);
                }
              },
            ),
            // Decline call button (red)
            IconButton(
              icon: const Icon(
                Icons.call_end,
                color: Colors.red,
              ),
              tooltip: 'Decline call',
              onPressed: () async {
                // Handle declining the call
                print('Attempting to decline call');
                final bool success = await _callDetectionService.rejectCall();
                if (success) {
                  print('Call declined successfully');
                } else {
                  print('Failed to decline call');
                  // Fall back to simulation if the actual call rejection fails
                  _callDetectionService.simulateIncomingCall(isIncoming: false);
                }
              },
            ),
          ] else if (_hasOngoingCall) ...[
            // Show ongoing call UI
            const Text(
              '📱 Call in Progress',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Use 👎 to end call',
              style: TextStyle(
                fontSize: 14,
                color: Colors.white,
              ),
            ),
          ],
          if (isSpatialTouchEnabled)
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Restart camera',
              onPressed: () {
                print('Manual camera restart requested');
                _cameraService.stopImageStream();
                if (_cameraService.controller != null) {
                  _cameraService.dispose();
                }
                _initializeCamera();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Camera restarting...'), duration: Duration(seconds: 1)),
                );
              },
            ),
        ],
      ),
      body: Stack(
        children: [
          Column(
            children: [
              // Settings Panel
              SettingsPanel(
                isEnabled: isSpatialTouchEnabled,
                enableBackgroundProcessing: _enableBackgroundProcessing,
                onToggle: (value) {
                  print('Air Touch switch toggled to: $value');
                  setState(() {
                    isSpatialTouchEnabled = value;
                  });

                  if (isSpatialTouchEnabled) {
                    print('Air Touch enabled, initializing camera');
                    _initializeCamera();
                    
                    // Make sure service is started if the app goes to background
                    if (_appInBackground && Platform.isAndroid) {
                      _backgroundService.startBackgroundService();
                    }
                  } else {
                    print('Air Touch disabled, stopping camera');
                    _cameraService.stopImageStream();
                    _cameraService.dispose();
                    
                    // Stop background service
                    if (Platform.isAndroid) {
                      _backgroundService.stopBackgroundService();
                    }
                    
                    setState(() {
                      gestureDetected = false;
                      detectedGesture = null;
                      confidence = null;
                    });
                  }
                },
                onBackgroundProcessingToggle: (value) {
                  setState(() {
                    _enableBackgroundProcessing = value;
                    print('Background processing set to: $value');
                  });
                  
                  if (value && _appInBackground) {
                    // If enabling while already in background, start service
                    _backgroundService.startBackgroundService();
                  } else if (!value && _appInBackground) {
                    // If disabling while in background, stop service
                    _backgroundService.stopBackgroundService();
                  }
                },
              ),
              
              // Gesture Detection Panel
              if (isSpatialTouchEnabled)
                GestureDetectionPanel(
                  gestureDetected: gestureDetected,
                  detectedGesture: detectedGesture,
                  confidence: confidence,
                ),
              
              // Camera Preview
              if (isSpatialTouchEnabled && _cameraService.controller != null && _cameraService.controller!.value.isInitialized)
                CameraPreviewWidget(
                  controller: _cameraService.controller!,
                  gestureDetected: gestureDetected,
                ),
                
              // Debug info
              Expanded(
                child: Container(),
              ),
            ],
          ),
          
          // Instagram launch overlay
          if (_isLaunchingInstagram)
            const InstagramLaunchOverlay(),
          
          // Add touch indicator overlay with position
          TouchIndicator(
            visible: _showTouchIndicator,
            position: _fingerPosition,
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cameraService.dispose();
    _callDetectionService.dispose();
    _gestureService.dispose();
    
    // Stop background service when the app is fully closed
    if (Platform.isAndroid) {
      _backgroundService.stopBackgroundService();
    }
    
    super.dispose();
  }
}