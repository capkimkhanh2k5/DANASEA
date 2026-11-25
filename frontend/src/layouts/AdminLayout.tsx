import { useEffect, useRef, useState } from 'react';
import { Outlet, useLocation, Navigate, Link, useNavigate } from 'react-router-dom';
import { FlaskConical, Menu, PanelLeftClose, PanelLeft, ShieldAlert, X } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { useAuthHydrated } from '../hooks/useAuthHydrated';
import { adminPages } from '../components/admin/adminRoutes';
import { AdminNavigation } from '../components/admin/AdminNavigation';
import { AdminAccount } from '../components/admin/AdminAccount';
import { evaluateAdminAccess } from '../components/admin/adminAuthGuard';
import { AdminForbiddenScreen } from '../components/admin/AdminForbiddenScreen';
import '../components/admin/admin.css';
import '../components/admin/adminInteractions.css';

export function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore(state => state.user);
  const token = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);

  // Hydration state check: do not prematurely redirect while Zustand persist is loading from localStorage
  const hydrated = useAuthHydrated();

  const [mobileOpen, setMobileOpen] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [isPinned, setIsPinned] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('danasea_admin_sidebar_pinned') === 'true';
    }
    return false;
  });

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('danasea_admin_sidebar_pinned', String(isPinned));
    }
  }, [isPinned]);

  const [days, setDays] = useState<7 | 30>(7);
  const sidebarRef = useRef<HTMLElement>(null);
  const menuRef = useRef<HTMLButtonElement>(null);

  function closeOverlays() {
    setMobileOpen(false);
  }

  useEffect(() => {
    if (!mobileOpen) return;
    const menuButton = menuRef.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusables = () => Array.from(sidebarRef.current?.querySelectorAll<HTMLElement>('a, button') ?? []).filter(el => el.getClientRects().length > 0 && !el.closest('[inert]'));
    focusables()[0]?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileOpen(false);
      if (event.key !== 'Tab') return;
      const targets = focusables();
      const first = targets[0];
      const last = targets.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    };
    const media = window.matchMedia('(min-width: 1024px)');
    const onResize = () => {
      if (media.matches) setMobileOpen(false);
    };
    document.addEventListener('keydown', onKey);
    media.addEventListener('change', onResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKey);
      media.removeEventListener('change', onResize);
      menuButton?.focus();
    };
  }, [mobileOpen]);

  const isDev = Boolean(import.meta.env.DEV);
  const demoParam = new URLSearchParams(location.search).get('demo') === '1';
  const accessResult = evaluateAdminAccess({
    isDev,
    demoParam,
    user,
    isAuthenticated,
    token,
  });

  // Client-side guard check (only in browser environment; in SSR test mode, render markup for nav testing)
  if (typeof window !== 'undefined') {
    if (!hydrated) {
      return (
        <div
          className="admin-shell"
          style={{
            display: 'grid',
            placeItems: 'center',
            minHeight: '100vh',
            background: '#f5f8fa',
          }}
        >
          <div style={{ textAlign: 'center', color: '#637582' }}>
            <p style={{ fontSize: 14, fontWeight: 550 }}>Đang khôi phục phiên làm việc…</p>
          </div>
        </div>
      );
    }

    if (!accessResult.allowed) {
      if (accessResult.reason === 'unauthenticated') {
        return (
          <Navigate
            to="/auth"
            state={{ from: location.pathname + location.search }}
            replace
          />
        );
      }
      return <AdminForbiddenScreen user={user} />;
    }
  }

  const demo = accessResult.allowed && accessResult.reason === 'demo';
  const page = adminPages.find(item => item.to === location.pathname) ?? adminPages[0];
  const isExpanded = mobileOpen || isPinned || isHovered;
  const isCopilot = location.pathname.startsWith('/admin/copilot');

  return (
    <div className={`admin-shell flex min-h-screen bg-[#f5f8fa] text-slate-800 ${isPinned ? 'admin-shell--pinned' : ''}`}>
      <a href="#admin-content" className="admin-skip-link">Đến nội dung chính</a>
      {mobileOpen && (
        <button
          className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-40 lg:hidden cursor-pointer"
          aria-label="Đóng menu"
          onClick={() => setMobileOpen(false)}
          tabIndex={-1}
        />
      )}

      {/* Anti-jitter spacer so main content does not jump on hover */}
      <div
        className="hidden lg:block shrink-0 transition-all duration-200 ease-in-out print:hidden"
        style={{ width: isPinned ? 260 : 68 }}
      />

      {/* Modern Collapsible Sidebar (Compact Rail -> Hover Expand / Pin) */}
      <aside
        id="admin-navigation"
        ref={sidebarRef}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        className={`fixed top-0 bottom-0 left-0 z-40 bg-white border-r border-slate-200/80 flex flex-col justify-between select-none print:hidden transition-[width,box-shadow] duration-200 ease-out ${
          mobileOpen
            ? 'shadow-2xl shadow-slate-900/20 translate-x-0'
            : isExpanded
            ? 'shadow-2xl shadow-slate-900/10'
            : 'shadow-none'
        } ${
          !mobileOpen ? 'max-lg:-translate-x-full lg:translate-x-0' : ''
        }`}
        style={{
          width: mobileOpen || isExpanded ? 260 : 68,
        }}
        aria-label="Điều hướng quản trị"
        role={mobileOpen ? 'dialog' : undefined}
        aria-modal={mobileOpen || undefined}
      >
        <div className="flex flex-col min-h-0 flex-1">
          {/* Brand Header */}
          <div className="h-16 px-3.5 border-b border-slate-200/80 flex items-center justify-between shrink-0 overflow-hidden">
            <div
              className="flex items-center gap-3 cursor-pointer shrink-0"
              onClick={() => {
                navigate('/admin');
                closeOverlays();
              }}
              title="DANASEA Hệ thống quản trị"
            >
              <img
                src="/images/danasea-logo-sm.png"
                alt="DANASEA Logo"
                className="w-9 h-9 object-contain rounded-xl shrink-0 shadow-xs"
              />
              <div
                className={`flex flex-col transition-all duration-200 ${
                  isExpanded ? 'opacity-100 max-w-[150px]' : 'opacity-0 max-w-0 overflow-hidden'
                }`}
              >
                <span className="font-extrabold text-base tracking-tight text-slate-900 leading-none">
                  DANASEA
                </span>
                <span className="text-[10px] text-[#0B5D6F] font-bold tracking-wider uppercase mt-1 whitespace-nowrap">
                  QUẢN TRỊ VIÊN
                </span>
              </div>
            </div>

            {/* Desktop Sidebar Toggle Button (Gemini Style) */}
            {isExpanded && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setIsPinned((prev) => !prev);
                }}
                className="hidden lg:flex p-1.5 rounded-lg text-slate-500 hover:text-slate-800 bg-transparent hover:bg-slate-100 transition-colors cursor-pointer shrink-0"
                title={isPinned ? 'Thu gọn thanh bên (Tự động ẩn)' : 'Ghim cố định thanh bên'}
                aria-label={isPinned ? 'Thu gọn thanh bên' : 'Ghim cố định thanh bên'}
              >
                {isPinned ? (
                  <PanelLeftClose className="w-4 h-4 transition-transform duration-200" />
                ) : (
                  <PanelLeft className="w-4 h-4 transition-transform duration-200" />
                )}
              </button>
            )}

            {/* Mobile Close Button */}
            {mobileOpen && (
              <button
                type="button"
                className="lg:hidden p-1.5 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition-colors cursor-pointer"
                onClick={() => setMobileOpen(false)}
                aria-label="Đóng menu điều hướng"
              >
                <X size={20} />
              </button>
            )}
          </div>

          {/* Navigation Links Grouped */}
          <AdminNavigation isExpanded={isExpanded} onNavigate={closeOverlays} />
        </div>

        {/* Footer Account Section */}
        <div className="p-3 border-t border-slate-200/80 flex flex-col gap-2 shrink-0">
          <AdminAccount
            user={user}
            token={token}
            demo={demo}
            isExpanded={isExpanded}
            onNavigate={closeOverlays}
          />
        </div>
      </aside>

      {/* WORKSPACE AREA - NO TOPBAR */}
      <div
        className={`admin-workspace flex-1 flex flex-col min-w-0 ${isCopilot ? 'admin-workspace--copilot' : ''}`}
        style={{ marginLeft: 0 }}
        inert={mobileOpen || undefined}
      >
        {!demo && user && user.role !== 'ADMIN' && (
          <div className="admin-role-context" role="status">
            <ShieldAlert size={17} />
            <span>Phiên hiện tại mang vai trò <strong>{user.role === 'VENDOR' ? 'Đối tác (Vendor)' : 'Khách hàng'}</strong>. Dữ liệu Admin vẫn yêu cầu quyền do Backend kiểm tra.</span>
          </div>
        )}
        <div id="admin-content" className={`admin-content ${isCopilot ? 'admin-content--copilot' : ''}`} tabIndex={-1}>
          <header className={`admin-main-header ${isCopilot ? 'admin-main-header--copilot' : ''}`}>
            <div className="admin-main-header-row">
              <div className="admin-main-header-left">
                <button
                  ref={menuRef}
                  className="admin-icon-button admin-mobile-only"
                  aria-label="Mở menu điều hướng"
                  aria-controls="admin-navigation"
                  aria-expanded={mobileOpen}
                  onClick={() => setMobileOpen(true)}
                >
                  <Menu size={20} />
                </button>
                <div>
                  <h1 className="admin-main-page-title">
                    {location.pathname === '/admin' || location.pathname === '/admin/'
                      ? 'Dashboard'
                      : (page as { pageTitle?: string; label: string }).pageTitle || page.label}
                  </h1>
                </div>
              </div>

              <div className="admin-main-header-right">
                {demo ? (
                  <div className="admin-demo-toggle-cluster">
                    <span
                      className="admin-demo-pill"
                      title="Dữ liệu mẫu độc lập. Thao tác chỉ thay đổi bản xem thử, không ảnh hưởng dữ liệu thật."
                      aria-label="Chế độ Demo Mode: Bật"
                    >
                      <span className="admin-demo-pulse-dot" />
                      DEMO MODE: Bật
                    </span>
                    <Link
                      to={location.pathname}
                      className="admin-demo-toggle-btn admin-demo-toggle-btn--off"
                      title="Tắt chế độ xem trước, chuyển về dữ liệu hệ thống"
                    >
                      Tắt
                    </Link>
                  </div>
                ) : (
                  isDev && (
                    <Link
                      to={`${location.pathname}?demo=1`}
                      className="admin-demo-toggle-btn admin-demo-toggle-btn--on"
                      title="Bật chế độ xem trước với dữ liệu mẫu độc lập"
                    >
                      <FlaskConical size={14} aria-hidden="true" />
                      Bật dữ liệu mẫu
                    </Link>
                  )
                )}

                {(location.pathname === '/admin' || location.pathname === '/admin/') && (
                  <div className="admin-time-segmented" role="group" aria-label="Kỳ thống kê">
                    <button
                      type="button"
                      aria-pressed={days === 7}
                      onClick={() => setDays(7)}
                    >
                      7 ngày qua
                    </button>
                    <button
                      type="button"
                      aria-pressed={days === 30}
                      onClick={() => setDays(30)}
                    >
                      30 ngày qua
                    </button>
                  </div>
                )}
              </div>
            </div>
          </header>
          <Outlet context={{ days, setDays }} />
        </div>
      </div>
    </div>
  );
}
