import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { 
  formatDisplayCode, 
  matchesDisplayCodeOrId, 
  formatTextWithDisplayCodes,
  resolveDisplayCodeToUuid,
  translateDisplayCodesToUuids,
} from '../displayCode.ts';

describe('displayCode — formatDisplayCode standardizer', () => {
  test('formats seed dispute UUID to DISP code with tail preservation', () => {
    assert.equal(
      formatDisplayCode('77777777-7777-7777-7777-777777777704', 'DISP'),
      'DISP-77777704'
    );
    assert.equal(
      formatDisplayCode('77777777-7777-7777-7777-777777777701', 'DISP'),
      'DISP-77777701'
    );
  });

  test('formats seed orders and payouts accurately', () => {
    assert.equal(
      formatDisplayCode('00010000-0000-0000-0000-000000000036', 'ORD'),
      'ORD-00010036'
    );
    assert.equal(
      formatDisplayCode('00010000-0000-0000-0000-000000000042', 'ORD'),
      'ORD-00010042'
    );
    assert.equal(
      formatDisplayCode('00020000-0000-0000-0000-000000000042', 'SUB'),
      'SUB-00020042'
    );
    assert.equal(
      formatDisplayCode('00060000-0000-0000-0000-000000000001', 'PAY'),
      'PAY-00060001'
    );
    assert.equal(
      formatDisplayCode('99999999-9999-9999-9999-999999999992', 'SUB'),
      'SUB-99999992'
    );
  });

  test('matchesDisplayCodeOrId matches both raw UUID and display codes', () => {
    const rawUuid = '00010000-0000-0000-0000-000000000042';
    assert.equal(matchesDisplayCodeOrId(rawUuid, rawUuid, 'ORD'), true);
    assert.equal(matchesDisplayCodeOrId('ORD-00010042', rawUuid, 'ORD'), true);
    assert.equal(matchesDisplayCodeOrId('#ORD-00010042', rawUuid, 'ORD'), true);
    assert.equal(matchesDisplayCodeOrId('#ord-00010042', rawUuid, 'ORD'), true);
    assert.equal(matchesDisplayCodeOrId('ORD-99999999', rawUuid, 'ORD'), false);
  });

  test('formatTextWithDisplayCodes transforms raw UUID in message or title', () => {
    const text = 'trạng thái đơn hàng 00010000-0000-0000-0000-000000000042';
    assert.equal(
      formatTextWithDisplayCodes(text),
      'trạng thái đơn hàng #ORD-00010042'
    );
  });

  test('formats random standard UUID v4 cleanly', () => {
    assert.equal(
      formatDisplayCode('1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', 'DISP'),
      'DISP-1A5E9F3E'
    );
  });

  test('formats custom prefixes like SLOT, DOC, VND', () => {
    assert.equal(
      formatDisplayCode('12345678-abcd-ef01-2345-6789abcdef01', 'SLOT'),
      'SLOT-12345678'
    );
    assert.equal(
      formatDisplayCode('87654321-abcd-ef01-2345-6789abcdef01', 'DOC'),
      'DOC-87654321'
    );
    assert.equal(
      formatDisplayCode('11111111-1111-1111-1111-111111111111', 'VND'),
      'VND-11111111'
    );
  });

  test('preserves already prefixed codes (idempotent)', () => {
    assert.equal(formatDisplayCode('DISP-77777704', 'DISP'), 'DISP-77777704');
    assert.equal(formatDisplayCode('ORD-00010036', 'ORD'), 'ORD-00010036');
    assert.equal(formatDisplayCode('SUB-99999992', 'SUB'), 'SUB-99999992');
    assert.equal(formatDisplayCode('PAY-00060001', 'PAY'), 'PAY-00060001');
    assert.equal(formatDisplayCode('SLOT-12345678', 'SLOT'), 'SLOT-12345678');
  });

  test('handles empty or falsy inputs', () => {
    assert.equal(formatDisplayCode(''), '');
    assert.equal(formatDisplayCode(null as unknown as string), '');
    assert.equal(formatDisplayCode(undefined as unknown as string), '');
  });

  test('resolveDisplayCodeToUuid reconstructs 36-char seed UUID from display code', () => {
    assert.equal(
      resolveDisplayCodeToUuid('ORD-00010042'),
      '00010000-0000-0000-0000-000000000042'
    );
    assert.equal(
      resolveDisplayCodeToUuid('#SUB-00020005'),
      '00020000-0000-0000-0000-000000000005'
    );
    assert.equal(
      resolveDisplayCodeToUuid('#DISP-00050001'),
      '00050000-0000-0000-0000-000000000001'
    );
    assert.equal(
      resolveDisplayCodeToUuid('SOMETHING-ELSE'),
      'SOMETHING-ELSE'
    );
  });

  test('translateDisplayCodesToUuids replaces short codes with seed UUIDs for backend matching', () => {
    const text = 'tra cứu trạng thái đơn hàng #ORD-00010042 và sub #SUB-00020015';
    assert.equal(
      translateDisplayCodesToUuids(text),
      'tra cứu trạng thái đơn hàng 00010000-0000-0000-0000-000000000042 và sub 00020000-0000-0000-0000-000000000015'
    );
  });
});
