import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type { AdminAuditLogDto } from '../types/adminAuditLog';
import type { SpringPage } from '../types/adminUser';

export interface AdminAuditLogFilters {
  action?: string;
  entityType?: string;
  search?: string;
}

export const adminAuditLogService = {
  /**
   * Fetch paginated audit logs from Backend with optional server-side filters
   */
  async getAuditLogs(
    page = 0,
    size = 20,
    filters?: AdminAuditLogFilters,
    signal?: AbortSignal
  ): Promise<SpringPage<AdminAuditLogDto>> {
    const params: Record<string, string | number> = { page, size };
    if (filters?.action && filters.action !== 'ALL' && filters.action.trim()) {
      params.action = filters.action.trim();
    }
    if (filters?.entityType && filters.entityType !== 'ALL' && filters.entityType.trim()) {
      params.entityType = filters.entityType.trim();
    }
    if (filters?.search && filters.search.trim()) {
      params.search = filters.search.trim();
    }

    const { data } = await apiClient.get<SpringPage<AdminAuditLogDto>>('/admin/audit-logs', {
      params,
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
