import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Info,
  Pencil,
  Waves,
  Wind,
} from 'lucide-react';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminNotice, AdminPagination } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  adminSettingService,
  getAdminSettingError,
} from '../../../services/adminSettingService';
import { adminCategoryService } from '../../../services/adminCategoryService';
import type { AdminCategoryDto } from '../../../types/adminCategory';
import type {
  CommissionRuleDto,
  SystemConfigDto,
} from '../../../types/adminSetting';
import {
  DEFAULT_SETTINGS,
  formatPercentDisplay,
  getCategoryGroupMeta,
  parseSafetyRebatePercent,
  toSafetyRebatePayload,
  validateCommissionRatePercent,
  validateDurationDays,
  validateSafetyRebateRate,
  type DisplayCategoryRule,
} from './settingsModel';
import './Settings.css';

export function Settings() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // API Data State
  const [commissionRules, setCommissionRules] = useState<CommissionRuleDto[]>([]);
  const [systemConfigs, setSystemConfigs] = useState<SystemConfigDto[]>([]);
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  // Notice & Preview states
  const [notice, setNotice] = useState<string | null>(null);
  const [previewState, setPreviewState] = useState<'normal' | 'loading' | 'error' | 'empty'>('normal');

  // Dialog State 1: Commission Rule Edit (Category or Onboarding)
  const [editingRule, setEditingRule] = useState<CommissionRuleDto | null>(null);
  const [ruleRateDraft, setRuleRateDraft] = useState('');
  const [ruleDurationDraft, setRuleDurationDraft] = useState('');
  const [ruleIsActiveDraft, setRuleIsActiveDraft] = useState(true);
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [isSavingRule, setIsSavingRule] = useState(false);

  // Pagination State for Category Rules (8 rows per page)
  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);

  // Dialog State 2: Platform Commission Rate Edit (Global)
  const [isEditingPlatformRate, setIsEditingPlatformRate] = useState(false);
  const [platformRateDraft, setPlatformRateDraft] = useState('');
  const [platformRateError, setPlatformRateError] = useState<string | null>(null);
  const [isSavingPlatformRate, setIsSavingPlatformRate] = useState(false);

  // Dialog State 3: Safety Rebate Edit (Policy & Rate)
  const [isEditingSafetyRebate, setIsEditingSafetyRebate] = useState(false);
  const [safetyEnabledDraft, setSafetyEnabledDraft] = useState(true);
  const [safetyRateDraft, setSafetyRateDraft] = useState('');
  const [safetyError, setSafetyError] = useState<string | null>(null);
  const [isSavingSafety, setIsSavingSafety] = useState(false);

  // Dialog State 4: Inherited Default Category Info Dialog
  const [defaultInfoCategory, setDefaultInfoCategory] = useState<DisplayCategoryRule | null>(null);

  // --------------------------------------------------------------------------
  // Data Loading from Backend APIs
  // --------------------------------------------------------------------------
  const loadData = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    setFetchError(null);
    try {
      const sig = signal || new AbortController().signal;
      const [rules, configs, cats] = await Promise.all([
        adminSettingService.getCommissionRules(sig),
        adminSettingService.getSystemConfigs(sig),
        adminCategoryService.getCategories(sig).catch(() => [] as AdminCategoryDto[]),
      ]);
      setCommissionRules(rules);
      setSystemConfigs(configs);
      setCategories(cats);
    } catch (err) {
      if (signal?.aborted) return;
      const { message } = getAdminSettingError(err);
      setFetchError(message);
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    loadData(controller.signal);
    return () => controller.abort();
  }, [loadData]);

  // --------------------------------------------------------------------------
  // Derived Configurations
  // --------------------------------------------------------------------------
  const platformConfig = useMemo(() => {
    return systemConfigs.find(c => c.key.toUpperCase() === 'PLATFORM_COMMISSION_RATE');
  }, [systemConfigs]);

  const platformRatePercent = useMemo(() => {
    if (!platformConfig?.value) return 10.0;
    const num = Number(platformConfig.value.trim());
    return isNaN(num) ? 10.0 : num;
  }, [platformConfig]);

  const onboardingRule = useMemo(() => {
    return commissionRules.find(r => r.targetType === 'ONBOARDING') || null;
  }, [commissionRules]);

  const categoryRules: DisplayCategoryRule[] = useMemo(() => {
    const customRules = commissionRules.filter(r => r.targetType === 'CATEGORY');
    const matchedCategoryIds = new Set<string>();
    const matchedCategorySlugs = new Set<string>();
    const items: DisplayCategoryRule[] = [];

    // 1. Nhóm các danh mục đã có biểu phí riêng (Custom Rule từ DB)
    for (const rule of customRules) {
      if (rule.categoryId) matchedCategoryIds.add(rule.categoryId);
      if (rule.categorySlug) matchedCategorySlugs.add(rule.categorySlug);

      const cat = categories.find(c => c.id === rule.categoryId || c.slug === rule.categorySlug);
      const categoryName = rule.categoryName || cat?.name || rule.ruleName;
      const categorySlug = rule.categorySlug || cat?.slug || '';

      items.push({
        id: rule.id,
        ruleCode: rule.ruleCode,
        ruleName: rule.ruleName,
        targetType: 'CATEGORY',
        categoryId: rule.categoryId || cat?.id || rule.id,
        categoryName,
        categorySlug,
        commissionRatePercent: rule.commissionRatePercent,
        isActive: rule.isActive,
        isCustom: true,
        originalRule: rule,
      });
    }

    // 2. Nhóm các danh mục chưa cấu hình riêng (Kế thừa tỷ lệ hoa hồng mặc định của sàn)
    for (const cat of categories) {
      if (matchedCategoryIds.has(cat.id) || (cat.slug && matchedCategorySlugs.has(cat.slug))) {
        continue;
      }
      items.push({
        id: `default-${cat.id}`,
        ruleCode: `DEFAULT_${(cat.slug || cat.id).toUpperCase().replace(/-/g, '_')}`,
        ruleName: cat.name,
        targetType: 'CATEGORY',
        categoryId: cat.id,
        categoryName: cat.name,
        categorySlug: cat.slug,
        commissionRatePercent: platformRatePercent,
        isActive: cat.isActive,
        isCustom: false,
      });
    }

    // 3. Sắp xếp danh mục theo thứ tự nhóm phí: gear -> sports -> tours -> other, sau đó theo bảng chữ cái
    const groupOrder: Record<string, number> = { gear: 1, sports: 2, tours: 3, other: 4 };
    return items.sort((a, b) => {
      const metaA = getCategoryGroupMeta(a.categorySlug);
      const metaB = getCategoryGroupMeta(b.categorySlug);
      const orderA = groupOrder[metaA.groupId] || 99;
      const orderB = groupOrder[metaB.groupId] || 99;
      if (orderA !== orderB) return orderA - orderB;
      return a.categoryName.localeCompare(b.categoryName);
    });
  }, [commissionRules, categories, platformRatePercent]);

  const totalCategoryPages = Math.max(1, Math.ceil(categoryRules.length / PAGE_SIZE));

  // Automatically adjust currentPage if category rules change
  useEffect(() => {
    if (currentPage > totalCategoryPages) {
      setCurrentPage(totalCategoryPages);
    }
  }, [currentPage, totalCategoryPages]);

  const paginatedCategoryRules = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return categoryRules.slice(start, start + PAGE_SIZE);
  }, [categoryRules, currentPage, PAGE_SIZE]);

  const safetyEnabledConfig = useMemo(() => {
    return systemConfigs.find(c => c.key.toUpperCase() === 'SAFETY_REBATE_ENABLED');
  }, [systemConfigs]);

  const safetyRebateEnabled = useMemo(() => {
    if (!safetyEnabledConfig?.value) return true;
    return safetyEnabledConfig.value.trim().toLowerCase() !== 'false';
  }, [safetyEnabledConfig]);

  const safetyRateConfig = useMemo(() => {
    return systemConfigs.find(c => c.key.toUpperCase() === 'SAFETY_REBATE_RATE');
  }, [systemConfigs]);

  const safetyRebatePercent = useMemo(() => {
    return parseSafetyRebatePercent(safetyRateConfig?.value);
  }, [safetyRateConfig]);

  // --------------------------------------------------------------------------
  // Handlers: Dialog 1 — Edit Commission Rule (Category or Onboarding)
  // --------------------------------------------------------------------------
  const handleOpenEditRule = (rule: CommissionRuleDto) => {
    setEditingRule(rule);
    setRuleRateDraft(String(rule.commissionRatePercent));
    setRuleDurationDraft(rule.durationDays != null ? String(rule.durationDays) : '');
    setRuleIsActiveDraft(rule.isActive);
    setRuleError(null);
  };

  const handleSaveRule = async () => {
    if (!editingRule) return;

    const rateErr = validateCommissionRatePercent(ruleRateDraft);
    if (rateErr) {
      setRuleError(rateErr);
      return;
    }

    let parsedDuration: number | null = null;
    if (editingRule.targetType === 'ONBOARDING') {
      const durErr = validateDurationDays(ruleDurationDraft);
      if (durErr) {
        setRuleError(durErr);
        return;
      }
      parsedDuration = Number(ruleDurationDraft.trim());
    }

    const nextRate = Number(ruleRateDraft.trim());
    const isRateUnchanged = nextRate === editingRule.commissionRatePercent;
    const isDurationUnchanged = editingRule.targetType === 'ONBOARDING'
      ? parsedDuration === (editingRule.durationDays ?? 90)
      : true;
    const isActiveUnchanged = ruleIsActiveDraft === editingRule.isActive;

    // Nếu giá trị không đổi so với ban đầu thì đóng dialog và không hiện thông báo
    if (isRateUnchanged && isDurationUnchanged && isActiveUnchanged) {
      setEditingRule(null);
      return;
    }

    setRuleError(null);
    setIsSavingRule(true);

    try {
      const updated = await adminSettingService.updateCommissionRule(editingRule.id, {
        commissionRatePercent: nextRate,
        durationDays: parsedDuration,
        isActive: ruleIsActiveDraft,
        description: editingRule.description ?? undefined,
      });

      setCommissionRules(prev =>
        prev.map(r => (r.id === updated.id ? { ...r, ...updated } : r))
      );
      const rawRuleName = editingRule.categoryName || editingRule.ruleName || '';
      const cleanRuleName = rawRuleName.replace(/\s*\(3\s*tháng\s*đầu\)/gi, '').trim();
      const finalRuleName = editingRule.targetType === 'ONBOARDING' && !cleanRuleName
        ? 'Ưu đãi Đối tác mới'
        : (cleanRuleName || 'Quy tắc hoa hồng');
      setNotice(`Đã cập nhật quy tắc "${finalRuleName}" thành công.`);
      setEditingRule(null);
    } catch (err) {
      const { message } = getAdminSettingError(err, true);
      setRuleError(message);
    } finally {
      setIsSavingRule(false);
    }
  };

  // --------------------------------------------------------------------------
  // Handlers: Dialog 2 — Edit Platform Commission Rate (Global)
  // --------------------------------------------------------------------------
  const handleOpenEditPlatformRate = () => {
    setPlatformRateDraft(String(platformRatePercent));
    setPlatformRateError(null);
    setIsEditingPlatformRate(true);
  };

  const handleSavePlatformRate = async () => {
    const err = validateCommissionRatePercent(platformRateDraft);
    if (err) {
      setPlatformRateError(err);
      return;
    }

    const nextPlatformRate = Number(platformRateDraft.trim());
    // Nếu mức sàn không đổi so với ban đầu thì đóng dialog và không hiện thông báo
    if (nextPlatformRate === platformRatePercent) {
      setIsEditingPlatformRate(false);
      return;
    }

    setPlatformRateError(null);
    setIsSavingPlatformRate(true);

    try {
      const updated = await adminSettingService.updateSystemConfig('PLATFORM_COMMISSION_RATE', {
        value: platformRateDraft.trim(),
        description: 'Tỷ lệ hoa hồng mặc định của sàn (%)',
      });

      setSystemConfigs(prev =>
        prev.map(c => (c.key.toUpperCase() === 'PLATFORM_COMMISSION_RATE' ? { ...c, ...updated } : c))
      );
      setNotice(`Đã cập nhật mức sàn hoa hồng mặc định thành ${platformRateDraft.trim()}%.`);
      setIsEditingPlatformRate(false);
    } catch (err) {
      const { message } = getAdminSettingError(err, true);
      setPlatformRateError(message);
    } finally {
      setIsSavingPlatformRate(false);
    }
  };

  // --------------------------------------------------------------------------
  // Handlers: Dialog 3 — Edit Safety Rebate Policy & Rate
  // --------------------------------------------------------------------------
  const handleOpenEditSafetyRebate = () => {
    setSafetyEnabledDraft(safetyRebateEnabled);
    setSafetyRateDraft(String(safetyRebatePercent));
    setSafetyError(null);
    setIsEditingSafetyRebate(true);
  };

  const handleSaveSafetyRebate = async () => {
    const rateErr = validateSafetyRebateRate(safetyRateDraft);
    if (rateErr) {
      setSafetyError(rateErr);
      return;
    }

    const nextSafetyRate = Number(safetyRateDraft.trim());
    const isRateUnchanged = nextSafetyRate === safetyRebatePercent;
    const isEnabledUnchanged = safetyEnabledDraft === safetyRebateEnabled;

    // Nếu cấu hình thưởng không đổi so với ban đầu thì đóng dialog và không hiện thông báo
    if (isRateUnchanged && isEnabledUnchanged) {
      setIsEditingSafetyRebate(false);
      return;
    }

    setSafetyError(null);
    setIsSavingSafety(true);

    try {
      // P0-1: Chuyển đổi sang decimal fraction an toàn tuyệt đối (ví dụ 2.0% -> "0.02")
      const payloadValue = toSafetyRebatePayload(safetyRateDraft.trim());

      const [updatedEnabled, updatedRate] = await Promise.all([
        adminSettingService.updateSystemConfig('SAFETY_REBATE_ENABLED', {
          value: String(safetyEnabledDraft),
          description: 'Bật/tắt chế độ cộng thưởng tiền mặt an toàn biển khi quyết toán',
        }),
        adminSettingService.updateSystemConfig('SAFETY_REBATE_RATE', {
          value: payloadValue,
          description: 'Tỷ lệ thưởng an toàn biển và chất lượng dịch vụ cộng tiền mặt khi quyết toán (%)',
        }),
      ]);

      setSystemConfigs(prev => {
        let next = prev.map(c => {
          if (c.key.toUpperCase() === 'SAFETY_REBATE_ENABLED') return { ...c, ...updatedEnabled };
          if (c.key.toUpperCase() === 'SAFETY_REBATE_RATE') return { ...c, ...updatedRate };
          return c;
        });
        if (!next.some(c => c.key.toUpperCase() === 'SAFETY_REBATE_ENABLED')) {
          next = [...next, updatedEnabled];
        }
        if (!next.some(c => c.key.toUpperCase() === 'SAFETY_REBATE_RATE')) {
          next = [...next, updatedRate];
        }
        return next;
      });

      setNotice(
        `Đã cập nhật Quỹ thưởng An toàn: ${safetyEnabledDraft ? 'Đang bật' : 'Đã tắt'}, Tỷ lệ +${safetyRateDraft.trim()}%.`
      );
      setIsEditingSafetyRebate(false);
    } catch (err) {
      const { message } = getAdminSettingError(err, true);
      setSafetyError(message);
    } finally {
      setIsSavingSafety(false);
    }
  };

  return (
    <main className="admin-workspace-page admin-settings-page" aria-label="Trang cấu hình hoa hồng">
      {/* Top Toolbar (Chỉ hiển thị khi bật chế độ demo) */}
      {demo && (
        <div className="admin-settings-toolbar">
          <div className="admin-settings-actions">
            <div className="admin-settings-preview-ctrl">
              <label htmlFor="settings-preview-select">Xem thử:</label>
              <select
                id="settings-preview-select"
                aria-label="Mô phỏng trạng thái trang"
                value={previewState}
                onChange={e => setPreviewState(e.target.value as 'normal' | 'loading' | 'error' | 'empty')}
                className="admin-settings-preview-select"
              >
                <option value="normal">Bình thường</option>
                <option value="loading">Đang tải</option>
                <option value="error">Lỗi kết nối</option>
                <option value="empty">Dữ liệu trống</option>
              </select>
            </div>
          </div>
        </div>
      )}

      {/* Main Viewport States */}
      {previewState === 'loading' || (isLoading && previewState === 'normal') ? (
        <AdminState kind="loading" title="Đang tải dữ liệu cấu hình hệ thống & biểu phí hoa hồng…" />
      ) : previewState === 'error' || (fetchError && previewState === 'normal') ? (
        <AdminState
          kind="error"
          title="Không thể tải bảng cấu hình hoa hồng"
          description={fetchError || 'Đã xảy ra lỗi khi đồng bộ cấu hình từ hệ thống. Vui lòng kiểm tra lại kết nối.'}
          action={
            <AdminButton variant="secondary" onClick={() => loadData()}>
              Thử tải lại
            </AdminButton>
          }
        />
      ) : previewState === 'empty' || (commissionRules.length === 0 && previewState === 'normal') ? (
        <AdminState
          kind="empty"
          title="Chưa có quy tắc hoa hồng nào"
          description="Bảng quy tắc hoa hồng hệ thống hiện đang trống hoặc chưa được nạp dữ liệu ban đầu."
          action={
            <AdminButton variant="primary" onClick={() => loadData()}>
              Tải lại danh sách
            </AdminButton>
          }
        />
      ) : (
        <div className="admin-settings-container">
          {/* KHU VỰC 1 — BA THẺ CHỈ SỐ CHIẾN LƯỢC (3 STAT CARDS) */}
          <div className="admin-settings-strategy-grid" role="region" aria-label="Ba thẻ chỉ số hoa hồng chiến lược">
            {/* THẺ 1: HOA HỒNG MẶC ĐỊNH */}
            <section className="admin-settings-card" aria-labelledby="heading-global-card">
              <div className="admin-card-header-row">
                <div className="admin-card-title-group">
                  <span className="admin-card-eyebrow">Toàn sàn</span>
                  <h3 className="admin-card-title" id="heading-global-card">
                    Hoa hồng mặc định
                  </h3>
                </div>
                <button
                  type="button"
                  className="admin-card-action-btn"
                  onClick={handleOpenEditPlatformRate}
                  aria-label="Chỉnh sửa hoa hồng mặc định"
                >
                  <Pencil size={12} aria-hidden="true" />
                  <span>Chỉnh sửa</span>
                </button>
              </div>

              <div className="admin-card-body">
                <div className="admin-metric-value-row">
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{formatPercentDisplay(platformRatePercent)}</span>
                    <span className="admin-setting-value-unit">%</span>
                  </div>
                  <span className="admin-subbadge admin-subbadge--neutral">
                    Mức sàn cơ bản
                  </span>
                </div>
              </div>
            </section>

            {/* THẺ 2: ĐỐI TÁC MỚI */}
            <section className="admin-settings-card" aria-labelledby="heading-onboarding-card">
              <div className="admin-card-header-row">
                <div className="admin-card-title-group">
                  <span className="admin-card-eyebrow">Chính sách gia nhập</span>
                  <h3 className="admin-card-title" id="heading-onboarding-card">
                    Đối tác mới
                  </h3>
                </div>
                {onboardingRule && (
                  <button
                    type="button"
                    className="admin-card-action-btn"
                    onClick={() => handleOpenEditRule(onboardingRule)}
                    aria-label="Cấu hình ưu đãi đối tác mới"
                  >
                    <Pencil size={12} aria-hidden="true" />
                    <span>Cấu hình</span>
                  </button>
                )}
              </div>

              <div className="admin-card-body">
                <div className="admin-metric-value-row">
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">
                      {onboardingRule ? formatPercentDisplay(onboardingRule.commissionRatePercent) : '5'}
                    </span>
                    <span className="admin-setting-value-unit">%</span>
                  </div>
                  <div className="admin-card-badges-wrap">
                    <span className="admin-subbadge">
                      {onboardingRule?.durationDays ?? 90} ngày đầu
                    </span>
                    {onboardingRule && (
                      <span
                        className={`admin-subbadge ${
                          onboardingRule.isActive ? 'admin-subbadge--active' : 'admin-subbadge--inactive'
                        }`}
                      >
                        <span className="admin-subbadge-dot" aria-hidden="true" />
                        {onboardingRule.isActive ? 'Đang áp dụng' : 'Tạm dừng'}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </section>

            {/* THẺ 3: THƯỞNG AN TOÀN & SLA */}
            <section className="admin-settings-card" aria-labelledby="heading-safety-rebate-card">
              <div className="admin-card-header-row">
                <div className="admin-card-title-group">
                  <span className="admin-card-eyebrow">Chỉ số vận hành</span>
                  <h3 className="admin-card-title" id="heading-safety-rebate-card">
                    Thưởng An toàn & SLA
                  </h3>
                </div>
                <button
                  type="button"
                  className="admin-card-action-btn"
                  onClick={handleOpenEditSafetyRebate}
                  aria-label="Cấu hình chính sách thưởng an toàn và SLA"
                >
                  <Pencil size={12} aria-hidden="true" />
                  <span>Cấu hình</span>
                </button>
              </div>

              <div className="admin-card-body">
                <div className="admin-metric-value-row">
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">+{formatPercentDisplay(safetyRebatePercent)}</span>
                    <span className="admin-setting-value-unit">%</span>
                  </div>
                  <div className="admin-card-badges-wrap">
                    <span
                      className={`admin-subbadge ${
                        safetyRebateEnabled ? 'admin-subbadge--active' : 'admin-subbadge--inactive'
                      }`}
                    >
                      <span className="admin-subbadge-dot" aria-hidden="true" />
                      {safetyRebateEnabled ? 'Đang bật' : 'Đã tắt'}
                    </span>
                  </div>
                </div>
              </div>
            </section>
          </div>

          {/* KHU VỰC 2 — BẢNG BIỂU PHÍ HOA HỒNG THEO DANH MỤC (12 DANH MỤC) */}
          <section className="admin-rules-table-section" aria-labelledby="heading-category-rules-table">
            <div className="admin-rules-table-header">
              <div>
                <h2 id="heading-category-rules-table" className="admin-rules-table-title">
                  Bảng Biểu Phí Hoa Hồng Theo Danh Mục Dịch Vụ
                </h2>
              </div>
            </div>

            <div className="admin-table-responsive">
              <table className="admin-data-table" aria-label="Danh sách biểu phí hoa hồng theo danh mục">
                <thead>
                  <tr>
                    <th scope="col" style={{ width: '30%' }}>Danh mục</th>
                    <th scope="col" style={{ width: '25%' }}>Nhóm phí</th>
                    <th scope="col" style={{ width: '15%' }}>Tỷ lệ (%)</th>
                    <th scope="col" style={{ width: '15%' }}>Trạng thái</th>
                    <th scope="col" style={{ width: '15%', textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedCategoryRules.map(rule => {
                    const groupMeta = getCategoryGroupMeta(rule.categorySlug);
                    return (
                      <tr key={rule.id}>
                        <td>
                          <div className="admin-category-cell">
                            <span className="admin-category-name">
                              {rule.categoryName || rule.ruleName}
                            </span>
                            {rule.categorySlug && (
                              <span className="admin-category-slug">{rule.categorySlug}</span>
                            )}
                          </div>
                        </td>
                        <td>
                          <span
                            className={`badge-group badge-group--${groupMeta.groupId}`}
                            style={{
                              backgroundColor: groupMeta.badgeBg,
                              color: groupMeta.badgeText,
                              borderColor: groupMeta.badgeBorder,
                            }}
                          >
                            {groupMeta.shortName}
                          </span>
                        </td>
                        <td>
                          <span className="admin-rate-text">
                            {rule.commissionRatePercent.toFixed(1)}%
                          </span>
                        </td>
                        <td>
                          {rule.isCustom ? (
                            <AdminBadge tone={rule.isActive ? 'teal' : 'neutral'}>
                              {rule.isActive ? 'Tùy chỉnh' : 'Tạm dừng'}
                            </AdminBadge>
                          ) : (
                            <AdminBadge tone="neutral">
                              Mặc định sàn
                            </AdminBadge>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          {rule.isCustom && rule.originalRule ? (
                            <button
                              type="button"
                              className="admin-table-edit-btn"
                              onClick={() => handleOpenEditRule(rule.originalRule!)}
                              aria-label={`Chỉnh sửa hoa hồng danh mục ${rule.categoryName || rule.ruleName}`}
                            >
                              <Pencil size={12} aria-hidden="true" />
                              <span>Sửa</span>
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="admin-table-edit-btn admin-table-edit-btn--default"
                              onClick={() => setDefaultInfoCategory(rule)}
                              aria-label={`Xem thông tin mức sàn danh mục ${rule.categoryName || rule.ruleName}`}
                              title="Danh mục đang kế thừa mức sàn mặc định"
                            >
                              <Info size={12} aria-hidden="true" />
                              <span>Mặc định</span>
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <AdminPagination
              currentPage={currentPage}
              totalPages={totalCategoryPages}
              totalRecords={categoryRules.length}
              pageSize={PAGE_SIZE}
              recordLabel="danh mục"
              onPageChange={setCurrentPage}
              hideOnSinglePage={false}
            />
          </section>

          {/* KHU VỰC 3 — BẢO TOÀN TÍNH NĂNG CŨ: THAM CHIẾU AN TOÀN BIỂN */}
          <div className="admin-safety-group" role="region" aria-labelledby="heading-safety-group">
            <div className="admin-safety-group-header">
              <h2 id="heading-safety-group" className="admin-safety-group-title">
                Ngưỡng cảnh báo an toàn biển
              </h2>
            </div>

            <div className="admin-settings-grid-row">
              {/* Card Tham chiếu Chiều cao sóng */}
              <section className="admin-settings-card" aria-labelledby="heading-wave-card">
                <div className="admin-card-header-row">
                  <div className="admin-card-category" id="heading-wave-card">
                    <span className="admin-card-category-icon" aria-hidden="true">
                      <Waves size={16} />
                    </span>
                    <span>Chiều cao sóng tham chiếu</span>
                  </div>
                </div>

                <div className="admin-card-body">
                  <span className="admin-setting-label">Ngưỡng cảnh báo an toàn</span>
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{DEFAULT_SETTINGS.maxSafeWave}</span>
                    <span className="admin-setting-value-unit">m</span>
                  </div>
                  <p className="admin-setting-description">Ngưỡng tham chiếu vận hành thể thao biển.</p>
                </div>
              </section>

              {/* Card Tham chiếu Tốc độ gió giật */}
              <section className="admin-settings-card" aria-labelledby="heading-wind-card">
                <div className="admin-card-header-row">
                  <div className="admin-card-category" id="heading-wind-card">
                    <span className="admin-card-category-icon" aria-hidden="true">
                      <Wind size={16} />
                    </span>
                    <span>Tốc độ gió giật</span>
                  </div>
                </div>

                <div className="admin-card-body">
                  <span className="admin-setting-label">Ngưỡng tham chiếu cảnh báo</span>
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{DEFAULT_SETTINGS.maxSafeWind}</span>
                    <span className="admin-setting-value-unit">km/h</span>
                  </div>
                  <p className="admin-setting-description">Ngưỡng tham chiếu cảnh báo gió giật.</p>
                </div>
              </section>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          DIALOG 1: SỬA QUY TẮC HOA HỒNG (CATEGORY HOẶC ONBOARDING)
          ===================================================================== */}
      {editingRule && (
        <AdminDialog
          open={Boolean(editingRule)}
          title={`Chỉnh sửa: ${editingRule.categoryName || editingRule.ruleName}`}
          description={
            editingRule.targetType === 'ONBOARDING'
              ? 'Cấu hình tỷ lệ hoa hồng ưu đãi và số ngày áp dụng cho đối tác mới.'
              : `Cập nhật biểu phí hoa hồng cho danh mục trải nghiệm "${editingRule.categoryName || editingRule.ruleName}".`
          }
          onClose={() => !isSavingRule && setEditingRule(null)}
          footer={
            <>
              <AdminButton
                variant="secondary"
                onClick={() => setEditingRule(null)}
                disabled={isSavingRule}
              >
                Hủy bỏ
              </AdminButton>
              <AdminButton
                variant="primary"
                onClick={handleSaveRule}
                loading={isSavingRule}
                disabled={isSavingRule}
              >
                Lưu thay đổi
              </AdminButton>
            </>
          }
        >
          <form
            className="admin-dialog-form"
            onSubmit={e => {
              e.preventDefault();
              handleSaveRule();
            }}
          >
            <div className="admin-form-group">
              <label htmlFor="rule-rate-input" className="admin-form-label">
                Tỷ lệ hoa hồng (%) *
              </label>
              <div className="admin-form-input-wrap">
                <input
                  id="rule-rate-input"
                  type="number"
                  step="0.1"
                  min="0"
                  max="100"
                  value={ruleRateDraft}
                  onChange={e => {
                    setRuleRateDraft(e.target.value);
                    setRuleError(null);
                  }}
                  className={`admin-form-input ${ruleError ? 'admin-form-input--invalid' : ''}`}
                  placeholder="Ví dụ: 7.0"
                  autoFocus
                  required
                />
                <span className="admin-form-suffix" aria-hidden="true">%</span>
              </div>
            </div>

            {editingRule.targetType === 'ONBOARDING' && (
              <div className="admin-form-group">
                <label htmlFor="rule-duration-input" className="admin-form-label">
                  Thời hạn ưu đãi (ngày) *
                </label>
                <div className="admin-form-input-wrap">
                  <input
                    id="rule-duration-input"
                    type="number"
                    step="1"
                    min="1"
                    max="3650"
                    value={ruleDurationDraft}
                    onChange={e => {
                      setRuleDurationDraft(e.target.value);
                      setRuleError(null);
                    }}
                    className={`admin-form-input ${ruleError ? 'admin-form-input--invalid' : ''}`}
                    placeholder="Mặc định: 90"
                    required
                  />
                  <span className="admin-form-suffix" aria-hidden="true">ngày</span>
                </div>
                <p className="admin-form-hint">
                  Tính từ ngày tài khoản đối tác được Quản trị viên duyệt (APPROVED).
                </p>
              </div>
            )}

            <div className="admin-form-switch-row">
              <div className="admin-form-switch-label">
                <span className="admin-form-switch-title">Trạng thái kích hoạt</span>
                <span className="admin-form-switch-desc">
                  {ruleIsActiveDraft
                    ? 'Quy tắc đang áp dụng tính phí tự động.'
                    : 'Tạm dừng quy tắc; hệ thống sẽ tự động dùng mức mặc định.'}
                </span>
              </div>
              <input
                type="checkbox"
                checked={ruleIsActiveDraft}
                onChange={e => setRuleIsActiveDraft(e.target.checked)}
                className="admin-switch-input"
                aria-label="Kích hoạt quy tắc hoa hồng"
              />
            </div>

            {ruleError && (
              <p className="admin-form-error" role="alert">
                {ruleError}
              </p>
            )}
          </form>
        </AdminDialog>
      )}

      {/* =====================================================================
          DIALOG 2: SỬA MỨC SÀN HOA HỒNG MẶC ĐỊNH (GLOBAL)
          ===================================================================== */}
      {isEditingPlatformRate && (
        <AdminDialog
          open={isEditingPlatformRate}
          title="Chỉnh sửa mức sàn hoa hồng mặc định"
          description="Cập nhật tỷ lệ hoa hồng mặc định toàn sàn. Áp dụng khi không khớp quy tắc đối tác mới hoặc danh mục."
          onClose={() => !isSavingPlatformRate && setIsEditingPlatformRate(false)}
          footer={
            <>
              <AdminButton
                variant="secondary"
                onClick={() => setIsEditingPlatformRate(false)}
                disabled={isSavingPlatformRate}
              >
                Hủy bỏ
              </AdminButton>
              <AdminButton
                variant="primary"
                onClick={handleSavePlatformRate}
                loading={isSavingPlatformRate}
                disabled={isSavingPlatformRate}
              >
                Lưu mức sàn
              </AdminButton>
            </>
          }
        >
          <form
            className="admin-dialog-form"
            onSubmit={e => {
              e.preventDefault();
              handleSavePlatformRate();
            }}
          >
            <div className="admin-form-group">
              <label htmlFor="platform-rate-input" className="admin-form-label">
                Tỷ lệ hoa hồng mặc định (%) *
              </label>
              <div className="admin-form-input-wrap">
                <input
                  id="platform-rate-input"
                  type="number"
                  step="0.1"
                  min="0"
                  max="100"
                  value={platformRateDraft}
                  onChange={e => {
                    setPlatformRateDraft(e.target.value);
                    setPlatformRateError(null);
                  }}
                  className={`admin-form-input ${platformRateError ? 'admin-form-input--invalid' : ''}`}
                  autoFocus
                  required
                />
                <span className="admin-form-suffix" aria-hidden="true">%</span>
              </div>
            </div>

            <p className="admin-form-hint">
              Mức sàn này được lưu vào cấu hình hệ thống và áp dụng cho các đơn hàng phát sinh tiếp theo.
            </p>

            {platformRateError && (
              <p className="admin-form-error" role="alert">
                {platformRateError}
              </p>
            )}
          </form>
        </AdminDialog>
      )}

      {/* =====================================================================
          DIALOG 3: CẤU HÌNH THƯỞNG AN TOÀN & SLA (SAFETY REBATE)
          ===================================================================== */}
      {isEditingSafetyRebate && (
        <AdminDialog
          open={isEditingSafetyRebate}
          title="Cấu hình chính sách Thưởng An toàn & SLA"
          description="Quỹ thưởng hoàn tiền mặt trực tiếp vào bảng quyết toán cho các đối tác đạt tiêu chuẩn an toàn hàng hải và dịch vụ xuất sắc."
          onClose={() => !isSavingSafety && setIsEditingSafetyRebate(false)}
          footer={
            <>
              <AdminButton
                variant="secondary"
                onClick={() => setIsEditingSafetyRebate(false)}
                disabled={isSavingSafety}
              >
                Hủy bỏ
              </AdminButton>
              <AdminButton
                variant="primary"
                onClick={handleSaveSafetyRebate}
                loading={isSavingSafety}
                disabled={isSavingSafety}
              >
                Lưu cấu hình
              </AdminButton>
            </>
          }
        >
          <form
            className="admin-dialog-form"
            onSubmit={e => {
              e.preventDefault();
              handleSaveSafetyRebate();
            }}
          >
            <div className="admin-form-switch-row">
              <div className="admin-form-switch-label">
                <span className="admin-form-switch-title">Kích hoạt Quỹ thưởng SLA</span>
                <span className="admin-form-switch-desc">
                  {safetyEnabledDraft
                    ? 'Chính sách đang bật; hệ thống sẽ tự động đánh giá và cộng thưởng khi quyết toán.'
                    : 'Tạm tắt chính sách thưởng tiền mặt trong kỳ đối soát.'}
                </span>
              </div>
              <input
                type="checkbox"
                checked={safetyEnabledDraft}
                onChange={e => setSafetyEnabledDraft(e.target.checked)}
                className="admin-switch-input"
                aria-label="Kích hoạt quỹ thưởng SLA"
              />
            </div>

            <div className="admin-form-group">
              <label htmlFor="safety-rate-input" className="admin-form-label">
                Tỷ lệ hoàn tiền mặt cộng thêm (%) *
              </label>
              <div className="admin-form-input-wrap">
                <input
                  id="safety-rate-input"
                  type="number"
                  step="0.1"
                  min="0"
                  max="10"
                  value={safetyRateDraft}
                  onChange={e => {
                    setSafetyRateDraft(e.target.value);
                    setSafetyError(null);
                  }}
                  className={`admin-form-input ${safetyError ? 'admin-form-input--invalid' : ''}`}
                  placeholder="Mặc định: 2.0"
                  required
                />
                <span className="admin-form-suffix" aria-hidden="true">%</span>
              </div>
              <p className="admin-form-hint">
                Tỷ lệ thưởng tối đa 10.0%. Hệ thống sẽ cộng trực tiếp vào tiền thực nhận của đối tác.
              </p>
            </div>

            <div className="admin-criteria-box">
              <span className="admin-criteria-title">
                <Info size={14} style={{ display: 'inline', verticalAlign: 'text-bottom', marginRight: 4 }} />
                Tiêu chuẩn kiểm duyệt tự động từ máy chủ:
              </span>
              <ul className="admin-criteria-list">
                <li>Trong kỳ đối soát: <strong>0 khiếu nại</strong> do lỗi nhà cung cấp.</li>
                <li><strong>0 lỗi phạt</strong> vi phạm hủy chuyến đơn phương.</li>
                <li>Điểm đánh giá trung bình <strong>&ge; 4.8 sao</strong> (tối thiểu 5 lượt đánh giá).</li>
              </ul>
            </div>

            {safetyError && (
              <p className="admin-form-error" role="alert">
                {safetyError}
              </p>
            )}
          </form>
        </AdminDialog>
      )}

      {/* =====================================================================
          DIALOG 4: THÔNG TIN HOA HỒNG KẾ THỪA MẶC ĐỊNH
          ===================================================================== */}
      {defaultInfoCategory && (
        <AdminDialog
          open={Boolean(defaultInfoCategory)}
          title="Thông tin hoa hồng kế thừa mặc định"
          description={`Danh mục "${defaultInfoCategory.categoryName}" đang áp dụng mức sàn hoa hồng mặc định của sàn.`}
          onClose={() => setDefaultInfoCategory(null)}
          footer={
            <>
              <AdminButton
                variant="secondary"
                onClick={() => setDefaultInfoCategory(null)}
              >
                Đóng
              </AdminButton>
              <AdminButton
                variant="primary"
                onClick={() => {
                  setDefaultInfoCategory(null);
                  handleOpenEditPlatformRate();
                }}
              >
                Chỉnh sửa mức sàn chung
              </AdminButton>
            </>
          }
        >
          <div className="admin-dialog-form">
            <div className="admin-criteria-box">
              <span className="admin-criteria-title">
                <Info size={14} style={{ display: 'inline', verticalAlign: 'text-bottom', marginRight: 4 }} />
                Cơ chế phân tầng biểu phí hoa hồng:
              </span>
              <ul className="admin-criteria-list">
                <li>Tỷ lệ áp dụng hiện tại: <strong>{platformRatePercent.toFixed(1)}%</strong> (kế thừa từ <em>Mức sàn hoa hồng mặc định</em>).</li>
                <li>Danh mục này chưa thiết lập quy tắc hoa hồng riêng trong cơ sở dữ liệu.</li>
                <li>Khi phát sinh đơn hàng, hệ thống quyết toán tự động áp dụng mức sàn chung của nền tảng.</li>
              </ul>
            </div>
          </div>
        </AdminDialog>
      )}

      {/* Toast Notice */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
    </main>
  );
}

export default Settings;
