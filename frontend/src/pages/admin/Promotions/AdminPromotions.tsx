import React, { useEffect, useState, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Plus,
  Search,
  Tag,
  Percent,
  Banknote,
  Calendar,
  Ticket,
  CheckCircle2,
  PauseCircle,
  AlertCircle,
  Edit2,
  Play,
  Pause,
} from 'lucide-react';
import { MOCK_ADMIN_PROMOTIONS } from '../../../data/adminMockData';
import { adminDiscountCodeService, getAdminDiscountError } from '../../../services/adminDiscountCodeService';
import type { DiscountCode, DiscountType } from '../../../types';
import {
  AdminBadge,
  AdminState,
} from '../../../components/admin/AdminUI';
import {
  AdminPreviewBanner,
  AdminFilterTabs,
  AdminButton,
  AdminNotice,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  filterPromotions,
  validatePromotionForm,
  savePromotion,
  togglePromotionActive,
  isPromotionExpired,
  isPromotionDepleted,
  getVietnamTodayString,
  getLastDayOfMonth,
  computeNextValidToOnStartChange,
  type PromotionStatusFilter,
  type PromotionTypeFilter,
  type PromotionFormData,
} from './promotionsModel';
import './AdminPromotions.css';

export function AdminPromotions() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // Data & Selection State
  const [promotions, setPromotions] = useState<DiscountCode[]>(() => demo ? MOCK_ADMIN_PROMOTIONS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<PromotionStatusFilter>('all');
  const [typeFilter, setTypeFilter] = useState<PromotionTypeFilter>('all');

  // Drawer & Modal State
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [editingPromo, setEditingPromo] = useState<DiscountCode | null>(null);
  const [pendingTogglePromo, setPendingTogglePromo] = useState<DiscountCode | null>(null);

  // Form State
  const [formData, setFormData] = useState<PromotionFormData>({
    code: '',
    discountType: 'FIXED',
    discountValue: 50000,
    maxUses: 500,
    validFrom: new Date().toISOString().slice(0, 10),
    validTo: '2026-12-31',
    isActive: true,
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminDiscountCodeService.getDiscountCodes({ page: 0, size: 50 })
      .then(res => {
        if (!isMounted) return;
        const mapped: DiscountCode[] = (res.content || []).map(d => ({
          id: d.id,
          code: d.code,
          scope: 'GLOBAL',
          vendorId: d.vendorId || '',
          discountType: d.discountType,
          discountValue: d.discountValue,
          maxUses: d.maxUses || 0,
          usedCount: d.usedCount || 0,
          validFrom: d.validFrom || '',
          validTo: d.validTo || '',
          isActive: d.isActive,
          createdAt: d.createdAt,
          updatedAt: d.updatedAt,
        }));
        setPromotions(mapped);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const info = getAdminDiscountError(err);
        setErrorMessage(info.message);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  const showNotice = (text: string) => {
    setNotice(text);
  };

  // Metrics Calculation
  const metrics = useMemo(() => {
    const total = promotions.length;
    let active = 0;
    let inactive = 0;
    let expired = 0;

    promotions.forEach(p => {
      const isExp = isPromotionExpired(p) || isPromotionDepleted(p);
      if (isExp) {
        expired++;
      } else if (p.isActive) {
        active++;
      } else {
        inactive++;
      }
    });

    return { total, active, inactive, expired };
  }, [promotions]);

  // Filtered List
  const filteredPromotions = useMemo(() => {
    return filterPromotions(promotions, searchQuery, statusFilter, typeFilter);
  }, [promotions, searchQuery, statusFilter, typeFilter]);

  // Drawer Handlers
  const handleOpenCreateDrawer = () => {
    const today = getVietnamTodayString();
    const endOfMonth = getLastDayOfMonth(today);
    setEditingPromo(null);
    setFormData({
      code: '',
      discountType: 'FIXED',
      discountValue: 50000,
      maxUses: 500,
      validFrom: today,
      validTo: endOfMonth,
      isActive: true,
    });
    setFormError(null);
    setIsDrawerOpen(true);
  };

  const handleValidFromChange = (newVal: string) => {
    setFormData(prev => ({
      ...prev,
      validFrom: newVal,
      validTo: computeNextValidToOnStartChange(newVal, prev.validTo),
    }));
  };

  const handleOpenEditDrawer = (promo: DiscountCode) => {
    setEditingPromo(promo);
    setFormData({
      code: promo.code,
      discountType: promo.discountType,
      discountValue: promo.discountValue,
      maxUses: promo.maxUses,
      validFrom: promo.validFrom?.slice(0, 10) || '',
      validTo: promo.validTo?.slice(0, 10) || '',
      isActive: promo.isActive,
    });
    setFormError(null);
    setIsDrawerOpen(true);
  };

  const handleTypeChange = (newType: DiscountType) => {
    if (newType === formData.discountType) return;
    let newValue = formData.discountValue;
    if (newType === 'PERCENTAGE') {
      if (newValue > 100) newValue = 15;
    } else {
      if (newValue <= 100) newValue = 50000;
    }
    setFormData(prev => ({
      ...prev,
      discountType: newType,
      discountValue: newValue,
    }));
  };

  const handleSavePromotion = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const validation = validatePromotionForm(formData);
    if (!validation.valid) {
      setFormError(validation.error || 'Dữ liệu không hợp lệ');
      return;
    }

    if (demo) {
      const { updatedList, savedItem } = savePromotion(
        promotions,
        formData,
        editingPromo ? editingPromo.id : null,
        demo
      );

      setPromotions(updatedList);
      setIsDrawerOpen(false);
      showNotice(
        editingPromo
          ? `Đã cập nhật mã ưu đãi ${savedItem?.code}`
          : `Đã tạo thành công mã ưu đãi toàn sàn ${savedItem?.code}`
      );
      return;
    }

    try {
      if (editingPromo) {
        const updated = await adminDiscountCodeService.updateDiscountCode(editingPromo.id, {
          discountValue: formData.discountValue,
          maxUses: formData.maxUses,
          validFrom: `${formData.validFrom}T00:00:00Z`,
          validTo: `${formData.validTo}T23:59:59Z`,
          isActive: formData.isActive,
        });
        setPromotions(prev => prev.map(p => p.id === updated.id ? {
          ...p,
          discountValue: updated.discountValue,
          maxUses: updated.maxUses || 0,
          validFrom: updated.validFrom || '',
          validTo: updated.validTo || '',
          isActive: updated.isActive,
        } : p));
        setIsDrawerOpen(false);
        showNotice(`Đã cập nhật mã ưu đãi ${updated.code}`);
      } else {
        const created = await adminDiscountCodeService.createDiscountCode({
          code: formData.code.toUpperCase().trim(),
          discountType: formData.discountType,
          discountValue: formData.discountValue,
          maxUses: formData.maxUses,
          validFrom: `${formData.validFrom}T00:00:00Z`,
          validTo: `${formData.validTo}T23:59:59Z`,
          scope: 'PLATFORM',
        });
        const newPromo: DiscountCode = {
          id: created.id,
          code: created.code,
          scope: 'GLOBAL',
          vendorId: created.vendorId || '',
          discountType: created.discountType,
          discountValue: created.discountValue,
          maxUses: created.maxUses || 0,
          usedCount: created.usedCount || 0,
          validFrom: created.validFrom || '',
          validTo: created.validTo || '',
          isActive: created.isActive,
          createdAt: created.createdAt,
          updatedAt: created.updatedAt,
        };
        setPromotions(prev => [newPromo, ...prev]);
        setIsDrawerOpen(false);
        showNotice(`Đã tạo thành công mã ưu đãi toàn sàn ${created.code}`);
      }
    } catch (err) {
      const info = getAdminDiscountError(err, true);
      setFormError(info.message);
    }
  };

  // Toggle Status
  const handleRequestToggle = (promo: DiscountCode) => {
    setPendingTogglePromo(promo);
  };

  const handleConfirmToggle = async () => {
    if (!pendingTogglePromo) return;

    if (demo) {
      const { updatedList, targetPromo, nextState } = togglePromotionActive(
        promotions,
        pendingTogglePromo.id,
        demo
      );

      setPromotions(updatedList);
      setPendingTogglePromo(null);
      showNotice(
        `Đã ${nextState ? 'kích hoạt' : 'tạm dừng'} mã ưu đãi ${targetPromo?.code}`
      );
      return;
    }

    try {
      const nextActive = !pendingTogglePromo.isActive;
      await adminDiscountCodeService.updateDiscountCode(pendingTogglePromo.id, {
        isActive: nextActive,
      });
      setPromotions(prev => prev.map(p => p.id === pendingTogglePromo.id ? { ...p, isActive: nextActive } : p));
      setPendingTogglePromo(null);
      showNotice(`Đã ${nextActive ? 'kích hoạt' : 'tạm dừng'} mã ưu đãi ${pendingTogglePromo.code}`);
    } catch (err) {
      const info = getAdminDiscountError(err, true);
      showNotice(`Lỗi: ${info.message}`);
      setPendingTogglePromo(null);
    }
  };

  return (
    <div className="admin-promotions">
      {/* Preview Banner */}
      <AdminPreviewBanner demo={demo} href={`${location.pathname}?demo=1`} />

      {/* KPI Cards */}
      <div className="admin-promotions__metrics" aria-label="Tổng quan khuyến mãi">
        <button
          type="button"
          className="admin-promotions__metric-card"
          onClick={() => setStatusFilter('all')}
          style={{ textAlign: 'left', cursor: 'pointer' }}
        >
          <div className="admin-promotions__metric-header">
            <span className="admin-promotions__metric-label">Tổng chương trình</span>
            <span className="admin-promotions__metric-icon admin-promotions__metric-icon--primary">
              <Ticket size={18} />
            </span>
          </div>
          <div className="admin-promotions__metric-value">{metrics.total}</div>
          <div className="admin-promotions__metric-desc">Mã voucher trong hệ thống</div>
        </button>

        <button
          type="button"
          className="admin-promotions__metric-card"
          onClick={() => setStatusFilter('active')}
          style={{ textAlign: 'left', cursor: 'pointer' }}
        >
          <div className="admin-promotions__metric-header">
            <span className="admin-promotions__metric-label">Đang kích hoạt</span>
            <span className="admin-promotions__metric-icon admin-promotions__metric-icon--info">
              <CheckCircle2 size={18} />
            </span>
          </div>
          <div className="admin-promotions__metric-value">{metrics.active}</div>
          <div className="admin-promotions__metric-desc">Sẵn sàng áp dụng cho giỏ hàng</div>
        </button>

        <button
          type="button"
          className="admin-promotions__metric-card"
          onClick={() => setStatusFilter('inactive')}
          style={{ textAlign: 'left', cursor: 'pointer' }}
        >
          <div className="admin-promotions__metric-header">
            <span className="admin-promotions__metric-label">Tạm dừng</span>
            <span className="admin-promotions__metric-icon admin-promotions__metric-icon--warning">
              <PauseCircle size={18} />
            </span>
          </div>
          <div className="admin-promotions__metric-value">{metrics.inactive}</div>
          <div className="admin-promotions__metric-desc">Tạm ngưng nhận áp dụng</div>
        </button>

        <button
          type="button"
          className="admin-promotions__metric-card"
          onClick={() => setStatusFilter('expired')}
          style={{ textAlign: 'left', cursor: 'pointer' }}
        >
          <div className="admin-promotions__metric-header">
            <span className="admin-promotions__metric-label">Hết hạn / Hết lượt</span>
            <span className="admin-promotions__metric-icon admin-promotions__metric-icon--danger">
              <AlertCircle size={18} />
            </span>
          </div>
          <div className="admin-promotions__metric-value">{metrics.expired}</div>
          <div className="admin-promotions__metric-desc">Đã quá ngày hoặc hết số suất</div>
        </button>
      </div>

      {/* Filter Tabs */}
      <AdminFilterTabs
        label="Lọc trạng thái mã khuyến mãi"
        value={statusFilter}
        onChange={(val) => setStatusFilter(val as PromotionStatusFilter)}
        items={[
          { value: 'all', label: 'Tất cả chương trình', count: metrics.total },
          { value: 'active', label: 'Đang áp dụng', count: metrics.active },
          { value: 'inactive', label: 'Tạm dừng', count: metrics.inactive },
          { value: 'expired', label: 'Đã kết thúc', count: metrics.expired },
        ]}
      />

      {/* Controls Toolbar */}
      <div className="admin-promotions__controls">
        <div className="admin-promotions__search-box">
          <Search size={18} className="admin-promotions__search-icon" aria-hidden="true" />
          <input
            type="text"
            className="admin-promotions__search-input"
            placeholder="Tìm theo mã voucher (VD: DANASEA50)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            aria-label="Tìm theo mã voucher"
          />
        </div>

        <div className="admin-promotions__filters">
          <select
            className="admin-promotions__select"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as PromotionTypeFilter)}
            aria-label="Lọc theo loại hình chiết khấu"
          >
            <option value="all">Tất cả loại ưu đãi</option>
            <option value="FIXED">Giảm tiền mặt (VNĐ)</option>
            <option value="PERCENTAGE">Giảm theo tỷ lệ (%)</option>
          </select>
          <AdminButton
            variant="primary"
            onClick={handleOpenCreateDrawer}
          >
            <Plus size={16} aria-hidden="true" />
            Tạo mã ưu đãi mới
          </AdminButton>
        </div>
      </div>

      {/* Main Promotions Table */}
      <div key={statusFilter} className="admin-promotions__card admin-tab-pane">
        {loading ? (
          <div style={{ padding: '40px 20px' }}>
            <AdminState
              kind="empty"
              title="Đang tải danh sách khuyến mãi..."
              description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
            />
          </div>
        ) : errorMessage ? (
          <div style={{ padding: '40px 20px' }}>
            <AdminState
              kind="error"
              title="Không thể tải khuyến mãi"
              description={errorMessage}
            />
          </div>
        ) : filteredPromotions.length === 0 ? (
          <div style={{ padding: '40px 20px' }}>
            <AdminState
              kind="empty"
              title="Không tìm thấy mã khuyến mãi nào"
              description={promotions.length === 0 ? "Hiện tại chưa có mã ưu đãi nào được kích hoạt trên sàn DANASEA." : "Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc trạng thái để xem các chương trình khác."}
              action={
                <AdminButton
                  onClick={() => {
                    setSearchQuery('');
                    setStatusFilter('all');
                    setTypeFilter('all');
                  }}
                >
                  Xóa bộ lọc
                </AdminButton>
              }
            />
          </div>
        ) : (
          <div className="admin-promotions__table-container">
            <table className="admin-promotions__table">
              <thead>
                <tr>
                  <th>Mã ưu đãi & Phạm vi</th>
                  <th>Mức giảm</th>
                  <th>Tiến độ sử dụng</th>
                  <th>Thời hạn áp dụng</th>
                  <th>Trạng thái</th>
                  <th style={{ textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredPromotions.map((promo) => {
                  const isExpired = isPromotionExpired(promo);
                  const isDepleted = isPromotionDepleted(promo);
                  const isConcluded = isExpired || isDepleted;
                  const progressPct = Math.min(
                    100,
                    Math.round((promo.usedCount / (promo.maxUses || 1)) * 100)
                  );

                  return (
                    <tr key={promo.id} className="admin-promotions__row">
                      {/* Code & Scope */}
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                          <span className="admin-promotions__code-chip">
                            <Tag size={14} aria-hidden="true" />
                            {promo.code}
                          </span>
                          <span className="admin-promotions__scope-tag">
                            {promo.scope === 'GLOBAL' ? 'Toàn bộ dịch vụ biển' : 'Theo đối tác'}
                          </span>
                        </div>
                      </td>

                      {/* Discount Amount */}
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span className="admin-promotions__discount-val">
                            {promo.discountType === 'FIXED'
                              ? `${promo.discountValue.toLocaleString('vi-VN')} đ`
                              : `${promo.discountValue}%`}
                          </span>
                          <span className="admin-promotions__discount-type">
                            {promo.discountType === 'FIXED' ? 'Khấu trừ trực tiếp' : 'Chiết khấu đơn hàng'}
                          </span>
                        </div>
                      </td>

                      {/* Usage Progress */}
                      <td>
                        <div className="admin-promotions__usage">
                          <div className="admin-promotions__usage-text">
                            <span>
                              {promo.usedCount} / {promo.maxUses} lượt
                            </span>
                            <span>{progressPct}%</span>
                          </div>
                          <div className="admin-promotions__progress-bar">
                            <div
                              className={`admin-promotions__progress-fill ${
                                isDepleted ? 'admin-promotions__progress-fill--depleted' : ''
                              }`}
                              style={{ width: `${progressPct}%` }}
                            />
                          </div>
                        </div>
                      </td>

                      {/* Date Range */}
                      <td>
                        <div className="admin-promotions__date-range">
                          <Calendar size={15} style={{ color: 'var(--admin-muted, #737373)' }} aria-hidden="true" />
                          <span className="admin-promotions__date">
                            {promo.validFrom?.slice(0, 10)} → {promo.validTo?.slice(0, 10)}
                          </span>
                        </div>
                      </td>

                      {/* Status Badge */}
                      <td>
                        {isConcluded ? (
                          <AdminBadge tone="neutral">
                            {isDepleted ? 'HẾT LƯỢT' : 'HẾT HẠN'}
                          </AdminBadge>
                        ) : promo.isActive ? (
                          <AdminBadge tone="teal">ĐANG ÁP DỤNG</AdminBadge>
                        ) : (
                          <AdminBadge tone="amber">TẠM DỪNG</AdminBadge>
                        )}
                      </td>

                      {/* Actions */}
                      <td>
                        <div className="admin-promotions__actions">
                          <AdminButton
                            onClick={() => handleOpenEditDrawer(promo)}
                          >
                            <Edit2 size={14} aria-hidden="true" />
                            Sửa
                          </AdminButton>
                          <AdminButton
                            onClick={() => handleRequestToggle(promo)}
                          >
                            {promo.isActive ? (
                              <>
                                <Pause size={14} aria-hidden="true" />
                                Tạm tắt
                              </>
                            ) : (
                              <>
                                <Play size={14} aria-hidden="true" />
                                Bật lại
                              </>
                            )}
                          </AdminButton>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Floating Notice */}
      {!isDrawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}

      {/* Create / Edit Drawer */}
      <AdminDialog
        open={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        variant="drawer"
        title={editingPromo ? `Chỉnh sửa mã ưu đãi: ${editingPromo.code}` : 'Tạo mới Mã khuyến mãi Toàn sàn'}
        description="Cấu hình chiết khấu, hạn mức và thời gian triển khai áp dụng cho khách hàng."
        footer={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setIsDrawerOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" onClick={() => handleSavePromotion()}>
              {editingPromo ? 'Lưu cấu hình mã' : 'Kích hoạt mã ưu đãi'}
            </AdminButton>
          </div>
        }
      >
        <form onSubmit={handleSavePromotion} className="admin-promotions__form">
          {formError && (
            <div style={{ padding: '10px 14px', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 8, color: '#991b1b', fontSize: 13, fontWeight: 500 }}>
              {formError}
            </div>
          )}

          {/* Promotion Code */}
          <div className="admin-promotions__form-group">
            <label className="admin-promotions__form-label">
              MÃ ƯU ĐÃI (PROMO CODE) *
              <span className="admin-promotions__form-hint">Chữ hoa & số, không dấu cách</span>
            </label>
            <input
              type="text"
              className="admin-promotions__input admin-promotions__input--mono"
              placeholder="VD: DANASEA50, BIENXANH15"
              value={formData.code}
              onChange={(e) => setFormData(prev => ({ ...prev, code: e.target.value.toUpperCase() }))}
              required
            />
          </div>

          {/* Discount Type */}
          <div className="admin-promotions__form-group">
            <label className="admin-promotions__form-label">LOẠI HÌNH CHIẾT KHẤU *</label>
            <div className="admin-promotions__type-buttons">
              <button
                type="button"
                className={`admin-promotions__type-btn ${
                  formData.discountType === 'FIXED' ? 'admin-promotions__type-btn--active' : ''
                }`}
                onClick={() => handleTypeChange('FIXED')}
              >
                <Banknote size={16} />
                Cố định (VNĐ)
              </button>
              <button
                type="button"
                className={`admin-promotions__type-btn ${
                  formData.discountType === 'PERCENTAGE' ? 'admin-promotions__type-btn--active' : ''
                }`}
                onClick={() => handleTypeChange('PERCENTAGE')}
              >
                <Percent size={16} />
                Phần trăm (%)
              </button>
            </div>
          </div>

          {/* Discount Value with Quick Presets */}
          <div className="admin-promotions__form-group">
            <label className="admin-promotions__form-label">
              MỨC GIẢM ({formData.discountType === 'FIXED' ? 'VNĐ' : '%'}) *
              <span className="admin-promotions__form-hint">
                {formData.discountType === 'PERCENTAGE' ? 'Khoảng từ 1% - 100%' : 'Tối thiểu 1.000 VNĐ'}
              </span>
            </label>
            <input
              type="number"
              className="admin-promotions__input"
              value={formData.discountValue}
              onChange={(e) => {
                let val = Number(e.target.value);
                if (formData.discountType === 'PERCENTAGE' && val > 100) val = 100;
                setFormData(prev => ({ ...prev, discountValue: val }));
              }}
              min={1}
              max={formData.discountType === 'PERCENTAGE' ? 100 : undefined}
              step={formData.discountType === 'PERCENTAGE' ? 1 : 1000}
              required
            />

            {/* Quick Presets */}
            <div className="admin-promotions__presets">
              <span style={{ fontSize: 11, color: 'var(--admin-muted, #737373)', fontWeight: 600 }}>Chọn nhanh:</span>
              {formData.discountType === 'PERCENTAGE'
                ? [5, 10, 15, 20, 30, 50].map((pct) => (
                    <button
                      key={pct}
                      type="button"
                      className={`admin-promotions__preset-btn ${
                        formData.discountValue === pct ? 'admin-promotions__preset-btn--active' : ''
                      }`}
                      onClick={() => setFormData(prev => ({ ...prev, discountValue: pct }))}
                    >
                      {pct}%
                    </button>
                  ))
                : [20000, 50000, 100000, 200000, 500000].map((amt) => (
                    <button
                      key={amt}
                      type="button"
                      className={`admin-promotions__preset-btn ${
                        formData.discountValue === amt ? 'admin-promotions__preset-btn--active' : ''
                      }`}
                      onClick={() => setFormData(prev => ({ ...prev, discountValue: amt }))}
                    >
                      {(amt / 1000).toLocaleString('vi-VN')}k
                    </button>
                  ))}
            </div>
          </div>

          {/* Max Uses */}
          <div className="admin-promotions__form-group">
            <label className="admin-promotions__form-label">
              GIỚI HẠN SỐ LƯỢT ÁP DỤNG *
              <span className="admin-promotions__form-hint">Tối thiểu 1 lượt</span>
            </label>
            <input
              type="number"
              className="admin-promotions__input"
              value={formData.maxUses}
              onChange={(e) => setFormData(prev => ({ ...prev, maxUses: Number(e.target.value) }))}
              min={1}
              required
            />
          </div>

          {/* Date Range */}
          <div className="admin-promotions__form-group">
            <label className="admin-promotions__form-label">THỜI GIAN HIỆU LỰC</label>
            <div className="admin-promotions__date-grid">
              <div>
                <span style={{ fontSize: 11, color: 'var(--admin-muted, #737373)', display: 'block', marginBottom: 4 }}>
                  Từ ngày
                </span>
                <input
                  type="date"
                  className="admin-promotions__input"
                  value={formData.validFrom}
                  onChange={(e) => handleValidFromChange(e.target.value)}
                  required
                />
              </div>
              <div>
                <span style={{ fontSize: 11, color: 'var(--admin-muted, #737373)', display: 'block', marginBottom: 4 }}>
                  Đến ngày (tối thiểu {formData.validFrom ? getLastDayOfMonth(formData.validFrom) : 'cuối tháng'})
                </span>
                <input
                  type="date"
                  className="admin-promotions__input"
                  value={formData.validTo}
                  min={formData.validFrom ? getLastDayOfMonth(formData.validFrom) : undefined}
                  onChange={(e) => setFormData(prev => ({ ...prev, validTo: e.target.value }))}
                  required
                />
              </div>
            </div>
          </div>

          {/* Toggle Active */}
          <div className="admin-promotions__toggle-card">
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--admin-ink, #171717)' }}>
                Kích hoạt ngay chương trình
              </div>
              <div style={{ fontSize: 12, color: 'var(--admin-muted, #737373)' }}>
                Cho phép khách hàng nhập và sử dụng mã ngay tại giỏ hàng
              </div>
            </div>
            <input
              type="checkbox"
              className="admin-promotions__checkbox"
              checked={formData.isActive}
              onChange={(e) => setFormData(prev => ({ ...prev, isActive: e.target.checked }))}
            />
          </div>
        </form>
      </AdminDialog>

      {/* Toggle Status Confirmation Modal */}
      <AdminDialog
        open={Boolean(pendingTogglePromo)}
        onClose={() => setPendingTogglePromo(null)}
        variant="modal"
        title={pendingTogglePromo?.isActive ? 'Tạm dừng mã khuyến mãi?' : 'Kích hoạt lại mã khuyến mãi?'}
        footer={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setPendingTogglePromo(null)}>Hủy bỏ</AdminButton>
            <AdminButton
              variant={pendingTogglePromo?.isActive ? 'danger' : 'primary'}
              onClick={handleConfirmToggle}
            >
              {pendingTogglePromo?.isActive ? 'Xác nhận tạm dừng' : 'Kích hoạt lại'}
            </AdminButton>
          </div>
        }
      >
        <div style={{ fontSize: 14, color: 'var(--admin-body, #262626)', lineHeight: 1.6, padding: '8px 0' }}>
          {pendingTogglePromo?.isActive ? (
            <>
              Bạn có chắc chắn muốn tạm dừng mã ưu đãi <strong>{pendingTogglePromo?.code}</strong>? Sau khi tạm dừng, khách hàng sẽ không thể áp dụng mã này khi đặt dịch vụ.
            </>
          ) : (
            <>
              Bạn có muốn kích hoạt lại mã ưu đãi <strong>{pendingTogglePromo?.code}</strong>? Khách hàng sẽ có thể tiếp tục áp dụng mã này nếu vẫn còn trong thời hạn hiệu lực.
            </>
          )}
        </div>
      </AdminDialog>
    </div>
  );
}

export default AdminPromotions;
