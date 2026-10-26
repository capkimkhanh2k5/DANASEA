import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/booking_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../orders/presentation/screens/order_detail_screen.dart';

/// Separate booking flow; existing detail and legacy booking layouts stay intact.
class BookingHoldScreen extends StatefulWidget {
  const BookingHoldScreen({
    super.key,
    required this.service,
    required this.slot,
  });
  final ServiceModel service;
  final CatalogSlot slot;

  @override
  State<BookingHoldScreen> createState() => _BookingHoldScreenState();
}

class _BookingHoldScreenState extends State<BookingHoldScreen> {
  final _repository = BookingRepository();
  final _orderRepository = OrderRepository();
  final _form = GlobalKey<FormState>();
  final _quantity = TextEditingController(text: '1');
  final _participants = TextEditingController(text: '1');
  final _discount = TextEditingController();
  BookingHold? _hold;
  MasterOrderModel? _createdOrder;
  DiscountPreviewResult? _discountPreview;
  Timer? _timer;
  bool _busy = false, _allowSplit = false, _cancelled = false;
  bool _uncertain = false, _orderUncertain = false, _allowExit = false;
  bool _closing = false;
  String? _error;
  late final String _orderIdempotencyKey =
      OrderRepository.generateIdempotencyKey('ord');

  bool get _package => widget.slot.pricingUnit == 'PER_PACKAGE';
  bool get _active =>
      _hold != null &&
      _createdOrder == null &&
      !_hold!.confirmed &&
      !_hold!.expired &&
      !_cancelled;

  @override
  void dispose() {
    _timer?.cancel();
    _quantity.dispose();
    _participants.dispose();
    _discount.dispose();
    super.dispose();
  }

  Future<void> _run(
    Future<void> Function() operation, {
    bool creating = false,
    bool creatingOrder = false,
  }) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await operation();
    } on AuthFailure catch (error) {
      if (mounted)
        setState(() {
          _error = error.message;
          // A missing response does not prove that the server rejected a POST.
          if (creating && (error.status == 0 || error.status >= 500))
            _uncertain = true;
          if (creatingOrder && (error.status == 0 || error.status >= 500))
            _orderUncertain = true;
        });
    } catch (_) {
      if (mounted)
        setState(() {
          _error = 'Không thể thực hiện yêu cầu. Vui lòng thử lại.';
          if (creating) _uncertain = true;
          if (creatingOrder) _orderUncertain = true;
        });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _create() async {
    if (_hold != null || _uncertain || !_form.currentState!.validate()) return;
    await _run(() async {
      final hold = await _repository.hold(
        slotId: widget.slot.id,
        optionId: widget.slot.optionId!,
        quantity: int.parse(_quantity.text.trim()),
        participantsCount: _package
            ? int.parse(_participants.text.trim())
            : null,
        allowSplit: !_package && _allowSplit,
      );
      if (!mounted) return;
      setState(() => _hold = hold);
      _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted) {
          timer.cancel();
          return;
        }
        setState(() {});
        if (_hold!.expired ||
            _hold!.confirmed ||
            _cancelled ||
            _createdOrder != null) {
          timer.cancel();
        }
      });
    }, creating: true);
  }

  Future<void> _previewDiscount() async {
    if (!_active || _busy) return;
    final code = _discount.text.trim();
    if (code.isEmpty) {
      setState(() {
        _discountPreview = null;
        _error = 'Vui lòng nhập mã giảm giá để kiểm tra.';
      });
      return;
    }
    await _run(() async {
      final preview = await _orderRepository.previewDiscount(
        bookingId: _hold!.id,
        code: code,
      );
      if (!mounted) return;
      setState(() {
        _discountPreview = preview;
        if (!preview.valid) {
          _error =
              preview.message ?? 'Mã giảm giá không hợp lệ hoặc đã hết hạn.';
        }
      });
    });
  }

  Future<void> _createOrder() async {
    if (!_active || _orderUncertain) return;
    await _run(() async {
      final appliedCode = _discountPreview?.valid == true
          ? _discountPreview!.code
          : _discount.text.trim();
      final order = await _orderRepository.createOrder(
        bookingId: _hold!.id,
        idempotencyKey: _orderIdempotencyKey,
        discountCode: appliedCode,
      );
      if (!mounted) return;
      _timer?.cancel();
      setState(() => _createdOrder = order);
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => order.status == MasterOrderStatus.pendingPayment
              ? CheckoutScreen(masterOrder: order)
              : OrderDetailScreen(masterOrder: order),
        ),
      );
    }, creatingOrder: true);
  }

  Future<void> _confirm() async {
    if (!_active) return;
    await _run(() async {
      final confirmed = await _repository.confirm(_hold!.id);
      if (!mounted) return;
      setState(() {
        _hold = confirmed;
        if (!confirmed.confirmed) _error = 'Đặt chỗ chưa được xác nhận.';
      });
    });
  }

  Future<void> _cancel() async {
    if (_hold == null ||
        _hold!.confirmed ||
        _cancelled ||
        _createdOrder != null)
      return;
    await _run(() async {
      await _repository.cancel(_hold!.id);
      if (mounted) setState(() => _cancelled = true);
    });
  }

  Future<void> _close() async {
    if (_busy || _closing) return;
    if (_active) {
      _closing = true;
      final leave = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const LocalizedText('Chỗ vẫn đang được giữ'),
          content: const LocalizedText(
            'Đóng màn hình không bỏ giữ chỗ. Chỗ sẽ được giữ đến khi hết hạn. Bạn có thể ở lại để bỏ giữ chỗ.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const LocalizedText('Ở lại'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const LocalizedText('Đóng'),
            ),
          ],
        ),
      );
      _closing = false;
      if (leave != true || !mounted) return;
    }
    if (!mounted) return;
    setState(() => _allowExit = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.of(context).pop();
    });
  }

  String? _positive(String? input, {int? max}) {
    final value = int.tryParse(input?.trim() ?? '');
    if (value == null || value < 1 || (max != null && value > max)) {
      return tr(context, 'Số lượng không hợp lệ.');
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final hold = _hold;
    final createdOrder = _createdOrder;
    final remaining =
        hold?.expiresAt?.difference(DateTime.now()).inSeconds ?? 0;
    final quantityLimit = (widget.slot.availableCapacity ?? 0)
        .clamp(0, 100)
        .toInt();
    return PopScope(
      canPop: _allowExit,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) _close();
      },
      child: Scaffold(
        appBar: AppBar(
          title: const LocalizedText('Giữ chỗ'),
          leading: IconButton(
            onPressed: _busy ? null : _close,
            icon: const Icon(Icons.arrow_back),
          ),
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(
              widget.service.name,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Text(
              '${widget.slot.date} · ${widget.slot.startTime} – ${widget.slot.endTime}',
            ),
            const SizedBox(height: 20),
            if (hold == null)
              Form(
                key: _form,
                child: Column(
                  children: [
                    TextFormField(
                      controller: _quantity,
                      enabled: !_busy && !_uncertain,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: tr(
                          context,
                          _package ? 'Số gói' : 'Số người',
                        ),
                      ),
                      validator: (value) =>
                          _positive(value, max: quantityLimit),
                    ),
                    if (_package)
                      TextFormField(
                        controller: _participants,
                        enabled: !_busy && !_uncertain,
                        keyboardType: TextInputType.number,
                        decoration: InputDecoration(
                          labelText: tr(context, 'Số người mỗi gói'),
                        ),
                        validator: (value) =>
                            _positive(value, max: widget.slot.maxPaxPerPackage),
                      ),
                    if (!_package)
                      CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const LocalizedText('Cho phép chia nhóm'),
                        value: _allowSplit,
                        onChanged: _busy || _uncertain
                            ? null
                            : (value) =>
                                  setState(() => _allowSplit = value ?? false),
                      ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed:
                          _busy ||
                              _uncertain ||
                              !widget.slot.bookable ||
                              widget.slot.optionId == null ||
                              quantityLimit < 1
                          ? null
                          : _create,
                      child: const LocalizedText('Giữ chỗ'),
                    ),
                  ],
                ),
              ),
            if (hold != null) ...[
              SelectableText(hold.id),
              Text(
                '${NumberFormat.decimalPattern('vi').format(createdOrder?.totalAmount ?? (_discountPreview?.valid == true ? _discountPreview!.finalPayableAmount : hold.totalAmount))} ₫',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              if (_discountPreview?.valid == true && createdOrder == null) ...[
                const SizedBox(height: 4),
                Text(
                  '${tr(context, 'Giảm giá')} (${_discountPreview!.code}): -${NumberFormat.decimalPattern('vi').format(_discountPreview!.totalDiscountAmount)} ₫',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                if (_discountPreview!.message != null &&
                    _discountPreview!.message!.isNotEmpty)
                  LocalizedText(_discountPreview!.message!),
              ],
              if (createdOrder != null) ...[
                const SizedBox(height: 8),
                LocalizedText('Đã tạo đơn hàng #${createdOrder.id}'),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) =>
                            OrderDetailScreen(masterOrder: createdOrder),
                      ),
                    );
                  },
                  child: const LocalizedText('Chi tiết đơn'),
                ),
              ] else if (_cancelled)
                const LocalizedText('Đã bỏ giữ chỗ.')
              else if (hold.confirmed)
                const LocalizedText('Đặt chỗ đã được xác nhận.')
              else if (hold.expired)
                const LocalizedText('Thời gian giữ chỗ đã hết.')
              else ...[
                LocalizedText(
                  'Thời gian còn lại: ${remaining ~/ 60}:${(remaining % 60).toString().padLeft(2, '0')}',
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _discount,
                        enabled: !_busy && !_orderUncertain,
                        textCapitalization: TextCapitalization.characters,
                        onChanged: (_) {
                          if (_discountPreview != null) {
                            setState(() => _discountPreview = null);
                          }
                        },
                        decoration: InputDecoration(
                          labelText: tr(context, 'Mã giảm giá (không bắt buộc)'),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton(
                      onPressed: _busy || _orderUncertain
                          ? null
                          : _previewDiscount,
                      child: const LocalizedText('Áp dụng'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _busy || _orderUncertain ? null : _createOrder,
                  child: const LocalizedText('Tạo đơn hàng'),
                ),
                const SizedBox(height: 8),
                const LocalizedText(
                  'Chỉ xác nhận được sau khi thanh toán thành công. Thanh toán trong app chưa khả dụng.',
                ),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: _busy ? null : _confirm,
                  child: const LocalizedText('Xác nhận đặt chỗ'),
                ),
              ],
              if (!_cancelled && !hold.confirmed && createdOrder == null)
                OutlinedButton(
                  onPressed: _busy ? null : _cancel,
                  child: const LocalizedText('Bỏ giữ chỗ'),
                ),
            ],
            if (_busy)
              const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: LocalizedText(_error!),
              ),
            if (_uncertain)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: LocalizedText(
                  'Chưa xác định được kết quả giữ chỗ. Không gửi lại để tránh tạo giữ chỗ trùng.',
                ),
              ),
            if (_orderUncertain)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: LocalizedText(
                  'Chưa xác định được kết quả tạo đơn hàng. Không tự động gửi lại để tránh trùng lặp, vui lòng kiểm tra Đơn hàng của tôi.',
                ),
              ),
          ],
        ),
      ),
    );
  }
}
