import React, { useState, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { useSearchParams, useNavigate } from "react-router-dom";
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

export function Schedule() {
  const navigate = useNavigate();
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
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [newDate, setNewDate] = useState<string>(todayStr);
  const [newStartTime, setNewStartTime] = useState<string>("06:00");
  const [newEndTime, setNewEndTime] = useState<string>("08:00");
  const [newInventoryType, setNewInventoryType] = useState<InventoryType>("PERSON_LIMIT");
  const [newCapacity, setNewCapacity] = useState<number>(10);
  const [newUnits, setNewUnits] = useState<CreateSlotUnitRequest[]>([
    { unitNumber: 1, capacity: 5 },
    { unitNumber: 2, capacity: 5 },
  ]);
  const [isSubmittingSlot, setIsSubmittingSlot] = useState(false);

  // Modal State: Edit Slot
  const [editingSlot, setEditingSlot] = useState<VendorServiceSlotDto | null>(null);
  const [inputCapacity, setInputCapacity] = useState<number>(10);
  const [inputStatus, setInputStatus] = useState<SlotStatus>("OPEN");
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [isUpdatingSlot, setIsUpdatingSlot] = useState(false);

  // 1. Fetch vendor services
  useEffect(() => {
    setIsLoadingServices(true);
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
  const loadSlots = useCallback(async (serviceId: string) => {
    if (!serviceId) {
      setSlots([]);
      return;
    }
    setIsLoadingSlots(true);
    setSlotsErrorMessage(null);
    try {
      const data = await vendorService.getSlots(serviceId);
      setSlots(data || []);
    } catch (err: unknown) {
      console.error("[Schedule] Lỗi nạp danh sách ca:", err);
      const msg = err instanceof Error ? err.message : "Không thể nạp lịch ca";
      setSlotsErrorMessage(msg);
    } finally {
      setIsLoadingSlots(false);
    }
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
    try {
      const updated = await vendorService.updateSlot(selectedServiceId, editingSlot.id, {
        status: inputStatus,
        capacity: editingSlot.inventoryType === "PERSON_LIMIT" ? inputCapacity : undefined,
      });
      setSlots((prev) => prev.map((s) => (s.id === editingSlot.id ? updated : s)));
      showToast(`Đã cập nhật cấu hình ca thành công!`);
      setEditingSlot(null);
    } catch (err: unknown) {
      console.error("[Schedule] Lỗi sửa ca:", err);
      const msg = err instanceof Error ? err.message : "Cập nhật ca thất bại";
      setCapacityError(msg);
    } finally {
      setIsUpdatingSlot(false);
    }
  };

  // Create Slot
  const handleCreateSlot = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedServiceId) {
      toast.warning("Vui lòng chọn dịch vụ trước khi tạo ca.");
      return;
    }

    if (newEndTime <= newStartTime) {
      toast.warning("Giờ kết thúc ca biển phải sau giờ bắt đầu!");
      return;
    }

    // Format start/end time HH:mm:ss if needed
    const formatTime = (t: string) => (t.length === 5 ? `${t}:00` : t);

    setIsSubmittingSlot(true);
    try {
      const payload = {
        date: newDate,
        startTime: formatTime(newStartTime),
        endTime: formatTime(newEndTime),
        inventoryType: newInventoryType,
        capacity: newInventoryType === "PERSON_LIMIT" ? newCapacity : undefined,
        units: newInventoryType === "SHARED_CAPACITY_UNITS" ? newUnits : undefined,
      };

      const created = await vendorService.createSlot(selectedServiceId, payload);
      setSlots((prev) => [...prev, created]);
      showToast(`Đã tạo ca khởi hành mới (${newStartTime} - ${newEndTime}) thành công!`);
      setIsCreateModalOpen(false);
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
    <div className="w-full bg-background flex-1">
      <div className="flex flex-col w-full px-6 py-4 space-y-4">
        {/* Header Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-300">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-black tracking-tight">
              Lịch Dịch vụ &amp; Sức chứa
            </h1>
            <p className="text-xs text-slate-600 mt-0.5">
              Cấu hình các ca vận hành biển, quản lý tồn chỗ, đóng/mở ca và bảo vệ chỗ đã cam kết với du khách
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsCreateModalOpen(true)}
              disabled={!selectedServiceId}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors shadow-xs disabled:opacity-50"
              type="button"
            >
              <span className="material-symbols-outlined text-[16px]">add_circle</span>
              <span>Tạo ca khởi hành mới</span>
            </button>
          </div>
        </div>

        {/* Filter & Service Selection Panel */}
        <div className="p-3.5 rounded-xl bg-white border border-slate-300 shadow-xs flex flex-col md:flex-row items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
            {/* Service Dropdown */}
            <div className="flex items-center gap-2 bg-slate-50 border border-slate-300 px-3 py-1.5 rounded-lg min-w-[240px]">
              <span className="material-symbols-outlined text-slate-600 text-[18px]">surfing</span>
              <select
                value={selectedServiceId}
                onChange={(e) => setSelectedServiceId(e.target.value)}
                className="bg-transparent text-black text-xs font-bold focus:outline-none cursor-pointer w-full"
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
            <div className="flex items-center gap-2 bg-slate-50 border border-slate-300 px-3 py-1.5 rounded-lg">
              <span className="material-symbols-outlined text-slate-600 text-[18px]">calendar_today</span>
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="bg-transparent text-black text-xs font-semibold focus:outline-none cursor-pointer"
              />
              {selectedDate && (
                <button
                  onClick={() => setSelectedDate("")}
                  className="text-slate-400 hover:text-black"
                  title="Xem tất cả ngày"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[14px]">close</span>
                </button>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => selectedServiceId && loadSlots(selectedServiceId)}
              className="p-1.5 rounded-lg bg-slate-100 border border-slate-300 text-slate-700 hover:bg-slate-200 transition-colors"
              title="Làm mới lịch ca"
              type="button"
            >
              <span className="material-symbols-outlined text-[16px]">refresh</span>
            </button>
          </div>
        </div>

        {/* Error Fallback */}
        {slotsErrorMessage && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-between text-rose-800 text-xs">
            <span>Lỗi tải danh sách ca: {slotsErrorMessage}</span>
            <button
              onClick={() => selectedServiceId && loadSlots(selectedServiceId)}
              className="px-2.5 py-1 bg-rose-600 text-white rounded font-bold hover:bg-rose-700"
              type="button"
            >
              Thử lại
            </button>
          </div>
        )}

        {/* Loading Skeleton */}
        {isLoadingSlots && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-pulse">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-44 bg-slate-100 rounded-xl border border-slate-200"></div>
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
                      className={`rounded-xl border p-4 bg-white shadow-xs transition-all flex flex-col justify-between ${
                        isClosed ? "border-slate-200 opacity-70 bg-slate-50" : "border-slate-200 hover:border-primary/50"
                      }`}
                    >
                      <div className="space-y-2">
                        {/* Time & Status Badge */}
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-1.5">
                            <span className="material-symbols-outlined text-primary text-[18px]">schedule</span>
                            <span className="font-bold text-sm text-black">
                              {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                            </span>
                          </div>

                          <div>
                            {isClosed ? (
                              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-200 text-slate-700">
                                Đã đóng
                              </span>
                            ) : isFull ? (
                              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800">
                                Hết chỗ
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">
                                Đang mở
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Date & Inventory Type */}
                        <div className="text-xs text-slate-600 flex items-center justify-between">
                          <span>Ngày: <strong className="text-black">{slot.date}</strong></span>
                          <span className="text-[11px] px-1.5 py-0.2 bg-slate-100 rounded text-slate-600">
                            {slot.inventoryType === "SHARED_CAPACITY_UNITS" ? "Đơn vị chia sẻ" : "Theo đầu người"}
                          </span>
                        </div>

                        {/* Capacity Progress Bar */}
                        <div className="space-y-1 pt-1">
                          <div className="flex items-center justify-between text-xs">
                            <span className="text-slate-500">Đã đặt:</span>
                            <span className="font-bold text-black">
                              {booked} / {slot.capacity} khách
                            </span>
                          </div>
                          <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all ${
                                isFull ? "bg-rose-500" : "bg-emerald-500"
                              }`}
                              style={{ width: `${Math.min(100, (booked / Math.max(1, slot.capacity)) * 100)}%` }}
                            ></div>
                          </div>
                          <div className="flex justify-between text-[11px] text-slate-500">
                            <span>Còn trống: <strong className="text-emerald-700">{Math.max(0, available)}</strong></span>
                          </div>
                        </div>

                        {/* Units Details if Shared Capacity Units */}
                        {slot.units && slot.units.length > 0 && (
                          <div className="pt-2 border-t border-slate-100 space-y-1">
                            <p className="text-[11px] font-semibold text-slate-700">Chi tiết phương tiện / units:</p>
                            <div className="flex flex-wrap gap-1.5">
                              {slot.units.map((u) => (
                                <span
                                  key={u.id}
                                  className="text-[10px] px-1.5 py-0.5 bg-slate-100 border border-slate-200 rounded text-slate-700"
                                >
                                  Cano #{u.unitNumber}: {u.bookedCount}/{u.capacity}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Actions */}
                      <div className="pt-3 mt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                        <button
                          onClick={() => handleToggleStatus(slot)}
                          className={`px-2.5 py-1 rounded text-xs font-semibold transition-colors flex items-center gap-1 ${
                            isClosed
                              ? "bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                              : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                          }`}
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[14px]">
                            {isClosed ? "lock_open" : "lock"}
                          </span>
                          <span>{isClosed ? "Mở lại ca" : "Đóng ca"}</span>
                        </button>

                        <button
                          onClick={() => handleOpenEditCapacity(slot)}
                          className="px-2.5 py-1 rounded border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors flex items-center gap-1"
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[14px]">edit</span>
                          <span>Cấu hình</span>
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              /* Empty State */
              <div className="w-full bg-white rounded-xl border border-slate-200 p-12 text-center">
                <div className="flex flex-col items-center justify-center space-y-2 text-slate-500">
                  <span className="material-symbols-outlined text-4xl text-slate-400">event_busy</span>
                  <p className="font-semibold text-slate-700">Chưa có ca khởi hành nào</p>
                  <p className="text-xs text-slate-500 max-w-sm">
                    {selectedServiceObj
                      ? `Dịch vụ "${selectedServiceObj.name}" chưa có ca khởi hành trong ngày đã chọn. Hãy tạo ca mới để bắt đầu đón khách!`
                      : "Vui lòng chọn một dịch vụ từ danh sách phía trên."}
                  </p>
                  {selectedServiceId && (
                    <button
                      onClick={() => setIsCreateModalOpen(true)}
                      className="mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors"
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[16px]">add</span>
                      <span>Tạo ca khởi hành mới</span>
                    </button>
                  )}
                </div>
              </div>
            )}
          </>
        )}

        {/* Modal: Create Slot */}
        {isCreateModalOpen && createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-md p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-sm text-black">Tạo ca khởi hành mới</h3>
                <button onClick={() => setIsCreateModalOpen(false)} className="text-slate-400 hover:text-black">
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>

              <form onSubmit={handleCreateSlot} className="space-y-3 text-xs">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Dịch vụ:</label>
                  <input
                    type="text"
                    disabled
                    value={selectedServiceObj?.name || "Chưa chọn"}
                    className="w-full px-2.5 py-1.5 bg-slate-100 border border-slate-200 rounded-lg text-slate-700 font-bold"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Ngày diễn ra:</label>
                  <input
                    type="date"
                    required
                    min={todayStr}
                    value={newDate}
                    onChange={(e) => setNewDate(e.target.value)}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Giờ bắt đầu:</label>
                    <input
                      type="time"
                      required
                      value={newStartTime}
                      onChange={(e) => setNewStartTime(e.target.value)}
                      className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none font-bold"
                    />
                  </div>
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Giờ kết thúc:</label>
                    <input
                      type="time"
                      required
                      value={newEndTime}
                      onChange={(e) => setNewEndTime(e.target.value)}
                      className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none font-bold"
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Mô hình tồn chỗ:</label>
                  <select
                    value={newInventoryType}
                    onChange={(e) => setNewInventoryType(e.target.value as InventoryType)}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none bg-white cursor-pointer"
                  >
                    <option value="PERSON_LIMIT">Giới hạn theo đầu người (PERSON_LIMIT)</option>
                    <option value="SHARED_CAPACITY_UNITS">Chia theo đơn vị/cano (SHARED_CAPACITY_UNITS)</option>
                  </select>
                </div>

                {newInventoryType === "PERSON_LIMIT" ? (
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">Tổng sức chứa (khách):</label>
                    <input
                      type="number"
                      required
                      min={1}
                      value={newCapacity}
                      onChange={(e) => setNewCapacity(Number(e.target.value))}
                      className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none font-bold"
                    />
                  </div>
                ) : (
                  <div className="space-y-2 p-2 bg-slate-50 border border-slate-200 rounded-lg">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-slate-700">Danh sách cano / units:</span>
                      <button
                        type="button"
                        onClick={() =>
                          setNewUnits((prev) => [
                            ...prev,
                            { unitNumber: prev.length + 1, capacity: 5 },
                          ])
                        }
                        className="text-primary font-bold hover:underline"
                      >
                        + Thêm cano
                      </button>
                    </div>
                    {newUnits.map((u, idx) => (
                      <div key={idx} className="flex items-center gap-2">
                        <span className="text-slate-600 font-medium">#{u.unitNumber}</span>
                        <input
                          type="number"
                          min={1}
                          value={u.capacity}
                          onChange={(e) => {
                            const val = Number(e.target.value);
                            setNewUnits((prev) =>
                              prev.map((item, i) => (i === idx ? { ...item, capacity: val } : item))
                            );
                          }}
                          className="w-24 px-2 py-1 border border-slate-300 rounded bg-white text-xs"
                          placeholder="Sức chứa"
                        />
                        <span className="text-slate-500">khách</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    onClick={() => setIsCreateModalOpen(false)}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    type="button"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmittingSlot}
                    className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-bold hover:bg-primary/90 transition-colors disabled:opacity-50"
                  >
                    {isSubmittingSlot ? "Đang tạo..." : "Xác nhận tạo ca"}
                  </button>
                </div>
              </form>
            </div>
          </div>,
          document.body
        )}

        {/* Modal: Edit Slot Capacity / Status */}
        {editingSlot && createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-xl shadow-lg border border-slate-200 w-full max-w-sm p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-sm text-black">Cấu hình ca vận hành</h3>
                <button onClick={() => setEditingSlot(null)} className="text-slate-400 hover:text-black">
                  <span className="material-symbols-outlined text-[18px]">close</span>
                </button>
              </div>

              <form onSubmit={handleSaveCapacity} className="space-y-3 text-xs">
                <div>
                  <p className="text-slate-600">
                    Thời gian: <strong className="text-black">{editingSlot.startTime.substring(0, 5)} - {editingSlot.endTime.substring(0, 5)}</strong> ({editingSlot.date})
                  </p>
                  <p className="text-slate-600 mt-0.5">
                    Số khách đã đặt: <strong className="text-emerald-700">{editingSlot.bookedCount || 0} khách</strong>
                  </p>
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Trạng thái ca:</label>
                  <select
                    value={inputStatus}
                    onChange={(e) => setInputStatus(e.target.value as SlotStatus)}
                    className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black focus:ring-1 focus:ring-primary focus:outline-none bg-white cursor-pointer"
                  >
                    <option value="OPEN">Mở đón khách (OPEN)</option>
                    <option value="CLOSED">Đóng ca (CLOSED)</option>
                  </select>
                </div>

                {editingSlot.inventoryType === "PERSON_LIMIT" && (
                  <div>
                    <label className="block font-semibold text-slate-700 mb-1">
                      Tổng sức chứa mới:
                    </label>
                    <input
                      type="number"
                      min={editingSlot.bookedCount || 1}
                      value={inputCapacity}
                      onChange={(e) => setInputCapacity(Number(e.target.value))}
                      className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-black font-bold focus:ring-1 focus:ring-primary focus:outline-none"
                    />
                    <p className="text-[11px] text-slate-500 mt-0.5">
                      Không được đặt nhỏ hơn số khách đã đặt ({editingSlot.bookedCount || 0} khách).
                    </p>
                  </div>
                )}

                {capacityError && (
                  <p className="p-2 bg-rose-50 border border-rose-200 rounded text-rose-700 text-[11px]">
                    {capacityError}
                  </p>
                )}

                <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                  <button
                    onClick={() => setEditingSlot(null)}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    type="button"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={isUpdatingSlot}
                    className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-bold hover:bg-primary/90 transition-colors disabled:opacity-50"
                  >
                    {isUpdatingSlot ? "Đang lưu..." : "Lưu thay đổi"}
                  </button>
                </div>
              </form>
            </div>
          </div>,
          document.body
        )}

        
      </div>
    </div>
  );
}
