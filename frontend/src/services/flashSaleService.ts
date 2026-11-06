import { apiClient } from './apiClient';

export interface ActiveFlashSaleBackendPayload {
  flashSaleId: string;
  serviceId: string;
  serviceOptionId: string;
  serviceName: string;
  serviceImage?: string;
  originalPrice: number;
  salePrice: number;
  actualDiscountAmount?: number;
  actualDiscountPercent?: number;
  startsAt: string;
  endsAt: string;
  remainingQuota: number;
  availability: string;
  currency: string;
}

export interface ActiveFlashSaleItem {
  id: string;
  flashSaleId: string;
  serviceId: string;
  serviceName: string;
  originalPrice: number;
  salePrice: number;
  discountPercentage: number;
  stockAllocated: number;
  stockSold: number;
  startTime: string;
  endTime: string;
  imageUrl?: string;
  location?: string;
}

export const flashSaleService = {
  getActiveFlashSales: async (): Promise<ActiveFlashSaleItem[]> => {
    try {
      const response = await apiClient.get<ActiveFlashSaleBackendPayload[]>('/flash-sales/active');
      const rawItems = response.data || [];
      return rawItems.map((item) => ({
        id: item.flashSaleId,
        flashSaleId: item.flashSaleId,
        serviceId: item.serviceId,
        serviceName: item.serviceName,
        originalPrice: item.originalPrice,
        salePrice: item.salePrice,
        discountPercentage: Math.round(item.actualDiscountPercent ?? 0),
        stockAllocated: item.remainingQuota ?? 10,
        stockSold: 0,
        startTime: item.startsAt,
        endTime: item.endsAt,
        imageUrl: item.serviceImage,
        location: 'Đà Nẵng',
      }));
    } catch {
      return [];
    }
  },
};
