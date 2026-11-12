import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../settlements/presentation/screens/settlement_revenue_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';
import '../../../profile/presentation/screens/edit_profile_bank_screen.dart';
import '../../../orders/presentation/screens/order_list_screen.dart';
import '../../../slots/presentation/screens/slot_calendar_screen.dart';
import '../../../reviews/presentation/screens/review_feedback_screen.dart';
import '../../../disputes/presentation/screens/dispute_management_screen.dart';
import '../../../orders/presentation/screens/qr_checkin_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';

class DashboardScreen extends StatefulWidget {
  final Function(int)? onNavigateTab;
  final VoidCallback? onOpenQrScan;
  final VoidCallback? onOpenNotifications;
  final VoidCallback? onOpenChat;
  final VoidCallback? onOpenSettlements;
  final VoidCallback? onOpenProfile;

  const DashboardScreen({
    super.key,
    this.onNavigateTab,
    this.onOpenQrScan,
    this.onOpenNotifications,
    this.onOpenChat,
    this.onOpenSettlements,
    this.onOpenProfile,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final _db = VendorStore.instance;
  final _dashboardRepo = VendorDashboardRepository();
  VendorDashboardModel _metrics = const VendorDashboardModel();
  bool _isBalanceVisible = true;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    _loadDashboardData();
  }

  Future<void> _loadDashboardData() async {
    try {
      final metrics = await _dashboardRepo.getDashboardMetrics();
      if (mounted) {
        setState(() {
          _metrics = metrics;
          _isLoading = false;
        });
      }
      await Future.wait([
        VendorProfileRepository().getVendorProfile().catchError((_) => _db.currentVendor),
        VendorOrderRepository().getSubOrders().catchError((_) => _db.subOrders),
        VendorServiceRepository().getServices().catchError((_) => _db.services),
        VendorSettlementRepository().getSettlements().catchError((_) => _db.settlements),
      ]);
      if (_db.services.isNotEmpty) {
        await VendorSlotRepository().getSlotsForService(_db.services.first.serviceId, DateTime.now()).catchError((_) => <ServiceSlotModel>[]);
      }
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  String _formatVnd(double amount) {
    final intVal = amount.toInt();
    final str = intVal.toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i != 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  void _openQrScan() {
    if (widget.onOpenQrScan != null) {
      widget.onOpenQrScan!();
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const QrCheckinScreen()));
    }
  }

  void _openNotifications() {
    if (widget.onOpenNotifications != null) {
      widget.onOpenNotifications!();
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationSafetyScreen()));
    }
  }

  void _openSettlements() {
    if (widget.onOpenSettlements != null) {
      widget.onOpenSettlements!();
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettlementRevenueScreen()));
    }
  }

  void _openProfile() {
    if (widget.onOpenProfile != null) {
      widget.onOpenProfile!();
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const BusinessProfileScreen()));
    }
  }

  void _openSlots() {
    if (widget.onNavigateTab != null) {
      widget.onNavigateTab!(2);
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SlotCalendarScreen()));
    }
  }

  void _openOrders() {
    if (widget.onNavigateTab != null) {
      widget.onNavigateTab!(3);
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const OrderListScreen()));
    }
  }

  void _openReviews() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ReviewFeedbackScreen()));
  }

  void _openDisputes() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const DisputeManagementScreen()));
  }

  @override
  Widget build(BuildContext context) {
    final vendor = _db.vendor;
    final subOrders = _db.subOrders;
    final slots = _db.slots;

    final confirmedCount = subOrders.where((o) => o.status == SubOrderStatus.confirmed).length;
    final pendingCount = subOrders.where((o) => o.status == SubOrderStatus.pending).length;
    final cancelledCount = subOrders.where((o) => o.status == SubOrderStatus.cancelled || o.status == SubOrderStatus.refunded).length;
    final reviewCount = _db.reviews.length;

    // Next upcoming slot
    final nextSlot = slots.isNotEmpty ? slots.first : null;
    final nextService = nextSlot != null && _db.services.isNotEmpty
        ? _db.services.firstWhere((s) => s.serviceId == nextSlot.serviceId, orElse: () => _db.services.first)
        : null;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        automaticallyImplyLeading: false,
        titleSpacing: 16,
        title: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: const BoxDecoration(
                color: Color(0xFF004D56),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.waves,
                color: Colors.white,
                size: 22,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                vendor.businessName.isNotEmpty ? vendor.businessName : 'Danabeach Club',
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.bold,
                  color: AppColors.onSurface,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        actions: [
          Stack(
            alignment: Alignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_none_rounded, color: Color(0xFF004D56), size: 24),
                onPressed: _openNotifications,
              ),
              Positioned(
                top: 10,
                right: 12,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Color(0xFFF97316),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(right: 16, left: 4),
            child: InkWell(
              onTap: _openProfile,
              borderRadius: BorderRadius.circular(20),
              child: const CircleAvatar(
                radius: 18,
                backgroundColor: Color(0xFFB91C1C),
                child: Icon(Icons.person, color: Colors.white, size: 22),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await _loadDashboardData();
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildVerificationBanner(vendor),

                // 1. HERO DARK TEAL CARD: DANASEA PAY (Style Ảnh 1 + Chi tiết Ảnh 2)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF004D56),
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x18004D56),
                        blurRadius: 12,
                        offset: Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Header Row: Logo/Icon + DANASEA PAY + Toggle eye + Chi tiết ví
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(5),
                                decoration: BoxDecoration(
                                  color: Colors.white.withValues(alpha: 0.15),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: const Icon(
                                  Icons.account_balance_wallet_rounded,
                                  color: Color(0xFF7DD3FC),
                                  size: 16,
                                ),
                              ),
                              const SizedBox(width: 8),
                              const Text(
                                'DANASEA PAY',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                  letterSpacing: 0.8,
                                ),
                              ),
                              const SizedBox(width: 6),
                              InkWell(
                                onTap: () => setState(() => _isBalanceVisible = !_isBalanceVisible),
                                borderRadius: BorderRadius.circular(20),
                                child: Padding(
                                  padding: const EdgeInsets.all(4),
                                  child: Icon(
                                    _isBalanceVisible ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                    size: 17,
                                    color: Colors.white70,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          InkWell(
                            onTap: _openSettlements,
                            borderRadius: BorderRadius.circular(20),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: const Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    'Chi tiết ví',
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      color: Colors.white,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                  SizedBox(width: 2),
                                  Icon(Icons.chevron_right, size: 14, color: Colors.white),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      // Main Balance
                      Text(
                        _isBalanceVisible ? _formatVnd(_metrics.totalRevenue) : '••••••••',
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 12),

                      // Detailed Settlement Sub-bar (Đã quyết toán & Chờ đối soát)
                      Container(
                        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 10),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.22),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.check_circle_rounded, size: 13, color: Color(0xFF34D399)),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                _isBalanceVisible ? 'Đã quyết toán: ' + _formatVnd(_metrics.netPayout) : 'Đã quyết toán: ••••',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: Color(0xFFE2E8F0),
                                  fontWeight: FontWeight.w500,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Container(width: 1, height: 16, color: Colors.white24),
                            const SizedBox(width: 8),
                            const Icon(Icons.schedule_rounded, size: 13, color: Color(0xFFFBBF24)),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                _isBalanceVisible ? 'Chờ đối soát: ' + _formatVnd((_metrics.totalRevenue - _metrics.netPayout) > 0 ? (_metrics.totalRevenue - _metrics.netPayout) : 0.0) : 'Chờ đối soát: ••••',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: Color(0xFFFDE68A),
                                  fontWeight: FontWeight.bold,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),

                      // Linked Bank Account Chip
                      InkWell(
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const EditProfileBankScreen()),
                          );
                        },
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.10),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.account_balance_rounded, size: 14, color: Colors.white70),
                              const SizedBox(width: 6),
                              Expanded(
                                child: Text(
                                  'Tài khoản nhận tiền: ${vendor.bankName != null && vendor.bankName!.isNotEmpty ? vendor.bankName : 'Chưa liên kết'} (••••${(vendor.bankAccountNumber ?? '').length > 4 ? vendor.bankAccountNumber!.substring(vendor.bankAccountNumber!.length - 4) : (vendor.bankAccountNumber ?? 'Chưa có')})',
                                  style: const TextStyle(
                                    fontSize: 11,
                                    color: Colors.white,
                                    fontWeight: FontWeight.w500,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const Icon(Icons.chevron_right, size: 14, color: Colors.white60),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // 2. VIỆC CẦN XỬ LÝ (From Ảnh 2 in Clean White Card Style)
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFECEFF1)),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x06000000),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Việc cần xử lý',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: _buildToDoItem(
                              count: '$confirmedCount',
                              label: 'Chờ check-in',
                              countColor: const Color(0xFFD97706),
                              onTap: _openOrders,
                            ),
                          ),
                          Container(width: 1, height: 26, color: const Color(0xFFE2E8F0)),
                          Expanded(
                            child: _buildToDoItem(
                              count: '${_metrics.newOrders > 0 ? _metrics.newOrders : pendingCount}',
                              label: 'Đơn mới',
                              countColor: const Color(0xFF0284C7),
                              onTap: _openOrders,
                            ),
                          ),
                          Container(width: 1, height: 26, color: const Color(0xFFE2E8F0)),
                          Expanded(
                            child: _buildToDoItem(
                              count: '$cancelledCount',
                              label: 'Đơn hủy / Hoàn',
                              countColor: const Color(0xFFDC2626),
                              onTap: _openDisputes,
                            ),
                          ),
                          Container(width: 1, height: 26, color: const Color(0xFFE2E8F0)),
                          Expanded(
                            child: _buildToDoItem(
                              count: '$reviewCount',
                              label: 'Đánh giá mới',
                              countColor: const Color(0xFF7C3AED),
                              onTap: _openReviews,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // 3. THAO TÁC VẬN HÀNH (From Ảnh 1: 2x2 Grid, Clean & Bold)
                const Text(
                  'Thao tác vận hành',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: _buildActionTile(
                        icon: Icons.qr_code_scanner,
                        iconBg: const Color(0xFFFEE2E2),
                        iconColor: const Color(0xFFDC2626),
                        title: 'Quét vé QR',
                        onTap: _openQrScan,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _buildActionTile(
                        icon: Icons.calendar_month_outlined,
                        iconBg: const Color(0xFFCCFBF1),
                        iconColor: const Color(0xFF0F766E),
                        title: 'Lịch ca chạy',
                        onTap: _openSlots,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: _buildActionTile(
                        icon: Icons.account_balance_wallet_outlined,
                        iconBg: const Color(0xFFFEF3C7),
                        iconColor: const Color(0xFFD97706),
                        title: 'Ví doanh thu',
                        onTap: _openSettlements,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _buildActionTile(
                        icon: Icons.star_outline_rounded,
                        iconBg: const Color(0xFFEDE9FE),
                        iconColor: const Color(0xFF7C3AED),
                        title: 'Đánh giá',
                        onTap: _openReviews,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),

                // 4. CHUYẾN XUẤT BẾN GẦN NHẤT (From Ảnh 1)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Chuyến xuất bến gần nhất',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                    InkWell(
                      onTap: _openSlots,
                      child: const Text(
                        'Xem lịch',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.secondary,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFECEFF1)),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x06000000),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: const Color(0xFFBAE6FD),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Icon(Icons.surfing_rounded, color: Color(0xFF0284C7), size: 22),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(
                                      nextSlot != null ? '${nextSlot.startTime.length >= 5 ? nextSlot.startTime.substring(0, 5) : nextSlot.startTime} - ${nextSlot.endTime.length >= 5 ? nextSlot.endTime.substring(0, 5) : nextSlot.endTime}' : '--:--',
                                      style: const TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                        color: AppColors.secondary,
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: const Color(0xFFE0F2FE),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: const Text(
                                        'Chuẩn bị đón khách',
                                        style: TextStyle(
                                          fontSize: 10,
                                          fontWeight: FontWeight.bold,
                                          color: Color(0xFF0369A1),
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  nextService?.nameVi ?? (nextSlot != null ? 'Ca chạy đã lên lịch' : 'Chưa có ca chạy hôm nay'),
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.onSurface,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            'Sức chứa khung giờ',
                            style: TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                          ),
                          Text(
                            nextSlot != null ? '${nextSlot.bookedCount}/${nextSlot.capacity} khách' : '0/0 khách',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: (nextSlot?.capacity ?? 10) > 0
                              ? ((nextSlot?.bookedCount ?? 8) / (nextSlot?.capacity ?? 10)).clamp(0.0, 1.0)
                              : 0.8,
                          minHeight: 5,
                          backgroundColor: const Color(0xFFF1F5F9),
                          valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF004D56)),
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: _openOrders,
                          icon: const Icon(Icons.people_outline, size: 16),
                          label: const Text(
                            'Xem danh sách khách của ca này',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                          ),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: const Color(0xFF004D56),
                            side: const BorderSide(color: Color(0xFF004D56)),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            padding: const EdgeInsets.symmetric(vertical: 10),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 100),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildToDoItem({
    required String count,
    required String label,
    required Color countColor,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          children: [
            Text(
              count,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: countColor,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: const TextStyle(
                fontSize: 10.5,
                color: Color(0xFF64748B),
                fontWeight: FontWeight.w500,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required Color iconBg,
    required Color iconColor,
    required String title,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFECEFF1)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x06000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: iconBg,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: iconColor, size: 22),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: 13.5,
                  fontWeight: FontWeight.bold,
                  color: AppColors.onSurface,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildVerificationBanner(VendorModel vendor) {
    final isRejected = vendor.verificationStatus == VendorVerificationStatus.rejected;
    final isPending = vendor.verificationStatus == VendorVerificationStatus.pending;

    if (!isRejected && !isPending) {
      return const SizedBox.shrink();
    }

    if (isRejected) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.errorContainer,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.error.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded, color: AppColors.error, size: 22),
            const SizedBox(width: 10),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Hồ sơ giấy tờ bị từ chối',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppColors.onErrorContainer),
                  ),
                  SizedBox(height: 2),
                  Text(
                    'Cần bổ sung/cập nhật lại giấy tờ để mở khóa dịch vụ.',
                    style: TextStyle(fontSize: 11, color: AppColors.onErrorContainer),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            ElevatedButton(
              onPressed: _openProfile,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.error,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                elevation: 0,
              ),
              child: const Text('Bổ sung', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      );
    }

    if (isPending) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFFFFF8E1),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFFFFE082)),
        ),
        child: Row(
          children: [
            const Icon(Icons.pending_actions_rounded, color: Color(0xFFF57F17), size: 22),
            const SizedBox(width: 10),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Hồ sơ đang chờ duyệt giấy tờ',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Color(0xFFE65100)),
                  ),
                  SizedBox(height: 2),
                  Text(
                    'Kiểm tra giấy tờ pháp lý để kích hoạt tài khoản.',
                    style: TextStyle(fontSize: 11, color: Color(0xFFE65100)),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _openProfile,
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFFE65100),
                side: const BorderSide(color: Color(0xFFFFA000)),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('Kiểm tra', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      );
    }

    return const SizedBox.shrink();
  }
}
