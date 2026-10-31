import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../store/useAuthStore';
import { useLanguageStore } from '../store/useLanguageStore';

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
    const isMockToken = token?.startsWith('mock_jwt_token_');

    if (token && !isMockToken && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    const currentLang = useLanguageStore.getState().language || 'vi';
    if (config.headers) {
      config.headers['Accept-Language'] = currentLang;
    }

    const method = (config.method || 'GET').toUpperCase();
    if (isMockToken && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && config.url?.startsWith('/admin/')) {
      console.warn(`[DEV Demo] Mutation blocked for ${method} ${config.url}`);
      return Promise.reject(new Error('Thao tác quản trị bị khóa trong chế độ xem trước (DEV Demo)'));
    }
    // Structured Debug Logging
    const url = `${config.baseURL || ''}${config.url || ''}`;
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
    const url = response.config.url;
    const status = response.status;
    console.groupCollapsed(`%c[API Response] ${status} ${url}`, 'color: #16a34a; font-weight: bold;');
    console.log('Status Text:', response.statusText);
    console.log('Response Body:', response.data);
    console.groupEnd();

    return response;
  },
  (error: AxiosError) => {
    const status = error.response?.status;
    const url = error.config?.url;
    const responseData = error.response?.data;

    console.group(`%c[API Error] ${status || 'NETWORK_ERROR'} ${url || ''}`, 'color: #dc2626; font-weight: bold;');
    console.error('Error Message:', error.message);
    console.error('Response Data:', responseData);
    console.groupEnd();

    // Handle token expiration or unauthorized access (exclude all /auth/ routes and DEV mock sessions)
    const currentToken = useAuthStore.getState().accessToken;
    const isMock = currentToken?.startsWith('mock_jwt_token_');
    if (status === 401 && !url?.includes('/auth/') && !isMock) {
      console.warn('[API Auth] Session expired or unauthorized (401). Clearing credentials...');
      useAuthStore.getState().logout();
      if (typeof window !== 'undefined' && (window.location.pathname.startsWith('/vendor') || window.location.pathname.startsWith('/admin'))) {
        window.location.href = '/auth';
      }
    }


    return Promise.reject(error);
  }
);
