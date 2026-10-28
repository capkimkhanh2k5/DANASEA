import { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import axios from 'axios';
import { adminDisputeService, getAdminDisputeError } from '../../../services/adminDisputeService';
import { useAuthStore } from '../../../store/useAuthStore';
import type { DisputeResponse, DisputeStatus } from '../../../types/adminDispute';

export function Disputes() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Data state
  const [disputes, setDisputes] = useState<DisputeResponse[]>([]);
  const [selectedDisputeId, setSelectedDisputeId] = useState<string>('');
  const [selectedDetail, setSelectedDetail] = useState<DisputeResponse | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [resolutionNote, setResolutionNote] = useState<string>('');

  // Loading & error state
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Financial verification guard after timeout/unknown outcome
  const [requiresVerification, setRequiresVerification] = useState<boolean>(false);

  // Modals State
  const [isRefundModalOpen, setIsRefundModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [refundPercentage, setRefundPercentage] = useState<number>(50);
  const [previewImage, setPreviewImage] = useState<string | null>(null);

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Pagination & Mutex lock for mutations & sequence counter for detail loading
  const [totalDisputesCount, setTotalDisputesCount] = useState<number>(0);
  const mutationLockRef = useRef<string | null>(null);
  const detailReqSeqRef = useRef<number>(0);

  // Initial load of disputes (tối đa 100 bản ghi mới nhất)
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        setIsLoading(true);
        setError(null);
        const data = await adminDisputeService.getDisputes({ size: 100 });
        if (!active) return;
        const items = data.content || [];
        setDisputes(items);
        setTotalDisputesCount(data.totalElements ?? items.length);
        if (items.length > 0) {
          setSelectedDisputeId(prev => (items.some(d => d.id === prev) ? prev : items[0].id));
        }
      } catch (err) {
        if (active) {
          setError(getAdminDisputeError(err, 'Không thể tải danh sách khiếu nại từ máy chủ'));
        }
      } finally {
        if (active) setIsLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Load detail asynchronously when selected dispute changes
  useEffect(() => {
    let active = true;
    if (!selectedDisputeId || !canAccess) return;

    const seq = ++detailReqSeqRef.current;
    const targetId = selectedDisputeId;

    void (async () => {
      try {
        setIsDetailLoading(true);
        setActionError(null);
        const detail = await adminDisputeService.getDisputeDetail(targetId);
        if (!active || detailReqSeqRef.current !== seq) return;
        setSelectedDetail(detail);
        setResolutionNote(detail.adminNote || '');
      } catch (err) {
        if (!active || detailReqSeqRef.current !== seq) return;
        setActionError(getAdminDisputeError(err, 'Không thể tải chi tiết khiếu nại'));
      } finally {
        if (active && detailReqSeqRef.current === seq) {
          setIsDetailLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedDisputeId, canAccess]);

  const handleSelectDispute = (disputeId: string) => {
    if (isMutating || disputeId === selectedDisputeId) return;
    setSelectedDisputeId(disputeId);
    setSelectedDetail(null);
    setResolutionNote('');
    setActionError(null);
  };

  // Manual refresh handler
  const handleRefresh = async () => {
    if (!canAccess || isLoading || isMutating) return;
    setIsLoading(true);
    setError(null);
    setRequiresVerification(false);
    try {
      const data = await adminDisputeService.getDisputes({ size: 100 });
      const items = data.content || [];
      setDisputes(items);
      setTotalDisputesCount(data.totalElements ?? items.length);
      if (items.length > 0) {
        setSelectedDisputeId(prev => (items.some(d => d.id === prev) ? prev : items[0].id));
      }
    } catch (err) {
      setError(getAdminDisputeError(err, 'Không thể tải danh sách khiếu nại'));
    } finally {
      setIsLoading(false);
    }
  };

  const filteredDisputes = useMemo(() => {
    return disputes.filter(d => {
      if (statusFilter === 'all') return true;
      if (statusFilter === 'OPEN') return d.status === 'OPEN' || d.status === 'UNDER_REVIEW';
      if (statusFilter === 'RESOLVED') {
        return (
          d.status === 'RESOLVED_REFUND' ||
          d.status === 'RESOLVED_PARTIAL' ||
          d.status === 'RESOLVED_REJECTED'
        );
      }
      return d.status === statusFilter;
    });
  }, [disputes, statusFilter]);

  const selectedDispute =
    (selectedDetail && selectedDetail.id === selectedDisputeId)
      ? selectedDetail
      : disputes.find(d => d.id === selectedDisputeId) || disputes[0] || null;

  const isDetailReady = selectedDetail !== null && selectedDetail.id === selectedDisputeId && !isDetailLoading;

  const isResolved =
    selectedDispute?.status === 'RESOLVED_REFUND' ||
    selectedDispute?.status === 'RESOLVED_PARTIAL' ||
    selectedDispute?.status === 'RESOLVED_REJECTED';

  // Action: Execute Refund Decision (100% or partial, kiểm tra lock Mutex chặt chẽ)
  const handleExecuteRefund = async () => {
    // Kiểm tra khóa mutex và điều kiện an toàn trước khi phát request
    if (!selectedDispute || isResolved || isMutating || mutationLockRef.current !== null) return;

    if (requiresVerification) {
      setActionError('Giao dịch trước đó chưa rõ kết quả. Vui lòng bấm "Làm mới" để đồng bộ trạng thái máy chủ trước khi tiếp tục.');
      return;
    }

    if (!isDetailReady || !selectedDetail) {
      setActionError('Đang đồng bộ chi tiết khiếu nại. Vui lòng đợi tải xong trước khi phán quyết.');
      return;
    }

    const note = resolutionNote.trim();
    if (!note) {
      setActionError('Vui lòng nhập lý do/căn cứ phán quyết vào ô kết luận thẩm định trước khi thực hiện.');
      return;
    }

    const targetId = selectedDispute.id;
    mutationLockRef.current = targetId;
    setIsMutating(true);
    setActionError(null);

    const resolution: DisputeStatus = refundPercentage === 100 ? 'RESOLVED_REFUND' : 'RESOLVED_PARTIAL';

    try {
      const updated = await adminDisputeService.resolveDispute(targetId, {
        resolution,
        refundPercentage,
        adminNote: note,
      });

      if (mutationLockRef.current === targetId) {
        setDisputes(prev => prev.map(d => (d.id === updated.id ? updated : d)));
        setSelectedDetail(updated);
        setIsRefundModalOpen(false);
        showToast(`Đã ban hành phán quyết chấp thuận hoàn ${refundPercentage}% (Lệnh hoàn tiền PENDING đã tạo)!`);
      }
    } catch (err) {
      if (mutationLockRef.current === targetId) {
        const isUnknown = axios.isAxiosError(err) && (err.code === 'ECONNABORTED' || err.message.toLowerCase().includes('timeout') || (!err.response && !!err.request));
        if (isUnknown) {
          setRequiresVerification(true);
        }
        setActionError(getAdminDisputeError(err, 'Phán quyết hoàn tiền thất bại'));
      }
    } finally {
      if (mutationLockRef.current === targetId) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Action: Dismiss/Reject Dispute Decision (kiểm tra lock Mutex chặt chẽ)
  const handleDismissDispute = async () => {
    // Kiểm tra khóa mutex và điều kiện an toàn trước khi phát request
    if (!selectedDispute || isResolved || isMutating || mutationLockRef.current !== null) return;

    if (requiresVerification) {
      setActionError('Giao dịch trước đó chưa rõ kết quả. Vui lòng bấm "Làm mới" để đồng bộ trạng thái máy chủ trước khi tiếp tục.');
      return;
    }

    if (!isDetailReady || !selectedDetail) {
      setActionError('Đang đồng bộ chi tiết khiếu nại. Vui lòng đợi tải xong trước khi phán quyết.');
      return;
    }

    const note = resolutionNote.trim();
    if (!note) {
      setActionError('Vui lòng nhập lý do bác bỏ khiếu nại vào ô kết luận thẩm định trước khi thực hiện.');
      return;
    }

    const targetId = selectedDispute.id;
    mutationLockRef.current = targetId;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminDisputeService.resolveDispute(targetId, {
        resolution: 'RESOLVED_REJECTED',
        refundPercentage: null,
        adminNote: note,
      });

      if (mutationLockRef.current === targetId) {
        setDisputes(prev => prev.map(d => (d.id === updated.id ? updated : d)));
        setSelectedDetail(updated);
        setIsRejectModalOpen(false);
        showToast(`Đã bác bỏ khiếu nại #${updated.id.slice(0, 8)} thành công.`);
      }
    } catch (err) {
      if (mutationLockRef.current === targetId) {
        const isUnknown = axios.isAxiosError(err) && (err.code === 'ECONNABORTED' || err.message.toLowerCase().includes('timeout') || (!err.response && !!err.request));
        if (isUnknown) {
          setRequiresVerification(true);
        }
        setActionError(getAdminDisputeError(err, 'Bác bỏ khiếu nại thất bại'));
      }
    } finally {
      if (mutationLockRef.current === targetId) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  const handleOpenRejectModal = () => {
    if (!resolutionNote.trim()) {
      setActionError('Vui lòng nhập lý do/căn cứ bác bỏ vào ô kết luận thẩm định trước khi xác nhận.');
      return;
    }
    setIsRejectModalOpen(true);
  };

  const handleOpenRefundModal = () => {
    if (!resolutionNote.trim()) {
      setActionError('Vui lòng nhập lý do/căn cứ phán quyết vào ô kết luận thẩm định trước khi mở bảng chọn tỷ lệ hoàn tiền.');
      return;
    }
    setIsRefundModalOpen(true);
  };

  // Metrics
  const openCount = disputes.filter(d => d.status === 'OPEN' || d.status === 'UNDER_REVIEW').length;
  const resolvedCount = disputes.filter(
    d => d.status === 'RESOLVED_REFUND' || d.status === 'RESOLVED_PARTIAL' || d.status === 'RESOLVED_REJECTED'
  ).length;

  if (!canAccess) {
    return (
      <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
        <div className="max-w-xl mx-auto mt-12 p-space-xl rounded-2xl bg-white border border-slate-200 text-center shadow-xs">
          <span className="material-symbols-outlined text-[48px] text-amber-500 mb-2">lock</span>
          <h2 className="text-lg font-bold text-slate-800">Yêu cầu quyền Quản trị viên</h2>
          <p className="text-xs text-slate-500 mt-1">
            Vui lòng đăng nhập với tài khoản có quyền ADMIN để xử lý khiếu nại và phán quyết tranh chấp.
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
                Quản lý Khiếu nại &amp; Tranh chấp
              </h1>
              <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600 text-xs font-medium">
                Hiển thị: <strong>{disputes.length}</strong> / <strong>{totalDisputesCount || disputes.length}</strong>
              </span>
            </div>
            <p className="text-slate-500 text-xs mt-1">
              Thống kê áp dụng cho danh sách hiển thị (tối đa 100 khiếu nại gần nhất).
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-medium">
            <button
              onClick={handleRefresh}
              disabled={isLoading || isMutating}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors disabled:opacity-50 mr-2"
              title="Làm mới dữ liệu từ máy chủ"
            >
              <span className={`material-symbols-outlined text-[18px] ${isLoading ? 'animate-spin' : ''}`}>
                refresh
              </span>
              <span>Làm mới</span>
            </button>
            <span className="px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 border border-amber-200">
              Chờ xử lý: <strong>{openCount}</strong>
            </span>
            <span className="px-2.5 py-1 rounded-md bg-primary/10 text-primary border border-primary/20">
              Đã giải quyết: <strong>{resolvedCount}</strong>
            </span>
          </div>
        </div>

        {/* 2-Column Split Workspace */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* LEFT COLUMN: Disputes List (5 cols) */}
          <div className="lg:col-span-5 flex flex-col gap-space-md">
            <div className="p-space-md bg-white rounded-xl shadow-xs flex items-center justify-between border border-slate-200">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">filter_list</span>
                <span className="font-bold text-slate-800 text-sm">Danh sách tranh chấp</span>
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
                  onClick={() => setStatusFilter('OPEN')}
                  className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                    statusFilter === 'OPEN'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Chờ xử lý
                </button>
                <button
                  onClick={() => setStatusFilter('RESOLVED')}
                  className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                    statusFilter === 'RESOLVED'
                      ? 'bg-primary text-white font-bold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Đã xong
                </button>
              </div>
            </div>

            <div className="flex flex-col gap-3">
              {filteredDisputes.length === 0 ? (
                <div className="p-8 rounded-xl bg-white border border-slate-200 text-center text-slate-400">
                  <span className="material-symbols-outlined text-[32px] text-slate-300 mb-1">
                    gavel
                  </span>
                  <p className="text-xs">Không có hồ sơ khiếu nại nào phù hợp</p>
                </div>
              ) : (
                filteredDisputes.map(disp => {
                  const isSelected = disp.id === selectedDispute?.id;

                  return (
                    <div
                      key={disp.id}
                      onClick={() => handleSelectDispute(disp.id)}
                      className={`p-space-md rounded-xl shadow-xs transition-all cursor-pointer border ${
                        isMutating ? 'opacity-70 cursor-not-allowed' : ''
                      } ${
                        isSelected
                          ? 'border-primary ring-2 ring-primary/20 bg-primary/5'
                          : 'border-slate-200 bg-white hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2 mb-1.5">
                        <span className="font-mono text-xs font-bold text-primary">#{disp.id.slice(0, 8)}</span>
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            disp.status === 'OPEN'
                              ? 'bg-amber-50 text-amber-700 border border-amber-200'
                              : disp.status === 'UNDER_REVIEW'
                              ? 'bg-blue-50 text-blue-700 border border-blue-200'
                              : disp.status === 'RESOLVED_REJECTED'
                              ? 'bg-red-50 text-red-700 border border-red-200'
                              : 'bg-primary/10 text-primary border border-primary/20'
                          }`}
                        >
                          {disp.status === 'RESOLVED_REFUND'
                            ? 'PHÁN QUYẾT HOÀN 100%'
                            : disp.status === 'RESOLVED_PARTIAL'
                            ? `PHÁN QUYẾT HOÀN ${disp.refundPercentage || 0}%`
                            : disp.status === 'RESOLVED_REJECTED'
                            ? 'BÁC BỎ YÊU CẦU'
                            : disp.status === 'UNDER_REVIEW'
                            ? 'ĐANG XỬ LÝ'
                            : 'MỚI TẠO'}
                        </span>
                      </div>

                      <h4 className="font-bold text-xs text-slate-800 line-clamp-1">
                        Lý do: {disp.reason}
                      </h4>
                      <p className="text-xs text-slate-500 line-clamp-2 mt-1">{disp.description}</p>

                      <div className="flex items-center justify-between pt-2 mt-2 border-t border-slate-200 text-xs">
                        <span className="font-semibold text-slate-700 font-mono text-[11px]">
                          Khách: #{disp.customerId.slice(0, 8)}
                        </span>
                        <span className="font-mono text-[11px] text-slate-400">
                          {disp.createdAt ? new Date(disp.createdAt).toLocaleDateString('vi-VN') : ''}
                        </span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* RIGHT COLUMN: Dispute Adjudication (7 cols) */}
          {selectedDispute && (
            <div className="lg:col-span-7 flex flex-col gap-space-lg">
              <div className="p-space-lg rounded-xl bg-white shadow-xs flex flex-col gap-4 border border-slate-200">
                <div className="flex items-start justify-between pb-3 border-b border-slate-200">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-label-sm text-xs font-medium text-primary">
                        Hồ sơ tranh chấp #{selectedDispute.id.slice(0, 8)}
                      </span>
                      {isDetailLoading && (
                        <span className="text-[11px] text-slate-400 flex items-center gap-1">
                          <span className="material-symbols-outlined text-[12px] animate-spin">progress_activity</span>
                          Đang đồng bộ...
                        </span>
                      )}
                    </div>
                    <h2 className="text-base font-bold text-slate-800 mt-0.5">Lý do: {selectedDispute.reason}</h2>
                    <span className="text-xs text-slate-500 font-mono">
                      Đơn con: #{selectedDispute.subOrderId?.slice(0, 8) || 'N/A'} • Master: #{selectedDispute.orderId?.slice(0, 8) || 'N/A'}
                    </span>
                  </div>
                  <span
                    className={`px-3 py-1 rounded-full font-bold text-xs border ${
                      isResolved
                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        : 'bg-amber-50 text-amber-700 border-amber-200'
                    }`}
                  >
                    {selectedDispute.status}
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
                        Giao dịch trước đó chưa rõ kết quả do mất kết nối hoặc hết thời gian chờ (Timeout). Để bảo đảm an toàn tài chính, thao tác đã bị khóa. Vui lòng bấm <strong>&quot;Làm mới&quot;</strong> để đối soát trạng thái thực tế từ máy chủ.
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

                {/* Parties Details */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="text-xs text-slate-500 block font-bold">Du khách khiếu nại</span>
                    <span className="font-mono font-bold text-xs text-slate-800 block mt-0.5 truncate">
                      ID: {selectedDispute.customerId}
                    </span>
                    <span className="text-[11px] text-slate-400 block mt-0.5">
                      Thời điểm tạo: {selectedDispute.createdAt ? new Date(selectedDispute.createdAt).toLocaleString('vi-VN') : 'Mới tạo'}
                    </span>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
                    <span className="text-xs text-slate-500 block font-bold">Mã đơn hàng liên quan</span>
                    <span className="font-mono font-bold text-xs text-slate-800 block mt-0.5 truncate">
                      SubOrder: #{selectedDispute.subOrderId}
                    </span>
                    <span className="text-[11px] text-primary font-semibold block mt-0.5">
                      MasterOrder: #{selectedDispute.orderId?.slice(0, 8)}
                    </span>
                  </div>
                </div>

                {/* Customer Claim */}
                <div>
                  <span className="text-xs font-semibold text-slate-800 block mb-1">
                    Nội dung phản ánh từ du khách:
                  </span>
                  <div className="p-3 rounded-xl bg-slate-50 text-xs text-slate-800 leading-relaxed border-l-2 border-primary">
                    {selectedDispute.description || 'Không có mô tả chi tiết'}
                  </div>
                </div>

                {/* Customer Evidence Images */}
                {selectedDispute.evidenceUrls && selectedDispute.evidenceUrls.length > 0 && (
                  <div>
                    <span className="text-xs font-semibold text-slate-800 block mb-1.5">
                      Hình ảnh bằng chứng đính kèm ({selectedDispute.evidenceUrls.length}):
                    </span>
                    <div className="flex gap-3 flex-wrap">
                      {selectedDispute.evidenceUrls.map((img, idx) => (
                        <img
                          key={idx}
                          src={img}
                          alt="Bằng chứng hiện trường"
                          onClick={() => setPreviewImage(img)}
                          className="w-32 h-20 rounded-xl object-cover border border-slate-200 cursor-pointer hover:opacity-90"
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* Vendor Response */}
                {selectedDispute.vendorResponse && (
                  <div>
                    <span className="text-xs font-semibold text-slate-800 block mb-1">
                      Giải trình đối soát từ Đối tác:
                    </span>
                    <div className="p-3 rounded-xl bg-slate-50 text-xs text-slate-800 leading-relaxed border-l-2 border-slate-400">
                      {selectedDispute.vendorResponse}
                      {selectedDispute.vendorRespondedAt && (
                        <span className="block text-[11px] text-slate-400 mt-1">
                          Phản hồi lúc: {new Date(selectedDispute.vendorRespondedAt).toLocaleString('vi-VN')}
                        </span>
                      )}
                    </div>
                  </div>
                )}

                {/* Port Authority Field Log / Audit Context */}
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600 flex flex-col gap-1">
                  <span className="font-bold text-primary flex items-center gap-1">
                    <span className="material-symbols-outlined text-[16px]">radar</span>
                    Quy chuẩn thẩm định an toàn &amp; Quyền phán quyết Admin
                  </span>
                  <p className="leading-relaxed">
                    Phán quyết hoàn tiền sẽ tự động tạo bản ghi Hoàn tiền (ADMIN_OVERRIDE) ở trạng thái PENDING gửi tới cổng thanh toán. Quyết định phán quyết là bất biến sau khi thực hiện.
                  </p>
                </div>

                {/* Resolution Note & Decision */}
                <div className="p-space-md bg-slate-50 border border-slate-200 rounded-xl flex flex-col gap-3">
                  <label className="text-xs font-bold text-slate-800">
                    Kết luận thẩm định &amp; Biên bản giải quyết:
                  </label>
                  <textarea
                    value={resolutionNote}
                    onChange={e => setResolutionNote(e.target.value)}
                    disabled={isResolved || isMutating}
                    placeholder="Nhập nội dung phán quyết, căn cứ đối soát hải trình hoặc lý do bồi hoàn..."
                    className="w-full p-2.5 rounded-xl bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-primary border border-slate-200 resize-none disabled:bg-slate-100 disabled:text-slate-500"
                    rows={3}
                  />

                  {isResolved ? (
                    <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-800 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-[20px] text-emerald-600">verified</span>
                        <div>
                          <strong>Phán quyết đã ban hành: {selectedDispute.status}</strong>
                          {selectedDispute.refundPercentage !== null && selectedDispute.refundPercentage !== undefined && (
                            <span className="block text-[11px] text-emerald-700">
                              Tỷ lệ chấp thuận bồi hoàn: {selectedDispute.refundPercentage}% (Lệnh hoàn tiền PENDING)
                            </span>
                          )}
                        </div>
                      </div>
                      {selectedDispute.resolvedAt && (
                        <span className="text-[11px] text-emerald-600 font-mono">
                          {new Date(selectedDispute.resolvedAt).toLocaleDateString('vi-VN')}
                        </span>
                      )}
                    </div>
                  ) : (
                    <div className="flex items-center justify-end gap-2 pt-1">
                      <button
                        onClick={handleOpenRejectModal}
                        disabled={isMutating || !isDetailReady || requiresVerification}
                        title={!isDetailReady ? 'Vui lòng đợi tải xong chi tiết khiếu nại' : undefined}
                        className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 font-semibold text-xs transition-colors disabled:opacity-50"
                      >
                        Bác bỏ khiếu nại (0%)
                      </button>
                      <button
                        onClick={handleOpenRefundModal}
                        disabled={isMutating || !isDetailReady || requiresVerification}
                        title={!isDetailReady ? 'Vui lòng đợi tải xong chi tiết khiếu nại' : undefined}
                        className="px-5 py-2 rounded-xl bg-primary text-white font-semibold text-xs hover:bg-primary-container flex items-center gap-1.5 shadow-xs transition-colors disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-[18px]">currency_exchange</span>
                        <span>Phán quyết hoàn tiền</span>
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* REFUND MODAL (Portaled to body) */}
      {isRefundModalOpen && selectedDispute && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-lg w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <h3 className="font-headline-md font-bold text-on-surface">Phán Quyết Chấp Thuận Hoàn Tiền</h3>
            <p className="text-xs text-on-surface-variant">
              Phán quyết chấp thuận bồi hoàn cho khiếu nại đơn con <strong>#{selectedDispute.subOrderId?.slice(0, 8)}</strong>. Hệ thống sẽ tạo lệnh hoàn tiền tự động (trạng thái PENDING) gửi tới cổng thanh toán. Lưu ý: Lệnh hoàn tiền PENDING cần thời gian xử lý từ cổng thanh toán/ngân hàng, chưa đồng nghĩa tiền đã hoàn tất về tài khoản khách hàng.
            </p>

            <div className="flex flex-col gap-2">
              <label className="text-xs font-bold text-on-surface">Chọn tỷ lệ bồi hoàn:</label>
              <div className="grid grid-cols-4 gap-2">
                {[20, 30, 50, 100].map(pct => (
                  <button
                    key={pct}
                    type="button"
                    onClick={() => setRefundPercentage(pct)}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      refundPercentage === pct
                        ? 'bg-primary text-on-primary shadow-xs'
                        : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
                    }`}
                  >
                    {pct}%
                  </button>
                ))}
              </div>
            </div>

            <div className="p-3 rounded-xl bg-surface-container-low flex items-center justify-between">
              <span className="text-xs text-outline">Tỷ lệ áp dụng phán quyết:</span>
              <span className="font-headline-md font-black text-secondary">{refundPercentage}% GIÁ TRỊ VÉ</span>
            </div>

            <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-[11px] leading-relaxed">
              * Phán quyết này sẽ kích hoạt lệnh hoàn tiền và ghi vết vào sổ kiểm toán. Thao tác không thể hoàn tác sau khi xác nhận.
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsRefundModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container text-xs font-semibold hover:bg-surface-container-high transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleExecuteRefund}
                disabled={isMutating}
                className="px-5 py-2.5 rounded-xl bg-primary text-on-primary text-xs font-bold shadow-sm hover:bg-primary-container transition-colors disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                )}
                <span>Ban hành phán quyết (Tạo lệnh PENDING)</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* REJECT CONFIRMATION MODAL (Portaled to body) */}
      {isRejectModalOpen && selectedDispute && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">block</span>
            </div>
            <div>
              <h3 className="font-headline-md font-bold text-on-surface">Xác Nhận Bác Bỏ Khiếu Nại</h3>
              <p className="text-xs text-on-surface-variant mt-1">
                Bạn đang thực hiện bác bỏ khiếu nại đơn con <strong>#{selectedDispute.subOrderId?.slice(0, 8)}</strong> (Mã khiếu nại #{selectedDispute.id.slice(0, 8)}). Du khách sẽ không nhận được bồi hoàn.
              </p>
            </div>

            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs">
              <span className="font-semibold text-slate-700 block mb-1">Căn cứ bác bỏ đã nhập:</span>
              <p className="text-slate-800 italic bg-white p-2.5 rounded-lg border border-slate-200">
                &quot;{resolutionNote}&quot;
              </p>
            </div>

            <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-[11px] leading-relaxed">
              * Cảnh báo: Quyết định bác bỏ khiếu nại là bất biến sau khi lưu vào hệ thống và sẽ kết thúc quy trình tra soát.
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsRejectModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container text-xs font-semibold hover:bg-surface-container-high transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleDismissDispute}
                disabled={isMutating}
                className="px-5 py-2.5 rounded-xl bg-red-600 text-white text-xs font-bold shadow-sm hover:bg-red-700 transition-colors disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                )}
                <span>Xác nhận bác bỏ (0% hoàn tiền)</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* IMAGE PREVIEW MODAL (Portaled to body) */}
      {previewImage && createPortal(
        <div
          onClick={() => setPreviewImage(null)}
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 p-4 animate-scale-in cursor-pointer"
        >
          <img
            src={previewImage}
            alt="Phóng to bằng chứng"
            className="max-w-2xl max-h-[80vh] rounded-2xl object-contain shadow-2xl"
          />
        </div>,
        document.body
      )}
    </main>
  );
}

export default Disputes;
