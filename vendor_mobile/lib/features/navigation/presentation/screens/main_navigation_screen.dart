import 'dart:ui';
import 'package:flutter/services.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../dashboard/presentation/screens/dashboard_screen.dart';
import '../../../services/presentation/screens/service_list_screen.dart';
import '../../../slots/presentation/screens/slot_calendar_screen.dart';
import '../../../orders/presentation/screens/order_list_screen.dart';
import '../../../profile/presentation/screens/partner_account_screen.dart';
import '../../../orders/presentation/screens/qr_checkin_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../settlements/presentation/screens/settlement_revenue_screen.dart';

class MainNavigationScreen extends StatefulWidget {
  final int initialIndex;

  const MainNavigationScreen({super.key, this.initialIndex = 0});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  late int _currentIndex;
  final VendorMockDatabase _db = VendorMockDatabase.instance;

  List<Widget> _buildScreens() {
    return [
      DashboardScreen(
        onNavigateTab: (index) => setState(() => _currentIndex = index.clamp(0, 3)),
        onOpenQrScan: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const QrCheckinScreen())),
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenSettlements: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettlementRevenueScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PartnerAccountScreen())),
      ),
      ServiceListScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PartnerAccountScreen())),
      ),
      SlotCalendarScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PartnerAccountScreen())),
      ),
      OrderListScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PartnerAccountScreen())),
      ),
    ];
  }

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex.clamp(0, 3);
    _db.addListener(_onDbChanged);
    _db.syncWithApi();
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
    // New orders count: strictly orders waiting for vendor review/confirmation
    final newOrdersCount = _db.subOrders.where((o) => o.status == SubOrderStatus.pending).length;

    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Stack(
        children: [
          // Screen content occupies full viewport
          IndexedStack(
            index: _currentIndex,
            children: _buildScreens(),
          ),

          // Floating Frosted Glass Capsule Bar
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _buildGlassBottomNav(context, newOrdersCount),
          ),
        ],
      ),
    );
  }

  Widget _buildGlassBottomNav(BuildContext context, int newOrdersCount) {
    final bottomInset = MediaQuery.of(context).padding.bottom;
    const double barHeight = 64.0;
    const double buttonDiameter = 54.0;
    const double protrusion = 18.0; // Phần nút nhô lên trên thanh bar

    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        bottom: bottomInset > 0 ? bottomInset + 10 : 18,
      ),
      child: SizedBox(
        height: barHeight + protrusion,
        child: Stack(
          clipBehavior: Clip.none,
          alignment: Alignment.bottomCenter,
          children: [
            // 1. THANH ĐIỀU HƯỚNG HIỆU ỨNG GƯƠNG MỜ TÁCH BIỆT (FROSTED GLASS FLOATING CAPSULE)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: barHeight,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(36),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [
                          Colors.white.withValues(alpha: 0.68),
                          Colors.white.withValues(alpha: 0.38),
                        ],
                      ),
                      borderRadius: BorderRadius.circular(36),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.75),
                        width: 1.2,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: const Color(0xFF0F172A).withValues(alpha: 0.08),
                          blurRadius: 20,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        // 1. Tổng quan
                        _buildNavItem(0, Icons.dashboard_outlined, Icons.dashboard_rounded, tr(context, 'Tổng quan')),

                        // 2. Dịch vụ
                        _buildNavItem(1, Icons.surfing_outlined, Icons.surfing_rounded, tr(context, 'Dịch vụ')),

                        // 3. Khoảng trống 1/5 dành cho nút QR nhô lên
                        const Expanded(child: SizedBox()),

                        // 4. Lịch chạy
                        _buildNavItem(2, Icons.calendar_month_outlined, Icons.calendar_month_rounded, tr(context, 'Lịch chạy')),

                        // 5. Đơn hàng (với badge số lượng)
                        _buildNavItem(
                          3,
                          Icons.receipt_long_outlined,
                          Icons.receipt_long_rounded,
                          tr(context, 'Đơn hàng'),
                          badgeCount: newOrdersCount,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            // 2. NÚT QUÉT QR NHÔ NỔI LÊN TRÊN (ELEVATED PROTRUDING BUTTON)
            Positioned(
              top: 0, // Nhô lên 18px so với đỉnh thanh bar
              child: GestureDetector(
                onTap: () {
                  HapticFeedback.mediumImpact();
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const QrCheckinScreen()),
                  );
                },
                child: Container(
                  width: buttonDiameter,
                  height: buttonDiameter,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: const LinearGradient(
                      colors: [Color(0xFF007E8D), AppColors.secondary],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    border: Border.all(
                      color: Colors.white,
                      width: 3.5, // Viền trắng halo tách biệt nổi bật
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.secondary.withValues(alpha: 0.40),
                        blurRadius: 14,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.qr_code_scanner_rounded,
                      color: Colors.white,
                      size: 26,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavItem(int index, IconData icon, IconData activeIcon, String label, {int badgeCount = 0}) {
    final bool isSelected = _currentIndex == index;
    return Expanded(
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          setState(() => _currentIndex = index);
        },
        borderRadius: BorderRadius.circular(20),
        child: Column(
          mainAxisSize: MainAxisSize.max,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 3),
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.secondaryContainer.withValues(alpha: 0.45) : Colors.transparent,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(
                    isSelected ? activeIcon : icon,
                    size: 22,
                    color: isSelected ? AppColors.secondary : AppColors.tertiary,
                  ),
                ),
                if (badgeCount > 0)
                  Positioned(
                    top: -2,
                    right: 2,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                      constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                      decoration: BoxDecoration(
                        color: AppColors.error,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Center(
                        child: Text(
                          badgeCount > 99 ? '99+' : '$badgeCount',
                          style: const TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: TextStyle(
                fontSize: 11,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                color: isSelected ? AppColors.secondary : AppColors.tertiary,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}
