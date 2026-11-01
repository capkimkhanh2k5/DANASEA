import { apiClient } from './apiClient';
import type { ServicePageResponse, ServiceDetailDto } from '../types/service';

export interface ServiceQueryParams {
  page?: number;
  size?: number;
  categoryId?: string;
  keyword?: string;
  sortBy?: 'price_asc' | 'price_desc' | 'rating_desc' | 'views_desc';
  minPrice?: number;
  maxPrice?: number;
}

export const serviceCatalogService = {
  /**
   * Fetch paginated published services from Backend
   */
  async getServices(params: ServiceQueryParams = {}): Promise<ServicePageResponse> {
    const query = {
      page: params.page ?? 0,
      size: params.size ?? 12,
      ...(params.categoryId ? { categoryId: params.categoryId } : {}),
      ...(params.keyword ? { keyword: params.keyword } : {}),
      ...(params.sortBy ? { sortBy: params.sortBy } : {}),
      ...(params.minPrice !== undefined ? { minPrice: params.minPrice } : {}),
      ...(params.maxPrice !== undefined ? { maxPrice: params.maxPrice } : {}),
    };

    try {
      const response = await apiClient.get<ServicePageResponse>('/services', { params: query });
      return response.data;
    } catch (err) {
      console.warn('[serviceCatalogService] Không thể tải dữ liệu dịch vụ từ Backend, kích hoạt fallback:', err);
      return {
        content: [],
        page: 0,
        size: query.size,
        totalElements: 0,
        total: 0,
      };
    }
  },

  /**
   * Fetch public service detail by ID or SEO Slug from Backend
   * Follows EPIC-03 Discovery R2 specification
   */
  async getServiceDetail(idOrSlug: string): Promise<ServiceDetailDto> {
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(idOrSlug);
    const endpoint = isUuid
      ? `/services/${encodeURIComponent(idOrSlug)}`
      : `/services/by-slug/${encodeURIComponent(idOrSlug)}`;
    const response = await apiClient.get<ServiceDetailDto>(endpoint);
    return response.data;
  },
};
