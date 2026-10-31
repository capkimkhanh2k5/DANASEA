import 'package:flutter/material.dart';

/// Centralized Animation & Motion Tokens for Danasea Mobile App
class AppAnimations {
  AppAnimations._();

  // Durations
  static const Duration micro = Duration(milliseconds: 100);
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration normal = Duration(milliseconds: 250);
  static const Duration slow = Duration(milliseconds: 350);
  static const Duration modal = Duration(milliseconds: 400);

  // Curves
  static const Curve standard = Curves.easeInOutCubic;
  static const Curve decelerate = Curves.easeOutCubic;
  static const Curve accelerate = Curves.easeInCubic;
  static const Curve bounce = Curves.easeOutBack;
  static const Curve emphasized = Curves.fastOutSlowIn;
}

