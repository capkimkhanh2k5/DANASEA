import axios from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminVendorResponse,
  VendorDocumentResponse,
  AdminVendorQueryParams,
  SpringPage,
} from '../types/adminVendor';

export const adminVendorService = {
  // GET /api/admin/vendors - Hỗ trợ phân trang, lọc trạng thái và sắp xếp
  async getVendors(params?: AdminVendorQueryParams): Promise<SpringPage<AdminVendorResponse>> {
    const queryParams: Record<string, string | number> = {};
    if (params?.status) queryParams.status = params.status;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    if (params?.sort) queryParams.sort = params.sort;

    const response = await apiClient.get<SpringPage<AdminVendorResponse>>('/admin/vendors', {
      params: queryParams,
    });
    return response.data;
  },

  // GET /api/admin/vendors/{id}
  async getVendor(id: string): Promise<AdminVendorResponse> {
    const response = await apiClient.get<AdminVendorResponse>(`/admin/vendors/${id}`);
    return response.data;
  },

  // PATCH /api/admin/vendors/{id}/approve - Yêu cầu BUSINESS_LICENSE và SAFETY_CERT phải ở trạng thái APPROVED
  async approveVendor(id: string): Promise<AdminVendorResponse> {
    const response = await apiClient.patch<AdminVendorResponse>(`/admin/vendors/${id}/approve`);
    return response.data;
  },

  // PATCH /api/admin/vendors/{id}/reject
  async rejectVendor(id: string, reason: string): Promise<AdminVendorResponse> {
    const response = await apiClient.patch<AdminVendorResponse>(`/admin/vendors/${id}/reject`, {
      reason,
    });
    return response.data;
  },

  // PATCH /api/admin/vendors/{vendorId}/documents/{documentId}/approve
  async approveVendorDocument(vendorId: string, documentId: string): Promise<VendorDocumentResponse> {
    const response = await apiClient.patch<VendorDocumentResponse>(
      `/admin/vendors/${vendorId}/documents/${documentId}/approve`
    );
    return response.data;
  },

  // PATCH /api/admin/vendors/{vendorId}/documents/{documentId}/reject
  async rejectVendorDocument(
    vendorId: string,
    documentId: string,
    reason: string
  ): Promise<VendorDocumentResponse> {
    const response = await apiClient.patch<VendorDocumentResponse>(
      `/admin/vendors/${vendorId}/documents/${documentId}/reject`,
      { reason }
    );
    return response.data;
  },
};

export function getAdminVendorError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý đối tác'): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('Vendor approval requires approved documents') || msg.includes('VENDOR_DOCUMENTS_INCOMPLETE')) {
        return 'Không thể duyệt đối tác: Tất cả chứng từ bắt buộc (Giấy phép kinh doanh, Chứng nhận an toàn) phải được duyệt trước.';
      }
      if (msg.includes('Vendor is not in PENDING status')) {
        return 'Hồ sơ đối tác không ở trạng thái Chờ duyệt (PENDING).';
      }
      if (msg.includes('Vendor not found')) {
        return 'Không tìm thấy thông tin đối tác trên hệ thống.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền ADMIN).';
    if (status === 404) return 'Không tìm thấy tài nguyên yêu cầu (404).';
    if (status === 409) return 'Xung đột dữ liệu hoặc trạng thái không hợp lệ.';
    if (status && status >= 500) return 'Lỗi máy chủ nội bộ. Vui lòng thử lại sau.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
