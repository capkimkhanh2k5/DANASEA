import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../models/vendor_models.dart';

enum BadgeStyle {
  teal,
  coral,
  slate,
  warning,
  error,
}

extension ToBadgeStatusExt on Object {
  Object toBadgeStatus() => this;
}

class StatusBadge extends StatelessWidget {
  final String label;
  final BadgeStyle style;
  final IconData? icon;

  StatusBadge({
    super.key,
    String? label,
    dynamic status,
    BadgeStyle? style,
    this.icon,
  })  : label = label ?? _extractLabel(status),
        style = style ?? _extractStyle(status);

  static String _extractLabel(dynamic status) {
    if (status == null) return '';
    if (status is SubOrderStatus) {
      switch (status) {
        case SubOrderStatus.pending:
          return 'Chờ xác nhận';
        case SubOrderStatus.confirmed:
          return 'Đã xác nhận';
        case SubOrderStatus.completed:
          return 'Hoàn thành';
        case SubOrderStatus.rejected:
          return 'Đã từ chối';
        case SubOrderStatus.cancelled:
          return 'Đã hủy';
        case SubOrderStatus.refunded:
          return 'Hoàn tiền';
      }
    }
    if (status is DocumentReviewStatus) {
      return status.labelVi;
    }
    if (status is SettlementStatus) {
      return status.labelVi;
    }
    if (status is PayoutRequestStatus) {
      return status.labelVi;
    }
    if (status is DisputeStatus) {
      return status.labelVi;
    }
    if (status is ServiceStatus) {
      return status.labelVi;
    }
    if (status is SlotStatus) {
      return status.labelVi;
    }
    return status.toString();
  }

  static BadgeStyle _extractStyle(dynamic status) {
    if (status == null) return BadgeStyle.teal;
    if (status == SubOrderStatus.confirmed ||
        status == SubOrderStatus.completed ||
        status == DocumentReviewStatus.approved ||
        status == SettlementStatus.paid ||
        status == SettlementStatus.finalized ||
        status == PayoutRequestStatus.approved ||
        status == PayoutRequestStatus.paid ||
        status == DisputeStatus.resolved) {
      return BadgeStyle.teal;
    }
    if (status == SubOrderStatus.pending ||
        status == DocumentReviewStatus.pending ||
        status == SettlementStatus.pending ||
        status == PayoutRequestStatus.requested ||
        status == DisputeStatus.inReview ||
        status == DisputeStatus.open) {
      return BadgeStyle.coral;
    }
    if (status == SubOrderStatus.rejected ||
        status == SubOrderStatus.cancelled ||
        status == SubOrderStatus.refunded ||
        status == DocumentReviewStatus.rejected ||
        status == PayoutRequestStatus.rejected ||
        status == DisputeStatus.rejected) {
      return BadgeStyle.error;
    }
    return BadgeStyle.slate;
  }

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;

    switch (style) {
      case BadgeStyle.teal:
        bg = AppColors.secondaryContainer.withAlpha(120);
        fg = AppColors.secondary;
        break;
      case BadgeStyle.coral:
        bg = AppColors.primaryFixed;
        fg = AppColors.primary;
        break;
      case BadgeStyle.slate:
        bg = AppColors.surfaceContainerHigh;
        fg = AppColors.onSurfaceVariant;
        break;
      case BadgeStyle.warning:
        bg = const Color(0xFFFFE082);
        fg = const Color(0xFF6D4C41);
        break;
      case BadgeStyle.error:
        bg = AppColors.errorContainer;
        fg = AppColors.onErrorContainer;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(9999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: fg),
            const SizedBox(width: 4),
          ],
          Flexible(
            child: LocalizedText(
              label,
              style: AppTypography.labelSm(color: fg).copyWith(
                fontWeight: FontWeight.w700,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
