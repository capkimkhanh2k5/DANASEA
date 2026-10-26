import React, { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { adminVendorService, getAdminVendorError } from '../../../services/adminVendorService';
import { useAuthStore } from '../../../store/useAuthStore';
import type {
  AdminVendorResponse,
  VendorDocumentResponse,
} from '../../../types/adminVendor';

export function VendorApproval() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Data state
  const [vendors, setVendors] = useState<AdminVendorResponse[]>([]);
  const [selectedVendorId, setSelectedVendorId] = useState<string>('');
  const [selectedVendorDetail, setSelectedVendorDetail] = useState<AdminVendorResponse | null>(null);

  // Loading & error state
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isMutating, setIsMutating] = useState(false);

  // Filter state
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [badgeFilter, setBadgeFilter] = useState<string>('all');

  // Modal state
  const [isApproveModalOpen, setIsApproveModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [docPreviewUrl, setDocPreviewUrl] = useState<{ name: string; url: string } | null>(null);

  // Reject single document modal
  const [docRejectTarget, setDocRejectTarget] = useState<VendorDocumentResponse | null>(null);
  const [docRejectReason, setDocRejectReason] = useState('');

  // Toast notification
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  // Mutex lock for safe actions
  const mutationLockRef = useRef<symbol | null>(null);

  // Initial load without cascading synchronous render
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        const data = await adminVendorService.getVendors({ size: 100 });
        if (!active) return;
        const items = data.content || [];
        setVendors(items);
        if (items.length > 0) {
          setSelectedVendorId(prev => (items.some(v => v.id === prev) ? prev : items[0].id));
        }
      } catch (err) {
        if (active) {
          setError(getAdminVendorError(err, 'Không thể tải danh sách đối tác từ máy chủ'));
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Load single vendor detail asynchronously when selected
  useEffect(() => {
    let active = true;
    if (!selectedVendorId || !canAccess) return;

    void (async () => {
      try {
        setIsDetailLoading(true);
        const detail = await adminVendorService.getVendor(selectedVendorId);
        if (active) {
          setSelectedVendorDetail(detail);
        }
      } catch (err) {
        if (active) {
          setActionError(getAdminVendorError(err, 'Không thể tải chi tiết đối tác'));
        }
      } finally {
        if (active) setIsDetailLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedVendorId, canAccess]);

  // Manual refresh handler
  const handleRefresh = async () => {
    if (!canAccess || isLoading) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminVendorService.getVendors({ size: 100 });
      const items = data.content || [];
      setVendors(items);
      if (items.length > 0) {
        setSelectedVendorId(prev => (items.some(v => v.id === prev) ? prev : items[0].id));
      }
    } catch (err) {
      setError(getAdminVendorError(err, 'Không thể tải danh sách đối tác từ máy chủ'));
    } finally {
      setIsLoading(false);
    }
  };

  // Filtered vendors in memory
  const filteredVendors = useMemo(() => {
    return vendors.filter(v => {
      const matchSearch =
        v.businessName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        v.taxCode.toLowerCase().includes(searchQuery.toLowerCase());

      const matchStatus =
        statusFilter === 'all' || v.verificationStatus === statusFilter;

      const matchBadge =
        badgeFilter === 'all' || v.badgeTier === badgeFilter;

      return matchSearch && matchStatus && matchBadge;
    });
  }, [vendors, searchQuery, statusFilter, badgeFilter]);

  const selectedVendor =
    (selectedVendorDetail && selectedVendorDetail.id === selectedVendorId)
      ? selectedVendorDetail
      : vendors.find(v => v.id === selectedVendorId) || null;

  // Finding 3 fix: match ApproveVendorUseCase requiring both BUSINESS_LICENSE and SAFETY_CERT in APPROVED status
  const documentCheck = useMemo(() => {
    const docs = selectedVendor?.documents || [];
    const hasLicense = docs.some(d => d.docType === 'BUSINESS_LICENSE' && d.status === 'APPROVED');
    const hasSafety = docs.some(d => d.docType === 'SAFETY_CERT' && d.status === 'APPROVED');
    const pendingDocs = docs.filter(d => d.status === 'PENDING');
    return {
      allApproved: hasLicense && hasSafety,
      hasLicense,
      hasSafety,
      pendingCount: pendingDocs.length,
      totalCount: docs.length,
    };
  }, [selectedVendor]);

  // Finding 1 fix: preserve existing documents and refresh via GET to prevent document wipeout
  const handleApprove = async () => {
    if (!selectedVendor) return;

    const lock = Symbol('approve-vendor');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminVendorService.approveVendor(selectedVendor.id);
      if (mutationLockRef.current === lock) {
        setVendors(prev => prev.map(v => (v.id === updated.id ? updated : v)));
        setSelectedVendorDetail(prev => (prev?.id === updated.id ? { ...updated, documents: prev.documents } : prev));
        setIsApproveModalOpen(false);
        showToast(`Đã phê duyệt đối tác ${updated.businessName} thành công!`);

        // Refresh detail via GET to synchronize authoritative state without document loss
        try {
          const refreshed = await adminVendorService.getVendor(updated.id);
          if (mutationLockRef.current === lock) {
            setSelectedVendorDetail(prev => (prev?.id === refreshed.id ? refreshed : prev));
          }
        } catch (refreshErr) {
          if (mutationLockRef.current === lock) {
            setActionError(getAdminVendorError(refreshErr, 'Thao tác thành công nhưng không thể đồng bộ lại chứng từ mới nhất'));
          }
        }
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminVendorError(err, 'Phê duyệt đối tác thất bại'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Finding 1 fix: preserve existing documents and refresh via GET on reject
  const handleReject = async () => {
    if (!selectedVendor || !rejectReason.trim()) {
      setActionError('Vui lòng nhập lý do từ chối hồ sơ đối tác');
      return;
    }

    const lock = Symbol('reject-vendor');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminVendorService.rejectVendor(selectedVendor.id, rejectReason.trim());
      if (mutationLockRef.current === lock) {
        setVendors(prev => prev.map(v => (v.id === updated.id ? updated : v)));
        setSelectedVendorDetail(prev => (prev?.id === updated.id ? { ...updated, documents: prev.documents } : prev));
        setIsRejectModalOpen(false);
        setRejectReason('');
        showToast(`Đã từ chối duyệt hồ sơ ${updated.businessName}.`);

        // Refresh detail via GET to synchronize authoritative state
        try {
          const refreshed = await adminVendorService.getVendor(updated.id);
          if (mutationLockRef.current === lock) {
            setSelectedVendorDetail(prev => (prev?.id === refreshed.id ? refreshed : prev));
          }
        } catch (refreshErr) {
          if (mutationLockRef.current === lock) {
            setActionError(getAdminVendorError(refreshErr, 'Thao tác thành công nhưng không thể đồng bộ lại chứng từ mới nhất'));
          }
        }
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminVendorError(err, 'Từ chối đối tác thất bại'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Approve single document
  const handleApproveDocument = async (doc: VendorDocumentResponse) => {
    if (!selectedVendor) return;

    const lock = Symbol('approve-doc');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updatedDoc = await adminVendorService.approveVendorDocument(selectedVendor.id, doc.id);
      if (mutationLockRef.current === lock) {
        setSelectedVendorDetail(prev => {
          if (!prev) return null;
          const nextDocs = (prev.documents || []).map(d => (d.id === updatedDoc.id ? updatedDoc : d));
          return { ...prev, documents: nextDocs };
        });
        showToast(`Đã duyệt tài liệu ${doc.docType === 'BUSINESS_LICENSE' ? 'Giấy phép kinh doanh' : 'Chứng nhận an toàn'}!`);
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminVendorError(err, 'Không thể duyệt chứng từ này'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Reject single document
  const handleRejectDocument = async () => {
    if (!selectedVendor || !docRejectTarget || !docRejectReason.trim()) {
      setActionError('Vui lòng nhập lý do từ chối tài liệu');
      return;
    }

    const lock = Symbol('reject-doc');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updatedDoc = await adminVendorService.rejectVendorDocument(
        selectedVendor.id,
        docRejectTarget.id,
        docRejectReason.trim()
      );
      if (mutationLockRef.current === lock) {
        setSelectedVendorDetail(prev => {
          if (!prev) return null;
          const nextDocs = (prev.documents || []).map(d => (d.id === updatedDoc.id ? updatedDoc : d));
          return { ...prev, documents: nextDocs };
        });
        setDocRejectTarget(null);
        setDocRejectReason('');
        showToast('Đã từ chối chứng từ đính kèm.');
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminVendorError(err, 'Không thể từ chối chứng từ này'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Metrics
  const totalCount = vendors.length;
  const verifiedCount = vendors.filter(v => v.verificationStatus === 'APPROVED').length;
  const pendingCount = vendors.filter(v => v.verificationStatus === 'PENDING').length;
  const rejectedCount = vendors.filter(v => v.verificationStatus === 'REJECTED').length;

  if (!canAccess) {
    return (
      <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
        <div className="max-w-xl mx-auto mt-12 p-space-xl rounded-2xl bg-white border border-slate-200 text-center shadow-xs">
          <span className="material-symbols-outlined text-[48px] text-amber-500 mb-2">lock</span>
          <h2 className="text-lg font-bold text-slate-800">Yêu cầu quyền Quản trị viên</h2>
          <p className="text-xs text-slate-500 mt-1">
            Vui lòng đăng nhập với tài khoản có quyền ADMIN để quản lý và thẩm định hồ sơ đối tác.
          </p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
      <div className="flex flex-col w-full gap-space-xl animate-fade-in-up">
        {/* Toast notification */}
        {toastMessage && (
          <div className="fixed top-20 right-8 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-primary text-on-primary shadow-xl animate-fade-in-up">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            <span className="font-label-md text-label-md font-semibold">{toastMessage}</span>
          </div>
        )}

        {/* Global action error alert */}
        {actionError && (
          <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center justify-between shadow-xs">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">error</span>
              <span className="font-semibold">{actionError}</span>
            </div>
            <button
              onClick={() => setActionError(null)}
              className="text-red-500 hover:text-red-700"
            >
              <span className="material-symbols-outlined text-[16px]">close</span>
            </button>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
              Phê duyệt Đối tác
            </h1>
          </div>
          <div className="flex items-center gap-2 text-xs font-medium">
            <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600">
              Tổng: <strong>{totalCount}</strong>
            </span>
            <span className="px-2.5 py-1 rounded-md bg-primary/10 text-primary border border-primary/20">
              Hoạt động: <strong>{verifiedCount}</strong>
            </span>
            <span className="px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 border border-amber-200">
              Chờ duyệt: <strong>{pendingCount}</strong>
            </span>
            {rejectedCount > 0 && (
              <span className="px-2.5 py-1 rounded-md bg-red-50 text-red-700 border border-red-200">
                Từ chối: <strong>{rejectedCount}</strong>
              </span>
            )}
            <button
              onClick={handleRefresh}
              disabled={isLoading}
              className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600 hover:text-slate-900 transition-colors flex items-center gap-1 disabled:opacity-50"
              title="Tải lại danh sách"
            >
              <span className={`material-symbols-outlined text-[14px] ${isLoading ? 'animate-spin' : ''}`}>
                refresh
              </span>
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Main workspace: 65% list / 35% drawer */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* Left panel: table */}
          <div className="lg:col-span-8 flex flex-col gap-space-md min-w-0">
            {/* Filter bar */}
            <div className="p-space-md rounded-xl bg-white shadow-xs flex flex-col md:flex-row items-center justify-between gap-space-sm border border-slate-200">
              <div className="relative w-full md:w-80">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-[18px]">
                  search
                </span>
                <input
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="w-full h-10 pl-9 pr-space-md rounded-lg bg-slate-50 text-slate-800 font-body-md text-body-md placeholder:text-slate-400 focus:outline-none focus:bg-white focus:ring-1 focus:ring-primary transition-colors border border-slate-200"
                  placeholder="Tìm theo tên đơn vị, MST..."
                  type="text"
                />
              </div>

              {/* Status & badge tabs */}
              <div className="flex items-center gap-space-xs w-full md:w-auto overflow-x-auto pb-1 md:pb-0">
                <div className="flex p-1 bg-slate-100 rounded-xl border border-slate-200">
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
                    onClick={() => setStatusFilter('PENDING')}
                    className={`px-3 py-1.5 rounded-lg text-xs flex items-center gap-1.5 transition-all ${
                      statusFilter === 'PENDING'
                        ? 'bg-primary text-white font-bold shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    <span>Chờ duyệt</span>
                    {pendingCount > 0 && (
                      <span
                        className={`px-1.5 py-0.2 rounded-full font-bold text-[10px] ${
                          statusFilter === 'PENDING' ? 'bg-white/20 text-white' : 'bg-slate-200 text-slate-700'
                        }`}
                      >
                        {pendingCount}
                      </span>
                    )}
                  </button>
                  <button
                    onClick={() => setStatusFilter('APPROVED')}
                    className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                      statusFilter === 'APPROVED'
                        ? 'bg-primary text-white font-bold shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Đã duyệt
                  </button>
                  <button
                    onClick={() => setStatusFilter('REJECTED')}
                    className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                      statusFilter === 'REJECTED'
                        ? 'bg-primary text-white font-bold shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Từ chối
                  </button>
                </div>

                {/* Badge dropdown */}
                <select
                  value={badgeFilter}
                  onChange={e => setBadgeFilter(e.target.value)}
                  className="h-9 px-3 rounded-lg bg-slate-50 text-slate-700 text-xs focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer border border-slate-200"
                >
                  <option value="all">Huy hiệu: Tất cả</option>
                  <option value="BRONZE">Đồng (BRONZE)</option>
                  <option value="SILVER">Bạc (SILVER)</option>
                  <option value="GOLD">Vàng (GOLD)</option>
                  <option value="PLATINUM">Bạch kim (PLATINUM)</option>
                </select>
              </div>
            </div>

            {/* Error message */}
            {error && (
              <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center justify-between">
                <span>{error}</span>
                <button onClick={handleRefresh} className="font-bold underline">Thử lại</button>
              </div>
            )}

            {/* Partner table card */}
            <div className="rounded-xl bg-white shadow-xs overflow-hidden flex flex-col border border-slate-200">
              <div className="overflow-x-auto custom-scrollbar">
                <table className="w-full text-left border-collapse min-w-[620px]">
                  <thead>
                    <tr className="bg-slate-50 text-slate-700 text-xs font-semibold border-b border-slate-200">
                      <th className="py-3 px-4 whitespace-nowrap border-r border-slate-200">Đơn vị &amp; Đại diện</th>
                      <th className="py-3 px-4 whitespace-nowrap border-r border-slate-200">Mã số thuế</th>
                      <th className="py-3 px-4 whitespace-nowrap border-r border-slate-200">Bến bãi &amp; Đánh giá</th>
                      <th className="py-3 px-4 whitespace-nowrap border-r border-slate-200">Trạng thái</th>
                      <th className="py-3 px-4 whitespace-nowrap border-r border-slate-200">Huy hiệu</th>
                      <th className="py-3 px-4 text-center whitespace-nowrap">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-xs text-slate-700">
                    {filteredVendors.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-12 text-center text-slate-400">
                          <span className="material-symbols-outlined text-[32px] text-slate-300 mb-1">
                            folder_off
                          </span>
                          <p className="text-xs">Không tìm thấy hồ sơ đối tác nào phù hợp</p>
                        </td>
                      </tr>
                    ) : (
                      filteredVendors.map(vendor => {
                        const isSelected = vendor.id === selectedVendor?.id;
                        return (
                          <tr
                            key={vendor.id}
                            onClick={() => setSelectedVendorId(vendor.id)}
                            className={`cursor-pointer transition-colors ${
                              isSelected ? 'bg-primary/5 hover:bg-primary/10' : 'hover:bg-slate-50'
                            }`}
                          >
                            <td className="py-3.5 px-4 border-r border-slate-200">
                              <div className="flex items-center gap-space-sm">
                                <div className="w-10 h-10 rounded-xl bg-slate-100 text-primary border border-slate-200 flex items-center justify-center font-bold text-sm shrink-0">
                                  {vendor.businessName.charAt(0).toUpperCase()}
                                </div>
                                <div className="flex flex-col min-w-0 max-w-[190px]">
                                  <span className="font-bold text-slate-800 truncate" title={vendor.businessName}>
                                    {vendor.businessName}
                                  </span>
                                  <span className="text-[11px] text-slate-500 truncate" title={vendor.bankAccountHolder}>
                                    Đại diện: {vendor.bankAccountHolder || 'Chưa cập nhật'}
                                  </span>
                                </div>
                              </div>
                            </td>

                            <td className="py-3.5 px-4 whitespace-nowrap border-r border-slate-200">
                              <span className="font-mono text-slate-800 font-semibold block">{vendor.taxCode}</span>
                              <div className="text-[11px] text-slate-500 font-normal truncate max-w-[130px]" title={vendor.bankName}>
                                {vendor.bankName}
                              </div>
                            </td>

                            <td className="py-3.5 px-4 border-r border-slate-200">
                              <div className="flex flex-col min-w-0 max-w-[170px]">
                                <span className="text-xs text-slate-800 font-semibold truncate" title={vendor.address}>
                                  {vendor.address}
                                </span>
                                <div className="flex items-center gap-2 mt-0.5">
                                  <span className="flex items-center gap-0.5 text-[11px] text-amber-600 font-bold">
                                    <span className="material-symbols-outlined text-[13px]" style={{ fontVariationSettings: "'FILL' 1" }}>
                                      star
                                    </span>
                                    {vendor.ratingAvg ?? 0}
                                  </span>
                                  <span className="text-[11px] text-slate-500">({vendor.ratingCount ?? 0} đ/g)</span>
                                </div>
                              </div>
                            </td>

                            <td className="py-3.5 px-4 whitespace-nowrap border-r border-slate-200">
                              <span
                                className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold ${
                                  vendor.verificationStatus === 'APPROVED'
                                    ? 'bg-primary/10 text-primary border border-primary/20'
                                    : vendor.verificationStatus === 'PENDING'
                                    ? 'bg-amber-50 text-amber-700 border border-amber-200'
                                    : 'bg-red-50 text-red-700 border border-red-200'
                                }`}
                              >
                                <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                                {vendor.verificationStatus === 'APPROVED'
                                  ? 'Đã duyệt'
                                  : vendor.verificationStatus === 'PENDING'
                                  ? 'Chờ duyệt'
                                  : 'Từ chối'}
                              </span>
                            </td>

                            <td className="py-3.5 px-4 whitespace-nowrap border-r border-slate-200">
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg bg-slate-100 text-slate-700 text-[11px] font-bold border border-slate-200">
                                <span className="material-symbols-outlined text-[13px] text-amber-600">military_tech</span>
                                {vendor.badgeTier || 'BRONZE'}
                              </span>
                            </td>

                            <td className="py-3.5 px-4 text-center whitespace-nowrap">
                              <div className="flex items-center justify-center">
                                <button
                                  onClick={e => {
                                    e.stopPropagation();
                                    setSelectedVendorId(vendor.id);
                                  }}
                                  className={`w-8 h-8 rounded-lg inline-flex items-center justify-center transition-all ${
                                    isSelected
                                      ? 'bg-primary text-white shadow-xs'
                                      : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                                  }`}
                                  title="Xem chi tiết"
                                >
                                  <span className="material-symbols-outlined text-[18px]">visibility</span>
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
          </div>

          {/* Right panel: drawer */}
          {selectedVendor && (
            <div className="lg:col-span-4 flex flex-col gap-space-md sticky top-20">
              <div className="p-space-lg rounded-xl bg-white shadow-xs flex flex-col gap-space-lg border border-slate-200">
                {/* Drawer header */}
                <div className="flex flex-col gap-space-xs">
                  <div className="flex items-center justify-between">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-slate-700 font-mono text-[12px] font-bold">
                      MÃ: #{selectedVendor.id.slice(0, 8)}
                    </span>
                    <span
                      className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full font-label-sm text-xs font-bold ${
                        selectedVendor.verificationStatus === 'APPROVED'
                          ? 'bg-primary/10 text-primary border border-primary/20'
                          : selectedVendor.verificationStatus === 'PENDING'
                          ? 'bg-amber-50 text-amber-700 border border-amber-200'
                          : 'bg-red-50 text-red-700 border border-red-200'
                      }`}
                    >
                      {selectedVendor.verificationStatus === 'APPROVED'
                        ? 'ĐÃ PHÊ DUYỆT'
                        : selectedVendor.verificationStatus === 'PENDING'
                        ? 'CHỜ THẨM ĐỊNH'
                        : 'TỪ CHỐI'}
                    </span>
                  </div>

                  <div className="mt-2">
                    <h2 className="text-lg text-slate-800 font-bold">{selectedVendor.businessName}</h2>
                    <div className="flex items-center gap-1.5 text-slate-500 font-body-sm text-xs mt-0.5">
                      <span className="material-symbols-outlined text-[16px] text-primary">location_on</span>
                      <span>{selectedVendor.address}</span>
                    </div>
                  </div>

                  <div className="p-space-sm rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-600 mt-1">
                    Người duyệt:{' '}
                    <strong className="text-slate-800">
                      {selectedVendor.verifiedBy || (selectedVendor.verificationStatus === 'APPROVED' ? 'Hệ thống Admin' : 'Đang chờ thẩm định')}
                    </strong>
                    {selectedVendor.verifiedAt && (
                      <span className="block text-[11px] text-slate-400 mt-0.5">
                        Ngày: {new Date(selectedVendor.verifiedAt).toLocaleDateString('vi-VN')}
                      </span>
                    )}
                  </div>
                </div>

                {/* Section: legal & financial */}
                <div className="flex flex-col gap-space-sm">
                  <span className="text-xs text-slate-800 font-semibold flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-[16px] text-primary">account_balance</span>
                    Thông tin pháp lý &amp; Tài khoản thụ hưởng
                  </span>
                  <div className="p-space-md rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2">
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-slate-500">Mã số thuế doanh nghiệp:</span>
                      <span className="font-mono font-bold text-slate-800">{selectedVendor.taxCode}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-slate-500">Ngân hàng thanh toán:</span>
                      <span className="font-semibold text-slate-800">{selectedVendor.bankName}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-slate-500">Số tài khoản thụ hưởng:</span>
                      <span className="font-mono font-bold text-primary text-sm">{selectedVendor.bankAccountNumber}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs pt-1 border-t border-slate-200">
                      <span className="text-slate-500">Chủ tài khoản:</span>
                      <span className="font-bold text-slate-800 text-right uppercase">{selectedVendor.bankAccountHolder}</span>
                    </div>
                  </div>
                </div>

                {/* Section: attached documents */}
                <div className="flex flex-col gap-space-sm">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-slate-800 font-semibold flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[16px] text-primary">folder_open</span>
                      Hồ sơ đính kèm ({selectedVendor.documents?.length || 0})
                    </span>
                    {isDetailLoading && (
                      <span className="text-[11px] text-slate-400 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[12px] animate-spin">progress_activity</span>
                        Đang tải...
                      </span>
                    )}
                  </div>

                  {isDetailLoading && !selectedVendor.documents ? (
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
                      <span className="material-symbols-outlined text-[16px] animate-spin text-primary">progress_activity</span>
                      Đang tải danh sách tài liệu...
                    </div>
                  ) : !selectedVendor.documents ? (
                    <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-center text-xs text-amber-700">
                      Chưa thể đồng bộ hồ sơ chứng từ của đối tác. Vui lòng thử lại.
                    </div>
                  ) : selectedVendor.documents.length === 0 ? (
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center text-xs text-slate-500">
                      Chưa có tài liệu đính kèm nào được tải lên
                    </div>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {selectedVendor.documents.map(doc => {
                        const isDocApproved = doc.status === 'APPROVED';
                        const isDocRejected = doc.status === 'REJECTED';
                        const docLabel =
                          doc.docType === 'BUSINESS_LICENSE'
                            ? 'Giấy phép kinh doanh'
                            : doc.docType === 'SAFETY_CERT'
                            ? 'Chứng chỉ an toàn biển'
                            : doc.docType;

                        return (
                          <div
                            key={doc.id}
                            className="p-space-sm rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div
                                onClick={() => setDocPreviewUrl({ name: docLabel, url: doc.fileUrl })}
                                className="flex items-center gap-2.5 min-w-0 cursor-pointer hover:text-primary transition-colors flex-1"
                              >
                                <div className="w-8 h-8 rounded-lg bg-white border border-slate-200 text-primary flex items-center justify-center shrink-0 shadow-xs">
                                  <span className="material-symbols-outlined text-[18px]">
                                    {doc.docType === 'SAFETY_CERT' ? 'verified' : 'picture_as_pdf'}
                                  </span>
                                </div>
                                <div className="flex flex-col min-w-0">
                                  <span className="text-xs text-slate-800 truncate font-semibold">{docLabel}</span>
                                  <span className="text-[10px] text-slate-500 truncate">Mã: #{doc.id.slice(0, 8)}</span>
                                </div>
                              </div>

                              <span
                                className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase shrink-0 ${
                                  isDocApproved
                                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                    : isDocRejected
                                    ? 'bg-red-50 text-red-700 border border-red-200'
                                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                                }`}
                              >
                                {isDocApproved ? 'Đã duyệt' : isDocRejected ? 'Từ chối' : 'Chờ duyệt'}
                              </span>
                            </div>

                            {/* Document action buttons */}
                            {!isDocApproved && selectedVendor.verificationStatus !== 'APPROVED' && (
                              <div className="flex items-center justify-end gap-1.5 pt-1 border-t border-slate-200/60">
                                <button
                                  type="button"
                                  disabled={isMutating}
                                  onClick={() => handleApproveDocument(doc)}
                                  className="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold flex items-center gap-1 shadow-2xs transition-colors disabled:opacity-50"
                                >
                                  <span className="material-symbols-outlined text-[14px]">check</span>
                                  <span>Duyệt tệp</span>
                                </button>
                                <button
                                  type="button"
                                  disabled={isMutating}
                                  onClick={() => {
                                    setDocRejectTarget(doc);
                                    setDocRejectReason('');
                                  }}
                                  className="px-2.5 py-1 rounded-lg bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 text-[11px] font-semibold flex items-center gap-1 transition-colors disabled:opacity-50"
                                >
                                  <span className="material-symbols-outlined text-[14px]">close</span>
                                  <span>Từ chối</span>
                                </button>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Badge display */}
                <div className="flex flex-col gap-space-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-slate-800 font-semibold flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[16px] text-primary">military_tech</span>
                      Huy hiệu sàn hiện tại
                    </span>
                    <span className="px-2 py-0.5 rounded-lg bg-primary/10 text-primary font-bold text-xs">
                      {selectedVendor.badgeTier || 'BRONZE'}
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 italic">
                    * Huy hiệu do hệ thống tự động xếp hạng dựa trên tiêu chí đánh giá và số lượng dịch vụ.
                  </p>
                </div>

                {/* Mandatory document check warning matching ApproveVendorUseCase */}
                {selectedVendor.verificationStatus === 'PENDING' && !documentCheck.allApproved && (
                  <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-start gap-2">
                    <span className="material-symbols-outlined text-[18px] text-amber-600 shrink-0 mt-0.5">
                      info
                    </span>
                    <div className="leading-relaxed">
                      <strong>Lưu ý nghiệp vụ:</strong> Cần duyệt đầy đủ Giấy phép kinh doanh &amp; Chứng nhận an toàn ở trên trước khi bấm phê duyệt đối tác.
                    </div>
                  </div>
                )}

                {/* Actions */}
                <div className="flex flex-col gap-2 pt-2 border-t border-slate-200">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setIsApproveModalOpen(true)}
                      disabled={isMutating || selectedVendor.verificationStatus === 'APPROVED'}
                      className={`flex-1 py-2.5 px-space-md rounded-xl font-semibold transition-colors shadow-xs flex items-center justify-center gap-2 text-xs ${
                        selectedVendor.verificationStatus === 'APPROVED'
                          ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
                          : 'bg-primary text-white hover:bg-primary-container'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[18px]">verified</span>
                      <span>
                        {selectedVendor.verificationStatus === 'APPROVED' ? 'Đã phê duyệt' : 'Phê duyệt đối tác'}
                      </span>
                    </button>
                    {selectedVendor.verificationStatus !== 'REJECTED' && (
                      <button
                        onClick={() => setIsRejectModalOpen(true)}
                        disabled={isMutating}
                        className="py-2.5 px-space-md rounded-xl bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 transition-colors flex items-center justify-center gap-1.5 font-semibold text-xs disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-[18px]">block</span>
                        <span>Từ chối</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Approve vendor modal */}
      {isApproveModalOpen && selectedVendor && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">fact_check</span>
            </div>
            <div>
              <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                Xác nhận phê duyệt đối tác
              </h3>
              <p className="font-body-md text-body-md text-on-surface-variant mt-1">
                Bạn đang phê duyệt hồ sơ đối tác <strong>{selectedVendor.businessName}</strong>. Sau khi duyệt, tài khoản đối tác sẽ được cấp quyền hoạt động trên sàn DANASEA.
              </p>
            </div>

            {/* Finding 3 check in modal */}
            {!documentCheck.allApproved && (
              <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
                Cảnh báo: Chưa duyệt đủ chứng từ bắt buộc (Giấy phép kinh doanh &amp; Chứng nhận an toàn). Nếu tiếp tục, máy chủ sẽ từ chối phê duyệt đối tác.
              </div>
            )}

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={() => setIsApproveModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md font-semibold transition-colors disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleApprove}
                disabled={isMutating}
                className="px-5 py-2.5 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-colors shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">
                    progress_activity
                  </span>
                )}
                <span>Xác nhận phê duyệt</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Reject vendor modal */}
      {isRejectModalOpen && selectedVendor && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-secondary-container/20 text-secondary flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">warning</span>
            </div>
            <div>
              <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                Từ chối thẩm định hồ sơ đối tác
              </h3>
              <p className="font-body-md text-body-md text-on-surface-variant mt-1">
                Nhập lý do chưa đạt chuẩn để lưu lại và phản hồi cho đối tác <strong>{selectedVendor.businessName}</strong>:
              </p>
            </div>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              className="w-full h-24 p-3 rounded-xl bg-surface text-on-surface font-body-sm text-body-sm focus:outline-none focus:ring-2 focus:ring-secondary border border-outline-variant/30 resize-none"
              placeholder="VD: Chứng chỉ cứu hộ mặt nước PADI Rescue đã hết hạn, hoặc mã số thuế chưa khớp với đăng ký kinh doanh..."
            />
            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                onClick={() => setIsRejectModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md font-semibold transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleReject}
                disabled={isMutating || !rejectReason.trim()}
                className="px-5 py-2.5 rounded-xl bg-secondary text-on-secondary font-label-md font-bold hover:bg-secondary-container transition-colors shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">
                    progress_activity
                  </span>
                )}
                <span>Xác nhận từ chối</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Reject single document modal */}
      {docRejectTarget && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">rule_folder</span>
            </div>
            <div>
              <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                Từ chối tệp chứng từ
              </h3>
              <p className="font-body-md text-body-md text-on-surface-variant mt-1 text-xs">
                Nhập lý do không đạt chuẩn cho tệp{' '}
                <strong>{docRejectTarget.docType === 'BUSINESS_LICENSE' ? 'Giấy phép kinh doanh' : 'Chứng nhận an toàn'}</strong>:
              </p>
            </div>
            <textarea
              value={docRejectReason}
              onChange={e => setDocRejectReason(e.target.value)}
              className="w-full h-24 p-3 rounded-xl bg-surface text-on-surface font-body-sm text-body-sm focus:outline-none focus:ring-2 focus:ring-secondary border border-outline-variant/30 resize-none text-xs"
              placeholder="VD: Bản scan mờ, thiếu dấu mộc hoặc đã quá hạn hiệu lực..."
            />
            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                onClick={() => {
                  setDocRejectTarget(null);
                  setDocRejectReason('');
                }}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-semibold transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleRejectDocument}
                disabled={isMutating || !docRejectReason.trim()}
                className="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold hover:bg-red-700 transition-colors shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[14px] animate-spin">
                    progress_activity
                  </span>
                )}
                <span>Từ chối tệp</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Document preview modal */}
      {docPreviewUrl && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl max-w-2xl w-full p-6 shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[24px]">description</span>
                <span className="font-bold text-on-surface text-base">{docPreviewUrl.name}</span>
              </div>
              <button
                onClick={() => setDocPreviewUrl(null)}
                className="w-8 h-8 rounded-full bg-surface-container flex items-center justify-center text-outline hover:text-on-surface transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]">close</span>
              </button>
            </div>
            <div className="h-72 rounded-xl bg-surface-container-low flex flex-col items-center justify-center p-6 text-center border border-dashed border-outline-variant">
              <span className="material-symbols-outlined text-primary text-[48px] mb-2">verified</span>
              <span className="font-bold text-on-surface text-sm">{docPreviewUrl.name}</span>
              <p className="text-xs text-outline max-w-md mt-1 font-mono break-all">
                Đường dẫn tệp: {docPreviewUrl.url || 'Chưa có URL lưu trữ trực tiếp'}
              </p>
              {docPreviewUrl.url && (
                <a
                  href={docPreviewUrl.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 px-3 py-1.5 rounded-lg bg-primary text-white text-xs font-bold inline-flex items-center gap-1 hover:bg-primary-container"
                >
                  <span className="material-symbols-outlined text-[14px]">open_in_new</span>
                  <span>Mở trong tab mới</span>
                </a>
              )}
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setDocPreviewUrl(null)}
                className="px-4 py-2 rounded-xl bg-primary text-on-primary font-bold text-sm hover:bg-primary-container transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </main>
  );
}

export default VendorApproval;
