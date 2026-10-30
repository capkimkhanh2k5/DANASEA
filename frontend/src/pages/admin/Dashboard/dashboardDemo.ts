import { mergeDailyReports, type DashboardData, type DateRange } from './dashboardModel.ts';

// Isolated UI fixture. This module has no API or authentication dependencies.
export function createDashboardDemo(range: DateRange): DashboardData {
  const bookings = [14, 16, 21, 25, 15, 13, 17, 18, 20, 24, 26, 16, 12, 15, 17, 19, 23, 27, 18, 16, 14, 17, 20, 18, 22, 15, 19, 21, 23, 24];
  const cash = [10.5, 12.1, 16.4, 19.8, 11.4, 9.8, 12.9, 13.7, 15.2, 18.6, 20.4, 11.9, 8.9, 11.2, 13.1, 14.8, 17.9, 21.5, 13.4, 12.3, 10.2, 12.8, 15.6, 13.5, 16.8, 11.2, 14.6, 15.9, 19.1, 17.3];
  const dates = mergeDailyReports([], [], range).map(point => point.date);
  const offset = bookings.length - dates.length;
  return {
    bookings: dates.map((periodKey, index) => ({ periodKey, totalOrders: bookings[offset + index], completedOrders: bookings[offset + index] - 2, cancelledOrders: 2 })),
    revenue: dates.map((periodKey, index) => ({ periodKey, collectedCash: Math.round(cash[offset + index] * 1e6), gmv: Math.round(cash[offset + index] * 1e6), platformCommission: Math.round(cash[offset + index] * 1e5), refunds: (offset + index) % 4 === 0 ? 300000 : 0 })),
    operations: { vendors: 3, services: 5, refunds: 2, disputes: 2, settlements: 3 },
    recentOrders: [
      { id: 'DEMO-9021', status: 'PAID', paymentStatus: 'PAID', totalAmount: 3600000, createdAt: `${range.to}T10:12:00+07:00` },
      { id: 'DEMO-9020', status: 'COMPLETED', paymentStatus: 'PAID', totalAmount: 700000, createdAt: `${range.to}T09:35:00+07:00` },
      { id: 'DEMO-9019', status: 'CANCELLED', paymentStatus: 'REFUNDED', totalAmount: 1200000, createdAt: `${range.to}T09:10:00+07:00` },
      { id: 'DEMO-9018', status: 'PENDING_PAYMENT', paymentStatus: 'UNPAID', totalAmount: 6400000, createdAt: `${range.to}T08:40:00+07:00` },
    ],
    errors: [],
  };
}
