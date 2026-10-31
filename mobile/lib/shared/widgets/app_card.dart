import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_shapes.dart';

/// Standardized Surface Card with micro-interaction scale and elevation
class AppCard extends StatefulWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final BorderRadius? borderRadius;
  final Color? backgroundColor;
  final Border? border;
  final List<BoxShadow>? boxShadow;

  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppShapes.spaceMd),
    this.onTap,
    this.borderRadius,
    this.backgroundColor,
    this.border,
    this.boxShadow,
  });

  @override
  State<AppCard> createState() => _AppCardState();
}

class _AppCardState extends State<AppCard> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final effectiveRadius = widget.borderRadius ?? AppShapes.radiusDefault;
    final isInteractive = widget.onTap != null;

    final cardContent = Container(
      padding: widget.padding,
      decoration: BoxDecoration(
        color: widget.backgroundColor ?? AppColors.surface,
        borderRadius: effectiveRadius,
        border: widget.border ??
            Border.all(
              color: AppColors.borderSubtle.withValues(alpha: 0.6),
              width: 0.8,
            ),
        boxShadow: widget.boxShadow ??
            [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 10,
                offset: const Offset(0, 2),
              ),
            ],
      ),
      child: widget.child,
    );

    if (!isInteractive) return cardContent;

    return GestureDetector(
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) {
        setState(() => _isPressed = false);
        HapticFeedback.selectionClick();
        widget.onTap!();
      },
      onTapCancel: () => setState(() => _isPressed = false),
      child: AnimatedScale(
        scale: _isPressed ? 0.98 : 1.0,
        duration: AppAnimations.fast,
        curve: AppAnimations.decelerate,
        child: cardContent,
      ),
    );
  }
}

