import { apiClient } from './apiClient';

export interface CreatePromotionRequest {
  code: string;
  scope: 'VENDOR' | 'SERVICE' | 'GLOBAL';
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderAmount?: number;
  maxDiscountAmount?: number;
  maxUses?: number;
  maxUsesPerUser?: number;
  validFrom?: string;
  validTo?: string;
  isActive?: boolean;
  serviceId?: string;
}

export interface PromotionResponse {
  id: string;
  code: string;
  scope: 'VENDOR' | 'SERVICE' | 'GLOBAL';
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderAmount?: number;
  maxDiscountAmount?: number;
  maxUses?: number;
  usedCount?: number;
  maxUsesPerUser?: number;
  validFrom?: string;
  validTo?: string;
  isActive: boolean;
  createdAt?: string;
}

export interface PromotionPageResponse {
  content: PromotionResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const vendorPromotionService = {
  /**
   * GET /api/vendor/discount-codes
   */
  async getPromotions(page = 0, size = 20): Promise<PromotionPageResponse> {
    const response = await apiClient.get<PromotionPageResponse>('/vendor/discount-codes', {
      params: { page, size },
    });
    return response.data;
  },

  /**
   * POST /api/vendor/discount-codes
   */
  async createPromotion(req: CreatePromotionRequest): Promise<PromotionResponse> {
    const response = await apiClient.post<PromotionResponse>('/vendor/discount-codes', req);
    return response.data;
  },

  /**
   * PATCH /api/vendor/discount-codes/{id}
   */
  async updatePromotionStatus(id: string, isActive: boolean): Promise<PromotionResponse> {
    const response = await apiClient.patch<PromotionResponse>(`/vendor/discount-codes/${id}`, {
      isActive,
    });
    return response.data;
  },
};
