import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { FEATURED_SERVICES, MOCK_IMAGES } from "../../../mockData";
import { useWishlistStore } from "../../../store/useWishlistStore";
import type { Service } from "../../../types";

export function Wishlist() {
  const navigate = useNavigate();
  const { wishlistIds, removeFromWishlist } = useWishlistStore();
  
  // State for active filter
  const [activeFilter, setActiveFilter] = useState("all");

  // Dynamic wishlist synchronized with global store
  const wishlist = useMemo(() => {
    return FEATURED_SERVICES.filter(service => wishlistIds.includes(service.id));
  }, [wishlistIds]);

  const handleRemove = (e: React.MouseEvent, service: Service) => {
    e.stopPropagation();
    removeFromWishlist(service.id, service.name);
  };

  const handleBook = (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    navigate(`/tour/${id}`);
  };

  // Compute filtered wishlist
  const filteredWishlist = wishlist.filter(service => {
    if (activeFilter === "all") return true;
    if (activeFilter === "sup") return service.categoryId === "cat-1";
    if (activeFilter === "snorkel") return service.categoryId === "cat-3";
    if (activeFilter === "cano") return service.categoryId === "cat-2" || service.categoryId === "cat-5";
    return true;
  });

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        {/* Main Content Container */}
        <section className="max-w-[1440px] mx-auto w-full px-4 sm:px-8 lg:px-12 py-space-xl">
          {/* Header Section */}
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-lg mb-space-2xl">
            <div className="max-w-2xl flex flex-col gap-space-xs">
              <div className="flex items-center gap-space-xs text-primary font-label-sm text-label-sm uppercase tracking-wider">
                <span className="material-symbols-outlined text-[16px]">bookmark_heart</span>
                DANASEA Wishlist Collection
              </div>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                Trải nghiệm biển bạn đã lưu <span className="text-primary font-light text-headline-md font-headline-md">({wishlist.length} hoạt động)</span>
              </h1>
              <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                Lưu lại những chuyến đi mơ ước tại Đà Nẵng để theo dõi khung giờ đẹp, mức giá ưu đãi và sẵn sàng đặt lịch khi thời tiết thuận lợi.
              </p>
            </div>
            
            {/* Quick Category Pills */}
            <div className="flex items-center flex-wrap gap-space-xs">
              <button 
                onClick={() => setActiveFilter('all')}
                className={`filter-btn px-space-md py-space-xs rounded-full font-label-md text-label-md transition-all flex items-center gap-1.5 cursor-pointer ${activeFilter === 'all' ? 'bg-secondary text-on-secondary shadow-sm' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`}
              >
                <span>Tất cả</span>
                {activeFilter === 'all' && <span className="w-5 h-5 rounded-full bg-on-secondary/20 flex items-center justify-center font-label-sm text-label-sm">{wishlist.length}</span>}
              </button>
              <button 
                onClick={() => setActiveFilter('sup')}
                className={`filter-btn px-space-md py-space-xs rounded-full font-label-md text-label-md transition-all flex items-center gap-1.5 cursor-pointer ${activeFilter === 'sup' ? 'bg-secondary text-on-secondary shadow-sm' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`}
              >
                <span className="material-symbols-outlined text-[16px]">surfing</span>
                <span>Chèo SUP</span>
              </button>
              <button 
                onClick={() => setActiveFilter('snorkel')}
                className={`filter-btn px-space-md py-space-xs rounded-full font-label-md text-label-md transition-all flex items-center gap-1.5 cursor-pointer ${activeFilter === 'snorkel' ? 'bg-secondary text-on-secondary shadow-sm' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`}
              >
                <span className="material-symbols-outlined text-[16px]">scuba_diving</span>
                <span>Lặn san hô</span>
              </button>
              <button 
                onClick={() => setActiveFilter('cano')}
                className={`filter-btn px-space-md py-space-xs rounded-full font-label-md text-label-md transition-all flex items-center gap-1.5 cursor-pointer ${activeFilter === 'cano' ? 'bg-secondary text-on-secondary shadow-sm' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`}
              >
                <span className="material-symbols-outlined text-[16px]">sailing</span>
                <span>Cano & Tour đảo</span>
              </button>
            </div>
          </div>

          {/* Active Wishlist Grid */}
          {filteredWishlist.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-space-xl items-stretch">
              {filteredWishlist.map(service => {
                const image = MOCK_IMAGES.find(i => i.serviceId === service.id && i.isPrimary)?.imageUrl || 'https://via.placeholder.com/800x600';
                const servicePrice = service.price || 0;
                return (
                  <article key={service.id} className="wishlist-card flex flex-col bg-surface-container-lowest rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 group cursor-pointer" onClick={() => navigate(`/tour/${service.id}`)}>
                    <div className="relative w-full aspect-[4/3] overflow-hidden bg-surface-container">
                      <img src={image} alt={service.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out" />
                      <div className="absolute top-space-sm left-space-sm flex flex-col gap-1 items-start">
                        <span className="px-space-sm py-1 rounded-full bg-secondary/90 backdrop-blur-md text-on-secondary font-label-sm text-label-sm flex items-center gap-1 shadow-sm">
                          <span className="material-symbols-outlined text-[14px]">verified</span>
                          Bảo trợ An Toàn
                        </span>
                      </div>
                      <button 
                        aria-label="Bỏ lưu khỏi danh sách" 
                        title="Bỏ lưu khỏi danh sách yêu thích"
                        className="absolute top-space-sm right-space-sm w-10 h-10 rounded-full bg-white text-red-500 ring-2 ring-red-100 flex items-center justify-center shadow-md hover:scale-110 active:scale-95 transition-all cursor-pointer" 
                        onClick={(e) => handleRemove(e, service)} 
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[22px] text-red-500" style={{ fontVariationSettings: "'FILL' 1" }}>favorite</span>
                      </button>
                    </div>
                    <div className="p-space-lg flex flex-col flex-1 justify-between gap-space-md">
                      <div className="flex flex-col gap-space-xs">
                        <div className="flex items-center justify-between text-on-surface-variant font-label-sm text-label-sm">
                          <span className="flex items-center gap-1">
                            <span className="material-symbols-outlined text-[16px] text-secondary">location_on</span>
                            {service.locationName}
                          </span>
                          <span className="flex items-center gap-1 font-bold text-on-surface">
                            <span className="material-symbols-outlined text-[16px] text-amber-500" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                            4.9
                          </span>
                        </div>
                        <h3 className="font-headline-sm text-headline-sm text-on-surface group-hover:text-primary transition-colors leading-snug">
                          {service.name}
                        </h3>
                      </div>
                      <div className="flex items-center justify-between mt-auto">
                        <div className="flex flex-col">
                          <span className="font-label-sm text-label-sm text-on-surface-variant line-through">{(servicePrice * 1.2).toLocaleString('vi-VN')} đ</span>
                          <span className="font-headline-sm text-headline-sm text-secondary font-bold">{servicePrice.toLocaleString('vi-VN')} đ</span>
                        </div>
                        <button 
                          onClick={(e) => handleBook(e, service.id)}
                          className="px-space-md py-space-xs rounded-full bg-surface-container-low text-secondary font-label-md text-label-md hover:bg-secondary hover:text-on-secondary shadow-sm transition-all flex items-center gap-1.5 cursor-pointer" type="button"
                        >
                          <span className="material-symbols-outlined text-[18px]">calendar_month</span>
                          Đặt lịch
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="bg-surface-container-low rounded-3xl p-space-2xl md:p-space-3xl flex flex-col items-center text-center justify-center shadow-sm w-full py-16">
              <div className="relative w-32 h-32 mb-space-md flex items-center justify-center">
                <div className="absolute inset-0 rounded-full bg-secondary/5 animate-ping"></div>
                <div className="w-24 h-24 rounded-full bg-surface-container-lowest flex items-center justify-center shadow-md text-secondary">
                  <svg className="w-12 h-12" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" viewBox="0 0 48 48">
                    <path d="M4 20c4-3 8-3 12 0s8 3 12 0 8-3 12 0 4 1 4 1"></path>
                    <path d="M4 28c4-3 8-3 12 0s8 3 12 0 8-3 12 0 4 1 4 1" opacity="0.6"></path>
                    <path d="M4 36c4-3 8-3 12 0s8 3 12 0 8-3 12 0 4 1 4 1" opacity="0.3"></path>
                    <circle cx="24" cy="14" fill="#ff735c" r="5" stroke="none"></circle>
                  </svg>
                </div>
              </div>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg leading-relaxed">
                Bạn chưa lưu hoạt động nào {activeFilter !== 'all' && "trong mục này"}
              </p>
              <div className="flex flex-wrap items-center justify-center gap-space-sm">
                <button 
                  onClick={() => navigate('/')}
                  className="px-space-xl py-space-sm rounded-full bg-primary-container text-on-primary font-label-lg text-label-lg shadow-md hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer"
                >
                  Khám phá ngay
                </button>
                {activeFilter !== 'all' && (
                  <button 
                    onClick={() => setActiveFilter('all')}
                    className="px-space-md py-space-sm rounded-full bg-surface-container-lowest text-secondary hover:bg-surface-container font-label-lg text-label-lg transition-colors cursor-pointer"
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
