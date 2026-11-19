import type {
  CopilotArtifact,
  RevenueTimeSeriesItem,
  BookingCancellationBreakdown,
  VendorDiagnosticItem,
  PlatformDashboardMetrics,
  QueueOverview,
  DisputeArbitrationResult,
} from '../../../types/adminAi';

/**
 * Format currency to Vietnamese Dong (₫).
 * Preserves exact numerical precision without inventing values.
 */
export function formatVND(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '0 ₫';
  const num = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(num)) return '0 ₫';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(num);
}

/**
 * Format compact currency for chart axes (e.g. 15 tr, 500 k, 0 ₫).
 */
export function formatCompactVND(value: number): string {
  if (!Number.isFinite(value) || value === 0) return '0 ₫';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(1).replace(/\.0$/, '')} tỷ`;
  }
  if (abs >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, '')} tr`;
  }
  if (abs >= 1_000) {
    return `${(value / 1_000).toFixed(0)} k`;
  }
  return `${value} ₫`;
}

/**
 * Format integer numbers with Vietnamese thousand separators.
 */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '0';
  return new Intl.NumberFormat('vi-VN').format(value);
}

/**
 * Format rates/percentages (supports both 0.155 and 15.5 input formats).
 */
export function formatPercent(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || !Number.isFinite(rate)) return '0%';
  if (rate === 0) return '0%';
  if (rate <= 1 && rate >= -1) {
    return `${(rate * 100).toFixed(1)}%`;
  }
  return `${rate.toFixed(1)}%`;
}

/**
 * Safely parse artifactsJson from backend message history or live chat response.
 * Returns undefined if malformed, empty or absent. Never throws.
 */
export function parseArtifactsJson(input: unknown): CopilotArtifact[] | undefined {
  if (!input) return undefined;

  if (Array.isArray(input)) {
    const valid = input.filter(
      (item) => item && typeof item === 'object' && 'type' in item
    ) as CopilotArtifact[];
    return valid.length > 0 ? valid : undefined;
  }

  if (typeof input === 'string') {
    const trimmed = input.trim();
    if (!trimmed || trimmed === 'null' || trimmed === '[]') return undefined;
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        const valid = parsed.filter(
          (item) => item && typeof item === 'object' && 'type' in item
        ) as CopilotArtifact[];
        return valid.length > 0 ? valid : undefined;
      }
    } catch {
      return undefined;
    }
  }

  return undefined;
}

/**
 * Extract and validate RevenueTimeSeriesItem list from artifact data.
 */
export function extractRevenueChartData(data: unknown): RevenueTimeSeriesItem[] | null {
  if (!Array.isArray(data)) return null;
  const items: RevenueTimeSeriesItem[] = [];
  for (const item of data) {
    if (!item || typeof item !== 'object') continue;
    const period = String(item.period ?? '');
    if (!period) continue;
    items.push({
      period,
      gmv: Number(item.gmv ?? 0) || 0,
      collectedCash: Number(item.collectedCash ?? 0) || 0,
      refunds: Number(item.refunds ?? 0) || 0,
      commission: Number(item.commission ?? 0) || 0,
      netVendorPayout: Number(item.netVendorPayout ?? 0) || 0,
    });
  }
  return items;
}

/**
 * Extract and validate VendorDiagnosticItem list from artifact data.
 */
export function extractVendorDiagnosticsData(data: unknown): VendorDiagnosticItem[] | null {
  if (!Array.isArray(data)) return null;
  const items: VendorDiagnosticItem[] = [];
  for (const item of data) {
    if (!item || typeof item !== 'object') continue;
    items.push({
      vendorId: String(item.vendorId ?? ''),
      businessName: String(item.businessName || 'Đối tác chưa đặt tên'),
      gmv: Number(item.gmv ?? 0) || 0,
      totalBookings: Number(item.totalBookings ?? 0) || 0,
      cancellationRate: Number(item.cancellationRate ?? 0) || 0,
      slotOccupancyRate: Number(item.slotOccupancyRate ?? 0) || 0,
      ratingAvg: Number(item.ratingAvg ?? 0) || 0,
      ratingCount: Number(item.ratingCount ?? 0) || 0,
      severity: String(item.severity || 'HEALTHY').toUpperCase(),
      issueTypes: Array.isArray(item.issueTypes) ? item.issueTypes.map(String) : [],
    });
  }
  return items;
}

/**
 * Extract and validate BookingCancellationBreakdown from artifact data.
 */
export function extractCancellationBreakdownData(data: unknown): BookingCancellationBreakdown | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (
    obj.totalBookings === undefined &&
    obj.totalCancelled === undefined &&
    obj.weatherCancellations === undefined
  ) {
    return null;
  }
  return {
    totalBookings: Number(obj.totalBookings ?? 0) || 0,
    completedBookings: Number(obj.completedBookings ?? 0) || 0,
    totalCancelled: Number(obj.totalCancelled ?? 0) || 0,
    weatherCancellations: Number(obj.weatherCancellations ?? 0) || 0,
    vendorFaultCancellations: Number(obj.vendorFaultCancellations ?? 0) || 0,
    customerCancellations: Number(obj.customerCancellations ?? 0) || 0,
    adminOverrideCancellations: Number(obj.adminOverrideCancellations ?? 0) || 0,
    disputeCancellations: Number(obj.disputeCancellations ?? 0) || 0,
    cancellationsByReason:
      obj.cancellationsByReason && typeof obj.cancellationsByReason === 'object'
        ? (obj.cancellationsByReason as Record<string, number>)
        : undefined,
  };
}

/**
 * Extract PlatformDashboardMetrics from artifact data.
 */
export function extractPlatformMetricsData(data: unknown): PlatformDashboardMetrics | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (obj.totalGmv === undefined && obj.totalBookings === undefined) return null;
  return {
    totalGmv: Number(obj.totalGmv ?? 0) || 0,
    netPayout: Number(obj.netPayout ?? 0) || 0,
    platformCommission: Number(obj.platformCommission ?? 0) || 0,
    totalBookings: Number(obj.totalBookings ?? 0) || 0,
    activeServices: Number(obj.activeServices ?? 0) || 0,
    activeVendors: Number(obj.activeVendors ?? 0) || 0,
    criticalVendorsCount: Number(obj.criticalVendorsCount ?? 0) || 0,
    operationalAlerts: Array.isArray(obj.operationalAlerts)
      ? obj.operationalAlerts.map(String)
      : [],
  };
}

/**
 * Extract QueueOverview from artifact data.
 */
export function extractQueueOverviewData(data: unknown): QueueOverview | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (
    obj.pendingRiskCasesCount === undefined &&
    obj.pendingSupportRequestsCount === undefined &&
    obj.openDisputesCount === undefined
  ) {
    return null;
  }
  return {
    pendingRiskCasesCount: Number(obj.pendingRiskCasesCount ?? 0) || 0,
    pendingSupportRequestsCount: Number(obj.pendingSupportRequestsCount ?? 0) || 0,
    openDisputesCount: Number(obj.openDisputesCount ?? 0) || 0,
  };
}

/**
 * Extract DisputeArbitrationResult from artifact data.
 */
export function extractDisputeBriefData(data: unknown): DisputeArbitrationResult | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (!obj.disputeId) return null;
  return {
    disputeId: String(obj.disputeId),
    subOrderId: obj.subOrderId ? String(obj.subOrderId) : undefined,
    orderId: obj.orderId ? String(obj.orderId) : undefined,
    customerName: obj.customerName ? String(obj.customerName) : undefined,
    vendorBusinessName: obj.vendorBusinessName ? String(obj.vendorBusinessName) : undefined,
    serviceTitle: obj.serviceTitle ? String(obj.serviceTitle) : undefined,
    disputeReason: obj.disputeReason ? String(obj.disputeReason) : undefined,
    timelineSummary: obj.timelineSummary ? String(obj.timelineSummary) : undefined,
    customerClaimSummary: obj.customerClaimSummary ? String(obj.customerClaimSummary) : undefined,
    vendorDefenseSummary: obj.vendorDefenseSummary ? String(obj.vendorDefenseSummary) : undefined,
    objectiveSystemFacts: Array.isArray(obj.objectiveSystemFacts)
      ? obj.objectiveSystemFacts.map(String)
      : [],
    recommendedResolution: obj.recommendedResolution
      ? String(obj.recommendedResolution)
      : undefined,
    recommendedRefundPercent:
      obj.recommendedRefundPercent !== undefined && obj.recommendedRefundPercent !== null
        ? Number(obj.recommendedRefundPercent)
        : null,
    recommendationReason: obj.recommendationReason
      ? String(obj.recommendationReason)
      : undefined,
    draftAdminNote: obj.draftAdminNote ? String(obj.draftAdminNote) : undefined,
  };
}

export interface OrderRiskAssessmentViewData {
  orderId: string;
  caseId: string | null;
  status: string;
  ruleVersion: string;
  riskSignals: string[];
  limitations: string[];
  fraudConfirmed: boolean;
  newCaseCreated: boolean;
  observed: Record<string, unknown> | null;
  quyetDecision: Record<string, unknown> | null;
}

export function extractOrderRiskAssessmentData(data: unknown): OrderRiskAssessmentViewData | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (!obj.orderId && !obj.caseId && !obj.riskSignals && !obj.observed) return null;
  return {
    orderId: obj.orderId ? String(obj.orderId) : '—',
    caseId: obj.caseId ? String(obj.caseId) : null,
    status: obj.status ? String(obj.status) : 'NEEDS_REVIEW',
    ruleVersion: obj.ruleVersion ? String(obj.ruleVersion) : 'velocity-demo-v1',
    riskSignals: Array.isArray(obj.riskSignals) ? obj.riskSignals.map(String) : [],
    limitations: Array.isArray(obj.limitations) ? obj.limitations.map(String) : [],
    fraudConfirmed: Boolean(obj.fraudConfirmed),
    newCaseCreated: Boolean(obj.newCaseCreated),
    observed: obj.observed && typeof obj.observed === 'object' ? (obj.observed as Record<string, unknown>) : null,
    quyetDecision:
      obj.quyetDecision && typeof obj.quyetDecision === 'object'
        ? (obj.quyetDecision as Record<string, unknown>)
        : null,
  };
}

export interface ContentAssessmentViewData {
  status: string;
  reasonCodes: string[];
  contactMatches: string[];
  publicationAuthorized: boolean;
  evidenceType: string;
  negatedWarningOnly: boolean;
  persisted: boolean;
  serviceId: string | null;
  caseId: string | null;
  suggestedCategory: string | null;
  assessedTextPreview: string | null;
}

export function extractContentAssessmentData(data: unknown): ContentAssessmentViewData | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (!obj.status && !obj.reasonCodes && !obj.evidenceType) return null;
  return {
    status: obj.status ? String(obj.status) : 'NEEDS_REVIEW',
    reasonCodes: Array.isArray(obj.reasonCodes) ? obj.reasonCodes.map(String) : [],
    contactMatches: Array.isArray(obj.contactMatches) ? obj.contactMatches.map(String) : [],
    publicationAuthorized: Boolean(obj.publicationAuthorized),
    evidenceType: obj.evidenceType ? String(obj.evidenceType) : 'TEXT_ONLY',
    negatedWarningOnly: Boolean(obj.negatedWarningOnly),
    persisted: Boolean(obj.persisted),
    serviceId: obj.serviceId ? String(obj.serviceId) : null,
    caseId: obj.caseId ? String(obj.caseId) : null,
    suggestedCategory: obj.suggestedCategory ? String(obj.suggestedCategory) : null,
    assessedTextPreview: obj.assessedTextPreview ? String(obj.assessedTextPreview) : null,
  };
}
