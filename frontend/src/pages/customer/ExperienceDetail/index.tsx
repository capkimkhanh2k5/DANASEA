import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useCartStore } from '../../../store/useCartStore';
import { useWishlistStore } from '../../../store/useWishlistStore';
import { serviceCatalogService } from '../../../services/serviceCatalogService';
import type { ServiceDetailDto, ServiceOptionDto, StructuredSlotDto } from '../../../types/service';
import { FEATURED_SERVICES, MOCK_SLOTS, MOCK_IMAGES } from '../../../mockData';

export function ExperienceDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [service, setService] = useState<ServiceDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<boolean>(false);

  // Booking selections
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<string>('');
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
  const [qty, setQty] = useState<number>(1);
  const [isWaiverChecked, setIsWaiverChecked] = useState<boolean>(false);
  const [isAdding, setIsAdding] = useState<boolean>(false);
  const [showAddSuccess, setShowAddSuccess] = useState<boolean>(false);

  const addItem = useCartStore((state) => state.addItem);
  const cartItems = useCartStore((state) => state.items);

  const { toggleWishlist, isInWishlist } = useWishlistStore();

  // Gallery slider state for the large main image
  const [activeImageIndex, setActiveImageIndex] = useState<number>(0);
  const [isHoveringGallery, setIsHoveringGallery] = useState<boolean>(false);

  // Lightbox Modal state for full-screen image preview
  const [isLightboxOpen, setIsLightboxOpen] = useState<boolean>(false);
  const [lightboxIndex, setLightboxIndex] = useState<number>(0);

  const handleOpenLightbox = (index: number) => {
    setLightboxIndex(index);
    setIsLightboxOpen(true);
  };

  // Fetch Service Detail from Backend API (with graceful fallback to mock if legacy ID)
  useEffect(() => {
    if (!id) {
      setLoadError(true);
      setIsLoading(false);
      return;
    }

    let isMounted = true;
    setIsLoading(true);
    setLoadError(false);

    serviceCatalogService
      .getServiceDetail(id)
      .then((data) => {
        if (!isMounted) return;
        setService(data);

        // Auto-select first option
        if (data.options && data.options.length > 0) {
          setSelectedOptionId(data.options[0].id);
        }

        // Auto-select first available date and slot
        if (data.slots && data.slots.length > 0) {
          const firstSlot = data.slots[0];
          setSelectedDate(firstSlot.date);
          setSelectedSlotId(firstSlot.slotId);
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        console.warn(`[ExperienceDetail] Không thể tải tour ${id} từ Backend, kiểm tra mock data:`, err);
        
        // Fallback for legacy demo ID (e.g. '1', '2'...)
        const fallback = FEATURED_SERVICES.find((s) => s.id === id);
        if (fallback) {
          const fallbackSlots: StructuredSlotDto[] = MOCK_SLOTS
            .filter((ms) => ms.serviceId === fallback.id)
            .map((ms) => ({
              slotId: ms.id,
              date: ms.date,
              startTime: ms.startTime,
              endTime: ms.endTime,
              capacity: ms.capacity,
              availableCapacity: Math.max(0, ms.capacity - ms.bookedCount),
              price: fallback.basePrice,
              bookable: ms.capacity - ms.bookedCount > 0,
            }));

          const fallbackImages: string[] = MOCK_IMAGES
            .filter((mi) => mi.serviceId === fallback.id)
            .map((mi) => mi.imageUrl || mi.url || '')
            .filter((url): url is string => Boolean(url));

          const mappedFallback: ServiceDetailDto = {
            id: fallback.id,
            name: fallback.name,
            description: fallback.description || '',
            price: fallback.basePrice,
            address: fallback.locationName,
            averageRating: 4.95,
            reviewCount: 128,
            viewCount: 1250,
            categoryId: 'a0000001-0000-0000-0000-000000000001',
            categoryName: 'Thể thao biển',
            imageUrls: fallbackImages.length > 0 ? fallbackImages : ['https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800'],
            options: [
              {
                id: `opt-${fallback.id}`,
                serviceId: fallback.id,
                name: 'Vé Tiêu Chuẩn',
                optionType: 'SHARED',
                pricingUnit: 'PER_PERSON',
                price: fallback.basePrice,
                maxPaxPerPackage: 1,
                benefits: 'Áo phao, người hướng dẫn, nước uống, bảo hiểm du lịch.',
                status: 'ACTIVE',
              },
            ],
            slots: fallbackSlots,
            vendorId: '22222222-2222-2222-2222-222222222221',
            businessName: 'DANASEA Official Partner',
            badgeTier: 'VERIFIED',
            duration: fallback.durationMinutes || 120,
            capacity: fallback.capacityPerSlot || 15,
            waiverRequired: true,
            waiverContent: fallback.waiverContent || 'Khách hàng cam kết đủ sức khỏe tham gia, bắt buộc mặc áo phao và tuân thủ hướng dẫn an toàn biển.',
            cancellationPolicy: 'Hoàn tiền 100% trước 24 giờ. Hoàn tiền 100% nếu biển động theo quy định cảng vụ.',
          };

          setService(mappedFallback);
          if (mappedFallback.options.length > 0) setSelectedOptionId(mappedFallback.options[0].id);
          if (mappedFallback.slots.length > 0) {
            setSelectedDate(mappedFallback.slots[0].date);
            setSelectedSlotId(mappedFallback.slots[0].slotId);
          }
        } else {
          setLoadError(true);
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [id]);

  // Derived selected option
  const selectedOption: ServiceOptionDto | undefined = useMemo(() => {
    if (!service || !service.options) return undefined;
    return service.options.find((opt) => opt.id === selectedOptionId) || service.options[0];
  }, [service, selectedOptionId]);

  // Unique dates from slots
  const availableDates: string[] = useMemo(() => {
    if (!service || !service.slots || service.slots.length === 0) return [];
    const dateSet = new Set<string>();
    service.slots.forEach((s) => dateSet.add(s.date));
    return Array.from(dateSet).sort();
  }, [service]);

  // Slots for the currently chosen date
  const slotsForSelectedDate: StructuredSlotDto[] = useMemo(() => {
    if (!service || !service.slots || !selectedDate) return [];
    return service.slots.filter((s) => s.date === selectedDate);
  }, [service, selectedDate]);

  // Active slot object
  const activeSlot: StructuredSlotDto | undefined = useMemo(() => {
    if (!slotsForSelectedDate) return undefined;
    return slotsForSelectedDate.find((s) => s.slotId === selectedSlotId) || slotsForSelectedDate[0];
  }, [slotsForSelectedDate, selectedSlotId]);

  // Pricing calculation
  const unitPrice = selectedOption ? selectedOption.price : (service?.price || 0);
  const totalPrice = qty * unitPrice;

  // Capacity validation
  const maxCapacity = activeSlot ? activeSlot.availableCapacity : 10;
  const isSoldOut = activeSlot ? activeSlot.availableCapacity <= 0 : false;

  const isItemInCart = useMemo(() => {
    if (!service || !activeSlot) return false;
    return cartItems.some(
      (item) => item.serviceId === service.id && item.slotId === activeSlot.slotId
    );
  }, [cartItems, service?.id, activeSlot?.slotId]);

  // Pure Vietnamese normalizers for demo/seed data
  const localizedSafetyRules = useMemo(() => {
    const raw = service?.safetyRules || '';
    if (raw.toLowerCase().includes('max wave') || raw.toLowerCase().includes('weather-sensitive')) {
      const wave = raw.match(/max wave:\s*([\d.]+m)/i)?.[1] || '0.80m';
      const wind = raw.match(/max wind:\s*([\d.]+\s*km\/h)/i)?.[1] || '5.00km/h';
      return `Sóng biển ≤ ${wave}, gió ≤ ${wind}`;
    }
    if (raw.includes('Bắt buộc') || raw.includes('áo phao')) {
      return raw.split('.')[0].trim();
    }
    return raw || 'Sóng biển ≤ 0.80m, gió ≤ 5.00km/h';
  }, [service?.safetyRules]);

  const localizedWaiverContent = useMemo(() => {
    const raw = service?.waiverContent || '';
    if (raw.toLowerCase().includes('participants must confirm') || !raw) {
      return 'Khách hàng cam kết đủ sức khỏe tham gia, bắt buộc mặc áo phao trong suốt thời gian trên biển và tuân thủ chỉ dẫn an toàn của huấn luyện viên.';
    }
    return raw;
  }, [service?.waiverContent]);

  const allImages: string[] = useMemo(() => {
    if (service?.imageUrls && service.imageUrls.length > 0) {
      return service.imageUrls;
    }
    return ['https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800'];
  }, [service?.imageUrls]);

  // Auto-play loop every 3.5 seconds in circular cycle (pauses when hovering)
  useEffect(() => {
    if (allImages.length <= 1 || isHoveringGallery) return;

    const timer = setInterval(() => {
      setActiveImageIndex((prev) => (prev + 1) % allImages.length);
    }, 3500);

    return () => clearInterval(timer);
  }, [allImages.length, isHoveringGallery]);

  const handlePrevImage = (e: React.MouseEvent) => {
    e.stopPropagation();
    setActiveImageIndex((prev) => (prev - 1 + allImages.length) % allImages.length);
  };

  const handleNextImage = (e: React.MouseEvent) => {
    e.stopPropagation();
    setActiveImageIndex((prev) => (prev + 1) % allImages.length);
  };

  // Keyboard navigation & body scroll lock for Lightbox Modal
  useEffect(() => {
    if (!isLightboxOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsLightboxOpen(false);
      if (e.key === 'ArrowLeft') setLightboxIndex((prev) => (prev - 1 + allImages.length) % allImages.length);
      if (e.key === 'ArrowRight') setLightboxIndex((prev) => (prev + 1) % allImages.length);
    };

    window.addEventListener('keydown', handleKeyDown);
    document.body.style.overflow = 'hidden';

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
    };
  }, [isLightboxOpen, allImages.length]);

  const handleAddToCart = () => {
    if (!service) return;
    if (!activeSlot) {
      alert('Vui lòng chọn khung giờ khởi hành.');
      return;
    }
    if (service.waiverRequired && !isWaiverChecked) {
      alert('Vui lòng đồng ý với Điều khoản an toàn & miễn trừ trách nhiệm trước khi đặt vé.');
      return;
    }

    setIsAdding(true);
    setTimeout(() => {
      addItem({
        serviceId: service.id,
        slotId: activeSlot.slotId,
        optionId: selectedOption?.id || activeSlot.optionId,
        locationName: service.address || 'Đà Nẵng',
        vendorName: service.businessName || 'DANASEA Official Partner',
        quantity: qty,
        unitPrice: unitPrice,
        serviceName: `${service.name}${selectedOption ? ` (${selectedOption.name})` : ''}`,
        thumbnailUrl: (service.imageUrls && service.imageUrls.length > 0) ? service.imageUrls[0] : 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
        date: activeSlot.date,
        timeSlot: `${activeSlot.startTime} - ${activeSlot.endTime}`,
        selectedDate: activeSlot.date,
      });
      setIsAdding(false);
      setShowAddSuccess(true);
      setTimeout(() => setShowAddSuccess(false), 3000);
    }, 400);
  };

  // 1. Loading State (Transparent Ocean Glass Skeleton)
  if (isLoading) {
    return (
      <main className="w-full pt-28 pb-20 bg-slate-900/10 min-h-screen flex flex-col items-center">
        <div className="max-w-[1440px] w-full px-4 sm:px-8 lg:px-12 animate-pulse space-y-8">
          <div className="h-6 bg-slate-200/70 rounded-md w-1/4"></div>
          <div className="h-10 bg-slate-200/70 rounded-xl w-3/4"></div>
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 h-[420px]">
            <div className="lg:col-span-7 bg-slate-200/70 rounded-2xl"></div>
            <div className="lg:col-span-5 grid grid-cols-2 gap-4">
              <div className="bg-slate-200/70 rounded-2xl"></div>
              <div className="bg-slate-200/70 rounded-2xl"></div>
              <div className="bg-slate-200/70 rounded-2xl"></div>
              <div className="bg-slate-200/70 rounded-2xl"></div>
            </div>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
            <div className="lg:col-span-8 space-y-4">
              <div className="h-40 bg-slate-200/70 rounded-2xl"></div>
              <div className="h-40 bg-slate-200/70 rounded-2xl"></div>
            </div>
            <div className="lg:col-span-4 h-96 bg-slate-200/70 rounded-2xl"></div>
          </div>
        </div>
      </main>
    );
  }

  // 2. Not Found / Error State
  if (loadError || !service) {
    return (
      <main className="w-full pt-32 pb-20 bg-slate-50 flex-1 flex flex-col items-center justify-center min-h-[70vh]">
        <div className="w-20 h-20 rounded-full bg-cyan-50 border border-cyan-100 flex items-center justify-center text-cyan-600 mb-5 shadow-sm">
          <span className="material-symbols-outlined text-[48px]">search_off</span>
        </div>
        <h2 className="text-2xl font-bold text-slate-800">Không tìm thấy trải nghiệm</h2>
        <p className="text-slate-500 mt-2 text-center max-w-md">
          Trải nghiệm bạn đang tìm kiếm không tồn tại, chưa được mở bán hoặc đã bị gỡ khỏi hệ thống.
        </p>
        <button
          onClick={() => navigate('/search')}
          className="mt-6 px-8 py-3 bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white rounded-full transition-all shadow-md hover:shadow-cyan-500/20 font-semibold cursor-pointer active:scale-95"
        >
          Về trang Khám phá
        </button>
      </main>
    );
  }

  const isSaved = isInWishlist(service.id);

  return (
    <main className="w-full pt-20 bg-slate-50/60 flex-1">
      <div className="flex flex-col w-full">
        {/* Breadcrumb Navigation */}
        <div className="w-full bg-white/70 backdrop-blur-md border-b border-slate-200/60">
          <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-8 lg:px-12 py-3">
            <nav className="flex items-center gap-2 text-xs sm:text-sm text-slate-500 overflow-x-auto overflow-y-hidden scrollbar-none whitespace-nowrap py-0.5">
              <span
                onClick={() => navigate('/')}
                className="hover:text-cyan-700 transition-colors flex items-center gap-1 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px]">home</span>
                Trang chủ
              </span>
              <span className="text-slate-300">/</span>
              <span
                onClick={() => navigate('/search')}
                className="hover:text-cyan-700 transition-colors cursor-pointer"
              >
                Khám phá
              </span>
              <span className="text-slate-300">/</span>
              {service.categoryName && (
                <>
                  <span className="text-slate-600 font-medium">{service.categoryName}</span>
                  <span className="text-slate-300">/</span>
                </>
              )}
              <span className="text-slate-900 font-bold truncate max-w-[280px] sm:max-w-md">{service.name}</span>
            </nav>
          </div>
        </div>

        <div className="max-w-[1440px] mx-auto w-full px-4 sm:px-8 lg:px-12 pt-5 pb-8 flex flex-col gap-5">
          
          {/* Title & Quick Actions Header */}
          <section className="flex flex-col lg:flex-row lg:items-end justify-between gap-4">
            <div className="flex flex-col gap-2 flex-1 min-w-0 pr-4">
              <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-slate-900 tracking-tight leading-snug">
                {service.name}
              </h1>
              <div className="flex flex-wrap items-center gap-y-2 gap-x-4 text-xs sm:text-sm text-slate-600">
                <div className="flex items-center gap-1 text-slate-900 font-semibold">
                  <span className="text-amber-500">★</span>
                  <span>{service.averageRating ? service.averageRating.toFixed(2) : '5.0'}</span>
                  <span className="text-slate-500 font-normal">({service.reviewCount || 100} đánh giá)</span>
                </div>
                <span className="text-slate-300">•</span>
                <div className="flex items-center gap-1 text-slate-700">
                  <span className="material-symbols-outlined text-[18px] text-cyan-600">schedule</span>
                  <span>{service.duration || 120} phút</span>
                </div>
                <span className="text-slate-300">•</span>
                <div className="flex items-center gap-1 text-slate-700">
                  <span className="material-symbols-outlined text-[18px] text-cyan-600">visibility</span>
                  <span>{service.viewCount || 1500} lượt xem</span>
                </div>
                {service.address && (
                  <>
                    <span className="text-slate-300">•</span>
                    <div className="flex items-center gap-1 text-slate-700 truncate max-w-xs sm:max-w-md">
                      <span className="material-symbols-outlined text-[18px] text-cyan-600 shrink-0">location_on</span>
                      <span className="truncate">{service.address}</span>
                    </div>
                  </>
                )}
              </div>
            </div>

            {/* Wishlist Quick Action */}
            <div className="flex items-center gap-3 self-start lg:self-center shrink-0">
              <button
                type="button"
                onClick={() => toggleWishlist(service.id, service.name)}
                className={`px-4 py-2.5 rounded-full text-xs sm:text-sm font-semibold flex items-center gap-2 border transition-all shadow-xs cursor-pointer hover:scale-105 active:scale-95 ${
                  isSaved
                    ? 'bg-rose-50 border-rose-200 text-rose-600 ring-2 ring-rose-100'
                    : 'bg-white border-slate-200 text-slate-700 hover:border-rose-300 hover:text-rose-600'
                }`}
              >
                <span className={`text-base ${isSaved ? 'text-rose-500' : 'text-slate-400'}`}>
                  {isSaved ? '♥' : '♡'}
                </span>
                <span>{isSaved ? 'Đã lưu yêu thích' : 'Lưu vào yêu thích'}</span>
              </button>
            </div>
          </section>

          {/* Lưu ý an toàn biển (Thanh mỏng trải dài ngang bằng ảnh, gọn theo chiều dọc, đúng 1 dòng) */}
          <div className="w-full flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-amber-50/80 border border-amber-200/70 text-amber-900 text-xs shadow-2xs">
            <span className="material-symbols-outlined text-amber-600 text-[16px] shrink-0">health_and_safety</span>
            <p className="leading-tight">
              <strong className="font-semibold text-amber-950">Lưu ý an toàn:</strong> Bắt buộc mặc áo phao, đủ sức khỏe tham gia và tuân thủ huấn luyện viên. Hoàn 100% nếu hủy trước 24h hoặc do biển động theo lệnh cảng vụ.
            </p>
          </div>

          {/* Main Content Layout: Left (Photos + Info), Right (Sticky Booking Card pushed to top) */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
            
            {/* LEFT COLUMN: Photos & Detailed Experience Intel */}
            <main className="lg:col-span-8 flex flex-col gap-4">
              
              {/* Photo Gallery: Main Carousel + Small Thumbnail Strip Directly Below */}
              <section className="w-full bg-white p-2 sm:p-2.5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col gap-2.5">
                {/* Large Main Carousel Image with Auto-play, Zoom Popup & Prev/Next Navigation */}
                <div
                  onClick={() => handleOpenLightbox(activeImageIndex)}
                  className="relative group overflow-hidden rounded-xl h-[320px] sm:h-[420px] bg-black select-none cursor-pointer"
                  onMouseEnter={() => setIsHoveringGallery(true)}
                  onMouseLeave={() => setIsHoveringGallery(false)}
                  title="Bấm vào để phóng to xem ảnh"
                >
                  {/* Smooth Sliding Carousel Track */}
                  <div
                    className="flex w-full h-full transition-transform duration-500 ease-out"
                    style={{ transform: `translateX(-${activeImageIndex * 100}%)` }}
                  >
                    {allImages.map((imgUrl, imgIdx) => (
                      <div key={imgIdx} className="w-full h-full shrink-0 relative overflow-hidden">
                        <img
                          src={imgUrl}
                          alt={`${service.name} - ${imgIdx + 1}`}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-out"
                          loading={imgIdx === 0 ? 'eager' : 'lazy'}
                        />
                      </div>
                    ))}
                  </div>

                  {/* Zoom Hint Icon - Slim & Elegant SVG */}
                  <div className="absolute top-3 right-3 w-6 h-6 rounded-full bg-black/40 backdrop-blur-xs border border-white/15 text-white/90 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity z-10 pointer-events-none shadow-xs">
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <circle cx="10.5" cy="10.5" r="6.5" strokeWidth="1.25" />
                      <path d="M15.5 15.5L20 20" strokeWidth="1.25" strokeLinecap="round" />
                      <path d="M10.5 7.5v6M7.5 10.5h6" strokeWidth="1.25" strokeLinecap="round" />
                    </svg>
                  </div>

                  {/* Previous Button on Main Image - Always visible, dimmed & inert if only 1 image */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (allImages.length > 1) {
                        handlePrevImage(e);
                      }
                    }}
                    aria-label="Ảnh trước"
                    className={`absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/50 text-white backdrop-blur-xs border border-white/10 flex items-center justify-center transition-all shadow-md z-10 ${
                      allImages.length <= 1
                        ? 'opacity-35 cursor-not-allowed'
                        : 'hover:bg-black/80 opacity-90 sm:opacity-0 sm:group-hover:opacity-100 cursor-pointer active:scale-90'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
                  </button>

                  {/* Next Button on Main Image - Always visible, dimmed & inert if only 1 image */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (allImages.length > 1) {
                        handleNextImage(e);
                      }
                    }}
                    aria-label="Ảnh sau"
                    className={`absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/50 text-white backdrop-blur-xs border border-white/10 flex items-center justify-center transition-all shadow-md z-10 ${
                      allImages.length <= 1
                        ? 'opacity-35 cursor-not-allowed'
                        : 'hover:bg-black/80 opacity-90 sm:opacity-0 sm:group-hover:opacity-100 cursor-pointer active:scale-90'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
                  </button>

                  {/* Compact Pure Black Counter Badge - Always visible */}
                  <div className="absolute bottom-3 right-3 px-2 py-0.5 rounded-full bg-black/75 backdrop-blur-xs text-white/95 text-[10px] font-medium tracking-wide shadow-xs z-10 pointer-events-none">
                    {activeImageIndex + 1} / {allImages.length}
                  </div>

                  {/* Indicator Dots - Always visible */}
                  <div
                    onClick={(e) => e.stopPropagation()}
                    className="absolute bottom-3 left-1/2 -translate-x-1/2 flex items-center gap-1.5 z-10"
                  >
                    {allImages.map((_, dotIdx) => (
                      <button
                        key={dotIdx}
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          if (allImages.length > 1) {
                            setActiveImageIndex(dotIdx);
                          }
                        }}
                        className={`transition-all duration-300 rounded-full ${
                          allImages.length <= 1 ? 'cursor-default' : 'cursor-pointer'
                        } ${
                          activeImageIndex === dotIdx
                            ? 'w-5 h-1.5 bg-white shadow-xs'
                            : 'w-1.5 h-1.5 bg-white/40 hover:bg-white/70'
                        }`}
                        aria-label={`Chuyển tới ảnh ${dotIdx + 1}`}
                      />
                    ))}
                  </div>
                </div>

                {/* Dải ảnh phụ nhỏ ngay bên dưới ảnh chính kèm 2 nút trái phải */}
                <div className="relative flex items-center gap-1 w-full pt-1 px-0.5">
                  {/* Nút mũi tên trái cho dải ảnh phụ - Bỏ nền, không viền, thanh thoát */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (allImages.length > 1) handlePrevImage(e);
                    }}
                    aria-label="Dải ảnh trước"
                    className={`w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center shrink-0 transition-all rounded-full ${
                      allImages.length <= 1
                        ? 'text-slate-300 opacity-40 cursor-not-allowed'
                        : 'text-slate-600 hover:text-slate-950 hover:bg-slate-100 active:scale-90 cursor-pointer'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
                  </button>

                  {/* Dải các thẻ ảnh phụ nhỏ */}
                  <div className="flex-1 flex items-center gap-2 overflow-x-auto py-1 scrollbar-none scroll-smooth">
                    {allImages.map((imgUrl, idx) => {
                      const isCurrentActive = activeImageIndex === idx;
                      return (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => setActiveImageIndex(idx)}
                          className={`relative shrink-0 w-20 h-14 sm:w-24 sm:h-16 rounded-xl overflow-hidden border-2 transition-all cursor-pointer ${
                            isCurrentActive
                              ? 'ring-2 ring-cyan-500 border-transparent shadow-xs scale-102'
                              : 'border-slate-200/80 hover:border-slate-300 opacity-75 hover:opacity-100'
                          }`}
                          aria-label={`Xem ảnh ${idx + 1}`}
                        >
                          <img
                            src={imgUrl}
                            alt={`${service.name} - ${idx + 1}`}
                            className="w-full h-full object-cover pointer-events-none"
                            loading="lazy"
                          />
                        </button>
                      );
                    })}
                  </div>

                  {/* Nút mũi tên phải cho dải ảnh phụ - Bỏ nền, không viền, thanh thoát */}
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (allImages.length > 1) handleNextImage(e);
                    }}
                    aria-label="Dải ảnh sau"
                    className={`w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center shrink-0 transition-all rounded-full ${
                      allImages.length <= 1
                        ? 'text-slate-300 opacity-40 cursor-not-allowed'
                        : 'text-slate-600 hover:text-slate-950 hover:bg-slate-100 active:scale-90 cursor-pointer'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
                  </button>
                </div>
              </section>

              {/* Mô tả trải nghiệm & Thông tin vận hành (Gộp vào 1 ô nội dung duy nhất) */}
              <section className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-3.5">
                <div>
                  <h2 className="text-xl font-bold text-slate-900 mb-1.5">Mô tả trải nghiệm</h2>
                  <p className="text-slate-700 leading-relaxed text-[15px] whitespace-pre-line">
                    {service.description}
                  </p>
                </div>

                <div className="pt-3.5 border-t border-slate-100 space-y-2.5">
                  <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">Thông tin vận hành</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2 text-xs sm:text-sm">
                    <div className="flex items-baseline gap-2 text-slate-600">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Thời lượng:</span>
                      <span className="font-normal text-slate-800">{service.duration || 120} phút / ca</span>
                    </div>
                    <div className="flex items-baseline gap-2 text-slate-600">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Sức chứa:</span>
                      <span className="font-normal text-slate-800">Tối đa {service.capacity || 15} người / ca</span>
                    </div>
                    <div className="flex items-baseline gap-2 text-slate-600 sm:col-span-2">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Điều kiện thời tiết:</span>
                      <span className="font-normal text-slate-800">{localizedSafetyRules}</span>
                    </div>
                    {service.businessName && (
                      <div className="flex items-baseline gap-2 text-slate-600 sm:col-span-2 min-w-0">
                        <span className="text-slate-500 whitespace-nowrap shrink-0">Đơn vị tổ chức:</span>
                        <span className="font-normal text-slate-800 truncate">{service.businessName}</span>
                        {service.badgeTier && (
                          <span className="text-[11px] font-medium text-teal-800 bg-teal-50 border border-teal-200/70 px-2 py-0.5 rounded-full whitespace-nowrap shrink-0">
                            {service.badgeTier === 'VERIFIED' ? 'Đã xác minh' : service.badgeTier}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </section>

              {/* Service Options (Gói dịch vụ) */}
              {service.options && service.options.length > 0 && (
                <section className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-xl font-bold text-slate-900">Lựa chọn gói dịch vụ</h2>
                  </div>
                  <div className="grid grid-cols-1 gap-2.5">
                    {service.options.map((opt) => {
                      const isSelected = selectedOptionId === opt.id;
                      return (
                        <div
                          key={opt.id}
                          onClick={() => setSelectedOptionId(opt.id)}
                          className={`p-3.5 rounded-xl border-2 transition-all cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                            isSelected
                              ? 'bg-cyan-50/50 border-cyan-500 shadow-sm'
                              : 'bg-slate-50/60 border-slate-200/70 hover:border-slate-300'
                          }`}
                        >
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-slate-900 text-sm sm:text-base">{opt.name}</span>
                              <span className="px-2 py-0.5 rounded-md bg-white border border-slate-200 text-[11px] font-medium text-slate-600">
                                {opt.optionType === 'PRIVATE' ? 'Bao trọn gói' : 'Vé ghép'}
                              </span>
                            </div>
                            {opt.benefits && (
                              <p className="text-xs text-slate-600 leading-normal">{opt.benefits}</p>
                            )}
                          </div>
                          <div className="flex items-baseline gap-1.5 sm:justify-end shrink-0 whitespace-nowrap">
                            <span className="font-extrabold text-base sm:text-lg text-cyan-700">
                              {opt.price.toLocaleString('vi-VN')}₫
                            </span>
                            <span className="text-xs text-slate-500 font-medium">
                              {opt.pricingUnit === 'PER_PERSON' ? '/ người' : '/ gói'}
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}
            </main>

            {/* RIGHT COLUMN: Sticky Interactive Booking Box */}
            <aside className="lg:col-span-4 w-full lg:sticky lg:top-24">
              <div className="w-full p-5 sm:p-6 rounded-2xl bg-white border border-slate-200/80 shadow-lg flex flex-col justify-between min-h-[528px]">
                
                {/* Upper Booking Configuration (Price, Date, Time Slots, Qty, Waiver) */}
                <div className="flex flex-col gap-4">
                  {/* Price Display */}
                  <div className="border-b border-slate-100 pb-3 flex items-baseline gap-1.5 flex-wrap">
                    <span className="text-3xl font-extrabold text-cyan-700 tracking-tight">
                      {unitPrice.toLocaleString('vi-VN')}₫
                    </span>
                    <span className="text-xs text-slate-500 font-medium whitespace-nowrap">
                      {selectedOption?.pricingUnit === 'PER_PACKAGE' ? '/ gói' : '/ người'}
                    </span>
                  </div>

                  {/* Date Selection */}
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs uppercase tracking-wider text-slate-600 font-bold">Ngày khởi hành</label>
                    {availableDates.length > 0 ? (
                      <select
                        value={selectedDate}
                        onChange={(e) => {
                          const newDate = e.target.value;
                          setSelectedDate(newDate);
                          const slotsOnDate = service?.slots?.filter((s) => s.date === newDate) || [];
                          const firstBookable = slotsOnDate.find((s) => s.bookable && s.availableCapacity > 0) || slotsOnDate[0];
                          setSelectedSlotId(firstBookable ? firstBookable.slotId : null);
                        }}
                        className="w-full p-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-800 text-sm font-medium cursor-pointer hover:bg-slate-100/80 transition-colors outline-none focus:ring-2 focus:ring-cyan-500"
                      >
                        {availableDates.map((d) => (
                          <option key={d} value={d}>
                            {d === new Date().toISOString().split('T')[0] ? `Hôm nay (${d})` : d}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <div className="p-3 rounded-xl bg-slate-50 text-xs text-slate-500">
                        Chưa có lịch mở bán cho dịch vụ này
                      </div>
                    )}
                  </div>

                  {/* Structured Slots Selection */}
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs uppercase tracking-wider text-slate-600 font-bold">Khung giờ khởi hành</label>
                    <div className="flex flex-col gap-2">
                      {slotsForSelectedDate.length === 0 ? (
                        <p className="text-slate-500 text-xs text-center py-2 bg-slate-50 rounded-xl">
                          Không có ca giờ nào trong ngày này.
                        </p>
                      ) : (
                        slotsForSelectedDate.map((slot) => {
                          const isSelected = activeSlot?.slotId === slot.slotId;
                          const remain = slot.availableCapacity;
                          const isFull = remain <= 0 || !slot.bookable;

                          return (
                            <div
                              key={slot.slotId}
                              onClick={() => !isFull && setSelectedSlotId(slot.slotId)}
                              className={`p-3 rounded-xl border-2 transition-all cursor-pointer flex items-center justify-between ${
                                isSelected
                                  ? 'bg-cyan-50/70 border-cyan-500 shadow-xs'
                                  : isFull
                                  ? 'bg-slate-100 border-transparent opacity-50 cursor-not-allowed'
                                  : 'bg-slate-50 border-slate-200/80 hover:bg-slate-100/80'
                              }`}
                            >
                              <div className="flex flex-col">
                                <span className={`text-sm font-bold ${isSelected ? 'text-cyan-800' : 'text-slate-800'}`}>
                                  {slot.startTime} - {slot.endTime}
                                </span>
                                <span className="text-[11px] text-slate-500 mt-0.5">
                                  {isFull ? 'Đã hết chỗ' : `Còn ${remain} chỗ trống (Tổng ${slot.capacity})`}
                                </span>
                              </div>
                              {isSelected && (
                                <span className="material-symbols-outlined text-cyan-600 text-lg">check_circle</span>
                              )}
                            </div>
                          );
                        })
                      )}
                    </div>
                  </div>

                  {/* Quantity */}
                  <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                    <div className="flex flex-col">
                      <span className="text-sm font-bold text-slate-800">Số lượng người</span>
                      {activeSlot && (
                        <span className="text-[11px] text-slate-500">Tối đa {maxCapacity} người</span>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => setQty(Math.max(1, qty - 1))}
                        disabled={qty <= 1}
                        className="w-8 h-8 rounded-full bg-white border border-slate-200 text-slate-700 flex items-center justify-center font-bold hover:bg-slate-100 transition-colors shadow-xs active:scale-95 disabled:opacity-40 cursor-pointer"
                      >
                        -
                      </button>
                      <span className="font-bold text-base text-slate-900 min-w-[20px] text-center">{qty}</span>
                      <button
                        type="button"
                        onClick={() => setQty(Math.min(maxCapacity > 0 ? maxCapacity : 1, qty + 1))}
                        disabled={!activeSlot || isSoldOut || qty >= maxCapacity}
                        className="w-8 h-8 rounded-full bg-white border border-slate-200 text-slate-700 flex items-center justify-center font-bold hover:bg-slate-100 transition-colors shadow-xs active:scale-95 disabled:opacity-40 cursor-pointer"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Waiver Checkbox */}
                  {service.waiverRequired && (
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 flex flex-col gap-2">
                      <label className="flex items-start gap-2.5 cursor-pointer select-none">
                        <input
                          type="checkbox"
                          checked={isWaiverChecked}
                          onChange={(e) => setIsWaiverChecked(e.target.checked)}
                          className="mt-1 w-4 h-4 rounded text-cyan-600 accent-cyan-600 cursor-pointer"
                        />
                        <span className="text-xs text-slate-700 leading-snug">
                          Tôi đã đọc, hiểu rõ và đồng ý với{' '}
                          <strong className="text-slate-900">Điều khoản an toàn &amp; miễn trừ trách nhiệm</strong> khi tham gia hoạt động biển.
                        </span>
                      </label>
                    </div>
                  )}
                </div>

                {/* Lower Summary & Actions (Anchored to Bottom to Perfectly Balance with Left Gallery) */}
                <div className="flex flex-col gap-3 pt-4 border-t border-slate-100 mt-2">
                  {/* Total Price */}
                  <div className="flex items-baseline justify-between">
                    <span className="text-sm font-semibold text-slate-600">Tổng tạm tính:</span>
                    <span className="text-2xl font-extrabold text-cyan-700">
                      {totalPrice.toLocaleString('vi-VN')}₫
                    </span>
                  </div>

                  {/* Actions */}
                  {isItemInCart ? (
                    <button
                      type="button"
                      onClick={() => navigate('/cart')}
                      className="w-full py-3.5 px-6 rounded-full font-bold text-sm shadow-md flex items-center justify-center gap-2 transition-all cursor-pointer bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white hover:scale-[1.02] active:scale-98 shadow-cyan-600/20"
                    >
                      <span className="material-symbols-outlined text-[20px]">shopping_cart</span>
                      <span>Xem giỏ hàng</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleAddToCart}
                      disabled={!activeSlot || isSoldOut || (service.waiverRequired && !isWaiverChecked) || isAdding}
                      className={`w-full py-3.5 px-6 rounded-full font-bold text-sm shadow-md flex items-center justify-center gap-2 transition-all cursor-pointer ${
                        isAdding
                          ? 'bg-cyan-600 text-white scale-98'
                          : (!activeSlot || isSoldOut || (service.waiverRequired && !isWaiverChecked))
                          ? 'bg-slate-200 text-slate-400 cursor-not-allowed shadow-none'
                          : 'bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white hover:scale-[1.02] active:scale-98 shadow-cyan-600/20'
                      }`}
                    >
                      {isAdding ? (
                        <span className="material-symbols-outlined animate-spin text-[20px]">refresh</span>
                      ) : (
                        <span className="material-symbols-outlined text-[20px]">shopping_bag</span>
                      )}
                      <span>{isAdding ? 'Đang thêm...' : 'Thêm vào giỏ hàng'}</span>
                    </button>
                  )}

                  {showAddSuccess && (
                    <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold flex items-center gap-1.5 animate-fade-in">
                      <span className="material-symbols-outlined text-sm text-emerald-600">check_circle</span>
                      <span>Đã thêm vào giỏ!</span>
                    </div>
                  )}
                </div>

              </div>
            </aside>
          </div>
        </div>
      </div>

      {/* Lightbox Pop-up Modal for Enlarged Image Preview (Frosted Glass Blur Backdrop, No Dark Overlay) */}
      {isLightboxOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-2xl bg-black/15 p-4 sm:p-8 animate-fade-in select-none"
          onClick={() => setIsLightboxOpen(false)}
        >
          {/* Close button */}
          <button
            type="button"
            onClick={() => setIsLightboxOpen(false)}
            aria-label="Đóng xem ảnh"
            className="absolute top-5 right-5 w-10 h-10 rounded-full bg-white/85 hover:bg-white text-slate-700 hover:text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 border border-slate-200/80 shadow-md hover:shadow-lg active:scale-90"
          >
            <span className="material-symbols-outlined text-[22px]">close</span>
          </button>

          {/* Prev button */}
          {allImages.length > 1 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((prev) => (prev - 1 + allImages.length) % allImages.length);
              }}
              aria-label="Ảnh trước"
              className="absolute left-4 sm:left-8 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/85 hover:bg-white text-slate-700 hover:text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 border border-slate-200/80 shadow-md hover:shadow-lg active:scale-90"
            >
              <span className="material-symbols-outlined text-[24px]">chevron_left</span>
            </button>
          )}

          {/* Next button */}
          {allImages.length > 1 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((prev) => (prev + 1) % allImages.length);
              }}
              aria-label="Ảnh sau"
              className="absolute right-4 sm:right-8 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/85 hover:bg-white text-slate-700 hover:text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 border border-slate-200/80 shadow-md hover:shadow-lg active:scale-90"
            >
              <span className="material-symbols-outlined text-[24px]">chevron_right</span>
            </button>
          )}

          {/* Active Image container */}
          <div
            className="relative max-w-5xl max-h-[85vh] flex flex-col items-center justify-center"
            onClick={(e) => e.stopPropagation()}
          >
            <img
              src={allImages[lightboxIndex]}
              alt={`${service.name} - ${lightboxIndex + 1}`}
              className="max-w-full max-h-[85vh] object-contain rounded-2xl shadow-2xl transition-all duration-300 ring-1 ring-slate-900/10"
            />
          </div>
        </div>
      )}
    </main>
  );
}
