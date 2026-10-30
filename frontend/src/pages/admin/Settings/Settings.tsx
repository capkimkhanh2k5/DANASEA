import React, { useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  AlertTriangle,
  Clock,
  RotateCcw,
  Save,
  ShieldAlert,
  ShieldCheck,
  Wallet,
  Waves,
} from 'lucide-react';
import { MOCK_ADMIN_CONFIGS } from '../../../data/adminMockData';
import type { SystemConfig } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import {
  AdminButton,
  AdminNotice,
  AdminPreviewBanner,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  CONFIG_DEFINITIONS,
  DEFAULT_CONFIG_VALUES,
  extractFormDataFromConfigs,
  formatDateTimeVN,
  getChangedKeys,
  hasFormChanges,
  validateSettingsForm,
  type ConfigDefinition,
  type SettingsErrors,
  type SettingsFormData,
} from './settingsModel';
import './Settings.css';

export function Settings() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  // State management
  const [configs, setConfigs] = useState<SystemConfig[]>(MOCK_ADMIN_CONFIGS);
  const activeFormData = useMemo(() => extractFormDataFromConfigs(configs), [configs]);
  const [formData, setFormData] = useState<SettingsFormData>(activeFormData);
  const [errors, setErrors] = useState<SettingsErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [isResetDialogOpen, setIsResetDialogOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [previewState, setPreviewState] = useState<'normal' | 'loading' | 'error' | 'empty'>('normal');

  // Change detection
  const isDirty = useMemo(() => hasFormChanges(formData, activeFormData), [formData, activeFormData]);
  const changedKeys = useMemo(() => getChangedKeys(formData, activeFormData), [formData, activeFormData]);

  // Handle input change
  const handleChange = (key: keyof SettingsFormData, value: string) => {
    const updated = { ...formData, [key]: value };
    setFormData(updated);

    // Validate field on change
    const validation = validateSettingsForm(updated);
    setErrors(prev => ({ ...prev, [key]: validation[key] }));
  };

  // Save changes
  const handleSave = () => {
    const validation = validateSettingsForm(formData);
    setErrors(validation);

    if (Object.keys(validation).length > 0) {
      setNotice('Vui lòng kiểm tra và sửa các giá trị chưa hợp lệ.');
      return;
    }

    setIsSaving(true);
    setTimeout(() => {
      const now = new Date().toISOString();
      setConfigs(prev =>
        prev.map(c => {
          if (c.key === 'PLATFORM_COMMISSION_RATE') return { ...c, value: formData.commissionRate, updatedBy: 'Vũ Hải Đăng', updatedAt: now };
          if (c.key === 'AUTO_PAYOUT_CYCLE_DAYS') return { ...c, value: formData.payoutCycle, updatedBy: 'Vũ Hải Đăng', updatedAt: now };
          if (c.key === 'WEATHER_MAX_SAFE_WAVE_M') return { ...c, value: formData.maxSafeWave, updatedBy: 'Vũ Hải Đăng', updatedAt: now };
          if (c.key === 'WEATHER_MAX_SAFE_WIND_KMH') return { ...c, value: formData.maxSafeWind, updatedBy: 'Vũ Hải Đăng', updatedAt: now };
          if (c.key === 'EMERGENCY_PORT_HOTLINE') return { ...c, value: formData.emergencyHotline, updatedBy: 'Vũ Hải Đăng', updatedAt: now };
          return c;
        })
      );
      setIsSaving(false);
      setNotice('Đã lưu thành công các tham số cấu hình hệ thống.');
    }, 400);
  };

  // Revert unsaved edits back to active values
  const handleDiscardChanges = () => {
    setFormData(activeFormData);
    setErrors({});
    setNotice('Đã hủy các thay đổi chưa lưu.');
  };

  // Reset all to system defaults
  const handleConfirmReset = () => {
    setFormData(DEFAULT_CONFIG_VALUES);
    const now = new Date().toISOString();
    setConfigs(prev =>
      prev.map(c => {
        if (c.key === 'PLATFORM_COMMISSION_RATE') return { ...c, value: DEFAULT_CONFIG_VALUES.commissionRate, updatedBy: 'Hệ thống (Mặc định)', updatedAt: now };
        if (c.key === 'AUTO_PAYOUT_CYCLE_DAYS') return { ...c, value: DEFAULT_CONFIG_VALUES.payoutCycle, updatedBy: 'Hệ thống (Mặc định)', updatedAt: now };
        if (c.key === 'WEATHER_MAX_SAFE_WAVE_M') return { ...c, value: DEFAULT_CONFIG_VALUES.maxSafeWave, updatedBy: 'Hệ thống (Mặc định)', updatedAt: now };
        if (c.key === 'WEATHER_MAX_SAFE_WIND_KMH') return { ...c, value: DEFAULT_CONFIG_VALUES.maxSafeWind, updatedBy: 'Hệ thống (Mặc định)', updatedAt: now };
        if (c.key === 'EMERGENCY_PORT_HOTLINE') return { ...c, value: DEFAULT_CONFIG_VALUES.emergencyHotline, updatedBy: 'Hệ thống (Mặc định)', updatedAt: now };
        return c;
      })
    );
    setErrors({});
    setIsResetDialogOpen(false);
    setNotice('Đã khôi phục các tham số cấu hình về giá trị mặc định.');
  };

  // Group definitions for direct display (no tabs)
  const operationDefs = useMemo(() => CONFIG_DEFINITIONS.filter(d => d.group === 'operations'), []);
  const maritimeDefs = useMemo(() => CONFIG_DEFINITIONS.filter(d => d.group === 'maritime_safety'), []);

  return (
    <main className="admin-workspace-page" aria-label="Trang cấu hình hệ thống">
      {/* Settings Actions Toolbar */}
      <div className="admin-settings-actions" style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, marginTop: '2px', marginBottom: '8px' }}>
        <AdminButton
          variant="secondary"
          onClick={() => setIsResetDialogOpen(true)}
          aria-label="Khôi phục mặc định toàn bộ cấu hình"
        >
          <RotateCcw size={15} aria-hidden="true" />
          Khôi phục mặc định
        </AdminButton>
        {isDirty && (
          <AdminButton
            variant="primary"
            onClick={handleSave}
            loading={isSaving}
            aria-label="Lưu các tham số cấu hình đã sửa đổi"
          >
            <Save size={15} aria-hidden="true" />
            Lưu cài đặt ({changedKeys.length})
          </AdminButton>
        )}
      </div>

      {/* Demo Banner */}
      <AdminPreviewBanner demo={demo} href="/admin/settings?demo=1" />

      {/* Demo State Controller (Dev only) */}
      {demo && (
        <div className="admin-demo-controls-bar" style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, fontSize: 12 }}>
          <label htmlFor="settings-preview-select" style={{ color: 'var(--admin-muted)', fontWeight: 500 }}>
            Mô phỏng trạng thái trang:
          </label>
          <select
            id="settings-preview-select"
            aria-label="Mô phỏng trạng thái trang"
            value={previewState}
            onChange={e => setPreviewState(e.target.value as 'normal' | 'loading' | 'error' | 'empty')}
            className="admin-field"
            style={{ minHeight: 32, width: 'auto', padding: '4px 10px', fontSize: 12 }}
          >
            <option value="normal">Bình thường (4 tham số)</option>
            <option value="loading">Đang tải cấu hình</option>
            <option value="error">Lỗi kết nối tham số</option>
            <option value="empty">Chưa có cấu hình</option>
          </select>
        </div>
      )}

      {/* State Switcher */}
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
          {/* Telemetry & Audit Integrity Card */}
          <section className="admin-settings-telemetry" aria-label="Thông tin kiểm soát hệ thống">
            <div className="admin-telemetry-left">
              <span className="admin-telemetry-icon" aria-hidden="true">
                <ShieldCheck size={20} />
              </span>
              <div className="admin-telemetry-copy">
                <strong>
                  Kiểm soát bất biến &amp; Nhật ký kiểm toán
                  <span className="admin-config-key">audit_logs</span>
                </strong>
                <p>
                  Mọi thay đổi tham số được ghi vết tự động kèm định danh quản trị viên. Không hồi tố tỷ lệ hoa hồng của các đơn hàng và chu kỳ đã quyết toán xong.
                </p>
              </div>
            </div>

            <div className="admin-telemetry-metrics" aria-label="Chỉ số bộ đệm thời tiết tham chiếu">
              <span style={{ color: 'var(--admin-muted)', fontWeight: 600 }}>Bộ đệm thời tiết:</span>
              <span>Sóng: <strong>0.5m</strong></span>
              <span>·</span>
              <span>Gió: <strong>12 km/h</strong></span>
              <span>·</span>
              <span>Mưa: <strong>0 mm</strong></span>
            </div>
          </section>

          {/* Group 1: Vận hành & Giao dịch Sàn */}
          {operationDefs.length > 0 && (
            <section className="admin-config-group" aria-labelledby="heading-operations-group">
              <div className="admin-config-group-header">
                <div>
                  <h2 id="heading-operations-group">
                    <Wallet size={18} className="text-primary" aria-hidden="true" />
                    Vận hành &amp; Giao dịch Sàn
                  </h2>
                  <p>Tham số tài chính, trích xuất hoa hồng và chu kỳ kế toán đối soát</p>
                </div>
                <AdminBadge tone="teal">Tài chính</AdminBadge>
              </div>

              <div className="admin-config-list">
                {operationDefs.map(def => renderConfigRow(def))}
              </div>
            </section>
          )}

          {/* Group 2: Tham số An toàn Biển & Cứu hộ */}
          {maritimeDefs.length > 0 && (
            <section className="admin-config-group" aria-labelledby="heading-safety-group">
              <div className="admin-config-group-header">
                <div>
                  <h2 id="heading-safety-group">
                    <Waves size={18} style={{ color: '#00646f' }} aria-hidden="true" />
                    Định mức Tham chiếu An toàn Biển &amp; Cứu hộ
                  </h2>
                  <p>Ngưỡng cảnh báo thời tiết tự động kích hoạt cờ an toàn và thông tin hỗ trợ khẩn cấp</p>
                </div>
                <AdminBadge tone="amber">Tham chiếu nội bộ</AdminBadge>
              </div>

              <div className="admin-config-list">
                {maritimeDefs.map(def => renderConfigRow(def))}
              </div>
            </section>
          )}

          {/* Sticky Floating Save / Discard Bar */}
          {isDirty && (
            <aside className="admin-settings-action-bar" role="region" aria-label="Thanh tác vụ lưu thay đổi">
              <div className="admin-action-bar-info">
                <AlertTriangle size={18} style={{ color: '#f59e0b' }} aria-hidden="true" />
                <span>
                  Bạn có <strong>{changedKeys.length} thay đổi</strong> chưa được lưu vào hệ thống.
                </span>
              </div>
              <div className="admin-action-bar-buttons">
                <AdminButton
                  variant="secondary"
                  onClick={handleDiscardChanges}
                  style={{ background: 'rgba(255,255,255,0.12)', color: 'white', borderColor: 'transparent' }}
                  aria-label="Hủy toàn bộ thay đổi chưa lưu"
                >
                  Hủy thay đổi
                </AdminButton>
                <AdminButton
                  variant="primary"
                  onClick={handleSave}
                  loading={isSaving}
                  aria-label="Lưu các thay đổi cấu hình vào hệ thống"
                >
                  <Save size={15} aria-hidden="true" />
                  Lưu cấu hình
                </AdminButton>
              </div>
            </aside>
          )}
        </div>
      )}

      {/* Confirmation Dialog for Reset Defaults */}
      <AdminDialog
        open={isResetDialogOpen}
        title="Xác nhận khôi phục cấu hình mặc định"
        description="Thao tác này sẽ đặt lại toàn bộ 4 tham số hệ thống về giá trị ban đầu. Lịch sử thay đổi sẽ được ghi vào nhật ký kiểm toán (Audit Log)."
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
        <div style={{ fontSize: 13, color: '#4d6978', lineHeight: 1.6 }}>
          <p>Các tham số sẽ được khôi phục về:</p>
          <ul style={{ margin: '8px 0 0 18px', padding: 0 }}>
            <li>Tỷ lệ hoa hồng sàn: <strong>10%</strong></li>
            <li>Ngưỡng sóng tham chiếu: <strong>1.5m</strong></li>
            <li>Ngưỡng gió giật tham chiếu: <strong>25 km/h</strong></li>
            <li>Số liên hệ BQL bán đảo Sơn Trà &amp; bãi biển: <strong>0236 3920 477</strong></li>
          </ul>
        </div>
      </AdminDialog>

      {/* Toast Notification */}
      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
    </main>
  );

  function renderConfigRow(def: ConfigDefinition) {
    const activeVal = activeFormData[def.key];
    const draftVal = formData[def.key];
    const isModified = activeVal !== draftVal;
    const errorMsg = errors[def.key];
    const originalConfig = configs.find(c => c.key === def.dbKey);

    return (
      <div className="admin-config-row" key={def.key} data-modified={isModified}>
        <div className="admin-config-info">
          <div className="admin-config-heading">
            <strong>{def.label}</strong>
            <span className="admin-config-key">{def.dbKey}</span>
            {isModified ? (
              <AdminBadge tone="amber">Chưa lưu</AdminBadge>
            ) : (
              <AdminBadge tone="neutral">Đang áp dụng</AdminBadge>
            )}
          </div>

          <p className="admin-config-description">{def.description}</p>

          {def.disclaimer && (
            <div className="admin-config-disclaimer">
              <ShieldAlert size={14} aria-hidden="true" />
              <span>{def.disclaimer}</span>
            </div>
          )}

          <div className="admin-config-meta">
            <span>
              <Clock size={12} style={{ display: 'inline', verticalAlign: '-1px', marginRight: 4 }} />
              Cập nhật: {formatDateTimeVN(originalConfig?.updatedAt)}
            </span>
            <span>·</span>
            <span>Người cập nhật: <strong>{originalConfig?.updatedBy || 'Hệ thống'}</strong></span>
          </div>
        </div>

        <div className="admin-config-control">
          <div className="admin-config-input-wrap">
            <input
              id={`config-input-${def.key}`}
              type={def.inputType}
              step={def.step}
              value={draftVal}
              onChange={e => handleChange(def.key, e.target.value)}
              placeholder={def.placeholder}
              className="admin-config-input"
              aria-invalid={Boolean(errorMsg)}
              aria-describedby={errorMsg ? `error-${def.key}` : undefined}
            />
            {def.unit && <span className="admin-config-unit">{def.unit}</span>}
          </div>

          <div className="admin-config-status-tag">
            <span>Giá trị đang lưu: <strong>{activeVal} {def.unit}</strong></span>
          </div>

          {errorMsg && (
            <div id={`error-${def.key}`} className="admin-config-error" role="alert">
              {errorMsg}
            </div>
          )}
        </div>
      </div>
    );
  }
}

export default Settings;
