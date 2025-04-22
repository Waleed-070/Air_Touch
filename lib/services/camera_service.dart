import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image/image.dart' as img;
import 'dart:typed_data';
import 'dart:async';
import 'dart:io';

class CameraService {
  CameraController? controller;
  List<CameraDescription>? cameras;
  bool isProcessing = false;
  bool isStreamingActive = false;
  
  // Frame rate control - frames per second
  final int targetFps = 10;
  
  // Add buffer stuck detection
  DateTime? _lastFrameProcessedTime;
  Timer? _bufferWatchdogTimer;
  final Duration bufferStuckTimeout = const Duration(seconds: 3);
  
  // Add retry counter for tracking initialization attempts
  int _initializationAttempts = 0;
  final int _maxInitializationAttempts = 3;
  
  Future<void> initialize() async {
    print('Initializing camera service (attempt: ${++_initializationAttempts})');
    
    // Reset flag to allow new initialization
    isStreamingActive = false;
    isProcessing = false;
    
    try {
      cameras = await availableCameras();
      if (cameras == null || cameras!.isEmpty) {
        print('No cameras available');
        return;
      }
      
      CameraDescription frontCamera = cameras!.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
        orElse: () => cameras![0],
      );

      // Dispose previous controller if exists
      if (controller != null) {
        await controller!.dispose();
        print('Disposed old camera controller');
      }

      print('Creating new camera controller');
      controller = CameraController(
        frontCamera,
        ResolutionPreset.high,
      );
      
      try {
        print('Initializing camera controller');
        await controller!.initialize();
        print('Camera controller initialized');
        
        // Reset retry counter on success
        _initializationAttempts = 0;
      } catch (e) {
        print('Error initializing camera: $e');
        
        // Retry initialization if within retry limit
        if (_initializationAttempts < _maxInitializationAttempts) {
          print('Retrying camera initialization...');
          await Future.delayed(const Duration(milliseconds: 500));
          return initialize();
        } else {
          print('Maximum initialization attempts reached. Giving up.');
          _initializationAttempts = 0;
        }
      }
    } catch (e) {
      print('Camera initialization error: $e');
      
      // Retry initialization if within retry limit
      if (_initializationAttempts < _maxInitializationAttempts) {
        print('Retrying camera initialization after error...');
        await Future.delayed(const Duration(milliseconds: 500));
        return initialize();
      } else {
        print('Maximum initialization attempts reached. Giving up.');
        _initializationAttempts = 0;
      }
    }
  }

  void startImageStream(Function(CameraImage) onImageAvailable) {
    if (controller == null || !controller!.value.isInitialized) {
      print('Cannot start image stream: controller null or not initialized');
      return;
    }
    
    if (controller!.value.isStreamingImages) {
      print('Image stream already active, skipping');
      return; // Already streaming
    }
    
    print('Starting camera image stream at $targetFps fps');
    
    // Calculate frame interval in milliseconds (e.g., 10fps = 100ms between frames)
    final int frameIntervalMs = (1000 / targetFps).round();
    int lastProcessedTime = DateTime.now().millisecondsSinceEpoch;
    isStreamingActive = true;
    _lastFrameProcessedTime = DateTime.now();

    try {
      controller!.startImageStream((CameraImage image) {
        final currentTime = DateTime.now().millisecondsSinceEpoch;
        final elapsedMs = currentTime - lastProcessedTime;
        
        // Process frame if enough time has passed and we're not already processing
        if (elapsedMs >= frameIntervalMs && !isProcessing) {
          onImageAvailable(image);
          lastProcessedTime = currentTime;
          _lastFrameProcessedTime = DateTime.now();
        }
      });
      print('Camera stream started successfully');
      
      // Start the buffer watchdog timer
      _startBufferWatchdog(onImageAvailable);
      
    } catch (e) {
      print('Error starting image stream: $e');
      isStreamingActive = false;
    }
  }

  void _startBufferWatchdog(Function(CameraImage) onImageAvailable) {
    // Cancel any existing timer
    _bufferWatchdogTimer?.cancel();
    
    // Create a new timer that checks if frames are being processed
    _bufferWatchdogTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      if (!isStreamingActive) {
        timer.cancel();
        return;
      }
      
      // Check if we haven't received frames for too long
      if (_lastFrameProcessedTime != null) {
        final timeSinceLastFrame = DateTime.now().difference(_lastFrameProcessedTime!);
        
        if (timeSinceLastFrame > bufferStuckTimeout) {
          print('⚠️ BUFFER STUCK DETECTED! No frames processed for ${timeSinceLastFrame.inSeconds} seconds');
          print('Attempting to restart camera stream...');
          
          // Reset the streaming flag to prevent any incorrect state
          isStreamingActive = false;
          isProcessing = false;
          
          // Restart the camera stream with more forceful cleanup
          await stopImageStream();
          await Future.delayed(const Duration(milliseconds: 500));
          if (controller != null) {
            try {
              await controller!.dispose();
              print('Disposed controller due to buffer stuck');
            } catch (e) {
              print('Error disposing controller: $e');
            }
            controller = null;
          }
          
          // Reinitialize from scratch
          await initialize();
          
          // Start streaming again
          if (controller != null && controller!.value.isInitialized) {
            print('Restarting image stream after buffer recovery');
            startImageStream(onImageAvailable);
          } else {
            print('Failed to restart camera stream - controller not initialized');
          }
        }
      }
    });
  }
  
  // Method to restart the camera stream when it gets stuck
  Future<void> restartCameraStream(Function(CameraImage) onImageAvailable) async {
    // Stop the existing stream
    await stopImageStream();
    
    // Small delay to ensure resources are released
    await Future.delayed(const Duration(milliseconds: 500));
    
    // Try to reinitialize
    await initialize();
    
    // Start streaming again
    if (controller != null && controller!.value.isInitialized) {
      startImageStream(onImageAvailable);
    } else {
      print('Failed to restart camera stream - controller not initialized');
    }
  }

  Future<void> stopImageStream() async {
    try {
      // Stop the buffer watchdog timer
      _bufferWatchdogTimer?.cancel();
      _bufferWatchdogTimer = null;
      
      if (controller != null && controller!.value.isStreamingImages) {
        print('Stopping camera image stream');
        await controller!.stopImageStream();
        isStreamingActive = false;
        print('Camera stream stopped');
      } else {
        print('No active stream to stop');
      }
    } catch (e) {
      print('Error stopping image stream: $e');
    }
  }

  Future<Uint8List> convertYUV420toJpeg(CameraImage image) async {
    try {
      print('Starting YUV to JPEG conversion...');
      final width = image.width;
      final height = image.height;

      img.Image imgLib = img.Image(width: width, height: height);

      final yBuffer = image.planes[0].bytes;
      final uBuffer = image.planes[1].bytes;
      final vBuffer = image.planes[2].bytes;

      final yRowStride = image.planes[0].bytesPerRow;
      final uvRowStride = image.planes[1].bytesPerRow;
      final uvPixelStride = image.planes[1].bytesPerPixel!;

      for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
          final yIndex = h * yRowStride + w;
          final uvIndex = (h ~/ 2) * uvRowStride + (w ~/ 2) * uvPixelStride;

          final y = yBuffer[yIndex];
          final u = uBuffer[uvIndex];
          final v = vBuffer[uvIndex];

          int r = (y + 1.402 * (v - 128)).round().clamp(0, 255);
          int g = (y - 0.344136 * (u - 128) - 0.714136 * (v - 128))
              .round()
              .clamp(0, 255);
          int b = (y + 1.772 * (u - 128)).round().clamp(0, 255);

          imgLib.setPixelRgb(w, h, r, g, b);
        }
      }

      // Correct rotation using copyRotate
      imgLib = img.copyRotate(imgLib, angle: 270);
      print('Image rotation applied (-90 degrees)');

      final jpegBytes = img.encodeJpg(imgLib, quality: 85);
      final compressedBytes = await FlutterImageCompress.compressWithList(
        jpegBytes,
        quality: 80,
      );

      return compressedBytes;
    } catch (e) {
      print('Error in YUV conversion: $e');
      isProcessing = false; // Reset processing flag if error occurs
      
      if (e.toString().contains('timeout') || e.toString().contains('TIMED_OUT')) {
        print('⚠️ Buffer timeout detected - will try to restart camera on next frame');
      }
      
      // Just return an empty buffer so the app doesn't crash
      return Uint8List(0);
    }
  }

  void dispose() {
    _bufferWatchdogTimer?.cancel();
    stopImageStream();
    controller?.dispose();
    controller = null;
  }
} 