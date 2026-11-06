import { apiClient } from './apiClient';
import type { ServicePageResponse, ServiceDetailDto, StructuredSlotDto } from '../types/service';
import { flowLogger } from '../utils/flowLogger';

export interface ServiceQueryParams {
  page?: number;
  size?: number;
  categoryId?: string;
  keyword?: string;
  sortBy?: 'price_asc' | 'price_desc' | 'rating_desc' | 'views_desc';
  minPrice?: number;
  maxPrice?: number;
}

interface RawSlotResponse {
  slotId: string;
  serviceId?: string;
  optionId?: string;
  date: string;
  startTime: string;
  endTime: string;
  price?: number;
  pricingUnit?: string;
  status?: string;
  availablePaxOrPackages?: number;
  capacity?: number;
  maxPaxPerPackage?: number;
  inventoryType?: string;
  bookable?: boolean;
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

    flowLogger.start({
      step: '01_CATALOG',
      stepName: 'Tải danh mục dịch vụ',
      operation: 'GET /api/services',
      payload: query,
    });

    try {
      const response = await apiClient.get<ServicePageResponse>('/services', { params: query });
      flowLogger.success(
        {
          step: '01_CATALOG',
          stepName: 'Tải danh mục dịch vụ',
          operation: 'GET /api/services',
        },
        { total: response.data.total, count: response.data.content?.length ?? 0 }
      );
      return response.data;
    } catch (err) {
      flowLogger.error(
        {
          step: '01_CATALOG',
          stepName: 'Tải danh mục dịch vụ',
          operation: 'GET /api/services',
          payload: query,
        },
        err
      );
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
  async getServiceDetail(idOrSlug: string, options?: { silent?: boolean }): Promise<ServiceDetailDto> {
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(idOrSlug);
    const endpoint = isUuid
      ? `/services/${encodeURIComponent(idOrSlug)}`
      : `/services/by-slug/${encodeURIComponent(idOrSlug)}`;

    if (!options?.silent) {
      flowLogger.start({
        step: '02_DETAIL',
        stepName: 'Chi tiết dịch vụ',
        operation: `GET /api${endpoint}`,
        payload: { idOrSlug },
      });
    }

    try {
      const response = await apiClient.get<ServiceDetailDto>(endpoint, {
        silent: options?.silent,
      });
      if (!options?.silent) {
        flowLogger.success(
          {
            step: '02_DETAIL',
            stepName: 'Chi tiết dịch vụ',
            operation: `GET /api${endpoint}`,
          },
          {
            id: response.data.id,
            name: response.data.name,
            optionsCount: response.data.options?.length ?? 0,
          }
        );
      }
      return response.data;
    } catch (err) {
      if (!options?.silent) {
        flowLogger.error(
          {
            step: '02_DETAIL',
            stepName: 'Chi tiết dịch vụ',
            operation: `GET /api${endpoint}`,
            payload: { idOrSlug },
          },
          err
        );
      }
      throw err;
    }
  },

  /**
   * Fetch available service slots by service ID and option ID
   */
  async getServiceSlots(
    serviceId: string,
    params: { optionId?: string; from?: string; to?: string; quantity?: number; allowSplit?: boolean } = {}
  ): Promise<StructuredSlotDto[]> {
    const slotParams = {
      ...(params.optionId ? { optionId: params.optionId } : {}),
      ...(params.from ? { from: params.from } : {}),
      ...(params.to ? { to: params.to } : {}),
      ...(params.quantity !== undefined ? { quantity: params.quantity } : {}),
      ...(params.allowSplit !== undefined ? { allowSplit: params.allowSplit } : {}),
    };

    flowLogger.start({
      step: '02_DETAIL',
      stepName: 'Nạp danh sách ca giờ (Slots)',
      operation: `GET /api/services/${serviceId}/slots`,
      payload: { serviceId, ...slotParams },
    });

    try {
      const response = await apiClient.get<RawSlotResponse[]>(`/services/${encodeURIComponent(serviceId)}/slots`, {
        params: slotParams,
      });

      const slots: StructuredSlotDto[] = (response.data || []).map((slot) => ({
        slotId: slot.slotId,
        date: slot.date,
        startTime: slot.startTime,
        endTime: slot.endTime,
        capacity: slot.capacity ?? slot.availablePaxOrPackages ?? 10,
        availableCapacity: slot.availablePaxOrPackages ?? slot.capacity ?? 10,
        price: slot.price ?? 0,
        optionId: slot.optionId,
        pricingUnit: slot.pricingUnit,
        maxPaxPerPackage: slot.maxPaxPerPackage,
        inventoryType: slot.inventoryType,
        bookable: slot.bookable ?? slot.status === 'OPEN',
      }));

      flowLogger.success(
        {
          step: '02_DETAIL',
          stepName: 'Nạp danh sách ca giờ (Slots)',
          operation: `GET /api/services/${serviceId}/slots`,
        },
        { totalSlotsFound: slots.length, bookableSlots: slots.filter((s) => s.bookable).length }
      );

      return slots;
    } catch (err) {
      flowLogger.error(
        {
          step: '02_DETAIL',
          stepName: 'Nạp danh sách ca giờ (Slots)',
          operation: `GET /api/services/${serviceId}/slots`,
          payload: { serviceId, ...slotParams },
        },
        err
      );
      return [];
    }
  },
};
