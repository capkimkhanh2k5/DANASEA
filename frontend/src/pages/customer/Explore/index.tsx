import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ExploreCarouselDeck } from '../../../components/customer/ExploreCarouselDeck';
import {
  TOP_RATED_DESTINATIONS,
  TOP_VISITED_EXPERIENCES,
  WEATHER_RECOMMENDED_EXPERIENCES,
} from '../../../data/exploreCarouselData';
import { useLanguageStore } from '../../../store/useLanguageStore';
import { serviceCatalogService } from '../../../services/serviceCatalogService';
import type { ServiceSummaryDto } from '../../../types/service';
import {
  getHeroImageUrl,
  getCardThumbnailUrl,
  FALLBACK_HERO_IMAGE,
  FALLBACK_CARD_IMAGE,
} from '../../../utils/imageOptimizer';
import { Star, ArrowRight, Clock } from 'lucide-react';

interface HeroFeaturedSpot {
  id: string;
  targetServiceId?: string;
  name: string;
  nameEn: string;
  shortDesc: string;
  shortDescEn: string;
  rating: number;
  reviewCount: number;
  location: string;
  locationEn: string;
  category: string;
  price: string;
  imageUrl: string;
  bgImageUrl?: string;
  videoUrl?: string;
  searchFilter: string;
  link?: string;
}

const HERO_FEATURED_SPOTS: HeroFeaturedSpot[] = [
  {
    id: 'hero-1',
    name: 'Lặn San Hô Tự Nhiên Hòn Sụp',
    nameEn: 'Hon Sup Pristine Coral Reef Snorkeling',
    shortDesc: 'Quần thể rạn san hô nguyên sinh quý hiếm cùng thợ lặn bản địa đạt chuẩn quốc tế.',
    shortDescEn: 'Pristine coral reefs with certified local instructors in crystal-clear morning waters.',
    rating: 5.0,
    reviewCount: 142,
    location: 'Bán đảo Sơn Trà',
    locationEn: 'Son Tra Peninsula',
    category: 'Lặn san hô',
    price: '450.000 ₫',
    imageUrl: '/images/hero/01-lan-ngam-san-ho-sontra.jpg',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    id: 'hero-2',
    name: 'Chèo Ván SUP Đón Bình Minh Mỹ Khê',
    nameEn: 'Sunrise SUP Paddleboarding at My Khe',
    shortDesc: 'Đón tia nắng sớm trên mặt biển phẳng lặng với huấn luyện viên chuyên nghiệp 1:1.',
    shortDescEn: 'Catch the golden sunrise over calm coastal waters with 1:1 instructor guidance.',
    rating: 4.9,
    reviewCount: 98,
    location: 'Bãi biển Mỹ Khê',
    locationEn: 'My Khe Beach',
    category: 'Chèo SUP',
    price: '200.000 ₫',
    imageUrl: '/images/hero/02-cheo-sup-binh-minh-my-khe.jpg',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    id: 'hero-3',
    name: 'Cano Cao Tốc Kéo Dù Bay Vịnh Đà Nẵng',
    nameEn: 'Tandem Parasailing Over Da Nang Bay',
    shortDesc: 'Thưởng ngoạn trọn vẹn toàn cảnh vịnh biển và bán đảo Sơn Trà từ độ cao trên không.',
    shortDescEn: 'Panoramic aerial views of the entire coastal bay and lush peninsula mountains.',
    rating: 4.9,
    reviewCount: 86,
    location: 'Vịnh Mỹ Khê',
    locationEn: 'My Khe Bay',
    category: 'Dù bay cano',
    price: '650.000 ₫',
    imageUrl: '/images/hero/03-du-bay-cano-vinh-da-nang.jpg',
    searchFilter: 'cano-du-bay'
  },
  {
    id: 'hero-4',
    name: 'Mô Tô Nước Jetski Yamaha 150HP',
    nameEn: 'Yamaha 150HP High-Speed Wave Rider',
    shortDesc: 'Chinh phục từng ngọn sóng biển với mô tô nước thế hệ mới và phao cứu hộ theo sát.',
    shortDescEn: 'Command the waves with modern high-speed jet skis and maritime safety escort.',
    rating: 4.8,
    reviewCount: 65,
    location: 'Bãi biển Non Nước',
    locationEn: 'Non Nuoc Beach',
    category: 'Mô tô nước',
    price: '550.000 ₫',
    imageUrl: '/images/hero/04-mo-to-nuoc-jetski-bien-non-nuoc.jpg',
    searchFilter: 'cano-du-bay'
  }
];

interface TopExperience {
  link?: string;
  name: string;
  nameEn: string;
  location: string;
  category: string;
  rating: number;
  reviewCount: number;
  price: string;
  imageUrl: string;
  searchFilter: string;
}

const TOP_10_EXPERIENCES: TopExperience[] = [
  {
    name: 'Lặn Ngắm San Hô Hòn Sụp Bán Đảo Sơn Trà',
    nameEn: 'Hon Sup Coral Reef Snorkeling Speedboat Tour',
    location: 'Bán đảo Sơn Trà',
    category: 'Lặn san hô',
    rating: 5.0,
    reviewCount: 142,
    price: '450.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    name: 'Chèo Ván SUP Đón Bình Minh Biển Mỹ Khê',
    nameEn: 'Sunrise SUP Paddleboarding at My Khe Beach',
    location: 'Bãi biển Mỹ Khê',
    category: 'Chèo SUP',
    rating: 4.9,
    reviewCount: 98,
    price: '200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Cano Cao Tốc Kéo Dù Bay Vịnh Đà Nẵng',
    nameEn: 'Tandem Parasailing Over Da Nang Bay',
    location: 'Vịnh Mỹ Khê',
    category: 'Dù bay cano',
    rating: 4.9,
    reviewCount: 86,
    price: '650.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Mô Tô Nước Jetski Yamaha 150HP Lướt Sóng',
    nameEn: 'Yamaha High-Speed Sea Jet Ski Experience',
    location: 'Mỹ Khê & Non Nước',
    category: 'Mô tô nước',
    rating: 4.8,
    reviewCount: 65,
    price: '550.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  },
  {
    name: 'Lặn Bình Khí Scuba Diving Bãi Bụt Sơn Trà',
    nameEn: 'PADI Certified Scuba Diving at Bai But',
    location: 'Bãi Bụt Sơn Trà',
    category: 'Lặn Scuba',
    rating: 5.0,
    reviewCount: 52,
    price: '850.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1682687220063-4742bd7fd538?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'lan-ngam-san-ho'
  },
  {
    name: 'Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Biển',
    nameEn: 'Luxury Catamaran Sunset Cruise & Tea',
    location: 'Bến DHC Marina',
    category: 'Du thuyền',
    rating: 4.9,
    reviewCount: 77,
    price: '1.200.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'du-thuyen-hoang-hon'
  },
  {
    name: 'Chèo Thuyền Kayak Vượt Sóng Vịnh Bãi Rạng',
    nameEn: 'Sea Kayaking Expedition at Bai Rang Cove',
    location: 'Bãi Rạng Sơn Trà',
    category: 'Kayak biển',
    rating: 4.8,
    reviewCount: 43,
    price: '250.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Tour Trải Nghiệm Câu Mực Đêm Biển Non Nước',
    nameEn: 'Night Squid Fishing with Local Fishermen',
    location: 'Bãi biển Non Nước',
    category: 'Câu mực đêm',
    rating: 4.7,
    reviewCount: 39,
    price: '400.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'du-thuyen-hoang-hon'
  },
  {
    name: 'Khóa Học Lướt Ván Sóng Surfboard Căn Bản',
    nameEn: 'Beginner Surfing Class with Certified Coach',
    location: 'Bãi biển Mỹ Khê',
    category: 'Lướt sóng',
    rating: 4.9,
    reviewCount: 31,
    price: '500.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cheo-sup-kayak'
  },
  {
    name: 'Phao Chuối Kéo Cano Tốc Độ Cho Nhóm Bạn',
    nameEn: 'High-Speed Banana Boat Group Ride',
    location: 'Bãi biển Mỹ Khê',
    category: 'Cảm giác mạnh',
    rating: 4.8,
    reviewCount: 58,
    price: '150.000 ₫',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80',
    searchFilter: 'cano-du-bay'
  }
];

interface CuratedItinerary {
  id: string;
  title: string;
  titleEn: string;
  duration: string;
  durationEn: string;
  imageUrl: string;
  highlights: string[];
  highlightsEn: string[];
  estimatedCost: string;
  targetCategory: string;
}

const CURATED_ITINERARIES: CuratedItinerary[] = [
  {
    id: 'itin-1',
    title: 'Hải trình bình minh và lặn san hô nửa ngày',
    titleEn: 'Half-day Sunrise SUP & Coral Reef Tour',
    duration: '05:30 - 11:30 (6 giờ)',
    durationEn: '05:30 - 11:30 (6 hrs)',
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80',
    highlights: [
      'Chèo ván SUP đón bình minh biển Mỹ Khê',
      'Điểm tâm hải sản và giải khát ven bờ',
      'Cano cao tốc ra Hòn Sụp lặn ngắm san hô',
      'Cập bến Bãi Bụt và tắm tráng nước ngọt'
    ],
    highlightsEn: [
      'Sunrise SUP paddleboarding at My Khe Beach',
      'Coastal seafood breakfast & refreshments',
      'Speedboat to Hon Sup for coral snorkeling',
      'Dock at Bai But with freshwater rinse'
    ],
    estimatedCost: '750.000 ₫/khách',
    targetCategory: 'lan-ngam-san-ho'
  },
  {
    id: 'itin-2',
    title: 'Trọn ngày thể thao nước cảm giác mạnh và tốc độ',
    titleEn: 'Full-day Watersports & High-Speed Thrill',
    duration: '08:30 - 17:00 (8.5 giờ)',
    durationEn: '08:30 - 17:00 (8.5 hrs)',
    imageUrl: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80',
    highlights: [
      'Cano kéo dù bay ngắm vịnh biển từ trên cao',
      'Mô tô nước Jetski Yamaha 150HP lướt sóng',
      'Bữa trưa hải sản tươi tại nhà hàng ven vịnh',
      'Lướt ván cano Wakeboarding tại Bãi Rạng'
    ],
    highlightsEn: [
      'Tandem parasailing above Da Nang Bay',
      'High-speed Yamaha Jet Ski wave riding',
      'Fresh local coastal seafood lunch',
      'Wakeboarding session in calm Bai Rang waters'
    ],
    estimatedCost: '1.450.000 ₫/khách',
    targetCategory: 'cano-du-bay'
  },
  {
    id: 'itin-3',
    title: 'Hoàng hôn du thuyền và câu mực đêm ven bờ',
    titleEn: 'Sunset Cruise & Night Squid Angling',
    duration: '15:30 - 21:00 (5.5 giờ)',
    durationEn: '15:30 - 21:00 (5.5 hrs)',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80',
    highlights: [
      'Chèo SUP thư giãn đón gió mát chiều Non Nước',
      'Lên du thuyền ngắm hoàng hôn & tiệc trà nhẹ',
      'Trải nghiệm câu mực đêm giải trí cùng ngư dân',
      'Thưởng thức hải sản tươi nướng trên tàu'
    ],
    highlightsEn: [
      'Gentle sunset paddleboarding on calm waters',
      'Board catamaran for golden-hour sunset tea',
      'Night squid angling experience with fishermen',
      'Fresh catch prepared and served on board'
    ],
    estimatedCost: '1.200.000 ₫/khách',
    targetCategory: 'du-thuyen-hoang-hon'
  }
];

export function Explore() {
  const { language } = useLanguageStore();
  const [activeHeroIndex, setActiveHeroIndex] = useState<number>(0);
  const [isHeroPaused, setIsHeroPaused] = useState<boolean>(false);
  const [liveServices, setLiveServices] = useState<ServiceSummaryDto[]>([]);

  // Fetch real services from Backend API
  useEffect(() => {
    let isMounted = true;
    serviceCatalogService
      .getServices({ size: 40 })
      .then((res) => {
        if (isMounted && res.content && res.content.length > 0) {
          setLiveServices(res.content);
        }
      })
      .catch((err) => {
        console.warn('[Explore] Không thể nạp live services từ BE, dùng fallback:', err);
      });
    return () => {
      isMounted = false;
    };
  }, [language]);

  const formatPrice = (price?: number | null, fallback = '350.000 ₫') => {
    if (price === undefined || price === null || isNaN(Number(price))) return fallback;
    return new Intl.NumberFormat('vi-VN').format(Number(price)) + ' ₫';
  };

  const formatRating = (rating?: number | null, fallback = 4.9) => {
    const num = Number(rating);
    return num > 0 ? num : fallback;
  };

  // 1. Live Hero Spots (Curated High-Res Ocean Photography with Live Backend Linkage)
  const heroSpots: HeroFeaturedSpot[] = HERO_FEATURED_SPOTS.map((spot) => {
    // Find matching real service from Backend by keyword if available
    const matchedService = liveServices.find((s) => {
      const name = s.name.toLowerCase();
      if (spot.id === 'hero-1') return name.includes('lặn') || name.includes('san hô');
      if (spot.id === 'hero-2') return name.includes('sup') || name.includes('bình minh');
      if (spot.id === 'hero-3') return name.includes('dù') || name.includes('parasail');
      if (spot.id === 'hero-4') return name.includes('mô tô') || name.includes('jetski') || name.includes('cano');
      return false;
    });

    return {
      ...spot,
      id: matchedService ? matchedService.id : spot.id,
      name: spot.name,
      nameEn: spot.nameEn,
      price: matchedService ? formatPrice(matchedService.price, spot.price) : spot.price,
      rating: matchedService ? formatRating(matchedService.averageRating, spot.rating) : spot.rating,
      reviewCount: matchedService?.reviewCount || spot.reviewCount,
      link: matchedService ? `/experience/${matchedService.id}` : `/search?category=${spot.searchFilter}`,
    };
  });

  // Auto-play interval for Hero: 4 seconds per slide
  useEffect(() => {
    if (isHeroPaused) return;
    const timer = setInterval(() => {
      setActiveHeroIndex((prev) => (prev + 1) % heroSpots.length);
    }, 4000);
    return () => clearInterval(timer);
  }, [isHeroPaused, heroSpots.length]);

  const currentHero = heroSpots[activeHeroIndex] || heroSpots[0];

  // 2. Live Top 10 Featured Activities
  const top10Activities = liveServices.length > 0
    ? liveServices.slice(0, 10).map((s, idx) => {
        const fallback = TOP_10_EXPERIENCES[idx % TOP_10_EXPERIENCES.length];
        return {
          id: s.id,
          name: s.name,
          nameEn: s.name,
          location: s.address || fallback.location,
          category: fallback.category,
          rating: formatRating(s.averageRating, fallback.rating),
          reviewCount: s.reviewCount || fallback.reviewCount,
          price: formatPrice(s.price, fallback.price),
          imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallback.imageUrl),
          searchFilter: fallback.searchFilter,
          link: `/experience/${s.id}`,
        };
      })
    : TOP_10_EXPERIENCES;

  // 3. Live Most Visited (Sorted by viewCount)
  const topVisitedList = liveServices.length > 0
    ? [...liveServices]
        .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
        .slice(0, 8)
        .map((s, idx) => {
          const fallback = TOP_VISITED_EXPERIENCES[idx % TOP_VISITED_EXPERIENCES.length];
          const views = s.viewCount && s.viewCount > 0 ? s.viewCount : 450 + idx * 170;
          return {
            id: s.id,
            name: s.name,
            nameEn: s.name,
            location: s.address || fallback.location,
            visitorsText: `${views}+ du khách quan tâm`,
            visitorsTextEn: `${views}+ travelers viewed`,
            rating: formatRating(s.averageRating, fallback.rating),
            price: formatPrice(s.price, fallback.price),
            imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallback.imageUrl),
            searchFilter: fallback.searchFilter,
            link: `/experience/${s.id}`,
          };
        })
    : TOP_VISITED_EXPERIENCES;

  // 4. Live Weather Recommended (Calm waters, coral & paddle sports)
  const weatherRecommendedList = liveServices.length > 0
    ? (liveServices.filter((s) => {
        const n = s.name.toLowerCase();
        return n.includes('san hô') || n.includes('sup') || n.includes('kayak') || n.includes('lặn') || n.includes('biển');
      }).concat(liveServices)).slice(0, 8).map((s, idx) => {
        const fallback = WEATHER_RECOMMENDED_EXPERIENCES[idx % WEATHER_RECOMMENDED_EXPERIENCES.length];
        return {
          id: s.id,
          name: s.name,
          nameEn: s.name,
          location: s.address || fallback.location,
          conditionReason: fallback.conditionReason,
          conditionReasonEn: fallback.conditionReasonEn,
          rating: formatRating(s.averageRating, fallback.rating),
          price: formatPrice(s.price, fallback.price),
          imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallback.imageUrl),
          searchFilter: fallback.searchFilter,
          link: `/experience/${s.id}`,
        };
      })
    : WEATHER_RECOMMENDED_EXPERIENCES;

  return (
    <main className="w-full min-h-screen bg-white text-slate-900 pb-20">
      
      {/* 1. Cinematic Split Hero Carousel (Nền ảnh chuyển động mượt mà tương ứng từng Card) */}
      <section
        className="relative w-full overflow-hidden min-h-[560px] sm:min-h-[620px] lg:min-h-[680px] max-h-[740px] flex flex-col justify-end mb-16 select-none"
      >
        {/* Full-width Responsive Hero Background Layers (Tương ứng theo từng Card) */}
        <div className="absolute inset-0 w-full h-full pointer-events-none overflow-hidden">
          {heroSpots.map((spot, idx) => {
            const isCurrent = activeHeroIndex === idx;
            return (
              <div
                key={spot.id}
                className={`absolute inset-0 w-full h-full transition-opacity duration-700 ease-in-out ${
                  isCurrent ? 'opacity-100 z-1' : 'opacity-0 z-0 pointer-events-none'
                }`}
              >
                <img
                  src={getHeroImageUrl(spot.bgImageUrl || spot.imageUrl)}
                  alt={spot.name}
                  loading={idx === 0 ? "eager" : "lazy"}
                  decoding="async"
                  onError={(e) => {
                    // Safe graceful fallback to local 2K scenic image if CDN fails
                    e.currentTarget.src = FALLBACK_HERO_IMAGE;
                  }}
                  className={`w-full h-full object-cover object-center transition-transform duration-[7000ms] ease-out ${
                    isCurrent ? 'scale-[1.02]' : 'scale-100'
                  }`}
                />
              </div>
            );
          })}
        </div>

        {/* 2-Column Split Content Container (Pinned to bottom) */}
        <div className="relative z-10 max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-4 sm:pb-6 grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-10 items-end">
          
          {/* Nửa bên trái: Tên, Mô tả ngắn 1 dòng, Đánh giá sao, Nút hành động */}
          <div className="lg:col-span-5 flex flex-col justify-end text-white pb-2 lg:pb-3 animate-fadeInUp">
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-black text-white tracking-tight leading-tight mb-3 drop-shadow-[0_2px_12px_rgba(0,0,0,0.9)]">
              {language === 'en' ? currentHero.nameEn : currentHero.name}
            </h1>

            {/* Mô tả ngắn cỡ 1 dòng */}
            <p className="text-sm sm:text-base text-white leading-relaxed font-medium mb-3 max-w-xl line-clamp-1 drop-shadow-[0_2px_8px_rgba(0,0,0,0.9)]">
              {language === 'en' ? currentHero.shortDescEn : currentHero.shortDesc}
            </p>

            {/* Dòng đánh giá sao & Địa danh */}
            <div className="flex items-center gap-2.5 mb-6 flex-wrap drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
              <div className="flex items-center gap-1">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className="w-4 h-4 fill-amber-400 text-amber-400 shrink-0" />
                ))}
              </div>
              <span className="font-extrabold text-white text-[13px]">{currentHero.rating.toFixed(1)}</span>
              <span className="text-white/90 text-[13px]">({currentHero.reviewCount} {language === 'en' ? 'reviews' : 'đánh giá'})</span>
              <span className="text-white/70 text-[13px]">•</span>
              <span className="text-sky-300 text-[13px] font-semibold">{currentHero.location}</span>
            </div>

            {/* Action CTA Button */}
            <div>
              <Link
                to={currentHero.link || `/search?category=${currentHero.searchFilter}`}
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-bold text-sm transition-all w-fit active:scale-95 cursor-pointer shadow-md shadow-sky-950/20"
              >
                <span>{language === 'en' ? 'Explore This Experience' : 'Khám phá trải nghiệm này'}</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>

          {/* Nửa bên phải ở dưới: 4 Thẻ nổi bật lớn hơn, nền kính trong suốt (Ocean Glass) */}
          <div
            className="lg:col-span-7 flex flex-col justify-end pb-1"
            onMouseEnter={() => setIsHeroPaused(true)}
            onMouseLeave={() => setIsHeroPaused(false)}
          >
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
              {heroSpots.map((spot, idx) => {
                const isActive = activeHeroIndex === idx;

                return (
                  <div
                    key={spot.id}
                    onClick={() => setActiveHeroIndex(idx)}
                    className={`rounded-2xl p-3.5 sm:p-4 transition-all duration-300 ease-out cursor-pointer flex flex-col justify-between backdrop-blur-md ${
                      isActive
                        ? 'scale-105 z-10 bg-black/45 border border-white/40 shadow-2xl opacity-100 -translate-y-1'
                        : 'scale-100 opacity-75 hover:opacity-100 hover:scale-[1.02] bg-black/25 hover:bg-black/40 border border-white/15 hover:border-white/30 shadow-md'
                    }`}
                  >
                    <div>
                      <div className="relative w-full h-24 sm:h-28 md:h-32 rounded-xl overflow-hidden mb-3 bg-white/5">
                        <img
                          src={getCardThumbnailUrl(spot.imageUrl)}
                          alt={spot.name}
                          loading="lazy"
                          onError={(e) => {
                            e.currentTarget.src = FALLBACK_CARD_IMAGE;
                          }}
                          className="w-full h-full object-cover transition-transform duration-500 hover:scale-105"
                        />
                      </div>
                      <h4 className="text-sm sm:text-base font-bold text-white line-clamp-2 leading-snug">
                        {language === 'en' ? spot.nameEn : spot.name}
                      </h4>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

        </div>
      </section>

      {/* 2. Top 10 Hoạt Động Nổi Bật */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Top 10 Featured Activities' : 'Top 10 Hoạt Động Nổi Bật'}
        subtitle={language === 'en' ? 'Highest rated water sports and island tours voted by travelers' : 'Bảng xếp hạng các hoạt động thể thao nước và tour biển được du khách đánh giá cao nhất'}
        viewAllLink="/search"
        items={top10Activities}
        renderItem={(item) => (
          <Link
            to={item.link || `/search?category=${item.searchFilter}`}
            className="group flex-1 flex flex-col bg-slate-50 hover:bg-slate-100/90 rounded-2xl overflow-hidden transition-all duration-300 hover:-translate-y-0.5 justify-between border border-slate-200/60 hover:border-slate-300"
          >
            <div className="relative aspect-[16/11] overflow-hidden bg-slate-100">
              <img
                src={item.imageUrl}
                alt={item.name}
                loading="lazy"
                onError={(e) => {
                  e.currentTarget.src = FALLBACK_CARD_IMAGE;
                }}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
              />
            </div>
            <div className="p-3.5 flex-1 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-[13px] sm:text-sm text-slate-900 group-hover:text-sky-700 transition-colors line-clamp-2 leading-snug">
                  {language === 'en' ? item.nameEn : item.name}
                </h3>
                <p className="text-[13px] text-slate-500 mt-1">{item.location}</p>
              </div>
              <div className="mt-3 pt-2 border-t border-slate-200/50 flex items-center justify-between">
                <div className="flex items-center gap-1 text-[13px] font-bold text-slate-800">
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                  <span>{item.rating.toFixed(1)}</span>
                </div>
                <span className="text-[13px] font-bold text-sky-700">{item.price}</span>
              </div>
            </div>
          </Link>
        )}
      />

      {/* 3. Đánh Giá Cao */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Top-Rated' : 'Đánh giá cao'}
        subtitle={language === 'en' ? 'Pristine coastal bays and certified watersport marine hubs in Da Nang' : 'Các vùng vịnh biển có cảnh quan nguyên sinh và hạ tầng bãi biển được du khách chấm điểm cao nhất'}
        viewAllLink="/search"
        items={TOP_RATED_DESTINATIONS}
        renderItem={(dest) => (
          <Link
            to={dest.link}
            className="group flex-1 flex flex-col bg-slate-50 hover:bg-slate-100/90 rounded-2xl overflow-hidden transition-all duration-300 hover:-translate-y-0.5 justify-between border border-slate-200/60 hover:border-slate-300"
          >
            <div className="relative aspect-[16/11] overflow-hidden bg-slate-100">
              <img
                src={dest.imageUrl}
                alt={dest.name}
                loading="lazy"
                onError={(e) => {
                  e.currentTarget.src = FALLBACK_CARD_IMAGE;
                }}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
              />
            </div>
            <div className="p-3.5 flex-1 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-[13px] sm:text-sm text-slate-900 group-hover:text-sky-700 transition-colors line-clamp-1 leading-snug">
                  {language === 'en' ? dest.nameEn : dest.name}
                </h3>
                <p className="text-[13px] text-slate-500 mt-1 line-clamp-1">
                  {language === 'en' ? dest.activityCountEn : dest.activityCount}
                </p>
              </div>
              <div className="mt-3 pt-2 border-t border-slate-200/50 flex items-center justify-between">
                <div className="flex items-center gap-1 text-[13px] font-bold text-slate-800">
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                  <span>{dest.rating.toFixed(2)}</span>
                </div>
                <span className="text-xs font-semibold text-sky-700 truncate max-w-[120px]">
                  {language === 'en' ? dest.highlightEn : dest.highlight}
                </span>
              </div>
            </div>
          </Link>
        )}
      />

      {/* 4. Ghé Thăm Nhiều Nhất */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Most Visited' : 'Ghé thăm nhiều nhất'}
        subtitle={language === 'en' ? 'Popular marine excursions with highest verified booking volumes' : 'Những tour thể thao biển thu hút đông đảo lượng khách trải nghiệm và tỷ lệ hài lòng cao'}
        viewAllLink="/search"
        items={topVisitedList}
        renderItem={(item) => (
          <Link
            to={item.link || `/search?category=${item.searchFilter}`}
            className="group flex-1 flex flex-col bg-slate-50 hover:bg-slate-100/90 rounded-2xl overflow-hidden transition-all duration-300 hover:-translate-y-0.5 justify-between border border-slate-200/60 hover:border-slate-300"
          >
            <div className="relative aspect-[16/11] overflow-hidden bg-slate-100">
              <img
                src={item.imageUrl}
                alt={item.name}
                loading="lazy"
                onError={(e) => {
                  e.currentTarget.src = FALLBACK_CARD_IMAGE;
                }}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
              />
            </div>
            <div className="p-3.5 flex-1 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-[13px] sm:text-sm text-slate-900 group-hover:text-sky-700 transition-colors line-clamp-2 leading-snug">
                  {language === 'en' ? item.nameEn : item.name}
                </h3>
                <p className="text-[13px] text-slate-500 mt-1 line-clamp-1">
                  {item.location} • <span className="text-emerald-700 font-semibold">{language === 'en' ? item.visitorsTextEn : item.visitorsText}</span>
                </p>
              </div>
              <div className="mt-3 pt-2 border-t border-slate-200/50 flex items-center justify-between">
                <div className="flex items-center gap-1 text-[13px] font-bold text-slate-800">
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                  <span>{item.rating.toFixed(1)}</span>
                </div>
                <span className="text-[13px] font-bold text-sky-700">{item.price}</span>
              </div>
            </div>
          </Link>
        )}
      />

      {/* 5. Đề Xuất */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Recommended' : 'Đề xuất'}
        subtitle={language === 'en' ? 'Optimized for current wave height, morning water clarity, and ocean wind speeds' : 'Được tính toán tự động dựa trên độ cao sóng, chu kỳ thủy triều và độ trong của nước hôm nay'}
        viewAllLink="/search"
        items={weatherRecommendedList}
        renderItem={(item) => (
          <Link
            to={item.link || `/search?category=${item.searchFilter}`}
            className="group flex-1 flex flex-col bg-slate-50 hover:bg-slate-100/90 rounded-2xl overflow-hidden transition-all duration-300 hover:-translate-y-0.5 justify-between border border-slate-200/60 hover:border-slate-300"
          >
            <div className="relative aspect-[16/11] overflow-hidden bg-slate-100">
              <img
                src={item.imageUrl}
                alt={item.name}
                loading="lazy"
                onError={(e) => {
                  e.currentTarget.src = FALLBACK_CARD_IMAGE;
                }}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
              />
            </div>
            <div className="p-3.5 flex-1 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-[13px] sm:text-sm text-slate-900 group-hover:text-sky-700 transition-colors line-clamp-2 leading-snug">
                  {language === 'en' ? item.nameEn : item.name}
                </h3>
                <p className="text-[13px] text-sky-700 font-medium mt-1 line-clamp-1">
                  {language === 'en' ? item.conditionReasonEn : item.conditionReason}
                </p>
              </div>
              <div className="mt-3 pt-2 border-t border-slate-200/50 flex items-center justify-between">
                <div className="flex items-center gap-1 text-[13px] font-bold text-slate-800">
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                  <span>{item.rating.toFixed(1)}</span>
                </div>
                <span className="text-[13px] font-bold text-sky-700">{item.price}</span>
              </div>
            </div>
          </Link>
        )}
      />

      {/* 6. Curated Sample Itineraries Section */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-20 animate-fadeInUp">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {language === 'en' ? 'Suggested Itineraries' : 'Lộ trình gợi ý'}
          </h2>
          <p className="text-base text-slate-700 mt-2 font-normal">
            {language === 'en'
              ? 'Optimized by maritime experts matching water tides, morning visibility, and sunset timing.'
              : 'Được thiết kế dựa trên chu kỳ thủy triều và khung giờ nước trong nhất của các vịnh biển Đà Nẵng.'}
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {CURATED_ITINERARIES.map((itin) => {
            const highlights = language === 'en' ? itin.highlightsEn : itin.highlights;

            return (
              <div
                key={itin.id}
                className="bg-slate-50 rounded-2xl overflow-hidden hover:bg-slate-100/90 transition-all flex flex-col justify-between animate-fadeInUp"
              >
                <div>
                  {/* Clean Photo Banner without Slop Badges */}
                  <div className="relative aspect-[16/9] overflow-hidden bg-slate-100">
                    <img
                      src={itin.imageUrl}
                      alt={itin.title}
                      loading="lazy"
                      className="w-full h-full object-cover hover:scale-105 transition-transform duration-500 ease-out"
                    />
                  </div>

                  <div className="p-5">
                    {/* Natural Metadata Row */}
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 mb-2">
                      <Clock className="w-3.5 h-3.5 text-slate-400" />
                      <span>{language === 'en' ? itin.durationEn : itin.duration}</span>
                    </div>

                    <h3 className="text-lg font-bold text-slate-900 leading-snug mb-4">
                      {language === 'en' ? itin.titleEn : itin.title}
                    </h3>

                    {/* Concise Highlight Steps (4 Clean Checkpoints) */}
                    <div className="space-y-2.5 mb-2">
                      {highlights.map((step, sIdx) => (
                        <div key={sIdx} className="flex items-center gap-2.5 text-[13px] text-slate-700">
                          <span className="w-5 h-5 rounded-full bg-white text-sky-700 font-bold text-[13px] flex items-center justify-center shrink-0">
                            {sIdx + 1}
                          </span>
                          <span className="font-medium truncate">{step}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Bottom Cost & CTA */}
                <div className="p-5 pt-0">
                  <div className="pt-4 flex items-center justify-between">
                    <div>
                      <span className="text-[13px] text-slate-500 block font-medium">Chi phí ước tính</span>
                      <span className="text-base font-bold text-sky-700">{itin.estimatedCost}</span>
                    </div>

                    <Link
                      to={`/search?category=${itin.targetCategory}`}
                      className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold transition-colors cursor-pointer"
                    >
                      <span>{language === 'en' ? 'Book Route →' : 'Đặt theo lộ trình →'}</span>
                    </Link>
                  </div>
                </div>

              </div>
            );
          })}
        </div>
      </section>

      {/* 5. Safe & Eco-Guidelines */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-16 animate-fadeInUp">
        <div className="bg-[#031d44] text-white rounded-2xl p-6 sm:p-10 w-full">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            <div className="lg:col-span-5">
              <h3 className="text-2xl font-black text-white mb-3">
                {language === 'en' 
                  ? 'Marine Safety and Coral Conservation Standards' 
                  : 'An toàn hàng hải & Bảo tồn rạn san hô'}
              </h3>

              <p className="text-slate-200 text-base leading-relaxed font-normal">
                DANASEA áp dụng tiêu chuẩn an toàn hàng hải tuyệt đối với 100% áo phao đạt kiểm định SOLAS, hướng dẫn viên đạt chứng chỉ cứu sinh PADI và quy định nghiêm ngặt không giẫm đạp rạn san hô tự nhiên tại bán đảo Sơn Trà.
              </p>
            </div>

            <div className="lg:col-span-7">
              <ul className="space-y-4 text-slate-200 text-base">
                <li className="flex items-start gap-3 bg-white/10 p-3.5 rounded-xl">
                  <span className="font-bold text-cyan-200 shrink-0">Áo phao SOLAS:</span>
                  <span>Bắt buộc mặc áo phao tiêu chuẩn trong suốt quá trình chèo SUP, lướt ván và cano cao tốc.</span>
                </li>
                <li className="flex items-start gap-3 bg-white/10 p-3.5 rounded-xl">
                  <span className="font-bold text-cyan-200 shrink-0">Bảo tồn san hô:</span>
                  <span>Giữ khoảng cách an toàn với rạn san hô bãi cạn và sử dụng kem chống nắng an toàn đại dương.</span>
                </li>
                <li className="flex items-start gap-3 bg-white/10 p-3.5 rounded-xl">
                  <span className="font-bold text-cyan-200 shrink-0">Huấn luyện viên:</span>
                  <span>Các tour lặn biển sâu Scuba luôn có thợ lặn chuyên nghiệp kèm sát từng du khách.</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

    </main>
  );
}

export default Explore;
