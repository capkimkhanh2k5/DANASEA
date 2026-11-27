import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/utils/display_code.dart';

void main() {
  test('formats seed and random UUIDs consistently with Web and Backend', () {
    expect(
      formatDisplayCode('00010000-0000-0000-0000-000000000042', prefix: 'ORD'),
      'ORD-00010042',
    );
    expect(
      formatDisplayCode('00020000-0000-0000-0000-000000000015', prefix: 'SUB'),
      'SUB-00020015',
    );
    expect(
      formatDisplayCode('b0010000-0000-0000-0000-000000000001', prefix: 'ORD'),
      'ORD-B0010001',
    );
    expect(
      formatDisplayCode('b0020000-0000-0000-0000-000000000009', prefix: 'SUB'),
      'SUB-B0020009',
    );
    expect(
      formatDisplayCode('00050000-0000-0000-0000-000000000001', prefix: 'DISP'),
      'DISP-00050001',
    );
    expect(
      formatDisplayCode('7a2f1c4d-1234-4abc-9def-0123456789ab', prefix: 'ORD'),
      'ORD-7A2F1C4D',
    );
    expect(
      formatDisplayCodeWithHash('00010000-0000-0000-0000-000000000042', prefix: 'ORD'),
      '#ORD-00010042',
    );
  });
}
