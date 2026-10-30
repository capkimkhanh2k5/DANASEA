export interface DateRange { from: string; to: string }
export interface BookingMetric {
  periodKey: string;
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
}
export interface RevenueMetric {
  periodKey: string;
  collectedCash: number | string;
  gmv: number | string;
  platformCommission: number | string;
  refunds: number | string;
}
export interface DailyMetric {
  date: string;
  bookings: number;
  completed: number;
  cancelled: number;
  revenue: number;
  commission: number;
  gmv: number;
  refunds: number;
}
export type OperationKind = 'vendors' | 'services' | 'refunds' | 'disputes' | 'settlements';
export type OperationCounts = Record<OperationKind, number | null>;
export const operationGroups: { key: OperationKind; label: string; title: string; description: string; path: string; priority: number }[] = [
  { key: 'disputes', label: 'Khiếu nại', title: 'khiếu nại đang chờ giải quyết', description: 'Đối chiếu phản hồi và bằng chứng từ khách hàng, đối tác.', path: '/admin/disputes', priority: 1 },
  { key: 'refunds', label: 'Đơn hàng', title: 'yêu cầu hoàn tiền cần theo dõi', description: 'Kiểm tra tiến trình hoàn tiền và thông tin đơn liên quan.', path: '/admin/orders', priority: 1 },
  { key: 'vendors', label: 'Đối tác', title: 'hồ sơ đối tác chờ thẩm định', description: 'Rà soát hồ sơ pháp lý và chứng từ của đối tác.', path: '/admin/vendor-approval', priority: 2 },
  { key: 'services', label: 'Dịch vụ', title: 'dịch vụ đang chờ kiểm duyệt', description: 'Kiểm tra nội dung, điều kiện và hồ sơ an toàn trải nghiệm.', path: '/admin/service-approval', priority: 2 },
  { key: 'settlements', label: 'Quyết toán', title: 'kỳ quyết toán chưa chốt', description: 'Rà soát bảng kê và các khoản đối soát với đối tác.', path: '/admin/payouts', priority: 3 },
];
export interface RecentOrder {
  id: string;
  status: string;
  paymentStatus: string;
  totalAmount: number;
  createdAt: string;
}
export interface DashboardData {
  bookings: BookingMetric[] | null;
  revenue: RevenueMetric[] | null;
  operations: OperationCounts;
  recentOrders: RecentOrder[] | null;
  errors: string[];
}

export function getDateRange(days: 7 | 30, now = new Date()): DateRange {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const part = (type: string) => parts.find(p => p.type === type)!.value;
  const to = `${part('year')}-${part('month')}-${part('day')}`;
  const start = new Date(`${to}T00:00:00Z`);
  start.setUTCDate(start.getUTCDate() - days + 1);
  return { from: start.toISOString().slice(0, 10), to };
}

export function numeric(value: unknown): number {
  if ((typeof value !== 'number' && typeof value !== 'string') || (typeof value === 'string' && value.trim() === '') || !Number.isFinite(Number(value))) {
    throw new Error('Dữ liệu số không hợp lệ');
  }
  return Number(value);
}

export function mergeDailyReports(bookings: BookingMetric[], revenue: RevenueMetric[], range: DateRange): DailyMetric[] {
  const bookingMap = new Map(bookings.map(point => [point.periodKey, point]));
  const revenueMap = new Map(revenue.map(point => [point.periodKey, point]));
  const points: DailyMetric[] = [];
  const cursor = new Date(`${range.from}T00:00:00Z`);
  while (cursor.toISOString().slice(0, 10) <= range.to) {
    const date = cursor.toISOString().slice(0, 10);
    const booking = bookingMap.get(date);
    const money = revenueMap.get(date);
    points.push({
      date, bookings: booking ? numeric(booking.totalOrders) : 0,
      completed: booking ? numeric(booking.completedOrders) : 0,
      cancelled: booking ? numeric(booking.cancelledOrders) : 0,
      revenue: money ? numeric(money.collectedCash) : 0,
      commission: money ? numeric(money.platformCommission) : 0,
      gmv: money ? numeric(money.gmv) : 0,
      refunds: money ? numeric(money.refunds) : 0,
    });
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return points;
}

export function summarizeMetrics(points: DailyMetric[]) {
  return points.reduce((sum, point) => ({
    bookings: sum.bookings + point.bookings, completed: sum.completed + point.completed,
    cancelled: sum.cancelled + point.cancelled, revenue: sum.revenue + point.revenue,
    commission: sum.commission + point.commission, gmv: sum.gmv + point.gmv, refunds: sum.refunds + point.refunds,
  }), { bookings: 0, completed: 0, cancelled: 0, revenue: 0, commission: 0, gmv: 0, refunds: 0 });
}

export function chartDomain(values: number[]) {
  const low = Math.min(0, ...values);
  const high = Math.max(0, ...values);
  const roughStep = (high - low || 4) / 4;
  const magnitude = 10 ** Math.floor(Math.log10(roughStep));
  const step = Math.ceil(roughStep / magnitude) * magnitude;
  return { min: Math.floor(low / step) * step, max: Math.ceil((high || (low === 0 ? 4 : 0)) / step) * step };
}

export const formatNumber = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
export const formatVND = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);
export const shortDate = (date: string) => `${date.slice(8, 10)}/${date.slice(5, 7)}`;
