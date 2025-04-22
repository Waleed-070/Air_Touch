import 'package:flutter/material.dart';
import 'dart:async';

class IncomingCallNotification extends StatefulWidget {
  final bool isVisible;
  
  const IncomingCallNotification({
    super.key,
    required this.isVisible,
  });

  @override
  State<IncomingCallNotification> createState() => _IncomingCallNotificationState();
}

class _IncomingCallNotificationState extends State<IncomingCallNotification> 
    with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  late Animation<double> _glowAnimation;
  late Animation<double> _scaleAnimation;
  
  @override
  void initState() {
    super.initState();
    
    // Initialize animation controller
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(reverse: true);
    
    // Create glow animation
    _glowAnimation = Tween<double>(begin: 1.0, end: 8.0).animate(
      CurvedAnimation(
        parent: _animationController,
        curve: Curves.easeInOut,
      ),
    );
    
    // Create scale animation
    _scaleAnimation = Tween<double>(begin: 0.95, end: 1.05).animate(
      CurvedAnimation(
        parent: _animationController,
        curve: Curves.easeInOut,
      ),
    );
  }

  @override
  void didUpdateWidget(IncomingCallNotification oldWidget) {
    super.didUpdateWidget(oldWidget);
    
    // Restart animation when becoming visible
    if (!oldWidget.isVisible && widget.isVisible) {
      _animationController.reset();
      _animationController.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    if (!widget.isVisible) {
      return const SizedBox.shrink(); // Hide the widget when not visible
    }
    
    return Positioned(
      top: 80.0, // Position it below the app bar
      left: 0,
      right: 0,
      child: Center(
        child: AnimatedBuilder(
          animation: _animationController,
          builder: (context, child) {
            return Transform.scale(
              scale: _scaleAnimation.value,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.8),
                  borderRadius: BorderRadius.circular(24.0),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.red.withOpacity(0.7),
                      blurRadius: _glowAnimation.value * 8,
                      spreadRadius: _glowAnimation.value * 2,
                    ),
                  ],
                ),
                child: child,
              ),
            );
          },
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.phone_in_talk,
                color: Colors.white,
                size: 32.0,
              ),
              SizedBox(width: 16.0),
              Text(
                'INCOMING CALL',
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 22.0,
                  letterSpacing: 1.5,
                  shadows: [
                    Shadow(
                      blurRadius: 5.0,
                      color: Colors.black45,
                      offset: Offset(0, 2),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
} 