import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type {
  DiscountCodeResponse,
  CreateDiscountCodeRequest,
  UpdateDiscountCodeRequest,
  GetDiscountCodesParams,
  PageDiscountCodeResponse,
} from '../types/adminDiscountCode';

export const adminDiscountCodeService = {
  // Lấy danh sách mã giảm giá phân trang và lọc theo điều kiện
  async getDiscountCodes(params: GetDiscountCodesParams = {}, signal?: AbortSignal): Promise<PageDiscountCodeResponse> {
    const queryParams: Record<string, string | number | boolean> = {};
    if (typeof params.page === 'number') queryParams.page = params.page;
    if (typeof params.size === 'number') queryParams.size = params.size;
    if (params.sort) queryParams.sort = params.sort;
    if (params.code && params.code.trim()) queryParams.code = params.code.trim();
    if (params.scope) queryParams.scope = params.scope;
    if (params.vendorId) queryParams.vendorId = params.vendorId;
    if (typeof params.isActive === 'boolean') queryParams.isActive = params.isActive;

    const { data } = await apiClient.get<PageDiscountCodeResponse>('/admin/discount-codes', {
      params: queryParams,
      signal,
    });
    return data;
  },

  // Lấy chi tiết một mã giảm giá theo UUID
  async getDiscountCodeById(id: string, signal?: AbortSignal): Promise<DiscountCodeResponse> {
    const { data } = await apiClient.get<DiscountCodeResponse>(`/admin/discount-codes/${encodeURIComponent(id)}`, {
      signal,
    });
    return data;
  },

  // Tạo mới mã giảm giá toàn sàn hoặc đối tác
  async createDiscountCode(payload: CreateDiscountCodeRequest): Promise<DiscountCodeResponse> {
    const { data } = await apiClient.post<DiscountCodeResponse>('/admin/discount-codes', payload);
    return data;
  },

  // Cập nhật cấu hình hoặc trạng thái mã giảm giá
  async updateDiscountCode(id: string, payload: UpdateDiscountCodeRequest): Promise<DiscountCodeResponse> {
    const { data } = await apiClient.patch<DiscountCodeResponse>(`/admin/discount-codes/${encodeURIComponent(id)}`, payload);
    return data;
  },
};

export function getAdminDiscountError(error: unknown, mutation = false) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  const outcomeUnknown = mutation && (status === undefined || status >= 500 || status === 408);
  if (outcomeUnknown) {
    return {
      outcomeUnknown,
      message: 'Chưa xác nhận được kết quả cập nhật mã ưu đãi do mất kết nối hoặc quá thời gian chờ (Timeout). Hãy làm mới dữ liệu trước khi thao tác tiếp.',
    };
  }

  let message: string;
  if (status === 401) {
    message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Admin.';
  } else if (status === 403) {
    message = 'Bạn không có quyền quản lý khuyến mãi. Cần tài khoản Admin.';
  } else if (status === 404) {
    message = 'Mã ưu đãi không tồn tại hoặc đã bị gỡ bỏ.';
  } else if (status === 400 || status === 422) {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Thông tin cấu hình mã ưu đãi không hợp lệ.';
  } else if (status === undefined) {
    message = isAxiosError(error)
      ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra kết nối mạng.'
      : 'Đã xảy ra lỗi không xác định khi tải dữ liệu khuyến mãi.';
  } else if (status >= 500) {
    message = 'Máy chủ đang gặp sự cố khi xử lý khuyến mãi. Vui lòng thử lại sau.';
  } else {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    message = typeof data?.message === 'string' && data.message.trim()
      ? data.message
      : 'Không thể xử lý yêu cầu quản lý khuyến mãi.';
  }

  return { message, outcomeUnknown };
}
