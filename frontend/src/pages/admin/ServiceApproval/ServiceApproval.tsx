import React, { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  adminServiceApprovalService,
  getAdminServiceError,
} from '../../../services/adminServiceApprovalService';
import { adminVendorService } from '../../../services/adminVendorService';
import { apiClient } from '../../../services/apiClient';
import { useAuthStore } from '../../../store/useAuthStore';
import type {
  AdminServiceResult,
  AdminServiceStatus,
  SafetyDocumentResponse,
} from '../../../types/adminServiceApproval';

interface SimpleCategory {
  id: string;
  name: string;
  children?: SimpleCategory[];
}

export function ServiceApproval() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Data State
  const [services, setServices] = useState<AdminServiceResult[]>([]);
  const [selectedServiceId, setSelectedServiceId] = useState<string>('');
  const [safetyDocsByService, setSafetyDocsByService] = useState<Record<string, SafetyDocumentResponse[]>>({});
  const [vendorsMap, setVendorsMap] = useState<Record<string, string>>({});
  const [categoriesMap, setCategoriesMap] = useState<Record<string, string>>({});

  // Loading & Error State
  const [isLoading, setIsLoading] = useState(false);
  const [isDocsLoading, setIsDocsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [docsError, setDocsError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isMutating, setIsMutating] = useState(false);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');

  // Modals & Action Target
  const [isApproveModalOpen, setIsApproveModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  // Reject Safety Doc Modal
  const [docRejectTarget, setDocRejectTarget] = useState<SafetyDocumentResponse | null>(null);
  const [docRejectReason, setDocRejectReason] = useState('');

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  // Mutex lock for mutations & sequence counter for async doc loading
  const mutationLockRef = useRef<symbol | null>(null);
  const docsReqSeqRef = useRef<number>(0);

  // Load Categories & Vendors lookup dictionary (graceful background enrichment)
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        const res = await adminVendorService.getVendors({ size: 100 });
        if (active) {
          const map: Record<string, string> = {};
          (res.content || []).forEach(v => {
            map[v.id] = v.businessName;
          });
          setVendorsMap(map);
        }
      } catch {
        // Non-blocking fallback
      }

      try {
        const catRes = await apiClient.get<SimpleCategory[]>('/admin/categories');
        if (active && Array.isArray(catRes.data)) {
          const map: Record<string, string> = {};
          const flatten = (cats: SimpleCategory[]) => {
            cats.forEach(c => {
              map[c.id] = c.name;
              if (c.children && c.children.length > 0) flatten(c.children);
            });
          };
          flatten(catRes.data);
          setCategoriesMap(map);
        }
      } catch {
        // Non-blocking fallback
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Initial Services Load (asynchronous effect to avoid cascading renders)
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        const data = await adminServiceApprovalService.getAdminServices();
        if (!active) return;
        const list = data || [];
        setServices(list);
        if (list.length > 0) {
          setSelectedServiceId(prev => (list.some(s => s.id === prev) ? prev : list[0].id));
        }
      } catch (err) {
        if (active) {
          setError(getAdminServiceError(err, 'Không thể tải danh sách dịch vụ'));
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Load Safety Documents for selected service with sequence guard against stale responses
  useEffect(() => {
    let active = true;
    if (!selectedServiceId || !canAccess) return;

    const seq = ++docsReqSeqRef.current;
    const targetServiceId = selectedServiceId;

    void (async () => {
      try {
        setIsDocsLoading(true);
        setDocsError(null);
        const docs = await adminServiceApprovalService.getSafetyDocuments(targetServiceId);
        if (!active || docsReqSeqRef.current !== seq) return;

        const normalized = (docs || []).map(d => ({
          ...d,
          serviceId: d.serviceId || targetServiceId,
        }));

        setSafetyDocsByService(prev => ({
          ...prev,
          [targetServiceId]: normalized,
        }));
      } catch (err) {
        if (!active || docsReqSeqRef.current !== seq) return;
        setDocsError(getAdminServiceError(err, 'Không thể tải chứng chỉ an toàn biển'));
      } finally {
        if (active && docsReqSeqRef.current === seq) {
          setIsDocsLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedServiceId, canAccess]);

  // Manual Refresh Handler
  const handleRefresh = async () => {
    if (!canAccess || isLoading) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminServiceApprovalService.getAdminServices();
      const list = data || [];
      setServices(list);
      if (list.length > 0) {
        setSelectedServiceId(prev => (list.some(s => s.id === prev) ? prev : list[0].id));
      }
    } catch (err) {
      setError(getAdminServiceError(err, 'Không thể tải danh sách dịch vụ'));
    } finally {
      setIsLoading(false);
    }
  };

  // Filtered Services
  const filteredServices = useMemo(() => {
    return services.filter(s => {
      const matchStatus = statusFilter === 'all' || s.status === statusFilter;
      const matchCategory = categoryFilter === 'all' || s.categoryId === categoryFilter;
      return matchStatus && matchCategory;
    });
  }, [services, statusFilter, categoryFilter]);

  const selectedService = services.find(s => s.id === selectedServiceId) || services[0] || null;

  // Distinct Categories present in services for filter dropdown
  const categoryFilterOptions = useMemo(() => {
    const map = new Map<string, string>();
    services.forEach(s => {
      if (s.categoryId) {
        const name = categoriesMap[s.categoryId] || `Danh mục #${s.categoryId.slice(0, 6)}`;
        map.set(s.categoryId, name);
      }
    });
    return Array.from(map.entries());
  }, [services, categoriesMap]);

  // Finding 2 fix: bind safety documents strictly to selected service ID, eliminating cross-service stale leak
  const currentSafetyDocs = useMemo(() => {
    if (!selectedService) return [];
    const docs = safetyDocsByService[selectedService.id];
    if (!docs) return [];
    return docs.filter(d => !d.serviceId || d.serviceId === selectedService.id);
  }, [selectedService, safetyDocsByService]);

  // Check if high-risk or weather sensitive service has approved safety docs
  const safetyStatusSummary = useMemo(() => {
    const docs = currentSafetyDocs;
    const hasApproved = docs.some(d => d.status === 'APPROVED');
    const pendingCount = docs.filter(d => d.status === 'PENDING').length;
    return {
      total: docs.length,
      hasApproved,
      pendingCount,
    };
  }, [currentSafetyDocs]);

  // Action: Approve Service
  const handleApprove = async () => {
    if (!selectedService) return;

    const lock = Symbol('approve-service');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminServiceApprovalService.approveService(selectedService.id);
      if (mutationLockRef.current === lock) {
        setServices(prev => prev.map(s => (s.id === updated.id ? updated : s)));
        setIsApproveModalOpen(false);
        showToast(`Đã phê duyệt xuất bản tour "${updated.name}" thành công (PUBLISHED)!`);
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminServiceError(err, 'Phê duyệt dịch vụ thất bại'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Action: Reject Service
  const handleReject = async () => {
    if (!selectedService || !rejectReason.trim()) {
      setActionError('Vui lòng nhập lý do từ chối tour');
      return;
    }

    const lock = Symbol('reject-service');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updated = await adminServiceApprovalService.rejectService(
        selectedService.id,
        rejectReason.trim()
      );
      if (mutationLockRef.current === lock) {
        setServices(prev => prev.map(s => (s.id === updated.id ? updated : s)));
        setIsRejectModalOpen(false);
        setRejectReason('');
        showToast(`Đã từ chối duyệt tour "${updated.name}".`);
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminServiceError(err, 'Từ chối dịch vụ thất bại'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Action: Approve Single Safety Document
  const handleApproveSafetyDoc = async (doc: SafetyDocumentResponse) => {
    if (!selectedService) return;

    const lock = Symbol('approve-safety-doc');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updatedDoc = await adminServiceApprovalService.approveSafetyDocument(
        selectedService.id,
        doc.id
      );
      if (mutationLockRef.current === lock) {
        const docWithServiceId: SafetyDocumentResponse = {
          ...updatedDoc,
          serviceId: updatedDoc.serviceId || selectedService.id,
        };
        setSafetyDocsByService(prev => {
          const current = prev[selectedService.id] || [];
          return {
            ...prev,
            [selectedService.id]: current.map(d => (d.id === docWithServiceId.id ? docWithServiceId : d)),
          };
        });
        showToast('Đã phê duyệt chứng chỉ an toàn biển!');
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminServiceError(err, 'Không thể duyệt chứng chỉ an toàn này'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Action: Reject Single Safety Document
  const handleRejectSafetyDoc = async () => {
    if (!selectedService || !docRejectTarget || !docRejectReason.trim()) {
      setActionError('Vui lòng nhập lý do từ chối chứng chỉ');
      return;
    }

    const lock = Symbol('reject-safety-doc');
    mutationLockRef.current = lock;
    setIsMutating(true);
    setActionError(null);

    try {
      const updatedDoc = await adminServiceApprovalService.rejectSafetyDocument(
        selectedService.id,
        docRejectTarget.id,
        docRejectReason.trim()
      );
      if (mutationLockRef.current === lock) {
        const docWithServiceId: SafetyDocumentResponse = {
          ...updatedDoc,
          serviceId: updatedDoc.serviceId || selectedService.id,
        };
        setSafetyDocsByService(prev => {
          const current = prev[selectedService.id] || [];
          return {
            ...prev,
            [selectedService.id]: current.map(d => (d.id === docWithServiceId.id ? docWithServiceId : d)),
          };
        });
        setDocRejectTarget(null);
        setDocRejectReason('');
        showToast('Đã từ chối chứng chỉ an toàn.');
      }
    } catch (err) {
      if (mutationLockRef.current === lock) {
        setActionError(getAdminServiceError(err, 'Không thể từ chối chứng chỉ này'));
      }
    } finally {
      if (mutationLockRef.current === lock) {
        mutationLockRef.current = null;
        setIsMutating(false);
      }
    }
  };

  // Metrics
  const totalCount = services.length;
  const publishedCount = services.filter(s => s.status === 'PUBLISHED').length;
  const pendingCount = services.filter(s => s.status === 'PENDING_REVIEW').length;
  const rejectedCount = services.filter(s => s.status === 'REJECTED').length;
  const pausedCount = services.filter(s => s.status === 'PAUSED').length;

  const renderStatusBadge = (status: AdminServiceStatus) => {
    switch (status) {
      case 'PUBLISHED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-primary/10 text-primary border border-primary/20">
            Đang mở bán
          </span>
        );
      case 'PENDING_REVIEW':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
            Chờ duyệt
          </span>
        );
      case 'REJECTED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-red-50 text-red-700 border border-red-200">
            Từ chối
          </span>
        );
      case 'PAUSED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
            Tạm dừng
          </span>
        );
      case 'DRAFT':
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-500 border border-slate-200">
            Bản nháp
          </span>
        );
    }
  };

  if (!canAccess) {
    return (
      <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
        <div className="max-w-xl mx-auto mt-12 p-space-xl rounded-2xl bg-white border border-slate-200 text-center shadow-xs">
          <span className="material-symbols-outlined text-[48px] text-amber-500 mb-2">lock</span>
          <h2 className="text-lg font-bold text-slate-800">Yêu cầu quyền Quản trị viên</h2>
          <p className="text-xs text-slate-500 mt-1">
            Vui lòng đăng nhập với tài khoản có quyền ADMIN để kiểm duyệt tour và dịch vụ biển.
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

        {/* Global Action Error Alert */}
        {actionError && (
          <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center justify-between shadow-xs">
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
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-slate-200">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
              Kiểm duyệt Dịch vụ
            </h1>
          </div>
          <div className="flex items-center gap-2 text-xs font-medium">
            <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600">
              Tổng: <strong>{totalCount}</strong>
            </span>
            <span className="px-2.5 py-1 rounded-md bg-primary/10 text-primary border border-primary/20">
              Đang mở bán: <strong>{publishedCount}</strong>
            </span>
            <span className="px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 border border-amber-200">
              Chờ duyệt: <strong>{pendingCount}</strong>
            </span>
            {rejectedCount > 0 && (
              <span className="px-2.5 py-1 rounded-md bg-red-50 text-red-700 border border-red-200">
                Từ chối: <strong>{rejectedCount}</strong>
              </span>
            )}
            {pausedCount > 0 && (
              <span className="px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 border border-slate-200">
                Tạm dừng: <strong>{pausedCount}</strong>
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

        {/* Filter Section */}
        <section className="mb-space-lg rounded-xl bg-white p-space-md shadow-xs border border-slate-200">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0">
              <button
                onClick={() => setStatusFilter('all')}
                className={`px-3.5 py-1.5 rounded-lg text-xs transition-all shrink-0 ${
                  statusFilter === 'all'
                    ? 'bg-primary text-white font-bold shadow-xs'
                    : 'bg-white text-slate-700 hover:bg-slate-50 border border-slate-200'
                }`}
              >
                Tất cả ({totalCount})
              </button>
              <button
                onClick={() => setStatusFilter('PENDING_REVIEW')}
                className={`px-3.5 py-1.5 rounded-lg text-xs transition-all shrink-0 ${
                  statusFilter === 'PENDING_REVIEW'
                    ? 'bg-primary text-white font-bold shadow-xs'
                    : 'bg-white text-slate-700 hover:bg-slate-50 border border-slate-200'
                }`}
              >
                Chờ duyệt ({pendingCount})
              </button>
              <button
                onClick={() => setStatusFilter('PUBLISHED')}
                className={`px-3.5 py-1.5 rounded-lg text-xs transition-all shrink-0 ${
                  statusFilter === 'PUBLISHED'
                    ? 'bg-primary text-white font-bold shadow-xs'
                    : 'bg-white text-slate-700 hover:bg-slate-50 border border-slate-200'
                }`}
              >
                Đang mở bán ({publishedCount})
              </button>
              <button
                onClick={() => setStatusFilter('REJECTED')}
                className={`px-3.5 py-1.5 rounded-lg text-xs transition-all shrink-0 ${
                  statusFilter === 'REJECTED'
                    ? 'bg-primary text-white font-bold shadow-xs'
                    : 'bg-white text-slate-700 hover:bg-slate-50 border border-slate-200'
                }`}
              >
                Bị từ chối ({rejectedCount})
              </button>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <select
                value={categoryFilter}
                onChange={e => setCategoryFilter(e.target.value)}
                className="h-9 px-3 rounded-lg bg-slate-50 text-slate-700 text-xs focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer border border-slate-200"
              >
                <option value="all">Tất cả danh mục biển</option>
                {categoryFilterOptions.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </section>

        {/* Global Error Banner */}
        {error && (
          <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center justify-between">
            <span>{error}</span>
            <button onClick={handleRefresh} className="font-bold underline">Thử lại</button>
          </div>
        )}

        {/* Dual Workspace: Review Queue & Verification Panel */}
        <section className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* Left Column (5 Cols): Review Queue Console */}
          <div className="lg:col-span-5 bg-white rounded-xl border border-slate-200 overflow-hidden shadow-xs">
            <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                Hàng Đợi Thẩm Định
              </span>
              <span className="text-xs text-slate-500 font-medium">{filteredServices.length} tour</span>
            </div>

            <div className="divide-y divide-slate-200 max-h-[820px] overflow-y-auto">
              {filteredServices.length === 0 ? (
                <div className="p-12 text-center text-slate-400">
                  <span className="material-symbols-outlined text-[32px] text-slate-300 mb-1">
                    folder_off
                  </span>
                  <p className="text-xs">Không có dịch vụ nào trong hàng đợi này</p>
                </div>
              ) : (
                filteredServices.map(service => {
                  const isSelected = service.id === selectedService?.id;
                  const vendorName =
                    vendorsMap[service.vendorId] || `Đối tác #${service.vendorId.slice(0, 6)}`;
                  const thumb =
                    service.images && service.images.length > 0
                      ? service.images[0].url
                      : '';

                  return (
                    <div
                      key={service.id}
                      onClick={() => setSelectedServiceId(service.id)}
                      className={`p-3.5 transition-colors cursor-pointer flex gap-3 ${
                        isSelected ? 'bg-primary/5 border-l-4 border-l-primary' : 'hover:bg-slate-50'
                      }`}
                    >
                      {thumb ? (
                        <img
                          src={thumb}
                          alt={service.name}
                          className="w-16 h-16 rounded-lg object-cover shrink-0 border border-slate-200"
                        />
                      ) : (
                        <div className="w-16 h-16 rounded-lg bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-400 shrink-0">
                          <span className="material-symbols-outlined text-[24px]">sailing</span>
                        </div>
                      )}
                      <div className="flex flex-col justify-between flex-1 min-w-0">
                        <div>
                          <div className="flex items-center justify-between gap-1 mb-0.5">
                            <span className="text-[11px] font-bold text-primary truncate uppercase" title={vendorName}>
                              {vendorName}
                            </span>
                            {renderStatusBadge(service.status)}
                          </div>
                          <h4 className="text-xs font-bold text-slate-800 leading-snug truncate" title={service.name}>
                            {service.name}
                          </h4>
                          <p className="text-[11px] text-slate-500 truncate">
                            {service.locationName} • {service.durationMinutes} phút
                          </p>
                        </div>

                        <div className="flex items-center justify-between pt-1 text-xs">
                          <span className="font-bold text-primary">
                            {(service.price || 0).toLocaleString('vi-VN')} đ
                          </span>
                          <span className="text-[11px] text-slate-400 font-mono">#{service.id.slice(0, 6)}</span>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Right Column (7 Cols) */}
          {selectedService && (
            <div className="lg:col-span-7 flex flex-col gap-space-lg">
              <div className="rounded-xl bg-white p-space-lg shadow-xs border border-slate-200">
                <div className="flex flex-col md:flex-row md:items-start justify-between gap-space-md pb-space-md border-b border-slate-200">
                  <div>
                    <div className="flex items-center gap-2 mb-1.5">
                      <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary font-label-sm text-xs uppercase font-bold">
                        Mã Tour: #{selectedService.id.slice(0, 8)}
                      </span>
                      <span className="px-2.5 py-0.5 rounded-full bg-surface-container text-on-surface font-label-sm text-xs font-semibold">
                        {categoriesMap[selectedService.categoryId] || 'Danh mục biển'}
                      </span>
                    </div>
                    <h2 className="font-headline-lg text-headline-lg text-on-surface tracking-tight font-black">
                      {selectedService.name}
                    </h2>
                    {selectedService.nameEn && (
                      <p className="font-body-md text-sm text-on-surface-variant italic mt-0.5">
                        {selectedService.nameEn}
                      </p>
                    )}
                  </div>

                  <div className="rounded-2xl bg-surface-container-low p-space-md text-right shrink-0">
                    <span className="font-label-sm text-on-surface-variant block text-xs font-medium">Đơn giá niêm yết</span>
                    <div className="flex items-baseline justify-end gap-1 mt-0.5">
                      <span className="font-headline-xl text-headline-xl text-primary font-black">
                        {(selectedService.price || 0).toLocaleString('vi-VN')}
                      </span>
                      <span className="font-label-lg text-primary font-bold">VNĐ</span>
                    </div>
                    <span className="font-body-sm text-xs text-on-surface-variant block">/ 01 khách</span>
                  </div>
                </div>

                {/* Service Specs */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-space-md rounded-2xl bg-surface-container-low my-space-md">
                  <div>
                    <span className="text-xs text-outline block">Đơn vị vận hành</span>
                    <span className="font-label-md text-xs font-bold text-on-surface truncate block" title={vendorsMap[selectedService.vendorId]}>
                      {vendorsMap[selectedService.vendorId] || `Đối tác #${selectedService.vendorId.slice(0, 8)}`}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Thời lượng tour</span>
                    <span className="font-label-md text-xs font-bold text-on-surface">{selectedService.durationMinutes} phút</span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Định mức phục vụ</span>
                    <span className="font-label-md text-xs font-bold text-on-surface">Tối đa {selectedService.capacityPerSlot} khách/ca</span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Tọa độ GPS</span>
                    <span className="font-mono text-xs font-bold text-primary">
                      {selectedService.latitude}° N, {selectedService.longitude}° E
                    </span>
                  </div>
                </div>

                {/* Safety Evaluation & Weather Constraints */}
                <div className="p-space-md rounded-2xl bg-surface-container mb-space-md">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-primary"></span>
                      <span className="font-headline-sm text-sm text-on-surface font-bold">
                        Tiêu chí An toàn Biển &amp; Thời tiết
                      </span>
                    </div>
                    <span className={`px-2.5 py-0.5 rounded-full font-label-sm text-xs font-semibold ${
                      safetyStatusSummary.hasApproved
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-amber-50 text-amber-700'
                    }`}>
                      {safetyStatusSummary.hasApproved ? 'Đã duyệt chứng chỉ an toàn' : 'Chưa có chứng chỉ duyệt'}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div className="p-2.5 rounded-xl bg-surface-container-lowest">
                      <span className="text-outline block">Sóng tối đa cho phép:</span>
                      <span className="font-bold text-on-surface">
                        ≤ {selectedService.maxWaveM ?? 1.5} mét
                      </span>
                    </div>
                    <div className="p-2.5 rounded-xl bg-surface-container-lowest">
                      <span className="text-outline block">Vận tốc gió tối thiểu:</span>
                      <span className="font-bold text-on-surface">
                        ≥ {selectedService.minWindKmh ?? 0} km/h
                      </span>
                    </div>
                  </div>
                </div>

                {/* Real Safety Documents Section */}
                <div className="p-space-md rounded-2xl bg-white border border-slate-200 mb-space-md flex flex-col gap-2.5">
                  <div className="flex items-center justify-between">
                    <span className="font-label-md text-xs font-bold text-slate-800 flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[16px] text-primary">verified</span>
                      Hồ sơ chứng chỉ an toàn ({currentSafetyDocs.length})
                    </span>
                    {isDocsLoading && (
                      <span className="text-[11px] text-slate-400 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[12px] animate-spin">progress_activity</span>
                        Đang đồng bộ...
                      </span>
                    )}
                  </div>

                  {isDocsLoading && currentSafetyDocs.length === 0 ? (
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
                      <span className="material-symbols-outlined text-[16px] animate-spin text-primary">progress_activity</span>
                      Đang tải danh sách chứng chỉ an toàn...
                    </div>
                  ) : docsError ? (
                    <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-center text-xs text-amber-700">
                      {docsError}
                    </div>
                  ) : currentSafetyDocs.length === 0 ? (
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center text-xs text-slate-500">
                      Chưa có chứng chỉ an toàn biển nào được tải lên cho tour này.
                    </div>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {currentSafetyDocs.map(doc => {
                        const isApproved = doc.status === 'APPROVED';
                        const isRejected = doc.status === 'REJECTED';

                        return (
                          <div
                            key={doc.id}
                            className="p-2.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-2"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="flex items-center gap-2 min-w-0">
                                <span className="material-symbols-outlined text-primary text-[18px]">
                                  assignment
                                </span>
                                <div className="flex flex-col min-w-0">
                                  <span className="text-xs font-semibold text-slate-800 truncate">
                                    Chứng nhận kiểm định #{doc.id.slice(0, 8)}
                                  </span>
                                  {doc.fileUrl && (
                                    <a
                                      href={doc.fileUrl}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-[11px] text-primary underline truncate"
                                    >
                                      Xem tệp chứng chỉ
                                    </a>
                                  )}
                                </div>
                              </div>

                              <span
                                className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase shrink-0 ${
                                  isApproved
                                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                    : isRejected
                                    ? 'bg-red-50 text-red-700 border border-red-200'
                                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                                }`}
                              >
                                {isApproved ? 'Đã duyệt' : isRejected ? 'Từ chối' : 'Chờ duyệt'}
                              </span>
                            </div>

                            {!isApproved && selectedService.status !== 'PUBLISHED' && (
                              <div className="flex items-center justify-end gap-1.5 pt-1 border-t border-slate-200/60">
                                <button
                                  type="button"
                                  disabled={isMutating}
                                  onClick={() => handleApproveSafetyDoc(doc)}
                                  className="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold flex items-center gap-1 shadow-2xs transition-colors disabled:opacity-50"
                                >
                                  <span className="material-symbols-outlined text-[14px]">check</span>
                                  <span>Duyệt chứng chỉ</span>
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

                {/* Waiver Form */}
                {selectedService.waiverContent && (
                  <div className="mb-space-md">
                    <span className="font-label-md text-on-surface font-semibold block mb-1 text-xs">
                      Cam kết Miễn trừ Trách nhiệm (waiver_content)
                    </span>
                    <div className="p-3 rounded-xl bg-surface-container-low text-xs text-on-surface-variant border-l-2 border-primary leading-relaxed">
                      {selectedService.waiverContent}
                    </div>
                  </div>
                )}

                {/* Rejection Reason notice if previously rejected */}
                {selectedService.status === 'REJECTED' && selectedService.rejectionReason && (
                  <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs mb-space-md leading-relaxed">
                    <strong>Lý do từ chối:</strong> {selectedService.rejectionReason}
                  </div>
                )}

                {/* Decision Actions */}
                <div className="p-space-md bg-surface-container-low rounded-2xl flex flex-col gap-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
                    <div>
                      {selectedService.status === 'PUBLISHED' ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-primary/15 text-primary text-xs font-bold">
                          <span className="material-symbols-outlined text-[16px]">verified</span>
                          <span>Đang mở bán công khai (PUBLISHED)</span>
                        </span>
                      ) : selectedService.status === 'REJECTED' ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-secondary/15 text-secondary text-xs font-bold">
                          <span className="material-symbols-outlined text-[16px]">block</span>
                          <span>Đã từ chối thẩm định</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-secondary-container/25 text-on-secondary-container text-xs font-bold animate-pulse">
                          <span className="w-1.5 h-1.5 rounded-full bg-secondary"></span>
                          <span>Hồ sơ đang chờ phê duyệt</span>
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      {selectedService.status === 'PUBLISHED' ? (
                        <button
                          type="button"
                          disabled
                          title="Chỉ Đối tác (Vendor) mới có quyền tạm ngưng dịch vụ qua Cổng đối tác"
                          className="px-4 py-2.5 rounded-xl bg-slate-100 text-slate-400 font-label-md text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-not-allowed border border-slate-200"
                        >
                          <span className="material-symbols-outlined text-[16px]">pause_circle</span>
                          <span>Tạm ngưng mở bán (Quyền Vendor)</span>
                        </button>
                      ) : (
                        <button
                          onClick={() => setIsRejectModalOpen(true)}
                          disabled={isMutating}
                          className="px-4 py-2.5 rounded-xl bg-secondary-container/20 text-secondary hover:bg-secondary-container font-label-md text-xs font-bold transition-colors flex items-center gap-1.5 shadow-xs disabled:opacity-50"
                        >
                          <span className="material-symbols-outlined text-[16px]">close</span>
                          <span>Từ chối duyệt (REJECT)</span>
                        </button>
                      )}

                      <button
                        onClick={() => setIsApproveModalOpen(true)}
                        disabled={isMutating || selectedService.status === 'PUBLISHED'}
                        className={`px-5 py-2.5 rounded-xl font-label-md text-xs font-bold transition-all flex items-center gap-1.5 shadow-sm ${
                          selectedService.status === 'PUBLISHED'
                            ? 'bg-slate-100 text-slate-400 cursor-not-allowed border border-slate-200'
                            : 'bg-primary text-on-primary hover:bg-primary-container'
                        }`}
                      >
                        <span className="material-symbols-outlined text-[16px]">done_all</span>
                        <span>
                          {selectedService.status === 'PUBLISHED'
                            ? 'Đã xuất bản'
                            : 'Phê duyệt xuất bản (PUBLISH)'}
                        </span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </section>
      </div>

      {/* APPROVE SERVICE MODAL */}
      {isApproveModalOpen && selectedService && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-lg w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[28px]">verified</span>
              </div>
              <div>
                <h3 className="font-headline-md text-headline-md font-bold text-on-surface">
                  Xác nhận Phê duyệt &amp; Xuất bản Tour
                </h3>
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-surface-container-low flex flex-col gap-2 border border-outline-variant/20">
              <div className="flex justify-between items-start">
                <span className="text-xs text-outline">Tên dịch vụ:</span>
                <span className="text-xs font-bold text-on-surface text-right max-w-xs">{selectedService.name}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-outline">Đơn vị vận hành:</span>
                <span className="text-xs font-bold text-primary">
                  {vendorsMap[selectedService.vendorId] || `Đối tác #${selectedService.vendorId.slice(0, 8)}`}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-outline">Đơn giá niêm yết:</span>
                <span className="text-xs font-black text-on-surface">
                  {(selectedService.price || 0).toLocaleString('vi-VN')} đ / khách
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-outline">Điều kiện thời tiết:</span>
                <span className="text-xs font-semibold text-primary">
                  Sóng ≤ {selectedService.maxWaveM ?? 1.5}m • Gió ≥ {selectedService.minWindKmh ?? 0}km/h
                </span>
              </div>
            </div>

            {!safetyStatusSummary.hasApproved && (
              <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs leading-relaxed">
                <strong>Cảnh báo hồ sơ an toàn:</strong> Tour này chưa có chứng chỉ an toàn biển nào được phê duyệt. Nếu tour thuộc danh mục mạo hiểm/rủi ro cao, máy chủ sẽ yêu cầu phải duyệt chứng chỉ trước khi xuất bản.
              </div>
            )}

            <div className="text-xs text-on-surface-variant bg-surface-container-low/40 p-3 rounded-xl border border-outline-variant/10 leading-relaxed">
              <strong>Lưu ý:</strong> Khi bạn xác nhận phê duyệt, tour này sẽ được chuyển ngay sang trạng thái <strong className="text-primary font-mono">PUBLISHED</strong> và hiển thị công khai trên Cổng đặt vé cho du khách bắt đầu đặt chỗ.
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={() => setIsApproveModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-xs font-semibold transition-colors disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleApprove}
                disabled={isMutating}
                className="px-5 py-2.5 rounded-xl bg-primary text-on-primary font-label-md text-xs font-bold hover:bg-primary-container transition-colors shadow-sm flex items-center gap-1.5 disabled:opacity-50"
              >
                {isMutating ? (
                  <span className="material-symbols-outlined text-[16px] animate-spin">
                    progress_activity
                  </span>
                ) : (
                  <span className="material-symbols-outlined text-[16px]">check_circle</span>
                )}
                <span>Xác nhận Xuất bản (PUBLISH)</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* REJECT SERVICE MODAL */}
      {isRejectModalOpen && selectedService && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-secondary-container/20 text-secondary flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[28px]">warning</span>
              </div>
              <div>
                <h3 className="font-headline-md text-headline-md font-bold text-on-surface">
                  Từ chối Thẩm định Tour
                </h3>
                <span className="text-xs text-outline">Gửi lý do phản hồi cho đối tác</span>
              </div>
            </div>

            <p className="font-body-md text-xs text-on-surface-variant">
              Nhập lý do chưa đảm bảo an toàn hải đồ để thông báo tới đơn vị:{' '}
              <strong>{vendorsMap[selectedService.vendorId] || `Đối tác #${selectedService.vendorId.slice(0, 8)}`}</strong>
            </p>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              placeholder="VD: Cự ly hải trình vượt quá phao an toàn 350m, hoặc thiếu chứng chỉ cứu sinh..."
              className="w-full h-24 p-3 rounded-xl bg-surface-container-low text-xs text-on-surface focus:outline-none focus:ring-2 focus:ring-secondary border border-outline-variant/30 resize-none"
            />
            <div className="flex justify-end gap-2 pt-1">
              <button
                onClick={() => setIsRejectModalOpen(false)}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-xs font-semibold transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleReject}
                disabled={isMutating || !rejectReason.trim()}
                className="px-5 py-2.5 rounded-xl bg-secondary text-on-secondary text-xs font-bold hover:bg-secondary-container transition-colors shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[14px] animate-spin">
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

      {/* REJECT SAFETY DOCUMENT MODAL */}
      {docRejectTarget && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[28px]">assignment_late</span>
            </div>
            <div>
              <h3 className="font-headline-md text-headline-md font-bold text-on-surface">
                Từ chối Chứng chỉ An toàn
              </h3>
              <p className="font-body-md text-xs text-on-surface-variant mt-1">
                Nhập lý do chưa đạt chuẩn cho chứng chỉ #{docRejectTarget.id.slice(0, 8)}:
              </p>
            </div>
            <textarea
              value={docRejectReason}
              onChange={e => setDocRejectReason(e.target.value)}
              placeholder="VD: Chứng chỉ cứu hộ đã hết hạn, hoặc không thuộc cơ quan cấp phép được công nhận..."
              className="w-full h-24 p-3 rounded-xl bg-surface-container-low text-xs text-on-surface focus:outline-none focus:ring-2 focus:ring-secondary border border-outline-variant/30 resize-none"
            />
            <div className="flex justify-end gap-2 pt-1">
              <button
                onClick={() => {
                  setDocRejectTarget(null);
                  setDocRejectReason('');
                }}
                disabled={isMutating}
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-xs font-semibold transition-colors disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                onClick={handleRejectSafetyDoc}
                disabled={isMutating || !docRejectReason.trim()}
                className="px-5 py-2.5 rounded-xl bg-red-600 text-white text-xs font-bold hover:bg-red-700 transition-colors shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {isMutating && (
                  <span className="material-symbols-outlined text-[14px] animate-spin">
                    progress_activity
                  </span>
                )}
                <span>Từ chối chứng chỉ</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </main>
  );
}

export default ServiceApproval;
