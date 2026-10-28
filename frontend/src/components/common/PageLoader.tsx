import React, { useEffect, useState, useRef } from 'react';
import { useLoadingStore } from '../../store/useLoadingStore';

export function PageLoader() {
  const { isLoading, durationMs, hideLoader, showLoader } = useLoadingStore();
  const [progress, setProgress] = useState(0);
  const [isExiting, setIsExiting] = useState(false);
  const rafRef = useRef<number | null>(null);
  const startTimeRef = useRef<number | null>(null);

  // 1. Initial Splash Screen on first session visit
  useEffect(() => {
    const hasSeenSplash = sessionStorage.getItem('danasea_initial_splash_shown');
    if (!hasSeenSplash) {
      sessionStorage.setItem('danasea_initial_splash_shown', 'true');
      showLoader('', '', 2400);
    }
  }, [showLoader]);

  // 2. Liquid Wave Progress Animation
  useEffect(() => {
    if (!isLoading) {
      setProgress(0);
      setIsExiting(false);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      return;
    }

    setProgress(0);
    setIsExiting(false);
    startTimeRef.current = null;

    const targetDuration = Math.max(durationMs || 2200, 1400);

    const step = (timestamp: number) => {
      if (!startTimeRef.current) startTimeRef.current = timestamp;
      const elapsed = timestamp - startTimeRef.current;
      const currentProgress = Math.min(100, (elapsed / targetDuration) * 100);

      setProgress(currentProgress);

      if (currentProgress < 100) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        // Hold at 100% for 250ms so user admires the fully filled logo, then smoothly fade out
        setTimeout(() => {
          setIsExiting(true);
          setTimeout(() => {
            hideLoader();
            setIsExiting(false);
            setProgress(0);
          }, 320);
        }, 250);
      }
    };

    rafRef.current = requestAnimationFrame(step);

    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [isLoading, durationMs, hideLoader]);

  if (!isLoading) return null;

  return (
    <div
      onClick={hideLoader}
      className={`fixed inset-0 z-[99999] flex items-center justify-center backdrop-blur-md bg-white/[0.02] dark:bg-black/10 select-none cursor-pointer transition-all duration-300 ${
        isExiting ? 'opacity-0 scale-105 pointer-events-none' : 'opacity-100 scale-100'
      }`}
      role="status"
      aria-label="Đang tải trang"
    >
      {/* =========================================================================
          PURE LIQUID WAVE LOGO (FLOATING SEAMLESSLY OVER FROSTED GLASS BACKGROUND)
         ========================================================================= */}
      <div className="relative w-56 h-56 sm:w-64 sm:h-64 flex items-center justify-center filter drop-shadow-[0_16px_36px_rgba(0,0,0,0.18)]">
        {/* Layer 1: Faint Logo Silhouette (Empty Glass Vessel Waiting to Fill) */}
        <img
          src="/images/danasea-logo.png"
          alt="DANASEA Logo Silhouette"
          className="absolute inset-0 w-full h-full object-contain opacity-20 filter contrast-125 select-none pointer-events-none"
        />

        {/* Layer 2: Rising Full-Color Logo (Dynamically pushed up from bottom to top) */}
        <div
          className="absolute inset-x-0 bottom-0 overflow-hidden pointer-events-none select-none"
          style={{ height: `${progress}%` }}
        >
          {/* Authentic Full-Color Logo pinned to bottom: coordinates match Layer 1 perfectly */}
          <div className="absolute inset-x-0 bottom-0 w-full h-56 sm:h-64 pointer-events-none select-none flex items-center justify-center">
            <img
              src="/images/danasea-logo.png"
              alt="DANASEA Logo Full Color"
              className="w-full h-full object-contain"
            />
          </div>
        </div>

        {/* Layer 3: Dynamic Ocean Wave Crests & White Foam (Rides exactly along the water level) */}
        {progress > 0 && progress < 100 && (
          <div
            className="absolute inset-0 w-full h-full select-none pointer-events-none overflow-hidden"
            style={{
              maskImage: 'url(/images/danasea-logo.png)',
              WebkitMaskImage: 'url(/images/danasea-logo.png)',
              maskSize: 'contain',
              WebkitMaskSize: 'contain',
              maskPosition: 'center',
              WebkitMaskPosition: 'center',
              maskRepeat: 'no-repeat',
              WebkitMaskRepeat: 'no-repeat',
            }}
          >
            {/* Wave strip centered directly on the rising water line */}
            <div
              className="absolute left-0 right-0 w-full h-16 sm:h-20 pointer-events-none -translate-y-1/2 overflow-visible"
              style={{
                top: `${100 - progress}%`,
              }}
            >
              {/* Wave 1: Back Wave (Brand Deep Sea Teal #08515a, rolling right to left) */}
              <div className="absolute inset-0 w-[200%] h-full opacity-75 animate-liquid-wave-rev pointer-events-none">
                <svg viewBox="0 0 1000 100" preserveAspectRatio="none" className="w-full h-full">
                  <path
                    d="M 0 50 Q 125 -10 250 50 T 500 50 Q 625 -10 750 50 T 1000 50"
                    fill="none"
                    stroke="#08515a"
                    strokeWidth="6"
                    strokeLinecap="round"
                  />
                </svg>
              </div>

              {/* Wave 2: Middle Wave (Brand Cyan-Teal #35a7ae, rolling left to right in counter-phase) */}
              <div className="absolute inset-0 w-[200%] h-full opacity-85 animate-liquid-wave-mid pointer-events-none">
                <svg viewBox="0 0 1000 100" preserveAspectRatio="none" className="w-full h-full">
                  <path
                    d="M 0 50 Q 125 110 250 50 T 500 50 Q 625 110 750 50 T 1000 50"
                    fill="none"
                    stroke="#35a7ae"
                    strokeWidth="5"
                    strokeLinecap="round"
                  />
                </svg>
              </div>

              {/* Wave 3: Front Wave (Brand Sea Spray & Dazzling White Foam Crest, fast rolling) */}
              <div className="absolute inset-0 w-[200%] h-full opacity-95 animate-liquid-wave pointer-events-none">
                <svg viewBox="0 0 1000 100" preserveAspectRatio="none" className="w-full h-full">
                  {/* Brand Cyan Glow along the wave crest */}
                  <path
                    d="M 0 50 Q 62.5 -5 125 50 T 250 50 Q 312.5 -5 375 50 T 500 50 Q 562.5 -5 625 50 T 750 50 Q 812.5 -5 875 50 T 1000 50"
                    fill="none"
                    stroke="#35a7ae"
                    strokeWidth="10"
                    strokeLinecap="round"
                    opacity="0.5"
                  />
                  {/* Crisp White Foam Crest along the wave peaks */}
                  <path
                    d="M 0 50 Q 62.5 -5 125 50 T 250 50 Q 312.5 -5 375 50 T 500 50 Q 562.5 -5 625 50 T 750 50 Q 812.5 -5 875 50 T 1000 50"
                    fill="none"
                    stroke="#ffffff"
                    strokeWidth="4"
                    strokeLinecap="round"
                    opacity="0.95"
                  />
                </svg>
              </div>
            </div>

            {/* Rising Sea Bubbles (strictly masked inside the logo geometry) */}
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
              <span className="absolute left-[20%] bottom-3 w-2.5 h-2.5 rounded-full bg-white/80 animate-liquid-bubble-1 filter drop-shadow-[0_0_4px_rgba(255,255,255,0.8)]" />
              <span className="absolute left-[40%] bottom-4 w-2 h-2 rounded-full bg-white/90 animate-liquid-bubble-2 filter drop-shadow-[0_0_4px_rgba(255,255,255,0.8)]" />
              <span className="absolute left-[65%] bottom-2 w-3 h-3 rounded-full bg-white/70 animate-liquid-bubble-3 filter drop-shadow-[0_0_4px_rgba(255,255,255,0.8)]" />
              <span className="absolute left-[85%] bottom-5 w-2 h-2 rounded-full bg-white/85 animate-liquid-bubble-4 filter drop-shadow-[0_0_4px_rgba(255,255,255,0.8)]" />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default PageLoader;
