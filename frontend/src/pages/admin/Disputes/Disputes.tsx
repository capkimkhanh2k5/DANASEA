import React, { useEffect, useState, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import {
  CheckCircle2,
  Clock,
  ShieldAlert,
  Search,
  Image as ImageIcon,
  FileText,
  Ban,
  User,
  RotateCcw,
  Info,
} from 'lucide-react';
import { MOCK_ADMIN_DISPUTES } from '../../../data/adminMockData';
import { adminDisputeService, getAdminDisputeError } from '../../../services/adminDisputeService';
import {
  AdminBadge,
  AdminState,
} from '../../../components/admin/AdminUI';
import {
  AdminFilterTabs,
  AdminButton,
  AdminNotice,
  AdminPagination,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  filterDisputes,
  calculateDisputeMetrics,
  resolveDisputeWithRefund,
  dismissDispute,
  DISPUTE_CATEGORY_LABELS,
  type DisputeExtended,
  type DisputeStatusFilter,
} from './disputesModel';
import './Disputes.css';

export function Disputes() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // Data & Selection State
  const [disputes, setDisputes] = useState<DisputeExtended[]>(() => demo ? MOCK_ADMIN_DISPUTES : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedDisputeId, setSelectedDisputeId] = useState<string>(() => demo ? (MOCK_ADMIN_DISPUTES[0]?.id || '') : '');
  const [statusFilter, setStatusFilter] = useState<DisputeStatusFilter>('all');
  const [searchQuery, setSearchQuery] = useState('');

  // Resolution Deliberation State
  const [resolutionNote, setResolutionNote] = useState<string>(
    demo
      ? (MOCK_ADMIN_DISPUTES[0]?.resolutionNote ||
        'Xác minh hiện trường: Nhà cung cấp chưa đảm bảo tiêu chuẩn trang bị an toàn mặt nước. Đề xuất hoàn 50% chi phí dịch vụ cho khách hàng.')
      : ''
  );

  // Modals State
  const [isRefundModalOpen, setIsRefundModalOpen] = useState(false);
  const [isDismissModalOpen, setIsDismissModalOpen] = useState(false);
  const [refundPercentage, setRefundPercentage] = useState<number>(50);
  const [dismissReason, setDismissReason] = useState('Đối tác đã cung cấp đầy đủ nhật trình vận hành và chứng minh dịch vụ được cung ứng đúng cam kết.');
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminDisputeService.getDisputes({ page: 0, size: 50 })
      .then(res => {
        if (!isMounted) return;
        const mapped: DisputeExtended[] = (res.content || []).map(d => ({
          id: d.id,
          subOrderId: d.subOrderId || '',
          raisedBy: d.customerId,
          category: 'SERVICE_QUALITY',
          description: d.description || '',
          status: (d.status === 'RESOLVED_REFUND' || d.status === 'RESOLVED_REJECTED' || d.status === 'RESOLVED_PARTIAL' ? 'RESOLVED' : d.status === 'UNDER_REVIEW' ? 'IN_PROGRESS' : 'OPEN') as unknown as DisputeExtended['status'],
          resolutionNote: d.adminNote || '',
          resolvedBy: d.resolvedBy || '',
          resolvedAt: d.resolvedAt || '',
          customerName: `Khách hàng ${d.customerId.substring(0, 8)}`,
          customerPhone: '',
          vendorName: 'Đơn vị đối tác',
          serviceName: 'Dịch vụ biển',
          orderAmount: 0,
          evidenceImages: d.evidenceUrls || [],
          createdAt: d.createdAt,
          updatedAt: d.updatedAt,
        }));
        setDisputes(mapped);
        if (mapped.length > 0) setSelectedDisputeId(mapped[0].id);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const msg = getAdminDisputeError(err, 'Không thể tải danh sách khiếu nại.');
        setErrorMessage(msg);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  // Metrics
  const metrics = useMemo(() => calculateDisputeMetrics(disputes), [disputes]);

  // Filtered List
  const filteredDisputes = useMemo(() => {
    return filterDisputes(disputes, searchQuery, statusFilter);
  }, [disputes, searchQuery, statusFilter]);

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);

  const totalPages = Math.ceil(filteredDisputes.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginatedDisputes = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filteredDisputes.slice(start, start + PAGE_SIZE);
  }, [filteredDisputes, activePage]);

  // Currently Selected Dispute
  const selectedDispute = useMemo(() => {
    return disputes.find(d => d.id === selectedDisputeId) || filteredDisputes[0] || disputes[0];
  }, [disputes, selectedDisputeId, filteredDisputes]);

  // Handlers
  const handleSelectDispute = (disp: DisputeExtended) => {
    setSelectedDisputeId(disp.id);
    if (disp.resolutionNote) {
      setResolutionNote(disp.resolutionNote);
    } else {
      setResolutionNote('Đã đối soát thông tin phản ánh hai chiều giữa du khách và đối tác vận hành biển.');
    }
  };

  const handleExecuteRefund = async () => {
    if (!selectedDispute) return;
    if (demo) {
      const { updatedList, targetDispute, refundAmount } = resolveDisputeWithRefund(
        disputes,
        selectedDispute.id,
        refundPercentage,
        resolutionNote,
        'Admin DANASEA',
        demo
      );

      setDisputes(updatedList);
      setIsRefundModalOpen(false);
      setNotice(
        `Đã phát lệnh hoàn ${refundAmount.toLocaleString('vi-VN')} đ (${refundPercentage}%) cho du khách ${targetDispute?.customerName} (Mô phỏng SEPAY - Demo)`
      );
      return;
    }

    try {
      await adminDisputeService.resolveDispute(selectedDispute.id, {
        resolution: refundPercentage === 100 ? 'RESOLVED_REFUND' : 'RESOLVED_PARTIAL',
        refundPercentage,
        adminNote: resolutionNote,
      });
      setDisputes(prev => prev.map(d => d.id === selectedDispute.id ? { ...d, status: 'RESOLVED' as unknown as DisputeExtended['status'] } : d));
      setIsRefundModalOpen(false);
      setNotice(`Đã phán quyết hoàn tiền ${refundPercentage}% cho khiếu nại #${selectedDispute.id}.`);
    } catch (err) {
      const msg = getAdminDisputeError(err, 'Lỗi khi xử lý phán quyết khiếu nại.');
      setNotice(msg);
      setIsRefundModalOpen(false);
    }
  };

  const handleDismissSubmit = async () => {
    if (!selectedDispute) return;
    if (demo) {
      const { updatedList, targetDispute } = dismissDispute(
        disputes,
        selectedDispute.id,
        dismissReason,
        'Admin DANASEA',
        demo
      );

      setDisputes(updatedList);
      setIsDismissModalOpen(false);
      setNotice(`Đã bác bỏ khiếu nại #${targetDispute?.id} (Demo)`);
      return;
    }

    try {
      await adminDisputeService.resolveDispute(selectedDispute.id, {
        resolution: 'RESOLVED_REJECTED',
        adminNote: dismissReason,
      });
      setDisputes(prev => prev.map(d => d.id === selectedDispute.id ? { ...d, status: 'RESOLVED' as unknown as DisputeExtended['status'] } : d));
      setIsDismissModalOpen(false);
      setNotice(`Đã bác bỏ khiếu nại #${selectedDispute.id}.`);
    } catch (err) {
      const msg = getAdminDisputeError(err, 'Lỗi khi bác bỏ khiếu nại.');
      setNotice(msg);
      setIsDismissModalOpen(false);
    }
  };

  return (
    <div className="admin-disputes">
      {/* Compact Summary Strip */}
      <div className="admin-disputes__summary-strip" aria-label="Tóm tắt tình trạng khiếu nại">
        <div className="admin-disputes__summary-item">
          <span>Tổng số vụ việc:</span>
          <strong style={{ color: 'var(--admin-ink, #171717)' }}>{metrics.totalCount}</strong>
        </div>
        <div className="admin-disputes__summary-divider" />
        <div className="admin-disputes__summary-item">
          <span className="admin-disputes__summary-badge admin-disputes__summary-badge--pending">
            <Clock size={14} />
            Đang thụ lý / Chờ xử lý: {metrics.pendingActionCount}
          </span>
          <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
            ({metrics.totalDisputedAmount.toLocaleString('vi-VN')} đ giá trị liên quan)
          </span>
        </div>
        <div className="admin-disputes__summary-divider" />
        <div className="admin-disputes__summary-item">
          <span className="admin-disputes__summary-badge admin-disputes__summary-badge--resolved">
            <CheckCircle2 size={14} />
            Đã giải quyết: {metrics.resolvedCount}
          </span>
        </div>
      </div>

      {/* Master-Detail Console Workspace */}
      <div className="admin-disputes__workspace">
        {/* Left Column: Master List */}
        <div className="admin-disputes__master">
          <div className="admin-disputes__master-header">
            <div className="admin-disputes__master-title-row">
              <span className="admin-disputes__master-title">
                <ShieldAlert size={18} style={{ color: 'var(--admin-primary, #0B5D6F)' }} />
                Danh sách hồ sơ khiếu nại
              </span>
              <span style={{ fontSize: 12, color: 'var(--admin-muted, #595959)', fontWeight: 600 }}>
                {filteredDisputes.length} / {disputes.length} hồ sơ
              </span>
            </div>

            {/* Filter Tabs */}
            <AdminFilterTabs
              label="Lọc trạng thái tranh chấp"
              value={statusFilter}
              onChange={(val) => {
                setStatusFilter(val as DisputeStatusFilter);
                setCurrentPage(1);
              }}
              items={[
                { value: 'all', label: 'Tất cả', count: metrics.totalCount },
                { value: 'OPEN', label: 'Chờ xử lý', count: metrics.pendingActionCount },
                { value: 'RESOLVED', label: 'Đã xong', count: metrics.resolvedCount },
              ]}
            />

            {/* Search Input */}
            <div className="admin-disputes__search-box">
              <Search size={16} className="admin-disputes__search-icon" aria-hidden="true" />
              <input
                type="text"
                className="admin-disputes__search-input"
                placeholder="Tìm mã hồ sơ, tên khách, đối tác hoặc dịch vụ..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setCurrentPage(1);
                }}
                aria-label="Tìm kiếm khiếu nại"
              />
            </div>
          </div>

          {/* Master Items List */}
          <div className="admin-disputes__list" role="listbox" aria-label="Danh sách tranh chấp">
            <div key={statusFilter} className="admin-tab-pane">
            {loading ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="empty"
                  title="Đang tải danh sách khiếu nại..."
                  description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
                />
              </div>
            ) : errorMessage ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="error"
                  title="Không thể tải khiếu nại"
                  description={errorMessage}
                />
              </div>
            ) : filteredDisputes.length === 0 ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="empty"
                  title="Không có hồ sơ nào"
                  description={disputes.length === 0 ? "Hiện tại sàn DANASEA không ghi nhận tranh chấp hoặc khiếu nại nào cần phân xử." : "Không tìm thấy tranh chấp phù hợp với bộ lọc hiện tại."}
                  action={
                    <AdminButton
                      onClick={() => {
                        setSearchQuery('');
                        setStatusFilter('all');
                      }}
                    >
                      Xóa bộ lọc
                    </AdminButton>
                  }
                />
              </div>
            ) : (
              paginatedDisputes.map(d => {
                const isSelected = d.id === selectedDispute?.id;
                return (
                  <div
                    key={d.id}
                    role="option"
                    aria-selected={isSelected}
                    tabIndex={0}
                    className={`admin-disputes__item ${isSelected ? 'admin-disputes__item--active' : ''}`}
                    onClick={() => handleSelectDispute(d)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleSelectDispute(d);
                      }
                    }}
                  >
                    <div className="admin-disputes__item-top">
                      <span className="admin-disputes__item-id">#{d.id}</span>
                      <AdminBadge
                        tone={
                          d.status === 'RESOLVED'
                            ? 'teal'
                            : d.status === 'IN_PROGRESS'
                            ? 'neutral'
                            : 'amber'
                        }
                      >
                        {d.status === 'RESOLVED' ? 'ĐÃ XONG' : d.status === 'IN_PROGRESS' ? 'ĐANG THỤ LÝ' : 'CHỜ DUYỆT'}
                      </AdminBadge>
                    </div>

                    <div className="admin-disputes__item-service">{d.serviceName}</div>
                    <div className="admin-disputes__item-desc">{d.description}</div>

                    <div className="admin-disputes__item-footer">
                      <span className="admin-disputes__item-customer">{d.customerName}</span>
                      <span className="admin-disputes__item-amount">
                        {d.orderAmount ? `${d.orderAmount.toLocaleString('vi-VN')} đ` : 'N/A'}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
            </div>
          </div>

          {!loading && !errorMessage && filteredDisputes.length > 0 && (
            <AdminPagination
              currentPage={activePage}
              totalPages={totalPages}
              totalRecords={filteredDisputes.length}
              pageSize={PAGE_SIZE}
              recordLabel="vụ việc"
              onPageChange={setCurrentPage}
            />
          )}
        </div>

        {/* Right Column: Detail Dossier */}
        {selectedDispute && (
          <aside className="admin-disputes__detail" aria-labelledby="dispute-detail-heading">
            <div key={selectedDispute.id} className="admin-master-detail-fade">
            {/* Header: Service, Code, Category, Status */}
            <div className="admin-disputes__detail-header">
              <div>
                <div className="admin-disputes__detail-tag-row">
                  <span className="admin-disputes__tag-pill">HỒ SƠ #{selectedDispute.id}</span>
                  <span style={{ fontSize: 12, color: 'var(--admin-muted, #595959)' }}>
                    Đơn hàng: <strong>{selectedDispute.subOrderId}</strong>
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--admin-primary, #0B5D6F)', fontWeight: 600 }}>
                    · {DISPUTE_CATEGORY_LABELS[selectedDispute.category] || selectedDispute.category}
                  </span>
                </div>
                <h2 id="dispute-detail-heading" className="admin-disputes__detail-service-title" style={{ textWrap: 'balance' }}>
                  {selectedDispute.serviceName}
                </h2>
              </div>

              <AdminBadge
                tone={
                  selectedDispute.status === 'RESOLVED'
                    ? 'teal'
                    : selectedDispute.status === 'IN_PROGRESS'
                    ? 'neutral'
                    : 'amber'
                }
              >
                {selectedDispute.status === 'RESOLVED' ? 'ĐÃ XONG' : selectedDispute.status === 'IN_PROGRESS' ? 'ĐANG THỤ LÝ' : 'CHỜ DUYỆT'}
              </AdminBadge>
            </div>

            {/* Parties: Customer & Vendor */}
            <div className="admin-disputes__section">
              <span className="admin-disputes__section-title">
                <User size={14} />
                Các bên liên quan
              </span>
              <div className="admin-disputes__parties-grid">
                <div className="admin-disputes__party-card">
                  <span className="admin-disputes__party-role">Du khách khiếu nại</span>
                  <span className="admin-disputes__party-name">{selectedDispute.customerName}</span>
                  <span className="admin-disputes__party-sub">SĐT: {selectedDispute.customerPhone}</span>
                </div>

                <div className="admin-disputes__party-card">
                  <span className="admin-disputes__party-role">Đối tác vận hành tour</span>
                  <span className="admin-disputes__party-name">{selectedDispute.vendorName}</span>
                  <span className="admin-disputes__party-sub" style={{ fontWeight: 600, color: 'var(--admin-primary, #0B5D6F)' }}>
                    Giá trị đơn: {selectedDispute.orderAmount ? `${selectedDispute.orderAmount.toLocaleString('vi-VN')} VNĐ` : 'N/A'}
                  </span>
                </div>
              </div>
            </div>

            {/* Claim Content */}
            <div className="admin-disputes__section">
              <span className="admin-disputes__section-title">
                <FileText size={14} />
                Nội dung phản ánh từ khách hàng
              </span>
              <div className="admin-disputes__claim-box">
                {selectedDispute.description}
              </div>
            </div>

            {/* Attached Evidence */}
            {selectedDispute.evidenceImages && selectedDispute.evidenceImages.length > 0 && (
              <div className="admin-disputes__section">
                <span className="admin-disputes__section-title">
                  <ImageIcon size={14} />
                  Hình ảnh bằng chứng hiện trường ({selectedDispute.evidenceImages.length} ảnh)
                </span>
                <div className="admin-disputes__gallery">
                  {selectedDispute.evidenceImages.map((img, idx) => (
                    <img
                      key={idx}
                      src={img}
                      alt="Bằng chứng hiện trường"
                      className="admin-disputes__evidence-thumb"
                      onClick={() => setPreviewImage(img)}
                      title="Bấm để xem ảnh phóng to"
                    />
                  ))}
                </div>
              </div>
            )}

            {/* Field Review Log (Neutral phrasing) */}
            <div className="admin-disputes__review-card">
              <Info size={18} style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <strong>Đối soát nhật trình vận hành (Dữ liệu mẫu):</strong> Ghi nhận dữ liệu thời gian xuất bến và nhật trình mặt nước theo hồ sơ mẫu. Đối tác có nghĩa vụ bồi hoàn thỏa đáng nếu điều kiện an toàn và trang bị mặt nước không đảm bảo cam kết.
              </div>
            </div>

            {/* Deliberation Note & Resolution Actions */}
            <div className="admin-disputes__resolution-box">
              <label style={{ fontSize: 12, fontWeight: 700, color: 'var(--admin-ink, #171717)' }}>
                KẾT LUẬN THẨM ĐỊNH &amp; PHƯƠNG ÁN GIẢI QUYẾT:
              </label>
              <textarea
                className="admin-disputes__textarea"
                value={resolutionNote}
                onChange={(e) => setResolutionNote(e.target.value)}
                placeholder="Nhập biên bản thẩm định hoặc ghi chú lý do hoàn tiền/bác bỏ..."
              />

              <div className="admin-disputes__actions-row">
                {selectedDispute.status !== 'RESOLVED' ? (
                  <>
                    <AdminButton
                      variant="secondary"
                      onClick={() => setIsDismissModalOpen(true)}
                    >
                      <Ban size={15} aria-hidden="true" />
                      Bác bỏ khiếu nại
                    </AdminButton>

                    <AdminButton
                      variant="primary"
                      onClick={() => setIsRefundModalOpen(true)}
                    >
                      <RotateCcw size={15} aria-hidden="true" />
                      Chấp thuận &amp; Hoàn tiền (Mô phỏng SEPAY)
                    </AdminButton>
                  </>
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#166534', fontWeight: 600 }}>
                    <CheckCircle2 size={16} />
                    <span>Hồ sơ đã được giải quyết bởi {selectedDispute.resolvedBy || 'Ban Quản trị DANASEA'}.</span>
                  </div>
                )}
              </div>
            </div>
            </div>
          </aside>
        )}
      </div>

      {/* Floating Notice */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />

      {/* Refund Modal */}
      <AdminDialog
        open={isRefundModalOpen}
        onClose={() => setIsRefundModalOpen(false)}
        variant="modal"
        title="Lệnh Hoàn Tiền Bảo Vệ Du Khách (Mô phỏng)"
        description="Ghi nhận lệnh hoàn tiền bảo chứng về tài khoản khách hàng trong phiên kiểm thử."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsRefundModalOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" onClick={handleExecuteRefund}>
              Xác nhận phát lệnh hoàn tiền
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: 13, color: 'var(--admin-body, #262626)' }}>
          <p>
            Phê duyệt lệnh hoàn trả cho du khách <strong>{selectedDispute?.customerName}</strong> đối với đơn{' '}
            <strong>{selectedDispute?.subOrderId}</strong>.
          </p>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: 'var(--admin-ink, #171717)', marginBottom: 6 }}>
              CHỌN TỶ LỆ HOÀN TRẢ CHI PHÍ:
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
              {[20, 30, 50, 100].map(pct => (
                <button
                  key={pct}
                  type="button"
                  onClick={() => setRefundPercentage(pct)}
                  style={{
                    padding: '8px 0',
                    borderRadius: 8,
                    fontSize: 13,
                    fontWeight: 700,
                    border: '1px solid',
                    borderColor: refundPercentage === pct ? '#0B5D6F' : '#cbd5e1',
                    background: refundPercentage === pct ? '#0B5D6F' : '#f8fafc',
                    color: refundPercentage === pct ? '#ffffff' : 'var(--admin-body, #262626)',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {pct}%
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 14px', background: 'var(--admin-tint, #e8f3f5)', borderRadius: 8, border: '1px solid #c2e0e5' }}>
            <span style={{ fontSize: 12, color: 'var(--admin-primary, #0B5D6F)', fontWeight: 600 }}>Số tiền hoàn trả dự kiến:</span>
            <span style={{ fontSize: 18, fontWeight: 800, color: 'var(--admin-ink, #171717)', fontVariantNumeric: 'tabular-nums' }}>
              {Math.round(((selectedDispute?.orderAmount || 1000000) * refundPercentage) / 100).toLocaleString('vi-VN')} VNĐ
            </span>
          </div>

          <div style={{ fontSize: 11, color: 'var(--admin-muted, #737373)', fontStyle: 'italic' }}>
            * Lưu ý: Thao tác chỉ mô phỏng trong phiên Demo. Hệ thống không gọi cổng thanh toán thực tế.
          </div>
        </div>
      </AdminDialog>

      {/* Dismiss Dispute Modal */}
      <AdminDialog
        open={isDismissModalOpen}
        onClose={() => setIsDismissModalOpen(false)}
        variant="modal"
        title="Bác bỏ khiếu nại tranh chấp"
        description="Ghi nhận lý do kết luận đối tác đã thực hiện đúng nghĩa vụ cung ứng dịch vụ."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsDismissModalOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="danger" onClick={handleDismissSubmit}>
              Xác nhận bác bỏ
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 13, color: 'var(--admin-body, #262626)' }}>
          <p>
            Bạn sắp bác bỏ khiếu nại <strong>#{selectedDispute?.id}</strong> từ du khách <strong>{selectedDispute?.customerName}</strong>.
          </p>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: 'var(--admin-ink, #171717)', marginBottom: 4 }}>
              CĂN CỨ BÁC BỎ KHIẾU NẠI *
            </label>
            <textarea
              rows={3}
              className="admin-disputes__textarea"
              value={dismissReason}
              onChange={(e) => setDismissReason(e.target.value)}
            />
          </div>
        </div>
      </AdminDialog>

      {/* Image Preview Modal */}
      <AdminDialog
        open={Boolean(previewImage)}
        onClose={() => setPreviewImage(null)}
        variant="modal"
        title="Bằng chứng hiện trường"
        description="Xem chi tiết hình ảnh phản ánh từ du khách phục vụ thẩm định"
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', flexWrap: 'wrap', gap: 8 }}>
            <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
              Nhấn phím Esc hoặc nút Đóng để quay lại
            </span>
            <div style={{ display: 'flex', gap: 8 }}>
              {previewImage && (
                <a
                  href={previewImage}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="admin-button"
                  style={{ fontSize: 12, textDecoration: 'none' }}
                >
                  Mở ảnh gốc
                </a>
              )}
              <AdminButton variant="secondary" onClick={() => setPreviewImage(null)}>
                Đóng
              </AdminButton>
            </div>
          </div>
        }
      >
        {previewImage && (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '10px 0', background: '#0b1320', borderRadius: 8, overflow: 'hidden' }}>
            <img
              src={previewImage}
              alt="Ảnh bằng chứng hiện trường"
              style={{ maxWidth: '100%', maxHeight: '68vh', objectFit: 'contain', display: 'block' }}
            />
          </div>
        )}
      </AdminDialog>
    </div>
  );
}

export default Disputes;
