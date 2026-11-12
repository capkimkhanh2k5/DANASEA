import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Eye, Search, UserCheck, RefreshCw, XCircle } from 'lucide-react';
import { adminPartnerApplicationService, getAdminPartnerApplicationError } from '../../../services/adminPartnerApplicationService';
import type { PartnerApplicationResponse } from '../../../types/partnerApplication';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminFilterTabs, AdminNotice, AdminPagination } from '../../../components/admin/AdminWorkspace';
import { filterApplications, formatDate, getStatusTone, getStatusLabel, MOCK_PARTNER_APPLICATIONS } from './roleApplicationsModel';
import { RoleApplicationDetail } from './RoleApplicationDetail';
import './RoleApplications.css';

export function RoleApplications() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  const [applications, setApplications] = useState<PartnerApplicationResponse[]>(() => demo ? MOCK_PARTNER_APPLICATIONS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [selectedApp, setSelectedApp] = useState<PartnerApplicationResponse | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const fetchApplications = () => {
    if (demo) {
      setApplications(MOCK_PARTNER_APPLICATIONS);
      setNotice('Đã làm mới danh sách đơn mẫu trong phiên demo.');
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    adminPartnerApplicationService.getApplications({ page: 0, size: 100 })
      .then(res => {
        setApplications(res.content || []);
        setLoading(false);
      })
      .catch(err => {
        setErrorMessage(getAdminPartnerApplicationError(err, 'Không thể tải danh sách đơn xét duyệt vai trò đối tác.'));
        setLoading(false);
      });
  };

  useEffect(() => {
    if (demo) return;
    let isMounted = true;
    adminPartnerApplicationService.getApplications({ page: 0, size: 100 })
      .then(res => {
        if (!isMounted) return;
        setApplications(res.content || []);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        setErrorMessage(getAdminPartnerApplicationError(err, 'Không thể tải danh sách đơn xét duyệt vai trò đối tác.'));
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  const filtered = useMemo(() => {
    return filterApplications(applications, search, status);
  }, [applications, search, status]);

  const countByStatus = (st: string) => applications.filter(a => a.status === st).length;

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginated = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filtered.slice(start, start + PAGE_SIZE);
  }, [filtered, activePage]);

  const resetFilters = () => {
    setSearch('');
    setStatus('all');
    setCurrentPage(1);
  };

  const handleOpenDetail = (app: PartnerApplicationResponse) => {
    setSelectedApp(app);
    setDrawerOpen(true);
  };

  const handleApprove = async (id: string) => {
    if (demo) {
      setApplications(prev => prev.map(a => a.id === id ? {
        ...a,
        status: 'APPROVED' as const,
        reviewedBy: 'Quản trị viên DANASEA',
        reviewedAt: new Date().toISOString(),
      } : a));
      if (selectedApp?.id === id) {
        setSelectedApp(prev => prev ? {
          ...prev,
          status: 'APPROVED' as const,
          reviewedBy: 'Quản trị viên DANASEA',
          reviewedAt: new Date().toISOString(),
        } : null);
      }
      setNotice(`Đã phê duyệt thành công đơn đối tác (Bản demo). Người dùng đã chuyển sang VENDOR.`);
      return;
    }
    try {
      setIsSubmitting(true);
      const updated = await adminPartnerApplicationService.approveApplication(id);
      setApplications(prev => prev.map(a => a.id === id ? updated : a));
      if (selectedApp?.id === id) {
        setSelectedApp(updated);
      }
      setNotice(`Đã phê duyệt thành công đơn đối tác "${updated.businessName}". Người dùng đã chuyển sang VENDOR.`);
    } catch (err) {
      setNotice(`Lỗi: ${getAdminPartnerApplicationError(err)}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async (id: string, reason: string) => {
    if (demo) {
      setApplications(prev => prev.map(a => a.id === id ? {
        ...a,
        status: 'REJECTED' as const,
        rejectionReason: reason,
        reviewedBy: 'Quản trị viên DANASEA',
        reviewedAt: new Date().toISOString(),
      } : a));
      if (selectedApp?.id === id) {
        setSelectedApp(prev => prev ? {
          ...prev,
          status: 'REJECTED' as const,
          rejectionReason: reason,
          reviewedBy: 'Quản trị viên DANASEA',
          reviewedAt: new Date().toISOString(),
        } : null);
      }
      setNotice(`Đã từ chối đơn đối tác với lý do rõ ràng (Bản demo).`);
      return;
    }
    try {
      setIsSubmitting(true);
      const updated = await adminPartnerApplicationService.rejectApplication(id, reason);
      setApplications(prev => prev.map(a => a.id === id ? updated : a));
      if (selectedApp?.id === id) {
        setSelectedApp(updated);
      }
      setNotice(`Đã từ chối đơn đối tác "${updated.businessName}" với lý do rõ ràng.`);
    } catch (err) {
      setNotice(`Lỗi: ${getAdminPartnerApplicationError(err)}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="admin-workspace-page admin-role-applications">
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />

      <section className="admin-workspace-card" aria-labelledby="role-app-heading">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
          <div>
            <h1 id="role-app-heading" className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-sky-600" aria-hidden="true" />
              <span>Duyệt vai trò đối tác (Quy trình 1)</span>
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">
              Thẩm định đơn đăng ký chuyển đổi từ Khách hàng (CUSTOMER) sang Nhà cung cấp (VENDOR).
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-slate-500 tabular-nums">
              {filtered.length} / {applications.length} đơn
            </span>
            <AdminButton onClick={fetchApplications} disabled={loading} className="p-2!">
              <RefreshCw size={15} className={loading ? 'admin-spin' : ''} aria-hidden="true" />
              <span className="sr-only">Làm mới dữ liệu</span>
            </AdminButton>
          </div>
        </div>

        {/* Filter Tabs */}
        <div className="mt-4">
          <AdminFilterTabs
            label="Lọc theo trạng thái xét duyệt"
            value={status}
            onChange={(val) => { setStatus(val); setCurrentPage(1); }}
            items={[
              { value: 'all', label: 'Tất cả', count: applications.length },
              { value: 'PENDING', label: 'Chờ duyệt', count: countByStatus('PENDING') },
              { value: 'APPROVED', label: 'Đã duyệt', count: countByStatus('APPROVED') },
              { value: 'REJECTED', label: 'Từ chối', count: countByStatus('REJECTED') },
            ]}
          />
        </div>

        {/* Search & Actions Toolbar */}
        <div className="admin-workspace-toolbar mt-4">
          <label className="admin-search-field">
            <Search size={18} aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setCurrentPage(1); }}
              aria-label="Tìm kiếm theo tên đối tác, MST, người thụ hưởng"
              placeholder="Tìm tên đối tác, MST, chủ tài khoản…"
            />
          </label>
          {(search || status !== 'all') && (
            <AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>
          )}
        </div>

        {/* Loading & Error States */}
        {loading && (
          <div className="py-8">
            <AdminState kind="loading" title="Đang tải danh sách đơn đối tác..." description="Hệ thống đang đồng bộ dữ liệu từ máy chủ." />
          </div>
        )}

        {errorMessage && !loading && (
          <div className="py-8">
            <AdminState
              kind="error"
              title="Không thể tải dữ liệu"
              description={errorMessage}
              action={<AdminButton onClick={fetchApplications} variant="secondary">Thử lại</AdminButton>}
            />
          </div>
        )}

        {/* Table View */}
        {!loading && !errorMessage && (
          <>
            {filtered.length === 0 ? (
              <div className="py-8">
                <AdminState
                  kind="empty"
                  title="Không tìm thấy đơn nào"
                  description={search || status !== 'all' ? 'Thử thay đổi bộ lọc tìm kiếm hoặc từ khóa.' : 'Hiện tại chưa có khách hàng nào gửi đơn xét duyệt đối tác.'}
                  action={search || status !== 'all' ? <AdminButton onClick={resetFilters}>Xem tất cả đơn</AdminButton> : undefined}
                />
              </div>
            ) : (
              <div className="admin-table-scroll mt-4" role="region" aria-label="Bảng danh sách đơn đối tác" tabIndex={0}>
                <table className="admin-workspace-table">
                  <caption className="sr-only">Danh sách đơn xin chuyển đổi vai trò sang VENDOR</caption>
                  <thead>
                    <tr>
                      <th scope="col">Doanh nghiệp / Hộ KD</th>
                      <th scope="col">Mã số thuế</th>
                      <th scope="col">Tài khoản ngân hàng</th>
                      <th scope="col">Trạng thái</th>
                      <th scope="col">Ngày gửi</th>
                      <th scope="col" className="text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginated.map((app) => (
                      <tr key={app.id} data-selected={app.id === selectedApp?.id}>
                        <td>
                          <div className="font-semibold text-slate-900">{app.businessName}</div>
                          <span className="text-[11px] text-slate-500 font-mono">User: {app.userId.slice(0, 8)}...</span>
                        </td>
                        <td>
                          <span className="font-mono text-slate-800 text-xs">{app.taxCode || 'Chưa cung cấp'}</span>
                        </td>
                        <td>
                          <div className="text-xs text-slate-800 font-semibold">{app.bankName}</div>
                          <span className="font-mono text-slate-600 text-[11px]">{app.bankAccountNumber} ({app.bankAccountHolder})</span>
                        </td>
                        <td>
                          <AdminBadge tone={getStatusTone(app.status)}>
                            {getStatusLabel(app.status)}
                          </AdminBadge>
                        </td>
                        <td>
                          <span className="text-xs text-slate-600">{formatDate(app.createdAt)}</span>
                        </td>
                        <td className="text-right">
                          <AdminButton
                            onClick={() => handleOpenDetail(app)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold"
                          >
                            <Eye size={14} aria-hidden="true" />
                            <span>Chi tiết</span>
                          </AdminButton>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {filtered.length > PAGE_SIZE && (
              <div className="mt-4 pt-4 border-t border-slate-100">
                <AdminPagination
                  currentPage={activePage}
                  totalPages={totalPages}
                  totalRecords={filtered.length}
                  pageSize={PAGE_SIZE}
                  recordLabel="đơn"
                  onPageChange={setCurrentPage}
                />
              </div>
            )}
          </>
        )}

        <div className="admin-vendors-list-footer mt-4 text-xs text-slate-500 text-center">
          Hiển thị {filtered.length} đơn {demo ? 'mẫu (Demo Mode)' : 'từ hệ thống DANASEA'} · Chi tiết thẩm định mở trong ngăn riêng, giữ nguyên danh sách và bộ lọc.
        </div>
      </section>

      {/* Right Drawer for Detail View */}
      {drawerOpen && selectedApp && (
        <div className="admin-drawer-overlay" onClick={() => setDrawerOpen(false)} role="presentation">
          <aside
            className="admin-drawer-panel"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label={`Chi tiết đơn: ${selectedApp.businessName}`}
          >
            <div className="admin-drawer-header">
              <div className="min-w-0 flex-1">
                <p className="admin-eyebrow">Thẩm định hồ sơ đối tác</p>
                <h2 className="admin-drawer-title truncate">{selectedApp.businessName}</h2>
              </div>
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                className="admin-dialog-close"
                aria-label="Đóng bảng chi tiết"
              >
                <XCircle size={20} aria-hidden="true" />
              </button>
            </div>

            <div className="admin-drawer-body">
              <RoleApplicationDetail
                application={selectedApp}
                onApprove={handleApprove}
                onReject={handleReject}
                isSubmitting={isSubmitting}
              />
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
