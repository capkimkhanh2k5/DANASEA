import { Link, useNavigate } from 'react-router-dom';
import { LogOut, Settings2 } from 'lucide-react';
import type { User } from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { logoutSession } from '../../services/sessionService';

interface AdminAccountProps {
  user: Pick<User, 'fullName' | 'role'> | null;
  token: string | null;
  demo: boolean;
  isExpanded?: boolean;
  onNavigate: () => void;
}

export function AdminAccount({
  user,
  token,
  demo,
  isExpanded = true,
  onNavigate,
}: AdminAccountProps) {
  const navigate = useNavigate();
  const name = demo ? 'Admin xem thử' : user?.fullName || 'Chưa đăng nhập';
  const roles = { ADMIN: 'Quản trị viên', VENDOR: 'Đối tác (Vendor)', CUSTOMER: 'Khách hàng' };
  const role = demo ? 'Dữ liệu mẫu' : user ? roles[user.role] : 'Chưa có phiên Admin';
  const initials = name.split(' ').filter(Boolean).slice(-2).map(word => word[0]).join('').toUpperCase() || 'AD';
  const source = demo
    ? 'Không thay đổi phiên đăng nhập'
    : token?.startsWith('mock_jwt_token_')
    ? 'Tài khoản demo từ bộ chuyển phân hệ'
    : user
    ? 'Hồ sơ của phiên đăng nhập hiện tại'
    : 'Đăng nhập để xem dữ liệu hệ thống';

  const handleLogout = async () => {
    if (demo) {
      useAuthStore.getState().logout();
    } else {
      await logoutSession();
    }
    onNavigate();
    navigate('/auth', { replace: true });
  };

  if (!isExpanded) {
    return (
      <div className="flex flex-col items-center gap-2 select-none" title={`${name} · ${role} · ${source}`}>
        {/* Avatar */}
        <div
          className="w-10 h-10 rounded-full bg-teal-50 text-[#0B5D6F] border border-teal-200/80 font-bold text-xs flex items-center justify-center shadow-2xs"
          title={name}
          aria-label={name}
        >
          {initials}
        </div>

        {/* Quick Settings */}
        <Link
          to={demo ? '/admin/settings?demo=1' : '/admin/settings'}
          onClick={onNavigate}
          className="w-10 h-9 rounded-xl flex items-center justify-center text-slate-500 hover:text-[#0B5D6F] hover:bg-teal-50 transition-colors"
          title="Cấu hình hệ thống"
          aria-label="Cấu hình hệ thống"
        >
          <Settings2 className="w-4 h-4" strokeWidth={1.75} />
        </Link>

        {/* Logout */}
        <button
          type="button"
          onClick={handleLogout}
          className="w-10 h-9 rounded-xl flex items-center justify-center text-rose-500 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
          title="Đăng xuất khỏi hệ thống"
          aria-label="Đăng xuất khỏi hệ thống"
        >
          <LogOut className="w-4 h-4" strokeWidth={1.75} />
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2 select-none" title={`${role} · ${source}`}>
      {/* Account Info Card */}
      <div className="px-2.5 py-2 w-full bg-slate-50/90 border border-slate-200/70 rounded-xl flex items-center justify-between gap-2.5">
        <div className="flex items-center gap-2.5 min-w-0">
          <div
            className="w-9 h-9 rounded-full bg-teal-50 text-[#0B5D6F] border border-teal-200/80 font-bold text-xs flex items-center justify-center shrink-0 shadow-2xs"
            aria-label={name}
          >
            {initials}
          </div>
          <div className="flex flex-col min-w-0 text-left">
            <span className="text-xs font-bold text-slate-900 truncate leading-tight">
              {name}
            </span>
            <span className="text-[10px] text-slate-500 font-medium truncate mt-0.5">
              {role}{!demo && token?.startsWith('mock_jwt_token_') ? ' · Demo' : ''}
            </span>
          </div>
        </div>

        {/* Settings button */}
        <Link
          to={demo ? '/admin/settings?demo=1' : '/admin/settings'}
          onClick={onNavigate}
          className="p-1.5 rounded-lg text-slate-400 hover:text-[#0B5D6F] hover:bg-teal-50 transition-colors shrink-0"
          title="Cấu hình hệ thống"
          aria-label="Cấu hình hệ thống"
        >
          <Settings2 className="w-4 h-4" strokeWidth={1.75} />
        </Link>
      </div>

      {/* Logout full width button */}
      <button
        type="button"
        onClick={handleLogout}
        className="group flex items-center h-9 rounded-xl text-xs font-medium text-rose-600 hover:bg-rose-50 px-3 w-full transition-all duration-150 cursor-pointer"
        title="Đăng xuất khỏi hệ thống"
        aria-label="Đăng xuất"
      >
        <LogOut className="w-4 h-4 text-rose-500 shrink-0" strokeWidth={1.75} />
        <span className="truncate whitespace-nowrap ml-3 transition-opacity duration-200">
          Đăng xuất
        </span>
      </button>
    </div>
  );
}
