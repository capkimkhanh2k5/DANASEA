import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class FlashSaleSection extends StatefulWidget {
  final List<FlashSaleModel> items;
  final ValueChanged<FlashSaleModel>? onItemTap;
  final VoidCallback? onSeeAll;

  const FlashSaleSection({
    super.key,
    required this.items,
    this.onItemTap,
    this.onSeeAll,
  });

  @override
  State<FlashSaleSection> createState() => _FlashSaleSectionState();
}

class _FlashSaleSectionState extends State<FlashSaleSection> {
  Timer? _timer;
  Timer? _autoScrollTimer;
  final ScrollController _scrollController = ScrollController();
  Duration _remaining = const Duration(hours: 3, minutes: 45, seconds: 20);
  bool _isUserInteracting = false;

  @override
  void initState() {
    super.initState();
    _calculateRemaining();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        _calculateRemaining();
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _startAutoScroll();
    });
  }

  void _startAutoScroll() {
    _autoScrollTimer?.cancel();
    // Chu kỳ: Lướt mượt 700ms -> Khựng đứng yên 2 giây (2000ms) -> Lướt tiếp theo vòng tròn vô tận
    _autoScrollTimer = Timer.periodic(const Duration(milliseconds: 2700), (_) {
      if (!mounted ||
          !_scrollController.hasClients ||
          _isUserInteracting ||
          widget.items.length <= 1) {
        return;
      }
      const cardStep = 165.0 + 12.0; // Chiều rộng 1 thẻ + khoảng cách = 177.0
      final current = _scrollController.offset;
      final cycleWidth = widget.items.length * cardStep;

      // Nếu đã cuộn qua nhiều vòng (>= 30 vòng), lùi lại 15 vòng một cách âm thầm (không giật hình)
      if (current >= cycleWidth * 30) {
        final rewindOffset = cycleWidth * 15;
        _scrollController.jumpTo(current - rewindOffset);
      }

      // Xác định index thẻ hiện tại và cuộn tiếp đến thẻ kế tiếp theo vòng tròn khép kín
      final currentCardIndex = (_scrollController.offset / cardStep).round();
      final targetOffset = (currentCardIndex + 1) * cardStep;

      _scrollController.animateTo(
        targetOffset,
        duration: const Duration(milliseconds: 700),
        curve: Curves.fastOutSlowIn,
      );
    });
  }

  void _calculateRemaining() {
    if (widget.items.isEmpty) return;
    final now = DateTime.now();
    DateTime? minEnd;
    for (final it in widget.items) {
      if (it.endsAt.isAfter(now)) {
        if (minEnd == null || it.endsAt.isBefore(minEnd)) {
          minEnd = it.endsAt;
        }
      }
    }
    if (minEnd != null) {
      final diff = minEnd.difference(now);
      if (mounted) {
        setState(() {
          _remaining = diff.isNegative ? Duration.zero : diff;
        });
      }
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _autoScrollTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  String _formatPrice(double price) {
    final intPrice = price.round();
    final str = intPrice.toString();
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

  Widget _buildTimeBox(String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2.5),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(5),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 3,
            offset: const Offset(0, 1),
          ),
        ],
      ),
      child: Text(
        value.padLeft(2, '0'),
        style: const TextStyle(
          color: Colors.white,
          fontSize: 11.5,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.items.isEmpty) {
      return const SizedBox.shrink();
    }

    final hours = _remaining.inHours;
    final minutes = _remaining.inMinutes % 60;
    final seconds = _remaining.inSeconds % 60;

    return Container(
      margin: const EdgeInsets.only(bottom: AppShapes.spaceLg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. HEADER ROW: ⚡ FLASH SALE + COUNTDOWN TIMER
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            child: Row(
              children: [
                // Bolt Icon
                Container(
                  padding: const EdgeInsets.all(5),
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [Color(0xFFFF3D00), Color(0xFFFF9100)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.bolt_rounded,
                    color: Colors.white,
                    size: 16,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            'FLASH SALE',
                            style: AppTypography.headlineSm(
                              color: const Color(0xFFD32F2F),
                              fontWeight: FontWeight.w800,
                            ).copyWith(
                              letterSpacing: 0.6,
                              fontSize: 16.5,
                            ),
                          ),
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 5,
                              vertical: 1.5,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFEBEE),
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(
                                color: const Color(0xFFFFCDD2),
                                width: 0.8,
                              ),
                            ),
                            child: const Text(
                              'GIỜ VÀNG',
                              style: TextStyle(
                                color: Color(0xFFD32F2F),
                                fontSize: 9.5,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Ưu đãi thể thao biển số lượng có hạn',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ).copyWith(fontSize: 11),
                      ),
                    ],
                  ),
                ),

                // Countdown Timer Box
                Row(
                  children: [
                    _buildTimeBox('$hours'),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 2.5),
                      child: Text(
                        ':',
                        style: TextStyle(
                          fontWeight: FontWeight.w900,
                          fontSize: 12,
                          color: Color(0xFF1E293B),
                        ),
                      ),
                    ),
                    _buildTimeBox('$minutes'),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 2.5),
                      child: Text(
                        ':',
                        style: TextStyle(
                          fontWeight: FontWeight.w900,
                          fontSize: 12,
                          color: Color(0xFF1E293B),
                        ),
                      ),
                    ),
                    _buildTimeBox('$seconds'),
                  ],
                ),

                // Arrow button to full Flash Sale Screen
                if (widget.onSeeAll != null) ...[
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: widget.onSeeAll,
                    borderRadius: BorderRadius.circular(20),
                    child: Container(
                      padding: const EdgeInsets.all(5),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFEBEE),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: const Color(0xFFFFCDD2),
                          width: 0.8,
                        ),
                      ),
                      child: const Icon(
                        Icons.arrow_forward_ios_rounded,
                        size: 13,
                        color: Color(0xFFD32F2F),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 12),

          // 2. HORIZONTAL LIST OF FLASH SALE CARDS (Auto-scroll & pause 2s)
          NotificationListener<ScrollNotification>(
            onNotification: (notification) {
              if (notification is ScrollStartNotification) {
                _isUserInteracting = true;
              } else if (notification is ScrollEndNotification) {
                Future.delayed(const Duration(seconds: 2), () {
                  if (mounted) _isUserInteracting = false;
                });
              }
              return false;
            },
            child: SizedBox(
              height: 242,
              child: ListView.builder(
                controller: _scrollController,
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.symmetric(
                  horizontal: AppShapes.gutterMobile,
                ),
                scrollDirection: Axis.horizontal,
                itemCount: widget.items.length > 1 ? null : widget.items.length,
                itemBuilder: (context, index) {
                  final item = widget.items[index % widget.items.length];
                  return Padding(
                    padding: const EdgeInsets.only(right: 12),
                    child: _buildFlashSaleCard(context, item),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFlashSaleCard(BuildContext context, FlashSaleModel item) {
    final discountPercent = item.actualDiscountPercent.round();

    return InkWell(
      onTap: () => widget.onItemTap?.call(item),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        width: 165,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: const Color(0xFFFFCC80).withValues(alpha: 0.5),
            width: 1,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 8,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // IMAGE with BADGES
            Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(13),
                  ),
                  child: Image.network(
                    item.serviceImage,
                    width: 165,
                    height: 115,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) => Container(
                      width: 165,
                      height: 115,
                      color: const Color(0xFFE2E8F0),
                      child: const Icon(
                        Icons.sailing_rounded,
                        color: Color(0xFF94A3B8),
                        size: 32,
                      ),
                    ),
                  ),
                ),
                // DISCOUNT PERCENT BADGE (Top-Left)
                if (discountPercent > 0)
                  Positioned(
                    top: 6,
                    left: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFFFF3D00), Color(0xFFFF1744)],
                        ),
                        borderRadius: BorderRadius.circular(6),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFFFF1744).withValues(alpha: 0.4),
                            blurRadius: 4,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Text(
                        '-$discountPercent%',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.2,
                        ),
                      ),
                    ),
                  ),
                // REMAINING QUOTA BADGE (Bottom-Left)
                if (item.remainingQuota > 0)
                  Positioned(
                    bottom: 6,
                    left: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.65),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.local_fire_department,
                            color: Color(0xFFFF9800),
                            size: 11,
                          ),
                          const SizedBox(width: 2),
                          Text(
                            'Còn ${item.remainingQuota} suất',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 9.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),

            // CONTENT
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 8,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // TITLE
                    Text(
                      item.serviceName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: AppTypography.labelMd(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w700,
                      ).copyWith(
                        fontSize: 12.5,
                        height: 1.25,
                      ),
                    ),

                    // PRICES & PROGRESS
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // SALE PRICE
                        Text(
                          _formatPrice(item.salePrice),
                          style: AppTypography.labelLg(
                            color: const Color(0xFFD32F2F),
                            fontWeight: FontWeight.w900,
                          ).copyWith(
                            fontSize: 14.5,
                          ),
                        ),
                        // ORIGINAL PRICE (Strikethrough)
                        if (item.originalPrice > item.salePrice)
                          Text(
                            _formatPrice(item.originalPrice),
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ).copyWith(
                              decoration: TextDecoration.lineThrough,
                              fontSize: 10.5,
                            ),
                          ),
                        const SizedBox(height: 5),
                        // PROGRESS BAR
                        ClipRRect(
                          borderRadius: BorderRadius.circular(3),
                          child: const LinearProgressIndicator(
                            value: 0.78,
                            backgroundColor: Color(0xFFFFEBEE),
                            valueColor: AlwaysStoppedAnimation<Color>(
                              Color(0xFFFF5252),
                            ),
                            minHeight: 4,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
