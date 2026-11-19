import '../../../../core/data/vendor_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/widgets/partner_account_app_bar_button.dart';
import 'sub_order_detail_screen.dart';

class OrderListScreen extends StatefulWidget {
  final VoidCallback? onOpenNotifications;
  final VoidCallback? onOpenProfile;
  final String? initialFilter;

  const OrderListScreen({
    super.key,
    this.onOpenNotifications,
    this.onOpenProfile,
    this.initialFilter,
  });

  @override
  State<OrderListScreen> createState() => _OrderListScreenState();
}

class _OrderListScreenState extends State<OrderListScreen> {
  final _db = VendorStore.instance;
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';
  late String _selectedFilter; // all, PENDING, CONFIRMED, COMPLETED, REJECTED, CANCELLED
  String _selectedShift = 'all'; // all, today, morning, afternoon

  String _normalize(String s) {
    var str = s.toLowerCase();
    str = str.replaceAll(RegExp(r'[àáạảãâầấậẩẫăằắặẳẵ]'), 'a');
    str = str.replaceAll(RegExp(r'[èéẹẻẽêềếệểễ]'), 'e');
    str = str.replaceAll(RegExp(r'[ìíịỉĩ]'), 'i');
    str = str.replaceAll(RegExp(r'[òóọỏõôồốộổỗơờớợởỡ]'), 'o');
    str = str.replaceAll(RegExp(r'[ùúụủũưừứựửữ]'), 'u');
    str = str.replaceAll(RegExp(r'[ỳýỵỷỹ]'), 'y');
    str = str.replaceAll(RegExp(r'[đ]'), 'd');
    return str;
  }

  bool _matchesShift(SubOrderModel o, String shift) {
    if (shift == 'all') return true;
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    DateTime? orderDate;
    if (o.slotDate.isNotEmpty) {
      orderDate = DateTime.tryParse(o.slotDate);
      if (orderDate == null && o.slotDate.contains('/')) {
        final parts = o.slotDate.split('/');
        if (parts.length == 3) {
          final d = int.tryParse(parts[0]);
          final m = int.tryParse(parts[1]);
          final y = int.tryParse(parts[2]);
          if (d != null && m != null && y != null) {
            orderDate = DateTime(y, m, d);
          }
        }
      }
    }
    orderDate ??= DateTime(o.createdAt.year, o.createdAt.month, o.createdAt.day);
    final orderDay = DateTime(orderDate.year, orderDate.month, orderDate.day);

    if (shift == 'today') {
      return orderDay == today;
    }
    if (shift == 'tomorrow') {
      final tomorrow = today.add(const Duration(days: 1));
      return orderDay == tomorrow;
    }
    if (shift == 'week') {
      final in7Days = today.add(const Duration(days: 7));
      return !orderDay.isBefore(today) && !orderDay.isAfter(in7Days);
    }
    return true;
  }

  String _formatShortCode(String code) {
    var c = code.trim();
    if (c.startsWith('#')) c = c.substring(1);
    if (c.isEmpty) return '';
    // Nếu mã ngắn (dưới hoặc bằng 16 ký tự): Hiển thị đầy đủ 100% không cắt bớt
    if (c.length <= 16) {
      return '#$c';
    }
    // Nếu là mã UUID dài (> 16 ký tự): Hiển thị đầu - cuối rõ ràng (#00020000...0040)
    if (c.contains('-')) {
      final parts = c.split('-');
      final first = parts.first;
      final last = parts.last;
      final suffix = last.length > 4 ? last.substring(last.length - 4) : last;
      return '#$first...$suffix';
    }
    return '#${c.substring(0, 8)}...${c.substring(c.length - 4)}';
  }

  @override
  void initState() {
    super.initState();
    _selectedFilter = (widget.initialFilter != null && widget.initialFilter!.isNotEmpty)
        ? widget.initialFilter!.toUpperCase()
        : 'all';
    _db.addListener(_onDbChanged);
    VendorOrderRepository().getSubOrders();
  }

  @override
  void dispose() {
    _searchController.dispose();
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final subOrders = _db.subOrders.where((o) {
      if (_selectedFilter != 'all') {
        if (_selectedFilter == 'CANCELLED') {
          if (o.status != SubOrderStatus.cancelled && o.status != SubOrderStatus.refunded) return false;
        } else if (o.status.name.toUpperCase() != _selectedFilter) {
          return false;
        }
      }
      if (!_matchesShift(o, _selectedShift)) return false;
      if (_searchQuery.isNotEmpty) {
        final rawQ = _searchQuery.trim().toLowerCase();
        final cleanQ = rawQ.replaceAll('#', '').trim();
        final normQ = _normalize(cleanQ);

        final subCode = o.subOrderCode.toLowerCase();
        final masterCode = o.masterOrderCode.toLowerCase();
        final custName = _normalize(o.customerName);
        final serviceName = _normalize(o.serviceName);

        final matchCode = subCode.contains(cleanQ) || masterCode.contains(cleanQ);
        final matchName = custName.contains(normQ) || o.customerName.toLowerCase().contains(rawQ);
        final matchService = serviceName.contains(normQ) || o.serviceName.toLowerCase().contains(rawQ);
        return matchCode || matchName || matchService;
      }
      return true;
    }).toList();

    final allCount = _db.subOrders.length;
    final todayCount = _db.subOrders.where((o) => _matchesShift(o, 'today')).length;
    final tomorrowCount = _db.subOrders.where((o) => _matchesShift(o, 'tomorrow')).length;
    final weekCount = _db.subOrders.where((o) => _matchesShift(o, 'week')).length;

    final pendingCount = _db.subOrders.where((o) => o.status == SubOrderStatus.pending).length;
    final confirmedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.confirmed).length;
    final completedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.completed).length;
    final rejectedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.rejected).length;
    final cancelledCount = _db.subOrders.where((o) => o.status == SubOrderStatus.cancelled || o.status == SubOrderStatus.refunded).length;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: Navigator.canPop(context)
            ? IconButton(
                icon: const Icon(Icons.arrow_back_ios_new_rounded, color: AppColors.navyAnchor, size: 20),
                onPressed: () => Navigator.pop(context),
              )
            : null,
        titleSpacing: Navigator.canPop(context) ? 0 : 16,
        title: const LocalizedText(
          'Quản lý đơn hàng',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.navyAnchor,
          ),
        ),
        actions: [
          PartnerAccountAppBarButton(onPressed: widget.onOpenProfile),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Search Bar + Shift Filter Dropdown (Shopee / GrabMerchant Style)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 42,
                      child: TextField(
                        controller: _searchController,
                        textAlignVertical: TextAlignVertical.center,
                        onChanged: (val) => setState(() => _searchQuery = val.trim()),
                        style: const TextStyle(fontSize: 13, color: AppColors.onSurface),
                        decoration: InputDecoration(
                          isDense: true,
                          filled: true,
                          fillColor: Colors.white,
                          hintText: tr(context, 'Tìm theo mã đơn, khách, tour...'),
                          hintStyle: const TextStyle(fontSize: 12, color: AppColors.outline),
                          prefixIcon: const Icon(Icons.search, size: 19, color: AppColors.tertiary),
                          suffixIcon: _searchQuery.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.cancel, size: 17, color: AppColors.tertiary),
                                  splashRadius: 16,
                                  onPressed: () {
                                    _searchController.clear();
                                    setState(() => _searchQuery = '');
                                  },
                                )
                              : null,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(20),
                            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(20),
                            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(20),
                            borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Shift Filter Popup Menu (Gọn gàng, không bị 2 hàng pill đè nhau)
                  PopupMenuButton<String>(
                    initialValue: _selectedShift,
                    tooltip: 'Lọc ca chạy',
                    onSelected: (val) => setState(() => _selectedShift = val),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    elevation: 4,
                    color: Colors.white,
                    itemBuilder: (ctx) => [
                          PopupMenuItem(
                            value: 'all',
                            child: Row(
                              children: [
                                const Icon(Icons.schedule_rounded, size: 16, color: AppColors.secondary),
                                const SizedBox(width: 8),
                                Text(
                                  'Tất cả ($allCount)',
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: _selectedShift == 'all' ? FontWeight.bold : FontWeight.normal,
                                    color: _selectedShift == 'all' ? AppColors.secondary : AppColors.onSurface,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          PopupMenuItem(
                            value: 'today',
                            child: Row(
                              children: [
                                const Icon(Icons.today_rounded, size: 16, color: Color(0xFF0F766E)),
                                const SizedBox(width: 8),
                                Text(
                                  'Hôm nay ($todayCount)',
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: _selectedShift == 'today' ? FontWeight.bold : FontWeight.normal,
                                    color: _selectedShift == 'today' ? AppColors.secondary : AppColors.onSurface,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          PopupMenuItem(
                            value: 'tomorrow',
                            child: Row(
                              children: [
                                const Icon(Icons.wb_sunny_rounded, size: 16, color: Color(0xFFD97706)),
                                const SizedBox(width: 8),
                                Text(
                                  'Ngày mai ($tomorrowCount)',
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: _selectedShift == 'tomorrow' ? FontWeight.bold : FontWeight.normal,
                                    color: _selectedShift == 'tomorrow' ? AppColors.secondary : AppColors.onSurface,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          PopupMenuItem(
                            value: 'week',
                            child: Row(
                              children: [
                                const Icon(Icons.date_range_rounded, size: 16, color: Color(0xFF0284C7)),
                                const SizedBox(width: 8),
                                Text(
                                  'Tuần này ($weekCount)',
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: _selectedShift == 'week' ? FontWeight.bold : FontWeight.normal,
                                    color: _selectedShift == 'week' ? AppColors.secondary : AppColors.onSurface,
                                  ),
                                ),
                              ],
                            ),
                          ),
                    ],
                    child: Container(
                      height: 42,
                      padding: const EdgeInsets.symmetric(horizontal: 11),
                      decoration: BoxDecoration(
                        color: _selectedShift != 'all'
                            ? AppColors.secondary.withValues(alpha: 0.1)
                            : Colors.white,
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                          color: _selectedShift != 'all'
                              ? AppColors.secondary
                              : const Color(0xFFE2E8F0),
                          width: _selectedShift != 'all' ? 1.5 : 1.0,
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            _getShiftIcon(_selectedShift),
                            size: 15,
                            color: _selectedShift != 'all' ? AppColors.secondary : AppColors.tertiary,
                          ),
                          const SizedBox(width: 5),
                          Text(
                            _getShiftLabel(_selectedShift),
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: _selectedShift != 'all' ? FontWeight.bold : FontWeight.w600,
                              color: _selectedShift != 'all' ? AppColors.secondary : AppColors.onSurface,
                            ),
                          ),
                          const SizedBox(width: 2),
                          Icon(
                            Icons.keyboard_arrow_down_rounded,
                            size: 16,
                            color: _selectedShift != 'all' ? AppColors.secondary : AppColors.tertiary,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Horizontal Status Filter Chips (Duy nhất 1 hàng tab trạng thái)
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
              child: Row(
                children: [
                  _buildFilterTab('all', 'Tất cả', allCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('PENDING', 'Chờ duyệt', pendingCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('CONFIRMED', 'Đã xác nhận', confirmedCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('COMPLETED', 'Đã hoàn thành', completedCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('REJECTED', 'Đã từ chối', rejectedCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('CANCELLED', 'Hủy / Hoàn', cancelledCount),
                ],
              ),
            ),
            const SizedBox(height: 4),

            // Sub-Orders List
            Expanded(
              child: RefreshIndicator(
                onRefresh: () async {
                  await VendorOrderRepository().getSubOrders();
                  if (mounted) setState(() {});
                },
                child: subOrders.isEmpty
                    ? ListView(
                        children: const [
                          SizedBox(height: 120),
                          Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(Icons.receipt_long_outlined, size: 48, color: AppColors.tertiary),
                                SizedBox(height: 8),
                                LocalizedText('Không tìm thấy đơn hàng nào', style: TextStyle(color: AppColors.tertiary, fontSize: 14)),
                              ],
                            ),
                          ),
                        ],
                      )
                    : ListView.builder(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.fromLTRB(16.0, 6.0, 16.0, 110.0),
                        itemCount: subOrders.length,
                        itemBuilder: (context, index) {
                          final order = subOrders[index];
                          return _buildSubOrderCard(order);
                        },
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _getShiftLabel(String key) {
    switch (key) {
      case 'today':
        return 'Hôm nay';
      case 'tomorrow':
        return 'Ngày mai';
      case 'week':
        return 'Tuần này';
      case 'all':
      default:
        return 'Tất cả';
    }
  }

  IconData _getShiftIcon(String key) {
    switch (key) {
      case 'today':
        return Icons.today_rounded;
      case 'tomorrow':
        return Icons.wb_sunny_rounded;
      case 'week':
        return Icons.date_range_rounded;
      case 'all':
      default:
        return Icons.schedule_rounded;
    }
  }

  Widget _buildFilterTab(String key, String label, int count) {
    final active = _selectedFilter == key;
    return InkWell(
      onTap: () => setState(() => _selectedFilter = key),
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.secondary : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: active ? AppColors.secondary : AppColors.borderSubtle,
          ),
          boxShadow: active
              ? [BoxShadow(color: AppColors.secondary.withValues(alpha: 0.2), blurRadius: 4, offset: const Offset(0, 2))]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: active ? FontWeight.bold : FontWeight.w500,
                color: active ? AppColors.onSecondary : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
              decoration: BoxDecoration(
                color: active ? AppColors.onSecondary.withValues(alpha: 0.22) : AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(8),
              ),
              child: LocalizedText(
                '$count',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: active ? AppColors.onSecondary : AppColors.tertiary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOrderCardTopBadge(SubOrderModel order, bool hasCheckedIn) {
    if (order.status == SubOrderStatus.confirmed) {
      if (hasCheckedIn) {
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: const Color(0xFFE8F5E9),
            borderRadius: BorderRadius.circular(6),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.check_circle_rounded, size: 12, color: Color(0xFF2E7D32)),
              SizedBox(width: 4),
              Text(
                'Đã check-in',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF2E7D32),
                ),
              ),
            ],
          ),
        );
      } else {
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: const Color(0xFFFFF8E1),
            borderRadius: BorderRadius.circular(6),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.pending_rounded, size: 12, color: Color(0xFFE65100)),
              SizedBox(width: 4),
              Text(
                'Chờ check-in',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFFE65100),
                ),
              ),
            ],
          ),
        );
      }
    }
    return StatusBadge(status: order.status);
  }

  Widget _buildSubOrderCard(SubOrderModel order) {
    final bool hasCheckedIn = order.checkedInAt != null;
    final formattedPrice = order.totalPrice.toStringAsFixed(0).replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (Match m) => '${m[1]}.',
    );

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.borderSubtle),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => SubOrderDetailScreen(subOrder: order),
              ),
            );
          },
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. Top row: Customer avatar + Customer name (left) & StatusBadge (right)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          Container(
                            width: 24,
                            height: 24,
                            decoration: const BoxDecoration(
                              color: AppColors.surfaceContainerHigh,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(Icons.person, size: 15, color: AppColors.tertiary),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              order.customerName.isNotEmpty ? order.customerName : 'Khách hàng',
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: AppColors.navyAnchor,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    _buildOrderCardTopBadge(order, hasCheckedIn),
                  ],
                ),
                const SizedBox(height: 8),
                const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                const SizedBox(height: 8),

                // 2. Service & Tour Information
                Text(
                  order.serviceName,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.navyAnchor,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  '${order.quantity} khách • ${order.slotDate} (${order.slotTime})',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.tertiary,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 8),

                // 3. Total Price Row (Shopee style, thoáng mắt, không nhãn thừa)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    if (hasCheckedIn && order.checkedInAt != null)
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.access_time_rounded, size: 12, color: Color(0xFF2E7D32)),
                          const SizedBox(width: 4),
                          Text(
                            'Check-in: ${order.checkedInAt!.hour.toString().padLeft(2, '0')}:${order.checkedInAt!.minute.toString().padLeft(2, '0')}',
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF2E7D32),
                            ),
                          ),
                        ],
                      )
                    else
                      const SizedBox.shrink(),

                    // Total price
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        const Text(
                          'Tổng thanh toán: ',
                          style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                        ),
                        Text(
                          '₫$formattedPrice',
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: AppColors.navyAnchor,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 10),

                // 4. Action button (Right aligned, Chi tiết đơn only)
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Container(
                      height: 32,
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.receipt_long_rounded, size: 14, color: AppColors.onSecondary),
                          SizedBox(width: 4),
                          Text(
                            'Chi tiết đơn',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSecondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                const SizedBox(height: 8),

                // 5. Bottom: "Mã đơn hàng" on left, "#<subOrderCode>" on right (Shopee style)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Mã đơn hàng',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.tertiary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        _formatShortCode(order.subOrderCode),
                        textAlign: TextAlign.right,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: AppColors.navyAnchor,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
