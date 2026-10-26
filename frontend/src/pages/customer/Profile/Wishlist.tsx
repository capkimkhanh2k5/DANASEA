import { useState, useMemo, useRef, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { FEATURED_SERVICES, MOCK_IMAGES } from "../../../mockData";
import {
  FLASH_SALE_EXPERIENCES,
  SUP_KAYAK_EXPERIENCES,
  DIVING_EXPERIENCES,
  THRILL_EXPERIENCES,
  COMBO_SUNSET_EXPERIENCES,
} from "../../../data/customerHomeData";
import { useWishlistStore } from "../../../store/useWishlistStore";

interface WishlistItem {
  id: string;
  name: string;
  categoryId?: string;
  category?: string;
  locationName: string;
  price: number;
  originalPrice: number;
  imageUrl: string;
  rating: number;
}

const CATEGORY_FILTERS = [
  { id: 'all', name: 'Tất cả' },
  { id: 'cheo-sup-kayak', name: 'Chèo SUP & Kayak' },
  { id: 'lan-ngam-san-ho', name: 'Lặn ngắm san hô' },
  { id: 'cano-du-bay', name: 'Cano & Dù bay' },
  { id: 'mo-to-nuoc-jetski', name: 'Mô tô nước Jetski' },
  { id: 'du-thuyen-ngam-hoang-hon', name: 'Du thuyền & Tour đảo' },
  { id: 'cau-ca-muc', name: 'Câu cá & Câu mực' },
  { id: 'hoc-luot-song', name: 'Lướt sóng' },
  { id: 'truot-phao-chuoi', name: 'Trượt phao chuối' },
];

function matchesCategory(itemCategory: string | undefined, categoryId: string): boolean {
  if (categoryId === 'all') return true;
  if (!itemCategory) return false;
  const val = itemCategory.toLowerCase();

  switch (categoryId) {
    case 'cheo-sup-kayak':
      return val.includes('sup') || val.includes('kayak') || val === 'cat-1' || val === 'cat-4';
    case 'lan-ngam-san-ho':
      return val.includes('lặn') || val.includes('san hô') || val.includes('diving') || val.includes('snorkel') || val === 'cat-3';
    case 'cano-du-bay':
      return (val.includes('cano') && !val.includes('san hô')) || val.includes('dù bay') || val.includes('parasailing') || val === 'cat-2';
    case 'mo-to-nuoc-jetski':
      return val.includes('jetski') || val.includes('mô tô') || val.includes('moto');
    case 'du-thuyen-ngam-hoang-hon':
      return val.includes('du thuyền') || val.includes('yacht') || val.includes('tour đảo') || val.includes('hoàng hôn') || val === 'cat-5';
    case 'cau-ca-muc':
      return val.includes('câu cá') || val.includes('câu mực') || val.includes('fishing');
    case 'hoc-luot-song':
      return val.includes('lướt sóng') || val.includes('surf');
    case 'truot-phao-chuoi':
      return val.includes('phao chuối') || val.includes('banana');
    default:
      return val === categoryId.toLowerCase();
  }
}

export function Wishlist() {
  const navigate = useNavigate();
  const { wishlistIds, removeFromWishlist } = useWishlistStore();
  
  // State for active filter
  const [activeFilter, setActiveFilter] = useState("all");

  // Single-row horizontal scroll navigation
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const checkScroll = useCallback(() => {
    if (!scrollRef.current) return;
    const { scrollLeft, scrollWidth, clientWidth } = scrollRef.current;
    setCanScrollLeft(scrollLeft > 4);
    setCanScrollRight(scrollLeft + clientWidth < scrollWidth - 4);
  }, []);

  useEffect(() => {
    checkScroll();
    const el = scrollRef.current;
    if (!el) return;
    el.addEventListener('scroll', checkScroll, { passive: true });
    window.addEventListener('resize', checkScroll);
    return () => {
      el.removeEventListener('scroll', checkScroll);
      window.removeEventListener('resize', checkScroll);
    };
  }, [checkScroll]);

  const handleScroll = (direction: 'left' | 'right') => {
    if (!scrollRef.current) return;
    const distance = 280;
    scrollRef.current.scrollBy({
      left: direction === 'left' ? -distance : distance,
      behavior: 'smooth',
    });
  };

  // Compile all known experiences across mock data & home catalog
  const allAvailableServices = useMemo<WishlistItem[]>(() => {
    const items: WishlistItem[] = [];
    const seenIds = new Set<string>();

    // 1. From FEATURED_SERVICES
    FEATURED_SERVICES.forEach(s => {
      if (!seenIds.has(s.id)) {
        seenIds.add(s.id);
        const img = MOCK_IMAGES.find(i => i.serviceId === s.id && i.isPrimary)?.imageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80';
        const p = s.basePrice || s.price || 0;
        items.push({
          id: s.id,
          name: s.name,
          categoryId: s.categoryId,
          category: s.categoryId === 'cat-1' ? 'Chèo SUP' : s.categoryId === 'cat-2' ? 'Cano lướt sóng' : 'Lặn san hô',
          locationName: s.locationName || 'Đà Nẵng',
          price: p,
          originalPrice: Math.round(p * 1.2),
          imageUrl: img,
          rating: 4.9,
        });
      }
    });

    // 2. From customer home data carousels
    const homeSets = [
      ...(FLASH_SALE_EXPERIENCES || []).map(f => ({
        id: f.id,
        name: f.title,
        category: f.category,
        locationName: f.location,
        price: f.salePrice || f.originalPrice,
        originalPrice: f.originalPrice,
        imageUrl: f.imageUrl,
        rating: f.rating || 4.9,
      })),
      ...(SUP_KAYAK_EXPERIENCES || []).map(a => ({
        id: a.id,
        name: a.title,
        category: a.category,
        locationName: a.location,
        price: a.price,
        originalPrice: Math.round(a.price * 1.2),
        imageUrl: a.imageUrl,
        rating: a.rating || 4.9,
      })),
      ...(DIVING_EXPERIENCES || []).map(a => ({
        id: a.id,
        name: a.title,
        category: a.category,
        locationName: a.location,
        price: a.price,
        originalPrice: Math.round(a.price * 1.2),
        imageUrl: a.imageUrl,
        rating: a.rating || 4.9,
      })),
      ...(THRILL_EXPERIENCES || []).map(a => ({
        id: a.id,
        name: a.title,
        category: a.category,
        locationName: a.location,
        price: a.price,
        originalPrice: Math.round(a.price * 1.2),
        imageUrl: a.imageUrl,
        rating: a.rating || 4.9,
      })),
      ...(COMBO_SUNSET_EXPERIENCES || []).map(a => ({
        id: a.id,
        name: a.title,
        category: a.category,
        locationName: a.location,
        price: a.price,
        originalPrice: Math.round(a.price * 1.2),
        imageUrl: a.imageUrl,
        rating: a.rating || 4.9,
      })),
    ];

    homeSets.forEach(h => {
      if (!seenIds.has(h.id)) {
        seenIds.add(h.id);
        items.push({
          id: h.id,
          name: h.name,
          category: h.category,
          locationName: h.locationName,
          price: h.price,
          originalPrice: h.originalPrice,
          imageUrl: h.imageUrl,
          rating: h.rating,
        });
      }
    });

    return items;
  }, []);

  // Synchronized wishlist matching saved IDs
  const wishlist = useMemo(() => {
    return allAvailableServices.filter(item => wishlistIds.includes(item.id));
  }, [allAvailableServices, wishlistIds]);

  const handleRemove = (e: React.MouseEvent, item: WishlistItem) => {
    e.stopPropagation();
    removeFromWishlist(item.id, item.name);
  };

  const handleBook = (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    navigate(`/tour/${id}`);
  };

  // Compute filtered wishlist
  const filteredWishlist = useMemo(() => {
    if (activeFilter === "all") return wishlist;
    return wishlist.filter(item => matchesCategory(item.categoryId || item.category, activeFilter));
  }, [wishlist, activeFilter]);

  return (
    <main className="w-full pt-20 bg-slate-50/50 min-h-screen flex-1">
      <div className="flex flex-col w-full">
        {/* Main Content Container */}
        <section className="max-w-[1440px] mx-auto w-full px-4 sm:px-8 lg:px-12 py-8">
          {/* Header & Category Filter Section */}
          <div className="flex flex-col gap-4 mb-8">
            {/* Title only */}
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
              Hoạt động đã lưu
            </h1>

            {/* Quick Category Filter Pills - 1 row with navigation buttons */}
            <div className="relative w-full flex items-center group">
              {/* Left Scroll Button */}
              {canScrollLeft && (
                <div className="absolute left-0 top-0 bottom-0 z-10 flex items-center pr-6 bg-gradient-to-r from-slate-50 via-slate-50/90 to-transparent">
                  <button
                    type="button"
                    onClick={() => handleScroll('left')}
                    aria-label="Cuộn sang trái"
                    className="w-8 h-8 rounded-full bg-white shadow-md border border-slate-200 text-slate-700 hover:text-cyan-600 hover:scale-105 active:scale-95 flex items-center justify-center transition-all cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
                  </button>
                </div>
              )}

              {/* Scrollable Pills Container (Strictly 1 row: flex-nowrap) */}
              <div
                ref={scrollRef}
                className="flex items-center gap-2 overflow-x-auto py-1 scroll-smooth flex-nowrap w-full scrollbar-none"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
              >
                {CATEGORY_FILTERS.map((cat) => {
                  const isActive = activeFilter === cat.id;
                  return (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => setActiveFilter(cat.id)}
                      className={`px-4 py-2 rounded-full text-sm font-medium transition-all duration-200 whitespace-nowrap shrink-0 cursor-pointer ${
                        isActive
                          ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/25 ring-1 ring-cyan-500/30"
                          : "bg-white text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-slate-200/80"
                      }`}
                    >
                      {cat.name}
                    </button>
                  );
                })}
              </div>

              {/* Right Scroll Button */}
              {canScrollRight && (
                <div className="absolute right-0 top-0 bottom-0 z-10 flex items-center pl-6 bg-gradient-to-l from-slate-50 via-slate-50/90 to-transparent">
                  <button
                    type="button"
                    onClick={() => handleScroll('right')}
                    aria-label="Cuộn sang phải"
                    className="w-8 h-8 rounded-full bg-white shadow-md border border-slate-200 text-slate-700 hover:text-cyan-600 hover:scale-105 active:scale-95 flex items-center justify-center transition-all cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Active Wishlist Grid */}
          {filteredWishlist.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 items-stretch">
              {filteredWishlist.map(service => {
                const servicePrice = service.price || 0;
                return (
                  <article
                    key={service.id}
                    className="flex flex-col bg-white rounded-2xl overflow-hidden border border-slate-200/80 shadow-sm hover:shadow-lg transition-all duration-300 group cursor-pointer"
                    onClick={() => navigate(`/tour/${service.id}`)}
                  >
                    <div className="relative w-full aspect-[4/3] overflow-hidden bg-slate-100">
                      <img
                        src={service.imageUrl}
                        alt={service.name}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                      />
                      <button 
                        aria-label="Bỏ lưu khỏi danh sách" 
                        title="Bỏ lưu khỏi danh sách yêu thích"
                        className="absolute top-3 right-3 w-9 h-9 rounded-full bg-white/50 backdrop-blur-md hover:bg-white/75 border border-white/50 shadow-sm flex items-center justify-center hover:scale-110 active:scale-95 transition-all cursor-pointer group/heart" 
                        onClick={(e) => handleRemove(e, service)} 
                        type="button"
                      >
                        <svg 
                          className="w-5 h-5 text-rose-500 fill-rose-500 drop-shadow-sm transition-transform duration-200 group-hover/heart:scale-110" 
                          viewBox="0 0 24 24" 
                          fill="currentColor"
                        >
                          <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                        </svg>
                      </button>
                    </div>

                    <div className="p-5 flex flex-col flex-1 justify-between gap-4">
                      <div className="flex flex-col gap-2">
                        <div className="flex items-center justify-between text-slate-500 text-xs">
                          <span className="flex items-center gap-1">
                            <span className="material-symbols-outlined text-[16px] text-cyan-600">location_on</span>
                            {service.locationName}
                          </span>
                          <span className="flex items-center gap-1 font-semibold text-slate-700">
                            <span className="material-symbols-outlined text-[16px] text-amber-500" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                            {service.rating}
                          </span>
                        </div>
                        <h3 className="text-base font-semibold text-slate-900 group-hover:text-cyan-600 transition-colors leading-snug line-clamp-2">
                          {service.name}
                        </h3>
                      </div>

                      <div className="flex items-center justify-between pt-3 border-t border-slate-100 mt-auto">
                        <div className="flex flex-col">
                          <span className="text-xs text-slate-400 line-through">
                            {service.originalPrice.toLocaleString('vi-VN')} đ
                          </span>
                          <span className="text-lg font-bold text-cyan-700">
                            {servicePrice.toLocaleString('vi-VN')} đ
                          </span>
                        </div>
                        <button 
                          onClick={(e) => handleBook(e, service.id)}
                          className="px-4 py-2 rounded-full bg-cyan-50 text-cyan-700 hover:bg-cyan-600 hover:text-white border border-cyan-200/80 font-medium text-xs shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[16px]">calendar_month</span>
                          Đặt lịch
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="bg-white/80 backdrop-blur-sm border border-slate-200/80 rounded-2xl p-8 md:p-12 flex flex-col items-center text-center justify-center shadow-sm w-full py-16">
              <div className="w-16 h-16 rounded-full bg-cyan-50 text-cyan-600 flex items-center justify-center mb-4">
                <span className="material-symbols-outlined text-3xl text-cyan-600">favorite_border</span>
              </div>
              <p className="text-slate-500 text-sm md:text-base max-w-md mb-6 leading-relaxed">
                Bạn chưa lưu hoạt động nào {activeFilter !== 'all' && "trong mục này"}
              </p>
              <div className="flex flex-wrap items-center justify-center gap-3">
                <button 
                  onClick={() => navigate('/')}
                  className="px-6 py-2.5 rounded-full bg-gradient-to-r from-cyan-600 to-blue-600 text-white font-medium text-sm shadow-sm hover:from-cyan-700 hover:to-blue-700 transition-all cursor-pointer"
                >
                  Khám phá ngay
                </button>
                {activeFilter !== 'all' && (
                  <button 
                    onClick={() => setActiveFilter('all')}
                    className="px-5 py-2.5 rounded-full bg-slate-100 text-slate-700 hover:bg-slate-200 font-medium text-sm transition-colors cursor-pointer"
                  >
                    Xem tất cả danh sách đã lưu
                  </button>
                )}
              </div>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
