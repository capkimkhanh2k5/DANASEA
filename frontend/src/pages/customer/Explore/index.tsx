import { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
  getStageCardImageUrl,
  FALLBACK_HERO_IMAGE,
  FALLBACK_CARD_IMAGE,
} from '../../../utils/imageOptimizer';
import { Star, ArrowRight, ChevronLeft, ChevronRight, MapPin, Clock } from 'lucide-react';
import { SEOHead } from '../../../components/common/SEOHead';
import { getTourUrl } from '../../../utils/seo';

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
    searchFilter: 'lan-ngam-san-ho',
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
    searchFilter: 'cheo-sup-kayak',
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
    searchFilter: 'cano-du-bay',
  },
  {
    id: 'hero-4',
    name: 'Mô Tô Nước Jetski Yamaha 150HP',
    nameEn: 'Yamaha 150HP High-Speed Wave Rider',
    shortDesc: 'Chinh phục từng ngọn sóng biển với mô tô nước thế hệ mới và phao cứu hộ theo sát.',
    shortDescEn: 'Command the waves with modern high-speed jet skis and maritime safety escort.',
    rating: 4.8,
    reviewCount: 64,
    location: 'Bãi biển Non Nước',
    locationEn: 'Non Nuoc Beach',
    category: 'Mô tô nước',
    price: '550.000 ₫',
    imageUrl: '/images/hero/04-mo-to-nuoc-jetski-bien-non-nuoc.jpg',
    searchFilter: 'jetski-mo-to-nuoc',
  },
];

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
      'Cập bến Bãi Bụt và tắm tráng nước ngọt',
    ],
    highlightsEn: [
      'Sunrise SUP paddleboarding at My Khe Beach',
      'Coastal seafood breakfast & refreshments',
      'Speedboat to Hon Sup for coral snorkeling',
      'Dock at Bai But with freshwater rinse',
    ],
    estimatedCost: '750.000 ₫/khách',
    targetCategory: 'lan-ngam-san-ho',
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
      'Lướt ván cano Wakeboarding tại Bãi Rạng',
    ],
    highlightsEn: [
      'Tandem parasailing above Da Nang Bay',
      'High-speed Yamaha Jet Ski wave riding',
      'Fresh local coastal seafood lunch',
      'Wakeboarding session in calm Bai Rang waters',
    ],
    estimatedCost: '1.450.000 ₫/khách',
    targetCategory: 'cano-du-bay',
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
      'Thưởng thức hải sản tươi nướng trên tàu',
    ],
    highlightsEn: [
      'Gentle sunset paddleboarding on calm waters',
      'Board catamaran for golden-hour sunset tea',
      'Night squid angling experience with fishermen',
      'Fresh catch prepared and served on board',
    ],
    estimatedCost: '1.200.000 ₫/khách',
    targetCategory: 'du-thuyen-hoang-hon',
  },
];

// Priority IDs of high-resolution, landscape-ratio (4:3 / 3:2) marine activities
const HIGH_RES_STAGE_SERVICE_IDS = [
  '5943ecd7-6480-5b4a-b42b-43abc0988776', // Học lướt sóng – Surf Shack (2560x1920, 2K 4:3)
  '1dd26db5-f863-5b03-b562-ac46f43df9d2', // Lớp học lướt sóng Mỹ Khê – DNOA (1920x1436, Full HD 4:3)
  '3758c297-54ff-5887-89bd-62220227570b', // Dù bay trên biển – Danabeach (1375x867, 16:10)
  'a6f4ab9c-4794-542b-a443-e3013e6bbe47', // Câu cá biển Sơn Trà – DaNaFi (1280x960, 4:3)
  '9f4eb04d-9ec6-5295-8de6-475b728e5cfc', // Surf tour tìm điểm sóng – Surf Shack (1235x824, 3:2)
  '2cdfabcf-13a2-550a-8f9e-283aad7053c9', // Mô tô nước – Kajin Vina (1200x800, 3:2)
  '5536f743-3b6d-518e-af2b-05634a218195', // Câu cá chuyên nghiệp Sơn Trà – DaNaFi (1200x900, 4:3)
  'c4c56ae2-5f9b-555c-81f1-f0d4c8dd7c1b', // Dù bay trên biển – DNOA (1170x879, 4:3)
  '088d874c-a61e-5b58-a7a9-497f32a56901', // Mô tô nước – Danabeach (910x607, 3:2)
  '4190046d-112a-5b93-beb2-bcc8b1cd16d4', // Dù bay trên biển – Kajin Vina (900x600, 3:2)
];

export function Explore() {
  const { language } = useLanguageStore();
  const [activeHeroIndex, setActiveHeroIndex] = useState(0);
  const [isHeroPaused, setIsHeroPaused] = useState(false);
  const [liveServices, setLiveServices] = useState<ServiceSummaryDto[]>([]);
  const navigate = useNavigate();
  const [carouselIndex, setCarouselIndex] = useState(0);
  const [isCarouselPaused, setIsCarouselPaused] = useState(false);
  const [touchStartX, setTouchStartX] = useState<number | null>(null);
  const [isMobile, setIsMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth < 640 : false));

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 640);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    let isMounted = true;
    serviceCatalogService
      .getServices({ size: 50 })
      .then((res) => {
        if (isMounted && res.content && res.content.length > 0) {
          setLiveServices(res.content);
        }
      })
      .catch((err) => {
        console.warn('[Explore] Không thể nạp live services từ BE:', err);
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
    if (rating === undefined || rating === null || isNaN(Number(rating))) return fallback;
    return Number(rating);
  };

  const heroSpots = useMemo(() => {
    return HERO_FEATURED_SPOTS.map((spot) => {
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
        targetServiceId: matchedService ? matchedService.id : spot.id,
        imageUrl: spot.imageUrl,
        price: matchedService ? formatPrice(matchedService.price, spot.price) : spot.price,
        rating: matchedService ? formatRating(matchedService.averageRating, spot.rating) : spot.rating,
        reviewCount: matchedService?.reviewCount || spot.reviewCount,
        link: matchedService ? getTourUrl(matchedService) : `/search?category=${spot.searchFilter}`,
      };
    });
  }, [liveServices]);

  const allExperiences = useMemo(() => {
    if (!liveServices || liveServices.length === 0) {
      return HERO_FEATURED_SPOTS.map((s) => ({
        id: s.id,
        name: s.name,
        nameEn: s.nameEn,
        location: s.location,
        rating: s.rating,
        reviewCount: s.reviewCount,
        price: s.price,
        imageUrl: s.imageUrl,
        link: '/search',
      }));
    }

    // Curate sharpest high-resolution activities with consistent landscape 4:3 / 3:2 ratio
    const featuredServices = [...liveServices].sort((a, b) => {
      const aRank = HIGH_RES_STAGE_SERVICE_IDS.indexOf(a.id);
      const bRank = HIGH_RES_STAGE_SERVICE_IDS.indexOf(b.id);
      if (aRank !== -1 && bRank !== -1) return aRank - bRank;
      if (aRank !== -1) return -1;
      if (bRank !== -1) return 1;
      return 0;
    }).slice(0, 10);

    return featuredServices.map((svc) => ({
      id: svc.id,
      name: svc.name,
      nameEn: svc.name,
      location: svc.address || 'Đà Nẵng',
      rating: formatRating(svc.averageRating, 4.9),
      reviewCount: svc.reviewCount || 24,
      price: formatPrice(svc.price, '400.000 ₫'),
      imageUrl: getStageCardImageUrl(svc.primaryImageUrl || FALLBACK_HERO_IMAGE),
      link: getTourUrl(svc),
    }));
  }, [liveServices]);

  // Derived curated lists for section decks
  const topVisitedList = useMemo(() => {
    if (liveServices.length === 0) return TOP_VISITED_EXPERIENCES;
    return [...liveServices]
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
          link: getTourUrl(s),
        };
      });
  }, [liveServices]);

  const weatherRecommendedList = useMemo(() => {
    if (liveServices.length === 0) return WEATHER_RECOMMENDED_EXPERIENCES;
    const filtered = liveServices.filter((s) => {
      const n = s.name.toLowerCase();
      return n.includes('san hô') || n.includes('sup') || n.includes('kayak') || n.includes('lặn') || n.includes('biển');
    });
    const pool = filtered.length > 0 ? filtered : liveServices;
    return pool.slice(0, 8).map((s, idx) => {
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
        link: getTourUrl(s),
      };
    });
  }, [liveServices]);

  // Auto-advance hero carousel
  useEffect(() => {
    if (isHeroPaused || heroSpots.length === 0) return;
    const timer = setInterval(() => {
      setActiveHeroIndex((prev) => (prev + 1) % heroSpots.length);
    }, 5500);
    return () => clearInterval(timer);
  }, [isHeroPaused, heroSpots.length]);

  const currentHero = heroSpots[activeHeroIndex] || heroSpots[0];

  // Auto-advance coverflow carousel
  useEffect(() => {
    if (isCarouselPaused || allExperiences.length === 0) return;
    const timer = setInterval(() => {
      setCarouselIndex((prev) => (prev + 1) % allExperiences.length);
    }, 4500);
    return () => clearInterval(timer);
  }, [isCarouselPaused, allExperiences.length]);

  const handlePrevExperience = () => {
    if (allExperiences.length === 0) return;
    setCarouselIndex((prev) => (prev - 1 + allExperiences.length) % allExperiences.length);
  };

  const handleNextExperience = () => {
    if (allExperiences.length === 0) return;
    setCarouselIndex((prev) => (prev + 1) % allExperiences.length);
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchStartX(e.touches[0].clientX);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX === null) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX;
    if (deltaX > 45) {
      handlePrevExperience();
    } else if (deltaX < -45) {
      handleNextExperience();
    }
    setTouchStartX(null);
  };

  const getOffset = (index: number, current: number, total: number) => {
    if (total === 0) return 0;
    let diff = index - current;
    while (diff > total / 2) diff -= total;
    while (diff < -total / 2) diff += total;
    return diff;
  };

  return (
    <main className="w-full min-h-screen bg-white text-slate-900 pb-20">
      <SEOHead
        title={
          language === 'en'
            ? 'Explore Marine Experiences & Coastal Watersports | DANASEA'
            : 'Khám Phá Trải Nghiệm Biển & Thể Thao Nước Tuyển Chọn | DANASEA'
        }
        description={
          language === 'en'
            ? 'Discover certified coral reef snorkeling, sunrise SUP, parasailing and high-speed water sports across Da Nang coastal bays.'
            : 'Khám phá tour lặn ngắm san hô Sơn Trà, chèo SUP đón bình minh Mỹ Khê, dù bay cano và trải nghiệm thể thao nước tiêu chuẩn quốc tế tại Đà Nẵng.'
        }
        canonicalUrl="/explore"
        ogType="website"
      />

      {/* 1. Cinematic Split Hero Carousel */}
      <section
        className="relative w-full overflow-hidden min-h-[560px] sm:min-h-[620px] lg:min-h-[680px] max-h-[740px] flex flex-col justify-end mb-14 select-none"
        onMouseEnter={() => setIsHeroPaused(true)}
        onMouseLeave={() => setIsHeroPaused(false)}
      >
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
                  loading={idx === 0 ? 'eager' : 'lazy'}
                  decoding="async"
                  onError={(e) => {
                    e.currentTarget.src = FALLBACK_HERO_IMAGE;
                  }}
                  className={`w-full h-full object-cover object-center transition-transform duration-[7000ms] ease-out ${
                    isCurrent ? 'scale-[1.02]' : 'scale-100'
                  }`}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/95 via-slate-950/50 to-slate-950/30" />
              </div>
            );
          })}
        </div>

        {/* Hero Content */}
        <div className="relative z-10 max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-8 grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-10 items-end">
          {/* Left Column: Title & Metadata */}
          <div className="lg:col-span-5 flex flex-col justify-end text-white pb-2 lg:pb-3 animate-fadeInUp">
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-black text-white tracking-tight leading-tight mb-3 drop-shadow-[0_2px_12px_rgba(0,0,0,0.9)]">
              {language === 'en' ? currentHero.nameEn : currentHero.name}
            </h1>

            <p className="text-sm sm:text-base text-slate-200 leading-relaxed font-medium mb-3 max-w-xl line-clamp-2 drop-shadow-[0_2px_8px_rgba(0,0,0,0.9)]">
              {language === 'en' ? currentHero.shortDescEn : currentHero.shortDesc}
            </p>

            <div className="flex items-center gap-2.5 mb-6 flex-wrap drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
              <div className="flex items-center gap-1">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className="w-4 h-4 fill-amber-400 text-amber-400 shrink-0" />
                ))}
              </div>
              <span className="font-extrabold text-white text-[13px]">{currentHero.rating.toFixed(1)}</span>
              <span className="text-white/90 text-[13px]">({currentHero.reviewCount} {language === 'en' ? 'reviews' : 'đánh giá'})</span>
              <span className="text-white/70 text-[13px]">•</span>
              <span className="text-sky-300 text-[13px] font-semibold">{language === 'en' ? currentHero.locationEn : currentHero.location}</span>
              <span className="text-white/70 text-[13px]">•</span>
              <span className="text-amber-300 font-extrabold text-[14px]">{currentHero.price}</span>
            </div>

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

          {/* Right Column: 4-Card Horizontal Row */}
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
                    className={`rounded-2xl p-3 sm:p-3.5 transition-all duration-300 ease-out cursor-pointer flex flex-col justify-between backdrop-blur-md ${
                      isActive
                        ? 'scale-105 z-10 bg-black/60 border border-sky-400/90 shadow-2xl opacity-100 -translate-y-1'
                        : 'scale-100 opacity-75 hover:opacity-100 hover:scale-[1.02] bg-black/35 hover:bg-black/50 border border-white/15 hover:border-white/30 shadow-md'
                    }`}
                  >
                    <div>
                      <div className="relative w-full h-24 sm:h-28 md:h-32 rounded-xl overflow-hidden mb-2.5 bg-white/5">
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
                      <h4 className="text-xs sm:text-sm font-bold text-white line-clamp-2 leading-snug">
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

      {/* 2. Single-Row 3D Stage Coverflow Carousel */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-20 select-none">
        {/* Section Header */}
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4 pb-4 border-b border-slate-200/80">
          <div>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
              {language === 'en' ? 'Featured Marine Catalog' : 'Danh Mục Trải Nghiệm Biển'}
            </h2>
            <p className="text-sm sm:text-base text-slate-600 mt-1 font-medium">
              {language === 'en'
                ? 'Certified ocean adventures, coral diving, and watersports in Da Nang'
                : 'Trải nghiệm thể thao nước và tour khám phá vịnh biển đặc sắc'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs text-slate-500 font-mono font-semibold">
              {String(carouselIndex + 1).padStart(2, '0')} / {String(allExperiences.length).padStart(2, '0')}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={handlePrevExperience}
                aria-label="Xem hoạt động trước"
                className="w-9 h-9 rounded-xl border border-slate-200/90 bg-white hover:bg-slate-50 text-slate-700 flex items-center justify-center transition-colors shadow-2xs hover:scale-105 active:scale-95 cursor-pointer"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={handleNextExperience}
                aria-label="Xem hoạt động tiếp theo"
                className="w-9 h-9 rounded-xl border border-slate-200/90 bg-white hover:bg-slate-50 text-slate-700 flex items-center justify-center transition-colors shadow-2xs hover:scale-105 active:scale-95 cursor-pointer"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* 3D Coverflow Stage with 50% Overlap & Edge-Gradient Fade */}
        <div
          className="relative w-full h-[390px] sm:h-[450px] md:h-[480px] lg:h-[510px] overflow-hidden flex items-center justify-center"
          onMouseEnter={() => setIsCarouselPaused(true)}
          onMouseLeave={() => setIsCarouselPaused(false)}
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
        >
          {/* Extreme Left Navigation Button */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              handlePrevExperience();
            }}
            aria-label="Trải nghiệm trước"
            className="absolute left-2 sm:left-4 lg:left-6 top-1/2 -translate-y-1/2 z-30 w-11 h-11 sm:w-12 sm:h-12 rounded-2xl bg-white/95 hover:bg-white text-slate-900 border border-slate-200/90 shadow-[0_8px_24px_rgba(15,23,42,0.18)] flex items-center justify-center transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer backdrop-blur-md group"
          >
            <ChevronLeft className="w-5 h-5 text-slate-800 group-hover:text-sky-700 transition-colors" />
          </button>

          {/* Extreme Right Navigation Button */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              handleNextExperience();
            }}
            aria-label="Trải nghiệm tiếp theo"
            className="absolute right-2 sm:right-4 lg:right-6 top-1/2 -translate-y-1/2 z-30 w-11 h-11 sm:w-12 sm:h-12 rounded-2xl bg-white/95 hover:bg-white text-slate-900 border border-slate-200/90 shadow-[0_8px_24px_rgba(15,23,42,0.18)] flex items-center justify-center transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer backdrop-blur-md group"
          >
            <ChevronRight className="w-5 h-5 text-slate-800 group-hover:text-sky-700 transition-colors" />
          </button>

          {/* Cards Stage */}
          {allExperiences.map((item, index) => {
            const diff = getOffset(index, carouselIndex, allExperiences.length);
            const isCenter = diff === 0;
            const isLeft = diff === -1;
            const isRight = diff === 1;

            let transform: string;
            let zIndex: number;
            let opacity: number;
            let pointerEvents: 'auto' | 'none' = 'none';
            let boxShadow = 'none';
            let maskImage = 'none';
            let webkitMaskImage = 'none';

            if (isCenter) {
              transform = 'translate(-50%, -50%) scale(1)';
              zIndex = 20;
              opacity = 1;
              pointerEvents = 'auto';
              boxShadow = '0 25px 50px -12px rgba(15, 23, 42, 0.45), 0 0 0 1px rgba(255, 255, 255, 0.15)';
              maskImage = 'none';
              webkitMaskImage = 'none';
            } else if (isLeft) {
              // Center card covers ~50% of the left card width
              transform = isMobile
                ? 'translate(-86%, -50%) scale(0.88)'
                : 'translate(-96%, -50%) scale(0.88)';
              zIndex = 10;
              opacity = 1;
              pointerEvents = 'auto';
              boxShadow = '0 10px 30px -10px rgba(15, 23, 42, 0.25)';
              // Gradual fade: sharp near center card edge, fading towards the outer left edge
              // Gentle fade: 100% clarity near center card, softly easing to 72% at the far outer edge
              maskImage = 'linear-gradient(to left, rgba(0,0,0,1) 0%, rgba(0,0,0,0.96) 45%, rgba(0,0,0,0.72) 100%)';
              webkitMaskImage = 'linear-gradient(to left, rgba(0,0,0,1) 0%, rgba(0,0,0,0.96) 45%, rgba(0,0,0,0.72) 100%)';
            } else if (isRight) {
              // Center card covers ~50% of the right card width
              transform = isMobile
                ? 'translate(-14%, -50%) scale(0.88)'
                : 'translate(-4%, -50%) scale(0.88)';
              zIndex = 10;
              opacity = 1;
              pointerEvents = 'auto';
              boxShadow = '0 10px 30px -10px rgba(15, 23, 42, 0.25)';
              // Gradual fade: sharp near center card edge, fading towards the outer right edge
              // Gentle fade: 100% clarity near center card, softly easing to 72% at the far outer edge
              maskImage = 'linear-gradient(to right, rgba(0,0,0,1) 0%, rgba(0,0,0,0.96) 45%, rgba(0,0,0,0.72) 100%)';
              webkitMaskImage = 'linear-gradient(to right, rgba(0,0,0,1) 0%, rgba(0,0,0,0.96) 45%, rgba(0,0,0,0.72) 100%)';
            } else if (diff < -1) {
              transform = isMobile
                ? 'translate(-140%, -50%) scale(0.7)'
                : 'translate(-160%, -50%) scale(0.72)';
              zIndex = 0;
              opacity = 0;
            } else {
              transform = isMobile
                ? 'translate(40%, -50%) scale(0.7)'
                : 'translate(60%, -50%) scale(0.72)';
              zIndex = 0;
              opacity = 0;
            }

            return (
              <div
                key={item.id}
                onClick={(e) => {
                  if (diff === -1) {
                    e.preventDefault();
                    handlePrevExperience();
                  } else if (diff === 1) {
                    e.preventDefault();
                    handleNextExperience();
                  } else if (diff === 0) {
                    const target = e.target as HTMLElement | null;
                    if (target && !target.closest('a')) {
                      navigate(item.link);
                    }
                  }
                }}
                style={{
                  transform,
                  zIndex,
                  opacity,
                  pointerEvents,
                  boxShadow,
                  maskImage,
                  WebkitMaskImage: webkitMaskImage,
                }}
                className={`absolute top-1/2 left-1/2 w-[86%] sm:w-[56%] md:w-[50%] lg:w-[46%] max-w-[580px] aspect-[4/3] rounded-3xl overflow-hidden transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] will-change-transform group ${
                  isCenter ? 'ring-1 ring-white/20' : 'cursor-pointer hover:opacity-100'
                }`}
              >
                {/* Full-bleed Background Image */}
                <img
                  src={item.imageUrl}
                  alt={item.name}
                  loading={isCenter ? 'eager' : 'lazy'}
                  onError={(e) => {
                    e.currentTarget.src = FALLBACK_CARD_IMAGE;
                  }}
                  className="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
                />

                {/* Gradient Overlays for High-Contrast Text Legibility */}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/95 via-slate-950/40 to-transparent" />
                <div className="absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-black/50 to-transparent pointer-events-none" />

                

                {/* Top Rating Pill */}
                <div className="absolute top-4 left-4 sm:top-5 sm:left-5 px-3 py-1.5 rounded-xl bg-slate-950/60 backdrop-blur-md border border-white/20 flex items-center gap-1.5 text-white text-xs font-bold shadow-sm">
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                  <span className="font-mono">{item.rating.toFixed(1)}</span>
                  <span className="text-white/60 font-normal">({item.reviewCount})</span>
                </div>

                {/* Top Right Tag */}
                <div className="absolute top-4 right-4 sm:top-5 sm:right-5 px-3 py-1.5 rounded-xl bg-sky-950/60 backdrop-blur-md border border-sky-400/30 text-sky-200 text-[11px] font-semibold tracking-wide uppercase">
                  Đà Nẵng
                </div>

                {/* Embedded Bottom Information Box */}
                <div className="absolute inset-x-0 bottom-0 p-4 sm:p-6 z-10 text-white flex flex-col justify-end">
                  <div className="flex items-center gap-1.5 text-xs text-sky-300 mb-1.5 font-medium">
                    <MapPin className="w-3.5 h-3.5 shrink-0" />
                    <span className="truncate">{item.location}</span>
                  </div>

                  <h3 className="text-base sm:text-xl md:text-2xl font-black text-white leading-snug drop-shadow-sm mb-2 sm:mb-2.5 group-hover:text-sky-200 transition-colors line-clamp-1 sm:line-clamp-2">
                    {language === 'en' ? item.nameEn : item.name}
                  </h3>

                  <div className="flex items-center justify-between pt-3 border-t border-white/15">
                    <div>
                      <span className="text-[11px] uppercase tracking-wider text-white/70 block font-semibold">
                        {language === 'en' ? 'Starting From' : 'Chi phí từ'}
                      </span>
                      <span className="text-lg sm:text-2xl font-black text-amber-300 font-mono tabular-nums drop-shadow-sm">
                        {item.price}
                      </span>
                    </div>

                    {isCenter ? (
                      <Link
                        to={item.link}
                        onClick={(e) => e.stopPropagation()}
                        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white text-xs sm:text-sm font-bold shadow-lg shadow-sky-600/40 hover:scale-105 active:scale-95 transition-all cursor-pointer"
                      >
                        <span>{language === 'en' ? 'Explore Now' : 'Khám phá ngay'}</span>
                        <ArrowRight className="w-4 h-4 shrink-0" />
                      </Link>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-white/20 text-white/90 text-xs font-semibold backdrop-blur-sm">
                        {language === 'en' ? 'Select' : 'Xem nhanh'}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Indicator Dots Below Stage */}
        <div className="flex items-center justify-center gap-2 mt-6 select-none">
          {allExperiences.map((_, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => setCarouselIndex(idx)}
              aria-label={`Chuyển đến hoạt động ${idx + 1}`}
              className={`h-2 transition-all duration-300 rounded-full cursor-pointer ${
                idx === carouselIndex
                  ? 'w-8 bg-[#0284C7]'
                  : 'w-2 bg-slate-300 hover:bg-slate-400'
              }`}
            />
          ))}
        </div>
      </section>

      {/* 3. Đánh Giá Cao (Top-Rated Destinations) */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Top-Rated Coastal Bays' : 'Vùng Vịnh Đánh Giá Cao'}
        subtitle={
          language === 'en'
            ? 'Pristine coastal bays and certified watersport marine hubs in Da Nang'
            : 'Các vùng vịnh biển có cảnh quan nguyên sinh và hạ tầng bãi biển được du khách chấm điểm cao nhất'
        }
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

      {/* 4. Ghé Thăm Nhiều Nhất (Most Visited) */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Most Visited Tours' : 'Hoạt Động Ghé Thăm Nhiều Nhất'}
        subtitle={
          language === 'en'
            ? 'Popular marine excursions with highest verified booking volumes'
            : 'Những tour thể thao biển thu hút đông đảo lượng khách trải nghiệm và tỷ lệ hài lòng cao'
        }
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

      {/* 5. Đề Xuất Theo Điều Kiện Sóng Biển (Weather & Marine Recommended) */}
      <ExploreCarouselDeck
        title={language === 'en' ? 'Weather Recommended' : 'Đề Xuất Theo Thời Tiết Biển'}
        subtitle={
          language === 'en'
            ? 'Optimized for current wave height, morning water clarity, and ocean wind speeds'
            : 'Được tính toán tự động dựa trên độ cao sóng, chu kỳ thủy triều và độ trong của nước hôm nay'
        }
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
            {language === 'en' ? 'Suggested Itineraries' : 'Lộ Trình Gợi Ý Tuyển Chọn'}
          </h2>
          <p className="text-sm sm:text-base text-slate-600 mt-2 font-medium">
            {language === 'en'
              ? 'Complete multi-experience journeys curated by local captains and certified marine instructors'
              : 'Gợi ý lịch trình kết hợp trải nghiệm biển trọn gói từ sáng đến hoàng hôn, tối ưu thời gian và chi phí'}
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 sm:gap-8">
          {CURATED_ITINERARIES.map((itin) => {
            const highlights = language === 'en' ? itin.highlightsEn : itin.highlights;
            return (
              <div
                key={itin.id}
                className="flex flex-col justify-between bg-slate-50 hover:bg-slate-100/90 rounded-2xl overflow-hidden border border-slate-200/80 shadow-xs hover:shadow-md transition-all duration-300 group"
              >
                <div>
                  <div className="relative aspect-[16/9] overflow-hidden bg-slate-100">
                    <img
                      src={itin.imageUrl}
                      alt={itin.title}
                      loading="lazy"
                      onError={(e) => {
                        e.currentTarget.src = FALLBACK_CARD_IMAGE;
                      }}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                    />
                  </div>

                  <div className="p-5">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-500 mb-2">
                      <Clock className="w-3.5 h-3.5 text-slate-400" />
                      <span>{language === 'en' ? itin.durationEn : itin.duration}</span>
                    </div>

                    <h3 className="text-lg font-bold text-slate-900 leading-snug mb-4">
                      {language === 'en' ? itin.titleEn : itin.title}
                    </h3>

                    <div className="space-y-2.5 mb-2">
                      {highlights.map((step, sIdx) => (
                        <div key={sIdx} className="flex items-center gap-2.5 text-[13px] text-slate-700">
                          <span className="w-5 h-5 rounded-full bg-white text-sky-700 font-bold text-[13px] flex items-center justify-center shrink-0 border border-slate-200">
                            {sIdx + 1}
                          </span>
                          <span className="font-medium truncate">{step}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="p-5 pt-0">
                  <div className="pt-4 border-t border-slate-200/60 flex items-center justify-between">
                    <div>
                      <span className="text-[12px] text-slate-500 block font-medium">Chi phí ước tính</span>
                      <span className="text-base font-bold text-sky-700 font-mono tabular-nums">{itin.estimatedCost}</span>
                    </div>

                    <Link
                      to={`/search?category=${itin.targetCategory}`}
                      className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs sm:text-sm font-semibold transition-colors cursor-pointer"
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
    </main>
  );
}

export default Explore;
