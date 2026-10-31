import 'package:flutter/material.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/danasea_logo.dart';

class HomeFooter extends StatelessWidget {
  const HomeFooter({super.key});

  Widget _buildTrustItem({
    required IconData icon,
    required Color iconColor,
    required Color iconBg,
    required String title,
    required String description,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.borderSubtle.withValues(alpha: 0.7),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: iconBg,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: iconColor, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                LocalizedText(
                  title,
                  style: AppTypography.labelLg(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 3),
                LocalizedText(
                  description,
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 12, height: 1.35),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(AppShapes.radiusLgValue),
          topRight: Radius.circular(AppShapes.radiusLgValue),
        ),
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: AppShapes.gutterMobile,
        vertical: AppShapes.spaceXl,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. BRAND HEADER
          Row(
            children: [
              const DanaSeaLogo(size: 30),
              const SizedBox(width: 8),
              LocalizedText(
                'DANASEA',
                style: AppTypography.headlineSm(
                  color: AppColors.secondary,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.secondary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.verified,
                      size: 14,
                      color: AppColors.secondary,
                    ),
                    const SizedBox(width: 4),
                    LocalizedText(
                      'Biển Đà Nẵng',
                      style: AppTypography.labelSm(
                        color: AppColors.secondary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          LocalizedText(
            'Cam kết an toàn & Đồng hành cùng bạn',
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ).copyWith(fontSize: 17),
          ),
          const SizedBox(height: 4),
          LocalizedText(
            'Hệ thống quản lý trải nghiệm và cứu hộ thể thao biển hàng đầu vịnh Đà Nẵng.',
            style: AppTypography.bodySm(
              color: AppColors.onSurfaceVariant,
            ).copyWith(fontSize: 12),
          ),
          const SizedBox(height: 16),

          // 2. THREE TRUST BADGES
          _buildTrustItem(
            icon: Icons.shield_outlined,
            iconColor: const Color(0xFF0284C7),
            iconBg: const Color(0xFFE0F2FE),
            title: 'Bảo hiểm du lịch trọn gói',
            description:
                '100% vé trải nghiệm thể thao biển đều kèm gói bảo hiểm tai nạn đường thủy toàn diện.',
          ),
          const SizedBox(height: 10),
          _buildTrustItem(
            icon: Icons.safety_check_outlined,
            iconColor: const Color(0xFF0D9488),
            iconBg: const Color(0xFFCCFBF1),
            title: 'HLV cứu hộ tiêu chuẩn 1:5',
            description:
                'Huấn luyện viên sở hữu chứng chỉ cứu hộ biển, trang bị áo phao & ván cứu sinh chuyên dụng.',
          ),
          const SizedBox(height: 10),
          _buildTrustItem(
            icon: Icons.cloud_sync_outlined,
            iconColor: const Color(0xFFE11D48),
            iconBg: const Color(0xFFFFE4E6),
            title: 'Bảo vệ thời tiết & hoàn hủy linh hoạt',
            description:
                'Miễn phí dời lịch hoặc hoàn tiền 100% khi thời tiết biển động xấu theo cảnh báo an toàn.',
          ),
          const SizedBox(height: 20),

          // 3. HOTLINE SUPPORT CARD
          InkWell(
            onTap: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: LocalizedText(
                    'Đang kết nối Tổng đài hỗ trợ biển Đà Nẵng: 1900 8899',
                  ),
                  backgroundColor: AppColors.secondary,
                  duration: Duration(seconds: 2),
                ),
              );
            },
            borderRadius: BorderRadius.circular(14),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.secondary.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: AppColors.secondary.withValues(alpha: 0.25),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 36,
                    height: 36,
                    decoration: const BoxDecoration(
                      color: AppColors.secondary,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.phone_in_talk,
                      color: AppColors.onSecondary,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        LocalizedText(
                          'Hotline cứu hộ & hỗ trợ 24/7',
                          style: AppTypography.labelSm(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                        LocalizedText(
                          '1900 8899 (Miễn cước)',
                          style: AppTypography.labelLg(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        LocalizedText(
                          'Gọi ngay',
                          style: AppTypography.labelSm(
                            color: AppColors.onSecondary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(width: 2),
                        const Icon(
                          Icons.arrow_forward_ios,
                          size: 11,
                          color: AppColors.onSecondary,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),

          Divider(
            color: AppColors.borderSubtle.withValues(alpha: 0.8),
            height: 1,
          ),
          const SizedBox(height: 14),

          // 4. FOOTER INFO
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.location_on_outlined,
                    size: 16,
                    color: AppColors.onSurfaceVariant,
                  ),
                  const SizedBox(width: 4),
                  LocalizedText(
                    'Đà Nẵng, Việt Nam',
                    style: AppTypography.bodySm(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  const Icon(
                    Icons.language,
                    size: 15,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 4),
                  LocalizedText(
                    'Tiếng Việt',
                    style: AppTypography.labelMd(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Copyright
          LocalizedText(
            '© 2026 DANASEA Platform. Bản quyền thuộc về Danasea Vietnam.',
            style: AppTypography.labelSm(
              color: AppColors.outline,
            ).copyWith(fontSize: 11),
          ),
        ],
      ),
    );
  }
}
