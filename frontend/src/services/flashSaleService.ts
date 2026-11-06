import { apiClient } from './apiClient';

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
      const response = await apiClient.get<ActiveFlashSaleItem[]>('/flash-sales/active');
      return response.data || [];
    } catch {
      return [];
    }
  },
};
