import 'dart:convert';

// AI Vendor Copilot Models for DANASEA
// Connected to Spring Boot Vendor AI endpoints (/api/vendor/ai/...)

class CopilotMessageModel {
  final String id;
  final String? conversationId;
  final String role; // 'user' or 'assistant'
  final String content;
  final DateTime createdAt;
  final bool isVerified;
  final List<String> details;
  final List<Map<String, dynamic>> artifacts;

  const CopilotMessageModel({
    required this.id,
    this.conversationId,
    required this.role,
    required this.content,
    required this.createdAt,
    this.isVerified = false,
    this.details = const [],
    this.artifacts = const [],
  });

  bool get isUser => role.toLowerCase() == 'user';

  factory CopilotMessageModel.fromJson(Map<String, dynamic> json) {
    List<Map<String, dynamic>> rawArtifacts = [];
    if (json['artifacts'] is List) {
      rawArtifacts = [
        for (final item in (json['artifacts'] as List).whereType<Map>())
          Map<String, dynamic>.from(item),
      ];
    } else if (json['artifactsJson'] is String && (json['artifactsJson'] as String).isNotEmpty) {
      try {
        final decoded = jsonDecode(json['artifactsJson'] as String);
        if (decoded is List) {
          rawArtifacts = [
            for (final item in decoded.whereType<Map>())
              Map<String, dynamic>.from(item),
          ];
        }
      } catch (_) {}
    }

    return CopilotMessageModel(
      id: json['id']?.toString() ?? 'msg-${DateTime.now().millisecondsSinceEpoch}',
      conversationId: json['conversationId']?.toString(),
      role: json['role']?.toString() ?? 'assistant',
      content: json['message']?.toString() ?? json['content']?.toString() ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      isVerified: json['generatedTextVerified'] == true,
      artifacts: rawArtifacts,
      details: [
        for (final artifact in rawArtifacts)
          ..._artifactDetails(artifact),
      ],
    );
  }
  static List<String> _artifactDetails(Map artifact) {
    final data = artifact['data'];
    if (data is! Map) return [];
    final lines = <String>{};
    const labels = {
      'totalReviewsAnalyzed': 'Số đánh giá', 'averageRating': 'Điểm trung bình',
      'totalSlotsEvaluated': 'Số ca được đánh giá', 'weatherRiskSlotsCount': 'Ca có rủi ro thời tiết',
      'gmv': 'Tổng giá trị giao dịch (đ)', 'netPayout': 'Thực thu (đ)',
      'totalBookings': 'Tổng đơn', 'cancelledBookings': 'Đơn đã hủy',
      'total': 'Tổng số dịch vụ', 'listingQualityScore': 'Điểm chất lượng bài đăng',
    };
    for (final entry in labels.entries) {
      if (data[entry.key] != null) lines.add('${entry.value}: ${data[entry.key]}');
    }
    final slotsByAdvice = <String, Set<String>>{};
    final recommendations = data['recommendations'];
    if (recommendations is List) {
      for (final item in recommendations.whereType<Map>()) {
        final advice = ['serviceTitle', 'actionTitle', 'adviceReason']
            .map((field) => item[field]).whereType<String>()
            .map((value) => value.trim()).where((value) => value.isNotEmpty)
            .join(' · ');
        if (advice.isEmpty) continue;
        final times = slotsByAdvice.putIfAbsent(advice, () => <String>{});
        final time = [item['date'], item['startTime']].whereType<String>().join(' ').trim();
        if (time.isNotEmpty) times.add(time);
      }
    }
    for (final entry in slotsByAdvice.entries) {
      final times = entry.value;
      if (times.isEmpty) {
        lines.add(entry.key);
      } else {
        final scope = times.length == 1 ? times.single : '${times.length} khung giờ';
        lines.add('$scope · ${entry.key}');
      }
    }
    for (final key in ['actionItems', 'missingItems']) {
      for (final item in (data[key] as List? ?? []).whereType<Map>()) {
        final text = ['serviceTitle', 'date', 'startTime', 'actionTitle', 'title',
          'recommendation', 'adviceReason', 'suggestion']
            .map((field) => item[field]).whereType<String>()
            .where((value) => value.trim().isNotEmpty).join(' · ');
        if (text.isNotEmpty) lines.add(text);
      }
    }
    final alerts = data['diagnosticAlerts'];
    if (alerts is List) lines.addAll(alerts.whereType<String>());
    return lines.toList();
  }

}

class SlotRecommendationModel {
  final String slotId;
  final String date;
  final String startTime;
  final String marineSafetyLevel;
  final String recommendationType;
  final String actionTitle;
  final String adviceReason;
  final double? bookedRatio;

  const SlotRecommendationModel({
    required this.slotId,
    required this.date,
    required this.startTime,
    required this.marineSafetyLevel,
    required this.recommendationType,
    required this.actionTitle,
    required this.adviceReason,
    required this.bookedRatio,
  });

  factory SlotRecommendationModel.fromJson(Map<String, dynamic> json) {
    return SlotRecommendationModel(
      slotId: json['slotId']?.toString() ?? '',
      date: json['date']?.toString() ?? '',
      startTime: json['startTime']?.toString() ?? '',
      marineSafetyLevel: (json['weatherRiskLevel'] ?? json['marineSafetyLevel'])?.toString() ?? 'UNKNOWN',
      recommendationType: json['recommendationType']?.toString() ?? '',
      actionTitle: json['actionTitle']?.toString() ?? '',
      adviceReason: json['adviceReason']?.toString() ?? '',
      bookedRatio: json['occupancyRate'] is num ? (json['occupancyRate'] as num) / 100 : (json['bookedRatio'] as num?)?.toDouble(),
    );
  }
}

class SlotAdvisorResultModel {
  final int totalSlotsEvaluated;
  final int highDemandSlotsCount;
  final int lowDemandSlotsCount;
  final int weatherRiskSlotsCount;
  final List<SlotRecommendationModel> recommendations;
  final String analysisMethod;

  const SlotAdvisorResultModel({
    required this.totalSlotsEvaluated,
    required this.highDemandSlotsCount,
    required this.lowDemandSlotsCount,
    required this.weatherRiskSlotsCount,
    this.recommendations = const [],
    this.analysisMethod = '',
  });

  factory SlotAdvisorResultModel.fromJson(Map<String, dynamic> json) {
    final recList = (json['recommendations'] as List)
        .whereType<Map<String, dynamic>>()
        .map(SlotRecommendationModel.fromJson)
        .toList();

    return SlotAdvisorResultModel(
      totalSlotsEvaluated: (json['totalSlotsEvaluated'] as num).toInt(),
      highDemandSlotsCount: (json['highDemandSlotsCount'] as num).toInt(),
      lowDemandSlotsCount: (json['lowDemandSlotsCount'] as num).toInt(),
      weatherRiskSlotsCount: (json['weatherRiskSlotsCount'] as num).toInt(),
      recommendations: recList,
      analysisMethod: json['analysisMethod']?.toString() ?? '',
    );
  }
}

class ReviewCoachingResultModel {
  final int totalReviewsAnalyzed;
  final double? averageRating;
  final Map<String, dynamic> pillars;
  final List<Map<String, dynamic>> actionItems;
  final Map<String, dynamic>? badgeProgression;

  const ReviewCoachingResultModel({
    required this.totalReviewsAnalyzed,
    required this.averageRating,
    this.pillars = const {},
    this.actionItems = const [],
    this.badgeProgression,
  });

  factory ReviewCoachingResultModel.fromJson(Map<String, dynamic> json) {
    return ReviewCoachingResultModel(
      totalReviewsAnalyzed: (json['totalReviewsAnalyzed'] as num).toInt(),
      averageRating: (json['averageRating'] as num?)?.toDouble(),
      pillars: json['pillars'] is Map<String, dynamic> ? json['pillars'] as Map<String, dynamic> : {},
      actionItems: (json['actionItems'] as List? ?? [])
          .whereType<Map<String, dynamic>>()
          .toList(),
      badgeProgression: json['badgeProgression'] is Map<String, dynamic>
          ? json['badgeProgression'] as Map<String, dynamic>
          : null,
    );
  }
}

class ReplyDraftResultModel {
  final String reviewId;
  final String draftedReply;
  final String? alternativeShortReply;
  final String tone;
  final bool verifiedSafe;

  const ReplyDraftResultModel({
    required this.reviewId,
    required this.draftedReply,
    this.alternativeShortReply,
    required this.tone,
    required this.verifiedSafe,
  });

  factory ReplyDraftResultModel.fromJson(Map<String, dynamic> json) {
    final reply = json['draftedReply'];
    if (reply is! String || reply.trim().isEmpty) {
      throw const FormatException('API chưa trả về nội dung bản nháp.');
    }
    return ReplyDraftResultModel(
      reviewId: json['reviewId']?.toString() ?? '',
      draftedReply: reply,
      alternativeShortReply: json['alternativeShortReply']?.toString(),
      tone: json['tone']?.toString() ?? '',
      verifiedSafe: json['verifiedSafe'] as bool,
    );
  }
}

class MissingChecklistItemModel {
  final String itemCode;
  final String description;
  final String suggestion;
  final bool critical;

  const MissingChecklistItemModel({
    required this.itemCode,
    required this.description,
    required this.suggestion,
    this.critical = false,
  });

  factory MissingChecklistItemModel.fromJson(Map<String, dynamic> json) {
    return MissingChecklistItemModel(
      itemCode: json['itemCode']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      suggestion: json['suggestion']?.toString() ?? '',
      critical: json['critical'] == true,
    );
  }
}

class LocalizedListingDraftModel {
  final String language;
  final String optimizedTitle;
  final String optimizedDescription;
  final List<String> sellingPoints;
  final List<String> travelerPackingList;
  final String safetyAdvisory;

  const LocalizedListingDraftModel({
    required this.language,
    required this.optimizedTitle,
    required this.optimizedDescription,
    this.sellingPoints = const [],
    this.travelerPackingList = const [],
    this.safetyAdvisory = '',
  });

  factory LocalizedListingDraftModel.fromJson(Map<String, dynamic> json) {
    return LocalizedListingDraftModel(
      language: json['language']?.toString() ?? '',
      optimizedTitle: json['optimizedTitle']?.toString() ?? '',
      optimizedDescription: json['optimizedDescription']?.toString() ?? '',
      sellingPoints: (json['sellingPoints'] as List? ?? []).map((e) => e.toString()).toList(),
      travelerPackingList: (json['travelerPackingList'] as List? ?? []).map((e) => e.toString()).toList(),
      safetyAdvisory: json['safetyAdvisory']?.toString() ?? '',
    );
  }
}

class ListingOptimizeResultModel {
  final String detectedCategory;
  final int listingQualityScore;
  final List<MissingChecklistItemModel> missingItems;
  final Map<String, LocalizedListingDraftModel> drafts;
  final bool policyCompliant;

  const ListingOptimizeResultModel({
    this.detectedCategory = '',
    required this.listingQualityScore,
    this.missingItems = const [],
    this.drafts = const {},
    required this.policyCompliant,
  });

  factory ListingOptimizeResultModel.fromJson(Map<String, dynamic> json) {
    final missingList = (json['missingItems'] as List)
        .whereType<Map<String, dynamic>>()
        .map(MissingChecklistItemModel.fromJson)
        .toList();

    final parsedDrafts = <String, LocalizedListingDraftModel>{};
    if (json['drafts'] is Map) {
      (json['drafts'] as Map).forEach((key, value) {
        if (value is Map<String, dynamic>) {
          parsedDrafts[key.toString()] = LocalizedListingDraftModel.fromJson(value);
        }
      });
    }

    return ListingOptimizeResultModel(
      detectedCategory: json['detectedCategory']?.toString() ?? '',
      listingQualityScore: (json['listingQualityScore'] as num).toInt(),
      missingItems: missingList,
      drafts: parsedDrafts,
      policyCompliant: json['policyCompliant'] as bool,
    );
  }
}


