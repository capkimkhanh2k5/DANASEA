import 'package:flutter/foundation.dart';
import '../auth/auth_session.dart';
import '../models/danasea_models.dart';

class NotificationRepository extends ChangeNotifier {
  NotificationRepository._();
  static final NotificationRepository instance = NotificationRepository._();

  final AuthSession _session = AuthSession.instance;

  int _unreadCount = 0;
  int get unreadCount => _unreadCount;

  /// GET /api/notifications
  Future<List<NotificationModel>> listNotifications({
    int page = 0,
    int size = 20,
  }) async {
    final safePage = page < 0 ? 0 : page;
    final safeSize = size.clamp(1, 100);
    final body = await _session.requestJson(
      'GET',
      '/api/notifications?page=$safePage&size=$safeSize',
      authenticated: true,
    );

    final rawList = _extractContentList(body);
    final items = rawList.map(_mapNotification).toList();
    return items;
  }

  /// GET /api/notifications/unread-count
  Future<int> fetchUnreadCount() async {
    if (!_session.isAuthenticated) {
      if (_unreadCount != 0) {
        _unreadCount = 0;
        notifyListeners();
      }
      return 0;
    }

    final body = await _session.requestJson(
      'GET',
      '/api/notifications/unread-count',
      authenticated: true,
    );

    if (body is Map) {
      final count = _asInt(body['unreadCount']) ?? 0;
      if (_unreadCount != count) {
        _unreadCount = count;
        notifyListeners();
      }
      return count;
    }
    return _unreadCount;
  }

  /// PATCH /api/notifications/{id}/read
  Future<NotificationModel> markAsRead(String notificationId) async {
    final trimmed = notificationId.trim();
    if (trimmed.isEmpty) {
      throw const AuthFailure('Mã thông báo không hợp lệ.');
    }

    final body = await _session.requestJson(
      'PATCH',
      '/api/notifications/$trimmed/read',
      authenticated: true,
    );

    if (body is! Map) {
      throw const AuthFailure('Phản hồi đánh dấu đã đọc không hợp lệ.');
    }

    final updated = _mapNotification(Map<String, dynamic>.from(body));
    if (_unreadCount > 0) {
      _unreadCount = _unreadCount - 1;
      notifyListeners();
    }
    return updated;
  }

  /// PATCH /api/notifications/read-all
  Future<int> markAllAsRead() async {
    final body = await _session.requestJson(
      'PATCH',
      '/api/notifications/read-all',
      authenticated: true,
    );

    int updatedCount = 0;
    if (body is Map) {
      updatedCount = _asInt(body['updatedCount']) ?? 0;
    }
    if (_unreadCount != 0) {
      _unreadCount = 0;
      notifyListeners();
    }
    return updatedCount;
  }

  List<Map<String, dynamic>> _extractContentList(Object? body) {
    if (body is List) {
      return body
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList();
    }
    if (body is Map) {
      final content = body['content'];
      if (content is List) {
        return content
            .whereType<Map>()
            .map((item) => Map<String, dynamic>.from(item))
            .toList();
      }
    }
    return const [];
  }

  NotificationModel _mapNotification(Map<String, dynamic> map) {
    return NotificationModel(
      id: _asString(map['id']) ?? '',
      userId: _asString(map['userId']) ?? '',
      type: _asString(map['type']) ?? 'GENERAL',
      title: _asString(map['title']) ?? 'Thông báo hệ thống',
      body: _asString(map['body']) ?? '',
      relatedEntityType: _asString(map['relatedEntityType']),
      relatedEntityId: _asString(map['relatedEntityId']),
      isRead: map['isRead'] == true || map['read'] == true,
      createdAt:
          _parseDateTime(map['createdAt']) ??
          _parseDateTime(map['sentAt']) ??
          DateTime.now(),
    );
  }

  String? _asString(Object? value) {
    if (value == null) return null;
    final text = value.toString().trim();
    return text.isEmpty ? null : text;
  }

  int? _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value.trim());
    return null;
  }

  DateTime? _parseDateTime(Object? value) {
    final text = _asString(value);
    if (text == null) return null;
    return DateTime.tryParse(text)?.toLocal();
  }
}
