import 'package:camera/camera.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'dart:convert';
import 'dart:typed_data';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'dart:io';

// Move typedefs to the file level
typedef GestureDetectedCallback = void Function(bool detected, String? gesture,
    double? confidence, Map<String, double>? position // Add position parameter
    );
typedef GestureActionCallback = void Function(
    String gesture, double confidence);

class GestureService {
  bool isProcessing = false;
  // Replace hardcoded URL with dynamic configuration
  String _serverUrl = 'http://192.168.30.187:8080/upload_frame';
  
  // Add server connectivity flags
  bool _serverConnectionLost = false;
  int _consecutiveErrors = 0; 
  int _maxConsecutiveErrors = 3;
  late Timer _reconnectTimer;
  
  // Add gesture debouncing to prevent multiple rapid triggers
  final Map<String, DateTime> _lastGestureTime = {};
  static const int _gestureCooldownMs = 2000; // 2 second cooldown between same gesture
  
  // Getter for server URL
  String get serverUrl => _serverUrl;
  
  // Method to update server URL
  void updateServerUrl(String newUrl) {
    print('Updating server URL from $_serverUrl to $newUrl');
    _serverUrl = newUrl;
    _serverConnectionLost = false;
    _consecutiveErrors = 0;
  }
  
  // Constructor
  GestureService() {
    // Try to determine the local network IP automatically
    _initServerUrl();
    // Start a reconnect timer that periodically checks server connection
    _startReconnectTimer();
  }
  
  void _startReconnectTimer() {
    _reconnectTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      if (_serverConnectionLost) {
        _pingServer();
      }
    });
  }
  
  Future<void> _pingServer() async {
    try {
      final response = await http.get(Uri.parse(_serverUrl.replaceAll('upload_frame', 'health')))
          .timeout(const Duration(seconds: 2));
      
      if (response.statusCode == 200) {
        print('Server connection restored!');
        _serverConnectionLost = false;
        _consecutiveErrors = 0;
      }
    } catch (e) {
      // Keep trying
      print('Failed to ping server: $e');
    }
  }
  
  Future<void> _initServerUrl() async {
    // List of common server URLs to try
    final List<String> possibleUrls = [
      'http://192.168.30.187:8080/upload_frame',  // Current IP
      'http://192.168.1.10:8080/upload_frame',    // Alternative IP
      'http://localhost:8080/upload_frame',       // Local machine
      'http://127.0.0.1:8080/upload_frame',       // Loopback
      'http://10.0.2.2:8080/upload_frame',        // Android emulator to host
    ];
    
    // Add common network patterns
    try {
      // Add common IPs in the 192.168.30.x subnet
      for (int i = 1; i < 255; i++) {
        if (i % 10 == 0 || i == 187) {  // Check every 10th IP and specifically 187
          possibleUrls.add('http://192.168.30.$i:8080/upload_frame');
        }
      }
      
      // Add common IPs in the 192.168.1.x subnet
      for (int i = 1; i < 255; i++) {
        if (i % 10 == 0) {
          possibleUrls.add('http://192.168.1.$i:8080/upload_frame');
        }
      }
      
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLinkLocal: false,
      );
      
      for (var interface in interfaces) {
        for (var addr in interface.addresses) {
          // Skip loopback addresses
          if (addr.address.startsWith('192.168.') || 
              addr.address.startsWith('10.') || 
              addr.address.startsWith('172.')) {
            print('Detected local IP: ${addr.address}');
            possibleUrls.add('http://${addr.address}:8080/upload_frame');
          }
        }
      }
    } catch (e) {
      print('Error detecting network interfaces: $e');
    }
    
    // Try all possible URLs to find a working server
    for (String url in possibleUrls) {
      print('Trying server URL: $url');
      try {
        final testResponse = await http.get(
          Uri.parse(url.replaceAll('upload_frame', 'health')),
        ).timeout(const Duration(seconds: 1));
        
        if (testResponse.statusCode == 200) {
          updateServerUrl(url);
          print('Server auto-detected at: $url');
          return;
        }
      } catch (e) {
        // Continue checking next URL
        print('Failed to connect to $url: $e');
      }
    }
    
    // If none of the URLs worked, keep the default
    print('Could not auto-detect server. Using default: $_serverUrl');
  }
  
  // Logging control - set to false to reduce console output for better performance
  final bool verboseLogging = false;

  Future<void> processAndSendFrame({
    required CameraImage image,
    required Function(CameraImage) imageConverter,
    required GestureDetectedCallback onGestureDetected,
    required GestureActionCallback onGestureAction,
    required bool inBackground,
  }) async {
    if (isProcessing) {
      return;
    }

    isProcessing = true;

    try {
      if (verboseLogging) {
        print('Processing frame: ${DateTime.now()} (background: $inBackground)');
      }

      // Convert CameraImage to bytes
      final bytes = await imageConverter(image);
      
      // Skip sending if conversion failed (returned empty buffer)
      if (bytes.isEmpty) {
        print('Frame conversion failed, skipping server upload');
        return;
      }
      
      if (verboseLogging) {
        print('Frame converted to JPEG - Size: ${(bytes.length / 1024).toStringAsFixed(2)} KB');
      }

      // Skip server upload if connection is lost and retries have failed
      if (_serverConnectionLost) {
        if (verboseLogging) {
          print('Skipping server upload - connection is down');
        }
        return;
      }

      // Send to server with timeout
      final response = await _sendFrameToServer(bytes, inBackground)
          .timeout(const Duration(seconds: 3), onTimeout: () {
        print('⚠️ Server request timeout after 3 seconds');
        _handleServerConnectionError();
        throw TimeoutException('Server request timed out');
      });

      if (response.statusCode == 200) {
        // Reset error counter on success
        _consecutiveErrors = 0;
        _serverConnectionLost = false;
        
        final jsonResponse = json.decode(response.body);

        final bool isDetected = jsonResponse['gesture_detected'] ?? false;
        final String? gesture = jsonResponse['detected_gesture'];
        final double? gestureConfidence = jsonResponse['confidence']?.toDouble();

        // Extract position data
        final positionData = jsonResponse['position'] != null
            ? _convertToDoubleMap(jsonResponse['position'])
            : null;

        // Log only if a gesture is detected or in verbose mode
        if (isDetected || verboseLogging) {
          final logMessage = isDetected
              ? 'Server response: Gesture detected - $gesture (${(gestureConfidence! * 100).toStringAsFixed(1)}%)'
              : 'Server response: No gesture detected';
          print(logMessage);

          if (positionData != null && isDetected) {
            print('Finger position: x=${positionData['x']}, y=${positionData['y']}');
          }
        }

        // Update the caller with detection results
        onGestureDetected(isDetected, gesture, gestureConfidence, positionData);

        // Process detected gestures
        if (isDetected && gesture != null && gestureConfidence != null) {
          _handleGesture(gesture, gestureConfidence, inBackground, onGestureAction);
        }
      } else {
        _consecutiveErrors++;
        if (_consecutiveErrors >= _maxConsecutiveErrors) {
          _handleServerConnectionError();
        }
        
        if (verboseLogging) {
          print('Frame upload failed: ${response.statusCode}');
        }
      }
    } catch (e) {
      print('Error processing frame: $e (background: $inBackground)');
      
      // Handle timeout or network errors specifically
      if (e is TimeoutException || e.toString().contains('timeout') || e.toString().contains('SocketException')) {
        print('Network or timeout error - may need to check server connection');
        _handleServerConnectionError();
        // Propagate the error up so camera service can handle it
        rethrow;
      }
    } finally {
      isProcessing = false;
    }
  }
  
  void _handleServerConnectionError() {
    _consecutiveErrors++;
    if (_consecutiveErrors >= _maxConsecutiveErrors) {
      if (!_serverConnectionLost) {
        print('⚠️ SERVER CONNECTION LOST after $_maxConsecutiveErrors consecutive errors');
        print('Will continue trying to reconnect in background');
        _serverConnectionLost = true;
      }
    }
  }

  Future<http.Response> _sendFrameToServer(
      Uint8List bytes, bool inBackground) async {
    try {
      final uri = Uri.parse(_serverUrl);
      final request = http.MultipartRequest('POST', uri)
        ..files.add(http.MultipartFile.fromBytes(
          'file',
          bytes,
          filename: 'frame.jpg',
          contentType: MediaType('image', 'jpeg'),
        ));

      if (verboseLogging) {
        print('Sending frame to server...');
      }
      final streamedResponse = await request.send();
      return await http.Response.fromStream(streamedResponse);
    } catch (e) {
      print('Error sending frame to server: $e');
      _handleServerConnectionError();
      rethrow;
    }
  }

  void _handleGesture(String gesture, double confidence, bool inBackground,
      GestureActionCallback onGestureAction) {
    const double standardThreshold = 0.70; // 70% confidence threshold for most gestures
    
    // Set gesture-specific thresholds
    double threshold;
    if (gesture == 'play_pause') {
      threshold = 0.15; // Much lower threshold specifically for play_pause gesture (15%)
    } else if (gesture == 'forward') {
      threshold = 0.20; // Lower threshold for forward gesture (20%)
    } else if (gesture == 'backward') {
      threshold = 0.20; // Lower threshold for backward gesture (20%)
    } else {
      threshold = standardThreshold;
    }

    // Only proceed if confidence is above threshold
    if (confidence <= threshold) {
      if (verboseLogging) {
        print('👉 $gesture gesture confidence too low: ${(confidence * 100).toStringAsFixed(1)}%');
      }
      return;
    }
    
    // Check for debouncing - don't trigger the same gesture too frequently
    final now = DateTime.now();
    final lastTime = _lastGestureTime[gesture];
    
    if (lastTime != null) {
      final timeSinceLastTrigger = now.difference(lastTime).inMilliseconds;
      if (timeSinceLastTrigger < _gestureCooldownMs) {
        // Skip this gesture as it's too soon after the last one
        if (verboseLogging) {
          print('🕒 $gesture gesture debounced: ${timeSinceLastTrigger}ms < ${_gestureCooldownMs}ms cooldown');
        }
        return;
      }
    }
    
    // Update the last trigger time for this gesture
    _lastGestureTime[gesture] = now;
    
    // Log the detected gesture
    String emoji = '✓'; // Default emoji
    switch (gesture) {
      case 'open_app':
        emoji = '✅';
        break;
      case 'scroll_up':
      case 'scroll_down':
        emoji = '⬆️';
        break;
      case 'move':
        emoji = '🟢';
        break;
      case 'thumbs_up':
        emoji = '👍';
        break;
      case 'thumbs_down':
        emoji = '👎';
        break;
      case 'fist':
        emoji = '✊';
        break;
      case 'play_pause':
        emoji = '⏯️';
        break;
      case 'forward':
        emoji = '⏭️';
        break;
      case 'backward':
        emoji = '⏮️';
        break;
    }
    
    print('$emoji $gesture DETECTED: ${(confidence * 100).toStringAsFixed(1)}%');
    
    // Call the action handler
    onGestureAction(gesture, confidence);
  }

  Map<String, double> _convertToDoubleMap(Map<String, dynamic> input) {
    return input.map((key, value) => MapEntry(key, value.toDouble()));
  }
  
  void dispose() {
    _reconnectTimer.cancel();
  }
}
