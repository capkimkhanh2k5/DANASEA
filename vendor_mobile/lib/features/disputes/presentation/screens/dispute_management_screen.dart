import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../orders/presentation/screens/sub_order_detail_screen.dart';

class DisputeManagementScreen extends StatefulWidget {
  const DisputeManagementScreen({super.key});

  @override
  State<DisputeManagementScreen> createState() => _DisputeManagementScreenState();
}

class _DisputeManagementScreenState extends State<DisputeManagementScreen> {
  final VendorDisputeRepository _disputeRepo = VendorDisputeRepository();
  final VendorStore _db = VendorStore.instance;

  String _selectedStatus = 'ALL'; // ALL, OPEN, IN_REVIEW, RESOLVED, REJECTED

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  void _showAddEvidenceDialog(DisputeModel dispute) {
    final noteController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rXl)),
        title: Row(
          children: [
            const Icon(Icons.upload_file, color: AppColors.secondary, size: 22),
            const SizedBox(width: 8),
            Expanded(
              child: LocalizedText(
                'Nộp minh chứng giải trình',
                style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText(
              'Khiếu nại #${dispute.id} (Đơn #${dispute.subOrderId})',
              style: AppTypography.labelMd.copyWith(color: AppColors.secondary),
            ),
            const SizedBox(height: 12),
            LocalizedText(
              'Nội dung biên bản / giải trình:',
              style: AppTypography.labelSm.copyWith(color: AppColors.tertiary),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: noteController,
              maxLines: 3,
              decoration: InputDecoration(
                hintText: tr(context, 'Nhập thông tin xác nhận bến bãi, camera an ninh...'),
                hintStyle: AppTypography.bodySm.copyWith(color: AppColors.outline),
                filled: true,
                fillColor: AppColors.surfaceContainerLow,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(AppShapes.rMd),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () {
                ToastNotification.show(
                  context,
                  message: 'Đã đính kèm ảnh chụp camera an ninh bến bãi.',
                  type: ToastType.info,
                );
              },
              icon: const Icon(Icons.add_photo_alternate, size: 16),
              label: const LocalizedText('Đính kèm ảnh hiện trường (Tùy chọn)'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.secondary,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const LocalizedText('Hủy'),
          ),
          ElevatedButton(
            onPressed: () async {
              final note = noteController.text.trim();
              if (note.isEmpty) {
                ToastNotification.show(context, message: 'Vui lòng nhập nội dung giải trình.', type: ToastType.error);
                return;
              }
              await _disputeRepo.submitDisputeEvidence(
                dispute.id,
                'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=300&auto=format&fit=crop&q=80',
              );
              if (!mounted) return;
              Navigator.pop(ctx);
              ToastNotification.show(
                context,
                message: 'Đã gửi minh chứng giải trình tới thẩm định viên DANASEA.',
                type: ToastType.success,
              );
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.secondary,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
            ),
            child: const LocalizedText('Gửi giải trình'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final allDisputes = _db.disputes;

    List<DisputeModel> filtered = allDisputes;
    if (_selectedStatus == 'OPEN') {
      filtered = allDisputes.where((d) => d.status == DisputeStatus.open).toList();
    } else if (_selectedStatus == 'IN_REVIEW') {
      filtered = allDisputes.where((d) => d.status == DisputeStatus.inReview).toList();
    } else if (_selectedStatus == 'RESOLVED') {
      filtered = allDisputes.where((d) => d.status == DisputeStatus.resolved).toList();
    } else if (_selectedStatus == 'REJECTED') {
      filtered = allDisputes.where((d) => d.status == DisputeStatus.rejected).toList();
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const LocalizedText('Khiếu nại dịch vụ'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.maybePop(context),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Horizontal Filter Tabs
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterTab('ALL', 'Tất cả (${allDisputes.length})'),
                  const SizedBox(width: 8),
                  _buildFilterTab('IN_REVIEW', 'Đang xử lý (${allDisputes.where((d) => d.status == DisputeStatus.inReview).length})'),
                  const SizedBox(width: 8),
                  _buildFilterTab('RESOLVED', 'Đã giải quyết (${allDisputes.where((d) => d.status == DisputeStatus.resolved).length})'),
                  const SizedBox(width: 8),
                  _buildFilterTab('OPEN', 'Chờ xử lý (${allDisputes.where((d) => d.status == DisputeStatus.open).length})'),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Dispute Cards List
            if (filtered.isEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(32),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(AppShapes.rLg),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.check_circle_outline, size: 48, color: AppColors.secondary),
                    const SizedBox(height: 12),
                    LocalizedText(
                      'Không có khiếu nại nào trong mục này.',
                      style: AppTypography.bodyMd.copyWith(color: AppColors.tertiary),
                    ),
                  ],
                ),
              ),
            ] else ...[
              ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: filtered.length,
                separatorBuilder: (_, __) => const SizedBox(height: 14),
                itemBuilder: (context, index) {
                  final dispute = filtered[index];
                  return _buildDisputeCard(dispute);
                },
              ),
            ],
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  Widget _buildFilterTab(String id, String label) {
    final isSelected = _selectedStatus == id;
    return GestureDetector(
      onTap: () => setState(() => _selectedStatus = id),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(AppShapes.rFull),
          boxShadow: isSelected
              ? const [BoxShadow(color: Color(0x14000000), blurRadius: 4, offset: Offset(0, 1))]
              : null,
        ),
        child: LocalizedText(
          label,
          style: AppTypography.labelMd.copyWith(
            color: isSelected ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  Widget _buildDisputeCard(DisputeModel dispute) {
    final isResolved = dispute.status == DisputeStatus.resolved;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppShapes.rLg),
        boxShadow: const [
          BoxShadow(color: Color(0x08000000), blurRadius: 6, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header Bar
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 6,
                      children: [
                        LocalizedText(
                          '#${dispute.id}',
                          style: AppTypography.labelLg.copyWith(
                            color: AppColors.onSurface,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppColors.secondaryFixed.withAlpha(80),
                            borderRadius: BorderRadius.circular(AppShapes.rFull),
                          ),
                          child: LocalizedText(
                            'Đơn #${dispute.subOrderId}',
                            style: AppTypography.labelSm.copyWith(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    LocalizedText(
                      dispute.serviceName,
                      style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              StatusBadge(status: dispute.status.toBadgeStatus()),
            ],
          ),
          const SizedBox(height: 12),

          // Meta Grid
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(AppShapes.rMd),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText('Khách hàng khiếu nại', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
                      const SizedBox(height: 2),
                      Row(
                        children: [
                          const Icon(Icons.person_outline, size: 14, color: AppColors.secondary),
                          const SizedBox(width: 4),
                          Expanded(
                            child: LocalizedText(
                              dispute.customerName,
                              style: AppTypography.labelMd.copyWith(color: AppColors.onSurface, fontWeight: FontWeight.bold),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText('Phân loại tranh chấp', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
                      const SizedBox(height: 2),
                      LocalizedText(
                        dispute.category,
                        style: AppTypography.labelMd.copyWith(color: AppColors.primary, fontWeight: FontWeight.bold),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Customer Description Block
          LocalizedText('Nội dung khiếu nại từ khách:', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
          const SizedBox(height: 4),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow.withAlpha(160),
              borderRadius: BorderRadius.circular(AppShapes.rMd),
            ),
            child: LocalizedText(
              '“${dispute.description}”',
              style: AppTypography.bodySm.copyWith(
                color: AppColors.onSurface,
                fontStyle: FontStyle.italic,
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Customer Evidence Images
          if (dispute.customerAttachments.isNotEmpty) ...[
            LocalizedText('Minh chứng khách hàng gửi:', style: AppTypography.labelSm.copyWith(color: AppColors.tertiary)),
            const SizedBox(height: 6),
            SizedBox(
              height: 100,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: dispute.customerAttachments.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, idx) {
                  return ClipRRect(
                    borderRadius: BorderRadius.circular(AppShapes.rMd),
                    child: Image.network(
                      dispute.customerAttachments[idx],
                      width: 140,
                      height: 100,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        width: 140,
                        height: 100,
                        color: AppColors.surfaceContainerLow,
                        child: const Icon(Icons.image, color: AppColors.tertiary),
                      ),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Resolution Note by Admin (if resolved)
          if (isResolved && dispute.resolutionNote != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.secondaryContainer.withAlpha(40),
                borderRadius: BorderRadius.circular(AppShapes.rMd),
                border: Border(left: BorderSide(color: AppColors.secondary, width: 3)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.verified, size: 16, color: AppColors.secondary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: LocalizedText(
                          'Kết luận từ ${dispute.resolvedBy ?? "Ban Quản Trị DANASEA"}',
                          style: AppTypography.labelSm.copyWith(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.bold,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  LocalizedText(
                    dispute.resolutionNote!,
                    style: AppTypography.bodySm.copyWith(color: AppColors.onSurface),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],



          // Actions
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {
                    final subOrder = _db.subOrders.firstWhere(
                      (o) => o.subOrderId == dispute.subOrderId || o.subOrderCode == dispute.subOrderId,
                      orElse: () => _db.subOrders.first,
                    );
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => SubOrderDetailScreen(subOrder: subOrder),
                      ),
                    );
                  },
                  icon: const Icon(Icons.receipt_long, size: 15),
                  label: const LocalizedText('Xem đơn liên quan', style: TextStyle(fontSize: 12)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.secondary,
                    side: const BorderSide(color: AppColors.secondary),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
              if (!isResolved) ...[
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: () => _showAddEvidenceDialog(dispute),
                    icon: const Icon(Icons.upload_file, size: 15),
                    label: const LocalizedText('Gửi minh chứng', style: TextStyle(fontSize: 12)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondaryFixed,
                      foregroundColor: AppColors.onSecondaryFixed,
                      elevation: 0,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppShapes.rFull)),
                      padding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
