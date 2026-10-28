import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../store/useAuthStore';
import { useLoadingStore } from '../store/useLoadingStore';
import { useLanguageStore } from '../store/useLanguageStore';

// Smart Latency Threshold: Only trigger full-screen wave if request takes > 600ms
let activeRequestsCount = 0;
let latencyTimer: ReturnType<typeof setTimeout> | null = null;

const startLoadingCountdown = (skipLoader?: boolean) => {
  if (skipLoader) return;
  activeRequestsCount++;
  if (activeRequestsCount === 1) {
    latencyTimer = setTimeout(() => {
      useLoadingStore.getState().showLoader('Đang đồng bộ dữ liệu...', 'Hệ thống đang kết nối máy chủ DANASEA', 4000);
    }, 600);
  }
};

const stopLoading = (skipLoader?: boolean) => {
  if (skipLoader) return;
  activeRequestsCount = Math.max(0, activeRequestsCount - 1);
  if (activeRequestsCount === 0) {
    if (latencyTimer) {
      clearTimeout(latencyTimer);
      latencyTimer = null;
    }
    useLoadingStore.getState().hideLoader();
  }
};

export const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // For HTTP-only refresh token cookies
  timeout: 15000,
});

// Request Interceptor: Attach JWT Bearer Token & Log Request
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    const currentLang = useLanguageStore.getState().language || 'vi';
    if (config.headers) {
      config.headers['Accept-Language'] = currentLang;
    }

    const skipLoader = config.headers?.['X-Skip-Loader'] === 'true' || Boolean(config.url?.includes('/weather'));
    startLoadingCountdown(skipLoader);

    // Structured Debug Logging
    const url = `${config.baseURL || ''}${config.url || ''}`;
    const method = (config.method || 'GET').toUpperCase();
    console.groupCollapsed(`%c[API Request] ${method} ${url}`, 'color: #0284c7; font-weight: bold;');
    console.log('Timestamp:', new Date().toISOString());
    console.log('Headers:', config.headers);
    if (config.data) {
      // Avoid logging sensitive password values directly in plain text in production
      const sanitizedData = { ...config.data };
      if (sanitizedData.password) sanitizedData.password = '******';
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

// Response Interceptor: Log Result & Handle Common Status Codes
apiClient.interceptors.response.use(
  (response) => {
    const skipLoader = response.config.headers?.['X-Skip-Loader'] === 'true' || Boolean(response.config.url?.includes('/weather'));
    stopLoading(skipLoader);
    const url = response.config.url;
    const status = response.status;
    console.groupCollapsed(`%c[API Response] ${status} ${url}`, 'color: #16a34a; font-weight: bold;');
    console.log('Status Text:', response.statusText);
    console.log('Response Body:', response.data);
    console.groupEnd();

    return response;
  },
  (error: AxiosError) => {
    const skipLoader = error.config?.headers?.['X-Skip-Loader'] === 'true' || Boolean(error.config?.url?.includes('/weather'));
    stopLoading(skipLoader);
    const status = error.response?.status;
    const url = error.config?.url;
    const responseData = error.response?.data;

    console.group(`%c[API Error] ${status || 'NETWORK_ERROR'} ${url || ''}`, 'color: #dc2626; font-weight: bold;');
    console.error('Error Message:', error.message);
    console.error('Response Data:', responseData);
    console.groupEnd();

    // Handle token expiration or unauthorized access (exclude all /auth/ routes)
    if (status === 401 && !url?.includes('/auth/')) {
      console.warn('[API Auth] Session expired or unauthorized (401). Clearing credentials...');
      useAuthStore.getState().logout();
      if (typeof window !== 'undefined' && (window.location.pathname.startsWith('/vendor') || window.location.pathname.startsWith('/admin'))) {
        window.location.href = '/auth';
      }
    }


    return Promise.reject(error);
  }
);
