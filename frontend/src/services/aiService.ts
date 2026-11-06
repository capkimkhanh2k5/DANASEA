import { apiClient } from './apiClient';

export interface AssistantChatRequest {
  message: string;
  conversationId?: string;
}

export interface AssistantChatResponse {
  status: string;
  conversationId: string;
  message: string;
  responseStatus: string;
  cards: unknown[];
  sources: unknown[];
  actions: unknown[];
  requiredInputs: string[];
  context: Record<string, unknown>;
  generatedTextVerified?: boolean;
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

export const aiService = {
  /**
   * POST /api/assistant/chat — Chat tư vấn dịch vụ và giải đáp hải văn
   */
  async chat(message: string, conversationId?: string): Promise<AssistantChatResponse> {
    const res = await apiClient.post<AssistantChatResponse>('/assistant/chat', {
      message,
      conversationId: conversationId || undefined,
    });
    return res.data;
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
};
