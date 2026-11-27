import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  GUEST_FREE_CHAT_LIMIT,
  GUEST_CHAT_COUNT_STORAGE_KEY,
  GUEST_CHAT_THREAD_STORAGE_KEY,
  clampGuestChatCount,
  getGuestChatCount,
  setGuestChatCount,
  incrementGuestChatCount,
  getRemainingGuestTurns,
  resetGuestChatCount,
  loadGuestAiThread,
  saveGuestAiThread,
  markGuestThreadReopenAfterLogin,
  clearGuestAiThread,
  mapHistoryMessagesToChatMessages,
  type StorageLike,
} from '../guestAiQuota.ts';

function createMemoryStorage(): StorageLike {
  const map = new Map<string, string>();
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => {
      map.set(key, value);
    },
    removeItem: (key) => {
      map.delete(key);
    },
  };
}

describe('guestAiQuota — 5 free turns before sign-in', () => {
  it('starts with 5 free turns and decrements cleanly', () => {
    const storage = createMemoryStorage();
    assert.equal(GUEST_FREE_CHAT_LIMIT, 5);
    assert.equal(getGuestChatCount(storage), 0);
    assert.equal(getRemainingGuestTurns(storage), 5);

    incrementGuestChatCount(storage);
    assert.equal(getGuestChatCount(storage), 1);
    assert.equal(getRemainingGuestTurns(storage), 4);

    for (let i = 0; i < 4; i++) {
      incrementGuestChatCount(storage);
    }
    assert.equal(getGuestChatCount(storage), 5);
    assert.equal(getRemainingGuestTurns(storage), 0);
  });

  it('clamps overflow and invalid stored values', () => {
    const storage = createMemoryStorage();
    storage.setItem(GUEST_CHAT_COUNT_STORAGE_KEY, 'not-a-number');
    assert.equal(getGuestChatCount(storage), 0);
    assert.equal(clampGuestChatCount(-3), 0);
    assert.equal(clampGuestChatCount(99), GUEST_FREE_CHAT_LIMIT);

    setGuestChatCount(99, storage);
    assert.equal(getRemainingGuestTurns(storage), 0);

    resetGuestChatCount(storage);
    assert.equal(getRemainingGuestTurns(storage), 5);
  });

  it('persists guest conversation snapshot, marks reopenAfterLogin, and clears cleanly', () => {
    const storage = createMemoryStorage();
    assert.equal(loadGuestAiThread(storage), null);

    saveGuestAiThread({
      conversationId: 'conv-123',
      messages: [
        { id: 'u1', role: 'user', content: 'Chính sách hoàn hủy?', timestamp: 1000 },
        { id: 'a1', role: 'ai', content: 'Hoàn 100% trước 24h.', timestamp: 1001 },
      ],
    }, storage);

    const loaded = loadGuestAiThread(storage);
    assert.equal(loaded?.conversationId, 'conv-123');
    assert.equal(loaded?.messages.length, 2);
    assert.equal(loaded?.reopenAfterLogin, false);

    markGuestThreadReopenAfterLogin(storage);
    assert.equal(loadGuestAiThread(storage)?.reopenAfterLogin, true);

    storage.setItem(GUEST_CHAT_THREAD_STORAGE_KEY, '{broken');
    assert.equal(loadGuestAiThread(storage), null);

    clearGuestAiThread(storage);
    assert.equal(loadGuestAiThread(storage), null);
  });

  it('maps backend DESC message history to chronological UI messages with cards', () => {
    const mapped = mapHistoryMessagesToChatMessages([
      {
        id: 'm3',
        role: 'ASSISTANT',
        content: 'DANASEA hỗ trợ VNPAY.',
        createdAt: '2026-11-27T03:00:02Z',
        responsePayload: JSON.stringify({
          responseStatus: 'ANSWERED',
          cards: [{ id: 'c1', type: 'PAYMENT_PROVIDERS', status: 'AVAILABLE', facts: {} }],
        }),
      },
      {
        id: 'm2',
        role: 'TOOL',
        content: '{"ignored":true}',
        createdAt: '2026-11-27T03:00:01Z',
      },
      {
        id: 'm1',
        role: 'USER',
        content: 'Hỗ trợ thanh toán nào?',
        createdAt: '2026-11-27T03:00:00Z',
      },
    ]);

    assert.equal(mapped.length, 2);
    assert.equal(mapped[0].role, 'user');
    assert.equal(mapped[0].content, 'Hỗ trợ thanh toán nào?');
    assert.equal(mapped[1].role, 'ai');
    assert.equal(mapped[1].responseStatus, 'ANSWERED');
    assert.equal(mapped[1].cards?.[0]?.type, 'PAYMENT_PROVIDERS');
  });
});
