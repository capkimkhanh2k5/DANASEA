import '../../../../core/data/vendor_mock_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/widgets/partner_account_app_bar_button.dart';
import 'service_create_edit_screen.dart';
import '../../../slots/presentation/screens/slot_calendar_screen.dart';

class ServiceListScreen extends StatefulWidget {
  final VoidCallback? onOpenNotifications;
  final VoidCallback? onOpenProfile;

  const ServiceListScreen({
    super.key,
    this.onOpenNotifications,
    this.onOpenProfile,
  });

  @override
  State<ServiceListScreen> createState() => _ServiceListScreenState();
}

class _ServiceListScreenState extends State<ServiceListScreen> {
  final _db = VendorMockDatabase.instance;
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';
  String _selectedFilter = 'all'; // all, PUBLISHED, PENDING, DRAFT, PAUSED, REJECTED

  String _normalize(String s) {
    var str = s.toLowerCase();
    str = str.replaceAll(RegExp(r'[àáạảãâầấậẩẫăằắặẳẵ]'), 'a');
    str = str.replaceAll(RegExp(r'[èéẹẻẽêềếệểễ]'), 'e');
    str = str.replaceAll(RegExp(r'[ìíịỉĩ]'), 'i');
    str = str.replaceAll(RegExp(r'[òóọỏõôồốộổỗơờớợởỡ]'), 'o');
    str = str.replaceAll(RegExp(r'[ùúụủũưừứựửữ]'), 'u');
    str = str.replaceAll(RegExp(r'[ỳýỵỷỹ]'), 'y');
    str = str.replaceAll(RegExp(r'[đ]'), 'd');
    return str;
  }

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    VendorServiceRepository().getServices();
  }

  @override
  void dispose() {
    _searchController.dispose();
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final services = _db.services.where((s) {
      if (_selectedFilter != 'all') {
        if (_selectedFilter == 'PUBLISHED' && s.status != ServiceStatus.published) return false;
        if (_selectedFilter == 'PENDING' && s.status != ServiceStatus.pendingReview) return false;
        if (_selectedFilter == 'DRAFT' && s.status != ServiceStatus.draft) return false;
        if (_selectedFilter == 'PAUSED' && s.status != ServiceStatus.paused) return false;
        if (_selectedFilter == 'REJECTED' && s.status != ServiceStatus.rejected) return false;
      }
      if (_searchQuery.isNotEmpty) {
        final rawQ = _searchQuery.trim().toLowerCase();
        final cleanQ = rawQ.replaceAll('#', '').trim();
        final normQ = _normalize(cleanQ);

        final nameViNorm = _normalize(s.nameVi);
        final matchVi = nameViNorm.contains(normQ) || s.nameVi.toLowerCase().contains(rawQ);
        final matchEn = s.nameEn.toLowerCase().contains(cleanQ);
        final matchId = s.serviceId.toLowerCase().contains(cleanQ);
        final matchLoc = _normalize(s.meetingPointName).contains(normQ);
        return matchVi || matchEn || matchId || matchLoc;
      }
      return true;
    }).toList();

    final totalCount = _db.services.length;
    final publishedCount = _db.services.where((s) => s.status == ServiceStatus.published).length;
    final pendingCount = _db.services.where((s) => s.status == ServiceStatus.pendingReview).length;
    final draftCount = _db.services.where((s) => s.status == ServiceStatus.draft).length;
    final pausedCount = _db.services.where((s) => s.status == ServiceStatus.paused).length;
    final rejectedCount = _db.services.where((s) => s.status == ServiceStatus.rejected).length;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleSpacing: 16,
        title: const LocalizedText(
          'Quản lý dịch vụ',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.navyAnchor,
          ),
        ),
        actions: [
          PartnerAccountAppBarButton(onPressed: widget.onOpenProfile),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top Search & Quick Add
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
              child: Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 44,
                      child: TextField(
                        controller: _searchController,
                        textAlignVertical: TextAlignVertical.center,
                        onChanged: (val) => setState(() => _searchQuery = val.trim()),
                        style: const TextStyle(fontSize: 13.5, color: AppColors.onSurface),
                        decoration: InputDecoration(
                          isDense: true,
                          filled: true,
                          fillColor: Colors.white,
                          hintText: tr(context, 'Tìm kiếm dịch vụ, bãi biển...'),
                          hintStyle: const TextStyle(fontSize: 12.5, color: AppColors.outline),
                          prefixIcon: const Icon(Icons.search, size: 20, color: AppColors.tertiary),
                          suffixIcon: _searchQuery.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.cancel, size: 18, color: AppColors.tertiary),
                                  splashRadius: 16,
                                  onPressed: () {
                                    _searchController.clear();
                                    setState(() => _searchQuery = '');
                                  },
                                )
                              : null,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton.icon(
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => const ServiceCreateEditScreen()),
                      );
                    },
                    icon: const Icon(Icons.add, size: 16),
                    label: const LocalizedText('Thêm', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: AppColors.onSecondary,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    ),
                  ),
                ],
              ),
            ),

            // Horizontal Status Filter Chips
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
              child: Row(
                children: [
                  _buildFilterChip('all', 'Tất cả', totalCount),
                  const SizedBox(width: 8),
                  _buildFilterChip('PUBLISHED', 'Đang mở bán', publishedCount),
                  const SizedBox(width: 8),
                  _buildFilterChip('PENDING', 'Chờ duyệt', pendingCount),
                  const SizedBox(width: 8),
                  _buildFilterChip('DRAFT', 'Bản nháp', draftCount),
                  const SizedBox(width: 8),
                  _buildFilterChip('PAUSED', 'Tạm dừng', pausedCount),
                  const SizedBox(width: 8),
                  _buildFilterChip('REJECTED', 'Từ chối', rejectedCount),
                ],
              ),
            ),
            const SizedBox(height: 4),

            // Service Cards Stream
            Expanded(
              child: services.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.kayaking_outlined, size: 48, color: AppColors.tertiary),
                          const SizedBox(height: 8),
                          const LocalizedText('Không tìm thấy dịch vụ nào', style: TextStyle(color: AppColors.tertiary, fontSize: 14)),

                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
                      itemCount: services.length,
                      itemBuilder: (context, index) {
                        final s = services[index];
                        return _buildServiceCard(s);
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFilterChip(String key, String label, int count) {
    final active = _selectedFilter == key;
    return InkWell(
      onTap: () => setState(() => _selectedFilter = key),
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.secondary : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: active ? AppColors.secondary : AppColors.borderSubtle,
          ),
          boxShadow: active
              ? [BoxShadow(color: AppColors.secondary.withOpacity(0.2), blurRadius: 4, offset: const Offset(0, 2))]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: active ? FontWeight.bold : FontWeight.w500,
                color: active ? AppColors.onSecondary : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            LocalizedText(
              '($count)',
              style: TextStyle(
                fontSize: 11,
                fontWeight: active ? FontWeight.bold : FontWeight.normal,
                color: active ? AppColors.onSecondary.withOpacity(0.9) : AppColors.tertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildServiceCard(ServiceModel service) {
    final hasImage = service.imageUrls.isNotEmpty;
    final primaryImageUrl = hasImage ? service.imageUrls.first : null;
    final showEnglishName = service.nameEn.trim().isNotEmpty &&
        service.nameEn.trim().toLowerCase() != service.nameVi.trim().toLowerCase();
    final formattedPrice = service.price.toStringAsFixed(0).replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (Match m) => '${m[1]}.',
    );

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.borderSubtle),
        boxShadow: const [
          BoxShadow(
            color: Color(0x08102F3A),
            blurRadius: 10,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => ServiceCreateEditScreen(service: service),
              ),
            );
          },
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. Cover Image Banner
              SizedBox(
                height: 145,
                width: double.infinity,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    if (primaryImageUrl != null)
                      Image.network(
                        primaryImageUrl,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => _buildFallbackCover(service),
                      )
                    else
                      _buildFallbackCover(service),

                    // Gradient overlay
                    DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [
                            Colors.black.withOpacity(0.4),
                            Colors.transparent,
                            Colors.black.withOpacity(0.55),
                          ],
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          stops: const [0.0, 0.45, 1.0],
                        ),
                      ),
                    ),

                    // Top row: Status Badge & Rating
                    Positioned(
                      top: 10,
                      left: 10,
                      right: 10,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          StatusBadge(status: service.status),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.92),
                              borderRadius: BorderRadius.circular(12),
                              boxShadow: const [
                                BoxShadow(color: Color(0x14000000), blurRadius: 4),
                              ],
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.star_rounded, size: 14, color: Colors.amber),
                                const SizedBox(width: 3),
                                Text(
                                  service.avgRating.toStringAsFixed(1),
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.navyAnchor,
                                  ),
                                ),
                                if (service.ratingCount > 0) ...[
                                  const SizedBox(width: 2),
                                  Text(
                                    '(${service.ratingCount})',
                                    style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),


                  ],
                ),
              ),

              // 2. Card Content
              Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LocalizedText(
                      service.nameVi,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppColors.navyAnchor,
                        height: 1.25,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (showEnglishName) ...[
                      const SizedBox(height: 2),
                      LocalizedText(
                        service.nameEn,
                        style: const TextStyle(
                          fontSize: 11,
                          color: AppColors.tertiary,
                          fontStyle: FontStyle.italic,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                    const SizedBox(height: 8),

                    // Compact Meta Badges
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: [
                        _buildSpecBadge(Icons.access_time_rounded, '${service.durationMinutes} phút'),
                        _buildSpecBadge(Icons.people_alt_outlined, 'Tối đa ${service.maxCapacity} khách'),
                        if (service.meetingPointName.isNotEmpty)
                          _buildSpecBadge(Icons.place_outlined, service.meetingPointName),
                      ],
                    ),
                    const SizedBox(height: 10),

                    const Divider(height: 1, color: AppColors.surfaceContainerHigh),
                    const SizedBox(height: 10),

                    // Price and Actions Row
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const LocalizedText(
                              'Đơn giá niêm yết',
                              style: TextStyle(fontSize: 10, color: AppColors.tertiary),
                            ),
                            Row(
                              crossAxisAlignment: CrossAxisAlignment.baseline,
                              textBaseline: TextBaseline.alphabetic,
                              children: [
                                Text(
                                  formattedPrice,
                                  style: const TextStyle(
                                    fontSize: 17,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.navyAnchor,
                                  ),
                                ),
                                const SizedBox(width: 2),
                                const Text(
                                  'đ/khách',
                                  style: TextStyle(fontSize: 10, color: AppColors.tertiary, fontWeight: FontWeight.w600),
                                ),
                              ],
                            ),
                          ],
                        ),

                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            // Slot calendar button
                            OutlinedButton.icon(
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => SlotCalendarScreen(initialServiceId: service.serviceId),
                                  ),
                                );
                              },
                              icon: const Icon(Icons.calendar_month_outlined, size: 14),
                              label: const Text('Lịch ca', style: TextStyle(fontSize: 12)),
                              style: OutlinedButton.styleFrom(
                                foregroundColor: AppColors.secondary,
                                side: BorderSide(color: AppColors.secondary.withOpacity(0.3)),
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                              ),
                            ),
                            const SizedBox(width: 6),
                            // Edit service button
                            ElevatedButton.icon(
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => ServiceCreateEditScreen(service: service),
                                  ),
                                );
                              },
                              icon: const Icon(Icons.edit_outlined, size: 14),
                              label: const Text('Sửa', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.secondary,
                                foregroundColor: Colors.white,
                                elevation: 0,
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFallbackCover(ServiceModel service) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          colors: [AppColors.secondary, AppColors.tertiary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Center(
        child: Icon(
          service.category == 'WATER_SPORTS' ? Icons.surfing : Icons.scuba_diving,
          color: Colors.white.withOpacity(0.25),
          size: 56,
        ),
      ),
    );
  }

  Widget _buildSpecBadge(IconData icon, String text) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 240),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: AppColors.secondary),
          const SizedBox(width: 4),
          Flexible(
            child: LocalizedText(
              text,
              style: const TextStyle(fontSize: 11, color: AppColors.tertiary, fontWeight: FontWeight.w500),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
