import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/library_store.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';

class WishlistScreen extends StatefulWidget {
  final VoidCallback? onExplore;
  final LibraryStore? store;

  const WishlistScreen({super.key, this.onExplore, this.store});

  @override
  State<WishlistScreen> createState() => _WishlistScreenState();
}

class _WishlistScreenState extends State<WishlistScreen> {
  late final LibraryStore store;
  final CatalogRepository _catalog = CatalogRepository();
  final Map<String, ServiceModel> _serviceCache = {};

  @override
  void initState() {
    super.initState();
    store = widget.store ?? LibraryStore.instance;
    store.addListener(_onStoreChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) store.loadWishlist();
    });
  }

  @override
  void dispose() {
    store.removeListener(_onStoreChanged);
    super.dispose();
  }

  void _onStoreChanged() {
    for (final item in store.wishlist) {
      if (!_serviceCache.containsKey(item.serviceId)) {
        _hydrateService(item.serviceId);
      }
    }
    if (mounted) setState(() {});
  }

  Future<void> _hydrateService(String serviceId) async {
    try {
      final detail = await _catalog.detail(serviceId);
      if (mounted) {
        setState(() {
          _serviceCache[serviceId] = detail;
        });
      }
    } catch (_) {}
  }

  ServiceModel _resolveServiceModel(SavedService saved) {
    final cached = _serviceCache[saved.serviceId];
    if (cached != null) return cached;

    return ServiceModel(
      id: saved.serviceId,
      vendorId: 'vnd-001',
      vendorName: 'Danang Ocean Club',
      categoryId: 'cat-001',
      categoryName: 'Trải nghiệm biển',
      name: saved.name,
      slug: saved.serviceId,
      description: '',
      price: 350000,
      durationMinutes: 120,
      capacityPerSlot: 15,
      locationName: 'Biển Mỹ Khê, Đà Nẵng',
      address: 'Biển Mỹ Khê, Đà Nẵng',
      latitude: 16.0601,
      longitude: 108.2465,
      waiverContent: '',
      avgRating: 4.8,
      ratingCount: 24,
      imageUrls: [
        if (saved.imageUrl != null && saved.imageUrl!.isNotEmpty)
          saved.imageUrl!,
      ],
      isFavorite: true,
    );
  }

  Future<void> _removeFromWishlist(SavedService saved) async {
    try {
      await store.toggle(saved);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(
            'Đã bỏ ${saved.name} khỏi danh sách yêu thích.',
          ),
          duration: const Duration(seconds: 1),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
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

  @override
  Widget build(BuildContext context) {
    final items = store.wishlist;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText(
              'Yêu thích (${items.length})',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            LocalizedText(
              'Trải nghiệm biển bạn quan tâm & lưu lại',
              style: AppTypography.bodySm(
                color: AppColors.onSurfaceVariant,
              ).copyWith(fontSize: 11),
            ),
          ],
        ),
      ),
      body: items.isEmpty
          ? _buildEmptyState()
          : RefreshIndicator(
              onRefresh: store.loadWishlist,
              child: ListView.separated(
                padding: const EdgeInsets.all(AppShapes.gutterMobile),
                itemCount: items.length,
                separatorBuilder: (_, unused) =>
                    const SizedBox(height: AppShapes.spaceMd),
                itemBuilder: (context, index) {
                  final saved = items[index];
                  final srv = _resolveServiceModel(saved);
                  return _buildWishlistCard(srv, saved);
                },
              ),
            ),
    );
  }

  Widget _buildWishlistCard(ServiceModel item, SavedService saved) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        boxShadow: AppShapes.shadowLevel1,
        border: Border.all(color: AppColors.borderSubtle),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Image with Favorite active button
          AspectRatio(
            aspectRatio: 16 / 9,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.network(
                  item.imageUrls.isNotEmpty
                      ? item.imageUrls.first
                      : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80',
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => Container(
                    color: AppColors.surfaceContainerHigh,
                    child: const Icon(Icons.waves, color: AppColors.secondary),
                  ),
                ),
                // Vendor pill
                Positioned(
                  top: 10,
                  left: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withValues(alpha: 0.9),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.verified,
                          size: 12,
                          color: AppColors.onSecondary,
                        ),
                        const SizedBox(width: 4),
                        LocalizedText(
                          item.vendorName.isNotEmpty
                              ? item.vendorName
                              : 'Danang Ocean Club',
                          style: AppTypography.labelSm(
                            color: AppColors.onSecondary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                // Remove favorite button
                Positioned(
                  top: 8,
                  right: 8,
                  child: CircleAvatar(
                    backgroundColor: Colors.white.withValues(alpha: 0.85),
                    radius: 18,
                    child: IconButton(
                      icon: const Icon(
                        Icons.favorite,
                        color: AppColors.primary,
                        size: 20,
                      ),
                      splashRadius: 18,
                      onPressed: store.busy(saved.serviceId)
                          ? null
                          : () => _removeFromWishlist(saved),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppShapes.spaceSm),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.star,
                      size: 15,
                      color: AppColors.starRating,
                    ),
                    const SizedBox(width: 4),
                    LocalizedText(
                      item.avgRating.toStringAsFixed(1),
                      style: AppTypography.labelMd(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(width: 4),
                    LocalizedText(
                      '(${item.ratingCount})',
                      style: AppTypography.bodySm(color: AppColors.outline),
                    ),
                    const Spacer(),
                    LocalizedText(
                      '${item.durationMinutes} phút',
                      style: AppTypography.labelSm(color: AppColors.secondary),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                LocalizedText(
                  item.name,
                  style: AppTypography.headlineSm(color: AppColors.onSurface),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Icon(
                      Icons.location_on,
                      size: 14,
                      color: AppColors.tertiary,
                    ),
                    const SizedBox(width: 4),
                    Expanded(
                      child: LocalizedText(
                        item.locationName.isNotEmpty
                            ? item.locationName
                            : item.address,
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            'Giá trọn gói',
                            style: AppTypography.labelSm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          LocalizedText(
                            _formatPrice(item.price),
                            style: AppTypography.headlineSm(
                              color: AppColors.primary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    AppPillButton(
                      label: 'Xem & Đặt chỗ',
                      variant: AppButtonVariant.primary,
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => CatalogDetailScreen(
                              serviceId: item.id,
                              library: store,
                            ),
                          ),
                        );
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.favorite_border,
                size: 36,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: 16),
            LocalizedText(
              'Chưa có trải nghiệm yêu thích',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 8),
            LocalizedText(
              'Khám phá các hoạt động chèo SUP, lặn biển, cano tại Đà Nẵng và nhấn biểu tượng trái tim để lưu lại.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 20),
            if (widget.onExplore != null)
              AppPillButton(
                label: 'Khám phá ngay',
                variant: AppButtonVariant.primary,
                onPressed: widget.onExplore,
              ),
          ],
        ),
      ),
    );
  }
}
