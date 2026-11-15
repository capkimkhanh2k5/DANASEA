import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';

class VoucherManagementScreen extends StatefulWidget {
  const VoucherManagementScreen({super.key});

  @override
  State<VoucherManagementScreen> createState() => _VoucherManagementScreenState();
}

class _VoucherManagementScreenState extends State<VoucherManagementScreen> with SingleTickerProviderStateMixin {
  final VendorVoucherRepository _voucherRepo = VendorVoucherRepository();
  final VendorStore _db = VendorStore.instance;

  late TabController _tabController;

  // Create Form Controllers
  final _codeController = TextEditingController();
  final _valueController = TextEditingController();
  final _maxUsesController = TextEditingController(text: '50');
  DiscountType _selectedType = DiscountType.percentage;
  DateTime _validFrom = DateTime.now();
  DateTime _validTo = DateTime.now().add(const Duration(days: 30));
  bool _isCreating = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _codeController.dispose();
    _valueController.dispose();
    _maxUsesController.dispose();
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _handleToggleActive(DiscountCodeModel voucher, bool active) async {
    try {
      await _voucherRepo.toggleVoucherActive(voucher.id, active);
      if (!mounted) return;
      ToastNotification.show(
        context,
        message: active ? 'Đã kích hoạt mã ${voucher.code}' : 'Đã tạm dừng mã ${voucher.code}',
        type: ToastType.info,
      );
    } catch (e) {
      if (!mounted) return;
      ToastNotification.show(context, message: 'Lỗi: $e', type: ToastType.error);
    }
  }

  Future<void> _handleCreateVoucher() async {
    final code = _codeController.text.trim().toUpperCase();
    final value = double.tryParse(_valueController.text.trim()) ?? 0;
    final maxUses = int.tryParse(_maxUsesController.text.trim()) ?? 0;

    if (code.isEmpty) {
      ToastNotification.show(context, message: 'Vui lòng nhập mã khuyến mãi.', type: ToastType.error);
      return;
    }
    if (value <= 0) {
      ToastNotification.show(context, message: 'Vui lòng nhập giá trị giảm hợp lệ.', type: ToastType.error);
      return;
    }
    if (_selectedType == DiscountType.percentage && value > 100) {
      ToastNotification.show(context, message: 'Phần trăm giảm không được vượt quá 100%.', type: ToastType.error);
      return;
    }
    if (maxUses <= 0) {
      ToastNotification.show(context, message: 'Số lượt sử dụng tối đa phải lớn hơn 0.', type: ToastType.error);
      return;
    }
    if (_validTo.isBefore(_validFrom)) {
      ToastNotification.show(context, message: 'Ngày kết thúc phải sau ngày bắt đầu.', type: ToastType.error);
      return;
    }

    setState(() => _isCreating = true);
    try {
      final newVoucher = DiscountCodeModel(
        id: 'voc-${DateTime.now().millisecondsSinceEpoch}',
        code: code,
        scope: DiscountScope.vendor, // Strictly VENDOR scope
        vendorId: _db.currentVendor.id,
        discountType: _selectedType,
        discountValue: value,
        maxUses: maxUses,
        usedCount: 0,
        validFrom: _validFrom,
        validTo: _validTo,
        isActive: true,
      );

      await _voucherRepo.createVoucher(newVoucher);

      if (!mounted) return;
      setState(() {
        _isCreating = false;
        _codeController.clear();
        _valueController.clear();
        _maxUsesController.text = '50';
      });

      _tabController.animateTo(0);
      ToastNotification.show(
        context,
        message: 'Tạo mã khuyến mãi $code thành công!',
        type: ToastType.success,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isCreating = false);
      ToastNotification.show(context, message: 'Lỗi tạo mã: $e', type: ToastType.error);
    }
  }

  void _showEditVoucherModal(DiscountCodeModel voucher) {
    final maxUsesCtrl = TextEditingController(text: voucher.maxUses.toString());
    DateTime editValidTo = voucher.validTo;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => Padding(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 20,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  LocalizedText(
                    'Chỉnh sửa mã ${voucher.code}',
                    style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              LocalizedText(
                'Lượt đã dùng: ${voucher.usedCount} (Không được giảm số lượt tối đa dưới số này)',
                style: AppTypography.bodySm.copyWith(color: AppColors.tertiary),
              ),
              const SizedBox(height: 14),

              LocalizedText('Số lượt dùng tối đa', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
              const SizedBox(height: 6),
              TextField(
                controller: maxUsesCtrl,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: AppColors.surfaceContainerLow,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AppShapes.rFull),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 14),

              LocalizedText('Ngày kết thúc hiệu lực', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
              const SizedBox(height: 6),
              OutlinedButton.icon(
                onPressed: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: editValidTo,
                    firstDate: voucher.validFrom,
                    lastDate: DateTime(2028),
                  );
                  if (picked != null) {
                    setModalState(() {
                      editValidTo = picked;
                    });
                  }
                },
                icon: const Icon(Icons.calendar_today, size: 16),
                label: LocalizedText(
                  '${editValidTo.day.toString().padLeft(2, '0')}/${editValidTo.month.toString().padLeft(2, '0')}/${editValidTo.year}',
                ),
              ),
              const SizedBox(height: 20),

              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: () async {
                    final newMax = int.tryParse(maxUsesCtrl.text.trim()) ?? voucher.maxUses;
                    if (newMax < voucher.usedCount) {
                      ToastNotification.show(
                        context,
                        message: 'Số lượt tối đa không được nhỏ hơn ${voucher.usedCount}.',
                        type: ToastType.error,
                      );
                      return;
                    }
                    final updated = voucher.copyWith(
                      maxUses: newMax,
                      validTo: editValidTo,
                    );
                    await _voucherRepo.updateVoucher(updated);
                    if (!mounted) return;
                    Navigator.pop(ctx);
                    ToastNotification.show(context, message: 'Cập nhật mã thành công!', type: ToastType.success);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                  ),
                  child: const LocalizedText('Lưu thay đổi'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final vouchers = _db.vouchers;
    final redemptions = _db.redemptions;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const LocalizedText('Mã giảm giá'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.maybePop(context),
        ),
      ),
      body: Column(
        children: [
          // Scope Indicator Banner (Strictly VENDOR)
          Container(
            margin: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(AppShapes.rLg),
              boxShadow: const [
                BoxShadow(color: Color(0x08000000), blurRadius: 6, offset: Offset(0, 2)),
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
                              color: AppColors.secondary,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(Icons.storefront, color: Colors.white, size: 18),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  'PHẠM VI ÁP DỤNG',
                                  style: AppTypography.labelSm.copyWith(
                                    color: AppColors.secondary,
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 0.5,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                                LocalizedText(
                                  'Mã khuyến mãi Nhà cung cấp',
                                  style: AppTypography.labelLg.copyWith(color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer,
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                      ),
                      child: LocalizedText(
                        'Độc quyền',
                        style: AppTypography.labelSm.copyWith(
                          color: AppColors.onSecondaryContainer,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                LocalizedText(
                  'Khuyến mãi trực tiếp từ đối tác DANASEA, chiết khấu trực tiếp vào gói chèo SUP, lặn biển và tour cano của bạn.',
                  style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant),
                ),
              ],
            ),
          ),

          // Segmented Tabs Bar
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16),
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(AppShapes.rFull),
            ),
            child: TabBar(
              controller: _tabController,
              indicator: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rFull),
                boxShadow: const [
                  BoxShadow(color: Color(0x14000000), blurRadius: 4, offset: Offset(0, 2)),
                ],
              ),
              indicatorSize: TabBarIndicatorSize.tab,
              dividerColor: Colors.transparent,
              labelColor: AppColors.onSurface,
              unselectedLabelColor: AppColors.tertiary,
              labelStyle: AppTypography.labelMd.copyWith(fontWeight: FontWeight.bold),
              unselectedLabelStyle: AppTypography.labelMd,
              tabs: [
                Tab(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.confirmation_number_outlined, size: 16),
                      const SizedBox(width: 4),
                      Flexible(
                        child: LocalizedText(
                          'Danh sách (${vouchers.length})',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                Tab(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.add_circle_outline, size: 16, color: AppColors.primaryContainer),
                      const SizedBox(width: 4),
                      const Flexible(
                        child: LocalizedText(
                          'Tạo mã mới',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Tab Views
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                // Tab 1: Voucher List + Redemptions
                _buildVoucherListTab(vouchers, redemptions),

                // Tab 2: Create Voucher Form
                _buildCreateVoucherTab(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVoucherListTab(List<DiscountCodeModel> vouchers, List<DiscountRedemptionModel> redemptions) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: vouchers.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final v = vouchers[index];
              final progress = v.maxUses > 0 ? (v.usedCount / v.maxUses).clamp(0.0, 1.0) : 0.0;
              final isExpired = v.validTo.isBefore(DateTime.now());
              final discountLabel = v.discountType == DiscountType.percentage
                  ? '-${v.discountValue.toInt()}%'
                  : '-${(v.discountValue >= 1000 ? "${(v.discountValue / 1000).toStringAsFixed(0)}K" : "${v.discountValue.toInt()}đ")}';

              return Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                  boxShadow: const [
                    BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: Stack(
                    children: [
                      IntrinsicHeight(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            // 1. Left Stub: Discount Value (-20% / -50K)
                            Container(
                              width: 90,
                              color: isExpired ? const Color(0xFF94A3B8) : AppColors.secondary,
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.local_offer_rounded, color: Colors.white70, size: 18),
                                  const SizedBox(height: 6),
                                  Text(
                                    discountLabel,
                                    style: const TextStyle(
                                      fontSize: 17,
                                      fontWeight: FontWeight.w800,
                                      color: Colors.white,
                                      letterSpacing: -0.5,
                                    ),
                                    textAlign: TextAlign.center,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    v.discountType == DiscountType.percentage ? 'GIẢM GIÁ' : 'TRỰC TIẾP',
                                    style: const TextStyle(
                                      fontSize: 8.5,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white70,
                                      letterSpacing: 0.8,
                                    ),
                                  ),
                                ],
                              ),
                            ),

                            // 2. Vertical Dashed Perforation Line
                            CustomPaint(
                              size: const Size(1, double.infinity),
                              painter: _DashedLinePainter(),
                            ),

                            // 3. Right Body: Code, Validity, Progress (Đã dùng X/Y)
                            Expanded(
                              child: Padding(
                                padding: const EdgeInsets.fromLTRB(14, 10, 12, 10),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Expanded(
                                          child: Text(
                                            v.code,
                                            style: TextStyle(
                                              fontSize: 14.5,
                                              fontWeight: FontWeight.bold,
                                              letterSpacing: 0.5,
                                              color: isExpired ? AppColors.tertiary : AppColors.navyAnchor,
                                            ),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                        Transform.scale(
                                          scale: 0.8,
                                          child: Switch(
                                            value: v.isActive && !isExpired,
                                            activeColor: AppColors.secondary,
                                            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                            onChanged: isExpired ? null : (val) => _handleToggleActive(v, val),
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 2),
                                    Row(
                                      children: [
                                        const Icon(Icons.access_time_rounded, size: 11, color: AppColors.tertiary),
                                        const SizedBox(width: 4),
                                        Text(
                                          'Hạn: ${v.validFrom.day}/${v.validFrom.month} - ${v.validTo.day}/${v.validTo.month}/${v.validTo.year}',
                                          style: const TextStyle(fontSize: 10.5, color: AppColors.tertiary),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 8),

                                    // Progress Bar
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Text(
                                          'Đã dùng: ${v.usedCount}/${v.maxUses}',
                                          style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.onSurfaceVariant),
                                        ),
                                        Text(
                                          '${(progress * 100).toInt()}%',
                                          style: TextStyle(
                                            fontSize: 10.5,
                                            fontWeight: FontWeight.bold,
                                            color: isExpired ? AppColors.tertiary : AppColors.secondary,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    ClipRRect(
                                      borderRadius: BorderRadius.circular(4),
                                      child: LinearProgressIndicator(
                                        value: progress,
                                        minHeight: 5,
                                        backgroundColor: const Color(0xFFF1F5F9),
                                        valueColor: AlwaysStoppedAnimation<Color>(
                                          isExpired ? AppColors.tertiary : AppColors.secondary,
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 6),
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.end,
                                      children: [
                                        InkWell(
                                          onTap: () => _showEditVoucherModal(v),
                                          borderRadius: BorderRadius.circular(6),
                                          child: const Padding(
                                            padding: EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                                            child: Row(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                Icon(Icons.edit_outlined, size: 12, color: AppColors.secondary),
                                                SizedBox(width: 3),
                                                Text(
                                                  'Chỉnh sửa',
                                                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Top notch cutout
                      Positioned(
                        top: -7,
                        left: 83,
                        child: Container(
                          width: 14,
                          height: 14,
                          decoration: const BoxDecoration(
                            color: AppColors.background,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                      // Bottom notch cutout
                      Positioned(
                        bottom: -7,
                        left: 83,
                        child: Container(
                          width: 14,
                          height: 14,
                          decoration: const BoxDecoration(
                            color: AppColors.background,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
          const SizedBox(height: 24),

          // Redemptions Table Section
          LocalizedText(
            'Lịch sử áp dụng theo mã đơn tổng',
            style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
          ),
          const SizedBox(height: 4),
          LocalizedText(
            'Theo dõi khách đã dùng mã trong các đơn booking',
            style: AppTypography.bodySm.copyWith(color: AppColors.tertiary),
          ),
          const SizedBox(height: 12),

          Container(
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(AppShapes.rLg),
              boxShadow: const [
                BoxShadow(color: Color(0x08000000), blurRadius: 6, offset: Offset(0, 2)),
              ],
            ),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: redemptions.length,
              separatorBuilder: (_, __) => const Divider(height: 1, color: AppColors.surfaceContainerHigh),
              itemBuilder: (context, idx) {
                final r = redemptions[idx];
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                'Đơn #${r.masterOrderId}',
                                style: AppTypography.labelLg.copyWith(
                                  color: AppColors.onSurface,
                                  fontWeight: FontWeight.bold,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                              LocalizedText(
                                'Mã: ${r.discountCodeText}',
                                style: AppTypography.labelSm.copyWith(color: AppColors.secondary),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        LocalizedText(
                          '-${r.amountDeducted.toInt()} đ',
                          style: AppTypography.headlineSm.copyWith(
                            color: AppColors.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                );
              },
            ),
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildCreateVoucherTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(AppShapes.rXl),
          boxShadow: const [
            BoxShadow(color: Color(0x0A000000), blurRadius: 8, offset: Offset(0, 2)),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText('Tạo mã giảm giá mới', style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface)),
            const SizedBox(height: 4),
            LocalizedText(
              'Mã áp dụng tự động cho toàn bộ dịch vụ của nhà cung cấp',
              style: AppTypography.bodySm.copyWith(color: AppColors.tertiary),
            ),
            const SizedBox(height: 18),

            // Voucher Code
            LocalizedText('Mã khuyến mãi (Viết liền, không dấu) *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
            const SizedBox(height: 6),
            TextField(
              controller: _codeController,
              textCapitalization: TextCapitalization.characters,
              decoration: InputDecoration(
                hintText: tr(context, 'VD: SUMMER2024'),
                prefixIcon: const Icon(Icons.confirmation_number_outlined, color: AppColors.tertiary),
                filled: true,
                fillColor: AppColors.surfaceContainerLow,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Discount Type Toggle
            LocalizedText('Hình thức giảm giá *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _selectedType = DiscountType.percentage),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: _selectedType == DiscountType.percentage
                            ? AppColors.secondary
                            : AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                      ),
                      alignment: Alignment.center,
                      child: LocalizedText(
                        'Phần trăm (%)',
                        style: AppTypography.labelMd.copyWith(
                          color: _selectedType == DiscountType.percentage ? Colors.white : AppColors.onSurface,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _selectedType = DiscountType.fixed),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: _selectedType == DiscountType.fixed
                            ? AppColors.secondary
                            : AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                      ),
                      alignment: Alignment.center,
                      child: LocalizedText(
                        'Số tiền cố định (đ)',
                        style: AppTypography.labelMd.copyWith(
                          color: _selectedType == DiscountType.fixed ? Colors.white : AppColors.onSurface,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Discount Value
            LocalizedText(
              _selectedType == DiscountType.percentage ? 'Tỷ lệ giảm (%) *' : 'Số tiền giảm (đ) *',
              style: AppTypography.labelMd.copyWith(color: AppColors.tertiary),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: _valueController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                hintText: tr(context, _selectedType == DiscountType.percentage ? 'VD: 15' : 'VD: 50000'),
                prefixIcon: Icon(
                  _selectedType == DiscountType.percentage ? Icons.percent : Icons.attach_money,
                  color: AppColors.tertiary,
                ),
                filled: true,
                fillColor: AppColors.surfaceContainerLow,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Max Uses
            LocalizedText('Số lượt sử dụng tối đa *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
            const SizedBox(height: 6),
            TextField(
              controller: _maxUsesController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.people_outline, color: AppColors.tertiary),
                filled: true,
                fillColor: AppColors.surfaceContainerLow,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Date Pickers
            LocalizedText('Thời hạn áp dụng *', style: AppTypography.labelMd.copyWith(color: AppColors.tertiary)),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: _validFrom,
                        firstDate: DateTime(2023),
                        lastDate: DateTime(2028),
                      );
                      if (picked != null) setState(() => _validFrom = picked);
                    },
                    icon: const Icon(Icons.calendar_today, size: 14),
                    label: LocalizedText('${_validFrom.day}/${_validFrom.month}/${_validFrom.year}'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                const LocalizedText('đến'),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: _validTo,
                        firstDate: _validFrom,
                        lastDate: DateTime(2028),
                      );
                      if (picked != null) setState(() => _validTo = picked);
                    },
                    icon: const Icon(Icons.calendar_today, size: 14),
                    label: LocalizedText('${_validTo.day}/${_validTo.month}/${_validTo.year}'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Submit Button
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed: _isCreating ? null : _handleCreateVoucher,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                ),
                child: _isCreating
                    ? const CircularProgressIndicator(color: Colors.white)
                    : Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.add, size: 20),
                          const SizedBox(width: 8),
                          LocalizedText('Tạo và kích hoạt mã', style: AppTypography.labelLg.copyWith(color: Colors.white)),
                        ],
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DashedLinePainter extends CustomPainter {
  const _DashedLinePainter();

  @override
  void paint(Canvas canvas, Size size) {
    const double dashHeight = 4.0;
    const double dashSpace = 3.0;
    double startY = 6.0;
    final paint = Paint()
      ..color = const Color(0xFFCBD5E1)
      ..strokeWidth = 1.2;
    while (startY < size.height - 6.0) {
      canvas.drawLine(Offset(0, startY), Offset(0, startY + dashHeight), paint);
      startY += dashHeight + dashSpace;
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
