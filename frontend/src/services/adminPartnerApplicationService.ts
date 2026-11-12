import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  PartnerApplicationResponse,
  PartnerApplicationQueryParams,
  SpringPage,
} from '../types/partnerApplication';

export const adminPartnerApplicationService = {
  // GET /api/admin/partner-applications
  async getApplications(params?: PartnerApplicationQueryParams): Promise<SpringPage<PartnerApplicationResponse>> {
    const queryParams: Record<string, string | number> = {};
    if (params?.status) queryParams.status = params.status;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    if (params?.sort) queryParams.sort = params.sort;

    const response = await apiClient.get<SpringPage<PartnerApplicationResponse>>('/admin/partner-applications', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/partner-applications/{id}
  async getApplication(id: string): Promise<PartnerApplicationResponse> {
    const response = await apiClient.get<PartnerApplicationResponse>(`/admin/partner-applications/${encodeURIComponent(id)}`);
    return response.data;
  },

  // PATCH /api/admin/partner-applications/{id}/approve
  async approveApplication(id: string): Promise<PartnerApplicationResponse> {
    const response = await apiClient.patch<PartnerApplicationResponse>(`/admin/partner-applications/${encodeURIComponent(id)}/approve`);
    return response.data;
  },

  // PATCH /api/admin/partner-applications/{id}/reject
  async rejectApplication(id: string, rejectionReason: string): Promise<PartnerApplicationResponse> {
    const response = await apiClient.patch<PartnerApplicationResponse>(
      `/admin/partner-applications/${encodeURIComponent(id)}/reject`,
      { rejectionReason }
    );
    return response.data;
  },
};

export function getAdminPartnerApplicationError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý đơn xét duyệt vai trò'): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('Application not found') || msg.includes('APPLICATION_NOT_FOUND')) {
        return 'Không tìm thấy đơn xét duyệt đối tác trên hệ thống.';
      }
      if (msg.includes('Application is not in PENDING status') || msg.includes('APPLICATION_STATUS_CONFLICT')) {
        return 'Đơn xét duyệt không ở trạng thái Chờ duyệt (PENDING) để thao tác.';
      }
      if (msg.includes('Rejection reason') || msg.includes('rejection_reason')) {
        return 'Vui lòng cung cấp lý do từ chối rõ ràng cho đối tác.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền quản trị để thao tác duyệt đơn đối tác (yêu cầu ADMIN).';
    if (status === 404) return 'Không tìm thấy đơn xét duyệt đối tác (404).';
    if (status === 409) return 'Xung đột trạng thái đơn xét duyệt hoặc tài khoản đã thay đổi.';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
