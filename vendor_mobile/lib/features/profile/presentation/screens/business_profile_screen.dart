import '../../../../core/widgets/symbolic_vendor_avatar.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/widgets/vendor_button.dart';
import '../../../../core/widgets/vendor_text_field.dart';
import '../../../../core/widgets/toast_notification.dart';
import 'vendor_documents_screen.dart';
import 'edit_profile_bank_screen.dart';

class BusinessProfileScreen extends StatefulWidget {
  const BusinessProfileScreen({super.key});

  @override
  State<BusinessProfileScreen> createState() => _BusinessProfileScreenState();
}

class _BusinessProfileScreenState extends State<BusinessProfileScreen> {
  final _db = VendorStore.instance;

  late TextEditingController _businessNameController;
  late TextEditingController _addressController;
  late TextEditingController _taxCodeController;
  late TextEditingController _phoneController;
  late TextEditingController _descController;

  @override
  void initState() {
    super.initState();
    final v = _db.vendor;
    _businessNameController = TextEditingController(text: v.businessName);
    _addressController = TextEditingController(text: v.businessAddress ?? 'Lô 12 Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà, Đà Nẵng');
    _taxCodeController = TextEditingController(text: v.taxCode ?? '0402123456');
    _phoneController = TextEditingController(text: v.phone ?? '0905 888 999');
    _descController = TextEditingController(
      text: v.description ?? 'Câu lạc bộ thể thao biển chuyên nghiệp tại bờ biển Mỹ Khê và Bán đảo Sơn Trà. Chuyên cung cấp dịch vụ chèo SUP, lướt sóng, lặn ngắm san hô với đội ngũ cứu hộ và HLV chứng chỉ quốc tế.',
    );
  }

  @override
  void dispose() {
    _businessNameController.dispose();
    _addressController.dispose();
    _taxCodeController.dispose();
    _phoneController.dispose();
    _descController.dispose();
    super.dispose();
  }

  void _saveProfile() {
    final v = _db.vendor;
    final updated = v.copyWith(
      businessName: _businessNameController.text.trim(),
      businessAddress: _addressController.text.trim(),
      taxCode: _taxCodeController.text.trim(),
      phone: _phoneController.text.trim(),
      description: _descController.text.trim(),
    );
    _db.updateVendor(updated);
    ToastNotification.showSuccess(context, 'Đã lưu hồ sơ doanh nghiệp thành công');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const LocalizedText('Hồ sơ doanh nghiệp', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Club Profile Header Card
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  children: [
                    Row(
                      children: [
                        const SymbolicVendorAvatar(
                          radius: 29,
                          animate: false,
                          showVerifiedBadge: true,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                _businessNameController.text,
                                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              const Row(
                                children: [
                                  Icon(Icons.event_available, size: 13, color: AppColors.tertiary),
                                  SizedBox(width: 4),
                                  Flexible(
                                    child: LocalizedText(
                                      'Gia nhập: 15/03/2023',
                                      style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(color: AppColors.secondary.withOpacity(0.12), borderRadius: BorderRadius.circular(10)),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.verified, size: 12, color: AppColors.secondary),
                                    SizedBox(width: 3),
                                    Flexible(
                                      child: LocalizedText(
                                        'Hồ sơ pháp nhân chính thức',
                                        style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Stats ribbon
                    Row(
                      children: [
                        Expanded(child: _buildStat('4.9★', '142 Đánh giá', AppColors.secondary)),
                        const SizedBox(width: 8),
                        Expanded(child: _buildStat('98%', 'Nhận tour chuẩn', AppColors.primary)),
                        const SizedBox(width: 8),
                        Expanded(child: _buildStat('2.4k', 'Khách biển', AppColors.secondary)),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Verification status showcase (APPROVED)
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: const [
                        Icon(Icons.rule, size: 18, color: AppColors.secondary),
                        SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            'Trạng thái thẩm định hồ sơ',
                            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: AppColors.secondaryContainer.withOpacity(0.4), borderRadius: BorderRadius.circular(10)),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 32,
                            height: 32,
                            decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary),
                            child: const Icon(Icons.check, color: Colors.white, size: 18),
                          ),
                          const SizedBox(width: 10),
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Expanded(
                                      child: LocalizedText(
                                        'ĐÃ PHÊ DUYỆT (APPROVED)',
                                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    SizedBox(width: 6),
                                    LocalizedText('Đang áp dụng', style: TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.bold)),
                                  ],
                                ),
                                SizedBox(height: 2),
                                LocalizedText(
                                  'Hồ sơ Vendor đã được duyệt. Mỗi dịch vụ vẫn cần được xét duyệt riêng trước khi mở bán chính thức.',
                                  style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Quick Links to Legal Documents & Bank Account
              Container(
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  children: [
                    Material(
                      color: Colors.transparent,
                      child: InkWell(
                        onTap: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const VendorDocumentsScreen()),
                          );
                        },
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          child: Row(
                            children: [
                              Container(
                                width: 36,
                                height: 36,
                                decoration: BoxDecoration(
                                  color: AppColors.secondaryContainer.withOpacity(0.5),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.verified, color: AppColors.secondary, size: 20),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: const [
                                    LocalizedText('Giấy tờ & Pháp lý bến bãi', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                    SizedBox(height: 2),
                                    LocalizedText('Hồ sơ pháp nhân & chứng chỉ an toàn', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                                  ],
                                ),
                              ),
                              const Icon(Icons.chevron_right, color: AppColors.outlineVariant),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                    Material(
                      color: Colors.transparent,
                      child: InkWell(
                        onTap: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const EditProfileBankScreen()),
                          );
                        },
                        borderRadius: const BorderRadius.vertical(bottom: Radius.circular(16)),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          child: Row(
                            children: [
                              Container(
                                width: 36,
                                height: 36,
                                decoration: BoxDecoration(
                                  color: AppColors.primaryFixed.withOpacity(0.5),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.account_balance, color: AppColors.primary, size: 20),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: const [
                                    LocalizedText('Tài khoản ngân hàng nhận tiền', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                    SizedBox(height: 2),
                                    LocalizedText('Cấu hình tài khoản nhận đối soát doanh thu', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),
                                  ],
                                ),
                              ),
                              const Icon(Icons.chevron_right, color: AppColors.outlineVariant),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Business Information Form
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusMd,
                  boxShadow: AppShapes.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: const [
                        Expanded(
                          child: Row(
                            children: [
                              Icon(Icons.badge_outlined, size: 18, color: AppColors.primary),
                              SizedBox(width: 6),
                              Expanded(
                                child: LocalizedText(
                                  'Thông tin kinh doanh',
                                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        SizedBox(width: 8),
                        LocalizedText('Bảo mật mã hóa', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                      ],
                    ),
                    const SizedBox(height: 14),
                    VendorTextField(
                      controller: _businessNameController,
                      label: 'Tên doanh nghiệp / CLB *',
                      isRequired: true,
                      hint: 'Danang Ocean Club',
                    ),
                    const SizedBox(height: 12),
                    VendorTextField(
                      controller: _addressController,
                      label: 'Địa chỉ trụ sở / Bến bãi *',
                      isRequired: true,
                      hint: 'Lô 12 Võ Nguyên Giáp, Phước Mỹ, Sơn Trà',
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: VendorTextField(
                            controller: _taxCodeController,
                            label: 'Mã số thuế',
                            hint: '0402123456',
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: VendorTextField(
                            controller: _phoneController,
                            label: 'Hotline bến bãi',
                            hint: '0905 888 999',
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    const LocalizedText('Mô tả giới thiệu dịch vụ', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _descController,
                      maxLines: 4,
                      style: const TextStyle(fontSize: 12),
                      decoration: InputDecoration(
                        filled: true,
                        fillColor: AppColors.surfaceContainerLow,
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                      ),
                    ),
                    const SizedBox(height: 20),
                    VendorButton(
                      text: 'Lưu thay đổi hồ sơ',
                      icon: Icons.save,
                      onPressed: _saveProfile,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStat(String val, String label, Color valColor) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(color: AppColors.surfaceContainerLowest, borderRadius: BorderRadius.circular(8)),
      child: Column(
        children: [
          LocalizedText(val, style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: valColor)),
          const SizedBox(height: 2),
          LocalizedText(label, style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
        ],
      ),
    );
  }
}
