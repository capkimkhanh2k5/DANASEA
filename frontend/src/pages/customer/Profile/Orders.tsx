import { useState, useMemo, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { orderService } from "../../../services/orderService";
import { serviceCatalogService } from "../../../services/serviceCatalogService";
import { useAuthStore } from "../../../store/useAuthStore";
import { authService } from "../../../services/authService";
import type {
  OrderResponse,
  SubOrderResponse,
  CancellationPreviewResponse,
  RefundRequest,
  QrCodeResponse,
} from "../../../types/order";
import type { ServiceDetailDto } from "../../../types/service";

type OrderFilter = "all" | "upcoming" | "pending" | "completed" | "cancelled";

export function Orders() {
  const navigate = useNavigate();
  const { user, isAuthenticated, accessToken, setAuth } = useAuthStore();

  // State management
  const [filter, setFilter] = useState<OrderFilter>("all");
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [serviceDetails, setServiceDetails] = useState<Record<string, ServiceDetailDto>>({});

  // QR Modal state
  const [selectedSubOrder, setSelectedSubOrder] = useState<SubOrderResponse | null>(null);
  const [qrData, setQrData] = useState<QrCodeResponse | null>(null);
  const [qrLoading, setQrLoading] = useState<boolean>(false);
  const [qrError, setQrError] = useState<string | null>(null);

  // Cancellation Preview Modal state
  const [cancellationOrder, setCancellationOrder] = useState<OrderResponse | null>(null);
  const [cancellationPreview, setCancellationPreview] = useState<CancellationPreviewResponse | null>(null);
  const [cancellationLoading, setCancellationLoading] = useState<boolean>(false);
  const [cancellationSubmitting, setCancellationSubmitting] = useState<boolean>(false);
  const [cancellationReason, setCancellationReason] = useState<RefundRequest["reason"]>("CUSTOMER_REQUEST");
  const [cancellationError, setCancellationError] = useState<string | null>(null);
  const [cancellationSuccess, setCancellationSuccess] = useState<string | null>(null);

  // PDF Receipt state
  const [downloadingPdfId, setDownloadingPdfId] = useState<string | null>(null);

  // Review Modal state
  const [reviewingSubOrder, setReviewingSubOrder] = useState<SubOrderResponse | null>(null);
  const [ratingStars, setRatingStars] = useState(5);
  const [reviewComment, setReviewComment] = useState("");
  const [submittedReviews, setSubmittedReviews] = useState<Record<string, { rating: number; comment: string }>>({});

  // Dispute Modal state
  const [disputingSubOrder, setDisputingSubOrder] = useState<SubOrderResponse | null>(null);
  const [disputeReason, setDisputeReason] = useState("");
  const [submittedDisputes, setSubmittedDisputes] = useState<Record<string, string>>({});

  // Fetch orders from API
  const fetchOrders = useCallback(async (targetPage = 0) => {
    if (!isAuthenticated || !accessToken) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const res = await orderService.getMyOrders(targetPage, 20);
      setOrders(res.content || []);
      setPage(res.page || 0);
      setTotalPages(res.totalPages || 1);

      // Extract unique service IDs to fetch service details
      const serviceIds = new Set<string>();
      (res.content || []).forEach((ord) => {
        const items = ord.items || ord.subOrders || [];
        items.forEach((item) => {
          if (item.serviceId) serviceIds.add(item.serviceId);
        });
      });

      // Lazy load service details for titles and images
      serviceIds.forEach(async (sId) => {
        if (!serviceDetails[sId]) {
          try {
            const detail = await serviceCatalogService.getServiceDetail(sId);
            setServiceDetails((prev) => ({ ...prev, [sId]: detail }));
          } catch {
            // Non-critical, fallback will be used
          }
        }
      });
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi tải danh sách đơn hàng:", err);
      let msg = "Không thể tải danh sách đơn hàng từ máy chủ.";
      if (err && typeof err === "object" && "response" in err) {
        const resp = (err as { response?: { status?: number; data?: { message?: string } } }).response;
        if (resp?.status === 401) {
          msg = "Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập. Vui lòng đăng nhập lại.";
        } else if (resp?.data?.message) {
          msg = resp.data.message;
        }
      } else if (err instanceof Error) {
        msg = err.message;
      }
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated, accessToken, serviceDetails]);

  useEffect(() => {
    if (isAuthenticated && accessToken) {
      fetchOrders(page);
    } else {
      setLoading(false);
    }
  }, [page, isAuthenticated, accessToken, fetchOrders]);

  // Quick Login helper for testing / unauthenticated state
  const handleQuickLogin = async () => {
    try {
      setLoading(true);
      const res = await authService.login({
        email: "customer@danasea.vn",
        password: "password123",
      });
      setAuth(
        {
          id: res.userId,
          email: res.email,
          fullName: "Nguyễn Văn Khách",
          role: res.role,
          avatarUrl: "",
          isEmailVerified: true,
          isLocked: false,
          passwordHash: "",
          phone: "0905000003",
          locale: "vi",
        },
        res.accessToken
      );
    } catch (err) {
      console.error("[Orders] Đăng nhập nhanh thất bại:", err);
      navigate("/auth");
    } finally {
      setLoading(false);
    }
  };

  // Filtered orders
  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      const items = order.items || order.subOrders || [];
      const masterStatus = (order.status || "").toUpperCase();

      if (filter === "all") return true;

      if (filter === "upcoming") {
        return (
          masterStatus === "PAID" ||
          items.some((i) => i.status === "CONFIRMED")
        );
      }

      if (filter === "pending") {
        return masterStatus === "PENDING_PAYMENT";
      }

      if (filter === "completed") {
        return (
          masterStatus === "COMPLETED" ||
          masterStatus === "PARTIALLY_COMPLETED" ||
          items.some((i) => i.status === "COMPLETED" || i.status === "CHECKED_IN")
        );
      }

      if (filter === "cancelled") {
        return (
          masterStatus === "CANCELLED" ||
          items.some((i) => i.status === "CANCELLED" || i.status === "REFUNDED" || i.status === "PARTIALLY_REFUNDED")
        );
      }

      return true;
    });
  }, [orders, filter]);

  // Tab counts
  const tabCounts = useMemo(() => {
    let upcoming = 0;
    let pending = 0;
    let completed = 0;
    let cancelled = 0;

    orders.forEach((ord) => {
      const st = (ord.status || "").toUpperCase();
      const items = ord.items || ord.subOrders || [];
      if (st === "PAID" || items.some((i) => i.status === "CONFIRMED")) upcoming++;
      if (st === "PENDING_PAYMENT") pending++;
      if (st === "COMPLETED" || st === "PARTIALLY_COMPLETED" || items.some((i) => i.status === "COMPLETED" || i.status === "CHECKED_IN")) completed++;
      if (st === "CANCELLED" || items.some((i) => i.status === "CANCELLED" || i.status === "REFUNDED")) cancelled++;
    });

    return {
      all: orders.length,
      upcoming,
      pending,
      completed,
      cancelled,
    };
  }, [orders]);

  // Check-in QR Generation handler
  const handleOpenQrModal = async (subOrder: SubOrderResponse) => {
    setSelectedSubOrder(subOrder);
    setQrLoading(true);
    setQrError(null);
    setQrData(null);

    try {
      const data = await orderService.getCheckinQrCode(subOrder.id);
      setQrData(data);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi tạo mã QR Check-in:", err);
      const msg = err instanceof Error ? err.message : "Không thể tạo mã QR cho đơn hàng này.";
      setQrError(msg);
    } finally {
      setQrLoading(false);
    }
  };

  // PDF Download handler
  const handleDownloadReceiptPdf = async (orderId: string) => {
    try {
      setDownloadingPdfId(orderId);
      const blob = await orderService.downloadOrderReceiptPdf(orderId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `DANASEA-Receipt-${orderId.slice(0, 8).toUpperCase()}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi tải vé/hóa đơn PDF:", err);
      alert("Không thể tải hóa đơn PDF. Vui lòng kiểm tra lại trạng thái đơn hàng.");
    } finally {
      setDownloadingPdfId(null);
    }
  };

  // Cancellation Preview handler
  const handleOpenCancellationModal = async (order: OrderResponse) => {
    setCancellationOrder(order);
    setCancellationLoading(true);
    setCancellationError(null);
    setCancellationSuccess(null);
    setCancellationPreview(null);

    try {
      const preview = await orderService.getCancellationPreview(order.id);
      setCancellationPreview(preview);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi lấy chính sách hủy:", err);
      const msg = err instanceof Error ? err.message : "Không thể lấy thông tin hủy đơn.";
      setCancellationError(msg);
    } finally {
      setCancellationLoading(false);
    }
  };

  // Confirm Cancellation handler
  const handleConfirmCancellation = async () => {
    if (!cancellationOrder) return;
    try {
      setCancellationSubmitting(true);
      setCancellationError(null);
      await orderService.requestRefund(cancellationOrder.id, cancellationReason);
      setCancellationSuccess("Yêu cầu hủy đơn và hoàn tiền đã được gửi thành công!");
      setTimeout(() => {
        setCancellationOrder(null);
        fetchOrders(page);
      }, 1500);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi xác nhận hủy đơn:", err);
      const msg = err instanceof Error ? err.message : "Gửi yêu cầu hủy đơn thất bại.";
      setCancellationError(msg);
    } finally {
      setCancellationSubmitting(false);
    }
  };

  // Review handler
  const handleOpenReview = (subOrder: SubOrderResponse) => {
    setReviewingSubOrder(subOrder);
    setRatingStars(5);
    setReviewComment("");
  };

  const handleSubmitReview = () => {
    if (!reviewingSubOrder) return;
    setSubmittedReviews((prev) => ({
      ...prev,
      [reviewingSubOrder.id]: { rating: ratingStars, comment: reviewComment },
    }));
    setReviewingSubOrder(null);
  };

  // Dispute handler
  const handleOpenDispute = (subOrder: SubOrderResponse) => {
    setDisputingSubOrder(subOrder);
    setDisputeReason("");
  };

  const handleSubmitDispute = () => {
    if (!disputingSubOrder || !disputeReason.trim()) return;
    setSubmittedDisputes((prev) => ({
      ...prev,
      [disputingSubOrder.id]: disputeReason,
    }));
    setDisputingSubOrder(null);
  };

  // Helper formatting functions
  const formatVnd = (amount?: number) => {
    return (amount ?? 0).toLocaleString("vi-VN") + "đ";
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return "N/A";
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return dateStr;
    }
  };

  const getStatusBadge = (status: string) => {
    const s = status.toUpperCase();
    switch (s) {
      case "PAID":
        return <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-bold">ĐÃ THANH TOÁN</span>;
      case "PENDING_PAYMENT":
      case "PENDING":
        return <span className="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-xs font-bold">CHỜ THANH TOÁN</span>;
      case "CONFIRMED":
        return <span className="px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 text-xs font-bold">ĐÃ XÁC NHẬN</span>;
      case "CHECKED_IN":
        return <span className="px-2.5 py-0.5 rounded-full bg-cyan-50 text-cyan-700 border border-cyan-200 text-xs font-bold">ĐÃ CHECK-IN</span>;
      case "COMPLETED":
        return <span className="px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 text-xs font-bold">HOÀN TẤT</span>;
      case "CANCELLED":
        return <span className="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 text-xs font-bold">ĐÃ HỦY</span>;
      case "REFUNDED":
      case "PARTIALLY_REFUNDED":
        return <span className="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 text-xs font-bold">ĐÃ HOÀN TIỀN</span>;
      default:
        return <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200 text-xs font-bold">{s}</span>;
    }
  };

  // Unauthenticated Guard Screen
  if (!isAuthenticated || !user) {
    return (
      <main className="w-full pt-28 pb-20 bg-slate-50/50 flex-1 min-h-screen flex items-center justify-center px-4">
        <div className="max-w-md w-full bg-white/90 backdrop-blur-md border border-cyan-100 rounded-3xl p-8 text-center space-y-5 shadow-sm">
          <div className="w-16 h-16 rounded-full bg-cyan-50 border border-cyan-200 flex items-center justify-center mx-auto text-cyan-600">
            <span className="material-symbols-outlined text-[32px]">lock</span>
          </div>
          <div className="space-y-1.5">
            <h2 className="text-xl font-bold text-slate-900">Vui lòng đăng nhập</h2>
            <p className="text-xs text-slate-500 max-w-xs mx-auto">
              Bạn cần đăng nhập tài khoản Khách hàng để xem vé điện tử và quản lý đơn đặt của mình.
            </p>
          </div>
          <div className="space-y-2 pt-2">
            <button
              onClick={() => navigate("/auth")}
              className="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-xs font-bold hover:shadow-md cursor-pointer transition-all"
            >
              Đến trang Đăng nhập
            </button>
            <button
              onClick={handleQuickLogin}
              className="w-full py-2.5 rounded-xl border border-cyan-200 bg-cyan-50/60 hover:bg-cyan-50 text-cyan-700 text-xs font-bold cursor-pointer transition-colors"
            >
              Đăng nhập nhanh (customer@danasea.vn)
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-20 bg-slate-50/50 flex-1 min-h-screen">
      <div className="flex flex-col w-full">
        {/* Top Filter Tabs Bar */}
        <div className="relative w-full overflow-hidden border-b border-cyan-100/60 bg-white/80 backdrop-blur-md">
          <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-8 lg:px-12 py-3.5">
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => setFilter("all")}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filter === "all"
                    ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/20"
                    : "bg-white text-slate-600 hover:bg-slate-100 border border-slate-200"
                }`}
                type="button"
              >
                <span>Tất cả</span>
                <span className={`px-1.5 py-0.5 rounded-full text-[10px] ${filter === "all" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-600"}`}>
                  {tabCounts.all}
                </span>
              </button>

              <button
                onClick={() => setFilter("upcoming")}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filter === "upcoming"
                    ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/20"
                    : "bg-white text-slate-600 hover:bg-slate-100 border border-slate-200"
                }`}
                type="button"
              >
                <span>Sắp diễn ra</span>
                <span className={`px-1.5 py-0.5 rounded-full text-[10px] ${filter === "upcoming" ? "bg-white/20 text-white" : "bg-cyan-50 text-cyan-700"}`}>
                  {tabCounts.upcoming}
                </span>
              </button>

              <button
                onClick={() => setFilter("pending")}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filter === "pending"
                    ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/20"
                    : "bg-white text-slate-600 hover:bg-slate-100 border border-slate-200"
                }`}
                type="button"
              >
                <span>Chờ thanh toán</span>
                {tabCounts.pending > 0 && (
                  <span className={`px-1.5 py-0.5 rounded-full text-[10px] ${filter === "pending" ? "bg-white/20 text-white" : "bg-amber-100 text-amber-800"}`}>
                    {tabCounts.pending}
                  </span>
                )}
              </button>

              <button
                onClick={() => setFilter("completed")}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filter === "completed"
                    ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/20"
                    : "bg-white text-slate-600 hover:bg-slate-100 border border-slate-200"
                }`}
                type="button"
              >
                <span>Đã hoàn thành</span>
                <span className={`px-1.5 py-0.5 rounded-full text-[10px] ${filter === "completed" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-600"}`}>
                  {tabCounts.completed}
                </span>
              </button>

              <button
                onClick={() => setFilter("cancelled")}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filter === "cancelled"
                    ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-sm shadow-cyan-500/20"
                    : "bg-white text-slate-600 hover:bg-slate-100 border border-slate-200"
                }`}
                type="button"
              >
                <span>Đã hủy / Hoàn tiền</span>
                <span className={`px-1.5 py-0.5 rounded-full text-[10px] ${filter === "cancelled" ? "bg-white/20 text-white" : "bg-slate-100 text-slate-600"}`}>
                  {tabCounts.cancelled}
                </span>
              </button>
            </div>
          </div>
        </div>

        {/* Content Container */}
        <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-8 lg:px-12 py-5 sm:py-6">
          {/* Loading Skeleton */}
          {loading && (
            <div className="flex flex-col gap-6">
              {[1, 2].map((i) => (
                <div key={i} className="bg-white/80 rounded-2xl p-6 border border-slate-200 animate-pulse space-y-4">
                  <div className="h-6 bg-slate-200 rounded w-1/3"></div>
                  <div className="h-24 bg-slate-100 rounded-xl"></div>
                </div>
              ))}
            </div>
          )}

          {/* Error Alert */}
          {!loading && error && (
            <div className="p-6 bg-white border border-rose-200 rounded-3xl text-center space-y-3 shadow-sm max-w-lg mx-auto">
              <div className="w-12 h-12 rounded-full bg-rose-50 text-rose-500 flex items-center justify-center mx-auto">
                <span className="material-symbols-outlined text-[26px]">info</span>
              </div>
              <p className="text-sm font-bold text-slate-800">{error}</p>
              <div className="flex items-center justify-center gap-2 pt-2">
                {error.includes("hết hạn") || error.includes("đăng nhập") ? (
                  <button
                    onClick={() => navigate("/auth")}
                    className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-xs font-bold cursor-pointer hover:shadow-md"
                  >
                    Đăng nhập lại
                  </button>
                ) : (
                  <button
                    onClick={() => fetchOrders(page)}
                    className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold cursor-pointer"
                  >
                    Thử lại
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Empty State */}
          {!loading && !error && filteredOrders.length === 0 && (
            <div className="p-12 bg-white/70 backdrop-blur-md border border-cyan-100/60 rounded-3xl text-center space-y-4 shadow-sm">
              <div className="w-16 h-16 rounded-full bg-cyan-50 border border-cyan-200 flex items-center justify-center mx-auto text-cyan-600">
                <span className="material-symbols-outlined text-[32px]">sailing</span>
              </div>
              <h3 className="text-lg font-bold text-slate-900">Chưa có đơn đặt tour nào</h3>
              <div>
                <button
                  onClick={() => navigate("/")}
                  className="px-6 py-2.5 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-xs font-bold hover:shadow-md cursor-pointer transition-all"
                >
                  Khám phá ngay
                </button>
              </div>
            </div>
          )}

          {/* Orders List */}
          {!loading && !error && filteredOrders.length > 0 && (
            <div className="w-full flex flex-col gap-6">
              {filteredOrders.map((masterOrder) => {
                const subOrders = masterOrder.items || masterOrder.subOrders || [];
                const orderCode = `#ORD-${(masterOrder.id || "").slice(0, 8).toUpperCase()}`;
                const masterStatus = (masterOrder.status || "").toUpperCase();
                const canDownloadPdf = masterStatus === "PAID" || masterStatus === "COMPLETED";
                const canCancelOrder = masterStatus === "PAID";

                return (
                  <article
                    key={masterOrder.id}
                    className="bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-sm border border-slate-200/80 flex flex-col gap-6 transition-all duration-300 hover:shadow-md hover:border-cyan-200"
                  >
                    {/* Master Order Header */}
                    <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-100">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-cyan-50 border border-cyan-200 text-cyan-700 flex items-center justify-center flex-shrink-0">
                          <span className="material-symbols-outlined text-[22px]">receipt_long</span>
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-xs text-slate-500 font-semibold uppercase tracking-wider">Đơn hàng:</span>
                            <span className="text-base font-bold text-slate-900 font-mono">{orderCode}</span>
                            {getStatusBadge(masterOrder.status || "PENDING")}
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5">
                            Ngày tạo: {formatDateTime(masterOrder.createdAt)}
                          </p>
                        </div>
                      </div>

                      <div className="flex flex-col sm:flex-row items-end sm:items-center gap-3">
                        <div className="text-right">
                          <span className="text-xs text-slate-500 uppercase tracking-wider block font-medium">Tổng tiền</span>
                          <span className="text-xl text-cyan-700 font-extrabold">
                            {formatVnd(masterOrder.finalAmount ?? masterOrder.totalAmount)}
                          </span>
                        </div>

                        {/* Master Order Action Buttons */}
                        <div className="flex items-center gap-2">
                          {canDownloadPdf && (
                            <button
                              onClick={() => handleDownloadReceiptPdf(masterOrder.id)}
                              disabled={downloadingPdfId === masterOrder.id}
                              className="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                              title="Tải hóa đơn & Vé điện tử PDF"
                            >
                              <span className="material-symbols-outlined text-[16px]">
                                {downloadingPdfId === masterOrder.id ? "sync" : "picture_as_pdf"}
                              </span>
                              <span>{downloadingPdfId === masterOrder.id ? "Đang xuất..." : "Tải vé PDF"}</span>
                            </button>
                          )}

                          {canCancelOrder && (
                            <button
                              onClick={() => handleOpenCancellationModal(masterOrder)}
                              className="px-3 py-2 rounded-xl border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-bold transition-colors cursor-pointer"
                            >
                              Yêu cầu hủy đơn
                            </button>
                          )}

                          {masterStatus === "PENDING_PAYMENT" && (
                            <button
                              onClick={() => navigate(`/checkout`)}
                              className="px-4 py-2 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white text-xs font-bold shadow-sm cursor-pointer"
                            >
                              Thanh toán ngay
                            </button>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Sub-Orders List */}
                    <div className="flex flex-col gap-3">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-slate-600 uppercase tracking-wider flex items-center gap-1.5">
                          <span className="material-symbols-outlined text-[18px] text-cyan-600">layers</span>
                          Các dịch vụ ({subOrders.length})
                        </span>
                      </div>

                      {subOrders.length === 0 ? (
                        <div className="p-4 rounded-xl bg-slate-50 text-center text-xs text-slate-500">
                          Không có dịch vụ con trong đơn hàng này.
                        </div>
                      ) : (
                        subOrders.map((subOrder) => {
                          const serviceDetail = serviceDetails[subOrder.serviceId];
                          const serviceName = serviceDetail?.name || subOrder.serviceName || `Dịch vụ #${subOrder.serviceId.slice(0, 8)}`;
                          const serviceImage = serviceDetail?.imageUrls?.[0] || subOrder.serviceThumbnail || "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80";
                          const subOrderStatus = (subOrder.status || "").toUpperCase();
                          const isConfirmed = subOrderStatus === "CONFIRMED";
                          const isCheckedIn = subOrderStatus === "CHECKED_IN";
                          const isCompleted = subOrderStatus === "COMPLETED";

                          return (
                            <div
                              key={subOrder.id}
                              className="bg-slate-50/80 rounded-xl p-4 flex flex-col md:flex-row gap-4 border border-slate-200/60 hover:bg-slate-50 transition-colors"
                            >
                              {/* Thumbnail */}
                              <div className="relative w-full md:w-52 h-32 flex-shrink-0 rounded-xl overflow-hidden shadow-sm bg-slate-200">
                                <img
                                  src={serviceImage}
                                  alt={serviceName}
                                  className="w-full h-full object-cover"
                                  onError={(e) => {
                                    (e.target as HTMLImageElement).src = "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80";
                                  }}
                                />
                                {subOrder.slotTime && (
                                  <span className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-slate-900/80 backdrop-blur-sm text-white text-[11px] font-semibold flex items-center gap-1 shadow-sm">
                                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"></span>
                                    {subOrder.slotTime}
                                  </span>
                                )}
                              </div>

                              {/* Details */}
                              <div className="flex flex-col justify-between flex-1 min-w-0">
                                <div>
                                  <div className="flex items-start justify-between gap-2">
                                    <div>
                                      <div className="flex items-center gap-2">
                                        <span className="font-mono font-bold text-xs text-cyan-700">
                                          #{subOrder.id.slice(0, 8).toUpperCase()}
                                        </span>
                                        {subOrder.waiverRequired && (
                                          <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full bg-teal-50 text-teal-700 border border-teal-200 text-[10px] font-semibold">
                                            <span className="material-symbols-outlined text-[12px]">verified</span>
                                            {subOrder.waiverAccepted ? "Đã ký waiver" : "Cần ký waiver"}
                                          </span>
                                        )}
                                      </div>

                                      <h3
                                        className="text-base font-bold text-slate-900 mt-1 hover:text-cyan-600 cursor-pointer transition-colors"
                                        onClick={() => navigate(`/tour/${subOrder.serviceId}`)}
                                      >
                                        {serviceName}
                                      </h3>

                                      {serviceDetail?.address && (
                                        <div className="flex items-center gap-1 mt-1 text-xs text-slate-500">
                                          <span className="material-symbols-outlined text-[14px] text-cyan-600">location_on</span>
                                          <span>{serviceDetail.address}</span>
                                        </div>
                                      )}
                                    </div>

                                    <div className="flex flex-col items-end">
                                      <span className="text-base text-cyan-700 font-bold">
                                        {formatVnd(subOrder.finalAmount ?? subOrder.subtotalAmount)}
                                      </span>
                                      <span className="text-xs text-slate-500">
                                        Số lượng: <strong>{subOrder.quantity} khách</strong>
                                      </span>
                                    </div>
                                  </div>
                                </div>

                                {/* Sub-Order Footer & Actions */}
                                <div className="flex flex-wrap items-center justify-between gap-3 pt-3 mt-3 border-t border-slate-200">
                                  <div className="flex items-center gap-2">
                                    {getStatusBadge(subOrder.status)}
                                    {submittedReviews[subOrder.id] && (
                                      <span className="px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-xs font-medium">
                                        ★ Đã đánh giá ({submittedReviews[subOrder.id].rating} sao)
                                      </span>
                                    )}
                                    {submittedDisputes[subOrder.id] && (
                                      <span className="px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 text-xs font-medium">
                                        Đang khiếu nại
                                      </span>
                                    )}
                                  </div>

                                  <div className="flex items-center gap-2">
                                    {/* Check-in QR button (P0 for CONFIRMED suborders) */}
                                    {isConfirmed && (
                                      <button
                                        onClick={() => handleOpenQrModal(subOrder)}
                                        className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-700 hover:to-blue-700 text-white text-xs font-bold flex items-center gap-1.5 shadow-sm shadow-cyan-500/20 cursor-pointer transition-all active:scale-95"
                                        type="button"
                                      >
                                        <span className="material-symbols-outlined text-[16px]">qr_code_2</span>
                                        <span>Mã QR Check-in</span>
                                      </button>
                                    )}

                                    {/* Checked-in status display */}
                                    {isCheckedIn && (
                                      <span className="px-3 py-1.5 rounded-xl bg-cyan-100/70 text-cyan-800 text-xs font-bold flex items-center gap-1">
                                        <span className="material-symbols-outlined text-[16px] text-cyan-600">done_all</span>
                                        <span>Đã quét vé lên tàu</span>
                                      </span>
                                    )}

                                    {/* Review action for completed items */}
                                    {isCompleted && !submittedReviews[subOrder.id] && (
                                      <button
                                        onClick={() => handleOpenReview(subOrder)}
                                        className="px-3.5 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold flex items-center gap-1 shadow-sm cursor-pointer"
                                        type="button"
                                      >
                                        <span className="material-symbols-outlined text-[15px]">rate_review</span>
                                        <span>Đánh giá</span>
                                      </button>
                                    )}

                                    {/* Support / Dispute action */}
                                    {!submittedDisputes[subOrder.id] && (
                                      <button
                                        onClick={() => handleOpenDispute(subOrder)}
                                        className="px-3 py-1.5 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-700 text-xs font-medium transition-colors cursor-pointer"
                                        type="button"
                                      >
                                        Hỗ trợ / Khiếu nại
                                      </button>
                                    )}
                                  </div>
                                </div>
                              </div>
                            </div>
                          );
                        })
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-8">
              <button
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-slate-700 text-xs font-bold disabled:opacity-40 cursor-pointer"
              >
                Trang trước
              </button>
              <span className="text-xs text-slate-600 font-semibold">
                Trang {page + 1} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-slate-700 text-xs font-bold disabled:opacity-40 cursor-pointer"
              >
                Trang sau
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ============================================================== */}
      {/* Portaled Check-in QR Code Modal (Real Backend HMAC Token)     */}
      {/* ============================================================== */}
      {selectedSubOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-white rounded-3xl p-6 sm:p-8 max-w-sm w-full shadow-2xl relative text-center border border-slate-100">
              <button
                onClick={() => setSelectedSubOrder(null)}
                className="absolute top-4 right-4 p-2 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer transition-colors"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>

              <div className="w-12 h-12 rounded-2xl bg-cyan-50 border border-cyan-200 text-cyan-600 flex items-center justify-center mx-auto mb-3">
                <span className="material-symbols-outlined text-[28px]">qr_code_scanner</span>
              </div>

              <h3 className="text-lg font-bold text-slate-900">
                Vé điện tử Check-in
              </h3>
              <p className="text-xs text-slate-500 mt-1">
                Xuất trình mã này tại quầy vé hoặc cầu tàu trước giờ khởi hành
              </p>

              {/* QR Content Body */}
              <div className="my-5 p-4 rounded-2xl bg-slate-50 border border-slate-200 inline-block">
                {qrLoading && (
                  <div className="w-48 h-48 flex flex-col items-center justify-center space-y-2">
                    <span className="material-symbols-outlined text-cyan-600 text-[36px] animate-spin">
                      progress_activity
                    </span>
                    <span className="text-xs text-slate-500 font-medium">Đang sinh mã QR ký số...</span>
                  </div>
                )}

                {!qrLoading && qrError && (
                  <div className="w-48 h-48 flex flex-col items-center justify-center p-3 text-center space-y-2">
                    <span className="material-symbols-outlined text-rose-500 text-[32px]">warning</span>
                    <span className="text-xs text-rose-700 font-semibold">{qrError}</span>
                  </div>
                )}

                {!qrLoading && !qrError && qrData && (
                  <img
                    src={`https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(
                      qrData.qrToken
                    )}`}
                    alt="QR Check-in"
                    className="w-48 h-48 mx-auto rounded-xl shadow-sm bg-white p-2"
                  />
                )}
              </div>

              {/* Ticket Details */}
              <div className="bg-slate-50 rounded-xl p-3 text-left space-y-1.5 mb-5 border border-slate-200/60 text-xs">
                <div className="flex justify-between">
                  <span className="text-slate-500">Mã vé (Sub-order):</span>
                  <span className="font-mono font-bold text-cyan-700">
                    #{selectedSubOrder.id.slice(0, 8).toUpperCase()}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Số lượng khách:</span>
                  <span className="font-bold text-slate-800">{selectedSubOrder.quantity} khách</span>
                </div>
                {qrData?.expiresAt && (
                  <div className="flex justify-between">
                    <span className="text-slate-500">Hiệu lực đến:</span>
                    <span className="font-semibold text-slate-700">
                      {formatDateTime(qrData.expiresAt)}
                    </span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-slate-500">Cơ chế an toàn:</span>
                  <span className="text-emerald-700 font-bold">HMAC-SHA256 Token</span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setSelectedSubOrder(null)}
                  className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-700 font-bold text-xs hover:bg-slate-100 transition-colors cursor-pointer"
                >
                  Đóng
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {/* ============================================================== */}
      {/* Portaled Cancellation & Refund Preview Modal                   */}
      {/* ============================================================== */}
      {cancellationOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-white rounded-3xl p-6 sm:p-8 max-w-lg w-full shadow-2xl relative border border-slate-100 space-y-4">
              <div className="flex items-center justify-between border-b pb-3">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-rose-500 text-[24px]">cancel</span>
                  <h3 className="text-lg font-bold text-slate-900">Yêu cầu hủy đơn &amp; Hoàn tiền</h3>
                </div>
                <button
                  onClick={() => setCancellationOrder(null)}
                  className="text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  <span className="material-symbols-outlined">close</span>
                </button>
              </div>

              {cancellationLoading && (
                <div className="py-8 flex flex-col items-center justify-center space-y-2">
                  <span className="material-symbols-outlined text-cyan-600 text-[32px] animate-spin">
                    progress_activity
                  </span>
                  <p className="text-xs text-slate-500">Đang tra cứu chính sách hoàn tiền theo thời điểm...</p>
                </div>
              )}

              {cancellationError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-medium">
                  {cancellationError}
                </div>
              )}

              {cancellationSuccess && (
                <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 font-semibold flex items-center gap-2">
                  <span className="material-symbols-outlined text-[18px] text-emerald-600">check_circle</span>
                  <span>{cancellationSuccess}</span>
                </div>
              )}

              {!cancellationLoading && cancellationPreview && !cancellationSuccess && (
                <div className="space-y-4">
                  {/* Policy Preview Card */}
                  <div className="bg-slate-50 border border-slate-200 rounded-2xl p-4 space-y-2 text-xs">
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Chính sách áp dụng:</span>
                      <span className="font-bold text-cyan-700 font-mono">
                        {cancellationPreview.policyApplied || "STANDARD_24H_V2"}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Tổng thanh toán ban đầu:</span>
                      <span className="font-bold text-slate-800">
                        {formatVnd(cancellationPreview.originalAmount)}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Tỷ lệ hoàn tiền:</span>
                      <span className="font-extrabold text-blue-600">
                        {cancellationPreview.refundPercentage}%
                      </span>
                    </div>
                    <div className="flex justify-between items-center pt-2 border-t border-slate-200 text-sm">
                      <span className="font-bold text-slate-900">Dự kiến tiền hoàn về ví/tài khoản:</span>
                      <span className="font-extrabold text-rose-600">
                        {formatVnd(cancellationPreview.refundAmount)}
                      </span>
                    </div>
                  </div>

                  {/* Reason selector */}
                  <div>
                    <label className="text-xs font-bold text-slate-800 block mb-2">Lý do hủy đơn</label>
                    <div className="space-y-2">
                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "CUSTOMER_REQUEST"}
                          onChange={() => setCancellationReason("CUSTOMER_REQUEST")}
                        />
                        <span>Thay đổi lịch trình / Lý do cá nhân</span>
                      </label>

                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "WEATHER_DISRUPTION"}
                          onChange={() => setCancellationReason("WEATHER_DISRUPTION")}
                        />
                        <span>Thời tiết biển xấu / Sự cố bất khả kháng</span>
                      </label>

                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "VENDOR_CANCELLED"}
                          onChange={() => setCancellationReason("VENDOR_CANCELLED")}
                        />
                        <span>Nhà cung cấp yêu cầu dời/hủy ca</span>
                      </label>
                    </div>
                  </div>

                  <p className="text-[11px] text-slate-500">
                    * Lưu ý: Khi xác nhận hủy, các mã vé QR tương ứng sẽ bị vô hiệu hóa ngay lập tức trên hệ thống cảng vụ.
                  </p>

                  <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                    <button
                      type="button"
                      onClick={() => setCancellationOrder(null)}
                      className="px-4 py-2 rounded-xl text-slate-600 hover:bg-slate-100 text-xs font-bold cursor-pointer"
                    >
                      Hủy bỏ
                    </button>
                    <button
                      type="button"
                      disabled={cancellationSubmitting}
                      onClick={handleConfirmCancellation}
                      className="px-5 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-sm cursor-pointer disabled:opacity-50"
                    >
                      {cancellationSubmitting ? "Đang xử lý..." : "Xác nhận hủy & Hoàn tiền"}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>,
          document.body
        )}

      {/* ============================================================== */}
      {/* Portaled Review & Rating Modal                                 */}
      {/* ============================================================== */}
      {reviewingSubOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-white rounded-3xl p-6 max-w-md w-full shadow-2xl relative space-y-4 border border-slate-100">
              <div className="flex items-center justify-between border-b pb-3">
                <h3 className="text-base font-bold text-slate-900">Đánh giá trải nghiệm</h3>
                <button
                  onClick={() => setReviewingSubOrder(null)}
                  className="text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  <span className="material-symbols-outlined">close</span>
                </button>
              </div>

              <div className="space-y-1">
                <span className="text-xs text-slate-500 font-mono">
                  Mã vé: #{reviewingSubOrder.id.slice(0, 8).toUpperCase()}
                </span>
                <p className="text-sm font-bold text-slate-800">
                  {serviceDetails[reviewingSubOrder.serviceId]?.name || "Dịch vụ du lịch biển"}
                </p>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1.5">Mức độ hài lòng</label>
                <div className="flex items-center gap-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      key={star}
                      type="button"
                      onClick={() => setRatingStars(star)}
                      className={`text-2xl cursor-pointer transition-transform hover:scale-110 ${
                        star <= ratingStars ? "text-amber-400" : "text-slate-300"
                      }`}
                    >
                      ★
                    </button>
                  ))}
                  <span className="text-xs font-bold text-slate-600 ml-2">
                    {ratingStars === 5
                      ? "Tuyệt vời"
                      : ratingStars === 4
                      ? "Tốt"
                      : ratingStars === 3
                      ? "Bình thường"
                      : "Cần cải thiện"}
                  </span>
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1.5">Nhận xét chi tiết</label>
                <textarea
                  rows={4}
                  value={reviewComment}
                  onChange={(e) => setReviewComment(e.target.value)}
                  placeholder="Chia sẻ cảm nhận về hướng dẫn viên, trang thiết bị an toàn, trải nghiệm..."
                  className="w-full p-3 rounded-xl border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-cyan-600 resize-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setReviewingSubOrder(null)}
                  className="px-4 py-2 rounded-xl text-slate-600 hover:bg-slate-100 text-xs font-semibold cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  onClick={handleSubmitReview}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-xs font-bold hover:shadow-sm cursor-pointer"
                >
                  Gửi đánh giá
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {/* ============================================================== */}
      {/* Portaled Dispute Modal                                         */}
      {/* ============================================================== */}
      {disputingSubOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-white rounded-3xl p-6 max-w-md w-full shadow-2xl relative space-y-4 border border-slate-100">
              <div className="flex items-center justify-between border-b pb-3">
                <h3 className="text-base font-bold text-slate-900">Gửi khiếu nại &amp; Hỗ trợ dịch vụ</h3>
                <button
                  onClick={() => setDisputingSubOrder(null)}
                  className="text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  <span className="material-symbols-outlined">close</span>
                </button>
              </div>

              <div className="space-y-1">
                <span className="text-xs text-slate-500 font-mono">
                  Mã vé: #{disputingSubOrder.id.slice(0, 8).toUpperCase()}
                </span>
                <p className="text-sm font-bold text-slate-800">
                  {serviceDetails[disputingSubOrder.serviceId]?.name || "Dịch vụ du lịch biển"}
                </p>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1.5">Lý do khiếu nại</label>
                <textarea
                  rows={4}
                  value={disputeReason}
                  onChange={(e) => setDisputeReason(e.target.value)}
                  placeholder="Mô tả sự cố bạn gặp phải (ví dụ: nhà cung cấp trễ ca không báo trước, trang thiết bị không đảm bảo an toàn...)"
                  className="w-full p-3 rounded-xl border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 resize-none"
                />
              </div>

              <p className="text-[11px] text-slate-500">
                Đội ngũ điều phối và hỗ trợ khách hàng DANASEA sẽ tiếp nhận và phản hồi trong vòng 24 giờ.
              </p>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setDisputingSubOrder(null)}
                  className="px-4 py-2 rounded-xl text-slate-600 hover:bg-slate-100 text-xs font-semibold cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={!disputeReason.trim()}
                  onClick={handleSubmitDispute}
                  className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold cursor-pointer disabled:opacity-50"
                >
                  Gửi khiếu nại
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </main>
  );
}
