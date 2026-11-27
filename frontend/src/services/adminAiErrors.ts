import axios from 'axios';

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

/**
 * Extracts a clear, user-friendly error message from an API error.
 * Explicitly surfaces timeouts, authorization errors, rate-limiting, and backend 5xx status.
 */
export function getAdminAiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (
      error.code === 'ECONNABORTED' ||
      (typeof error.message === 'string' && error.message.toLowerCase().includes('timeout'))
    ) {
      return 'Yêu cầu AI đã vượt quá thời gian phản hồi cho phép. Vui lòng kiểm tra lại kết nối mạng hoặc thử lại thủ công.';
    }

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
