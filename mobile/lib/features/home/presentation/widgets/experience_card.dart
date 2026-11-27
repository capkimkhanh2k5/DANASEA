import '../../../../core/models/danasea_models.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../domain/models/home_models.dart';

class ExperienceCard extends StatelessWidget {
  final ExperienceItem item;
  final VoidCallback? onTap;
  final VoidCallback? onFavoriteToggle;

  const ExperienceCard({
    super.key,
    required this.item,
    this.onTap,
    this.onFavoriteToggle,
  });

  String _formatPrice(int price) {
    // 280000 -> 280.000 đ
    final str = price.toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i > 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusLg,
        boxShadow: AppShapes.shadowLevel1,
        border: Border.all(
          color: AppColors.borderSubtle.withValues(alpha: 0.6),
          width: 0.8,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Image with 4:3 ratio + overlays
            AspectRatio(
              aspectRatio: 4 / 3,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.network(
                    item.imageUrl,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      return Container(
                        color: AppColors.surfaceContainerHighest,
                        child: const Center(
                          child: Icon(Icons.broken_image, color: AppColors.outline),
                        ),
                      );
                    },
                  ),
                  // Badge (top-left): Hide when none, Verified (emerald), Top Rated (amber)
                  if (item.vendorBadge != VendorBadgeTier.none)
                    Positioned(
                      top: 12,
                      left: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: item.vendorBadge == VendorBadgeTier.topRated
                              ? const Color(0xFFD97706)
                              : AppColors.secondary.withValues(alpha: 0.90),
                          borderRadius: AppShapes.radiusFull,
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              item.vendorBadge == VendorBadgeTier.topRated
                                  ? Icons.workspace_premium
                                  : Icons.verified,
                              size: 14,
                              color: Colors.white,
                            ),
                            const SizedBox(width: 4),
                            LocalizedText(
                              item.vendorBadge == VendorBadgeTier.topRated
                                  ? 'Đối tác hàng đầu'
                                  : 'Đã xác minh',
                              style: AppTypography.labelSm(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  // Favorite Heart Button (top-right)
                  Positioned(
                    top: 12,
                    right: 12,
                    child: InkWell(
                      onTap: onFavoriteToggle,
                      borderRadius: BorderRadius.circular(18),
                      child: Container(
                        width: 36,
                        height: 36,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLowest.withValues(alpha: 0.85),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          item.isFavorite ? Icons.favorite : Icons.favorite_border,
                          size: 20,
                          color: item.isFavorite ? AppColors.primary : AppColors.onSurface,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Card Body Details
            Padding(
              padding: const EdgeInsets.all(AppShapes.spaceMd),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Location + Rating Row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          item.duration.isNotEmpty
                              ? '${item.location} • ${item.duration}'
                              : item.location,
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.star,
                            size: 16,
                            color: AppColors.primary,
                          ),
                          const SizedBox(width: 3),
                          LocalizedText(
                            item.rating.toStringAsFixed(1),
                            style: AppTypography.labelMd(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          LocalizedText(
                            ' (${item.reviewCount})',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),

                  // Title
                  LocalizedText(
                    item.title,
                    style: AppTypography.headlineSm(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Pricing & CTA Row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            LocalizedText(
                              'Giá ưu đãi',
                              style: AppTypography.labelSm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            RichText(
                              text: TextSpan(
                                children: [
                                  TextSpan(
                                    text: tr(context, 'Từ ${_formatPrice(item.price)}'),
                                    style: AppTypography.headlineSm(
                                      color: AppColors.primary,
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                                  TextSpan(
                                    text: tr(context, '/người'),
                                    style: AppTypography.bodySm(
                                      color: AppColors.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      // View Detail Button
                      InkWell(
                        onTap: onTap,
                        borderRadius: AppShapes.radiusFull,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.secondary,
                            borderRadius: AppShapes.radiusFull,
                            boxShadow: [
                              BoxShadow(
                                color: AppColors.secondary.withValues(alpha: 0.25),
                                blurRadius: 6,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: LocalizedText(
                            'Xem chi tiết',
                            style: AppTypography.labelMd(
                              color: AppColors.onSecondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
