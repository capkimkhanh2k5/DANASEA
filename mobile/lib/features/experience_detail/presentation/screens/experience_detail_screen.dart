import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/mock_database_data.dart';
import '../../../../core/data/review_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../widgets/booking_slot_sheet.dart';

class ExperienceDetailScreen extends StatefulWidget {
  final ServiceModel service;
  final VoidCallback? onAddToCart;
  final Future<void> Function()? onToggleFavorite;
  final bool favoriteBusy;
  final VoidCallback? onOpenVendor;
  final VoidCallback? onOpenBooking;
  final Function(ServiceModel service, ServiceSlotModel slot, int guests)?
      onBookNow;

  const ExperienceDetailScreen({
    super.key,
    required this.service,
    this.onAddToCart,
    this.onToggleFavorite,
    this.favoriteBusy = false,
    this.onOpenVendor,
    this.onOpenBooking,
    this.onBookNow,
  });

  @override
  State<ExperienceDetailScreen> createState() => _ExperienceDetailScreenState();
}

class _ExperienceDetailScreenState extends State<ExperienceDetailScreen> {
  final ReviewRepository _reviewRepository = ReviewRepository();
  late bool _isFavorite;
  int _currentImageIndex = 0;
  bool _isDescriptionExpanded = false;
  List<ReviewModel> _reviews = [];
  int _reviewPage = 0;
  int _reviewTotalPages = 1;
  int _reviewTotalElements = 0;
  bool _loadingReviews = true;
  bool _loadingMoreReviews = false;
  bool _reviewActionBusy = false;
  String? _reviewsError;

  @override
  void initState() {
    super.initState();
    _isFavorite = widget.service.isFavorite;
    _reviewTotalElements = widget.service.ratingCount;
    _loadReviews();
  }

  Future<void> _loadReviews({bool loadMore = false}) async {
    if (loadMore) {
      if (_loadingMoreReviews || _reviewPage + 1 >= _reviewTotalPages) return;
      setState(() => _loadingMoreReviews = true);
    } else {
      setState(() {
        _loadingReviews = true;
        _reviewsError = null;
      });
    }
    try {
      final targetPage = loadMore ? _reviewPage + 1 : 0;
      final page = await _reviewRepository.getServiceReviews(
        widget.service.id,
        page: targetPage,
        size: 10,
      );
      if (!mounted) return;
      setState(() {
        if (loadMore) {
          _reviews = [..._reviews, ...page.content];
        } else {
          _reviews = page.content;
        }
        _reviewPage = page.page;
        _reviewTotalPages = page.totalPages;
        _reviewTotalElements = page.totalElements;
      });
    } on AuthFailure catch (e) {
      if (mounted && !loadMore) {
        setState(() => _reviewsError = e.message);
      }
    } catch (_) {
      if (mounted && !loadMore) {
        setState(
          () => _reviewsError =
              'Không thể tải danh sách đánh giá. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _loadingReviews = false;
          _loadingMoreReviews = false;
        });
      }
    }
  }

  Future<void> _openEditReviewDialog(ReviewModel rev) async {
    if (_reviewActionBusy) return;
    int selectedRating = rev.rating;
    final commentCtrl = TextEditingController(text: rev.comment);
    final updated = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (sheetContext, setSheetState) {
            return Padding(
              padding: EdgeInsets.only(
                left: AppShapes.gutterMobile,
                right: AppShapes.gutterMobile,
                top: AppShapes.gutterMobile,
                bottom:
                    MediaQuery.of(sheetContext).viewInsets.bottom +
                    AppShapes.gutterMobile,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          'Chỉnh sửa đánh giá',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.pop(sheetContext, false),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(5, (idx) {
                      final star = idx + 1;
                      return IconButton(
                        icon: Icon(
                          star <= selectedRating
                              ? Icons.star
                              : Icons.star_border,
                          color: AppColors.starRating,
                          size: 28,
                        ),
                        onPressed: () =>
                            setSheetState(() => selectedRating = star),
                      );
                    }),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: TextField(
                      controller: commentCtrl,
                      maxLines: 3,
                      decoration: InputDecoration(
                        hintText: tr(sheetContext, 'Nội dung đánh giá...'),
                        contentPadding: const EdgeInsets.all(10),
                        border: InputBorder.none,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  AppPillButton(
                    label: 'Lưu thay đổi',
                    variant: AppButtonVariant.primary,
                    width: double.infinity,
                    onPressed: () => Navigator.pop(sheetContext, true),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
    if (updated != true || !mounted) return;
    final newComment = commentCtrl.text.trim();
    if (newComment.isEmpty) return;

    setState(() => _reviewActionBusy = true);
    try {
      await _reviewRepository.updateReview(
        reviewId: rev.id,
        rating: selectedRating,
        comment: newComment,
        images: rev.images,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã cập nhật đánh giá trải nghiệm biển!'),
          backgroundColor: AppColors.secondary,
        ),
      );
      _loadReviews();
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
              'Không thể cập nhật đánh giá. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _reviewActionBusy = false);
    }
  }

  Future<void> _openFlagReviewDialog(ReviewModel rev) async {
    if (_reviewActionBusy) return;
    final reasonCtrl = TextEditingController();
    final submitted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const LocalizedText('Báo cáo đánh giá vi phạm'),
          content: TextField(
            controller: reasonCtrl,
            maxLines: 3,
            decoration: InputDecoration(
              hintText: tr(
                dialogContext,
                'Nhập lý do báo cáo (ngôn từ không phù hợp, spam...)',
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const LocalizedText('Hủy'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.error,
              ),
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const LocalizedText(
                'Gửi báo cáo',
                style: TextStyle(color: Colors.white),
              ),
            ),
          ],
        );
      },
    );
    if (submitted != true || !mounted) return;
    final reason = reasonCtrl.text.trim();
    if (reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng nhập lý do báo cáo đánh giá.'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    setState(() => _reviewActionBusy = true);
    try {
      await _reviewRepository.flagReview(reviewId: rev.id, reason: reason);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Đã gửi báo cáo đánh giá cho bộ phận kiểm duyệt.',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      _loadReviews();
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
              'Không thể gửi báo cáo đánh giá. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _reviewActionBusy = false);
    }
  }

  String _formatPrice(int price) {
    final str = price.toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i > 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  void _openBookingSheet() {
    if (widget.onOpenBooking != null) {
      widget.onOpenBooking!();
      return;
    }
    final slots =
        MockDatabaseData.getSlotsForService(widget.service.id, DateTime.now());
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return BookingSlotSheet(
          service: widget.service,
          availableSlots: slots,
          onConfirm: (slot, guests, isInstantCheckout) {
            if (isInstantCheckout) {
              if (widget.onBookNow != null) {
                widget.onBookNow!(widget.service, slot, guests);
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: LocalizedText(
                      'Đặt ${widget.service.name} ($guests khách, ${slot.startTime} - ${slot.endTime}) thành công!',
                    ),
                    backgroundColor: AppColors.secondary,
                  ),
                );
              }
            } else {
              if (widget.onAddToCart != null) {
                widget.onAddToCart!();
              }
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: LocalizedText(
                    'Đã thêm ${widget.service.name} vào giỏ hàng!',
                  ),
                  backgroundColor: AppColors.secondary,
                ),
              );
            }
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final service = widget.service;
    final images = service.imageUrls.isNotEmpty
        ? service.imageUrls
        : [
            'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&auto=format&fit=crop&q=80'
          ];

    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Stack(
        children: [
          // MAIN SCROLL CONTENT
          SingleChildScrollView(
            padding: const EdgeInsets.only(bottom: 100),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. HERO IMAGE CAROUSEL
                Stack(
                  children: [
                    SizedBox(
                      height: 320,
                      width: double.infinity,
                      child: PageView.builder(
                        itemCount: images.length,
                        onPageChanged: (idx) {
                          setState(() => _currentImageIndex = idx);
                        },
                        itemBuilder: (context, index) {
                          return Image.network(
                            images[index],
                            fit: BoxFit.cover,
                            errorBuilder: (context, error, stackTrace) =>
                                Container(
                              color: AppColors.secondaryContainer,
                              child: const Center(
                                child: Icon(Icons.beach_access,
                                    size: 48, color: Colors.white),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                    // Gradient overlay
                    Positioned.fill(
                      child: Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.black.withValues(alpha: 0.4),
                              Colors.transparent,
                              AppColors.surface.withValues(alpha: 0.8),
                              AppColors.surface,
                            ],
                            stops: const [0.0, 0.4, 0.85, 1.0],
                          ),
                        ),
                      ),
                    ),
                    // Image Index Pill
                    Positioned(
                      bottom: 24,
                      right: AppShapes.gutterMobile,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.5),
                          borderRadius: AppShapes.radiusFull,
                        ),
                        child: LocalizedText(
                          '${_currentImageIndex + 1} / ${images.length}',
                          style: AppTypography.labelSm(color: Colors.white),
                        ),
                      ),
                    ),
                  ],
                ),

                // 2. MAIN DETAILS
                Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: AppShapes.gutterMobile),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Badges
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          GestureDetector(
                            onTap: widget.onOpenVendor,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(
                                color: AppColors.secondary,
                                borderRadius: AppShapes.radiusFull,
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.verified,
                                      size: 13, color: AppColors.onSecondary),
                                  const SizedBox(width: 4),
                                  LocalizedText(
                                    service.vendorName,
                                    style: AppTypography.labelSm(
                                      color: AppColors.onSecondary,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerHigh,
                              borderRadius: AppShapes.radiusFull,
                            ),
                            child: LocalizedText(
                              service.categoryName,
                              style: AppTypography.labelSm(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      // Title
                      LocalizedText(
                        service.name,
                        style: AppTypography.headlineLgMobile(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 8),

                      // Rating & Location row
                      Row(
                        children: [
                          const Icon(
                            Icons.star,
                            color: AppColors.starRating,
                            size: 18,
                          ),
                          const SizedBox(width: 4),
                          LocalizedText(
                            service.avgRating.toStringAsFixed(1),
                            style: AppTypography.labelLg(
                              color: AppColors.onSurface,
                            ),
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: LocalizedText(
                              '($_reviewTotalElements đánh giá)',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppTypography.bodySm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.schedule,
                                  size: 16, color: AppColors.tertiary),
                              const SizedBox(width: 4),
                              LocalizedText(
                                '${service.durationMinutes} phút',
                                style: AppTypography.labelMd(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),

                      // Location text
                      Row(
                        children: [
                          const Icon(Icons.location_on,
                              size: 16, color: AppColors.tertiary),
                          const SizedBox(width: 4),
                          Expanded(
                            child: LocalizedText(
                              '${service.locationName} • ${service.address}',
                              style: AppTypography.bodySm(
                                color: AppColors.onSurfaceVariant,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // 3. REAL-TIME OCEAN & SAFETY CARD
                      Container(
                        padding: const EdgeInsets.all(AppShapes.spaceSm),
                        decoration: BoxDecoration(
                          color: AppColors.secondaryContainer
                              .withValues(alpha: 0.35),
                          borderRadius: AppShapes.radiusDefault,
                          border: Border.all(
                            color: AppColors.secondaryContainer,
                            width: 0.8,
                          ),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 36,
                              height: 36,
                              decoration: const BoxDecoration(
                                color: AppColors.secondary,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.waves,
                                  color: AppColors.onSecondary, size: 20),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    'Điều kiện biển hôm nay: Rất tốt',
                                    style: AppTypography.labelMd(
                                      color: AppColors.onSecondaryContainer,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  LocalizedText(
                                    'Sóng 0.4m • Gió 8 km/h • Đủ điều kiện an toàn xuất bến',
                                    style: AppTypography.bodySm(
                                      color: AppColors.onSurfaceVariant,
                                    ).copyWith(fontSize: 12),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 20),

                      // 4. HIGHLIGHTS & INCLUSIONS
                      LocalizedText(
                        'Điểm nổi bật của trải nghiệm',
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 10),
                      _buildInclusionItem(
                        Icons.camera_alt_outlined,
                        'Miễn phí ảnh máy cơ & flycam',
                        'Chụp ảnh chất lượng cao bắt trọn khoảnh khắc bình minh.',
                      ),
                      _buildInclusionItem(
                        Icons.security_outlined,
                        'Trang thiết bị an toàn tiêu chuẩn',
                        'Áo phao đạt chuẩn, dây leash và ván chèo chuyên dụng.',
                      ),
                      _buildInclusionItem(
                        Icons.sports_outlined,
                        'Huấn luyện viên kèm 1:5',
                        'Hỗ trợ kỹ thuật chèo, cứu hộ và đồng hành suốt chuyến.',
                      ),
                      const SizedBox(height: 16),

                      // 5. DESCRIPTION
                      LocalizedText(
                        'Mô tả chi tiết',
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 8),
                      LocalizedText(
                        service.description,
                        style: AppTypography.bodyMd(
                          color: AppColors.onSurface,
                        ),
                        maxLines: _isDescriptionExpanded ? null : 4,
                        overflow: _isDescriptionExpanded
                            ? TextOverflow.visible
                            : TextOverflow.ellipsis,
                      ),
                      InkWell(
                        onTap: () {
                          setState(() {
                            _isDescriptionExpanded = !_isDescriptionExpanded;
                          });
                        },
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: LocalizedText(
                            _isDescriptionExpanded ? 'Thu gọn' : 'Xem thêm',
                            style: AppTypography.labelMd(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),

                      // 6. WAIVER AGREEMENT NOTICE
                      Container(
                        padding: const EdgeInsets.all(AppShapes.spaceSm),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: AppShapes.radiusDefault,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.gavel,
                                    size: 16, color: AppColors.primary),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: LocalizedText(
                                    'Cam kết miễn trừ trách nhiệm',
                                    style: AppTypography.labelMd(
                                      color: AppColors.onSurface,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            LocalizedText(
                              service.waiverContent,
                              style: AppTypography.bodySm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 20),

                      // 7. REVIEWS PREVIEW
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: LocalizedText(
                              'Đánh giá từ khách hàng ($_reviewTotalElements)',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppTypography.headlineSm(
                                color: AppColors.onSurface,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.star,
                                  color: AppColors.starRating, size: 16),
                              const SizedBox(width: 4),
                              LocalizedText(
                                service.avgRating.toStringAsFixed(1),
                                style: AppTypography.labelMd(
                                  color: AppColors.onSurface,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),

                      if (_loadingReviews)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (_reviewsError != null)
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(AppShapes.spaceSm),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: AppShapes.radiusDefault,
                            border: Border.all(color: AppColors.borderSubtle),
                          ),
                          child: Column(
                            children: [
                              LocalizedText(
                                _reviewsError!,
                                textAlign: TextAlign.center,
                                style: AppTypography.bodySm(
                                  color: AppColors.error,
                                ),
                              ),
                              const SizedBox(height: 8),
                              TextButton.icon(
                                onPressed: _loadReviews,
                                icon: const Icon(Icons.refresh, size: 16),
                                label: const LocalizedText('Thử lại'),
                              ),
                            ],
                          ),
                        )
                      else if (_reviews.isEmpty)
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(AppShapes.spaceSm),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLowest,
                            borderRadius: AppShapes.radiusDefault,
                            border: Border.all(color: AppColors.borderSubtle),
                          ),
                          child: LocalizedText(
                            'Chưa có đánh giá nào cho dịch vụ này.',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        )
                      else ...[
                        ..._reviews.map((rev) {
                          final currentUserId =
                              AuthSession.instance.profile?['id']?.toString() ??
                              '';
                          final isOwnReview =
                              currentUserId.isNotEmpty &&
                              rev.customerId == currentUserId;
                          final initialChar = rev.customerName.trim().isNotEmpty
                              ? rev.customerName.trim()[0].toUpperCase()
                              : 'K';
                          return Container(
                            margin: const EdgeInsets.only(bottom: 12),
                            padding: const EdgeInsets.all(AppShapes.spaceSm),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLowest,
                              borderRadius: AppShapes.radiusDefault,
                              border: Border.all(color: AppColors.borderSubtle),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    CircleAvatar(
                                      radius: 16,
                                      backgroundColor:
                                          AppColors.secondaryContainer,
                                      child: Text(
                                        initialChar,
                                        style: AppTypography.labelSm(
                                          color: AppColors.onSecondaryContainer,
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            rev.customerName,
                                            style: AppTypography.labelMd(
                                              color: AppColors.onSurface,
                                              fontWeight: FontWeight.w700,
                                            ),
                                          ),
                                          Text(
                                            '${rev.createdAt.day.toString().padLeft(2, '0')}/${rev.createdAt.month.toString().padLeft(2, '0')}/${rev.createdAt.year}',
                                            style: AppTypography.bodySm(
                                              color: AppColors.outline,
                                            ).copyWith(fontSize: 11),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Row(
                                      children: List.generate(
                                        rev.rating,
                                        (_) => const Icon(
                                          Icons.star,
                                          size: 14,
                                          color: AppColors.starRating,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  rev.comment,
                                  style: AppTypography.bodySm(
                                    color: AppColors.onSurface,
                                  ),
                                ),
                                if (rev.images.isNotEmpty) ...[
                                  const SizedBox(height: 8),
                                  Wrap(
                                    spacing: 6,
                                    runSpacing: 6,
                                    children: rev.images.map((imgUrl) {
                                      return ClipRRect(
                                        borderRadius: BorderRadius.circular(6),
                                        child: Image.network(
                                          imgUrl,
                                          width: 56,
                                          height: 56,
                                          fit: BoxFit.cover,
                                          errorBuilder: (_, _, _) => Container(
                                            width: 56,
                                            height: 56,
                                            color:
                                                AppColors.surfaceContainerHigh,
                                            child: const Icon(
                                              Icons.image_outlined,
                                              size: 18,
                                            ),
                                          ),
                                        ),
                                      );
                                    }).toList(),
                                  ),
                                ],
                                if (rev.vendorReply != null &&
                                    rev.vendorReply!.trim().isNotEmpty) ...[
                                  const SizedBox(height: 8),
                                  Container(
                                    width: double.infinity,
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: AppColors.surfaceContainerLow,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        LocalizedText(
                                          'Phản hồi từ đối tác',
                                          style: AppTypography.labelSm(
                                            color: AppColors.secondary,
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          rev.vendorReply!,
                                          style: AppTypography.bodySm(
                                            color: AppColors.onSurfaceVariant,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                                const SizedBox(height: 6),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.end,
                                  children: [
                                    if (isOwnReview)
                                      TextButton.icon(
                                        style: TextButton.styleFrom(
                                          visualDensity: VisualDensity.compact,
                                        ),
                                        onPressed: _reviewActionBusy
                                            ? null
                                            : () => _openEditReviewDialog(rev),
                                        icon: const Icon(
                                          Icons.edit_outlined,
                                          size: 14,
                                          color: AppColors.secondary,
                                        ),
                                        label: LocalizedText(
                                          'Chỉnh sửa',
                                          style: AppTypography.labelSm(
                                            color: AppColors.secondary,
                                          ),
                                        ),
                                      ),
                                    TextButton.icon(
                                      style: TextButton.styleFrom(
                                        visualDensity: VisualDensity.compact,
                                      ),
                                      onPressed:
                                          (_reviewActionBusy || rev.isFlagged)
                                          ? null
                                          : () => _openFlagReviewDialog(rev),
                                      icon: Icon(
                                        rev.isFlagged
                                            ? Icons.flag
                                            : Icons.flag_outlined,
                                        size: 14,
                                        color: rev.isFlagged
                                            ? AppColors.error
                                            : AppColors.onSurfaceVariant,
                                      ),
                                      label: LocalizedText(
                                        rev.isFlagged
                                            ? 'Đã báo cáo'
                                            : 'Báo cáo',
                                        style: AppTypography.labelSm(
                                          color: rev.isFlagged
                                              ? AppColors.error
                                              : AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          );
                        }),
                        if (_reviewPage + 1 < _reviewTotalPages)
                          Center(
                            child: OutlinedButton(
                              onPressed: _loadingMoreReviews
                                  ? null
                                  : () => _loadReviews(loadMore: true),
                              child: LocalizedText(
                                _loadingMoreReviews
                                    ? 'Đang tải...'
                                    : 'Tải thêm đánh giá',
                              ),
                            ),
                          ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),

          // TOP NAVIGATION BAR (OVERLAY)
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: AppShapes.gutterMobile, vertical: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    CircleAvatar(
                      backgroundColor:
                          AppColors.surface.withValues(alpha: 0.85),
                      child: IconButton(
                        icon: const Icon(Icons.arrow_back,
                            color: AppColors.onSurface),
                        onPressed: () => Navigator.pop(context),
                      ),
                    ),
                    Row(
                      children: [
                        CircleAvatar(
                          backgroundColor:
                              AppColors.surface.withValues(alpha: 0.85),
                          child: IconButton(
                            icon: const Icon(Icons.share,
                                color: AppColors.onSurface),
                            onPressed: () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                    content: LocalizedText(
                                        'Đã sao chép liên kết chia sẻ!')),
                              );
                            },
                          ),
                        ),
                        const SizedBox(width: 8),
                        CircleAvatar(
                          backgroundColor:
                              AppColors.surface.withValues(alpha: 0.85),
                          child: IconButton(
                            icon: Icon(
                              (widget.onToggleFavorite != null
                                      ? widget.service.isFavorite
                                      : _isFavorite)
                                  ? Icons.favorite
                                  : Icons.favorite_border,
                              color: (widget.onToggleFavorite != null
                                      ? widget.service.isFavorite
                                      : _isFavorite)
                                  ? AppColors.primary
                                  : AppColors.onSurface,
                            ),
                            onPressed: widget.favoriteBusy
                                ? null
                                : () async {
                                    if (widget.onToggleFavorite != null) {
                                      await widget.onToggleFavorite!();
                                    } else {
                                      setState(
                                          () => _isFavorite = !_isFavorite);
                                    }
                                  },
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),

          // STICKY BOTTOM BAR (PRICE & BOOKING BUTTON)
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: EdgeInsets.only(
                left: AppShapes.gutterMobile,
                right: AppShapes.gutterMobile,
                top: 12,
                bottom: MediaQuery.of(context).padding.bottom + 12,
              ),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                boxShadow: AppShapes.bottomNavShadow,
                border: Border(
                  top: BorderSide(
                    color: AppColors.borderSubtle.withValues(alpha: 0.8),
                    width: 0.5,
                  ),
                ),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        LocalizedText(
                          'Giá trọn gói',
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                        RichText(
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          text: TextSpan(
                            children: [
                              TextSpan(
                                text: tr(context, _formatPrice(service.price)),
                                style: AppTypography.headlineMd(
                                  color: AppColors.primary,
                                ),
                              ),
                              TextSpan(
                                text: tr(context, ' / khách'),
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  AppPillButton(
                    label: 'Chọn lịch & đặt',
                    variant: AppButtonVariant.primary,
                    trailingIcon: Icons.calendar_today,
                    onPressed: _openBookingSheet,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInclusionItem(IconData icon, String title, String subtitle) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: AppColors.secondaryContainer.withValues(alpha: 0.4),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(icon, size: 18, color: AppColors.secondary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                LocalizedText(
                  title,
                  style: AppTypography.labelMd(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                LocalizedText(
                  subtitle,
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
