import 'package:flutter/material.dart';

class TouchIndicator extends StatefulWidget {
  final bool visible;
  final Offset position;

  const TouchIndicator({
    super.key,
    required this.visible,
    required this.position,
  });

  @override
  _TouchIndicatorState createState() => _TouchIndicatorState();
}

class _TouchIndicatorState extends State<TouchIndicator> with SingleTickerProviderStateMixin {
  late AnimationController _fadeController;
  Offset _animatedPosition = Offset.zero;
  
  // For position smoothing
  final double _smoothingFactor = 0.3; // Lower = smoother but slower tracking

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _animatedPosition = widget.position;
  }

  @override
  void didUpdateWidget(TouchIndicator oldWidget) {
    // Handle visibility changes
    if (widget.visible != oldWidget.visible) {
      widget.visible ? _fadeController.forward() : _fadeController.reverse();
    }
    
    // Smoothly update position
    if (widget.position != oldWidget.position && widget.visible) {
      setState(() {
        // Apply smoothing by moving a percentage of the distance to the target
        final double dx = widget.position.dx - _animatedPosition.dx;
        final double dy = widget.position.dy - _animatedPosition.dy;
        
        _animatedPosition = Offset(
          _animatedPosition.dx + (dx * _smoothingFactor),
          _animatedPosition.dy + (dy * _smoothingFactor)
        );
      });
    }
    
    super.didUpdateWidget(oldWidget);
  }

  @override
  void dispose() {
    _fadeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Always move toward target position
    if (widget.visible) {
      Future.microtask(() {
        if (!mounted) return;
        
        final double dx = widget.position.dx - _animatedPosition.dx;
        final double dy = widget.position.dy - _animatedPosition.dy;
        
        // Only update if the difference is significant
        if (dx.abs() > 0.5 || dy.abs() > 0.5) {
          setState(() {
            _animatedPosition = Offset(
              _animatedPosition.dx + (dx * _smoothingFactor),
              _animatedPosition.dy + (dy * _smoothingFactor)
            );
          });
        }
      });
    }
    
    return Stack(
      children: [
        Positioned(
          left: _animatedPosition.dx - 30,  // Use animated position
          top: _animatedPosition.dy - 30,
          child: FadeTransition(
            opacity: _fadeController,
            child: ScaleTransition(
              scale: _fadeController.drive(CurveTween(curve: Curves.easeOut)),
              child: Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.3),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.green, width: 2),
                  boxShadow: const [
                    BoxShadow(
                      color: Colors.greenAccent,
                      blurRadius: 10,
                      spreadRadius: 1,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
} 