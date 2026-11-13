import '../../../../core/data/vendor_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/widgets/partner_account_app_bar_button.dart';
import 'sub_order_detail_screen.dart';
import '../../../chat/presentation/screens/chat_conversation_screen.dart';

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
            // Search Bar
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: SizedBox(
                height: 44,
                child: TextField(
                  controller: _searchController,
                  textAlignVertical: TextAlignVertical.center,
                  onChanged: (val) => setState(() => _searchQuery = val.trim()),
                  style: const TextStyle(fontSize: 13.5, color: AppColors.onSurface),
                  decoration: InputDecoration(
                    isDense: true,
                    filled: true,
                    fillColor: Colors.white,
                    hintText: tr(context, 'Tìm theo mã đơn (#DNS...), tên khách hoặc dịch vụ...'),
                    hintStyle: const TextStyle(fontSize: 12.5, color: AppColors.outline),
                    prefixIcon: const Icon(Icons.search, size: 20, color: AppColors.tertiary),
                    suffixIcon: _searchQuery.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.cancel, size: 18, color: AppColors.tertiary),
                            splashRadius: 16,
                            onPressed: () {
                              _searchController.clear();
                              setState(() => _searchQuery = '');
                            },
                          )
                        : null,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 14),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(22),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(22),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(22),
                      borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),
                    ),
                  ),
                ),
              ),
            ),

            // Horizontal Filter Chips
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
              child: subOrders.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.receipt_long_outlined, size: 48, color: AppColors.tertiary),
                          const SizedBox(height: 8),
                          const LocalizedText('Không tìm thấy đơn hàng nào', style: TextStyle(color: AppColors.tertiary, fontSize: 14)),

                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
                      itemCount: subOrders.length,
                      itemBuilder: (context, index) {
                        final order = subOrders[index];
                        return _buildSubOrderCard(order);
                      },
                    ),
            ),
          ],
        ),
      ),
    );
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
              ? [BoxShadow(color: AppColors.secondary.withOpacity(0.2), blurRadius: 4, offset: const Offset(0, 2))]
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
                color: active ? AppColors.onSecondary.withOpacity(0.22) : AppColors.surfaceContainerLow,
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
                // 1. Order Code & Status Badge Header
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '#${order.subOrderCode}',
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: AppColors.navyAnchor,
                          ),
                        ),
                        if (order.masterOrderCode.isNotEmpty && order.masterOrderCode != order.subOrderCode) ...[
                          const SizedBox(width: 6),
                          Text(
                            '(${order.masterOrderCode})',
                            style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                          ),
                        ],
                      ],
                    ),
                    StatusBadge(status: order.status),
                  ],
                ),
                const SizedBox(height: 8),

                // 2. Service & Customer Information (Clean, high-density typography)
                Text(
                  order.serviceName,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.navyAnchor,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  '${order.customerName} • ${order.quantity} khách • ${order.slotDate} (${order.slotTime})',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.tertiary,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 10),
                const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                const SizedBox(height: 8),
                // 3. Check-in Status & Price Row
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
                        Text(
                          formattedPrice,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: AppColors.navyAnchor,
                          ),
                        ),
                        const SizedBox(width: 2),
                        const Text(
                          'đ',
                          style: TextStyle(fontSize: 11, color: AppColors.tertiary, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // 4. Action Buttons
                Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => ChatConversationScreen(
                                conversationId: 'conv-001',
                                customerName: order.customerName,
                                subOrderCode: order.subOrderCode,
                              ),
                            ),
                          );
                        },
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          height: 34,
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: AppColors.borderSubtle),
                          ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.chat_bubble_outline_rounded, size: 14, color: AppColors.navyAnchor),
                              SizedBox(width: 4),
                              Text('Nhắn tin', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.navyAnchor)),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: InkWell(
                        onTap: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => SubOrderDetailScreen(subOrder: order),
                            ),
                          );
                        },
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          height: 34,
                          decoration: BoxDecoration(
                            color: AppColors.secondary,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.receipt_long_rounded, size: 14, color: AppColors.onSecondary),
                              SizedBox(width: 4),
                              Text('Chi tiết đơn', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSecondary)),
                            ],
                          ),
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
    );
  }
}
