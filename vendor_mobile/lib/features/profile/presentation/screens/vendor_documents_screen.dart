import '../../../../core/widgets/toast_notification.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';

class VendorDocumentsScreen extends StatefulWidget {
  const VendorDocumentsScreen({super.key});

  @override
  State<VendorDocumentsScreen> createState() => _VendorDocumentsScreenState();
}

class _VendorDocumentsScreenState extends State<VendorDocumentsScreen> {
  final VendorDocumentRepository _docRepo = VendorDocumentRepository();
  final VendorStore _db = VendorStore.instance;
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

  Future<void> _openUploadBottomSheet(VendorDocType type, String currentFileName) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: Colors.white,
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
                  'Tải lên ${type.labelVi} (Cloud)',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined, color: AppColors.secondary),
                title: const Text('Chọn ảnh giấy tờ từ Thư viện'),
                subtitle: const Text('Tải trực tiếp lên hệ thống lưu trữ đám mây Cloudinary', style: TextStyle(fontSize: 11)),
                onTap: () => Navigator.pop(ctx, ImageSource.gallery),
              ),
              ListTile(
                leading: const Icon(Icons.camera_alt_outlined, color: AppColors.primary),
                title: const Text('Chụp ảnh giấy tờ từ Camera'),
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
      final bytesLen = await picked.length();
      final sizeKb = (bytesLen / 1024).toStringAsFixed(0);
      await _docRepo.uploadOrReplaceDocument(
        docType: type,
        fileName: picked.name,
        fileSize: '$sizeKb KB',
        filePath: picked.path,
      );
      await _loadDocuments();
      if (!mounted) return;
      ToastNotification.showSuccess(context, 'Đã tải giấy tờ lên Cloud thành công (Chờ thẩm định)');
    } catch (e) {
      if (!mounted) return;
      ToastNotification.showError(context, 'Lỗi tải giấy tờ lên Cloud: $e');
    }
  }

  void _openDocumentViewer(VendorDocumentModel doc, String title) {
    showDialog<void>(context: context, builder: (context) => AlertDialog(
      title: Text(title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (doc.fileUrl.startsWith('http://') || doc.fileUrl.startsWith('https://')) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                doc.fileUrl,
                height: 180,
                width: double.infinity,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const SizedBox.shrink(),
              ),
            ),
            const SizedBox(height: 8),
          ],
          SelectableText(doc.fileUrl.isEmpty ? 'Chưa có đường dẫn tài liệu trên máy chủ.' : doc.fileUrl),
        ],
      ),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Đóng'))],
    ));
  }

  @override
  Widget build(BuildContext context) {
    final docs = _db.vendorDocuments;

    final businessLicense = docs.firstWhere(
      (d) => d.docType == VendorDocType.businessLicense,
      orElse: () => VendorDocumentModel(
        id: '',
        vendorId: _db.currentVendor.id,
        docType: VendorDocType.businessLicense,
        fileUrl: '',
        status: DocumentReviewStatus.pending,
        fileName: 'Chưa tải lên giấy phép kinh doanh',
        fileSize: '0 KB',
        uploadedAt: DateTime.now(),
      ),
    );

    final safetyCert = docs.firstWhere(
      (d) => d.docType == VendorDocType.safetyCert,
      orElse: () => VendorDocumentModel(
        id: '',
        vendorId: _db.currentVendor.id,
        docType: VendorDocType.safetyCert,
        fileUrl: '',
        status: DocumentReviewStatus.pending,
        fileName: 'Chưa tải lên chứng nhận an toàn biển',
        fileSize: '0 KB',
        uploadedAt: DateTime.now(),
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
