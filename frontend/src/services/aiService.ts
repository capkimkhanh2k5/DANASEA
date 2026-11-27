import { apiClient } from './apiClient';
import { translateDisplayCodesToUuids } from '../utils/displayCode';
import type { MarineBriefingOverview } from '../types/marineBriefing';

export interface AssistantChatRequest {
  message: string;
  conversationId?: string;
}

export interface AssistantAction {
  type: string;
  method: string;
  path: string;
  parameters?: Record<string, unknown>;
  confirmationRequired?: boolean;
}

export interface AssistantCard {
  id: string;
  type: string;
  tool?: string;
  status: string;
  facts: Record<string, unknown>;
  actions?: AssistantAction[];
  requiredInputs?: string[];
}

export interface BookingConfirmOutcome {
  status: 'success' | 'alternative_needed' | 'out_of_stock' | 'error' | string;
  message?: string;
  holdId?: string;
  bookingId?: string;
  paymentDeadline?: string;
  oldCardId?: string;
  reason?: string;
  previousPrice?: number;
  previousTotalAmount?: number;
  newPrice?: number;
  newTotalAmount?: number;
  previousDate?: string;
  priceChanged?: boolean;
  slotChanged?: boolean;
  newCard?: {
    id: string;
    price?: number;
    quantity?: number;
    date?: string;
    participantsCount?: number;
    participantsPerPackage?: number[];
  };
}

export interface AssistantChatResponse {
  status: string;
  conversationId: string;
  message: string;
  responseStatus: string;
  cards: AssistantCard[];
  sources: unknown[];
  actions: unknown[];
  requiredInputs: string[];
  context: Record<string, unknown>;
  generatedTextVerified?: boolean;
  guestRemainingTurns?: number;
  guestLimit?: number;
}

export interface AiRecommendationRequest {
  query?: string;
  categoryId?: string;
  totalBudget?: number;
  partySize?: number;
  from?: string;
  to?: string;
}

export interface AiItineraryItem {
  id: string;
  ownerId: string;
  version: number;
  lifecycle: string;
  plan: {
    status: string;
    totalPrice: number;
    items: unknown[];
    unresolvedConstraints: string[];
    planningMethod: string;
  };
  createdAt: string;
  updatedAt: string;
}

export interface AssistantConversationSummary {
  id: string;
  userId?: string | null;
  title?: string | null;
  locale?: string;
  updatedAt?: string;
}

export interface AssistantHistoryMessageRecord {
  id: string;
  conversationId?: string;
  role: 'USER' | 'ASSISTANT' | 'TOOL' | string;
  content: string;
  responsePayload?: string | null;
  createdAt?: string;
}

export const aiService = {
  /**
   * POST /api/assistant/chat — Chat tư vấn dịch vụ và giải đáp hải văn
   */
  async chat(message: string, conversationId?: string): Promise<AssistantChatResponse> {
    const res = await apiClient.post<AssistantChatResponse>('/assistant/chat', {
      message: translateDisplayCodesToUuids(message),
      conversationId: conversationId || undefined,
    }, { timeout: 130_000 });
    return res.data;
  },

  /**
   * POST /api/assistant/conversations/{id}/claim — Gắn hội thoại khách vào tài khoản sau đăng nhập
   */
  async claimConversation(conversationId: string): Promise<AssistantConversationSummary> {
    const res = await apiClient.post<AssistantConversationSummary>(
      `/assistant/conversations/${encodeURIComponent(conversationId)}/claim`
    );
    return res.data;
  },

  /**
   * GET /api/assistant/conversations — Liệt kê các cuộc trò chuyện của người dùng
   */
  async listConversations(limit = 10): Promise<AssistantConversationSummary[]> {
    const res = await apiClient.get<AssistantConversationSummary[]>(
      `/assistant/conversations?archived=false&limit=${limit}`
    );
    return Array.isArray(res.data) ? res.data : [];
  },

  /**
   * GET /api/assistant/conversations/{id}/history — Lấy lịch sử tin nhắn của cuộc trò chuyện
   */
  async getConversationHistory(conversationId: string, limit = 40): Promise<AssistantHistoryMessageRecord[]> {
    const res = await apiClient.get<AssistantHistoryMessageRecord[]>(
      `/assistant/conversations/${encodeURIComponent(conversationId)}/history?limit=${limit}`
    );
    return Array.isArray(res.data) ? res.data : [];
  },

  /**
   * POST /api/ai/recommendations — Gợi ý trải nghiệm thông minh dựa trên nhu cầu & ngân sách
   */
  async getRecommendations(params: AiRecommendationRequest) {
    const res = await apiClient.post('/ai/recommendations', params);
    return res.data;
  },

  /**
   * POST /api/ai/search — Tìm kiếm ngữ nghĩa đa tiêu chí
   */
  async search(query: string, totalBudget?: number) {
    const res = await apiClient.post('/ai/search', { query, totalBudget });
    return res.data;
  },

  /**
   * POST /api/ai/itineraries — Tự động thiết lập lịch trình du lịch biển
   */
  async createItinerary(criteria: AiRecommendationRequest): Promise<AiItineraryItem> {
    const res = await apiClient.post<AiItineraryItem>('/ai/itineraries', criteria);
    return res.data;
  },

  /**
   * GET /api/ai/itineraries — Lịch sử các kế hoạch lịch trình của người dùng
   */
  async getItineraries(): Promise<AiItineraryItem[]> {
    const res = await apiClient.get<AiItineraryItem[]>('/ai/itineraries');
    return res.data;
  },

  /**
   * GET /api/ai/review-summaries/{serviceId} — Tóm tắt đánh giá khách hàng bằng AI
   */
  async getReviewSummary(serviceId: string) {
    const res = await apiClient.get(`/ai/review-summaries/${serviceId}`);
    return res.data;
  },

  /**
   * GET /api/ai/orders/{orderId}/marine-briefing — Cẩm nang ra khơi 24h & checklist an toàn
   */
  async getMarineBriefing(orderId: string): Promise<MarineBriefingOverview> {
    const res = await apiClient.get<MarineBriefingOverview>(`/ai/orders/${orderId}/marine-briefing`);
    return res.data;
  },

  /**
   * POST /api/assistant/conversations/{conversationId}/confirm — Xác nhận đặt chỗ từ thẻ AI
   */
  async confirmBooking(conversationId: string, cardId: string, sessionId?: string): Promise<BookingConfirmOutcome> {
    const res = await apiClient.post<BookingConfirmOutcome>(
      `/assistant/conversations/${encodeURIComponent(conversationId)}/confirm`,
      { cardId, ...(sessionId ? { sessionId } : {}) }
    );
    return res.data;
  },

  /**
   * POST /api/ai/itineraries/{itineraryId}/proposals/{proposalId}/accept
   */
  async acceptProposal(itineraryId: string, proposalId: string, expectedVersion: number): Promise<AiItineraryItem> {
    const res = await apiClient.post<AiItineraryItem>(
      `/ai/itineraries/${encodeURIComponent(itineraryId)}/proposals/${encodeURIComponent(proposalId)}/accept`,
      { expectedVersion }
    );
    return res.data;
  },

  /**
   * POST /api/ai/itineraries/{itineraryId}/proposals/{proposalId}/reject
   */
  async rejectProposal(itineraryId: string, proposalId: string, expectedVersion: number): Promise<Record<string, unknown>> {
    const res = await apiClient.post<Record<string, unknown>>(
      `/ai/itineraries/${encodeURIComponent(itineraryId)}/proposals/${encodeURIComponent(proposalId)}/reject`,
      { expectedVersion }
    );
    return res.data;
  },
};
