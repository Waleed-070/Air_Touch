import 'package:flutter/material.dart';
import 'package:camera/camera.dart';

class CameraPreviewWidget extends StatelessWidget {
  final CameraController controller;
  final bool gestureDetected;
  
  const CameraPreviewWidget({
    Key? key,
    required this.controller,
    this.gestureDetected = false,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    if (!controller.value.isInitialized) {
      return const Center(
        child: Text('Camera not initialized'),
      );
    }
    
    return Positioned(
      top: 10,
      right: 20,
      child: Container(
        width: MediaQuery.of(context).size.width * 0.2,
        height: MediaQuery.of(context).size.height * 0.15,
        decoration: BoxDecoration(
          border: Border.all(
            color: gestureDetected ? Colors.green : Colors.red,
            width: 2,
          ),
          borderRadius: BorderRadius.circular(8),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.3),
              spreadRadius: 1,
              blurRadius: 3,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: CameraPreview(controller),
        ),
      ),
    );
  }
} 