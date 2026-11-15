import React, { useState, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { useSearchParams } from "react-router-dom";
import { vendorService } from "../../../services/vendorService";
import { toast } from "../../../store/useToastStore";
import { extractErrorMessage } from "../../../utils/errorHandler";
import type {
  VendorServiceDto,
  VendorServiceSlotDto,
  InventoryType,
  SlotStatus,
  CreateSlotUnitRequest,
} from "../../../types/vendorService";
import {
  Clock,
  Plus,
  RotateCcw,
  X,
  Lock,
  Unlock,
  Edit2,
  CalendarDays,
  AlertCircle,
  Layers,
  ChevronDown,
  Trash2,
} from "lucide-react";
import {
  AtlasPageHeader,
  AtlasBadge,
  AtlasButton
} from "../../../components/vendor/atlas/AtlasDesignSystem";
import { SlotAdvisorBanner } from "./SlotAdvisorBanner";
import { BatchCreateSlotsModal } from "./BatchCreateSlotsModal";

export function Schedule() {
  const [searchParams] = useSearchParams();
  const queryServiceId = searchParams.get("serviceId");

  const showToast = (msg: string) => {
    toast.success(msg);
  };

  // Services State
  const [services, setServices] = useState<VendorServiceDto[]>([]);
  const [selectedServiceId, setSelectedServiceId] = useState<string>("");
  const [isLoadingServices, setIsLoadingServices] = useState<boolean>(true);

  // Slots State
  const [slots, setSlots] = useState<VendorServiceSlotDto[]>([]);
  const [isLoadingSlots, setIsLoadingSlots] = useState<boolean>(false);
  const [slotsErrorMessage, setSlotsErrorMessage] = useState<string | null>(null);

  // Date Filter State
  const todayStr = new Date().toISOString().split("T")[0];
  const [selectedDate, setSelectedDate] = useState<string>(todayStr);

  // Modal State: Create Slot
  const [createMenuAnchor, setCreateMenuAnchor] = useState<'header' | 'empty' | null>(null);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isBatchModalOpen, setIsBatchModalOpen] = useState(false);
  const [newDate, setNewDate] = useState<string>(todayStr);
  const [newStartTime, setNewStartTime] = useState<string>("06:00");
  const [newEndTime, setNewEndTime] = useState<string>("08:00");
  const [newInventoryType, setNewInventoryType] = useState<InventoryType>("PERSON_LIMIT");
  const [newCapacity, setNewCapacity] = useState<number>(10);
  const [newUnits, setNewUnits] = useState<CreateSlotUnitRequest[]>([
    { unitNumber: 1, capacity: 5 },
    { unitNumber: 2, capacity: 5 },
  ]);
  const [repeatDays, setRepeatDays] = useState<number>(1);
  const [isSubmittingSlot, setIsSubmittingSlot] = useState(false);

  // Modal State: Edit Slot
  const [editingSlot, setEditingSlot] = useState<VendorServiceSlotDto | null>(null);
  const [inputCapacity, setInputCapacity] = useState<number>(10);
  const [inputStatus, setInputStatus] = useState<SlotStatus>("OPEN");
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [isUpdatingSlot, setIsUpdatingSlot] = useState(false);

  // Close create menu when clicking outside
  useEffect(() => {
    if (!createMenuAnchor) return;
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as HTMLElement;
      if (!target.closest('[data-create-menu-container]')) {
        setCreateMenuAnchor(null);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setCreateMenuAnchor(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [createMenuAnchor]);

  const [previousQueryService, setPreviousQueryService] = useState(queryServiceId);
  if (previousQueryService !== queryServiceId) {
    setPreviousQueryService(queryServiceId);
    setIsLoadingServices(true);
  }
  const [previousService, setPreviousService] = useState(selectedServiceId);
  if (previousService !== selectedServiceId) {
    setPreviousService(selectedServiceId);
    setSlots([]);
    setSlotsErrorMessage(null);
    setIsLoadingSlots(Boolean(selectedServiceId));
  }

  // 1. Fetch vendor services
  useEffect(() => {
    vendorService.getMyServices()
      .then((data) => {
        const list = data || [];
        setServices(list);
        if (queryServiceId && list.some(s => s.id === queryServiceId)) {
          setSelectedServiceId(queryServiceId);
        } else if (list.length > 0) {
          setSelectedServiceId(list[0].id);
        }
      })
      .catch((err) => {
        console.error("[Schedule] Lỗi nạp danh sách dịch vụ:", err);
      })
      .finally(() => {
        setIsLoadingServices(false);
      });
  }, [queryServiceId]);

  // 2. Fetch slots for selected service
  const loadSlots = useCallback((serviceId: string) => {
    if (!serviceId) return;
    return vendorService.getSlots(serviceId).then((data) => {
      setSlotsErrorMessage(null);
      setSlots(data || []);
    }).catch((err: unknown) => {
      console.error("[Schedule] Lỗi nạp danh sách ca:", err);
      const msg = err instanceof Error ? err.message : "Không thể nạp lịch ca";
      setSlotsErrorMessage(msg);
    }).finally(() => {
      setIsLoadingSlots(false);
    });
  }, []);

  useEffect(() => {
    if (selectedServiceId) {
      loadSlots(selectedServiceId);
    }
  }, [selectedServiceId, loadSlots]);

  // Toggle status between OPEN and CLOSED
  const handleToggleStatus = async (slot: VendorServiceSlotDto) => {
    if (!selectedServiceId) return;
    const nextStatus: SlotStatus = slot.status === "OPEN" ? "CLOSED" : "OPEN";
    try {
      const updated = await vendorService.updateSlot(selectedServiceId, slot.id, {
        status: nextStatus,
      });
      setSlots((prev) => prev.map((s) => (s.id === slot.id ? updated : s)));
      showToast(
        nextStatus === "CLOSED"
          ? "Đã đóng ca vận hành (tạm ngừng nhận khách)!"
          : "Đã mở lại ca vận hành đón khách!"
      );
    } catch (err: unknown) {
      console.error('[Schedule] Lỗi đổi trạng thái ca:', err);
      const msg = extractErrorMessage(err, 'Đổi trạng thái ca thất bại');
      toast.error(msg);
    }
  };

  // Open Edit Capacity Modal
  const handleOpenEditCapacity = (slot: VendorServiceSlotDto) => {
    setEditingSlot(slot);
    setInputCapacity(slot.capacity);
    setInputStatus(slot.status);
    setCapacityError(null);
  };

  // Save Slot Edit
  const handleSaveCapacity = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSlot || !selectedServiceId) return;

    const booked = editingSlot.bookedCount || 0;
    if (inputCapacity < booked) {
      setCapacityError(
        `Không thể giảm sức chứa xuống dưới ${booked} vì đã có ${booked} khách đặt thành công!`
      );
      return;
    }

    setIsUpdatingSlot(true);
    setCapacityError(null);
    try {
      const updated = await vendorService.updateSlot(selectedServiceId, editingSlot.id, {
        capacity: inputCapacity,
        status: inputStatus,
      });
      setSlots((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
      showToast("Cập nhật ca khởi hành thành công!");
      setEditingSlot(null);
    } catch (err: unknown) {
      console.error("[Schedule] Lỗi sửa ca:", err);
      const msg = extractErrorMessage(err, "Cập nhật ca thất bại");
      setCapacityError(msg);
      toast.error(msg);
    } finally {
      setIsUpdatingSlot(false);
    }
  };

  // Create New Slot
  const handleCreateSlot = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedServiceId) {
      toast.warning("Vui lòng chọn một dịch vụ trước khi tạo ca.");
      return;
    }

    if (newStartTime >= newEndTime) {
      toast.warning("Giờ bắt đầu phải trước giờ kết thúc.");
      return;
    }

    setIsSubmittingSlot(true);
    try {
      const payloadBase = {
        startTime: `${newStartTime}:00`,
        endTime: `${newEndTime}:00`,
        inventoryType: newInventoryType,
        capacity: newInventoryType === "PERSON_LIMIT"
          ? newCapacity
          : newUnits.reduce((acc, u) => acc + (u.capacity || 0), 0),
        units: newInventoryType === "SHARED_CAPACITY_UNITS" ? newUnits : undefined,
      };

      const datesToCreate: string[] = [];
      const base = new Date(newDate);
      for (let i = 0; i < repeatDays; i++) {
        const d = new Date(base);
        d.setDate(base.getDate() + i);
        datesToCreate.push(d.toISOString().split("T")[0]);
      }

      const createdSlots: VendorServiceSlotDto[] = [];
      let successCount = 0;
      let errorOccurred: unknown = null;

      for (const dStr of datesToCreate) {
        try {
          const res = await vendorService.createSlot(selectedServiceId, {
            ...payloadBase,
            date: dStr,
          });
          createdSlots.push(res);
          successCount++;
        } catch (err) {
          console.warn(`[Schedule] Bỏ qua ngày ${dStr} do trùng ca hoặc lỗi:`, err);
          errorOccurred = err;
        }
      }

      if (createdSlots.length > 0) {
        setSlots((prev) => [...prev, ...createdSlots]);
      }

      if (repeatDays > 1) {
        if (successCount > 0) {
          showToast(`Đã tạo thành công ${successCount}/${datesToCreate.length} ca khởi hành liên tiếp!`);
          setIsCreateModalOpen(false);
        } else {
          const msg = extractErrorMessage(errorOccurred, 'Không thể tạo chuỗi ca khởi hành');
          toast.error(msg);
        }
      } else {
        if (successCount > 0) {
          showToast(`Đã tạo ca khởi hành mới (${newStartTime} - ${newEndTime}) thành công!`);
          setIsCreateModalOpen(false);
        } else {
          const msg = extractErrorMessage(errorOccurred, 'Không thể tạo ca khởi hành');
          toast.error(msg);
        }
      }
    } catch (err: unknown) {
      console.error('[Schedule] Lỗi tạo ca:', err);
      const msg = extractErrorMessage(err, 'Không thể tạo ca khởi hành');
      toast.error(msg);
    } finally {
      setIsSubmittingSlot(false);
    }
  };

  // Filter slots by selected date if set
  const filteredSlots = slots.filter((s) => {
    if (selectedDate && s.date !== selectedDate) return false;
    return true;
  });

  const selectedServiceObj = services.find((s) => s.id === selectedServiceId);

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Lịch Khởi hành & Điều phối Ca"
        actions={
          <div className="relative" data-create-menu-container>
            <button
              type="button"
              disabled={!selectedServiceId}
              onClick={() => setCreateMenuAnchor((prev) => (prev === 'header' ? null : 'header'))}
              aria-haspopup="true"
              aria-expanded={createMenuAnchor === 'header'}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 text-white text-sm font-medium shadow-sm transition-all duration-200 hover:bg-slate-800 hover:ring-2 hover:ring-teal-500/20 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer focus:outline-none focus:ring-2 focus:ring-teal-500/40"
            >
              <Plus className="w-4 h-4 shrink-0 text-white" />
              <span>Tạo ca khởi hành</span>
              <ChevronDown
                className={`w-4 h-4 shrink-0 text-slate-300 transition-transform duration-200 ${
                  createMenuAnchor === 'header' ? 'rotate-180' : ''
                }`}
              />
            </button>

            {createMenuAnchor === 'header' && (
              <div
                role="menu"
                className="absolute right-0 top-full mt-2 w-72 bg-white rounded-2xl shadow-xl border border-slate-200/90 py-2 z-50 transition-all duration-200 transform opacity-100 translate-y-0 animate-in fade-in slide-in-from-top-2"
              >
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setCreateMenuAnchor(null);
                    setIsCreateModalOpen(true);
                  }}
                  className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer group"
                >
                  <div className="p-2 rounded-xl bg-slate-100 text-slate-700 group-hover:bg-slate-200/70 transition-colors shrink-0 mt-0.5">
                    <Clock className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="font-bold text-xs text-slate-900">A. Tạo ca đơn</div>
                    <div className="text-[11px] text-slate-500 mt-0.5 leading-snug">
                      Tạo một ca riêng lẻ hoặc lặp lại 1 - 30 ngày liên tiếp
                    </div>
                  </div>
                </button>

                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setCreateMenuAnchor(null);
                    setIsBatchModalOpen(true);
                  }}
                  className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer border-t border-slate-100 group"
                >
                  <div className="p-2 rounded-xl bg-teal-50 text-teal-700 group-hover:bg-teal-100/80 transition-colors shrink-0 mt-0.5">
                    <Layers className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="font-bold text-xs text-slate-900 flex items-center gap-1.5">
                      <span>B. Tạo ca hàng loạt</span>
                    </div>
                    <div className="text-[11px] text-slate-500 mt-0.5 leading-snug">
                      Cấu hình ca theo thứ, áp dụng khoảng ngày hoặc lặp 90 ngày
                    </div>
                  </div>
                </button>
              </div>
            )}
          </div>
        }
      />

      {/* 2. AI Slot Advisor Banner */}
      <SlotAdvisorBanner serviceId={selectedServiceId} />

      {/* 3. Filter & Service Selection Panel (Atlas Toolbar) */}
      <div className="bg-white p-3.5 rounded-xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3.5">
        <div className="flex flex-wrap items-center gap-3">
          {/* Service Dropdown */}
          <div className="min-w-[280px]">
            <select
              value={selectedServiceId}
              onChange={(e) => setSelectedServiceId(e.target.value)}
              className="w-full h-11 px-3.5 py-2.5 rounded-xl bg-slate-50/70 border border-slate-200 text-sm font-medium text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/20"
              disabled={isLoadingServices}
            >
              {services.length === 0 ? (
                <option value="">(Chưa có dịch vụ nào)</option>
              ) : (
                services.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.status})
                  </option>
                ))
              )}
            </select>
          </div>

          {/* Date Picker */}
          <div className="relative flex items-center">
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="h-11 px-3.5 py-2.5 rounded-xl bg-slate-50/70 border border-slate-200 text-sm font-medium text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
            {selectedDate && (
              <button
                onClick={() => setSelectedDate("")}
                className="ml-2 text-slate-400 hover:text-slate-600 cursor-pointer p-1"
                title="Xem tất cả ngày"
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {selectedDate === todayStr && (
            <AtlasBadge label="Hôm nay" variant="info" />
          )}
        </div>

        <button
          onClick={() => {
            if (selectedServiceId) {
              setIsLoadingSlots(true);
              loadSlots(selectedServiceId);
            }
          }}
          className="h-11 w-11 flex items-center justify-center rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 transition-colors cursor-pointer self-end md:self-auto"
          title="Làm mới lịch ca"
          type="button"
        >
          <RotateCcw className="w-4 h-4 text-slate-600" />
        </button>
      </div>

      {/* Error Fallback */}
      {slotsErrorMessage && (
        <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-between text-rose-800 text-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />
            <span>Lỗi tải danh sách ca: {slotsErrorMessage}</span>
          </div>
          <AtlasButton
            variant="danger"
            size="sm"
            onClick={() => {
              if (selectedServiceId) {
                setIsLoadingSlots(true);
                loadSlots(selectedServiceId);
              }
            }}
          >
            Thử lại
          </AtlasButton>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoadingSlots && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-pulse">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-44 bg-slate-100 rounded-xl border border-slate-200/80" />
          ))}
        </div>
      )}

      {/* Slots Grid */}
      {!isLoadingSlots && !slotsErrorMessage && (
        <>
          {filteredSlots.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {filteredSlots.map((slot) => {
                const booked = slot.bookedCount || 0;
                const available = slot.availableCapacity != null ? slot.availableCapacity : (slot.capacity - booked);
                const isClosed = slot.status === "CLOSED";
                const isFull = available <= 0;

                return (
                  <div
                    key={slot.id}
                    className={`rounded-xl border p-5 bg-white shadow-xs transition-all flex flex-col justify-between ${
                      isClosed
                        ? "border-slate-200/60 opacity-80 bg-slate-50/50"
                        : "border-slate-200/80 hover:border-slate-300"
                    }`}
                  >
                    <div className="space-y-3">
                      {/* Time & Status Badge */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Clock className="w-4 h-4 text-slate-400" />
                          <span className="font-mono text-base font-bold text-slate-900 tracking-tight">
                            {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                          </span>
                        </div>

                        <div>
                          {isClosed ? (
                            <AtlasBadge label="Đã đóng" variant="neutral" />
                          ) : isFull ? (
                            <AtlasBadge label="Hết chỗ" variant="danger" />
                          ) : (
                            <AtlasBadge label="Đang mở" variant="success" />
                          )}
                        </div>
                      </div>

                      {/* Date & Inventory Type */}
                      <div className="text-xs text-slate-500 flex items-center justify-between">
                        <span>Ngày: <strong className="text-slate-800 font-mono">{slot.date}</strong></span>
                        <span className="px-2 py-0.5 bg-slate-100 rounded text-slate-600 font-medium">
                          {slot.inventoryType === "SHARED_CAPACITY_UNITS" ? "Theo cano" : "Theo đầu người"}
                        </span>
                      </div>

                      {/* Capacity Progress Bar */}
                      <div className="space-y-1.5 pt-1">
                        <div className="flex items-center justify-between text-xs">
                          <span className="text-slate-500">Đã đặt:</span>
                          <span className="font-mono font-bold text-slate-900 tabular-nums">
                            {booked} / {slot.capacity} khách
                          </span>
                        </div>
                        <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all ${
                              isFull ? "bg-rose-500" : isClosed ? "bg-slate-400" : "bg-slate-900"
                            }`}
                            style={{ width: `${Math.min(100, (booked / Math.max(1, slot.capacity)) * 100)}%` }}
                          />
                        </div>
                        <div className="flex justify-between text-xs text-slate-500">
                          <span>Chỗ khả dụng: <strong className="font-mono text-emerald-700 font-bold">{Math.max(0, available)}</strong></span>
                          <span className="font-mono text-slate-400">
                            {Math.round((booked / Math.max(1, slot.capacity)) * 100)}%
                          </span>
                        </div>
                      </div>

                      {/* Units Details if Shared Capacity Units */}
                      {slot.units && slot.units.length > 0 && (
                        <div className="pt-2 border-t border-slate-100 space-y-1">
                          <p className="text-xs font-semibold text-slate-700">Phương tiện điều phối:</p>
                          <div className="flex flex-wrap gap-1.5">
                            {slot.units.map((u) => (
                              <span
                                key={u.id}
                                className="text-xs font-mono px-2 py-0.5 bg-slate-50 border border-slate-200 rounded text-slate-700"
                              >
                                Cano #{u.unitNumber}: {u.bookedCount}/{u.capacity}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div className="pt-4 mt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                      <AtlasButton
                        variant={isClosed ? "secondary" : "outline"}
                        size="sm"
                        onClick={() => handleToggleStatus(slot)}
                        icon={isClosed ? <Unlock className="w-3.5 h-3.5" /> : <Lock className="w-3.5 h-3.5" />}
                      >
                        {isClosed ? "Mở nhận khách" : "Đóng ca"}
                      </AtlasButton>

                      <AtlasButton
                        variant="primary"
                        size="sm"
                        onClick={() => handleOpenEditCapacity(slot)}
                        icon={<Edit2 className="w-3.5 h-3.5" />}
                      >
                        Điều chỉnh
                      </AtlasButton>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            /* Empty State */
            <div className="w-full bg-white rounded-xl border border-slate-200/80 p-12 text-center">
              <div className="flex flex-col items-center justify-center space-y-3 text-slate-500 max-w-sm mx-auto">
                <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400">
                  <CalendarDays className="w-6 h-6" />
                </div>
                <p className="font-semibold text-slate-900 text-base">Chưa có ca khởi hành nào</p>
                <p className="text-xs text-slate-500">
                  {selectedServiceObj
                    ? `Dịch vụ "${selectedServiceObj.name}" chưa có ca khởi hành trong ngày đã chọn. Hãy tạo ca mới để bắt đầu đón khách!`
                    : "Vui lòng chọn một dịch vụ từ danh sách phía trên."}
                </p>
                {selectedServiceId && (
                  <div className="relative inline-block" data-create-menu-container>
                    <button
                      type="button"
                      disabled={!selectedServiceId}
                      onClick={() => setCreateMenuAnchor((prev) => (prev === 'empty' ? null : 'empty'))}
                      aria-haspopup="true"
                      aria-expanded={createMenuAnchor === 'empty'}
                      className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-900 text-white text-xs font-medium shadow-sm transition-all duration-200 hover:bg-slate-800 hover:ring-2 hover:ring-teal-500/20 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer focus:outline-none focus:ring-2 focus:ring-teal-500/40"
                    >
                      <Plus className="w-4 h-4 shrink-0 text-white" />
                      <span>Tạo ca khởi hành mới</span>
                      <ChevronDown
                        className={`w-3.5 h-3.5 shrink-0 text-slate-300 transition-transform duration-200 ${
                          createMenuAnchor === 'empty' ? 'rotate-180' : ''
                        }`}
                      />
                    </button>

                    {createMenuAnchor === 'empty' && (
                      <div
                        role="menu"
                        className="absolute left-1/2 -translate-x-1/2 top-full mt-2 w-72 bg-white rounded-2xl shadow-xl border border-slate-200/90 py-2 z-50 text-left transition-all duration-200 transform opacity-100 translate-y-0 animate-in fade-in slide-in-from-top-2"
                      >
                        <button
                          type="button"
                          role="menuitem"
                          onClick={() => {
                            setCreateMenuAnchor(null);
                            setIsCreateModalOpen(true);
                          }}
                          className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer group"
                        >
                          <div className="p-2 rounded-xl bg-slate-100 text-slate-700 group-hover:bg-slate-200/70 transition-colors shrink-0 mt-0.5">
                            <Clock className="w-4 h-4" />
                          </div>
                          <div>
                            <div className="font-bold text-xs text-slate-900">A. Tạo ca đơn</div>
                            <div className="text-[11px] text-slate-500 mt-0.5 leading-snug">
                              Tạo một ca riêng lẻ hoặc lặp lại 1 - 30 ngày liên tiếp
                            </div>
                          </div>
                        </button>

                        <button
                          type="button"
                          role="menuitem"
                          onClick={() => {
                            setCreateMenuAnchor(null);
                            setIsBatchModalOpen(true);
                          }}
                          className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer border-t border-slate-100 group"
                        >
                          <div className="p-2 rounded-xl bg-teal-50 text-teal-700 group-hover:bg-teal-100/80 transition-colors shrink-0 mt-0.5">
                            <Layers className="w-4 h-4" />
                          </div>
                          <div>
                            <div className="font-bold text-xs text-slate-900 flex items-center gap-1.5">
                              <span>B. Tạo ca hàng loạt</span>
                            </div>
                            <div className="text-[11px] text-slate-500 mt-0.5 leading-snug">
                              Cấu hình ca theo thứ, áp dụng khoảng ngày hoặc lặp 90 ngày
                            </div>
                          </div>
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {/* Modal: Create Slot */}
      {isCreateModalOpen && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/90 w-full max-w-md p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-slate-900">Tạo ca khởi hành mới</h3>
              <button onClick={() => setIsCreateModalOpen(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateSlot} className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Dịch vụ:</label>
                <input
                  type="text"
                  disabled
                  value={selectedServiceObj?.name || "Chưa chọn"}
                  className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl text-slate-800 font-semibold text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Ngày diễn ra:</label>
                <input
                  type="date"
                  required
                  min={todayStr}
                  value={newDate}
                  onChange={(e) => setNewDate(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Giờ bắt đầu:</label>
                  <input
                    type="time"
                    required
                    value={newStartTime}
                    onChange={(e) => setNewStartTime(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none font-bold"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Giờ kết thúc:</label>
                  <input
                    type="time"
                    required
                    value={newEndTime}
                    onChange={(e) => setNewEndTime(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none font-bold"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Lặp lại lịch ca:</label>
                <div className="grid grid-cols-4 gap-2">
                  {[
                    { label: '1 ngày', val: 1 },
                    { label: '7 ngày', val: 7 },
                    { label: '14 ngày', val: 14 },
                    { label: '30 ngày', val: 30 }
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      type="button"
                      onClick={() => setRepeatDays(opt.val)}
                      className={`py-2 px-2 rounded-xl border text-xs font-semibold transition-colors cursor-pointer ${
                        repeatDays === opt.val
                          ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                          : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Mô hình tồn chỗ:</label>
                <select
                  value={newInventoryType}
                  onChange={(e) => setNewInventoryType(e.target.value as InventoryType)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none bg-white cursor-pointer"
                >
                  <option value="PERSON_LIMIT">Giới hạn theo đầu người</option>
                  <option value="SHARED_CAPACITY_UNITS">Chia theo từng cano / phương tiện</option>
                </select>
              </div>

              {newInventoryType === "PERSON_LIMIT" ? (
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Tổng sức chứa (khách):</label>
                  <input
                    type="number"
                    required
                    min={1}
                    value={newCapacity}
                    onChange={(e) => setNewCapacity(Number(e.target.value))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none font-bold"
                  />
                </div>
              ) : (
                <div className="space-y-2 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-slate-700 text-xs">Danh sách cano / phương tiện:</span>
                    <button
                      type="button"
                      onClick={() =>
                        setNewUnits((prev) => [
                          ...prev,
                          { unitNumber: prev.length + 1, capacity: 5 },
                        ])
                      }
                      className="text-primary text-xs font-bold hover:underline cursor-pointer"
                    >
                      + Thêm cano
                    </button>
                  </div>
                  <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
                    {newUnits.map((u, idx) => (
                      <div key={idx} className="flex items-center gap-2">
                        <span className="text-slate-600 font-medium text-xs w-10 shrink-0">#{u.unitNumber}</span>
                        <input
                          type="number"
                          min={1}
                          value={u.capacity}
                          onChange={(e) => {
                            const val = Number(e.target.value);
                            setNewUnits((prev) =>
                              prev.map((item, i) => (i === idx ? { ...item, capacity: Math.max(1, val) } : item))
                            );
                          }}
                          className="w-24 px-2 py-1.5 border border-slate-300 rounded-lg bg-white text-xs"
                          placeholder="Sức chứa"
                        />
                        <span className="text-slate-500 text-xs">khách</span>

                        {/* Nút xóa cano */}
                        <button
                          type="button"
                          disabled={newUnits.length <= 1}
                          onClick={() => {
                            if (newUnits.length <= 1) return;
                            setNewUnits((prev) => {
                              const remaining = prev.filter((_, i) => i !== idx);
                              return remaining.map((unit, i) => ({
                                ...unit,
                                unitNumber: i + 1,
                              }));
                            });
                          }}
                          className={`p-1 rounded transition-colors ml-auto ${
                            newUnits.length <= 1
                              ? 'text-slate-300 cursor-not-allowed'
                              : 'text-slate-400 hover:text-rose-600 hover:bg-rose-50 cursor-pointer'
                          }`}
                          title={newUnits.length <= 1 ? "Cần tối thiểu 1 phương tiện" : "Xóa phương tiện này"}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>

                  <div className="text-[11px] text-slate-500 pt-1.5 border-t border-slate-200/60 flex items-center justify-between">
                    <span>
                      Tổng sức chứa: <strong className="text-slate-900">{newUnits.reduce((acc, u) => acc + (u.capacity || 0), 0)}</strong> khách
                    </span>
                    <span>({newUnits.length} phương tiện)</span>
                  </div>
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <AtlasButton
                  variant="outline"
                  size="sm"
                  onClick={() => setIsCreateModalOpen(false)}
                >
                  Hủy
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={isSubmittingSlot}
                >
                  {isSubmittingSlot ? "Đang tạo..." : "Xác nhận tạo ca"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* Modal: Edit Slot Capacity / Status */}
      {editingSlot && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/90 w-full max-w-sm p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-slate-900">Cấu hình ca vận hành</h3>
              <button onClick={() => setEditingSlot(null)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveCapacity} className="space-y-3 text-sm">
              <div>
                <p className="text-slate-600 text-xs">
                  Thời gian: <strong className="text-slate-900">{editingSlot.startTime.substring(0, 5)} - {editingSlot.endTime.substring(0, 5)}</strong> ({editingSlot.date})
                </p>
                <p className="text-slate-600 text-xs mt-1">
                  Số khách đã đặt: <strong className="text-emerald-700 font-bold">{editingSlot.bookedCount || 0} khách</strong>
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Trạng thái ca:</label>
                <select
                  value={inputStatus}
                  onChange={(e) => setInputStatus(e.target.value as SlotStatus)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 focus:ring-2 focus:ring-slate-900 focus:outline-none bg-white cursor-pointer text-sm"
                >
                  <option value="OPEN">Mở đón khách (OPEN)</option>
                  <option value="CLOSED">Đóng ca (CLOSED)</option>
                </select>
              </div>

              {editingSlot.inventoryType === "PERSON_LIMIT" && (
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Tổng sức chứa mới:
                  </label>
                  <input
                    type="number"
                    min={editingSlot.bookedCount || 1}
                    value={inputCapacity}
                    onChange={(e) => setInputCapacity(Number(e.target.value))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-slate-900 font-bold focus:ring-2 focus:ring-slate-900 focus:outline-none text-sm"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    Không được đặt nhỏ hơn số khách đã đặt ({editingSlot.bookedCount || 0} khách).
                  </p>
                </div>
              )}

              {capacityError && (
                <p className="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs">
                  {capacityError}
                </p>
              )}

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <AtlasButton
                  variant="outline"
                  size="sm"
                  onClick={() => setEditingSlot(null)}
                >
                  Hủy
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={isUpdatingSlot}
                >
                  {isUpdatingSlot ? "Đang lưu..." : "Lưu thay đổi"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* Modal: Batch Create Slots */}
      <BatchCreateSlotsModal
        isOpen={isBatchModalOpen}
        onClose={() => setIsBatchModalOpen(false)}
        serviceId={selectedServiceId}
        serviceName={selectedServiceObj?.name || ""}
        capacityPerSlot={selectedServiceObj?.capacityPerSlot}
        onSuccess={() => {
          if (selectedServiceId) {
            setIsLoadingSlots(true);
            loadSlots(selectedServiceId);
          }
        }}
      />
    </div>
  );
}
