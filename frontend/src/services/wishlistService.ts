import { apiClient } from './apiClient';
import type { WishlistItemDto } from '../types/wishlist';

export const wishlistService = {
  /**
   * Lấy danh sách dịch vụ yêu thích của người dùng hiện tại từ Backend
   * GET /api/wishlists
   */
  async getWishlists(): Promise<WishlistItemDto[]> {
    try {
      const response = await apiClient.get<WishlistItemDto[]>('/wishlists');
      return response.data || [];
    } catch (err) {
      console.warn('[wishlistService] Không thể tải wishlist từ server:', err);
      return [];
    }
  },

  /**
   * Thêm dịch vụ vào danh sách yêu thích
   * POST /api/wishlists/{serviceId}
   */
  async addToWishlist(serviceId: string): Promise<void> {
    await apiClient.post(`/wishlists/${encodeURIComponent(serviceId)}`);
  },

  /**
   * Xóa dịch vụ khỏi danh sách yêu thích
   * DELETE /api/wishlists/{serviceId}
   */
  async removeFromWishlist(serviceId: string): Promise<void> {
    await apiClient.delete(`/wishlists/${encodeURIComponent(serviceId)}`);
  },
};
