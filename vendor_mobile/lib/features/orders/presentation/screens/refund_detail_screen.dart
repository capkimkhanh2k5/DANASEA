import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_store.dart';
import 'sub_order_detail_screen.dart';

class RefundDetailScreen extends StatelessWidget {
  final RefundModel? refund;
  final SubOrderModel? subOrder;

  const RefundDetailScreen({
    super.key,
    this.refund,
    this.subOrder,
  });

  @override
  Widget build(BuildContext context) {
    final db = VendorStore.instance;
    final currentRefund = this.refund ?? (db.refunds.isNotEmpty ? db.refunds.first : null);
    if (currentRefund == null) {
      return Scaffold(
        backgroundColor: AppColors.surface,
        appBar: AppBar(
          backgroundColor: AppColors.surface,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          title: const LocalizedText('Chi tiết hoàn tiền', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
        ),
        body: const Center(
          child: LocalizedText('Chưa có dữ liệu hoàn tiền từ máy chủ.', style: TextStyle(color: AppColors.tertiary)),
        ),
      );
    }
    final refund = currentRefund;

    final subOrder = this.subOrder ?? (db.subOrders.any((o) => o.id == refund.subOrderId)
        ? db.subOrders.firstWhere((o) => o.id == refund.subOrderId)
        : SubOrderModel(
            id: refund.subOrderId,
            masterOrderId: refund.subOrderId,
            vendorId: db.vendor.id,
            serviceId: '',
            slotId: '',
            quantity: 1,
            unitPrice: refund.originalSubtotal,
            subtotalAmount: refund.originalSubtotal,
            commissionRate: 10.0,
            commissionAmount: refund.originalSubtotal * 0.1,
            vendorPayoutAmount: refund.originalSubtotal * 0.9,
            status: SubOrderStatus.refunded,
            qrSecret: '',
            customerName: refund.customerName ?? 'Khách hàng',
            customerPhone: '',
            serviceName: refund.serviceName ?? '',
            serviceImageUrl: '',
            slotDate: '',
            slotTime: '',
            createdAt: refund.processedAt ?? DateTime.now(),
          ));
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const LocalizedText('Chi tiết hoàn tiền', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // System Alert Notice
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withOpacity(0.5),
                  borderRadius: AppShapes.radiusMd,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 36,
                      height: 36,
                      decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary),
                      child: const Icon(Icons.cloud_sync, color: Colors.white, size: 20),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Flexible(
                                child: LocalizedText(
                                  'THÔNG BÁO TỪ HỆ THỐNG',
                                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer, letterSpacing: 0.5),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(color: Colors.white.withOpacity(0.8), borderRadius: BorderRadius.circular(8)),
                                child: const LocalizedText('Bất khả kháng', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.primary)),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          LocalizedText(
                            'Khoản hoàn tiền được xử lý theo quy định bảo vệ khách hàng do điều kiện thời tiết biển bất khả kháng (Đơn #${subOrder.subOrderCode}).',
                            style: const TextStyle(fontSize: 11, color: AppColors.onSecondaryFixedVariant),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // Related Sub-Order Overview Card
              Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => SubOrderDetailScreen(subOrder: subOrder),
                      ),
                    );
                  },
                  borderRadius: AppShapes.radiusMd,
                  child: Container(
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
                              child: Row(
                                children: [
                                  const Icon(Icons.receipt_long, size: 16, color: AppColors.secondary),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: LocalizedText(
                                      'Đơn con: #${subOrder.subOrderCode}',
                                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                  decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(8)),
                                  child: LocalizedText('Slot ${subOrder.slotTime}', style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                                ),
                                const SizedBox(width: 4),
                                const Icon(Icons.chevron_right, size: 16, color: AppColors.tertiary),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        LocalizedText(subOrder.serviceName, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                        const SizedBox(height: 2),
                        LocalizedText('Nhà cung cấp: nhà cung cấp', style: const TextStyle(fontSize: 11, color: AppColors.tertiary)),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(color: AppColors.surfaceContainerLow, borderRadius: BorderRadius.circular(10)),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const LocalizedText('KHÁCH HÀNG', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.tertiary)),
                                    const SizedBox(height: 4),
                                    LocalizedText(subOrder.customerName, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                    LocalizedText('Ngày: ${subOrder.slotDate}', style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(color: AppColors.surfaceContainerLow, borderRadius: BorderRadius.circular(10)),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const LocalizedText('TIỀN ĐƠN BAN ĐẦU', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.tertiary)),
                                    const SizedBox(height: 4),
                                    LocalizedText(
                                      '${subOrder.totalPrice.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
                                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                    ),
                                    const Row(
                                      children: [
                                        Icon(Icons.check_circle, size: 10, color: AppColors.secondary),
                                        SizedBox(width: 2),
                                        Flexible(
                                          child: LocalizedText(
                                            'Đã thanh toán',
                                            style: TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.bold),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),

              // Main Refund Execution Card
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
                                width: 32,
                                height: 32,
                                decoration: BoxDecoration(shape: BoxShape.circle, color: AppColors.primaryContainer.withOpacity(0.2)),
                                child: const Icon(Icons.currency_exchange, color: AppColors.primary, size: 18),
                              ),
                              const SizedBox(width: 8),
                              const Expanded(
                                child: LocalizedText(
                                  'Khoản hoàn tiền dịch vụ',
                                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                          decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(12)),
                          child: LocalizedText(
                            'Hoàn ${(refund.percentage * 100).toStringAsFixed(0)}%',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    // Big Number Block
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [AppColors.primaryFixed.withOpacity(0.7), AppColors.surfaceContainerLowest],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        children: [
                          const LocalizedText('SỐ TIỀN HOÀN TRẢ CHO KHÁCH (AMOUNT)',
                              style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant, letterSpacing: 0.5)),
                          const SizedBox(height: 6),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.baseline,
                            textBaseline: TextBaseline.alphabetic,
                            children: [
                              Flexible(
                                child: LocalizedText(
                                  refund.amount.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.'),
                                  style: const TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: AppColors.primary),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 4),
                              const LocalizedText('VNĐ', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary)),
                            ],
                          ),
                          const SizedBox(height: 4),
                          const LocalizedText('Tự động trả về phương thức thanh toán ban đầu của khách hàng.',
                              style: TextStyle(fontSize: 11, color: AppColors.onPrimaryFixedVariant), textAlign: TextAlign.center),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Refund Reason
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: AppColors.surfaceContainerLow, borderRadius: BorderRadius.circular(10)),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Expanded(
                                child: LocalizedText(
                                  'Lý do hoàn tiền:',
                                  style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Flexible(
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                  decoration: BoxDecoration(color: AppColors.primaryFixed, borderRadius: BorderRadius.circular(8)),
                                  child: LocalizedText(
                                    refund.reason.labelVi,
                                    style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: const [
                              Icon(Icons.storm, size: 18, color: AppColors.primary),
                              SizedBox(width: 8),
                              Expanded(
                                child: LocalizedText(
                                  'Điều kiện thời tiết biển không đảm bảo an toàn hoạt động theo khuyến cáo của cơ quan quản lý vịnh Đà Nẵng.',
                                  style: TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant, height: 1.4),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),

                    // Processing status
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer.withOpacity(0.4),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 34,
                            height: 34,
                            decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary),
                            child: const Icon(Icons.verified, color: Colors.white, size: 18),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Flexible(
                                      child: LocalizedText(
                                        'Đã xử lý thành công',
                                        style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixedVariant),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                                      decoration: BoxDecoration(color: AppColors.secondary, borderRadius: BorderRadius.circular(6)),
                                      child: const LocalizedText('PROCESSED', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: Colors.white)),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 2),
                                LocalizedText(
                                  'Thời gian hoàn tất: ${refund.createdAt.hour}:${refund.createdAt.minute.toString().padLeft(2, '0')} • ${refund.createdAt.day}/${refund.createdAt.month}/${refund.createdAt.year}',
                                  style: const TextStyle(fontSize: 10, color: AppColors.onSecondaryContainer),
                                ),
                              ],
                            ),
                          ),
                          const Icon(Icons.task_alt, color: AppColors.secondary, size: 22),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Read-only advisory notice
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: AppShapes.radiusSm),
                child: const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, size: 16, color: AppColors.tertiary),
                    SizedBox(width: 8),
                    Expanded(
                      child: LocalizedText(
                        'Khoản hoàn tiền được hệ thống DANASEA tự động hạch toán giảm trừ vào kỳ đối soát tương ứng. Đối tác không cần thao tác tài chính thủ công.',
                        style: TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
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
