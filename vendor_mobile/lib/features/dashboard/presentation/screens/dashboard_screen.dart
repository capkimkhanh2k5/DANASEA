import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/widgets/vendor_app_bar.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../orders/presentation/screens/qr_checkin_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../chat/presentation/screens/chat_list_screen.dart';
import '../../../settlements/presentation/screens/settlement_revenue_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';
import '../../../orders/presentation/screens/order_list_screen.dart';
import '../../../slots/presentation/screens/slot_calendar_screen.dart';
import '../../../disputes/presentation/screens/dispute_management_screen.dart';
import '../../../reviews/presentation/screens/review_feedback_screen.dart';
import '../../../vouchers/presentation/screens/voucher_management_screen.dart';
import '../../../../core/models/vendor_models.dart';


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
  final _db = VendorMockDatabase.instance;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  int _actionPageIndex = 0;
  final PageController _actionPageController = PageController();

  @override
  void dispose() {
    _actionPageController.dispose();
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _openDisputes() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const DisputeManagementScreen()));
  }

  void _openReviews() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ReviewFeedbackScreen()));
  }

  void _openVouchers() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const VoucherManagementScreen()));
  }

  void _openServices() {
    if (widget.onNavigateTab != null) {
      widget.onNavigateTab!(1);
    }
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

  void _openChat() {
    if (widget.onOpenChat != null) {
      widget.onOpenChat!();
    } else {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ChatListScreen()));
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

  @override
  Widget build(BuildContext context) {
    final vendor = _db.vendor;
    final weather = _db.weather;
    final subOrders = _db.subOrders;
    final slots = _db.slots;

    final todayOrders = subOrders.length;
    final checkedInOrders = subOrders.where((o) => o.checkedInAt != null).length;
    final unreadNotifs = _db.notifications.where((n) => !n.isRead).length;
    final confirmedCount = subOrders.where((o) => o.status == SubOrderStatus.confirmed).length;
    final cancelledCount = subOrders.where((o) => o.status == SubOrderStatus.cancelled).length;
    final disputeCount = _db.disputes.length;
    final reviewCount = _db.reviews.length;

    // Next tour calculation
    final nextSlot = slots.isNotEmpty ? slots.first : null;
    final nextService = nextSlot != null
        ? _db.services.firstWhere((s) => s.serviceId == nextSlot.serviceId, orElse: () => _db.services.first)
        : null;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: VendorAppBar(
        vendorName: vendor.businessName,
        unreadCount: unreadNotifs,
        onNotificationTap: _openNotifications,
        onProfileTap: _openProfile,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await _db.syncWithApi();
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Profile Capsule
                InkWell(
                  onTap: _openProfile,
                  borderRadius: BorderRadius.circular(12),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.surfaceContainerHigh,
                          border: Border.all(color: AppColors.secondary, width: 2),
                        ),
                        child: const Center(
                          child: Icon(Icons.sailing, color: AppColors.secondary, size: 26),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            LocalizedText(
                              vendor.businessName,
                              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Wrap(
                              crossAxisAlignment: WrapCrossAlignment.center,
                              spacing: 6,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: AppColors.secondaryContainer,
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: const Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(Icons.verified, size: 12, color: AppColors.onSecondaryContainer),
                                      SizedBox(width: 2),
                                      Flexible(
                                        child: LocalizedText(
                                          'Đối tác xác thực',
                                          style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const LocalizedText('• Cảng Sơn Trà', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                              ],
                            ),
                          ],
                        ),
                      ),
                      InkWell(
                        onTap: _openQrScan,
                        child: Container(
                          width: 42,
                          height: 42,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppColors.primaryContainer,
                            boxShadow: AppShapes.shadowSm,
                          ),
                          child: const Icon(Icons.qr_code_scanner, color: AppColors.onPrimary, size: 22),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Real-time Ocean Condition Card
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: AppShapes.radiusMd,
                    boxShadow: AppShapes.shadowSm,
                  ),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Row(
                              children: [
                                Container(
                                  width: 8,
                                  height: 8,
                                  decoration: const BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: AppColors.secondary,
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: LocalizedText(
                                    'THỜI TIẾT BIỂN ${weather.locationName.toUpperCase()}',
                                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.secondary.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Row(
                              children: [
                                Icon(Icons.check_circle, size: 12, color: AppColors.secondary),
                                SizedBox(width: 4),
                                LocalizedText('Ra khơi an toàn', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: _buildWeatherMetric(
                              Icons.tsunami,
                              'Độ cao sóng',
                              '${weather.waveHeightM.toStringAsFixed(1)}m',
                              'Dữ liệu gần nhất',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildWeatherMetric(
                              Icons.air,
                              'Sức gió',
                              '${weather.windSpeedKmh.toStringAsFixed(0)} km/h',
                              'Dữ liệu gần nhất',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildWeatherMetric(
                              Icons.wb_sunny_outlined,
                              'Lượng mưa',
                              'Nắng ráo',
                              '0.0 mm',
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Shopee-style Unified Hub (Order Status Counters + Action Buttons)
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: AppShapes.shadowSm,
                  ),
                  padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
                  child: Column(
                    children: [
                      // 1. Shopee 4-column Order Status Counters
                      Row(
                        children: [
                          _buildShopeeMetricItem(
                            count: confirmedCount > 0 ? confirmedCount.toString() : '0',
                            label: 'Chờ check-in',
                            highlightColor: confirmedCount > 0 ? AppColors.primary : AppColors.onSurface,
                            onTap: _openOrders,
                          ),
                          _buildShopeeMetricItem(
                            count: cancelledCount.toString(),
                            label: 'Đơn hủy',
                            highlightColor: AppColors.onSurface,
                            onTap: _openOrders,
                          ),
                          _buildShopeeMetricItem(
                            count: disputeCount.toString(),
                            label: 'Khiếu nại/Hoàn',
                            highlightColor: AppColors.onSurface,
                            onTap: _openDisputes,
                          ),
                          _buildShopeeMetricItem(
                            count: reviewCount > 0 ? reviewCount.toString() : '0',
                            label: 'Đánh giá',
                            highlightColor: reviewCount > 0 ? AppColors.primary : AppColors.onSurface,
                            onTap: _openReviews,
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Divider(height: 1, thickness: 0.8, color: AppColors.outlineVariant.withOpacity(0.35)),
                      const SizedBox(height: 12),

                      // 2. Shopee Action Buttons Carousel (2 pages x 5 items)
                      SizedBox(
                        height: 82,
                        child: PageView(
                          controller: _actionPageController,
                          onPageChanged: (idx) => setState(() => _actionPageIndex = idx),
                          children: [
                            // Page 1: 5 Core daily actions
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceAround,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                _buildShopeeActionButton(
                                  icon: Icons.inventory_2_outlined,
                                  label: 'Dịch vụ',
                                  bgColor: AppColors.secondary.withOpacity(0.12),
                                  color: AppColors.secondary,
                                  onTap: _openServices,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.receipt_long_outlined,
                                  label: 'Đơn hàng',
                                  bgColor: AppColors.primary.withOpacity(0.12),
                                  color: AppColors.primary,
                                  onTap: _openOrders,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.qr_code_scanner_rounded,
                                  label: 'Quét vé QR',
                                  bgColor: const Color(0xFFE8F5E9),
                                  color: const Color(0xFF2E7D32),
                                  onTap: _openQrScan,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.account_balance_wallet_outlined,
                                  label: 'Tài chính',
                                  bgColor: const Color(0xFFFFF8E1),
                                  color: const Color(0xFFF57F17),
                                  onTap: _openSettlements,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.shield_outlined,
                                  label: 'Cảng vụ',
                                  bgColor: AppColors.errorContainer,
                                  color: AppColors.error,
                                  onTap: _openNotifications,
                                ),
                              ],
                            ),
                            // Page 2: 5 Management & Promotion actions
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceAround,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                _buildShopeeActionButton(
                                  icon: Icons.chat_bubble_outline_rounded,
                                  label: 'Tin nhắn',
                                  bgColor: const Color(0xFFE1F5FE),
                                  color: const Color(0xFF0288D1),
                                  badgeCount: 2,
                                  onTap: _openChat,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.calendar_month_outlined,
                                  label: 'Lịch chạy',
                                  bgColor: const Color(0xFFEDE7F6),
                                  color: const Color(0xFF7E57C2),
                                  onTap: _openSlots,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.confirmation_number_outlined,
                                  label: 'Khuyến mãi',
                                  bgColor: const Color(0xFFFFF3E0),
                                  color: const Color(0xFFE65100),
                                  onTap: _openVouchers,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.rate_review_outlined,
                                  label: 'Đánh giá',
                                  bgColor: const Color(0xFFFFFDE7),
                                  color: const Color(0xFFFBC02D),
                                  onTap: _openReviews,
                                ),
                                _buildShopeeActionButton(
                                  icon: Icons.storefront_outlined,
                                  label: 'Hồ sơ',
                                  bgColor: const Color(0xFFECEFF1),
                                  color: const Color(0xFF546E7A),
                                  onTap: _openProfile,
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 6),

                      // 3. Shopee Page Indicator Dots [ = - ]
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          AnimatedContainer(
                            duration: const Duration(milliseconds: 200),
                            width: _actionPageIndex == 0 ? 14 : 5,
                            height: 3.5,
                            decoration: BoxDecoration(
                              color: _actionPageIndex == 0 ? AppColors.primary : AppColors.outlineVariant.withOpacity(0.6),
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                          const SizedBox(width: 4),
                          AnimatedContainer(
                            duration: const Duration(milliseconds: 200),
                            width: _actionPageIndex == 1 ? 14 : 5,
                            height: 3.5,
                            decoration: BoxDecoration(
                              color: _actionPageIndex == 1 ? AppColors.primary : AppColors.outlineVariant.withOpacity(0.6),
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Today's Operational Highlights
                const Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        'Vận hành hôm nay',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    SizedBox(width: 8),
                    LocalizedText('04 TH5 2025', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.tertiary, letterSpacing: 0.5)),
                  ],
                ),
                const SizedBox(height: 10),

                // Revenue Hero Ribbon
                InkWell(
                  onTap: _openSettlements,
                  borderRadius: AppShapes.radiusMd,
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      borderRadius: AppShapes.radiusMd,
                      gradient: const LinearGradient(
                        colors: [AppColors.secondary, Color(0xFF004D56)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Expanded(
                              child: LocalizedText('DOANH THU TẠM TÍNH HÔM NAY',
                                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondaryFixed, letterSpacing: 0.5),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: Colors.white.withOpacity(0.15),
                              ),
                              child: const Icon(Icons.trending_up, color: Colors.white, size: 20),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.baseline,
                          textBaseline: TextBaseline.alphabetic,
                          children: const [
                            LocalizedText('5.040.000', style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Colors.white)),
                            SizedBox(width: 4),
                            LocalizedText('đ', style: TextStyle(fontSize: 14, color: AppColors.secondaryFixed)),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: const [
                            Icon(Icons.arrow_upward, size: 14, color: AppColors.secondaryFixed),
                            SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                'Tăng 18.5% so với cùng kỳ thứ Bảy tuần trước',
                                style: TextStyle(fontSize: 11, color: AppColors.secondaryFixed, fontWeight: FontWeight.w600),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),

                // Dual Metric Cards
                Row(
                  children: [
                    // Slot Schedule Card
                    Expanded(
                      child: InkWell(
                        onTap: _openSlots,
                        borderRadius: AppShapes.radiusMd,
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: AppShapes.radiusMd,
                            boxShadow: AppShapes.shadowSm,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  const Expanded(
                                    child: LocalizedText(
                                      'Khởi hành',
                                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.tertiary),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.secondary.withValues(alpha: 0.1),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: LocalizedText('${slots.length} Slot',
                                        style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              RichText(
                                text:  TextSpan(
                                  children: [
                                    TextSpan(text: tr(context, '18 '), style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                    TextSpan(text: tr(context, '/ 20 khách'), style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 6),
                              ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: const LinearProgressIndicator(
                                  value: 0.9,
                                  minHeight: 5,
                                  backgroundColor: AppColors.surfaceContainerHighest,
                                  valueColor: AlwaysStoppedAnimation<Color>(AppColors.secondary),
                                ),
                              ),
                              const SizedBox(height: 8),
                              const Row(
                                children: [
                                  Icon(Icons.schedule, size: 12, color: AppColors.tertiary),
                                  SizedBox(width: 4),
                                  Expanded(
                                    child: LocalizedText('05:00 • 07:30 • 15:30',
                                        style: TextStyle(fontSize: 10, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),

                    // Orders Status Card
                    Expanded(
                      child: InkWell(
                        onTap: _openOrders,
                        borderRadius: AppShapes.radiusMd,
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: AppShapes.radiusMd,
                            boxShadow: AppShapes.shadowSm,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  const Expanded(
                                    child: LocalizedText(
                                      'Đơn dịch vụ',
                                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.tertiary),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppColors.primaryContainer,
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: LocalizedText('+$todayOrders',
                                        style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              RichText(
                                text:  TextSpan(
                                  children: [
                                    TextSpan(text: tr(context, '2 '), style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                    TextSpan(text: tr(context, 'cần chuẩn bị SUP'), style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.primary)),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 4),
                              const LocalizedText('Áo phao & mái chèo sẵn sàng', style: TextStyle(fontSize: 10, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                              const SizedBox(height: 9),
                              Row(
                                children: [
                                  const Icon(Icons.task_alt, size: 12, color: AppColors.secondary),
                                  const SizedBox(width: 4),
                                  Expanded(
                                    child: LocalizedText(
                                      '$checkedInOrders đơn đã check-in',
                                      style: const TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.w600),
                                      maxLines: 1,
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
                  ],
                ),
                const SizedBox(height: 20),

                // Upcoming Tour Spotlight
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Row(
                        children: [
                          Icon(Icons.departure_board, size: 18, color: AppColors.primary),
                          SizedBox(width: 6),
                          Expanded(
                            child: LocalizedText(
                              'Tour xuất bến gần nhất',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.primaryFixed,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const LocalizedText('Sắp khởi hành', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onPrimaryFixedVariant)),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusMd,
                    boxShadow: AppShapes.shadowSm,
                  ),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 64,
                            height: 64,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(10),
                              color: AppColors.surfaceContainerHigh,
                            ),
                            child: const Center(
                              child: Icon(Icons.surfing, color: AppColors.secondary, size: 32),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
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
                                          const Icon(Icons.access_time, size: 11, color: AppColors.secondary),
                                          const SizedBox(width: 3),
                                          Flexible(
                                            child: LocalizedText(
                                              '${nextSlot?.startTime.substring(0, 5) ?? '05:00'} - ${nextSlot?.endTime.substring(0, 5) ?? '07:00'}',
                                              style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: AppColors.primaryFixed,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: const LocalizedText('Chuẩn bị xuất bến',
                                          style: TextStyle(fontSize: 8.5, fontWeight: FontWeight.bold, color: AppColors.primary)),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                LocalizedText(
                                  nextService?.nameVi ?? 'Chèo SUP ngắm bình minh Mỹ Khê',
                                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                const SizedBox(height: 4),
                                Row(
                                  children: [
                                    const Icon(Icons.group, size: 14, color: AppColors.secondary),
                                    const SizedBox(width: 4),
                                    Expanded(
                                      child: LocalizedText(
                                        '${nextSlot?.bookedCount ?? 8}/${nextSlot?.capacity ?? 8} khách (${(nextSlot?.bookedCount ?? 8) >= (nextSlot?.capacity ?? 8) ? 'Đầy chỗ' : 'Còn chỗ'})',
                                        style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                                        maxLines: 1,
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
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: ElevatedButton.icon(
                              onPressed: _openOrders, // Orders tab
                              icon: const Icon(Icons.checklist, size: 16),
                              label: const LocalizedText('Xem danh sách khách', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.primaryContainer,
                                foregroundColor: AppColors.onPrimary,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                                elevation: 0,
                                padding: const EdgeInsets.symmetric(vertical: 10),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: AppColors.surfaceContainerLow,
                            ),
                            child: IconButton(
                              icon: const Icon(Icons.call, size: 18, color: AppColors.secondary),
                              onPressed: () => ToastNotification.showInfo(context, 'Gọi HDV phụ trách bến'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Weekly Revenue Sparkline Card
                InkWell(
                  onTap: _openSettlements,
                  borderRadius: AppShapes.radiusMd,
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: AppShapes.radiusMd,
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText('Doanh thu tuần', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                                  LocalizedText('28 Th4 - 04 Th5', style: TextStyle(fontSize: 11, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: const [
                                LocalizedText('32.8M', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                                Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.north_east, size: 11, color: AppColors.secondary),
                                    LocalizedText('+14.2%', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                                  ],
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        // Mini bar chart
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            _buildChartBar('T2', 34, false),
                            _buildChartBar('T3', 42, false),
                            _buildChartBar('T4', 27, false),
                            _buildChartBar('T5', 48, false),
                            _buildChartBar('T6', 54, false),
                            _buildChartBar('T7', 66, true), // Today
                            _buildChartBar('CN', 58, false, isProjected: true),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildWeatherMetric(IconData icon, String label, String value, String sub) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 13, color: AppColors.tertiary),
              const SizedBox(width: 3),
              Expanded(
                child: LocalizedText(label, style: const TextStyle(fontSize: 10, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
              ),
            ],
          ),
          const SizedBox(height: 4),
          LocalizedText(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
          const SizedBox(height: 2),
          LocalizedText(sub, style: const TextStyle(fontSize: 9, color: AppColors.secondary), maxLines: 1, overflow: TextOverflow.ellipsis),
        ],
      ),
    );
  }

  Widget _buildShopeeMetricItem({
    required String count,
    required String label,
    required Color highlightColor,
    required VoidCallback onTap,
  }) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                count,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: highlightColor,
                ),
              ),
              const SizedBox(height: 4),
              LocalizedText(
                label,
                style: const TextStyle(
                  fontSize: 11,
                  color: AppColors.onSurfaceVariant,
                  fontWeight: FontWeight.w500,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildShopeeActionButton({
    required IconData icon,
    required String label,
    required Color color,
    required Color bgColor,
    int? badgeCount,
    VoidCallback? onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 62,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: bgColor,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(icon, color: color, size: 22),
                ),
                if (badgeCount != null && badgeCount > 0)
                  Positioned(
                    top: -3,
                    right: -3,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                      decoration: BoxDecoration(
                        color: AppColors.primary,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                      child: Text(
                        badgeCount > 99 ? '99+' : badgeCount.toString(),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 6),
            LocalizedText(
              label,
              style: const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w500,
                color: AppColors.onSurface,
                height: 1.15,
              ),
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }


  Widget _buildChartBar(String day, double height, bool isPeak, {bool isProjected = false}) {
    return Flexible(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Container(
            width: 16,
            height: height,
            decoration: BoxDecoration(
              color: isPeak
                  ? AppColors.primaryContainer
                  : isProjected
                      ? AppColors.secondary.withOpacity(0.4)
                      : AppColors.tertiaryFixedDim,
              borderRadius: BorderRadius.circular(5),
            ),
          ),
          const SizedBox(height: 6),
          LocalizedText(
            day,
            style: TextStyle(
              fontSize: 10,
              fontWeight: isPeak ? FontWeight.bold : FontWeight.normal,
              color: isPeak ? AppColors.primary : AppColors.tertiary,
            ),
          ),
        ],
      ),
    );
  }
}
