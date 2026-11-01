import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '../types';
import { MOCK_TEST_ACCOUNTS } from '../mockData';

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
        const targetUser = MOCK_TEST_ACCOUNTS[roleKey];
        if (!import.meta.env.DEV) {
          return targetUser;
        }
        console.log('[useAuthStore] switchRole() to:', roleKey);
        set({ user: targetUser, accessToken: `mock_jwt_token_${roleKey}`, isAuthenticated: true });
        return targetUser;
      },
    }),
    {
      name: 'danasea_auth_storage',
    }
  )
);
