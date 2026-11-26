import React, { useState, useEffect, useCallback, useMemo } from "react";
import { createPortal } from "react-dom";
import { Link, useSearchParams } from "react-router-dom";
import axios from "axios";
import { vendorService, type OperatingScheduleExceptionDto } from "../../../services/vendorService";
import { vendorEquipmentService, type BackendEquipment } from "../../../services/vendorEquipmentService";
import { toast } from "../../../store/useToastStore";
import { extractErrorMessage } from "../../../utils/errorHandler";
import type {
  VendorServiceDto,
  VendorServiceSlotDto,
  InventoryType,
  SlotStatus,
  CreateSlotUnitRequest,
  CreateServiceSlotRequest,
  UpdateServiceSlotRequest,
  VendorServiceOptionDto,
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
  AlertTriangle,
  Layers,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Ship,
  Users,
  Check,
  CalendarOff,
  Trash2,
  Loader2,
} from "lucide-react";
import {
  AtlasPageHeader,
  AtlasBadge,
  AtlasButton,
} from "../../../components/vendor/atlas/AtlasDesignSystem";
import { SlotAdvisorBanner } from "./SlotAdvisorBanner";
import { BatchCreateSlotsModal } from "./BatchCreateSlotsModal";

function getServiceStatusLabel(status: string) {
  switch (status) {
    case "PUBLISHED":
      return "Đang mở bán";
    case "PENDING_REVIEW":
      return "Chờ duyệt";
    case "DRAFT":
      return "Bản nháp";
    case "REJECTED":
      return "Bị từ chối";
    case "PAUSED":
      return "Tạm ngưng";
    default:
      return status;
  }
}

function formatOptionPriceUnit(price: number, pricingUnit: string, optionType: string) {
  const unitLabel = pricingUnit === "PER_PACKAGE" || optionType === "PRIVATE" ? "/gói" : "/người";
  return `${price.toLocaleString("vi-VN")} VNĐ${unitLabel}`;
}

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
  const dateInVietnam = useMemo(
    () => new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" }),
    []
  );
  const todayStr = useMemo(() => dateInVietnam.format(new Date()), [dateInVietnam]);
  const [selectedDate, setSelectedDate] = useState<string>(todayStr);
  const [nowTimestamp] = useState(() => Date.now());

  // Equipment Fleet State
  const [equipmentList, setEquipmentList] = useState<BackendEquipment[]>([]);

  // Modal / Drawer State
  const [createMenuOpen, setCreateMenuOpen] = useState(false);
  const [isCreateDrawerOpen, setIsCreateDrawerOpen] = useState(false);
  const [isBatchModalOpen, setIsBatchModalOpen] = useState(false);

  // Form State: Create Slot
  interface UnitWithEquipment extends CreateSlotUnitRequest {
    equipmentId?: string;
  }
  const [newDate, setNewDate] = useState<string>(todayStr);
  const [newStartTime, setNewStartTime] = useState<string>("08:00");
  const [newEndTime, setNewEndTime] = useState<string>("10:00");
  const [newInventoryType, setNewInventoryType] = useState<InventoryType>("PERSON_LIMIT");
  const [newCapacity, setNewCapacity] = useState<number>(10);
  const [newUnits, setNewUnits] = useState<UnitWithEquipment[]>([
    { unitNumber: 1, capacity: 5 },
    { unitNumber: 2, capacity: 5 },
  ]);
  const [repeatDays, setRepeatDays] = useState<number>(1);
  const [isSubmittingSlot, setIsSubmittingSlot] = useState(false);

  // Options State (pre-configured in Service Form Tab 3)
  const [serviceOptions, setServiceOptions] = useState<VendorServiceOptionDto[]>([]);
  const [isLoadingOptions, setIsLoadingOptions] = useState<boolean>(false);
  const [newOptionIds, setNewOptionIds] = useState<string[]>([]);
  const [slotFormErrors, setSlotFormErrors] = useState<Record<string, string>>({});

  // Modal State: Edit Slot
  const [editingSlot, setEditingSlot] = useState<VendorServiceSlotDto | null>(null);
  const [inputCapacity, setInputCapacity] = useState<number>(10);
  const [inputStatus, setInputStatus] = useState<SlotStatus>("OPEN");
  const [editUnits, setEditUnits] = useState<UnitWithEquipment[]>([]);
  const [editOptionIds, setEditOptionIds] = useState<string[]>([]);
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [isUpdatingSlot, setIsUpdatingSlot] = useState(false);

  // Modal State: Cancel Departure (Bão biển / Sự cố)
  const [cancellingSlot, setCancellingSlot] = useState<VendorServiceSlotDto | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [isSubmittingCancel, setIsSubmittingCancel] = useState(false);

  // Modal State: Operating Schedule Exceptions (Ngày nghỉ / Ngoại lệ)
  const [isExceptionsModalOpen, setIsExceptionsModalOpen] = useState(false);
  const [exceptions, setExceptions] = useState<OperatingScheduleExceptionDto[]>([]);
  const [isLoadingExceptions, setIsLoadingExceptions] = useState(false);
  const [newExDateFrom, setNewExDateFrom] = useState(todayStr);
  const [newExDateTo, setNewExDateTo] = useState(todayStr);
  const [newExReason, setNewExReason] = useState("");
  const [isSubmittingException, setIsSubmittingException] = useState(false);

  const selectedServiceObj = useMemo(() => {
    return services.find((s) => s.id === selectedServiceId);
  }, [services, selectedServiceId]);

  // Load Services & Equipment
  useEffect(() => {
    let isMounted = true;
    setIsLoadingServices(true);
    vendorService
      .getMyServices()
      .then((data) => {
        if (!isMounted) return;
        setServices(data || []);
        if (data && data.length > 0) {
          if (queryServiceId && data.some((s) => s.id === queryServiceId)) {
            setSelectedServiceId(queryServiceId);
          } else {
            setSelectedServiceId(data[0].id);
          }
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        console.error("[Schedule] Lỗi nạp danh sách dịch vụ:", err);
        toast.error("Không thể tải danh sách dịch vụ.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingServices(false);
      });

    vendorEquipmentService
      .getEquipment()
      .then((data) => {
        if (!isMounted) return;
        setEquipmentList(data || []);
      })
      .catch((err) => {
        console.warn("[Schedule] Lỗi nạp thiết bị:", err);
        if (isMounted) setEquipmentList([]);
      });

    return () => {
      isMounted = false;
    };
  }, [queryServiceId]);

  // Load ACTIVE Options for selected service
  const loadOptions = useCallback((serviceId: string) => {
    setIsLoadingOptions(true);
    vendorService
      .getOptions(serviceId)
      .then((data) => {
        const active = (data || []).filter((o) => o.status === "ACTIVE");
        setServiceOptions(active);
        // Only keep previously selected active options; NEVER auto-select all
        setNewOptionIds((prev) => prev.filter((id) => active.some((o) => o.id === id)));
      })
      .catch((err) => {
        console.error("[Schedule] Lỗi nạp gói dịch vụ:", err);
        setServiceOptions([]);
        setNewOptionIds([]);
      })
      .finally(() => {
        setIsLoadingOptions(false);
      });
  }, []);

  // Load Slots for selected service
  const loadSlots = useCallback((serviceId: string) => {
    if (!serviceId) return;
    setIsLoadingSlots(true);
    setSlotsErrorMessage(null);

    vendorService
      .getSlots(serviceId)
      .then((data) => {
        setSlots(data || []);
      })
      .catch((err: unknown) => {
        console.error("[Schedule] Lỗi tải danh sách ca:", err);
        const msg = extractErrorMessage(err, "Không thể tải danh sách ca khởi hành.");
        setSlotsErrorMessage(msg);
        setSlots([]);
      })
      .finally(() => {
        setIsLoadingSlots(false);
      });
  }, []);

  useEffect(() => {
    if (selectedServiceId) {
      loadSlots(selectedServiceId);
      loadOptions(selectedServiceId);
    } else {
      setSlots([]);
      setServiceOptions([]);
      setNewOptionIds([]);
    }
  }, [selectedServiceId, loadSlots, loadOptions]);

  // Filter slots by selected date
  const filteredSlots = useMemo(() => {
    if (!selectedDate) return slots;
    return slots.filter((s) => s.date === selectedDate);
  }, [slots, selectedDate]);

  // Generate 7-day strip centered around selectedDate (or today)
  const weekDays = useMemo(() => {
    const baseDate = selectedDate ? new Date(selectedDate) : new Date(todayStr);
    const dayOfWeek = baseDate.getDay();
    const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const monday = new Date(baseDate);
    monday.setDate(baseDate.getDate() + mondayOffset);

    const days = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      const isoStr = d.toISOString().split("T")[0];
      const slotCount = slots.filter((s) => s.date === isoStr).length;
      const dayNames = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
      days.push({
        iso: isoStr,
        label: dayNames[d.getDay()],
        dayNum: d.getDate(),
        monthNum: d.getMonth() + 1,
        slotCount,
        isToday: isoStr === todayStr,
        isSelected: isoStr === selectedDate,
      });
    }
    return days;
  }, [selectedDate, todayStr, slots]);

  const handleNextDay = () => {
    const cur = new Date(selectedDate || todayStr);
    cur.setDate(cur.getDate() + 1);
    setSelectedDate(cur.toISOString().split("T")[0]);
  };

  const handlePrevDay = () => {
    const cur = new Date(selectedDate || todayStr);
    cur.setDate(cur.getDate() - 1);
    setSelectedDate(cur.toISOString().split("T")[0]);
  };

  const handleToggleNewOption = (optionId: string) => {
    setNewOptionIds((prev) => {
      const exists = prev.includes(optionId);
      const next = exists
        ? prev.filter((id) => id !== optionId)
        : Array.from(new Set([...prev, optionId]));
      const selectedOpts = serviceOptions.filter((o) => next.includes(o.id));
      if (selectedOpts.some((o) => o.optionType === "PRIVATE")) {
        setNewInventoryType("SHARED_CAPACITY_UNITS");
      }
      return next;
    });
  };

  const handleToggleEditOption = (optionId: string) => {
    setEditOptionIds((prev) => {
      const exists = prev.includes(optionId);
      if (exists && prev.length <= 1) {
        toast.warning("Ca vận hành phải có ít nhất một gói bán.");
        return prev;
      }
      return exists ? prev.filter((id) => id !== optionId) : Array.from(new Set([...prev, optionId]));
    });
  };

  const handleToggleStatus = async (slot: VendorServiceSlotDto) => {
    if (!selectedServiceId) return;
    if (slot.date < todayStr) {
      toast.error("Ca đã qua, không thể chỉnh sửa.");
      return;
    }
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
      console.error("[Schedule] Lỗi đổi trạng thái ca:", err);
      toast.error(extractErrorMessage(err, "Đổi trạng thái ca thất bại"));
    }
  };

  const handleOpenEditCapacity = (slot: VendorServiceSlotDto) => {
    if (slot.date < todayStr) {
      toast.error("Ca đã qua, không thể chỉnh sửa.");
      return;
    }
    setEditingSlot(slot);
    setInputCapacity(slot.capacity);
    setInputStatus(slot.status);
    const existingOptIds =
      slot.options && slot.options.length > 0
        ? slot.options.map((o) => o.id)
        : slot.optionId
          ? [slot.optionId]
          : [];
    setEditOptionIds(existingOptIds);
    setEditUnits(
      slot.units && slot.units.length > 0
        ? slot.units.map((u) => ({
            unitNumber: u.unitNumber,
            capacity: u.capacity,
            equipmentId: u.equipmentId || undefined,
          }))
        : [{ unitNumber: 1, capacity: slot.capacity || 6 }]
    );
    setCapacityError(null);
  };

  const handleSaveCapacity = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSlot || !selectedServiceId) return;
    if (editingSlot.date < todayStr) {
      setCapacityError("Ca đã qua, không thể chỉnh sửa.");
      return;
    }

    if (editOptionIds.length === 0) {
      setCapacityError("Ca vận hành phải có ít nhất một gói bán.");
      return;
    }

    const booked = editingSlot.bookedCount || 0;
    if (editingSlot.inventoryType === "PERSON_LIMIT") {
      if (inputCapacity < booked) {
        setCapacityError(
          `Không thể giảm sức chứa xuống dưới ${booked} vì đã có ${booked} khách đặt thành công!`
        );
        return;
      }
    } else {
      const sumUnits = editUnits.reduce((acc, u) => acc + (u.capacity || 0), 0);
      if (sumUnits < booked) {
        setCapacityError(
          `Tổng sức chứa các phương tiện (${sumUnits}) không thể nhỏ hơn số khách đã đặt (${booked})!`
        );
        return;
      }
    }

    setIsUpdatingSlot(true);
    setCapacityError(null);
    try {
      const payload: UpdateServiceSlotRequest = {
        status: inputStatus,
        optionIds: Array.from(new Set(editOptionIds)),
        ...(editingSlot.inventoryType === "SHARED_CAPACITY_UNITS"
          ? {
              units: editUnits.map((u) => ({ unitNumber: u.unitNumber, capacity: u.capacity })),
            }
          : { capacity: inputCapacity }),
      };

      await vendorService.updateSlot(selectedServiceId, editingSlot.id, payload);

      await loadSlots(selectedServiceId);
      showToast("Cập nhật ca khởi hành thành công!");
      setEditingSlot(null);
    } catch (err: unknown) {
      if (selectedServiceId) {
        await loadSlots(selectedServiceId);
      }
      console.error("[Schedule] Lỗi sửa ca:", err);
      let msg = extractErrorMessage(err, "Cập nhật ca thất bại");
      if (axios.isAxiosError(err)) {
        const rawMsg = err.response?.data?.message || err.response?.data?.error || "";
        if (typeof rawMsg === "string") {
          if (rawMsg.includes("Cannot resize a unit linked to physical equipment")) {
            msg = "Không thể thay đổi sức chứa của đơn vị đã liên kết thiết bị.";
          } else if (
            rawMsg.includes("Private options require shared capacity units") ||
            rawMsg.includes("private option maximum capacity")
          ) {
            msg = "Gói thuê riêng yêu cầu ít nhất một đơn vị có đủ sức chứa tối đa của gói.";
          } else if (rawMsg.includes("Cannot reduce unit") || rawMsg.includes("below committed capacity")) {
            msg = "Không thể giảm sức chứa đơn vị xuống dưới số chỗ khách đã giữ hoặc đặt cọc.";
          } else if (rawMsg.includes("linked to physical equipment must be managed by equipment maintenance")) {
            msg = "Đơn vị đã liên kết thiết bị không thể xóa trực tiếp khỏi ca. Vui lòng quản lý qua lịch bảo trì thiết bị.";
          } else if (rawMsg.includes("Physical equipment already assigned to an overlapping slot")) {
            msg = "Thiết bị này đã được gán cho một ca khác trùng khung giờ.";
          } else if (
            rawMsg.includes("committed booking or active hold") ||
            rawMsg.includes("Only an empty, unassigned slot unit")
          ) {
            msg = "Không thể gỡ hoặc thay đổi thiết bị khi đơn vị đang có khách giữ chỗ (HOLD) hoặc đã đặt.";
          }
        }
      }
      setCapacityError(msg);
      toast.error(msg);
    } finally {
      setIsUpdatingSlot(false);
    }
  };

  // Exceptions management
  const loadExceptions = useCallback(async (serviceId: string) => {
    if (!serviceId) return;
    setIsLoadingExceptions(true);
    try {
      const data = await vendorService.getOperatingScheduleExceptions(serviceId);
      setExceptions(data || []);
    } catch (err) {
      console.error("[Schedule] Lỗi nạp lịch ngoại lệ:", err);
    } finally {
      setIsLoadingExceptions(false);
    }
  }, []);

  const handleOpenExceptions = () => {
    if (!selectedServiceId) {
      toast.warning("Vui lòng chọn một dịch vụ để quản lý lịch ngoại lệ.");
      return;
    }
    setIsExceptionsModalOpen(true);
    loadExceptions(selectedServiceId);
  };

  const handleAddException = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedServiceId || !newExReason.trim()) return;
    setIsSubmittingException(true);
    try {
      await vendorService.addOperatingScheduleException(selectedServiceId, {
        dateFrom: newExDateFrom,
        dateTo: newExDateTo,
        reason: newExReason.trim(),
      });
      toast.success("Đã thêm ngày nghỉ / lịch ngoại lệ thành công!");
      setNewExReason("");
      loadExceptions(selectedServiceId);
    } catch (err) {
      toast.error(extractErrorMessage(err, "Không thể thêm ngày nghỉ ngoại lệ"));
    } finally {
      setIsSubmittingException(false);
    }
  };

  const handleRemoveException = async (exceptionId: string) => {
    if (!selectedServiceId) return;
    try {
      await vendorService.removeOperatingScheduleException(selectedServiceId, exceptionId);
      toast.success("Đã xóa ngày nghỉ ngoại lệ.");
      setExceptions((prev) => prev.filter((ex) => ex.id !== exceptionId));
    } catch (err) {
      toast.error(extractErrorMessage(err, "Không thể xóa lịch ngoại lệ"));
    }
  };

  // Departure cancellation
  const handleOpenCancelDeparture = (slot: VendorServiceSlotDto) => {
    setCancellingSlot(slot);
    setCancelReason("Bão biển / Thời tiết xấu, cảng vụ yêu cầu ngừng xuất bến để đảm bảo an toàn hàng hải.");
  };

  const handleConfirmCancelDeparture = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cancellingSlot || !selectedServiceId || !cancelReason.trim()) return;
    setIsSubmittingCancel(true);
    try {
      await vendorService.cancelSlotDeparture(selectedServiceId, cancellingSlot.id, cancelReason.trim());
      toast.success("Đã xác nhận hủy ca xuất bến và kích hoạt đối soát hoàn tiền cho khách!");
      setCancellingSlot(null);
      loadSlots(selectedServiceId);
    } catch (err) {
      toast.error(extractErrorMessage(err, "Hủy ca xuất bến thất bại"));
    } finally {
      setIsSubmittingCancel(false);
    }
  };

  // Create Slot
  const handleCreateSlot = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedServiceId) {
      toast.warning("Vui lòng chọn một dịch vụ trước khi tạo ca.");
      return;
    }

    if (selectedServiceObj?.status !== "PUBLISHED") {
      toast.error(
        `Dịch vụ hiện ở trạng thái "${getServiceStatusLabel(selectedServiceObj?.status || "")}". Chỉ dịch vụ đã mở bán mới có thể tạo ca.`
      );
      return;
    }

    const errors: Record<string, string> = {};
    if (!newStartTime || !newEndTime) {
      errors.time = "Vui lòng nhập đầy đủ giờ bắt đầu và kết thúc.";
    } else if (newStartTime >= newEndTime) {
      errors.time = "Giờ bắt đầu phải trước giờ kết thúc.";
    }

    if (newDate === todayStr) {
      const nowH = String(new Date().getHours()).padStart(2, "0");
      const nowM = String(new Date().getMinutes()).padStart(2, "0");
      const nowTime = `${nowH}:${nowM}`;
      if (newStartTime <= nowTime) {
        errors.time = `Giờ bắt đầu (${newStartTime}) phải sau thời điểm hiện tại (${nowTime}).`;
      }
    }

    if (newOptionIds.length === 0) {
      errors.options = "Vui lòng chọn ít nhất một gói bán cho ca này.";
    }

    const selectedOptions = serviceOptions.filter((o) => newOptionIds.includes(o.id));
    const privateOptions = selectedOptions.filter((o) => o.optionType === "PRIVATE");
    const hasPrivate = privateOptions.length > 0;
    if (hasPrivate && newInventoryType !== "SHARED_CAPACITY_UNITS") {
      errors.inventory = "Ca có gói thuê riêng bắt buộc phải phân bổ theo từng phương tiện.";
    }

    if (newInventoryType === "SHARED_CAPACITY_UNITS") {
      if (newUnits.length === 0 || newUnits.some((u) => !u.capacity || u.capacity <= 0)) {
        errors.units = "Mỗi phương tiện phải có sức chứa lớn hơn 0.";
      } else if (hasPrivate) {
        const maxPax = Math.max(...privateOptions.map((o) => o.maxPaxPerPackage || 1));
        if (!newUnits.some((u) => (u.capacity || 0) >= maxPax)) {
          errors.units = `Gói thuê riêng yêu cầu tối đa ${maxPax} khách. Cần ít nhất 1 phương tiện có sức chứa >= ${maxPax} chỗ.`;
        }
      }
    } else {
      if (!newCapacity || newCapacity <= 0) {
        errors.capacity = "Sức chứa tối đa phải lớn hơn 0.";
      }
    }

    if (Object.keys(errors).length > 0) {
      setSlotFormErrors(errors);
      return;
    }
    setSlotFormErrors({});

    const uniqueOptionIds = Array.from(new Set(newOptionIds));
    const cleanUnits: CreateSlotUnitRequest[] = newUnits.map((u) => ({
      unitNumber: u.unitNumber,
      capacity: u.capacity,
    }));

    setIsSubmittingSlot(true);
    try {
      const payloadBase: CreateServiceSlotRequest = {
        date: newDate,
        startTime: `${newStartTime}:00`,
        endTime: `${newEndTime}:00`,
        inventoryType: newInventoryType,
        capacity:
          newInventoryType === "PERSON_LIMIT"
            ? newCapacity
            : cleanUnits.reduce((acc, u) => acc + (u.capacity || 0), 0),
        units: newInventoryType === "SHARED_CAPACITY_UNITS" ? cleanUnits : undefined,
        optionId: uniqueOptionIds[0],
        optionIds: uniqueOptionIds,
      };

      const datesToCreate: string[] = [];
      const base = new Date(newDate);
      for (let i = 0; i < repeatDays; i++) {
        const d = new Date(base);
        d.setDate(base.getDate() + i);
        datesToCreate.push(d.toISOString().split("T")[0]);
      }

      let successCount = 0;
      let errorOccurred: unknown = null;

      for (const dStr of datesToCreate) {
        try {
          const created = await vendorService.createSlot(selectedServiceId, {
            ...payloadBase,
            date: dStr,
          });

          if (newInventoryType === "SHARED_CAPACITY_UNITS" && created?.id) {
            for (const u of newUnits) {
              if (u.equipmentId) {
                try {
                  await vendorEquipmentService.attachEquipmentToSlot(
                    u.equipmentId,
                    created.id,
                    u.unitNumber
                  );
                } catch (eqErr: unknown) {
                  const eqName =
                    equipmentList.find((item) => item.id === u.equipmentId)?.name ||
                    `Đơn vị #${u.unitNumber}`;
                  console.warn(`[Schedule] Lỗi gán thiết bị ${eqName}:`, eqErr);
                  toast.warning(
                    extractErrorMessage(
                      eqErr,
                      `Không thể gán ${eqName} (có thể trùng ca khác hoặc đang bảo trì).`
                    )
                  );
                }
              }
            }
          }

          successCount++;
        } catch (err) {
          console.warn(`[Schedule] Bỏ qua ngày ${dStr} do trùng ca hoặc lỗi:`, err);
          errorOccurred = err;
        }
      }

      await loadSlots(selectedServiceId);

      if (repeatDays > 1) {
        if (successCount > 0) {
          showToast(`Đã tạo thành công ${successCount}/${datesToCreate.length} ca khởi hành liên tiếp!`);
          setIsCreateDrawerOpen(false);
        } else {
          toast.error(extractErrorMessage(errorOccurred, "Không thể tạo chuỗi ca khởi hành"));
        }
      } else {
        if (successCount > 0) {
          showToast(`Đã tạo ca khởi hành mới (${newStartTime} - ${newEndTime}) thành công!`);
          setIsCreateDrawerOpen(false);
        } else {
          toast.error(extractErrorMessage(errorOccurred, "Không thể tạo ca khởi hành"));
        }
      }
    } catch (err: unknown) {
      console.error("[Schedule] Lỗi tạo ca:", err);
      toast.error(extractErrorMessage(err, "Không thể tạo ca khởi hành"));
    } finally {
      setIsSubmittingSlot(false);
    }
  };

  const hasPrivateOptionSelected = useMemo(() => {
    return serviceOptions
      .filter((o) => newOptionIds.includes(o.id))
      .some((o) => o.optionType === "PRIVATE");
  }, [serviceOptions, newOptionIds]);

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      {/* 1. Page Header */}
      <AtlasPageHeader
        title="Lịch khởi hành & Điều phối ca"
        actions={
          <div className="flex items-center gap-2 flex-wrap">
            <AtlasButton
              variant="outline"
              size="md"
              onClick={handleOpenExceptions}
              disabled={!selectedServiceId}
              icon={<CalendarOff className="w-4 h-4 text-slate-500" />}
            >
              Lịch ngoại lệ / Ngày nghỉ
            </AtlasButton>

            <div className="relative inline-block text-left">
              <button
                type="button"
                onClick={() => setCreateMenuOpen((prev) => !prev)}
                disabled={!selectedServiceId || selectedServiceObj?.status !== "PUBLISHED"}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-teal-800 text-white text-sm font-semibold shadow-xs hover:bg-teal-900 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Plus className="w-4 h-4" />
                <span>Tạo ca khởi hành</span>
                <ChevronDown
                  className={`w-4 h-4 transition-transform duration-150 ${createMenuOpen ? "rotate-180" : ""}`}
                />
              </button>

              {createMenuOpen && (
                <div
                  className="absolute right-0 top-full mt-2 w-64 bg-white rounded-xl shadow-lg border border-slate-200/90 py-1.5 z-50 text-left animate-in fade-in slide-in-from-top-1"
                  onMouseLeave={() => setCreateMenuOpen(false)}
                >
                  <button
                    type="button"
                    onClick={() => {
                      setCreateMenuOpen(false);
                      setIsCreateDrawerOpen(true);
                    }}
                    className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    <Clock className="w-4 h-4 text-slate-500 mt-0.5 shrink-0" />
                    <div>
                      <div className="font-semibold text-xs text-slate-900">Tạo ca đơn lẻ</div>
                      <div className="text-[11px] text-slate-500">Tạo 1 ca riêng hoặc lặp 1 - 30 ngày</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setCreateMenuOpen(false);
                      setIsBatchModalOpen(true);
                    }}
                    className="w-full px-4 py-2.5 text-left flex items-start gap-3 hover:bg-slate-50 transition-colors cursor-pointer border-t border-slate-100"
                  >
                    <Layers className="w-4 h-4 text-teal-700 mt-0.5 shrink-0" />
                    <div>
                      <div className="font-semibold text-xs text-slate-900">Tạo ca hàng loạt / Định kỳ</div>
                      <div className="text-[11px] text-slate-500">Cấu hình nhiều ca theo thứ hoặc 90 ngày</div>
                    </div>
                  </button>
                </div>
              )}
            </div>
          </div>
        }
      />

      {/* 2. AI Advisor Banner */}
      <SlotAdvisorBanner serviceId={selectedServiceId} />

      {/* 3. Unified Flat Toolbar */}
      <div className="bg-white p-3 rounded-xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
        <div className="flex-1 min-w-[280px] max-w-md">
          <label htmlFor="service-select" className="sr-only">
            Chọn dịch vụ
          </label>
          <select
            id="service-select"
            value={selectedServiceId}
            onChange={(e) => setSelectedServiceId(e.target.value)}
            className="w-full h-10 pl-3.5 pr-9 rounded-lg bg-slate-50 border border-slate-200 text-sm font-semibold text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-teal-700/20"
            disabled={isLoadingServices}
          >
            {services.length === 0 ? (
              <option value="">(Chưa có dịch vụ nào)</option>
            ) : (
              services.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} • {getServiceStatusLabel(s.status)}
                </option>
              ))
            )}
          </select>
        </div>

        <div className="flex items-center gap-1.5 self-center">
          <button
            type="button"
            onClick={handlePrevDay}
            className="h-9 w-9 flex items-center justify-center rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-600 transition-colors"
            title="Ngày trước"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>

          <input
            type="date"
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
            className="h-9 px-3 rounded-lg bg-slate-50 border border-slate-200 text-xs font-semibold text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-teal-700/20 font-mono"
          />

          <button
            type="button"
            onClick={handleNextDay}
            className="h-9 w-9 flex items-center justify-center rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-600 transition-colors"
            title="Ngày sau"
          >
            <ChevronRight className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={() => setSelectedDate(todayStr)}
            className={`h-9 px-2.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
              selectedDate === todayStr
                ? "bg-teal-50 text-teal-800 border border-teal-200"
                : "bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-600"
            }`}
          >
            Hôm nay
          </button>

          {selectedDate && (
            <button
              type="button"
              onClick={() => setSelectedDate("")}
              className="h-9 px-2 rounded-lg text-slate-400 hover:text-slate-600 text-xs hover:bg-slate-50 cursor-pointer"
              title="Xem tất cả ngày"
            >
              Xem tất cả
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 self-end md:self-center">
          {selectedServiceObj && selectedServiceObj.status !== "PUBLISHED" && (
            <div className="px-2.5 py-1 rounded-lg bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-center gap-1.5 font-medium">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
              <span>Chưa mở bán (cần duyệt để đón khách)</span>
            </div>
          )}

          <button
            onClick={() => selectedServiceId && loadSlots(selectedServiceId)}
            className="h-9 w-9 flex items-center justify-center rounded-lg bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-600 transition-colors cursor-pointer"
            title="Làm mới lịch ca"
            type="button"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 4. Compact 7-Day Quick Strip */}
      <div className="grid grid-cols-7 gap-2">
        {weekDays.map((d) => (
          <button
            key={d.iso}
            type="button"
            onClick={() => setSelectedDate(d.iso)}
            className={`py-2 px-1 rounded-xl border text-center transition-all cursor-pointer flex flex-col items-center justify-center ${
              d.isSelected
                ? "bg-teal-900 text-white border-teal-900 shadow-xs"
                : d.isToday
                  ? "bg-teal-50/70 border-teal-200 text-teal-950 hover:bg-teal-100/50"
                  : "bg-white border-slate-200/80 text-slate-700 hover:bg-slate-50"
            }`}
          >
            <span className={`text-[11px] font-semibold ${d.isSelected ? "text-teal-200" : "text-slate-500"}`}>
              {d.label}
            </span>
            <span className="text-base font-bold font-mono tracking-tight leading-tight">
              {d.dayNum}/{d.monthNum}
            </span>
            <span
              className={`text-[10px] mt-0.5 font-medium ${
                d.isSelected
                  ? "text-teal-200"
                  : d.slotCount > 0
                    ? "text-teal-700 font-semibold"
                    : "text-slate-400"
              }`}
            >
              {d.slotCount > 0 ? `${d.slotCount} ca` : "Trống"}
            </span>
          </button>
        ))}
      </div>

      {/* Error Fallback */}
      {slotsErrorMessage && (
        <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-between text-rose-800 text-xs">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>Lỗi tải danh sách ca: {slotsErrorMessage}</span>
          </div>
          <AtlasButton
            variant="danger"
            size="sm"
            onClick={() => selectedServiceId && loadSlots(selectedServiceId)}
          >
            Thử lại
          </AtlasButton>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoadingSlots && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5 animate-pulse">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-44 bg-slate-100 rounded-xl border border-slate-200/80" />
          ))}
        </div>
      )}

      {/* 5. Slots Grid */}
      {!isLoadingSlots && !slotsErrorMessage && (
        <>
          {filteredSlots.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {filteredSlots.map((slot) => {
                const booked = slot.bookedCount || 0;
                const available =
                  slot.availableCapacity != null ? slot.availableCapacity : slot.capacity - booked;
                const isClosed = slot.status === "CLOSED";
                const isCancelled = (slot.status as string) === "CANCELLED";
                const isBlocked = (slot.status as string) === "BLOCKED";
                const isFull = available <= 0;
                const slotDateTime = new Date(`${slot.date}T${slot.endTime || "23:59:59"}`);
                const isPastSlot = slot.date < todayStr || slotDateTime.getTime() < nowTimestamp;

                return (
                  <div
                    key={slot.id}
                    className={`rounded-xl border p-4.5 bg-white shadow-xs transition-all flex flex-col justify-between ${
                      isClosed || isCancelled
                        ? "border-slate-200/60 opacity-85 bg-slate-50/50"
                        : "border-slate-200/90 hover:border-slate-300"
                    }`}
                  >
                    <div className="space-y-3">
                      {/* Row 1: Time, Date & Status */}
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <Clock className="w-4 h-4 text-teal-700" />
                            <span className="font-mono text-base font-bold text-slate-900 tracking-tight">
                              {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                            </span>
                          </div>
                          <p className="text-[11px] font-mono text-slate-500 mt-0.5">Ngày: {slot.date}</p>
                        </div>

                        <div>
                          {isPastSlot ? (
                            isBlocked ? (
                              <AtlasBadge label="Bị chặn" variant="danger" />
                            ) : isCancelled ? (
                              <AtlasBadge label="Đã hủy" variant="danger" />
                            ) : (
                              <AtlasBadge label="Ca đã qua" variant="neutral" />
                            )
                          ) : isCancelled ? (
                            <AtlasBadge label="Đã hủy xuất bến" variant="danger" />
                          ) : isBlocked ? (
                            <AtlasBadge label="Bị chặn" variant="danger" />
                          ) : isClosed ? (
                            <AtlasBadge label="Tạm đóng" variant="neutral" />
                          ) : isFull ? (
                            <AtlasBadge label="Hết chỗ" variant="danger" />
                          ) : (
                            <AtlasBadge label="Đang đón khách" variant="success" />
                          )}
                        </div>
                      </div>

                      {/* Row 2: Bundled Options (each option shown individually with type & price/unit) */}
                      <div className="space-y-1.5 pt-1">
                        <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
                          Gói bán áp dụng:
                        </span>
                        {slot.options && slot.options.length > 0 ? (
                          <div className="space-y-1.5">
                            {slot.options.map((opt) => (
                              <div
                                key={opt.id}
                                className={`flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg text-xs border ${
                                  opt.optionType === "PRIVATE"
                                    ? "bg-teal-50/70 text-teal-900 border-teal-200"
                                    : "bg-sky-50/70 text-sky-900 border-sky-200"
                                }`}
                              >
                                <div className="flex items-center gap-1.5 min-w-0">
                                  <span className="font-semibold truncate">{opt.name}</span>
                                  <span
                                    className={`px-1.5 py-0.5 rounded text-[10px] font-bold shrink-0 ${
                                      opt.optionType === "PRIVATE"
                                        ? "bg-teal-100 text-teal-800"
                                        : "bg-sky-100 text-sky-800"
                                    }`}
                                  >
                                    {opt.optionType}
                                  </span>
                                </div>
                                <span className="font-mono font-bold text-[11px] shrink-0">
                                  {formatOptionPriceUnit(opt.price, opt.pricingUnit, opt.optionType)}
                                </span>
                              </div>
                            ))}
                          </div>
                        ) : slot.optionName ? (
                          <div
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs border ${
                              slot.optionType === "PRIVATE"
                                ? "bg-teal-50/70 text-teal-900 border-teal-200"
                                : "bg-sky-50/70 text-sky-900 border-sky-200"
                            }`}
                          >
                            <span className="font-semibold">{slot.optionName}</span>
                            {slot.optionType && (
                              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-white/70">
                                {slot.optionType}
                              </span>
                            )}
                            <span className="text-[10px] text-slate-600 font-medium">· Legacy đơn lẻ</span>
                          </div>
                        ) : (() => {
                          const compat =
                            slot.legacyCompatibleOptions && slot.legacyCompatibleOptions.length > 0
                              ? slot.legacyCompatibleOptions
                              : serviceOptions.filter(
                                  (o) =>
                                    o.status === "ACTIVE" &&
                                    (o.optionType !== "PRIVATE" || slot.inventoryType === "SHARED_CAPACITY_UNITS")
                                );
                          return (
                            <div className="space-y-1.5">
                              <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium bg-amber-50 text-amber-800 border border-amber-200">
                                Chưa gắn option trực tiếp · Tương thích legacy
                              </span>
                              {compat.length > 0 && (
                                <div className="space-y-1">
                                  {compat.map((opt) => (
                                    <div
                                      key={opt.id}
                                      className="flex items-center justify-between gap-2 px-2 py-1 rounded-md text-[11px] bg-slate-50 border border-dashed border-slate-200 text-slate-700"
                                    >
                                      <span className="truncate font-medium">
                                        {opt.name} · {opt.optionType}
                                      </span>
                                      <span className="font-mono text-slate-600 shrink-0">
                                        {formatOptionPriceUnit(opt.price, opt.pricingUnit, opt.optionType)}
                                      </span>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          );
                        })()}
                      </div>

                      {/* Row 3: Capacity & Resource Availability Breakdown */}
                      {(() => {
                        const effectiveOpts =
                          slot.options && slot.options.length > 0
                            ? slot.options
                            : slot.legacyCompatibleOptions && slot.legacyCompatibleOptions.length > 0
                              ? slot.legacyCompatibleOptions
                              : !slot.optionName
                                ? serviceOptions.filter(
                                    (o) =>
                                      o.status === "ACTIVE" &&
                                      (o.optionType !== "PRIVATE" || slot.inventoryType === "SHARED_CAPACITY_UNITS")
                                  )
                                : [];
                        const hasSharedOpt =
                          effectiveOpts.some((o) => o.optionType === "SHARED") || slot.optionType === "SHARED" || effectiveOpts.length === 0;
                        const privateOpts = effectiveOpts.filter((o) => o.optionType === "PRIVATE");
                        const hasPrivateOpt = privateOpts.length > 0 || slot.optionType === "PRIVATE";
                        const minRequiredPrivatePax =
                          privateOpts.length > 0
                            ? Math.min(...privateOpts.map((o) => Math.max(1, o.maxPaxPerPackage || 1)))
                            : 1;
                        const emptyEligiblePrivateUnits =
                          slot.units?.filter(
                            (u) =>
                              !u.equipmentBlocked &&
                              (u.bookedCount || 0) === 0 &&
                              u.capacity >= minRequiredPrivatePax
                          ).length ?? 0;

                        return (
                          <div className="space-y-1.5 pt-1">
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-slate-500">
                                {slot.inventoryType === "SHARED_CAPACITY_UNITS"
                                  ? `Sức chứa (${slot.units?.length || 0} đơn vị)`
                                  : "Sức chứa giới hạn khách"}
                              </span>
                              <span className="font-mono font-bold text-slate-900 tabular-nums">
                                Đã đặt {booked} / {slot.capacity} chỗ
                              </span>
                            </div>
                            <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                              <div
                                className={`h-full rounded-full transition-all ${
                                  isFull
                                    ? "bg-rose-500"
                                    : isClosed || isCancelled
                                      ? "bg-slate-400"
                                      : isPastSlot
                                        ? "bg-slate-400"
                                        : "bg-teal-700"
                                }`}
                                style={{ width: `${Math.min(100, (booked / Math.max(1, slot.capacity)) * 100)}%` }}
                              />
                            </div>
                            <div className="flex justify-between items-center gap-2 text-[11px] text-slate-500 flex-wrap">
                              {slot.inventoryType === "SHARED_CAPACITY_UNITS" ? (
                                <div className="flex items-center gap-2 flex-wrap">
                                  {hasSharedOpt && (
                                    <span>
                                      Chỗ ghép còn trống:{" "}
                                      <strong className="font-mono text-teal-800 font-bold">
                                        {Math.max(0, available)} chỗ
                                      </strong>
                                    </span>
                                  )}
                                  {hasPrivateOpt && (
                                    <span>
                                      Gói thuê riêng trống:{" "}
                                      <strong className="font-mono text-teal-800 font-bold">
                                        {emptyEligiblePrivateUnits} gói
                                      </strong>
                                    </span>
                                  )}
                                </div>
                              ) : (
                                <span>
                                  Chỗ ghép còn trống:{" "}
                                  <strong className="font-mono text-teal-800 font-bold">
                                    {Math.max(0, available)} chỗ
                                  </strong>
                                </span>
                              )}
                              <span className="font-mono text-slate-400">
                                {Math.round((booked / Math.max(1, slot.capacity)) * 100)}%
                              </span>
                            </div>
                            {hasSharedOpt && hasPrivateOpt && slot.inventoryType === "SHARED_CAPACITY_UNITS" && (
                              <p className="text-[10px] text-slate-500">
                                Dùng chung quỹ đơn vị tài nguyên, không cộng dồn giữa các gói.
                              </p>
                            )}
                          </div>
                        );
                      })()}

                      {/* Row 4: Units Details */}
                      {slot.units && slot.units.length > 0 && (
                        <div className="pt-2 border-t border-slate-100 text-[11px] text-slate-600 flex flex-wrap gap-1.5">
                          {slot.units.map((u) => {
                            const linkedEq = u.equipmentId
                              ? equipmentList.find((item) => item.id === u.equipmentId)
                              : undefined;
                            return (
                              <span
                                key={u.unitNumber}
                                className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-mono ${
                                  u.equipmentBlocked
                                    ? "bg-rose-50 text-rose-800 border border-rose-200"
                                    : u.equipmentId
                                      ? "bg-sky-50 text-sky-900 border border-sky-200"
                                      : "bg-slate-100 text-slate-700"
                                }`}
                                title={
                                  u.equipmentId
                                    ? `Thiết bị đã liên kết: ${linkedEq?.name || u.equipmentId}`
                                    : "Tài nguyên tự cấu hình, chưa liên kết thiết bị"
                                }
                              >
                                <Ship className="w-3 h-3 shrink-0 opacity-70" />
                                <span>
                                  Đơn vị #{u.unitNumber}: {u.bookedCount}/{u.capacity} chỗ ·{" "}
                                  {u.equipmentBlocked
                                    ? "Bảo trì"
                                    : u.equipmentId
                                      ? linkedEq?.name || "Đã liên kết"
                                      : "Tự cấu hình"}
                                </span>
                              </span>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* Card Footer Actions */}
                    <div className="flex items-center justify-between gap-2 flex-wrap pt-3 mt-3 border-t border-slate-100">
                      <div className="flex items-center gap-2">
                        <AtlasButton
                          variant={isClosed ? "secondary" : "outline"}
                          size="sm"
                          disabled={isPastSlot || isCancelled}
                          onClick={() => handleToggleStatus(slot)}
                          icon={isClosed ? <Unlock className="w-3.5 h-3.5" /> : <Lock className="w-3.5 h-3.5" />}
                        >
                          {isClosed ? "Mở ca" : "Đóng ca"}
                        </AtlasButton>

                        <AtlasButton
                          variant="primary"
                          size="sm"
                          disabled={isPastSlot || isCancelled}
                          onClick={() => handleOpenEditCapacity(slot)}
                          icon={<Edit2 className="w-3.5 h-3.5" />}
                        >
                          Điều chỉnh
                        </AtlasButton>
                      </div>

                      {!isPastSlot && !isCancelled && (
                        <button
                          type="button"
                          onClick={() => handleOpenCancelDeparture(slot)}
                          className="px-2.5 py-1.5 text-xs font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg border border-rose-200/80 transition-colors cursor-pointer flex items-center gap-1"
                          title="Hủy ca do bão biển hoặc sự cố kỹ thuật"
                        >
                          <AlertTriangle className="w-3.5 h-3.5 text-rose-500" />
                          <span>Hủy ca</span>
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="w-full bg-white rounded-xl border border-slate-200/80 p-12 text-center shadow-xs">
              <div className="flex flex-col items-center justify-center space-y-3 text-slate-500 max-w-sm mx-auto">
                <div className="w-12 h-12 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-center text-slate-400">
                  <CalendarDays className="w-6 h-6 text-slate-400" />
                </div>
                <p className="font-semibold text-slate-900 text-base">Chưa có ca khởi hành</p>
                <p className="text-xs text-slate-500">
                  {selectedServiceObj
                    ? `Dịch vụ "${selectedServiceObj.name}" chưa có ca nào trong ngày ${selectedDate || "này"}.`
                    : "Vui lòng chọn dịch vụ để xem hoặc tạo lịch đón khách."}
                </p>
                {selectedServiceId && selectedServiceObj?.status === "PUBLISHED" && (
                  <button
                    type="button"
                    onClick={() => setIsCreateDrawerOpen(true)}
                    className="mt-2 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-teal-800 hover:bg-teal-900 text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer"
                  >
                    <Plus className="w-4 h-4" />
                    <span>Tạo ca đón khách ngay</span>
                  </button>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {/* 6. DRAWER: Create Single Slot */}
      {isCreateDrawerOpen && createPortal(
        <div className="fixed inset-0 z-50 overflow-hidden bg-slate-900/40 backdrop-blur-xs flex justify-end">
          <div className="w-full max-w-lg bg-white h-full shadow-2xl flex flex-col animate-in slide-in-from-right duration-200 border-l border-slate-200">
            <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/60 shrink-0">
              <div>
                <h3 className="font-bold text-base text-slate-900">Tạo ca khởi hành mới</h3>
                <p className="text-xs text-slate-500 mt-0.5">{selectedServiceObj?.name}</p>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateDrawerOpen(false)}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form id="create-slot-form" onSubmit={handleCreateSlot} className="flex-1 overflow-y-auto p-6 space-y-5">
              {/* Section 1: Thời gian */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">1. Thời gian khởi hành</h4>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Ngày diễn ra:</label>
                    <input
                      type="date"
                      required
                      min={todayStr}
                      value={newDate}
                      onChange={(e) => setNewDate(e.target.value)}
                      className="w-full h-10 px-3 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-700/20"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Lặp lại liên tiếp:</label>
                    <select
                      value={repeatDays}
                      onChange={(e) => setRepeatDays(Number(e.target.value))}
                      className="w-full h-10 px-3 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-700/20 cursor-pointer"
                    >
                      <option value={1}>1 ngày (Chỉ ngày này)</option>
                      <option value={3}>3 ngày liên tiếp</option>
                      <option value={7}>7 ngày (1 tuần)</option>
                      <option value={14}>14 ngày (2 tuần)</option>
                      <option value={30}>30 ngày (1 tháng)</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Giờ bắt đầu:</label>
                    <input
                      type="time"
                      required
                      value={newStartTime}
                      onChange={(e) => setNewStartTime(e.target.value)}
                      className="w-full h-10 px-3 border border-slate-200 rounded-lg text-xs font-bold font-mono text-slate-800 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-700/20"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">Giờ kết thúc:</label>
                    <input
                      type="time"
                      required
                      value={newEndTime}
                      onChange={(e) => setNewEndTime(e.target.value)}
                      className="w-full h-10 px-3 border border-slate-200 rounded-lg text-xs font-bold font-mono text-slate-800 bg-slate-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-700/20"
                    />
                  </div>
                </div>

                <div className="flex items-center gap-1.5 pt-1">
                  <span className="text-[11px] text-slate-400">Khung giờ mẫu:</span>
                  {[
                    ["08:00", "10:00"],
                    ["10:30", "12:30"],
                    ["14:00", "16:00"],
                  ].map(([start, end]) => (
                    <button
                      key={start}
                      type="button"
                      onClick={() => {
                        setNewStartTime(start);
                        setNewEndTime(end);
                      }}
                      className="px-2 py-0.5 rounded bg-slate-100 hover:bg-slate-200/70 text-[11px] font-mono text-slate-700 transition-colors"
                    >
                      {start} - {end}
                    </button>
                  ))}
                </div>
                {slotFormErrors.time && (
                  <p className="text-xs text-rose-600 font-medium flex items-center gap-1 mt-1">
                    <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                    <span>{slotFormErrors.time}</span>
                  </p>
                )}
              </div>

              {/* Section 2: Chọn Option sẵn có (NO inline create option, NO price input) */}
              <div className="space-y-3 pt-4 border-t border-slate-100">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    2. Chọn gói bán cho ca này <span className="text-rose-500">*</span>
                  </h4>
                  {selectedServiceId && (
                    <Link
                      to={`/vendor/services/${selectedServiceId}/edit?tab=OPTIONS`}
                      className="text-xs text-teal-800 font-semibold hover:underline"
                    >
                      Quản lý gói ở Tab 3
                    </Link>
                  )}
                </div>

                {slotFormErrors.options && (
                  <p className="text-xs text-rose-600 font-medium flex items-center gap-1">
                    <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                    <span>{slotFormErrors.options}</span>
                  </p>
                )}

                {isLoadingOptions ? (
                  <p className="text-xs text-slate-400">Đang tải danh sách gói...</p>
                ) : serviceOptions.length === 0 ? (
                  <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-900 text-xs space-y-1.5">
                    <p className="font-semibold">Dịch vụ chưa có gói bán nào đang mở (ACTIVE).</p>
                    <p className="text-[11px] text-amber-800">
                      Vui lòng mở{" "}
                      <Link
                        to={`/vendor/services/${selectedServiceId}/edit?tab=OPTIONS`}
                        className="font-bold underline text-teal-800 hover:text-teal-900"
                      >
                        Tab 3. Lựa chọn bán
                      </Link>{" "}
                      của dịch vụ để tạo hoặc kích hoạt gói trước khi tạo ca.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {serviceOptions.map((opt) => {
                      const isChecked = newOptionIds.includes(opt.id);
                      return (
                        <label
                          key={opt.id}
                          className={`flex items-center justify-between gap-2 p-3 rounded-xl border text-xs cursor-pointer transition-all ${
                            isChecked
                              ? "bg-teal-50/50 border-teal-300 shadow-xs"
                              : "bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                          }`}
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => handleToggleNewOption(opt.id)}
                              className="rounded border-slate-300 text-teal-800 focus:ring-teal-700 w-4 h-4 shrink-0"
                            />
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className="font-semibold text-slate-900">{opt.name}</span>
                                <span
                                  className={`text-[10px] px-1.5 py-0.5 rounded font-bold ${
                                    opt.optionType === "PRIVATE"
                                      ? "bg-teal-100/80 text-teal-800"
                                      : "bg-sky-100/80 text-sky-800"
                                  }`}
                                >
                                  {opt.optionType} · {opt.optionType === "PRIVATE" ? "Thuê riêng" : "Vé ghép"}
                                </span>
                              </div>
                              {opt.maxPaxPerPackage && (
                                <p className="text-[11px] text-slate-500 mt-0.5">
                                  Tối đa {opt.maxPaxPerPackage} khách/gói
                                </p>
                              )}
                            </div>
                          </div>
                          <span className="font-mono font-bold text-slate-800 shrink-0">
                            {formatOptionPriceUnit(opt.price, opt.pricingUnit, opt.optionType)}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Section 3: Phương thức & Sức chứa */}
              <div className="space-y-3 pt-4 border-t border-slate-100">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  3. Phương thức vận hành &amp; Sức chứa
                </h4>

                {slotFormErrors.inventory && (
                  <p className="text-xs text-rose-600 font-medium flex items-center gap-1">
                    <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                    <span>{slotFormErrors.inventory}</span>
                  </p>
                )}

                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    disabled={hasPrivateOptionSelected}
                    onClick={() => setNewInventoryType("PERSON_LIMIT")}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      newInventoryType === "PERSON_LIMIT"
                        ? "bg-teal-50 border-teal-300 text-teal-900 font-semibold"
                        : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                    } ${hasPrivateOptionSelected ? "opacity-40 cursor-not-allowed" : "cursor-pointer"}`}
                  >
                    <div className="flex items-center gap-1.5 text-xs font-bold">
                      <Users className="w-4 h-4 text-slate-500" />
                      <span>Chia theo đầu người</span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 leading-snug">
                      Phù hợp dịch vụ vé ghép, không chia theo đơn vị riêng
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => setNewInventoryType("SHARED_CAPACITY_UNITS")}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      newInventoryType === "SHARED_CAPACITY_UNITS"
                        ? "bg-teal-50 border-teal-300 text-teal-900 font-semibold"
                        : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex items-center gap-1.5 text-xs font-bold">
                      <Ship className="w-4 h-4 text-teal-700" />
                      <span>Chia theo đơn vị tài nguyên</span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 leading-snug">
                      Quản lý theo từng đơn vị hoặc thiết bị
                    </p>
                  </button>
                </div>

                {hasPrivateOptionSelected && (
                  <div className="p-2.5 bg-teal-50/70 border border-teal-200 rounded-lg text-teal-900 text-[11px] flex items-center gap-2">
                    <Check className="w-3.5 h-3.5 text-teal-700 shrink-0" />
                    <span>Gói thuê riêng cần quản lý chỗ theo từng đơn vị tài nguyên.</span>
                  </div>
                )}

                {newInventoryType === "PERSON_LIMIT" ? (
                  <div>
                    <label className="block text-xs font-medium text-slate-700 mb-1">
                      Tổng số lượng khách tối đa:
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={500}
                      value={newCapacity}
                      onChange={(e) => setNewCapacity(Math.max(1, parseInt(e.target.value) || 1))}
                      className="w-full h-10 px-3 border border-slate-200 rounded-lg text-sm font-bold font-mono text-slate-800"
                    />
                    {slotFormErrors.capacity && (
                      <p className="text-xs text-rose-600 font-medium flex items-center gap-1 mt-1">
                        <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                        <span>{slotFormErrors.capacity}</span>
                      </p>
                    )}
                  </div>
                ) : (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-xs font-medium text-slate-700">
                          Phân bổ đơn vị tài nguyên:
                        </span>
                        <p className="text-[11px] text-slate-500">
                          Chọn thiết bị đã liên kết để khóa trùng ca hoặc tự cấu hình
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          setNewUnits((prev) => {
                            const assignedIds = new Set(prev.map((item) => item.equipmentId).filter(Boolean));
                            const nextAvail = equipmentList.find((item) => !assignedIds.has(item.id));
                            if (nextAvail) {
                              return [
                                ...prev,
                                { unitNumber: prev.length + 1, capacity: nextAvail.capacity, equipmentId: nextAvail.id },
                              ];
                            }
                            return [...prev, { unitNumber: prev.length + 1, capacity: 5 }];
                          });
                        }}
                        className="text-xs text-teal-800 font-semibold hover:underline flex items-center gap-1 cursor-pointer shrink-0"
                      >
                        <Plus className="w-3 h-3" />
                        <span>Thêm đơn vị</span>
                      </button>
                    </div>

                    {slotFormErrors.units && (
                      <p className="text-xs text-rose-600 font-medium flex items-center gap-1">
                        <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                        <span>{slotFormErrors.units}</span>
                      </p>
                    )}

                    <div className="space-y-2">
                      {newUnits.map((u, idx) => (
                        <div key={idx} className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-slate-700">Đơn vị #{u.unitNumber}</span>
                            {newUnits.length > 1 && (
                              <button
                                type="button"
                                onClick={() =>
                                  setNewUnits((prev) =>
                                    prev
                                      .filter((_, i) => i !== idx)
                                      .map((item, i) => ({ ...item, unitNumber: i + 1 }))
                                  )
                                }
                                className="text-slate-400 hover:text-rose-600 p-0.5 cursor-pointer"
                                title="Xóa đơn vị này"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            <div>
                              <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                                Thiết bị liên kết:
                              </label>
                              <select
                                value={u.equipmentId || ""}
                                onChange={(e) => {
                                  const eqId = e.target.value;
                                  setNewUnits((prev) =>
                                    prev.map((item, i) => {
                                      if (i !== idx) return item;
                                      if (!eqId) return { ...item, equipmentId: undefined };
                                      const eq = equipmentList.find((x) => x.id === eqId);
                                      return {
                                        ...item,
                                        equipmentId: eqId,
                                        capacity: eq ? eq.capacity : item.capacity,
                                      };
                                    })
                                  );
                                }}
                                className="w-full h-8 px-2 bg-white border border-slate-200 rounded text-xs text-slate-800"
                              >
                                <option value="">-- Tự cấu hình (Chưa liên kết thiết bị) --</option>
                                {equipmentList.map((eq) => {
                                  const isUsedElsewhere = newUnits.some(
                                    (other, oIdx) => oIdx !== idx && other.equipmentId === eq.id
                                  );
                                  return (
                                    <option key={eq.id} value={eq.id} disabled={isUsedElsewhere}>
                                      {eq.name} ({eq.capacity} chỗ) {isUsedElsewhere ? "(Đã chọn ở ca này)" : ""}
                                    </option>
                                  );
                                })}
                              </select>
                            </div>

                            <div>
                              <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                                Sức chứa ca này:
                              </label>
                              <div className="flex items-center gap-1.5">
                                <input
                                  type="number"
                                  min={1}
                                  max={500}
                                  disabled={Boolean(u.equipmentId)}
                                  value={u.capacity}
                                  onChange={(e) => {
                                    const val = Math.max(1, parseInt(e.target.value) || 1);
                                    setNewUnits((prev) =>
                                      prev.map((item, i) => (i === idx ? { ...item, capacity: val } : item))
                                    );
                                  }}
                                  className="w-full h-8 px-2 bg-white border border-slate-200 rounded text-xs font-bold font-mono text-slate-800 disabled:bg-slate-100 disabled:text-slate-500"
                                />
                                <span className="text-xs text-slate-500 whitespace-nowrap">chỗ</span>
                              </div>
                              {u.equipmentId && (
                                <span className="text-[10px] text-teal-700">Đồng bộ theo sức chứa thiết bị</span>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>

                    <div className="text-right text-xs font-semibold text-slate-600 pt-1">
                      Tổng sức chứa:{" "}
                      <span className="font-mono text-teal-900 font-bold">
                        {newUnits.reduce((acc, u) => acc + (u.capacity || 0), 0)} chỗ
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </form>

            <div className="p-4 border-t border-slate-200 bg-slate-50/80 flex items-center justify-end gap-2.5 shrink-0">
              <AtlasButton
                variant="outline"
                size="md"
                onClick={() => setIsCreateDrawerOpen(false)}
                disabled={isSubmittingSlot}
              >
                Hủy
              </AtlasButton>
              <AtlasButton
                type="submit"
                variant="primary"
                size="md"
                form="create-slot-form"
                disabled={isSubmittingSlot}
              >
                {isSubmittingSlot ? "Đang tạo ca..." : "Tạo ca khởi hành"}
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* 7. MODAL: Batch Create Slots */}
      {isBatchModalOpen && (
        <BatchCreateSlotsModal
          isOpen={isBatchModalOpen}
          onClose={() => setIsBatchModalOpen(false)}
          serviceId={selectedServiceId}
          serviceName={selectedServiceObj?.name || ""}
          capacityPerSlot={selectedServiceObj?.capacityPerSlot}
          serviceStatus={selectedServiceObj?.status}
          onSuccess={(count) => {
            showToast(`Đã tạo thành công ${count} ca vận hành mới!`);
            setIsBatchModalOpen(false);
            if (selectedServiceId) loadSlots(selectedServiceId);
          }}
        />
      )}

      {/* 8. MODAL: Edit Slot Capacity / Status / Bundled Options */}
      {editingSlot && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200/90 w-full max-w-lg p-6 space-y-4 max-h-[90vh] overflow-y-auto animate-in fade-in duration-150">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="font-bold text-base text-slate-900">Điều chỉnh ca vận hành</h3>
                <p className="text-xs text-slate-500 mt-0.5 font-mono">
                  {editingSlot.date} • {editingSlot.startTime.substring(0, 5)} - {editingSlot.endTime.substring(0, 5)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditingSlot(null)}
                className="text-slate-400 hover:text-slate-600 p-1 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveCapacity} className="space-y-3.5 text-xs">
              {capacityError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-800 flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <span>{capacityError}</span>
                </div>
              )}

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Trạng thái ca:</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setInputStatus("OPEN")}
                    className={`py-2 px-3 rounded-lg border font-semibold transition-all ${
                      inputStatus === "OPEN"
                        ? "bg-emerald-50 border-emerald-300 text-emerald-800"
                        : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                    }`}
                  >
                    Đón khách (OPEN)
                  </button>
                  <button
                    type="button"
                    onClick={() => setInputStatus("CLOSED")}
                    className={`py-2 px-3 rounded-lg border font-semibold transition-all ${
                      inputStatus === "CLOSED"
                        ? "bg-slate-100 border-slate-300 text-slate-800"
                        : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                    }`}
                  >
                    Tạm đóng (CLOSED)
                  </button>
                </div>
              </div>

              {editingSlot.inventoryType === "SHARED_CAPACITY_UNITS" ? (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="font-semibold text-slate-700 block text-xs">
                        Danh sách đơn vị tài nguyên (Đã đặt: {editingSlot.bookedCount || 0} khách):
                      </span>
                      <span className="text-[11px] text-slate-500">
                        Đơn vị đã liên kết giữ nguyên sức chứa thiết bị; đơn vị tự cấu hình cho phép điều chỉnh
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setEditUnits((prev) => {
                          const assignedIds = new Set(prev.map((item) => item.equipmentId).filter(Boolean));
                          const nextAvail = equipmentList.find((item) => !assignedIds.has(item.id));
                          if (nextAvail) {
                            return [
                              ...prev,
                              { unitNumber: prev.length + 1, capacity: nextAvail.capacity, equipmentId: nextAvail.id },
                            ];
                          }
                          return [...prev, { unitNumber: prev.length + 1, capacity: 5 }];
                        });
                      }}
                      className="text-xs text-teal-800 font-semibold hover:underline flex items-center gap-1 cursor-pointer shrink-0"
                    >
                      <Plus className="w-3 h-3" />
                      <span>Thêm đơn vị</span>
                    </button>
                  </div>
                  <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                    {editUnits.map((u, idx) => {
                      const isRegistered = Boolean(u.equipmentId);
                      const eqInfo = equipmentList.find((item) => item.id === u.equipmentId);
                      return (
                        <div key={idx} className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 space-y-2">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <span className="text-xs font-bold text-slate-700">Đơn vị #{u.unitNumber}</span>
                              {isRegistered ? (
                                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-sky-100 text-sky-800 border border-sky-200">
                                  Đã liên kết: {eqInfo?.name || "Thiết bị"}
                                </span>
                              ) : (
                                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-200/80 text-slate-700">
                                  Tự cấu hình
                                </span>
                              )}
                            </div>
                            {editUnits.length > 1 && !isRegistered && (
                              <button
                                type="button"
                                onClick={() =>
                                  setEditUnits((prev) =>
                                    prev
                                      .filter((_, i) => i !== idx)
                                      .map((item, i) => ({ ...item, unitNumber: i + 1 }))
                                  )
                                }
                                className="text-slate-400 hover:text-rose-600 p-0.5 cursor-pointer"
                                title="Xóa đơn vị tự cấu hình này"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            <div>
                              <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                                Thiết bị liên kết:
                              </label>
                              <select
                                value={u.equipmentId || ""}
                                disabled={isRegistered}
                                onChange={(e) => {
                                  const eqId = e.target.value;
                                  setEditUnits((prev) =>
                                    prev.map((item, i) => {
                                      if (i !== idx) return item;
                                      if (!eqId) return { ...item, equipmentId: undefined };
                                      const eq = equipmentList.find((x) => x.id === eqId);
                                      return {
                                        ...item,
                                        equipmentId: eqId,
                                        capacity: eq ? eq.capacity : item.capacity,
                                      };
                                    })
                                  );
                                }}
                                className="w-full h-8 px-2 bg-white border border-slate-200 rounded text-xs text-slate-800 disabled:bg-slate-100 disabled:text-slate-500"
                              >
                                <option value="">-- Tự cấu hình (Chưa liên kết thiết bị) --</option>
                                {equipmentList.map((eq) => {
                                  const isUsedElsewhere = editUnits.some(
                                    (other, oIdx) => oIdx !== idx && other.equipmentId === eq.id
                                  );
                                  return (
                                    <option key={eq.id} value={eq.id} disabled={isUsedElsewhere}>
                                      {eq.name} ({eq.capacity} chỗ) {isUsedElsewhere ? "(Đã chọn)" : ""}
                                    </option>
                                  );
                                })}
                              </select>
                            </div>

                            <div>
                              <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                                Sức chứa ca này:
                              </label>
                              <div className="flex items-center gap-1.5">
                                <input
                                  type="number"
                                  min={1}
                                  max={500}
                                  disabled={isRegistered}
                                  value={u.capacity}
                                  onChange={(e) => {
                                    const val = Math.max(1, parseInt(e.target.value) || 1);
                                    setEditUnits((prev) =>
                                      prev.map((item, i) => (i === idx ? { ...item, capacity: val } : item))
                                    );
                                  }}
                                  className="w-full h-8 px-2 bg-white border border-slate-200 rounded text-xs font-bold font-mono text-slate-800 disabled:bg-slate-100 disabled:text-slate-500"
                                />
                                <span className="text-xs text-slate-500 whitespace-nowrap">chỗ</span>
                              </div>
                              {isRegistered ? (
                                <span className="text-[10px] text-teal-700 block mt-0.5">
                                  Cố định theo thiết bị đã liên kết
                                </span>
                              ) : (
                                <span className="text-[10px] text-slate-500 block mt-0.5">
                                  Cho phép điều chỉnh linh hoạt
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  <div className="text-right text-xs font-semibold text-slate-600 pt-1">
                    Tổng sức chứa:{" "}
                    <span className="font-mono text-teal-900 font-bold">
                      {editUnits.reduce((acc, u) => acc + (u.capacity || 0), 0)} chỗ
                    </span>
                  </div>
                </div>
              ) : (
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    Sức chứa tối đa (Đã đặt: {editingSlot.bookedCount || 0} khách):
                  </label>
                  <input
                    type="number"
                    min={editingSlot.bookedCount || 1}
                    max={500}
                    value={inputCapacity}
                    onChange={(e) => setInputCapacity(parseInt(e.target.value) || 0)}
                    className="w-full h-10 px-3 border border-slate-200 rounded-lg text-sm font-bold font-mono text-slate-800"
                  />
                </div>
              )}

              {/* Edit Options */}
              <div className="space-y-1.5 pt-2 border-t border-slate-100">
                <span className="font-semibold text-slate-700 block">Gói bán áp dụng:</span>
                <div className="space-y-1.5 max-h-44 overflow-y-auto pr-1">
                  {serviceOptions.map((opt) => {
                    const isChecked = editOptionIds.includes(opt.id);
                    const isPrivateDisallowed =
                      editingSlot.inventoryType === "PERSON_LIMIT" && opt.optionType === "PRIVATE";
                    return (
                      <label
                        key={opt.id}
                        className={`flex items-center justify-between gap-2 p-2.5 rounded-lg border text-xs ${
                          isPrivateDisallowed
                            ? "bg-slate-50 border-slate-200 text-slate-400 cursor-not-allowed opacity-60"
                            : isChecked
                              ? "bg-teal-50/50 border-teal-300 text-slate-900 cursor-pointer"
                              : "bg-white border-slate-200 text-slate-600 cursor-pointer"
                        }`}
                        title={
                          isPrivateDisallowed
                            ? "Gói thuê riêng chỉ áp dụng cho ca quản lý theo từng phương tiện (SHARED_CAPACITY_UNITS)"
                            : undefined
                        }
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            disabled={isPrivateDisallowed}
                            onChange={() => !isPrivateDisallowed && handleToggleEditOption(opt.id)}
                            className="rounded border-slate-300 text-teal-800 w-4 h-4 disabled:opacity-50 shrink-0"
                          />
                          <span className="font-semibold truncate">{opt.name}</span>
                          <span
                            className={`text-[10px] font-bold px-1.5 py-0.5 rounded shrink-0 ${
                              opt.optionType === "PRIVATE"
                                ? "bg-teal-100 text-teal-800"
                                : "bg-sky-100 text-sky-800"
                            }`}
                          >
                            {opt.optionType}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          {isPrivateDisallowed && (
                            <span className="text-[10px] text-amber-600 font-medium">
                              Cần SHARED_CAPACITY_UNITS
                            </span>
                          )}
                          <span className="font-mono font-bold text-slate-800">
                            {formatOptionPriceUnit(opt.price, opt.pricingUnit, opt.optionType)}
                          </span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <AtlasButton
                  variant="outline"
                  size="sm"
                  onClick={() => setEditingSlot(null)}
                  disabled={isUpdatingSlot}
                >
                  Hủy
                </AtlasButton>
                <AtlasButton type="submit" variant="primary" size="sm" disabled={isUpdatingSlot}>
                  {isUpdatingSlot ? "Đang lưu..." : "Lưu thay đổi"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* 9. MODAL: Cancel Departure */}
      {cancellingSlot && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/50 backdrop-blur-2xs p-4 animate-in fade-in duration-100">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200 flex flex-col gap-4 text-xs animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0" />
                <h4 className="font-bold text-slate-900 text-sm">Hủy ca xuất bến &amp; Hoàn tiền</h4>
              </div>
              <button
                type="button"
                onClick={() => setCancellingSlot(null)}
                className="text-slate-400 hover:text-slate-700 p-1 rounded-lg hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-3 bg-amber-50 rounded-xl border border-amber-200/80 text-amber-900 space-y-1">
              <p className="font-semibold text-xs flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-amber-700" />
                <span>
                  Ca: {cancellingSlot.startTime.slice(0, 5)} - {cancellingSlot.endTime.slice(0, 5)} ngày{" "}
                  {cancellingSlot.date}
                </span>
              </p>
              <p className="text-[11px] text-amber-800 leading-relaxed">
                Đã có <strong className="font-mono font-bold text-slate-900">{cancellingSlot.bookedCount || 0}</strong> khách đặt chỗ.
                Khi xác nhận hủy, hệ thống sẽ tự động hủy các booking đang giữ chỗ và kích hoạt đối soát hoàn tiền cho khách.
              </p>
            </div>

            <form onSubmit={handleConfirmCancelDeparture} className="space-y-3">
              <div>
                <label className="font-semibold text-slate-700 block mb-1">
                  Lý do hủy xuất bến (bắt buộc):
                </label>
                <textarea
                  rows={3}
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                  placeholder="Ví dụ: Bão số 6, cảng vụ cấm tàu thuyền xuất bến bảo đảm an toàn..."
                  className="w-full text-xs p-3 rounded-xl border border-slate-300 bg-slate-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 resize-none leading-relaxed"
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <AtlasButton
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setCancellingSlot(null)}
                >
                  Đóng
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="danger"
                  size="sm"
                  disabled={isSubmittingCancel || !cancelReason.trim()}
                >
                  {isSubmittingCancel ? "Đang xử lý..." : "Xác nhận hủy ca"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* 10. MODAL: Operating Schedule Exceptions */}
      {isExceptionsModalOpen && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/50 backdrop-blur-2xs p-4 animate-in fade-in duration-100">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-200 flex flex-col gap-4 text-xs animate-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <CalendarOff className="w-5 h-5 text-slate-700 shrink-0" />
                <h4 className="font-bold text-slate-900 text-sm">Lịch ngoại lệ &amp; Ngày nghỉ</h4>
              </div>
              <button
                type="button"
                onClick={() => setIsExceptionsModalOpen(false)}
                className="text-slate-400 hover:text-slate-700 p-1 rounded-lg hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-2">
              <span className="font-semibold text-slate-700 block">Danh sách ngày nghỉ đã đăng ký:</span>
              {isLoadingExceptions ? (
                <div className="py-6 text-center text-slate-400 flex items-center justify-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin text-sky-600" />
                  <span>Đang tải lịch ngoại lệ...</span>
                </div>
              ) : exceptions.length === 0 ? (
                <div className="p-4 bg-slate-50 rounded-xl border border-slate-200/80 text-center text-slate-500 text-xs">
                  Chưa có ngày nghỉ ngoại lệ nào cho dịch vụ này.
                </div>
              ) : (
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {exceptions.map((ex) => (
                    <div
                      key={ex.id}
                      className="p-3 bg-slate-50 rounded-xl border border-slate-200/80 flex items-center justify-between gap-3 text-xs"
                    >
                      <div>
                        <div className="font-bold text-slate-900 font-mono">
                          {ex.dateFrom} {ex.dateTo !== ex.dateFrom ? `đến ${ex.dateTo}` : ""}
                        </div>
                        <p className="text-[11px] text-slate-600 mt-0.5">{ex.reason}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleRemoveException(ex.id)}
                        className="text-slate-400 hover:text-rose-600 p-1.5 rounded-lg hover:bg-rose-50 transition-colors"
                        title="Xóa ngày nghỉ này"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <form onSubmit={handleAddException} className="space-y-3 pt-3 border-t border-slate-100">
              <span className="font-semibold text-slate-700 block">Thêm ngày nghỉ mới:</span>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">Từ ngày:</label>
                  <input
                    type="date"
                    value={newExDateFrom}
                    onChange={(e) => setNewExDateFrom(e.target.value)}
                    className="w-full text-xs p-2.5 rounded-xl border border-slate-300 bg-slate-50 focus:bg-white focus:outline-none focus:ring-1 focus:ring-sky-500"
                    required
                  />
                </div>
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">Đến ngày:</label>
                  <input
                    type="date"
                    value={newExDateTo}
                    onChange={(e) => setNewExDateTo(e.target.value)}
                    className="w-full text-xs p-2.5 rounded-xl border border-slate-300 bg-slate-50 focus:bg-white focus:outline-none focus:ring-1 focus:ring-sky-500"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="text-[11px] text-slate-500 block mb-1">Lý do nghỉ / ngoại lệ:</label>
                <input
                  type="text"
                  value={newExReason}
                  onChange={(e) => setNewExReason(e.target.value)}
                  placeholder="Ví dụ: Nghỉ bảo dưỡng định kỳ, nghỉ Tết..."
                  className="w-full text-xs p-2.5 rounded-xl border border-slate-300 bg-slate-50 focus:bg-white focus:outline-none focus:ring-1 focus:ring-sky-500"
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2">
                <AtlasButton
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsExceptionsModalOpen(false)}
                >
                  Đóng
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={isSubmittingException || !newExReason.trim()}
                >
                  {isSubmittingException ? "Đang lưu..." : "Thêm ngày nghỉ"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
