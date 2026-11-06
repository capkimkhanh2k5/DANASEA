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
};
