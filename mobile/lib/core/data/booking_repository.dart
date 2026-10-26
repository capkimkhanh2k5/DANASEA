import '../auth/auth_session.dart';

class BookingHold {
  const BookingHold({
    required this.id,
    required this.status,
    required this.totalAmount,
    required this.expiresAt,
  });
  final String id, status;
  final num totalAmount;
  final DateTime? expiresAt;

  bool get confirmed => status == 'CONFIRMED';
  bool get expired => expiresAt != null && !DateTime.now().isBefore(expiresAt!);

  factory BookingHold.fromJson(Map<String, dynamic> json) {
    final expiresAt = DateTime.tryParse(
      json['holdExpiresAt']?.toString() ?? '',
    );
    if (json['bookingId'] is! String ||
        (json['bookingId'] as String).isEmpty ||
        json['status'] is! String ||
        json['totalAmount'] is! num ||
        (json['status'] == 'HOLD' && expiresAt == null)) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return BookingHold(
      id: json['bookingId'] as String,
      status: json['status'] as String,
      totalAmount: json['totalAmount'] as num,
      expiresAt: expiresAt,
    );
  }
}

class BookingRepository {
  BookingRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;
  final AuthSession session;

  Future<BookingPage> list({int page = 0, String? status}) async {
    final query = Uri(
      queryParameters: {
        'page': '$page',
        'size': '10',
        'sortBy': 'createdAt',
        'sortDir': 'desc',
        if (status != null) 'status': status,
      },
    ).query;
    final json = await session.request('/api/bookings?$query');
    return BookingPage(
      (json['content'] as List)
          .map((e) => CustomerBooking.fromJson(e as Map<String, dynamic>))
          .toList(),
      json['page'] as int,
      json['totalPages'] as int,
    );
  }

  Future<CustomerBooking> detail(String id) async => CustomerBooking.fromJson(
    await session.request('/api/bookings/${Uri.encodeComponent(id)}'),
  );

  Future<BookingCancellation> cancelBooking(String id, {String? reason}) async {
    final json = await session.request(
      '/api/bookings/${Uri.encodeComponent(id)}/cancel',
      method: 'PATCH',
      data: {
        if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
      },
    );
    return BookingCancellation(
      json['message'] as String? ?? 'Đã hủy booking.',
      json['refundEligible'] as bool,
      json['refundPercentage'] as int,
      json['refundAmount'] as num,
    );
  }

  Future<BookingHold> hold({
    required String slotId,
    required String optionId,
    required int quantity,
    int? participantsCount,
    bool allowSplit = false,
  }) async {
    if (slotId.isEmpty ||
        optionId.isEmpty ||
        quantity < 1 ||
        quantity > 100 ||
        (participantsCount != null && participantsCount < 1)) {
      throw AuthFailure('Vui lòng kiểm tra số lượng và khung giờ.');
    }
    return BookingHold.fromJson(
      await session.request(
        '/api/bookings/hold',
        method: 'POST',
        data: {
          'items': [
            {
              'slotId': slotId,
              'optionId': optionId,
              'quantity': quantity,
              if (participantsCount != null)
                'participantsCount': participantsCount,
              'allowSplit': allowSplit,
            },
          ],
        },
      ),
    );
  }

  Future<BookingHold> confirm(String holdId) async => BookingHold.fromJson(
    await session.request(
      '/api/bookings/${Uri.encodeComponent(holdId)}/confirm',
      method: 'POST',
    ),
  );

  Future<void> cancel(String holdId) async {
    // DELETE returns 204 with no JSON payload.
    await session.requestJson(
      '/api/bookings/hold/${Uri.encodeComponent(holdId)}',
      method: 'DELETE',
    );
  }
}

class BookingPage {
  const BookingPage(this.content, this.page, this.totalPages);
  final List<CustomerBooking> content;
  final int page, totalPages;
}

class CustomerBooking {
  const CustomerBooking({
    required this.id,
    required this.status,
    required this.totalAmount,
    required this.totalItems,
    this.createdAt,
    this.expiresAt,
    required this.items,
  });
  final String id, status;
  final num totalAmount;
  final int totalItems;
  final DateTime? createdAt, expiresAt;
  final List<CustomerBookingItem> items;
  bool get canCancel =>
      ['HOLD', 'PENDING_PAYMENT', 'CONFIRMED'].contains(status);

  factory CustomerBooking.fromJson(Map<String, dynamic> json) {
    final items = (json['items'] as List? ?? [])
        .map((e) => CustomerBookingItem.fromJson(e as Map<String, dynamic>))
        .toList();
    return CustomerBooking(
      id: json['bookingId'] as String,
      status: json['status'] as String,
      totalAmount: json['totalAmount'] as num,
      totalItems: json['totalItems'] as int? ?? items.length,
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      expiresAt: DateTime.tryParse(json['holdExpiresAt']?.toString() ?? ''),
      items: items,
    );
  }
}

class CustomerBookingItem {
  const CustomerBookingItem(
    this.serviceId,
    this.vendorId,
    this.date,
    this.time,
    this.quantity,
    this.price,
    this.subtotal,
  );
  final String serviceId, vendorId, date, time;
  final int quantity;
  final num price, subtotal;
  factory CustomerBookingItem.fromJson(Map<String, dynamic> json) =>
      CustomerBookingItem(
        json['serviceId'] as String,
        json['vendorId'] as String,
        json['bookingDate']?.toString() ?? '—',
        json['bookingTime']?.toString() ?? '—',
        json['quantity'] as int,
        json['price'] as num,
        json['subtotal'] as num,
      );
}

class BookingCancellation {
  const BookingCancellation(
    this.message,
    this.refundEligible,
    this.refundPercentage,
    this.refundAmount,
  );
  final String message;
  final bool refundEligible;
  final int refundPercentage;
  final num refundAmount;
}
