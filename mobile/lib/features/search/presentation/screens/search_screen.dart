import 'dart:async';
import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';

class SearchScreen extends StatefulWidget {
  final String? initialQuery;
  final String? initialCategory;
  final CatalogRepository? repository;

  const SearchScreen({
    super.key,
    this.initialQuery,
    this.initialCategory,
    this.repository,
  });

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  late final CatalogRepository _repository;
  late TextEditingController _searchController;
  bool _isMapView = false;
  String? _selectedLocation;
  String? _selectedCategory;
  String? _selectedTimeOfDay;
  int? _maxPrice;

  List<CategoryModel> _categories = [];
  List<ServiceModel> _items = [];
  bool _loading = true;
  Timer? _debounce;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _repository = widget.repository ?? CatalogRepository();
    _searchController = TextEditingController(text: widget.initialQuery ?? '');
    _selectedCategory = widget.initialCategory;
    _loadCategories();
    _search();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadCategories() async {
    try {
      final categories = await _repository.categories();
      if (mounted) {
        setState(() {
          _categories = categories;
        });
      }
    } catch (_) {}
  }

  String? _resolveCategoryId(String? categorySelection) {
    if (categorySelection == null) return null;
    for (final cat in _categories) {
      if (cat.id == categorySelection ||
          cat.name.toLowerCase() == categorySelection.toLowerCase() ||
          cat.nameEn.toLowerCase() == categorySelection.toLowerCase()) {
        return cat.id;
      }
    }
    return null;
  }

  String _displayCategoryName(String categorySelection) {
    for (final cat in _categories) {
      if (cat.id == categorySelection) {
        return AppLanguage.instance.code == 'en' && cat.nameEn.isNotEmpty
            ? cat.nameEn
            : cat.name;
      }
    }
    return categorySelection;
  }

  Future<void> _search() async {
    _debounce?.cancel();
    final generation = ++_generation;
    setState(() {
      _loading = true;
    });
    try {
      final resolvedCategoryId = _resolveCategoryId(_selectedCategory);
      final result = await _repository.search(
        keyword: _searchController.text.trim(),
        categoryId: resolvedCategoryId,
        maxPrice: _maxPrice,
        page: 0,
      );
      if (!mounted || generation != _generation) return;
      setState(() {
        _items = result.items;
      });
    } catch (_) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _items = [];
      });
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  void _onSearchChanged(String _) {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _search);
  }

  int get _activeFilterCount {
    int count = 0;
    if (_selectedLocation != null) count++;
    if (_selectedCategory != null) count++;
    if (_selectedTimeOfDay != null) count++;
    if (_maxPrice != null) count++;
    return count;
  }

  List<ServiceModel> get _filteredServices {
    final resolvedCategoryId = _resolveCategoryId(_selectedCategory);
    return _items.where((srv) {
      final query = _searchController.text.trim().toLowerCase();
      if (query.isNotEmpty) {
        final matchesName = srv.name.toLowerCase().contains(query);
        final matchesVendor = srv.vendorName.toLowerCase().contains(query);
        final matchesLoc =
            srv.locationName.toLowerCase().contains(query) ||
            srv.address.toLowerCase().contains(query);
        if (!matchesName && !matchesVendor && !matchesLoc) return false;
      }

      if (_selectedLocation != null &&
          !srv.locationName.toLowerCase().contains(
            _selectedLocation!.toLowerCase(),
          ) &&
          !srv.address.toLowerCase().contains(
            _selectedLocation!.toLowerCase(),
          )) {
        return false;
      }

      if (_selectedCategory != null && resolvedCategoryId == null) {
        final catLower = _selectedCategory!.toLowerCase();
        if (!srv.categoryName.toLowerCase().contains(catLower) &&
            !srv.name.toLowerCase().contains(catLower)) {
          return false;
        }
      }

      if (_maxPrice != null && srv.price > _maxPrice!) {
        return false;
      }

      return true;
    }).toList();
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

  void _openFilterModal() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final categoryOptions = _categories.isNotEmpty
                ? _categories
                      .map((c) => {'id': c.id, 'label': c.name})
                      .toList()
                : [
                    'Chèo SUP',
                    'Lặn biển',
                    'Cano lướt sóng',
                    'Lướt ván',
                  ].map((c) => {'id': c, 'label': c}).toList();

            return Container(
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).padding.bottom + 16,
                top: 12,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: const Color(0xFFCBD5E1),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppShapes.gutterMobile,
                      vertical: 12,
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        LocalizedText(
                          'Bộ lọc trải nghiệm',
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                        TextButton(
                          onPressed: () {
                            setModalState(() {
                              _selectedLocation = null;
                              _selectedCategory = null;
                              _selectedTimeOfDay = null;
                              _maxPrice = null;
                            });
                            setState(() {});
                            _search();
                          },
                          child: LocalizedText(
                            'Đặt lại',
                            style: AppTypography.labelMd(
                              color: AppColors.secondary,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.all(AppShapes.gutterMobile),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // VÙNG BIỂN
                        LocalizedText(
                          'Vùng biển',
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: ['Mỹ Khê', 'Sơn Trà', 'Non Nước'].map((
                            loc,
                          ) {
                            final isSel = _selectedLocation == loc;
                            return ChoiceChip(
                              label: LocalizedText(loc),
                              selected: isSel,
                              onSelected: (val) {
                                setModalState(() {
                                  _selectedLocation = val ? loc : null;
                                });
                                setState(() {});
                              },
                              backgroundColor: const Color(0xFFF0F9FA),
                              selectedColor: AppColors.secondary,
                              side: BorderSide(
                                color: isSel
                                    ? AppColors.secondary
                                    : const Color(0xFFB4E3E8),
                                width: 1,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              labelStyle: TextStyle(
                                color: isSel
                                    ? Colors.white
                                    : AppColors.onSurface,
                                fontWeight: isSel
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                            );
                          }).toList(),
                        ),
                        const SizedBox(height: 16),

                        // DANH MỤC
                        LocalizedText(
                          'Loại hoạt động',
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: categoryOptions.map((cat) {
                            final catId = cat['id']!;
                            final catLabel = cat['label']!;
                            final isSel =
                                _selectedCategory == catId ||
                                _selectedCategory == catLabel;
                            return ChoiceChip(
                              label: LocalizedText(catLabel),
                              selected: isSel,
                              onSelected: (val) {
                                setModalState(() {
                                  _selectedCategory = val ? catId : null;
                                });
                                setState(() {});
                                _search();
                              },
                              backgroundColor: const Color(0xFFF0F9FA),
                              selectedColor: AppColors.secondary,
                              side: BorderSide(
                                color: isSel
                                    ? AppColors.secondary
                                    : const Color(0xFFB4E3E8),
                                width: 1,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              labelStyle: TextStyle(
                                color: isSel
                                    ? Colors.white
                                    : AppColors.onSurface,
                                fontWeight: isSel
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                            );
                          }).toList(),
                        ),
                        const SizedBox(height: 16),

                        // GIÁ TỐI ĐA
                        LocalizedText(
                          'Mức giá',
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            {'label': 'Dưới 300k', 'val': 300000},
                            {'label': 'Dưới 500k', 'val': 500000},
                            {'label': 'Dưới 1 triệu', 'val': 1000000},
                          ].map((item) {
                            final val = item['val'] as int;
                            final isSel = _maxPrice == val;
                            return ChoiceChip(
                              label: LocalizedText(item['label'] as String),
                              selected: isSel,
                              onSelected: (selected) {
                                setModalState(() {
                                  _maxPrice = selected ? val : null;
                                });
                                setState(() {});
                                _search();
                              },
                              backgroundColor: const Color(0xFFF0F9FA),
                              selectedColor: AppColors.secondary,
                              side: BorderSide(
                                color: isSel
                                    ? AppColors.secondary
                                    : const Color(0xFFB4E3E8),
                                width: 1,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              labelStyle: TextStyle(
                                color: isSel
                                    ? Colors.white
                                    : AppColors.onSurface,
                                fontWeight: isSel
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppShapes.gutterMobile,
                    ),
                    child: SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(24),
                          ),
                        ),
                        onPressed: () => Navigator.pop(context),
                        child: LocalizedText(
                          'Áp dụng kết quả (${_filteredServices.length})',
                          style: AppTypography.labelLg(color: Colors.white),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final services = _filteredServices;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        titleSpacing: 0,
        title: Container(
          height: 44,
          margin: const EdgeInsets.only(right: 8),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: AppColors.borderSubtle),
          ),
          child: TextField(
            controller: _searchController,
            onTapOutside: (_) => FocusManager.instance.primaryFocus?.unfocus(),
            onChanged: _onSearchChanged,
            onSubmitted: (_) => _search(),
            decoration: InputDecoration(
              hintText: tr(context, 'Tìm hoạt động, bãi biển, nhà cung cấp...'),
              hintStyle: AppTypography.bodySm(color: AppColors.outline),
              prefixIcon: const Icon(
                Icons.search,
                size: 20,
                color: AppColors.secondary,
              ),
              suffixIcon: _searchController.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () {
                        _searchController.clear();
                        setState(() {});
                        _search();
                      },
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 10),
            ),
          ),
        ),
        actions: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              IconButton(
                icon: const Icon(Icons.tune, color: AppColors.onSurface),
                onPressed: _openFilterModal,
              ),
              if (_activeFilterCount > 0)
                Positioned(
                  top: 6,
                  right: 6,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                    ),
                    constraints: const BoxConstraints(
                      minWidth: 16,
                      minHeight: 16,
                    ),
                    child: Center(
                      child: LocalizedText(
                        '$_activeFilterCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          // ACTIVE CHIPS ROW
          if (_activeFilterCount > 0)
            Container(
              height: 42,
              padding: const EdgeInsets.symmetric(
                horizontal: AppShapes.gutterMobile,
              ),
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  if (_selectedLocation != null)
                    _buildActiveChip(_selectedLocation!, () {
                      setState(() => _selectedLocation = null);
                    }),
                  if (_selectedCategory != null)
                    _buildActiveChip(
                      _displayCategoryName(_selectedCategory!),
                      () {
                        setState(() => _selectedCategory = null);
                        _search();
                      },
                    ),
                  if (_maxPrice != null)
                    _buildActiveChip('Giá <= ${_formatPrice(_maxPrice!)}', () {
                      setState(() => _maxPrice = null);
                      _search();
                    }),
                  TextButton(
                    onPressed: () {
                      setState(() {
                        _selectedLocation = null;
                        _selectedCategory = null;
                        _selectedTimeOfDay = null;
                        _maxPrice = null;
                      });
                      _search();
                    },
                    child: LocalizedText(
                      'Xóa tất cả',
                      style: AppTypography.labelSm(color: AppColors.primary),
                    ),
                  ),
                ],
              ),
            ),

          // RESULTS COUNTER & VIEW TOGGLE (LIST / MAP)
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
              vertical: 8,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText(
                        '${services.length} trải nghiệm',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                        ),
                      ),
                      LocalizedText(
                        'Phù hợp điều kiện sóng biển hôm nay',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ).copyWith(fontSize: 11),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.all(2),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      InkWell(
                        onTap: () => setState(() => _isMapView = false),
                        borderRadius: BorderRadius.circular(18),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: !_isMapView
                                ? AppColors.surfaceContainerLowest
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(18),
                            boxShadow: !_isMapView
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withValues(
                                        alpha: 0.06,
                                      ),
                                      blurRadius: 4,
                                    ),
                                  ]
                                : null,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.view_agenda,
                                size: 15,
                                color: !_isMapView
                                    ? AppColors.secondary
                                    : AppColors.onSurfaceVariant,
                              ),
                              const SizedBox(width: 4),
                              LocalizedText(
                                'Danh sách',
                                style: AppTypography.labelSm(
                                  color: !_isMapView
                                      ? AppColors.secondary
                                      : AppColors.onSurfaceVariant,
                                  fontWeight: !_isMapView
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      InkWell(
                        onTap: () => setState(() => _isMapView = true),
                        borderRadius: BorderRadius.circular(18),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: _isMapView
                                ? AppColors.surfaceContainerLowest
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(18),
                            boxShadow: _isMapView
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withValues(
                                        alpha: 0.06,
                                      ),
                                      blurRadius: 4,
                                    ),
                                  ]
                                : null,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.map,
                                size: 15,
                                color: _isMapView
                                    ? AppColors.secondary
                                    : AppColors.onSurfaceVariant,
                              ),
                              const SizedBox(width: 4),
                              LocalizedText(
                                'Bản đồ',
                                style: AppTypography.labelSm(
                                  color: _isMapView
                                      ? AppColors.secondary
                                      : AppColors.onSurfaceVariant,
                                  fontWeight: _isMapView
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          if (_loading) const LinearProgressIndicator(minHeight: 2),

          // CONTENT: LIST OR MAP
          Expanded(
            child: _isMapView
                ? _buildMapViewMock(services)
                : services.isEmpty && !_loading
                ? _buildEmptyState()
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppShapes.gutterMobile,
                      vertical: 8,
                    ),
                    itemCount: services.length,
                    separatorBuilder: (_, unused) =>
                        const SizedBox(height: AppShapes.spaceMd),
                    itemBuilder: (context, index) {
                      final item = services[index];
                      return _buildExperienceSearchCard(item);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildActiveChip(String label, VoidCallback onRemove) {
    return Container(
      margin: const EdgeInsets.only(right: 6, top: 4, bottom: 4),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.secondaryContainer.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          LocalizedText(
            label,
            style: AppTypography.labelSm(
              color: AppColors.onSecondaryContainer,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(width: 4),
          InkWell(
            onTap: onRemove,
            child: const Icon(
              Icons.close,
              size: 14,
              color: AppColors.onSecondaryContainer,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildExperienceSearchCard(ServiceModel srv) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        boxShadow: AppShapes.shadowLevel1,
        border: Border.all(color: AppColors.borderSubtle),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CatalogDetailScreen(
                serviceId: srv.id,
                repository: _repository,
              ),
            ),
          );
        },
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Image with 16:9 ratio & Badges
            AspectRatio(
              aspectRatio: 16 / 9,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.network(
                    srv.imageUrls.isNotEmpty
                        ? srv.imageUrls.first
                        : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80',
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => Container(
                      color: AppColors.surfaceContainerHigh,
                      child: const Icon(
                        Icons.waves,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                  // Vendor Badge
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
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.verified,
                            size: 12,
                            color: AppColors.onSecondary,
                          ),
                          const SizedBox(width: 4),
                          LocalizedText(
                            srv.vendorName.isNotEmpty
                                ? srv.vendorName
                                : 'DanaSea Partner',
                            style: AppTypography.labelSm(
                              color: AppColors.onSecondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  // Ocean condition badge
                  Positioned(
                    bottom: 10,
                    left: 10,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceBright.withValues(alpha: 0.92),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.waves,
                            size: 12,
                            color: AppColors.secondary,
                          ),
                          const SizedBox(width: 4),
                          LocalizedText(
                            'Sóng êm: 0.4m',
                            style: AppTypography.labelSm(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
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
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            Icons.star,
                            size: 15,
                            color: AppColors.starRating,
                          ),
                          const SizedBox(width: 4),
                          LocalizedText(
                            srv.avgRating.toStringAsFixed(1),
                            style: AppTypography.labelMd(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(width: 4),
                          LocalizedText(
                            '(${srv.ratingCount})',
                            style: AppTypography.bodySm(
                              color: AppColors.outline,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(width: 6),
                      Flexible(
                        child: LocalizedText(
                          'Khởi hành 05:00',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: AppTypography.labelSm(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  LocalizedText(
                    srv.name,
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
                          srv.locationName.isNotEmpty
                              ? srv.locationName
                              : srv.address,
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const Icon(
                        Icons.schedule,
                        size: 14,
                        color: AppColors.tertiary,
                      ),
                      const SizedBox(width: 4),
                      LocalizedText(
                        '${srv.durationMinutes}p',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
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
                              _formatPrice(srv.price),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppTypography.headlineSm(
                                color: AppColors.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 8,
                          ),
                        ),
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => CatalogDetailScreen(
                                serviceId: srv.id,
                                repository: _repository,
                              ),
                            ),
                          );
                        },
                        child: LocalizedText(
                          'Xem chi tiết',
                          style: AppTypography.labelMd(color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMapViewMock(List<ServiceModel> services) {
    return Container(
      color: AppColors.secondaryContainer.withValues(alpha: 0.2),
      child: Stack(
        children: [
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.map_outlined,
                  size: 64,
                  color: AppColors.secondary,
                ),
                const SizedBox(height: 12),
                LocalizedText(
                  'Bản đồ vị trí bãi biển Đà Nẵng',
                  style: AppTypography.headlineSm(color: AppColors.secondary),
                ),
                const SizedBox(height: 6),
                LocalizedText(
                  'Đang hiển thị ${services.length} điểm dịch vụ ven biển',
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          // Pins simulation
          Positioned(
            top: 60,
            left: 80,
            child: _buildMapPin('Mỹ Khê', 'SUP & Lướt sóng'),
          ),
          Positioned(
            top: 140,
            right: 60,
            child: _buildMapPin('Bán đảo Sơn Trà', 'Lặn ngắm san hô'),
          ),
          Positioned(
            bottom: 120,
            left: 100,
            child: _buildMapPin('Non Nước', 'Trường dạy lướt ván'),
          ),
        ],
      ),
    );
  }

  Widget _buildMapPin(String title, String desc) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        boxShadow: AppShapes.shadowLevel2,
        border: Border.all(color: AppColors.secondary, width: 1.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.location_on, size: 16, color: AppColors.primary),
          const SizedBox(width: 4),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              LocalizedText(
                title,
                style: AppTypography.labelSm(
                  color: AppColors.onSurface,
                  fontWeight: FontWeight.bold,
                ),
              ),
              LocalizedText(
                desc,
                style: AppTypography.bodySm(
                  color: AppColors.outline,
                ).copyWith(fontSize: 10),
              ),
            ],
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
                Icons.search_off,
                size: 36,
                color: AppColors.outline,
              ),
            ),
            const SizedBox(height: 16),
            LocalizedText(
              'Không tìm thấy trải nghiệm phù hợp',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 8),
            LocalizedText(
              'Hãy thử thay đổi từ khóa hoặc xóa bớt tiêu chí lọc khu vực biển và mức giá.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: () {
                setState(() {
                  _searchController.clear();
                  _selectedLocation = null;
                  _selectedCategory = null;
                  _selectedTimeOfDay = null;
                  _maxPrice = null;
                });
                _search();
              },
              child: const LocalizedText('Xóa tất cả bộ lọc'),
            ),
          ],
        ),
      ),
    );
  }
}
