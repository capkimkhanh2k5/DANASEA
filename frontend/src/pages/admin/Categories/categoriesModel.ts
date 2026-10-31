import type { Category } from '../../../types';

export type CategoryStatusFilter = 'all' | 'active' | 'inactive';
export type CategoryCertFilter = 'all' | 'required' | 'optional';

export function slugifyCategory(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

export function filterCategories(
  categories: Category[],
  query: string,
  status: CategoryStatusFilter,
  certFilter: CategoryCertFilter
): Category[] {
  const search = query.trim().toLowerCase();
  return categories.filter(category => {
    const matchesSearch =
      !search ||
      category.name.toLowerCase().includes(search) ||
      (category.nameEn && category.nameEn.toLowerCase().includes(search)) ||
      category.slug.toLowerCase().includes(search) ||
      category.id.toLowerCase().includes(search);

    const matchesStatus =
      status === 'all' ||
      (status === 'active' && category.isActive) ||
      (status === 'inactive' && !category.isActive);

    const matchesCert =
      certFilter === 'all' ||
      (certFilter === 'required' && category.requiresSafetyCert) ||
      (certFilter === 'optional' && !category.requiresSafetyCert);

    return matchesSearch && matchesStatus && matchesCert;
  });
}

export type CategoryFormData = {
  name: string;
  nameEn: string;
  slug: string;
  parentId: string;
  iconUrl: string;
  isActive: boolean;
  requiresSafetyCert: boolean;
};

export function validateCategoryForm(data: CategoryFormData): { valid: boolean; error?: string } {
  if (!data.name.trim()) {
    return { valid: false, error: 'Tên danh mục không được để trống' };
  }
  if (!data.slug.trim()) {
    return { valid: false, error: 'Mã định danh URL (slug) không được để trống' };
  }
  if (!/^[a-z0-9-]+$/.test(data.slug.trim())) {
    return { valid: false, error: 'Slug chỉ được chứa chữ thường không dấu, số và dấu gạch ngang' };
  }
  return { valid: true };
}

export function saveCategory(
  categories: Category[],
  data: CategoryFormData,
  editingId: string | null,
  demo: boolean
): { updatedList: Category[]; savedItem: Category | null } {
  if (!demo) return { updatedList: categories, savedItem: null };

  const now = new Date().toISOString();
  if (editingId) {
    const existing = categories.find(c => c.id === editingId);
    if (!existing) return { updatedList: categories, savedItem: null };

    const updatedItem: Category = {
      ...existing,
      name: data.name.trim(),
      nameEn: data.nameEn.trim(),
      slug: data.slug.trim(),
      parentId: data.parentId || '',
      iconUrl: data.iconUrl || 'surfing',
      isActive: data.isActive,
      requiresSafetyCert: data.requiresSafetyCert,
      updatedAt: now,
    };

    return {
      updatedList: categories.map(c => (c.id === editingId ? updatedItem : c)),
      savedItem: updatedItem,
    };
  }

  const newItem: Category = {
    id: `cat-${Date.now()}`,
    name: data.name.trim(),
    nameEn: data.nameEn.trim(),
    slug: data.slug.trim(),
    parentId: data.parentId || '',
    iconUrl: data.iconUrl || 'surfing',
    isActive: data.isActive,
    requiresSafetyCert: data.requiresSafetyCert,
    createdAt: now,
    updatedAt: now,
  };

  return {
    updatedList: [...categories, newItem],
    savedItem: newItem,
  };
}

export function toggleCategoryStatus(
  categories: Category[],
  id: string,
  demo: boolean
): Category[] {
  if (!demo) return categories;
  return categories.map(c => (c.id === id ? { ...c, isActive: !c.isActive, updatedAt: new Date().toISOString() } : c));
}

export function deleteCategory(
  categories: Category[],
  id: string,
  demo: boolean
): Category[] {
  if (!demo) return categories;
  return categories.filter(c => c.id !== id);
}
