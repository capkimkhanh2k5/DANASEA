import 'package:flutter_test/flutter_test.dart';
import 'package:vendor_mobile/core/data/vendor_repositories.dart';
import 'package:vendor_mobile/core/models/vendor_models.dart';

void main() {
  test('keeps a cancelled departure and its calendar date from the API', () {
    final slot = VendorSlotRepository.parseSlot({
      'id': 'slot-1',
      'serviceId': 'service-1',
      'date': '2026-11-05',
      'status': 'CANCELLED',
    });

    expect(slot.status, SlotStatus.cancelled);
    expect(slot.matchesDate(DateTime(2026, 11, 5, 12)), isTrue);
    expect(slot.matchesDate(DateTime(2027, 11, 5)), isFalse);
  });
}
