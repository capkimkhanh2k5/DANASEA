import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  DisputeResponse,
  ResolveDisputeRequest,
  AdminDisputeQueryParams,
} from '../types/adminDispute';
import type { SpringPage } from '../types/adminOrder';

export const adminDisputeService = {
  // GET /api/admin/disputes - Danh sách khiếu nại & tranh chấp kèm bộ lọc trạng thái và phân trang
  async getDisputes(params?: AdminDisputeQueryParams): Promise<SpringPage<DisputeResponse>> {
    const queryParams: Record<string, string | number> = {};
    if (params?.status) queryParams.status = params.status;
    if (params?.reason) queryParams.reason = params.reason;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    if (params?.sort) queryParams.sort = params.sort;

    const response = await apiClient.get<SpringPage<DisputeResponse>>('/admin/disputes', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/disputes/{id} - Chi tiết khiếu nại, phản hồi của đối tác và bằng chứng hình ảnh
  async getDisputeDetail(id: string): Promise<DisputeResponse> {
    const response = await apiClient.get<DisputeResponse>(`/admin/disputes/${id}`);
    return response.data;
  },

  // PATCH /api/admin/disputes/{id}/resolve - Phán quyết khiếu nại (hoàn tiền toàn phần, một phần hoặc bác bỏ)
  async resolveDispute(id: string, request: ResolveDisputeRequest): Promise<DisputeResponse> {
    const response = await apiClient.patch<DisputeResponse>(`/admin/disputes/${id}/resolve`, request);
    return response.data;
  },
};

export function getAdminDisputeError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý khiếu nại'): string {
  if (axios.isAxiosError(error)) {
    // Phân biệt lỗi timeout hoặc mất kết nối mạng (kết quả chưa xác định)
    if (error.code === 'ECONNABORTED' || error.message.toLowerCase().includes('timeout') || (!error.response && error.request)) {
      return 'Phán quyết chưa xác định kết quả do quá thời gian chờ (Timeout) hoặc mất kết nối mạng. Không tự động thử lại để bảo đảm an toàn tài chính. Vui lòng nhấn "Làm mới" để kiểm tra trạng thái thực tế.';
    }

    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('DISPUTE_NOT_FOUND')) {
        return 'Không tìm thấy hồ sơ khiếu nại.';
      }
      if (msg.includes('DISPUTE_ALREADY_RESOLVED')) {
        return 'Khiếu nại này đã có phán quyết trước đó, không thể chỉnh sửa.';
      }
      if (msg.includes('INVALID_DISPUTE_RESOLUTION')) {
        return 'Phương án phán quyết không hợp lệ (yêu cầu: Hoàn tiền, Hoàn một phần hoặc Bác bỏ).';
      }
      if (msg.includes('Refund percentage must be greater than 0')) {
        return 'Tỷ lệ hoàn tiền phải lớn hơn 0% và không vượt quá 100%.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền ADMIN).';
    if (status === 404) return 'Không tìm thấy hồ sơ khiếu nại trên hệ thống.';
    if (status === 409) return 'Khiếu nại đã được xử lý hoặc có xung đột trạng thái.';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
