import { apiClient } from './apiClient';
import type { CoachingResult, ReplyDraftResult, SlotAdvisorResult } from '../types/vendorAi';

export const vendorAiService = {
  /**
   * POST /api/vendor/ai/review-coaching
   * Phân tích đánh giá dịch vụ 4 trụ cột (GUIDE, SAFETY, PUNCTUALITY, VALUE)
   */
  async getReviewCoaching(serviceId?: string, periodDays = 90): Promise<CoachingResult> {
    const res = await apiClient.post<CoachingResult>('/vendor/ai/review-coaching', {
      serviceId: serviceId || null,
      periodDays,
    });
    return res.data;
  },

  /**
   * POST /api/vendor/ai/reviews/{reviewId}/reply-draft
   * AI soạn câu trả lời review lịch sự, cầu thị, chuẩn an toàn
   */
  async draftReply(reviewId: string, tone = 'PROFESSIONAL', customPoints?: string): Promise<ReplyDraftResult> {
    const res = await apiClient.post<ReplyDraftResult>(`/vendor/ai/reviews/${reviewId}/reply-draft`, {
      tone,
      customPoints: customPoints || null,
    });
    return res.data;
  },

  /**
   * POST /api/vendor/ai/slots/advisor
   * Cố vấn công suất slot & cảnh báo thời tiết
   */
  async getSlotAdvisor(serviceId?: string, horizonDays = 7): Promise<SlotAdvisorResult> {
    const res = await apiClient.post<SlotAdvisorResult>('/vendor/ai/slots/advisor', {
      serviceId: serviceId || null,
      horizonDays,
    });
    return res.data;
  },
};
