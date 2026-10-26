import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/notification_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../orders/presentation/screens/order_detail_screen.dart';
import '../../../refund_dispute/presentation/screens/refund_tracking_screen.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final NotificationRepository _repository = NotificationRepository.instance;

  List<NotificationModel> _notifications = const [];
  bool _isLoading = true;
  bool _isMarkingAll = false;
  final Set<String> _markingIds = <String>{};
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadNotifications();
  }

  Future<void> _loadNotifications() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final results = await Future.wait([
        _repository.listNotifications(page: 0, size: 50),
        _repository.fetchUnreadCount(),
      ]);
      if (!mounted) return;
      setState(() {
        _notifications = results[0] as List<NotificationModel>;
        _isLoading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.message;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorMessage = 'Không thể tải danh sách thông báo.';
        _isLoading = false;
      });
    }
  }

  Future<void> _markAsRead(NotificationModel notif) async {
    if (notif.isRead || _markingIds.contains(notif.id)) return;
    setState(() => _markingIds.add(notif.id));
    try {
      final updated = await _repository.markAsRead(notif.id);
      if (!mounted) return;
      setState(() {
        _notifications =
            _notifications
                .map((item) => item.id == notif.id ? updated : item)
                .toList();
      });
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText('Không thể cập nhật trạng thái thông báo.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _markingIds.remove(notif.id));
      }
    }
  }

  Future<void> _markAllAsRead() async {
    if (_isMarkingAll) return;
    setState(() => _isMarkingAll = true);
    try {
      final count = await _repository.markAllAsRead();
      if (!mounted) return;
      setState(() {
        _notifications =
            _notifications
                .map(
                  (n) => NotificationModel(
                    id: n.id,
                    userId: n.userId,
                    type: n.type,
                    title: n.title,
                    body: n.body,
                    relatedEntityType: n.relatedEntityType,
                    relatedEntityId: n.relatedEntityId,
                    isRead: true,
                    createdAt: n.createdAt,
                  ),
                )
                .toList();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${tr(context, 'Đã đánh dấu đọc tất cả thông báo')} ($count)',
          ),
          backgroundColor: AppColors.secondary,
        ),
      );
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText('Không thể đánh dấu đọc tất cả thông báo.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isMarkingAll = false);
    }
  }

  String _formatTimestamp(DateTime dt) {
    final local = dt.toLocal();
    return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year} ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final hasUnread = _notifications.any((n) => !n.isRead);

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              'Thông báo',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            if (_repository.unreadCount > 0) ...[
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  '${_repository.unreadCount}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ],
        ),
        centerTitle: true,
        actions: [
          if (hasUnread)
            IconButton(
              tooltip: tr(context, 'Đánh dấu đã đọc tất cả'),
              icon:
                  _isMarkingAll
                      ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                      : const Icon(
                        Icons.done_all,
                        color: AppColors.secondary,
                      ),
              onPressed: _isMarkingAll ? null : _markAllAsRead,
            ),
          IconButton(
            icon: const Icon(Icons.refresh, color: AppColors.onSurface),
            onPressed: _isLoading ? null : _loadNotifications,
          ),
        ],
      ),
      body:
          _isLoading
              ? const Center(child: CircularProgressIndicator())
              : _errorMessage != null
              ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.error_outline,
                        size: 48,
                        color: AppColors.error,
                      ),
                      const SizedBox(height: 12),
                      LocalizedText(
                        _errorMessage!,
                        textAlign: TextAlign.center,
                        style: AppTypography.bodyMd(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton.icon(
                        onPressed: _loadNotifications,
                        icon: const Icon(Icons.refresh, size: 18),
                        label: const LocalizedText('Thử lại'),
                      ),
                    ],
                  ),
                ),
              )
              : _notifications.isEmpty
              ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.notifications_none_outlined,
                        size: 48,
                        color: AppColors.outline,
                      ),
                      const SizedBox(height: 12),
                      LocalizedText(
                        'Bạn chưa có thông báo nào.',
                        style: AppTypography.bodyMd(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              )
              : RefreshIndicator(
                onRefresh: _loadNotifications,
                child: ListView.separated(
                  padding: const EdgeInsets.all(AppShapes.gutterMobile),
                  itemCount: _notifications.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final notif = _notifications[index];
                    return _buildNotificationCard(context, notif);
                  },
                ),
              ),
    );
  }

  Widget _buildNotificationCard(BuildContext context, NotificationModel notif) {
    IconData icon;
    Color iconColor;
    Color iconBg;

    final upperType = notif.type.toUpperCase();
    final isOrderType =
        upperType.contains('ORDER') ||
        upperType.contains('PAYMENT') ||
        upperType.contains('BOOKING') ||
        (notif.relatedEntityType ?? '').toLowerCase().contains('order');
    final isWeatherType =
        upperType.contains('WEATHER') || upperType.contains('DISRUPTION');
    final isRefundType =
        upperType.contains('REFUND') ||
        (notif.relatedEntityType ?? '').toLowerCase().contains('refund');

    if (isOrderType) {
      icon = Icons.confirmation_number_outlined;
      iconColor = AppColors.primary;
      iconBg = AppColors.primaryContainer.withValues(alpha: 0.15);
    } else if (isWeatherType) {
      icon = Icons.air;
      iconColor = AppColors.secondary;
      iconBg = AppColors.secondary.withValues(alpha: 0.15);
    } else {
      icon = Icons.price_check;
      iconColor = AppColors.secondaryTeal;
      iconBg = AppColors.secondaryFixed.withValues(alpha: 0.3);
    }

    final shortEntityCode =
        notif.relatedEntityId != null && notif.relatedEntityId!.length > 8
            ? notif.relatedEntityId!.substring(0, 8).toUpperCase()
            : notif.relatedEntityId;

    return InkWell(
      onTap: () => _markAsRead(notif),
      borderRadius: AppShapes.radiusDefault,
      child: Container(
        padding: const EdgeInsets.all(AppShapes.spaceSm),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: AppShapes.radiusDefault,
          boxShadow: AppShapes.shadowLevel1,
          border: Border.all(
            color:
                notif.isRead
                    ? AppColors.borderSubtle
                    : AppColors.primary.withValues(alpha: 0.4),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: iconBg,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icon, size: 20, color: iconColor),
                ),
                const SizedBox(width: 10),
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
                              style: AppTypography.labelLg(
                                color: AppColors.onSurface,
                                fontWeight:
                                    notif.isRead
                                        ? FontWeight.w500
                                        : FontWeight.w700,
                              ),
                            ),
                          ),
                          if (!notif.isRead)
                            Container(
                              width: 8,
                              height: 8,
                              decoration: const BoxDecoration(
                                color: AppColors.primary,
                                shape: BoxShape.circle,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        _formatTimestamp(notif.createdAt),
                        style: AppTypography.bodySm(
                          color: AppColors.outline,
                        ).copyWith(fontSize: 10),
                      ),
                      const SizedBox(height: 6),
                      LocalizedText(
                        notif.body,
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (isOrderType &&
                notif.relatedEntityId != null &&
                notif.relatedEntityId!.isNotEmpty) ...[
              const SizedBox(height: 10),
              InkWell(
                onTap: () async {
                  if (!notif.isRead) {
                    await _markAsRead(notif);
                  }
                  if (!context.mounted) return;
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder:
                          (_) => OrderDetailScreen(
                            orderId: notif.relatedEntityId!,
                          ),
                    ),
                  );
                },
                borderRadius: BorderRadius.circular(10),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            const Icon(
                              Icons.qr_code_2,
                              size: 18,
                              color: AppColors.primary,
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                '${tr(context, 'Xem đơn hàng')} #$shortEntityCode',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.labelSm(
                                  color: AppColors.primary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.chevron_right,
                        size: 16,
                        color: AppColors.primary,
                      ),
                    ],
                  ),
                ),
              ),
            ],
            if (isRefundType) ...[
              const SizedBox(height: 10),
              InkWell(
                onTap: () async {
                  if (!notif.isRead) {
                    await _markAsRead(notif);
                  }
                  if (!context.mounted) return;
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder:
                          (_) => RefundTrackingScreen(
                            orderId: notif.relatedEntityId,
                          ),
                    ),
                  );
                },
                borderRadius: BorderRadius.circular(10),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            const Icon(
                              Icons.price_check,
                              size: 18,
                              color: AppColors.secondary,
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: LocalizedText(
                                'Xem chi tiết hoàn tiền',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: AppTypography.labelSm(
                                  color: AppColors.secondary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.chevron_right,
                        size: 16,
                        color: AppColors.secondary,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
