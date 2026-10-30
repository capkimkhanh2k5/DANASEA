import { mergeDailyReports, type DashboardData, type DateRange, type HotServiceItem, type RecentOrder, type TopVendorItem } from './dashboardModel';
import {
  MOCK_ADMIN_MASTER_ORDERS,
  MOCK_ADMIN_REFUNDS,
  MOCK_ADMIN_SERVICES,
  MOCK_ADMIN_SUB_ORDERS,
  MOCK_ADMIN_VENDORS
} from '../../../data/adminMockData';

const DEMO_SERVICE_NAMES: Record<string, string> = {
  '088d874c-a61e-5b58-a7a9-497f32a56901': 'Mô tô nước – Danabeach',
  '3758c297-54ff-5887-89bd-62220227570b': 'Dù bay trên biển – Danabeach',
  '1dd26db5-f863-5b03-b562-ac46f43df9d2': 'Lớp học lướt sóng Mỹ Khê – DNOA',
  '57c22a43-22fd-57da-894c-257ade02450d': 'Lặn ngắm san hô Sơn Trà – Loca Travel',
  '4190046d-112a-5b93-beb2-bcc8b1cd16d4': 'Dù bay trên biển – Kajin Vina',
  '160e67fc-f236-5871-9971-5555cf855d02': 'Thuê ván SUP – Surf Shack',
  '4b79c4cd-a138-59ca-83c3-d396c1208ac4': 'Thuê ván lướt sóng – Surf Shack',
  'cd0c7a8d-b0b8-579a-968c-a1783174a0fc': 'Chèo SUP Mân Thái – Go N Chill',
  '6d4249fe-a82b-5537-a712-818b23c177d2': 'Kayak kết hợp trải nghiệm biển – DNOA',
  '2cdfabcf-13a2-550a-8f9e-283aad7053c9': 'Mô tô nước – Kajin Vina',
  '83ee83c5-c08b-5f6e-924e-db3ceaacd364': 'Phao chuối – Danabeach',
  '5943ecd7-6480-5b4a-b42b-43abc0988776': 'Học lướt sóng – Surf Shack',
  'd51355fc-bf7e-5366-b199-6272efe7f52b': 'Lặn ngắm san hô Bãi Nam – Loca Travel',
  'c4c56ae2-5f9b-555c-81f1-f0d4c8dd7c1b': 'Dù bay trên biển – DNOA',
  '88e06f4d-fbb7-5700-a45d-0f6ad1cb0d08': 'Câu cá biển bằng cano riêng – D2Tour',
  'bf8626cd-2271-59ab-9273-4fdd10eb451e': 'Thuê kayak đơn hoặc đôi – DNOA',
  'fd6fced2-812e-56ce-817f-80bb3d0841e9': 'Phao chuối – Kajin Vina',
  'srv-01': 'Lặn Bình Khí Khám Phá Rạn San Hô Mũi Nghê Sơn Trà',
  'srv-02': 'Bay Dù Lượn Trên Không Trung Biển Mỹ Khê',
  'srv-03': 'Thuê Mô Tô Nước Sea-Doo GTX 300 Chinh Phục Vịnh Đà Nẵng',
  'srv-04': 'Hoàng Hôn Du Thuyền Catamaran Thưởng Rượu Vang Sơn Trà',
};

function getVendorName(vendorId: string): string {
  const found = MOCK_ADMIN_VENDORS.find(v => v.id === vendorId);
  return found?.businessName || `Đối tác #${vendorId.slice(0, 8)}`;
}

function getServiceName(serviceId: string): string {
  if (DEMO_SERVICE_NAMES[serviceId]) return DEMO_SERVICE_NAMES[serviceId];
  const found = MOCK_ADMIN_SERVICES.find(s => s.id === serviceId);
  return found?.name || `Trải nghiệm #${serviceId.slice(0, 8)}`;
}

function computeHotServices(range: DateRange): HotServiceItem[] {
  const inRange = MOCK_ADMIN_SUB_ORDERS.filter(s => {
    const d = (s.createdAt || '').slice(0, 10);
    return d >= range.from && d <= range.to;
  });
  const map = new Map<string, { serviceId: string; serviceName: string; vendorName?: string; bookingCount: number }>();
  for (const s of inRange) {
    const existing = map.get(s.serviceId) || {
      serviceId: s.serviceId,
      serviceName: getServiceName(s.serviceId),
      vendorName: getVendorName(s.vendorId),
      bookingCount: 0,
    };
    existing.bookingCount += 1;
    map.set(s.serviceId, existing);
  }
  const items = Array.from(map.values()).sort((a, b) => b.bookingCount - a.bookingCount || a.serviceName.localeCompare(b.serviceName)).slice(0, 5);
  const max = Math.max(1, ...items.map(i => i.bookingCount));
  return items.map(i => ({ ...i, sharePercent: Math.round((i.bookingCount / max) * 100) }));
}

function computeTopVendors(range: DateRange): TopVendorItem[] {
  const inRange = MOCK_ADMIN_SUB_ORDERS.filter(s => {
    const d = (s.createdAt || '').slice(0, 10);
    return d >= range.from && d <= range.to && ['CONFIRMED', 'COMPLETED', 'PAID'].includes(s.status);
  });
  const map = new Map<string, { vendorId: string; vendorName: string; revenue: number; completedOrders: number }>();
  for (const s of inRange) {
    const existing = map.get(s.vendorId) || {
      vendorId: s.vendorId,
      vendorName: getVendorName(s.vendorId),
      revenue: 0,
      completedOrders: 0,
    };
    existing.revenue += s.subtotalAmount;
    existing.completedOrders += 1;
    map.set(s.vendorId, existing);
  }
  const items = Array.from(map.values()).sort((a, b) => b.revenue - a.revenue || b.completedOrders - a.completedOrders).slice(0, 5);
  const max = Math.max(1, ...items.map(i => i.revenue));
  return items.map(i => ({ ...i, sharePercent: Math.round((i.revenue / max) * 100) }));
}

// Reconciled with Single Source of Truth from MOCK_ADMIN_MASTER_ORDERS (158 orders)
export function createDashboardDemo(range: DateRange): DashboardData {
  const dates = mergeDailyReports([], [], range).map(point => point.date);

  const bookings = dates.map(periodKey => {
    const ordersToday = MOCK_ADMIN_MASTER_ORDERS.filter(o => (o.createdAt || '').slice(0, 10) === periodKey);
    return {
      periodKey,
      totalOrders: ordersToday.length,
      completedOrders: ordersToday.filter(o => o.status === 'COMPLETED').length,
      cancelledOrders: ordersToday.filter(o => o.status === 'CANCELLED').length,
    };
  });

  const revenue = dates.map(periodKey => {
    const ordersToday = MOCK_ADMIN_MASTER_ORDERS.filter(o => (o.createdAt || '').slice(0, 10) === periodKey);
    const completedToday = ordersToday.filter(o => o.status === 'COMPLETED');
    const collectedCash = completedToday.reduce((sum, o) => sum + o.totalAmount, 0);
    const gmv = ordersToday.filter(o => o.status !== 'CANCELLED').reduce((sum, o) => sum + o.totalAmount, 0);
    const refundsToday = MOCK_ADMIN_REFUNDS.filter(r => (r.createdAt || '').slice(0, 10) === periodKey)
      .reduce((sum, r) => sum + r.amount, 0);

    return {
      periodKey,
      collectedCash,
      gmv,
      platformCommission: Math.round(collectedCash * 0.10),
      refunds: refundsToday,
    };
  });

  const recentOrders: RecentOrder[] = MOCK_ADMIN_MASTER_ORDERS.slice(-5).reverse().map(o => ({
    id: o.id,
    status: o.status,
    paymentStatus: o.paymentStatus || 'PAID',
    totalAmount: o.totalAmount,
    createdAt: o.createdAt || '',
  }));

  return {
    bookings,
    revenue,
    // Đồng nhất hoàn toàn với 4 nhóm công việc chờ xử lý (tổng: 6 mục)
    // - vendors: 1 (Đối tác DaNaFi đang PENDING)
    // - services: 2 (Dịch vụ srv-03, srv-04 đang PENDING_APPROVAL)
    // - settlements: 2 (Quyết toán PAY-2026-10-002, PAY-2026-10-003 đang REQUESTED)
    // - disputes: 1 (Khiếu nại DSP-01 đang OPEN)
    // - refunds: 2
    operations: { vendors: 1, services: 2, settlements: 2, disputes: 1, refunds: 2 },
    hotServices: computeHotServices(range),
    topVendors: computeTopVendors(range),
    recentOrders,
    errors: [],
  };
}
