export const GUEST_FREE_CHAT_LIMIT = 5;
export const GUEST_CHAT_COUNT_STORAGE_KEY = 'danasea_guest_ai_chat_count';
export const GUEST_CHAT_THREAD_STORAGE_KEY = 'danasea_guest_ai_thread_v1';

export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface PersistedChatMessage {
  id: string;
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
  cards?: Array<{
    id: string;
    type: string;
    tool?: string;
    status: string;
    facts: Record<string, unknown>;
    actions?: Array<{
      type: string;
      method: string;
      path: string;
      parameters?: Record<string, unknown>;
      confirmationRequired?: boolean;
    }>;
    requiredInputs?: string[];
  }>;
  responseStatus?: string;
  requiresLogin?: boolean;
}

export interface SavedGuestAiThread {
  conversationId?: string;
  messages: PersistedChatMessage[];
  reopenAfterLogin?: boolean;
}

export interface BackendAiMessageRecord {
  id: string;
  role: string;
  content: string;
  createdAt?: string;
  responsePayload?: string | null;
}

const defaultStorage = (): StorageLike | null => {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null;
  } catch {
    return null;
  }
};

export function clampGuestChatCount(count: number): number {
  if (!Number.isFinite(count) || count < 0) return 0;
  return Math.min(GUEST_FREE_CHAT_LIMIT, Math.floor(count));
}

export function getGuestChatCount(storage: StorageLike | null = defaultStorage()): number {
  if (!storage) return 0;
  const raw = storage.getItem(GUEST_CHAT_COUNT_STORAGE_KEY);
  const parsed = raw ? Number.parseInt(raw, 10) : 0;
  return clampGuestChatCount(parsed);
}

export function setGuestChatCount(count: number, storage: StorageLike | null = defaultStorage()): number {
  const clamped = clampGuestChatCount(count);
  storage?.setItem(GUEST_CHAT_COUNT_STORAGE_KEY, String(clamped));
  return clamped;
}

export function incrementGuestChatCount(storage: StorageLike | null = defaultStorage()): number {
  return setGuestChatCount(getGuestChatCount(storage) + 1, storage);
}

export function getRemainingGuestTurns(storage: StorageLike | null = defaultStorage()): number {
  return Math.max(0, GUEST_FREE_CHAT_LIMIT - getGuestChatCount(storage));
}

export function resetGuestChatCount(storage: StorageLike | null = defaultStorage()): void {
  storage?.removeItem(GUEST_CHAT_COUNT_STORAGE_KEY);
}

export function loadGuestAiThread(storage: StorageLike | null = defaultStorage()): SavedGuestAiThread | null {
  if (!storage) return null;
  const raw = storage.getItem(GUEST_CHAT_THREAD_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<SavedGuestAiThread> | null;
    if (!parsed || !Array.isArray(parsed.messages)) return null;
    const messages = parsed.messages.filter(
      (msg): msg is PersistedChatMessage =>
        Boolean(msg)
        && typeof msg.id === 'string'
        && (msg.role === 'user' || msg.role === 'ai')
        && typeof msg.content === 'string'
    );
    return {
      conversationId: typeof parsed.conversationId === 'string' && parsed.conversationId.trim()
        ? parsed.conversationId
        : undefined,
      messages,
      reopenAfterLogin: Boolean(parsed.reopenAfterLogin),
    };
  } catch {
    return null;
  }
}

export function saveGuestAiThread(
  thread: SavedGuestAiThread,
  storage: StorageLike | null = defaultStorage()
): void {
  if (!storage) return;
  try {
    storage.setItem(GUEST_CHAT_THREAD_STORAGE_KEY, JSON.stringify({
      conversationId: thread.conversationId,
      messages: thread.messages,
      reopenAfterLogin: Boolean(thread.reopenAfterLogin),
    }));
  } catch {
    // Ignore storage quota / privacy mode write errors cleanly
  }
}

export function markGuestThreadReopenAfterLogin(storage: StorageLike | null = defaultStorage()): void {
  const existing = loadGuestAiThread(storage);
  if (!existing) return;
  saveGuestAiThread({ ...existing, reopenAfterLogin: true }, storage);
}

export function clearGuestAiThread(storage: StorageLike | null = defaultStorage()): void {
  storage?.removeItem(GUEST_CHAT_THREAD_STORAGE_KEY);
}

export function mapHistoryMessagesToChatMessages(
  history: BackendAiMessageRecord[]
): PersistedChatMessage[] {
  if (!Array.isArray(history) || history.length === 0) return [];
  const chronological = [...history].reverse();
  return chronological
    .filter((entry) => entry && (entry.role === 'USER' || entry.role === 'ASSISTANT'))
    .map((entry, idx) => {
      let cards: PersistedChatMessage['cards'];
      let responseStatus: string | undefined;
      if (entry.responsePayload && typeof entry.responsePayload === 'string') {
        try {
          const payload = JSON.parse(entry.responsePayload) as {
            cards?: PersistedChatMessage['cards'];
            responseStatus?: string;
          };
          if (Array.isArray(payload.cards) && payload.cards.length > 0) {
            cards = payload.cards;
          }
          if (typeof payload.responseStatus === 'string') {
            responseStatus = payload.responseStatus;
          }
        } catch {
          // Non-JSON payload fallback
        }
      }
      const parsedTime = entry.createdAt ? Date.parse(entry.createdAt) : Number.NaN;
      return {
        id: entry.id || `hist-${idx}`,
        role: entry.role === 'USER' ? 'user' : 'ai',
        content: entry.content || '',
        timestamp: Number.isFinite(parsedTime) ? parsedTime : Date.now() + idx,
        ...(cards ? { cards } : {}),
        ...(responseStatus ? { responseStatus } : {}),
      };
    });
}
