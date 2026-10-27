import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/useAuthStore';
import { useLoadingStore } from '../../store/useLoadingStore';
import { authService } from '../../services/authService';
import type { Role } from '../../types';

export function DevRoleSwitcher() {
  const [isOpen, setIsOpen] = useState(false);
  const { user, switchRole, logout } = useAuthStore();
  const { triggerDemoLoading } = useLoadingStore();
  const navigate = useNavigate();
  const location = useLocation();
  const menuRef = useRef<HTMLDivElement>(null);

  // Close when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const currentRole: Role = user?.role || 'CUSTOMER';

  const roleConfig: Record<Role, {
    label: string;
    shortLabel: string;
    icon: string;
    accountKey: 'customer' | 'vendor' | 'admin';
    targetPath: string;
    color: string;
    badge: string;
    desc: string;
  }> = {
    CUSTOMER: {
      label: 'Khách hàng',
      shortLabel: 'Du khách',
      icon: 'person',
      accountKey: 'customer',
      targetPath: '/',
      color: 'bg-primary text-on-primary',
      badge: 'bg-emerald-500 text-white',
      desc: 'Đặt tour, giỏ hàng, QR vé, wishlist, hồ sơ du khách',
    },
    VENDOR: {
      label: 'Đối tác Vendor',
      shortLabel: 'Vendor',
      icon: 'sailing',
      accountKey: 'vendor',
      targetPath: '/vendor',
      color: 'bg-secondary text-on-secondary',
      badge: 'bg-amber-500 text-white',
      desc: 'Quản lý tour lặn & cano, kiểm soát vé QR, doanh thu, lịch trình',
    },
    ADMIN: {
      label: 'Quản trị viên',
      shortLabel: 'Admin',
      icon: 'admin_panel_settings',
      accountKey: 'admin',
      targetPath: '/admin',
      color: 'bg-primary-container text-on-primary',
      badge: 'bg-indigo-600 text-white',
      desc: 'Duyệt hồ sơ vendor, duyệt tour mới, quản lý hoàn tiền, hệ thống',
    },
  };

  const handleSelectRole = async (key: 'customer' | 'vendor' | 'admin', path: string) => {
    const targetName = key === 'admin' ? 'Cổng Quản Trị Viên' : key === 'vendor' ? 'Cổng Đối Tác Vendor' : 'Cổng Trải Nghiệm Du Khách';
    triggerDemoLoading(850, `Đang kết nối ${targetName}...`, 'Tải dữ liệu phân hệ và cấu hình quyền hạn');

    const creds = {
      customer: { email: 'customer@danasea.vn', password: 'password123' },
      vendor: { email: 'vendor@danasea.vn', password: 'password123' },
      admin: { email: 'admin@danasea.vn', password: 'password123' },
    }[key];

    try {
      const res = await authService.login(creds);
      useAuthStore.getState().setAuth(
        {
          id: res.userId,
          email: res.email,
          fullName: res.role === 'ADMIN' ? 'Quản Trị Viên Danasea' : res.role === 'VENDOR' ? 'Công Ty Biển Xanh Danasea' : 'Nguyễn Văn Khách',
          role: res.role,
          avatarUrl: '',
          isEmailVerified: true,
          isLocked: false,
          passwordHash: '',
          phone: '',
          locale: 'vi',
        },
        res.accessToken
      );
    } catch (err) {
      console.warn('[DevRoleSwitcher] Không thể đăng nhập API thật, fallback mock:', err);
      switchRole(key);
    }

    setIsOpen(false);
    navigate(path);
  };

  const handleLogout = () => {
    logout();
    setIsOpen(false);
    navigate('/auth');
  };

  const activeMeta = roleConfig[currentRole] || roleConfig.CUSTOMER;

  return (
    <div ref={menuRef} className="fixed bottom-5 left-5 z-[9999] font-sans">
      {/* Floating Trigger Pill */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="group flex items-center gap-2 px-3.5 py-2 rounded-full bg-surface-container-lowest/95 backdrop-blur-md border border-outline-variant/40 shadow-xl hover:shadow-2xl transition-all duration-200 hover:scale-105 cursor-pointer text-left"
        title="Chuyển đổi phân hệ kiểm tra (Dev Role Switcher)"
      >
        <span className={`w-2.5 h-2.5 rounded-full animate-pulse ${
          currentRole === 'ADMIN' ? 'bg-indigo-500' : currentRole === 'VENDOR' ? 'bg-amber-500' : 'bg-emerald-500'
        }`} />
        
        <div className="flex items-center gap-1.5 text-xs font-bold text-on-surface">
          <span className="material-symbols-outlined text-[16px] text-primary-container">
            {activeMeta.icon}
          </span>
          <span>{activeMeta.shortLabel}</span>
        </div>

        <span className="px-1.5 py-0.5 rounded-md bg-surface-container-high text-[10px] font-semibold text-on-surface-variant uppercase tracking-wider">
          Phân hệ
        </span>

        <span className={`material-symbols-outlined text-[16px] text-outline transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}>
          expand_less
        </span>
      </button>

      {/* Popover Panel */}
      {isOpen && (
        <div className="absolute bottom-12 left-0 w-84 sm:w-96 rounded-2xl bg-surface-container-lowest/98 backdrop-blur-xl border border-outline-variant/40 shadow-2xl p-4 space-y-3 animate-in fade-in zoom-in-95 duration-150">
          {/* Header */}
          <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg bg-primary-container/10 flex items-center justify-center text-primary-container">
                <span className="material-symbols-outlined text-[18px]">swap_horiz</span>
              </div>
              <div>
                <h4 className="font-headline-sm text-xs font-bold text-on-surface uppercase tracking-wide">
                  Chuyển nhanh phân hệ
                </h4>
                <p className="font-label-sm text-[10px] text-outline">
                  Mật khẩu mặc định: <span className="font-mono font-bold text-on-surface">password123</span>
                </p>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="w-6 h-6 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center cursor-pointer transition-colors"
            >
              <span className="material-symbols-outlined text-[16px]">close</span>
            </button>
          </div>

          {/* Current Active Info */}
          <div className="flex items-center justify-between px-2.5 py-1.5 rounded-xl bg-surface-container-low text-xs">
            <span className="text-outline">Đang đăng nhập:</span>
            <span className="font-bold text-on-surface truncate max-w-[180px]">
              {user?.email || 'Chưa đăng nhập'}
            </span>
          </div>

          {/* Role Options */}
          <div className="space-y-1.5">
            {/* Customer Option */}
            <button
              type="button"
              onClick={() => handleSelectRole('customer', '/')}
              className={`w-full p-2.5 rounded-xl border text-left transition-all cursor-pointer flex items-start gap-3 group ${
                currentRole === 'CUSTOMER'
                  ? 'bg-primary/5 border-primary shadow-sm'
                  : 'bg-surface-container-lowest hover:bg-surface-container-low border-outline-variant/30'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-600 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span className="material-symbols-outlined text-[18px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                  person
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-on-surface">1. Khách hàng (Du khách)</span>
                  {currentRole === 'CUSTOMER' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-500 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-outline font-mono mt-0.5">customer@danasea.vn</p>
                <p className="text-[10px] text-on-surface-variant truncate mt-1">
                  {roleConfig.CUSTOMER.desc}
                </p>
              </div>
            </button>

            {/* Vendor Option */}
            <button
              type="button"
              onClick={() => handleSelectRole('vendor', '/vendor')}
              className={`w-full p-2.5 rounded-xl border text-left transition-all cursor-pointer flex items-start gap-3 group ${
                currentRole === 'VENDOR'
                  ? 'bg-amber-500/5 border-amber-500 shadow-sm'
                  : 'bg-surface-container-lowest hover:bg-surface-container-low border-outline-variant/30'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-600 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span className="material-symbols-outlined text-[18px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                  sailing
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-on-surface">2. Đối tác (Vendor Portal)</span>
                  {currentRole === 'VENDOR' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-500 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-outline font-mono mt-0.5">vendor@danasea.vn</p>
                <p className="text-[10px] text-on-surface-variant truncate mt-1">
                  {roleConfig.VENDOR.desc}
                </p>
              </div>
            </button>

            {/* Admin Option */}
            <button
              type="button"
              onClick={() => handleSelectRole('admin', '/admin')}
              className={`w-full p-2.5 rounded-xl border text-left transition-all cursor-pointer flex items-start gap-3 group ${
                currentRole === 'ADMIN'
                  ? 'bg-primary-container/10 border-primary-container shadow-sm'
                  : 'bg-surface-container-lowest hover:bg-surface-container-low border-outline-variant/30'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-indigo-500/10 text-indigo-600 flex items-center justify-center flex-shrink-0 mt-0.5">
                <span className="material-symbols-outlined text-[18px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                  admin_panel_settings
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-on-surface">3. Quản trị viên (Admin Hub)</span>
                  {currentRole === 'ADMIN' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-indigo-600 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-outline font-mono mt-0.5">admin@danasea.vn</p>
                <p className="text-[10px] text-on-surface-variant truncate mt-1">
                  {roleConfig.ADMIN.desc}
                </p>
              </div>
            </button>
          </div>

          {/* Test Loading Animation Action */}
          <button
            type="button"
            onClick={() => {
              triggerDemoLoading(3000, "Đang kết nối sóng biển DANASEA...", "Chuẩn bị hải trình và cập nhật dữ liệu trải nghiệm");
              setIsOpen(false);
            }}
            className="w-full py-2 px-3 rounded-xl bg-gradient-to-r from-primary/10 via-secondary/10 to-primary-container/10 border border-primary/20 hover:border-primary/40 text-primary flex items-center justify-center gap-2 text-xs font-bold transition-all hover:shadow-sm cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px] text-secondary">sailing</span>
            <span>Xem thử hiệu ứng Sóng nước (Liquid Wave Loader)</span>
          </button>

          {/* Footer Actions */}
          <div className="pt-2 border-t border-outline-variant/30 flex items-center justify-between text-xs">
            <span className="text-[11px] text-outline">
              Đường dẫn hiện tại: <span className="font-mono text-on-surface">{location.pathname}</span>
            </span>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1 text-[11px] font-semibold text-error hover:text-error/80 cursor-pointer"
            >
              <span className="material-symbols-outlined text-[14px]">logout</span>
              Đăng xuất
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
