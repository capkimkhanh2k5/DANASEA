import 'features/auth/presentation/screens/session_gate.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'core/theme/app_theme.dart';
import 'core/data/vendor_store.dart';
import 'features/auth/presentation/screens/login_register_screen.dart';
import 'features/auth/presentation/screens/forgot_password_screen.dart';
import 'features/dashboard/presentation/screens/dashboard_screen.dart';
import 'features/profile/presentation/screens/business_profile_screen.dart';
import 'features/profile/presentation/screens/vendor_documents_screen.dart';
import 'features/profile/presentation/screens/edit_profile_bank_screen.dart';
import 'features/profile/presentation/screens/change_password_screen.dart';
import 'features/profile/presentation/screens/partner_account_screen.dart';
import 'features/services/presentation/screens/service_list_screen.dart';
import 'features/services/presentation/screens/service_create_edit_screen.dart';
import 'features/slots/presentation/screens/slot_calendar_screen.dart';
import 'features/slots/presentation/screens/slot_config_screen.dart';
import 'features/orders/presentation/screens/order_list_screen.dart';
import 'features/orders/presentation/screens/sub_order_detail_screen.dart';
import 'features/orders/presentation/screens/qr_checkin_screen.dart';
import 'features/orders/presentation/screens/refund_detail_screen.dart';
import 'features/chat/presentation/screens/chat_list_screen.dart';
import 'features/notifications/presentation/screens/notification_safety_screen.dart';
import 'features/reviews/presentation/screens/review_feedback_screen.dart';
import 'features/vouchers/presentation/screens/voucher_management_screen.dart';
import 'features/settlements/presentation/screens/settlement_revenue_screen.dart';
import 'features/settlements/presentation/screens/payout_detail_screen.dart';
import 'features/disputes/presentation/screens/dispute_management_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppLanguage.instance.initialize();
  // Initialize vendor data store
  VendorStore.instance;
  runApp(const DanaSeaVendorApp());
}

class DanaSeaVendorApp extends StatelessWidget {
  const DanaSeaVendorApp({super.key});

  static final _navigatorObserver = _AppNavigatorObserver();

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: AppLanguage.instance,
      builder: (context, child) => MaterialApp(
        navigatorObservers: [_navigatorObserver],
        builder: (context, child) => _GlobalKeyboardDismissWrapper(
          child: child ?? const SizedBox.shrink(),
        ),
        locale: AppLanguage.instance.locale,
        supportedLocales: const [Locale('vi'), Locale('en')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        title: 'DANASEA Vendor',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        initialRoute: '/',
        routes: {
          '/': (context) => const SessionGate(),
          '/login': (context) => const LoginRegisterScreen(),
          '/forgot-password': (context) => const ForgotPasswordScreen(),
          '/dashboard': (context) => const DashboardScreen(),
          '/business-profile': (context) => const BusinessProfileScreen(),
          '/vendor-documents': (context) => const VendorDocumentsScreen(),
          '/edit-profile-bank': (context) => const EditProfileBankScreen(),
          '/services': (context) => const ServiceListScreen(),
          '/service-create': (context) => const ServiceCreateEditScreen(),
          '/slots-calendar': (context) => const SlotCalendarScreen(),
          '/slot-config': (context) => const SlotConfigScreen(),
          '/orders': (context) => const OrderListScreen(),
          '/qr-checkin': (context) => const QrCheckinScreen(),
          '/refund-detail': (context) => const RefundDetailScreen(),
          '/chat': (context) => const ChatListScreen(),
          '/notifications': (context) => const NotificationSafetyScreen(),
          '/reviews': (context) => const ReviewFeedbackScreen(),
          '/vouchers': (context) => const VoucherManagementScreen(),
          '/settlements': (context) => const SettlementRevenueScreen(),
          '/payout-detail': (context) => const PayoutDetailScreen(),
          '/disputes': (context) => const DisputeManagementScreen(),
          '/change-password': (context) => const ChangePasswordScreen(),
          '/partner-account': (context) => const PartnerAccountScreen(),
        },
        onGenerateRoute: (settings) {
          if (settings.name == '/service-edit') {
            final serviceId = settings.arguments as String?;
            final service = serviceId != null
                ? VendorStore.instance.services.firstWhere(
                    (s) => s.id == serviceId,
                    orElse: () => VendorStore.instance.services.first,
                  )
                : null;
            return MaterialPageRoute(
              builder: (context) => ServiceCreateEditScreen(service: service),
            );
          }
          if (settings.name == '/order-detail') {
            final subOrderId = settings.arguments as String? ?? 'DNS-8924-1';
            final subOrder = VendorStore.instance.subOrders.firstWhere(
              (o) => o.id == subOrderId,
              orElse: () => VendorStore.instance.subOrders.first,
            );
            return MaterialPageRoute(
              builder: (context) => SubOrderDetailScreen(subOrder: subOrder),
            );
          }
          return null;
        },
      ),
    );
  }
}

class _AppNavigatorObserver extends NavigatorObserver {
  void _unfocus() {
    FocusManager.instance.primaryFocus?.unfocus();
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _unfocus();
    super.didPop(route, previousRoute);
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _unfocus();
    super.didPush(route, previousRoute);
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    _unfocus();
    super.didReplace(newRoute: newRoute, oldRoute: oldRoute);
  }

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _unfocus();
    super.didRemove(route, previousRoute);
  }
}

class _GlobalKeyboardDismissWrapper extends StatefulWidget {
  final Widget child;
  const _GlobalKeyboardDismissWrapper({required this.child});

  @override
  State<_GlobalKeyboardDismissWrapper> createState() =>
      _GlobalKeyboardDismissWrapperState();
}

class _GlobalKeyboardDismissWrapperState
    extends State<_GlobalKeyboardDismissWrapper> with WidgetsBindingObserver {
  double _lastBottomInset = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    final view = View.maybeOf(context);
    final currentBottomInset = view?.viewInsets.bottom ?? 0;
    // When the soft keyboard closes (e.g. via back button / gesture):
    if (_lastBottomInset > 0 && currentBottomInset == 0) {
      FocusManager.instance.primaryFocus?.unfocus();
    }
    _lastBottomInset = currentBottomInset;
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (event) {
        final currentFocus = FocusManager.instance.primaryFocus;
        if (currentFocus != null && currentFocus.hasFocus) {
          final renderObject = currentFocus.context?.findRenderObject();
          if (renderObject is RenderBox) {
            final localPos = renderObject.globalToLocal(event.position);
            if (!renderObject.paintBounds.contains(localPos)) {
              currentFocus.unfocus();
            }
          }
        }
      },
      child: widget.child,
    );
  }
}
