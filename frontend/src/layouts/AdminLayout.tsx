import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate, Navigate } from 'react-router-dom';
import { ArrowUpRight, Bell, ChevronDown, ChevronRight, Menu, PanelLeftClose, Search, ShieldAlert, X } from 'lucide-react';
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
  const navigate = useNavigate();
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
  const [query, setQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const notificationRef = useRef<HTMLDivElement>(null);
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

  function closeOverlays() { setMobileOpen(false); setSearchOpen(false); setNotificationsOpen(false); }

  useEffect(() => {
    const onPointer = (event: PointerEvent) => {
      if (!searchRef.current?.contains(event.target as Node)) setSearchOpen(false);
      if (!notificationRef.current?.contains(event.target as Node)) setNotificationsOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); inputRef.current?.focus(); setSearchOpen(true); }
      if (event.key === 'Escape') { setSearchOpen(false); setNotificationsOpen(false); }
    };
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('pointerdown', onPointer); document.removeEventListener('keydown', onKey); };
  }, []);

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
      const first = targets[0]; const last = targets.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const media = window.matchMedia('(min-width: 1024px)');
    const onResize = () => { if (media.matches) setMobileOpen(false); };
    document.addEventListener('keydown', onKey);
    media.addEventListener('change', onResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKey); media.removeEventListener('change', onResize);
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
  const adminHref = (path: string) => `${path}${demo ? '?demo=1' : ''}`;
  const page = adminPages.find(item => item.to === location.pathname) ?? adminPages[0];
  const searchResults = adminPages.filter(item => `${item.label} ${item.group}`.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi')));
  const initials = (demo ? 'Admin xem thử' : user?.fullName || 'Chưa đăng nhập').split(' ').filter(Boolean).slice(-2).map(word => word[0]).join('').toUpperCase();

  return <div className={`admin-shell ${collapsed ? 'admin-shell--collapsed' : ''} ${mobileOpen ? 'admin-shell--mobile-open' : ''}`}>
    <a href="#admin-content" className="admin-skip-link">Đến nội dung chính</a>
    {mobileOpen && <button className="admin-backdrop" aria-label="Đóng menu" onClick={() => setMobileOpen(false)} tabIndex={-1} />}
    <aside id="admin-navigation" ref={sidebarRef} className="admin-sidebar" aria-label="Điều hướng quản trị" role={mobileOpen ? 'dialog' : undefined} aria-modal={mobileOpen || undefined}>
      <div className="admin-brand-row">
        <Link to={adminHref('/admin')} className="admin-brand" onClick={closeOverlays} aria-label="DANASEA Admin — Tổng quan">
          <span className="admin-brand-mark">
            <img src="/images/danasea-logo.png" alt="DANASEA Logo" className="admin-brand-logo" />
          </span>
          <span className="admin-brand-copy"><strong>DANASEA<span>ADMIN</span></strong><small>Marine Experience Platform</small></span>
        </Link>
        <button className="admin-icon-button admin-mobile-only" onClick={() => setMobileOpen(false)} aria-label="Đóng menu điều hướng"><X size={20} /></button>
      </div>
      <AdminNavigation collapsed={collapsed && !mobileOpen} onNavigate={closeOverlays} />
      <div className="admin-sidebar-bottom">
        <AdminAccount user={user} token={token} demo={demo} onNavigate={closeOverlays} />
      </div>
    </aside>
    <div className="admin-workspace" inert={mobileOpen || undefined}>
      <header className="admin-topbar">
        <div className="admin-topbar-context">
          <button ref={menuRef} className="admin-icon-button admin-mobile-only" aria-label="Mở menu điều hướng" aria-controls="admin-navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)}><Menu size={21} /></button>
          <button
            className="admin-icon-button admin-desktop-only"
            onClick={() => setCollapsed(value => !value)}
            aria-label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'}
            title={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'}
            aria-expanded={!collapsed}
            aria-controls="admin-navigation"
          >
            <span
              className="admin-collapse-toggle-icon"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'transform 280ms cubic-bezier(0.4, 0, 0.2, 1)',
                transform: collapsed ? 'rotate(180deg)' : 'rotate(0deg)',
              }}
            >
              <PanelLeftClose size={20} />
            </span>
          </button>
          <h1 className="admin-topbar-title">{(page as { pageTitle?: string; label: string }).pageTitle || page.label}</h1>
        </div>
        <div className="admin-topbar-tools">
          <div
            ref={searchRef}
            className="admin-search"
            onClick={(event) => {
              if ((event.target as HTMLElement).closest('.admin-search-results')) return;
              inputRef.current?.focus();
              setSearchOpen(true);
            }}
          >
            <Search size={17} aria-hidden="true" />
            <input
              ref={inputRef}
              role="combobox"
              aria-label="Tìm trang quản trị"
              aria-expanded={searchOpen}
              aria-controls="admin-search-results"
              aria-autocomplete="list"
              aria-activedescendant={searchOpen && searchResults[highlight] ? `admin-search-${highlight}` : undefined}
              value={query}
              placeholder="Tìm trang quản trị…"
              onFocus={() => setSearchOpen(true)}
              onClick={() => setSearchOpen(true)}
              onChange={event => { setQuery(event.target.value); setHighlight(0); setSearchOpen(true); }}
              onKeyDown={event => {
                if (event.key === 'ArrowDown') { event.preventDefault(); setHighlight(value => Math.max(0, Math.min(value + 1, searchResults.length - 1))); }
                if (event.key === 'ArrowUp') { event.preventDefault(); setHighlight(value => Math.max(value - 1, 0)); }
                if (event.key === 'Enter' && searchResults[highlight]) {
                  navigate(adminHref(searchResults[highlight].to));
                  closeOverlays();
                  inputRef.current?.blur();
                  setQuery('');
                }
              }}
            />
            <kbd>⌘ K</kbd>
            {searchOpen && (
              <div
                className="admin-search-results"
                id="admin-search-results"
                role="listbox"
                aria-label="Trang quản trị"
                onClick={e => e.stopPropagation()}
              >
                {searchResults.length === 0 && <p>Không tìm thấy trang phù hợp.</p>}
                {searchResults.map((item, index) => (
                  <Link
                    role="option"
                    aria-selected={highlight === index}
                    id={`admin-search-${index}`}
                    key={item.to}
                    to={adminHref(item.to)}
                    onClick={() => {
                      closeOverlays();
                      inputRef.current?.blur();
                      setQuery('');
                    }}
                  >
                    <item.icon size={17} /><span>{item.label}<small>{item.group}</small></span><ChevronRight size={14} />
                  </Link>
                ))}
              </div>
            )}
          </div>
          <div ref={notificationRef} className="admin-notification">
            <button className={`admin-icon-button ${notificationsOpen ? 'is-active' : ''}`} aria-label="Thông báo và công việc" aria-expanded={notificationsOpen} aria-controls="admin-notification-menu" onClick={() => setNotificationsOpen(value => !value)}><Bell size={20} /></button>
            {notificationsOpen && <div className="admin-notification-menu" id="admin-notification-menu"><strong>Công việc vận hành</strong><p>Theo dõi các mục đang chờ tại bảng điều khiển.</p>
              {[['/admin', 'Mở hàng đợi ưu tiên'], ['/admin/vendor-approval', 'Hồ sơ đối tác'], ['/admin/disputes', 'Khiếu nại & tranh chấp'], ['/admin/payouts', 'Đối soát & quyết toán']].map(([to, label]) => <Link key={to} to={adminHref(to)} onClick={closeOverlays}>{label}<ArrowUpRight size={15} /></Link>)}
            </div>}
          </div>
          <span className="admin-topbar-divider" />
          <Link to={adminHref('/admin/users')} className="admin-topbar-account" aria-label="Quản lý người dùng"><span className="admin-avatar">{initials}</span><ChevronDown size={14} /></Link>
        </div>
      </header>
      {!demo && user && user.role !== 'ADMIN' && <div className="admin-role-context" role="status"><ShieldAlert size={17} /><span>Phiên hiện tại mang vai trò <strong>{user.role === 'VENDOR' ? 'Đối tác (Vendor)' : 'Khách hàng'}</strong>. Dữ liệu Admin vẫn yêu cầu quyền do Backend kiểm tra.</span></div>}
      <div id="admin-content" className="admin-content" tabIndex={-1}><Outlet /></div>
    </div>
  </div>;
}
