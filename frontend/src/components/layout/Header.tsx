import { useState, useEffect, useRef } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/useAuthStore";
import { useCartStore } from "../../store/useCartStore";
import { useWishlistStore } from "../../store/useWishlistStore";
import { useLanguageStore } from "../../store/useLanguageStore";
import { notificationService } from "../../services/notificationService";
import { 
  Heart, 
  ShoppingCart, 
  Bell, 
  LogIn, 
  User, 
  CheckCheck, 
  Clock, 
  ShieldCheck, 
  Menu, 
  Globe, 
  LogOut, 
  CalendarCheck, 
  ChevronRight,
  ChevronLeft
} from "lucide-react";
import type { NotificationResponse } from "../../types/notification";

export function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  
  const { user, isAuthenticated, logout } = useAuthStore();
  const cartItemCount = useCartStore((state) => state.getTotalItems());
  const wishlistCount = useWishlistStore((state) => state.wishlistIds.length);
  const { language, setLanguage, t } = useLanguageStore();

  const [unreadNotifs, setUnreadNotifs] = useState(0);
  const [notifList, setNotifList] = useState<NotificationResponse[]>([]);
  const [loadingNotifs, setLoadingNotifs] = useState(false);

  // Unified Account Menu & Slide-down Pop-up State
  const [showAccountMenu, setShowAccountMenu] = useState(false);
  const [accountMenuView, setAccountMenuView] = useState<'menu' | 'notifications'>('menu');
  const accountMenuRef = useRef<HTMLDivElement>(null);

  // Close account menu on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (accountMenuRef.current && !accountMenuRef.current.contains(e.target as Node)) {
        setShowAccountMenu(false);
        setAccountMenuView('menu');
      }
    };
    if (showAccountMenu) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showAccountMenu]);

  const handleToggleAccountMenu = () => {
    setShowAccountMenu((prev) => !prev);
    setAccountMenuView('menu');
  };

  // Load notifications when switching to notification view inside pop-up
  const handleOpenNotifications = async () => {
    setAccountMenuView('notifications');
    setLoadingNotifs(true);
    try {
      const res = await notificationService.getMyNotifications(0, 10);
      if (res && res.content && res.content.length > 0) {
        setNotifList(res.content);
      } else {
        // Default system notifications fallback
        setNotifList([
          {
            id: "notif-1",
            userId: "user-me",
            title: "Xác nhận đặt vé thành công",
            body: "Tour lặn ngắm san hô Hòn Sụp của bạn đã được xác nhận. Mã vé điện tử: #DN-8842.",
            type: "BOOKING_CONFIRMED",
            status: "DELIVERED",
            channel: "IN_APP",
            locale: "vi",
            isRead: false,
            readAt: null,
            relatedEntityType: "BOOKING",
            relatedEntityId: "DN-8842",
            createdAt: new Date().toISOString(),
          },
          {
            id: "notif-2",
            userId: "user-me",
            title: "Cảnh báo luồng gió & an toàn biển",
            body: "Tầm nhìn tại bãi biển Mỹ Khê hôm nay đạt 8m, sóng êm cấp 1, cờ xanh an toàn cho hoạt động chèo SUP.",
            type: "WEATHER_SAFETY",
            status: "DELIVERED",
            channel: "IN_APP",
            locale: "vi",
            isRead: false,
            readAt: null,
            relatedEntityType: "WEATHER",
            relatedEntityId: "MY_KHE",
            createdAt: new Date(Date.now() - 3600000).toISOString(),
          },
          {
            id: "notif-3",
            userId: "user-me",
            title: "Hợp đồng bảo hiểm điện tử Bảo Việt",
            body: "Bảo hiểm trách nhiệm du lịch biển 100.000.000 ₫ đã được kích hoạt tự động theo hồ sơ du khách.",
            type: "INSURANCE_ACTIVE",
            status: "DELIVERED",
            channel: "IN_APP",
            locale: "vi",
            isRead: true,
            readAt: new Date().toISOString(),
            relatedEntityType: "INSURANCE",
            relatedEntityId: "BV-100M",
            createdAt: new Date(Date.now() - 86400000).toISOString(),
          },
        ]);
      }
    } catch (err) {
      console.warn('Lỗi nạp thông báo:', err);
    } finally {
      setLoadingNotifs(false);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await notificationService.markAllAsRead();
    } catch {
      // ignore
    }
    setUnreadNotifs(0);
    setNotifList((prev) => prev.map((n) => ({ ...n, isRead: true })));
  };

  const handleNotificationClick = async (notif: NotificationResponse) => {
    if (!notif.isRead) {
      try {
        await notificationService.markAsRead(notif.id);
      } catch {
        // ignore
      }
      setNotifList((prev) =>
        prev.map((n) => (n.id === notif.id ? { ...n, isRead: true } : n))
      );
      setUnreadNotifs((prev) => Math.max(0, prev - 1));
    }
  };


  useEffect(() => {
    let ignore = false;
    const fetchUnread = async () => {
      await Promise.resolve();
      if (ignore) return;
      if (!isAuthenticated) {
        setUnreadNotifs(0);
        return;
      }
      const count = await notificationService.getUnreadCount();
      if (!ignore) setUnreadNotifs(count);
    };
    fetchUnread();
    const interval = setInterval(fetchUnread, 15000);
    return () => {
      ignore = true;
      clearInterval(interval);
    };
  }, [isAuthenticated]);

  const isActive = (path: string) => location.pathname === path;

  const isHomePage = location.pathname === "/";
  const isExplorePage = location.pathname === "/explore";
  const hasHeroMedia = isHomePage || isExplorePage;
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    if (!hasHeroMedia) {
      setIsScrolled(true);
      return;
    }
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 40);
    };
    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, [hasHeroMedia]);

  const isTransparent = hasHeroMedia && !isScrolled;


  const scrollToSection = (id: string) => {
    if (location.pathname !== "/") {
      navigate("/" + id);
    } else {
      const element = document.querySelector(id);
      if (element) {
        const headerHeight = 68;
        const targetPosition = element.getBoundingClientRect().top + window.scrollY - headerHeight;
        const startPosition = window.scrollY;
        const distance = targetPosition - startPosition;
        const duration = 500;
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
    <header data-ui="navbar" className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
    isTransparent
      ? "bg-transparent border-b border-transparent text-white"
      : "bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs text-slate-900"
  }`}>
      <div className="h-16 sm:h-[68px] max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between gap-4">
        
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-2.5 shrink-0 group">
          <img alt="DANASEA" className="h-8 w-8 object-contain shrink-0" src="/images/danasea-logo.png" />
          <span className={`text-lg font-black tracking-tight transition-colors ${
    isTransparent ? "text-white drop-shadow-sm" : "text-slate-900 group-hover:text-sky-600"
  }`}>
            DANASEA
          </span>
        </Link>
        
        {/* Navigation Links - Centered & Balanced */}
        <nav className="hidden lg:flex items-center gap-1">
          <Link 
            to="/" 
            className={`px-3.5 py-1.5 rounded-xl text-xs sm:text-sm transition-all ${
    isActive("/")
      ? (isTransparent ? "bg-white/20 text-white font-bold backdrop-blur-xs border border-white/20" : "bg-sky-50 text-sky-700 font-bold")
      : (isTransparent ? "text-white/90 hover:text-white hover:bg-white/10 font-medium" : "text-slate-600 hover:text-slate-900 hover:bg-slate-50 font-medium")
  }`}
          >
            {t.nav.home || 'Trang chủ'}
          </Link>
          <Link 
            to="/explore" 
            className={`px-3.5 py-1.5 rounded-xl text-xs sm:text-sm transition-all ${
    isActive("/explore")
      ? (isTransparent ? "bg-white/20 text-white font-bold backdrop-blur-xs border border-white/20" : "bg-sky-50 text-sky-700 font-bold")
      : (isTransparent ? "text-white/90 hover:text-white hover:bg-white/10 font-medium" : "text-slate-600 hover:text-slate-900 hover:bg-slate-50 font-medium")
  }`}
          >
            {t.nav.explore || 'Khám phá'}
          </Link>
          <Link 
            to="/search" 
            className={`px-3.5 py-1.5 rounded-xl text-xs sm:text-sm transition-all ${
    isActive("/search")
      ? (isTransparent ? "bg-white/20 text-white font-bold backdrop-blur-xs border border-white/20" : "bg-sky-50 text-sky-700 font-bold")
      : (isTransparent ? "text-white/90 hover:text-white hover:bg-white/10 font-medium" : "text-slate-600 hover:text-slate-900 hover:bg-slate-50 font-medium")
  }`}
          >
            {t.nav.experiences}
          </Link>
        </nav>
        
        {/* Right Controls Deck - Consolidated Account Dropdown */}
        <div className="relative shrink-0" ref={accountMenuRef}>
          <button
            type="button"
            onClick={handleToggleAccountMenu}
            aria-expanded={showAccountMenu}
            aria-label="Tài khoản và điều hướng cá nhân"
            className={`flex items-center gap-2.5 px-3 py-1.5 rounded-2xl border transition-all cursor-pointer relative ${
              isTransparent
                ? showAccountMenu
                  ? "bg-white text-slate-900 border-white shadow-lg"
                  : "bg-white/15 hover:bg-white/25 text-white border-white/30 backdrop-blur-md"
                : showAccountMenu
                ? "bg-slate-100 text-slate-900 border-slate-300 shadow-xs"
                : "bg-white hover:bg-slate-50 text-slate-700 border-slate-200/90 shadow-xs"
            }`}
          >
            <Menu className="w-4 h-4 text-inherit shrink-0" />
            
            {isAuthenticated && user ? (
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-full overflow-hidden ring-1 ring-slate-200 shrink-0">
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt={user.fullName || "User"} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full bg-sky-600 text-white flex items-center justify-center font-bold text-xs">
                      {user.fullName ? user.fullName.charAt(0).toUpperCase() : <User className="w-3.5 h-3.5 text-white" />}
                    </div>
                  )}
                </div>
                <span className="text-xs font-bold max-w-[110px] truncate hidden md:inline-block">
                  {user.fullName || "Tài khoản"}
                </span>
              </div>
            ) : (
              <div className="flex items-center gap-1.5">
                <div className="w-7 h-7 rounded-full bg-slate-200/80 flex items-center justify-center shrink-0">
                  <User className="w-4 h-4 text-slate-600" />
                </div>
                <span className="text-xs font-bold hidden sm:inline-block">
                  {language === 'en' ? 'Account' : 'Tài khoản'}
                </span>
              </div>
            )}

            {/* Subtle Notification / Activity Indicator Dot */}
            {(unreadNotifs > 0 || cartItemCount > 0 || wishlistCount > 0) && (
              <span className="absolute -top-1 -right-1 flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-rose-600 ring-2 ring-white"></span>
              </span>
            )}
          </button>

          {/* Slide-Down Pop-up Menu */}
          {showAccountMenu && (
            <div className="absolute right-0 top-full mt-2 w-80 sm:w-96 bg-white rounded-2xl shadow-2xl border border-slate-200/90 z-50 overflow-hidden text-left animate-in fade-in slide-in-from-top-2 duration-200 text-slate-900">
              {accountMenuView === 'menu' ? (
                <div className="divide-y divide-slate-100">
                  {/* User Profile Header or Guest Greeting */}
                  {isAuthenticated && user ? (
                    <Link
                      to="/profile"
                      onClick={() => setShowAccountMenu(false)}
                      className="p-3.5 flex items-center justify-between hover:bg-slate-50/80 transition-colors group cursor-pointer"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-10 h-10 rounded-full overflow-hidden ring-1 ring-slate-200 shrink-0">
                          {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt={user.fullName || "User"} className="w-full h-full object-cover" />
                          ) : (
                            <div className="w-full h-full bg-slate-800 text-white flex items-center justify-center font-bold text-sm">
                              {user.fullName ? user.fullName.charAt(0).toUpperCase() : <User className="w-4 h-4 text-white" />}
                            </div>
                          )}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-bold text-slate-900 truncate group-hover:text-sky-600 transition-colors">
                            {user.fullName || "Khách hàng"}
                          </p>
                          <p className="text-xs text-slate-500 truncate">
                            {user.email || user.phone || "Thành viên DANASEA"}
                          </p>
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-slate-700 transition-colors shrink-0" />
                    </Link>
                  ) : (
                    <div className="p-4 bg-slate-50/50">
                      <p className="text-sm font-bold text-slate-900">
                        {language === 'en' ? 'Welcome to DANASEA' : 'Chào mừng bạn đến với DANASEA'}
                      </p>
                      <div className="mt-3">
                        <Link
                          to="/auth"
                          onClick={() => setShowAccountMenu(false)}
                          className="block w-full py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs text-center transition-colors shadow-xs"
                        >
                          {language === 'en' ? 'Sign In / Register' : 'Đăng nhập / Đăng ký'}
                        </Link>
                      </div>
                    </div>
                  )}

                  {/* Core Navigation Items (Disciplined Monochrome & Zero Subtitle Bloat) */}
                  <div className="p-1.5 space-y-0.5">
                    {/* Yêu thích */}
                    <Link
                      to="/profile/wishlist"
                      onClick={() => setShowAccountMenu(false)}
                      className="flex items-center justify-between px-3.5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors text-slate-700 hover:text-slate-900 group cursor-pointer"
                    >
                      <div className="flex items-center gap-3">
                        <Heart className="w-4 h-4 text-slate-500 group-hover:text-slate-900 transition-colors shrink-0" />
                        <span className="text-xs font-semibold">
                          {language === 'en' ? 'Wishlist' : 'Yêu thích'}
                        </span>
                      </div>
                      {wishlistCount > 0 ? (
                        <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-slate-100 text-slate-700 font-bold text-xs flex items-center justify-center tabular-nums">
                          {wishlistCount}
                        </span>
                      ) : (
                        <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500" />
                      )}
                    </Link>

                    {/* Giỏ hàng */}
                    <Link
                      to="/cart"
                      onClick={() => setShowAccountMenu(false)}
                      className="flex items-center justify-between px-3.5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors text-slate-700 hover:text-slate-900 group cursor-pointer"
                    >
                      <div className="flex items-center gap-3">
                        <ShoppingCart className="w-4 h-4 text-slate-500 group-hover:text-slate-900 transition-colors shrink-0" />
                        <span className="text-xs font-semibold">
                          {language === 'en' ? 'Cart' : 'Giỏ hàng'}
                        </span>
                      </div>
                      {cartItemCount > 0 ? (
                        <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-slate-100 text-slate-700 font-bold text-xs flex items-center justify-center tabular-nums">
                          {cartItemCount}
                        </span>
                      ) : (
                        <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500" />
                      )}
                    </Link>

                    {/* Vé đã đặt */}
                    <Link
                      to="/profile"
                      onClick={() => setShowAccountMenu(false)}
                      className="flex items-center justify-between px-3.5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors text-slate-700 hover:text-slate-900 group cursor-pointer"
                    >
                      <div className="flex items-center gap-3">
                        <CalendarCheck className="w-4 h-4 text-slate-500 group-hover:text-slate-900 transition-colors shrink-0" />
                        <span className="text-xs font-semibold">
                          {language === 'en' ? 'My Bookings' : 'Vé đã đặt'}
                        </span>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500" />
                    </Link>

                    {/* Thông báo */}
                    <button
                      type="button"
                      onClick={handleOpenNotifications}
                      className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl hover:bg-slate-50 transition-colors text-slate-700 hover:text-slate-900 group cursor-pointer text-left"
                    >
                      <div className="flex items-center gap-3">
                        <Bell className="w-4 h-4 text-slate-500 group-hover:text-slate-900 transition-colors shrink-0" />
                        <span className="text-xs font-semibold">
                          {language === 'en' ? 'Notifications' : 'Thông báo'}
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        {unreadNotifs > 0 && (
                          <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-slate-100 text-slate-700 font-bold text-xs flex items-center justify-center tabular-nums">
                            {unreadNotifs > 9 ? "9+" : unreadNotifs}
                          </span>
                        )}
                        <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500" />
                      </div>
                    </button>
                  </div>

                  {/* Language Switcher Row */}
                  <div className="px-4 py-2.5 flex items-center justify-between">
                    <div className="flex items-center gap-2 text-slate-600">
                      <Globe className="w-4 h-4" />
                      <span className="text-xs font-semibold">
                        {language === 'en' ? 'Language' : 'Ngôn ngữ'}
                      </span>
                    </div>
                    <div className="flex items-center p-0.5 rounded-lg bg-slate-100 border border-slate-200">
                      <button
                        type="button"
                        onClick={() => setLanguage('vi')}
                        className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all cursor-pointer ${
                          language === 'vi'
                            ? 'bg-white text-slate-900 shadow-xs'
                            : 'text-slate-500 hover:text-slate-800'
                        }`}
                      >
                        VI
                      </button>
                      <button
                        type="button"
                        onClick={() => setLanguage('en')}
                        className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all cursor-pointer ${
                          language === 'en'
                            ? 'bg-white text-slate-900 shadow-xs'
                            : 'text-slate-500 hover:text-slate-800'
                        }`}
                      >
                        EN
                      </button>
                    </div>
                  </div>

                  {/* Logout Row if Authenticated */}
                  {isAuthenticated && (
                    <div className="p-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          logout();
                          setShowAccountMenu(false);
                        }}
                        className="w-full flex items-center gap-2.5 px-3.5 py-2 rounded-xl text-slate-600 hover:text-rose-600 hover:bg-rose-50/60 transition-colors text-xs font-semibold cursor-pointer"
                      >
                        <LogOut className="w-4 h-4 shrink-0" />
                        <span>{language === 'en' ? 'Sign Out' : 'Đăng xuất'}</span>
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                /* Sub-view: Notifications List */
                <div>
                  <div className="p-3.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/80">
                    <button
                      type="button"
                      onClick={() => setAccountMenuView('menu')}
                      className="flex items-center gap-1 text-xs font-bold text-slate-700 hover:text-slate-900 cursor-pointer"
                    >
                      <ChevronLeft className="w-4 h-4" />
                      <span>{language === 'en' ? 'Back' : 'Quay lại'}</span>
                    </button>
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-xs text-slate-900">
                        {language === 'en' ? 'Notifications' : 'Thông báo'}
                      </span>
                      {unreadNotifs > 0 && (
                        <span className="px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-700 text-[10px] font-bold tabular-nums">
                          {unreadNotifs}
                        </span>
                      )}
                    </div>
                    {unreadNotifs > 0 ? (
                      <button
                        type="button"
                        onClick={handleMarkAllRead}
                        className="text-[11px] font-semibold text-slate-600 hover:text-slate-900 flex items-center gap-1 cursor-pointer"
                      >
                        <CheckCheck className="w-3 h-3 text-slate-500" />
                        <span>Đã đọc hết</span>
                      </button>
                    ) : <div className="w-12" />}
                  </div>

                  <div className="max-h-[360px] overflow-y-auto divide-y divide-slate-100">
                    {loadingNotifs ? (
                      <div className="p-8 text-center text-xs text-slate-500">
                        Đang tải thông báo...
                      </div>
                    ) : notifList.length > 0 ? (
                      notifList.map((n) => (
                        <div
                          key={n.id}
                          onClick={() => handleNotificationClick(n)}
                          className={`p-3.5 hover:bg-slate-50 transition-colors cursor-pointer flex items-start gap-3 ${
                            !n.isRead ? "bg-slate-50/70" : ""
                          }`}
                        >
                          <div className="w-8 h-8 rounded-xl bg-slate-100 flex items-center justify-center shrink-0 mt-0.5">
                            <Bell className="w-4 h-4 text-slate-600" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between gap-1">
                              <p className="text-xs font-bold text-slate-900 truncate">
                                {n.title}
                              </p>
                              {!n.isRead && (
                                <span className="w-2 h-2 rounded-full bg-sky-600 shrink-0"></span>
                              )}
                            </div>
                            <p className="text-xs text-slate-600 line-clamp-2 mt-0.5 leading-relaxed">
                              {n.body}
                            </p>
                            <div className="flex items-center gap-1 text-[10px] text-slate-400 mt-1">
                              <Clock className="w-3 h-3 text-slate-400" />
                              <span>{new Date(n.createdAt).toLocaleDateString('vi-VN')}</span>
                            </div>
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="p-8 text-center text-xs text-slate-500">
                        Không có thông báo hệ thống nào
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
