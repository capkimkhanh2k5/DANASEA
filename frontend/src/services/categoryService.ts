import { apiClient } from './apiClient';
import type { CategoryDto } from '../types/category';
import { useLanguageStore } from '../store/useLanguageStore';

const cachedCategoriesByLang: Record<string, { data: CategoryDto[]; timestamp: number }> = {};
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

export const categoryService = {
  /**
   * Fetch active sea sports categories from Backend
   */
  async getCategories(forceRefresh: boolean = false): Promise<CategoryDto[]> {
    const currentLang = useLanguageStore.getState().language || 'vi';
    const now = Date.now();
    const cached = cachedCategoriesByLang[currentLang];
    if (!forceRefresh && cached && now - cached.timestamp < CACHE_TTL_MS) {
      console.log(`[categoryService] Trả về danh mục (${currentLang}) từ in-memory cache:`, cached.data.length, 'danh mục');
      return cached.data;
    }

    console.groupCollapsed(`%c[Category API] Lấy danh mục thể thao biển (${currentLang})...`, 'color: #00646f; font-weight: bold;');
    console.log('[Bước 1/3] Gửi yêu cầu GET /api/categories...');

    try {
      const response = await apiClient.get<CategoryDto[]>('/categories');
      const data = response.data || [];
      console.log(`[Bước 2/3] Nhận danh mục thành công từ Backend (${currentLang}):`, data.length, 'danh mục');
      
      cachedCategoriesByLang[currentLang] = { data, timestamp: now };
      console.log(`[Bước 3/3] Cache danh mục (${currentLang}) thành công (TTL: 10 phút)`);
      console.groupEnd();
      return data;
    } catch (err) {
      console.groupEnd();
      console.error('[categoryService] Lỗi khi nạp danh mục:', err);
      return cached?.data || [];
    }
  },
};
