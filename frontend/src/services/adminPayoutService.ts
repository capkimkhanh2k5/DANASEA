import { apiClient } from './apiClient';
import type { PayoutRequestItem, SpringPage } from './vendorPayoutService';

export interface AdminPayoutFilter {
  vendorId?: string;
  settlementId?: string;
  status?: string;
  page?: number;
  size?: number;
}

export interface AdminRejectPayoutPayload {
  rejectionReason: string;
  adminNote?: string;
}

export interface AdminMarkPaidPayload {
  bankReference?: string;
  adminNote?: string;
}

export interface AdminApprovePayload {
  adminNote?: string;
}

export const adminPayoutService = {
  /**
   * Get all payout requests across all vendors
   */
  async getPayoutRequests(filter: AdminPayoutFilter = {}): Promise<SpringPage<PayoutRequestItem>> {
    try {
      const params: Record<string, string | number> = {
        page: filter.page ?? 0,
        size: filter.size ?? 20,
      };
      if (filter.vendorId) params.vendorId = filter.vendorId;
      if (filter.settlementId) params.settlementId = filter.settlementId;
      if (filter.status && filter.status !== 'ALL') params.status = filter.status;

      const res = await apiClient.get<SpringPage<PayoutRequestItem>>('/admin/payout-requests', { params });
      return res.data;
    } catch (err) {
      console.warn('[adminPayoutService] Failed to load payout requests:', err);
      return { content: [], totalElements: 0, totalPages: 0, size: filter.size ?? 20, number: filter.page ?? 0 };
    }
  },

  /**
   * Approve a payout request
   */
  async approvePayout(id: string, adminNote?: string): Promise<PayoutRequestItem> {
    const payload: AdminApprovePayload = { adminNote };
    const res = await apiClient.patch<PayoutRequestItem>(`/admin/payout-requests/${encodeURIComponent(id)}/approve`, payload);
    return res.data;
  },

  /**
   * Reject a payout request with reason
   */
  async rejectPayout(id: string, rejectionReason: string, adminNote?: string): Promise<PayoutRequestItem> {
    const payload: AdminRejectPayoutPayload = { rejectionReason, adminNote };
    const res = await apiClient.patch<PayoutRequestItem>(`/admin/payout-requests/${encodeURIComponent(id)}/reject`, payload);
    return res.data;
  },

  /**
   * Mark payout as PAID with bank reference / transaction code
   */
  async markPaid(id: string, bankReference: string, adminNote?: string): Promise<PayoutRequestItem> {
    const payload: AdminMarkPaidPayload = { bankReference, adminNote };
    const res = await apiClient.patch<PayoutRequestItem>(`/admin/payout-requests/${encodeURIComponent(id)}/mark-paid`, payload);
    return res.data;
  },
};
