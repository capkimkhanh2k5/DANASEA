import { Link } from 'react-router-dom';
import { Settings2 } from 'lucide-react';
import type { User } from '../../types';

export function AdminAccount({ user, token, demo, onNavigate }: {
  user: Pick<User, 'fullName' | 'role'> | null; token: string | null; demo: boolean; onNavigate: () => void;
}) {
  const name = demo ? 'Admin xem thử' : user?.fullName || 'Chưa đăng nhập';
  const roles = { ADMIN: 'Quản trị viên', VENDOR: 'Đối tác (Vendor)', CUSTOMER: 'Khách hàng' };
  const role = demo ? 'Dữ liệu mẫu' : user ? roles[user.role] : 'Chưa có phiên Admin';
  const initials = name.split(' ').filter(Boolean).slice(-2).map(word => word[0]).join('').toUpperCase();
  const source = demo ? 'Không thay đổi phiên đăng nhập' : token?.startsWith('mock_jwt_token_') ? 'Tài khoản demo từ bộ chuyển phân hệ' : user ? 'Hồ sơ của phiên đăng nhập hiện tại' : 'Đăng nhập để xem dữ liệu hệ thống';
  return (
    <div className="admin-account" title={`${role} · ${source}`}>
      <span className="admin-avatar">{initials}</span>
      <div className="admin-account-info">
        <strong>{name}</strong>
        <small>{role}{!demo && token?.startsWith('mock_jwt_token_') ? ' · Tài khoản demo' : ''}</small>
      </div>
      <Link
        to={demo ? '/admin/settings?demo=1' : '/admin/settings'}
        onClick={onNavigate}
        className="admin-account-action"
        aria-label="Cấu hình hệ thống"
        title="Cấu hình hệ thống"
      >
        <Settings2 size={17} />
      </Link>
    </div>
  );
}
