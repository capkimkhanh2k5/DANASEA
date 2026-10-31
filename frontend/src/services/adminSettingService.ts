import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type {
  CategorySafetyRuleResponse,
  UpdateCategorySafetyRuleRequest,
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
};

export function getAdminSettingError(error: unknown, mutation = false) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  const outcomeUnknown = mutation && (status === undefined || status >= 500 || status === 408);
  if (outcomeUnknown) {
    return {
      outcomeUnknown,
      message: 'Chưa xác nhận được kết quả cập nhật tham số an toàn do mất kết nối hoặc quá thời gian chờ (Timeout). Hãy làm mới dữ liệu trước khi thao tác tiếp.',
    };
  }

  let message: string;
  if (status === 401) {
    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Admin.';
  } else if (status === 403) {
    message = 'Bạn không có quyền quản lý cài đặt hệ thống. Cần tài khoản Admin.';
  } else if (status === 404) {
    message = 'Không tìm thấy danh mục hoặc quy tắc an toàn hàng hải.';
  } else if (status === 400 || status === 422) {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Tham số định mức an toàn không hợp lệ (giá trị phải lớn hơn hoặc bằng 0).';
  } else if (status === undefined) {
    message = isAxiosError(error)
      ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra kết nối mạng.'
      : 'Đã xảy ra lỗi không xác định khi tải dữ liệu cài đặt.';
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
