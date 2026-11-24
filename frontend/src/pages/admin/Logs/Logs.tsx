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

// Active Backend actions that currently produce audit logs
const ACTIVE_BE_ACTIONS: { value: LogActionFilter; label: string }[] = [
  { value: 'RECONCILE_PAYMENT_PENDING', label: 'Đối soát thanh toán (Chờ xác nhận)' },
  { value: 'RECONCILE_PAYMENT_SUCCESS', label: 'Đối soát thanh toán thành công' },
  { value: 'RECONCILE_PAYMENT_FAILED', label: 'Đối soát thanh toán thất bại' },
  { value: 'RECONCILE_PAYMENT_SKIPPED', label: 'Bỏ qua đối soát thanh toán' },
  { value: 'PARTNER_APPLICATION_APPROVAL', label: 'Duyệt đơn đăng ký đối tác' },
  { value: 'PARTNER_APPLICATION_REJECTION', label: 'Từ chối đơn đăng ký đối tác' },
  { value: 'VENDOR_APPROVAL', label: 'Phê duyệt hồ sơ đối tác' },
  { value: 'VENDOR_REJECTION', label: 'Từ chối hồ sơ đối tác' },
  { value: 'VENDOR_DOCUMENT_APPROVED', label: 'Duyệt chứng từ đối tác' },
  { value: 'VENDOR_DOCUMENT_REJECTED', label: 'Từ chối chứng từ đối tác' },
  { value: 'VENDOR_PROFILE_RESUBMITTED', label: 'Nộp lại hồ sơ đối tác' },
  { value: 'SERVICE_APPROVED', label: 'Phê duyệt dịch vụ' },
  { value: 'SERVICE_REJECTED', label: 'Từ chối dịch vụ' },
  { value: 'FINALIZE_SETTLEMENT', label: 'Chốt kỳ quyết toán' },
  { value: 'PAYOUT_REQUEST_APPROVED', label: 'Phê duyệt yêu cầu chi trả' },
  { value: 'PAYOUT_REQUEST_REJECTED', label: 'Từ chối yêu cầu chi trả' },
  { value: 'PAYOUT_REQUEST_PAID', label: 'Đánh dấu đã chi trả' },
  { value: 'DISPUTE_RESOLVED', label: 'Giải quyết khiếu nại' },
  { value: 'SYSTEM_CONFIG_UPDATED', label: 'Cập nhật cấu hình hệ thống' },
  { value: 'COMMISSION_RULE_UPDATED', label: 'Cập nhật quy tắc hoa hồng' },
  { value: 'REFUND_MANUALLY_COMPLETED', label: 'Hoàn tiền thủ công thành công' },
  { value: 'USER_LOCKED', label: 'Khóa tài khoản' },
  { value: 'USER_UNLOCKED', label: 'Mở khóa tài khoản' },
  { value: 'CHANGE_PASSWORD', label: 'Thay đổi mật khẩu' },
  { value: 'PASSWORD_RESET', label: 'Đặt lại mật khẩu' },
  { value: 'FLASH_SALE_CREATED', label: 'Tạo chương trình Flash Sale' },
  { value: 'FLASH_SALE_APPROVED', label: 'Phê duyệt Flash Sale' },
  { value: 'FLASH_SALE_CANCELLED', label: 'Hủy Flash Sale' },
  { value: 'COMPLETE_SUB_ORDER', label: 'Hoàn tất đơn dịch vụ' },
  { value: 'COMPLETE_SUB_ORDER_NO_SHOW', label: 'Đơn dịch vụ khách vắng mặt (No-show)' },
  { value: 'DISPATCH_OVERDUE_SLOT', label: 'Điều phối khung giờ quá hạn' },
  { value: 'EARLY_DISPATCH_SLOT', label: 'Điều phối khung giờ sớm' },
];

// Planned actions that do not yet emit backend audit logs
const PLANNED_ACTIONS: { value: LogActionFilter; label: string }[] = [
  { value: 'VERIFY_VENDOR', label: 'Xác minh đối tác (Chưa có log BE)' },
  { value: 'PROCESS_REFUND', label: 'Xử lý hoàn tiền (Chưa có log BE)' },
  { value: 'CREATE_PROMOTION', label: 'Tạo mã khuyến mãi (Chưa có log BE)' },
  { value: 'UPDATE_PROMOTION', label: 'Cập nhật khuyến mãi (Chưa có log BE)' },
  { value: 'ADMIN_LOGIN', label: 'Đăng nhập quản trị (Chưa có log BE)' },
];

// Standard uppercase entity types supported by Backend
const ACTIVE_BE_ENTITIES: { value: LogEntityFilter; label: string }[] = [
  { value: 'PAYMENT', label: 'Giao dịch thanh toán' },
  { value: 'PARTNER_APPLICATION', label: 'Đơn đăng ký đối tác' },
  { value: 'VENDOR', label: 'Hồ sơ đối tác' },
  { value: 'VENDOR_DOCUMENT', label: 'Chứng từ đối tác' },
  { value: 'SERVICE', label: 'Dịch vụ trải nghiệm' },
  { value: 'SETTLEMENT', label: 'Kỳ quyết toán' },
  { value: 'PAYOUT_REQUEST', label: 'Yêu cầu chi trả' },
  { value: 'DISPUTE', label: 'Khiếu nại khách hàng' },
  { value: 'SYSTEM_CONFIG', label: 'Cấu hình hệ thống' },
  { value: 'COMMISSION_RULE', label: 'Quy tắc hoa hồng' },
  { value: 'REFUND', label: 'Yêu cầu hoàn tiền' },
  { value: 'USER', label: 'Tài khoản người dùng' },
  { value: 'ORDER', label: 'Đơn hàng' },
  { value: 'FLASH_SALE', label: 'Flash Sale' },
];

// Planned entity types (not yet actively logged)
const PLANNED_ENTITIES: { value: LogEntityFilter; label: string }[] = [
  { value: 'PROMOTION', label: 'Mã khuyến mãi (Chưa có log BE)' },
];

export function Logs() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // State
  const [logs, setLogs] = useState<AdminAuditLogItem[]>(() => demo ? MOCK_ADMIN_AUDIT_LOGS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
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

  // Debounce search input (350ms) to prevent request flooding
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(prev => {
        if (prev !== search) {
          setCurrentPage(1);
          return search;
        }
        return prev;
      });
    }, 350);
    return () => clearTimeout(handler);
  }, [search]);

  const handleActionChange = (action: LogActionFilter) => {
    setSelectedAction(action);
    setCurrentPage(1);
  };

  const handleEntityChange = (entity: LogEntityFilter) => {
    setSelectedEntity(entity);
    setCurrentPage(1);
  };

  useEffect(() => {
    if (demo) {
      return;
    }

    const abortController = new AbortController();
    setLoading(true);
    setErrorMessage(null);

    const filters = {
      action: selectedAction !== 'ALL' ? selectedAction : undefined,
      entityType: selectedEntity !== 'ALL' ? selectedEntity : undefined,
      search: debouncedSearch.trim() ? debouncedSearch.trim() : undefined,
    };

    adminAuditLogService.getAuditLogs(currentPage - 1, PAGE_SIZE, filters, abortController.signal)
      .then(res => {
        const mapped: AdminAuditLogItem[] = (res.content || []).map(l => ({
          id: l.id,
          actorUserId: l.actorUserId || '',
          actorName: l.actorName || (l.actorUserId ? 'Không xác định' : 'Hệ thống'),
          actorEmail: l.actorEmail || '',
          action: l.action,
          entityType: l.entityType,
          entityId: l.entityId || '',
          metadata: l.metadata || '{}',
          createdAt: l.timestamp,
          updatedAt: l.timestamp,
        }));
        setLogs(mapped);
        setTotalPages(res.totalPages || 0);
        setTotalElements(res.totalElements || 0);
        setLoading(false);
      })
      .catch(err => {
        if (abortController.signal.aborted) {
          return;
        }
        const info = getAdminAuditLogError(err);
        setErrorMessage(info.message);
        setLoading(false);
      });

    return () => {
      abortController.abort();
    };
  }, [demo, currentPage, debouncedSearch, selectedAction, selectedEntity, refreshKey]);

  // Action options categorized into active Backend actions and planned actions
  const activeActionOptions = useMemo(() => {
    const activeMap = new Map<string, string>();
    ACTIVE_BE_ACTIONS.forEach(a => activeMap.set(a.value, a.label));
    const plannedSet = new Set(PLANNED_ACTIONS.map(p => p.value));

    // Dynamic inclusion of any action appearing in loaded logs
    logs.forEach(l => {
      if (!activeMap.has(l.action) && !plannedSet.has(l.action)) {
        activeMap.set(l.action, getActionMeta(l.action).label);
      }
    });

    return Array.from(activeMap.entries())
      .map(([value, label]) => ({
        value: value as LogActionFilter,
        label,
      }))
      .sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  }, [logs]);

  const plannedActionOptions = useMemo(() => {
    return [...PLANNED_ACTIONS].sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  }, []);

  // Entity options categorized into active Backend entities and planned entities
  const activeEntityOptions = useMemo(() => {
    const activeMap = new Map<string, string>();
    ACTIVE_BE_ENTITIES.forEach(e => activeMap.set(e.value, e.label));
    const plannedSet = new Set(PLANNED_ENTITIES.map(p => p.value));

    logs.forEach(l => {
      const upper = l.entityType.toUpperCase();
      if (!activeMap.has(upper) && !plannedSet.has(upper)) {
        activeMap.set(upper, ENTITY_LABELS[upper] || ENTITY_LABELS[l.entityType] || l.entityType);
      }
    });

    return Array.from(activeMap.entries())
      .map(([value, label]) => ({
        value: value as LogEntityFilter,
        label,
      }))
      .sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  }, [logs]);

  const plannedEntityOptions = useMemo(() => {
    return [...PLANNED_ENTITIES].sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  }, []);

  // Filtered data (Demo mode filters in-memory; live mode uses server-side filtered logs)
  const filteredLogs = useMemo(() => {
    if (demo) {
      return filterAuditLogs(logs, search, selectedAction, selectedEntity);
    }
    return logs;
  }, [demo, logs, search, selectedAction, selectedEntity]);

  const calculatedTotalPages = demo
    ? (Math.ceil(filteredLogs.length / PAGE_SIZE) || 1)
    : Math.max(1, totalPages);
  const activePage = Math.min(Math.max(1, currentPage), calculatedTotalPages);

  const paginatedLogs = useMemo(() => {
    if (!demo) return filteredLogs;
    const start = (activePage - 1) * PAGE_SIZE;
    return filteredLogs.slice(start, start + PAGE_SIZE);
  }, [demo, filteredLogs, activePage]);

  // Transparent statistics: Global total vs. page-level operational indicators
  const stats = useMemo(() => {
    const totalStored = demo ? filteredLogs.length : totalElements;
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
  }, [demo, filteredLogs.length, totalElements, logs]);

  // Reset all filters
  const handleResetFilters = () => {
    setSearch('');
    setDebouncedSearch('');
    setSelectedAction('ALL');
    setSelectedEntity('ALL');
    setCurrentPage(1);
    setNotice('Đã đặt lại toàn bộ bộ lọc và từ khóa tìm kiếm.');
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
              <span>{hasActiveFilters ? 'Số bản ghi khớp bộ lọc' : 'Tổng số bản ghi lưu trữ'}</span>
              <strong>{stats.totalStored.toLocaleString('vi-VN')}</strong>
              <small>{hasActiveFilters ? 'Toàn hệ thống (Server-side)' : 'Toàn hệ thống CSDL · Toàn thời gian'}</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Hồ sơ &amp; Quyết toán đã duyệt</span>
              <strong style={{ color: 'var(--admin-primary)' }}>{stats.pageApprovals}</strong>
              <small>Trong {logs.length} bản ghi trang {activePage}</small>
            </div>
            <div className="admin-workspace-metric">
              <span>Xử lý kỷ luật &amp; Đối soát</span>
              <strong style={{ color: '#b45309' }}>{stats.pageResolutions}</strong>
              <small>Trong {logs.length} bản ghi trang {activePage}</small>
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
                    onClick={() => {
                      setSearch('');
                      setDebouncedSearch('');
                      setCurrentPage(1);
                    }}
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
                onChange={e => handleActionChange(e.target.value as LogActionFilter)}
                className="admin-logs-select"
              >
                <option value="ALL">Tất cả hành động</option>
                <optgroup label="Hành động đã hỗ trợ ghi nhận (Backend)">
                  {activeActionOptions.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Hành động dự kiến (Chưa có nguồn log BE)">
                  {plannedActionOptions.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </optgroup>
              </select>

              {/* Entity Type Filter */}
              <select
                aria-label="Lọc theo đối tượng tác động"
                value={selectedEntity}
                onChange={e => handleEntityChange(e.target.value as LogEntityFilter)}
                className="admin-logs-select"
              >
                <option value="ALL">Tất cả đối tượng</option>
                <optgroup label="Đối tượng đã hỗ trợ ghi nhận (Backend)">
                  {activeEntityOptions.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="Đối tượng dự kiến (Chưa có log BE)">
                  {plannedEntityOptions.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </optgroup>
              </select>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ fontSize: 13, color: 'var(--admin-muted)', fontWeight: 550, whiteSpace: 'nowrap' }}>
                Hiển thị <strong>{logs.length}</strong> bản ghi (Trang {activePage}/{calculatedTotalPages}) · Tổng cộng {totalElements.toLocaleString('vi-VN')} bản ghi
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
                  title={hasActiveFilters ? 'Không tìm thấy nhật ký phù hợp với bộ lọc hiện tại' : 'Chưa có bản ghi nhật ký nào'}
                  description={
                    hasActiveFilters
                      ? 'Không tìm thấy bản ghi nhật ký kiểm toán nào khớp với bộ lọc hoặc từ khóa tìm kiếm. Bạn có thể thay đổi tiêu chí hoặc đặt lại bộ lọc.'
                      : 'Hiện tại hệ thống chưa ghi nhận bản ghi nhật ký kiểm toán nào trong cơ sở dữ liệu.'
                  }
                  action={
                    hasActiveFilters ? (
                      <AdminButton variant="secondary" onClick={handleResetFilters}>
                        Đặt lại bộ lọc
                      </AdminButton>
                    ) : undefined
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
        description={selectedLog ? `Mã bản ghi hệ thống: #${formatDisplayCode(selectedLog.id, 'LOG')}` : "Bản ghi lưu vết hoạt động quản trị trên hệ thống DANASEA."}
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
                    <strong>{selectedLog.actorName || (selectedLog.actorUserId ? 'Không xác định' : 'Hệ thống')}</strong>
                    <div style={{ fontSize: 12, color: 'var(--admin-muted)' }}>
                      {selectedLog.actorEmail || (selectedLog.actorUserId ? '' : 'Tác vụ ngầm hệ thống')}
                    </div>
                  </dd>
                </div>
                <div>
                  <dt>Mã định danh User</dt>
                  <dd className="admin-log-entity-id" title={selectedLog.actorUserId}>{selectedLog.actorUserId ? '#' + formatDisplayCode(selectedLog.actorUserId, 'USER') : 'Tự động (System)'}</dd>
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
