import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/booking_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../cart/data/cart_manager.dart';

/// Bottom sheet chọn ngày, khung giờ & số lượng người trải nghiệm biển Danasea
class ServiceAvailabilitySheet extends StatefulWidget {
  const ServiceAvailabilitySheet({
    super.key,
    required this.service,
    this.isCartEdit = false,
    this.initialQuantity = 1,
    this.initialDate,
    this.initialSlotId,
    this.onUpdateCart,
  });

  final ServiceModel service;
  final bool isCartEdit;
  final int initialQuantity;
  final DateTime? initialDate;
  final String? initialSlotId;
  final void Function(CatalogSlot slot, int quantity)? onUpdateCart;

  @override
  State<ServiceAvailabilitySheet> createState() =>
      _ServiceAvailabilitySheetState();
}

class _ServiceAvailabilitySheetState extends State<ServiceAvailabilitySheet> {
  final _bookingRepository = BookingRepository();
  final _orderRepository = OrderRepository();

  String? _optionId;
  late List<CatalogSlot> _slots;
  CatalogSlot? _selectedSlot;
  DateTime _selectedDate = DateTime.now();

  int _quantity = 1;
  bool _allowSplit = false;

  bool _refreshing = false;
  bool _holding = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _optionId = widget.service.options.firstOrNull?.id;
    _slots = List.of(widget.service.catalogSlots);
    _quantity = widget.initialQuantity;

    if (widget.initialDate != null) {
      _selectedDate = DateTime(
        widget.initialDate!.year,
        widget.initialDate!.month,
        widget.initialDate!.day,
      );
    } else {
      final now = DateTime.now();
      _selectedDate = DateTime(now.year, now.month, now.day);
    }

    if (_slots.isEmpty) {
      _refresh();
    } else {
      _autoSelectFirstAvailableSlot();
    }
  }

  void _autoSelectFirstAvailableSlot() {
    final daySlots = _getSlotsForDate(_selectedDate);
    if (widget.initialSlotId != null) {
      final matched = daySlots
          .where((s) => s.id == widget.initialSlotId)
          .firstOrNull;
      if (matched != null) {
        _selectedSlot = matched;
        return;
      }
    }

    final bookableSlots = daySlots.where(
      (s) => s.bookable && (s.availableCapacity ?? 0) > 0,
    );
    if (bookableSlots.isNotEmpty) {
      _selectedSlot = bookableSlots.first;
    } else {
      final tomorrow = DateTime.now().add(const Duration(days: 1));
      final tomorrowClean = DateTime(
        tomorrow.year,
        tomorrow.month,
        tomorrow.day,
      );
      final tomorrowSlots = _getSlotsForDate(tomorrowClean).where(
        (s) => s.bookable && (s.availableCapacity ?? 0) > 0,
      );
      if (tomorrowSlots.isNotEmpty) {
        _selectedDate = tomorrowClean;
        _selectedSlot = tomorrowSlots.first;
      }
    }
  }

  List<CatalogSlot> _getFilteredSlots() {
    return _slots
        .where((slot) => _optionId == null || slot.optionId == _optionId)
        .toList();
  }

  List<CatalogSlot> _getSlotsForDate(DateTime date) {
    final dateStr = DateFormat('yyyy-MM-dd').format(date);
    return _getFilteredSlots().where((s) => s.date == dateStr).toList()
      ..sort((a, b) => a.startTime.compareTo(b.startTime));
  }

  Future<void> _refresh() async {
    if (_refreshing) return;
    final optionId = _optionId;
    setState(() {
      _refreshing = true;
      _errorMessage = null;
      _slots.removeWhere(
        (slot) => optionId == null || slot.optionId == optionId,
      );
    });
    try {
      final slots = await CatalogRepository().slots(
        widget.service.id,
        optionId: optionId,
      );
      if (mounted) {
        setState(() {
          _slots.addAll(slots);
          _autoSelectFirstAvailableSlot();
        });
      }
    } catch (error) {
      if (mounted) setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final firstDate = DateTime(now.year, now.month, now.day);
    final lastDate = firstDate.add(const Duration(days: 30));

    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate.isBefore(firstDate) ? firstDate : _selectedDate,
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
            datePickerTheme: const DatePickerThemeData(
              headerBackgroundColor: AppColors.secondary,
              headerForegroundColor: AppColors.onSecondary,
            ),
          ),
          child: child!,
        );
      },
    );

    if (picked != null && picked != _selectedDate) {
      setState(() {
        _selectedDate = picked;
        _selectedSlot = null;
        final available = _getSlotsForDate(picked).where(
          (s) => s.bookable && (s.availableCapacity ?? 0) > 0,
        );
        if (available.isNotEmpty) {
          _selectedSlot = available.first;
        }
      });
    }
  }

  Future<void> _submitBooking() async {
    final slot = _selectedSlot;
    if (slot == null || _holding) return;

    final optId = slot.optionId ?? _optionId ?? widget.service.options.firstOrNull?.id;
    if (optId == null) {
      setState(() => _errorMessage = 'Vui lòng chọn gói dịch vụ.');
      return;
    }

    setState(() {
      _holding = true;
      _errorMessage = null;
    });

    try {
      // 1. Giữ chỗ (Hold Slot)
      final hold = await _bookingRepository.hold(
        slotId: slot.id,
        optionId: optId,
        quantity: _quantity,
        participantsCount: _quantity,
        allowSplit: _allowSplit,
      );

      // 2. Tạo đơn hàng (Create Master Order)
      final idempotencyKey = OrderRepository.generateIdempotencyKey('ord');
      final order = await _orderRepository.createOrder(
        bookingId: hold.id,
        idempotencyKey: idempotencyKey,
      );

      if (!mounted) return;

      // 3. Đóng sheet và chuyển thẳng sang màn hình Thanh toán (Checkout)
      Navigator.of(context).pop();
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => CheckoutScreen(masterOrder: order),
        ),
      );
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _errorMessage = _friendlyErrorMessage(e.message));
    } catch (_) {
      if (mounted) {
        setState(
          () => _errorMessage = 'Không thể giữ chỗ lúc này. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _holding = false);
    }
  }

  String _friendlyErrorMessage(String raw) {
    if (raw.contains('COMMISSION_POLICY_MISSING')) {
      return 'Chính sách dịch vụ đang được cập nhật. Vui lòng thử lại.';
    }
    if (raw.contains('INSUFFICIENT_CAPACITY') || raw.contains('SLOT_FULL')) {
      return 'Khung giờ này không đủ chỗ cho số lượng bạn chọn.';
    }
    if (raw.contains('HOLD_EXPIRED') || raw.contains('EXPIRED')) {
      return 'Phiên giữ chỗ đã hết hạn, vui lòng thử lại.';
    }
    return raw;
  }

  void _addToCart() {
    final slot = _selectedSlot;
    if (slot == null || _holding) return;

    CartManager.instance.addItem(
      service: widget.service,
      slot: slot,
      quantity: _quantity,
    );

    _showAddToCartToast();
  }

  void _showAddToCartToast() {
    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (_) => _AddToCartToastWidget(
        onDismissed: () {
          if (entry.mounted) {
            entry.remove();
          }
        },
      ),
    );
    overlay.insert(entry);
  }

  String _formatPrice(num price) {
    return '${NumberFormat('#,###', 'vi').format(price.toInt())} ₫';
  }

  String _formatFullDateDisplay(DateTime date) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final target = DateTime(date.year, date.month, date.day);
    final diff = target.difference(today).inDays;

    String prefix;
    if (diff == 0) {
      prefix = 'Hôm nay';
    } else if (diff == 1) {
      prefix = 'Ngày mai';
    } else {
      prefix = switch (date.weekday) {
        DateTime.monday => 'Thứ Hai',
        DateTime.tuesday => 'Thứ Ba',
        DateTime.wednesday => 'Thứ Tư',
        DateTime.thursday => 'Thứ Năm',
        DateTime.friday => 'Thứ Sáu',
        DateTime.saturday => 'Thứ Bảy',
        DateTime.sunday => 'Chủ Nhật',
        _ => '',
      };
    }
    return '$prefix, ${DateFormat('dd/MM/yyyy').format(date)}';
  }

  int _getHour(String timeStr) {
    final parts = timeStr.split(':');
    return int.tryParse(parts.first) ?? 0;
  }

  @override
  Widget build(BuildContext context) {
    final daySlots = _getSlotsForDate(_selectedDate);

    // Phân loại ca
    final morningSlots =
        daySlots.where((s) => _getHour(s.startTime) < 12).toList();
    final afternoonSlots = daySlots.where((s) {
      final h = _getHour(s.startTime);
      return h >= 12 && h < 17;
    }).toList();
    final eveningSlots =
        daySlots.where((s) => _getHour(s.startTime) >= 17).toList();

    return Container(
      height: MediaQuery.sizeOf(context).height * 0.85,
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(AppShapes.radiusMdValue),
        ),
      ),
      child: Column(
        children: [
          // 1. THANH TIÊU ĐỀ
          _buildHeader(),

          const Divider(height: 1, color: AppColors.surfaceVariant),

          // 2. NỘI DUNG CUỘN CHÍNH
          Expanded(
            child: _refreshing
                ? const Center(
                    child: CircularProgressIndicator(
                      color: AppColors.secondary,
                    ),
                  )
                : SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (_errorMessage != null) _buildErrorBanner(),

                        // A. GÓI DỊCH VỤ (NẾU CÓ TRÊN 1 GÓI)
                        if (widget.service.options.length > 1) ...[
                          _buildOptionSelector(),
                          const SizedBox(height: 18),
                        ],

                        // B. Ô CHỌN NGÀY THÁNG NĂM KÈM MŨI TÊN (MỞ LỊCH DATEPICKER)
                        LocalizedText(
                          'Ngày trải nghiệm',
                          style: AppTypography.labelLg(
                            color: AppColors.navyAnchor,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 8),
                        _buildDatePickerField(),

                        const SizedBox(height: 20),

                        // C. SỐ LƯỢNG NGƯỜI & CHIA NHÓM (TÍCH HỢP TRỰC TIẾP TẠI ĐÂY)
                        _buildQuantitySection(),

                        const SizedBox(height: 20),

                        // D. DANH SÁCH KHUNG GIỜ THEO CA
                        _buildShowtimeSection(
                          daySlots: daySlots,
                          morningSlots: morningSlots,
                          afternoonSlots: afternoonSlots,
                          eveningSlots: eveningSlots,
                        ),
                      ],
                    ),
                  ),
          ),

          // 3. THANH CHỐT GIỮ CHỖ & THANH TOÁN
          _buildBottomBar(),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 10, 12, 12),
      child: Column(
        children: [
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: AppColors.surfaceVariant,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LocalizedText(
                      widget.isCartEdit
                          ? 'Đổi lịch & số người'
                          : 'Đặt chỗ trải nghiệm',
                      style: AppTypography.headlineSm(
                        color: AppColors.navyAnchor,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      widget.service.name,
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
                icon: Container(
                  padding: const EdgeInsets.all(5),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerHigh.withValues(
                      alpha: 0.6,
                    ),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.close,
                    size: 18,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildErrorBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.primaryContainer.withValues(alpha: 0.2),
        borderRadius: AppShapes.radiusSm,
        border: Border.all(color: AppColors.primary),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.primary, size: 20),
          const SizedBox(width: 8),
          Expanded(child: LocalizedText(_errorMessage!)),
          TextButton(
            onPressed: () => setState(() => _errorMessage = null),
            child: const LocalizedText('Đóng'),
          ),
        ],
      ),
    );
  }

  Widget _buildOptionSelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LocalizedText(
          'Gói dịch vụ',
          style: AppTypography.labelLg(
            color: AppColors.navyAnchor,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 42,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: widget.service.options.length,
            separatorBuilder: (_, _) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final opt = widget.service.options[index];
              final isSelected = opt.id == _optionId;
              return InkWell(
                onTap: _refreshing
                    ? null
                    : () {
                        setState(() {
                          _optionId = opt.id;
                          _selectedSlot = null;
                        });
                        _refresh();
                      },
                borderRadius: AppShapes.radiusFull,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? AppColors.secondary.withValues(alpha: 0.10)
                        : AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusFull,
                    border: Border.all(
                      color: isSelected
                          ? AppColors.secondary
                          : AppColors.surfaceVariant,
                      width: isSelected ? 1.8 : 1.0,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (isSelected) ...[
                        const Icon(
                          Icons.check_circle_rounded,
                          size: 15,
                          color: AppColors.secondary,
                        ),
                        const SizedBox(width: 6),
                      ],
                      Text(
                        opt.name,
                        style: AppTypography.labelMd(
                          color: isSelected
                              ? AppColors.secondary
                              : AppColors.onSurface,
                          fontWeight:
                              isSelected ? FontWeight.w700 : FontWeight.w500,
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

  /// Ô chọn ngày tháng năm kèm icon mũi tên xuống, bấm vào mở Lịch DatePicker
  Widget _buildDatePickerField() {
    final availableCount = _getSlotsForDate(_selectedDate)
        .where((s) => s.bookable && (s.availableCapacity ?? 0) > 0)
        .length;

    return InkWell(
      onTap: _pickDate,
      borderRadius: AppShapes.radiusDefault,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: AppShapes.radiusDefault,
          border: Border.all(
            color: AppColors.secondary.withValues(alpha: 0.35),
            width: 1.2,
          ),
          boxShadow: AppShapes.shadowLevel1,
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppColors.secondary.withValues(alpha: 0.10),
                borderRadius: AppShapes.radiusSm,
              ),
              child: const Icon(
                Icons.calendar_month_rounded,
                size: 22,
                color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _formatFullDateDisplay(_selectedDate),
                    style: AppTypography.labelLg(
                      color: AppColors.navyAnchor,
                      fontWeight: FontWeight.w700,
                    ).copyWith(fontSize: 15),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    availableCount > 0
                        ? '$availableCount khung giờ có sẵn'
                        : 'Đã hết ca trong ngày này',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: availableCount > 0
                          ? AppColors.secondary
                          : AppColors.outline,
                    ),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.all(5),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh.withValues(alpha: 0.5),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.keyboard_arrow_down_rounded,
                size: 20,
                color: AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Bộ chọn số lượng vé / số người tham gia và tùy chọn chia nhóm
  Widget _buildQuantitySection() {
    final maxCapacity = (_selectedSlot?.availableCapacity ?? 30).clamp(1, 50);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        border: Border.all(
          color: AppColors.surfaceVariant.withValues(alpha: 0.9),
          width: 1.0,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  LocalizedText(
                    'Số khách tham gia',
                    style: AppTypography.labelLg(
                      color: AppColors.navyAnchor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Tối đa $maxCapacity người/suất',
                    style: const TextStyle(
                      fontSize: 11.5,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              // Bộ đếm [-] [1] [+]
              Container(
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: AppShapes.radiusFull,
                  border: Border.all(color: AppColors.surfaceVariant),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.remove, size: 18),
                      color: _quantity > 1 ? AppColors.secondary : AppColors.outline,
                      onPressed: _quantity > 1
                          ? () => setState(() => _quantity--)
                          : null,
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      child: Text(
                        '$_quantity',
                        style: AppTypography.headlineSm(
                          color: AppColors.navyAnchor,
                        ).copyWith(fontWeight: FontWeight.w800, fontSize: 16),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.add, size: 18),
                      color: _quantity < maxCapacity ? AppColors.secondary : AppColors.outline,
                      onPressed: _quantity < maxCapacity
                          ? () => setState(() => _quantity++)
                          : null,
                    ),
                  ],
                ),
              ),
            ],
          ),

          // Tùy chọn chia nhóm (chỉ hiện khi đi nhóm từ 2 người trở lên)
          if (_quantity > 1) ...[
            const SizedBox(height: 12),
            const Divider(height: 1, color: AppColors.surfaceVariant),
            const SizedBox(height: 10),
            InkWell(
              onTap: () => setState(() => _allowSplit = !_allowSplit),
              borderRadius: AppShapes.radiusSm,
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    Checkbox(
                      value: _allowSplit,
                      activeColor: AppColors.secondary,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(4),
                      ),
                      onChanged: (val) => setState(() => _allowSplit = val ?? false),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            'Cho phép chia nhóm thuyền/cano',
                            style: AppTypography.labelMd(
                              color: AppColors.navyAnchor,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 2),
                          const Text(
                            'Đồng ý tách đoàn lên các thuyền/cano khác nhau nếu không đủ chỗ cùng 1 thuyền.',
                            style: TextStyle(
                              fontSize: 11,
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildShowtimeSection({
    required List<CatalogSlot> daySlots,
    required List<CatalogSlot> morningSlots,
    required List<CatalogSlot> afternoonSlots,
    required List<CatalogSlot> eveningSlots,
  }) {
    if (daySlots.isEmpty) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 36),
        child: Column(
          children: [
            Icon(
              Icons.event_busy,
              size: 44,
              color: AppColors.outline.withValues(alpha: 0.6),
            ),
            const SizedBox(height: 10),
            LocalizedText(
              'Không có khung giờ trong ngày này.',
              style: AppTypography.bodyMd(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 6),
            InkWell(
              onTap: _pickDate,
              borderRadius: AppShapes.radiusFull,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: AppColors.secondary.withValues(alpha: 0.1),
                  borderRadius: AppShapes.radiusFull,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.calendar_today_rounded,
                      size: 14,
                      color: AppColors.secondary,
                    ),
                    const SizedBox(width: 6),
                    LocalizedText(
                      'Bấm để chọn ngày khác',
                      style: AppTypography.labelSm(
                        color: AppColors.secondary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LocalizedText(
          'Khung giờ xuất phát',
          style: AppTypography.labelLg(
            color: AppColors.navyAnchor,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        if (morningSlots.isNotEmpty)
          _buildSessionGroup(
            title: 'Buổi sáng',
            icon: Icons.wb_sunny_outlined,
            slots: morningSlots,
          ),
        if (afternoonSlots.isNotEmpty) ...[
          const SizedBox(height: 18),
          _buildSessionGroup(
            title: 'Buổi chiều',
            icon: Icons.wb_twilight_rounded,
            slots: afternoonSlots,
          ),
        ],
        if (eveningSlots.isNotEmpty) ...[
          const SizedBox(height: 18),
          _buildSessionGroup(
            title: 'Hoàng hôn & Tối',
            icon: Icons.nights_stay_outlined,
            slots: eveningSlots,
          ),
        ],
      ],
    );
  }

  Widget _buildSessionGroup({
    required String title,
    required IconData icon,
    required List<CatalogSlot> slots,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 16, color: AppColors.secondaryTeal),
            const SizedBox(width: 6),
            Text(
              title,
              style: AppTypography.labelMd(
                color: AppColors.navyAnchor,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: slots.map((s) => _buildSlotCard(s)).toList(),
        ),
      ],
    );
  }

  Widget _buildSlotCard(CatalogSlot slot) {
    final isSelected = _selectedSlot?.id == slot.id;
    final isBookable = slot.bookable && (slot.availableCapacity ?? 0) >= _quantity;
    final capacity = slot.availableCapacity ?? 0;
    final startDisplay = slot.startTime.length >= 5
        ? slot.startTime.substring(0, 5)
        : slot.startTime;
    final endDisplay = slot.endTime.length >= 5
        ? slot.endTime.substring(0, 5)
        : slot.endTime;

    final cardWidth = (MediaQuery.sizeOf(context).width - 40 - 10) / 2;

    return InkWell(
      onTap: isBookable
          ? () {
              setState(() {
                _selectedSlot = slot;
              });
            }
          : null,
      borderRadius: AppShapes.radiusDefault,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        width: cardWidth,
        padding: const EdgeInsets.symmetric(vertical: 11, horizontal: 12),
        decoration: BoxDecoration(
          color: !isBookable
              ? AppColors.surfaceContainerHigh.withValues(alpha: 0.35)
              : isSelected
                  ? AppColors.secondary.withValues(alpha: 0.08)
                  : AppColors.surfaceContainerLowest,
          borderRadius: AppShapes.radiusDefault,
          border: Border.all(
            color: !isBookable
                ? AppColors.surfaceVariant
                : isSelected
                    ? AppColors.secondary
                    : AppColors.surfaceVariant.withValues(alpha: 0.8),
            width: isSelected ? 1.8 : 1.0,
          ),
          boxShadow: isSelected ? AppShapes.shadowLevel1 : null,
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Text(
                        startDisplay,
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                          color: !isBookable
                              ? AppColors.outline
                              : isSelected
                                  ? AppColors.secondary
                                  : AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '~ $endDisplay',
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w500,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  if (slot.price != null)
                    Text(
                      _formatPrice(slot.price!),
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                        color: !isBookable
                            ? AppColors.outline
                            : AppColors.primary,
                      ),
                    ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
              decoration: BoxDecoration(
                color: !isBookable
                    ? AppColors.surfaceVariant
                    : capacity <= 5
                        ? const Color(0xFFFFF3E0)
                        : const Color(0xFFE0F2F1),
                borderRadius: AppShapes.radiusFull,
              ),
              child: Text(
                !isBookable
                    ? 'Hết ca'
                    : 'Còn $capacity',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: !isBookable
                      ? AppColors.outline
                      : capacity <= 5
                          ? const Color(0xFFE65100)
                          : AppColors.secondary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomBar() {
    final hasSelection = _selectedSlot != null;
    final slot = _selectedSlot;
    final totalPrice = (slot?.price ?? 0) * _quantity;

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 18),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        boxShadow: [
          BoxShadow(
            color: AppColors.navyAnchor.withValues(alpha: 0.08),
            blurRadius: 16,
            offset: const Offset(0, -4),
          ),
        ],
        border: Border(
          top: BorderSide(
            color: AppColors.surfaceVariant.withValues(alpha: 0.8),
            width: 0.8,
          ),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: hasSelection && slot != null
                  ? Text(
                      _formatPrice(totalPrice),
                      style: AppTypography.headlineSm(
                        color: AppColors.primary,
                        fontWeight: FontWeight.w800,
                      ).copyWith(fontSize: 20),
                    )
                  : LocalizedText(
                      'Chưa chọn khung giờ',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: AppColors.outline,
                      ),
                    ),
            ),
            const SizedBox(width: 12),
            if (widget.isCartEdit)
              InkWell(
                onTap: hasSelection
                    ? () {
                        if (slot == null) return;
                        widget.onUpdateCart?.call(slot, _quantity);
                        Navigator.of(context).pop();
                      }
                    : null,
                borderRadius: AppShapes.radiusFull,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 26,
                    vertical: 13,
                  ),
                  decoration: BoxDecoration(
                    color: hasSelection
                        ? AppColors.secondary
                        : AppColors.surfaceVariant,
                    borderRadius: AppShapes.radiusFull,
                    boxShadow: hasSelection ? AppShapes.shadowLevel1 : null,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      LocalizedText(
                        'Cập nhật',
                        style: AppTypography.labelLg(
                          color: hasSelection
                              ? AppColors.onSecondary
                              : AppColors.outline,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Icon(
                        Icons.check_rounded,
                        size: 18,
                        color: hasSelection
                            ? AppColors.onSecondary
                            : AppColors.outline,
                      ),
                    ],
                  ),
                ),
              )
            else ...[
              // Nút Thêm vào giỏ hàng (Icon Button)
              InkWell(
                onTap: hasSelection && !_holding ? _addToCart : null,
                borderRadius: AppShapes.radiusFull,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: hasSelection && !_holding
                        ? AppColors.secondary.withValues(alpha: 0.12)
                        : AppColors.surfaceVariant.withValues(alpha: 0.5),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: hasSelection && !_holding
                          ? AppColors.secondary.withValues(alpha: 0.35)
                          : Colors.transparent,
                      width: 1.2,
                    ),
                  ),
                  child: Icon(
                    Icons.add_shopping_cart_rounded,
                    size: 20,
                    color: hasSelection && !_holding
                        ? AppColors.secondary
                        : AppColors.outline,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              // Nút Giữ chỗ ngay (Primary CTA)
              InkWell(
                onTap: hasSelection && !_holding ? _submitBooking : null,
                borderRadius: AppShapes.radiusFull,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 13,
                  ),
                  decoration: BoxDecoration(
                    color: hasSelection && !_holding
                        ? AppColors.secondary
                        : AppColors.surfaceVariant,
                    borderRadius: AppShapes.radiusFull,
                    boxShadow: hasSelection ? AppShapes.shadowLevel1 : null,
                  ),
                  child: _holding
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.onSecondary,
                          ),
                        )
                      : Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            LocalizedText(
                              'Thanh toán',
                              style: AppTypography.labelLg(
                                color: hasSelection
                                    ? AppColors.onSecondary
                                    : AppColors.outline,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Icon(
                              Icons.arrow_forward_rounded,
                              size: 18,
                              color: hasSelection
                                  ? AppColors.onSecondary
                                  : AppColors.outline,
                            ),
                          ],
                        ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Bảng thông báo dạng HUD nhỏ gọn xuất hiện giữa màn hình khi thêm giỏ hàng thành công
class _AddToCartToastWidget extends StatefulWidget {
  final VoidCallback onDismissed;
  const _AddToCartToastWidget({required this.onDismissed});

  @override
  State<_AddToCartToastWidget> createState() => _AddToCartToastWidgetState();
}

class _AddToCartToastWidgetState extends State<_AddToCartToastWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _opacity;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
    );
    _opacity = CurvedAnimation(parent: _controller, curve: Curves.easeOut);
    _scale = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutBack),
    );
    _controller.forward();

    // Hiển thị trong khoảng 2s rồi mờ dần và biến mất
    Future.delayed(const Duration(milliseconds: 1750), () {
      if (mounted) {
        _controller.reverse().then((_) {
          widget.onDismissed();
        });
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Material(
        color: Colors.transparent,
        child: FadeTransition(
          opacity: _opacity,
          child: ScaleTransition(
            scale: _scale,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 26, vertical: 20),
              decoration: BoxDecoration(
                color: const Color(0xEB1E293B),
                borderRadius: BorderRadius.circular(16),
                boxShadow: const [
                  BoxShadow(
                    color: Colors.black26,
                    blurRadius: 18,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: const [
                  Icon(
                    Icons.check_circle_rounded,
                    color: Color(0xFF22C55E),
                    size: 46,
                  ),
                  SizedBox(height: 10),
                  Text(
                    'Đã thêm vào giỏ hàng',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 14.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
