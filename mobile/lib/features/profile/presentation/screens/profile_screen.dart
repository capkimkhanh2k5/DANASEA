import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../auth/presentation/screens/forgot_password_screen.dart';
import '../../../auth/presentation/screens/login_register_screen.dart';
import '../../../chat/presentation/screens/ai_assistant_screen.dart';
import '../../../chat/presentation/screens/conversations_screen.dart';
import '../../../notifications/presentation/screens/notifications_screen.dart';
import '../../../refund_dispute/presentation/screens/my_disputes_screen.dart';
import '../../../refund_dispute/presentation/screens/refund_tracking_screen.dart';
import 'edit_profile_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  String get _locale => AppLanguage.instance.code;

  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  Future<void> _loadProfile() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await AuthSession.instance.loadProfile();
    } catch (e) {
      if (mounted) _error = e.toString();
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _editProfile() async {
    await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const EditProfileScreen()),
    );
    if (mounted) setState(() {});
  }

  void _showLogoutDialog() {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
          title: LocalizedText(
            'Đăng xuất tài khoản?',
            style: AppTypography.headlineSm(color: AppColors.onSurface),
          ),
          content: LocalizedText(
            'Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng DANASEA trên thiết bị này?',
            style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const LocalizedText('Hủy'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primary,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              onPressed: () async {
                try {
                  await AuthSession.instance.logout();
                } catch (_) {}
                if (!context.mounted) return;
                Navigator.pop(context);
                Navigator.pushAndRemoveUntil(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const LoginRegisterScreen(),
                  ),
                  (_) => false,
                );
              },
              child: const LocalizedText(
                'Đăng xuất',
                style: TextStyle(color: Colors.white),
              ),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    Localizations.localeOf(context);
    final user = AuthSession.instance.profile ?? <String, dynamic>{};
    final avatar = user['avatarUrl']?.toString();
    final hasAvatar = avatar != null && avatar.isNotEmpty;
    final name = user['fullName']?.toString() ?? '';
    final email = user['email']?.toString() ?? '';

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        automaticallyImplyLeading: false,
        centerTitle: true,
        title: LocalizedText(
          'Tài khoản',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_loading) const LinearProgressIndicator(),
            if (_error != null) ...[
              Text(_error!, style: const TextStyle(color: Colors.red)),
              TextButton(
                onPressed: _loadProfile,
                child: const LocalizedText('Thử lại'),
              ),
            ],
            // USER PROFILE CARD (Phần phía trên gọn gàng như Image 2)
            InkWell(
              onTap: _editProfile,
              borderRadius: AppShapes.radiusDefault,
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusDefault,
                  boxShadow: AppShapes.shadowLevel1,
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 28,
                      backgroundColor: AppColors.secondaryContainer,
                      backgroundImage: hasAvatar ? NetworkImage(avatar) : null,
                      child: !hasAvatar
                          ? Text(
                              name.isNotEmpty ? name.characters.first : '?',
                              style: const TextStyle(
                                fontSize: 22,
                                fontWeight: FontWeight.bold,
                                color: AppColors.secondary,
                              ),
                            )
                          : null,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            name.isNotEmpty ? name : 'Người dùng',
                            style: AppTypography.headlineSm(
                              color: AppColors.onSurface,
                            ).copyWith(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            email,
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ).copyWith(fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // SETTINGS & SECURITY
            LocalizedText(
              'CÀI ĐẶT & BẢO MẬT',
              style: AppTypography.labelSm(
                color: AppColors.secondary,
                fontWeight: FontWeight.w800,
              ).copyWith(letterSpacing: 0.8),
            ),
            const SizedBox(height: 8),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusDefault,
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                children: [
                  _buildMenuTile(
                    icon: Icons.lock_outline_rounded,
                    title: 'Đổi mật khẩu',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const ForgotPasswordScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.language_rounded,
                          size: 21,
                          color: AppColors.onSurfaceVariant,
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: LocalizedText(
                            'Ngôn ngữ ứng dụng',
                            style: AppTypography.labelMd(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w600,
                            ).copyWith(fontSize: 14.5),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerHigh,
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Row(
                            children: [
                              _buildLangButton('VI', 'vi'),
                              _buildLangButton('EN', 'en'),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // UTILITIES
            LocalizedText(
              'TIỆN ÍCH TRẢI NGHIỆM',
              style: AppTypography.labelSm(
                color: AppColors.secondary,
                fontWeight: FontWeight.w800,
              ).copyWith(letterSpacing: 0.8),
            ),
            const SizedBox(height: 8),
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusDefault,
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                children: [
                  _buildMenuTile(
                    icon: Icons.chat_bubble_outline_rounded,
                    title: 'Tin nhắn với nhà cung cấp',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const ConversationsScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  _buildMenuTile(
                    icon: Icons.notifications_none_rounded,
                    title: 'Thông báo đơn & thời tiết',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const NotificationsScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  _buildMenuTile(
                    icon: Icons.shield_outlined,
                    title: 'Khiếu nại của tôi',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const MyDisputesScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  _buildMenuTile(
                    icon: Icons.account_balance_wallet_outlined,
                    title: 'Theo dõi hoàn tiền',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const RefundTrackingScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  _buildMenuTile(
                    icon: Icons.auto_awesome_outlined,
                    title: 'Trợ lý AI DanaSea',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const AiAssistantScreen(),
                        ),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  // Dòng đăng xuất tài khoản ở cuối danh sách, bên phải có icon màu
                  _buildMenuTile(
                    icon: Icons.logout_rounded,
                    title: 'Đăng xuất tài khoản',
                    iconColor: AppColors.primary,
                    titleColor: AppColors.primary,
                    trailing: Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: AppColors.primary.withValues(alpha: 0.12),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.logout_rounded,
                        size: 16,
                        color: AppColors.primary,
                      ),
                    ),
                    onTap: _showLogoutDialog,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildMenuTile({
    required IconData icon,
    required String title,
    required VoidCallback onTap,
    Color? iconColor,
    Color? titleColor,
    Widget? trailing,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Icon(
              icon,
              size: 21,
              color: iconColor ?? AppColors.onSurfaceVariant,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: LocalizedText(
                title,
                style: AppTypography.labelMd(
                  color: titleColor ?? AppColors.onSurface,
                  fontWeight: FontWeight.w600,
                ).copyWith(fontSize: 14.5),
              ),
            ),
            trailing ??
                const Icon(
                  Icons.chevron_right_rounded,
                  size: 20,
                  color: AppColors.outline,
                ),
          ],
        ),
      ),
    );
  }

  Widget _buildLangButton(String text, String code) {
    final isSel = _locale == code;
    return InkWell(
      onTap: () => AppLanguage.instance.setLanguage(code),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: isSel ? AppColors.secondary : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
        ),
        child: LocalizedText(
          text,
          style: AppTypography.labelSm(
            color: isSel ? Colors.white : AppColors.onSurfaceVariant,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
