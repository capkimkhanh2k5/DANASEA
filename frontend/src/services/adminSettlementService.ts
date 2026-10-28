import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  SettlementResponse,
  SettlementDetailResponse,
  GenerateSettlementRequest,
  AdminSettlementQueryParams,
} from '../types/adminSettlement';
import type { SpringPage } from '../types/adminOrder';

export const adminSettlementService = {
  // GET /api/admin/settlements - Danh sách kỳ đối soát và quyết toán tài chính
  async getSettlements(params?: AdminSettlementQueryParams): Promise<SpringPage<SettlementResponse>> {
    const queryParams: Record<string, string | number> = {};
    if (params?.vendorId) queryParams.vendorId = params.vendorId;
    if (params?.status) queryParams.status = params.status;
    if (params?.from) queryParams.from = params.from;
    if (params?.to) queryParams.to = params.to;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    if (params?.sort) queryParams.sort = params.sort;

    const response = await apiClient.get<SpringPage<SettlementResponse>>('/admin/settlements', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/settlements/{id} - Chi tiết kỳ đối soát kèm danh sách mục thanh toán (lineItems)
  async getSettlementDetail(id: string): Promise<SettlementDetailResponse> {
    const response = await apiClient.get<SettlementDetailResponse>(`/admin/settlements/${id}`);
    return response.data;
  },

  // POST /api/admin/settlements/generate - Khởi tạo kỳ đối soát cho đối tác theo khoảng thời gian
  async generateSettlement(request: GenerateSettlementRequest): Promise<SettlementResponse> {
    const response = await apiClient.post<SettlementResponse>('/admin/settlements/generate', request);
    return response.data;
  },

  // PATCH /api/admin/settlements/{id}/finalize - Chốt sổ kỳ đối soát, cố định số tiền giải ngân
  async finalizeSettlement(id: string): Promise<SettlementResponse> {
    const response = await apiClient.patch<SettlementResponse>(`/admin/settlements/${id}/finalize`);
    return response.data;
  },
};

export function getAdminSettlementError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý đối soát'): string {
  if (axios.isAxiosError(error)) {
    // Phân biệt lỗi timeout hoặc mất kết nối mạng (kết quả chưa xác định)
    if (error.code === 'ECONNABORTED' || error.message.toLowerCase().includes('timeout') || (!error.response && error.request)) {
      return 'Giao dịch chưa xác định kết quả do quá thời gian chờ (Timeout) hoặc mất kết nối mạng. Không tự động thử lại để bảo đảm an toàn tài chính. Vui lòng nhấn "Làm mới" để kiểm tra trạng thái thực tế.';
    }

    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('SETTLEMENT_NOT_FOUND')) {
        return 'Không tìm thấy hồ sơ đối soát yêu cầu.';
      }
      if (msg.includes('SETTLEMENT_ALREADY_FINALIZED')) {
        return 'Kỳ đối soát này đã được chốt sổ (FINALIZED), không thể thực hiện lại.';
      }
      if (msg.includes('SETTLEMENT_ALREADY_PAID')) {
        return 'Kỳ đối soát đã được giải ngân hoàn tất (PAID).';
      }
      if (msg.includes('VENDOR_NOT_FOUND')) {
        return 'Không tìm thấy đối tác cung cấp dịch vụ.';
      }
      if (msg.includes('INVALID_SETTLEMENT_PERIOD')) {
        return 'Khoảng thời gian kỳ đối soát không hợp lệ (ngày kết thúc phải sau ngày bắt đầu).';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền ADMIN).';
    if (status === 404) return 'Không tìm thấy hồ sơ đối soát trên hệ thống.';
    if (status === 409) return 'Xung đột dữ liệu hoặc trạng thái kỳ đối soát không hợp lệ.';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
