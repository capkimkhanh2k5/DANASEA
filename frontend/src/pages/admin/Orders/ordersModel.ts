import type { MasterOrder, SubOrder } from '../../../types/index.ts';

export interface AdminDisplayOrder extends MasterOrder {
  customerName?: string;
  paymentStatus?: string;
  paymentProvider?: string;
  totalItems?: number;
}

export function formatDisplayCode(id: string, prefix = 'ORD'): string {
  if (!id) return '';
  if (
    id.startsWith('ORD-') ||
    id.startsWith('PAY-') ||
    id.startsWith('STL-') ||
    id.startsWith('CUST-') ||
    id.startsWith('DISP-') ||
    id.startsWith('LOG-') ||
    id.startsWith('SETTLE-') ||
    id.startsWith('SUB-')
  ) {
    return id;
  }
  const clean = id.replace(/-/g, '');
  const isSeed =
    clean.startsWith('00010000') ||
    clean.startsWith('00060000') ||
    clean.startsWith('00000000') ||
    new Set(clean.slice(0, 8).split('')).size <= 2;
  const code = isSeed
    ? (clean.slice(0, 4) + clean.slice(-4))
    : clean.substring(0, 8);
  return `${prefix}-${code.toUpperCase()}`;
}

export function filterMasterOrders(orders: MasterOrder[], query: string, status: string) {
  const search = query.trim().toLowerCase();
  return orders.filter(order => {
    const matchesStatus = status === 'all' || order.status === status;
    const customerName = ((order as AdminDisplayOrder).customerName || '').toLowerCase();
    const customerId = (order.customerId || '').toLowerCase();
    const orderId = (order.id || '').toLowerCase();
    const orderCode = formatDisplayCode(order.id, 'ORD').toLowerCase();
    const matchesSearch =
      !search ||
      orderId.includes(search) ||
      orderCode.includes(search) ||
      customerId.includes(search) ||
      customerName.includes(search);
    return matchesStatus && matchesSearch;
  });
}

export function relatedSubOrders(orders: SubOrder[], masterOrderId: string) {
  return orders.filter(order => order.masterOrderId === masterOrderId);
}

export function ordersToCsv(orders: MasterOrder[]) {
  const quote = (value: string | number) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  return [
    ['Mã Hiển Thị', 'Mã Đơn (UUID)', 'Khách Hàng', 'Mã Khách Hàng (UUID)', 'Trạng Thái', 'Tổng Tiền (VNĐ)', 'Giảm Giá', 'Trạng Thái TT', 'Thời Gian Tạo'],
    ...orders.map(order => [
      formatDisplayCode(order.id, 'ORD'),
      order.id,
      (order as AdminDisplayOrder).customerName || order.customerId,
      order.customerId,
      order.status,
      order.totalAmount,
      order.discountAmount || 0,
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
