import { apiClient } from './apiClient';

export type SettlementStatus = 'PENDING' | 'FINALIZED' | 'PAID' | 'DISPUTED';

export interface SettlementLineItemResponse {
  id: string;
  subOrderId: string;
  orderNumber?: string;
  serviceTitle?: string;
  customerName?: string;
  departureTime?: string;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  status: string;
}

export interface SettlementResponse {
  id: string;
  vendorId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionAmount: number;
  netPayableAmount: number;
  status: SettlementStatus;
  generatedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SettlementDetailResponse extends SettlementResponse {
  lineItems: SettlementLineItemResponse[];
}

export interface SettlementPageResponse {
  content: SettlementResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const vendorSettlementService = {
  /**
   * GET /api/vendor/settlements
   */
  async getSettlements(params?: {
    status?: SettlementStatus;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }): Promise<SettlementPageResponse> {
    const response = await apiClient.get<SettlementPageResponse>('/vendor/settlements', {
      params: {
        status: params?.status,
        from: params?.from,
        to: params?.to,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
      },
    });
    return response.data;
  },

  /**
   * GET /api/vendor/settlements/{id}
   */
  async getSettlementDetail(id: string): Promise<SettlementDetailResponse> {
    const response = await apiClient.get<SettlementDetailResponse>(`/vendor/settlements/${id}`);
    return response.data;
  },
};
