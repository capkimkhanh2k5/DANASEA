import { create } from 'zustand';
import { aiService, type AssistantCard } from '../services/aiService';
import { useAuthStore } from './useAuthStore';
import { useLanguageStore } from './useLanguageStore';
import {
  GUEST_FREE_CHAT_LIMIT,
  clearGuestAiThread,
  getGuestChatCount,
  getRemainingGuestTurns,
  incrementGuestChatCount,
  loadGuestAiThread,
  mapHistoryMessagesToChatMessages,
  resetGuestChatCount,
  saveGuestAiThread,
  setGuestChatCount,
} from './guestAiQuota';

export interface AiChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
  cards?: AssistantCard[];
  responseStatus?: string;
  requiresLogin?: boolean;
}

interface AiModalState {
  isOpen: boolean;
  conversationId?: string;
  conversationWasGuest?: boolean;
  userClearedThread?: boolean;
  messages: AiChatMessage[];
  isGenerating: boolean;
  guestRemainingTurns: number;
  openAiModal: (initialPrompt?: string) => void;
  closeAiModal: () => void;
  toggleAiModal: () => void;
  sendMessage: (prompt: string) => Promise<void>;
  prepareGuestLoginRedirect: () => void;
  syncAfterLogin: () => Promise<void>;
  resetForLogout: () => void;
  clearHistory: () => void;
}

const INITIAL_AI_MESSAGE: AiChatMessage = {
  id: 'welcome-msg',
  role: 'ai',
  content: 'Hệ thống hỗ trợ tra cứu điều kiện hải văn, cập nhật thời tiết và tư vấn đặt dịch vụ biển tại Đà Nẵng. Bạn cần tìm kiếm thông tin nào?',
  timestamp: Date.now(),
};

const initialGuestThread = typeof window !== 'undefined' ? loadGuestAiThread() : null;

export const useAiModalStore = create<AiModalState>((set, get) => ({
  isOpen: false,
  conversationId: initialGuestThread?.conversationId,
  conversationWasGuest: Boolean(initialGuestThread && (initialGuestThread.conversationId || initialGuestThread.messages.length > 0)),
  userClearedThread: false,
  messages: initialGuestThread && initialGuestThread.messages.length > 0
    ? (initialGuestThread.messages as AiChatMessage[])
    : [INITIAL_AI_MESSAGE],
  isGenerating: false,
  guestRemainingTurns: getRemainingGuestTurns(),

  openAiModal: (initialPrompt?: string) => {
    set({ isOpen: true, guestRemainingTurns: getRemainingGuestTurns() });
    if (initialPrompt && initialPrompt.trim()) {
      void get().sendMessage(initialPrompt.trim());
    }
  },

  closeAiModal: () => set({ isOpen: false }),

  toggleAiModal: () => set((state) => ({ isOpen: !state.isOpen })),

  prepareGuestLoginRedirect: () => {
    const state = get();
    saveGuestAiThread({
      conversationId: state.conversationId,
      messages: state.messages,
      reopenAfterLogin: true,
    });
    set({ isOpen: false });
  },

  syncAfterLogin: async () => {
    const { isAuthenticated, accessToken, user } = useAuthStore.getState();
    if (!isAuthenticated || !accessToken) return;

    const saved = loadGuestAiThread();
    const state = get();
    const guestConvId = (state.conversationWasGuest ? state.conversationId : undefined) || saved?.conversationId;
    const shouldReopen = Boolean(saved?.reopenAfterLogin || state.isOpen);

    if (guestConvId) {
      let hydratedMessages: AiChatMessage[] = [];
      try {
        await aiService.claimConversation(guestConvId);
        const history = await aiService.getConversationHistory(guestConvId, 40);
        hydratedMessages = mapHistoryMessagesToChatMessages(history) as AiChatMessage[];
      } catch (err) {
        console.warn('[AI Chat] Failed to claim or fetch guest conversation after login:', err);
      }
      const fallbackMessages = (saved?.messages && saved.messages.length > 0 ? saved.messages : state.messages)
        .filter((msg) => !(msg.requiresLogin && msg.id.startsWith('ai-err-')))
        .map((msg) => ({ ...msg, requiresLogin: false })) as AiChatMessage[];

      clearGuestAiThread();
      resetGuestChatCount();
      set({
        conversationId: guestConvId,
        conversationWasGuest: false,
        userClearedThread: false,
        messages: hydratedMessages.length > 0 ? hydratedMessages : fallbackMessages,
        guestRemainingTurns: GUEST_FREE_CHAT_LIMIT,
        isOpen: shouldReopen,
      });
      return;
    }

    if (saved) {
      const cleanedSaved = saved.messages
        .filter((msg) => !(msg.requiresLogin && msg.id.startsWith('ai-err-')))
        .map((msg) => ({ ...msg, requiresLogin: false })) as AiChatMessage[];
      clearGuestAiThread();
      resetGuestChatCount();
      if (cleanedSaved.length > 0) {
        set({
          conversationWasGuest: false,
          messages: cleanedSaved,
          guestRemainingTurns: GUEST_FREE_CHAT_LIMIT,
          isOpen: shouldReopen,
        });
      }
      return;
    }

    resetGuestChatCount();
    if (!state.conversationId && !state.userClearedThread && state.messages.length <= 1 && user?.role === 'CUSTOMER') {
      try {
        const conversations = await aiService.listConversations(1);
        const latest = conversations[0];
        if (latest?.id) {
          const history = await aiService.getConversationHistory(latest.id, 40);
          const mapped = mapHistoryMessagesToChatMessages(history) as AiChatMessage[];
          if (mapped.length > 0) {
            set({
              conversationId: latest.id,
              conversationWasGuest: false,
              messages: mapped,
              guestRemainingTurns: GUEST_FREE_CHAT_LIMIT,
            });
          }
        }
      } catch (err) {
        console.warn('[AI Chat] Could not restore recent customer conversation:', err);
      }
    }
  },

  resetForLogout: () => {
    clearGuestAiThread();
    set({
      isOpen: false,
      conversationId: undefined,
      conversationWasGuest: false,
      userClearedThread: false,
      messages: [{ ...INITIAL_AI_MESSAGE, timestamp: Date.now() }],
      isGenerating: false,
      guestRemainingTurns: getRemainingGuestTurns(),
    });
  },

  sendMessage: async (prompt: string) => {
    if (!prompt.trim() || get().isGenerating) return;

    const userMessage: AiChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: prompt.trim(),
      timestamp: Date.now(),
    };

    const { isAuthenticated, accessToken } = useAuthStore.getState();
    const isGuest = !isAuthenticated || !accessToken;
    const isEn = useLanguageStore.getState().language === 'en';
    const activeConversationId = get().conversationId;

    if (isGuest && getGuestChatCount() >= GUEST_FREE_CHAT_LIMIT && !activeConversationId) {
      const limitNotice: AiChatMessage = {
        id: `ai-err-${Date.now()}`,
        role: 'ai',
        content: isEn
          ? `You have used all ${GUEST_FREE_CHAT_LIMIT} free messages. Please sign in to continue this conversation.`
          : `Bạn đã sử dụng hết ${GUEST_FREE_CHAT_LIMIT} lượt hỏi miễn phí. Vui lòng đăng nhập để tiếp tục cuộc trò chuyện này.`,
        requiresLogin: true,
        timestamp: Date.now(),
      };
      set((state) => {
        const nextMessages = [...state.messages, userMessage, limitNotice];
        saveGuestAiThread({ conversationId: state.conversationId, messages: nextMessages });
        return {
          messages: nextMessages,
          guestRemainingTurns: 0,
          isGenerating: false,
        };
      });
      return;
    }

    set((state) => ({
      messages: [...state.messages, userMessage],
      conversationWasGuest: isGuest ? true : state.conversationWasGuest,
      userClearedThread: false,
      isGenerating: true,
    }));

    try {
      const response = await aiService.chat(prompt.trim(), activeConversationId);
      let nextGuestRemaining = get().guestRemainingTurns;
      if (isGuest) {
        if (typeof response.guestRemainingTurns === 'number') {
          setGuestChatCount(GUEST_FREE_CHAT_LIMIT - response.guestRemainingTurns);
          nextGuestRemaining = response.guestRemainingTurns;
        } else {
          const used = incrementGuestChatCount();
          nextGuestRemaining = Math.max(0, GUEST_FREE_CHAT_LIMIT - used);
        }
      }

      const needsAuth = Array.isArray(response.requiredInputs) && response.requiredInputs.includes('AUTHENTICATION');
      const aiMessage: AiChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: response.message || (isEn ? 'Your request has been received.' : 'Hệ thống đã ghi nhận yêu cầu của bạn.'),
        cards: response.cards,
        responseStatus: response.responseStatus,
        requiresLogin: isGuest && needsAuth,
        timestamp: Date.now(),
      };

      set((state) => {
        const resolvedConvId = response.conversationId || state.conversationId;
        const nextMessages = [...state.messages, aiMessage];
        if (isGuest) {
          saveGuestAiThread({ conversationId: resolvedConvId, messages: nextMessages });
        } else {
          clearGuestAiThread();
        }
        return {
          messages: nextMessages,
          conversationId: resolvedConvId,
          conversationWasGuest: isGuest,
          guestRemainingTurns: nextGuestRemaining,
          isGenerating: false,
        };
      });
    } catch (err: unknown) {
      console.error('[AI Chat] API request failed:', err);
      const apiError = err as { response?: { status?: number; data?: { message?: string; code?: string } } };
      const code = apiError.response?.data?.code;
      const limitHit = isGuest && (code === 'GUEST_CHAT_LIMIT_REACHED' || apiError.response?.status === 401);
      if (limitHit) {
        setGuestChatCount(GUEST_FREE_CHAT_LIMIT);
      }
      const errorMessage = apiError.response?.data?.message || (isEn
        ? 'AI service is currently unavailable or disconnected from language model. Please try again later.'
        : 'Dịch vụ Trợ lý AI hiện không khả dụng hoặc chưa kết nối được máy chủ ngôn ngữ. Vui lòng thử lại sau.');

      const systemErrorMessage: AiChatMessage = {
        id: `ai-err-${Date.now()}`,
        role: 'ai',
        content: errorMessage,
        requiresLogin: Boolean(limitHit),
        timestamp: Date.now(),
      };
      set((state) => {
        const nextMessages = [...state.messages, systemErrorMessage];
        if (isGuest) {
          saveGuestAiThread({ conversationId: state.conversationId, messages: nextMessages });
        }
        return {
          messages: nextMessages,
          guestRemainingTurns: limitHit ? 0 : state.guestRemainingTurns,
          isGenerating: false,
        };
      });
    }
  },

  clearHistory: () => {
    const isEn = useLanguageStore.getState().language === 'en';
    clearGuestAiThread();
    set({
      conversationId: undefined,
      conversationWasGuest: false,
      userClearedThread: true,
      messages: [
        {
          id: `welcome-${Date.now()}`,
          role: 'ai',
          content: isEn
            ? 'Conversation reset. I am ready to assist with marine conditions, watersports tours, and ocean safety!'
            : 'Cuộc trò chuyện đã được làm mới. Tôi sẵn sàng giải đáp thắc mắc về điều kiện biển, tour thể thao nước và an toàn hàng hải!',
          timestamp: Date.now(),
        },
      ],
    });
  },
}));
