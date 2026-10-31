import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';

class FlashSaleScreen extends StatefulWidget {
  final List<FlashSaleModel>? initialItems;

  const FlashSaleScreen({super.key, this.initialItems});

  @override
  State<FlashSaleScreen> createState() => _FlashSaleScreenState();
}

class _FlashSaleScreenState extends State<FlashSaleScreen> {
  final CatalogRepository _catalogRepository = CatalogRepository();
  List<FlashSaleModel> _items = [];
  bool _loading = false;
  String? _error;
  Timer? _countdownTimer;
  Duration _remaining = const Duration(hours: 3, minutes: 45, seconds: 20);

  @override
  void initState() {
    super.initState();
    if (widget.initialItems != null && widget.initialItems!.isNotEmpty) {
      _items = widget.initialItems!;
      _calculateRemaining();
    } else {
      _loadFlashSales();
    }

    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        _calculateRemaining();
      }
    });
  }

  void _calculateRemaining() {
    if (_items.isEmpty) return;
    final now = DateTime.now();
    DateTime? minEnd;
    for (final it in _items) {
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

  Future<void> _loadFlashSales() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await _catalogRepository.flashSales();
      if (!mounted) return;
      setState(() {
        _items = items;
      });
      _calculateRemaining();
    } catch (e) {
      if (mounted) {
        setState(() => _error = 'Không thể tải danh sách ưu đãi. Vui lòng thử lại.');
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
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

  Widget _buildBigTimeBox(String value, String unit) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
          decoration: BoxDecoration(
            color: const Color(0xFF0F172A).withValues(alpha: 0.85),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: Colors.white.withValues(alpha: 0.2),
              width: 0.8,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.3),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Text(
            value.padLeft(2, '0'),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w900,
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(
          unit,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.9),
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.5,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final hours = _remaining.inHours;
    final minutes = _remaining.inMinutes % 60;
    final seconds = _remaining.inSeconds % 60;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.bolt_rounded,
              color: Color(0xFFFF3D00),
              size: 22,
            ),
            const SizedBox(width: 4),
            Text(
              'FLASH SALE GIỜ VÀNG',
              style: AppTypography.headlineSm(
                color: const Color(0xFFD32F2F),
                fontWeight: FontWeight.w900,
              ).copyWith(fontSize: 17, letterSpacing: 0.5),
            ),
          ],
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _loadFlashSales,
        color: const Color(0xFFFF3D00),
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. TOP HERO BANNER WITH BIG COUNTDOWN (Sea Background)
              Stack(
                children: [
                  // Ocean Background Image
                  Positioned.fill(
                    child: Image.network(
                      'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200&auto=format&fit=crop&q=80',
                      fit: BoxFit.cover,
                      alignment: Alignment.center,
                      errorBuilder: (context, error, stackTrace) {
                        return Container(
                          decoration: const BoxDecoration(
                            gradient: LinearGradient(
                              colors: [Color(0xFF0D5C75), Color(0xFF003844)],
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  // Oceanic Scrim Gradient for high contrast and readability
                  Positioned.fill(
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            const Color(0xFF032B3A).withValues(alpha: 0.68),
                            const Color(0xFF023E4D).withValues(alpha: 0.58),
                            const Color(0xFF011E26).withValues(alpha: 0.78),
                          ],
                          stops: const [0.0, 0.5, 1.0],
                        ),
                      ),
                    ),
                  ),
                  // Content
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 22,
                    ),
                    child: Column(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.22),
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(
                              color: Colors.white.withValues(alpha: 0.35),
                              width: 0.8,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.15),
                                blurRadius: 4,
                                offset: const Offset(0, 1),
                              ),
                            ],
                          ),
                          child: const Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.local_fire_department,
                                color: Color(0xFFFFD54F),
                                size: 16,
                              ),
                              SizedBox(width: 5),
                              Text(
                                'ƯU ĐÃI ĐỘC QUYỀN HÔM NAY',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.5,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'Săn Deal Thể Thao Biển Giảm Sốc',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 21,
                            fontWeight: FontWeight.w900,
                            height: 1.25,
                            shadows: [
                              Shadow(
                                color: Colors.black45,
                                blurRadius: 6,
                                offset: Offset(0, 2),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          'Số lượng suất ưu đãi có hạn theo từng khung giờ',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.95),
                            fontSize: 12.5,
                            fontWeight: FontWeight.w500,
                            shadows: const [
                              Shadow(
                                color: Colors.black38,
                                blurRadius: 4,
                                offset: Offset(0, 1),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 18),

                        // Countdown Timer Boxes
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            _buildBigTimeBox('$hours', 'GIỜ'),
                            const Padding(
                              padding: EdgeInsets.symmetric(horizontal: 6, vertical: 8),
                              child: Text(
                                ':',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 22,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ),
                            _buildBigTimeBox('$minutes', 'PHÚT'),
                            const Padding(
                              padding: EdgeInsets.symmetric(horizontal: 6, vertical: 8),
                              child: Text(
                                ':',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 22,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ),
                            _buildBigTimeBox('$seconds', 'GIÂY'),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // 2. SECTION HEADER
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppShapes.gutterMobile,
                ),
                child: Text(
                  'Tất cả ưu đãi chớp nhoáng',
                  style: AppTypography.headlineSm(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w800,
                  ).copyWith(fontSize: 16),
                ),
              ),

              const SizedBox(height: 12),

              // 3. GRID OF FLASH SALE ITEMS
              if (_loading && _items.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(40),
                  child: Center(
                    child: CircularProgressIndicator(
                      color: Color(0xFFFF3D00),
                    ),
                  ),
                )
              else if (_error != null && _items.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(30),
                  child: Center(
                    child: Column(
                      children: [
                        const Icon(
                          Icons.error_outline,
                          size: 40,
                          color: AppColors.error,
                        ),
                        const SizedBox(height: 8),
                        Text(_error!),
                        const SizedBox(height: 12),
                        ElevatedButton(
                          onPressed: _loadFlashSales,
                          child: const Text('Thử lại'),
                        ),
                      ],
                    ),
                  ),
                )
              else
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppShapes.gutterMobile,
                  ),
                  child: GridView.builder(
                    physics: const NeverScrollableScrollPhysics(),
                    shrinkWrap: true,
                    itemCount: _items.length,
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 14,
                      childAspectRatio: 0.65,
                    ),
                    itemBuilder: (context, index) {
                      final item = _items[index];
                      return _buildGridCard(context, item);
                    },
                  ),
                ),

              const SizedBox(height: 30),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGridCard(BuildContext context, FlashSaleModel item) {
    final discountPercent = item.actualDiscountPercent.round();

    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => CatalogDetailScreen(serviceId: item.serviceId),
          ),
        );
      },
      borderRadius: BorderRadius.circular(14),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: const Color(0xFFFFCC80).withValues(alpha: 0.6),
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
            // IMAGE & BADGES
            Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(13),
                  ),
                  child: Image.network(
                    item.serviceImage,
                    width: double.infinity,
                    height: 120,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) => Container(
                      height: 120,
                      color: const Color(0xFFE2E8F0),
                      child: const Center(
                        child: Icon(
                          Icons.sailing_rounded,
                          color: Color(0xFF94A3B8),
                          size: 36,
                        ),
                      ),
                    ),
                  ),
                ),
                // DISCOUNT PERCENT BADGE
                if (discountPercent > 0)
                  Positioned(
                    top: 8,
                    left: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 3.5,
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
                          fontSize: 11.5,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.2,
                        ),
                      ),
                    ),
                  ),
                // REMAINING QUOTA
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
                        color: Colors.black.withValues(alpha: 0.7),
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
                padding: const EdgeInsets.all(10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      item.serviceName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: AppTypography.labelMd(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w700,
                      ).copyWith(
                        fontSize: 13,
                        height: 1.25,
                      ),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _formatPrice(item.salePrice),
                          style: AppTypography.labelLg(
                            color: const Color(0xFFD32F2F),
                            fontWeight: FontWeight.w900,
                          ).copyWith(
                            fontSize: 15,
                          ),
                        ),
                        if (item.originalPrice > item.salePrice)
                          Text(
                            _formatPrice(item.originalPrice),
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ).copyWith(
                              decoration: TextDecoration.lineThrough,
                              fontSize: 11,
                            ),
                          ),
                        const SizedBox(height: 6),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [Color(0xFFFF5252), Color(0xFFFF7A00)],
                            ),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          alignment: Alignment.center,
                          child: const Text(
                            'Săn deal ngay',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 11.5,
                              fontWeight: FontWeight.w800,
                            ),
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
