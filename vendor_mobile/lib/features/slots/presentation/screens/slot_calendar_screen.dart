import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/widgets/partner_account_app_bar_button.dart';
import 'slot_config_screen.dart';

class SlotCalendarScreen extends StatefulWidget {
  final String? initialServiceId;
  final VoidCallback? onOpenNotifications;
  final VoidCallback? onOpenProfile;

  const SlotCalendarScreen({
    super.key,
    this.initialServiceId,
    this.onOpenNotifications,
    this.onOpenProfile,
  });

  @override
  State<SlotCalendarScreen> createState() => _SlotCalendarScreenState();
}

class _SlotCalendarScreenState extends State<SlotCalendarScreen> {
  final _db = VendorStore.instance;
  final _slotRepo = VendorSlotRepository();
  late String _selectedServiceId;
  DateTime _selectedDate = DateTime.now();
  String _slotFilter = 'all'; // all, open, blocked
  bool _isServiceDropdownExpanded = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _selectedDate = DateTime(now.year, now.month, now.day);
    _selectedServiceId = widget.initialServiceId ?? (_db.services.isNotEmpty ? _db.services.first.serviceId : '');
    _db.addListener(_onDbChanged);
    _loadSlots();
  }

  void _loadSlots() {
    if (_selectedServiceId.isNotEmpty) {
      _slotRepo.getSlotsForService(_selectedServiceId, _selectedDate);
    }
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }



  @override
  Widget build(BuildContext context) {
    final services = _db.services;
    final hasMatch = services.any((s) => s.serviceId == _selectedServiceId);
    if (!hasMatch && services.isNotEmpty) {
      _selectedServiceId = services.first.serviceId;
      _loadSlots();
    }

    final dateStr =
        '${_selectedDate.year}-${_selectedDate.month.toString().padLeft(2, '0')}-${_selectedDate.day.toString().padLeft(2, '0')}';

    final slotsForDay = _db.slots.where((s) {
      if (s.serviceId != _selectedServiceId) return false;
      if (!s.matchesDate(_selectedDate)) return false;
      if (_slotFilter == 'open') return s.status == SlotStatus.open && s.availableCount > 0;
      if (_slotFilter == 'blocked') return s.isBlocked;
      return true;
    }).toList();

    // Summary metrics for day
    final daySlots = _db.slots.where((s) => s.serviceId == _selectedServiceId && s.matchesDate(_selectedDate));
    final totalSlots = daySlots.length;
    final totalBooked = daySlots.fold<int>(0, (sum, s) => sum + s.bookedCount);
    final totalCap = daySlots.fold<int>(0, (sum, s) => sum + s.capacity);


    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleSpacing: 16,
        title: const LocalizedText(
          'Lịch hoạt động & Slot',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.navyAnchor,
          ),
        ),
        actions: [
          PartnerAccountAppBarButton(onPressed: widget.onOpenProfile),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Service Selector Card with Direct Downward Dropdown
              Container(
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _isServiceDropdownExpanded ? AppColors.secondary : AppColors.borderSubtle,
                    width: _isServiceDropdownExpanded ? 1.5 : 1.0,
                  ),
                  boxShadow: const [
                    BoxShadow(color: Color(0x06102F3A), blurRadius: 8, offset: Offset(0, 2)),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Main Selector Bar
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        children: [
                          Expanded(
                            child: InkWell(
                              onTap: () {
                                setState(() {
                                  _isServiceDropdownExpanded = !_isServiceDropdownExpanded;
                                });
                              },
                              borderRadius: BorderRadius.circular(10),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(vertical: 2.0),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const LocalizedText(
                                      'DỊCH VỤ ÁP DỤNG',
                                      style: TextStyle(
                                        fontSize: 9.5,
                                        fontWeight: FontWeight.bold,
                                        color: AppColors.tertiary,
                                        letterSpacing: 0.5,
                                      ),
                                    ),
                                    const SizedBox(height: 3),
                                    Row(
                                      children: [
                                        Expanded(
                                          child: Text(
                                            services.cast<ServiceModel?>().firstWhere(
                                                  (s) => s?.serviceId == _selectedServiceId,
                                                  orElse: () => services.isNotEmpty ? services.first : null,
                                                )?.nameVi ?? 'Chưa chọn dịch vụ',
                                            style: const TextStyle(
                                              fontSize: 14,
                                              fontWeight: FontWeight.bold,
                                              color: AppColors.navyAnchor,
                                            ),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                        const SizedBox(width: 4),
                                        AnimatedRotation(
                                          turns: _isServiceDropdownExpanded ? 0.5 : 0.0,
                                          duration: const Duration(milliseconds: 200),
                                          child: const Icon(
                                            Icons.keyboard_arrow_down,
                                            size: 20,
                                            color: AppColors.secondary,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          InkWell(
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => SlotConfigScreen(
                                    serviceId: _selectedServiceId,
                                    initialDate: dateStr,
                                  ),
                                ),
                              );
                            },
                            borderRadius: BorderRadius.circular(19),
                            child: Container(
                              width: 36,
                              height: 36,
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.secondary,
                              ),
                              child: const Icon(Icons.add, color: AppColors.onSecondary, size: 20),
                            ),
                          ),
                        ],
                      ),
                    ),

                    // Dropdown Options expanding directly downwards from the card
                    if (_isServiceDropdownExpanded) ...[
                      const Divider(height: 1, color: Color(0xFFE2E8F0)),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                        child: Column(
                          children: services.map((s) {
                            final isSelected = s.serviceId == _selectedServiceId;
                            return Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              child: InkWell(
                                onTap: () {
                                  setState(() {
                                    _selectedServiceId = s.serviceId;
                                    _isServiceDropdownExpanded = false;
                                  });
                                  _slotRepo.getSlotsForService(s.serviceId, _selectedDate);
                                },
                                borderRadius: BorderRadius.circular(12),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                  decoration: BoxDecoration(
                                    color: isSelected
                                        ? AppColors.secondaryContainer.withOpacity(0.25)
                                        : const Color(0xFFF8FAFC),
                                    borderRadius: BorderRadius.circular(12),
                                    border: Border.all(
                                      color: isSelected ? AppColors.secondary : const Color(0xFFE2E8F0),
                                      width: isSelected ? 1.5 : 1.0,
                                    ),
                                  ),
                                  child: Row(
                                    children: [
                                      Container(
                                        width: 32,
                                        height: 32,
                                        decoration: BoxDecoration(
                                          color: isSelected ? AppColors.secondary : AppColors.surfaceContainerLow,
                                          shape: BoxShape.circle,
                                        ),
                                        child: Icon(
                                          Icons.surfing,
                                          color: isSelected ? Colors.white : AppColors.secondary,
                                          size: 16,
                                        ),
                                      ),
                                      const SizedBox(width: 10),
                                      Expanded(
                                        child: Text(
                                          s.nameVi,
                                          style: TextStyle(
                                            fontSize: 13.5,
                                            fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                                            color: isSelected ? AppColors.secondary : AppColors.navyAnchor,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                      if (isSelected)
                                        const Icon(Icons.check_circle, color: AppColors.secondary, size: 18),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          }).toList(),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // Weekly Horizontal Date Strip Header
              LocalizedText(
                'Tháng ${_selectedDate.month} / ${_selectedDate.year}',
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
              ),
              const SizedBox(height: 8),

              // 14-day pill row
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: List.generate(14, (i) {
                    final today = DateTime.now();
                    final dayDate = DateTime(today.year, today.month, today.day).add(Duration(days: i - 2));
                    final isSelected = dayDate.day == _selectedDate.day &&
                        dayDate.month == _selectedDate.month &&
                        dayDate.year == _selectedDate.year;
                    final dayOfWeekNames = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
                    final dayName = dayOfWeekNames[dayDate.weekday % 7];

                    final daySlotsCount = _db.slots.where((s) => s.serviceId == _selectedServiceId && s.matchesDate(dayDate)).length;

                    return GestureDetector(
                      onTap: () => setState(() => _selectedDate = dayDate),
                      child: Container(
                        margin: const EdgeInsets.only(right: 8),
                        width: 58,
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        decoration: BoxDecoration(
                          color: isSelected ? AppColors.secondary : AppColors.surfaceContainerLowest,
                          borderRadius: BorderRadius.circular(16),
                          border: isSelected ? null : Border.all(color: AppColors.borderSubtle),
                          boxShadow: isSelected
                              ? [BoxShadow(color: AppColors.secondary.withOpacity(0.28), blurRadius: 8, offset: const Offset(0, 3))]
                              : const [BoxShadow(color: Color(0x06102F3A), blurRadius: 4, offset: Offset(0, 1))],
                        ),
                        child: Column(
                          children: [
                            LocalizedText(
                              dayName,
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: isSelected ? Colors.white70 : AppColors.tertiary,
                              ),
                            ),
                            const SizedBox(height: 3),
                            LocalizedText(
                              dayDate.day.toString().padLeft(2, '0'),
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: isSelected ? Colors.white : AppColors.navyAnchor,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: isSelected ? Colors.white.withOpacity(0.22) : AppColors.surfaceContainerLow,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: LocalizedText(
                                '$daySlotsCount slot',
                                style: TextStyle(
                                  fontSize: 9,
                                  fontWeight: FontWeight.bold,
                                  color: isSelected ? Colors.white : AppColors.tertiary,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                ),
              ),
              const SizedBox(height: 14),

              // Sleek 1-line summary (clean, low-noise)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Ngày ${_selectedDate.day}/${_selectedDate.month} • $totalSlots ca chạy', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.navyAnchor)),
                    Text('Đã đặt: $totalBooked/$totalCap khách', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.secondary)),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              // Filter pills: Tất cả, Đang mở, Đã khóa
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _buildSlotFilterChip('all', 'Tất cả ($totalSlots)'),
                    const SizedBox(width: 8),
                    _buildSlotFilterChip('open', 'Đang mở'),
                    const SizedBox(width: 8),
                    _buildSlotFilterChip('blocked', 'Đã tạm khóa'),
                  ],
                ),
              ),
              const SizedBox(height: 10),

              // Slot Cards List
              if (slotsForDay.isEmpty)
                Container(
                  padding: const EdgeInsets.all(32),
                  width: double.infinity,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  child: Center(
                    child: Column(
                      children: [
                        const Icon(Icons.event_busy, size: 40, color: AppColors.tertiary),
                        const SizedBox(height: 8),
                        const LocalizedText('Chưa có khung giờ nào cho ngày này', style: TextStyle(fontSize: 13, color: AppColors.tertiary)),
                        const SizedBox(height: 10),
                        ElevatedButton.icon(
                          onPressed: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => SlotConfigScreen(
                                  serviceId: _selectedServiceId,
                                  initialDate: dateStr,
                                ),
                              ),
                            );
                          },
                          icon: const Icon(Icons.add, size: 16),
                          label: const LocalizedText('Thêm khung giờ ngay', style: TextStyle(fontSize: 12)),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.secondary,
                            foregroundColor: AppColors.onSecondary,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: slotsForDay.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, idx) {
                    final slot = slotsForDay[idx];
                    return _buildSlotItemCard(slot);
                  },
                ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }


  Widget _buildSlotFilterChip(String key, String label) {
    final active = _slotFilter == key;
    return InkWell(
      onTap: () => setState(() => _slotFilter = key),
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.secondary : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: active ? AppColors.secondary : AppColors.borderSubtle,
          ),
          boxShadow: active
              ? [BoxShadow(color: AppColors.secondary.withOpacity(0.2), blurRadius: 4, offset: const Offset(0, 2))]
              : null,
        ),
        child: LocalizedText(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: active ? FontWeight.bold : FontWeight.w500,
            color: active ? Colors.white : AppColors.onSurfaceVariant,
          ),
        ),
      ),
    );
  }

  Widget _buildSlotItemCard(ServiceSlotModel slot) {
    final bool isFull = slot.bookedCount >= slot.capacity;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(14),
        border: slot.isBlocked
            ? Border.all(color: AppColors.errorContainer, width: 1.5)
            : Border.all(color: AppColors.borderSubtle),
        boxShadow: const [
          BoxShadow(color: Color(0x06102F3A), blurRadius: 8, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              LocalizedText(
                '${slot.startTime.substring(0, 5)} - ${slot.endTime.substring(0, 5)}',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: slot.isBlocked ? AppColors.tertiary : AppColors.navyAnchor,
                ),
              ),
              if (slot.status == SlotStatus.cancelled || slot.status == SlotStatus.closed)
                LocalizedText(slot.status.labelVi)
              else if (slot.isBlocked)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.block, size: 11, color: AppColors.error),
                      SizedBox(width: 3),
                      LocalizedText('TẠM KHÓA', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onErrorContainer)),
                    ],
                  ),
                )
              else if (isFull)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const LocalizedText('HẾT CHỖ', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.tertiary)),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.secondaryContainer.withOpacity(0.5),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const LocalizedText('CÒN CHỖ', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                ),
            ],
          ),
          const SizedBox(height: 8),

          // Capacity & Booked row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const LocalizedText('Khách đã đặt: ', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                  LocalizedText(
                    '${slot.bookedCount}',
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                  ),
                  LocalizedText(' / ${slot.capacity} khách', style: const TextStyle(fontSize: 11, color: AppColors.tertiary)),
                ],
              ),
              if (slot.heldCount > 0)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(6)),
                  child: LocalizedText('Tạm giữ: ${slot.heldCount}', style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(2),
            child: LinearProgressIndicator(
              value: slot.capacity > 0 ? (slot.bookedCount / slot.capacity).clamp(0.0, 1.0) : 0,
              minHeight: 4,
              backgroundColor: AppColors.surfaceContainerHigh,
              valueColor: AlwaysStoppedAnimation<Color>(slot.isBlocked ? AppColors.tertiary : AppColors.secondary),
            ),
          ),
          const SizedBox(height: 8),

          // Actions
          if (slot.status != SlotStatus.cancelled)
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                // Toggle block button
                InkWell(
                  onTap: () {
                    _db.toggleSlotBlocked(slot.slotId);
                    ToastNotification.showInfo(
                      context,
                      slot.isBlocked ? 'Đã mở khóa slot ${slot.startTime}' : 'Đã tạm khóa slot ${slot.startTime}',
                    );
                  },
                  borderRadius: BorderRadius.circular(8),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          slot.isBlocked ? Icons.lock_open : Icons.block,
                          size: 14,
                          color: slot.isBlocked ? AppColors.secondary : AppColors.tertiary,
                        ),
                        const SizedBox(width: 4),
                        LocalizedText(
                          slot.isBlocked ? 'Mở khóa' : 'Tạm khóa',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: slot.isBlocked ? AppColors.secondary : AppColors.tertiary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                // Edit config button
                InkWell(
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => SlotConfigScreen(
                          slot: slot,
                          serviceId: _selectedServiceId,
                        ),
                      ),
                    );
                  },
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppColors.secondary.withOpacity(0.25)),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.edit_outlined, size: 13, color: AppColors.secondary),
                        SizedBox(width: 4),
                        LocalizedText(
                          'Chỉnh sửa',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: AppColors.secondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}