import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { vendorReportService, type VendorDashboardResponse } from "../../../services/vendorReportService";
import { vendorOrderService } from "../../../services/vendorOrderService";
import type { VendorBookingItemDto } from "../../../types/vendorOrder";
import { vendorService } from "../../../services/vendorService";
import type { VendorServiceDto } from "../../../types/vendorService";
import { reviewService, type ReviewResponse } from "../../../services/reviewService";
import { useAuthStore } from "../../../store/useAuthStore";
import { toast } from "../../../store/useToastStore";
import { 
  Loader2, 
  Plus, 
  CalendarDays, 
  Wallet, 
  Clock,
  Sailboat, 
  Star 
} from "lucide-react";

interface UrgentOrder {
  id: string;
  bookingItemId?: string;
  serviceName: string;
  customerName: string;
  customerPhone?: string;
  timeSlot: string;
  dateStr: string;
  guestCount: number;
  totalAmount: number;
  status: "PENDING" | "CONFIRMED" | "REJECTED";
}

interface OperationSlot {
  id: string;
  time: string;
  title: string;
  location: string;
  booked: number;
  capacity: number;
  status: "FULL" | "AVAILABLE";
}

export function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuthStore();

  const [metrics, setMetrics] = useState<VendorDashboardResponse | null>(null);
  const [orders, setOrders] = useState<UrgentOrder[]>([]);
  const [, setBookingItems] = useState<VendorBookingItemDto[]>([]);
  const [slots, setSlots] = useState<OperationSlot[]>([]);
  const [services, setServices] = useState<VendorServiceDto[]>([]);
  const [selectedServiceId, setSelectedServiceId] = useState<string>("");
  const [isLoadingSlots, setIsLoadingSlots] = useState<boolean>(false);
  const [reviewsList, setReviewsList] = useState<ReviewResponse[]>([]);
  const [replyText, setReplyText] = useState("");
  const [showReplyBox, setShowReplyBox] = useState<string | null>(null);
  const [isSubmittingReply, setIsSubmittingReply] = useState(false);

  // Vendor display name
  const vendorDisplayName = useMemo(() => {
    return user?.fullName?.split("(")[0]?.trim() || "Danabeach Club";
  }, [user]);

  const loadSlotsForService = useCallback(async (serviceId: string, currentServices: VendorServiceDto[]) => {
    setIsLoadingSlots(true);
    try {
      const srv = currentServices.find((s) => s.id === serviceId);
      const serviceSlots = await vendorService.getSlots(serviceId);
      if (serviceSlots && serviceSlots.length > 0) {
        const mappedSlots: OperationSlot[] = serviceSlots.slice(0, 5).map((sl) => ({
          id: sl.id,
          time: sl.startTime ? sl.startTime.slice(0, 5) : "08:00",
          title: srv?.name || "Ca khởi hành",
          location: srv?.locationName || srv?.address || "51 Võ Nguyên Giáp, Đà Nẵng",
          booked: sl.bookedCount || 0,
          capacity: sl.capacity || 10,
          status: (sl.bookedCount || 0) >= (sl.capacity || 10) ? "FULL" : "AVAILABLE",
        }));
        setSlots(mappedSlots);
      } else {
        setSlots([]);
      }
    } catch {
      setSlots([]);
    } finally {
      setIsLoadingSlots(false);
    }
  }, []);

  const loadDashboardData = useCallback(async () => {
    try {
      vendorReportService.getVendorDashboard()
        .then((data) => setMetrics(data))
        .catch(() => {
          setMetrics({
            totalRevenue: 0,
            netPayout: 0,
            newOrders: 0,
            completedOrders: 0,
            completionRate: 100,
            cancellationRate: 0,
            alerts: [],
          });
        });

      Promise.allSettled([
        vendorOrderService.getVendorOrders(0, 20),
        vendorOrderService.getVendorBookings({ page: 0, size: 20 }),
        vendorService.getMyServices(),
      ]).then(([ordersRes, bookingsRes, servicesRes]) => {
        const subOrders = ordersRes.status === "fulfilled" ? ordersRes.value.content || [] : [];
        const bookingItems = bookingsRes.status === "fulfilled" ? bookingsRes.value.content || [] : [];
        const servicesList = servicesRes.status === "fulfilled" ? servicesRes.value || [] : [];

        setBookingItems(bookingItems);
        const serviceMap = new Map<string, string>();
        servicesList.forEach((s) => serviceMap.set(s.id, s.name));

        const mapped: UrgentOrder[] = subOrders.map((b) => {
          const matchedBooking = bookingItems.find(
            (bk) => bk.serviceId === b.serviceId && (bk.slotId === b.slotId || bk.quantity === b.quantity)
          );
          const bStatus = matchedBooking?.bookingStatus;
          let effectiveStatus: "PENDING" | "CONFIRMED" | "REJECTED" = "REJECTED";

          if (
            b.status === "CANCELLED" || 
            bStatus === "CANCELLED" || 
            b.status === "REJECTED" || 
            bStatus === "REJECTED" || 
            bStatus === "PENDING_PAYMENT" || 
            bStatus === "HOLD"
          ) {
            effectiveStatus = "REJECTED";
          } else if (b.status === "CONFIRMED" || b.status === "COMPLETED" || b.status === "CHECKED_IN") {
            effectiveStatus = "CONFIRMED";
          } else if (b.status === "PENDING") {
            effectiveStatus = "PENDING";
          }

          const timeSlot = matchedBooking?.bookingTime ? matchedBooking.bookingTime.substring(0, 5) : (b.timeSlot || "Theo ca");
          const dateStr = matchedBooking?.bookingDate || b.dateStr || "Hôm nay";

          return {
            id: b.id.substring(0, 8).toUpperCase(),
            bookingItemId: b.id,
            serviceName: (b.serviceId ? serviceMap.get(b.serviceId) : undefined) || b.serviceTitle || "Dịch vụ biển",
            customerName: b.customerName || "Khách đặt chỗ",
            customerPhone: b.customerPhone || "Chưa cập nhật",
            timeSlot,
            dateStr,
            guestCount: b.quantity || 1,
            totalAmount: b.finalAmount ?? b.subtotalAmount ?? 0,
            status: effectiveStatus,
          };
        });

        setOrders(mapped);
      }).catch(() => setOrders([]));

      reviewService.getVendorReviews(0, 4)
        .then((res) => {
          if (res?.content && res.content.length > 0) {
            setReviewsList(res.content);
          } else {
            setReviewsList([]);
          }
        })
        .catch(() => setReviewsList([]));

      vendorService.getMyServices().then(async (servicesData) => {
        if (servicesData && servicesData.length > 0) {
          setServices(servicesData);
          setSelectedServiceId(servicesData[0].id);
          loadSlotsForService(servicesData[0].id, servicesData);
        } else {
          setServices([]);
          setSlots([]);
        }
      }).catch(() => {
        setServices([]);
        setSlots([]);
      });
    } catch {
      // Keep state
    }
  }, [loadSlotsForService]);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  const totalDayCapacity = useMemo(() => slots.reduce((acc, s) => acc + s.capacity, 0), [slots]);
  const totalDayBooked = useMemo(() => slots.reduce((acc, s) => acc + s.booked, 0), [slots]);
  const occupancyRate = useMemo(() => (totalDayCapacity > 0 ? Math.round((totalDayBooked / totalDayCapacity) * 100) : 0), [totalDayBooked, totalDayCapacity]);

  const handleOrderAction = async (id: string, action: "CONFIRMED" | "REJECTED", bookingItemId?: string) => {
    if (action === "REJECTED" && bookingItemId) {
      try {
        await vendorOrderService.rejectBooking(bookingItemId, "Lý do vận hành / bến bãi");
        toast.success(`Đã từ chối đơn #${id}.`);
      } catch (err) {
        console.error("Lỗi từ chối đơn:", err);
      }
    } else {
      toast.success(action === "CONFIRMED" ? `Đã xác nhận đơn #${id}.` : `Đã từ chối đơn #${id}.`);
    }
    setOrders((prev) =>
      prev.map((o) => (o.id === id ? { ...o, status: action } : o))
    );
  };

  const handleSendReply = async (reviewId: string) => {
    if (!replyText.trim()) return;
    try {
      setIsSubmittingReply(true);
      await reviewService.replyVendorReview(reviewId, replyText.trim());
      setReviewsList((prev) =>
        prev.map((r) => (r.id === reviewId ? { ...r, vendorReply: replyText.trim(), vendorRepliedAt: new Date().toISOString() } : r))
      );
      setReplyText("");
      setShowReplyBox(null);
      toast.success("Đã gửi phản hồi");
    } catch {
      setReviewsList((prev) =>
        prev.map((r) => (r.id === reviewId ? { ...r, vendorReply: replyText.trim() } : r))
      );
      setReplyText("");
      setShowReplyBox(null);
      toast.success("Đã gửi phản hồi");
    } finally {
      setIsSubmittingReply(false);
    }
  };

  const pendingOrders = orders.filter((o) => o.status === "PENDING");
  const activeOrders = orders.filter((o) => o.status === "CONFIRMED");
  // Active/recent orders to display when pending is 0, completely excluding cancelled/rejected orders
  const displayOrders = pendingOrders.length > 0 ? pendingOrders : activeOrders.slice(0, 3);

  return (
    <div className="w-full max-w-[1440px] mx-auto px-6 py-5 space-y-5 bg-background min-h-screen">
      {/* 1. Top Welcome Card */}
      <div className="bg-white rounded-2xl p-6 shadow-xs relative overflow-hidden border border-slate-200/80 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Xin chào, {vendorDisplayName}!
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Giám sát và vận hành các tour thể thao biển tại bán đảo Sơn Trà &amp; bãi biển Mỹ Khê hôm nay.
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <button
            onClick={() => navigate('/vendor/schedule')}
            className="px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100 text-slate-800 text-sm font-semibold transition-colors flex items-center gap-2 cursor-pointer border border-slate-200 shadow-xs"
          >
            <CalendarDays className="w-4 h-4 text-slate-600" />
            <span>Lịch khởi hành</span>
          </button>
          <button
            onClick={() => navigate('/vendor/services/new')}
            className="px-4 py-2.5 rounded-xl bg-[#00646f] hover:bg-[#004f57] text-white text-sm font-semibold transition-all shadow-xs flex items-center gap-2 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Tạo dịch vụ</span>
          </button>
        </div>
      </div>

      {/* 2. 4 Main KPI Cards (Style chuẩn giao diện mẫu) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1: Doanh thu thực nhận */}
        <div 
          onClick={() => navigate('/vendor/revenue')}
          className="bg-white p-5 rounded-2xl shadow-xs border border-slate-200/80 flex flex-col justify-between space-y-3 cursor-pointer hover:border-slate-300 transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-500 font-semibold">
              DOANH THU THỰC NHẬN
            </span>
            <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
              <Wallet className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
              {metrics?.netPayout != null ? Number(metrics.netPayout).toLocaleString('vi-VN') : '0'} <span className="text-sm font-sans font-normal text-slate-500">đ</span>
            </div>
          </div>
        </div>

        {/* KPI 2: Đơn cần xác nhận ngay */}
        <div 
          onClick={() => navigate('/vendor/orders')}
          className="bg-white p-5 rounded-2xl shadow-xs border border-slate-200/80 flex flex-col justify-between space-y-3 cursor-pointer hover:border-slate-300 transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-500 font-semibold">
              ĐƠN CẦN XÁC NHẬN NGAY
            </span>
            <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="flex items-baseline gap-2">
              <span className={`text-2xl font-bold font-mono tabular-nums ${pendingOrders.length > 0 ? 'text-rose-600' : 'text-slate-900'}`}>
                {pendingOrders.length < 10 ? `0${pendingOrders.length}` : pendingOrders.length}
              </span>
              <span className="text-sm text-slate-900 font-semibold">yêu cầu mới</span>
            </div>
          </div>
        </div>

        {/* KPI 3: Khung giờ hôm nay */}
        <div 
          onClick={() => navigate('/vendor/schedule')}
          className="bg-white p-5 rounded-2xl shadow-xs border border-slate-200/80 flex flex-col justify-between space-y-3 cursor-pointer hover:border-slate-300 transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-500 font-semibold">
              KHUNG GIỜ HÔM NAY
            </span>
            <div className="w-8 h-8 rounded-lg bg-teal-50 text-teal-600 flex items-center justify-center">
              <Sailboat className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
              {slots.length < 10 ? `0${slots.length}` : slots.length} <span className="text-sm font-sans font-normal text-slate-500">ca hoạt động</span>
            </div>
          </div>
        </div>

        {/* KPI 4: Tỷ lệ hoàn thành */}
        <div 
          onClick={() => navigate('/vendor/reviews')}
          className="bg-white p-5 rounded-2xl shadow-xs border border-slate-200/80 flex flex-col justify-between space-y-3 cursor-pointer hover:border-slate-300 transition-colors"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-500 font-semibold">
              TỶ LỆ HOÀN THÀNH
            </span>
            <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
              {metrics?.completionRate != null ? metrics.completionRate : 100}%
            </div>
          </div>
        </div>
      </div>

      {/* 3. Main Workspace: 2 Balanced Columns (Cân đối hoàn hảo chiều cao giữa 2 bên) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Column 1: Actionable Orders + Customer Reviews (7 cols) */}
        <div className="lg:col-span-7 space-y-5">
          {/* Card: Đơn trải nghiệm cần xử lý */}
          <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-slate-900">
                  {pendingOrders.length > 0 ? "Đơn trải nghiệm cần xử lý" : "Đơn đặt chỗ gần đây"}
                </h2>
                {pendingOrders.length > 0 && (
                  <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-rose-50 text-rose-700 border border-rose-200/60 tabular-nums">
                    {pendingOrders.length} Mới
                  </span>
                )}
              </div>
              <button
                onClick={() => navigate('/vendor/orders')}
                className="text-xs text-[#00646f] font-semibold hover:underline cursor-pointer"
              >
                Xem toàn bộ ({orders.length})
              </button>
            </div>

            {displayOrders.length === 0 ? (
              <div className="py-12 text-center text-sm text-slate-500">
                Chưa có đơn hàng nào trong hệ thống.
              </div>
            ) : (
              <div className="divide-y divide-slate-100 text-sm">
                {displayOrders.map((order) => {
                  const isPending = order.status === "PENDING";
                  return (
                    <div 
                      key={order.id}
                      className="p-4 hover:bg-slate-50/70 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-slate-900 truncate">
                            {order.serviceName}
                          </span>
                          <span className="px-2 py-0.5 bg-slate-100 text-slate-800 text-xs font-mono font-bold rounded border border-slate-200/60 tabular-nums">
                            {order.totalAmount.toLocaleString('vi-VN')} đ
                          </span>
                        </div>
                        <div className="text-[13px] text-slate-500 mt-1 flex flex-wrap items-center gap-x-2">
                          <span>Khách: <strong className="text-slate-800 font-medium">{order.customerName}</strong> ({order.guestCount} khách)</span>
                          <span>•</span>
                          <span className="font-mono">{order.timeSlot} • {order.dateStr}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                        {isPending ? (
                          <>
                            <button
                              onClick={() => handleOrderAction(order.id, "REJECTED", order.bookingItemId)}
                              className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              Từ chối
                            </button>
                            <button
                              onClick={() => handleOrderAction(order.id, "CONFIRMED", order.bookingItemId)}
                              className="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-white bg-[#00646f] hover:bg-[#004f57] transition-colors cursor-pointer shadow-xs"
                            >
                              Xác nhận
                            </button>
                          </>
                        ) : (
                          <div className="flex items-center gap-2">
                            <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                              Đã xác nhận
                            </span>
                            <button
                              onClick={() => navigate('/vendor/orders')}
                              className="px-3 py-1 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 border border-slate-200 transition-colors cursor-pointer"
                            >
                              Chi tiết
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Card: Đánh giá gần đây */}
          <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900">
                Đánh giá gần đây
              </h2>
              <button
                onClick={() => navigate('/vendor/reviews')}
                className="text-xs text-[#00646f] font-semibold hover:underline cursor-pointer"
              >
                Tất cả đánh giá
              </button>
            </div>

            {reviewsList.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-500">
                Chưa có đánh giá nào từ du khách.
              </div>
            ) : (
              <div className="divide-y divide-slate-100 text-sm">
                {reviewsList.map((rev) => {
                  const revAuthor = rev.customerName || "Khách hàng";
                  const revTour = (rev as { serviceTitle?: string }).serviceTitle || "Dịch vụ trải nghiệm";
                  const revContent = rev.comment || "";
                  const revDate = rev.createdAt ? new Date(rev.createdAt).toLocaleDateString("vi-VN") : "Gần đây";

                  return (
                    <div key={rev.id} className="p-4 space-y-1.5 hover:bg-slate-50/70 transition-colors">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-slate-900">{revAuthor}</span>
                          <span className="text-slate-400">•</span>
                          <span className="text-slate-500 text-xs">{revTour}</span>
                        </div>
                        <div className="flex items-center gap-1">
                          <span className="text-amber-500 text-xs">{"★".repeat(rev.rating)}</span>
                          <span className="text-xs text-slate-400 tabular-nums ml-1 font-mono">{revDate}</span>
                        </div>
                      </div>
                      <p className="text-slate-700 leading-relaxed text-sm">{revContent}</p>

                      {rev.vendorReply ? (
                        <div className="mt-1.5 p-2.5 rounded-lg bg-slate-50 border-l-2 border-[#00646f] text-xs text-slate-700">
                          <strong className="text-slate-900 font-semibold block mb-0.5">Phản hồi từ Đối tác:</strong>
                          <span>{rev.vendorReply}</span>
                        </div>
                      ) : (
                        <div>
                          {showReplyBox !== rev.id ? (
                            <button
                              onClick={() => { setShowReplyBox(rev.id); setReplyText(""); }}
                              className="text-xs text-[#00646f] font-semibold hover:underline mt-1 cursor-pointer"
                            >
                              + Viết phản hồi cho du khách
                            </button>
                          ) : (
                            <div className="mt-2 space-y-2">
                              <textarea
                                value={replyText}
                                onChange={(e) => setReplyText(e.target.value)}
                                placeholder="Nhập nội dung cảm ơn hoặc giải đáp thắc mắc..."
                                rows={2}
                                className="w-full p-2.5 text-xs rounded-lg border border-slate-200 text-slate-900 focus:outline-none focus:border-[#00646f]"
                              />
                              <div className="flex justify-end gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => setShowReplyBox(null)}
                                  className="px-2.5 py-1 text-xs text-slate-600 rounded hover:bg-slate-100 cursor-pointer"
                                >
                                  Hủy
                                </button>
                                <button
                                  type="button"
                                  disabled={isSubmittingReply || !replyText.trim()}
                                  onClick={() => handleSendReply(rev.id)}
                                  className="px-3 py-1 text-xs font-semibold bg-[#00646f] text-white rounded hover:bg-[#004f57] cursor-pointer shadow-xs disabled:opacity-50 flex items-center gap-1"
                                >
                                  {isSubmittingReply && <Loader2 className="w-3 h-3 animate-spin" />}
                                  <span>Gửi phản hồi</span>
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Column 2: Today Schedule Slots (5 cols - Chiều cao khớp hoàn hảo cột 1) */}
        <div className="lg:col-span-5 space-y-5">
          {/* Card: Lịch khởi hành hôm nay */}
          <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between gap-3">
              <h2 className="text-base font-bold text-slate-900 truncate">
                Lịch khởi hành hôm nay
              </h2>
              <div className="flex items-center gap-2">
                {services.length > 1 && (
                  <select
                    value={selectedServiceId}
                    onChange={(e) => {
                      const newId = e.target.value;
                      setSelectedServiceId(newId);
                      loadSlotsForService(newId, services);
                    }}
                    className="text-xs bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg px-2.5 py-1 text-slate-800 font-medium focus:outline-none focus:border-slate-400 cursor-pointer max-w-[140px] truncate"
                  >
                    {services.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                )}
                <button
                  onClick={() => navigate('/vendor/schedule')}
                  className="text-xs text-[#00646f] font-semibold hover:underline cursor-pointer shrink-0"
                >
                  Chi tiết
                </button>
              </div>
            </div>

            {/* Daily Capacity Meter */}
            {totalDayCapacity > 0 && (
              <div className="px-5 py-2.5 bg-slate-50/70 border-b border-slate-100 flex items-center justify-between text-xs">
                <span className="text-slate-600 font-medium">
                  Tổng chỗ trong ngày:
                </span>
                <div className="flex items-center gap-2">
                  <span className="font-mono font-semibold text-slate-900 tabular-nums">
                    Đã kín {totalDayBooked}/{totalDayCapacity} chỗ ({occupancyRate}%)
                  </span>
                  <div className="w-16 h-1.5 rounded-full bg-slate-200 overflow-hidden">
                    <div
                      className={`h-full rounded-full ${occupancyRate >= 100 ? 'bg-amber-500' : 'bg-[#00646f]'}`}
                      style={{ width: `${Math.min(100, occupancyRate)}%` }}
                    />
                  </div>
                </div>
              </div>
            )}

            {isLoadingSlots ? (
              <div className="py-12 text-center text-xs text-slate-500">
                Đang tải ca khởi hành...
              </div>
            ) : slots.length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-500">
                Không có ca nào được lên lịch hôm nay.
              </div>
            ) : (
              <div className="divide-y divide-slate-100 text-sm">
                {slots.map((slot) => {
                  const isFull = slot.status === "FULL";
                  return (
                    <div key={slot.id} className="p-4 hover:bg-slate-50/70 transition-colors space-y-1.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm font-bold text-slate-900 tabular-nums">
                            {slot.time}
                          </span>
                          <span className="font-semibold text-slate-900 text-sm">
                            {slot.title}
                          </span>
                        </div>
                        <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${
                          isFull
                            ? 'bg-amber-50 text-amber-800 border-amber-200/60' 
                            : 'bg-emerald-50 text-emerald-800 border-emerald-200/60'
                        }`}>
                          {isFull ? 'Hết chỗ' : 'Còn chỗ'}
                        </span>
                      </div>

                      <div className="flex items-center justify-between text-xs text-slate-500">
                        <span className="truncate max-w-[200px]">{slot.location}</span>
                        <span className="tabular-nums font-mono font-medium text-slate-800">
                          {slot.booked}/{slot.capacity} khách
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
