import 'dart:async';
import 'package:flutter/services.dart';
import 'dart:io';

class CallDetectionService {
  // Singleton instance
  static final CallDetectionService _instance = CallDetectionService._internal();
  factory CallDetectionService() => _instance;
  CallDetectionService._internal();

  final MethodChannel _callChannel = const MethodChannel('com.example.app/call_detection');
  
  // Stream controller for call status notifications
  final StreamController<bool> _callStreamController = StreamController<bool>.broadcast();
  Stream<bool> get callStream => _callStreamController.stream;
  
  bool _isInitialized = false;
  bool _isListening = false;
  bool _isIncomingCall = false;
  bool _isOngoingCall = false;
  
  bool get isIncomingCall => _isIncomingCall;
  bool get isOngoingCall => _isOngoingCall;
  
  // Initialize the call detection
  Future<void> initialize() async {
    if (_isInitialized) return;
    
    print('Initializing call detection service');
    
    // Set up method channel handler
    _callChannel.setMethodCallHandler((call) async {
      print('Call detection service received method call: ${call.method}');
      
      if (call.method == 'onIncomingCall') {
        final bool isIncomingCall = call.arguments as bool;
        _isIncomingCall = isIncomingCall;
        _isOngoingCall = false; // Reset on new call
        
        print('Call detection: incoming call status changed to: $isIncomingCall');
        
        _callStreamController.add(isIncomingCall);
      } else if (call.method == 'onOngoingCall') {
        final bool isOngoingCall = call.arguments as bool;
        _isOngoingCall = isOngoingCall;
        
        print('Call detection: ongoing call status changed to: $isOngoingCall');
        
        _callStreamController.add(isOngoingCall);
      }
      return null;
    });
    
    _isInitialized = true;
    print('Call detection service initialized');
  }
  
  // Start listening for calls
  Future<void> startListening() async {
    if (!_isInitialized) {
      await initialize();
    }
    
    if (_isListening) return;
    
    if (Platform.isAndroid) {
      try {
        print('Starting call detection listener');
        await _callChannel.invokeMethod('startCallDetection');
        _isListening = true;
        print('Call detection listener started successfully');
      } catch (e) {
        print('Error starting call detection: $e');
      }
    }
  }
  
  // Stop listening for calls
  Future<void> stopListening() async {
    if (!_isListening) return;
    
    if (Platform.isAndroid) {
      try {
        print('Stopping call detection listener');
        await _callChannel.invokeMethod('stopCallDetection');
        _isListening = false;
        print('Call detection listener stopped');
      } catch (e) {
        print('Error stopping call detection: $e');
      }
    }
  }
  
  // Accept incoming call
  Future<bool> acceptCall() async {
    print('Accepting incoming call');
    if (!_isIncomingCall) {
      print('No incoming call to accept');
      return false;
    }
    
    if (Platform.isAndroid) {
      try {
        final bool result = await _callChannel.invokeMethod('acceptCall') ?? false;
        if (result) {
          _isIncomingCall = false;
          _isOngoingCall = true; // Set ongoing call flag
          _callStreamController.add(false);
        }
        return result;
      } catch (e) {
        print('Error accepting call: $e');
        return false;
      }
    }
    
    return false;
  }
  
  // Reject incoming call
  Future<bool> rejectCall() async {
    print('Rejecting incoming call');
    if (!_isIncomingCall) {
      print('No incoming call to reject');
      return false;
    }
    
    if (Platform.isAndroid) {
      try {
        final bool result = await _callChannel.invokeMethod('rejectCall') ?? false;
        if (result) {
          _isIncomingCall = false;
          _callStreamController.add(false);
        }
        return result;
      } catch (e) {
        print('Error rejecting call: $e');
        return false;
      }
    }
    
    return false;
  }
  
  // End ongoing call
  Future<bool> endOngoingCall() async {
    print('Ending ongoing call');
    if (!_isOngoingCall) {
      print('No ongoing call to end');
      return false;
    }
    
    if (Platform.isAndroid) {
      try {
        final bool result = await _callChannel.invokeMethod('endOngoingCall') ?? false;
        if (result) {
          _isOngoingCall = false;
          _callStreamController.add(false);
        }
        return result;
      } catch (e) {
        print('Error ending call: $e');
        return false;
      }
    }
    
    return false;
  }
  
  // Simulate an incoming call (for testing)
  Future<void> simulateIncomingCall({required bool isIncoming}) async {
    print('Simulating incoming call: $isIncoming');
    _isIncomingCall = isIncoming;
    _isOngoingCall = false;
    _callStreamController.add(isIncoming);
  }
  
  // Simulate an ongoing call (for testing)
  Future<void> simulateOngoingCall({required bool isOngoing}) async {
    print('Simulating ongoing call: $isOngoing');
    _isOngoingCall = isOngoing;
    _isIncomingCall = false;
    _callStreamController.add(isOngoing);
  }
  
  // Dispose the service
  void dispose() {
    stopListening();
    _callStreamController.close();
    print('Call detection service disposed');
  }
} 