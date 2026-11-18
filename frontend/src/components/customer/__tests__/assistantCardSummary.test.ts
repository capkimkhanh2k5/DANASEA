import assert from 'node:assert/strict';
import { test } from 'node:test';
import { summarizeAssistantCard } from '../assistantCardSummary.ts';

test('reads platform refund tiers and stays distinct from an order-specific quote', () => {
  const summary = summarizeAssistantCard({
    id: 'policy', type: 'POLICY', status: 'AVAILABLE',
    facts: { policy: { customerCancellation: [
      { condition: 'MORE_THAN_24_HOURS', percentage: 100 },
      { condition: 'AT_MOST_24_HOURS', percentage: 0 },
    ] } },
  }, 'vi');
  assert.equal(summary?.title, 'Chính sách chung');
  assert.deepEqual(summary?.rows.map((row) => row.value), ['100%', '0%']);
});

test('area weather does not confirm a departure', () => {
  const summary = summarizeAssistantCard({
    id: 'weather', type: 'WEATHER', status: 'AVAILABLE',
    facts: { location: 'Mỹ Khê', alertLevel: 'GREEN', isSafe: true },
  }, 'vi');
  assert.match(summary?.note ?? '', /tham khảo/);
  assert.doesNotMatch(JSON.stringify(summary), /GREEN|An toàn/u);
});

test('failed booking calls provide no confirmation affordance', () => {
  assert.equal(summarizeAssistantCard({
    id: 'booking', type: 'BOOKING_CONFIRMATION', status: 'UNAVAILABLE',
    facts: { errorCode: 'EXPLICIT_BOOKING_INTENT_REQUIRED' },
  }, 'vi'), null);
});

test('shows the actual date and time on a marine briefing card', () => {
  const summary = summarizeAssistantCard({
    id: 'briefing', type: 'MARINE_BRIEFING_CARD', status: 'AVAILABLE',
    facts: { activities: [{ serviceTitle: 'Kayak', departureDate: [2026, 11, 8], departureTime: [8, 30] }] },
  }, 'vi');
  assert.equal(summary?.rows[0]?.value, '08/11/2026 · 08:30');
});

test('summarizes payment providers, itinerary list, replan proposal, and out_of_stock booking cards', () => {
  const paySummary = summarizeAssistantCard({
    id: 'pay', type: 'PAYMENT_PROVIDERS', status: 'AVAILABLE',
    facts: { enabledProviders: ['VNPAY', 'PAYPAL'], disabledProviders: ['MOMO'] },
  }, 'vi');
  assert.match(paySummary?.rows[0]?.value ?? '', /VNPAY, PAYPAL/);

  const listSummary = summarizeAssistantCard({
    id: 'it-list', type: 'ITINERARY_LIST', status: 'AVAILABLE',
    facts: { itineraries: [{ index: 1, version: 2, lifecycle: 'ACCEPTED', totalPrice: 500000, from: '2026-11-20', to: '2026-11-20' }] },
  }, 'vi');
  assert.match(listSummary?.rows[0]?.label ?? '', /#1/);

  const proposalSummary = summarizeAssistantCard({
    id: 'it-prop', type: 'ITINERARY', status: 'PROPOSED',
    facts: {
      removedSlotIds: ['s1'],
      addedSlotIds: ['s2'],
      proposal: {
        id: 'p1', baseVersion: 2, state: 'PENDING',
        plan: { totalPrice: 650000, items: [{ serviceTitle: 'SUP Mỹ Khê', startTime: [14, 0], endTime: [16, 0] }] },
      },
    },
  }, 'vi');
  assert.match(proposalSummary?.title ?? '', /Đề xuất/);
  assert.match(proposalSummary?.rows.map((r) => r.value).join(' ') ?? '', /-1 \/ \+1/);

  const soldOut = summarizeAssistantCard({
    id: 'bk-out', type: 'BOOKING_CONFIRMATION', status: 'out_of_stock',
    facts: {},
  }, 'vi');
  assert.match(soldOut?.note ?? '', /overbook/i);
});
