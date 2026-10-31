import '../../../../core/auth/google_login.dart';
import '../../../../core/auth/auth_session.dart';
import 'verify_email_screen.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../home/presentation/screens/home_screen.dart';
import 'forgot_password_screen.dart';
import '../../../../shared/widgets/danasea_logo.dart';

class LoginRegisterScreen extends StatefulWidget {
  final bool initialIsLogin;

  const LoginRegisterScreen({super.key, this.initialIsLogin = true});

  @override
  State<LoginRegisterScreen> createState() => _LoginRegisterScreenState();
}

class _LoginRegisterScreenState extends State<LoginRegisterScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _intro;
  late bool _isLogin;
  bool _obscurePassword = true;
  bool _busy = false;
  final _confirmController = TextEditingController();

  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _isLogin = widget.initialIsLogin;
    _intro = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 5400),
    );
    _intro.forward();
  }

  @override
  void dispose() {
    _confirmController.dispose();
    _intro.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _nameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _googleLogin() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final token = await GoogleLogin.idToken();
      if (token == null) return;
      await AuthSession.instance.loginWithGoogle(token);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const HomeScreen()),
        (_) => false,
      );
    } catch (e) {
      if (mounted) _error(e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submit() async {
    if (_busy) return;
    final email = _emailController.text.trim();
    if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email) ||
        _passwordController.text.isEmpty) {
      _error('Nhập email hợp lệ và mật khẩu.');
      return;
    }
    if (!_isLogin &&
        (_nameController.text.trim().isEmpty ||
            !AuthSession.strongPassword(_passwordController.text) ||
            _passwordController.text != _confirmController.text)) {
      _error('Nhập họ tên và mật khẩu mạnh; xác nhận mật khẩu phải khớp.');
      return;
    }
    setState(() => _busy = true);
    try {
      final auth = AuthSession.instance;
      if (auth.pendingVerification &&
          auth.pendingEmail?.toLowerCase() == email.toLowerCase()) {
        final verified = await Navigator.push<bool>(
          context,
          MaterialPageRoute(builder: (_) => const VerifyEmailScreen()),
        );
        if (verified != true) return;
      } else if (_isLogin) {
        await auth.login(email, _passwordController.text);
      } else {
        await auth.register(
          _nameController.text,
          email,
          _passwordController.text,
        );
        if (!mounted) return;
        final verified = await Navigator.push<bool>(
          context,
          MaterialPageRoute(builder: (_) => const VerifyEmailScreen()),
        );
        if (verified != true) return;
      }
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const HomeScreen()),
        (_) => false,
      );
    } catch (e) {
      if (mounted) _error(e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _error(String text) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: LocalizedText(text)));

  @override
  Widget build(BuildContext context) {
    final reducedMotion = MediaQuery.disableAnimationsOf(context);
    final top = MediaQuery.paddingOf(context).top;
    final screenHeight = MediaQuery.sizeOf(context).height;
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final form = RepaintBoundary(
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(24, top + 155, 24, 20),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: (constraints.maxHeight - top - 175).clamp(
                    0.0,
                    double.infinity,
                  ),
                ),
                child: IntrinsicHeight(
                  child: AutofillGroup(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children:
                          [
                            LocalizedText(
                              _isLogin ? 'Đăng nhập' : 'Đăng ký',
                              style: AppTypography.headlineMd().copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 24),
                            if (!_isLogin) ...[
                              _field(
                                'Họ và tên',
                                'Họ và tên của bạn',
                                _nameController,
                                autofill: AutofillHints.name,
                              ),
                              const SizedBox(height: 16),
                            ],
                            _field(
                              'Email',
                              'Nhập địa chỉ email',
                              _emailController,
                              keyboard: TextInputType.emailAddress,
                              autofill: AutofillHints.email,
                            ),
                            const SizedBox(height: 16),
                            _field(
                              'Mật khẩu',
                              'Nhập mật khẩu',
                              _passwordController,
                              password: true,
                              autofill: _isLogin
                                  ? AutofillHints.password
                                  : AutofillHints.newPassword,
                            ),
                            if (!_isLogin) ...[
                              const SizedBox(height: 16),
                              _field(
                                'Xác nhận mật khẩu',
                                'Nhập lại mật khẩu',
                                _confirmController,
                                password: true,
                              ),
                              const LocalizedText(
                                'Mật khẩu 8–100 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.',
                              ),
                            ],
                            if (_busy) const LinearProgressIndicator(),
                            if (_isLogin)
                              Align(
                                alignment: Alignment.centerRight,
                                child: TextButton(
                                  style: TextButton.styleFrom(
                                    foregroundColor: AppColors.secondary,
                                  ),
                                  onPressed: () => Navigator.push(
                                    context,
                                    MaterialPageRoute<void>(
                                      builder: (_) =>
                                          const ForgotPasswordScreen(),
                                    ),
                                  ),
                                  child: const LocalizedText('Quên mật khẩu?'),
                                ),
                              )
                            else
                              const SizedBox(height: 20),
                            FilledButton(
                              style: FilledButton.styleFrom(
                                backgroundColor: AppColors.secondary,
                                foregroundColor: Colors.white,
                                minimumSize: const Size(double.infinity, 52),
                                textStyle: AppTypography.bodyMd(
                                  fontWeight: FontWeight.w600,
                                ),
                                shape: const StadiumBorder(),
                              ),
                              onPressed: _busy ? null : _submit,
                              child: LocalizedText(
                                _isLogin ? 'Đăng nhập' : 'Đăng ký',
                              ),
                            ),
                            const SizedBox(height: 26),
                            Row(
                              children: [
                                const Expanded(
                                  child: Divider(
                                    color: AppColors.outlineVariant,
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 14,
                                  ),
                                  child: LocalizedText(
                                    'Hoặc',
                                    style: AppTypography.bodySm(
                                      color: AppColors.outline,
                                    ),
                                  ),
                                ),
                                const Expanded(
                                  child: Divider(
                                    color: AppColors.outlineVariant,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                _social(
                                  'Facebook',
                                  const Icon(
                                    Icons.facebook,
                                    color: Color(0xFF1877F2),
                                    size: 28,
                                  ),
                                ),
                                const SizedBox(width: 18),
                                _social(
                                  'Google',
                                  const Text(
                                    'G',
                                    style: TextStyle(
                                      fontSize: 26,
                                      fontWeight: FontWeight.w700,
                                      color: Color(0xFF4285F4),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            LocalizedText(
                              'Facebook sắp ra mắt',
                              textAlign: TextAlign.center,
                              style: AppTypography.bodySm(
                                color: AppColors.outline,
                              ).copyWith(fontSize: 11),
                            ),
                            const SizedBox(height: 40),
                            const Spacer(),
                            Wrap(
                              alignment: WrapAlignment.center,
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                LocalizedText(
                                  _isLogin
                                      ? 'Chưa có tài khoản?'
                                      : 'Đã có tài khoản?',
                                  style: AppTypography.bodySm(
                                    color: AppColors.outline,
                                  ),
                                ),
                                TextButton(
                                  style: TextButton.styleFrom(
                                    foregroundColor: AppColors.secondary,
                                  ),
                                  onPressed: () =>
                                      setState(() => _isLogin = !_isLogin),
                                  child: LocalizedText(
                                    _isLogin ? 'Đăng ký' : 'Đăng nhập',
                                  ),
                                ),
                              ],
                            ),
                          ].asMap().entries.map((entry) {
                            if (entry.value is Spacer ||
                                entry.value is SizedBox)
                              return entry.value;
                            return AnimatedBuilder(
                              animation: _intro,
                              child: entry.value,
                              builder: (context, child) {
                                final t = reducedMotion ? 1.0 : _intro.value;
                                final start =
                                    .82 +
                                    (entry.key / 35).clamp(0.0, 1.0) * .04;
                                final p = _phase(t, start, start + .075);
                                return Opacity(
                                  opacity: p,
                                  child: Transform.translate(
                                    offset: Offset(0, -12 * (1 - p)),
                                    child: child,
                                  ),
                                );
                              },
                            );
                          }).toList(),
                    ),
                  ),
                ),
              ),
            ),
          );
          return AnimatedBuilder(
            animation: _intro,
            child: form,
            builder: (context, child) {
              final t = reducedMotion ? 1.0 : _intro.value;
              final enter = _phase(t, 0, 1200 / 5400, Curves.easeInOutCubic);
              final exit = _phase(
                t,
                2700 / 5400,
                3000 / 5400,
                Curves.easeInCubic,
              );
              final lift = _phase(
                t,
                3050 / 5400,
                4000 / 5400,
                Curves.easeInOutCubic,
              );
              final heading = _phase(
                t,
                4050 / 5400,
                4425 / 5400,
                Curves.easeInOutCubic,
              );
              return Stack(
                clipBehavior: Clip.hardEdge,
                children: [
                  IgnorePointer(ignoring: t < .935, child: child!),
                  IgnorePointer(
                    child: ClipPath(
                      clipper: _LoginHeaderClipper(lift),
                      child: Container(
                        key: const ValueKey('login-curtain'),
                        height: screenHeight * (1 - lift) + (top + 156) * lift,
                        width: double.infinity,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                  if (t < 3050 / 5400)
                    Positioned.fill(
                      child: IgnorePointer(
                        child: Center(
                          child: Opacity(
                            opacity: enter * (1 - exit),
                            child: Transform.translate(
                              offset: Offset(
                                MediaQuery.sizeOf(context).width * exit,
                                30 * (1 - enter),
                              ),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const DanaSeaLogo(
                                    size: 64,
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    'DANASEA',
                                    key: const ValueKey('intro-wordmark'),
                                    style: AppTypography.headlineMd(
                                      color: Colors.white,
                                    ).copyWith(fontSize: 23, letterSpacing: 5),
                                  ),
                                  const SizedBox(height: 8),
                                  LocalizedText(
                                    'Chạm sóng biển, mở chuyến đi riêng',
                                    style: AppTypography.bodySm(
                                      color: const Color(0xFFD5F0F2),
                                    ).copyWith(fontSize: 10),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  if (t >= 4000 / 5400)
                    Positioned(
                      left: 24,
                      right: 24,
                      top: top + 22,
                      child: IgnorePointer(
                        child: Opacity(
                          opacity: heading,
                          child: Transform.translate(
                            offset: Offset(0, 8 * (1 - heading)),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'DANASEA',
                                  style: AppTypography.headlineMd(
                                    color: Colors.white,
                                  ).copyWith(fontSize: 22, letterSpacing: 4),
                                ),
                                const SizedBox(height: 6),
                                LocalizedText(
                                  'Chào mừng trở lại!',
                                  style: AppTypography.bodySm(
                                    color: const Color(0xFFD5F0F2),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          );
        },
      ),
    );
  }

  double _phase(
    double t,
    double start,
    double end, [
    Curve curve = Curves.easeOutCubic,
  ]) => curve.transform(((t - start) / (end - start)).clamp(0.0, 1.0));

  Widget _field(
    String label,
    String hint,
    TextEditingController controller, {
    bool password = false,
    TextInputType? keyboard,
    String? autofill,
  }) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      LocalizedText(
        label,
        style: AppTypography.bodySm(
          color: AppColors.onSurface,
          fontWeight: FontWeight.w600,
        ),
      ),
      const SizedBox(height: 8),
      TextField(
        controller: controller,
        cursorColor: AppColors.secondary,
        onTapOutside: (_) => FocusManager.instance.primaryFocus?.unfocus(),
        obscureText: password && _obscurePassword,
        keyboardType: keyboard,
        autofillHints: autofill == null ? null : [autofill],
        autocorrect: !password,
        enableSuggestions: !password,
        textInputAction: password ? TextInputAction.done : TextInputAction.next,
        onSubmitted: password ? (_) => _submit() : null,
        style: AppTypography.bodyMd(),
        decoration: InputDecoration(
          hintText: tr(context, hint),
          hintStyle: AppTypography.bodySm(color: AppColors.outline),
          filled: true,
          fillColor: Colors.white,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 16,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(13),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(13),
            borderSide: BorderSide.none,
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(13),
            borderSide: const BorderSide(color: AppColors.secondary),
          ),
          suffixIcon: password
              ? IconButton(
                  tooltip: tr(
                    context,
                    _obscurePassword ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                  ),
                  onPressed: () =>
                      setState(() => _obscurePassword = !_obscurePassword),
                  icon: Icon(
                    _obscurePassword
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    size: 20,
                    color: AppColors.outline,
                  ),
                )
              : null,
        ),
      ),
    ],
  );

  Widget _social(String provider, Widget icon) => IconButton(
    tooltip: provider == 'Google' ? 'Google' : 'Facebook — Sắp ra mắt',
    onPressed: provider == 'Google' && !_busy ? _googleLogin : null,
    icon: icon,
    style: IconButton.styleFrom(
      backgroundColor: Colors.white,
      fixedSize: const Size(54, 54),
    ),
  );
}

class _LoginHeaderClipper extends CustomClipper<Path> {
  const _LoginHeaderClipper(this.progress);
  final double progress;
  @override
  Path getClip(Size size) => Path()
    ..lineTo(0, size.height - 54 * progress)
    ..cubicTo(
      size.width * .30,
      size.height,
      size.width * .65,
      size.height,
      size.width,
      size.height - 129 * progress,
    )
    ..lineTo(size.width, 0)
    ..close();
  @override
  bool shouldReclip(_LoginHeaderClipper oldClipper) =>
      oldClipper.progress != progress;
}
