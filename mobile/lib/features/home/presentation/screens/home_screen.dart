import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/library_store.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_bottom_nav_bar.dart';
import '../../../cart/presentation/screens/cart_screen.dart';
import '../../../chat/presentation/screens/ai_assistant_screen.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';
import '../../../notifications/presentation/screens/notifications_screen.dart';
import '../../../orders/presentation/screens/my_orders_screen.dart';
import '../../../profile/presentation/screens/profile_screen.dart';
import '../../../search/presentation/screens/search_screen.dart';
import '../../../wishlist/presentation/screens/wishlist_screen.dart';
import '../../domain/models/home_models.dart';
import 'flash_sale_screen.dart';
import '../widgets/ai_assistant_section.dart';
import '../widgets/experience_card.dart';
import '../widgets/flash_sale_section.dart';
import '../widgets/hero_search_widget.dart';
import '../widgets/home_footer.dart';
import '../widgets/home_header.dart';
import '../widgets/recently_viewed_section.dart';
import '../widgets/weather_ocean_section.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentNavIndex = 0;
  final GlobalKey<MyOrdersScreenState> _ordersKey =
      GlobalKey<MyOrdersScreenState>();
  List<ExperienceItem> _experiences = [];
  List<FlashSaleModel> _flashSales = [];
  final _catalog = CatalogRepository();
  final _library = LibraryStore.instance;
  bool _loading = true;
  String? _error;

  late final ScrollController _exploreScrollController;
  bool _isScrolled = false;

  String _formatDuration(int minutes) {
    if (minutes <= 0) return '2 giờ';
    if (minutes >= 60 && minutes % 60 == 0) {
      return '${minutes ~/ 60} giờ';
    }
    if (minutes >= 60) {
      return '${(minutes / 60).toStringAsFixed(1)} giờ';
    }
    return '$minutes phút';
  }

  Future<void> _loadCatalog() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        _catalog.search(size: 7, sortBy: 'popularity'),
        _catalog.flashSales(),
      ]);
      if (!mounted) return;
      final apiServices = (results[0] as CatalogPage).items;
      final apiFlashSales = results[1] as List<FlashSaleModel>;
      setState(() {
        _flashSales = apiFlashSales;
        _experiences = apiServices
            .take(7)
            .map(
              (s) => ExperienceItem(
                id: s.id,
                title: s.name,
                location: s.locationName.isNotEmpty
                    ? s.locationName
                    : s.address,
                duration: _formatDuration(s.durationMinutes),
                rating: s.avgRating,
                reviewCount: s.ratingCount,
                price: s.price,
                imageUrl: s.imageUrls.isEmpty
                    ? 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80'
                    : s.imageUrls.first,
                isVerified: true,
              ),
            )
            .toList();
      });
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void initState() {
    super.initState();
    _exploreScrollController = ScrollController();
    _exploreScrollController.addListener(() {
      final scrolled = _exploreScrollController.hasClients &&
          _exploreScrollController.offset > 40;
      if (scrolled != _isScrolled) {
        setState(() => _isScrolled = scrolled);
      }
    });
    _loadCatalog();
    _library.addListener(_libraryChanged);
  }

  void _libraryChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _exploreScrollController.dispose();
    _library.removeListener(_libraryChanged);
    super.dispose();
  }

  Future<void> _toggleFavorite(int index) async {
    final item = _experiences[index];
    try {
      await _library.toggle(
        SavedService(
          serviceId: item.id,
          name: item.title,
          imageUrl: item.imageUrl,
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

  void _openDetail(ExperienceItem item) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => CatalogDetailScreen(serviceId: item.id),
      ),
    );
  }

  void _openSearch([String? query, String? category]) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            SearchScreen(initialQuery: query, initialCategory: category),
      ),
    );
  }

  void _openAiAssistant([String? prompt]) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => AiAssistantScreen(initialPrompt: prompt),
      ),
    );
  }

  void _openNotifications() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const NotificationsScreen()),
    );
  }

  void _openCart() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const CartScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true,
      extendBodyBehindAppBar: _currentNavIndex == 0,
      backgroundColor: AppColors.surface,
      appBar: _currentNavIndex == 0
          ? HomeHeader(
              isScrolled: _isScrolled,
              onNotificationTap: _openNotifications,
              onCartTap: _openCart,
              onSearchTap: () => _openSearch(),
            )
          : null,
      bottomNavigationBar: AppBottomNavBar(
        currentIndex: _currentNavIndex,
        onTap: (index) {
          if (index == 2) {
            _ordersKey.currentState?.reload();
          }
          setState(() => _currentNavIndex = index);
        },
      ),
      body: IndexedStack(
        index: _currentNavIndex,
        children: [
          _buildExploreBody(),
          WishlistScreen(onExplore: () => setState(() => _currentNavIndex = 0)),
          MyOrdersScreen(
            key: _ordersKey,
            onExplore: () => setState(() => _currentNavIndex = 0),
          ),
          const ProfileScreen(),
        ],
      ),
    );
  }

  Widget _buildExploreBody() {
    return SingleChildScrollView(
      controller: _exploreScrollController,
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. HERO SECTION & FLOATING SEARCH CARD
          HeroSearchWidget(
            onExploreTap: () => _openSearch(),
            onAiSuggestTap: _openAiAssistant,
            onSearchTap: (location, date, guests) {
              final query = (location != null &&
                      location != 'Mỹ Khê, Sơn Trà, Non Nước' &&
                      location != 'Tất cả khu vực biển')
                  ? location
                  : null;
              _openSearch(query);
            },
          ),

          // 2. FLASH SALE SECTION ("Giờ vàng săn deal" - Mặc định tối đa 7 deal)
          if (_flashSales.isNotEmpty)
            FlashSaleSection(
              items: _flashSales.take(7).toList(),
              onItemTap: (item) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => CatalogDetailScreen(
                      serviceId: item.serviceId,
                    ),
                  ),
                );
              },
              onSeeAll: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => FlashSaleScreen(initialItems: _flashSales),
                  ),
                );
              },
            ),

          // 3. FEATURED EXPERIENCES ("Trải nghiệm nổi bật tại Đà Nẵng")
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                LocalizedText(
                  'Trải nghiệm nổi bật tại Đà Nẵng',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.headlineSm(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w800,
                  ).copyWith(fontSize: 17.5),
                ),
                const SizedBox(height: 12),

                if (_loading) const LinearProgressIndicator(),
                if (_error != null) ...[
                  Text(_error!),
                  TextButton(
                    onPressed: _loadCatalog,
                    child: const LocalizedText('Thử lại'),
                  ),
                ],
                // Cards List
                Column(
                  children: [
                    for (int index = 0; index < _experiences.length; index++) ...[
                      if (index > 0) const SizedBox(height: AppShapes.spaceLg),
                      ExperienceCard(
                        item: _experiences[index].copyWith(
                          isFavorite: _library.contains(_experiences[index].id),
                        ),
                        onFavoriteToggle: _library.busy(_experiences[index].id)
                            ? null
                            : () => _toggleFavorite(index),
                        onTap: () => _openDetail(_experiences[index]),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: AppShapes.spaceLg),

          // 5. AI SMART ASSISTANT SECTION
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
            ),
            child: AiAssistantSection(
              onPlanWithAiTap: _openAiAssistant,
              onPromptSelected: (prompt) => _openAiAssistant(prompt),
              onExploreAllTap: () => _openSearch(),
            ),
          ),
          const SizedBox(height: AppShapes.spaceLg),

          // 6. REAL-TIME WEATHER & OCEAN CONDITIONS
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: AppShapes.gutterMobile),
            child: WeatherOceanSection(),
          ),
          const SizedBox(height: AppShapes.spaceLg),

          // 8. RECENTLY VIEWED CAROUSEL
          RecentlyViewedSection(
            store: _library,
            onItemTap: (item) => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => CatalogDetailScreen(serviceId: item.serviceId),
              ),
            ),
          ),
          const SizedBox(height: AppShapes.spaceLg),

          // 9. FOOTER
          const HomeFooter(),
          const SizedBox(height: 100),
        ],
      ),
    );
  }
}
