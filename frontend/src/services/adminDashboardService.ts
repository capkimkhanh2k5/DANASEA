import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminDashboardResponse,
  AdminDashboardQueryParams,
  ReportQueryParams,
  RevenueReportResponse,
  BookingReportResponse,
  VendorPerformanceResponse,
  DashboardOperationalCounts,
  DashboardActionItem,
  DashboardRecentOrder,
  DashboardOverviewData,
} from '../types/adminDashboard';
import type { AdminServiceResult } from '../types/adminServiceApproval';
import type { DisputeResponse } from '../types/adminDispute';
import type { AdminVendorResponse } from '../types/adminVendor';
import type { SettlementResponse } from '../types/adminSettlement';
import type { AdminOrderSummaryResponse, SpringPage } from '../types/adminOrder';

export const adminDashboardService = {
  // GET /api/admin/dashboard - Tổng quan chỉ số GMV, hoa hồng, tỷ lệ hoàn tất/hủy và cảnh báo bất thường
  async getDashboardMetrics(params?: AdminDashboardQueryParams): Promise<AdminDashboardResponse> {
    const queryParams: Record<string, string> = {};
    if (params?.from) queryParams.from = params.from;
    if (params?.to) queryParams.to = params.to;
    if (params?.vendorId) queryParams.vendorId = params.vendorId;

    const response = await apiClient.get<AdminDashboardResponse>('/admin/dashboard', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/services?status=PENDING_REVIEW - Danh sách dịch vụ chờ kiểm duyệt an toàn
  async getPendingServices(): Promise<AdminServiceResult[]> {
    const response = await apiClient.get<AdminServiceResult[]>('/admin/services', {
      params: { status: 'PENDING_REVIEW' },
    });
    return response.data || [];
  },

  // GET /api/admin/disputes?status=OPEN - Danh sách khiếu nại đang mở cần phân xử
  async getOpenDisputes(size = 10): Promise<SpringPage<DisputeResponse>> {
    const response = await apiClient.get<SpringPage<DisputeResponse>>('/admin/disputes', {
      params: { status: 'OPEN', page: 0, size, sort: 'createdAt,desc' },
    });
    return response.data;
  },

  // GET /api/admin/vendors?status=PENDING - Danh sách hồ sơ đối tác mới chờ duyệt
  async getPendingVendors(size = 10): Promise<SpringPage<AdminVendorResponse>> {
    const response = await apiClient.get<SpringPage<AdminVendorResponse>>('/admin/vendors', {
      params: { status: 'PENDING', page: 0, size, sort: 'createdAt,desc' },
    });
    return response.data;
  },

  // GET /api/admin/settlements?status=PENDING - Danh sách kỳ đối soát/lệnh chi chờ xử lý
  async getPendingSettlements(size = 10): Promise<SpringPage<SettlementResponse>> {
    const response = await apiClient.get<SpringPage<SettlementResponse>>('/admin/settlements', {
      params: { status: 'PENDING', page: 0, size, sort: 'createdAt,desc' },
    });
    return response.data;
  },

  // GET /api/admin/orders - 5 đơn đặt chỗ mới nhất cho Live Feed
  async getRecentOrders(limit = 5): Promise<SpringPage<AdminOrderSummaryResponse>> {
    const response = await apiClient.get<SpringPage<AdminOrderSummaryResponse>>('/admin/orders', {
      params: { page: 0, size: limit, sort: 'createdAt,desc' },
    });
    return response.data;
  },

  // GET /api/admin/reports/revenue - Báo cáo chuỗi thời gian doanh thu (GMV, thực thu, hoa hồng, hoàn tiền)
  async getRevenueReport(params: ReportQueryParams): Promise<RevenueReportResponse> {
    const queryParams: Record<string, string> = {
      from: params.from,
      to: params.to,
      groupBy: params.groupBy || 'day',
    };
    if (params.vendorId) queryParams.vendorId = params.vendorId;

    const response = await apiClient.get<RevenueReportResponse>('/admin/reports/revenue', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/reports/bookings - Báo cáo chuỗi thời gian đơn đặt theo trạng thái và lý do hủy
  async getBookingReport(params: ReportQueryParams): Promise<BookingReportResponse> {
    const queryParams: Record<string, string> = {
      from: params.from,
      to: params.to,
      groupBy: params.groupBy || 'day',
    };
    if (params.vendorId) queryParams.vendorId = params.vendorId;

    const response = await apiClient.get<BookingReportResponse>('/admin/reports/bookings', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/reports/vendors - Báo cáo hiệu suất hoạt động từng đối tác (doanh thu, tỷ lệ lấp đầy slot, vi phạm)
  async getVendorPerformance(params: { from: string; to: string; vendorId?: string }): Promise<VendorPerformanceResponse> {
    const queryParams: Record<string, string> = {
      from: params.from,
      to: params.to,
    };
    if (params.vendorId) queryParams.vendorId = params.vendorId;

    const response = await apiClient.get<VendorPerformanceResponse>('/admin/reports/vendors', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/reports/export - Tải file CSV báo cáo tài chính hoặc vận hành
  async exportReportCsv(params: {
    type: 'revenue' | 'booking' | 'vendor';
    from: string;
    to: string;
    groupBy?: string;
    vendorId?: string;
  }): Promise<Blob> {
    const queryParams: Record<string, string> = {
      type: params.type,
      from: params.from,
      to: params.to,
      groupBy: params.groupBy || 'day',
    };
    if (params.vendorId) queryParams.vendorId = params.vendorId;

    const response = await apiClient.get<Blob>('/admin/reports/export', {
      params: queryParams,
      responseType: 'blob',
    });
    return response.data;
  },

  // Aggregated Overview Call: Song song hóa các endpoint vận hành với Promise.allSettled để chống single-point-of-failure
  async getDashboardOverview(params?: AdminDashboardQueryParams): Promise<DashboardOverviewData> {
    const [
      metricsResult,
      servicesResult,
      disputesResult,
      vendorsResult,
      settlementsResult,
      ordersResult,
    ] = await Promise.allSettled([
      this.getDashboardMetrics(params),
      this.getPendingServices(),
      this.getOpenDisputes(10),
      this.getPendingVendors(10),
      this.getPendingSettlements(10),
      this.getRecentOrders(5),
    ]);

    // 1. Metrics tổng quan
    const metrics: AdminDashboardResponse | null =
      metricsResult.status === 'fulfilled' ? metricsResult.value : null;

    // 2. Danh sách dịch vụ chờ duyệt
    const pendingServices: AdminServiceResult[] =
      servicesResult.status === 'fulfilled' ? servicesResult.value : [];

    // 3. Khiếu nại đang mở
    const openDisputesPage =
      disputesResult.status === 'fulfilled' ? disputesResult.value : null;
    const openDisputes: DisputeResponse[] = openDisputesPage?.content || [];

    // 4. Hồ sơ đối tác chờ duyệt
    const pendingVendorsPage =
      vendorsResult.status === 'fulfilled' ? vendorsResult.value : null;
    const pendingVendors: AdminVendorResponse[] = pendingVendorsPage?.content || [];

    // 5. Lệnh chi/đối soát chờ duyệt
    const pendingSettlementsPage =
      settlementsResult.status === 'fulfilled' ? settlementsResult.value : null;
    const pendingSettlements: SettlementResponse[] = pendingSettlementsPage?.content || [];

    // 6. Đơn gần đây
    const recentOrdersPage =
      ordersResult.status === 'fulfilled' ? ordersResult.value : null;
    const rawOrders: AdminOrderSummaryResponse[] = recentOrdersPage?.content || [];

    // Tính toán số lượng tồn đọng chính xác từ metadata phân trang của database
    const pendingServicesCount = pendingServices.length;
    const openDisputesCount = openDisputesPage?.totalElements ?? openDisputes.length;
    const pendingVendorsCount = pendingVendorsPage?.totalElements ?? pendingVendors.length;
    const pendingPayoutsCount = pendingSettlementsPage?.totalElements ?? pendingSettlements.length;

    const counts: DashboardOperationalCounts = {
      pendingServicesCount,
      openDisputesCount,
      pendingVendorsCount,
      pendingPayoutsCount,
      totalUrgentCount: pendingServicesCount + openDisputesCount,
      totalPendingCount: pendingServicesCount + openDisputesCount + pendingVendorsCount + pendingPayoutsCount,
    };

    // Chuẩn hóa danh sách hành động (Priority Action Items)
    const actionItems: DashboardActionItem[] = [
      ...pendingServices.map(s => ({
        id: s.id,
        type: 'services' as const,
        title: s.name,
        subtitle: `Địa điểm: ${s.locationName || 'Đà Nẵng'} • Giá: ${(s.price || 0).toLocaleString('vi-VN')} đ`,
        tag: 'Chờ duyệt',
        tagVariant: 'info' as const,
        link: '/admin/service-approval',
        actionLabel: 'Thẩm định',
        timestamp: s.createdAt,
        raw: s,
      })),
      ...openDisputes.map(d => ({
        id: d.id,
        type: 'disputes' as const,
        title: `Khiếu nại #${d.id.slice(0, 8)}`,
        subtitle: `Lý do: ${d.reason || 'Tranh chấp dịch vụ'} • Khách: ${d.customerId?.slice(0, 8) || 'N/A'}`,
        tag: d.status || 'OPEN',
        tagVariant: 'danger' as const,
        link: '/admin/disputes',
        actionLabel: 'Xử lý',
        timestamp: d.createdAt,
        raw: d,
      })),
      ...pendingVendors.map(v => ({
        id: v.id,
        type: 'vendors' as const,
        title: v.businessName || 'Đối tác mới',
        subtitle: `MST: ${v.taxCode || 'Chưa cập nhật'} • ${v.address || ''}`,
        tag: 'Chờ duyệt',
        tagVariant: 'warning' as const,
        link: '/admin/vendor-approval',
        actionLabel: 'Hồ sơ',
        timestamp: v.createdAt,
        raw: v,
      })),
      ...pendingSettlements.map(p => ({
        id: p.id,
        type: 'payouts' as const,
        title: `Kỳ đối soát ${p.periodStart} - ${p.periodEnd}`,
        subtitle: `Số tiền: ${(p.netPayableAmount || 0).toLocaleString('vi-VN')} đ`,
        tag: p.status || 'PENDING',
        tagVariant: 'success' as const,
        link: '/admin/payouts',
        actionLabel: 'Duyệt chi',
        timestamp: p.createdAt,
        raw: p,
      })),
    ];

    // Chuẩn hóa 5 đơn gần nhất
    const recentOrders: DashboardRecentOrder[] = rawOrders.slice(0, 5).map(o => ({
      id: o.id,
      orderNumber: o.id.slice(0, 8).toUpperCase(),
      customerId: o.customerId ? o.customerId.slice(0, 8) : 'Khách vãng lai',
      status: o.status,
      paymentStatus: o.paymentStatus,
      totalAmount: o.totalAmount || 0,
      discountAmount: o.discountAmount || 0,
      createdAt: o.createdAt,
    }));

    return {
      metrics,
      counts,
      actionItems,
      recentOrders,
      lastUpdated: new Date(),
    };
  },
};

export function getAdminDashboardError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi tải dữ liệu bảng điều khiển'): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED' || error.message.toLowerCase().includes('timeout') || (!error.response && error.request)) {
      return 'Không thể kết nối đến máy chủ do quá thời gian chờ (Timeout). Vui lòng nhấn "Làm mới" để tải lại dữ liệu.';
    }

    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('ADMIN_ACCESS_DENIED') || status === 403) {
        return 'Tài khoản không có quyền truy cập dữ liệu quản trị (Yêu cầu vai trò ADMIN).';
      }
      if (status === 401) {
        return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
      }
      return msg;
    }

    if (status === 500) {
      return 'Lỗi máy chủ nội bộ trong quá trình tổng hợp báo cáo. Vui lòng thử lại sau.';
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallbackMessage;
}
