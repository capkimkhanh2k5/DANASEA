import type { Service, User, ServiceSlot, ServiceImage, MasterOrder, SubOrder } from './types';

export const CATEGORIES = [
  { id: 'cat-1', name: 'Chèo SUP', icon: 'surfing' },
  { id: 'cat-2', name: 'Cano lướt sóng', icon: 'speed' },
  { id: 'cat-3', name: 'Lặn biển ngắm san hô', icon: 'scuba_diving' },
  { id: 'cat-4', name: 'Chèo Kayak', icon: 'kayaking' },
  { id: 'cat-5', name: 'Tour biển đảo', icon: 'sailing' },
];

export const FEATURED_SERVICES: Service[] = [
  {
    id: "tour-1",
    categoryId: "cat-1",
    name: "Chèo SUP đón bình minh Mỹ Khê",
    description: "Trải nghiệm ngắm bình minh trên biển Mỹ Khê bằng ván SUP, chụp ảnh lưu niệm.",
    basePrice: 280000,
    durationMinutes: 120,
    locationName: "Biển Mỹ Khê, Đà Nẵng",
    status: 'APPROVED',
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "tour-2",
    categoryId: "cat-2",
    name: "Khám phá biển bằng cano",
    description: "Cảm giác mạnh với cano tốc độ cao trên biển.",
    basePrice: 450000,
    durationMinutes: 210,
    locationName: "Mũi Nghê - Sơn Trà",
    status: 'APPROVED',
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "tour-3",
    categoryId: "cat-3",
    name: "Trải nghiệm lặn biển ngắm san hô",
    description: "Khám phá thế giới dưới đại dương, nước trong vắt.",
    basePrice: 520000,
    durationMinutes: 150,
    locationName: "Bán đảo Sơn Trà",
    status: 'APPROVED',
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  }
];

export const MOCK_SLOTS: ServiceSlot[] = [
  { id: 'slot-1', serviceId: 'tour-1', date: '2024-10-28', startTime: '05:00', endTime: '07:00', capacity: 12, bookedCount: 6, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'slot-2', serviceId: 'tour-1', date: '2024-10-28', startTime: '07:15', endTime: '09:15', capacity: 12, bookedCount: 4, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'slot-3', serviceId: 'tour-1', date: '2024-10-29', startTime: '05:00', endTime: '07:00', capacity: 12, bookedCount: 12, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'slot-4', serviceId: 'tour-2', date: '2024-10-28', startTime: '08:00', endTime: '11:30', capacity: 8, bookedCount: 2, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'slot-5', serviceId: 'tour-2', date: '2024-10-29', startTime: '13:00', endTime: '16:30', capacity: 8, bookedCount: 8, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'slot-6', serviceId: 'tour-3', date: '2024-10-28', startTime: '07:30', endTime: '10:00', capacity: 15, bookedCount: 5, status: 'OPEN', createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
];

export const MOCK_IMAGES: ServiceImage[] = [
  { id: 'img-1', serviceId: 'tour-1', url: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAUQYcWnZo3_Kb31_qfroEFXgTerymIKQCOpVaTesW0pBt3yg2CiNGXu2vGlRckKERopIXFerqe-1sAv_OkI5zmzYO_iK7aXukpJ89Oyny7Z8VECdpaEufZqmZucGaCmzQZTiCLwcwXH1COA5JTI42EQeyE0vnRTJvI9enpzD8cKsC5HsI2Cv6MuYVgbn52pZbXVgeex4h-_Vwb6-ZS20BNeUb30ntwL8mB9p5n-EX-6dvp2hzW-l9l7g', imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAUQYcWnZo3_Kb31_qfroEFXgTerymIKQCOpVaTesW0pBt3yg2CiNGXu2vGlRckKERopIXFerqe-1sAv_OkI5zmzYO_iK7aXukpJ89Oyny7Z8VECdpaEufZqmZucGaCmzQZTiCLwcwXH1COA5JTI42EQeyE0vnRTJvI9enpzD8cKsC5HsI2Cv6MuYVgbn52pZbXVgeex4h-_Vwb6-ZS20BNeUb30ntwL8mB9p5n-EX-6dvp2hzW-l9l7g', isPrimary: true, sortOrder: 1, createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'img-2', serviceId: 'tour-2', url: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAF387oWmm_8nPnxR-c5ZKJMVr8nMVfG8okUUsvmty6gUqPyrkER62dTJft9DbFBKYsiDAaTUHOk6LWNLdvML_m9NOWb-lz2HLP_sMyXHn9oGXAnpcUIkD9L7kzBVzvSwLrqjLy-wRjeOm4cxDZ_2s-9Uc5zHCmUajHe5me2F7HKaJwSdhYi4HSkDigSN9mR0Yj8qj_MwrQTl44GFqDvQ64LrIX7Mimwbzz7sIhGsU5Zq2UfDPszMDA5w', imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAF387oWmm_8nPnxR-c5ZKJMVr8nMVfG8okUUsvmty6gUqPyrkER62dTJft9DbFBKYsiDAaTUHOk6LWNLdvML_m9NOWb-lz2HLP_sMyXHn9oGXAnpcUIkD9L7kzBVzvSwLrqjLy-wRjeOm4cxDZ_2s-9Uc5zHCmUajHe5me2F7HKaJwSdhYi4HSkDigSN9mR0Yj8qj_MwrQTl44GFqDvQ64LrIX7Mimwbzz7sIhGsU5Zq2UfDPszMDA5w', isPrimary: true, sortOrder: 1, createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
  { id: 'img-3', serviceId: 'tour-3', url: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBtasf_r_9GK6iKA93o15qgwNTn_a6oh6Oc3CRQdBnUvmsGuzSc53UYu0XpAi9fMyzzkukAKhpJweN3M9uZr7CVwawANfuuZbOspABKxKMzAy_tOrEM9qNn3IK7LMvUrGwGsmervLKLdXQgQ13XdORa6gIhStPR5QcE2lCDFPX5DY-2c7N9fMUKZSw06Gr9VppMAKpilqtxIlIwZON8BCwJkypr7xoPm1RJ-hlVcmZGbG9XxE1C_KqeSQ', imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBtasf_r_9GK6iKA93o15qgwNTn_a6oh6Oc3CRQdBnUvmsGuzSc53UYu0XpAi9fMyzzkukAKhpJweN3M9uZr7CVwawANfuuZbOspABKxKMzAy_tOrEM9qNn3IK7LMvUrGwGsmervLKLdXQgQ13XdORa6gIhStPR5QcE2lCDFPX5DY-2c7N9fMUKZSw06Gr9VppMAKpilqtxIlIwZON8BCwJkypr7xoPm1RJ-hlVcmZGbG9XxE1C_KqeSQ', isPrimary: true, sortOrder: 1, createdAt: "2024-01-01T00:00:00Z", updatedAt: "2024-01-01T00:00:00Z" },
];

export const MOCK_TEST_ACCOUNTS: Record<'customer' | 'vendor' | 'admin', User> = {
  customer: {
    id: "user-cust-01",
    phone: "0912345678",
    email: "customer@danasea.vn",
    passwordHash: "123456",
    fullName: "Lê Thu Thảo (Du khách)",
    avatarUrl: "https://lh3.googleusercontent.com/aida-public/AB6AXuCyLkVgLH1bg5qkuBnTpbXnIRb0jQNfLavasaNwMNb63HfhRK5NHCVqztNBT0vfFqBLb2E0UkQhryLN1sNHSQj0iYgZ7Iex5ObWxha95JUP91YHSTKcBO09lHUy_BUxZRIMEAklv7iFRWstVjV5hKuyUgWPlPxt13Zu7geQ6Dxo8d5Djz7BmWuvXJUWab8x1QMFrAKsJc-az3eVPI_JYI2KCit1q7C1jtgzv_7TVW2i3OUHeQ3-bAsvVg",
    role: 'CUSTOMER',
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  vendor: {
    id: "user-vendor-01",
    phone: "0987654321",
    email: "vendor@danasea.vn",
    passwordHash: "123456",
    fullName: "Sơn Trà Eco Watersports (Đối tác)",
    avatarUrl: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=150&auto=format&fit=crop&q=80",
    role: 'VENDOR',
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  admin: {
    id: "user-admin-01",
    phone: "0909999888",
    email: "admin@danasea.vn",
    passwordHash: "123456",
    fullName: "Ban Quản Lý Cảng Vụ (Admin)",
    avatarUrl: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&auto=format&fit=crop&q=80",
    role: 'ADMIN',
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  }
};

export const CURRENT_USER: User = MOCK_TEST_ACCOUNTS.customer;

export const MOCK_MASTER_ORDERS: MasterOrder[] = [
  {
    id: "master-1",
    createdAt: "2024-10-27T08:00:00Z",
    updatedAt: "2024-10-27T08:00:00Z",
    customerId: "user-test-01",
    status: 'CONFIRMED', // Assuming ACTIVE means PAID/Confirmed for this mock
    totalAmount: 1030000,
    discountAmount: 0,
    discountCodeId: ""
  },
  {
    id: "master-2",
    createdAt: "2024-09-15T09:30:00Z",
    updatedAt: "2024-09-15T09:30:00Z",
    customerId: "user-test-01",
    status: 'CONFIRMED', // COMPLETED equivalent
    totalAmount: 520000,
    discountAmount: 0,
    discountCodeId: ""
  },
  {
    id: "master-3",
    createdAt: "2024-08-10T14:20:00Z",
    updatedAt: "2024-08-10T14:20:00Z",
    customerId: "user-test-01",
    status: 'CANCELLED', // CANCELLED equivalent
    totalAmount: 450000,
    discountAmount: 0,
    discountCodeId: ""
  }
];

export const MOCK_SUB_ORDERS: SubOrder[] = [
  {
    id: "sub-1",
    createdAt: "2024-10-27T08:00:00Z",
    updatedAt: "2024-10-27T08:00:00Z",
    masterOrderId: "master-1",
    vendorId: "vendor-1",
    serviceId: "tour-1",
    slotId: "slot-1",
    quantity: 2,
    unitPrice: 280000,
    subtotalAmount: 560000,
    commissionRate: 10,
    commissionAmount: 56000,
    vendorPayoutAmount: 504000,
    status: 'CONFIRMED',
    waiverAccepted: true,
    waiverAcceptedAt: "2024-10-27T08:00:00Z",
    qrSecret: "SECRET_QR_1",
    checkedInAt: ""
  },
  {
    id: "sub-2",
    createdAt: "2024-10-27T08:00:00Z",
    updatedAt: "2024-10-27T08:00:00Z",
    masterOrderId: "master-1",
    vendorId: "vendor-1",
    serviceId: "tour-3",
    slotId: "slot-6",
    quantity: 1,
    unitPrice: 470000,
    subtotalAmount: 470000,
    commissionRate: 10,
    commissionAmount: 47000,
    vendorPayoutAmount: 423000,
    status: 'CONFIRMED',
    waiverAccepted: true,
    waiverAcceptedAt: "2024-10-27T08:00:00Z",
    qrSecret: "SECRET_QR_2",
    checkedInAt: ""
  },
  {
    id: "sub-3",
    createdAt: "2024-09-15T09:30:00Z",
    updatedAt: "2024-09-15T09:30:00Z",
    masterOrderId: "master-2",
    vendorId: "vendor-1",
    serviceId: "tour-3",
    slotId: "slot-6",
    quantity: 1,
    unitPrice: 520000,
    subtotalAmount: 520000,
    commissionRate: 10,
    commissionAmount: 52000,
    vendorPayoutAmount: 468000,
    status: 'COMPLETED', // Completed or inactive
    waiverAccepted: true,
    waiverAcceptedAt: "2024-09-15T09:30:00Z",
    qrSecret: "SECRET_QR_3",
    checkedInAt: "2024-09-16T07:30:00Z"
  }
];

export const MOCK_DISPUTES: any[] = [
  {
    id: "DSP-01",
    subOrderId: "SUB-89412-01",
    raisedBy: "CST-01",
    category: "SERVICE_QUALITY",
    description: "Thái độ hướng dẫn viên không tốt, thiết bị lặn quá cũ gây nước vào kính.",
    status: "IN_PROGRESS",
    resolutionNote: "",
    resolvedBy: null,
    resolvedAt: null,
    createdAt: "2024-10-18T10:00:00Z"
  }
];
