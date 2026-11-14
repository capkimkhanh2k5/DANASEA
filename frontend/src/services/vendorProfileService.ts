import axios from 'axios';
import { apiClient } from './apiClient';

export interface VendorProfileDto {
  id: string;
  userId: string;
  businessName: string;
  brandName?: string;
  contactEmail?: string;
  contactPhone?: string;
  taxCode?: string;
  address?: string;
  businessAddress?: string;
  bankName?: string;
  bankAccountNumber?: string;
  bankAccountHolder?: string;
  verificationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED';
  badgeTier?: 'NONE' | 'VERIFIED' | 'TOP_RATED';
  ratingAvg?: number;
  ratingCount?: number;
  status?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  rejectionReason?: string;
  ratingAverage?: number;
  totalReviews?: number;
  verifiedAt?: string;
}

export interface VendorDocumentDto {
  id: string;
  vendorId: string;
  docType: 'BUSINESS_LICENSE' | 'MARITIME_PERMIT' | 'SAFETY_CERT' | 'INSURANCE' | 'OTHER';
  fileName?: string;
  fileUrl: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  uploadedAt?: string;
}

export interface UpdateVendorProfilePayload {
  businessName?: string;
  brandName?: string;
  contactPhone?: string;
  businessAddress?: string;
  taxCode?: string;
  bankName?: string;
  bankAccountNumber?: string;
  bankAccountHolder?: string;
}

export const vendorProfileService = {
  /**
   * GET /api/vendor/profile
   */
  async getProfile(): Promise<VendorProfileDto> {
    const response = await apiClient.get<VendorProfileDto>('/vendor/profile');
    return response.data;
  },

  /**
   * PATCH /api/vendor/profile
   */
  async updateProfile(payload: UpdateVendorProfilePayload): Promise<VendorProfileDto> {
    const response = await apiClient.patch<VendorProfileDto>('/vendor/profile', payload);
    return response.data;
  },

  /**
   * POST /api/vendor/profile/resubmit
   */
  async resubmitProfile(): Promise<VendorProfileDto> {
    const response = await apiClient.post<VendorProfileDto>('/vendor/profile/resubmit');
    return response.data;
  },

  /**
   * GET /api/vendor/documents
   */
  async getDocuments(): Promise<VendorDocumentDto[]> {
    const response = await apiClient.get<VendorDocumentDto[]>('/vendor/documents');
    return response.data || [];
  },

  /**
   * POST /api/vendor/documents
   */
  async uploadDocument(file: File, docType: string): Promise<VendorDocumentDto> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('doc_type', docType);
    const response = await apiClient.post<VendorDocumentDto>('/vendor/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
};

export function getVendorProfileError(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra khi xử lý hồ sơ đối tác'): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;

    if (msg) {
      if (msg.includes('VendorDocumentsIncompleteException') || msg.includes('VENDOR_DOCUMENTS_INCOMPLETE')) {
        return 'Chưa thể gửi duyệt lại: Bạn cần tải lên đầy đủ Giấy phép kinh doanh (BUSINESS_LICENSE) và Chứng chỉ cứu sinh an toàn (SAFETY_CERT).';
      }
      if (msg.includes('VendorStatusConflictException') || msg.includes('VENDOR_STATUS_CONFLICT')) {
        return 'Hồ sơ đối tác chỉ có thể gửi duyệt lại khi đang ở trạng thái TỪ CHỐI (REJECTED).';
      }
      if (msg.includes('VendorProfileIncompleteException') || msg.includes('VENDOR_PROFILE_INCOMPLETE')) {
        return 'Vui lòng điền đầy đủ các thông tin pháp lý bắt buộc trước khi gửi duyệt lại.';
      }
      return msg;
    }

    if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền truy cập kênh Đối tác.';
    if (status === 404) return 'Không tìm thấy hồ sơ đối tác của bạn.';
    if (status === 409) return 'Xung đột trạng thái hồ sơ đối tác.';
    if (status && status >= 500) return 'Máy chủ đang xử lý. Vui lòng thử lại sau ít phút.';
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
