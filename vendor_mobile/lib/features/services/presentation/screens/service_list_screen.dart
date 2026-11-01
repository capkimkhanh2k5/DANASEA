import '../../../../core/data/vendor_mock_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/status_badge.dart';
import 'service_create_edit_screen.dart';
import 'service_safety_cert_screen.dart';
import '../../../slots/presentation/screens/slot_calendar_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';

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
  String _searchQuery = '';
  String _selectedFilter = 'all'; // all, PUBLISHED, PENDING, DRAFT, PAUSED, REJECTED

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    VendorServiceRepository().getServices();
  }

  @override
  void dispose() {
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
        final query = _searchQuery.toLowerCase();
        final matchVi = s.nameVi.toLowerCase().contains(query);
        final matchEn = s.nameEn.toLowerCase().contains(query);
        final matchId = s.serviceId.toLowerCase().contains(query);
        final matchLoc = s.meetingPointName.toLowerCase().contains(query);
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
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                LocalizedText(
                  'DANASEA',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                      ),
                ),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const LocalizedText('VENDOR', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                ),
              ],
            ),
            const LocalizedText('Dịch vụ', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined, color: AppColors.secondary),
            onPressed: () {
              if (widget.onOpenNotifications != null) {
                widget.onOpenNotifications!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const NotificationSafetyScreen()),
                );
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.account_circle, color: AppColors.primary),
            onPressed: () {
              if (widget.onOpenProfile != null) {
                widget.onOpenProfile!();
              } else {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const BusinessProfileScreen()),
                );
              }
            },
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top Command & Search
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            Container(
                              width: 8,
                              height: 8,
                              decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary),
                            ),
                            const SizedBox(width: 6),
                            const Flexible(
                              child: LocalizedText(
                                'DANANG OCEAN CLUB',
                                style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(color: AppColors.surfaceContainerHigh, borderRadius: BorderRadius.circular(10)),
                        child: const LocalizedText('Mã: DOC-8842', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: LocalizedText(
                          'Quản lý dịch vụ',
                          style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton.icon(
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const ServiceCreateEditScreen()),
                          );
                        },
                        icon: const Icon(Icons.add, size: 18),
                        label: const LocalizedText('Thêm mới', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
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
                  const SizedBox(height: 12),
                  // Search capsule
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.search, size: 20, color: AppColors.tertiary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            onChanged: (val) => setState(() => _searchQuery = val.trim()),
                            decoration:  InputDecoration(
                              hintText: tr(context, 'Tìm theo tên dịch vụ, ID hoặc bãi biển...'),
                              hintStyle: TextStyle(fontSize: 12, color: AppColors.outline),
                              border: InputBorder.none,
                            ),
                            style: const TextStyle(fontSize: 13),
                          ),
                        ),
                      ],
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

            // Summary mini banner
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withOpacity(0.4),
                  borderRadius: AppShapes.radiusSm,
                ),
                child: const Row(
                  children: [
                    Icon(Icons.verified_user, size: 20, color: AppColors.secondary),
                    SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LocalizedText('Tỉ lệ lấp đầy bình quân: 92%', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer)),
                          LocalizedText('Thời tiết biển hôm nay rất lý tưởng cho SUP', style: TextStyle(fontSize: 11, color: AppColors.secondary)),
                        ],
                      ),
                    ),
                    Icon(Icons.sunny, size: 20, color: AppColors.secondary),
                  ],
                ),
              ),
            ),

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
                          if (_searchQuery.isNotEmpty || _selectedFilter != 'all')
                            TextButton(
                              onPressed: () {
                                setState(() {
                                  _searchQuery = '';
                                  _selectedFilter = 'all';
                                });
                              },
                              child: const LocalizedText('Xóa bộ lọc'),
                            ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
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
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.secondary : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          boxShadow: active ? AppShapes.shadowSm : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: active ? FontWeight.bold : FontWeight.normal,
                color: active ? AppColors.onSecondary : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            LocalizedText(
              '($count)',
              style: TextStyle(
                fontSize: 11,
                color: active ? AppColors.onSecondary.withOpacity(0.9) : AppColors.tertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildServiceCard(ServiceModel service) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ServiceCreateEditScreen(service: service),
            ),
          );
        },
        borderRadius: AppShapes.radiusMd,
        child: Container(
          margin: const EdgeInsets.only(bottom: 16),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: AppShapes.radiusMd,
            boxShadow: AppShapes.shadowSm,
          ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Image / Media placeholder
          Container(
            height: 140,
            width: double.infinity,
            decoration: BoxDecoration(
              borderRadius: AppShapes.radiusSm,
              color: AppColors.surfaceContainerHigh,
              gradient: const LinearGradient(
                colors: [AppColors.secondary, AppColors.tertiary],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
            child: Stack(
              children: [
                Center(
                  child: Icon(
                    service.category == 'WATER_SPORTS' ? Icons.surfing : Icons.scuba_diving,
                    color: Colors.white.withOpacity(0.3),
                    size: 64,
                  ),
                ),
                Positioned(
                  top: 10,
                  left: 10,
                  child: Row(
                    children: [
                      StatusBadge(status: service.status),
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: Colors.black.withOpacity(0.6),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: LocalizedText(
                          '#${service.serviceId.toUpperCase()}',
                          style: const TextStyle(fontSize: 10, color: Colors.white, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ),
                Positioned(
                  top: 10,
                  right: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.9),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.star, size: 13, color: AppColors.primaryContainer),
                        SizedBox(width: 3),
                        LocalizedText('4.9', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                      ],
                    ),
                  ),
                ),
                Positioned(
                  bottom: 8,
                  left: 10,
                  right: 10,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.9),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.water, size: 12, color: AppColors.secondary),
                        const SizedBox(width: 4),
                        Expanded(
                          child: LocalizedText(
                            'Sóng max: ${service.maxWaveM.toStringAsFixed(1)}m • Gió min: ${service.minWindKmh.toStringAsFixed(0)} km/h',
                            style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Title & Description
          LocalizedText(
            service.nameVi,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
          ),
          const SizedBox(height: 2),
          LocalizedText(
            service.nameEn,
            style: const TextStyle(fontSize: 12, color: AppColors.tertiary, fontStyle: FontStyle.italic),
          ),
          const SizedBox(height: 8),

          // Specs badges
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              _buildSpecBadge(Icons.schedule, '${service.durationMinutes} phút'),
              _buildSpecBadge(Icons.groups, 'Tối đa ${service.maxCapacity} khách/slot'),
              _buildSpecBadge(Icons.place, service.meetingPointName),
            ],
          ),
          const SizedBox(height: 12),

          // Pricing and actions (Responsive Wrap)
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 8,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const LocalizedText('Đơn giá duy nhất', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      LocalizedText(
                        '${service.price.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')}',
                        style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: AppColors.primary),
                      ),
                      const SizedBox(width: 2),
                      const LocalizedText('đ/khách', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                    ],
                  ),
                ],
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Button Edit
                  OutlinedButton.icon(
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => ServiceCreateEditScreen(service: service),
                        ),
                      );
                    },
                    icon: const Icon(Icons.edit, size: 12),
                    label: const LocalizedText('Sửa', style: TextStyle(fontSize: 11)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.secondary,
                      side: const BorderSide(color: AppColors.secondary),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    ),
                  ),
                  const SizedBox(width: 4),
                  // Button Safety & Cert
                  OutlinedButton.icon(
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => ServiceSafetyCertScreen(service: service),
                        ),
                      );
                    },
                    icon: const Icon(Icons.shield_outlined, size: 12),
                    label: const LocalizedText('An toàn', style: TextStyle(fontSize: 11)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.primary,
                      side: const BorderSide(color: AppColors.primary),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    ),
                  ),
                  const SizedBox(width: 2),
                  // Button Calendar
                  IconButton(
                    padding: const EdgeInsets.all(4),
                    constraints: const BoxConstraints(),
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => SlotCalendarScreen(initialServiceId: service.serviceId),
                        ),
                      );
                    },
                    icon: const Icon(Icons.calendar_month, color: AppColors.secondary, size: 18),
                    tooltip: tr(context, 'Xem lịch khung giờ'),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ),
  ),
);
  }

  Widget _buildSpecBadge(IconData icon, String text) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 240),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: AppColors.secondary),
          const SizedBox(width: 4),
          Flexible(
            child: LocalizedText(
              text,
              style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
