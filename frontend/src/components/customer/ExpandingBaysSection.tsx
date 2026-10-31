import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { COASTAL_BAYS } from '../../data/customerHomeData';
import type { CoastalBayData } from '../../data/customerHomeData';
import { useLanguageStore } from '../../store/useLanguageStore';

export const ExpandingBaysSection: React.FC = () => {
  const { language, t } = useLanguageStore();
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  return (
    <div className="py-8 sm:py-10">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
        <div>
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
            {t.panels.title}
          </h2>
          <p className="text-base text-slate-600 mt-2 font-medium">
            {language === 'en'
              ? 'Pristine coastal bays, limestone peninsulas, and natural marine conservation havens'
              : 'Khám phá các vịnh biển nguyên sơ, bán đảo đá vôi và khu bảo tồn san hô tự nhiên'}
          </p>
        </div>

        <Link
          to="/search"
          className="group/all text-[13px] sm:text-sm font-semibold text-slate-600 hover:text-sky-700 transition-colors inline-flex items-center gap-1.5 cursor-pointer hover:translate-x-0.5"
        >
          <span>{t.sections.viewAll}</span>
          <ArrowRight className="w-4 h-4 transition-transform duration-200 group-hover/all:translate-x-1" />
        </Link>
      </div>

      {/* Accordion Bay Panels Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
        {COASTAL_BAYS.map((bay: CoastalBayData, index: number) => {
          const isHovered = hoveredIndex === index;
          const displayName = language === 'en' ? bay.nameEn : bay.name;
          const displayTagline = language === 'en' ? bay.taglineEn : bay.tagline;

          return (
            <div
              key={bay.id}
              data-ui="bay-card"
              onMouseEnter={() => setHoveredIndex(index)}
              onMouseLeave={() => setHoveredIndex(null)}
              className={`relative rounded-2xl overflow-hidden border border-white/20 transition-all duration-300 ease-out flex flex-col justify-end min-h-[360px] group ${
                isHovered ? '-translate-y-0.5 border-cyan-400 z-10' : 'hover:border-white/40'
              }`}
            >
              {/* Background Image */}
              <img
                src={bay.imageUrl}
                alt={displayName}
                className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 ease-out group-hover:scale-105 pointer-events-none"
                loading="lazy"
              />

              {/* Gradient Scrim */}
              <div
                className="absolute inset-0 pointer-events-none"
                style={{
                  background: 'linear-gradient(to top, rgba(0, 0, 0, 0.75) 0%, rgba(0, 0, 0, 0.2) 50%, rgba(0, 0, 0, 0) 100%)', // taste-skill-ignore
                }}
              />

              {/* Bottom Content Pod */}
              <div className="relative p-4 z-10 text-white flex flex-col justify-end">
                <h3 className="text-lg font-bold text-white mb-1 group-hover:text-cyan-300 transition-colors">
                  {displayName}
                </h3>

                <p className="text-[13px] text-slate-100 line-clamp-2 mb-3 leading-relaxed">
                  {displayTagline}
                </p>

                <Link
                  to={`/search?location=${encodeURIComponent(displayName)}`}
                  className="w-full py-2 px-3 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-semibold text-[13px] flex items-center justify-center gap-1.5 transition-all"
                >
                  <span>{t.panels.exploreSpot}</span>
                  <ArrowRight className="w-4 h-4" />
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
