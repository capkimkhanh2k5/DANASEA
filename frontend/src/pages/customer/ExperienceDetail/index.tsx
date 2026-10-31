import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Home,
  Clock,
  Eye,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  X,
  SearchX,
  Heart,
  Star,
  Minus,
  Plus,
  Grid,
  MessageSquare,
  MessageCircle,
  User,
} from 'lucide-react';
import { useCartStore } from '../../../store/useCartStore';
import { useWishlistStore } from '../../../store/useWishlistStore';
import { useLanguageStore } from '../../../store/useLanguageStore';
import { serviceCatalogService } from '../../../services/serviceCatalogService';
import { reviewService, type ReviewResponse } from '../../../services/reviewService';
import type { ServiceDetailDto, ServiceOptionDto, StructuredSlotDto } from '../../../types/service';
import { FEATURED_SERVICES, MOCK_SLOTS, MOCK_IMAGES } from '../../../mockData';

export function ExperienceDetail() {
  const { id } = useParams<{ id: string }>();
  return <ExperienceDetailPage key={id} id={id} />;
}

function ExperienceDetailPage({ id }: { id: string | undefined }) {
  const { language } = useLanguageStore();
  const navigate = useNavigate();

  const [service, setService] = useState<ServiceDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(id));
  const [loadError, setLoadError] = useState<boolean>(!id);

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

  // Gallery slider state for the large main image (Seamless Infinite Carousel)
  const [trackIndex, setTrackIndex] = useState<number>(1);
  const [isTransitioning, setIsTransitioning] = useState<boolean>(true);
  const [isHoveringGallery] = useState<boolean>(false);

  // Lightbox Modal state for full-screen image preview
  const [isLightboxOpen, setIsLightboxOpen] = useState<boolean>(false);
  const [lightboxIndex, setLightboxIndex] = useState<number>(0);

  const handleOpenLightbox = (index: number) => {
    setLightboxIndex(index);
    setIsLightboxOpen(true);
  };

  // Fetch Service Detail from Backend API (with graceful fallback to mock if legacy ID)
  useEffect(() => {
    if (!id) return;

    let isMounted = true;

    serviceCatalogService
      .getServiceDetail(id)
      .then((data) => {
        if (!isMounted) return;

        // Auto-select first option
        if (data.options && data.options.length > 0) {
          setSelectedOptionId(data.options[0].id);
        }

        // Auto-select first available date and slot with defensive fallback (Rule 3)
        let normalizedData = data;
        if (!data.slots || data.slots.length === 0) {
          const today = new Date();
          const todayStr = today.toISOString().split('T')[0];
          const tomorrow = new Date(today);
          tomorrow.setDate(tomorrow.getDate() + 1);
          const tomorrowStr = tomorrow.toISOString().split('T')[0];

          const fallbackUpcomingSlots: StructuredSlotDto[] = [
            {
              slotId: `slot-${data.id}-today-1400`,
              date: todayStr,
              startTime: '14:00:00',
              endTime: '16:00:00',
              capacity: 15,
              availableCapacity: 12,
              price: data.price,
              bookable: true,
            },
            {
              slotId: `slot-${data.id}-today-1630`,
              date: todayStr,
              startTime: '16:30:00',
              endTime: '18:30:00',
              capacity: 15,
              availableCapacity: 15,
              price: data.price,
              bookable: true,
            },
            {
              slotId: `slot-${data.id}-tomorrow-0830`,
              date: tomorrowStr,
              startTime: '08:30:00',
              endTime: '10:30:00',
              capacity: 15,
              availableCapacity: 10,
              price: data.price,
              bookable: true,
            },
            {
              slotId: `slot-${data.id}-tomorrow-1430`,
              date: tomorrowStr,
              startTime: '14:30:00',
              endTime: '16:30:00',
              capacity: 15,
              availableCapacity: 8,
              price: data.price,
              bookable: true,
            },
          ];

          normalizedData = { ...data, slots: fallbackUpcomingSlots };
          setSelectedDate(todayStr);
          setSelectedSlotId(fallbackUpcomingSlots[0].slotId);
        } else {
          const firstBookable = data.slots.find((s) => s.bookable && s.availableCapacity > 0) || data.slots[0];
          setSelectedDate(firstBookable.date);
          setSelectedSlotId(firstBookable.slotId);
        }

        setService(normalizedData);
      })
      .catch((err) => {
        console.warn('[ExperienceDetail] Lỗi nạp dịch vụ từ BE, kiểm tra mock fallback:', err);
        if (!isMounted) return;

        const fallback = FEATURED_SERVICES.find((s) => s.id === id || s.id === `tour-${id}` || id.includes(s.id));
        if (fallback) {
          const today = new Date();
          const todayStr = today.toISOString().split('T')[0];
          const tomorrow = new Date(today);
          tomorrow.setDate(tomorrow.getDate() + 1);
          const tomorrowStr = tomorrow.toISOString().split('T')[0];

          const fallbackSlots: StructuredSlotDto[] = MOCK_SLOTS.filter(
            (slot) => slot.serviceId === fallback.id
          ).map((slot) => ({
            slotId: slot.id,
            date: slot.date || todayStr,
            startTime: slot.startTime || '08:00:00',
            endTime: slot.endTime || '10:00:00',
            capacity: slot.capacity || 15,
            availableCapacity: Math.max(0, (slot.capacity || 15) - (slot.bookedCount || 0)),
            price: fallback.basePrice,
            bookable: slot.status === 'OPEN',
          }));

          if (fallbackSlots.length === 0) {
            fallbackSlots.push(
              {
                slotId: `slot-${fallback.id}-today-1400`,
                date: todayStr,
                startTime: '14:00:00',
                endTime: '16:00:00',
                capacity: 15,
                availableCapacity: 12,
                price: fallback.basePrice,
                bookable: true,
              },
              {
                slotId: `slot-${fallback.id}-today-1630`,
                date: todayStr,
                startTime: '16:30:00',
                endTime: '18:30:00',
                capacity: 15,
                availableCapacity: 15,
                price: fallback.basePrice,
                bookable: true,
              },
              {
                slotId: `slot-${fallback.id}-tomorrow-0830`,
                date: tomorrowStr,
                startTime: '08:30:00',
                endTime: '10:30:00',
                capacity: 15,
                availableCapacity: 10,
                price: fallback.basePrice,
                bookable: true,
              }
            );
          }

          const fallbackImages = MOCK_IMAGES
            .filter((img) => img.serviceId === fallback.id)
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
            imageUrls: fallbackImages.length > 0 ? fallbackImages : [
              'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
              'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
              'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
              'https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?w=800',
            ],
            options: [
              {
                id: `opt-${fallback.id}`,
                serviceId: fallback.id,
                name: 'Vé Tiêu Chuẩn',
                optionType: 'SHARED',
                pricingUnit: 'PER_PERSON',
                price: fallback.basePrice,
                maxPaxPerPackage: 1,
                benefits: 'Áo phao SOLAS, người hướng dẫn PADI, nước uống, bảo hiểm du lịch 100 triệu.',
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
  }, [id, language]);

  // Customer Reviews state & fetch effect
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const hasReviewId = Boolean(id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id));
  const [isLoadingReviews, setIsLoadingReviews] = useState(hasReviewId);
  const [reviewsTotal, setReviewsTotal] = useState<number>(0);

  useEffect(() => {
    if (!id || !hasReviewId) return;

    let isMounted = true;
    reviewService.getServiceReviews(id, 0, 10)
      .then((res) => {
        if (isMounted) {
          setReviews(res.content || []);
          setReviewsTotal(res.totalElements || (res.content ? res.content.length : 0));
        }
      })
      .catch((err) => {
        console.warn('[ExperienceDetail] Không thể tải đánh giá từ server:', err);
        if (isMounted) {
          setReviews([]);
          setReviewsTotal(0);
        }
      })
      .finally(() => {
        if (isMounted) setIsLoadingReviews(false);
      });

    return () => {
      isMounted = false;
    };
  }, [id, hasReviewId]);

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

  // Slots for the currently chosen date, deduplicated by slotId and time window
  const slotsForSelectedDate: StructuredSlotDto[] = useMemo(() => {
    if (!service || !service.slots || !selectedDate) return [];
    const seenSlotIds = new Set<string>();
    const seenTimeRanges = new Set<string>();
    const uniqueSlots: StructuredSlotDto[] = [];

    for (const slot of service.slots) {
      if (slot.date !== selectedDate) continue;

      const timeKey = `${slot.startTime.slice(0, 5)}-${slot.endTime.slice(0, 5)}`;
      if (seenSlotIds.has(slot.slotId) || seenTimeRanges.has(timeKey)) {
        continue;
      }

      seenSlotIds.add(slot.slotId);
      seenTimeRanges.add(timeKey);
      uniqueSlots.push(slot);
    }

    return uniqueSlots;
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
  }, [cartItems, service, activeSlot]);

  // Pure Vietnamese normalizers for demo/seed data
  const localizedSafetyRules = useMemo(() => {
    const raw = service?.safetyRules || '';
    if (raw.toLowerCase().includes('max wave') || raw.toLowerCase().includes('weather-sensitive')) {
      const wave = raw.match(/max wave:\s*([\d.]+m)/i)?.[1] || '0.80m';
      const wind = raw.match(/max wind:\s*([\d.]+\s*km\/h)/i)?.[1] || '5.00km/h';
      return `Sóng biển ≤ ${wave}, gió ≤ ${wind}`;
    }
    if (
      raw.toLowerCase().includes('standard marine safety') ||
      raw.toLowerCase().includes('life jackets') ||
      raw.toLowerCase().includes('worn at all times')
    ) {
      return 'Phù hợp điều kiện thời tiết thông thường (Biển êm, bắt buộc mặc áo phao)';
    }
    if (raw.includes('Bắt buộc') || raw.includes('áo phao')) {
      return raw.split('.')[0].trim();
    }
    return raw || 'Sóng biển ≤ 0.80m, gió ≤ 5.00km/h';
  }, [service?.safetyRules]);

  const imageUrls = service?.imageUrls;
  const allImages: string[] = useMemo(() => {
    if (imageUrls && imageUrls.length >= 4) {
      return imageUrls;
    }
    if (imageUrls && imageUrls.length > 0) {
      const defaults = [
        'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
        'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
        'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
        'https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?w=800',
      ];
      return Array.from(new Set([...imageUrls, ...defaults])).slice(0, 6);
    }
    return [
      'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
      'https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?w=800',
    ];
  }, [imageUrls]);

  const mosaicImages: string[] = useMemo(() => {
    const fallbacks = [
      'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=1200',
      'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
      'https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?w=800',
      'https://images.unsplash.com/photo-1519046904884-53103b34b206?w=800',
    ];
    return Array.from(new Set([...allImages, ...fallbacks])).slice(0, 5);
  }, [allImages]);

  // Infinite Carousel Slides Track: [cloneLast, ...allImages, cloneFirst]
  const slides = useMemo(() => {
    if (allImages.length <= 1) return allImages;
    return [allImages[allImages.length - 1], ...allImages, allImages[0]];
  }, [allImages]);

  // Sync track position when images list changes
  useEffect(() => {
    let enableFrame = 0;
    const resetFrame = requestAnimationFrame(() => {
      setTrackIndex(allImages.length > 1 ? 1 : 0);
      setIsTransitioning(false);
      enableFrame = requestAnimationFrame(() => setIsTransitioning(true));
    });
    return () => { cancelAnimationFrame(resetFrame); cancelAnimationFrame(enableFrame); };
  }, [allImages.length]);

  // Active Real Image Index (0..N-1) for dots, badge counter and thumbnail strip
  const activeRealIndex = useMemo(() => {
    if (allImages.length <= 1) return 0;
    return (trackIndex - 1 + allImages.length) % allImages.length;
  }, [trackIndex, allImages.length]);

  // Handle transition end for seamless infinite wrap-around
  const handleTransitionEnd = () => {
    if (allImages.length <= 1) return;
    if (trackIndex === slides.length - 1) {
      setIsTransitioning(false);
      setTrackIndex(1);
    } else if (trackIndex === 0) {
      setIsTransitioning(false);
      setTrackIndex(allImages.length);
    }
  };

  useEffect(() => {
    if (!isTransitioning) {
      const raf = requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          setIsTransitioning(true);
        });
      });
      return () => cancelAnimationFrame(raf);
    }
  }, [isTransitioning]);

  useEffect(() => {
    if (allImages.length <= 1 || isHoveringGallery) return;

    const timer = setInterval(() => {
      setTrackIndex((prev) => prev + 1);
    }, 4000);

    return () => clearInterval(timer);
  }, [allImages.length, isHoveringGallery]);

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
        locationName: 'Đà Nẵng',
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

  // 1. Loading State
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
        </div>
      </main>
    );
  }

  // 2. Not Found / Error State
  if (loadError || !service) {
    return (
      <main className="w-full pt-32 pb-20 bg-slate-50 flex-1 flex flex-col items-center justify-center min-h-[70vh]">
        <div className="w-16 h-16 rounded-2xl bg-sky-50 border border-sky-100 flex items-center justify-center text-sky-600 mb-4">
          <SearchX className="w-8 h-8 text-sky-600" />
        </div>
        <h2 className="text-xl sm:text-2xl font-bold text-slate-900">
          {language === 'en' ? 'Experience Not Found' : 'Không tìm thấy trải nghiệm'}
        </h2>
        <p className="text-slate-600 mt-2 text-center max-w-md text-sm sm:text-base">
          {language === 'en'
            ? 'The requested ocean experience does not exist or has been paused.'
            : 'Trải nghiệm bạn đang tìm kiếm không tồn tại, chưa được mở bán hoặc đã bị gỡ khỏi hệ thống.'}
        </p>
        <button
          onClick={() => navigate('/search')}
          className="mt-6 px-6 py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-xl transition-all font-semibold text-sm cursor-pointer active:scale-95 shadow-sm"
        >
          {language === 'en' ? 'Back to Discovery' : 'Về trang Khám phá'}
        </button>
      </main>
    );
  }

  const isSaved = isInWishlist(service.id);

  return (
    <main className="w-full pt-20 bg-slate-50/60 flex-1">
      <div className="flex flex-col w-full">
        {/* Breadcrumb Navigation */}
        <div data-ui="breadcrumb" className="animate-hero-1 w-full bg-white">
          <div className="max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-3">
            <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-[13px] sm:text-sm text-slate-600 overflow-x-auto whitespace-nowrap py-0.5">
              <Link
                to="/"
                className="hover:text-sky-600 transition-colors flex items-center gap-1.5 cursor-pointer font-medium"
              >
                <Home className="w-3.5 h-3.5 text-slate-500" />
                <span>{language === 'en' ? 'Home' : 'Trang chủ'}</span>
              </Link>
              <span className="text-slate-300">/</span>
              <Link
                to="/search"
                className="hover:text-sky-600 transition-colors cursor-pointer font-medium"
              >
                {language === 'en' ? 'Discovery' : 'Khám phá'}
              </Link>
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

        <div className="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 pt-6 pb-12 flex flex-col gap-6">
          
          {/* Title & Quick Actions Header */}
          <section className="animate-hero-2 flex flex-col lg:flex-row lg:items-end justify-between gap-4">
            <div className="flex flex-col gap-2 flex-1 min-w-0 pr-4">
              <h1 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight leading-tight">
                {service.name}
              </h1>
              <div className="flex flex-wrap items-center gap-y-2 gap-x-4 text-sm text-slate-700">
                <div className="flex items-center gap-1.5 text-slate-900 font-bold">
                  <Star className="w-4 h-4 fill-amber-400 text-amber-500" />
                  <span className="tabular-nums">{service.averageRating ? service.averageRating.toFixed(2) : '5.0'}</span>
                  <span className="text-slate-500 font-normal tabular-nums">({service.reviewCount || 100})</span>
                </div>
                <span className="text-slate-300">•</span>
                <div className="flex items-center gap-1.5 text-slate-700 font-medium">
                  <Clock className="w-4 h-4 text-sky-600" />
                  <span className="tabular-nums">{service.duration || 120} phút</span>
                </div>
                <span className="text-slate-300">•</span>
                <div className="flex items-center gap-1.5 text-slate-700 font-medium">
                  <Eye className="w-4 h-4 text-sky-600" />
                  <span className="tabular-nums">{service.viewCount || 1500} lượt xem</span>
                </div>
              </div>
            </div>

            {/* Wishlist Quick Action */}
            <div className="flex items-center gap-3 self-start lg:self-center shrink-0">
              <button
                type="button"
                onClick={() => toggleWishlist(service.id, service.name)}
                className={`px-4 py-2.5 rounded-xl text-[13px] sm:text-sm font-semibold flex items-center gap-2 border transition-all cursor-pointer active:scale-95 ${
                  isSaved
                    ? 'bg-rose-50 border-rose-300 text-rose-600'
                    : 'bg-white border-slate-200 text-slate-700 hover:border-rose-300 hover:text-rose-600'
                }`}
              >
                <Heart className={`w-4 h-4 ${isSaved ? 'fill-rose-500 text-rose-500' : 'text-slate-400'}`} />
                <span>{isSaved ? 'Đã lưu yêu thích' : 'Lưu vào yêu thích'}</span>
              </button>
            </div>
          </section>

          {/* =========================================================================
              AIRBNB SIGNATURE PHOTO GALLERY: Mosaic 5-Photo Grid (Desktop) & Carousel (Mobile)
             ========================================================================= */}
          <section data-ui="gallery" className="w-full">
            {/* Desktop / Tablet: Signature Airbnb 5-Photo Mosaic Grid */}
            <div className="hidden md:grid grid-cols-4 grid-rows-2 gap-2.5 h-[420px] rounded-2xl overflow-hidden relative shadow-xs border border-slate-200/80 bg-slate-100">
              {/* Photo 1: Large Main Image (Left 50%) */}
              <div
                onClick={() => handleOpenLightbox(0)}
                className="col-span-2 row-span-2 relative group overflow-hidden cursor-pointer select-none"
              >
                <img
                  src={mosaicImages[0]}
                  alt={`${service.name} - Ảnh chính`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
              </div>

              {/* Photo 2: Top Mid */}
              <div
                onClick={() => handleOpenLightbox(1)}
                className="relative group overflow-hidden cursor-pointer select-none"
              >
                <img
                  src={mosaicImages[1]}
                  alt={`${service.name} - Ảnh 2`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
              </div>

              {/* Photo 3: Top Right */}
              <div
                onClick={() => handleOpenLightbox(2)}
                className="relative group overflow-hidden cursor-pointer select-none"
              >
                <img
                  src={mosaicImages[2]}
                  alt={`${service.name} - Ảnh 3`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
              </div>

              {/* Photo 4: Bottom Mid */}
              <div
                onClick={() => handleOpenLightbox(3)}
                className="relative group overflow-hidden cursor-pointer select-none"
              >
                <img
                  src={mosaicImages[3]}
                  alt={`${service.name} - Ảnh 4`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
              </div>

              {/* Photo 5: Bottom Right with Floating "Show all photos" Button */}
              <div
                onClick={() => handleOpenLightbox(4)}
                className="relative group overflow-hidden cursor-pointer select-none"
              >
                <img
                  src={mosaicImages[4]}
                  alt={`${service.name} - Ảnh 5`}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />

                {/* Floating "Show all photos" Pill */}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleOpenLightbox(0);
                  }}
                  className="absolute bottom-3 right-3 px-3.5 py-2 rounded-xl bg-white/95 backdrop-blur-md text-slate-900 border border-slate-200 text-xs font-semibold shadow-xs flex items-center gap-1.5 hover:bg-white transition-all cursor-pointer z-10 active:scale-95"
                >
                  <Grid className="w-3.5 h-3.5" />
                  <span>Hiển thị tất cả {allImages.length} ảnh</span>
                </button>
              </div>
            </div>

            {/* Mobile: Swiper Carousel */}
            <div className="block md:hidden">
              <div
                onClick={() => handleOpenLightbox(activeRealIndex)}
                className="relative overflow-hidden rounded-2xl h-[280px] bg-black select-none cursor-pointer"
              >
                <div
                  onTransitionEnd={handleTransitionEnd}
                  className={`flex w-full h-full ${
                    isTransitioning ? 'transition-transform duration-500 ease-out' : ''
                  }`}
                  style={{ transform: `translateX(-${trackIndex * 100}%)` }}
                >
                  {slides.map((imgUrl, sIdx) => (
                    <div key={sIdx} className="w-full h-full shrink-0 relative overflow-hidden flex items-center justify-center bg-slate-900">
                      <img
                        src={imgUrl}
                        alt={`${service.name} - slide ${sIdx + 1}`}
                        className="w-full h-full object-cover select-none"
                        loading={sIdx <= 2 ? 'eager' : 'lazy'}
                      />
                    </div>
                  ))}
                </div>

                <div className="absolute bottom-3 right-3 px-2.5 py-1 rounded-md bg-black/75 text-white text-xs font-semibold tabular-nums tracking-wide z-10 pointer-events-none">
                  {activeRealIndex + 1} / {allImages.length}
                </div>
              </div>
            </div>
          </section>

          {/* Lưu ý an toàn biển (Thanh mỏng trải dài ngang đúng 1 dòng duy nhất) */}
          <div data-ui="safety-banner" className="animate-hero-3 w-full flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-950 overflow-hidden">
            <ShieldCheck className="w-4 h-4 text-amber-700 shrink-0" />
            <p className="flex-1 min-w-0 text-[13px] sm:text-sm text-amber-950 leading-normal truncate">
              <strong className="font-bold text-amber-950">Quy chuẩn an toàn:</strong>{' '}
              <span className="text-amber-900">Bắt buộc áo phao SOLAS &amp; HLV PADI. Hoàn 100% trước 24h hoặc khi cảng vụ hoãn chuyến.</span>
            </p>
          </div>

          {/* Main Content Layout: Left (Detailed Experience Intel), Right (Sticky Reservation Rail) */}
          <div className="animate-hero-4 grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            
            {/* LEFT COLUMN: Detailed Experience Intel (64% Width) */}
            <div className="lg:col-span-8 flex flex-col gap-6">
              
              {/* Host / Captain Information Card - Airbnb Signature */}
              <div className="p-5 sm:p-6 bg-white rounded-2xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-full overflow-hidden bg-slate-100 shrink-0 border border-slate-200">
                    <img
                      src="https://images.unsplash.com/photo-1544717305-2782549b5136?w=200"
                      alt="Thuyền trưởng & Hướng dẫn viên"
                      className="w-full h-full object-cover"
                    />
                  </div>
                  <div>
                    <h2 className="font-bold text-base text-slate-900">
                      Tổ chức bởi {service.businessName || "Đội ngũ DANASEA Partner"}
                    </h2>
                    <p className="text-[13px] text-slate-600 mt-0.5">
                      Thuyền trưởng hàng hải 5+ năm kinh nghiệm • Đạt chuẩn PADI &amp; SOLAS
                    </p>
                  </div>
                </div>

                <span className="hidden sm:inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-800 text-xs font-semibold border border-emerald-200">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                  Đối tác xác minh
                </span>
              </div>

              {/* Mô tả trải nghiệm & Thông tin vận hành */}
              <section data-ui="experience-info" className="animate-hero-4 bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 transition-transform duration-300">
                <div>
                  <h2 className="text-xl font-bold text-slate-900 mb-2">Mô tả trải nghiệm</h2>
                  <p className="text-slate-800 leading-relaxed text-base whitespace-pre-line max-w-[60ch]">
                    {service.description}
                  </p>
                </div>

                <div className="pt-4 border-t border-slate-100 space-y-3">
                  <h3 className="text-sm font-bold text-slate-900">Thông tin vận hành &amp; Tiêu chuẩn an toàn</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2.5 text-sm">
                    <div className="flex items-baseline gap-2 text-slate-700">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Thời lượng:</span>
                      <span className="font-semibold text-slate-900">{service.duration || 120} phút / ca</span>
                    </div>
                    <div className="flex items-baseline gap-2 text-slate-700">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Sức chứa:</span>
                      <span className="font-semibold text-slate-900">Tối đa {service.capacity || 15} người / ca</span>
                    </div>
                    <div className="flex items-baseline gap-2 text-slate-700 sm:col-span-2">
                      <span className="text-slate-500 whitespace-nowrap shrink-0">Điều kiện thời tiết:</span>
                      <span className="font-semibold text-slate-900">{localizedSafetyRules}</span>
                    </div>
                    {service.businessName && (
                      <div className="flex items-baseline gap-2 text-slate-700 sm:col-span-2 min-w-0">
                        <span className="text-slate-500 whitespace-nowrap shrink-0">Đơn vị tổ chức:</span>
                        <span className="font-semibold text-slate-900 truncate">{service.businessName}</span>
                        {service.badgeTier && (
                          <span className="text-[13px] font-semibold text-emerald-800 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md whitespace-nowrap shrink-0">
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
                <section data-ui="experience-options" className="animate-hero-4 bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-3 transition-transform duration-300">
                  <h2 className="text-xl font-bold text-slate-900">Lựa chọn gói dịch vụ</h2>
                  <div className="grid grid-cols-1 gap-2.5">
                    {service.options.map((opt) => {
                      const isSelected = selectedOptionId === opt.id;
                      return (
                        <div
                          key={opt.id}
                          onClick={() => setSelectedOptionId(opt.id)}
                          className={`animate-hero-4 p-3.5 rounded-xl border-2 transition-transform duration-200 hover:-translate-y-0.5 cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                            isSelected
                              ? 'bg-sky-50/60 border-sky-500'
                              : 'bg-slate-50 border-slate-200/70 hover:border-slate-300'
                          }`}
                        >
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-slate-900 text-sm sm:text-base">{opt.name}</span>
                              <span className="px-2 py-0.5 rounded-md bg-white border border-slate-200 text-[13px] font-semibold text-slate-700">
                                {opt.optionType === 'PRIVATE' ? 'Bao trọn gói' : 'Vé ghép'}
                              </span>
                            </div>
                            {opt.benefits && (
                              <p className="text-base text-slate-700 leading-relaxed max-w-[60ch]">{opt.benefits}</p>
                            )}
                          </div>
                          <div className="flex items-baseline gap-1.5 sm:justify-end shrink-0 whitespace-nowrap">
                            <span className="font-black text-lg text-sky-700 tabular-nums">
                              {opt.price.toLocaleString('vi-VN')}₫
                            </span>
                            <span className="text-[13px] text-slate-500 font-medium">
                              {opt.pricingUnit === 'PER_PERSON' ? '/ người' : '/ gói'}
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}
            </div>

            {/* RIGHT COLUMN: Sticky Interactive Booking Box */}
            <aside className="lg:col-span-4 w-full lg:sticky lg:top-24">
              <div data-ui="booking-deck" className="animate-hero-3 w-full p-5 sm:p-6 rounded-2xl bg-white border border-slate-200/90 shadow-sm flex flex-col justify-between min-h-[528px]">
                
                {/* Upper Booking Configuration */}
                <div className="flex flex-col gap-4">
                  {/* Price Display */}
                  <div className="border-b border-slate-100 pb-3 flex items-baseline gap-1.5 flex-wrap">
                    <span className="text-3xl font-black text-sky-700 tabular-nums tracking-tight">
                      {unitPrice.toLocaleString('vi-VN')}₫
                    </span>
                    <span className="text-[13px] text-slate-500 font-medium whitespace-nowrap">
                      {selectedOption?.pricingUnit === 'PER_PACKAGE' ? '/ gói' : '/ người'}
                    </span>
                  </div>

                  {/* Date Selection */}
                  <div className="flex flex-col gap-1.5">
                    <label className="text-[13px] font-bold text-slate-700">Ngày khởi hành</label>
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
                        className="w-full p-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 text-sm font-semibold cursor-pointer hover:bg-slate-100 transition-colors outline-none focus:ring-2 focus:ring-sky-500"
                      >
                        {availableDates.map((d) => (
                          <option key={d} value={d}>
                            {d === new Date().toISOString().split('T')[0] ? `Hôm nay (${d})` : d}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <div className="p-3 rounded-xl bg-slate-50 text-[13px] text-slate-500">
                        Chưa có lịch mở bán cho dịch vụ này
                      </div>
                    )}
                  </div>

                  {/* Structured Slots Selection */}
                  <div className="flex flex-col gap-1.5">
                    <label className="text-[13px] font-bold text-slate-700">Khung giờ khởi hành</label>
                    <div className="flex flex-col gap-2">
                      {slotsForSelectedDate.length === 0 ? (
                        <p className="text-slate-500 text-[13px] text-center py-2 bg-slate-50 rounded-xl">
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
                              className={`animate-hero-4 p-3 rounded-xl border-2 transition-transform duration-200 hover:-translate-y-0.5 cursor-pointer flex items-center justify-between ${
                                isSelected
                                  ? 'bg-sky-50 border-sky-500'
                                  : isFull
                                  ? 'bg-slate-100 border-transparent opacity-50 cursor-not-allowed'
                                  : 'bg-slate-50 border-slate-200/80 hover:bg-slate-100'
                              }`}
                            >
                              <div className="flex flex-col">
                                <span className={`text-sm font-bold tabular-nums ${isSelected ? 'text-sky-900' : 'text-slate-900'}`}>
                                  {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                                </span>
                                <span className="text-[13px] text-slate-600 mt-0.5 tabular-nums">
                                  {isFull ? 'Đã hết chỗ' : `Còn ${remain} / ${slot.capacity} chỗ`}
                                </span>
                              </div>
                              {isSelected && (
                                <CheckCircle2 className="w-5 h-5 text-sky-600 shrink-0" />
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
                      <span className="text-sm font-bold text-slate-900">Số lượng người</span>
                      {activeSlot && (
                        <span className="text-[13px] text-slate-500 tabular-nums">Tối đa {maxCapacity} người</span>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => setQty(Math.max(1, qty - 1))}
                        disabled={qty <= 1}
                        className="w-8 h-8 rounded-lg bg-white border border-slate-300 text-slate-700 flex items-center justify-center font-bold hover:bg-slate-100 transition-colors active:scale-95 disabled:opacity-40 cursor-pointer"
                      >
                        <Minus className="w-3.5 h-3.5" />
                      </button>
                      <span className="font-bold text-base text-slate-900 min-w-[20px] text-center tabular-nums">{qty}</span>
                      <button
                        type="button"
                        onClick={() => setQty(Math.min(maxCapacity > 0 ? maxCapacity : 1, qty + 1))}
                        disabled={!activeSlot || isSoldOut || qty >= maxCapacity}
                        className="w-8 h-8 rounded-lg bg-white border border-slate-300 text-slate-700 flex items-center justify-center font-bold hover:bg-slate-100 transition-colors active:scale-95 disabled:opacity-40 cursor-pointer"
                      >
                        <Plus className="w-3.5 h-3.5" />
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
                          className="mt-1 w-4 h-4 rounded text-sky-600 accent-sky-600 cursor-pointer"
                        />
                        <span className="text-[13px] text-slate-700 leading-snug">
                          Tôi đã đọc, hiểu rõ và đồng ý với{' '}
                          <strong className="text-slate-900 font-semibold">Điều khoản an toàn &amp; miễn trừ trách nhiệm</strong> khi tham gia hoạt động biển.
                        </span>
                      </label>
                    </div>
                  )}
                </div>

                {/* Lower Summary & Actions */}
                <div className="flex flex-col gap-3 pt-4 border-t border-slate-100 mt-2">
                  <div className="flex items-baseline justify-between">
                    <span className="text-sm font-semibold text-slate-600">Tổng tạm tính:</span>
                    <span className="text-2xl font-black text-sky-700 tabular-nums">
                      {totalPrice.toLocaleString('vi-VN')}₫
                    </span>
                  </div>

                  {isItemInCart ? (
                    <button
                      type="button"
                      onClick={() => navigate('/cart')}
                      className="w-full h-12 rounded-xl font-bold text-sm flex items-center justify-center transition-all cursor-pointer bg-[#0284c7] hover:bg-[#0369a1] text-white shadow-xs active:scale-98"
                    >
                      Xem giỏ hàng
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        if (!activeSlot || isSoldOut) {
                          alert('Khung giờ này đã hết chỗ, vui lòng chọn ca khác.');
                          return;
                        }
                        if (service.waiverRequired && !isWaiverChecked) {
                          alert('Vui lòng tích chọn đồng ý Điều khoản an toàn & miễn trừ trách nhiệm trước khi đặt vé.');
                          return;
                        }
                        handleAddToCart();
                      }}
                      className={`w-full h-12 rounded-xl font-bold text-sm flex items-center justify-center transition-all cursor-pointer shadow-xs ${
                        isAdding
                          ? 'bg-[#0284c7] text-white scale-98'
                          : (!activeSlot || isSoldOut || (service.waiverRequired && !isWaiverChecked))
                          ? 'bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold border border-slate-300'
                          : 'bg-[#0284c7] hover:bg-[#0369a1] text-white active:scale-98'
                      }`}
                    >
                      {isAdding ? 'Đang thêm...' : 'Thêm vào giỏ hàng'}
                    </button>
                  )}

                  {showAddSuccess && (
                    <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-[13px] font-semibold flex items-center gap-1.5 animate-fade-in">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                      <span>Đã thêm vào giỏ!</span>
                    </div>
                  )}
                </div>

              </div>
            </aside>
          </div>

          {/* Customer Reviews Section (Đánh giá từ khách hàng) */}
          <section data-ui="experience-reviews" className="mt-12 pt-10 border-t border-slate-200/80">
            {/* Header: Star rating & Total review count & Verification badge */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
              <div className="flex items-center gap-3 flex-wrap">
                <div className="flex items-center gap-1.5 text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
                  <Star className="w-7 h-7 fill-amber-400 text-amber-400" />
                  <span className="tabular-nums">
                    {service.averageRating && service.averageRating > 0 ? service.averageRating.toFixed(2) : '5.00'}
                  </span>
                </div>
                <span className="text-slate-300 text-2xl font-light">·</span>
                <h2 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                  {reviewsTotal > 0 ? `${reviewsTotal} đánh giá từ du khách` : 'Đánh giá từ khách hàng'}
                </h2>
              </div>

              {/* Verified Guest Badge */}
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-50/80 border border-emerald-200 text-xs font-semibold text-emerald-800 self-start sm:self-auto">
                <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>100% đánh giá từ khách đã hoàn thành chuyến đi</span>
              </div>
            </div>

            {/* Shimmer Skeleton Loader when fetching reviews */}
            {isLoadingReviews ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {[1, 2].map((idx) => (
                  <div key={idx} className="p-5 rounded-2xl border border-slate-200/80 bg-white animate-pulse space-y-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-slate-100" />
                      <div className="space-y-1 flex-1">
                        <div className="h-4 bg-slate-100 rounded w-1/3" />
                        <div className="h-3 bg-slate-100 rounded w-1/4" />
                      </div>
                    </div>
                    <div className="h-3 bg-slate-100 rounded w-24" />
                    <div className="space-y-1.5 pt-1">
                      <div className="h-3.5 bg-slate-100 rounded w-full" />
                      <div className="h-3.5 bg-slate-100 rounded w-4/5" />
                    </div>
                  </div>
                ))}
              </div>
            ) : reviews.length > 0 ? (
              /* Review Cards Grid */
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
                {reviews.map((rev) => {
                  const reviewDate = rev.createdAt
                    ? new Date(rev.createdAt).toLocaleDateString(language === 'en' ? 'en-US' : 'vi-VN', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })
                    : '';

                  return (
                    <article
                      key={rev.id}
                      className="p-5 sm:p-6 rounded-2xl bg-white border border-slate-200/80 shadow-xs flex flex-col justify-between gap-4 transition-all duration-200 hover:border-slate-300"
                    >
                      <div className="space-y-3">
                        {/* Customer Info Header */}
                        <div className="flex items-center justify-between gap-3">
                          <div className="flex items-center gap-3 min-w-0">
                            {rev.customerAvatar ? (
                              <img
                                src={rev.customerAvatar}
                                alt={rev.customerName || 'Khách hàng'}
                                className="w-10 h-10 rounded-full object-cover border border-slate-200 bg-slate-100 shrink-0"
                              />
                            ) : (
                              <div className="w-10 h-10 rounded-full bg-sky-100 text-sky-800 font-bold text-sm flex items-center justify-center border border-sky-200 shrink-0">
                                {rev.customerName ? rev.customerName.charAt(0).toUpperCase() : <User className="w-4 h-4 text-sky-600" />}
                              </div>
                            )}
                            <div className="min-w-0">
                              <h3 className="font-bold text-sm text-slate-900 truncate">
                                {rev.customerName || 'Du khách DANASEA'}
                              </h3>
                              <p className="text-xs text-slate-500 tabular-nums">{reviewDate}</p>
                            </div>
                          </div>

                          {/* Star Rating */}
                          <div className="flex items-center gap-0.5 shrink-0">
                            {[1, 2, 3, 4, 5].map((s) => (
                              <Star
                                key={s}
                                className={`w-3.5 h-3.5 ${
                                  s <= (rev.rating || 5)
                                    ? 'fill-amber-400 text-amber-400'
                                    : 'text-slate-200'
                                }`}
                              />
                            ))}
                          </div>
                        </div>

                        {/* Comment Text */}
                        <p className="text-sm text-slate-700 leading-relaxed whitespace-pre-line">
                          {rev.comment}
                        </p>

                        {/* Customer Uploaded Photos */}
                        {rev.images && rev.images.length > 0 && (
                          <div className="flex items-center gap-2 pt-1 flex-wrap">
                            {rev.images.map((imgUrl, imgIdx) => (
                              <img
                                key={imgIdx}
                                src={imgUrl}
                                alt={`Ảnh đánh giá ${imgIdx + 1}`}
                                className="w-16 h-16 rounded-xl object-cover border border-slate-200 hover:opacity-90 transition-opacity cursor-pointer"
                              />
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Vendor Reply Callout (nếu có phản hồi từ Nhà cung cấp) */}
                      {rev.vendorReply && (
                        <div className="bg-slate-50 border-l-2 border-sky-600 rounded-r-xl p-3.5 text-xs text-slate-700 space-y-1 mt-1">
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-bold text-sky-900 flex items-center gap-1.5">
                              <MessageCircle className="w-3.5 h-3.5 text-sky-600" />
                              <span>Phản hồi từ Nhà cung cấp</span>
                            </span>
                            {rev.vendorRepliedAt && (
                              <span className="text-[11px] text-slate-400 tabular-nums">
                                {new Date(rev.vendorRepliedAt).toLocaleDateString('vi-VN')}
                              </span>
                            )}
                          </div>
                          <p className="text-slate-600 italic leading-relaxed">{rev.vendorReply}</p>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            ) : (
              /* Empty State */
              <div className="bg-white rounded-2xl border border-slate-200/80 p-8 sm:p-10 text-center flex flex-col items-center justify-center shadow-xs">
                <div className="w-12 h-12 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-center text-slate-400 mb-3">
                  <MessageSquare className="w-6 h-6 text-slate-400" />
                </div>
                <h3 className="font-bold text-base text-slate-900 mb-1">
                  Chưa có đánh giá nào cho hoạt động này
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 max-w-md leading-relaxed">
                  Sau khi hoàn thành chuyến đi, bạn có thể quay lại chia sẻ cảm nhận thực tế và đánh giá chất lượng phục vụ của hướng dẫn viên.
                </p>
              </div>
            )}
          </section>
        </div>
      </div>

      {/* Lightbox Pop-up Modal */}
      {isLightboxOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 sm:p-8 animate-fade-in select-none"
          onClick={() => setIsLightboxOpen(false)}
        >
          <button
            type="button"
            onClick={() => setIsLightboxOpen(false)}
            aria-label="Đóng xem ảnh"
            className="absolute top-5 right-5 w-10 h-10 rounded-xl bg-white/90 hover:bg-white text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 shadow-md active:scale-90"
          >
            <X className="w-5 h-5 text-slate-900" />
          </button>

          {allImages.length > 1 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((prev) => (prev - 1 + allImages.length) % allImages.length);
              }}
              aria-label="Ảnh trước"
              className="absolute left-4 sm:left-8 top-1/2 -translate-y-1/2 w-11 h-11 rounded-xl bg-white/90 hover:bg-white text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 shadow-md active:scale-90"
            >
              <ChevronLeft className="w-6 h-6 text-slate-900" />
            </button>
          )}

          {allImages.length > 1 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((prev) => (prev + 1) % allImages.length);
              }}
              aria-label="Ảnh sau"
              className="absolute right-4 sm:right-8 top-1/2 -translate-y-1/2 w-11 h-11 rounded-xl bg-white/90 hover:bg-white text-slate-900 flex items-center justify-center transition-all cursor-pointer z-50 shadow-md active:scale-90"
            >
              <ChevronRight className="w-6 h-6 text-slate-900" />
            </button>
          )}

          <div
            className="relative max-w-5xl max-h-[85vh] flex flex-col items-center justify-center"
            onClick={(e) => e.stopPropagation()}
          >
            <img
              src={allImages[lightboxIndex]}
              alt={`${service.name} - ${lightboxIndex + 1}`}
              className="max-w-full max-h-[85vh] object-contain rounded-2xl shadow-2xl transition-all duration-300"
            />
          </div>
        </div>
      )}
    </main>
  );
}

export default ExperienceDetail;
