import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type {
  CategorySafetyRuleResponse,
  CommissionRuleDto,
  SystemConfigDto,
  UpdateCategorySafetyRuleRequest,
  UpdateCommissionRuleRequest,
  UpdateSystemConfigRequest,
} from '../types/adminSetting';

export const adminSettingService = {
  // Lấy danh sách định mức an toàn hàng hải theo danh mục dịch vụ
  async getCategorySafetyRules(signal?: AbortSignal): Promise<CategorySafetyRuleResponse[]> {
    const { data } = await apiClient.get<CategorySafetyRuleResponse[]>('/admin/category-safety-rules', {
      signal,
    });
    return data;
  },

  // Cập nhật ngưỡng an toàn thời tiết hàng hải cho một danh mục cụ thể
  async updateCategorySafetyRule(
    categoryId: string,
    payload: UpdateCategorySafetyRuleRequest
  ): Promise<CategorySafetyRuleResponse> {
    const { data } = await apiClient.patch<CategorySafetyRuleResponse>(
      `/admin/category-safety-rules/${encodeURIComponent(categoryId)}`,
      payload
    );
    return data;
  },

  // Lấy danh sách 13 quy tắc hoa hồng động (1 Onboarding + 12 Category)
  async getCommissionRules(signal?: AbortSignal): Promise<CommissionRuleDto[]> {
    const { data } = await apiClient.get<CommissionRuleDto[]>('/admin/settings/commission-rules', {
      signal,
    });
    return data;
  },

  // Cập nhật quy tắc hoa hồng theo ID
  async updateCommissionRule(
    id: string,
    payload: UpdateCommissionRuleRequest
  ): Promise<CommissionRuleDto> {
    const { data } = await apiClient.put<CommissionRuleDto>(
      `/admin/settings/commission-rules/${encodeURIComponent(id)}`,
      payload
    );
    return data;
  },

  // Lấy toàn bộ danh sách cấu hình hệ thống (system_configs)
  async getSystemConfigs(signal?: AbortSignal): Promise<SystemConfigDto[]> {
    const { data } = await apiClient.get<SystemConfigDto[]>('/admin/settings', {
      signal,
    });
    return data;
  },

  // Lấy chi tiết một cấu hình hệ thống theo Key
  async getSystemConfig(key: string, signal?: AbortSignal): Promise<SystemConfigDto> {
    const { data } = await apiClient.get<SystemConfigDto>(
      `/admin/settings/${encodeURIComponent(key)}`,
      { signal }
    );
    return data;
  },

  // Cập nhật cấu hình hệ thống theo Key (PLATFORM_COMMISSION_RATE, SAFETY_REBATE_*, ...)
  async updateSystemConfig(
    key: string,
    payload: UpdateSystemConfigRequest
  ): Promise<SystemConfigDto> {
    const { data } = await apiClient.put<SystemConfigDto>(
      `/admin/settings/${encodeURIComponent(key)}`,
      payload
    );
    return data;
  },
};

export function getAdminSettingError(error: unknown, mutation = false) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  const outcomeUnknown = mutation && (status === undefined || status >= 500 || status === 408);
  if (outcomeUnknown) {
    return {
      outcomeUnknown,
      message: 'Chưa xác nhận được kết quả cập nhật do mất kết nối hoặc quá thời gian chờ (Timeout). Hãy làm mới dữ liệu trước khi thao tác tiếp.',
    };
  }

  let message: string;
  if (status === 401) {
    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Quản trị viên (Admin).';
  } else if (status === 403) {
    message = 'Bạn không có quyền quản lý cài đặt hệ thống. Yêu cầu quyền ROLE_ADMIN.';
  } else if (status === 404) {
    message = 'Không tìm thấy thông tin cấu hình hoặc quy tắc hoa hồng yêu cầu.';
  } else if (status === 400 || status === 422) {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Tham số cấu hình hoặc tỷ lệ hoa hồng không hợp lệ (giá trị phải từ 0% đến 100%).';
  } else if (status === undefined) {
    message = isAxiosError(error)
      ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra kết nối mạng.'
      : 'Đã xảy ra lỗi không xác định khi tải dữ liệu cấu hình hệ thống.';
  } else if (status >= 500) {
    message = 'Máy chủ đang gặp sự cố khi xử lý tham số. Vui lòng thử lại sau.';
  } else {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Không thể xử lý yêu cầu cài đặt hệ thống.';
  }

  return { message, outcomeUnknown };
}
