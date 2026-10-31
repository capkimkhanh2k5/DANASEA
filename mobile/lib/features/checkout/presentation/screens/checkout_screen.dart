import 'dart:async';
import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/data/payment_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../cart/presentation/screens/cart_screen.dart';
import 'payment_result_screen.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

class CheckoutScreen extends StatefulWidget {
  final List<CartItemData> selectedItems;
  final MasterOrderModel? masterOrder;
  final String? orderId;
  final String? bookingId;

  const CheckoutScreen({
    super.key,
    this.selectedItems = const [],
    this.masterOrder,
    this.orderId,
    this.bookingId,
  });

  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  final OrderRepository _orderRepository = OrderRepository();
  final PaymentRepository _paymentRepository = PaymentRepository();

  PaymentProvider _selectedProvider = PaymentProvider.vnpay;
  bool _waiverAccepted = false;
  final TextEditingController _discountController = TextEditingController();

  MasterOrderModel? _order;
  DiscountPreviewResult? _discountPreview;
  List<OrderPaymentRecord> _existingPayments = const [];
  bool _loadingOrder = false;
  bool _previewingDiscount = false;
  bool _acceptingWaiver = false;
  bool _submitting = false;
  final Set<PaymentProvider> _uncertainProviders = <PaymentProvider>{};
  final Map<PaymentProvider, String> _idempotencyKeys =
      <PaymentProvider, String>{};
  String? _error;
  Timer? _countdownTimer;
  Duration _holdRemaining = const Duration(minutes: 14, seconds: 55);

  String? get _targetOrderId => widget.orderId ?? widget.masterOrder?.id;
  String? get _targetBookingId => widget.bookingId ?? _order?.bookingId;

  @override
  void initState() {
    super.initState();
    _order = widget.masterOrder;
    if (_order != null) {
      _waiverAccepted = _order!.subOrders.every(
        (s) => !s.waiverRequired || s.waiverAccepted,
      );
    }
    _initHoldCountdown();
    if (_targetOrderId != null && _targetOrderId!.isNotEmpty) {
      _loadOrderAndPaymentStatus();
    }
  }

  void _initHoldCountdown() {
    _updateHoldCountdown();
    _countdownTimer?.cancel();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (_holdRemaining.inSeconds > 0) {
        setState(() {
          _holdRemaining = _holdRemaining - const Duration(seconds: 1);
        });
      } else {
        _countdownTimer?.cancel();
      }
    });
  }

  void _updateHoldCountdown() {
    if (_order != null) {
      final deadline = _order!.createdAt.add(const Duration(minutes: 15));
      final diff = deadline.difference(DateTime.now());
      setState(() {
        _holdRemaining = diff.isNegative ? Duration.zero : diff;
      });
    } else {
      setState(() {
        _holdRemaining = const Duration(minutes: 14, seconds: 55);
      });
    }
  }

  String _formatCountdown(Duration d) {
    if (d.isNegative || d.inSeconds <= 0) return '00:00';
    final minutes = d.inMinutes;
    final seconds = d.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _discountController.dispose();
    super.dispose();
  }

  Future<void> _loadOrderAndPaymentStatus() async {
    final targetId = _targetOrderId;
    if (targetId == null || targetId.isEmpty) return;
    setState(() {
      _loadingOrder = true;
      _error = null;
    });
    try {
      final results = await Future.wait<dynamic>([
        _orderRepository.detail(targetId),
        _paymentRepository.getOrderStatus(targetId),
      ]);
      if (!mounted) return;
      final loadedOrder = results[0] as MasterOrderModel;
      final payments = results[1] as List<OrderPaymentRecord>;
      setState(() {
        _order = loadedOrder;
        _existingPayments = payments;
        _waiverAccepted = loadedOrder.subOrders.every(
          (s) => !s.waiverRequired || s.waiverAccepted,
        );
      });
      _updateHoldCountdown();
    } on AuthFailure catch (e) {
      if (mounted) {
        setState(() => _error = e.message);
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Không tải được thông tin đơn hàng để thanh toán.',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingOrder = false);
      }
    }
  }

  int get _subtotal {
    if (_discountPreview?.valid == true) {
      return _discountPreview!.originalTotalAmount.round();
    }
    if (_order != null) {
      return _order!.subtotalAmount;
    }
    return widget.selectedItems.fold(0, (sum, item) => sum + item.subtotal);
  }

  int get _discountAmount {
    if (_discountPreview?.valid == true) {
      return _discountPreview!.totalDiscountAmount.round();
    }
    if (_order != null) {
      return _order!.discountAmount;
    }
    return 0;
  }

  int get _finalTotal {
    if (_discountPreview?.valid == true) {
      return _discountPreview!.finalPayableAmount.round();
    }
    if (_order != null) {
      return _order!.totalAmount;
    }
    return (_subtotal - _discountAmount).clamp(0, 999999999);
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

  Future<void> _applyDiscount() async {
    if (_previewingDiscount || _submitting) return;
    final code = _discountController.text.trim();
    if (code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng nhập mã giảm giá để kiểm tra.'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }
    final bookingId = _targetBookingId;
    if (bookingId == null || bookingId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Không tìm thấy mã giữ chỗ (bookingId) để xem trước mã giảm giá.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }
    setState(() {
      _previewingDiscount = true;
      _error = null;
    });
    try {
      final preview = await _orderRepository.previewDiscount(
        bookingId: bookingId,
        code: code,
      );
      if (!mounted) return;
      setState(() {
        _discountPreview = preview;
      });
      if (preview.valid) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(
              preview.message ??
                  'Áp dụng mã ${preview.code} (-${_formatPrice(preview.totalDiscountAmount)})!',
            ),
            backgroundColor: AppColors.secondary,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(
              preview.message ?? 'Mã giảm giá không hợp lệ hoặc đã hết hạn.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } on AuthFailure catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(e.message),
          backgroundColor: AppColors.error,
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Không thể kiểm tra mã giảm giá. Vui lòng thử lại.'),
          backgroundColor: AppColors.error,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _previewingDiscount = false);
      }
    }
  }

  Future<void> _processPayment() async {
    if (_submitting) return;
    if (_uncertainProviders.contains(_selectedProvider)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Chưa xác định được kết quả tạo thanh toán trước đó. Vui lòng bấm làm mới trạng thái thanh toán trước khi thử lại.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    final order = _order;
    if (order == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Cần có đơn hàng thực tế từ giữ chỗ để khởi tạo thanh toán trên máy chủ.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    if (order.status == MasterOrderStatus.paid ||
        order.status == MasterOrderStatus.partiallyCompleted ||
        order.status == MasterOrderStatus.completed) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => PaymentResultScreen(
            masterOrder: order,
            provider: _selectedProvider,
            isSuccess: true,
            initialPayment: _existingPayments.isNotEmpty
                ? _existingPayments.first
                : null,
          ),
        ),
      );
      return;
    }

    if (!_waiverAccepted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Vui lòng xác nhận Cam kết miễn trừ trách nhiệm trước khi thanh toán.',
          ),
          backgroundColor: AppColors.primary,
        ),
      );
      return;
    }

    final idempotencyKey = _idempotencyKeys.putIfAbsent(
      _selectedProvider,
      () => PaymentRepository.generateIdempotencyKey('pay'),
    );

    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final langCode =
          Localizations.localeOf(context).languageCode.toUpperCase() == 'EN'
              ? 'EN'
              : 'VI';
      final pendingWaivers = order.subOrders
          .where((s) => s.waiverRequired && !s.waiverAccepted)
          .toList();
      if (pendingWaivers.isNotEmpty) {
        for (final sub in pendingWaivers) {
          await _orderRepository.acceptSubOrderWaiver(
            subOrderId: sub.id,
            version: sub.waiverVersion ?? 1,
            language: langCode,
          );
        }
        final refreshed = await _orderRepository.detail(order.id);
        if (mounted) {
          setState(() => _order = refreshed);
        }
      }

      final intent = await _paymentRepository.createIntent(
        orderId: order.id,
        provider: _selectedProvider,
        idempotencyKey: idempotencyKey,
      );
      OrderPaymentRecord? detail;
      try {
        detail = await _paymentRepository.getPaymentDetail(intent.paymentId);
      } catch (_) {}

      if (!mounted) return;
      final confirmedSuccess =
          detail?.status.toUpperCase() == 'SUCCESS' ||
          intent.status.toUpperCase() == 'SUCCESS';

      // Nếu là thanh toán qua link (PayPal, VNPay, v.v.), chuyển hướng trực tiếp sang Chrome và không cần hiện trang kết quả
      final redirectUrl = (detail?.paymentUrl != null && detail!.paymentUrl!.trim().isNotEmpty)
          ? detail.paymentUrl!.trim()
          : (intent.hasRedirectUrl ? intent.paymentReference.trim() : null);

      if (redirectUrl != null &&
          (redirectUrl.startsWith('http://') || redirectUrl.startsWith('https://')) &&
          !confirmedSuccess) {
        try {
          final uri = Uri.parse(redirectUrl);
          final launched = await launchUrl(
            uri,
            mode: LaunchMode.externalApplication,
          );
          if (!launched) {
            Clipboard.setData(ClipboardData(text: redirectUrl));
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: LocalizedText(
                    'Không thể tự mở trình duyệt. Đã sao chép liên kết thanh toán!',
                  ),
                  backgroundColor: AppColors.secondary,
                ),
              );
            }
          }
        } catch (_) {
          Clipboard.setData(ClipboardData(text: redirectUrl));
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: LocalizedText(
                  'Đã sao chép liên kết thanh toán vào bộ nhớ tạm!',
                ),
                backgroundColor: AppColors.secondary,
              ),
            );
          }
        }

        if (!mounted) return;
        // Sau khi điều hướng sang bên thứ 3 thì mở trang chờ thanh toán
        await Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => PaymentResultScreen(
              masterOrder: order,
              provider: _selectedProvider,
              isSuccess: false,
              paymentIntent: intent,
              initialPayment: detail,
              paymentId: intent.paymentId,
            ),
          ),
        );
        return;
      }

      await Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => PaymentResultScreen(
            masterOrder: order,
            provider: _selectedProvider,
            isSuccess: confirmedSuccess,
            paymentIntent: intent,
            initialPayment: detail,
            paymentId: intent.paymentId,
          ),
        ),
      );
    } on AuthFailure catch (e) {
      if (!mounted) return;
      String message = e.message;
      final payload = e.payload;
      if (payload != null &&
          payload['code']?.toString() == 'WAIVER_ACCEPTANCE_REQUIRED') {
        final missing = payload['missingSubOrders'];
        if (missing is List && missing.isNotEmpty) {
          final names = missing
              .whereType<Map<String, dynamic>>()
              .map((m) => m['serviceName']?.toString() ?? m['subOrderId']?.toString() ?? '')
              .where((s) => s.isNotEmpty)
              .join(', ');
          if (names.isNotEmpty) {
            message = '$message ($names)';
          }
        }
      }
      setState(() {
        _error = message;
        if (e.status == 0 || e.status >= 500) {
          _uncertainProviders.add(_selectedProvider);
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(message),
          backgroundColor: AppColors.error,
        ),
      );
    } catch (_) {
      if (!mounted) return;
      const msg =
          'Chưa xác định được kết quả khởi tạo thanh toán. Vui lòng kiểm tra trạng thái đơn hàng trước khi thử lại.';
      setState(() {
        _error = msg;
        _uncertainProviders.add(_selectedProvider);
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(msg),
          backgroundColor: AppColors.error,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final profile = AuthSession.instance.profile;
    final fullName = (profile?['fullName']?.toString() ?? '').trim();
    final email = (profile?['email']?.toString() ?? '').trim();
    final phone = (profile?['phone']?.toString() ?? '').trim();
    final order = _order;
    final itemCount = order != null
        ? order.subOrders.length
        : widget.selectedItems.length;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: _submitting
              ? null
              : () {
                  FocusManager.instance.primaryFocus?.unfocus();
                  Navigator.pop(context);
                },
        ),
        title: LocalizedText(
          'Thanh toán',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
        actions: [
          if (_targetOrderId != null)
            IconButton(
              icon: const Icon(Icons.refresh, color: AppColors.secondary),
              onPressed: (_loadingOrder || _submitting)
                  ? null
                  : () {
                      _uncertainProviders.clear();
                      _loadOrderAndPaymentStatus();
                    },
            )
          else
            const Padding(
              padding: EdgeInsets.only(right: 16),
              child: Icon(Icons.lock_outline, color: AppColors.secondary),
            ),
        ],
      ),
      body: _loadingOrder && order == null
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 1. COUNTDOWN INVENTORY LOCK BANNER (Trải nghiệm FOMO trực quan, không dùng mã kỹ thuật)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      color: _holdRemaining.inMinutes < 3
                          ? const Color(0xFFFFF3E0)
                          : const Color(0xFFE6F4F1),
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(
                        color: _holdRemaining.inMinutes < 3
                            ? const Color(0xFFFFB74D)
                            : const Color(0xFF80CBC4).withValues(alpha: 0.6),
                        width: 1,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: _holdRemaining.inMinutes < 3
                                ? const Color(0xFFFF9800)
                                : AppColors.secondary,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.timer_outlined,
                            size: 20,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  LocalizedText(
                                    'Thời gian giữ chỗ còn lại: ',
                                    style: AppTypography.labelMd(
                                      color: AppColors.onSurface,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  Text(
                                    _formatCountdown(_holdRemaining),
                                    style: TextStyle(
                                      color: _holdRemaining.inMinutes < 3
                                          ? const Color(0xFFD32F2F)
                                          : AppColors.secondary,
                                      fontSize: 15,
                                      fontWeight: FontWeight.w900,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 3),
                              LocalizedText(
                                '(Chỗ của bạn đang được ưu tiên bảo lưu. Vui lòng thanh toán trước khi hết giờ).',
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurfaceVariant,
                                ).copyWith(
                                  fontSize: 11.5,
                                  fontStyle: FontStyle.italic,
                                  height: 1.25,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
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
                  const SizedBox(height: 16),

                  // 2. CUSTOMER INFO CARD
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
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  const Icon(
                                    Icons.badge_outlined,
                                    size: 18,
                                    color: AppColors.secondary,
                                  ),
                                  const SizedBox(width: 6),
                                  Flexible(
                                    child: LocalizedText(
                                      'Thông tin người đặt',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: AppTypography.labelLg(
                                        color: AppColors.onSurface,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            LocalizedText(
                              'Từ hồ sơ',
                              style: AppTypography.labelSm(
                                color: AppColors.secondary,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Column(
                            children: [
                              _buildContactRow(
                                Icons.person,
                                fullName.isNotEmpty
                                    ? fullName
                                    : (order != null
                                          ? '#${order.customerId}'
                                          : 'Khách hàng DANASEA'),
                              ),
                              if (phone.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                _buildContactRow(Icons.phone, phone),
                              ],
                              if (email.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                _buildContactRow(Icons.email, email),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 3. ORDER SUMMARY BY PROVIDER
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
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  const Icon(
                                    Icons.sailing,
                                    size: 18,
                                    color: AppColors.secondary,
                                  ),
                                  const SizedBox(width: 6),
                                  Flexible(
                                    child: LocalizedText(
                                      'Tóm tắt dịch vụ biển',
                                      overflow: TextOverflow.ellipsis,
                                      style: AppTypography.labelLg(
                                        color: AppColors.onSurface,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            LocalizedText(
                              '$itemCount dịch vụ',
                              style: AppTypography.labelSm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        if (order != null)
                          ...order.subOrders.map((sub) {
                            return Container(
                              margin: const EdgeInsets.only(bottom: 8),
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainerLow,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Row(
                                children: [
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(6),
                                    child: SizedBox(
                                      width: 48,
                                      height: 48,
                                      child: sub.serviceImageUrl.isNotEmpty
                                          ? Image.network(
                                              sub.serviceImageUrl,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, _, _) =>
                                                  Container(
                                                    color: AppColors
                                                        .surfaceContainerHigh,
                                                    child: const Icon(
                                                      Icons.sailing,
                                                      size: 20,
                                                      color:
                                                          AppColors.secondary,
                                                    ),
                                                  ),
                                            )
                                          : Container(
                                              color: AppColors
                                                  .surfaceContainerHigh,
                                              child: const Icon(
                                                Icons.sailing,
                                                size: 20,
                                                color: AppColors.secondary,
                                              ),
                                            ),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        LocalizedText(
                                          sub.serviceName,
                                          style: AppTypography.labelMd(
                                            color: AppColors.onSurface,
                                            fontWeight: FontWeight.w700,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                        LocalizedText(
                                          '${sub.vendorName} • ${sub.quantity} khách',
                                          style: AppTypography.bodySm(
                                            color: AppColors.onSurfaceVariant,
                                          ).copyWith(fontSize: 11),
                                        ),
                                        if (sub.waiverRequired) ...[
                                          const SizedBox(height: 4),
                                          InkWell(
                                            onTap: (_acceptingWaiver || _submitting)
                                                ? null
                                                : () => _openSubOrderWaiverSheet(sub),
                                            child: Row(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                Icon(
                                                  sub.waiverAccepted
                                                      ? Icons.verified_user
                                                      : Icons.assignment_late_outlined,
                                                  size: 13,
                                                  color: sub.waiverAccepted
                                                      ? AppColors.secondary
                                                      : AppColors.primary,
                                                ),
                                                const SizedBox(width: 4),
                                                Flexible(
                                                  child: LocalizedText(
                                                    sub.waiverAccepted
                                                        ? 'Đã ký miễn trừ v${sub.waiverVersion ?? 1}'
                                                        : 'Xem & ký miễn trừ v${sub.waiverVersion ?? 1}',
                                                    style: AppTypography.labelSm(
                                                      color: sub.waiverAccepted
                                                          ? AppColors.secondary
                                                          : AppColors.primary,
                                                      fontWeight: FontWeight.w700,
                                                    ).copyWith(fontSize: 10),
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                  LocalizedText(
                                    _formatPrice(sub.finalAmount),
                                    style: AppTypography.labelMd(
                                      color: AppColors.primary,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          })
                        else
                          ...widget.selectedItems.map((item) {
                            return Container(
                              margin: const EdgeInsets.only(bottom: 8),
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainerLow,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Row(
                                children: [
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(6),
                                    child: SizedBox(
                                      width: 48,
                                      height: 48,
                                      child: Image.network(
                                        item.service.imageUrls.isNotEmpty
                                            ? item.service.imageUrls.first
                                            : '',
                                        fit: BoxFit.cover,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        LocalizedText(
                                          item.service.name,
                                          style: AppTypography.labelMd(
                                            color: AppColors.onSurface,
                                            fontWeight: FontWeight.w700,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                        LocalizedText(
                                          '${item.service.vendorName} • ${item.quantity} khách',
                                          style: AppTypography.bodySm(
                                            color: AppColors.onSurfaceVariant,
                                          ).copyWith(fontSize: 11),
                                        ),
                                      ],
                                    ),
                                  ),
                                  LocalizedText(
                                    _formatPrice(item.subtotal),
                                    style: AppTypography.labelMd(
                                      color: AppColors.primary,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 4. DISCOUNT CODE SECTION
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.confirmation_number_outlined,
                          size: 20,
                          color: AppColors.secondary,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            controller: _discountController,
                            enabled: !_previewingDiscount && !_submitting,
                            textCapitalization: TextCapitalization.characters,
                            onTapOutside: (_) =>
                                FocusManager.instance.primaryFocus?.unfocus(),
                            onChanged: (_) {
                              if (_discountPreview != null) {
                                setState(() => _discountPreview = null);
                              }
                            },
                            decoration: InputDecoration(
                              hintText: tr(
                                context,
                                'Mã giảm giá (ví dụ DANASEA2024)',
                              ),
                              hintStyle: AppTypography.bodySm(
                                color: AppColors.outline,
                              ),
                              border: InputBorder.none,
                              isDense: true,
                            ),
                          ),
                        ),
                        ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.secondary,
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                          ),
                          onPressed: (_previewingDiscount || _submitting)
                              ? null
                              : _applyDiscount,
                          child: _previewingDiscount
                              ? const SizedBox(
                                  width: 14,
                                  height: 14,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : LocalizedText(
                                  'Áp dụng',
                                  style: AppTypography.labelSm(
                                    color: Colors.white,
                                  ),
                                ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 5. PAYMENT METHOD SELECTION (VNPAY, PAYPAL, MOMO, SEPAY)
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
                        Row(
                          children: [
                            const Icon(
                              Icons.payment,
                              size: 18,
                              color: AppColors.secondary,
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: LocalizedText(
                                'Phương thức thanh toán',
                                style: AppTypography.labelLg(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        _buildPaymentRadio(
                          provider: PaymentProvider.vnpay,
                          title: 'VNPAY Cổng thanh toán',
                          subtitle:
                              'Hỗ trợ thẻ ATM nội địa, QR Pay, Visa/Mastercard',
                          icon: Icons.credit_card,
                        ),
                        _buildPaymentRadio(
                          provider: PaymentProvider.paypal,
                          title: 'PayPal Quốc tế',
                          subtitle:
                              'Thanh toán quốc tế an toàn qua tài khoản PayPal',
                          icon: Icons.public,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 16),

                  // 6. WAIVER AGREEMENT CHECKBOX
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(
                        color: _waiverAccepted
                            ? AppColors.secondary
                            : AppColors.borderSubtle,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Checkbox(
                          value: _waiverAccepted,
                          activeColor: AppColors.secondary,
                          onChanged: (val) {
                            setState(() => _waiverAccepted = val ?? false);
                          },
                        ),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: LocalizedText(
                              'Tôi xác nhận đủ điều kiện sức khỏe và đồng ý với Cam kết miễn trừ trách nhiệm khi tham gia các hoạt động thể thao biển tại Đà Nẵng.',
                              style: AppTypography.bodySm(
                                color: AppColors.onSurface,
                              ).copyWith(fontSize: 12),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),

                  // 7. SUMMARY & CONFIRM BUTTON
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            LocalizedText(
                              'Tạm tính:',
                              style: AppTypography.bodySm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            LocalizedText(
                              _formatPrice(_subtotal),
                              style: AppTypography.labelMd(
                                color: AppColors.onSurface,
                              ),
                            ),
                          ],
                        ),
                        if (_discountAmount > 0) ...[
                          const SizedBox(height: 6),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              LocalizedText(
                                'Giảm giá (${_discountPreview?.valid == true ? _discountPreview!.code : (order?.discountCode ?? 'DISCOUNT')}):',
                                style: AppTypography.bodySm(
                                  color: AppColors.secondary,
                                ),
                              ),
                              LocalizedText(
                                '- ${_formatPrice(_discountAmount)}',
                                style: AppTypography.labelMd(
                                  color: AppColors.secondary,
                                ),
                              ),
                            ],
                          ),
                        ],
                        const Divider(height: 16),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: LocalizedText(
                                'Tổng thanh toán:',
                                style: AppTypography.labelLg(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            LocalizedText(
                              _formatPrice(_finalTotal),
                              style: AppTypography.headlineSm(
                                color: AppColors.primary,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        AppPillButton(
                          label: _submitting
                              ? 'Đang khởi tạo thanh toán...'
                              : 'Xác nhận thanh toán ${_formatPrice(_finalTotal)}',
                          variant: AppButtonVariant.primary,
                          width: double.infinity,
                          onPressed: _submitting ? null : _processPayment,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Future<void> _openSubOrderWaiverSheet(SubOrderModel sub) async {
    if (_acceptingWaiver || _submitting) return;
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
                else
                  AppPillButton(
                    label: 'Đồng ý & Ký cam kết miễn trừ',
                    variant: AppButtonVariant.primary,
                    width: double.infinity,
                    onPressed: () => Navigator.pop(sheetContext, true),
                  ),
              ],
            ),
          ),
        );
      },
    );
    if (confirmed != true || !mounted) return;
    setState(() => _acceptingWaiver = true);
    try {
      final langCode =
          Localizations.localeOf(context).languageCode.toUpperCase() == 'EN'
              ? 'EN'
              : 'VI';
      await _orderRepository.acceptSubOrderWaiver(
        subOrderId: sub.id,
        version: sub.waiverVersion ?? 1,
        language: langCode,
      );
      await _loadOrderAndPaymentStatus();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã xác nhận cam kết miễn trừ trách nhiệm!'),
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
            content: LocalizedText(
              'Không thể ghi nhận cam kết miễn trừ. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _acceptingWaiver = false);
    }
  }

  Widget _buildContactRow(IconData icon, String text) {
    return Row(
      children: [
        Icon(icon, size: 16, color: AppColors.tertiary),
        const SizedBox(width: 8),
        Expanded(
          child: LocalizedText(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: AppTypography.bodySm(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildPaymentRadio({
    required PaymentProvider provider,
    required String title,
    required String subtitle,
    required IconData icon,
  }) {
    final isSelected = _selectedProvider == provider;
    return InkWell(
      onTap: _submitting
          ? null
          : () => setState(() => _selectedProvider = provider),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.secondaryContainer.withValues(alpha: 0.3)
              : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isSelected ? AppColors.secondary : Colors.transparent,
            width: 1.2,
          ),
        ),
        child: Row(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              child: Container(
                width: 20,
                height: 20,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: isSelected ? AppColors.secondary : AppColors.outline,
                    width: 2,
                  ),
                ),
                child: isSelected
                    ? Center(
                        child: Container(
                          width: 10,
                          height: 10,
                          decoration: const BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppColors.secondary,
                          ),
                        ),
                      )
                    : null,
              ),
            ),
            Icon(icon, size: 20, color: AppColors.secondary),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  LocalizedText(
                    title,
                    style: AppTypography.labelMd(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  LocalizedText(
                    subtitle,
                    style: AppTypography.bodySm(
                      color: AppColors.onSurfaceVariant,
                    ).copyWith(fontSize: 10),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
