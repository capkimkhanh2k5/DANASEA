import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/auth/auth_session.dart';
import 'package:mobile/core/data/payment_repository.dart';
import 'package:mobile/core/models/danasea_models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('legacy backend does not advertise unsupported gateways', () async {
    HttpOverrides.global = null;
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final session = AuthSession(baseUrl: 'http://127.0.0.1:${server.port}');
    addTearDown(() async {
      session.client.close(force: true);
      await server.close(force: true);
    });
    var supportsDiscovery = false;
    server.listen((request) async {
      expect(request.uri.path, '/api/payments/providers');
      request.response.headers.contentType = ContentType.json;
      request.response.statusCode = supportsDiscovery ? 200 : 404;
      request.response.write(jsonEncode(supportsDiscovery
          ? ['PAYOS', 'ZALOPAY']
          : {'message': 'Endpoint not found'}));
      await request.response.close();
    });
    final repository = PaymentRepository(session: session);
    expect(await repository.getAvailableProviders(),
        [PaymentProvider.vnpay, PaymentProvider.paypal]);
    supportsDiscovery = true;
    expect(await repository.getAvailableProviders(),
        [PaymentProvider.payos, PaymentProvider.zalopay]);
  });

  test('rejects legacy mock orders before payment requests', () {
    for (final id in ['DNS-12345', 'DNS-SUB-12345', '', '123']) {
      expect(
        () => PaymentRepository.validateOrderId(id),
        throwsA(isA<AuthFailure>().having((e) => e.status, 'status', 400)),
      );
    }
  });

  test('accepts backend UUID order identifiers', () {
    expect(
      () => PaymentRepository.validateOrderId('550e8400-e29b-41d4-a716-446655440000'),
      returnsNormally,
    );
  });
}
