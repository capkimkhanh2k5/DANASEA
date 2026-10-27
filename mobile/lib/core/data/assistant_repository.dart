import 'dart:math';
import '../auth/auth_session.dart';
import '../models/danasea_models.dart';

class AssistantCardModel {
  final String id;
  final String type;
  final String title;
  final String subtitle;
  final String? serviceId;
  final int? price;
  final String? imageUrl;
  final Map<String, dynamic> raw;

  const AssistantCardModel({
    required this.id,
    required this.type,
    required this.title,
    required this.subtitle,
    this.serviceId,
    this.price,
    this.imageUrl,
    this.raw = const {},
  });
}

class AssistantChatReply {
  final String conversationId;
  final String message;
  final String responseStatus;
  final List<AssistantCardModel> cards;
  final List<String> requiredInputs;
  final ServiceModel? recommendedService;

  const AssistantChatReply({
    required this.conversationId,
    required this.message,
    required this.responseStatus,
    this.cards = const [],
    this.requiredInputs = const [],
    this.recommendedService,
  });
}

class AiItineraryItemModel {
  final String serviceId;
  final String? optionId;
  final String? slotId;
  final String serviceName;
  final String? start;
  final String? end;
  final int quantity;
  final int unitPrice;
  final int partyTotal;
  final String? weatherStatus;
  final String? weatherWarning;

  const AiItineraryItemModel({
    required this.serviceId,
    this.optionId,
    this.slotId,
    required this.serviceName,
    this.start,
    this.end,
    this.quantity = 1,
    this.unitPrice = 0,
    this.partyTotal = 0,
    this.weatherStatus,
    this.weatherWarning,
  });
}

class AiSavedItineraryModel {
  final String id;
  final int version;
  final String status;
  final String lifecycle;
  final int totalPrice;
  final List<AiItineraryItemModel> items;
  final DateTime? createdAt;

  const AiSavedItineraryModel({
    required this.id,
    required this.version,
    required this.status,
    required this.lifecycle,
    required this.totalPrice,
    required this.items,
    this.createdAt,
  });
}

class AiPreferenceProfileModel {
  final int version;
  final bool enabled;
  final List<String> interests;
  final List<String> exclusions;

  const AiPreferenceProfileModel({
    required this.version,
    required this.enabled,
    required this.interests,
    required this.exclusions,
  });
}

class AiSupportRequestModel {
  final String id;
  final String orderId;
  final String kind;
  final String message;
  final String status;
  final int version;
  final String? desiredDate;
  final String? responseNote;
  final DateTime? createdAt;

  const AiSupportRequestModel({
    required this.id,
    required this.orderId,
    required this.kind,
    required this.message,
    required this.status,
    required this.version,
    this.desiredDate,
    this.responseNote,
    this.createdAt,
  });
}

class AssistantRepository {
  AssistantRepository._();
  static final AssistantRepository instance = AssistantRepository._();

  final AuthSession _session = AuthSession.instance;

  static String generateIdempotencyKey([String prefix = 'ai']) {
    final rng = Random();
    final randPart = List.generate(
      12,
      (_) => rng.nextInt(36).toRadixString(36),
    ).join();
    return '$prefix-${DateTime.now().millisecondsSinceEpoch}-$randPart';
  }

  /// POST /api/assistant/chat
  Future<AssistantChatReply> chat({
    required String message,
    String? conversationId,
    String? idempotencyKey,
  }) async {
    final trimmed = message.trim();
    if (trimmed.isEmpty) {
      throw AuthFailure('Vui lòng nhập nội dung câu hỏi.');
    }

    final key = idempotencyKey ?? generateIdempotencyKey('chat');
    final body = await _session.requestJson(
      '/api/assistant/chat',
      method: 'POST',
      headers: {'Idempotency-Key': key},
      data: <String, dynamic>{
        'message': trimmed,
        if (conversationId != null && conversationId.trim().isNotEmpty)
          'conversationId': conversationId.trim(),
      },
      retry: false,
      authenticated: _session.profile != null,
    );

    if (body is! Map) {
      throw AuthFailure('Phản hồi từ trợ lý AI không hợp lệ.');
    }

    final map = Map<String, dynamic>.from(body);
    final convId = _asString(map['conversationId']) ?? conversationId ?? '';
    final replyText = _asString(map['message']) ?? '';
    final respStatus = _asString(map['responseStatus']) ?? 'OK';
    final cardsRaw = map['cards'];
    final cards = <AssistantCardModel>[];
    ServiceModel? firstRecommended;

    if (cardsRaw is List) {
      for (final raw in cardsRaw.whereType<Map>()) {
        final cardMap = Map<String, dynamic>.from(raw);
        final card = _mapCard(cardMap);
        cards.add(card);
        if (firstRecommended == null && card.serviceId != null) {
          firstRecommended = _cardToServiceModel(card);
        }
      }
    }

    final reqInputs = <String>[];
    if (map['requiredInputs'] is List) {
      for (final item in map['requiredInputs'] as List) {
        final text = _asString(item);
        if (text != null) reqInputs.add(text);
      }
    }

    return AssistantChatReply(
      conversationId: convId,
      message: replyText,
      responseStatus: respStatus,
      cards: cards,
      requiredInputs: reqInputs,
      recommendedService: firstRecommended,
    );
  }

  /// POST /api/assistant/conversations/{id}/confirm
  Future<Map<String, dynamic>> confirmConversationCard({
    required String conversationId,
    required String cardId,
    String? sessionId,
  }) async {
    final body = await _session.requestJson(
      '/api/assistant/conversations/${Uri.encodeComponent(conversationId.trim())}/confirm',
      method: 'POST',
      data: <String, dynamic>{
        'cardId': cardId.trim(),
        if (sessionId != null && sessionId.trim().isNotEmpty)
          'sessionId': sessionId.trim(),
      },
      retry: false,
      authenticated: true,
    );
    if (body is Map) {
      return Map<String, dynamic>.from(body);
    }
    return const {};
  }

  /// GET /api/assistant/conversations/{id}
  Future<Map<String, dynamic>> getAssistantConversation(
    String conversationId,
  ) async {
    final body = await _session.requestJson(
      '/api/assistant/conversations/${Uri.encodeComponent(conversationId.trim())}',
      method: 'GET',
      authenticated: true,
    );
    if (body is Map) {
      return Map<String, dynamic>.from(body);
    }
    return const {};
  }

  /// GET /api/assistant/conversations/{id}/history
  Future<List<AiMessageModel>> getConversationHistory(
    String conversationId, {
    int limit = 20,
  }) async {
    final safeLimit = limit.clamp(1, 50);
    final body = await _session.requestJson(
      '/api/assistant/conversations/${Uri.encodeComponent(conversationId.trim())}/history?limit=$safeLimit',
      method: 'GET',
      authenticated: true,
    );

    if (body is! List) return const [];
    return body.whereType<Map>().map((raw) {
      final map = Map<String, dynamic>.from(raw);
      final roleRaw = (_asString(map['role']) ?? 'assistant').toLowerCase();
      final role =
          roleRaw == 'user'
              ? AiRole.user
              : roleRaw == 'tool'
              ? AiRole.tool
              : AiRole.assistant;
      return AiMessageModel(
        id:
            _asString(map['id']) ??
            'ai-${DateTime.now().microsecondsSinceEpoch}',
        role: role,
        content: _asString(map['content']) ?? '',
        createdAt: _parseDateTime(map['createdAt']) ?? DateTime.now(),
      );
    }).toList();
  }

  /// POST /api/ai/search | /api/ai/recommendations | /api/ai/nearby
  Future<Map<String, dynamic>> discoverServices({
    required String query,
    String mode = 'SEARCH', // SEARCH, RECOMMENDATION, NEARBY
    int? partySize,
    num? totalBudget,
    bool weatherSafeOnly = false,
    double? latitude,
    double? longitude,
  }) async {
    final endpoint =
        mode == 'RECOMMENDATION'
            ? '/api/ai/recommendations'
            : mode == 'NEARBY'
            ? '/api/ai/nearby'
            : '/api/ai/search';
    final body = await _session.requestJson(
      endpoint,
      method: 'POST',
      data: <String, dynamic>{
        'query': query.trim(),
        if (partySize != null) 'partySize': partySize,
        if (totalBudget != null) 'totalBudget': totalBudget,
        'weatherSafeOnly': weatherSafeOnly,
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
      },
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/services/compare
  Future<Map<String, dynamic>> compareServices({
    required List<String> serviceIds,
    required String query,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/services/compare',
      method: 'POST',
      data: <String, dynamic>{
        'serviceIds': serviceIds,
        'context': {'query': query.trim()},
      },
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/weather
  Future<Map<String, dynamic>> checkAiWeather({
    String? serviceId,
    String? optionId,
    String? slotId,
    required String query,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/weather',
      method: 'POST',
      data: <String, dynamic>{
        if (serviceId != null && serviceId.isNotEmpty) 'serviceId': serviceId,
        if (optionId != null && optionId.isNotEmpty) 'optionId': optionId,
        if (slotId != null && slotId.isNotEmpty) 'slotId': slotId,
        'context': {'query': query.trim()},
      },
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// GET /api/ai/review-summaries/{serviceId}
  Future<Map<String, dynamic>> getReviewSummary(String serviceId) async {
    final body = await _session.requestJson(
      '/api/ai/review-summaries/${Uri.encodeComponent(serviceId.trim())}',
      method: 'GET',
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/support
  Future<Map<String, dynamic>> askAiSupport({
    String? orderId,
    required String message,
    bool includeCancellationPreview = false,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/support',
      method: 'POST',
      data: <String, dynamic>{
        if (orderId != null && orderId.trim().isNotEmpty)
          'orderId': orderId.trim(),
        'message': message.trim(),
        'includeCancellationPreview': includeCancellationPreview,
      },
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/itineraries
  Future<AiSavedItineraryModel> createItinerary({
    required String query,
    int? partySize,
    num? totalBudget,
    bool weatherSafeOnly = true,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ?? generateIdempotencyKey('itin');
    final body = await _session.requestJson(
      '/api/ai/itineraries',
      method: 'POST',
      headers: {'Idempotency-Key': key},
      data: <String, dynamic>{
        'query': query.trim(),
        if (partySize != null) 'partySize': partySize,
        if (totalBudget != null) 'totalBudget': totalBudget,
        'weatherSafeOnly': weatherSafeOnly,
      },
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể tạo lịch trình AI.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// GET /api/ai/itineraries & GET /api/ai/itineraries/page
  Future<List<AiSavedItineraryModel>> listItineraries({
    int page = 0,
    int size = 20,
    bool paged = false,
  }) async {
    final path =
        paged
            ? '/api/ai/itineraries/page?page=$page&size=$size'
            : '/api/ai/itineraries';
    final body = await _session.requestJson(
      path,
      method: 'GET',
      authenticated: true,
    );
    if (body is! List) return const [];
    return body
        .whereType<Map>()
        .map((m) => _mapSavedItinerary(Map<String, dynamic>.from(m)))
        .toList();
  }

  /// GET /api/ai/itineraries/{id}
  Future<AiSavedItineraryModel> getItinerary(String id) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}',
      method: 'GET',
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không tìm thấy lịch trình AI.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/itineraries/{id}/replan
  Future<Map<String, dynamic>> replanItinerary({
    required String id,
    required int expectedVersion,
    String? query,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/replan',
      method: 'POST',
      data: <String, dynamic>{
        'expectedVersion': expectedVersion,
        if (query != null && query.trim().isNotEmpty)
          'context': {'query': query.trim()},
        'trigger': 'CUSTOMER_REQUEST',
      },
      retry: false,
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/itineraries/preview
  Future<Map<String, dynamic>> previewItinerary({required String query}) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/preview',
      method: 'POST',
      data: {'query': query.trim()},
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/itineraries/previews/{previewId}/save
  Future<AiSavedItineraryModel> saveItineraryPreview({
    required String previewId,
    required String alternativeId,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ?? generateIdempotencyKey('save-itin');
    final body = await _session.requestJson(
      '/api/ai/itineraries/previews/${Uri.encodeComponent(previewId.trim())}/save',
      method: 'POST',
      headers: {'Idempotency-Key': key},
      data: {'alternativeId': alternativeId.trim()},
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể lưu bản xem trước lịch trình.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/itineraries/{id}/accept
  Future<AiSavedItineraryModel> acceptItinerary({
    required String id,
    required int expectedVersion,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/accept',
      method: 'POST',
      data: {'expectedVersion': expectedVersion},
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể xác nhận lịch trình.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/itineraries/{id}/archive
  Future<AiSavedItineraryModel> archiveItinerary({
    required String id,
    required int expectedVersion,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/archive',
      method: 'POST',
      data: {'expectedVersion': expectedVersion},
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể lưu trữ lịch trình.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// GET /api/ai/itineraries/{id}/proposals
  Future<List<Map<String, dynamic>>> listItineraryProposals(String id) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/proposals',
      method: 'GET',
      authenticated: true,
    );
    if (body is! List) return const [];
    return body
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
  }

  /// POST /api/ai/itineraries/{id}/proposals/{proposalId}/accept
  Future<AiSavedItineraryModel> acceptItineraryProposal({
    required String id,
    required String proposalId,
    required int expectedVersion,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/proposals/${Uri.encodeComponent(proposalId.trim())}/accept',
      method: 'POST',
      data: {'expectedVersion': expectedVersion},
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể chấp nhận đề xuất điều chỉnh lịch trình.');
    }
    return _mapSavedItinerary(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/itineraries/{id}/proposals/{proposalId}/reject
  Future<Map<String, dynamic>> rejectItineraryProposal({
    required String id,
    required String proposalId,
    required int expectedVersion,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/proposals/${Uri.encodeComponent(proposalId.trim())}/reject',
      method: 'POST',
      data: {'expectedVersion': expectedVersion},
      retry: false,
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// GET /api/ai/itineraries/{id}/revisions
  Future<List<Map<String, dynamic>>> listItineraryRevisions(String id) async {
    final body = await _session.requestJson(
      '/api/ai/itineraries/${Uri.encodeComponent(id.trim())}/revisions',
      method: 'GET',
      authenticated: true,
    );
    if (body is! List) return const [];
    return body
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
  }

  /// GET /api/ai/preferences
  Future<AiPreferenceProfileModel> getPreferences() async {
    final body = await _session.requestJson(
      '/api/ai/preferences',
      method: 'GET',
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể tải sở thích cá nhân AI.');
    }
    return _mapPreferences(Map<String, dynamic>.from(body));
  }

  /// PUT /api/ai/preferences
  Future<AiPreferenceProfileModel> updatePreferences({
    required int expectedVersion,
    required bool enabled,
    required List<String> interests,
    required List<String> exclusions,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/preferences',
      method: 'PUT',
      data: <String, dynamic>{
        'expectedVersion': expectedVersion,
        'enabled': enabled,
        'interests': interests,
        'exclusions': exclusions,
      },
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể lưu sở thích cá nhân AI.');
    }
    return _mapPreferences(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/recommendations/{id}/feedback
  Future<Map<String, dynamic>> submitRecommendationFeedback({
    required String recommendationId,
    required String serviceId,
    required String signal,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ?? generateIdempotencyKey('fb');
    final body = await _session.requestJson(
      '/api/ai/recommendations/${Uri.encodeComponent(recommendationId.trim())}/feedback',
      method: 'POST',
      headers: {'Idempotency-Key': key},
      data: <String, dynamic>{
        'serviceId': serviceId.trim(),
        'signal': signal.trim(),
      },
      retry: false,
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/support/requests/preview
  Future<Map<String, dynamic>> previewSupportRequest({
    required String orderId,
    required String kind, // HANDOFF, CHANGE_REQUEST
    required String message,
    String? desiredDate,
    String? desiredSlotId,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/support/requests/preview',
      method: 'POST',
      data: <String, dynamic>{
        'orderId': orderId.trim(),
        'kind': kind.trim(),
        'message': message.trim(),
        if (desiredDate != null && desiredDate.trim().isNotEmpty)
          'desiredDate': desiredDate.trim(),
        if (desiredSlotId != null && desiredSlotId.trim().isNotEmpty)
          'desiredSlotId': desiredSlotId.trim(),
      },
      authenticated: true,
    );
    if (body is Map) return Map<String, dynamic>.from(body);
    return const {};
  }

  /// POST /api/ai/support/requests
  Future<AiSupportRequestModel> createSupportRequest({
    required String orderId,
    required String kind,
    required String message,
    String? desiredDate,
    String? desiredSlotId,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ?? generateIdempotencyKey('sup');
    final body = await _session.requestJson(
      '/api/ai/support/requests',
      method: 'POST',
      headers: {'Idempotency-Key': key},
      data: <String, dynamic>{
        'orderId': orderId.trim(),
        'kind': kind.trim(),
        'message': message.trim(),
        if (desiredDate != null && desiredDate.trim().isNotEmpty)
          'desiredDate': desiredDate.trim(),
        if (desiredSlotId != null && desiredSlotId.trim().isNotEmpty)
          'desiredSlotId': desiredSlotId.trim(),
        'confirmed': true,
      },
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể gửi yêu cầu hỗ trợ AI.');
    }
    return _mapSupportRequest(Map<String, dynamic>.from(body));
  }

  /// GET /api/ai/support/requests
  Future<List<AiSupportRequestModel>> listSupportRequests() async {
    final body = await _session.requestJson(
      '/api/ai/support/requests',
      method: 'GET',
      authenticated: true,
    );
    if (body is! List) return const [];
    return body
        .whereType<Map>()
        .map((e) => _mapSupportRequest(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// GET /api/ai/support/requests/{id}
  Future<AiSupportRequestModel> getSupportRequest(String id) async {
    final body = await _session.requestJson(
      '/api/ai/support/requests/${Uri.encodeComponent(id.trim())}',
      method: 'GET',
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không tìm thấy yêu cầu hỗ trợ.');
    }
    return _mapSupportRequest(Map<String, dynamic>.from(body));
  }

  /// POST /api/ai/support/requests/{id}/cancel
  Future<AiSupportRequestModel> cancelSupportRequest({
    required String id,
    required int expectedVersion,
  }) async {
    final body = await _session.requestJson(
      '/api/ai/support/requests/${Uri.encodeComponent(id.trim())}/cancel',
      method: 'POST',
      data: {'expectedVersion': expectedVersion},
      retry: false,
      authenticated: true,
    );
    if (body is! Map) {
      throw AuthFailure('Không thể hủy yêu cầu hỗ trợ.');
    }
    return _mapSupportRequest(Map<String, dynamic>.from(body));
  }

  AssistantCardModel _mapCard(Map<String, dynamic> map) {
    final id = _asString(map['id']) ?? _asString(map['cardId']) ?? '';
    final type = _asString(map['type']) ?? 'SERVICE';
    final title =
        _asString(map['title']) ??
        _asString(map['serviceName']) ??
        'Gợi ý trải nghiệm DanaSea';
    final subtitle =
        _asString(map['subtitle']) ??
        _asString(map['summary']) ??
        _asString(map['vendorName']) ??
        'Đà Nẵng';
    final serviceId =
        _asString(map['serviceId']) ??
        (map['data'] is Map
            ? _asString((map['data'] as Map)['serviceId'])
            : null);
    final price =
        _asInt(map['price']) ??
        (map['data'] is Map ? _asInt((map['data'] as Map)['price']) : null);
    final imageUrl =
        _asString(map['imageUrl']) ??
        (map['data'] is Map
            ? _asString((map['data'] as Map)['imageUrl'])
            : null);

    return AssistantCardModel(
      id: id,
      type: type,
      title: title,
      subtitle: subtitle,
      serviceId: serviceId,
      price: price,
      imageUrl: imageUrl,
      raw: map,
    );
  }

  ServiceModel _cardToServiceModel(AssistantCardModel card) {
    return ServiceModel(
      id: card.serviceId ?? card.id,
      vendorId: '',
      vendorName: card.subtitle,
      categoryId: '',
      categoryName: 'Thể thao biển',
      name: card.title,
      slug: card.serviceId ?? card.id,
      description: card.subtitle,
      price: card.price ?? 350000,
      durationMinutes: 120,
      capacityPerSlot: 10,
      locationName: 'Bãi biển Đà Nẵng',
      address: 'Đà Nẵng',
      latitude: 16.0598,
      longitude: 108.2469,
      waiverContent: 'Cam kết an toàn thể thao biển Đà Nẵng.',
      imageUrls: card.imageUrl != null ? [card.imageUrl!] : const [],
    );
  }

  AiSavedItineraryModel _mapSavedItinerary(Map<String, dynamic> map) {
    final planMap =
        map['plan'] is Map
            ? Map<String, dynamic>.from(map['plan'] as Map)
            : map;
    final rawItems = planMap['items'];
    final items = <AiItineraryItemModel>[];
    if (rawItems is List) {
      for (final raw in rawItems.whereType<Map>()) {
        final item = Map<String, dynamic>.from(raw);
        items.add(
          AiItineraryItemModel(
            serviceId: _asString(item['serviceId']) ?? '',
            optionId: _asString(item['optionId']),
            slotId: _asString(item['slotId']),
            serviceName: _asString(item['serviceName']) ?? 'Hoạt động biển',
            start: _asString(item['start']),
            end: _asString(item['end']),
            quantity: _asInt(item['quantity']) ?? 1,
            unitPrice: _asInt(item['unitPrice']) ?? 0,
            partyTotal: _asInt(item['partyTotal']) ?? 0,
            weatherStatus: _asString(item['weatherStatus']),
            weatherWarning: _asString(item['weatherWarning']),
          ),
        );
      }
    }

    return AiSavedItineraryModel(
      id: _asString(map['id']) ?? '',
      version: _asInt(map['version']) ?? 0,
      status: _asString(planMap['status']) ?? 'READY',
      lifecycle: _asString(map['lifecycle']) ?? 'DRAFT',
      totalPrice: _asInt(planMap['totalPrice']) ?? 0,
      items: items,
      createdAt: _parseDateTime(map['createdAt']),
    );
  }

  AiPreferenceProfileModel _mapPreferences(Map<String, dynamic> map) {
    final interests = <String>[];
    if (map['interests'] is List) {
      for (final i in map['interests'] as List) {
        final text = _asString(i);
        if (text != null) interests.add(text);
      }
    }
    final exclusions = <String>[];
    if (map['exclusions'] is List) {
      for (final e in map['exclusions'] as List) {
        final text = _asString(e);
        if (text != null) exclusions.add(text);
      }
    }
    return AiPreferenceProfileModel(
      version: _asInt(map['version']) ?? 0,
      enabled: map['enabled'] != false,
      interests: interests,
      exclusions: exclusions,
    );
  }

  AiSupportRequestModel _mapSupportRequest(Map<String, dynamic> map) {
    return AiSupportRequestModel(
      id: _asString(map['id']) ?? '',
      orderId: _asString(map['orderId']) ?? '',
      kind: _asString(map['kind']) ?? 'HANDOFF',
      message: _asString(map['message']) ?? '',
      status: _asString(map['status']) ?? 'WAITING_REVIEW',
      version: _asInt(map['version']) ?? 0,
      desiredDate: _asString(map['desiredDate']),
      responseNote: _asString(map['responseNote']),
      createdAt: _parseDateTime(map['createdAt']),
    );
  }

  String? _asString(Object? value) {
    if (value == null) return null;
    final text = value.toString().trim();
    return text.isEmpty ? null : text;
  }

  int? _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.round();
    if (value is String) return num.tryParse(value.trim())?.round();
    return null;
  }

  DateTime? _parseDateTime(Object? value) {
    final text = _asString(value);
    if (text == null) return null;
    return DateTime.tryParse(text)?.toLocal();
  }
}
