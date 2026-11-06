import { apiClient } from './apiClient';

export interface MessageItem {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  attachmentUrl?: string | null;
  isRead: boolean;
  createdAt: string;
  sequence?: number | null;
}

export interface ConversationItem {
  id: string;
  masterOrderId?: string | null;
  customerId: string;
  vendorId: string;
  createdAt: string;
  updatedAt: string;
  lastMessage?: MessageItem | null;
  unreadCount?: number;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  empty?: boolean;
}

const vendorCache = new Map<string, { businessName: string; address?: string }>();

export const communicationService = {
  /**
   * Fetch current user's conversations (Customer or Vendor)
   */
  async getConversations(page = 0, size = 50): Promise<SpringPage<ConversationItem>> {
    const response = await apiClient.get<SpringPage<ConversationItem>>('/conversations', {
      params: { page, size, sort: 'updatedAt,desc' },
    });
    return response.data;
  },

  /**
   * Fetch message history in a conversation
   */
  async getMessages(conversationId: string): Promise<MessageItem[]> {
    const response = await apiClient.get<MessageItem[]>(`/conversations/${conversationId}/messages`);
    return response.data || [];
  },

  /**
   * Send a new message into a conversation
   */
  async sendMessage(conversationId: string, content: string, attachmentUrl?: string): Promise<MessageItem> {
    const response = await apiClient.post<MessageItem>(`/conversations/${conversationId}/messages`, {
      content,
      attachmentUrl: attachmentUrl || null,
    });
    return response.data;
  },

  /**
   * Create or open an existing conversation for an order
   */
  async createOrGetConversation(masterOrderId: string, vendorId?: string): Promise<ConversationItem> {
    const response = await apiClient.post<ConversationItem>('/conversations', {
      masterOrderId,
      vendorId: vendorId || null,
    });
    return response.data;
  },

  /**
   * Get vendor info by vendorId (cached)
   */
  async getVendorInfo(vendorId: string): Promise<{ businessName: string; address?: string }> {
    if (vendorCache.has(vendorId)) {
      return vendorCache.get(vendorId)!;
    }
    try {
      const res = await apiClient.get<{ businessName?: string; address?: string }>(`/vendors/${vendorId}`);
      const info = {
        businessName: res.data?.businessName || 'Nhà điều hành đối tác',
        address: res.data?.address || undefined,
      };
      vendorCache.set(vendorId, info);
      return info;
    } catch {
      const fallback = { businessName: 'Nhà điều hành tour biển' };
      vendorCache.set(vendorId, fallback);
      return fallback;
    }
  },
};
