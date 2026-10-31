import { useState, useMemo, useRef, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  Heart,
  Star,
  MapPin,
  Calendar,
  Compass,
  ArrowRight,
  ArrowLeft,
} from "lucide-react";
import { FEATURED_SERVICES, MOCK_IMAGES } from "../../../mockData";
import {
  FLASH_SALE_EXPERIENCES,
  SUP_KAYAK_EXPERIENCES,
  DIVING_EXPERIENCES,
  THRILL_EXPERIENCES,
  COMBO_SUNSET_EXPERIENCES,
} from "../../../data/customerHomeData";
import { useWishlistStore } from "../../../store/useWishlistStore";
import { useAuthStore } from "../../../store/useAuthStore";
import { serviceCatalogService } from "../../../services/serviceCatalogService";
import type { ServiceSummaryDto } from "../../../types/service";

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
  const { wishlistIds, serverItems, removeFromWishlist, fetchWishlistFromServer } = useWishlistStore();
  const { isAuthenticated } = useAuthStore();
  const [catalogServices, setCatalogServices] = useState<ServiceSummaryDto[]>([]);
  const [isLoadingCatalog, setIsLoadingCatalog] = useState(true);

  useEffect(() => {
    if (isAuthenticated) {
      fetchWishlistFromServer();
    }
  }, [isAuthenticated, fetchWishlistFromServer]);

  useEffect(() => {
    let isMounted = true;
    serviceCatalogService.getServices({ size: 100 })
      .then((res) => {
        if (isMounted) {
          setCatalogServices(res.content || []);
        }
      })
      .catch((err) => {
        console.warn('[Wishlist] Không thể tải danh mục từ backend:', err);
      })
      .finally(() => {
        if (isMounted) setIsLoadingCatalog(false);
      });
    return () => {
      isMounted = false;
    };
  }, []);
  
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

  // Compile all known experiences across live backend catalog, server wishlist, and mock fallbacks
  const allAvailableServices = useMemo<WishlistItem[]>(() => {
    const items: WishlistItem[] = [];
    const seenIds = new Set<string>();

    // 0. From live Backend catalog (highest priority: real photos, prices, ratings)
    catalogServices.forEach((s) => {
      if (!seenIds.has(s.id)) {
        seenIds.add(s.id);
        const p = s.price || 0;
        items.push({
          id: s.id,
          name: s.name,
          category: 'Thể thao biển Đà Nẵng',
          locationName: s.address || 'Đà Nẵng',
          price: p,
          originalPrice: Math.round(p * 1.15),
          imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80',
          rating: s.averageRating && s.averageRating > 0 ? Number(s.averageRating.toFixed(1)) : 4.9,
        });
      }
    });

    // 0.1 From server synced wishlist items
    serverItems.forEach((si) => {
      if (!seenIds.has(si.serviceId)) {
        seenIds.add(si.serviceId);
        items.push({
          id: si.serviceId,
          name: si.serviceName,
          category: 'Đã lưu trên máy chủ',
          locationName: 'Đà Nẵng',
          price: 0,
          originalPrice: 0,
          imageUrl: si.primaryImageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80',
          rating: 4.9,
        });
      }
    });

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
  }, [catalogServices, serverItems]);

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
    navigate(`/experience/${id}`);
  };

  // Compute filtered wishlist
  const filteredWishlist = useMemo(() => {
    if (activeFilter === "all") return wishlist;
    return wishlist.filter(item => matchesCategory(item.categoryId || item.category, activeFilter));
  }, [wishlist, activeFilter]);

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        {/* Main Content Container - Airbnb Max Width 1280px */}
        <section className="max-w-[1280px] mx-auto w-full px-4 sm:px-6 lg:px-8 py-8">
          {/* Header & Category Strip Section */}
          <div className="flex flex-col gap-5 mb-8">
            {/* Navigation Breadcrumb */}
            <div className="flex items-center gap-2 text-[13px] text-slate-500 font-medium">
              <button 
                onClick={() => navigate('/profile')} 
                className="hover:text-slate-900 transition-colors cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1 text-slate-600 font-semibold"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>Tài khoản của bạn</span>
              </button>
              <span className="text-slate-300">/</span>
              <span className="text-slate-900 font-semibold">Danh sách tour đã lưu</span>
            </div>

            <div className="flex items-baseline justify-between border-b border-slate-200/80 pb-4">
              <div>
                <h1 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                  Danh sách yêu thích
                </h1>
                <p className="text-sm text-slate-600 mt-1">
                  {wishlist.length} trải nghiệm đã lưu vào bộ sưu tập
                </p>
              </div>
            </div>

            {/* Quick Category Strip - Airbnb clean line style */}
            <div className="relative w-full flex items-center group">
              {/* Left Scroll Button */}
              {canScrollLeft && (
                <div className="absolute left-0 top-0 bottom-0 z-10 flex items-center pr-4 bg-gradient-to-r from-white via-white/90 to-transparent">
                  <button
                    type="button"
                    onClick={() => handleScroll('left')}
                    aria-label="Cuộn sang trái"
                    className="w-8 h-8 rounded-full bg-white shadow-sm border border-slate-200 text-slate-700 hover:text-slate-900 hover:border-slate-400 flex items-center justify-center transition-all cursor-pointer"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                </div>
              )}

              {/* Scrollable Category Tabs */}
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
                      className={`px-3.5 py-2 rounded-xl text-xs sm:text-sm font-medium transition-all duration-200 whitespace-nowrap shrink-0 cursor-pointer ${
                        isActive
                          ? "bg-slate-900 text-white shadow-xs"
                          : "bg-slate-50 text-slate-700 hover:text-slate-900 hover:bg-slate-100 border border-slate-200/80"
                      }`}
                    >
                      {cat.name}
                    </button>
                  );
                })}
              </div>

              {/* Right Scroll Button */}
              {canScrollRight && (
                <div className="absolute right-0 top-0 bottom-0 z-10 flex items-center pl-4 bg-gradient-to-l from-white via-white/90 to-transparent">
                  <button
                    type="button"
                    onClick={() => handleScroll('right')}
                    aria-label="Cuộn sang phải"
                    className="w-8 h-8 rounded-full bg-white shadow-sm border border-slate-200 text-slate-700 hover:text-slate-900 hover:border-slate-400 flex items-center justify-center transition-all cursor-pointer"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Active Wishlist Grid - Airbnb Property Cards */}
          {isLoadingCatalog && wishlistIds.length > 0 && wishlist.length === 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 items-stretch">
              {[1, 2, 3].map((idx) => (
                <div key={idx} className="flex flex-col bg-white rounded-2xl overflow-hidden border border-slate-200/80 shadow-xs animate-pulse">
                  <div className="w-full aspect-[4/3] bg-slate-100" />
                  <div className="p-4 flex flex-col flex-1 justify-between gap-3">
                    <div className="space-y-2">
                      <div className="h-4 bg-slate-100 rounded w-1/3" />
                      <div className="h-5 bg-slate-100 rounded w-4/5" />
                    </div>
                    <div className="pt-3 border-t border-slate-100 flex justify-between items-center">
                      <div className="h-5 bg-slate-100 rounded w-1/4" />
                      <div className="h-8 bg-slate-100 rounded-xl w-24" />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : filteredWishlist.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 items-stretch">
              {filteredWishlist.map((service) => {
                const servicePrice = service.price || 0;
                return (
                  <article
                    key={service.id}
                    className="flex flex-col bg-white rounded-2xl overflow-hidden border border-slate-200/80 shadow-xs hover:shadow-md transition-all duration-200 group cursor-pointer hover:-translate-y-0.5"
                    onClick={() => navigate(`/experience/${service.id}`)}
                  >
                    {/* Card Photo & Actions */}
                    <div className="relative w-full aspect-[4/3] overflow-hidden bg-slate-100">
                      <img
                        src={service.imageUrl}
                        alt={service.name}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                      />
                      
                      {/* Floating Guest Favorite / Category Badge */}
                      <div className="absolute top-3 left-3 bg-white/95 backdrop-blur-xs px-2.5 py-1 rounded-md text-[11px] font-bold text-slate-900 border border-slate-200/70 shadow-xs">
                        {service.category || 'Trải nghiệm biển'}
                      </div>

                      {/* Heart Button - Airbnb Circular Icon */}
                      <button 
                        aria-label="Bỏ lưu khỏi danh sách" 
                        title="Bỏ lưu khỏi danh sách yêu thích"
                        className="absolute top-3 right-3 w-8 h-8 rounded-full bg-white/90 backdrop-blur-xs hover:bg-white border border-slate-200/80 shadow-xs flex items-center justify-center hover:scale-105 active:scale-95 transition-all cursor-pointer" 
                        onClick={(e) => handleRemove(e, service)} 
                        type="button"
                      >
                        <Heart className="w-4 h-4 text-rose-500 fill-rose-500" />
                      </button>
                    </div>

                    {/* Card Body - Airbnb Typography */}
                    <div className="p-4 flex flex-col flex-1 justify-between gap-3">
                      <div className="flex flex-col gap-1.5">
                        <div className="flex items-center justify-between text-xs text-slate-600">
                          <span className="flex items-center gap-1 truncate max-w-[200px]">
                            <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            <span className="truncate">{service.locationName}</span>
                          </span>
                          <span className="flex items-center gap-1 font-bold text-slate-900 shrink-0">
                            <Star className="w-3.5 h-3.5 text-amber-500 fill-amber-500" />
                            <span>{service.rating}</span>
                          </span>
                        </div>
                        
                        <h3 className="text-base font-semibold text-slate-900 group-hover:text-slate-700 transition-colors line-clamp-2 leading-snug">
                          {service.name}
                        </h3>
                      </div>

                      {/* Card Footer: Price & Reserve Button */}
                      <div className="flex items-center justify-between pt-3 border-t border-slate-100 mt-auto">
                        <div className="flex flex-col">
                          {service.originalPrice > servicePrice && (
                            <span className="text-[11px] text-slate-400 line-through">
                              {service.originalPrice.toLocaleString('vi-VN')}₫
                            </span>
                          )}
                          <div className="flex items-baseline gap-1">
                            <span className="text-base font-bold text-slate-900 tabular-nums">
                              {servicePrice.toLocaleString('vi-VN')}₫
                            </span>
                            <span className="text-xs text-slate-500 font-normal">
                              / người
                            </span>
                          </div>
                        </div>

                        <button 
                          onClick={(e) => handleBook(e, service.id)}
                          className="px-3.5 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-xs shadow-xs transition-colors flex items-center gap-1.5 cursor-pointer active:scale-95"
                          type="button"
                        >
                          <Calendar className="w-3.5 h-3.5" />
                          Đặt lịch
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="bg-white border border-slate-200/80 rounded-2xl p-8 md:p-12 flex flex-col items-center text-center justify-center shadow-xs w-full py-16">
              <div className="w-12 h-12 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-center mb-4 text-slate-400">
                <Compass className="w-6 h-6" />
              </div>
              <h2 className="text-lg font-bold text-slate-900 mb-1">
                Chưa có trải nghiệm nào được lưu
              </h2>
              <p className="text-slate-600 text-sm max-w-md mb-6 leading-relaxed">
                Khám phá các tour cano, lặn biển và chèo SUP tại Đà Nẵng để lưu lại trải nghiệm yêu thích cho chuyến đi của bạn.
              </p>
              <button 
                onClick={() => navigate('/')}
                className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-sm shadow-xs transition-all flex items-center gap-2 cursor-pointer"
              >
                <span>Khám phá ngay</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
