import { apiClient } from './apiClient';

export interface EligibleSettlementPayout {
  settlementId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionAmount: number;
  netPayableAmount: number;
  alreadyRequestedAmount: number;
  alreadyPaidAmount: number;
  remainingEligibleBalance: number;
  status: 'PENDING' | 'DISPUTED' | 'FINALIZED' | 'CLOSED';
}

export interface PayoutRequestItem {
  id: string;
  vendorId: string;
  settlementId: string;
  amount: number;
  status: 'REQUESTED' | 'APPROVED' | 'PAID' | 'REJECTED';
  processedBy?: string | null;
  processedAt?: string | null;
  rejectionReason?: string | null;
  bankReference?: string | null;
  adminNote?: string | null;
  note?: string | null;
  vendorNote?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CreatePayoutRequestPayload {
  settlementId: string;
  amount: number;
  note?: string;
}

export const vendorPayoutService = {
  /**
   * Get settlements with remaining eligible balance for withdrawal
   */
  async getEligibleSettlements(): Promise<EligibleSettlementPayout[]> {
    try {
      const res = await apiClient.get<EligibleSettlementPayout[]>('/vendor/payout-requests/eligible-settlements');
      return res.data || [];
    } catch (err) {
      console.warn('[vendorPayoutService] Failed to load eligible settlements:', err);
      return [];
    }
  },

  /**
   * Get vendor payout requests with pagination
   */
  async getPayoutRequests(status?: string, page = 0, size = 20): Promise<SpringPage<PayoutRequestItem>> {
    try {
      const params: Record<string, string | number> = { page, size };
      if (status && status !== 'ALL') params.status = status;
      const res = await apiClient.get<SpringPage<PayoutRequestItem>>('/vendor/payout-requests', { params });
      return res.data;
    } catch (err) {
      console.warn('[vendorPayoutService] Failed to load payout requests:', err);
      return { content: [], totalElements: 0, totalPages: 0, size, number: page };
    }
  },

  /**
   * Create a new payout request
   */
  async createPayoutRequest(payload: CreatePayoutRequestPayload): Promise<PayoutRequestItem> {
    const res = await apiClient.post<PayoutRequestItem>('/vendor/payout-requests', payload);
    return res.data;
  },

  /**
   * Get payout request detail
   */
  async getPayoutRequestDetail(id: string): Promise<PayoutRequestItem> {
    const res = await apiClient.get<PayoutRequestItem>(`/vendor/payout-requests/${encodeURIComponent(id)}`);
    return res.data;
  },
};
