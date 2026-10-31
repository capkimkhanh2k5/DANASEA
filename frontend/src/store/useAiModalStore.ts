import { create } from 'zustand';

export interface AiChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
}

interface AiModalState {
  isOpen: boolean;
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
  content: 'Xin chào! Tôi là Trợ lý AI DANASEA. Tôi có thể hỗ trợ bạn kiểm tra điều kiện sóng gió thủy triều hôm nay, gợi ý lịch trình biển phù hợp (lặn san hô Hòn Sụp, chèo SUP Mỹ Khê, cano kéo dù bay) và giải đáp tiêu chuẩn an toàn hàng hải SOLAS. Bạn muốn khám phá hoạt động nào?',
  timestamp: Date.now(),
};

function generateMaritimeAiResponse(query: string): string {
  const q = query.toLowerCase();

  if (q.includes('thời tiết') || q.includes('sóng') || q.includes('gió') || q.includes('thủy triều') || q.includes('weather') || q.includes('tide')) {
    return 'Dữ liệu hải văn Đà Nẵng hôm nay: Khu vực Bán đảo Sơn Trà & Mỹ Khê có gió Đông Nam cấp 2 - 3 (8-12 km/h), sóng êm 0.4m - 0.7m, nhiệt độ nước biển 27°C, tầm nhìn đáy 8 - 12m. Điều kiện lý tưởng cho chèo SUP (05:00 - 07:00) và lặn ngắm san hô Hòn Sụp (08:30 - 11:30).';
  }

  if (q.includes('san hô') || q.includes('hòn sụp') || q.includes('lặn') || q.includes('snorkeling') || q.includes('diving')) {
    return 'Lặn biển ngắm san hô tại Hòn Sụp (Sơn Trà) đẹp nhất vào khung giờ 08:30 - 11:30 sáng khi ánh nắng xuyên qua làn nước trong vắt. Tour trọn gói bao gồm cano cao tốc khứ hồi từ bến DHC, kính lặn - ống thở khử trùng tiêu chuẩn, áo phao SOLAS và hướng dẫn viên cứu hộ bơi lội kèm sát 1:1.';
  }

  if (q.includes('sup') || q.includes('bình minh') || q.includes('chèo')) {
    return 'Chèo SUP đón bình minh tại bãi biển Mỹ Khê (khu vực Công viên Biển Đông) là trải nghiệm được yêu thích nhất. Thời gian xuất phát từ 05:00 - 06:45 sáng. Mặt biển sáng sớm phẳng lặng, sóng < 0.4m. Trước khi xuống nước, bạn sẽ được hướng dẫn kỹ thuật chèo, giữ thăng bằng và quy tắc an toàn trong 15 phút.';
  }

  if (q.includes('cano') || q.includes('dù bay') || q.includes('moto') || q.includes('jetski') || q.includes('hoàng hôn') || q.includes('du thuyền')) {
    return 'Hoạt động Cano kéo dù bay (độ cao 70 - 100m) và Moto nước hoạt động từ 08:00 đến 16:30 hàng ngày. Tất cả cano đều có kiểm định an toàn kỹ thuật đường thủy nội địa, phao cứu sinh tự bung và bảo hiểm du lịch Bảo Việt 100M/vụ. Nếu thích ngắm hoàng hôn, bạn có thể tham khảo du thuyền vịnh Đà Nẵng lúc 17:00 - 18:30.';
  }

  if (q.includes('an toàn') || q.includes('bảo hiểm') || q.includes('solas') || q.includes('hủy') || q.includes('hoàn tiền')) {
    return 'Cam kết an toàn hàng hải tại DANASEA:\n1. 100% du khách được trang bị áo phao đạt chuẩn SOLAS quốc tế.\n2. Hướng dẫn viên có chứng chỉ bơi lội cứu hộ biển chuyên nghiệp.\n3. Bảo hiểm tai nạn du lịch Bảo Việt mức trách nhiệm 100.000.000 VNĐ/người/vụ.\n4. Chính sách an toàn thời tiết: Miễn phí dời lịch hoặc hoàn tiền 100% nếu sóng > 1.5m hoặc có cảnh báo thời tiết xấu từ cơ quan cảng vụ.';
  }

  return `DANASEA AI đã tiếp nhận câu hỏi của bạn về: "${query}". Hiện tại các bãi biển Mỹ Khê, Sơn Trà và Non Nước đang hoạt động bình thường với điều kiện biển tốt. Bạn có thể tham khảo các gói tour phổ biến như Chèo SUP bình minh, Lặn san hô Hòn Sụp hoặc Cano kéo dù bay trên trang Khám phá hoặc mục Trải nghiệm!`;
}

export const useAiModalStore = create<AiModalState>((set, get) => ({
  isOpen: false,
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

    set((state) => ({
      messages: [...state.messages, userMessage],
      isGenerating: true,
    }));

    // Giả lập thời gian suy nghĩ & phân tích dữ liệu hải văn (600ms - 900ms)
    await new Promise((resolve) => setTimeout(resolve, 750));

    const replyContent = generateMaritimeAiResponse(prompt.trim());
    const aiMessage: AiChatMessage = {
      id: `ai-${Date.now()}`,
      role: 'ai',
      content: replyContent,
      timestamp: Date.now(),
    };

    set((state) => ({
      messages: [...state.messages, aiMessage],
      isGenerating: false,
    }));
  },

  clearHistory: () => {
    set({
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
