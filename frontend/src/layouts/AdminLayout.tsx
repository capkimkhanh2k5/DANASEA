import { useEffect, useRef, useState } from 'react';
import { Outlet, useLocation, Navigate, Link } from 'react-router-dom';
import { FlaskConical, Menu, PanelLeftClose, ShieldAlert, X } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminPages } from '../components/admin/adminRoutes';
import { AdminNavigation } from '../components/admin/AdminNavigation';
import { AdminAccount } from '../components/admin/AdminAccount';
import { evaluateAdminAccess } from '../components/admin/adminAuthGuard';
import { AdminForbiddenScreen } from '../components/admin/AdminForbiddenScreen';
import '../components/admin/admin.css';
import '../components/admin/adminInteractions.css';

export function AdminLayout() {
  const location = useLocation();
  const user = useAuthStore(state => state.user);
  const token = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);

  // Hydration state check: do not prematurely redirect while Zustand persist is loading from localStorage
  const [hydrated, setHydrated] = useState(() => {
    if (typeof window === 'undefined') return true;
    return useAuthStore.persist?.hasHydrated?.() ?? true;
  });

  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [days, setDays] = useState<7 | 30>(7);
  const sidebarRef = useRef<HTMLElement>(null);
  const menuRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const unsub = useAuthStore.persist?.onFinishHydration?.(() => {
      setHydrated(true);
    });
    return () => {
      unsub?.();
    };
  }, []);

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

  return (
    <div className={`admin-shell ${collapsed ? 'admin-shell--collapsed' : ''} ${mobileOpen ? 'admin-shell--mobile-open' : ''}`}>
      <a href="#admin-content" className="admin-skip-link">Đến nội dung chính</a>
      {mobileOpen && <button className="admin-backdrop" aria-label="Đóng menu" onClick={() => setMobileOpen(false)} tabIndex={-1} />}

      {/* GEMINI-STYLE SIDEBAR */}
      <aside
        id="admin-navigation"
        ref={sidebarRef}
        className="admin-sidebar"
        aria-label="Điều hướng quản trị"
        role={mobileOpen ? 'dialog' : undefined}
        aria-modal={mobileOpen || undefined}
      >
        <div className="admin-brand-row">
          {collapsed && !mobileOpen ? (
            /* Khi thu gọn: hiển thị logo ở đầu thanh bên, bấm vào logo thì mở rộng ra lại */
            <button
              type="button"
              className="admin-brand-collapsed-btn admin-desktop-only"
              onClick={() => setCollapsed(false)}
              aria-label="Mở rộng thanh bên"
              title="Mở rộng thanh bên"
            >
              <span className="admin-brand-mark">
                <img src="/images/danasea-logo.png" alt="DANASEA Logo" className="admin-brand-logo" />
              </span>
            </button>
          ) : (
            /* Khi mở rộng: logo và tên thương hiệu là tĩnh (không bấm chuyển trang), nút đóng ở bên phải */
            <>
              <div className="admin-brand-container">
                <div className="admin-brand admin-brand--static" aria-label="DANASEA Admin — Hệ thống quản trị">
                  <span className="admin-brand-mark">
                    <img src="/images/danasea-logo.png" alt="DANASEA Logo" className="admin-brand-logo" />
                  </span>
                  <span className="admin-brand-copy">
                    <strong>DANASEA</strong>
                    <small>Marine Experience Platform</small>
                  </span>
                </div>
              </div>
              <button
                type="button"
                className="admin-sidebar-toggle-btn admin-desktop-only"
                onClick={() => setCollapsed(true)}
                aria-label="Đóng thanh bên"
                title="Đóng thanh bên"
              >
                <PanelLeftClose size={20} />
              </button>
            </>
          )}
          <button
            type="button"
            className="admin-icon-button admin-mobile-only"
            onClick={() => setMobileOpen(false)}
            aria-label="Đóng menu điều hướng"
          >
            <X size={20} />
          </button>
        </div>

        <AdminNavigation collapsed={collapsed && !mobileOpen} onNavigate={closeOverlays} />

        <div className="admin-sidebar-bottom">
          <AdminAccount
            user={user}
            token={token}
            demo={demo}
            collapsed={collapsed && !mobileOpen}
            onNavigate={closeOverlays}
          />
        </div>
      </aside>

      {/* WORKSPACE AREA - NO TOPBAR */}
      <div className="admin-workspace" inert={mobileOpen || undefined}>
        {!demo && user && user.role !== 'ADMIN' && (
          <div className="admin-role-context" role="status">
            <ShieldAlert size={17} />
            <span>Phiên hiện tại mang vai trò <strong>{user.role === 'VENDOR' ? 'Đối tác (Vendor)' : 'Khách hàng'}</strong>. Dữ liệu Admin vẫn yêu cầu quyền do Backend kiểm tra.</span>
          </div>
        )}
        <div id="admin-content" className="admin-content" tabIndex={-1}>
          <header className="admin-main-header">
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
                  {(location.pathname === '/admin' || location.pathname === '/admin/') && (
                    <p className="admin-main-page-sub">Tổng quan vận hành</p>
                  )}
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
