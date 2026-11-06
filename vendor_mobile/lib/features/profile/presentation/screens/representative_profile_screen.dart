import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/data/vendor_mock_repositories.dart';

class RepresentativeProfileScreen extends StatefulWidget {
  const RepresentativeProfileScreen({super.key});

  @override
  State<RepresentativeProfileScreen> createState() => _RepresentativeProfileScreenState();
}

class _RepresentativeProfileScreenState extends State<RepresentativeProfileScreen> {
  final VendorProfileRepository _profileRepo = VendorProfileRepository();
  final VendorMockDatabase _db = VendorMockDatabase.instance;

  late TextEditingController _fullNameController;
  late TextEditingController _phoneController;
  String? _avatarUrl;

  bool _isSaving = false;

  final List<String> _avatarPresets = const [
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300&auto=format&fit=crop&q=80',
  ];

  @override
  void initState() {
    super.initState();
    final user = _db.currentUser;
    _fullNameController = TextEditingController(text: user.fullName);
    _phoneController = TextEditingController(text: user.phone ?? '');
    _avatarUrl = user.avatarUrl;
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  void _openAvatarPicker() {
    final urlController = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 14,
            bottom: MediaQuery.of(ctx).padding.bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: const Color(0xFFCBD5E1),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  LocalizedText(
                    'Chọn ảnh đại diện',
                    style: AppTypography.headlineSm.copyWith(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20, color: AppColors.tertiary),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              LocalizedText(
                'Chọn một trong các ảnh chân dung chuyên nghiệp:',
                style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 14,
                runSpacing: 14,
                children: _avatarPresets.map((preset) {
                  final isSelected = _avatarUrl == preset;
                  return InkWell(
                    onTap: () {
                      setState(() => _avatarUrl = preset);
                      Navigator.pop(ctx);
                      ToastNotification.show(
                        context,
                        message: 'Đã chọn ảnh đại diện mới. Bấm "Lưu" để cập nhật.',
                        type: ToastType.success,
                      );
                    },
                    borderRadius: BorderRadius.circular(40),
                    child: Stack(
                      children: [
                        Container(
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: isSelected ? AppColors.secondary : Colors.transparent,
                              width: 3,
                            ),
                          ),
                          child: CircleAvatar(
                            radius: 34,
                            backgroundImage: NetworkImage(preset),
                          ),
                        ),
                        if (isSelected)
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: Container(
                              width: 22,
                              height: 22,
                              decoration: const BoxDecoration(
                                color: AppColors.secondary,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.check, size: 14, color: Colors.white),
                            ),
                          ),
                      ],
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 20),
              const Divider(height: 1),
              const SizedBox(height: 16),
              LocalizedText(
                'Hoặc nhập liên kết ảnh trực tiếp (URL):',
                style: AppTypography.labelMd.copyWith(color: AppColors.tertiary),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: urlController,
                      decoration: InputDecoration(
                        hintText: 'https://...',
                        prefixIcon: const Icon(Icons.link, size: 20, color: AppColors.tertiary),
                        filled: true,
                        fillColor: AppColors.surfaceContainerLow,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AppShapes.rFull),
                          borderSide: BorderSide.none,
                        ),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: () {
                      final text = urlController.text.trim();
                      if (text.startsWith('http')) {
                        setState(() => _avatarUrl = text);
                        Navigator.pop(ctx);
                        ToastNotification.show(
                          context,
                          message: 'Đã áp dụng ảnh đại diện từ URL.',
                          type: ToastType.success,
                        );
                      } else {
                        ToastNotification.show(
                          context,
                          message: 'Vui lòng nhập đường dẫn URL ảnh hợp lệ (http/https).',
                          type: ToastType.error,
                        );
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    ),
                    child: const LocalizedText('Áp dụng'),
                  ),
                ],
              ),
              if (_avatarUrl != null) ...[
                const SizedBox(height: 12),
                Center(
                  child: TextButton.icon(
                    onPressed: () {
                      setState(() => _avatarUrl = null);
                      Navigator.pop(ctx);
                      ToastNotification.show(
                        context,
                        message: 'Đã đặt lại về ảnh mặc định.',
                        type: ToastType.info,
                      );
                    },
                    icon: const Icon(Icons.delete_outline, size: 16, color: AppColors.error),
                    label: const LocalizedText('Xóa ảnh tùy chỉnh, dùng mặc định', style: TextStyle(color: AppColors.error)),
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }

  Future<void> _handleSave() async {
    final fullName = _fullNameController.text.trim();
    if (fullName.isEmpty) {
      ToastNotification.show(context, message: 'Vui lòng nhập họ và tên người đại diện.', type: ToastType.error);
      return;
    }

    setState(() => _isSaving = true);
    try {
      await _profileRepo.updateVendorProfile(
        fullName: fullName,
        phone: _phoneController.text.trim(),
        avatarUrl: _avatarUrl,
      );

      if (!mounted) return;
      setState(() => _isSaving = false);

      ToastNotification.show(
        context,
        message: 'Đã cập nhật thông tin người đại diện thành công!',
        type: ToastType.success,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isSaving = false);
      ToastNotification.show(context, message: 'Lỗi cập nhật: $e', type: ToastType.error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = _db.currentUser;

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
            title: LocalizedText(
              'Người đại diện pháp lý',
              style: AppTypography.headlineSm.copyWith(
                color: AppColors.onSurface,
                fontWeight: FontWeight.bold,
              ),
            ),
            backgroundColor: AppColors.surface,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.arrow_back),
              onPressed: () {
                FocusScope.of(context).unfocus();
                Navigator.maybePop(context);
              },
            ),
          ),
          body: SingleChildScrollView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rLg),
                boxShadow: const [
                  BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Avatar Zone
                  Row(
                    children: [
                      InkWell(
                        onTap: _openAvatarPicker,
                        borderRadius: BorderRadius.circular(40),
                        child: Stack(
                          children: [
                            CircleAvatar(
                              radius: 36,
                              backgroundColor: AppColors.surfaceContainer,
                              backgroundImage: (_avatarUrl != null && _avatarUrl!.isNotEmpty)
                                  ? NetworkImage(_avatarUrl!)
                                  : null,
                              child: (_avatarUrl == null || _avatarUrl!.isEmpty)
                                  ? const Icon(Icons.person, size: 36, color: AppColors.primary)
                                  : null,
                            ),
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: Container(
                                width: 26,
                                height: 26,
                                decoration: const BoxDecoration(
                                  color: AppColors.secondary,
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.camera_alt, color: Colors.white, size: 14),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            OutlinedButton.icon(
                              onPressed: _openAvatarPicker,
                              icon: const Icon(Icons.photo_library_outlined, size: 16),
                              label: const LocalizedText('Thay đổi ảnh'),
                              style: OutlinedButton.styleFrom(
                                foregroundColor: AppColors.onSurface,
                                side: const BorderSide(color: AppColors.outlineVariant),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                                ),
                              ),
                            ),
                            const SizedBox(height: 4),
                            LocalizedText(
                              'Chạm để chọn ảnh chân dung hoặc nhập liên kết ảnh.',
                              style: AppTypography.bodySm.copyWith(color: AppColors.tertiary, fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Full name
                  LocalizedText('Họ và tên người đại diện *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _fullNameController,
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.person_outline, color: AppColors.tertiary),
                      filled: true,
                      fillColor: AppColors.surfaceContainerLow,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Phone
                  LocalizedText('Số điện thoại liên hệ', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _phoneController,
                    keyboardType: TextInputType.phone,
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.phone_android, color: AppColors.tertiary),
                      filled: true,
                      fillColor: AppColors.surfaceContainerLow,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Email (Read-only)
                  LocalizedText('Email định danh tài khoản', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  TextField(
                    readOnly: true,
                    controller: TextEditingController(text: user.email),
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.mark_email_read, color: AppColors.tertiary),
                      filled: true,
                      fillColor: AppColors.surfaceContainer,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(AppShapes.rMd),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.info_outline, color: AppColors.tertiary, size: 16),
                        const SizedBox(width: 8),
                        Expanded(
                          child: LocalizedText(
                            'Email định danh liên kết với tài khoản đối tác, không thể thay đổi trực tiếp tại đây.',
                            style: AppTypography.bodySm.copyWith(color: AppColors.tertiary, fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Save Button
            SizedBox(
              width: double.infinity,
              height: 52,
              child: ElevatedButton(
                onPressed: _isSaving ? null : _handleSave,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  foregroundColor: Colors.white,
                  elevation: 2,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                ),
                child: _isSaving
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.save_outlined, size: 20),
                          const SizedBox(width: 8),
                          Flexible(
                            child: LocalizedText(
                              'Lưu thông tin người đại diện',
                              style: AppTypography.labelLg.copyWith(color: Colors.white),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
              ),
            ),
          ],
        ),
          ),
        ),
      ),
    );
  }
}
