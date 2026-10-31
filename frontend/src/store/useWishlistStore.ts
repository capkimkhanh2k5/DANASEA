import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { wishlistService } from '../services/wishlistService';
import { useAuthStore } from './useAuthStore';
import type { WishlistItemDto } from '../types/wishlist';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface WishlistState {
  wishlistIds: string[];
  serverItems: WishlistItemDto[];
  isLoading: boolean;
  toastMessage: string | null;
  toastTimestamp: number;
  toggleWishlist: (serviceId: string, serviceName?: string) => boolean;
  isInWishlist: (serviceId: string) => boolean;
  removeFromWishlist: (serviceId: string, serviceName?: string) => void;
  fetchWishlistFromServer: () => Promise<void>;
  clearToast: () => void;
}

export const useWishlistStore = create<WishlistState>()(
  persist(
    (set, get) => ({
      wishlistIds: [],
      serverItems: [],
      isLoading: false,
      toastMessage: null,
      toastTimestamp: 0,

      isInWishlist: (serviceId: string) => {
        return get().wishlistIds.includes(serviceId);
      },

      fetchWishlistFromServer: async () => {
        const { isAuthenticated } = useAuthStore.getState();
        if (!isAuthenticated) return;

        set({ isLoading: true });
        try {
          const items = await wishlistService.getWishlists();
          const serverIds = items.map((i) => i.serviceId);
          // Merge server IDs with local IDs to avoid losing state
          const merged = Array.from(new Set([...get().wishlistIds, ...serverIds]));
          set({
            serverItems: items,
            wishlistIds: merged,
            isLoading: false,
          });
        } catch (err) {
          console.warn('[useWishlistStore] Không thể đồng bộ từ máy chủ:', err);
          set({ isLoading: false });
        }
      },

      toggleWishlist: (serviceId: string, serviceName?: string) => {
        const current = get().wishlistIds;
        const exists = current.includes(serviceId);
        const { isAuthenticated } = useAuthStore.getState();
        const isUuid = UUID_REGEX.test(serviceId);

        if (exists) {
          // Optimistic remove
          set({
            wishlistIds: current.filter((id) => id !== serviceId),
            serverItems: get().serverItems.filter((item) => item.serviceId !== serviceId),
            toastMessage: serviceName ? `Đã bỏ lưu "${serviceName}" khỏi yêu thích` : 'Đã bỏ lưu khỏi danh sách yêu thích!',
            toastTimestamp: Date.now(),
          });

          // Sync with server in background
          if (isAuthenticated && isUuid) {
            wishlistService.removeFromWishlist(serviceId).catch((err) => {
              console.warn('[useWishlistStore] Lỗi đồng bộ xóa wishlist:', err);
            });
          }
          return false;
        } else {
          // Optimistic add
          set({
            wishlistIds: [...current, serviceId],
            toastMessage: serviceName ? `Đã lưu "${serviceName}" vào yêu thích` : 'Đã lưu vào danh sách yêu thích!',
            toastTimestamp: Date.now(),
          });

          // Sync with server in background
          if (isAuthenticated && isUuid) {
            wishlistService.addToWishlist(serviceId).catch((err) => {
              console.warn('[useWishlistStore] Lỗi đồng bộ thêm wishlist:', err);
            });
          }
          return true;
        }
      },

      removeFromWishlist: (serviceId: string, serviceName?: string) => {
        const current = get().wishlistIds;
        const { isAuthenticated } = useAuthStore.getState();
        const isUuid = UUID_REGEX.test(serviceId);

        set({
          wishlistIds: current.filter((id) => id !== serviceId),
          serverItems: get().serverItems.filter((item) => item.serviceId !== serviceId),
          toastMessage: serviceName ? `Đã bỏ lưu "${serviceName}" khỏi yêu thích` : 'Đã bỏ lưu khỏi danh sách yêu thích!',
          toastTimestamp: Date.now(),
        });

        if (isAuthenticated && isUuid) {
          wishlistService.removeFromWishlist(serviceId).catch((err) => {
            console.warn('[useWishlistStore] Lỗi đồng bộ xóa wishlist:', err);
          });
        }
      },

      clearToast: () => set({ toastMessage: null }),
    }),
    {
      name: 'danasea_wishlist_storage',
      partialize: (state) => ({ wishlistIds: state.wishlistIds }),
    }
  )
);
