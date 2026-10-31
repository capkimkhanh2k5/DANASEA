import 'package:flutter/material.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class AiAssistantSection extends StatelessWidget {
  final VoidCallback? onPlanWithAiTap;
  final ValueChanged<String>? onPromptSelected;
  final VoidCallback? onExploreAllTap;

  const AiAssistantSection({
    super.key,
    this.onPlanWithAiTap,
    this.onPromptSelected,
    this.onExploreAllTap,
  });

  @override
  Widget build(BuildContext context) {
    final prompts = [
      '"Đi biển nửa ngày cùng nhóm bạn"',
      '"Tìm trải nghiệm nhẹ nhàng cho hai người"',
      '"Gợi ý hoạt động theo thời tiết"',
    ];

    return Container(
      decoration: BoxDecoration(
        color: AppColors.secondaryContainer.withValues(alpha: 0.35),
        borderRadius: AppShapes.radiusLg,
      ),
      padding: const EdgeInsets.all(AppShapes.spaceMd),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Tag
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: const BoxDecoration(
                  color: AppColors.secondary,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.auto_awesome,
                  color: AppColors.onSecondary,
                  size: 18,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: LocalizedText(
                  'TRỢ LÝ DU LỊCH THÔNG MINH',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.labelSm(
                    color: AppColors.secondary,
                    fontWeight: FontWeight.w800,
                  ).copyWith(letterSpacing: 0.5),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Title & Subtitle
          LocalizedText(
            'Một chuyến đi hợp gu, bắt đầu từ bạn.',
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          LocalizedText(
            'Gợi ý hoạt động cá nhân hoá theo ngân sách, khung giờ và sở thích nhóm bạn chỉ trong vài giây.',
            style: AppTypography.bodySm(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 14),

          // Minimal Chat Preview Box
          Container(
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest.withValues(alpha: 0.9),
              borderRadius: AppShapes.radiusDefault,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.03),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            padding: const EdgeInsets.all(AppShapes.spaceSm),
            child: Column(
              children: [
                // User Bubble (Right)
                Align(
                  alignment: Alignment.centerRight,
                  child: Container(
                    constraints: const BoxConstraints(maxWidth: 280),
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: const BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(AppShapes.radiusDefaultValue),
                        topRight: Radius.circular(AppShapes.radiusDefaultValue),
                        bottomLeft: Radius.circular(AppShapes.radiusDefaultValue),
                        bottomRight: Radius.zero,
                      ),
                    ),
                    child: LocalizedText(
                      'Bọn mình có 3 người, muốn trải nghiệm vào sáng sớm mai ở Mỹ Khê, ngân sách tầm 300k/người.',
                      style: AppTypography.bodySm(
                        color: AppColors.onSecondary,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 10),

                // AI Bubble (Left)
                Align(
                  alignment: Alignment.centerLeft,
                  child: Container(
                    constraints: const BoxConstraints(maxWidth: 300),
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: const BoxDecoration(
                      color: AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.zero,
                        topRight: Radius.circular(AppShapes.radiusDefaultValue),
                        bottomLeft: Radius.circular(AppShapes.radiusDefaultValue),
                        bottomRight: Radius.circular(AppShapes.radiusDefaultValue),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.smart_toy,
                              size: 15,
                              color: AppColors.secondary,
                            ),
                            const SizedBox(width: 4),
                            LocalizedText(
                              'DANASEA AI',
                              style: AppTypography.labelSm(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        RichText(
                          text: TextSpan(
                            style: AppTypography.bodySm(color: AppColors.onSurface),
                            children: [
                              TextSpan(
                                text: tr(context, 'Chào bạn! Gợi ý hoàn hảo nhất là buổi '),
                              ),
                              TextSpan(
                                text: tr(context, 'Chèo SUP đón bình minh'),
                                style: const TextStyle(
                                  color: AppColors.primary,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              TextSpan(
                                text: tr(context, ' lúc 5:00 sáng tại Mỹ Khê (280.000đ/người) có huấn luyện viên kèm và chụp ảnh lưu niệm.'),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Quick Prompt Chips
          LocalizedText(
            'Gợi ý nhanh cho bạn:',
            style: AppTypography.labelSm(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: prompts.map((prompt) {
              final cleanPrompt = prompt.replaceAll('"', '');
              return InkWell(
                onTap: () => onPromptSelected?.call(cleanPrompt),
                borderRadius: AppShapes.radiusFull,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusFull,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.04),
                        blurRadius: 4,
                        offset: const Offset(0, 1),
                      ),
                    ],
                  ),
                  child: LocalizedText(
                    prompt,
                    style: AppTypography.labelSm(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 14),

          // AI Action Button
          AppPillButton(
            label: 'Lên lịch cùng AI',
            variant: AppButtonVariant.primary,
            leadingIcon: Icons.auto_awesome,
            width: double.infinity,
            onPressed: onPlanWithAiTap,
          ),
        ],
      ),
    );
  }
}
