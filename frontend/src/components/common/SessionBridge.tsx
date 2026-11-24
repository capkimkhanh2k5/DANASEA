import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/useAuthStore';
import { refreshAccessToken, TOKEN_REFRESH_SKEW_MS } from '../../services/apiClient';
import { expireSession, getTokenExpiryMs, registerSessionNavigator } from '../../services/sessionEvents';
import { syncProfile } from '../../services/sessionService';

const AUTH_PERSIST_KEY = 'danasea_auth_storage';
const PROFILE_RESYNC_MS = 5 * 60_000;

/**
 * Headless component that keeps the session alive and consistent:
 *  - registers a React Router navigator so expiry redirects don't hard-reload the page
 *  - refreshes the access token shortly before it expires (also after tab sleep/focus)
 *  - syncs /users/me on start and when the tab regains focus (throttled)
 *  - mirrors login/logout done in other tabs
 */
export function SessionBridge() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    registerSessionNavigator((to, options) => navigate(to, options));
    return () => registerSessionNavigator(null);
  }, [navigate]);

  // Schedule a silent refresh right before the current access token expires.
  useEffect(() => {
    if (!isAuthenticated || !accessToken) return;
    const exp = getTokenExpiryMs(accessToken);
    if (exp === null) return;

    const delay = Math.max(1_000, exp - Date.now() - TOKEN_REFRESH_SKEW_MS);
    const timer = window.setTimeout(() => {
      refreshAccessToken().catch((err) => {
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 401 || status === 403) expireSession();
      });
    }, Math.min(delay, 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [isAuthenticated, accessToken]);

  // Initial profile sync + re-validate on focus (covers laptop sleep and role changes by admin).
  useEffect(() => {
    if (!isAuthenticated) return;
    let lastSync = Date.now();
    void syncProfile();

    const onWake = () => {
      if (document.visibilityState === 'hidden') return;
      const { accessToken: token } = useAuthStore.getState();
      const exp = getTokenExpiryMs(token);
      if (exp !== null && exp - Date.now() < TOKEN_REFRESH_SKEW_MS) {
        refreshAccessToken().catch((err) => {
          const status = (err as { response?: { status?: number } })?.response?.status;
          if (status === 401 || status === 403) expireSession();
        });
      }
      if (Date.now() - lastSync > PROFILE_RESYNC_MS) {
        lastSync = Date.now();
        void syncProfile();
      }
    };
    document.addEventListener('visibilitychange', onWake);
    window.addEventListener('focus', onWake);
    return () => {
      document.removeEventListener('visibilitychange', onWake);
      window.removeEventListener('focus', onWake);
    };
  }, [isAuthenticated]);

  // Cross-tab sync: another tab logged in/out or rotated the token.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === AUTH_PERSIST_KEY) void useAuthStore.persist.rehydrate();
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  return null;
}
