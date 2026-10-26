import 'dart:math';
import 'dart:typed_data';
import '../auth/auth_session.dart';
import '../models/danasea_models.dart';
import 'catalog_repository.dart';

class OrderPage {
  const OrderPage({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<MasterOrderModel> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
}

class OrderPaymentRecord {
  const OrderPaymentRecord({
    required this.id,
    required this.masterOrderId,
    required this.provider,
    this.providerTransactionId,
    required this.amount,
    required this.status,
    this.idempotencyKey,
    this.webhookEventId,
    this.paymentUrl,
    this.qrCodeUrl,
    this.expiresAt,
    this.createdAt,
    this.updatedAt,
    this.providerOrderId,
    this.lastError,
    this.reconciliationAttempts,
    this.reconciliationNextAttemptAt,
    this.lastReconciledAt,
  });

  final String id;
  final String masterOrderId;
  final String provider;
  final String? providerTransactionId;
  final num amount;
  final String status;
  final String? idempotencyKey;
  final String? webhookEventId;
  final String? paymentUrl;
  final String? qrCodeUrl;
  final DateTime? expiresAt;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final String? providerOrderId;
  final String? lastError;
  final int? reconciliationAttempts;
  final DateTime? reconciliationNextAttemptAt;
  final DateTime? lastReconciledAt;

  factory OrderPaymentRecord.fromJson(Map<String, dynamic> json) {
    if (json['id'] is! String ||
        json['masterOrderId'] is! String ||
        json['provider'] is! String ||
        json['amount'] is! num ||
        json['status'] is! String) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return OrderPaymentRecord(
      id: json['id'] as String,
      masterOrderId: json['masterOrderId'] as String,
      provider: json['provider'] as String,
      providerTransactionId: json['providerTransactionId']?.toString(),
      amount: json['amount'] as num,
      status: json['status'] as String,
      idempotencyKey: json['idempotencyKey']?.toString(),
      webhookEventId: json['webhookEventId']?.toString(),
      paymentUrl: json['paymentUrl']?.toString(),
      qrCodeUrl: json['qrCodeUrl']?.toString(),
      expiresAt: DateTime.tryParse(json['expiresAt']?.toString() ?? ''),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? ''),
      providerOrderId: json['providerOrderId']?.toString(),
      lastError: json['lastError']?.toString(),
      reconciliationAttempts: (json['reconciliationAttempts'] as num?)?.toInt(),
      reconciliationNextAttemptAt: DateTime.tryParse(
        json['reconciliationNextAttemptAt']?.toString() ?? '',
      ),
      lastReconciledAt: DateTime.tryParse(
        json['lastReconciledAt']?.toString() ?? '',
      ),
    );
  }
}

class SubOrderCancellationPreviewItem {
  const SubOrderCancellationPreviewItem({
    required this.subOrderId,
    required this.serviceId,
    required this.slotId,
    this.departureTime,
    required this.originalAmount,
    required this.refundPercentage,
    required this.refundAmount,
    required this.policyApplied,
  });

  final String subOrderId;
  final String serviceId;
  final String slotId;
  final DateTime? departureTime;
  final num originalAmount;
  final num refundPercentage;
  final num refundAmount;
  final String policyApplied;

  factory SubOrderCancellationPreviewItem.fromJson(Map<String, dynamic> json) {
    return SubOrderCancellationPreviewItem(
      subOrderId: json['subOrderId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      slotId: json['slotId']?.toString() ?? '',
      departureTime: DateTime.tryParse(json['departureTime']?.toString() ?? ''),
      originalAmount: (json['originalAmount'] as num?) ?? 0,
      refundPercentage: (json['refundPercentage'] as num?) ?? 0,
      refundAmount: (json['refundAmount'] as num?) ?? 0,
      policyApplied: json['policyApplied']?.toString() ?? '',
    );
  }
}

class OrderCancellationPreview {
  const OrderCancellationPreview({
    required this.orderId,
    required this.originalAmount,
    required this.refundPercentage,
    required this.refundAmount,
    required this.policyApplied,
    required this.items,
  });

  final String orderId;
  final num originalAmount;
  final num refundPercentage;
  final num refundAmount;
  final String policyApplied;
  final List<SubOrderCancellationPreviewItem> items;

  factory OrderCancellationPreview.fromJson(Map<String, dynamic> json) {
    if (json['orderId'] is! String ||
        json['originalAmount'] is! num ||
        json['refundPercentage'] is! num ||
        json['refundAmount'] is! num) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawItems = json['items'] as List? ?? const [];
    return OrderCancellationPreview(
      orderId: json['orderId'] as String,
      originalAmount: json['originalAmount'] as num,
      refundPercentage: json['refundPercentage'] as num,
      refundAmount: json['refundAmount'] as num,
      policyApplied: json['policyApplied']?.toString() ?? '',
      items: rawItems
          .whereType<Map<String, dynamic>>()
          .map(SubOrderCancellationPreviewItem.fromJson)
          .toList(),
    );
  }
}

class OrderReceiptItem {
  const OrderReceiptItem({
    required this.subOrderId,
    required this.serviceId,
    required this.serviceName,
    required this.quantity,
    required this.unitPrice,
    required this.subtotal,
    required this.discountAmount,
    required this.finalAmount,
  });

  final String subOrderId;
  final String serviceId;
  final String serviceName;
  final int quantity;
  final num unitPrice;
  final num subtotal;
  final num discountAmount;
  final num finalAmount;

  factory OrderReceiptItem.fromJson(Map<String, dynamic> json) {
    return OrderReceiptItem(
      subOrderId: json['subOrderId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      serviceName: json['serviceName']?.toString() ?? 'DANASEA service',
      quantity: (json['quantity'] as num?)?.toInt() ?? 1,
      unitPrice: (json['unitPrice'] as num?) ?? 0,
      subtotal: (json['subtotal'] as num?) ?? 0,
      discountAmount: (json['discountAmount'] as num?) ?? 0,
      finalAmount: (json['finalAmount'] as num?) ?? 0,
    );
  }
}

class OrderReceipt {
  const OrderReceipt({
    required this.orderId,
    required this.orderCode,
    required this.receiptCode,
    required this.customerId,
    required this.totalAmount,
    required this.discountAmount,
    required this.status,
    this.paidAt,
    this.paymentProvider,
    required this.items,
  });

  final String orderId;
  final String orderCode;
  final String receiptCode;
  final String customerId;
  final num totalAmount;
  final num discountAmount;
  final String status;
  final DateTime? paidAt;
  final String? paymentProvider;
  final List<OrderReceiptItem> items;

  factory OrderReceipt.fromJson(Map<String, dynamic> json) {
    if (json['orderId'] is! String ||
        json['orderCode'] is! String ||
        json['receiptCode'] is! String ||
        json['totalAmount'] is! num) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawItems = json['items'] as List? ?? const [];
    return OrderReceipt(
      orderId: json['orderId'] as String,
      orderCode: json['orderCode'] as String,
      receiptCode: json['receiptCode'] as String,
      customerId: json['customerId']?.toString() ?? '',
      totalAmount: json['totalAmount'] as num,
      discountAmount: (json['discountAmount'] as num?) ?? 0,
      status: json['status']?.toString() ?? '',
      paidAt: DateTime.tryParse(json['paidAt']?.toString() ?? ''),
      paymentProvider: json['paymentProvider']?.toString(),
      items: rawItems
          .whereType<Map<String, dynamic>>()
          .map(OrderReceiptItem.fromJson)
          .toList(),
    );
  }
}

class OrderRepository {
  OrderRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;

  final AuthSession session;
  static final Map<String, ServiceModel> _serviceCache = {};

  static String generateIdempotencyKey([String prefix = 'ord']) {
    final random = Random();
    final suffix = List.generate(
      12,
      (_) => random.nextInt(36).toRadixString(36),
    ).join();
    return '$prefix-${DateTime.now().millisecondsSinceEpoch}-$suffix';
  }

  static String _shortId(String id) =>
      id.length > 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase();

  Future<ServiceModel?> _lookupService(String serviceId) async {
    if (serviceId.isEmpty) return null;
    final cached = _serviceCache[serviceId];
    if (cached != null) return cached;
    try {
      final json = await session.request(
        '/api/services/${Uri.encodeComponent(serviceId)}',
        authenticated: false,
      );
      final model = CatalogRepository.serviceFromJson(json);
      _serviceCache[serviceId] = model;
      return model;
    } catch (_) {
      return null;
    }
  }

  Future<MasterOrderModel> _parseOrder(Map<String, dynamic> json) async {
    if (json['id'] is! String ||
        json['customerId'] is! String ||
        json['status'] is! String ||
        json['totalAmount'] is! num) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final orderId = json['id'] as String;
    final createdAt =
        DateTime.tryParse(json['createdAt']?.toString() ?? '')?.toLocal() ??
        DateTime.now();
    final updatedAt = DateTime.tryParse(
      json['updatedAt']?.toString() ?? '',
    )?.toLocal();
    final rawItems = (json['items'] as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .toList();

    final serviceIds = rawItems
        .map((e) => e['serviceId']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toSet();
    await Future.wait(serviceIds.map(_lookupService));

    final subOrders = rawItems.map((item) {
      final subId = item['id']?.toString() ?? '';
      final vendorId = item['vendorId']?.toString() ?? '';
      final serviceId = item['serviceId']?.toString() ?? '';
      final slotId = item['slotId']?.toString() ?? '';
      final service = _serviceCache[serviceId];

      CatalogSlot? matchedSlot;
      if (service != null) {
        for (final slot in service.catalogSlots) {
          if (slot.id == slotId) {
            matchedSlot = slot;
            break;
          }
        }
      }

      final slotDate = matchedSlot != null
          ? (DateTime.tryParse(matchedSlot.date) ?? createdAt)
          : createdAt;
      final slotTime =
          matchedSlot != null &&
              matchedSlot.startTime.isNotEmpty &&
              matchedSlot.endTime.isNotEmpty
          ? '${matchedSlot.startTime} - ${matchedSlot.endTime}'
          : 'Slot #${_shortId(slotId)}';

      final rawStatus = item['status']?.toString() ?? 'PENDING';
      final unitPrice = ((item['unitPrice'] as num?) ?? 0).round();
      final subtotal = ((item['subtotalAmount'] as num?) ?? 0).round();
      final discount = ((item['discountAmount'] as num?) ?? 0).round();
      final finalAmt = ((item['finalAmount'] as num?) ?? subtotal).round();

      return SubOrderModel(
        id: subId,
        masterOrderId: orderId,
        vendorId: vendorId,
        vendorName: (service != null && service.vendorName.isNotEmpty)
            ? service.vendorName
            : 'Đối tác #${_shortId(vendorId)}',
        serviceId: serviceId,
        serviceName: (service != null && service.name.isNotEmpty)
            ? service.name
            : 'Dịch vụ #${_shortId(serviceId)}',
        serviceImageUrl: (service != null && service.imageUrls.isNotEmpty)
            ? service.imageUrls.first
            : '',
        locationName: (service != null && service.locationName.isNotEmpty)
            ? service.locationName
            : 'Đà Nẵng',
        slotId: slotId,
        slotDate: slotDate,
        slotTime: slotTime,
        quantity: (item['quantity'] as num?)?.toInt() ?? 1,
        unitPrice: unitPrice,
        subtotalAmount: subtotal,
        discountAmount: discount,
        finalAmount: finalAmt,
        status: _mapSubOrderStatus(rawStatus),
        rawStatus: rawStatus,
        waiverRequired: item['waiverRequired'] == true,
        waiverVersion: (item['waiverVersion'] as num?)?.toInt(),
        waiverContent: item['waiverContent']?.toString(),
        waiverLanguage: item['waiverLanguage']?.toString(),
        waiverFallbackUsed: item['waiverFallbackUsed'] == true,
        waiverAccepted: item['waiverAccepted'] == true,
        waiverAcceptedAt: DateTime.tryParse(
          item['waiverAcceptedAt']?.toString() ?? '',
        )?.toLocal(),
        waiverAcceptedBy: item['waiverAcceptedBy']?.toString(),
        waiverAcceptedLanguage: item['waiverAcceptedLanguage']?.toString(),
        cancellationPolicyVersion: item['cancellationPolicyVersion']
            ?.toString(),
        qrSecret: subId,
      );
    }).toList();

    return MasterOrderModel(
      id: orderId,
      bookingId: json['bookingId']?.toString(),
      customerId: json['customerId'] as String,
      status: _mapMasterOrderStatus(json['status'] as String),
      totalAmount: (json['totalAmount'] as num).round(),
      discountAmount: ((json['discountAmount'] as num?) ?? 0).round(),
      createdAt: createdAt,
      updatedAt: updatedAt,
      subOrders: subOrders,
    );
  }

  static MasterOrderStatus _mapMasterOrderStatus(String raw) {
    switch (raw) {
      case 'PENDING_PAYMENT':
        return MasterOrderStatus.pendingPayment;
      case 'PAID':
        return MasterOrderStatus.paid;
      case 'PARTIALLY_COMPLETED':
        return MasterOrderStatus.partiallyCompleted;
      case 'COMPLETED':
        return MasterOrderStatus.completed;
      case 'CANCELLED':
        return MasterOrderStatus.cancelled;
      default:
        return MasterOrderStatus.pendingPayment;
    }
  }

  static SubOrderStatus _mapSubOrderStatus(String raw) {
    switch (raw) {
      case 'PENDING':
        return SubOrderStatus.pending;
      case 'CONFIRMED':
      case 'CHECKED_IN':
      case 'IN_PROGRESS':
        return SubOrderStatus.confirmed;
      case 'REJECTED':
        return SubOrderStatus.rejected;
      case 'COMPLETED':
        return SubOrderStatus.completed;
      case 'CANCELLED':
        return SubOrderStatus.cancelled;
      case 'REFUNDED':
      case 'PARTIALLY_REFUNDED':
        return SubOrderStatus.refunded;
      default:
        return SubOrderStatus.pending;
    }
  }

  static RefundReason _mapRefundReason(String? raw) {
    switch (raw) {
      case 'WEATHER':
        return RefundReason.weather;
      case 'DISPUTE':
        return RefundReason.dispute;
      case 'COMPENSATION':
      case 'VENDOR_FAULT':
      case 'SYSTEM_FAULT':
      case 'ADMIN_OVERRIDE':
        return RefundReason.compensation;
      case 'CUSTOMER_CANCEL':
      case 'CUSTOMER_REQUEST':
      default:
        return RefundReason.customerCancel;
    }
  }

  static RefundStatus _mapRefundStatus(String? raw) {
    switch (raw) {
      case 'PROCESSED':
        return RefundStatus.processed;
      case 'FAILED':
        return RefundStatus.failed;
      case 'PENDING':
      default:
        return RefundStatus.pending;
    }
  }

  Future<MasterOrderModel> createOrder({
    required String bookingId,
    required String idempotencyKey,
    String? discountCode,
  }) async {
    if (bookingId.trim().isEmpty || idempotencyKey.trim().isEmpty) {
      throw AuthFailure('Thiếu mã giữ chỗ hoặc khóa chống trùng.');
    }
    final cleanDiscount = discountCode?.trim();
    final json = await session.request(
      '/api/orders',
      method: 'POST',
      headers: {'Idempotency-Key': idempotencyKey.trim()},
      data: {
        'bookingId': bookingId.trim(),
        if (cleanDiscount != null && cleanDiscount.isNotEmpty)
          'discountCode': cleanDiscount,
      },
    );
    return _parseOrder(json);
  }

  Future<OrderPage> list({int page = 0, int size = 20}) async {
    final query = Uri(
      queryParameters: {'page': '$page', 'size': '$size'},
    ).query;
    final json = await session.request('/api/orders?$query');
    if (json['content'] is! List ||
        json['page'] is! num ||
        json['totalPages'] is! num) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawList = (json['content'] as List)
        .whereType<Map<String, dynamic>>()
        .toList();
    final orders = await Future.wait(rawList.map(_parseOrder));
    return OrderPage(
      content: orders,
      page: (json['page'] as num).toInt(),
      size: (json['size'] as num?)?.toInt() ?? size,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? orders.length,
      totalPages: (json['totalPages'] as num).toInt(),
    );
  }

  Future<MasterOrderModel> detail(String orderId) async {
    final json = await session.request(
      '/api/orders/${Uri.encodeComponent(orderId)}',
    );
    return _parseOrder(json);
  }

  Future<OrderCancellationPreview> cancellationPreview(
    String orderOrSubOrderId,
  ) async {
    final json = await session.request(
      '/api/orders/${Uri.encodeComponent(orderOrSubOrderId)}/cancellation-preview',
    );
    return OrderCancellationPreview.fromJson(json);
  }

  Future<List<RefundModel>> requestRefund(
    String orderOrSubOrderId, {
    required String idempotencyKey,
    String? reason,
    String? masterOrderId,
    Map<String, String>? subOrderServiceNames,
  }) async {
    if (orderOrSubOrderId.trim().isEmpty || idempotencyKey.trim().isEmpty) {
      throw AuthFailure('Thiếu mã đơn hàng hoặc khóa chống trùng.');
    }
    final body = await session.requestJson(
      '/api/orders/${Uri.encodeComponent(orderOrSubOrderId)}/refund-request',
      method: 'POST',
      headers: {'Idempotency-Key': idempotencyKey.trim()},
      data: {if (reason != null && reason.isNotEmpty) 'reason': reason},
    );
    if (body is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return body.whereType<Map<String, dynamic>>().map((item) {
      final subOrderId = item['subOrderId']?.toString() ?? '';
      final rawReason = item['reason']?.toString();
      final createdAt =
          DateTime.tryParse(item['createdAt']?.toString() ?? '')?.toLocal() ??
          DateTime.now();
      return RefundModel(
        id: item['id']?.toString() ?? '',
        subOrderId: subOrderId,
        subOrderCode: '#${_shortId(subOrderId)}',
        masterOrderId: masterOrderId,
        serviceName:
            subOrderServiceNames?[subOrderId] ??
            'Dịch vụ #${_shortId(subOrderId)}',
        amount: ((item['amount'] as num?) ?? 0).round(),
        refundPercentage: ((item['percentage'] as num?) ?? 0).toDouble(),
        reason: _mapRefundReason(rawReason),
        rawReason: rawReason,
        status: _mapRefundStatus(item['status']?.toString()),
        originalPaymentMethod: 'Cổng thanh toán gốc',
        createdAt: createdAt,
      );
    }).toList();
  }

  Future<List<OrderPaymentRecord>> payments(String orderId) async {
    final body = await session.requestJson(
      '/api/orders/${Uri.encodeComponent(orderId)}/payments',
    );
    if (body is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return body
        .whereType<Map<String, dynamic>>()
        .map(OrderPaymentRecord.fromJson)
        .toList();
  }

  Future<List<RefundModel>> refunds(
    String orderId, {
    Map<String, String>? subOrderServiceNames,
  }) async {
    final body = await session.requestJson(
      '/api/orders/${Uri.encodeComponent(orderId)}/refunds',
    );
    if (body is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return body.whereType<Map<String, dynamic>>().map((item) {
      final subOrderId = item['subOrderId']?.toString() ?? '';
      final rawReason = item['reason']?.toString();
      final provider = item['provider']?.toString();
      final createdAt =
          DateTime.tryParse(item['createdAt']?.toString() ?? '')?.toLocal() ??
          DateTime.now();
      final processedAt = DateTime.tryParse(
        item['processedAt']?.toString() ?? '',
      )?.toLocal();
      return RefundModel(
        id: item['id']?.toString() ?? '',
        subOrderId: subOrderId,
        subOrderCode: '#${_shortId(subOrderId)}',
        masterOrderId: orderId,
        serviceName:
            subOrderServiceNames?[subOrderId] ??
            'Dịch vụ #${_shortId(subOrderId)}',
        amount: ((item['amount'] as num?) ?? 0).round(),
        refundPercentage: ((item['percentage'] as num?) ?? 0).toDouble(),
        reason: _mapRefundReason(rawReason),
        rawReason: rawReason,
        status: _mapRefundStatus(item['status']?.toString()),
        originalPaymentMethod: provider ?? 'Cổng thanh toán gốc',
        providerTransactionId: item['providerTransactionId']?.toString(),
        providerRefundId: item['providerRefundId']?.toString(),
        lastError: item['lastError']?.toString(),
        processedAt: processedAt,
        createdAt: createdAt,
      );
    }).toList();
  }

  Future<OrderReceipt> receipt(String orderId) async {
    final json = await session.request(
      '/api/orders/${Uri.encodeComponent(orderId)}/receipt',
    );
    return OrderReceipt.fromJson(json);
  }

  Future<Uint8List> receiptPdf(String orderId) async {
    return session.requestBytes(
      '/api/orders/${Uri.encodeComponent(orderId)}/receipt?format=pdf',
      headers: const {'Accept': 'application/pdf'},
    );
  }

  Future<DiscountPreviewResult> previewDiscount({
    required String bookingId,
    required String code,
  }) async {
    final json = await session.request(
      '/api/checkout/discount-preview',
      method: 'POST',
      data: {
        'bookingId': bookingId,
        'code': code.trim(),
      },
    );
    return DiscountPreviewResult.fromJson(json);
  }

  Future<CheckinQrCode> generateQrCode(String subOrderOrOrderId) async {
    final json = await session.request(
      '/api/bookings/${Uri.encodeComponent(subOrderOrOrderId)}/qr-code',
      method: 'POST',
      retry: false,
    );
    return CheckinQrCode.fromJson(json);
  }
}

class CheckinQrCode {
  const CheckinQrCode({
    required this.subOrderId,
    required this.qrToken,
    required this.expiresAt,
  });

  final String subOrderId;
  final String qrToken;
  final DateTime expiresAt;

  factory CheckinQrCode.fromJson(Map<String, dynamic> json) {
    if (json['subOrderId'] is! String ||
        json['qrToken'] is! String ||
        (json['qrToken'] as String).isEmpty) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return CheckinQrCode(
      subOrderId: json['subOrderId'] as String,
      qrToken: json['qrToken'] as String,
      expiresAt:
          DateTime.tryParse(json['expiresAt']?.toString() ?? '') ??
          DateTime.now().add(const Duration(days: 1)),
    );
  }
}

class DiscountPreviewItem {
  const DiscountPreviewItem({
    required this.bookingItemId,
    required this.vendorId,
    required this.serviceId,
    required this.originalSubtotal,
    required this.eligible,
    required this.vendorDiscountAmount,
    required this.platformDiscountAmount,
    required this.discountAmount,
    required this.finalSubtotal,
  });

  final String bookingItemId;
  final String vendorId;
  final String serviceId;
  final num originalSubtotal;
  final bool eligible;
  final num vendorDiscountAmount;
  final num platformDiscountAmount;
  final num discountAmount;
  final num finalSubtotal;

  factory DiscountPreviewItem.fromJson(Map<String, dynamic> json) {
    return DiscountPreviewItem(
      bookingItemId: json['bookingItemId']?.toString() ?? '',
      vendorId: json['vendorId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      originalSubtotal: (json['originalSubtotal'] as num?) ?? 0,
      eligible: json['eligible'] == true,
      vendorDiscountAmount: (json['vendorDiscountAmount'] as num?) ?? 0,
      platformDiscountAmount: (json['platformDiscountAmount'] as num?) ?? 0,
      discountAmount: (json['discountAmount'] as num?) ?? 0,
      finalSubtotal: (json['finalSubtotal'] as num?) ?? 0,
    );
  }
}

class DiscountPreviewResult {
  const DiscountPreviewResult({
    required this.valid,
    this.errorCode,
    this.message,
    required this.code,
    this.scope,
    this.sponsorType,
    required this.originalTotalAmount,
    required this.totalDiscountAmount,
    required this.finalPayableAmount,
    required this.itemBreakdown,
  });

  final bool valid;
  final String? errorCode;
  final String? message;
  final String code;
  final String? scope;
  final String? sponsorType;
  final num originalTotalAmount;
  final num totalDiscountAmount;
  final num finalPayableAmount;
  final List<DiscountPreviewItem> itemBreakdown;

  factory DiscountPreviewResult.fromJson(Map<String, dynamic> json) {
    if (json['valid'] is! bool) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    final rawItems = json['itemBreakdown'] as List? ?? const [];
    return DiscountPreviewResult(
      valid: json['valid'] as bool,
      errorCode: json['errorCode']?.toString(),
      message: json['message']?.toString(),
      code: json['code']?.toString() ?? '',
      scope: json['scope']?.toString(),
      sponsorType: json['sponsorType']?.toString(),
      originalTotalAmount: (json['originalTotalAmount'] as num?) ?? 0,
      totalDiscountAmount: (json['totalDiscountAmount'] as num?) ?? 0,
      finalPayableAmount: (json['finalPayableAmount'] as num?) ?? 0,
      itemBreakdown: rawItems
          .whereType<Map<String, dynamic>>()
          .map(DiscountPreviewItem.fromJson)
          .toList(),
    );
  }
}
