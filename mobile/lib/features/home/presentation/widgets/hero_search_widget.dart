import 'package:flutter/material.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class HeroSearchWidget extends StatefulWidget {
  final VoidCallback? onExploreTap;
  final VoidCallback? onAiSuggestTap;
  final void Function(String? location, DateTime? date, int guests)? onSearchTap;

  const HeroSearchWidget({
    super.key,
    this.onExploreTap,
    this.onAiSuggestTap,
    this.onSearchTap,
  });

  @override
  State<HeroSearchWidget> createState() => _HeroSearchWidgetState();
}

class _HeroSearchWidgetState extends State<HeroSearchWidget> {
  String _selectedLocation = 'Mỹ Khê, Sơn Trà, Non Nước';
  DateTime _selectedDate = DateTime.now().add(const Duration(days: 1));
  int _selectedGuests = 2;

  final List<Map<String, String>> _locations = [
    {
      'title': 'Mỹ Khê, Sơn Trà, Non Nước',
      'subtitle': 'Tất cả các khu vực biển tại Đà Nẵng',
      'icon': 'waves',
    },
    {
      'title': 'Bãi biển Mỹ Khê',
      'subtitle': 'Chèo SUP đón bình minh, cano lướt ván, dù bay',
      'icon': 'beach_access',
    },
    {
      'title': 'Bán đảo Sơn Trà',
      'subtitle': 'Lặn ngắm san hô Hòn Sụp, Bãi Bụt, ngắm voọc',
      'icon': 'scuba_diving',
    },
    {
      'title': 'Bãi biển Non Nước',
      'subtitle': 'Không gian thoáng đãng, lướt sóng nhẹ, resort',
      'icon': 'surfing',
    },
    {
      'title': 'Bãi biển Mân Thái',
      'subtitle': 'Làng chài truyền thống, bình minh SUP êm sóng',
      'icon': 'sailing',
    },
    {
      'title': 'Bãi Rạng & Nam Ô',
      'subtitle': 'Rạn đá ngầm hoang sơ, check-in rêu xanh',
      'icon': 'terrain',
    },
  ];

  String _formatDateText(DateTime date) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final target = DateTime(date.year, date.month, date.day);
    final diff = target.difference(today).inDays;

    if (diff == 0) return 'Hôm nay';
    if (diff == 1) return 'Ngày mai';
    if (diff == 2) return 'Ngày kia';
    return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}';
  }

  void _pickLocation() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    const Icon(
                      Icons.location_on,
                      color: AppColors.secondary,
                      size: 22,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      'Chọn khu vực biển',
                      style: AppTypography.headlineSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w800,
                      ).copyWith(fontSize: 18),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  'Chọn bãi biển bạn muốn tham gia hoạt động tại Đà Nẵng',
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 14),
                ...List.generate(_locations.length, (index) {
                  final loc = _locations[index];
                  final isSelected = loc['title'] == _selectedLocation;
                  return InkWell(
                    onTap: () {
                      setState(() {
                        _selectedLocation = loc['title']!;
                      });
                      Navigator.pop(sheetContext);
                    },
                    borderRadius: BorderRadius.circular(12),
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 12,
                      ),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? AppColors.secondaryContainer.withValues(alpha: 0.35)
                            : const Color(0xFFF8FAFC),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: isSelected
                              ? AppColors.secondary
                              : const Color(0xFFE2E8F0),
                          width: isSelected ? 1.5 : 1,
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? AppColors.secondary
                                  : const Color(0xFFE0F2F1),
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.beach_access,
                              size: 16,
                              color: isSelected
                                  ? Colors.white
                                  : AppColors.secondary,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  loc['title']!,
                                  style: AppTypography.labelMd(
                                    color: isSelected
                                        ? AppColors.secondary
                                        : AppColors.onSurface,
                                    fontWeight: isSelected
                                        ? FontWeight.w800
                                        : FontWeight.w600,
                                  ).copyWith(fontSize: 14),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  loc['subtitle']!,
                                  style: AppTypography.bodySm(
                                    color: AppColors.onSurfaceVariant,
                                  ).copyWith(fontSize: 11),
                                ),
                              ],
                            ),
                          ),
                          if (isSelected)
                            const Icon(
                              Icons.check_circle_rounded,
                              color: AppColors.secondary,
                              size: 20,
                            ),
                        ],
                      ),
                    ),
                  );
                }),
                const SizedBox(height: 10),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final firstDate = DateTime(now.year, now.month, now.day);
    final lastDate = firstDate.add(const Duration(days: 365));

    final picked = await showDatePicker(
      context: context,
      initialDate:
          _selectedDate.isBefore(firstDate) ? firstDate : _selectedDate,
      firstDate: firstDate,
      lastDate: lastDate,
      helpText: 'CHỌN NGÀY TRẢI NGHIỆM',
      cancelText: 'HUỶ',
      confirmText: 'CHỌN',
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.light(
              primary: AppColors.secondary,
              onPrimary: AppColors.onSecondary,
              surface: AppColors.surfaceContainerLowest,
              onSurface: AppColors.onSurface,
            ),
            datePickerTheme: DatePickerThemeData(
              headerBackgroundColor: AppColors.secondary,
              headerForegroundColor: AppColors.onSecondary,
              backgroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(24),
              ),
              headerHeadlineStyle: const TextStyle(
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.w700,
              ),
              headerHelpStyle: const TextStyle(
                color: Colors.white70,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
              ),
            ),
          ),
          child: child!,
        );
      },
    );
    if (picked != null && mounted) {
      setState(() {
        _selectedDate = picked;
      });
    }
  }

  void _pickGuests() {
    int tempGuests = _selectedGuests;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            return Container(
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              child: SafeArea(
                top: false,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Center(
                      child: Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: const Color(0xFFCBD5E1),
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        const Icon(
                          Icons.group,
                          color: AppColors.secondary,
                          size: 22,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Số lượng khách',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                            fontWeight: FontWeight.w800,
                          ).copyWith(fontSize: 18),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Chọn số lượng người để chuẩn bị ván SUP, bình khí và HLV phù hợp',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),

                    // STEPPER BOX
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Khách tham gia',
                                style: TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 15,
                                  color: AppColors.onSurface,
                                ),
                              ),
                              SizedBox(height: 2),
                              Text(
                                'Bao gồm người lớn & trẻ em',
                                style: TextStyle(
                                  fontSize: 11.5,
                                  color: Color(0xFF64748B),
                                ),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              // Minus
                              IconButton(
                                style: IconButton.styleFrom(
                                  backgroundColor: tempGuests > 1
                                      ? Colors.white
                                      : const Color(0xFFE2E8F0),
                                  shape: const CircleBorder(),
                                ),
                                icon: Icon(
                                  Icons.remove,
                                  color: tempGuests > 1
                                      ? AppColors.primary
                                      : const Color(0xFF94A3B8),
                                ),
                                onPressed: tempGuests > 1
                                    ? () {
                                        setSheetState(() => tempGuests--);
                                      }
                                    : null,
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                ),
                                child: Text(
                                  '$tempGuests',
                                  style: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.w900,
                                    color: AppColors.onSurface,
                                  ),
                                ),
                              ),
                              // Plus
                              IconButton(
                                style: IconButton.styleFrom(
                                  backgroundColor: Colors.white,
                                  shape: const CircleBorder(),
                                ),
                                icon: const Icon(
                                  Icons.add,
                                  color: AppColors.primary,
                                ),
                                onPressed: tempGuests < 30
                                    ? () {
                                        setSheetState(() => tempGuests++);
                                      }
                                    : null,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 24),

                    // CONFIRM BUTTON
                    AppPillButton(
                      label: 'Xác nhận $tempGuests khách',
                      variant: AppButtonVariant.primary,
                      width: double.infinity,
                      onPressed: () {
                        setState(() {
                          _selectedGuests = tempGuests;
                        });
                        Navigator.pop(sheetContext);
                      },
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // ---------------- HERO BANNER ----------------
        Stack(
          children: [
            // Background Image
            AspectRatio(
              aspectRatio: 4 / 4.8,
              child: Image.network(
                'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200&auto=format&fit=crop&q=80',
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) {
                  return Container(
                    color: AppColors.secondary.withValues(alpha: 0.8),
                    child: const Center(
                      child: Icon(Icons.beach_access, size: 64, color: Colors.white),
                    ),
                  );
                },
              ),
            ),
            // Gradient Scrim
            Positioned.fill(
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      AppColors.inverseSurface.withValues(alpha: 0.35),
                      AppColors.inverseSurface.withValues(alpha: 0.85),
                      AppColors.inverseSurface.withValues(alpha: 0.98),
                    ],
                    stops: const [0.0, 0.45, 0.75, 1.0],
                  ),
                ),
              ),
            ),
            // Hero Content Overlay
            Positioned(
              left: AppShapes.gutterMobile,
              right: AppShapes.gutterMobile,
              bottom: 40,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Pill Tag
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: AppShapes.radiusFull,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.2),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.waves, size: 14, color: AppColors.onSecondary),
                        const SizedBox(width: 6),
                        Flexible(
                          child: LocalizedText(
                            'TRẢI NGHIỆM BIỂN ĐÀ NẴNG',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.labelSm(
                              color: AppColors.onSecondary,
                              fontWeight: FontWeight.w700,
                            ).copyWith(
                              letterSpacing: 0.4,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  // Headline
                  LocalizedText(
                    'Chạm sóng biển,\nmở chuyến đi riêng.',
                    style: AppTypography.headlineLgMobile(
                      color: AppColors.inverseOnSurface,
                    ).copyWith(
                      fontWeight: FontWeight.w800,
                      height: 1.15,
                    ),
                  ),
                  const SizedBox(height: 8),
                  // Subtitle
                  LocalizedText(
                    'Khám phá những trải nghiệm biển phù hợp với bạn. Chọn hoạt động, đặt lịch và sẵn sàng tận hưởng Đà Nẵng.',
                    style: AppTypography.bodyMd(
                      color: AppColors.surfaceVariant,
                    ).copyWith(
                      height: 1.4,
                    ),
                  ),
                  const SizedBox(height: 20),
                  // CTA 1
                  AppPillButton(
                    label: 'Khám phá trải nghiệm',
                    variant: AppButtonVariant.primary,
                    trailingIcon: Icons.arrow_forward,
                    width: double.infinity,
                    onPressed: widget.onExploreTap,
                  ),
                  const SizedBox(height: 10),
                  // CTA 2
                  AppPillButton(
                    label: 'Gợi ý lịch trình cho tôi',
                    variant: AppButtonVariant.frosted,
                    leadingIcon: Icons.auto_awesome,
                    width: double.infinity,
                    onPressed: widget.onAiSuggestTap,
                  ),
                ],
              ),
            ),
          ],
        ),

        // ---------------- FLOATING SEARCH CARD ----------------
        Transform.translate(
          offset: const Offset(0, -26),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
            child: Container(
              padding: const EdgeInsets.all(AppShapes.spaceMd),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusLg,
                boxShadow: AppShapes.shadowLevel2,
                border: Border.all(
                  color: AppColors.borderOcean.withValues(alpha: 0.5),
                  width: 0.8,
                ),
              ),
              child: Column(
                children: [
                  // Row 1: Destination (Clickable)
                  InkWell(
                    onTap: _pickLocation,
                    borderRadius: AppShapes.radiusDefault,
                    child: Container(
                      padding: const EdgeInsets.all(AppShapes.spaceXs),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: AppShapes.radiusDefault,
                        border: Border.all(
                          color: const Color(0xFFE2E8F0),
                          width: 0.8,
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 36,
                            height: 36,
                            decoration: const BoxDecoration(
                              color: AppColors.secondaryContainer,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.location_on,
                              color: AppColors.onSecondaryContainer,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  'Khu vực biển',
                                  style: AppTypography.labelSm(
                                    color: AppColors.onSurfaceVariant,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                                Text(
                                  _selectedLocation,
                                  style: AppTypography.labelLg(
                                    color: AppColors.onSurface,
                                    fontWeight: FontWeight.w700,
                                  ).copyWith(fontSize: 13.5),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          const Icon(
                            Icons.expand_more,
                            color: AppColors.outline,
                            size: 20,
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),

                  // Row 2: Date & Guests (Clickable)
                  Row(
                    children: [
                      // Date
                      Expanded(
                        child: InkWell(
                          onTap: _pickDate,
                          borderRadius: AppShapes.radiusDefault,
                          child: Container(
                            padding: const EdgeInsets.all(AppShapes.spaceXs),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLow,
                              borderRadius: AppShapes.radiusDefault,
                              border: Border.all(
                                color: const Color(0xFFE2E8F0),
                                width: 0.8,
                              ),
                            ),
                            child: Row(
                              children: [
                                Container(
                                  width: 32,
                                  height: 32,
                                  decoration: const BoxDecoration(
                                    color: AppColors.secondaryContainer,
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.calendar_today,
                                    color: AppColors.onSecondaryContainer,
                                    size: 16,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      LocalizedText(
                                        'Thời gian',
                                        style: AppTypography.labelSm(
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                      Text(
                                        _formatDateText(_selectedDate),
                                        style: AppTypography.labelMd(
                                          color: AppColors.onSurface,
                                          fontWeight: FontWeight.w700,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      // Guests
                      Expanded(
                        child: InkWell(
                          onTap: _pickGuests,
                          borderRadius: AppShapes.radiusDefault,
                          child: Container(
                            padding: const EdgeInsets.all(AppShapes.spaceXs),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLow,
                              borderRadius: AppShapes.radiusDefault,
                              border: Border.all(
                                color: const Color(0xFFE2E8F0),
                                width: 0.8,
                              ),
                            ),
                            child: Row(
                              children: [
                                Container(
                                  width: 32,
                                  height: 32,
                                  decoration: const BoxDecoration(
                                    color: AppColors.secondaryContainer,
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.group,
                                    color: AppColors.onSecondaryContainer,
                                    size: 18,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      LocalizedText(
                                        'Số lượng',
                                        style: AppTypography.labelSm(
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                      Text(
                                        '$_selectedGuests khách',
                                        style: AppTypography.labelMd(
                                          color: AppColors.onSurface,
                                          fontWeight: FontWeight.w700,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Submit Search Button
                  AppPillButton(
                    label: 'Tìm trải nghiệm',
                    variant: AppButtonVariant.primary,
                    leadingIcon: Icons.search,
                    width: double.infinity,
                    onPressed: () {
                      if (widget.onSearchTap != null) {
                        widget.onSearchTap!(
                          _selectedLocation,
                          _selectedDate,
                          _selectedGuests,
                        );
                      }
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
