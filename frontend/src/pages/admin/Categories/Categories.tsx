import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Anchor,
  Compass,
  FolderKanban,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  Ship,
  Sun,
  Trash2,
  Waves,
  Wind,
} from 'lucide-react';
import { MOCK_ADMIN_CATEGORIES } from '../../../data/adminMockData';
import { adminCategoryService, getAdminCategoryError } from '../../../services/adminCategoryService';
import type { Category } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import {
  AdminButton,
  AdminFilterTabs,
  AdminNotice,
  AdminPagination,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  deleteCategory,
  filterCategories,
  saveCategory,
  slugifyCategory,
  toggleCategoryStatus,
  validateCategoryForm,
  type CategoryCertFilter,
  type CategoryFormData,
  type CategoryStatusFilter,
} from './categoriesModel';
import './Categories.css';

const ICON_PRESETS: { key: string; label: string; icon: React.ComponentType<{ size?: number }> }[] = [
  { key: 'surfing', label: 'Lướt sóng / SUP', icon: Waves },
  { key: 'speed', label: 'Cano / Tốc độ', icon: Ship },
  { key: 'scuba_diving', label: 'Lặn biển', icon: Anchor },
  { key: 'kayaking', label: 'Kayak', icon: Wind },
  { key: 'sailing', label: 'Du thuyền', icon: Compass },
  { key: 'waves', label: 'Vui chơi biển', icon: Sun },
];

function getCategoryIcon(key: string) {
  const preset = ICON_PRESETS.find(p => p.key === key);
  return preset ? preset.icon : FolderKanban;
}

export function Categories() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  const [categories, setCategories] = useState<Category[]>(() => demo ? MOCK_ADMIN_CATEGORIES : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<CategoryStatusFilter>('all');
  const [certFilter, setCertFilter] = useState<CategoryCertFilter>('all');

  // Drawer Form State
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [formName, setFormName] = useState('');
  const [formNameEn, setFormNameEn] = useState('');
  const [formSlug, setFormSlug] = useState('');
  const [formParentId, setFormParentId] = useState('');
  const [formIcon, setFormIcon] = useState('surfing');
  const [formIsActive, setFormIsActive] = useState(true);
  const [formRequiresCert, setFormRequiresCert] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [_isSubmitting, setIsSubmitting] = useState(false);

  // Delete Modal State
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);

  // Notice
  const [notice, setNotice] = useState<string | null>(null);

  const refreshCategories = useCallback(async (signal?: AbortSignal) => {
    if (demo) return;
    try {
      setLoading(true);
      const dtos = await adminCategoryService.getCategories(signal);
      const mapped: Category[] = dtos.map(c => ({
        id: c.id,
        name: c.name,
        nameEn: c.nameEn || '',
        slug: c.slug,
        parentId: c.parentId || '',
        iconUrl: c.iconUrl || 'surfing',
        isActive: c.isActive,
        requiresSafetyCert: c.requiresSafetyCert ?? null,
      }));
      setCategories(mapped);
    } catch (err) {
      if (signal?.aborted) return;
      const info = getAdminCategoryError(err);
      setErrorMessage(info.message);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [demo]);

  useEffect(() => {
    const controller = new AbortController();
    refreshCategories(controller.signal);
    return () => {
      controller.abort();
    };
  }, [refreshCategories]);

  const visibleCategories = categories;

  const filtered = useMemo(
    () => filterCategories(visibleCategories, searchQuery, statusFilter, certFilter),
    [visibleCategories, searchQuery, statusFilter, certFilter]
  );

  const activeCount = visibleCategories.filter(c => c.isActive).length;
  const inactiveCount = visibleCategories.filter(c => !c.isActive).length;
  const certRequiredCount = visibleCategories.filter(c => c.requiresSafetyCert === true).length;
  const certUnknownCount = visibleCategories.filter(c => c.requiresSafetyCert === null || c.requiresSafetyCert === undefined).length;

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const resetFilters = () => {
    setSearchQuery('');
    setStatusFilter('all');
    setCertFilter('all');
    setCurrentPage(1);
  };

  const paginatedCategories = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filtered.slice(start, start + PAGE_SIZE);
  }, [filtered, activePage]);

  const openCreateDrawer = () => {
    setEditingCategory(null);
    setFormName('');
    setFormNameEn('');
    setFormSlug('');
    setFormParentId('');
    setFormIcon('surfing');
    setFormIsActive(true);
    setFormRequiresCert(false);
    setFormError(null);
    setIsDrawerOpen(true);
  };

  const openEditDrawer = (category: Category) => {
    setEditingCategory(category);
    setFormName(category.name);
    setFormNameEn(category.nameEn);
    setFormSlug(category.slug);
    setFormParentId(category.parentId || '');
    setFormIcon(category.iconUrl || 'surfing');
    setFormIsActive(category.isActive);
    setFormRequiresCert(category.requiresSafetyCert === true);
    setFormError(null);
    setIsDrawerOpen(true);
  };

  const handleNameChange = (name: string) => {
    setFormName(name);
    if (!editingCategory) {
      setFormSlug(slugifyCategory(name));
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();

    const data: CategoryFormData = {
      name: formName,
      nameEn: formNameEn,
      slug: formSlug,
      parentId: formParentId,
      iconUrl: formIcon,
      isActive: formIsActive,
      requiresSafetyCert: formRequiresCert,
    };

    const validation = validateCategoryForm(data);
    if (!validation.valid) {
      setFormError(validation.error || 'Dữ liệu không hợp lệ');
      return;
    }

    if (demo) {
      const { updatedList, savedItem } = saveCategory(categories, data, editingCategory?.id || null, demo);
      setCategories(updatedList);
      setIsDrawerOpen(false);

      if (savedItem) {
        setNotice(
          editingCategory
            ? `Đã cập nhật danh mục "${savedItem.name}" thành công trong bản demo.`
            : `Đã tạo mới danh mục "${savedItem.name}" thành công trong bản demo.`
        );
      }
      return;
    }

    try {
      setIsSubmitting(true);
      if (editingCategory) {
        const updated = await adminCategoryService.updateCategory(editingCategory.id, {
          name: formName.trim(),
          nameEn: formNameEn.trim() || undefined,
          slug: formSlug.trim(),
          iconUrl: formIcon,
          isActive: formIsActive,
          requiresSafetyCert: formRequiresCert,
        });
        setCategories(prev => prev.map(c => c.id === updated.id ? {
          ...c,
          name: updated.name,
          nameEn: updated.nameEn || '',
          slug: updated.slug,
          iconUrl: updated.iconUrl || 'surfing',
          isActive: updated.isActive,
          requiresSafetyCert: updated.requiresSafetyCert ?? c.requiresSafetyCert ?? null,
        } : c));
        setNotice(`Đã cập nhật danh mục "${updated.name}".`);
      } else {
        const created = await adminCategoryService.createCategory({
          name: formName.trim(),
          nameEn: formNameEn.trim() || undefined,
          slug: formSlug.trim(),
          parentId: formParentId || undefined,
          iconUrl: formIcon,
          requiresSafetyCert: formRequiresCert,
        });
        const newCat: Category = {
          id: created.id,
          name: created.name,
          nameEn: created.nameEn || '',
          slug: created.slug,
          parentId: created.parentId || '',
          iconUrl: created.iconUrl || 'surfing',
          isActive: created.isActive,
          requiresSafetyCert: created.requiresSafetyCert ?? null,
        };
        setCategories(prev => [newCat, ...prev]);
        setNotice(`Đã tạo mới danh mục "${created.name}".`);
      }
      setIsDrawerOpen(false);
    } catch (err) {
      const info = getAdminCategoryError(err, true);
      setFormError(info.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggle = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const target = categories.find(c => c.id === id);
    if (!target) return;

    if (demo) {
      setCategories(prev => toggleCategoryStatus(prev, id, demo));
      setNotice(`Đã chuyển trạng thái danh mục "${target.name}" sang ${!target.isActive ? 'Hiển thị' : 'Tạm ẩn'}.`);
      return;
    }

    try {
      if (target.isActive) {
        await adminCategoryService.deactivateCategory(id);
      } else {
        await adminCategoryService.updateCategory(id, { isActive: true });
      }
      const nextActive = !target.isActive;
      setCategories(prev => prev.map(c => c.id === id ? { ...c, isActive: nextActive } : c));
      setNotice(`Đã chuyển trạng thái danh mục "${target.name}" sang ${nextActive ? 'Hiển thị' : 'Tạm ẩn'}.`);
    } catch (err) {
      const info = getAdminCategoryError(err, true);
      setNotice(`Lỗi: ${info.message}`);
    }
  };

  const promptDelete = (category: Category, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteTarget(category);
    setIsDeleteModalOpen(true);
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;

    if (demo) {
      setCategories(prev => deleteCategory(prev, deleteTarget.id, demo));
      setIsDeleteModalOpen(false);
      setNotice(`Đã xóa danh mục "${deleteTarget.name}" khỏi danh sách demo.`);
      setDeleteTarget(null);
      return;
    }

    try {
      await adminCategoryService.deleteCategory(deleteTarget.id);
      setIsDeleteModalOpen(false);
      setNotice(`Đã xóa vĩnh viễn danh mục "${deleteTarget.name}".`);
      setDeleteTarget(null);
      await refreshCategories();
    } catch (err) {
      const info = getAdminCategoryError(err, true);
      setIsDeleteModalOpen(false);
      setDeleteTarget(null);
      setNotice(`Không thể xóa: ${info.message}`);
    }
  };

  return (
    <div className="admin-workspace-page admin-categories">
      {/* 4. Category Table Workspace */}
      <section className="admin-categories-list" aria-labelledby="categories-list-title">
        <div className="admin-categories-list-heading">
          <h2 id="categories-list-title">Danh sách phân loại trải nghiệm</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span>
              {filtered.length} / {visibleCategories.length} danh mục
            </span>
            <AdminButton variant="primary" onClick={openCreateDrawer}>
              <Plus size={16} aria-hidden="true" /> Thêm danh mục
            </AdminButton>
          </div>
        </div>

        {/* Filter Tabs */}
        <AdminFilterTabs
          label="Lọc trạng thái danh mục"
          value={
            certFilter === 'required'
              ? 'cert_required'
              : certFilter === 'unknown'
              ? 'cert_unknown'
              : statusFilter
          }
          onChange={val => {
            setCurrentPage(1);
            if (val === 'cert_required') {
              setCertFilter('required');
              setStatusFilter('all');
            } else if (val === 'cert_unknown') {
              setCertFilter('unknown');
              setStatusFilter('all');
            } else {
              setCertFilter('all');
              setStatusFilter(val as CategoryStatusFilter);
            }
          }}
          items={[
            { value: 'all', label: 'Tất cả', count: visibleCategories.length },
            { value: 'active', label: 'Đang hiển thị', count: activeCount },
            { value: 'inactive', label: 'Tạm ẩn', count: inactiveCount },
            { value: 'cert_required', label: 'Yêu cầu chứng chỉ', count: certRequiredCount },
            ...(certUnknownCount > 0 ? [{ value: 'cert_unknown', label: 'Chưa rõ chứng chỉ', count: certUnknownCount }] : []),
          ]}
        />

        {/* Search Toolbar */}
        <div className="admin-workspace-toolbar">
          <label className="admin-search-field">
            <Search size={18} aria-hidden="true" />
            <input
              type="search"
              value={searchQuery}
              onChange={e => {
                setSearchQuery(e.target.value);
                setCurrentPage(1);
              }}
              aria-label="Tìm theo tên danh mục hoặc slug URL"
              placeholder="Tìm theo tên danh mục hoặc slug..."
            />
          </label>
          {(searchQuery || statusFilter !== 'all' || certFilter !== 'all') && (
            <AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>
          )}
        </div>

        {/* Loading & Error States */}
        {loading && (
          <AdminState
            kind="empty"
            compact
            title="Đang tải danh mục trải nghiệm..."
            description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
          />
        )}
        {errorMessage && !loading && (
          <AdminState
            kind="error"
            compact
            title="Không thể tải danh mục"
            description={errorMessage}
          />
        )}

        {/* Data Table */}
        {!loading && !errorMessage && (
        <div className="admin-categories-table-scroll" role="region" aria-label="Bảng danh mục" tabIndex={0}>
          <table className="admin-workspace-table admin-categories-table">
            <caption className="sr-only">Danh mục trải nghiệm du lịch biển</caption>
            <thead>
              <tr>
                <th scope="col">Biểu tượng & Tên danh mục</th>
                <th scope="col">Đường dẫn (Slug URL)</th>
                <th scope="col">Chứng chỉ an toàn</th>
                <th scope="col">Hiển thị</th>
                <th scope="col">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {paginatedCategories.map(cat => {
                const IconComponent = getCategoryIcon(cat.iconUrl);
                return (
                  <tr key={cat.id}>
                    <td>
                      <div className="admin-categories-identity">
                        <div className="admin-categories-icon-badge" aria-hidden="true">
                          <IconComponent size={22} />
                        </div>
                        <div>
                          <strong>{cat.name}</strong>
                          <span>{cat.nameEn}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="admin-categories-slug-chip">/{cat.slug}</span>
                    </td>
                    <td>
                      {cat.requiresSafetyCert === true ? (
                        <AdminBadge tone="amber">
                          <ShieldCheck size={12} aria-hidden="true" /> Bắt buộc khai báo
                        </AdminBadge>
                      ) : cat.requiresSafetyCert === false ? (
                        <AdminBadge tone="neutral">Tùy chọn</AdminBadge>
                      ) : (
                        <AdminBadge tone="neutral">Chưa có dữ liệu</AdminBadge>
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        onClick={e => handleToggle(cat.id, e)}
                        className="admin-categories-toggle-btn"
                        data-active={cat.isActive}
                        aria-label={`Chuyển trạng thái hiển thị cho ${cat.name}`}
                      >
                        <span className="admin-categories-toggle-dot" aria-hidden="true" />
                        <span>{cat.isActive ? 'Đang hiển thị' : 'Tạm ẩn'}</span>
                      </button>
                    </td>
                    <td>
                      <div className="admin-categories-actions-group">
                        <button
                          type="button"
                          className="admin-action-icon-btn"
                          aria-label={`Chỉnh sửa ${cat.name}`}
                          title="Chỉnh sửa danh mục"
                          onClick={() => openEditDrawer(cat)}
                        >
                          <Pencil size={16} aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          className="admin-action-icon-btn admin-action-icon-btn--danger"
                          aria-label={`Xóa danh mục ${cat.name}`}
                          title="Xóa danh mục"
                          onClick={e => promptDelete(cat, e)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      </div>
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
            recordLabel="danh mục"
            onPageChange={setCurrentPage}
          />
        )}

        {/* Empty State */}
        {!loading && !errorMessage && filtered.length === 0 && (
          <AdminState
            kind="empty"
            compact
            title="Không tìm thấy danh mục phù hợp"
            description="Hãy thử từ khóa tìm kiếm khác hoặc xóa bộ lọc để xem toàn bộ danh sách."
            action={<AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>}
          />
        )}

        <div className="admin-categories-list-footer">
          Hiển thị {filtered.length} danh mục {demo ? 'mẫu · Danh mục được gán trực tiếp vào các tour và trải nghiệm biển của Vendor.' : 'từ hệ thống cơ sở dữ liệu DANASEA.'}
        </div>
      </section>

      {/* Floating Notice */}
      {!isDrawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}

      {/* 5. Create / Edit Category Drawer */}
      <AdminDialog
        open={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        variant="drawer"
        title={editingCategory ? `Chỉnh sửa: ${editingCategory.name}` : 'Thêm mới danh mục trải nghiệm'}
        description={
          demo
            ? 'Cấu hình thông tin danh mục dịch vụ biển · Dữ liệu dùng thử trong phiên'
            : 'Cấu hình thông tin danh mục dịch vụ biển trên hệ thống DANASEA'
        }
        footer={
          <div className="admin-categories-drawer-footer">
            <AdminButton onClick={() => setIsDrawerOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" onClick={handleSave}>
              {editingCategory ? 'Lưu thay đổi' : 'Tạo danh mục mới'}
            </AdminButton>
          </div>
        }
      >
        <form onSubmit={handleSave} className="admin-categories-form">
          {formError && <div className="admin-categories-form-error">{formError}</div>}

          <div className="admin-categories-form-row">
            <label className="admin-field">
              <span>Tên danh mục (Tiếng Việt) *</span>
              <input
                type="text"
                value={formName}
                onChange={e => handleNameChange(e.target.value)}
                placeholder="VD: Chèo SUP & Kayak"
                required
              />
            </label>

            <label className="admin-field">
              <span>Tên tiếng Anh (name_en) *</span>
              <input
                type="text"
                value={formNameEn}
                onChange={e => setFormNameEn(e.target.value)}
                placeholder="VD: Stand-Up Paddleboarding & Kayak"
                required
              />
            </label>
          </div>

          <div className="admin-categories-form-row">
            <label className="admin-field">
              <span>Mã định danh URL (slug) *</span>
              <input
                type="text"
                value={formSlug}
                onChange={e => setFormSlug(e.target.value.toLowerCase().replace(/\s+/g, '-'))}
                placeholder="VD: cheo-sup-kayak"
                required
              />
              <small>Đường dẫn hiển thị: danasea.vn/{formSlug || 'ten-danh-muc'}</small>
            </label>

            <label className="admin-field">
              <span>Danh mục cha (tùy chọn)</span>
              <select value={formParentId} onChange={e => setFormParentId(e.target.value)}>
                <option value="">Không có danh mục cha (Cấp 1)</option>
                {categories
                  .filter(c => !editingCategory || c.id !== editingCategory.id)
                  .map(c => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </select>
            </label>
          </div>

          <label className="admin-field">
            <span>Biểu tượng hiển thị</span>
            <div className="admin-categories-icon-picker">
              {ICON_PRESETS.map(preset => {
                const Icon = preset.icon;
                const isSelected = formIcon === preset.key;
                return (
                  <button
                    key={preset.key}
                    type="button"
                    className="admin-categories-icon-opt"
                    data-selected={isSelected}
                    onClick={() => setFormIcon(preset.key)}
                    title={preset.label}
                  >
                    <Icon size={20} />
                  </button>
                );
              })}
            </div>
          </label>

          <div className="admin-categories-toggle-row">
            <span>
              <strong>Yêu cầu chứng chỉ an toàn (khai báo)</strong>
              <small>Vendor phải nộp giấy tờ liên quan khi tạo dịch vụ trong danh mục này</small>
            </span>
            <input
              type="checkbox"
              checked={formRequiresCert}
              onChange={e => setFormRequiresCert(e.target.checked)}
            />
          </div>

          <div className="admin-categories-toggle-row">
            <span>
              <strong>Kích hoạt hiển thị</strong>
              <small>Cho phép du khách tìm kiếm và đặt dịch vụ thuộc nhóm này</small>
            </span>
            <input
              type="checkbox"
              checked={formIsActive}
              onChange={e => setFormIsActive(e.target.checked)}
            />
          </div>
        </form>
      </AdminDialog>

      {/* 6. Delete Confirmation Modal */}
      <AdminDialog
        open={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        variant="modal"
        title="Xóa danh mục trải nghiệm"
        description="Thao tác này sẽ xóa vĩnh viễn danh mục khỏi hệ thống"
        footer={
          <>
            <AdminButton onClick={() => setIsDeleteModalOpen(false)}>Quay lại</AdminButton>
            <AdminButton variant="danger" onClick={confirmDelete}>
              Xác nhận xóa
            </AdminButton>
          </>
        }
      >
        <div className="admin-categories-delete-dialog">
          <p>
            Bạn có chắc chắn muốn xóa danh mục <strong>"{deleteTarget?.name}"</strong> không?
          </p>
          <p className="admin-detail-note">
            Lưu ý: Chỉ có thể xóa danh mục không có bất kỳ dịch vụ nào liên kết, không có danh mục con và không có quy tắc hoa hồng / an toàn. Thao tác này không thể hoàn tác.
          </p>
        </div>
      </AdminDialog>
    </div>
  );
}

export default Categories;
