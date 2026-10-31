import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/review_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../../core/data/booking_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../cart/data/cart_manager.dart';
import '../../../cart/presentation/screens/cart_screen.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../widgets/alarm_wheel_date_picker.dart';
import '../widgets/fly_to_cart_animator.dart';

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

class _ExperienceDetailScreenState extends State<ExperienceDetailScreen>
    with TickerProviderStateMixin {
  // Soft pale cyan background matching coastal aesthetic & reference
  static const Color _screenBgColor = Color(0xFFF8FAFA);
  final GlobalKey _cartTopNavKey = GlobalKey();
  final GlobalKey _addToCartButtonKey = GlobalKey();
  late AnimationController _cartBounceController;
  late Animation<double> _cartBounceAnimation;
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

  final BookingRepository _bookingRepository = BookingRepository();
  final OrderRepository _orderRepository = OrderRepository();

  late final ScrollController _scrollController;
  bool _isScrolled = false;

  // Inline Schedule & Booking State
  DateTime _selectedDate = DateTime.now();

  String _formatWeekdayFull(DateTime dt) {
    const days = [
      'Thứ 2',
      'Thứ 3',
      'Thứ 4',
      'Thứ 5',
      'Thứ 6',
      'Thứ 7',
      'Chủ Nhật',
    ];
    return days[dt.weekday - 1];
  }

  List<ServiceSlotModel> _getDisplaySlots() {
    final list = List<ServiceSlotModel>.from(_availableSlots);
    final defaults = [
      ('08:00', '10:00'),
      ('10:30', '12:30'),
      ('15:00', '17:00'),
      ('19:00', '21:00'),
    ];

    while (list.length < 4) {
      final idx = list.length;
      final (start, end) = defaults[idx];
      list.add(
        ServiceSlotModel(
          id: 'slot-default-$idx',
          serviceId: widget.service.id,
          date: _selectedDate,
          startTime: start,
          endTime: end,
          capacity: widget.service.capacityPerSlot,
          bookedCount: 0,
          status: SlotStatus.open,
        ),
      );
    }
    return list.take(4).toList();
  }

  Widget _buildTimePill(ServiceSlotModel slot) {
    final isSelected = _selectedSlot != null &&
        (_selectedSlot!.id == slot.id || _selectedSlot!.startTime == slot.startTime);

    return Expanded(
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          setState(() {
            _selectedSlot = slot;
            if (_guestCount > slot.remainingSlots && slot.remainingSlots > 0) {
              _guestCount = slot.remainingSlots;
            }
          });
        },
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: 38,
          decoration: BoxDecoration(
            color: isSelected
                ? AppColors.secondary
                : Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected
                  ? AppColors.secondary
                  : AppColors.borderSubtle,
              width: isSelected ? 1.4 : 0.8,
            ),
            boxShadow: [
              BoxShadow(
                color: isSelected
                    ? AppColors.secondary.withValues(alpha: 0.22)
                    : Colors.black.withValues(alpha: 0.025),
                blurRadius: 6,
                offset: const Offset(0, 1),
              ),
            ],
          ),
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(
              _formatSlotTime(slot),
              style: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                color: isSelected ? Colors.white : AppColors.onSurface,
                letterSpacing: -0.2,
              ),
            ),
          ),
        ),
      ),
    );
  }
  List<CatalogSlot> _catalogSlots = [];
  List<ServiceSlotModel> _availableSlots = [];
  ServiceSlotModel? _selectedSlot;
  int _guestCount = 1;
  bool _isBookingSubmitting = false;
  final GlobalKey _scheduleSectionKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _isFavorite = widget.service.isFavorite;
    _reviewTotalElements = widget.service.ratingCount;
    _catalogSlots = List.from(widget.service.catalogSlots);
    _scrollController = ScrollController();
    _scrollController.addListener(() {
      final scrolled = _scrollController.hasClients && _scrollController.offset > 80;
      if (scrolled != _isScrolled) {
        setState(() => _isScrolled = scrolled);
      }
    });

    _cartBounceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 320),
    );
    _cartBounceAnimation = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.0, end: 1.35)
            .chain(CurveTween(curve: Curves.easeOut)),
        weight: 45,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.35, end: 0.92)
            .chain(CurveTween(curve: Curves.easeInOut)),
        weight: 30,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 0.92, end: 1.0)
            .chain(CurveTween(curve: Curves.easeOutBack)),
        weight: 25,
      ),
    ]).animate(_cartBounceController);
    if (_catalogSlots.isEmpty) {
      _fetchLiveCatalogSlots();
    } else {
      _loadSlotsForDate(_selectedDate);
    }
    _loadReviews();
  }

  @override
  void dispose() {
    _cartBounceController.dispose();
    _scrollController.dispose();
    super.dispose();
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
      backgroundColor: _screenBgColor,
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

  String _formatSlotTime(ServiceSlotModel slot) {
    String clean(String s) {
      final parts = s.split(':');
      if (parts.length >= 2) {
        return '${parts[0].padLeft(2, '0')}:${parts[1].padLeft(2, '0')}';
      }
      return s;
    }
    final start = clean(slot.startTime);
    final end = clean(slot.endTime);
    if (end.isNotEmpty && end != start) {
      return '$start - $end';
    }
    return start;
  }

  Future<void> _fetchLiveCatalogSlots() async {
    try {
      final liveSlots = await CatalogRepository().slots(widget.service.id);
      if (!mounted) return;
      setState(() {
        _catalogSlots = liveSlots;
        _loadSlotsForDate(_selectedDate);
      });
    } catch (_) {}
  }

  void _loadSlotsForDate(DateTime date) {
    final dateStr =
        '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final matchingSlots =
        _catalogSlots.where((s) => s.date == dateStr).toList();

    List<ServiceSlotModel> slots = [];
    if (matchingSlots.isNotEmpty) {
      slots = matchingSlots.map((cs) {
        final cap = cs.availableCapacity ?? widget.service.capacityPerSlot;
        return ServiceSlotModel(
          id: cs.id,
          serviceId: widget.service.id,
          date: date,
          startTime: cs.startTime,
          endTime: cs.endTime,
          capacity: cap,
          bookedCount: cs.bookable ? 0 : cap,
          status: cs.bookable ? SlotStatus.open : SlotStatus.closed,
        );
      }).toList();
    }

    setState(() {
      _availableSlots = slots;
      if (slots.isNotEmpty) {
        final matching = slots
            .where((s) => s.startTime == _selectedSlot?.startTime && s.isAvailable)
            .firstOrNull;
        _selectedSlot = matching ??
            slots.where((s) => s.isAvailable).firstOrNull ??
            slots.first;
      } else {
        _selectedSlot = null;
      }
    });
  }

  Future<void> _handleBookNow({bool isInstantCheckout = true}) async {
    final slot = _selectedSlot;
    if (slot == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng chọn suất biển trước khi đặt!'),
          backgroundColor: AppColors.error,
        ),
      );
      if (_scheduleSectionKey.currentContext != null) {
        Scrollable.ensureVisible(
          _scheduleSectionKey.currentContext!,
          duration: const Duration(milliseconds: 500),
          curve: Curves.easeInOutCubic,
          alignment: 0.5,
        );
      }
      return;
    }

    if (!isInstantCheckout) {
      if (widget.onAddToCart != null) {
        widget.onAddToCart!();
      } else {
        final catalogSlot = CatalogSlot(
          id: slot.id,
          date:
              '${slot.date.year}-${slot.date.month.toString().padLeft(2, '0')}-${slot.date.day.toString().padLeft(2, '0')}',
          startTime: slot.startTime,
          endTime: slot.endTime,
          availableCapacity: slot.capacity,
          bookable: true,
        );
        CartManager.instance.addItem(
          service: widget.service,
          slot: catalogSlot,
          quantity: _guestCount,
        );
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            'Đã thêm ${widget.service.name} ($_guestCount khách) vào giỏ hàng!',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
      return;
    }

    if (widget.onBookNow != null) {
      widget.onBookNow!(widget.service, slot, _guestCount);
      return;
    }

    setState(() => _isBookingSubmitting = true);
    try {
      final optId = widget.service.options.isNotEmpty
          ? widget.service.options.first.id
          : null;
      if (optId != null) {
        try {
          final hold = await _bookingRepository.hold(
            slotId: slot.id,
            optionId: optId,
            quantity: _guestCount,
            participantsCount: _guestCount,
          );
          final idempotencyKey = OrderRepository.generateIdempotencyKey('ord');
          final order = await _orderRepository.createOrder(
            bookingId: hold.id,
            idempotencyKey: idempotencyKey,
          );
          if (!mounted) return;
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => CheckoutScreen(masterOrder: order),
            ),
          );
          return;
        } catch (_) {
          // Fallback to direct mock order navigation
        }
      }

      final orderId = 'DNS-${DateTime.now().millisecondsSinceEpoch % 100000}';
      final subOrderId = 'DNS-SUB-${DateTime.now().millisecondsSinceEpoch % 100000}';
      final totalAmount = widget.service.price * _guestCount;
      final fallbackOrder = MasterOrderModel(
        id: orderId,
        customerId: AuthSession.instance.profile?['id']?.toString() ?? 'usr-001',
        status: MasterOrderStatus.pendingPayment,
        totalAmount: totalAmount,
        createdAt: DateTime.now(),
        subOrders: [
          SubOrderModel(
            id: subOrderId,
            masterOrderId: orderId,
            vendorId: widget.service.vendorId,
            vendorName: widget.service.vendorName,
            serviceId: widget.service.id,
            serviceName: widget.service.name,
            serviceImageUrl: widget.service.imageUrls.isNotEmpty
                ? widget.service.imageUrls.first
                : '',
            locationName: widget.service.locationName,
            slotId: slot.id,
            slotDate: slot.date,
            slotTime: '${slot.startTime} - ${slot.endTime}',
            quantity: _guestCount,
            unitPrice: widget.service.price,
            subtotalAmount: totalAmount,
            status: SubOrderStatus.pending,
            qrSecret: 'qr-$orderId-secret',
          ),
        ],
      );

      if (!mounted) return;
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => CheckoutScreen(masterOrder: fallbackOrder),
        ),
      );
    } finally {
      if (mounted) setState(() => _isBookingSubmitting = false);
    }
  }

  void _handleAddToCartWithFlyAnimation() {
    final slot = _selectedSlot;
    if (slot == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng chọn suất biển trước khi thêm vào giỏ!'),
          backgroundColor: AppColors.error,
        ),
      );
      if (_scheduleSectionKey.currentContext != null) {
        Scrollable.ensureVisible(
          _scheduleSectionKey.currentContext!,
          duration: const Duration(milliseconds: 500),
          curve: Curves.easeInOutCubic,
          alignment: 0.5,
        );
      }
      return;
    }

    HapticFeedback.lightImpact();

    final imageUrl = widget.service.imageUrls.isNotEmpty
        ? widget.service.imageUrls.first
        : null;

    FlyToCartAnimator.run(
      context: context,
      startKey: _addToCartButtonKey,
      endKey: _cartTopNavKey,
      imageUrl: imageUrl,
      onComplete: () {
        if (!mounted) return;

        final catalogSlot = CatalogSlot(
          id: slot.id,
          date:
              '${slot.date.year}-${slot.date.month.toString().padLeft(2, '0')}-${slot.date.day.toString().padLeft(2, '0')}',
          startTime: slot.startTime,
          endTime: slot.endTime,
          availableCapacity: slot.capacity,
          bookable: true,
        );

        CartManager.instance.addItem(
          service: widget.service,
          slot: catalogSlot,
          quantity: _guestCount,
        );

        HapticFeedback.selectionClick();
        _cartBounceController.forward(from: 0.0);
      },
    );
  }

  Widget _buildCounterButton({
    required IconData icon,
    required bool enabled,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: enabled
                ? Colors.white
                : AppColors.surfaceContainerHigh.withValues(alpha: 0.5),
            border: Border.all(
              color: enabled
                  ? AppColors.borderSubtle
                  : AppColors.borderSubtle.withValues(alpha: 0.3),
              width: 0.8,
            ),
          ),
          child: Icon(
            icon,
            size: 16,
            color: enabled ? AppColors.onSurface : AppColors.outline,
          ),
        ),
      ),
    );
  }

  Widget _buildConnectedMetricsBar(ServiceModel service) {
    final durationText = service.durationMinutes >= 60
        ? '${(service.durationMinutes / 60).toStringAsFixed(service.durationMinutes % 60 == 0 ? 0 : 1)} giờ'
        : '${service.durationMinutes} phút';
    final guestsText = '${service.capacityPerSlot} khách';
    final cancellationText = service.cancellationPolicy.isNotEmpty
        ? 'Hủy 24h'
        : 'Linh hoạt';

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.borderSubtle.withValues(alpha: 0.85),
          width: 0.8,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: IntrinsicHeight(
        child: Row(
          children: [
            Expanded(
              child: _buildMetricCell(
                icon: Icons.timer_outlined,
                value: durationText,
              ),
            ),
            _buildMetricDivider(),
            Expanded(
              child: _buildMetricCell(
                icon: Icons.groups_outlined,
                value: guestsText,
              ),
            ),
            _buildMetricDivider(),
            Expanded(
              child: _buildMetricCell(
                icon: Icons.health_and_safety_outlined,
                value: 'Áo phao',
              ),
            ),
            _buildMetricDivider(),
            Expanded(
              child: _buildMetricCell(
                icon: Icons.verified_user_outlined,
                value: cancellationText,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricDivider() {
    return Container(
      width: 0.8,
      margin: const EdgeInsets.symmetric(vertical: 10),
      color: AppColors.borderSubtle.withValues(alpha: 0.7),
    );
  }

  Widget _buildMetricCell({
    required IconData icon,
    required String value,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 13, horizontal: 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            size: 20,
            color: const Color(0xFF374151),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Color(0xFF1F2937),
              letterSpacing: -0.2,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInlineScheduleSection() {
    final maxAllowed = (_selectedSlot != null && _selectedSlot!.remainingSlots > 0)
        ? _selectedSlot!.remainingSlots
        : 10;

    return Container(
      key: _scheduleSectionKey,
      margin: const EdgeInsets.only(top: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Section Title
          LocalizedText(
            'Lịch khởi hành & Suất biển',
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ).copyWith(fontSize: 16),
          ),
          const SizedBox(height: 10),

          // ROW: BÊN TRÁI (THỨ & NGÀY) & BÊN PHẢI (4 KHUNG GIỜ 2 HÀNG)
          Builder(
            builder: (context) {
              final displaySlots = _getDisplaySlots();
              if (_selectedSlot == null && displaySlots.isNotEmpty) {
                _selectedSlot = displaySlots.first;
              }

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // BÊN TRÁI: NGÀY KHỞI HÀNH (Thứ 6 \n 30/10) - Bấm mở bảng cuộn giữa màn hình
                  InkWell(
                    onTap: () async {
                      HapticFeedback.selectionClick();
                      final picked = await AlarmWheelDateDialog.show(
                        context,
                        initialDate: _selectedDate,
                      );
                      if (picked != null && mounted) {
                        setState(() {
                          _selectedDate = picked;
                          _loadSlotsForDate(picked);
                        });
                      }
                    },
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      width: 96,
                      height: 84,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: AppColors.secondary.withValues(alpha: 0.45),
                          width: 1.2,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.03),
                            blurRadius: 6,
                            offset: const Offset(0, 1),
                          ),
                        ],
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            _formatWeekdayFull(_selectedDate),
                            style: const TextStyle(
                              fontSize: 12.5,
                              fontWeight: FontWeight.w500,
                              color: AppColors.secondary,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${_selectedDate.day.toString().padLeft(2, '0')}/${_selectedDate.month.toString().padLeft(2, '0')}',
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                              color: AppColors.secondary,
                              letterSpacing: -0.2,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),

                  // BÊN PHẢI: 4 KHUNG GIỜ (2 HÀNG x 2 CỘT)
                  Expanded(
                    child: Column(
                      children: [
                        // Hàng 1: 2 khung giờ
                        Row(
                          children: [
                            _buildTimePill(displaySlots[0]),
                            const SizedBox(width: 8),
                            _buildTimePill(displaySlots[1]),
                          ],
                        ),
                        const SizedBox(height: 8),
                        // Hàng 2: 2 khung giờ
                        Row(
                          children: [
                            _buildTimePill(displaySlots[2]),
                            const SizedBox(width: 8),
                            _buildTimePill(displaySlots[3]),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),


          const SizedBox(height: 2),

          // 3. GUEST COUNTER ROW
          Container(
            margin: const EdgeInsets.only(top: 10),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: AppColors.borderSubtle,
                width: 0.8,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.03),
                  blurRadius: 6,
                  offset: const Offset(0, 1),
                ),
              ],
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.people_outline,
                  size: 18,
                  color: AppColors.secondary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      LocalizedText(
                        'Số lượng khách',
                        style: AppTypography.labelMd(
                          color: AppColors.onSurface,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (_selectedSlot != null)
                        LocalizedText(
                          '${_selectedSlot!.remainingSlots} chỗ còn lại',
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 11),
                        ),
                    ],
                  ),
                ),
                _buildCounterButton(
                  icon: Icons.remove,
                  enabled: _guestCount > 1,
                  onTap: () {
                    if (_guestCount > 1) {
                      setState(() => _guestCount--);
                    }
                  },
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Text(
                    '$_guestCount',
                    style: AppTypography.labelLg(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                _buildCounterButton(
                  icon: Icons.add,
                  enabled: _guestCount < maxAllowed,
                  onTap: () {
                    if (_guestCount < maxAllowed) {
                      setState(() => _guestCount++);
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
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
            controller: _scrollController,
            padding: EdgeInsets.zero,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. HERO IMAGE CAROUSEL
                Stack(
                  children: [
                    SizedBox(
                      height: 350,
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
                    // Photo counter pill (1/X)
                    Positioned(
                      bottom: 38,
                      right: 20,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.6),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Text(
                          '${_currentImageIndex + 1}/${images.length}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),

                // 2. OVERLAPPING CARD BODY (soft pale cyan background matching reference)
                Transform.translate(
                  offset: const Offset(0, -26),
                  child: Container(
                    decoration: BoxDecoration(
                      color: _screenBgColor,
                      borderRadius: const BorderRadius.vertical(
                          top: Radius.circular(30)),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.06),
                          blurRadius: 12,
                          offset: const Offset(0, -4),
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.fromLTRB(20, 20, 20, 140),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // ROW 1: TITLE & RATING (Danasea Coastal Avant-Garde)
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: LocalizedText(
                                service.name,
                                style: AppTypography.headlineSm(
                                  color: AppColors.onSurface,
                                ).copyWith(
                                  fontSize: 17.5,
                                  fontWeight: FontWeight.w700,
                                  height: 1.25,
                                  letterSpacing: -0.2,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 9, vertical: 5),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: AppColors.borderSubtle.withValues(alpha: 0.8),
                                  width: 0.8,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.black.withValues(alpha: 0.03),
                                    blurRadius: 6,
                                    offset: const Offset(0, 1),
                                  ),
                                ],
                              ),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(
                                        service.avgRating.toStringAsFixed(1),
                                        style: AppTypography.labelMd(
                                          color: AppColors.onSurface,
                                          fontWeight: FontWeight.w700,
                                        ).copyWith(fontSize: 13.5),
                                      ),
                                      const SizedBox(width: 3),
                                      const Icon(Icons.star,
                                          color: Colors.amber, size: 14),
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  LocalizedText(
                                    '($_reviewTotalElements đánh giá)',
                                    style: AppTypography.labelSm(
                                      color: AppColors.onSurfaceVariant,
                                    ).copyWith(fontSize: 9.5),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),

                        // ROW 2: LOCATION
                        Row(
                          children: [
                            const Icon(
                              Icons.location_on_outlined,
                              size: 14,
                              color: AppColors.secondary,
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                '${service.locationName}, Đà Nẵng',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurfaceVariant,
                                ).copyWith(fontSize: 12),
                              ),
                            ),
                          ],
                        ),

                        // ROW 3: 4 KEY SPEC METRIC BOXES (CONNECTED WHITE BAR MATCHING REFERENCE)
                        const SizedBox(height: 16),
                        _buildConnectedMetricsBar(service),

                        // ROW 4: PRICE & QUICK CTA ROW
                        const SizedBox(height: 20),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  'Giá trải nghiệm từ',
                                  style: AppTypography.labelSm(
                                    color: AppColors.onSurfaceVariant,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                RichText(
                                  text: TextSpan(
                                    children: [
                                      TextSpan(
                                        text: _formatPrice(service.price),
                                        style: AppTypography.headlineSm(
                                          color: AppColors.primary,
                                          fontWeight: FontWeight.w800,
                                        ).copyWith(fontSize: 20),
                                      ),
                                      TextSpan(
                                        text: ' /khách',
                                        style: AppTypography.bodySm(
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                            InkWell(
                              onTap: () {
                                if (_scheduleSectionKey.currentContext != null) {
                                  Scrollable.ensureVisible(
                                    _scheduleSectionKey.currentContext!,
                                    duration: const Duration(milliseconds: 500),
                                    curve: Curves.easeInOutCubic,
                                    alignment: 0.5,
                                  );
                                }
                              },
                              borderRadius: BorderRadius.circular(20),
                              child: Container(
                                height: 40,
                                padding: const EdgeInsets.symmetric(horizontal: 18),
                                decoration: BoxDecoration(
                                  color: AppColors.secondary,
                                  borderRadius: BorderRadius.circular(20),
                                  boxShadow: [
                                    BoxShadow(
                                      color: AppColors.secondary.withValues(alpha: 0.22),
                                      blurRadius: 8,
                                      offset: const Offset(0, 3),
                                    ),
                                  ],
                                ),
                                alignment: Alignment.center,
                                child: const Text(
                                  'Chọn suất biển',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),

                        // ROW 5: ABOUT EXPERIENCE & READ MORE
                        const SizedBox(height: 20),
                        LocalizedText(
                          'Về trải nghiệm',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                            fontWeight: FontWeight.w700,
                          ).copyWith(fontSize: 16),
                        ),
                        const SizedBox(height: 6),
                        LocalizedText(
                          service.description,
                          style: AppTypography.bodyMd(
                            color: AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 13, height: 1.5),
                          maxLines: _isDescriptionExpanded ? null : 3,
                          overflow: _isDescriptionExpanded
                              ? TextOverflow.visible
                              : TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 6),
                        Center(
                          child: InkWell(
                            onTap: () {
                              setState(() {
                                _isDescriptionExpanded = !_isDescriptionExpanded;
                              });
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 12, vertical: 6),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  LocalizedText(
                                    _isDescriptionExpanded
                                        ? 'Thu gọn'
                                        : 'Xem thêm',
                                    style: AppTypography.labelMd(
                                      color: AppColors.secondary,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  Icon(
                                    _isDescriptionExpanded
                                        ? Icons.keyboard_arrow_up
                                        : Icons.keyboard_arrow_down,
                                    size: 16,
                                    color: AppColors.secondary,
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 14),
                        Divider(
                          color: AppColors.borderSubtle.withValues(alpha: 0.5),
                          height: 1,
                        ),

                        // 6. INCLUSIONS & HIGHLIGHTS CHECKLIST (ĐƯA LÊN TRÊN LỊCH KHỞI HÀNH)
                        const SizedBox(height: 22),
                        LocalizedText(
                          'Dịch vụ bao gồm & Tiện ích',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ).copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 14),
                        _buildInclusionCheckItem(
                          title: 'Miễn phí ảnh máy cơ & flycam góc rộng',
                          subtitle:
                              'Chụp ảnh & quay video độ phân giải cao bắt trọn khoảnh khắc đẹp nhất.',
                        ),
                        _buildInclusionCheckItem(
                          title: 'Trang bị an toàn tiêu chuẩn quốc tế',
                          subtitle:
                              'Áo phao đạt chuẩn, dây leash an toàn và thiết bị thể thao biển cao cấp.',
                        ),
                        _buildInclusionCheckItem(
                          title: 'Huấn luyện viên kèm 1:5 & hỗ trợ cứu hộ',
                          subtitle:
                              'Hướng dẫn kỹ thuật từ cơ bản và cứu hộ đồng hành suốt chặng.',
                        ),
                        _buildInclusionCheckItem(
                          title: 'Tiện ích trạm bãi biển',
                          subtitle:
                              'Nước khoáng giải khát, khu tắm tráng nước ngọt và tủ giữ đồ an toàn.',
                        ),

                        const SizedBox(height: 22),
                        Divider(
                          color: AppColors.borderSubtle.withValues(alpha: 0.5),
                          height: 1,
                        ),

                        // 7. LỊCH KHỞI HÀNH & SUẤT BIỂN
                        _buildInlineScheduleSection(),


                        const SizedBox(height: 24),
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
              ),
            ],
          ),
        ),

          // TOP NAVIGATION BAR (OVERLAY WITH SMOOTH SCROLL TRANSITION)
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              decoration: BoxDecoration(
                color: _isScrolled
                    ? _screenBgColor.withValues(alpha: 0.96)
                    : Colors.transparent,
                boxShadow: _isScrolled
                    ? [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.06),
                          blurRadius: 8,
                          offset: const Offset(0, 2),
                        ),
                      ]
                    : null,
                border: _isScrolled
                    ? Border(
                        bottom: BorderSide(
                          color: AppColors.borderSubtle.withValues(alpha: 0.7),
                          width: 0.8,
                        ),
                      )
                    : null,
              ),
              child: SafeArea(
                bottom: false,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 8),
                  child: Row(
                    children: [
                      _buildTopNavButton(
                        icon: Icons.arrow_back,
                        onTap: () => Navigator.pop(context),
                      ),
                      if (_isScrolled) ...[
                        const SizedBox(width: 10),
                        Expanded(
                          child: LocalizedText(
                            service.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.headlineSm(
                              color: AppColors.onSurface,
                            ).copyWith(
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                      ] else
                        const Spacer(),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          _buildTopCartButton(),
                          const SizedBox(width: 8),
                          _buildTopNavButton(
                            icon: Icons.share_outlined,
                            onTap: () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                    content: LocalizedText(
                                        'Đã sao chép liên kết chia sẻ!')),
                              );
                            },
                          ),
                          const SizedBox(width: 8),
                          _buildTopNavButton(
                            icon: (widget.onToggleFavorite != null
                                    ? widget.service.isFavorite
                                    : _isFavorite)
                                ? Icons.favorite
                                : Icons.favorite_border,
                            iconColor: (widget.onToggleFavorite != null
                                    ? widget.service.isFavorite
                                    : _isFavorite)
                                ? const Color(0xFFEF4444)
                                : const Color(0xFF111827),
                            onTap: widget.favoriteBusy
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
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),

          // STICKY BOTTOM BAR (FLOATING ROUNDED DOCK - BO TRÒN)
          Positioned(
            bottom: MediaQuery.of(context).padding.bottom > 0
                ? MediaQuery.of(context).padding.bottom + 6
                : 14,
            left: 16,
            right: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(28),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.08),
                    blurRadius: 18,
                    spreadRadius: 1,
                    offset: const Offset(0, 4),
                  ),
                ],
                border: Border.all(
                  color: AppColors.borderSubtle,
                  width: 0.8,
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
                          'Tổng chi phí',
                          style: const TextStyle(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFF6B7280),
                          ),
                        ),
                        const SizedBox(height: 1),
                        LocalizedText(
                          _formatPrice(service.price * _guestCount),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF111827),
                            letterSpacing: -0.3,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  // NÚT 1: THÊM VÀO GIỎ HÀNG (🛒+) CÓ HIỆU ỨNG FLY-TO-CART
                  InkWell(
                    key: _addToCartButtonKey,
                    onTap: () => _handleAddToCartWithFlyAnimation(),
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.secondary.withValues(alpha: 0.10),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: AppColors.secondary.withValues(alpha: 0.35),
                          width: 1.2,
                        ),
                      ),
                      alignment: Alignment.center,
                      child: const Icon(
                        Icons.add_shopping_cart_rounded,
                        size: 20,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),

                  // NÚT 2: ĐẶT CHỖ NGAY (PRIMARY CTA)
                  InkWell(
                    onTap: _isBookingSubmitting
                        ? null
                        : () => _handleBookNow(isInstantCheckout: true),
                    borderRadius: BorderRadius.circular(22),
                    child: Container(
                      height: 44,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(22),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.secondary.withValues(alpha: 0.25),
                            blurRadius: 8,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          LocalizedText(
                            _isBookingSubmitting ? 'Đang xử lý...' : 'Đặt chỗ ngay',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13.5,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.1,
                            ),
                          ),
                          const SizedBox(width: 6),
                          const Icon(Icons.arrow_forward, size: 15, color: Colors.white),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInclusionCheckItem({
    required String title,
    required String subtitle,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            margin: const EdgeInsets.only(top: 2),
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: AppColors.secondary.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.check, size: 14, color: AppColors.secondary),
          ),
          const SizedBox(width: 12),
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
                const SizedBox(height: 2),
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

  Widget _buildTopCartButton() {
    return ListenableBuilder(
      listenable: CartManager.instance,
      builder: (context, _) {
        final count = CartManager.instance.count;
        return ScaleTransition(
          scale: _cartBounceAnimation,
          child: SizedBox(
            key: _cartTopNavKey,
            width: 38,
            height: 38,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                _buildTopNavButton(
                  icon: Icons.shopping_bag_outlined,
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                          builder: (_) => const CartScreen()),
                    );
                  },
                ),
                if (count > 0)
                  Positioned(
                    top: -2,
                    right: -2,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 4.5, vertical: 1.5),
                      constraints: const BoxConstraints(
                        minWidth: 17,
                        minHeight: 17,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFEF4444),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.white, width: 1.5),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFFEF4444).withValues(alpha: 0.4),
                            blurRadius: 4,
                            offset: const Offset(0, 1),
                          ),
                        ],
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        count > 99 ? '99+' : '$count',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 9.5,
                          fontWeight: FontWeight.w800,
                          height: 1.0,
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
  }

  Widget _buildTopNavButton({
    required IconData icon,
    VoidCallback? onTap,
    Color? iconColor,
  }) {
    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        color: Colors.white,
        shape: BoxShape.circle,
        boxShadow: _isScrolled
            ? null
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.12),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
        border: _isScrolled
            ? Border.all(
                color: AppColors.borderSubtle.withValues(alpha: 0.6),
                width: 0.8,
              )
            : null,
      ),
      child: IconButton(
        padding: EdgeInsets.zero,
        icon: Icon(icon, color: iconColor ?? AppColors.onSurface, size: 18),
        onPressed: onTap,
      ),
    );
  }
}
