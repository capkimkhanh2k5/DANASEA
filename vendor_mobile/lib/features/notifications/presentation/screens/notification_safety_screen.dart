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

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LocalizedText('DANASEA VENDOR',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.8,
                    )),
            LocalizedText(
              _db.vendor.businessName.isNotEmpty ? _db.vendor.businessName : 'nhà cung cấp',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.done_all, color: AppColors.secondary),
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
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Screen Title Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: const [
                            Icon(Icons.waves, size: 14, color: AppColors.secondary),
                            SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                'HỆ THỐNG CỨU HỘ & QUAN TRẮC',
                                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary, letterSpacing: 0.5),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        const LocalizedText(
                          'Thông báo & Cảnh báo an toàn',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.surfaceContainerLow,
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: const Icon(Icons.radar, color: AppColors.secondary, size: 22),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Filter Tabs
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _buildFilterPill('all', 'Tất cả thông báo'),
                    const SizedBox(width: 8),
                    _buildFilterPill('sea_safety', 'An toàn biển'),
                    const SizedBox(width: 8),
                    _buildFilterPill('order', 'Đơn hàng mới'),
                    const SizedBox(width: 8),
                    _buildFilterPill('system', 'Hệ thống'),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Show Sea Safety monitoring cards if "all" or "sea_safety"
              if (_selectedFilter == 'all' || _selectedFilter == 'sea_safety') ...[
                // Slot Safety Monitor Card
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Row(
                        children: [
                          Icon(Icons.sailing, size: 18, color: AppColors.secondary),
                          SizedBox(width: 6),
                          Expanded(
                            child: LocalizedText(
                              'Giám sát an toàn theo slot',
                              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer.withOpacity(0.5),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: LocalizedText(
                        weather.locationKey.isNotEmpty ? 'Trạm ${weather.locationKey}' : 'Trạm Đà Nẵng',
                        style: const TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusMd,
                    boxShadow: AppShapes.shadowSm,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  _db.slots.isNotEmpty
                                      ? 'Khung giờ hoạt động: ${_db.slots.first.startTime} - ${_db.slots.first.endTime}'
                                      : 'Khung giờ hoạt động hôm nay',
                                  style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                LocalizedText(
                                  _db.services.isNotEmpty && _db.services.first.locationName.isNotEmpty
                                      ? _db.services.first.locationName
                                      : weather.locationName,
                                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 6),
                          Flexible(
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                              decoration: BoxDecoration(
                                color: (_advanceSafety?.canBook ?? true)
                                    ? AppColors.secondaryContainer
                                    : AppColors.errorContainer,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    (_advanceSafety?.canBook ?? true)
                                        ? Icons.verified
                                        : Icons.warning_amber_rounded,
                                    size: 12,
                                    color: (_advanceSafety?.canBook ?? true)
                                        ? AppColors.onSecondaryContainer
                                        : AppColors.onErrorContainer,
                                  ),
                                  const SizedBox(width: 3),
                                  Flexible(
                                    child: LocalizedText(
                                      (_advanceSafety?.canBook ?? true)
                                          ? 'Đủ điều kiện xuất bến'
                                          : 'Cần theo dõi sóng gió',
                                      style: TextStyle(
                                        fontSize: 10,
                                        fontWeight: FontWeight.bold,
                                        color: (_advanceSafety?.canBook ?? true)
                                            ? AppColors.onSecondaryContainer
                                            : AppColors.onErrorContainer,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: _buildMetricBox(
                              icon: Icons.air,
                              label: 'Sức gió',
                              value: '${weather.windSpeedKmh.toStringAsFixed(0)} km/h',
                              sub: _db.services.isNotEmpty && _db.services.first.minWindKmh > 0
                                  ? 'Tối thiểu: ${_db.services.first.minWindKmh.toStringAsFixed(0)}'
                                  : (_advanceSafety?.safetyStatus ?? 'Trực tuyến'),
                              color: AppColors.secondary,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildMetricBox(
                              icon: Icons.tsunami,
                              label: 'Độ cao sóng',
                              value: '${weather.waveHeightM.toStringAsFixed(1)} m',
                              sub: _db.services.isNotEmpty && _db.services.first.maxWaveM > 0
                                  ? 'Ngưỡng: <${_db.services.first.maxWaveM.toStringAsFixed(1)}'
                                  : (_advanceSafety?.safetyStatus ?? 'Trực tuyến'),
                              color: AppColors.secondary,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildMetricBox(
                              icon: Icons.wb_sunny_outlined,
                              label: 'Lượng mưa',
                              value: '${weather.precipitationMm.toStringAsFixed(1)} mm',
                              sub: weather.precipitationMm <= 0.5 ? 'Trời quang' : 'Có mưa',
                              color: AppColors.secondary,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.info_outline, size: 16, color: AppColors.secondary),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    (_advanceSafety != null && _advanceSafety!.summaryMessage.isNotEmpty)
                                        ? _advanceSafety!.summaryMessage
                                        : ((weather.windSpeedKmh <= 25 && weather.waveHeightM <= 1.2)
                                            ? 'Mặt nước êm ái, hướng gió thuận lợi cho các hoạt động thể thao biển.'
                                            : 'Sóng gió đang tăng cao, vui lòng kiểm tra trang thiết bị bảo hộ trước khi xuất bến.'),
                                    style: const TextStyle(fontSize: 12, color: AppColors.onSurface),
                                  ),
                                  if (_advanceSafety != null && _advanceSafety!.advisoryDetails.isNotEmpty) ...[
                                    const SizedBox(height: 3),
                                    Text(
                                      _advanceSafety!.advisoryDetails.first,
                                      style: const TextStyle(fontSize: 11, color: AppColors.onSurfaceVariant),
                                    ),
                                  ],
                                  const SizedBox(height: 4),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      LocalizedText(
                                        'Cập nhật lúc: ${weather.fetchedAt.hour.toString().padLeft(2, "0")}:${weather.fetchedAt.minute.toString().padLeft(2, "0")} • Trạng thái: ${_advanceSafety?.safetyStatus ?? "SAFE"}',
                                        style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                                      ),
                                      InkWell(
                                        onTap: _loadingWeather ? null : _refreshLiveMarineSafety,
                                        child: Text(
                                          _loadingWeather ? 'Đang tải...' : 'Làm mới',
                                          style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.secondary),
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
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Slot Weather Alert Card
                const Row(
                  children: [
                    Icon(Icons.warning_amber_rounded, size: 18, color: AppColors.primaryContainer),
                    SizedBox(width: 6),
                    Expanded(
                      child: LocalizedText(
                        'Cảnh báo slot thay đổi thời tiết',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusMd,
                    boxShadow: AppShapes.shadowSm,
                  ),
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
                                  decoration: const BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: AppColors.primaryContainer,
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: LocalizedText(
                                    _db.slots.isNotEmpty
                                        ? 'Khung giờ liên quan: ${_db.slots.last.startTime}'
                                        : 'Khung giờ theo dõi tiếp theo',
                                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: weather.windSpeedKmh > 25 ? AppColors.errorContainer : AppColors.secondaryContainer,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: LocalizedText(
                              weather.windSpeedKmh > 25 ? 'Gió giật cục bộ' : 'Theo dõi định kỳ',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                color: weather.windSpeedKmh > 25 ? AppColors.onErrorContainer : AppColors.onSecondaryContainer,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 34,
                              height: 34,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: weather.windSpeedKmh > 25 ? AppColors.errorContainer : AppColors.secondaryContainer,
                              ),
                              child: Icon(
                                weather.windSpeedKmh > 25 ? Icons.storm : Icons.air,
                                color: weather.windSpeedKmh > 25 ? AppColors.error : AppColors.secondary,
                                size: 18,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    'Sức gió ghi nhận: ${weather.windSpeedKmh.toStringAsFixed(0)} km/h • Sóng: ${weather.waveHeightM.toStringAsFixed(1)} m',
                                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  ),
                                  const LocalizedText('Kết quả đánh giá theo khung giờ từ máy chủ', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 8),
                      const LocalizedText(
                        'Dữ liệu gió được cập nhật từ nguồn thời tiết cảng vụ. Theo dõi kết quả đánh giá an toàn trước giờ cung cấp dịch vụ.',
                        style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: ElevatedButton(
                              onPressed: _db.slots.isEmpty
                                  ? null
                                  : () async {
                                      final targetSlot = _db.slots.last;
                                      await VendorSlotRepository().toggleBlockSlot(targetSlot.slotId, !targetSlot.isBlocked);
                                      if (!mounted) return;
                                      ToastNotification.showSuccess(
                                        context,
                                        'Đã cập nhật trạng thái slot ${targetSlot.startTime}',
                                      );
                                    },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.primaryContainer,
                                foregroundColor: AppColors.onPrimary,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                                elevation: 0,
                                padding: const EdgeInsets.symmetric(vertical: 8),
                              ),
                              child: const LocalizedText('Tạm khóa slot này', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () => widget.onNavigateTab?.call(2), // Calendar tab
                              style: OutlinedButton.styleFrom(
                                side: const BorderSide(color: AppColors.outlineVariant),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                                padding: const EdgeInsets.symmetric(vertical: 8),
                              ),
                              child: const LocalizedText('Xem lịch hoạt động', style: TextStyle(fontSize: 11, color: AppColors.onSurface)),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
              ],

              // Notification List Section
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Expanded(
                    child: Row(
                      children: [
                        Icon(Icons.notifications_active, size: 18, color: AppColors.secondary),
                        SizedBox(width: 6),
                        Expanded(
                          child: LocalizedText(
                            'Thông báo cập nhật',
                            style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  LocalizedText('Thời gian thực (${notifications.length})', style: const TextStyle(fontSize: 11, color: AppColors.tertiary)),
                ],
              ),
              const SizedBox(height: 10),

              if (notifications.isEmpty)
                Container(
                  padding: const EdgeInsets.all(24),
                  width: double.infinity,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppShapes.radiusMd,
                  ),
                  child: const Center(
                    child: LocalizedText('Không có thông báo nào', style: TextStyle(color: AppColors.tertiary)),
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
              const SizedBox(height: 20),
            ],
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

  Widget _buildMetricBox({
    required IconData icon,
    required String label,
    required String value,
    required String sub,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 14, color: color),
              const SizedBox(width: 4),
              Flexible(
                child: LocalizedText(
                  label,
                  style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          LocalizedText(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
          const SizedBox(height: 2),
          LocalizedText(sub, style: TextStyle(fontSize: 9, color: color, fontWeight: FontWeight.bold), maxLines: 1, overflow: TextOverflow.ellipsis),
        ],
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
}
