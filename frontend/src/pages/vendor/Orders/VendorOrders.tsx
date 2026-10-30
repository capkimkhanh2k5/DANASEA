import { useState, useMemo, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { vendorOrderService } from "../../../services/vendorOrderService";
import { vendorService } from "../../../services/vendorService";
import { orderService } from "../../../services/orderService";
import { toast } from "../../../store/useToastStore";
import { extractErrorMessage } from "../../../utils/errorHandler";
import type { VendorServiceDto, VendorServiceSlotDto } from "../../../types/vendorService";
import type { VendorSubOrderDto, VendorBookingItemDto } from "../../../types/vendorOrder";

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
}

const INITIAL_ORDERS: OrderItem[] = [
  {
    id: "SUB-89412-01",
    orderNumber: "DNS-89412",
    serviceTitle: "Chèo SUP ngắm bình minh Mỹ Khê",
    customerName: "Nguyễn Văn An",
    customerPhone: "0912 345 678",
    customerEmail: "an.nguyen@gmail.com",
    timeSlot: "05:30 - 07:30",
    dateStr: "Hôm nay (24/10)",
    guestCount: 2,
    unitPrice: 280000,
    totalAmount: 560000,
    commissionRate: 10,
    commissionAmount: 56000,
    payoutAmount: 504000,
    status: "CONFIRMED",
    checked_in_at: null,
    departurePoint: "Bãi tắm Phạm Văn Đồng (Bến 02)",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89415-03",
    orderNumber: "DNS-89415",
    serviceTitle: "Lặn ngắm san hô Bãi Bụt (Cano khứ hồi)",
    customerName: "Trần Thu Hà",
    customerPhone: "0988 765 432",
    customerEmail: "ha.tran@outlook.com",
    timeSlot: "08:30 - 11:30",
    dateStr: "Hôm nay (24/10)",
    guestCount: 4,
    unitPrice: 520000,
    totalAmount: 2080000,
    commissionRate: 12,
    commissionAmount: 249600,
    payoutAmount: 1830400,
    status: "PENDING",
    checked_in_at: null,
    departurePoint: "Sơn Trà Pier B",
    paymentMethod: "Thẻ Quốc tế (Visa)",
    image: "https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89422-04",
    orderNumber: "DNS-89422",
    serviceTitle: "Mô tô nước Jetski cảm giác mạnh",
    customerName: "Hoàng Tuấn Vũ",
    customerPhone: "0934 999 888",
    customerEmail: "vuh@fpt.edu.vn",
    timeSlot: "14:00 - 14:45",
    dateStr: "Hôm qua (23/10)",
    guestCount: 2,
    unitPrice: 600000,
    totalAmount: 1200000,
    commissionRate: 12,
    commissionAmount: 144000,
    payoutAmount: 1056000,
    status: "REJECTED",
    checked_in_at: null,
    departurePoint: "Bãi tắm Mỹ Khê",
    paymentMethod: "Chuyển khoản",
    image: "https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89425-05",
    orderNumber: "DNS-89425",
    serviceTitle: "Tour SUP Bãi Bụt ngắm hoàng hôn",
    customerName: "Đinh Phương Thảo",
    customerPhone: "0977 123 456",
    customerEmail: "thaodp@gmail.com",
    timeSlot: "16:30 - 18:30",
    dateStr: "22/10/2024",
    guestCount: 2,
    unitPrice: 350000,
    totalAmount: 700000,
    commissionRate: 10,
    commissionAmount: 70000,
    payoutAmount: 630000,
    status: "CANCELLED",
    checked_in_at: null,
    departurePoint: "Bến Bãi Bụt",
    paymentMethod: "Ví MoMo",
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89428-06",
    orderNumber: "DNS-89428",
    serviceTitle: "Lặn biển bình khí chuyên sâu",
    customerName: "Vũ Hải Nam",
    customerPhone: "0905 444 333",
    customerEmail: "namvu@viettel.com.vn",
    timeSlot: "09:00 - 12:00",
    dateStr: "21/10/2024",
    guestCount: 1,
    unitPrice: 950000,
    totalAmount: 950000,
    commissionRate: 12,
    commissionAmount: 114000,
    payoutAmount: 836000,
    status: "REFUNDED",
    checked_in_at: null,
    departurePoint: "Trạm Hòn Chảo",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
    refundInfo: {
      subOrderId: "SUB-89428-06",
      amount: 950000,
      refund_percentage: 100,
      reason: "Cấm biển khẩn cấp do sóng lớn gió cấp 6 theo thông báo Cảng vụ",
      processed_at: "21/10/2024 15:30",
      status: "PROCESSED"
    }
  },
  {
    id: "SUB-89430-07",
    orderNumber: "DNS-89430",
    serviceTitle: "Chèo SUP đón bình minh Mỹ Khê",
    customerName: "Lê Minh Trí",
    customerPhone: "0913 222 111",
    customerEmail: "minhtri@danang.gov.vn",
    timeSlot: "05:30 - 07:30",
    dateStr: "20/10/2024",
    guestCount: 2,
    unitPrice: 280000,
    totalAmount: 560000,
    commissionRate: 10,
    commissionAmount: 56000,
    payoutAmount: 504000,
    status: "COMPLETED",
    checked_in_at: "20/10/2024 lúc 05:25",
    departurePoint: "Bãi tắm Phạm Văn Đồng (Bến 02)",
    paymentMethod: "Ví ShopeePay",
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89433-08",
    orderNumber: "DNS-89433",
    serviceTitle: "Tour Thuyền Kayak trong suốt vịnh Sơn Trà",
    customerName: "Phạm Quỳnh Chi",
    customerPhone: "0945 678 123",
    customerEmail: "quynhchi.p@gmail.com",
    timeSlot: "15:00 - 17:00",
    dateStr: "Hôm nay (24/10)",
    guestCount: 3,
    unitPrice: 320000,
    totalAmount: 960000,
    commissionRate: 10,
    commissionAmount: 96000,
    payoutAmount: 864000,
    status: "CONFIRMED",
    checked_in_at: null,
    departurePoint: "Bến Bãi Bụt",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89436-09",
    orderNumber: "DNS-89436",
    serviceTitle: "Lặn bình khí Scuba khám phá Rạn Nam Ô",
    customerName: "Ngô Quốc Huy",
    customerPhone: "0909 333 777",
    customerEmail: "huy.ngo@techcombank.com.vn",
    timeSlot: "08:00 - 11:00",
    dateStr: "Hôm nay (24/10)",
    guestCount: 2,
    unitPrice: 900000,
    totalAmount: 1800000,
    commissionRate: 12,
    commissionAmount: 216000,
    payoutAmount: 1584000,
    status: "PENDING",
    checked_in_at: null,
    departurePoint: "Bến thủy Nam Ô",
    paymentMethod: "Thẻ ATM Nội địa",
    image: "https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89440-10",
    orderNumber: "DNS-89440",
    serviceTitle: "Chèo SUP rèn luyện kỹ năng nâng cao",
    customerName: "Bùi Thị Mai",
    customerPhone: "0971 888 222",
    customerEmail: "maibui@gmail.com",
    timeSlot: "05:30 - 07:30",
    dateStr: "24/10/2024",
    guestCount: 1,
    unitPrice: 350000,
    totalAmount: 350000,
    commissionRate: 10,
    commissionAmount: 35000,
    payoutAmount: 315000,
    status: "COMPLETED",
    checked_in_at: "24/10/2024 lúc 05:20",
    departurePoint: "Bãi tắm Phạm Văn Đồng (Bến 02)",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89442-11",
    orderNumber: "DNS-89442",
    serviceTitle: "Cano lướt sóng cao tốc Bãi Rạng",
    customerName: "Lâm Thế Vinh",
    customerPhone: "0938 112 334",
    customerEmail: "vinhthe@yahoo.com",
    timeSlot: "10:00 - 11:00",
    dateStr: "23/10/2024",
    guestCount: 5,
    unitPrice: 450000,
    totalAmount: 2250000,
    commissionRate: 12,
    commissionAmount: 270000,
    payoutAmount: 1980000,
    status: "COMPLETED",
    checked_in_at: "23/10/2024 lúc 09:50",
    departurePoint: "Bến thủy du lịch Tiên Sa",
    paymentMethod: "Visa / Mastercard",
    image: "https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89445-12",
    orderNumber: "DNS-89445",
    serviceTitle: "Phao chuối cảm giác mạnh (Nhóm 5 người)",
    customerName: "Đỗ Gia Hân",
    customerPhone: "0982 556 677",
    customerEmail: "giahan.do@gmail.com",
    timeSlot: "15:30 - 16:30",
    dateStr: "23/10/2024",
    guestCount: 5,
    unitPrice: 200000,
    totalAmount: 1000000,
    commissionRate: 10,
    commissionAmount: 100000,
    payoutAmount: 900000,
    status: "CONFIRMED",
    checked_in_at: null,
    departurePoint: "Bãi tắm Mỹ Khê",
    paymentMethod: "Ví MoMo",
    image: "https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89448-13",
    orderNumber: "DNS-89448",
    serviceTitle: "Tour SUP & Yoga trên biển sớm mai",
    customerName: "Dương Minh Châu",
    customerPhone: "0915 789 456",
    customerEmail: "chauduong@yoga.vn",
    timeSlot: "05:00 - 07:00",
    dateStr: "22/10/2024",
    guestCount: 3,
    unitPrice: 380000,
    totalAmount: 1140000,
    commissionRate: 10,
    commissionAmount: 114000,
    payoutAmount: 1026000,
    status: "CONFIRMED",
    checked_in_at: null,
    departurePoint: "Bãi biển Non Nước",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89450-14",
    orderNumber: "DNS-89450",
    serviceTitle: "Lặn biển chụp ảnh dưới nước nghệ thuật",
    customerName: "Trịnh Thùy Linh",
    customerPhone: "0968 444 888",
    customerEmail: "linhtrinh@artphoto.com",
    timeSlot: "13:30 - 16:00",
    dateStr: "22/10/2024",
    guestCount: 2,
    unitPrice: 850000,
    totalAmount: 1700000,
    commissionRate: 12,
    commissionAmount: 204000,
    payoutAmount: 1496000,
    status: "PENDING",
    checked_in_at: null,
    departurePoint: "Bến Bãi Bụt",
    paymentMethod: "Chuyển khoản ngân hàng",
    image: "https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89455-15",
    orderNumber: "DNS-89455",
    serviceTitle: "Dù lượn kéo bằng cano ngắm toàn cảnh vịnh",
    customerName: "Lý Văn Kiệt",
    customerPhone: "0903 667 889",
    customerEmail: "kietly@vietinbank.vn",
    timeSlot: "14:30 - 15:30",
    dateStr: "21/10/2024",
    guestCount: 2,
    unitPrice: 650000,
    totalAmount: 1300000,
    commissionRate: 12,
    commissionAmount: 156000,
    payoutAmount: 1144000,
    status: "CANCELLED",
    checked_in_at: null,
    departurePoint: "Bãi tắm Mỹ Khê",
    paymentMethod: "Ví MoMo",
    image: "https://images.unsplash.com/photo-1559827291-72ee739d0d9a?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89458-16",
    orderNumber: "DNS-89458",
    serviceTitle: "Chèo SUP ngắm bình minh Mỹ Khê",
    customerName: "Nguyễn Hoài Thương",
    customerPhone: "0935 222 999",
    customerEmail: "thuong.nh@gmail.com",
    timeSlot: "05:30 - 07:30",
    dateStr: "20/10/2024",
    guestCount: 2,
    unitPrice: 280000,
    totalAmount: 560000,
    commissionRate: 10,
    commissionAmount: 56000,
    payoutAmount: 504000,
    status: "COMPLETED",
    checked_in_at: "20/10/2024 lúc 05:22",
    departurePoint: "Bãi tắm Phạm Văn Đồng (Bến 02)",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89460-17",
    orderNumber: "DNS-89460",
    serviceTitle: "Tour lặn biển bán đảo Sơn Trà & Tiệc BBQ bãi cát",
    customerName: "Tạ Minh Hoàng",
    customerPhone: "0979 555 111",
    customerEmail: "hoangtm@sunworld.vn",
    timeSlot: "08:30 - 14:00",
    dateStr: "19/10/2024",
    guestCount: 6,
    unitPrice: 750000,
    totalAmount: 4500000,
    commissionRate: 12,
    commissionAmount: 540000,
    payoutAmount: 3960000,
    status: "COMPLETED",
    checked_in_at: "19/10/2024 lúc 08:15",
    departurePoint: "Sơn Trà Pier B",
    paymentMethod: "Chuyển khoản doanh nghiệp",
    image: "https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=600&q=80",
  },
  {
    id: "SUB-89465-18",
    orderNumber: "DNS-89465",
    serviceTitle: "Lặn ngắm san hô Bãi Bụt (Cano khứ hồi)",
    customerName: "Cao Bá Quát",
    customerPhone: "0918 333 444",
    customerEmail: "quatcb@edu.vn",
    timeSlot: "08:30 - 11:30",
    dateStr: "18/10/2024",
    guestCount: 3,
    unitPrice: 520000,
    totalAmount: 1560000,
    commissionRate: 12,
    commissionAmount: 187200,
    payoutAmount: 1372800,
    status: "REFUNDED",
    checked_in_at: null,
    departurePoint: "Sơn Trà Pier B",
    paymentMethod: "VNPay QR",
    image: "https://images.unsplash.com/photo-1518837695005-2083093ee35b?auto=format&fit=crop&w=600&q=80",
    refundInfo: {
      subOrderId: "SUB-89465-18",
      amount: 1560000,
      refund_percentage: 100,
      reason: "Hỏng chân vịt cano vận chuyển đột xuất, đối tác chủ động báo huỷ chuyến",
      processed_at: "18/10/2024 10:15",
      status: "PROCESSED"
    }
  }
];

export function VendorOrders() {
  const [activeFilter, setActiveFilter] = useState<"ALL" | SubOrderStatus>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedShift, setSelectedShift] = useState<"ALL" | "MORNING" | "AFTERNOON">("ALL");
  const [showFinancials, setShowFinancials] = useState(false);
  const [orders, setOrders] = useState<OrderItem[]>(INITIAL_ORDERS);
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
  const [scannerResult, setScannerResult] = useState<{
    type: "SUCCESS" | "USED" | "INVALID" | "WRONG_VENDOR" | null;
    order?: OrderItem;
    message?: string;
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

      // 1. Process booking items
      bookingItems.forEach((b) => {
        const srv = serviceMap.get(b.serviceId);
        const matchedSub = subOrders.find(
          (so) => so.serviceId === b.serviceId && (so.slotId === b.slotId || so.quantity === b.quantity)
        );

        const subId = matchedSub?.id || b.itemId;
        const total = b.subtotal || b.price * b.quantity;
        const commission = total * 0.1;
        const payout = total - commission;

        let status: SubOrderStatus = "CONFIRMED";
        if (b.bookingStatus === "CHECKED_IN" || matchedSub?.status === "CHECKED_IN") {
          status = "COMPLETED";
        } else if (b.bookingStatus === "CANCELLED" || matchedSub?.status === "CANCELLED") {
          status = "CANCELLED";
        } else if (b.bookingStatus === "REJECTED" || matchedSub?.status === "REJECTED") {
          status = "REJECTED";
        } else if (matchedSub?.status) {
          status = matchedSub.status;
        }

        mergedList.push({
          id: subId,
          orderNumber: `DNS-${(b.itemId || b.bookingId || subId).replace(/-/g, '').slice(-5).toUpperCase()}`,
          bookingItemId: b.itemId,
          serviceId: b.serviceId,
          slotId: b.slotId || undefined,
          serviceTitle: srv?.name || "Dịch vụ thể thao biển",
          customerName: "Nguyễn Văn Khách",
          customerPhone: "0905 123 456",
          customerEmail: "khachhang@danasea.vn",
          timeSlot: b.bookingTime ? b.bookingTime.slice(0, 5) : "08:00 - 10:00",
          dateStr: b.bookingDate || "Hôm nay",
          guestCount: b.quantity || 1,
          unitPrice: b.price || total / (b.quantity || 1),
          totalAmount: total,
          commissionRate: 10,
          commissionAmount: commission,
          payoutAmount: payout,
          status,
          checked_in_at: matchedSub?.checkedInAt || (status === "COMPLETED" ? "Đã check-in" : null),
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
          mergedList.push({
            id: so.id,
            orderNumber: `DNS-${(so.id || '').replace(/-/g, '').slice(-5).toUpperCase()}`,
            serviceId: so.serviceId,
            slotId: so.slotId || undefined,
            serviceTitle: srv?.name || "Trải nghiệm thể thao biển",
            customerName: "Nguyễn Văn Khách",
            customerPhone: "0905 123 456",
            customerEmail: "khachhang@danasea.vn",
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

      if (mergedList.length > 0) {
        setOrders(mergedList);
      } else {
        setOrders(INITIAL_ORDERS);
      }
    } catch (err) {
      console.error("[VendorOrders] Load error:", err);
      setOrders(INITIAL_ORDERS);
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
            ? { ...o, status: "COMPLETED", checked_in_at: checkinTime }
            : o
        )
      );

      const matched = orders.find((o) => o.id === verifyRes.subOrderId || o.id === rawInput);
      setScannerResult({
        type: "SUCCESS",
        order: matched,
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

  // Total Net Revenue calculation for header
  const totalNetRevenue = useMemo(() => {
    return orders
      .filter((o) => o.status === "COMPLETED" || o.status === "CONFIRMED")
      .reduce((sum, o) => sum + (o.payoutAmount || o.totalAmount || 0), 0);
  }, [orders]);

  return (
    <div className="w-full min-h-screen bg-background flex flex-col">
      {/* Top Banner & Action Header */}
      <div className="w-full bg-surface border-b border-slate-200 px-6 py-4">
        <div className="w-full flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl md:text-2xl font-bold text-slate-900 tracking-tight">
                Đơn hàng
              </h1>
              <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200 font-semibold text-xs">
                {orders.length}
              </span>
              <span className="text-xs text-slate-500 font-medium ml-2">
                Tạm tính thực nhận: <strong className="font-mono text-emerald-600 font-bold">{totalNetRevenue.toLocaleString("vi-VN")} đ</strong>
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowFinancials(!showFinancials)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium border border-slate-200 transition-all cursor-pointer shadow-xs"
            >
              <span className="material-symbols-outlined text-[18px] text-slate-500">payments</span>
              <span>{showFinancials ? "Ẩn chiết khấu sàn" : "Chiết khấu sàn"}</span>
            </button>
            <button
              onClick={() => {
                setScannerResult({ type: null });
                setManualCode("");
                setIsTerminalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-bold hover:bg-primary-container transition-all cursor-pointer shadow-xs"
            >
              <span className="material-symbols-outlined text-[18px]">qr_code_scanner</span>
              <span>Quét vé QR</span>
            </button>
          </div>
        </div>

        {/* Filter Tabs by Status */}
        <div className="w-full flex items-center gap-2 overflow-x-auto pb-1 mt-3 text-nowrap scrollbar-none">
          <button
            onClick={() => { setActiveFilter("ALL"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "ALL"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span>Tất cả</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "ALL" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {orders.length}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("PENDING"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "PENDING"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-amber-500"></span>
            <span>Cần duyệt</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "PENDING" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countPending}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("CONFIRMED"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "CONFIRMED"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-blue-500"></span>
            <span>Đã xác nhận</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "CONFIRMED" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countConfirmed}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("COMPLETED"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "COMPLETED"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span>Đã đi tour</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "COMPLETED" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countCompleted}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("REJECTED"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "REJECTED"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-red-500"></span>
            <span>Từ chối</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "REJECTED" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countRejected}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("CANCELLED"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "CANCELLED"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-slate-500"></span>
            <span>Đã hủy</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "CANCELLED" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countCancelled}
            </span>
          </button>

          <button
            onClick={() => { setActiveFilter("REFUNDED"); setCurrentPage(1); }}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              activeFilter === "REFUNDED"
                ? "bg-primary text-white font-bold shadow-xs"
                : "bg-white text-black hover:bg-slate-50 border border-slate-200"
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-purple-500"></span>
            <span>Đã hoàn tiền</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold ${activeFilter === "REFUNDED" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700"}`}>
              {countRefunded}
            </span>
          </button>
        </div>
      </div>

      {/* Main Container */}
      <div className="flex-1 w-full px-6 py-3.5 flex flex-col space-y-3.5">
        {/* Search & Filter Toolbar */}
        <div className="bg-white p-3 rounded-xl border border-slate-200 shadow-xs flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
          <div className="flex-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
            {/* Search Input */}
            <div className="relative flex-1 min-w-[240px]">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-black text-[18px]">
                search
              </span>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                placeholder="Tìm mã đơn, tên khách hàng, số điện thoại, tour..."
                className="w-full pl-9 pr-8 py-2 rounded-lg bg-slate-50 border border-slate-200 text-xs text-black placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-primary transition-all"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-black hover:text-slate-600 cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>
              )}
            </div>

            {/* Shift Filter Dropdown */}
            <div className="flex items-center gap-1.5">
              <span className="text-xs text-black font-semibold whitespace-nowrap hidden lg:inline">
                Khung giờ:
              </span>
              <select
                value={selectedShift}
                onChange={(e) => { setSelectedShift(e.target.value as typeof selectedShift); setCurrentPage(1); }}
                className="px-3 py-2 rounded-lg bg-slate-50 border border-slate-200 text-xs text-black font-semibold cursor-pointer focus:outline-none"
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
                toast.success("Đã đồng bộ lại dữ liệu đơn hàng từ máy chủ!");
              }}
              className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 border border-slate-200 text-black transition-colors cursor-pointer"
              title="Làm mới sổ cái"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
            </button>
          </div>
        </div>

        {/* Master Order Table Card */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden flex flex-col flex-1">
          {/* Responsive Table Body */}
          <div className="overflow-x-auto flex-1 scrollbar-thin">
            <table className="w-full text-left text-xs border-collapse min-w-[960px]">
              <thead>
                <tr className="bg-slate-50 text-slate-900 text-xs font-bold border-b border-slate-200">
                  <th className="py-3 px-4 font-bold border-r border-slate-200 whitespace-nowrap">Mã Đơn &amp; Giờ</th>
                  <th className="py-3 px-4 font-bold border-r border-slate-200 min-w-[190px]">Khách Hàng</th>
                  <th className="py-3 px-4 font-bold border-r border-slate-200 min-w-[240px]">Dịch Vụ &amp; Trải Nghiệm</th>
                  <th className="py-3 px-4 font-bold border-r border-slate-200 whitespace-nowrap">Khởi Hành &amp; Bến</th>
                  <th className="py-3 px-4 font-bold border-r border-slate-200 text-right whitespace-nowrap">Tổng Tiền</th>
                  {showFinancials && (
                    <th className="py-3 px-4 font-bold border-r border-slate-200 text-right whitespace-nowrap">Thực Nhận</th>
                  )}
                  <th className="py-3 px-4 font-bold border-r border-slate-200 text-center whitespace-nowrap">Trạng Thái</th>
                  <th className="py-3 px-4 font-bold text-center whitespace-nowrap">Thao Tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-sm text-slate-800">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, idx) => (
                    <tr key={`skeleton-${idx}`} className="animate-pulse">
                      <td className="py-4 px-4 border-r border-slate-200">
                        <div className="h-4 w-20 bg-slate-200 rounded mb-1.5"></div>
                        <div className="h-3 w-16 bg-slate-100 rounded"></div>
                      </td>
                      <td className="py-4 px-4 border-r border-slate-200">
                        <div className="h-4 w-28 bg-slate-200 rounded mb-1.5"></div>
                        <div className="h-3 w-24 bg-slate-100 rounded"></div>
                      </td>
                      <td className="py-4 px-4 border-r border-slate-200">
                        <div className="flex items-center gap-3">
                          <div className="w-11 h-11 bg-slate-200 rounded-lg shrink-0"></div>
                          <div className="space-y-1.5">
                            <div className="h-4 w-36 bg-slate-200 rounded"></div>
                            <div className="h-3 w-20 bg-slate-100 rounded"></div>
                          </div>
                        </div>
                      </td>
                      <td className="py-4 px-4 border-r border-slate-200">
                        <div className="h-4 w-20 bg-slate-200 rounded mb-1.5"></div>
                        <div className="h-3 w-32 bg-slate-100 rounded"></div>
                      </td>
                      <td className="py-4 px-4 text-right border-r border-slate-200">
                        <div className="h-4 w-24 bg-slate-200 rounded ml-auto mb-1.5"></div>
                        <div className="h-3 w-16 bg-slate-100 rounded ml-auto"></div>
                      </td>
                      {showFinancials && (
                        <td className="py-4 px-4 text-right border-r border-slate-200">
                          <div className="h-4 w-20 bg-slate-200 rounded ml-auto"></div>
                        </td>
                      )}
                      <td className="py-4 px-4 text-center border-r border-slate-200">
                        <div className="h-6 w-20 bg-slate-200 rounded-full mx-auto"></div>
                      </td>
                      <td className="py-4 px-4 text-center">
                        <div className="h-7 w-16 bg-slate-200 rounded-lg mx-auto"></div>
                      </td>
                    </tr>
                  ))
                ) : paginatedOrders.length > 0 ? (
                  paginatedOrders.map((order) => {
                    const isDrawerOpenForThis = drawerOrderId === order.id;
                    return (
                      <tr
                        key={order.id}
                        onClick={() => setDrawerOrderId(order.id)}
                        className={`cursor-pointer transition-colors ${
                          isDrawerOpenForThis
                            ? "bg-slate-100 font-medium text-slate-900"
                            : "hover:bg-slate-50 text-slate-800"
                        }`}
                      >
                        {/* ID & Time */}
                        <td className="py-3.5 px-4 whitespace-nowrap border-r border-slate-200">
                          <span className="font-mono text-xs font-bold text-slate-900 block">
                            #{order.id}
                          </span>
                          <span className="text-[11px] text-slate-500 flex items-center gap-1 mt-0.5">
                            <span className="material-symbols-outlined text-[13px] text-slate-400">schedule</span>
                            {order.timeSlot}
                          </span>
                        </td>

                        {/* Customer Info */}
                        <td className="py-3.5 px-4 border-r border-slate-200">
                          <div className="font-bold text-slate-900 text-sm">
                            {order.customerName}
                          </div>
                          <div className="text-[11px] text-slate-600 font-mono mt-0.5 flex items-center gap-1">
                            <span className="material-symbols-outlined text-[13px] text-slate-400">call</span>
                            {order.customerPhone}
                          </div>
                        </td>

                        {/* Service / Tour */}
                        <td className="py-3.5 px-4 border-r border-slate-200">
                          <div className="flex items-center gap-3">
                            <img
                              src={order.image}
                              alt={order.serviceTitle}
                              className="w-11 h-11 rounded-lg object-cover shrink-0 border border-slate-200"
                            />
                            <div className="min-w-0">
                              <span className="font-bold text-slate-900 block text-xs line-clamp-1" title={order.serviceTitle}>
                                {order.serviceTitle}
                              </span>
                              <span className="text-[11px] text-slate-500 block mt-0.5">
                                {order.guestCount} người • {order.paymentMethod || "Trực tuyến"}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* Departure Date & Point */}
                        <td className="py-3.5 px-4 whitespace-nowrap border-r border-slate-200">
                          <span className="font-bold text-slate-900 block text-xs">
                            {order.dateStr}
                          </span>
                          <span className="text-[11px] text-slate-500 block truncate max-w-[200px]" title={order.departurePoint}>
                            {order.departurePoint}
                          </span>
                        </td>

                        {/* Gross Amount */}
                        <td className="py-3.5 px-4 text-right whitespace-nowrap border-r border-slate-200">
                          <span className="font-mono font-bold text-sm text-slate-900 block">
                            {order.totalAmount.toLocaleString("vi-VN")} đ
                          </span>
                          <span className="text-[10px] text-slate-500 block">
                            {order.guestCount} × {order.unitPrice.toLocaleString("vi-VN")} đ
                          </span>
                        </td>

                        {/* Net Amount if toggled */}
                        {showFinancials && (
                          <td className="py-3.5 px-4 text-right whitespace-nowrap font-mono font-bold text-sm text-primary border-r border-slate-200">
                            {order.payoutAmount.toLocaleString("vi-VN")} đ
                            <span className="text-[10px] text-slate-500 block">
                              Sàn -{order.commissionRate}%
                            </span>
                          </td>
                        )}

                        {/* Status Badge */}
                        <td className="py-3.5 px-4 text-center whitespace-nowrap border-r border-slate-200">
                          <span
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold ${
                              order.status === "PENDING"
                                ? "bg-amber-50 text-amber-700 border border-amber-200"
                                : order.status === "CONFIRMED"
                                ? "bg-blue-50 text-blue-700 border border-blue-200"
                                : order.status === "COMPLETED"
                                ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                                : order.status === "REJECTED"
                                ? "bg-red-50 text-red-700 border border-red-200"
                                : order.status === "CANCELLED"
                                ? "bg-slate-100 text-slate-600 border border-slate-200"
                                : "bg-purple-50 text-purple-700 border border-purple-200"
                            }`}
                          >
                            <span
                              className={`w-1.5 h-1.5 rounded-full ${
                                order.status === "PENDING"
                                  ? "bg-amber-600"
                                  : order.status === "CONFIRMED"
                                  ? "bg-blue-600"
                                  : order.status === "COMPLETED"
                                  ? "bg-emerald-600"
                                  : order.status === "REJECTED"
                                  ? "bg-red-600"
                                  : order.status === "CANCELLED"
                                  ? "bg-slate-500"
                                  : "bg-purple-600"
                              }`}
                            ></span>
                            {order.status}
                          </span>
                          {order.checked_in_at && (
                            <div className="text-[10px] text-emerald-700 font-bold mt-1 flex items-center justify-center gap-0.5">
                              <span className="material-symbols-outlined text-[13px]">verified</span>
                              <span>Check-in</span>
                            </div>
                          )}
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 px-4 text-center whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                          <div className="inline-flex items-center justify-center gap-1.5">
                            {order.status === "PENDING" && (
                              <button
                                onClick={() => handleConfirmOrder(order.id)}
                                className="px-2.5 py-1 rounded bg-primary text-white text-xs font-semibold hover:bg-primary-container transition-colors cursor-pointer shadow-xs"
                                title="Xác nhận đơn"
                              >
                                Duyệt
                              </button>
                            )}

                            {order.status === "CONFIRMED" && (
                              <button
                                onClick={() => {
                                  setManualCode(order.id);
                                  setIsTerminalOpen(true);
                                  handleVerifyCode(order.id);
                                }}
                                className="px-2.5 py-1 rounded bg-emerald-600 text-white text-xs font-semibold hover:bg-emerald-700 transition-colors flex items-center gap-1 cursor-pointer shadow-xs"
                                title="Soát vé & Check-in"
                              >
                                <span className="material-symbols-outlined text-[14px]">qr_code_scanner</span>
                                Check-in
                              </button>
                            )}

                            <button
                              onClick={() => setDrawerOrderId(order.id)}
                              className="px-2.5 py-1 rounded bg-white border border-slate-200 text-slate-700 text-xs font-medium hover:bg-slate-100 transition-colors cursor-pointer shadow-xs"
                            >
                              Chi tiết
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={showFinancials ? 8 : 7} className="py-16 text-center text-black">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <span className="material-symbols-outlined text-[48px] text-slate-400">
                          inbox
                        </span>
                        <span className="text-sm font-bold text-black">Không tìm thấy đơn hàng nào</span>
                        <p className="text-xs text-black">
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
          <div className="px-4 py-2 border-t border-slate-200 bg-slate-50 flex items-center justify-between text-xs select-none">
            {/* Page Size Selector */}
            <div className="flex items-center gap-2">
              <span className="text-black font-semibold">Số dòng:</span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setCurrentPage(1);
                }}
                className="px-2 py-1 rounded bg-white border border-slate-200 text-xs font-semibold text-black cursor-pointer focus:outline-none"
              >
                <option value={8}>8</option>
                <option value={12}>12</option>
                <option value={20}>20</option>
              </select>
            </div>

            {/* Pagination Controls */}
            <div className="flex items-center gap-1">
              <button
                disabled={validCurrentPage <= 1}
                onClick={() => setCurrentPage(1)}
                className="p-1.5 rounded text-black hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title="Về trang đầu"
              >
                <span className="material-symbols-outlined text-[18px]">first_page</span>
              </button>

              <button
                disabled={validCurrentPage <= 1}
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                className="px-2 py-1 rounded text-black hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed font-medium flex items-center gap-1 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_left</span>
                <span>Trước</span>
              </button>

              {/* Page Numbers */}
              <div className="flex items-center gap-1 px-1">
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((pageNum) => (
                  <button
                    key={pageNum}
                    onClick={() => setCurrentPage(pageNum)}
                    className={`w-7 h-7 rounded font-bold text-xs transition-colors cursor-pointer ${
                      pageNum === validCurrentPage
                        ? "bg-primary text-white"
                        : "text-black hover:bg-slate-100 border border-slate-200"
                    }`}
                  >
                    {pageNum}
                  </button>
                ))}
              </div>

              <button
                disabled={validCurrentPage >= totalPages}
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                className="px-2 py-1 rounded text-black hover:bg-slate-200 disabled:opacity-40 disabled:cursor-not-allowed font-medium flex items-center gap-1 cursor-pointer"
              >
                <span>Sau</span>
                <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              </button>

              <button
                disabled={validCurrentPage >= totalPages}
                onClick={() => setCurrentPage(totalPages)}
                className="p-1.5 rounded text-black hover:bg-slate-200 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                title="Đến trang cuối"
              >
                <span className="material-symbols-outlined text-[18px]">last_page</span>
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
            className="fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity"
            onClick={() => setDrawerOrderId(null)}
          ></div>

          {/* Drawer Panel content */}
          <div className="relative w-full max-w-lg bg-surface-container-lowest shadow-2xl h-full flex flex-col z-10 border-l border-outline-variant/30 overflow-y-auto">
            {/* Drawer Header */}
            <div className="p-5 border-b border-outline-variant/20 bg-surface-container-low/30 flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-sm font-bold text-primary">#{drawerOrder.id}</span>
                  <span
                    className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold ${
                      drawerOrder.status === "PENDING"
                        ? "bg-amber-100 text-amber-900"
                        : drawerOrder.status === "CONFIRMED"
                        ? "bg-blue-100 text-blue-900"
                        : drawerOrder.status === "COMPLETED"
                        ? "bg-emerald-100 text-emerald-900"
                        : drawerOrder.status === "REJECTED"
                        ? "bg-red-100 text-red-900"
                        : drawerOrder.status === "CANCELLED"
                        ? "bg-slate-200 text-slate-800"
                        : "bg-purple-100 text-purple-900"
                    }`}
                  >
                    {drawerOrder.status}
                  </span>
                </div>
                <div className="text-xs text-outline mt-1">
                  Mã đơn đặt tổng: <strong className="text-on-surface">{drawerOrder.orderNumber}</strong>
                </div>
              </div>

              <button
                onClick={() => setDrawerOrderId(null)}
                className="p-1.5 rounded-xl text-on-surface-variant hover:bg-surface-container transition-colors cursor-pointer"
                title="Đóng chi tiết"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Drawer Body */}
            <div className="p-6 flex flex-col gap-5 flex-1">
              {/* Tour Hero Card */}
              <div className="flex gap-4 p-3.5 rounded-2xl bg-surface-container-low/50 border border-outline-variant/20">
                <img
                  src={drawerOrder.image}
                  alt={drawerOrder.serviceTitle}
                  className="w-20 h-20 rounded-xl object-cover shrink-0 border border-outline-variant/20 shadow-xs"
                />
                <div className="flex flex-col justify-between min-w-0">
                  <h3 className="font-bold text-sm text-on-surface leading-snug line-clamp-2">
                    {drawerOrder.serviceTitle}
                  </h3>
                  <div className="text-xs text-on-surface-variant flex items-center gap-1.5 mt-1">
                    <span className="material-symbols-outlined text-[16px] text-primary">schedule</span>
                    <span>{drawerOrder.timeSlot} • {drawerOrder.dateStr}</span>
                  </div>
                  <div className="text-xs text-outline flex items-center gap-1 mt-0.5">
                    <span className="material-symbols-outlined text-[16px] text-secondary">pin_drop</span>
                    <span className="truncate">{drawerOrder.departurePoint}</span>
                  </div>
                </div>
              </div>

              {/* Customer Info Card */}
              <div className="p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 shadow-2xs space-y-2 text-xs">
                <span className="font-bold text-on-surface uppercase tracking-wider text-[11px] block text-outline">
                  Thông tin khách hàng
                </span>
                <div className="flex justify-between py-1 border-b border-outline-variant/15">
                  <span className="text-outline">Họ tên du khách:</span>
                  <strong className="text-on-surface text-sm">{drawerOrder.customerName}</strong>
                </div>
                <div className="flex justify-between py-1 border-b border-outline-variant/15">
                  <span className="text-outline">Số điện thoại liên hệ:</span>
                  <span className="font-mono font-bold text-primary text-sm">{drawerOrder.customerPhone}</span>
                </div>
                {drawerOrder.customerEmail && (
                  <div className="flex justify-between py-1 border-b border-outline-variant/15">
                    <span className="text-outline">Email:</span>
                    <span className="text-on-surface font-medium">{drawerOrder.customerEmail}</span>
                  </div>
                )}
                <div className="flex justify-between py-1">
                  <span className="text-outline">Phương thức thanh toán:</span>
                  <span className="font-semibold text-on-surface">{drawerOrder.paymentMethod || "VNPay"}</span>
                </div>
              </div>

              {/* Financial Calculation Breakdown */}
              <div className="p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 shadow-2xs space-y-2.5 text-xs">
                <span className="font-bold text-on-surface uppercase tracking-wider text-[11px] block text-outline">
                  Quyết toán tài chính đơn hàng
                </span>
                <div className="flex justify-between text-on-surface-variant">
                  <span>Số lượng vé xuất bến:</span>
                  <strong className="text-on-surface">{drawerOrder.guestCount} vé × {drawerOrder.unitPrice.toLocaleString("vi-VN")} đ</strong>
                </div>
                <div className="flex justify-between text-on-surface-variant">
                  <span>Tổng tiền thanh toán (Gross):</span>
                  <strong className="font-mono text-sm text-on-surface">{drawerOrder.totalAmount.toLocaleString("vi-VN")} đ</strong>
                </div>
                <div className="flex justify-between text-on-surface-variant">
                  <span>Khấu trừ hoa hồng sàn ({drawerOrder.commissionRate}%):</span>
                  <span className="text-secondary font-semibold font-mono">-{drawerOrder.commissionAmount.toLocaleString("vi-VN")} đ</span>
                </div>
                <div className="pt-2 border-t border-outline-variant/20 flex justify-between items-baseline">
                  <span className="font-bold text-on-surface text-sm">Thực nhận về đối tác (Net):</span>
                  <strong className="font-mono text-lg font-bold text-primary">{drawerOrder.payoutAmount.toLocaleString("vi-VN")} đ</strong>
                </div>
              </div>

              {/* Check-in Admission Record */}
              {drawerOrder.checked_in_at ? (
                <div className="p-4 rounded-2xl bg-emerald-50 text-emerald-900 text-xs font-semibold flex items-center gap-2.5 border border-emerald-200">
                  <span className="material-symbols-outlined text-[24px] text-emerald-700">verified</span>
                  <div>
                    <div className="font-bold text-sm">Khách đã hoàn tất Check-in</div>
                    <div className="text-[11px] text-emerald-800 mt-0.5">Thời điểm: {drawerOrder.checked_in_at}</div>
                  </div>
                </div>
              ) : (
                <div className="p-3.5 rounded-2xl bg-surface-container-low text-xs text-on-surface-variant flex items-center gap-2 border border-outline-variant/20">
                  <span className="material-symbols-outlined text-outline text-[20px]">how_to_reg</span>
                  <span>Chưa check-in. Vui lòng quét mã QR khi khách đến bến.</span>
                </div>
              )}
            </div>

            {/* Drawer Actions Footer */}
            <div className="p-5 border-t border-outline-variant/20 bg-surface-container-lowest flex flex-col gap-2">
              {drawerOrder.status === "PENDING" && (
                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    onClick={() => { handleConfirmOrder(drawerOrder.id); setDrawerOrderId(null); }}
                    className="py-3 rounded-xl bg-primary text-on-primary font-bold text-xs hover:bg-primary-container transition-colors shadow-sm cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <span className="material-symbols-outlined text-[18px]">check_circle</span>
                    Xác nhận đơn
                  </button>
                  <button
                    onClick={() => { setRejectOrderId(drawerOrder.id); setDrawerOrderId(null); }}
                    className="py-3 rounded-xl bg-surface-container-high text-error font-bold text-xs hover:bg-error/10 transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                  >
                    <span className="material-symbols-outlined text-[18px]">cancel</span>
                    Từ chối đơn
                  </button>
                </div>
              )}

              {drawerOrder.status === "CONFIRMED" && (
                <div className="flex flex-col gap-2">
                  <button
                    onClick={() => {
                      setManualCode(drawerOrder.id);
                      setIsTerminalOpen(true);
                      handleVerifyCode(drawerOrder.id);
                      setDrawerOrderId(null);
                    }}
                    className="w-full py-3 rounded-xl bg-primary text-on-primary font-bold text-xs hover:bg-primary-container transition-colors shadow-md flex items-center justify-center gap-2 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px]">qr_code_scanner</span>
                    Mở Terminal Soát vé &amp; Check-in
                  </button>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      onClick={() => handleOpenReschedule(drawerOrder)}
                      className="py-2.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 font-bold text-xs hover:bg-amber-100 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[16px]">event_repeat</span>
                      Đề xuất đổi ca
                    </button>
                    <button
                      onClick={() => { setRejectOrderId(drawerOrder.id); setDrawerOrderId(null); }}
                      className="py-2.5 rounded-xl bg-rose-50 text-rose-800 border border-rose-200 font-bold text-xs hover:bg-rose-100 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[16px]">cancel</span>
                      Từ chối / Hủy
                    </button>
                  </div>
                </div>
              )}

              {drawerOrder.status === "REFUNDED" && drawerOrder.refundInfo && (
                <button
                  onClick={() => { setSelectedRefundOrder(drawerOrder); setDrawerOrderId(null); }}
                  className="w-full py-3 rounded-xl bg-purple-100 text-purple-900 font-bold text-xs hover:bg-purple-200 transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-[18px]">currency_exchange</span>
                  Xem chi tiết hoàn tiền
                </button>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Terminal Check-in QR Modal with Full Test Suite (Portaled to body) */}
      {isTerminalOpen && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface rounded-2xl max-w-lg w-full p-space-xl shadow-2xl flex flex-col gap-space-md border border-outline-variant/30">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[24px]">qr_code_scanner</span>
                <h3 className="font-headline-sm font-bold text-on-surface">Terminal Soát vé Bến bãi</h3>
              </div>
              <button onClick={() => setIsTerminalOpen(false)} className="text-on-surface-variant hover:text-on-surface cursor-pointer">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            {/* Test Suite Shortcut Chips */}
            <div className="flex flex-col gap-1.5 p-3 rounded-xl bg-surface-container-low text-[12px]">
              <span className="font-bold text-on-surface">Bộ vé mock thử nghiệm nghiệp vụ:</span>
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89412-01"); handleVerifyCode("SUB-89412-01"); }}
                  className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 font-medium hover:bg-emerald-200 cursor-pointer"
                >
                  ✓ Vé hợp lệ
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89415-03"); handleVerifyCode("SUB-89415-03"); }}
                  className="px-2 py-0.5 rounded bg-amber-100 text-amber-900 font-medium hover:bg-amber-200 cursor-pointer"
                >
                  ⚠ Chưa xác nhận
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89422-04"); handleVerifyCode("SUB-89422-04"); }}
                  className="px-2 py-0.5 rounded bg-red-100 text-red-900 font-medium hover:bg-red-200 cursor-pointer"
                >
                  ✗ Đã từ chối
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89425-05"); handleVerifyCode("SUB-89425-05"); }}
                  className="px-2 py-0.5 rounded bg-slate-200 text-slate-800 font-medium hover:bg-slate-300 cursor-pointer"
                >
                  ✗ Đã hủy
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89428-06"); handleVerifyCode("SUB-89428-06"); }}
                  className="px-2 py-0.5 rounded bg-purple-100 text-purple-900 font-medium hover:bg-purple-200 cursor-pointer"
                >
                  ✗ Đã hoàn tiền
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-89430-07"); handleVerifyCode("SUB-89430-07"); }}
                  className="px-2 py-0.5 rounded bg-blue-100 text-blue-900 font-medium hover:bg-blue-200 cursor-pointer"
                >
                  ✗ Đã check-in rồi
                </button>
                <button
                  type="button"
                  onClick={() => { setManualCode("SUB-OTHER-999"); handleVerifyCode("SUB-OTHER-999"); }}
                  className="px-2 py-0.5 rounded bg-red-100 text-red-900 font-medium hover:bg-red-200 cursor-pointer"
                >
                  ✗ Sai đối tác
                </button>
              </div>
            </div>

            {/* High-Tech Animated Scanner Viewfinder */}
            <div className="relative w-full h-44 rounded-2xl bg-neutral-950/80 border border-primary/30 flex items-center justify-center overflow-hidden shadow-inner group">
              <div className="absolute top-3 left-3 w-6 h-6 border-t-2 border-l-2 border-primary"></div>
              <div className="absolute top-3 right-3 w-6 h-6 border-t-2 border-r-2 border-primary"></div>
              <div className="absolute bottom-3 left-3 w-6 h-6 border-b-2 border-l-2 border-primary"></div>
              <div className="absolute bottom-3 right-3 w-6 h-6 border-b-2 border-r-2 border-primary"></div>

              <div className="absolute left-6 right-6 h-0.5 bg-gradient-to-r from-transparent via-primary-container to-transparent shadow-[0_0_15px_#00646f] animate-scan-line pointer-events-none"></div>

              <div className="relative z-10 flex flex-col items-center justify-center text-white/40 group-hover:text-white/60 transition-colors">
                <span className="material-symbols-outlined text-[54px] animate-pulse">qr_code_scanner</span>
                <span className="text-[12px] font-medium text-primary-fixed mt-1">
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
                className="flex-1 px-space-md py-2.5 rounded-xl border border-outline-variant bg-surface text-on-surface font-body-md focus:outline-primary"
                onKeyDown={(e) => { if (e.key === "Enter") handleVerifyCode(); }}
              />
              <button
                onClick={() => handleVerifyCode()}
                disabled={isCheckingIn}
                className="px-4 py-2.5 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container disabled:opacity-50 shadow-sm cursor-pointer"
              >
                {isCheckingIn ? "Đang kiểm tra..." : "Kiểm tra"}
              </button>
            </div>

            {/* Scanner Result Display */}
            {scannerResult.type && (
              <div
                className={`p-space-md rounded-xl text-body-sm flex flex-col gap-2 ${
                  scannerResult.type === "SUCCESS"
                    ? "bg-emerald-50 text-emerald-900 border border-emerald-300"
                    : "bg-red-50 text-red-900 border border-red-300"
                }`}
              >
                <div className="flex items-center gap-2 font-bold">
                  <span className="material-symbols-outlined">
                    {scannerResult.type === "SUCCESS" ? "check_circle" : "error"}
                  </span>
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
                <p>{scannerResult.message}</p>

                {scannerResult.type === "SUCCESS" && scannerResult.order && (
                  <button
                    onClick={() => handleConfirmAdmit(scannerResult.order!.id)}
                    className="mt-2 w-full py-2.5 rounded-xl bg-emerald-600 text-white font-bold hover:bg-emerald-700 shadow-md flex items-center justify-center gap-1.5 cursor-pointer"
                  >
                    <span className="material-symbols-outlined">how_to_reg</span>
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
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface rounded-2xl max-w-md w-full p-space-lg shadow-2xl flex flex-col gap-space-md border border-outline-variant/30">
            <h3 className="font-headline-sm font-bold text-on-surface">Từ chối đơn #{rejectOrderId}</h3>
            <div className="flex flex-col gap-1.5">
              <label className="font-label-md font-semibold text-on-surface text-xs">Lý do từ chối vận hành</label>
              <select
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="p-2.5 rounded-xl border border-outline-variant bg-surface text-on-surface font-body-sm text-xs focus:outline-primary"
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
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setRejectOrderId(null)}
                className="px-4 py-2 rounded-xl bg-surface-container text-on-surface font-label-md text-xs cursor-pointer"
              >
                Hủy
              </button>
              <button
                onClick={handleRejectOrder}
                disabled={isRejecting}
                className="px-4 py-2 rounded-xl bg-error text-white font-label-md font-bold text-xs hover:bg-error/90 disabled:opacity-50 cursor-pointer shadow-sm"
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
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface rounded-2xl max-w-md w-full p-space-xl shadow-2xl flex flex-col gap-space-md border border-outline-variant/30">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-purple-600">currency_exchange</span>
                <h3 className="font-headline-sm font-bold text-on-surface">Chi tiết Hoàn tiền (Refund)</h3>
              </div>
              <button onClick={() => setSelectedRefundOrder(null)} className="text-on-surface-variant hover:text-on-surface cursor-pointer">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            <div className="flex flex-col gap-2 font-body-sm text-xs">
              <div className="flex justify-between py-1 border-b border-outline-variant/20">
                <span className="text-on-surface-variant">Mã đơn con:</span>
                <span className="font-bold text-on-surface font-mono">{selectedRefundOrder.id}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-outline-variant/20">
                <span className="text-on-surface-variant">Số tiền hoàn (amount):</span>
                <span className="font-bold text-purple-700 font-mono">
                  {selectedRefundOrder.refundInfo.amount.toLocaleString("vi-VN")} đ ({selectedRefundOrder.refundInfo.refund_percentage}%)
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-outline-variant/20">
                <span className="text-on-surface-variant">Trạng thái refund:</span>
                <span className="px-2 py-0.5 rounded-full bg-purple-100 text-purple-900 font-bold text-[11px]">
                  {selectedRefundOrder.refundInfo.status}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-outline-variant/20">
                <span className="text-on-surface-variant">Thời gian xử lý:</span>
                <span className="text-on-surface">{selectedRefundOrder.refundInfo.processed_at}</span>
              </div>
              <div className="flex flex-col py-1">
                <span className="text-on-surface-variant font-semibold">Lý do hoàn tiền:</span>
                <p className="mt-1 p-2.5 rounded-xl bg-surface-container-low text-on-surface text-[12px] leading-relaxed">
                  {selectedRefundOrder.refundInfo.reason}
                </p>
              </div>
            </div>
            <div className="flex justify-end pt-2">
              <button
                onClick={() => setSelectedRefundOrder(null)}
                className="px-4 py-2 rounded-xl bg-surface-container-high text-on-surface font-label-md text-xs cursor-pointer"
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
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface rounded-2xl max-w-md w-full p-6 shadow-2xl flex flex-col gap-4 border border-outline-variant/30">
            <div className="flex items-center justify-between border-b border-outline-variant/20 pb-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-amber-600">event_repeat</span>
                <h3 className="font-bold text-slate-900 text-sm md:text-base">Đề xuất đổi ca cho đơn #{drawerOrder.id.slice(0, 8)}...</h3>
              </div>
              <button onClick={() => setIsRescheduleOpen(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="flex flex-col gap-3 text-xs">
              <div className="flex flex-col gap-1">
                <label className="font-semibold text-slate-700">Lý do đề xuất đổi lịch:</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setRescheduleReasonType("WEATHER")}
                    className={`py-2 px-3 rounded-lg border text-xs font-semibold cursor-pointer transition-all ${
                      rescheduleReasonType === "WEATHER"
                        ? "bg-amber-500 text-white border-amber-600 shadow-xs"
                        : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"
                    }`}
                  >
                    Thời tiết biển động
                  </button>
                  <button
                    type="button"
                    onClick={() => setRescheduleReasonType("OPERATIONAL")}
                    className={`py-2 px-3 rounded-lg border text-xs font-semibold cursor-pointer transition-all ${
                      rescheduleReasonType === "OPERATIONAL"
                        ? "bg-blue-600 text-white border-blue-700 shadow-xs"
                        : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"
                    }`}
                  >
                    Vận hành / Kỹ thuật
                  </button>
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="font-semibold text-slate-700">Chi tiết lý do gửi du khách:</label>
                <input
                  type="text"
                  value={rescheduleReason}
                  onChange={(e) => setRescheduleReason(e.target.value)}
                  className="p-2.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-900 text-xs focus:outline-primary"
                  placeholder="Nhập lý do chi tiết..."
                />
              </div>

              <div className="flex flex-col gap-1">
                <label className="font-semibold text-slate-700">Chọn ca khởi hành thay thế:</label>
                {availableSlots.length > 0 ? (
                  <select
                    value={rescheduleSlotId}
                    onChange={(e) => setRescheduleSlotId(e.target.value)}
                    className="p-2.5 rounded-lg border border-slate-200 bg-white text-slate-900 font-semibold text-xs focus:outline-primary"
                  >
                    {availableSlots.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.date} • {s.startTime.slice(0, 5)} - {s.endTime.slice(0, 5)} (Còn {s.capacity - s.bookedCount} chỗ)
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-800 text-[11px]">
                    Không có ca nào đang mở cho dịch vụ này. Vui lòng mở thêm ca tại mục Lịch khởi hành.
                  </div>
                )}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setIsRescheduleOpen(false)}
                className="px-4 py-2 rounded-lg bg-slate-100 text-slate-700 font-semibold text-xs hover:bg-slate-200 cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleSubmitReschedule}
                disabled={isSubmittingReschedule || !rescheduleSlotId}
                className="px-4 py-2 rounded-lg bg-primary text-white font-bold text-xs hover:bg-primary-container disabled:opacity-50 cursor-pointer shadow-xs"
              >
                {isSubmittingReschedule ? "Đang gửi..." : "Gửi đề xuất cho khách"}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
