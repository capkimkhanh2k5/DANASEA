export interface NotificationResponse {
  id: string;
  userId: string;
  title: string;
  body: string;
  type: string;
  status: string;
  channel: string;
  locale: string;
  isRead: boolean;
  readAt: string | null;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface MarkAllNotificationsReadResponse {
  updatedCount: number;
}
