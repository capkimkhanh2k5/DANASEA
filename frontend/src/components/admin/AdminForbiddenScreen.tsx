import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Home, ArrowRight, LogOut } from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';

export function AdminForbiddenScreen({
  user,
}: {
  user: { role?: string; fullName?: string; email?: string } | null;
}) {
  const navigate = useNavigate();
  const logout = useAuthStore(state => state.logout);

  const roleLabel =
    user?.role === 'VENDOR'
      ? 'Đối tác (Vendor)'
      : user?.role === 'CUSTOMER'
      ? 'Khách hàng (Customer)'
      : user?.role || 'Chưa xác định';

  return (
    <div
      className="admin-shell"
      style={{
        display: 'grid',
        placeItems: 'center',
        minHeight: '100vh',
        padding: '24px',
        background: '#f5f8fa',
      }}
    >
      <div
        style={{
          maxWidth: 480,
          width: '100%',
          background: 'white',
          border: '1px solid #e3eaed',
          borderRadius: 16,
          padding: '36px 32px',
          textAlign: 'center',
          boxShadow: '0 12px 40px rgba(18, 51, 61, 0.08)',
        }}
      >
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: '50%',
            background: '#fef2f2',
            color: '#b91c1c',
            display: 'grid',
            placeItems: 'center',
            margin: '0 auto 18px',
          }}
        >
          <ShieldAlert size={28} />
        </div>

        <h1
          style={{
            fontSize: 20,
            fontWeight: 700,
            color: 'var(--admin-ink, #171717)',
            marginBottom: 8,
            letterSpacing: '-0.3px',
          }}
        >
          403 — Không có quyền truy cập
        </h1>

        <p
          style={{
            fontSize: 13,
            color: 'var(--admin-secondary, #525252)',
            lineHeight: 1.6,
            marginBottom: 24,
          }}
        >
          Tài khoản <strong>{user?.fullName || user?.email || 'hiện tại'}</strong> mang vai trò{' '}
          <strong>{roleLabel}</strong>. Cổng Quản trị DANASEA chỉ dành riêng cho tài khoản mang quyền{' '}
          <strong style={{ color: 'var(--admin-primary)' }}>Quản trị viên (ADMIN)</strong>.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {user?.role === 'VENDOR' && (
            <button
              type="button"
              className="admin-button admin-button--primary"
              onClick={() => navigate('/vendor')}
              style={{ width: '100%', justifyContent: 'center' }}
            >
              <span>Đến Cổng Đối tác Vendor</span>
              <ArrowRight size={15} />
            </button>
          )}

          <button
            type="button"
            className="admin-button"
            onClick={() => navigate('/')}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            <Home size={15} />
            <span>Về trang chủ Danasea</span>
          </button>

          <button
            type="button"
            className="admin-button admin-button--danger"
            onClick={() => {
              logout();
              navigate('/auth');
            }}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            <LogOut size={15} />
            <span>Đăng xuất &amp; Đăng nhập tài khoản khác</span>
          </button>
        </div>
      </div>
    </div>
  );
}
