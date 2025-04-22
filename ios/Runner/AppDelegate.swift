import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
  private var callDetectionChannel: FlutterMethodChannel?
  
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    let controller = window?.rootViewController as! FlutterViewController
    
    // Setup call detection channel
    callDetectionChannel = FlutterMethodChannel(
      name: "com.example.app/call_detection",
      binaryMessenger: controller.binaryMessenger)
    
    callDetectionChannel?.setMethodCallHandler { [weak self] (call, result) in
      guard let self = self else { return }
      
      switch call.method {
      case "startCallDetection":
        // iOS doesn't allow direct call detection in the same way as Android
        // This would typically require CallKit integration for VoIP apps
        print("Call detection started (limited iOS functionality)")
        result(nil)
      case "stopCallDetection":
        print("Call detection stopped (limited iOS functionality)")
        result(nil)
      case "acceptCall":
        self.handleAcceptCall(result: result)
      case "rejectCall":
        self.handleRejectCall(result: result)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
    
    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
  
  private func handleAcceptCall(result: @escaping FlutterResult) {
    // iOS restrictions prevent programmatically accepting calls
    // This would require CallKit and is limited to VoIP apps
    print("iOS does not allow programmatically accepting cellular calls")
    
    // Show an alert to the user explaining the limitation
    DispatchQueue.main.async {
      let alertController = UIAlertController(
        title: "iOS Limitation",
        message: "iOS does not allow apps to programmatically accept cellular calls. Please accept the call using the native iOS call interface.",
        preferredStyle: .alert)
      
      alertController.addAction(UIAlertAction(title: "OK", style: .default))
      
      self.window?.rootViewController?.present(alertController, animated: true)
    }
    
    // Return false to indicate we couldn't programmatically accept the call
    result(false)
  }
  
  private func handleRejectCall(result: @escaping FlutterResult) {
    // iOS restrictions prevent programmatically rejecting calls
    // This would require CallKit and is limited to VoIP apps
    print("iOS does not allow programmatically rejecting cellular calls")
    
    // Show an alert to the user explaining the limitation
    DispatchQueue.main.async {
      let alertController = UIAlertController(
        title: "iOS Limitation",
        message: "iOS does not allow apps to programmatically reject cellular calls. Please reject the call using the native iOS call interface.",
        preferredStyle: .alert)
      
      alertController.addAction(UIAlertAction(title: "OK", style: .default))
      
      self.window?.rootViewController?.present(alertController, animated: true)
    }
    
    // Return false to indicate we couldn't programmatically reject the call
    result(false)
  }
}
