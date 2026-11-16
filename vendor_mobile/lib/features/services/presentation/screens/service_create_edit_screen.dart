import 'dart:io';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../../../core/theme/app_colors.dart';

import '../../../../core/theme/app_shapes.dart';

import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';

import '../../../../core/widgets/vendor_button.dart';

import '../../../../core/widgets/vendor_text_field.dart';

import '../../../../core/widgets/toast_notification.dart';

import 'service_safety_cert_screen.dart';



class ServiceCreateEditScreen extends StatefulWidget {

  final ServiceModel? service;



  const ServiceCreateEditScreen({super.key, this.service});



  @override

  State<ServiceCreateEditScreen> createState() => _ServiceCreateEditScreenState();

}



class _ServiceCreateEditScreenState extends State<ServiceCreateEditScreen> {

  final _db = VendorStore.instance;



  final TextEditingController _nameViController = TextEditingController();

  final TextEditingController _nameEnController = TextEditingController();

  final TextEditingController _slugController = TextEditingController();

  final TextEditingController _descViController = TextEditingController();

  final TextEditingController _descEnController = TextEditingController();

  final TextEditingController _priceController = TextEditingController();

  final TextEditingController _durationController = TextEditingController();

  final TextEditingController _capacityController = TextEditingController();

  final TextEditingController _meetingPointNameController = TextEditingController();

  final TextEditingController _meetingPointAddressController = TextEditingController();

  final TextEditingController _latController = TextEditingController();

  final TextEditingController _lngController = TextEditingController();
  bool _showEnglishName = false;
  bool _isAutoSlug = true;

  String _slugify(String text) {
    var str = text.toLowerCase().trim();
    const map = {
      'a': 'áàảãạăắằẳẵặâấầẩẫậ',
      'd': 'đ',
      'e': 'éèẻẽẹêếềểễệ',
      'i': 'íìỉĩị',
      'o': 'óòỏõọôốồổỗộơớờởỡợ',
      'u': 'úùủũụưứừửữự',
      'y': 'ýỳỷỹỵ',
    };
    map.forEach((non, diacritics) {
      for (int i = 0; i < diacritics.length; i++) {
        str = str.replaceAll(diacritics[i], non);
      }
    });
    str = str.replaceAll(RegExp(r'[^a-z0-9\s-]'), '');
    str = str.replaceAll(RegExp(r'[\s-]+'), '-');
    return str.replaceAll(RegExp(r'^-+|-+$'), '');
  }



  String _category = 'WATER_SPORTS';

  String _descTab = 'vi';

  List<String> _images = [];
  final Map<String, String> _imageIdByUrl = {};
  List<VendorServiceOptionModel> _serviceOptions = [];

  bool _isOptimizingAi = false;
  bool _isUploadingImage = false;
  bool _isReorderingImages = false;
  bool _isLoadingOptions = false;
  bool _isSavingDraft = false;
  String? _serverServiceId;

  final ScrollController _scrollController = ScrollController();
  final GlobalKey _section1Key = GlobalKey();
  final GlobalKey _section2Key = GlobalKey();
  final GlobalKey _section3Key = GlobalKey();
  final GlobalKey _section4Key = GlobalKey();
  int _activeSectionIndex = 0;

  void _scrollToSection(GlobalKey key, int index) {
    setState(() => _activeSectionIndex = index);
    final ctx = key.currentContext;
    if (ctx != null) {
      Scrollable.ensureVisible(
        ctx,
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeInOut,
        alignment: 0.05,
      );
    }
  }

  bool _isValidUuid(String? value) {
    if (value == null || value.isEmpty) return false;
    return RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(value);
  }

  Future<void> _loadServerOptionsAndImages(String serviceId) async {
    if (!_isValidUuid(serviceId)) return;
    setState(() => _isLoadingOptions = true);
    try {
      final repo = VendorServiceRepository();
      final results = await Future.wait([
        repo.getServiceOptions(serviceId),
        repo.getServiceImagesWithIds(serviceId),
      ]);
      if (!mounted) return;
      final opts = results[0] as List<VendorServiceOptionModel>;
      final imgPairs = results[1] as List<Map<String, String>>;
      setState(() {
        _serviceOptions = opts;
        for (final pair in imgPairs) {
          final id = pair['id'] ?? '';
          final url = pair['url'] ?? '';
          if (id.isNotEmpty && url.isNotEmpty) {
            _imageIdByUrl[url] = id;
          }
        }
        if (imgPairs.isNotEmpty && _images.every((u) => u.startsWith('http'))) {
          _images = imgPairs.map((p) => p['url']!).toList();
        }
        _isLoadingOptions = false;
      });
    } catch (_) {
      if (mounted) setState(() => _isLoadingOptions = false);
    }
  }

  Future<void> _syncServerImageOrder() async {
    final currentId = _serverServiceId ?? widget.service?.serviceId;
    if (!_isValidUuid(currentId)) return;
    final orderedIds = _images
        .map((url) => _imageIdByUrl[url])
        .whereType<String>()
        .where((id) => id.isNotEmpty)
        .toList();
    if (orderedIds.length < 2) return;
    setState(() => _isReorderingImages = true);
    try {
      await VendorServiceRepository().reorderServiceImages(
        serviceId: currentId!,
        imageIds: orderedIds,
      );
      if (mounted) {
        ToastNotification.showSuccess(context, 'Đã đồng bộ thứ tự ảnh lên máy chủ');
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Không thể sắp xếp thứ tự ảnh: $e');
      }
    } finally {
      if (mounted) setState(() => _isReorderingImages = false);
    }
  }

  Future<void> _moveImage(int fromIndex, int toIndex) async {
    if (fromIndex < 0 ||
        fromIndex >= _images.length ||
        toIndex < 0 ||
        toIndex >= _images.length ||
        fromIndex == toIndex) {
      return;
    }
    setState(() {
      final item = _images.removeAt(fromIndex);
      _images.insert(toIndex, item);
    });
    await _syncServerImageOrder();
  }

  Future<void> _deleteImageAt(int index) async {
    if (index < 0 || index >= _images.length) return;
    final url = _images[index];
    final imageId = _imageIdByUrl[url];
    final currentId = _serverServiceId ?? widget.service?.serviceId;
    setState(() {
      _images.removeAt(index);
    });
    if (_isValidUuid(currentId) && imageId != null && imageId.isNotEmpty) {
      try {
        await VendorServiceRepository().deleteServiceImage(
          serviceId: currentId!,
          imageId: imageId,
        );
        _imageIdByUrl.remove(url);
        if (mounted) {
          ToastNotification.showSuccess(context, 'Đã xóa ảnh khỏi dịch vụ');
        }
      } catch (e) {
        if (mounted) {
          ToastNotification.showError(context, 'Lỗi xóa ảnh trên máy chủ: $e');
        }
      }
    }
  }

  Future<void> _pickAndUploadImage() async {
    if (_images.length >= 10) {
      ToastNotification.showError(context, 'Mỗi dịch vụ chỉ được tải tối đa 10 hình ảnh.');
      return;
    }
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: AppColors.surfaceContainerLowest,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: LocalizedText(
                  'Tải ảnh dịch vụ lên Cloud',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined, color: AppColors.secondary),
                title: const LocalizedText('Chọn ảnh từ Thư viện'),
                subtitle: const LocalizedText('Tải ảnh JPEG, PNG, WebP từ thiết bị lên Cloudinary', style: TextStyle(fontSize: 11)),
                onTap: () => Navigator.pop(ctx, ImageSource.gallery),
              ),
              ListTile(
                leading: const Icon(Icons.camera_alt_outlined, color: AppColors.primary),
                title: const LocalizedText('Chụp ảnh mới từ Camera'),
                onTap: () => Navigator.pop(ctx, ImageSource.camera),
              ),
            ],
          ),
        ),
      ),
    );
    if (source == null || !mounted) return;

    try {
      final picker = ImagePicker();
      final picked = await picker.pickImage(source: source, imageQuality: 85);
      if (picked == null || !mounted) return;

      final currentId = _serverServiceId ?? widget.service?.serviceId;
      if (_isValidUuid(currentId)) {
        setState(() => _isUploadingImage = true);
        final res = await VendorServiceRepository().uploadServiceImage(
          serviceId: currentId!,
          filePath: picked.path,
        );
        if (!mounted) return;
        final cloudUrl = (res['url'] ?? res['imageUrl'] ?? '').toString();
        final imgId = (res['id'] ?? '').toString();
        setState(() {
          _isUploadingImage = false;
          if (cloudUrl.isNotEmpty) {
            _images.add(cloudUrl);
            if (imgId.isNotEmpty) {
              _imageIdByUrl[cloudUrl] = imgId;
            }
          }
        });
        ToastNotification.showSuccess(context, 'Đã tải ảnh lên Cloudinary thành công');
      } else {
        setState(() {
          _images.add(picked.path);
        });
        ToastNotification.showInfo(
          context,
          'Đã chọn ảnh từ máy. Ảnh sẽ được tải lên Cloudinary ngay khi lưu dịch vụ.',
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _isUploadingImage = false);
      ToastNotification.showError(context, 'Không thể tải ảnh lên: $e');
    }
  }

  Future<void> _optimizeListingWithAi() async {
    final rawTitle = _nameViController.text.trim();
    final rawDesc = _descViController.text.trim();
    if (rawTitle.isEmpty && rawDesc.isEmpty) {
      ToastNotification.showError(context, 'Vui lòng nhập tên hoặc mô tả dịch vụ trước khi tối ưu bằng AI.');
      return;
    }
    setState(() => _isOptimizingAi = true);
    try {
      final result = await VendorAiRepository().optimizeListing(
        serviceId: widget.service?.serviceId,
        rawTitle: rawTitle,
        rawDescription: rawDesc,
        highlights: [
          if (_meetingPointNameController.text.trim().isNotEmpty)
            _meetingPointNameController.text.trim(),
          if (_meetingPointAddressController.text.trim().isNotEmpty)
            _meetingPointAddressController.text.trim(),
        ],
        targetLanguages: const ['vi', 'en'],
      );
      if (!mounted) return;
      setState(() => _isOptimizingAi = false);
      _showListingOptimizeModal(result);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isOptimizingAi = false);
      ToastNotification.showError(context, 'Lỗi tối ưu AI: $e');
    }
  }

  void _showListingOptimizeModal(ListingOptimizeResultModel result) {
    final viDraft = result.drafts['vi'] ?? result.drafts['VI'];
    final enDraft = result.drafts['en'] ?? result.drafts['EN'];
    final canApply = [viDraft, enDraft].any((draft) => draft != null &&
        (draft.optimizedTitle.trim().isNotEmpty || draft.optimizedDescription.trim().isNotEmpty));
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(ctx).size.height * 0.85),
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 16,
          bottom: MediaQuery.of(ctx).padding.bottom + 20,
        ),
        decoration: const BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 14),
                  decoration: BoxDecoration(
                    color: const Color(0xFFCBD5E1),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.auto_awesome, color: AppColors.secondary, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'AI Chấm điểm & Tối ưu bài đăng',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: result.listingQualityScore >= 75
                          ? AppColors.secondaryContainer
                          : AppColors.primaryFixed,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'Điểm chuẩn SEO: ${result.listingQualityScore}/100',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.secondary),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (result.missingItems.isNotEmpty) ...[
                const Text(
                  'Checklist thông tin an toàn & vận hành cần bổ sung:',
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 6),
                ...result.missingItems.map((item) => Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: item.critical
                            ? AppColors.errorContainer.withValues(alpha: 0.35)
                            : AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            item.critical ? Icons.warning_amber_rounded : Icons.info_outline,
                            size: 16,
                            color: item.critical ? AppColors.error : AppColors.secondary,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.description,
                                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                ),
                                if (item.suggestion.isNotEmpty) ...[
                                  const SizedBox(height: 2),
                                  Text(
                                    item.suggestion,
                                    style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ),
                    )),
                const SizedBox(height: 10),
              ],
              if (viDraft != null) ...[
                const Text(
                  'Bản nháp Tiếng Việt đề xuất:',
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                ),
                const SizedBox(height: 4),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        viDraft.optimizedTitle,
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        viDraft.optimizedDescription,
                        style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
              if (enDraft != null) ...[
                const Text(
                  'Bản dịch Tiếng Anh đề xuất:',
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                ),
                const SizedBox(height: 4),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        enDraft.optimizedTitle,
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        enDraft.optimizedDescription,
                        style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
              ],
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(ctx),
                      child: const Text('Đóng'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: !canApply ? null : () {
                        setState(() {
                          if (viDraft != null) {
                            if (viDraft.optimizedTitle.isNotEmpty) {
                              _nameViController.text = viDraft.optimizedTitle;
                            }
                            if (viDraft.optimizedDescription.isNotEmpty) {
                              _descViController.text = viDraft.optimizedDescription;
                            }
                          }
                          if (enDraft != null) {
                            if (enDraft.optimizedTitle.isNotEmpty) {
                              _nameEnController.text = enDraft.optimizedTitle;
                            }
                            if (enDraft.optimizedDescription.isNotEmpty) {
                              _descEnController.text = enDraft.optimizedDescription;
                            }
                          }
                        });
                        Navigator.pop(ctx);
                        ToastNotification.showSuccess(context, 'Đã áp dụng tiêu đề & mô tả tối ưu từ AI');
                      },
                      icon: const Icon(Icons.check, size: 16),
                      label: const Text('Áp dụng ngay'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.secondary,
                        foregroundColor: Colors.white,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }



  void _showOptionFormModal({VendorServiceOptionModel? existingOption, int? editIndex}) {
    final nameCtrl = TextEditingController(text: existingOption?.name ?? '');
    final priceCtrl = TextEditingController(
      text: existingOption != null ? existingOption.price.toStringAsFixed(0) : (_priceController.text.isNotEmpty ? _priceController.text : ''),
    );
    final maxPaxCtrl = TextEditingController(
      text: existingOption?.maxPaxPerPackage?.toString() ?? '6',
    );
    final benefitsCtrl = TextEditingController(text: existingOption?.benefits ?? '');
    String optionType = existingOption?.optionType ?? 'SHARED';
    String pricingUnit = existingOption?.pricingUnit ?? 'PER_PERSON';
    String status = existingOption?.status ?? 'ACTIVE';
    bool isSubmittingOption = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 16,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + MediaQuery.of(ctx).padding.bottom + 20,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    LocalizedText(
                      existingOption == null ? 'Thêm gói / Lựa chọn bán' : 'Chỉnh sửa lựa chọn bán',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () => Navigator.pop(ctx),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                VendorTextField(
                  controller: nameCtrl,
                  label: 'Tên gói / Lựa chọn',
                  isRequired: true,
                  hint: 'VD: Bao trọn cano riêng kèm flycam',
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const LocalizedText(
                            'Hình thức bán',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                          ),
                          const SizedBox(height: 6),
                          Container(
                            height: 48,
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: AppColors.secondary.withValues(alpha: 0.6), width: 1.2),
                              boxShadow: const [
                                BoxShadow(
                                  color: Color(0x06000000),
                                  blurRadius: 4,
                                  offset: Offset(0, 2),
                                ),
                              ],
                            ),
                            child: DropdownButtonHideUnderline(
                              child: DropdownButton<String>(
                                value: optionType,
                                isExpanded: true,
                                borderRadius: BorderRadius.circular(12),
                                dropdownColor: Colors.white,
                                elevation: 3,
                                icon: const Icon(Icons.keyboard_arrow_down_rounded, color: AppColors.secondary),
                                items: const [
                                  DropdownMenuItem(
                                    value: 'SHARED',
                                    child: Row(
                                      children: [
                                        Icon(Icons.groups_rounded, size: 16, color: AppColors.secondary),
                                        SizedBox(width: 6),
                                        Text('Ghép đoàn', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface)),
                                      ],
                                    ),
                                  ),
                                  DropdownMenuItem(
                                    value: 'PRIVATE',
                                    child: Row(
                                      children: [
                                        Icon(Icons.workspace_premium_rounded, size: 16, color: AppColors.primary),
                                        SizedBox(width: 6),
                                        Text('Bao trọn gói', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface)),
                                      ],
                                    ),
                                  ),
                                ],
                                onChanged: (val) {
                                  if (val == null) return;
                                  setModalState(() {
                                    optionType = val;
                                    pricingUnit = val == 'PRIVATE' ? 'PER_PACKAGE' : 'PER_PERSON';
                                  });
                                },
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const LocalizedText(
                            'Đơn vị tính',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                          ),
                          const SizedBox(height: 6),
                          Container(
                            height: 48,
                            alignment: Alignment.centerLeft,
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF1F5F9),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: const Color(0xFFE2E8F0)),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  pricingUnit == 'PER_PACKAGE' ? Icons.inventory_2_outlined : Icons.person_outline_rounded,
                                  size: 15,
                                  color: AppColors.tertiary,
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  pricingUnit == 'PER_PACKAGE' ? 'Theo gói' : 'Theo người / vé',
                                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.onSurfaceVariant),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: VendorTextField(
                        controller: priceCtrl,
                        label: 'Đơn giá (VNĐ)',
                        isRequired: true,
                        hint: '300000',
                        keyboardType: TextInputType.number,
                      ),
                    ),
                    if (optionType == 'PRIVATE') ...[
                      const SizedBox(width: 10),
                      Expanded(
                        child: VendorTextField(
                          controller: maxPaxCtrl,
                          label: 'Tối đa khách / gói',
                          isRequired: true,
                          hint: '6',
                          keyboardType: TextInputType.number,
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 12),
                VendorTextField(
                  controller: benefitsCtrl,
                  label: 'Quyền lợi bổ sung (Tùy chọn)',
                  hint: 'VD: Nước suối, khăn lạnh, chụp ảnh miễn phí...',
                ),
                if (existingOption != null) ...[
                  const SizedBox(height: 10),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Đang mở bán (ACTIVE)', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
                    value: status == 'ACTIVE',
                    activeColor: AppColors.secondary,
                    onChanged: (val) => setModalState(() => status = val ? 'ACTIVE' : 'INACTIVE'),
                  ),
                ],
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: isSubmittingOption ? null : () => Navigator.pop(ctx),
                        child: const Text('Hủy'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: isSubmittingOption
                            ? null
                            : () async {
                                final name = nameCtrl.text.trim();
                                final priceVal = double.tryParse(priceCtrl.text.replaceAll('.', '').replaceAll(',', '')) ?? 0;
                                final maxPaxVal = optionType == 'PRIVATE' ? int.tryParse(maxPaxCtrl.text.trim()) : null;
                                if (name.isEmpty) {
                                  ToastNotification.showError(ctx, 'Vui lòng nhập tên gói/lựa chọn');
                                  return;
                                }
                                if (priceVal <= 0) {
                                  ToastNotification.showError(ctx, 'Đơn giá phải lớn hơn 0 đ');
                                  return;
                                }
                                if (optionType == 'PRIVATE' && (maxPaxVal == null || maxPaxVal <= 0)) {
                                  ToastNotification.showError(ctx, 'Gói bao trọn bắt buộc nhập số khách tối đa > 0');
                                  return;
                                }

                                final currentId = _serverServiceId ?? widget.service?.serviceId;
                                setModalState(() => isSubmittingOption = true);
                                try {
                                  final repo = VendorServiceRepository();
                                  if (_isValidUuid(currentId)) {
                                    if (existingOption != null && _isValidUuid(existingOption.id)) {
                                      final updated = await repo.updateServiceOption(
                                        serviceId: currentId!,
                                        optionId: existingOption.id,
                                        name: name,
                                        optionType: optionType,
                                        pricingUnit: pricingUnit,
                                        price: priceVal,
                                        maxPaxPerPackage: maxPaxVal,
                                        benefits: benefitsCtrl.text.trim(),
                                        status: status,
                                      );
                                      if (mounted && editIndex != null) {
                                        setState(() => _serviceOptions[editIndex] = updated);
                                      }
                                    } else {
                                      final created = await repo.createServiceOption(
                                        serviceId: currentId!,
                                        name: name,
                                        optionType: optionType,
                                        pricingUnit: pricingUnit,
                                        price: priceVal,
                                        maxPaxPerPackage: maxPaxVal,
                                        benefits: benefitsCtrl.text.trim(),
                                      );
                                      if (mounted) {
                                        setState(() => _serviceOptions.add(created));
                                      }
                                    }
                                    if (mounted) {
                                      Navigator.pop(ctx);
                                      ToastNotification.showSuccess(context, 'Đã lưu lựa chọn bán lên máy chủ');
                                    }
                                  } else {
                                    final localOpt = VendorServiceOptionModel(
                                      id: existingOption?.id ?? 'local-${DateTime.now().millisecondsSinceEpoch}',
                                      serviceId: '',
                                      name: name,
                                      optionType: optionType,
                                      pricingUnit: pricingUnit,
                                      price: priceVal,
                                      maxPaxPerPackage: maxPaxVal,
                                      benefits: benefitsCtrl.text.trim(),
                                      status: status,
                                    );
                                    if (mounted) {
                                      setState(() {
                                        if (editIndex != null) {
                                          _serviceOptions[editIndex] = localOpt;
                                        } else {
                                          _serviceOptions.add(localOpt);
                                        }
                                      });
                                      Navigator.pop(ctx);
                                      ToastNotification.showInfo(
                                        context,
                                        'Đã thêm gói bán. Gói sẽ được đẩy lên máy chủ ngay khi lưu dịch vụ.',
                                      );
                                    }
                                  }
                                } catch (e) {
                                  setModalState(() => isSubmittingOption = false);
                                  if (mounted) {
                                    ToastNotification.showError(ctx, 'Lỗi lưu lựa chọn bán: $e');
                                  }
                                }
                              },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: Colors.white,
                        ),
                        child: isSubmittingOption
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text('Lưu lựa chọn'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  bool get _isEdit => widget.service != null;



  String _normalizeCategory(String cat) {

    switch (cat) {

      case 'cat-001':

      case 'WATER_SPORTS':

        return 'WATER_SPORTS';

      case 'cat-002':

      case 'DIVING':

        return 'DIVING';

      case 'cat-003':

      case 'SURFING':

        return 'SURFING';

      case 'cat-004':

      case 'BOAT_CRUISE':

        return 'BOAT_CRUISE';

      default:

        return 'WATER_SPORTS';

    }

  }



  @override

  void initState() {

    super.initState();

    if (_isEdit) {
      final s = widget.service!;
      _nameViController.text = s.nameVi;
      _nameEnController.text = s.nameEn;
      if (s.nameEn.isNotEmpty) {
        _showEnglishName = true;
      }
      _slugController.text = s.slug;
      _isAutoSlug = s.slug.isEmpty;
      _descViController.text = s.descriptionVi;
      _descEnController.text = s.descriptionEn;
      _priceController.text = s.price.toStringAsFixed(0);
      _durationController.text = s.durationMinutes.toString();
      _capacityController.text = s.maxCapacity.toString();
      _meetingPointNameController.text = s.meetingPointName;
      _meetingPointAddressController.text = s.meetingPointAddress;
      _latController.text = s.latitude.toString();
      _lngController.text = s.longitude.toString();
      _category = _normalizeCategory(s.category);
      _images = List.from(s.images);
      _serverServiceId = s.serviceId;
      _loadServerOptionsAndImages(s.serviceId);
    } else {
      if (_db.categories.isEmpty) {
        VendorServiceRepository().getCategories().then((_) {
          if (mounted) setState(() {});
        });
      }
    }

    _nameViController.addListener(() {
      if (_isAutoSlug) {
        final slug = _slugify(_nameViController.text);
        if (_slugController.text != slug) {
          _slugController.text = slug;
        }
      }
    });

  }



  @override

  void dispose() {

    _nameViController.dispose();

    _nameEnController.dispose();

    _slugController.dispose();

    _descViController.dispose();

    _descEnController.dispose();

    _priceController.dispose();

    _durationController.dispose();

    _capacityController.dispose();

    _meetingPointNameController.dispose();

    _meetingPointAddressController.dispose();

    _latController.dispose();

    _lngController.dispose();

    _scrollController.dispose();

    super.dispose();

  }



  String _resolveCategoryUuid() {
    if (_isValidUuid(widget.service?.categoryId)) {
      return widget.service!.categoryId;
    }
    if (_isValidUuid(_category)) {
      return _category;
    }
    if (_db.categories.isNotEmpty) {
      return _db.categories.first.id;
    }
    return _category;
  }

  ServiceModel _buildServiceModel() {
    final parsedPrice = double.tryParse(_priceController.text.replaceAll('.', '').replaceAll(',', ''));
    final parsedDuration = int.tryParse(_durationController.text.trim());
    final parsedCapacity = int.tryParse(_capacityController.text.trim());
    final parsedLat = double.tryParse(_latController.text.trim());
    final parsedLng = double.tryParse(_lngController.text.trim());

    if (parsedPrice == null ||
        parsedDuration == null ||
        parsedCapacity == null ||
        parsedLat == null ||
        parsedLng == null) {
      throw const FormatException('Vui lòng nhập đầy đủ giá, thời lượng, sức chứa và tọa độ hợp lệ.');
    }
    return ServiceModel(
      id: _serverServiceId ?? widget.service?.id ?? widget.service?.serviceId ?? '',
      vendorId: widget.service?.vendorId ?? VendorStore.instance.currentVendor.id,
      categoryId: _resolveCategoryUuid(),
      name: _nameViController.text.trim(),
      nameEn: _nameEnController.text.trim(),
      slug: _slugController.text.trim(),
      description: _descViController.text.trim(),
      descriptionEn: _descEnController.text.trim(),
      price: parsedPrice,
      durationMinutes: parsedDuration,
      capacityPerSlot: parsedCapacity,
      locationName: _meetingPointNameController.text.trim(),
      address: _meetingPointAddressController.text.trim(),
      latitude: parsedLat,
      longitude: parsedLng,
      weatherSensitive: widget.service?.weatherSensitive ?? true,
      minWindKmh: widget.service?.minWindKmh ?? 0.0,
      maxWaveM: widget.service?.maxWaveM ?? 0.0,
      waiverContent: widget.service?.waiverContent,
      status: widget.service?.status ?? ServiceStatus.draft,
      imageUrls: _images,
      createdAt: widget.service?.createdAt ?? DateTime.now(),
      updatedAt: DateTime.now(),
    );
  }

  Future<void> _saveDraftAndProceed() async {
    if (_isSavingDraft) return;
    ServiceModel draft;
    try {
      draft = _buildServiceModel();
    } catch (e) {
      ToastNotification.showError(context, e.toString());
      return;
    }

    setState(() => _isSavingDraft = true);
    final repo = VendorServiceRepository();
    ServiceModel savedService = draft;
    try {
      if (_isValidUuid(draft.id)) {
        savedService = await repo.updateService(draft);
      } else {
        savedService = await repo.createService(draft);
      }
      _serverServiceId = savedService.id;

      // Upload any pending local images directly to Cloudinary via /api/vendor/services/{id}/images
      final uploadedUrls = <String>[];
      for (final img in _images) {
        if (img.startsWith('http://') || img.startsWith('https://')) {
          uploadedUrls.add(img);
        } else if (_isValidUuid(savedService.id)) {
          try {
            final res = await repo.uploadServiceImage(
              serviceId: savedService.id,
              filePath: img,
            );
            final cloudUrl = (res['url'] ?? res['imageUrl'] ?? '').toString();
            final imgId = (res['id'] ?? '').toString();
            if (cloudUrl.isNotEmpty) {
              uploadedUrls.add(cloudUrl);
              if (imgId.isNotEmpty) {
                _imageIdByUrl[cloudUrl] = imgId;
              }
            }
          } catch (_) {
            uploadedUrls.add(img);
          }
        }
      }
      if (uploadedUrls.isNotEmpty) {
        _images = uploadedUrls;
        savedService = savedService.copyWith(imageUrls: uploadedUrls);
      }

      // Sync image reorder if multiple images have server IDs
      final orderedIds = _images
          .map((u) => _imageIdByUrl[u])
          .whereType<String>()
          .where((id) => id.isNotEmpty)
          .toList();
      if (_isValidUuid(savedService.id) && orderedIds.length >= 2) {
        try {
          await repo.reorderServiceImages(
            serviceId: savedService.id,
            imageIds: orderedIds,
          );
        } catch (_) {}
      }

      // Sync any local pending Service Options to /api/vendor/services/{id}/options
      if (_isValidUuid(savedService.id)) {
        for (int idx = 0; idx < _serviceOptions.length; idx++) {
          final opt = _serviceOptions[idx];
          if (!_isValidUuid(opt.id)) {
            try {
              final createdOpt = await repo.createServiceOption(
                serviceId: savedService.id,
                name: opt.name,
                optionType: opt.optionType,
                pricingUnit: opt.pricingUnit,
                price: opt.price,
                maxPaxPerPackage: opt.maxPaxPerPackage,
                benefits: opt.benefits,
              );
              _serviceOptions[idx] = createdOpt;
            } catch (_) {}
          }
        }
      }

      _db.saveService(savedService);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isSavingDraft = false);
      ToastNotification.showError(context, 'Không thể lưu dịch vụ lên máy chủ: $e');
      return;
    }

    if (!mounted) return;
    setState(() => _isSavingDraft = false);
    ToastNotification.showSuccess(context, 'Đã lưu thông tin dịch vụ lên máy chủ');

    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ServiceSafetyCertScreen(service: savedService),
      ),
    );
  }



  @override

  Widget build(BuildContext context) {

    return Scaffold(

      backgroundColor: AppColors.surface,

      appBar: AppBar(

        backgroundColor: AppColors.surface,

        elevation: 0,

        leading: IconButton(

          icon: const Icon(Icons.arrow_back_ios_new, color: AppColors.secondary, size: 20),

          onPressed: () => Navigator.of(context).maybePop(),

        ),

        title: LocalizedText(

          _isEdit ? 'Sửa dịch vụ' : 'Tạo mới dịch vụ',

          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: AppColors.secondary),

        ),

      ),

      body: SafeArea(
        child: Column(
          children: [
            _buildStickySectionBar(),
            Expanded(
              child: SingleChildScrollView(
                controller: _scrollController,
                padding: const EdgeInsets.fromLTRB(16.0, 16.0, 16.0, 60.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [

              // SECTION 1: Thông tin cơ bản
              _buildSectionCard(
                key: _section1Key,
                title: 'Thông tin cơ bản',
                icon: Icons.edit_note,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const LocalizedText(
                          'Tên dịch vụ',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                        ),
                        InkWell(
                          onTap: () => setState(() => _showEnglishName = !_showEnglishName),
                          borderRadius: BorderRadius.circular(12),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(_showEnglishName ? Icons.remove_circle_outline : Icons.add_circle_outline, size: 13, color: AppColors.secondary),
                                const SizedBox(width: 4),
                                Text(
                                  _showEnglishName ? 'Ẩn tên tiếng Anh' : '+ Thêm tên tiếng Anh',
                                  style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _nameViController,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.onSurface),
                      decoration: InputDecoration(
                        hintText: 'VD: Chèo SUP ngắm bình minh biển Mỹ Khê',
                        hintStyle: const TextStyle(fontSize: 13, color: AppColors.outline),
                        filled: true,
                        fillColor: AppColors.surfaceContainerLow,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8)),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8)),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),
                        ),
                      ),
                    ),
                    if (_showEnglishName) ...[
                      const SizedBox(height: 10),
                      VendorTextField(
                        controller: _nameEnController,
                        label: 'Tên tiếng Anh (Tùy chọn)',
                        isRequired: false,
                        hint: 'VD: Sunrise SUP Surfing Experience',
                      ),
                    ],
                    const SizedBox(height: 6),
                    ValueListenableBuilder<TextEditingValue>(
                      valueListenable: _slugController,
                      builder: (context, val, _) {
                        final currentSlug = val.text.isNotEmpty ? val.text : _slugify(_nameViController.text);
                        return Row(
                          children: [
                            const Icon(Icons.link_rounded, size: 14, color: AppColors.secondary),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                'Liên kết tự động: danasea.vn/tours/$currentSlug',
                                style: const TextStyle(fontSize: 11, color: AppColors.tertiary, fontStyle: FontStyle.italic),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        );
                      },
                    ),

                    const SizedBox(height: 12),

                    const LocalizedText('Danh mục trải nghiệm', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface)),

                    const SizedBox(height: 6),

                    Container(

                      height: 48,

                      padding: const EdgeInsets.symmetric(horizontal: 14),

                      decoration: BoxDecoration(

                        color: AppColors.surfaceContainerLow,

                        borderRadius: BorderRadius.circular(12),

                        border: Border.all(color: AppColors.borderSubtle.withValues(alpha: 0.8)),

                      ),

                      child: DropdownButtonHideUnderline(

                        child: DropdownButton<String>(

                          value: _category,

                          isExpanded: true,

                          icon: const Icon(Icons.arrow_drop_down, color: AppColors.secondary),

                          items: const [

                            DropdownMenuItem(value: 'WATER_SPORTS', child: LocalizedText('Chèo SUP & Kayak', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500))),

                            DropdownMenuItem(value: 'DIVING', child: LocalizedText('Lặn ngắm san hô & Đi bộ dưới biển', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500))),

                            DropdownMenuItem(value: 'SURFING', child: LocalizedText('Lướt ván diều & Lướt sóng', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500))),

                            DropdownMenuItem(value: 'BOAT_CRUISE', child: LocalizedText('Cano lướt sóng & Dù bay biển', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500))),

                          ],

                          onChanged: (val) {

                            if (val != null) setState(() => _category = val);

                          },

                        ),

                      ),

                    ),

                    const SizedBox(height: 14),



                    // Multi-language Description Tabs

                    Row(

                      mainAxisAlignment: MainAxisAlignment.spaceBetween,

                      children: [

                        const Expanded(

                          child: LocalizedText(

                            'Mô tả chi tiết lịch trình',

                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                            maxLines: 1,

                            overflow: TextOverflow.ellipsis,

                          ),

                        ),

                        const SizedBox(width: 8),

                        Container(

                          padding: const EdgeInsets.all(2),

                          decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(16)),

                          child: Row(

                            children: [

                              _buildTabPill('vi', 'Tiếng Việt'),

                              _buildTabPill('en', 'Tiếng Anh'),

                            ],

                          ),

                        ),

                      ],

                    ),

                    const SizedBox(height: 8),

                    TextField(

                      controller: _descTab == 'vi' ? _descViController : _descEnController,

                      maxLines: 4,

                      style: const TextStyle(fontSize: 13.5, color: AppColors.onSurface, height: 1.4),

                      decoration: InputDecoration(

                        filled: true,

                        fillColor: AppColors.surfaceContainerLow,

                        contentPadding: const EdgeInsets.all(14),

                        border: OutlineInputBorder(

                          borderRadius: BorderRadius.circular(12),

                          borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8)),

                        ),

                        enabledBorder: OutlineInputBorder(

                          borderRadius: BorderRadius.circular(12),

                          borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8)),

                        ),

                        focusedBorder: OutlineInputBorder(

                          borderRadius: BorderRadius.circular(12),

                          borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),

                        ),

                        hintText: tr(context, _descTab == 'vi' ? 'Mô tả chi tiết hành trình bằng tiếng Việt...' : 'Tour itinerary in English...'),

                        hintStyle: const TextStyle(fontSize: 13, color: AppColors.outline),

                      ),

                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: _isOptimizingAi ? null : _optimizeListingWithAi,
                        icon: _isOptimizingAi
                            ? const SizedBox(
                                width: 15,
                                height: 15,
                                child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.secondary),
                              )
                            : const Icon(Icons.auto_awesome, size: 16, color: AppColors.secondary),
                        label: LocalizedText(
                          _isOptimizingAi
                              ? 'AI đang chấm điểm & tối ưu...'
                              : 'AI Tối ưu tiêu đề & mô tả',
                          style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: AppColors.secondary),
                        ),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.secondary),
                          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // SECTION 2: Vận hành & Đơn giá duy nhất
              _buildSectionCard(
                key: _section2Key,
                title: 'Vận hành & Đơn giá',

                icon: Icons.monetization_on,

                child: Column(

                  crossAxisAlignment: CrossAxisAlignment.start,

                  children: [

                    Container(

                      padding: const EdgeInsets.all(12),

                      decoration: BoxDecoration(

                        color: AppColors.surfaceContainerLow,

                        borderRadius: AppShapes.radiusSm,

                      ),

                      child: Column(

                        crossAxisAlignment: CrossAxisAlignment.start,

                        children: [

                          const LocalizedText(

                            'Đơn giá dịch vụ',

                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface),

                          ),

                          const SizedBox(height: 8),

                          Container(

                            height: 48,

                            padding: const EdgeInsets.symmetric(horizontal: 14),

                            decoration: BoxDecoration(

                              color: AppColors.surfaceContainerLowest,

                              borderRadius: BorderRadius.circular(12),

                              border: Border.all(color: AppColors.borderSubtle.withValues(alpha: 0.8)),

                            ),

                            child: Row(

                              children: [

                                Expanded(

                                  child: TextField(

                                    controller: _priceController,

                                    keyboardType: TextInputType.number,

                                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.primary),

                                    decoration: const InputDecoration(border: InputBorder.none, contentPadding: EdgeInsets.zero, isDense: true),

                                  ),

                                ),

                                const LocalizedText('VNĐ', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.primary)),

                                const SizedBox(width: 4),

                                const LocalizedText('/ khách', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),

                              ],

                            ),

                          ),

                          const SizedBox(height: 6),

                          const Row(

                            children: [

                              Icon(Icons.info_outline, size: 12, color: AppColors.secondary),

                              SizedBox(width: 4),

                              Expanded(

                                child: LocalizedText('Giá trọn gói đã bao gồm thiết bị chèo, áo phao và hướng dẫn viên.',

                                    style: TextStyle(fontSize: 10, color: AppColors.tertiary)),

                              ),

                            ],

                          ),

                        ],

                      ),

                    ),

                    const SizedBox(height: 12),

                    Row(

                      children: [

                        Expanded(

                          child: VendorTextField(

                            controller: _durationController,

                            label: 'Thời lượng (phút)',

                            hint: '120',

                            keyboardType: TextInputType.number,

                            prefixIcon: Icons.schedule,

                          ),

                        ),

                        const SizedBox(width: 10),

                        Expanded(

                          child: VendorTextField(

                            controller: _capacityController,

                            label: 'Sức chứa tối đa (Khách)',

                            hint: '10',

                            keyboardType: TextInputType.number,

                            prefixIcon: Icons.groups,

                          ),

                        ),

                      ],

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),

              // SECTION 2B: Gói & Lựa chọn bán (Service Options)
              _buildSectionCard(
                title: 'Gói & Tùy chọn giá',
                icon: Icons.sell_outlined,
                badge: '${_serviceOptions.length} gói',
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const LocalizedText(
                      'Tùy chọn bán theo khách ghép đoàn lẻ hoặc bao trọn tàu/cano theo gói riêng.',
                      style: TextStyle(fontSize: 11.5, color: AppColors.tertiary),
                    ),
                    const SizedBox(height: 10),
                    if (_isLoadingOptions)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 12),
                        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                      )
                    else if (_serviceOptions.isEmpty)
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const LocalizedText(
                          'Chưa có lựa chọn bổ sung. Dịch vụ sẽ áp dụng đơn giá chuẩn bên trên.',
                          style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                        ),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _serviceOptions.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 8),
                        itemBuilder: (context, idx) {
                          final opt = _serviceOptions[idx];
                          final isPrivate = opt.optionType == 'PRIVATE';
                          return Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: AppColors.borderSubtle),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Expanded(
                                            child: Text(
                                              opt.name,
                                              style: const TextStyle(
                                                fontSize: 13,
                                                fontWeight: FontWeight.bold,
                                                color: AppColors.onSurface,
                                              ),
                                            ),
                                          ),
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                            decoration: BoxDecoration(
                                              color: isPrivate
                                                  ? AppColors.primaryFixed.withValues(alpha: 0.45)
                                                  : AppColors.secondaryContainer.withValues(alpha: 0.55),
                                              borderRadius: BorderRadius.circular(6),
                                            ),
                                            child: Text(
                                              isPrivate ? 'Bao trọn gói' : 'Ghép đoàn',
                                              style: TextStyle(
                                                fontSize: 10,
                                                fontWeight: FontWeight.bold,
                                                color: isPrivate ? AppColors.primary : AppColors.secondary,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        '${opt.price.toStringAsFixed(0)} đ / ${opt.pricingUnit == 'PER_PACKAGE' ? 'gói' : 'người'}${opt.maxPaxPerPackage != null ? ' • Tối đa ${opt.maxPaxPerPackage} khách/gói' : ''}',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.w700,
                                          color: AppColors.primary,
                                        ),
                                      ),
                                      if (opt.benefits != null && opt.benefits!.isNotEmpty) ...[
                                        const SizedBox(height: 3),
                                        Text(
                                          'Quyền lợi: ${opt.benefits}',
                                          style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                                        ),
                                      ],
                                    ],
                                  ),
                                ),
                                IconButton(
                                  tooltip: 'Sửa gói bán',
                                  icon: const Icon(Icons.edit_outlined, size: 17, color: AppColors.secondary),
                                  onPressed: () => _showOptionFormModal(existingOption: opt, editIndex: idx),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    const SizedBox(height: 10),
                    OutlinedButton.icon(
                      onPressed: () => _showOptionFormModal(),
                      icon: const Icon(Icons.add_circle_outline, size: 16),
                      label: const LocalizedText(
                        'Thêm gói / tùy chọn giá',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                      ),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.secondary,
                        side: const BorderSide(color: AppColors.secondary),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),



              // SECTION 3: Điểm đón khách
              _buildSectionCard(
                key: _section3Key,
                title: 'Điểm đón khách',

                icon: Icons.location_on,

                child: Column(

                  children: [

                    VendorTextField(

                      controller: _meetingPointNameController,

                      label: 'Tên điểm hẹn tập kết',

                      hint: 'Bến bãi nhà cung cấp',

                    ),

                    const SizedBox(height: 12),

                    VendorTextField(

                      controller: _meetingPointAddressController,

                      label: 'Địa chỉ cụ thể',

                      hint: 'Lô 12 Võ Nguyên Giáp, Sơn Trà, Đà Nẵng',

                    ),

                    const SizedBox(height: 12),

                    Row(

                      children: [

                        Expanded(

                          child: VendorTextField(

                            controller: _latController,

                            label: 'Vĩ độ',

                            hint: '16.0601',

                            keyboardType: const TextInputType.numberWithOptions(decimal: true),

                          ),

                        ),

                        const SizedBox(width: 10),

                        Expanded(

                          child: VendorTextField(

                            controller: _lngController,

                            label: 'Kinh độ',

                            hint: '108.2472',

                            keyboardType: const TextInputType.numberWithOptions(decimal: true),

                          ),

                        ),

                      ],

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // SECTION 4: Quản lý hình ảnh
              _buildSectionCard(
                key: _section4Key,
                title: 'Hình ảnh dịch vụ',

                icon: Icons.photo_library,

                child: Column(

                  crossAxisAlignment: CrossAxisAlignment.start,

                  children: [

                    if (_images.isEmpty)

                      const LocalizedText('Chưa có hình ảnh nào', style: TextStyle(fontSize: 12, color: AppColors.tertiary))

                    else

                      ListView.separated(

                        shrinkWrap: true,

                        physics: const NeverScrollableScrollPhysics(),

                        itemCount: _images.length,

                        separatorBuilder: (_, __) => const SizedBox(height: 8),

                        itemBuilder: (context, i) {

                          return Container(

                            padding: const EdgeInsets.all(8),

                            decoration: BoxDecoration(

                              color: AppColors.surfaceContainerLow,

                              borderRadius: BorderRadius.circular(10),

                            ),

                            child: Row(

                              children: [

                                ClipRRect(
                                  borderRadius: BorderRadius.circular(6),
                                  child: Container(
                                    width: 48,
                                    height: 40,
                                    color: AppColors.surfaceContainerHighest,
                                    child: (_images[i].startsWith('http://') || _images[i].startsWith('https://'))
                                        ? Image.network(
                                            _images[i],
                                            fit: BoxFit.cover,
                                            errorBuilder: (_, __, ___) =>
                                                const Icon(Icons.image, color: AppColors.tertiary, size: 20),
                                          )
                                        : File(_images[i]).existsSync()
                                            ? Image.file(
                                                File(_images[i]),
                                                fit: BoxFit.cover,
                                              )
                                            : const Icon(Icons.image, color: AppColors.tertiary, size: 20),
                                  ),
                                ),

                                const SizedBox(width: 10),

                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      LocalizedText(
                                        i == 0 ? 'Ảnh bìa đại diện' : 'Ảnh ${i + 1}',
                                        style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: i == 0 ? FontWeight.bold : FontWeight.normal,
                                          color: i == 0 ? AppColors.secondary : AppColors.onSurface,
                                        ),
                                      ),
                                      Text(
                                        (_images[i].startsWith('http://') || _images[i].startsWith('https://'))
                                            ? 'Đã lưu trên Cloudinary'
                                            : 'Chờ tải lên Cloud khi lưu',
                                        style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                                      ),
                                    ],
                                  ),
                                ),

                                if (i > 0)
                                  TextButton(
                                    onPressed: _isReorderingImages ? null : () => _moveImage(i, 0),
                                    style: TextButton.styleFrom(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      minimumSize: Size.zero,
                                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                    ),
                                    child: const LocalizedText(
                                      'Đặt bìa',
                                      style: TextStyle(
                                        fontSize: 10.5,
                                        fontWeight: FontWeight.bold,
                                        color: AppColors.secondary,
                                      ),
                                    ),
                                  ),

                                IconButton(

                                  icon: const Icon(Icons.arrow_upward, size: 16),

                                  onPressed: (i > 0 && !_isReorderingImages)
                                      ? () => _moveImage(i, i - 1)
                                      : null,

                                ),

                                IconButton(

                                  icon: const Icon(Icons.arrow_downward, size: 16),

                                  onPressed: (i < _images.length - 1 && !_isReorderingImages)
                                      ? () => _moveImage(i, i + 1)
                                      : null,

                                ),

                                IconButton(

                                  icon: const Icon(Icons.delete_outline, color: AppColors.error, size: 18),

                                  onPressed: () => _deleteImageAt(i),

                                ),

                              ],

                            ),

                          );

                        },

                      ),

                    const SizedBox(height: 10),

                    OutlinedButton.icon(
                      onPressed: _isUploadingImage ? null : _pickAndUploadImage,
                      icon: _isUploadingImage
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.cloud_upload_outlined, size: 16),
                      label: LocalizedText(
                        _isUploadingImage ? 'Đang tải ảnh lên Cloud...' : 'Thêm ảnh trải nghiệm (Tải lên Cloud)',
                        style: const TextStyle(fontSize: 12),
                      ),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.secondary,
                        side: const BorderSide(color: AppColors.secondary),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      ),
                    ),

                    const SizedBox(height: 4),

                    const LocalizedText('Ảnh đầu tiên là ảnh bìa. Dùng mũi tên để thay đổi thứ tự.', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // Action Buttons

              Row(

                children: [

                  Expanded(

                    child: VendorButton(

                      text: 'Hủy thay đổi',

                      variant: VendorButtonVariant.secondary,

                      onPressed: () => Navigator.of(context).maybePop(),

                    ),

                  ),

                  const SizedBox(width: 12),

                  Expanded(

                    flex: 2,

                    child: VendorButton(

                      text: 'Sang bước 2: An toàn',

                      icon: Icons.arrow_forward,

                      onPressed: _saveDraftAndProceed,

                    ),

                  ),

                ],

              ),

              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    ],
  ),
),
);
}

  Widget _buildStickySectionBar() {
    final sections = [
      (title: '📝 Thông tin', key: _section1Key, icon: Icons.edit_note_rounded),
      (title: '🏷️ Giá & Gói', key: _section2Key, icon: Icons.sell_outlined),
      (title: '📍 Điểm đón', key: _section3Key, icon: Icons.location_on_outlined),
      (title: '📸 Hình ảnh', key: _section4Key, icon: Icons.photo_library_outlined),
    ];
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        border: Border(
          bottom: BorderSide(color: Color(0xFFE2E8F0)),
        ),
        boxShadow: [
          BoxShadow(
            color: Color(0x06000000),
            blurRadius: 4,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: List.generate(sections.length, (idx) {
            final s = sections[idx];
            final active = _activeSectionIndex == idx;
            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: InkWell(
                onTap: () => _scrollToSection(s.key, idx),
                borderRadius: BorderRadius.circular(20),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: active ? AppColors.secondary : AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: active ? AppColors.secondary : const Color(0xFFE2E8F0),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        s.icon,
                        size: 14,
                        color: active ? Colors.white : AppColors.secondary,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        s.title,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: active ? FontWeight.bold : FontWeight.w500,
                          color: active ? Colors.white : AppColors.onSurface,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }



  Widget _buildSectionCard({

    Key? key,

    required String title,

    required IconData icon,

    String? badge,

    required Widget child,

  }) {

    return Container(

      key: key,

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

            children: [

              Expanded(

                child: Row(

                  children: [

                    Icon(icon, color: AppColors.secondary, size: 20),

                    const SizedBox(width: 8),

                    Expanded(

                      child: LocalizedText(

                        title,

                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                        maxLines: 1,

                        overflow: TextOverflow.ellipsis,

                      ),

                    ),

                  ],

                ),

              ),

              if (badge != null) ...[

                const SizedBox(width: 8),

                Container(

                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),

                  decoration: BoxDecoration(color: AppColors.primaryFixed.withOpacity(0.5), borderRadius: BorderRadius.circular(10)),

                  child: LocalizedText(badge, style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.primary)),

                ),

              ],

            ],

          ),

          const SizedBox(height: 14),

          child,

        ],

      ),

    );

  }



  Widget _buildTabPill(String tab, String label) {

    final active = _descTab == tab;

    return GestureDetector(

      onTap: () => setState(() => _descTab = tab),

      child: Container(

        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),

        decoration: BoxDecoration(

          color: active ? AppColors.surfaceContainerLowest : Colors.transparent,

          borderRadius: BorderRadius.circular(14),

          boxShadow: active ? AppShapes.shadowSm : null,

        ),

        child: LocalizedText(

          label,

          style: TextStyle(

            fontSize: 10,

            fontWeight: active ? FontWeight.bold : FontWeight.normal,

            color: active ? AppColors.primary : AppColors.tertiary,

          ),

        ),

      ),

    );

  }

}

