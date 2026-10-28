import { create } from 'zustand';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  title?: string;
  message: string;
  duration?: number;
}

interface ToastState {
  toasts: ToastItem[];
  showToast: (toast: Omit<ToastItem, 'id'>) => string;
  removeToast: (id: string) => void;
  clearAll: () => void;
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  showToast: (toast) => {
    const id = `toast_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    const duration = toast.duration ?? 4000;

    set((state) => ({
      toasts: [...state.toasts, { ...toast, id, duration }],
    }));

    if (duration > 0) {
      setTimeout(() => {
        set((state) => ({
          toasts: state.toasts.filter((t) => t.id !== id),
        }));
      }, duration);
    }

    return id;
  },
  removeToast: (id) =>
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    })),
  clearAll: () => set({ toasts: [] }),
}));

// Tiện ích gọi nhanh ở mọi nơi không cần hook
export const toast = {
  success: (message: string, title?: string, duration?: number) =>
    useToastStore.getState().showToast({ type: 'success', message, title, duration }),
  error: (message: string, title?: string, duration?: number) =>
    useToastStore.getState().showToast({ type: 'error', message, title, duration }),
  warning: (message: string, title?: string, duration?: number) =>
    useToastStore.getState().showToast({ type: 'warning', message, title, duration }),
  info: (message: string, title?: string, duration?: number) =>
    useToastStore.getState().showToast({ type: 'info', message, title, duration }),
};
