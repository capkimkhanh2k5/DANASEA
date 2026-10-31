import React, { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { OCEAN_SPORTS_SHOWCASE } from '../../data/customerHomeData';
import type { OceanSportShowcase } from '../../data/customerHomeData';
import { useLanguageStore } from '../../store/useLanguageStore';
import { ArrowRight, Check } from 'lucide-react';

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
    <section data-ui="sports-showcase" className="py-8 sm:py-10 mb-6">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
        <div>
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
            {t.sportsShowcase.title}
          </h2>
          <p className="text-sm sm:text-base text-slate-600 mt-1 font-medium">
            {language === 'en'
              ? 'High-thrill watersports with certified safety equipment and instructors'
              : 'Trải nghiệm thể thao nước phấn khích cùng trang thiết bị và huấn luyện viên chuẩn quốc tế'}
          </p>
        </div>

        <Link
          to="/search?category=sports"
          onClick={handleNavigate}
          className="group/all inline-flex items-center gap-1.5 text-slate-600 hover:text-sky-700 font-semibold text-sm transition-colors shrink-0 cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <ArrowRight className="w-4 h-4 transition-transform duration-200 group-hover/all:translate-x-1.5" />
        </Link>
      </div>

      {/* Split Layout: Left Visual, Right Interactive Activity Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left Column: Visual Container with smooth image crossfade */}
        <div className="lg:col-span-6">
          <div className="relative rounded-2xl overflow-hidden border border-white/20 aspect-[4/3] bg-slate-900">
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
            <div
              className="absolute inset-0 pointer-events-none"
              style={{
                background: 'linear-gradient(to top, rgba(0, 0, 0, 0.45) 0%, rgba(0, 0, 0, 0) 50%)', // taste-skill-ignore
              }}
            />

            {/* Bottom Card Overlay */}
            <div className="absolute bottom-4 left-4 right-4 z-10 p-3.5 sm:p-4 rounded-xl bg-white border border-slate-200/90 text-slate-900">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-[13px] font-semibold text-sky-700 block mb-0.5">
                    {language === 'en' ? activeSport.levelEn : activeSport.level}
                  </span>
                  <h4 className="text-sm sm:text-base font-extrabold text-slate-900">
                    {language === 'en' ? activeSport.nameEn : activeSport.name}
                  </h4>
                </div>
                <div className="text-right">
                  <span className="text-[13px] text-slate-500 block">{language === 'en' ? 'From' : 'Chỉ từ'}</span>
                  <span className="text-sm sm:text-base font-black text-rose-600 tabular-nums">
                    {activeSport.startingPrice.toLocaleString('vi-VN')}₫
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Interactive Activity Cards */}
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
                className={`p-4 rounded-2xl cursor-pointer transition-all duration-200 ease-out border ${
                  isActive
                    ? 'bg-white border-sky-500 -translate-y-0.5 text-slate-900 shadow-md'
                    : 'bg-white border-slate-200/80 hover:border-slate-300 text-slate-800'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className={`w-2 h-2 rounded-full shrink-0 ${isActive ? 'bg-sky-600' : 'bg-slate-300'}`} />
                    <h3 className="text-base font-bold text-slate-900">
                      {displayName}
                    </h3>
                  </div>

                  <span className={`text-[13px] font-extrabold tabular-nums ${isActive ? 'text-sky-700' : 'text-slate-600'}`}>
                    {sport.startingPrice.toLocaleString('vi-VN')}₫
                  </span>
                </div>

                {/* Expanded Details */}
                {isActive && (
                  <div className="mt-3 pt-3 border-t border-slate-100">
                    <p className="text-slate-600 text-sm leading-relaxed mb-3">
                      {displayDesc}
                    </p>

                    <ul className="space-y-1.5 mb-3.5">
                      {displayHighlights.map((highlight: string, idx: number) => (
                        <li key={idx} className="flex items-center gap-2 text-[13px] text-slate-700">
                          <Check className="w-4 h-4 text-sky-600 shrink-0" />
                          <span>{highlight}</span>
                        </li>
                      ))}
                    </ul>

                    <div className="flex items-center justify-between pt-1">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/search?category=${encodeURIComponent(sport.name)}`);
                        }}
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-500 active:bg-sky-700 text-white text-[13px] font-semibold transition-colors cursor-pointer"
                      >
                        <span>{t.sportsShowcase.bookThis}</span>
                        <ArrowRight className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};

export default OceanSportsShowcase;
