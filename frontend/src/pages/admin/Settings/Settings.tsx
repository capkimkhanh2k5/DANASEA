import React, { useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Check,
  Copy,
  Pencil,
  PhoneCall,
  RotateCcw,
  Wallet,
  Waves,
  Wind,
  X,
} from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminNotice } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  DEFAULT_SETTINGS,
  validateCommissionRate,
  validateHotline,
} from './settingsModel';
import './Settings.css';

export function Settings() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // Core values state
  const [commissionRate, setCommissionRate] = useState<string>(DEFAULT_SETTINGS.commissionRate);
  const [emergencyHotline, setEmergencyHotline] = useState<string>(DEFAULT_SETTINGS.emergencyHotline);

  // Edit mode state for Commission
  const [isEditingCommission, setIsEditingCommission] = useState(false);
  const [draftCommission, setDraftCommission] = useState(commissionRate);
  const [commissionError, setCommissionError] = useState<string | null>(null);
  const [isSavingCommission, setIsSavingCommission] = useState(false);

  // Edit mode state for Hotline
  const [isEditingHotline, setIsEditingHotline] = useState(false);
  const [draftHotline, setDraftHotline] = useState(emergencyHotline);
  const [hotlineError, setHotlineError] = useState<string | null>(null);
  const [isSavingHotline, setIsSavingHotline] = useState(false);

  // Copy feedback state
  const [hasCopiedHotline, setHasCopiedHotline] = useState(false);

  // UI state
  const [notice, setNotice] = useState<string | null>(null);
  const [isResetDialogOpen, setIsResetDialogOpen] = useState(false);
  const [previewState, setPreviewState] = useState<'normal' | 'loading' | 'error' | 'empty'>('normal');

  // --- Handlers for Commission Rate Edit ---
  const handleStartEditCommission = () => {
    setDraftCommission(commissionRate);
    setCommissionError(null);
    setIsEditingCommission(true);
  };

  const handleCommissionChange = (val: string) => {
    setDraftCommission(val);
    const err = validateCommissionRate(val);
    setCommissionError(err);
  };

  const handleSaveCommission = () => {
    const err = validateCommissionRate(draftCommission);
    if (err) {
      setCommissionError(err);
      return;
    }

    setIsSavingCommission(true);
    setTimeout(() => {
      setCommissionRate(draftCommission.trim());
      setIsEditingCommission(false);
      setIsSavingCommission(false);
      setNotice(`Đã cập nhật hoa hồng mặc định của sàn thành ${draftCommission.trim()}%.`);
    }, 300);
  };

  const handleCancelCommission = () => {
    setDraftCommission(commissionRate);
    setCommissionError(null);
    setIsEditingCommission(false);
  };

  const handleKeyDownCommission = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSaveCommission();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      handleCancelCommission();
    }
  };

  // --- Handlers for Hotline Edit ---
  const handleStartEditHotline = () => {
    setDraftHotline(emergencyHotline);
    setHotlineError(null);
    setIsEditingHotline(true);
  };

  const handleHotlineChange = (val: string) => {
    setDraftHotline(val);
    const err = validateHotline(val);
    setHotlineError(err);
  };

  const handleSaveHotline = () => {
    const err = validateHotline(draftHotline);
    if (err) {
      setHotlineError(err);
      return;
    }

    setIsSavingHotline(true);
    setTimeout(() => {
      setEmergencyHotline(draftHotline.trim());
      setIsEditingHotline(false);
      setIsSavingHotline(false);
      setNotice(`Đã cập nhật số điện thoại liên hệ thành ${draftHotline.trim()}.`);
    }, 300);
  };

  const handleCancelHotline = () => {
    setDraftHotline(emergencyHotline);
    setHotlineError(null);
    setIsEditingHotline(false);
  };

  const handleKeyDownHotline = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSaveHotline();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      handleCancelHotline();
    }
  };

  // --- Handler for Copy Hotline ---
  const handleCopyHotline = async () => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(emergencyHotline);
      } else if (typeof document !== 'undefined') {
        const textarea = document.createElement('textarea');
        textarea.value = emergencyHotline;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
    } catch {
      // Best-effort copy
    }

    setHasCopiedHotline(true);
    setNotice(`Đã sao chép số điện thoại Ban Quản lý Bán đảo Sơn Trà: ${emergencyHotline}`);
    setTimeout(() => setHasCopiedHotline(false), 2000);
  };

  // --- Handler for Reset Defaults ---
  const isAnyModified =
    commissionRate !== DEFAULT_SETTINGS.commissionRate ||
    emergencyHotline !== DEFAULT_SETTINGS.emergencyHotline;

  const handleConfirmReset = () => {
    setCommissionRate(DEFAULT_SETTINGS.commissionRate);
    setEmergencyHotline(DEFAULT_SETTINGS.emergencyHotline);
    setIsEditingCommission(false);
    setIsEditingHotline(false);
    setCommissionError(null);
    setHotlineError(null);
    setIsResetDialogOpen(false);
    setNotice('Đã khôi phục các tham số cấu hình về giá trị mặc định.');
  };

  return (
    <main className="admin-workspace-page admin-settings-page" aria-label="Trang cấu hình hệ thống">
      {/* Settings Toolbar */}
      <div className="admin-settings-toolbar">
        {demo && (
          <div className="admin-settings-preview-ctrl">
            <label htmlFor="settings-preview-select">Trạng thái xem thử:</label>
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
        )}

        {isAnyModified && (
          <AdminButton
            variant="secondary"
            onClick={() => setIsResetDialogOpen(true)}
            aria-label="Khôi phục mặc định"
          >
            <RotateCcw size={14} aria-hidden="true" />
            Khôi phục mặc định
          </AdminButton>
        )}
      </div>

      {/* Preview States Switcher */}
      {previewState === 'loading' ? (
        <AdminState kind="loading" title="Đang tải dữ liệu cấu hình hệ thống…" />
      ) : previewState === 'error' ? (
        <AdminState
          kind="error"
          title="Không thể tải bảng tham số cấu hình"
          description="Đã xảy ra lỗi khi đồng bộ cấu hình từ hệ thống. Vui lòng thử lại sau."
          action={
            <AdminButton variant="secondary" onClick={() => setPreviewState('normal')}>
              Thử tải lại
            </AdminButton>
          }
        />
      ) : previewState === 'empty' ? (
        <AdminState
          kind="empty"
          title="Chưa có tham số cấu hình nào"
          description="Bảng cấu hình hệ thống hiện đang trống hoặc chưa được khởi tạo."
          action={
            <AdminButton variant="primary" onClick={handleConfirmReset}>
              Khởi tạo tham số mặc định
            </AdminButton>
          }
        />
      ) : (
        <div className="admin-settings-container">
          {/* HÀNG 1 — GRID 2 CỘT: TÀI CHÍNH & LIÊN HỆ HỖ TRỢ */}
          <div className="admin-settings-grid-row">
            {/* KHU VỰC A — TÀI CHÍNH */}
            <section
              className={`admin-settings-card ${isEditingCommission ? 'admin-settings-card--editing' : ''}`}
              aria-labelledby="heading-finance-card"
            >
              <div className="admin-card-header-row">
                <div className="admin-card-category" id="heading-finance-card">
                  <span className="admin-card-category-icon" aria-hidden="true">
                    <Wallet size={16} />
                  </span>
                  <span>Tài chính</span>
                </div>

                {!isEditingCommission ? (
                  <button
                    type="button"
                    className="admin-card-action-btn"
                    onClick={handleStartEditCommission}
                    aria-label="Chỉnh sửa hoa hồng mặc định của sàn"
                  >
                    <Pencil size={12} aria-hidden="true" />
                    <span>Chỉnh sửa</span>
                  </button>
                ) : (
                  <span className="admin-card-status-badge">Đang chỉnh sửa</span>
                )}
              </div>

              <div className="admin-card-body">
                <span className="admin-setting-label">Hoa hồng mặc định của sàn</span>

                {!isEditingCommission ? (
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{commissionRate}</span>
                    <span className="admin-setting-value-unit">%</span>
                  </div>
                ) : (
                  <div className="admin-setting-edit-box">
                    <div className="admin-setting-edit-inputs">
                      <div className="admin-setting-input-field-wrap">
                        <input
                          id="commission-rate-input"
                          type="number"
                          step="0.5"
                          min="0"
                          max="100"
                          value={draftCommission}
                          onChange={e => handleCommissionChange(e.target.value)}
                          onKeyDown={handleKeyDownCommission}
                          autoFocus
                          className="admin-setting-edit-input"
                          aria-label="Tỷ lệ hoa hồng mặc định"
                          aria-invalid={Boolean(commissionError)}
                        />
                        <span className="admin-setting-edit-suffix" aria-hidden="true">%</span>
                      </div>

                      <div className="admin-setting-edit-buttons">
                        <button
                          type="button"
                          className="admin-setting-btn-save"
                          onClick={handleSaveCommission}
                          disabled={isSavingCommission || Boolean(commissionError)}
                          aria-label="Lưu tỷ lệ hoa hồng"
                        >
                          <Check size={14} aria-hidden="true" />
                          <span>{isSavingCommission ? 'Đang lưu…' : 'Lưu'}</span>
                        </button>
                        <button
                          type="button"
                          className="admin-setting-btn-cancel"
                          onClick={handleCancelCommission}
                          disabled={isSavingCommission}
                          aria-label="Hủy chỉnh sửa hoa hồng"
                        >
                          <X size={14} aria-hidden="true" />
                          <span>Hủy</span>
                        </button>
                      </div>
                    </div>

                    {commissionError && (
                      <p className="admin-setting-error-msg" role="alert">
                        {commissionError}
                      </p>
                    )}
                  </div>
                )}

                <p className="admin-setting-description">Áp dụng trên giá trị dịch vụ con.</p>
              </div>
            </section>

            {/* KHU VỰC B — LIÊN HỆ HỖ TRỢ */}
            <section
              className={`admin-settings-card ${isEditingHotline ? 'admin-settings-card--editing' : ''}`}
              aria-labelledby="heading-support-card"
            >
              <div className="admin-card-header-row">
                <div className="admin-card-category" id="heading-support-card">
                  <span className="admin-card-category-icon" aria-hidden="true">
                    <PhoneCall size={16} />
                  </span>
                  <span>Liên hệ hỗ trợ</span>
                </div>

                {!isEditingHotline ? (
                  <button
                    type="button"
                    className="admin-card-action-btn"
                    onClick={handleStartEditHotline}
                    aria-label="Chỉnh sửa số điện thoại liên hệ Ban Quản lý Bán đảo Sơn Trà"
                  >
                    <Pencil size={12} aria-hidden="true" />
                    <span>Chỉnh sửa</span>
                  </button>
                ) : (
                  <span className="admin-card-status-badge">Đang chỉnh sửa</span>
                )}
              </div>

              <div className="admin-card-body">
                <span className="admin-setting-label">Ban Quản lý Bán đảo Sơn Trà</span>

                {!isEditingHotline ? (
                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number admin-setting-value-number--phone">
                      {emergencyHotline}
                    </span>
                    <button
                      type="button"
                      className={`admin-mini-copy-btn ${hasCopiedHotline ? 'admin-mini-copy-btn--copied' : ''}`}
                      onClick={handleCopyHotline}
                      title={hasCopiedHotline ? 'Đã sao chép' : 'Sao chép số điện thoại'}
                      aria-label="Sao chép số điện thoại"
                    >
                      {hasCopiedHotline ? <Check size={13} aria-hidden="true" /> : <Copy size={13} aria-hidden="true" />}
                    </button>
                  </div>
                ) : (
                  <div className="admin-setting-edit-box">
                    <div className="admin-setting-edit-inputs">
                      <div className="admin-setting-input-field-wrap">
                        <input
                          id="hotline-input"
                          type="text"
                          value={draftHotline}
                          onChange={e => handleHotlineChange(e.target.value)}
                          onKeyDown={handleKeyDownHotline}
                          autoFocus
                          className="admin-setting-edit-input admin-setting-edit-input--phone"
                          aria-label="Số điện thoại liên hệ"
                          aria-invalid={Boolean(hotlineError)}
                        />
                      </div>

                      <div className="admin-setting-edit-buttons">
                        <button
                          type="button"
                          className="admin-setting-btn-save"
                          onClick={handleSaveHotline}
                          disabled={isSavingHotline || Boolean(hotlineError)}
                          aria-label="Lưu số điện thoại"
                        >
                          <Check size={14} aria-hidden="true" />
                          <span>{isSavingHotline ? 'Đang lưu…' : 'Lưu'}</span>
                        </button>
                        <button
                          type="button"
                          className="admin-setting-btn-cancel"
                          onClick={handleCancelHotline}
                          disabled={isSavingHotline}
                          aria-label="Hủy chỉnh sửa số điện thoại"
                        >
                          <X size={14} aria-hidden="true" />
                          <span>Hủy</span>
                        </button>
                      </div>
                    </div>

                    {hotlineError && (
                      <p className="admin-setting-error-msg" role="alert">
                        {hotlineError}
                      </p>
                    )}
                  </div>
                )}

                <p className="admin-setting-description">Thông tin liên hệ tham khảo.</p>
              </div>
            </section>
          </div>

          {/* KHU VỰC C — NGƯỠNG THAM CHIẾU AN TOÀN BIỂN */}
          <div className="admin-safety-group" role="region" aria-labelledby="heading-safety-group">
            {/* HÀNG 2 — TIÊU ĐỀ NHÓM & DÒNG MÔ TẢ DÙNG CHUNG */}
            <div className="admin-safety-group-header">
              <h2 id="heading-safety-group" className="admin-safety-group-title">
                Ngưỡng tham chiếu an toàn biển
              </h2>
              <p className="admin-safety-group-desc">
                Thông số tham khảo phục vụ vận hành, không phải tiêu chuẩn pháp lý nhà nước.
              </p>
            </div>

            {/* HÀNG 3 — GRID 2 CỘT: CHIỀU CAO SÓNG & TỐC ĐỘ GIÓ GIẬT */}
            <div className="admin-settings-grid-row">
              {/* Card 1 — Chiều cao sóng */}
              <section className="admin-settings-card" aria-labelledby="heading-wave-card">
                <div className="admin-card-header-row">
                  <div className="admin-card-category" id="heading-wave-card">
                    <span className="admin-card-category-icon" aria-hidden="true">
                      <Waves size={16} />
                    </span>
                    <span>Chiều cao sóng</span>
                  </div>
                </div>

                <div className="admin-card-body">
                  <span className="admin-setting-label">Chiều cao sóng</span>

                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{DEFAULT_SETTINGS.maxSafeWave}</span>
                    <span className="admin-setting-value-unit">m</span>
                  </div>

                  <p className="admin-setting-description">Ngưỡng tham chiếu vận hành</p>
                </div>
              </section>

              {/* Card 2 — Tốc độ gió giật */}
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
                  <span className="admin-setting-label">Tốc độ gió giật</span>

                  <div className="admin-setting-value-wrap">
                    <span className="admin-setting-value-number">{DEFAULT_SETTINGS.maxSafeWind}</span>
                    <span className="admin-setting-value-unit">km/h</span>
                  </div>

                  <p className="admin-setting-description">Ngưỡng tham chiếu cảnh báo</p>
                </div>
              </section>
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Dialog for Reset Defaults */}
      <AdminDialog
        open={isResetDialogOpen}
        title="Xác nhận khôi phục cấu hình mặc định"
        description="Thao tác này sẽ đặt lại tỷ lệ hoa hồng (10%) và số điện thoại liên hệ (0236 3920 477) về giá trị mặc định ban đầu."
        onClose={() => setIsResetDialogOpen(false)}
        footer={
          <>
            <AdminButton variant="secondary" onClick={() => setIsResetDialogOpen(false)}>
              Hủy bỏ
            </AdminButton>
            <AdminButton variant="danger" onClick={handleConfirmReset}>
              Xác nhận khôi phục
            </AdminButton>
          </>
        }
      >
        <p style={{ fontSize: 13, color: 'var(--admin-secondary, #525252)', margin: 0 }}>
          Các tham số sẽ được khôi phục về giá trị mặc định:
        </p>
        <ul style={{ margin: '8px 0 0 18px', padding: 0, fontSize: 13, color: 'var(--admin-secondary, #525252)' }}>
          <li>Hoa hồng mặc định của sàn: <strong>10%</strong></li>
          <li>Số điện thoại liên hệ BQL: <strong>0236 3920 477</strong></li>
        </ul>
      </AdminDialog>

      {/* Toast Notification */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
    </main>
  );
}

export default Settings;
