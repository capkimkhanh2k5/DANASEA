export interface FlashSaleExperience {
  id: string;
  title: string;
  titleEn: string;
  category: string;
  categoryEn: string;
  location: string;
  locationEn: string;
  duration: string;
  durationEn: string;
  originalPrice: number;
  salePrice: number;
  discountPercent: number;
  rating: number;
  reviewCount: number;
  slotsLeft: number;
  totalSlots: number;
  badge?: string;
  badgeEn?: string;
  imageUrl: string;
}

export interface ActivityCardData {
  id: string;
  title: string;
  titleEn: string;
  category: string;
  categoryEn: string;
  location: string;
  duration: string;
  durationEn: string;
  price: number;
  rating: number;
  reviewCount: number;
  badge?: string;
  badgeEn?: string;
  imageUrl: string;
}

export interface CoastalBayData {
  id: string;
  name: string;
  nameEn: string;
  tagline: string;
  taglineEn: string;
  bestTime: string;
  bestTimeEn: string;
  waterDepth: string;
  tideCondition: string;
  tideConditionEn: string;
  imageUrl: string;
  tourCount: number;
}

export interface OceanSportShowcase {
  id: string;
  index: string;
  name: string;
  nameEn: string;
  level: string;
  levelEn: string;
  description: string;
  descriptionEn: string;
  highlights: string[];
  highlightsEn: string[];
  startingPrice: number;
  imageUrl: string;
}

// 1. Coastal Bays Expanding Panels (Danang Geographic Landmarks)
export const COASTAL_BAYS: CoastalBayData[] = [
  {
    id: 'bay-1',
    name: 'Bãi Biển Mỹ Khê',
    nameEn: 'My Khe Beach',
    tagline: 'Top 6 Bãi Biển Quyến Rũ Nhất Hành Tinh - Forbes',
    taglineEn: 'Top 6 Most Attractive Beaches on Earth - Forbes',
    bestTime: '05:00 - 08:30 & 16:00 - 18:30',
    bestTimeEn: '05:00 - 08:30 & 16:00 - 18:30',
    waterDepth: '1.2m - 3.5m',
    tideCondition: 'Sóng êm, bãi cát thoai thoải, nước ấm',
    tideConditionEn: 'Gentle waves, gradual slope, warm water',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=80',
    tourCount: 16,
  },
  {
    id: 'bay-2',
    name: 'Bán Đảo Sơn Trà',
    nameEn: 'Son Tra Peninsula',
    tagline: 'Viên Ngọc Xanh - Rạn San Hô Nguyên Sinh Tuyệt Sắc',
    taglineEn: 'Green Jewel - Pristine Biodiversity & Corals',
    bestTime: '07:30 - 11:30 (Nước trong nhất)',
    bestTimeEn: '07:30 - 11:30 (Highest visibility)',
    waterDepth: '3.0m - 12.0m',
    tideCondition: 'Tầm nhìn 10m - 14m, nhiều vách đá vôi',
    tideConditionEn: '10-14m visibility, limestone reefs',
    imageUrl: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80',
    tourCount: 12,
  },
  {
    id: 'bay-3',
    name: 'Bãi Rạng & Hòn Sụp',
    nameEn: 'Bai Rang & Hon Sup',
    tagline: 'Thiên Đường Lặn Biển & Câu Cá Bờ Cực Hot',
    taglineEn: 'Premier Snorkeling & Coastal Reef Fishing',
    bestTime: '08:00 - 14:00',
    bestTimeEn: '08:00 - 14:00',
    waterDepth: '2.0m - 6.0m',
    tideCondition: 'Nước phẳng lặng, quần thể cá hề & sao biển',
    tideConditionEn: 'Calm water, clownfish & starfish colonies',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=1200&q=80',
    tourCount: 9,
  },
  {
    id: 'bay-4',
    name: 'Bãi Biển Non Nước',
    nameEn: 'Non Nuoc Beach',
    tagline: 'Thơ Mộng Dưới Chân Núi Ngũ Hành Sơn Kì Vĩ',
    taglineEn: 'Serene Haven at the Foot of Marble Mountains',
    bestTime: '15:30 - 18:30 (Hoàng hôn lộng gió)',
    bestTimeEn: '15:30 - 18:30 (Breezy Golden Hour)',
    waterDepth: '1.0m - 2.8m',
    tideCondition: 'Bờ cát trắng mịn trải dài 5km, gió lộng',
    tideConditionEn: '5km white sand coastline, gentle breeze',
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=1200&q=80',
    tourCount: 8,
  },
];

// 2. Ocean Sports Showcase (Hover-to-swap overview)
export const OCEAN_SPORTS_SHOWCASE: OceanSportShowcase[] = [
  {
    id: 'sport-1',
    index: '01',
    name: 'Chèo SUP Đón Bình Minh',
    nameEn: 'Sunrise Paddleboarding',
    level: 'Dễ tiếp cận • Mọi lứa tuổi',
    levelEn: 'Easy • All ages welcome',
    description: 'Thả mình giữa làn nước biển sớm mai, đón tia nắng đầu tiên ló rạng trên đường chân trời Mỹ Khê. Thao tác đơn giản, huấn luyện viên kèm sát 1:1.',
    descriptionEn: 'Glide effortlessly on mirrored morning waters, greeting the first sunrise rays over My Khe horizon. Beginner-friendly with 1:1 certified instructor.',
    highlights: ['Bao gồm trọn bộ ván SUP, mái chèo carbon, áo phao đạt chuẩn', 'Tặng bộ ảnh nhiếp ảnh gia chụp máy cơ chuyên nghiệp', 'Nước uống giải khát & túi chống nước điện thoại'],
    highlightsEn: ['Full gear: Carbon paddle, certified life vest, SUP board', 'Free photoshoot package by professional photographer', 'Refreshment drink & waterproof phone pouch included'],
    startingPrice: 280000,
    imageUrl: 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=1000&q=80',
  },
  {
    id: 'sport-2',
    index: '02',
    name: 'Lặn Bình Khí Scuba Diving',
    nameEn: 'Scuba Diving Adventure',
    level: 'Thể lực vừa phải • Kèm Master Diver',
    levelEn: 'Moderate fitness • Master Diver guided',
    description: 'Chìm đắm vào lòng đại dương Sơn Trà, ngắm nhìn rạn san hô đĩa, san hô cành hàng trăm năm tuổi và những đàn cá sặc sỡ bơi lội xung quanh.',
    descriptionEn: 'Immerse into Son Tra underwater paradise, marveling at centuries-old coral formations and schools of tropical marine life.',
    highlights: ['Trang bị bình khí nén oxy tinh khiết, kính lặn chuyên dụng', 'Hướng dẫn thở & xử lý áp suất tai chi tiết trước khi xuống nước', 'Bảo hiểm du lịch thể thao mạo hiểm 100%'],
    highlightsEn: ['Full scuba gear: Pure compressed air tank, mask, fins', 'Pre-dive ear pressure & breathing safety briefing', '100% Comprehensive marine adventure insurance'],
    startingPrice: 550000,
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1000&q=80',
  },
  {
    id: 'sport-3',
    index: '03',
    name: 'Dù Lượn Bay Trên Vịnh Biển',
    nameEn: 'Coastal Bay Parasailing',
    level: 'Cực đã • Không cần kinh nghiệm',
    levelEn: 'Top thrill • No experience required',
    description: 'Cano cao tốc tăng tốc đưa bạn bay vút lên độ cao 70m so với mặt biển. Ngắm trọn vẹn đường cong bán đảo Sơn Trà và cung đường biển Đà Nẵng tuyệt mỹ.',
    descriptionEn: 'Speedboat accelerates to lift you 70 meters high into the sky. Soak in the breathtaking curve of Son Tra Peninsula and the Da Nang skyline.',
    highlights: ['Dù đôi hoặc dù đơn chịu tải 250kg an toàn tuyệt đối', 'Hệ thống tời cuốn tự động êm ái, hạ cánh nhẹ nhàng', 'Trải nghiệm nhúng chân nước biển cực sảng khoái'],
    highlightsEn: ['Tandem/single parachute tested up to 250kg capacity', 'Smooth hydraulic winch system with soft water landing', 'Refreshing optional sea dip during flight'],
    startingPrice: 650000,
    imageUrl: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1000&q=80',
  },
  {
    id: 'sport-4',
    index: '04',
    name: 'Mô Tô Nước Jet Ski Cao Tốc',
    nameEn: 'High-speed Jet Ski Waves',
    level: 'Cảm giác mạnh • Có HLV hỗ trợ',
    levelEn: 'Extreme thrill • Instructor supported',
    description: 'Tự tay vít ga cầm lái cỗ máy phản lực 1800cc xé toang bọt sóng trắng xóa. Cảm nhận adrenaline dâng trào giữa biển khơi bao la.',
    descriptionEn: 'Take the handlebars of a 1800cc jet engine machine carving through ocean waves. Feel the pure adrenaline rush amidst the vast turquoise ocean.',
    highlights: ['Mô tô nước Yamaha WaveRunner đời mới 2025/2026', 'Áo phao gia cường chống va đập và còi cứu sinh khẩn cấp', 'Huấn luyện viên kèm lái hoặc tự do cầm lái theo tuyến phao an toàn'],
    highlightsEn: ['Latest Yamaha WaveRunner 2025/2026 edition', 'Impact-resistant reinforced life jacket with whistle', 'Instructor accompaniment or solo ride within marked safe zones'],
    startingPrice: 550000,
    imageUrl: 'https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=1000&q=80',
  },
];

// 3. Guest Memories Gallery Photos (Authentic Portraits & Joyful Traveler Moments)
export const GUEST_GALLERY_ROW_1 = [
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=600&q=80',
];

export const GUEST_GALLERY_ROW_2 = [
  'https://images.unsplash.com/photo-1529156069898-49953e39b3ac?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?auto=format&fit=crop&w=600&q=80',
  'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=600&q=80',
];
