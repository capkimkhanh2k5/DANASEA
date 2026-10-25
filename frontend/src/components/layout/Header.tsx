import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/useAuthStore";
import { useCartStore } from "../../store/useCartStore";
import { useWishlistStore } from "../../store/useWishlistStore";
import { useLanguageStore } from "../../store/useLanguageStore";

export function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  
  const { user, isAuthenticated } = useAuthStore();
  const cartItemCount = useCartStore((state) => state.getTotalItems());
  const wishlistCount = useWishlistStore((state) => state.wishlistIds.length);
  const { language, setLanguage, t } = useLanguageStore();

  const isActive = (path: string) => location.pathname === path;

  const scrollToSection = (id: string) => {
    if (location.pathname !== "/") {
      navigate("/" + id);
    } else {
      const element = document.querySelector(id);
      if (element) {
        const headerHeight = 80;
        const targetPosition = element.getBoundingClientRect().top + window.scrollY - headerHeight;
        const startPosition = window.scrollY;
        const distance = targetPosition - startPosition;
        const duration = 600; // 600ms
        let start: number | null = null;

        const easeInOutQuad = (t: number, b: number, c: number, d: number) => {
          t /= d / 2;
          if (t < 1) return (c / 2) * t * t + b;
          t--;
          return (-c / 2) * (t * (t - 2) - 1) + b;
        };

        const animation = (currentTime: number) => {
          if (start === null) start = currentTime;
          const timeElapsed = currentTime - start;
          const run = easeInOutQuad(timeElapsed, startPosition, distance, duration);
          window.scrollTo(0, run);
          if (timeElapsed < duration) requestAnimationFrame(animation);
        };

        requestAnimationFrame(animation);
      }
    }
  };

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-white/95 border-b border-slate-200/80 shadow-[0_4px_20px_-2px_rgba(16,47,58,0.06)] transform-gpu will-change-transform">
      <div className="h-20 max-w-[1440px] w-full mx-auto px-4 sm:px-8 lg:px-12 flex items-center justify-between gap-space-lg">
        <div className="flex items-center gap-space-sm flex-shrink-0">
          <img alt="DANASEA Brand Logo" className="h-8 w-8 object-contain shrink-0" src="/images/danasea-logo.png" />
          <Link to="/" className="font-headline-md text-headline-md tracking-tight text-slate-900 hover:text-primary transition-colors flex items-center gap-1.5 font-bold">DANASEA</Link>
        </div>
        
        <nav className="hidden xl:flex items-center gap-space-xl">
          <Link 
            to="/" 
            className={`font-label-lg text-label-lg transition-all hover:-translate-y-0.5 hover:text-primary ${isActive("/") ? "text-primary font-bold border-b-2 border-primary pb-1" : "text-slate-600"}`}
          >
            {t.nav.explore}
          </Link>
          <Link 
            to="/search" 
            className={`font-label-lg text-label-lg transition-all hover:-translate-y-0.5 hover:text-primary ${isActive("/search") ? "text-primary font-bold border-b-2 border-primary pb-1" : "text-slate-600"}`}
          >
            {t.nav.experiences}
          </Link>
          <button 
            onClick={() => scrollToSection('#destinations')} 
            className="font-label-lg text-label-lg text-slate-600 transition-all hover:-translate-y-0.5 hover:text-primary cursor-pointer"
          >
            {t.nav.destinations}
          </button>
        </nav>
        
        <div className="flex items-center gap-space-md flex-shrink-0">
          {/* Language Switcher Pill */}
          <div className="flex items-center bg-slate-100 p-0.5 rounded-full border border-slate-200 text-xs font-semibold">
            <button
              onClick={() => setLanguage('vi')}
              className={`px-3 py-1 rounded-full transition-all text-xs font-bold ${
                language === 'vi' 
                  ? 'bg-primary text-white shadow-xs' 
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              VI
            </button>
            <button
              onClick={() => setLanguage('en')}
              className={`px-3 py-1 rounded-full transition-all text-xs font-bold ${
                language === 'en' 
                  ? 'bg-primary text-white shadow-xs' 
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              EN
            </button>
          </div>
          
          <div className="flex items-center gap-space-xs">
            <Link to="/profile/wishlist" aria-label="Yêu thích" className={`relative w-10 h-10 rounded-full flex items-center justify-center bg-surface-container-low transition-colors ${wishlistCount > 0 ? 'text-red-500 hover:bg-red-50' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'}`}>
              <span className="material-symbols-outlined text-[20px]" style={{ fontVariationSettings: wishlistCount > 0 ? "'FILL' 1" : "'FILL' 0" }}>favorite</span>
              {wishlistCount > 0 && (
                <span className="absolute top-1 right-1 w-4 h-4 rounded-full bg-red-500 text-white font-label-sm text-[10px] font-bold flex items-center justify-center leading-none shadow-sm">{wishlistCount}</span>
              )}
            </Link>
            
            <Link to="/cart" aria-label="Giỏ hàng" className="relative w-10 h-10 rounded-full flex items-center justify-center bg-surface-container-low text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors">
              <span className="material-symbols-outlined text-[20px]">shopping_cart</span>
              {cartItemCount > 0 && (
                <span className="absolute top-1 right-1 w-4 h-4 rounded-full bg-secondary text-on-secondary font-label-sm text-[10px] flex items-center justify-center leading-none">{cartItemCount}</span>
              )}
            </Link>
          </div>
          
          <div className="hidden md:flex items-center gap-space-xs">
            {!isAuthenticated ? (
              <Link to="/auth" className="px-space-md py-space-xs rounded-full bg-surface-container-low text-on-surface font-label-lg text-label-lg hover:bg-surface-container-high transition-all">Đăng nhập</Link>
            ) : null}
            <button onClick={() => scrollToSection('#ai-concierge')} className="px-space-md py-space-xs rounded-full bg-primary-container text-on-primary font-label-lg text-label-lg shadow-[0_8px_20px_rgba(255,115,92,0.3)] hover:scale-[1.02] transition-transform cursor-pointer">Lên lịch cùng AI</button>
          </div>
          
          {isAuthenticated && user ? (
            <Link to="/profile" className="w-8 h-8 rounded-full overflow-hidden flex items-center justify-center flex-shrink-0 shadow-[0_2px_8px_rgba(170,53,36,0.25)] hover:ring-2 hover:ring-primary transition-all">
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.fullName} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full bg-primary flex items-center justify-center">
                  <span className="material-symbols-outlined text-on-primary text-[18px]">person</span>
                </div>
              )}
            </Link>
          ) : null}
        </div>
      </div>
    </header>
  );
}
