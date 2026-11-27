import axios, { type AxiosRequestConfig } from 'axios';
import { apiClient } from './apiClient';
import { translateDisplayCodesToUuids, resolveDisplayCodeToUuid } from '../utils/displayCode';
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

import { AI_TIMEOUT_CONFIG, getAdminAiError } from './adminAiErrors';

export { AI_TIMEOUT_CONFIG, getAdminAiError };

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
    const effectivePayload: CopilotChatRequestDto = {
      ...payload,
      message: translateDisplayCodesToUuids(payload.message),
      contextId: payload.contextId ? resolveDisplayCodeToUuid(payload.contextId) : payload.contextId,
    };
    const response = await apiClient.post<CopilotChatResponse>(
      '/admin/ai/copilot/chat',
      effectivePayload,
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
