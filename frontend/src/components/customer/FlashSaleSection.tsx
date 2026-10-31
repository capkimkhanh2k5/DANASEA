import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { FLASH_SALE_EXPERIENCES } from '../../data/customerHomeData';
import type { FlashSaleExperience } from '../../data/customerHomeData';
import { useWishlistStore } from '../../store/useWishlistStore';
import { useLanguageStore } from '../../store/useLanguageStore';
import { Clock, Star, MapPin, Flame } from 'lucide-react';

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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {t.flashSale.title}
          </h2>
        </div>

        {/* Countdown Clock */}
        <div className="flex items-center gap-3 bg-white px-4 py-2 rounded-2xl border border-slate-200/90 shrink-0 self-start sm:self-auto">
          <div className="flex items-center gap-1.5 text-amber-700 font-semibold text-[13px]">
            <Clock className="w-4 h-4 text-amber-700 shrink-0" />
            <span>{t.flashSale.endsIn}</span>
          </div>
          <div className="flex items-center gap-1 font-bold text-sm tabular-nums">
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-[13px]">{formatNumber(timeLeft.hours)}h</span>
            <span className="text-slate-400 font-bold">:</span>
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-[13px]">{formatNumber(timeLeft.minutes)}m</span>
            <span className="text-slate-400 font-bold">:</span>
            <span className="bg-slate-900 text-white px-2.5 py-1 rounded-md text-[13px]">{formatNumber(timeLeft.seconds)}s</span>
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

          const isThisHovered = hoveredCardId === item.id;

          return (
            <div
              key={`${item.id}-${idx}`}
              data-ui="flash-sale-card"
              onMouseEnter={() => setHoveredCardId(item.id)}
              onMouseLeave={() => setHoveredCardId(null)}
              style={{
                transitionDelay: isRevealed ? `${idx * 70}ms` : '0ms',
              }}
              className={`group bg-white rounded-2xl overflow-hidden border transition-all duration-300 ease-out flex flex-col cursor-pointer ${
                !isRevealed
                  ? 'opacity-0 translate-y-6'
                  : isThisHovered
                  ? 'border-slate-300 shadow-md -translate-y-0.5'
                  : 'border-slate-200/80 hover:border-slate-300 hover:shadow-md'
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

                {/* Discount Badge */}
                <div className="absolute top-2.5 left-2.5 flex items-center gap-1.5">
                  <span className="px-2 py-0.5 rounded-md bg-rose-600 text-white text-[13px] font-bold">
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
                  className="absolute top-2.5 right-2.5 w-8 h-8 rounded-xl bg-slate-900/60 hover:bg-slate-900/80 border border-white/30 flex items-center justify-center hover:scale-105 active:scale-95 transition-transform cursor-pointer z-10 group/heart"
                >
                  <svg className={`w-4 h-4 transition-all duration-200 drop-shadow-sm ${isSaved ? 'text-rose-500 fill-rose-500 scale-105' : 'text-white fill-none stroke-white stroke-[2.2] group-hover/heart:scale-110'}`} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill={isSaved ? 'currentColor' : 'none'} /></svg>
                </button>

                {/* Duration Tag */}
                <div className="absolute bottom-2 left-2 px-2.5 py-0.5 rounded-md bg-black/70 text-white text-[13px] font-medium flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-white" />
                  <span>{duration}</span>
                </div>
              </div>

              {/* Content Body */}
              <div className="p-3.5 flex flex-col flex-1 justify-between gap-3">
                <div>
                  <div className="flex items-center justify-between text-[13px] text-slate-500 mb-1">
                    <span className="font-semibold text-cyan-700">{category}</span>
                    <span className="flex items-center gap-1 text-slate-700 font-medium">
                      <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-500 shrink-0" />
                      <span className="tabular-nums font-bold">{item.rating.toFixed(2)}</span>
                      <span className="text-slate-600 font-medium text-[13px]">({item.reviewCount})</span>
                    </span>
                  </div>

                  <h3 className="font-bold text-slate-900 text-sm sm:text-base line-clamp-2 leading-snug group-hover:text-cyan-700 transition-colors">
                    {title}
                  </h3>

                  <p className="text-[13px] text-slate-500 mt-1 flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span className="truncate">{location}</span>
                  </p>
                </div>

                {/* Price & Scarcity Meter */}
                <div className="pt-2.5 border-t border-slate-100 flex flex-col gap-2">
                  {/* Urgency Progress Bar */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between text-[13px]">
                      <span className="font-medium text-rose-600 flex items-center gap-1">
                        <Flame className="w-3.5 h-3.5 text-rose-600 shrink-0" />
                        <span>{t.flashSale.seatsLeft.replace('{count}', String(item.slotsLeft))}</span>
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-slate-100 overflow-hidden">
                      <div
                        className="h-full bg-rose-500 rounded-full transition-all duration-500"
                        style={{ width: `${(item.slotsLeft / item.totalSlots) * 100}%` }}
                      />
                    </div>
                  </div>

                  {/* Price and CTA */}
                  <div className="flex items-center justify-between pt-1">
                    <div>
                      <span className="text-[13px] text-slate-500 line-through block tabular-nums">
                        {item.originalPrice.toLocaleString('vi-VN')}₫
                      </span>
                      <span className="text-base font-bold text-rose-600 tabular-nums">
                        {item.salePrice.toLocaleString('vi-VN')}₫
                        <span className="text-[13px] font-normal text-slate-600"> / {t.sections.perGuest}</span>
                      </span>
                    </div>

                    <Link
                      to={`/tour/${item.id}`}
                      className="px-3.5 py-1.5 rounded-xl bg-sky-700 hover:bg-sky-800 active:bg-sky-900 text-white text-[13px] font-semibold transition-colors"
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
