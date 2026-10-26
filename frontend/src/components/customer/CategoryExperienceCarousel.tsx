import React, { useCallback, useEffect, useRef, useState } from 'react';
import useEmblaCarousel from 'embla-carousel-react';
import { Link, useNavigate } from 'react-router-dom';
import type { ActivityCardData } from '../../data/customerHomeData';
import { useWishlistStore } from '../../store/useWishlistStore';
import { useLanguageStore } from '../../store/useLanguageStore';

interface CategoryExperienceCarouselProps {
  title: string;
  subtitle?: string;
  categoryLink?: string;
  categorySlug?: string;
  items?: ActivityCardData[];
  experiences?: ActivityCardData[];
}

export function CategoryExperienceCarousel({
  title,
  categoryLink,
  categorySlug,
  items,
  experiences,
}: CategoryExperienceCarouselProps) {
  const { toggleWishlist, isInWishlist } = useWishlistStore();
  const { language, t } = useLanguageStore();
  const navigate = useNavigate();

  const displayItems = experiences || items || [];
  const linkHref = categoryLink || (categorySlug ? `/search?category=${encodeURIComponent(categorySlug)}` : '/search');

  // Smooth page transition handler
  const handleNavigate = (e: React.MouseEvent) => {
    e.preventDefault();
    if (typeof document !== 'undefined' && document.startViewTransition) {
      document.startViewTransition(() => {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        navigate(linkHref);
      });
    } else {
      window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
      navigate(linkHref);
    }
  };

  // Initialize Embla Carousel with Snap & Peek physics
  const [emblaRef, emblaApi] = useEmblaCarousel({
    align: 'start',
    containScroll: 'trimSnaps',
    dragFree: false,
    skipSnaps: false,
  });

  const [canScrollPrev, setCanScrollPrev] = useState(false);
  const [canScrollNext, setCanScrollNext] = useState(false);
  const [hoveredCardId, setHoveredCardId] = useState<string | number | null>(null);
  const [isRevealed, setIsRevealed] = useState(false);
  const sectionRef = useRef<HTMLDivElement>(null);

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

  const onSelect = useCallback(() => {
    if (!emblaApi) return;
    setCanScrollPrev(emblaApi.canScrollPrev());
    setCanScrollNext(emblaApi.canScrollNext());
  }, [emblaApi]);

  useEffect(() => {
    if (!emblaApi) return;
    const frame = requestAnimationFrame(onSelect);
    emblaApi.on('select', onSelect);
    emblaApi.on('reInit', onSelect);
    return () => {
      cancelAnimationFrame(frame);
      emblaApi.off('select', onSelect);
      emblaApi.off('reInit', onSelect);
    };
  }, [emblaApi, onSelect]);

  const scrollPrev = useCallback(() => {
    if (emblaApi) emblaApi.scrollPrev();
  }, [emblaApi]);

  const scrollNext = useCallback(() => {
    if (emblaApi) emblaApi.scrollNext();
  }, [emblaApi]);

  return (
    <div ref={sectionRef} className="py-2">
      {/* Header: Clean title without duplicate arrow, no subtitle, single 'Xem tất cả →' */}
      <div className="flex items-center justify-between mb-4 gap-4">
        <div>
          <Link
            to={linkHref}
            onClick={handleNavigate}
            className="font-extrabold text-2xl sm:text-3xl text-white hover:text-cyan-200 transition-colors drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_20px_rgba(0,0,0,0.7)] tracking-tight block cursor-pointer"
          >
            {title}
          </Link>
        </div>

        <Link
          to={linkHref}
          onClick={handleNavigate}
          className="group/all text-xs sm:text-sm font-bold text-cyan-300 hover:text-white transition-all inline-flex items-center gap-1.5 drop-shadow-md cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <span className="material-symbols-outlined text-[16px] transition-transform duration-200 group-hover/all:translate-x-1.5">
            arrow_forward
          </span>
        </Link>
      </div>

      {/* Carousel Container */}
      <div className="relative group/carousel">
        {/* Previous Button */}
        <button
          type="button"
          onClick={scrollPrev}
          disabled={!canScrollPrev}
          aria-label="Previous slide"
          className={`absolute -left-3 sm:-left-4 top-1/2 -translate-y-1/2 z-30 w-10 h-10 rounded-full bg-white text-slate-800 border border-slate-200 shadow-md flex items-center justify-center transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer ${
            canScrollPrev
              ? 'opacity-100 pointer-events-auto translate-x-0'
              : 'opacity-0 pointer-events-none -translate-x-2'
          }`}
        >
          <svg className="w-4 h-4 text-slate-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        {/* Next Button */}
        <button
          type="button"
          onClick={scrollNext}
          disabled={!canScrollNext}
          aria-label="Next slide"
          className={`absolute -right-3 sm:-right-4 top-1/2 -translate-y-1/2 z-30 w-10 h-10 rounded-full bg-white text-slate-800 border border-slate-200 shadow-md flex items-center justify-center transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer ${
            canScrollNext
              ? 'opacity-100 pointer-events-auto translate-x-0'
              : 'opacity-0 pointer-events-none translate-x-2'
          }`}
        >
          <svg className="w-4 h-4 text-slate-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 5l7 7-7 7" />
          </svg>
        </button>

        {/* Embla Viewport */}
        <div className="overflow-hidden select-none -mx-2 px-2 py-4 -my-2" ref={emblaRef}>
          <div className="flex -ml-4 sm:-ml-5">
            {displayItems.map((item, idx) => {
              const isSaved = isInWishlist(item.id);
              const title = language === 'en' ? item.titleEn : item.title;
              const category = language === 'en' ? item.categoryEn : item.category;
              const duration = language === 'en' ? item.durationEn : item.duration;
              const badge = language === 'en' ? item.badgeEn : item.badge;

              const isThisHovered = hoveredCardId === item.id;

              return (
                <div
                  key={`${item.id}-${idx}`}
                  className="flex-[0_0_100%] sm:flex-[0_0_50%] md:flex-[0_0_33.333%] lg:flex-[0_0_25%] min-w-0 pl-4 sm:pl-5 select-none flex flex-col relative py-2"
                  style={{ zIndex: isThisHovered ? 20 : 1 }}
                >
                  <Link
                    to={`/tour/${item.id}`}
                    onMouseEnter={() => setHoveredCardId(item.id)}
                    onMouseLeave={() => setHoveredCardId(null)}
                    style={{
                      transitionDelay: isRevealed ? `${idx * 60}ms` : '0ms',
                    }}
                    className={`group flex-1 flex flex-col bg-white rounded-2xl p-2.5 border border-slate-200/80 transition-all duration-300 ease-out cursor-pointer ${
                      !isRevealed
                        ? 'opacity-0 translate-y-6'
                        : isThisHovered
                        ? 'scale-[1.03] -translate-y-1.5 shadow-lg border-cyan-300 ring-1 ring-cyan-300/40'
                        : 'scale-100 translate-y-0 opacity-100 shadow-sm hover:shadow-md'
                    }`}
                  >
                    {/* Image Container */}
                    <div className="relative aspect-[4/3] rounded-xl overflow-hidden bg-slate-100 mb-2.5">
                      <img
                        src={item.imageUrl}
                        alt={title}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out pointer-events-none"
                        loading="lazy"
                      />

                      {/* Top-right Wishlist Button */}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          toggleWishlist(item.id);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === ' ' || e.key === 'Enter') {
                            e.stopPropagation();
                          }
                        }}
                        aria-label="Save to wishlist"
                        className="absolute top-2.5 right-2.5 w-7 h-7 rounded-full bg-white/90 backdrop-blur-sm flex items-center justify-center hover:scale-110 active:scale-90 transition-transform shadow-sm cursor-pointer z-10"
                      >
                        <span className={`text-sm ${isSaved ? 'text-rose-500' : 'text-slate-600'}`}>
                          {isSaved ? '♥' : '♡'}
                        </span>
                      </button>
                    </div>

                    {/* Card Content & Details */}
                    <div className="flex-1 flex flex-col justify-between px-1">
                      <div>
                        {/* Category & Duration */}
                        <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-1.5 min-w-0">
                          <span className="font-semibold text-cyan-700 whitespace-nowrap shrink-0">{category}</span>
                          <span className="shrink-0 text-slate-300">•</span>
                          <span className="whitespace-nowrap shrink-0 font-medium text-slate-600">{duration}</span>
                          <span className="shrink-0 text-slate-300">•</span>
                          <span className="truncate min-w-0 flex-1">{item.location}</span>
                        </div>

                        {/* Title */}
                        <h3 className="font-bold text-sm text-slate-900 line-clamp-2 leading-snug group-hover:text-cyan-700 transition-colors">
                          {title}
                        </h3>
                      </div>

                      {/* Price & Rating (Bottom Row) */}
                      <div className="mt-2.5 pt-2 border-t border-slate-100 flex items-center justify-between">
                        <div>
                          <span className="font-bold text-sm text-slate-900">
                            {item.price.toLocaleString('vi-VN')}₫
                          </span>
                          <span className="text-[11px] text-slate-500 ml-1">
                            {t.sections.perGuest}
                          </span>
                        </div>

                        {/* Rating & Review Count */}
                        <div className="flex items-center gap-1 text-xs text-slate-700">
                          <span className="text-amber-500 font-bold">★</span>
                          <span className="font-semibold">{item.rating.toFixed(2)}</span>
                          <span className="text-slate-400 text-[11px]">({item.reviewCount})</span>
                        </div>
                      </div>
                    </div>
                  </Link>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

export default CategoryExperienceCarousel;
