import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { formatDisplayCode } from '../displayCode.ts';

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
      formatDisplayCode('00060000-0000-0000-0000-000000000001', 'PAY'),
      'PAY-00060001'
    );
    assert.equal(
      formatDisplayCode('99999999-9999-9999-9999-999999999992', 'SUB'),
      'SUB-99999992'
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
});
