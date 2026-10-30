import { useEffect, useState } from 'react';
import { apiClient } from '../../../services/apiClient';
import { useAuthStore } from '../../../store/useAuthStore';
import { loadAdminDashboard } from './dashboardData';
import { createDashboardDemo } from './dashboardDemo';
import type { DashboardData, DateRange } from './dashboardModel';

export function useAdminDashboard(range: DateRange, demo: boolean) {
  const token = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const [revision, setRevision] = useState(0);

  const isMockSession = Boolean(import.meta.env.DEV && (token?.startsWith('mock_jwt_token_') || !token));
  const isRealAdmin = Boolean(isAuthenticated && token && !token.startsWith('mock_jwt_token_') && userRole === 'ADMIN');
  const shouldUseDemo = Boolean(demo || isMockSession);

  const key = `${range.from}/${range.to}/${shouldUseDemo}/${revision}/${token}`;
  const [result, setResult] = useState<{ key: string; data?: DashboardData; error?: string; loadedAt?: Date }>(() => {
    if (shouldUseDemo) {
      return { key, data: createDashboardDemo(range), loadedAt: new Date() };
    }
    return { key: '' };
  });

  useEffect(() => {
    if (!shouldUseDemo && !isRealAdmin) return;
    const controller = new AbortController();
    async function load() {
      try {
        const data = shouldUseDemo
          ? createDashboardDemo(range)
          : await loadAdminDashboard((path, config) => apiClient.get(path, config), range, controller.signal);
        if (!controller.signal.aborted) setResult({ key, data, loadedAt: new Date() });
      } catch {
        if (!controller.signal.aborted) setResult({ key, error: 'Không thể tải bảng điều khiển. Vui lòng thử lại.' });
      }
    }
    void load();
    return () => controller.abort();
  }, [key, range, shouldUseDemo, isRealAdmin]);

  const needsSession = !shouldUseDemo && !isRealAdmin;
  return {
    data: !needsSession && (result.key === key || shouldUseDemo) ? result.data : undefined,
    loading: !needsSession && result.key !== key && !shouldUseDemo,
    error: needsSession ? 'Vui lòng đăng nhập bằng tài khoản Admin để xem dữ liệu hệ thống.' : result.key === key ? result.error : undefined,
    needsSession,
    loadedAt: result.key === key ? result.loadedAt : undefined,
    reload: () => setRevision(value => value + 1),
  };
}
