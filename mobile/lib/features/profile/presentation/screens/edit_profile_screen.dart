import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class EditProfileScreen extends StatefulWidget {
  const EditProfileScreen({super.key});

  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  late TextEditingController _nameController;
  late TextEditingController _phoneController;
  late TextEditingController _emailController;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final user = AuthSession.instance.profile ?? <String, dynamic>{};
    _nameController = TextEditingController(
      text: user['fullName']?.toString() ?? '',
    );
    _phoneController = TextEditingController(
      text: user['phone']?.toString() ?? '0905 123 456',
    );
    _emailController = TextEditingController(
      text: user['email']?.toString() ?? '',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _saveProfile() async {
    if (_saving) return;
    final name = _nameController.text.trim();
    if (name.isEmpty || name.length > 255) {
      setState(() => _error = 'Họ và tên phải có từ 1 đến 255 ký tự.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await AuthSession.instance.updateProfile(fullName: name);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã cập nhật thông tin hồ sơ thành công!'),
          backgroundColor: AppColors.secondary,
        ),
      );
      Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final avatar = AuthSession.instance.profile?['avatarUrl']?.toString();
    final hasAvatar = avatar != null && avatar.isNotEmpty;
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: LocalizedText(
          'Chỉnh sửa hồ sơ',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          children: [
            // AVATAR WITH CAMERA OVERLAY
            Center(
              child: Stack(
                children: [
                  CircleAvatar(
                    radius: 46,
                    backgroundColor: AppColors.secondaryContainer,
                    backgroundImage: hasAvatar ? NetworkImage(avatar) : null,
                    child: hasAvatar
                        ? null
                        : const Icon(Icons.person_outline, size: 40),
                  ),
                  Positioned(
                    bottom: 0,
                    right: 0,
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: const BoxDecoration(
                        color: AppColors.primary,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.camera_alt,
                        size: 16,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // FORM FIELDS
            _buildFieldGroup(
              label: 'Họ và tên',
              controller: _nameController,
              icon: Icons.person_outline,
            ),
            const SizedBox(height: 16),
            _buildFieldGroup(
              label: 'Số điện thoại',
              controller: _phoneController,
              icon: Icons.phone_outlined,
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 16),
            _buildFieldGroup(
              label: 'Email (Đã xác minh)',
              controller: _emailController,
              icon: Icons.mail_outline,
              readOnly: true,
              trailing: const Icon(
                Icons.check_circle,
                color: AppColors.secondary,
                size: 20,
              ),
            ),
            const SizedBox(height: 32),

            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: LocalizedText(
                  _error!,
                  style: const TextStyle(color: Colors.red),
                ),
              ),
            if (_saving) const LinearProgressIndicator(),

            // SAVE BUTTON
            AppPillButton(
              label: 'Lưu thay đổi',
              variant: AppButtonVariant.primary,
              width: double.infinity,
              onPressed: _saving ? null : _saveProfile,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFieldGroup({
    required String label,
    required TextEditingController controller,
    required IconData icon,
    bool readOnly = false,
    Widget? trailing,
    TextInputType? keyboardType,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LocalizedText(
          label,
          style: AppTypography.labelMd(color: AppColors.onSurface),
        ),
        const SizedBox(height: 6),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: readOnly
                ? AppColors.surfaceContainerLow
                : AppColors.surfaceContainerLowest,
            borderRadius: AppShapes.radiusDefault,
            border: Border.all(color: AppColors.borderSubtle),
          ),
          child: Row(
            children: [
              Icon(icon, size: 20, color: AppColors.tertiary),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: controller,
                  readOnly: readOnly || _saving,
                  keyboardType: keyboardType,
                  decoration: const InputDecoration(
                    border: InputBorder.none,
                    isDense: true,
                    contentPadding: EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
              ?trailing,
            ],
          ),
        ),
      ],
    );
  }
}
