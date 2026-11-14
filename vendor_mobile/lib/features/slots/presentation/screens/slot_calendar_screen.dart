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
          IconButton(
            tooltip: 'AI Cố vấn mở ca & thời tiết',
            icon: const Icon(Icons.auto_awesome, color: AppColors.secondary),
            onPressed: _showSlotAdvisorSheet,
          ),
          IconButton(
            tooltip: 'Lịch hoạt động định kỳ',
            icon: const Icon(Icons.tune_outlined, color: AppColors.navyAnchor),
            onPressed: _showOperatingScheduleSheet,
          ),
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
          if (slot.units.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              'Đơn vị thuyền/cano: ${slot.units.map((u) => 'Đơn vị #${u['unitNumber']} (${u['capacity']} chỗ)').join(' • ')}',
              style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.secondary),
            ),
          ],
          const SizedBox(height: 8),

          // Actions
          if (slot.status != SlotStatus.cancelled)
            Wrap(
              alignment: WrapAlignment.end,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 6,
              runSpacing: 6,
              children: [
                // Assign equipment to slot unit button
                InkWell(
                  onTap: () => _showAssignEquipmentDialog(slot),
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer.withValues(alpha: 0.45),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppColors.secondary.withValues(alpha: 0.3)),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.directions_boat_outlined, size: 13, color: AppColors.secondary),
                        SizedBox(width: 4),
                        LocalizedText(
                          'Gán thiết bị',
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
                // Cancel slot emergency button
                InkWell(
                  onTap: () => _showCancelSlotDialog(slot),
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                    decoration: BoxDecoration(
                      color: AppColors.errorContainer.withOpacity(0.4),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppColors.error.withOpacity(0.3)),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.event_busy, size: 13, color: AppColors.error),
                        SizedBox(width: 4),
                        LocalizedText(
                          'Hủy ca',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: AppColors.error,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
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

  void _showSlotAdvisorSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SlotAdvisorModal(
        serviceId: _selectedServiceId,
        onSelectDate: (dateStr) {
          Navigator.pop(ctx);
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => SlotConfigScreen(
                serviceId: _selectedServiceId,
                initialDate: dateStr,
              ),
            ),
          );
        },
      ),
    );
  }

  void _showOperatingScheduleSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _OperatingScheduleModal(serviceId: _selectedServiceId),
    );
  }

  void _showCancelSlotDialog(ServiceSlotModel slot) {
    final reasonController = TextEditingController();
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Row(
            children: const [
              Icon(Icons.warning_amber_rounded, color: AppColors.error),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Hủy ca chạy khẩn cấp',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Khung giờ: ${slot.startTime.length >= 5 ? slot.startTime.substring(0, 5) : slot.startTime} - ${slot.endTime.length >= 5 ? slot.endTime.substring(0, 5) : slot.endTime}',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 4),
              Text(
                'Số khách bị ảnh hưởng: ${slot.bookedCount} khách',
                style: const TextStyle(color: AppColors.tertiary, fontSize: 12),
              ),
              const SizedBox(height: 12),
              const Text(
                'Lý do hủy (bắt buộc):',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              TextField(
                controller: reasonController,
                maxLines: 3,
                maxLength: 500,
                decoration: InputDecoration(
                  hintText: 'Nhập lý do hủy ca (thời tiết, bảo trì tàu, khẩn cấp...)',
                  hintStyle: const TextStyle(fontSize: 12, color: AppColors.tertiary),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                  contentPadding: const EdgeInsets.all(10),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Đóng'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      final reason = reasonController.text.trim();
                      if (reason.isEmpty) {
                        ToastNotification.showError(ctx, 'Vui lòng nhập lý do hủy ca');
                        return;
                      }
                      setDialogState(() => isSubmitting = true);
                      try {
                        await VendorSlotRepository().cancelSlot(
                          slot.serviceId.isNotEmpty ? slot.serviceId : _selectedServiceId,
                          slot.slotId,
                          reason,
                        );
                        if (mounted) {
                          Navigator.pop(ctx);
                          ToastNotification.showSuccess(context, 'Đã gửi yêu cầu hủy ca khẩn cấp thành công');
                          setState(() {});
                        }
                      } catch (e) {
                        setDialogState(() => isSubmitting = false);
                        if (mounted) {
                          ToastNotification.showError(ctx, 'Lỗi hủy ca: $e');
                        }
                      }
                    },
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.error,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: isSubmitting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Text('Xác nhận hủy'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showAssignEquipmentDialog(ServiceSlotModel slot) async {
    final equipmentRepo = VendorEquipmentRepository();
    List<VendorEquipmentModel> equipmentList = [];
    bool isLoadingEq = true;
    bool isSubmittingEq = false;
    String? selectedEquipmentId;
    final availableUnits = slot.units.isNotEmpty
        ? slot.units.map((u) => u['unitNumber'] ?? 1).toList()
        : <int>[1];
    int selectedUnitNumber = availableUnits.first;
    final customUnitController = TextEditingController(text: selectedUnitNumber.toString());

    equipmentRepo.listEquipment().then((list) {
      equipmentList = list;
      if (list.isNotEmpty) {
        selectedEquipmentId = list.first.id;
      }
      isLoadingEq = false;
    }).catchError((_) {
      isLoadingEq = false;
    });

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          if (isLoadingEq && equipmentList.isEmpty) {
            equipmentRepo.listEquipment().then((list) {
              if (!ctx.mounted) return;
              setDialogState(() {
                equipmentList = list;
                if (selectedEquipmentId == null && list.isNotEmpty) {
                  selectedEquipmentId = list.first.id;
                }
                isLoadingEq = false;
              });
            }).catchError((_) {
              if (!ctx.mounted) return;
              setDialogState(() => isLoadingEq = false);
            });
          }

          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: const Row(
              children: [
                Icon(Icons.directions_boat_outlined, color: AppColors.secondary),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Gán thiết bị vào đơn vị ca chạy',
                    style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Ca chạy: ${slot.startTime.length >= 5 ? slot.startTime.substring(0, 5) : slot.startTime} - ${slot.endTime.length >= 5 ? slot.endTime.substring(0, 5) : slot.endTime}',
                    style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                  ),
                  const SizedBox(height: 12),
                  if (isLoadingEq)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 16),
                      child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                    )
                  else if (equipmentList.isEmpty)
                    const Text(
                      'Chưa có thiết bị nào trên hệ thống. Vui lòng thêm Cano/Kayak/SUP trong mục Quản lý thiết bị & Bảo trì.',
                      style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                    )
                  else ...[
                    const Text(
                      'Chọn thiết bị (Cano / Thuyền / SUP):',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 6),
                    DropdownButtonFormField<String>(
                      value: selectedEquipmentId,
                      isExpanded: true,
                      decoration: InputDecoration(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                      items: equipmentList.map((eq) {
                        return DropdownMenuItem<String>(
                          value: eq.id,
                          child: Text(
                            '${eq.name} (${eq.capacity} chỗ • ${eq.compatibilityGroup})',
                            style: const TextStyle(fontSize: 12.5),
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      }).toList(),
                      onChanged: (val) => setDialogState(() => selectedEquipmentId = val),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'Đơn vị thuyền / Cano trong ca (unitNumber):',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 6),
                    if (slot.units.isNotEmpty)
                      DropdownButtonFormField<int>(
                        value: selectedUnitNumber,
                        isExpanded: true,
                        decoration: InputDecoration(
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        items: slot.units.map((u) {
                          final uNum = u['unitNumber'] ?? 1;
                          final uCap = u['capacity'] ?? 0;
                          return DropdownMenuItem<int>(
                            value: uNum,
                            child: Text(
                              'Đơn vị #$uNum (Sức chứa $uCap khách)',
                              style: const TextStyle(fontSize: 12.5),
                            ),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) {
                            setDialogState(() {
                              selectedUnitNumber = val;
                              customUnitController.text = val.toString();
                            });
                          }
                        },
                      )
                    else
                      TextField(
                        controller: customUnitController,
                        keyboardType: TextInputType.number,
                        decoration: InputDecoration(
                          hintText: 'Nhập số thứ tự đơn vị (VD: 1)',
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: isSubmittingEq ? null : () => Navigator.pop(ctx),
                child: const Text('Đóng'),
              ),
              if (equipmentList.isNotEmpty)
                ElevatedButton(
                  onPressed: isSubmittingEq
                      ? null
                      : () async {
                          final eqId = selectedEquipmentId;
                          final unitNum = slot.units.isNotEmpty
                              ? selectedUnitNumber
                              : (int.tryParse(customUnitController.text.trim()) ?? 1);
                          if (eqId == null || eqId.isEmpty || unitNum < 1) {
                            ToastNotification.showError(ctx, 'Vui lòng chọn thiết bị và đơn vị hợp lệ');
                            return;
                          }
                          setDialogState(() => isSubmittingEq = true);
                          try {
                            await equipmentRepo.attachEquipmentToSlot(
                              equipmentId: eqId,
                              slotId: slot.slotId,
                              unitNumber: unitNum,
                            );
                            if (mounted) {
                              Navigator.pop(ctx);
                              ToastNotification.showSuccess(
                                context,
                                'Đã gán thiết bị vào đơn vị #$unitNum của ca ${slot.startTime.substring(0, 5)}',
                              );
                            }
                          } catch (e) {
                            setDialogState(() => isSubmittingEq = false);
                            if (mounted) {
                              ToastNotification.showError(ctx, 'Không thể gán thiết bị: $e');
                            }
                          }
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  child: isSubmittingEq
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('Gán thiết bị'),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _OperatingScheduleModal extends StatefulWidget {
  final String serviceId;

  const _OperatingScheduleModal({required this.serviceId});

  @override
  State<_OperatingScheduleModal> createState() => _OperatingScheduleModalState();
}

class _OperatingScheduleModalState extends State<_OperatingScheduleModal> {
  final _slotRepo = VendorSlotRepository();
  bool _isLoading = true;
  bool _isSaving = false;
  bool _active = true;
  String _startsOn = '';
  String _endsOn = '';
  List<dynamic> _rules = [];
  List<OperatingScheduleExceptionModel> _exceptions = [];

  @override
  void initState() {
    super.initState();
    _loadSchedule();
  }

  Future<void> _loadSchedule() async {
    setState(() => _isLoading = true);
    final results = await Future.wait([
      _slotRepo.getOperatingSchedule(widget.serviceId),
      _slotRepo.listScheduleExceptions(widget.serviceId),
    ]);
    final data = results[0] as Map<String, dynamic>?;
    final exList = results[1] as List<OperatingScheduleExceptionModel>;
    if (mounted) {
      if (data != null) {
        _active = data['active'] == true;
        _startsOn = data['startsOn']?.toString() ?? '';
        _endsOn = data['endsOn']?.toString() ?? '';
        _rules = data['rules'] as List<dynamic>? ?? [];
      } else {
        final now = DateTime.now();
        _startsOn = now.toIso8601String().split('T').first;
        _endsOn = now.add(const Duration(days: 90)).toIso8601String().split('T').first;
      }
      _exceptions = exList;
      setState(() => _isLoading = false);
    }
  }

  Future<void> _promptAddException() async {
    final nowStr = DateTime.now().toIso8601String().split('T').first;
    final fromCtrl = TextEditingController(text: nowStr);
    final toCtrl = TextEditingController(text: nowStr);
    final timeCtrl = TextEditingController();
    final reasonCtrl = TextEditingController(text: 'Bảo trì thiết bị / Nghỉ thời tiết biển');

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thêm Ngày nghỉ / Bảo trì'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: fromCtrl,
                decoration: const InputDecoration(
                  labelText: 'Từ ngày (YYYY-MM-DD)',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: toCtrl,
                decoration: const InputDecoration(
                  labelText: 'Đến ngày (YYYY-MM-DD)',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: timeCtrl,
                decoration: const InputDecoration(
                  labelText: 'Khung giờ (HH:mm - Để trống nếu nghỉ cả ngày)',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: reasonCtrl,
                decoration: const InputDecoration(
                  labelText: 'Lý do nghỉ / bảo trì',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Thêm ngoại lệ'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      try {
        final rawTime = timeCtrl.text.trim();
        final formattedTime = rawTime.isEmpty
            ? null
            : (rawTime.length == 5 ? '$rawTime:00' : rawTime);
        await _slotRepo.addScheduleException(
          serviceId: widget.serviceId,
          dateFrom: fromCtrl.text.trim(),
          dateTo: toCtrl.text.trim(),
          startTime: formattedTime,
          reason: reasonCtrl.text.trim(),
        );
        await _loadSchedule();
        if (mounted) {
          ToastNotification.showSuccess(context, 'Đã thêm ngày nghỉ / ngoại lệ lịch định kỳ');
        }
      } catch (e) {
        if (mounted) {
          ToastNotification.showError(context, 'Lỗi thêm ngày nghỉ: $e');
        }
      }
    }
  }

  Future<void> _deleteException(String exceptionId) async {
    try {
      await _slotRepo.removeScheduleException(widget.serviceId, exceptionId);
      await _loadSchedule();
      if (mounted) {
        ToastNotification.showSuccess(context, 'Đã xóa ngày nghỉ / ngoại lệ');
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Lỗi xóa ngoại lệ: $e');
      }
    }
  }

  Future<void> _saveSchedule() async {
    setState(() => _isSaving = true);
    try {
      await _slotRepo.updateOperatingSchedule(widget.serviceId, {
        'startsOn': _startsOn,
        'endsOn': _endsOn,
        'active': _active,
        'rules': _rules,
      });
      if (mounted) {
        Navigator.pop(context);
        ToastNotification.showSuccess(context, 'Đã cập nhật lịch hoạt động định kỳ');
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Lỗi lưu lịch định kỳ: $e');
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: _isLoading
          ? const Center(heightFactor: 4, child: CircularProgressIndicator())
          : SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      margin: const EdgeInsets.only(bottom: 16),
                      decoration: BoxDecoration(
                        color: const Color(0xFFCBD5E1),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Lịch hoạt động định kỳ',
                        style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                      ),
                      Switch(
                        value: _active,
                        activeColor: AppColors.secondary,
                        onChanged: (val) => setState(() => _active = val),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Thời gian áp dụng: $_startsOn  đến  $_endsOn',
                    style: const TextStyle(fontSize: 13, color: AppColors.tertiary),
                  ),
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHigh.withOpacity(0.35),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.info_outline, size: 18, color: AppColors.secondary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _active
                                ? 'Hệ thống tự động sinh slot hàng ngày theo quy tắc định kỳ đã cấu hình.'
                                : 'Lịch định kỳ đang tắt. Không có slot mới nào được sinh tự động.',
                            style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Ngày nghỉ / Bảo trì (Ngoại lệ)',
                        style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                      ),
                      TextButton.icon(
                        onPressed: _promptAddException,
                        icon: const Icon(Icons.add_circle_outline, size: 16, color: AppColors.secondary),
                        label: const Text(
                          'Thêm ngày nghỉ',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.secondary),
                        ),
                      ),
                    ],
                  ),
                  if (_exceptions.isEmpty)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Text(
                        'Chưa có ngày nghỉ hoặc lịch bảo trì ngoại lệ nào.',
                        style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                      ),
                    )
                  else
                    ..._exceptions.map((ex) => Container(
                          margin: const EdgeInsets.only(bottom: 8),
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.event_busy, size: 16, color: AppColors.error),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '${ex.dateFrom} → ${ex.dateTo}${ex.startTime != null && ex.startTime!.isNotEmpty ? " (${ex.startTime})" : " (Cả ngày)"}',
                                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                    ),
                                    if (ex.reason.isNotEmpty)
                                      Text(
                                        ex.reason,
                                        style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                                      ),
                                  ],
                                ),
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline, size: 18, color: AppColors.error),
                                onPressed: () => _deleteException(ex.id),
                              ),
                            ],
                          ),
                        )),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: _isSaving ? null : _saveSchedule,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.secondary,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                      child: _isSaving
                          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Text('Lưu thay đổi', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

class _SlotAdvisorModal extends StatefulWidget {
  final String serviceId;
  final ValueChanged<String> onSelectDate;

  const _SlotAdvisorModal({
    required this.serviceId,
    required this.onSelectDate,
  });

  @override
  State<_SlotAdvisorModal> createState() => _SlotAdvisorModalState();
}

class _SlotAdvisorModalState extends State<_SlotAdvisorModal> {
  final _aiRepo = VendorAiRepository();
  bool _isLoading = true;
  SlotAdvisorResultModel _result = const SlotAdvisorResultModel();

  @override
  void initState() {
    super.initState();
    _loadAdvisor();
  }

  Future<void> _loadAdvisor() async {
    setState(() => _isLoading = true);
    final res = await _aiRepo.getSlotAdvisor(serviceId: widget.serviceId, horizonDays: 7);
    if (mounted) {
      setState(() {
        _result = res;
        _isLoading = false;
      });
    }
  }

  Color _getSafetyColor(String level) {
    switch (level.toUpperCase()) {
      case 'SAFE':
        return const Color(0xFF16A34A);
      case 'CAUTION':
        return const Color(0xFFD97706);
      case 'DANGEROUS':
        return const Color(0xFFDC2626);
      default:
        return AppColors.secondary;
    }
  }

  String _getSafetyLabel(String level) {
    switch (level.toUpperCase()) {
      case 'SAFE':
        return 'Biển êm an toàn';
      case 'CAUTION':
        return 'Chú ý sóng cấp 3-4';
      case 'DANGEROUS':
        return 'Cảnh báo biển động';
      default:
        return 'Bình thường';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.85),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 16,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.only(bottom: 14),
              decoration: BoxDecoration(
                color: const Color(0xFFCBD5E1),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.auto_awesome, color: AppColors.secondary, size: 20),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'AI Cố vấn mở ca & Thời tiết',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                    ),
                    Text(
                      'Tối ưu doanh thu theo xu hướng dự báo biển',
                      style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                    ),
                  ],
                ),
              ),
              IconButton(
                icon: const Icon(Icons.refresh, size: 20, color: AppColors.tertiary),
                onPressed: _loadAdvisor,
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Overview metrics chips
          Row(
            children: [
              _buildStatChip('Đánh giá', '${_result.totalSlotsEvaluated} ca', const Color(0xFF0F766E), const Color(0xFFCCFBF1)),
              const SizedBox(width: 8),
              _buildStatChip('Nhu cầu cao', '${_result.highDemandSlotsCount}', const Color(0xFF2563EB), const Color(0xFFDBEAFE)),
              const SizedBox(width: 8),
              _buildStatChip('Rủi ro biển', '${_result.weatherRiskSlotsCount}', const Color(0xFFDC2626), const Color(0xFFFEE2E2)),
            ],
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: Color(0xFFE2E8F0)),
          const SizedBox(height: 10),

          // Recommendations List
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _result.recommendations.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.wb_sunny_outlined, size: 40, color: AppColors.tertiary),
                            const SizedBox(height: 8),
                            const Text(
                              'Các ca hiện tại đang tối ưu theo thời tiết.',
                              style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Đã phân tích ${_result.totalSlotsEvaluated} ca chạy trong 7 ngày tới.',
                              style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                            ),
                          ],
                        ),
                      )
                    : ListView.separated(
                        itemCount: _result.recommendations.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 10),
                        itemBuilder: (ctx, i) {
                          final rec = _result.recommendations[i];
                          final safetyColor = _getSafetyColor(rec.marineSafetyLevel);
                          final safetyText = _getSafetyLabel(rec.marineSafetyLevel);

                          return Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: const Color(0xFFE2E8F0)),
                              boxShadow: const [
                                BoxShadow(color: Color(0x04000000), blurRadius: 4, offset: Offset(0, 1)),
                              ],
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Row(
                                      children: [
                                        const Icon(Icons.access_time_rounded, size: 14, color: AppColors.secondary),
                                        const SizedBox(width: 4),
                                        Text(
                                          '${rec.startTime.length >= 5 ? rec.startTime.substring(0, 5) : rec.startTime} (${rec.date})',
                                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppColors.navyAnchor),
                                        ),
                                      ],
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: safetyColor.withValues(alpha: 0.12),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Text(
                                        safetyText,
                                        style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: safetyColor),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  rec.actionTitle,
                                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  rec.adviceReason,
                                  style: const TextStyle(fontSize: 11.5, color: AppColors.onSurfaceVariant, height: 1.35),
                                ),
                                const SizedBox(height: 10),
                                Align(
                                  alignment: Alignment.centerRight,
                                  child: OutlinedButton.icon(
                                    onPressed: () => widget.onSelectDate(rec.date),
                                    icon: const Icon(Icons.add_circle_outline, size: 14),
                                    label: const Text('Cấu hình ca này', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                                    style: OutlinedButton.styleFrom(
                                      foregroundColor: AppColors.secondary,
                                      side: const BorderSide(color: AppColors.secondary),
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                      visualDensity: VisualDensity.compact,
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatChip(String label, String value, Color textColor, Color bgColor) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 8),
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          children: [
            Text(value, style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: textColor)),
            const SizedBox(height: 1),
            Text(label, style: TextStyle(fontSize: 10, color: textColor.withValues(alpha: 0.85)), maxLines: 1),
          ],
        ),
      ),
    );
  }
}