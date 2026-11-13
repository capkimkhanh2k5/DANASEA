import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/widgets/toast_notification.dart';

class NotificationSafetyScreen extends StatefulWidget {
  final Function(int)? onNavigateTab;

  const NotificationSafetyScreen({super.key, this.onNavigateTab});

  @override
  State<NotificationSafetyScreen> createState() => _NotificationSafetyScreenState();
}

class _NotificationSafetyScreenState extends State<NotificationSafetyScreen> {
  final _db = VendorStore.instance;
  String _selectedFilter = 'all'; // all, sea_safety, order, system

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
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
            LocalizedText('Danang Ocean Club',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    )),
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
                      child: const LocalizedText('Trạm Mỹ Khê #02', style: TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.bold)),
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
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText('Khung giờ hoạt động: 05:00 - 07:00', style: TextStyle(fontSize: 11, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                                LocalizedText('Bãi biển Mỹ Khê', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                              ],
                            ),
                          ),
                          const SizedBox(width: 6),
                          Flexible(
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                              decoration: BoxDecoration(
                                color: AppColors.secondaryContainer,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: const Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.verified, size: 12, color: AppColors.onSecondaryContainer),
                                  SizedBox(width: 3),
                                  Flexible(
                                    child: LocalizedText(
                                      'Đủ điều kiện xuất bến',
                                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer),
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
                              sub: 'Ngưỡng: <25',
                              color: AppColors.secondary,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildMetricBox(
                              icon: Icons.tsunami,
                              label: 'Độ cao sóng',
                              value: '${weather.waveHeightM.toStringAsFixed(1)} m',
                              sub: 'Ngưỡng: <1.2',
                              color: AppColors.secondary,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _buildMetricBox(
                              icon: Icons.wb_sunny_outlined,
                              label: 'Lượng mưa',
                              value: '0.0 mm',
                              sub: 'Trời quang',
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
                        child: const Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(Icons.info_outline, size: 16, color: AppColors.secondary),
                            SizedBox(width: 8),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    'Mặt nước êm ái, hướng gió thuận lợi cho hoạt động chèo SUP và lướt sóng có huấn luyện viên.',
                                    style: TextStyle(fontSize: 12, color: AppColors.onSurface),
                                  ),
                                  SizedBox(height: 4),
                                  LocalizedText('Đánh giá lúc: 04:30 sáng nay cho slot 05:00 - 07:00',
                                      style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
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
                                const Expanded(
                                  child: LocalizedText(
                                    'Khung giờ liên quan: 15:30',
                                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
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
                              color: AppColors.errorContainer,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: const LocalizedText('Gió giật cục bộ',
                                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.onErrorContainer)),
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
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.errorContainer,
                              ),
                              child: const Icon(Icons.storm, color: AppColors.error, size: 18),
                            ),
                            const SizedBox(width: 10),
                            const Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText('Sức gió ghi nhận: 28 km/h', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface)),
                                  LocalizedText('Kết quả đánh giá theo khung giờ', style: TextStyle(fontSize: 10, color: AppColors.tertiary)),
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
                              onPressed: () {
                                final afternoonSlot = _db.slots.firstWhere((s) => s.startTime.startsWith('15:'), orElse: () => _db.slots.first);
                                _db.toggleSlotBlocked(afternoonSlot.slotId);
                                ToastNotification.showSuccess(context, 'Đã tạm khóa slot 15:30 để đảm bảo an toàn gió');
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
