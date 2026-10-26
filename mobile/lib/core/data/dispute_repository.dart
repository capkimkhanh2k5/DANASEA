import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import '../auth/auth_session.dart';
import '../models/danasea_models.dart';

class DisputePage {
  const DisputePage({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<DisputeModel> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
}

class DisputeRepository {
  DisputeRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;

  final AuthSession session;

  static final Uint8List sampleValidPngBytes = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  );

  static String _shortId(String id) =>
      id.length > 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase();

  static DisputeStatus mapDisputeStatus(String? raw) {
    switch (raw) {
      case 'UNDER_REVIEW':
        return DisputeStatus.inReview;
      case 'RESOLVED_REFUND':
      case 'RESOLVED_PARTIAL':
        return DisputeStatus.resolved;
      case 'RESOLVED_REJECTED':
        return DisputeStatus.rejected;
      case 'OPEN':
      default:
        return DisputeStatus.open;
    }
  }

  static String reasonLabelFromBackend(String? raw) {
    switch (raw) {
      case 'SERVICE_NOT_AS_DESCRIBED':
        return 'Chất lượng không đúng cam kết';
      case 'SAFETY_CONCERN':
        return 'Thiếu trang bị an toàn / phao cứu sinh';
      case 'VENDOR_NO_SHOW':
        return 'Tự ý hủy hoặc dời giờ xuất phát quá 60p';
      case 'PAYMENT_ISSUE':
        return 'Vấn đề thanh toán / phụ phí';
      case 'OTHER':
      default:
        return 'Sự cố phát sinh ngoài thỏa thuận';
    }
  }

  static String backendReasonFromCategory(String category) {
    if (category.contains('không đúng cam kết')) {
      return 'SERVICE_NOT_AS_DESCRIBED';
    }
    if (category.contains('an toàn')) {
      return 'SAFETY_CONCERN';
    }
    if (category.contains('Tự ý hủy') || category.contains('dời giờ')) {
      return 'VENDOR_NO_SHOW';
    }
    if (category.contains('thanh toán')) {
      return 'PAYMENT_ISSUE';
    }
    if (category == 'SERVICE_NOT_AS_DESCRIBED' ||
        category == 'VENDOR_NO_SHOW' ||
        category == 'SAFETY_CONCERN' ||
        category == 'PAYMENT_ISSUE' ||
        category == 'OTHER') {
      return category;
    }
    return 'OTHER';
  }

  static DisputeModel disputeFromJson(
    Map<String, dynamic> json, {
    String? fallbackServiceName,
    String? fallbackVendorName,
  }) {
    if (json['id'] is! String ||
        json['subOrderId'] is! String ||
        json['description'] is! String) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final id = json['id'] as String;
    final subOrderId = json['subOrderId'] as String;
    final rawReason = json['reason']?.toString();
    final rawStatus = json['status']?.toString();
    final rawEvidence = json['evidenceUrls'] as List? ?? const [];
    final rawVendorEvidence = json['vendorEvidenceUrls'] as List? ?? const [];

    return DisputeModel(
      id: id,
      orderId: json['orderId']?.toString(),
      subOrderId: subOrderId,
      subOrderCode: _shortId(subOrderId),
      serviceName:
          fallbackServiceName ?? 'Dịch vụ đơn #${_shortId(subOrderId)}',
      vendorName: fallbackVendorName ?? 'Đối tác DANASEA',
      raisedBy: json['customerId']?.toString() ?? '',
      category: reasonLabelFromBackend(rawReason),
      rawReason: rawReason,
      description: json['description'] as String,
      status: mapDisputeStatus(rawStatus),
      rawStatus: rawStatus,
      refundPercentage: (json['refundPercentage'] as num?)?.toDouble(),
      resolutionNote: json['adminNote']?.toString(),
      resolvedAt: DateTime.tryParse(
        json['resolvedAt']?.toString() ?? '',
      )?.toLocal(),
      createdAt:
          DateTime.tryParse(json['createdAt']?.toString() ?? '')?.toLocal() ??
          DateTime.now(),
      attachmentUrls: rawEvidence
          .map((e) => e?.toString() ?? '')
          .where((s) => s.isNotEmpty)
          .toList(),
      vendorResponse: json['vendorResponse']?.toString(),
      vendorEvidenceUrls: rawVendorEvidence
          .map((e) => e?.toString() ?? '')
          .where((s) => s.isNotEmpty)
          .toList(),
      vendorRespondedAt: DateTime.tryParse(
        json['vendorRespondedAt']?.toString() ?? '',
      )?.toLocal(),
    );
  }

  Future<DisputeModel> createDispute({
    required String orderId,
    required String subOrderId,
    required String reason,
    required String description,
    List<String> evidenceUrls = const [],
    String? serviceName,
    String? vendorName,
  }) async {
    if (orderId.trim().isEmpty || subOrderId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã đơn hàng để tạo khiếu nại.');
    }
    final cleanUrls = evidenceUrls
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final json = await session.request(
      '/api/orders/${Uri.encodeComponent(orderId.trim())}/disputes',
      method: 'POST',
      retry: false,
      data: {
        'subOrderId': subOrderId.trim(),
        'reason': backendReasonFromCategory(reason),
        'description': description.trim(),
        'evidenceUrls': cleanUrls,
      },
    );
    return disputeFromJson(
      json,
      fallbackServiceName: serviceName,
      fallbackVendorName: vendorName,
    );
  }

  Future<DisputePage> getCustomerDisputes({
    int page = 0,
    int size = 20,
    Map<String, SubOrderModel>? subOrderMap,
  }) async {
    final query = Uri(
      queryParameters: {
        'page': '$page',
        'size': '$size',
        'sort': 'createdAt,desc',
      },
    ).query;
    final json = await session.request('/api/disputes?$query');
    if (json['content'] is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawList = (json['content'] as List)
        .whereType<Map<String, dynamic>>()
        .toList();
    final items = rawList.map((item) {
      final subId = item['subOrderId']?.toString() ?? '';
      final sub = subOrderMap?[subId];
      return disputeFromJson(
        item,
        fallbackServiceName: sub?.serviceName,
        fallbackVendorName: sub?.vendorName,
      );
    }).toList();
    return DisputePage(
      content: items,
      page:
          (json['number'] as num?)?.toInt() ??
          (json['page'] as num?)?.toInt() ??
          page,
      size: (json['size'] as num?)?.toInt() ?? size,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? items.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
    );
  }

  Future<DisputeModel> getCustomerDisputeDetail(
    String disputeId, {
    String? fallbackServiceName,
    String? fallbackVendorName,
  }) async {
    if (disputeId.trim().isEmpty) {
      throw AuthFailure('Thiếu mã khiếu nại.');
    }
    final json = await session.request(
      '/api/disputes/${Uri.encodeComponent(disputeId.trim())}',
    );
    return disputeFromJson(
      json,
      fallbackServiceName: fallbackServiceName,
      fallbackVendorName: fallbackVendorName,
    );
  }

  Future<String> uploadEvidenceBytes({
    required Uint8List fileBytes,
    required String filename,
    String contentType = 'image/png',
  }) async {
    final json = await session.requestMultipart(
      '/api/disputes/evidence',
      fieldName: 'file',
      fileBytes: fileBytes,
      filename: filename,
      contentType: contentType,
      retry: false,
    );
    final fileUrl = json['fileUrl']?.toString() ?? '';
    if (fileUrl.isEmpty) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return fileUrl;
  }

  Future<String> uploadEvidenceFromPath(String filePath) async {
    final file = File(filePath.trim());
    if (!await file.exists()) {
      throw AuthFailure('Không tìm thấy tệp tại đường dẫn đã chọn.');
    }
    final bytes = await file.readAsBytes();
    final lower = filePath.toLowerCase();
    final contentType = lower.endsWith('.pdf')
        ? 'application/pdf'
        : lower.endsWith('.webp')
        ? 'image/webp'
        : (lower.endsWith('.jpg') || lower.endsWith('.jpeg'))
        ? 'image/jpeg'
        : 'image/png';
    final name = file.uri.pathSegments.isNotEmpty
        ? file.uri.pathSegments.last
        : 'evidence.png';
    return uploadEvidenceBytes(
      fileBytes: bytes,
      filename: name,
      contentType: contentType,
    );
  }
}
