import '../../../../core/auth/google_login.dart';
import '../../../../core/auth/auth_session.dart';
import 'verify_email_screen.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/vendor_button.dart';
import '../../../../core/widgets/vendor_text_field.dart';
import '../../../../core/widgets/toast_notification.dart';
import 'forgot_password_screen.dart';
import 'vendor_access_screen.dart';

class LoginRegisterScreen extends StatefulWidget {
  const LoginRegisterScreen({super.key});

  @override
  State<LoginRegisterScreen> createState() => _LoginRegisterScreenState();
}

class _LoginRegisterScreenState extends State<LoginRegisterScreen> {
  final _authRepo = AuthSession.instance;
  bool _isLoginTab = true;
  bool _googleBusy = false;

  // Login form
  final _loginFormKey = GlobalKey<FormState>();
  final _loginEmailController = TextEditingController();
  final _loginPasswordController = TextEditingController();
  bool _rememberMe = true;
  bool _hideLoginPassword = true;
  bool _isLoadingLogin = false;

  // Register form
  final _registerFormKey = GlobalKey<FormState>();
  final _regNameController = TextEditingController();
  final _regEmailController = TextEditingController();
  final _regPasswordController = TextEditingController();
  final _regConfirmPasswordController = TextEditingController();
  bool _termsAgreed = false;
  bool _hideRegPassword = true;
  bool _hideRegConfirmPassword = true;
  bool _isLoadingRegister = false;

  // Demo state banner simulation
  String? _statusBannerMessage;

  @override
  void dispose() {
    _loginEmailController.dispose();
    _loginPasswordController.dispose();
    _regNameController.dispose();
    _regEmailController.dispose();
    _regPasswordController.dispose();
    _regConfirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _googleLogin() async {
    if (_googleBusy || _isLoadingLogin || _isLoadingRegister) return;
    if (!_isLoginTab && !_termsAgreed) {
      showVendorToast(
        context,
        message: 'Vui lòng đồng ý với điều khoản hợp tác đối tác.',
        isError: true,
      );
      return;
    }
    setState(() => _googleBusy = true);
    try {
      final token = await GoogleLogin.idToken();
      if (token == null) return;
      _authRepo.remember = _isLoginTab ? _rememberMe : true;
      await _authRepo.loginWithGoogle(token);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const VendorAccessScreen()),
        (_) => false,
      );
    } catch (e) {
      if (mounted)
        showVendorToast(context, message: e.toString(), isError: true);
    } finally {
      if (mounted) setState(() => _googleBusy = false);
    }
  }

  Future<void> _handleLogin() async {
    if (_googleBusy ||
        _isLoadingLogin ||
        !_loginFormKey.currentState!.validate())
      return;
    setState(() => _isLoadingLogin = true);
    try {
      _authRepo.remember = _rememberMe;
      await _authRepo.login(
        _loginEmailController.text,
        _loginPasswordController.text,
      );
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const VendorAccessScreen()),
        );
      }
    } catch (e) {
      if (e is AuthFailure && (e.status == 403 || e.payload?['code'] == 'EMAIL_NOT_VERIFIED')) {
        final pendingToken = e.payload?['accessToken'] as String?;
        _authRepo.setPendingVerification(
          email: _loginEmailController.text,
          accessToken: pendingToken,
        );
        _authRepo.sendOtp().catchError((_) {});
        if (mounted) {
          final verified = await Navigator.push<bool>(
            context,
            MaterialPageRoute(builder: (_) => const VerifyEmailScreen()),
          );
          if (verified == true && mounted) {
            Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const VendorAccessScreen()),
            );
          }
        }
        return;
      }
      if (mounted) {
        showVendorToast(
          context,
          message: e.toString().replaceAll('Exception: ', ''),
          isError: true,
        );
      }
    } finally {
      if (mounted) setState(() => _isLoadingLogin = false);
    }
  }

  Future<void> _handleRegister() async {
    if (_googleBusy ||
        _isLoadingRegister ||
        !_registerFormKey.currentState!.validate())
      return;
    if (!_termsAgreed) {
      showVendorToast(
        context,
        message: 'Vui lòng đồng ý với điều khoản hợp tác đối tác.',
        isError: true,
      );
      return;
    }
    setState(() => _isLoadingRegister = true);
    try {
      if (!AuthSession.strongPassword(_regPasswordController.text)) {
        throw AuthFailure(
          'Mật khẩu 8–100 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.',
        );
      }
      _authRepo.remember = true;
      if (!_authRepo.pendingVerification ||
          _authRepo.pendingEmail?.toLowerCase() !=
              _regEmailController.text.trim().toLowerCase()) {
        await _authRepo.register(
          _regNameController.text,
          _regEmailController.text,
          _regPasswordController.text,
        );
      }
      if (!mounted) return;
      final verified = await Navigator.push<bool>(
        context,
        MaterialPageRoute(builder: (_) => const VerifyEmailScreen()),
      );
      if (verified == true && mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const VendorAccessScreen()),
          (_) => false,
        );
      }
    } catch (e) {
      if (mounted) {
        showVendorToast(
          context,
          message: e.toString().replaceAll('Exception: ', ''),
          isError: true,
        );
      }
    } finally {
      if (mounted) setState(() => _isLoadingRegister = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Top portal header
                  _buildHeaderCard(),
                  const SizedBox(height: 16),

                  // Tab switcher
                  _buildTabSwitcher(),
                  const SizedBox(height: 16),

                  // Status warning banner if any
                  if (_statusBannerMessage != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer.withAlpha(120),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: AppColors.secondary.withAlpha(80),
                        ),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.info_outline,
                            color: AppColors.secondary,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: LocalizedText(
                              _statusBannerMessage!,
                              style: AppTypography.bodySm(
                                color: AppColors.onSecondaryContainer,
                              ),
                            ),
                          ),
                          InkWell(
                            onTap: () =>
                                setState(() => _statusBannerMessage = null),
                            child: const Icon(
                              Icons.close,
                              size: 18,
                              color: AppColors.secondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],

                  // Form Container
                  if (_isLoginTab) _buildLoginForm() else _buildRegisterForm(),

                  const SizedBox(height: 20),

                  OutlinedButton(
                    onPressed:
                        _googleBusy || _isLoadingLogin || _isLoadingRegister
                        ? null
                        : _googleLogin,
                    child: LocalizedText(
                      _googleBusy ? 'Đang đăng nhập...' : 'Tiếp tục với Google',
                    ),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderCard() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        boxShadow: AppShapes.shadowLevel1,
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 6,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 42,
                    height: 42,
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
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.all(5),
                    child: ClipOval(
                      child: Image.asset(
                        'assets/danasea-logo.png',
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText(
                        'DANASEA',
                        style: AppTypography.headlineSm(
                          color: AppColors.secondary,
                        ),
                      ),
                      LocalizedText(
                        'VENDOR PORTAL',
                        style: AppTypography.labelSm(color: AppColors.tertiary),
                      ),
                    ],
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer,
                  borderRadius: BorderRadius.circular(9999),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 6,
                      height: 6,
                      decoration: const BoxDecoration(
                        color: AppColors.secondary,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    LocalizedText(
                      'Hệ thống trực tuyến',
                      style: AppTypography.labelSm(
                        color: AppColors.onSecondaryContainer,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: SizedBox(
              height: 120,
              width: double.infinity,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.network(
                    'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=80',
                    height: 120,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => Container(
                      height: 120,
                      width: double.infinity,
                      color: AppColors.secondary.withAlpha(30),
                      child: const Icon(
                        Icons.beach_access,
                        size: 40,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                  Positioned.fill(
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Colors.transparent,
                            AppColors.onSurface.withAlpha(180),
                          ],
                        ),
                      ),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 8,
                      ),
                      alignment: Alignment.bottomLeft,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            'CỔNG KẾT NỐI DỊCH VỤ BIỂN',
                            style: AppTypography.labelSm(
                              color: AppColors.secondaryContainer,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          LocalizedText(
                            'Đồng hành phát triển du lịch biển Đà Nẵng',
                            style: AppTypography.headlineSm(
                              color: Colors.white,
                            ).copyWith(fontSize: 15),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabSwitcher() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(9999),
        boxShadow: AppShapes.shadowLevel1,
      ),
      child: Row(
        children: [
          Expanded(
            child: InkWell(
              onTap: () => setState(() => _isLoginTab = true),
              borderRadius: BorderRadius.circular(9999),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: _isLoginTab ? AppColors.secondary : Colors.transparent,
                  borderRadius: BorderRadius.circular(9999),
                ),
                alignment: Alignment.center,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.lock_open_rounded,
                      size: 16,
                      color: _isLoginTab
                          ? AppColors.onSecondary
                          : AppColors.tertiary,
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: LocalizedText(
                        'Đăng nhập',
                        style: AppTypography.labelLg(
                          color: _isLoginTab
                              ? AppColors.onSecondary
                              : AppColors.tertiary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Expanded(
            child: InkWell(
              onTap: () => setState(() => _isLoginTab = false),
              borderRadius: BorderRadius.circular(9999),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: !_isLoginTab
                      ? AppColors.secondary
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(9999),
                ),
                alignment: Alignment.center,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.storefront_rounded,
                      size: 16,
                      color: !_isLoginTab
                          ? AppColors.onSecondary
                          : AppColors.tertiary,
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: LocalizedText(
                        'Đăng ký đối tác',
                        style: AppTypography.labelLg(
                          color: !_isLoginTab
                              ? AppColors.onSecondary
                              : AppColors.tertiary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoginForm() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(20),
        boxShadow: AppShapes.shadowLevel1,
      ),
      padding: const EdgeInsets.all(20),
      child: Form(
        key: _loginFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText(
              'Chào mừng trở lại!',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 4),
            LocalizedText(
              'Truy cập trung tâm điều hành & quản trị đơn dịch vụ biển DANASEA.',
              style: AppTypography.bodySm(color: AppColors.tertiary),
            ),
            const SizedBox(height: 18),
            VendorTextField(
              label: 'Email tài khoản đối tác',
              controller: _loginEmailController,
              prefixIcon: Icons.mail_outline_rounded,
              keyboardType: TextInputType.emailAddress,
              validator: (v) {
                if (v == null || v.trim().isEmpty)
                  return 'Vui lòng nhập email.';
                if (!v.contains('@')) return 'Email không hợp lệ.';
                return null;
              },
            ),
            const SizedBox(height: 14),
            VendorTextField(
              label: 'Mật khẩu quản trị',
              controller: _loginPasswordController,
              prefixIcon: Icons.key_rounded,
              obscureText: _hideLoginPassword,
              suffixIcon: IconButton(
                icon: Icon(
                  _hideLoginPassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: AppColors.tertiary,
                  size: 20,
                ),
                onPressed: () =>
                    setState(() => _hideLoginPassword = !_hideLoginPassword),
              ),
              validator: (v) {
                if (v == null || v.isEmpty) return 'Vui lòng nhập mật khẩu.';
                return null;
              },
            ),
            const SizedBox(height: 10),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 4,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 24,
                      height: 24,
                      child: Checkbox(
                        value: _rememberMe,
                        onChanged: (v) =>
                            setState(() => _rememberMe = v ?? true),
                        activeColor: AppColors.secondary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(4),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    LocalizedText(
                      'Ghi nhớ đăng nhập',
                      style: AppTypography.bodySm(color: AppColors.tertiary),
                    ),
                  ],
                ),
                InkWell(
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => const ForgotPasswordScreen(),
                      ),
                    );
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: LocalizedText(
                      'Quên mật khẩu?',
                      style: AppTypography.labelMd(color: AppColors.secondary),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            VendorButton(
              label: 'Đăng nhập quản trị',
              icon: Icons.arrow_forward_rounded,
              isLoading: _isLoadingLogin,
              onPressed: _handleLogin,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRegisterForm() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(20),
        boxShadow: AppShapes.shadowLevel1,
      ),
      padding: const EdgeInsets.all(20),
      child: Form(
        key: _registerFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText(
              'Gia nhập Mạng lưới Đối tác',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 16),
            VendorTextField(
              label: 'Họ và tên người đại diện',
              controller: _regNameController,
              prefixIcon: Icons.badge_outlined,
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Vui lòng nhập họ tên.'
                  : null,
            ),
            const SizedBox(height: 14),
            VendorTextField(
              label: 'Email doanh nghiệp / Hộ kinh doanh',
              controller: _regEmailController,
              prefixIcon: Icons.corporate_fare_rounded,
              keyboardType: TextInputType.emailAddress,
              validator: (v) {
                if (v == null || v.trim().isEmpty)
                  return 'Vui lòng nhập email.';
                if (!v.contains('@')) return 'Email không đúng định dạng.';
                return null;
              },
            ),
            const SizedBox(height: 14),
            VendorTextField(
              label: 'Mật khẩu',
              controller: _regPasswordController,
              prefixIcon: Icons.lock_outline_rounded,
              obscureText: _hideRegPassword,
              suffixIcon: IconButton(
                icon: Icon(
                  _hideRegPassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: AppColors.tertiary,
                  size: 20,
                ),
                onPressed: () =>
                    setState(() => _hideRegPassword = !_hideRegPassword),
              ),
              validator: (v) {
                if (v == null || v.isEmpty) return 'Vui lòng nhập mật khẩu.';
                if (v.length < 8) return 'Mật khẩu phải từ 8 ký tự trở lên.';
                return null;
              },
            ),
            const SizedBox(height: 14),
            VendorTextField(
              label: 'Xác nhận mật khẩu',
              controller: _regConfirmPasswordController,
              prefixIcon: Icons.check_circle_outline_rounded,
              obscureText: _hideRegConfirmPassword,
              suffixIcon: IconButton(
                icon: Icon(
                  _hideRegConfirmPassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: AppColors.tertiary,
                  size: 20,
                ),
                onPressed: () => setState(
                  () => _hideRegConfirmPassword = !_hideRegConfirmPassword,
                ),
              ),
              validator: (v) {
                if (v != _regPasswordController.text)
                  return 'Mật khẩu xác nhận không khớp.';
                return null;
              },
            ),
            const SizedBox(height: 14),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 24,
                  height: 24,
                  child: Checkbox(
                    value: _termsAgreed,
                    onChanged: (v) => setState(() => _termsAgreed = v ?? false),
                    activeColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: LocalizedText(
                    'Tôi đồng ý với Điều khoản hợp tác nhà cung cấp và quy chuẩn an toàn hàng hải của DANASEA.',
                    style: AppTypography.bodySm(color: AppColors.onSurface),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            VendorButton(
              label: 'Đăng ký trở thành đối tác',
              icon: Icons.check_rounded,
              variant: VendorButtonVariant.coral,
              isLoading: _isLoadingRegister,
              onPressed: _handleRegister,
            ),
          ],
        ),
      ),
    );
  }


}
