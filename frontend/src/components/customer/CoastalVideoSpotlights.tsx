import React, { useRef, useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useLanguageStore } from '../../store/useLanguageStore';

interface SpotlightItemData {
  id: string;
  locationTag: string;
  locationTagEn: string;
  title: string;
  titleEn: string;
  description: string;
  descriptionEn: string;
  videoUrl: string;
  posterUrl: string;
  ctaText: string;
  ctaTextEn: string;
  link: string;
}

const SPOTLIGHT_ITEMS: SpotlightItemData[] = [
  {
    id: 'spot-son-tra',
    locationTag: 'BÁN ĐẢO SƠN TRÀ',
    locationTagEn: 'SON TRA PENINSULA',
    title: 'Lặn Ngắm San Hô Nguyên Sinh Hòn Sụp',
    titleEn: 'Natural Coral Sanctuary at Hon Sup',
    description: 'Rạn san hô tự nhiên tại Hòn Sụp với làn nước trong sáng sớm và hướng dẫn viên lặn kèm sát 1:1.',
    descriptionEn: 'Natural coral reefs at Hon Sup with crystal morning water and 1:1 certified dive guide.',
    videoUrl: '/videos/danasea_ocean_10s_720p.mp4',
    posterUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1200&q=80',
    ctaText: 'Khám phá tour lặn biển Sơn Trà',
    ctaTextEn: 'Explore Son Tra Diving Tours',
    link: '/search?category=lan-ngam-san-ho',
  },
  {
    id: 'spot-my-khe',
    locationTag: 'VỊNH BIỂN MỸ KHÊ',
    locationTagEn: 'MY KHE COASTAL BAY',
    title: 'Cano Cao Tốc 150HP & Dù Bay Lượn Sóng',
    titleEn: 'High-Speed Speedboat & Parasailing',
    description: 'Ngắm trọn vịnh biển từ độ cao 80m trên dù bay và trải nghiệm cano cao tốc lướt sóng Mỹ Khê.',
    descriptionEn: 'Panoramic coastal views from 80m parasail and high-speed wave riding at My Khe.',
    videoUrl: '/videos/danasea_sunset_10s_720p.mp4',
    posterUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=80',
    ctaText: 'Xem lịch cano & dù bay Mỹ Khê',
    ctaTextEn: 'View My Khe Speedboat Slots',
    link: '/search?category=cano-du-bay',
  },
  {
    id: 'spot-bai-rang',
    locationTag: 'BÃI RẠNG & MÂN THÁI',
    locationTagEn: 'BAI RANG & MAN THAI',
    title: 'Thuyền Chuối Siêu Tốc & Lướt Sóng Bãi Rạng',
    titleEn: 'Epic Banana Boat Ride & Surf Waves',
    description: 'Trải nghiệm thuyền chuối siêu tốc lướt trên từng ngọn sóng cùng đội cano cứu hộ theo sát.',
    descriptionEn: 'High-speed banana boat rides across coastal swells with safety escort patrol.',
    videoUrl: '/videos/danasea_banana_boat_10s_720p.mp4',
    posterUrl: 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=1200&q=80',
    ctaText: 'Khám phá thuyền chuối & kayak',
    ctaTextEn: 'Explore Banana Boat & Kayak',
    link: '/search?category=cheo-sup-kayak',
  },
];

// Lazy looping video player with viewport play/pause
const SpotlightVideo: React.FC<{ src: string; poster: string; title: string }> = ({ src, poster, title }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [isInView, setIsInView] = useState(false);

  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsInView(true);
          el.play().catch(() => {});
        } else {
          el.pause();
        }
      },
      { threshold: 0.25 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div className="relative w-full aspect-[16/10] rounded-3xl overflow-hidden bg-slate-900 shadow-2xl border border-slate-200/90 group">
      <video
        ref={videoRef}
        src={isInView ? src : undefined}
        poster={poster}
        preload="none"
        autoPlay
        loop
        muted
        playsInline
        aria-label={title}
        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-out"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-slate-950/40 via-transparent to-transparent pointer-events-none" />
    </div>
  );
};

// Single spotlight block with scroll entrance animation
const SpotlightBlock: React.FC<{
  item: SpotlightItemData;
  index: number;
}> = ({ item, index }) => {
  const { language } = useLanguageStore();
  const blockRef = useRef<HTMLDivElement>(null);
  const [hasEntered, setHasEntered] = useState(false);

  // Even index: Text Left (col 1-6), Video Right (col 7-12)
  // Odd index: Video Left (col 1-6), Text Right (col 7-12)
  const isVideoOnRight = index % 2 === 0;

  useEffect(() => {
    const el = blockRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setHasEntered(true);
        }
      },
      { threshold: 0.2 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const locationTag = language === 'en' ? item.locationTagEn : item.locationTag;
  const title = language === 'en' ? item.titleEn : item.title;
  const description = language === 'en' ? item.descriptionEn : item.description;

  return (
    <div ref={blockRef} className="relative z-10 w-full py-8 sm:py-12">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-16 items-center">
        
        {/* TEXT CONTENT COLUMN */}
        <div
          className={`lg:col-span-6 flex flex-col gap-3.5 text-left transition-all duration-1000 ease-out ${
            isVideoOnRight ? 'lg:order-1' : 'lg:order-2'
          } ${
            hasEntered
              ? 'opacity-100 translate-x-0'
              : isVideoOnRight
              ? 'opacity-0 -translate-x-14 sm:-translate-x-20'
              : 'opacity-0 translate-x-14 sm:translate-x-20'
          }`}
        >
          {/* Location Tag (No counting pill) */}
          <div className="w-full max-w-xl flex justify-center">
            <span className="text-xs font-bold text-sky-700 uppercase tracking-widest block text-center">
              {locationTag}
            </span>
          </div>

          {/* Heading */}
          <h3 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-slate-900 tracking-tight leading-snug">
            {title}
          </h3>

          {/* Short Factual Description (No notes, no spec pills) */}
          <p className="text-sm sm:text-base text-slate-600 leading-relaxed font-normal max-w-xl">
            {description}
          </p>

          {/* CTA Link Button: Outline đen, chữ đen, nền trắng, nội dung "Khám phá" */}
          <div className="pt-2 w-full max-w-xl flex justify-center">
            <Link
              to={item.link}
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-white hover:bg-slate-50 active:bg-slate-100 text-slate-900 border border-slate-900 font-bold text-xs sm:text-sm transition-all cursor-pointer shadow-xs group hover:-translate-y-0.5"
            >
              <span>{language === 'en' ? 'Explore' : 'Khám phá'}</span>
              <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
            </Link>
          </div>
        </div>

        {/* VIDEO COLUMN */}
        <div
          className={`lg:col-span-6 transition-all duration-1000 ease-out delay-100 ${
            isVideoOnRight ? 'lg:order-2' : 'lg:order-1'
          } ${
            hasEntered
              ? 'opacity-100 translate-x-0'
              : isVideoOnRight
              ? 'opacity-0 translate-x-14 sm:translate-x-20'
              : 'opacity-0 -translate-x-14 sm:-translate-x-20'
          }`}
        >
          <SpotlightVideo src={item.videoUrl} poster={item.posterUrl} title={title} />
        </div>

      </div>
    </div>
  );
};

// Curving Dashed Connection SVG Line between Video 1 (Right) and Video 2 (Left)
const CurvingDashedConnectorRightToLeft: React.FC = () => {
  return (
    <div className="w-full h-44 sm:h-56 lg:h-64 relative my-6 sm:my-10 overflow-visible pointer-events-none hidden lg:block">
      <svg
        className="w-full h-full overflow-visible"
        viewBox="0 0 1000 240"
        fill="none"
        preserveAspectRatio="none"
      >
        <path
          d="M 750,0 C 750,120 250,120 250,240"
          stroke="#0284c7"
          strokeWidth="2.5"
          strokeDasharray="8 8"
          strokeOpacity="0.45"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
};

// Curving Dashed Connection SVG Line between Video 2 (Left) and Video 3 (Right)
const CurvingDashedConnectorLeftToRight: React.FC = () => {
  return (
    <div className="w-full h-44 sm:h-56 lg:h-64 relative my-6 sm:my-10 overflow-visible pointer-events-none hidden lg:block">
      <svg
        className="w-full h-full overflow-visible"
        viewBox="0 0 1000 240"
        fill="none"
        preserveAspectRatio="none"
      >
        <path
          d="M 250,0 C 250,120 750,120 750,240"
          stroke="#0284c7"
          strokeWidth="2.5"
          strokeDasharray="8 8"
          strokeOpacity="0.45"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
};

// Mobile / Tablet Vertical Dashed Line
const MobileDashedConnector: React.FC = () => {
  return (
    <div className="w-full h-20 sm:h-28 flex items-center justify-center pointer-events-none lg:hidden my-4 sm:my-6">
      <div className="w-0.5 h-full border-r-2 border-dashed border-sky-400/50" />
    </div>
  );
};

export const CoastalVideoSpotlights: React.FC = () => {
  return (
    <section className="w-full py-10 sm:py-16 relative overflow-hidden bg-gradient-to-b from-white via-slate-50/60 to-white">
      <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Alternating Blocks with Curving Dashed Connectors (Directly to content, no section header banner) */}
        <div className="relative">
          
          {/* Block 1: Son Tra (Text Left, Video Right) */}
          <SpotlightBlock item={SPOTLIGHT_ITEMS[0]} index={0} />

          {/* Connector 1 -> 2 */}
          <CurvingDashedConnectorRightToLeft />
          <MobileDashedConnector />

          {/* Block 2: My Khe (Video Left, Text Right) */}
          <SpotlightBlock item={SPOTLIGHT_ITEMS[1]} index={1} />

          {/* Connector 2 -> 3 */}
          <CurvingDashedConnectorLeftToRight />
          <MobileDashedConnector />

          {/* Block 3: Bai Rang (Text Left, Video Right) */}
          <SpotlightBlock item={SPOTLIGHT_ITEMS[2]} index={2} />

        </div>

      </div>
    </section>
  );
};

export default CoastalVideoSpotlights;
