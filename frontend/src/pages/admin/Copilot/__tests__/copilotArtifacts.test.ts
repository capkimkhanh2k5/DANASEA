import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  formatVND,
  formatCompactVND,
  formatPercent,
  parseArtifactsJson,
  extractRevenueChartData,
  extractVendorDiagnosticsData,
  extractCancellationBreakdownData,
  extractPlatformMetricsData,
  extractQueueOverviewData,
} from '../copilotArtifactHelpers';

describe('Copilot Artifact Helpers & Safety Handlers', () => {
  describe('formatVND & formatCompactVND', () => {
    it('formats numbers into Vietnamese Dong safely', () => {
      assert.ok(formatVND(1500000).includes('1.500.000'));
      assert.ok(formatVND(0).includes('0'));
      assert.equal(formatVND(null), '0 ₫');
      assert.equal(formatVND(undefined), '0 ₫');
      assert.equal(formatVND(NaN), '0 ₫');
    });

    it('formats compact values for chart axes', () => {
      assert.equal(formatCompactVND(15_000_000), '15 tr');
      assert.equal(formatCompactVND(500_000), '500 k');
      assert.equal(formatCompactVND(0), '0 ₫');
    });
  });

  describe('formatPercent', () => {
    it('handles decimal ratios and whole percentages correctly', () => {
      assert.equal(formatPercent(0.125), '12.5%');
      assert.equal(formatPercent(15), '15.0%');
      assert.equal(formatPercent(0), '0%');
      assert.equal(formatPercent(null), '0%');
      assert.equal(formatPercent(undefined), '0%');
    });
  });

  describe('parseArtifactsJson', () => {
    it('returns undefined for null, empty or invalid input', () => {
      assert.equal(parseArtifactsJson(null), undefined);
      assert.equal(parseArtifactsJson(undefined), undefined);
      assert.equal(parseArtifactsJson(''), undefined);
      assert.equal(parseArtifactsJson('[]'), undefined);
      assert.equal(parseArtifactsJson('{ invalid json }'), undefined);
    });

    it('parses valid JSON string array of artifacts', () => {
      const raw = JSON.stringify([
        {
          tool: 'admin_revenue_timeseries',
          type: 'ADMIN_REVENUE_CHART_CARD',
          data: [{ period: '2026-10-15', gmv: 5000000 }],
        },
      ]);
      const parsed = parseArtifactsJson(raw);
      assert.ok(parsed);
      assert.equal(parsed.length, 1);
      assert.equal(parsed[0].tool, 'admin_revenue_timeseries');
    });

    it('accepts already parsed array of artifacts', () => {
      const arr = [
        {
          tool: 'admin_platform_dashboard',
          type: 'ADMIN_KPI_SUMMARY_CARD',
          data: { totalGmv: 1000000 },
        },
      ];
      const res = parseArtifactsJson(arr);
      assert.ok(res);
      assert.equal(res.length, 1);
    });
  });

  describe('extractRevenueChartData', () => {
    it('extracts real time-series items and ignores invalid points', () => {
      const rawData = [
        {
          period: '2026-10-10',
          gmv: 12000000,
          collectedCash: 12000000,
          refunds: 0,
          commission: 1200000,
          netVendorPayout: 10800000,
        },
        null,
        { period: '2026-10-11', gmv: '8000000', commission: 800000 },
      ];

      const extracted = extractRevenueChartData(rawData);
      assert.ok(extracted);
      assert.equal(extracted.length, 2);
      assert.equal(extracted[0].period, '2026-10-10');
      assert.equal(extracted[0].gmv, 12000000);
      assert.equal(extracted[1].period, '2026-10-11');
      assert.equal(extracted[1].gmv, 8000000);
    });

    it('returns null if data is not an array', () => {
      assert.equal(extractRevenueChartData(null), null);
      assert.equal(extractRevenueChartData({}), null);
    });
  });

  describe('extractVendorDiagnosticsData', () => {
    it('safely normalizes vendor items and severity', () => {
      const raw = [
        {
          vendorId: 'v-123',
          businessName: 'Cty Tour Son Tra',
          gmv: 45000000,
          totalBookings: 32,
          cancellationRate: 0.25,
          slotOccupancyRate: 0.78,
          ratingAvg: 3.8,
          ratingCount: 15,
          severity: 'critical',
          issueTypes: ['Tỷ lệ hủy cao', 'Đánh giá giảm'],
        },
      ];
      const res = extractVendorDiagnosticsData(raw);
      assert.ok(res);
      assert.equal(res.length, 1);
      assert.equal(res[0].businessName, 'Cty Tour Son Tra');
      assert.equal(res[0].severity, 'CRITICAL');
      assert.equal(res[0].cancellationRate, 0.25);
    });

    it('returns null for invalid data', () => {
      assert.equal(extractVendorDiagnosticsData('not an array'), null);
    });
  });

  describe('extractCancellationBreakdownData', () => {
    it('extracts breakdown counts without inventing numbers', () => {
      const raw = {
        totalBookings: 150,
        completedBookings: 120,
        totalCancelled: 30,
        weatherCancellations: 18,
        vendorFaultCancellations: 6,
        customerCancellations: 4,
        adminOverrideCancellations: 1,
        disputeCancellations: 1,
      };
      const res = extractCancellationBreakdownData(raw);
      assert.ok(res);
      assert.equal(res.totalBookings, 150);
      assert.equal(res.totalCancelled, 30);
      assert.equal(res.weatherCancellations, 18);
    });

    it('returns null when given empty object or non-object', () => {
      assert.equal(extractCancellationBreakdownData(null), null);
      assert.equal(extractCancellationBreakdownData({ foo: 'bar' }), null);
    });
  });

  describe('extractPlatformMetricsData & QueueOverview', () => {
    it('extracts platform metrics accurately', () => {
      const raw = {
        totalGmv: 150000000,
        netPayout: 135000000,
        platformCommission: 15000000,
        totalBookings: 420,
        activeServices: 28,
        activeVendors: 14,
        criticalVendorsCount: 2,
        operationalAlerts: ['2 vendor có tỷ lệ hủy trên 20%'],
      };
      const res = extractPlatformMetricsData(raw);
      assert.ok(res);
      assert.equal(res.totalGmv, 150000000);
      assert.equal(res.criticalVendorsCount, 2);
      assert.equal(res.operationalAlerts.length, 1);
    });

    it('extracts queue overview accurately', () => {
      const raw = {
        pendingRiskCasesCount: 3,
        pendingSupportRequestsCount: 7,
        openDisputesCount: 2,
      };
      const res = extractQueueOverviewData(raw);
      assert.ok(res);
      assert.equal(res.pendingRiskCasesCount, 3);
      assert.equal(res.openDisputesCount, 2);
    });
  });

  describe('Edge cases and missing fields safety', () => {
    it('handles partially populated revenue items without NaN or crash', () => {
      const partial = [
        { period: '2026-11-01' }, // all financial fields omitted
        { period: '2026-11-02', gmv: 'invalid_number' },
      ];
      const res = extractRevenueChartData(partial);
      assert.ok(res);
      assert.equal(res.length, 2);
      assert.equal(res[0].gmv, 0);
      assert.equal(res[0].commission, 0);
      assert.equal(res[1].gmv, 0);
    });

    it('handles non-array JSON string safely in parseArtifactsJson', () => {
      assert.equal(parseArtifactsJson('{"not":"an_array"}'), undefined);
      assert.equal(parseArtifactsJson('   '), undefined);
      assert.equal(parseArtifactsJson('true'), undefined);
      assert.equal(parseArtifactsJson('12345'), undefined);
    });

    it('safely handles vendor items with undefined metrics', () => {
      const partialVendor = [{ vendorId: 'v-test' }];
      const res = extractVendorDiagnosticsData(partialVendor);
      assert.ok(res);
      assert.equal(res.length, 1);
      assert.equal(res[0].businessName, 'Đối tác chưa đặt tên');
      assert.equal(res[0].severity, 'HEALTHY');
      assert.equal(res[0].gmv, 0);
      assert.equal(res[0].cancellationRate, 0);
    });
  });
});
