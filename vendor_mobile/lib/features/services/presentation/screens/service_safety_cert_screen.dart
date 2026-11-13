import 'package:vendor_mobile/core/l10n/app_localizations.dart';

import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';

import '../../../../core/theme/app_shapes.dart';

import '../../../../core/data/vendor_store.dart';

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

  }



  @override

  void dispose() {

    _minWindController.dispose();

    _maxWaveController.dispose();

    _waiverController.dispose();

    super.dispose();

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



  void _save(ServiceStatus targetStatus, String successMsg) {

    final updated = _updateModel(targetStatus);

    _db.saveService(updated);

    ToastNotification.showSuccess(context, successMsg);

    Navigator.of(context).popUntil((route) => route.isFirst);

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

          padding: const EdgeInsets.all(16.0),

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

                          onPressed: () {

                            _db.addServiceSafetyDoc(

                              widget.service.serviceId,

                              'Chứng chỉ Cứu hộ đường nước cấp mới',

                              'https://example.com/safety_cert_new.pdf',

                            );

                            ToastNotification.showSuccess(context, 'Đã tải lên chứng chỉ mới (Đang chờ duyệt)');

                          },

                          icon: const Icon(Icons.upload_file, size: 14),

                          label: const LocalizedText('Tải lên', style: TextStyle(fontSize: 11)),

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

                    const LocalizedText('Các chứng chỉ an toàn, phao cứu sinh và sơ cấp cứu gắn riêng cho dịch vụ này.',

                        style: TextStyle(fontSize: 11, color: AppColors.tertiary)),

                    const SizedBox(height: 12),

                    if (safetyDocs.isEmpty)

                      Container(

                        padding: const EdgeInsets.all(12),

                        decoration: BoxDecoration(

                          color: AppColors.surfaceContainerLow,

                          borderRadius: BorderRadius.circular(8),

                        ),

                        child: const Center(

                          child: LocalizedText('Chưa có chứng chỉ an toàn riêng cho dịch vụ này', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),

                        ),

                      )

                    else

                      ListView.separated(

                        shrinkWrap: true,

                        physics: const NeverScrollableScrollPhysics(),

                        itemCount: safetyDocs.length,

                        separatorBuilder: (_, __) => const SizedBox(height: 8),

                        itemBuilder: (context, idx) {

                          final doc = safetyDocs[idx];

                          return Container(

                            padding: const EdgeInsets.all(10),

                            decoration: BoxDecoration(

                              color: AppColors.surfaceContainerLow,

                              borderRadius: BorderRadius.circular(10),

                            ),

                            child: Row(

                              children: [

                                const Icon(Icons.picture_as_pdf, color: AppColors.primary, size: 24),

                                const SizedBox(width: 10),

                                Expanded(

                                  child: Column(

                                    crossAxisAlignment: CrossAxisAlignment.start,

                                    children: [

                                      LocalizedText(doc.documentName, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface)),

                                      LocalizedText('ID: ${doc.docId} • Cập nhật: ${doc.uploadedAt.day}/${doc.uploadedAt.month}',

                                          style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),

                                    ],

                                  ),

                                ),

                                StatusBadge(status: doc.status),

                              ],

                            ),

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

                                child: const LocalizedText('BÃI BIỂN MỸ KHÊ', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onSecondaryFixed)),

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

