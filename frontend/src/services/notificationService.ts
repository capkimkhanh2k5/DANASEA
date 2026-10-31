import { apiClient } from "./apiClient";
import type {
  NotificationResponse,
  UnreadCountResponse,
  MarkAllNotificationsReadResponse,
} from "../types/notification";

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const notificationService = {
  getMyNotifications: async (page = 0, size = 20): Promise<PageResponse<NotificationResponse>> => {
    const res = await apiClient.get<PageResponse<NotificationResponse>>(
      `/notifications?page=${page}&size=${size}`
    );
    return res.data;
  },

  getUnreadCount: async (): Promise<number> => {
    try {
      const res = await apiClient.get<UnreadCountResponse>("/notifications/unread-count");
      return res.data?.unreadCount ?? 0;
    } catch {
      return 0;
    }
  },

  markAsRead: async (id: string): Promise<NotificationResponse> => {
    const res = await apiClient.patch<NotificationResponse>(`/notifications/${id}/read`);
    return res.data;
  },

  markAllAsRead: async (): Promise<MarkAllNotificationsReadResponse> => {
    const res = await apiClient.patch<MarkAllNotificationsReadResponse>("/notifications/read-all");
    return res.data;
  },
};
