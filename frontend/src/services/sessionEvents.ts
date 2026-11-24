import { useAuthStore } from '../store/useAuthStore';
import { toast } from '../store/useToastStore';

/**
 * Lightweight session-lifecycle helpers shared by apiClient, layouts and SessionBridge.
 * Kept free of any service import so it can be used from apiClient without circular deps.
 */

type SessionNavigate = (to: string, options?: { replace?: boolean; state?: unknown }) => void;

let sessionNavigate: SessionNavigate | null = null;

/** Registered by <SessionBridge /> so expiry can use React Router (no hard reload). */
export function registerSessionNavigator(fn: SessionNavigate | null) {
  sessionNavigate = fn;
}

const PROTECTED_PREFIXES = ['/vendor', '/admin', '/profile', '/checkout', '/inbox'];

export function isProtectedPath(pathname: string) {
  return PROTECTED_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/**
 * Validate a post-login destination: internal absolute path only, never the auth page,
 * and never a portal the role cannot enter.
 */
export function resolveSafeReturnTo(returnTo: unknown, role: string): string | null {
  if (typeof returnTo !== 'string') return null;
  if (!returnTo.startsWith('/') || returnTo.startsWith('//') || returnTo.includes('\\')) return null;
  const path = returnTo.split(/[?#]/)[0];
  if (path === '/auth' || path.startsWith('/auth/')) return null;
  if ((path === '/admin' || path.startsWith('/admin/')) && role !== 'ADMIN') return null;
  if ((path === '/vendor' || path.startsWith('/vendor/')) && role !== 'VENDOR' && role !== 'ADMIN') return null;
  return returnTo;
}

/**
 * Terminal session end (refresh token expired/revoked, account locked...).
 * Idempotent: concurrent failing requests only trigger it once.
 */
export function expireSession() {
  const { isAuthenticated, accessToken } = useAuthStore.getState();
  if (!isAuthenticated && !accessToken) return;

  useAuthStore.getState().logout();
  toast.warning('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');

  if (typeof window === 'undefined') return;
  const { pathname, search, hash } = window.location;
  if (!isProtectedPath(pathname)) return;

  const returnTo = `${pathname}${search}${hash}`;
  if (sessionNavigate) {
    sessionNavigate('/auth', { replace: true, state: { returnTo } });
  } else {
    window.location.assign('/auth');
  }
}

/** Decode JWT `exp` (ms epoch). Returns null for opaque/mock/invalid tokens. */
export function getTokenExpiryMs(token: string | null | undefined): number | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(b64.padEnd(Math.ceil(b64.length / 4) * 4, '='))
        .split('')
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    );
    const exp = (JSON.parse(json) as { exp?: number }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}
