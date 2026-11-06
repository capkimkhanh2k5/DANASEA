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
