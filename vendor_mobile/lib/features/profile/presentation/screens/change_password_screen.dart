import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/vendor_button.dart';
import '../../../../core/widgets/vendor_text_field.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../auth/presentation/screens/forgot_password_screen.dart';

class ChangePasswordScreen extends StatefulWidget {
  const ChangePasswordScreen({super.key});

  @override
  State<ChangePasswordScreen> createState() => _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends State<ChangePasswordScreen> {
  final TextEditingController _currentPassController = TextEditingController();
  final TextEditingController _newPassController = TextEditingController();
  final TextEditingController _confirmPassController = TextEditingController();

  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  String? _errorMessage;
  bool _showSuccess = false;

  @override
  void dispose() {
    _currentPassController.dispose();
    _newPassController.dispose();
    _confirmPassController.dispose();
    super.dispose();
  }

  int _calculateStrength(String pass) {
    int score = 0;
    if (pass.length >= 8) score++;
    if (pass.contains(RegExp(r'[a-z]')) && pass.contains(RegExp(r'[A-Z]'))) score++;
    if (pass.contains(RegExp(r'[0-9]'))) score++;
    if (pass.contains(RegExp(r'[^A-Za-z0-9]'))) score++;
    return score;
  }

  void _submit() {
    setState(() {
      _errorMessage = null;
      _showSuccess = false;
    });

    final current = _currentPassController.text.trim();
    final newPass = _newPassController.text.trim();
    final confirm = _confirmPassController.text.trim();

    if (current.isEmpty) {
      setState(() {
        _errorMessage = 'Vui lòng điền mật khẩu hiện tại của bạn.';
      });
      return;
    }

    if (newPass.length < 8) {
      setState(() {
        _errorMessage = 'Mật khẩu mới phải có ít nhất 8 ký tự.';
      });
      return;
    }

    if (newPass != confirm) {
      setState(() {
        _errorMessage = 'Mật khẩu xác nhận không trùng khớp.';
      });
      return;
    }

    setState(() {
      _showSuccess = true;
      _currentPassController.clear();
      _newPassController.clear();
      _confirmPassController.clear();
    });
    ToastNotification.showSuccess(context, 'Đổi mật khẩu thành công!');
  }

  @override
  Widget build(BuildContext context) {
    final newPass = _newPassController.text;
    final strength = _calculateStrength(newPass);
    final isMatch = _confirmPassController.text.isNotEmpty && _confirmPassController.text == newPass;

    final hasLen = newPass.length >= 8;
    final hasUpperLower = newPass.contains(RegExp(r'[a-z]')) && newPass.contains(RegExp(r'[A-Z]'));
    final hasNumber = newPass.contains(RegExp(r'[0-9]'));
    final hasSpecial = newPass.contains(RegExp(r'[^A-Za-z0-9]'));

    return PopScope(
      onPopInvokedWithResult: (didPop, _) {
        FocusManager.instance.primaryFocus?.unfocus();
      },
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => FocusScope.of(context).unfocus(),
        child: Scaffold(
          backgroundColor: AppColors.background,
          appBar: AppBar(
            backgroundColor: AppColors.surface,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.arrow_back),
              onPressed: () {
                FocusScope.of(context).unfocus();
                Navigator.of(context).maybePop();
              },
            ),
            title: LocalizedText(
              'Đổi mật khẩu',
              style: AppTypography.headlineSm.copyWith(
                color: AppColors.onSurface,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          body: SafeArea(
            child: SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
              child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Subtle guidance text
              Padding(
                padding: const EdgeInsets.only(bottom: 16, left: 4, right: 4),
                child: LocalizedText(
                  'Để bảo mật tài khoản, vui lòng sử dụng mật khẩu mạnh có ít nhất 8 ký tự, kết hợp chữ hoa, chữ thường, số và ký tự đặc biệt.',
                  style: AppTypography.bodySm.copyWith(
                    color: AppColors.onSurfaceVariant,
                    height: 1.4,
                  ),
                ),
              ),

              // Alert banners
              if (_errorMessage != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer,
                    borderRadius: BorderRadius.circular(AppShapes.rMd),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.warning_amber_rounded, color: AppColors.error, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const LocalizedText(
                              'Không thể cập nhật mật khẩu',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onErrorContainer),
                            ),
                            const SizedBox(height: 2),
                            LocalizedText(
                              _errorMessage!,
                              style: const TextStyle(fontSize: 11, color: AppColors.onErrorContainer),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        icon: const Icon(Icons.close, size: 16, color: AppColors.onErrorContainer),
                        onPressed: () => setState(() => _errorMessage = null),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              if (_showSuccess) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFE8F5E9),
                    borderRadius: BorderRadius.circular(AppShapes.rMd),
                    border: Border.all(color: const Color(0xFFA5D6A7)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.check_circle_rounded, color: Color(0xFF2E7D32), size: 20),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: LocalizedText(
                          'Mật khẩu của bạn đã được thay đổi thành công.',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF1B5E20)),
                        ),
                      ),
                      IconButton(
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        icon: const Icon(Icons.close, size: 16, color: Color(0xFF2E7D32)),
                        onPressed: () => setState(() => _showSuccess = false),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              // Form fields card
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(AppShapes.rLg),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.04),
                      blurRadius: 10,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Field 1: Current password
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Expanded(
                          child: LocalizedText(
                            'Mật khẩu hiện tại',
                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        InkWell(
                          onTap: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
                            );
                          },
                          child: const LocalizedText(
                            'Quên mật khẩu?',
                            style: TextStyle(fontSize: 12, color: AppColors.secondary, fontWeight: FontWeight.w600),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    VendorTextField(
                      controller: _currentPassController,
                      label: '',
                      hint: 'Nhập mật khẩu đang dùng',
                      prefixIcon: Icons.lock_outline,
                      obscureText: _obscureCurrent,
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscureCurrent ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                          color: AppColors.tertiary,
                          size: 20,
                        ),
                        onPressed: () => setState(() => _obscureCurrent = !_obscureCurrent),
                      ),
                    ),
                    const SizedBox(height: 20),

                    // Field 2: New password
                    const LocalizedText(
                      'Mật khẩu mới',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                    ),
                    const SizedBox(height: 8),
                    VendorTextField(
                      controller: _newPassController,
                      label: '',
                      hint: 'Nhập mật khẩu mới',
                      prefixIcon: Icons.lock_reset_outlined,
                      obscureText: _obscureNew,
                      onChanged: (val) => setState(() {}),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscureNew ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                          color: AppColors.tertiary,
                          size: 20,
                        ),
                        onPressed: () => setState(() => _obscureNew = !_obscureNew),
                      ),
                    ),
                    const SizedBox(height: 10),

                    if (newPass.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      // Strength indicator bar & label
                      Row(
                        children: [
                          Expanded(
                            child: Row(
                              children: List.generate(4, (i) {
                                Color c = AppColors.surfaceContainerHighest;
                                if (strength == 1 && i == 0) c = AppColors.error;
                                if (strength == 2 && i <= 1) c = const Color(0xFFF57C00);
                                if (strength == 3 && i <= 2) c = AppColors.secondary;
                                if (strength >= 4) c = const Color(0xFF2E7D32);
                                return Expanded(
                                  child: Container(
                                    height: 3.5,
                                    margin: EdgeInsets.only(right: i < 3 ? 5 : 0),
                                    decoration: BoxDecoration(
                                      color: c,
                                      borderRadius: BorderRadius.circular(2),
                                    ),
                                  ),
                                );
                              }),
                            ),
                          ),
                          const SizedBox(width: 12),
                          LocalizedText(
                            strength <= 1
                                ? 'Yếu'
                                : strength <= 2
                                    ? 'Trung bình'
                                    : strength == 3
                                        ? 'Khá'
                                        : 'Mạnh',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: strength <= 1
                                  ? AppColors.error
                                  : strength <= 2
                                      ? const Color(0xFFF57C00)
                                      : strength == 3
                                          ? AppColors.secondary
                                          : const Color(0xFF2E7D32),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      // Compact condition badges
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          _buildConditionBadge(hasLen, 'Tối thiểu 8 ký tự'),
                          _buildConditionBadge(hasUpperLower, 'Chữ hoa & thường'),
                          _buildConditionBadge(hasNumber, 'Chữ số (0-9)'),
                          _buildConditionBadge(hasSpecial, 'Ký tự đặc biệt (@#\$)'),
                        ],
                      ),
                    ] else ...[
                      const SizedBox(height: 6),
                      Padding(
                        padding: const EdgeInsets.only(left: 2),
                        child: Row(
                          children: [
                            Icon(Icons.info_outline, size: 13, color: AppColors.onSurfaceVariant.withValues(alpha: 0.7)),
                            const SizedBox(width: 5),
                            Expanded(
                              child: LocalizedText(
                                'Tối thiểu 8 ký tự gồm chữ hoa, chữ thường, số và ký tự đặc biệt',
                                style: TextStyle(
                                  fontSize: 11,
                                  color: AppColors.onSurfaceVariant.withValues(alpha: 0.7),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 20),

                    // Field 3: Confirm password
                    const LocalizedText(
                      'Xác nhận mật khẩu mới',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                    ),
                    const SizedBox(height: 8),
                    VendorTextField(
                      controller: _confirmPassController,
                      label: '',
                      hint: 'Nhập lại mật khẩu mới',
                      prefixIcon: Icons.check_circle_outline,
                      obscureText: _obscureConfirm,
                      onChanged: (val) => setState(() {}),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscureConfirm ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                          color: AppColors.tertiary,
                          size: 20,
                        ),
                        onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
                      ),
                    ),
                    if (_confirmPassController.text.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 6, left: 4),
                        child: Row(
                          children: [
                            Icon(
                              isMatch ? Icons.check_circle : Icons.cancel_outlined,
                              size: 14,
                              color: isMatch ? const Color(0xFF2E7D32) : AppColors.error,
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                isMatch ? 'Mật khẩu xác nhận hoàn toàn trùng khớp' : 'Mật khẩu chưa trùng khớp',
                                style: TextStyle(
                                  fontSize: 11,
                                  color: isMatch ? const Color(0xFF2E7D32) : AppColors.error,
                                  fontWeight: FontWeight.bold,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                    const SizedBox(height: 24),

                    // Actions
                    VendorButton(
                      text: 'Cập nhật mật khẩu',
                      icon: Icons.check,
                      onPressed: _submit,
                    ),
                  ],
                ),
              ),
            ],
          ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildConditionBadge(bool passed, String text) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: passed ? const Color(0xFFE8F5E9) : AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: passed ? const Color(0xFFA5D6A7) : AppColors.borderSubtle.withValues(alpha: 0.6),
          width: 0.8,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            passed ? Icons.check : Icons.circle,
            size: passed ? 12 : 5,
            color: passed ? const Color(0xFF2E7D32) : AppColors.onSurfaceVariant.withValues(alpha: 0.4),
          ),
          const SizedBox(width: 5),
          LocalizedText(
            text,
            style: TextStyle(
              fontSize: 11,
              fontWeight: passed ? FontWeight.w600 : FontWeight.normal,
              color: passed ? const Color(0xFF1B5E20) : AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
