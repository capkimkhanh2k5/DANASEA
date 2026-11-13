import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  formatDisplayCode,
  filterMasterOrders,
  ordersToCsv,
  type AdminDisplayOrder,
} from '../ordersModel.ts';
import type { MasterOrder } from '../../../../types/index.ts';

describe('ordersModel — formatDisplayCode and SUB prefix', () => {
  test('formats MasterOrder and SubOrder seeds distinctly', () => {
    assert.equal(formatDisplayCode('99999999-9999-9999-9999-999999999991', 'ORD'), 'ORD-99999991');
    assert.equal(formatDisplayCode('99999999-9999-9999-9999-999999999992', 'SUB'), 'SUB-99999992');
    assert.equal(formatDisplayCode('99999999-9999-9999-9999-999999999993', 'DISP'), 'DISP-99999993');
  });

  test('preserves already formatted SUB and ORD codes', () => {
    assert.equal(formatDisplayCode('SUB-99999992'), 'SUB-99999992');
    assert.equal(formatDisplayCode('ORD-99999991'), 'ORD-99999991');
  });
});

describe('ordersModel — filterMasterOrders across all statuses', () => {
  const sampleOrders: MasterOrder[] = [
    { id: 'ord-1', customerId: 'c-1', status: 'COMPLETED' as unknown as MasterOrder['status'], totalAmount: 100, discountAmount: 0, discountCodeId: '' },
    { id: 'ord-2', customerId: 'c-2', status: 'PAID' as unknown as MasterOrder['status'], totalAmount: 200, discountAmount: 0, discountCodeId: '' },
    { id: 'ord-3', customerId: 'c-3', status: 'CANCELLED' as unknown as MasterOrder['status'], totalAmount: 300, discountAmount: 0, discountCodeId: '' },
  ];

  test('filters by all returns all items', () => {
    assert.equal(filterMasterOrders(sampleOrders, '', 'all').length, 3);
  });

  test('filters by PAID status accurately', () => {
    const paid = filterMasterOrders(sampleOrders, '', 'PAID');
    assert.equal(paid.length, 1);
    assert.equal(paid[0].id, 'ord-2');
  });

  test('filters by COMPLETED status accurately', () => {
    const completed = filterMasterOrders(sampleOrders, '', 'COMPLETED');
    assert.equal(completed.length, 1);
    assert.equal(completed[0].id, 'ord-1');
  });

  test('filters by CANCELLED status accurately', () => {
    const cancelled = filterMasterOrders(sampleOrders, '', 'CANCELLED');
    assert.equal(cancelled.length, 1);
    assert.equal(cancelled[0].id, 'ord-3');
  });
});

describe('ordersModel — ordersToCsv export', () => {
  test('exports CSV with readable display code and proper columns', () => {
    const sample: AdminDisplayOrder[] = [
      {
        id: '99999999-9999-9999-9999-999999999991',
        customerId: '00010000-0000-0000-0000-000000000001',
        customerName: 'Nguyễn Văn Test',
        status: 'PAID' as unknown as MasterOrder['status'],
        totalAmount: 650000,
        discountAmount: 0,
        discountCodeId: '',
        paymentStatus: 'COMPLETED',
        createdAt: '2026-11-13T10:00:00Z',
      },
    ];

    const csv = ordersToCsv(sample);
    assert.ok(csv.includes('Mã Hiển Thị'));
    assert.ok(csv.includes('Mã Đơn (UUID)'));
    assert.ok(csv.includes('ORD-99999991'));
    assert.ok(csv.includes('Nguyễn Văn Test'));
  });
});
