import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class BookingSlotSheet extends StatefulWidget {
  final ServiceModel service;
  final List<ServiceSlotModel> availableSlots;
  final Function(ServiceSlotModel slot, int guestCount, bool isInstantCheckout)
      onConfirm;
  final bool isEditMode;

  const BookingSlotSheet({
    super.key,
    required this.service,
    required this.availableSlots,
    required this.onConfirm,
    this.isEditMode = false,
  });

  @override
  State<BookingSlotSheet> createState() => _BookingSlotSheetState();
}

class _BookingSlotSheetState extends State<BookingSlotSheet> {
  late int _selectedDateIndex;
  late ServiceSlotModel _selectedSlot;
  int _guestCount = 2;

  final List<DateTime> _dates = List.generate(
    7,
    (index) => DateTime.now().add(Duration(days: index)),
  );

  @override
  void initState() {
    super.initState();
    _selectedDateIndex = 0;
    _selectedSlot = widget.availableSlots.isNotEmpty
        ? widget.availableSlots.first
        : ServiceSlotModel(
            id: 'slt-default',
            serviceId: widget.service.id,
            date: DateTime.now(),
            startTime: '05:00',
            endTime: '07:00',
            capacity: 15,
            bookedCount: 5,
          );
  }

  String _formatPrice(int price) {
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

  String _getDayName(DateTime date, int index) {
    if (index == 0) return 'Hôm nay';
    if (index == 1) return 'Ngày mai';
    switch (date.weekday) {
      case DateTime.monday:
        return 'T2';
      case DateTime.tuesday:
        return 'T3';
      case DateTime.wednesday:
        return 'T4';
      case DateTime.thursday:
        return 'T5';
      case DateTime.friday:
        return 'T6';
      case DateTime.saturday:
        return 'T7';
      default:
        return 'CN';
    }
  }

  @override
  Widget build(BuildContext context) {
    final subtotal = widget.service.price * _guestCount;
    final maxAllowed = _selectedSlot.remainingSlots > 0
        ? _selectedSlot.remainingSlots
        : 10;

    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: const BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom +
              MediaQuery.of(context).padding.bottom +
              16,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Drag handle
            Center(
              child: Container(
                margin: const EdgeInsets.only(top: 10, bottom: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.outlineVariant.withValues(alpha: 0.6),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),

            // Header
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        LocalizedText(
                          'Chọn lịch & số người',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 2),
                        LocalizedText(
                          '${widget.service.name} • ${widget.service.vendorName}',
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: AppColors.outline),
                    splashRadius: 20,
                  ),
                ],
              ),
            ),
            const Divider(height: 16),

            // Scrollable body
            Flexible(
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1. DATE SELECTION
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
                      child: LocalizedText(
                        '1. Chọn ngày trải nghiệm',
                        style: AppTypography.labelLg(color: AppColors.onSurface),
                      ),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      height: 72,
                      child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(
                  horizontal: AppShapes.gutterMobile),
              itemCount: _dates.length,
              separatorBuilder: (_, unused) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final date = _dates[index];
                final isSelected = _selectedDateIndex == index;
                return InkWell(
                  onTap: () {
                    setState(() => _selectedDateIndex = index);
                  },
                  borderRadius: BorderRadius.circular(14),
                  child: Container(
                    width: 64,
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppColors.secondary
                          : AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: isSelected
                            ? AppColors.secondary
                            : AppColors.borderSubtle,
                      ),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        LocalizedText(
                          _getDayName(date, index),
                          style: AppTypography.labelSm(
                            color: isSelected
                                ? AppColors.onSecondary
                                : AppColors.onSurfaceVariant,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 2),
                        LocalizedText(
                          '${date.day}/${date.month}',
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
          const SizedBox(height: 16),

          // 2. TIME SLOT SELECTION
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: LocalizedText(
                    '2. Chọn khung giờ xuất phát',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.secondaryFixed.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.waves,
                          size: 12, color: AppColors.onSecondaryFixedVariant),
                      const SizedBox(width: 4),
                      LocalizedText(
                        'Sóng êm: 0.4m',
                        style: AppTypography.labelSm(
                          color: AppColors.onSecondaryFixedVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: widget.availableSlots.map((slot) {
                final isSelected = _selectedSlot.id == slot.id;
                return InkWell(
                  onTap: () {
                    setState(() {
                      _selectedSlot = slot;
                      if (_guestCount > slot.remainingSlots &&
                          slot.remainingSlots > 0) {
                        _guestCount = slot.remainingSlots;
                      }
                    });
                  },
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppColors.primary
                          : AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isSelected
                            ? AppColors.primary
                            : AppColors.borderSubtle,
                      ),
                    ),
                    child: Column(
                      children: [
                        LocalizedText(
                          '${slot.startTime} - ${slot.endTime}',
                          style: AppTypography.labelMd(
                            color: isSelected
                                ? Colors.white
                                : AppColors.onSurface,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 2),
                        LocalizedText(
                          'Còn ${slot.remainingSlots} chỗ',
                          style: AppTypography.bodySm(
                            color: isSelected
                                ? Colors.white.withValues(alpha: 0.85)
                                : AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 11),
                        ),
                      ],
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 16),

          // 3. GUEST COUNTER
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText(
                        '3. Số lượng khách',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.labelLg(color: AppColors.onSurface),
                      ),
                      LocalizedText(
                        '${_formatPrice(widget.service.price)} / khách',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  child: Row(
                    children: [
                      IconButton(
                        onPressed: _guestCount > 1
                            ? () => setState(() => _guestCount--)
                            : null,
                        icon: const Icon(Icons.remove, size: 18),
                        splashRadius: 18,
                        color: AppColors.onSurface,
                      ),
                      Container(
                        constraints: const BoxConstraints(minWidth: 32),
                        alignment: Alignment.center,
                        child: LocalizedText(
                          '$_guestCount',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      IconButton(
                        onPressed: _guestCount < maxAllowed
                            ? () => setState(() => _guestCount++)
                            : null,
                        icon: const Icon(Icons.add, size: 18),
                        splashRadius: 18,
                        color: AppColors.onSurface,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
                  ],
                ),
              ),
            ),

          // BOTTOM BAR ACTIONS
          Container(
            padding: const EdgeInsets.all(AppShapes.gutterMobile),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              border: Border(
                top: BorderSide(
                  color: AppColors.borderSubtle.withValues(alpha: 0.8),
                  width: 0.5,
                ),
              ),
            ),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        'Tổng tạm tính ($_guestCount khách):',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.labelMd(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    LocalizedText(
                      _formatPrice(subtotal),
                      style: AppTypography.headlineMd(
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    if (!widget.isEditMode) ...[
                      Expanded(
                        child: AppPillButton(
                          label: 'Thêm vào giỏ',
                          variant: AppButtonVariant.secondary,
                          leadingIcon: Icons.shopping_bag_outlined,
                          onPressed: () {
                            Navigator.pop(context);
                            widget.onConfirm(_selectedSlot, _guestCount, false);
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                    ],
                    Expanded(
                      child: AppPillButton(
                        label: widget.isEditMode ? 'Cập nhật' : 'Đặt ngay',
                        variant: AppButtonVariant.primary,
                        trailingIcon: widget.isEditMode ? Icons.check : Icons.arrow_forward,
                        onPressed: () {
                          Navigator.pop(context);
                          widget.onConfirm(_selectedSlot, _guestCount, !widget.isEditMode);
                        },
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
