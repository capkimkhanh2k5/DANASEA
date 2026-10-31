import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { User, Ship, ShieldAlert, ArrowLeftRight, X, ChevronUp, LogOut } from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { authService } from '../../services/authService';
import type { Role } from '../../types';

export function DevRoleSwitcher() {
  const [isOpen, setIsOpen] = useState(false);
  const { user, switchRole, logout } = useAuthStore();
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
    accountKey: 'customer' | 'vendor' | 'admin';
    targetPath: string;
    color: string;
    badge: string;
    desc: string;
  }> = {
    CUSTOMER: {
      label: 'Khách hàng',
      shortLabel: 'Du khách',
      accountKey: 'customer',
      targetPath: '/',
      color: 'bg-primary text-on-primary',
      badge: 'bg-emerald-500 text-white',
      desc: 'Đặt tour, giỏ hàng, QR vé, wishlist, hồ sơ du khách',
    },
    VENDOR: {
      label: 'Đối tác Vendor',
      shortLabel: 'Vendor',
      accountKey: 'vendor',
      targetPath: '/vendor',
      color: 'bg-secondary text-on-secondary',
      badge: 'bg-amber-500 text-white',
      desc: 'Quản lý tour lặn & cano, kiểm soát vé QR, doanh thu, lịch trình',
    },
    ADMIN: {
      label: 'Quản trị viên',
      shortLabel: 'Admin',
      accountKey: 'admin',
      targetPath: '/admin',
      color: 'bg-primary-container text-on-primary',
      badge: 'bg-sky-600 text-white',
      desc: 'Duyệt hồ sơ vendor, duyệt tour mới, quản lý hoàn tiền, hệ thống',
    },
  };

  const handleSelectRole = async (key: 'customer' | 'vendor' | 'admin', path: string) => {
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
    <div ref={menuRef} data-ui="dev-role-switcher" className="hidden sm:block fixed bottom-5 left-5 z-[9999] font-sans">
      {/* Floating Trigger Pill */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="group flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white/95 border border-slate-300 transition-all duration-200 hover:-translate-y-0.5 cursor-pointer text-left"
        title="Chuyển đổi phân hệ kiểm tra (Dev Role Switcher)"
      >
        <span className={`w-2.5 h-2.5 rounded-full ${
          currentRole === 'ADMIN' ? 'bg-sky-600' : currentRole === 'VENDOR' ? 'bg-amber-500' : 'bg-emerald-500'
        }`} />
        
        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
          {currentRole === 'ADMIN' ? (
            <ShieldAlert className="w-4 h-4 text-sky-600 shrink-0" />
          ) : currentRole === 'VENDOR' ? (
            <Ship className="w-4 h-4 text-amber-600 shrink-0" />
          ) : (
            <User className="w-4 h-4 text-emerald-600 shrink-0" />
          )}
          <span>{activeMeta.shortLabel}</span>
        </div>

        <span className="px-1.5 py-0.5 rounded-md bg-slate-100 text-[10px] font-semibold text-slate-600 uppercase tracking-wider">
          Phân hệ
        </span>

        <ChevronUp className={`w-4 h-4 text-slate-500 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {/* Popover Panel */}
      {isOpen && (
        <div className="absolute bottom-12 left-0 w-84 sm:w-96 rounded-2xl bg-white border border-slate-300 p-4 space-y-3">
          {/* Header */}
          <div className="flex items-center justify-between pb-2 border-b border-slate-200">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg bg-sky-50 flex items-center justify-center text-sky-600">
                <ArrowLeftRight className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
                  Chuyển nhanh phân hệ
                </h4>
                <p className="text-[11px] text-slate-500">
                  Mật khẩu mặc định: <span className="font-semibold text-slate-800">password123</span>
                </p>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="w-6 h-6 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center cursor-pointer transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Current Active Info */}
          <div className="flex items-center justify-between px-2.5 py-1.5 rounded-xl bg-slate-50 text-xs">
            <span className="text-slate-500">Đang đăng nhập:</span>
            <span className="font-bold text-slate-800 truncate max-w-[180px]">
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
                  ? 'bg-emerald-50 border-emerald-500'
                  : 'bg-white hover:bg-slate-50 border-slate-200'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center flex-shrink-0 mt-0.5">
                <User className="w-4 h-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-slate-900">1. Khách hàng (Du khách)</span>
                  {currentRole === 'CUSTOMER' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-600 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5">customer@danasea.vn</p>
                <p className="text-[11px] text-slate-600 truncate mt-1">
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
                  ? 'bg-amber-50 border-amber-500'
                  : 'bg-white hover:bg-slate-50 border-slate-200'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center flex-shrink-0 mt-0.5">
                <Ship className="w-4 h-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-slate-900">2. Đối tác (Vendor Portal)</span>
                  {currentRole === 'VENDOR' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-600 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5">vendor@danasea.vn</p>
                <p className="text-[11px] text-slate-600 truncate mt-1">
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
                  ? 'bg-sky-50 border-sky-500'
                  : 'bg-white hover:bg-slate-50 border-slate-200'
              }`}
            >
              <div className="w-8 h-8 rounded-lg bg-sky-100 text-sky-700 flex items-center justify-center flex-shrink-0 mt-0.5">
                <ShieldAlert className="w-4 h-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-slate-900">3. Quản trị viên (Admin Hub)</span>
                  {currentRole === 'ADMIN' && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-sky-600 text-white">
                      Đang chọn
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5">admin@danasea.vn</p>
                <p className="text-[11px] text-slate-600 truncate mt-1">
                  {roleConfig.ADMIN.desc}
                </p>
              </div>
            </button>
          </div>

          {/* Footer Actions */}
          <div className="pt-2 border-t border-slate-200 flex items-center justify-between text-xs">
            <span className="text-[11px] text-slate-500">
              Đường dẫn hiện tại: <span className="font-medium text-slate-800">{location.pathname}</span>
            </span>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1 text-[11px] font-semibold text-rose-600 hover:text-rose-700 cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              Đăng xuất
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
