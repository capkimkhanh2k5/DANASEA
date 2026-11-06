import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
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
  final _db = VendorMockDatabase.instance;
  late SubOrderModel _order;

  @override
  void initState() {
    super.initState();
    _order = widget.subOrder;
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
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

  @override
  Widget build(BuildContext context) {
    final refund = _db.refunds.cast<RefundModel?>().firstWhere(
      (r) => r?.subOrderId == _order.subOrderId,
      orElse: () => null,
    );
    final hasRefund = refund != null;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText('ĐƠN CON #${_order.subOrderCode}',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5)),
            LocalizedText('Mã gốc: ${_order.masterOrderCode}', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
          ],
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Status & Quick Summary Banner
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withOpacity(0.3),
                  borderRadius: AppShapes.radiusSm,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(width: 8, height: 8, decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary)),
                          const SizedBox(width: 6),
                          const Flexible(
                            child: LocalizedText(
                              'TRẠNG THÁI ĐƠN CON',
                              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer, letterSpacing: 0.5),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    StatusBadge(status: _order.status),
                  ],
                ),
              ),
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
                                    LocalizedText(_order.customerPhone, style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant), maxLines: 1, overflow: TextOverflow.ellipsis),
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
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => ChatConversationScreen(
                                    conversationId: 'conv-001',
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
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: LocalizedText(_order.serviceName, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface), overflow: TextOverflow.ellipsis),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.secondaryFixed, borderRadius: BorderRadius.circular(8)),
                          child: const LocalizedText('Mỹ Khê Ocean', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixed)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        const Icon(Icons.schedule, size: 16, color: AppColors.secondary),
                        const SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText('${_order.slotDate} • ${_order.slotTime}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface), overflow: TextOverflow.ellipsis),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: const [
                        Icon(Icons.storefront, size: 16, color: AppColors.tertiary),
                        SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText('Nhà cung cấp: Danang Ocean Club', style: TextStyle(fontSize: 12, color: AppColors.tertiary), overflow: TextOverflow.ellipsis),
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
                            'Đơn giá ${_order.quantity} khách (${_order.quantity} × ${_order.unitPrice.toStringAsFixed(0)} đ)',
                            style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        LocalizedText('${_order.totalPrice.toStringAsFixed(0)} đ', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface)),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: LocalizedText(
                            'Hoa hồng sàn DANASEA (${(_order.commissionRate > 1 ? _order.commissionRate : _order.commissionRate * 100).toStringAsFixed(0)}%)',
                            style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        LocalizedText('-${_order.commissionAmount.toStringAsFixed(0)} đ', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.error)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.primaryFixed.withOpacity(0.4),
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
                            '${_order.payoutAmount.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
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
                                : 'Sẵn sàng quét mã QR khi khách tập trung tại bãi cát Mỹ Khê.',
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
                    color: AppColors.errorContainer.withOpacity(0.4),
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
    );
  }
}
