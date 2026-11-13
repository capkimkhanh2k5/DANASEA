import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';

class EditProfileBankScreen extends StatefulWidget {
  const EditProfileBankScreen({super.key});

  @override
  State<EditProfileBankScreen> createState() => _EditProfileBankScreenState();
}

class _EditProfileBankScreenState extends State<EditProfileBankScreen> {
  final VendorProfileRepository _profileRepo = VendorProfileRepository();
  final VendorStore _db = VendorStore.instance;

  late TextEditingController _bankAccountController;
  late TextEditingController _bankHolderController;
  String _selectedBank = 'Vietcombank';

  bool _isSaving = false;
  bool _showSuccessAlert = false;

  final List<String> _bankOptions = [
    'Vietcombank',
    'BIDV',
    'Techcombank',
    'MB Bank',
    'VietinBank',
    'ACB',
    'VPBank',
  ];

  @override
  void initState() {
    super.initState();
    final vendor = _db.currentVendor;
    _bankAccountController = TextEditingController(text: vendor.bankAccountNumber ?? '0041000889988');
    _bankHolderController = TextEditingController(text: vendor.bankAccountHolder ?? 'TRAN HAI DANG');
    if (vendor.bankName != null && _bankOptions.contains(vendor.bankName)) {
      _selectedBank = vendor.bankName!;
    }
  }

  @override
  void dispose() {
    _bankAccountController.dispose();
    _bankHolderController.dispose();
    super.dispose();
  }

  void _openBankSelectorBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(ctx).size.height * 0.7,
          ),
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 14,
            bottom: MediaQuery.of(ctx).padding.bottom + 16,
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
                    'Chọn ngân hàng thụ hưởng',
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
              const SizedBox(height: 10),
              Flexible(
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: _bankOptions.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final bank = _bankOptions[index];
                    final isSelected = bank == _selectedBank;
                    return InkWell(
                      onTap: () {
                        setState(() => _selectedBank = bank);
                        Navigator.pop(ctx);
                      },
                      borderRadius: BorderRadius.circular(12),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
                        child: Row(
                          children: [
                            Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: isSelected
                                    ? AppColors.secondaryContainer
                                    : AppColors.surfaceContainerLow,
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                Icons.account_balance,
                                size: 18,
                                color: isSelected
                                    ? AppColors.secondary
                                    : AppColors.tertiary,
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: LocalizedText(
                                bank,
                                style: AppTypography.bodyMd.copyWith(
                                  color: isSelected ? AppColors.secondary : AppColors.onSurface,
                                  fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                                ),
                              ),
                            ),
                            if (isSelected)
                              const Icon(
                                Icons.check_circle_rounded,
                                color: AppColors.secondary,
                                size: 22,
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _handleSave() async {
    final bankAccount = _bankAccountController.text.trim();
    final bankHolder = _bankHolderController.text.trim();

    if (bankAccount.isEmpty || bankHolder.isEmpty) {
      ToastNotification.show(context, message: 'Vui lòng nhập đầy đủ thông tin tài khoản ngân hàng.', type: ToastType.error);
      return;
    }

    setState(() => _isSaving = true);
    try {
      await _profileRepo.updateBankProfile(
        bankName: _selectedBank,
        bankAccountNumber: bankAccount,
        bankAccountHolder: bankHolder.toUpperCase(),
      );

      if (!mounted) return;
      setState(() {
        _isSaving = false;
        _showSuccessAlert = true;
      });

      ToastNotification.show(
        context,
        message: 'Cập nhật tài khoản ngân hàng thành công!',
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
              'Tài khoản ngân hàng',
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
            // Success alert if triggered
            if (_showSuccessAlert) ...[
              Container(
                margin: const EdgeInsets.only(bottom: 16),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFE8F5E9),
                  borderRadius: BorderRadius.circular(AppShapes.rLg),
                  border: Border.all(color: const Color(0xFFA5D6A7)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle_rounded, color: Color(0xFF2E7D32), size: 20),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            'Đã cập nhật tài khoản nhận tiền!',
                            style: AppTypography.labelLg.copyWith(
                              color: const Color(0xFF1B5E20),
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          LocalizedText(
                            'Các khoản thanh toán đối soát tiếp theo sẽ chuyển vào tài khoản này.',
                            style: AppTypography.bodySm.copyWith(
                              color: const Color(0xFF2E7D32),
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, size: 18, color: Color(0xFF2E7D32)),
                      onPressed: () => setState(() => _showSuccessAlert = false),
                    ),
                  ],
                ),
              ),
            ],

            // SECTION: Bank Account for Payout
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
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            Container(
                              width: 32,
                              height: 32,
                              decoration: const BoxDecoration(
                                color: AppColors.primaryFixed,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.account_balance, color: AppColors.primary, size: 18),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: LocalizedText(
                                'Tài khoản nhận tiền quyết toán',
                                style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(AppShapes.rFull),
                        ),
                        child: LocalizedText(
                          'Chu kỳ: Tuần/Tháng',
                          style: AppTypography.labelSm.copyWith(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Visual Payout Card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [AppColors.secondary, Color(0xFF004F57)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(AppShapes.rLg),
                      boxShadow: const [
                        BoxShadow(color: Color(0x24006874), blurRadius: 10, offset: Offset(0, 4)),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  ClipOval(
                                    child: Image.asset(
                                      'assets/danasea-logo.png',
                                      width: 22,
                                      height: 22,
                                      fit: BoxFit.contain,
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: LocalizedText(
                                      'DANASEA PAYOUT',
                                      style: AppTypography.labelSm.copyWith(
                                        color: AppColors.secondaryFixed,
                                        letterSpacing: 1.0,
                                        fontWeight: FontWeight.bold,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Icon(Icons.contactless, color: Colors.white.withAlpha(180), size: 22),
                          ],
                        ),
                        const SizedBox(height: 18),
                        LocalizedText(
                          'SỐ TÀI KHOẢN THỤ HƯỞNG',
                          style: AppTypography.labelSm.copyWith(color: AppColors.secondaryFixed),
                        ),
                        const SizedBox(height: 4),
                        LocalizedText(
                          _bankAccountController.text.isNotEmpty ? _bankAccountController.text : '•••• •••• ••••',
                          style: AppTypography.headlineSm.copyWith(
                            color: Colors.white,
                            letterSpacing: 2.0,
                            fontFamily: 'monospace',
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 18),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    'CHỦ TÀI KHOẢN',
                                    style: AppTypography.labelSm.copyWith(color: AppColors.secondaryFixed),
                                  ),
                                  const SizedBox(height: 2),
                                  LocalizedText(
                                    _bankHolderController.text.isNotEmpty ? _bankHolderController.text.toUpperCase() : 'CHƯA NHẬP',
                                    style: AppTypography.labelLg.copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(
                                color: Colors.white.withAlpha(40),
                                borderRadius: BorderRadius.circular(AppShapes.rFull),
                              ),
                              child: LocalizedText(
                                _selectedBank,
                                style: AppTypography.labelSm.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Bank selector
                  LocalizedText('Ngân hàng thụ hưởng *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  InkWell(
                    onTap: _openBankSelectorBottomSheet,
                    borderRadius: BorderRadius.circular(AppShapes.rFull),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.account_balance, size: 20, color: AppColors.secondary),
                          const SizedBox(width: 12),
                          Expanded(
                            child: LocalizedText(
                              _selectedBank,
                              style: AppTypography.bodyMd.copyWith(
                                color: AppColors.onSurface,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          const Icon(Icons.keyboard_arrow_down, color: AppColors.tertiary),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Account Number Input
                  LocalizedText('Số tài khoản *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _bankAccountController,
                    keyboardType: TextInputType.number,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.pin, color: AppColors.tertiary),
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

                  // Account Holder Name Input
                  LocalizedText('Tên chủ tài khoản (in hoa không dấu) *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _bankHolderController,
                    textCapitalization: TextCapitalization.characters,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.person, color: AppColors.tertiary),
                      filled: true,
                      fillColor: AppColors.surfaceContainerLow,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                  const SizedBox(height: 14),

                  // Info Note
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(AppShapes.rMd),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(Icons.info_outline, size: 16, color: AppColors.secondary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: LocalizedText(
                            'Doanh thu dịch vụ sau khi đối soát thành công sẽ được chuyển tự động vào tài khoản này theo chu kỳ quyết toán.',
                            style: AppTypography.bodySm.copyWith(color: AppColors.tertiary, fontSize: 11),
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
                              'Lưu tài khoản ngân hàng',
                              style: AppTypography.labelLg.copyWith(color: Colors.white),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
              ),
            ),
            const SizedBox(height: 40),
          ],
        ),
          ),
        ),
      ),
    );
  }
}
