import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminOrderSummaryResponse,
  OrderResponse,
  PaymentResponse,
  RefundDetailResponse,
  AdminOrderQueryParams,
  SpringPage,
} from '../types/adminOrder';

export const adminOrderService = {
  // GET /api/admin/orders - Danh sách đơn hàng Master kèm bộ lọc trạng thái và phân trang
  async getOrders(params?: AdminOrderQueryParams): Promise<SpringPage<AdminOrderSummaryResponse>> {
    const queryParams: Record<string, string | number> = {};
    if (params?.status) queryParams.status = params.status;
    if (params?.paymentStatus) queryParams.paymentStatus = params.paymentStatus;
    if (params?.vendorId) queryParams.vendorId = params.vendorId;
    if (params?.customerId) queryParams.customerId = params.customerId;
    if (params?.fromDate) queryParams.fromDate = params.fromDate;
    if (params?.toDate) queryParams.toDate = params.toDate;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    if (params?.sort) queryParams.sort = params.sort;

    const response = await apiClient.get<SpringPage<AdminOrderSummaryResponse>>('/admin/orders', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/orders/{id} - Chi tiết đơn hàng Master kèm danh sách dịch vụ con (SubOrders)
  async getOrder(id: string): Promise<OrderResponse> {
    const response = await apiClient.get<OrderResponse>(`/orders/${id}`);
    return response.data;
  },

  // GET /api/admin/payments - Danh sách giao dịch thanh toán kèm bộ lọc mã đơn
  async getPayments(orderId?: string): Promise<SpringPage<PaymentResponse>> {
    const params = orderId ? { orderId, size: 50 } : { size: 50 };
    const response = await apiClient.get<SpringPage<PaymentResponse>>('/admin/payments', { params });
    return response.data;
  },

  // GET /api/admin/refunds - Danh sách các lệnh hoàn tiền hệ thống
  async getRefunds(orderId?: string): Promise<SpringPage<RefundDetailResponse>> {
    const params = orderId ? { orderId, size: 50 } : { size: 50 };
    const response = await apiClient.get<SpringPage<RefundDetailResponse>>('/admin/refunds', { params });
    return response.data;
  },
};

export function getAdminOrderError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý đơn hàng'): string {
  if (axios.isAxiosError(error)) {
    // Phân biệt lỗi timeout hoặc mất kết nối mạng (kết quả chưa xác định)
    if (error.code === 'ECONNABORTED' || error.message.toLowerCase().includes('timeout') || (!error.response && error.request)) {
      return 'Yêu cầu chưa xác định kết quả do quá thời gian chờ (Timeout) hoặc mất kết nối mạng. Vui lòng nhấn "Làm mới" để tải lại dữ liệu từ máy chủ.';
    }

    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('ORDER_NOT_FOUND') || msg.includes('Order not found')) {
        return 'Không tìm thấy thông tin đơn hàng trên hệ thống.';
      }
      if (msg.includes('ORDER_NOT_PAID')) {
        return 'Đơn hàng chưa thanh toán thành công.';
      }
      if (msg.includes('INVALID_ORDER_STATE')) {
        return 'Trạng thái đơn hàng không hợp lệ cho thao tác này.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền ADMIN).';
    if (status === 404) return 'Không tìm thấy dữ liệu yêu cầu (404).';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
