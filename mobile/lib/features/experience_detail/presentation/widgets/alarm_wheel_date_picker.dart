import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';

/// Xiaomi / Android Alarm-style 3D Wheel Date Picker Modal
class AlarmWheelDatePicker extends StatefulWidget {
  final DateTime initialDate;
  final DateTime minimumDate;
  final DateTime maximumDate;

  const AlarmWheelDatePicker({
    super.key,
    required this.initialDate,
    required this.minimumDate,
    required this.maximumDate,
  });

  static Future<DateTime?> show(
    BuildContext context, {
    required DateTime initialDate,
    DateTime? minimumDate,
    DateTime? maximumDate,
  }) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final min = minimumDate ?? today;
    final max = maximumDate ?? today.add(const Duration(days: 365));

    final clampedInitial = initialDate.isBefore(min)
        ? min
        : (initialDate.isAfter(max) ? max : initialDate);

    return showModalBottomSheet<DateTime>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => AlarmWheelDatePicker(
        initialDate: clampedInitial,
        minimumDate: min,
        maximumDate: max,
      ),
    );
  }

  @override
  State<AlarmWheelDatePicker> createState() => _AlarmWheelDatePickerState();
}

class _AlarmWheelDatePickerState extends State<AlarmWheelDatePicker> {
  late int _selectedYear;
  late int _selectedMonth;
  late int _selectedDay;

  late List<int> _years;
  late FixedExtentScrollController _dayController;
  late FixedExtentScrollController _monthController;
  late FixedExtentScrollController _yearController;

  static const double _itemExtent = 52.0;

  @override
  void initState() {
    super.initState();
    _selectedYear = widget.initialDate.year;
    _selectedMonth = widget.initialDate.month;
    _selectedDay = widget.initialDate.day;

    final startYear = widget.minimumDate.year;
    final endYear = widget.maximumDate.year;
    _years = [for (int y = startYear; y <= endYear; y++) y];

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
    DateTime dt = DateTime(_selectedYear, _selectedMonth, safeDay);
    if (dt.isBefore(widget.minimumDate)) return widget.minimumDate;
    if (dt.isAfter(widget.maximumDate)) return widget.maximumDate;
    return dt;
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

    if (diff < 0) return 'Đã qua ${-diff} ngày';
    if (diff == 0) return 'Hôm nay • Khởi hành trong ngày';
    if (diff == 1) return 'Ngày mai • Khởi hành sau 1 ngày';
    return 'Khởi hành sau $diff ngày';
  }

  void _onDayChanged(int index) {
    HapticFeedback.selectionClick();
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    final newDay = (index + 1).clamp(1, maxDays);
    setState(() => _selectedDay = newDay);
  }

  void _onMonthChanged(int index) {
    HapticFeedback.selectionClick();
    final newMonth = index + 1;
    final maxDays = _getDaysInMonth(_selectedYear, newMonth);
    setState(() {
      _selectedMonth = newMonth;
      if (_selectedDay > maxDays) {
        _selectedDay = maxDays;
        _dayController.jumpToItem(maxDays - 1);
      }
    });
  }

  void _onYearChanged(int index) {
    HapticFeedback.selectionClick();
    final newYear = _years[index];
    final maxDays = _getDaysInMonth(newYear, _selectedMonth);
    setState(() {
      _selectedYear = newYear;
      if (_selectedDay > maxDays) {
        _selectedDay = maxDays;
        _dayController.jumpToItem(maxDays - 1);
      }
    });
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
      height: _itemExtent * 3, // Exactly 3 visible items like Xiaomi Alarm
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
                  letterSpacing: -0.5,
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

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 12,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Top Drag Handle
            Center(
              child: Container(
                width: 38,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: AppColors.borderSubtle,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),

            // ALARM-STYLE HEADER CARD PREVIEW (matching Image 2)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: AppColors.borderSubtle.withValues(alpha: 0.7),
                  width: 0.8,
                ),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Large Bold Date Display (like '05:40')
                        Text(
                          '${currentDt.day.toString().padLeft(2, '0')}/${currentDt.month.toString().padLeft(2, '0')}/${currentDt.year}',
                          style: const TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            color: AppColors.onSurface,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 3),
                        // Subtitle: Weekday & Relative Day (like 'Mỗi ngày | Báo thức sau...')
                        Row(
                          children: [
                            Text(
                              _formatWeekday(currentDt),
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: AppColors.secondary,
                              ),
                            ),
                            const Text(
                              '  •  ',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.outline,
                              ),
                            ),
                            Expanded(
                              child: Text(
                                _formatRelativeDays(currentDt),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w500,
                                  color: AppColors.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.alarm,
                      size: 22,
                      color: AppColors.secondary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),

            // 3D ROLLING WHEEL PICKER (matching Image 2)
            Stack(
              alignment: Alignment.center,
              children: [
                // Center Selection Highlight Band
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

                // 3 Columns: Ngày (Day), Tháng (Month), Năm (Year)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // Column 1: Ngày
                    _buildWheelColumn(
                      controller: _dayController,
                      itemCount: daysInCurrentMonth,
                      selectedIndex: (_selectedDay - 1).clamp(0, daysInCurrentMonth - 1),
                      labelBuilder: (idx) => (idx + 1).toString().padLeft(2, '0'),
                      onSelectedItemChanged: _onDayChanged,
                      width: 76,
                    ),

                    const Text(
                      '/',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w600,
                        color: AppColors.borderSubtle,
                      ),
                    ),

                    // Column 2: Tháng
                    _buildWheelColumn(
                      controller: _monthController,
                      itemCount: 12,
                      selectedIndex: _selectedMonth - 1,
                      labelBuilder: (idx) => 'Th ${(idx + 1).toString().padLeft(2, '0')}',
                      onSelectedItemChanged: _onMonthChanged,
                      width: 96,
                    ),

                    const Text(
                      '/',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w600,
                        color: AppColors.borderSubtle,
                      ),
                    ),

                    // Column 3: Năm
                    _buildWheelColumn(
                      controller: _yearController,
                      itemCount: _years.length,
                      selectedIndex: _years.indexOf(_selectedYear).clamp(0, _years.length - 1),
                      labelBuilder: (idx) => '${_years[idx]}',
                      onSelectedItemChanged: _onYearChanged,
                      width: 86,
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // ALARM-STYLE DUAL ACTION BUTTONS (matching Image 2: [ Cài đặt khác / Hủy ] & [ Xong ])
            Row(
              children: [
                // Button [ Hủy ]
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      alignment: Alignment.center,
                      child: const LocalizedText(
                        'Hủy',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 14),

                // Button [ Xong ]
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context, currentDt),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.secondary.withValues(alpha: 0.28),
                            blurRadius: 10,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: const LocalizedText(
                        'Xong',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
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


/// Xiaomi / Android Alarm-style 2-Column Wheel Time Picker (Hour & Minute)
class AlarmWheelTimePicker extends StatefulWidget {
  final TimeOfDay initialTime;

  const AlarmWheelTimePicker({
    super.key,
    required this.initialTime,
  });

  static Future<TimeOfDay?> show(
    BuildContext context, {
    required TimeOfDay initialTime,
  }) {
    return showModalBottomSheet<TimeOfDay>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => AlarmWheelTimePicker(initialTime: initialTime),
    );
  }

  @override
  State<AlarmWheelTimePicker> createState() => _AlarmWheelTimePickerState();
}

class _AlarmWheelTimePickerState extends State<AlarmWheelTimePicker> {
  late int _selectedHour;
  late int _selectedMinute;

  late FixedExtentScrollController _hourController;
  late FixedExtentScrollController _minuteController;

  static const double _itemExtent = 52.0;

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
    double width = 90,
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
                  fontSize: isSelected ? 36 : 22,
                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                  color: isSelected ? AppColors.onSurface : AppColors.outline,
                  letterSpacing: -0.5,
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
    final timeStr = ':';

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 12,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 38,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: AppColors.borderSubtle,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: AppColors.borderSubtle.withValues(alpha: 0.7),
                  width: 0.8,
                ),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          timeStr,
                          style: const TextStyle(
                            fontSize: 32,
                            fontWeight: FontWeight.w800,
                            color: AppColors.onSurface,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 3),
                        const Text(
                          'Khung giờ khởi hành • Đặt lịch theo báo thức',
                          style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: AppColors.secondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.alarm,
                      size: 24,
                      color: AppColors.secondary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            Stack(
              alignment: Alignment.center,
              children: [
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
                      width: 90,
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        ':',
                        style: TextStyle(
                          fontSize: 32,
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
                      width: 90,
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(context),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      alignment: Alignment.center,
                      child: const LocalizedText(
                        'Hủy',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: InkWell(
                    onTap: () => Navigator.pop(
                      context,
                      TimeOfDay(hour: _selectedHour, minute: _selectedMinute),
                    ),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.secondary.withValues(alpha: 0.28),
                            blurRadius: 10,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: const LocalizedText(
                        'Xong',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
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


/// Xiaomi Alarm-Style INLINE 3D Date Roller Wheel
class InlineAlarmWheelDatePicker extends StatefulWidget {
  final DateTime initialDate;
  final DateTime? minimumDate;
  final DateTime? maximumDate;
  final ValueChanged<DateTime> onDateChanged;
  final VoidCallback onDone;

  const InlineAlarmWheelDatePicker({
    super.key,
    required this.initialDate,
    this.minimumDate,
    this.maximumDate,
    required this.onDateChanged,
    required this.onDone,
  });

  @override
  State<InlineAlarmWheelDatePicker> createState() => _InlineAlarmWheelDatePickerState();
}

class _InlineAlarmWheelDatePickerState extends State<InlineAlarmWheelDatePicker> {
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
    final max = widget.maximumDate ?? today.add(const Duration(days: 365));

    final startYear = [min.year, _selectedYear].reduce((a, b) => a < b ? a : b);
    final endYear = [max.year, _selectedYear].reduce((a, b) => a > b ? a : b);
    _years = [for (int y = startYear; y <= endYear; y++) y];

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

  void _notifyChange() {
    HapticFeedback.selectionClick();
    widget.onDateChanged(_currentDateTime);
  }

  void _onDayChanged(int index) {
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    _selectedDay = (index + 1).clamp(1, maxDays);
    setState(() {});
    _notifyChange();
  }

  void _onMonthChanged(int index) {
    _selectedMonth = index + 1;
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    if (_selectedDay > maxDays) {
      _selectedDay = maxDays;
      _dayController.jumpToItem(maxDays - 1);
    }
    setState(() {});
    _notifyChange();
  }

  void _onYearChanged(int index) {
    _selectedYear = _years[index];
    final maxDays = _getDaysInMonth(_selectedYear, _selectedMonth);
    if (_selectedDay > maxDays) {
      _selectedDay = maxDays;
      _dayController.jumpToItem(maxDays - 1);
    }
    setState(() {});
    _notifyChange();
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
                  fontSize: isSelected ? 24 : 17,
                  fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                  color: isSelected ? AppColors.onSurface : AppColors.outline.withValues(alpha: 0.6),
                  letterSpacing: -0.2,
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

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: AppColors.secondary.withValues(alpha: 0.35),
          width: 1.2,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.alarm, size: 16, color: AppColors.secondary),
                  const SizedBox(width: 6),
                  Text(
                    '${_formatWeekday(currentDt)}, ${currentDt.day.toString().padLeft(2, '0')}/${currentDt.month.toString().padLeft(2, '0')}/${currentDt.year}',
                    style: const TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w700,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              InkWell(
                onTap: widget.onDone,
                borderRadius: BorderRadius.circular(14),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppColors.secondary,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: const Text(
                    'Xong',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
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
                    width: 70,
                  ),
                  const Text(
                    '/',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                      color: AppColors.borderSubtle,
                    ),
                  ),
                  _buildWheelColumn(
                    controller: _monthController,
                    itemCount: 12,
                    selectedIndex: _selectedMonth - 1,
                    labelBuilder: (idx) => 'Th ${(idx + 1).toString().padLeft(2, '0')}',
                    onSelectedItemChanged: _onMonthChanged,
                    width: 90,
                  ),
                  const Text(
                    '/',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                      color: AppColors.borderSubtle,
                    ),
                  ),
                  _buildWheelColumn(
                    controller: _yearController,
                    itemCount: _years.length,
                    selectedIndex: _years.indexOf(_selectedYear).clamp(0, _years.length - 1),
                    labelBuilder: (idx) => '${_years[idx]}',
                    onSelectedItemChanged: _onYearChanged,
                    width: 80,
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}


/// Xiaomi Alarm-Style CENTER POPUP DIALOG (Giữa màn hình như thông báo)
class AlarmWheelDateDialog extends StatefulWidget {
  final DateTime initialDate;
  final DateTime? minimumDate;
  final DateTime? maximumDate;

  const AlarmWheelDateDialog({
    super.key,
    required this.initialDate,
    this.minimumDate,
    this.maximumDate,
  });

  static Future<DateTime?> show(
    BuildContext context, {
    required DateTime initialDate,
    DateTime? minimumDate,
    DateTime? maximumDate,
  }) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final min = minimumDate ?? today;
    final max = maximumDate ?? today.add(const Duration(days: 90));

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
    final max = widget.maximumDate ?? today.add(const Duration(days: 90));

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

    if (diff == 0) return 'Hôm nay • Khởi hành trong ngày';
    if (diff == 1) return 'Ngày mai • Khởi hành sau 1 ngày';
    return 'Khởi hành sau $diff ngày';
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
                  fontSize: isSelected ? 28 : 18,
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
