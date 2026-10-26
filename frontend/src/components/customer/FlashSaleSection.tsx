import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { FLASH_SALE_EXPERIENCES } from '../../data/customerHomeData';
import type { FlashSaleExperience } from '../../data/customerHomeData';
import { useWishlistStore } from '../../store/useWishlistStore';
import { useLanguageStore } from '../../store/useLanguageStore';

interface FlashSaleSectionProps {
  experiences?: FlashSaleExperience[];
}

export function FlashSaleSection({ experiences = FLASH_SALE_EXPERIENCES }: FlashSaleSectionProps = {}) {
  const { toggleWishlist, isInWishlist } = useWishlistStore();
  const { language, t } = useLanguageStore();

  const sectionRef = useRef<HTMLDivElement>(null);
  const [isRevealed, setIsRevealed] = useState(false);
  const [hoveredCardId, setHoveredCardId] = useState<string | number | null>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsRevealed(true);
          observer.disconnect();
        }
      },
      { threshold: 0.1 }
    );
    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  // Stable persistent countdown: Target is 23:59:59 of current day
  const [timeLeft, setTimeLeft] = useState(() => {
    const now = new Date();
    const endOfDay = new Date();
    endOfDay.setHours(23, 59, 59, 999);
    const diff = Math.max(0, Math.floor((endOfDay.getTime() - now.getTime()) / 1000));
    return {
      hours: Math.floor(diff / 3600),
      minutes: Math.floor((diff % 3600) / 60),
      seconds: diff % 60,
    };
  });

  useEffect(() => {
    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev.seconds > 0) {
          return { ...prev, seconds: prev.seconds - 1 };
        } else if (prev.minutes > 0) {
          return { ...prev, minutes: prev.minutes - 1, seconds: 59 };
        } else if (prev.hours > 0) {
          return { hours: prev.hours - 1, minutes: 59, seconds: 59 };
        }
        return { hours: 0, minutes: 0, seconds: 0 };
      });
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatNumber = (num: number) => String(num).padStart(2, '0');

  return (
    <div ref={sectionRef} className="py-2">
      {/* Header with Title and Countdown */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/20">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-400/20 backdrop-blur-md border border-amber-300/40 text-amber-300 text-xs font-bold tracking-wide uppercase mb-2 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
            <span>{t.flashSale.badge}</span>
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_20px_rgba(0,0,0,0.7)]">
            {t.flashSale.title}
          </h2>
        </div>

        {/* Countdown Clock */}
        <div className="flex items-center gap-3 bg-white/95 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-white/90 shadow-xl shrink-0 self-start sm:self-auto">
          <div className="flex items-center gap-1.5 text-amber-600 font-semibold text-xs">
            <span className="material-symbols-outlined text-[18px]">timer</span>
            <span>{t.flashSale.endsIn}</span>
          </div>
          <div className="flex items-center gap-1 font-mono font-bold text-sm">
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-xs shadow-xs">{formatNumber(timeLeft.hours)}</span>
            <span className="text-slate-400 font-bold">:</span>
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-xs shadow-xs">{formatNumber(timeLeft.minutes)}</span>
            <span className="text-slate-400 font-bold">:</span>
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-xs shadow-xs">{formatNumber(timeLeft.seconds)}</span>
          </div>
        </div>
      </div>

      {/* Flash Sale Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 pt-6">
        {experiences.map((item, idx) => {
          const isSaved = isInWishlist(item.id);
          const title = language === 'en' ? item.titleEn : item.title;
          const category = language === 'en' ? item.categoryEn : item.category;
          const location = language === 'en' ? item.locationEn : item.location;
          const duration = language === 'en' ? item.durationEn : item.duration;
          const _badge = language === 'en' ? item.badgeEn : item.badge;

          const isThisHovered = hoveredCardId === item.id;

          return (
            <div
              key={`${item.id}-${idx}`}
              onMouseEnter={() => setHoveredCardId(item.id)}
              onMouseLeave={() => setHoveredCardId(null)}
              style={{
                transitionDelay: isRevealed ? `${idx * 70}ms` : '0ms',
              }}
              className={`group bg-white rounded-2xl overflow-hidden border border-slate-200/80 transition-all duration-300 ease-out flex flex-col cursor-pointer ${
                !isRevealed
                  ? 'opacity-0 translate-y-6'
                  : isThisHovered
                  ? 'scale-[1.03] -translate-y-1.5 shadow-lg border-amber-300 ring-1 ring-amber-300/40 z-10'
                  : 'scale-100 translate-y-0 opacity-100 shadow-sm hover:shadow-md'
              }`}
            >
              {/* Image Container with Badges */}
              <div className="relative aspect-[4/3] overflow-hidden bg-slate-100">
                <img
                  src={item.imageUrl}
                  alt={title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  loading="lazy"
                />

                {/* Gradient Overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />

                {/* Discount Badge */}
                <div className="absolute top-2.5 left-2.5 flex items-center gap-1.5">
                  <span className="px-2 py-0.5 rounded-full bg-rose-600 text-white text-[11px] font-bold shadow-sm">
                    -{item.discountPercent}%
                  </span>
                </div>

                {/* Wishlist Button */}
                <button
                  type="button"
                  onClick={(e) => {
                    e.preventDefault();
                    toggleWishlist(item.id, title);
                  }}
                  aria-label="Wishlist"
                  className="absolute top-2.5 right-2.5 w-8 h-8 rounded-full bg-black/20 backdrop-blur-md hover:bg-black/35 border border-white/30 shadow-sm flex items-center justify-center hover:scale-110 active:scale-90 transition-all cursor-pointer z-10 group/heart"
                >
                  <svg className={`w-4.5 h-4.5 transition-all duration-200 drop-shadow-sm ${isSaved ? 'text-rose-500 fill-rose-500 scale-105' : 'text-white fill-none stroke-white stroke-[2.2] group-hover/heart:scale-110'}`} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill={isSaved ? 'currentColor' : 'none'} /></svg>
                </button>

                {/* Duration Tag */}
                <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded-md bg-black/60 backdrop-blur-xs text-white text-[10px] font-medium flex items-center gap-1">
                  <span className="material-symbols-outlined text-[12px]">schedule</span>
                  <span>{duration}</span>
                </div>
              </div>

              {/* Content Body */}
              <div className="p-3.5 flex flex-col flex-1 justify-between gap-3">
                <div>
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
                    <span className="font-semibold text-cyan-700">{category}</span>
                    <span className="flex items-center gap-1 text-slate-700 font-medium">
                      <span className="material-symbols-outlined text-amber-500 text-[13px]">star</span>
                      <span>{item.rating.toFixed(2)}</span>
                      <span className="text-slate-400 text-[11px]">({item.reviewCount})</span>
                    </span>
                  </div>

                  <h3 className="font-bold text-slate-900 text-sm line-clamp-2 leading-snug group-hover:text-cyan-700 transition-colors">
                    {title}
                  </h3>

                  <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[13px] text-slate-400">location_on</span>
                    <span className="truncate">{location}</span>
                  </p>
                </div>

                {/* Price & Scarcity Meter */}
                <div className="pt-2.5 border-t border-slate-100 flex flex-col gap-2">
                  {/* Urgency Progress Bar */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="font-medium text-rose-600 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[13px]">local_fire_department</span>
                        <span>{t.flashSale.seatsLeft.replace('{count}', String(item.slotsLeft))}</span>
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-slate-100 overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-amber-500 to-rose-500 rounded-full transition-all duration-500"
                        style={{ width: `${(item.slotsLeft / item.totalSlots) * 100}%` }}
                      />
                    </div>
                  </div>

                  {/* Price and CTA */}
                  <div className="flex items-center justify-between pt-1">
                    <div>
                      <span className="text-[11px] text-slate-400 line-through block">
                        {item.originalPrice.toLocaleString('vi-VN')} đ
                      </span>
                      <span className="text-sm font-bold text-rose-600">
                        {item.salePrice.toLocaleString('vi-VN')} đ
                        <span className="text-[11px] font-normal text-slate-500"> {t.sections.perGuest}</span>
                      </span>
                    </div>

                    <Link
                      to={`/tour/${item.id}`}
                      className="px-3 py-1.5 rounded-xl bg-cyan-700 hover:bg-cyan-800 text-white text-xs font-semibold shadow-xs transition-colors"
                    >
                      {t.flashSale.bookNow}
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default FlashSaleSection;
