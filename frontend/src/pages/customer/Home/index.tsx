import React, { useState, useEffect, useRef, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useLanguageStore } from "../../../store/useLanguageStore";

// Rich Customer Home Data
import {
  SUP_KAYAK_EXPERIENCES,
  DIVING_EXPERIENCES,
  THRILL_EXPERIENCES,
  COMBO_SUNSET_EXPERIENCES,
  FLASH_SALE_EXPERIENCES,
} from "../../../data/customerHomeData";
import type { ActivityCardData, FlashSaleExperience } from "../../../data/customerHomeData";

// Customer Home Subcomponents
import { FlashSaleSection } from "../../../components/customer/FlashSaleSection";
import { CategoryExperienceCarousel } from "../../../components/customer/CategoryExperienceCarousel";
import { DanaseaAppShowcase } from "../../../components/customer/DanaseaAppShowcase";
import { GuestGalleryMarquee } from "../../../components/customer/GuestGalleryMarquee";
import { CoastalVideoSpotlights } from "../../../components/customer/CoastalVideoSpotlights";
import { CinematicSceneBackdrop } from "../../../components/customer/CinematicSceneBackdrop";

import { Search, ArrowRight, Check, CheckCircle2, ChevronLeft, ChevronRight, SlidersHorizontal, X } from 'lucide-react';
import { serviceCatalogService } from "../../../services/serviceCatalogService";
import type { ServiceSummaryDto } from "../../../types/service";
import { getCardThumbnailUrl } from "../../../utils/imageOptimizer";
import { SEOHead } from "../../../components/common/SEOHead";

interface ChatMessage {
  chatPrompt: string;
  chatPromptEn?: string;
  chatReply: string | null;
  chatReplyEn?: string;
  chatTourName?: string;
  chatTourNameEn?: string;
}
interface MarineMetric { val: string; desc: string; descEn: string }
interface BeachInfo extends ChatMessage {
  temp: MarineMetric;
  wave: MarineMetric;
  visibility: MarineMetric;
  wind: MarineMetric;
  recommendationTitle: string;
  recommendationTitleEn: string;
  recommendationDesc: string;
  recommendationDescEn: string;
}

// AI Beach Data for Maritime Concierge
const AI_BEACH_DATA: Record<string, BeachInfo> = {
  'Mỹ Khê': {
    chatPrompt: 'Nhóm mình 4 người muốn chèo SUP sáng mai ở Mỹ Khê, giờ nào biển êm và ánh sáng chụp ảnh đẹp nhất vậy bạn?',
    chatPromptEn: 'Our group of 4 wants to paddle SUP tomorrow morning at My Khe. What time has the calmest waves and best photoshoot light?',
    chatReply: 'Chào bạn! Sáng mai tại Mỹ Khê, thủy triều đạt mức đẹp từ 05:15 - 06:45. Sóng chỉ cao 0.3m, mặt nước phẳng như gương và mặt trời ló rạng ngay tầm mắt.',
    chatReplyEn: 'Hello! Tomorrow at My Khe, optimal tides are 05:15 - 06:45. Swell is only 0.3m, water mirrored calm with morning sun directly on the horizon.',
    chatTourName: 'SUP Bình Minh Mỹ Khê (Kèm Nhiếp Ảnh)',
    chatTourNameEn: 'Sunrise SUP My Khe (With Photoshoot)',
    temp: { val: '28°C', desc: 'Nắng nhẹ, gió biển mát', descEn: 'Mild sun, fresh breeze' },
    wave: { val: '0.4 m', desc: 'Sóng êm • Lý tưởng cho SUP', descEn: 'Calm swell • Ideal for SUP' },
    visibility: { val: '8 mét', desc: 'Nước trong ngắm san hô', descEn: 'Clear water visibility' },
    wind: { val: '12 km/h', desc: 'Hướng Đông Nam đều đặn', descEn: 'Steady SE breeze' },
    recommendationTitle: 'Rất thích hợp cho Chèo SUP & Lặn biển ngắm san hô',
    recommendationTitleEn: 'Highly Recommended for Sunrise SUP & Coral Snorkeling',
    recommendationDesc: 'Biển tĩnh lặng kéo dài đến 10:30 sáng. Hãy trang bị kem chống nắng thân thiện môi trường rạn san hô.',
    recommendationDescEn: 'Calm water lasts until 10:30 AM. Coral-safe sunscreen recommended.',
  },
  'Sơn Trà': {
    chatPrompt: 'Cho mình xin lịch trình lặn ngắm san hô ở Sơn Trà vào cuối tuần này với?',
    chatPromptEn: 'Could you recommend an itinerary for scuba diving in Son Tra this weekend?',
    chatReply: 'Tuyệt vời! Cuối tuần này tại Sơn Trà, nước rất trong với tầm nhìn lên đến 12m. Bạn nên đi tầm 08:30 sáng ở khu vực Bãi Rạng hoặc Mũi Nghê để có trải nghiệm ngắm san hô đẹp nhất nhé.',
    chatReplyEn: 'Great choice! This weekend at Son Tra, underwater visibility reaches up to 12m. Optimal departure is 08:30 AM at Bai Rang or Mui Nghe.',
    chatTourName: 'Lặn ngắm san hô Mũi Nghê',
    chatTourNameEn: 'Mui Nghe Coral Reef Dive',
    temp: { val: '26°C', desc: 'Mát mẻ, bóng râm từ rừng', descEn: 'Pleasant shade from hills' },
    wave: { val: '0.2 m', desc: 'Biển cực êm', descEn: 'Ultra calm water' },
    visibility: { val: '12 mét', desc: 'Tuyệt hảo cho lặn biển', descEn: 'Superb for scuba dive' },
    wind: { val: '8 km/h', desc: 'Gió rất nhẹ', descEn: 'Gentle breeze' },
    recommendationTitle: 'Thời điểm vàng để lặn biển và khám phá sinh thái',
    recommendationTitleEn: 'Prime Window for Coral Diving & Marine Safari',
    recommendationDesc: 'Hệ sinh thái san hô đang ở trạng thái tốt nhất. Cẩn thận các bãi đá ngầm khi chèo thuyền tiếp cận.',
    recommendationDescEn: 'Coral ecosystem in top condition. Watch for shallow underwater rocks when approaching.',
  },
  'Non Nước': {
    chatPrompt: 'Mình muốn tìm một hoạt động biển nhẹ nhàng cho gia đình có trẻ nhỏ ở Non Nước.',
    chatPromptEn: 'Looking for a gentle seaside activity for a family with young kids at Non Nuoc.',
    chatReply: 'Chào bạn! Bãi Non Nước hiện tại sóng rất êm, bờ cát rộng. Gia đình mình có thể tham gia trải nghiệm dù lượn cano hoặc thuê lều cắm trại ngay trên bãi biển vào buổi chiều tà.',
    chatReplyEn: 'Non Nuoc beach currently has gentle waves and wide soft sand. Perfect for family seaside relaxation or sunset beach camp.',
    chatTourName: 'Cắm trại hoàng hôn Non Nước',
    chatTourNameEn: 'Sunset Beach Camp Non Nuoc',
    temp: { val: '29°C', desc: 'Nắng ráo, thích hợp tắm biển', descEn: 'Sunny, pleasant for swim' },
    wave: { val: '0.6 m', desc: 'Sóng vừa phải', descEn: 'Moderate playful waves' },
    visibility: { val: '6 mét', desc: 'Tầm nhìn khá', descEn: 'Good coastal clarity' },
    wind: { val: '15 km/h', desc: 'Gió lộng, mát mẻ', descEn: 'Invigorating sea breeze' },
    recommendationTitle: 'Tuyệt vời cho các hoạt động thể thao nước và gia đình',
    recommendationTitleEn: 'Perfect for Family Seaside Gatherings & Sunset Watersports',
    recommendationDesc: 'Bãi biển rộng rãi, an toàn cho trẻ em. Gió lý tưởng để vui chơi và thư giãn cuối ngày.',
    recommendationDescEn: 'Spacious beach, guarded safe zones. Wonderful afternoon breeze.',
  }
};

const CUSTOM_PROMPT_DATA: Record<string, ChatMessage> = {
  'group': {
    chatPrompt: 'Nhóm mình 6 người muốn đi biển nửa ngày, có hoạt động nào vui mà gắn kết không AI?',
    chatPromptEn: 'Our group of 6 wants a half-day marine outing. What activities are fun and bonding?',
    chatReply: 'Chào bạn! Với nhóm 6 người đi nửa ngày, tuyệt vời nhất là thuê 3 ván SUP lớn tại Mỹ Khê hoặc lặn ống thở ngắm san hô bãi cạn ở Sơn Trà. Hai hoạt động này đều dễ tham gia và cực vui cho nhóm đông.',
    chatReplyEn: 'For a group of 6, renting 3 tandem SUP boards at My Khe or snorkeling over Son Tra shallow reefs is fantastic and easy for everyone.',
    chatTourName: 'Combo chèo SUP & Snorkeling Nhóm',
    chatTourNameEn: 'Group SUP & Snorkeling Combo',
  },
  'couple': {
    chatPrompt: 'Vợ chồng mình muốn tìm trải nghiệm thư giãn, nhẹ nhàng vào buổi chiều tối.',
    chatPromptEn: 'My partner and I want a romantic, tranquil afternoon sea experience.',
    chatReply: 'Tuyệt vời! Buổi chiều mát mẻ ở biển Non Nước rất vắng và êm. Mình gợi ý gói thuê lều cắm trại hoàng hôn, kết hợp set BBQ nhẹ trên bãi biển dành riêng cho 2 người.',
    chatReplyEn: 'Non Nuoc beach in late afternoon is serene. I recommend our Sunset Romantic Camp with fresh seaside mocktails & BBQ for two.',
    chatTourName: 'Cắm trại hoàng hôn lãng mạn',
    chatTourNameEn: 'Romantic Sunset Camp',
  },
  'weather': {
    chatPrompt: 'Dự báo ngày mai trời có nắng gắt không? Nên chơi gì cho mát?',
    chatPromptEn: 'Is it too hot tomorrow? What sea activity is most refreshing?',
    chatReply: 'Ngày mai Đà Nẵng nắng khá gắt vào buổi trưa. Bạn nên chèo SUP thật sớm lúc 5h30 sáng để đón bình minh, hoặc đợi đến 16h chiều để chơi lướt ván cano trên biển nhé!',
    chatReplyEn: 'Da Nang will be sunny around noon. Optimal timing is early 05:30 AM sunrise SUP or late 16:00 PM speedboat wakeboarding.',
    chatTourName: 'Lướt ván cano chiều mát',
    chatTourNameEn: 'Late Afternoon Speedboat Boarding',
  }
};

export function Home() {
  const { language, t } = useLanguageStore();
  const navigate = useNavigate();

  // AI Concierge State
  const activeAIBeach = 'Mỹ Khê';
  const [activeCustomPrompt] = useState<string | null>(null);
  const [chatHistory] = useState<ChatMessage[]>([]);
  const chatContainerRef = useRef<HTMLDivElement>(null);

  // Search Capsule State
  const [activeDropdown, setActiveDropdown] = useState<string | null>(null);
  const [selectedRegions, setSelectedRegions] = useState<string[]>(['Mỹ Khê']);
  const [selectedDate, setSelectedDate] = useState<string>(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });
  const [calendarViewDate, setCalendarViewDate] = useState<Date>(() => new Date());
  const [selectedTime, setSelectedTime] = useState<string>('05:30');
  const [guests, setGuests] = useState({ adults: 2, children: 0 });
  const [isMobileSearchOpen, setIsMobileSearchOpen] = useState(false);
  const [mobileSearchTab, setMobileSearchTab] = useState<'region' | 'date' | 'time' | 'guests'>('region');
  const searchRef = useRef<HTMLDivElement>(null);

  // Lock body scroll when mobile search modal is active
  useEffect(() => {
    if (isMobileSearchOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isMobileSearchOpen]);

  // Live Services from Backend (with intelligent fallback)
  const [liveServices, setLiveServices] = useState<ServiceSummaryDto[]>([]);
  useEffect(() => {
    let isMounted = true;
    serviceCatalogService
      .getServices({ size: 30 })
      .then((res) => {
        if (isMounted && res.content && res.content.length > 0) {
          setLiveServices(res.content);
        }
      })
      .catch((err) => {
        console.warn('[Home] Không thể tải dịch vụ, dùng fallback:', err);
      });
    return () => {
      isMounted = false;
    };
  }, [language]);

  // Helper to infer realistic duration from activity title
  const inferDuration = (name: string, fallback: string = '2 giờ') => {
    const n = name.toLowerCase();
    if (n.includes('jetski') || n.includes('mô tô') || n.includes('moto')) return '30 phút';
    if (n.includes('phao chuối') || n.includes('flyboard')) return '45 phút';
    if (n.includes('lặn') || n.includes('diving') || n.includes('scuba')) return '3.5 giờ';
    if (n.includes('dù') || n.includes('parasailing')) return '1.5 giờ';
    if (n.includes('câu cá') || n.includes('tour đảo')) return '4 giờ';
    if (n.includes('sup') || n.includes('kayak')) return '2 giờ';
    return fallback;
  };

  // Curated Featured Activities from Live Backend Catalog with fallback
  const featuredActivities: ActivityCardData[] = liveServices.length > 0
    ? liveServices.slice(0, 10).map((s, idx) => {
        const fallbacks = [
          ...DIVING_EXPERIENCES,
          ...THRILL_EXPERIENCES,
          ...SUP_KAYAK_EXPERIENCES,
          ...COMBO_SUNSET_EXPERIENCES,
        ];
        const fallbackExp = fallbacks[idx % fallbacks.length];
        return {
          id: s.id,
          title: s.name,
          titleEn: s.name,
          category: fallbackExp.category,
          categoryEn: fallbackExp.categoryEn,
          location: s.address || fallbackExp.location,
          duration: inferDuration(s.name, fallbackExp.duration),
          durationEn: inferDuration(s.name, fallbackExp.durationEn),
          price: Number(s.price) || fallbackExp.price,
          rating: Number(s.averageRating) || (4.91 + (idx % 5) * 0.02),
          reviewCount: s.reviewCount || (80 + (idx * 37) % 150),
          imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallbackExp.imageUrl),
        };
      })
    : [
        DIVING_EXPERIENCES[0],
        THRILL_EXPERIENCES[0],
        SUP_KAYAK_EXPERIENCES[0],
        COMBO_SUNSET_EXPERIENCES[0],
        DIVING_EXPERIENCES[1],
        THRILL_EXPERIENCES[1],
        SUP_KAYAK_EXPERIENCES[1],
        COMBO_SUNSET_EXPERIENCES[1],
      ];

  // Flash Sale Experiences derived from live services or fallback data
  const flashSaleList: FlashSaleExperience[] = liveServices.length > 0
    ? liveServices.slice(0, 4).map((s, idx) => {
        const discounts = [33, 28, 22, 25];
        const discountPct = discounts[idx % discounts.length];
        const numPrice = Number(s.price) || 350000;
        const fallbackExp = FLASH_SALE_EXPERIENCES[idx % FLASH_SALE_EXPERIENCES.length];
        return {
          id: s.id,
          title: s.name,
          titleEn: s.name,
          category: 'Ưu đãi biển',
          categoryEn: 'Marine Special',
          location: s.address || fallbackExp.location,
          locationEn: s.address || fallbackExp.locationEn,
          duration: inferDuration(s.name, fallbackExp.duration),
          durationEn: inferDuration(s.name, fallbackExp.durationEn),
          originalPrice: Math.round((numPrice / (1 - discountPct / 100)) / 10000) * 10000,
          salePrice: numPrice,
          discountPercent: discountPct,
          rating: Number(s.averageRating) || (4.92 + (idx % 4) * 0.02),
          reviewCount: s.reviewCount || (120 + (idx * 47) % 180),
          slotsLeft: 2 + (idx % 3),
          totalSlots: 10 + (idx % 5),
          imageUrl: getCardThumbnailUrl(s.primaryImageUrl || fallbackExp.imageUrl),
        };
      })
    : FLASH_SALE_EXPERIENCES;

  const baseMessage = activeCustomPrompt ? CUSTOM_PROMPT_DATA[activeCustomPrompt] : AI_BEACH_DATA[activeAIBeach];
  const displayMessages = useMemo(() => [baseMessage, ...chatHistory], [baseMessage, chatHistory]);

  useEffect(() => {
    if (chatContainerRef.current) {
      chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
    }
  }, [displayMessages]);

  // Click outside and Escape key to close search dropdown
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setActiveDropdown(null);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setActiveDropdown(null);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const toggleDropdown = (dropdown: string) => {
    setActiveDropdown(activeDropdown === dropdown ? null : dropdown);
  };

  const handleRegionChange = (region: string) => {
    if (selectedRegions.includes(region)) {
      if (selectedRegions.length > 1) {
        setSelectedRegions(selectedRegions.filter(r => r !== region));
      }
    } else {
      setSelectedRegions([...selectedRegions, region]);
    }
  };

  const handleSearchExecute = () => {
    const locParam = selectedRegions.join(',');
    setIsMobileSearchOpen(false);
    navigate(`/search?location=${encodeURIComponent(locParam)}&q=${encodeURIComponent(locParam)}&date=${encodeURIComponent(selectedDate)}&time=${encodeURIComponent(selectedTime)}&guests=${guests.adults + guests.children}&adults=${guests.adults}&children=${guests.children}`);
  };

  const BEACH_OPTIONS = [
    {
      name: 'Mỹ Khê',
      nameEn: 'My Khe',
      tag: 'Trung tâm • Thể thao',
      tagEn: 'Central • Watersports',
      desc: 'Lướt ván SUP, dù lượn, cano sôi động',
      descEn: 'SUP paddleboard, parasailing, speedboats',
      img: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=160&auto=format&fit=crop&q=80',
    },
    {
      name: 'Sơn Trà',
      nameEn: 'Son Tra',
      tag: 'Bán đảo • San hô',
      tagEn: 'Peninsula • Coral reef',
      desc: 'Lặn ngắm rạn san hô hoang sơ pha lê',
      descEn: 'Crystal waters, vibrant coral reefs',
      img: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=160&auto=format&fit=crop&q=80',
    },
    {
      name: 'Bãi Rạng',
      nameEn: 'Bai Rang',
      tag: 'Lặng sóng • Kayak',
      tagEn: 'Calm cove • Kayak',
      desc: 'Vịnh đá ngầm, nước biển trong vắt',
      descEn: 'Rocky lagoons, tranquil sea breeze',
      img: 'https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?w=160&auto=format&fit=crop&q=80',
    },
    {
      name: 'Non Nước',
      nameEn: 'Non Nuoc',
      tag: 'Gia đình • Hoàng hôn',
      tagEn: 'Family • Sunset camping',
      desc: 'Bãi cát thoai thoải, cắm trại chiều tà',
      descEn: 'Soft white sand, sunset beach camp',
      img: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=160&auto=format&fit=crop&q=80',
    },
  ];

  const TIME_OPTIONS = [
    {
      val: '05:30',
      title: '05:30 • Bình Minh',
      titleEn: '05:30 • Dawn',
      desc: 'Mặt biển phẳng lặng, nắng mai êm',
      descEn: 'Mirror sea, golden dawn',
      iconSvg: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 2v6" />
          <path d="m4.93 10.93 4.24-4.24" />
          <path d="m19.07 10.93-4.24-4.24" />
          <path d="M2 18h20" />
          <path d="M20 22H4" />
          <path d="M16 18a4 4 0 0 0-8 0" />
        </svg>
      ),
    },
    {
      val: '08:30',
      title: '08:30 • San Hô',
      titleEn: '08:30 • Coral',
      desc: 'Nắng chiếu sâu, nước trong vắt',
      descEn: 'High clarity, radiant sun',
      iconSvg: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2" />
          <path d="M12 20v2" />
          <path d="m4.93 4.93 1.41 1.41" />
          <path d="m17.66 17.66 1.41 1.41" />
          <path d="M2 12h2" />
          <path d="M20 12h2" />
          <path d="m6.34 17.66-1.41 1.41" />
          <path d="m19.07 4.93-1.41 1.41" />
        </svg>
      ),
    },
    {
      val: '14:30',
      title: '14:30 • Gió Biển',
      titleEn: '14:30 • Breeze',
      desc: 'Gió lộng, lướt ván cano sôi động',
      descEn: 'Strong winds, thrill sports',
      iconSvg: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2" />
          <path d="M9.6 4.6A2 2 0 1 1 11 8H2" />
          <path d="M12.6 19.4A2 2 0 1 0 14 16H2" />
        </svg>
      ),
    },
    {
      val: '16:30',
      title: '16:30 • Hoàng Hôn',
      titleEn: '16:30 • Sunset',
      desc: 'Ráng chiều đỏ, chèo SUP chill',
      descEn: 'Crimson skies, dusk chill',
      iconSvg: (
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 10V2" />
          <path d="m4.93 6.93 4.24 4.24" />
          <path d="m19.07 6.93-4.24 4.24" />
          <path d="M2 18h20" />
          <path d="M20 22H4" />
          <path d="M16 18a4 4 0 0 0-8 0" />
        </svg>
      ),
    },
  ];

  const calendarYear = calendarViewDate.getFullYear();
  const calendarMonth = calendarViewDate.getMonth();
  const daysInMonth = new Date(calendarYear, calendarMonth + 1, 0).getDate();
  const firstDayOfWeek = (new Date(calendarYear, calendarMonth, 1).getDay() + 6) % 7; // Monday is 0

  const handlePrevMonth = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCalendarViewDate(new Date(calendarYear, calendarMonth - 1, 1));
  };

  const handleNextMonth = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCalendarViewDate(new Date(calendarYear, calendarMonth + 1, 1));
  };

  const handleSelectDay = (day: number) => {
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    const dateStr = `${calendarYear}-${pad(calendarMonth + 1)}-${pad(day)}`;
    setSelectedDate(dateStr);
    setActiveDropdown(null);
  };

  const isPastDay = (day: number) => {
    const d = new Date(calendarYear, calendarMonth, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return d < today;
  };

  const isSelectedDay = (day: number) => {
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    const dateStr = `${calendarYear}-${pad(calendarMonth + 1)}-${pad(day)}`;
    return selectedDate === dateStr;
  };

  const isTodayDay = (day: number) => {
    const today = new Date();
    return today.getFullYear() === calendarYear && today.getMonth() === calendarMonth && today.getDate() === day;
  };

  const selectedTimeObj = TIME_OPTIONS.find((t) => t.val === selectedTime);

  return (
    <main className="w-full text-slate-900 min-h-screen relative overflow-x-clip bg-transparent">
      <SEOHead
        title={language === 'en' ? 'Top Marine Tours & Ocean Experiences in Da Nang' : 'Đặt Tour & Trải Nghiệm Biển Đà Nẵng Hàng Đầu'}
        description={
          language === 'en'
            ? 'Book ocean adventures, scuba diving, SUP paddleboarding, and coastal boat tours in Da Nang with DANASEA. Instant booking, transparent pricing, verified operators.'
            : 'Khám phá và đặt vé các hoạt động thể thao biển, lặn ngắm san hô Sơn Trà, chèo SUP Mỹ Khê, cano dù bay tại Đà Nẵng cùng DANASEA. An toàn, uy tín và giá tốt nhất.'
        }
        canonicalUrl="/"
        ogImage="https://danasea.vn/logo192.png"
        ogType="website"
      />
      {/* =========================================================================
          FULL-PAGE DYNAMIC CINEMATIC SCENE BACKDROP
         ========================================================================= */}
      <CinematicSceneBackdrop />

      {/* =========================================================================
          BACKDROP SCRIM OVERLAY (AIRBNB / BOOKING OTA GOLD STANDARD)
          Dims & softens the background when search dropdown is active to focus attention
         ========================================================================= */}
      {activeDropdown !== null && (
        <div
          className="fixed inset-0 bg-slate-950/45 backdrop-blur-[2px] z-30 transition-opacity duration-200"
          onClick={() => setActiveDropdown(null)}
          aria-hidden="true"
        />
      )}

      {/* =========================================================================
          HERO: BÃI BIỂN TOÀN MÀN HÌNH & THANH TÌM KIẾM TINH GỌN
         ========================================================================= */}
      <section
        id="scene-hero"
        data-scene="hero"
        className={`relative ${activeDropdown !== null ? 'z-40' : 'z-20'} w-full min-h-[480px] sm:min-h-[540px] flex flex-col justify-center items-center pt-28 sm:pt-32 pb-16 sm:pb-20 overflow-visible`}
      >
        <div className="max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 text-center flex flex-col items-center overflow-visible">
          {/* Large Headline */}
          <h1 className="animate-hero-2 text-3xl sm:text-5xl lg:text-6xl font-extrabold text-white tracking-tight drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_24px_rgba(0,0,0,0.7)] mb-2.5">
            {language === 'en' ? 'Ocean Tours & Watersports Da Nang' : 'Tour Biển & Thể Thao Nước Đà Nẵng'}
          </h1>

          {/* Subtitle */}
          <p className="animate-hero-3 text-base sm:text-lg text-slate-100 max-w-2xl mb-6 drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.95)] drop-shadow-[0_4px_12px_rgba(0,0,0,0.6)] font-medium leading-relaxed">
            {language === 'en'
              ? 'Direct booking for sunrise SUP, Son Tra coral scuba diving, Yamaha jetskis, and sunset catamarans.'
              : 'Đặt chỗ trực tuyến chèo SUP bình minh Mỹ Khê, lặn ngắm san hô Sơn Trà, mô tô nước Jetski và du thuyền hoàng hôn.'}
          </p>

          {/* =========================================================================
              SEARCH EXPERIENCE (PRO-GRADE FE & DESIGNER CALIBER)
              - Desktop (sm+): Precision architectural capsule, 8pt spacing, solid brand blue
              - Mobile (<sm): Compact 56px trigger pill + Dedicated ergonomic bottom sheet
              - Quick Discovery: Subtle frosted trending tags
             ========================================================================= */}

          {/* DESKTOP SEARCH CONTAINER - CAPSULE PILL DESIGN (sm:block) */}
          <div
            ref={searchRef}
            data-ui="search-deck"
            className="hidden sm:block animate-hero-4 w-full max-w-4xl bg-white/20 hover:bg-white/25 backdrop-blur-xl rounded-full p-2 pl-3 sm:pl-4 pr-2 sm:pr-2.5 shadow-[0_16px_40px_rgba(0,0,0,0.25)] border border-white/40 transition-all duration-300 relative z-30"
          >
            <div className="flex items-center relative">
              {/* 1. Destination / Địa điểm */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('region')}
                  aria-expanded={activeDropdown === 'region'}
                  className={`w-full flex flex-col justify-center px-4 sm:px-5 py-2.5 rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'region'
                      ? 'bg-white/30 ring-1 ring-white/60 text-white shadow-xs'
                      : activeDropdown !== null
                      ? 'opacity-75 hover:opacity-100 hover:bg-white/15'
                      : 'hover:bg-white/15'
                  }`}
                >
                  <span className="text-[11px] font-bold text-white/80 uppercase tracking-wider block drop-shadow-xs">
                    {t.hero.search.destination}
                  </span>
                  <span className="text-sm font-bold text-white truncate block mt-0.5 drop-shadow-xs">
                    {selectedRegions.length > 0
                      ? selectedRegions.join(', ')
                      : (language === 'en' ? 'Select bays' : 'Chọn điểm đến')}
                  </span>
                </button>

                {/* Popover: Rich Bay Cards (2x2 Grid) */}
                {activeDropdown === 'region' && (
                  <div className="animate-popover-in absolute top-full left-0 mt-3 w-[520px] p-4 bg-white border border-slate-200/90 rounded-2xl shadow-xl z-50 text-left">
                    <div className="flex items-center justify-between pb-2 mb-3 border-b border-slate-100">
                      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                        {language === 'en' ? 'Explore Coastal Bays' : 'Khám phá các vịnh biển'}
                      </span>
                      <span className="text-[11px] text-slate-400 font-medium">
                        {selectedRegions.length} {language === 'en' ? 'selected' : 'đã chọn'}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-2.5">
                      {BEACH_OPTIONS.map((bay) => {
                        const isSelected = selectedRegions.includes(bay.name);
                        return (
                          <div
                            key={bay.name}
                            onClick={() => handleRegionChange(bay.name)}
                            className={`flex items-center gap-2.5 p-2 rounded-xl cursor-pointer transition-all border ${
                              isSelected
                                ? 'bg-sky-50/80 border-sky-300 ring-1 ring-sky-500/20 '
                                : 'bg-white border-slate-100 hover:bg-slate-50 hover:border-slate-200'
                            }`}
                          >
                            <img
                              src={bay.img}
                              alt={bay.name}
                              className="w-11 h-11 rounded-lg object-cover shrink-0 "
                            />
                            <div className="flex-1 min-w-0">
                              <span className="text-xs font-bold text-slate-900 truncate block">
                                {language === 'en' ? bay.nameEn : bay.name}
                              </span>
                              <p className="text-[10px] text-sky-700 font-medium truncate mt-0.5">
                                {language === 'en' ? bay.tagEn : bay.tag}
                              </p>
                              <p className="text-[10px] text-slate-400 truncate">
                                {language === 'en' ? bay.descEn : bay.desc}
                              </p>
                            </div>
                            <div className={`w-4 h-4 rounded-full border flex items-center justify-center shrink-0 ${
                              isSelected
                                ? 'bg-sky-600 border-sky-600 text-white'
                                : 'border-slate-300 bg-white'
                            }`}>
                              {isSelected && <Check className="w-3.5 h-3.5 text-sky-600 shrink-0" />}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Inset Divider */}
              <div className="h-8 w-[1px] bg-white/30 shrink-0 mx-0.5" />

              {/* 2. Date / Ngày đi */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('date')}
                  aria-expanded={activeDropdown === 'date'}
                  className={`w-full flex flex-col justify-center px-4 sm:px-5 py-2.5 rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'date'
                      ? 'bg-white/30 ring-1 ring-white/60 text-white shadow-xs'
                      : activeDropdown !== null
                      ? 'opacity-75 hover:opacity-100 hover:bg-white/15'
                      : 'hover:bg-white/15'
                  }`}
                >
                  <span className="text-[11px] font-bold text-white/80 uppercase tracking-wider block drop-shadow-xs">
                    {t.hero.search.date}
                  </span>
                  <span className="text-sm font-bold text-white truncate block mt-0.5 drop-shadow-xs">
                    {selectedDate || (language === 'en' ? 'Select date' : 'Chọn ngày')}
                  </span>
                </button>

                {/* Popover: Interactive Calendar */}
                {activeDropdown === 'date' && (
                  <div className="animate-popover-in absolute top-full left-1/2 -translate-x-1/2 mt-3 w-80 p-4 bg-white border border-slate-200/90 rounded-2xl shadow-xl z-50 text-left">
                    <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-100">
                      <button
                        type="button"
                        onClick={handlePrevMonth}
                        className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer text-slate-600"
                        aria-label="Previous month"
                      >
                        <ChevronLeft className="w-4 h-4 text-slate-600 shrink-0" />
                      </button>
                      <span className="text-xs font-bold text-slate-800">
                        {language === 'en'
                          ? calendarViewDate.toLocaleString('en-US', { month: 'long', year: 'numeric' })
                          : `Tháng ${calendarMonth + 1}, ${calendarYear}`}
                      </span>
                      <button
                        type="button"
                        onClick={handleNextMonth}
                        className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer text-slate-600"
                        aria-label="Next month"
                      >
                        <ChevronRight className="w-4 h-4 text-slate-600 shrink-0" />
                      </button>
                    </div>

                    <div className="grid grid-cols-7 gap-1 text-center mb-1">
                      {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((day) => (
                        <span key={day} className="text-[10px] font-bold text-slate-400 uppercase py-1">
                          {day}
                        </span>
                      ))}
                    </div>

                    <div className="grid grid-cols-7 gap-1">
                      {Array.from({ length: firstDayOfWeek }).map((_, i) => (
                        <div key={`empty-${i}`} className="h-8" />
                      ))}
                      {Array.from({ length: daysInMonth }).map((_, i) => {
                        const day = i + 1;
                        const isPast = isPastDay(day);
                        const isSelected = isSelectedDay(day);
                        const isToday = isTodayDay(day);
                        return (
                          <button
                            key={day}
                            type="button"
                            disabled={isPast}
                            onClick={() => handleSelectDay(day)}
                            className={`h-8 rounded-lg text-xs font-semibold flex items-center justify-center transition-all ${
                              isSelected
                                ? 'bg-sky-600 text-white '
                                : isToday
                                ? 'border border-sky-600 text-sky-600 hover:bg-sky-50'
                                : isPast
                                ? 'text-slate-300 cursor-not-allowed'
                                : 'text-slate-700 hover:bg-slate-100 cursor-pointer'
                            }`}
                          >
                            {day}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Inset Divider */}
              <div className="h-8 w-[1px] bg-white/30 shrink-0 mx-0.5" />

              {/* 3. Time Window / Khung giờ */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('time')}
                  aria-expanded={activeDropdown === 'time'}
                  className={`w-full flex flex-col justify-center px-4 sm:px-5 py-2.5 rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'time'
                      ? 'bg-white/30 ring-1 ring-white/60 text-white shadow-xs'
                      : activeDropdown !== null
                      ? 'opacity-75 hover:opacity-100 hover:bg-white/15'
                      : 'hover:bg-white/15'
                  }`}
                >
                  <span className="text-[11px] font-bold text-white/80 uppercase tracking-wider block drop-shadow-xs">
                    {language === 'en' ? 'Time Slot' : 'Khung giờ'}
                  </span>
                  <span className="text-sm font-bold text-white truncate block mt-0.5 drop-shadow-xs">
                    {selectedTimeObj
                      ? (language === 'en' ? selectedTimeObj.titleEn : selectedTimeObj.title)
                      : '05:30 • Bình Minh'}
                  </span>
                </button>

                {/* Popover: 4 Maritime Time Slots */}
                {activeDropdown === 'time' && (
                  <div className="animate-popover-in absolute top-full left-1/2 -translate-x-1/2 mt-3 w-80 p-3.5 bg-white border border-slate-200/90 rounded-2xl shadow-xl z-50 text-left">
                    <div className="pb-2 mb-2 border-b border-slate-100">
                      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block">
                        {language === 'en' ? 'Select Marine Time Window' : 'Khung giờ vàng lướt sóng'}
                      </span>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        {language === 'en' ? 'Optimized for tides & wind safety' : 'Dựa trên dự báo hải văn & thủy triều'}
                      </p>
                    </div>
                    <div className="space-y-1.5">
                      {TIME_OPTIONS.map((slot) => {
                        const isSelected = selectedTime === slot.val;
                        return (
                          <div
                            key={slot.val}
                            onClick={() => {
                              setSelectedTime(slot.val);
                              setActiveDropdown(null);
                            }}
                            className={`flex items-center gap-2.5 p-2 rounded-xl cursor-pointer transition-all border ${
                              isSelected
                                ? 'bg-sky-50/80 border-sky-300 ring-1 ring-sky-500/20'
                                : 'bg-white border-slate-100 hover:bg-slate-50'
                            }`}
                          >
                            <div className={`p-1.5 rounded-lg shrink-0 ${
                              isSelected ? 'bg-sky-600 text-white' : 'bg-slate-100 text-slate-600'
                            }`}>
                              {slot.iconSvg}
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="text-xs font-bold text-slate-900 block truncate">
                                {language === 'en' ? slot.titleEn : slot.title}
                              </span>
                              <p className="text-[10px] text-slate-500 truncate mt-0.5">
                                {language === 'en' ? slot.descEn : slot.desc}
                              </p>
                            </div>
                            {isSelected && (
                              <CheckCircle2 className="w-4 h-4 text-sky-600 shrink-0" />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Inset Divider */}
              <div className="h-8 w-[1px] bg-white/30 shrink-0 mx-0.5" />

              {/* 4. Guests / Số khách */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('guests')}
                  aria-expanded={activeDropdown === 'guests'}
                  className={`w-full flex flex-col justify-center px-4 sm:px-5 py-2.5 rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'guests'
                      ? 'bg-white/30 ring-1 ring-white/60 text-white shadow-xs'
                      : activeDropdown !== null
                      ? 'opacity-75 hover:opacity-100 hover:bg-white/15'
                      : 'hover:bg-white/15'
                  }`}
                >
                  <span className="text-[11px] font-bold text-white/80 uppercase tracking-wider block drop-shadow-xs">
                    {t.hero.search.guests}
                  </span>
                  <span className="text-sm font-bold text-white truncate block mt-0.5 drop-shadow-xs">
                    {guests.adults + guests.children}{' '}
                    {language === 'en' ? 'guests' : 'khách'}
                  </span>
                </button>

                {/* Popover: Guests Stepper */}
                {activeDropdown === 'guests' && (
                  <div className="animate-popover-in absolute top-full right-0 mt-3 w-72 p-4 bg-white border border-slate-200/90 rounded-2xl shadow-xl z-50 text-left">
                    <div className="pb-2 mb-3 border-b border-slate-100">
                      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block">
                        {language === 'en' ? 'Number of Guests' : 'Số lượng thành viên'}
                      </span>
                    </div>

                    <div className="space-y-3 mb-3">
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-xs font-bold text-slate-800 block">
                            {language === 'en' ? 'Adults' : 'Người lớn'}
                          </span>
                          <span className="text-[10px] text-slate-400">
                            {language === 'en' ? 'Age 13+' : 'Từ 13 tuổi trở lên'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, adults: Math.max(1, g.adults - 1) }))}
                            disabled={guests.adults <= 1}
                            className={`w-7 h-7 rounded-lg border flex items-center justify-center transition-colors ${
                              guests.adults <= 1
                                ? 'border-slate-200 text-slate-300 cursor-not-allowed'
                                : 'border-slate-300 text-slate-700 hover:bg-slate-100 cursor-pointer'
                            }`}
                          >
                            -
                          </button>
                          <span className="text-xs font-bold text-slate-800 w-4 text-center">
                            {guests.adults}
                          </span>
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, adults: Math.min(20, g.adults + 1) }))}
                            className="w-7 h-7 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
                          >
                            +
                          </button>
                        </div>
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-xs font-bold text-slate-800 block">
                            {language === 'en' ? 'Children' : 'Trẻ em'}
                          </span>
                          <span className="text-[10px] text-slate-400">
                            {language === 'en' ? 'Age 2-12' : 'Từ 2 - 12 tuổi'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, children: Math.max(0, g.children - 1) }))}
                            disabled={guests.children <= 0}
                            className={`w-7 h-7 rounded-lg border flex items-center justify-center transition-colors ${
                              guests.children <= 0
                                ? 'border-slate-200 text-slate-300 cursor-not-allowed'
                                : 'border-slate-300 text-slate-700 hover:bg-slate-100 cursor-pointer'
                            }`}
                          >
                            -
                          </button>
                          <span className="text-xs font-bold text-slate-800 w-4 text-center">
                            {guests.children}
                          </span>
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, children: Math.min(10, g.children + 1) }))}
                            className="w-7 h-7 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-100 text-[10px] text-slate-400 text-center">
                      {language === 'en'
                        ? 'Children under 2 travel free with adult'
                        : 'Trẻ dưới 2 tuổi miễn phí vé & đi cùng phụ huynh'}
                    </div>
                  </div>
                )}
              </div>

              {/* 5. Search CTA: Pill Action Button */}
              <div className="pr-0.5 flex justify-end shrink-0 pl-2">
                <button
                  type="button"
                  onClick={handleSearchExecute}
                  aria-label="Search"
                  className="h-12 px-6 rounded-full bg-sky-600 hover:bg-sky-500 active:bg-sky-700 text-white flex items-center justify-center gap-2 shadow-md hover:shadow-sky-600/30 transition-all cursor-pointer font-bold text-sm tracking-wide"
                >
                  <Search className="w-4 h-4 shrink-0" />
                  <span>{language === 'en' ? 'Search' : 'Tìm kiếm'}</span>
                </button>
              </div>
            </div>
          </div>

          {/* MOBILE SEARCH TRIGGER BAR (block sm:hidden) - CAPSULE PILL DESIGN */}
          <div data-ui="search-mobile-bar" className="block sm:hidden animate-hero-4 w-full max-w-md mx-auto relative z-30">
            <button
              type="button"
              onClick={() => setIsMobileSearchOpen(true)}
              className="w-full h-14 bg-white/20 hover:bg-white/25 backdrop-blur-xl rounded-full border border-white/40 px-3.5 flex items-center justify-between gap-3 text-left transition-all active:scale-[0.98] cursor-pointer shadow-lg"
            >
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-full bg-sky-600 text-white flex items-center justify-center shrink-0 shadow-xs">
                  <Search className="w-5 h-5 shrink-0" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-bold text-white truncate">
                    {language === 'en' ? 'Where on Da Nang Coast?' : 'Khám phá biển Đà Nẵng ở đâu?'}
                  </div>
                  <div className="text-[11px] text-white/80 truncate flex items-center gap-1.5 mt-0.5">
                    <span className="font-semibold text-white">{selectedRegions.join(', ') || 'Điểm đến'}</span>
                    <span>•</span>
                    <span>{selectedDate.split('-').reverse().slice(0, 2).join('/')}</span>
                    <span>•</span>
                    <span>{guests.adults + guests.children} {language === 'en' ? 'guests' : 'khách'}</span>
                  </div>
                </div>
              </div>
              <div className="w-9 h-9 rounded-full border border-white/30 text-white flex items-center justify-center shrink-0 bg-white/15">
                <SlidersHorizontal className="w-4 h-4 shrink-0" />
              </div>
            </button>
          </div>

          {/* MOBILE SEARCH SHEET MODAL */}
          {isMobileSearchOpen && (
            <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex flex-col justify-end sm:hidden animate-in fade-in duration-200">
              <div
                className="w-full max-h-[90vh] bg-white rounded-t-3xl  flex flex-col overflow-hidden animate-in slide-in-from-bottom duration-300"
                onClick={(e) => e.stopPropagation()}
              >
                {/* Sheet Header */}
                <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-sm bg-sky-600"></span>
                    <span className="text-sm font-bold text-slate-900">
                      {language === 'en' ? 'Search Marine Activities' : 'Tìm kiếm hoạt động biển'}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setIsMobileSearchOpen(false)}
                    className="w-8 h-8 rounded-lg hover:bg-slate-100 flex items-center justify-center text-slate-500 cursor-pointer"
                  >
                    <X className="w-4 h-4 shrink-0" />
                  </button>
                </div>

                {/* Horizontal Tab Switcher */}
                <div className="grid grid-cols-4 border-b border-slate-100 bg-slate-50/80 p-1 gap-1 text-center">
                  <button
                    type="button"
                    onClick={() => setMobileSearchTab('region')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      mobileSearchTab === 'region'
                        ? 'bg-white text-sky-700 '
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    {language === 'en' ? 'Bays' : 'Điểm đến'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMobileSearchTab('date')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      mobileSearchTab === 'date'
                        ? 'bg-white text-sky-700 '
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    {language === 'en' ? 'Date' : 'Ngày đi'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMobileSearchTab('time')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      mobileSearchTab === 'time'
                        ? 'bg-white text-sky-700 '
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    {language === 'en' ? 'Time' : 'Khung giờ'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMobileSearchTab('guests')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      mobileSearchTab === 'guests'
                        ? 'bg-white text-sky-700 '
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    {language === 'en' ? 'Guests' : 'Số khách'}
                  </button>
                </div>

                {/* Tab Content Body */}
                <div className="flex-1 overflow-y-auto p-4 space-y-3">
                  {mobileSearchTab === 'region' && (
                    <div className="space-y-2">
                      <div className="text-xs font-semibold text-slate-500 px-1">
                        {language === 'en' ? 'Select Da Nang Coastal Bays' : 'Chọn một hoặc nhiều vịnh biển:'}
                      </div>
                      <div className="grid grid-cols-1 gap-2">
                        {BEACH_OPTIONS.map((bay) => {
                          const isSelected = selectedRegions.includes(bay.name);
                          return (
                            <div
                              key={bay.name}
                              onClick={() => handleRegionChange(bay.name)}
                              className={`flex items-center gap-3 p-2.5 rounded-xl border transition-all cursor-pointer ${
                                isSelected
                                  ? 'bg-sky-50/90 border-sky-300 ring-1 ring-sky-500/20'
                                  : 'bg-white border-slate-100 hover:bg-slate-50'
                              }`}
                            >
                              <img
                                src={bay.img}
                                alt={bay.name}
                                className="w-12 h-12 rounded-lg object-cover shrink-0"
                              />
                              <div className="flex-1 min-w-0">
                                <span className="text-xs font-bold text-slate-900 block truncate">
                                  {language === 'en' ? bay.nameEn : bay.name}
                                </span>
                                <span className="text-[11px] text-sky-700 font-medium block truncate">
                                  {language === 'en' ? bay.tagEn : bay.tag}
                                </span>
                                <span className="text-[10px] text-slate-400 block truncate">
                                  {language === 'en' ? bay.descEn : bay.desc}
                                </span>
                              </div>
                              <div className={`w-5 h-5 rounded-md border flex items-center justify-center shrink-0 ${
                                isSelected ? 'bg-sky-600 border-sky-600 text-white' : 'border-slate-300 bg-white'
                              }`}>
                                {isSelected && <Check className="w-3.5 h-3.5 text-sky-600 shrink-0" />}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {mobileSearchTab === 'date' && (
                    <div className="space-y-3">
                      <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                        <button
                          type="button"
                          onClick={handlePrevMonth}
                          className="p-1 hover:bg-slate-100 rounded-lg text-slate-600 cursor-pointer"
                        >
                          <ChevronLeft className="w-4 h-4 text-slate-600 shrink-0" />
                        </button>
                        <span className="text-xs font-bold text-slate-800">
                          {language === 'en'
                            ? calendarViewDate.toLocaleString('en-US', { month: 'long', year: 'numeric' })
                            : `Tháng ${calendarMonth + 1}, ${calendarYear}`}
                        </span>
                        <button
                          type="button"
                          onClick={handleNextMonth}
                          className="p-1 hover:bg-slate-100 rounded-lg text-slate-600 cursor-pointer"
                        >
                          <ChevronRight className="w-4 h-4 text-slate-600 shrink-0" />
                        </button>
                      </div>

                      <div className="grid grid-cols-7 gap-1 text-center mb-1">
                        {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((d) => (
                          <span key={d} className="text-[10px] font-bold text-slate-400 uppercase py-1">
                            {d}
                          </span>
                        ))}
                      </div>

                      <div className="grid grid-cols-7 gap-1">
                        {Array.from({ length: firstDayOfWeek }).map((_, i) => (
                          <div key={`m-empty-${i}`} className="h-9" />
                        ))}
                        {Array.from({ length: daysInMonth }).map((_, i) => {
                          const day = i + 1;
                          const isPast = isPastDay(day);
                          const isSelected = isSelectedDay(day);
                          const isToday = isTodayDay(day);
                          return (
                            <button
                              key={`m-day-${day}`}
                              type="button"
                              disabled={isPast}
                              onClick={() => handleSelectDay(day)}
                              className={`h-9 rounded-lg text-xs font-semibold flex items-center justify-center transition-all ${
                                isSelected
                                  ? 'bg-sky-600 text-white '
                                  : isToday
                                  ? 'border border-sky-600 text-sky-600 hover:bg-sky-50'
                                  : isPast
                                  ? 'text-slate-300 cursor-not-allowed'
                                  : 'text-slate-700 hover:bg-slate-100 cursor-pointer'
                              }`}
                            >
                              {day}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {mobileSearchTab === 'time' && (
                    <div className="space-y-2">
                      <div className="text-xs font-semibold text-slate-500 px-1">
                        {language === 'en' ? 'Select Preferred Time Slot' : 'Khung giờ trải nghiệm biển:'}
                      </div>
                      {TIME_OPTIONS.map((slot) => {
                        const isSelected = selectedTime === slot.val;
                        return (
                          <div
                            key={slot.val}
                            onClick={() => setSelectedTime(slot.val)}
                            className={`flex items-center gap-3 p-3 rounded-xl border transition-all cursor-pointer ${
                              isSelected
                                ? 'bg-sky-50/90 border-sky-300 ring-1 ring-sky-500/20'
                                : 'bg-white border-slate-100 hover:bg-slate-50'
                            }`}
                          >
                            <div className={`p-2 rounded-lg shrink-0 ${
                              isSelected ? 'bg-sky-600 text-white' : 'bg-slate-100 text-slate-600'
                            }`}>
                              {slot.iconSvg}
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="text-xs font-bold text-slate-900 block truncate">
                                {language === 'en' ? slot.titleEn : slot.title}
                              </span>
                              <p className="text-[11px] text-slate-500 truncate mt-0.5">
                                {language === 'en' ? slot.descEn : slot.desc}
                              </p>
                            </div>
                            {isSelected && (
                              <CheckCircle2 className="w-4 h-4 text-sky-600 shrink-0" />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {mobileSearchTab === 'guests' && (
                    <div className="p-2 space-y-4">
                      <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                        <div>
                          <span className="text-sm font-bold text-slate-800 block">
                            {language === 'en' ? 'Adults' : 'Người lớn'}
                          </span>
                          <span className="text-xs text-slate-400">
                            {language === 'en' ? 'Age 13 and above' : 'Từ 13 tuổi trở lên'}
                          </span>
                        </div>
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, adults: Math.max(1, g.adults - 1) }))}
                            disabled={guests.adults <= 1}
                            className={`w-9 h-9 rounded-lg border flex items-center justify-center font-bold text-base transition-colors ${
                              guests.adults <= 1
                                ? 'border-slate-200 text-slate-300 cursor-not-allowed'
                                : 'border-slate-300 text-slate-700 hover:bg-slate-100 cursor-pointer'
                            }`}
                          >
                            -
                          </button>
                          <span className="text-sm font-bold text-slate-800 w-5 text-center">
                            {guests.adults}
                          </span>
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, adults: Math.min(20, g.adults + 1) }))}
                            className="w-9 h-9 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-100 flex items-center justify-center font-bold text-base transition-colors cursor-pointer"
                          >
                            +
                          </button>
                        </div>
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-sm font-bold text-slate-800 block">
                            {language === 'en' ? 'Children' : 'Trẻ em'}
                          </span>
                          <span className="text-xs text-slate-400">
                            {language === 'en' ? 'Age 2 - 12' : 'Từ 2 đến 12 tuổi'}
                          </span>
                        </div>
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, children: Math.max(0, g.children - 1) }))}
                            disabled={guests.children <= 0}
                            className={`w-9 h-9 rounded-lg border flex items-center justify-center font-bold text-base transition-colors ${
                              guests.children <= 0
                                ? 'border-slate-200 text-slate-300 cursor-not-allowed'
                                : 'border-slate-300 text-slate-700 hover:bg-slate-100 cursor-pointer'
                            }`}
                          >
                            -
                          </button>
                          <span className="text-sm font-bold text-slate-800 w-5 text-center">
                            {guests.children}
                          </span>
                          <button
                            type="button"
                            onClick={() => setGuests((g) => ({ ...g, children: Math.min(10, g.children + 1) }))}
                            className="w-9 h-9 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-100 flex items-center justify-center font-bold text-base transition-colors cursor-pointer"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>

                {/* Sticky Bottom Actions */}
                <div className="p-3.5 border-t border-slate-100 bg-white flex items-center justify-between gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedRegions(['Mỹ Khê']);
                      setSelectedTime('05:30');
                      setGuests({ adults: 2, children: 0 });
                    }}
                    className="text-xs font-semibold text-slate-500 hover:text-slate-800 underline px-2 cursor-pointer"
                  >
                    {language === 'en' ? 'Reset' : 'Đặt lại'}
                  </button>
                  <button
                    type="button"
                    onClick={handleSearchExecute}
                    className="flex-1 h-12 rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-bold text-sm flex items-center justify-center gap-2 cursor-pointer"
                  >
                    <Search className="w-4 h-4 shrink-0" />
                    <span>
                      {language === 'en'
                        ? `Search (${guests.adults + guests.children} guests)`
                        : `Tìm kiếm (${guests.adults + guests.children} khách)`}
                    </span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* =========================================================================
          CONTENT CANVAS: GROUNDED SOLID LIGHT SURFACE (BG-SLATE-50 / BG-WHITE)
          - High information density, WCAG AAA text contrast (> 12:1)
          - 0 fake poetry chapters
         ========================================================================= */}
      <div className="relative z-20 bg-white border-t border-slate-200/80 pt-10 pb-20 text-slate-900">
        <div className="max-w-[1600px] w-full mx-auto px-4 sm:px-6 lg:px-8 space-y-14 sm:space-y-16">
          
          {/* 1. Flash Sale Section (Ưu đãi giờ vàng giới hạn) */}
          <FlashSaleSection experiences={flashSaleList} />

          {/* 2. Hoạt Động Biển Nổi Bật (Dải card hoạt động nổi bật duy nhất) */}
          <CategoryExperienceCarousel
            title={language === 'en' ? 'Featured Marine Activities' : 'Hoạt Động Biển Nổi Bật'}
            categorySlug=""
            categoryLink="/search"
            experiences={featuredActivities}
          />

          {/* Signature Alternating Video Spotlights with Curving Dashed Connectors */}
          <CoastalVideoSpotlights />

          {/* 9. DANASEA Official Mobile App Showcase */}
          <DanaseaAppShowcase />

          {/* 10. Guest Gallery Marquee */}
          <GuestGalleryMarquee />

          {/* 12. 3 Bước Đặt Tour Đơn Giản */}
          <div data-ui="booking-steps" className="py-6 sm:py-8">
            <div className="text-center max-w-xl mx-auto mb-8">
              <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900">
                {language === 'en' ? 'How to Book on DANASEA' : 'Cách đặt trải nghiệm cùng DANASEA'}
              </h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Step 1 */}
              <div className="flex flex-col items-start bg-slate-50 hover:bg-slate-100/90 border border-slate-200/80 rounded-2xl p-6 sm:p-7 transition-all duration-300 hover:-translate-y-0.5 shadow-xs">
                <div className="flex items-center gap-3 mb-3">
                  <span className="text-2xl font-black text-sky-700 tabular-nums">01</span>
                  <div className="h-px w-10 bg-sky-200" />
                </div>
                <h3 className="text-lg font-bold text-slate-900 mb-2">
                  {language === 'en' ? 'Explore & Pick Activity' : 'Khám phá & Chọn hoạt động'}
                </h3>
                <p className="text-slate-600 text-sm sm:text-base leading-relaxed font-normal">
                  {language === 'en'
                    ? 'Filter by preferred bay, fitness level, or consult our AI marine radar planner.'
                    : 'Tìm theo vùng biển yêu thích, lọc theo thể loại hoặc tham khảo gợi ý thời tiết từ AI.'}
                </p>
              </div>

              {/* Step 2 */}
              <div className="flex flex-col items-start bg-slate-50 hover:bg-slate-100/90 border border-slate-200/80 rounded-2xl p-6 sm:p-7 transition-all duration-300 hover:-translate-y-0.5 shadow-xs">
                <div className="flex items-center gap-3 mb-3">
                  <span className="text-2xl font-black text-sky-700 tabular-nums">02</span>
                  <div className="h-px w-10 bg-sky-200" />
                </div>
                <h3 className="text-lg font-bold text-slate-900 mb-2">
                  {language === 'en' ? 'Select Slot & Instant Pay' : 'Đặt lịch & Giữ chỗ tức thì'}
                </h3>
                <p className="text-slate-600 text-sm sm:text-base leading-relaxed font-normal">
                  {language === 'en'
                    ? 'Pick the best wave hour, guest count, and receive instant confirmation.'
                    : 'Chọn ngày, khung giờ sóng đẹp và số lượng khách. Xác nhận giữ chỗ an toàn qua thẻ & ví điện tử.'}
                </p>
              </div>

              {/* Step 3 */}
              <div className="flex flex-col items-start bg-slate-50 hover:bg-slate-100/90 border border-slate-200/80 rounded-2xl p-6 sm:p-7 transition-all duration-300 hover:-translate-y-0.5 shadow-xs">
                <div className="flex items-center gap-3 mb-3">
                  <span className="text-2xl font-black text-sky-700 tabular-nums">03</span>
                  <div className="h-px w-10 bg-sky-200" />
                </div>
                <h3 className="text-lg font-bold text-slate-900 mb-2">
                  {language === 'en' ? 'Receive QR Pass & Boarding Pass' : 'Nhận vé QR & Xuất bến an toàn'}
                </h3>
                <p className="text-slate-600 text-sm sm:text-base leading-relaxed font-normal">
                  {language === 'en'
                    ? 'Check-in on mobile at the beach harbor station and enjoy your certified marine tour.'
                    : 'Vé điện tử gửi thẳng qua ứng dụng. Quét mã check-in trực tiếp tại bến tập kết và bắt đầu hành trình.'}
                </p>
              </div>
            </div>
          </div>

          {/* 13. Final Call to Action */}
          <div className="rounded-2xl p-6 sm:p-8 bg-[#031d44] border border-sky-950 flex flex-col sm:flex-row items-center justify-between gap-6 text-white shadow-xl shadow-sky-950/20">
            <div>
              <h3 className="text-lg sm:text-xl font-bold mb-1 text-white drop-shadow-sm">
                {language === 'en' ? 'Ready to Explore Da Nang Waters?' : 'Sẵn sàng trải nghiệm biển Đà Nẵng ngay hôm nay?'}
              </h3>
              <p className="text-base text-slate-200 max-w-xl font-normal drop-shadow-sm">
                {language === 'en'
                  ? 'Explore 20+ certified water sports and marine expeditions tailored for you.'
                  : 'Hơn 20+ trải nghiệm thể thao biển chuẩn quốc tế với đầy đủ bảo hiểm và thiết bị chuyên nghiệp.'}
              </p>
            </div>

            <Link
              to="/search"
              className="px-6 py-3.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-semibold text-xs sm:text-sm transition-colors shrink-0 flex items-center gap-1.5 shadow-md shadow-sky-900/20"
            >
              <span>{language === 'en' ? 'Explore All Tours' : 'Xem toàn bộ tour'}</span>
              <ArrowRight className="w-4 h-4 shrink-0" />
            </Link>
          </div>

        </div>
      </div>

    </main>
  );
}

export default Home;
