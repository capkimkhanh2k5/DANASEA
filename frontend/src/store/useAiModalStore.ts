import { create } from 'zustand';
import { aiService } from '../services/aiService';
import { useAuthStore } from './useAuthStore';
import { useLanguageStore } from './useLanguageStore';

export interface AiChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
}

interface AiModalState {
  isOpen: boolean;
  conversationId?: string;
  messages: AiChatMessage[];
  isGenerating: boolean;
  openAiModal: (initialPrompt?: string) => void;
  closeAiModal: () => void;
  toggleAiModal: () => void;
  sendMessage: (prompt: string) => Promise<void>;
  clearHistory: () => void;
}

const INITIAL_AI_MESSAGE: AiChatMessage = {
  id: 'welcome-msg',
  role: 'ai',
  content: 'Hệ thống hỗ trợ tra cứu điều kiện hải văn, cập nhật thời tiết và tư vấn đặt dịch vụ biển tại Đà Nẵng. Bạn cần tìm kiếm thông tin nào?',
  timestamp: Date.now(),
};

export const useAiModalStore = create<AiModalState>((set, get) => ({
  isOpen: false,
  conversationId: undefined,
  messages: [INITIAL_AI_MESSAGE],
  isGenerating: false,

  openAiModal: (initialPrompt?: string) => {
    set({ isOpen: true });
    if (initialPrompt && initialPrompt.trim()) {
      get().sendMessage(initialPrompt.trim());
    }
  },

  closeAiModal: () => set({ isOpen: false }),

  toggleAiModal: () => set((state) => ({ isOpen: !state.isOpen })),

  sendMessage: async (prompt: string) => {
    if (!prompt.trim() || get().isGenerating) return;

    const userMessage: AiChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: prompt.trim(),
      timestamp: Date.now(),
    };

    const { isAuthenticated, accessToken } = useAuthStore.getState();
    const isEn = useLanguageStore.getState().language === 'en';
    if (!isAuthenticated || !accessToken) {
      const loginNotice: AiChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: isEn
          ? 'Please sign in to send questions and save your maritime travel history.'
          : 'Vui lòng đăng nhập tài khoản để gửi câu hỏi và lưu lịch sử tư vấn hải trình.',
        timestamp: Date.now(),
      };
      set((state) => ({
        messages: [...state.messages, userMessage, loginNotice],
        isGenerating: false,
      }));
      return;
    }

    set((state) => ({
      messages: [...state.messages, userMessage],
      isGenerating: true,
    }));

    try {
      const response = await aiService.chat(prompt.trim(), get().conversationId);
      const aiMessage: AiChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: response.message || (isEn ? 'Your request has been received.' : 'Hệ thống đã ghi nhận yêu cầu của bạn.'),
        timestamp: Date.now(),
      };

      set((state) => ({
        messages: [...state.messages, aiMessage],
        conversationId: response.conversationId || state.conversationId,
        isGenerating: false,
      }));
    } catch (err: unknown) {
      console.error('[AI Chat] API request failed:', err);
      const apiError = err as { response?: { data?: { message?: string; code?: string } } };
      const errorMessage = apiError.response?.data?.message || (isEn 
        ? 'AI service is currently unavailable or disconnected from language model. Please try again later.' 
        : 'Dịch vụ Trợ lý AI hiện không khả dụng hoặc chưa kết nối được máy chủ ngôn ngữ. Vui lòng thử lại sau.');

      const systemErrorMessage: AiChatMessage = {
        id: `ai-err-${Date.now()}`,
        role: 'ai',
        content: errorMessage,
        timestamp: Date.now(),
      };
      set((state) => ({
        messages: [...state.messages, systemErrorMessage],
        isGenerating: false,
      }));
    }
  },

  clearHistory: () => {
    const isEn = useLanguageStore.getState().language === 'en';
    set({
      conversationId: undefined,
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
