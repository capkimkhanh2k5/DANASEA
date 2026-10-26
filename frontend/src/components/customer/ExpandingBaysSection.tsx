import React, { useRef, useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { COASTAL_BAYS } from '../../data/customerHomeData';
import type { CoastalBayData } from '../../data/customerHomeData';
import { useLanguageStore } from '../../store/useLanguageStore';

export const ExpandingBaysSection: React.FC = () => {
  const { language, t } = useLanguageStore();
  const navigate = useNavigate();

  const handleNavigate = (e: React.MouseEvent) => {
    e.preventDefault();
    if (typeof document !== 'undefined' && document.startViewTransition) {
      document.startViewTransition(() => {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        navigate('/search?category=bays');
      });
    } else {
      window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
      navigate('/search?category=bays');
    }
  };
  const sectionRef = useRef<HTMLDivElement>(null);
  const [isRevealed, setIsRevealed] = useState(false);
  const [hoveredBayId, setHoveredBayId] = useState<string | null>(null);

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

  return (
    <div ref={sectionRef} className="py-2">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-6 pb-4 border-b border-white/20">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-400/20 backdrop-blur-md border border-cyan-300/40 text-cyan-200 text-xs font-bold uppercase mb-2 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
            <span>{language === 'en' ? 'Coastal Sanctuaries' : 'Kỳ Quan Vịnh Biển Đà Nẵng'}</span>
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.95)] drop-shadow-[0_8px_20px_rgba(0,0,0,0.7)]">
            {t.panels.title}
          </h2>
        </div>

        <Link
          to="/search?category=bays"
          onClick={handleNavigate}
          className="group/all text-xs sm:text-sm font-bold text-cyan-300 hover:text-white transition-all inline-flex items-center gap-1.5 shrink-0 drop-shadow-md cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <span className="material-symbols-outlined text-[16px] transition-transform duration-200 group-hover/all:translate-x-1.5">arrow_forward</span>
        </Link>
      </div>

      {/* 4 Distinct Coastal Bay Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {COASTAL_BAYS.map((bay: CoastalBayData, idx: number) => {
          const displayName = language === 'en' ? bay.nameEn : bay.name;
          const displayTagline = language === 'en' ? bay.taglineEn : bay.tagline;

          const isThisHovered = hoveredBayId === bay.id;

          return (
            <div
              key={bay.id}
              onMouseEnter={() => setHoveredBayId(bay.id)}
              onMouseLeave={() => setHoveredBayId(null)}
              style={{
                transitionDelay: isRevealed ? `${idx * 70}ms` : '0ms',
              }}
              className={`group relative rounded-2xl overflow-hidden bg-slate-900 transition-all duration-300 ease-out flex flex-col justify-end h-[380px] sm:h-[400px] cursor-pointer ${
                !isRevealed
                  ? 'opacity-0 translate-y-6'
                  : isThisHovered
                  ? 'scale-[1.03] -translate-y-1.5 shadow-xl ring-2 ring-cyan-400/50 z-10'
                  : 'scale-100 translate-y-0 opacity-100 shadow-sm hover:shadow-md'
              }`}
            >
              {/* Image */}
              <img
                src={bay.imageUrl}
                alt={displayName}
                className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
                loading="lazy"
              />

              {/* Gradient Scrim */}
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950/60 via-slate-950/15 to-transparent" />

              {/* Bottom Content Pod */}
              <div className="relative p-4 z-10 text-white flex flex-col justify-end">
                <h3 className="text-lg font-bold text-white mb-1 group-hover:text-cyan-300 transition-colors">
                  {displayName}
                </h3>

                <p className="text-xs text-slate-200 line-clamp-2 mb-3 leading-relaxed">
                  {displayTagline}
                </p>

                <Link
                  to={`/search?location=${encodeURIComponent(displayName)}`}
                  className="w-full py-2 px-3 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-semibold text-xs flex items-center justify-center gap-1.5 transition-all shadow-sm"
                >
                  <span>{t.panels.exploreSpot}</span>
                  <span>→</span>
                </Link>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default ExpandingBaysSection;
