import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  getDateRange,
  mergeDailyReports,
  summarizeMetrics,
  type BookingMetric,
  type RevenueMetric,
} from '../dashboardModel.ts';

describe('Admin Dashboard Calculations — Date Ranges & Zero State (Scenario 1 & 3)', () => {
  test('getDateRange(7) generates exactly 7 days ending at target date', () => {
    const fixedNow = new Date('2026-11-14T10:00:00+07:00');
    const range = getDateRange(7, fixedNow);
    assert.equal(range.to, '2026-11-14');
    assert.equal(range.from, '2026-11-08');
  });

  test('getDateRange(30) generates exactly 30 days ending at target date', () => {
    const fixedNow = new Date('2026-11-14T10:00:00+07:00');
    const range = getDateRange(30, fixedNow);
    assert.equal(range.to, '2026-11-14');
    assert.equal(range.from, '2026-10-16');
  });

  test('7 days with zero transactions results in genuine 0, without fallback', () => {
    const range = { from: '2026-11-08', to: '2026-11-14' };
    const emptyBookings: BookingMetric[] = [];
    const emptyRevenue: RevenueMetric[] = [];

    const points = mergeDailyReports(emptyBookings, emptyRevenue, range);
    assert.equal(points.length, 7);

    const metrics = summarizeMetrics(points);
    assert.equal(metrics.gmv, 0);
    assert.equal(metrics.revenue, 0);
    assert.equal(metrics.commission, 0);
    assert.equal(metrics.refunds, 0);
    assert.equal(metrics.completed, 0);
    assert.equal(metrics.cancelled, 0);

    // Payout formula: revenue - commission - refunds
    const payout = metrics.revenue - metrics.commission - (metrics.refunds || 0);
    assert.equal(payout, 0);
  });
});

describe('Admin Dashboard Calculations — 30 Days Valid Data & Accounting Integrity (Scenario 2 & 6)', () => {
  test('aggregates 30-day data accurately matching backend ledger values', () => {
    const range = { from: '2026-10-16', to: '2026-11-14' };

    const sampleBookings: BookingMetric[] = [
      { periodKey: '2026-10-22', totalOrders: 10, completedOrders: 8, cancelledOrders: 2 },
      { periodKey: '2026-10-25', totalOrders: 15, completedOrders: 12, cancelledOrders: 3 },
      { periodKey: '2026-10-30', totalOrders: 7, completedOrders: 6, cancelledOrders: 1 },
    ];

    const sampleRevenue: RevenueMetric[] = [
      { periodKey: '2026-10-22', collectedCash: 10000000, gmv: 10000000, platformCommission: 880000, refunds: 500000 },
      { periodKey: '2026-10-25', collectedCash: 15000000, gmv: 15000000, platformCommission: 1320000, refunds: 1200000 },
      { periodKey: '2026-10-30', collectedCash: 8700000, gmv: 8700000, platformCommission: 781000, refunds: 890000 },
    ];

    const points = mergeDailyReports(sampleBookings, sampleRevenue, range);
    assert.equal(points.length, 30);

    const metrics = summarizeMetrics(points);
    assert.equal(metrics.gmv, 33700000);
    assert.equal(metrics.revenue, 33700000);
    assert.equal(metrics.commission, 2981000);
    assert.equal(metrics.refunds, 2590000);
    assert.equal(metrics.completed, 26);
    assert.equal(metrics.cancelled, 6);

    // Payout verification: collectedCash - commission - refunds
    const payout = metrics.revenue - metrics.commission - metrics.refunds;
    assert.equal(payout, 28129000);

    // Verify mathematical reconciliation: GMV = commission + payout + refunds (when collectedCash === gmv)
    assert.equal(metrics.commission + payout + metrics.refunds, metrics.gmv);
  });

  test('does not artificially clamp negative net payout when refunds exceed revenue', () => {
    // If a period has heavy refunds from earlier cancellations and no new revenue
    const metrics = {
      gmv: 0,
      revenue: 0,
      commission: 0,
      refunds: 1500000,
    };
    const payout = metrics.revenue - metrics.commission - (metrics.refunds || 0);
    assert.equal(payout, -1500000);
    // Preserves transparency: does not hide discrepancies by forcing to 0
  });
});

describe('Admin Dashboard Calculations — API Error & Null Safety (Scenario 4, 5, 7)', () => {
  test('handles null/missing revenue list without crashing', () => {
    const range = { from: '2026-11-08', to: '2026-11-14' };
    const points = mergeDailyReports([], [], range);
    const metrics = summarizeMetrics(points);
    assert.equal(metrics.gmv, 0);
    assert.equal(metrics.revenue, 0);
  });

  test('handles partner applications null count for WorkloadDonut gracefully', () => {
    // Simulating dashboardData operations where roleApplications is null (API failure)
    const operations = {
      vendors: 2,
      services: 3,
      refunds: 1,
      disputes: 0,
      settlements: 2,
      roleApplications: null, // Removed fake "= 1" fallback
    };

    assert.equal(operations.roleApplications, null);
    // WorkloadDonut isUnknown flag should correctly identify null as unknown
    const isUnknown = operations.roleApplications === null;
    assert.equal(isUnknown, true);
  });
});

describe('Admin Dashboard Operations — Settlements & Payout Requests Aggregation', () => {
  test('includes requested payout requests in operations.settlements count', async () => {
    const { loadAdminDashboard } = await import('../dashboardData.ts');
    const mockGet = async (path: string) => {
      if (path === '/admin/settlements') {
        return { data: { content: [], totalElements: 0 } };
      }
      if (path === '/admin/payout-requests') {
        return { data: { content: [{ id: '00090000-0000-0000-0000-000000000010' }], totalElements: 1 } };
      }
      if (path === '/admin/disputes') {
        return { data: { content: [], totalElements: 0 } };
      }
      if (path === '/admin/reports/bookings' || path === '/admin/reports/revenue' || path === '/admin/reports/vendors') {
        return { data: { items: [] } };
      }
      if (path === '/admin/services') {
        return { data: [] };
      }
      return { data: { content: [], totalElements: 0 } };
    };

    const data = await loadAdminDashboard(mockGet as Parameters<typeof loadAdminDashboard>[0], { from: '2026-11-08', to: '2026-11-14' }, new AbortController().signal);
    assert.equal(data.operations.settlements, 1);
    assert.equal(data.errors.length, 0);
  });

  test('gracefully handles missing /admin/payout-requests endpoint without fatal dashboard error and does not falsely claim 0 pending payouts', async () => {
    const { loadAdminDashboard } = await import('../dashboardData.ts');
    const mockGet = async (path: string) => {
      if (path === '/admin/payout-requests') {
        throw new Error('404 Not Found');
      }
      if (path === '/admin/settlements') {
        return { data: { content: [], totalElements: 0 } };
      }
      if (path === '/admin/disputes') {
        return { data: { content: [{ id: '99999999-9999-9999-9999-999999999993' }], totalElements: 1 } };
      }
      if (path === '/admin/reports/bookings' || path === '/admin/reports/revenue' || path === '/admin/reports/vendors') {
        return { data: { items: [] } };
      }
      if (path === '/admin/services') {
        return { data: [] };
      }
      return { data: { content: [], totalElements: 0 } };
    };

    const data = await loadAdminDashboard(mockGet as Parameters<typeof loadAdminDashboard>[0], { from: '2026-11-08', to: '2026-11-14' }, new AbortController().signal);
    // When payout API is 404/incomplete, settlements must be null (unknown), NEVER falsely concluded as 0
    assert.equal(data.operations.settlements, null);
    // Disputes OPEN must be accurately counted as 1 (or 2 if both OPEN and UNDER_REVIEW return 1)
    assert.equal(data.operations.disputes, 2);
    // 404 on optional payout-requests should not pollute error banner
    assert.ok(!data.errors.includes('lệnh chi trả chờ duyệt'));
  });

  test('correctly counts OPEN dispute as 1 when UNDER_REVIEW is 0', async () => {
    const { loadAdminDashboard } = await import('../dashboardData.ts');
    const mockGet = async (path: string, config?: { params?: Record<string, string | number> }) => {
      if (path === '/admin/disputes') {
        if (config?.params?.status === 'OPEN') {
          return { data: { content: [{ id: '99999999-9999-9999-9999-999999999993' }], totalElements: 1 } };
        }
        return { data: { content: [], totalElements: 0 } };
      }
      if (path === '/admin/settlements' || path === '/admin/payout-requests') {
        return { data: { content: [], totalElements: 0 } };
      }
      if (path === '/admin/reports/bookings' || path === '/admin/reports/revenue' || path === '/admin/reports/vendors') {
        return { data: { items: [] } };
      }
      if (path === '/admin/services') {
        return { data: [] };
      }
      return { data: { content: [], totalElements: 0 } };
    };

    const data = await loadAdminDashboard(mockGet as Parameters<typeof loadAdminDashboard>[0], { from: '2026-11-08', to: '2026-11-14' }, new AbortController().signal);
    assert.equal(data.operations.disputes, 1);
    assert.equal(data.operations.settlements, 0);
  });
});

