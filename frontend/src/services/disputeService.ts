import { apiClient } from './apiClient';
import type {
  CreateDisputeRequest,
  DisputeResponse,
  DisputePageResponse,
  SubmitVendorResponseRequest,
} from '../types/dispute';

export const disputeService = {
  /**
   * Customer: Mở khiếu nại cho một đơn hàng / sub-order
   * POST /api/orders/{id}/disputes
   */
  async createDispute(orderId: string, req: CreateDisputeRequest): Promise<DisputeResponse> {
    const response = await apiClient.post<DisputeResponse>(`/orders/${orderId}/disputes`, req);
    return response.data;
  },

  /**
   * Customer: Danh sách khiếu nại của du khách
   * GET /api/disputes
   */
  async getCustomerDisputes(page = 0, size = 20): Promise<DisputePageResponse> {
    const response = await apiClient.get<DisputePageResponse>('/disputes', {
      params: { page, size },
    });
    return response.data;
  },

  /**
   * Customer: Chi tiết khiếu nại
   * GET /api/disputes/{id}
   */
  async getCustomerDisputeDetail(id: string): Promise<DisputeResponse> {
    const response = await apiClient.get<DisputeResponse>(`/disputes/${id}`);
    return response.data;
  },

  /**
   * Vendor: Danh sách khiếu nại đối với vendor
   * GET /api/vendor/disputes
   */
  async getVendorDisputes(status?: string, page = 0, size = 20): Promise<DisputePageResponse> {
    const params: Record<string, unknown> = { page, size };
    if (status && status !== 'ALL') {
      params.status = status;
    }
    const response = await apiClient.get<DisputePageResponse>('/vendor/disputes', { params });
    return response.data;
  },

  /**
   * Vendor: Chi tiết khiếu nại
   * GET /api/vendor/disputes/{id}
   */
  async getVendorDisputeDetail(id: string): Promise<DisputeResponse> {
    const response = await apiClient.get<DisputeResponse>(`/vendor/disputes/${id}`);
    return response.data;
  },

  /**
   * Vendor: Gửi giải trình / bằng chứng phản hồi khiếu nại
   * POST /api/vendor/disputes/{id}/responses
   */
  async submitVendorResponse(id: string, req: SubmitVendorResponseRequest): Promise<DisputeResponse> {
    const response = await apiClient.post<DisputeResponse>(`/vendor/disputes/${id}/responses`, req);
    return response.data;
  },

  /**
   * Tải lên tệp bằng chứng tranh chấp (JPEG, PNG, WEBP, PDF tối đa 5MB)
   * POST /api/disputes/evidence
   */
  async uploadEvidence(file: File): Promise<{ fileUrl: string }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post<{ fileUrl: string }>('/disputes/evidence', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
