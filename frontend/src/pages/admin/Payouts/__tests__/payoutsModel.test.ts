import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  formatDisplayCode,
  calculatePayoutMetrics,
  filterPayouts,
  type PayoutExtended,
} from '../payoutsModel.ts';

describe('payoutsModel — formatDisplayCode short code generation', () => {
  test('formats standard UUID to clean short code', () => {
    assert.equal(formatDisplayCode('18b958c2-28cb-4f1a-b67c-96beec944919', 'PAY'), 'PAY-18B958C2');
    assert.equal(formatDisplayCode('18b958c2-28cb-4f1a-b67c-96beec944919', 'STL'), 'STL-18B958C2');
  });

  test('formats sequential seed UUIDs without collision using tail entropy', () => {
    assert.equal(formatDisplayCode('00060000-0000-0000-0000-000000000001', 'PAY'), 'PAY-00060001');
    assert.equal(formatDisplayCode('00060000-0000-0000-0000-000000000010', 'PAY'), 'PAY-00060010');
    assert.equal(formatDisplayCode('00060000-0000-0000-0000-000000000011', 'PAY'), 'PAY-00060011');
    assert.notEqual(
      formatDisplayCode('00060000-0000-0000-0000-000000000010'),
      formatDisplayCode('00060000-0000-0000-0000-000000000011')
    );
    // 99999999 seed disambiguation between Dispute (...9993) and SubOrder (...9992)
    assert.equal(formatDisplayCode('99999999-9999-9999-9999-999999999993', 'DISP'), 'DISP-99999993');
    assert.equal(formatDisplayCode('99999999-9999-9999-9999-999999999992', 'ORD'), 'ORD-99999992');
    assert.notEqual(
      formatDisplayCode('99999999-9999-9999-9999-999999999993', 'DISP'),
      formatDisplayCode('99999999-9999-9999-9999-999999999992', 'ORD')
    );
  });

  test('preserves already formatted codes', () => {
    assert.equal(formatDisplayCode('PAY-18B958C2'), 'PAY-18B958C2');
    assert.equal(formatDisplayCode('STL-00060010'), 'STL-00060010');
    assert.equal(formatDisplayCode(''), '');
  });
});

describe('payoutsModel — calculatePayoutMetrics & filterPayouts', () => {
  const mockPayouts: PayoutExtended[] = [
    {
      id: '00060000-0000-0000-0000-000000000001',
      vendorId: 'v1',
      settlementId: 's1',
      amount: 1000000,
      status: 'REQUESTED' as unknown as PayoutExtended['status'],
      processedBy: '',
      processedAt: '',
      vendorName: 'Loca Travel Đà Nẵng',
      bankName: 'BIDV',
      bankAccountNumber: '621140119996',
      bankAccountHolder: 'Loca Travel',
      periodText: 'Kỳ 1',
      totalOrdersCount: 5,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-01',
    },
    {
      id: '00060000-0000-0000-0000-000000000002',
      vendorId: 'v2',
      settlementId: 's2',
      amount: 2500000,
      status: 'APPROVED' as unknown as PayoutExtended['status'],
      processedBy: 'Admin',
      processedAt: '2026-11-02',
      vendorName: 'Surf Shack',
      bankName: 'VPBank',
      bankAccountNumber: '797979683863',
      bankAccountHolder: 'Surf Shack',
      periodText: 'Kỳ 1',
      totalOrdersCount: 10,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-02',
    },
    {
      id: '00060000-0000-0000-0000-000000000003',
      vendorId: 'v3',
      settlementId: 's3',
      amount: 500000,
      status: 'PAID' as unknown as PayoutExtended['status'],
      processedBy: 'Admin',
      processedAt: '2026-11-03',
      vendorName: 'Danabeach Club',
      bankName: 'VPBank',
      bankAccountNumber: '6969696163',
      bankAccountHolder: 'Danabeach',
      periodText: 'Kỳ 1',
      totalOrdersCount: 2,
      createdAt: '2026-11-01',
      updatedAt: '2026-11-03',
    },
  ];

  test('calculates correct metrics with APPROVED and PAID', () => {
    const metrics = calculatePayoutMetrics(mockPayouts);
    assert.equal(metrics.totalCount, 3);
    assert.equal(metrics.pendingCount, 1);
    assert.equal(metrics.approvedCount, 1);
    assert.equal(metrics.paidCount, 1);
    assert.equal(metrics.totalPendingAmount, 1000000);
    assert.equal(metrics.totalApprovedAmount, 2500000);
    assert.equal(metrics.totalPaidAmount, 500000);
  });

  test('filters by short code search', () => {
    const filtered = filterPayouts(mockPayouts, 'PAY-00060002', 'all');
    assert.equal(filtered.length, 1);
    assert.equal(filtered[0].vendorName, 'Surf Shack');
  });

  test('filters by status APPROVED and PAID', () => {
    const approved = filterPayouts(mockPayouts, '', 'APPROVED');
    assert.equal(approved.length, 1);
    assert.equal(approved[0].status, 'APPROVED');

    const paid = filterPayouts(mockPayouts, '', 'PAID');
    assert.equal(paid.length, 1);
    assert.equal(paid[0].status, 'PAID');
  });
});
