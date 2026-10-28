import { Outlet, useLocation } from "react-router-dom";
import { Header } from "../components/layout/Header";
import { Footer } from "../components/layout/Footer";
import { useEffect, useState } from "react";
import { useWishlistStore } from "../store/useWishlistStore";

// Định nghĩa thứ tự các trang chính để xác định hướng trượt
const PAGE_ORDER: Record<string, number> = {
  "/": 0,
  "/search": 1,
  "/cart": 2,
  "/profile": 3,
};

export function CustomerLayout() {
  const location = useLocation();
  const [prevPath, setPrevPath] = useState(location.pathname);
  const [animationClass, setAnimationClass] = useState("animate-fade-in-up");
  const { toastMessage, toastTimestamp, clearToast } = useWishlistStore();
  const [currentToast, setCurrentToast] = useState<{ text: string; id: number } | null>(null);
  const [isExiting, setIsExiting] = useState(false);

  useEffect(() => {
    if (toastMessage && toastTimestamp) {
      setIsExiting(false);
      setCurrentToast({ text: toastMessage, id: toastTimestamp });

      // After 2.5s, trigger exit slide-out animation to the right
      const exitTimer = setTimeout(() => {
        setIsExiting(true);
      }, 2500);

      // After 2.5s + 380ms = 2880ms, clean up
      const clearTimer = setTimeout(() => {
        clearToast();
        setCurrentToast(null);
      }, 2880);

      return () => {
        clearTimeout(exitTimer);
        clearTimeout(clearTimer);
      };
    }
  }, [toastMessage, toastTimestamp, clearToast]);

  const handleCloseToast = () => {
    setIsExiting(true);
    setTimeout(() => {
      clearToast();
      setCurrentToast(null);
    }, 350);
  };

  if (location.pathname !== prevPath) {
    const prevOrder = PAGE_ORDER[prevPath] ?? 0;
    const currentOrder = PAGE_ORDER[location.pathname] ?? 0;
    setPrevPath(location.pathname);
    if (currentOrder > prevOrder) {
      setAnimationClass("animate-slide-in-right");
    } else if (currentOrder < prevOrder) {
      setAnimationClass("animate-slide-in-left");
    } else {
      setAnimationClass("animate-fade-in-up");
    }
  }

  useEffect(() => {
    if (location.hash) {
      const timer = setTimeout(() => {
        const element = document.querySelector(location.hash);
        if (element) {
          const headerHeight = 80;
          const targetPosition = element.getBoundingClientRect().top + window.scrollY - headerHeight;
          window.scrollTo({ top: targetPosition, behavior: "smooth" });
        }
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [location.hash]);

  const isHomePage = location.pathname === '/';
  const isAuthPage = location.pathname === '/auth';

  return (
    <div className={`${isHomePage ? 'bg-transparent' : isAuthPage ? 'bg-slate-950' : 'bg-surface'} font-body-md text-on-surface min-h-screen flex flex-col overflow-x-hidden`}>
      <Header />
      {/* Bao bọc Outlet bằng một the div chứa key={location.pathname} để ép React render lại khi đổi route */}
      <div
        key={location.pathname}
        onAnimationEnd={() => setAnimationClass("")}
        className={`flex-1 flex flex-col ${animationClass}`}
      >
        <Outlet />
      </div>
      {!isAuthPage && <Footer />}

      {/* Global Floating Wishlist Toast Notification (Carousel slide: right-to-left in, left-to-right out) */}
      {currentToast && (
        <div
          key={currentToast.id}
          className={`fixed top-24 right-5 sm:top-24 sm:right-8 z-[99999] bg-emerald-500/35 backdrop-blur-xl text-white px-4 py-2.5 rounded-2xl shadow-[0_12px_36px_-4px_rgba(5,150,105,0.35)] flex items-center gap-3 border border-emerald-400/40 transform-gpu pointer-events-auto ${
            isExiting ? 'animate-toast-slide-out' : 'animate-toast-slide-in'
          }`}
        >
          <div className="w-7 h-7 rounded-full bg-white/20 border border-white/30 flex items-center justify-center shrink-0">
            <span
              className="material-symbols-outlined text-rose-400 text-[18px]"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              favorite
            </span>
          </div>
          <span className="font-semibold text-xs sm:text-sm text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.6)] whitespace-nowrap pr-1">
            {currentToast.text}
          </span>
          <button
            onClick={handleCloseToast}
            className="ml-auto p-1 rounded-full text-white/70 hover:text-white hover:bg-white/20 cursor-pointer transition-colors"
            aria-label="Close toast"
          >
            <span className="material-symbols-outlined text-[16px]">close</span>
          </button>
        </div>
      )}
    </div>
  );
}
