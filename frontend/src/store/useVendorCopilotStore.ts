import { create } from 'zustand';
import { vendorAiService } from '../services/vendorAiService';
import type { 
  CopilotConversation, 
  CopilotArtifact 
} from '../types/vendorAi';

export interface ChatUiMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  artifacts?: CopilotArtifact[];
  toolsUsed?: string[];
  createdAt: string;
}

export interface AttachedContext {
  type: 'ORDER' | 'SERVICE';
  id: string;
  label: string;
}

interface VendorCopilotState {
  isOpen: boolean;
  activeConversationId?: string;
  conversations: CopilotConversation[];
  isSessionsLoading: boolean;
  messages: ChatUiMessage[];
  isLoading: boolean;
  attachedContext?: AttachedContext;
  error?: string | null;

  // Actions
  openCopilot: (context?: AttachedContext, initialPrompt?: string) => void;
  closeCopilot: () => void;
  toggleCopilot: () => void;
  setAttachedContext: (context?: AttachedContext) => void;
  clearAttachedContext: () => void;
  loadConversations: () => Promise<void>;
  selectConversation: (id: string) => Promise<void>;
  startNewConversation: () => void;
  sendMessage: (text: string) => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  togglePinConversation: (id: string, currentPinned: boolean) => Promise<void>;
}

export const useVendorCopilotStore = create<VendorCopilotState>((set, get) => ({
  isOpen: false,
  activeConversationId: undefined,
  conversations: [],
  isSessionsLoading: false,
  messages: [],
  isLoading: false,
  attachedContext: undefined,
  error: null,

  openCopilot: (context?: AttachedContext, initialPrompt?: string) => {
    set({ isOpen: true, attachedContext: context || get().attachedContext, error: null });
    get().loadConversations();
    if (initialPrompt && initialPrompt.trim()) {
      get().sendMessage(initialPrompt.trim());
    }
  },

  closeCopilot: () => set({ isOpen: false }),

  toggleCopilot: () => {
    const nextOpen = !get().isOpen;
    set({ isOpen: nextOpen });
    if (nextOpen && get().conversations.length === 0) {
      get().loadConversations();
    }
  },

  setAttachedContext: (context?: AttachedContext) => {
    set({ attachedContext: context });
  },

  clearAttachedContext: () => {
    set({ attachedContext: undefined });
  },

  loadConversations: async () => {
    try {
      set({ isSessionsLoading: true });
      const convs = await vendorAiService.getConversations(false, 30);
      set({ conversations: convs, isSessionsLoading: false });
    } catch (err) {
      console.error('[VendorCopilot] Failed to load conversations:', err);
      set({ isSessionsLoading: false });
    }
  },

  selectConversation: async (id: string) => {
    try {
      set({ activeConversationId: id, isLoading: true, error: null });
      const history = await vendorAiService.getConversationHistory(id);
      
      const uiMessages: ChatUiMessage[] = history.map((m) => {
        let parsedArtifacts: CopilotArtifact[] | undefined;
        if (m.artifactsJson) {
          try {
            parsedArtifacts = JSON.parse(m.artifactsJson);
          } catch {
            parsedArtifacts = undefined;
          }
        }
        const toolsUsedList = m.toolsUsed ? m.toolsUsed.split(',').filter(Boolean) : undefined;
        return {
          id: m.id,
          role: m.role.toLowerCase() === 'user' ? 'user' : 'assistant',
          content: m.content,
          artifacts: parsedArtifacts,
          toolsUsed: toolsUsedList,
          createdAt: m.createdAt,
        };
      });

      set({ messages: uiMessages, isLoading: false });
    } catch (err: unknown) {
      console.error('[VendorCopilot] Failed to load history:', err);
      const apiError = err as { response?: { data?: { message?: string } } };
      set({ 
        isLoading: false, 
        error: apiError.response?.data?.message || 'Không thể tải lịch sử đoạn chat.' 
      });
    }
  },

  startNewConversation: () => {
    set({
      activeConversationId: undefined,
      messages: [],
      error: null,
    });
  },

  sendMessage: async (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || get().isLoading) return;

    const { activeConversationId, attachedContext } = get();
    const userMsgId = 'user-' + Date.now();
    const userMsg: ChatUiMessage = {
      id: userMsgId,
      role: 'user',
      content: trimmed,
      createdAt: new Date().toISOString(),
    };

    set((state) => ({
      messages: [...state.messages, userMsg],
      isLoading: true,
      error: null,
    }));

    try {
      const res = await vendorAiService.chatCopilot({
        conversationId: activeConversationId,
        message: trimmed,
        contextType: attachedContext?.type,
        contextId: attachedContext?.id,
      });

      const assistantMsg: ChatUiMessage = {
        id: 'assistant-' + Date.now(),
        role: 'assistant',
        content: res.message,
        artifacts: res.artifacts,
        toolsUsed: res.toolsUsed,
        createdAt: new Date().toISOString(),
      };

      set((state) => ({
        activeConversationId: res.conversationId || state.activeConversationId,
        messages: [...state.messages, assistantMsg],
        isLoading: false,
      }));

      // Reload conversations list in background to update title & timestamp
      get().loadConversations();
    } catch (err: unknown) {
      console.error('[VendorCopilot] Chat request failed:', err);
      const apiError = err as { response?: { data?: { message?: string; code?: string } } };
      const errorText = apiError.response?.data?.message || 'Trợ lý Copilot tạm thời không phản hồi. Vui lòng thử lại sau.';

      set((state) => ({
        isLoading: false,
        error: errorText,
        messages: [
          ...state.messages,
          {
            id: 'err-' + Date.now(),
            role: 'assistant',
            content: `⚠️ ${errorText}`,
            createdAt: new Date().toISOString(),
          },
        ],
      }));
    }
  },

  deleteConversation: async (id: string) => {
    try {
      await vendorAiService.deleteConversation(id);
      set((state) => ({
        conversations: state.conversations.filter((c) => c.id !== id),
        ...(state.activeConversationId === id ? { activeConversationId: undefined, messages: [] } : {}),
      }));
    } catch (err) {
      console.error('[VendorCopilot] Failed to delete conversation:', err);
    }
  },

  togglePinConversation: async (id: string, currentPinned: boolean) => {
    try {
      const updated = await vendorAiService.updateConversation(id, { pinned: !currentPinned });
      set((state) => ({
        conversations: state.conversations.map((c) => (c.id === id ? updated : c)),
      }));
    } catch (err) {
      console.error('[VendorCopilot] Failed to toggle pin:', err);
    }
  },
}));
