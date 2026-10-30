import type { MasterOrder, SubOrder } from '../../../types/index.ts';

export function filterMasterOrders(orders: MasterOrder[], query: string, status: string) {
  const search = query.trim().toLowerCase();
  return orders.filter(order => (status === 'all' || order.status === status)
    && (order.id.toLowerCase().includes(search) || order.customerId.toLowerCase().includes(search)));
}

export function relatedSubOrders(orders: SubOrder[], masterOrderId: string) {
  return orders.filter(order => order.masterOrderId === masterOrderId);
}

export function ordersToCsv(orders: MasterOrder[]) {
  const quote = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`;
  return [
    ['Mã Đơn', 'Khách Hàng', 'Trạng Thái', 'Tổng Tiền', 'Giảm Giá', 'Thời Gian'],
    ...orders.map(order => [order.id, order.customerId, order.status, order.totalAmount, order.discountAmount, order.createdAt || '']),
  ].map(row => row.map(quote).join(',')).join('\r\n');
}

export function simulateRefund(orders: SubOrder[], input: { demo: boolean; masterOrderId: string; subOrderId: string; reason: string }) {
  const target = orders.find(order => order.id === input.subOrderId && order.masterOrderId === input.masterOrderId);
  if (!input.demo || !input.reason.trim() || !target || target.status === 'REFUNDED') return orders;
  return orders.map(order => order === target ? { ...order, status: 'REFUNDED' as const } : order);
}
