import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type {
  AdminCategoryDto,
  AdminCategoryResponse,
  AdminCategoryTreeDto,
  CreateAdminCategoryRequest,
  UpdateAdminCategoryRequest,
} from '../types/adminCategory';

function requireCategory(value: AdminCategoryDto): void {
  if (!value || typeof value.id !== 'string' || !value.id ||
      typeof value.name !== 'string' || typeof value.slug !== 'string' ||
      typeof value.isActive !== 'boolean' ||
      !(value.nameEn === null || typeof value.nameEn === 'string') ||
      !(value.parentId === null || typeof value.parentId === 'string') ||
      !(value.iconUrl === null || typeof value.iconUrl === 'string')) {
    throw new Error('Phản hồi danh mục không đúng hợp đồng Backend.');
  }
}

export const adminCategoryService = {
  async getCategories(signal: AbortSignal): Promise<AdminCategoryDto[]> {
    const { data } = await apiClient.get<AdminCategoryTreeDto[]>('/admin/categories', { signal });
    const categories: AdminCategoryDto[] = [];
    const seen = new Set<string>();
    const visit = (nodes: AdminCategoryTreeDto[]) => {
      if (!Array.isArray(nodes)) throw new Error('Phản hồi cây danh mục không hợp lệ.');
      for (const node of nodes) {
        requireCategory(node);
        if (seen.has(node.id)) throw new Error('Cây danh mục chứa ID trùng lặp.');
        seen.add(node.id);
        const { children, ...category } = node;
        // Preserve the server parentId, including orphan nodes returned as roots.
        categories.push(category);
        visit(children);
      }
    };
    visit(data);
    return categories;
  },

  async createCategory(body: CreateAdminCategoryRequest): Promise<AdminCategoryResponse> {
    const { data } = await apiClient.post<AdminCategoryResponse>('/admin/categories', body);
    requireCategory(data);
    if (!data.isActive || Object.entries(body).some(([key, value]) => data[key as keyof CreateAdminCategoryRequest] !== value)) {
      throw new Error('Backend chưa xác nhận dữ liệu tạo danh mục.');
    }
    return data;
  },

  async updateCategory(id: string, body: UpdateAdminCategoryRequest): Promise<AdminCategoryResponse> {
    const { data } = await apiClient.patch<AdminCategoryResponse>(`/admin/categories/${encodeURIComponent(id)}`, body);
    requireCategory(data);
    if (data.id !== id || Object.entries(body).some(([key, value]) => data[key as keyof UpdateAdminCategoryRequest] !== value)) {
      throw new Error('Backend chưa xác nhận dữ liệu cập nhật danh mục.');
    }
    return data;
  },

  async deactivateCategory(id: string): Promise<AdminCategoryResponse> {
    const { data } = await apiClient.patch<AdminCategoryResponse>(`/admin/categories/${encodeURIComponent(id)}/deactivate`);
    requireCategory(data);
    if (data.id !== id || data.isActive) throw new Error('Backend chưa xác nhận tạm ẩn danh mục.');
    return data;
  },
};

export function getAdminCategoryError(error: unknown, mutation = false) {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  // A timeout, lost response, server error or malformed success body may follow a committed write.
  const outcomeUnknown = mutation && (status === undefined || status >= 500 || status === 408);
  if (outcomeUnknown) {
    return {
      outcomeUnknown,
      message: 'Chưa xác nhận được kết quả ghi dữ liệu. Hãy tải lại danh mục và kiểm tra trước khi thao tác tiếp; yêu cầu không được tự động gửi lại.',
    };
  }
  let message: string;
  if (status === 401) message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại bằng tài khoản Admin.';
  else if (status === 403) message = 'Bạn không có quyền quản lý danh mục. Cần tài khoản Admin.';
  else if (status === 404) message = 'Danh mục hoặc danh mục cha không còn tồn tại. Hãy tải lại danh sách.';
  else if (status === undefined) message = isAxiosError(error)
    ? 'Không thể kết nối máy chủ hoặc yêu cầu đã hết thời gian chờ. Hãy kiểm tra mạng và tải lại danh mục.'
    : 'Phản hồi danh mục không đúng hợp đồng Backend. Hãy tải lại danh mục.';
  else if (status >= 500) message = 'Máy chủ đang gặp lỗi. Hãy tải lại danh mục sau.';
  else {
    const data = isAxiosError<{ code?: string; message?: string }>(error) ? error.response?.data : undefined;
    const messages: Record<string, string> = {
      SLUG_ALREADY_EXISTS: 'Slug đã được sử dụng. Vui lòng chọn slug khác.',
      CATEGORY_HAS_ACTIVE_SERVICES: 'Không thể tạm ẩn danh mục đang có dịch vụ PUBLISHED.',
      CATEGORY_HIERARCHY_LOOP: 'Danh mục cha không được là chính nó hoặc danh mục con của nó.',
      CATEGORY_NOT_FOUND: 'Danh mục hoặc danh mục cha không còn tồn tại. Hãy tải lại danh sách.',
    };
    message = messages[data?.code ?? ''] ||
      (typeof data?.message === 'string' ? data.message : 'Không thể xử lý danh mục. Vui lòng kiểm tra dữ liệu.');
  }
  return { message, outcomeUnknown };
}
