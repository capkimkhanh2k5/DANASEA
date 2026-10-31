export interface TopDestination {
  id: string;
  name: string;
  nameEn: string;
  activityCount: string;
  activityCountEn: string;
  highlight: string;
  highlightEn: string;
  rating: number;
  reviewCount: number;
  imageUrl: string;
  link: string;
}

export const TOP_RATED_DESTINATIONS: TopDestination[] = [
  {
    id: 'dest-son-tra',
    name: 'Bán Đảo Sơn Trà',
    nameEn: 'Son Tra Peninsula',
    activityCount: '8 hoạt động thể thao biển',
    activityCountEn: '8 marine activities',
    highlight: 'Rạn san hô nguyên sinh',
    highlightEn: 'Pristine coral sanctuary',
    rating: 4.98,
    reviewCount: 1420,
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    link: '/search?location=S%C6%A1n%20Tr%C3%A0'
  },
  {
    id: 'dest-my-khe',
    name: 'Bãi Biển Mỹ Khê',
    nameEn: 'My Khe Beach',
    activityCount: '12 trải nghiệm lướt sóng & cano',
    activityCountEn: '12 watersports & tours',
    highlight: 'Bờ cát trắng mịn, sóng êm',
    highlightEn: 'White sand, gentle waves',
    rating: 4.95,
    reviewCount: 2840,
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80',
    link: '/search?location=M%E1%BB%B9%20Kh%C3%AA'
  },
  {
    id: 'dest-bai-rang',
    name: 'Bãi Rạng & Mân Thái',
    nameEn: 'Bai Rang & Man Thai',
    activityCount: '6 tour kayak & phao chuối',
    activityCountEn: '6 kayak & banana boat tours',
    highlight: 'Vùng nước phẳng lặng chân núi',
    highlightEn: 'Sheltered calm waters',
    rating: 4.93,
    reviewCount: 860,
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=600&q=80',
    link: '/search?location=B%C3%A3i%20R%E1%BA%A1ng'
  },
  {
    id: 'dest-non-nuoc',
    name: 'Bãi Biển Non Nước',
    nameEn: 'Non Nuoc Coastal Bay',
    activityCount: '5 hoạt động lướt ván & câu đêm',
    activityCountEn: '5 surfing & night fishing tours',
    highlight: 'Sóng tự nhiên đón gió đại dương',
    highlightEn: 'Natural ocean surf swells',
    rating: 4.91,
    reviewCount: 620,
    imageUrl: 'https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=600&q=80',
    link: '/search?location=Non%20N%C6%B0%E1%BB%9Bc'
  },
  {
    id: 'dest-dhc-marina',
    name: 'Bến Du Thuyền DHC Marina',
    nameEn: 'DHC Marina Port',
    activityCount: '4 hải trình hoàng hôn vịnh biển',
    activityCountEn: '4 sunset catamaran cruises',
    highlight: 'Cửa vịnh ngắm thành phố lên đèn',
    highlightEn: 'Bay mouth city lights view',
    rating: 4.90,
    reviewCount: 540,
    imageUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=600&q=80',
    link: '/search?category=du-thuyen-hoang-hon'
  },
  {
    id: 'dest-hon-sup',
    name: 'Hòn Sụp & Bãi Bụt',
    nameEn: 'Hon Sup & Bai But',
    activityCount: '4 điểm lặn ngắm san hô bãi cạn',
    activityCountEn: '4 shallow reef dive sites',
    highlight: 'Tầm nhìn nước đạt 8–12 mét',
    highlightEn: '8–12m underwater visibility',
    rating: 4.96,
    reviewCount: 710,
    imageUrl: 'https://images.unsplash.com/photo-1682687220063-4742bd7fd538?auto=format&fit=crop&w=600&q=80',
    link: '/search?category=lan-ngam-san-ho'
  }
];

export interface VisitedExperience {
  link?: string;
  name: string;
  nameEn: string;
  location: string;
  visitorsText: string;
  visitorsTextEn: string;
  rating: number;
  price: string;
  imageUrl: string;
  searchFilter: string;
}

export const TOP_VISITED_EXPERIENCES: VisitedExperience[] = [
  {
    name: 'Chèo Ván SUP Đón Bình Minh Biển Mỹ Khê',
    nameEn: 'Sunrise SUP Paddleboarding at My Khe Beach',
    location: 'Bãi biển Mỹ Khê',
    visitorsText: '2.450+ du khách đã đặt',
    visitorsTextEn: '2,450+ guests booked',
    rating: 4.9,
    price: '200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Cano Cao Tốc Kéo Dù Bay Vịnh Đà Nẵng',
    nameEn: 'Tandem Parasailing Over Da Nang Bay',
    location: 'Vịnh Mỹ Khê',
    visitorsText: '1.820+ du khách đã đặt',
    visitorsTextEn: '1,820+ guests booked',
    rating: 4.9,
    price: '650.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Lặn Ngắm San Hô Hòn Sụp Bán Đảo Sơn Trà',
    nameEn: 'Hon Sup Coral Reef Snorkeling Speedboat Tour',
    location: 'Bán đảo Sơn Trà',
    visitorsText: '1.640+ du khách đã đặt',
    visitorsTextEn: '1,640+ guests booked',
    rating: 5.0,
    price: '450.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    name: 'Mô Tô Nước Jetski Yamaha 150HP Lướt Sóng',
    nameEn: 'Yamaha High-Speed Sea Jet Ski Experience',
    location: 'Mỹ Khê & Non Nước',
    visitorsText: '1.290+ du khách đã đặt',
    visitorsTextEn: '1,290+ guests booked',
    rating: 4.8,
    price: '550.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Phao Chuối Kéo Cano Tốc Độ Cho Nhóm Bạn',
    nameEn: 'High-Speed Banana Boat Group Ride',
    location: 'Bãi Rạng Sơn Trà',
    visitorsText: '980+ du khách đã đặt',
    visitorsTextEn: '980+ guests booked',
    rating: 4.8,
    price: '150.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Biển',
    nameEn: 'Luxury Catamaran Sunset Cruise & Tea',
    location: 'Bến DHC Marina',
    visitorsText: '850+ du khách đã đặt',
    visitorsTextEn: '850+ guests booked',
    rating: 4.9,
    price: '1.200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'du-thuyen-hoang-hon'
  },
  {
    name: 'Lặn Bình Khí Scuba Diving Bãi Bụt Sơn Trà',
    nameEn: 'PADI Certified Scuba Diving at Bai But',
    location: 'Bãi Bụt Sơn Trà',
    visitorsText: '760+ du khách đã đặt',
    visitorsTextEn: '760+ guests booked',
    rating: 5.0,
    price: '850.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1682687220063-4742bd7fd538?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    name: 'Khóa Học Lướt Ván Sóng Surfboard Căn Bản',
    nameEn: 'Beginner Surfing Class with Certified Coach',
    location: 'Bãi biển Mỹ Khê',
    visitorsText: '620+ du khách đã đặt',
    visitorsTextEn: '620+ guests booked',
    rating: 4.9,
    price: '500.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  }
];

export interface WeatherExperience {
  link?: string;
  name: string;
  nameEn: string;
  location: string;
  conditionReason: string;
  conditionReasonEn: string;
  rating: number;
  price: string;
  imageUrl: string;
  searchFilter: string;
}

export const WEATHER_RECOMMENDED_EXPERIENCES: WeatherExperience[] = [
  {
    name: 'Lặn Ngắm San Hô Hòn Sụp Bán Đảo Sơn Trà',
    nameEn: 'Hon Sup Coral Reef Snorkeling Speedboat Tour',
    location: 'Bán đảo Sơn Trà',
    conditionReason: 'Nước trong 8-12m, không sóng ngầm',
    conditionReasonEn: '8-12m water visibility, calm current',
    rating: 5.0,
    price: '450.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    name: 'Chèo Ván SUP Đón Bình Minh Biển Mỹ Khê',
    nameEn: 'Sunrise SUP Paddleboarding at My Khe Beach',
    location: 'Bãi biển Mỹ Khê',
    conditionReason: 'Mặt biển phẳng lặng 0.3m, gió nhẹ 8km/h',
    conditionReasonEn: 'Calm 0.3m swell, light 8km/h breeze',
    rating: 4.9,
    price: '200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Cano Cao Tốc Kéo Dù Bay Vịnh Đà Nẵng',
    nameEn: 'Tandem Parasailing Over Da Nang Bay',
    location: 'Vịnh Mỹ Khê',
    conditionReason: 'Bầu trời quang mây, tầm nhìn thoáng đãng',
    conditionReasonEn: 'Clear blue skies, optimal aerial vision',
    rating: 4.9,
    price: '650.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Chèo Thuyền Kayak Vượt Sóng Vịnh Bãi Rạng',
    nameEn: 'Sea Kayaking Expedition at Bai Rang Cove',
    location: 'Bãi Rạng Sơn Trà',
    conditionReason: 'Vùng vịnh khuất gió, sóng êm an toàn',
    conditionReasonEn: 'Sheltered windward bay, safe calm water',
    rating: 4.8,
    price: '250.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Biển',
    nameEn: 'Luxury Catamaran Sunset Cruise & Tea',
    location: 'Bến DHC Marina',
    conditionReason: 'Chiều tà mát 26°C, hoàng hôn không mưa',
    conditionReasonEn: 'Pleasant 26°C evening, rain-free sunset',
    rating: 4.9,
    price: '1.200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'du-thuyen-hoang-hon'
  },
  {
    name: 'Mô Tô Nước Jetski Yamaha 150HP Lướt Sóng',
    nameEn: 'Yamaha High-Speed Sea Jet Ski Experience',
    location: 'Mỹ Khê & Non Nước',
    conditionReason: 'Sóng ven bờ 0.4–0.6m lý tưởng tăng tốc',
    conditionReasonEn: '0.4-0.6m surf ideal for wave rides',
    rating: 4.8,
    price: '550.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  }
];
