// AI Vendor Copilot Models for DANASEA
// Connected to Spring Boot Vendor AI endpoints (/api/vendor/ai/...)

class CopilotMessageModel {
  final String id;
  final String? conversationId;
  final String role; // 'user' or 'assistant'
  final String content;
  final DateTime createdAt;
  final bool isVerified;

  const CopilotMessageModel({
    required this.id,
    this.conversationId,
    required this.role,
    required this.content,
    required this.createdAt,
    this.isVerified = true,
  });

  bool get isUser => role.toLowerCase() == 'user';

  factory CopilotMessageModel.fromJson(Map<String, dynamic> json) {
    return CopilotMessageModel(
      id: json['id']?.toString() ?? 'msg-${DateTime.now().millisecondsSinceEpoch}',
      conversationId: json['conversationId']?.toString(),
      role: json['role']?.toString() ?? 'assistant',
      content: json['message']?.toString() ?? json['content']?.toString() ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      isVerified: json['generatedTextVerified'] == true,
    );
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
  final double bookedRatio;

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
      marineSafetyLevel: json['marineSafetyLevel']?.toString() ?? 'SAFE',
      recommendationType: json['recommendationType']?.toString() ?? 'EXPAND_CAPACITY',
      actionTitle: json['actionTitle']?.toString() ?? 'Đề xuất ca chạy',
      adviceReason: json['adviceReason']?.toString() ?? '',
      bookedRatio: (json['bookedRatio'] as num?)?.toDouble() ?? 0.0,
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
    this.totalSlotsEvaluated = 0,
    this.highDemandSlotsCount = 0,
    this.lowDemandSlotsCount = 0,
    this.weatherRiskSlotsCount = 0,
    this.recommendations = const [],
    this.analysisMethod = '',
  });

  factory SlotAdvisorResultModel.fromJson(Map<String, dynamic> json) {
    final recList = (json['recommendations'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(SlotRecommendationModel.fromJson)
        .toList();

    return SlotAdvisorResultModel(
      totalSlotsEvaluated: (json['totalSlotsEvaluated'] as num?)?.toInt() ?? 0,
      highDemandSlotsCount: (json['highDemandSlotsCount'] as num?)?.toInt() ?? 0,
      lowDemandSlotsCount: (json['lowDemandSlotsCount'] as num?)?.toInt() ?? 0,
      weatherRiskSlotsCount: (json['weatherRiskSlotsCount'] as num?)?.toInt() ?? 0,
      recommendations: recList,
      analysisMethod: json['analysisMethod']?.toString() ?? '',
    );
  }
}

class ReviewCoachingResultModel {
  final int totalReviewsAnalyzed;
  final double averageRating;
  final Map<String, dynamic> pillars;
  final List<Map<String, dynamic>> actionItems;
  final Map<String, dynamic>? badgeProgression;

  const ReviewCoachingResultModel({
    this.totalReviewsAnalyzed = 0,
    this.averageRating = 5.0,
    this.pillars = const {},
    this.actionItems = const [],
    this.badgeProgression,
  });

  factory ReviewCoachingResultModel.fromJson(Map<String, dynamic> json) {
    return ReviewCoachingResultModel(
      totalReviewsAnalyzed: (json['totalReviewsAnalyzed'] as num?)?.toInt() ?? 0,
      averageRating: (json['averageRating'] as num?)?.toDouble() ?? 5.0,
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
    this.tone = 'PROFESSIONAL',
    this.verifiedSafe = true,
  });

  factory ReplyDraftResultModel.fromJson(Map<String, dynamic> json) {
    return ReplyDraftResultModel(
      reviewId: json['reviewId']?.toString() ?? '',
      draftedReply: json['draftedReply']?.toString() ?? '',
      alternativeShortReply: json['alternativeShortReply']?.toString(),
      tone: json['tone']?.toString() ?? 'PROFESSIONAL',
      verifiedSafe: json['verifiedSafe'] != false,
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
      language: json['language']?.toString() ?? 'vi',
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
    this.listingQualityScore = 0,
    this.missingItems = const [],
    this.drafts = const {},
    this.policyCompliant = true,
  });

  factory ListingOptimizeResultModel.fromJson(Map<String, dynamic> json) {
    final missingList = (json['missingItems'] as List? ?? [])
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
      listingQualityScore: (json['listingQualityScore'] as num?)?.toInt() ?? 0,
      missingItems: missingList,
      drafts: parsedDrafts,
      policyCompliant: json['policyCompliant'] != false,
    );
  }
}


