import type { MasterOrder, Payment, Refund, SubOrder } from '../types';

export interface ExtendedMasterOrder extends MasterOrder {
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  subOrdersCount?: number;
  refundCount?: number;
  disputeCount?: number;
  paymentStatus?: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  paymentProvider?: string;
  paymentTransactionId?: string;
  paidAt?: string;
}

const DEMO_SERVICES = [
  { id: "088d874c-a61e-5b58-a7a9-497f32a56901", name: "Mô tô nước – Danabeach", vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0", price: 650000 },
  { id: "3758c297-54ff-5887-89bd-62220227570b", name: "Dù bay trên biển – Danabeach", vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0", price: 800000 },
  { id: "1dd26db5-f863-5b03-b562-ac46f43df9d2", name: "Lớp học lướt sóng Mỹ Khê – DNOA", vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0", price: 1200000 },
  { id: "57c22a43-22fd-57da-894c-257ade02450d", name: "Lặn ngắm san hô Sơn Trà – Loca Travel", vendorId: "10f8ffc5-e987-592f-9d38-426ad7f97066", price: 950000 },
  { id: "cd0c7a8d-b0b8-579a-968c-a1783174a0fc", name: "Chèo SUP Mân Thái – Go N Chill", vendorId: "c7dcff25-f5dc-5fee-a719-5c23b6a899cb", price: 300000 },
  { id: "4190046d-112a-5b93-beb2-bcc8b1cd16d4", name: "Dù bay trên biển – Kajin Vina", vendorId: "baea9605-392b-5003-93f1-60f2c0582f65", price: 700000 },
  { id: "160e67fc-f236-5871-9971-5555cf855d02", name: "Thuê ván SUP – Surf Shack", vendorId: "5a53c8e5-3a76-5383-b104-17b22d4af50f", price: 250000 },
  { id: "c4c56ae2-5f9b-555c-81f1-f0d4c8dd7c1b", name: "Dù bay trên biển – DNOA", vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0", price: 750000 },
  { id: "2cdfabcf-13a2-550a-8f9e-283aad7053c9", name: "Mô tô nước – Kajin Vina", vendorId: "baea9605-392b-5003-93f1-60f2c0582f65", price: 600000 },
  { id: "88e06f4d-fbb7-5700-a45d-0f6ad1cb0d08", name: "Câu cá biển bằng cano riêng – D2Tour", vendorId: "128672c5-94e7-5560-896c-158794913b8c", price: 2500000 },
  { id: "srv-01", name: "Lặn Bình Khí Khám Phá Rạn San Hô Mũi Nghê Sơn Trà", vendorId: "vnd-01", price: 850000 },
  { id: "srv-04", name: "Hoàng Hôn Du Thuyền Catamaran Thưởng Rượu Vang Sơn Trà", vendorId: "vnd-01", price: 1250000 },
  { id: "6d4249fe-a82b-5537-a712-818b23c177d2", name: "Kayak kết hợp trải nghiệm biển – DNOA", vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0", price: 450000 },
  { id: "83ee83c5-c08b-5f6e-924e-db3ceaacd364", name: "Phao chuối – Danabeach", vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0", price: 500000 },
  { id: "d51355fc-bf7e-5366-b199-6272efe7f52b", name: "Lặn ngắm san hô Bãi Nam – Loca Travel", vendorId: "10f8ffc5-e987-592f-9d38-426ad7f97066", price: 850000 },
  { id: "fd6fced2-812e-56ce-817f-80bb3d0841e9", name: "Phao chuối – Kajin Vina", vendorId: "baea9605-392b-5003-93f1-60f2c0582f65", price: 450000 },
  { id: "4b79c4cd-a138-59ca-83c3-d396c1208ac4", name: "Thuê ván lướt sóng – Surf Shack", vendorId: "5a53c8e5-3a76-5383-b104-17b22d4af50f", price: 350000 },
  { id: "5943ecd7-6480-5b4a-b42b-43abc0988776", name: "Học lướt sóng – Surf Shack", vendorId: "5a53c8e5-3a76-5383-b104-17b22d4af50f", price: 850000 },
  { id: "bf8626cd-2271-59ab-9273-4fdd10eb451e", name: "Thuê kayak đơn hoặc đôi – DNOA", vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0", price: 300000 },
  { id: "srv-02", name: "Bay Dù Lượn Trên Không Trung Biển Mỹ Khê", vendorId: "vnd-02", price: 650000 },
  { id: "srv-03", name: "Thuê Mô Tô Nước Sea-Doo GTX 300 Chinh Phục Vịnh Đà Nẵng", vendorId: "vnd-03", price: 700000 }
];

const CUSTOMERS = [
  { id: "usr-customer-01", name: "Nguyễn Văn An", email: "customer@danasea.test", phone: "0905123456" },
  { id: "usr-customer-02", name: "Trần Thị Mai", email: "mai.tran@example.com", phone: "0912345678" },
  { id: "usr-customer-03", name: "Lê Hoàng Long", email: "long.le@example.com", phone: "0934567890" },
  { id: "usr-customer-04", name: "Phạm Thu Hà", email: "ha.pham@example.com", phone: "0945678901" },
  { id: "usr-customer-05", name: "Đặng Minh Tuấn", email: "tuan.dang@example.com", phone: "0956789012" },
  { id: "usr-customer-06", name: "Vũ Bảo Ngọc", email: "ngoc.vu@example.com", phone: "0967890123" }
];

function buildOrdersFixture() {
  const allDates: string[] = [];
  for (let d = 2; d <= 31; d++) {
    const dayStr = d < 10 ? `0${d}` : `${d}`;
    allDates.push(`2026-10-${dayStr}`);
  }

  // Distribution:
  // Days 0..22 (23 days, Oct 02..24): exactly 123 orders (natural weekly rhythm with clear peaks)
  // Days 23..29 (7 days, Oct 25..31): exactly 35 orders [3, 4, 3, 7, 6, 7, 5]
  // Total = 158 orders
  const earlierCounts = [6, 7, 6, 3, 4, 5, 6, 7, 8, 6, 3, 4, 5, 5, 7, 8, 6, 3, 4, 5, 6, 5, 4];
  const last7Counts = [3, 4, 3, 7, 6, 7, 5];
  const dailyCounts = [...earlierCounts, ...last7Counts];

  // Specific popularity pools to produce clear, descending staircases:
  // Top 1: 0 (Mô tô nước – Danabeach) -> 7 in 7d, 24 in 30d
  // Top 2: 1 (Dù bay – Danabeach)     -> 5 in 7d, 18 in 30d
  // Top 3: 2 (Lướt sóng – DNOA)       -> 4 in 7d, 15 in 30d
  // Top 4: 3 (Lặn ngắm san hô – Loca) -> 3 in 7d, 12 in 30d
  // Top 5: 4 (Chèo SUP – Go N Chill)  -> 2 in 7d, 10 in 30d
  const pool7DaysServices = [
    // Top 1: 7 times
    0, 0, 0, 0, 0, 0, 0,
    // Top 2: 5 times
    1, 1, 1, 1, 1,
    // Top 3: 4 times
    2, 2, 2, 2,
    // Top 4: 3 times
    3, 3, 3,
    // Top 5: 2 times
    4, 4,
    // Others: indices 5..20
    5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 5, 7, 8, 10, 12, 13
  ];

  const poolEarlierServices: number[] = [];
  for (let i = 0; i < 17; i++) poolEarlierServices.push(0);
  for (let i = 0; i < 13; i++) poolEarlierServices.push(1);
  for (let i = 0; i < 11; i++) poolEarlierServices.push(2);
  for (let i = 0; i < 9; i++) poolEarlierServices.push(3);
  for (let i = 0; i < 8; i++) poolEarlierServices.push(4);
  for (let i = 0; i < 9; i++) poolEarlierServices.push(9); // Cano câu cá riêng 2.5M
  for (let i = 0; i < 8; i++) poolEarlierServices.push(11); // Catamaran 1.25M

  let otherIdx = 5;
  while (poolEarlierServices.length < 155) {
    if (otherIdx !== 9 && otherIdx !== 11) {
      poolEarlierServices.push(otherIdx);
    }
    otherIdx = 5 + ((otherIdx - 5 + 1) % 16);
  }

  const masterOrders: ExtendedMasterOrder[] = [];
  const subOrders: SubOrder[] = [];
  const payments: Payment[] = [];
  const refunds: Refund[] = [];

  let orderIndex = 1;
  let pool7Pointer = 0;
  let poolEarlyPointer = 0;

  for (let dateIdx = 0; dateIdx < allDates.length; dateIdx++) {
    const dateStr = allDates[dateIdx];
    const countToday = dailyCounts[dateIdx];
    const isLast7 = dateIdx >= 23;

    for (let c = 0; c < countToday; c++) {
      const orderNum = 1000 + orderIndex;
      const orderId = `ORD-2026-${orderNum}`;
      const cust = CUSTOMERS[(orderIndex - 1) % CUSTOMERS.length];

      let status: MasterOrder['status'];
      let paymentStatus: Payment['status'];

      if (isLast7) {
        // 35 orders in 7 days: 28 Completed, 4 Cancelled, 2 Confirmed, 1 Pending
        const relIdx = orderIndex - 123; // 1..35
        if (relIdx === 35) {
          status = 'PENDING';
          paymentStatus = 'PENDING';
        } else if (relIdx === 25 || relIdx === 31) {
          status = 'CONFIRMED';
          paymentStatus = 'COMPLETED';
        } else if (relIdx === 6 || relIdx === 14 || relIdx === 20 || relIdx === 27) {
          status = 'CANCELLED';
          paymentStatus = 'REFUNDED';
        } else {
          status = 'COMPLETED';
          paymentStatus = 'COMPLETED';
        }
      } else {
        // 123 earlier orders: 108 Completed, 13 Cancelled, 2 Confirmed
        if (orderIndex === 30 || orderIndex === 85) {
          status = 'CONFIRMED';
          paymentStatus = 'COMPLETED';
        } else if ([10, 20, 29, 39, 49, 59, 69, 79, 89, 99, 109, 115, 120].includes(orderIndex)) {
          status = 'CANCELLED';
          paymentStatus = 'REFUNDED';
        } else {
          status = 'COMPLETED';
          paymentStatus = 'COMPLETED';
        }
      }

      const hasTwoSubs = (orderIndex % 4 === 0);
      const serviceIdx1 = isLast7
        ? pool7DaysServices[pool7Pointer++ % pool7DaysServices.length]
        : poolEarlierServices[poolEarlyPointer++ % poolEarlierServices.length];
      const service1 = DEMO_SERVICES[serviceIdx1];

      // Realistic quantity: banana boat / SUP / catamaran group bookings
      let qty1 = 1;
      if (service1.price <= 500000 && orderIndex % 3 === 0) qty1 = 2;
      if (service1.id === "83ee83c5-c08b-5f6e-924e-db3ceaacd364" && orderIndex % 2 === 0) qty1 = 3;
      if (service1.id === "srv-04" && orderIndex % 2 === 0) qty1 = 2;

      const hour = 8 + (c * 2) % 11;
      const timeStr = `${hour < 10 ? '0' + hour : hour}:${(c * 17) % 60 < 10 ? '0' + ((c * 17) % 60) : (c * 17) % 60}:00Z`;
      const createdAt = `${dateStr}T${timeStr}`;

      const sub1: SubOrder = {
        id: `SUB-${orderNum}-01`,
        masterOrderId: orderId,
        vendorId: service1.vendorId,
        serviceId: service1.id,
        slotId: `slot-${orderNum}-01`,
        quantity: qty1,
        unitPrice: service1.price,
        subtotalAmount: service1.price * qty1,
        commissionRate: 10,
        commissionAmount: Math.round(service1.price * qty1 * 0.10),
        vendorPayoutAmount: Math.round(service1.price * qty1 * 0.90),
        status: status === 'CONFIRMED' ? 'CONFIRMED' : status === 'CANCELLED' ? 'CANCELLED' : status === 'PENDING' ? 'PENDING' : 'COMPLETED',
        waiverAccepted: true,
        waiverAcceptedAt: createdAt,
        qrSecret: `SEC_QR_${orderNum}_01`,
        checkedInAt: status === 'COMPLETED' ? createdAt : '',
        createdAt,
        updatedAt: createdAt
      };
      subOrders.push(sub1);

      let totalAmount = sub1.subtotalAmount;

      if (hasTwoSubs) {
        const serviceIdx2 = isLast7
          ? pool7DaysServices[pool7Pointer++ % pool7DaysServices.length]
          : poolEarlierServices[poolEarlyPointer++ % poolEarlierServices.length];
        const service2 = DEMO_SERVICES[serviceIdx2];
        const qty2 = 1;

        const sub2: SubOrder = {
          id: `SUB-${orderNum}-02`,
          masterOrderId: orderId,
          vendorId: service2.vendorId,
          serviceId: service2.id,
          slotId: `slot-${orderNum}-02`,
          quantity: qty2,
          unitPrice: service2.price,
          subtotalAmount: service2.price * qty2,
          commissionRate: 10,
          commissionAmount: Math.round(service2.price * qty2 * 0.10),
          vendorPayoutAmount: Math.round(service2.price * qty2 * 0.90),
          status: sub1.status,
          waiverAccepted: true,
          waiverAcceptedAt: createdAt,
          qrSecret: `SEC_QR_${orderNum}_02`,
          checkedInAt: status === 'COMPLETED' ? createdAt : '',
          createdAt,
          updatedAt: createdAt
        };
        subOrders.push(sub2);
        totalAmount += sub2.subtotalAmount;
      }

      const master: ExtendedMasterOrder = {
        id: orderId,
        customerId: cust.id,
        customerName: cust.name,
        customerEmail: cust.email,
        customerPhone: cust.phone,
        status,
        totalAmount,
        discountAmount: 0,
        discountCodeId: '',
        subOrdersCount: hasTwoSubs ? 2 : 1,
        refundCount: status === 'CANCELLED' ? 1 : 0,
        disputeCount: 0,
        paymentStatus,
        paymentProvider: 'VNPAY',
        paymentTransactionId: `TXN_${orderNum}`,
        paidAt: paymentStatus === 'COMPLETED' ? createdAt : undefined,
        createdAt,
        updatedAt: createdAt
      };
      masterOrders.push(master);

      const payment: Payment = {
        id: `PAY-2026-${orderNum}`,
        masterOrderId: orderId,
        provider: 'VNPAY',
        providerTransactionId: `TXN_${orderNum}`,
        amount: totalAmount,
        status: paymentStatus,
        rawWebhookPayload: `{"status":"${paymentStatus}","amount":${totalAmount}}`,
        createdAt,
        updatedAt: createdAt
      };
      payments.push(payment);

      if (status === 'CANCELLED') {
        const refund: Refund = {
          id: `REF-2026-${String(refunds.length + 1).padStart(3, '0')}`,
          subOrderId: sub1.id,
          reason: 'Hủy lịch do thời tiết biển hoặc kế hoạch cá nhân',
          amount: totalAmount,
          refundPercentage: 100,
          status: 'PROCESSED',
          processedAt: createdAt,
          createdAt,
          updatedAt: createdAt
        };
        refunds.push(refund);
      }

      orderIndex++;
    }
  }

  return {
    masterOrders,
    subOrders,
    payments,
    refunds
  };
}

const fixture = buildOrdersFixture();

export const MOCK_ADMIN_MASTER_ORDERS: ExtendedMasterOrder[] = fixture.masterOrders;
export const MOCK_ADMIN_SUB_ORDERS: SubOrder[] = fixture.subOrders;
export const MOCK_ADMIN_PAYMENTS: Payment[] = fixture.payments;
export const MOCK_ADMIN_REFUNDS: Refund[] = fixture.refunds;

export const MOCK_ADMIN_MASTER_ORDERS_128 = MOCK_ADMIN_MASTER_ORDERS;
export const MOCK_ADMIN_SUB_ORDERS_128 = MOCK_ADMIN_SUB_ORDERS;
export const MOCK_ADMIN_PAYMENTS_128 = MOCK_ADMIN_PAYMENTS;
export const MOCK_ADMIN_REFUNDS_128 = MOCK_ADMIN_REFUNDS;
