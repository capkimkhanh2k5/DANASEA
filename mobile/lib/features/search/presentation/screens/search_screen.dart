import 'dart:async';
import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/assistant_repository.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/data/weather_repository.dart';
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

  // Live weather & AI Nearby / Compare state
  WeatherSnapshotModel? _weatherSnapshot;
  bool _aiNearbyActive = false;
  bool _loadingAiNearby = false;
  Map<String, double> _aiDistanceByServiceId = {};
  Map<String, int> _aiQuoteByServiceId = {};
  final Set<String> _compareServiceIds = <String>{};
  bool _loadingAiCompare = false;

  @override
  void initState() {
    super.initState();
    _repository = widget.repository ?? CatalogRepository();
    _searchController = TextEditingController(text: widget.initialQuery ?? '');
    _selectedCategory = widget.initialCategory;
    _loadCategories();
    _loadLiveWeather();
    _search();
  }

  Future<void> _loadLiveWeather() async {
    try {
      final w = await WeatherRepository.instance.getCurrentWeather();
      if (mounted) {
        setState(() => _weatherSnapshot = w);
      }
    } catch (_) {}
  }

  (double, double) _resolveCoordinatesForLocation(String? loc) {
    final lower = (loc ?? '').toLowerCase();
    if (lower.contains('sơn trà') || lower.contains('son tra')) {
      return (16.1060, 108.2615);
    }
    if (lower.contains('non nước') || lower.contains('non nuoc')) {
      return (16.0025, 108.2690);
    }
    // Default Da Nang coastal center (My Khe)
    return (16.0598, 108.2465);
  }

  Future<void> _toggleAiNearby() async {
    if (_aiNearbyActive) {
      setState(() {
        _aiNearbyActive = false;
        _aiDistanceByServiceId = {};
        _aiQuoteByServiceId = {};
      });
      await _search();
      return;
    }

    setState(() {
      _loadingAiNearby = true;
      _aiNearbyActive = true;
    });
    try {
      final (lat, lng) = _resolveCoordinatesForLocation(_selectedLocation);
      final queryText = _searchController.text.trim().isNotEmpty
          ? _searchController.text.trim()
          : (_selectedCategory != null
                ? _displayCategoryName(_selectedCategory!)
                : 'trải nghiệm biển gần đây');
      final res = await AssistantRepository.instance.discoverServices(
        query: queryText,
        mode: 'NEARBY',
        latitude: lat,
        longitude: lng,
        radiusKm: 15.0,
        totalBudget: _maxPrice,
      );
      if (!mounted) return;
      final candidates = res['candidates'];
      final distMap = <String, double>{};
      final quoteMap = <String, int>{};
      final nearbyIds = <String>[];
      final aiServices = <ServiceModel>[];

      if (candidates is List) {
        for (final raw in candidates.whereType<Map>()) {
          final map = Map<String, dynamic>.from(raw);
          final srvMap = map['service'] is Map
              ? Map<String, dynamic>.from(map['service'] as Map)
              : <String, dynamic>{};
          final srvId = srvMap['id']?.toString() ?? '';
          if (srvId.isEmpty) continue;
          nearbyIds.add(srvId);
          final dist = (map['distanceKm'] as num?)?.toDouble();
          if (dist != null) distMap[srvId] = dist;
          final minTotal = (map['minimumPartyTotal'] as num?)?.toInt();
          if (minTotal != null) quoteMap[srvId] = minTotal;

          final existing = _items.where((s) => s.id == srvId).firstOrNull;
          if (existing != null) {
            aiServices.add(existing);
          } else {
            aiServices.add(
              ServiceModel(
                id: srvId,
                vendorId: srvMap['vendorId']?.toString() ?? '',
                vendorName: 'DanaSea Partner',
                categoryId: srvMap['categoryId']?.toString() ?? '',
                categoryName: srvMap['categoryName']?.toString() ?? '',
                name: srvMap['name']?.toString() ?? 'Trải nghiệm biển',
                slug: srvId,
                description: srvMap['description']?.toString() ?? '',
                price: minTotal ?? 0,
                durationMinutes:
                    (srvMap['durationMinutes'] as num?)?.toInt() ?? 60,
                capacityPerSlot: 10,
                locationName: srvMap['address']?.toString() ?? 'Đà Nẵng',
                address: srvMap['address']?.toString() ?? 'Đà Nẵng',
                latitude: (srvMap['latitude'] as num?)?.toDouble() ?? lat,
                longitude: (srvMap['longitude'] as num?)?.toDouble() ?? lng,
                waiverContent: '',
                weatherSensitive: srvMap['weatherSensitive'] == true,
                avgRating:
                    (srvMap['averageRating'] as num?)?.toDouble() ?? 5.0,
                ratingCount: (srvMap['reviewCount'] as num?)?.toInt() ?? 0,
              ),
            );
          }
        }
      }

      setState(() {
        _aiDistanceByServiceId = distMap;
        _aiQuoteByServiceId = quoteMap;
        if (aiServices.isNotEmpty) {
          _items = aiServices;
        }
      });
    } on AuthFailure catch (e) {
      if (mounted) {
        setState(() => _aiNearbyActive = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } catch (_) {
      if (mounted) {
        setState(() => _aiNearbyActive = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải gợi ý AI gần bạn. Vui lòng đăng nhập và thử lại.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingAiNearby = false);
      }
    }
  }

  void _toggleCompareSelection(String serviceId) {
    setState(() {
      if (_compareServiceIds.contains(serviceId)) {
        _compareServiceIds.remove(serviceId);
      } else {
        if (_compareServiceIds.length >= 4) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: LocalizedText('Chỉ có thể chọn tối đa 4 dịch vụ để so sánh AI.'),
            ),
          );
          return;
        }
        _compareServiceIds.add(serviceId);
      }
    });
  }

  Future<void> _openAiComparisonSheet() async {
    if (_compareServiceIds.length < 2) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Vui lòng chọn từ 2 đến 4 dịch vụ để AI so sánh.'),
        ),
      );
      return;
    }
    setState(() => _loadingAiCompare = true);
    try {
      final (lat, lng) = _resolveCoordinatesForLocation(_selectedLocation);
      final queryText = _searchController.text.trim().isNotEmpty
          ? _searchController.text.trim()
          : 'So sánh dịch vụ biển phù hợp nhất';
      final result = await AssistantRepository.instance.compareServices(
        serviceIds: _compareServiceIds.toList(),
        query: queryText,
        latitude: lat,
        longitude: lng,
      );
      if (!mounted) return;
      await _showAiCompareResultModal(result);
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể thực hiện so sánh AI lúc này. Vui lòng thử lại.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingAiCompare = false);
      }
    }
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
            final categoryOptions = _categories
                .map((c) => {'id': c.id, 'label': c.name})
                .toList();

            final maxHeight = MediaQuery.of(context).size.height * 0.85;
            return Container(
              constraints: BoxConstraints(maxHeight: maxHeight),
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(height: 12),
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
                  Flexible(
                    child: SingleChildScrollView(
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
                  ),
                  SafeArea(
                    top: false,
                    child: Padding(
                      padding: const EdgeInsets.only(
                        left: AppShapes.gutterMobile,
                        right: AppShapes.gutterMobile,
                        top: 12,
                        bottom: 16,
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
                      FittedBox(
                        fit: BoxFit.scaleDown,
                        alignment: Alignment.centerLeft,
                        child: LocalizedText(
                          _aiNearbyActive
                              ? 'Đã xếp hạng theo AI Gợi ý gần bạn'
                              : (_weatherSnapshot?.waveHeight != null
                                    ? 'Sóng hiện tại ${_weatherSnapshot!.waveHeight!.toStringAsFixed(1)}m • Gió ${(_weatherSnapshot!.windSpeed ?? 0).toStringAsFixed(0)} km/h'
                                    : 'Dữ liệu trực tiếp từ hệ thống'),
                          maxLines: 1,
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 11),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 6),
                InkWell(
                  onTap: _loadingAiNearby ? null : _toggleAiNearby,
                  borderRadius: BorderRadius.circular(18),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: _aiNearbyActive
                          ? AppColors.secondary
                          : AppColors.secondary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(
                        color: AppColors.secondary.withValues(alpha: 0.35),
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (_loadingAiNearby)
                          const SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(strokeWidth: 1.8),
                          )
                        else
                          Icon(
                            Icons.near_me_outlined,
                            size: 14,
                            color: _aiNearbyActive
                                ? Colors.white
                                : AppColors.secondary,
                          ),
                        const SizedBox(width: 4),
                        LocalizedText(
                          'AI Gần bạn',
                          style: AppTypography.labelSm(
                            color: _aiNearbyActive
                                ? Colors.white
                                : AppColors.secondary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 6),
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

          if (_compareServiceIds.isNotEmpty)
            Container(
              margin: const EdgeInsets.symmetric(
                horizontal: AppShapes.gutterMobile,
                vertical: 4,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.secondary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: AppColors.secondary.withValues(alpha: 0.3),
                ),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.compare_arrows,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: LocalizedText(
                      'Đã chọn ${_compareServiceIds.length}/4 dịch vụ để so sánh AI',
                      style: AppTypography.labelSm(
                        color: AppColors.onSurface,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: () => setState(() => _compareServiceIds.clear()),
                    child: const LocalizedText('Bỏ chọn'),
                  ),
                  const SizedBox(width: 4),
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      visualDensity: VisualDensity.compact,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    onPressed:
                        (_compareServiceIds.length < 2 || _loadingAiCompare)
                        ? null
                        : _openAiComparisonSheet,
                    icon: _loadingAiCompare
                        ? const SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.auto_awesome, size: 14),
                    label: const LocalizedText('So sánh AI'),
                  ),
                ],
              ),
            ),

          // CONTENT: LIST OR MAP
          Expanded(
            child: _isMapView
                ? _buildMapPreview(services)
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
                  // Ocean condition badge (live weather + AI distance)
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
                            _aiDistanceByServiceId.containsKey(srv.id)
                                ? 'Cách bạn ${_aiDistanceByServiceId[srv.id]!.toStringAsFixed(1)} km'
                                : (_weatherSnapshot?.waveHeight != null
                                      ? 'Sóng thực tế: ${_weatherSnapshot!.waveHeight!.toStringAsFixed(1)}m'
                                      : (srv.weatherSensitive
                                            ? 'Theo dõi sóng biển'
                                            : 'Hoạt động mọi thời tiết')),
                            style: AppTypography.labelSm(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  // AI Compare toggle badge
                  Positioned(
                    top: 10,
                    right: 10,
                    child: InkWell(
                      onTap: () => _toggleCompareSelection(srv.id),
                      borderRadius: BorderRadius.circular(14),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: _compareServiceIds.contains(srv.id)
                              ? AppColors.primary
                              : Colors.black.withValues(alpha: 0.55),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              _compareServiceIds.contains(srv.id)
                                  ? Icons.check_circle
                                  : Icons.compare_arrows,
                              size: 13,
                              color: Colors.white,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              _compareServiceIds.contains(srv.id)
                                  ? 'Đã chọn so sánh'
                                  : 'So sánh AI',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 10.5,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
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
                          srv.availableSlots.isNotEmpty
                              ? 'Khởi hành ${srv.availableSlots.first}'
                              : (srv.catalogSlots.isNotEmpty
                                  ? 'Khởi hành ${srv.catalogSlots.first.startTime}'
                                  : 'Mở theo ca'),
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
                          srv.locationName.trim().isNotEmpty
                              ? srv.locationName.trim()
                              : (srv.address.trim().isNotEmpty
                                  ? srv.address.trim()
                                  : 'Bãi biển Đà Nẵng'),
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(
                        Icons.schedule,
                        size: 14,
                        color: AppColors.tertiary,
                      ),
                      const SizedBox(width: 4),
                      LocalizedText(
                        srv.durationMinutes > 0
                            ? (srv.durationMinutes >= 60
                                ? '${srv.durationMinutes ~/ 60}h${srv.durationMinutes % 60 > 0 ? '${srv.durationMinutes % 60}p' : ''}'
                                : '${srv.durationMinutes}p')
                            : 'Theo ca',
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

  Widget _buildMapPreview(List<ServiceModel> services) {
    final pinPositions = <Alignment>[
      const Alignment(-0.55, -0.55),
      const Alignment(0.55, -0.35),
      const Alignment(-0.45, 0.45),
      const Alignment(0.45, 0.55),
    ];
    final topServices = services.take(4).toList();

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
                  'Đang hiển thị ${services.length} điểm dịch vụ ven biển từ hệ thống',
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          for (var i = 0; i < topServices.length; i++)
            Align(
              alignment: pinPositions[i % pinPositions.length],
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: InkWell(
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => CatalogDetailScreen(
                          serviceId: topServices[i].id,
                          repository: _repository,
                        ),
                      ),
                    );
                  },
                  child: _buildMapPin(
                    topServices[i].locationName.trim().isNotEmpty
                        ? topServices[i].locationName.trim()
                        : topServices[i].name,
                    '${topServices[i].name} • ${_formatPrice(_aiQuoteByServiceId[topServices[i].id] ?? topServices[i].price)}',
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _showAiCompareResultModal(Map<String, dynamic> result) async {
    final matrixRaw = result['matrix'];
    final rows = matrixRaw is List
        ? matrixRaw
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList()
        : <Map<String, dynamic>>[];
    final bestFit = result['bestFit'] is Map
        ? Map<String, dynamic>.from(result['bestFit'] as Map)
        : null;
    final bestServiceId = bestFit?['serviceId']?.toString() ?? '';
    final diffRaw = result['keyDifferences'];
    final diffs = diffRaw is List
        ? diffRaw
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList()
        : <Map<String, dynamic>>[];

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) {
        return Container(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(sheetCtx).size.height * 0.85,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.all(AppShapes.gutterMobile),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      Icons.auto_awesome,
                      color: AppColors.secondary,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: LocalizedText(
                      'AI So sánh dịch vụ biển (${rows.length})',
                      style: AppTypography.headlineSm(
                        color: AppColors.onSurface,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(sheetCtx),
                  ),
                ],
              ),
              const Divider(height: 16),
              Flexible(
                child: rows.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: LocalizedText(
                            'Chưa có dữ liệu so sánh từ hệ thống cho các dịch vụ đã chọn.',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ),
                      )
                    : ListView(
                        shrinkWrap: true,
                        children: [
                          ...rows.map((row) {
                            final srvId = row['serviceId']?.toString() ?? '';
                            final isBest =
                                bestServiceId.isNotEmpty &&
                                srvId == bestServiceId;
                            final name =
                                row['serviceName']?.toString() ?? 'Dịch vụ';
                            final partyTotal =
                                (row['partyTotal'] as num?)?.toInt() ??
                                (row['unitPrice'] as num?)?.toInt() ??
                                0;
                            final duration =
                                (row['durationMinutes'] as num?)?.toInt() ?? 0;
                            final rating =
                                (row['rating'] as num?)?.toDouble() ?? 0.0;
                            final reviews =
                                (row['reviewCount'] as num?)?.toInt() ?? 0;
                            final slots =
                                (row['bookableSlots'] as num?)?.toInt() ?? 0;
                            final dist = (row['distanceKm'] as num?)
                                ?.toDouble();
                            final benefits =
                                row['benefits']?.toString().trim() ?? '';

                            return Container(
                              margin: const EdgeInsets.only(bottom: 12),
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: isBest
                                    ? AppColors.secondary.withValues(
                                        alpha: 0.07,
                                      )
                                    : Colors.white,
                                borderRadius: BorderRadius.circular(14),
                                border: Border.all(
                                  color: isBest
                                      ? AppColors.secondary
                                      : AppColors.borderSubtle,
                                  width: isBest ? 1.5 : 1,
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          name,
                                          style: AppTypography.labelLg(
                                            color: AppColors.onSurface,
                                            fontWeight: FontWeight.w800,
                                          ),
                                        ),
                                      ),
                                      if (isBest)
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 8,
                                            vertical: 3,
                                          ),
                                          decoration: BoxDecoration(
                                            color: AppColors.secondary,
                                            borderRadius: BorderRadius.circular(
                                              10,
                                            ),
                                          ),
                                          child: const Text(
                                            'AI Đề xuất tối ưu',
                                            style: TextStyle(
                                              color: Colors.white,
                                              fontSize: 10,
                                              fontWeight: FontWeight.w700,
                                            ),
                                          ),
                                        ),
                                    ],
                                  ),
                                  const SizedBox(height: 8),
                                  Wrap(
                                    spacing: 12,
                                    runSpacing: 6,
                                    children: [
                                      Text(
                                        'Giá: ${_formatPrice(partyTotal)}',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.w700,
                                          color: AppColors.primary,
                                        ),
                                      ),
                                      Text(
                                        'Đánh giá: ${rating.toStringAsFixed(1)}★ ($reviews)',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.onSurface,
                                        ),
                                      ),
                                      if (duration > 0)
                                        Text(
                                          'Thời lượng: ${duration}p',
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: AppColors.onSurfaceVariant,
                                          ),
                                        ),
                                      Text(
                                        'Ca trống: $slots',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: AppColors.secondary,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                      if (dist != null)
                                        Text(
                                          'Khoảng cách: ${dist.toStringAsFixed(1)} km',
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: AppColors.onSurfaceVariant,
                                          ),
                                        ),
                                    ],
                                  ),
                                  if (benefits.isNotEmpty) ...[
                                    const SizedBox(height: 6),
                                    Text(
                                      'Quyền lợi gói: $benefits',
                                      style: AppTypography.bodySm(
                                        color: AppColors.onSurfaceVariant,
                                      ).copyWith(fontSize: 11.5),
                                    ),
                                  ],
                                ],
                              ),
                            );
                          }),
                          if (diffs.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            LocalizedText(
                              'Điểm khác biệt chính (từ dữ liệu Catalog thực tế):',
                              style: AppTypography.labelMd(
                                color: AppColors.onSurface,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: diffs.map((d) {
                                final field = d['field']?.toString() ?? '';
                                return Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 4,
                                  ),
                                  decoration: BoxDecoration(
                                    color: AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Text(
                                    field,
                                    style: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w600,
                                      color: AppColors.onSurfaceVariant,
                                    ),
                                  ),
                                );
                              }).toList(),
                            ),
                          ],
                        ],
                      ),
              ),
            ],
          ),
        );
      },
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
