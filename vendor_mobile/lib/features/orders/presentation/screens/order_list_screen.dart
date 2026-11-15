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

  const OrderListScreen({
    super.key,
    this.onOpenNotifications,
    this.onOpenProfile,
  });

  @override
  State<OrderListScreen> createState() => _OrderListScreenState();
}

class _OrderListScreenState extends State<OrderListScreen> {
  final _db = VendorStore.instance;
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';
  String _selectedFilter = 'all'; // all, PENDING, CONFIRMED, COMPLETED, REJECTED, CANCELLED, REFUNDED
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
    final todayStr1 = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    final todayStr2 = '${now.day.toString().padLeft(2, '0')}/${now.month.toString().padLeft(2, '0')}/${now.year}';
    final isToday = o.slotDate.contains(todayStr1) ||
        o.slotDate.contains(todayStr2) ||
        (o.createdAt.year == now.year && o.createdAt.month == now.month && o.createdAt.day == now.day);

    if (shift == 'today') return isToday;

    int startHour = 8;
    final timeStr = o.slotTime.trim();
    final match = RegExp(r'^(\d{1,2})').firstMatch(timeStr);
    if (match != null) {
      startHour = int.tryParse(match.group(1)!) ?? 8;
    }

    if (shift == 'morning') {
      return startHour < 12;
    }
    if (shift == 'afternoon') {
      return startHour >= 12;
    }
    return true;
  }

  @override
  void initState() {
    super.initState();
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
      if (_selectedFilter != 'all' && o.status.name.toUpperCase() != _selectedFilter) return false;
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
    final morningCount = _db.subOrders.where((o) => _matchesShift(o, 'morning')).length;
    final afternoonCount = _db.subOrders.where((o) => _matchesShift(o, 'afternoon')).length;

    final pendingCount = _db.subOrders.where((o) => o.status == SubOrderStatus.pending).length;
    final confirmedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.confirmed).length;
    final completedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.completed).length;
    final rejectedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.rejected).length;
    final cancelledCount = _db.subOrders.where((o) => o.status == SubOrderStatus.cancelled).length;
    final refundedCount = _db.subOrders.where((o) => o.status == SubOrderStatus.refunded).length;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleSpacing: 16,
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
                            const Icon(Icons.access_time_rounded, size: 16, color: AppColors.tertiary),
                            const SizedBox(width: 8),
                            Text(
                              'Tất cả ca ($allCount)',
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
                        value: 'morning',
                        child: Row(
                          children: [
                            const Icon(Icons.wb_twilight_rounded, size: 16, color: Color(0xFFD97706)),
                            const SizedBox(width: 8),
                            Text(
                              'Ca sáng <12h ($morningCount)',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: _selectedShift == 'morning' ? FontWeight.bold : FontWeight.normal,
                                color: _selectedShift == 'morning' ? AppColors.secondary : AppColors.onSurface,
                              ),
                            ),
                          ],
                        ),
                      ),
                      PopupMenuItem(
                        value: 'afternoon',
                        child: Row(
                          children: [
                            const Icon(Icons.wb_sunny_rounded, size: 16, color: Color(0xFFEA580C)),
                            const SizedBox(width: 8),
                            Text(
                              'Ca chiều ≥12h ($afternoonCount)',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: _selectedShift == 'afternoon' ? FontWeight.bold : FontWeight.normal,
                                color: _selectedShift == 'afternoon' ? AppColors.secondary : AppColors.onSurface,
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
                  _buildFilterTab('CANCELLED', 'Đã hủy', cancelledCount),
                  const SizedBox(width: 8),
                  _buildFilterTab('REFUNDED', 'Đã hoàn tiền', refundedCount),
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
      case 'morning':
        return 'Ca sáng';
      case 'afternoon':
        return 'Ca chiều';
      case 'all':
      default:
        return 'Tất cả ca';
    }
  }

  IconData _getShiftIcon(String key) {
    switch (key) {
      case 'today':
        return Icons.today_rounded;
      case 'morning':
        return Icons.wb_twilight_rounded;
      case 'afternoon':
        return Icons.wb_sunny_rounded;
      case 'all':
      default:
        return Icons.access_time_rounded;
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
                    StatusBadge(status: order.status),
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

                // 3. Check-in Status & Total Price Row (Shopee style)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // Check-in badge
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: hasCheckedIn ? const Color(0xFFE8F5E9) : const Color(0xFFFFF8E1),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            hasCheckedIn ? Icons.check_circle_rounded : Icons.pending_rounded,
                            size: 12,
                            color: hasCheckedIn ? const Color(0xFF2E7D32) : const Color(0xFFE65100),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            hasCheckedIn ? 'Đã check-in bãi' : 'Chờ khách check-in',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: hasCheckedIn ? const Color(0xFF2E7D32) : const Color(0xFFE65100),
                            ),
                          ),
                        ],
                      ),
                    ),

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
                        '#${order.subOrderCode}',
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
