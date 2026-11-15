import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import {
  X,
  Plus,
  Trash2,
  Clock,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Sparkles,
} from 'lucide-react';
import { vendorService } from '../../../services/vendorService';
import { toast } from '../../../store/useToastStore';
import { extractErrorMessage } from '../../../utils/errorHandler';
import type {
  InventoryType,
  CreateSlotUnitRequest,
  SlotTemplateRequest,
  OperatingScheduleDto,
  OperatingScheduleWeeklyRuleDto,
} from '../../../types/vendorService';

interface BatchCreateSlotsModalProps {
  isOpen: boolean;
  onClose: () => void;
  serviceId: string;
  serviceName: string;
  capacityPerSlot?: number;
  onSuccess: (count: number) => void;
}

interface TemplateItem {
  id: string;
  startTime: string;
  endTime: string;
  inventoryType: InventoryType;
  capacity: number;
  units: CreateSlotUnitRequest[];
}

const DAYS_OF_WEEK = [
  { key: 'MONDAY', label: 'T2', fullLabel: 'Thứ 2', jsDay: 1 },
  { key: 'TUESDAY', label: 'T3', fullLabel: 'Thứ 3', jsDay: 2 },
  { key: 'WEDNESDAY', label: 'T4', fullLabel: 'Thứ 4', jsDay: 3 },
  { key: 'THURSDAY', label: 'T5', fullLabel: 'Thứ 5', jsDay: 4 },
  { key: 'FRIDAY', label: 'T6', fullLabel: 'Thứ 6', jsDay: 5 },
  { key: 'SATURDAY', label: 'T7', fullLabel: 'Thứ 7', jsDay: 6 },
  { key: 'SUNDAY', label: 'CN', fullLabel: 'Chủ nhật', jsDay: 0 },
];

export function BatchCreateSlotsModal({
  isOpen,
  onClose,
  serviceId,
  serviceName,
  capacityPerSlot,
  onSuccess,
}: BatchCreateSlotsModalProps) {
  const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);

  // 1. Templates State
  const [templates, setTemplates] = useState<TemplateItem[]>([
    {
      id: 't-1',
      startTime: '08:00',
      endTime: '10:00',
      inventoryType: 'PERSON_LIMIT',
      capacity: capacityPerSlot && capacityPerSlot > 0 ? capacityPerSlot : 10,
      units: [
        { unitNumber: 1, capacity: 5 },
        { unitNumber: 2, capacity: 5 },
      ],
    },
    {
      id: 't-2',
      startTime: '14:00',
      endTime: '16:00',
      inventoryType: 'PERSON_LIMIT',
      capacity: capacityPerSlot && capacityPerSlot > 0 ? capacityPerSlot : 10,
      units: [
        { unitNumber: 1, capacity: 5 },
        { unitNumber: 2, capacity: 5 },
      ],
    },
  ]);

  // 2. Days of week selected
  const [selectedDays, setSelectedDays] = useState<string[]>([
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY',
  ]);

  // 3. Application mode: DATE_RANGE vs RECURRING_90_DAYS
  const [applyMode, setApplyMode] = useState<'DATE_RANGE' | 'RECURRING_90_DAYS'>('DATE_RANGE');

  // Date range state
  const defaultEndDate = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + 6);
    return d.toISOString().split('T')[0];
  }, []);

  const [startDate, setStartDate] = useState<string>(todayStr);
  const [endDate, setEndDate] = useState<string>(defaultEndDate);

  // Existing schedule check for 90 days mode
  const [existingSchedule, setExistingSchedule] = useState<OperatingScheduleDto | null>(null);
  const [_isLoadingSchedule, setIsLoadingSchedule] = useState<boolean>(false);
  const [confirmOverwriteRecurring, setConfirmOverwriteRecurring] = useState<boolean>(false);

  // Submission state
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Fetch current operating schedule when opening modal or switching to RECURRING_90_DAYS
  useEffect(() => {
    if (!isOpen || !serviceId) return;
    setIsLoadingSchedule(true);
    vendorService
      .getOperatingSchedule(serviceId)
      .then((data) => setExistingSchedule(data))
      .catch(() => setExistingSchedule(null))
      .finally(() => setIsLoadingSchedule(false));
  }, [isOpen, serviceId]);

  // Reset error when user changes inputs
  useEffect(() => {
    setErrorMessage(null);
  }, [startDate, endDate, selectedDays, templates, applyMode]);

  // Day toggle handlers
  const handleToggleDay = (dayKey: string) => {
    setSelectedDays((prev) =>
      prev.includes(dayKey) ? prev.filter((d) => d !== dayKey) : [...prev, dayKey]
    );
  };

  const handleSelectAllDays = () => {
    setSelectedDays(DAYS_OF_WEEK.map((d) => d.key));
  };

  const handleSelectWeekdays = () => {
    setSelectedDays(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
  };

  const handleSelectWeekends = () => {
    setSelectedDays(['SATURDAY', 'SUNDAY']);
  };

  // Template handlers
  const handleAddTemplate = () => {
    const nextHour = (8 + templates.length * 2) % 24;
    const startStr = `${String(nextHour).padStart(2, '0')}:00`;
    const endStr = `${String((nextHour + 2) % 24).padStart(2, '0')}:00`;

    setTemplates((prev) => [
      ...prev,
      {
        id: `t-${Date.now()}`,
        startTime: startStr,
        endTime: endStr,
        inventoryType: 'PERSON_LIMIT',
        capacity: capacityPerSlot && capacityPerSlot > 0 ? capacityPerSlot : 10,
        units: [
          { unitNumber: 1, capacity: 5 },
          { unitNumber: 2, capacity: 5 },
        ],
      },
    ]);
  };

  const handleRemoveTemplate = (templateId: string) => {
    if (templates.length <= 1) return;
    setTemplates((prev) => prev.filter((t) => t.id !== templateId));
  };

  const handleUpdateTemplate = (templateId: string, patch: Partial<TemplateItem>) => {
    setTemplates((prev) =>
      prev.map((t) => (t.id === templateId ? { ...t, ...patch } : t))
    );
  };

  // Unit handlers for a specific template
  const handleAddUnit = (templateId: string) => {
    setTemplates((prev) =>
      prev.map((t) => {
        if (t.id !== templateId) return t;
        const nextNum = t.units.length + 1;
        return {
          ...t,
          units: [...t.units, { unitNumber: nextNum, capacity: 5 }],
        };
      })
    );
  };

  const handleDeleteUnit = (templateId: string, indexToDelete: number) => {
    setTemplates((prev) =>
      prev.map((t) => {
        if (t.id !== templateId) return t;
        if (t.units.length <= 1) return t;
        const filtered = t.units.filter((_, i) => i !== indexToDelete);
        const renumbered = filtered.map((u, idx) => ({
          ...u,
          unitNumber: idx + 1,
        }));
        return {
          ...t,
          units: renumbered,
        };
      })
    );
  };

  const handleUpdateUnitCapacity = (
    templateId: string,
    index: number,
    capacity: number
  ) => {
    setTemplates((prev) =>
      prev.map((t) => {
        if (t.id !== templateId) return t;
        const updatedUnits = t.units.map((u, i) =>
          i === index ? { ...u, capacity: Math.max(1, capacity) } : u
        );
        return {
          ...t,
          units: updatedUnits,
        };
      })
    );
  };

  // Calculate projected slots for date range
  const projectedSlotsCount = useMemo(() => {
    if (applyMode !== 'DATE_RANGE') return 0;
    if (!startDate || !endDate || startDate > endDate) return 0;
    if (selectedDays.length === 0 || templates.length === 0) return 0;

    let count = 0;
    const start = new Date(startDate);
    const end = new Date(endDate);

    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const jsDay = d.getDay();
      const match = DAYS_OF_WEEK.find((day) => day.jsDay === jsDay);
      if (match && selectedDays.includes(match.key)) {
        count += templates.length;
      }
    }
    return count;
  }, [applyMode, startDate, endDate, selectedDays, templates.length]);

  // Form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    // Validation 1: Templates
    if (templates.length === 0) {
      setErrorMessage('Cần ít nhất một ca mẫu.');
      return;
    }
    const startTimesSet = new Set<string>();
    for (const t of templates) {
      if (!t.startTime || !t.endTime) {
        setErrorMessage('Giờ bắt đầu và kết thúc của mỗi ca là bắt buộc.');
        return;
      }
      if (t.startTime >= t.endTime) {
        setErrorMessage(`Ca ${t.startTime} - ${t.endTime}: Giờ bắt đầu phải trước giờ kết thúc.`);
        return;
      }
      if (startTimesSet.has(t.startTime)) {
        setErrorMessage(`Trùng lặp giờ bắt đầu: Ca lúc ${t.startTime} đã xuất hiện.`);
        return;
      }
      startTimesSet.add(t.startTime);

      if (t.inventoryType === 'PERSON_LIMIT') {
        if (!t.capacity || t.capacity <= 0) {
          setErrorMessage(`Ca lúc ${t.startTime}: Sức chứa phải lớn hơn 0.`);
          return;
        }
      } else {
        if (!t.units || t.units.length === 0) {
          setErrorMessage(`Ca lúc ${t.startTime}: Cần ít nhất 1 phương tiện điều phối.`);
          return;
        }
        for (const u of t.units) {
          if (!u.capacity || u.capacity <= 0) {
            setErrorMessage(`Ca lúc ${t.startTime}, cano #${u.unitNumber}: Sức chứa phải lớn hơn 0.`);
            return;
          }
        }
      }
    }

    // Validation 2: Days
    if (selectedDays.length === 0) {
      setErrorMessage('Vui lòng chọn ít nhất một thứ trong tuần áp dụng.');
      return;
    }

    setIsSubmitting(true);

    try {
      if (applyMode === 'DATE_RANGE') {
        // --- LỰA CHỌN 1: BATCH TRONG KHOẢNG NGÀY ---
        if (!startDate || !endDate) {
          setErrorMessage('Vui lòng chọn ngày bắt đầu và kết thúc.');
          setIsSubmitting(false);
          return;
        }
        if (startDate > endDate) {
          setErrorMessage('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
          setIsSubmitting(false);
          return;
        }
        const startD = new Date(startDate);
        const endD = new Date(endDate);
        const diffDays = Math.round((endD.getTime() - startD.getTime()) / (1000 * 60 * 60 * 24));
        if (diffDays > 31) {
          setErrorMessage('Khoảng ngày tạo ca không được vượt quá 31 ngày.');
          setIsSubmitting(false);
          return;
        }

        const slotTemplatesPayload: SlotTemplateRequest[] = templates.map((t) => ({
          startTime: `${t.startTime}:00`,
          endTime: `${t.endTime}:00`,
          inventoryType: t.inventoryType,
          capacity: t.inventoryType === 'PERSON_LIMIT' ? t.capacity : null,
          units:
            t.inventoryType === 'SHARED_CAPACITY_UNITS'
              ? t.units.map((u) => ({ unitNumber: u.unitNumber, capacity: u.capacity }))
              : null,
        }));

        const res = await vendorService.batchCreateSlots(serviceId, {
          startDate,
          endDate,
          daysOfWeek: selectedDays,
          slotTemplates: slotTemplatesPayload,
        });

        toast.success(`Đã tạo thành công ${res.totalCreated} ca khởi hành trong khoảng ngày đã chọn!`);
        onSuccess(res.totalCreated);
        onClose();
      } else {
        // --- LỰA CHỌN 2: LẶP 90 NGÀY (OPERATING SCHEDULE) ---
        const totalRules = selectedDays.length * templates.length;
        if (totalRules > 84) {
          setErrorMessage(`Số lượng quy tắc (${totalRules}) vượt quá giới hạn 84 rules của hệ thống.`);
          setIsSubmitting(false);
          return;
        }

        if (existingSchedule && existingSchedule.rules && existingSchedule.rules.length > 0 && !confirmOverwriteRecurring) {
          setErrorMessage('Vui lòng tích xác nhận thay thế cấu hình lịch lặp 90 ngày hiện tại.');
          setIsSubmitting(false);
          return;
        }

        const rules: OperatingScheduleWeeklyRuleDto[] = [];
        for (const day of selectedDays) {
          for (const t of templates) {
            rules.push({
              dayOfWeek: day,
              startTime: `${t.startTime}:00`,
              endTime: `${t.endTime}:00`,
              inventoryType: t.inventoryType,
              capacity: t.inventoryType === 'PERSON_LIMIT' ? t.capacity : null,
              units:
                t.inventoryType === 'SHARED_CAPACITY_UNITS'
                  ? t.units.map((u) => ({ unitNumber: u.unitNumber, capacity: u.capacity, equipmentId: null }))
                  : [],
            });
          }
        }

        const res = await vendorService.putOperatingSchedule(serviceId, {
          startsOn: todayStr,
          endsOn: null,
          active: true,
          rules,
        });

        toast.success(
          `Đã cấu hình lịch lặp 90 ngày thành công! Hệ thống đã tự động sinh ${res.createdSlots} ca khởi hành mới.`
        );
        onSuccess(res.createdSlots);
        onClose();
      }
    } catch (err: unknown) {
      console.error('[BatchCreateSlotsModal] Lỗi tạo ca:', err);
      const msg = extractErrorMessage(err, 'Thao tác tạo ca thất bại');
      setErrorMessage(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200/90 w-full max-w-2xl my-6 flex flex-col max-h-[92vh] animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between shrink-0 bg-slate-50/50 rounded-t-2xl">
          <div>
            <h3 className="font-bold text-lg text-slate-900 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-teal-600" />
              Tạo ca khởi hành hàng loạt
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Dịch vụ: <span className="font-semibold text-slate-800">{serviceName}</span>
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
            title="Đóng"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-6 flex-1 text-sm">
          {/* BƯỚC 1: CẤU HÌNH CA MẪU */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                  <span className="w-5 h-5 rounded-full bg-slate-900 text-white text-xs flex items-center justify-center font-bold">
                    1
                  </span>
                  Cấu hình các ca mẫu trong ngày
                </h4>
                <p className="text-xs text-slate-500 mt-0.5">
                  Các ca này sẽ được áp dụng đồng loạt cho những ngày bạn chọn bên dưới.
                </p>
              </div>
              <button
                type="button"
                onClick={handleAddTemplate}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-teal-600 text-teal-700 hover:bg-teal-50 text-xs font-semibold transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                Thêm ca mẫu
              </button>
            </div>

            <div className="space-y-3">
              {templates.map((tpl, tIndex) => {
                const totalUnitsCapacity = tpl.units.reduce((acc, u) => acc + (u.capacity || 0), 0);
                return (
                  <div
                    key={tpl.id}
                    className="p-4 bg-slate-50/80 border border-slate-200/90 rounded-xl space-y-3 relative group"
                  >
                    <div className="flex items-center justify-between border-b border-slate-200/60 pb-2">
                      <div className="flex items-center gap-2">
                        <Clock className="w-4 h-4 text-slate-500" />
                        <span className="font-semibold text-xs text-slate-800">
                          Ca #{tIndex + 1}:
                        </span>
                        <span className="text-xs font-bold text-slate-900 font-mono">
                          {tpl.startTime} - {tpl.endTime}
                        </span>
                      </div>
                      {templates.length > 1 && (
                        <button
                          type="button"
                          onClick={() => handleRemoveTemplate(tpl.id)}
                          className="text-slate-400 hover:text-rose-600 p-1 rounded hover:bg-rose-50 transition-colors cursor-pointer"
                          title="Xóa ca mẫu này"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                      <div>
                        <label className="block text-xs font-medium text-slate-700 mb-1">
                          Giờ bắt đầu
                        </label>
                        <input
                          type="time"
                          required
                          value={tpl.startTime}
                          onChange={(e) => handleUpdateTemplate(tpl.id, { startTime: e.target.value })}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold text-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-medium text-slate-700 mb-1">
                          Giờ kết thúc
                        </label>
                        <input
                          type="time"
                          required
                          value={tpl.endTime}
                          onChange={(e) => handleUpdateTemplate(tpl.id, { endTime: e.target.value })}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold text-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900"
                        />
                      </div>
                      <div className="sm:col-span-2">
                        <label className="block text-xs font-medium text-slate-700 mb-1">
                          Mô hình tồn chỗ
                        </label>
                        <select
                          value={tpl.inventoryType}
                          onChange={(e) =>
                            handleUpdateTemplate(tpl.id, { inventoryType: e.target.value as InventoryType })
                          }
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-slate-900 cursor-pointer"
                        >
                          <option value="PERSON_LIMIT">Giới hạn theo đầu người</option>
                          <option value="SHARED_CAPACITY_UNITS">Chia theo từng cano / phương tiện</option>
                        </select>
                      </div>
                    </div>

                    {tpl.inventoryType === 'PERSON_LIMIT' ? (
                      <div className="flex items-center gap-3 pt-1">
                        <label className="text-xs font-medium text-slate-700 whitespace-nowrap">
                          Sức chứa ca này:
                        </label>
                        <input
                          type="number"
                          min={1}
                          required
                          value={tpl.capacity}
                          onChange={(e) =>
                            handleUpdateTemplate(tpl.id, { capacity: Number(e.target.value) })
                          }
                          className="w-28 px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold text-slate-900"
                          placeholder="Số khách"
                        />
                        <span className="text-xs text-slate-500">khách</span>
                      </div>
                    ) : (
                      <div className="p-3 bg-white border border-slate-200 rounded-xl space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-slate-800">
                            Danh sách cano / phương tiện:
                          </span>
                          <button
                            type="button"
                            onClick={() => handleAddUnit(tpl.id)}
                            className="text-xs font-bold text-teal-700 hover:text-teal-800 hover:underline cursor-pointer"
                          >
                            + Thêm cano
                          </button>
                        </div>
                        <div className="space-y-1.5 max-h-36 overflow-y-auto pr-1">
                          {tpl.units.map((unit, uIdx) => (
                            <div key={uIdx} className="flex items-center gap-2">
                              <span className="text-xs font-medium text-slate-600 w-12 shrink-0">
                                #{unit.unitNumber}
                              </span>
                              <input
                                type="number"
                                min={1}
                                required
                                value={unit.capacity}
                                onChange={(e) =>
                                  handleUpdateUnitCapacity(tpl.id, uIdx, Number(e.target.value))
                                }
                                className="w-24 px-2 py-1 border border-slate-300 rounded-lg text-xs bg-slate-50/50"
                                placeholder="Sức chứa"
                              />
                              <span className="text-xs text-slate-500">khách</span>

                              {/* Nút xóa cano */}
                              <button
                                type="button"
                                disabled={tpl.units.length <= 1}
                                onClick={() => handleDeleteUnit(tpl.id, uIdx)}
                                className={`p-1 rounded transition-colors ml-auto ${
                                  tpl.units.length <= 1
                                    ? 'text-slate-300 cursor-not-allowed'
                                    : 'text-slate-400 hover:text-rose-600 hover:bg-rose-50 cursor-pointer'
                                }`}
                                title={
                                  tpl.units.length <= 1
                                    ? 'Cần tối thiểu 1 phương tiện'
                                    : 'Xóa phương tiện này'
                                }
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          ))}
                        </div>
                        <div className="text-[11px] text-slate-500 pt-1 border-t border-slate-100 flex items-center justify-between">
                          <span>
                            Tổng sức chứa: <strong className="text-slate-900">{totalUnitsCapacity}</strong> khách
                          </span>
                          <span>({tpl.units.length} cano/phương tiện)</span>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* BƯỚC 2: CHỌN THỨ ÁP DỤNG */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                  <span className="w-5 h-5 rounded-full bg-slate-900 text-white text-xs flex items-center justify-center font-bold">
                    2
                  </span>
                  Chọn các thứ trong tuần áp dụng
                </h4>
                <p className="text-xs text-slate-500 mt-0.5">
                  Phải chọn ít nhất 1 thứ. Các ca mẫu sẽ được tạo vào các thứ này.
                </p>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={handleSelectAllDays}
                  className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium cursor-pointer"
                >
                  Cả tuần
                </button>
                <button
                  type="button"
                  onClick={handleSelectWeekdays}
                  className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium cursor-pointer"
                >
                  T2-T6
                </button>
                <button
                  type="button"
                  onClick={handleSelectWeekends}
                  className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium cursor-pointer"
                >
                  T7-CN
                </button>
              </div>
            </div>

            <div className="grid grid-cols-7 gap-1.5">
              {DAYS_OF_WEEK.map((d) => {
                const isSelected = selectedDays.includes(d.key);
                return (
                  <button
                    key={d.key}
                    type="button"
                    onClick={() => handleToggleDay(d.key)}
                    className={`py-2 px-1 rounded-xl text-xs font-bold border transition-all text-center cursor-pointer ${
                      isSelected
                        ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                        : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    <div>{d.label}</div>
                    <div className={`text-[10px] font-normal ${isSelected ? 'text-slate-300' : 'text-slate-400'}`}>
                      {d.key === 'SUNDAY' ? 'CN' : d.label}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* BƯỚC 3: CHỌN CÁCH ÁP DỤNG */}
          <div className="space-y-3">
            <h4 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
              <span className="w-5 h-5 rounded-full bg-slate-900 text-white text-xs flex items-center justify-center font-bold">
                3
              </span>
              Chọn cách áp dụng
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {/* Lựa chọn 1 */}
              <label
                className={`p-3.5 rounded-xl border-2 transition-all cursor-pointer flex flex-col justify-between ${
                  applyMode === 'DATE_RANGE'
                    ? 'border-slate-900 bg-slate-50/70 shadow-xs'
                    : 'border-slate-200 bg-white hover:border-slate-300'
                }`}
              >
                <div className="flex items-start gap-2.5">
                  <input
                    type="radio"
                    name="applyMode"
                    value="DATE_RANGE"
                    checked={applyMode === 'DATE_RANGE'}
                    onChange={() => setApplyMode('DATE_RANGE')}
                    className="mt-0.5 text-slate-900 focus:ring-slate-900 cursor-pointer"
                  />
                  <div>
                    <span className="font-bold text-xs text-slate-900 block">
                      Tạo trong khoảng ngày
                    </span>
                    <span className="text-xs text-slate-500 mt-0.5 block leading-relaxed">
                      Tạo các ca trong khoảng ngày bạn chọn, không tự động lặp về sau.
                    </span>
                  </div>
                </div>
              </label>

              {/* Lựa chọn 2 */}
              <label
                className={`p-3.5 rounded-xl border-2 transition-all cursor-pointer flex flex-col justify-between ${
                  applyMode === 'RECURRING_90_DAYS'
                    ? 'border-slate-900 bg-slate-50/70 shadow-xs'
                    : 'border-slate-200 bg-white hover:border-slate-300'
                }`}
              >
                <div className="flex items-start gap-2.5">
                  <input
                    type="radio"
                    name="applyMode"
                    value="RECURRING_90_DAYS"
                    checked={applyMode === 'RECURRING_90_DAYS'}
                    onChange={() => setApplyMode('RECURRING_90_DAYS')}
                    className="mt-0.5 text-slate-900 focus:ring-slate-900 cursor-pointer"
                  />
                  <div>
                    <span className="font-bold text-xs text-slate-900 block">
                      Tự động lặp 90 ngày
                    </span>
                    <span className="text-xs text-slate-500 mt-0.5 block leading-relaxed">
                      Thiết lập lịch theo tuần để hệ thống tự tạo ca cho 90 ngày tới.
                    </span>
                  </div>
                </div>
              </label>
            </div>

            {/* Form chi tiết tương ứng với Lựa chọn */}
            {applyMode === 'DATE_RANGE' ? (
              <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Từ ngày:
                    </label>
                    <input
                      type="date"
                      required
                      min={todayStr}
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-slate-900 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-slate-900"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Đến ngày:
                    </label>
                    <input
                      type="date"
                      required
                      min={startDate || todayStr}
                      value={endDate}
                      onChange={(e) => setEndDate(e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-slate-900 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-slate-900"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs pt-1 border-t border-slate-200/60">
                  <span className="text-slate-600">
                    Số ca dự kiến tạo:
                  </span>
                  <span className="font-bold text-sm font-mono text-teal-800 bg-teal-50 px-2.5 py-0.5 rounded-lg border border-teal-200">
                    {projectedSlotsCount} ca
                  </span>
                </div>
              </div>
            ) : (
              <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3 text-xs">
                <div>
                  <p className="font-bold text-xs text-slate-900">
                    Thiết lập lịch lặp 90 ngày
                  </p>
                  <p className="mt-0.5 text-slate-600 leading-relaxed">
                    Hệ thống sẽ tự tạo lịch khởi hành theo các thứ và khung giờ bạn đã chọn.
                  </p>
                </div>

                {existingSchedule && existingSchedule.rules && existingSchedule.rules.length > 0 && (
                  <div className="p-3 bg-amber-50/80 border border-amber-200 rounded-lg space-y-2 text-amber-950">
                    <p className="font-bold text-xs text-amber-900 flex items-center gap-1.5">
                      <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                      Dịch vụ này đã có lịch lặp
                    </p>
                    <p className="text-[11px] text-amber-800 leading-normal">
                      Khi thay thế, những ca không còn thuộc lịch mới có thể được đóng bán. Các đơn đã đặt vẫn được giữ nguyên.
                    </p>
                    <label className="flex items-center gap-2 text-xs font-semibold text-slate-900 cursor-pointer pt-1">
                      <input
                        type="checkbox"
                        checked={confirmOverwriteRecurring}
                        onChange={(e) => setConfirmOverwriteRecurring(e.target.checked)}
                        className="rounded text-slate-900 focus:ring-slate-900 cursor-pointer"
                      />
                      <span>Tôi xác nhận thay thế lịch lặp hiện tại</span>
                    </label>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Error Message Alert */}
          {errorMessage && (
            <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-2.5 text-rose-800 text-xs">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <div className="flex-1 font-medium">{errorMessage}</div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3 shrink-0">
            <button
              type="button"
              disabled={isSubmitting}
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50 font-semibold text-xs transition-colors cursor-pointer disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs shadow-sm transition-colors cursor-pointer flex items-center gap-2 disabled:opacity-60"
            >
              {isSubmitting ? (
                <>
                  <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Đang xử lý...</span>
                </>
              ) : applyMode === 'DATE_RANGE' ? (
                <>
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Tạo {projectedSlotsCount} ca ngay</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Xác nhận thiết lập lịch 90 ngày</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
