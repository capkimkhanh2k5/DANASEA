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
import '../../../chat/presentation/screens/chat_list_screen.dart';
import '../../../settlements/presentation/screens/settlement_revenue_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';

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
        onNavigateTab: (index) => setState(() => _currentIndex = index),
        onOpenQrScan: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const QrCheckinScreen())),
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenChat: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ChatListScreen())),
        onOpenSettlements: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettlementRevenueScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BusinessProfileScreen())),
      ),
      ServiceListScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BusinessProfileScreen())),
      ),
      SlotCalendarScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BusinessProfileScreen())),
      ),
      OrderListScreen(
        onOpenNotifications: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationSafetyScreen())),
        onOpenProfile: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BusinessProfileScreen())),
      ),
      const PartnerAccountScreen(),
    ];
  }

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
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
    final pendingOrdersCount = _db.subOrders.where((o) => o.status == SubOrderStatus.pending).length;

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _buildScreens(),
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: AppColors.surface,
          boxShadow: [
            BoxShadow(
              color: Color(0x0F102F3A),
              blurRadius: 16,
              offset: Offset(0, -4),
            ),
          ],
        ),
        child: SafeArea(
          child: NavigationBar(
            selectedIndex: _currentIndex,
            onDestinationSelected: (index) {
              setState(() {
                _currentIndex = index;
              });
            },
            backgroundColor: Colors.transparent,
            indicatorColor: AppColors.secondaryContainer,
            elevation: 0,
            labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
            destinations: [
               NavigationDestination(
                icon: Icon(Icons.dashboard_outlined),
                selectedIcon: Icon(Icons.dashboard, color: AppColors.onSecondaryContainer),
                label: tr(context, 'Tổng quan'),
              ),
               NavigationDestination(
                icon: Icon(Icons.surfing_outlined),
                selectedIcon: Icon(Icons.surfing, color: AppColors.onSecondaryContainer),
                label: tr(context, 'Dịch vụ'),
              ),
               NavigationDestination(
                icon: Icon(Icons.calendar_month_outlined),
                selectedIcon: Icon(Icons.calendar_month, color: AppColors.onSecondaryContainer),
                label: tr(context, 'Lịch chạy'),
              ),
              NavigationDestination(
                icon: pendingOrdersCount > 0
                    ? Badge(
                        label: LocalizedText('$pendingOrdersCount'),
                        backgroundColor: AppColors.primaryContainer,
                        textColor: AppColors.onPrimary,
                        child: const Icon(Icons.receipt_long_outlined),
                      )
                    : const Icon(Icons.receipt_long_outlined),
                selectedIcon: const Icon(Icons.receipt_long, color: AppColors.onSecondaryContainer),
                label: 'Đơn hàng',
              ),
               NavigationDestination(
                icon: Icon(Icons.person_outline),
                selectedIcon: Icon(Icons.person, color: AppColors.onSecondaryContainer),
                label: tr(context, 'Tài khoản'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
