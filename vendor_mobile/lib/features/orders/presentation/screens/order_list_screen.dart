import '../../../../core/data/vendor_mock_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
import 'sub_order_detail_screen.dart';
import 'qr_checkin_screen.dart';
import '../../../chat/presentation/screens/chat_conversation_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';

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
  final _db = VendorMockDatabase.instance;
  String _searchQuery = '';
  String _selectedFilter = 'all'; // all, PENDING, CONFIRMED, COMPLETED, REJECTED, CANCELLED, REFUNDED

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    VendorOrderRepository().getSubOrders();
  }

  @override
  void dispose() {
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
        final q = _searchQuery.toLowerCase();
        final matchCode = o.subOrderCode.toLowerCase().contains(q) || o.masterOrderCode.toLowerCase().contains(q);
        final matchName = o.customerName.toLowerCase().contains(q);
        final matchService = o.serviceName.toLowerCase().contains(q);
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
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(
                  child: LocalizedText(
                    'DANASEA',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: AppColors.secondary,
                        ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const LocalizedText('VENDOR', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                ),
              ],
            ),
            const LocalizedText('Đơn dịch vụ', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.qr_code_scanner, color: AppColors.primary),
            tooltip: tr(context, 'Quét QR Check-in'),
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const QrCheckinScreen()),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.notifications_outlined, color: AppColors.secondary),
            onPressed: () {
              if (widget.onOpenNotifications != null) {
                widget.onOpenNotifications!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const NotificationSafetyScreen()),
                );
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.account_circle, color: AppColors.primary),
            onPressed: () {
              if (widget.onOpenProfile != null) {
                widget.onOpenProfile!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const BusinessProfileScreen()),
                );
              }
            },
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Header context
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(width: 6, height: 6, decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.primaryContainer)),
                            const SizedBox(width: 6),
                            const Flexible(
                              child: LocalizedText(
                                'KÊNH ĐỐI TÁC BIỂN',
                                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(10)),
                        child: LocalizedText('$allCount đơn ghi nhận', style: const TextStyle(fontSize: 10, color: AppColors.tertiary, fontWeight: FontWeight.bold)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const LocalizedText('Quản lý đơn dịch vụ', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                  const SizedBox(height: 10),

                  // Search input
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.search, size: 20, color: AppColors.tertiary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            onChanged: (val) => setState(() => _searchQuery = val.trim()),
                            decoration:  InputDecoration(
                              hintText: tr(context, 'Tìm theo mã đơn con, tên khách hoặc dịch vụ...'),
                              hintStyle: TextStyle(fontSize: 12, color: AppColors.outline),
                              border: InputBorder.none,
                            ),
                            style: const TextStyle(fontSize: 13),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
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

            // Ocean safety notice strip
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: AppColors.secondaryFixed.withOpacity(0.4),
                  borderRadius: AppShapes.radiusSm,
                ),
                child: const Row(
                  children: [
                    Icon(Icons.waves, size: 16, color: AppColors.secondary),
                    SizedBox(width: 8),
                    Expanded(
                      child: LocalizedText(
                        'Sóng 0.4m • Gió 9 km/h • Khung giờ 05:00 lý tưởng cho hoạt động SUP',
                        style: TextStyle(fontSize: 11, color: AppColors.onSecondaryFixedVariant),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),

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
                          if (_searchQuery.isNotEmpty || _selectedFilter != 'all')
                            TextButton(
                              onPressed: () => setState(() {
                                _searchQuery = '';
                                _selectedFilter = 'all';
                              }),
                              child: const LocalizedText('Xóa bộ lọc'),
                            ),
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
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.primaryContainer : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          boxShadow: active ? AppShapes.shadowSm : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: active ? FontWeight.bold : FontWeight.normal,
                color: active ? AppColors.onPrimary : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
              decoration: BoxDecoration(
                color: active ? AppColors.onPrimary.withOpacity(0.2) : AppColors.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: LocalizedText(
                '$count',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: active ? AppColors.onPrimary : AppColors.tertiary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSubOrderCard(SubOrderModel order) {
    final hasCheckedIn = order.checkedInAt != null;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => SubOrderDetailScreen(subOrder: order),
            ),
          );
        },
        borderRadius: AppShapes.radiusMd,
        child: Container(
          margin: const EdgeInsets.only(bottom: 12),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: AppShapes.radiusMd,
            boxShadow: AppShapes.shadowSm,
          ),
          child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Codes & Status header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: LocalizedText(
                        '#${order.subOrderCode}',
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: LocalizedText(
                        '(${order.masterOrderCode})',
                        style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
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
          const SizedBox(height: 10),

          // Customer info & service
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Center(
                  child: Icon(Icons.surfing, color: AppColors.secondary, size: 24),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LocalizedText(
                      order.serviceName,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.person, size: 13, color: AppColors.tertiary),
                        const SizedBox(width: 3),
                        Flexible(
                          child: LocalizedText(
                            order.customerName,
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const LocalizedText(' • ', style: TextStyle(color: AppColors.tertiary)),
                        LocalizedText('${order.quantity} khách', style: const TextStyle(fontSize: 12, color: AppColors.tertiary)),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.schedule, size: 13, color: AppColors.secondary),
                        const SizedBox(width: 3),
                        Expanded(
                          child: LocalizedText(
                            '${order.slotDate} • ${order.slotTime}',
                            style: const TextStyle(fontSize: 11, color: AppColors.secondary, fontWeight: FontWeight.bold),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Check-in & Financial Snapshot Bar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: hasCheckedIn ? AppColors.secondary : AppColors.outlineVariant,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: LocalizedText(
                          hasCheckedIn ? 'Đã check-in bãi' : 'Chưa check-in',
                          style: TextStyle(
                            fontSize: 11,
                            color: hasCheckedIn ? AppColors.secondary : AppColors.tertiary,
                            fontWeight: hasCheckedIn ? FontWeight.bold : FontWeight.normal,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    LocalizedText(
                      '${order.quantity}× ',
                      style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                    ),
                    LocalizedText(
                      '${order.totalPrice.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.primary),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),

          // Actions
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {
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
                  icon: const Icon(Icons.chat_bubble_outline, size: 14),
                  label: const LocalizedText('Nhắn tin', style: TextStyle(fontSize: 12)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.onSurface,
                    side: const BorderSide(color: AppColors.outlineVariant),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => SubOrderDetailScreen(subOrder: order),
                      ),
                    );
                  },
                  icon: const Icon(Icons.receipt_long, size: 14),
                  label: const LocalizedText('Chi tiết đơn', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primaryContainer,
                    foregroundColor: AppColors.onPrimary,
                    elevation: 0,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    ),
  ),
);
  }
}
