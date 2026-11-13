import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/auth/auth_session.dart';
import 'package:mobile/core/data/booking_repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('cart holds all selected slots in one booking and preserves options', () async {
    HttpOverrides.global = null;
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final session = AuthSession(baseUrl: 'http://127.0.0.1:${server.port}');
    addTearDown(() async {
      session.client.close(force: true);
      await server.close(force: true);
    });
    final items = [
      {'slotId': 'slot-1', 'optionId': 'option-1', 'quantity': 2,
        'participantsCount': 2, 'allowSplit': true},
      {'slotId': 'slot-2', 'optionId': 'option-2', 'quantity': 1,
        'participantsCount': 1, 'allowSplit': false},
    ];
    var requests = 0;
    server.listen((request) async {
      requests++;
      expect(request.method, 'POST');
      expect(request.uri.path, '/api/bookings/hold');
      expect(jsonDecode(await utf8.decoder.bind(request).join()), {'items': items});
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({
        'bookingId': 'booking-cart', 'status': 'HOLD', 'totalAmount': 420000,
        'holdExpiresAt': DateTime.now().add(const Duration(minutes: 5)).toIso8601String(),
      }));
      await request.response.close();
    });
    final repository = BookingRepository(session: session);
    final hold = await repository.holdItems(items);
    expect(hold.id, 'booking-cart');
    expect(hold.totalAmount, 420000);
    expect(requests, 1);
    await expectLater(repository.holdItems([]), throwsA(isA<AuthFailure>()));
    expect(requests, 1);
  });
}
