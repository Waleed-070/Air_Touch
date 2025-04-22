import 'package:flutter/material.dart';

class GestureDetectionPanel extends StatelessWidget {
  final bool gestureDetected;
  final String? detectedGesture;
  final double? confidence;
  
  const GestureDetectionPanel({
    Key? key, 
    required this.gestureDetected,
    this.detectedGesture,
    this.confidence,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      padding: const EdgeInsets.all(12.0),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 4,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Available Gestures',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
          ),
          // const SizedBox(height: 10),
          // Row(
          //   mainAxisAlignment: MainAxisAlignment.spaceBetween,
          //   children: [
          //     const Text('Status:'),
          //     Text(
          //       gestureDetected ? 'Active' : 'Standby',
          //       style: TextStyle(
          //         color: gestureDetected ? Colors.green : Colors.grey,
          //         fontWeight: FontWeight.bold,
          //       ),
          //     ),
          //   ],
          // ),
          // const SizedBox(height: 8),
          // Row(
          //   mainAxisAlignment: MainAxisAlignment.spaceBetween,
          //   children: [
          //     const Text('Detected Gesture:'),
          //     Text(
          //       detectedGesture ?? 'None',
          //       style: TextStyle(
          //         color: detectedGesture != null
          //             ? Colors.black
          //             : Colors.grey,
          //         fontWeight: FontWeight.bold,
          //       ),
          //     ),
          //   ],
          // ),
          // if (confidence != null) ...[
          //   const SizedBox(height: 8),
          //   Row(
          //     mainAxisAlignment: MainAxisAlignment.spaceBetween,
          //     children: [
          //       const Text('Confidence:'),
          //       Text(
          //         '${(confidence! * 100).toStringAsFixed(1)}%',
          //         style: const TextStyle(
          //           fontWeight: FontWeight.bold,
          //         ),
          //       ),
          //     ],
          //   ),
          // ],
          // const SizedBox(height: 10),
          // const Text('Available Gestures:',
          //     style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 3),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildGestureIndicator('Thumbs Up', 'thumbs_up'),
               _buildGestureIndicator('Move', 'move'),
              _buildGestureIndicator('Thumbs Down', 'thumbs_down'),
             
              // _buildGestureIndicator('Click', 'click'),
              
            ],
          ),
          const SizedBox(height: 3),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildGestureIndicator('Scroll Down', 'scroll_down'),
              _buildGestureIndicator('Open App', 'open_app'),
              _buildGestureIndicator('Scroll Up', 'scroll_up'),

            ],
          ),
          // const SizedBox(height: 3),
          // Row(
          //   mainAxisAlignment: MainAxisAlignment.spaceAround,
          //   children: [

                

          //   ],
          // ),
          if (detectedGesture == 'open_app') ...[
            const SizedBox(height: 15),
            const Center(
              child: Text(
                'This gesture will open Instagram',
                style: TextStyle(
                  color: Colors.green,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ],
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildGestureIndicator(String label, String gestureType) {
    final bool isActive = detectedGesture == gestureType;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isActive
            ? Colors.green.withOpacity(0.2)
            : Colors.grey.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: isActive ? Colors.green : Colors.grey.withOpacity(0.3),
          width: 1,
        ),
      ),
      child: Column(
        children: [
          Text(
            label,
            style: TextStyle(
              fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
              color: isActive ? Colors.green.shade800 : Colors.black54,
            ),
          ),
          if (isActive && confidence != null)
            Text(
              '${(confidence! * 100).toStringAsFixed(1)}%',
              style: TextStyle(
                fontSize: 12,
                color: Colors.green.shade800,
              ),
            ),
        ],
      ),
    );
  }
} 