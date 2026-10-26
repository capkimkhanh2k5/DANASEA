import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/data/payment_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../checkout/presentation/screens/e_ticket_qr_screen.dart';
import '../../../checkout/presentation/screens/payment_result_screen.dart';
import '../../../refund_dispute/presentation/screens/cancel_booking_screen.dart';
import '../../../refund_dispute/presentation/screens/create_dispute_screen.dart';
import '../../../refund_dispute/presentation/screens/refund_tracking_screen.dart';
import '../../../reviews/presentation/screens/review_experience_screen.dart';

class OrderDetailScreen extends StatefulWidget {
  final MasterOrderModel? masterOrder;
  final String? orderId;

  const OrderDetailScreen({
    super.key,
    this.masterOrder,
    this.orderId,
  }) : assert(masterOrder != null || orderId != null);

  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen> {
  final OrderRepository _repository = OrderRepository();
  final PaymentRepository _paymentRepository = PaymentRepository();
  MasterOrderModel? _order;
  List<OrderPaymentRecord> _payments = [];
  List<RefundModel> _refunds = [];
  bool _loading = true;
  bool _loadingReceipt = false;
  bool _openingPayment = false;
  String? _error;
  int _generation = 0;

  String get _targetOrderId => widget.orderId ?? widget.masterOrder!.id;

  @override
  void initState() {
    super.initState();
    _loadDetail();
  }

  Future<void> _loadDetail() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final order = await _repository.detail(_targetOrderId);
      final serviceNames = <String, String>{
        for (final sub in order.subOrders) sub.id: sub.serviceName,
      };
      final results = await Future.wait<dynamic>([
        _paymentRepository.getOrderPayments(order.id),
        _repository.refunds(order.id, subOrderServiceNames: serviceNames),
      ]);
      if (!mounted || generation != _generation) return;
      setState(() {
        _order = order;
        _payments = results[0] as List<OrderPaymentRecord>;
        _refunds = results[1] as List<RefundModel>;
      });
    } on AuthFailure catch (e) {
      if (mounted && generation == _generation) {
        setState(() {
          _order = null;
          _error = e.message;
        });
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(() {
          _order = null;
          _error = 'Không thể tải chi tiết đơn hàng. Vui lòng thử lại.';
        });
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  String _formatPrice(num price) {
    final str = price.round().toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i > 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  String _getMasterStatusTitle(MasterOrderStatus status) {
    switch (status) {
      case MasterOrderStatus.pendingPayment:
        return 'Đang chờ thanh toán';
      case MasterOrderStatus.paid:
        return 'Đã thanh toán thành công';
      case MasterOrderStatus.partiallyCompleted:
        return 'Hoàn thành một phần';
      case MasterOrderStatus.completed:
        return 'Đã hoàn thành';
      case MasterOrderStatus.cancelled:
        return 'Đã hủy';
    }
  }

  Future<void> _openPaymentDetail(
    MasterOrderModel order,
    OrderPaymentRecord summary,
  ) async {
    if (_openingPayment) return;
    setState(() => _openingPayment = true);
    try {
      final detail = await _paymentRepository.getPaymentDetail(summary.id);
      if (!mounted) return;
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => PaymentResultScreen(
            masterOrder: order,
            provider: PaymentRepository.providerFromBackend(detail.provider),
            isSuccess: detail.status.toUpperCase() == 'SUCCESS',
            initialPayment: detail,
            paymentId: detail.id,
          ),
        ),
      );
      if (mounted) _loadDetail();
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải chi tiết giao dịch thanh toán.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _openingPayment = false);
    }
  }

  Future<void> _openReceipt(MasterOrderModel order) async {
    if (_loadingReceipt) return;
    setState(() => _loadingReceipt = true);
    try {
      final receipt = await _repository.receipt(order.id);
      final pdfBytes = await _repository.receiptPdf(order.id);
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: AppColors.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        builder: (sheetContext) {
          final paidAt = receipt.paidAt?.toLocal();
          return SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          'Biên nhận điện tử ${receipt.receiptCode}',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.pop(sheetContext),
                      ),
                    ],
                  ),
                  Text(
                    '${tr(context, 'Mã đơn')}: ${receipt.orderCode} • ${receipt.status}',
                    style: AppTypography.bodySm(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  if (receipt.paymentProvider != null)
                    Text(
                      '${tr(context, 'Cổng thanh toán')}: ${receipt.paymentProvider}',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  if (paidAt != null)
                    Text(
                      '${tr(context, 'Thời gian thanh toán')}: ${paidAt.day}/${paidAt.month}/${paidAt.year} ${paidAt.hour.toString().padLeft(2, '0')}:${paidAt.minute.toString().padLeft(2, '0')}',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  const Divider(height: 20),
                  for (final item in receipt.items)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.serviceName,
                                  style: AppTypography.labelMd(
                                    color: AppColors.onSurface,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                Text(
                                  '${item.quantity} × ${_formatPrice(item.unitPrice)}',
                                  style: AppTypography.bodySm(
                                    color: AppColors.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _formatPrice(item.finalAmount),
                            style: AppTypography.labelMd(
                              color: AppColors.primary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  const Divider(height: 16),
                  if (receipt.discountAmount > 0)
                    _buildPriceRow(
                      'Giảm giá:',
                      '- ${_formatPrice(receipt.discountAmount)}',
                      valueColor: AppColors.secondary,
                    ),
                  const SizedBox(height: 6),
                  _buildPriceRow(
                    'Tổng thanh toán:',
                    _formatPrice(receipt.totalAmount),
                    valueColor: AppColors.primary,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'PDF: receipt-${order.id}.pdf (${(pdfBytes.lengthInBytes / 1024).toStringAsFixed(1)} KB)',
                    style: AppTypography.bodySm(color: AppColors.secondary),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Clipboard.setData(
                          ClipboardData(text: receipt.receiptCode),
                        );
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: LocalizedText(
                              'Đã sao chép mã biên nhận ${receipt.receiptCode}!',
                            ),
                          ),
                        );
                      },
                      icon: const Icon(Icons.copy, size: 16),
                      label: const LocalizedText('Sao chép mã biên nhận'),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      );
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải biên nhận điện tử. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingReceipt = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final profile = AuthSession.instance.profile;
    final fullName = (profile?['fullName']?.toString() ?? '').trim();
    final email = (profile?['email']?.toString() ?? '').trim();
    final phone = (profile?['phone']?.toString() ?? '').trim();
    final masterOrder = _order;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Column(
          children: [
            LocalizedText(
              'Chi tiết đơn #$_targetOrderId',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            LocalizedText(
              'Đặt chỗ trực tuyến',
              style: AppTypography.labelSm(
                color: AppColors.secondary,
              ).copyWith(fontSize: 10),
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.share, color: AppColors.onSurface),
            onPressed: () {
              Clipboard.setData(ClipboardData(text: _targetOrderId));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: LocalizedText('Đã sao chép mã đơn hàng!'),
                ),
              );
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadDetail,
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null || masterOrder == null
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(AppShapes.gutterMobile),
                    children: [
                      const SizedBox(height: 80),
                      const Icon(
                        Icons.error_outline,
                        size: 48,
                        color: AppColors.error,
                      ),
                      const SizedBox(height: 12),
                      Center(
                        child: LocalizedText(
                          _error ?? 'Không tìm thấy đơn hàng.',
                          textAlign: TextAlign.center,
                          style: AppTypography.bodyMd(
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Center(
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.secondary,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(18),
                            ),
                          ),
                          onPressed: _loadDetail,
                          child: const LocalizedText(
                            'Thử lại',
                            style: TextStyle(color: Colors.white),
                          ),
                        ),
                      ),
                    ],
                  )
                : SingleChildScrollView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(AppShapes.gutterMobile),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // OVERALL STATUS CARD
                        Container(
                          padding: const EdgeInsets.all(AppShapes.spaceSm),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: AppShapes.radiusDefault,
                            boxShadow: AppShapes.shadowLevel1,
                            border: Border.all(color: AppColors.borderSubtle),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Container(
                                    width: 36,
                                    height: 36,
                                    decoration: const BoxDecoration(
                                      color: AppColors.secondaryContainer,
                                      shape: BoxShape.circle,
                                    ),
                                    child: Icon(
                                      masterOrder.status ==
                                              MasterOrderStatus.cancelled
                                          ? Icons.cancel
                                          : masterOrder.status ==
                                                  MasterOrderStatus
                                                      .pendingPayment
                                              ? Icons.schedule
                                              : Icons.check_circle,
                                      color: masterOrder.status ==
                                              MasterOrderStatus.cancelled
                                          ? AppColors.error
                                          : AppColors.secondary,
                                      size: 22,
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        LocalizedText(
                                          _getMasterStatusTitle(
                                            masterOrder.status,
                                          ),
                                          style: AppTypography.headlineSm(
                                            color: masterOrder.status ==
                                                    MasterOrderStatus.cancelled
                                                ? AppColors.error
                                                : AppColors.secondary,
                                          ),
                                        ),
                                        LocalizedText(
                                          '${masterOrder.createdAt.hour.toString().padLeft(2, '0')}:${masterOrder.createdAt.minute.toString().padLeft(2, '0')} • ${masterOrder.createdAt.day}/${masterOrder.createdAt.month}/${masterOrder.createdAt.year}',
                                          style: AppTypography.bodySm(
                                            color: AppColors.onSurfaceVariant,
                                          ).copyWith(fontSize: 11),
                                        ),
                                      ],
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: AppColors.surfaceContainerHigh,
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: LocalizedText(
                                      '${masterOrder.subOrders.length} dịch vụ',
                                      style: AppTypography.labelSm(
                                        color: AppColors.onSurfaceVariant,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 10),
                              // Weather tip pill
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 10,
                                  vertical: 6,
                                ),
                                decoration: BoxDecoration(
                                  color: AppColors.surfaceContainerLow,
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Row(
                                  children: [
                                    const Icon(
                                      Icons.waves,
                                      size: 14,
                                      color: AppColors.secondary,
                                    ),
                                    const SizedBox(width: 6),
                                    Expanded(
                                      child: LocalizedText(
                                        'Biển Mỹ Khê & Bãi Bụt sáng êm, gió nhẹ 8km/h, sóng 0.4m trong lành.',
                                        style: AppTypography.bodySm(
                                          color: AppColors
                                              .onSecondaryFixedVariant,
                                        ).copyWith(fontSize: 11),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),

                        // SUB-ORDERS JOURNEY LIST
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: LocalizedText(
                                'Hành trình trải nghiệm',
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.headlineSm(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            LocalizedText(
                              'Độc lập từng chặng',
                              style: AppTypography.labelSm(
                                color: AppColors.secondary,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),

                        ...masterOrder.subOrders.map(
                          (sub) => _buildSubOrderDetailCard(
                            context,
                            masterOrder,
                            sub,
                          ),
                        ),

                        const SizedBox(height: 16),

                        // FINANCIAL SUMMARY CARD
                        Container(
                          padding: const EdgeInsets.all(AppShapes.spaceSm),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: AppShapes.radiusDefault,
                            border: Border.all(color: AppColors.borderSubtle),
                            boxShadow: AppShapes.shadowLevel1,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                'Hóa đơn & Thanh toán',
                                style: AppTypography.headlineSm(
                                  color: AppColors.onSurface,
                                ),
                              ),
                              const SizedBox(height: 12),
                              _buildPriceRow(
                                'Tổng giá dịch vụ:',
                                _formatPrice(masterOrder.subtotalAmount),
                              ),
                              if (masterOrder.discountAmount > 0) ...[
                                const SizedBox(height: 6),
                                _buildPriceRow(
                                  'Mã giảm giá (${masterOrder.discountCode ?? 'DISCOUNT'}):',
                                  '- ${_formatPrice(masterOrder.discountAmount)}',
                                  valueColor: AppColors.secondary,
                                ),
                              ],
                              const Divider(height: 16),
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  Expanded(
                                    child: LocalizedText(
                                      'Tổng thực thu:',
                                      style: AppTypography.labelLg(
                                        color: AppColors.onSurface,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  LocalizedText(
                                    _formatPrice(masterOrder.totalAmount),
                                    style: AppTypography.headlineSm(
                                      color: AppColors.primary,
                                    ),
                                  ),
                                ],
                              ),
                              if (masterOrder.status ==
                                  MasterOrderStatus.pendingPayment) ...[
                                const SizedBox(height: 12),
                                SizedBox(
                                  width: double.infinity,
                                  child: ElevatedButton.icon(
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: AppColors.primary,
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(20),
                                      ),
                                      minimumSize: const Size(
                                        double.infinity,
                                        44,
                                      ),
                                    ),
                                    onPressed: () async {
                                      await Navigator.push<void>(
                                        context,
                                        MaterialPageRoute(
                                          builder: (_) => CheckoutScreen(
                                            masterOrder: masterOrder,
                                          ),
                                        ),
                                      );
                                      if (mounted) _loadDetail();
                                    },
                                    icon: const Icon(
                                      Icons.payment,
                                      size: 18,
                                      color: Colors.white,
                                    ),
                                    label: const LocalizedText(
                                      'Thanh toán đơn hàng',
                                      style: TextStyle(color: Colors.white),
                                    ),
                                  ),
                                ),
                              ],
                              if (_payments.isNotEmpty) ...[
                                const Divider(height: 16),
                                LocalizedText(
                                  'Giao dịch thanh toán (${_payments.length})',
                                  style: AppTypography.labelMd(
                                    color: AppColors.onSurface,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 6),
                                for (final p in _payments)
                                  InkWell(
                                    onTap: _openingPayment
                                        ? null
                                        : () => _openPaymentDetail(
                                            masterOrder,
                                            p,
                                          ),
                                    borderRadius: BorderRadius.circular(8),
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(
                                        vertical: 4,
                                      ),
                                      child: _buildPriceRow(
                                        '${p.provider} • ${p.status}',
                                        _formatPrice(p.amount),
                                      ),
                                    ),
                                  ),
                              ],
                              if (_refunds.isNotEmpty) ...[
                                const Divider(height: 16),
                                Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Expanded(
                                      child: LocalizedText(
                                        'Yêu cầu hoàn tiền (${_refunds.length})',
                                        style: AppTypography.labelMd(
                                          color: AppColors.secondary,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    ),
                                    TextButton(
                                      onPressed: () {
                                        Navigator.push(
                                          context,
                                          MaterialPageRoute(
                                            builder: (_) =>
                                                RefundTrackingScreen(
                                              orderId: masterOrder.id,
                                              initialRefunds: _refunds,
                                            ),
                                          ),
                                        );
                                      },
                                      child: const LocalizedText(
                                        'Theo dõi hoàn tiền',
                                      ),
                                    ),
                                  ],
                                ),
                                for (final r in _refunds)
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 4),
                                    child: _buildPriceRow(
                                      '${r.subOrderCode} • ${r.refundPercentage.toInt()}%',
                                      _formatPrice(r.amount),
                                      valueColor: AppColors.secondary,
                                    ),
                                  ),
                              ],
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),

                        // CUSTOMER INFO
                        Container(
                          padding: const EdgeInsets.all(AppShapes.spaceSm),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: AppShapes.radiusDefault,
                            border: Border.all(color: AppColors.borderSubtle),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                'Thông tin liên lạc',
                                style: AppTypography.labelLg(
                                  color: AppColors.onSurface,
                                ),
                              ),
                              const SizedBox(height: 8),
                              LocalizedText(
                                [
                                  if (fullName.isNotEmpty) fullName,
                                  if (phone.isNotEmpty) phone,
                                ].join(' • ').isNotEmpty
                                    ? [
                                        if (fullName.isNotEmpty) fullName,
                                        if (phone.isNotEmpty) phone,
                                      ].join(' • ')
                                    : (email.isNotEmpty
                                        ? email
                                        : '#${masterOrder.customerId}'),
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurface,
                                ),
                              ),
                              if (email.isNotEmpty)
                                LocalizedText(
                                  email,
                                  style: AppTypography.bodySm(
                                    color: AppColors.outline,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 20),

                        // INVOICE DOWNLOAD BUTTON
                        OutlinedButton.icon(
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: AppColors.secondary),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(20),
                            ),
                            minimumSize: const Size(double.infinity, 44),
                          ),
                          icon: _loadingReceipt
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(
                                  Icons.receipt_long,
                                  color: AppColors.secondary,
                                ),
                          label: LocalizedText(
                            _loadingReceipt
                                ? 'Đang tải biên nhận...'
                                : 'Tải hóa đơn điện tử VAT (PDF)',
                            style: AppTypography.labelMd(
                              color: AppColors.secondary,
                            ),
                          ),
                          onPressed: _loadingReceipt
                              ? null
                              : () => _openReceipt(masterOrder),
                        ),
                      ],
                    ),
                  ),
      ),
    );
  }

  Widget _buildSubOrderDetailCard(
    BuildContext context,
    MasterOrderModel masterOrder,
    SubOrderModel sub,
  ) {
    final isConfirmed = sub.status == SubOrderStatus.confirmed ||
        sub.status == SubOrderStatus.completed;
    final isCompleted = sub.status == SubOrderStatus.completed;
    final statusLabel = switch (sub.rawStatus ?? sub.status.name.toUpperCase()) {
      'CONFIRMED' => 'Đã xác nhận',
      'CHECKED_IN' => 'Đã check-in',
      'IN_PROGRESS' => 'Đang diễn ra',
      'COMPLETED' => 'Đã hoàn thành',
      'CANCELLED' => 'Đã hủy',
      'REJECTED' => 'Bị từ chối',
      'REFUNDED' => 'Đã hoàn tiền',
      'PARTIALLY_REFUNDED' => 'Hoàn tiền một phần',
      _ => isConfirmed ? 'Đã xác nhận' : 'Đang xử lý',
    };

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(AppShapes.spaceSm),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        border: Border.all(color: AppColors.borderSubtle),
        boxShadow: AppShapes.shadowLevel1,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Sub-order header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: LocalizedText(
                        sub.vendorName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.labelSm(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 5,
                          vertical: 1,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: LocalizedText(
                          '#${sub.id}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 10,
                            fontFamily: 'monospace',
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isConfirmed
                      ? AppColors.secondaryContainer
                      : AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: LocalizedText(
                  statusLabel,
                  style: AppTypography.labelSm(
                    color: isConfirmed
                        ? AppColors.onSecondaryContainer
                        : AppColors.onSurfaceVariant,
                    fontWeight: FontWeight.w700,
                  ).copyWith(fontSize: 10),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          LocalizedText(
            sub.serviceName,
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              const Icon(Icons.schedule, size: 14, color: AppColors.tertiary),
              const SizedBox(width: 4),
              Expanded(
                child: LocalizedText(
                  '${sub.slotTime} • ${sub.slotDate.day}/${sub.slotDate.month}/${sub.slotDate.year}',
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 11),
                ),
              ),
              const SizedBox(width: 8),
              LocalizedText(
                _formatPrice(sub.finalAmount),
                style: AppTypography.labelLg(
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const Divider(height: 16),

          // Sub-order Action Buttons Row
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (isConfirmed)
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                  ),
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => ETicketQrScreen(
                          subOrder: sub,
                          allSubOrders: masterOrder.subOrders,
                        ),
                      ),
                    );
                  },
                  icon: const Icon(
                    Icons.qr_code_2,
                    size: 16,
                    color: Colors.white,
                  ),
                  label: const LocalizedText(
                    'Xem vé QR',
                    style: TextStyle(color: Colors.white, fontSize: 11),
                  ),
                ),
              OutlinedButton.icon(
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.borderSubtle),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                ),
                onPressed: () async {
                  await Navigator.push<void>(
                    context,
                    MaterialPageRoute(
                      builder: (_) => CancelBookingScreen(subOrder: sub),
                    ),
                  );
                  if (mounted) _loadDetail();
                },
                icon: const Icon(
                  Icons.cancel_outlined,
                  size: 14,
                  color: AppColors.onSurfaceVariant,
                ),
                label: LocalizedText(
                  'Yêu cầu hủy',
                  style: AppTypography.labelSm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
              OutlinedButton.icon(
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.borderSubtle),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                ),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => CreateDisputeScreen(subOrder: sub),
                    ),
                  );
                },
                icon: const Icon(
                  Icons.report_problem_outlined,
                  size: 14,
                  color: AppColors.onSurfaceVariant,
                ),
                label: LocalizedText(
                  'Khiếu nại',
                  style: AppTypography.labelSm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
              if (isCompleted)
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                  ),
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => ReviewExperienceScreen(subOrder: sub),
                      ),
                    );
                  },
                  icon: const Icon(Icons.star, size: 14, color: Colors.white),
                  label: const LocalizedText(
                    'Đánh giá',
                    style: TextStyle(color: Colors.white, fontSize: 11),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPriceRow(String label, String value, {Color? valueColor}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(
          child: LocalizedText(
            label,
            style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
          ),
        ),
        const SizedBox(width: 8),
        LocalizedText(
          value,
          style: AppTypography.labelMd(
            color: valueColor ?? AppColors.onSurface,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}
