import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '../types';
import { apiClient } from '../services/apiClient';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  setAuth: (user: User, token: string) => void;
  setPendingToken: (token: string) => void;
  login: (userData: User, token?: string) => void;
  logout: () => void;
  switchRole: (roleKey: 'customer' | 'vendor' | 'admin') => User;
}

// Authoritative demo accounts from demo_data_handoff/base/users.json
const OFFICIAL_SEED_ACCOUNTS: Record<'customer' | 'vendor' | 'admin', User & { password: string }> = {
  customer: {
    id: '46c8f2ea-2be5-5365-be80-31fd57d9e260',
    phone: '0912345678',
    email: 'customer_1@danasea.test',
    password: 'Danasea@2026',
    passwordHash: 'Danasea@2026',
    fullName: 'Nguyễn Hoàng Long',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150',
    role: 'CUSTOMER',
    isEmailVerified: true,
    isLocked: false,
    locale: 'vi',
    createdAt: '2026-10-15T09:06:26.816505Z',
    updatedAt: '2026-10-15T09:06:26.816505Z',
  },
  vendor: {
    id: '865a115c-d7f9-5ebd-a36b-1834a590177a',
    phone: '0905123456',
    email: 'vender_1@danasea.test',
    password: 'Danasea@2026',
    passwordHash: 'Danasea@2026',
    fullName: 'Danabeach Club (Điểm dịch vụ thể thao biển)',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
    role: 'VENDOR',
    isEmailVerified: true,
    isLocked: false,
    locale: 'vi',
    createdAt: '2026-10-15T09:06:26.816505Z',
    updatedAt: '2026-10-15T09:06:26.816505Z',
  },
  admin: {
    id: '3e3005a7-001a-5d9f-b4ee-993b52a01d61',
    phone: '0905000001',
    email: 'admin_demo@danasea.test',
    password: 'Danasea@2026',
    passwordHash: 'Danasea@2026',
    fullName: 'Quản trị viên Hệ thống DANASEA',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
    role: 'ADMIN',
    isEmailVerified: true,
    isLocked: false,
    locale: 'vi',
    createdAt: '2026-10-15T09:06:26.816505Z',
    updatedAt: '2026-10-15T09:06:26.816505Z',
  },
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      isAuthenticated: false,

      setAuth: (user, token) => {
        if (import.meta.env.DEV) {
          console.log('[useAuthStore] Setting authenticated user:', user.email, 'Role:', user.role);
        }
        set({ user, accessToken: token, isAuthenticated: true });
      },

      setPendingToken: (token) => {
        if (import.meta.env.DEV) {
          console.log('[useAuthStore] Setting temporary registration token (unauthenticated) for OTP flow');
        }
        set({ user: null, accessToken: token, isAuthenticated: false });
      },

      login: (userData, token) => {
        if (import.meta.env.DEV) {
          console.log('[useAuthStore] login() invoked for:', userData.email);
        }
        set({
          user: userData,
          accessToken: token || null,
          isAuthenticated: true,
        });
      },

      logout: () => {
        if (import.meta.env.DEV) {
          console.log('[useAuthStore] logout() invoked. Clearing state.');
        }
        set({ user: null, accessToken: null, isAuthenticated: false });
      },

      switchRole: (roleKey) => {
        const targetUser = OFFICIAL_SEED_ACCOUNTS[roleKey];
        console.log('[useAuthStore] switchRole() to:', roleKey, targetUser.email);

        // Optimistically set authentic user
        set({ user: targetUser, accessToken: null, isAuthenticated: true });

        // Authenticate directly with Backend API to obtain real JWT Bearer token
        apiClient
          .post<{ accessToken: string }>('/auth/login', {
            email: targetUser.email,
            password: targetUser.password,
          })
          .then((res) => {
            if (res.data?.accessToken) {
              console.log('[useAuthStore] Real JWT token received from Backend for:', roleKey);
              set({ accessToken: res.data.accessToken, isAuthenticated: true });
            }
          })
          .catch((err) => {
            console.warn('[useAuthStore] Could not auto-authenticate demo account:', err);
          });
        return targetUser;
      },
    }),
    {
      name: 'danasea_auth_storage',
    }
  )
);
