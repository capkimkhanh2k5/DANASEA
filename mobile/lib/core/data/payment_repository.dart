import 'dart:math';
import '../auth/auth_session.dart';
import '../models/danasea_models.dart';
import 'order_repository.dart';

class PaymentIntentModel {
  const PaymentIntentModel({
    required this.paymentId,
    required this.orderId,
    required this.provider,
    required this.amount,
    required this.status,
    required this.paymentReference,
    required this.createdAt,
  });

  final String paymentId;
  final String orderId;
  final String provider;
  final num amount;
  final String status;
  final String paymentReference;
  final DateTime createdAt;

  bool get hasRedirectUrl {
    final ref = paymentReference.trim();
    return ref.startsWith('http://') || ref.startsWith('https://');
  }

  factory PaymentIntentModel.fromJson(Map<String, dynamic> json) {
    if (json['paymentId'] is! String ||
        json['orderId'] is! String ||
        json['provider'] is! String ||
        json['amount'] is! num ||
        json['status'] is! String) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return PaymentIntentModel(
      paymentId: json['paymentId'] as String,
      orderId: json['orderId'] as String,
      provider: json['provider'] as String,
      amount: json['amount'] as num,
      status: json['status'] as String,
      paymentReference: json['paymentReference']?.toString() ?? '',
      createdAt:
          DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
          DateTime.now(),
    );
  }
}

class PaymentRepository {
  PaymentRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;

  final AuthSession session;

  static String generateIdempotencyKey([String prefix = 'pay']) {
    final random = Random();
    final suffix = List.generate(
      12,
      (_) => random.nextInt(36).toRadixString(36),
    ).join();
    return '$prefix-${DateTime.now().millisecondsSinceEpoch}-$suffix';
  }

  static String providerToBackend(PaymentProvider provider) {
    switch (provider) {
      case PaymentProvider.vnpay:
        return 'VNPAY';
      case PaymentProvider.momo:
        return 'MOMO';
      case PaymentProvider.sepay:
        return 'SEPAY';
      case PaymentProvider.paypal:
        return 'PAYPAL';
    }
  }

  static PaymentProvider providerFromBackend(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'PAYPAL':
        return PaymentProvider.paypal;
      case 'MOMO':
        return PaymentProvider.momo;
      case 'SEPAY':
        return PaymentProvider.sepay;
      case 'VNPAY':
      default:
        return PaymentProvider.vnpay;
    }
  }

  static PaymentStatus statusFromBackend(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'SUCCESS':
        return PaymentStatus.success;
      case 'FAILED':
        return PaymentStatus.failed;
      case 'REFUNDED':
        return PaymentStatus.refunded;
      case 'PENDING':
      default:
        return PaymentStatus.pending;
    }
  }

  Future<PaymentIntentModel> createIntent({
    required String orderId,
    required PaymentProvider provider,
    required String idempotencyKey,
  }) async {
    final json = await session.request(
      '/api/payments/${Uri.encodeComponent(orderId)}/create-intent',
      method: 'POST',
      headers: {'Idempotency-Key': idempotencyKey},
      data: {'provider': providerToBackend(provider)},
      retry: false,
    );
    return PaymentIntentModel.fromJson(json);
  }

  Future<List<OrderPaymentRecord>> getOrderStatus(String orderId) async {
    final raw = await session.requestJson(
      '/api/payments/${Uri.encodeComponent(orderId)}/status',
    );
    if (raw is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return raw
        .whereType<Map<String, dynamic>>()
        .map(OrderPaymentRecord.fromJson)
        .toList();
  }

  Future<List<OrderPaymentRecord>> getOrderPayments(String orderId) async {
    final raw = await session.requestJson(
      '/api/payments/${Uri.encodeComponent(orderId)}/payments',
    );
    if (raw is! List) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return raw
        .whereType<Map<String, dynamic>>()
        .map(OrderPaymentRecord.fromJson)
        .toList();
  }

  Future<OrderPaymentRecord> getPaymentDetail(String paymentId) async {
    final json = await session.request(
      '/api/payments/detail/${Uri.encodeComponent(paymentId)}',
    );
    return OrderPaymentRecord.fromJson(json);
  }

  Future<OrderPaymentRecord> capturePayPalOrder({
    required String paymentId,
    required String paypalOrderId,
    required String idempotencyKey,
  }) async {
    final json = await session.request(
      '/api/payments/paypal/capture',
      method: 'POST',
      headers: {'Idempotency-Key': idempotencyKey},
      data: {
        'paymentId': paymentId,
        'paypalOrderId': paypalOrderId.trim(),
      },
      retry: false,
    );
    return OrderPaymentRecord.fromJson(json);
  }
}
