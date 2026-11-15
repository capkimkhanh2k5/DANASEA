import 'package:vendor_mobile/core/l10n/app_localizations.dart';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../../../core/theme/app_colors.dart';

import '../../../../core/theme/app_shapes.dart';

import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';

import '../../../../core/widgets/vendor_button.dart';

import '../../../../core/widgets/status_badge.dart';

import '../../../../core/widgets/toast_notification.dart';



class ServiceSafetyCertScreen extends StatefulWidget {

  final ServiceModel service;



  const ServiceSafetyCertScreen({super.key, required this.service});



  @override

  State<ServiceSafetyCertScreen> createState() => _ServiceSafetyCertScreenState();

}



class _ServiceSafetyCertScreenState extends State<ServiceSafetyCertScreen> {

  final _db = VendorStore.instance;
  final _serviceRepo = VendorServiceRepository();



  late bool _isWeatherSensitive;

  late TextEditingController _minWindController;

  late TextEditingController _maxWaveController;

  late TextEditingController _waiverController;



  @override

  void initState() {

    super.initState();

    _isWeatherSensitive = widget.service.isWeatherSensitive;

    _minWindController = TextEditingController(text: widget.service.minWindKmh.toStringAsFixed(1));

    _maxWaveController = TextEditingController(text: widget.service.maxWaveM.toStringAsFixed(1));

    _waiverController = TextEditingController(text: widget.service.waiverContent);
    if (widget.service.serviceId.isNotEmpty) {
      _serviceRepo.getServiceSafetyDocuments(widget.service.serviceId).then((_) {
        if (mounted) setState(() {});
      });
    }
  }



  @override

  void dispose() {

    _minWindController.dispose();

    _maxWaveController.dispose();

    _waiverController.dispose();

    super.dispose();

  }



  Future<void> _uploadSafetyDoc({String? titleHint}) async {
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
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  titleHint != null
                      ? 'Tải lên: $titleHint'
                      : 'Tải lên chứng chỉ an toàn dịch vụ (Cloud)',
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined, color: AppColors.secondary),
                title: const LocalizedText('Chọn tệp ảnh chứng chỉ từ Thư viện'),
                onTap: () => Navigator.pop(ctx, ImageSource.gallery),
              ),
              ListTile(
                leading: const Icon(Icons.camera_alt_outlined, color: AppColors.primary),
                title: const LocalizedText('Chụp ảnh chứng chỉ trực tiếp'),
                onTap: () => Navigator.pop(ctx, ImageSource.camera),
              ),
            ],
          ),
        ),
      ),
    );
    if (source == null || !mounted) return;
    try {
      final picked = await ImagePicker().pickImage(source: source, imageQuality: 85);
      if (picked == null || !mounted) return;
      final finalName = titleHint != null ? '$titleHint - ${picked.name}' : picked.name;
      await _serviceRepo.uploadServiceSafetyDocument(
        serviceId: widget.service.serviceId,
        fileName: finalName,
        filePath: picked.path,
      );
      if (!mounted) return;
      setState(() {});
      ToastNotification.showSuccess(context, 'Đã tải chứng chỉ lên Cloudinary (Đang chờ duyệt)');
    } catch (e) {
      if (!mounted) return;
      ToastNotification.showError(context, 'Lỗi tải chứng chỉ lên Cloud: $e');
    }
  }

  ServiceModel _updateModel(ServiceStatus newStatus) {

    return widget.service.copyWith(

      weatherSensitive: _isWeatherSensitive,

      minWindKmh: double.tryParse(_minWindController.text) ?? widget.service.minWindKmh,

      maxWaveM: double.tryParse(_maxWaveController.text) ?? widget.service.maxWaveM,

      waiverContent: _waiverController.text.trim(),

      status: newStatus,

      updatedAt: DateTime.now(),

    );

  }



  Future<void> _save(ServiceStatus targetStatus, String successMsg) async {
    final updated = _updateModel(targetStatus);
    try {
      ServiceModel saved = updated;
      if (RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(updated.id)) {
        saved = await _serviceRepo.updateService(updated);
      } else {
        saved = await _serviceRepo.createService(updated);
      }
      if (targetStatus == ServiceStatus.pendingReview ||
          targetStatus == ServiceStatus.paused ||
          targetStatus == ServiceStatus.published) {
        saved = await _serviceRepo.updateServiceStatus(saved.id, targetStatus);
      }
      _db.saveService(saved);
      if (!mounted) return;
      ToastNotification.showSuccess(context, successMsg);
      Navigator.of(context).popUntil((route) => route.isFirst);
    } catch (e) {
      if (!mounted) return;
      ToastNotification.showError(context, 'Không thể cập nhật trạng thái dịch vụ: $e');
    }
  }



  @override

  Widget build(BuildContext context) {

    // Look up service-specific safety docs

    final safetyDocs = _db.serviceSafetyDocs.where((d) => d.serviceId == widget.service.serviceId).toList();



    return Scaffold(

      backgroundColor: AppColors.surface,

      appBar: AppBar(

        backgroundColor: AppColors.surface,

        elevation: 0,

        leading: IconButton(

          icon: const Icon(Icons.arrow_back_ios_new, color: AppColors.secondary, size: 20),

          onPressed: () => Navigator.of(context).maybePop(),

        ),

        title: const LocalizedText('An toàn & Chứng chỉ', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.secondary)),

      ),

      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16.0, 16.0, 16.0, 40.0),

          child: Column(

            crossAxisAlignment: CrossAxisAlignment.start,

            children: [

              // Progress Bar (Bước 2/2)

              Row(

                children: [

                  Expanded(

                    child: Container(height: 5, decoration: BoxDecoration(color: AppColors.secondary, borderRadius: BorderRadius.circular(3))),

                  ),

                  const SizedBox(width: 6),

                  Expanded(

                    child: Container(height: 5, decoration: BoxDecoration(color: AppColors.secondary, borderRadius: BorderRadius.circular(3))),

                  ),

                ],

              ),

              const SizedBox(height: 6),

              const Row(

                children: [

                  Expanded(

                    child: LocalizedText('1. Thông tin dịch vụ',

                      style: TextStyle(fontSize: 12, color: AppColors.tertiary),

                      maxLines: 1,

                      overflow: TextOverflow.ellipsis,

                    ),

                  ),

                  Expanded(

                    child: LocalizedText('2. An toàn & Chứng chỉ',

                      textAlign: TextAlign.end,

                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.secondary),

                      maxLines: 1,

                      overflow: TextOverflow.ellipsis,

                    ),

                  ),

                ],

              ),

              const SizedBox(height: 14),



              // Step 2 Badge & Status

              Row(

                mainAxisAlignment: MainAxisAlignment.spaceBetween,

                children: [

                  Expanded(

                    child: Row(

                      children: [

                        Container(

                          width: 24,

                          height: 24,

                          decoration: const BoxDecoration(

                            shape: BoxShape.circle,

                            color: AppColors.secondaryFixed,

                          ),

                          child: const Center(

                            child: Icon(Icons.check, size: 14, color: AppColors.onSecondaryFixed),

                          ),

                        ),

                        const SizedBox(width: 8),

                        const Expanded(

                          child: LocalizedText(

                            'Bước 2/2: An toàn & Chứng chỉ dịch vụ',

                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.secondary),

                            maxLines: 1,

                            overflow: TextOverflow.ellipsis,

                          ),

                        ),

                      ],

                    ),

                  ),

                  const SizedBox(width: 8),

                  Container(

                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),

                    decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(12)),

                    child: Row(

                      mainAxisSize: MainAxisSize.min,

                      children: [

                        Container(width: 6, height: 6, decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary)),

                        const SizedBox(width: 4),

                        LocalizedText(widget.service.status.labelVi, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSurfaceVariant)),

                      ],

                    ),

                  ),

                ],

              ),

              const SizedBox(height: 16),



              // CARD 1: Weather & Ocean Safety Constraints

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

                      children: [

                        Container(

                          width: 36,

                          height: 36,

                          decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondaryFixed),

                          child: const Icon(Icons.air, color: AppColors.secondary, size: 20),

                        ),

                        const SizedBox(width: 10),

                        const Expanded(

                          child: Column(

                            crossAxisAlignment: CrossAxisAlignment.start,

                            children: [

                              LocalizedText('Giới hạn Khí tượng & Biển',

                                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                                  maxLines: 1,

                                  overflow: TextOverflow.ellipsis),

                              LocalizedText('Tự động khóa lịch đặt nếu vượt ngưỡng',

                                  style: TextStyle(fontSize: 11, color: AppColors.tertiary),

                                  maxLines: 1,

                                  overflow: TextOverflow.ellipsis),

                            ],

                          ),

                        ),

                      ],

                    ),

                    const SizedBox(height: 14),



                    // Toggle Weather Sensitive

                    Container(

                      padding: const EdgeInsets.all(12),

                      decoration: BoxDecoration(

                        color: AppColors.surfaceContainerLow,

                        borderRadius: AppShapes.radiusSm,

                      ),

                      child: Row(

                        mainAxisAlignment: MainAxisAlignment.spaceBetween,

                        children: [

                          const Expanded(

                            child: Column(

                              crossAxisAlignment: CrossAxisAlignment.start,

                              children: [

                                LocalizedText('Độ nhạy thời tiết biển', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface)),

                                LocalizedText('Dịch vụ phụ thuộc điều kiện thời tiết biển', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),

                              ],

                            ),

                          ),

                          Switch.adaptive(

                            value: _isWeatherSensitive,

                            activeThumbColor: AppColors.secondary, activeTrackColor: AppColors.secondaryFixed,

                            onChanged: (val) => setState(() => _isWeatherSensitive = val),

                          ),

                        ],

                      ),

                    ),

                    const SizedBox(height: 12),



                    // Constraint inputs

                    Row(

                      children: [

                        Expanded(

                          child: Column(

                            crossAxisAlignment: CrossAxisAlignment.start,

                            children: [

                              const LocalizedText('Ngưỡng gió tối thiểu (km/h)', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSurface)),

                              const SizedBox(height: 4),

                              TextField(

                                controller: _minWindController,

                                keyboardType: const TextInputType.numberWithOptions(decimal: true),

                                decoration: InputDecoration(

                                  filled: true,

                                  fillColor: AppColors.surfaceContainerLow,

                                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),

                                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),

                                  suffixText: 'km/h',

                                ),

                              ),

                            ],

                          ),

                        ),

                        const SizedBox(width: 12),

                        Expanded(

                          child: Column(

                            crossAxisAlignment: CrossAxisAlignment.start,

                            children: [

                              const LocalizedText('Chiều cao sóng tối đa (m)', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSurface)),

                              const SizedBox(height: 4),

                              TextField(

                                controller: _maxWaveController,

                                keyboardType: const TextInputType.numberWithOptions(decimal: true),

                                decoration: InputDecoration(

                                  filled: true,

                                  fillColor: AppColors.surfaceContainerLow,

                                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),

                                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),

                                  suffixText: 'm',

                                ),

                              ),

                            ],

                          ),

                        ),

                      ],

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // CARD 2: Safety Waiver Content

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

                      children: const [

                        Icon(Icons.assignment_turned_in, size: 20, color: AppColors.secondary),

                        SizedBox(width: 8),

                        Expanded(

                          child: LocalizedText(

                            'Cam kết miễn trừ trách nhiệm',

                            style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                            maxLines: 1,

                            overflow: TextOverflow.ellipsis,

                          ),

                        ),

                      ],

                    ),

                    const SizedBox(height: 4),

                    const LocalizedText('Khách hàng buộc phải ký điện tử trước giờ lên ván SUP', style: TextStyle(fontSize: 11, color: AppColors.tertiary)),

                    const SizedBox(height: 12),

                    Container(

                      padding: const EdgeInsets.all(12),

                      decoration: BoxDecoration(

                        color: AppColors.surfaceContainerLow,

                        borderRadius: AppShapes.radiusSm,

                      ),

                      child: Column(

                        crossAxisAlignment: CrossAxisAlignment.start,

                        children: [

                          const Row(

                            mainAxisAlignment: MainAxisAlignment.spaceBetween,

                            children: [

                              Expanded(

                                child: LocalizedText(

                                  'NỘI DUNG XÁC THỰC BẮT BUỘC',

                                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5),

                                  overflow: TextOverflow.ellipsis,

                                ),

                              ),

                              SizedBox(width: 8),

                              Icon(Icons.lock_outline, size: 14, color: AppColors.tertiary),

                            ],

                          ),

                          const SizedBox(height: 8),

                          TextField(

                            controller: _waiverController,

                            maxLines: 5,

                            style: const TextStyle(fontSize: 12, height: 1.5),

                            decoration: const InputDecoration(border: InputBorder.none, contentPadding: EdgeInsets.zero),

                          ),

                        ],

                      ),

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // CARD 3: Service-specific Safety Documents

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

                      children: [

                        const Expanded(

                          child: Row(

                            children: [

                              Icon(Icons.verified_outlined, size: 20, color: AppColors.secondary),

                              SizedBox(width: 8),

                              Expanded(

                                child: LocalizedText(

                                  'Chứng chỉ an toàn dịch vụ',

                                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                                  maxLines: 1,

                                  overflow: TextOverflow.ellipsis,

                                ),

                              ),

                            ],

                          ),

                        ),

                        const SizedBox(width: 8),

                        OutlinedButton.icon(
                          onPressed: () => _uploadSafetyDoc(),
                          icon: const Icon(Icons.cloud_upload_outlined, size: 14),
                          label: const LocalizedText('Tải lên Cloud', style: TextStyle(fontSize: 11)),
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            side: const BorderSide(color: AppColors.secondary),
                            foregroundColor: AppColors.secondary,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const LocalizedText(
                      'Các chứng chỉ an toàn, phao cứu sinh và sơ cấp cứu bắt buộc cho dịch vụ biển này.',
                      style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                    ),
                    const SizedBox(height: 12),

                    // Render Standard Safety Documents List with Status Cards (Emerald for Uploaded, Orange for Missing)
                    Builder(
                      builder: (context) {
                        const standardDocs = [
                          (
                            title: 'Đăng kiểm & An toàn kỹ thuật phương tiện',
                            desc: 'Giấy chứng nhận an toàn phương tiện cano, thuyền, ván chèo SUP',
                            icon: Icons.directions_boat_outlined,
                            keyword: 'dang kiem',
                          ),
                          (
                            title: 'Bảo hiểm trách nhiệm dân sự hành khách',
                            desc: 'Hợp đồng bảo hiểm du lịch bắt buộc cho du khách tham gia',
                            icon: Icons.health_and_safety_outlined,
                            keyword: 'bao hiem',
                          ),
                          (
                            title: 'Chứng chỉ Cứu hộ bờ biển & Sơ cấp cứu',
                            desc: 'Bằng cấp/chứng chỉ cứu sinh bờ biển, sơ cấp cứu của HDV/thuyền trưởng',
                            icon: Icons.medical_services_outlined,
                            keyword: 'cuu ho',
                          ),
                          (
                            title: 'Phương án ứng phó sự cố hàng hải & Áo phao',
                            desc: 'Quy trình cứu nạn cứu hộ và biên bản kiểm tra áo phao định kỳ',
                            icon: Icons.warning_amber_rounded,
                            keyword: 'ao phao',
                          ),
                        ];

                        final matchedDocIds = <String>{};

                        return Column(
                          children: [
                            ...standardDocs.map((item) {
                              ServiceSafetyDocumentModel? foundDoc;
                              for (final doc in safetyDocs) {
                                final nameLower = doc.documentName.toLowerCase();
                                if (nameLower.contains(item.keyword) ||
                                    nameLower.contains(item.title.toLowerCase().substring(0, 8))) {
                                  foundDoc = doc;
                                  matchedDocIds.add(doc.docId);
                                  break;
                                }
                              }
                              // Also assign if there's any unassigned doc sequentially
                              if (foundDoc == null && safetyDocs.isNotEmpty) {
                                for (final doc in safetyDocs) {
                                  if (!matchedDocIds.contains(doc.docId)) {
                                    foundDoc = doc;
                                    matchedDocIds.add(doc.docId);
                                    break;
                                  }
                                }
                              }

                              final isUploaded = foundDoc != null;

                              return Container(
                                margin: const EdgeInsets.only(bottom: 10),
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: isUploaded
                                      ? const Color(0xFFF0FDF4)
                                      : const Color(0xFFFFF7ED),
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: isUploaded
                                        ? const Color(0xFF059669)
                                        : const Color(0xFFF97316),
                                    width: isUploaded ? 1.5 : 1.2,
                                  ),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Container(
                                          width: 34,
                                          height: 34,
                                          decoration: BoxDecoration(
                                            color: isUploaded
                                                ? const Color(0xFFDCFCE7)
                                                : const Color(0xFFFFEDD5),
                                            shape: BoxShape.circle,
                                          ),
                                          child: Icon(
                                            item.icon,
                                            size: 18,
                                            color: isUploaded
                                                ? const Color(0xFF059669)
                                                : const Color(0xFFEA580C),
                                          ),
                                        ),
                                        const SizedBox(width: 10),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(
                                                item.title,
                                                style: const TextStyle(
                                                  fontSize: 13,
                                                  fontWeight: FontWeight.bold,
                                                  color: AppColors.onSurface,
                                                ),
                                              ),
                                              const SizedBox(height: 2),
                                              Text(
                                                item.desc,
                                                style: const TextStyle(
                                                  fontSize: 10.5,
                                                  color: AppColors.tertiary,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                              horizontal: 8, vertical: 3),
                                          decoration: BoxDecoration(
                                            color: isUploaded
                                                ? const Color(0xFF059669)
                                                : const Color(0xFFEA580C),
                                            borderRadius: BorderRadius.circular(12),
                                          ),
                                          child: Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: [
                                              Icon(
                                                isUploaded
                                                    ? Icons.check_circle_rounded
                                                    : Icons.warning_amber_rounded,
                                                size: 12,
                                                color: Colors.white,
                                              ),
                                              const SizedBox(width: 4),
                                              Text(
                                                isUploaded ? 'Đã tải lên' : 'Chưa có',
                                                style: const TextStyle(
                                                  fontSize: 10,
                                                  fontWeight: FontWeight.bold,
                                                  color: Colors.white,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 10),
                                    if (isUploaded)
                                      Row(
                                        mainAxisAlignment:
                                            MainAxisAlignment.spaceBetween,
                                        children: [
                                          Expanded(
                                            child: Text(
                                              'Tệp: ${foundDoc.documentName} • ${foundDoc.uploadedAt.day}/${foundDoc.uploadedAt.month}',
                                              style: const TextStyle(
                                                fontSize: 11,
                                                fontWeight: FontWeight.w500,
                                                color: Color(0xFF065F46),
                                              ),
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                          ),
                                          StatusBadge(status: foundDoc.status),
                                        ],
                                      )
                                    else
                                      SizedBox(
                                        width: double.infinity,
                                        child: OutlinedButton.icon(
                                          onPressed: () =>
                                              _uploadSafetyDoc(titleHint: item.title),
                                          icon: const Icon(Icons.upload_file_outlined,
                                              size: 15),
                                          label: const Text(
                                            'Tải ảnh / tài liệu chứng nhận',
                                            style: TextStyle(
                                              fontSize: 11.5,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                          style: OutlinedButton.styleFrom(
                                            foregroundColor: const Color(0xFFC2410C),
                                            side: const BorderSide(
                                                color: Color(0xFFF97316)),
                                            padding: const EdgeInsets.symmetric(
                                                vertical: 8),
                                            shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(8),
                                            ),
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              );
                            }),

                            // Render any remaining extra uploaded documents
                            if (safetyDocs.length > matchedDocIds.length) ...[
                              const SizedBox(height: 8),
                              const Align(
                                alignment: Alignment.centerLeft,
                                child: Text(
                                  'Tài liệu an toàn bổ sung khác:',
                                  style: TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.secondary,
                                  ),
                                ),
                              ),
                              const SizedBox(height: 6),
                              ...safetyDocs
                                  .where((d) => !matchedDocIds.contains(d.docId))
                                  .map((doc) => Container(
                                        margin: const EdgeInsets.only(bottom: 6),
                                        padding: const EdgeInsets.all(10),
                                        decoration: BoxDecoration(
                                          color: const Color(0xFFF0FDF4),
                                          borderRadius:
                                              BorderRadius.circular(10),
                                          border: Border.all(
                                              color: const Color(0xFF059669)),
                                        ),
                                        child: Row(
                                          children: [
                                            const Icon(Icons.picture_as_pdf,
                                                color: Color(0xFF059669),
                                                size: 20),
                                            const SizedBox(width: 8),
                                            Expanded(
                                              child: Text(
                                                doc.documentName,
                                                style: const TextStyle(
                                                  fontSize: 11.5,
                                                  fontWeight: FontWeight.w600,
                                                ),
                                              ),
                                            ),
                                            StatusBadge(status: doc.status),
                                          ],
                                        ),
                                      )),
                            ],
                          ],
                        );
                      },
                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // CARD 4: Live Customer Preview Card

              const Row(

                children: [

                  Icon(Icons.visibility, size: 18, color: AppColors.primary),

                  SizedBox(width: 6),

                  Expanded(

                    child: LocalizedText(

                      'Xem trước hiển thị khách hàng',

                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),

                      maxLines: 1,

                      overflow: TextOverflow.ellipsis,

                    ),

                  ),

                ],

              ),

              const SizedBox(height: 8),

              Container(

                decoration: BoxDecoration(

                  color: AppColors.surfaceContainerLowest,

                  borderRadius: AppShapes.radiusMd,

                  boxShadow: AppShapes.shadowSm,

                ),

                child: Column(

                  crossAxisAlignment: CrossAxisAlignment.start,

                  children: [

                    Container(

                      height: 140,

                      decoration: const BoxDecoration(

                        color: AppColors.secondary,

                        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),

                      ),

                      child: Stack(

                        children: [

                          Center(

                            child: Icon(Icons.surfing, size: 60, color: Colors.white.withOpacity(0.3)),

                          ),

                          Positioned(

                            top: 10,

                            right: 10,

                            child: Container(

                              width: 32,

                              height: 32,

                              decoration: const BoxDecoration(shape: BoxShape.circle, color: Colors.white),

                              child: const Icon(Icons.favorite_border, color: AppColors.primary, size: 18),

                            ),

                          ),

                          Positioned(

                            bottom: 8,

                            left: 10,

                            right: 10,

                            child: Container(

                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),

                              decoration: BoxDecoration(

                                color: Colors.black.withOpacity(0.7),

                                borderRadius: BorderRadius.circular(16),

                              ),

                              child: Row(

                                mainAxisAlignment: MainAxisAlignment.spaceBetween,

                                children: [

                                  Flexible(

                                    child: Row(

                                      mainAxisSize: MainAxisSize.min,

                                      children: [

                                        const Icon(Icons.waves, size: 13, color: AppColors.secondaryContainer),

                                        const SizedBox(width: 4),

                                        Flexible(

                                          child: LocalizedText('Sóng: ${_maxWaveController.text}m',

                                            style: const TextStyle(fontSize: 10, color: Colors.white),

                                            overflow: TextOverflow.ellipsis,

                                          ),

                                        ),

                                      ],

                                    ),

                                  ),

                                  const SizedBox(width: 6),

                                  Flexible(

                                    child: Row(

                                      mainAxisSize: MainAxisSize.min,

                                      children: [

                                        const Icon(Icons.air, size: 13, color: AppColors.primaryFixed),

                                        const SizedBox(width: 4),

                                        Flexible(

                                          child: LocalizedText('Gió: ${_minWindController.text} km/h',

                                            style: const TextStyle(fontSize: 10, color: Colors.white),

                                            overflow: TextOverflow.ellipsis,

                                          ),

                                        ),

                                      ],

                                    ),

                                  ),

                                ],

                              ),

                            ),

                          ),

                        ],

                      ),

                    ),

                    Padding(

                      padding: const EdgeInsets.all(12),

                      child: Column(

                        crossAxisAlignment: CrossAxisAlignment.start,

                        children: [

                          Row(

                            children: [

                              Container(

                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),

                                decoration: BoxDecoration(color: AppColors.secondaryFixed, borderRadius: BorderRadius.circular(8)),

                                child: LocalizedText(
                                  widget.service.locationName.isNotEmpty
                                      ? widget.service.locationName.toUpperCase()
                                      : 'ĐIỂM ĐÓN KHÁCH',
                                  style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixed),
                                ),

                              ),

                              const SizedBox(width: 6),

                              LocalizedText('${widget.service.durationMinutes} phút', style: const TextStyle(fontSize: 11, color: AppColors.tertiary)),

                            ],

                          ),

                          const SizedBox(height: 6),

                          LocalizedText(widget.service.nameVi, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface)),

                          const SizedBox(height: 4),

                          LocalizedText(widget.service.descriptionVi, style: const TextStyle(fontSize: 11, color: AppColors.tertiary), maxLines: 2, overflow: TextOverflow.ellipsis),

                          const SizedBox(height: 10),

                          Row(

                            mainAxisAlignment: MainAxisAlignment.spaceBetween,

                            children: [

                              Expanded(

                                child: Column(

                                  crossAxisAlignment: CrossAxisAlignment.start,

                                  children: [

                                    const LocalizedText('Giá niêm yết', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),

                                    LocalizedText(

                                      '${widget.service.price.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')} đ',

                                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary),

                                      maxLines: 1,

                                      overflow: TextOverflow.ellipsis,

                                    ),

                                  ],

                                ),

                              ),

                              const SizedBox(width: 8),

                              Container(

                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),

                                decoration: BoxDecoration(color: AppColors.secondary, borderRadius: BorderRadius.circular(16)),

                                child: const LocalizedText('Đặt ngay', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.white)),

                              ),

                            ],

                          ),

                        ],

                      ),

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // Vendor Policy Reminder

              Container(

                padding: const EdgeInsets.all(12),

                decoration: BoxDecoration(

                  color: AppColors.surfaceContainerHigh,

                  borderRadius: AppShapes.radiusSm,

                ),

                child: const Row(

                  crossAxisAlignment: CrossAxisAlignment.start,

                  children: [

                    Icon(Icons.policy, size: 18, color: AppColors.primary),

                    SizedBox(width: 8),

                    Expanded(

                      child: LocalizedText(

                        'Dịch vụ sau khi gửi sẽ được Ban kiểm duyệt DANASEA xem xét hồ sơ theo quy định trước khi mở bán chính thức.',

                        style: TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),

                      ),

                    ),

                  ],

                ),

              ),

              const SizedBox(height: 16),



              // Action Buttons

              VendorButton(

                text: 'Gửi kiểm duyệt',

                icon: Icons.send_time_extension,

                onPressed: () => _save(ServiceStatus.pendingReview, 'Đã gửi dịch vụ lên Ban kiểm duyệt DANASEA'),

              ),

              const SizedBox(height: 10),

              Row(

                children: [

                  Expanded(

                    child: OutlinedButton.icon(

                      onPressed: () => _save(ServiceStatus.draft, 'Đã lưu dịch vụ dưới dạng bản nháp'),

                      icon: const Icon(Icons.bookmark_border, size: 16),

                      label: const LocalizedText('Lưu bản nháp', style: TextStyle(fontSize: 12)),

                      style: OutlinedButton.styleFrom(

                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),

                        padding: const EdgeInsets.symmetric(vertical: 12),

                      ),

                    ),

                  ),

                  const SizedBox(width: 10),

                  Expanded(

                    child: OutlinedButton.icon(

                      onPressed: () => _save(ServiceStatus.paused, 'Đã tạm dừng nhận khách cho dịch vụ'),

                      icon: const Icon(Icons.pause_circle_outline, size: 16, color: AppColors.primary),

                      label: const LocalizedText('Tạm dừng', style: TextStyle(fontSize: 12, color: AppColors.primary)),

                      style: OutlinedButton.styleFrom(

                        side: const BorderSide(color: AppColors.primary),

                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),

                        padding: const EdgeInsets.symmetric(vertical: 12),

                      ),

                    ),

                  ),

                ],

              ),

              const SizedBox(height: 20),

            ],

          ),

        ),

      ),

    );

  }

}

