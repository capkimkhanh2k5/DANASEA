import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/widgets/vendor_button.dart';
import 'qr_checkin_screen.dart';
import 'refund_detail_screen.dart';
import '../../../chat/presentation/screens/chat_conversation_screen.dart';

class SubOrderDetailScreen extends StatefulWidget {
  final SubOrderModel subOrder;

  const SubOrderDetailScreen({super.key, required this.subOrder});

  @override
  State<SubOrderDetailScreen> createState() => _SubOrderDetailScreenState();
}

class _SubOrderDetailScreenState extends State<SubOrderDetailScreen> {
  final _db = VendorStore.instance;
  late SubOrderModel _order;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _order = widget.subOrder;
    _db.addListener(_onDbChanged);
    _loadFreshDetail();
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  Future<void> _loadFreshDetail() async {
    setState(() => _isLoading = true);
    try {
      final fresh = await VendorOrderRepository().getSubOrderById(_order.subOrderId);
      if (fresh != null && mounted) {
        setState(() {
          _order = fresh;
        });
      }
    } catch (_) {}
    if (mounted) {
      setState(() => _isLoading = false);
    }
  }

  void _onDbChanged() {
    final fresh = _db.subOrders.firstWhere(
      (o) => o.subOrderId == _order.subOrderId,
      orElse: () => _order,
    );
    if (mounted) {
      setState(() {
        _order = fresh;
      });
    }
  }

  Future<void> _openRescheduleProposalSheet() async {
    final slotRepo = VendorSlotRepository();
    final orderRepo = VendorOrderRepository();

    // 1. Xác định chính xác serviceId của dịch vụ đơn hàng
    String targetServiceId = _order.serviceId;
    if (targetServiceId.isEmpty && _db.services.isNotEmpty) {
      final match = _db.services.firstWhere(
        (srv) => srv.name.toLowerCase() == _order.serviceName.toLowerCase(),
        orElse: () => _db.services.first,
      );
      targetServiceId = match.serviceId;
    }

    if (targetServiceId.isNotEmpty) {
      await slotRepo.getSlotsForService(targetServiceId, DateTime.now()).catchError((_) => <ServiceSlotModel>[]);
    }
    if (!mounted) return;

    // 2. Chỉ hiển thị ca của dịch vụ hiện tại, từ hôm nay trở đi, còn đủ chỗ trống
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final availableSlots = _db.slots.where((s) {
      if (targetServiceId.isNotEmpty && s.serviceId != targetServiceId) return false;
      if (s.date.isBefore(today)) return false;
      return s.status == SlotStatus.open && s.availableCount >= _order.quantity && s.slotId != _order.slotId;
    }).toList();

    // Sắp xếp ngày & khung giờ tăng dần
    availableSlots.sort((a, b) {
      final d = a.date.compareTo(b.date);
      if (d != 0) return d;
      return a.startTime.compareTo(b.startTime);
    });

    String? selectedSlotId = availableSlots.isNotEmpty ? availableSlots.first.slotId : null;
    String reasonType = 'WEATHER';
    final reasonCtrl = TextEditingController(
      text: 'Thời tiết biển có sóng gió bất lợi, đề xuất chuyển sang khung giờ an toàn hơn.',
    );
    bool submitting = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 16,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + MediaQuery.of(ctx).padding.bottom + 20,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SingleChildScrollView(
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
                const Row(
                  children: [
                    Icon(Icons.event_repeat, color: AppColors.secondary, size: 20),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Đề xuất dời lịch khởi hành cho khách',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text(
                  'Loại nguyên nhân:',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    ChoiceChip(
                      avatar: Icon(
                        Icons.thunderstorm_outlined,
                        size: 15,
                        color: reasonType == 'WEATHER' ? Colors.white : AppColors.secondary,
                      ),
                      label: const Text('Thời tiết xấu (WEATHER)'),
                      selected: reasonType == 'WEATHER',
                      selectedColor: AppColors.secondary,
                      labelStyle: TextStyle(
                        fontSize: 12,
                        fontWeight: reasonType == 'WEATHER' ? FontWeight.bold : FontWeight.normal,
                        color: reasonType == 'WEATHER' ? Colors.white : AppColors.onSurface,
                      ),
                      onSelected: (v) {
                        if (v) setSheetState(() => reasonType = 'WEATHER');
                      },
                    ),
                    ChoiceChip(
                      avatar: Icon(
                        Icons.build_outlined,
                        size: 15,
                        color: reasonType == 'OPERATIONAL' ? Colors.white : AppColors.secondary,
                      ),
                      label: const Text('Vận hành (OPERATIONAL)'),
                      selected: reasonType == 'OPERATIONAL',
                      selectedColor: AppColors.secondary,
                      labelStyle: TextStyle(
                        fontSize: 12,
                        fontWeight: reasonType == 'OPERATIONAL' ? FontWeight.bold : FontWeight.normal,
                        color: reasonType == 'OPERATIONAL' ? Colors.white : AppColors.onSurface,
                      ),
                      onSelected: (v) {
                        if (v) setSheetState(() => reasonType = 'OPERATIONAL');
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text(
                  'Chọn ca thay thế:',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 6),
                if (availableSlots.isEmpty)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Text(
                      'Chưa có ca mở bán khác đủ chỗ trống cho dịch vụ này. Vui lòng mở thêm slot trong Lịch hoạt động.',
                      style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                    ),
                  )
                else
                  DropdownButtonFormField<String>(
                    initialValue: selectedSlotId,
                    isExpanded: true,
                    menuMaxHeight: 240, // Giới hạn chiều cao hiển thị tầm 4-5 ca, có thể lướt xuống nếu nhiều hơn
                    dropdownColor: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    icon: const Icon(
                      Icons.arrow_drop_down_rounded,
                      size: 28,
                      color: AppColors.secondary,
                    ),
                    decoration: InputDecoration(
                      filled: true,
                      fillColor: AppColors.surfaceContainerLow,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                    ),
                    items: availableSlots.map((s) {
                      String formatT(String t) => t.length >= 5 ? t.substring(0, 5) : t;
                      return DropdownMenuItem<String>(
                        value: s.slotId,
                        child: Row(
                          children: [
                            const Icon(Icons.event_available_rounded, size: 16, color: AppColors.secondary),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '${s.slotDate} • ${formatT(s.startTime)} - ${formatT(s.endTime)}',
                                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                              decoration: BoxDecoration(
                                color: AppColors.secondary.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                'Trống ${s.availableCount}',
                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary),
                              ),
                            ),
                          ],
                        ),
                      );
                    }).toList(),
                    onChanged: (val) => setSheetState(() => selectedSlotId = val),
                  ),
                const SizedBox(height: 12),
                const Text(
                  'Lý do gửi khách hàng:',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 6),
                TextField(
                  controller: reasonCtrl,
                  maxLines: 3,
                  style: const TextStyle(fontSize: 13),
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    onPressed: (submitting || selectedSlotId == null)
                        ? null
                        : () async {
                            final reason = reasonCtrl.text.trim();
                            if (reason.isEmpty) {
                              ToastNotification.showError(ctx, 'Vui lòng nhập lý do dời lịch');
                              return;
                            }
                            setSheetState(() => submitting = true);
                            try {
                              await orderRepo.createRescheduleProposal(
                                subOrderId: _order.subOrderId,
                                proposedSlotId: selectedSlotId!,
                                reason: reason,
                                reasonType: reasonType,
                              );
                              if (!mounted) return;
                              Navigator.pop(ctx);
                              ToastNotification.showSuccess(
                                context,
                                'Đã gửi đề xuất dời lịch cho khách hàng thành công!',
                              );
                            } catch (e) {
                              setSheetState(() => submitting = false);
                              if (mounted) {
                                ToastNotification.showError(ctx, 'Lỗi gửi đề xuất: $e');
                              }
                            }
                          },
                    icon: submitting
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.send, size: 16),
                    label: const Text('Gửi đề xuất đổi ca', style: TextStyle(fontWeight: FontWeight.bold)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildOrderLifecycleStepper(SubOrderModel order) {
    final bool isCancelled = order.status == SubOrderStatus.cancelled;
    final bool isRejected = order.status == SubOrderStatus.rejected;
    final bool isRefunded = order.status == SubOrderStatus.refunded;
    final bool isTerminalNegative = isCancelled || isRejected || isRefunded;

    int currentStep = 0;
    if (order.status == SubOrderStatus.completed) {
      currentStep = 3;
    } else if (order.checkedInAt != null) {
      currentStep = 2;
    } else if (order.status == SubOrderStatus.confirmed) {
      currentStep = 1;
    } else {
      currentStep = 0;
    }

    final steps = [
      {'title': 'Đặt chỗ', 'icon': Icons.shopping_bag_outlined},
      {'title': 'Xác nhận', 'icon': Icons.check_circle_outline},
      {'title': 'Đã Check-in', 'icon': Icons.qr_code_scanner_rounded},
      {'title': 'Hoàn tất', 'icon': Icons.task_alt_rounded},
    ];

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Row(
                children: [
                  Icon(Icons.route_rounded, size: 16, color: AppColors.secondary),
                  SizedBox(width: 6),
                  Text(
                    'Tiến trình vòng đời đơn',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              if (isTerminalNegative)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFEF2F2),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFFCA5A5)),
                  ),
                  child: Text(
                    isCancelled ? 'Đã hủy' : (isRejected ? 'Đã từ chối' : 'Đã hoàn tiền'),
                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFFDC2626)),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: List.generate(steps.length * 2 - 1, (index) {
              if (index.isOdd) {
                final stepIndex = index ~/ 2;
                final isDone = !isTerminalNegative && currentStep > stepIndex;
                return Expanded(
                  child: Container(
                    height: 2.5,
                    color: isDone ? AppColors.secondary : const Color(0xFFE2E8F0),
                  ),
                );
              }
              final stepIndex = index ~/ 2;
              final isDone = !isTerminalNegative && currentStep > stepIndex;
              final isCurrent = !isTerminalNegative && currentStep == stepIndex;
              final step = steps[stepIndex];

              final Color circleBg = isDone
                  ? AppColors.secondary
                  : (isCurrent ? AppColors.secondaryContainer : const Color(0xFFF1F5F9));
              final Color iconColor = isDone
                  ? Colors.white
                  : (isCurrent ? AppColors.secondary : const Color(0xFF94A3B8));
              final Color textColor = isCurrent
                  ? AppColors.secondary
                  : (isDone ? AppColors.navyAnchor : const Color(0xFF94A3B8));

              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: circleBg,
                      shape: BoxShape.circle,
                      border: isCurrent
                          ? Border.all(color: AppColors.secondary, width: 2)
                          : null,
                    ),
                    child: Icon(
                      isDone ? Icons.check : (step['icon'] as IconData),
                      size: 16,
                      color: iconColor,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    step['title'] as String,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: isCurrent || isDone ? FontWeight.bold : FontWeight.w500,
                      color: textColor,
                    ),
                  ),
                ],
              );
            }),
          ),
        ],
      ),
    );
  }

  Widget _buildOrderInfoRow(BuildContext context, {required String label, required String value, required bool canCopy}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 95,
          child: Text(
            label,
            style: const TextStyle(fontSize: 12, color: AppColors.tertiary),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurface),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (canCopy) ...[
          const SizedBox(width: 6),
          InkWell(
            onTap: () {
              Clipboard.setData(ClipboardData(text: value));
              ToastNotification.showSuccess(context, 'Đã sao chép $value');
            },
            borderRadius: BorderRadius.circular(6),
            child: const Tooltip(
              message: 'Sao chép',
              child: Padding(
                padding: EdgeInsets.all(4),
                child: Icon(
                  Icons.copy_rounded,
                  size: 16,
                  color: AppColors.secondary,
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final refund = _db.refunds.cast<RefundModel?>().firstWhere(
      (r) => r?.subOrderId == _order.subOrderId,
      orElse: () => null,
    );
    final hasRefund = refund != null;

    final double effRate = _order.commissionRate > 0
        ? (_order.commissionRate > 1 ? _order.commissionRate : _order.commissionRate * 100)
        : 10.0;
    final double effCommissionAmount = _order.commissionAmount > 0
        ? _order.commissionAmount
        : (_order.subtotalAmount * effRate / 100);
    final double effPayoutAmount = _order.payoutAmount > 0 && _order.payoutAmount < _order.subtotalAmount
        ? _order.payoutAmount
        : (_order.subtotalAmount - effCommissionAmount);

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const LocalizedText(
          'Chi tiết đơn hàng',
          style: TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.navyAnchor,
          ),
        ),
        bottom: _isLoading
            ? const PreferredSize(
                preferredSize: Size.fromHeight(2),
                child: LinearProgressIndicator(minHeight: 2, color: AppColors.secondary),
              )
            : null,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _loadFreshDetail,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_order.cancellationReason != null && _order.cancellationReason!.isNotEmpty) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFEF2F2),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFFFCA5A5)),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.info_outline, color: Color(0xFFDC2626), size: 18),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Lý do hủy/hoàn: ${_order.cancellationReason}',
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFFDC2626)),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                if (_order.rescheduledAt != null) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0FDF4),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFF86EFAC)),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.update, color: Color(0xFF16A34A), size: 18),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Đơn hàng đã được dời lịch khởi hành thành công',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF16A34A)),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                // Order Lifecycle Stepper
                _buildOrderLifecycleStepper(_order),
                const SizedBox(height: 14),

                // Customer Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
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
                                Container(
                                  width: 44,
                                  height: 44,
                                  decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.primaryFixed),
                                  child: Center(
                                    child: LocalizedText(
                                      _order.customerName.isNotEmpty ? _order.customerName[0].toUpperCase() : 'K',
                                      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixed),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const LocalizedText('KHÁCH HÀNG CHÍNH', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.tertiary)),
                                      LocalizedText(_order.customerName, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                                      LocalizedText(_order.customerPhone.isNotEmpty ? _order.customerPhone : (_order.customerEmail ?? 'Chưa có SĐT'), style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant), maxLines: 1, overflow: TextOverflow.ellipsis),
                                      if (_order.customerEmail != null && _order.customerEmail!.isNotEmpty && _order.customerPhone.isNotEmpty)
                                        LocalizedText(_order.customerEmail!, style: const TextStyle(fontSize: 11, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(12)),
                          child: LocalizedText('${_order.quantity} khách', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () {
                              final matchedConv = _db.conversations.where(
                                (c) => c.subOrderId == _order.subOrderId || c.masterOrderId == _order.masterOrderId || c.customerName == _order.customerName,
                              );
                              final convId = matchedConv.isNotEmpty
                                  ? matchedConv.first.id
                                  : (_db.conversations.isNotEmpty ? _db.conversations.first.id : _order.subOrderId);
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => ChatConversationScreen(
                                    conversationId: convId,
                                    customerName: _order.customerName,
                                    subOrderCode: _order.subOrderCode,
                                  ),
                                ),
                              );
                            },
                            icon: const Icon(Icons.chat_bubble_outline, size: 15),
                            label: const LocalizedText('Nhắn tin khách', style: TextStyle(fontSize: 12)),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.primary,
                              foregroundColor: AppColors.onPrimary,
                              elevation: 0,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                              padding: const EdgeInsets.symmetric(vertical: 8),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => QrCheckinScreen(initialLookupCode: _order.subOrderCode),
                                ),
                              );
                            },
                            icon: const Icon(Icons.qr_code_scanner, size: 15),
                            label: const LocalizedText('Quét vé QR', style: TextStyle(fontSize: 12)),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppColors.secondary,
                              side: const BorderSide(color: AppColors.secondary),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                              padding: const EdgeInsets.symmetric(vertical: 8),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // Experience & Slot Card
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
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                _order.serviceName,
                                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 4),
                              Row(
                                children: [
                                  const Icon(Icons.location_on, size: 13, color: AppColors.secondary),
                                  const SizedBox(width: 3),
                                  Expanded(
                                    child: Text(
                                      _db.services.any((s) => s.id == _order.serviceId && s.locationName.isNotEmpty)
                                          ? _db.services.firstWhere((s) => s.id == _order.serviceId).locationName
                                          : (_db.vendor.businessAddress != null && _db.vendor.businessAddress!.isNotEmpty
                                              ? _db.vendor.businessAddress!
                                              : '51 Võ Nguyên Giáp, Đà Nẵng'),
                                      style: const TextStyle(fontSize: 11, color: AppColors.tertiary, fontWeight: FontWeight.w500),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    if (_order.optionName != null && _order.optionName!.isNotEmpty) ...[
                      Row(
                        children: [
                          const Icon(Icons.confirmation_number_outlined, size: 16, color: AppColors.secondary),
                          const SizedBox(width: 6),
                          Expanded(
                            child: LocalizedText(
                              'Gói: ${_order.optionName!}',
                              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurfaceVariant),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                    ],
                    Row(
                      children: [
                        const Icon(Icons.schedule, size: 16, color: AppColors.secondary),
                        const SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            '${_order.slotDate} • ${_order.slotTime}${_order.endTime != null && _order.endTime!.isNotEmpty ? " - ${_order.endTime!.length >= 5 ? _order.endTime!.substring(0, 5) : _order.endTime}" : ""}',
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        const Icon(Icons.storefront_outlined, size: 16, color: AppColors.tertiary),
                        const SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            _db.vendor.businessName.isNotEmpty
                                ? (_db.vendor.businessName.contains('(')
                                    ? _db.vendor.businessName.split('(').first.trim()
                                    : _db.vendor.businessName)
                                : 'Danabeach Club',
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurfaceVariant),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // Financial Snapshot Breakdown
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
                              Icon(Icons.receipt_long, size: 18, color: AppColors.primary),
                              SizedBox(width: 6),
                              Expanded(
                                child: LocalizedText(
                                  'Chi tiết tài chính',
                                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(8)),
                          child: const LocalizedText('Snapshot', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: LocalizedText(
                            'Đơn giá ${_order.quantity} khách (${_order.quantity} × ${_order.unitPrice.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ)',
                            style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        LocalizedText(
                          '${_order.totalPrice.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: LocalizedText(
                            'Hoa hồng sàn DANASEA (${effRate.toStringAsFixed(0)}%)',
                            style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        LocalizedText(
                          '-${effCommissionAmount.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.error),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.primaryFixed.withValues(alpha: 0.4),
                        borderRadius: AppShapes.radiusSm,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText('THỰC NHẬN ĐỐI TÁC', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant, letterSpacing: 0.5), maxLines: 1, overflow: TextOverflow.ellipsis),
                                LocalizedText('Tự động quyết toán theo kỳ', style: TextStyle(fontSize: 10, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          LocalizedText(
                            '${effPayoutAmount.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
                            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.primary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // Safety & Check-in Verification Card
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
                        Icon(Icons.verified_user, size: 18, color: AppColors.secondary),
                        SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText('Kiểm tra an toàn & Check-in', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    // Liability waiver
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(color: AppColors.surfaceContainerLow, borderRadius: BorderRadius.circular(8)),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Expanded(
                                child: LocalizedText('Cam kết miễn trừ trách nhiệm', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                              ),
                              const SizedBox(width: 6),
                              Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.draw, size: 13, color: _order.waiverSignedAt != null ? AppColors.secondary : AppColors.tertiary),
                                  const SizedBox(width: 3),
                                  LocalizedText(
                                    _order.waiverSignedAt != null ? 'ĐÃ KÝ ĐIỆN TỬ' : 'CHƯA KÝ',
                                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: _order.waiverSignedAt != null ? AppColors.secondary : AppColors.tertiary),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          if (_order.waiverSignedAt != null) ...[
                            const SizedBox(height: 4),
                            LocalizedText('Thời gian ký: ${_order.waiverSignedAt!.day}/${_order.waiverSignedAt!.month} lúc ${_order.waiverSignedAt!.hour}:${_order.waiverSignedAt!.minute.toString().padLeft(2, '0')}',
                                style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 10),
                    // Check-in status
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(color: AppColors.surfaceContainerLow, borderRadius: BorderRadius.circular(8)),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Expanded(
                                child: LocalizedText('Trạng thái vào bãi (Check-in)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: _order.checkedInAt != null ? AppColors.secondaryContainer : AppColors.surfaceContainer,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: LocalizedText(
                                  _order.checkedInAt != null ? 'ĐÃ CHECK-IN' : 'Chưa check-in',
                                  style: TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                    color: _order.checkedInAt != null ? AppColors.onSecondaryContainer : AppColors.tertiary,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          LocalizedText(
                            _order.checkedInAt != null
                                ? 'Thời gian vào bãi: ${_order.checkedInAt!.day}/${_order.checkedInAt!.month} lúc ${_order.checkedInAt!.hour}:${_order.checkedInAt!.minute.toString().padLeft(2, '0')}'
                                : 'Sẵn sàng quét mã QR khi khách tập trung tại điểm hẹn.',
                            style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // If refund exists, link to refund detail
              if (hasRefund) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer.withValues(alpha: 0.4),
                    borderRadius: AppShapes.radiusMd,
                    border: Border.all(color: AppColors.errorContainer),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.currency_exchange, color: AppColors.error, size: 22),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const LocalizedText('Đơn có khoản hoàn tiền', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onErrorContainer)),
                            LocalizedText('Lý do: ${refund.reason.labelVi}', style: const TextStyle(fontSize: 11, color: AppColors.onErrorContainer)),
                          ],
                        ),
                      ),
                      TextButton(
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => RefundDetailScreen(refund: refund, subOrder: _order),
                            ),
                          );
                        },
                        child: const LocalizedText('Xem chi tiết', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.primary)),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              // Order Identifiers Card (Shopee Style: Mã đơn con & Mã gốc ở dưới)
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
                      children: const [
                        Icon(Icons.receipt_outlined, size: 16, color: AppColors.secondary),
                        SizedBox(width: 6),
                        Text(
                          'Mã định danh đơn hàng',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                        ),
                      ],
                    ),
                    const Divider(height: 18, color: Color(0xFFF1F5F9)),
                    _buildOrderInfoRow(
                      context,
                      label: 'Mã đơn con:',
                      value: _order.subOrderCode,
                      canCopy: true,
                    ),
                    const SizedBox(height: 8),
                    _buildOrderInfoRow(
                      context,
                      label: 'Mã đơn gốc:',
                      value: _order.masterOrderCode,
                      canCopy: true,
                    ),
                    const SizedBox(height: 8),
                    _buildOrderInfoRow(
                      context,
                      label: 'Thời gian đặt:',
                      value: '${_order.createdAt.day.toString().padLeft(2, '0')}/${_order.createdAt.month.toString().padLeft(2, '0')}/${_order.createdAt.year} lúc ${_order.createdAt.hour.toString().padLeft(2, '0')}:${_order.createdAt.minute.toString().padLeft(2, '0')}',
                      canCopy: false,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // State Actions
              if (_order.status == SubOrderStatus.confirmed && _order.checkedInAt != null) ...[
                VendorButton(
                  text: 'Hoàn thành chuyến đi',
                  icon: Icons.check_circle,
                  onPressed: () {
                    _db.completeSubOrder(_order.subOrderId);
                    ToastNotification.showSuccess(context, 'Đã cập nhật chuyến đi hoàn thành');
                  },
                ),
                const SizedBox(height: 10),
              ] else if (_order.status == SubOrderStatus.pending) ...[
                Row(
                  children: [
                    Expanded(
                      child: VendorButton(
                        text: 'Xác nhận đơn',
                        icon: Icons.check,
                        onPressed: () {
                          _db.confirmSubOrder(_order.subOrderId);
                          ToastNotification.showSuccess(context, 'Đã xác nhận đơn hàng');
                        },
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: VendorButton(
                        text: 'Từ chối',
                        variant: VendorButtonVariant.secondary,
                        onPressed: () {
                          _db.rejectSubOrder(_order.subOrderId);
                          ToastNotification.showInfo(context, 'Đã từ chối đơn hàng');
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
              ],

              if (_order.status == SubOrderStatus.pending || _order.status == SubOrderStatus.confirmed) ...[
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: _openRescheduleProposalSheet,
                    icon: const Icon(Icons.event_repeat, size: 17, color: AppColors.secondary),
                    label: const LocalizedText(
                      'Đề xuất dời lịch (Thời tiết / Vận hành)',
                      style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.secondary),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
              ],

              // Platform Policy Note
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: AppShapes.radiusSm),
                child: const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.policy, size: 16, color: AppColors.tertiary),
                    SizedBox(width: 8),
                    Expanded(
                      child: LocalizedText(
                        'Chính sách hoàn hủy và giải quyết khiếu nại áp dụng theo quy chế bảo vệ khách hàng DANASEA. Đối tác phối hợp xử lý theo chuẩn dịch vụ bờ biển.',
                        style: TextStyle(fontSize: 10, color: AppColors.onSurfaceVariant),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    ),
  );
  }
}
