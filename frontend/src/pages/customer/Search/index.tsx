import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Search as SearchIcon,
  MapPin,
  Star,
  Clock,
  ShieldCheck,
  Heart,
  RotateCcw,
  SlidersHorizontal,
  Users,
  Calendar,
  ArrowRight,
  Sparkles,
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  ChevronLeft,
  ChevronRight,
  Zap,
  Tag,
  Gift,
  Copy,
  Check,
} from 'lucide-react';
import { useWishlistStore } from '../../../store/useWishlistStore';
import { useAuthStore } from '../../../store/useAuthStore';
import { useLanguageStore } from '../../../store/useLanguageStore';
import { TRANSLATIONS } from '../../../i18n/translations';

import { serviceCatalogService, type ServiceQueryParams } from '../../../services/serviceCatalogService';
import { flashSaleService, type ActiveFlashSaleItem } from '../../../services/flashSaleService';
import { customerDiscountService } from '../../../services/customerDiscountService';
import { categoryService } from '../../../services/categoryService';
import type { BadgeTier } from '../../../types';
import type { DiscountCodeResponse } from '../../../types/adminDiscountCode';
import type { CategoryDto } from '../../../types/category';
import { getBadgeTierLabel, isPublicBadgeVisible, normalizeBadgeTier } from '../../../utils/badgeTier';
import { getCardThumbnailUrl, FALLBACK_CARD_IMAGE } from '../../../utils/imageOptimizer';
import { resolveServicePromotion, type ResolvedVendorVoucher } from '../../../utils/servicePromotion';
import { SEOHead } from '../../../components/common/SEOHead';
import { getTourUrl } from '../../../utils/seo';

// Normalized experience item interface for Traveloka-style display
interface DisplayExperience {
  id: string;
  name: string;
  nameEn?: string;
  slug?: string;
  description: string;
  price: number;
  effectivePrice: number;
  strikethroughPrice: number | null;
  hasFlashSale: boolean;
  flashSalePercent: number;
  bestVendorVoucher: ResolvedVendorVoucher | null;
  hasAnyPromotion: boolean;
  rating: number;
  reviewCount: number;
  location: string;
  area: 'Sơn Trà' | 'Mỹ Khê' | 'Bãi Rạng' | 'Non Nước';
  imageUrl: string;
  duration?: string;
  badge?: string;
  vendorBadgeTier?: BadgeTier;
  categoryId?: string;
  categoryName?: string;
  hasPhotos?: boolean;
  freeCancel?: boolean;
  hasCoach?: boolean;
  distanceFromCenter?: string;
}

export function Search() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const { toggleWishlist, isInWishlist } = useWishlistStore();
  const { isAuthenticated } = useAuthStore();
  const { language } = useLanguageStore();
  const t = TRANSLATIONS[language].searchPage;
  const isEn = language === 'en';

  const categoryParam = searchParams.get('category');
  const keywordParam = searchParams.get('q') || '';
  const badgeTierParam = searchParams.get('badgeTier');
  const onSaleParam = searchParams.get('onSale');

  // Data states
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [services, setServices] = useState<DisplayExperience[]>([]);
  const [activeFlashSales, setActiveFlashSales] = useState<ActiveFlashSaleItem[]>([]);
  const [personalVouchers, setPersonalVouchers] = useState<DiscountCodeResponse[]>([]);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Search input state
  const [keyword, setKeyword] = useState<string>(keywordParam);

  // Traveloka Filter States
  // 1. Popular Filters (Bộ lọc phổ biến)
  const [popFreeCancel, setPopFreeCancel] = useState<boolean>(false);
  const [popTopRated, setPopTopRated] = useState<boolean>(false);
  const [onlyTopRatedVendors, setOnlyTopRatedVendors] = useState<boolean>(
    badgeTierParam?.toUpperCase() === 'TOP_RATED'
  );
  const [onlyOnSale, setOnlyOnSale] = useState<boolean>(
    onSaleParam === 'true' || onSaleParam === '1'
  );
  const [popUnder500k, setPopUnder500k] = useState<boolean>(false);
  const [popHasCoach, setPopHasCoach] = useState<boolean>(false);
  const [popHasPhotos, setPopHasPhotos] = useState<boolean>(false);

  // 2. Area / Vùng biển Đà Nẵng
  const [selectedAreas, setSelectedAreas] = useState<string[]>([]);

  // 3. Category / Thể loại
  const [selectedCategorySlugs, setSelectedCategorySlugs] = useState<string[]>(
    categoryParam ? [categoryParam] : []
  );

  // 4. Price Slider
  const [priceMax, setPriceMax] = useState<number>(2500000);

  // 5. Min Rating
  const [minRating, setMinRating] = useState<number>(0);

  // 6. Sort by
  const [sortBy, setSortBy] = useState<'popular' | 'newest' | 'rating_desc' | 'price_asc' | 'price_desc'>('popular');

  // Accordion toggle states for sidebar
  const [collapsePop, setCollapsePop] = useState(false);
  const [collapseArea, setCollapseArea] = useState(false);
  const [collapsePrice, setCollapsePrice] = useState(false);
  const [collapseCat, setCollapseCat] = useState(false);
  const [collapseRating, setCollapseRating] = useState(false);

  // Mobile filter drawer state
  const [mobileFilterOpen, setMobileFilterOpen] = useState<boolean>(false);

  // Pagination state (strictly 10 items per page)
  const [currentPage, setCurrentPage] = useState<number>(1);
  const pageSize = 10;

  // Reset page when filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [
    keyword,
    selectedAreas,
    selectedCategorySlugs,
    priceMax,
    minRating,
    sortBy,
    popFreeCancel,
    popTopRated,
    onlyTopRatedVendors,
    onlyOnSale,
    popUnder500k,
    popHasCoach,
    popHasPhotos,
  ]);

  // 1. Fetch categories
  useEffect(() => {
    let isMounted = true;
    async function loadCategories() {
      try {
        const remoteCats = await categoryService.getCategories();
        if (isMounted) {
          setCategories(remoteCats || []);
        }
      } catch (err) {
        console.warn('[Search] Lỗi nạp danh mục:', err);
        if (isMounted) setCategories([]);
      }
    }
    loadCategories();
    return () => { isMounted = false; };
  }, []);

  // 1b. Fetch active Flash Sales for top Merchandising Strip
  useEffect(() => {
    let isMounted = true;
    flashSaleService
      .getActiveFlashSales()
      .then((items) => {
        if (isMounted) setActiveFlashSales(items || []);
      })
      .catch(() => {
        if (isMounted) setActiveFlashSales([]);
      });
    return () => { isMounted = false; };
  }, []);

  // 1c. Fetch logged-in customer's active personal/compensation vouchers for subtle reminder strip
  useEffect(() => {
    if (!isAuthenticated) {
      setPersonalVouchers([]);
      return;
    }
    let isMounted = true;
    const nowMs = Date.now();
    customerDiscountService
      .getMyDiscountCodes({ size: 20 })
      .then((res) => {
        if (!isMounted) return;
        const usable = (res.content || []).filter((v) => {
          if (v.isActive === false) return false;
          if (v.maxUses != null && (v.usedCount ?? 0) >= v.maxUses) return false;
          if (v.validTo && new Date(v.validTo).getTime() <= nowMs) return false;
          if (v.validFrom && new Date(v.validFrom).getTime() > nowMs) return false;
          return true;
        });
        setPersonalVouchers(usable);
      })
      .catch(() => {
        if (isMounted) setPersonalVouchers([]);
      });
    return () => { isMounted = false; };
  }, [isAuthenticated]);

  const handleCopyCode = (code: string, e?: React.MouseEvent) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(code).catch(() => {});
    }
    setCopiedCode(code);
    setTimeout(() => {
      setCopiedCode((prev) => (prev === code ? null : prev));
    }, 2200);
  };

  // Reset category selection when navigation changes the URL category.
  const [previousCategoryParam, setPreviousCategoryParam] = useState(categoryParam);
  if (previousCategoryParam !== categoryParam) {
    setPreviousCategoryParam(categoryParam);
    if (categoryParam) setSelectedCategorySlugs([categoryParam]);
  }

  // 3. Fetch services with fallback
  useEffect(() => {
    let isMounted = true;
    async function fetchServices() {
      setIsLoading(true);

      try {
        const queryParams: ServiceQueryParams = {
          size: 40,
          ...(onlyTopRatedVendors ? { badgeTier: 'TOP_RATED' } : {}),
          ...(onlyOnSale ? { onSale: true } : {}),
          ...(sortBy === 'newest' || sortBy === 'price_asc' || sortBy === 'price_desc' || sortBy === 'rating_desc' ? { sortBy } : {}),
        };

        const response = await serviceCatalogService.getServices(queryParams);

        if (isMounted) {
          if (response && response.content && response.content.length > 0) {
            const mapped: DisplayExperience[] = response.content.map((item, idx) => {
              const areaOptions: ('Sơn Trà' | 'Mỹ Khê' | 'Bãi Rạng' | 'Non Nước')[] = [
                'Sơn Trà', 'Mỹ Khê', 'Bãi Rạng', 'Non Nước'
              ];
              const determinedArea = areaOptions[idx % 4];
              const promo = resolveServicePromotion({
                price: Number(item.price) || 0,
                flashSales: item.flashSales,
                vendorPromotions: item.vendorPromotions,
              });

              return {
                id: item.id,
                name: item.name,
                nameEn: item.nameEn,
                slug: item.slug,
                description: (language === 'en' && 'shortDescriptionEn' in item && typeof item.shortDescriptionEn === 'string')
                  ? (item.shortDescriptionEn as string)
                  : item.shortDescription || (language === 'en' ? 'Standard maritime safety experience in Da Nang Bay, equipped with SOLAS life vests and 1:1 local instructor.' : 'Trải nghiệm thể thao biển chuẩn an toàn hàng hải tại vịnh Đà Nẵng, trang bị áo phao SOLAS và hướng dẫn viên 1:1.'),
                price: promo.basePrice,
                effectivePrice: promo.effectivePrice,
                strikethroughPrice: promo.strikethroughPrice,
                hasFlashSale: promo.hasFlashSale,
                flashSalePercent: promo.flashSalePercent,
                bestVendorVoucher: promo.bestVendorVoucher,
                hasAnyPromotion: promo.hasAnyPromotion,
                rating: item.averageRating || 4.9,
                reviewCount: item.reviewCount || 128,
                location: item.address ? item.address.split(',')[0] : (language === 'en' ? `${determinedArea} Beach` : `Bán đảo ${determinedArea}`),
                area: determinedArea,
                imageUrl: getCardThumbnailUrl(item.primaryImageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80'),
                duration: ('duration' in item && typeof item.duration === 'number' && item.duration > 0 ? `${item.duration} phút` : undefined) || (item.name.toLowerCase().includes('phao chuối') ? '20 phút' : '60 phút'),
                vendorBadgeTier: item.vendorBadgeTier ? normalizeBadgeTier(item.vendorBadgeTier) : undefined,
                categoryId: ('categoryId' in item && typeof item.categoryId === 'string' ? item.categoryId : undefined) || (item.name.toLowerCase().includes('sup') ? 'cheo-sup-kayak' : item.name.toLowerCase().includes('lặn') || item.name.toLowerCase().includes('san hô') ? 'lan-ngam-san-ho' : item.name.toLowerCase().includes('cano') || item.name.toLowerCase().includes('dù') ? 'cano-du-bay' : 'mo-to-nuoc-jetski'),
                hasPhotos: true,
                freeCancel: true,
                hasCoach: true,
                distanceFromCenter: `${(idx * 2 + 4).toFixed(1)} km từ trung tâm`,
              };
            });
            setServices(mapped);
          } else {
            setServices([]);
          }
        }
      } catch (err) {
        console.warn('[Search] Lỗi nạp dịch vụ:', err);
        if (isMounted) {
          setServices([]);
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    fetchServices();
    return () => { isMounted = false; };
  }, [sortBy, onlyTopRatedVendors, onlyOnSale, language]);

  // Toggle helpers for Traveloka Multi-Checkboxes
  const toggleArea = (area: string) => {
    setSelectedAreas((prev) => 
      prev.includes(area) ? prev.filter((a) => a !== area) : [...prev, area]
    );
  };

  const toggleCategorySlug = (slug: string) => {
    setSelectedCategorySlugs((prev) => {
      const next = prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug];
      if (next.length === 1) {
        setSearchParams({ category: next[0] });
      } else if (next.length === 0) {
        setSearchParams({});
      }
      return next;
    });
  };

  // Comprehensive Client-side Filtering
  const filteredServices = useMemo(() => {
    let result = [...services];

    // Keyword search
    if (keyword.trim()) {
      const kw = keyword.toLowerCase().trim();
      result = result.filter(
        (s) => s.name.toLowerCase().includes(kw) || s.description.toLowerCase().includes(kw) || s.location.toLowerCase().includes(kw)
      );
    }

    // Popular Filters
    if (popFreeCancel) {
      result = result.filter((s) => s.freeCancel);
    }
    if (popTopRated) {
      result = result.filter((s) => s.rating >= 4.8);
    }
    if (popUnder500k) {
      result = result.filter((s) => s.effectivePrice <= 500000);
    }
    if (popHasCoach) {
      result = result.filter((s) => s.hasCoach);
    }
    if (popHasPhotos) {
      result = result.filter((s) => s.hasPhotos);
    }

    // Area filter (Multi-selection)
    if (selectedAreas.length > 0) {
      result = result.filter((s) => selectedAreas.includes(s.area));
    }

    // Category filter (Multi-selection)
    if (selectedCategorySlugs.length > 0) {
      result = result.filter((s) => {
        if (!s.categoryId) return true;
        return selectedCategorySlugs.some((slug) => s.categoryId?.includes(slug));
      });
    }

    // Price Max filter (effectivePrice = Flash Sale price when active)
    if (priceMax < 2500000) {
      result = result.filter((s) => s.effectivePrice <= priceMax);
    }

    // Min Rating filter
    if (minRating > 0) {
      result = result.filter((s) => s.rating >= minRating);
    }

    // Sorting (price sort uses effectivePrice; default 'popular' preserves backend Composite Recommended order)
    if (sortBy === 'price_asc') {
      result.sort((a, b) => a.effectivePrice - b.effectivePrice);
    } else if (sortBy === 'price_desc') {
      result.sort((a, b) => b.effectivePrice - a.effectivePrice);
    } else if (sortBy === 'rating_desc') {
      result.sort((a, b) => b.rating - a.rating);
    }

    return result;
  }, [
    services,
    keyword,
    popFreeCancel,
    popTopRated,
    popUnder500k,
    popHasCoach,
    popHasPhotos,
    selectedAreas,
    selectedCategorySlugs,
    priceMax,
    minRating,
    sortBy,
  ]);

  const totalPages = Math.ceil(filteredServices.length / pageSize);
  const paginatedServices = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredServices.slice(start, start + pageSize);
  }, [filteredServices, currentPage, pageSize]);

  // Counts calculation for Traveloka Badges
  const areaCounts = useMemo(() => {
    const counts: Record<string, number> = { 'Sơn Trà': 0, 'Mỹ Khê': 0, 'Bãi Rạng': 0, 'Non Nước': 0 };
    services.forEach((s) => {
      if (counts[s.area] !== undefined) counts[s.area]++;
    });
    return counts;
  }, [services]);

  const handleResetFilters = () => {
    setPopFreeCancel(false);
    setPopTopRated(false);
    setOnlyTopRatedVendors(false);
    setOnlyOnSale(false);
    setPopUnder500k(false);
    setPopHasCoach(false);
    setPopHasPhotos(false);
    setSelectedAreas([]);
    setSelectedCategorySlugs([]);
    setKeyword('');
    setPriceMax(2500000);
    setMinRating(0);
    setSortBy('popular');
    setSearchParams({});
  };

  const hasActiveFilters = Boolean(
    popFreeCancel ||
    popTopRated ||
    onlyTopRatedVendors ||
    onlyOnSale ||
    popUnder500k ||
    popHasCoach ||
    popHasPhotos ||
    selectedAreas.length > 0 ||
    selectedCategorySlugs.length > 0 ||
    keyword ||
    priceMax < 2500000 ||
    minRating > 0 ||
    sortBy !== 'popular'
  );

  return (
    <div className="w-full bg-[#f8fafc] min-h-screen text-slate-900 pt-20 pb-32">
      
      {/* =========================================================================
          TOP UNIFIED SEARCH & CONTROL STRIP (Header Query Bar)
         ========================================================================= */}
      <section className="bg-white border-b border-slate-200/80 text-slate-900 py-4 sm:py-5 px-4 sm:px-6 lg:px-8 shadow-xs">
        <div className="max-w-[1440px] mx-auto animate-fadeInUp">
          
          {/* Quick Query Bar (Modern Geometric Design: rounded-2xl & rounded-xl) */}
          <div className="bg-white rounded-2xl border border-slate-200/90 p-2 sm:p-2.5 flex flex-col md:flex-row items-center gap-2 text-slate-800 shadow-xs hover:border-slate-300 transition-colors">
            
            {/* Field 1: Destination / Keyword */}
            <div className="flex-1 min-w-0 w-full flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100/80 focus-within:bg-white focus-within:ring-1 focus-within:ring-sky-500 border border-transparent focus-within:border-sky-500 transition-all">
              <MapPin className="w-4 h-4 text-sky-700 shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">{t.destLabel}</p>
                <input
                  type="text"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder={t.destPlaceholder}
                  className="w-full bg-transparent text-[13px] sm:text-sm font-semibold text-slate-900 placeholder:text-slate-400 outline-none"
                />
              </div>
              {keyword && (
                <button type="button" onClick={() => setKeyword('')} className="text-slate-400 hover:text-slate-700 text-sm px-1 cursor-pointer">✕</button>
              )}
            </div>

            {/* Field 2: Date Selector */}
            <div className="w-full md:w-56 flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100/80 border border-transparent transition-colors cursor-pointer shrink-0">
              <Calendar className="w-4 h-4 text-sky-700 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">{t.dateLabel}</p>
                <p className="text-[13px] sm:text-sm font-semibold text-slate-900 truncate">{t.dateTodayTomorrow}</p>
              </div>
            </div>

            {/* Field 3: Pax / Guests Count */}
            <div className="w-full md:w-64 lg:w-72 flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100/80 border border-transparent transition-colors cursor-pointer shrink-0">
              <Users className="w-4 h-4 text-sky-700 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">{t.guestsLabel}</p>
                <p className="text-[13px] sm:text-sm font-semibold text-slate-900 truncate" title={t.guestsDefault}>
                  {t.guestsDefault}
                </p>
              </div>
            </div>

            {/* Search Submit Button */}
            <button
              type="button"
              className="w-full md:w-auto px-6 py-3 rounded-xl bg-sky-700 hover:bg-sky-600 active:bg-sky-800 text-white font-bold text-[13px] sm:text-sm flex items-center justify-center gap-2 transition-all shrink-0 hover:-translate-y-0.5 shadow-xs cursor-pointer"
            >
              <SearchIcon className="w-4 h-4" />
              <span>{t.searchBtn}</span>
            </button>

          </div>

        </div>
      </section>

      {/* =========================================================================
          MAIN 2-COLUMN TRAVELOKA SEARCH LAYOUT (Sidebar Filters + Results)
         ========================================================================= */}
      <main className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-44">
        <SEOHead
          title={
            language === 'en'
              ? keyword
                ? `Search: "${keyword}"`
                : categoryParam
                  ? `Category: ${categoryParam}`
                  : 'Search Marine Tours & Ocean Experiences'
              : keyword
                ? `Tìm kiếm: "${keyword}"`
                : categoryParam
                  ? `Dịch vụ: ${categoryParam}`
                  : 'Tìm Kiếm Tour & Trải Nghiệm Biển'
          }
          description={
            language === 'en'
              ? 'Search and book water sports, SUP paddleboarding, coral snorkeling, and speedboats in Da Nang at the best prices on DANASEA.'
              : 'Tìm kiếm và đặt tour thể thao biển, chèo SUP, lặn ngắm san hô, cano lướt sóng tại Đà Nẵng với giá tốt nhất trên DANASEA.'
          }
          canonicalUrl="/search"
        />
        
        {/* Mobile Filter Toggle Button */}
        <div className="lg:hidden mb-4 flex justify-end">
          <button
            type="button"
            onClick={() => setMobileFilterOpen(!mobileFilterOpen)}
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-[13px] font-bold text-slate-700 cursor-pointer"
          >
            <SlidersHorizontal className="w-3.5 h-3.5 text-sky-700" />
            <span>{mobileFilterOpen ? t.closeFilter : t.filterTitle}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* =====================================================================
              LEFT COLUMN: TRAVELOKA FILTER SIDEBAR (Popular, Area, Price, etc.)
             ===================================================================== */}
          <aside className={`lg:col-span-4 xl:col-span-3 ${mobileFilterOpen ? 'block' : 'hidden lg:block'} space-y-4`}>
            
            <div className="bg-white rounded-2xl border border-slate-200/90 p-4 sm:p-5 sticky top-24 space-y-6">
              
              {/* Header: Title & Reset Button */}
              <div className="flex items-center justify-between pb-1">
                <div className="flex items-center gap-2">
                  <SlidersHorizontal className="w-4 h-4 text-sky-700" />
                  <h2 className="font-bold text-sm text-slate-900">{t.filterTitle}</h2>
                </div>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={handleResetFilters}
                    className="text-[13px] font-semibold text-sky-700 hover:text-sky-800 flex items-center gap-1 cursor-pointer"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>{t.resetBtn}</span>
                  </button>
                )}
              </div>

              {/* SECTION 1: FILTER POPULER (Bộ lọc phổ biến như Traveloka) */}
              <div className="space-y-3">
                <div 
                  className="flex items-center justify-between cursor-pointer select-none"
                  onClick={() => setCollapsePop(!collapsePop)}
                >
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900 flex items-center gap-1.5">
                    <Sparkles className="w-4 h-4 text-amber-500" />
                    <span>{t.popularFilters}</span>
                  </span>
                  {collapsePop ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapsePop && (
                  <div className="space-y-2.5 pt-1 text-[13px] text-slate-700">
                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popFreeCancel}
                        onChange={(e) => setPopFreeCancel(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>{t.freeCancel}</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popTopRated}
                        onChange={(e) => setPopTopRated(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>{t.topRated}</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popUnder500k}
                        onChange={(e) => setPopUnder500k(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>{t.under500k}</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popHasCoach}
                        onChange={(e) => setPopHasCoach(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>{t.hasCoach}</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popHasPhotos}
                        onChange={(e) => setPopHasPhotos(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>{t.hasPhotos}</span>
                    </label>
                  </div>
                )}
              </div>

              {/* SECTION 2: AREA / KHU VỰC BIỂN (Traveloka Area Filter) */}
              <div className="space-y-3">
                <div 
                  className="flex items-center justify-between cursor-pointer select-none"
                  onClick={() => setCollapseArea(!collapseArea)}
                >
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900 flex items-center gap-1.5">
                    <MapPin className="w-4 h-4 text-sky-700" />
                    <span>{t.areaTitle}</span>
                  </span>
                  {collapseArea ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseArea && (
                  <div className="space-y-2.5 pt-1 text-[13px] text-slate-700">
                    {[
                      { id: 'Sơn Trà', label: t.areas.sonTra, note: t.areas.sonTraNote },
                      { id: 'Mỹ Khê', label: t.areas.myKhe, note: t.areas.myKheNote },
                      { id: 'Bãi Rạng', label: t.areas.baiRang, note: t.areas.baiRangNote },
                      { id: 'Non Nước', label: t.areas.nonNuoc, note: t.areas.nonNuocNote },
                    ].map((areaItem) => {
                      const isChecked = selectedAreas.includes(areaItem.id);
                      return (
                        <label 
                          key={areaItem.id}
                          className="flex items-start justify-between gap-2 cursor-pointer hover:text-slate-900 transition-colors"
                        >
                          <div className="flex items-start gap-2.5">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => toggleArea(areaItem.id)}
                              className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer mt-0.5"
                            />
                            <div>
                              <p className="font-semibold text-slate-800 text-[13px]">{areaItem.label}</p>
                              <p className="text-slate-500 text-[13px]">{areaItem.note}</p>
                            </div>
                          </div>
                          <span className="text-slate-600 text-[13px] tabular-nums font-semibold">
                            ({areaCounts[areaItem.id] || 0})
                          </span>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* SECTION 3: MỨC GIÁ / KISARAN HARGA (Price Range) */}
              <div className="space-y-3">
                <div 
                  className="flex items-center justify-between cursor-pointer select-none"
                  onClick={() => setCollapsePrice(!collapsePrice)}
                >
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">{t.priceTitle}</span>
                  {collapsePrice ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapsePrice && (
                  <div className="space-y-2.5 pt-1">
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="text-slate-500 font-medium">{t.priceCeiling}</span>
                      <span className="font-bold text-[#991b1b] tabular-nums">
                        {priceMax >= 2500000 ? t.allPrices : `${(priceMax / 1000).toLocaleString(isEn ? 'en-US' : 'vi-VN')}k ₫ ${t.perGuest}`}
                      </span>
                    </div>
                    <input
                      type="range"
                      min="200000"
                      max="2500000"
                      step="50000"
                      value={priceMax}
                      onChange={(e) => setPriceMax(Number(e.target.value))}
                      className="w-full accent-sky-600 cursor-pointer h-1.5 bg-slate-200 rounded-lg"
                    />
                    <div className="flex justify-between text-[13px] text-slate-500 font-medium">
                      <span>200k ₫</span>
                      <span>1.000k ₫</span>
                      <span>2.500k+ ₫</span>
                    </div>
                  </div>
                )}
              </div>

              {/* SECTION 4: THỂ LOẠI HOẠT ĐỘNG (Categories) */}
              <div className="space-y-3">
                <div 
                  className="flex items-center justify-between cursor-pointer select-none"
                  onClick={() => setCollapseCat(!collapseCat)}
                >
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">{t.categoriesTitle}</span>
                  {collapseCat ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseCat && (
                  <div className="space-y-2.5 pt-1 text-[13px] text-slate-700">
                    {categories.map((cat) => {
                      const slug = cat.slug || cat.id;
                      const isChecked = selectedCategorySlugs.includes(slug);
                      const catName = (isEn && cat.nameEn) ? cat.nameEn : cat.name;

                      return (
                        <label 
                          key={cat.id}
                          className="flex items-center justify-between gap-2 cursor-pointer hover:text-slate-900 transition-colors"
                        >
                          <div className="flex items-center gap-2.5">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => toggleCategorySlug(slug)}
                              className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                            />
                            <span className="font-medium text-slate-800">{catName}</span>
                          </div>
                          {isChecked && <CheckCircle2 className="w-4 h-4 text-sky-700" />}
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* SECTION 5: ĐÁNH GIÁ CỦA KHÁCH (Rating) */}
              <div className="space-y-3">
                <div 
                  className="flex items-center justify-between cursor-pointer select-none"
                  onClick={() => setCollapseRating(!collapseRating)}
                >
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">{t.ratingTitle}</span>
                  {collapseRating ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseRating && (
                  <div className="grid grid-cols-3 gap-1.5 text-[13px] pt-1">
                    {[
                      { label: '4.8+', val: 4.8 },
                      { label: '4.5+', val: 4.5 },
                      { label: t.allRatings, val: 0 },
                    ].map((r) => (
                      <button
                        key={r.label}
                        type="button"
                        onClick={() => setMinRating(r.val)}
                        className={`py-1.5 px-2 rounded-lg font-bold border transition-all cursor-pointer ${
                          minRating === r.val
                            ? 'bg-sky-700 text-white border-sky-700'
                            : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                        }`}
                      >
                        {r.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>

            </div>

          </aside>

          {/* =====================================================================
              RIGHT COLUMN: TRAVELOKA-STYLE RESULTS CARDS
             ===================================================================== */}
          <main className="lg:col-span-8 xl:col-span-9 space-y-4">

            {/* Personal Compensation / Reward Voucher Reminder Strip (logged-in customers only) */}
            {personalVouchers.length > 0 && (
              <div className="bg-emerald-50/90 border border-emerald-200 rounded-2xl px-4 py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                <div className="flex items-start sm:items-center gap-2.5 min-w-0">
                  <div className="w-8 h-8 rounded-xl bg-emerald-600 text-white flex items-center justify-center shrink-0">
                    <Gift className="w-4 h-4" />
                  </div>
                  <div className="min-w-0 text-[13px] text-emerald-950 leading-snug">
                    <span className="font-bold">
                      {isEn
                        ? `You have ${personalVouchers.length} personal voucher${personalVouchers.length > 1 ? 's' : ''}:`
                        : `Bạn đang có ${personalVouchers.length} voucher cá nhân toàn sàn:`}
                    </span>{' '}
                    <span className="font-mono font-bold text-emerald-800">{personalVouchers[0].code}</span>{' '}
                    <span>
                      (
                      {personalVouchers[0].discountType === 'PERCENTAGE'
                        ? isEn
                          ? `Save ${personalVouchers[0].discountValue}%`
                          : `Giảm ${personalVouchers[0].discountValue}%`
                        : isEn
                          ? `Save ${Number(personalVouchers[0].discountValue).toLocaleString('vi-VN')}₫`
                          : `Giảm ${Number(personalVouchers[0].discountValue).toLocaleString('vi-VN')}₫`}
                      {personalVouchers[0].validTo
                        ? ` • ${isEn ? 'Exp' : 'HSD'} ${new Date(personalVouchers[0].validTo).toLocaleDateString('vi-VN')}`
                        : ''}
                      )
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleCopyCode(personalVouchers[0].code)}
                  className="inline-flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-xl bg-white border border-emerald-300 hover:bg-emerald-100 text-emerald-800 text-xs font-bold transition-colors shrink-0 cursor-pointer"
                >
                  {copiedCode === personalVouchers[0].code ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-600" />
                      <span>{isEn ? 'Copied' : 'Đã sao chép'}</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" />
                      <span>{isEn ? 'Copy code' : 'Sao chép mã'}</span>
                    </>
                  )}
                </button>
              </div>
            )}

            {/* Header: Kết quả tìm kiếm & Thanh sắp xếp */}
            <div className="bg-white rounded-2xl border border-slate-200/90 p-4 sm:p-5 flex flex-col gap-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
                  {selectedAreas.length > 0
                    ? `${t.pageTitleAreaPrefix} ${selectedAreas.map((a) => ({
                        'Sơn Trà': t.areas.sonTra,
                        'Mỹ Khê': t.areas.myKhe,
                        'Bãi Rạng': t.areas.baiRang,
                        'Non Nước': t.areas.nonNuoc,
                      }[a] || a)).join(', ')}`
                    : t.pageTitle}
                </h1>
                <p className="text-[13px] text-slate-600">
                  {t.foundCount} <strong className="text-sky-700 tabular-nums font-bold">{filteredServices.length}</strong> {t.availableCount}
                </p>
              </div>

              {/* Sort Pills & Quick Deal / Top-Rated Filters */}
              <div className="flex flex-wrap items-center gap-1.5 pt-2.5 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setOnlyOnSale((prev) => !prev)}
                  className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-xl text-[13px] font-bold transition-all shrink-0 cursor-pointer border ${
                    onlyOnSale
                      ? 'bg-rose-50 border-rose-300 text-rose-800'
                      : 'bg-white border-slate-200 hover:border-rose-300 text-slate-700'
                  }`}
                >
                  <Zap className={`w-3.5 h-3.5 ${onlyOnSale ? 'fill-rose-500 text-rose-600' : 'text-rose-500'}`} />
                  <span>{isEn ? 'On Sale' : 'Đang ưu đãi'}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setOnlyTopRatedVendors((prev) => !prev)}
                  className={`px-3 py-1.5 rounded-xl text-[13px] font-bold transition-all shrink-0 cursor-pointer border ${
                    onlyTopRatedVendors
                      ? 'bg-amber-50 border-amber-300 text-amber-900'
                      : 'bg-white border-slate-200 hover:border-amber-300 text-slate-700'
                  }`}
                >
                  {isEn ? 'Top Partner' : 'Đối tác hàng đầu'}
                </button>
                <span className="text-[13px] text-slate-600 font-medium shrink-0 mx-1 hidden sm:inline">{t.sortBy}</span>
                {([
                  { id: 'popular', label: isEn ? 'Recommended' : 'Đề xuất' },
                  { id: 'newest', label: isEn ? 'Newest' : 'Mới nhất' },
                  { id: 'rating_desc', label: t.sortRating },
                  { id: 'price_asc', label: t.sortPriceAsc },
                  { id: 'price_desc', label: t.sortPriceDesc },
                ] as const).map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => setSortBy(s.id)}
                    className={`px-3 py-1.5 rounded-xl text-[13px] font-bold transition-all shrink-0 cursor-pointer ${
                      sortBy === s.id
                        ? 'bg-sky-700 text-white'
                        : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                    }`}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Compact Active Flash Sale Merchandising Strip (shown when browsing broadly) */}
            {!keyword.trim() && activeFlashSales.length > 0 && (
              <section className="bg-gradient-to-r from-rose-50 via-amber-50/60 to-sky-50/70 rounded-2xl border border-rose-200/70 p-4">
                <div className="flex items-center justify-between gap-3 mb-3">
                  <div className="flex items-center gap-2">
                    <span className="inline-flex items-center justify-center w-7 h-7 rounded-lg bg-rose-600 text-white">
                      <Zap className="w-4 h-4 fill-amber-300 text-amber-300" />
                    </span>
                    <div>
                      <h2 className="text-sm sm:text-base font-extrabold text-slate-900 leading-tight">
                        {isEn ? 'Live Flash Sales & Deals' : 'Ưu đãi & Flash Sale đang diễn ra'}
                      </h2>
                      <p className="text-xs text-slate-600">
                        {isEn
                          ? 'Direct platform-subsidized prices — stackable with shop vouchers at checkout'
                          : 'Giảm trực tiếp vào giá vé — có thể cộng dồn thêm mã giảm giá của đối tác khi thanh toán'}
                      </p>
                    </div>
                  </div>
                  {!onlyOnSale && (
                    <button
                      type="button"
                      onClick={() => setOnlyOnSale(true)}
                      className="text-xs font-bold text-rose-700 hover:text-rose-800 whitespace-nowrap cursor-pointer inline-flex items-center gap-1"
                    >
                      <span>{isEn ? 'View all deals' : 'Lọc ưu đãi'}</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {activeFlashSales.slice(0, 4).map((fs) => (
                    <div
                      key={fs.flashSaleId}
                      onClick={() => navigate(`/tour/${fs.serviceId}`)}
                      className="group bg-white rounded-xl border border-rose-200/70 hover:border-rose-400 p-2.5 flex items-center gap-3 cursor-pointer transition-all hover:shadow-sm"
                    >
                      <div className="relative w-20 h-16 rounded-lg overflow-hidden bg-slate-100 shrink-0">
                        <img
                          src={getCardThumbnailUrl(fs.imageUrl || FALLBACK_CARD_IMAGE)}
                          alt={fs.serviceName}
                          onError={(e) => {
                            e.currentTarget.src = FALLBACK_CARD_IMAGE;
                          }}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                        />
                        {fs.discountPercentage > 0 && (
                          <span className="absolute top-1 left-1 px-1.5 py-0.5 rounded bg-rose-600 text-white text-[10px] font-extrabold tabular-nums">
                            -{fs.discountPercentage}%
                          </span>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-[13px] font-bold text-slate-900 group-hover:text-sky-700 truncate">
                          {fs.serviceName}
                        </p>
                        <div className="flex items-baseline gap-1.5 mt-0.5">
                          <span className="text-sm font-black text-[#991b1b] tabular-nums">
                            {fs.salePrice.toLocaleString('vi-VN')} ₫
                          </span>
                          <span className="text-xs text-slate-400 line-through tabular-nums">
                            {fs.originalPrice.toLocaleString('vi-VN')} ₫
                          </span>
                        </div>
                        <p className="text-[11px] text-rose-700 font-semibold mt-0.5">
                          {isEn
                            ? `${Math.max(0, fs.stockAllocated - fs.stockSold)} deals left`
                            : `Còn ${Math.max(0, fs.stockAllocated - fs.stockSold)} suất ưu đãi`}
                        </p>
                      </div>
                      <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-rose-600 group-hover:translate-x-0.5 transition-all shrink-0 mr-1" />
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* List of Traveloka-Style Horizontal Cards */}
            {isLoading ? (
              <div className="space-y-4">
                {[1, 2, 3].map((n) => (
                  <div key={n} className="bg-white rounded-2xl border border-slate-200/80 p-4 animate-pulse flex flex-col sm:flex-row gap-4 h-56">
                    <div className="w-full sm:w-64 bg-slate-200 rounded-xl"></div>
                    <div className="flex-1 space-y-3 py-2">
                      <div className="h-5 bg-slate-200 rounded w-3/4"></div>
                      <div className="h-4 bg-slate-200 rounded w-1/2"></div>
                      <div className="h-4 bg-slate-200 rounded w-full"></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : filteredServices.length > 0 ? (
              <div className="space-y-4">
                {paginatedServices.map((service) => {
                  const isSaved = isInWishlist(service.id);

                  return (
                    <article
                      key={service.id}
                      data-ui="traveloka-experience-card"
                      onClick={() => navigate(getTourUrl(service))}
                      className="group bg-white rounded-2xl border border-slate-200/90 hover:border-sky-400 hover:shadow-lg transition-all duration-200 flex flex-col sm:flex-row overflow-hidden cursor-pointer animate-fadeInUp"
                    >
                      {/* Left: Image Box (~280px) */}
                      <div className="relative w-full sm:w-64 md:w-72 h-48 sm:h-auto shrink-0 overflow-hidden bg-slate-100">
                        <img
                          src={service.imageUrl}
                          alt={(language === 'en' && service.nameEn) ? service.nameEn : service.name}
                          loading="lazy"
                          onError={(e) => {
                            e.currentTarget.src = FALLBACK_CARD_IMAGE;
                          }}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                        />
                        
                        {/* Status / Single Clean Promo Badge Overlay on Image */}
                        <div className="absolute top-2.5 left-2.5">
                          {service.hasFlashSale ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-rose-600/95 backdrop-blur-xs text-white text-[12px] font-extrabold shadow-sm">
                              <Zap className="w-3.5 h-3.5 fill-amber-300 text-amber-300 shrink-0" />
                              <span>Flash Sale -{service.flashSalePercent}%</span>
                            </span>
                          ) : (
                            <span className="px-2.5 py-1 rounded-md bg-[#031d44]/90 backdrop-blur-xs text-white text-[13px] font-bold">
                              {t.solasBadge}
                            </span>
                          )}
                        </div>

                        {/* Wishlist Heart */}
                        <button
                          type="button"
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            toggleWishlist(service.id, (language === 'en' && service.nameEn) ? service.nameEn : service.name);
                          }}
                          className="absolute top-2.5 right-2.5 w-8 h-8 rounded-xl bg-slate-900/60 hover:bg-slate-900/80 flex items-center justify-center hover:scale-105 active:scale-95 transition-transform cursor-pointer z-10"
                        >
                          <Heart className={`w-4 h-4 transition-colors ${isSaved ? 'text-rose-500 fill-rose-500' : 'text-white'}`} />
                        </button>
                      </div>

                      {/* Middle: Details & Features */}
                      <div className="p-4 sm:p-5 flex-1 flex flex-col justify-between">
                        <div>
                          
                          {/* Title */}
                          <h2 className="text-base sm:text-lg font-bold text-slate-900 group-hover:text-sky-700 transition-colors line-clamp-2 leading-snug">
                            {(language === 'en' && service.nameEn) ? service.nameEn : service.name}
                          </h2>

                          {/* Rating & Review Count (Traveloka Score Pill Style) */}
                          <div className="flex flex-wrap items-center gap-2 mt-2">
                            <div className="inline-flex items-center gap-1 bg-sky-50 text-sky-800 px-2 py-0.5 rounded-md font-bold text-[13px] tabular-nums">
                              <Star className="w-3.5 h-3.5 fill-sky-700 text-sky-700" />
                              <span>{service.rating.toFixed(1)} / 5</span>
                            </div>
                            <span className="text-[13px] font-semibold text-slate-700">{t.ratingExcellent}</span>
                            <span className="text-[13px] text-slate-600 tabular-nums font-medium">({service.reviewCount} {t.reviewsCount})</span>
                            {isPublicBadgeVisible(service.vendorBadgeTier) && (
                              <span
                                className={`inline-flex items-center px-2 py-0.5 rounded-md text-[12px] font-semibold border ${
                                  normalizeBadgeTier(service.vendorBadgeTier) === 'TOP_RATED'
                                    ? 'bg-amber-50 text-amber-800 border-amber-200'
                                    : 'bg-emerald-50 text-emerald-800 border-emerald-200'
                                }`}
                              >
                                {isEn
                                  ? (normalizeBadgeTier(service.vendorBadgeTier) === 'TOP_RATED' ? 'Top Partner' : 'Verified')
                                  : getBadgeTierLabel(service.vendorBadgeTier)}
                              </span>
                            )}
                          </div>

                          {/* Location & Duration & Distance */}
                          <div className="flex flex-wrap items-center gap-2 text-[13px] text-slate-600 mt-2 font-medium">
                            <span className="flex items-center gap-1 font-semibold text-slate-800">
                              <MapPin className="w-3.5 h-3.5 text-sky-700" />
                              <span>{service.location}</span>
                            </span>
                            <span>•</span>
                            <span className="text-slate-500">
                              {isEn ? service.distanceFromCenter?.replace('từ trung tâm', 'from center') : service.distanceFromCenter}
                            </span>
                            <span>•</span>
                            <span className="flex items-center gap-1 text-slate-500">
                              <Clock className="w-3.5 h-3.5 text-slate-500" />
                              <span>{service.duration ? (isEn ? service.duration.replace('phút', 'mins') : service.duration) : t.durationDefault}</span>
                            </span>
                          </div>

                          {/* Short Description */}
                          <p className="text-[13px] text-slate-600 line-clamp-2 leading-relaxed mt-2">
                            {service.description}
                          </p>

                          {/* Amenities Chips */}
                          <div className="flex flex-wrap items-center gap-1.5 mt-3">
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-[13px] font-medium">
                              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                              {t.lifeVest}
                            </span>
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-[13px] font-medium">
                              <Sparkles className="w-3.5 h-3.5 text-sky-700" />
                              {t.coach1on1}
                            </span>
                            {service.hasPhotos && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-[13px] font-medium">
                                <Camera className="w-3.5 h-3.5 text-amber-600" />
                                {t.freePhotos}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Cancellation Policy Banner */}
                        <div className="mt-3 pt-2.5 border-t border-slate-100 flex items-center gap-1.5 text-[13px] text-emerald-700 font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                          <span>{t.policyBanner}</span>
                        </div>
                      </div>

                      {/* Right: Commercial Pricing & Action Box (Traveloka Style) */}
                      <div className="p-4 sm:p-5 sm:border-l sm:border-slate-100 flex flex-col justify-between items-start w-full sm:w-60 shrink-0 bg-slate-50/50 sm:bg-transparent">
                        
                        {/* Price Display (Honest Primary Flash Sale Price + Tasteful Shop Voucher Pill) */}
                        <div className="text-left w-full">
                          {service.strikethroughPrice && service.strikethroughPrice > service.effectivePrice && (
                            <p className="text-[13px] text-slate-400 line-through tabular-nums font-medium">
                              {service.strikethroughPrice.toLocaleString('vi-VN')} ₫
                            </p>
                          )}
                          <p className="text-xl sm:text-2xl font-black text-[#991b1b] tabular-nums tracking-tight">
                            {service.effectivePrice.toLocaleString('vi-VN')} ₫
                          </p>
                          <p className="text-[12px] text-slate-600 mt-0.5">
                            {t.perGuest} <span className="text-emerald-700 font-medium">{t.insuranceIncluded}</span>
                          </p>

                          {/* Second Badge: Compact Shop Voucher Pill + Estimated Price when eligible */}
                          {service.bestVendorVoucher && (
                            <div className="mt-2.5 pt-2 border-t border-slate-200/70 space-y-1">
                              <button
                                type="button"
                                onClick={(e) => handleCopyCode(service.bestVendorVoucher!.code, e)}
                                title={isEn ? 'Click to copy shop code' : 'Nhấn để sao chép mã giảm giá'}
                                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-50 hover:bg-amber-100 border border-amber-300/80 text-amber-900 text-[11px] font-bold transition-colors max-w-full cursor-pointer"
                              >
                                <Tag className="w-3 h-3 text-amber-600 shrink-0" />
                                <span className="truncate">
                                  {copiedCode === service.bestVendorVoucher.code
                                    ? (isEn ? `Copied ${service.bestVendorVoucher.code}` : `Đã chép ${service.bestVendorVoucher.code}`)
                                    : (isEn ? service.bestVendorVoucher.shortLabelEn : service.bestVendorVoucher.shortLabelVi)}
                                </span>
                              </button>
                              {service.bestVendorVoucher.estimatedAfterVoucherPrice != null ? (
                                <p className="text-[11px] text-emerald-700 font-semibold tabular-nums leading-snug">
                                  {isEn
                                    ? `Est. ${service.bestVendorVoucher.estimatedAfterVoucherPrice.toLocaleString('vi-VN')} ₫ w/ code`
                                    : `Còn ~${service.bestVendorVoucher.estimatedAfterVoucherPrice.toLocaleString('vi-VN')} ₫ khi áp mã`}
                                </p>
                              ) : (service.bestVendorVoucher.conditionLabelVi || service.bestVendorVoucher.conditionLabelEn) ? (
                                <p className="text-[11px] text-slate-500 font-medium leading-snug">
                                  {isEn
                                    ? `Applies on orders ${service.bestVendorVoucher.conditionLabelEn}`
                                    : `Áp dụng cho ${service.bestVendorVoucher.conditionLabelVi}`}
                                </p>
                              ) : null}
                            </div>
                          )}
                        </div>

                        {/* CTA Button */}
                        <div className="w-full mt-3 sm:mt-0">
                          <button
                            type="button"
                            className="w-full py-2.5 px-4 rounded-xl bg-sky-700 hover:bg-sky-800 text-white font-bold text-[13px] sm:text-sm flex items-center justify-center gap-1.5 transition-all active:scale-[0.98] cursor-pointer"
                          >
                            <span>{t.selectBtn}</span>
                            <ArrowRight className="w-4 h-4" />
                          </button>
                        </div>

                      </div>

                    </article>
                  );
                })}

                {/* Pagination Controls */}
                {totalPages > 1 && (
                  <div className="mt-8 flex flex-col sm:flex-row items-center justify-between gap-4 bg-white rounded-2xl border border-slate-200/90 p-4 shadow-xs">
                    <span className="text-xs text-slate-500 font-medium">
                      {t.showing} <strong className="text-slate-800 font-mono">{(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredServices.length)}</strong> {t.outOf} <strong className="text-slate-800 font-mono">{filteredServices.length}</strong> {t.experiencesUnit}
                    </span>
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        disabled={currentPage === 1}
                        onClick={() => {
                          setCurrentPage((p) => Math.max(1, p - 1));
                          window.scrollTo({ top: 120, behavior: 'smooth' });
                        }}
                        className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <ChevronLeft className="w-3.5 h-3.5" />
                        <span>{t.prev}</span>
                      </button>
                      {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
                        <button
                          key={p}
                          type="button"
                          onClick={() => {
                            setCurrentPage(p);
                            window.scrollTo({ top: 120, behavior: 'smooth' });
                          }}
                          className={`w-8 h-8 rounded-xl text-xs font-mono font-bold transition-colors cursor-pointer ${
                            currentPage === p
                              ? 'bg-sky-700 text-white shadow-xs'
                              : 'text-slate-700 hover:bg-slate-100 border border-slate-200'
                          }`}
                        >
                          {p}
                        </button>
                      ))}
                      <button
                        type="button"
                        disabled={currentPage === totalPages}
                        onClick={() => {
                          setCurrentPage((p) => Math.min(totalPages, p + 1));
                          window.scrollTo({ top: 120, behavior: 'smooth' });
                        }}
                        className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{t.next}</span>
                        <ChevronRight className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* Empty state */
              <div className="bg-white rounded-2xl border border-slate-200/90 p-12 text-center shadow-xs">
                <div className="w-12 h-12 rounded-2xl bg-sky-50 text-sky-700 flex items-center justify-center mx-auto mb-3">
                  <SearchIcon className="w-6 h-6" />
                </div>
                <h3 className="font-bold text-base text-slate-900 mb-1">
                  {t.emptyTitle}
                </h3>
                <p className="text-[13px] text-slate-500 max-w-sm mx-auto mb-4">
                  {t.emptyDesc}
                </p>
                <button
                  type="button"
                  onClick={handleResetFilters}
                  className="px-4 py-2 rounded-xl bg-sky-700 text-white text-[13px] font-bold hover:bg-sky-800 transition-colors cursor-pointer"
                >
                  {t.clearFiltersBtn}
                </button>
              </div>
            )}

          </main>

        </div>

      </main>

    </div>
  );
}
