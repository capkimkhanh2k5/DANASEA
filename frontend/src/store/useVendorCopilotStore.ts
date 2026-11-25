import { create } from 'zustand';
import { vendorAiService } from '../services/vendorAiService';
import { detectLanguage } from '../utils/languageDetector';
import type { 
  CopilotConversation, 
  CopilotArtifact 
} from '../types/vendorAi';

export interface ChatUiMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  context?: AttachedContext;
  media?: AttachedMedia[];
  artifacts?: CopilotArtifact[];
  toolsUsed?: string[];
  createdAt: string;
}

export type VendorContextType =
  | 'ORDER'
  | 'SERVICE'
  | 'REVIEW'
  | 'DISPUTE'
  | 'REFUND'
  | 'SLOT'
  | 'EQUIPMENT'
  | 'PAYOUT';

export interface AttachedMedia {
  id: string;
  type: 'image' | 'file';
  name: string;
  url?: string;
  size?: number;
  previewUrl?: string;
}

export interface AttachedContext {
  type: VendorContextType;
  id: string;
  label: string;
  meta?: Record<string, unknown>;
}

interface VendorCopilotState {
  isOpen: boolean;
  activeConversationId?: string;
  conversations: CopilotConversation[];
  isSessionsLoading: boolean;
  messages: ChatUiMessage[];
  isLoading: boolean;
  attachedContext?: AttachedContext;
  attachedMedia: AttachedMedia[];
  error?: string | null;

  // Actions
  openCopilot: (context?: AttachedContext, initialPrompt?: string) => void;
  closeCopilot: () => void;
  toggleCopilot: () => void;
  setAttachedContext: (context?: AttachedContext) => void;
  clearAttachedContext: () => void;
  addAttachedMedia: (media: AttachedMedia) => void;
  removeAttachedMedia: (id: string) => void;
  clearAttachedMedia: () => void;
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
  attachedMedia: [],
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

  addAttachedMedia: (media: AttachedMedia) => {
    set((state) => ({ attachedMedia: [...state.attachedMedia, media] }));
  },

  removeAttachedMedia: (id: string) => {
    set((state) => ({ attachedMedia: state.attachedMedia.filter((m) => m.id !== id) }));
  },

  clearAttachedMedia: () => {
    set({ attachedMedia: [] });
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

        // Parse legacy or backend-saved prefix "[Tệp đính kèm: ...]" and "[Về ...]" out of user text into clean UI elements
        let displayContent = m.content;
        let extractedContext: AttachedContext | undefined;
        const extractedMedia: AttachedMedia[] = [];

        // Check for media attachment prefix
        const mediaMatches = displayContent.matchAll(/\[Tệp đính kèm: (.+?)(?: \((https?:\/\/[^\s)]+)\))?\]\n*/g);
        for (const match of mediaMatches) {
          const fileName = match[1];
          const fileUrl = match[2];
          extractedMedia.push({
            id: 'hist-media-' + Math.random().toString(36).substring(2, 9),
            type: fileName.match(/\.(png|jpe?g|webp|gif)$/i) ? 'image' : 'file',
            name: fileName,
            url: fileUrl,
            previewUrl: fileUrl,
          });
        }
        displayContent = displayContent.replace(/\[Tệp đính kèm: .+?\]\n*/g, '').trim();

        // Check for context prefix
        const prefixMatch = displayContent.match(/^\[Về (?:\[(.+?)\] )?(.+?) \(ID: ([a-f0-9-]+)\)\] (.*)$/s);
        if (prefixMatch && m.role.toLowerCase() === 'user') {
          const typeStr = prefixMatch[1] || 'SERVICE';
          const labelStr = prefixMatch[2];
          const idStr = prefixMatch[3];
          displayContent = prefixMatch[4];
          extractedContext = {
            type: (typeStr.includes('Dịch vụ') ? 'SERVICE' : 'ORDER') as VendorContextType,
            id: idStr,
            label: labelStr,
          };
        }

        return {
          id: m.id,
          role: m.role.toLowerCase() === 'user' ? 'user' : 'assistant',
          content: displayContent,
          context: extractedContext,
          media: extractedMedia.length > 0 ? extractedMedia : undefined,
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

    const { activeConversationId, attachedContext, attachedMedia } = get();

    // If media is attached, enrich prompt with media references
    let effectiveText = trimmed;
    if (attachedMedia.length > 0) {
      const mediaList = attachedMedia
        .map((m) => `[Tệp đính kèm: ${m.name}${m.url ? ` (${m.url})` : ''}]`)
        .join('\n');
      effectiveText = `${mediaList}\n\n${trimmed}`;
    }

    const userMsgId = 'user-' + Date.now();
    const userMsg: ChatUiMessage = {
      id: userMsgId,
      role: 'user',
      content: trimmed, // Clean user text without raw bracketed url prefixes
      context: attachedContext, // Clean context chip rendered above or inside message
      media: attachedMedia.length > 0 ? [...attachedMedia] : undefined,
      createdAt: new Date().toISOString(),
    };

    set((state) => ({
      messages: [...state.messages, userMsg],
      isLoading: true,
      attachedContext: undefined, // Consume pinned context on send like LLM attachments
      attachedMedia: [],
      error: null,
    }));

    try {
      const detectedLang = detectLanguage(effectiveText);
      const res = await vendorAiService.chatCopilot({
        conversationId: activeConversationId,
        message: effectiveText, // Send clean user message
        contextType: attachedContext?.type,
        contextId: attachedContext?.id,
      }, detectedLang);

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
            content: errorText,
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
