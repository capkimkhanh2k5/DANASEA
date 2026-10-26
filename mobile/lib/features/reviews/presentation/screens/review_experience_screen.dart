import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/review_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';

class ReviewExperienceScreen extends StatefulWidget {
  final SubOrderModel subOrder;
  final ReviewModel? existingReview;

  const ReviewExperienceScreen({
    super.key,
    required this.subOrder,
    this.existingReview,
  });

  @override
  State<ReviewExperienceScreen> createState() => _ReviewExperienceScreenState();
}

class _ReviewExperienceScreenState extends State<ReviewExperienceScreen> {
  final ReviewRepository _repository = ReviewRepository();
  ReviewModel? _existingReview;
  int _rating = 5;
  final TextEditingController _commentController = TextEditingController();
  final List<String> _photos = [];
  bool _checkingExisting = false;
  bool _submitting = false;
  String? _error;

  final List<String> _ratingLabels = [
    '',
    'Chưa hài lòng',
    'Tạm được',
    'Khá ổn',
    'Rất tốt và an toàn',
    'Tuyệt vời trên cả mong đợi!',
  ];

  @override
  void initState() {
    super.initState();
    if (widget.existingReview != null) {
      _applyExistingReview(widget.existingReview!);
    } else {
      _checkExistingSubOrderReview();
    }
  }

  void _applyExistingReview(ReviewModel review) {
    _existingReview = review;
    _rating = review.rating.clamp(1, 5);
    _commentController.text = review.comment;
    _photos
      ..clear()
      ..addAll(review.images.take(5));
  }

  Future<void> _checkExistingSubOrderReview() async {
    final serviceId = widget.subOrder.serviceId.trim();
    if (serviceId.isEmpty) return;
    setState(() => _checkingExisting = true);
    try {
      final page = await _repository.getServiceReviews(
        serviceId,
        page: 0,
        size: 50,
      );
      if (!mounted) return;
      for (final rev in page.content) {
        if (rev.subOrderId == widget.subOrder.id) {
          setState(() => _applyExistingReview(rev));
          break;
        }
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _checkingExisting = false);
    }
  }

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _promptAddPhotoUrl() async {
    if (_photos.length >= 5) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Tối đa 5 hình ảnh cho mỗi đánh giá.'),
          backgroundColor: AppColors.primary,
        ),
      );
      return;
    }
    final urlController = TextEditingController(
      text:
          'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80',
    );
    final entered = await showDialog<String>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const LocalizedText('Thêm liên kết ảnh chuyến đi'),
          content: TextField(
            controller: urlController,
            decoration: InputDecoration(
              hintText: tr(context, 'https://...'),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const LocalizedText('Hủy'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.secondary,
              ),
              onPressed: () =>
                  Navigator.pop(dialogContext, urlController.text.trim()),
              child: const LocalizedText(
                'Thêm ảnh',
                style: TextStyle(color: Colors.white),
              ),
            ),
          ],
        );
      },
    );
    if (entered == null || entered.isEmpty || !mounted) return;
    if (!entered.startsWith('http://') && !entered.startsWith('https://')) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Liên kết ảnh phải bắt đầu bằng http:// hoặc https://.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }
    setState(() {
      if (_photos.length < 5) {
        _photos.add(entered);
      }
    });
  }

  Future<void> _submitReview() async {
    if (_submitting) return;
    final comment = _commentController.text.trim();
    if (comment.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng chia sẻ cảm nhận của bạn.'),
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
      ReviewModel saved;
      final existing = _existingReview;
      if (existing != null) {
        if (existing.id.isNotEmpty) {
          saved = await _repository.updateReview(
            reviewId: existing.id,
            rating: _rating,
            comment: comment,
            images: _photos,
          );
        } else {
          saved = await _repository.updateReviewBySubOrder(
            subOrderId: widget.subOrder.id,
            rating: _rating,
            comment: comment,
            images: _photos,
          );
        }
      } else {
        try {
          saved = await _repository.createReview(
            subOrderId: widget.subOrder.id,
            rating: _rating,
            comment: comment,
            images: _photos,
          );
        } on AuthFailure catch (e) {
          if (e.status == 409 ||
              e.payload?['code']?.toString() == 'DUPLICATE_REVIEW') {
            saved = await _repository.updateReviewBySubOrder(
              subOrderId: widget.subOrder.id,
              rating: _rating,
              comment: comment,
              images: _photos,
            );
          } else {
            rethrow;
          }
        }
      }

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            existing != null
                ? 'Đã cập nhật đánh giá trải nghiệm biển!'
                : 'Cảm ơn bạn đã gửi đánh giá trải nghiệm biển!',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      Navigator.pop(context, saved);
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
      const msg = 'Không thể gửi đánh giá. Vui lòng thử lại.';
      setState(() => _error = msg);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(msg),
          backgroundColor: AppColors.error,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final sub = widget.subOrder;
    final isEditing = _existingReview != null;

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
          isEditing ? 'Chỉnh sửa đánh giá' : 'Đánh giá trải nghiệm',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
      ),
      body: _checkingExisting
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // SERVICE HEADER
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceSm),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppShapes.radiusDefault,
                      boxShadow: AppShapes.shadowLevel1,
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Row(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(10),
                          child: SizedBox(
                            width: 64,
                            height: 64,
                            child: sub.serviceImageUrl.isNotEmpty
                                ? Image.network(
                                    sub.serviceImageUrl,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, _, _) => Container(
                                      color: AppColors.surfaceContainerHigh,
                                      child: const Icon(
                                        Icons.sailing,
                                        color: AppColors.secondary,
                                      ),
                                    ),
                                  )
                                : Container(
                                    color: AppColors.surfaceContainerHigh,
                                    child: const Icon(
                                      Icons.sailing,
                                      color: AppColors.secondary,
                                    ),
                                  ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LocalizedText(
                                sub.vendorName,
                                style: AppTypography.labelSm(
                                  color: AppColors.secondary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              LocalizedText(
                                sub.serviceName,
                                style: AppTypography.headlineSm(
                                  color: AppColors.onSurface,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              LocalizedText(
                                'Hoàn thành chuyến đi • #${sub.id}',
                                style: AppTypography.bodySm(
                                  color: AppColors.outline,
                                ).copyWith(fontSize: 11),
                              ),
                            ],
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

                  // STAR RATING SECTION (CORAL STARS ONLY, NO YELLOW)
                  Container(
                    padding: const EdgeInsets.all(AppShapes.spaceMd),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppShapes.radiusDefault,
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Column(
                      children: [
                        LocalizedText(
                          'Bạn thấy trải nghiệm biển thế nào?',
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: List.generate(5, (index) {
                            final starNum = index + 1;
                            return IconButton(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 4,
                                vertical: 6,
                              ),
                              constraints: const BoxConstraints(),
                              iconSize: 32,
                              icon: Icon(
                                starNum <= _rating
                                    ? Icons.star
                                    : Icons.star_border,
                                color: AppColors.starRating,
                              ),
                              onPressed: _submitting
                                  ? null
                                  : () {
                                      setState(() => _rating = starNum);
                                    },
                            );
                          }),
                        ),
                        const SizedBox(height: 4),
                        LocalizedText(
                          _ratingLabels[_rating],
                          style: AppTypography.labelMd(
                            color: AppColors.primary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // COMMENT FIELD
                  LocalizedText(
                    'Cảm nhận thực tế của bạn',
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
                      controller: _commentController,
                      enabled: !_submitting,
                      maxLines: 4,
                      decoration: InputDecoration(
                        hintText: tr(
                          context,
                          'Chia sẻ về chất lượng ván SUP, kính lặn, hướng dẫn viên đồng hành, độ trong của nước biển...',
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

                  // PHOTO ATTACHMENTS
                  LocalizedText(
                    'Hình ảnh chuyến đi (tùy chọn)',
                    style: AppTypography.labelLg(color: AppColors.onSurface),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      InkWell(
                        onTap: _submitting ? null : _promptAddPhotoUrl,
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          width: 72,
                          height: 72,
                          decoration: BoxDecoration(
                            color: AppColors.secondaryContainer.withValues(
                              alpha: 0.3,
                            ),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(
                                Icons.add_a_photo,
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
                      ..._photos.map((p) {
                        return Stack(
                          children: [
                            Container(
                              width: 72,
                              height: 72,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(10),
                                image: DecorationImage(
                                  image: NetworkImage(p),
                                  fit: BoxFit.cover,
                                ),
                              ),
                            ),
                            Positioned(
                              top: 2,
                              right: 2,
                              child: InkWell(
                                onTap: _submitting
                                    ? null
                                    : () => setState(() => _photos.remove(p)),
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
                        ? 'Đang gửi đánh giá...'
                        : (isEditing
                              ? 'Cập nhật đánh giá'
                              : 'Gửi đánh giá trải nghiệm'),
                    variant: AppButtonVariant.primary,
                    width: double.infinity,
                    isLoading: _submitting,
                    onPressed: _submitting ? null : _submitReview,
                  ),
                ],
              ),
            ),
    );
  }
}
