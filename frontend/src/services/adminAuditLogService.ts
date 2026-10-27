import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type { AdminAuditLogDto } from '../types/adminAuditLog';
import type { SpringPage } from '../types/adminUser';

export const adminAuditLogService = {
  /**
   * Fetch paginated audit logs from Backend
   */
  async getAuditLogs(page = 0, size = 20, signal?: AbortSignal): Promise<SpringPage<AdminAuditLogDto>> {
    const { data } = await apiClient.get<SpringPage<AdminAuditLogDto>>('/admin/audit-logs', {
      params: { page, size },
      signal,
    });
    return data;
  },

  /**
   * Fetch audit log detail by UUID
   */
  async getAuditLogDetail(id: string, signal?: AbortSignal): Promise<AdminAuditLogDto> {
    const { data } = await apiClient.get<AdminAuditLogDto>(`/admin/audit-logs/${encodeURIComponent(id)}`, {
      signal,
    });
    return data;
  },
};

export function getAdminAuditLogError(error: unknown) {
  const status = isAxiosError(error) ? error.response?.status : undefined;

  let message: string;
  if (status === 401) {
    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Admin.';
  } else if (status === 403) {
    message = 'Bạn không có quyền xem nhật ký kiểm toán. Cần tài khoản Admin.';
  } else if (status === 404) {
    message = 'Bản ghi nhật ký kiểm toán không tồn tại.';
  } else if (status === undefined) {
    message = isAxiosError(error)
      ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra kết nối mạng.'
      : 'Đã xảy ra lỗi không xác định khi tải nhật ký kiểm toán.';
  } else if (status >= 500) {
    message = 'Máy chủ đang gặp sự cố khi nạp nhật ký kiểm toán. Vui lòng thử lại sau.';
  } else {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Không thể xử lý yêu cầu nhật ký kiểm toán.';
  }

  return { message };
}
