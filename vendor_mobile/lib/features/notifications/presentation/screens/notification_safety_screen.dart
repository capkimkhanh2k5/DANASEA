import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';

class NotificationSafetyScreen extends StatefulWidget {
  final Function(int)? onNavigateTab;

  const NotificationSafetyScreen({super.key, this.onNavigateTab});

  @override
  State<NotificationSafetyScreen> createState() => _NotificationSafetyScreenState();
}

class _NotificationSafetyScreenState extends State<NotificationSafetyScreen> {
  final _db = VendorStore.instance;
  final _notifRepo = VendorNotificationRepository();
  String _selectedFilter = 'all'; // all, sea_safety, order, system
  AdvanceSafetyCheckModel? _advanceSafety;
  bool _loadingWeather = false;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    _refreshLiveMarineSafety();
    _notifRepo.getNotifications().catchError((_) => _db.notifications);
  }

  Future<void> _refreshLiveMarineSafety() async {
    if (_loadingWeather) return;
    setState(() => _loadingWeather = true);
    try {
      final firstService = _db.services.isNotEmpty ? _db.services.first : null;
      final firstSlot = _db.slots.isNotEmpty ? _db.slots.first : null;
      final firstCategory = _db.categories.isNotEmpty ? _db.categories.first : null;
      final results = await Future.wait([
        _notifRepo.getWeatherInfo(
          lat: firstService?.latitude,
          lng: firstService?.longitude,
        ),
        _notifRepo.checkAdvanceSafety(
          slotId: firstSlot?.slotId,
          categoryId: firstService?.categoryId ?? firstCategory?.id,
          categorySlug: firstCategory?.slug,
          latitude: firstService?.latitude,
          longitude: firstService?.longitude,
          bookingDate: firstSlot?.date ?? DateTime.now(),
          startTime: firstSlot?.startTime,
          endTime: firstSlot?.endTime,
        ),
      ]);
      if (mounted) {
        setState(() {
          _advanceSafety = results[1] as AdvanceSafetyCheckModel;
          _loadingWeather = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loadingWeather = false);
    }
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  String _formatTime(String t) {
    if (t.isEmpty) return '';
    final parts = t.split(':');
    if (parts.length >= 2) return '${parts[0]}:${parts[1]}';
    return t;
  }

  @override
  Widget build(BuildContext context) {
    final weather = _db.weather;
    final notifications = _db.notifications.where((n) {
      if (_selectedFilter == 'all') return true;
      if (_selectedFilter == 'sea_safety') return n.type == 'WEATHER_WARNING';
      if (_selectedFilter == 'order') return n.type == 'NEW_ORDER' || n.type == 'DISPUTE';
      if (_selectedFilter == 'system') return n.type == 'SYSTEM';
      return true;
    }).toList();

    final bool isSeaWeatherRisky = weather.windSpeedKmh > 25 ||
        weather.waveHeightM > 1.2 ||
        !(_advanceSafety?.canBook ?? true);

    final targetSlot = _db.slots.isNotEmpty ? _db.slots.last : null;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const LocalizedText(
          'Cảnh báo & An toàn biển',
          style: TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.bold,
            color: AppColors.navyAnchor,
          ),
        ),
        actions: [
          IconButton(
            icon: _loadingWeather
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.secondary),
                  )
                : const Icon(Icons.refresh_rounded, color: AppColors.secondary),
            tooltip: 'Làm mới thời tiết',
            onPressed: _loadingWeather ? null : _refreshLiveMarineSafety,
          ),
          IconButton(
            icon: const Icon(Icons.done_all_rounded, color: AppColors.secondary),
            tooltip: tr(context, 'Đánh dấu tất cả đã đọc'),
            onPressed: () {
              for (var n in _db.notifications) {
                _db.markNotificationRead(n.notificationId);
              }
              ToastNotification.showSuccess(context, 'Đã đánh dấu tất cả đã đọc');
            },
          ),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await Future.wait([
              _refreshLiveMarineSafety(),
              _notifRepo.getNotifications().catchError((_) => _db.notifications),
            ]);
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Compact Marine Station Badge
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0FDF4),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFBBF7D0)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.radar_rounded, size: 16, color: Color(0xFF16A34A)),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Trạm quan trắc ${weather.locationKey.isNotEmpty ? weather.locationKey : "Đà Nẵng"} • Cập nhật ${weather.fetchedAt.hour.toString().padLeft(2, '0')}:${weather.fetchedAt.minute.toString().padLeft(2, '0')}',
                          style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF15803D)),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: const Color(0xFF16A34A),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Text(
                          'TRỰC TUYẾN',
                          style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),

                // 3 Marine Safety Quick Metrics
                Row(
                  children: [
                    Expanded(
                      child: _buildTopMarineKpiCard(
                        icon: Icons.waves_rounded,
                        iconColor: const Color(0xFF0284C7),
                        iconBg: const Color(0xFFE0F2FE),
                        label: 'Chiều cao sóng',
                        value: '${weather.waveHeightM.toStringAsFixed(1)} m',
                        status: weather.waveHeightM <= 1.2 ? 'Sóng êm' : (weather.waveHeightM <= 2.0 ? 'Sóng vừa' : 'Sóng lớn'),
                        statusColor: weather.waveHeightM <= 1.2 ? const Color(0xFF059669) : const Color(0xFFD97706),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildTopMarineKpiCard(
                        icon: Icons.air_rounded,
                        iconColor: const Color(0xFF0D9488),
                        iconBg: const Color(0xFFCCFBF1),
                        label: 'Tốc độ gió',
                        value: '${weather.windSpeedKmh.toStringAsFixed(0)} km/h',
                        status: weather.windSpeedKmh <= 25 ? 'Gió nhẹ' : 'Gió giật',
                        statusColor: weather.windSpeedKmh <= 25 ? const Color(0xFF059669) : const Color(0xFFDC2626),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildTopMarineKpiCard(
                        icon: (_advanceSafety?.canBook ?? true)
                            ? Icons.verified_user_rounded
                            : Icons.warning_amber_rounded,
                        iconColor: (_advanceSafety?.canBook ?? true)
                            ? const Color(0xFF059669)
                            : const Color(0xFFD97706),
                        iconBg: (_advanceSafety?.canBook ?? true)
                            ? const Color(0xFFD1FAE5)
                            : const Color(0xFFFEF3C7),
                        label: 'Khuyến nghị',
                        value: (_advanceSafety?.canBook ?? true) ? 'An toàn' : 'Cảnh giác',
                        status: (_advanceSafety?.canBook ?? true) ? 'Cho chạy' : 'Hạn chế',
                        statusColor: (_advanceSafety?.canBook ?? true)
                            ? const Color(0xFF059669)
                            : const Color(0xFFD97706),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Filter Tabs
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _buildFilterPill('all', 'Tất cả (${_db.notifications.length})'),
                      const SizedBox(width: 8),
                      _buildFilterPill('sea_safety', 'An toàn biển'),
                      const SizedBox(width: 8),
                      _buildFilterPill('order', 'Đơn hàng mới'),
                      const SizedBox(width: 8),
                      _buildFilterPill('system', 'Hệ thống'),
                    ],
                  ),
                ),
                const SizedBox(height: 14),

                // Chỉ khi nào biển có rủi ro/bão thật sự mới hiện banner đỏ cảnh báo ca chạy
                if (isSeaWeatherRisky && targetSlot != null) ...[
                    // High-Risk Alert Card
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFF7ED),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFFFDBA74)),
                        boxShadow: const [
                          BoxShadow(color: Color(0x06000000), blurRadius: 8, offset: Offset(0, 2)),
                        ],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(6),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFEA580C),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: const Icon(Icons.warning_amber_rounded, size: 16, color: Colors.white),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text(
                                      'Cảnh báo thời tiết ca chạy',
                                      style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFF9A3412)),
                                    ),
                                    Text(
                                      'Ca ảnh hưởng: ${_formatTime(targetSlot.startTime)} - ${_formatTime(targetSlot.endTime)}',
                                      style: const TextStyle(fontSize: 12, color: Color(0xFFC2410C)),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Text(
                            'Sức gió đo được ${weather.windSpeedKmh.toStringAsFixed(0)} km/h, sóng cao ${weather.waveHeightM.toStringAsFixed(1)} m vượt ngưỡng an toàn. Khuyến nghị tạm dừng hoặc đổi ca cho khách.',
                            style: const TextStyle(fontSize: 12, color: Color(0xFF7C2D12), height: 1.35),
                          ),
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Expanded(
                                child: ElevatedButton(
                                  onPressed: () async {
                                    await VendorSlotRepository().toggleBlockSlot(targetSlot.slotId, !targetSlot.isBlocked);
                                    if (!mounted) return;
                                    ToastNotification.showSuccess(
                                      context,
                                      'Đã cập nhật trạng thái slot ${_formatTime(targetSlot.startTime)}',
                                    );
                                  },
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: const Color(0xFFEA580C),
                                    foregroundColor: Colors.white,
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                                    elevation: 0,
                                    padding: const EdgeInsets.symmetric(vertical: 10),
                                  ),
                                  child: Text(
                                    targetSlot.isBlocked ? 'Mở lại ca chạy' : 'Tạm khóa ca này',
                                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: OutlinedButton(
                                  onPressed: () => widget.onNavigateTab?.call(2),
                                  style: OutlinedButton.styleFrom(
                                    side: const BorderSide(color: Color(0xFFEA580C)),
                                    foregroundColor: const Color(0xFFEA580C),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                                    padding: const EdgeInsets.symmetric(vertical: 10),
                                  ),
                                  child: const Text('Xem lịch ca chạy', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                // Notification List Section
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.notifications_active_rounded, size: 17, color: AppColors.secondary),
                        SizedBox(width: 6),
                        Text(
                          'Danh sách thông báo',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                        ),
                      ],
                    ),
                    Text(
                      '${notifications.length} tin',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.tertiary),
                    ),
                  ],
                ),
                const SizedBox(height: 10),

                if (notifications.isEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
                    width: double.infinity,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: const Color(0xFFE2E8F0)),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(Icons.notifications_off_outlined, size: 40, color: Color(0xFFCBD5E1)),
                        SizedBox(height: 10),
                        Text(
                          'Chưa có thông báo nào trong mục này',
                          style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                        ),
                      ],
                    ),
                  )
                else
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: notifications.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final notif = notifications[index];
                      return _buildNotificationItem(notif);
                    },
                  ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildFilterPill(String key, String label) {
    final active = _selectedFilter == key;
    return InkWell(
      onTap: () => setState(() => _selectedFilter = key),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: active ? AppColors.secondary : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          boxShadow: active ? AppShapes.shadowSm : null,
        ),
        child: LocalizedText(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: active ? FontWeight.bold : FontWeight.normal,
            color: active ? AppColors.onSecondary : AppColors.onSurfaceVariant,
          ),
        ),
      ),
    );
  }

  Widget _buildNotificationItem(dynamic notif) {
    IconData icon = Icons.notifications;
    Color iconColor = AppColors.secondary;
    Color bgColor = AppColors.secondaryContainer;

    if (notif.type == 'WEATHER_WARNING') {
      icon = Icons.storm;
      iconColor = AppColors.error;
      bgColor = AppColors.errorContainer;
    } else if (notif.type == 'NEW_ORDER') {
      icon = Icons.shopping_cart_checkout;
      iconColor = AppColors.secondary;
      bgColor = AppColors.secondaryContainer;
    } else if (notif.type == 'DISPUTE') {
      icon = Icons.report_problem;
      iconColor = AppColors.primary;
      bgColor = AppColors.primaryFixed;
    } else if (notif.type == 'SYSTEM') {
      icon = Icons.verified_user;
      iconColor = AppColors.tertiary;
      bgColor = AppColors.surfaceContainerHigh;
    }

    return InkWell(
      onTap: () {
        _db.markNotificationRead(notif.notificationId);
      },
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: notif.isRead ? AppColors.surfaceContainerLowest : AppColors.surfaceContainerLow,
          borderRadius: AppShapes.radiusMd,
          border: notif.isRead ? null : Border.all(color: AppColors.secondaryContainer, width: 1.5),
          boxShadow: AppShapes.shadowSm,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: bgColor,
              ),
              child: Icon(icon, color: iconColor, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          notif.title,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: notif.isRead ? FontWeight.w600 : FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      LocalizedText(
                        '${notif.createdAt.hour.toString().padLeft(2, '0')}:${notif.createdAt.minute.toString().padLeft(2, '0')}',
                        style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  LocalizedText(notif.content, style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopMarineKpiCard({
    required IconData icon,
    required Color iconColor,
    required Color iconBg,
    required String label,
    required String value,
    required String status,
    required Color statusColor,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(color: Color(0x06000000), blurRadius: 6, offset: Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(color: iconBg, borderRadius: BorderRadius.circular(8)),
                child: Icon(icon, color: iconColor, size: 16),
              ),
              Text(
                status,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: statusColor),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: const TextStyle(
              fontSize: 14.5,
              fontWeight: FontWeight.w800,
              color: AppColors.navyAnchor,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 10.5, color: AppColors.tertiary),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
