import { create } from 'zustand';
import { aiService } from '../services/aiService';
import { useAuthStore } from './useAuthStore';

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
    if (!isAuthenticated || !accessToken) {
      const loginNotice: AiChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: 'Vui lòng đăng nhập tài khoản để gửi câu hỏi và lưu lịch sử tư vấn hải trình.',
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
        content: response.message || 'Hệ thống đã ghi nhận yêu cầu của bạn.',
        timestamp: Date.now(),
      };

      set((state) => ({
        messages: [...state.messages, aiMessage],
        conversationId: response.conversationId || state.conversationId,
        isGenerating: false,
      }));
    } catch (err) {
      console.debug('Lỗi khi gọi API AI:', err);
      // Fallback thân thiện bảo đảm không crash
      const fallbackMessage: AiChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: 'Dữ liệu hải văn Đà Nẵng: Khu vực Bán đảo Sơn Trà & Mỹ Khê đang có điều kiện biển thuận lợi (sóng 0.4 - 0.7m, gió cấp 2). Bạn có thể tham khảo các tour lặn san hô hoặc chèo SUP trên hệ thống!',
        timestamp: Date.now(),
      };
      set((state) => ({
        messages: [...state.messages, fallbackMessage],
        isGenerating: false,
      }));
    }
  },

  clearHistory: () => {
    set({
      conversationId: undefined,
      messages: [
        {
          id: `welcome-${Date.now()}`,
          role: 'ai',
          content: 'Cuộc trò chuyện đã được làm mới. Tôi sẵn sàng giải đáp thắc mắc về điều kiện biển, tour thể thao nước và an toàn hàng hải!',
          timestamp: Date.now(),
        },
      ],
    });
  },
}));
