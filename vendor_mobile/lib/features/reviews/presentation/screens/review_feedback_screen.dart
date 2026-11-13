import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';

class ReviewFeedbackScreen extends StatefulWidget {
  const ReviewFeedbackScreen({super.key});

  @override
  State<ReviewFeedbackScreen> createState() => _ReviewFeedbackScreenState();
}

class _ReviewFeedbackScreenState extends State<ReviewFeedbackScreen> {
  final VendorReviewRepository _reviewRepo = VendorReviewRepository();
  final VendorStore _db = VendorStore.instance;

  String _currentFilter = 'all'; // all, pending, 5star, 4star, flagged
  final Map<String, TextEditingController> _replyControllers = {};
  String? _editingReplyReviewId;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    for (final ctrl in _replyControllers.values) {
      ctrl.dispose();
    }
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  TextEditingController _getReplyController(String reviewId, String? initialText) {
    if (!_replyControllers.containsKey(reviewId)) {
      _replyControllers[reviewId] = TextEditingController(text: initialText ?? '');
    }
    return _replyControllers[reviewId]!;
  }

  Future<void> _submitReply(String reviewId) async {
    final text = _replyControllers[reviewId]?.text.trim() ?? '';
    if (text.isEmpty) {
      ToastNotification.show(context, message: 'Vui lòng nhập nội dung phản hồi.', type: ToastType.error);
      return;
    }

    try {
      await _reviewRepo.replyToReview(reviewId, text);
      if (!mounted) return;
      setState(() {
        _editingReplyReviewId = null;
      });
      ToastNotification.show(context, message: 'Đã gửi phản hồi tới khách hàng thành công!', type: ToastType.success);
    } catch (e) {
      if (!mounted) return;
      ToastNotification.show(context, message: 'Lỗi gửi phản hồi: $e', type: ToastType.error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final allReviews = _db.reviews;
    final pendingCount = allReviews.where((r) => r.vendorReply == null).length;
    final flaggedCount = allReviews.where((r) => r.isFlagged).length;

    List<ReviewModel> filteredReviews = allReviews;
    if (_currentFilter == 'pending') {
      filteredReviews = allReviews.where((r) => r.vendorReply == null).toList();
    } else if (_currentFilter == '5star') {
      filteredReviews = allReviews.where((r) => r.rating == 5).toList();
    } else if (_currentFilter == '4star') {
      filteredReviews = allReviews.where((r) => r.rating == 4).toList();
    } else if (_currentFilter == 'flagged') {
      filteredReviews = allReviews.where((r) => r.isFlagged).toList();
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const LocalizedText('Đánh giá & Phản hồi'),
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
            // Dynamic Notification Banner: Pending Action
            if (pendingCount > 0) ...[
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(AppShapes.rLg),
                  boxShadow: const [
                    BoxShadow(color: Color(0x0A000000), blurRadius: 6, offset: Offset(0, 2)),
                  ],
                ),
                child: Row(
                  children: [
                    Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: AppColors.primaryContainer.withAlpha(35),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.reply_all, color: AppColors.primary, size: 22),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            '$pendingCount phản hồi đang chờ',
                            style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
                          ),
                          LocalizedText(
                            'Phản hồi đánh giá của khách hàng kịp thời để nâng cao uy tín',
                            style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.primaryContainer,
                        borderRadius: BorderRadius.circular(AppShapes.rFull),
                      ),
                      child: LocalizedText(
                        'Ưu tiên cao',
                        style: AppTypography.labelSm.copyWith(
                          color: AppColors.onPrimary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Performance Metrics Hub
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(AppShapes.rXl),
                boxShadow: const [
                  BoxShadow(color: Color(0x0A000000), blurRadius: 10, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            LocalizedText(
                              'DANANG OCEAN CLUB',
                              style: AppTypography.labelSm.copyWith(
                                color: AppColors.secondary,
                                letterSpacing: 0.5,
                                fontWeight: FontWeight.bold,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            LocalizedText(
                              'Đánh giá dịch vụ',
                              style: AppTypography.headlineSm.copyWith(color: AppColors.onSurface),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: AppColors.secondaryContainer,
                          borderRadius: BorderRadius.circular(AppShapes.rFull),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.verified, size: 14, color: AppColors.onSecondaryContainer),
                            const SizedBox(width: 4),
                            LocalizedText(
                              '98% Hài lòng',
                              style: AppTypography.labelSm.copyWith(
                                color: AppColors.onSecondaryContainer,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),

                  // Rating Breakdown
                  Row(
                    children: [
                      // Score Column
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(AppShapes.rMd),
                        ),
                        child: Column(
                          children: [
                            LocalizedText(
                              '4.9',
                              style: AppTypography.headlineLgMobile.copyWith(
                                color: AppColors.onSurface,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: List.generate(
                                5,
                                (index) => const Icon(
                                  Icons.star,
                                  size: 16,
                                  color: AppColors.primaryContainer,
                                ),
                              ),
                            ),
                            const SizedBox(height: 4),
                            LocalizedText(
                              '${allReviews.length + 139} lượt',
                              style: AppTypography.labelSm.copyWith(color: AppColors.tertiary),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),

                      // Progress Bars
                      Expanded(
                        child: Column(
                          children: [
                            _buildRatingBar(5, 0.92, 131),
                            const SizedBox(height: 4),
                            _buildRatingBar(4, 0.07, 10),
                            const SizedBox(height: 4),
                            _buildRatingBar(3, 0.01, 1),
                            const SizedBox(height: 4),
                            _buildRatingBar(2, 0.0, 0),
                            const SizedBox(height: 4),
                            _buildRatingBar(1, 0.0, 0),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Horizontal Filter Tabs
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterTab('all', 'Tất cả (${allReviews.length})'),
                  const SizedBox(width: 8),
                  _buildFilterTab('pending', 'Chưa phản hồi', badge: pendingCount.toString()),
                  const SizedBox(width: 8),
                  _buildFilterTab('5star', '5 sao', icon: Icons.star),
                  const SizedBox(width: 8),
                  _buildFilterTab('4star', '4 sao', icon: Icons.star),
                  if (flaggedCount > 0) ...[
                    const SizedBox(width: 8),
                    _buildFilterTab('flagged', 'Bị báo cáo ($flaggedCount)', icon: Icons.flag, isDestructive: true),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Review List
            if (filteredReviews.isEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(32),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(AppShapes.rLg),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.rate_review_outlined, size: 48, color: AppColors.outlineVariant),
                    const SizedBox(height: 12),
                    LocalizedText(
                      'Không có đánh giá nào trong bộ lọc này.',
                      style: AppTypography.bodyMd.copyWith(color: AppColors.tertiary),
                    ),
                  ],
                ),
              ),
            ] else ...[
              ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: filteredReviews.length,
                separatorBuilder: (_, __) => const SizedBox(height: 14),
                itemBuilder: (context, index) {
                  final review = filteredReviews[index];
                  return _buildReviewCard(review);
                },
              ),
            ],
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  Widget _buildRatingBar(int star, double percentage, int count) {
    return Row(
      children: [
        SizedBox(
          width: 14,
          child: LocalizedText(
            '$star',
            style: AppTypography.labelSm.copyWith(color: AppColors.onSurface),
          ),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(AppShapes.rFull),
            child: LinearProgressIndicator(
              value: percentage,
              minHeight: 6,
              backgroundColor: AppColors.surfaceContainerHigh,
              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.primaryContainer),
            ),
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(
          width: 28,
          child: LocalizedText(
            '$count',
            textAlign: TextAlign.right,
            style: AppTypography.labelSm.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ),
      ],
    );
  }

  Widget _buildFilterTab(String id, String label, {String? badge, IconData? icon, bool isDestructive = false}) {
    final isSelected = _currentFilter == id;

    return GestureDetector(
      onTap: () => setState(() => _currentFilter = id),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.secondary : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(AppShapes.rFull),
          boxShadow: const [
            BoxShadow(color: Color(0x0A000000), blurRadius: 4, offset: Offset(0, 1)),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(
                icon,
                size: 15,
                color: isSelected
                    ? AppColors.onSecondary
                    : (isDestructive ? AppColors.error : AppColors.primaryContainer),
              ),
              const SizedBox(width: 4),
            ],
            LocalizedText(
              label,
              style: AppTypography.labelMd.copyWith(
                color: isSelected
                    ? AppColors.onSecondary
                    : (isDestructive ? AppColors.error : AppColors.onSurface),
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
            if (badge != null) ...[
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                decoration: BoxDecoration(
                  color: isSelected ? Colors.white : AppColors.primaryContainer,
                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                ),
                child: LocalizedText(
                  badge,
                  style: AppTypography.labelSm.copyWith(
                    color: isSelected ? AppColors.secondary : AppColors.onPrimary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildReviewCard(ReviewModel review) {
    final hasReply = review.vendorReply != null && review.vendorReply!.isNotEmpty;
    final isEditing = _editingReplyReviewId == review.id;
    final replyController = _getReplyController(review.id, review.vendorReply);

    return Container(
      padding: const EdgeInsets.all(16),
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
          // Header
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: AppColors.secondaryFixed,
                child: LocalizedText(
                  review.customerName.split(' ').last.substring(0, 1).toUpperCase(),
                  style: AppTypography.labelLg.copyWith(
                    color: AppColors.onSecondaryFixed,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LocalizedText(
                      review.customerName,
                      style: AppTypography.labelLg.copyWith(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.bold,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Flexible(
                          child: LocalizedText(
                            '#${review.subOrderId}',
                            style: AppTypography.labelSm.copyWith(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.bold,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 4),
                        Flexible(
                          child: LocalizedText(
                            '• ${review.serviceName}',
                            style: AppTypography.labelSm.copyWith(color: AppColors.tertiary),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: hasReply ? AppColors.surfaceContainer : AppColors.primaryFixed,
                  borderRadius: BorderRadius.circular(AppShapes.rFull),
                ),
                child: LocalizedText(
                  hasReply ? 'Đã trả lời' : 'Chưa trả lời',
                  style: AppTypography.labelSm.copyWith(
                    color: hasReply ? AppColors.secondary : AppColors.onPrimaryFixedVariant,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Rating Stars
          Row(
            children: List.generate(
              5,
              (index) => Icon(
                index < review.rating ? Icons.star : Icons.star_border,
                size: 16,
                color: AppColors.primaryContainer,
              ),
            ),
          ),
          const SizedBox(height: 8),

          // Customer Comment (Strictly Read-only)
          LocalizedText(
            review.comment,
            style: AppTypography.bodyMd.copyWith(
              color: AppColors.onSurface,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 10),

          // Review images if present
          if (review.images.isNotEmpty) ...[
            SizedBox(
              height: 70,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: review.images.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, imgIdx) {
                  return ClipRRect(
                    borderRadius: BorderRadius.circular(AppShapes.rMd),
                    child: Image.network(
                      review.images[imgIdx],
                      width: 90,
                      height: 70,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        width: 90,
                        height: 70,
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

          // Vendor Response Section
          if (hasReply && !isEditing) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(AppShapes.rMd),
                border: Border(left: BorderSide(color: AppColors.secondary, width: 3)),
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
                            const Icon(Icons.reply, size: 14, color: AppColors.secondary),
                            const SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                'Phản hồi từ Danang Ocean Club',
                                style: AppTypography.labelSm.copyWith(
                                  color: AppColors.secondary,
                                  fontWeight: FontWeight.bold,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 6),
                      TextButton.icon(
                        onPressed: () {
                          setState(() {
                            _editingReplyReviewId = review.id;
                            replyController.text = review.vendorReply!;
                          });
                        },
                        icon: const Icon(Icons.edit, size: 14, color: AppColors.secondary),
                        label: LocalizedText(
                          'Sửa',
                          style: AppTypography.labelSm.copyWith(color: AppColors.secondary),
                        ),
                        style: TextButton.styleFrom(
                          padding: EdgeInsets.zero,
                          minimumSize: Size.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  LocalizedText(
                    review.vendorReply!,
                    style: AppTypography.bodySm.copyWith(color: AppColors.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ] else ...[
            // Reply Input Form (either initial reply or editing existing reply)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(AppShapes.rMd),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  LocalizedText(
                    hasReply ? 'Chỉnh sửa phản hồi của bạn:' : 'Viết phản hồi tới khách hàng:',
                    style: AppTypography.labelSm.copyWith(
                      color: AppColors.tertiary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: replyController,
                    maxLines: 3,
                    decoration: InputDecoration(
                      hintText: tr(context, 'Cảm ơn khách hàng và giải đáp thắc mắc nếu có...'),
                      hintStyle: AppTypography.bodySm.copyWith(color: AppColors.outline),
                      filled: true,
                      fillColor: AppColors.surfaceContainerLowest,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppShapes.rMd),
                        borderSide: const BorderSide(color: AppColors.outlineVariant),
                      ),
                      contentPadding: const EdgeInsets.all(10),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (hasReply) ...[
                        TextButton(
                          onPressed: () {
                            setState(() => _editingReplyReviewId = null);
                          },
                          child: const LocalizedText('Hủy'),
                        ),
                        const SizedBox(width: 8),
                      ],
                      ElevatedButton.icon(
                        onPressed: () => _submitReply(review.id),
                        icon: const Icon(Icons.send, size: 15),
                        label: LocalizedText(hasReply ? 'Cập nhật' : 'Gửi phản hồi'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(AppShapes.rFull),
                          ),
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
