import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_shapes.dart';
import '../../core/theme/app_typography.dart';

enum AppButtonVariant { primary, secondary, frosted, coral, outline, dark }

class AppPillButton extends StatefulWidget {
  final String label;
  final VoidCallback? onPressed;
  final IconData? leadingIcon;
  final IconData? trailingIcon;
  final AppButtonVariant variant;
  final double height;
  final double? width;
  final double fontSize;
  final bool isLoading;

  const AppPillButton({
    super.key,
    required this.label,
    this.onPressed,
    this.leadingIcon,
    this.trailingIcon,
    this.variant = AppButtonVariant.primary,
    this.height = 48.0,
    this.width,
    this.fontSize = 14.0,
    this.isLoading = false,
  });

  @override
  State<AppPillButton> createState() => _AppPillButtonState();
}

class _AppPillButtonState extends State<AppPillButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;
    Border? border;

    switch (widget.variant) {
      case AppButtonVariant.primary:
        bg = AppColors.secondary;
        fg = AppColors.onSecondary;
        break;
      case AppButtonVariant.secondary:
        bg = AppColors.surfaceContainerLow;
        fg = AppColors.onSurface;
        break;
      case AppButtonVariant.frosted:
        bg = Colors.white.withValues(alpha: 0.2);
        fg = AppColors.inverseOnSurface;
        break;
      case AppButtonVariant.coral:
        bg = AppColors.primaryContainer;
        fg = AppColors.onPrimary;
        break;
      case AppButtonVariant.outline:
        bg = Colors.transparent;
        fg = AppColors.secondary;
        border = Border.all(color: AppColors.borderSubtle);
        break;
      case AppButtonVariant.dark:
        bg = AppColors.navyAnchor;
        fg = Colors.white;
        break;
    }

    final isEnabled = widget.onPressed != null && !widget.isLoading;

    return GestureDetector(
      onTapDown: isEnabled ? (_) => setState(() => _isPressed = true) : null,
      onTapUp: isEnabled
          ? (_) {
              setState(() => _isPressed = false);
              HapticFeedback.lightImpact();
              widget.onPressed!();
            }
          : null,
      onTapCancel: isEnabled ? () => setState(() => _isPressed = false) : null,
      child: AnimatedScale(
        scale: _isPressed ? 0.96 : 1.0,
        duration: AppAnimations.fast,
        curve: AppAnimations.decelerate,
        child: AnimatedContainer(
          duration: AppAnimations.fast,
          curve: AppAnimations.standard,
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            color: isEnabled ? bg : bg.withValues(alpha: 0.5),
            borderRadius: AppShapes.radiusFull,
            border: border,
            boxShadow: widget.variant == AppButtonVariant.primary && isEnabled
                ? [
                    BoxShadow(
                      color: AppColors.secondary.withValues(alpha: 0.25),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ]
                : widget.variant == AppButtonVariant.dark && isEnabled
                    ? [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.12),
                          blurRadius: 10,
                          offset: const Offset(0, 3),
                        ),
                      ]
                    : null,
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            mainAxisSize: widget.width == null ? MainAxisSize.min : MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (widget.isLoading) ...[
                SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: fg,
                  ),
                ),
                const SizedBox(width: 8),
              ] else if (widget.leadingIcon != null) ...[
                Icon(widget.leadingIcon, size: 18, color: fg),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: LocalizedText(
                  widget.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: AppTypography.labelLg(color: fg).copyWith(
                    fontSize: widget.fontSize,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (widget.trailingIcon != null && !widget.isLoading) ...[
                const SizedBox(width: 8),
                Icon(widget.trailingIcon, size: 18, color: fg),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
