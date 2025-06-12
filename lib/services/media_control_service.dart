import 'package:flutter/services.dart';
import 'dart:async';
import 'dart:io';

// Define callback types
typedef MediaStateCallback = void Function(bool isPlaying);

class MediaControlService {
  // Method channel for communication with native code
  final MethodChannel _mediaChannel = const MethodChannel('com.example.app/media_control');
  
  // State variables
  bool _isPlaying = false;
  
  // Getters
  bool get isPlaying => _isPlaying;
  
  // Stream controllers for media state
  final _mediaStateController = StreamController<bool>.broadcast();
  Stream<bool> get mediaStateStream => _mediaStateController.stream;
  
  // Constructor
  MediaControlService() {
    // Initialize state
    _checkMediaState();
  }
  
  // Check if media is currently playing
  Future<void> _checkMediaState() async {
    try {
      final result = await _mediaChannel.invokeMethod<bool>('isPlaying');
      _isPlaying = result ?? false;
      _mediaStateController.add(_isPlaying);
    } catch (e) {
      print('Error checking media state: $e');
      // Default to not playing if we can't determine
      _isPlaying = false;
      _mediaStateController.add(_isPlaying);
    }
  }
  
  // Toggle play/pause
  Future<bool> togglePlayPause() async {
    try {
      final result = await _mediaChannel.invokeMethod<bool>('playPause');
      
      // Toggle state if command was successful
      if (result == true) {
        _isPlaying = !_isPlaying;
        _mediaStateController.add(_isPlaying);
      }
      
      return result ?? false;
    } catch (e) {
      print('Error toggling media playback: $e');
      return false;
    }
  }
  
  // Skip to next track
  Future<bool> skipNext() async {
    try {
      final result = await _mediaChannel.invokeMethod<bool>('skipNext');
      return result ?? false;
    } catch (e) {
      print('Error skipping to next track: $e');
      return false;
    }
  }
  
  // Skip to previous track
  Future<bool> skipPrevious() async {
    try {
      final result = await _mediaChannel.invokeMethod<bool>('skipPrevious');
      return result ?? false;
    } catch (e) {
      print('Error skipping to previous track: $e');
      return false;
    }
  }
  
  // Dispose resources
  void dispose() {
    _mediaStateController.close();
  }
} 