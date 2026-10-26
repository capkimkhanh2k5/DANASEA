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
import '../../../../shared/widgets/app_pill_button.dart';
import '../../presentation/screens/e_ticket_qr_screen.dart';
import '../../../orders/presentation/screens/order_detail_screen.dart';

class PaymentResultScreen extends StatefulWidget {
  final MasterOrderModel masterOrder;
  final PaymentProvider provider;
  final bool isSuccess;
  final PaymentIntentModel? paymentIntent;
  final OrderPaymentRecord? initialPayment;
  final String? paymentId;

  const PaymentResultScreen({
    super.key,
    required this.masterOrder,
    required this.provider,
    this.isSuccess = false,
    this.paymentIntent,
    this.initialPayment,
    this.paymentId,
  });

  @override
  State<PaymentResultScreen> createState() => _PaymentResultScreenState();
}

class _PaymentResultScreenState extends State<PaymentResultScreen> {
  final PaymentRepository _paymentRepository = PaymentRepository();
  final OrderRepository _orderRepository = OrderRepository();
  late final TextEditingController _paypalOrderController;

  late MasterOrderModel _order;
  OrderPaymentRecord? _payment;
  List<OrderPaymentRecord> _orderPayments = const [];
  bool _loadingStatus = false;
  bool _capturingPayPal = false;
  bool _captureUncertain = false;
  bool _downloadingReceipt = false;
  String? _error;
  late String _captureIdempotencyKey;

  String? get _effectivePaymentId =>
      _payment?.id ??
      widget.paymentId ??
      widget.initialPayment?.id ??
      widget.paymentIntent?.paymentId;

  String get _effectiveStatus {
    final raw = _payment?.status ?? widget.paymentIntent?.status;
    if (raw != null && raw.isNotEmpty) {
      return raw.toUpperCase();
    }
    if (_order.status == MasterOrderStatus.paid ||
        _order.status == MasterOrderStatus.partiallyCompleted ||
        _order.status == MasterOrderStatus.completed) {
      return 'SUCCESS';
    }
    return widget.isSuccess ? 'SUCCESS' : 'PENDING';
  }

  bool get _isConfirmedSuccess =>
      _effectiveStatus == 'SUCCESS' ||
      _order.status == MasterOrderStatus.paid ||
      _order.status == MasterOrderStatus.partiallyCompleted ||
      _order.status == MasterOrderStatus.completed;

  bool get _isPending => !_isConfirmedSuccess && _effectiveStatus == 'PENDING';

  PaymentProvider get _effectiveProvider {
    final raw = _payment?.provider ?? widget.paymentIntent?.provider;
    if (raw != null && raw.isNotEmpty) {
      return PaymentRepository.providerFromBackend(raw);
    }
    return widget.provider;
  }

  String? get _paymentUrl {
    final url = _payment?.paymentUrl?.trim();
    if (url != null && url.isNotEmpty) return url;
    final ref = widget.paymentIntent?.paymentReference.trim();
    if (ref != null && ref.isNotEmpty) return ref;
    return null;
  }

  @override
  void initState() {
    super.initState();
    _order = widget.masterOrder;
    _payment = widget.initialPayment;
    _captureIdempotencyKey = PaymentRepository.generateIdempotencyKey('cap');
    _paypalOrderController = TextEditingController(
      text: widget.initialPayment?.providerOrderId ?? '',
    );
    _refreshPaymentState();
  }

  @override
  void dispose() {
    _paypalOrderController.dispose();
    super.dispose();
  }

  Future<void> _refreshPaymentState() async {
    if (_loadingStatus) return;
    setState(() {
      _loadingStatus = true;
      _error = null;
    });
    try {
      final pid = _effectivePaymentId;
      OrderPaymentRecord? detail;
      if (pid != null && pid.isNotEmpty) {
        detail = await _paymentRepository.getPaymentDetail(pid);
      }
      final results = await Future.wait<dynamic>([
        _paymentRepository.getOrderStatus(_order.id),
        _orderRepository.detail(_order.id),
      ]);
      if (!mounted) return;
      final statusList = results[0] as List<OrderPaymentRecord>;
      final refreshedOrder = results[1] as MasterOrderModel;
      final resolvedPayment =
          detail ?? (statusList.isNotEmpty ? statusList.first : _payment);
      setState(() {
        _order = refreshedOrder;
        _orderPayments = statusList;
        _payment = resolvedPayment;
        if (_paypalOrderController.text.trim().isEmpty &&
            resolvedPayment?.providerOrderId != null &&
            resolvedPayment!.providerOrderId!.isNotEmpty) {
          _paypalOrderController.text = resolvedPayment.providerOrderId!;
        }
      });
    } on AuthFailure catch (e) {
      if (mounted) {
        setState(() => _error = e.message);
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Không tải được trạng thái thanh toán. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingStatus = false);
      }
    }
  }

  Future<void> _capturePayPal() async {
    if (_capturingPayPal || _captureUncertain) return;
    final pid = _effectivePaymentId;
    final paypalOrderId = _paypalOrderController.text.trim();
    if (pid == null || pid.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Không tìm thấy mã giao dịch thanh toán.'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }
    if (paypalOrderId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng nhập mã đơn PayPal (PayPal Order ID).'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() {
      _capturingPayPal = true;
      _error = null;
    });
    try {
      final captured = await _paymentRepository.capturePayPalOrder(
        paymentId: pid,
        paypalOrderId: paypalOrderId,
        idempotencyKey: _captureIdempotencyKey,
      );
      final refreshedOrder = await _orderRepository.detail(_order.id);
      if (!mounted) return;
      setState(() {
        _payment = captured;
        _order = refreshedOrder;
      });
      if (captured.status.toUpperCase() == 'SUCCESS') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText('Xác nhận thanh toán PayPal thành công!'),
            backgroundColor: AppColors.secondary,
          ),
        );
      }
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        if (e.status == 0 || e.status >= 500) {
          _captureUncertain = true;
        }
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error =
            'Chưa xác định được kết quả capture PayPal. Vui lòng kiểm tra trạng thái thanh toán trước khi thử lại.';
        _captureUncertain = true;
      });
    } finally {
      if (mounted) {
        setState(() => _capturingPayPal = false);
      }
    }
  }

  Future<void> _downloadReceiptPdf() async {
    if (_downloadingReceipt) return;
    setState(() => _downloadingReceipt = true);
    try {
      final receipt = await _orderRepository.receipt(_order.id);
      final pdfBytes = await _orderRepository.receiptPdf(_order.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            'Đã tải biên nhận PDF (${(pdfBytes.lengthInBytes / 1024).toStringAsFixed(1)} KB) cho biên nhận #${receipt.receiptCode}.',
          ),
          backgroundColor: AppColors.secondary,
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
            content: LocalizedText('Không tải được biên nhận PDF.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _downloadingReceipt = false);
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

  String _getProviderName(PaymentProvider p) {
    switch (p) {
      case PaymentProvider.vnpay:
        return 'VNPAY Cổng thanh toán';
      case PaymentProvider.momo:
        return 'Ví MoMo';
      case PaymentProvider.sepay:
        return 'VietQR (SePay)';
      case PaymentProvider.paypal:
        return 'PayPal Quốc tế';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isSuccess = _isConfirmedSuccess;
    final isPending = _isPending;
    final email =
        (AuthSession.instance.profile?['email']?.toString() ?? '').trim();
    final createdAt =
        (_payment?.createdAt ??
                widget.paymentIntent?.createdAt ??
                _order.createdAt)
            .toLocal();
    final expiresAt = _payment?.expiresAt?.toLocal();
    final amount =
        _payment?.amount ?? widget.paymentIntent?.amount ?? _order.totalAmount;
    final txnDisplay =
        (_payment?.providerTransactionId?.isNotEmpty == true)
        ? _payment!.providerTransactionId!
        : (_payment?.providerOrderId?.isNotEmpty == true)
        ? _payment!.providerOrderId!
        : (_effectivePaymentId ?? 'Đang chờ xác nhận');

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: LocalizedText(
          'Kết quả thanh toán',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: tr(context, 'Làm mới'),
            onPressed: _loadingStatus ? null : _refreshPaymentState,
            icon: _loadingStatus
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh, color: AppColors.onSurface),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          children: [
            const SizedBox(height: 12),

            // SUCCESS / PENDING / FAILED ICON & STATUS
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: isSuccess
                    ? AppColors.secondaryContainer
                    : isPending
                    ? AppColors.primaryFixed
                    : AppColors.errorContainer,
                shape: BoxShape.circle,
              ),
              child: Icon(
                isSuccess
                    ? Icons.check_circle
                    : isPending
                    ? Icons.schedule
                    : Icons.error,
                size: 52,
                color: isSuccess
                    ? AppColors.onSecondaryContainer
                    : isPending
                    ? AppColors.primary
                    : AppColors.error,
              ),
            ),
            const SizedBox(height: 16),
            LocalizedText(
              isSuccess
                  ? 'Thanh toán thành công!'
                  : isPending
                  ? 'Đang chờ xác nhận thanh toán'
                  : 'Thanh toán chưa hoàn tất',
              style: AppTypography.headlineLgMobile(
                color: isSuccess
                    ? AppColors.secondary
                    : isPending
                    ? AppColors.primary
                    : AppColors.error,
              ),
            ),
            const SizedBox(height: 6),
            LocalizedText(
              isSuccess
                  ? 'Đơn đặt chỗ trải nghiệm biển của bạn đã được xác nhận.'
                  : isPending
                  ? 'Yêu cầu thanh toán đã được khởi tạo trên máy chủ. Vui lòng hoàn tất trên cổng thanh toán và kiểm tra lại trạng thái.'
                  : 'Giao dịch bị gián đoạn, vui lòng kiểm tra lại phương thức thanh toán.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(AppShapes.spaceSm),
                decoration: BoxDecoration(
                  color: AppColors.errorContainer,
                  borderRadius: AppShapes.radiusDefault,
                ),
                child: LocalizedText(
                  _error!,
                  style: AppTypography.bodySm(color: AppColors.error),
                ),
              ),
            ],
            const SizedBox(height: 24),

            // TRANSACTION DETAILS CARD
            Container(
              padding: const EdgeInsets.all(AppShapes.spaceMd),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusDefault,
                boxShadow: AppShapes.shadowLevel1,
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                children: [
                  _buildDetailRow('Mã đơn hàng:', '#${_order.id}'),
                  if (_effectivePaymentId != null) ...[
                    const Divider(height: 16),
                    _buildDetailRow('Mã thanh toán:', '#$_effectivePaymentId'),
                  ],
                  const Divider(height: 16),
                  _buildDetailRow(
                    'Cổng thanh toán:',
                    _getProviderName(_effectiveProvider),
                  ),
                  const Divider(height: 16),
                  _buildDetailRow('Trạng thái thanh toán:', _effectiveStatus),
                  const Divider(height: 16),
                  _buildDetailRow('Mã giao dịch:', txnDisplay),
                  const Divider(height: 16),
                  _buildDetailRow(
                    'Thời gian:',
                    '${createdAt.hour.toString().padLeft(2, '0')}:${createdAt.minute.toString().padLeft(2, '0')} • ${createdAt.day}/${createdAt.month}/${createdAt.year}',
                  ),
                  if (expiresAt != null && isPending) ...[
                    const Divider(height: 16),
                    _buildDetailRow(
                      'Hạn thanh toán:',
                      '${expiresAt.hour.toString().padLeft(2, '0')}:${expiresAt.minute.toString().padLeft(2, '0')} • ${expiresAt.day}/${expiresAt.month}/${expiresAt.year}',
                    ),
                  ],
                  const Divider(height: 16),
                  _buildDetailRow(
                    'Số lượng dịch vụ:',
                    '${_order.subOrders.length} dịch vụ',
                  ),
                  if (_orderPayments.length > 1) ...[
                    const Divider(height: 16),
                    _buildDetailRow(
                      'Số lần giao dịch:',
                      '${_orderPayments.length} giao dịch',
                    ),
                  ],
                  if (_payment?.lastError != null &&
                      _payment!.lastError!.isNotEmpty) ...[
                    const Divider(height: 16),
                    _buildDetailRow('Ghi chú hệ thống:', _payment!.lastError!),
                  ],
                  const Divider(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          isSuccess
                              ? 'Tổng tiền đã thanh toán:'
                              : 'Tổng tiền cần thanh toán:',
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      LocalizedText(
                        _formatPrice(amount),
                        style: AppTypography.headlineSm(
                          color: AppColors.primary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // PAYMENT REDIRECT URL / REFERENCE BOX (WHEN PENDING)
            if (isPending && _paymentUrl != null && _paymentUrl!.isNotEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(AppShapes.spaceSm),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusDefault,
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(
                          Icons.link,
                          size: 18,
                          color: AppColors.secondary,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            'Liên kết thanh toán từ cổng ${_getProviderName(_effectiveProvider)}',
                            style: AppTypography.labelMd(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    SelectableText(
                      _paymentUrl!,
                      maxLines: 3,
                      style: AppTypography.bodySm(
                        color: AppColors.onSurfaceVariant,
                      ).copyWith(fontSize: 11),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () {
                          Clipboard.setData(ClipboardData(text: _paymentUrl!));
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: LocalizedText(
                                'Đã sao chép liên kết thanh toán!',
                              ),
                              backgroundColor: AppColors.secondary,
                            ),
                          );
                        },
                        icon: const Icon(Icons.copy, size: 16),
                        label: const LocalizedText(
                          'Sao chép liên kết thanh toán',
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // PAYPAL CAPTURE BOX (WHEN PAYPAL & PENDING)
            if (isPending && _effectiveProvider == PaymentProvider.paypal) ...[
              Container(
                width: double.infinity,
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
                      'Xác nhận đơn PayPal (Capture)',
                      style: AppTypography.labelLg(color: AppColors.onSurface),
                    ),
                    const SizedBox(height: 4),
                    LocalizedText(
                      'Sau khi chấp thuận thanh toán trên PayPal, nhập hoặc xác nhận mã PayPal Order ID để hoàn tất thu tiền.',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurfaceVariant,
                      ).copyWith(fontSize: 11),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _paypalOrderController,
                      enabled: !_capturingPayPal && !_captureUncertain,
                      decoration: InputDecoration(
                        labelText: tr(context, 'Mã đơn PayPal (PayPal Order ID)'),
                        hintText: 'PAYPAL-ORDER-ID',
                        isDense: true,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                    ),
                    if (_captureUncertain) ...[
                      const SizedBox(height: 8),
                      LocalizedText(
                        'Chưa xác định được kết quả capture PayPal. Vui lòng bấm Kiểm tra trạng thái thanh toán trước khi gửi lại.',
                        style: AppTypography.bodySm(color: AppColors.error),
                      ),
                    ],
                    const SizedBox(height: 10),
                    AppPillButton(
                      label: _capturingPayPal
                          ? 'Đang xác nhận PayPal...'
                          : 'Xác nhận thanh toán PayPal',
                      variant: AppButtonVariant.primary,
                      width: double.infinity,
                      isLoading: _capturingPayPal,
                      onPressed: (_capturingPayPal || _captureUncertain)
                          ? null
                          : _capturePayPal,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // INSTRUCTION BOX
            if (isSuccess)
              Container(
                padding: const EdgeInsets.all(AppShapes.spaceSm),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: AppShapes.radiusDefault,
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.mark_email_read_outlined,
                      size: 24,
                      color: AppColors.secondary,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: LocalizedText(
                        email.isNotEmpty
                            ? 'Vé điện tử QR và thông tin chi tiết đã được gửi đến email $email'
                            : 'Vé điện tử QR và thông tin chi tiết đã được cập nhật trong đơn hàng của bạn.',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurface,
                        ).copyWith(fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 20),

            // ACTION BUTTONS
            if (!isSuccess) ...[
              AppPillButton(
                label: _loadingStatus
                    ? 'Đang kiểm tra trạng thái...'
                    : 'Kiểm tra trạng thái thanh toán',
                variant: AppButtonVariant.primary,
                leadingIcon: Icons.sync,
                width: double.infinity,
                isLoading: _loadingStatus,
                onPressed: _loadingStatus ? null : _refreshPaymentState,
              ),
              const SizedBox(height: 12),
            ],
            if (isSuccess && _order.subOrders.isNotEmpty) ...[
              AppPillButton(
                label: 'Xem vé QR check-in',
                variant: AppButtonVariant.primary,
                leadingIcon: Icons.qr_code_2,
                width: double.infinity,
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ETicketQrScreen(
                        subOrder: _order.subOrders.first,
                        allSubOrders: _order.subOrders,
                      ),
                    ),
                  );
                },
              ),
              const SizedBox(height: 12),
            ],
            if (isSuccess) ...[
              AppPillButton(
                label: _downloadingReceipt
                    ? 'Đang tải biên nhận...'
                    : 'Tải hóa đơn điện tử (PDF)',
                variant: AppButtonVariant.secondary,
                leadingIcon: Icons.receipt_long,
                width: double.infinity,
                isLoading: _downloadingReceipt,
                onPressed: _downloadingReceipt ? null : _downloadReceiptPdf,
              ),
              const SizedBox(height: 12),
            ],
            AppPillButton(
              label: 'Xem chi tiết đơn hàng',
              variant: AppButtonVariant.secondary,
              leadingIcon: Icons.receipt_outlined,
              width: double.infinity,
              onPressed: () {
                Navigator.pushReplacement(
                  context,
                  MaterialPageRoute(
                    builder: (_) => OrderDetailScreen(masterOrder: _order),
                  ),
                );
              },
            ),
            const SizedBox(height: 12),
            TextButton.icon(
              onPressed: () {
                Navigator.of(context).popUntil((route) => route.isFirst);
              },
              icon: const Icon(Icons.arrow_back, size: 16),
              label: const LocalizedText('Về trang Khám phá'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        LocalizedText(
          label,
          style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: LocalizedText(
            value,
            textAlign: TextAlign.right,
            style: AppTypography.labelMd(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }
}
