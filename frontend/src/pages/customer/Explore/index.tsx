import { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  TOP_VISITED_EXPERIENCES,
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
import { Star, ArrowRight, ChevronLeft, ChevronRight, MapPin } from 'lucide-react';
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

interface ExploreExperienceCard {
  id: string;
  name: string;
  nameEn: string;
  location: string;
  rating: number;
  reviewCount: number;
  price: string;
  imageUrl: string;
  link: string;
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
    reviewCount: 64,
    location: 'Bãi biển Non Nước',
    locationEn: 'Non Nuoc Beach',
    category: 'Mô tô nước',
    price: '550.000 ₫',
    imageUrl: '/images/hero/04-jetski-toc-do-non-nuoc.jpg',
    searchFilter: 'jetski-mo-to-nuoc'
  }
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
    const num = Number(rating);
    return num > 0 ? num : fallback;
  };

  // Live Hero Spots
  const heroSpots: HeroFeaturedSpot[] = useMemo(() => {
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
        id: matchedService ? matchedService.id : spot.id,
        name: spot.name,
        nameEn: spot.nameEn,
        price: matchedService ? formatPrice(matchedService.price, spot.price) : spot.price,
        rating: matchedService ? formatRating(matchedService.averageRating, spot.rating) : spot.rating,
        reviewCount: matchedService?.reviewCount || spot.reviewCount,
        link: matchedService ? getTourUrl(matchedService) : `/search?category=${spot.searchFilter}`,
      };
    });
  }, [liveServices]);
  useEffect(() => {
    if (isHeroPaused) return;
    const timer = setInterval(() => {
      setActiveHeroIndex((prev) => (prev + 1) % heroSpots.length);
    }, 4000);
    return () => clearInterval(timer);
  }, [isHeroPaused, heroSpots.length]);

  const currentHero = heroSpots[activeHeroIndex] || heroSpots[0];

  // Consolidated 10 experiences per page
  const allExperiences: ExploreExperienceCard[] = useMemo(() => {
    if (liveServices.length > 0) {
      return liveServices.map((s, idx) => {
        const fallback = TOP_VISITED_EXPERIENCES[idx % TOP_VISITED_EXPERIENCES.length];
        return {
          id: s.id,
          name: s.name,
          nameEn: s.name,
          location: s.address || fallback?.location || 'Đà Nẵng',
          rating: formatRating(s.averageRating, fallback?.rating || 4.9),
          reviewCount: s.reviewCount || 48,
          price: formatPrice(s.price, fallback?.price || '350.000 ₫'),
          imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallback?.imageUrl || FALLBACK_CARD_IMAGE),
          link: getTourUrl(s),
        };
      });
    }
    return TOP_VISITED_EXPERIENCES.map((e, idx) => ({
      id: `fallback-${idx}`,
      name: e.name,
      nameEn: e.nameEn,
      location: e.location,
      rating: e.rating,
      reviewCount: 52,
      price: e.price,
      imageUrl: getCardThumbnailUrl(e.imageUrl),
      link: e.link || `/search?category=${e.searchFilter}`,
    }));
  }, [liveServices]);

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
        title={language === 'en' ? 'Explore Ocean Adventures in Da Nang' : 'Khám Phá Các Hoạt Động Biển Đà Nẵng'}
        description={
          language === 'en'
            ? 'Discover top-rated coastal destinations, snorkeling tours, paddleboarding, and water sports in Da Nang. Plan your seaside getaway with DANASEA.'
            : 'Khám phá các điểm đến biển hàng đầu, tour lặn ngắm san hô, chèo SUP, kayak và các hoạt động thể thao biển hấp dẫn tại Đà Nẵng cùng DANASEA.'
        }
        canonicalUrl="/explore"
      />
      
      {/* 1. Cinematic Split Hero Carousel */}
      <section
        className="relative w-full overflow-hidden min-h-[560px] sm:min-h-[620px] lg:min-h-[680px] max-h-[740px] flex flex-col justify-end mb-14 select-none"
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
                  loading={idx === 0 ? "eager" : "lazy"}
                  decoding="async"
                  onError={(e) => {
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

        {/* Hero Content */}
        <div className="relative z-10 max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-6 grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-10 items-end">
          <div className="lg:col-span-5 flex flex-col justify-end text-white pb-2 lg:pb-3 animate-fadeInUp">
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-black text-white tracking-tight leading-tight mb-3 drop-shadow-[0_2px_12px_rgba(0,0,0,0.9)]">
              {language === 'en' ? currentHero.nameEn : currentHero.name}
            </h1>

            <p className="text-sm sm:text-base text-white leading-relaxed font-medium mb-3 max-w-xl line-clamp-1 drop-shadow-[0_2px_8px_rgba(0,0,0,0.9)]">
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
              <span className="text-sky-300 text-[13px] font-semibold">{currentHero.location}</span>
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

      {/* 2. Single-Row 3D Stage Coverflow Carousel */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-20 select-none">
        {/* Section Header */}
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4 pb-4 border-b border-slate-200/80">
          <div>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
              {language === 'en' ? 'Curated Marine Experiences' : 'Danh Mục Trải Nghiệm Biển'}
            </h2>
            <p className="text-sm sm:text-base text-slate-600 mt-1 font-medium">
              {language === 'en'
                ? 'Certified ocean adventures, coral diving, and watersports in Da Nang'
                : 'Trải nghiệm thể thao nước và tour khám phá vịnh biển đặc sắc'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs text-slate-500 font-mono font-semibold">
              {String(carouselIndex + 1).padStart(2, '0')} / {String(allExperiences.length).padStart(2, '0')} ({allExperiences.length} {language === 'en' ? 'activities' : 'hoạt động'})
            </span>
          </div>
        </div>

        {/* 3D Stage Carousel (Only 1 row, 3 visible cards, center largest, side cards behind & dimmed & partially overlapped) */}
        <div
          className="relative w-full h-[490px] sm:h-[550px] lg:h-[580px] overflow-hidden"
          onMouseEnter={() => setIsCarouselPaused(true)}
          onMouseLeave={() => setIsCarouselPaused(false)}
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
        >
          {/* Navigation Button: Previous (Left end) */}
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

          {/* Navigation Button: Next (Right end) */}
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

            if (isCenter) {
              transform = 'translate(-50%, -50%) scale(1)';
              zIndex = 20;
              opacity = 1;
              pointerEvents = 'auto';
              boxShadow = '0 25px 50px -12px rgba(15, 23, 42, 0.45), 0 0 0 1px rgba(255, 255, 255, 0.15)';
            } else if (isLeft) {
              transform = isMobile
                ? 'translate(-108%, -50%) scale(0.86)'
                : 'translate(-126%, -50%) scale(0.88)';
              zIndex = 10;
              opacity = 0.55;
              pointerEvents = 'auto';
              boxShadow = '0 10px 30px -10px rgba(15, 23, 42, 0.25)';
            } else if (isRight) {
              transform = isMobile
                ? 'translate(8%, -50%) scale(0.86)'
                : 'translate(26%, -50%) scale(0.88)';
              zIndex = 10;
              opacity = 0.55;
              pointerEvents = 'auto';
              boxShadow = '0 10px 30px -10px rgba(15, 23, 42, 0.25)';
            } else if (diff < -1) {
              transform = isMobile
                ? 'translate(-160%, -50%) scale(0.7)'
                : 'translate(-190%, -50%) scale(0.72)';
              zIndex = 0;
              opacity = 0;
            } else {
              transform = isMobile
                ? 'translate(60%, -50%) scale(0.7)'
                : 'translate(90%, -50%) scale(0.72)';
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
                }}
                className={`absolute top-1/2 left-1/2 w-[84%] sm:w-[50%] md:w-[46%] lg:w-[42%] max-w-[540px] h-[450px] sm:h-[510px] lg:h-[530px] rounded-3xl overflow-hidden transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] will-change-transform group ${
                  isCenter ? 'ring-1 ring-white/20' : 'cursor-pointer hover:opacity-80'
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

                {/* Content Contained Within the Image */}
                <div className="absolute bottom-0 inset-x-0 p-5 sm:p-7 flex flex-col justify-end text-left text-white select-none">
                  {/* Location */}
                  <div className="flex items-center gap-1.5 text-xs text-sky-300 font-medium mb-1.5">
                    <MapPin className="w-3.5 h-3.5 shrink-0 text-sky-400" />
                    <span className="truncate">{item.location}</span>
                  </div>

                  {/* Name */}
                  <h3 className="text-lg sm:text-2xl font-extrabold text-white line-clamp-2 leading-snug drop-shadow-sm group-hover:text-sky-200 transition-colors">
                    {language === 'en' ? item.nameEn : item.name}
                  </h3>

                  {/* Price & CTA */}
                  <div className="mt-3.5 pt-3.5 border-t border-white/15 flex items-center justify-between">
                    <div className="flex flex-col">
                      <span className="text-[11px] text-white/70 uppercase tracking-wider font-semibold">
                        {language === 'en' ? 'Starting From' : 'Chi phí từ'}
                      </span>
                      <span className="text-lg sm:text-2xl font-black text-amber-400 font-mono tabular-nums drop-shadow-sm">
                        {item.price}
                      </span>
                    </div>

                    {isCenter ? (
                      <Link
                        to={item.link}
                        className="inline-flex items-center gap-1.5 px-4 py-2 sm:px-5 sm:py-2.5 rounded-xl bg-[#0284C7] hover:bg-[#0369a1] text-white text-xs sm:text-sm font-bold shadow-lg transition-all transform active:scale-95 cursor-pointer"
                      >
                        <span>{language === 'en' ? 'Explore Tour' : 'Khám phá ngay'}</span>
                        <ArrowRight className="w-4 h-4" />
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

    </main>
  );
}

export default Explore;
