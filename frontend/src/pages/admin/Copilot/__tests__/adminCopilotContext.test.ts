import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  formatDisplayCode,
  matchesDisplayCodeOrId,
  formatTextWithDisplayCodes,
  resolveDisplayCodeToUuid,
  translateDisplayCodesToUuids,
} from '../../../../utils/displayCode';
import {
  DANABOT_PINNED_SESSION_KEY,
  getStoredPinnedSession,
  savePinnedSession,
  clearPinnedSessionStorage,
  type PinnedCopilotSession,
} from '../adminCopilotSession';

describe('Admin DaNaBot — Context & Display Code Synchronization', () => {
  const seedOrderUuid = '00010000-0000-0000-0000-000000000039';
  const seedSubOrderUuid = '00020000-0000-0000-0000-000000000044';
  const seedDisputeId = '77777702-0000-0000-0000-000000000001';
  const seedDisputeUuid = '00050000-0000-0000-0000-000000000012';

  it('formats internal UUIDs into standardized display codes for Admin UI', () => {
    assert.equal(formatDisplayCode(seedOrderUuid, 'ORD'), 'ORD-00010039');
    assert.equal(formatDisplayCode(seedSubOrderUuid, 'SUB'), 'SUB-00020044');
    assert.equal(formatDisplayCode(seedDisputeId, 'DISP'), 'DISP-77777702');
  });

  it('generates copyable business codes without # prefix', () => {
    // MasterOrder
    const orderDisplay = `#${formatDisplayCode(seedOrderUuid, 'ORD')}`;
    const orderCopy = orderDisplay.replace(/^#/, '');
    assert.equal(orderCopy, 'ORD-00010039');
    assert.ok(!orderCopy.startsWith('#'), 'MasterOrder copy code must not include # prefix');

    // SubOrder
    const subDisplay = `#${formatDisplayCode(seedSubOrderUuid, 'SUB')}`;
    const subCopy = subDisplay.replace(/^#/, '');
    assert.equal(subCopy, 'SUB-00020044');
    assert.ok(!subCopy.startsWith('#'), 'SubOrder copy code must not include # prefix');

    // Dispute
    const disputeDisplay = `#${formatDisplayCode(seedDisputeId, 'DISP')}`;
    const disputeCopy = disputeDisplay.replace(/^#/, '');
    assert.equal(disputeCopy, 'DISP-77777702');
    assert.ok(!disputeCopy.startsWith('#'), 'Dispute copy code must not include # prefix');
  });

  it('matches display codes and raw UUIDs bidirectionally', () => {
    assert.ok(matchesDisplayCodeOrId('ORD-00010039', seedOrderUuid, 'ORD'));
    assert.ok(matchesDisplayCodeOrId('#ORD-00010039', seedOrderUuid, 'ORD'));
    assert.ok(matchesDisplayCodeOrId(seedOrderUuid, seedOrderUuid, 'ORD'));
    assert.ok(!matchesDisplayCodeOrId('ORD-99999999', seedOrderUuid, 'ORD'));
  });

  it('translates user prompt with display code into raw UUID for backend AI tools', () => {
    const userPrompt = 'Kiểm tra rủi ro đơn #ORD-00010039 và khiếu nại #DISP-00050012';
    const translated = translateDisplayCodesToUuids(userPrompt);

    assert.ok(
      translated.includes(seedOrderUuid),
      `Expected ${seedOrderUuid} in translated prompt, got: ${translated}`
    );
    assert.ok(
      translated.includes(seedDisputeUuid),
      `Expected ${seedDisputeUuid} in translated prompt, got: ${translated}`
    );
  });

  it('reconstructs raw UUID from manual short display code in Context Picker', () => {
    assert.equal(resolveDisplayCodeToUuid('ORD-00010039'), seedOrderUuid);
    assert.equal(resolveDisplayCodeToUuid('#ORD-00010039'), seedOrderUuid);
    assert.equal(resolveDisplayCodeToUuid('DISP-00050012'), seedDisputeUuid);
    assert.equal(resolveDisplayCodeToUuid('#DISP-00050012'), seedDisputeUuid);
    assert.equal(resolveDisplayCodeToUuid(seedOrderUuid), seedOrderUuid);
  });

  it('formats text containing raw UUIDs back into display codes for user presentation', () => {
    const aiResponse = `Đã phân tích hồ sơ ${seedOrderUuid} và khiếu nại ${seedDisputeUuid}.`;
    const formatted = formatTextWithDisplayCodes(aiResponse);

    assert.ok(
      formatted.includes('#ORD-00010039'),
      `Expected #ORD-00010039 in formatted text, got: ${formatted}`
    );
    assert.ok(
      formatted.includes('#DISP-00050012'),
      `Expected #DISP-00050012 in formatted text, got: ${formatted}`
    );
    assert.ok(!formatted.includes(seedOrderUuid), 'Raw UUID should be transformed to display code');
  });

  it('manages pinned work session state persistence via sessionStorage', () => {
    const store = new Map<string, string>();
    (globalThis as unknown as { sessionStorage: unknown }).sessionStorage = {
      getItem: (key: string) => store.get(key) || null,
      setItem: (key: string, val: string) => store.set(key, val),
      removeItem: (key: string) => store.delete(key),
      clear: () => store.clear(),
    };

    const sessionData: PinnedCopilotSession = {
      isPinned: true,
      conversationId: 'conv-test-123',
      inputText: 'Xin chào DaNaBot, kiểm tra đơn hàng này',
      contextType: 'ORDER',
      contextId: seedOrderUuid,
      pinnedSummary: {
        type: 'ORDER',
        id: seedOrderUuid,
        displayCode: 'ORD-00010039',
        title: 'Đơn hàng #ORD-00010039',
      },
      scrollTop: 350,
    };

    // Save session using helper
    savePinnedSession(sessionData);
    const retrieved = getStoredPinnedSession();

    assert.ok(retrieved !== null);
    assert.equal(retrieved.isPinned, true);
    assert.equal(retrieved.conversationId, 'conv-test-123');
    assert.equal(retrieved.inputText, 'Xin chào DaNaBot, kiểm tra đơn hàng này');
    assert.equal(retrieved.contextType, 'ORDER');
    assert.equal(retrieved.contextId, seedOrderUuid);
    assert.equal(retrieved.pinnedSummary?.displayCode, 'ORD-00010039');
    assert.equal(retrieved.scrollTop, 350);

    // Clear session (e.g. on unpin, selecting other chat, new chat, or logout)
    clearPinnedSessionStorage();
    assert.equal(getStoredPinnedSession(), null);
    assert.equal(sessionStorage.getItem(DANABOT_PINNED_SESSION_KEY), null);
  });
});
