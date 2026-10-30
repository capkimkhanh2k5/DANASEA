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
  AdminPreviewBanner,
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

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminAuditLogService.getAuditLogs(0, 50)
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
  }, [demo]);

  // Filtered data
  const filteredLogs = useMemo(() => {
    return filterAuditLogs(logs, search, selectedAction, selectedEntity);
  }, [logs, search, selectedAction, selectedEntity]);

  // Statistics
  const stats = useMemo(() => {
    const total = logs.length;
    const approvals = logs.filter(l => l.action.includes('APPROVE') || l.action.includes('VERIFY')).length;
    const resolutions = logs.filter(l => l.action.includes('RESOLVE') || l.action.includes('LOCK')).length;
    return { total, approvals, resolutions };
  }, [logs]);

  // Reset all filters
  const handleResetFilters = () => {
    setSearch('');
    setSelectedAction('ALL');
    setSelectedEntity('ALL');
    setNotice('Đã đặt lại toàn bộ bộ lọc nhật ký.');
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
      {/* Demo Banner */}
      <AdminPreviewBanner demo={demo} href="/admin/logs?demo=1" />

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
          {/* Summary KPI Strip: 3 real metrics, ungrounded 'Bảo mật dữ liệu' removed */}
          <section
            className="admin-workspace-summary"
            aria-label="Thống kê nhanh nhật ký"
            style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}
          >
            <div className="admin-workspace-metric">
              <span>Tổng số bản ghi lưu trữ</span>
              <strong>{stats.total}</strong>
              <small>Giao diện chỉ xem · Toàn thời gian</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Hồ sơ &amp; Quyết toán đã duyệt</span>
              <strong style={{ color: 'var(--admin-primary)' }}>{stats.approvals}</strong>
              <small>Xác minh đối tác &amp; lệnh chi giải ngân</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Xử lý kỷ luật &amp; Tranh chấp</span>
              <strong style={{ color: '#b45309' }}>{stats.resolutions}</strong>
              <small>Phân xử khiếu nại &amp; tài khoản tạm khóa</small>
            </div>
          </section>

          {/* Filter Toolbar */}
          <div className="admin-logs-toolbar" role="search" aria-label="Bộ lọc tìm kiếm nhật ký">
            <div className="admin-logs-filters">
              <div className="admin-search-field admin-logs-search">
                <Search size={15} aria-hidden="true" />
                <input
                  type="text"
                  placeholder="Tìm theo mã log, tên người thực hiện, đối tượng..."
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
                <option value="ALL">Tất cả hành động ({logs.length})</option>
                <option value="APPROVE_PAYOUT">Duyệt quyết toán (APPROVE_PAYOUT)</option>
                <option value="VERIFY_VENDOR">Xác minh đối tác (VERIFY_VENDOR)</option>
                <option value="RESOLVE_DISPUTE">Giải quyết khiếu nại (RESOLVE_DISPUTE)</option>
                <option value="UPDATE_SYSTEM_CONFIG">Cập nhật cấu hình (UPDATE_SYSTEM_CONFIG)</option>
                <option value="LOCK_USER_ACCOUNT">Khóa tài khoản (LOCK_USER_ACCOUNT)</option>
              </select>

              {/* Entity Type Filter */}
              <select
                aria-label="Lọc theo đối tượng tác động"
                value={selectedEntity}
                onChange={e => setSelectedEntity(e.target.value as LogEntityFilter)}
                className="admin-logs-select"
              >
                <option value="ALL">Tất cả đối tượng</option>
                <option value="PayoutRequest">Quyết toán (PayoutRequest)</option>
                <option value="Vendor">Đối tác (Vendor)</option>
                <option value="Dispute">Khiếu nại (Dispute)</option>
                <option value="SystemConfig">Cấu hình (SystemConfig)</option>
                <option value="User">Người dùng (User)</option>
              </select>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ fontSize: 13, color: 'var(--admin-muted)', fontWeight: 550, whiteSpace: 'nowrap' }}>
                Hiển thị <strong>{filteredLogs.length}</strong> / {logs.length} bản ghi
              </div>
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
                onClick={() => {
                  setNotice('Đã làm mới dữ liệu nhật ký kiểm toán.');
                }}
                aria-label="Làm mới nhật ký kiểm toán"
              >
                <RefreshCw size={15} aria-hidden="true" />
                Làm mới
              </AdminButton>
            </div>
          </div>

          {/* Master Table: Metadata column removed, balanced widths, clickable row */}
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
                />
              </div>
            ) : filteredLogs.length === 0 ? (
              <div style={{ padding: '40px 20px' }}>
                <AdminState
                  compact
                  kind="empty"
                  title="Không tìm thấy nhật ký kiểm toán phù hợp"
                  description={logs.length === 0 ? "Hiện tại hệ thống chưa ghi nhận bản ghi nhật ký kiểm toán nào." : "Thử thay đổi từ khóa tìm kiếm hoặc đặt lại các bộ lọc."}
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
                      <th scope="col" style={{ width: '90px', textAlign: 'right' }}>Chi tiết</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredLogs.map(log => {
                      const meta = getActionMeta(log.action);
                      const isSelected = selectedLog?.id === log.id;
                      const initials = (log.actorName || log.actorUserId || 'AD')
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
                          <td className="admin-log-id-cell">
                            {log.id}
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
                                <strong>{log.actorName || log.actorUserId}</strong>
                                <small>{log.actorEmail || log.actorUserId}</small>
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
                              <span className="admin-log-entity-id">#{log.entityId}</span>
                            </div>
                          </td>
                          <td style={{ textAlign: 'right' }}>
                            <span className="admin-queue-action" aria-hidden="true">
                              <Eye size={13} />
                              Xem
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Detail Drawer */}
      <AdminDialog
        open={Boolean(selectedLog)}
        variant="drawer"
        title={selectedLog ? `Chi tiết kiểm toán: ${selectedLog.id}` : ''}
        description="Bản ghi lưu vết hoạt động quản trị trên hệ thống DANASEA."
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
                {getActionMeta(selectedLog.action).label} ({selectedLog.action})
              </AdminBadge>
              <span className="admin-config-key">{selectedLog.id}</span>
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
                    <strong>{selectedLog.actorName || 'Quản trị viên'}</strong>
                    <div style={{ fontSize: 12, color: 'var(--admin-muted)' }}>
                      {selectedLog.actorEmail || selectedLog.actorUserId}
                    </div>
                  </dd>
                </div>
                <div>
                  <dt>Mã định danh User</dt>
                  <dd className="admin-log-entity-id">{selectedLog.actorUserId}</dd>
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
                  <dt>Đối tượng tác động</dt>
                  <dd>
                    <span style={{ fontWeight: 600 }}>
                      {ENTITY_LABELS[selectedLog.entityType] || selectedLog.entityType}
                    </span>
                    <div className="admin-log-entity-id">#{selectedLog.entityId}</div>
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

            {/* 4. Read-Only Scope Note (No unverified WORM claims) */}
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
