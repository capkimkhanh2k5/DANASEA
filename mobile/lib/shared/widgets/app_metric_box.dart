import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/l10n/app_localizations.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';

/// Standardized Metric/Spec Box for Experience Details & Service Cards
class AppMetricBox extends StatefulWidget {
  final IconData icon;
  final String value;
  final String? label;
  final VoidCallback? onTap;
  final Color? iconColor;

  const AppMetricBox({
    super.key,
    required this.icon,
    required this.value,
    this.label,
    this.onTap,
    this.iconColor,
  });

  @override
  State<AppMetricBox> createState() => _AppMetricBoxState();
}

class _AppMetricBoxState extends State<AppMetricBox> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTapDown: widget.onTap != null ? (_) => setState(() => _isPressed = true) : null,
        onTapUp: widget.onTap != null
            ? (_) {
                setState(() => _isPressed = false);
                HapticFeedback.selectionClick();
                widget.onTap!();
              }
            : null,
        onTapCancel: widget.onTap != null ? () => setState(() => _isPressed = false) : null,
        child: AnimatedScale(
          scale: _isPressed ? 0.94 : 1.0,
          duration: AppAnimations.fast,
          curve: AppAnimations.decelerate,
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 13, horizontal: 4),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: AppColors.borderSubtle,
                width: 0.8,
              ),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  widget.icon,
                  size: 19,
                  color: widget.iconColor ?? AppColors.secondary,
                ),
                const SizedBox(height: 6),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: LocalizedText(
                      widget.value,
                      maxLines: 1,
                      style: AppTypography.labelSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w600,
                      ).copyWith(fontSize: 11.5),
                    ),
                  ),
                ),
                if (widget.label != null && widget.label!.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  LocalizedText(
                    widget.label!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: AppTypography.bodySm(
                      color: AppColors.onSurfaceVariant,
                    ).copyWith(fontSize: 10),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
