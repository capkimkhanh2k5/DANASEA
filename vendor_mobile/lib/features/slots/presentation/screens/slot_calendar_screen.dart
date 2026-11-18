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
          'Lịch chạy',
          style: TextStyle(
            fontSize: 20,
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
          padding: const EdgeInsets.fromLTRB(16.0, 6.0, 16.0, 110.0),
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
                    // Main Selector Bar (Tinh gọn, cân đối)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
                                padding: const EdgeInsets.symmetric(vertical: 4.0),
                                child: Row(
                                  children: [
                                    Container(
                                      width: 32,
                                      height: 32,
                                      decoration: BoxDecoration(
                                        color: AppColors.secondary.withValues(alpha: 0.1),
                                        shape: BoxShape.circle,
                                      ),
                                      child: const Icon(
                                        Icons.surfing_rounded,
                                        color: AppColors.secondary,
                                        size: 17,
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          const Text(
                                            'Dịch vụ áp dụng',
                                            style: TextStyle(
                                              fontSize: 10,
                                              fontWeight: FontWeight.w600,
                                              color: AppColors.tertiary,
                                            ),
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
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
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    AnimatedRotation(
                                      turns: _isServiceDropdownExpanded ? 0.5 : 0.0,
                                      duration: const Duration(milliseconds: 200),
                                      child: const Icon(
                                        Icons.keyboard_arrow_down_rounded,
                                        size: 20,
                                        color: AppColors.secondary,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
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
                            icon: const Icon(Icons.add_rounded, size: 16),
                            label: const Text(
                              'Thêm ca',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                            ),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.secondary,
                              foregroundColor: Colors.white,
                              elevation: 0,
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 0),
                              minimumSize: const Size(0, 36),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
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
    final double fillRatio = slot.capacity > 0
        ? (slot.bookedCount / slot.capacity).clamp(0.0, 1.0)
        : 0.0;
    final int fillPercent = (fillRatio * 100).round();
    final Color occupancyColor = slot.isBlocked
        ? const Color(0xFF94A3B8)
        : (fillRatio >= 1.0
            ? const Color(0xFFDC2626)
            : (fillRatio >= 0.8
                ? const Color(0xFFD97706)
                : (fillRatio >= 0.4
                    ? const Color(0xFF0D9488)
                    : const Color(0xFF004D56))));

    final hasUnits = slot.inventoryType == 'SHARED_CAPACITY_UNITS' || slot.units.isNotEmpty;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
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
          // 1. Header: Time, Status Badge & 3-dot Menu (Tạm khóa / Hủy ca)
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withValues(alpha: 0.35),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.access_time_rounded,
                  size: 15,
                  color: AppColors.secondary,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${slot.startTime.length >= 5 ? slot.startTime.substring(0, 5) : slot.startTime} - ${slot.endTime.length >= 5 ? slot.endTime.substring(0, 5) : slot.endTime}',
                  style: TextStyle(
                    fontSize: 15.5,
                    fontWeight: FontWeight.bold,
                    color: slot.isBlocked ? AppColors.tertiary : AppColors.navyAnchor,
                  ),
                ),
              ),
              if (slot.status == SlotStatus.cancelled || slot.status == SlotStatus.closed)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: LocalizedText(
                    slot.status.labelVi,
                    style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.tertiary),
                  ),
                )
              else if (slot.isBlocked)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
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
                    color: AppColors.secondaryContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const LocalizedText('CÒN CHỖ', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                ),
              const SizedBox(width: 4),
              // Menu 3 chấm gom các thao tác phụ (Tạm khóa / Mở khóa, Hủy ca khẩn cấp)
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert_rounded, size: 20, color: AppColors.tertiary),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
                tooltip: 'Thao tác khác',
                onSelected: (val) {
                  if (val == 'toggle_block') {
                    _db.toggleSlotBlocked(slot.slotId);
                    ToastNotification.showInfo(
                      context,
                      slot.isBlocked ? 'Đã mở khóa slot ${slot.startTime}' : 'Đã tạm khóa slot ${slot.startTime}',
                    );
                  } else if (val == 'cancel_slot') {
                    _showCancelSlotDialog(slot);
                  }
                },
                itemBuilder: (ctx) => [
                  PopupMenuItem(
                    value: 'toggle_block',
                    child: Row(
                      children: [
                        Icon(
                          slot.isBlocked ? Icons.lock_open_rounded : Icons.block_rounded,
                          size: 18,
                          color: slot.isBlocked ? AppColors.secondary : AppColors.tertiary,
                        ),
                        const SizedBox(width: 8),
                        Text(slot.isBlocked ? 'Mở khóa ca chạy' : 'Tạm khóa ca chạy'),
                      ],
                    ),
                  ),
                  if (slot.status != SlotStatus.cancelled)
                    const PopupMenuItem(
                      value: 'cancel_slot',
                      child: Row(
                        children: [
                          Icon(Icons.event_busy_rounded, size: 18, color: AppColors.error),
                          SizedBox(width: 8),
                          Text('Hủy ca khẩn cấp', style: TextStyle(color: AppColors.error)),
                        ],
                      ),
                    ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),

          // 2. Occupancy progress bar (tinh gọn, không lồng hộp xám)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Đã đặt: ${slot.bookedCount}/${slot.capacity} khách${slot.heldCount > 0 ? ' (giữ ${slot.heldCount})' : ''}',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: occupancyColor,
                ),
              ),
              Text(
                '$fillPercent%',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: occupancyColor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 5),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: fillRatio,
              minHeight: 5,
              backgroundColor: const Color(0xFFE2E8F0),
              valueColor: AlwaysStoppedAnimation<Color>(occupancyColor),
            ),
          ),

          // 3. Equipment row (nếu có thuyền/cano)
          if (hasUnits) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                const Icon(Icons.directions_boat_filled_outlined, size: 13, color: Color(0xFF0369A1)),
                const SizedBox(width: 4),
                const Text(
                  'Thuyền:',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: (slot.units.isNotEmpty
                              ? slot.units
                              : [
                                  {'unitNumber': 1, 'capacity': (slot.capacity / 2).ceil()},
                                  {'unitNumber': 2, 'capacity': (slot.capacity / 2).floor()},
                                ])
                          .map((u) {
                        final uNum = u['unitNumber'] ?? 1;
                        final uCap = u['capacity'] ?? 0;
                        return Padding(
                          padding: const EdgeInsets.only(right: 6),
                          child: InkWell(
                            onTap: () => _showAssignEquipmentDialog(slot, initialUnitNumber: uNum),
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: const Color(0xFFE0F2FE),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: const Color(0xFFBAE6FD)),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    '#$uNum ($uCap chỗ)',
                                    style: const TextStyle(
                                      fontSize: 10.5,
                                      fontWeight: FontWeight.bold,
                                      color: Color(0xFF0369A1),
                                    ),
                                  ),
                                  const SizedBox(width: 3),
                                  const Icon(Icons.add_link, size: 11, color: Color(0xFF0284C7)),
                                ],
                              ),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ),
              ],
            ),
          ],

          // 4. Clean Bottom Actions (1 dòng duy nhất)
          if (slot.status != SlotStatus.cancelled) ...[
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (hasUnits) ...[
                  OutlinedButton.icon(
                    onPressed: () => _showAssignEquipmentDialog(slot),
                    icon: const Icon(Icons.directions_boat_outlined, size: 13),
                    label: const Text('Gán thiết bị', style: TextStyle(fontSize: 11.5)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.secondary,
                      side: BorderSide(color: AppColors.secondary.withValues(alpha: 0.35)),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 0),
                      minimumSize: const Size(0, 32),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                ElevatedButton.icon(
                  onPressed: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => SlotConfigScreen(
                          slot: slot,
                          serviceId: _selectedServiceId,
                        ),
                      ),
                    );
                  },
                  icon: const Icon(Icons.edit_outlined, size: 13),
                  label: const Text('Chỉnh sửa', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 0),
                    minimumSize: const Size(0, 32),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                ),
              ],
            ),
          ],
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
        onSelectSlot: (rec) async {
          final serviceId = _selectedServiceId;
          Navigator.pop(ctx);
          final slots = await _slotRepo.getSlotsForService(serviceId, DateTime.tryParse(rec.date) ?? _selectedDate);
          if (!mounted) return;
          final slot = slots.where((s) => s.slotId == rec.slotId && s.serviceId == serviceId).firstOrNull;
          if (slot == null) {
            ToastNotification.showError(context, 'Không tìm thấy ca chạy này. Vui lòng tải lại lịch.');
            return;
          }
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => SlotConfigScreen(
                serviceId: serviceId,
                slot: slot,
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

  Future<void> _showAssignEquipmentDialog(ServiceSlotModel slot, {int? initialUnitNumber}) async {
    final equipmentRepo = VendorEquipmentRepository();
    List<VendorEquipmentModel> equipmentList = [];
    bool isLoadingEq = true;
    bool isSubmittingEq = false;
    String? selectedEquipmentId;
    final availableUnits = slot.units.isNotEmpty
        ? slot.units.map((u) => u['unitNumber'] ?? 1).toList()
        : <int>[1];
    int selectedUnitNumber = initialUnitNumber ?? availableUnits.first;
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
                                'Đã gán thiết bị vào đơn vị #$unitNum của ca ${slot.startTime.length >= 5 ? slot.startTime.substring(0, 5) : slot.startTime}',
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
                    'Thời gian áp dụng: ${_startsOn.isNotEmpty ? _startsOn : "Từ hôm nay"} ${_endsOn.isNotEmpty ? "đến $_endsOn" : "(Không thời hạn)"}',
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
                      const Expanded(
                        child: Text(
                          'Ngày nghỉ / Bảo trì (Ngoại lệ)',
                          style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 4),
                      TextButton.icon(
                        onPressed: _promptAddException,
                        style: TextButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                          visualDensity: VisualDensity.compact,
                        ),
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
  final ValueChanged<SlotRecommendationModel> onSelectSlot;

  const _SlotAdvisorModal({
    required this.serviceId,
    required this.onSelectSlot,
  });

  @override
  State<_SlotAdvisorModal> createState() => _SlotAdvisorModalState();
}

class _SlotAdvisorModalState extends State<_SlotAdvisorModal> {
  final _aiRepo = VendorAiRepository();
  bool _isLoading = true;
  SlotAdvisorResultModel? _result;

  @override
  void initState() {
    super.initState();
    _loadAdvisor();
  }

  Future<void> _loadAdvisor() async {
    setState(() {
      _isLoading = true;
      _result = null;
    });
    try {
      final result = await _aiRepo.getSlotAdvisor(serviceId: widget.serviceId, horizonDays: 7);
      if (mounted) setState(() => _result = result);
    } catch (_) {
      // A missing result displays the retry state below.
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Color _getSafetyColor(String level) {
    switch (level.toUpperCase()) {
      case 'SAFE':
        return const Color(0xFF16A34A);
      case 'MODERATE':
      case 'CAUTION':
        return const Color(0xFFD97706);
      case 'HIGH_RISK':
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
      case 'MODERATE':
      case 'CAUTION':
        return 'Cần lưu ý điều kiện biển';
      case 'HIGH_RISK':
      case 'DANGEROUS':
        return 'Cảnh báo biển động';
      case 'NOT_REQUIRED':
        return 'Không yêu cầu đánh giá biển';
      default:
        return 'Chưa xác định điều kiện biển';
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = _result;
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
                onPressed: _isLoading ? null : _loadAdvisor,
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Overview metrics chips
          if (result != null)
          Row(
            children: [
              _buildStatChip('Đánh giá', '${result.totalSlotsEvaluated} ca', const Color(0xFF0F766E), const Color(0xFFCCFBF1)),
              const SizedBox(width: 8),
              _buildStatChip('Nhu cầu cao', '${result.highDemandSlotsCount}', const Color(0xFF2563EB), const Color(0xFFDBEAFE)),
              const SizedBox(width: 8),
              _buildStatChip('Rủi ro biển', '${result.weatherRiskSlotsCount}', const Color(0xFFDC2626), const Color(0xFFFEE2E2)),
            ],
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: Color(0xFFE2E8F0)),
          const SizedBox(height: 10),

          // Recommendations List
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : result == null
                    ? Center(child: TextButton(onPressed: _loadAdvisor, child: const Text('Không tải được khuyến nghị ca chạy. Thử lại')))
                    : result.recommendations.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.wb_sunny_outlined, size: 40, color: AppColors.tertiary),
                            const SizedBox(height: 8),
                            const Text(
                              'Chưa có khuyến nghị cho các ca trong kỳ này.',
                              style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Đã phân tích ${result.totalSlotsEvaluated} ca chạy trong 7 ngày tới.',
                              style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                            ),
                          ],
                        ),
                      )
                    : ListView.separated(
                        itemCount: result.recommendations.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 10),
                        itemBuilder: (ctx, i) {
                          final rec = result.recommendations[i];
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
                                    onPressed: () => widget.onSelectSlot(rec),
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