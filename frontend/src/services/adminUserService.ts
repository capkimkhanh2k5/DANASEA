import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminUserSummaryDto,
  AdminUserDetailDto,
  AdminUserQueryParams,
  SpringPage,
  LockUserRequest,
} from '../types/adminUser';

export const adminUserService = {
  /**
   * Fetch paginated users list with server-side filters
   */
  async getUsers(params: AdminUserQueryParams, signal?: AbortSignal): Promise<SpringPage<AdminUserSummaryDto>> {
    const queryParams: Record<string, string | number | boolean> = {};
    if (typeof params.page === 'number') queryParams.page = params.page;
    if (typeof params.size === 'number') queryParams.size = params.size;
    if (params.role) queryParams.role = params.role;
    if (typeof params.isLocked === 'boolean') queryParams.isLocked = params.isLocked;
    if (params.search && params.search.trim()) queryParams.search = params.search.trim();

    const { data } = await apiClient.get<SpringPage<AdminUserSummaryDto>>('/admin/users', {
      params: queryParams,
      signal,
    });
    return data;
  },

  /**
   * Fetch single user detail by UUID
   */
  async getUserDetail(id: string, signal?: AbortSignal): Promise<AdminUserDetailDto> {
    const { data } = await apiClient.get<AdminUserDetailDto>(`/admin/users/${encodeURIComponent(id)}`, {
      signal,
    });
    return data;
  },

  /**
   * Lock a user account with optional reason
   */
  async lockUser(id: string, reason?: string): Promise<void> {
    const payload: LockUserRequest = {};
    if (reason && reason.trim()) payload.reason = reason.trim();
    await apiClient.patch(`/admin/users/${encodeURIComponent(id)}/lock`, payload);
  },

  /**
   * Unlock a user account
   */
  async unlockUser(id: string): Promise<void> {
    await apiClient.patch(`/admin/users/${encodeURIComponent(id)}/unlock`);
  },
};

export function getAdminUserError(error: unknown, mutation = false) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  const outcomeUnknown = mutation && (status === undefined || status >= 500 || status === 408);
  if (outcomeUnknown) {
    return {
      outcomeUnknown,
      message: 'Chưa xác nhận được kết quả cập nhật tài khoản. Hãy tải lại danh sách trước khi thao tác tiếp; yêu cầu không được tự động gửi lại.',
    };
  }

  let message: string;
  if (status === 401) {
    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Admin.';
  } else if (status === 403) {
    message = 'Bạn không có quyền quản lý người dùng. Cần tài khoản Admin.';
  } else if (status === 404) {
    message = 'Tài khoản người dùng không tồn tại hoặc đã bị xóa.';
  } else if (status === undefined) {
    message = isAxiosError(error)
      ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra kết nối mạng.'
      : 'Đã xảy ra lỗi không xác định khi tải dữ liệu người dùng.';
  } else if (status >= 500) {
    message = 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.';
  } else {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Không thể xử lý yêu cầu quản lý người dùng.';
  }

  return { message, outcomeUnknown };
}
