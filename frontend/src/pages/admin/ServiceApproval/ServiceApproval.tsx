import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { FileSearch, Filter, Search } from 'lucide-react';
import { MOCK_ADMIN_SERVICES, MOCK_ADMIN_CATEGORIES, MOCK_ADMIN_VENDORS } from '../../../data/adminMockData';
import { adminServiceApprovalService, getAdminServiceError } from '../../../services/adminServiceApprovalService';
import { adminVendorService } from '../../../services/adminVendorService';
import { adminCategoryService } from '../../../services/adminCategoryService';
import type { AdminServiceResult } from '../../../types/adminServiceApproval';
import { AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminFilterTabs, AdminNotice, AdminPagination } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  applyServiceDecision,
  filterServices,
  type ServiceDecisionKind,
  type ServiceRecord,
  type ServiceReviewState,
} from './serviceApprovalModel';
import { ServiceDetail, ServiceStatusBadge } from './ServiceDetail';
import './ServiceApproval.css';

function mapResultToRecord(
  s: AdminServiceResult,
  vendorMap?: Map<string, string>,
  categoryMap?: Map<string, string>
): ServiceRecord {
  const thumb = (s.images && s.images[0]?.url) || '';
  const vendorName =
    vendorMap?.get(s.vendorId) ||
    MOCK_ADMIN_VENDORS.find(v => v.id === s.vendorId)?.businessName ||
    (s.vendorId ? `Đối tác ${s.vendorId.substring(0, 8)}` : 'Chưa liên kết đối tác');

  const categoryName =
    categoryMap?.get(s.categoryId) ||
    MOCK_ADMIN_CATEGORIES.find(c => c.id === s.categoryId)?.name ||
    'Hoạt động biển';

  return {
    id: s.id,
    vendorId: s.vendorId,
    vendorName,
    categoryId: s.categoryId,
    categoryName,
    name: s.name,
    nameEn: s.nameEn || '',
    slug: s.slug || '',
    description: s.description || '',
    price: s.price,
    basePrice: s.price,
    durationMinutes: s.durationMinutes || 60,
    locationName: s.locationName || s.address || 'Đà Nẵng',
    address: s.address || '',
    status: (s.status === 'PUBLISHED' ? 'ACTIVE' : s.status) as unknown as ServiceRecord['status'],
    rejectionReason: s.rejectionReason || '',
    waiverContent: s.waiverContent || '',
    weatherSensitive: Boolean(s.weatherSensitive),
    minWindKmh: s.minWindKmh || 0,
    maxWaveM: s.maxWaveM || 0,
    avgRating: s.avgRating || 5.0,
    ratingCount: s.ratingCount || 0,
    viewCount: s.viewCount || 0,
    thumbnailUrl: thumb,
    createdAt: s.createdAt,
    updatedAt: s.updatedAt,
  };
}

export function ServiceApproval() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  const [review, setReview] = useState<ServiceReviewState>(() => ({
    services: demo ? MOCK_ADMIN_SERVICES : [],
    timeline: [],
  }));
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string>('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [categoryFilter, setCategoryFilter] = useState('all');

  // Decision Modals & Review State
  const [decisionKind, setDecisionKind] = useState<ServiceDecisionKind>('approve');
  const [decisionOpen, setDecisionOpen] = useState(false);
  const [adminNote, setAdminNote] = useState('Đã rà soát thông tin khai báo và điều kiện vận hành biển.');
  const [rejectReason, setRejectReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    const controller = new AbortController();

    Promise.allSettled([
      adminServiceApprovalService.getAdminServices(),
      adminVendorService.getVendors({ page: 0, size: 100 }),
      adminCategoryService.getCategories(controller.signal),
    ])
      .then(([servicesRes, vendorsRes, categoriesRes]) => {
        if (!isMounted) return;

        if (servicesRes.status === 'rejected') {
          const msg = getAdminServiceError(servicesRes.reason, 'Không thể tải danh sách dịch vụ từ máy chủ.');
          setErrorMessage(msg);
          setLoading(false);
          return;
        }

        const vendorMap = new Map<string, string>();
        if (vendorsRes.status === 'fulfilled' && vendorsRes.value?.content) {
          vendorsRes.value.content.forEach(v => {
            if (v.id && v.businessName) {
              vendorMap.set(v.id, v.businessName);
            }
          });
        }

        const categoryMap = new Map<string, string>();
        if (categoriesRes.status === 'fulfilled' && Array.isArray(categoriesRes.value)) {
          categoriesRes.value.forEach(c => {
            if (c.id && c.name) {
              categoryMap.set(c.id, c.name);
            }
          });
        }

        const mapped = servicesRes.value.map(s => mapResultToRecord(s, vendorMap, categoryMap));
        setReview({ services: mapped, timeline: [] });
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const msg = getAdminServiceError(err, 'Lỗi không xác định khi tải dữ liệu dịch vụ.');
        setErrorMessage(msg);
        setLoading(false);
      });

    return () => {
      isMounted = false;
      controller.abort();
    };
  }, [demo]);

  const visibleServices = review.services;
  const selected = visibleServices.find(service => service.id === selectedId);

  const filtered = useMemo(
    () => filterServices(visibleServices, search, status, categoryFilter),
    [visibleServices, search, status, categoryFilter]
  );

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginatedServices = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filtered.slice(start, start + PAGE_SIZE);
  }, [filtered, activePage]);

  const countByStatus = (statusValue: string) =>
    visibleServices.filter(s => s.status === statusValue).length;

  const resetFilters = () => {
    setSearch('');
    setStatus('all');
    setCategoryFilter('all');
    setCurrentPage(1);
  };

  const openServiceDrawer = (service: ServiceRecord) => {
    setSelectedId(service.id);
    setRejectReason(service.rejectionReason || '');
    setNotice(null);
    setDrawerOpen(true);
  };

  const initiateDecision = (kind: ServiceDecisionKind) => {
    setDecisionKind(kind);
    if (kind === 'reject') {
      setRejectReason(selected?.rejectionReason || '');
    }
    setDecisionOpen(true);
  };

  const confirmDecision = async () => {
    if (!selected || !decisionOpen) return;

    if (demo) {
      const action = {
        serviceId: selected.id,
        kind: decisionKind,
        reason: decisionKind === 'reject' ? rejectReason : '',
        note: adminNote,
        at: new Date().toISOString(),
      };

      setReview(prev => applyServiceDecision(prev, action, demo));
      setDecisionOpen(false);

      const message =
        decisionKind === 'approve'
          ? `Đã phê duyệt xuất bản tour "${selected.name}" trong bản demo. Dịch vụ chuyển sang ACTIVE.`
          : decisionKind === 'pause'
          ? `Đã tạm ngưng mở bán tour "${selected.name}" trong bản demo.`
          : `Đã ghi nhận từ chối tour "${selected.name}" trong bản demo.`;

      setNotice(message);
      return;
    }

    try {
      setIsSubmitting(true);
      if (decisionKind === 'approve') {
        const result = await adminServiceApprovalService.approveService(selected.id);
        const nextStatus = (result.status === 'PUBLISHED' ? 'ACTIVE' : result.status) as unknown as ServiceRecord['status'];
        setReview(prev => ({
          ...prev,
          services: prev.services.map(s => s.id === selected.id ? { ...s, status: nextStatus } : s)
        }));
        setNotice(`Đã phê duyệt xuất bản tour "${selected.name}".`);
      } else if (decisionKind === 'reject') {
        await adminServiceApprovalService.rejectService(selected.id, rejectReason);
        setReview(prev => ({
          ...prev,
          services: prev.services.map(s => s.id === selected.id ? { ...s, status: 'REJECTED' as unknown as ServiceRecord['status'], rejectionReason: rejectReason } : s)
        }));
        setNotice(`Đã từ chối duyệt tour "${selected.name}".`);
      }
      setDecisionOpen(false);
    } catch (err) {
      const msg = getAdminServiceError(err, 'Lỗi khi cập nhật trạng thái dịch vụ.');
      setNotice(msg);
      setDecisionOpen(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="admin-workspace-page admin-services">
      {/* 4. Main Service List & Workspace */}
      <section className="admin-services-list" aria-labelledby="services-list-title">
        <div className="admin-services-list-heading">
          <h2 id="services-list-title">Danh sách dịch vụ trải nghiệm biển</h2>
          <span>
            {filtered.length} / {visibleServices.length} dịch vụ
          </span>
        </div>

        {/* Status Filter Tabs */}
        <AdminFilterTabs
          label="Lọc trạng thái dịch vụ"
          value={status}
          onChange={val => {
            setStatus(val);
            setCurrentPage(1);
          }}
          items={[
            { value: 'all', label: 'Tất cả', count: visibleServices.length },
            { value: 'PENDING_APPROVAL', label: 'Chờ duyệt', count: countByStatus('PENDING_APPROVAL') },
            { value: 'ACTIVE', label: 'Đang mở bán', count: countByStatus('ACTIVE') },
            { value: 'PAUSED', label: 'Tạm ngưng', count: countByStatus('PAUSED') },
            { value: 'REJECTED', label: 'Bị từ chối', count: countByStatus('REJECTED') },
          ]}
        />

        {/* Search & Category Filter Toolbar */}
        <div className="admin-workspace-toolbar">
          <label className="admin-search-field">
            <Search size={18} aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={e => {
                setSearch(e.target.value);
                setCurrentPage(1);
              }}
              aria-label="Tìm dịch vụ theo tên, mã tour hoặc đối tác"
              placeholder="Tìm theo tên tour, mã tour hoặc tên đối tác..."
            />
          </label>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <label className="admin-field admin-services-category-filter">
              <Filter size={16} aria-hidden="true" />
              <span>Danh mục</span>
              <select
                value={categoryFilter}
                onChange={e => {
                  setCategoryFilter(e.target.value);
                  setCurrentPage(1);
                }}
                aria-label="Lọc theo danh mục biển"
              >
                <option value="all">Tất cả danh mục biển</option>
                {MOCK_ADMIN_CATEGORIES.map(category => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>

            {(search || status !== 'all' || categoryFilter !== 'all') && (
              <AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>
            )}
          </div>
        </div>

        {/* Loading & Error States */}
        {loading && (
          <AdminState
            kind="empty"
            compact
            title="Đang tải danh sách dịch vụ..."
            description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
          />
        )}
        {errorMessage && !loading && (
          <AdminState
            kind="error"
            compact
            title="Không thể tải dịch vụ"
            description={errorMessage}
          />
        )}

        {/* Data Table */}
        {!loading && !errorMessage && (
        <div className="admin-services-table-scroll" role="region" aria-label="Bảng dịch vụ biển" tabIndex={0}>
          <table className="admin-workspace-table admin-services-table">
            <caption className="sr-only">Danh sách dịch vụ trải nghiệm biển; chọn Thẩm định để xem chi tiết</caption>
            <thead>
              <tr>
                <th scope="col">Dịch vụ & Trải nghiệm</th>
                <th scope="col">Đơn vị vận hành</th>
                <th scope="col">Đơn giá niêm yết</th>
                <th scope="col">Ngưỡng vận hành khai báo</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {paginatedServices.map(service => {
                const isSelected = service.id === selectedId;
                const price = service.price ?? service.basePrice ?? 0;
                return (
                  <tr key={service.id} data-selected={isSelected}>
                    <td>
                      <div className="admin-services-identity">
                        {service.thumbnailUrl && (
                          <img
                            src={service.thumbnailUrl}
                            alt=""
                            width="52"
                            height="52"
                            className="admin-services-thumb"
                          />
                        )}
                        <div>
                          <strong>{service.name}</strong>
                          <span>
                            {service.locationName} • {service.durationMinutes} phút
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="admin-services-vendor-cell">
                        <strong>{service.vendorName || 'Chưa liên kết'}</strong>
                      </div>
                    </td>
                    <td>
                      <div className="admin-services-price-cell">
                        <strong>{price.toLocaleString('vi-VN')} ₫</strong>
                        <small>Tối đa {service.capacityPerSlot} khách/ca</small>
                      </div>
                    </td>
                    <td>
                      <div className="admin-services-safety-cell-table">
                        <strong>Sóng ≤ {service.maxWaveM}m</strong>
                        <span>Gió ≤ {service.minWindKmh} km/h</span>
                      </div>
                    </td>
                    <td>
                      <ServiceStatusBadge status={service.status} />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="admin-action-icon-btn"
                        aria-label={`Thẩm định hồ sơ ${service.name}`}
                        title="Thẩm định hồ sơ"
                        aria-haspopup="dialog"
                        aria-expanded={drawerOpen && isSelected}
                        onClick={() => openServiceDrawer(service)}
                      >
                        <FileSearch size={17} aria-hidden="true" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        )}

        {!loading && !errorMessage && filtered.length > 0 && (
          <AdminPagination
            currentPage={activePage}
            totalPages={totalPages}
            totalRecords={filtered.length}
            pageSize={PAGE_SIZE}
            recordLabel="dịch vụ"
            onPageChange={setCurrentPage}
          />
        )}

        {/* Empty State */}
        {!loading && !errorMessage && filtered.length === 0 && (
          <AdminState
            kind="empty"
            compact
            title="Không tìm thấy dịch vụ phù hợp"
            description="Hãy thử thay đổi từ khóa tìm kiếm, chọn danh mục khác hoặc đặt lại bộ lọc."
            action={<AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>}
          />
        )}

        <div className="admin-services-list-footer">
          Hiển thị {filtered.length} dịch vụ {demo ? 'biển mẫu · Chi tiết mở trong ngăn bên riêng, giữ nguyên vị trí danh sách.' : 'từ hệ thống cơ sở dữ liệu DANASEA.'}
        </div>
      </section>

      {/* Floating Notice when drawer is closed */}
      {!drawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}

      {/* 5. Service Detail Drawer */}
      <AdminDialog
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        variant="drawer"
        title={selected?.name || 'Chi tiết dịch vụ'}
        description="Hồ sơ trải nghiệm biển · Rà soát điều kiện vận hành khai báo"
        footer={
          selected && (
            <div className="admin-services-drawer-footer">
              <p>
                {demo
                  ? 'Thao tác demo chỉ lưu trong phiên này. Tải lại trang sẽ khôi phục dữ liệu mẫu ban đầu.'
                  : 'Bật thao tác demo để thử nghiệm phê duyệt. Bản xem trước hiện tại chỉ cho phép xem thông tin.'}
              </p>
              <div className="admin-services-drawer-buttons">
                {selected.status === 'ACTIVE' ? (
                  <>
                    <AdminButton
                      disabled={!demo}
                      variant="secondary"
                      onClick={() => initiateDecision('pause')}
                    >
                      Tạm ngưng mở bán
                    </AdminButton>
                    <AdminButton
                      disabled={!demo}
                      variant="primary"
                      onClick={() => initiateDecision('approve')}
                    >
                      Cập nhật phê duyệt
                    </AdminButton>
                  </>
                ) : selected.status === 'PAUSED' ? (
                  <>
                    <AdminButton
                      disabled={!demo}
                      variant="primary"
                      onClick={() => initiateDecision('approve')}
                    >
                      Kích hoạt mở bán lại
                    </AdminButton>
                  </>
                ) : (
                  <>
                    <AdminButton
                      disabled={!demo}
                      variant="danger"
                      onClick={() => initiateDecision('reject')}
                    >
                      Từ chối duyệt
                    </AdminButton>
                    <AdminButton
                      disabled={!demo}
                      variant="primary"
                      onClick={() => initiateDecision('approve')}
                    >
                      Phê duyệt & Xuất bản
                    </AdminButton>
                  </>
                )}
              </div>
            </div>
          )
        }
      >
        {selected && (
          <>
            <ServiceDetail
              key={selected.id}
              service={selected}
              timeline={demo ? review.timeline.filter(e => e.serviceId === selected.id) : []}
              demo={demo}
              adminNote={adminNote}
              onAdminNoteChange={setAdminNote}
            />
            {drawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}
          </>
        )}
      </AdminDialog>

      {/* 6. Decision Confirmation Modal */}
      <AdminDialog
        open={decisionOpen}
        onClose={() => setDecisionOpen(false)}
        variant="modal"
        title={
          decisionKind === 'approve'
            ? 'Xác nhận Phê duyệt & Xuất bản Tour'
            : decisionKind === 'pause'
            ? 'Xác nhận Tạm ngưng mở bán'
            : 'Xác nhận Từ chối Thẩm định'
        }
        description={demo ? "Chỉ cập nhật dữ liệu mẫu trong phiên xem thử demo; không gửi thông báo hay ảnh hưởng CSDL thật." : "Thao tác sẽ cập nhật trực tiếp trạng thái dịch vụ trên hệ thống."}
        footer={
          <>
            <AdminButton disabled={isSubmitting} onClick={() => setDecisionOpen(false)}>Quay lại</AdminButton>
            <AdminButton
              variant={decisionKind === 'reject' ? 'danger' : 'primary'}
              disabled={isSubmitting || !selected}
              onClick={confirmDecision}
            >
              {isSubmitting
                ? 'Đang xử lý...'
                : decisionKind === 'approve'
                ? 'Xác nhận xuất bản (ACTIVE)'
                : decisionKind === 'pause'
                ? 'Xác nhận tạm ngưng (PAUSED)'
                : 'Xác nhận từ chối (REJECTED)'}
            </AdminButton>
          </>
        }
      >
        <div className="admin-services-confirm-dialog">
          <div className="admin-services-recap-box">
            <div className="admin-services-recap-row">
              <span>Tên dịch vụ:</span>
              <strong>{selected?.name}</strong>
            </div>
            <div className="admin-services-recap-row">
              <span>Đơn vị vận hành:</span>
              <strong>{selected?.vendorName}</strong>
            </div>
            <div className="admin-services-recap-row">
              <span>Đơn giá:</span>
              <strong>
                {(selected?.price ?? selected?.basePrice ?? 0).toLocaleString('vi-VN')} ₫ / khách
              </strong>
            </div>
            <div className="admin-services-recap-row">
              <span>Ngưỡng sóng / gió khai báo:</span>
              <strong>
                Sóng ≤ {selected?.maxWaveM}m · Gió ≤ {selected?.minWindKmh} km/h
              </strong>
            </div>
          </div>

          {decisionKind === 'approve' && (
            <p>
              Khi xác nhận, dịch vụ này sẽ chuyển sang trạng thái <strong>ACTIVE (Đang mở bán)</strong> và hiển thị trên cổng đặt vé cho du khách.
            </p>
          )}

          {decisionKind === 'pause' && (
            <p>
              Dịch vụ sẽ chuyển sang trạng thái <strong>PAUSED (Tạm ngưng)</strong>. Các ca đặt mới sẽ bị tạm ngưng cho đến khi điều kiện biển an toàn trở lại.
            </p>
          )}

          {decisionKind === 'reject' && (
            <div>
              <label className="admin-field">
                <span>
                  Lý do từ chối <small>(phản hồi cho đối tác)</small>
                </span>
                <textarea
                  value={rejectReason}
                  onChange={e => setRejectReason(e.target.value)}
                  rows={3}
                  placeholder="VD: Cần bổ sung phương án bảo hộ hoặc điều chỉnh thông số vận hành..."
                />
              </label>
            </div>
          )}

          <p className="admin-detail-note">
            Thao tác mô phỏng an toàn, không thực hiện thay đổi dữ liệu trên máy chủ hoặc dịch vụ thanh toán.
          </p>
        </div>
      </AdminDialog>
    </div>
  );
}

export default ServiceApproval;
