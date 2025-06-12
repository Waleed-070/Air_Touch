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
    final gestures = [
      {'label': 'Thumbs Up', 'type': 'thumbs_up'},
      {'label': 'Move', 'type': 'move'},
      {'label': 'Thumbs Down', 'type': 'thumbs_down'},
      {'label': 'Scroll Down', 'type': 'scroll_down'},
      {'label': 'Open App', 'type': 'open_app'},
      {'label': 'Scroll Up', 'type': 'scroll_up'},
      {'label': 'Fist (Volume)', 'type': 'fist'},
      {'label': 'Click', 'type': 'click'},
      {'label': 'Swipe Left', 'type': 'swipe_left'},
      {'label': 'Play/Pause', 'type': 'play_pause'},
      {'label': 'Swipe Right', 'type': 'swipe_right'},
      {'label': 'Backward', 'type': 'backward'},
      {'label': 'Forward', 'type': 'forward'},
    ];

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
        children: [
          const Text(
            'Available Gestures',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8.0,
            runSpacing: 8.0,
            alignment: WrapAlignment.center,
            children: gestures.map((gesture) {
              return _buildGestureIndicator(
                  gesture['label']!, gesture['type']!);
            }).toList(),
          ),
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