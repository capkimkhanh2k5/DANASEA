import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/conversation_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/data/payment_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../chat/presentation/screens/vendor_chat_screen.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../checkout/presentation/screens/e_ticket_qr_screen.dart';
import '../../../checkout/presentation/screens/payment_result_screen.dart';
import '../../../refund_dispute/presentation/screens/cancel_booking_screen.dart';
import '../../../refund_dispute/presentation/screens/create_dispute_screen.dart';
import '../../../refund_dispute/presentation/screens/refund_tracking_screen.dart';
import '../../../reviews/presentation/screens/review_experience_screen.dart';
import 'package:url_launcher/url_launcher.dart';

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

class _OrderDetailScreenState extends State<OrderDetailScreen> with WidgetsBindingObserver {
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
    WidgetsBinding.instance.addObserver(this);
    _loadDetail();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _loadDetail();
    }
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

  Future<void> _openPaymentDetail(
    MasterOrderModel order,
    OrderPaymentRecord summary,
  ) async {
    if (_openingPayment) return;
    setState(() => _openingPayment = true);
    try {
      final detail = await _paymentRepository.getPaymentDetail(summary.id);
      if (!mounted) return;

      final url = detail.paymentUrl?.trim();
      final isPending = detail.status.toUpperCase() == 'PENDING';
      if (isPending && url != null && (url.startsWith('http://') || url.startsWith('https://'))) {
        try {
          final uri = Uri.parse(url);
          final launched = await launchUrl(
            uri,
            mode: LaunchMode.externalApplication,
          );
          if (launched) return;
        } catch (_) {}
      }

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
                        ScaffoldMessenger.of(context).hideCurrentSnackBar();
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            behavior: SnackBarBehavior.floating,
                            backgroundColor: const Color(0xFF1E293B),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            margin: const EdgeInsets.symmetric(
                              horizontal: 24,
                              vertical: 16,
                            ),
                            duration: const Duration(seconds: 2),
                            content: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(
                                  Icons.check_circle_rounded,
                                  color: Color(0xFF10B981),
                                  size: 20,
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: LocalizedText(
                                    'Đã sao chép mã biên nhận ${receipt.receiptCode}!',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                              ],
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
      backgroundColor: const Color(0xFFF7F9FA),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF7F9FA),
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Column(
          children: [
            const Text(
              'Chi tiết đặt chỗ',
              style: TextStyle(
                color: AppColors.onSurface,
                fontSize: 16.5,
                fontWeight: FontWeight.w700,
              ),
            ),
            Text(
              '#${_targetOrderId.length > 8 ? _targetOrderId.substring(0, 8).toUpperCase() : _targetOrderId}',
              style: const TextStyle(
                color: AppColors.onSurfaceVariant,
                fontSize: 11.5,
                fontFamily: 'monospace',
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.share_outlined, color: AppColors.onSurface),
            onPressed: () {
              Clipboard.setData(ClipboardData(text: _targetOrderId));
              ScaffoldMessenger.of(context).hideCurrentSnackBar();
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  behavior: SnackBarBehavior.floating,
                  backgroundColor: const Color(0xFF1E293B),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  margin: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 16,
                  ),
                  duration: const Duration(seconds: 2),
                  content: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.check_circle_rounded,
                        color: Color(0xFF10B981),
                        size: 20,
                      ),
                      const SizedBox(width: 10),
                      const Expanded(
                        child: LocalizedText(
                          'Đã sao chép mã đơn hàng!',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
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
                        // STATUS HERO BANNER
                        if (masterOrder.status == MasterOrderStatus.cancelled)
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFF8F8),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFFFFCDD2),
                                width: 1,
                              ),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  width: 40,
                                  height: 40,
                                  decoration: const BoxDecoration(
                                    color: Color(0xFFFFEBEE),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.cancel_rounded,
                                    color: Color(0xFFD32F2F),
                                    size: 24,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      const Text(
                                        'Đơn hàng đã hủy',
                                        style: TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w800,
                                          color: Color(0xFFD32F2F),
                                        ),
                                      ),
                                      const SizedBox(height: 3),
                                      Text(
                                        'Đã hủy lúc ${masterOrder.createdAt.hour.toString().padLeft(2, '0')}:${masterOrder.createdAt.minute.toString().padLeft(2, '0')} • ${_formatDate(masterOrder.createdAt)}',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      const Text(
                                        'Đơn đặt chỗ này không còn hiệu lực. Quý khách có thể khám phá thêm các hoạt động khác trên Danasea.',
                                        style: TextStyle(
                                          fontSize: 11.5,
                                          color: Color(0xFF757575),
                                          height: 1.3,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          )
                        else if (masterOrder.status ==
                            MasterOrderStatus.pendingPayment)
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFFDF5),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFFFFE082),
                                width: 1,
                              ),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  width: 40,
                                  height: 40,
                                  decoration: const BoxDecoration(
                                    color: Color(0xFFFFF8E1),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.access_time_rounded,
                                    color: Color(0xFFE65100),
                                    size: 24,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      const Text(
                                        'Đang chờ thanh toán',
                                        style: TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w800,
                                          color: Color(0xFFE65100),
                                        ),
                                      ),
                                      const SizedBox(height: 3),
                                      Text(
                                        'Đặt lúc ${masterOrder.createdAt.hour.toString().padLeft(2, '0')}:${masterOrder.createdAt.minute.toString().padLeft(2, '0')} • ${_formatDate(masterOrder.createdAt)}',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      const Text(
                                        'Vui lòng hoàn tất thanh toán để đảm bảo giữ chỗ trước khi hết hạn dịch vụ.',
                                        style: TextStyle(
                                          fontSize: 11.5,
                                          color: Color(0xFF757575),
                                          height: 1.3,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          )
                        else
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF9FDF9),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFFA5D6A7),
                                width: 1,
                              ),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  width: 40,
                                  height: 40,
                                  decoration: const BoxDecoration(
                                    color: Color(0xFFE8F5E9),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.check_circle_rounded,
                                    color: Color(0xFF2E7D32),
                                    size: 24,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      const Text(
                                        'Đặt chỗ thành công',
                                        style: TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w800,
                                          color: Color(0xFF2E7D32),
                                        ),
                                      ),
                                      const SizedBox(height: 3),
                                      Text(
                                        'Đặt lúc ${masterOrder.createdAt.hour.toString().padLeft(2, '0')}:${masterOrder.createdAt.minute.toString().padLeft(2, '0')} • ${_formatDate(masterOrder.createdAt)}',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      const Text(
                                        'Vé điện tử đã sẵn sàng. Vui lòng xuất trình mã QR tại điểm đón khi tham gia.',
                                        style: TextStyle(
                                          fontSize: 11.5,
                                          color: Color(0xFF757575),
                                          height: 1.3,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        const SizedBox(height: 18),

                        // SUB-ORDERS LIST HEADER
                        Padding(
                          padding: const EdgeInsets.only(left: 2, bottom: 8),
                          child: Text(
                            'Vé trải nghiệm (${masterOrder.subOrders.length})',
                            style: const TextStyle(
                              fontSize: 15.5,
                              fontWeight: FontWeight.w800,
                              color: AppColors.onSurface,
                            ),
                          ),
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
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: const Color(0xFFECEFF1),
                              width: 1,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.03),
                                blurRadius: 10,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Chi tiết thanh toán',
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: AppColors.onSurface,
                                ),
                              ),
                              const SizedBox(height: 14),
                              _buildPriceRow(
                                'Tiền dịch vụ (${masterOrder.subOrders.length} dịch vụ):',
                                _formatPrice(masterOrder.subtotalAmount),
                              ),
                              if (masterOrder.discountAmount > 0) ...[
                                const SizedBox(height: 8),
                                _buildPriceRow(
                                  'Mã giảm giá (${masterOrder.discountCode ?? 'Ưu đãi'}):',
                                  '- ${_formatPrice(masterOrder.discountAmount)}',
                                  valueColor: AppColors.secondary,
                                ),
                              ],
                              const SizedBox(height: 12),
                              const Divider(height: 1, color: Color(0xFFF0F2F5)),
                              const SizedBox(height: 12),
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text(
                                    'Tổng thanh toán:',
                                    style: TextStyle(
                                      fontSize: 14.5,
                                      fontWeight: FontWeight.w700,
                                      color: AppColors.onSurface,
                                    ),
                                  ),
                                  Text(
                                    _formatPrice(masterOrder.totalAmount),
                                    style: const TextStyle(
                                      fontSize: 17,
                                      fontWeight: FontWeight.w800,
                                      color: AppColors.secondary,
                                    ),
                                  ),
                                ],
                              ),
                              if (_payments.isNotEmpty) ...[
                                const SizedBox(height: 12),
                                InkWell(
                                  onTap: _openingPayment
                                      ? null
                                      : () => _openPaymentDetail(
                                            masterOrder,
                                            _payments.first,
                                          ),
                                  borderRadius: BorderRadius.circular(10),
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 8,
                                    ),
                                    decoration: BoxDecoration(
                                      color: const Color(0xFFF8FAFB),
                                      borderRadius: BorderRadius.circular(10),
                                      border: Border.all(
                                        color: const Color(0xFFECEFF1),
                                      ),
                                    ),
                                    child: Row(
                                      children: [
                                        const Icon(
                                          Icons.payment_rounded,
                                          size: 16,
                                          color: AppColors.secondary,
                                        ),
                                        const SizedBox(width: 8),
                                        Text(
                                          'Cổng ${_payments.first.provider.toUpperCase()}',
                                          style: const TextStyle(
                                            fontSize: 12,
                                            fontWeight: FontWeight.w700,
                                            color: AppColors.onSurface,
                                          ),
                                        ),
                                        const Spacer(),
                                        _buildPaymentStatusTag(
                                          _payments.first.status,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ],
                              if (_refunds.isNotEmpty) ...[
                                const SizedBox(height: 12),
                                const Divider(
                                  height: 1,
                                  color: Color(0xFFF0F2F5),
                                ),
                                const SizedBox(height: 10),
                                Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(
                                      'Yêu cầu hoàn tiền (${_refunds.length})',
                                      style: const TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.w700,
                                        color: AppColors.secondary,
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
                                      style: TextButton.styleFrom(
                                        padding: EdgeInsets.zero,
                                        minimumSize: Size.zero,
                                        tapTargetSize:
                                            MaterialTapTargetSize.shrinkWrap,
                                      ),
                                      child: const Text(
                                        'Theo dõi hoàn tiền',
                                        style: TextStyle(fontSize: 12),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6),
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
                        const SizedBox(height: 14),

                        // CUSTOMER INFO
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: const Color(0xFFECEFF1),
                              width: 1,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.03),
                                blurRadius: 10,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Thông tin người đặt',
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: AppColors.onSurface,
                                ),
                              ),
                              const SizedBox(height: 12),
                              if (fullName.isNotEmpty)
                                Row(
                                  children: [
                                    const Icon(
                                      Icons.person_outline_rounded,
                                      size: 17,
                                      color: AppColors.secondary,
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Text(
                                        fullName,
                                        style: const TextStyle(
                                          fontSize: 13.5,
                                          fontWeight: FontWeight.w600,
                                          color: AppColors.onSurface,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              if (email.isNotEmpty) ...[
                                if (fullName.isNotEmpty)
                                  const SizedBox(height: 8),
                                Row(
                                  children: [
                                    const Icon(
                                      Icons.mail_outline_rounded,
                                      size: 17,
                                      color: AppColors.secondary,
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Text(
                                        email,
                                        style: const TextStyle(
                                          fontSize: 13,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                              if (phone.isNotEmpty) ...[
                                const SizedBox(height: 8),
                                Row(
                                  children: [
                                    const Icon(
                                      Icons.phone_outlined,
                                      size: 17,
                                      color: AppColors.secondary,
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Text(
                                        phone,
                                        style: const TextStyle(
                                          fontSize: 13,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                              if (fullName.isEmpty &&
                                  email.isEmpty &&
                                  phone.isEmpty)
                                Text(
                                  'Mã khách hàng: #${masterOrder.customerId}',
                                  style: const TextStyle(
                                    fontSize: 13,
                                    color: AppColors.onSurfaceVariant,
                                  ),
                                ),
                              const SizedBox(height: 10),
                              const Divider(
                                height: 1,
                                color: Color(0xFFF0F2F5),
                              ),
                              const SizedBox(height: 8),
                              Row(
                                children: [
                                  const Icon(
                                    Icons.info_outline_rounded,
                                    size: 13,
                                    color: Color(0xFF9E9E9E),
                                  ),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(
                                      'Vé điện tử và hướng dẫn tập trung được gửi qua thông tin này.',
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: Colors.grey.shade600,
                                      ),
                                    ),
                                  ),
                                ],
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
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
      ),
      bottomNavigationBar: masterOrder != null &&
              masterOrder.status == MasterOrderStatus.pendingPayment
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.08),
                    blurRadius: 10,
                    offset: const Offset(0, -3),
                  ),
                ],
              ),
              child: SafeArea(
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            'Tổng thanh toán:',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 2),
                          LocalizedText(
                            _formatPrice(masterOrder.totalAmount),
                            style: AppTypography.headlineSm(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Container(
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [
                            Color(0xFF006874),
                            Color(0xFF00897B),
                          ],
                        ),
                        borderRadius: BorderRadius.circular(24),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF006874)
                                .withValues(alpha: 0.35),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.transparent,
                          shadowColor: Colors.transparent,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(24),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 20,
                            vertical: 12,
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
                          Icons.payment_rounded,
                          size: 18,
                          color: Colors.white,
                        ),
                        label: const Text(
                          'Thanh toán ngay',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            )
          : null,
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
    final isPendingPayment =
        masterOrder.status == MasterOrderStatus.pendingPayment;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFECEFF1), width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Sub-order header: Vendor Name & Status badge
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Row(
                    children: [
                      const Icon(
                        Icons.storefront_outlined,
                        size: 15,
                        color: AppColors.secondary,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          sub.vendorName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: AppColors.secondary,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                _buildStatusBadge(sub.status, sub.rawStatus),
              ],
            ),
          ),
          const Divider(height: 1, color: Color(0xFFF0F2F5)),

          // Hero Activity Row: Thumbnail on Left + Information on Right
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Thumbnail
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: SizedBox(
                    width: 76,
                    height: 76,
                    child: sub.serviceImageUrl.isNotEmpty
                        ? Image.network(
                            sub.serviceImageUrl,
                            fit: BoxFit.cover,
                            errorBuilder: (_, _, _) => Container(
                              color: const Color(0xFFE0F2F1),
                              child: const Icon(
                                Icons.surfing,
                                size: 28,
                                color: AppColors.secondary,
                              ),
                            ),
                          )
                        : Container(
                            color: const Color(0xFFE0F2F1),
                            child: const Icon(
                              Icons.surfing,
                              size: 28,
                              color: AppColors.secondary,
                            ),
                          ),
                  ),
                ),
                const SizedBox(width: 12),

                // Details
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        sub.serviceName,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w700,
                          color: AppColors.onSurface,
                          height: 1.25,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          const Icon(
                            Icons.access_time_rounded,
                            size: 13,
                            color: AppColors.onSurfaceVariant,
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              '${_cleanSlotTime(sub.slotTime)} • ${_formatDate(sub.slotDate)}',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                          ),
                        ],
                      ),
                      if (sub.locationName.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            const Icon(
                              Icons.location_on_outlined,
                              size: 13,
                              color: AppColors.onSurfaceVariant,
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                sub.locationName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 11.5,
                                  color: AppColors.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                      const SizedBox(height: 6),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            '${sub.quantity} khách',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.onSurfaceVariant,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          Text(
                            _formatPrice(sub.finalAmount),
                            style: const TextStyle(
                              fontSize: 14.5,
                              fontWeight: FontWeight.w800,
                              color: AppColors.primary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Safety / Waiver Confirmation
          if (sub.waiverRequired) ...[
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 14),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: sub.waiverAccepted
                    ? const Color(0xFFF1F8E9)
                    : const Color(0xFFFFF3E0),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    sub.waiverAccepted
                        ? Icons.check_circle_rounded
                        : Icons.warning_amber_rounded,
                    size: 14,
                    color: sub.waiverAccepted
                        ? const Color(0xFF2E7D32)
                        : const Color(0xFFE65100),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      sub.waiverAccepted
                          ? 'Đã hoàn tất cam kết an toàn & miễn trừ trách nhiệm'
                          : 'Cần ký cam kết an toàn thể thao biển',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: sub.waiverAccepted
                            ? const Color(0xFF2E7D32)
                            : const Color(0xFFE65100),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
          ],

          const Divider(height: 1, color: Color(0xFFF0F2F5)),

          // Action Buttons
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
            child: Row(
              children: [
                // Chat with partner
                Expanded(
                  child: OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: Color(0xFFB2DFDB)),
                      backgroundColor: const Color(0xFFF4FBFA),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 9),
                    ),
                    onPressed: () => _openVendorChat(masterOrder, sub),
                    icon: const Icon(
                      Icons.chat_bubble_outline_rounded,
                      size: 15,
                      color: AppColors.secondary,
                    ),
                    label: const Text(
                      'Nhắn đối tác',
                      style: TextStyle(
                        color: AppColors.secondary,
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),

                if (isPendingPayment) ...[
                  if (sub.waiverRequired && !sub.waiverAccepted) ...[
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: Color(0xFFFFB74D)),
                          backgroundColor: const Color(0xFFFFF8E1),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          padding: const EdgeInsets.symmetric(vertical: 9),
                        ),
                        onPressed: () =>
                            _openSubOrderWaiverSheet(masterOrder, sub),
                        icon: const Icon(
                          Icons.fact_check_outlined,
                          size: 15,
                          color: Color(0xFFE65100),
                        ),
                        label: const Text(
                          'Ký cam kết',
                          style: TextStyle(
                            color: Color(0xFFE65100),
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ),
                  ] else ...[
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: Color(0xFFE0E0E0)),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          padding: const EdgeInsets.symmetric(vertical: 9),
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
                          Icons.close_rounded,
                          size: 15,
                          color: Color(0xFF757575),
                        ),
                        label: const Text(
                          'Hủy đặt chỗ',
                          style: TextStyle(
                            color: Color(0xFF616161),
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ] else if (isConfirmed) ...[
                  if (sub.status == SubOrderStatus.confirmed &&
                      (sub.rawStatus == null || sub.rawStatus == 'CONFIRMED')) ...[
                    const SizedBox(width: 8),
                    IconButton(
                      tooltip: 'Đổi lịch',
                      icon: const Icon(
                        Icons.event_repeat_rounded,
                        color: AppColors.secondary,
                        size: 20,
                      ),
                      onPressed: () => _openRescheduleSheet(sub),
                    ),
                  ],
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 9),
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
                        Icons.qr_code_2_rounded,
                        size: 16,
                        color: Colors.white,
                      ),
                      label: const Text(
                        'Xem vé QR',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ] else if (isCompleted) ...[
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.secondary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 9),
                      ),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                ReviewExperienceScreen(subOrder: sub),
                          ),
                        );
                      },
                      icon: const Icon(
                        Icons.star_rounded,
                        size: 15,
                        color: Colors.white,
                      ),
                      label: const Text(
                        'Đánh giá',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ] else ...[
                  // Cancelled or other state
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: Color(0xFFE0E0E0)),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 9),
                      ),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                CreateDisputeScreen(subOrder: sub),
                          ),
                        );
                      },
                      icon: const Icon(
                        Icons.report_problem_outlined,
                        size: 15,
                        color: Color(0xFF757575),
                      ),
                      label: const Text(
                        'Khiếu nại',
                        style: TextStyle(
                          color: Color(0xFF616161),
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _openVendorChat(
    MasterOrderModel masterOrder,
    SubOrderModel sub,
  ) async {
    try {
      final conv = await ConversationRepository.instance
          .createOrGetConversation(
            masterOrderId: masterOrder.id,
            vendorId: sub.vendorId.isNotEmpty ? sub.vendorId : null,
            fallbackVendorName: sub.vendorName,
            fallbackServiceName: sub.serviceName,
          );
      if (!mounted) return;
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => VendorChatScreen(conversation: conv),
        ),
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
              'Không thể mở hội thoại với đối tác. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  Future<void> _openSubOrderWaiverSheet(
    MasterOrderModel masterOrder,
    SubOrderModel sub,
  ) async {
    final canSign =
        masterOrder.status == MasterOrderStatus.pendingPayment &&
        sub.status == SubOrderStatus.pending &&
        !sub.waiverAccepted;
    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(AppShapes.gutterMobile),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        'Cam kết miễn trừ trách nhiệm (v${sub.waiverVersion ?? 1})',
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(sheetContext, false),
                    ),
                  ],
                ),
                Text(
                  sub.serviceName,
                  style: AppTypography.labelMd(
                    color: AppColors.secondary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Container(
                  constraints: const BoxConstraints(maxHeight: 260),
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  child: SingleChildScrollView(
                    child: Text(
                      (sub.waiverContent != null &&
                              sub.waiverContent!.trim().isNotEmpty)
                          ? sub.waiverContent!
                          : tr(
                              context,
                              'Tôi xác nhận đủ điều kiện sức khỏe và đồng ý với Cam kết miễn trừ trách nhiệm khi tham gia các hoạt động thể thao biển tại Đà Nẵng.',
                            ),
                      style: AppTypography.bodySm(color: AppColors.onSurface),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                if (sub.waiverAccepted)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: LocalizedText(
                      'Đã ký cam kết miễn trừ trách nhiệm (${sub.waiverAcceptedLanguage ?? 'VI'}).',
                      style: AppTypography.labelMd(
                        color: AppColors.onSecondaryContainer,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  )
                else if (canSign)
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                        minimumSize: const Size(double.infinity, 44),
                      ),
                      onPressed: () => Navigator.pop(sheetContext, true),
                      icon: const Icon(
                        Icons.verified_user,
                        size: 18,
                        color: Colors.white,
                      ),
                      label: const LocalizedText(
                        'Đồng ý & Ký cam kết miễn trừ',
                        style: TextStyle(color: Colors.white),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
    if (confirmed != true || !mounted) return;
    try {
      final langCode =
          Localizations.localeOf(context).languageCode.toUpperCase() == 'EN'
              ? 'EN'
              : 'VI';
      await _repository.acceptSubOrderWaiver(
        subOrderId: sub.id,
        version: sub.waiverVersion ?? 1,
        language: langCode,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã xác nhận cam kết miễn trừ trách nhiệm!'),
          backgroundColor: AppColors.secondary,
        ),
      );
      _loadDetail();
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
              'Không thể ghi nhận cam kết miễn trừ. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  Future<void> _openRescheduleSheet(SubOrderModel sub) async {
    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _SubOrderRescheduleSheet(
        subOrder: sub,
        repository: _repository,
      ),
    );
    if (changed == true && mounted) {
      _loadDetail();
    }
  }

  String _cleanSlotTime(String raw) {
    if (raw.isEmpty) return raw;
    return raw.replaceAllMapped(
      RegExp(r'(\d{1,2}:\d{2}):00'),
      (match) => match.group(1) ?? '',
    );
  }

  String _formatDate(DateTime dt) {
    return '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')}/${dt.year}';
  }

  Widget _buildStatusBadge(SubOrderStatus status, String? rawStatus) {
    final label = switch (rawStatus ?? status.name.toUpperCase()) {
      'CONFIRMED' => 'Đã xác nhận',
      'CHECKED_IN' => 'Đã check-in',
      'IN_PROGRESS' => 'Đang diễn ra',
      'COMPLETED' => 'Hoàn thành',
      'CANCELLED' => 'Đã hủy',
      'REJECTED' => 'Bị từ chối',
      'REFUNDED' => 'Đã hoàn tiền',
      'PARTIALLY_REFUNDED' => 'Hoàn tiền',
      _ => status == SubOrderStatus.confirmed ? 'Đã xác nhận' : 'Đang xử lý',
    };

    final isCancelled =
        status == SubOrderStatus.cancelled || rawStatus == 'CANCELLED';
    final isConfirmed = status == SubOrderStatus.confirmed ||
        status == SubOrderStatus.completed;

    final bgColor = isCancelled
        ? const Color(0xFFFFEBEE)
        : isConfirmed
            ? const Color(0xFFE0F2F1)
            : const Color(0xFFFFF3E0);

    final textColor = isCancelled
        ? const Color(0xFFC62828)
        : isConfirmed
            ? const Color(0xFF00695C)
            : const Color(0xFFE65100);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: textColor,
          fontWeight: FontWeight.w700,
          fontSize: 10.5,
        ),
      ),
    );
  }

  Widget _buildPaymentStatusTag(String status) {
    final upper = status.toUpperCase();
    final isSuccess = upper == 'PAID' || upper == 'SUCCESS';
    final isPending = upper == 'PENDING';

    final bgColor = isSuccess
        ? const Color(0xFFE8F5E9)
        : isPending
            ? const Color(0xFFFFF3E0)
            : const Color(0xFFFFEBEE);

    final textColor = isSuccess
        ? const Color(0xFF2E7D32)
        : isPending
            ? const Color(0xFFE65100)
            : const Color(0xFFC62828);

    final text = isSuccess
        ? 'Thành công'
        : isPending
            ? 'Đang chờ'
            : 'Thất bại';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2.5),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: textColor,
          fontWeight: FontWeight.w700,
          fontSize: 10.5,
        ),
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

class _SubOrderRescheduleSheet extends StatefulWidget {
  final SubOrderModel subOrder;
  final OrderRepository repository;

  const _SubOrderRescheduleSheet({
    required this.subOrder,
    required this.repository,
  });

  @override
  State<_SubOrderRescheduleSheet> createState() =>
      _SubOrderRescheduleSheetState();
}

class _SubOrderRescheduleSheetState extends State<_SubOrderRescheduleSheet> {
  RescheduleOptionsSummary? _summary;
  RescheduleOptionItem? _selectedOption;
  bool _loading = true;
  bool _submitting = false;
  bool _uncertainReschedule = false;
  String? _idempotencyKey;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadOptions();
  }

  Future<void> _loadOptions() async {
    setState(() {
      _loading = true;
      _error = null;
      _uncertainReschedule = false;
    });
    try {
      final summary = await widget.repository.getRescheduleOptions(
        widget.subOrder.id,
      );
      if (!mounted) return;
      RescheduleOptionItem? defaultSelected;
      for (final opt in summary.options) {
        if (opt.canAccommodate && opt.isProposedByVendor) {
          defaultSelected = opt;
          break;
        }
      }
      if (defaultSelected == null) {
        for (final opt in summary.options) {
          if (opt.canAccommodate) {
            defaultSelected = opt;
            break;
          }
        }
      }
      setState(() {
        _summary = summary;
        _selectedOption = defaultSelected;
      });
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Không thể tải danh sách ca thay thế khả dụng.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submitReschedule() async {
    final summary = _summary;
    final selected = _selectedOption;
    if (_submitting || summary == null || selected == null) return;
    if (_uncertainReschedule) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Chưa xác định được kết quả đổi lịch trước đó. Vui lòng bấm làm mới trước khi thử lại.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    final key = _idempotencyKey ??= OrderRepository.generateIdempotencyKey(
      'rsch',
    );
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final result = await widget.repository.confirmReschedule(
        subOrderId: widget.subOrder.id,
        targetSlotId: selected.slotId,
        proposalId: selected.proposalId,
        expectedVersion: summary.rescheduleVersion,
        idempotencyKey: key,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            result.message.isNotEmpty
                ? result.message
                : 'Đã chuyển sang ca ${result.newBookingTime} ngày ${result.newBookingDate}!',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      Navigator.pop(context, true);
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        if (e.status == 0 || e.status >= 500) {
          _uncertainReschedule = true;
        }
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error =
            'Chưa xác định được kết quả đổi lịch. Vui lòng làm mới trạng thái trước khi thử lại.';
        _uncertainReschedule = true;
      });
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _submitDisruptionRefund() async {
    if (_submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final res = await widget.repository.chooseDisruptionRefund(
        widget.subOrder.id,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            'Đã ghi nhận hoàn tiền 100% do gián đoạn (${res.status}).',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      Navigator.pop(context, true);
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Không thể gửi yêu cầu hoàn tiền gián đoạn. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final summary = _summary;
    final deadline = summary?.decisionDeadline;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: LocalizedText(
                    'Đổi lịch & Xử lý gián đoạn',
                    style: AppTypography.headlineSm(color: AppColors.onSurface),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.refresh, color: AppColors.secondary),
                  onPressed: (_loading || _submitting) ? null : _loadOptions,
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: _submitting
                      ? null
                      : () => Navigator.pop(context, false),
                ),
              ],
            ),
            Text(
              widget.subOrder.serviceName,
              style: AppTypography.labelMd(
                color: AppColors.secondary,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 32),
                child: Center(child: CircularProgressIndicator()),
              )
            else ...[
              if (_error != null) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: LocalizedText(
                    _error!,
                    style: AppTypography.bodySm(color: AppColors.error),
                  ),
                ),
                const SizedBox(height: 10),
              ],
              if (summary != null) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText(
                        'Ca hiện tại: ${summary.currentBookingTime} • ${summary.currentBookingDate}',
                        style: AppTypography.labelMd(
                          color: AppColors.onSurface,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 4),
                      LocalizedText(
                        summary.rescheduleAllowed
                            ? 'Trạng thái: Đủ điều kiện đổi lịch (${summary.eligibilityReason ?? 'AVAILABLE'})'
                            : 'Chưa có cảnh báo thời tiết hoặc đề xuất đổi lịch từ đối tác.',
                        style: AppTypography.bodySm(
                          color: summary.rescheduleAllowed
                              ? AppColors.secondary
                              : AppColors.onSurfaceVariant,
                        ),
                      ),
                      if (deadline != null) ...[
                        const SizedBox(height: 4),
                        LocalizedText(
                          'Hạn xác nhận quyết định: ${deadline.day}/${deadline.month}/${deadline.year} ${deadline.hour.toString().padLeft(2, '0')}:${deadline.minute.toString().padLeft(2, '0')}',
                          style: AppTypography.bodySm(
                            color: AppColors.primary,
                          ).copyWith(fontSize: 11),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                LocalizedText(
                  'Ca thay thế khả dụng (${summary.options.length})',
                  style: AppTypography.labelLg(color: AppColors.onSurface),
                ),
                const SizedBox(height: 8),
                if (summary.options.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Center(
                      child: LocalizedText(
                        'Hiện chưa có ca thay thế khả dụng trong 14 ngày tới.',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  )
                else
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxHeight: 240),
                    child: ListView.builder(
                      shrinkWrap: true,
                      itemCount: summary.options.length,
                      itemBuilder: (context, index) {
                        final opt = summary.options[index];
                        final isSelected = _selectedOption?.slotId == opt.slotId;
                        return InkWell(
                          onTap: (!opt.canAccommodate || _submitting)
                              ? null
                              : () {
                                  setState(() {
                                    _selectedOption = opt;
                                    _idempotencyKey = null;
                                  });
                                },
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            margin: const EdgeInsets.only(bottom: 8),
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? AppColors.secondaryContainer.withValues(
                                      alpha: 0.35,
                                    )
                                  : AppColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(
                                color: isSelected
                                    ? AppColors.secondary
                                    : AppColors.borderSubtle,
                                width: isSelected ? 1.5 : 1,
                              ),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  isSelected
                                      ? Icons.radio_button_checked
                                      : Icons.radio_button_off,
                                  size: 18,
                                  color: opt.canAccommodate
                                      ? AppColors.secondary
                                      : AppColors.outline,
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Expanded(
                                            child: Text(
                                              '${opt.bookingDate} • ${opt.startTime} - ${opt.endTime}',
                                              style: AppTypography.labelMd(
                                                color: opt.canAccommodate
                                                    ? AppColors.onSurface
                                                    : AppColors.outline,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                          ),
                                          if (opt.isProposedByVendor)
                                            Container(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: 6,
                                                    vertical: 2,
                                                  ),
                                              decoration: BoxDecoration(
                                                color:
                                                    AppColors.secondaryContainer,
                                                borderRadius:
                                                    BorderRadius.circular(8),
                                              ),
                                              child: LocalizedText(
                                                'Đề xuất từ đối tác',
                                                style: AppTypography.labelSm(
                                                  color: AppColors
                                                      .onSecondaryContainer,
                                                ).copyWith(fontSize: 9),
                                              ),
                                            ),
                                        ],
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        '${tr(context, 'Còn chỗ')}: ${opt.availableCapacity} • ${opt.accommodateNote}',
                                        style: AppTypography.bodySm(
                                          color: AppColors.onSurfaceVariant,
                                        ).copyWith(fontSize: 11),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                const SizedBox(height: 12),
                if (summary.rescheduleAllowed && _selectedOption != null)
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                        minimumSize: const Size(double.infinity, 44),
                      ),
                      onPressed: _submitting ? null : _submitReschedule,
                      icon: const Icon(
                        Icons.event_available,
                        size: 18,
                        color: Colors.white,
                      ),
                      label: LocalizedText(
                        _submitting
                            ? 'Đang xử lý...'
                            : 'Xác nhận đổi sang ca đã chọn',
                        style: const TextStyle(color: Colors.white),
                      ),
                    ),
                  ),
                if (summary.fullRefundAvailable) ...[
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: AppColors.error),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                        ),
                        minimumSize: const Size(double.infinity, 44),
                      ),
                      onPressed: _submitting ? null : _submitDisruptionRefund,
                      icon: const Icon(
                        Icons.currency_exchange,
                        size: 16,
                        color: AppColors.error,
                      ),
                      label: LocalizedText(
                        'Chọn hoàn tiền 100% do gián đoạn (${summary.disruptionReason ?? 'WEATHER'})',
                        style: AppTypography.labelMd(color: AppColors.error),
                      ),
                    ),
                  ),
                ],
              ],
            ],
          ],
        ),
      ),
    );
  }
}
