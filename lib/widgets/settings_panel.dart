import 'package:flutter/material.dart';
import 'dart:io';

class SettingsPanel extends StatelessWidget {
  final bool isEnabled;
  final bool enableBackgroundProcessing;
  final Function(bool) onToggle;
  final Function(bool)? onBackgroundProcessingToggle;

  const SettingsPanel({
    Key? key,
    required this.isEnabled,
    this.enableBackgroundProcessing = true,
    required this.onToggle,
    this.onBackgroundProcessingToggle,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16.0),
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
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Enable Air Touch™',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              Switch(
                value: isEnabled,
                onChanged: onToggle,
              ),
            ],
          ),
          const SizedBox(height: 10),
          const Text(
              'Air Touch™ will run automatically when supported apps are launched.'),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Auto-off Timer',
                  style: TextStyle(fontWeight: FontWeight.bold)),
              DropdownButton<String>(
                value: '30 min',
                items: ['15 min', '30 min', '60 min'].map((String value) {
                  return DropdownMenuItem<String>(
                    value: value,
                    child: Text(value),
                  );
                }).toList(),
                onChanged: (String? newValue) {},
              ),
            ],
          ),
          if (Platform.isAndroid) ...[
            const SizedBox(height: 15),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Keep running in background',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                      SizedBox(height: 4),
                      Text(
                        'Required for gesture detection when app is minimized',
                        style: TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: enableBackgroundProcessing,
                  activeColor: Colors.green,
                  onChanged: onBackgroundProcessingToggle,
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
} 