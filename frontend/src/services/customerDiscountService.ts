import { apiClient } from './apiClient';
import type { PageDiscountCodeResponse } from '../types/adminDiscountCode';

export const customerDiscountService = {
  /**
   * GET /api/customers/me/discount-codes
   * Lấy danh sách mã ưu đãi và voucher đền bù cá nhân thực tế từ hệ thống
   */
  async getMyDiscountCodes(params: { page?: number; size?: number } = {}, signal?: AbortSignal): Promise<PageDiscountCodeResponse> {
    const { data } = await apiClient.get<PageDiscountCodeResponse>('/customers/me/discount-codes', {
      params,
      signal,
    });
    return data;
  },
};
