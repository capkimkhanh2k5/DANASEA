import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../checkout/presentation/screens/e_ticket_qr_screen.dart';
import 'order_detail_screen.dart';
import 'my_bookings_screen.dart';

class MyOrdersScreen extends StatefulWidget {
  final VoidCallback? onExplore;

  const MyOrdersScreen({super.key, this.onExplore});

  @override
  State<MyOrdersScreen> createState() => _MyOrdersScreenState();
}

class _MyOrdersScreenState extends State<MyOrdersScreen> {
  final OrderRepository _repository = OrderRepository();
  int _selectedFilterIndex = 0;
  List<MasterOrderModel> _orders = [];
  bool _loading = false;
  bool _hasMore = false;
  int _page = -1;
  int _generation = 0;
  String? _error;

  final List<String> _filters = [
    'Tất cả',
    'Chờ thanh toán',
    'Đã thanh toán',
    'Hoàn thành một phần',
    'Đã hoàn thành',
    'Đã hủy',
  ];

  @override
  void initState() {
    super.initState();
    AuthSession.instance.accountVersion.addListener(_onAccountChanged);
    _loadOrders();
  }

  void _onAccountChanged() {
    _loadOrders();
  }

  @override
  void dispose() {
    AuthSession.instance.accountVersion.removeListener(_onAccountChanged);
    super.dispose();
  }

  Future<void> _loadOrders({bool loadMore = false}) async {
    if (loadMore && _loading) return;
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      if (!loadMore) {
        _orders = [];
        _page = -1;
        _hasMore = false;
      }
    });
    try {
      final result = await _repository.list(page: _page + 1, size: 20);
      if (!mounted || generation != _generation) return;
      setState(() {
        _orders = [..._orders, ...result.content];
        _page = result.page;
        _hasMore = _page + 1 < result.totalPages;
      });
    } on AuthFailure catch (e) {
      if (mounted && generation == _generation) {
        setState(() => _error = e.message);
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(
          () => _error = 'Không thể tải danh sách đơn hàng. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  List<MasterOrderModel> get _filteredOrders {
    final all = _orders;
    if (_selectedFilterIndex == 0) return all;
    if (_selectedFilterIndex == 1) {
      return all
          .where((o) => o.status == MasterOrderStatus.pendingPayment)
          .toList();
    }
    if (_selectedFilterIndex == 2) {
      return all.where((o) => o.status == MasterOrderStatus.paid).toList();
    }
    if (_selectedFilterIndex == 3) {
      return all
          .where((o) => o.status == MasterOrderStatus.partiallyCompleted)
          .toList();
    }
    if (_selectedFilterIndex == 4) {
      return all
          .where((o) => o.status == MasterOrderStatus.completed)
          .toList();
    }
    return all
        .where((o) => o.status == MasterOrderStatus.cancelled)
        .toList();
  }

  String _formatPrice(int price) {
    final str = price.toString();
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

  String _getStatusName(MasterOrderStatus status) {
    switch (status) {
      case MasterOrderStatus.pendingPayment:
        return 'Chờ thanh toán';
      case MasterOrderStatus.paid:
        return 'Đã thanh toán';
      case MasterOrderStatus.partiallyCompleted:
        return 'Hoàn thành một phần';
      case MasterOrderStatus.completed:
        return 'Đã hoàn thành';
      case MasterOrderStatus.cancelled:
        return 'Đã hủy';
    }
  }

  Color _getStatusColor(MasterOrderStatus status) {
    switch (status) {
      case MasterOrderStatus.paid:
        return AppColors.secondary;
      case MasterOrderStatus.completed:
        return AppColors.secondaryTeal;
      case MasterOrderStatus.pendingPayment:
        return AppColors.primary;
      case MasterOrderStatus.partiallyCompleted:
        return AppColors.tertiary;
      case MasterOrderStatus.cancelled:
        return AppColors.error;
    }
  }

  @override
  Widget build(BuildContext context) {
    final orders = _filteredOrders;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText(
              'Đơn hàng của tôi',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            LocalizedText(
              'Quản lý vé & lịch trình trải nghiệm biển',
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant)
                  .copyWith(fontSize: 11),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.event_note, color: AppColors.secondary),
            title: const LocalizedText('Booking của tôi'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () async {
              await Navigator.push<void>(
                context,
                MaterialPageRoute(
                  builder: (_) => const MyBookingsScreen(),
                ),
              );
              if (mounted) _loadOrders();
            },
          ),
          // STATUS FILTER TABS
          Container(
            height: 48,
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(
                  horizontal: AppShapes.gutterMobile),
              itemCount: _filters.length,
              separatorBuilder: (_, unused) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final isSel = _selectedFilterIndex == index;
                return InkWell(
                  onTap: () => setState(() => _selectedFilterIndex = index),
                  borderRadius: BorderRadius.circular(18),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 6),
                    decoration: BoxDecoration(
                      color: isSel
                          ? AppColors.secondary
                          : AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(18),
                      boxShadow: isSel ? AppShapes.shadowLevel1 : null,
                    ),
                    child: Center(
                      child: LocalizedText(
                        _filters[index],
                        style: AppTypography.labelSm(
                          color: isSel ? Colors.white : AppColors.onSurfaceVariant,
                          fontWeight: isSel ? FontWeight.w700 : FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),

          // ORDERS LIST
          Expanded(
            child: RefreshIndicator(
              onRefresh: _loadOrders,
              child: _loading && _orders.isEmpty
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null && _orders.isEmpty
                      ? _buildErrorState()
                      : orders.isEmpty
                          ? ListView(
                              physics: const AlwaysScrollableScrollPhysics(),
                              children: [
                                SizedBox(
                                  height:
                                      MediaQuery.of(context).size.height * 0.55,
                                  child: _buildEmptyState(),
                                ),
                              ],
                            )
                          : ListView.separated(
                              physics: const AlwaysScrollableScrollPhysics(),
                              padding: const EdgeInsets.symmetric(
                                horizontal: AppShapes.gutterMobile,
                                vertical: 10,
                              ),
                              itemCount: orders.length +
                                  ((_loading || _error != null || _hasMore)
                                      ? 1
                                      : 0),
                              separatorBuilder: (_, unused) =>
                                  const SizedBox(height: AppShapes.spaceMd),
                              itemBuilder: (context, index) {
                                if (index == orders.length) {
                                  if (_loading) {
                                    return const Padding(
                                      padding: EdgeInsets.all(16),
                                      child: Center(
                                        child: CircularProgressIndicator(),
                                      ),
                                    );
                                  }
                                  if (_error != null) {
                                    return Column(
                                      children: [
                                        LocalizedText(
                                          _error!,
                                          style: AppTypography.bodySm(
                                            color: AppColors.error,
                                          ),
                                          textAlign: TextAlign.center,
                                        ),
                                        TextButton(
                                          onPressed: () =>
                                              _loadOrders(loadMore: true),
                                          child: const LocalizedText('Thử lại'),
                                        ),
                                      ],
                                    );
                                  }
                                  if (_hasMore) {
                                    return AppPillButton(
                                      label: 'Xem thêm',
                                      onPressed: () =>
                                          _loadOrders(loadMore: true),
                                    );
                                  }
                                }
                                return _buildOrderCard(orders[index]);
                              },
                            ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorState() {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(AppShapes.gutterMobile),
      children: [
        const SizedBox(height: 80),
        const Icon(Icons.error_outline, size: 48, color: AppColors.error),
        const SizedBox(height: 12),
        Center(
          child: LocalizedText(
            _error!,
            textAlign: TextAlign.center,
            style: AppTypography.bodyMd(color: AppColors.onSurface),
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
            onPressed: _loadOrders,
            child: const LocalizedText(
              'Thử lại',
              style: TextStyle(color: Colors.white),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildOrderCard(MasterOrderModel order) {
    final statusColor = _getStatusColor(order.status);
    final confirmedCount = order.subOrders
        .where((s) =>
            s.status == SubOrderStatus.confirmed ||
            s.status == SubOrderStatus.completed)
        .length;

    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        boxShadow: AppShapes.shadowLevel1,
        border: Border.all(color: AppColors.borderSubtle),
      ),
      padding: const EdgeInsets.all(AppShapes.spaceSm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Order Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: LocalizedText(
                        '#${order.id}',
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    InkWell(
                      onTap: () {
                        Clipboard.setData(ClipboardData(text: order.id));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: LocalizedText('Đã sao chép mã đơn hàng!'),
                            duration: Duration(seconds: 1),
                          ),
                        );
                      },
                      child: const Icon(Icons.copy,
                          size: 15, color: AppColors.secondary),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: LocalizedText(
                  _getStatusName(order.status),
                  style: AppTypography.labelSm(
                    color: statusColor,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          LocalizedText(
            'Ngày đặt: ${order.createdAt.day}/${order.createdAt.month}/${order.createdAt.year}',
            style: AppTypography.bodySm(color: AppColors.outline)
                .copyWith(fontSize: 11),
          ),
          const SizedBox(height: 10),

          // Activation Progress Bar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        'TIẾN TRÌNH KÍCH HOẠT',
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.labelSm(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.w700,
                        ).copyWith(fontSize: 10),
                      ),
                    ),
                    const SizedBox(width: 8),
                    LocalizedText(
                      '$confirmedCount / ${order.subOrders.length} sẵn sàng',
                      style: AppTypography.labelSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w700,
                      ).copyWith(fontSize: 10),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                LinearProgressIndicator(
                  value: order.subOrders.isNotEmpty
                      ? confirmedCount / order.subOrders.length
                      : 0,
                  backgroundColor: AppColors.surfaceContainerHighest,
                  color: AppColors.secondary,
                  minHeight: 4,
                  borderRadius: BorderRadius.circular(2),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),

          // Sub Orders
          ...order.subOrders.map((sub) {
            final isConfirmed = sub.status == SubOrderStatus.confirmed ||
                sub.status == SubOrderStatus.completed;
            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: isConfirmed
                    ? AppColors.surfaceContainerLow
                    : AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: SizedBox(
                      width: 50,
                      height: 50,
                      child: sub.serviceImageUrl.isNotEmpty
                          ? Image.network(
                              sub.serviceImageUrl,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(
                                color: AppColors.surfaceContainerHigh,
                                child: const Icon(
                                  Icons.waves,
                                  size: 20,
                                  color: AppColors.secondary,
                                ),
                              ),
                            )
                          : Container(
                              color: AppColors.surfaceContainerHigh,
                              child: const Icon(
                                Icons.waves,
                                size: 20,
                                color: AppColors.secondary,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
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
                          '${sub.vendorName} • ${sub.quantity} khách • ${sub.slotTime}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 11),
                        ),
                        const SizedBox(height: 2),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              isConfirmed
                                  ? Icons.check_circle
                                  : Icons.hourglass_top,
                              size: 12,
                              color: isConfirmed
                                  ? AppColors.secondary
                                  : AppColors.outline,
                            ),
                            const SizedBox(width: 4),
                            Flexible(
                              child: LocalizedText(
                                isConfirmed
                                    ? 'Đã xác nhận'
                                    : 'Chờ đối tác duyệt',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.labelSm(
                                  color: isConfirmed
                                      ? AppColors.secondary
                                      : AppColors.outline,
                                ).copyWith(fontSize: 10),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  if (isConfirmed)
                    IconButton(
                      icon: const Icon(Icons.qr_code_2,
                          color: AppColors.primary, size: 28),
                      tooltip: tr(context, 'Xem vé QR'),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => ETicketQrScreen(
                              subOrder: sub,
                              allSubOrders: order.subOrders,
                            ),
                          ),
                        );
                      },
                    ),
                ],
              ),
            );
          }),

          const Divider(height: 12),

          // Total & Actions
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: LocalizedText(
                  'Tổng thanh toán:',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 11),
                ),
              ),
              const SizedBox(width: 8),
              LocalizedText(
                _formatPrice(order.totalAmount),
                style: AppTypography.headlineSm(
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: Wrap(
              alignment: WrapAlignment.end,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: AppColors.borderSubtle),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 8),
                  ),
                  onPressed: () async {
                    await Navigator.push<void>(
                      context,
                      MaterialPageRoute(
                        builder: (_) =>
                            OrderDetailScreen(masterOrder: order),
                      ),
                    );
                    if (mounted) _loadOrders();
                  },
                  child: LocalizedText(
                    'Chi tiết đơn',
                    style: AppTypography.labelSm(color: AppColors.onSurface),
                  ),
                ),
                if (confirmedCount > 0)
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                    ),
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ETicketQrScreen(
                            subOrder: order.subOrders.first,
                            allSubOrders: order.subOrders,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.qr_code_2,
                        size: 16, color: Colors.white),
                    label: LocalizedText(
                      'Vé QR & Kích hoạt',
                      style: AppTypography.labelSm(color: Colors.white),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.receipt_long_outlined,
                  size: 36, color: AppColors.outline),
            ),
            const SizedBox(height: 14),
            LocalizedText(
              'Chưa có đơn hàng trong mục này',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 6),
            LocalizedText(
              'Các đơn đặt chỗ trải nghiệm biển của bạn sẽ được hiển thị và cập nhật theo trạng thái tại đây.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 16),
            if (widget.onExplore != null)
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.secondary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(18),
                  ),
                ),
                onPressed: widget.onExplore,
                child: const LocalizedText('Khám phá trải nghiệm biển'),
              ),
          ],
        ),
      ),
    );
  }
}
