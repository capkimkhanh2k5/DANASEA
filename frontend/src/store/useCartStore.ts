import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface CartItem {
  id: string; // unique cart item id
  serviceId: string;
  slotId: string;
  optionId?: string;
  quantity: number;
  unitPrice: number;
  // Denormalized data for UI display
  serviceName: string;
  serviceNameEn?: string;
  thumbnailUrl: string;
  date: string;
  timeSlot: string;
  selectedDate?: string;
  locationName?: string;
  vendorName?: string;
}

export function isCartItemExpired(item: { date?: string; selectedDate?: string; timeSlot?: string }): boolean {
  const dateStr = item.selectedDate || item.date;
  if (!dateStr) return false;

  let year: number;
  let month: number;
  let day: number;

  if (/^\d{4}-\d{2}-\d{2}/.test(dateStr)) {
    const parts = dateStr.slice(0, 10).split('-').map(Number);
    year = parts[0];
    month = parts[1] - 1;
    day = parts[2];
  } else if (/^\d{2}\/\d{2}\/\d{4}/.test(dateStr)) {
    const parts = dateStr.slice(0, 10).split('/').map(Number);
    day = parts[0];
    month = parts[1] - 1;
    year = parts[2];
  } else {
    const parsed = new Date(dateStr);
    if (isNaN(parsed.getTime())) return false;
    year = parsed.getFullYear();
    month = parsed.getMonth();
    day = parsed.getDate();
  }

  // Parse start time (e.g. "05:30:00 - 07:30:00" -> 05:30)
  let hour = 23;
  let minute = 59;
  if (item.timeSlot) {
    const match = item.timeSlot.match(/^(\d{1,2}):(\d{2})/);
    if (match) {
      hour = parseInt(match[1], 10);
      minute = parseInt(match[2], 10);
    }
  }

  const slotStart = new Date(year, month, day, hour, minute, 0, 0);
  return slotStart.getTime() <= Date.now();
}

interface CartState {
  items: CartItem[];
  addItem: (item: Omit<CartItem, 'id'>) => void;
  updateCartItem: (id: string, updatedData: Partial<Omit<CartItem, 'id'>>) => void;
  removeItem: (id: string) => void;
  removeExpiredItems: () => void;
  updateQuantity: (id: string, quantity: number) => void;
  clearCart: () => void;
  
  // Computed (getters could be here or just use standard selectors)
  getTotalItems: () => number;
  getTotalPrice: () => number;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const isLegacyMockId = (id?: string) => !id || !UUID_REGEX.test(id) || id.startsWith('d0000001');

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      
      addItem: (itemData) => set((state) => {
        // Guardrail: reject legacy non-UUID items from polluting the cart
        if (isLegacyMockId(itemData.serviceId) || isLegacyMockId(itemData.slotId)) {
          console.warn('[useCartStore] Bỏ qua thêm item vì ID không phải UUID chuẩn:', itemData);
          return state;
        }

        // Check if item already exists (same service & slot)
        const existingItem = state.items.find(
          (item) => item.serviceId === itemData.serviceId && item.slotId === itemData.slotId
        );
        
        if (existingItem) {
          return {
            items: state.items.map((item) =>
              item.id === existingItem.id
                ? { ...item, quantity: item.quantity + itemData.quantity }
                : item
            )
          };
        }
        
        // Create new item
        const newItem: CartItem = {
          ...itemData,
          id: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 11),
        };
        return { items: [...state.items, newItem] };
      }),
      
      updateCartItem: (id, updatedData) => set((state) => ({
        items: state.items.map((item) =>
          item.id === id ? { ...item, ...updatedData } : item
        )
      })),
      
      removeItem: (id) => set((state) => ({
        items: state.items.filter((item) => item.id !== id)
      })),

      removeExpiredItems: () => set((state) => ({
        items: state.items.filter((item) => !isCartItemExpired(item))
      })),
      
      updateQuantity: (id, quantity) => set((state) => ({
        items: state.items.map((item) => 
          item.id === id ? { ...item, quantity: Math.max(1, quantity) } : item
        )
      })),
      
      clearCart: () => set({ items: [] }),
      
      getTotalItems: () => {
        return get().items.reduce((total, item) => total + item.quantity, 0);
      },
      
      getTotalPrice: () => {
        return get().items.reduce((total, item) => total + (item.unitPrice * item.quantity), 0);
      }
    }),
    {
      name: 'danasea_cart_storage',
      onRehydrateStorage: () => (state) => {
        if (state && Array.isArray(state.items)) {
          // Auto-clean any legacy mock items stored in user's browser localStorage
          const cleanItems = state.items.filter(
            (i) => !isLegacyMockId(i.serviceId) && !isLegacyMockId(i.slotId)
          );
          if (cleanItems.length !== state.items.length) {
            console.log(`[useCartStore] Đã tự động dọn dẹp ${state.items.length - cleanItems.length} item mock cũ khỏi giỏ hàng.`);
            state.items = cleanItems;
          }
        }
      },
    }
  )
);
