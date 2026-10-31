import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/library_store.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class RecentlyViewedSection extends StatefulWidget {
  final LibraryStore? store;
  final VoidCallback? onClearHistory;
  final ValueChanged<SavedService>? onItemTap;

  const RecentlyViewedSection({
    super.key,
    this.store,
    this.onClearHistory,
    this.onItemTap,
  });

  @override
  State<RecentlyViewedSection> createState() => _RecentlyViewedSectionState();
}

class _RecentlyViewedSectionState extends State<RecentlyViewedSection> {
  late final LibraryStore store;
  final CatalogRepository _catalog = CatalogRepository();
  final Map<String, ServiceModel> _serviceCache = {};

  @override
  void initState() {
    super.initState();
    store = widget.store ?? LibraryStore.instance;
    store.addListener(_onStoreChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) store.loadRecent();
    });
  }

  @override
  void dispose() {
    store.removeListener(_onStoreChanged);
    super.dispose();
  }

  void _onStoreChanged() {
    for (final item in store.recent) {
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
    if (store.recent.isEmpty) {
      return const SizedBox.shrink();
    }
    final displayItems = store.recent
        .map((saved) {
          final cached = _serviceCache[saved.serviceId];
          return (
            saved: saved,
            title: saved.name,
            location: cached != null && cached.locationName.isNotEmpty
                ? cached.locationName
                : 'Đà Nẵng',
            price: cached?.price ?? 350000,
            imageUrl:
                saved.imageUrl ??
                (cached != null && cached.imageUrls.isNotEmpty
                    ? cached.imageUrls.first
                    : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80'),
          );
        })
        .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Header
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: AppShapes.gutterMobile,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    const Icon(
                      Icons.history,
                      color: AppColors.secondary,
                      size: 20,
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: LocalizedText(
                        'Gần đây bạn xem',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              InkWell(
                onTap: widget.onClearHistory ?? store.loadRecent,
                child: LocalizedText(
                  'Xóa lịch sử',
                  style: AppTypography.labelSm(color: AppColors.outline),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // Horizontal Carousel
        SizedBox(
          height: 220,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            scrollDirection: Axis.horizontal,
            itemCount: displayItems.length,
            separatorBuilder: (context, index) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              final item = displayItems[index];

              return Container(
                width: 230,
                padding: const EdgeInsets.all(AppShapes.spaceXs),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusDefault,
                  boxShadow: AppShapes.shadowLevel1,
                  border: Border.all(
                    color: AppColors.borderSubtle.withValues(alpha: 0.6),
                    width: 0.8,
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Image with Location Tag
                    ClipRRect(
                      borderRadius: AppShapes.radiusDefault,
                      child: Stack(
                        children: [
                          SizedBox(
                            height: 110,
                            width: double.infinity,
                            child: Image.network(
                              item.imageUrl,
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) =>
                                  Container(
                                    color: AppColors.surfaceContainerHighest,
                                  ),
                            ),
                          ),
                          Positioned(
                            bottom: 8,
                            left: 8,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: AppColors.inverseSurface.withValues(
                                  alpha: 0.8,
                                ),
                                borderRadius: AppShapes.radiusFull,
                              ),
                              child: LocalizedText(
                                item.location,
                                style: AppTypography.labelSm(
                                  color: AppColors.inverseOnSurface,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),

                    // Title & Price
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText(
                            item.title,
                            style: AppTypography.labelLg(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w700,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          LocalizedText(
                            _formatPrice(item.price),
                            style: AppTypography.labelMd(
                              color: AppColors.primary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Spacer(),

                    // Fast Book Button
                    InkWell(
                      onTap: () => widget.onItemTap?.call(item.saved),
                      borderRadius: AppShapes.radiusFull,
                      child: Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        decoration: BoxDecoration(
                          color: AppColors.secondary,
                          borderRadius: AppShapes.radiusFull,
                        ),
                        child: Center(
                          child: LocalizedText(
                            'Đặt nhanh',
                            style: AppTypography.labelSm(
                              color: AppColors.onSecondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
