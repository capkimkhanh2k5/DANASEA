import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminServiceResult,
  AdminServiceStatus,
  SafetyDocumentResponse,
} from '../types/adminServiceApproval';

export const adminServiceApprovalService = {
  // GET /api/admin/services - Danh sách dịch vụ kèm bộ lọc trạng thái
  async getAdminServices(status?: AdminServiceStatus): Promise<AdminServiceResult[]> {
    const params = status ? { status } : undefined;
    const response = await apiClient.get<AdminServiceResult[]>('/admin/services', { params });
    return response.data;
  },

  // PATCH /api/admin/services/{id}/approve - Chuyển trạng thái sang PUBLISHED
  async approveService(id: string): Promise<AdminServiceResult> {
    const response = await apiClient.patch<AdminServiceResult>(`/admin/services/${id}/approve`);
    return response.data;
  },

  // PATCH /api/admin/services/{id}/reject
  async rejectService(id: string, reason: string): Promise<AdminServiceResult> {
    const response = await apiClient.patch<AdminServiceResult>(`/admin/services/${id}/reject`, {
      reason,
    });
    return response.data;
  },

  // GET /api/admin/services/{serviceId}/safety-documents - Danh sách chứng chỉ an toàn biển
  async getSafetyDocuments(serviceId: string): Promise<SafetyDocumentResponse[]> {
    const response = await apiClient.get<SafetyDocumentResponse[]>(
      `/admin/services/${serviceId}/safety-documents`
    );
    return response.data;
  },

  // PATCH /api/admin/services/{serviceId}/safety-documents/{docId}/approve
  async approveSafetyDocument(serviceId: string, docId: string): Promise<SafetyDocumentResponse> {
    const response = await apiClient.patch<SafetyDocumentResponse>(
      `/admin/services/${serviceId}/safety-documents/${docId}/approve`
    );
    return response.data;
  },

  // PATCH /api/admin/services/{serviceId}/safety-documents/{docId}/reject
  async rejectSafetyDocument(
    serviceId: string,
    docId: string,
    rejectionReason: string
  ): Promise<SafetyDocumentResponse> {
    const response = await apiClient.patch<SafetyDocumentResponse>(
      `/admin/services/${serviceId}/safety-documents/${docId}/reject`,
      { rejectionReason }
    );
    return response.data;
  },
};

export function getAdminServiceError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý dịch vụ'): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('High-risk services require an approved safety document') || msg.includes('SAFETY_DOCUMENT_REQUIRED')) {
        return 'Dịch vụ rủi ro cao yêu cầu phải phê duyệt ít nhất một chứng chỉ an toàn biển trước khi xuất bản.';
      }
      if (msg.includes('Cannot approve service in status') || msg.includes('Must be PENDING_REVIEW')) {
        return 'Chỉ có thể phê duyệt dịch vụ đang ở trạng thái Chờ duyệt (PENDING_REVIEW).';
      }
      if (msg.includes('Vendor is not approved') || msg.includes('VENDOR_NOT_APPROVED')) {
        return 'Không thể phê duyệt dịch vụ: Đơn vị đối tác cung cấp dịch vụ này chưa được duyệt.';
      }
      if (msg.includes('Service not found')) {
        return 'Không tìm thấy thông tin tour/dịch vụ trên hệ thống.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền ADMIN).';
    if (status === 404) return 'Không tìm thấy tour/dịch vụ yêu cầu (404).';
    if (status === 409) return 'Xung đột dữ liệu hoặc trạng thái tour không hợp lệ.';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
