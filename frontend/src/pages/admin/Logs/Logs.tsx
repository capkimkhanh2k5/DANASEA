import React, { useState, useEffect, useMemo, useRef } from 'react';
import { adminAuditLogService, getAdminAuditLogError } from '../../../services/adminAuditLogService';
import { useAuthStore } from '../../../store/useAuthStore';
import type { AdminAuditLogDto } from '../../../types/adminAuditLog';

export function Logs() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Server data & Pagination
  const [logs, setLogs] = useState<AdminAuditLogDto[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Search & Filter (client-side over current server page)
  const [search, setSearch] = useState('');
  const [selectedAction, setSelectedAction] = useState<string>('ALL');

  // Loading & Error states
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadVersion, setReloadVersion] = useState(0);

  const generation = useRef(0);

  // Fetch audit logs from server
  useEffect(() => {
    const controller = new AbortController();
    const current = ++generation.current;

    const load = async () => {
      setLoading(true);
      setErrorMessage(null);

      if (!canAccess) {
        setErrorMessage(!isAuthenticated || !accessToken
          ? 'Vui lòng đăng nhập bằng tài khoản Admin để xem nhật ký kiểm toán.'
          : 'Bạn không có quyền xem nhật ký kiểm toán. Cần tài khoản Admin.');
        setLoading(false);
        return;
      }

      try {
        const pageData = await adminAuditLogService.getAuditLogs(page, pageSize, controller.signal);
        if (controller.signal.aborted || generation.current !== current) return;

        setLogs(pageData.content || []);
        setTotalElements(pageData.totalElements || 0);
        setTotalPages(Math.max(1, pageData.totalPages || 1));
        setHasLoaded(true);
      } catch (err) {
        if (!controller.signal.aborted && generation.current === current) {
          setErrorMessage(getAdminAuditLogError(err).message);
        }
      } finally {
        if (!controller.signal.aborted && generation.current === current) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      controller.abort();
      generation.current = current + 1;
    };
  }, [canAccess, isAuthenticated, accessToken, page, reloadVersion]);

  // Dynamic distinct action list from loaded logs
  const availableActions = useMemo(() => {
    const set = new Set<string>();
    logs.forEach(l => {
      if (l.action) set.add(l.action);
    });
    return Array.from(set).sort();
  }, [logs]);

  // Client-side filtering on current server page
  const filteredLogs = useMemo(() => {
    return logs.filter(log => {
      const q = search.trim().toLowerCase();
      const matchesSearch =
        !q ||
        log.id.toLowerCase().includes(q) ||
        (log.actorUserId && log.actorUserId.toLowerCase().includes(q)) ||
        (log.entityType && log.entityType.toLowerCase().includes(q)) ||
        (log.entityId && log.entityId.toLowerCase().includes(q)) ||
        log.action.toLowerCase().includes(q) ||
        (log.metadata && log.metadata.toLowerCase().includes(q));

      const matchesAction = selectedAction === 'ALL' || log.action === selectedAction;
      return matchesSearch && matchesAction;
    });
  }, [logs, search, selectedAction]);

  const getActionBadge = (action: string) => {
    if (action.includes('APPROVE') || action.includes('VERIFY') || action.includes('CREATE') || action.includes('UNLOCK')) {
      return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    }
    if (action.includes('LOCK') || action.includes('REJECT') || action.includes('DISPUTE') || action.includes('DELETE') || action.includes('DEACTIVATE')) {
      return 'bg-rose-100 text-rose-800 border-rose-200';
    }
    return 'bg-blue-100 text-blue-800 border-blue-200';
  };

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">Nhật Ký Kiểm Toán (Audit Logs)</h1>
          <p className="text-sm text-gray-500 mt-1">Theo dõi toàn bộ lịch sử thao tác quản trị, bảo mật và vận hành hệ thống.</p>
        </div>
        <div className="flex items-center gap-3 w-full sm:w-auto">
          <input
            type="text"
            placeholder="Tìm theo ID, hành động, đối tượng..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="px-4 py-2 border rounded-xl text-sm w-full sm:w-72 outline-none focus:ring-2 focus:ring-blue-500 border-gray-200"
          />
          <select
            value={selectedAction}
            onChange={(e) => setSelectedAction(e.target.value)}
            className="px-3 py-2 border rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-500 border-gray-200 bg-white"
          >
            <option value="ALL">Tất cả hành động</option>
            {availableActions.map(action => (
              <option key={action} value={action}>{action}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Filter Scope Notice */}
      <div className="flex items-center justify-between text-xs text-gray-500 px-1">
        <span>
          Đang hiển thị <strong>{filteredLogs.length}</strong> / <strong>{logs.length}</strong> bản ghi (Trang {page + 1}/{totalPages}) • Tổng cộng: <strong>{hasLoaded ? totalElements : '—'}</strong> bản ghi
        </span>
        <span className="italic text-gray-400">
          * Tìm kiếm & lọc hành động áp dụng trên các bản ghi của trang hiện tại
        </span>
      </div>

      {/* Error Alert */}
      {errorMessage && (
        <div role="alert" className="p-4 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700 flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-red-500 text-[20px]">error</span>
            <p>{errorMessage}</p>
          </div>
          {canAccess && (
            <button
              type="button"
              disabled={loading}
              onClick={() => setReloadVersion(v => v + 1)}
              className="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 transition-colors"
            >
              Tải lại nhật ký
            </button>
          )}
        </div>
      )}

      {/* Audit Logs Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table aria-busy={loading} className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-gray-50/75 border-b border-gray-100 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="py-3.5 px-4">Mã Log</th>
                <th className="py-3.5 px-4">Thời gian</th>
                <th className="py-3.5 px-4">Người thực hiện</th>
                <th className="py-3.5 px-4">Hành động</th>
                <th className="py-3.5 px-4">Đối tượng</th>
                <th className="py-3.5 px-4">Chi tiết (Metadata)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-gray-500" role="status">
                    <div className="flex items-center justify-center gap-2">
                      <span className="material-symbols-outlined animate-spin text-blue-600 text-[20px]">progress_activity</span>
                      <span>Đang nạp nhật ký kiểm toán từ máy chủ...</span>
                    </div>
                  </td>
                </tr>
              )}

              {!loading && filteredLogs.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-gray-400" role="status">
                    {search || selectedAction !== 'ALL'
                      ? 'Không tìm thấy nhật ký kiểm toán phù hợp với bộ lọc trang hiện tại.'
                      : 'Chưa có bản ghi nhật ký kiểm toán nào.'}
                  </td>
                </tr>
              )}

              {!loading && filteredLogs.map((log) => (
                <tr key={log.id} className="hover:bg-gray-50/50 transition-colors">
                  <td className="py-3.5 px-4 font-mono text-xs text-gray-600" title={log.id}>
                    {log.id.slice(0, 8)}...
                  </td>
                  <td className="py-3.5 px-4 text-xs text-gray-500 whitespace-nowrap">
                    {log.timestamp ? new Date(log.timestamp).toLocaleString('vi-VN') : '—'}
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-mono text-xs text-gray-800" title={log.actorUserId || 'Hệ thống'}>
                      {log.actorUserId ? `User #${log.actorUserId.slice(0, 8)}` : 'Hệ thống / Cron'}
                    </div>
                  </td>
                  <td className="py-3.5 px-4">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getActionBadge(log.action)}`}>
                      {log.action}
                    </span>
                  </td>
                  <td className="py-3.5 px-4">
                    <span className="font-mono text-xs bg-gray-100 text-gray-700 px-2 py-1 rounded">
                      {log.entityType} {log.entityId ? `#${log.entityId.slice(0, 8)}` : ''}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 font-mono text-xs text-gray-600 max-w-md truncate" title={log.metadata || ''}>
                    {log.metadata || '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Server Pagination Toolbar */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 bg-gray-50/75 border-t border-gray-100 text-xs text-gray-600">
          <div>
            Hiển thị <strong>{totalElements > 0 ? page * pageSize + 1 : 0}</strong> - <strong>{Math.min((page + 1) * pageSize, totalElements)}</strong> trong <strong>{totalElements}</strong> nhật ký
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0 || loading}
              className="px-3 py-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Trang trước
            </button>
            <span className="px-3 py-1.5 font-semibold text-gray-800">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || loading}
              className="px-3 py-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Trang sau
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Logs;
