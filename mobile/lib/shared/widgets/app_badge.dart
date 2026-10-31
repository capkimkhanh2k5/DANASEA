import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/l10n/app_localizations.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_shapes.dart';
import '../../core/theme/app_typography.dart';

enum AppBadgeVariant {
  vendor,
  category,
  success,
  warning,
  error,
  neutral,
}

/// Standardized Badge & Tag Element across Danasea Mobile
class AppBadge extends StatelessWidget {
  final String label;
  final IconData? leadingIcon;
  final AppBadgeVariant variant;
  final VoidCallback? onTap;
  final bool isSelected;

  const AppBadge({
    super.key,
    required this.label,
    this.leadingIcon,
    this.variant = AppBadgeVariant.neutral,
    this.onTap,
    this.isSelected = false,
  });

  @override
  Widget build(BuildContext context) {
    Color bgColor;
    Color textColor;
    Color? borderColor;

    switch (variant) {
      case AppBadgeVariant.vendor:
        bgColor = AppColors.secondary;
        textColor = AppColors.onSecondary;
        borderColor = null;
        break;
      case AppBadgeVariant.category:
        bgColor = isSelected
            ? AppColors.secondary
            : AppColors.secondaryContainer.withValues(alpha: 0.35);
        textColor = isSelected ? AppColors.onSecondary : AppColors.secondary;
        borderColor = isSelected
            ? null
            : AppColors.secondaryContainer.withValues(alpha: 0.8);
        break;
      case AppBadgeVariant.success:
        bgColor = const Color(0xFFE8F5E9);
        textColor = const Color(0xFF2E7D32);
        borderColor = const Color(0xFFA5D6A7);
        break;
      case AppBadgeVariant.warning:
        bgColor = const Color(0xFFFFF8E1);
        textColor = const Color(0xFFF57F17);
        borderColor = const Color(0xFFFFE082);
        break;
      case AppBadgeVariant.error:
        bgColor = AppColors.errorContainer.withValues(alpha: 0.4);
        textColor = AppColors.error;
        borderColor = AppColors.error.withValues(alpha: 0.3);
        break;
      case AppBadgeVariant.neutral:
        bgColor = AppColors.surfaceContainerHigh;
        textColor = AppColors.onSurfaceVariant;
        borderColor = AppColors.borderSubtle;
        break;
    }

    final content = AnimatedContainer(
      duration: AppAnimations.fast,
      curve: AppAnimations.standard,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: AppShapes.radiusFull,
        border: borderColor != null ? Border.all(color: borderColor, width: 0.8) : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (leadingIcon != null) ...[
            Icon(leadingIcon, size: 13, color: textColor),
            const SizedBox(width: 4),
          ],
          LocalizedText(
            label,
            style: AppTypography.labelSm(
              color: textColor,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );

    if (onTap == null) return content;

    return InkWell(
      onTap: () {
        HapticFeedback.selectionClick();
        onTap!();
      },
      borderRadius: AppShapes.radiusFull,
      child: content,
    );
  }
}

