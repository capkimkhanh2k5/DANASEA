import { apiClient } from './apiClient';
import type {
  PartnerApplicationResponse,
  SubmitPartnerApplicationRequest,
  ResubmitPartnerApplicationRequest,
} from '../types/partnerApplication';

export const customerPartnerService = {
  /**
   * Submit an application to become a partner/vendor (POST /api/partner-applications)
   */
  async submitApplication(data: SubmitPartnerApplicationRequest): Promise<PartnerApplicationResponse> {
    const res = await apiClient.post<PartnerApplicationResponse>('/partner-applications', data);
    return res.data;
  },

  /**
   * Get current user's partner application status (GET /api/partner-applications/my)
   */
  async getMyApplication(): Promise<PartnerApplicationResponse | null> {
    try {
      const res = await apiClient.get<PartnerApplicationResponse>('/partner-applications/my');
      return res.data;
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 404) {
        return null; // No application submitted yet
      }
      console.warn('[customerPartnerService] Error loading my application:', err);
      return null;
    }
  },

  /**
   * Resubmit a rejected partner application (POST /api/partner-applications/resubmit)
   */
  async resubmitApplication(data: ResubmitPartnerApplicationRequest): Promise<PartnerApplicationResponse> {
    const res = await apiClient.post<PartnerApplicationResponse>('/partner-applications/resubmit', data);
    return res.data;
  },
};
