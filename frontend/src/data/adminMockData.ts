import type {
  User,
  Vendor,
  Service,
  Category,
  Dispute,
  PayoutRequest,
  DiscountCode,
  SystemConfig,
  AuditLog,
  MasterOrder,
  SubOrder,
  Payment,
  Refund,
  Settlement
} from '../types';
import {
  MOCK_ADMIN_MASTER_ORDERS_128,
  MOCK_ADMIN_SUB_ORDERS_128,
  MOCK_ADMIN_PAYMENTS_128,
  MOCK_ADMIN_REFUNDS_128
} from './adminOrdersFixture';

// =========================================================================
// 1. DEMO USERS (4 Admins, 14 Vendors, 8 Customers - Tổng: 26 tài khoản)
// =========================================================================
export const MOCK_ADMIN_USERS: User[] = [
  // --- ADMIN ACCOUNTS ---
  {
    id: "usr-adm-01",
    fullName: "Vũ Hải Đăng",
    email: "dang.cangvu@danasea.gov.vn",
    phone: "0905112233",
    passwordHash: "hash_admin_01",
    role: "ADMIN",
    avatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-10T08:00:00Z",
    updatedAt: "2026-10-20T14:30:00Z"
  },
  {
    id: "usr-adm-02",
    fullName: "Trần Minh Quân",
    email: "quan.dieuhanh@danasea.vn",
    phone: "0914889900",
    passwordHash: "hash_admin_02",
    role: "ADMIN",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-02-15T09:00:00Z",
    updatedAt: "2026-10-18T11:00:00Z"
  },
  {
    id: "3e3005a7-001a-5d9f-b4ee-993b52a01d61",
    fullName: "DANASEA DEMO Admin",
    email: "admin_demo@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_admin_03",
    role: "ADMIN",
    avatarUrl: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T08:00:00Z",
    updatedAt: "2026-10-28T10:00:00Z"
  },
  {
    id: "11111111-1111-1111-1111-111111111111",
    fullName: "Quản Trị Viên Danasea",
    email: "admin@danasea.vn",
    phone: "0905000001",
    passwordHash: "hash_admin_04",
    role: "ADMIN",
    avatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T08:00:00Z",
    updatedAt: "2026-10-28T10:00:00Z"
  },

  // --- VENDOR ACCOUNTS (Liên kết 1-1 với hồ sơ Vendor) ---
  {
    id: "865a115c-d7f9-5ebd-a36b-1834a590177a",
    fullName: "Lê Cảnh Tuấn (Danabeach Club)",
    email: "vender_1@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_01",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-01T07:30:00Z",
    updatedAt: "2026-10-25T16:00:00Z"
  },
  {
    id: "322173c1-c330-54d9-9d1b-b69c7e480920",
    fullName: "Trần Đình Trọng (Công ty TNHH Kajin Vina)",
    email: "vender_2@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_02",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-12T10:15:00Z",
    updatedAt: "2026-10-24T09:20:00Z"
  },
  {
    id: "3ad7d9af-4d4c-59b7-9897-288fcce5be79",
    fullName: "Nguyễn Thành Long (Da Nang Outdoor Adventures)",
    email: "vender_3@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_03",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-20T08:00:00Z",
    updatedAt: "2026-10-26T14:10:00Z"
  },
  {
    id: "44d76fc6-fc8b-5031-87ce-2ee2191bd900",
    fullName: "Phạm Minh Khang (Loca Travel Đà Nẵng)",
    email: "vender_4@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_04",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-04-01T09:00:00Z",
    updatedAt: "2026-10-27T11:00:00Z"
  },
  {
    id: "5777bfcf-0c5a-5957-a83f-83a3253668e4",
    fullName: "Hoàng Bảo Anh (Công ty Cổ phần D BLUE)",
    email: "vender_5@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_05",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-04-10T10:30:00Z",
    updatedAt: "2026-10-28T09:15:00Z"
  },
  {
    id: "4e428199-0ca1-5e4c-98dd-35d20a52692e",
    fullName: "Nguyễn Văn Khuê (Khuê Nguyễn Câu Mực Đêm)",
    email: "vender_6@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_06",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: true,
    locale: "vi",
    createdAt: "2024-04-15T08:00:00Z",
    updatedAt: "2026-10-22T08:15:00Z"
  },
  {
    id: "1066fe9e-e4d4-5aa9-9178-84f1d006f75b",
    fullName: "Phan Văn Hải (DaNaFi Tour Câu Cá)",
    email: "vender_7@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_07",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-04-20T14:00:00Z",
    updatedAt: "2026-10-25T16:00:00Z"
  },
  {
    id: "06223bd1-3fae-525c-afc3-4f42943be6e3",
    fullName: "Võ Hoài Nam (Go N Chill SUP Club)",
    email: "vender_8@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_08",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-05-01T07:00:00Z",
    updatedAt: "2026-10-26T15:30:00Z"
  },
  {
    id: "32797f00-04d0-53fc-b55d-6672984aba55",
    fullName: "Đặng Quốc Bảo (Surf Shack Da Nang)",
    email: "vender_9@danasea.test",
    phone: "0905123456",
    passwordHash: "hash_vnd_09",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-05-10T11:00:00Z",
    updatedAt: "2026-10-27T08:45:00Z"
  },
  {
    id: "22222222-2222-2222-2222-222222222222",
    fullName: "Lê Quang Huy (Công Ty Biển Xanh Danasea)",
    email: "vendor@danasea.vn",
    phone: "0905000002",
    passwordHash: "hash_vnd_10",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T08:00:00Z",
    updatedAt: "2026-10-28T10:00:00Z"
  },
  {
    id: "usr-vnd-01",
    fullName: "Lê Cảnh Tuấn",
    email: "tuan.sontramarinedivers@gmail.com",
    phone: "0935123456",
    passwordHash: "hash_vnd_legacy_01",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-01T07:30:00Z",
    updatedAt: "2026-10-25T16:00:00Z"
  },
  {
    id: "usr-vnd-02",
    fullName: "Nguyễn Thu Thảo",
    email: "thao.danangparasailing@outlook.com",
    phone: "0988654321",
    passwordHash: "hash_vnd_legacy_02",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-12T10:15:00Z",
    updatedAt: "2026-10-24T09:20:00Z"
  },
  {
    id: "usr-vnd-03",
    fullName: "Hoàng Đức Nam",
    email: "nam.jetskidn@gmail.com",
    phone: "0905778899",
    passwordHash: "hash_vnd_legacy_03",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: true,
    locale: "vi",
    createdAt: "2024-04-05T14:00:00Z",
    updatedAt: "2026-10-22T08:15:00Z"
  },
  {
    id: "usr-vnd-04",
    fullName: "Lê Hoàng Long",
    email: "long.kayaksontra@gmail.com",
    phone: "0915223344",
    passwordHash: "hash_vnd_legacy_04",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-04-10T10:00:00Z",
    updatedAt: "2026-10-18T10:00:00Z"
  },

  // --- CUSTOMER ACCOUNTS (Họ tên tiếng Việt chuẩn mực, thông tin rõ ràng) ---
  {
    id: "usr-cst-01",
    fullName: "Nguyễn Văn An",
    email: "an.nguyen@example.com",
    phone: "0912345678",
    passwordHash: "hash_cst_01",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-05-01T10:00:00Z",
    updatedAt: "2026-10-26T15:00:00Z"
  },
  {
    id: "usr-cst-02",
    fullName: "Trần Thị Mai Lan",
    email: "lan.tran@gmail.com",
    phone: "0977112244",
    passwordHash: "hash_cst_02",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-05-18T11:20:00Z",
    updatedAt: "2026-10-27T08:45:00Z"
  },
  {
    id: "usr-cst-03",
    fullName: "Phạm Quốc Hùng",
    email: "hung.pham@vietjetair.com",
    phone: "0934998877",
    passwordHash: "hash_cst_03",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: false,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-06-02T13:40:00Z",
    updatedAt: "2026-10-25T17:10:00Z"
  },
  {
    id: "46c8f2ea-2be5-5365-be80-31fd57d9e260",
    fullName: "Nguyễn Văn Minh",
    email: "minh.nguyen@gmail.com",
    phone: "0905123890",
    passwordHash: "hash_cst_04",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-06-15T08:30:00Z",
    updatedAt: "2026-10-28T10:00:00Z"
  },
  {
    id: "1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0",
    fullName: "Lê Thu Thảo",
    email: "thao.lethu@danangtrip.vn",
    phone: "0918345671",
    passwordHash: "hash_cst_05",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-07-01T09:15:00Z",
    updatedAt: "2026-10-28T09:30:00Z"
  },
  {
    id: "33333333-3333-3333-3333-333333333333",
    fullName: "Nguyễn Văn Khách",
    email: "customer@danasea.vn",
    phone: "0905000003",
    passwordHash: "hash_cst_06",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-01-01T08:00:00Z",
    updatedAt: "2026-10-28T11:00:00Z"
  },
  {
    id: "usr-cst-07",
    fullName: "Hoàng Đức Nam",
    email: "nam.hoangduc@fpt.com.vn",
    phone: "0935889922",
    passwordHash: "hash_cst_07",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-07-15T14:20:00Z",
    updatedAt: "2026-10-25T15:10:00Z"
  },
  {
    id: "usr-cst-08",
    fullName: "Đỗ Bích Phương",
    email: "bichphuong.do@hcmut.edu.vn",
    phone: "0982345611",
    passwordHash: "hash_cst_08",
    role: "CUSTOMER",
    avatarUrl: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-08-01T10:00:00Z",
    updatedAt: "2026-10-28T14:40:00Z"
  }
];

// =========================================================================
// 2. DEMO VENDORS (14 Đối tác chuẩn mực phản ánh Master Data và lịch sử)
// Phân bổ trạng thái: 10 Đã duyệt (VERIFIED), 2 Chờ duyệt (PENDING), 2 Từ chối (REJECTED)
// =========================================================================
export const MOCK_ADMIN_VENDORS: (Vendor & {
  email?: string;
  phone?: string;
  avatarUrl?: string;
  activeServicesCount?: number;
  totalRevenue?: number;
  registrationDate?: string;
  safetyCertificateStatus?: string;
})[] = [
  // --- 1. Danabeach Club (Master Vendor 01) ---
  {
    id: "27afde91-58fd-5daa-b9fa-7f93f00135f0",
    userId: "865a115c-d7f9-5ebd-a36b-1834a590177a",
    businessName: "Danabeach Club (Điểm dịch vụ thể thao biển)",
    taxCode: "0401889922",
    address: "51 Võ Nguyên Giáp, Phường Mỹ An, Quận Ngũ Hành Sơn, TP. Đà Nẵng",
    bankName: "VPBank Đà Nẵng",
    bankAccountNumber: "6969696163",
    bankAccountHolder: "DANASEA DEMO VENDOR 01",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-03-05T09:00:00Z",
    ratingAvg: 4.9,
    ratingCount: 184,
    badgeTier: "PLATINUM",
    email: "vender_1@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 3,
    totalRevenue: 284000000,
    registrationDate: "2024-03-01",
    safetyCertificateStatus: "Đã kiểm định (Hạn 12/2026)"
  },
  // --- 2. Kajin Vina (Master Vendor 02) ---
  {
    id: "baea9605-392b-5003-93f1-60f2c0582f65",
    userId: "322173c1-c330-54d9-9d1b-b69c7e480920",
    businessName: "Công ty TNHH Kajin Vina",
    taxCode: "0402177261",
    address: "53 Chính Hữu, P. An Hải, TP. Đà Nẵng",
    bankName: "Techcombank Đà Nẵng",
    bankAccountNumber: "69986999999999",
    bankAccountHolder: "DANASEA DEMO VENDOR 02",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-03-15T14:30:00Z",
    ratingAvg: 4.8,
    ratingCount: 126,
    badgeTier: "GOLD",
    email: "vender_2@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 3,
    totalRevenue: 198500000,
    registrationDate: "2024-03-12",
    safetyCertificateStatus: "Đã kiểm định (Hạn 08/2026)"
  },
  // --- 3. DNOA (Master Vendor 03) ---
  {
    id: "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
    userId: "3ad7d9af-4d4c-59b7-9897-288fcce5be79",
    businessName: "Da Nang Outdoor Adventures (DNOA)",
    taxCode: "0401923456",
    address: "35 An Thượng 5, Phường Mỹ An, Quận Ngũ Hành Sơn, TP. Đà Nẵng",
    bankName: "ACB Đà Nẵng",
    bankAccountNumber: "127273899",
    bankAccountHolder: "DANASEA DEMO VENDOR 03",
    verificationStatus: "VERIFIED",
    verifiedBy: "Trần Minh Quân",
    verifiedAt: "2024-03-25T10:00:00Z",
    ratingAvg: 4.9,
    ratingCount: 215,
    badgeTier: "PLATINUM",
    email: "vender_3@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 6,
    totalRevenue: 342000000,
    registrationDate: "2024-03-20",
    safetyCertificateStatus: "Đã kiểm định (Hạn 10/2026)"
  },
  // --- 4. Loca Travel (Master Vendor 04) ---
  {
    id: "10f8ffc5-e987-592f-9d38-426ad7f97066",
    userId: "44d76fc6-fc8b-5031-87ce-2ee2191bd900",
    businessName: "Loca Travel Đà Nẵng",
    taxCode: "0402034567",
    address: "83 Nguyễn Xuân Khoát, Phường An Hải, TP. Đà Nẵng",
    bankName: "BIDV Hải Châu",
    bankAccountNumber: "621140119996",
    bankAccountHolder: "DANASEA DEMO VENDOR 04",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-04-05T09:00:00Z",
    ratingAvg: 4.7,
    ratingCount: 94,
    badgeTier: "GOLD",
    email: "vender_4@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 2,
    totalRevenue: 156000000,
    registrationDate: "2024-04-01",
    safetyCertificateStatus: "Đã kiểm định (Hạn 11/2026)"
  },
  // --- 5. D BLUE (Master Vendor 05) ---
  {
    id: "128672c5-94e7-5560-896c-158794913b8c",
    userId: "5777bfcf-0c5a-5957-a83f-83a3253668e4",
    businessName: "Công ty Cổ phần D BLUE",
    taxCode: "0401802233",
    address: "179 Nguyễn Sắc Kim, Cẩm Lệ, Đà Nẵng",
    bankName: "BIDV Cẩm Lệ",
    bankAccountNumber: "622440868686",
    bankAccountHolder: "DANASEA DEMO VENDOR 05",
    verificationStatus: "VERIFIED",
    verifiedBy: "Trần Minh Quân",
    verifiedAt: "2024-04-15T11:00:00Z",
    ratingAvg: 4.6,
    ratingCount: 52,
    badgeTier: "SILVER",
    email: "vender_5@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 1,
    totalRevenue: 88000000,
    registrationDate: "2024-04-10",
    safetyCertificateStatus: "Đã kiểm định (Hạn 09/2026)"
  },
  // --- 6. Surf Shack Da Nang (Master Vendor 09) ---
  {
    id: "5a53c8e5-3a76-5383-b104-17b22d4af50f",
    userId: "32797f00-04d0-53fc-b55d-6672984aba55",
    businessName: "Surf Shack Da Nang",
    taxCode: "0402145678",
    address: "33 An Thượng 4, Phường Mỹ An, Quận Ngũ Hành Sơn, TP. Đà Nẵng",
    bankName: "VPBank Ngũ Hành Sơn",
    bankAccountNumber: "797979683863",
    bankAccountHolder: "DANASEA DEMO VENDOR 09",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-05-15T15:00:00Z",
    ratingAvg: 4.8,
    ratingCount: 140,
    badgeTier: "GOLD",
    email: "vender_9@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 4,
    totalRevenue: 215000000,
    registrationDate: "2024-05-10",
    safetyCertificateStatus: "Đã kiểm định (Hạn 07/2026)"
  },
  // --- 7. Go N Chill SUP Club (Master Vendor 08) ---
  {
    id: "c7dcff25-f5dc-5fee-a719-5c23b6a899cb",
    userId: "06223bd1-3fae-525c-afc3-4f42943be6e3",
    businessName: "Go N Chill SUP Club",
    taxCode: "0402256789",
    address: "Đường Hoàng Sa, Phường Mân Thái, Quận Sơn Trà, TP. Đà Nẵng",
    bankName: "Vietcombank Sơn Trà",
    bankAccountNumber: "00700648686",
    bankAccountHolder: "DANASEA DEMO VENDOR 08",
    verificationStatus: "VERIFIED",
    verifiedBy: "Trần Minh Quân",
    verifiedAt: "2024-05-05T08:30:00Z",
    ratingAvg: 4.7,
    ratingCount: 88,
    badgeTier: "SILVER",
    email: "vender_8@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 1,
    totalRevenue: 64000000,
    registrationDate: "2024-05-01",
    safetyCertificateStatus: "Đã kiểm định (Hạn 06/2026)"
  },
  // --- 8. Danasea Water Sports JSC (V38 Seed Vendor) ---
  {
    id: "22222222-2222-2222-2222-222222222221",
    userId: "22222222-2222-2222-2222-222222222222",
    businessName: "Danasea Water Sports JSC",
    taxCode: "0401234567",
    address: "Bán đảo Sơn Trà, TP. Đà Nẵng",
    bankName: "Vietcombank Đà Nẵng",
    bankAccountNumber: "9876543210",
    bankAccountHolder: "DANASEA WATER SPORTS",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-01-05T08:00:00Z",
    ratingAvg: 4.9,
    ratingCount: 128,
    badgeTier: "PLATINUM",
    email: "vendor@danasea.vn",
    phone: "0905000002",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 4,
    totalRevenue: 310000000,
    registrationDate: "2024-01-01",
    safetyCertificateStatus: "Đã kiểm định (Hạn 01/2027)"
  },
  // --- 9. DaNaFi Tour Câu Cá (Master Vendor 07) - PENDING ---
  {
    id: "5fb18fee-16fd-5b9f-bcbd-759b47768d9b",
    userId: "1066fe9e-e4d4-5aa9-9178-84f1d006f75b",
    businessName: "DaNaFi – Tour Câu Cá Đà Nẵng",
    taxCode: "0402367890",
    address: "Vùng biển bán đảo Sơn Trà, Quận Sơn Trà, TP. Đà Nẵng",
    bankName: "Vietcombank Sơn Trà",
    bankAccountNumber: "00700797976",
    bankAccountHolder: "DANASEA DEMO VENDOR 07",
    verificationStatus: "PENDING",
    verifiedBy: "",
    verifiedAt: "",
    ratingAvg: 4.6,
    ratingCount: 38,
    badgeTier: "BRONZE",
    email: "vender_7@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 3,
    totalRevenue: 45000000,
    registrationDate: "2024-10-10",
    safetyCertificateStatus: "Chờ thẩm duyệt hồ sơ đăng kiểm tàu du lịch"
  },
  // --- 10. Khuê Nguyễn Câu Mực Đêm (Master Vendor 06) - REJECTED ---
  {
    id: "2680f19f-8b5a-5aa8-8474-cb13e2ff1c22",
    userId: "4e428199-0ca1-5e4c-98dd-35d20a52692e",
    businessName: "Khuê Nguyễn – Dịch Vụ Câu Mực Đà Nẵng",
    taxCode: "0402478901",
    address: "Cảng cá Thọ Quang, Phường Thọ Quang, Quận Sơn Trà, TP. Đà Nẵng",
    bankName: "ACB Sơn Trà",
    bankAccountNumber: "1974197426",
    bankAccountHolder: "DANASEA DEMO VENDOR 06",
    verificationStatus: "REJECTED",
    verifiedBy: "Trần Minh Quân",
    verifiedAt: "2024-10-22T08:15:00Z",
    ratingAvg: 3.8,
    ratingCount: 16,
    badgeTier: "BRONZE",
    email: "vender_6@danasea.test",
    phone: "0905123456",
    avatarUrl: "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 0,
    totalRevenue: 15000000,
    registrationDate: "2024-10-05",
    safetyCertificateStatus: "Giấy phép phương tiện thủy hết hạn"
  },
  // --- 11-14. Legacy Mock Vendors (Bảo toàn tham chiếu cũ) ---
  {
    id: "vnd-01",
    userId: "usr-vnd-01",
    businessName: "Sơn Trà Marine Diving Co., Ltd",
    taxCode: "0401988231",
    address: "Bến thủy nội địa Bãi Rạng, Bán đảo Sơn Trà, Đà Nẵng",
    bankName: "Vietcombank Đà Nẵng",
    bankAccountNumber: "0041000889922",
    bankAccountHolder: "CTY TNHH DU LICH BIEN SON TRA",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-03-05T09:00:00Z",
    ratingAvg: 4.9,
    ratingCount: 184,
    badgeTier: "PLATINUM",
    email: "tuan.sontramarinedivers@gmail.com",
    phone: "0935123456",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 6,
    totalRevenue: 284000000,
    registrationDate: "2024-03-01",
    safetyCertificateStatus: "Đã kiểm định (Hạn 12/2026)"
  },
  {
    id: "vnd-02",
    userId: "usr-vnd-02",
    businessName: "Công ty Dù Bay Biển Đà Nẵng (My Khe Parasailing)",
    taxCode: "0402115599",
    address: "Khu vực thể thao biển Mỹ Khê - Bãi 3, Võ Nguyên Giáp, Sơn Trà, Đà Nẵng",
    bankName: "BIDV Hải Châu",
    bankAccountNumber: "1281000998877",
    bankAccountHolder: "NGUYEN THU THAO",
    verificationStatus: "VERIFIED",
    verifiedBy: "Vũ Hải Đăng",
    verifiedAt: "2024-03-15T14:30:00Z",
    ratingAvg: 4.8,
    ratingCount: 126,
    badgeTier: "GOLD",
    email: "thao.danangparasailing@outlook.com",
    phone: "0988654321",
    avatarUrl: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 4,
    totalRevenue: 198500000,
    registrationDate: "2024-03-12",
    safetyCertificateStatus: "Đã kiểm định (Hạn 08/2026)"
  },
  {
    id: "vnd-03",
    userId: "usr-vnd-03",
    businessName: "Hợp tác xã Mô tô nước Bán đảo Sơn Trà (Danang Jetski Club)",
    taxCode: "0402998811",
    address: "Bãi Bụt, Phường Thọ Quang, Quận Sơn Trà, Đà Nẵng",
    bankName: "Techcombank Đà Nẵng",
    bankAccountNumber: "19034455667788",
    bankAccountHolder: "HOANG DUC NAM",
    verificationStatus: "PENDING",
    verifiedBy: "",
    verifiedAt: "",
    ratingAvg: 4.5,
    ratingCount: 32,
    badgeTier: "BRONZE",
    email: "nam.jetskidn@gmail.com",
    phone: "0905778899",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 2,
    totalRevenue: 42000000,
    registrationDate: "2024-10-15",
    safetyCertificateStatus: "Chờ thẩm duyệt hồ sơ cứu hộ"
  },
  {
    id: "vnd-04",
    userId: "usr-vnd-04",
    businessName: "CLB Chèo Thuyền Khám Phá Cù Lao Chàm & Sơn Trà",
    taxCode: "0403556677",
    address: "Âu thuyền Thọ Quang, Sơn Trà, Đà Nẵng",
    bankName: "MB Bank Sơn Trà",
    bankAccountNumber: "088899988899",
    bankAccountHolder: "LE HOANG LONG",
    verificationStatus: "REJECTED",
    verifiedBy: "Trần Minh Quân",
    verifiedAt: "2024-10-18T10:00:00Z",
    ratingAvg: 3.8,
    ratingCount: 14,
    badgeTier: "BRONZE",
    email: "long.kayaksontra@gmail.com",
    phone: "0915223344",
    avatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80",
    activeServicesCount: 0,
    totalRevenue: 12000000,
    registrationDate: "2024-10-10",
    safetyCertificateStatus: "Giấy phép phương tiện thủy hết hạn"
  }
];

// =========================================================================
// 3. CATEGORIES & SERVICES (Preserved & Consistent)
// =========================================================================
export const MOCK_ADMIN_CATEGORIES: Category[] = [
  {
    id: "cat-1",
    name: "Chèo SUP & Kayak",
    nameEn: "Stand-Up Paddleboarding & Kayak",
    slug: "cheo-sup-kayak",
    parentId: "",
    iconUrl: "surfing",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cat-2",
    name: "Cano lướt sóng & Dù bay biển",
    nameEn: "Speedboat & Parasailing",
    slug: "cano-du-bay",
    parentId: "",
    iconUrl: "speed",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cat-3",
    name: "Lặn ngắm san hô & Đi bộ dưới biển",
    nameEn: "Scuba Diving & Seawalker",
    slug: "lan-ngam-san-ho",
    parentId: "",
    iconUrl: "scuba_diving",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cat-4",
    name: "Mô tô nước (Jetski)",
    nameEn: "Jetski Rental",
    slug: "mo-to-nuoc-jetski",
    parentId: "",
    iconUrl: "waves",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cat-5",
    name: "Du thuyền ngắm hoàng hôn vịnh",
    nameEn: "Luxury Sunset Yacht Tour",
    slug: "du-thuyen-ngam-hoang-hon",
    parentId: "",
    iconUrl: "sailing",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cat-6",
    name: "Trượt phao chuối cảm giác mạnh",
    nameEn: "Banana Boat Ride",
    slug: "truot-phao-chuoi",
    parentId: "",
    iconUrl: "sports_kabaddi",
    isActive: true,
    requiresSafetyCert: true,
    createdAt: "2024-02-01T00:00:00Z",
    updatedAt: "2024-02-01T00:00:00Z"
  }
];

export const MOCK_ADMIN_SERVICES: (Service & {
  vendorName?: string;
  categoryName?: string;
  thumbnailUrl?: string;
})[] = [
  {
    id: "srv-01",
    vendorId: "vnd-01",
    categoryId: "cat-3",
    name: "Lặn Bình Khí Khám Phá Rạn San Hô Mũi Nghê Sơn Trà",
    nameEn: "Scuba Diving at Mui Nghe Coral Reef - Son Tra",
    slug: "lan-binh-khi-mui-nghe",
    description: "Tour lặn biển tiêu chuẩn PADI, hướng dẫn viên 1 kèm 1, trang bị áo phao tự thổi và bình dưỡng khí y tế.",
    descriptionEn: "PADI standard diving tour with 1-on-1 certified instructor, medical oxygen & auto lifejacket.",
    price: 850000,
    basePrice: 850000,
    durationMinutes: 180,
    capacityPerSlot: 12,
    locationName: "Bãi Rạng - Mũi Nghê, Bán đảo Sơn Trà",
    address: "Hoàng Sa, Thọ Quang, Sơn Trà, Đà Nẵng",
    latitude: 16.1132,
    longitude: 108.2815,
    status: "ACTIVE",
    rejectionReason: "",
    waiverContent: "Du khách cam kết không có tiền sử bệnh tim mạch huyết áp, tuân thủ hướng dẫn viên hải đồ.",
    weatherSensitive: true,
    minWindKmh: 5,
    maxWaveM: 1.2,
    avgRating: 4.9,
    ratingCount: 88,
    viewCount: 1420,
    vendorName: "Sơn Trà Marine Diving Co., Ltd",
    categoryName: "Lặn ngắm san hô & Đi bộ dưới biển",
    thumbnailUrl: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80",
    createdAt: "2024-03-10T08:00:00Z",
    updatedAt: "2026-10-20T10:00:00Z"
  },
  {
    id: "srv-02",
    vendorId: "vnd-02",
    categoryId: "cat-2",
    name: "Bay Dù Lượn Trên Không Trung Biển Mỹ Khê Ngắm Toàn Cảnh Thành Phố",
    nameEn: "Tandem Parasailing over My Khe Beach",
    slug: "bay-du-luon-my-khe",
    description: "Trải nghiệm bay dù đôi kéo bằng cano tốc độ cao công suất 250HP, ngắm trọn vẹn bờ biển Đà Nẵng từ độ cao 80m.",
    descriptionEn: "Fly over the turquoise My Khe water at 80m altitude behind high speed speedboat.",
    price: 650000,
    basePrice: 650000,
    durationMinutes: 20,
    capacityPerSlot: 6,
    locationName: "Bãi tắm Mỹ Khê số 3",
    address: "Võ Nguyên Giáp, Phước Mỹ, Sơn Trà, Đà Nẵng",
    latitude: 16.0601,
    longitude: 108.2467,
    status: "ACTIVE",
    rejectionReason: "",
    waiverContent: "Mặc áo phao bắt buộc trong suốt hành trình; ngừng dịch vụ khi sức gió giật cấp 4 trở lên.",
    weatherSensitive: true,
    minWindKmh: 8,
    maxWaveM: 0.9,
    avgRating: 4.8,
    ratingCount: 114,
    viewCount: 2310,
    vendorName: "Công ty Dù Bay Biển Đà Nẵng",
    categoryName: "Cano lướt sóng & Dù bay biển",
    thumbnailUrl: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&auto=format&fit=crop&q=80",
    createdAt: "2024-03-20T09:00:00Z",
    updatedAt: "2026-10-22T11:30:00Z"
  },
  {
    id: "srv-03",
    vendorId: "vnd-03",
    categoryId: "cat-4",
    name: "Thuê Mô Tô Nước Sea-Doo GTX 300 Chinh Phục Vịnh Đà Nẵng",
    nameEn: "Jetski Sea-Doo GTX 300 Rental Da Nang Bay",
    slug: "thue-mo-to-nuoc-sea-doo",
    description: "Đội mô tô nước đời mới 2024 có gắn định vị GPS giám sát hải trình tự động, phao cứu sinh đạt chuẩn Cảng vụ.",
    descriptionEn: "High-performance Jetski equipped with GPS tracker and safety port transponder.",
    price: 700000,
    basePrice: 700000,
    durationMinutes: 30,
    capacityPerSlot: 4,
    locationName: "Bãi Bụt, Sơn Trà",
    address: "Bãi Bụt, Hoàng Sa, Thọ Quang, Sơn Trà, Đà Nẵng",
    latitude: 16.1042,
    longitude: 108.2612,
    status: "PENDING_APPROVAL",
    rejectionReason: "",
    waiverContent: "Ký cam kết giữ khoảng cách 100m với khu vực bơi lội của du khách.",
    weatherSensitive: true,
    minWindKmh: 5,
    maxWaveM: 1.0,
    avgRating: 4.5,
    ratingCount: 18,
    viewCount: 650,
    vendorName: "CLB Mô tô nước Bán đảo Sơn Trà",
    categoryName: "Mô tô nước (Jetski)",
    thumbnailUrl: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80",
    createdAt: "2024-10-24T14:00:00Z",
    updatedAt: "2024-10-24T14:00:00Z"
  },
  {
    id: "srv-04",
    vendorId: "vnd-01",
    categoryId: "cat-5",
    name: "Hoàng Hôn Du Thuyền Catamaran Thưởng Rượu Vang Sơn Trà",
    nameEn: "Catamaran Sunset Cruise with Wine Tasting",
    slug: "du-thuyen-catamaran-hoang-hon",
    description: "Du ngoạn 3 tiếng quanh chân núi Sơn Trà, tiệc canapé và cocktail ngắm hoàng hôn buông xuống cầu Thuận Phước.",
    descriptionEn: "3-hour cruise along Son Tra cliffs with wine and canape at sunset.",
    price: 1250000,
    basePrice: 1250000,
    durationMinutes: 180,
    capacityPerSlot: 20,
    locationName: "Bến du thuyền DHC Marina",
    address: "Trần Hưng Đạo, An Hải Tây, Sơn Trà, Đà Nẵng",
    latitude: 16.0645,
    longitude: 108.2321,
    status: "PENDING_APPROVAL",
    rejectionReason: "",
    waiverContent: "Hành khách phải xuất trình CCCD hoặc hộ chiếu trước khi lên tàu.",
    weatherSensitive: true,
    minWindKmh: 4,
    maxWaveM: 0.8,
    avgRating: 0,
    ratingCount: 0,
    viewCount: 310,
    vendorName: "Sơn Trà Marine Diving Co., Ltd",
    categoryName: "Du thuyền ngắm hoàng hôn vịnh",
    thumbnailUrl: "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=500&auto=format&fit=crop&q=80",
    createdAt: "2024-10-25T16:00:00Z",
    updatedAt: "2024-10-25T16:00:00Z"
  }
];

// =========================================================================
// 4. DEMO PROMOTIONS (Chính xác 4 chương trình theo yêu cầu Task D)
// - 5%: 769 / 1.000 lượt (Đang áp dụng, còn 231 lượt)
// - 10%: 100 / 100 lượt (Hết lượt, còn 0 lượt)
// - 20.000đ: 231 / 500 lượt (Đang áp dụng, còn 269 lượt)
// - 100.000đ: 2 / 2 lượt (Hết lượt, còn 0 lượt)
// =========================================================================
export const MOCK_ADMIN_PROMOTIONS: DiscountCode[] = [
  {
    id: "promo-01",
    code: "DANASEA5",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "PERCENTAGE",
    discountValue: 5,
    maxUses: 1000,
    usedCount: 769,
    validFrom: "2026-06-01T00:00:00Z",
    validTo: "2026-12-31T23:59:59Z",
    isActive: true,
    createdAt: "2026-05-25T10:00:00Z",
    updatedAt: "2026-10-28T08:00:00Z"
  },
  {
    id: "promo-02",
    code: "DANASUMMER10",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "PERCENTAGE",
    discountValue: 10,
    maxUses: 100,
    usedCount: 100,
    validFrom: "2026-06-01T00:00:00Z",
    validTo: "2026-12-31T23:59:59Z",
    isActive: true,
    createdAt: "2026-05-28T09:00:00Z",
    updatedAt: "2026-10-25T14:00:00Z"
  },
  {
    id: "promo-03",
    code: "BIENXANH20K",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "FIXED",
    discountValue: 20000,
    maxUses: 500,
    usedCount: 231,
    validFrom: "2026-08-01T00:00:00Z",
    validTo: "2026-12-31T23:59:59Z",
    isActive: true,
    createdAt: "2026-07-28T10:00:00Z",
    updatedAt: "2026-10-28T11:00:00Z"
  },
  {
    id: "promo-04",
    code: "DANAVIP100K",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "FIXED",
    discountValue: 100000,
    maxUses: 2,
    usedCount: 2,
    validFrom: "2026-09-01T00:00:00Z",
    validTo: "2026-12-31T23:59:59Z",
    isActive: true,
    createdAt: "2026-08-30T08:00:00Z",
    updatedAt: "2026-10-24T16:00:00Z"
  }
];

// =========================================================================
// 5. DEMO MASTER ORDERS & SUBORDERS (16 Master Orders, 20 SubOrders thực tế)
// Phân bổ trạng thái: 8 COMPLETED, 3 CONFIRMED, 2 PENDING, 1 CANCELLED, 2 REFUNDED
// =========================================================================
export interface DemoMasterOrderExtended extends MasterOrder {
  customerName?: string;
  paymentStatus?: 'PAID' | 'UNPAID' | 'REFUNDED' | 'FAILED' | 'PENDING';
  paymentProvider?: string;
  paymentTransactionId?: string;
  paidAt?: string;
}

export const MOCK_ADMIN_MASTER_ORDERS: DemoMasterOrderExtended[] = MOCK_ADMIN_MASTER_ORDERS_128 as DemoMasterOrderExtended[];
export const MOCK_ADMIN_SUB_ORDERS: SubOrder[] = MOCK_ADMIN_SUB_ORDERS_128;
export const MOCK_ADMIN_PAYMENTS: Payment[] = MOCK_ADMIN_PAYMENTS_128;
export const MOCK_ADMIN_REFUNDS: Refund[] = MOCK_ADMIN_REFUNDS_128;

// =========================================================================
// 7. DEMO SETTLEMENTS & PAYOUTS (Khớp đối soát tài chính theo kỳ thực tế)
// =========================================================================
export const MOCK_ADMIN_SETTLEMENTS: Settlement[] = [
  {
    id: "SETTLE-OCT-01",
    vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0", // Danabeach Club
    periodStart: "2026-10-01T00:00:00Z",
    periodEnd: "2026-10-15T23:59:59Z",
    grossAmount: 2300000,
    commissionAmount: 230000,
    netPayableAmount: 2070000,
    status: "COMPLETED",
    generatedAt: "2026-10-16T02:00:00Z",
    createdAt: "2026-10-16T02:00:00Z",
    updatedAt: "2026-10-18T10:00:00Z"
  },
  {
    id: "SETTLE-OCT-02",
    vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0", // DNOA
    periodStart: "2026-10-01T00:00:00Z",
    periodEnd: "2026-10-15T23:59:59Z",
    grossAmount: 3500000,
    commissionAmount: 350000,
    netPayableAmount: 3150000,
    status: "PENDING",
    generatedAt: "2026-10-16T02:00:00Z",
    createdAt: "2026-10-16T02:00:00Z",
    updatedAt: "2026-10-16T02:00:00Z"
  },
  {
    id: "SETTLE-OCT-03",
    vendorId: "128672c5-94e7-5560-896c-158794913b8c", // D BLUE (SUB-1013-01: 8.000.000đ)
    periodStart: "2026-10-16T00:00:00Z",
    periodEnd: "2026-10-31T23:59:59Z",
    grossAmount: 8000000,
    commissionAmount: 800000,
    netPayableAmount: 7200000,
    status: "PENDING",
    generatedAt: "2026-10-22T02:00:00Z",
    createdAt: "2026-10-22T02:00:00Z",
    updatedAt: "2026-10-22T02:00:00Z"
  },
  {
    id: "SETTLE-SEP-02",
    vendorId: "5a53c8e5-3a76-5383-b104-17b22d4af50f", // Surf Shack Da Nang
    periodStart: "2026-09-16T00:00:00Z",
    periodEnd: "2026-09-30T23:59:59Z",
    grossAmount: 4800000,
    commissionAmount: 480000,
    netPayableAmount: 4320000,
    status: "COMPLETED",
    generatedAt: "2026-10-01T02:00:00Z",
    createdAt: "2026-10-01T02:00:00Z",
    updatedAt: "2026-10-02T14:20:00Z"
  }
];

export const MOCK_ADMIN_PAYOUTS: (PayoutRequest & {
  vendorName?: string;
  bankName?: string;
  bankAccountNumber?: string;
  bankAccountHolder?: string;
  periodText?: string;
  totalOrdersCount?: number;
})[] = [
  {
    id: "PAY-2026-10-001",
    vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0",
    settlementId: "SETTLE-OCT-01",
    amount: 2070000,
    status: "PAID",
    processedBy: "Vũ Hải Đăng",
    processedAt: "2026-10-18T10:00:00Z",
    vendorName: "Danabeach Club (Điểm dịch vụ thể thao biển)",
    bankName: "VPBank Đà Nẵng",
    bankAccountNumber: "6969696163",
    bankAccountHolder: "DANASEA DEMO VENDOR 01",
    periodText: "Kỳ đối soát 01/10 - 15/10/2026",
    totalOrdersCount: 3,
    createdAt: "2026-10-16T08:30:00Z",
    updatedAt: "2026-10-18T10:00:00Z"
  },
  {
    id: "PAY-2026-10-002",
    vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
    settlementId: "SETTLE-OCT-02",
    amount: 3150000,
    status: "REQUESTED",
    processedBy: "",
    processedAt: "",
    vendorName: "Da Nang Outdoor Adventures (DNOA)",
    bankName: "ACB Đà Nẵng",
    bankAccountNumber: "127273899",
    bankAccountHolder: "DANASEA DEMO VENDOR 03",
    periodText: "Kỳ đối soát 01/10 - 15/10/2026",
    totalOrdersCount: 4,
    createdAt: "2026-10-16T10:00:00Z",
    updatedAt: "2026-10-16T10:00:00Z"
  },
  {
    id: "PAY-2026-10-003",
    vendorId: "128672c5-94e7-5560-896c-158794913b8c",
    settlementId: "SETTLE-OCT-03",
    amount: 7200000,
    status: "REQUESTED",
    processedBy: "",
    processedAt: "",
    vendorName: "Công ty Cổ phần D BLUE",
    bankName: "BIDV Cẩm Lệ",
    bankAccountNumber: "622440868686",
    bankAccountHolder: "DANASEA DEMO VENDOR 05",
    periodText: "Kỳ đối soát 16/10 - 31/10/2026",
    totalOrdersCount: 1,
    createdAt: "2026-10-22T08:30:00Z",
    updatedAt: "2026-10-22T08:30:00Z"
  },
  {
    id: "PAY-2026-09-089",
    vendorId: "5a53c8e5-3a76-5383-b104-17b22d4af50f",
    settlementId: "SETTLE-SEP-02",
    amount: 4320000,
    status: "PAID",
    processedBy: "Trần Minh Quân",
    processedAt: "2026-10-02T14:20:00Z",
    vendorName: "Surf Shack Da Nang",
    bankName: "VPBank Ngũ Hành Sơn",
    bankAccountNumber: "797979683863",
    bankAccountHolder: "DANASEA DEMO VENDOR 09",
    periodText: "Kỳ đối soát 16/09 - 30/09/2026",
    totalOrdersCount: 6,
    createdAt: "2026-10-01T09:00:00Z",
    updatedAt: "2026-10-02T14:20:00Z"
  }
];

// =========================================================================
// 8. DEMO DISPUTES (Khớp với đơn con và khách hàng thực tế)
// =========================================================================
export const MOCK_ADMIN_DISPUTES: (Dispute & {
  customerName?: string;
  customerPhone?: string;
  vendorName?: string;
  serviceName?: string;
  orderAmount?: number;
  evidenceImages?: string[];
})[] = [
  {
    id: "DSP-01",
    subOrderId: "SUB-1001-01",
    raisedBy: "46c8f2ea-2be5-5365-be80-31fd57d9e260",
    category: "SERVICE_QUALITY",
    description: "Khách hàng phản ánh nhân viên trạm mô tô nước hỗ trợ trễ 20 phút so với giờ đã đặt do chuẩn bị phao tiêu chậm.",
    status: "OPEN",
    resolutionNote: "",
    resolvedBy: "",
    resolvedAt: "",
    customerName: "Nguyễn Văn Minh",
    customerPhone: "0905123890",
    vendorName: "Danabeach Club (Điểm dịch vụ thể thao biển)",
    serviceName: "Mô tô nước – Danabeach",
    orderAmount: 650000,
    evidenceImages: [
      "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80"
    ],
    createdAt: "2026-10-28T10:15:00Z",
    updatedAt: "2026-10-28T10:15:00Z"
  },
  {
    id: "DSP-02",
    subOrderId: "SUB-1004-01",
    raisedBy: "usr-cst-02",
    category: "WEATHER_CANCELLATION",
    description: "Thời tiết sóng to gió lớn Cảng vụ cấm biển xuất phát lúc 14h, khách đề nghị hoàn tiền trực tiếp thay vì dời lịch sang tuần kế tiếp.",
    status: "RESOLVED",
    resolutionNote: "Đã xác minh lệnh cấm biển từ Trung tâm Giám sát lúc 14:05 ngày 26/10 và hoàn 100% tiền qua VNPay.",
    resolvedBy: "Vũ Hải Đăng",
    resolvedAt: "2026-10-26T15:30:00Z",
    customerName: "Trần Thị Mai Lan",
    customerPhone: "0977112244",
    vendorName: "Công ty TNHH Kajin Vina",
    serviceName: "Dù bay trên biển – Kajin Vina",
    orderAmount: 1300000,
    evidenceImages: [
      "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&auto=format&fit=crop&q=80"
    ],
    createdAt: "2026-10-26T14:30:00Z",
    updatedAt: "2026-10-26T15:30:00Z"
  },
  {
    id: "DSP-03",
    subOrderId: "SUB-1008-01",
    raisedBy: "usr-cst-03",
    category: "OVERCHARGING",
    description: "Khách phản ánh hệ thống ghi nhận trừ tiền tài khoản ngân hàng nhưng trễ phiên xác nhận khiến đơn chuyển sang CANCELLED.",
    status: "RESOLVED",
    resolutionNote: "Đã đối soát với cổng VNPay ghi nhận giao dịch timeout, ngân hàng phát hành tự động hoàn tiền trong 24h.",
    resolvedBy: "Trần Minh Quân",
    resolvedAt: "2026-10-24T17:00:00Z",
    customerName: "Phạm Quốc Hùng",
    customerPhone: "0934998877",
    vendorName: "Công ty TNHH Kajin Vina",
    serviceName: "Mô tô nước – Kajin Vina",
    orderAmount: 650000,
    evidenceImages: [],
    createdAt: "2026-10-24T16:20:00Z",
    updatedAt: "2026-10-24T17:00:00Z"
  }
];

// =========================================================================
// 9. DEMO SYSTEM CONFIGS
// =========================================================================
export const MOCK_ADMIN_CONFIGS: SystemConfig[] = [
  {
    id: "cfg-01",
    key: "PLATFORM_COMMISSION_RATE",
    value: "10",
    description: "Tỷ lệ hoa hồng chiết khấu sàn mặc định (%) áp dụng trên giá trị hợp đồng dịch vụ",
    updatedBy: "Vũ Hải Đăng",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2026-10-15T10:00:00Z"
  },
  {
    id: "cfg-02",
    key: "AUTO_PAYOUT_CYCLE_DAYS",
    value: "15",
    description: "Chu kỳ đối soát và cho phép đối tác tạo lệnh rút tiền tự động (ngày)",
    updatedBy: "Vũ Hải Đăng",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  },
  {
    id: "cfg-03",
    key: "WEATHER_MAX_SAFE_WAVE_M",
    value: "1.5",
    description: "Ngưỡng độ cao sóng biển tối đa (mét) cho phép hoạt động cano & dù lượn",
    updatedBy: "Trần Minh Quân",
    createdAt: "2024-02-10T08:00:00Z",
    updatedAt: "2026-10-01T09:30:00Z"
  },
  {
    id: "cfg-04",
    key: "WEATHER_MAX_SAFE_WIND_KMH",
    value: "25",
    description: "Ngưỡng vận tốc gió giật (km/h) kích hoạt cảnh báo Cờ Vàng / Cờ Đỏ",
    updatedBy: "Trần Minh Quân",
    createdAt: "2024-02-10T08:00:00Z",
    updatedAt: "2026-10-01T09:30:00Z"
  },
  {
    id: "cfg-05",
    key: "EMERGENCY_PORT_HOTLINE",
    value: "0236 3920 477",
    description: "Số điện thoại liên hệ BQL bán đảo Sơn Trà và các bãi biển du lịch Đà Nẵng",
    updatedBy: "Vũ Hải Đăng",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z"
  }
];

// =========================================================================
// 10. DEMO AUDIT LOGS (24 bản ghi nghiệp vụ đầy đủ, liên kết chặt chẽ)
// =========================================================================
export const MOCK_ADMIN_AUDIT_LOGS: (AuditLog & {
  actorName?: string;
  actorEmail?: string;
})[] = [
  {
    id: "LOG-99224",
    actorUserId: "usr-adm-01",
    action: "UPDATE_PROMOTION",
    entityType: "DiscountCode",
    entityId: "promo-03",
    metadata: JSON.stringify({ code: "BIENXANH20K", oldUsedCount: 230, newUsedCount: 231, orderId: "ORD-2026-1005" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-28T11:22:00Z",
    updatedAt: "2026-10-28T11:22:00Z"
  },
  {
    id: "LOG-99223",
    actorUserId: "usr-adm-01",
    action: "PROCESS_REFUND",
    entityType: "Refund",
    entityId: "REF-2026-001",
    metadata: JSON.stringify({ amount: 1300000, masterOrderId: "ORD-2026-1004", reason: "WEATHER_DISRUPTION", gateway: "VNPAY" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-26T15:30:00Z",
    updatedAt: "2026-10-26T15:30:00Z"
  },
  {
    id: "LOG-99222",
    actorUserId: "usr-adm-02",
    action: "RESOLVE_DISPUTE",
    entityType: "Dispute",
    entityId: "DSP-02",
    metadata: JSON.stringify({ disputeId: "DSP-02", resolution: "FULL_REFUND_WEATHER", refundId: "REF-2026-001" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-26T15:28:00Z",
    updatedAt: "2026-10-26T15:28:00Z"
  },
  {
    id: "LOG-99221",
    actorUserId: "usr-adm-02",
    action: "RESOLVE_DISPUTE",
    entityType: "Dispute",
    entityId: "DSP-03",
    metadata: JSON.stringify({ disputeId: "DSP-03", resolution: "CONFIRM_TIMEOUT_REFUND", bankRef: "VNP14881900" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-24T17:00:00Z",
    updatedAt: "2026-10-24T17:00:00Z"
  },
  {
    id: "LOG-99220",
    actorUserId: "usr-adm-01",
    action: "GENERATE_SETTLEMENT",
    entityType: "Settlement",
    entityId: "SETTLE-OCT-03",
    metadata: JSON.stringify({ vendorId: "128672c5-94e7-5560-896c-158794913b8c", grossAmount: 8000000, commission: 800000, netPayable: 7200000 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-22T08:30:00Z",
    updatedAt: "2026-10-22T08:30:00Z"
  },
  {
    id: "LOG-99219",
    actorUserId: "usr-adm-02",
    action: "REJECT_VENDOR",
    entityType: "Vendor",
    entityId: "2680f19f-8b5a-5aa8-8474-cb13e2ff1c22",
    metadata: JSON.stringify({ vendorName: "Khuê Nguyễn Câu Mực Đêm", reason: "Giấy phép phương tiện thủy hết hạn" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-22T08:15:00Z",
    updatedAt: "2026-10-22T08:15:00Z"
  },
  {
    id: "LOG-99218",
    actorUserId: "usr-adm-02",
    action: "LOCK_USER_ACCOUNT",
    entityType: "User",
    entityId: "4e428199-0ca1-5e4c-98dd-35d20a52692e",
    metadata: JSON.stringify({ reason: "Tạm dừng tài khoản đối tác do hồ sơ an toàn thủy nội địa bị từ chối" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-22T08:15:00Z",
    updatedAt: "2026-10-22T08:15:00Z"
  },
  {
    id: "LOG-99217",
    actorUserId: "usr-adm-01",
    action: "PROCESS_REFUND",
    entityType: "Refund",
    entityId: "REF-2026-002",
    metadata: JSON.stringify({ amount: 300000, masterOrderId: "ORD-2026-1014", reason: "CUSTOMER_CANCEL", gateway: "VNPAY" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-20T12:00:00Z",
    updatedAt: "2026-10-20T12:00:00Z"
  },
  {
    id: "LOG-99216",
    actorUserId: "usr-adm-01",
    action: "APPROVE_PAYOUT",
    entityType: "PayoutRequest",
    entityId: "PAY-2026-10-001",
    metadata: JSON.stringify({ amount: 2070000, bankRef: "VPB-6969696163", vendor: "Danabeach Club" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-18T10:00:00Z",
    updatedAt: "2026-10-18T10:00:00Z"
  },
  {
    id: "LOG-99215",
    actorUserId: "usr-adm-01",
    action: "UPDATE_SYSTEM_CONFIG",
    entityType: "SystemConfig",
    entityId: "cfg-01",
    metadata: JSON.stringify({ oldVal: "12%", newVal: "10%", reason: "Chương trình kích cầu du lịch biển mùa thu" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-15T10:00:00Z",
    updatedAt: "2026-10-15T10:00:00Z"
  },
  {
    id: "LOG-99214",
    actorUserId: "usr-adm-02",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "5a53c8e5-3a76-5383-b104-17b22d4af50f",
    metadata: JSON.stringify({ vendorName: "Surf Shack Da Nang", badgeTier: "GOLD", verifiedDocs: 2 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-10T14:00:00Z",
    updatedAt: "2026-10-10T14:00:00Z"
  },
  {
    id: "LOG-99213",
    actorUserId: "usr-adm-01",
    action: "CREATE_PROMOTION",
    entityType: "DiscountCode",
    entityId: "promo-04",
    metadata: JSON.stringify({ code: "DANAVIP100K", discountType: "FIXED", value: 100000, maxUses: 2 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-08-30T08:00:00Z",
    updatedAt: "2026-08-30T08:00:00Z"
  },
  {
    id: "LOG-99212",
    actorUserId: "usr-adm-01",
    action: "CREATE_PROMOTION",
    entityType: "DiscountCode",
    entityId: "promo-03",
    metadata: JSON.stringify({ code: "BIENXANH20K", discountType: "FIXED", value: 20000, maxUses: 500 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-07-28T10:00:00Z",
    updatedAt: "2026-07-28T10:00:00Z"
  },
  {
    id: "LOG-99211",
    actorUserId: "usr-adm-02",
    action: "CREATE_PROMOTION",
    entityType: "DiscountCode",
    entityId: "promo-01",
    metadata: JSON.stringify({ code: "DANASEA5", discountType: "PERCENTAGE", value: 5, maxUses: 1000 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-05-25T10:00:00Z",
    updatedAt: "2026-05-25T10:00:00Z"
  },
  {
    id: "LOG-99210",
    actorUserId: "usr-adm-02",
    action: "CREATE_PROMOTION",
    entityType: "DiscountCode",
    entityId: "promo-02",
    metadata: JSON.stringify({ code: "DANASUMMER10", discountType: "PERCENTAGE", value: 10, maxUses: 100 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-05-28T09:00:00Z",
    updatedAt: "2026-05-28T09:00:00Z"
  },
  {
    id: "LOG-99209",
    actorUserId: "usr-adm-01",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "27afde91-58fd-5daa-b9fa-7f93f00135f0",
    metadata: JSON.stringify({ vendorName: "Danabeach Club", badgeTier: "PLATINUM", verifiedDocs: 2 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-03-05T09:00:00Z",
    updatedAt: "2024-03-05T09:00:00Z"
  },
  {
    id: "LOG-99208",
    actorUserId: "usr-adm-01",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "baea9605-392b-5003-93f1-60f2c0582f65",
    metadata: JSON.stringify({ vendorName: "Công ty TNHH Kajin Vina", badgeTier: "GOLD", verifiedDocs: 2 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-03-15T14:30:00Z",
    updatedAt: "2024-03-15T14:30:00Z"
  },
  {
    id: "LOG-99207",
    actorUserId: "usr-adm-02",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
    metadata: JSON.stringify({ vendorName: "Da Nang Outdoor Adventures", badgeTier: "PLATINUM", verifiedDocs: 2 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2024-03-25T10:00:00Z",
    updatedAt: "2024-03-25T10:00:00Z"
  },
  {
    id: "LOG-99206",
    actorUserId: "usr-adm-01",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "10f8ffc5-e987-592f-9d38-426ad7f97066",
    metadata: JSON.stringify({ vendorName: "Loca Travel Đà Nẵng", badgeTier: "GOLD", verifiedDocs: 2 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-04-05T09:00:00Z",
    updatedAt: "2024-04-05T09:00:00Z"
  },
  {
    id: "LOG-99205",
    actorUserId: "usr-adm-02",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "128672c5-94e7-5560-896c-158794913b8c",
    metadata: JSON.stringify({ vendorName: "Công ty Cổ phần D BLUE", badgeTier: "SILVER", verifiedDocs: 2 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2024-04-15T11:00:00Z",
    updatedAt: "2024-04-15T11:00:00Z"
  },
  {
    id: "LOG-99204",
    actorUserId: "usr-adm-02",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "c7dcff25-f5dc-5fee-a719-5c23b6a899cb",
    metadata: JSON.stringify({ vendorName: "Go N Chill SUP Club", badgeTier: "SILVER", verifiedDocs: 2 }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2024-05-05T08:30:00Z",
    updatedAt: "2024-05-05T08:30:00Z"
  },
  {
    id: "LOG-99203",
    actorUserId: "usr-adm-01",
    action: "APPROVE_PAYOUT",
    entityType: "PayoutRequest",
    entityId: "PAY-2026-09-089",
    metadata: JSON.stringify({ amount: 4320000, bankRef: "VPB-797979683863", vendor: "Surf Shack Da Nang" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-02T14:20:00Z",
    updatedAt: "2026-10-02T14:20:00Z"
  },
  {
    id: "LOG-99202",
    actorUserId: "usr-adm-01",
    action: "ADMIN_LOGIN",
    entityType: "User",
    entityId: "usr-adm-01",
    metadata: JSON.stringify({ ipAddress: "14.241.120.45", userAgent: "Chrome 130.0 Win64" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2026-10-28T07:30:00Z",
    updatedAt: "2026-10-28T07:30:00Z"
  },
  {
    id: "LOG-99201",
    actorUserId: "usr-adm-02",
    action: "ADMIN_LOGIN",
    entityType: "User",
    entityId: "usr-adm-02",
    metadata: JSON.stringify({ ipAddress: "118.69.182.10", userAgent: "Firefox 131.0 Win64" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2026-10-28T08:00:00Z",
    updatedAt: "2026-10-28T08:00:00Z"
  }
];
