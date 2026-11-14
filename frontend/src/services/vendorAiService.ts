import { apiClient } from './apiClient';
import type { 
  CoachingResult, 
  ReplyDraftResult, 
  SlotAdvisorResult,
  OptimizeListingRequest,
  OptimizeListingResult,
  CopilotChatRequest,
  CopilotChatResponse,
  CopilotConversation,
  CopilotMessage,
  UpdateConversationMetaRequest
} from '../types/vendorAi';

export const vendorAiService = {
  /**
   * POST /api/vendor/ai/listings/optimize
   * AI tối ưu hóa tiêu đề, mô tả chuẩn SEO, trang bị an toàn và đa ngữ cho Tour
   */
  async optimizeListing(req: OptimizeListingRequest): Promise<OptimizeListingResult> {
    const res = await apiClient.post<OptimizeListingResult>('/vendor/ai/listings/optimize', req);
    return res.data;
  },

  /**
   * POST /api/vendor/ai/copilot/chat
   * Chat hỏi đáp KPIs, doanh thu, phân tích đơn hàng và hỗ trợ đính kèm ngữ cảnh
   */
  async chatCopilot(req: CopilotChatRequest): Promise<CopilotChatResponse> {
    const res = await apiClient.post<CopilotChatResponse>('/vendor/ai/copilot/chat', req);
    return res.data;
  },

  /**
   * GET /api/vendor/ai/copilot/conversations
   * Lấy danh sách đoạn chat của Vendor
   */
  async getConversations(archived = false, limit = 20): Promise<CopilotConversation[]> {
    const res = await apiClient.get<CopilotConversation[]>('/vendor/ai/copilot/conversations', {
      params: { archived, limit },
    });
    return res.data;
  },

  /**
   * GET /api/vendor/ai/copilot/conversations/{id}/history
   * Lấy lịch sử tin nhắn của một phiên chat
   */
  async getConversationHistory(id: string): Promise<CopilotMessage[]> {
    const res = await apiClient.get<CopilotMessage[]>(`/vendor/ai/copilot/conversations/${id}/history`);
    return res.data;
  },

  /**
   * PATCH /api/vendor/ai/copilot/conversations/{id}
   * Đổi tên, ghim hoặc lưu trữ đoạn chat
   */
  async updateConversation(id: string, meta: UpdateConversationMetaRequest): Promise<CopilotConversation> {
    const res = await apiClient.patch<CopilotConversation>(`/vendor/ai/copilot/conversations/${id}`, meta);
    return res.data;
  },

  /**
   * DELETE /api/vendor/ai/copilot/conversations/{id}
   * Xóa đoạn chat
   */
  async deleteConversation(id: string): Promise<void> {
    await apiClient.delete(`/vendor/ai/copilot/conversations/${id}`);
  },

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
