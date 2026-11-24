import { apiClient } from './apiClient';

export interface ReportAlertDto {
  level: string;
  message: string;
  count: number;
}

export interface VendorDashboardResponse {
  totalRevenue: number;
  netPayout: number;
  newOrders: number;
  completedOrders: number;
  completionRate: number;
  cancellationRate: number;
  alerts: ReportAlertDto[];
}

export interface RevenueReportItemDto {
  periodKey: string;
  gmv: number;
  discountAmount: number;
  collectedCash: number;
  refunds: number;
  platformCommission: number;
  netVendorPayout: number;
  orderCount: number;
}

export interface RevenueReportResponseDto {
  items: RevenueReportItemDto[];
  timeRange: { from: string; to: string };
}

export interface BookingReportItemDto {
  periodKey: string;
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  cancellationReasonBreakdown: Record<string, number>;
}

export interface BookingReportResponseDto {
  items: BookingReportItemDto[];
  timeRange: { from: string; to: string };
}

export const vendorReportService = {
  /**
   * GET /api/vendor/dashboard?from=&to=
   */
  async getVendorDashboard(from?: string, to?: string): Promise<VendorDashboardResponse> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get<VendorDashboardResponse>('/vendor/dashboard', { params });
    return response.data;
  },

  /**
   * GET /api/vendor/reports/revenue
   */
  async getRevenueReport(from: string, to: string, groupBy = 'day'): Promise<RevenueReportResponseDto> {
    const response = await apiClient.get<RevenueReportResponseDto>('/vendor/reports/revenue', {
      params: { from, to, groupBy },
    });
    return response.data;
  },

  /**
   * GET /api/vendor/reports/bookings
   */
  async getBookingReport(from: string, to: string, groupBy = 'day'): Promise<BookingReportResponseDto> {
    const response = await apiClient.get<BookingReportResponseDto>('/vendor/reports/bookings', {
      params: { from, to, groupBy },
    });
    return response.data;
  },

  /**
   * GET /api/vendor/reports/export
   * Tải xuống file CSV/Excel báo cáo đối soát doanh thu hoặc đơn hàng
   */
  async exportReport(type: 'revenue' | 'bookings', from: string, to: string, groupBy = 'day'): Promise<Blob> {
    const response = await apiClient.get('/vendor/reports/export', {
      params: { type, from, to, groupBy },
      responseType: 'blob',
    });
    return response.data;
  },
};
