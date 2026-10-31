import type { MasterOrder, SubOrder } from '../../../types/index.ts';

export interface AdminDisplayOrder extends MasterOrder {
  customerName?: string;
  paymentStatus?: string;
}

export function filterMasterOrders(orders: MasterOrder[], query: string, status: string) {
  const search = query.trim().toLowerCase();
  return orders.filter(order => {
    const matchesStatus = status === 'all' || order.status === status;
    const customerName = ((order as AdminDisplayOrder).customerName || '').toLowerCase();
    const customerId = (order.customerId || '').toLowerCase();
    const orderId = (order.id || '').toLowerCase();
    const matchesSearch = !search || orderId.includes(search) || customerId.includes(search) || customerName.includes(search);
    return matchesStatus && matchesSearch;
  });
}

export function relatedSubOrders(orders: SubOrder[], masterOrderId: string) {
  return orders.filter(order => order.masterOrderId === masterOrderId);
}

export function ordersToCsv(orders: MasterOrder[]) {
  const quote = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`;
  return [
    ['Mã Đơn', 'Khách Hàng', 'Mã Khách Hàng', 'Trạng Thái', 'Tổng Tiền', 'Giảm Giá', 'Thanh Toán', 'Thời Gian'],
    ...orders.map(order => [
      order.id,
      (order as AdminDisplayOrder).customerName || order.customerId,
      order.customerId,
      order.status,
      order.totalAmount,
      order.discountAmount,
      (order as AdminDisplayOrder).paymentStatus || '',
      order.createdAt || ''
    ]),
  ].map(row => row.map(quote).join(',')).join('\r\n');
}

export function simulateRefund(orders: SubOrder[], input: { demo: boolean; masterOrderId: string; subOrderId: string; reason: string }) {
  const target = orders.find(order => order.id === input.subOrderId && order.masterOrderId === input.masterOrderId);
  if (!input.demo || !input.reason.trim() || !target || target.status === 'REFUNDED') return orders;
  return orders.map(order => order === target ? { ...order, status: 'REFUNDED' as const } : order);
}
