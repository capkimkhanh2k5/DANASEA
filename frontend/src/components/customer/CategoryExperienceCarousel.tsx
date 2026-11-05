import React, { useCallback, useEffect, useRef, useState } from 'react';
import useEmblaCarousel from 'embla-carousel-react';
import { Link, useNavigate } from 'react-router-dom';
import type { ActivityCardData } from '../../data/customerHomeData';
import { useWishlistStore } from '../../store/useWishlistStore';
import { useLanguageStore } from '../../store/useLanguageStore';
import { ArrowRight, Star } from 'lucide-react';
import { getTourUrl } from '../../utils/seo';

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
    <div ref={sectionRef} data-ui="carousel-deck" className="py-4 sm:py-6">
      {/* Header: Clean title without duplicate arrow, single 'Xem tất cả →' */}
      <div className="flex items-center justify-between mb-4 gap-4">
        <div>
          <Link
            to={linkHref}
            onClick={handleNavigate}
            className="font-extrabold text-2xl sm:text-3xl text-slate-900 hover:text-sky-700 transition-colors tracking-tight block cursor-pointer"
          >
            {title}
          </Link>
        </div>

        <Link
          to={linkHref}
          onClick={handleNavigate}
          className="group/all text-sm font-semibold text-slate-600 hover:text-sky-700 transition-colors inline-flex items-center gap-1.5 cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <ArrowRight className="w-4 h-4 transition-transform duration-200 group-hover/all:translate-x-1.5" />
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
          className={`absolute -left-3 sm:-left-4 top-1/2 -translate-y-1/2 z-30 w-10 h-10 rounded-xl bg-white text-slate-800 border border-slate-200/90 flex items-center justify-center transition-transform duration-200 hover:scale-105 active:scale-95 cursor-pointer ${
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
          className={`absolute -right-3 sm:-right-4 top-1/2 -translate-y-1/2 z-30 w-10 h-10 rounded-xl bg-white text-slate-800 border border-slate-200/90 flex items-center justify-center transition-transform duration-200 hover:scale-105 active:scale-95 cursor-pointer ${
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
              const cardTitle = language === 'en' ? item.titleEn : item.title;
              const category = language === 'en' ? item.categoryEn : item.category;
              const duration = language === 'en' ? item.durationEn : item.duration;

              const isThisHovered = hoveredCardId === item.id;

              return (
                <div
                  key={`${item.id}-${idx}`}
                  className="flex-[0_0_100%] sm:flex-[0_0_50%] md:flex-[0_0_33.333%] lg:flex-[0_0_25%] min-w-0 pl-4 sm:pl-5 select-none flex flex-col relative py-2"
                  style={{ zIndex: isThisHovered ? 20 : 1 }}
                >
                  <Link
                    to={getTourUrl(item)}
                    onMouseEnter={() => setHoveredCardId(item.id)}
                    onMouseLeave={() => setHoveredCardId(null)}
                    style={{
                      transitionDelay: isRevealed ? `${idx * 60}ms` : '0ms',
                    }}
                    className={`group flex-1 flex flex-col bg-white rounded-2xl p-3 border transition-all duration-300 ease-out cursor-pointer ${
                      !isRevealed
                        ? 'opacity-0 translate-y-6'
                        : isThisHovered
                        ? 'border-slate-300 -translate-y-0.5'
                        : 'border-slate-200/80 hover:border-slate-300'
                    }`}
                  >
                    {/* Image Container */}
                    <div className="relative aspect-[4/3] rounded-xl overflow-hidden bg-slate-100 mb-2.5">
                      <img
                        src={item.imageUrl}
                        alt={cardTitle}
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
                        className="absolute top-2.5 right-2.5 w-8 h-8 rounded-xl bg-slate-900/60 hover:bg-slate-900/80 border border-white/30 flex items-center justify-center hover:scale-105 active:scale-95 transition-transform cursor-pointer z-10 group/heart"
                      >
                        <svg className={`w-4 h-4 transition-all duration-200 drop-shadow-sm ${isSaved ? 'text-rose-500 fill-rose-500 scale-105' : 'text-white fill-none stroke-white stroke-[2.2] group-hover/heart:scale-110'}`} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill={isSaved ? 'currentColor' : 'none'} /></svg>
                      </button>
                    </div>

                    {/* Card Content & Details */}
                    <div className="flex-1 flex flex-col justify-between px-1">
                      <div>
                        {/* Category & Duration */}
                        <div className="flex items-center gap-1.5 text-[13px] text-slate-500 mb-1.5 min-w-0">
                          <span className="font-semibold text-cyan-700 whitespace-nowrap shrink-0">{category}</span>
                          <span className="shrink-0 text-slate-300">•</span>
                          <span className="whitespace-nowrap shrink-0 font-medium text-slate-600">{duration}</span>
                          <span className="shrink-0 text-slate-300">•</span>
                          <span className="truncate min-w-0 flex-1">{item.location}</span>
                        </div>

                        {/* Title */}
                        <h3 className="font-bold text-sm sm:text-base text-slate-900 line-clamp-2 leading-snug group-hover:text-cyan-700 transition-colors">
                          {cardTitle}
                        </h3>
                      </div>

                      {/* Price & Rating (Bottom Row) */}
                      <div className="mt-3 pt-2.5 border-t border-slate-100 flex items-center justify-between">
                        <div className="flex items-baseline gap-1">
                          <span className="font-bold text-base text-slate-900 tabular-nums">
                            {item.price.toLocaleString('vi-VN')}₫
                          </span>
                          <span className="text-[13px] text-slate-500">
                            / {t.sections.perGuest}
                          </span>
                        </div>

                        {/* Pro-grade Rating Badge */}
                        <div className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-amber-50 border border-amber-200/60 text-amber-900 text-[13px] font-bold tabular-nums">
                          <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-500 shrink-0" />
                          <span>{item.rating.toFixed(2)}</span>
                          <span className="text-amber-950 font-medium text-[13px]">({item.reviewCount})</span>
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
