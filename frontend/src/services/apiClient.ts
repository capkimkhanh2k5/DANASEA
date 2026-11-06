import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../store/useAuthStore';
import { useLanguageStore } from '../store/useLanguageStore';

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
  (config: InternalAxiosRequestConfig & { _startTime?: number; _correlationId?: string }) => {
    // Generate Correlation ID for distributed tracing (frontend ⇄ backend)
    const correlationId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `req-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    config._startTime = Date.now();
    config._correlationId = correlationId;

    if (config.headers) {
      config.headers['X-Correlation-Id'] = correlationId;
    }

    const token = useAuthStore.getState().accessToken;
    const isMockToken = token?.startsWith('mock_jwt_token_');

    if (token && !isMockToken && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    const currentLang = useLanguageStore.getState().language || 'vi';
    if (config.headers) {
      config.headers['Accept-Language'] = currentLang;
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
  (error: AxiosError) => {
    const config = (error.config || {}) as InternalAxiosRequestConfig & { _startTime?: number; _correlationId?: string; silent?: boolean };
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

    // Handle token expiration or unauthorized access
    const currentToken = useAuthStore.getState().accessToken;
    const isMock = currentToken?.startsWith('mock_jwt_token_');
    if (status === 401 && !url.includes('/auth/') && currentToken && !isMock) {
      console.warn('[API Auth] Session expired or unauthorized (401). Clearing credentials...');
      useAuthStore.getState().logout();
      if (typeof window !== 'undefined') {
        if (
          window.location.pathname.startsWith('/vendor') ||
          window.location.pathname.startsWith('/admin') ||
          window.location.pathname.startsWith('/profile')
        ) {
          window.location.href = '/auth';
        }
      }
    }

    return Promise.reject(error);
  }
);
