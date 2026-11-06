import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_colors.dart';

/// Xiaomi / Android Alarm-Style 2-Column Wheel Time Dialog (Hour & Minute)
class AlarmWheelTimeDialog extends StatefulWidget {
  final TimeOfDay initialTime;
  final String title;
  final String subtitle;

  const AlarmWheelTimeDialog({
    super.key,
    required this.initialTime,
    this.title = 'Chọn giờ',
    this.subtitle = 'Khung giờ hoạt động ca chạy',
  });

  static Future<TimeOfDay?> show(
    BuildContext context, {
    required TimeOfDay initialTime,
    String title = 'Chọn khung giờ',
    String subtitle = 'Khung giờ hoạt động ca chạy',
  }) {
    return showDialog<TimeOfDay>(
      context: context,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(alpha: 0.55),
      builder: (_) => AlarmWheelTimeDialog(
        initialTime: initialTime,
        title: title,
        subtitle: subtitle,
      ),
    );
  }

  @override
  State<AlarmWheelTimeDialog> createState() => _AlarmWheelTimeDialogState();
}

class _AlarmWheelTimeDialogState extends State<AlarmWheelTimeDialog> {
  late int _selectedHour;
  late int _selectedMinute;

  late FixedExtentScrollController _hourController;
  late FixedExtentScrollController _minuteController;

  static const double _itemExtent = 48.0;

  @override
  void initState() {
    super.initState();
    _selectedHour = widget.initialTime.hour;
    _selectedMinute = widget.initialTime.minute;

    _hourController = FixedExtentScrollController(initialItem: _selectedHour);
    _minuteController = FixedExtentScrollController(initialItem: _selectedMinute);
  }

  @override
  void dispose() {
    _hourController.dispose();
    _minuteController.dispose();
    super.dispose();
  }

  void _onHourChanged(int index) {
    HapticFeedback.selectionClick();
    setState(() => _selectedHour = index);
  }

  void _onMinuteChanged(int index) {
    HapticFeedback.selectionClick();
    setState(() => _selectedMinute = index);
  }

  Widget _buildWheelColumn({
    required FixedExtentScrollController controller,
    required int itemCount,
    required String Function(int index) labelBuilder,
    required ValueChanged<int> onSelectedItemChanged,
    required int selectedIndex,
    double width = 80,
  }) {
    return SizedBox(
      width: width,
      height: _itemExtent * 3,
      child: ListWheelScrollView.useDelegate(
        controller: controller,
        itemExtent: _itemExtent,
        perspective: 0.003,
        diameterRatio: 1.5,
        physics: const FixedExtentScrollPhysics(),
        useMagnifier: true,
        magnification: 1.15,
        overAndUnderCenterOpacity: 0.35,
        onSelectedItemChanged: onSelectedItemChanged,
        childDelegate: ListWheelChildBuilderDelegate(
          builder: (context, index) {
            if (index < 0 || index >= itemCount) return null;
            final isSelected = index == selectedIndex;
            return Center(
              child: AnimatedDefaultTextStyle(
                duration: const Duration(milliseconds: 150),
                style: TextStyle(
                  fontSize: isSelected ? 30 : 20,
                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                  color: isSelected ? AppColors.onSurface : AppColors.outline,
                  letterSpacing: -0.4,
                ),
                child: Text(labelBuilder(index)),
              ),
            );
          },
          childCount: itemCount,
        ),
      ),
    );
  }

  String _formatTimePeriod(int hour) {
    if (hour >= 4 && hour < 11) return 'Buổi sáng • Bình minh biển';
    if (hour >= 11 && hour < 14) return 'Buổi trưa • Nắng ấm';
    if (hour >= 14 && hour < 18) return 'Buổi chiều • Hoàng hôn';
    return 'Buổi tối • Ca đêm';
  }

  @override
  Widget build(BuildContext context) {
    final hourStr = _selectedHour.toString().padLeft(2, '0');
    final minStr = _selectedMinute.toString().padLeft(2, '0');

    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 28),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: 28,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withValues(alpha: 0.12),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.schedule, size: 20, color: AppColors.secondary),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.subtitle,
                        style: const TextStyle(
                          fontSize: 11.5,
                          color: AppColors.tertiary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Divider(height: 1, color: AppColors.borderSubtle.withValues(alpha: 0.7)),
            const SizedBox(height: 12),

            // Time Preview & Period
            Container(
              padding: const EdgeInsets.symmetric(vertical: 8),
              alignment: Alignment.center,
              child: Column(
                children: [
                  Text(
                    '$hourStr : $minStr',
                    style: const TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.w800,
                      color: AppColors.secondary,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _formatTimePeriod(_selectedHour),
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: AppColors.tertiary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),

            // 3D Wheel Pickers
            Stack(
              alignment: Alignment.center,
              children: [
                // Selection Highlight Bar
                Container(
                  height: _itemExtent,
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: AppColors.secondary.withValues(alpha: 0.25),
                      width: 1.2,
                    ),
                  ),
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _buildWheelColumn(
                      controller: _hourController,
                      itemCount: 24,
                      selectedIndex: _selectedHour,
                      labelBuilder: (idx) => idx.toString().padLeft(2, '0'),
                      onSelectedItemChanged: _onHourChanged,
                      width: 80,
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 14),
                      child: Text(
                        ':',
                        style: TextStyle(
                          fontSize: 26,
                          fontWeight: FontWeight.w800,
                          color: AppColors.onSurface,
                        ),
                      ),
                    ),
                    _buildWheelColumn(
                      controller: _minuteController,
                      itemCount: 60,
                      selectedIndex: _selectedMinute,
                      labelBuilder: (idx) => idx.toString().padLeft(2, '0'),
                      onSelectedItemChanged: _onMinuteChanged,
                      width: 80,
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Action Buttons
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context),
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      alignment: Alignment.center,
                      child: const Text(
                        'Hủy',
                        style: TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w600,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(
                      context,
                      TimeOfDay(hour: _selectedHour, minute: _selectedMinute),
                    ),
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.secondary.withValues(alpha: 0.28),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: const Text(
                        'Xong',
                        style: TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Xiaomi Alarm-Style 3D Wheel Date Dialog (Day / Month / Year)
class AlarmWheelDateDialog extends StatefulWidget {
  final DateTime initialDate;
  final DateTime? minimumDate;
  final DateTime? maximumDate;
  final String title;

  const AlarmWheelDateDialog({
    super.key,
    required this.initialDate,
    this.minimumDate,
    this.maximumDate,
    this.title = 'Chọn ngày áp dụng',
  });

  static Future<DateTime?> show(
    BuildContext context, {
    required DateTime initialDate,
    DateTime? minimumDate,
    DateTime? maximumDate,
    String title = 'Chọn ngày áp dụng',
  }) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final min = minimumDate ?? today;
    final max = maximumDate ?? today.add(const Duration(days: 180));

    final clamped = initialDate.isBefore(min)
        ? min
        : (initialDate.isAfter(max) ? max : initialDate);

    return showDialog<DateTime>(
      context: context,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(alpha: 0.55),
      builder: (_) => AlarmWheelDateDialog(
        initialDate: clamped,
        minimumDate: min,
        maximumDate: max,
        title: title,
      ),
    );
  }

  @override
  State<AlarmWheelDateDialog> createState() => _AlarmWheelDateDialogState();
}

class _AlarmWheelDateDialogState extends State<AlarmWheelDateDialog> {
  late int _selectedYear;
  late int _selectedMonth;
  late int _selectedDay;

  late List<int> _years;
  late FixedExtentScrollController _dayController;
  late FixedExtentScrollController _monthController;
  late FixedExtentScrollController _yearController;

  static const double _itemExtent = 48.0;

  @override
  void initState() {
    super.initState();
    _selectedYear = widget.initialDate.year;
    _selectedMonth = widget.initialDate.month;
    _selectedDay = widget.initialDate.day;

    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final min = widget.minimumDate ?? today;
    final max = widget.maximumDate ?? today.add(const Duration(days: 180));

    _years = [for (int y = min.year; y <= max.year; y++) y];

    final initialYearIdx = _years.indexOf(_selectedYear).clamp(0, _years.length - 1);
    final initialMonthIdx = (_selectedMonth - 1).clamp(0, 11);
    final initialDayIdx = (_selectedDay - 1).clamp(0, 30);

    _yearController = FixedExtentScrollController(initialItem: initialYearIdx);
    _monthController = FixedExtentScrollController(initialItem: initialMonthIdx);
    _dayController = FixedExtentScrollController(initialItem: initialDayIdx);
  }

  @override
  void dispose() {
    _yearController.dispose();
    _monthController.dispose();
    _dayController.dispose();
    super.dispose();
  }

  int _getDaysInMonth(int year, int month) {
    return DateTime(year, month + 1, 0).day;
  }

  DateTime get _currentDateTime {
    final maxDay = _getDaysInMonth(_selectedYear, _selectedMonth);
    final safeDay = _selectedDay.clamp(1, maxDay);
    return DateTime(_selectedYear, _selectedMonth, safeDay);
  }

  String _formatWeekday(DateTime dt) {
    const days = [
      'Thứ Hai',
      'Thứ Ba',
      'Thứ Tư',
      'Thứ Năm',
      'Thứ Sáu',
      'Thứ Bảy',
      'Chủ Nhật',
    ];
    return days[dt.weekday - 1];
  }

  String _formatRelativeDays(DateTime dt) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final target = DateTime(dt.year, dt.month, dt.day);
    final diff = target.difference(today).inDays;

    if (diff == 0) return 'Hôm nay • Trong ngày';
    if (diff == 1) return 'Ngày mai • Sau 1 ngày';
    if (diff < 0) return 'Đã qua ${-diff} ngày trước';
    return 'Sau $diff ngày nữa';
  }

  void _onDayChanged(int index) {
    HapticFeedback.selectionClick();
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    _selectedDay = (index + 1).clamp(1, maxDays);
    setState(() {});
  }

  void _onMonthChanged(int index) {
    HapticFeedback.selectionClick();
    _selectedMonth = index + 1;
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    if (_selectedDay > maxDays) {
      _selectedDay = maxDays;
      _dayController.jumpToItem(maxDays - 1);
    }
    setState(() {});
  }

  void _onYearChanged(int index) {
    HapticFeedback.selectionClick();
    _selectedYear = _years[index];
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    if (_selectedDay > maxDays) {
      _selectedDay = maxDays;
      _dayController.jumpToItem(maxDays - 1);
    }
    setState(() {});
  }

  Widget _buildWheelColumn({
    required FixedExtentScrollController controller,
    required int itemCount,
    required String Function(int index) labelBuilder,
    required ValueChanged<int> onSelectedItemChanged,
    required int selectedIndex,
    double width = 80,
  }) {
    return SizedBox(
      width: width,
      height: _itemExtent * 3,
      child: ListWheelScrollView.useDelegate(
        controller: controller,
        itemExtent: _itemExtent,
        perspective: 0.003,
        diameterRatio: 1.5,
        physics: const FixedExtentScrollPhysics(),
        useMagnifier: true,
        magnification: 1.15,
        overAndUnderCenterOpacity: 0.35,
        onSelectedItemChanged: onSelectedItemChanged,
        childDelegate: ListWheelChildBuilderDelegate(
          builder: (context, index) {
            if (index < 0 || index >= itemCount) return null;
            final isSelected = index == selectedIndex;
            return Center(
              child: AnimatedDefaultTextStyle(
                duration: const Duration(milliseconds: 150),
                style: TextStyle(
                  fontSize: isSelected ? 26 : 18,
                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                  color: isSelected ? AppColors.onSurface : AppColors.outline,
                  letterSpacing: -0.4,
                ),
                child: Text(labelBuilder(index)),
              ),
            );
          },
          childCount: itemCount,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final currentDt = _currentDateTime;
    final daysInCurrentMonth = _getDaysInMonth(_selectedYear, _selectedMonth);

    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: 28,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  '${_formatWeekday(currentDt)}, ${currentDt.day.toString().padLeft(2, '0')}/${currentDt.month.toString().padLeft(2, '0')}/${currentDt.year}',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                    letterSpacing: -0.2,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _formatRelativeDays(currentDt),
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w500,
                    color: AppColors.secondary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Divider(height: 1, color: AppColors.borderSubtle.withValues(alpha: 0.7)),
            const SizedBox(height: 12),
            Stack(
              alignment: Alignment.center,
              children: [
                Container(
                  height: _itemExtent,
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: AppColors.secondary.withValues(alpha: 0.22),
                      width: 1.0,
                    ),
                  ),
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildWheelColumn(
                      controller: _dayController,
                      itemCount: daysInCurrentMonth,
                      selectedIndex: (_selectedDay - 1).clamp(0, daysInCurrentMonth - 1),
                      labelBuilder: (idx) => (idx + 1).toString().padLeft(2, '0'),
                      onSelectedItemChanged: _onDayChanged,
                      width: 68,
                    ),
                    const Text(
                      '/',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w400,
                        color: AppColors.borderSubtle,
                      ),
                    ),
                    _buildWheelColumn(
                      controller: _monthController,
                      itemCount: 12,
                      selectedIndex: _selectedMonth - 1,
                      labelBuilder: (idx) => 'Th ${(idx + 1).toString().padLeft(2, '0')}',
                      onSelectedItemChanged: _onMonthChanged,
                      width: 86,
                    ),
                    const Text(
                      '/',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w400,
                        color: AppColors.borderSubtle,
                      ),
                    ),
                    _buildWheelColumn(
                      controller: _yearController,
                      itemCount: _years.length,
                      selectedIndex: _years.indexOf(_selectedYear).clamp(0, _years.length - 1),
                      labelBuilder: (idx) => '${_years[idx]}',
                      onSelectedItemChanged: _onYearChanged,
                      width: 78,
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context),
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      alignment: Alignment.center,
                      child: const Text(
                        'Hủy',
                        style: TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w600,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context, currentDt),
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.secondary.withValues(alpha: 0.28),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: const Text(
                        'Xong',
                        style: TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

