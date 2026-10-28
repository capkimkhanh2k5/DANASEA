import React, { useState, useEffect, useRef, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useLanguageStore } from "../../../store/useLanguageStore";

// Rich Customer Home Data
import {
  FLASH_SALE_EXPERIENCES,
  SUP_KAYAK_EXPERIENCES,
  DIVING_EXPERIENCES,
  THRILL_EXPERIENCES,
  COMBO_SUNSET_EXPERIENCES,
} from "../../../data/customerHomeData";

// Customer Home Subcomponents
import { FlashSaleSection } from "../../../components/customer/FlashSaleSection";
import { CategoryExperienceCarousel } from "../../../components/customer/CategoryExperienceCarousel";
import { ExpandingBaysSection } from "../../../components/customer/ExpandingBaysSection";
import { OceanSportsShowcase } from "../../../components/customer/OceanSportsShowcase";
import { SmartOceanPlanner } from "../../../components/customer/SmartOceanPlanner";
import { GuestGalleryMarquee } from "../../../components/customer/GuestGalleryMarquee";
import { ScrollToTopButton } from "../../../components/customer/ScrollToTopButton";
import { CinematicSceneBackdrop } from "../../../components/customer/CinematicSceneBackdrop";
import { ScrollReveal } from "../../../components/common/ScrollReveal";
import { MarineWeatherPill } from "../../../components/customer/MarineWeatherPill";
import { serviceCatalogService } from "../../../services/serviceCatalogService";
import type { ServiceSummaryDto } from "../../../types/service";
import type { FlashSaleExperience, ActivityCardData } from "../../../data/customerHomeData";

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
  const [activeCustomPrompt, setActiveCustomPrompt] = useState<string | null>(null);
  const [chatInputValue, setChatInputValue] = useState('');
  const [isChatLoading, setIsChatLoading] = useState(false);
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);
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
  const searchRef = useRef<HTMLDivElement>(null);

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

  // Map live services to carousel formats
  const flashSaleList: FlashSaleExperience[] = liveServices.length > 0
    ? liveServices.slice(0, 4).map((s, idx) => ({
        id: s.id,
        title: s.name,
        titleEn: s.name,
        category: 'Ưu đãi biển',
        categoryEn: 'Marine Special',
        location: 'Đà Nẵng',
        locationEn: 'Da Nang Coast',
        duration: '2 giờ',
        durationEn: '2 hours',
        originalPrice: Math.round(Number(s.price) * 1.3),
        salePrice: Number(s.price),
        discountPercent: 23,
        rating: Number(s.averageRating) || 4.95,
        reviewCount: s.reviewCount || 100,
        slotsLeft: 3 + (idx % 4),
        totalSlots: 15,
        imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
      }))
    : FLASH_SALE_EXPERIENCES;

  const supFiltered = liveServices.filter((s) => {
    const n = s.name.toLowerCase();
    return n.includes('sup') || n.includes('kayak');
  });
  const supKayakList: ActivityCardData[] = supFiltered.length > 0
    ? supFiltered.map((s) => ({
        id: s.id,
        title: s.name,
        titleEn: s.name,
        category: 'Chèo SUP & Kayak',
        categoryEn: 'SUP & Kayak',
        location: 'Mân Thái, Sơn Trà',
        duration: '2 giờ',
        durationEn: '2 hours',
        price: Number(s.price),
        rating: Number(s.averageRating) || 4.95,
        reviewCount: s.reviewCount || 50,
        imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800',
      }))
    : SUP_KAYAK_EXPERIENCES;

  const divingFiltered = liveServices.filter((s) => {
    const n = s.name.toLowerCase();
    return n.includes('lặn') || n.includes('diving') || n.includes('san hô');
  });
  const divingList: ActivityCardData[] = divingFiltered.length > 0
    ? divingFiltered.map((s) => ({
        id: s.id,
        title: s.name,
        titleEn: s.name,
        category: 'Lặn ngắm san hô',
        categoryEn: 'Scuba Diving',
        location: 'Hòn Sụp, Sơn Trà',
        duration: '3.5 giờ',
        durationEn: '3.5 hours',
        price: Number(s.price),
        rating: Number(s.averageRating) || 4.96,
        reviewCount: s.reviewCount || 80,
        imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
      }))
    : DIVING_EXPERIENCES;

  const sunsetYachtFiltered = liveServices.filter((s) => {
    const n = s.name.toLowerCase();
    return n.includes('du thuyền') || n.includes('yacht') || n.includes('catamaran') || n.includes('hoàng hôn');
  });
  const sunsetYachtList: ActivityCardData[] = sunsetYachtFiltered.length > 0
    ? sunsetYachtFiltered.map((s) => ({
        id: s.id,
        title: s.name,
        titleEn: s.name,
        category: 'Du thuyền hoàng hôn',
        categoryEn: 'Sunset Yacht',
        location: 'Bến DHC Marina',
        duration: '2 giờ',
        durationEn: '2 hours',
        price: Number(s.price),
        rating: Number(s.averageRating) || 5.0,
        reviewCount: s.reviewCount || 90,
        imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
      }))
    : COMBO_SUNSET_EXPERIENCES;

  const thrillFiltered = liveServices.filter((s) => {
    const n = s.name.toLowerCase();
    return (
      n.includes('cano') ||
      n.includes('dù') ||
      n.includes('jetski') ||
      n.includes('mô tô nước') ||
      n.includes('phao chuối') ||
      n.includes('flying fish')
    );
  });
  const thrillList: ActivityCardData[] = thrillFiltered.length > 0
    ? thrillFiltered.map((s) => ({
        id: s.id,
        title: s.name,
        titleEn: s.name,
        category: 'Cảm giác mạnh & Cano',
        categoryEn: 'Thrill & Watersports',
        location: 'Biển Mỹ Khê',
        duration: '1 giờ',
        durationEn: '1 hour',
        price: Number(s.price),
        rating: Number(s.averageRating) || 4.93,
        reviewCount: s.reviewCount || 120,
        imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?w=800',
      }))
    : THRILL_EXPERIENCES;

  const baseMessage = activeCustomPrompt ? CUSTOM_PROMPT_DATA[activeCustomPrompt] : AI_BEACH_DATA[activeAIBeach];
  const displayMessages = useMemo(() => [baseMessage, ...chatHistory], [baseMessage, chatHistory]);
  const currentBeachData = AI_BEACH_DATA[activeAIBeach] || AI_BEACH_DATA['Mỹ Khê'];

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

  const formatDateDisplay = (isoStr: string) => {
    if (!isoStr) return language === 'en' ? 'Select date' : 'Chọn ngày';
    const [y, m, d] = isoStr.split('-').map(Number);
    const target = new Date(y, m - 1, d);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const diffDays = Math.round((target.getTime() - today.getTime()) / (1000 * 3600 * 24));
    
    if (diffDays === 0) return language === 'en' ? 'Today' : 'Hôm nay';
    if (diffDays === 1) return language === 'en' ? 'Tomorrow' : 'Ngày mai';
    return `${d}/${m}/${y}`;
  };

  const handleShortcutToday = () => {
    const today = new Date();
    setSelectedDate(today.toISOString().split('T')[0]);
    setActiveDropdown(null);
  };

  const handleShortcutTomorrow = () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    setSelectedDate(tomorrow.toISOString().split('T')[0]);
    setActiveDropdown(null);
  };

  const handleShortcutWeekend = () => {
    const d = new Date();
    const day = d.getDay();
    const diff = (6 - day + 7) % 7 || 7;
    d.setDate(d.getDate() + diff);
    setSelectedDate(d.toISOString().split('T')[0]);
    setActiveDropdown(null);
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

  // Fixed immutable chat submission handler
  const handleChatSubmit = () => {
    if (!chatInputValue.trim() || isChatLoading) return;
    const promptText = chatInputValue;

    setChatHistory(prev => [
      ...prev,
      {
        chatPrompt: promptText,
        chatReply: null,
      }
    ]);
    setChatInputValue('');
    setIsChatLoading(true);

    setTimeout(() => {
      const replyText = language === 'en'
        ? `DANASEA Maritime Radar verified: Best conditions at ${activeAIBeach} with ${currentBeachData.temp.val} temperature and ${currentBeachData.wave.val} swell. Would you like to reserve a slot?`
        : `DANASEA AI đã tra cứu dữ liệu hải văn: Tại ${activeAIBeach}, nhiệt độ hiện tại ${currentBeachData.temp.val}, sóng ${currentBeachData.wave.val}. Bạn có muốn xem thêm lịch khởi hành chi tiết không?`;

      setChatHistory(prev =>
        prev.map((msg, idx) =>
          idx === prev.length - 1 ? { ...msg, chatReply: replyText } : msg
        )
      );
      setIsChatLoading(false);
    }, 1000);
  };

  const handleSearchExecute = () => {
    const locParam = selectedRegions.join(',');
    navigate(`/search?location=${encodeURIComponent(locParam)}&date=${encodeURIComponent(selectedDate)}&time=${encodeURIComponent(selectedTime)}&guests=${guests.adults + guests.children}&adults=${guests.adults}&children=${guests.children}`);
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
      {/* =========================================================================
          FULL-PAGE DYNAMIC CINEMATIC SCENE BACKDROP
         ========================================================================= */}
      <CinematicSceneBackdrop />

      {/* =========================================================================
          HERO: BÃI BIỂN TOÀN MÀN HÌNH & THANH TÌM KIẾM TINH GỌN
         ========================================================================= */}
      <section
        id="scene-hero"
        data-scene="hero"
        className="relative z-20 w-full min-h-[480px] sm:min-h-[540px] flex flex-col justify-center items-center pt-28 sm:pt-32 pb-12 overflow-visible"
      >
        <div className="max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 text-center flex flex-col items-center overflow-visible">
          {/* Large Headline */}
          <h1 className="animate-hero-2 text-3xl sm:text-5xl lg:text-6xl font-extrabold text-white tracking-tight drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_24px_rgba(0,0,0,0.7)] mb-2.5">
            {language === 'en' ? 'Touch The Ocean,' : 'Chạm sóng biển,'}{' '}
            <span className="text-cyan-300">
              {language === 'en' ? 'Craft Your Journey' : 'mở chuyến đi riêng'}
            </span>
          </h1>

          {/* Subtitle */}
          <p className="animate-hero-3 text-sm sm:text-base text-slate-100 max-w-2xl mb-6 drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.95)] drop-shadow-[0_4px_12px_rgba(0,0,0,0.6)] font-medium leading-relaxed">
            {language === 'en'
              ? 'Handpicked certified marine adventures. Paddleboarding, scuba diving, island speedboats, and tranquil sunset bays.'
              : 'Nền tảng đặt tour thể thao biển chính hãng tại Đà Nẵng. Khám phá bình minh Mỹ Khê, san hô Sơn Trà và những vịnh biển ngọc bích.'}
          </p>

          {/* Professional Airbnb-style Search Capsule (4 Segments: Địa điểm | Ngày | Khung giờ | Khách) */}
          <div
            ref={searchRef}
            className="animate-hero-4 w-full max-w-4xl bg-white/95 backdrop-blur-xl rounded-3xl sm:rounded-full p-2 sm:p-2 shadow-[0_20px_60px_rgba(0,0,0,0.28)] border border-white/80 ring-1 ring-slate-900/5 transition-all relative z-30"
          >
            <div className="flex flex-col sm:flex-row sm:items-center relative">
              {/* 1. Destination / Địa điểm */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('region')}
                  aria-expanded={activeDropdown === 'region'}
                  className={`w-full flex flex-col justify-center px-4 py-2.5 sm:px-5 sm:py-2.5 rounded-2xl sm:rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'region'
                      ? 'bg-white shadow-[0_4px_16px_rgba(0,0,0,0.1)] ring-1 ring-slate-200'
                      : activeDropdown !== null
                      ? 'opacity-60 hover:opacity-90 hover:bg-slate-100/60'
                      : 'hover:bg-slate-100/80'
                  }`}
                >
                  <span className="text-[10px] sm:text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                    {t.hero.search.destination}
                  </span>
                  <span className="text-xs sm:text-sm font-semibold text-slate-900 truncate block mt-0.5">
                    {selectedRegions.length > 0
                      ? selectedRegions.join(', ')
                      : (language === 'en' ? 'Select bays' : 'Chọn điểm đến')}
                  </span>
                </button>

                {/* Popover: Rich Bay Cards (2x2 Grid) */}
                {activeDropdown === 'region' && (
                  <div className="animate-popover-in absolute top-full left-0 sm:left-1/2 sm:-translate-x-1/2 mt-3 w-80 sm:w-[500px] p-3.5 sm:p-4 bg-white border border-slate-200/90 rounded-3xl shadow-2xl z-50 text-left">
                    <div className="flex items-center justify-between pb-2 mb-2.5 border-b border-slate-100">
                      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                        {language === 'en' ? 'Explore Coastal Bays' : 'Khám phá các vịnh biển'}
                      </span>
                      <span className="text-[11px] text-slate-400">
                        {selectedRegions.length} {language === 'en' ? 'selected' : 'đã chọn'}
                      </span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {BEACH_OPTIONS.map((bay) => {
                        const isSelected = selectedRegions.includes(bay.name);
                        return (
                          <div
                            key={bay.name}
                            onClick={() => handleRegionChange(bay.name)}
                            className={`flex items-center gap-2.5 p-2 rounded-2xl cursor-pointer transition-all border ${
                              isSelected
                                ? 'bg-cyan-50/70 border-cyan-400/80 shadow-xs ring-1 ring-cyan-500/30'
                                : 'bg-white border-slate-100 hover:bg-slate-50 hover:border-slate-200'
                            }`}
                          >
                            <img
                              src={bay.img}
                              alt={bay.name}
                              className="w-11 h-11 rounded-xl object-cover shrink-0 shadow-xs"
                            />
                            <div className="flex-1 min-w-0">
                              <span className="text-xs font-bold text-slate-900 truncate block">
                                {language === 'en' ? bay.nameEn : bay.name}
                              </span>
                              <p className="text-[10px] text-cyan-700 font-medium truncate mt-0.5">
                                {language === 'en' ? bay.tagEn : bay.tag}
                              </p>
                              <p className="text-[10px] text-slate-400 truncate">
                                {language === 'en' ? bay.descEn : bay.desc}
                              </p>
                            </div>
                            <div className={`w-4 h-4 rounded-full border flex items-center justify-center shrink-0 ${
                              isSelected
                                ? 'bg-cyan-600 border-cyan-600 text-white'
                                : 'border-slate-300 bg-white'
                            }`}>
                              {isSelected && <span className="material-symbols-outlined text-[12px]">check</span>}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Vertical Divider 1 */}
              <div
                className={`hidden sm:block w-px h-7 bg-slate-200 shrink-0 transition-opacity ${
                  activeDropdown === 'region' || activeDropdown === 'date' ? 'opacity-0' : 'opacity-100'
                }`}
              />

              {/* 2. Date / Ngày */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('date')}
                  aria-expanded={activeDropdown === 'date'}
                  className={`w-full flex flex-col justify-center px-4 py-2.5 sm:px-5 sm:py-2.5 rounded-2xl sm:rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'date'
                      ? 'bg-white shadow-[0_4px_16px_rgba(0,0,0,0.1)] ring-1 ring-slate-200'
                      : activeDropdown !== null
                      ? 'opacity-60 hover:opacity-90 hover:bg-slate-100/60'
                      : 'hover:bg-slate-100/80'
                  }`}
                >
                  <span className="text-[10px] sm:text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                    {language === 'en' ? 'Date' : 'Ngày đi'}
                  </span>
                  <span className="text-xs sm:text-sm font-semibold text-slate-900 truncate block mt-0.5">
                    {formatDateDisplay(selectedDate)}
                  </span>
                </button>

                {/* Popover: Shortcuts & Mini Calendar */}
                {activeDropdown === 'date' && (
                  <div className="animate-popover-in absolute top-full left-0 sm:left-1/2 sm:-translate-x-1/2 mt-3 w-80 sm:w-84 p-4 bg-white border border-slate-200/90 rounded-3xl shadow-2xl z-50 text-left">
                    {/* Quick Shortcuts */}
                    <div className="flex items-center gap-1.5 pb-3 border-b border-slate-100 mb-3">
                      <button
                        type="button"
                        onClick={handleShortcutToday}
                        className="flex-1 py-1 px-2 text-[11px] font-semibold rounded-lg bg-slate-100 hover:bg-cyan-50 hover:text-cyan-700 text-slate-700 transition-colors text-center"
                      >
                        {language === 'en' ? 'Today' : 'Hôm nay'}
                      </button>
                      <button
                        type="button"
                        onClick={handleShortcutTomorrow}
                        className="flex-1 py-1 px-2 text-[11px] font-semibold rounded-lg bg-slate-100 hover:bg-cyan-50 hover:text-cyan-700 text-slate-700 transition-colors text-center"
                      >
                        {language === 'en' ? 'Tomorrow' : 'Ngày mai'}
                      </button>
                      <button
                        type="button"
                        onClick={handleShortcutWeekend}
                        className="flex-1 py-1 px-2 text-[11px] font-semibold rounded-lg bg-slate-100 hover:bg-cyan-50 hover:text-cyan-700 text-slate-700 transition-colors text-center"
                      >
                        {language === 'en' ? 'Weekend' : 'Cuối tuần'}
                      </button>
                    </div>

                    {/* Month Header */}
                    <div className="flex items-center justify-between mb-2 px-1">
                      <button
                        type="button"
                        onClick={handlePrevMonth}
                        aria-label="Previous month"
                        className="w-7 h-7 flex items-center justify-center rounded-full hover:bg-slate-100 text-slate-600 transition-colors"
                      >
                        <span className="material-symbols-outlined text-[18px]">chevron_left</span>
                      </button>
                      <span className="text-xs font-bold text-slate-900">
                        {language === 'en'
                          ? calendarViewDate.toLocaleString('en-US', { month: 'long', year: 'numeric' })
                          : `Tháng ${calendarMonth + 1}, ${calendarYear}`}
                      </span>
                      <button
                        type="button"
                        onClick={handleNextMonth}
                        aria-label="Next month"
                        className="w-7 h-7 flex items-center justify-center rounded-full hover:bg-slate-100 text-slate-600 transition-colors"
                      >
                        <span className="material-symbols-outlined text-[18px]">chevron_right</span>
                      </button>
                    </div>

                    {/* Days of Week */}
                    <div className="grid grid-cols-7 gap-1 text-center mb-1">
                      {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((dw) => (
                        <span key={dw} className="text-[10px] font-bold text-slate-400 py-1">
                          {dw}
                        </span>
                      ))}
                    </div>

                    {/* Calendar Day Grid */}
                    <div className="grid grid-cols-7 gap-1 text-center">
                      {Array.from({ length: firstDayOfWeek }).map((_, i) => (
                        <div key={`empty-${i}`} className="h-8" />
                      ))}
                      {Array.from({ length: daysInMonth }).map((_, i) => {
                        const day = i + 1;
                        const past = isPastDay(day);
                        const selected = isSelectedDay(day);
                        const isToday = isTodayDay(day);

                        return (
                          <button
                            key={`day-${day}`}
                            type="button"
                            disabled={past}
                            onClick={() => handleSelectDay(day)}
                            className={`h-8 w-8 mx-auto rounded-full text-xs font-semibold flex items-center justify-center transition-all ${
                              selected
                                ? 'bg-cyan-600 text-white shadow-md'
                                : past
                                ? 'text-slate-300 cursor-not-allowed'
                                : isToday
                                ? 'text-cyan-700 bg-cyan-50 font-bold ring-1 ring-cyan-500'
                                : 'text-slate-700 hover:bg-slate-100'
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

              {/* Vertical Divider 2 */}
              <div
                className={`hidden sm:block w-px h-7 bg-slate-200 shrink-0 transition-opacity ${
                  activeDropdown === 'date' || activeDropdown === 'time' ? 'opacity-0' : 'opacity-100'
                }`}
              />

              {/* 3. Time Slot / Khung giờ */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('time')}
                  aria-expanded={activeDropdown === 'time'}
                  className={`w-full flex flex-col justify-center px-4 py-2.5 sm:px-5 sm:py-2.5 rounded-2xl sm:rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'time'
                      ? 'bg-white shadow-[0_4px_16px_rgba(0,0,0,0.1)] ring-1 ring-slate-200'
                      : activeDropdown !== null
                      ? 'opacity-60 hover:opacity-90 hover:bg-slate-100/60'
                      : 'hover:bg-slate-100/80'
                  }`}
                >
                  <span className="text-[10px] sm:text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                    {language === 'en' ? 'Time Slot' : 'Khung giờ'}
                  </span>
                  <span className="text-xs sm:text-sm font-semibold text-slate-900 truncate block mt-0.5">
                    {selectedTimeObj
                      ? (language === 'en' ? selectedTimeObj.titleEn : selectedTimeObj.title)
                      : selectedTime}
                  </span>
                </button>

                {/* Popover: 4 Large Sun/Ocean Time Chips (2x2 Grid) */}
                {activeDropdown === 'time' && (
                  <div className="animate-popover-in absolute top-full left-0 sm:left-1/2 sm:-translate-x-1/2 mt-3 w-80 sm:w-[500px] p-3.5 sm:p-4 bg-white border border-slate-200/90 rounded-3xl shadow-2xl z-50 text-left">
                    <div className="flex items-center justify-between pb-2 mb-2.5 border-b border-slate-100">
                      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                        {language === 'en' ? 'Select Departure Window' : 'Khung giờ trải nghiệm tối ưu'}
                      </span>
                      <span className="text-[11px] text-cyan-700 font-semibold">
                        {language === 'en' ? 'Synced with Radar' : 'Đồng bộ Hải văn'}
                      </span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {TIME_OPTIONS.map((opt) => {
                        const isSelected = selectedTime === opt.val;
                        return (
                          <button
                            key={opt.val}
                            type="button"
                            onClick={() => {
                              setSelectedTime(opt.val);
                              setActiveDropdown(null);
                            }}
                            className={`w-full text-left p-2.5 rounded-2xl transition-all border flex items-center gap-2.5 cursor-pointer ${
                              isSelected
                                ? 'bg-cyan-50/70 border-cyan-400/80 ring-2 ring-cyan-500/20 shadow-xs'
                                : 'bg-white border-slate-100 hover:border-slate-200 hover:bg-slate-50'
                            }`}
                          >
                            <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                              isSelected ? 'bg-cyan-600 text-white shadow-xs' : 'bg-slate-100 text-slate-600'
                            }`}>
                              {opt.iconSvg}
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="text-xs font-bold text-slate-900 block truncate">
                                {language === 'en' ? opt.titleEn : opt.title}
                              </span>
                              <span className="text-[10px] text-slate-500 block truncate mt-0.5">
                                {language === 'en' ? opt.descEn : opt.desc}
                              </span>
                            </div>
                            {isSelected && (
                              <span className="material-symbols-outlined text-cyan-600 text-[18px] shrink-0">
                                check_circle
                              </span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Vertical Divider 3 */}
              <div
                className={`hidden sm:block w-px h-7 bg-slate-200 shrink-0 transition-opacity ${
                  activeDropdown === 'time' || activeDropdown === 'guests' ? 'opacity-0' : 'opacity-100'
                }`}
              />

              {/* 4. Guests / Khách */}
              <div className="relative flex-1">
                <button
                  type="button"
                  onClick={() => toggleDropdown('guests')}
                  aria-expanded={activeDropdown === 'guests'}
                  className={`w-full flex flex-col justify-center px-4 py-2.5 sm:px-5 sm:py-2.5 rounded-2xl sm:rounded-full transition-all text-left cursor-pointer ${
                    activeDropdown === 'guests'
                      ? 'bg-white shadow-[0_4px_16px_rgba(0,0,0,0.1)] ring-1 ring-slate-200'
                      : activeDropdown !== null
                      ? 'opacity-60 hover:opacity-90 hover:bg-slate-100/60'
                      : 'hover:bg-slate-100/80'
                  }`}
                >
                  <span className="text-[10px] sm:text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                    {t.hero.search.guests}
                  </span>
                  <span className="text-xs sm:text-sm font-semibold text-slate-900 truncate block mt-0.5">
                    {guests.adults + guests.children} {language === 'en' ? 'Guests' : 'Khách'}
                  </span>
                </button>

                {/* Popover: 2-Row Stepper for Adults & Children with age annotations */}
                {activeDropdown === 'guests' && (
                  <div className="animate-popover-in absolute top-full right-0 mt-3 w-72 sm:w-80 p-4 bg-white border border-slate-200/90 rounded-3xl shadow-2xl z-50 text-left space-y-4">
                    {/* Row 1: Adults */}
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-xs font-bold text-slate-900 block">
                          {language === 'en' ? 'Adults' : 'Người lớn'}
                        </span>
                        <span className="text-[11px] text-slate-400 block">
                          {language === 'en' ? 'Age 12 and above' : 'Từ 12 tuổi trở lên'}
                        </span>
                      </div>
                      <div className="flex items-center gap-2.5">
                        <button
                          type="button"
                          disabled={guests.adults <= 1}
                          onClick={() => setGuests({ ...guests, adults: Math.max(1, guests.adults - 1) })}
                          aria-label="Decrease adults"
                          className="w-8 h-8 rounded-full border border-slate-300 hover:border-slate-400 bg-white text-slate-700 font-bold flex items-center justify-center transition-all disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          −
                        </button>
                        <span className="text-sm font-bold w-5 text-center text-slate-800">
                          {guests.adults}
                        </span>
                        <button
                          type="button"
                          disabled={guests.adults >= 20}
                          onClick={() => setGuests({ ...guests, adults: guests.adults + 1 })}
                          aria-label="Increase adults"
                          className="w-8 h-8 rounded-full border border-slate-300 hover:border-slate-400 bg-white text-slate-700 font-bold flex items-center justify-center transition-all disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          +
                        </button>
                      </div>
                    </div>

                    <div className="w-full h-px bg-slate-100" />

                    {/* Row 2: Children */}
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-xs font-bold text-slate-900 block">
                          {language === 'en' ? 'Children' : 'Trẻ em'}
                        </span>
                        <span className="text-[11px] text-slate-400 block">
                          {language === 'en' ? 'Age 2 – 11' : 'Từ 2 – 11 tuổi'}
                        </span>
                      </div>
                      <div className="flex items-center gap-2.5">
                        <button
                          type="button"
                          disabled={guests.children <= 0}
                          onClick={() => setGuests({ ...guests, children: Math.max(0, guests.children - 1) })}
                          aria-label="Decrease children"
                          className="w-8 h-8 rounded-full border border-slate-300 hover:border-slate-400 bg-white text-slate-700 font-bold flex items-center justify-center transition-all disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          −
                        </button>
                        <span className="text-sm font-bold w-5 text-center text-slate-800">
                          {guests.children}
                        </span>
                        <button
                          type="button"
                          disabled={guests.children >= 10}
                          onClick={() => setGuests({ ...guests, children: guests.children + 1 })}
                          aria-label="Increase children"
                          className="w-8 h-8 rounded-full border border-slate-300 hover:border-slate-400 bg-white text-slate-700 font-bold flex items-center justify-center transition-all disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                        >
                          +
                        </button>
                      </div>
                    </div>

                    <div className="pt-1 border-t border-slate-100 text-[10px] text-slate-400 text-center">
                      {language === 'en'
                        ? 'Children under 2 travel free with adult'
                        : 'Trẻ dưới 2 tuổi miễn phí vé & đi cùng phụ huynh'}
                    </div>
                  </div>
                )}
              </div>

              {/* 5. Search CTA: Round button expanding when activeDropdown is open */}
              <div className="p-1 sm:p-0 sm:pr-1 flex justify-end shrink-0">
                <button
                  type="button"
                  onClick={handleSearchExecute}
                  aria-label="Search"
                  className={`h-11 sm:h-12 rounded-full bg-gradient-to-r from-cyan-600 to-teal-600 hover:from-cyan-500 hover:to-teal-500 text-white flex items-center justify-center shadow-lg hover:shadow-cyan-500/30 hover:scale-[1.02] active:scale-95 transition-all cursor-pointer ${
                    activeDropdown !== null
                      ? 'w-full sm:w-auto px-5 gap-2'
                      : 'w-full sm:w-12 px-0'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">search</span>
                  {activeDropdown !== null && (
                    <span className="text-xs font-bold tracking-wide">
                      {language === 'en' ? 'Search' : 'Tìm kiếm'}
                    </span>
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* Real-time Marine Weather & Safety Pill */}
          <div className="animate-hero-5 mt-4 flex justify-center w-full relative z-20">
            <MarineWeatherPill />
          </div>

        </div>
      </section>

      {/* =========================================================================
          KHOẢNG THỞ 01 (BÌNH MINH)
         ========================================================================= */}
      <div className="py-20 sm:py-28 text-center max-w-3xl mx-auto px-4 select-none">
        <span className="text-xs font-mono tracking-widest text-white/80 uppercase block mb-3 drop-shadow-sm">
          {language === 'en' ? 'Chapter 01 • Dawn Awakening' : 'Chương 01 • Bình Minh Thức Giấc'}
        </span>
        <p className="text-lg sm:text-2xl font-medium text-white drop-shadow-[0_3px_12px_rgba(0,0,0,0.65)] leading-relaxed italic">
          {language === 'en'
            ? '"When the first golden rays awaken the quiet bay, Da Nang’s ocean breathes in its purest serenity."'
            : '"Khi ánh bình minh đầu tiên nhuộm vàng mặt vịnh, biển Đà Nẵng thức giấc trong sự tĩnh lặng thuần khiết nhất."'}
        </p>
      </div>

      {/* =========================================================================
          CHƯƠNG 01: BÌNH MINH & MẶT NƯỚC ÊM (PANEL SÁNG LIỀN KHỐI)
         ========================================================================= */}
      <section id="scene-chapter-1" data-scene="hero" className="max-w-[1600px] w-full mx-auto px-3 sm:px-6 lg:px-8">
        <ScrollReveal>
          <div className="space-y-12 sm:space-y-16">
            {/* Flash Sale Section */}
            <FlashSaleSection experiences={flashSaleList} />

            {/* Carousel SUP & Kayak Biển */}
            <CategoryExperienceCarousel
              title={t.sections.supKayakTitle}



              categorySlug="cheo-sup-kayak"
              experiences={supKayakList}
            />
          </div>
        </ScrollReveal>
      </section>

      {/* =========================================================================
          KHOẢNG THỞ 02 (CÙNG NHAU)
         ========================================================================= */}
      <div className="py-20 sm:py-28 text-center max-w-3xl mx-auto px-4 select-none">
        <span className="text-xs font-mono tracking-widest text-white/80 uppercase block mb-3 drop-shadow-sm">
          {language === 'en' ? 'Chapter 02 • Together Over Waves' : 'Chương 02 • Cùng Nhau Vượt Sóng'}
        </span>
        <p className="text-lg sm:text-2xl font-medium text-white drop-shadow-[0_3px_12px_rgba(0,0,0,0.65)] leading-relaxed italic">
          {language === 'en'
            ? '"The sweetest adventure is not measured in distance, but in the laughter shared over crystal tides."'
            : '"Hành trình kỳ diệu nhất không phải là đi xa bao nhiêu, mà là cùng ai vượt sóng ngắm nhìn rạn san hô nguyên sơ."'}
        </p>
      </div>

      {/* =========================================================================
          CHƯƠNG 02: CÙNG NHAU & VỊNH ĐẢO (PANEL SÁNG LIỀN KHỐI)
         ========================================================================= */}
      <section id="scene-chapter-2" data-scene="bays" className="max-w-[1600px] w-full mx-auto px-3 sm:px-6 lg:px-8">
        <ScrollReveal>
          <div className="space-y-12 sm:space-y-16">
            {/* Vịnh Biển Đà Nẵng */}
            <ExpandingBaysSection />

            {/* Carousel Lặn biển & San hô Sơn Trà */}
            <CategoryExperienceCarousel
              title={t.sections.divingTitle}



              categorySlug="lan-ngam-san-ho"
              experiences={divingList}
            />

            {/* Carousel Combo Trọn Gói & Hoàng Hôn */}
            <CategoryExperienceCarousel
              title={t.sections.comboTitle}



              categorySlug="du-thuyen-ngam-hoang-hon"
              experiences={sunsetYachtList}
            />
          </div>
        </ScrollReveal>
      </section>

      {/* =========================================================================
          KHOẢNG THỞ 03 (SÓNG & NĂNG LƯỢNG ĐẠI DƯƠNG)
         ========================================================================= */}
      <div className="py-20 sm:py-28 text-center max-w-3xl mx-auto px-4 select-none">
        <span className="text-xs font-mono tracking-widest text-white/80 uppercase block mb-3 drop-shadow-sm">
          {language === 'en' ? 'Chapter 03 • Ocean Pulse & Energy' : 'Chương 03 • Sóng & Năng Lượng Biển'}
        </span>
        <p className="text-lg sm:text-2xl font-medium text-white drop-shadow-[0_3px_12px_rgba(0,0,0,0.65)] leading-relaxed italic">
          {language === 'en'
            ? '"Let the ocean waves sweep away every hesitation, unleashing boundless energy upon the sea."'
            : '"Để tiếng sóng cuốn phăng âu lo, đón nhận trọn vẹn sự tự do và cảm giác phấn khích giữa đại dương bao la."'}
        </p>
      </div>

      {/* =========================================================================
          CHƯƠNG 03: SÓNG & THỂ THAO BIỂN (SPLIT STICKY TRÊN PANEL SÁNG)
         ========================================================================= */}
      <section id="scene-chapter-3" data-scene="sports" className="max-w-[1600px] w-full mx-auto px-3 sm:px-6 lg:px-8">
        <ScrollReveal>
          <div className="space-y-12 sm:space-y-16">
            {/* Split Sticky Thể Thao Biển */}
            <OceanSportsShowcase />

            {/* Carousel Thể Thao Cảm Giác Mạnh & Cano */}
            <CategoryExperienceCarousel
              title={t.sections.thrillTitle}



              categorySlug="cano-du-bay"
              experiences={thrillList}
            />
          </div>
        </ScrollReveal>
      </section>

      {/* =========================================================================
          KHOẢNG THỞ 04 (KHÁM PHÁ & THẤU HIỂU BIỂN)
         ========================================================================= */}
      <div className="py-20 sm:py-28 text-center max-w-3xl mx-auto px-4 select-none">
        <span className="text-xs font-mono tracking-widest text-white/80 uppercase block mb-3 drop-shadow-sm">
          {language === 'en' ? 'Chapter 04 • Harmony with the Tides' : 'Chương 04 • Thấu Hiểu Nhịp Biển'}
        </span>
        <p className="text-lg sm:text-2xl font-medium text-white drop-shadow-[0_3px_12px_rgba(0,0,0,0.65)] leading-relaxed italic">
          {language === 'en'
            ? '"A memorable voyage begins with understanding the tides, the breeze, and the heartbeat of the coast."'
            : '"Một chuyến đi hoàn hảo bắt đầu từ việc thấu hiểu thủy triều, hướng gió và nhịp thở của biển cả."'}
        </p>
      </div>

      {/* =========================================================================
          CHƯƠNG 04: KHÁM PHÁ & TRỢ LÝ HẢI VĂN AI (PANEL SÁNG LIỀN KHỐI)
         ========================================================================= */}
      <section id="scene-chapter-4" data-scene="sunset" className="max-w-[1600px] w-full mx-auto px-3 sm:px-6 lg:px-8">
        <ScrollReveal>
          <div className="space-y-12 sm:space-y-16">
            {/* Smart Ocean Planner */}
            <SmartOceanPlanner />

            {/* AI Maritime Concierge */}
            <div id="ai-concierge">
              <div className="mb-6">
                <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_20px_rgba(0,0,0,0.7)]">
                  {t.aiStudio.title}
                </h2>
                
              </div>

              {/* AI Assistant Unified Chat Container */}
              <div className="max-w-4xl mx-auto bg-white/10 backdrop-blur-xl rounded-3xl p-5 sm:p-6 border border-white/20 shadow-2xl flex flex-col h-[380px]">
                {/* Messages Scroll Area */}
                <div ref={chatContainerRef} className="flex-1 overflow-y-auto space-y-3.5 pr-2">
                  {displayMessages.map((msg, idx) => (
                    <div key={idx} className="space-y-2 animate-fadeIn">
                      {/* User message */}
                      <div className="flex justify-end">
                        <div className="bg-cyan-600/90 backdrop-blur-sm text-white text-xs font-medium px-4 py-2.5 rounded-2xl rounded-br-none max-w-md shadow-md border border-cyan-400/30">
                          {language === 'en' ? (msg.chatPromptEn || msg.chatPrompt) : msg.chatPrompt}
                        </div>
                      </div>

                      {/* AI response */}
                      {msg.chatReply && (
                        <div className="flex justify-start">
                          <div className="bg-white/15 backdrop-blur-xl text-white text-xs leading-relaxed px-4.5 py-3.5 rounded-2xl rounded-bl-none max-w-lg border border-white/25 shadow-xl">
                            <div className="flex items-center gap-1.5 mb-1.5 text-cyan-300 font-bold text-[11px]">
                              <span className="material-symbols-outlined text-[14px]">smart_toy</span>
                              <span>DANASEA AI</span>
                            </div>
                            <p className="text-white/95">{language === 'en' ? (msg.chatReplyEn || msg.chatReply) : msg.chatReply}</p>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                  {isChatLoading && (
                    <div className="flex justify-start">
                      <div className="bg-white/15 backdrop-blur-xl text-cyan-200 text-xs px-4 py-2.5 rounded-2xl border border-white/25 shadow-md animate-pulse">
                        {language === 'en' ? 'DANASEA AI is analyzing marine data...' : 'DANASEA AI đang phân tích dữ liệu hải văn...'}
                      </div>
                    </div>
                  )}
                </div>

                {/* Quick Suggestion Chips inside Chat */}
                <div className="pt-3 pb-2 flex flex-wrap items-center gap-2 border-t border-white/15">
                  <span className="text-[11px] font-bold text-white/70 uppercase tracking-wider shrink-0 mr-1">
                    {language === 'en' ? 'Suggestions:' : 'Gợi ý nhanh:'}
                  </span>
                  {[
                    { id: 'group', label: language === 'en' ? 'Group of 6, half-day tour?' : 'Nhóm 6 người đi nửa ngày?' },
                    { id: 'couple', label: language === 'en' ? 'Romantic sunset for couples?' : 'Cặp đôi thư giãn hoàng hôn?' },
                    { id: 'weather', label: language === 'en' ? 'Best timing for sunny day?' : 'Nắng nóng nên chơi giờ nào?' },
                  ].map((prompt) => (
                    <button
                      key={prompt.id}
                      type="button"
                      onClick={() => { setActiveCustomPrompt(prompt.id); }}
                      className="px-3 py-1.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 hover:border-cyan-300 text-xs font-medium text-white transition-all cursor-pointer backdrop-blur-sm"
                    >
                      {prompt.label}
                    </button>
                  ))}
                </div>

                {/* Input Bar */}
                <div className="pt-2 flex items-center gap-2">
                  <input
                    type="text"
                    value={chatInputValue}
                    onChange={(e) => setChatInputValue(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleChatSubmit()}
                    placeholder={language === 'en' ? 'Ask anything about Da Nang beaches & activities...' : 'Hỏi bất kỳ điều gì về biển và hoạt động tại Đà Nẵng...'}
                    className="flex-1 bg-white/10 backdrop-blur-sm border border-white/25 rounded-xl px-3.5 py-2.5 text-xs text-white placeholder-white/60 focus:outline-none focus:border-cyan-400 focus:bg-white/20 transition-all"
                  />
                  <button
                    type="button"
                    onClick={handleChatSubmit}
                    disabled={isChatLoading || !chatInputValue.trim()}
                    className="px-5 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-xs shadow-md transition-all disabled:opacity-40 cursor-pointer flex items-center gap-1.5 shrink-0"
                  >
                    <span>{language === 'en' ? 'Send' : 'Gửi'}</span>
                    <span className="material-symbols-outlined text-[14px]">send</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </ScrollReveal>
      </section>

      {/* =========================================================================
          PHẦN KẾT: HỒI ỨC, CÁCH ĐẶT TOUR & CTA (PANEL SÁNG LIỀN KHỐI)
         ========================================================================= */}
      <section id="scene-footer" data-scene="sunset" className="relative z-20 max-w-[1600px] w-full mx-auto px-3 sm:px-6 lg:px-8 my-12">
        <ScrollReveal>
          <div className="space-y-12 sm:space-y-16">
            {/* Gallery Khách */}
            <GuestGalleryMarquee />

            {/* 3 Bước Đặt Tour Đơn Giản */}
            <div>
              <div className="text-center max-w-xl mx-auto mb-8">
                <h2 className="text-2xl sm:text-3xl font-extrabold text-white drop-shadow-[0_2px_10px_rgba(0,0,0,0.75)]">
                  {language === 'en' ? 'How to Book on DANASEA' : 'Cách đặt trải nghiệm cùng DANASEA'}
                </h2>
                <p className="text-xs sm:text-sm text-white/90 mt-1 font-medium drop-shadow-[0_1px_6px_rgba(0,0,0,0.65)]">
                  {language === 'en' ? 'Three quick steps from browsing to hitting the waves' : 'Ba bước nhanh chóng từ lúc chọn tour đến khi bước chân xuống mặt nước'}
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 relative z-20">
                {/* Step 1 */}
                <div className="relative z-20 p-6 sm:p-7 rounded-3xl bg-transparent border border-white/20 hover:border-white/40 hover:-translate-y-1.5 transition-all group flex flex-col items-start">
                  <div className="w-full flex items-center justify-between mb-3">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-amber-500 to-orange-500 text-white flex items-center justify-center shadow-md">
                      <span className="material-symbols-outlined text-[24px]">explore</span>
                    </div>
                    <span className="text-4xl sm:text-5xl font-mono font-black text-white/30 group-hover:text-white/50 transition-colors select-none">
                      01
                    </span>
                  </div>
                  <h3 className="text-base sm:text-lg font-bold text-white mb-2 drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
                    {language === 'en' ? 'Explore & Pick Activity' : 'Khám phá & Chọn hoạt động'}
                  </h3>
                  <p className="text-slate-100 text-xs sm:text-sm leading-relaxed font-medium drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.8)]">
                    {language === 'en'
                      ? 'Filter by preferred bay, fitness level, or consult our AI marine radar planner.'
                      : 'Tìm theo vùng biển yêu thích, lọc theo thể loại hoặc tham khảo gợi ý thời tiết từ AI.'}
                  </p>
                </div>

                {/* Step 2 */}
                <div className="relative z-20 p-6 sm:p-7 rounded-3xl bg-transparent border border-white/20 hover:border-white/40 hover:-translate-y-1.5 transition-all group flex flex-col items-start">
                  <div className="w-full flex items-center justify-between mb-3">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-cyan-600 to-teal-600 text-white flex items-center justify-center shadow-md">
                      <span className="material-symbols-outlined text-[24px]">event_available</span>
                    </div>
                    <span className="text-4xl sm:text-5xl font-mono font-black text-white/30 group-hover:text-white/50 transition-colors select-none">
                      02
                    </span>
                  </div>
                  <h3 className="text-base sm:text-lg font-bold text-white mb-2 drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
                    {language === 'en' ? 'Select Slot & Instant Pay' : 'Đặt lịch & Giữ chỗ tức thì'}
                  </h3>
                  <p className="text-slate-100 text-xs sm:text-sm leading-relaxed font-medium drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.8)]">
                    {language === 'en'
                      ? 'Pick the best wave hour, guest count, and receive instant confirmation.'
                      : 'Chọn ngày, khung giờ sóng đẹp và số lượng khách. Xác nhận giữ chỗ an toàn qua thẻ & ví điện tử.'}
                  </p>
                </div>

                {/* Step 3 */}
                <div className="relative z-20 p-6 sm:p-7 rounded-3xl bg-transparent border border-white/20 hover:border-white/40 hover:-translate-y-1.5 transition-all group flex flex-col items-start">
                  <div className="w-full flex items-center justify-between mb-3">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-slate-800 to-slate-900 text-white flex items-center justify-center shadow-md">
                      <span className="material-symbols-outlined text-[24px]">qr_code_2</span>
                    </div>
                    <span className="text-4xl sm:text-5xl font-mono font-black text-white/30 group-hover:text-white/50 transition-colors select-none">
                      03
                    </span>
                  </div>
                  <h3 className="text-base sm:text-lg font-bold text-white mb-2 drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
                    {language === 'en' ? 'Receive QR Pass & Dive In' : 'Nhận vé QR & Chạm sóng biển'}
                  </h3>
                  <p className="text-slate-100 text-xs sm:text-sm leading-relaxed font-medium drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.8)]">
                    {language === 'en'
                      ? 'Check-in on mobile at the beach harbor station and enjoy your certified marine tour.'
                      : 'Vé điện tử gửi thẳng qua ứng dụng. Quét mã check-in trực tiếp tại bến tập kết và bắt đầu hành trình.'}
                  </p>
                </div>
              </div>
            </div>

            {/* Final Call to Action */}
            <div className="rounded-3xl p-6 sm:p-8 bg-white/10 backdrop-blur-xl border border-white/20 text-white flex flex-col sm:flex-row items-center justify-between gap-6 shadow-2xl">
              <div>
                <h3 className="text-lg sm:text-xl font-bold mb-1 text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
                  {language === 'en' ? 'Ready to Touch Da Nang Waters?' : 'Sẵn sàng chạm sóng biển Đà Nẵng ngay hôm nay?'}
                </h3>
                <p className="text-xs sm:text-sm text-white/90 max-w-xl font-medium drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.7)]">
                  {language === 'en'
                    ? 'Explore 20+ certified water sports and marine expeditions tailored for you.'
                    : 'Hơn 20+ trải nghiệm thể thao biển chuẩn quốc tế với đầy đủ bảo hiểm và thiết bị chuyên nghiệp.'}
                </p>
              </div>

              <Link
                to="/search"
                className="px-6 py-3.5 rounded-2xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-xs sm:text-sm shadow-lg shadow-cyan-600/30 transition-all shrink-0 hover:scale-[1.02] flex items-center gap-1.5"
              >
                <span>{language === 'en' ? 'Explore All Tours' : 'Xem toàn bộ tour'}</span>
                <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
              </Link>
            </div>
          </div>
        </ScrollReveal>
      </section>

      {/* Floating Scroll to Top Button */}
      <ScrollToTopButton />
    </main>
  );
}

export default Home;
