import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useLanguageStore } from '../../store/useLanguageStore';

interface TimeSlotData {
  time: string;
  label: string;
  labelEn: string;
  sunCondition: string;
  sunConditionEn: string;
  location: string;
  locationEn: string;
  tourTitle: string;
  tourTitleEn: string;
  tourCategory: string;
  tourCategoryEn: string;
  price: number;
  imageUrl: string;
}

const TIME_SLOTS: TimeSlotData[] = [
  {
    time: '05:30',
    label: 'Bình Minh Biển Sớm',
    labelEn: 'Early Sunrise',
    sunCondition: 'Trải nghiệm chèo ván đứng SUP đón bình minh trên vùng biển Mân Thái phẳng lặng, có hướng dẫn viên đồng hành và hỗ trợ chụp ảnh.',
    sunConditionEn: 'Sunrise stand-up paddleboarding in calm morning waters at Man Thai, with dedicated instructor and photoshoot included.',
    location: 'Bãi biển Mân Thái & Mỹ Khê',
    locationEn: 'Man Thai & My Khe Beach',
    tourTitle: 'Chèo SUP Đón Bình Minh Biển Mỹ Khê & Mân Thái',
    tourTitleEn: 'Sunrise SUP Paddleboarding at My Khe & Man Thai',
    tourCategory: 'Chèo SUP & Kayak',
    tourCategoryEn: 'Stand-Up Paddleboard',
    price: 280000,
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80',
  },
  {
    time: '09:00',
    label: 'Biển Nắng Pha Lê',
    labelEn: 'Crystal Waters',
    sunCondition: 'Tour cano cao tốc lặn ngắm rạn san hô tự nhiên Bãi Sụp - Bán đảo Sơn Trà, nước trong vắt và trang bị lặn chuyên nghiệp.',
    sunConditionEn: 'Speedboat coral snorkeling adventure at Hon Sup - Son Tra Peninsula with high-clarity waters and certified equipment.',
    location: 'Hòn Sụp, Bán đảo Sơn Trà',
    locationEn: 'Hon Sup, Son Tra Peninsula',
    tourTitle: 'Lặn Bình Khí Khám Phá Rạn San Hô Hòn Sụp Bán Đảo Sơn Trà',
    tourTitleEn: 'Scuba Diving at Hon Sup Coral Reefs - Son Tra Peninsula',
    tourCategory: 'Lặn Biển Ngắm San Hô',
    tourCategoryEn: 'Scuba Diving',
    price: 550000,
    imageUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80',
  },
  {
    time: '14:30',
    label: 'Gió Biển & Tốc Độ',
    labelEn: 'Ocean Breeze & Speed',
    sunCondition: 'Bay dù lượn cano kéo trên bầu trời vịnh Mỹ Khê, chiêm ngưỡng toàn cảnh bờ biển Đà Nẵng với trang bị bảo hộ an toàn.',
    sunConditionEn: 'Parasailing flight over My Khe Beach with panoramic coastline views and certified safety harnesses.',
    location: 'Bãi biển Mỹ Khê',
    locationEn: 'My Khe Beach',
    tourTitle: 'Bay Dù Lượn Cano Kéo Bãi Biển Mỹ Khê',
    tourTitleEn: 'Tandem Parasailing Flight at My Khe Beach',
    tourCategory: 'Cano & Dù Bay',
    tourCategoryEn: 'Speedboat & Parasailing',
    price: 500000,
    imageUrl: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=800&q=80',
  },
  {
    time: '17:00',
    label: 'Hoàng Hôn Vịnh Biển',
    labelEn: 'Sunset Horizon',
    sunCondition: 'Du ngoạn du thuyền ngắm hoàng hôn vịnh biển buông xuống chân cầu Thuận Phước, thưởng thức tiệc nhẹ và ngắm biển chiều.',
    sunConditionEn: 'Sunset yacht cruise along Da Nang Bay with light refreshments and panoramic golden hour views.',
    location: 'Vịnh Đà Nẵng & Bán Đảo Sơn Trà',
    locationEn: 'Da Nang Bay & Son Tra',
    tourTitle: 'Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Đà Nẵng',
    tourTitleEn: 'Luxury Sunset Yacht Tour in Da Nang Bay',
    tourCategory: 'Du Thuyền Hoàng Hôn',
    tourCategoryEn: 'Sunset Cruise',
    price: 450000,
    imageUrl: 'https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=800&q=80',
  },
];

export const SmartOceanPlanner: React.FC = () => {
  const { language } = useLanguageStore();
  const [selectedSlotIndex, setSelectedSlotIndex] = useState<number>(0);
  const currentSlot = TIME_SLOTS[selectedSlotIndex];

  return (
    <section data-ui="planner-deck" className="pt-2 pb-6 sm:pt-4 sm:pb-8 bg-transparent relative overflow-hidden">
      <div className="w-full relative z-10 max-w-[1360px] mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-6">
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
            {language === 'en' ? 'Experiences by Ocean Hours' : 'Trải Nghiệm Theo Khung Giờ Biển'}
          </h2>
        </div>

        {/* Time Preset Buttons */}
        <div className="flex flex-wrap items-center justify-center gap-2.5 sm:gap-3 mb-6">
          {TIME_SLOTS.map((slot: TimeSlotData, index: number) => {
            const isActive = index === selectedSlotIndex;
            return (
              <button
                key={slot.time}
                type="button"
                onClick={() => setSelectedSlotIndex(index)}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-[13px] sm:text-sm font-semibold transition-all duration-300 cursor-pointer ${
                  isActive
                    ? 'bg-sky-600 text-white border border-sky-400'
                    : 'bg-white/15 hover:bg-white/25 text-white border border-white/20'
                }`}
              >
                <span className={`tabular-nums font-bold ${isActive ? 'text-white' : 'text-cyan-300'}`}>
                  {slot.time}
                </span>
                <span className="drop-shadow-sm">{language === 'en' ? slot.labelEn : slot.label}</span>
              </button>
            );
          })}
        </div>

        {/* Dynamic Card Container */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8 items-center bg-slate-950/40 backdrop-blur-xl rounded-2xl p-6 sm:p-8 lg:p-10 border border-white/20">
          {/* Left Column: Context & Moment Atmosphere */}
          <div className="lg:col-span-7 flex flex-col justify-center space-y-4">
            <h3 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight drop-shadow-sm">
              {language === 'en' ? currentSlot.labelEn : currentSlot.label} ({currentSlot.time})
            </h3>
            <p className="text-base text-slate-100 leading-relaxed font-normal drop-shadow-sm">
              {language === 'en' ? currentSlot.sunConditionEn : currentSlot.sunCondition}
            </p>

            <div className="pt-2 flex items-center gap-2 text-[13px] sm:text-sm font-medium text-slate-200">
              <span className="font-bold text-cyan-300">
                {language === 'en' ? 'Area:' : 'Địa điểm:'}
              </span>
              <span>{language === 'en' ? currentSlot.locationEn : currentSlot.location}</span>
            </div>
          </div>

          {/* Right Column: Experience Card - Solid Grounded White Card */}
          <div className="lg:col-span-5">
            <div className="rounded-2xl overflow-hidden bg-white border border-slate-200/80 group transition-all duration-300 hover:border-slate-300 hover:shadow-md">
              <div className="relative aspect-[16/10] overflow-hidden bg-slate-900/30">
                <img
                  src={currentSlot.imageUrl}
                  alt={currentSlot.tourTitle}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                />
              </div>

              <div className="p-5 sm:p-6 bg-white">
                <span className="text-[13px] font-semibold text-sky-700 block mb-1">
                  {language === 'en' ? currentSlot.tourCategoryEn : currentSlot.tourCategory}
                </span>
                <h4 className="text-base font-bold text-slate-900 group-hover:text-sky-600 transition-colors line-clamp-2 mb-4 leading-snug">
                  {language === 'en' ? currentSlot.tourTitleEn : currentSlot.tourTitle}
                </h4>

                <div className="flex items-center justify-between pt-4 border-t border-slate-100">
                  <div>
                    <span className="text-[13px] text-slate-500 block">
                      {language === 'en' ? 'Ticket Price' : 'Giá vé'}
                    </span>
                    <span className="text-lg tabular-nums font-black text-slate-900">
                      {currentSlot.price.toLocaleString('vi-VN')}₫
                    </span>
                  </div>
                  <Link
                    to="/search"
                    className="px-5 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-500 active:bg-sky-700 text-white font-semibold text-[13px] transition-colors"
                  >
                    {language === 'en' ? 'Book Now' : 'Đặt ngay'}
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default SmartOceanPlanner;
