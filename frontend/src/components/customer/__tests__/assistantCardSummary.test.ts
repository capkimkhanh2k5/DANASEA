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
  assert.equal(paySummary?.note, undefined);

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

test('summarizes CUSTOMER_SUPPORT order details with or without cancellation preview, and ORDER_LIST', () => {
  const orderId = '7a2f1c4d-1234-4abc-9def-0123456789ab';
  const orderOnly = summarizeAssistantCard({
    id: 'sup-1',
    type: 'CUSTOMER_SUPPORT',
    status: 'AVAILABLE',
    facts: {
      order: {
        id: orderId,
        orderCode: 'ORD-7A2F1C4D',
        status: 'PAID',
        paymentStatus: 'PAID',
        totalAmount: 1200000,
        items: [{
          subOrderCode: 'SUB-EC938B9F',
          serviceName: 'Lặn san hô Hòn Sụp',
          slotDate: '2026-11-28',
          slotStartTime: '08:00',
          status: 'CONFIRMED',
        }],
      },
    },
  }, 'vi');
  assert.equal(orderOnly?.title, 'Thông tin đơn hàng');
  assert.equal(orderOnly?.rows[0]?.label, '#ORD-7A2F1C4D');
  assert.equal(orderOnly?.rows[0]?.href, `/profile/orders?orderId=${orderId}`);
  assert.equal(orderOnly?.note, undefined);

  const orderWithPreview = summarizeAssistantCard({
    id: 'sup-2',
    type: 'CUSTOMER_SUPPORT',
    status: 'AVAILABLE',
    facts: {
      order: { id: orderId, orderCode: 'ORD-7A2F1C4D', status: 'PAID', paymentStatus: 'PAID', totalAmount: 1200000 },
      cancellationPreview: { refundAmount: 1200000 },
    },
  }, 'vi');
  assert.ok(orderWithPreview?.note?.includes('hoàn tiền'));

  const orderList = summarizeAssistantCard({
    id: 'ord-list',
    type: 'ORDER_LIST',
    status: 'AVAILABLE',
    facts: {
      orders: [{ id: orderId, orderCode: 'ORD-7A2F1C4D', status: 'PAID', totalAmount: 1200000 }],
    },
  }, 'vi');
  assert.equal(orderList?.title, 'Đơn hàng của bạn');
  assert.equal(orderList?.rows[0]?.label, '#ORD-7A2F1C4D');
});
