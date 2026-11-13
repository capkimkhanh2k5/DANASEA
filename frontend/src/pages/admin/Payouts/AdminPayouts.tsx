import React, { useEffect, useState, useMemo, useCallback } from 'react';
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
  Plus,
  RefreshCw,
} from 'lucide-react';
import { MOCK_ADMIN_PAYOUTS } from '../../../data/adminMockData';
import { adminSettlementService, getAdminSettlementError } from '../../../services/adminSettlementService';
import { adminVendorService } from '../../../services/adminVendorService';
import { adminPayoutService } from '../../../services/adminPayoutService';
import type { PayoutRequestItem } from '../../../services/vendorPayoutService';
import type { SettlementDetailResponse, SettlementResponse } from '../../../types/adminSettlement';
import type { AdminVendorResponse } from '../../../types/adminVendor';
import {
  AdminBadge,
  AdminState,
} from '../../../components/admin/AdminUI';
import {
  AdminFilterTabs,
  AdminButton,
  AdminNotice,
  AdminDisclosure,
  AdminPagination,
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

  // Vendors & Settlement Detail State
  const [vendors, setVendors] = useState<AdminVendorResponse[]>([]);
  const [selectedDetail, setSelectedDetail] = useState<SettlementDetailResponse | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // Modals State
  const [isApproveModalOpen, setIsApproveModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [bankRefCode, setBankRefCode] = useState('VCB-NAPAS-998231');
  const [rejectReason, setRejectReason] = useState('Thông tin đối soát số tài khoản không trùng khớp');
  const [notice, setNotice] = useState<string | null>(null);

  // Generate Settlement Modal State
  const [isGenerateModalOpen, setIsGenerateModalOpen] = useState(false);
  const [genVendorId, setGenVendorId] = useState('');
  const [genPeriodStart, setGenPeriodStart] = useState('2026-11-01');
  const [genPeriodEnd, setGenPeriodEnd] = useState('2026-11-05');
  const [isGenerating, setIsGenerating] = useState(false);

  // Load Settlements and Vendors
  const loadSettlementsData = useCallback(() => {
    if (demo) return;
    setLoading(true);
    setErrorMessage(null);

    Promise.all([
      adminPayoutService.getPayoutRequests({ size: 100 }),
      adminSettlementService.getSettlements({ page: 0, size: 50 }).catch(() => ({ content: [] })),
      adminVendorService.getVendors({ size: 100 }).catch(() => ({ content: [] as AdminVendorResponse[] })),
    ])
      .then(([pRes, res, vRes]) => {
        const vList = (vRes as { content?: AdminVendorResponse[] }).content || [];
        setVendors(vList);
        const vMap = new Map(vList.map(v => [v.id, v]));

        const pList: PayoutRequestItem[] = (pRes as { content?: PayoutRequestItem[] }).content || [];
        const sList: SettlementResponse[] = (res as { content?: SettlementResponse[] }).content || [];
        const sMap = new Map(sList.map(s => [s.id, s]));

        let mapped: PayoutExtended[] = [];

        if (pList.length > 0) {
          mapped = pList.map(p => {
            const v = vMap.get(p.vendorId);
            const s = sMap.get(p.settlementId);
            const periodStr = s ? `${s.periodStart.slice(0, 10)} – ${s.periodEnd.slice(0, 10)}` : 'Kỳ đối soát hoàn tất';
            return {
              id: p.id,
              vendorId: p.vendorId,
              settlementId: p.settlementId,
              amount: Number(p.amount) || 0,
              status: p.status as unknown as PayoutExtended['status'],
              processedBy: p.processedBy ? 'Ban Quản trị DANASEA' : '',
              processedAt: p.processedAt || p.updatedAt,
              vendorName: v?.businessName || `Đối tác ${p.vendorId.substring(0, 8)}`,
              bankName: v?.bankName || 'Vietcombank (Hàng hải)',
              bankAccountNumber: v?.bankAccountNumber || '****',
              bankAccountHolder: v?.bankAccountHolder || v?.businessName || 'Chủ tài khoản đối tác',
              periodText: `Kỳ: ${periodStr}`,
              totalOrdersCount: 0,
              createdAt: p.createdAt,
              updatedAt: p.updatedAt,
              bankReference: p.bankReference,
              rejectionReason: p.rejectionReason,
              adminNote: p.adminNote,
              note: p.note || p.vendorNote,
            };
          });
        } else {
          mapped = sList.map(s => {
            const v = vMap.get(s.vendorId);
            return {
              id: s.id,
              vendorId: s.vendorId,
              settlementId: s.id,
              amount: s.netPayableAmount,
              status: (s.status === 'PAID' || s.status === 'FINALIZED' ? 'PAID' : 'REQUESTED') as unknown as PayoutExtended['status'],
              processedBy: s.status === 'FINALIZED' ? 'Ban Quản trị DANASEA' : '',
              processedAt: s.updatedAt,
              vendorName: v?.businessName || `Đối tác ${s.vendorId.substring(0, 8)}`,
              bankName: v?.bankName || 'Vietcombank',
              bankAccountNumber: v?.bankAccountNumber || '****',
              bankAccountHolder: v?.bankAccountHolder || v?.businessName || 'Chủ tài khoản đối tác',
              periodText: `Kỳ đối soát ${s.periodStart.slice(0, 10)} - ${s.periodEnd.slice(0, 10)}`,
              totalOrdersCount: 0,
              createdAt: s.createdAt,
              updatedAt: s.updatedAt,
            };
          });
        }

        setPayouts(mapped);
        if (mapped.length > 0) {
          setSelectedPayoutId(prev => prev && mapped.some(p => p.id === prev) ? prev : mapped[0].id);
        }
        if (vList.length > 0) {
          setGenVendorId(prev => prev || vList[0].id);
        }
        setLoading(false);
      })
      .catch(err => {
        const msg = getAdminSettlementError(err, 'Không thể tải danh sách quyết toán.');
        setErrorMessage(msg);
        setLoading(false);
      });
  }, [demo]);

  useEffect(() => {
    loadSettlementsData();
  }, [loadSettlementsData]);

  // Load Line Items for Selected Settlement
  useEffect(() => {
    if (demo || !selectedPayoutId) return;
    let isMounted = true;
    setLoadingDetail(true);

    const targetPayout = payouts.find(p => p.id === selectedPayoutId);
    const targetSettlementId = targetPayout?.settlementId || selectedPayoutId;

    adminSettlementService.getSettlementDetail(targetSettlementId)
      .then(detail => {
        if (!isMounted) return;
        setSelectedDetail(detail);
        setPayouts(prev => prev.map(p => p.id === selectedPayoutId ? { ...p, totalOrdersCount: detail.lineItems?.length || 0 } : p));
        setLoadingDetail(false);
      })
      .catch(err => {
        if (!isMounted) return;
        console.debug('Failed to load settlement detail:', err);
        setLoadingDetail(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo, selectedPayoutId, payouts]);

  // Metrics
  const metrics = useMemo(() => calculatePayoutMetrics(payouts), [payouts]);

  // Filtered List
  const filteredPayouts = useMemo(() => {
    return filterPayouts(payouts, searchQuery, statusFilter);
  }, [payouts, searchQuery, statusFilter]);

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);

  const totalPages = Math.ceil(filteredPayouts.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginatedPayouts = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filteredPayouts.slice(start, start + PAGE_SIZE);
  }, [filteredPayouts, activePage]);

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
      // 1. If status is REQUESTED, approve it
      if (selectedPayout.status === 'REQUESTED') {
        await adminPayoutService.approvePayout(selectedPayout.id, bankRefCode);
        setPayouts(prev => prev.map(p => p.id === selectedPayout.id ? { 
          ...p, 
          status: 'APPROVED' as unknown as PayoutExtended['status'], 
          processedBy: 'Ban Quản trị DANASEA', 
          processedAt: new Date().toISOString() 
        } : p));
        setIsApproveModalOpen(false);
        setNotice(`Đã phê duyệt lệnh chi trả #${selectedPayout.id.slice(0, 8).toUpperCase()}. Tiếp theo có thể bấm xác nhận giải ngân chuyển khoản.`);
        return;
      }

      // 2. If status is APPROVED, mark as PAID with bankRefCode
      if (selectedPayout.status === 'APPROVED') {
        await adminPayoutService.markPaid(selectedPayout.id, bankRefCode);
        setPayouts(prev => prev.map(p => p.id === selectedPayout.id ? { 
          ...p, 
          status: 'PAID' as unknown as PayoutExtended['status'], 
          bankReference: bankRefCode,
          processedBy: 'Ban Quản trị DANASEA', 
          processedAt: new Date().toISOString() 
        } : p));
        setIsApproveModalOpen(false);
        setNotice(`Đã xác nhận giải ngân thành công (Ref: ${bankRefCode}) cho đối tác ${selectedPayout.vendorName}.`);
        return;
      }

      // 3. Fallback for settlements
      await adminSettlementService.finalizeSettlement(selectedPayout.id);
      setPayouts(prev => prev.map(p => p.id === selectedPayout.id ? { ...p, status: 'PAID' as unknown as PayoutExtended['status'], processedBy: 'Ban Quản trị DANASEA', processedAt: new Date().toISOString() } : p));
      setIsApproveModalOpen(false);
      setNotice(`Đã chốt sổ và giải ngân kỳ đối soát #${selectedPayout.id.slice(0, 8).toUpperCase()}.`);
    } catch (err) {
      const msg = getAdminSettlementError(err, 'Lỗi khi xử lý lệnh chi trả.');
      setNotice(msg);
      setIsApproveModalOpen(false);
    }
  };

  const handleRejectPayoutSubmit = async () => {
    if (!selectedPayout) return;
    if (demo) {
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
      return;
    }

    try {
      await adminPayoutService.rejectPayout(selectedPayout.id, rejectReason);
      setPayouts(prev => prev.map(p => p.id === selectedPayout.id ? { 
        ...p, 
        status: 'REJECTED' as unknown as PayoutExtended['status'], 
        rejectionReason: rejectReason,
        processedBy: 'Ban Quản trị DANASEA', 
        processedAt: new Date().toISOString() 
      } : p));
      setIsRejectModalOpen(false);
      setNotice(`Đã từ chối lệnh chi trả #${selectedPayout.id.slice(0, 8).toUpperCase()}: ${rejectReason}`);
    } catch (err) {
      const msg = getAdminSettlementError(err, 'Lỗi khi từ chối lệnh chi trả.');
      setNotice(msg);
      setIsRejectModalOpen(false);
    }
  };

  const handleGenerateSettlementSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!genVendorId) {
      setNotice('Vui lòng chọn đối tác vận hành');
      return;
    }
    try {
      setIsGenerating(true);
      const newSettlement = await adminSettlementService.generateSettlement({
        vendorId: genVendorId,
        periodStart: genPeriodStart,
        periodEnd: genPeriodEnd,
      });
      setIsGenerating(false);
      setIsGenerateModalOpen(false);
      setNotice(`Khởi tạo kỳ đối soát #${newSettlement.id.substring(0, 8)} thành công!`);
      loadSettlementsData();
      setSelectedPayoutId(newSettlement.id);
    } catch (err) {
      setIsGenerating(false);
      const msg = getAdminSettlementError(err, 'Lỗi khi khởi tạo kỳ đối soát.');
      setNotice(msg);
    }
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
          <span className="admin-payouts__summary-badge" style={{ background: '#e0f2fe', color: '#0369a1', borderColor: '#bae6fd' }}>
            <Clock size={14} />
            Đã duyệt: {metrics.approvedCount}
          </span>
          <span style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
            ({metrics.totalApprovedAmount.toLocaleString('vi-VN')} đ)
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
                <CreditCard size={18} style={{ color: 'var(--admin-primary, #0B5D6F)' }} />
                Danh sách lệnh chi trả
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 12, color: 'var(--admin-muted, #595959)', fontWeight: 600 }}>
                  {filteredPayouts.length} / {payouts.length} lệnh
                </span>
                <AdminButton
                  variant="primary"
                  onClick={() => setIsGenerateModalOpen(true)}
                  style={{ fontSize: 12, padding: '4px 8px' }}
                  title="Khởi tạo kỳ đối soát mới"
                >
                  <Plus size={13} />
                  Đối soát mới
                </AdminButton>
                <AdminButton
                  onClick={loadSettlementsData}
                  style={{ fontSize: 12, padding: '4px 8px' }}
                  title="Làm mới dữ liệu"
                >
                  <RefreshCw size={13} />
                </AdminButton>
              </div>
            </div>

            {/* Filter Tabs */}
            <AdminFilterTabs
              label="Lọc trạng thái lệnh chi"
              value={statusFilter}
              onChange={(val) => {
                setStatusFilter(val as PayoutStatusFilter);
                setCurrentPage(1);
              }}
              items={[
                { value: 'all', label: 'Tất cả', count: metrics.totalCount },
                { value: 'REQUESTED', label: 'Chờ duyệt', count: metrics.pendingCount },
                { value: 'APPROVED', label: 'Đã duyệt', count: metrics.approvedCount },
                { value: 'PAID', label: 'Đã giải ngân', count: metrics.paidCount },
                { value: 'REJECTED', label: 'Từ chối', count: metrics.rejectedCount },
              ]}
            />

            {/* Action Bar: Search & Export */}
            <div className="admin-payouts__action-bar">
              <div className="admin-payouts__search-box">
                <Search size={14} className="admin-payouts__search-icon" aria-hidden="true" />
                <input
                  type="text"
                  placeholder="Tìm theo mã lệnh, đối tác, số TK..."
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="admin-payouts__search-input"
                  aria-label="Tìm kiếm lệnh chi trả"
                />
              </div>

              <AdminButton
                variant="secondary"
                onClick={handleExportStatement}
                disabled={filteredPayouts.length === 0}
                title="Xuất bảng kê chi trả ra file CSV"
              >
                <Download size={14} aria-hidden="true" />
                Xuất CSV
              </AdminButton>
            </div>
          </div>

          {/* List Body with Skeleton / Empty / Error */}
          <div className="admin-payouts__list-viewport">
            <div className="admin-payouts__list" role="list" aria-label="Danh sách các lệnh chi">
            {loading ? (
              <div style={{ padding: 16 }}>
                <AdminState
                  kind="loading"
                  title="Đang tải dữ liệu lệnh chi..."
                  description="Hệ thống đang đồng bộ dữ liệu quyết toán từ đối tác và ngân hàng."
                />
              </div>
            ) : errorMessage ? (
              <div style={{ padding: 16 }}>
                <AdminState
                  kind="error"
                  title="Không thể tải dữ liệu"
                  description={errorMessage}
                  action={
                    <AdminButton variant="primary" onClick={loadSettlementsData}>
                      Thử lại
                    </AdminButton>
                  }
                />
              </div>
            ) : paginatedPayouts.length === 0 ? (
              <div style={{ padding: 16 }}>
                <AdminState
                  kind="empty"
                  title="Không có lệnh chi nào phù hợp"
                  description={
                    searchQuery
                      ? 'Thử thay đổi từ khóa tìm kiếm hoặc bỏ lọc trạng thái.'
                      : 'Hiện chưa có lệnh chi trả nào trong hệ thống.'
                  }
                  action={
                    searchQuery ? (
                      <AdminButton
                        onClick={() => {
                          setSearchQuery('');
                          setStatusFilter('all');
                        }}
                      >
                        Xóa bộ lọc
                      </AdminButton>
                    ) : undefined
                  }
                />
              </div>
            ) : (
              paginatedPayouts.map((p) => {
                const isSelected = p.id === selectedPayout?.id;
                return (
                  <div
                    key={p.id}
                    role="listitem"
                    tabIndex={0}
                    className={`admin-payouts__item ${isSelected ? 'admin-payouts__item--selected' : ''}`}
                    onClick={() => setSelectedPayoutId(p.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setSelectedPayoutId(p.id);
                      }
                    }}
                    aria-selected={isSelected}
                  >
                    <div className="admin-payouts__item-top">
                      <span className="admin-payouts__item-id">#{p.id.substring(0, 12)}...</span>
                      <AdminBadge
                        tone={
                          p.status === 'PAID'
                            ? 'teal'
                            : p.status === 'APPROVED'
                            ? 'teal'
                            : p.status === 'REQUESTED'
                            ? 'amber'
                            : 'rose'
                        }
                      >
                        {p.status === 'PAID' ? 'Đã giải ngân' : p.status === 'APPROVED' ? 'Đã duyệt' : p.status === 'REQUESTED' ? 'Chờ duyệt' : 'Từ chối'}
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

          {!loading && !errorMessage && filteredPayouts.length > 0 && (
            <AdminPagination
              currentPage={activePage}
              totalPages={totalPages}
              totalRecords={filteredPayouts.length}
              pageSize={PAGE_SIZE}
              recordLabel="lệnh chi trả"
              onPageChange={setCurrentPage}
            />
          )}
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
                    : selectedPayout.status === 'APPROVED'
                    ? 'teal'
                    : selectedPayout.status === 'REQUESTED'
                    ? 'amber'
                    : 'rose'
                }
              >
                {selectedPayout.status === 'PAID' ? 'ĐÃ GIẢI NGÂN' : selectedPayout.status === 'APPROVED' ? 'ĐÃ DUYỆT' : selectedPayout.status === 'REQUESTED' ? 'CHỜ DUYỆT' : 'TỪ CHỐI'}
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
              <div style={{ padding: '8px 12px', background: '#ffffff', borderRadius: 8, border: '1px solid #ccfbf1', fontSize: 12, color: 'var(--admin-primary, #0B5D6F)', fontWeight: 600 }}>
                {selectedDetail?.lineItems?.length ?? selectedPayout.totalOrdersCount} đơn tour hoàn tất
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
              title={`Danh sách đơn tour cấu thành (${selectedDetail?.lineItems?.length ?? selectedPayout.totalOrdersCount ?? 0} đơn)`}
              description="Các dịch vụ trải nghiệm biển đã hoàn thành và đối soát trong kỳ"
            >
              <div className="admin-payouts__orders-breakdown">
                {loadingDetail ? (
                  <div style={{ padding: '12px', textAlign: 'center', fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
                    Đang tải chi tiết các đơn hàng cấu thành...
                  </div>
                ) : selectedDetail && selectedDetail.lineItems && selectedDetail.lineItems.length > 0 ? (
                  selectedDetail.lineItems.map(item => (
                    <div key={item.id} className="admin-payouts__orders-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f1f5f9' }}>
                      <div>
                        <div style={{ fontFamily: 'monospace', fontWeight: 600, color: 'var(--admin-ink, #171717)', fontSize: 13 }}>
                          #VE-{item.subOrderId.substring(0, 8).toUpperCase()}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--admin-muted, #64748b)' }}>
                          Doanh thu: {item.grossAmount.toLocaleString('vi-VN')} đ • Phí sàn: -{item.commissionAmount.toLocaleString('vi-VN')} đ ({Math.round(item.commissionRate * 100)}%)
                        </div>
                      </div>
                      <strong style={{ color: 'var(--admin-primary, #0B5D6F)', fontSize: 13 }}>
                        {item.netAmount.toLocaleString('vi-VN')} đ
                      </strong>
                    </div>
                  ))
                ) : (
                  <div style={{ padding: '12px', textAlign: 'center', fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
                    Chưa có đơn hàng con nào trong kỳ đối soát này.
                  </div>
                )}
                <div className="admin-payouts__orders-footer">
                  <span>Tổng số tiền thực nhận (Net):</span>
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
                    Phê duyệt yêu cầu rút tiền
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
              ) : selectedPayout.status === 'APPROVED' ? (
                <>
                  <AdminButton
                    variant="primary"
                    onClick={() => setIsApproveModalOpen(true)}
                    style={{ width: '100%', justifyContent: 'center' }}
                  >
                    <CheckCircle2 size={16} aria-hidden="true" />
                    Xác nhận giải ngân chuyển khoản (Ghi nhận mã Ref)
                  </AdminButton>
                  <div style={{ fontSize: 12, color: 'var(--admin-muted, #737373)', textAlign: 'center', marginTop: 4 }}>
                    Lệnh đã được duyệt. Bấm nút trên sau khi kế toán thực hiện ủy nhiệm chi.
                  </div>
                </>
              ) : selectedPayout.status === 'REJECTED' ? (
                <div className="admin-payouts__processed-notice" style={{ background: '#fef2f2', borderColor: '#fecaca', color: '#991b1b' }}>
                  <Ban size={18} />
                  <span>
                    Lệnh chi đã bị từ chối: <strong>{selectedPayout.rejectionReason || 'Hồ sơ không hợp lệ'}</strong>.
                  </span>
                </div>
              ) : (
                <div className="admin-payouts__processed-notice">
                  <CheckCircle2 size={18} />
                  <div>
                    <div>
                      Lệnh chi đã được giải ngân thành công bởi{' '}
                      <strong>{selectedPayout.processedBy || 'Ban Quản trị DANASEA'}</strong>
                      {selectedPayout.processedAt ? ` vào ngày ${selectedPayout.processedAt.slice(0, 10)}` : ''}.
                    </div>
                    {selectedPayout.bankReference && (
                      <div style={{ fontFamily: 'monospace', fontWeight: 600, color: '#047857', marginTop: 2 }}>
                        Mã giao dịch / Ref: {selectedPayout.bankReference}
                      </div>
                    )}
                  </div>
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

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="payout-bank-ref" style={{ fontWeight: 600, fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
              Mã giao dịch ngân hàng / Ref Code:
            </label>
            <input
              id="payout-bank-ref"
              type="text"
              value={bankRefCode}
              onChange={(e) => setBankRefCode(e.target.value)}
              className="admin-payouts__search-input"
              style={{ width: '100%', padding: '8px 12px' }}
            />
          </div>
        </div>
      </AdminDialog>

      {/* Reject Payout Modal */}
      <AdminDialog
        open={isRejectModalOpen}
        onClose={() => setIsRejectModalOpen(false)}
        variant="modal"
        title="Từ chối lệnh chi trả"
        description="Gửi thông báo yêu cầu làm rõ hồ sơ đối soát lại cho đối tác."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsRejectModalOpen(false)}>Hủy bỏ</AdminButton>
            <AdminButton variant="primary" onClick={handleRejectPayoutSubmit} style={{ background: '#b91c1c' }}>
              Xác nhận từ chối
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: 13, color: 'var(--admin-body, #262626)' }}>
          <p>
            Bạn có chắc chắn muốn từ chối lệnh chi{' '}
            <strong style={{ fontFamily: 'monospace' }}>#{selectedPayout?.id}</strong> không?
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="reject-payout-reason" style={{ fontWeight: 600, fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
              Lý do từ chối:
            </label>
            <textarea
              id="reject-payout-reason"
              rows={3}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              className="admin-payouts__search-input"
              style={{ width: '100%', padding: '8px 12px', resize: 'vertical' }}
            />
          </div>
        </div>
      </AdminDialog>

      {/* Generate Settlement Modal */}
      <AdminDialog
        open={isGenerateModalOpen}
        onClose={() => !isGenerating && setIsGenerateModalOpen(false)}
        variant="modal"
        title="Khởi tạo kỳ đối soát tài chính mới"
        description="Quét toàn bộ các đơn hàng con hoàn tất trong khoảng thời gian để kết chuyển doanh thu và tính phí hoa hồng sàn."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton disabled={isGenerating} onClick={() => setIsGenerateModalOpen(false)}>
              Hủy
            </AdminButton>
            <AdminButton variant="primary" disabled={isGenerating} onClick={handleGenerateSettlementSubmit}>
              {isGenerating ? 'Đang khởi tạo...' : 'Khởi tạo đối soát'}
            </AdminButton>
          </div>
        }
      >
        <form onSubmit={handleGenerateSettlementSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14, fontSize: 13 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label style={{ fontWeight: 600, fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
              Đối tác vận hành (Vendor):
            </label>
            <select
              value={genVendorId}
              onChange={(e) => setGenVendorId(e.target.value)}
              className="admin-payouts__search-input"
              style={{ width: '100%', padding: '8px 12px', background: '#fff' }}
              required
            >
              <option value="">-- Chọn đối tác --</option>
              {vendors.map(v => (
                <option key={v.id} value={v.id}>
                  {v.businessName || v.id}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <label style={{ fontWeight: 600, fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
                Từ ngày (Period Start):
              </label>
              <input
                type="date"
                value={genPeriodStart}
                onChange={(e) => setGenPeriodStart(e.target.value)}
                className="admin-payouts__search-input"
                style={{ width: '100%', padding: '8px 12px' }}
                required
              />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <label style={{ fontWeight: 600, fontSize: 12, color: 'var(--admin-secondary, #525252)' }}>
                Đến ngày (Period End):
              </label>
              <input
                type="date"
                value={genPeriodEnd}
                onChange={(e) => setGenPeriodEnd(e.target.value)}
                className="admin-payouts__search-input"
                style={{ width: '100%', padding: '8px 12px' }}
                required
              />
            </div>
          </div>
        </form>
      </AdminDialog>
    </div>
  );
}
