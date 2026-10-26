import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/booking_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../../core/models/danasea_models.dart';
import 'order_detail_screen.dart';

const _statuses = {
  'HOLD': 'Đang giữ chỗ',
  'PENDING_PAYMENT': 'Chờ thanh toán',
  'CONFIRMED': 'Đã xác nhận',
  'CANCELLED': 'Đã hủy',
  'REJECTED': 'Bị từ chối',
};

String _money(num amount) =>
    '${amount.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+$)'), (m) => '${m[1]}.')} đ';
String _date(DateTime? date) {
  if (date == null) return '—';
  final local = date.toLocal();
  return '${local.day}/${local.month}/${local.year} ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
}

String _error(Object error) => error is AuthFailure
    ? error.message
    : 'Không thể tải dữ liệu. Vui lòng thử lại.';

Widget _card(List<Widget> children) => Container(
  padding: const EdgeInsets.all(AppShapes.spaceSm),
  decoration: BoxDecoration(
    color: AppColors.surfaceContainerLowest,
    borderRadius: AppShapes.radiusDefault,
    boxShadow: AppShapes.shadowLevel1,
    border: Border.all(color: AppColors.borderSubtle),
  ),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: children,
  ),
);

class MyBookingsScreen extends StatefulWidget {
  const MyBookingsScreen({super.key});
  @override
  State<MyBookingsScreen> createState() => _MyBookingsScreenState();
}

class _MyBookingsScreenState extends State<MyBookingsScreen> {
  final _repository = BookingRepository();
  List<CustomerBooking> _bookings = [];
  String? _status, _failure;
  bool _loading = false, _hasMore = false;
  int _page = -1, _generation = 0;

  @override
  void initState() {
    super.initState();
    AuthSession.instance.accountVersion.addListener(_accountChanged);
    _load();
  }

  void _accountChanged() => _load();

  @override
  void dispose() {
    AuthSession.instance.accountVersion.removeListener(_accountChanged);
    super.dispose();
  }

  Future<void> _load({bool more = false}) async {
    if (more && _loading) return;
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _failure = null;
      if (!more) {
        _bookings = [];
        _page = -1;
        _hasMore = false;
      }
    });
    try {
      final result = await _repository.list(page: _page + 1, status: _status);
      if (!mounted || generation != _generation) return;
      setState(() {
        _bookings = [..._bookings, ...result.content];
        _page = result.page;
        _hasMore = _page + 1 < result.totalPages;
      });
    } catch (e) {
      if (mounted && generation == _generation)
        setState(() => _failure = _error(e));
    } finally {
      if (mounted && generation == _generation)
        setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: AppColors.surface,
    appBar: AppBar(
      title: const LocalizedText('Booking của tôi'),
      backgroundColor: AppColors.surface,
    ),
    body: Column(
      children: [
        SizedBox(
          height: 56,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            children: [
              for (final entry in <String?, String>{
                null: 'Tất cả',
                ..._statuses,
              }.entries)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: LocalizedText(entry.value),
                    selected: _status == entry.key,
                    selectedColor: AppColors.secondaryContainer,
                    onSelected: (_) {
                      setState(() => _status = entry.key);
                      _load();
                    },
                  ),
                ),
            ],
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: _load,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              children: [
                for (final booking in _bookings)
                  Padding(
                    padding: const EdgeInsets.only(bottom: AppShapes.spaceMd),
                    child: _card([
                      Text(
                        '#${booking.id}',
                        style: AppTypography.labelMd(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 8),
                      LocalizedText(
                        _statuses[booking.status] ?? booking.status,
                        style: AppTypography.labelMd(
                          color: AppColors.secondary,
                        ),
                      ),
                      Text(_date(booking.createdAt)),
                      Text('${booking.totalItems} ${tr(context, 'dịch vụ')}'),
                      const Divider(),
                      Text(
                        _money(booking.totalAmount),
                        style: AppTypography.headlineSm(
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(height: 12),
                      AppPillButton(
                        label: 'Xem chi tiết',
                        width: double.infinity,
                        onPressed: () async {
                          await Navigator.push<void>(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  BookingDetailScreen(bookingId: booking.id),
                            ),
                          );
                          if (mounted) await _load();
                        },
                      ),
                    ]),
                  ),
                if (_loading) const Center(child: CircularProgressIndicator()),
                if (_failure != null) ...[
                  LocalizedText(_failure!),
                  TextButton(
                    onPressed: () => _load(more: _bookings.isNotEmpty),
                    child: const LocalizedText('Thử lại'),
                  ),
                ],
                if (!_loading && _failure == null && _bookings.isEmpty)
                  const Center(
                    child: LocalizedText(
                      'Chưa có booking trong danh sách này.',
                    ),
                  ),
                if (!_loading && _failure == null && _hasMore)
                  AppPillButton(
                    label: 'Xem thêm',
                    onPressed: () => _load(more: true),
                  ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class BookingDetailScreen extends StatefulWidget {
  const BookingDetailScreen({super.key, required this.bookingId});
  final String bookingId;
  @override
  State<BookingDetailScreen> createState() => _BookingDetailScreenState();
}

class _BookingDetailScreenState extends State<BookingDetailScreen> {
  final _repository = BookingRepository();
  final _orderRepository = OrderRepository();
  CustomerBooking? _booking;
  BookingCancellation? _cancellation;
  String? _failure;
  bool _loading = true, _cancelling = false, _creatingOrder = false;
  bool _orderUncertain = false;
  int _generation = 0;
  late final String _orderIdempotencyKey =
      OrderRepository.generateIdempotencyKey('ord');

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _failure = null;
    });
    try {
      final booking = await _repository.detail(widget.bookingId);
      if (mounted && generation == _generation)
        setState(() => _booking = booking);
    } catch (e) {
      if (mounted && generation == _generation)
        setState(() => _failure = _error(e));
    } finally {
      if (mounted && generation == _generation)
        setState(() => _loading = false);
    }
  }

  Future<void> _createOrder() async {
    if (_creatingOrder || _cancelling || _orderUncertain) return;
    String discountCode = '';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const LocalizedText('Tạo đơn hàng'),
        content: TextField(
          onChanged: (value) => discountCode = value,
          textCapitalization: TextCapitalization.characters,
          decoration: InputDecoration(
            labelText: tr(context, 'Mã giảm giá (không bắt buộc)'),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const LocalizedText('Quay lại'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const LocalizedText('Tạo đơn hàng'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _creatingOrder = true);
    try {
      final order = await _orderRepository.createOrder(
        bookingId: widget.bookingId,
        idempotencyKey: _orderIdempotencyKey,
        discountCode: discountCode,
      );
      if (!mounted) return;
      await _load();
      if (!mounted) return;
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => order.status == MasterOrderStatus.pendingPayment
              ? CheckoutScreen(masterOrder: order)
              : OrderDetailScreen(masterOrder: order),
        ),
      );
    } on AuthFailure catch (e) {
      if (mounted) {
        if (e.status == 0 || e.status >= 500) {
          setState(() => _orderUncertain = true);
        }
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(e.message)));
      }
    } catch (e) {
      if (mounted) {
        setState(() => _orderUncertain = true);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(_error(e))));
      }
    } finally {
      if (mounted) setState(() => _creatingOrder = false);
    }
  }

  Future<void> _cancel() async {
    if (_cancelling) return;
    String reason = '';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const LocalizedText('Hủy booking'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const LocalizedText(
              'Yêu cầu này hủy toàn bộ dịch vụ trong booking. Tiếp tục?',
            ),
            TextField(
              onChanged: (value) => reason = value,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: tr(context, 'Lý do hủy (không bắt buộc)'),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const LocalizedText('Quay lại'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const LocalizedText('Xác nhận hủy'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _cancelling = true);
    try {
      final result = await _repository.cancelBooking(
        widget.bookingId,
        reason: reason,
      );
      if (!mounted) return;
      setState(() => _cancellation = result);
      await _load();
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(_error(e))));
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final booking = _booking;
    final cancellation = _cancellation;
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        title: const LocalizedText('Chi tiết booking'),
        backgroundColor: AppColors.surface,
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(AppShapes.gutterMobile),
          children: [
            if (cancellation != null)
              _card([
                LocalizedText(cancellation.message),
                LocalizedText(
                  cancellation.refundEligible
                      ? 'Đủ điều kiện hoàn tiền'
                      : 'Không có khoản hoàn tiền',
                ),
                Text(
                  '${cancellation.refundPercentage}% • ${_money(cancellation.refundAmount)}',
                ),
              ]),
            if (_loading)
              const Center(child: CircularProgressIndicator())
            else if (_failure != null) ...[
              LocalizedText(_failure!),
              TextButton(
                onPressed: _load,
                child: const LocalizedText('Thử lại'),
              ),
            ] else if (booking != null) ...[
              _card([
                SelectableText('#${booking.id}'),
                const SizedBox(height: 8),
                LocalizedText(
                  _statuses[booking.status] ?? booking.status,
                  style: AppTypography.headlineSm(color: AppColors.secondary),
                ),
                Text('${tr(context, 'Ngày tạo')}: ${_date(booking.createdAt)}'),
                if (booking.status == 'HOLD' && booking.expiresAt != null)
                  Text(
                    '${tr(context, 'Hết hạn giữ chỗ')}: ${_date(booking.expiresAt)}',
                  ),
                const Divider(),
                Text(
                  _money(booking.totalAmount),
                  style: AppTypography.headlineSm(color: AppColors.primary),
                ),
              ]),
              for (final item in booking.items)
                Padding(
                  padding: const EdgeInsets.only(top: AppShapes.spaceMd),
                  child: _card([
                    Text('${tr(context, 'Dịch vụ')}: ${item.serviceId}'),
                    Text('${tr(context, 'Nhà cung cấp')}: ${item.vendorId}'),
                    Text('${item.date} • ${item.time}'),
                    Text('${item.quantity} × ${_money(item.price)}'),
                    const Divider(),
                    Text(
                      _money(item.subtotal),
                      style: AppTypography.labelLg(color: AppColors.primary),
                    ),
                  ]),
                ),
              if (booking.status == 'HOLD' && cancellation == null) ...[
                const SizedBox(height: 24),
                AppPillButton(
                  label: _creatingOrder ? 'Đang tạo đơn...' : 'Tạo đơn hàng',
                  onPressed:
                      _creatingOrder || _cancelling || _orderUncertain
                          ? null
                          : _createOrder,
                ),
                if (_orderUncertain)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: LocalizedText(
                      'Chưa xác định được kết quả tạo đơn hàng. Không tự động gửi lại để tránh trùng lặp, vui lòng kiểm tra Đơn hàng của tôi.',
                    ),
                  ),
              ],
              if (booking.canCancel && cancellation == null) ...[
                const SizedBox(height: 12),
                AppPillButton(
                  label: _cancelling ? 'Đang gửi yêu cầu...' : 'Hủy booking',
                  variant: AppButtonVariant.outline,
                  onPressed: _cancelling || _creatingOrder ? null : _cancel,
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}
