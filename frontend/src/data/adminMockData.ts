import type {
  User,
  Vendor,
  Service,
  Category,
  Dispute,
  PayoutRequest,
  DiscountCode,
  SystemConfig,
  AuditLog
} from '../types';

export const MOCK_ADMIN_USERS: User[] = [
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
    updatedAt: "2024-10-20T14:30:00Z"
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
    updatedAt: "2024-10-18T11:00:00Z"
  },
  {
    id: "usr-vnd-01",
    fullName: "Lê Cảnh Tuấn",
    email: "tuan.sontramarinedivers@gmail.com",
    phone: "0935123456",
    passwordHash: "hash_vnd_01",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-01T07:30:00Z",
    updatedAt: "2024-10-25T16:00:00Z"
  },
  {
    id: "usr-vnd-02",
    fullName: "Nguyễn Thu Thảo",
    email: "thao.danangparasailing@outlook.com",
    phone: "0988654321",
    passwordHash: "hash_vnd_02",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: false,
    locale: "vi",
    createdAt: "2024-03-12T10:15:00Z",
    updatedAt: "2024-10-24T09:20:00Z"
  },
  {
    id: "usr-vnd-03",
    fullName: "Hoàng Đức Nam",
    email: "nam.jetskidn@gmail.com",
    phone: "0905778899",
    passwordHash: "hash_vnd_03",
    role: "VENDOR",
    avatarUrl: "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=150&auto=format&fit=crop&q=80",
    isEmailVerified: true,
    isLocked: true,
    locale: "vi",
    createdAt: "2024-04-05T14:00:00Z",
    updatedAt: "2024-10-22T08:15:00Z"
  },
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
    updatedAt: "2024-10-26T15:00:00Z"
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
    updatedAt: "2024-10-27T08:45:00Z"
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
    isLocked: true,
    locale: "vi",
    createdAt: "2024-06-02T13:40:00Z",
    updatedAt: "2024-10-25T17:10:00Z"
  }
];

export const MOCK_ADMIN_VENDORS: (Vendor & {
  email?: string;
  phone?: string;
  avatarUrl?: string;
  activeServicesCount?: number;
  totalRevenue?: number;
  registrationDate?: string;
  safetyCertificateStatus?: string;
})[] = [
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
    safetyCertificateStatus: "Đã kiểm định (Hạn 12/2025)"
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
    safetyCertificateStatus: "Đã kiểm định (Hạn 08/2025)"
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
    isActive: false,
    requiresSafetyCert: false,
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
    updatedAt: "2024-10-20T10:00:00Z"
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
    updatedAt: "2024-10-22T11:30:00Z"
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
    subOrderId: "SUB-89412-01",
    raisedBy: "usr-cst-01",
    category: "SERVICE_QUALITY",
    description: "Thái độ hướng dẫn viên không đúng chuẩn mực an toàn, thiết bị lặn có mùi mốc và nước tràn vào mặt nạ khiến gia đình hoảng loạn phải hủy ngang sau 15 phút.",
    status: "OPEN",
    resolutionNote: "",
    resolvedBy: "",
    resolvedAt: "",
    customerName: "Nguyễn Văn An",
    customerPhone: "0912345678",
    vendorName: "Sơn Trà Marine Diving Co., Ltd",
    serviceName: "Lặn Bình Khí Khám Phá Rạn San Hô Mũi Nghê Sơn Trà",
    orderAmount: 1700000,
    evidenceImages: [
      "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80"
    ],
    createdAt: "2024-10-26T10:15:00Z",
    updatedAt: "2024-10-26T10:15:00Z"
  },
  {
    id: "DSP-02",
    subOrderId: "SUB-89304-02",
    raisedBy: "usr-cst-02",
    category: "WEATHER_CANCELLATION",
    description: "Thời tiết sóng to gió lớn Cảng vụ cấm biển từ 14h, nhưng nhà cung cấp không hoàn tiền qua ví mà yêu cầu đổi ngày sang tuần sau trong khi khách phải bay về Hà Nội.",
    status: "IN_PROGRESS",
    resolutionNote: "Đang chờ đối soát dữ liệu lệnh cấm biển từ Trung tâm Giám sát lúc 14:05 ngày 25/10.",
    resolvedBy: "Vũ Hải Đăng",
    resolvedAt: "",
    customerName: "Trần Thị Mai Lan",
    customerPhone: "0977112244",
    vendorName: "Công ty Dù Bay Biển Đà Nẵng",
    serviceName: "Bay Dù Lượn Trên Không Trung Biển Mỹ Khê",
    orderAmount: 1300000,
    evidenceImages: [
      "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&auto=format&fit=crop&q=80"
    ],
    createdAt: "2024-10-25T16:30:00Z",
    updatedAt: "2024-10-26T09:00:00Z"
  },
  {
    id: "DSP-03",
    subOrderId: "SUB-88910-01",
    raisedBy: "usr-cst-03",
    category: "OVERCHARGING",
    description: "Bị phụ thu thêm 200.000đ tiền chụp ảnh flycam tại bến mà không thông báo từ đầu trên hệ thống.",
    status: "RESOLVED",
    resolutionNote: "Đã phạt cảnh cáo nhà cung cấp và hoàn 200.000đ từ quỹ bảo chứng về tài khoản khách hàng.",
    resolvedBy: "Trần Minh Quân",
    resolvedAt: "2024-10-24T15:00:00Z",
    customerName: "Phạm Quốc Hùng",
    customerPhone: "0934998877",
    vendorName: "CLB Mô tô nước Bán đảo Sơn Trà",
    serviceName: "Thuê Mô Tô Nước Sea-Doo GTX 300",
    orderAmount: 700000,
    evidenceImages: [],
    createdAt: "2024-10-23T11:00:00Z",
    updatedAt: "2024-10-24T15:00:00Z"
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
    id: "PAY-2024-10-001",
    vendorId: "vnd-01",
    settlementId: "SETTLE-OCT-01",
    amount: 54600000,
    status: "REQUESTED",
    processedBy: "",
    processedAt: "",
    vendorName: "Sơn Trà Marine Diving Co., Ltd",
    bankName: "Vietcombank Đà Nẵng",
    bankAccountNumber: "0041000889922",
    bankAccountHolder: "CTY TNHH DU LICH BIEN SON TRA",
    periodText: "Kỳ đối soát 01/10 - 15/10/2024",
    totalOrdersCount: 48,
    createdAt: "2024-10-16T08:30:00Z",
    updatedAt: "2024-10-16T08:30:00Z"
  },
  {
    id: "PAY-2024-10-002",
    vendorId: "vnd-02",
    settlementId: "SETTLE-OCT-02",
    amount: 38200000,
    status: "REQUESTED",
    processedBy: "",
    processedAt: "",
    vendorName: "Công ty Dù Bay Biển Đà Nẵng",
    bankName: "BIDV Hải Châu",
    bankAccountNumber: "1281000998877",
    bankAccountHolder: "NGUYEN THU THAO",
    periodText: "Kỳ đối soát 01/10 - 15/10/2024",
    totalOrdersCount: 32,
    createdAt: "2024-10-16T10:00:00Z",
    updatedAt: "2024-10-16T10:00:00Z"
  },
  {
    id: "PAY-2024-09-089",
    vendorId: "vnd-01",
    settlementId: "SETTLE-SEP-02",
    amount: 62400000,
    status: "PAID",
    processedBy: "Vũ Hải Đăng",
    processedAt: "2024-10-02T14:20:00Z",
    vendorName: "Sơn Trà Marine Diving Co., Ltd",
    bankName: "Vietcombank Đà Nẵng",
    bankAccountNumber: "0041000889922",
    bankAccountHolder: "CTY TNHH DU LICH BIEN SON TRA",
    periodText: "Kỳ đối soát 16/09 - 30/09/2024",
    totalOrdersCount: 56,
    createdAt: "2024-10-01T09:00:00Z",
    updatedAt: "2024-10-02T14:20:00Z"
  }
];

export const MOCK_ADMIN_PROMOTIONS: DiscountCode[] = [
  {
    id: "promo-01",
    code: "DANASEA50",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "FIXED",
    discountValue: 50000,
    maxUses: 1000,
    usedCount: 942,
    validFrom: "2024-10-01T00:00:00Z",
    validTo: "2024-10-31T23:59:59Z",
    isActive: true,
    createdAt: "2024-09-28T10:00:00Z",
    updatedAt: "2024-10-20T12:00:00Z"
  },
  {
    id: "promo-02",
    code: "BIENXANH15",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "PERCENTAGE",
    discountValue: 15,
    maxUses: 500,
    usedCount: 142,
    validFrom: "2024-10-15T00:00:00Z",
    validTo: "2024-11-15T23:59:59Z",
    isActive: true,
    createdAt: "2024-10-14T08:00:00Z",
    updatedAt: "2024-10-25T11:00:00Z"
  },
  {
    id: "promo-03",
    code: "HEMUA2024",
    scope: "GLOBAL",
    vendorId: "",
    discountType: "PERCENTAGE",
    discountValue: 20,
    maxUses: 2000,
    usedCount: 2000,
    validFrom: "2024-06-01T00:00:00Z",
    validTo: "2024-08-31T23:59:59Z",
    isActive: false,
    createdAt: "2024-05-25T09:00:00Z",
    updatedAt: "2024-09-01T00:00:00Z"
  }
];

export const MOCK_ADMIN_CONFIGS: SystemConfig[] = [
  {
    id: "cfg-01",
    key: "PLATFORM_COMMISSION_RATE",
    value: "10",
    description: "Tỷ lệ hoa hồng chiết khấu sàn mặc định (%) áp dụng trên giá trị hợp đồng dịch vụ",
    updatedBy: "Vũ Hải Đăng",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-10-15T10:00:00Z"
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
    updatedAt: "2024-10-01T09:30:00Z"
  },
  {
    id: "cfg-04",
    key: "WEATHER_MAX_SAFE_WIND_KMH",
    value: "25",
    description: "Ngưỡng vận tốc gió giật (km/h) kích hoạt cảnh báo Cờ Vàng / Cờ Đỏ",
    updatedBy: "Trần Minh Quân",
    createdAt: "2024-02-10T08:00:00Z",
    updatedAt: "2024-10-01T09:30:00Z"
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

export const MOCK_ADMIN_AUDIT_LOGS: (AuditLog & {
  actorName?: string;
  actorEmail?: string;
})[] = [
  {
    id: "LOG-99214",
    actorUserId: "usr-adm-01",
    action: "APPROVE_PAYOUT",
    entityType: "PayoutRequest",
    entityId: "PAY-2024-09-089",
    metadata: JSON.stringify({ amount: 62400000, bankRef: "VCB-8839210", vendor: "Sơn Trà Marine Diving Co., Ltd" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-10-02T14:20:00Z",
    updatedAt: "2024-10-02T14:20:00Z"
  },
  {
    id: "LOG-99215",
    actorUserId: "usr-adm-01",
    action: "VERIFY_VENDOR",
    entityType: "Vendor",
    entityId: "vnd-01",
    metadata: JSON.stringify({ badgeTier: "PLATINUM", licenseNo: "0401988231", verifiedDocs: 4 }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-03-05T09:00:00Z",
    updatedAt: "2024-03-05T09:00:00Z"
  },
  {
    id: "LOG-99216",
    actorUserId: "usr-adm-02",
    action: "RESOLVE_DISPUTE",
    entityType: "Dispute",
    entityId: "DSP-03",
    metadata: JSON.stringify({ resolution: "REFUND_200K", penalty: "WARNING_NOTE", vendorId: "vnd-03" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2024-10-24T15:00:00Z",
    updatedAt: "2024-10-24T15:00:00Z"
  },
  {
    id: "LOG-99217",
    actorUserId: "usr-adm-01",
    action: "UPDATE_SYSTEM_CONFIG",
    entityType: "SystemConfig",
    entityId: "cfg-01",
    metadata: JSON.stringify({ oldVal: "12%", newVal: "10%", reason: "Chương trình kích cầu du lịch biển mùa thu" }),
    actorName: "Vũ Hải Đăng",
    actorEmail: "dang.cangvu@danasea.gov.vn",
    createdAt: "2024-10-15T10:00:00Z",
    updatedAt: "2024-10-15T10:00:00Z"
  },
  {
    id: "LOG-99218",
    actorUserId: "usr-adm-02",
    action: "LOCK_USER_ACCOUNT",
    entityType: "User",
    entityId: "usr-vnd-03",
    metadata: JSON.stringify({ reason: "Tạm dừng hoạt động chờ bổ sung chứng chỉ cứu hộ đường thủy" }),
    actorName: "Trần Minh Quân",
    actorEmail: "quan.dieuhanh@danasea.vn",
    createdAt: "2024-10-22T08:15:00Z",
    updatedAt: "2024-10-22T08:15:00Z"
  }
];
