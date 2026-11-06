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
} from 'lucide-react';
import { useWishlistStore } from '../../../store/useWishlistStore';
import { useLanguageStore } from '../../../store/useLanguageStore';

import { serviceCatalogService, type ServiceQueryParams } from '../../../services/serviceCatalogService';
import { categoryService } from '../../../services/categoryService';
import type { CategoryDto } from '../../../types/category';
import { getCardThumbnailUrl, FALLBACK_CARD_IMAGE } from '../../../utils/imageOptimizer';
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
  promotionalPrice?: number | null;
  rating: number;
  reviewCount: number;
  location: string;
  area: 'Sơn Trà' | 'Mỹ Khê' | 'Bãi Rạng' | 'Non Nước';
  imageUrl: string;
  duration?: string;
  badge?: string;
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
  const { language } = useLanguageStore();

  const categoryParam = searchParams.get('category');
  const keywordParam = searchParams.get('q') || '';

  // Data states
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [services, setServices] = useState<DisplayExperience[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Search input state
  const [keyword, setKeyword] = useState<string>(keywordParam);

  // Interactive Query Bar Date & Pax States
  const today = useMemo(() => new Date(), []);
  const formatIsoDate = (d: Date) => d.toISOString().split('T')[0];
  const formatDisplayDate = (d: Date) => {
    return `${d.getDate().toString().padStart(2, '0')}/${(d.getMonth() + 1).toString().padStart(2, '0')}/${d.getFullYear()}`;
  };

  const [dateType, setDateType] = useState<'today' | 'tomorrow' | 'weekend' | 'custom'>('today');
  const [selectedDate, setSelectedDate] = useState<string>(() => formatIsoDate(today));
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);

  // Guests & Equipment Counts
  const [adults, setAdults] = useState<number>(2);
  const [childrenCount, setChildrenCount] = useState<number>(0);
  const [equipmentCount, setEquipmentCount] = useState<number>(1);
  const [isGuestsPickerOpen, setIsGuestsPickerOpen] = useState(false);

  const dateDisplayText = useMemo(() => {
    if (dateType === 'today') return `Hôm nay, ${formatDisplayDate(today)}`;
    if (dateType === 'tomorrow') {
      const tm = new Date(today.getTime() + 86400000);
      return `Ngày mai, ${formatDisplayDate(tm)}`;
    }
    if (dateType === 'weekend') return 'Cuối tuần này (T7/CN)';
    try {
      return `Ngày ${formatDisplayDate(new Date(selectedDate))}`;
    } catch {
      return selectedDate;
    }
  }, [dateType, selectedDate, today]);

  const guestsDisplayText = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${adults} Người lớn`);
    if (childrenCount > 0) parts.push(`${childrenCount} Trẻ em`);
    parts.push(`${equipmentCount} Cano/Ván`);
    return parts.join(', ');
  }, [adults, childrenCount, equipmentCount]);

  // Traveloka Filter States
  // 1. Popular Filters (Bộ lọc phổ biến)
  const [popFreeCancel, setPopFreeCancel] = useState<boolean>(false);
  const [popTopRated, setPopTopRated] = useState<boolean>(false);
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
  const [sortBy, setSortBy] = useState<'popular' | 'rating_desc' | 'price_asc' | 'price_desc'>('popular');

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
          ...(sortBy === 'price_asc' || sortBy === 'price_desc' || sortBy === 'rating_desc' ? { sortBy } : {}),
        };

        const response = await serviceCatalogService.getServices(queryParams);

        if (isMounted) {
          if (response && response.content && response.content.length > 0) {
            const mapped: DisplayExperience[] = response.content.map((item, idx) => {
              const areaOptions: ('Sơn Trà' | 'Mỹ Khê' | 'Bãi Rạng' | 'Non Nước')[] = [
                'Sơn Trà', 'Mỹ Khê', 'Bãi Rạng', 'Non Nước'
              ];
              const determinedArea = areaOptions[idx % 4];

              return {
                id: item.id,
                name: item.name,
                slug: item.slug,
                description: item.shortDescription || 'Trải nghiệm thể thao biển chuẩn an toàn hàng hải tại vịnh Đà Nẵng, trang bị áo phao SOLAS và hướng dẫn viên 1:1.',
                price: item.price,
                promotionalPrice: item.promotionalPrice,
                rating: item.averageRating || 4.9,
                reviewCount: item.reviewCount || 128,
                location: item.address ? item.address.split(',')[0] : `Bán đảo ${determinedArea}`,
                area: determinedArea,
                imageUrl: getCardThumbnailUrl(item.primaryImageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80'),
                duration: ('duration' in item && typeof item.duration === 'number' && item.duration > 0 ? `${item.duration} phút` : undefined) || (item.name.toLowerCase().includes('phao chuối') ? '20 phút' : '60 phút'),
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
  }, [sortBy]);

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
      result = result.filter((s) => s.price <= 500000);
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

    // Price Max filter
    if (priceMax < 2500000) {
      result = result.filter((s) => s.price <= priceMax);
    }

    // Min Rating filter
    if (minRating > 0) {
      result = result.filter((s) => s.rating >= minRating);
    }

    // Sorting
    if (sortBy === 'price_asc') {
      result.sort((a, b) => a.price - b.price);
    } else if (sortBy === 'price_desc') {
      result.sort((a, b) => b.price - a.price);
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
          TOP UNIFIED SEARCH & CONTROL STRIP (Header Query Bar with Maritime Ocean Backdrop)
         ========================================================================= */}
      <section className="relative bg-gradient-to-r from-[#031d44] via-[#022b68] to-[#013571] text-white py-6 sm:py-7 px-4 sm:px-6 lg:px-8 shadow-md overflow-hidden">
        {/* Subtle decorative maritime wave glow contours in background */}
        <div className="absolute inset-0 pointer-events-none opacity-25">
          <div className="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-sky-400/20 blur-3xl" />
          <div className="absolute -bottom-24 -right-24 w-96 h-96 rounded-full bg-cyan-400/20 blur-3xl" />
          <svg className="absolute inset-0 w-full h-full object-cover opacity-15" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 320">
            <path fill="#ffffff" fillOpacity="0.4" d="M0,192L48,197.3C96,203,192,213,288,192C384,171,480,117,576,122.7C672,128,768,192,864,202.7C960,213,1056,171,1152,144C1248,117,1344,107,1392,101.3L1440,96L1440,320L1392,320C1344,320,1248,320,1152,320C1056,320,960,320,864,320C768,320,672,320,576,320C480,320,384,320,288,320C192,320,96,320,48,320L0,320Z" />
          </svg>
        </div>

        <div className="max-w-[1440px] mx-auto relative z-10 animate-fadeInUp">
          {/* Quick Query Bar (Solid White Floating Deck) */}
          <div className="bg-white rounded-2xl border border-white/20 p-2 sm:p-2.5 flex flex-col md:flex-row items-center gap-2 text-slate-800 shadow-xl">

            {/* Field 1: Destination / Keyword */}
            <div className="flex-1 min-w-0 w-full flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100/80 focus-within:bg-white focus-within:ring-1 focus-within:ring-sky-500 border border-transparent focus-within:border-sky-500 transition-all">
              <MapPin className="w-4 h-4 text-sky-700 shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">Điểm đến / Trải nghiệm</p>
                <input
                  type="text"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="Mỹ Khê, Sơn Trà, chèo SUP, lặn biển..."
                  className="w-full bg-transparent text-[13px] sm:text-sm font-semibold text-slate-900 placeholder:text-slate-400 outline-none"
                />
              </div>
              {keyword && (
                <button type="button" onClick={() => setKeyword('')} className="text-slate-400 hover:text-slate-700 text-sm px-1 cursor-pointer">×</button>
              )}
            </div>

            {/* Field 2: Date Selector (Interactive Popover) */}
            <div className="relative w-full md:w-60 shrink-0">
              <button
                type="button"
                onClick={() => {
                  setIsDatePickerOpen((prev) => !prev);
                  setIsGuestsPickerOpen(false);
                }}
                className={`w-full flex items-center gap-2.5 px-4 py-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                  isDatePickerOpen
                    ? 'bg-white border-sky-500 ring-1 ring-sky-500 shadow-xs'
                    : 'bg-slate-50 hover:bg-slate-100/80 border-transparent'
                }`}
              >
                <Calendar className="w-4 h-4 text-sky-700 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">Ngày trải nghiệm</p>
                  <p className="text-[13px] sm:text-sm font-semibold text-slate-900 truncate">
                    {dateDisplayText}
                  </p>
                </div>
              </button>

              {/* Date Popover */}
              {isDatePickerOpen && (
                <div className="absolute top-full left-0 mt-2 z-50 w-72 p-3.5 bg-white rounded-2xl border border-slate-200 shadow-2xl animate-in fade-in slide-in-from-top-2 duration-150">
                  <p className="text-xs font-bold text-slate-800 mb-2">Chọn ngày khởi hành:</p>
                  <div className="grid grid-cols-2 gap-1.5 mb-3">
                    <button
                      type="button"
                      onClick={() => {
                        setDateType('today');
                        setSelectedDate(formatIsoDate(today));
                        setIsDatePickerOpen(false);
                      }}
                      className={`px-3 py-2 rounded-xl text-xs font-semibold text-center transition-colors cursor-pointer ${
                        dateType === 'today'
                          ? 'bg-sky-600 text-white shadow-xs'
                          : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                      }`}
                    >
                      Hôm nay
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setDateType('tomorrow');
                        const tm = new Date(today.getTime() + 86400000);
                        setSelectedDate(formatIsoDate(tm));
                        setIsDatePickerOpen(false);
                      }}
                      className={`px-3 py-2 rounded-xl text-xs font-semibold text-center transition-colors cursor-pointer ${
                        dateType === 'tomorrow'
                          ? 'bg-sky-600 text-white shadow-xs'
                          : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                      }`}
                    >
                      Ngày mai
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setDateType('weekend');
                        setIsDatePickerOpen(false);
                      }}
                      className={`px-3 py-2 rounded-xl text-xs font-semibold text-center transition-colors cursor-pointer col-span-2 ${
                        dateType === 'weekend'
                          ? 'bg-sky-600 text-white shadow-xs'
                          : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                      }`}
                    >
                      Cuối tuần này (T7 / CN)
                    </button>
                  </div>

                  <div className="pt-2 border-t border-slate-100">
                    <label className="block text-[11px] font-medium text-slate-500 mb-1">
                      Hoặc chọn ngày cụ thể:
                    </label>
                    <input
                      type="date"
                      min={formatIsoDate(today)}
                      value={selectedDate}
                      onChange={(e) => {
                        setSelectedDate(e.target.value);
                        setDateType('custom');
                        setIsDatePickerOpen(false);
                      }}
                      className="w-full px-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-xs font-semibold text-slate-800 focus:outline-none focus:ring-1 focus:ring-sky-500"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Field 3: Pax / Guests Count (Interactive Popover) */}
            <div className="relative w-full md:w-64 lg:w-72 shrink-0">
              <button
                type="button"
                onClick={() => {
                  setIsGuestsPickerOpen((prev) => !prev);
                  setIsDatePickerOpen(false);
                }}
                className={`w-full flex items-center gap-2.5 px-4 py-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                  isGuestsPickerOpen
                    ? 'bg-white border-sky-500 ring-1 ring-sky-500 shadow-xs'
                    : 'bg-slate-50 hover:bg-slate-100/80 border-transparent'
                }`}
              >
                <Users className="w-4 h-4 text-sky-700 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-[12px] text-slate-500 font-medium leading-none mb-1">Số lượng khách</p>
                  <p className="text-[13px] sm:text-sm font-semibold text-slate-900 truncate" title={guestsDisplayText}>
                    {guestsDisplayText}
                  </p>
                </div>
              </button>

              {/* Guests Popover */}
              {isGuestsPickerOpen && (
                <div className="absolute top-full left-0 md:right-0 md:left-auto mt-2 z-50 w-80 p-4 bg-white rounded-2xl border border-slate-200 shadow-2xl animate-in fade-in slide-in-from-top-2 duration-150">
                  <p className="text-xs font-bold text-slate-900 mb-3">Số lượng người tham gia & phương tiện:</p>
                  
                  {/* Stepper 1: Người lớn */}
                  <div className="flex items-center justify-between py-2 border-b border-slate-100">
                    <div>
                      <p className="text-xs font-bold text-slate-800">Người lớn</p>
                      <p className="text-[11px] text-slate-400">Từ 12 tuổi trở lên</p>
                    </div>
                    <div className="flex items-center gap-2.5">
                      <button
                        type="button"
                        disabled={adults <= 1}
                        onClick={() => setAdults((prev) => Math.max(1, prev - 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        -
                      </button>
                      <span className="w-5 text-center font-bold text-xs font-mono tabular-nums text-slate-900">
                        {adults}
                      </span>
                      <button
                        type="button"
                        disabled={adults >= 20}
                        onClick={() => setAdults((prev) => Math.min(20, prev + 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Stepper 2: Trẻ em */}
                  <div className="flex items-center justify-between py-2 border-b border-slate-100">
                    <div>
                      <p className="text-xs font-bold text-slate-800">Trẻ em</p>
                      <p className="text-[11px] text-slate-400">Dưới 12 tuổi (áo phao riêng)</p>
                    </div>
                    <div className="flex items-center gap-2.5">
                      <button
                        type="button"
                        disabled={childrenCount <= 0}
                        onClick={() => setChildrenCount((prev) => Math.max(0, prev - 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        -
                      </button>
                      <span className="w-5 text-center font-bold text-xs font-mono tabular-nums text-slate-900">
                        {childrenCount}
                      </span>
                      <button
                        type="button"
                        disabled={childrenCount >= 10}
                        onClick={() => setChildrenCount((prev) => Math.min(10, prev + 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Stepper 3: Số phương tiện */}
                  <div className="flex items-center justify-between py-2 border-b border-slate-100">
                    <div>
                      <p className="text-xs font-bold text-slate-800">Cano / Ván SUP</p>
                      <p className="text-[11px] text-slate-400">Số lượng thiết bị cần thuê</p>
                    </div>
                    <div className="flex items-center gap-2.5">
                      <button
                        type="button"
                        disabled={equipmentCount <= 1}
                        onClick={() => setEquipmentCount((prev) => Math.max(1, prev - 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        -
                      </button>
                      <span className="w-5 text-center font-bold text-xs font-mono tabular-nums text-slate-900">
                        {equipmentCount}
                      </span>
                      <button
                        type="button"
                        disabled={equipmentCount >= 10}
                        onClick={() => setEquipmentCount((prev) => Math.min(10, prev + 1))}
                        className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer font-bold text-sm"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Done Button */}
                  <div className="mt-3 flex justify-end">
                    <button
                      type="button"
                      onClick={() => setIsGuestsPickerOpen(false)}
                      className="px-4 py-1.5 rounded-xl bg-sky-700 hover:bg-sky-600 text-white text-xs font-bold cursor-pointer transition-colors"
                    >
                      Áp dụng
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Search Submit Button */}
            <button
              type="button"
              onClick={() => {
                const params: Record<string, string> = {};
                if (keyword.trim()) params.q = keyword.trim();
                if (categoryParam) params.category = categoryParam;
                setSearchParams(params);
                setIsDatePickerOpen(false);
                setIsGuestsPickerOpen(false);
              }}
              className="w-full md:w-auto px-6 py-3 rounded-xl bg-sky-700 hover:bg-sky-600 active:bg-sky-800 text-white font-bold text-[13px] sm:text-sm flex items-center justify-center gap-2 transition-all shrink-0 hover:-translate-y-0.5 shadow-md cursor-pointer"
            >
              <SearchIcon className="w-4 h-4" />
              <span>Tìm kiếm</span>
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
            <span>{mobileFilterOpen ? 'Đóng bộ lọc' : 'Bộ lọc tìm kiếm'}</span>
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
                  <h2 className="font-bold text-sm text-slate-900">Bộ lọc tìm kiếm</h2>
                </div>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={handleResetFilters}
                    className="text-[13px] font-semibold text-sky-700 hover:text-sky-800 flex items-center gap-1 cursor-pointer"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Đặt lại</span>
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
                    <span>Bộ lọc phổ biến</span>
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
                      <span>Miễn phí hủy trước 24h</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popTopRated}
                        onChange={(e) => setPopTopRated(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>Đánh giá xuất sắc (4.8+ ⭐)</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popUnder500k}
                        onChange={(e) => setPopUnder500k(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>Dưới 500.000 ₫ / khách</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popHasCoach}
                        onChange={(e) => setPopHasCoach(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>Kèm huấn luyện viên 1:1</span>
                    </label>

                    <label className="flex items-center gap-2.5 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={popHasPhotos}
                        onChange={(e) => setPopHasPhotos(e.target.checked)}
                        className="w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                      />
                      <span>Chụp ảnh / Flycam miễn phí</span>
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
                    <span>Khu vực biển (Area)</span>
                  </span>
                  {collapseArea ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseArea && (
                  <div className="space-y-2.5 pt-1 text-[13px] text-slate-700">
                    {[
                      { id: 'Sơn Trà', label: 'Bán đảo Sơn Trà', note: 'Lặn ngắm san hô & Kayak' },
                      { id: 'Mỹ Khê', label: 'Bãi biển Mỹ Khê', note: 'Chèo SUP & Dù bay cano' },
                      { id: 'Bãi Rạng', label: 'Bãi Rạng - Nam Ô', note: 'Jetski & Rạn đá rêu' },
                      { id: 'Non Nước', label: 'Vịnh Non Nước', note: 'Câu mực đêm & Lướt sóng' },
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
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">Khoảng giá tối đa</span>
                  {collapsePrice ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapsePrice && (
                  <div className="space-y-2.5 pt-1">
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="text-slate-500 font-medium">Mức trần:</span>
                      <span className="font-bold text-[#991b1b] tabular-nums">
                        {priceMax >= 2500000 ? 'Tất cả mức giá' : `${(priceMax / 1000).toLocaleString('vi-VN')}k ₫ / khách`}
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
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">Thể loại hoạt động</span>
                  {collapseCat ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseCat && (
                  <div className="space-y-2.5 pt-1 text-[13px] text-slate-700">
                    {categories.map((cat) => {
                      const slug = cat.slug || cat.id;
                      const isChecked = selectedCategorySlugs.includes(slug);

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
                            <span className="font-medium text-slate-800">{cat.name}</span>
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
                  <span className="font-bold text-[13px] sm:text-sm text-slate-900">Đánh giá của khách</span>
                  {collapseRating ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronUp className="w-4 h-4 text-slate-400" />}
                </div>

                {!collapseRating && (
                  <div className="grid grid-cols-3 gap-1.5 text-[13px] pt-1">
                    {[
                      { label: '4.8+', val: 4.8 },
                      { label: '4.5+', val: 4.5 },
                      { label: 'Tất cả', val: 0 },
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
            
            {/* Header: Kết quả tìm kiếm & Thanh sắp xếp */}
            <div className="bg-white rounded-2xl border border-slate-200/90 p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
                  {selectedAreas.length > 0
                    ? `Trải nghiệm biển tại ${selectedAreas.join(', ')}`
                    : 'Trải nghiệm thể thao biển Đà Nẵng'}
                </h1>
                <p className="text-[13px] text-slate-600 mt-1">
                  Tìm thấy <strong className="text-sky-700 tabular-nums font-bold">{filteredServices.length}</strong> trải nghiệm khả dụng
                </p>
              </div>

              {/* Sort Pills (Traveloka Tabs - Wrap on narrow viewports without triggering horizontal scroll) */}
              <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                <span className="text-[13px] text-slate-600 font-medium shrink-0 mr-1 hidden sm:inline">Sắp xếp:</span>
                {([
                  { id: 'popular', label: 'Phổ biến nhất' },
                  { id: 'rating_desc', label: 'Đánh giá cao' },
                  { id: 'price_asc', label: 'Giá thấp nhất' },
                  { id: 'price_desc', label: 'Giá cao nhất' },
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
                          alt={service.name}
                          loading="lazy"
                          onError={(e) => {
                            e.currentTarget.src = FALLBACK_CARD_IMAGE;
                          }}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                        />
                        
                        {/* Status / Badge overlay */}
                        <div className="absolute top-2.5 left-2.5">
                          <span className="px-2.5 py-1 rounded-md bg-[#031d44]/90 backdrop-blur-xs text-white text-[13px] font-bold">
                            Chuẩn an toàn SOLAS
                          </span>
                        </div>

                        {/* Wishlist Heart */}
                        <button
                          type="button"
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            toggleWishlist(service.id, service.name);
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
                            {service.name}
                          </h2>

                          {/* Rating & Review Count (Traveloka Score Pill Style) */}
                          <div className="flex items-center gap-2 mt-2">
                            <div className="inline-flex items-center gap-1 bg-sky-50 text-sky-800 px-2 py-0.5 rounded-md font-bold text-[13px] tabular-nums">
                              <Star className="w-3.5 h-3.5 fill-sky-700 text-sky-700" />
                              <span>{service.rating.toFixed(1)} / 5</span>
                            </div>
                            <span className="text-[13px] font-semibold text-slate-700">Tuyệt vời</span>
                            <span className="text-[13px] text-slate-600 tabular-nums font-medium">({service.reviewCount} đánh giá)</span>
                          </div>

                          {/* Location & Duration & Distance */}
                          <div className="flex flex-wrap items-center gap-2 text-[13px] text-slate-600 mt-2 font-medium">
                            <span className="flex items-center gap-1 font-semibold text-slate-800">
                              <MapPin className="w-3.5 h-3.5 text-sky-700" />
                              <span>{service.location}</span>
                            </span>
                            <span>•</span>
                            <span className="text-slate-500">{service.distanceFromCenter}</span>
                            <span>•</span>
                            <span className="flex items-center gap-1 text-slate-500">
                              <Clock className="w-3.5 h-3.5 text-slate-500" />
                              <span>{service.duration || '120 phút'}</span>
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
                              Áo phao đạt chuẩn
                            </span>
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-[13px] font-medium">
                              <Sparkles className="w-3.5 h-3.5 text-sky-700" />
                              HDV 1:1 bản địa
                            </span>
                            {service.hasPhotos && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-[13px] font-medium">
                                <Camera className="w-3.5 h-3.5 text-amber-600" />
                                Chụp ảnh miễn phí
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Cancellation Policy Banner */}
                        <div className="mt-3 pt-2.5 border-t border-slate-100 flex items-center gap-1.5 text-[13px] text-emerald-700 font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                          <span>Miễn phí hủy trước 24h • Hoàn tiền nếu biển động</span>
                        </div>
                      </div>

                      {/* Right: Commercial Pricing & Action Box (Traveloka Style) */}
                      <div className="p-4 sm:p-5 sm:border-l sm:border-slate-100 flex flex-col justify-between items-start w-full sm:w-56 shrink-0 bg-slate-50/50 sm:bg-transparent">
                        
                        {/* Price Display (Left-aligned within box to prevent floating button collision) */}
                        <div className="text-left w-full">
                          {service.promotionalPrice && (
                            <p className="text-[13px] text-slate-500 line-through tabular-nums font-medium">
                              {service.promotionalPrice.toLocaleString('vi-VN')} ₫
                            </p>
                          )}
                          <p className="text-xl sm:text-2xl font-black text-[#991b1b] tabular-nums tracking-tight">
                            {service.price.toLocaleString('vi-VN')} ₫
                          </p>
                          <p className="text-[13px] text-slate-600">
                            / khách <span className="text-emerald-700 font-medium">(Đã gồm bảo hiểm)</span>
                          </p>
                        </div>

                        {/* CTA Button */}
                        <div className="w-full mt-3 sm:mt-0">
                          <button
                            type="button"
                            className="w-full py-2.5 px-4 rounded-xl bg-sky-700 hover:bg-sky-800 text-white font-bold text-[13px] sm:text-sm flex items-center justify-center gap-1.5 transition-all active:scale-[0.98] cursor-pointer"
                          >
                            <span>Chọn trải nghiệm</span>
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
                      Hiển thị <strong className="text-slate-800 font-mono">{(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredServices.length)}</strong> trong tổng số <strong className="text-slate-800 font-mono">{filteredServices.length}</strong> trải nghiệm
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
                        <span>Trước</span>
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
                        <span>Sau</span>
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
                  Không tìm thấy trải nghiệm phù hợp
                </h3>
                <p className="text-[13px] text-slate-500 max-w-sm mx-auto mb-4">
                  Thử nới rộng khoảng giá hoặc bỏ bớt các tiêu chí lọc để xem thêm các hoạt động biển khác tại Đà Nẵng.
                </p>
                <button
                  type="button"
                  onClick={handleResetFilters}
                  className="px-4 py-2 rounded-xl bg-sky-700 text-white text-[13px] font-bold hover:bg-sky-800 transition-colors cursor-pointer"
                >
                  Xóa tất cả bộ lọc
                </button>
              </div>
            )}

          </main>

        </div>

      </main>

    </div>
  );
}
