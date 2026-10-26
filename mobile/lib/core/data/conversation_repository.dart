import '../auth/auth_session.dart';
import '../models/danasea_models.dart';
import 'order_repository.dart';

class ConversationRepository {
  ConversationRepository._();
  static final ConversationRepository instance = ConversationRepository._();

  final AuthSession _session = AuthSession.instance;

  /// POST /api/conversations
  Future<ConversationModel> createOrGetConversation({
    required String masterOrderId,
    String? vendorId,
    String? fallbackVendorName,
    String? fallbackServiceName,
  }) async {
    final trimmedOrderId = masterOrderId.trim();
    if (trimmedOrderId.isEmpty) {
      throw const AuthFailure('Mã đơn hàng không hợp lệ.');
    }

    final payload = <String, dynamic>{
      'masterOrderId': trimmedOrderId,
      if (vendorId != null && vendorId.trim().isNotEmpty)
        'vendorId': vendorId.trim(),
    };

    final body = await _session.requestJson(
      'POST',
      '/api/conversations',
      body: payload,
      authenticated: true,
    );

    if (body is! Map) {
      throw const AuthFailure('Phản hồi hội thoại không hợp lệ.');
    }

    return _mapConversation(
      Map<String, dynamic>.from(body),
      fallbackVendorName: fallbackVendorName,
      fallbackServiceName: fallbackServiceName,
    );
  }

  /// GET /api/conversations
  Future<List<ConversationModel>> listConversations({
    int page = 0,
    int size = 50,
  }) async {
    final safePage = page < 0 ? 0 : page;
    final safeSize = size.clamp(1, 100);
    final body = await _session.requestJson(
      'GET',
      '/api/conversations?page=$safePage&size=$safeSize',
      authenticated: true,
    );

    final rawList = _extractContentList(body);

    // Best-effort lookup of vendorName & serviceName from user's orders
    final orderVendorNames = <String, String>{};
    final orderServiceNames = <String, String>{};
    try {
      final orders = await OrderRepository.instance.listMyOrders(
        page: 0,
        size: 50,
      );
      for (final order in orders) {
        for (final sub in order.subOrders) {
          final key = '${order.id}:${sub.vendorId}';
          if (sub.vendorName.trim().isNotEmpty) {
            orderVendorNames[key] = sub.vendorName.trim();
            orderVendorNames[sub.vendorId] = sub.vendorName.trim();
          }
          if (sub.serviceName.trim().isNotEmpty) {
            orderServiceNames[key] = sub.serviceName.trim();
            orderServiceNames[order.id] = sub.serviceName.trim();
          }
        }
      }
    } catch (_) {
      // Ignore metadata enrichment failure; conversation list still renders.
    }

    return rawList.map((map) {
      final masterOrderId = _asString(map['masterOrderId']);
      final vendorId = _asString(map['vendorId']) ?? '';
      final compositeKey =
          masterOrderId != null ? '$masterOrderId:$vendorId' : vendorId;
      return _mapConversation(
        map,
        fallbackVendorName:
            orderVendorNames[compositeKey] ?? orderVendorNames[vendorId],
        fallbackServiceName:
            orderServiceNames[compositeKey] ??
            (masterOrderId != null ? orderServiceNames[masterOrderId] : null),
      );
    }).toList();
  }

  /// GET /api/conversations/{id}/messages
  Future<List<ChatMessageModel>> listMessages(
    String conversationId, {
    DateTime? after,
    int? afterSequence,
    int page = 0,
    int size = 50,
  }) async {
    final trimmedId = conversationId.trim();
    if (trimmedId.isEmpty) {
      throw const AuthFailure('Mã hội thoại không hợp lệ.');
    }

    final safePage = page < 0 ? 0 : page;
    final safeSize = size.clamp(1, 100);
    final queryParams = <String, String>{
      'page': '$safePage',
      'size': '$safeSize',
    };
    if (afterSequence != null && afterSequence >= 0) {
      queryParams['afterSequence'] = '$afterSequence';
    } else if (after != null) {
      queryParams['after'] = after.toUtc().toIso8601String();
    }

    final queryString = Uri(queryParameters: queryParams).query;
    final body = await _session.requestJson(
      'GET',
      '/api/conversations/$trimmedId/messages?$queryString',
      authenticated: true,
    );

    final currentUserId = _session.currentUser?.id;
    final rawList = _extractContentList(body);
    return rawList
        .map(
          (map) => _mapMessage(
            map,
            currentUserId: currentUserId,
            fallbackConversationId: trimmedId,
          ),
        )
        .toList();
  }

  /// POST /api/conversations/{id}/messages
  Future<ChatMessageModel> sendMessage({
    required String conversationId,
    required String content,
    String? attachmentUrl,
  }) async {
    final trimmedId = conversationId.trim();
    if (trimmedId.isEmpty) {
      throw const AuthFailure('Mã hội thoại không hợp lệ.');
    }
    final trimmedContent = content.trim();
    if (trimmedContent.isEmpty) {
      throw const AuthFailure('Nội dung tin nhắn không được để trống.');
    }

    final payload = <String, dynamic>{
      'content': trimmedContent,
      if (attachmentUrl != null && attachmentUrl.trim().isNotEmpty)
        'attachmentUrl': attachmentUrl.trim(),
    };

    final body = await _session.requestJson(
      'POST',
      '/api/conversations/$trimmedId/messages',
      body: payload,
      authenticated: true,
    );

    if (body is! Map) {
      throw const AuthFailure('Phản hồi gửi tin nhắn không hợp lệ.');
    }

    return _mapMessage(
      Map<String, dynamic>.from(body),
      currentUserId: _session.currentUser?.id,
      fallbackConversationId: trimmedId,
      forceIsMe: true,
    );
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

  ConversationModel _mapConversation(
    Map<String, dynamic> map, {
    String? fallbackVendorName,
    String? fallbackServiceName,
  }) {
    final id = _asString(map['id']) ?? '';
    final customerId = _asString(map['customerId']) ?? '';
    final vendorId = _asString(map['vendorId']) ?? '';
    final masterOrderId = _asString(map['masterOrderId']);
    final createdAt = _parseDateTime(map['createdAt']) ?? DateTime.now();
    final updatedAt = _parseDateTime(map['updatedAt']) ?? createdAt;

    String lastMessageText = 'Bắt đầu cuộc trò chuyện với đối tác';
    DateTime lastMessageTime = updatedAt;
    final lastMsgRaw = map['lastMessage'];
    if (lastMsgRaw is Map) {
      final content = _asString(lastMsgRaw['content']);
      if (content != null && content.isNotEmpty) {
        lastMessageText = content;
      } else if (_asString(lastMsgRaw['attachmentUrl']) != null) {
        lastMessageText = '[Hình ảnh đính kèm]';
      }
      final msgCreated = _parseDateTime(lastMsgRaw['createdAt']);
      if (msgCreated != null) {
        lastMessageTime = msgCreated;
      }
    }

    final shortVendorCode =
        vendorId.length >= 6
            ? vendorId.substring(0, 6).toUpperCase()
            : vendorId.toUpperCase();
    final resolvedVendorName =
        _asString(map['vendorName']) ??
        fallbackVendorName ??
        (shortVendorCode.isNotEmpty
            ? 'Đối tác #$shortVendorCode'
            : 'Đối tác DanaSea');

    return ConversationModel(
      id: id,
      customerId: customerId,
      vendorId: vendorId,
      vendorName: resolvedVendorName,
      vendorAvatar: _asString(map['vendorAvatar']),
      masterOrderId: masterOrderId,
      serviceName: _asString(map['serviceName']) ?? fallbackServiceName,
      lastMessage: lastMessageText,
      lastMessageTime: lastMessageTime,
      createdAt: createdAt,
      unreadCount: _asInt(map['unreadCount']) ?? 0,
      isOnline: true,
    );
  }

  ChatMessageModel _mapMessage(
    Map<String, dynamic> map, {
    String? currentUserId,
    required String fallbackConversationId,
    bool forceIsMe = false,
  }) {
    final senderId = _asString(map['senderId']) ?? '';
    final isMe =
        forceIsMe ||
        (currentUserId != null &&
            currentUserId.isNotEmpty &&
            senderId.toLowerCase() == currentUserId.toLowerCase());
    return ChatMessageModel(
      id: _asString(map['id']) ?? '',
      conversationId:
          _asString(map['conversationId']) ?? fallbackConversationId,
      senderId: senderId,
      isMe: isMe,
      content: _asString(map['content']) ?? '',
      attachmentUrl: _asString(map['attachmentUrl']),
      isRead: map['isRead'] == true,
      createdAt: _parseDateTime(map['createdAt']) ?? DateTime.now(),
      sequence: _asInt(map['sequence']),
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
