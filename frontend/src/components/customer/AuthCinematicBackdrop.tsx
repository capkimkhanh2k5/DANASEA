import React, { useEffect, useRef, useState } from 'react';

export interface ImageScene {
  id: string;
  url: string;
  label: string;
  badge: string;
  subtitle: string;
}

const AUTH_SCENES: ImageScene[] = [
  {
    id: 'hero',
    url: '/images/scenes/sean-oulashin-KMn4VEeEPR8-unsplash.jpg',
    label: 'Bãi biển Mỹ Khê & Biển Sớm',
    badge: '01 / 04',
    subtitle: 'Mặt trời ló rạng & chèo SUP bình minh',
  },
  {
    id: 'bays',
    url: '/images/scenes/danny-rienecker-19Dam2dFVC8-unsplash.jpg',
    label: 'Vịnh Bán Đảo Sơn Trà',
    badge: '02 / 04',
    subtitle: 'Nước ngọc bích & rạn san hô hoang sơ',
  },
  {
    id: 'sports',
    url: '/images/scenes/brandon-mcdonald-_oU62drqdho-unsplash.jpg',
    label: 'Đại Dương & Thể Thao Biển',
    badge: '03 / 04',
    subtitle: 'Cano cao tốc & lướt ván dù lượn',
  },
  {
    id: 'sunset',
    url: '/images/scenes/austin-neill-uHD0uyp79Dg-unsplash.jpg',
    label: 'Hoàng Hôn Non Nước Lãng Mạn',
    badge: '04 / 04',
    subtitle: 'Ráng chiều đỏ & không gian biển yên bình',
  },
];

interface AuthCinematicBackdropProps {
  autoRotateInterval?: number;
}

export const AuthCinematicBackdrop: React.FC<AuthCinematicBackdropProps> = ({
  autoRotateInterval = 5500,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [progressKey, setProgressKey] = useState<number>(0);

  // 1. Auto-transition interval
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % AUTH_SCENES.length);
      setProgressKey((k) => k + 1);
    }, autoRotateInterval);

    return () => clearInterval(timer);
  }, [autoRotateInterval]);

  // 2. Mouse Parallax Drift
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    let tx = 0, ty = 0, cx = 0, cy = 0;
    let isRunning = false;
    let rafId: number;

    const tick = () => {
      cx += (tx - cx) * 0.04;
      cy += (ty - cy) * 0.04;
      const w = window.innerWidth;
      const h = window.innerHeight;
      const dx = -cx * w * 0.01;
      const dy = -cy * h * 0.01;
      const sc = 1.05;

      if (containerRef.current) {
        containerRef.current.style.transform = `translate3d(${dx.toFixed(2)}px, ${dy.toFixed(2)}px, 0) scale(${sc})`;
      }

      if (Math.abs(tx - cx) < 0.0004 && Math.abs(ty - cy) < 0.0004) {
        isRunning = false;
        return;
      }
      rafId = requestAnimationFrame(tick);
    };

    const wake = () => {
      if (!isRunning) {
        isRunning = true;
        rafId = requestAnimationFrame(tick);
      }
    };

    const handlePointerMove = (e: PointerEvent) => {
      if (e.pointerType === 'touch') return;
      tx = (e.clientX / window.innerWidth) * 2 - 1;
      ty = (e.clientY / window.innerHeight) * 2 - 1;
      wake();
    };

    const handlePointerLeave = () => {
      tx = 0;
      ty = 0;
      wake();
    };

    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    document.addEventListener('pointerleave', handlePointerLeave);

    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      document.removeEventListener('pointerleave', handlePointerLeave);
      if (rafId) cancelAnimationFrame(rafId);
    };
  }, []);

  return (
    <>
      {/* Top Countdown Progress Line */}
      <div className="fixed top-0 left-0 right-0 h-[2.5px] z-[60] bg-slate-800/40 pointer-events-none">
        <div
          key={progressKey}
          className="h-full bg-gradient-to-r from-cyan-400 via-teal-300 to-amber-300 origin-left"
          style={{
            animation: `sceneCountdown ${autoRotateInterval}ms linear forwards`,
          }}
        />
      </div>

      {/* Full-Page Background Container */}
      <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden bg-slate-950 select-none">
        {/* Parallax Container */}
        <div
          ref={containerRef}
          className="absolute inset-0 w-full h-full transition-transform duration-300 ease-out"
          style={{ transformOrigin: '50% 50%' }}
        >
          {AUTH_SCENES.map((scene, idx) => {
            const isActive = idx === currentIndex;

            return (
              <img
                key={scene.id}
                src={scene.url}
                alt=""
                aria-hidden="true"
                loading={idx === 0 ? 'eager' : 'lazy'}
                className={`absolute inset-0 w-full h-full object-cover transition-all duration-1000 ease-in-out ${
                  isActive ? 'opacity-100 scale-100' : 'opacity-0 scale-105'
                }`}
              />
            );
          })}
        </div>

        {/* Optical Contrast Scrim: Rich coastal gradient ensuring optimal text & card readability */}
        <div className="absolute inset-0 bg-gradient-to-tr from-slate-950/85 via-slate-950/50 to-slate-950/70 pointer-events-none" />
      </div>
    </>
  );
};

export default AuthCinematicBackdrop;
