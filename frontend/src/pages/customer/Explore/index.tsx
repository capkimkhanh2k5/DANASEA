import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
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
  const [currentPage, setCurrentPage] = useState(0);
  const pageSize = 10;

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

  const totalPages = Math.max(1, Math.ceil(allExperiences.length / pageSize));
  const pagedExperiences = allExperiences.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

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

      {/* 2. Consolidated 10 Experiences Grid with Pagination */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-16">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4 pb-4 border-b border-slate-200/80">
          <div>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
              {language === 'en' ? 'Curated Marine Experiences' : 'Danh Mục Trải Nghiệm Biển'}
            </h2>
            <p className="text-sm sm:text-base text-slate-600 mt-1 font-medium">
              {language === 'en'
                ? 'Certified ocean adventures, coral diving, and watersports in Da Nang'
                : '10 trải nghiệm thể thao nước và tour khám phá vịnh biển tuyển chọn mỗi trang'}
            </p>
          </div>
          <span className="text-xs text-slate-500 font-mono">
            Trang {currentPage + 1} / {totalPages} ({allExperiences.length} hoạt động)
          </span>
        </div>

        {/* 10-Item Responsive Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-5">
          {pagedExperiences.map((item: ExploreExperienceCard) => (
            <Link
              key={item.id}
              to={item.link}
              className="group flex flex-col bg-white rounded-2xl overflow-hidden border border-slate-200/80 hover:border-slate-300 transition-all duration-300 hover:-translate-y-0.5 shadow-xs hover:shadow-md justify-between"
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
                  <p className="text-[12px] text-slate-500 mt-1 flex items-center gap-1">
                    <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                    <span className="truncate">{item.location}</span>
                  </p>
                </div>
                <div className="mt-3 pt-2.5 border-t border-slate-100 flex items-center justify-between">
                  <div className="flex items-center gap-1 text-xs font-bold text-slate-800">
                    <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                    <span>{item.rating.toFixed(1)}</span>
                  </div>
                  {/* Terracotta / deep warm reddish-brown price */}
                  <span className="text-sm font-bold text-[#991b1b] tabular-nums font-mono">{item.price}</span>
                </div>
              </div>
            </Link>
          ))}
        </div>

        {/* Clean Standard Pagination */}
        <div className="flex items-center justify-between pt-8 mt-6 border-t border-slate-200/80">
          <span className="text-xs text-slate-500 font-mono">
            Hiển thị {currentPage * pageSize + 1} - {Math.min((currentPage + 1) * pageSize, allExperiences.length)} trong tổng số {allExperiences.length} trải nghiệm
          </span>
          <div className="flex items-center gap-1.5 select-none">
            <button
              onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              disabled={currentPage === 0}
              className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
              <ChevronLeft className="w-3.5 h-3.5 inline mr-0.5" />
              Trước
            </button>
            {Array.from({ length: totalPages }).map((_, i) => (
              <button
                key={i}
                onClick={() => setCurrentPage(i)}
                className={`w-8 h-8 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                  currentPage === i
                    ? 'bg-[#0284C7] text-white shadow-xs'
                    : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                {i + 1}
              </button>
            ))}
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={currentPage >= totalPages - 1}
              className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
              Sau
              <ChevronRight className="w-3.5 h-3.5 inline ml-0.5" />
            </button>
          </div>
        </div>
      </section>

      {/* 3. Maritime Safety Storytelling Banner (No dry bullet points, ocean backdrop) */}
      <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-16 animate-fadeInUp">
        <div className="relative rounded-2xl overflow-hidden shadow-xl border border-sky-900/60">
          <div 
            className="absolute inset-0 bg-cover bg-center"
            style={{ backgroundImage: `url('/images/hero/08-cano-cao-toc-luot-vinh-da-nang.jpg')` }}
          />
          <div className="absolute inset-0 bg-gradient-to-r from-slate-950/95 via-slate-950/75 to-slate-950/20" />
          <div className="relative z-10 p-8 sm:p-12 max-w-2xl text-white space-y-4">
            <span className="text-xs uppercase tracking-wider font-bold text-sky-400 block">
              {language === 'en' ? 'Maritime Safety Assurance' : 'Quy Chuẩn An Toàn Hàng Hải'}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white leading-snug">
              {language === 'en' 
                ? 'Marine Safety and Coral Conservation Standards' 
                : 'An Toàn Hàng Hải & Bảo Tồn Rạn San Hô Tự Nhiên'}
            </h2>
            <p className="text-sm sm:text-base text-slate-200 leading-relaxed font-normal">
              {language === 'en'
                ? 'DANASEA enforces strict maritime safety standards with 100% SOLAS-certified life jackets, certified PADI rescue instructors, and strict coral conservation protocols. High-speed vessels and diving equipment undergo regular technical inspections, with emergency patrol teams stationed across Da Nang bays.'
                : 'DANASEA áp dụng tiêu chuẩn an toàn hàng hải tuyệt đối với 100% áo phao đạt kiểm định SOLAS quốc tế, hướng dẫn viên đạt chứng chỉ cứu sinh PADI và quy định nghiêm ngặt bảo vệ rạn san hô tự nhiên tại bán đảo Sơn Trà. Toàn bộ cano cao tốc, thiết bị lặn biển được kiểm định kỹ thuật định kỳ cùng đội tuần tra cứu hộ thường trực, bảo đảm mọi du khách an tâm trọn vẹn trong suốt hành trình.'}
            </p>
          </div>
        </div>
      </section>

    </main>
  );
}

export default Explore;
