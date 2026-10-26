import React, { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { adminCategoryService, getAdminCategoryError } from '../../../services/adminCategoryService';
import { useAuthStore } from '../../../store/useAuthStore';
import type { AdminCategoryDto } from '../../../types/adminCategory';

export function Categories() {
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const role = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && role === 'ADMIN';
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [needsReload, setNeedsReload] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const inFlight = useRef(false);
  const generation = useRef(0);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const controlsDisabled = !canAccess || loading || !hasLoaded || saving || needsReload;

  const [editingCategory, setEditingCategory] = useState<AdminCategoryDto | null>(null);

  // Form State
  const [formName, setFormName] = useState('');
  const [formNameEn, setFormNameEn] = useState('');
  const [formSlug, setFormSlug] = useState('');
  const [formParentId, setFormParentId] = useState('');
  const [formIcon, setFormIcon] = useState('surfing');
  const [formIsActive, setFormIsActive] = useState(true);

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToastMessage(msg);
    toastTimer.current = setTimeout(() => setToastMessage(null), 3000);
  };

  const populateForm = useCallback((cat: AdminCategoryDto | null) => {
    setEditingCategory(cat);
    setFormName(cat?.name ?? '');
    setFormNameEn(cat?.nameEn ?? '');
    setFormSlug(cat?.slug ?? '');
    setFormParentId(cat?.parentId ?? '');
    setFormIcon(cat ? cat.iconUrl ?? '' : 'surfing');
    setFormIsActive(cat?.isActive ?? true);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const current = ++generation.current;
    const load = async () => {
      setLoading(true);
      setHasLoaded(false);
      setCategories([]);
      populateForm(null);
      setErrorMessage(null);
      setToastMessage(null);
      setSaving(false);
      inFlight.current = false;
      if (!canAccess) {
        setErrorMessage(!isAuthenticated || !accessToken
          ? 'Vui lòng đăng nhập bằng tài khoản Admin để quản lý danh mục.'
          : 'Bạn không có quyền quản lý danh mục. Cần tài khoản Admin.');
        setLoading(false);
        return;
      }
      try {
        const data = await adminCategoryService.getCategories(controller.signal);
        if (controller.signal.aborted) return;
        setCategories(data);
        populateForm(data[0] ?? null);
        setNeedsReload(false);
        setHasLoaded(true);
      } catch (error) {
        if (!controller.signal.aborted) setErrorMessage(getAdminCategoryError(error).message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => {
      controller.abort();
      generation.current = current + 1;
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, [accessToken, canAccess, isAuthenticated, role, reloadVersion, populateForm]);

  const filteredCategories = useMemo(() => {
    return categories.filter(c =>
      c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      c.slug.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (c.nameEn ?? '').toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [categories, searchQuery]);

  const handleSelectForEdit = (cat: AdminCategoryDto) => {
    if (controlsDisabled || inFlight.current) return;
    populateForm(cat);
  };

  const handleResetToCreate = () => {
    if (controlsDisabled || inFlight.current) return;
    populateForm(null);
  };

  const parentOptions = useMemo(() => {
    const excluded = new Set<string>();
    if (editingCategory) {
      const childrenByParent = new Map<string, string[]>();
      for (const cat of categories) {
        if (cat.parentId) {
          const siblings = childrenByParent.get(cat.parentId) ?? [];
          siblings.push(cat.id);
          childrenByParent.set(cat.parentId, siblings);
        }
      }
      const pending = [editingCategory.id];
      while (pending.length) {
        const id = pending.pop()!;
        if (excluded.has(id)) continue;
        excluded.add(id);
        pending.push(...(childrenByParent.get(id) ?? []));
      }
    }
    return categories.filter(cat => !excluded.has(cat.id));
  }, [categories, editingCategory]);

  const persistCategory = async (
    request: () => Promise<AdminCategoryDto>,
    success: (cat: AdminCategoryDto) => string,
    updateEditor = true,
  ) => {
    if (controlsDisabled || inFlight.current) return;
    inFlight.current = true;
    const current = generation.current;
    setSaving(true);
    setErrorMessage(null);
    setToastMessage(null);
    try {
      const saved = await request();
      if (generation.current !== current) return;
      setCategories(prev => prev.some(cat => cat.id === saved.id)
        ? prev.map(cat => cat.id === saved.id ? saved : cat)
        : [...prev, saved]);
      if (updateEditor) populateForm(saved);
      else if (editingCategory?.id === saved.id) {
        setEditingCategory(saved);
        setFormIsActive(saved.isActive);
      }
      showToast(success(saved));
    } catch (error) {
      if (generation.current !== current) return;
      const failure = getAdminCategoryError(error, true);
      setErrorMessage(failure.message);
      setNeedsReload(failure.outcomeUnknown);
    } finally {
      if (generation.current === current) {
        inFlight.current = false;
        setSaving(false);
      }
    }
  };

  const handleAutoSlug = (name: string) => {
    setFormName(name);
    if (!editingCategory) {
      const slug = name
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[đĐ]/g, "d")
        .replace(/[^a-z0-9\s-]/g, "")
        .trim()
        .replace(/\s+/g, "-");
      setFormSlug(slug);
    }
  };

  const handleSaveCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (controlsDisabled || inFlight.current) return;
    if (!formName.trim() || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(formSlug.trim())) {
      setErrorMessage('Vui lòng nhập tên danh mục và slug gồm chữ thường, số, phân cách bằng dấu gạch ngang.');
      return;
    }
    if (formParentId && !parentOptions.some(cat => cat.id === formParentId) && formParentId !== editingCategory?.parentId) {
      setErrorMessage('Danh mục cha không hợp lệ hoặc gây vòng lặp phân cấp.');
      return;
    }
    const body = {
      name: formName.trim(),
      nameEn: formNameEn.trim(),
      slug: formSlug.trim(),
      ...(!editingCategory || formIcon !== (editingCategory.iconUrl ?? '') ? { iconUrl: formIcon } : {}),
    };
    await persistCategory(
      () => editingCategory
        ? adminCategoryService.updateCategory(editingCategory.id, {
            ...body,
            ...(formParentId && formParentId !== editingCategory.parentId ? { parentId: formParentId } : {}),
            isActive: formIsActive,
          })
        : adminCategoryService.createCategory({ ...body, parentId: formParentId || null }),
      saved => `Đã ${editingCategory ? 'cập nhật' : 'tạo mới'} danh mục "${saved.name}" thành công!`,
    );
  };

  const handleToggleActive = async (id: string, current: boolean, e: React.MouseEvent) => {
    e.stopPropagation();
    await persistCategory(
      () => current
        ? adminCategoryService.deactivateCategory(id)
        : adminCategoryService.updateCategory(id, { isActive: true }),
      saved => `Đã ${saved.isActive ? 'kích hoạt' : 'tạm ẩn'} danh mục "${saved.name}".`,
      false,
    );
  };

  const totalCount = categories.length;
  const activeCount = categories.filter(c => c.isActive).length;

  return (
    <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
      <div className="flex flex-col w-full animate-fade-in-up">
        {/* Toast */}
        {toastMessage && (
          <div role="status" className="fixed top-20 right-8 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-primary text-on-primary shadow-xl animate-fade-in-up">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            <span className="font-label-md text-label-md font-semibold">{toastMessage}</span>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-slate-200">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
              Quản lý Danh mục
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600 text-xs font-medium">
              Tổng: <strong>{hasLoaded ? totalCount : '—'}</strong> ({hasLoaded ? activeCount : '—'} kích hoạt)
            </span>
            <button 
              onClick={handleResetToCreate}
              disabled={controlsDisabled}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-white text-sm font-semibold hover:bg-primary-container transition-colors shadow-xs"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Tạo Danh mục Mới</span>
            </button>
          </div>
        </div>

        {errorMessage && (
          <div role="alert" className="mb-4 p-space-md bg-white rounded-xl border border-slate-200 text-sm text-slate-700">
            <p>{errorMessage}</p>
            {canAccess && (
              <button
                type="button"
                disabled={loading || saving}
                onClick={() => { if (!inFlight.current) setReloadVersion(version => version + 1); }}
                className="mt-2 px-3.5 py-1.5 rounded-lg bg-primary text-white text-sm font-semibold"
              >
                Tải lại danh mục
              </button>
            )}
          </div>
        )}

        {/* Main 2-Column Workspace */}
        <section className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* LEFT COLUMN (Span 7): Category List */}
          <div className="lg:col-span-7 flex flex-col gap-space-md">
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-space-sm p-space-md bg-white rounded-xl shadow-xs border border-slate-200">
              <div className="relative flex-1">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-[20px]">filter_list</span>
                <input 
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full h-10 pl-10 pr-space-md rounded-lg bg-slate-50 text-slate-800 font-body-md text-body-md placeholder:text-slate-400 focus:outline-none focus:bg-white focus:ring-1 focus:ring-primary border border-slate-200" 
                  placeholder="Lọc theo tên phân loại hoặc slug..." 
                  type="text"
                />
              </div>
            </div>

            {/* List Table */}
            <div className="bg-white rounded-xl shadow-xs overflow-hidden border border-slate-200">
              <div className="overflow-x-auto">
                <table aria-busy={loading || saving} className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 text-slate-700 text-xs font-semibold border-b border-slate-200">
                      <th className="py-3 px-4 border-r border-slate-200 whitespace-nowrap">Biểu tượng &amp; Tên danh mục</th>
                      <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Slug (URL)</th>
                      <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap text-center">Chứng chỉ</th>
                      <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap text-center">Hiển thị</th>
                      <th className="py-3 px-4 text-center whitespace-nowrap">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-xs text-slate-700">
                    {(loading || !hasLoaded || filteredCategories.length === 0) && (
                      <tr>
                        <td colSpan={5} className="py-3.5 px-4 text-center text-slate-500" role="status">
                          {loading ? 'Đang tải danh mục...' : !hasLoaded ? 'Chưa tải được dữ liệu danh mục.'
                            : categories.length === 0 ? 'Chưa có danh mục nào.' : 'Không tìm thấy danh mục phù hợp.'}
                        </td>
                      </tr>
                    )}
                    {filteredCategories.map(cat => {
                      const isSelected = cat.id === editingCategory?.id;
                      return (
                        <tr 
                          key={cat.id}
                          onClick={() => handleSelectForEdit(cat)}
                          className={`cursor-pointer transition-colors ${
                            isSelected ? 'bg-primary/5 hover:bg-primary/10' : 'hover:bg-slate-50'
                          }`}
                        >
                          <td className="py-3.5 px-4 border-r border-slate-200">
                            <div className="flex items-center gap-3">
                              <div className="w-9 h-9 rounded-xl bg-primary/10 border border-primary/20 text-primary flex items-center justify-center font-bold">
                                <span className="material-symbols-outlined text-[20px]">
                                  {/^(https?:\/\/|\/)/.test(cat.iconUrl ?? '')
                                    ? <img src={cat.iconUrl!} alt="" className="w-5 h-5 object-contain" />
                                    : cat.iconUrl || 'surfing'}
                                </span>
                              </div>
                              <div>
                                <span className="font-bold text-slate-800 block">{cat.name}</span>
                                <span className="text-[11px] text-slate-400 italic">{cat.nameEn}</span>
                              </div>
                            </div>
                          </td>

                          <td className="py-3.5 px-3 font-mono text-xs text-slate-500 border-r border-slate-200 whitespace-nowrap">
                            /{cat.slug}
                          </td>

                          <td className="py-3.5 px-3 text-center border-r border-slate-200 whitespace-nowrap">
                            <span title="API Categories không trả về requiresSafetyCert" className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 border border-slate-200 text-[11px]">
                              Chưa có dữ liệu
                            </span>
                          </td>

                          <td className="py-3.5 px-3 text-center border-r border-slate-200 whitespace-nowrap">
                            <button
                              type="button"
                              onClick={(e) => handleToggleActive(cat.id, cat.isActive, e)}
                              disabled={controlsDisabled}
                              aria-label={`${cat.isActive ? 'Tạm ẩn' : 'Kích hoạt lại'} danh mục ${cat.name}`}
                              aria-pressed={cat.isActive}
                              className={`w-9 h-5 rounded-full transition-colors relative inline-flex items-center p-0.5 ${
                                cat.isActive ? 'bg-primary' : 'bg-slate-300'
                              }`}
                            >
                              <span className={`w-4 h-4 rounded-full bg-white transition-transform ${
                                cat.isActive ? 'translate-x-4' : 'translate-x-0'
                              }`} />
                            </button>
                          </td>

                          <td className="py-3.5 px-4 text-center whitespace-nowrap">
                            <div className="flex items-center justify-center gap-1.5">
                              <button 
                                onClick={(e) => { e.stopPropagation(); handleSelectForEdit(cat); }}
                                disabled={controlsDisabled}
                                className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 transition-colors"
                                title="Chỉnh sửa"
                              >
                                <span className="material-symbols-outlined text-[18px]">edit</span>
                              </button>
                              <button 
                                disabled
                                className="p-1.5 rounded-lg bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 transition-colors"
                                title="Backend chưa hỗ trợ xóa danh mục; dùng công tắc Hiển thị để tạm ẩn"
                                aria-label="Xóa danh mục chưa được Backend hỗ trợ"
                              >
                                <span className="material-symbols-outlined text-[18px]">delete</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* RIGHT COLUMN (Span 5): Editor Form */}
          <div className="lg:col-span-5 flex flex-col gap-space-md">
            <div className="bg-surface-container-lowest rounded-2xl shadow-sm p-space-xl flex flex-col border border-outline-variant/20">
              <div className="flex items-center justify-between pb-space-md mb-space-md border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center shadow-sm">
                    <span className="material-symbols-outlined text-[24px]">
                      {/^(https?:\/\/|\/)/.test(formIcon)
                        ? <img src={formIcon} alt="" className="w-6 h-6 object-contain" />
                        : formIcon || 'surfing'}
                    </span>
                  </div>
                  <div>
                    <span className="font-label-sm text-label-sm text-primary font-medium">
                      {editingCategory ? 'Chỉnh sửa danh mục' : 'Thêm mới danh mục'}
                    </span>
                    <h2 className="font-headline-md font-bold text-on-surface truncate">
                      {editingCategory ? editingCategory.name : 'Tạo mới'}
                    </h2>
                  </div>
                </div>
                <button 
                  onClick={handleResetToCreate}
                  disabled={controlsDisabled}
                  className="px-2.5 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface-variant text-xs font-semibold"
                >
                  Tạo mới
                </button>
              </div>

              <form onSubmit={handleSaveCategory} aria-busy={saving} className="flex flex-col gap-4">
                <div>
                  <label className="block text-xs font-bold text-on-surface mb-1">Tên danh mục (Tiếng Việt) *</label>
                  <input 
                    value={formName}
                    onChange={(e) => handleAutoSlug(e.target.value)}
                    required
                    maxLength={100}
                    disabled={controlsDisabled}
                    placeholder="VD: Lặn ngắm san hô"
                    className="w-full h-11 px-3.5 rounded-xl bg-surface-container-low text-on-surface font-body-md text-sm focus:outline-none focus:ring-2 focus:ring-primary border border-outline-variant/30"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-on-surface mb-1">Tên tiếng Anh (name_en)</label>
                  <input 
                    value={formNameEn}
                    onChange={(e) => setFormNameEn(e.target.value)}
                    maxLength={100}
                    disabled={controlsDisabled}
                    placeholder="VD: Scuba Diving & Snorkeling"
                    className="w-full h-11 px-3.5 rounded-xl bg-surface-container-low text-on-surface font-body-md text-sm focus:outline-none focus:ring-2 focus:ring-primary border border-outline-variant/30"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-on-surface mb-1">Mã định danh URL (slug) *</label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-outline font-mono text-xs">danasea.vn/</span>
                    <input 
                      value={formSlug}
                      onChange={(e) => setFormSlug(e.target.value)}
                      required
                      maxLength={120}
                      pattern="[a-z0-9]+(-[a-z0-9]+)*"
                      disabled={controlsDisabled}
                      className="w-full h-11 pl-24 pr-3.5 rounded-xl bg-surface-container-low text-on-surface font-mono text-xs focus:outline-none focus:ring-2 focus:ring-primary border border-outline-variant/30"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="category-parent" className="block text-xs font-bold text-on-surface mb-1">Danh mục cha</label>
                  <select
                    id="category-parent"
                    value={formParentId}
                    onChange={e => setFormParentId(e.target.value)}
                    disabled={controlsDisabled}
                    className="w-full h-11 px-3.5 rounded-xl bg-surface-container-low text-on-surface font-body-md text-sm focus:outline-none focus:ring-2 focus:ring-primary border border-outline-variant/30"
                  >
                    <option value="" disabled={!!editingCategory?.parentId}>Không có (danh mục gốc)</option>
                    {editingCategory?.parentId && !parentOptions.some(cat => cat.id === editingCategory.parentId) && (
                      <option value={editingCategory.parentId} disabled>Danh mục cha hiện tại ({editingCategory.parentId})</option>
                    )}
                    {parentOptions.map(cat => <option key={cat.id} value={cat.id}>{cat.name} (/{cat.slug})</option>)}
                  </select>
                  {!!editingCategory?.parentId && (
                    <p className="mt-1 text-xs text-on-surface-variant">Backend chưa hỗ trợ tách danh mục con về danh mục gốc.</p>
                  )}
                </div>

                {/* Icon Selector */}
                <div>
                  <label className="block text-xs font-bold text-on-surface mb-1">Biểu tượng (Icon)</label>
                  <div className="grid grid-cols-6 gap-2">
                    {['surfing', 'speed', 'scuba_diving', 'kayaking', 'sailing', 'waves'].map(icon => (
                      <button
                        key={icon}
                        type="button"
                        onClick={() => setFormIcon(icon)}
                        disabled={controlsDisabled}
                        className={`h-10 rounded-xl flex items-center justify-center transition-all ${
                          formIcon === icon
                            ? 'bg-primary text-on-primary shadow-xs'
                            : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                        }`}
                      >
                        <span className="material-symbols-outlined text-[20px]">{icon}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Requirements & Status Toggles */}
                <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low">
                  <span className="text-xs font-bold text-on-surface">Yêu cầu chứng chỉ an toàn (requires_safety_cert)</span>
                  <input 
                    type="checkbox"
                    ref={element => { if (element) element.indeterminate = true; }}
                    aria-checked="mixed"
                    disabled
                    aria-label="Chứng chỉ an toàn chưa có dữ liệu từ API Categories"
                    className="w-4 h-4 accent-primary rounded cursor-pointer"
                  />
                </div>
                <p className="text-xs text-on-surface-variant">API Categories chưa trả về hoặc hỗ trợ sửa chứng chỉ an toàn; ô này không thể hiện giá trị thực. Backend chưa hỗ trợ xóa danh mục.</p>

                <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low">
                  <span className="text-xs font-bold text-on-surface">Kích hoạt hiển thị (is_active)</span>
                  <input 
                    type="checkbox"
                    checked={formIsActive}
                    onChange={(e) => setFormIsActive(e.target.checked)}
                    disabled={controlsDisabled || !editingCategory}
                    aria-label="Kích hoạt hiển thị danh mục"
                    className="w-4 h-4 accent-primary rounded cursor-pointer"
                  />
                </div>
                {!editingCategory && <p className="text-xs text-on-surface-variant">Backend luôn tạo danh mục ở trạng thái kích hoạt. Có thể tạm ẩn sau khi tạo.</p>}

                <div className="flex items-center gap-2 pt-2">
                  <button 
                    type="submit"
                    disabled={controlsDisabled}
                    className="flex-1 h-11 rounded-xl bg-primary text-on-primary font-bold text-sm shadow-sm hover:bg-primary-container transition-all flex items-center justify-center gap-1.5"
                  >
                    <span className="material-symbols-outlined text-[18px]">save</span>
                    <span>{saving ? 'Đang lưu...' : editingCategory ? 'Lưu thay đổi' : 'Tạo danh mục'}</span>
                  </button>
                  <button 
                    type="button"
                    onClick={handleResetToCreate}
                    disabled={controlsDisabled}
                    className="h-11 px-4 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-semibold text-sm"
                  >
                    Hủy
                  </button>
                </div>
              </form>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}

export default Categories;
