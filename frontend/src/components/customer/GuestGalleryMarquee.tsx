import React, { useState, useEffect } from 'react';
import { useLanguageStore } from '../../store/useLanguageStore';
import { apiClient } from '../../services/apiClient';

interface GuestCard {
  imageUrl: string;
  guestName: string;
  guestLocation: string;
  activity: string;
  activityEn: string;
  quote: string;
  quoteEn: string;
  rating: number;
  isReal?: boolean;
}

export const GuestGalleryMarquee: React.FC = () => {
  const { language } = useLanguageStore();
  const [row1, setRow1] = useState<GuestCard[]>([]);
  const [row2, setRow2] = useState<GuestCard[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    async function loadRealShowcase() {
      try {
        setIsLoading(true);
        const res = await apiClient.get('/services', { params: { size: 12 } });
        const services = res.data?.data?.content || res.data?.content || [];
        if (!Array.isArray(services) || services.length === 0) return;

        const cards: GuestCard[] = [];

        // 1. Gather real reviews from real services
        for (const s of services) {
          try {
            const revRes = await apiClient.get(`/services/${s.id}/reviews`, { params: { size: 3 } });
            const reviews = revRes.data?.data?.content || revRes.data?.content || [];
            if (Array.isArray(reviews)) {
              for (const r of reviews) {
                if (r.isVisible !== false && r.comment) {
                  cards.push({
                    imageUrl: r.customerAvatar || s.primaryImageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=400&q=80',
                    guestName: r.customerName || (language === 'en' ? 'Verified Guest' : 'Du khách DANASEA'),
                    guestLocation: language === 'en' ? 'Verified Experience' : 'Đã trải nghiệm',
                    activity: s.name,
                    activityEn: s.nameEn || s.name,
                    quote: r.comment,
                    quoteEn: r.comment,
                    rating: r.rating || 5,
                    isReal: true,
                  });
                }
              }
            }
          } catch {
            // Ignore individual service review error
          }
        }

        // 2. Add real service highlight cards from the real catalog
        for (const s of services) {
          cards.push({
            imageUrl: s.primaryImageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=400&q=80',
            guestName: s.vendorName || (language === 'en' ? 'Verified Provider' : 'Đơn vị tổ chức uy tín'),
            guestLocation: s.location || 'Đà Nẵng',
            activity: s.name,
            activityEn: s.nameEn || s.name,
            quote: s.shortDescription || s.description || (language === 'en' ? 'Certified marine sport experience' : 'Trải nghiệm thể thao biển an toàn đạt chuẩn SOLAS'),
            quoteEn: s.shortDescription || s.description || 'Certified marine sport experience',
            rating: s.averageRating && s.averageRating > 0 ? Number(s.averageRating.toFixed(1)) : 5,
            isReal: true,
          });
        }

        if (isMounted && cards.length > 0) {
          const newRow1: GuestCard[] = [];
          const newRow2: GuestCard[] = [];

          cards.forEach((card, idx) => {
            if (idx % 2 === 0) newRow1.push(card);
            else newRow2.push(card);
          });

          // Duplicate cards if needed for seamless infinite marquee loop
          const minCardsPerRow = 6;
          while (newRow1.length > 0 && newRow1.length < minCardsPerRow) {
            newRow1.push(...newRow1);
          }
          while (newRow2.length > 0 && newRow2.length < minCardsPerRow) {
            newRow2.push(...newRow2);
          }

          setRow1(newRow1);
          setRow2(newRow2);
        }
      } catch (err) {
        console.warn('[GuestGalleryMarquee] Lỗi tải dữ liệu thật:', err);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadRealShowcase();
    return () => {
      isMounted = false;
    };
  }, [language]);

  if (isLoading && row1.length === 0) {
    return null;
  }

  if (row1.length === 0 && row2.length === 0) {
    return null;
  }

  return (
    <div className="py-8 sm:py-12 overflow-hidden bg-transparent">
      <style>{`
        @keyframes marquee-left {
          0% { transform: translate3d(0, 0, 0); }
          100% { transform: translate3d(-50%, 0, 0); }
        }
        @keyframes marquee-right {
          0% { transform: translate3d(-50%, 0, 0); }
          100% { transform: translate3d(0, 0, 0); }
        }
        .animate-marquee-left {
          display: flex;
          width: max-content;
          will-change: transform;
          animation: marquee-left 45s linear infinite;
        }
        .animate-marquee-right {
          display: flex;
          width: max-content;
          will-change: transform;
          animation: marquee-right 45s linear infinite;
        }
        .animate-marquee-left:hover,
        .animate-marquee-right:hover {
          animation-play-state: paused;
        }
      `}</style>

      {/* Row 1: Left */}
      <div className="flex overflow-hidden mb-4 sm:mb-6">
        <div className="animate-marquee-left flex gap-4 sm:gap-6 pl-4">
          {[...row1, ...row1].map((card, idx) => (
            <div
              key={`r1-${idx}`}
              className="w-72 sm:w-80 shrink-0 bg-white/90 backdrop-blur-md rounded-2xl p-4 border border-slate-200/80 shadow-xs hover:shadow-md transition-all duration-300 -translate-y-0.5"
            >
              <div className="flex items-center gap-3 mb-3">
                <img
                  src={card.imageUrl}
                  alt={card.guestName}
                  className="w-12 h-12 rounded-full object-cover border border-slate-200"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=400&q=80';
                  }}
                />
                <div className="flex-1 min-w-0">
                  <h4 className="text-sm font-bold text-slate-900 truncate">{card.guestName}</h4>
                  <p className="text-xs text-slate-500 truncate">{card.guestLocation}</p>
                </div>
                <div className="flex items-center gap-0.5 text-amber-500 font-bold text-xs">
                  <span className="material-symbols-outlined text-[16px]" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                  <span>{card.rating}</span>
                </div>
              </div>
              <div className="mb-2">
                <span className="inline-block px-2 py-0.5 rounded text-[11px] font-semibold bg-sky-50 text-sky-700 border border-sky-200 truncate max-w-full">
                  {language === 'en' ? card.activityEn : card.activity}
                </span>
              </div>
              <p className="text-xs text-slate-600 line-clamp-2 italic leading-relaxed">
                “{language === 'en' ? card.quoteEn : card.quote}”
              </p>
            </div>
          ))}
        </div>
      </div>

      {/* Row 2: Right */}
      <div className="flex overflow-hidden">
        <div className="animate-marquee-right flex gap-4 sm:gap-6 pl-4">
          {[...row2, ...row2].map((card, idx) => (
            <div
              key={`r2-${idx}`}
              className="w-72 sm:w-80 shrink-0 bg-white/90 backdrop-blur-md rounded-2xl p-4 border border-slate-200/80 shadow-xs hover:shadow-md transition-all duration-300 -translate-y-0.5"
            >
              <div className="flex items-center gap-3 mb-3">
                <img
                  src={card.imageUrl}
                  alt={card.guestName}
                  className="w-12 h-12 rounded-full object-cover border border-slate-200"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=400&q=80';
                  }}
                />
                <div className="flex-1 min-w-0">
                  <h4 className="text-sm font-bold text-slate-900 truncate">{card.guestName}</h4>
                  <p className="text-xs text-slate-500 truncate">{card.guestLocation}</p>
                </div>
                <div className="flex items-center gap-0.5 text-amber-500 font-bold text-xs">
                  <span className="material-symbols-outlined text-[16px]" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                  <span>{card.rating}</span>
                </div>
              </div>
              <div className="mb-2">
                <span className="inline-block px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 truncate max-w-full">
                  {language === 'en' ? card.activityEn : card.activity}
                </span>
              </div>
              <p className="text-xs text-slate-600 line-clamp-2 italic leading-relaxed">
                “{language === 'en' ? card.quoteEn : card.quote}”
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default GuestGalleryMarquee;
