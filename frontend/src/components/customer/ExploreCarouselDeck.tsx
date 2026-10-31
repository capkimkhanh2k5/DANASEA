import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import useEmblaCarousel from 'embla-carousel-react';
import { ChevronLeft, ChevronRight, ArrowRight } from 'lucide-react';
import { useLanguageStore } from '../../store/useLanguageStore';

interface ExploreCarouselDeckProps<T> {
  title: string;
  subtitle: string;
  viewAllLink?: string;
  items: T[];
  renderItem: (item: T, idx: number) => React.ReactNode;
  weatherBadge?: React.ReactNode;
}

export function ExploreCarouselDeck<T>({
  title,
  subtitle,
  viewAllLink = '/search',
  items,
  renderItem,
  weatherBadge,
}: ExploreCarouselDeckProps<T>) {
  const { language } = useLanguageStore();
  const [emblaRef, emblaApi] = useEmblaCarousel({
    loop: true,
    align: 'start',
  });
  const [isPaused, setIsPaused] = useState(false);

  useEffect(() => {
    if (!emblaApi || isPaused) return;
    const interval = setInterval(() => {
      emblaApi.scrollNext();
    }, 3800);
    return () => clearInterval(interval);
  }, [emblaApi, isPaused]);

  return (
    <section className="w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 mb-16 animate-fadeInUp">
      {/* Deck Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-6 gap-4">
        <div className="max-w-2xl">
          {weatherBadge && <div className="mb-2">{weatherBadge}</div>}
          <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {title}
          </h2>
          <p className="text-sm sm:text-base text-slate-600 mt-1">
            {subtitle}
          </p>
        </div>

        <div className="self-start sm:self-auto shrink-0">
          <Link
            to={viewAllLink}
            className="group/all inline-flex items-center gap-1.5 text-sm font-semibold text-slate-600 hover:text-sky-700 transition-colors cursor-pointer"
          >
            <span>{language === 'en' ? 'View all' : 'Xem tất cả'}</span>
            <ArrowRight className="w-4 h-4 transition-transform group-hover/all:translate-x-1" />
          </Link>
        </div>
      </div>

      {/* Carousel Track with Physics & Side Controls */}
      <div
        className="relative group/carousel"
        onMouseEnter={() => setIsPaused(true)}
        onMouseLeave={() => setIsPaused(false)}
      >
        {/* Left Arrow Button */}
        <button
          type="button"
          onClick={() => emblaApi?.scrollPrev()}
          aria-label="Cuộn sang trái"
          className="flex absolute -left-3 sm:-left-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-white border border-slate-200/90 shadow-md items-center justify-center text-slate-700 hover:text-slate-900 hover:bg-slate-50 transition-all cursor-pointer opacity-90 hover:opacity-100 hover:scale-105 active:scale-95"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>

        {/* Embla Viewport */}
        <div className="overflow-hidden select-none -mx-2 px-2 py-3" ref={emblaRef}>
          <div className="flex -ml-4">
            {items.map((item, idx) => (
              <div
                key={idx}
                className="flex-[0_0_82%] sm:flex-[0_0_48%] md:flex-[0_0_32%] lg:flex-[0_0_24%] xl:flex-[0_0_20%] min-w-0 pl-4 select-none flex flex-col"
              >
                {renderItem(item, idx)}
              </div>
            ))}
          </div>
        </div>

        {/* Right Arrow Button */}
        <button
          type="button"
          onClick={() => emblaApi?.scrollNext()}
          aria-label="Cuộn sang phải"
          className="flex absolute -right-3 sm:-right-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-white border border-slate-200/90 shadow-md items-center justify-center text-slate-700 hover:text-slate-900 hover:bg-slate-50 transition-all cursor-pointer opacity-90 hover:opacity-100 hover:scale-105 active:scale-95"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>
    </section>
  );
}

export default ExploreCarouselDeck;
