import React, { useEffect, useRef, useState } from 'react';

interface ImageScene {
  id: string;
  url: string;
  label: string;
  badge: string;
}

const IMAGE_SCENES: ImageScene[] = [
  {
    id: 'hero',
    url: '/images/scenes/sean-oulashin-KMn4VEeEPR8-unsplash.jpg',
    label: 'Bãi biển Mỹ Khê & Biển Sớm',
    badge: '01 / 04',
  },
  {
    id: 'bays',
    url: '/images/scenes/danny-rienecker-19Dam2dFVC8-unsplash.jpg',
    label: 'Vịnh Bán Đảo Sơn Trà',
    badge: '02 / 04',
  },
  {
    id: 'sports',
    url: '/images/scenes/brandon-mcdonald-_oU62drqdho-unsplash.jpg',
    label: 'Đại Dương & Thể Thao Biển',
    badge: '03 / 04',
  },
  {
    id: 'sunset',
    url: '/images/scenes/austin-neill-uHD0uyp79Dg-unsplash.jpg',
    label: 'Hoàng Hôn Non Nước Lãng Mạn',
    badge: '04 / 04',
  },
];

export const CinematicSceneBackdrop: React.FC = () => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [activeSceneId, setActiveSceneId] = useState<string>('hero');

  // 1. Mouse Parallax (Stratum Drift)
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    let tx = 0, ty = 0, cx = 0, cy = 0;
    let isRunning = false;
    let rafId: number;

    const tick = () => {
      cx += (tx - cx) * 0.045;
      cy += (ty - cy) * 0.045;
      const w = window.innerWidth;
      const h = window.innerHeight;
      const dx = -cx * w * 0.012;
      const dy = -cy * h * 0.012;
      const cover = 2 * Math.max(Math.abs(dx) / w, Math.abs(dy) / h);
      const sc = 1.04 + cover + 0.01 * Math.max(0, cy);

      if (containerRef.current) {
        containerRef.current.style.transform = `translate3d(${dx.toFixed(2)}px, ${dy.toFixed(2)}px, 0) scale(${sc.toFixed(4)})`;
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

  // 3. Scene switching driven by scroll position (Symmetric Up & Down, and Instant on Scroll-to-Top)
  useEffect(() => {
    let ticking = false;

    const updateActiveScene = () => {
      const scrollY = window.scrollY || document.documentElement.scrollTop;
      if (scrollY < 200) {
        setActiveSceneId('hero');
        ticking = false;
        return;
      }

      const sections = Array.from(document.querySelectorAll<HTMLElement>('[data-scene]'));
      if (!sections.length) return;

      const viewportMid = window.innerHeight * 0.5;
      let bestScene = 'hero';
      let minDistance = Infinity;

      for (const section of sections) {
        const rect = section.getBoundingClientRect();
        const scene = section.getAttribute('data-scene');
        if (!scene) continue;

        // If the focal point (viewport center) is inside this section, it's 100% active
        if (rect.top <= viewportMid && rect.bottom >= viewportMid) {
          bestScene = scene;
          break;
        }

        // Otherwise calculate closest distance to the focal point
        const dist = rect.bottom < viewportMid ? (viewportMid - rect.bottom) : (rect.top - viewportMid);
        if (dist < minDistance) {
          minDistance = dist;
          bestScene = scene;
        }
      }

      setActiveSceneId((prev) => (prev !== bestScene ? bestScene : prev));
      ticking = false;
    };

    const onScroll = () => {
      if (!ticking) {
        requestAnimationFrame(updateActiveScene);
        ticking = true;
      }
    };

    const handleScrollToTopEvent = () => {
      setActiveSceneId('hero');
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('danasea-scroll-to-top', handleScrollToTopEvent);
    updateActiveScene();

    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('danasea-scroll-to-top', handleScrollToTopEvent);
    };
  }, []);

  const _activeScene = IMAGE_SCENES.find((s) => s.id === activeSceneId) || IMAGE_SCENES[0];

  return (
    <>

      {/* Full-Page Background Container */}
      <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden bg-slate-950 select-none">
        {/* Parallax Container */}
        <div
          ref={containerRef}
          className="absolute inset-0 w-full h-full"
          style={{ transformOrigin: '50% 50%' }}
        >
          {IMAGE_SCENES.map((scene, idx) => {
            const isActive = scene.id === activeSceneId;

            return (
              <img
                key={scene.id}
                src={scene.url}
                alt=""
                aria-hidden="true"
                loading={idx === 0 ? 'eager' : 'lazy'}
                className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-1000 ease-in-out ${
                  isActive ? 'opacity-100' : 'opacity-0'
                }`}
              />
            );
          })}
        </div>

        {/* Optical Contrast Scrim: Soft ambient layer protecting text legibility over white foam & harsh sunlight */}
        <div className="absolute inset-0 bg-gradient-to-b from-slate-950/40 via-slate-950/20 to-slate-950/60 pointer-events-none transition-opacity duration-700" />
      </div>

    </>
  );
};

export default CinematicSceneBackdrop;
