import React, { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Check,
  ChevronDown,
  Code2,
  Copy,
  Eye,
  RefreshCw,
  RotateCcw,
  Search,
  Shield,
  X,
} from 'lucide-react';
import { MOCK_ADMIN_AUDIT_LOGS } from '../../../data/adminMockData';
import { adminAuditLogService, getAdminAuditLogError } from '../../../services/adminAuditLogService';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import {
  AdminButton,
  AdminNotice,
  AdminPagination,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  ENTITY_LABELS,
  filterAuditLogs,
  formatDateTimeVN,
  getActionMeta,
  parseAuditOutcome,
  sanitizeMetadata,
  type AdminAuditLogItem,
  type LogActionFilter,
  type LogEntityFilter,
} from './logsModel';
import { formatDisplayCode } from '../Orders/ordersModel';
import './Logs.css';

export function Logs() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // State
  const [logs, setLogs] = useState<AdminAuditLogItem[]>(() => demo ? MOCK_ADMIN_AUDIT_LOGS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [selectedAction, setSelectedAction] = useState<LogActionFilter>('ALL');
  const [selectedEntity, setSelectedEntity] = useState<LogEntityFilter>('ALL');
  const [selectedLog, setSelectedLog] = useState<AdminAuditLogItem | null>(null);
  const [showTechnicalJson, setShowTechnicalJson] = useState(false);
  const [copied, setCopied] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [previewState, setPreviewState] = useState<'normal' | 'loading' | 'error' | 'empty'>('normal');
  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(() => demo ? Math.max(1, Math.ceil(MOCK_ADMIN_AUDIT_LOGS.length / PAGE_SIZE)) : 1);
  const [totalElements, setTotalElements] = useState(() => demo ? MOCK_ADMIN_AUDIT_LOGS.length : 0);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (demo) {
      return;
    }

    let isMounted = true;
    setLoading(true);
    setErrorMessage(null);

    adminAuditLogService.getAuditLogs(currentPage - 1, PAGE_SIZE)
      .then(res => {
        if (!isMounted) return;
        const mapped: AdminAuditLogItem[] = (res.content || []).map(l => ({
          id: l.id,
          actorUserId: l.actorUserId || '',
          actorName: l.actorUserId ? `User ${l.actorUserId.substring(0, 8)}` : 'Hệ thống',
          actorEmail: '',
          action: l.action,
          entityType: l.entityType,
          entityId: l.entityId || '',
          metadata: l.metadata || '{}',
          createdAt: l.timestamp,
          updatedAt: l.timestamp,
        }));
        setLogs(mapped);
        setTotalPages(res.totalPages || 1);
        setTotalElements(res.totalElements || mapped.length);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const info = getAdminAuditLogError(err);
        setErrorMessage(info.message);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo, currentPage, refreshKey]);

  // Extract available action options dynamically from current logs & known actions
  const availableActionOptions = useMemo(() => {
    const predefinedActions: string[] = [
      'RECONCILE_PAYMENT_PENDING',
      'RECONCILE_PAYMENT_FAILED',
      'RECONCILE_PAYMENT_SUCCESS',
      'APPROVE_PAYOUT',
      'GENERATE_SETTLEMENT',
      'VERIFY_VENDOR',
      'APPROVE_VENDOR',
      'REJECT_VENDOR',
      'PARTNER_APPLICATION_APPROVAL',
      'PARTNER_APPLICATION_REJECTION',
      'VENDOR_DOCUMENT_APPROVE',
      'VENDOR_DOCUMENT_REJECT',
      'RESOLVE_DISPUTE',
      'PROCESS_REFUND',
      'REFUND_MANUALLY_COMPLETED',
      'UPDATE_PROMOTION',
      'CREATE_PROMOTION',
      'UPDATE_SYSTEM_CONFIG',
      'LOCK_USER_ACCOUNT',
      'UNLOCK_USER_ACCOUNT',
      'ADMIN_LOGIN',
    ];

    const pageActions = Array.from(new Set(logs.map(l => l.action)));
    const allActionKeys = Array.from(new Set([...pageActions, ...predefinedActions]));

    return allActionKeys.map(actionKey => {
      const countOnPage = logs.filter(l => l.action === actionKey).length;
      const meta = getActionMeta(actionKey);
      return {
        value: actionKey,
        label: meta.label,
        countOnPage,
      };
    }).sort((a, b) => b.countOnPage - a.countOnPage || a.label.localeCompare(b.label, 'vi'));
  }, [logs]);

  // Extract available entity options dynamically
  const availableEntityOptions = useMemo(() => {
    const predefinedEntities = [
      'PAYMENT',
      'PARTNER_APPLICATION',
      'VENDOR_DOCUMENT',
      'PayoutRequest',
      'Vendor',
      'Dispute',
      'SystemConfig',
      'User',
      'Promotion',
      'Order',
      'Settlement',
      'Refund',
    ];

    const pageEntities = Array.from(new Set(logs.map(l => l.entityType)));
    const allEntityKeys = Array.from(new Set([...pageEntities, ...predefinedEntities]));

    return allEntityKeys.map(entKey => {
      const countOnPage = logs.filter(l => l.entityType === entKey).length;
      const label = ENTITY_LABELS[entKey] || entKey;
      return {
        value: entKey,
        label,
        countOnPage,
      };
    }).sort((a, b) => b.countOnPage - a.countOnPage || a.label.localeCompare(b.label, 'vi'));
  }, [logs]);

  // Filtered data (applied to currently loaded page)
  const filteredLogs = useMemo(() => {
    return filterAuditLogs(logs, search, selectedAction, selectedEntity);
  }, [logs, search, selectedAction, selectedEntity]);

  const calculatedTotalPages = demo
    ? (Math.ceil(filteredLogs.length / PAGE_SIZE) || 1)
    : totalPages;
  const activePage = Math.min(Math.max(1, currentPage), calculatedTotalPages);

  const paginatedLogs = useMemo(() => {
    if (!demo) return filteredLogs;
    const start = (activePage - 1) * PAGE_SIZE;
    return filteredLogs.slice(start, start + PAGE_SIZE);
  }, [demo, filteredLogs, activePage]);

  // Transparent statistics: Global total vs. page-level operational indicators
  const stats = useMemo(() => {
    const totalStored = demo ? MOCK_ADMIN_AUDIT_LOGS.length : totalElements;
    const pageApprovals = logs.filter(l =>
      l.action.includes('APPROVE') ||
      l.action.includes('VERIFY') ||
      l.action.includes('SUCCESS')
    ).length;
    const pageResolutions = logs.filter(l =>
      l.action.includes('RESOLVE') ||
      l.action.includes('LOCK') ||
      l.action.includes('REJECT') ||
      l.action.includes('PENDING') ||
      l.action.includes('FAILED')
    ).length;
    return { totalStored, pageApprovals, pageResolutions };
  }, [demo, totalElements, logs]);

  // Reset all filters
  const handleResetFilters = () => {
    setSearch('');
    setSelectedAction('ALL');
    setSelectedEntity('ALL');
    setNotice('Đã đặt lại bộ lọc nhật ký trên trang hiện tại.');
  };

  // Real data refresh handler
  const handleRefresh = () => {
    if (demo) {
      setNotice('Đã làm mới dữ liệu nhật ký kiểm toán (Demo).');
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    setRefreshKey(k => k + 1);
    setNotice('Đã làm mới dữ liệu nhật ký kiểm toán từ máy chủ.');
  };

  // Copy sanitized JSON metadata
  const handleCopyMetadata = (raw?: string) => {
    if (!raw) return;
    const sanitized = sanitizeMetadata(raw);
    const jsonStr = JSON.stringify(sanitized, null, 2);
    navigator.clipboard?.writeText(jsonStr);
    setCopied(true);
    setNotice('Đã sao chép siêu dữ liệu (JSON) đã xử lý an toàn vào clipboard.');
    setTimeout(() => setCopied(false), 2500);
  };

  const handleCopyText = (text: string, label: string) => {
    if (!text) return;
    navigator.clipboard?.writeText(text);
    setNotice(`Đã sao chép ${label}: ${text}`);
  };

  // Open drawer and ensure technical section starts collapsed
  const handleOpenDetail = (log: AdminAuditLogItem) => {
    setSelectedLog(log);
    setShowTechnicalJson(false);
    setCopied(false);
  };

  const hasActiveFilters = search.trim() !== '' || selectedAction !== 'ALL' || selectedEntity !== 'ALL';

  // Audit outcome items for selected log
  const auditOutcomes = useMemo(() => {
    return selectedLog ? parseAuditOutcome(selectedLog.metadata) : [];
  }, [selectedLog]);

  return (
    <main className="admin-workspace-page" aria-label="Trang nhật ký kiểm toán hệ thống">
      {/* Demo State Controller (Dev only) */}
      {demo && (
        <div className="admin-demo-controls-bar" style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, fontSize: 12 }}>
          <label htmlFor="logs-preview-select" style={{ color: 'var(--admin-muted)', fontWeight: 500 }}>
            Mô phỏng trạng thái trang:
          </label>
          <select
            id="logs-preview-select"
            aria-label="Mô phỏng trạng thái trang"
            value={previewState}
            onChange={e => setPreviewState(e.target.value as 'normal' | 'loading' | 'error' | 'empty')}
            className="admin-field"
            style={{ minHeight: 32, width: 'auto', padding: '4px 10px', fontSize: 12 }}
          >
            <option value="normal">Bình thường ({logs.length} bản ghi)</option>
            <option value="loading">Đang tải nhật ký</option>
            <option value="error">Lỗi kết nối nhật ký</option>
            <option value="empty">Nhật ký trống</option>
          </select>
        </div>
      )}

      {/* State Switcher */}
      {previewState === 'loading' ? (
        <AdminState kind="loading" title="Đang tải nhật ký kiểm toán hệ thống…" />
      ) : previewState === 'error' ? (
        <AdminState
          kind="error"
          title="Không thể tải nhật ký kiểm toán"
          description="Đã xảy ra lỗi khi truy xuất bản ghi từ hệ thống lưu trữ kiểm toán. Vui lòng thử lại sau."
          action={
            <AdminButton variant="secondary" onClick={() => setPreviewState('normal')}>
              Thử tải lại
            </AdminButton>
          }
        />
      ) : previewState === 'empty' ? (
        <AdminState
          kind="empty"
          title="Chưa có bản ghi nhật ký nào"
          description="Hệ thống chưa ghi nhận thao tác kiểm toán nào trong cơ sở dữ liệu."
        />
      ) : (
        <div className="admin-logs-container">
          {/* Summary KPI Strip: Distinguishes between global DB total and current page indicators */}
          <section
            className="admin-workspace-summary"
            aria-label="Thống kê nhanh nhật ký"
            style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}
          >
            <div className="admin-workspace-metric">
              <span>Tổng số bản ghi lưu trữ</span>
              <strong>{stats.totalStored.toLocaleString('vi-VN')}</strong>
              <small>Toàn hệ thống CSDL · Toàn thời gian</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Hồ sơ &amp; Quyết toán đã duyệt</span>
              <strong style={{ color: 'var(--admin-primary)' }}>{stats.pageApprovals}</strong>
              <small>Trong {logs.length} bản ghi trang {activePage} (Giới hạn API)</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Xử lý kỷ luật &amp; Đối soát</span>
              <strong style={{ color: '#b45309' }}>{stats.pageResolutions}</strong>
              <small>Trong {logs.length} bản ghi trang {activePage} (Giới hạn API)</small>
            </div>
          </section>

          {/* Filter Toolbar */}
          <div className="admin-logs-toolbar" role="search" aria-label="Bộ lọc tìm kiếm nhật ký">
            <div className="admin-logs-filters">
              <div className="admin-search-field admin-logs-search">
                <Search size={15} aria-hidden="true" />
                <input
                  type="text"
                  placeholder="Tìm theo mã log, người thực hiện, đối tượng..."
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  aria-label="Tìm kiếm nhật ký kiểm toán"
                />
                {search && (
                  <button
                    type="button"
                    onClick={() => setSearch('')}
                    aria-label="Xóa nội dung tìm kiếm"
                    style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--admin-muted)' }}
                  >
                    <X size={14} />
                  </button>
                )}
              </div>

              {/* Action Filter */}
              <select
                aria-label="Lọc theo loại hành động"
                value={selectedAction}
                onChange={e => setSelectedAction(e.target.value as LogActionFilter)}
                className="admin-logs-select"
              >
                <option value="ALL">Tất cả hành động ({logs.length} trên trang)</option>
                {availableActionOptions.map(opt => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}{opt.countOnPage > 0 ? ` (${opt.countOnPage})` : ''}
                  </option>
                ))}
              </select>

              {/* Entity Type Filter */}
              <select
                aria-label="Lọc theo đối tượng tác động"
                value={selectedEntity}
                onChange={e => setSelectedEntity(e.target.value as LogEntityFilter)}
                className="admin-logs-select"
              >
                <option value="ALL">Tất cả đối tượng</option>
                {availableEntityOptions.map(opt => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}{opt.countOnPage > 0 ? ` (${opt.countOnPage})` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ fontSize: 13, color: 'var(--admin-muted)', fontWeight: 550, whiteSpace: 'nowrap' }}>
                Hiển thị <strong>{filteredLogs.length}</strong> / {logs.length} bản ghi (Trang {activePage}/{calculatedTotalPages})
              </div>
              <span
                style={{
                  fontSize: 11,
                  background: '#f1f5f9',
                  color: '#475569',
                  padding: '3px 8px',
                  borderRadius: 6,
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                }}
                title="API máy chủ phân trang theo thời gian; các bộ lọc áp dụng trên trang hiện tại đang tải."
              >
                Bộ lọc trang hiện tại
              </span>
              {hasActiveFilters && (
                <AdminButton
                  variant="secondary"
                  onClick={handleResetFilters}
                  aria-label="Đặt lại bộ lọc tìm kiếm"
                >
                  <RotateCcw size={15} aria-hidden="true" />
                  Đặt lại lọc
                </AdminButton>
              )}
              <AdminButton
                variant="secondary"
                onClick={handleRefresh}
                loading={loading}
                aria-label="Làm mới nhật ký kiểm toán"
              >
                <RefreshCw size={15} aria-hidden="true" />
                Làm mới
              </AdminButton>
            </div>
          </div>

          {/* Master Table */}
          <div className="admin-logs-card">
            {loading ? (
              <div style={{ padding: '40px 20px' }}>
                <AdminState
                  compact
                  kind="empty"
                  title="Đang tải nhật ký kiểm toán..."
                  description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
                />
              </div>
            ) : errorMessage ? (
              <div style={{ padding: '40px 20px' }}>
                <AdminState
                  compact
                  kind="error"
                  title="Không thể tải nhật ký"
                  description={errorMessage}
                  action={
                    <AdminButton variant="secondary" onClick={handleRefresh}>
                      Thử lại
                    </AdminButton>
                  }
                />
              </div>
            ) : filteredLogs.length === 0 ? (
              <div style={{ padding: '40px 20px' }}>
                <AdminState
                  compact
                  kind="empty"
                  title={logs.length === 0 ? 'Chưa có bản ghi nhật ký nào' : `Không tìm thấy bản ghi phù hợp trên trang ${activePage}`}
                  description={
                    logs.length === 0
                      ? 'Hiện tại hệ thống chưa ghi nhận bản ghi nhật ký kiểm toán nào trong cơ sở dữ liệu.'
                      : `Trong số ${logs.length} bản ghi của trang ${activePage} không có kết quả khớp với điều kiện lọc hiện tại. Bộ lọc đang áp dụng trên trang đang tải do hệ thống phân trang máy chủ theo thời gian. Bạn có thể đặt lại bộ lọc hoặc chuyển sang các trang khác.`
                  }
                  action={
                    <AdminButton variant="secondary" onClick={handleResetFilters}>
                      Đặt lại bộ lọc
                    </AdminButton>
                  }
                />
              </div>
            ) : (
              <div className="admin-logs-table-wrap">
                <table className="admin-logs-table" aria-label="Bảng danh sách nhật ký kiểm toán">
                  <thead>
                    <tr>
                      <th scope="col" style={{ width: '140px' }}>Mã Log</th>
                      <th scope="col" style={{ width: '180px' }}>Thời gian ghi nhận</th>
                      <th scope="col" style={{ width: '250px' }}>Người thực hiện</th>
                      <th scope="col" style={{ width: '200px' }}>Hành động</th>
                      <th scope="col">Đối tượng tác động</th>
                      <th scope="col" style={{ width: '80px', textAlign: 'center' }}>Chi tiết</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedLogs.map(log => {
                      const meta = getActionMeta(log.action);
                      const isSelected = selectedLog?.id === log.id;
                      const initials = (log.actorName || log.actorUserId || 'HT')
                        .split(' ')
                        .filter(Boolean)
                        .slice(-2)
                        .map(w => w[0])
                        .join('')
                        .toUpperCase();

                      return (
                        <tr
                          key={log.id}
                          className="admin-logs-row"
                          data-active={isSelected}
                          onClick={() => handleOpenDetail(log)}
                          tabIndex={0}
                          role="button"
                          onKeyDown={e => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault();
                              handleOpenDetail(log);
                            }
                          }}
                          aria-label={`Xem chi tiết bản ghi ${log.id}, hành động ${meta.label}`}
                        >
                          <td className="admin-log-id-cell" title={log.id}>
                            #{formatDisplayCode(log.id, 'LOG')}
                          </td>
                          <td className="admin-log-time-cell">
                            <strong>{formatDateTimeVN(log.createdAt)}</strong>
                            <small>Giờ Việt Nam (ICT)</small>
                          </td>
                          <td>
                            <div className="admin-log-actor">
                              <span className="admin-actor-avatar" aria-hidden="true">
                                {initials}
                              </span>
                              <div className="admin-actor-info">
                                <strong>{log.actorName || 'Hệ thống'}</strong>
                                <small>{log.actorEmail || (log.actorUserId ? log.actorUserId.substring(0, 18) + '...' : 'Tự động (System)')}</small>
                              </div>
                            </div>
                          </td>
                          <td>
                            <AdminBadge tone={meta.tone}>
                              {meta.label}
                            </AdminBadge>
                          </td>
                          <td>
                            <div className="admin-log-entity-badge">
                              <span>{ENTITY_LABELS[log.entityType] || log.entityType}:</span>
                              <span className="admin-log-entity-id" title={log.entityId}>
                                #{formatDisplayCode(log.entityId, log.entityType === 'ORDER' || log.entityType === 'MASTER_ORDER' ? 'ORD' : log.entityType === 'SETTLEMENT' ? 'STL' : 'ENT')}
                              </span>
                            </div>
                          </td>
                          <td style={{ textAlign: 'center' }}>
                            <button
                              type="button"
                              className="admin-action-icon-btn"
                              aria-label={`Xem chi tiết nhật ký #${log.id}`}
                              title="Xem chi tiết"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleOpenDetail(log);
                              }}
                            >
                              <Eye size={16} aria-hidden="true" />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {!loading && !errorMessage && totalElements > 0 && (
              <AdminPagination
                currentPage={activePage}
                totalPages={calculatedTotalPages}
                totalRecords={demo ? filteredLogs.length : totalElements}
                pageSize={PAGE_SIZE}
                recordLabel="bản ghi nhật ký"
                onPageChange={setCurrentPage}
              />
            )}
          </div>
        </div>
      )}

      {/* Detail Drawer */}
      <AdminDialog
        open={Boolean(selectedLog)}
        variant="drawer"
        title={selectedLog ? `Chi tiết kiểm toán: #${formatDisplayCode(selectedLog.id, 'LOG')}` : ''}
        description={selectedLog ? `Mã bản ghi hệ thống: ${selectedLog.id}` : "Bản ghi lưu vết hoạt động quản trị trên hệ thống DANASEA."}
        onClose={() => setSelectedLog(null)}
        footer={
          <AdminButton variant="secondary" onClick={() => setSelectedLog(null)}>
            Đóng
          </AdminButton>
        }
      >
        {selectedLog && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Header badges */}
            <div className="admin-log-drawer-header">
              <AdminBadge tone={getActionMeta(selectedLog.action).tone}>
                {getActionMeta(selectedLog.action).label}
              </AdminBadge>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span className="admin-config-key" title={selectedLog.id}>
                  #{formatDisplayCode(selectedLog.id, 'LOG')}
                </span>
                <button
                  type="button"
                  onClick={() => handleCopyText(selectedLog.id, 'UUID bản ghi')}
                  title="Sao chép UUID bản ghi"
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 2, color: 'var(--admin-muted)' }}
                >
                  <Copy size={12} />
                </button>
              </div>
            </div>

            {/* 1. Core Audit Context */}
            <section className="admin-detail-section">
              <h3 style={{ fontSize: 13, fontWeight: 650, color: 'var(--admin-ink)', marginBottom: 12 }}>
                1. Thông tin thao tác
              </h3>
              <dl className="admin-detail-grid">
                <div>
                  <dt>Người thực hiện</dt>
                  <dd>
                    <strong>{selectedLog.actorName || (selectedLog.actorUserId ? `User ${selectedLog.actorUserId.substring(0, 8)}` : 'Hệ thống tự động')}</strong>
                    <div style={{ fontSize: 12, color: 'var(--admin-muted)' }}>
                      {selectedLog.actorEmail || (selectedLog.actorUserId ? selectedLog.actorUserId : 'Tác vụ ngầm hệ thống')}
                    </div>
                  </dd>
                </div>
                <div>
                  <dt>Mã định danh User</dt>
                  <dd className="admin-log-entity-id">{selectedLog.actorUserId || 'Tự động (System)'}</dd>
                </div>
                <div>
                  <dt>Thời gian thực hiện</dt>
                  <dd>
                    {formatDateTimeVN(selectedLog.createdAt)}
                    <small style={{ display: 'block', color: 'var(--admin-muted)', fontSize: 11 }}>
                      ISO: {selectedLog.createdAt}
                    </small>
                  </dd>
                </div>
                <div>
                  <dt>Hành động ghi nhận</dt>
                  <dd>
                    <strong>{getActionMeta(selectedLog.action).label}</strong>
                    <small style={{ display: 'block', color: 'var(--admin-muted)', fontSize: 11, fontFamily: 'monospace' }}>
                      {selectedLog.action}
                    </small>
                  </dd>
                </div>
                <div>
                  <dt>Đối tượng tác động</dt>
                  <dd>
                    <span style={{ fontWeight: 600 }}>
                      {ENTITY_LABELS[selectedLog.entityType] || selectedLog.entityType}
                    </span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
                      <span className="admin-log-entity-id" title={selectedLog.entityId}>
                        #{formatDisplayCode(selectedLog.entityId, selectedLog.entityType === 'ORDER' || selectedLog.entityType === 'MASTER_ORDER' ? 'ORD' : selectedLog.entityType === 'SETTLEMENT' ? 'STL' : 'ENT')}
                      </span>
                      <button
                        type="button"
                        onClick={() => handleCopyText(selectedLog.entityId, 'UUID đối tượng tác động')}
                        title="Sao chép UUID đối tượng tác động"
                        style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 2, color: 'var(--admin-muted)' }}
                      >
                        <Copy size={12} />
                      </button>
                    </div>
                  </dd>
                </div>
              </dl>
            </section>

            {/* 2. Human-Readable Outcome / Changes */}
            <section className="admin-detail-section">
              <h3 style={{ fontSize: 13, fontWeight: 650, color: 'var(--admin-ink)', marginBottom: 12 }}>
                2. Thông tin thay đổi &amp; Kết quả thực hiện
              </h3>
              {auditOutcomes.length === 0 ? (
                <p style={{ margin: 0, fontSize: 12.5, color: 'var(--admin-muted)', fontStyle: 'italic' }}>
                  Không có dữ liệu chi tiết kèm theo thao tác này.
                </p>
              ) : (
                <div className="admin-outcome-list">
                  {auditOutcomes.map(item => (
                    <div className="admin-outcome-item" key={item.key}>
                      <span className="admin-outcome-label">{item.label}:</span>
                      <span className="admin-outcome-val" data-highlight={item.highlight}>
                        {item.value}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* 3. Collapsible Technical JSON (Default collapsed, sanitized) */}
            <section className="admin-technical-section">
              <button
                type="button"
                className="admin-technical-trigger"
                onClick={() => setShowTechnicalJson(prev => !prev)}
                aria-expanded={showTechnicalJson}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Code2 size={16} style={{ color: 'var(--admin-primary)' }} aria-hidden="true" />
                  Dữ liệu kỹ thuật (JSON)
                </span>
                <ChevronDown
                  size={16}
                  style={{
                    transform: showTechnicalJson ? 'rotate(180deg)' : 'none',
                    transition: 'transform var(--admin-motion-fast) var(--admin-ease)',
                  }}
                  aria-hidden="true"
                />
              </button>

              {showTechnicalJson && (
                <div style={{ marginTop: 10 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                    <small style={{ color: 'var(--admin-muted)', fontSize: 11.5 }}>
                      Dữ liệu kỹ thuật phục vụ đối chiếu hệ thống (đã qua bộ lọc an toàn)
                    </small>
                    <button
                      type="button"
                      className="admin-copy-json-btn"
                      onClick={() => handleCopyMetadata(selectedLog.metadata)}
                      aria-label="Sao chép JSON kỹ thuật"
                    >
                      {copied ? <Check size={13} /> : <Copy size={13} />}
                      <span>{copied ? 'Đã sao chép' : 'Sao chép JSON'}</span>
                    </button>
                  </div>

                  <div className="admin-log-json-box">
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                      {JSON.stringify(sanitizeMetadata(selectedLog.metadata), null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </section>

            {/* 4. Read-Only Scope Note */}
            <div className="admin-detail-note" style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
              <Shield size={18} style={{ color: 'var(--admin-primary)', flexShrink: 0, marginTop: 2 }} aria-hidden="true" />
              <div>
                <strong>Giao diện chỉ xem (Read-only):</strong>
                <p style={{ margin: '3px 0 0', fontSize: 11.5, color: '#3f6270' }}>
                  Trang quản trị chỉ hỗ trợ tra cứu và theo dõi lịch sử thao tác, không hỗ trợ chỉnh sửa hoặc xóa bỏ bản ghi kiểm toán trên giao diện.
                </p>
              </div>
            </div>
          </div>
        )}
      </AdminDialog>

      {/* Toast Notice */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
    </main>
  );
}

export default Logs;
