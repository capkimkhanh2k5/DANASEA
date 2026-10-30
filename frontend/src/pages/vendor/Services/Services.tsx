import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { vendorService } from '../../../services/vendorService';
import { categoryService } from '../../../services/categoryService';
import type { VendorServiceDto, ServiceStatus } from '../../../types/vendorService';
import type { CategoryDto } from '../../../types/category';
import { toast } from '../../../store/useToastStore';
import { extractErrorMessage } from '../../../utils/errorHandler';

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
    // newest default: compare updatedAt or createdAt
    const timeA = new Date(a.updatedAt || a.createdAt || 0).getTime();
    const timeB = new Date(b.updatedAt || b.createdAt || 0).getTime();
    return timeB - timeA;
  });

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(6);

  const totalItems = filteredServices.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const validCurrentPage = Math.min(currentPage, totalPages);
  const paginatedServices = filteredServices.slice(
    (validCurrentPage - 1) * pageSize,
    validCurrentPage * pageSize
  );

  // Tab counters (Defensive counts)
  const publishedCount = services.filter(s => s.status === 'PUBLISHED').length;
  const pendingCount = services.filter(s => s.status === 'PENDING_REVIEW').length;
  const approvedCount = services.filter(s => s.status === 'APPROVED').length;
  const rejectedCount = services.filter(s => s.status === 'REJECTED').length;
  const draftCount = services.filter(s => s.status === 'DRAFT').length;
  const pausedCount = services.filter(s => s.status === 'PAUSED').length;

  return (
    <div className="w-full bg-background flex-1">
      <div className="flex flex-col w-full px-6 py-4 space-y-3.5">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-300">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-black tracking-tight">
              Quản lý Dịch vụ
            </h1>
            <p className="text-xs text-slate-600 mt-0.5">
              Quản lý danh sách dịch vụ, nộp duyệt, cấu hình giá, lịch ca và theo dõi trạng thái kiểm duyệt
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button 
              onClick={() => navigate('/vendor/services/new')}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-sm font-medium hover:bg-primary/90 transition-colors shadow-xs" 
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Tạo dịch vụ mới</span>
            </button>
          </div>
        </div>

        {/* Primary Filter Tabs & Unified Filter Bar */}
        <div className="flex flex-col space-y-2.5">
          {/* Status Pill Tabs */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 text-nowrap scrollbar-none">
            <button 
              onClick={() => { setActiveTab('ALL'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'ALL' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span>Tất cả</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${activeTab === 'ALL' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {services.length}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('PUBLISHED'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'PUBLISHED' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span>Đang mở bán</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'PUBLISHED' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {publishedCount}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('PENDING_REVIEW'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'PENDING_REVIEW' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-amber-500"></span>
              <span>Chờ duyệt</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'PENDING_REVIEW' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {pendingCount}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('APPROVED'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'APPROVED' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-sky-500"></span>
              <span>Đã duyệt (Sẵn sàng)</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'APPROVED' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {approvedCount}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('REJECTED'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'REJECTED' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-rose-500"></span>
              <span>Bị từ chối</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'REJECTED' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {rejectedCount}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('DRAFT'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'DRAFT' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-slate-400"></span>
              <span>Bản nháp</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'DRAFT' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {draftCount}
              </span>
            </button>

            <button 
              onClick={() => { setActiveTab('PAUSED'); setCurrentPage(1); }}
              className={`px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 ${
                activeTab === 'PAUSED' 
                  ? 'bg-primary text-on-primary font-bold shadow-xs' 
                  : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
              }`} 
              type="button"
            >
              <span className="w-2 h-2 rounded-full bg-amber-600"></span>
              <span>Tạm ngưng</span>
              <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${activeTab === 'PAUSED' ? 'bg-on-primary/20 text-on-primary' : 'bg-slate-100 text-slate-700'}`}>
                {pausedCount}
              </span>
            </button>
          </div>

          {/* Search & Filter Controls Panel */}
          <div className="p-3 rounded-xl bg-white border border-slate-300 shadow-xs flex flex-col md:flex-row items-center justify-between gap-3">
            <div className="relative w-full md:max-w-md">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 text-[18px]">search</span>
              <input 
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                className="w-full pl-9 pr-3 py-1.5 rounded-lg bg-slate-50 border border-slate-300 text-black placeholder:text-slate-500 text-xs focus:outline-none focus:ring-1 focus:ring-primary" 
                placeholder="Tìm theo tên dịch vụ, bến xuất phát, địa chỉ..." 
                type="text"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-300 px-2.5 py-1 rounded-lg">
                <span className="material-symbols-outlined text-slate-600 text-[16px]">category</span>
                <select 
                  value={categoryFilter}
                  onChange={(e) => { setCategoryFilter(e.target.value); setCurrentPage(1); }}
                  className="bg-transparent text-black text-xs font-medium focus:outline-none cursor-pointer"
                >
                  <option value="all">Tất cả danh mục</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>
              <div className="flex items-center gap-2 bg-slate-50 border border-slate-300 px-2.5 py-1 rounded-lg">
                <span className="material-symbols-outlined text-slate-600 text-[16px]">swap_vert</span>
                <select 
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as 'newest' | 'price_asc' | 'price_desc' | 'rating')}
                  className="bg-transparent text-black text-xs font-medium focus:outline-none cursor-pointer"
                >
                  <option value="newest">Mới cập nhật</option>
                  <option value="price_asc">Giá tăng dần</option>
                  <option value="price_desc">Giá giảm dần</option>
                  <option value="rating">Đánh giá cao nhất</option>
                </select>
              </div>
              <button 
                onClick={() => { setSearchQuery(''); setCategoryFilter('all'); setSortBy('newest'); setActiveTab('ALL'); setCurrentPage(1); setIsLoading(true); loadData(); }}
                className="p-1.5 rounded-lg bg-slate-100 border border-slate-300 text-slate-700 hover:bg-slate-200 transition-colors" 
                title="Làm mới dữ liệu" 
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">refresh</span>
              </button>
            </div>
          </div>
        </div>

        {/* Error Fallback Banner */}
        {errorMessage && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 flex items-start justify-between gap-3 text-rose-800 text-sm">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-rose-600">error</span>
              <span>Không thể tải danh sách dịch vụ: {errorMessage}</span>
            </div>
            <button
              onClick={() => { setIsLoading(true); loadData(); }}
              className="px-3 py-1 bg-rose-600 text-white rounded-lg text-xs font-semibold hover:bg-rose-700 transition-colors shrink-0"
              type="button"
            >
              Thử lại
            </button>
          </div>
        )}

        {/* Loading Skeleton */}
        {isLoading && (
          <div className="w-full bg-white rounded-xl border border-slate-200 p-6 space-y-4 animate-pulse">
            <div className="h-6 bg-slate-200 rounded w-1/4"></div>
            <div className="space-y-3">
              {[1, 2, 3, 4].map(i => (
                <div key={i} className="h-14 bg-slate-100 rounded-lg w-full"></div>
              ))}
            </div>
          </div>
        )}

        {/* Main Service Management Table */}
        {!isLoading && !errorMessage && (
          <div className="w-full bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden flex flex-col">
            <div className="overflow-x-auto scrollbar-thin">
              <table className="w-full text-left border-collapse min-w-[960px]">
                <thead>
                  <tr className="bg-slate-50 text-slate-800 text-xs font-bold border-b border-slate-200">
                    <th className="py-3 px-4 border-r border-slate-200">Tên dịch vụ &amp; Trải nghiệm</th>
                    <th className="py-3 px-3 border-r border-slate-200">Danh mục</th>
                    <th className="py-3 px-3 text-right border-r border-slate-200">Đơn giá niêm yết</th>
                    <th className="py-3 px-3 border-r border-slate-200">Thời lượng &amp; Ca</th>
                    <th className="py-3 px-3 border-r border-slate-200">Đánh giá</th>
                    <th className="py-3 px-3 border-r border-slate-200">Trạng thái</th>
                    <th className="py-3 px-3 text-center sticky right-0 bg-slate-50 z-10 w-[160px]">
                      Thao tác
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 text-sm text-black">
                  {paginatedServices.length > 0 ? (
                    paginatedServices.map(service => {
                      // Defensive fallbacks for images and names
                      const primaryImageUrl = (service.images && service.images.length > 0 && service.images[0]?.url)
                        ? service.images[0].url
                        : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=300&q=80';
                      const displayName = service.name || 'Dịch vụ chưa đặt tên';
                      const displayLocation = service.locationName || service.address || 'Đà Nẵng';
                      const categoryName = categoryMap.get(service.categoryId) || 'Thể thao biển';
                      const isActionBusy = actionInProgressId === service.id;

                      return (
                        <tr key={service.id} className="hover:bg-slate-50/80 transition-colors group">
                          {/* Name & Image */}
                          <td className="py-3 px-4 border-r border-slate-200">
                            <div className="flex items-center gap-3">
                              <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0 bg-slate-100 border border-slate-200">
                                <img 
                                  className="w-full h-full object-cover" 
                                  alt={displayName} 
                                  src={primaryImageUrl}
                                  onError={(e) => {
                                    // Fallback to placeholder if image fails to load
                                    (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=300&q=80';
                                  }}
                                />
                              </div>
                              <div className="flex flex-col min-w-0 max-w-xs">
                                <span 
                                  onClick={() => navigate(`/vendor/services/${service.id}/edit`)}
                                  className="font-bold text-sm text-black hover:text-primary hover:underline truncate cursor-pointer"
                                  title={displayName}
                                >
                                  {displayName}
                                </span>
                                {service.nameEn && (
                                  <span className="text-[11px] text-slate-500 truncate" title={service.nameEn}>
                                    {service.nameEn}
                                  </span>
                                )}
                                <span className="text-xs text-slate-600 flex items-center gap-1 mt-0.5 truncate">
                                  <span className="material-symbols-outlined text-[13px] text-slate-500">location_on</span>
                                  <span>{displayLocation}</span>
                                </span>
                              </div>
                            </div>
                          </td>

                          {/* Category */}
                          <td className="py-3 px-3 border-r border-slate-200">
                            <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-800 text-xs font-medium border border-slate-200 whitespace-nowrap">
                              {categoryName}
                            </span>
                          </td>

                          {/* Price */}
                          <td className="py-3 px-3 text-right border-r border-slate-200">
                            <div className="flex flex-col items-end">
                              <div className="flex items-center gap-1">
                                <span className="font-mono font-bold text-sm text-black">
                                  {(service.price || 0).toLocaleString('vi-VN')} đ
                                </span>
                                <button 
                                  onClick={() => { setEditingService(service); setEditPriceInput(service.price.toString()); }}
                                  className="text-slate-500 hover:text-primary transition-colors cursor-pointer"
                                  title="Sửa giá nhanh"
                                  type="button"
                                >
                                  <span className="material-symbols-outlined text-[15px]">edit</span>
                                </button>
                              </div>
                              <span className="text-[11px] text-slate-500">/ người</span>
                            </div>
                          </td>

                          {/* Duration & Capacity */}
                          <td className="py-3 px-3 border-r border-slate-200">
                            <div className="flex flex-col text-xs space-y-0.5 text-slate-700">
                              <span className="font-medium text-black flex items-center gap-1">
                                <span className="material-symbols-outlined text-[14px] text-slate-500">schedule</span>
                                <span>{service.durationMinutes ? `${service.durationMinutes} phút` : 'Chưa đặt'}</span>
                              </span>
                              <span className="text-[11px] text-slate-600 flex items-center gap-1">
                                <span className="material-symbols-outlined text-[14px] text-slate-500">group</span>
                                <span>{service.capacityPerSlot ? `${service.capacityPerSlot} người/ca` : 'Tùy chỉnh'}</span>
                              </span>
                            </div>
                          </td>

                          {/* Rating */}
                          <td className="py-3 px-3 border-r border-slate-200">
                            {service.avgRating != null && service.avgRating > 0 ? (
                              <div className="flex items-center gap-1 text-xs">
                                <span className="material-symbols-outlined text-amber-500 text-[16px]" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                                <span className="font-bold text-black">{Number(service.avgRating).toFixed(1)}</span>
                                <span className="text-slate-500">({service.ratingCount || 0})</span>
                              </div>
                            ) : (
                              <span className="text-slate-500 text-xs italic">Chưa có</span>
                            )}
                          </td>

                          {/* Status */}
                          <td className="py-3 px-3 border-r border-slate-200">
                            {service.status === 'PUBLISHED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-300">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
                                <span>Đang mở bán</span>
                              </span>
                            )}
                            {service.status === 'PENDING_REVIEW' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-300">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-600"></span>
                                <span>Chờ duyệt</span>
                              </span>
                            )}
                            {service.status === 'APPROVED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-sky-50 text-sky-800 border border-sky-300">
                                <span className="w-1.5 h-1.5 rounded-full bg-sky-600"></span>
                                <span>Đã duyệt</span>
                              </span>
                            )}
                            {service.status === 'REJECTED' && (
                              <div className="flex flex-col gap-0.5">
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-rose-50 text-rose-800 border border-rose-300">
                                  <span className="w-1.5 h-1.5 rounded-full bg-rose-600"></span>
                                  <span>Bị từ chối</span>
                                </span>
                                {service.rejectionReason && (
                                  <span className="text-[10px] text-rose-600 max-w-[130px] truncate" title={service.rejectionReason}>
                                    Lý do: {service.rejectionReason}
                                  </span>
                                )}
                              </div>
                            )}
                            {service.status === 'DRAFT' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800 border border-slate-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-slate-500"></span>
                                <span>Bản nháp</span>
                              </span>
                            )}
                            {service.status === 'PAUSED' && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-800 border border-amber-300">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-600"></span>
                                <span>Tạm ngưng</span>
                              </span>
                            )}
                          </td>

                          {/* Actions */}
                          <td className="py-3 px-3 text-center sticky right-0 bg-white group-hover:bg-slate-50/80 z-10 transition-colors w-[160px]">
                            <div className="flex items-center justify-center gap-1">
                              {/* Edit Button */}
                              <button 
                                onClick={() => navigate(`/vendor/services/${service.id}/edit`)}
                                className="p-1 rounded text-slate-700 hover:text-primary hover:bg-slate-100 transition-colors" 
                                title="Chỉnh sửa dịch vụ" 
                                type="button"
                                disabled={isActionBusy}
                              >
                                <span className="material-symbols-outlined text-[17px]">edit</span>
                              </button>

                              {/* Submit for review: DRAFT or REJECTED */}
                              {(service.status === 'DRAFT' || service.status === 'REJECTED') && (
                                <button 
                                  onClick={() => handleSubmitForReview(service)}
                                  className="p-1 rounded text-emerald-600 hover:bg-emerald-50 transition-colors" 
                                  title="Gửi duyệt dịch vụ tới Admin" 
                                  type="button"
                                  disabled={isActionBusy}
                                >
                                  <span className="material-symbols-outlined text-[17px]">send</span>
                                </button>
                              )}

                              {/* Pause: PUBLISHED */}
                              {service.status === 'PUBLISHED' && (
                                <button 
                                  onClick={() => handlePauseService(service)}
                                  className="p-1 rounded text-amber-600 hover:bg-amber-50 transition-colors" 
                                  title="Tạm ngưng nhận khách" 
                                  type="button"
                                  disabled={isActionBusy}
                                >
                                  <span className="material-symbols-outlined text-[17px]">pause_circle</span>
                                </button>
                              )}

                              {/* Resume: PAUSED */}
                              {service.status === 'PAUSED' && (
                                <button 
                                  onClick={() => handleResumeService(service)}
                                  className="p-1 rounded text-emerald-600 hover:bg-emerald-50 transition-colors" 
                                  title="Mở bán lại" 
                                  type="button"
                                  disabled={isActionBusy}
                                >
                                  <span className="material-symbols-outlined text-[17px]">play_circle</span>
                                </button>
                              )}

                              {/* Schedule / Slots Button */}
                              <button 
                                onClick={() => navigate(`/vendor/schedule?serviceId=${service.id}`)}
                                className="p-1 rounded text-sky-600 hover:bg-sky-50 transition-colors" 
                                title="Lịch ca & Sức chứa"
                                type="button"
                                disabled={isActionBusy}
                              >
                                <span className="material-symbols-outlined text-[17px]">calendar_month</span>
                              </button>

                              {/* Delete Button: DRAFT or REJECTED */}
                              {(service.status === 'DRAFT' || service.status === 'REJECTED') && (
                                <button 
                                  onClick={() => handleDeleteService(service.id, displayName)}
                                  className="p-1 rounded text-rose-600 hover:bg-rose-50 transition-colors" 
                                  title="Xóa dịch vụ" 
                                  type="button"
                                  disabled={isActionBusy}
                                >
                                  <span className="material-symbols-outlined text-[17px]">delete</span>
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    /* Defensive Empty State */
                    <tr>
                      <td colSpan={7} className="py-12 text-center">
                        <div className="flex flex-col items-center justify-center space-y-2 text-slate-500">
                          <span className="material-symbols-outlined text-4xl text-slate-400">inventory_2</span>
                          <p className="font-semibold text-slate-700">Chưa tìm thấy dịch vụ nào</p>
                          <p className="text-xs text-slate-500 max-w-sm">
                            {searchQuery || categoryFilter !== 'all' || activeTab !== 'ALL'
                              ? 'Không có kết quả khớp với tiêu chí tìm kiếm. Hãy thử đổi bộ lọc.'
                              : 'Bạn chưa tạo dịch vụ trải nghiệm biển nào. Hãy nhấn nút bên dưới để tạo ngay!'}
                          </p>
                          <button
                            onClick={() => navigate('/vendor/services/new')}
                            className="mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-medium hover:bg-primary/90 transition-colors"
                            type="button"
                          >
                            <span className="material-symbols-outlined text-[16px]">add</span>
                            <span>Tạo dịch vụ mới</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {totalItems > 0 && (
              <div className="px-4 py-3 bg-slate-50 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-600">
                <span>
                  Hiển thị <span className="font-semibold text-black">{(validCurrentPage - 1) * pageSize + 1}</span> - <span className="font-semibold text-black">{Math.min(validCurrentPage * pageSize, totalItems)}</span> trong <span className="font-semibold text-black">{totalItems}</span> dịch vụ
                </span>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                    disabled={validCurrentPage === 1}
                    className="p-1 rounded border border-slate-300 bg-white disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 transition-colors"
                    title="Trang trước"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[16px]">chevron_left</span>
                  </button>
                  <span className="px-2 font-medium">Trang {validCurrentPage} / {totalPages}</span>
                  <button
                    onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                    disabled={validCurrentPage >= totalPages}
                    className="p-1 rounded border border-slate-300 bg-white disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 transition-colors"
                    title="Trang sau"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[16px]">chevron_right</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Quick Price Edit Modal */}
        {editingService && createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-sm p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-sm text-black">Chỉnh sửa đơn giá nhanh</h3>
                <button
                  onClick={() => setEditingService(null)}
                  className="text-slate-400 hover:text-black"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>

              <div>
                <p className="text-xs text-slate-600 font-medium truncate mb-1">
                  Dịch vụ: <span className="font-bold text-black">{editingService.name}</span>
                </p>
                {editingService.status === 'PUBLISHED' && (
                  <p className="text-[11px] text-amber-700 bg-amber-50 p-2 rounded border border-amber-200 mb-2">
                    Cảnh báo: Sửa giá dịch vụ đang mở bán sẽ chuyển trạng thái về "Chờ duyệt" theo quy tắc an toàn của sàn.
                  </p>
                )}
                <label className="block text-xs font-semibold text-slate-700 mb-1">Đơn giá mới (VNĐ):</label>
                <input
                  type="number"
                  min={1000}
                  step={1000}
                  value={editPriceInput}
                  onChange={(e) => setEditPriceInput(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm font-mono font-bold focus:outline-none focus:ring-1 focus:ring-primary"
                  placeholder="Nhập giá mới..."
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  onClick={() => setEditingService(null)}
                  className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 hover:bg-slate-50"
                  type="button"
                >
                  Hủy
                </button>
                <button
                  onClick={handleSavePrice}
                  disabled={actionInProgressId === editingService.id}
                  className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-bold hover:bg-primary/90 transition-colors disabled:opacity-50"
                  type="button"
                >
                  {actionInProgressId === editingService.id ? 'Đang lưu...' : 'Lưu đơn giá'}
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

        
      </div>
    </div>
  );
}
