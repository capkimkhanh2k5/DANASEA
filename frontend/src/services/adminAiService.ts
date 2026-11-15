import axios, { type AxiosRequestConfig } from 'axios';
import { apiClient } from './apiClient';
import type {
  DisputeArbitrationResult,
  DisputeSummary,
  PlatformDashboardMetrics,
  CopilotConversation,
  CopilotMessage,
  CopilotChatRequestDto,
  CopilotChatResponse,
  UpdateConversationMetaDto,
} from '../types/adminAi';

/**
 * Timeout Configuration Matrix based on backend execution profiles:
 * - Executive Digest: In-memory JPA aggregations (0 LLM calls) -> 15s
 * - Dispute Summary: Deterministic evidence compilation (0 LLM calls) -> 20s
 * - Copilot Multi-Turn Chat: Multi-tool reasoning LLM agent (budget 120s, Nginx 125s) -> 90s
 * - Copilot Sessions & History CRUD: Standard database queries -> 15s
 */
export const AI_TIMEOUT_CONFIG = {
  EXECUTIVE_DIGEST_MS: 15_000,
  DISPUTE_ARBITRATION_MS: 20_000,
  COPILOT_CHAT_MS: 90_000,
  COPILOT_CRUD_MS: 15_000,
} as const;

export class AdminAiService {
  // ==================== DISPUTE ARBITRATION AI ====================

  /**
   * Request deterministic evidence compilation and arbitration recommendation for a dispute.
   * Calls POST /api/admin/ai/disputes/{disputeId}/summarize
   */
  async summarizeDispute(disputeId: string): Promise<DisputeArbitrationResult> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.DISPUTE_ARBITRATION_MS,
    };
    const response = await apiClient.post<DisputeArbitrationResult>(
      `/admin/ai/disputes/${encodeURIComponent(disputeId)}/summarize`,
      {},
      config
    );
    return response.data;
  }

  /**
   * Retrieve previously saved dispute arbitration summary.
   * Calls GET /api/admin/ai/disputes/{disputeId}/summary
   */
  async getDisputeSummary(disputeId: string): Promise<DisputeSummary | null> {
    try {
      const config: AxiosRequestConfig = {
        timeout: AI_TIMEOUT_CONFIG.DISPUTE_ARBITRATION_MS,
      };
      const response = await apiClient.get<DisputeSummary>(
        `/admin/ai/disputes/${encodeURIComponent(disputeId)}/summary`,
        config
      );
      return response.data;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 404) {
        return null;
      }
      throw err;
    }
  }

  // ==================== EXECUTIVE DIGEST ====================

  /**
   * Fetch one-touch executive digest metrics for the platform.
   * Calls GET /api/admin/ai/executive-digest?period={period}
   */
  async getExecutiveDigest(period: string = 'THIS_MONTH'): Promise<PlatformDashboardMetrics> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.EXECUTIVE_DIGEST_MS,
      params: { period },
    };
    const response = await apiClient.get<PlatformDashboardMetrics>(
      '/admin/ai/executive-digest',
      config
    );
    return response.data;
  }

  // ==================== ADMIN COPILOT ====================

  /**
   * Send a chat message to Admin Copilot.
   * Calls POST /api/admin/ai/copilot/chat
   * Note: This request is NOT idempotent. Never auto-retry automatically on failure.
   */
  async chatCopilot(payload: CopilotChatRequestDto): Promise<CopilotChatResponse> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.COPILOT_CHAT_MS,
    };
    const response = await apiClient.post<CopilotChatResponse>(
      '/admin/ai/copilot/chat',
      payload,
      config
    );
    return response.data;
  }

  /**
   * List conversations for Admin Copilot.
   * Calls GET /api/admin/ai/copilot/conversations
   */
  async listConversations(params?: {
    archived?: boolean;
    limit?: number;
  }): Promise<CopilotConversation[]> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.COPILOT_CRUD_MS,
      params: {
        ...(params?.archived !== undefined ? { archived: params.archived } : {}),
        limit: params?.limit ?? 30,
      },
    };
    const response = await apiClient.get<CopilotConversation[]>(
      '/admin/ai/copilot/conversations',
      config
    );
    return response.data;
  }

  /**
   * Get historical messages for a conversation session.
   * Calls GET /api/admin/ai/copilot/conversations/{id}/history
   */
  async getConversationHistory(conversationId: string): Promise<CopilotMessage[]> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.COPILOT_CRUD_MS,
    };
    const response = await apiClient.get<CopilotMessage[]>(
      `/admin/ai/copilot/conversations/${encodeURIComponent(conversationId)}/history`,
      config
    );
    return response.data;
  }

  /**
   * Update conversation metadata (title, pinned, archived).
   * Calls PATCH /api/admin/ai/copilot/conversations/{id}
   */
  async updateConversation(
    conversationId: string,
    dto: UpdateConversationMetaDto
  ): Promise<CopilotConversation> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.COPILOT_CRUD_MS,
    };
    const response = await apiClient.patch<CopilotConversation>(
      `/admin/ai/copilot/conversations/${encodeURIComponent(conversationId)}`,
      dto,
      config
    );
    return response.data;
  }

  /**
   * Soft-delete a conversation.
   * Calls DELETE /api/admin/ai/copilot/conversations/{id}
   */
  async deleteConversation(conversationId: string): Promise<void> {
    const config: AxiosRequestConfig = {
      timeout: AI_TIMEOUT_CONFIG.COPILOT_CRUD_MS,
    };
    await apiClient.delete(
      `/admin/ai/copilot/conversations/${encodeURIComponent(conversationId)}`,
      config
    );
  }
}

export const adminAiService = new AdminAiService();

/**
 * Extracts a clear, user-friendly error message from an API error.
 * Explicitly surfaces timeouts, authorization errors, rate-limiting, and backend 5xx status.
 */
export function getAdminAiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    // 1. Timeout detection
    if (
      error.code === 'ECONNABORTED' ||
      (typeof error.message === 'string' && error.message.toLowerCase().includes('timeout'))
    ) {
      return 'Yêu cầu AI đã vượt quá thời gian phản hồi cho phép. Vui lòng kiểm tra lại kết nối mạng hoặc thử lại thủ công.';
    }

    // 2. HTTP Status detection
    const status = error.response?.status;
    const serverMessage =
      (error.response?.data as { message?: string; error?: string })?.message ||
      (error.response?.data as { message?: string; error?: string })?.error;

    if (serverMessage && typeof serverMessage === 'string') {
      return serverMessage;
    }

    if (status === 401 || status === 403) {
      return 'Phiên làm việc đã hết hạn hoặc bạn không có quyền Quản trị viên để thực hiện thao tác AI này.';
    }
    if (status === 404) {
      return 'Không tìm thấy dữ liệu yêu cầu hoặc dịch vụ AI chưa được cấu hình tại đường dẫn này.';
    }
    if (status === 429) {
      return 'Hệ thống đang tiếp nhận nhiều yêu cầu. Vui lòng chờ 3-5 giây rồi thử lại.';
    }
    if (status && status >= 500) {
      return `Máy chủ Backend phản hồi lỗi (${status}). Dịch vụ AI hoặc cơ sở dữ liệu có thể đang khởi động hoặc chưa sẵn sàng.`;
    }

    if (error.message) {
      return error.message;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Đã xảy ra lỗi không xác định khi kết nối với máy chủ AI.';
}
