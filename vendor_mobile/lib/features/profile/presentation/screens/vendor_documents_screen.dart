import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/data/vendor_mock_repositories.dart';

class VendorDocumentsScreen extends StatefulWidget {
  const VendorDocumentsScreen({super.key});

  @override
  State<VendorDocumentsScreen> createState() => _VendorDocumentsScreenState();
}

class _VendorDocumentsScreenState extends State<VendorDocumentsScreen> {
  final VendorDocumentRepository _docRepo = VendorDocumentRepository();
  final VendorMockDatabase _db = VendorMockDatabase.instance;
  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    _docRepo.getVendorDocuments();
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _loadDocuments() async {
    await _docRepo.getVendorDocuments();
  }

  void _openUploadBottomSheet(VendorDocType type, String currentFileName) {
    FocusManager.instance.primaryFocus?.unfocus();

    final isSafety = type == VendorDocType.safetyCert;
    final title = isSafety
        ? 'Tải lên Chứng nhận an toàn biển'
        : 'Tải lên Giấy phép đăng ký kinh doanh';

    final sampleFiles = isSafety
        ? [
            {
              'name': 'ChungNhan_AnToanBien_BQL_2024_BanGoc.pdf',
              'size': '2.4 MB',
              'desc': 'Bản scan gốc có mộc đỏ Ban Quản lý Bán đảo Sơn Trà',
            },
            {
              'name': 'ChungNhan_CuuHo_BaoHiemBien_2024.pdf',
              'size': '3.1 MB',
              'desc': 'Hồ sơ năng lực cứu hộ và chứng thư bảo hiểm du khách',
            },
          ]
        : [
            {
              'name': 'GPKD_DanangOceanClub_2024_CapNhat.pdf',
              'size': '2.1 MB',
              'desc': 'Bản scan Giấy phép ĐKKD cấp đổi mới nhất',
            },
            {
              'name': 'GPKD_SoKeHoachDauTu_DaNang_CongChung.pdf',
              'size': '1.9 MB',
              'desc': 'Bản sao y công chứng điện tử hợp lệ',
            },
          ];

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetCtx) {
        String selectedFileName = sampleFiles.first['name']!;
        String selectedFileSize = sampleFiles.first['size']!;
        bool isSubmitting = false;

        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            return Container(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 16,
                bottom: MediaQuery.of(ctx).padding.bottom + 20,
              ),
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      margin: const EdgeInsets.only(bottom: 16),
                      decoration: BoxDecoration(
                        color: const Color(0xFFCBD5E1),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppColors.secondaryContainer.withAlpha(80),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(Icons.upload_file_rounded,
                            color: AppColors.secondary, size: 22),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: AppColors.onSurface,
                              ),
                            ),
                            const Text(
                              'Định dạng hỗ trợ: PDF, JPG, PNG (Dưới 10MB)',
                              style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  const Text(
                    'Chọn tệp tài liệu thay thế:',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                  const SizedBox(height: 8),

                  ...sampleFiles.map((file) {
                    final isChosen = selectedFileName == file['name'];
                    return GestureDetector(
                      onTap: () {
                        setSheetState(() {
                          selectedFileName = file['name']!;
                          selectedFileSize = file['size']!;
                        });
                      },
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: isChosen ? AppColors.secondary.withAlpha(12) : const Color(0xFFF8FAFC),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: isChosen ? AppColors.secondary : const Color(0xFFE2E8F0),
                            width: isChosen ? 1.5 : 1,
                          ),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              isChosen ? Icons.radio_button_checked : Icons.radio_button_off,
                              color: isChosen ? AppColors.secondary : const Color(0xFF94A3B8),
                              size: 18,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    file['name']!,
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.bold,
                                      color: isChosen ? AppColors.secondary : AppColors.onSurface,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${file['size']} • ${file['desc']}',
                                    style: const TextStyle(fontSize: 10.5, color: Color(0xFF64748B)),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  }),

                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFFBEB),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFFDE68A)),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: const [
                        Icon(Icons.info_outline, size: 16, color: Color(0xFFD97706)),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Sau khi tải lên thành công, hồ sơ sẽ chuyển sang trạng thái "Đang chờ duyệt" để Admin DANASEA kiểm tra và kích hoạt.',
                            style: TextStyle(fontSize: 11, color: Color(0xFF92400E), height: 1.35),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),

                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: isSubmitting
                          ? null
                          : () async {
                              setSheetState(() => isSubmitting = true);
                              await _docRepo.uploadOrReplaceDocument(
                                docType: type,
                                fileName: selectedFileName,
                                fileSize: selectedFileSize,
                              );
                              if (!sheetCtx.mounted) return;
                              Navigator.pop(sheetCtx);
                              if (!mounted) return;
                              ToastNotification.show(
                                context,
                                message: 'Đã gửi tài liệu thành công! Hồ sơ đang ở trạng thái Chờ duyệt bởi Admin DANASEA.',
                                type: ToastType.success,
                              );
                            },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.secondary,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: isSubmitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                            )
                          : const Text(
                              'Xác nhận gửi hồ sơ xét duyệt',
                              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                            ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _openDocumentViewer(VendorDocumentModel doc, String title) {
    FocusManager.instance.primaryFocus?.unfocus();

    final isSafety = doc.docType == VendorDocType.safetyCert;
    final authority = isSafety
        ? 'Ban Quản lý Bán đảo Sơn Trà và các bãi biển du lịch Đà Nẵng'
        : 'Sở Kế hoạch và Đầu tư Thành phố Đà Nẵng';
    final docNumber = isSafety ? 'GCN-ATB: 2024/BQL-SHT' : 'Số ĐKKD: 0401829482';

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetCtx) => Container(
        height: MediaQuery.of(sheetCtx).size.height * 0.85,
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 16,
          bottom: MediaQuery.of(sheetCtx).padding.bottom + 16,
        ),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: const Color(0xFFCBD5E1),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                StatusBadge(status: doc.status.toBadgeStatus()),
              ],
            ),
            const SizedBox(height: 12),

            // Metadata card
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Column(
                children: [
                  _buildViewerMetaRow('Mã hồ sơ:', docNumber),
                  const SizedBox(height: 6),
                  _buildViewerMetaRow('Cơ quan cấp:', authority),
                  const SizedBox(height: 6),
                  _buildViewerMetaRow(
                    'Ngày tải lên:',
                    '${doc.uploadedAt.day.toString().padLeft(2, '0')}/${doc.uploadedAt.month.toString().padLeft(2, '0')}/${doc.uploadedAt.year}',
                  ),
                  const SizedBox(height: 6),
                  _buildViewerMetaRow('Tệp tin số:', '${doc.fileName} (${doc.fileSize})'),
                ],
              ),
            ),
            const SizedBox(height: 14),

            // Electronic verified certificate preview container
            Expanded(
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFCBD5E1)),
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: const BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: Color(0x10000000),
                            blurRadius: 8,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: const Icon(
                        Icons.picture_as_pdf_rounded,
                        color: AppColors.primary,
                        size: 44,
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      doc.fileName,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'BẢN SAO HỢP THỨC HÓA ĐIỆN TỬ',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                        letterSpacing: 0.6,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'DANASEA CLOUD VERIFIED ELECTRONIC COPY',
                      style: TextStyle(fontSize: 10, color: Color(0xFF64748B)),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: const Color(0xFFE2E8F0),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: const [
                          Icon(Icons.verified, size: 14, color: AppColors.secondary),
                          SizedBox(width: 5),
                          Text(
                            'Chữ ký số hợp lệ',
                            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.secondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),

            // Download CTA
            SizedBox(
              width: double.infinity,
              height: 46,
              child: ElevatedButton.icon(
                onPressed: () {
                  ToastNotification.show(
                    context,
                    message: 'Đang tải xuống ${doc.fileName}... Tệp đã được lưu vào thư mục Downloads.',
                    type: ToastType.success,
                  );
                },
                icon: const Icon(Icons.cloud_download_outlined, size: 18),
                label: const Text('Tải bản sao về máy', style: TextStyle(fontWeight: FontWeight.bold)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.secondary,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildViewerMetaRow(String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 100,
          child: Text(
            label,
            style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.onSurface),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final docs = _db.vendorDocuments;

    final businessLicense = docs.firstWhere(
      (d) => d.docType == VendorDocType.businessLicense,
      orElse: () => VendorDocumentModel(
        id: 'vdoc-001',
        vendorId: _db.currentVendor.id,
        docType: VendorDocType.businessLicense,
        fileUrl: 'GPKD_DanangOceanClub_2023.pdf',
        status: DocumentReviewStatus.approved,
        fileName: 'GPKD_DanangOceanClub_2023.pdf',
        fileSize: '1.8 MB',
        uploadedAt: DateTime(2023, 3, 15),
      ),
    );

    final safetyCert = docs.firstWhere(
      (d) => d.docType == VendorDocType.safetyCert,
      orElse: () => VendorDocumentModel(
        id: 'vdoc-002',
        vendorId: _db.currentVendor.id,
        docType: VendorDocType.safetyCert,
        fileUrl: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        status: DocumentReviewStatus.approved,
        fileName: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        fileSize: '2.4 MB',
        uploadedAt: DateTime(2024, 1, 12),
      ),
    );

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F9),
      appBar: AppBar(
        title: const Text(
          'Giấy tờ đối tác',
          style: TextStyle(
            color: AppColors.onSurface,
            fontWeight: FontWeight.bold,
            fontSize: 18,
          ),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 1,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () {
            FocusManager.instance.primaryFocus?.unfocus();
            Navigator.maybePop(context);
          },
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _loadDocuments,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top Verification Summary Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF006874), Color(0xFF004D56)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(18),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x1A006874),
                      blurRadius: 10,
                      offset: Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: const [
                            Icon(Icons.verified_user, color: AppColors.secondaryFixed, size: 18),
                            SizedBox(width: 6),
                            Text(
                              'HỒ SƠ PHÁP LÝ ĐỐI TÁC',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                                letterSpacing: 0.6,
                              ),
                            ),
                          ],
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: Colors.white.withAlpha(40),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Text(
                            '2 Hạng mục',
                            style: TextStyle(fontSize: 10.5, color: Colors.white, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'Giấy tờ & Chứng nhận an toàn',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${_db.currentVendor.businessName} cam kết đảm bảo đầy đủ tư cách pháp nhân và an toàn theo tiêu chuẩn thể thao du lịch biển DANASEA.',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.white.withAlpha(220),
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Item 1: Business License Card
              _buildConnectedDocCard(
                title: 'Giấy phép đăng ký kinh doanh',
                subtitle: 'Loại: Giấy tờ pháp lý doanh nghiệp',
                icon: Icons.policy_rounded,
                doc: businessLicense,
                onView: () => _openDocumentViewer(
                  businessLicense,
                  'Giấy phép kinh doanh',
                ),
                onUploadNew: () => _openUploadBottomSheet(
                  VendorDocType.businessLicense,
                  businessLicense.fileName,
                ),
              ),
              const SizedBox(height: 14),

              // Item 2: Safety Certificate Card
              _buildConnectedDocCard(
                title: 'Chứng nhận an toàn biển & Cứu hộ',
                subtitle: 'Loại: Chứng nhận tiêu chuẩn thể thao biển',
                icon: Icons.scuba_diving_rounded,
                doc: safetyCert,
                description:
                    'Chứng nhận an toàn thể thao biển và năng lực cứu hộ cứu nạn cấp bởi Ban quản lý bán đảo Sơn Trà và các bãi biển du lịch Đà Nẵng.',
                onView: () => _openDocumentViewer(
                  safetyCert,
                  'Chứng nhận an toàn biển',
                ),
                onUploadNew: () => _openUploadBottomSheet(
                  VendorDocType.safetyCert,
                  safetyCert.fileName,
                ),
              ),
              const SizedBox(height: 16),

              // Process Notice Box
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFECEFF1)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Icon(Icons.info_outline_rounded, color: AppColors.secondary, size: 20),
                    SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Quy trình kiểm duyệt giấy tờ',
                            style: TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.bold,
                              color: AppColors.onSurface,
                            ),
                          ),
                          SizedBox(height: 4),
                          Text(
                            'Giấy tờ sau khi được gửi lại sẽ chuyển sang trạng thái "Đang chờ duyệt". Admin hệ thống DANASEA sẽ thẩm định và phản hồi kết quả trong vòng 24h - 48h làm việc.',
                            style: TextStyle(
                              fontSize: 11.5,
                              color: Color(0xFF64748B),
                              height: 1.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 36),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildConnectedDocCard({
    required String title,
    required String subtitle,
    required IconData icon,
    required VendorDocumentModel doc,
    String? description,
    required VoidCallback onView,
    required VoidCallback onUploadNew,
  }) {
    String statusNote;
    Color statusNoteColor;
    Color statusNoteBg;
    IconData statusNoteIcon;

    switch (doc.status) {
      case DocumentReviewStatus.approved:
        statusNote = 'Giấy tờ hợp lệ và đã được Admin DANASEA phê duyệt chính thức.';
        statusNoteColor = const Color(0xFF059669);
        statusNoteBg = const Color(0xFFECFDF5);
        statusNoteIcon = Icons.check_circle_outline_rounded;
        break;
      case DocumentReviewStatus.pending:
        statusNote = 'Hồ sơ đang trong quá trình thẩm định. Thời gian xử lý: 24h - 48h làm việc.';
        statusNoteColor = const Color(0xFFD97706);
        statusNoteBg = const Color(0xFFFFFBEB);
        statusNoteIcon = Icons.schedule_rounded;
        break;
      case DocumentReviewStatus.rejected:
        statusNote = 'Giấy tờ bị từ chối do mờ hoặc hết hạn. Vui lòng tải lại bản công chứng mới.';
        statusNoteColor = AppColors.primary;
        statusNoteBg = const Color(0xFFFEF2F2);
        statusNoteIcon = Icons.error_outline_rounded;
        break;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFECEFF1)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06000000),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withAlpha(50),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: AppColors.secondary, size: 22),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 14.5,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              StatusBadge(status: doc.status.toBadgeStatus()),
            ],
          ),

          if (description != null) ...[
            const SizedBox(height: 8),
            Text(
              description,
              style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B), height: 1.35),
            ),
          ],
          const SizedBox(height: 12),

          // File Capsule Box
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Row(
              children: [
                Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer.withAlpha(40),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.picture_as_pdf_rounded, color: AppColors.primary, size: 18),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        doc.fileName,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${doc.fileSize} • Tải lên: ${doc.uploadedAt.day.toString().padLeft(2, '0')}/${doc.uploadedAt.month.toString().padLeft(2, '0')}/${doc.uploadedAt.year}',
                        style: const TextStyle(fontSize: 10.5, color: Color(0xFF64748B)),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.cloud_download_outlined, color: AppColors.secondary, size: 20),
                  tooltip: 'Tải về máy',
                  onPressed: () {
                    ToastNotification.show(
                      context,
                      message: 'Đang tải xuống ${doc.fileName}...',
                      type: ToastType.success,
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),

          // Real Status Note Pill
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            decoration: BoxDecoration(
              color: statusNoteBg,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(statusNoteIcon, size: 14, color: statusNoteColor),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    statusNote,
                    style: TextStyle(
                      fontSize: 11,
                      color: statusNoteColor,
                      fontWeight: FontWeight.w500,
                      height: 1.3,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Actions Buttons: View & Upload New
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: onView,
                  icon: const Icon(Icons.visibility_outlined, size: 16),
                  label: const Text('Xem tài liệu', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.secondary,
                    side: const BorderSide(color: Color(0xFFCBD5E1)),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: onUploadNew,
                  icon: const Icon(Icons.file_upload_outlined, size: 16),
                  label: const Text('Tải lại file mới', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: doc.status == DocumentReviewStatus.rejected
                        ? AppColors.primary
                        : AppColors.secondary,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    padding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
