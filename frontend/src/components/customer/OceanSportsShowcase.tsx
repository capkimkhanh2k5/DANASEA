import React, { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { OCEAN_SPORTS_SHOWCASE } from '../../data/customerHomeData';
import type { OceanSportShowcase } from '../../data/customerHomeData';
import { useLanguageStore } from '../../store/useLanguageStore';

export const OceanSportsShowcase: React.FC = () => {
  const { language, t } = useLanguageStore();
  const navigate = useNavigate();

  const handleNavigate = (e: React.MouseEvent) => {
    e.preventDefault();
    if (typeof document !== 'undefined' && document.startViewTransition) {
      document.startViewTransition(() => {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        navigate('/search?category=sports');
      });
    } else {
      window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
      navigate('/search?category=sports');
    }
  };
  const [activeSportIndex, setActiveSportIndex] = useState<number>(0);
  const hoverTimeoutRef = useRef<number | null>(null);

  const activeSport = OCEAN_SPORTS_SHOWCASE[activeSportIndex];

  const handleMouseEnter = (index: number) => {
    if (hoverTimeoutRef.current) {
      window.clearTimeout(hoverTimeoutRef.current);
    }
    hoverTimeoutRef.current = window.setTimeout(() => {
      setActiveSportIndex(index);
    }, 80);
  };

  const handleMouseLeave = () => {
    if (hoverTimeoutRef.current) {
      window.clearTimeout(hoverTimeoutRef.current);
    }
  };

  useEffect(() => {
    return () => {
      if (hoverTimeoutRef.current) {
        window.clearTimeout(hoverTimeoutRef.current);
      }
    };
  }, []);

  return (
    <div className="py-2">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 pb-4 border-b border-white/20 gap-4">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-400/20 backdrop-blur-md border border-cyan-300/40 text-cyan-200 text-xs font-bold uppercase mb-2 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
            <span>{language === 'en' ? 'Active Waters' : 'Thể Thao & Năng Lượng Biển'}</span>
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_20px_rgba(0,0,0,0.7)]">
            {t.sportsShowcase.title}
          </h2>
        </div>

        <Link
          to="/search?category=sports"
          onClick={handleNavigate}
          className="group/all inline-flex items-center gap-1.5 text-cyan-300 hover:text-white font-bold text-xs sm:text-sm transition-all shrink-0 drop-shadow-md cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <span className="material-symbols-outlined text-[16px] transition-transform duration-200 group-hover/all:translate-x-1.5">arrow_forward</span>
        </Link>
      </div>

      {/* Split Layout: Left Visual, Right Interactive Activity Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left Column: Visual Container with smooth image crossfade */}
        <div className="lg:col-span-6">
          <div className="relative rounded-2xl overflow-hidden border border-white/20 shadow-xl aspect-[4/3] bg-slate-100">
            {OCEAN_SPORTS_SHOWCASE.map((sport: OceanSportShowcase, idx: number) => (
              <img
                key={sport.id}
                src={sport.imageUrl}
                alt={sport.name}
                className={`absolute inset-0 w-full h-full object-cover transition-all duration-700 ease-in-out ${
                  idx === activeSportIndex
                    ? 'opacity-100 scale-100'
                    : 'opacity-0 scale-105 pointer-events-none'
                }`}
                loading="lazy"
              />
            ))}

            {/* Gradient Scrim */}
            <div className="absolute inset-0 bg-gradient-to-t from-slate-950/30 via-transparent to-transparent pointer-events-none" />

            {/* Bottom Card Overlay */}
            <div className="absolute bottom-4 left-4 right-4 z-10 p-3.5 sm:p-4 rounded-xl bg-white/95 backdrop-blur-xl border border-white/80 shadow-[0_8px_30px_rgba(0,0,0,0.12)] text-slate-900">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-[11px] font-bold text-cyan-700 tracking-wider uppercase block">
                    {activeSport.index} / 04
                  </span>
                  <h4 className="text-sm sm:text-base font-extrabold text-slate-900">
                    {language === 'en' ? activeSport.nameEn : activeSport.name}
                  </h4>
                </div>
                <div className="text-right">
                  <span className="text-[11px] text-slate-400 block">{language === 'en' ? 'From' : 'Chỉ từ'}</span>
                  <span className="text-sm sm:text-base font-black text-rose-600">
                    {activeSport.startingPrice.toLocaleString('vi-VN')}₫
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Interactive Activity Cards (All 4 cards clearly visible) */}
        <div className="lg:col-span-6 space-y-3">
          {OCEAN_SPORTS_SHOWCASE.map((sport: OceanSportShowcase, index: number) => {
            const isActive = index === activeSportIndex;
            const displayName = language === 'en' ? sport.nameEn : sport.name;
            const displayDesc = language === 'en' ? sport.descriptionEn : sport.description;
            const displayHighlights = language === 'en' ? sport.highlightsEn : sport.highlights;

            return (
              <div
                key={sport.id}
                onMouseEnter={() => handleMouseEnter(index)}
                onMouseLeave={handleMouseLeave}
                onClick={() => setActiveSportIndex(index)}
                className={`p-3.5 sm:p-4 rounded-2xl cursor-pointer transition-all duration-300 ease-out border backdrop-blur-md ${
                  isActive
                    ? 'bg-white/20 border-cyan-400 shadow-xl ring-2 ring-cyan-400/30 -translate-y-0.5'
                    : 'bg-white/10 border-white/20 hover:bg-white/15 hover:border-white/30 text-white'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span
                      className={`text-xs font-mono font-bold px-2 py-0.5 rounded-md transition-colors ${
                        isActive
                          ? 'bg-cyan-500 text-white shadow-sm'
                          : 'bg-white/15 text-cyan-200'
                      }`}
                    >
                      {sport.index}
                    </span>
                    <h3
                      className={`text-sm sm:text-base font-bold transition-colors ${
                        isActive ? 'text-white' : 'text-white/90'
                      } drop-shadow-sm`}
                    >
                      {displayName}
                    </h3>
                  </div>

                  <span className={`text-xs font-extrabold ${isActive ? 'text-cyan-300' : 'text-cyan-200'} drop-shadow-sm`}>
                    {sport.startingPrice.toLocaleString('vi-VN')}₫
                  </span>
                </div>

                {/* Expanded Details */}
                <div
                  className={`grid transition-[grid-template-rows,opacity] duration-300 ease-out ${
                    isActive ? 'grid-rows-[1fr] opacity-100 mt-2.5 pt-2.5 border-t border-white/15' : 'grid-rows-[0fr] opacity-0'
                  }`}
                >
                  <div className="overflow-hidden">
                    <p className="text-white/85 text-xs sm:text-sm leading-relaxed mb-3 drop-shadow-sm">
                      {displayDesc}
                    </p>

                    <ul className="space-y-1 mb-3.5">
                      {displayHighlights.map((highlight: string, idx: number) => (
                        <li key={idx} className="flex items-center gap-2 text-xs text-white/90 drop-shadow-sm">
                          <span className="material-symbols-outlined text-[15px] text-cyan-300 shrink-0">check</span>
                          <span>{highlight}</span>
                        </li>
                      ))}
                    </ul>

                    <div className="flex items-center justify-between pt-1">
                      <Link
                        to={`/search?category=${encodeURIComponent(sport.name)}`}
                        className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold shadow-md transition-colors"
                      >
                        <span>{t.sportsShowcase.bookThis}</span>
                        <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                      </Link>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default OceanSportsShowcase;
