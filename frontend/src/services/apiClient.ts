import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { getAuthEpoch, useAuthStore } from '../store/useAuthStore';
import { useLanguageStore } from '../store/useLanguageStore';
import { expireSession, getTokenExpiryMs } from './sessionEvents';

/**
 * Production-ready API interaction telemetry record.
 * Stored in window.__DANASEA_API_LOGS__ for live remote debugging and error reporting.
 */
export interface ApiTelemetryRecord {
  correlationId: string;
  timestamp: string;
  durationMs: number;
  method: string;
  url: string;
  status?: number;
  statusText?: string;
  requestPayload?: unknown;
  responsePayload?: unknown;
  error?: string;
}

// Global in-memory circular telemetry buffer (keeps last 50 transactions)
const MAX_TELEMETRY_LOGS = 50;
const telemetryLogs: ApiTelemetryRecord[] = [];

if (typeof window !== 'undefined') {
  (window as unknown as { __DANASEA_API_LOGS__: ApiTelemetryRecord[] }).__DANASEA_API_LOGS__ = telemetryLogs;

  // Helper utility for developers and QA testers to download/export telemetry logs directly from DevTools console
  (window as unknown as { exportDanaseaLogs: () => void }).exportDanaseaLogs = () => {
    const jsonStr = JSON.stringify(telemetryLogs, null, 2);
    try {
      navigator.clipboard.writeText(jsonStr);
      console.log('%c[DANASEA Telemetry] Đã sao chép 50 nhật ký API gần nhất vào Clipboard!', 'color: #16a34a; font-weight: bold;');
    } catch {
      console.log('%c[DANASEA Telemetry] Nhật ký tương tác API gần nhất:', 'color: #0284c7; font-weight: bold;');
      console.log(jsonStr);
    }
  };
}

const AUTH_PERSIST_KEY = 'danasea_auth_storage';
const REFRESH_LOCK_NAME = 'danasea-token-refresh';
/** Refresh proactively when the access token has less than this left. */
export const TOKEN_REFRESH_SKEW_MS = 60_000;

// Bare client (no interceptors) so a failing refresh can never recurse into itself.
/** @internal Exported for unit tests. */
export const refreshClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  timeout: 15000,
});

function isMockToken(token: string | null | undefined) {
  return Boolean(token?.startsWith('mock_jwt_token_'));
}

/** Token persisted by another tab (shared localStorage), if any. */
function readPersistedToken(): string | null {
  try {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(AUTH_PERSIST_KEY);
    if (!raw) return null;
    return (JSON.parse(raw) as { state?: { accessToken?: string | null } }).state?.accessToken ?? null;
  } catch {
    return null;
  }
}

function isTokenFresh(token: string | null | undefined, skewMs: number) {
  const exp = getTokenExpiryMs(token);
  return exp !== null && exp - Date.now() > skewMs;
}

async function performRefresh(staleToken: string | null, startEpoch: number): Promise<string> {
  if (getAuthEpoch() !== startEpoch || !useAuthStore.getState().isAuthenticated) {
    throw new Error('Session changed before refresh started');
  }
  // Another tab may have rotated the refresh token already; reuse its access token.
  // (Refresh tokens rotate with reuse-detection, so two tabs refreshing = family revoked.)
  const persisted = readPersistedToken();
  if (persisted && persisted !== staleToken && isTokenFresh(persisted, 5_000)) {
    if (getAuthEpoch() === startEpoch && useAuthStore.getState().isAuthenticated) {
      useAuthStore.getState().setAccessToken(persisted);
    }
    return persisted;
  }
  const res = await refreshClient.post<{ accessToken: string }>('/auth/refresh');
  const token = res.data?.accessToken;
  if (!token) throw new Error('Refresh response missing accessToken');
  // Never overwrite state if user logged out or switched accounts while the refresh was in flight.
  if (getAuthEpoch() !== startEpoch || !useAuthStore.getState().isAuthenticated) {
    throw new Error('Session changed during refresh');
  }
  useAuthStore.getState().setAccessToken(token);
  return token;
}

let refreshInFlight: Promise<string> | null = null;

/**
 * Single-flight access-token refresh: concurrent callers (in this tab) share one request,
 * and a Web Lock serializes refreshes across tabs.
 */
export function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    const stale = useAuthStore.getState().accessToken;
    const startEpoch = getAuthEpoch();
    const run = () => performRefresh(stale, startEpoch);
    const locked =
      typeof navigator !== 'undefined' && 'locks' in navigator
        ? navigator.locks.request(REFRESH_LOCK_NAME, run)
        : run();
    refreshInFlight = Promise.resolve(locked).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/** True only when the server definitively rejected the refresh token (not on network errors). */
function isRefreshRejected(err: unknown) {
  const status = axios.isAxiosError(err) ? err.response?.status : undefined;
  return status === 401 || status === 403;
}

/** True when a 401 on /change-password actually means the user mistyped their current password (not an expired JWT). */
function isPasswordMismatch401(url: string, responseData: unknown) {
  const code = (responseData as { code?: string } | null | undefined)?.code;
  return url.includes('/change-password') && code === 'INVALID_CREDENTIALS';
}

export const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // For HTTP-only refresh token cookies
  timeout: 15000,
});

// Request Interceptor: Attach JWT Bearer Token, Correlation ID & Structured Logging
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig & { _startTime?: number; _correlationId?: string }) => {
    // Generate Correlation ID for distributed tracing (frontend ⇄ backend)
    const correlationId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `req-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    config._startTime = Date.now();
    config._correlationId = correlationId;

    if (config.headers) {
      config.headers['X-Correlation-Id'] = correlationId;
    }

    // Proactive refresh: avoid sending a request with a token that is about to expire.
    const authState = useAuthStore.getState();
    if (
      authState.isAuthenticated &&
      authState.accessToken &&
      !isMockToken(authState.accessToken) &&
      !config.url?.includes('/auth/') &&
      getTokenExpiryMs(authState.accessToken) !== null &&
      !isTokenFresh(authState.accessToken, TOKEN_REFRESH_SKEW_MS)
    ) {
      try {
        await refreshAccessToken();
      } catch {
        // Let the request go out; the 401 handler decides whether the session is really over.
      }
    }

    const token = useAuthStore.getState().accessToken;

    if (token && !isMockToken(token) && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
      config._sentToken = token;
    }

    // Vendor and Admin backoffice portals default to Vietnamese ('vi') for local operators.
    // They must never blindly inherit the international tourist language toggle from the customer store.
    const isBackoffice =
      config.url?.includes('/vendor') ||
      config.url?.includes('/admin') ||
      (typeof window !== 'undefined' &&
        (window.location.pathname.startsWith('/vendor') || window.location.pathname.startsWith('/admin')));

    const defaultLang = isBackoffice ? 'vi' : (useLanguageStore.getState().language || 'vi');
    if (config.headers && !config.headers['Accept-Language']) {
      config.headers['Accept-Language'] = defaultLang;
    }

    const method = (config.method || 'GET').toUpperCase();
    const url = `${config.baseURL || ''}${config.url || ''}`;

    // Sanitize data (mask sensitive keys)
    let sanitizedData: unknown = undefined;
    if (config.data) {
      if (typeof config.data === 'object' && config.data !== null) {
        const copy = { ...(config.data as Record<string, unknown>) };
        if ('password' in copy) copy.password = '******';
        if ('oldPassword' in copy) copy.oldPassword = '******';
        if ('currentPassword' in copy) copy.currentPassword = '******';
        if ('newPassword' in copy) copy.newPassword = '******';
        sanitizedData = copy;
      } else {
        sanitizedData = config.data;
      }
    }

    // Structured Console Logging with Correlation ID
    console.groupCollapsed(
      `%c[API REQ] ${method} ${url} %c#${correlationId.slice(-6)}`,
      'color: #0284c7; font-weight: bold;',
      'color: #64748b; font-weight: normal;'
    );
    console.log('Correlation ID:', correlationId);
    console.log('Timestamp:', new Date().toISOString());
    console.log('Headers:', config.headers);
    if (sanitizedData !== undefined) {
      console.log('Body Payload:', sanitizedData);
    }
    console.groupEnd();

    return config;
  },
  (error) => {
    console.error('[API Request Error]:', error);
    return Promise.reject(error);
  }
);

// Module augmentation for silent requests and tracing
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean;
    _retry?: boolean;
    _sentToken?: string;
    _startTime?: number;
    _correlationId?: string;
  }
}

// Response Interceptor: Record Telemetry & Structured Logging
apiClient.interceptors.response.use(
  (response) => {
    const config = response.config as InternalAxiosRequestConfig & { _startTime?: number; _correlationId?: string };
    const method = (config.method || 'GET').toUpperCase();
    const url = config.url || '';
    const status = response.status;
    const durationMs = config._startTime ? Date.now() - config._startTime : 0;
    const correlationId = config._correlationId || 'unknown';

    // Record into global telemetry buffer
    telemetryLogs.unshift({
      correlationId,
      timestamp: new Date().toISOString(),
      durationMs,
      method,
      url,
      status,
      statusText: response.statusText,
      requestPayload: config.data,
      responsePayload: response.data,
    });
    if (telemetryLogs.length > MAX_TELEMETRY_LOGS) {
      telemetryLogs.pop();
    }

    // Professional Colored Console Output with Latency
    console.groupCollapsed(
      `%c[API RES ${status}] ${method} ${url} %c(${durationMs}ms) #${correlationId.slice(-6)}`,
      'color: #16a34a; font-weight: bold;',
      'color: #64748b; font-weight: normal;'
    );
    console.log('Correlation ID:', correlationId);
    console.log('Status Text:', response.statusText);
    console.log('Duration:', `${durationMs}ms`);
    console.log('Response Body:', response.data);
    console.groupEnd();

    return response;
  },
  async (error: AxiosError) => {
    const config = (error.config || {}) as InternalAxiosRequestConfig & { _startTime?: number; _correlationId?: string; silent?: boolean; _retry?: boolean };
    const method = (config.method || 'GET').toUpperCase();
    const url = config.url || '';
    const status = error.response?.status;
    const durationMs = config._startTime ? Date.now() - config._startTime : 0;
    const correlationId = config._correlationId || 'unknown';
    const responseData = error.response?.data;
    const isSilent = config.silent;

    // Gracefully ignore intentional request cancellations
    if (axios.isCancel(error) || error.name === 'CanceledError' || error.message === 'canceled') {
      return Promise.reject(error);
    }

    const authState = useAuthStore.getState();
    const currentToken = authState.accessToken;
    const canHandle401 =
      status === 401 &&
      !url.includes('/auth/') &&
      !isPasswordMismatch401(url, responseData) &&
      Boolean(currentToken) &&
      authState.isAuthenticated &&
      !isMockToken(currentToken);

    // Handle token expiration: silently refresh once, replay the request, only then give up.
    if (canHandle401 && !config._retry) {
      config._retry = true;
      try {
        // Another concurrent request may have already rotated the access token; replay directly.
        if (!config._sentToken || currentToken === config._sentToken) {
          await refreshAccessToken();
        }
        return apiClient(config);
      } catch (refreshErr) {
        if (isRefreshRejected(refreshErr)) {
          console.warn('[API Auth] Refresh token rejected. Ending session...');
          expireSession();
        }
        // Network/5xx during refresh: keep the session, surface the original error below.
      }
    } else if (canHandle401 && config._retry) {
      console.warn('[API Auth] Still 401 after token refresh. Ending session...');
      expireSession();
    }

    // Record error in global telemetry buffer
    telemetryLogs.unshift({
      correlationId,
      timestamp: new Date().toISOString(),
      durationMs,
      method,
      url,
      status,
      statusText: error.response?.statusText,
      requestPayload: config.data,
      responsePayload: responseData,
      error: error.message,
    });
    if (telemetryLogs.length > MAX_TELEMETRY_LOGS) {
      telemetryLogs.pop();
    }

    if (!isSilent) {
      console.group(
        `%c[API ERR ${status || 'NETWORK'}] ${method} ${url} %c(${durationMs}ms) #${correlationId.slice(-6)}`,
        'color: #dc2626; font-weight: bold;',
        'color: #991b1b; font-weight: normal;'
      );
      console.error('Correlation ID:', correlationId);
      console.error('Error Message:', error.message);
      console.error('Backend Response:', responseData);
      console.groupEnd();
    }

    return Promise.reject(error);
  }
);
