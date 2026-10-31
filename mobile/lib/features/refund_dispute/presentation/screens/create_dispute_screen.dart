import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/dispute_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class CreateDisputeScreen extends StatefulWidget {
  final SubOrderModel? subOrder;

  const CreateDisputeScreen({super.key, this.subOrder});

  @override
  State<CreateDisputeScreen> createState() => _CreateDisputeScreenState();
}

class _CreateDisputeScreenState extends State<CreateDisputeScreen> {
  final DisputeRepository _disputeRepository = DisputeRepository();
  final OrderRepository _orderRepository = OrderRepository();

  SubOrderModel? _selectedSubOrder;
  List<SubOrderModel> _availableSubOrders = [];
  bool _loadingOrders = false;
  bool _uploadingEvidence = false;
  bool _submitting = false;
  String? _error;

  String _selectedCategory = 'Chất lượng không đúng cam kết';
  final TextEditingController _descController = TextEditingController();
  final List<String> _attachedImages = [];

  final List<String> _categories = [
    'Chất lượng không đúng cam kết',
    'Thiếu trang bị an toàn / phao cứu sinh',
    'Tự ý hủy hoặc dời giờ xuất phát quá 60p',
    'Vấn đề thanh toán / phụ phí',
    'Sự cố phát sinh ngoài thỏa thuận',
  ];

  @override
  void initState() {
    super.initState();
    if (widget.subOrder != null) {
      _selectedSubOrder = widget.subOrder;
      _availableSubOrders = [widget.subOrder!];
    } else {
      _loadCustomerSubOrders();
    }
  }

  Future<void> _loadCustomerSubOrders() async {
    setState(() {
      _loadingOrders = true;
      _error = null;
    });
    try {
      final page = await _orderRepository.list(page: 0, size: 20);
      if (!mounted) return;
      final subs = <SubOrderModel>[
        for (final order in page.content) ...order.subOrders,
      ];
      setState(() {
        _availableSubOrders = subs;
        if (subs.isNotEmpty) {
          _selectedSubOrder = subs.first;
        }
      });
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Không tải được danh sách đơn hàng để khiếu nại.',
        );
      }
    } finally {
      if (mounted) setState(() => _loadingOrders = false);
    }
  }

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  String _formatShortId(String rawId) {
    if (rawId.isEmpty) return '';
    return rawId.length > 8
        ? rawId.substring(0, 8).toUpperCase()
        : rawId.toUpperCase();
  }

  IconData _getCategoryIcon(String category) {
    if (category.contains('Chất lượng')) return Icons.thumb_down_alt_outlined;
    if (category.contains('an toàn') || category.contains('phao')) {
      return Icons.health_and_safety_outlined;
    }
    if (category.contains('giờ xuất phát') || category.contains('dời')) {
      return Icons.schedule_outlined;
    }
    if (category.contains('thanh toán') || category.contains('phụ phí')) {
      return Icons.payments_outlined;
    }
    return Icons.report_problem_outlined;
  }

  void _openCategoryPicker() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: SafeArea(
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
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Phân loại vấn đề',
                      style: AppTypography.headlineSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w800,
                      ).copyWith(fontSize: 18),
                    ),
                    IconButton(
                      icon: const Icon(
                        Icons.close,
                        size: 20,
                        color: Color(0xFF64748B),
                      ),
                      onPressed: () => Navigator.pop(sheetContext),
                    ),
                  ],
                ),
                const Text(
                  'Chọn lý do khiếu nại phù hợp nhất với trải nghiệm thực tế của bạn:',
                  style: TextStyle(fontSize: 13, color: Color(0xFF64748B)),
                ),
                const SizedBox(height: 14),
                ..._categories.map((cat) {
                  final isSel = _selectedCategory == cat;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: InkWell(
                      onTap: () {
                        setState(() => _selectedCategory = cat);
                        Navigator.pop(sheetContext);
                      },
                      borderRadius: BorderRadius.circular(14),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 12,
                        ),
                        decoration: BoxDecoration(
                          color: isSel
                              ? const Color(0xFFE8F6F8)
                              : const Color(0xFFF8FAFC),
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(
                            color: isSel
                                ? AppColors.secondary
                                : const Color(0xFFE2E8F0),
                            width: isSel ? 1.5 : 1,
                          ),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: isSel
                                    ? AppColors.secondary
                                    : Colors.white,
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                _getCategoryIcon(cat),
                                size: 18,
                                color: isSel
                                    ? Colors.white
                                    : AppColors.secondary,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                cat,
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: isSel
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                  color: isSel
                                      ? AppColors.secondary
                                      : AppColors.onSurface,
                                ),
                              ),
                            ),
                            if (isSel)
                              const Icon(
                                Icons.check_circle_rounded,
                                color: AppColors.secondary,
                                size: 20,
                              ),
                          ],
                        ),
                      ),
                    ),
                  );
                }),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _addEvidence() async {
    if (_uploadingEvidence || _submitting) return;
    final pathController = TextEditingController();
    final mode = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 14,
            bottom:
                MediaQuery.of(sheetContext).viewInsets.bottom + 20,
          ),
          child: SafeArea(
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
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Tải bằng chứng khiếu nại',
                      style: AppTypography.headlineSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w800,
                      ).copyWith(fontSize: 18),
                    ),
                    IconButton(
                      icon: const Icon(
                        Icons.close,
                        size: 20,
                        color: Color(0xFF64748B),
                      ),
                      onPressed: () => Navigator.pop(sheetContext),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                const Text(
                  'Chọn tải tệp ảnh minh chứng từ máy hoặc đính kèm đường dẫn ảnh:',
                  style: TextStyle(fontSize: 13, color: Color(0xFF64748B)),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: pathController,
                  decoration: InputDecoration(
                    hintText: 'Nhập liên kết ảnh URL hoặc đường dẫn tệp...',
                    hintStyle: const TextStyle(
                      fontSize: 13,
                      color: Color(0xFF94A3B8),
                    ),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    filled: true,
                    fillColor: const Color(0xFFF8FAFC),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(
                        color: AppColors.secondary,
                        width: 1.5,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    onPressed: () => Navigator.pop(sheetContext, 'upload'),
                    icon: const Icon(
                      Icons.cloud_upload_outlined,
                      size: 20,
                    ),
                    label: const Text(
                      'Tải tệp ảnh minh chứng lên',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.onSurface,
                      side: const BorderSide(color: Color(0xFFCBD5E1)),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    onPressed: () => Navigator.pop(sheetContext, 'url'),
                    icon: const Icon(
                      Icons.link,
                      size: 20,
                      color: AppColors.secondary,
                    ),
                    label: const Text(
                      'Đính kèm từ liên kết URL',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    if (mode == null || !mounted) return;
    final input = pathController.text.trim();
    if (mode == 'url') {
      final url = input.isNotEmpty
          ? input
          : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80';
      setState(() => _attachedImages.add(url));
      return;
    }

    setState(() {
      _uploadingEvidence = true;
      _error = null;
    });
    try {
      String uploadedUrl;
      if (input.isNotEmpty &&
          !input.startsWith('http://') &&
          !input.startsWith('https://')) {
        uploadedUrl = await _disputeRepository.uploadEvidenceFromPath(input);
      } else {
        uploadedUrl = await _disputeRepository.uploadEvidenceBytes(
          fileBytes: DisputeRepository.sampleValidPngBytes,
          filename:
              'evidence-${DateTime.now().millisecondsSinceEpoch}.png',
          contentType: 'image/png',
        );
      }
      if (!mounted) return;
      setState(() => _attachedImages.add(uploadedUrl));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã tải bằng chứng lên máy chủ thành công!'),
          backgroundColor: AppColors.secondary,
        ),
      );
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải bằng chứng lên máy chủ. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _uploadingEvidence = false);
    }
  }

  Future<void> _submitDispute() async {
    if (_submitting || _uploadingEvidence) return;
    final sub = _selectedSubOrder;
    if (sub == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng chọn đơn hàng dịch vụ cần khiếu nại.'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }
    if (_descController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng mô tả chi tiết sự việc khiếu nại.'),
          backgroundColor: AppColors.primary,
        ),
      );
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final created = await _disputeRepository.createDispute(
        orderId: sub.masterOrderId,
        subOrderId: sub.id,
        reason: _selectedCategory,
        description: _descController.text.trim(),
        evidenceUrls: _attachedImages,
        serviceName: sub.serviceName,
        vendorName: sub.vendorName,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Đã gửi khiếu nại thành công! Ban quản trị DANASEA sẽ phản hồi trong 24h.',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      Navigator.pop(context, created);
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(e.message),
          backgroundColor: AppColors.error,
        ),
      );
    } catch (_) {
      if (!mounted) return;
      const msg = 'Không thể gửi hồ sơ khiếu nại. Vui lòng thử lại.';
      setState(() => _error = msg);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(msg),
          backgroundColor: AppColors.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final selectedSub = _selectedSubOrder;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: _submitting ? null : () => Navigator.pop(context),
        ),
        title: LocalizedText(
          'Gửi khiếu nại dịch vụ',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
      ),
      body: _loadingOrders
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // NOTICE
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer.withValues(alpha: 0.3),
                      borderRadius: AppShapes.radiusDefault,
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.policy_outlined,
                          size: 20,
                          color: AppColors.secondary,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: LocalizedText(
                            'Hồ sơ khiếu nại được gửi trực tiếp đến Bộ phận Quản trị & Đối soát DANASEA để xác minh độc lập với nhà cung cấp.',
                            style: AppTypography.bodySm(
                              color: AppColors.onSecondaryContainer,
                            ).copyWith(fontSize: 11),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(AppShapes.spaceSm),
                      decoration: BoxDecoration(
                        color: AppColors.errorContainer,
                        borderRadius: AppShapes.radiusDefault,
                      ),
                      child: LocalizedText(
                        _error!,
                        style: AppTypography.bodySm(color: AppColors.error),
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),

                  // 1. SELECT SERVICE
                  LocalizedText(
                    '1. Dịch vụ cần khiếu nại',
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  if (selectedSub == null)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(AppShapes.spaceSm),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest,
                        borderRadius: AppShapes.radiusDefault,
                        border: Border.all(color: AppColors.borderSubtle),
                      ),
                      child: LocalizedText(
                        'Chưa tìm thấy đơn hàng nào để khiếu nại.',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    )
                  else if (_availableSubOrders.length > 1 &&
                      widget.subOrder == null)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest,
                        borderRadius: AppShapes.radiusDefault,
                        border: Border.all(color: AppColors.borderSubtle),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<SubOrderModel>(
                          value: selectedSub,
                          isExpanded: true,
                          items: _availableSubOrders.map((sub) {
                            return DropdownMenuItem<SubOrderModel>(
                              value: sub,
                              child: Text(
                                '${sub.serviceName} (#${_formatShortId(sub.id)})',
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            );
                          }).toList(),
                          onChanged: _submitting
                              ? null
                              : (val) {
                                  if (val != null) {
                                    setState(() => _selectedSubOrder = val);
                                  }
                                },
                        ),
                      ),
                    )
                  else
                    Container(
                      padding: const EdgeInsets.all(AppShapes.spaceSm),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest,
                        borderRadius: AppShapes.radiusDefault,
                        border: Border.all(color: AppColors.borderSubtle),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.sailing,
                            size: 20,
                            color: AppColors.secondary,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  selectedSub.serviceName,
                                  style: AppTypography.labelMd(
                                    color: AppColors.onSurface,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                Text(
                                  '${selectedSub.vendorName} • Mã đơn: #${_formatShortId(selectedSub.id)}',
                                  style: AppTypography.bodySm(
                                    color: AppColors.onSurfaceVariant,
                                  ).copyWith(fontSize: 11),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 16),

                  // 2. CATEGORY SELECTOR
                  LocalizedText(
                    '2. Phân loại vấn đề',
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  InkWell(
                    onTap: _submitting ? null : _openCategoryPicker,
                    borderRadius: AppShapes.radiusDefault,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 13,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest,
                        borderRadius: AppShapes.radiusDefault,
                        border: Border.all(
                          color: const Color(0xFFCBD5E1),
                          width: 1,
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            _getCategoryIcon(_selectedCategory),
                            size: 20,
                            color: AppColors.secondary,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              _selectedCategory,
                              style: const TextStyle(
                                color: AppColors.onSurface,
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          const Icon(
                            Icons.keyboard_arrow_down_rounded,
                            color: Color(0xFF64748B),
                            size: 22,
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 3. DESCRIPTION
                  LocalizedText(
                    '3. Mô tả chi tiết sự việc',
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: TextField(
                      controller: _descController,
                      enabled: !_submitting,
                      maxLines: 4,
                      decoration: InputDecoration(
                        hintText: tr(
                          context,
                          'Trình bày thời gian, diễn biến sự việc và yêu cầu xử lý...',
                        ),
                        hintStyle: AppTypography.bodySm(
                          color: AppColors.outline,
                        ),
                        contentPadding: const EdgeInsets.all(12),
                        border: InputBorder.none,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 4. ATTACHMENTS
                  LocalizedText(
                    '4. Hình ảnh / Video minh chứng',
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      InkWell(
                        onTap: (_uploadingEvidence || _submitting)
                            ? null
                            : _addEvidence,
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          width: 72,
                          height: 72,
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: AppColors.secondary,
                              style: BorderStyle.solid,
                            ),
                          ),
                          child: _uploadingEvidence
                              ? const Center(
                                  child: SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  ),
                                )
                              : Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    const Icon(
                                      Icons.add_a_photo_outlined,
                                      size: 22,
                                      color: AppColors.secondary,
                                    ),
                                    const SizedBox(height: 4),
                                    LocalizedText(
                                      'Thêm ảnh',
                                      style: AppTypography.labelSm(
                                        color: AppColors.secondary,
                                      ).copyWith(fontSize: 10),
                                    ),
                                  ],
                                ),
                        ),
                      ),
                      ..._attachedImages.map((img) {
                        return Stack(
                          children: [
                            Container(
                              width: 72,
                              height: 72,
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainerHigh,
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: Image.network(
                                  img,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, _, _) => const Center(
                                    child: Icon(
                                      Icons.insert_drive_file_outlined,
                                      color: AppColors.secondary,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            Positioned(
                              top: 2,
                              right: 2,
                              child: InkWell(
                                onTap: _submitting
                                    ? null
                                    : () {
                                        setState(
                                          () => _attachedImages.remove(img),
                                        );
                                      },
                                child: Container(
                                  padding: const EdgeInsets.all(2),
                                  decoration: const BoxDecoration(
                                    color: Colors.black54,
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.close,
                                    size: 12,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        );
                      }),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // SUBMIT BUTTON
                  AppPillButton(
                    label: _submitting
                        ? 'Đang gửi khiếu nại...'
                        : 'Gửi hồ sơ khiếu nại',
                    variant: AppButtonVariant.primary,
                    width: double.infinity,
                    onPressed: (_submitting || selectedSub == null)
                        ? null
                        : _submitDispute,
                  ),
                ],
              ),
            ),
    );
  }
}
