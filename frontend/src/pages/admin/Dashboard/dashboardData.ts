import { numeric, type BookingMetric, type DashboardData, type DateRange, type RecentOrder, type RevenueMetric } from './dashboardModel.ts';

export type DashboardGet = (path: string, config: {
  params: Record<string, string | number>;
  signal: AbortSignal;
}) => Promise<{ data: unknown }>;

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid response');
  return value as Record<string, unknown>;
}
function list(value: unknown): unknown[] {
  if (!Array.isArray(value)) throw new Error('Invalid list');
  return value;
}
function count(value: unknown): number {
  const page = record(value);
  list(page.content);
  const total = numeric(page.totalElements);
  if (!Number.isSafeInteger(total) || total < 0) throw new Error('Invalid total');
  return total;
}
function period(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('Invalid period');
  const date = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error('Invalid calendar date');
  return value;
}
function reportRows(value: unknown, range: DateRange): Array<Record<string, unknown> & { periodKey: string }> {
  const seen = new Set<string>();
  return list(record(value).items).map(item => {
    const row = record(item);
    const key = period(row.periodKey);
    if (key < range.from || key > range.to || seen.has(key)) throw new Error('Invalid report period');
    seen.add(key);
    return { ...row, periodKey: key };
  });
}
function orderCount(value: unknown) {
  const result = numeric(value);
  if (!Number.isSafeInteger(result) || result < 0) throw new Error('Invalid order count');
  return result;
}
function parseBookings(value: unknown, range: DateRange): BookingMetric[] {
  return reportRows(value, range).map(row => ({
    periodKey: row.periodKey, totalOrders: orderCount(row.totalOrders), completedOrders: orderCount(row.completedOrders), cancelledOrders: orderCount(row.cancelledOrders),
  }));
}
function parseRevenue(value: unknown, range: DateRange): RevenueMetric[] {
  return reportRows(value, range).map(row => {
    return { periodKey: row.periodKey, collectedCash: numeric(row.collectedCash), gmv: numeric(row.gmv), platformCommission: numeric(row.platformCommission), refunds: numeric(row.refunds) };
  });
}
function parseOrders(value: unknown): RecentOrder[] {
  count(value);
  return list(record(value).content).map(item => {
    const row = record(item);
    if (typeof row.id !== 'string' || typeof row.status !== 'string' || typeof row.paymentStatus !== 'string' || typeof row.createdAt !== 'string') throw new Error('Invalid order');
    if (!Number.isFinite(Date.parse(row.createdAt))) throw new Error('Invalid order timestamp');
    return { id: row.id, status: row.status, paymentStatus: row.paymentStatus, totalAmount: numeric(row.totalAmount), createdAt: row.createdAt };
  });
}

export async function loadAdminDashboard(get: DashboardGet, range: DateRange, signal: AbortSignal): Promise<DashboardData> {
  const errors: string[] = [];
  async function read<T>(path: string, params: Record<string, string | number>, parse: (data: unknown) => T, label: string): Promise<T | null> {
    try {
      const response = await get(path, { params, signal });
      return parse(response.data);
    } catch {
      errors.push(label);
      return null;
    }
  }
  const page = { page: 0, size: 1 };
  const [bookings, revenue, vendors, services, refunds, disputesOpen, disputesReview, settlementDraft, settlementPending, recentOrders] = await Promise.all([
    read('/admin/reports/bookings', { ...range, groupBy: 'day' }, data => parseBookings(data, range), 'báo cáo đặt chỗ'),
    read('/admin/reports/revenue', { ...range, groupBy: 'day' }, data => parseRevenue(data, range), 'báo cáo tiền thu'),
    read('/admin/vendors', { ...page, status: 'PENDING' }, count, 'hồ sơ đối tác'),
    read('/admin/services', { status: 'PENDING_REVIEW' }, data => list(data).length, 'dịch vụ chờ duyệt'),
    read('/admin/refunds', { ...page, status: 'PENDING' }, count, 'yêu cầu hoàn tiền'),
    read('/admin/disputes', { ...page, status: 'OPEN' }, count, 'khiếu nại mới'),
    read('/admin/disputes', { ...page, status: 'UNDER_REVIEW' }, count, 'khiếu nại đang xem xét'),
    read('/admin/settlements', { ...page, status: 'DRAFT' }, count, 'kỳ quyết toán nháp'),
    read('/admin/settlements', { ...page, status: 'PENDING' }, count, 'kỳ quyết toán chờ xử lý'),
    read('/admin/orders', { page: 0, size: 5, sort: 'createdAt,desc' }, parseOrders, 'đơn hàng gần đây'),
  ]);
  return {
    bookings, revenue, recentOrders, errors,
    operations: {
      vendors, services, refunds,
      disputes: disputesOpen === null || disputesReview === null ? null : disputesOpen + disputesReview,
      settlements: settlementDraft === null || settlementPending === null ? null : settlementDraft + settlementPending,
    },
  };
}
