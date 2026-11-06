import '../../../../core/auth/auth_session.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../auth/presentation/screens/login_register_screen.dart';
import 'business_profile_screen.dart';
import 'representative_profile_screen.dart';
import 'vendor_documents_screen.dart';
import 'edit_profile_bank_screen.dart';
import 'change_password_screen.dart';
import '../../../reviews/presentation/screens/review_feedback_screen.dart';
import '../../../vouchers/presentation/screens/voucher_management_screen.dart';
import '../../../settlements/presentation/screens/settlement_revenue_screen.dart';
import '../../../settlements/presentation/screens/payout_detail_screen.dart';
import '../../../disputes/presentation/screens/dispute_management_screen.dart';
import '../../../../core/widgets/symbolic_vendor_avatar.dart';

class PartnerAccountScreen extends StatefulWidget {
  const PartnerAccountScreen({super.key});

  @override
  State<PartnerAccountScreen> createState() => _PartnerAccountScreenState();
}

class _PartnerAccountScreenState extends State<PartnerAccountScreen> {
  final VendorMockDatabase _db = VendorMockDatabase.instance;
  String get _currentLocale => AppLanguage.instance.code;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  void _handleLogout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rXl)),
        title: const LocalizedText('Đăng xuất tài khoản'),
        content: const LocalizedText('Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng DANASEA Vendor?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const LocalizedText('Hủy'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.error,
              foregroundColor: Colors.white,
            ),
            child: const LocalizedText('Đăng xuất'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try { await AuthSession.instance.logout(); } catch (_) {}
      if (!mounted) return;
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (ctx) => const LoginRegisterScreen()),
        (route) => false,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    Localizations.localeOf(context);
    final user = _db.currentUser;
    final vendor = _db.currentVendor;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.secondary.withValues(alpha: 0.15),
                  width: 1.2,
                ),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.secondary.withValues(alpha: 0.12),
                    blurRadius: 4,
                    offset: const Offset(0, 1),
                  ),
                ],
              ),
              padding: const EdgeInsets.all(4),
              child: ClipOval(
                child: Image.asset(
                  'assets/danasea-logo.png',
                  fit: BoxFit.contain,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  LocalizedText(
                    'DANASEA',
                    style: AppTypography.headlineSm.copyWith(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.bold,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  LocalizedText(
                    'ĐỐI TÁC',
                    style: AppTypography.labelSm.copyWith(
                      color: AppColors.secondary,
                      letterSpacing: 1.0,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
        backgroundColor: AppColors.surface,
        elevation: 0,

      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Partner Profile Hero Card
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x08000000), blurRadius: 8, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: [
                  // Symbolic animated avatar with verified badge
                  const SymbolicVendorAvatar(
                    radius: 44,
                    animate: true,
                    showVerifiedBadge: true,
                  ),
                  const SizedBox(height: 12),
                  LocalizedText(
                    vendor.businessName.isNotEmpty
                        ? vendor.businessName
                        : (AuthSession.instance.profile?['fullName'] ?? '').toString(),
                    style: AppTypography.headlineSm.copyWith(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.bold,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: BorderRadius.circular(AppShapes.rFull),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.verified_user, size: 14, color: AppColors.secondary),
                        const SizedBox(width: 4),
                        Flexible(
                          child: LocalizedText(
                            'Đã xác thực',
                            style: AppTypography.labelSm.copyWith(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.bold,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Contact Metadata Pills
                  Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 8,
                    runSpacing: 6,
                    children: [
                      Container(
                        constraints: const BoxConstraints(maxWidth: 240),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(AppShapes.rFull),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.mail_outline, size: 14, color: AppColors.tertiary),
                            const SizedBox(width: 4),
                            Flexible(
                              child: LocalizedText(
                                (AuthSession.instance.profile?['email'] ?? '').toString(),
                                style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant, fontSize: 12),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (user.phone != null) ...[
                        Container(
                          constraints: const BoxConstraints(maxWidth: 240),
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(AppShapes.rFull),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.phone_iphone, size: 14, color: AppColors.tertiary),
                              const SizedBox(width: 4),
                              Flexible(
                                child: LocalizedText(
                                  user.phone!,
                                  style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant, fontSize: 12),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Operational Metrics Strip
                  Row(
                    children: [
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: BorderRadius.circular(AppShapes.rMd),
                          ),
                          child: Column(
                            children: [
                              LocalizedText('Điểm đánh giá', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
                              const SizedBox(height: 2),
                              LocalizedText('4.9 / 5.0', style: AppTypography.labelLg.copyWith(color: AppColors.secondary, fontWeight: FontWeight.bold)),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: BorderRadius.circular(AppShapes.rMd),
                          ),
                          child: Column(
                            children: [
                              LocalizedText('Tỉ lệ check-in', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
                              const SizedBox(height: 2),
                              LocalizedText('98.5%', style: AppTypography.labelLg.copyWith(color: AppColors.secondary, fontWeight: FontWeight.bold)),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Language Switcher Bar (Responsive Wrap)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(AppShapes.rMd),
              ),
              child: Wrap(
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                runSpacing: 8,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.translate, size: 18, color: AppColors.tertiary),
                      const SizedBox(width: 8),
                      LocalizedText(
                        'Ngôn ngữ',
                        style: AppTypography.labelMd.copyWith(color: AppColors.onSurface, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      GestureDetector(
                        onTap: () => AppLanguage.instance.setLanguage('vi'),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: _currentLocale == 'vi' ? AppColors.secondary : Colors.transparent,
                            borderRadius: BorderRadius.circular(AppShapes.rFull),
                          ),
                          child: LocalizedText(
                            'Tiếng Việt',
                            style: AppTypography.labelSm.copyWith(
                              color: _currentLocale == 'vi' ? Colors.white : AppColors.onSurfaceVariant,
                              fontWeight: _currentLocale == 'vi' ? FontWeight.bold : FontWeight.normal,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      GestureDetector(
                        onTap: () => AppLanguage.instance.setLanguage('en'),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: _currentLocale == 'en' ? AppColors.secondary : Colors.transparent,
                            borderRadius: BorderRadius.circular(AppShapes.rFull),
                          ),
                          child: LocalizedText(
                            'English',
                            style: AppTypography.labelSm.copyWith(
                              color: _currentLocale == 'en' ? Colors.white : AppColors.onSurfaceVariant,
                              fontWeight: _currentLocale == 'en' ? FontWeight.bold : FontWeight.normal,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Group 1: Business & Legal
            _buildSectionHeader(Icons.business_center_outlined, 'Kinh doanh & Pháp lý'),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: [
                  _buildMenuItem(
                    icon: Icons.storefront_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Hồ sơ doanh nghiệp',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const BusinessProfileScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.badge_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Người đại diện pháp lý',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const RepresentativeProfileScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.account_balance_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Tài khoản ngân hàng nhận tiền',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const EditProfileBankScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.verified_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Giấy tờ & Chứng nhận an toàn',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const VendorDocumentsScreen()),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Group 2: Sales & Customer Care
            _buildSectionHeader(Icons.storefront_outlined, 'Bán hàng & Khách hàng'),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: [
                  _buildMenuItem(
                    icon: Icons.star_outline_rounded,
                    iconBgColor: AppColors.primaryFixed.withAlpha(80),
                    iconColor: AppColors.primaryContainer,
                    title: 'Đánh giá của khách hàng',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const ReviewFeedbackScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.confirmation_number_outlined,
                    iconBgColor: AppColors.primaryFixed.withAlpha(80),
                    iconColor: AppColors.primaryContainer,
                    title: 'Mã giảm giá của đối tác',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const VoucherManagementScreen()),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Group 3: Finance & Dispatches
            _buildSectionHeader(Icons.account_balance_wallet_outlined, 'Tài chính & Khiếu nại'),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: [
                  _buildMenuItem(
                    icon: Icons.query_stats_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Đối soát doanh thu kỳ này',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const SettlementRevenueScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.payments_outlined,
                    iconBgColor: AppColors.secondaryContainer.withAlpha(80),
                    iconColor: AppColors.secondary,
                    title: 'Yêu cầu thanh toán & Lịch sử',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const PayoutDetailScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.shield_outlined,
                    iconBgColor: AppColors.errorContainer.withAlpha(70),
                    iconColor: AppColors.error,
                    title: 'Khiếu nại dịch vụ',
                    badgeCount: _db.disputes.where((d) => d.status == DisputeStatus.inReview).length,
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const DisputeManagementScreen()),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Group 4: System Settings
            _buildSectionHeader(Icons.settings_outlined, 'Cài đặt hệ thống'),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: [
                  _buildMenuItem(
                    icon: Icons.lock_outline,
                    iconBgColor: AppColors.surfaceContainer,
                    iconColor: AppColors.onSurfaceVariant,
                    title: 'Đổi mật khẩu',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (ctx) => const ChangePasswordScreen()),
                    ),
                  ),
                  const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                  _buildMenuItem(
                    icon: Icons.logout,
                    iconBgColor: AppColors.errorContainer.withAlpha(60),
                    iconColor: AppColors.error,
                    title: 'Đăng xuất',
                    titleColor: AppColors.error,
                    onTap: _handleLogout,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 50),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(IconData icon, String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: AppColors.secondary),
          const SizedBox(width: 8),
          Expanded(
            child: LocalizedText(
              title,
              style: AppTypography.labelLg.copyWith(
                color: AppColors.onSurface,
                fontWeight: FontWeight.bold,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMenuItem({
    required IconData icon,
    required Color iconBgColor,
    required Color iconColor,
    required String title,
    String? subtitle,
    int? badgeCount,
    String? badgeText,
    bool badgeIsDestructive = false,
    Color? titleColor,
    required VoidCallback onTap,
  }) {
    final hasSubtitle = subtitle != null && subtitle.trim().isNotEmpty;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: 16,
            vertical: hasSubtitle ? 12 : 14,
          ),
          child: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: iconBgColor,
                  shape: BoxShape.circle,
                ),
                child: Icon(icon, color: iconColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: hasSubtitle
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            title,
                            style: AppTypography.labelLg.copyWith(
                              color: titleColor ?? AppColors.onSurface,
                              fontWeight: FontWeight.bold,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          LocalizedText(
                            subtitle,
                            style: AppTypography.bodySm.copyWith(color: AppColors.tertiary),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      )
                    : LocalizedText(
                        title,
                        style: AppTypography.labelLg.copyWith(
                          color: titleColor ?? AppColors.onSurface,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
              ),
              if (badgeCount != null && badgeCount > 0) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
                  decoration: const BoxDecoration(
                    color: AppColors.error,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text(
                      badgeCount > 99 ? '99+' : '$badgeCount',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        height: 1.1,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
              ] else if (badgeText != null) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: badgeIsDestructive
                        ? AppColors.errorContainer
                        : AppColors.secondaryFixed.withAlpha(80),
                    borderRadius: BorderRadius.circular(AppShapes.rFull),
                  ),
                  child: LocalizedText(
                    badgeText,
                    style: AppTypography.labelSm.copyWith(
                      color: badgeIsDestructive
                          ? AppColors.onErrorContainer
                          : AppColors.onSecondaryFixedVariant,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 6),
              ],
              const Icon(Icons.chevron_right, color: AppColors.outlineVariant),
            ],
          ),
        ),
      ),
    );
  }
}
