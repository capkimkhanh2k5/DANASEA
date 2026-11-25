import type { CopilotContextType } from '../../../types/adminAi';
import type { ContextSummary } from './AdminCopilotContextPicker';

export const DANABOT_PINNED_SESSION_KEY = 'danabot_pinned_session';

export interface PinnedCopilotSession {
  isPinned: boolean;
  conversationId: string | null;
  inputText: string;
  contextType: CopilotContextType | null;
  contextId: string | null;
  pinnedSummary: ContextSummary | null;
  scrollTop?: number;
}

function getSessionStorage(): Storage | null {
  if (typeof window !== 'undefined' && window.sessionStorage) {
    return window.sessionStorage;
  }
  if (typeof sessionStorage !== 'undefined') {
    return sessionStorage;
  }
  return null;
}

export function getStoredPinnedSession(): PinnedCopilotSession | null {
  const storage = getSessionStorage();
  if (!storage) return null;
  try {
    const raw = storage.getItem(DANABOT_PINNED_SESSION_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch (err) {
    void err;
    return null;
  }
}

export function savePinnedSession(data: PinnedCopilotSession): void {
  const storage = getSessionStorage();
  if (!storage) return;
  try {
    storage.setItem(DANABOT_PINNED_SESSION_KEY, JSON.stringify(data));
  } catch (err) {
    void err;
  }
}

export function clearPinnedSessionStorage(): void {
  const storage = getSessionStorage();
  if (!storage) return;
  try {
    storage.removeItem(DANABOT_PINNED_SESSION_KEY);
  } catch (err) {
    void err;
  }
}
