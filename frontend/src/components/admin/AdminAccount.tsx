import { Link, useNavigate } from 'react-router-dom';
import { LogOut, Settings2 } from 'lucide-react';
import type { User } from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { authService } from '../../services/authService';

export function AdminAccount({
  user,
  token,
  demo,
  collapsed = false,
  onNavigate,
}: {
  user: Pick<User, 'fullName' | 'role'> | null;
  token: string | null;
  demo: boolean;
  collapsed?: boolean;
  onNavigate: () => void;
}) {
  const navigate = useNavigate();
  const name = demo ? 'Admin xem thử' : user?.fullName || 'Chưa đăng nhập';
  const roles = { ADMIN: 'Quản trị viên', VENDOR: 'Đối tác (Vendor)', CUSTOMER: 'Khách hàng' };
  const role = demo ? 'Dữ liệu mẫu' : user ? roles[user.role] : 'Chưa có phiên Admin';
  const initials = name.split(' ').filter(Boolean).slice(-2).map(word => word[0]).join('').toUpperCase();
  const source = demo ? 'Không thay đổi phiên đăng nhập' : token?.startsWith('mock_jwt_token_') ? 'Tài khoản demo từ bộ chuyển phân hệ' : user ? 'Hồ sơ của phiên đăng nhập hiện tại' : 'Đăng nhập để xem dữ liệu hệ thống';

  const handleLogout = async () => {
    try {
      if (!demo && token && !token.startsWith('mock_jwt_token_')) {
        await authService.logout();
      }
    } catch (err) {
      console.warn('[AdminAccount] Logout notification error, proceeding with local cleanup:', err);
    } finally {
      useAuthStore.getState().logout();
      onNavigate();
      navigate('/auth');
    }
  };

  if (collapsed) {
    return (
      <div className="admin-account admin-account--collapsed" title={`${name} · ${role} · ${source}`}>
        <span className="admin-avatar" title={name} aria-label={name}>
          {initials}
        </span>
        <button
          type="button"
          onClick={handleLogout}
          className="admin-account-action admin-account-action--logout"
          aria-label="Đăng xuất khỏi hệ thống"
          title="Đăng xuất khỏi hệ thống"
        >
          <LogOut size={16} />
        </button>
      </div>
    );
  }

  return (
    <div className="admin-account" title={`${role} · ${source}`}>
      <span className="admin-avatar" aria-label={name}>{initials}</span>
      <div className="admin-account-info">
        <strong>{name}</strong>
        <small>{role}{!demo && token?.startsWith('mock_jwt_token_') ? ' · Tài khoản demo' : ''}</small>
      </div>
      <div className="admin-account-actions">
        <Link
          to={demo ? '/admin/settings?demo=1' : '/admin/settings'}
          onClick={onNavigate}
          className="admin-account-action"
          aria-label="Cấu hình hệ thống"
          title="Cấu hình hệ thống"
        >
          <Settings2 size={16} />
        </Link>
        <button
          type="button"
          onClick={handleLogout}
          className="admin-account-action admin-account-action--logout"
          aria-label="Đăng xuất khỏi hệ thống"
          title="Đăng xuất khỏi hệ thống"
        >
          <LogOut size={16} />
        </button>
      </div>
    </div>
  );
}
