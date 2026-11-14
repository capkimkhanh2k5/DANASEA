import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  filterCategories,
  validateCategoryForm,
  slugifyCategory,
  saveCategory,
  toggleCategoryStatus,
  deleteCategory,
} from '../categoriesModel.ts';
import type { Category } from '../../../../types/index.ts';

const mockCategories: Category[] = [
  {
    id: 'cat-1',
    name: 'Chèo SUP & Kayak',
    nameEn: 'SUP & Kayak',
    slug: 'cheo-sup-kayak',
    parentId: '',
    iconUrl: 'surfing',
    isActive: true,
    requiresSafetyCert: true,
  },
  {
    id: 'cat-2',
    name: 'Dạo bộ bờ biển',
    nameEn: 'Beach Walking',
    slug: 'dao-bo-bo-bien',
    parentId: '',
    iconUrl: 'waves',
    isActive: true,
    requiresSafetyCert: false,
  },
  {
    id: 'cat-3',
    name: 'Lặn ngắm san hô',
    nameEn: 'Snorkeling',
    slug: 'lan-ngam-san-ho',
    parentId: '',
    iconUrl: 'scuba_diving',
    isActive: false,
    requiresSafetyCert: null, // Unknown / not returned by API
  },
  {
    id: 'cat-4',
    name: 'Thuê ván lướt sóng',
    nameEn: 'Surfboard Rental',
    slug: 'thue-van-luot-song',
    parentId: '',
    iconUrl: 'surfing',
    isActive: true,
    // requiresSafetyCert is undefined
  },
];

describe('categoriesModel - slugifyCategory', () => {
  it('converts Vietnamese text to valid slug', () => {
    assert.equal(slugifyCategory('Chèo SUP & Kayak Đà Nẵng'), 'cheo-sup-kayak-da-nang');
    assert.equal(slugifyCategory('Du thuyền 5 sao!'), 'du-thuyen-5-sao');
  });
});

describe('categoriesModel - filterCategories with certFilter', () => {
  it('certFilter "all" returns all matching items', () => {
    const result = filterCategories(mockCategories, '', 'all', 'all');
    assert.equal(result.length, 4);
  });

  it('certFilter "required" only returns categories with requiresSafetyCert === true', () => {
    const result = filterCategories(mockCategories, '', 'all', 'required');
    assert.equal(result.length, 1);
    assert.equal(result[0].id, 'cat-1');
  });

  it('certFilter "optional" only returns categories with requiresSafetyCert === false, excluding unknown', () => {
    const result = filterCategories(mockCategories, '', 'all', 'optional');
    assert.equal(result.length, 1);
    assert.equal(result[0].id, 'cat-2');
  });

  it('certFilter "unknown" returns categories with requiresSafetyCert === null or undefined', () => {
    const result = filterCategories(mockCategories, '', 'all', 'unknown');
    assert.equal(result.length, 2);
    assert.deepEqual(result.map(c => c.id).sort(), ['cat-3', 'cat-4']);
  });

  it('filters by status and search combined with certFilter', () => {
    const result = filterCategories(mockCategories, 'Kayak', 'active', 'required');
    assert.equal(result.length, 1);
    assert.equal(result[0].id, 'cat-1');

    const resultInactive = filterCategories(mockCategories, '', 'inactive', 'unknown');
    assert.equal(resultInactive.length, 1);
    assert.equal(resultInactive[0].id, 'cat-3');
  });
});

describe('categoriesModel - validateCategoryForm', () => {
  it('validates a valid form payload', () => {
    const res = validateCategoryForm({
      name: 'Câu cá biển',
      nameEn: 'Sea Fishing',
      slug: 'cau-ca-bien',
      parentId: '',
      iconUrl: 'waves',
      isActive: true,
      requiresSafetyCert: null,
    });
    assert.equal(res.valid, true);
  });

  it('rejects empty name or empty slug', () => {
    const emptyName = validateCategoryForm({
      name: '  ',
      nameEn: 'Fishing',
      slug: 'cau-ca',
      parentId: '',
      iconUrl: 'waves',
      isActive: true,
      requiresSafetyCert: null,
    });
    assert.equal(emptyName.valid, false);

    const emptySlug = validateCategoryForm({
      name: 'Câu cá',
      nameEn: 'Fishing',
      slug: '  ',
      parentId: '',
      iconUrl: 'waves',
      isActive: true,
      requiresSafetyCert: null,
    });
    assert.equal(emptySlug.valid, false);
  });

  it('rejects invalid slug characters', () => {
    const invalidSlug = validateCategoryForm({
      name: 'Câu cá',
      nameEn: 'Fishing',
      slug: 'cau_ca/bien',
      parentId: '',
      iconUrl: 'waves',
      isActive: true,
      requiresSafetyCert: null,
    });
    assert.equal(invalidSlug.valid, false);
  });
});

describe('categoriesModel - saveCategory & demo guards', () => {
  it('returns unchanged list when demo is false', () => {
    const res = saveCategory(mockCategories, {
      name: 'New',
      nameEn: 'New',
      slug: 'new',
      parentId: '',
      iconUrl: 'surfing',
      isActive: true,
      requiresSafetyCert: null,
    }, null, false);
    assert.equal(res.updatedList, mockCategories);
    assert.equal(res.savedItem, null);
  });

  it('appends new category when demo is true', () => {
    const res = saveCategory(mockCategories, {
      name: 'New Demo',
      nameEn: 'New Demo En',
      slug: 'new-demo',
      parentId: '',
      iconUrl: 'surfing',
      isActive: true,
      requiresSafetyCert: true,
    }, null, true);
    assert.equal(res.updatedList.length, mockCategories.length + 1);
    assert.equal(res.savedItem?.name, 'New Demo');
    assert.equal(res.savedItem?.requiresSafetyCert, true);
  });

  it('updates existing category when editing in demo mode', () => {
    const res = saveCategory(mockCategories, {
      name: 'Chèo SUP Nâng Cao',
      nameEn: 'Advanced SUP',
      slug: 'cheo-sup-nang-cao',
      parentId: '',
      iconUrl: 'surfing',
      isActive: true,
      requiresSafetyCert: false,
    }, 'cat-1', true);
    const updated = res.updatedList.find(c => c.id === 'cat-1');
    assert.equal(updated?.name, 'Chèo SUP Nâng Cao');
    assert.equal(updated?.requiresSafetyCert, false);
  });
});

describe('categoriesModel - toggle and delete guards', () => {
  it('toggleCategoryStatus toggles isActive only in demo mode', () => {
    const nonDemo = toggleCategoryStatus(mockCategories, 'cat-1', false);
    assert.equal(nonDemo[0].isActive, true);

    const demoRes = toggleCategoryStatus(mockCategories, 'cat-1', true);
    assert.equal(demoRes[0].isActive, false);
  });

  it('deleteCategory removes item only in demo mode', () => {
    const nonDemo = deleteCategory(mockCategories, 'cat-1', false);
    assert.equal(nonDemo.length, mockCategories.length);

    const demoRes = deleteCategory(mockCategories, 'cat-1', true);
    assert.equal(demoRes.length, mockCategories.length - 1);
  });
});
