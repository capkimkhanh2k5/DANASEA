import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { CinematicSceneBackdrop } from '../../../components/customer/CinematicSceneBackdrop';
import { TourCardSkeleton } from '../../../components/common/skeletons';
import { useWishlistStore } from '../../../store/useWishlistStore';
import { useLanguageStore } from '../../../store/useLanguageStore';
import { serviceCatalogService, type ServiceQueryParams } from '../../../services/serviceCatalogService';
import { categoryService } from '../../../services/categoryService';
import type { ServiceSummaryDto } from '../../../types/service';
import type { CategoryDto } from '../../../types/category';
import { FEATURED_SERVICES, CATEGORIES, MOCK_IMAGES } from '../../../mockData';

// Normalized experience item interface for seamless display
interface DisplayExperience {
  id: string;
  name: string;
  nameEn?: string;
  description: string;
  price: number;
  promotionalPrice?: number | null;
  rating: number;
  reviewCount: number;
  location: string;
  imageUrl: string;
  duration?: string;
  badge?: string;
  categoryId?: string;
}

// Category descriptions & slogans for cinematic FPT Play / Netflix-style category showcase
const CATEGORY_META: Record<string, { titleVi: string; titleEn: string; descVi: string; descEn: string; tag: string }> = {
  'cheo-sup-kayak': {
    titleVi: 'CHÈO SUP & KAYAK ĐÀ NẴNG',
    titleEn: 'STAND-UP PADDLEBOARDING & KAYAK',
    descVi: 'Lướt êm trên mặt biển phẳng lặng sớm mai, đón ánh bình minh rạng rỡ và ngắm nhìn trọn vẹn vịnh biển Đà Nẵng.',
    descEn: 'Glide peacefully across calm morning waters, embrace the sunrise and admire the pristine bay of Da Nang.',
    tag: 'Đón Bình Minh',
  },
  'lan-ngam-san-ho': {
    titleVi: 'LẶN NGẮM SAN HÔ BÁN ĐẢO SƠN TRÀ',
    titleEn: 'CORAL REEF SCUBA & SEAWALKER',
    descVi: 'Đắm mình trong làn nước pha lê, khám phá quần thể san hô đa sắc màu cùng huấn luyện viên PADI chuyên nghiệp.',
    descEn: 'Dive into crystal-clear waters, exploring vibrant coral reefs guided by certified PADI master divers.',
    tag: 'Sinh Thái Biển',
  },
  'cano-du-bay': {
    titleVi: 'CANO LƯỚT SÓNG & BAY DÙ LƯỢN BIỂN',
    titleEn: 'SPEEDBOAT & PARASAILING ADVENTURE',
    descVi: 'Bay vút lên không trung ngắm toàn cảnh bờ biển Mỹ Khê và thử thách cano cao tốc lướt sóng cực phấn khích.',
    descEn: 'Soar high above My Khe coastline and feel the rush of high-speed wave carving with maximum safety.',
    tag: 'Cảm Giác Mạnh',
  },
  'mo-to-nuoc-jetski': {
    titleVi: 'MÔ TÔ NƯỚC JETSKI CẢM GIÁC MẠNH',
    titleEn: 'JETSKI HIGH-SPEED WAVE RIDING',
    descVi: 'Tự tay cầm lái mô tô nước Yamaha công suất lớn, chinh phục từng con sóng vịnh biển với sự bảo hộ an toàn tuyệt đối.',
    descEn: 'Take the helm of high-performance Yamaha jetskis, carving dynamic waves under professional marine surveillance.',
    tag: 'Tốc Độ & Sóng',
  },
  'truot-phao-chuoi': {
    titleVi: 'TRƯỢT PHAO CHUỐI TỐC ĐỘ CAO CHO NHÓM',
    titleEn: 'GROUP BANANA BOAT WAVE RIDE',
    descVi: 'Trải nghiệm phấn khích tột độ cùng hội bạn thân: lướt nhanh trên mặt nước, tạo bọt sóng trắng xóa ngập tràn tiếng cười.',
    descEn: 'Exhilarating group ocean action: fast water-skimming turns, splashing foam and unforgettable shared laughter.',
    tag: 'Nhóm & Bạn Bè',
  },
  'du-thuyen-ngam-hoang-hon': {
    titleVi: 'DU THUYỀN HẠNG SANG NGẮM HOÀNG HÔN',
    titleEn: 'LUXURY SUNSET CATAMARAN YACHT',
    descVi: 'Hành trình lãng mạn trên vịnh Đà Nẵng: thưởng thức trà chiều, rượu vang và ngắm hoàng hôn buông xuống chân trời.',
    descEn: 'A breathtaking romantic cruise across Da Nang Bay: afternoon tea, premium wine and golden sunset on the horizon.',
    tag: 'Sang Trọng & Combo',
  },
};

export function Search() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { language, t } = useLanguageStore();
  const { toggleWishlist, isInWishlist } = useWishlistStore();

  const categoryParam = searchParams.get('category');
  const keywordParam = searchParams.get('q') || '';

  // Local state
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [services, setServices] = useState<DisplayExperience[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [keyword, setKeyword] = useState<string>(keywordParam);
  const [selectedCategorySlug, setSelectedCategorySlug] = useState<string | null>(categoryParam);
  const [priceMax, setPriceMax] = useState<number>(1500000);
  const [sortBy, setSortBy] = useState<'rating_desc' | 'price_asc' | 'price_desc' | 'popular'>('popular');

  // Staggered reveal tracker
  const [revealedIds, setRevealedIds] = useState<Set<string>>(new Set());

  // 1. Fetch categories from Backend or Fallback
  useEffect(() => {
    let isMounted = true;
    async function loadCategories() {
      try {
        const remoteCats = await categoryService.getCategories();
        if (isMounted && remoteCats && remoteCats.length > 0) {
          setCategories(remoteCats);
        } else if (isMounted) {
          // Fallback to mock categories
          const fallbackCats: CategoryDto[] = CATEGORIES.map((c, idx) => ({
            id: `a0000001-0000-0000-0000-00000000000${idx + 1}`,
            name: c.name,
            nameEn: c.name,
            slug: c.id === 'cat-1' ? 'cheo-sup-kayak' : c.id === 'cat-2' ? 'cano-du-bay' : c.id === 'cat-3' ? 'lan-ngam-san-ho' : c.id === 'cat-4' ? 'cheo-sup-kayak' : 'du-thuyen-ngam-hoang-hon',
            iconUrl: c.icon,
            isActive: true,
          }));
          setCategories(fallbackCats);
        }
      } catch (err) {
        console.warn('[Search] Lỗi nạp danh mục, sử dụng fallback:', err);
      }
    }
    loadCategories();
    return () => { isMounted = false; };
  }, [language]);

  // 2. Synchronize URL query parameter when changed
  useEffect(() => {
    setSelectedCategorySlug(categoryParam);
  }, [categoryParam]);

  // Resolve matching category entity from selectedCategorySlug
  const matchedCategory = useMemo(() => {
    if (!selectedCategorySlug) return null;
    const lower = selectedCategorySlug.toLowerCase();

    // Direct match by slug or id
    const direct = categories.find(c => c.slug?.toLowerCase() === lower || c.id === selectedCategorySlug);
    if (direct) return direct;

    // Aliases mapping (sup, diving, thrill, combo, kayak)
    if (lower === 'sup' || lower.includes('kayak')) {
      return categories.find(c => c.slug?.includes('sup') || c.slug?.includes('kayak')) || categories[0];
    }
    if (lower === 'diving' || lower.includes('lan') || lower.includes('san-ho')) {
      return categories.find(c => c.slug?.includes('lan') || c.slug?.includes('san-ho')) || categories[1];
    }
    if (lower === 'thrill' || lower.includes('cano') || lower.includes('jetski') || lower.includes('du-bay')) {
      return categories.find(c => c.slug?.includes('cano') || c.slug?.includes('jetski')) || categories[2];
    }
    if (lower === 'combo' || lower.includes('du-thuyen') || lower.includes('hoang-hon')) {
      return categories.find(c => c.slug?.includes('du-thuyen')) || categories[5];
    }

    return null;
  }, [selectedCategorySlug, categories]);

  // 3. Fetch services from Backend with fallback
  useEffect(() => {
    let isMounted = true;
    async function fetchServices() {
      setIsLoading(true);
      console.groupCollapsed('%c[Search Page] Nạp dữ liệu trải nghiệm biển...', 'color: #0284c7; font-weight: bold;');
      console.log('[Bước 1/3] Khởi tạo bộ lọc tìm kiếm:', {
        categoryId: matchedCategory?.id,
        categorySlug: selectedCategorySlug,
        keyword,
        sortBy,
      });

      try {
        const queryParams: ServiceQueryParams = {
          size: 24,
          ...(matchedCategory?.id ? { categoryId: matchedCategory.id } : {}),
          ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
          ...(sortBy === 'price_asc' || sortBy === 'price_desc' || sortBy === 'rating_desc' ? { sortBy } : {}),
        };

        const response = await serviceCatalogService.getServices(queryParams);

        if (isMounted) {
          if (response && response.content && response.content.length > 0) {
            console.log(`[Bước 2/3] Backend trả về ${response.content.length} dịch vụ trực tiếp`);
            const mapped: DisplayExperience[] = response.content.map((item, idx) => ({
              id: item.id,
              name: item.name,
              description: item.shortDescription || 'Trải nghiệm thể thao biển chuẩn an toàn hàng hải tại vịnh Đà Nẵng.',
              price: item.price,
              promotionalPrice: item.promotionalPrice,
              rating: item.averageRating || 4.95,
              reviewCount: item.reviewCount || 150,
              location: item.address ? item.address.split(',')[0] : 'Bãi biển Mỹ Khê',
              imageUrl: item.primaryImageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80',
              duration: '120 phút',
              categoryId: matchedCategory?.id,
            }));
            setServices(mapped);
          } else {
            console.log('[Bước 2/3] Backend rỗng hoặc đang chạy kiểm thử -> Sử dụng fallback thông minh từ mockData');
            // Intelligent fallback from mockData
            const filteredMock = FEATURED_SERVICES.filter(item => {
              if (matchedCategory && item.categoryId) {
                const matchesMockCat = (matchedCategory.slug?.includes('sup') && item.categoryId === 'cat-1') ||
                  (matchedCategory.slug?.includes('cano') && item.categoryId === 'cat-2') ||
                  (matchedCategory.slug?.includes('lan') && item.categoryId === 'cat-3') ||
                  (matchedCategory.slug?.includes('du-thuyen') && item.categoryId === 'cat-5');
                if (!matchesMockCat && selectedCategorySlug) return false;
              }
              if (keyword.trim()) {
                const kw = keyword.toLowerCase();
                const matchName = item.name.toLowerCase().includes(kw);
                const matchDesc = (item.description || '').toLowerCase().includes(kw);
                if (!matchName && !matchDesc) return false;
              }
              return true;
            });

            const mappedMock: DisplayExperience[] = filteredMock.map((item, idx) => {
              const img = MOCK_IMAGES.find(i => i.serviceId === item.id && i.isPrimary)?.imageUrl ||
                'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80';
              return {
                id: item.id,
                name: item.name,
                description: item.description || 'Trải nghiệm thể thao biển cao cấp tại Đà Nẵng.',
                price: item.basePrice,
                rating: item.avgRating || 4.9,
                reviewCount: item.ratingCount || 98,
                location: item.locationName,
                imageUrl: img,
                duration: `${item.durationMinutes || 90} phút`,
                categoryId: item.categoryId,
              };
            });
            setServices(mappedMock);
          }
          console.log('[Bước 3/3] Hoàn tất nạp dữ liệu trải nghiệm cho trang Search');
          console.groupEnd();
        }
      } catch (err) {
        console.groupEnd();
        console.warn('[Search] Lỗi nạp dịch vụ:', err);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    fetchServices();
    return () => { isMounted = false; };
  }, [matchedCategory, selectedCategorySlug, keyword, sortBy, language]);

  // Client-side filtering by max price & sorting if needed
  const displayServices = useMemo(() => {
    let result = [...services];
    if (priceMax < 1500000) {
      result = result.filter(s => s.price <= priceMax);
    }
    if (sortBy === 'price_asc') {
      result.sort((a, b) => a.price - b.price);
    } else if (sortBy === 'price_desc') {
      result.sort((a, b) => b.price - a.price);
    } else if (sortBy === 'rating_desc') {
      result.sort((a, b) => b.rating - a.rating);
    }
    return result;
  }, [services, priceMax, sortBy]);

  // ScrollReveal observer for smooth staggered card entrance
  useEffect(() => {
    if (isLoading || displayServices.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const id = entry.target.getAttribute('data-card-id');
            if (id) {
              setRevealedIds((prev) => new Set([...prev, id]));
            }
          }
        });
      },
      { threshold: 0.15, rootMargin: '0px 0px -50px 0px' }
    );

    const cards = document.querySelectorAll('[data-card-id]');
    cards.forEach((card) => observer.observe(card));

    return () => observer.disconnect();
  }, [isLoading, displayServices]);

  // Category change handler
  const handleSelectCategory = (slug: string | null) => {
    setSelectedCategorySlug(slug);
    if (slug) {
      setSearchParams({ category: slug });
    } else {
      setSearchParams({});
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Category metadata for showcase header
  const categoryMeta = useMemo(() => {
    if (!matchedCategory && !selectedCategorySlug) return null;
    const key = matchedCategory?.slug || selectedCategorySlug || '';
    if (CATEGORY_META[key]) return CATEGORY_META[key];

    // Fuzzy matching
    for (const [k, v] of Object.entries(CATEGORY_META)) {
      if (key.includes(k) || k.includes(key)) return v;
    }

    return {
      titleVi: matchedCategory?.name.toUpperCase() || 'DANH MỤC TRẢI NGHIỆM BIỂN',
      titleEn: matchedCategory?.nameEn?.toUpperCase() || 'MARINE EXPERIENCE CATEGORY',
      descVi: 'Khám phá tất cả các gói hoạt động thể thao biển được yêu thích nhất tại Đà Nẵng.',
      descEn: 'Explore all popular water sports and ocean adventures in Da Nang.',
      tag: 'Chuẩn An Toàn',
    };
  }, [matchedCategory, selectedCategorySlug]);

  return (
    <main className="w-full text-slate-900 min-h-screen relative overflow-x-clip bg-transparent pb-28">
      {/* Dynamic Ocean Backdrop matching Homepage */}
      <CinematicSceneBackdrop />

      <div className="relative z-20 max-w-[1520px] w-full mx-auto px-4 sm:px-6 lg:px-8 pt-24 sm:pt-28">

        {/* =========================================================================
            TRANSPARENT CINEMATIC HEADER (FPT PLAY / NETFLIX STYLE - NO CLUNKY BOX)
           ========================================================================= */}
        <section data-scene="hero" className="mb-6 select-none">
          {/* Breadcrumb Navigation - Transparent and Clean */}
          <div className="flex items-center gap-2 text-xs sm:text-sm text-white/90 drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.9)] mb-3">
            <Link
              to="/"
              className="inline-flex items-center gap-1 font-medium hover:text-cyan-300 transition-colors"
            >
              <span className="material-symbols-outlined text-[16px]">arrow_back</span>
              <span>{language === 'en' ? 'Home' : 'Trang chủ'}</span>
            </Link>
            <span className="text-white/40">/</span>
            <button
              onClick={() => handleSelectCategory(null)}
              className={`hover:text-cyan-300 transition-colors ${!selectedCategorySlug ? 'text-cyan-300 font-bold' : 'text-white/80'}`}
            >
              {language === 'en' ? 'All Activities' : 'Tất cả hoạt động'}
            </button>
            {matchedCategory && (
              <>
                <span className="text-white/40">/</span>
                <span className="text-white font-bold truncate">
                  {language === 'en' ? matchedCategory.nameEn || matchedCategory.name : matchedCategory.name}
                </span>
              </>
            )}
          </div>

          {/* Large Title Directly Over Sea Backdrop */}
          <div className="max-w-4xl">
            <h1 className="text-3xl sm:text-5xl lg:text-6xl font-black text-white tracking-tight uppercase drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_24px_rgba(0,0,0,0.7)] mb-2">
              {categoryMeta
                ? (language === 'en' ? categoryMeta.titleEn : categoryMeta.titleVi)
                : (language === 'en' ? 'EXPLORE DA NANG MARINE EXPERIENCES' : 'KHÁM PHÁ TRẢI NGHIỆM BIỂN ĐÀ NẴNG')}
            </h1>
          </div>
        </section>

        {/* =========================================================================
            UNIFIED CONTROLLER BAR: CATEGORIES + SEARCH + FILTERS IN ONE SEAMLESS CAPSULE
           ========================================================================= */}
        <section className="sticky top-20 z-30 mb-8">
          <div className="bg-white/95 dark:bg-slate-900/90 backdrop-blur-md border border-white/40 dark:border-white/10 rounded-2xl p-2.5 sm:p-3.5 shadow-[0_12px_40px_-6px_rgba(0,0,0,0.25)] flex flex-col gap-2.5">
            {/* Row 1: Category Switcher Pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-1 border-b border-slate-100 dark:border-white/10">
              <button
                type="button"
                onClick={() => handleSelectCategory(null)}
                className={`px-3.5 py-1 rounded-full text-xs font-bold transition-all shrink-0 cursor-pointer ${
                  !selectedCategorySlug
                    ? 'bg-cyan-600 text-white shadow-sm scale-105'
                    : 'bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300'
                }`}
              >
                {language === 'en' ? 'All Activities' : 'Tất cả'}
              </button>

              {categories.map((cat) => {
                const isSelected = selectedCategorySlug === cat.slug || matchedCategory?.id === cat.id;
                return (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => handleSelectCategory(cat.slug || cat.id)}
                    className={`px-3.5 py-1 rounded-full text-xs font-bold transition-all shrink-0 cursor-pointer flex items-center gap-1.5 ${
                      isSelected
                        ? 'bg-cyan-600 text-white shadow-sm scale-105'
                        : 'bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300'
                    }`}
                  >
                    <span>{language === 'en' ? cat.nameEn || cat.name : cat.name}</span>
                  </button>
                );
              })}
            </div>

            {/* Row 2: Search Input + Price Filter + Sort Dropdown */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-2.5">
              {/* Search Input Box */}
              <div className="w-full sm:flex-1 flex items-center gap-2 bg-slate-50 dark:bg-slate-800 rounded-xl px-3 py-1.5 border border-slate-200/80 dark:border-white/10 focus-within:border-cyan-500 focus-within:bg-white dark:focus-within:bg-slate-900 transition-all">
                <span className="material-symbols-outlined text-cyan-600 text-[18px]">search</span>
                <input
                  type="text"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder={language === 'en' ? 'Search by activity, location (e.g. My Khe, Son Tra)...' : 'Tìm theo tên trải nghiệm, bãi biển...'}
                  className="bg-transparent text-xs sm:text-sm text-slate-800 dark:text-white placeholder-slate-400 outline-none w-full"
                />
                {keyword && (
                  <button
                    type="button"
                    onClick={() => setKeyword('')}
                    className="text-slate-400 hover:text-slate-600 dark:hover:text-white"
                  >
                    <span className="material-symbols-outlined text-[16px]">close</span>
                  </button>
                )}
              </div>

              {/* Price & Sort Controls */}
              <div className="w-full sm:w-auto flex items-center justify-between sm:justify-end gap-2 text-xs">
                {/* Price Filter with stable width and generous slider */}
                <div className="flex items-center gap-2 bg-slate-50 dark:bg-slate-800 rounded-xl px-3 py-1.5 border border-slate-200/80 dark:border-white/10 shrink-0">
                  <span className="text-slate-500 dark:text-slate-400 shrink-0">{language === 'en' ? 'Max:' : 'Mức giá:'}</span>
                  <span className="font-bold text-cyan-700 dark:text-cyan-400 w-[64px] text-left shrink-0">
                    {priceMax >= 1500000
                      ? (language === 'en' ? 'All' : 'Tất cả')
                      : (priceMax >= 1000000
                        ? `${(priceMax / 1000000).toFixed(1).replace('.0', '')}tr ₫`
                        : `${priceMax / 1000}k ₫`)}
                  </span>
                  <input
                    type="range"
                    min="200000"
                    max="1500000"
                    step="50000"
                    value={priceMax}
                    onChange={(e) => setPriceMax(Number(e.target.value))}
                    className="w-24 sm:w-32 accent-cyan-600 cursor-pointer h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg"
                  />
                </div>

                {/* Sort Dropdown */}
                <div className="flex items-center gap-1 bg-slate-50 dark:bg-slate-800 rounded-xl px-2.5 py-1.5 border border-slate-200/80 dark:border-white/10 shrink-0">
                  <span className="text-slate-500 dark:text-slate-400">{language === 'en' ? 'Sort:' : 'Xếp:'}</span>
                  <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as any)}
                    className="bg-transparent text-slate-800 dark:text-white font-bold outline-none cursor-pointer text-xs"
                  >
                    <option value="popular" className="bg-white dark:bg-slate-900 text-slate-800 dark:text-white">
                      {language === 'en' ? 'Popular' : 'Phổ biến'}
                    </option>
                    <option value="rating_desc" className="bg-white dark:bg-slate-900 text-slate-800 dark:text-white">
                      {language === 'en' ? 'Top Rated ★' : 'Đánh giá ★'}
                    </option>
                    <option value="price_asc" className="bg-white dark:bg-slate-900 text-slate-800 dark:text-white">
                      {language === 'en' ? 'Price: Low' : 'Giá tăng dần'}
                    </option>
                    <option value="price_desc" className="bg-white dark:bg-slate-900 text-slate-800 dark:text-white">
                      {language === 'en' ? 'Price: High' : 'Giá giảm dần'}
                    </option>
                  </select>
                </div>

                {/* Reset Filter Button - Stable layout reservation */}
                <button
                  type="button"
                  onClick={() => {
                    handleSelectCategory(null);
                    setKeyword('');
                    setPriceMax(1500000);
                    setSortBy('popular');
                  }}
                  className={`text-cyan-700 dark:text-cyan-400 hover:underline font-bold px-1 cursor-pointer transition-opacity shrink-0 ${
                    (selectedCategorySlug || keyword || priceMax < 1500000 || sortBy !== 'popular')
                      ? 'opacity-100 pointer-events-auto'
                      : 'opacity-0 pointer-events-none'
                  }`}
                >
                  {language === 'en' ? 'Reset' : 'Đặt lại'}
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* =========================================================================
            EXPERIENCES RESULTS GRID WITH SMOOTH SCROLL REVEAL (60 FPS)
           ========================================================================= */}
        <section data-scene="sports">
          {isLoading ? (
            /* Shared Skeleton Loading State */
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              <TourCardSkeleton count={8} />
            </div>
          ) : displayServices.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {displayServices.map((service, idx) => {
                const isSaved = isInWishlist(service.id);
                const isRevealed = revealedIds.has(service.id);

                return (
                  <article
                    key={service.id}
                    data-card-id={service.id}
                    style={{
                      transitionDelay: `${(idx % 4) * 75}ms`,
                    }}
                    className={`group flex flex-col bg-white rounded-2xl overflow-hidden border border-slate-200/90 shadow-[0_4px_20px_-2px_rgba(16,47,58,0.06)] hover:shadow-[0_20px_40px_-4px_rgba(16,47,58,0.14)] hover:-translate-y-2 hover:border-cyan-400 transition-all duration-400 ease-out cursor-pointer ${
                      isRevealed
                        ? 'opacity-100 translate-y-0'
                        : 'opacity-0 translate-y-8'
                    }`}
                  >
                    {/* Image Box */}
                    <div
                      className="relative aspect-[4/3] overflow-hidden bg-slate-100"
                      onClick={() => navigate(`/tour/${service.id}`)}
                    >
                      <img
                        src={service.imageUrl}
                        alt={service.name}
                        loading="lazy"
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-600 ease-out pointer-events-none"
                      />

                      {/* Optical subtle scrim on image bottom */}
                      <div className="absolute inset-0 bg-gradient-to-t from-slate-950/60 via-transparent to-transparent pointer-events-none" />



                      {/* Top Right Wishlist Heart */}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          toggleWishlist(service.id, service.name);
                        }}
                        aria-label="Toggle wishlist"
                        className="absolute top-2.5 right-2.5 w-8 h-8 rounded-full bg-white/90 backdrop-blur-md border border-white/60 flex items-center justify-center hover:scale-110 active:scale-95 transition-transform shadow-md cursor-pointer z-10"
                      >
                        <span className={`text-base ${isSaved ? 'text-rose-500' : 'text-slate-600'}`}>
                          {isSaved ? '♥' : '♡'}
                        </span>
                      </button>

                      {/* Bottom Info on Image */}
                      <div className="absolute bottom-2.5 left-2.5 right-2.5 flex items-center justify-between text-white text-xs font-medium">
                        <span className="flex items-center gap-1 drop-shadow-md">
                          <span className="material-symbols-outlined text-[14px] text-cyan-300">location_on</span>
                          <span className="truncate">{service.location}</span>
                        </span>
                        {service.duration && (
                          <span className="flex items-center gap-1 drop-shadow-md">
                            <span className="material-symbols-outlined text-[14px] text-amber-300">schedule</span>
                            <span>{service.duration}</span>
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Content Box */}
                    <div
                      className="p-4 flex-1 flex flex-col justify-between text-slate-900"
                      onClick={() => navigate(`/tour/${service.id}`)}
                    >
                      <div>
                        {/* Rating Row */}
                        <div className="flex items-center justify-between mb-1.5 text-xs">
                          <div className="flex items-center gap-1 text-amber-500 font-bold">
                            <span>★</span>
                            <span className="text-slate-800 font-semibold">{service.rating.toFixed(2)}</span>
                            <span className="text-slate-400 font-normal">({service.reviewCount})</span>
                          </div>
                          <span className="text-[11px] text-cyan-700 font-semibold uppercase tracking-wider">
                            {language === 'en' ? 'Maritime Verified' : 'Chuẩn An Toàn'}
                          </span>
                        </div>

                        {/* Title */}
                        <h3 className="font-bold text-base leading-snug line-clamp-2 text-slate-900 group-hover:text-cyan-700 transition-colors">
                          {service.name}
                        </h3>

                        {/* Description */}
                        <p className="mt-1.5 text-xs text-slate-500 line-clamp-2 leading-relaxed">
                          {service.description}
                        </p>
                      </div>

                      {/* Price & Action Row */}
                      <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between">
                        <div>
                          <div className="text-xs text-slate-400">
                            {language === 'en' ? 'From' : 'Đơn giá từ'}
                          </div>
                          <div className="flex items-baseline gap-1">
                            <span className="font-black text-lg text-rose-600">
                              {service.price.toLocaleString('vi-VN')}₫
                            </span>
                            <span className="text-xs text-slate-400">
                              /{language === 'en' ? 'guest' : 'khách'}
                            </span>
                          </div>
                        </div>

                        {/* Booking Details Arrow Button */}
                        <span className="w-9 h-9 rounded-full bg-cyan-50 text-cyan-700 flex items-center justify-center group-hover:bg-cyan-600 group-hover:text-white transition-all duration-200">
                          <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                        </span>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            /* Empty State */
            <div className="bg-white/90 backdrop-blur-md border border-white/60 rounded-3xl p-12 text-center max-w-xl mx-auto my-12 shadow-xl">
              <span className="material-symbols-outlined text-cyan-600 text-[64px] mb-3">search_off</span>
              <h2 className="text-xl font-bold text-slate-900 mb-2">
                {language === 'en' ? 'No marine experiences found' : 'Không tìm thấy trải nghiệm phù hợp'}
              </h2>
              <p className="text-sm text-slate-500 mb-6 leading-relaxed">
                {language === 'en'
                  ? 'Try expanding your price range or clearing keyword filters to view other exciting ocean adventures.'
                  : 'Hãy thử nới rộng mức giá lọc hoặc xóa từ khóa tìm kiếm để khám phá các hoạt động biển hấp dẫn khác.'}
              </p>
              <button
                type="button"
                onClick={() => {
                  handleSelectCategory(null);
                  setKeyword('');
                  setPriceMax(1500000);
                  setSortBy('popular');
                }}
                className="px-6 py-2.5 rounded-full bg-gradient-to-r from-cyan-600 to-teal-600 text-white font-bold text-sm shadow-md shadow-cyan-600/30 hover:scale-105 active:scale-95 transition-transform cursor-pointer"
              >
                {language === 'en' ? 'Show All Experiences' : 'Xem tất cả trải nghiệm biển'}
              </button>
            </div>
          )}
        </section>

      </div>
    </main>
  );
}

export default Search;
