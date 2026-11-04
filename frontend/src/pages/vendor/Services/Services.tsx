import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { vendorService } from '../../../services/vendorService';
import { categoryService } from '../../../services/categoryService';
import type { VendorServiceDto, ServiceStatus } from '../../../types/vendorService';
import type { CategoryDto } from '../../../types/category';
import { toast } from '../../../store/useToastStore';
import { extractErrorMessage } from '../../../utils/errorHandler';
import { 
  Plus, 
  Search, 
  RotateCcw, 
  Edit2, 
  Send, 
  PauseCircle, 
  PlayCircle, 
  Calendar, 
  Trash2, 
  X, 
  MapPin, 
  Clock, 
  Users, 
  Star, 
  Package,
  AlertCircle
} from 'lucide-react';
import { 
  AtlasPageHeader, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasButton,
  AtlasPagination
} from '../../../components/vendor/atlas/AtlasDesignSystem';

export function Services() {
  const navigate = useNavigate();
  const [services, setServices] = useState<VendorServiceDto[]>([]);
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<'ALL' | ServiceStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [sortBy, setSortBy] = useState<'newest' | 'price_asc' | 'price_desc' | 'rating'>('newest');
  
  const [actionInProgressId, setActionInProgressId] = useState<string | null>(null);

  // Edit price modal state
  const [editingService, setEditingService] = useState<VendorServiceDto | null>(null);
  const [editPriceInput, setEditPriceInput] = useState('');

  const showToast = (msg: string) => {
    toast.success(msg);
  };

  const loadData = useCallback(() => {
    return Promise.all([
      vendorService.getMyServices(),
      categoryService.getCategories(),
    ]).then(([servicesData, categoriesData]) => {
      setErrorMessage(null);
      setServices(servicesData || []);
      setCategories(categoriesData || []);
    }).catch((err: unknown) => {
      console.error('[Services] Lỗi khi nạp danh sách dịch vụ:', err);
      const msg = err instanceof Error ? err.message : 'Không thể kết nối máy chủ';
      setErrorMessage(msg);
    }).finally(() => {
      setIsLoading(false);
    });
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handle Submit for Review (DRAFT or REJECTED)
  const handleSubmitForReview = async (service: VendorServiceDto) => {
    if (!service.images || service.images.length === 0) {
      toast.warning('Dịch vụ cần ít nhất 1 ảnh để gửi duyệt. Vui lòng mở chỉnh sửa dịch vụ và tải ảnh lên.');
      return;
    }
    if (!window.confirm(`Bạn có chắc muốn gửi duyệt dịch vụ "${service.name}" tới Quản trị viên?`)) {
      return;
    }
    setActionInProgressId(service.id);
    try {
      const updated = await vendorService.submitForReview(service.id);
      setServices(prev => prev.map(s => s.id === updated.id ? updated : s));
      showToast(`Đã gửi duyệt thành công dịch vụ "${service.name}". Trạng thái chuyển thành Chờ duyệt.`);
    } catch (err: unknown) {
      console.error('[Services] Lỗi submit duyệt:', err);
      const msg = extractErrorMessage(err, 'Không thể gửi duyệt dịch vụ');
      toast.error(msg);
    } finally {
      setActionInProgressId(null);
    }
  };

  // Handle Pause (PUBLISHED -> PAUSED)
  const handlePauseService = async (service: VendorServiceDto) => {
    if (!window.confirm(`Bạn có chắc muốn tạm ngưng nhận khách cho dịch vụ "${service.name}"?`)) {
      return;
    }
    setActionInProgressId(service.id);
    try {
      const updated = await vendorService.pauseService(service.id);
      setServices(prev => prev.map(s => s.id === updated.id ? updated : s));
      showToast(`Đã tạm ngưng dịch vụ "${service.name}".`);
    } catch (err: unknown) {
      console.error('[Services] Lỗi tạm ngưng:', err);
      const msg = extractErrorMessage(err, 'Không thể tạm ngưng dịch vụ');
      toast.error(msg);
    } finally {
      setActionInProgressId(null);
    }
  };

  // Handle Resume (PAUSED -> PUBLISHED)
  const handleResumeService = async (service: VendorServiceDto) => {
    if (!window.confirm(`Bạn có chắc muốn mở bán lại dịch vụ "${service.name}"?`)) {
      return;
    }
    setActionInProgressId(service.id);
    try {
      const updated = await vendorService.resumeService(service.id);
      setServices(prev => prev.map(s => s.id === updated.id ? updated : s));
      showToast(`Đã mở bán lại thành công dịch vụ "${service.name}".`);
    } catch (err: unknown) {
      console.error('[Services] Lỗi mở bán lại:', err);
      const msg = extractErrorMessage(err, 'Không thể mở bán lại dịch vụ');
      toast.error(msg);
    } finally {
      setActionInProgressId(null);
    }
  };

  // Handle Delete (Only DRAFT or REJECTED)
  const handleDeleteService = async (id: string, name: string) => {
    if (!window.confirm(`Bạn có chắc muốn xóa vĩnh viễn dịch vụ "${name}"? Thao tác này không thể hoàn tác.`)) {
      return;
    }
    setActionInProgressId(id);
    try {
      await vendorService.deleteService(id);
      setServices(prev => prev.filter(s => s.id !== id));
      showToast(`Đã xóa vĩnh viễn dịch vụ "${name}".`);
    } catch (err: unknown) {
      console.error('[Services] Lỗi xóa dịch vụ:', err);
      const msg = extractErrorMessage(err, 'Không thể xóa dịch vụ');
      toast.error(msg);
    } finally {
      setActionInProgressId(null);
    }
  };

  // Quick Price Edit
  const handleSavePrice = async () => {
    if (!editingService) return;
    const newPrice = parseInt(editPriceInput.replace(/\D/g, ''), 10);
    if (isNaN(newPrice) || newPrice <= 0) {
      toast.warning('Vui lòng nhập đơn giá hợp lệ lớn hơn 0 đ.');
      return;
    }

    if (editingService.status === 'PUBLISHED') {
      const confirmWarning = window.confirm(
        'LƯU Ý: Dịch vụ đang mở bán (PUBLISHED). Khi chỉnh sửa giá, Backend sẽ tự động chuyển trạng thái về "Chờ duyệt" (PENDING_REVIEW) để Admin kiểm duyệt lại.\n\nBạn có chắc muốn tiếp tục?'
      );
      if (!confirmWarning) return;
    }

    setActionInProgressId(editingService.id);
    try {
      const updated = await vendorService.updateService(editingService.id, { price: newPrice });
      setServices(prev => prev.map(s => s.id === updated.id ? updated : s));
      showToast(`Đã cập nhật giá mới: ${newPrice.toLocaleString('vi-VN')} đ cho "${editingService.name}"`);
      setEditingService(null);
    } catch (err: unknown) {
      console.error('[Services] Lỗi sửa giá nhanh:', err);
      const msg = extractErrorMessage(err, 'Cập nhật giá thất bại');
      toast.error(msg);
    } finally {
      setActionInProgressId(null);
    }
  };

  // Category map helper for defensive display
  const categoryMap = new Map<string, string>();
  categories.forEach(cat => {
    categoryMap.set(cat.id, cat.name);
    if (cat.children && cat.children.length > 0) {
      cat.children.forEach(sub => categoryMap.set(sub.id, sub.name));
    }
  });

  // Filter and sort
  const filteredServices = services.filter(service => {
    if (activeTab !== 'ALL' && service.status !== activeTab) return false;
    if (categoryFilter !== 'all' && service.categoryId !== categoryFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchName = (service.name || '').toLowerCase().includes(q);
      const matchEn = (service.nameEn || '').toLowerCase().includes(q);
      const matchLoc = (service.locationName || service.address || '').toLowerCase().includes(q);
      if (!matchName && !matchEn && !matchLoc) return false;
    }
    return true;
  }).sort((a, b) => {
    if (sortBy === 'price_asc') return (a.price || 0) - (b.price || 0);
    if (sortBy === 'price_desc') return (b.price || 0) - (a.price || 0);
    if (sortBy === 'rating') return (b.avgRating || 0) - (a.avgRating || 0);
    const timeA = new Date(a.updatedAt || a.createdAt || 0).getTime();
    const timeB = new Date(b.updatedAt || b.createdAt || 0).getTime();
    return timeB - timeA;
  });

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(8);

  const totalItems = filteredServices.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const validCurrentPage = Math.min(currentPage, totalPages);
  const paginatedServices = filteredServices.slice(
    (validCurrentPage - 1) * pageSize,
    validCurrentPage * pageSize
  );

  // Tab counters
  const publishedCount = services.filter(s => s.status === 'PUBLISHED').length;
  const pendingCount = services.filter(s => s.status === 'PENDING_REVIEW').length;
  const approvedCount = services.filter(s => s.status === 'APPROVED').length;
  const rejectedCount = services.filter(s => s.status === 'REJECTED').length;
  const draftCount = services.filter(s => s.status === 'DRAFT').length;
  const pausedCount = services.filter(s => s.status === 'PAUSED').length;

  const renderStatusBadge = (status: ServiceStatus, reason?: string) => {
    switch (status) {
      case 'PUBLISHED':
        return <AtlasBadge label="Đang mở bán" variant="success" />;
      case 'PENDING_REVIEW':
        return <AtlasBadge label="Chờ duyệt" variant="warning" />;
      case 'APPROVED':
        return <AtlasBadge label="Đã duyệt" variant="info" />;
      case 'REJECTED':
        return (
          <div className="flex flex-col items-center gap-0.5">
            <AtlasBadge label="Bị từ chối" variant="danger" />
            {reason && (
              <span className="text-xs text-rose-600 max-w-[130px] truncate" title={reason}>
                {reason}
              </span>
            )}
          </div>
        );
      case 'PAUSED':
        return <AtlasBadge label="Tạm ngưng" variant="warning" />;
      case 'DRAFT':
      default:
        return <AtlasBadge label="Bản nháp" variant="neutral" />;
    }
  };

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Page Header (Atlas Page Header) */}
      <AtlasPageHeader
        title="Dịch vụ & Ca Tour Biển"
        actions={
          <AtlasButton
            variant="primary"
            size="md"
            onClick={() => navigate('/vendor/services/new')}
            icon={<Plus className="w-4 h-4" />}
          >
            Tạo dịch vụ mới
          </AtlasButton>
        }
      />

      {/* 2. Status Filter Tabs (Atlas Segmented Control) */}
      <AtlasSegmentedTabs<'ALL' | ServiceStatus>
        activeTab={activeTab}
        onChange={(tab) => {
          setActiveTab(tab);
          setCurrentPage(1);
        }}
        tabs={[
          { id: 'ALL', label: 'Tất cả', count: services.length },
          { id: 'PUBLISHED', label: 'Đang mở bán', count: publishedCount },
          { id: 'PENDING_REVIEW', label: 'Chờ duyệt', count: pendingCount },
          { id: 'APPROVED', label: 'Sẵn sàng', count: approvedCount },
          { id: 'REJECTED', label: 'Bị từ chối', count: rejectedCount },
          { id: 'DRAFT', label: 'Bản nháp', count: draftCount },
          { id: 'PAUSED', label: 'Tạm ngưng', count: pausedCount },
        ]}
      />

      {/* 3. Search & Filter Bar */}
      <div className="bg-white p-3.5 rounded-xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3.5">
        <div className="flex-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
          <div className="relative flex-1 min-w-[260px]">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
              placeholder="Tìm theo tên dịch vụ, bến xuất phát, địa chỉ..."
              className="w-full h-11 pl-10 pr-9 py-2.5 rounded-xl bg-slate-50/70 border border-slate-200 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <select
              value={categoryFilter}
              onChange={(e) => { setCategoryFilter(e.target.value); setCurrentPage(1); }}
              className="h-11 px-3.5 py-2.5 rounded-xl bg-slate-50/70 border border-slate-200 text-sm font-medium text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/20"
            >
              <option value="all">Tất cả danh mục</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>

            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
              className="h-11 px-3.5 py-2.5 rounded-xl bg-slate-50/70 border border-slate-200 text-sm font-medium text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/20"
            >
              <option value="newest">Mới cập nhật</option>
              <option value="price_asc">Giá tăng dần</option>
              <option value="price_desc">Giá giảm dần</option>
              <option value="rating">Đánh giá cao nhất</option>
            </select>
          </div>
        </div>

        <button
          onClick={() => {
            setSearchQuery('');
            setCategoryFilter('all');
            setSortBy('newest');
            setActiveTab('ALL');
            setCurrentPage(1);
            setIsLoading(true);
            loadData();
          }}
          className="h-11 w-11 flex items-center justify-center rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 transition-colors cursor-pointer self-end md:self-auto"
          title="Làm mới danh sách"
        >
          <RotateCcw className="w-4 h-4 text-slate-600" />
        </button>
      </div>

      {/* Error Fallback Banner */}
      {errorMessage && (
        <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-between gap-3 text-rose-800 text-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />
            <span>Không thể tải danh sách dịch vụ: {errorMessage}</span>
          </div>
          <AtlasButton
            variant="danger"
            size="sm"
            onClick={() => { setIsLoading(true); loadData(); }}
          >
            Thử lại
          </AtlasButton>
        </div>
      )}

      {/* Main Table Card (Atlas Flat Table) */}
      <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden flex flex-col">
        <div className="overflow-x-auto scrollbar-thin">
          <table className="w-full text-left text-sm border-collapse table-auto">
            <thead>
              <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                <th className="py-3.5 px-4">TÊN DỊCH VỤ &amp; TRẢI NGHIỆM</th>
                <th className="py-3.5 px-4 w-[140px]">DANH MỤC</th>
                <th className="py-3.5 px-4 text-right w-[140px]">ĐƠN GIÁ</th>
                <th className="py-3.5 px-4 w-[150px]">THỜI LƯỢNG &amp; CA</th>
                <th className="py-3.5 px-4 text-center w-[110px]">ĐÁNH GIÁ</th>
                <th className="py-3.5 px-4 text-center w-[130px]">TRẠNG THÁI</th>
                <th className="py-3.5 px-4 text-center w-[160px]">THAO TÁC</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, idx) => (
                  <tr key={`skeleton-${idx}`} className="animate-pulse">
                    <td className="py-4 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-12 h-12 bg-slate-200 rounded-xl shrink-0" />
                        <div className="space-y-1.5 flex-1">
                          <div className="h-4 w-48 bg-slate-200 rounded" />
                          <div className="h-3 w-28 bg-slate-100 rounded" />
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-4"><div className="h-6 w-20 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-4 text-right"><div className="h-4 w-24 bg-slate-200 rounded ml-auto" /></td>
                    <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-4 text-center"><div className="h-4 w-12 bg-slate-200 rounded mx-auto" /></td>
                    <td className="py-4 px-4 text-center"><div className="h-6 w-20 bg-slate-200 rounded-full mx-auto" /></td>
                    <td className="py-4 px-4 text-center"><div className="h-8 w-24 bg-slate-200 rounded-lg mx-auto" /></td>
                  </tr>
                ))
              ) : paginatedServices.length > 0 ? (
                paginatedServices.map((service) => {
                  const primaryImageUrl = (service.images && service.images.length > 0 && service.images[0]?.url)
                    ? service.images[0].url
                    : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=300&q=80';
                  const displayName = service.name || 'Dịch vụ chưa đặt tên';
                  const displayLocation = service.locationName || service.address || 'Đà Nẵng';
                  const categoryName = categoryMap.get(service.categoryId) || 'Thể thao biển';
                  const isActionBusy = actionInProgressId === service.id;

                  return (
                    <tr key={service.id} className="hover:bg-slate-50/70 transition-colors">
                      {/* Name & Thumbnail */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-slate-100 border border-slate-200/80">
                            <img
                              src={primaryImageUrl}
                              alt={displayName}
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=300&q=80';
                              }}
                            />
                          </div>
                          <div className="min-w-0 max-w-sm">
                            <span
                              onClick={() => navigate(`/vendor/services/${service.id}/edit`)}
                              className="font-medium text-slate-900 hover:text-primary hover:underline truncate block cursor-pointer"
                              title={displayName}
                            >
                              {displayName}
                            </span>
                            <span className="text-xs text-slate-500 flex items-center gap-1 mt-0.5 truncate">
                              <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                              <span>{displayLocation}</span>
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Category */}
                      <td className="py-3.5 px-4">
                        <span className="px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 text-xs font-medium border border-slate-200/60 whitespace-nowrap">
                          {categoryName}
                        </span>
                      </td>

                      {/* Price */}
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex flex-col items-end">
                          <div className="flex items-center gap-1">
                            <span className="font-mono font-bold text-sm text-slate-900 tabular-nums">
                              {(service.price || 0).toLocaleString('vi-VN')} đ
                            </span>
                            <button
                              onClick={() => { setEditingService(service); setEditPriceInput(service.price.toString()); }}
                              className="text-slate-400 hover:text-slate-700 transition-colors cursor-pointer p-0.5"
                              title="Sửa giá nhanh"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                          <span className="text-xs text-slate-400">/ người</span>
                        </div>
                      </td>

                      {/* Duration & Capacity */}
                      <td className="py-3.5 px-4 text-xs text-slate-600 space-y-1">
                        <div className="flex items-center gap-1.5 font-medium text-slate-800">
                          <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                          <span>{service.durationMinutes ? `${service.durationMinutes} phút` : 'Chưa đặt'}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-slate-500">
                          <Users className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                          <span>{service.capacityPerSlot ? `${service.capacityPerSlot} người/ca` : 'Tùy chỉnh'}</span>
                        </div>
                      </td>

                      {/* Rating */}
                      <td className="py-3.5 px-4 text-center">
                        {service.avgRating != null && service.avgRating > 0 ? (
                          <div className="inline-flex items-center gap-1 text-xs">
                            <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400 shrink-0" />
                            <span className="font-bold text-slate-900">{Number(service.avgRating).toFixed(1)}</span>
                            <span className="text-slate-400">({service.ratingCount || 0})</span>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400 italic">Chưa có</span>
                        )}
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4 text-center">
                        {renderStatusBadge(service.status, service.rejectionReason || undefined)}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          <button
                            onClick={() => navigate(`/vendor/services/${service.id}/edit`)}
                            className="p-1.5 rounded-lg text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition-colors cursor-pointer"
                            title="Chỉnh sửa dịch vụ"
                            disabled={isActionBusy}
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>

                          {(service.status === 'DRAFT' || service.status === 'REJECTED') && (
                            <button
                              onClick={() => handleSubmitForReview(service)}
                              className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 transition-colors cursor-pointer"
                              title="Gửi duyệt dịch vụ"
                              disabled={isActionBusy}
                            >
                              <Send className="w-4 h-4" />
                            </button>
                          )}

                          {service.status === 'PUBLISHED' && (
                            <button
                              onClick={() => handlePauseService(service)}
                              className="p-1.5 rounded-lg text-amber-600 hover:bg-amber-50 transition-colors cursor-pointer"
                              title="Tạm ngưng nhận khách"
                              disabled={isActionBusy}
                            >
                              <PauseCircle className="w-4 h-4" />
                            </button>
                          )}

                          {service.status === 'PAUSED' && (
                            <button
                              onClick={() => handleResumeService(service)}
                              className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 transition-colors cursor-pointer"
                              title="Mở bán lại"
                              disabled={isActionBusy}
                            >
                              <PlayCircle className="w-4 h-4" />
                            </button>
                          )}

                          <button
                            onClick={() => navigate(`/vendor/schedule?serviceId=${service.id}`)}
                            className="p-1.5 rounded-lg text-sky-600 hover:bg-sky-50 transition-colors cursor-pointer"
                            title="Lịch ca & Sức chứa"
                            disabled={isActionBusy}
                          >
                            <Calendar className="w-4 h-4" />
                          </button>

                          {(service.status === 'DRAFT' || service.status === 'REJECTED') && (
                            <button
                              onClick={() => handleDeleteService(service.id, displayName)}
                              className="p-1.5 rounded-lg text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                              title="Xóa dịch vụ"
                              disabled={isActionBusy}
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={7} className="py-16 text-center">
                    <div className="flex flex-col items-center justify-center space-y-3 text-slate-500 max-w-sm mx-auto">
                      <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400">
                        <Package className="w-6 h-6" />
                      </div>
                      <p className="font-semibold text-slate-800 text-base">Chưa tìm thấy dịch vụ nào</p>
                      <p className="text-xs text-slate-500">
                        {searchQuery || categoryFilter !== 'all' || activeTab !== 'ALL'
                          ? 'Không có kết quả khớp với tiêu chí tìm kiếm. Hãy thử đổi bộ lọc.'
                          : 'Bạn chưa tạo dịch vụ trải nghiệm biển nào. Hãy bấm nút bên dưới để tạo ngay!'}
                      </p>
                      <AtlasButton
                        variant="primary"
                        size="sm"
                        onClick={() => navigate('/vendor/services/new')}
                        icon={<Plus className="w-4 h-4" />}
                      >
                        Tạo dịch vụ mới
                      </AtlasButton>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Atlas Pagination */}
        <AtlasPagination
          currentPage={validCurrentPage}
          totalPages={totalPages}
          totalItems={totalItems}
          pageSize={pageSize}
          onPageChange={setCurrentPage}
        />
      </div>

      {/* Quick Price Edit Modal */}
      {editingService && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/90 w-full max-w-sm p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-slate-900">Chỉnh sửa đơn giá nhanh</h3>
              <button
                onClick={() => setEditingService(null)}
                className="text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div>
              <p className="text-xs text-slate-600 font-medium truncate mb-2">
                Dịch vụ: <strong className="text-slate-900">{editingService.name}</strong>
              </p>
              {editingService.status === 'PUBLISHED' && (
                <p className="text-xs text-amber-800 bg-amber-50 p-2.5 rounded-xl border border-amber-200/80 mb-3">
                  Lưu ý: Sửa giá dịch vụ đang mở bán sẽ chuyển trạng thái về &quot;Chờ duyệt&quot; theo quy định an toàn của sàn.
                </p>
              )}
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1.5">
                Đơn giá mới (VNĐ):
              </label>
              <input
                type="number"
                min={1000}
                step={1000}
                value={editPriceInput}
                onChange={(e) => setEditPriceInput(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-200 rounded-xl text-base font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900"
                placeholder="Nhập giá mới..."
              />
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
              <AtlasButton
                variant="outline"
                size="sm"
                onClick={() => setEditingService(null)}
              >
                Hủy
              </AtlasButton>
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={handleSavePrice}
                disabled={actionInProgressId === editingService.id}
              >
                {actionInProgressId === editingService.id ? 'Đang lưu...' : 'Lưu đơn giá'}
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
