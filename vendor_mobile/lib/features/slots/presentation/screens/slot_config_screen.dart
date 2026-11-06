import '../../../../core/data/vendor_mock_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/vendor_button.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/widgets/alarm_wheel_picker.dart';

class SlotConfigScreen extends StatefulWidget {
  final ServiceSlotModel? slot;
  final String serviceId;
  final String? initialDate;

  const SlotConfigScreen({
    super.key,
    this.slot,
    this.serviceId = 'srv-001',
    this.initialDate,
  });

  @override
  State<SlotConfigScreen> createState() => _SlotConfigScreenState();
}

class _SlotConfigScreenState extends State<SlotConfigScreen> {
  final _db = VendorMockDatabase.instance;
  final _slotRepo = VendorSlotRepository();
  bool _isSaving = false;

  late TextEditingController _dateController;
  late TextEditingController _startTimeController;
  late TextEditingController _endTimeController;
  late int _capacity;
  late bool _isBlocked;
  bool _repeatWeekdays = false;

  bool get _isEdit => widget.slot != null;
  int get _bookedCount => widget.slot?.bookedCount ?? 0;
  int get _heldCount => widget.slot?.heldCount ?? 0;
  int get _minAllowedCapacity => _bookedCount + _heldCount;

  @override
  void initState() {
    super.initState();
    if (_isEdit) {
      final s = widget.slot!;
      _dateController = TextEditingController(text: s.slotDate);
      _startTimeController = TextEditingController(text: s.startTime.substring(0, 5));
      _endTimeController = TextEditingController(text: s.endTime.substring(0, 5));
      _capacity = s.capacity;
      _isBlocked = s.isBlocked;
    } else {
      _dateController = TextEditingController(text: widget.initialDate ?? '2025-05-04');
      _startTimeController = TextEditingController(text: '05:00');
      _endTimeController = TextEditingController(text: '07:00');
      _capacity = 10;
      _isBlocked = false;
    }
  }

  @override
  void dispose() {
    _dateController.dispose();
    _startTimeController.dispose();
    _endTimeController.dispose();
    super.dispose();
  }

  void _changeCapacity(int delta) {
    final newCap = _capacity + delta;
    if (newCap < _minAllowedCapacity) {
      ToastNotification.showError(
        context,
        'Không thể giảm sức chứa dưới tổng số khách đã đặt & tạm giữ ($_minAllowedCapacity khách)',
      );
      return;
    }
    if (newCap < 1) return;
    setState(() {
      _capacity = newCap;
    });
  }

  Future<void> _pickDate() async {
    final currentDt = DateTime.tryParse(_dateController.text) ?? DateTime.now();
    final picked = await AlarmWheelDateDialog.show(
      context,
      initialDate: currentDt,
      title: 'Chọn ngày áp dụng',
    );
    if (picked != null) {
      setState(() {
        _dateController.text =
            '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
      });
    }
  }

  Future<void> _pickTime({required bool isStart}) async {
    final currentStr = isStart ? _startTimeController.text : _endTimeController.text;
    final parts = currentStr.split(':');
    final initialHour = parts.isNotEmpty ? (int.tryParse(parts[0]) ?? (isStart ? 5 : 7)) : (isStart ? 5 : 7);
    final initialMinute = parts.length > 1 ? (int.tryParse(parts[1]) ?? 0) : 0;

    final picked = await AlarmWheelTimeDialog.show(
      context,
      initialTime: TimeOfDay(hour: initialHour, minute: initialMinute),
      title: isStart ? 'Chọn giờ bắt đầu' : 'Chọn giờ kết thúc',
      subtitle: isStart ? 'Khung giờ bắt đầu ca chạy trải nghiệm' : 'Khung giờ kết thúc ca chạy trải nghiệm',
    );

    if (picked != null) {
      final formatted = '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
      setState(() {
        if (isStart) {
          _startTimeController.text = formatted;
        } else {
          _endTimeController.text = formatted;
        }
      });
    }
  }

  Future<void> _saveSlot() async {
    final date = _dateController.text.trim();
    final start = _startTimeController.text.trim();
    final end = _endTimeController.text.trim();

    if (_capacity < _minAllowedCapacity) {
      ToastNotification.showError(
        context,
        'Sức chứa tối thiểu phải từ $_minAllowedCapacity khách trở lên',
      );
      return;
    }

    setState(() => _isSaving = true);

    DateTime parsedDate = DateTime.tryParse(date) ?? DateTime.now();

    final newSlot = ServiceSlotModel(
      slotId: widget.slot?.slotId ?? 'slt-${DateTime.now().millisecondsSinceEpoch % 10000}',
      serviceId: widget.serviceId,
      date: parsedDate,
      slotDate: date,
      startTime: start.length == 5 ? '$start:00' : start,
      endTime: end.length == 5 ? '$end:00' : end,
      capacity: _capacity,
      bookedCount: _bookedCount,
      heldCount: _heldCount,
      isBlocked: _isBlocked,
      createdAt: widget.slot?.createdAt ?? DateTime.now(),
      updatedAt: DateTime.now(),
    );

    try {
      await _slotRepo.saveSlot(newSlot);
      if (mounted) {
        ToastNotification.showSuccess(context, 'Đã lưu ca chạy thành công');
        Navigator.of(context).maybePop();
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Lỗi lưu ca chạy: $e');
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final service = _db.services.firstWhere(
      (s) => s.serviceId == widget.serviceId,
      orElse: () => _db.services.first,
    );

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: LocalizedText(
          _isEdit ? 'Chỉnh sửa ca chạy' : 'Thêm ca chạy mới',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: AppColors.onSurface),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Service Header Info
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Row(
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.kayaking, color: AppColors.onSecondaryContainer, size: 24),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(color: AppColors.secondaryContainer, borderRadius: BorderRadius.circular(8)),
                            child: const LocalizedText('DỊCH VỤ ĐANG CHỌN', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer)),
                          ),
                          const SizedBox(height: 4),
                          LocalizedText(service.nameVi, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                          const SizedBox(height: 2),
                          Row(
                            children: [
                              const Icon(Icons.location_on, size: 12, color: AppColors.secondary),
                              const SizedBox(width: 3),
                              Expanded(
                                child: LocalizedText(service.meetingPointName, style: const TextStyle(fontSize: 11, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // CARD 1: Date & Recurrence
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Expanded(
                          child: Row(
                            children: [
                              Icon(Icons.calendar_today, size: 18, color: AppColors.primary),
                              SizedBox(width: 6),
                              Expanded(
                                child: LocalizedText('Ngày áp dụng',
                                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.secondaryFixed, borderRadius: BorderRadius.circular(10)),
                          child: const LocalizedText('Hôm nay', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixed)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    InkWell(
                      onTap: _pickDate,
                      borderRadius: BorderRadius.circular(24),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.event, size: 20, color: AppColors.tertiary),
                            const SizedBox(width: 10),
                            Expanded(
                              child: LocalizedText(_dateController.text, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.onSurface)),
                            ),
                            const Icon(Icons.edit_calendar, size: 18, color: AppColors.secondary),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Material(
                      color: Colors.transparent,
                      child: CheckboxListTile(
                        value: _repeatWeekdays,
                        onChanged: (val) => setState(() => _repeatWeekdays = val ?? false),
                        contentPadding: EdgeInsets.zero,
                        controlAffinity: ListTileControlAffinity.leading,
                        title: const LocalizedText('Lặp lại cho các ngày trong tuần (T2 - T6)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                        subtitle: const LocalizedText('Tạo khung giờ tương ứng cho các ngày làm việc', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // CARD 2: Operating Time
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Expanded(
                          child: Row(
                            children: [
                              Icon(Icons.schedule, size: 18, color: AppColors.secondary),
                              SizedBox(width: 6),
                              Expanded(
                                child: LocalizedText(
                                  'Khung giờ hoạt động',
                                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.primaryFixed, borderRadius: BorderRadius.circular(10)),
                          child: LocalizedText('Thời lượng: ${service.durationMinutes} phút',
                              style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const LocalizedText('Giờ bắt đầu', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                              const SizedBox(height: 4),
                              InkWell(
                                onTap: () => _pickTime(isStart: true),
                                borderRadius: BorderRadius.circular(24),
                                child: Container(
                                  height: 48,
                                  padding: const EdgeInsets.symmetric(horizontal: 14),
                                  decoration: BoxDecoration(
                                    color: AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(24),
                                  ),
                                  child: Row(
                                    children: [
                                      const Icon(Icons.wb_twilight, size: 18, color: AppColors.secondary),
                                      Expanded(
                                        child: Text(
                                          _startTimeController.text,
                                          textAlign: TextAlign.center,
                                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                        ),
                                      ),
                                      const Icon(Icons.unfold_more, size: 16, color: AppColors.outline),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const LocalizedText('Giờ kết thúc', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                              const SizedBox(height: 4),
                              InkWell(
                                onTap: () => _pickTime(isStart: false),
                                borderRadius: BorderRadius.circular(24),
                                child: Container(
                                  height: 48,
                                  padding: const EdgeInsets.symmetric(horizontal: 14),
                                  decoration: BoxDecoration(
                                    color: AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(24),
                                  ),
                                  child: Row(
                                    children: [
                                      const Icon(Icons.wb_sunny, size: 18, color: AppColors.primaryContainer),
                                      Expanded(
                                        child: Text(
                                          _endTimeController.text,
                                          textAlign: TextAlign.center,
                                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                        ),
                                      ),
                                      const Icon(Icons.unfold_more, size: 16, color: AppColors.outline),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // CARD 3: Capacity & Booked Stepper
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.groups, size: 18, color: AppColors.primary),
                        SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            'Sức chứa tối đa',
                            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Capacity Stepper
                    Row(
                      children: [
                        InkWell(
                          onTap: () => _changeCapacity(-1),
                          child: Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: AppColors.surfaceContainerLow,
                            ),
                            child: const Icon(Icons.remove, color: AppColors.onSurface, size: 20),
                          ),
                        ),
                        Expanded(
                          child: Center(
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              crossAxisAlignment: CrossAxisAlignment.baseline,
                              textBaseline: TextBaseline.alphabetic,
                              children: [
                                LocalizedText('$_capacity', style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                const SizedBox(width: 4),
                                const LocalizedText('khách', style: TextStyle(fontSize: 13, color: AppColors.tertiary)),
                              ],
                            ),
                          ),
                        ),
                        InkWell(
                          onTap: () => _changeCapacity(1),
                          child: Container(
                            width: 44,
                            height: 44,
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              color: AppColors.secondaryContainer,
                            ),
                            child: const Icon(Icons.add, color: AppColors.onSecondaryContainer, size: 20),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Read-only Booked & Held Info Box
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainer,
                        borderRadius: AppShapes.radiusSm,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Expanded(
                                child: Row(
                                  children: [
                                    Icon(Icons.lock_outline, size: 14, color: AppColors.tertiary),
                                    SizedBox(width: 4),
                                    Expanded(
                                      child: LocalizedText(
                                        'Số khách đã đặt:',
                                        style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(color: AppColors.surfaceDim, borderRadius: BorderRadius.circular(10)),
                                child: LocalizedText('$_bookedCount khách', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                              ),
                            ],
                          ),
                          if (_heldCount > 0) ...[
                            const SizedBox(height: 6),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                const Expanded(
                                  child: Row(
                                    children: [
                                      Icon(Icons.timer_outlined, size: 14, color: AppColors.tertiary),
                                      SizedBox(width: 4),
                                      Expanded(
                                        child: LocalizedText(
                                          'Tạm giữ thanh toán:',
                                          style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 8),
                                LocalizedText('$_heldCount khách', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.primary)),
                              ],
                            ),
                          ],
                          const SizedBox(height: 8),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(3),
                            child: LinearProgressIndicator(
                              value: _capacity > 0 ? (_bookedCount / _capacity).clamp(0.0, 1.0) : 0,
                              minHeight: 5,
                              backgroundColor: AppColors.surfaceContainerHighest,
                              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.primary),
                            ),
                          ),
                          const SizedBox(height: 6),
                          const LocalizedText(
                            'Dữ liệu chỉ đọc từ hệ thống đơn đặt của khách. Đối tác không thể giảm sức chứa dưới tổng số khách đã đặt & tạm giữ.',
                            style: TextStyle(fontSize: 10, color: AppColors.tertiary),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Block toggle
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: AppShapes.radiusSm,
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.block, size: 20, color: AppColors.primary),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const LocalizedText('Chặn nhận thêm khách', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                LocalizedText(
                                  'Ngừng mở bán mới trên ứng dụng mà không ảnh hưởng tới $_bookedCount khách đã đặt thành công.',
                                  style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                                ),
                              ],
                            ),
                          ),
                          Switch.adaptive(
                            value: _isBlocked,
                            activeThumbColor: AppColors.primary,
                            activeTrackColor: AppColors.primaryFixed,
                            onChanged: (val) => setState(() => _isBlocked = val),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Action buttons
              VendorButton(
                text: 'Lưu cấu hình khung giờ',
                icon: Icons.save,
                isLoading: _isSaving,
                onPressed: _isSaving ? null : _saveSlot,
              ),
              const SizedBox(height: 10),
              if (_isEdit && _bookedCount == 0)
                Center(
                  child: TextButton.icon(
                    onPressed: () {
                      _db.deleteSlot(widget.slot!.slotId);
                      ToastNotification.showSuccess(context, 'Đã xóa khung giờ');
                      Navigator.of(context).maybePop();
                    },
                    icon: const Icon(Icons.delete_outline, color: AppColors.error, size: 16),
                    label: const LocalizedText('Xóa khung giờ này', style: TextStyle(color: AppColors.error, fontSize: 12)),
                  ),
                ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}
