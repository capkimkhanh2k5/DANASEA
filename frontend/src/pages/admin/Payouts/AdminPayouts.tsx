import { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import axios from 'axios';
import { adminSettlementService, getAdminSettlementError } from '../../../services/adminSettlementService';
import { apiClient } from '../../../services/apiClient';
import { useAuthStore } from '../../../store/useAuthStore';
import type {
  SettlementResponse,
  SettlementDetailResponse,
} from '../../../types/adminSettlement';

interface VendorLookup {
  id: string;
  businessName: string;
  bankName?: string;
  bankAccountNumber?: string;
  bankAccountHolder?: string;
}

export function AdminPayouts() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Data state
  const [settlements, setSettlements] = useState<SettlementResponse[]>([]);
  const [selectedSettlementId, setSelectedSettlementId] = useState<string>('');
  const [selectedDetail, setSelectedDetail] = useState<SettlementDetailResponse | null>(null);
  const [vendorsMap, setVendorsMap] = useState<Record<string, VendorLookup>>({});

  // Loading & error state
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isVendorLoading, setIsVendorLoading] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Financial verification guard after timeout/unknown outcome
  const [requiresVerification, setRequiresVerification] = useState<boolean>(false);

  // Filters & pagination
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [totalSettlementsCount, setTotalSettlementsCount] = useState<number>(0);

  // Banking Info Modal & Finalize Confirmation Modal
  const [isDisburseModalOpen, setIsDisburseModalOpen] = useState(false);
  const [isFinalizeConfirmOpen, setIsFinalizeConfirmOpen] = useState(false);

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Mutex lock for mutations & sequence counter for detail fetch
  const mutationLockRef = useRef<string | null>(null);
  const detailReqSeqRef = useRef<number>(0);

  // Background lookup for vendor details
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        const res = await apiClient.get<{ content?: VendorLookup[] }>('/admin/vendors', {
          params: { size: 100 },
        });
        if (active && res.data?.content) {
          const map: Record<string, VendorLookup> = {};
          res.data.content.forEach(v => {
            map[v.id] = v;
          });
          setVendorsMap(map);
        }
      } catch {
        // Non-blocking lookup
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Initial load of settlements (tối đa 100 bản ghi mới nhất)
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        setIsLoading(true);
        setError(null);
        const data = await adminSettlementService.getSettlements({ size: 100 });
        if (!active) return;
        const items = data.content || [];
        setSettlements(items);
        setTotalSettlementsCount(data.totalElements ?? items.length);
        if (items.length > 0) {
          setSelectedSettlementId(prev => (items.some(s => s.id === prev) ? prev : items[0].id));
        }
      } catch (err) {
        if (active) {
          setError(getAdminSettlementError(err, 'Không thể tải danh sách đối soát từ máy chủ'));
        }
      } finally {
        if (active) setIsLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Load detail asynchronously for selected settlement
  useEffect(() => {
    let active = true;
    if (!selectedSettlementId || !canAccess) return;

    const seq = ++detailReqSeqRef.current;
    const targetId = selectedSettlementId;

    void (async () => {
      try {
        setIsDetailLoading(true);
        setActionError(null);
        const detail = await adminSettlementService.getSettlementDetail(targetId);
        if (!active || detailReqSeqRef.current !== seq) return;
        setSelectedDetail(detail);
      } catch (err) {
        if (!active || detailReqSeqRef.current !== seq) return;
        setActionError(getAdminSettlementError(err, 'Không thể tải chi tiết đối soát'));
      } finally {
        if (active && detailReqSeqRef.current === seq) {
          setIsDetailLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedSettlementId, canAccess]);

  const handleSelectSettlement = (settlementId: string) => {
    if (isMutating || settlementId === selectedSettlementId) return;
    setSelectedSettlementId(settlementId);
    setSelectedDetail(null);
    setActionError(null);
  };

  // Manual refresh handler
  const handleRefresh = async () => {
    if (!canAccess || isLoading || isMutating) return;
    setIsLoading(true);
    setError(null);
    setRequiresVerification(false);
    try {
      const data = await adminSettlementService.getSettlements({ size: 100 });
      const items = data.content || [];
      setSettlements(items);
      setTotalSettlementsCount(data.totalElements ?? items.length);
      if (items.length > 0) {
        setSelectedSettlementId(prev => (items.some(s => s.id === prev) ? prev : items[0].id));
      }
    } catch (err) {
      setError(getAdminSettlementError(err, 'Không thể tải danh sách đối soát'));
    } finally {
      setIsLoading(false);
    }
  };

  const filteredSettlements = useMemo(() => {
    return settlements.filter(s => {
      if (statusFilter === 'all') return true;
      if (statusFilter === 'REQUESTED') return s.status === 'PENDING' || s.status === 'DRAFT';
      return s.status === statusFilter;
    });
  }, [settlements, statusFilter]);

  const selectedPayout = settlements.find(s => s.id === selectedSettlementId) || settlements[0] || null;
  const currentVendor = selectedPayout ? vendorsMap[selectedPayout.vendorId] : null;

  // On-demand vendor lookup nếu vendorId chưa có trong map (vượt quá 100 bản ghi trang đầu)
  useEffect(() => {
    let active = true;
    if (!selectedPayout?.vendorId || !canAccess) return;
    const targetVendorId = selectedPayout.vendorId;
    if (vendorsMap[targetVendorId]) return;

    void (async () => {
      try {
        setIsVendorLoading(true);
        const res = await apiClient.get<VendorLookup>(`/admin/vendors/${targetVendorId}`);
        if (!active || !res.data) return;
        setVendorsMap(prev => ({
          ...prev,
          [targetVendorId]: res.data,
        }));
      } catch {
        // Giữ fallback nếu không tìm thấy
      } finally {
        if (active) setIsVendorLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedPayout?.vendorId, canAccess, vendorsMap]);

  // Finalize settlement action (chốt sổ đối soát có khóa Mutex chặt chẽ)
  const handleFinalizeSettlement = async () => {
    // Kiểm tra khóa mutex và điều kiện an toàn trước khi phát request để chống duplicate mutation
    if (!selectedPayout || isMutating || mutationLockRef.current !== null) return;
    if (selectedPayout.status === 'FINALIZED' || selectedPayout.status === 'PAID') return;

    if (requiresVerification) {
      setActionError('Giao dịch trước đó chưa rõ kết quả do mất kết nối hoặc timeout. Vui lòng bấm "Làm mới" để đồng bộ trạng thái máy chủ trước khi tiếp tục.');
      return;
    }

    const targetId = selectedPayout.id;
    mutationLockRef.current = targetId;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminSettlementService.finalizeSettlement(targetId);
      if (mutationLockRef.current === targetId) {
        setSettlements(prev => prev.map(s => (s.id === updated.id ? updated : s)));
        setSelectedDetail(prev => (prev?.id === updated.id ? { ...prev, status: updated.status } : prev));
        setIsFinalizeConfirmOpen(false);
        showToast(`Đã chốt sổ kỳ đối soát #${updated.id.slice(0, 8)} (FINALIZED) thành công!`);

        // Refresh detail to synchronize authoritative state
        try {
          const refreshed = await adminSettlementService.getSettlementDetail(updated.id);
          if (mutationLockRef.current === targetId) {
            setSelectedDetail(refreshed);
          }
        } catch {
          // Keep existing state on refresh network error
        }
      }
    } catch (err) {
      if (mutationLockRef.current === targetId) {
        const isUnknown = axios.isAxiosError(err) && (err.code === 'ECONNABORTED' || err.message.toLowerCase().includes('timeout') || (!err.response && !!err.request));
        if (isUnknown) {
          setRequiresVerification(true);
        }
        setActionError(getAdminSettlementError(err, 'Chốt sổ kỳ đối soát thất bại'));
      }
    } finally {
      if (mutationLockRef.current === targetId) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Xuất báo cáo đối soát ra file CSV (chuẩn hóa chống formula injection)
  const handleExportStatement = () => {
    const sanitizeCsvCell = (val: string | number | undefined | null) => {
      if (val === undefined || val === null) return '""';
      let str = String(val).replace(/"/g, '""');
      if (/^[=+\-@\t\r]/.test(str)) {
        str = `'${str}`;
      }
      return `"${str}"`;
    };

    const headers = 'Mã Lệnh,Đối Tác,Kỳ Đối Soát,Doanh Thu,Hoa Hồng,Giải Ngân,Trạng Thái,Ngân Hàng,Số Tài Khoản\n';
    const rows = filteredSettlements
      .map(p => {
        const v = vendorsMap[p.vendorId];
        const vName = v?.businessName || `Đối tác #${p.vendorId.slice(0, 8)}`;
        const period = `${p.periodStart} - ${p.periodEnd}`;
        const vBank = v?.bankName || 'Chưa cập nhật';
        const vAcc = v?.bankAccountNumber || 'Chưa cập nhật';
        return `${sanitizeCsvCell(p.id)},${sanitizeCsvCell(vName)},${sanitizeCsvCell(period)},${sanitizeCsvCell(p.grossAmount)},${sanitizeCsvCell(p.commissionAmount)},${sanitizeCsvCell(p.netPayableAmount)},${sanitizeCsvCell(p.status)},${sanitizeCsvCell(vBank)},${sanitizeCsvCell(vAcc)}`;
      })
      .join('\n');

    const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `DANASEA_Payout_Statement_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast(`Đã xuất ${filteredSettlements.length} kỳ đối soát sang file CSV (tối đa 100 bản ghi hiện hành)!`);
  };

  // Metrics: Phân biệt rõ ràng giữa FINALIZED và PAID
  const pendingCount = settlements.filter(p => p.status === 'PENDING' || p.status === 'DRAFT').length;
  const finalizedCount = settlements.filter(p => p.status === 'FINALIZED').length;
  const paidCount = settlements.filter(p => p.status === 'PAID').length;
  const totalFinalizedAmount = settlements
    .filter(p => p.status === 'FINALIZED')
    .reduce((sum, p) => sum + (p.netPayableAmount || 0), 0);
  const totalPaidAmount = settlements
    .filter(p => p.status === 'PAID')
    .reduce((sum, p) => sum + (p.netPayableAmount || 0), 0);

  if (!canAccess) {
    return (
      <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
        <div className="max-w-xl mx-auto mt-12 p-space-xl rounded-2xl bg-white border border-slate-200 text-center shadow-xs">
          <span className="material-symbols-outlined text-[48px] text-amber-500 mb-2">lock</span>
          <h2 className="text-lg font-bold text-slate-800">Yêu cầu quyền Quản trị viên</h2>
          <p className="text-xs text-slate-500 mt-1">
            Vui lòng đăng nhập với tài khoản có quyền ADMIN để quản lý đối soát và giải ngân đối tác.
          </p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
      <div className="flex flex-col w-full animate-fade-in-up">
        {/* Toast */}
        {toastMessage && (
          <div className="fixed top-20 right-8 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-primary text-on-primary shadow-xl animate-fade-in-up">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            <span className="font-label-md text-label-md font-semibold">{toastMessage}</span>
          </div>
        )}

        {/* Global error banner */}
        {error && (
          <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">error</span>
              <span>{error}</span>
            </div>
            <button onClick={handleRefresh} className="font-bold underline hover:no-underline">
              Thử lại
            </button>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-slate-200">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
                Quản lý Chi trả (Payouts &amp; Settlements)
              </h1>
              <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600 text-xs font-medium">
                Hiển thị: <strong>{settlements.length}</strong> / <strong>{totalSettlementsCount || settlements.length}</strong>
              </span>
            </div>
            <p className="text-slate-500 text-xs mt-1">
              Thống kê &amp; xuất file áp dụng cho danh sách hiển thị (tối đa 100 kỳ gần nhất).
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={handleRefresh}
              disabled={isLoading || isMutating}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors disabled:opacity-50"
              title="Làm mới dữ liệu từ máy chủ"
            >
              <span className={`material-symbols-outlined text-[18px] ${isLoading ? 'animate-spin' : ''}`}>
                refresh
              </span>
              <span>Làm mới</span>
            </button>
            <span className="px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 border border-amber-200 text-xs font-semibold">
              Chờ chốt: {pendingCount}
            </span>
            <span className="px-2.5 py-1 rounded-md bg-blue-50 text-blue-700 border border-blue-200 text-xs font-semibold">
              Đã chốt sổ: {finalizedCount} ({totalFinalizedAmount.toLocaleString('vi-VN')} đ)
            </span>
            <span className="px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold">
              Đã giải ngân: {paidCount} ({totalPaidAmount.toLocaleString('vi-VN')} đ)
            </span>
            <button
              onClick={handleExportStatement}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">file_download</span>
              <span>Xuất CSV</span>
            </button>
          </div>
        </div>

        {/* Main 2-Column Split Console */}
        <div className="grid grid-cols-12 gap-space-lg items-start">
          {/* LEFT COLUMN: Payout List (7 cols) */}
          <div className="col-span-12 lg:col-span-7 flex flex-col gap-space-md">
            <div className="p-space-md rounded-xl bg-white shadow-xs flex items-center justify-between border border-slate-200">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">payments</span>
                <span className="font-bold text-slate-800 text-sm">Kỳ Đối Soát &amp; Lệnh Quyết Toán</span>
              </div>
              <div className="flex gap-1.5 p-1 bg-slate-100 rounded-xl border border-slate-200">
                <button
                  onClick={() => setStatusFilter('all')}
                  className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                    statusFilter === 'all'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Tất cả
                </button>
                <button
                  onClick={() => setStatusFilter('REQUESTED')}
                  className={`px-3 py-1.5 rounded-lg text-xs flex items-center gap-1.5 transition-all ${
                    statusFilter === 'REQUESTED'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <span>Chờ duyệt</span>
                  {pendingCount > 0 && (
                    <span
                      className={`px-1.5 py-0.2 rounded-full font-bold text-[10px] ${
                        statusFilter === 'REQUESTED' ? 'bg-white/20 text-white' : 'bg-slate-200 text-slate-700'
                      }`}
                    >
                      {pendingCount}
                    </span>
                  )}
                </button>
                <button
                  onClick={() => setStatusFilter('FINALIZED')}
                  className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                    statusFilter === 'FINALIZED'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Đã chốt sổ
                </button>
                <button
                  onClick={() => setStatusFilter('PAID')}
                  className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                    statusFilter === 'PAID'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Đã giải ngân
                </button>
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-xs overflow-hidden border border-slate-200">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 text-slate-700 text-xs font-semibold border-b border-slate-200">
                    <th className="py-3 px-4 border-r border-slate-200 whitespace-nowrap">Mã kỳ &amp; Đối tác</th>
                    <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Kỳ đối soát</th>
                    <th className="py-3 px-3 text-right border-r border-slate-200 whitespace-nowrap">Số tiền giải ngân</th>
                    <th className="py-3 px-3 text-center border-r border-slate-200 whitespace-nowrap">Trạng thái</th>
                    <th className="py-3 px-4 text-center whitespace-nowrap">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 text-xs text-slate-700">
                  {filteredSettlements.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-12 text-center text-slate-400">
                        <span className="material-symbols-outlined text-[32px] text-slate-300 mb-1">
                          folder_off
                        </span>
                        <p className="text-xs">Không tìm thấy kỳ đối soát nào</p>
                      </td>
                    </tr>
                  ) : (
                    filteredSettlements.map(payout => {
                      const isSelected = payout.id === selectedPayout?.id;
                      const vendor = vendorsMap[payout.vendorId];
                      const vendorName = vendor?.businessName || `Đối tác #${payout.vendorId.slice(0, 8)}`;
                      const periodText = `${payout.periodStart} → ${payout.periodEnd}`;

                      return (
                        <tr
                          key={payout.id}
                          onClick={() => handleSelectSettlement(payout.id)}
                          className={`cursor-pointer transition-colors ${
                            isMutating ? 'opacity-70 cursor-not-allowed' : ''
                          } ${
                            isSelected ? 'bg-primary/5 hover:bg-primary/10' : 'hover:bg-slate-50'
                          }`}
                        >
                          <td className="py-3.5 px-4 border-r border-slate-200">
                            <span className="font-mono font-bold text-primary block">
                              #{payout.id.slice(0, 8)}
                            </span>
                            <span className="font-bold text-slate-800 text-sm truncate max-w-[170px] block" title={vendorName}>
                              {vendorName}
                            </span>
                          </td>

                          <td className="py-3.5 px-3 text-slate-500 border-r border-slate-200 whitespace-nowrap font-mono text-[11px]">
                            {periodText}
                          </td>

                          <td className="py-3.5 px-3 text-right font-bold text-slate-800 text-sm border-r border-slate-200 whitespace-nowrap">
                            {(payout.netPayableAmount || 0).toLocaleString('vi-VN')} đ
                          </td>

                          <td className="py-3.5 px-3 text-center border-r border-slate-200 whitespace-nowrap">
                            <span
                              className={`px-2.5 py-1 rounded-full font-bold text-[10px] ${
                                payout.status === 'PAID'
                                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                  : payout.status === 'FINALIZED'
                                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                                  : payout.status === 'PENDING'
                                  ? 'bg-amber-50 text-amber-700 border border-amber-200'
                                  : payout.status === 'DRAFT'
                                  ? 'bg-slate-100 text-slate-700 border border-slate-200'
                                  : 'bg-red-50 text-red-700 border border-red-200'
                              }`}
                            >
                              {payout.status === 'PAID'
                                ? 'ĐÃ GIẢI NGÂN'
                                : payout.status === 'FINALIZED'
                                ? 'ĐÃ CHỐT SỔ'
                                : payout.status === 'PENDING'
                                ? 'CHỜ CHỐT SỔ'
                                : payout.status === 'DRAFT'
                                ? 'BẢN NHÁP'
                                : payout.status}
                            </span>
                          </td>

                          <td className="py-3.5 px-4 text-center whitespace-nowrap">
                            <div className="flex items-center justify-center">
                              <button
                                disabled={isMutating}
                                onClick={e => {
                                  e.stopPropagation();
                                  if (!isMutating) setSelectedSettlementId(payout.id);
                                }}
                                className={`p-1.5 rounded-lg border transition-colors inline-flex items-center justify-center disabled:opacity-50 ${
                                  isSelected
                                    ? 'bg-primary text-white border-primary shadow-xs'
                                    : 'bg-slate-100 hover:bg-slate-200 text-slate-700 border-slate-200'
                                }`}
                                title="Xem chi tiết"
                              >
                                <span className="material-symbols-outlined text-[18px]">chevron_right</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* RIGHT COLUMN: Payout Detail & Action Drawer (5 cols) */}
          {selectedPayout && (
            <div className="col-span-12 lg:col-span-5 flex flex-col gap-space-lg sticky top-20">
              <div className="bg-white rounded-xl shadow-xs p-space-lg flex flex-col gap-4 border border-slate-200">
                <div className="flex items-start justify-between pb-3 border-b border-slate-200">
                  <div>
                    <span className="text-xs font-medium text-primary">Kỳ đối soát #{selectedPayout.id.slice(0, 8)}</span>
                    <h2 className="text-lg font-bold text-slate-800 mt-0.5">
                      {currentVendor?.businessName || `Đối tác #${selectedPayout.vendorId.slice(0, 8)}`}
                    </h2>
                    <span className="text-xs text-slate-500 font-mono">
                      {selectedPayout.periodStart} → {selectedPayout.periodEnd}
                    </span>
                  </div>
                  <span
                    className={`px-2.5 py-1 rounded-full font-bold text-xs ${
                      selectedPayout.status === 'PAID'
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : selectedPayout.status === 'FINALIZED'
                        ? 'bg-blue-50 text-blue-700 border border-blue-200'
                        : 'bg-amber-50 text-amber-700 border border-amber-200'
                    }`}
                  >
                    {selectedPayout.status}
                  </span>
                </div>

                {actionError && (
                  <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
                    {actionError}
                  </div>
                )}

                {requiresVerification && (
                  <div className="p-3.5 rounded-xl bg-red-50 border border-red-300 text-red-800 text-xs flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[20px] text-red-600">warning</span>
                      <span>
                        Giao dịch chốt sổ trước đó chưa rõ kết quả do mất kết nối hoặc timeout. Để bảo đảm an toàn tài chính, thao tác đã bị khóa. Vui lòng bấm <strong>&quot;Làm mới&quot;</strong> để đối soát trạng thái thực tế từ máy chủ.
                      </span>
                    </div>
                    <button
                      onClick={handleRefresh}
                      disabled={isLoading}
                      className="ml-3 px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white font-bold text-xs whitespace-nowrap transition-colors"
                    >
                      Làm mới
                    </button>
                  </div>
                )}

                {/* Bank Details */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2">
                  <span className="text-xs font-semibold text-slate-800 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[16px] text-primary">account_balance</span>
                    Thông tin tài khoản ngân hàng thụ hưởng
                  </span>
                  <div className="flex justify-between items-center text-xs">
                    <span className="text-slate-500">Ngân hàng:</span>
                    <span className="font-bold text-slate-800">
                      {isVendorLoading && !currentVendor ? 'Đang tải thông tin...' : (currentVendor?.bankName || 'Chưa cập nhật')}
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-xs">
                    <span className="text-slate-500">Số tài khoản:</span>
                    <span className="font-mono font-bold text-primary text-sm">
                      {isVendorLoading && !currentVendor ? 'Đang tải...' : (currentVendor?.bankAccountNumber || 'Chưa cập nhật')}
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-xs border-t border-slate-200 pt-1">
                    <span className="text-slate-500">Chủ tài khoản:</span>
                    <span className="font-bold text-slate-800 text-right uppercase">
                      {isVendorLoading && !currentVendor ? 'Đang tải thông tin đối tác...' : (currentVendor?.bankAccountHolder || 'Chưa cập nhật')}
                    </span>
                  </div>
                </div>

                {/* Settlement Financial Breakdown */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2 text-xs">
                  <span className="font-semibold text-slate-800 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[16px] text-primary">analytics</span>
                    Bảng phân tích doanh thu &amp; hoa hồng sàn
                  </span>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">Tổng doanh thu phát sinh (Gross):</span>
                    <span className="font-bold text-slate-800">
                      {(selectedPayout.grossAmount || 0).toLocaleString('vi-VN')} đ
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">Hoa hồng sàn DANASEA trích giữ:</span>
                    <span className="font-bold text-red-600">
                      -{(selectedPayout.commissionAmount || 0).toLocaleString('vi-VN')} đ
                    </span>
                  </div>
                  <div className="flex justify-between items-center pt-2 border-t border-slate-200">
                    <span className="font-bold text-slate-700">Thực lĩnh đối tác (Net Payable):</span>
                    <span className="text-base font-bold text-primary">
                      {(selectedPayout.netPayableAmount || 0).toLocaleString('vi-VN')} đ
                    </span>
                  </div>
                </div>

                {/* Line Items Count */}
                {isDetailLoading || selectedDetail?.id !== selectedPayout.id ? (
                  <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
                    <span className="material-symbols-outlined text-[16px] animate-spin text-primary">progress_activity</span>
                    Đang đồng bộ chi tiết giao dịch...
                  </div>
                ) : (
                  <div className="text-[11px] text-slate-500 flex items-center justify-between px-1">
                    <span>Số lượng đơn con trong kỳ đối soát:</span>
                    <strong className="text-slate-800 font-mono">{selectedDetail?.lineItems?.length || 0} mục</strong>
                  </div>
                )}

                {/* Action Buttons */}
                <div className="flex flex-col gap-2 pt-2 border-t border-slate-200">
                  {selectedPayout.status === 'PENDING' || selectedPayout.status === 'DRAFT' ? (
                    <>
                      <button
                        onClick={() => setIsFinalizeConfirmOpen(true)}
                        disabled={isMutating || requiresVerification}
                        className="w-full py-2.5 rounded-xl bg-primary text-white font-semibold text-xs hover:bg-primary-container transition-all flex items-center justify-center gap-1.5 shadow-xs disabled:opacity-50"
                      >
                        {isMutating ? (
                          <span className="material-symbols-outlined text-[18px] animate-spin">progress_activity</span>
                        ) : (
                          <span className="material-symbols-outlined text-[18px]">verified</span>
                        )}
                        <span>Chốt sổ kỳ đối soát (Finalize Settlement)</span>
                      </button>
                      <button
                        onClick={() => setIsDisburseModalOpen(true)}
                        disabled={isMutating}
                        className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 font-semibold text-xs transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-[16px] text-primary">account_balance</span>
                        <span>Xem thông tin tài khoản giải ngân</span>
                      </button>
                    </>
                  ) : (
                    <>
                      <div className={`p-3 rounded-xl border text-xs font-semibold flex items-center gap-2 ${
                        selectedPayout.status === 'PAID'
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                          : 'bg-blue-50 text-blue-800 border-blue-200'
                      }`}>
                        <span className="material-symbols-outlined text-[18px] text-emerald-600">check_circle</span>
                        <span>Kỳ đối soát đã chốt sổ bất biến ({selectedPayout.status}). Số liệu được bảo toàn.</span>
                      </div>
                      <button
                        onClick={() => setIsDisburseModalOpen(true)}
                        disabled={isMutating}
                        className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 font-semibold text-xs transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-[16px] text-primary">account_balance</span>
                        <span>Xem thông tin tài khoản giải ngân</span>
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* BANKING INFO MODAL (Thông tin tài khoản đối tác) */}
      {isDisburseModalOpen && selectedPayout && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <h3 className="font-headline-md font-bold text-on-surface">Thông Tin Tài Khoản Giải Ngân</h3>
            <p className="text-xs text-on-surface-variant">
              Số tiền thực lĩnh đối tác:{' '}
              <strong className="text-primary font-bold">
                {(selectedPayout.netPayableAmount || 0).toLocaleString('vi-VN')} đ
              </strong>
            </p>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2 text-xs">
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Đối tác:</span>
                <span className="font-bold text-slate-800">{currentVendor?.businessName || `Đối tác #${selectedPayout.vendorId.slice(0, 8)}`}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Ngân hàng:</span>
                <span className="font-bold text-slate-800">
                  {isVendorLoading && !currentVendor ? 'Đang tải thông tin...' : (currentVendor?.bankName || 'Chưa cập nhật')}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Số tài khoản:</span>
                <span className="font-mono font-bold text-primary text-sm">
                  {isVendorLoading && !currentVendor ? 'Đang tải...' : (currentVendor?.bankAccountNumber || 'Chưa cập nhật')}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Chủ tài khoản:</span>
                <span className="font-bold text-slate-800 uppercase">
                  {isVendorLoading && !currentVendor ? 'Đang tải thông tin đối tác...' : (currentVendor?.bankAccountHolder || 'Chưa cập nhật')}
                </span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-[11px] text-slate-600">
              * Lưu ý nghiệp vụ: Hệ thống chưa hỗ trợ cổng giải ngân tự động qua API. Admin thực hiện chuyển khoản ngân hàng thủ công theo thông tin trên và chốt sổ kỳ đối soát.
            </div>

            <div className="flex justify-end gap-2 pt-2">
              {currentVendor?.bankAccountNumber && (
                <button
                  onClick={() => {
                    void navigator.clipboard.writeText(currentVendor.bankAccountNumber || '');
                    showToast('Đã sao chép số tài khoản ngân hàng!');
                  }}
                  className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition-colors flex items-center gap-1"
                >
                  <span className="material-symbols-outlined text-[16px]">content_copy</span>
                  <span>Sao chép STK</span>
                </button>
              )}
              <button
                onClick={() => setIsDisburseModalOpen(false)}
                className="px-5 py-2.5 rounded-xl bg-primary text-white text-xs font-bold shadow-sm hover:bg-primary-container transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* FINALIZE CONFIRMATION MODAL (Portaled to body) */}
      {isFinalizeConfirmOpen && selectedPayout && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">verified</span>
            </div>
            <div>
              <h3 className="font-headline-md font-bold text-on-surface">Xác Nhận Chốt Sổ Kỳ Đối Soát</h3>
              <p className="text-xs text-on-surface-variant mt-1">
                Bạn đang chuẩn bị chốt sổ kỳ đối soát <strong>#{selectedPayout.id.slice(0, 8)}</strong> ({selectedPayout.periodStart} đến {selectedPayout.periodEnd}).
              </p>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-1.5 text-xs">
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Đối tác:</span>
                <span className="font-bold text-slate-800">{currentVendor?.businessName || `Đối tác #${selectedPayout.vendorId.slice(0, 8)}`}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Doanh thu phát sinh (Gross):</span>
                <span className="font-bold text-slate-800">{(selectedPayout.grossAmount || 0).toLocaleString('vi-VN')} đ</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Hoa hồng sàn DANASEA:</span>
                <span className="font-bold text-red-600">-{(selectedPayout.commissionAmount || 0).toLocaleString('vi-VN')} đ</span>
              </div>
              <div className="flex justify-between items-center pt-1 border-t border-slate-200 font-bold">
                <span className="text-slate-700">Thực lĩnh đối tác (Net):</span>
                <span className="text-primary text-sm">{(selectedPayout.netPayableAmount || 0).toLocaleString('vi-VN')} đ</span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-[11px] leading-relaxed">
              * Lưu ý nghiệp vụ quan trọng: Thao tác chốt sổ kỳ đối soát là bất biến (FINALIZED). Dữ liệu tính toán doanh thu và hoa hồng sẽ được khóa vĩnh viễn trong sổ cái kế toán để phục vụ giải ngân.
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsFinalizeConfirmOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container text-xs font-semibold hover:bg-surface-container-high transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleFinalizeSettlement}
                disabled={isMutating}
                className="px-5 py-2.5 rounded-xl bg-primary text-on-primary text-xs font-bold shadow-sm hover:bg-primary-container transition-colors disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                )}
                <span>Xác nhận chốt sổ (FINALIZED)</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </main>
  );
}

export default AdminPayouts;
