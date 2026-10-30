import React, { useEffect, useState, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Building2,
  CreditCard,
  Download,
  CheckCircle2,
  Clock,
  Ban,
  Search,
  FileText,
  Calendar,
  Check,
  Copy,
} from 'lucide-react';
import { MOCK_ADMIN_PAYOUTS } from '../../../data/adminMockData';
import { adminSettlementService, getAdminSettlementError } from '../../../services/adminSettlementService';
import {
  AdminBadge,
  AdminState,
} from '../../../components/admin/AdminUI';
import {
  AdminPreviewBanner,
  AdminFilterTabs,
  AdminButton,
  AdminNotice,
  AdminDisclosure,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  filterPayouts,
  calculatePayoutMetrics,
  approvePayout,
  rejectPayout,
  exportPayoutsCsv,
  type PayoutExtended,
  type PayoutStatusFilter,
} from './payoutsModel';
import './AdminPayouts.css';

export function AdminPayouts() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // Data & Selection State
  const [payouts, setPayouts] = useState<PayoutExtended[]>(() => demo ? MOCK_ADMIN_PAYOUTS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedPayoutId, setSelectedPayoutId] = useState<string>(() => demo ? (MOCK_ADMIN_PAYOUTS[0]?.id || '') : '');
  const [statusFilter, setStatusFilter] = useState<PayoutStatusFilter>('all');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals State
  const [isApproveModalOpen, setIsApproveModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [bankRefCode, setBankRefCode] = useState('VCB-NAPAS-998231');
  const [rejectReason, setRejectReason] = useState('Thông tin đối soát số tài khoản không trùng khớp');
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminSettlementService.getSettlements({ page: 0, size: 50 })
      .then(res => {
        if (!isMounted) return;
        const mapped: PayoutExtended[] = (res.content || []).map(s => ({
          id: s.id,
          vendorId: s.vendorId,
          settlementId: s.id,
          amount: s.netPayableAmount,
          status: (s.status === 'PAID' ? 'PAID' : 'REQUESTED') as unknown as PayoutExtended['status'],
          processedBy: '',
          processedAt: s.updatedAt,
          vendorName: `Đối tác ${s.vendorId.substring(0, 8)}`,
          bankName: 'Vietcombank',
          bankAccountNumber: '****',
          bankAccountHolder: 'Vendor',
          periodText: `Kỳ đối soát ${s.periodStart.slice(0, 10)} - ${s.periodEnd.slice(0, 10)}`,
          totalOrdersCount: 0,
          createdAt: s.createdAt,
          updatedAt: s.updatedAt,
        }));
        setPayouts(mapped);
        if (mapped.length > 0) setSelectedPayoutId(mapped[0].id);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const msg = getAdminSettlementError(err, 'Không thể tải danh sách quyết toán.');
        setErrorMessage(msg);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  // Metrics
  const metrics = useMemo(() => calculatePayoutMetrics(payouts), [payouts]);

  // Filtered List
  const filteredPayouts = useMemo(() => {
    return filterPayouts(payouts, searchQuery, statusFilter);
  }, [payouts, searchQuery, statusFilter]);

  // Currently Selected Payout
  const selectedPayout = useMemo(() => {
    return payouts.find(p => p.id === selectedPayoutId) || filteredPayouts[0] || payouts[0];
  }, [payouts, selectedPayoutId, filteredPayouts]);

  // Handlers
  const handleApproveDisbursement = async () => {
    if (!selectedPayout) return;
    if (demo) {
      const { updatedList, targetPayout } = approvePayout(
        payouts,
        selectedPayout.id,
        bankRefCode,
        'Admin DANASEA',
        demo
      );

      setPayouts(updatedList);
      setIsApproveModalOpen(false);
      setNotice(
        `Đã duyệt giải ngân ${targetPayout?.amount.toLocaleString('vi-VN')} đ cho đối tác ${targetPayout?.vendorName} (Mô phỏng Napas247)`
      );
      return;
    }

    try {
      await adminSettlementService.finalizeSettlement(selectedPayout.id);
      setPayouts(prev => prev.map(p => p.id === selectedPayout.id ? { ...p, status: 'PAID' as unknown as PayoutExtended['status'] } : p));
      setIsApproveModalOpen(false);
      setNotice(`Đã chốt sổ và giải ngân kỳ đối soát #${selectedPayout.id}.`);
    } catch (err) {
      const msg = getAdminSettlementError(err, 'Lỗi khi giải ngân kỳ đối soát.');
      setNotice(msg);
      setIsApproveModalOpen(false);
    }
  };

  const handleRejectPayoutSubmit = () => {
    if (!selectedPayout) return;
    if (!demo) {
      setNotice('Thao tác từ chối bị khóa ngoài Demo Mode (?demo=1)');
      setIsRejectModalOpen(false);
      return;
    }

    const { updatedList, targetPayout } = rejectPayout(
      payouts,
      selectedPayout.id,
      rejectReason,
      'Vũ Hải Đăng (Admin)',
      demo
    );

    setPayouts(updatedList);
    setIsRejectModalOpen(false);
    setNotice(`Đã từ chối lệnh chi trả #${targetPayout?.id}`);
  };

  const handleExportStatement = () => {
    const csvContent = exportPayoutsCsv(filteredPayouts);
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `DANASEA_Payout_Statement_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setNotice(`Đã xuất ${filteredPayouts.length} lệnh chi trả ra file CSV báo cáo`);
  };

  const handleCopyAccount = (accNo?: string) => {
    if (!accNo) return;
    navigator.clipboard?.writeText(accNo);
    setNotice(`Đã sao chép số tài khoản: ${accNo}`);
  };

  return (
    <div className="admin-payouts">
      {/* Preview Banner */}
      <AdminPreviewBanner demo={demo} href={`${location.pathname}?demo=1`} />

      {/* Compact Summary Strip */}
      <div className="admin-payouts__summary-strip" aria-label="Tóm tắt trạng thái chi trả">
        <div className="admin-payouts__summary-item">
          <span>Tổng số lệnh:</span>
          <strong style={{ color: 'var(--admin-ink, #171717)' }}>{metrics.totalCount}</strong>
        </div>
        <div className="admin-payouts__summary-divider" />
        <div className="admin-payouts__summary-item">
          <span className="admin-payouts__summary-badge admin-payouts__summary-badge--pending">
            <Clock size={14} />
            Chờ duyệt: {metrics.pendingCount}
          </span>
          <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
            ({metrics.totalPendingAmount.toLocaleString('vi-VN')} đ)
          </span>
        </div>
        <div className="admin-payouts__summary-divider" />
        <div className="admin-payouts__summary-item">
          <span className="admin-payouts__summary-badge admin-payouts__summary-badge--paid">
            <CheckCircle2 size={14} />
            Đã giải ngân: {metrics.paidCount}
          </span>
          <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
            ({metrics.totalPaidAmount.toLocaleString('vi-VN')} đ)
          </span>
        </div>
      </div>

      {/* Master-Detail Console Workspace */}
      <div className="admin-payouts__workspace">
        {/* Left Column: Master List */}
        <div className="admin-payouts__master">
          <div className="admin-payouts__master-header">
            <div className="admin-payouts__master-title-row">
              <span className="admin-payouts__master-title">
                <CreditCard size={18} style={{ color: '#00646f' }} />
                Danh sách lệnh chi trả
              </span>
              <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)', fontWeight: 600 }}>
                {filteredPayouts.length} / {payouts.length} lệnh
              </span>
            </div>

            {/* Filter Tabs */}
            <AdminFilterTabs
              label="Lọc trạng thái lệnh chi"
              value={statusFilter}
              onChange={(val) => setStatusFilter(val as PayoutStatusFilter)}
              items={[
                { value: 'all', label: 'Tất cả', count: metrics.totalCount },
                { value: 'REQUESTED', label: 'Chờ duyệt', count: metrics.pendingCount },
                { value: 'PAID', label: 'Đã chi', count: metrics.paidCount },
                { value: 'REJECTED', label: 'Từ chối', count: metrics.rejectedCount },
              ]}
            />

            {/* Search Input & Export Action */}
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
              <div className="admin-payouts__search-box" style={{ flex: 1 }}>
                <Search size={16} className="admin-payouts__search-icon" aria-hidden="true" />
                <input
                  type="text"
                  className="admin-payouts__search-input"
                  placeholder="Tìm theo mã lệnh, tên đối tác hoặc số tài khoản..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  aria-label="Tìm kiếm lệnh chi trả"
                />
              </div>
              <AdminButton
                onClick={handleExportStatement}
                title="Xuất bảng đối soát ra file CSV"
              >
                <Download size={16} aria-hidden="true" />
                Xuất CSV đối soát
              </AdminButton>
            </div>
          </div>

          {/* Master Items List */}
          <div className="admin-payouts__list" role="listbox" aria-label="Danh sách lệnh chi trả">
            <div key={statusFilter} className="admin-tab-pane">
            {loading ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="empty"
                  title="Đang tải danh sách quyết toán..."
                  description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
                />
              </div>
            ) : errorMessage ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="error"
                  title="Không thể tải quyết toán"
                  description={errorMessage}
                />
              </div>
            ) : filteredPayouts.length === 0 ? (
              <div style={{ padding: '32px 16px' }}>
                <AdminState
                  kind="empty"
                  title="Không có lệnh chi nào"
                  description={payouts.length === 0 ? "Hiện tại chưa có đợt quyết toán tài chính nào cần xử lý." : "Không tìm thấy yêu cầu chi trả phù hợp với bộ lọc hiện tại."}
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
              filteredPayouts.map(p => {
                const isSelected = p.id === selectedPayout?.id;
                return (
                  <div
                    key={p.id}
                    role="option"
                    aria-selected={isSelected}
                    tabIndex={0}
                    className={`admin-payouts__item ${isSelected ? 'admin-payouts__item--active' : ''}`}
                    onClick={() => setSelectedPayoutId(p.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setSelectedPayoutId(p.id);
                      }
                    }}
                  >
                    <div className="admin-payouts__item-top">
                      <span className="admin-payouts__item-id">{p.id}</span>
                      <AdminBadge
                        tone={
                          p.status === 'PAID'
                            ? 'teal'
                            : p.status === 'REQUESTED'
                            ? 'amber'
                            : 'neutral'
                        }
                      >
                        {p.status === 'PAID' ? 'ĐÃ CHI TRẢ' : p.status === 'REQUESTED' ? 'CHỜ DUYỆT' : 'TỪ CHỐI'}
                      </AdminBadge>
                    </div>

                    <div className="admin-payouts__item-vendor">{p.vendorName}</div>

                    <div className="admin-payouts__item-meta">
                      <span>{p.periodText}</span>
                      <span className="admin-payouts__item-amount">
                        {p.amount.toLocaleString('vi-VN')} đ
                      </span>
                    </div>
                  </div>
                );
              })
            )}
            </div>
          </div>
        </div>

        {/* Right Column: Detail Dossier */}
        {selectedPayout && (
          <aside className="admin-payouts__detail" aria-labelledby="payout-detail-heading">
            <div key={selectedPayout.id} className="admin-master-detail-fade">
            <div className="admin-payouts__detail-header">
              <div>
                <span className="admin-payouts__detail-id-tag">LỆNH CHI #{selectedPayout.id}</span>
                <h2 id="payout-detail-heading" className="admin-payouts__detail-vendor-name">
                  {selectedPayout.vendorName}
                </h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
                  <Calendar size={14} />
                  <span>{selectedPayout.periodText}</span>
                </div>
              </div>

              <AdminBadge
                tone={
                  selectedPayout.status === 'PAID'
                    ? 'teal'
                    : selectedPayout.status === 'REQUESTED'
                    ? 'amber'
                    : 'neutral'
                }
              >
                {selectedPayout.status === 'PAID' ? 'ĐÃ CHI TRẢ' : selectedPayout.status === 'REQUESTED' ? 'CHỜ DUYỆT' : 'TỪ CHỐI'}
              </AdminBadge>
            </div>

            {/* Prominent Amount Display */}
            <div className="admin-payouts__amount-card">
              <div>
                <span className="admin-payouts__amount-label">Số tiền cần thanh toán</span>
                <div className="admin-payouts__amount-value">
                  {selectedPayout.amount.toLocaleString('vi-VN')} <span style={{ fontSize: 18 }}>VNĐ</span>
                </div>
              </div>
              <div style={{ padding: '8px 12px', background: '#ffffff', borderRadius: 8, border: '1px solid #ccfbf1', fontSize: 12, color: '#00646f', fontWeight: 600 }}>
                {selectedPayout.totalOrdersCount} đơn tour hoàn tất
              </div>
            </div>

            {/* Bank Information Group */}
            <div className="admin-payouts__info-group">
              <span className="admin-payouts__info-title">
                <Building2 size={14} />
                Tài khoản ngân hàng thụ hưởng
              </span>
              <div className="admin-payouts__info-card">
                <div className="admin-payouts__info-row">
                  <span className="admin-payouts__info-key">Ngân hàng:</span>
                  <span className="admin-payouts__info-val">{selectedPayout.bankName}</span>
                </div>
                <div className="admin-payouts__info-row">
                  <span className="admin-payouts__info-key">Số tài khoản:</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span className="admin-payouts__info-val admin-payouts__info-val--mono">
                      {selectedPayout.bankAccountNumber}
                    </span>
                    <button
                      type="button"
                      className="admin-payouts__copy-btn"
                      onClick={() => handleCopyAccount(selectedPayout.bankAccountNumber)}
                      title="Sao chép số tài khoản"
                      aria-label="Sao chép số tài khoản"
                    >
                      <Copy size={13} />
                    </button>
                  </div>
                </div>
                <div className="admin-payouts__info-row">
                  <span className="admin-payouts__info-key">Chủ tài khoản:</span>
                  <span className="admin-payouts__info-val">{selectedPayout.bankAccountHolder}</span>
                </div>
              </div>
            </div>

            {/* Reconciliation Details Group */}
            <div className="admin-payouts__info-group">
              <span className="admin-payouts__info-title">
                <FileText size={14} />
                Thông tin hồ sơ quyết toán
              </span>
              <div className="admin-payouts__info-card">
                <div className="admin-payouts__info-row">
                  <span className="admin-payouts__info-key">Mã bảng quyết toán:</span>
                  <span className="admin-payouts__info-val" style={{ fontFamily: 'monospace' }}>
                    {selectedPayout.settlementId}
                  </span>
                </div>
                <div className="admin-payouts__info-row">
                  <span className="admin-payouts__info-key">Thời gian tạo yêu cầu:</span>
                  <span className="admin-payouts__info-val">
                    {selectedPayout.createdAt?.slice(0, 10)} {selectedPayout.createdAt?.slice(11, 16)}
                  </span>
                </div>
                {selectedPayout.processedAt && (
                  <div className="admin-payouts__info-row">
                    <span className="admin-payouts__info-key">Thời gian xử lý:</span>
                    <span className="admin-payouts__info-val">
                      {selectedPayout.processedAt.slice(0, 10)} {selectedPayout.processedAt.slice(11, 16)}
                    </span>
                  </div>
                )}
              </div>
            </div>

            {/* Constituent Orders Breakdown (Collapsible) */}
            <AdminDisclosure
              title={`Danh sách đơn tour cấu thành (${selectedPayout.totalOrdersCount ?? 0} đơn)`}
              description="Các dịch vụ trải nghiệm biển đã hoàn thành và đối soát trong kỳ"
            >
              <div className="admin-payouts__orders-breakdown">
                <div className="admin-payouts__orders-row">
                  <span>Dịch vụ lặn biển & thể thao nước (Demo)</span>
                  <strong>{Math.round((selectedPayout.totalOrdersCount ?? 0) * 0.6)} đơn</strong>
                </div>
                <div className="admin-payouts__orders-row">
                  <span>Tour cano & tham quan vịnh biển (Demo)</span>
                  <strong>{Math.round((selectedPayout.totalOrdersCount ?? 0) * 0.4)} đơn</strong>
                </div>
                <div className="admin-payouts__orders-footer">
                  <span>Tổng số tiền quyết toán:</span>
                  <strong>{selectedPayout.amount.toLocaleString('vi-VN')} đ</strong>
                </div>
              </div>
            </AdminDisclosure>

            {/* Action Buttons Section */}
            <div className="admin-payouts__actions-box">
              {selectedPayout.status === 'REQUESTED' ? (
                <>
                  <AdminButton
                    variant="primary"
                    onClick={() => setIsApproveModalOpen(true)}
                    style={{ width: '100%', justifyContent: 'center' }}
                  >
                    <Check size={16} aria-hidden="true" />
                    Phê duyệt giải ngân (Mô phỏng Napas247 - Demo)
                  </AdminButton>

                  <AdminButton
                    variant="secondary"
                    onClick={() => setIsRejectModalOpen(true)}
                    style={{ width: '100%', justifyContent: 'center', color: '#b91c1c' }}
                  >
                    <Ban size={16} aria-hidden="true" />
                    Từ chối lệnh chi trả
                  </AdminButton>
                </>
              ) : (
                <div className="admin-payouts__processed-notice">
                  <CheckCircle2 size={18} />
                  <span>
                    Lệnh chi đã được xử lý bởi{' '}
                    <strong>{selectedPayout.processedBy || 'Ban Quản trị DANASEA'}</strong>
                    {selectedPayout.processedAt ? ` vào ngày ${selectedPayout.processedAt.slice(0, 10)}` : ''}.
                  </span>
                </div>
              )}
            </div>
            </div>
          </aside>
        )}
      </div>

      {/* Floating Notice */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />

      {/* Approve Disbursement Modal */}
      <AdminDialog
        open={isApproveModalOpen}
        onClose={() => setIsApproveModalOpen(false)}
        variant="modal"
        title="Xác nhận phê duyệt giải ngân Napas247 (Demo)"
        description="Thao tác này ghi nhận lệnh chuyển tiền chi trả cho đối tác trong phiên kiểm thử."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsApproveModalOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" onClick={handleApproveDisbursement}>
              Xác nhận giải ngân
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: 13, color: 'var(--admin-body, #262626)' }}>
          <p>
            Bạn đang chuẩn bị giải ngân số tiền{' '}
            <strong style={{ color: 'var(--admin-ink, #171717)', fontSize: 15 }}>
              {selectedPayout?.amount.toLocaleString('vi-VN')} VNĐ
            </strong>{' '}
            cho đối tác <strong>{selectedPayout?.vendorName}</strong>.
          </p>

          <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: 8, border: '1px solid #e2e8f0' }}>
            <div style={{ fontSize: 12, color: 'var(--admin-muted, #737373)', marginBottom: 4 }}>Tài khoản nhận:</div>
            <div style={{ fontWeight: 700, color: 'var(--admin-ink, #171717)' }}>
              {selectedPayout?.bankName} — {selectedPayout?.bankAccountNumber}
            </div>
            <div style={{ fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
              Chủ TK: {selectedPayout?.bankAccountHolder}
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: 'var(--admin-ink, #171717)', marginBottom: 4 }}>
              MÃ THAM CHIẾU GIAO DỊCH (BANK REF CODE):
            </label>
            <input
              type="text"
              className="admin-payouts__search-input"
              value={bankRefCode}
              onChange={(e) => setBankRefCode(e.target.value)}
              style={{ fontFamily: 'monospace', fontWeight: 700 }}
            />
          </div>

          <div style={{ fontSize: 11, color: 'var(--admin-muted, #737373)', fontStyle: 'italic' }}>
            * Lưu ý an toàn: Đây là môi trường Demo. Hệ thống không gửi lệnh chuyển tiền thật tới Napas hay cổng ngân hàng.
          </div>
        </div>
      </AdminDialog>

      {/* Reject Payout Modal */}
      <AdminDialog
        open={isRejectModalOpen}
        onClose={() => setIsRejectModalOpen(false)}
        variant="modal"
        title="Từ chối yêu cầu chi trả"
        description="Ghi nhận lý do từ chối để đối tác cập nhật lại thông tin đối soát hoặc tài khoản ngân hàng."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsRejectModalOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="danger" onClick={handleRejectPayoutSubmit}>
              Xác nhận từ chối
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 13, color: 'var(--admin-body, #262626)' }}>
          <p>
            Bạn sắp từ chối lệnh chi <strong>#{selectedPayout?.id}</strong> của <strong>{selectedPayout?.vendorName}</strong>.
          </p>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: 'var(--admin-ink, #171717)', marginBottom: 4 }}>
              LÝ DO TỪ CHỐI DUYỆT CHI *
            </label>
            <textarea
              rows={3}
              className="admin-payouts__search-input"
              style={{ height: 'auto', padding: 10 }}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
          </div>
        </div>
      </AdminDialog>
    </div>
  );
}

export default AdminPayouts;
