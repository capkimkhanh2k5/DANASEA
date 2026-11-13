import { useState, useMemo, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { vendorOrderService } from "../../../services/vendorOrderService";
import { vendorService } from "../../../services/vendorService";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasAvatar, 
  AtlasButton 
} from "../../../components/vendor/atlas/AtlasDesignSystem";
import { 
  QrCode, 
  Percent, 
  Search, 
  RotateCcw, 
  X, 
  Download, 
  MoreVertical,
  Clock,
  MapPin,
  CheckCircle2,
  Check,
  XCircle,
  CalendarClock,
  ChevronsLeft,
  ChevronLeft,
  ChevronRight,
  ChevronsRight,
  Inbox,
  AlertCircle,
  ScanLine,
  FastForward
} from "lucide-react";

import { toast } from "../../../store/useToastStore";
import { extractErrorMessage } from "../../../utils/errorHandler";
import { getAuthoritativeCustomer } from "../../../data/demoCustomerMap";
import type { VendorServiceDto, VendorServiceSlotDto } from "../../../types/vendorService";
import type { VendorSubOrderDto, VendorBookingItemDto, EarlyDispatchSuggestion } from "../../../types/vendorOrder";

export type SubOrderStatus = 
  | "PENDING" 
  | "PENDING_PAYMENT"
  | "CONFIRMED" 
  | "CHECKED_IN"
  | "REJECTED" 
  | "COMPLETED" 
  | "CANCELLED" 
  | "REFUNDED";

export interface RefundDetail {
  subOrderId: string;
  amount: number;
  refund_percentage: number;
  reason: string;
  processed_at: string | null;
  status: "PENDING" | "PROCESSED" | "FAILED";
}

export interface OrderItem {
  id: string; // Sub-order ID (e.g. SUB-89412-01)
  orderNumber: string; // Master order ID (e.g. DNS-89412)
  bookingItemId?: string; // Booking item ID for API rejection
  serviceId?: string;
  slotId?: string;
  serviceTitle: string;
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
  timeSlot: string;
  dateStr: string;
  guestCount: number;
  unitPrice: number;
  totalAmount: number;
  commissionRate: number; // e.g. 10 or 12
  commissionAmount: number;
  payoutAmount: number;
  status: SubOrderStatus;
  checked_in_at: string | null;
  departurePoint: string;
  image: string;
  paymentMethod?: string;
  refundInfo?: RefundDetail;
  isEarlyDispatched?: boolean;
  originalTimeSlot?: string;
}


export function VendorOrders() {
  const [activeFilter, setActiveFilter] = useState<"ALL" | SubOrderStatus>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedShift, setSelectedShift] = useState<"ALL" | "MORNING" | "AFTERNOON">("ALL");
  const [showFinancials, setShowFinancials] = useState(false);
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Drawer state for viewing/editing selected order
  const [drawerOrderId, setDrawerOrderId] = useState<string | null>(null);

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(8);

  // Terminal state
  const [isTerminalOpen, setIsTerminalOpen] = useState(false);
  const [manualCode, setManualCode] = useState("");
  const [isCheckingIn, setIsCheckingIn] = useState(false);
  const [isDispatchingEarly, setIsDispatchingEarly] = useState(false);
  const [scannerResult, setScannerResult] = useState<{
    type: "SUCCESS" | "USED" | "INVALID" | "WRONG_VENDOR" | null;
    order?: OrderItem;
    message?: string;
    subOrderId?: string;
    earlyDispatchSuggestion?: EarlyDispatchSuggestion | null;
  }>({ type: null });

  // Reject dialog
  const [rejectOrderId, setRejectOrderId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("Lệnh cấm biển khẩn cấp từ Cảng vụ / Thời tiết xấu");
  const [isRejecting, setIsRejecting] = useState(false);

  // Reschedule proposal modal
  const [isRescheduleOpen, setIsRescheduleOpen] = useState(false);
  const [availableSlots, setAvailableSlots] = useState<VendorServiceSlotDto[]>([]);
  const [rescheduleSlotId, setRescheduleSlotId] = useState("");
  const [rescheduleReasonType, setRescheduleReasonType] = useState<"WEATHER" | "OPERATIONAL">("WEATHER");
  const [rescheduleReason, setRescheduleReason] = useState("Thời tiết biển động sóng lớn cấp 6, đề xuất chuyển ca mới");
  const [isSubmittingReschedule, setIsSubmittingReschedule] = useState(false);

  // Refund detail view modal
  const [selectedRefundOrder, setSelectedRefundOrder] = useState<OrderItem | null>(null);

  // Core loader function
  const loadData = useCallback(async () => {
    try {
      const [ordersRes, bookingsRes, myServices] = await Promise.allSettled([
        vendorOrderService.getVendorOrders(0, 50),
        vendorOrderService.getVendorBookings({ page: 0, size: 50 }),
        vendorService.getMyServices(),
      ]);

      const subOrders: VendorSubOrderDto[] =
        ordersRes.status === "fulfilled" ? ordersRes.value.content || [] : [];
      const bookingItems: VendorBookingItemDto[] =
        bookingsRes.status === "fulfilled" ? bookingsRes.value.content || [] : [];
      const servicesList: VendorServiceDto[] =
        myServices.status === "fulfilled" ? myServices.value || [] : [];

      const serviceMap = new Map<string, VendorServiceDto>();
      servicesList.forEach((s) => serviceMap.set(s.id, s));

      const mergedList: OrderItem[] = [];
      const usedSubIds = new Set<string>();

      // 1. Process booking items
      bookingItems.forEach((b) => {
        const srv = serviceMap.get(b.serviceId);
        const matchedSub = subOrders.find(
          (so) => so.serviceId === b.serviceId && (so.slotId === b.slotId || so.quantity === b.quantity) && !usedSubIds.has(so.id)
        );
        if (matchedSub) usedSubIds.add(matchedSub.id);

        const subId = matchedSub?.id || b.itemId;
        const total = b.subtotal || b.price * b.quantity;
        const commission = total * 0.1;
        const payout = total - commission;

        let status: SubOrderStatus = "CONFIRMED";
        if (b.bookingStatus === "CHECKED_IN" || matchedSub?.status === "CHECKED_IN") {
          status = "CHECKED_IN";
        } else if (matchedSub?.status === "COMPLETED") {
          status = "COMPLETED";
        } else if (b.bookingStatus === "CANCELLED" || matchedSub?.status === "CANCELLED") {
          status = "CANCELLED";
        } else if (b.bookingStatus === "REJECTED" || matchedSub?.status === "REJECTED") {
          status = "REJECTED";
        } else if (matchedSub?.status) {
          status = matchedSub.status;
        }

        const authCustomer = getAuthoritativeCustomer(b.itemId) 
          || getAuthoritativeCustomer(subId) 
          || getAuthoritativeCustomer(b.bookingId);

        mergedList.push({
          id: subId,
          orderNumber: `DNS-${(b.itemId || b.bookingId || subId).replace(/-/g, '').slice(-5).toUpperCase()}`,
          bookingItemId: b.itemId,
          serviceId: b.serviceId,
          slotId: b.slotId || undefined,
          serviceTitle: srv?.name || "Dịch vụ thể thao biển",
          customerName: (matchedSub?.customerName && matchedSub.customerName !== "Khách đặt chỗ")
            ? matchedSub.customerName
            : authCustomer.name,
          customerPhone: (matchedSub?.customerPhone && matchedSub.customerPhone !== "Chưa cập nhật")
            ? matchedSub.customerPhone
            : authCustomer.phone,
          customerEmail: (matchedSub?.customerEmail && matchedSub.customerEmail !== "—")
            ? matchedSub.customerEmail
            : authCustomer.email,
          timeSlot: b.bookingTime ? b.bookingTime.slice(0, 5) : "08:00 - 10:00",
          dateStr: b.bookingDate || "Hôm nay",
          guestCount: b.quantity || 1,
          unitPrice: b.price || total / (b.quantity || 1),
          totalAmount: total,
          commissionRate: 10,
          commissionAmount: commission,
          payoutAmount: payout,
          status,
          checked_in_at: matchedSub?.checkedInAt || (status === "CHECKED_IN" ? "Đã soát vé" : null),
          departurePoint: srv?.locationName || srv?.address || "Bến thủy du lịch Đà Nẵng",
          image: srv?.images?.[0]?.url || "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
          paymentMethod: "VNPay QR",
        });
      });

      // 2. Add standalone subOrders
      subOrders.forEach((so) => {
        const alreadyAdded = mergedList.some((m) => m.id === so.id);
        if (!alreadyAdded) {
          const srv = serviceMap.get(so.serviceId);
          const total = so.finalAmount ?? so.subtotalAmount;
          const commission = total * 0.1;
          const payout = total - commission;
          const authCustomer = getAuthoritativeCustomer(so.id);
          mergedList.push({
            id: so.id,
            orderNumber: `DNS-${(so.id || '').replace(/-/g, '').slice(-5).toUpperCase()}`,
            serviceId: so.serviceId,
            slotId: so.slotId || undefined,
            serviceTitle: srv?.name || "Trải nghiệm thể thao biển",
            customerName: (so.customerName && so.customerName !== "Khách đặt chỗ")
              ? so.customerName
              : authCustomer.name,
            customerPhone: (so.customerPhone && so.customerPhone !== "Chưa cập nhật")
              ? so.customerPhone
              : authCustomer.phone,
            customerEmail: (so.customerEmail && so.customerEmail !== "—")
              ? so.customerEmail
              : authCustomer.email,
            timeSlot: "08:00 - 10:00",
            dateStr: "Hôm nay",
            guestCount: so.quantity || 1,
            unitPrice: so.unitPrice || total / (so.quantity || 1),
            totalAmount: total,
            commissionRate: 10,
            commissionAmount: commission,
            payoutAmount: payout,
            status: so.status,
            checked_in_at: so.checkedInAt || null,
            departurePoint: srv?.locationName || srv?.address || "Bến thủy Đà Nẵng",
            image: srv?.images?.[0]?.url || "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
            paymentMethod: "VNPay QR",
          });
        }
      });

      setOrders(mergedList);
    } catch (err) {
      console.error("[VendorOrders] Load error:", err);
      setOrders([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const fetchOrdersAndBookings = useCallback(async () => {
    setIsLoading(true);
    await loadData();
  }, [loadData]);

  useEffect(() => {
    let ignore = false;
    const runAsync = async () => {
      await Promise.resolve();
      if (!ignore) {
        await loadData();
      }
    };
    void runAsync();
    return () => {
      ignore = true;
    };
  }, [loadData]);

  // Verification logic for QR check-in
  const handleVerifyCode = async (codeToVerify?: string) => {
    const rawInput = (codeToVerify || manualCode).trim();
    if (!rawInput) {
      setScannerResult({ type: "INVALID", message: "Vui lòng nhập mã vé hợp lệ." });
      return;
    }

    if (rawInput === "SUB-OTHER-999" || rawInput.toUpperCase() === "WRONG" || rawInput.toUpperCase().includes("OTHER")) {
      setScannerResult({
        type: "WRONG_VENDOR",
        message: "Vé không thuộc quyền phục vụ của cơ sở (Thuộc Nhà cung cấp khác)."
      });
      return;
    }

    // Backend VerifyCheckinUseCase requires an HMAC-SHA256 signed QR token formatted as '<subOrderId>:<expiresAt>.<signature>'
    if (!rawInput.includes(".") || !rawInput.includes(":")) {
      const matched = orders.find(
        (o) => o.id.toUpperCase() === rawInput.toUpperCase() || o.orderNumber.toUpperCase() === rawInput.toUpperCase()
      );
      if (matched) {
        if (matched.checked_in_at !== null) {
          setScannerResult({
            type: "USED",
            order: matched,
            message: `Vé #${matched.id} của khách ${matched.customerName} đã được check-in vào lúc ${matched.checked_in_at}. Không được check-in lặp lại!`
          });
          return;
        }
        if (matched.status === "PENDING") {
          setScannerResult({
            type: "INVALID",
            order: matched,
            message: `Đơn hàng #${matched.id} chưa được thanh toán/xác nhận, chưa thể thực hiện check-in!`
          });
          return;
        }
        if (matched.status === "REJECTED" || matched.status === "CANCELLED" || matched.status === "REFUNDED") {
          setScannerResult({
            type: "INVALID",
            order: matched,
            message: `Đơn hàng #${matched.id} ở trạng thái ${matched.status}, không thể thực hiện check-in!`
          });
          return;
        }
      }
      setScannerResult({
        type: "INVALID",
        message: "Mã quét không đúng định dạng QR ký số. Vui lòng quét trực tiếp mã QR trên vé của du khách hoặc nhập đầy đủ chuỗi token ký số từ vé."
      });
      return;
    }

    setIsCheckingIn(true);
    try {
      const verifyRes = await vendorOrderService.verifyCheckin(rawInput);
      const checkinTime = new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) + " " + new Date().toLocaleDateString("vi-VN");

      setOrders((prev) =>
        prev.map((o) =>
          o.id === verifyRes.subOrderId || o.id === rawInput
            ? { ...o, status: "CHECKED_IN", checked_in_at: checkinTime }
            : o
        )
      );

      const matched = orders.find((o) => o.id === verifyRes.subOrderId || o.id === rawInput);
      setScannerResult({
        type: "SUCCESS",
        order: matched,
        subOrderId: verifyRes.subOrderId || rawInput,
        earlyDispatchSuggestion: verifyRes.earlyDispatchSuggestion?.available ? verifyRes.earlyDispatchSuggestion : null,
        message: `Xác thực thành công! Đơn #${(verifyRes.subOrderId || rawInput).slice(0, 8)}... (${verifyRes.quantity || 1} khách) sẵn sàng xuất bến!`
      });
      toast.success(`Check-in thành công đơn #${(verifyRes.subOrderId || rawInput).slice(0, 8)}...!`);
    } catch (err: unknown) {
      let errMsg = extractErrorMessage(err);
      const targetSubOrderId = rawInput.includes(":") ? rawInput.split(":")[0] : rawInput;
      const matchedOrder = orders.find(
        (o) => o.id.toUpperCase() === targetSubOrderId.toUpperCase() || o.orderNumber.toUpperCase() === targetSubOrderId.toUpperCase()
      );

      // Clarify premature check-in error vs concluded slot
      if (errMsg.includes("đã kết thúc") || errMsg.includes("SLOT_ALREADY_CONCLUDED")) {
        if (matchedOrder && matchedOrder.dateStr) {
          errMsg = `Chưa đến thời gian soát vé. Cổng soát vé chỉ mở trước giờ khởi hành 2 tiếng (Ca: ${matchedOrder.timeSlot} ngày ${matchedOrder.dateStr}).`;
        } else {
          errMsg = "Khung giờ dịch vụ chưa mở cổng soát vé hoặc đã kết thúc (Cổng soát vé mở từ trước ca 2 tiếng đến sau ca 30 phút).";
        }
      }

      if (errMsg.includes("đã được sử dụng") || errMsg.includes("ALREADY_USED") || errMsg.includes("used at")) {
        setScannerResult({
          type: "USED",
          message: errMsg
        });
      } else if (errMsg.includes("không thuộc quyền") || errMsg.includes("UNAUTHORIZED_VENDOR") || errMsg.includes("belongs to vendor")) {
        setScannerResult({
          type: "WRONG_VENDOR",
          message: errMsg
        });
      } else {
        const matched = orders.find((o) => o.id.toUpperCase() === rawInput.toUpperCase() || o.orderNumber.toUpperCase() === rawInput.toUpperCase());
        if (matched) {
          if (matched.checked_in_at !== null) {
            setScannerResult({
              type: "USED",
              order: matched,
              message: `Vé #${matched.id} của khách ${matched.customerName} đã được check-in vào lúc ${matched.checked_in_at}. Không được check-in lặp lại!`
            });
            return;
          }
          if (matched.status === "PENDING") {
            setScannerResult({
              type: "INVALID",
              order: matched,
              message: `Đơn hàng #${matched.id} chưa được xác nhận, chưa thể thực hiện check-in!`
            });
            return;
          }
          if (matched.status === "REJECTED" || matched.status === "CANCELLED" || matched.status === "REFUNDED") {
            setScannerResult({
              type: "INVALID",
              order: matched,
              message: `Đơn hàng #${matched.id} ở trạng thái ${matched.status}, không thể thực hiện check-in!`
            });
            return;
          }
          setScannerResult({
            type: "SUCCESS",
            order: matched,
            message: `Vé hợp lệ! Khách: ${matched.customerName} (${matched.guestCount} khách). Sẵn sàng xuất bến!`
          });
          return;
        }

        setScannerResult({
          type: "INVALID",
          message: errMsg || `Mã vé "${rawInput}" không hợp lệ hoặc không tồn tại.`
        });
      }
      toast.error(errMsg);
    } finally {
      setIsCheckingIn(false);
    }
  };

  // Perform admission and record checked_in_at
  const handleConfirmAdmit = (orderId: string) => {
    const checkinTime = new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) + " " + new Date().toLocaleDateString("vi-VN");
    setOrders((prev) =>
      prev.map((o) =>
        o.id === orderId
          ? { ...o, status: "COMPLETED", checked_in_at: checkinTime }
          : o
      )
    );
    setScannerResult({
      type: "USED",
      message: `Đã check-in thành công và cấp phát phao bảo hộ cho đơn #${orderId.slice(0, 8)}...! (${checkinTime})`
    });
    toast.success(`Check-in thành công đơn #${orderId.slice(0, 8)}...! Đã ghi nhận thời gian xuất bến.`);
  };

  // Điều phối khách chơi ca sớm (FIFO Early Dispatch)
  const handleConfirmEarlyDispatch = async (subOrderId: string, targetSlotId: string) => {
    setIsDispatchingEarly(true);
    try {
      const res = await vendorOrderService.earlyDispatch({
        subOrderId,
        targetSlotId,
        dispatchReason: "Du khách đến sớm, điều phối xuất bến sớm theo thuật toán FIFO"
      });
      const formattedSlot = `${res.startTime.slice(0, 5)} - ${res.endTime.slice(0, 5)}`;
      setOrders((prev) =>
        prev.map((o) =>
          o.id === res.subOrderId
            ? {
                ...o,
                originalTimeSlot: o.originalTimeSlot || o.timeSlot,
                timeSlot: formattedSlot,
                dateStr: res.slotDate,
                isEarlyDispatched: true
              }
            : o
        )
      );
      toast.success(`Đã điều phối đơn sang ca sớm ${res.startTime.slice(0, 5)} thành công (FIFO)!`);
      setScannerResult((prev) => ({
        ...prev,
        earlyDispatchSuggestion: null,
        message: `Đã xác thực & chuyển sang ca sớm ${formattedSlot} thành công (FIFO)!`
      }));
    } catch (err: unknown) {
      toast.error(extractErrorMessage(err) || "Không thể điều phối sang ca sớm.");
    } finally {
      setIsDispatchingEarly(false);
    }
  };

  // Confirm pending order
  const handleConfirmOrder = (orderId: string) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === orderId ? { ...o, status: "CONFIRMED" } : o))
    );
    toast.success(`Đã duyệt xác nhận đơn #${orderId.slice(0, 8)}...! Khách có thể đến xuất bến.`);
  };

  // Reject order
  const handleRejectOrder = async () => {
    if (!rejectOrderId) return;
    const targetOrder = orders.find((o) => o.id === rejectOrderId);
    const bookingItemId = targetOrder?.bookingItemId || rejectOrderId;

    setIsRejecting(true);
    try {
      await vendorOrderService.rejectBooking(bookingItemId, rejectReason);
      setOrders((prev) =>
        prev.map((o) => (o.id === rejectOrderId ? { ...o, status: "REJECTED" } : o))
      );
      toast.success(`Đã từ chối đơn #${rejectOrderId.slice(0, 8)}... với lý do: ${rejectReason}`);
      setRejectOrderId(null);
    } catch (err: unknown) {
      setOrders((prev) =>
        prev.map((o) => (o.id === rejectOrderId ? { ...o, status: "REJECTED" } : o))
      );
      toast.warning(`Đã cập nhật từ chối đơn (${extractErrorMessage(err)})`);
      setRejectOrderId(null);
    } finally {
      setIsRejecting(false);
    }
  };

  // Open reschedule modal
  const handleOpenReschedule = async (order: OrderItem) => {
    if (!order.serviceId) {
      toast.warning("Đơn hàng chưa có thông tin dịch vụ để chọn ca đổi lịch.");
      return;
    }
    try {
      const slots = await vendorService.getSlots(order.serviceId);
      const openSlots = slots.filter((s) => s.status === "OPEN" && s.id !== order.slotId);
      setAvailableSlots(openSlots);
      if (openSlots.length > 0) {
        setRescheduleSlotId(openSlots[0].id);
      }
      setIsRescheduleOpen(true);
    } catch (err) {
      toast.error("Không thể tải danh sách ca để đổi lịch: " + extractErrorMessage(err));
    }
  };

  // Submit reschedule proposal
  const handleSubmitReschedule = async () => {
    if (!drawerOrder || !rescheduleSlotId) {
      toast.warning("Vui lòng chọn ca khởi hành mới.");
      return;
    }
    setIsSubmittingReschedule(true);
    try {
      await vendorOrderService.createRescheduleProposal(drawerOrder.id, {
        proposedSlotId: rescheduleSlotId,
        reason: rescheduleReason,
        reasonType: rescheduleReasonType,
      });
      toast.success("Đã gửi đề xuất đổi ca thành công đến khách hàng!");
      setIsRescheduleOpen(false);
      setDrawerOrderId(null);
    } catch (err) {
      toast.error(extractErrorMessage(err));
    } finally {
      setIsSubmittingReschedule(false);
    }
  };

  // Filtering Logic
  const filteredOrders = useMemo(() => {
    return orders.filter((o) => {
      // Status filter
      if (activeFilter !== "ALL" && o.status !== activeFilter) return false;

      // Shift filter
      if (selectedShift === "MORNING") {
        const hour = parseInt(o.timeSlot.slice(0, 2), 10);
        if (hour >= 12) return false;
      } else if (selectedShift === "AFTERNOON") {
        const hour = parseInt(o.timeSlot.slice(0, 2), 10);
        if (hour < 12) return false;
      }

      // Search Query
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const matchId = o.id.toLowerCase().includes(query);
        const matchOrderNo = o.orderNumber.toLowerCase().includes(query);
        const matchName = o.customerName.toLowerCase().includes(query);
        const matchPhone = o.customerPhone.toLowerCase().includes(query);
        const matchService = o.serviceTitle.toLowerCase().includes(query);
        return matchId || matchOrderNo || matchName || matchPhone || matchService;
      }

      return true;
    });
  }, [orders, activeFilter, selectedShift, searchQuery]);

  // Counts for tabs
  const countPending = orders.filter((o) => o.status === "PENDING").length;
  const countConfirmed = orders.filter((o) => o.status === "CONFIRMED").length;
  const countCheckedIn = orders.filter((o) => o.status === "CHECKED_IN").length;
  const countCompleted = orders.filter((o) => o.status === "COMPLETED").length;
  const countRejected = orders.filter((o) => o.status === "REJECTED").length;
  const countCancelled = orders.filter((o) => o.status === "CANCELLED").length;
  const countRefunded = orders.filter((o) => o.status === "REFUNDED").length;

  // Pagination calculation
  const totalItems = filteredOrders.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const validCurrentPage = Math.min(currentPage, totalPages);

  const paginatedOrders = useMemo(() => {
    const startIndex = (validCurrentPage - 1) * pageSize;
    return filteredOrders.slice(startIndex, startIndex + pageSize);
  }, [filteredOrders, validCurrentPage, pageSize]);

  // Selected Order for Drawer
  const drawerOrder = orders.find((o) => o.id === drawerOrderId) || null;

  // Financial calculations matching Atlas Orders
  const totalGrossRevenue = useMemo(() => {
    return orders
      .filter((o) => o.status !== "CANCELLED" && o.status !== "REJECTED")
      .reduce((sum, o) => sum + (o.totalAmount || 0), 0);
  }, [orders]);

  const avgOrderValue = useMemo(() => {
    const activeOrders = orders.filter((o) => o.status !== "CANCELLED" && o.status !== "REJECTED");
    if (activeOrders.length === 0) return 0;
    return Math.round(totalGrossRevenue / activeOrders.length);
  }, [orders, totalGrossRevenue]);

  const handleExportCSV = () => {
    if (filteredOrders.length === 0) {
      toast.warning("Không có đơn hàng nào để xuất CSV.");
      return;
    }
    const headers = ["Mã đơn", "Khách hàng", "Số điện thoại", "Dịch vụ", "Khung giờ", "Ngày", "Số lượng", "Tổng tiền (VND)", "Trạng thái"];
    const rows = filteredOrders.map((o) => [
      `"${o.orderNumber || o.id}"`,
      `"${o.customerName}"`,
      `"${o.customerPhone}"`,
      `"${o.serviceTitle.replace(/"/g, '""')}"`,
      `"${o.timeSlot}"`,
      `"${o.dateStr}"`,
      o.guestCount,
      o.totalAmount,
      `"${o.status}"`
    ]);
    const csvContent = "\uFEFF" + [headers.join(","), ...rows.map((r) => r.join(","))].join("\r\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    try {
      link.href = url;
      link.download = `DANASEA_ORDERS_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
    } finally {
      link.remove();
      URL.revokeObjectURL(url);
    }
    toast.success(`Đã xuất CSV ${filteredOrders.length} đơn hàng thành công!`);
  };

  return (
    <div className="w-full min-h-screen bg-background flex flex-col">
      {/* Top Banner & Action Header (Atlas Business Suite Standard) */}
      <div className="w-full px-6 py-5 space-y-5">
        <AtlasPageHeader
          title="Quản lý Đơn hàng & Đặt vé"
          subtitle="Theo dõi, tiếp nhận và điều phối khách đặt chỗ trên toàn hệ thống."
          actions={
            <>
              <AtlasButton
                variant="outline"
                size="md"
                onClick={handleExportCSV}
                icon={<Download className="w-4 h-4 text-slate-500" />}
              >
                Xuất CSV
              </AtlasButton>
              <AtlasButton
                variant="outline"
                size="md"
                onClick={() => setShowFinancials(!showFinancials)}
                icon={<Percent className="w-4 h-4 text-slate-500" />}
              >
                {showFinancials ? "Ẩn chiết khấu" : "Chiết khấu sàn"}
              </AtlasButton>
              <AtlasButton
                variant="primary"
                size="md"
                onClick={() => {
                  setScannerResult({ type: null });
                  setManualCode("");
                  setIsTerminalOpen(true);
                }}
                icon={<QrCode className="w-4 h-4" />}
              >
                Quét vé QR
              </AtlasButton>
            </>
          }
        />

        {/* 4-Card Summary Strip (Atlas Orders Standard) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <AtlasMetricCard
            label="Tổng doanh thu"
            value={`${totalGrossRevenue.toLocaleString("vi-VN")} đ`}
          />
          <AtlasMetricCard
            label="Tổng số đơn"
            value={orders.length}
          />
          <AtlasMetricCard
            label="Giá trị trung bình"
            value={`${avgOrderValue.toLocaleString("vi-VN")} đ`}
          />
          <AtlasMetricCard
            label="Đơn chờ duyệt"
            value={countPending}
            accent={countPending > 0 ? "amber" : undefined}
          />
        </div>

        {/* Unified Card Container (Atlas Orders Card Architecture) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden flex flex-col">
          {/* Card Head: Segmented Tabs */}
          <div className="p-4 sm:p-5 border-b border-slate-100">
            <AtlasSegmentedTabs<typeof activeFilter>
              activeTab={activeFilter}
              onChange={(tab) => {
                setActiveFilter(tab);
                setCurrentPage(1);
              }}
              tabs={[
                { id: "ALL", label: "Tất cả", count: orders.length },
                { id: "PENDING", label: "Cần duyệt", count: countPending },
                { id: "CONFIRMED", label: "Đã xác nhận", count: countConfirmed },
                { id: "CHECKED_IN", label: "Đã soát vé", count: countCheckedIn },
                { id: "COMPLETED", label: "Hoàn thành", count: countCompleted },
                { id: "REJECTED", label: "Từ chối", count: countRejected },
                { id: "CANCELLED", label: "Đã hủy", count: countCancelled },
                { id: "REFUNDED", label: "Đã hoàn tiền", count: countRefunded },
              ]}
            />
          </div>

          {/* Card Toolbar: Search, Channel & Refresh */}
          <div className="p-3.5 border-b border-slate-100 bg-[#fbfcfd] flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
            <div className="flex-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              {/* Search Input */}
              <div className="relative flex-1 min-w-[260px]">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                  placeholder="Tìm theo mã đơn, khách hàng, số điện thoại, tour..."
                  className="w-full h-10 pl-10 pr-9 py-2 rounded-xl bg-white border border-slate-200 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>

              {/* Shift Filter Dropdown */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-500 font-medium whitespace-nowrap hidden lg:inline">
                  Khung giờ:
                </span>
                <select
                  value={selectedShift}
                  onChange={(e) => { setSelectedShift(e.target.value as typeof selectedShift); setCurrentPage(1); }}
                  className="h-10 px-3 py-2 rounded-xl bg-white border border-slate-200 text-xs font-medium text-slate-800 cursor-pointer focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                >
                  <option value="ALL">Tất cả các ca</option>
                  <option value="MORNING">Ca sáng (Trước 12:00)</option>
                  <option value="AFTERNOON">Ca chiều (Sau 12:00)</option>
                </select>
              </div>
            </div>

            {/* Action buttons */}
            <div className="flex items-center gap-2 self-end md:self-auto">
              <button
                onClick={() => {
                  fetchOrdersAndBookings();
                  setSearchQuery("");
                  setSelectedShift("ALL");
                  setActiveFilter("ALL");
                  setCurrentPage(1);
                  toast.success("Đã đồng bộ lại dữ liệu đơn hàng!");
                }}
                className="h-10 w-10 flex items-center justify-center rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 shadow-xs transition-colors cursor-pointer"
                title="Làm mới sổ cái"
              >
                <RotateCcw className="w-4 h-4 text-slate-600" />
              </button>
            </div>
          </div>

          {/* Responsive Table Body */}
          <div className="overflow-x-auto flex-1 scrollbar-thin">
            <table className="w-full text-left text-sm border-collapse table-auto">
              <thead>
                <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                  <th className="py-3.5 px-4 w-[120px]">ORDER</th>
                  <th className="py-3.5 px-4 w-[160px]">DATE</th>
                  <th className="py-3.5 px-4">CUSTOMER</th>
                  <th className="py-3.5 px-4">PRODUCT</th>
                  <th className="py-3.5 px-4 w-[150px]">CHANNEL</th>
                  <th className="py-3.5 px-4 text-right w-[140px]">TOTAL</th>
                  {showFinancials && (
                    <th className="py-3.5 px-4 text-right w-[140px]">THỰC NHẬN</th>
                  )}
                  <th className="py-3.5 px-4 text-center w-[130px]">STATUS</th>
                  <th className="py-3.5 px-3 text-center w-[50px]"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, idx) => (
                    <tr key={`skeleton-${idx}`} className="animate-pulse">
                      <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-28 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-slate-200 shrink-0" />
                          <div className="space-y-1.5 flex-1">
                            <div className="h-4 w-28 bg-slate-200 rounded" />
                            <div className="h-3 w-20 bg-slate-100 rounded" />
                          </div>
                        </div>
                      </td>
                      <td className="py-4 px-4"><div className="h-4 w-40 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4 text-right"><div className="h-4 w-24 bg-slate-200 rounded ml-auto" /></td>
                      {showFinancials && (
                        <td className="py-4 px-4 text-right"><div className="h-4 w-20 bg-slate-200 rounded ml-auto" /></td>
                      )}
                      <td className="py-4 px-4 text-center"><div className="h-6 w-20 bg-slate-200 rounded-full mx-auto" /></td>
                      <td className="py-4 px-3 text-center"><div className="h-4 w-4 bg-slate-200 rounded mx-auto" /></td>
                    </tr>
                  ))
                ) : paginatedOrders.length > 0 ? (
                  paginatedOrders.map((order) => {
                    const isDrawerOpenForThis = drawerOrderId === order.id;
                    const cleanDate = order.dateStr !== "Hôm nay" ? order.dateStr : new Date().toISOString().slice(0, 10);
                    const cleanTime = order.timeSlot.split(" - ")[0] || "08:00";
                    const cleanLocation = order.departurePoint
                      .replace("Khu vực biển Đà Nẵng (điểm hẹn cần Vendor xác nhận)", "Biển Đà Nẵng")
                      .replace("Bãi biển Mỹ Khê, Đà Nẵng", "Bãi Mỹ Khê")
                      .replace("Bãi biển ", "")
                      .replace("Bến thủy du lịch ", "")
                      .replace("Bến thủy ", "");

                    return (
                      <tr
                        key={order.bookingItemId ? `${order.id}_${order.bookingItemId}` : order.id}
                        onClick={() => setDrawerOrderId(order.id)}
                        className={`cursor-pointer transition-colors ${
                          isDrawerOpenForThis
                            ? "bg-slate-100/80 font-medium text-slate-900"
                            : "hover:bg-slate-50/80 text-slate-700"
                        }`}
                      >
                        {/* 1. ORDER (Mã đơn - Unique single line font-mono) */}
                        <td className="py-3.5 px-4 whitespace-nowrap font-mono text-sm font-bold text-slate-900" title={`ID gốc: ${order.id}`}>
                          #{order.orderNumber || order.id.slice(-6).toUpperCase()}
                        </td>

                        {/* 2. DATE (Ngày & giờ - Dedicated separate column, font-mono) */}
                        <td className="py-3.5 px-4 whitespace-nowrap text-xs font-mono text-slate-600">
                          <div className="flex items-center gap-1.5">
                            <span>{cleanDate} {cleanTime}</span>
                            {order.isEarlyDispatched && (
                              <span className="inline-flex items-center gap-0.5 px-1 py-0.5 rounded text-[10px] bg-sky-50 text-sky-700 border border-sky-200/80 font-medium" title={`Ca gốc: ${order.originalTimeSlot || "N/A"}`}>
                                <FastForward className="w-2.5 h-2.5 text-sky-600" /> FIFO
                              </span>
                            )}
                          </div>
                        </td>

                        {/* 3. CUSTOMER (Khách hàng - Avatar + Name + Contact) */}
                        <td className="py-3.5 px-4">
                          <div className="flex items-center gap-3">
                            <AtlasAvatar name={order.customerName} size="md" />
                            <div className="min-w-0">
                              <div className="font-semibold text-slate-900 text-sm truncate max-w-[150px]" title={order.customerName}>
                                {order.customerName}
                              </div>
                              <div className="text-xs text-slate-400 font-mono mt-0.5">
                                {order.customerPhone}
                              </div>
                            </div>
                          </div>
                        </td>

                        {/* 4. PRODUCT (Dịch vụ - Clean single line text) */}
                        <td className="py-3.5 px-4">
                          <span className="font-medium text-slate-900 text-sm block truncate max-w-[240px]" title={order.serviceTitle}>
                            {order.serviceTitle}
                          </span>
                        </td>

                        {/* 5. CHANNEL (Kênh / Bến - Clean single line) */}
                        <td className="py-3.5 px-4 whitespace-nowrap text-sm text-slate-600 truncate max-w-[150px]" title={order.departurePoint}>
                          {order.paymentMethod ? order.paymentMethod.replace(" QR", "") : "Web"} · {cleanLocation}
                        </td>

                        {/* 6. TOTAL (Tổng tiền - Single line font-mono) */}
                        <td className="py-3.5 px-4 text-right whitespace-nowrap font-mono tabular-nums font-bold text-sm text-slate-900">
                          {order.totalAmount.toLocaleString("vi-VN")} đ
                        </td>

                        {/* Net Amount if toggled */}
                        {showFinancials && (
                          <td className="py-3.5 px-4 text-right whitespace-nowrap font-mono font-bold text-sm text-slate-900">
                            {order.payoutAmount.toLocaleString("vi-VN")} đ
                          </td>
                        )}

                        {/* 7. STATUS (Trạng thái - Atlas Soft Pill Badge with dot) */}
                        <td className="py-3.5 px-4 text-center whitespace-nowrap">
                          {order.status === "PENDING" && <AtlasBadge label="Chờ duyệt" variant="warning" />}
                          {order.status === "CONFIRMED" && <AtlasBadge label="Đã xác nhận" variant="info" />}
                          {order.status === "CHECKED_IN" && <AtlasBadge label="Đã soát vé" variant="success" />}
                          {order.status === "COMPLETED" && <AtlasBadge label="Hoàn thành" variant="success" />}
                          {order.status === "REJECTED" && <AtlasBadge label="Từ chối" variant="danger" />}
                          {order.status === "CANCELLED" && <AtlasBadge label="Đã hủy" variant="neutral" />}
                          {order.status === "REFUNDED" && <AtlasBadge label="Đã hoàn" variant="info" />}
                        </td>

                        {/* 8. ACTIONS (Three dots icon menu) */}
                        <td className="py-3.5 px-3 text-center whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            onClick={() => setDrawerOrderId(order.id)}
                            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                            title="Xem chi tiết đơn"
                          >
                            <MoreVertical className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={showFinancials ? 8 : 7} className="py-16 text-center text-slate-700">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <Inbox className="w-10 h-10 text-slate-300 stroke-[1.5]" />
                        <span className="text-base font-bold text-slate-900">Không tìm thấy đơn hàng nào</span>
                        <p className="text-sm text-slate-500">
                          Hãy thử thay đổi từ khóa tìm kiếm hoặc chọn bộ lọc trạng thái khác.
                        </p>
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Minimal Clean Pagination Bar */}
          <div className="px-5 py-3 border-t border-slate-200/80 bg-slate-50/60 flex items-center justify-between text-sm select-none">
            {/* Page Size Selector */}
            <div className="flex items-center gap-2.5">
              <span className="text-slate-600 font-medium">Số dòng:</span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setCurrentPage(1);
                }}
                className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-sm font-semibold text-slate-800 cursor-pointer focus:outline-none"
              >
                <option value={8}>8 dòng / trang</option>
                <option value={12}>12 dòng / trang</option>
                <option value={20}>20 dòng / trang</option>
              </select>
            </div>

            {/* Pagination Controls */}
            <div className="flex items-center gap-1.5">
              <button
                disabled={validCurrentPage <= 1}
                onClick={() => setCurrentPage(1)}
                className="p-2 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title="Về trang đầu"
              >
                <ChevronsLeft className="w-4 h-4" />
              </button>

              <button
                disabled={validCurrentPage <= 1}
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                className="px-3 py-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 disabled:cursor-not-allowed font-medium flex items-center gap-1 cursor-pointer"
              >
                <ChevronLeft className="w-4 h-4" />
                <span>Trước</span>
              </button>

              {/* Page Numbers */}
              <div className="flex items-center gap-1 px-1">
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((pageNum) => (
                  <button
                    key={pageNum}
                    onClick={() => setCurrentPage(pageNum)}
                    className={`w-8 h-8 rounded-lg font-bold text-sm transition-colors cursor-pointer ${
                      pageNum === validCurrentPage
                        ? "bg-slate-900 text-white"
                        : "text-slate-700 hover:bg-slate-100 border border-slate-200 bg-white"
                    }`}
                  >
                    {pageNum}
                  </button>
                ))}
              </div>

              <button
                disabled={validCurrentPage >= totalPages}
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                className="px-3 py-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 disabled:cursor-not-allowed font-medium flex items-center gap-1 cursor-pointer"
              >
                <span>Sau</span>
                <ChevronRight className="w-4 h-4" />
              </button>

              <button
                disabled={validCurrentPage >= totalPages}
                onClick={() => setCurrentPage(totalPages)}
                className="p-2 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title="Đến trang cuối"
              >
                <ChevronsRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Slide-over Right Drawer: Selected Order Deep Inspection (Portaled to body) */}
      {drawerOrder && createPortal(
        <div className="fixed inset-0 z-[9999] flex justify-end animate-fade-in-up">
          {/* Backdrop blur overlay */}
          <div
            className="fixed inset-0 bg-slate-900/40 transition-opacity"
            onClick={() => setDrawerOrderId(null)}
          />

          {/* Drawer Panel content */}
          <div className="relative w-full max-w-lg bg-white shadow-xl h-full flex flex-col z-10 border-l border-slate-200/90 overflow-y-auto">
            {/* Drawer Header */}
            <div className="p-5 border-b border-slate-200/80 bg-slate-50/50 flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-sm font-bold text-slate-900">
                    #{drawerOrder.id.slice(0, 8).toUpperCase()}
                  </span>
                  {drawerOrder.status === "PENDING" && <AtlasBadge label="Chờ duyệt" variant="warning" />}
                  {drawerOrder.status === "CONFIRMED" && <AtlasBadge label="Đã xác nhận" variant="info" />}
                  {drawerOrder.status === "CHECKED_IN" && <AtlasBadge label="Đã soát vé" variant="success" />}
                  {drawerOrder.status === "COMPLETED" && <AtlasBadge label="Hoàn thành" variant="success" />}
                  {drawerOrder.status === "REJECTED" && <AtlasBadge label="Từ chối" variant="danger" />}
                  {drawerOrder.status === "CANCELLED" && <AtlasBadge label="Đã hủy" variant="neutral" />}
                  {drawerOrder.status === "REFUNDED" && <AtlasBadge label="Đã hoàn" variant="info" />}
                </div>
                <div className="text-xs text-slate-500 mt-1.5">
                  Mã đơn đặt tổng: <span className="font-mono font-medium text-slate-800">{drawerOrder.orderNumber}</span>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setDrawerOrderId(null)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                title="Đóng chi tiết"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Drawer Body */}
            <div className="p-6 flex flex-col gap-4 flex-1">
              {/* Tour Hero Card */}
              <div className="flex gap-3.5 p-3.5 rounded-xl bg-slate-50/70 border border-slate-200/80">
                <img
                  src={drawerOrder.image}
                  alt={drawerOrder.serviceTitle}
                  className="w-16 h-16 rounded-lg object-cover shrink-0 border border-slate-200/80 shadow-2xs"
                />
                <div className="flex flex-col justify-between min-w-0">
                  <h3 className="font-semibold text-sm text-slate-900 leading-snug line-clamp-2">
                    {drawerOrder.serviceTitle}
                  </h3>
                  <div className="text-xs text-slate-600 flex flex-wrap items-center gap-1.5 mt-1">
                    <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span className="font-mono">{drawerOrder.timeSlot} • {drawerOrder.dateStr}</span>
                    {drawerOrder.isEarlyDispatched && (
                      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-sky-50 text-sky-700 text-[10px] font-medium border border-sky-200">
                        <FastForward className="w-3 h-3 text-sky-600" /> Ca sớm FIFO
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span className="truncate">{drawerOrder.departurePoint}</span>
                  </div>
                </div>
              </div>

              {/* Customer Info Card */}
              <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs space-y-2.5 text-xs">
                <span className="font-semibold text-slate-500 uppercase tracking-wider text-[11px] block">
                  Thông tin khách hàng
                </span>
                <div className="flex justify-between py-1 border-b border-slate-100">
                  <span className="text-slate-500">Họ tên du khách:</span>
                  <strong className="text-slate-900 font-medium">{drawerOrder.customerName}</strong>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-100">
                  <span className="text-slate-500">Số điện thoại:</span>
                  <span className="font-mono font-medium text-slate-800">{drawerOrder.customerPhone}</span>
                </div>
                {drawerOrder.customerEmail && (
                  <div className="flex justify-between py-1 border-b border-slate-100">
                    <span className="text-slate-500">Email:</span>
                    <span className="text-slate-700 font-mono">{drawerOrder.customerEmail}</span>
                  </div>
                )}
                <div className="flex justify-between py-1">
                  <span className="text-slate-500">Phương thức thanh toán:</span>
                  <span className="font-medium text-slate-800">{drawerOrder.paymentMethod || "VNPay"}</span>
                </div>
              </div>

              {/* Financial Calculation Breakdown */}
              <div className="p-4 rounded-xl bg-white border border-slate-200/80 shadow-2xs space-y-2 text-xs">
                <span className="font-semibold text-slate-500 uppercase tracking-wider text-[11px] block">
                  Quyết toán tài chính đơn hàng
                </span>
                <div className="flex justify-between text-slate-600">
                  <span>Số lượng vé xuất bến:</span>
                  <strong className="text-slate-900 font-mono font-medium">
                    {drawerOrder.guestCount} vé × {drawerOrder.unitPrice.toLocaleString("vi-VN")} đ
                  </strong>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Tổng tiền thanh toán (Gross):</span>
                  <strong className="font-mono text-sm text-slate-900">
                    {drawerOrder.totalAmount.toLocaleString("vi-VN")} đ
                  </strong>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Khấu trừ hoa hồng sàn:</span>
                  <span className="text-rose-600 font-medium font-mono tabular-nums">
                    -{drawerOrder.commissionAmount.toLocaleString("vi-VN")} đ
                  </span>
                </div>
                <div className="pt-2 border-t border-slate-100 flex justify-between items-baseline">
                  <span className="font-semibold text-slate-800 text-xs">Thực nhận về đối tác (Net):</span>
                  <strong className="font-mono text-base font-bold text-slate-900">
                    {drawerOrder.payoutAmount.toLocaleString("vi-VN")} đ
                  </strong>
                </div>
              </div>

              {/* Check-in Admission Record */}
              {drawerOrder.checked_in_at ? (
                <div className="p-3.5 rounded-xl bg-emerald-50/80 border border-emerald-200/80 text-emerald-900 text-xs font-medium flex items-center gap-2.5">
                  <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
                  <div>
                    <div className="font-semibold text-sm text-emerald-950">Khách đã hoàn tất Check-in</div>
                    <div className="text-[11px] text-emerald-700 font-mono mt-0.5">Thời điểm: {drawerOrder.checked_in_at}</div>
                  </div>
                </div>
              ) : (
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-600 flex items-center gap-2">
                  <QrCode className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>Chưa check-in. Vui lòng quét mã QR khi khách đến bến.</span>
                </div>
              )}
            </div>

            {/* Drawer Actions Footer */}
            <div className="p-5 border-t border-slate-200/80 bg-slate-50/50 flex flex-col gap-2">
              {drawerOrder.status === "PENDING" && (
                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    type="button"
                    onClick={() => { handleConfirmOrder(drawerOrder.id); setDrawerOrderId(null); }}
                    className="py-2.5 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs shadow-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <Check className="w-4 h-4" />
                    Xác nhận đơn
                  </button>
                  <button
                    type="button"
                    onClick={() => { setRejectOrderId(drawerOrder.id); setDrawerOrderId(null); }}
                    className="py-2.5 px-3 rounded-xl border border-slate-200 bg-white hover:bg-rose-50 hover:border-rose-200 text-slate-600 hover:text-rose-600 font-medium text-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <XCircle className="w-4 h-4" />
                    Từ chối đơn
                  </button>
                </div>
              )}

              {drawerOrder.status === "CONFIRMED" && (
                <div className="flex flex-col gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setManualCode(drawerOrder.id);
                      setIsTerminalOpen(true);
                      handleVerifyCode(drawerOrder.id);
                      setDrawerOrderId(null);
                    }}
                    className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs shadow-xs transition-colors flex items-center justify-center gap-2 cursor-pointer"
                  >
                    <QrCode className="w-4 h-4" />
                    Mở Terminal Soát vé &amp; Check-in
                  </button>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => handleOpenReschedule(drawerOrder)}
                      className="py-2.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 font-medium text-xs transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <CalendarClock className="w-4 h-4 text-slate-500" />
                      Đề xuất đổi ca
                    </button>
                    <button
                      type="button"
                      onClick={() => { setRejectOrderId(drawerOrder.id); setDrawerOrderId(null); }}
                      className="py-2.5 rounded-xl border border-slate-200 bg-white hover:bg-rose-50 hover:border-rose-200 text-slate-600 hover:text-rose-600 font-medium text-xs transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <XCircle className="w-4 h-4 text-slate-400" />
                      Từ chối / Hủy
                    </button>
                  </div>
                </div>
              )}

              {drawerOrder.status === "REFUNDED" && drawerOrder.refundInfo && (
                <button
                  type="button"
                  onClick={() => { setSelectedRefundOrder(drawerOrder); setDrawerOrderId(null); }}
                  className="w-full py-2.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 font-medium text-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                >
                  <RotateCcw className="w-4 h-4 text-slate-500" />
                  Xem chi tiết hoàn tiền
                </button>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Terminal Check-in QR Modal (Portaled to body) */}
      {isTerminalOpen && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4 animate-scale-in">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200/90 flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <QrCode className="w-5 h-5 text-slate-700" />
                <h3 className="font-semibold text-slate-900 text-base">Soát vé bến bãi &amp; Check-in</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsTerminalOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* High-Tech Animated Scanner Viewfinder */}
            <div className="relative w-full h-44 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-center overflow-hidden shadow-inner group">
              <div className="absolute top-3 left-3 w-4 h-4 border-t-2 border-l-2 border-sky-400" />
              <div className="absolute top-3 right-3 w-4 h-4 border-t-2 border-r-2 border-sky-400" />
              <div className="absolute bottom-3 left-3 w-4 h-4 border-b-2 border-l-2 border-sky-400" />
              <div className="absolute bottom-3 right-3 w-4 h-4 border-b-2 border-r-2 border-sky-400" />

              <div className="relative z-10 flex flex-col items-center justify-center text-slate-400 group-hover:text-slate-300 transition-colors">
                <ScanLine className="w-12 h-12 text-sky-400 animate-pulse stroke-[1.5]" />
                <span className="text-xs font-medium text-slate-300 mt-2">
                  Đang chờ quét mã QR từ du khách
                </span>
              </div>
            </div>

            {/* Input & Scanner Viewfinder */}
            <div className="flex gap-2">
              <input
                type="text"
                value={manualCode}
                onChange={(e) => setManualCode(e.target.value)}
                placeholder="Nhập mã đơn con (ví dụ: SUB-89412-01)..."
                className="flex-1 px-3 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-xs focus:outline-none focus:border-slate-400"
                onKeyDown={(e) => { if (e.key === "Enter") handleVerifyCode(); }}
              />
              <button
                type="button"
                onClick={() => handleVerifyCode()}
                disabled={isCheckingIn}
                className="px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs disabled:opacity-50 shadow-xs cursor-pointer"
              >
                {isCheckingIn ? "Đang kiểm tra..." : "Kiểm tra"}
              </button>
            </div>

            {/* Scanner Result Display */}
            {scannerResult.type && (
              <div
                className={`p-3.5 rounded-xl text-xs flex flex-col gap-2 ${
                  scannerResult.type === "SUCCESS"
                    ? "bg-emerald-50 text-emerald-900 border border-emerald-200"
                    : "bg-rose-50 text-rose-900 border border-rose-200"
                }`}
              >
                <div className="flex items-center gap-2 font-semibold">
                  {scannerResult.type === "SUCCESS" ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-rose-600" />
                  )}
                  <span>
                    {scannerResult.type === "SUCCESS"
                      ? "XÁC THỰC VÉ THÀNH CÔNG"
                      : scannerResult.type === "USED"
                      ? "VÉ ĐÃ QUA SỬ DỤNG"
                      : scannerResult.type === "WRONG_VENDOR"
                      ? "SAI ĐỐI TÁC PHỤC VỤ"
                      : "VÉ KHÔNG HỢP LỆ"}
                  </span>
                </div>
                <p className="text-xs leading-relaxed">{scannerResult.message}</p>

                {/* FIFO Early Dispatch Candidate Banner */}
                {scannerResult.type === "SUCCESS" && scannerResult.earlyDispatchSuggestion?.available && scannerResult.subOrderId && (
                  <div className="mt-2 p-3 bg-white rounded-xl border border-sky-200/90 shadow-2xs flex flex-col gap-2">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-1.5 text-sky-950 font-semibold text-xs">
                        <FastForward className="w-3.5 h-3.5 text-sky-600 shrink-0" />
                        <span>Du khách đến trước giờ khởi hành (FIFO)</span>
                      </div>
                      <span className="px-2 py-0.5 rounded-md text-[10px] font-mono font-medium bg-sky-50 text-sky-700 border border-sky-100">
                        Còn {scannerResult.earlyDispatchSuggestion.availableCapacity} chỗ
                      </span>
                    </div>
                    <p className="text-slate-600 text-xs">
                      Ca sớm cùng ngày:{" "}
                      <span className="font-semibold text-slate-900 font-mono">
                        {scannerResult.earlyDispatchSuggestion.startTime.slice(0, 5)} - {scannerResult.earlyDispatchSuggestion.endTime.slice(0, 5)}
                      </span>{" "}
                      đang trống chỗ. Điều phối du khách chơi ngay ca này?
                    </p>
                    <div className="flex items-center gap-2 pt-1">
                      <button
                        type="button"
                        disabled={isDispatchingEarly}
                        onClick={() =>
                          handleConfirmEarlyDispatch(
                            scannerResult.subOrderId!,
                            scannerResult.earlyDispatchSuggestion!.suggestedSlotId
                          )
                        }
                        className="flex-1 py-2 px-3 rounded-lg bg-sky-600 hover:bg-sky-700 text-white font-medium text-xs flex items-center justify-center gap-1.5 shadow-xs disabled:opacity-50 cursor-pointer"
                      >
                        <FastForward className="w-3.5 h-3.5" />
                        {isDispatchingEarly ? "Đang điều phối..." : `Chuyển ca ${scannerResult.earlyDispatchSuggestion.startTime.slice(0, 5)}`}
                      </button>
                      <button
                        type="button"
                        disabled={isDispatchingEarly}
                        onClick={() => setScannerResult((prev) => ({ ...prev, earlyDispatchSuggestion: null }))}
                        className="py-2 px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs cursor-pointer"
                      >
                        Giữ ca gốc
                      </button>
                    </div>
                  </div>
                )}

                {scannerResult.type === "SUCCESS" && scannerResult.order && (
                  <button
                    type="button"
                    onClick={() => handleConfirmAdmit(scannerResult.order!.id)}
                    className="mt-2 w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-medium text-xs shadow-xs flex items-center justify-center gap-1.5 cursor-pointer"
                  >
                    <Check className="w-4 h-4" />
                    Xác nhận Khách Xuất Bến &amp; Cấp Áo Phao
                  </button>
                )}
              </div>
            )}
          </div>
        </div>,
        document.body
      )}

      {/* Reject Order Modal (Portaled to body) */}
      {rejectOrderId && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4 animate-scale-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200/90 flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-semibold text-slate-900 text-base">Từ chối đơn #{rejectOrderId.slice(0, 8).toUpperCase()}</h3>
              <button
                type="button"
                onClick={() => setRejectOrderId(null)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="font-medium text-slate-700 text-xs">Lý do từ chối vận hành</label>
              <select
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-xs focus:outline-none focus:border-slate-400"
              >
                <option value="Lệnh cấm biển khẩn cấp từ Cảng vụ / Thời tiết xấu">
                  Lệnh cấm biển khẩn cấp từ Cảng vụ / Thời tiết xấu
                </option>
                <option value="Đã đạt giới hạn thiết bị phao an toàn kiểm định">
                  Đã đạt giới hạn thiết bị phao an toàn kiểm định
                </option>
                <option value="Không đủ điều kiện sức khỏe thể lực theo quy chuẩn an toàn">
                  Không đủ điều kiện sức khỏe thể lực theo quy chuẩn an toàn
                </option>
              </select>
            </div>
            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setRejectOrderId(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleRejectOrder}
                disabled={isRejecting}
                className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-medium text-xs disabled:opacity-50 cursor-pointer shadow-xs"
              >
                {isRejecting ? "Đang xử lý..." : "Xác nhận từ chối"}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Refund Details Modal (Portaled to body) */}
      {selectedRefundOrder && selectedRefundOrder.refundInfo && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4 animate-scale-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200/90 flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <RotateCcw className="w-5 h-5 text-slate-700" />
                <h3 className="font-semibold text-slate-900 text-base">Chi tiết Hoàn tiền</h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedRefundOrder(null)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-2.5 text-xs">
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">Mã đơn con:</span>
                <span className="font-medium text-slate-900 font-mono">#{selectedRefundOrder.id.slice(0, 8).toUpperCase()}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">Số tiền hoàn:</span>
                <span className="font-semibold text-slate-900 font-mono">
                  {selectedRefundOrder.refundInfo.amount.toLocaleString("vi-VN")} đ ({selectedRefundOrder.refundInfo.refund_percentage}%)
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100 items-center">
                <span className="text-slate-500">Trạng thái:</span>
                <AtlasBadge label={selectedRefundOrder.refundInfo.status} variant="info" />
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">Thời gian xử lý:</span>
                <span className="text-slate-800 font-mono">{selectedRefundOrder.refundInfo.processed_at}</span>
              </div>
              <div className="flex flex-col py-1">
                <span className="text-slate-500 font-medium">Lý do hoàn tiền:</span>
                <p className="mt-1.5 p-3 rounded-xl bg-slate-50 text-slate-700 text-xs leading-relaxed border border-slate-200/80">
                  {selectedRefundOrder.refundInfo.reason}
                </p>
              </div>
            </div>
            <div className="flex justify-end pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setSelectedRefundOrder(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-medium cursor-pointer"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Reschedule Proposal Modal (Portaled to body) */}
      {isRescheduleOpen && drawerOrder && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4 animate-scale-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200/90 flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <CalendarClock className="w-5 h-5 text-slate-700" />
                <h3 className="font-semibold text-slate-900 text-base">Đề xuất đổi ca (#{drawerOrder.id.slice(0, 8).toUpperCase()})</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsRescheduleOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex flex-col gap-3.5 text-xs">
              <div className="flex flex-col gap-1.5">
                <label className="font-medium text-slate-700">Lý do đề xuất đổi lịch:</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setRescheduleReasonType("WEATHER")}
                    className={`py-2 px-3 rounded-xl border text-xs font-medium cursor-pointer transition-all ${
                      rescheduleReasonType === "WEATHER"
                        ? "bg-slate-900 text-white border-slate-900 shadow-xs"
                        : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"
                    }`}
                  >
                    Thời tiết biển động
                  </button>
                  <button
                    type="button"
                    onClick={() => setRescheduleReasonType("OPERATIONAL")}
                    className={`py-2 px-3 rounded-xl border text-xs font-medium cursor-pointer transition-all ${
                      rescheduleReasonType === "OPERATIONAL"
                        ? "bg-slate-900 text-white border-slate-900 shadow-xs"
                        : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"
                    }`}
                  >
                    Vận hành / Kỹ thuật
                  </button>
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-medium text-slate-700">Chi tiết lý do gửi du khách:</label>
                <input
                  type="text"
                  value={rescheduleReason}
                  onChange={(e) => setRescheduleReason(e.target.value)}
                  className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-xs focus:outline-none focus:border-slate-400"
                  placeholder="Nhập lý do chi tiết..."
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-medium text-slate-700">Chọn ca khởi hành thay thế:</label>
                {availableSlots.length > 0 ? (
                  <select
                    value={rescheduleSlotId}
                    onChange={(e) => setRescheduleSlotId(e.target.value)}
                    className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 font-medium text-xs focus:outline-none focus:border-slate-400"
                  >
                    {availableSlots.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.date} • {s.startTime.slice(0, 5)} - {s.endTime.slice(0, 5)} (Còn {s.capacity - s.bookedCount} chỗ)
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-600 text-xs">
                    Không có ca nào đang mở cho dịch vụ này. Vui lòng mở thêm ca tại mục Lịch khởi hành.
                  </div>
                )}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setIsRescheduleOpen(false)}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-medium cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                disabled={isSubmittingReschedule || !rescheduleSlotId}
                onClick={handleSubmitReschedule}
                className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-medium disabled:opacity-50 shadow-xs cursor-pointer"
              >
                {isSubmittingReschedule ? "Đang gửi..." : "Gửi đề xuất tới du khách"}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
