// DANASEA Admin Dashboard Type Definitions
// Mapping directly to Backend DTOs & Domain Models:
// - com.danasea.backend.modules.report.presentation.dtos.AdminDashboardResponse
// - com.danasea.backend.modules.report.presentation.dtos.ReportAlertDto
// - com.danasea.backend.modules.report.domain.models.RevenueReportItem
// - com.danasea.backend.modules.report.domain.models.BookingReportItem
// - com.danasea.backend.modules.report.domain.models.VendorPerformanceItem

import type { AdminServiceResult } from './adminServiceApproval';
import type { DisputeResponse } from './adminDispute';
import type { AdminVendorResponse } from './adminVendor';
import type { SettlementResponse } from './adminSettlement';
import type { AdminOrderSummaryResponse } from './adminOrder';

// --- Backend Contract Types ---

export type AlertIssueType = 'HIGH_CANCELLATION' | 'OVERLOADED' | string;
export type AlertSeverity = 'HEALTHY' | 'INFO' | 'WARNING' | 'CRITICAL' | string;

export interface ReportAlertDto {
  vendorId?: string | null;
  businessName?: string | null;
  issueType: AlertIssueType;
  severity: AlertSeverity;
  message: string;
  metricValue: number;
}

export interface AdminDashboardResponse {
  status: string;
  totalRevenue: number;     // Gross Merchandise Value (GMV) across all bookings
  netPayout: number;        // Platform Commission / Sàn thực thu
  newOrders: number;        // Total bookings in the time period
  completedOrders: number;  // Completed bookings
  completionRate: number;   // Completed rate (%)
  cancellationRate: number; // Cancellation rate (%)
  alerts: ReportAlertDto[]; // Real-time anomaly alerts
}

export interface RevenueReportItem {
  periodKey: string;
  gmv: number;
  discountAmount: number;
  collectedCash: number;
  refunds: number;
  platformCommission: number;
  netVendorPayout: number;
  orderCount: number;
}

export interface RevenueReportResponse {
  items: RevenueReportItem[];
  timeRange: {
    from: string;
    to: string;
  };
}

export interface BookingReportItem {
  periodKey: string;
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  cancellationReasonBreakdown: Record<string, number>;
}

export interface BookingReportResponse {
  items: BookingReportItem[];
  timeRange: {
    from: string;
    to: string;
  };
}

export interface VendorPerformanceItem {
  vendorId: string;
  businessName: string;
  revenue: number;
  netPayout: number;
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  cancellationRate: number;
  slotOccupancyRate: number;
  rating: number;
  ratingCount: number;
  severity: AlertSeverity;
  issues: AlertIssueType[];
}

export interface VendorPerformanceResponse {
  items: VendorPerformanceItem[];
  timeRange: {
    from: string;
    to: string;
  };
}

// --- Query Parameters ---

export interface AdminDashboardQueryParams {
  from?: string; // YYYY-MM-DD
  to?: string;   // YYYY-MM-DD
  vendorId?: string;
}

export interface ReportQueryParams {
  from: string; // YYYY-MM-DD
  to: string;   // YYYY-MM-DD
  groupBy?: 'day' | 'month';
  vendorId?: string;
}

// --- Normalized Models for UI Agent Coordination ---

export interface DashboardOperationalCounts {
  pendingServicesCount: number;
  openDisputesCount: number;
  pendingVendorsCount: number;
  pendingPayoutsCount: number;
  totalUrgentCount: number;  // pendingServicesCount + openDisputesCount
  totalPendingCount: number; // pendingServicesCount + openDisputesCount + pendingVendorsCount + pendingPayoutsCount
}

export type DashboardActionType = 'services' | 'disputes' | 'vendors' | 'payouts';

export interface DashboardActionItem {
  id: string;
  type: DashboardActionType;
  title: string;
  subtitle: string;
  tag: string;
  tagVariant: 'warning' | 'danger' | 'info' | 'neutral' | 'success';
  link: string;
  actionLabel: string;
  timestamp?: string;
  raw: AdminServiceResult | DisputeResponse | AdminVendorResponse | SettlementResponse;
}

export interface DashboardRecentOrder {
  id: string;
  orderNumber: string;
  customerId: string;
  status: string;
  paymentStatus: string;
  totalAmount: number;
  discountAmount: number;
  createdAt: string;
  raw?: AdminOrderSummaryResponse;
}

export interface DashboardOverviewData {
  metrics: AdminDashboardResponse | null;
  counts: DashboardOperationalCounts;
  actionItems: DashboardActionItem[];
  recentOrders: DashboardRecentOrder[];
  lastUpdated: Date;
}
