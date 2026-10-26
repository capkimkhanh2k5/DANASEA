import '../auth/auth_session.dart';
import '../models/danasea_models.dart';

class ReviewPage {
  const ReviewPage({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<ReviewModel> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
}

class FlagReviewResult {
  const FlagReviewResult({
    required this.id,
    required this.isFlagged,
  });

  final String id;
  final bool isFlagged;

  factory FlagReviewResult.fromJson(Map<String, dynamic> json) {
    if (json['id'] is! String || json['isFlagged'] is! bool) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return FlagReviewResult(
      id: json['id'] as String,
      isFlagged: json['isFlagged'] as bool,
    );
  }
}

class ReviewRepository {
  ReviewRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;

  final AuthSession session;

  static ReviewModel reviewFromJson(Map<String, dynamic> json) {
    if (json['id'] is! String ||
        json['subOrderId'] is! String ||
        json['rating'] is! num ||
        json['comment'] is! String) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawImages = json['images'] as List? ?? const [];
    final customerName = (json['customerName']?.toString() ?? '').trim();
    return ReviewModel(
      id: json['id'] as String,
      subOrderId: json['subOrderId'] as String,
      customerId: json['customerId']?.toString() ?? '',
      customerName: customerName.isNotEmpty
          ? customerName
          : 'Khách hàng DANASEA',
      customerAvatar: json['customerAvatar']?.toString(),
      vendorId: json['vendorId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      rating: (json['rating'] as num).toInt().clamp(1, 5),
      comment: json['comment'] as String,
      images: rawImages
          .map((e) => e?.toString() ?? '')
          .where((s) => s.isNotEmpty)
          .toList(),
      vendorReply: json['vendorReply']?.toString(),
      vendorRepliedAt: DateTime.tryParse(
        json['vendorRepliedAt']?.toString() ?? '',
      )?.toLocal(),
      isFlagged: json['isFlagged'] == true,
      isVisible: json['isVisible'] != false,
      flagReason: json['flagReason']?.toString(),
      moderationNote: json['moderationNote']?.toString(),
      createdAt:
          DateTime.tryParse(json['createdAt']?.toString() ?? '')?.toLocal() ??
          DateTime.now(),
      updatedAt: DateTime.tryParse(
        json['updatedAt']?.toString() ?? '',
      )?.toLocal(),
    );
  }

  Future<ReviewPage> getServiceReviews(
    String serviceId, {
    int page = 0,
    int size = 10,
  }) async {
    if (serviceId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã dịch vụ để tải đánh giá.');
    }
    final query = Uri(
      queryParameters: {
        'page': '$page',
        'size': '$size',
        'sort': 'createdAt,desc',
      },
    ).query;
    final json = await session.request(
      '/api/services/${Uri.encodeComponent(serviceId.trim())}/reviews?$query',
      authenticated: false,
    );
    if (json['content'] is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawList = (json['content'] as List)
        .whereType<Map<String, dynamic>>()
        .toList();
    final items = rawList.map(reviewFromJson).toList();
    final pageNum =
        (json['number'] as num?)?.toInt() ??
        (json['page'] as num?)?.toInt() ??
        page;
    final pageSize = (json['size'] as num?)?.toInt() ?? size;
    final totalElements =
        (json['totalElements'] as num?)?.toInt() ?? items.length;
    final totalPages = (json['totalPages'] as num?)?.toInt() ?? 1;
    return ReviewPage(
      content: items,
      page: pageNum,
      size: pageSize,
      totalElements: totalElements,
      totalPages: totalPages,
    );
  }

  Future<ReviewModel> createReview({
    required String subOrderId,
    required int rating,
    required String comment,
    List<String> images = const [],
  }) async {
    if (subOrderId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã đơn hàng con để gửi đánh giá.');
    }
    final cleanImages = images
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final json = await session.request(
      '/api/sub-orders/${Uri.encodeComponent(subOrderId.trim())}/reviews',
      method: 'POST',
      retry: false,
      data: {
        'rating': rating.clamp(1, 5),
        'comment': comment.trim(),
        if (cleanImages.isNotEmpty) 'images': cleanImages,
      },
    );
    return reviewFromJson(json);
  }

  Future<ReviewModel> updateReviewBySubOrder({
    required String subOrderId,
    required int rating,
    required String comment,
    List<String> images = const [],
  }) async {
    if (subOrderId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã đơn hàng con để cập nhật đánh giá.');
    }
    final cleanImages = images
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final json = await session.request(
      '/api/sub-orders/${Uri.encodeComponent(subOrderId.trim())}/reviews',
      method: 'PUT',
      retry: false,
      data: {
        'rating': rating.clamp(1, 5),
        'comment': comment.trim(),
        'images': cleanImages,
      },
    );
    return reviewFromJson(json);
  }

  Future<ReviewModel> updateReview({
    required String reviewId,
    required int rating,
    required String comment,
    List<String> images = const [],
  }) async {
    if (reviewId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã đánh giá để cập nhật.');
    }
    final cleanImages = images
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final json = await session.request(
      '/api/reviews/${Uri.encodeComponent(reviewId.trim())}',
      method: 'PUT',
      retry: false,
      data: {
        'rating': rating.clamp(1, 5),
        'comment': comment.trim(),
        'images': cleanImages,
      },
    );
    return reviewFromJson(json);
  }

  Future<FlagReviewResult> flagReview({
    required String reviewId,
    required String reason,
  }) async {
    if (reviewId.trim().isEmpty || reason.trim().isEmpty) {
      throw AuthFailure('Vui lòng nhập lý do báo cáo đánh giá.');
    }
    final json = await session.request(
      '/api/reviews/${Uri.encodeComponent(reviewId.trim())}/flag',
      method: 'POST',
      retry: false,
      data: {'reason': reason.trim()},
    );
    return FlagReviewResult.fromJson(json);
  }
}
