import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/data/vendor_mock_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';
import 'slot_config_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';

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
  final _db = VendorMockDatabase.instance;
  final _slotRepo = VendorSlotRepository();
  late String _selectedServiceId;
  DateTime _selectedDate = DateTime.now();
  String _slotFilter = 'all'; // all, open, blocked

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
      if (_slotFilter == 'open') return !s.isBlocked && s.bookedCount < s.capacity;
      if (_slotFilter == 'blocked') return s.isBlocked;
      return true;
    }).toList();

    // Summary metrics for day
    final daySlots = _db.slots.where((s) => s.serviceId == _selectedServiceId && s.matchesDate(_selectedDate));
    final totalSlots = daySlots.length;
    final totalBooked = daySlots.fold<int>(0, (sum, s) => sum + s.bookedCount);
    final totalCap = daySlots.fold<int>(0, (sum, s) => sum + s.capacity);

    final fillRate = totalCap > 0 ? (totalBooked / totalCap * 100).toInt() : 0;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                LocalizedText(
                  'DANASEA',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                      ),
                ),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const LocalizedText('VENDOR', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                ),
              ],
            ),
            const LocalizedText('Lịch hoạt động & Slot', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined, color: AppColors.secondary),
            onPressed: () {
              if (widget.onOpenNotifications != null) {
                widget.onOpenNotifications!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const NotificationSafetyScreen()),
                );
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.account_circle, color: AppColors.primary),
            onPressed: () {
              if (widget.onOpenProfile != null) {
                widget.onOpenProfile!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const BusinessProfileScreen()),
                );
              }
            },
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Service Selector Floating Card
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.secondaryContainer,
                      ),
                      child: const Icon(Icons.surfing, color: AppColors.onSecondaryContainer, size: 20),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const LocalizedText('DỊCH VỤ ÁP DỤNG', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.tertiary, letterSpacing: 0.5)),
                          DropdownButtonHideUnderline(
                            child: DropdownButton<String>(
                              value: services.any((s) => s.serviceId == _selectedServiceId) ? _selectedServiceId : null,
                              isDense: true,
                              isExpanded: true,
                              hint: const LocalizedText('Chưa có dịch vụ nào', style: TextStyle(fontSize: 13, color: AppColors.outline)),
                              items: services.map((s) {
                                return DropdownMenuItem<String>(
                                  value: s.serviceId,
                                  child: LocalizedText(s.nameVi, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface), overflow: TextOverflow.ellipsis),
                                );
                              }).toList(),
                              onChanged: (val) {
                                if (val != null) {
                                  setState(() => _selectedServiceId = val);
                                  _slotRepo.getSlotsForService(val, _selectedDate);
                                }
                              },
                            ),
                          ),
                        ],
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
                      child: Container(
                        width: 38,
                        height: 38,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.primary,
                        ),
                        child: const Icon(Icons.add, color: AppColors.onPrimary, size: 22),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Weekly Horizontal Date Strip
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        Flexible(
                          child: LocalizedText(
                            'Tháng ${_selectedDate.month} / ${_selectedDate.year}',
                            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.secondaryFixed, borderRadius: BorderRadius.circular(10)),
                          child: const LocalizedText('Tuần 18', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixed)),
                        ),
                      ],
                    ),
                  ),
                  TextButton.icon(
                    onPressed: () {
                      final now = DateTime.now();
                      setState(() {
                        _selectedDate = DateTime(now.year, now.month, now.day);
                      });
                    },
                    icon: const Icon(Icons.calendar_today, size: 14, color: AppColors.secondary),
                    label: const LocalizedText('Hôm nay', style: TextStyle(fontSize: 12, color: AppColors.secondary, fontWeight: FontWeight.bold)),
                  ),
                ],
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
                          color: isSelected ? AppColors.primary : AppColors.surfaceContainerLowest,
                          borderRadius: BorderRadius.circular(16),
                          boxShadow: AppShapes.shadowSm,
                        ),
                        child: Column(
                          children: [
                            LocalizedText(dayName,
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: isSelected ? Colors.white70 : AppColors.tertiary,
                                )),
                            const SizedBox(height: 4),
                            LocalizedText(
                              dayDate.day.toString().padLeft(2, '0'),
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: isSelected ? Colors.white : AppColors.onSurface,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: isSelected ? Colors.white.withOpacity(0.25) : AppColors.surfaceContainerHigh,
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
              const SizedBox(height: 16),

              // Day Highlights & Tide Rhythm Bento Card
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainer,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Row(
                            children: [
                              const Icon(Icons.tsunami, size: 18, color: AppColors.secondary),
                              const SizedBox(width: 6),
                              Expanded(
                                child: LocalizedText(
                                  'Tổng quan ngày ${_selectedDate.day}/${_selectedDate.month}',
                                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.secondaryFixed, borderRadius: BorderRadius.circular(10)),
                          child: const Row(
                            children: [
                              Icon(Icons.waves, size: 12, color: AppColors.secondary),
                              SizedBox(width: 3),
                              LocalizedText('Sóng êm 0.4m', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: _buildBentoMetric('Khung giờ', '$totalSlots ca', 'Sáng & Chiều'),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: _buildBentoMetric('Khách đã đặt', '$totalBooked/$totalCap', 'Còn ${totalCap - totalBooked} chỗ'),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: _buildBentoMetric('Tỉ lệ lấp đầy', '$fillRate%', 'Công suất', progress: fillRate / 100),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

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
              const SizedBox(height: 12),

              // Slot Cards List
              if (slotsForDay.isEmpty)
                Container(
                  padding: const EdgeInsets.all(32),
                  width: double.infinity,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusMd,
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

  Widget _buildBentoMetric(String label, String value, String sub, {double? progress}) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LocalizedText(label, style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
          const SizedBox(height: 4),
          LocalizedText(value, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
          const SizedBox(height: 2),
          LocalizedText(sub, style: const TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.w600)),
          if (progress != null) ...[
            const SizedBox(height: 4),
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: progress.clamp(0.0, 1.0),
                minHeight: 4,
                backgroundColor: AppColors.surfaceContainerHigh,
                valueColor: const AlwaysStoppedAnimation<Color>(AppColors.secondary),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildSlotFilterChip(String key, String label) {
    final active = _slotFilter == key;
    return InkWell(
      onTap: () => setState(() => _slotFilter = key),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.onSurface : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
        ),
        child: LocalizedText(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: active ? FontWeight.bold : FontWeight.normal,
            color: active ? Colors.white : AppColors.onSurfaceVariant,
          ),
        ),
      ),
    );
  }

  Widget _buildSlotItemCard(ServiceSlotModel slot) {
    final bool isFull = slot.bookedCount >= slot.capacity;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusMd,
        border: slot.isBlocked ? Border.all(color: AppColors.errorContainer, width: 1.5) : null,
        boxShadow: AppShapes.shadowSm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(
                    slot.startTime.startsWith('05') ? Icons.wb_twilight : Icons.wb_sunny,
                    size: 18,
                    color: slot.isBlocked ? AppColors.tertiary : AppColors.secondary,
                  ),
                  const SizedBox(width: 6),
                  LocalizedText(
                    '${slot.startTime.substring(0, 5)} - ${slot.endTime.substring(0, 5)}',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: slot.isBlocked ? AppColors.tertiary : AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              if (slot.isBlocked)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.block, size: 12, color: AppColors.error),
                      SizedBox(width: 4),
                      LocalizedText('TẠM KHÓA', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onErrorContainer)),
                    ],
                  ),
                )
              else if (isFull)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.primaryFixed,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const LocalizedText('HẾT CHỖ', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant)),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.secondaryContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const LocalizedText('CÒN CHỖ', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer)),
                ),
            ],
          ),
          const SizedBox(height: 10),

          // Capacity & Booked row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const LocalizedText('Khách đã đặt: ', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
                  LocalizedText('${slot.bookedCount}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.primary)),
                  LocalizedText(' / ${slot.capacity} khách', style: const TextStyle(fontSize: 12, color: AppColors.tertiary)),
                ],
              ),
              if (slot.heldCount > 0)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(8)),
                  child: LocalizedText('Tạm giữ: ${slot.heldCount}', style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: slot.capacity > 0 ? (slot.bookedCount / slot.capacity).clamp(0.0, 1.0) : 0,
              minHeight: 5,
              backgroundColor: AppColors.surfaceContainerHigh,
              valueColor: AlwaysStoppedAnimation<Color>(slot.isBlocked ? AppColors.tertiary : AppColors.secondary),
            ),
          ),
          const SizedBox(height: 12),

          // Actions
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              // Toggle block button
              TextButton.icon(
                onPressed: () {
                  _db.toggleSlotBlocked(slot.slotId);
                  ToastNotification.showInfo(
                    context,
                    slot.isBlocked ? 'Đã mở khóa slot ${slot.startTime}' : 'Đã tạm khóa slot ${slot.startTime}',
                  );
                },
                icon: Icon(slot.isBlocked ? Icons.lock_open : Icons.block, size: 15, color: slot.isBlocked ? AppColors.secondary : AppColors.error),
                label: LocalizedText(
                  slot.isBlocked ? 'Mở khóa' : 'Tạm khóa',
                  style: TextStyle(fontSize: 12, color: slot.isBlocked ? AppColors.secondary : AppColors.error),
                ),
              ),
              const SizedBox(width: 8),
              // Edit config button
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
                icon: const Icon(Icons.tune, size: 14),
                label: const LocalizedText('Cấu hình', style: TextStyle(fontSize: 12)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.surfaceContainerLow,
                  foregroundColor: AppColors.secondary,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
