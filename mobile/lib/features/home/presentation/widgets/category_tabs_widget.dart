import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class CategoryTabsWidget extends StatefulWidget {
  final ValueChanged<int>? onCategoryChanged;
  final List<CategoryModel> categories;
  final VoidCallback? onShowAll;

  const CategoryTabsWidget({
    super.key,
    this.onCategoryChanged,
    this.categories = const [],
    this.onShowAll,
  });

  @override
  State<CategoryTabsWidget> createState() => _CategoryTabsWidgetState();
}

class _CategoryTabsWidgetState extends State<CategoryTabsWidget> {
  int _selectedIndex = 0;

  static const List<Map<String, dynamic>> _defaultCategories = [
    {'name': 'Chèo SUP', 'icon': Icons.surfing},
    {'name': 'Cano lướt sóng', 'icon': Icons.speed},
    {'name': 'Lặn san hô', 'icon': Icons.scuba_diving},
    {'name': 'Chèo kayak', 'icon': Icons.kayaking},
    {'name': 'Tour biển đảo', 'icon': Icons.sailing},
  ];

  IconData _iconForCategory(String nameOrSlug, int index) {
    final lower = nameOrSlug.toLowerCase();
    if (lower.contains('sup') || lower.contains('surf')) {
      return Icons.surfing;
    }
    if (lower.contains('cano') ||
        lower.contains('jetski') ||
        lower.contains('moto') ||
        lower.contains('speed')) {
      return Icons.speed;
    }
    if (lower.contains('lặn') ||
        lower.contains('lan') ||
        lower.contains('dive') ||
        lower.contains('snorkel')) {
      return Icons.scuba_diving;
    }
    if (lower.contains('kayak')) {
      return Icons.kayaking;
    }
    if (lower.contains('tour') ||
        lower.contains('đảo') ||
        lower.contains('dao') ||
        lower.contains('sail')) {
      return Icons.sailing;
    }
    const fallbackIcons = [
      Icons.surfing,
      Icons.speed,
      Icons.scuba_diving,
      Icons.kayaking,
      Icons.sailing,
    ];
    return fallbackIcons[index % fallbackIcons.length];
  }

  @override
  Widget build(BuildContext context) {
    final hasApiCategories = widget.categories.isNotEmpty;
    final itemCount = hasApiCategories
        ? widget.categories.length
        : _defaultCategories.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section Title
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: AppShapes.gutterMobile,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: LocalizedText(
                  'Khám phá theo sở thích',
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.headlineSm(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              InkWell(
                onTap: widget.onShowAll ?? () {},
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 4,
                    vertical: 2,
                  ),
                  child: LocalizedText(
                    'Xem tất cả',
                    style: AppTypography.labelMd(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        // Horizontal Scrollable Category Pills
        SizedBox(
          height: 44,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            scrollDirection: Axis.horizontal,
            itemCount: itemCount,
            separatorBuilder: (context, index) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final isSelected = _selectedIndex == index;
              final String label;
              final IconData icon;
              if (hasApiCategories) {
                final cat = widget.categories[index];
                label =
                    AppLanguage.instance.code == 'en' && cat.nameEn.isNotEmpty
                    ? cat.nameEn
                    : cat.name;
                icon = _iconForCategory('${cat.slug} ${cat.name}', index);
              } else {
                final item = _defaultCategories[index];
                label = item['name'] as String;
                icon = item['icon'] as IconData;
              }

              return InkWell(
                onTap: () {
                  setState(() => _selectedIndex = index);
                  widget.onCategoryChanged?.call(index);
                },
                borderRadius: AppShapes.radiusFull,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? AppColors.secondary
                        : AppColors.surfaceContainerHigh,
                    borderRadius: AppShapes.radiusFull,
                    boxShadow: isSelected
                        ? [
                            BoxShadow(
                              color: AppColors.secondary.withValues(
                                alpha: 0.25,
                              ),
                              blurRadius: 8,
                              offset: const Offset(0, 3),
                            ),
                          ]
                        : null,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        icon,
                        size: 18,
                        color: isSelected
                            ? AppColors.onSecondary
                            : AppColors.onSurface,
                      ),
                      const SizedBox(width: 8),
                      LocalizedText(
                        label,
                        style: AppTypography.labelMd(
                          color: isSelected
                              ? AppColors.onSecondary
                              : AppColors.onSurface,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
