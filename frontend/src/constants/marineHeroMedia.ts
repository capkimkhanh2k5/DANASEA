/**
 * DANASEA - Marine High-Resolution Image Assets Catalog (2K Quality)
 * Localized in /public/images/hero/ for high performance, zero external CDN dependency.
 * Designed for Hero Showcase, Full-width Banners, and Backend Service Seeding.
 */

export interface MarineHeroAsset {
  id: string;
  filename: string;
  localPath: string;
  category: string;
  vietnameseTitle: string;
  englishTitle: string;
  location: string;
  suggestedServiceKeywords: string[];
}

export const DANASEA_HERO_ASSETS: MarineHeroAsset[] = [
  {
    id: 'hero-asset-01',
    filename: '01-lan-ngam-san-ho-sontra.jpg',
    localPath: '/images/hero/01-lan-ngam-san-ho-sontra.jpg',
    category: 'Lặn san hô',
    vietnameseTitle: 'Lặn ngắm san hô tự nhiên Bán đảo Sơn Trà',
    englishTitle: 'Hon Sup Pristine Coral Reef Snorkeling',
    location: 'Bán đảo Sơn Trà',
    suggestedServiceKeywords: ['lặn', 'san hô', 'hòn sụp', 'snorkeling']
  },
  {
    id: 'hero-asset-02',
    filename: '02-cheo-sup-binh-minh-my-khe.jpg',
    localPath: '/images/hero/02-cheo-sup-binh-minh-my-khe.jpg',
    category: 'Chèo SUP',
    vietnameseTitle: 'Chèo ván SUP đón bình minh biển Mỹ Khê',
    englishTitle: 'Sunrise SUP Paddleboarding at My Khe Beach',
    location: 'Bãi biển Mỹ Khê',
    suggestedServiceKeywords: ['sup', 'bình minh', 'mân thái', 'paddle']
  },
  {
    id: 'hero-asset-03',
    filename: '03-du-bay-cano-vinh-da-nang.jpg',
    localPath: '/images/hero/03-du-bay-cano-vinh-da-nang.jpg',
    category: 'Dù bay cano',
    vietnameseTitle: 'Cano kéo dù bay trên vịnh Đà Nẵng',
    englishTitle: 'Tandem Parasailing Over Da Nang Bay',
    location: 'Vịnh Mỹ Khê',
    suggestedServiceKeywords: ['dù bay', 'cano kéo', 'parasailing']
  },
  {
    id: 'hero-asset-04',
    filename: '04-mo-to-nuoc-jetski-bien-non-nuoc.jpg',
    localPath: '/images/hero/04-mo-to-nuoc-jetski-bien-non-nuoc.jpg',
    category: 'Mô tô nước',
    vietnameseTitle: 'Mô tô nước Jetski cảm giác mạnh Non Nước',
    englishTitle: 'High-Speed Wave Runner Jet Skiing',
    location: 'Bãi biển Non Nước',
    suggestedServiceKeywords: ['mô tô nước', 'jetski', 'tốc độ cao']
  },
  {
    id: 'hero-asset-05',
    filename: '05-lop-hoc-luot-song-my-khe.jpg',
    localPath: '/images/hero/05-lop-hoc-luot-song-my-khe.jpg',
    category: 'Lướt sóng',
    vietnameseTitle: 'Lớp học lướt sóng biển Mỹ Khê',
    englishTitle: 'My Khe Surfing School & Wave Coaching',
    location: 'Bãi biển Mỹ Khê',
    suggestedServiceKeywords: ['lướt sóng', 'surfing', 'surf shack']
  },
  {
    id: 'hero-asset-06',
    filename: '06-cheo-thuyen-kayak-bai-rang.jpg',
    localPath: '/images/hero/06-cheo-thuyen-kayak-bai-rang.jpg',
    category: 'Kayak',
    vietnameseTitle: 'Chèo thuyền Kayak bãi Rạng Sơn Trà',
    englishTitle: 'Coastal Kayaking Adventure at Bai Rang',
    location: 'Bãi Rạng – Sơn Trà',
    suggestedServiceKeywords: ['kayak', 'bãi rạng', 'bãi bụt']
  },
  {
    id: 'hero-asset-07',
    filename: '07-lan-binh-khi-scuba-diving.jpg',
    localPath: '/images/hero/07-lan-binh-khi-scuba-diving.jpg',
    category: 'Lặn bình khí',
    vietnameseTitle: 'Lặn biển bình khí chuyên sâu Scuba Diving',
    englishTitle: 'Certified Deep Sea Scuba Diving Tour',
    location: 'Hòn Sụp – Sơn Trà',
    suggestedServiceKeywords: ['scuba', 'bình khí', 'lặn sâu']
  },
  {
    id: 'hero-asset-08',
    filename: '08-cano-cao-toc-luot-vinh-da-nang.jpg',
    localPath: '/images/hero/08-cano-cao-toc-luot-vinh-da-nang.jpg',
    category: 'Cano cao tốc',
    vietnameseTitle: 'Tour cano cao tốc lướt vịnh biển Đà Nẵng',
    englishTitle: 'Speedboat Coastal Island Discovery Tour',
    location: 'Vịnh Đà Nẵng',
    suggestedServiceKeywords: ['cano', 'cao tốc', 'tour vịnh']
  },
  {
    id: 'hero-asset-09',
    filename: '09-phao-chuoi-cam-giac-manh.jpg',
    localPath: '/images/hero/09-phao-chuoi-cam-giac-manh.jpg',
    category: 'Phao chuối',
    vietnameseTitle: 'Trò chơi phao chuối đồng đội bãi biển',
    englishTitle: 'Banana Boat Group Coastal Thrill Ride',
    location: 'Bãi biển Mỹ Khê',
    suggestedServiceKeywords: ['phao chuối', 'danabeach', 'chuối']
  },
  {
    id: 'hero-asset-10',
    filename: '10-ngam-hoang-hon-vinh-bien.jpg',
    localPath: '/images/hero/10-ngam-hoang-hon-vinh-bien.jpg',
    category: 'Ngắm hoàng hôn',
    vietnameseTitle: 'Ngắm hoàng hôn & bình minh trên vịnh biển',
    englishTitle: 'Sunset & Sunrise Coastal Relaxation Tour',
    location: 'Vịnh Đà Nẵng',
    suggestedServiceKeywords: ['hoàng hôn', 'sunset', 'bình minh']
  }
];
