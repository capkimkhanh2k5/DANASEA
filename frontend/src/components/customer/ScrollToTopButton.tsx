import React, { useState, useEffect } from 'react';

export const ScrollToTopButton: React.FC = () => {
  const [isVisible, setIsVisible] = useState<boolean>(false);
  const [isSliding, setIsSliding] = useState<boolean>(false);
  const progressCircleRef = React.useRef<SVGCircleElement>(null);
  const radius = 20;
  const circumference = 2 * Math.PI * radius;

  useEffect(() => {
    let ticking = false;

    const handleScroll = () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          const totalHeight = document.documentElement.scrollHeight - window.innerHeight;
          if (totalHeight > 0 && progressCircleRef.current) {
            const currentProgress = Math.min(100, Math.max(0, (window.scrollY / totalHeight) * 100));
            const offset = circumference - (currentProgress / 100) * circumference;
            progressCircleRef.current.style.strokeDashoffset = `${offset}`;
          }
          
          const shouldShow = window.scrollY > 300;
          setIsVisible((prev) => (prev !== shouldShow ? shouldShow : prev));
          ticking = false;
        });
        ticking = true;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [circumference]);

  // Smooth Carousel Inertia Glide Scroll
  const scrollToTop = () => {
    setIsSliding(true);
    // Dispatch event to switch backdrop image to Hero immediately
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('danasea-scroll-to-top'));
    }
    const startPosition = window.scrollY;
    const startTime = performance.now();
    const duration = 650; // 650ms smooth momentum

    // Easing curve: ease-out quint (carousel deceleration)
    const easeOutQuint = (t: number) => 1 - Math.pow(1 - t, 5);

    const animateScroll = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const easedProgress = easeOutQuint(progress);

      window.scrollTo(0, Math.round(startPosition * (1 - easedProgress)));

      if (progress < 1) {
        requestAnimationFrame(animateScroll);
      } else {
        setTimeout(() => setIsSliding(false), 300);
      }
    };

    requestAnimationFrame(animateScroll);
  };

  return (
    <button
      onClick={scrollToTop}
      aria-label="Scroll to top"
      className={`fixed bottom-8 right-8 z-50 w-11 h-11 rounded-xl bg-white text-slate-700 hover:text-sky-600 border border-slate-200/90 shadow-md flex items-center justify-center group hover:scale-105 active:scale-95 transition-all duration-200 cursor-pointer overflow-hidden ${
        isVisible ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-90 pointer-events-none'
      }`}
    >





      {/* SVG Progress Ring */}
      <svg className="absolute inset-0 w-full h-full -rotate-90 pointer-events-none" viewBox="0 0 48 48">
        <circle
          cx="24"
          cy="24"
          r={radius}
          className="text-slate-100"
          strokeWidth="3"
          stroke="currentColor"
          fill="transparent"
        />
        <circle
          ref={progressCircleRef}
          cx="24"
          cy="24"
          r={radius}
          className="text-cyan-600 transition-all duration-150"
          strokeWidth="3"
          strokeDasharray={circumference}
          strokeDashoffset={circumference}
          strokeLinecap="round"
          stroke="currentColor"
          fill="transparent"
        />
      </svg>

      {/* Vertical Carousel Slide Icon Animation */}
      <div className="relative w-5 h-5 overflow-hidden flex flex-col items-center justify-center z-10 pointer-events-none">
        {/* Slide 1 (exits top when clicked) */}
        <span
          className={`flex items-center justify-center transition-transform duration-500 ease-out ${
            isSliding ? '-translate-y-6 opacity-0' : 'translate-y-0 opacity-100'
          }`}
        >
          <svg className="w-4 h-4 text-cyan-700 font-bold group-hover:-translate-y-0.5 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 10l7-7m0 0l7 7m-7-7v18" />
          </svg>
        </span>

        {/* Slide 2 (enters from bottom on click) */}
        <span
          className={`absolute flex items-center justify-center transition-transform duration-500 ease-out ${
            isSliding ? 'translate-y-0 opacity-100' : 'translate-y-6 opacity-0'
          }`}
        >
          <svg className="w-4 h-4 text-cyan-700 font-bold" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 10l7-7m0 0l7 7m-7-7v18" />
          </svg>
        </span>
      </div>
    </button>
  );
};

export default ScrollToTopButton;
