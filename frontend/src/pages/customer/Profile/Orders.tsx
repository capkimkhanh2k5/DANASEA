import { useState, useMemo, useEffect, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Lock,
  Compass,
  Receipt,
  FileText,
  Layers,
  MapPin,
  QrCode,
  Ban,
  CheckCheck,
  Star,
  Calendar,
  Clock,
  CheckCircle2,
  XCircle,
  X,
  Send,
  AlertCircle,
  AlertTriangle,
  Edit3,
  Copy,
  Check,
  Info,
  Loader2,
  ArrowLeft,
} from "lucide-react";
import { orderService } from "../../../services/orderService";
import { ContinuePaymentDialog } from "./ContinuePaymentDialog";
import { reviewService, type ReviewResponse } from "../../../services/reviewService";
import { serviceCatalogService } from "../../../services/serviceCatalogService";
import { disputeService } from "../../../services/disputeService";
import { notificationService } from "../../../services/notificationService";
import type { DisputeReason, DisputeResponse } from "../../../types/dispute";
import { useAuthStore } from "../../../store/useAuthStore";
import { authService } from "../../../services/authService";
import { useLanguageStore } from "../../../store/useLanguageStore";
import { toast } from "../../../store/useToastStore";
import type {
  OrderResponse,
  SubOrderResponse,
  CancellationPreviewResponse,
  RefundResponse,
  QrCodeResponse,
  RescheduleOptionsSummaryResponse,
  RescheduleOptionItem,
} from "../../../types/order";
import type { ServiceDetailDto } from "../../../types/service";

type OrderFilter = "all" | "upcoming" | "pending" | "completed" | "cancelled";

export function Orders() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user, isAuthenticated, accessToken, setAuth } = useAuthStore();
  const { language } = useLanguageStore();
  const isEn = language === 'en';

  // State management
  const [filter, setFilter] = useState<OrderFilter>("all");
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [orderRefunds, setOrderRefunds] = useState<Record<string, RefundResponse[]>>({});
  const [loadingState, setLoading] = useState<boolean>(true);
  const loading = isAuthenticated && Boolean(accessToken) && loadingState;
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [previousRequest, setPreviousRequest] = useState({ page, accessToken });
  if (previousRequest.page !== page || previousRequest.accessToken !== accessToken) {
    setPreviousRequest({ page, accessToken });
    setLoading(true);
  }

  const [serviceDetails, setServiceDetails] = useState<Record<string, ServiceDetailDto>>({});

  const [paymentOrderId, setPaymentOrderId] = useState<string | null>(null);

  const [paymentClock, setPaymentClock] = useState(Date.now);
  const isOrderPaymentExpired = (order: OrderResponse) =>
    order.status === "PENDING_PAYMENT" && Date.parse(order.paymentDeadline ?? "") <= paymentClock;

  useEffect(() => {
    const nextDeadline = Math.min(...orders
      .filter((order) => order.status === "PENDING_PAYMENT")
      .map((order) => Date.parse(order.paymentDeadline ?? ""))
      .filter((deadline) => deadline > paymentClock));
    if (!Number.isFinite(nextDeadline)) return;
    const timer = window.setTimeout(() => setPaymentClock(Date.now()),
      Math.min(2_147_483_647, Math.max(0, nextDeadline - Date.now())));
    return () => window.clearTimeout(timer);
  }, [orders, paymentClock]);

  // Reschedule state
  const [rescheduleMap, setRescheduleMap] = useState<Record<string, RescheduleOptionsSummaryResponse>>({});
  const [confirmingRescheduleId, setConfirmingRescheduleId] = useState<string | null>(null);

  // QR Modal state
  const [selectedSubOrder, setSelectedSubOrder] = useState<SubOrderResponse | null>(null);
  const [selectedMasterOrder, setSelectedMasterOrder] = useState<OrderResponse | null>(null);
  const [qrData, setQrData] = useState<QrCodeResponse | null>(null);
  const [qrLoading, setQrLoading] = useState<boolean>(false);
  const [qrError, setQrError] = useState<string | null>(null);
  const [copiedToken, setCopiedToken] = useState<boolean>(false);

  // Cancellation Preview Modal state
  const [cancellationOrder, setCancellationOrder] = useState<OrderResponse | null>(null);
  const [cancellationPreview, setCancellationPreview] = useState<CancellationPreviewResponse | null>(null);
  const [cancellationLoading, setCancellationLoading] = useState<boolean>(false);
  const [cancellationSubmitting, setCancellationSubmitting] = useState<boolean>(false);
  const [cancellationReason, setCancellationReason] = useState<string>("PERSONAL");
  const [cancellationError, setCancellationError] = useState<string | null>(null);
  const [cancellationSuccess, setCancellationSuccess] = useState<string | null>(null);

  // PDF Receipt state
  const [downloadingPdfId, setDownloadingPdfId] = useState<string | null>(null);

  // Refund Details Modal state
  const [viewingRefundOrder, setViewingRefundOrder] = useState<{
    order: OrderResponse;
    refunds: RefundResponse[];
  } | null>(null);

  // Review state
  const [reviewingSubOrder, setReviewingSubOrder] = useState<SubOrderResponse | null>(null);
  const [ratingStars, setRatingStars] = useState(5);
  const [reviewComment, setReviewComment] = useState("");
  const [reviewImagesInput, setReviewImagesInput] = useState("");
  const [isSubmittingReview, setIsSubmittingReview] = useState(false);
  const [reviewsMap, setReviewsMap] = useState<Record<string, ReviewResponse>>(() => {
    try {
      const saved = localStorage.getItem("danasea_customer_reviews");
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  // Dispute Modal state
  const [disputingSubOrder, setDisputingSubOrder] = useState<SubOrderResponse | null>(null);
  const [disputeReasonType, setDisputeReasonType] = useState<DisputeReason>("SERVICE_NOT_AS_DESCRIBED");
  const [disputeReason, setDisputeReason] = useState("");
  const [isSubmittingDispute, setIsSubmittingDispute] = useState(false);
  const [submittedDisputes, setSubmittedDisputes] = useState<Record<string, DisputeResponse>>({});

  const serviceDetailsCache = useRef<Record<string, ServiceDetailDto>>({});

  // Fetch orders from API
  const fetchOrders = useCallback((targetPage = 0) => {
    if (!isAuthenticated || !accessToken) return;

    return orderService.getMyOrders(targetPage, 20).then((res) => {
      setError(null);
      setOrders(res.content || []);
      setPage(res.page || 0);
      setTotalPages(res.totalPages || 1);

      // Extract unique service IDs to fetch service details and fetch refund history
      const serviceIds = new Set<string>();
      const activeSubOrderIds = new Set<string>();
      (res.content || []).forEach((ord) => {
        // isPaidMaster verified
        const items = ord.items || ord.subOrders || [];
        items.forEach((item) => {
          if (item.serviceId) serviceIds.add(item.serviceId);
          if (item.id && (item.status === 'CONFIRMED' || item.status === 'PENDING')) {
            activeSubOrderIds.add(item.id);
          }
        });
      });

      // Targeted: Load reschedule options ONLY for active sub-orders with proposals or URL params
      const paramSubId = searchParams.get('rescheduleSubOrderId');
      if (paramSubId && activeSubOrderIds.has(paramSubId)) {
        orderService.getRescheduleOptions(paramSubId, true).then((summary) => {
          if (summary && summary.options && summary.options.length > 0) {
            setRescheduleMap((prev) => ({ ...prev, [paramSubId]: summary }));
          }
        }).catch(() => {});
      }

      notificationService.getMyNotifications(0, 50).then((notifRes) => {
        if (notifRes && notifRes.content) {
          const proposalSubIds = new Set(
            notifRes.content
              .filter(
                (n) =>
                  (n.type === 'RESCHEDULE_PROPOSAL' || n.type === 'DISRUPTION_DECISION') &&
                  n.relatedEntityType === 'SUB_ORDER' &&
                  n.relatedEntityId &&
                  activeSubOrderIds.has(n.relatedEntityId)
              )
              .map((n) => n.relatedEntityId as string)
          );

          proposalSubIds.forEach((subId) => {
            orderService.getRescheduleOptions(subId, true).then((summary) => {
              if (summary && summary.options && summary.options.length > 0) {
                setRescheduleMap((prev) => ({ ...prev, [subId]: summary }));
              }
            }).catch(() => {});
          });
        }
      }).catch(() => {});

      (res.content || []).forEach((ord) => {

        // Query refund history for each order
        orderService.getOrderRefunds(ord.id).then((refunds) => {
          if (refunds && refunds.length > 0) {
            setOrderRefunds((prev) => ({ ...prev, [ord.id]: refunds }));
          }
        }).catch(() => {
          // Non-critical, ignore
        });
      });

      // Load existing customer disputes from backend
      disputeService.getCustomerDisputes(0, 50).then((pageRes) => {
        if (pageRes && pageRes.content) {
          const map: Record<string, DisputeResponse> = {};
          pageRes.content.forEach((d) => {
            if (d.subOrderId) map[d.subOrderId] = d;
          });
          setSubmittedDisputes((prev) => ({ ...prev, ...map }));
        }
      }).catch(() => {
        // Non-critical, ignore
      });

      // Load public services catalog in one batch to enrich order card titles and thumbnails
      serviceCatalogService.getServices({ size: 100 }).then((catalogRes) => {
        const list = catalogRes.content || [];
        const detailsMap: Record<string, ServiceDetailDto> = {};
        list.forEach((s) => {
          if (s.id) {
            const item: ServiceDetailDto = {
              id: s.id,
              name: s.name,
              description: s.shortDescription || '',
              price: s.price,
              promotionalPrice: s.promotionalPrice,
              address: s.address || '',
              averageRating: s.averageRating || 5,
              reviewCount: s.reviewCount || 0,
              viewCount: s.viewCount || 0,
              categoryId: '',
              imageUrls: s.primaryImageUrl ? [s.primaryImageUrl] : [],
              options: [],
              vendorId: '',
              waiverRequired: false,
              slots: [],
            };
            serviceDetailsCache.current[s.id] = item;
            detailsMap[s.id] = item;
          }
        });
        setServiceDetails((prev) => ({ ...prev, ...detailsMap }));
      }).catch(() => {
        // Non-critical: subOrder defaults will be used
      });
    }).catch((err: unknown) => {
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
    }).finally(() => {
      setLoading(false);
    });
  }, [isAuthenticated, accessToken, searchParams]);

  useEffect(() => {
    if (isAuthenticated && accessToken) {
      fetchOrders(page);
    }
  }, [page, isAuthenticated, accessToken, fetchOrders]);

  // Quick Login helper for testing / unauthenticated state
  const handleQuickLogin = async () => {
    try {
      setLoading(true);
      const res = await authService.login({
        email: "customer_1@danasea.test",
        password: "password123",
      });
      setAuth(
        {
          id: res.userId,
          email: res.email,
          fullName: "Nguyễn Hoàng Long",
          role: res.role,
          avatarUrl: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
          isEmailVerified: true,
          isLocked: false,
          passwordHash: "",
          phone: "0912345678",
          locale: "vi",
        },
        res.accessToken
      );
    } catch (err) {
      console.warn("[Orders] Đăng nhập nhanh thất bại qua API, dùng switchRole:", err);
      useAuthStore.getState().switchRole('customer');
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

      if (filter === "pending") {
        return masterStatus === "PENDING_PAYMENT";
      }

      if (items.length > 0) {
        if (filter === "upcoming") {
          return items.some((i) => {
            const s = (i.status || "").toUpperCase();
            return s === "CONFIRMED" || s === "PENDING";
          });
        }

        if (filter === "completed") {
          return items.some((i) => {
            const s = (i.status || "").toUpperCase();
            return s === "COMPLETED" || s === "CHECKED_IN";
          });
        }

        if (filter === "cancelled") {
          return items.some((i) => {
            const s = (i.status || "").toUpperCase();
            return s === "CANCELLED" || s === "REFUNDED" || s === "PARTIALLY_REFUNDED";
          });
        }
      }

      // Fallback if items array is empty
      if (filter === "upcoming") return masterStatus === "PAID";
      if (filter === "completed") return masterStatus === "COMPLETED" || masterStatus === "PARTIALLY_COMPLETED";
      if (filter === "cancelled") return masterStatus === "CANCELLED";

      return true;
    });
  }, [orders, filter]);

  // Confirm reschedule proposal handler
  const handleConfirmReschedule = async (
    subOrderId: string,
    vendorOption: RescheduleOptionItem,
    version: number
  ) => {
    setConfirmingRescheduleId(subOrderId);
    try {
      await orderService.confirmReschedule(subOrderId, {
        targetSlotId: vendorOption.slotId,
        proposalId: vendorOption.proposalId,
        expectedVersion: version,
      });
      toast.success("Đã đồng ý chuyển sang ca khởi hành mới thành công!");
      setRescheduleMap((prev) => {
        const next = { ...prev };
        delete next[subOrderId];
        return next;
      });
      fetchOrders(page);
    } catch (err: unknown) {
      let msg = "Không thể xác nhận đổi ca.";
      if (err && typeof err === "object" && "response" in err) {
        const resp = (err as { response?: { data?: { message?: string } } }).response;
        if (resp?.data?.message) msg = resp.data.message;
      } else if (err instanceof Error) {
        msg = err.message;
      }
      toast.error("Lỗi: " + msg);
    } finally {
      setConfirmingRescheduleId(null);
    }
  };

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

  // Master Order QR Generation handler (All-in-One Pass)
  const handleOpenMasterQrModal = async (order: OrderResponse) => {
    setSelectedMasterOrder(order);
    setSelectedSubOrder(null);
    setQrLoading(true);
    setQrError(null);
    setQrData(null);

    try {
      const data = await orderService.getCheckinQrCode(order.id);
      setQrData(data);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi khi tạo mã QR đơn hàng:", err);
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
      await orderService.requestRefund(cancellationOrder.id, "CUSTOMER_REQUEST");
      setCancellationSuccess("Yêu cầu hủy đơn và hoàn tiền đã được gửi thành công!");
      try {
        const refunds = await orderService.getOrderRefunds(cancellationOrder.id);
        if (refunds && refunds.length > 0) {
          setOrderRefunds((prev) => ({ ...prev, [cancellationOrder.id]: refunds }));
        }
      } catch {
        // ignore
      }
      setTimeout(() => {
        setCancellationOrder(null);
        setLoading(true);
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

  // Review handlers
  const handleOpenReview = (subOrder: SubOrderResponse) => {
    setReviewingSubOrder(subOrder);
    const existing = reviewsMap[subOrder.id];
    if (existing) {
      setRatingStars(existing.rating || 5);
      setReviewComment(existing.comment || "");
      setReviewImagesInput(existing.images ? existing.images.join("\n") : "");
    } else {
      setRatingStars(5);
      setReviewComment("");
      setReviewImagesInput("");
    }
  };

  const handleSubmitReview = async () => {
    if (!reviewingSubOrder) return;
    if (!reviewComment.trim()) {
      toast.error("Vui lòng nhập nội dung nhận xét chi tiết.");
      return;
    }

    const existingReview = reviewsMap[reviewingSubOrder.id];
    const images = reviewImagesInput
      .split("\n")
      .map((s) => s.trim())
      .filter((s) => s.startsWith("http"));

    try {
      setIsSubmittingReview(true);
      let result: ReviewResponse;
      if (existingReview?.id && !existingReview.id.startsWith("rev-")) {
        result = await reviewService.updateReviewBySubOrder(reviewingSubOrder.id, {
          rating: ratingStars,
          comment: reviewComment.trim(),
          images: images.length > 0 ? images : undefined,
        });
        toast.success("Cập nhật đánh giá thành công!");
      } else {
        result = await reviewService.createReview(reviewingSubOrder.id, {
          rating: ratingStars,
          comment: reviewComment.trim(),
          images: images.length > 0 ? images : undefined,
        });
        toast.success("Gửi đánh giá trải nghiệm thành công!");
      }
      setReviewsMap((prev) => {
        const next = { ...prev, [reviewingSubOrder.id]: result };
        try {
          localStorage.setItem("danasea_customer_reviews", JSON.stringify(next));
        } catch {
          // Storage can be unavailable; keep the review in component state.
        }
        return next;
      });
      setReviewingSubOrder(null);
    } catch (err: unknown) {
      console.warn("[Orders] Gửi review lên máy chủ thất bại, lưu trữ cục bộ:", err);
      const fallbackReview: ReviewResponse = {
        id: existingReview?.id || `rev-${Date.now()}`,
        subOrderId: reviewingSubOrder.id,
        customerId: user?.id || "cust-1",
        customerName: user?.fullName || "Nguyễn Văn Khách",
        serviceId: reviewingSubOrder.serviceId,
        rating: ratingStars,
        comment: reviewComment.trim(),
        images: images.length > 0 ? images : undefined,
        createdAt: existingReview?.createdAt || new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      setReviewsMap((prev) => {
        const next = { ...prev, [reviewingSubOrder.id]: fallbackReview };
        try {
          localStorage.setItem("danasea_customer_reviews", JSON.stringify(next));
        } catch {
          // Storage can be unavailable; keep the review in component state.
        }
        return next;
      });
      toast.success("Đã ghi nhận đánh giá trải nghiệm!");
      setReviewingSubOrder(null);
    } finally {
      setIsSubmittingReview(false);
    }
  };

  // Dispute handler
  const handleOpenDispute = (subOrder: SubOrderResponse) => {
    setDisputingSubOrder(subOrder);
    setDisputeReason("");
    setDisputeReasonType("SERVICE_NOT_AS_DESCRIBED");
  };

  const handleSubmitDispute = async () => {
    if (!disputingSubOrder || !disputeReason.trim()) return;

    const parentOrder = orders.find((o) =>
      (o.items || o.subOrders || []).some((sub) => sub.id === disputingSubOrder.id)
    );
    if (!parentOrder) {
      toast.error("Không tìm thấy thông tin đơn hàng gốc.");
      return;
    }

    try {
      setIsSubmittingDispute(true);
      const createdDispute = await disputeService.createDispute(parentOrder.id, {
        subOrderId: disputingSubOrder.id,
        reason: disputeReasonType,
        description: disputeReason.trim(),
      });
      toast.success("Đã gửi khiếu nại thành công! Đội ngũ điều phối sẽ xử lý trong 24h.");
      setSubmittedDisputes((prev) => ({
        ...prev,
        [disputingSubOrder.id]: createdDispute || {
          id: "",
          orderId: parentOrder.id,
          subOrderId: disputingSubOrder.id,
          customerId: parentOrder.customerId || "",
          reason: disputeReasonType,
          description: disputeReason.trim(),
          status: "OPEN",
          createdAt: new Date().toISOString(),
        },
      }));
      setDisputingSubOrder(null);
    } catch (err: unknown) {
      console.error("[Orders] Lỗi gửi khiếu nại:", err);
      let msg = "Không thể gửi khiếu nại.";
      if (err && typeof err === "object" && "response" in err) {
        const resp = (err as { response?: { data?: { message?: string; code?: string } } }).response;
        const code = resp?.data?.code;
        const rawMsg = resp?.data?.message || "";
        if (code === "ACTIVE_DISPUTE_ALREADY_EXISTS" || rawMsg.includes("already exists")) {
          msg = "Đơn này đã có khiếu nại đang được xử lý.";
        } else if (code === "INVALID_SUB_ORDER_STATE" || rawMsg.includes("đánh giá") || rawMsg.includes("started")) {
          msg = "Chuyến đi chưa đến giờ khởi hành hoặc trạng thái đơn chưa hợp lệ để mở khiếu nại.";
        } else if (rawMsg) {
          msg = rawMsg;
        }
      } else if (err instanceof Error) {
        msg = err.message;
      }
      toast.error("Lỗi: " + msg);
    } finally {
      setIsSubmittingDispute(false);
    }
  };

  // Helper formatting functions
  const formatVnd = (amount?: number) => {
    return (amount ?? 0).toLocaleString("vi-VN") + "đ";
  };

  const formatPolicyDescription = (rawPolicy?: string | null, percentage?: number) => {
    if (!rawPolicy) return isEn ? "Standard 24-hour cancellation policy" : "Chính sách tiêu chuẩn 24 giờ";
    const lower = rawPolicy.toLowerCase();
    if (lower.includes("within 24 hours") || lower.includes("no refund") || percentage === 0) {
      return isEn ? "No refund for cancellation within 24 hours before departure" : "Không hoàn tiền do hủy trong vòng 24h trước giờ khởi hành";
    }
    if (lower.includes("more than 24 hours") || lower.includes("100% refund") || percentage === 100) {
      return isEn ? "100% refund for cancellation more than 24 hours before departure" : "Hoàn tiền 100% khi hủy trước giờ khởi hành trên 24 giờ";
    }
    if (lower.includes("weather") || lower.includes("disruption")) {
      return isEn ? "100% refund due to severe ocean weather conditions" : "Hoàn tiền 100% do thời tiết biển bất khả kháng";
    }
    if (lower.includes("vendor") || lower.includes("operator")) {
      return isEn ? "100% refund due to partner departure cancellation" : "Hoàn tiền 100% do đối tác hủy ca hoạt động";
    }
    return rawPolicy;
  };

  const formatDateTime = (dateStr?: string) => {
    if (!dateStr) return "N/A";
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString(isEn ? "en-US" : "vi-VN", {
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

  const getMasterStatusBadge = (status: string) => {
    const s = (status || "").toUpperCase();
    switch (s) {
      case "PAID":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">{isEn ? "PAID" : "ĐÃ THANH TOÁN"}</span>;
      case "PENDING_PAYMENT":
      case "UNPAID":
        return <span className="px-2.5 py-0.5 rounded-md bg-amber-50 text-amber-900 border border-amber-200 text-xs font-semibold tracking-tight">{isEn ? "PENDING PAYMENT" : "CHỜ THANH TOÁN"}</span>;
      case "CONFIRMED":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">{isEn ? "CONFIRMED" : "ĐÃ XÁC NHẬN"}</span>;
      case "CHECKED_IN":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">{isEn ? "CHECKED IN" : "ĐÃ CHECK-IN"}</span>;
      case "PARTIALLY_COMPLETED":
        return <span className="px-2.5 py-0.5 rounded-md bg-sky-50 text-sky-800 border border-sky-200 text-xs font-semibold tracking-tight">{isEn ? "PARTIALLY COMPLETED" : "HOÀN TẤT MỘT PHẦN"}</span>;
      case "COMPLETED":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">{isEn ? "COMPLETED" : "HOÀN TẤT"}</span>;
      case "CANCELLED":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-500 border border-slate-200 text-xs font-semibold tracking-tight">{isEn ? "CANCELLED" : "ĐÃ HỦY"}</span>;
      case "REFUNDED":
      case "PARTIALLY_REFUNDED":
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-700 border border-slate-200 text-xs font-semibold tracking-tight">{isEn ? "REFUNDED" : "ĐÃ HOÀN TIỀN"}</span>;
      default:
        return <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-700 border border-slate-200 text-xs font-semibold tracking-tight">{s}</span>;
    }
  };

  const getSubOrderStatusBadge = (subStatus: string, masterStatus: string) => {
    const s = (subStatus || "").toUpperCase();
    const m = (masterStatus || "").toUpperCase();

    // If master order is unpaid, sub-orders are also waiting for payment
    if (m === "PENDING_PAYMENT" || m === "UNPAID") {
      return (
        <span className="px-2.5 py-0.5 rounded-md bg-amber-50 text-amber-900 border border-amber-200 text-xs font-semibold tracking-tight">
          {isEn ? "PENDING PAYMENT" : "CHỜ THANH TOÁN"}
        </span>
      );
    }

    // If master order is already paid, PENDING means waiting for operator confirmation/dispatch
    switch (s) {
      case "PENDING":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-sky-50 text-sky-800 border border-sky-200 text-xs font-semibold tracking-tight">
            {isEn ? "PENDING ACCEPTANCE" : "CHỜ TIẾP NHẬN"}
          </span>
        );
      case "CONFIRMED":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">
            {isEn ? "CONFIRMED" : "ĐÃ XÁC NHẬN"}
          </span>
        );
      case "CHECKED_IN":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">
            {isEn ? "CHECKED IN" : "ĐÃ CHECK-IN"}
          </span>
        );
      case "IN_PROGRESS":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-semibold tracking-tight">
            {isEn ? "IN PROGRESS" : "ĐANG DIỄN RA"}
          </span>
        );
      case "COMPLETED":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 text-xs font-semibold tracking-tight">
            {isEn ? "COMPLETED" : "HOÀN TẤT"}
          </span>
        );
      case "CANCELLED":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-500 border border-slate-200 text-xs font-semibold tracking-tight">
            {isEn ? "CANCELLED" : "ĐÃ HỦY"}
          </span>
        );
      case "REFUNDED":
      case "PARTIALLY_REFUNDED":
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-700 border border-slate-200 text-xs font-semibold tracking-tight">
            {isEn ? "REFUNDED" : "ĐÃ HOÀN TIỀN"}
          </span>
        );
      default:
        return (
          <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-700 border border-slate-200 text-xs font-semibold tracking-tight">
            {s}
          </span>
        );
    }
  };



  // Unauthenticated Guard Screen
  if (!isAuthenticated || !user) {
    return (
      <main className="w-full min-h-[calc(100vh-80px)] bg-slate-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white border border-slate-200 rounded-2xl p-8 text-center space-y-6">
          <div className="w-16 h-16 rounded-2xl bg-sky-50 border border-sky-200 flex items-center justify-center mx-auto text-sky-700">
            <Lock className="w-8 h-8" />
          </div>
          <div className="space-y-2">
            <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 tracking-tight">
              {isEn ? "Please Sign In" : "Vui lòng đăng nhập tài khoản"}
            </h1>
            <p className="text-base text-slate-700 max-w-sm mx-auto leading-relaxed">
              {isEn
                ? "Please sign in with your customer account to view e-tickets, check-in QR codes, and submit service reviews."
                : "Bạn cần đăng nhập tài khoản khách hàng để xem vé điện tử, mã QR check-in và gửi đánh giá dịch vụ biển."}
            </p>
          </div>
          <div className="space-y-3 pt-2">
            <button
              onClick={() => navigate("/auth")}
              className="w-full py-3 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-base font-bold cursor-pointer transition-colors"
            >
              {isEn ? "Go to Sign In" : "Đến trang Đăng nhập"}
            </button>
            <button
              onClick={handleQuickLogin}
              className="w-full py-2.5 rounded-xl border border-slate-300 bg-slate-50 hover:bg-slate-100 text-slate-800 text-sm font-semibold cursor-pointer transition-colors"
            >
              {isEn ? "Quick Sign In (customer_1@danasea.test)" : "Đăng nhập nhanh (customer_1@danasea.test)"}
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-20 bg-slate-50/50 flex-1 min-h-screen">
      <div className="flex flex-col w-full">
        {/* Top Header & Filter Tabs Bar */}
        <div className="relative w-full border-b border-slate-200 bg-white">
          <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-8 lg:px-12 pt-6 pb-4">
            {/* Navigation Breadcrumb */}
            <div className="flex items-center gap-2 mb-4 text-[13px] text-slate-500 font-medium">
              <button 
                onClick={() => navigate('/profile')} 
                className="hover:text-slate-900 transition-colors cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1 text-slate-600 font-semibold"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>{isEn ? "Your Account" : "Tài khoản của bạn"}</span>
              </button>
              <span className="text-slate-300">/</span>
              <span className="text-slate-900 font-semibold">
                {isEn ? "Booking History & QR Tickets" : "Lịch sử đặt tour & Vé QR"}
              </span>
            </div>

            <div className="mb-4">
              <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
                {isEn ? "Manage Tickets & Bookings" : "Quản lý vé & Đơn trải nghiệm"}
              </h1>
            </div>

            {/* Airbnb Signature Underline Tabs */}
            <div className="flex items-center gap-6 sm:gap-8 border-b border-slate-200 overflow-x-auto no-scrollbar pt-2">
              <button
                onClick={() => setFilter("all")}
                className={`pb-3.5 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
                  filter === "all"
                    ? "text-[#0284c7] font-bold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                type="button"
              >
                <span>{isEn ? "All Bookings" : "Tất cả đơn hàng"}</span>
                {filter === "all" && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0284c7] rounded-full" />
                )}
              </button>

              <button
                onClick={() => setFilter("upcoming")}
                className={`pb-3.5 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
                  filter === "upcoming"
                    ? "text-[#0284c7] font-bold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                type="button"
              >
                <span>{isEn ? "Upcoming" : "Sắp diễn ra"}</span>
                {filter === "upcoming" && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0284c7] rounded-full" />
                )}
              </button>

              <button
                onClick={() => setFilter("pending")}
                className={`pb-3.5 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
                  filter === "pending"
                    ? "text-[#0284c7] font-bold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                type="button"
              >
                <span>{isEn ? "Pending Payment" : "Chờ thanh toán"}</span>
                {filter === "pending" && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0284c7] rounded-full" />
                )}
              </button>

              <button
                onClick={() => setFilter("completed")}
                className={`pb-3.5 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
                  filter === "completed"
                    ? "text-[#0284c7] font-bold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                type="button"
              >
                <span>{isEn ? "Completed" : "Đã hoàn thành"}</span>
                {filter === "completed" && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0284c7] rounded-full" />
                )}
              </button>

              <button
                onClick={() => setFilter("cancelled")}
                className={`pb-3.5 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
                  filter === "cancelled"
                    ? "text-[#0284c7] font-bold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                type="button"
              >
                <span>{isEn ? "Cancelled & Refunded" : "Đã hủy & Hoàn tiền"}</span>
                {filter === "cancelled" && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0284c7] rounded-full" />
                )}
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
                <div key={i} className="bg-white rounded-2xl p-6 border border-slate-200/80 animate-pulse space-y-4">
                  <div className="h-6 bg-slate-200 rounded w-1/3"></div>
                  <div className="h-24 bg-slate-100 rounded-xl"></div>
                </div>
              ))}
            </div>
          )}

          {/* Error Alert */}
          {!loading && error && (
            <div className="p-6 bg-white border border-rose-200 rounded-2xl text-center space-y-3 shadow-xs max-w-lg mx-auto">
              <div className="w-12 h-12 rounded-xl bg-rose-50 text-rose-500 flex items-center justify-center mx-auto">
                <AlertCircle className="w-6 h-6 text-rose-600" />
              </div>
              <p className="text-sm font-bold text-slate-800">{error}</p>
              <div className="flex items-center justify-center gap-2 pt-2">
                {error.includes("hết hạn") || error.includes("đăng nhập") ? (
                  <button
                    onClick={() => navigate("/auth")}
                    className="px-5 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold cursor-pointer shadow-xs"
                  >
                    Đăng nhập lại
                  </button>
                ) : (
                  <button
                    onClick={() => { setLoading(true); fetchOrders(page); }}
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
            <div className="p-12 bg-white/90 backdrop-blur-md border border-cyan-100/80 rounded-2xl text-center space-y-4 shadow-xs">
              <div className="w-14 h-14 rounded-2xl bg-cyan-50 border border-cyan-200 flex items-center justify-center mx-auto text-cyan-700">
                <Compass className="w-7 h-7" />
              </div>
              <h3 className="text-lg font-bold text-slate-900">
                {isEn ? "No bookings found" : "Chưa có đơn đặt tour nào"}
              </h3>
              <div>
                <button
                  onClick={() => navigate("/")}
                  className="px-6 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold shadow-xs cursor-pointer transition-all"
                >
                  {isEn ? "Explore Experiences Now" : "Khám phá ngay"}
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
                const allSubCancelled = subOrders.length > 0 && subOrders.every((s) => {
                  const st = (s.status || "").toUpperCase();
                  return st === "CANCELLED" || st === "REFUNDED";
                });
                const masterStatus = allSubCancelled ? "CANCELLED" : (masterOrder.status || "").toUpperCase();
                const canDownloadPdf = masterStatus === "PAID" || masterStatus === "COMPLETED";
                const refunds = orderRefunds[masterOrder.id] || [];
                const hasRefund = refunds.length > 0;
                const latestRefund = hasRefund ? refunds[0] : null;
                const isAnyCheckedInOrDone = subOrders.some((s) => {
                  const st = (s.status || "").toUpperCase();
                  return st === "CHECKED_IN" || st === "COMPLETED";
                });
                const canCancelOrder = masterStatus === "PAID" && !hasRefund && !isAnyCheckedInOrDone;

                const cancelledCount = subOrders.filter(
                  (s) => s.status === "CANCELLED" || s.status === "REFUNDED" || s.status === "PARTIALLY_REFUNDED"
                ).length;
                const isPartialRefund = hasRefund && cancelledCount > 0 && cancelledCount < subOrders.length;
                const hasValidSubOrdersForQr = subOrders.some((s) => {
                  const st = (s.status || "").toUpperCase();
                  return st === "CONFIRMED" || st === "CHECKED_IN" || st === "COMPLETED";
                });
                const canShowMasterQr = (masterStatus === "PAID" || masterStatus === "COMPLETED" || masterStatus === "PARTIALLY_COMPLETED") && hasValidSubOrdersForQr;

                const displayedSubOrders = subOrders.filter((subOrder) => {
                  const sStatus = (subOrder.status || "").toUpperCase();
                  if (filter === "cancelled") {
                    return sStatus === "CANCELLED" || sStatus === "REFUNDED" || sStatus === "PARTIALLY_REFUNDED";
                  }
                  if (filter === "upcoming") {
                    return sStatus === "CONFIRMED" || sStatus === "PENDING";
                  }
                  if (filter === "completed") {
                    return sStatus === "COMPLETED" || sStatus === "CHECKED_IN";
                  }
                  return true;
                });

                return (
                  <article
                    key={masterOrder.id}
                    data-ui="order-card"
                    className="bg-white rounded-2xl p-6 border border-slate-200/90 flex flex-col gap-6"
                  >
                    {/* Master Order Header */}
                    <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-100">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-cyan-50 border border-cyan-200 text-cyan-700 flex items-center justify-center flex-shrink-0">
                          <Receipt className="w-5 h-5 text-cyan-700" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-xs text-slate-500 font-semibold uppercase tracking-wider">
                              {isEn ? "Order:" : "Đơn hàng:"}
                            </span>
                            <span className="text-base font-bold text-slate-900 font-mono">{orderCode}</span>
                            {!hasRefund && getMasterStatusBadge(masterOrder.status || "PENDING")}
                            {hasRefund && (
                              <span
                                className="px-2.5 py-0.5 rounded-md text-xs font-semibold border tracking-tight bg-slate-100 text-slate-800 border-slate-200"
                              >
                                {isPartialRefund
                                  ? (isEn ? `Partial refund (${cancelledCount}/${subOrders.length} services)` : `Hoàn tiền 1 phần (${cancelledCount}/${subOrders.length} dịch vụ)`)
                                  : latestRefund?.status === "COMPLETED" || latestRefund?.status === "PROCESSED"
                                  ? (isEn ? "Refunded" : "Đã hoàn tiền")
                                  : (isEn ? "Refund processing" : "Đang xử lý hoàn")}
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {isEn ? "Created:" : "Ngày tạo:"} {formatDateTime(masterOrder.createdAt)}
                          </p>
                        </div>
                      </div>

                      <div className="flex flex-col sm:flex-row items-end sm:items-center gap-3">
                        <div className="text-right">
                          <span className="text-xs text-slate-500 uppercase tracking-wider block font-medium">
                            {isEn ? "Total Amount" : "Tổng tiền"}
                          </span>
                          <span className="text-xl text-[#991b1b] font-extrabold tabular-nums">
                            {formatVnd(masterOrder.finalAmount ?? masterOrder.totalAmount)}
                          </span>
                        </div>

                        {/* Master Order Action Buttons */}
                        <div className="flex items-center gap-2">
                          {canShowMasterQr && (
                            <button
                              type="button"
                              onClick={() => handleOpenMasterQrModal(masterOrder)}
                              className="px-3.5 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer active:scale-95"
                              title={isEn ? "View unified QR pass for all activities" : "Xem mã QR dùng chung cho toàn bộ hoạt động trong đơn"}
                            >
                              <QrCode className="w-4 h-4" />
                              <span>{isEn ? "All-in-One Pass" : "Vé QR toàn đơn"}</span>
                            </button>
                          )}
                          {canDownloadPdf && (
                            <button
                              onClick={() => handleDownloadReceiptPdf(masterOrder.id)}
                              disabled={downloadingPdfId === masterOrder.id}
                              className="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                              title={isEn ? "Download PDF e-ticket & receipt" : "Tải hóa đơn & Vé điện tử PDF"}
                            >
                              {downloadingPdfId === masterOrder.id ? (
                                <Loader2 className="w-4 h-4 animate-spin text-slate-700" />
                              ) : (
                                <FileText className="w-4 h-4 text-slate-700" />
                              )}
                              <span>{downloadingPdfId === masterOrder.id ? (isEn ? "Exporting..." : "Đang xuất...") : (isEn ? "Download PDF" : "Tải vé PDF")}</span>
                            </button>
                          )}

                          {canCancelOrder && (
                            <button
                              onClick={() => handleOpenCancellationModal(masterOrder)}
                              className="px-3 py-2 rounded-xl border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-bold transition-colors cursor-pointer"
                            >
                              {isEn ? "Cancel Booking" : "Yêu cầu hủy đơn"}
                            </button>
                          )}

                          {hasRefund && (latestRefund?.status === "COMPLETED" || latestRefund?.status === "PROCESSED") && (
                            <button
                              type="button"
                              onClick={() => setViewingRefundOrder({ order: masterOrder, refunds })}
                              className="px-3.5 py-2 rounded-xl bg-white hover:bg-slate-50 text-slate-800 text-xs font-bold border border-slate-200 shadow-xs transition-colors cursor-pointer"
                              title={isEn ? "View refund transaction receipt" : "Xem chi tiết biên nhận hoàn tiền"}
                            >
                              {isEn ? "Refund Details" : "Chi tiết hoàn tiền"}
                            </button>
                          )}

                          {masterStatus === "PENDING_PAYMENT" && (
                            <div className="flex items-center gap-2">
                              {isOrderPaymentExpired(masterOrder) && (
                                <span className="px-2.5 py-1 rounded-xl bg-amber-50 text-amber-800 text-[11px] font-semibold border border-amber-200 flex items-center gap-1 shadow-2xs">
                                  <Clock className="w-3.5 h-3.5 text-amber-600" />
                                  <span>{isEn ? "Payment Expired" : "Quá hạn giữ chỗ"}</span>
                                </span>
                              )}
                              <button
                                onClick={() => setPaymentOrderId(masterOrder.id)}
                                disabled={isOrderPaymentExpired(masterOrder)}
                                className="px-4 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold shadow-xs cursor-pointer transition-colors disabled:opacity-50 flex items-center gap-1.5"
                              >
                                <span>{isEn ? "Pay Now" : "Thanh toán ngay"}</span>
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Sub-Orders List */}
                    <div className="flex flex-col gap-3">
                      <div className="flex items-center justify-between flex-wrap gap-2">
                        <span className="text-xs font-bold text-slate-600 uppercase tracking-wider flex items-center gap-1.5">
                          <Layers className="w-4 h-4 text-cyan-700" />
                          {filter === "all"
                            ? (isEn ? `Services (${subOrders.length})` : `Các dịch vụ (${subOrders.length})`)
                            : (isEn ? `Services in this tab (${displayedSubOrders.length}/${subOrders.length})` : `Dịch vụ thuộc mục này (${displayedSubOrders.length}/${subOrders.length})`)}
                        </span>
                        {filter !== "all" && displayedSubOrders.length < subOrders.length && (
                          <span className="text-[11px] text-slate-500 font-medium bg-slate-100 px-2 py-0.5 rounded-md">
                            {filter === "cancelled" && `${subOrders.length - displayedSubOrders.length} dịch vụ khác còn hiệu lực (xem ở tab Sắp tới/Đã hoàn thành)`}
                            {filter === "upcoming" && `${subOrders.length - displayedSubOrders.length} dịch vụ khác đã check-in hoặc bị huỷ`}
                            {filter === "completed" && `${subOrders.length - displayedSubOrders.length} dịch vụ khác sắp tới hoặc bị huỷ`}
                          </span>
                        )}
                      </div>

                      {displayedSubOrders.length === 0 ? (
                        <div className="p-4 rounded-xl bg-slate-50 text-center text-xs text-slate-500">
                          Không có dịch vụ con nào thuộc mục này.
                        </div>
                      ) : (
                        displayedSubOrders.map((subOrder) => {
                          const serviceDetail = serviceDetails[subOrder.serviceId];
                          const serviceName = (language === 'en' && serviceDetail?.nameEn)
                            ? serviceDetail.nameEn
                            : (serviceDetail?.name || subOrder.serviceName || `Dịch vụ #${subOrder.serviceId.slice(0, 8)}`);
                          const serviceImage = serviceDetail?.imageUrls?.[0] || subOrder.serviceThumbnail || "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80";
                          const matchedSlot = serviceDetail?.slots?.find((s) => s.slotId === subOrder.slotId);
                          const displaySlotTime = subOrder.slotTime || (matchedSlot ? `${matchedSlot.startTime.slice(0, 5)} - ${matchedSlot.endTime.slice(0, 5)} • ${matchedSlot.date}` : undefined);
                          const subOrderStatus = (subOrder.status || "").toUpperCase();
                          const isConfirmed = subOrderStatus === "CONFIRMED";
                          const isCheckedIn = subOrderStatus === "CHECKED_IN";
                          const isCompleted = subOrderStatus === "COMPLETED";
                          const summary = rescheduleMap[subOrder.id];
                          const vendorOption = summary?.options?.find((option) => option.isProposedByVendor);

                          return (
                            <div
                              key={subOrder.id}
                              className="bg-white rounded-xl p-3.5 flex flex-col md:flex-row gap-3.5 border border-slate-200/70 hover:border-slate-300 transition-all shadow-2xs"
                            >
                              {/* Thumbnail */}
                              <div className="relative w-full md:w-44 h-28 flex-shrink-0 rounded-xl overflow-hidden shadow-xs bg-slate-200">
                                <img
                                  src={serviceImage}
                                  alt={serviceName}
                                  className="w-full h-full object-cover"
                                  onError={(e) => {
                                    (e.target as HTMLImageElement).src = "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80";
                                  }}
                                />
                                {displaySlotTime && (
                                  <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-slate-900/80 backdrop-blur-sm text-white text-[11px] font-semibold flex items-center gap-1 shadow-xs">
                                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"></span>
                                    {displaySlotTime}
                                  </span>
                                )}
                              </div>

                              {/* Details */}
                              <div className="flex flex-col justify-between flex-1 min-w-0">
                                <div>
                                  <div className="flex items-start justify-between gap-2">
                                    <div>
                                      <div className="flex items-center gap-2">
                                        <span className="font-mono font-bold text-xs text-cyan-700" title={`Mã vé đầy đủ: ${subOrder.id}`}>
                                          #VE-{(subOrder.id || "").replace(/-/g, "").slice(-6).toUpperCase()}
                                        </span>
                                      </div>

                                      <h3
                                        className="text-base font-bold text-slate-900 mt-1 hover:text-cyan-600 cursor-pointer transition-colors"
                                        onClick={() => navigate(`/tour/${subOrder.serviceId}`)}
                                      >
                                        {serviceName}
                                      </h3>

                                      {serviceDetail?.address && (
                                        <div className="flex items-center gap-1 mt-1 text-xs text-slate-500">
                                          <MapPin className="w-3.5 h-3.5 text-cyan-700 shrink-0" />
                                          <span>{serviceDetail.address}</span>
                                        </div>
                                      )}

                                      {displaySlotTime ? (
                                        <div className="flex items-center gap-1.5 mt-1 text-xs text-sky-700 font-semibold">
                                          <Clock className="w-3.5 h-3.5 text-sky-600 shrink-0" />
                                          <span>{isEn ? "Departure:" : "Khởi hành:"} {displaySlotTime}</span>
                                        </div>
                                      ) : subOrder.slotId ? (
                                        <div className="flex items-center gap-1.5 mt-1 text-xs text-slate-500">
                                          <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                                          <span>{isEn ? "Departure slot:" : "Ca khởi hành:"} #{subOrder.slotId.slice(0, 8)}</span>
                                        </div>
                                      ) : null}
                                    </div>

                                    <div className="flex flex-col items-end">
                                      <span className="text-base text-[#991b1b] font-bold tabular-nums">
                                        {formatVnd(subOrder.finalAmount ?? subOrder.subtotalAmount)}
                                      </span>
                                      <span className="text-xs text-slate-500">
                                        {isEn ? "Quantity:" : "Số lượng:"} <strong>{subOrder.quantity} {isEn ? "guest(s)" : "khách"}</strong>
                                      </span>
                                    </div>
                                  </div>
                                </div>

                                {/* Sub-Order Footer & Actions */}
                                <div className="flex flex-wrap items-center justify-between gap-3 pt-3 mt-3 border-t border-slate-200">
                                  <div className="flex items-center gap-2">
                                    {getSubOrderStatusBadge(subOrder.status, masterStatus)}
                                    {submittedDisputes[subOrder.id] && (
                                      submittedDisputes[subOrder.id].status === "RESOLVED_REJECTED" ? (
                                        <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-600 border border-slate-200 text-xs font-semibold">
                                          {isEn ? "Dispute Rejected" : "Khiếu nại bị từ chối"}
                                        </span>
                                      ) : submittedDisputes[subOrder.id].status === "RESOLVED_REFUND" || submittedDisputes[subOrder.id].status === "RESOLVED_PARTIAL" ? (
                                        <span className="px-2.5 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold">
                                          {isEn ? "Refunded via Dispute" : "Đã giải quyết hoàn tiền"}
                                        </span>
                                      ) : (
                                        <span className="px-2.5 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-200 text-xs font-semibold">
                                          {isEn ? "Dispute in progress" : "Đang khiếu nại"}
                                        </span>
                                      )
                                    )}
                                  </div>

                                  <div className="flex items-center gap-2">
                                    {masterStatus === "PENDING_PAYMENT" && (
                                      <button
                                        onClick={() => setPaymentOrderId(masterOrder.id)}
                                        disabled={isOrderPaymentExpired(masterOrder)}
                                        className="px-3.5 py-1.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-all active:scale-95 disabled:opacity-50"
                                        type="button"
                                      >
                                        <span>{isEn ? "Pay Now" : "Thanh toán ngay"}</span>
                                      </button>
                                    )}
                                    {/* Check-in QR button (P0 for CONFIRMED suborders) */}
                                    {isConfirmed && (
                                      <button
                                        onClick={() => handleOpenQrModal(subOrder)}
                                        className="px-3.5 py-1.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-all active:scale-95"
                                        type="button"
                                      >
                                        <QrCode className="w-4 h-4" />
                                        <span>{isEn ? "Check-in QR Pass" : "Mã QR Check-in"}</span>
                                      </button>
                                    )}

                                    {/* Refunded status display */}
                                    {subOrder.status === "REFUNDED" && (
                                      <span className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-500 text-xs font-semibold flex items-center gap-1">
                                        <Ban className="w-3.5 h-3.5 text-slate-400" />
                                        <span>{isEn ? "Booking Cancelled" : "Vé đã hủy"}</span>
                                      </span>
                                    )}

                                    {/* Checked-in status display */}
                                    {isCheckedIn && (
                                      <span className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-800 border border-slate-200 text-xs font-semibold flex items-center gap-1">
                                        <CheckCheck className="w-4 h-4 text-slate-700" />
                                        <span>{isEn ? "Boarded / Checked-in" : "Đã quét vé lên tàu"}</span>
                                      </span>
                                    )}

                                    {/* Support / Dispute action */}
                                    {submittedDisputes[subOrder.id] ? (
                                      submittedDisputes[subOrder.id].status === "RESOLVED_REJECTED" ? (
                                        <span
                                          className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-700 border border-slate-200 text-xs font-medium flex items-center gap-1.5 max-w-md"
                                          title={submittedDisputes[subOrder.id].adminNote || undefined}
                                        >
                                          <Ban className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                                          <span className="truncate">
                                            Bác bỏ khiếu nại{submittedDisputes[subOrder.id].adminNote ? `: ${submittedDisputes[subOrder.id].adminNote}` : ''}
                                          </span>
                                        </span>
                                      ) : submittedDisputes[subOrder.id].status === "RESOLVED_REFUND" || submittedDisputes[subOrder.id].status === "RESOLVED_PARTIAL" ? (
                                        <span className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-800 border border-slate-200 text-xs font-semibold flex items-center gap-1.5">
                                          <CheckCircle2 className="w-3.5 h-3.5 text-slate-700 shrink-0" />
                                          <span>
                                            Đã hoàn {submittedDisputes[subOrder.id].refundPercentage ? `${submittedDisputes[subOrder.id].refundPercentage}%` : ''} tiền dịch vụ
                                          </span>
                                        </span>
                                      ) : (
                                        <span className="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 text-xs font-semibold flex items-center gap-1.5">
                                          <AlertCircle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                                          <span>Đang khiếu nại: {submittedDisputes[subOrder.id].description}</span>
                                        </span>
                                      )
                                    ) : (
                                      <button
                                        onClick={() => handleOpenDispute(subOrder)}
                                        className="px-3 py-1.5 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-700 text-xs font-medium transition-colors cursor-pointer"
                                        type="button"
                                      >
                                        {isEn ? "Support / Dispute" : "Hỗ trợ / Khiếu nại"}
                                      </button>
                                    )}
                                  </div>
                                </div>

                                {/* Comprehensive Review Section for COMPLETED sub-orders */}
                                {isCompleted && (
                                  <div className="mt-3.5 pt-3.5 border-t border-slate-200/70">
                                    {reviewsMap[subOrder.id] ? (
                                      <div data-ui="suborder-review-card" className="p-3.5 sm:p-4 rounded-xl bg-white border border-slate-200 shadow-2xs space-y-2.5">
                                        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-2">
                                          <div className="flex items-center gap-2">
                                            <div className="flex items-center gap-0.5">
                                              {[1, 2, 3, 4, 5].map((star) => (
                                                <Star
                                                  key={star}
                                                  className={`w-4 h-4 ${
                                                    star <= (reviewsMap[subOrder.id]?.rating || 5)
                                                      ? "fill-amber-400 text-amber-400"
                                                      : "fill-slate-200 text-slate-200"
                                                  }`}
                                                />
                                              ))}
                                            </div>
                                            <span className="text-xs font-bold text-slate-900">
                                              {reviewsMap[subOrder.id]?.rating === 5
                                                ? (isEn ? "5/5 • Excellent" : "5/5 • Tuyệt vời")
                                                : reviewsMap[subOrder.id]?.rating === 4
                                                ? (isEn ? "4/5 • Very Good" : "4/5 • Rất tốt")
                                                : `${reviewsMap[subOrder.id]?.rating}/5 • ${isEn ? "Reviewed" : "Đã đánh giá"}`}
                                            </span>
                                            <span className="text-[11px] text-slate-500 font-medium">
                                              • {formatDateTime(reviewsMap[subOrder.id]?.createdAt)}
                                            </span>
                                          </div>

                                          <button
                                            type="button"
                                            onClick={() => handleOpenReview(subOrder)}
                                            className="text-xs font-semibold text-sky-700 hover:text-sky-800 flex items-center gap-1 hover:underline cursor-pointer"
                                          >
                                            <Edit3 className="w-3.5 h-3.5" />
                                            <span>{isEn ? "Edit Review" : "Sửa đánh giá"}</span>
                                          </button>
                                        </div>

                                        <p className="text-sm text-slate-800 leading-relaxed whitespace-pre-line">
                                          {reviewsMap[subOrder.id]?.comment}
                                        </p>

                                        {reviewsMap[subOrder.id]?.images && (reviewsMap[subOrder.id]?.images?.length ?? 0) > 0 && (
                                          <div className="flex items-center gap-2 pt-1 flex-wrap">
                                            {reviewsMap[subOrder.id]?.images?.map((img, i) => (
                                              <img
                                                key={i}
                                                src={img}
                                                alt={isEn ? "Trip photo" : "Ảnh thực tế chuyến đi"}
                                                className="w-16 h-16 rounded-lg object-cover border border-slate-200 shadow-xs"
                                              />
                                            ))}
                                          </div>
                                        )}

                                        {reviewsMap[subOrder.id]?.vendorReply && (
                                          <div className="mt-2 p-3 rounded-lg bg-sky-50/80 border border-sky-100 text-xs space-y-1">
                                            <div className="flex items-center justify-between">
                                              <strong className="font-bold text-sky-950">
                                                {isEn ? "Partner Response:" : "Phản hồi từ đơn vị tổ chức:"}
                                              </strong>
                                              {reviewsMap[subOrder.id]?.vendorRepliedAt && (
                                                <span className="text-[11px] text-sky-700">
                                                  {formatDateTime(reviewsMap[subOrder.id]?.vendorRepliedAt)}
                                                </span>
                                              )}
                                            </div>
                                            <p className="text-sky-900 leading-relaxed">
                                              {reviewsMap[subOrder.id]?.vendorReply}
                                            </p>
                                          </div>
                                        )}
                                      </div>
                                    ) : (
                                      <div data-ui="suborder-review-invitation" className="p-3.5 sm:p-4 rounded-xl bg-amber-50/80 border border-amber-200/90 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-2xs">
                                        <div className="flex items-start gap-3">
                                          <div className="w-8 h-8 rounded-lg bg-amber-100 border border-amber-200 text-amber-800 flex items-center justify-center shrink-0">
                                            <Star className="w-4 h-4 fill-amber-500 text-amber-500" />
                                          </div>
                                          <div className="space-y-0.5">
                                            <h4 className="text-sm font-bold text-slate-900">
                                              {isEn ? "Review Service & Safety Standards" : "Đánh giá dịch vụ & Tiêu chuẩn an toàn"}
                                            </h4>
                                            <p className="text-xs text-slate-700 leading-relaxed">
                                              {isEn
                                                ? "Your experience is completed. Share your feedback on instructors, safety equipment, and overall satisfaction."
                                                : "Trải nghiệm đã hoàn tất. Hãy chia sẻ cảm nhận về hướng dẫn viên, áo phao cứu sinh và độ hài lòng thực tế."}
                                            </p>
                                          </div>
                                        </div>

                                        <button
                                          type="button"
                                          onClick={() => handleOpenReview(subOrder)}
                                          className="px-4 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer shrink-0 transition-transform active:scale-95"
                                        >
                                          <Star className="w-3.5 h-3.5 fill-white text-white" />
                                          <span>{isEn ? "Write Review" : "Viết đánh giá ngay"}</span>
                                        </button>
                                      </div>
                                    )}
                                  </div>
                                )}

                                {/* Pending Vendor Reschedule Proposal Banner */}
                                {summary && vendorOption && (
                                    <div className="mt-3 p-3.5 sm:p-4 rounded-xl bg-amber-50/90 border border-amber-300 flex flex-col md:flex-row items-start md:items-center justify-between gap-3 shadow-xs animate-fade-in">
                                      <div className="flex items-start gap-3">
                                        <div className="w-9 h-9 rounded-xl bg-amber-500 text-white flex items-center justify-center flex-shrink-0 shadow-xs">
                                          <Calendar className="w-5 h-5 text-white" />
                                        </div>
                                        <div className="flex flex-col gap-1 text-left">
                                          <div className="flex items-center gap-2 flex-wrap">
                                            <span className="px-2 py-0.5 rounded-md bg-amber-200 text-amber-900 text-[10px] font-bold uppercase tracking-wider">
                                              Đối tác đề xuất đổi ca
                                            </span>
                                            <span className="text-xs font-bold text-slate-900">
                                              Ca mới: {vendorOption.bookingDate} ({vendorOption.startTime.slice(0, 5)} - {vendorOption.endTime.slice(0, 5)})
                                            </span>
                                          </div>
                                          <p className="text-xs text-slate-700">
                                            <strong>Lý do từ đối tác:</strong> {summary.disruptionReason || "Điều kiện vận hành / an toàn thời tiết"}
                                          </p>
                                          {vendorOption.proposalExpiresAt && (
                                            <p className="text-[11px] text-amber-800 font-medium flex items-center gap-1">
                                              <Clock className="w-3.5 h-3.5 text-amber-800" />
                                              Xác nhận trước: {formatDateTime(vendorOption.proposalExpiresAt)}
                                            </p>
                                          )}
                                        </div>
                                      </div>

                                      <div className="flex items-center gap-2 self-end md:self-center flex-shrink-0">
                                        {summary.fullRefundAvailable && (
                                          <button
                                            type="button"
                                            onClick={() => handleOpenCancellationModal(masterOrder)}
                                            className="px-3.5 py-2 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer transition-all active:scale-95"
                                          >
                                            <XCircle className="w-4 h-4 text-rose-600" />
                                            <span>Từ chối & Hoàn tiền</span>
                                          </button>
                                        )}
                                        <button
                                          type="button"
                                          disabled={confirmingRescheduleId === subOrder.id}
                                          onClick={() => handleConfirmReschedule(subOrder.id, vendorOption, summary.rescheduleVersion)}
                                          className="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer disabled:opacity-50 transition-all active:scale-95"
                                        >
                                          <CheckCircle2 className="w-4 h-4 text-white" />
                                          <span>{confirmingRescheduleId === subOrder.id ? "Đang xử lý..." : "Đồng ý chuyển ca"}</span>
                                        </button>
                                      </div>
                                    </div>
                                )}
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
      {/* Portaled Master Order All-in-One Check-in Pass Modal          */}
      {/* ============================================================== */}
      {selectedMasterOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-scale-in">
            <div className="bg-white rounded-2xl max-w-md w-full shadow-2xl relative border border-slate-200/90 overflow-hidden flex flex-col max-h-[92vh]">
              {/* Header: Master Order context & Code */}
              <div className="p-5 pb-4 bg-slate-50/70 border-b border-slate-100 flex items-start justify-between gap-3 shrink-0">
                <div className="space-y-1 min-w-0 pr-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded-md bg-sky-100 text-sky-900 border border-sky-200">
                      #ORD-{(selectedMasterOrder.id || "").replace(/-/g, "").slice(-8).toUpperCase()}
                    </span>
                    <span className="text-[11px] font-semibold text-slate-500">
                      {(selectedMasterOrder.items || selectedMasterOrder.subOrders || []).length} dịch vụ
                    </span>
                  </div>
                  <h3 className="text-sm font-bold text-slate-900 leading-snug">
                    Vé điện tử toàn đơn (All-in-One Pass)
                  </h3>
                  <p className="text-xs text-slate-500">
                    Mã QR hợp nhất dùng cho tất cả hoạt động thuộc đơn hàng
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedMasterOrder(null)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-colors shrink-0 cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Scrollable Content */}
              <div className="p-5 flex flex-col items-center text-center overflow-y-auto">
                <div className="p-3 bg-white rounded-xl border border-slate-200 shadow-xs flex items-center justify-center">
                  {qrLoading && (
                    <div className="w-56 h-56 flex flex-col items-center justify-center space-y-2">
                      <Loader2 className="w-8 h-8 text-sky-600 animate-spin" />
                      <span className="text-xs text-slate-500 font-medium">Đang tạo mã QR bảo mật...</span>
                    </div>
                  )}

                  {!qrLoading && qrError && (
                    <div className="w-56 h-56 flex flex-col items-center justify-center p-3 text-center space-y-2">
                      <AlertTriangle className="w-8 h-8 text-rose-500" />
                      <span className="text-xs text-rose-700 font-semibold">{qrError}</span>
                    </div>
                  )}

                  {!qrLoading && !qrError && qrData && (
                    <img
                      src={`https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=${encodeURIComponent(
                        qrData.qrToken
                      )}`}
                      alt="QR Check-in Toàn Đơn"
                      className="w-56 h-56 object-contain"
                    />
                  )}
                </div>

                <p className="text-xs text-slate-600 mt-3 font-medium px-2">
                  Chỉ cần 1 mã này cho toàn bộ chuyến đi. Đến từng bến/điểm dịch vụ, điều hành viên tại bến sẽ quét mã để tự động nhận diện đúng dịch vụ.
                </p>

                {/* Fallback Token Box */}
                {qrData?.qrToken && (
                  <div className="w-full mt-3 p-2.5 rounded-xl bg-slate-50 border border-slate-200 text-left flex items-center justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider block">
                        Mã ký số dự phòng
                      </span>
                      <span
                        className="text-xs font-mono text-slate-800 truncate block select-all"
                        title={qrData.qrToken}
                      >
                        {qrData.qrToken}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(qrData.qrToken);
                        setCopiedToken(true);
                        setTimeout(() => setCopiedToken(false), 2000);
                      }}
                      className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all shrink-0 cursor-pointer ${
                        copiedToken
                          ? "bg-emerald-600 text-white shadow-2xs"
                          : "bg-white hover:bg-slate-100 text-slate-700 border border-slate-200 shadow-2xs"
                      }`}
                    >
                      {copiedToken ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                      <span>{copiedToken ? "Đã chép" : "Sao chép"}</span>
                    </button>
                  </div>
                )}

                {/* Perforated separator */}
                <div className="w-full border-t border-dashed border-slate-200 relative my-4">
                  <span className="absolute -left-7 -top-2 w-4 h-4 rounded-full bg-slate-950/70" />
                  <span className="absolute -right-7 -top-2 w-4 h-4 rounded-full bg-slate-950/70" />
                </div>

                {/* Included Sub-Orders List */}
                <div className="w-full text-left space-y-2">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 block">
                    Các hoạt động áp dụng trong vé ({(selectedMasterOrder.items || selectedMasterOrder.subOrders || []).length})
                  </span>

                  <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                    {(selectedMasterOrder.items || selectedMasterOrder.subOrders || []).map((item, idx) => {
                      const itemDetail = serviceDetails[item.serviceId];
                      const sName = (language === 'en' && itemDetail?.nameEn)
                        ? itemDetail.nameEn
                        : (itemDetail?.name || item.serviceName || `Dịch vụ #${item.serviceId.slice(0, 8)}`);
                      const st = (item.status || "").toUpperCase();
                      const matchedSlot = itemDetail?.slots?.find((s) => s.slotId === item.slotId);
                      const slotDisplay = item.slotTime || (matchedSlot ? `${matchedSlot.startTime.slice(0, 5)} - ${matchedSlot.endTime.slice(0, 5)} • ${matchedSlot.date}` : undefined);

                      return (
                        <div
                          key={item.id || idx}
                          className="p-2.5 rounded-xl bg-slate-50/80 border border-slate-200/70 text-xs flex items-start justify-between gap-2"
                        >
                          <div className="min-w-0 flex-1 space-y-0.5">
                            <p className="font-bold text-slate-900 truncate">{sName}</p>
                            {slotDisplay && (
                              <p className="text-[11px] text-slate-500 flex items-center gap-1 font-medium">
                                <Clock className="w-3 h-3 text-sky-600 shrink-0" />
                                <span>{slotDisplay}</span>
                              </p>
                            )}
                            <p className="text-[11px] text-slate-500">
                              Số lượng: <strong className="text-slate-800">{item.quantity} khách</strong>
                            </p>
                          </div>
                          <div className="shrink-0 pt-0.5">
                            {st === "CHECKED_IN" ? (
                              <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 font-semibold text-[10px]">
                                ĐÃ CHECK-IN
                              </span>
                            ) : st === "CONFIRMED" ? (
                              <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 font-semibold text-[10px]">
                                CHỜ SOÁT VÉ
                              </span>
                            ) : st === "COMPLETED" ? (
                              <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-800 border border-slate-200/90 font-semibold text-[10px]">
                                HOÀN TẤT
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 border border-slate-200 font-medium text-[10px]">
                                {st}
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {qrData?.expiresAt && (
                  <div className="w-full flex justify-between items-center pt-3 text-[11px] text-slate-500">
                    <span>Hiệu lực quét đến:</span>
                    <span className="font-semibold text-slate-700 font-mono">
                      {formatDateTime(qrData.expiresAt)}
                    </span>
                  </div>
                )}
              </div>

              {/* Footer */}
              <div className="p-4 pt-0 shrink-0">
                <button
                  type="button"
                  onClick={() => setSelectedMasterOrder(null)}
                  className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors cursor-pointer"
                >
                  Đóng vé
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {/* ============================================================== */}
      {/* Portaled Check-in QR Code Modal (Real Backend HMAC Token)     */}
      {/* Redesigned under Taste Skill & Anti-Slop (Boarding Pass Model)*/}
      {/* ============================================================== */}
      {selectedSubOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-scale-in">
            <div className="bg-white rounded-2xl max-w-sm w-full shadow-2xl relative border border-slate-200/90 overflow-hidden flex flex-col">
              {/* Header: Service context & Ticket Code */}
              <div className="p-5 pb-4 bg-slate-50/70 border-b border-slate-100 flex items-start justify-between gap-3">
                <div className="space-y-1 min-w-0 pr-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded-md bg-sky-100 text-sky-900 border border-sky-200">
                      #VE-{(selectedSubOrder.id || "").replace(/-/g, "").slice(-6).toUpperCase()}
                    </span>
                  </div>
                  <h3 className="text-sm font-bold text-slate-900 leading-snug line-clamp-1 pt-0.5">
                    {(language === 'en' && serviceDetails[selectedSubOrder.serviceId]?.nameEn)
                      ? serviceDetails[selectedSubOrder.serviceId]?.nameEn
                      : (serviceDetails[selectedSubOrder.serviceId]?.name || selectedSubOrder.serviceName || "Trải nghiệm biển Đà Nẵng")}
                  </h3>
                  {(() => {
                    const modalMatchedSlot = serviceDetails[selectedSubOrder.serviceId]?.slots?.find((s) => s.slotId === selectedSubOrder.slotId);
                    const modalSlotTime = selectedSubOrder.slotTime || (modalMatchedSlot ? `${modalMatchedSlot.startTime.slice(0, 5)} - ${modalMatchedSlot.endTime.slice(0, 5)} • ${modalMatchedSlot.date}` : undefined);
                    return modalSlotTime ? (
                      <div className="flex items-center gap-1.5 text-xs text-slate-600 font-medium">
                        <Clock className="w-3.5 h-3.5 text-sky-700 shrink-0" />
                        <span>Ca khởi hành: <strong className="text-slate-900">{modalSlotTime}</strong></span>
                      </div>
                    ) : null;
                  })()}
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedSubOrder(null)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-colors shrink-0 cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* QR Centerpiece */}
              <div className="p-5 flex flex-col items-center text-center">
                <div className="p-3 bg-white rounded-xl border border-slate-200 shadow-xs flex items-center justify-center">
                  {qrLoading && (
                    <div className="w-56 h-56 flex flex-col items-center justify-center space-y-2">
                      <Loader2 className="w-8 h-8 text-sky-600 animate-spin" />
                      <span className="text-xs text-slate-500 font-medium">Đang tạo mã QR bảo mật...</span>
                    </div>
                  )}

                  {!qrLoading && qrError && (
                    <div className="w-56 h-56 flex flex-col items-center justify-center p-3 text-center space-y-2">
                      <AlertTriangle className="w-8 h-8 text-rose-500" />
                      <span className="text-xs text-rose-700 font-semibold">{qrError}</span>
                    </div>
                  )}

                  {!qrLoading && !qrError && qrData && (
                    <img
                      src={`https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=${encodeURIComponent(
                        qrData.qrToken
                      )}`}
                      alt="QR Check-in"
                      className="w-56 h-56 object-contain"
                    />
                  )}
                </div>

                <p className="text-xs text-slate-500 mt-2.5 font-medium">
                  Xuất trình mã này cho điều hành viên tại cầu tàu để quét lên tàu
                </p>

                {/* Fallback Token Box (Compact Density & Mono) */}
                {qrData?.qrToken && (
                  <div className="w-full mt-4 p-2.5 rounded-xl bg-slate-50 border border-slate-200 text-left flex items-center justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider block">
                        Mã ký số dự phòng
                      </span>
                      <span
                        className="text-xs font-mono text-slate-800 truncate block select-all"
                        title={qrData.qrToken}
                      >
                        {qrData.qrToken}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(qrData.qrToken);
                        setCopiedToken(true);
                        setTimeout(() => setCopiedToken(false), 2000);
                      }}
                      className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all shrink-0 cursor-pointer ${
                        copiedToken
                          ? "bg-emerald-600 text-white shadow-2xs"
                          : "bg-white hover:bg-slate-100 text-slate-700 border border-slate-200 shadow-2xs"
                      }`}
                    >
                      {copiedToken ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                      <span>{copiedToken ? "Đã chép" : "Sao chép"}</span>
                    </button>
                  </div>
                )}
              </div>

              {/* Perforated separator / border */}
              <div className="border-t border-dashed border-slate-200 relative mx-5">
                <span className="absolute -left-7 -top-2 w-4 h-4 rounded-full bg-slate-950/70" />
                <span className="absolute -right-7 -top-2 w-4 h-4 rounded-full bg-slate-950/70" />
              </div>

              {/* Metadata details */}
              <div className="p-5 pt-4 space-y-2 text-xs">
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Số lượng khách:</span>
                  <span className="font-bold text-slate-900">{selectedSubOrder.quantity} khách</span>
                </div>
                {serviceDetails[selectedSubOrder.serviceId]?.address && (
                  <div className="flex justify-between items-start gap-2 py-0.5">
                    <span className="text-slate-500 shrink-0">Điểm đón:</span>
                    <span className="font-medium text-slate-800 text-right line-clamp-1">
                      {serviceDetails[selectedSubOrder.serviceId].address}
                    </span>
                  </div>
                )}
                {qrData?.expiresAt && (
                  <div className="flex justify-between items-center py-0.5">
                    <span className="text-slate-500">Hiệu lực quét đến:</span>
                    <span className="font-semibold text-slate-700 font-mono">
                      {formatDateTime(qrData.expiresAt)}
                    </span>
                  </div>
                )}
              </div>

              {/* Footer CTA */}
              <div className="p-5 pt-0">
                <button
                  type="button"
                  onClick={() => setSelectedSubOrder(null)}
                  className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors cursor-pointer"
                >
                  Đóng vé
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
            <div className="bg-white rounded-2xl p-6 sm:p-8 max-w-lg w-full shadow-2xl relative border border-slate-100 space-y-4">
              <div className="flex items-center justify-between border-b pb-3">
                <h3 className="text-lg font-bold text-slate-900">Yêu cầu hủy đơn &amp; Hoàn tiền</h3>
                <button
                  onClick={() => setCancellationOrder(null)}
                  className="text-slate-400 hover:text-slate-600 cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {cancellationLoading && (
                <div className="py-8 flex flex-col items-center justify-center space-y-2">
                  <Loader2 className="w-8 h-8 text-cyan-600 animate-spin" />
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
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  <span>{cancellationSuccess}</span>
                </div>
              )}

              {!cancellationLoading && cancellationPreview && !cancellationSuccess && (
                <div className="space-y-4">
                  {/* Policy Preview Card */}
                  <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-2 text-xs">
                    <div className="flex justify-between items-start gap-4">
                      <span className="text-slate-500 shrink-0">Chính sách áp dụng:</span>
                      <span className="font-semibold text-slate-800 text-right leading-relaxed">
                        {formatPolicyDescription(
                          cancellationPreview.policyApplied,
                          cancellationPreview.refundPercentage
                        )}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Tổng thanh toán ban đầu:</span>
                      <span className="font-bold text-slate-800 tabular-nums">
                        {formatVnd(cancellationPreview.originalAmount)}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Tỷ lệ hoàn tiền:</span>
                      <span className="font-extrabold text-blue-600 tabular-nums">
                        {cancellationPreview.refundPercentage}%
                      </span>
                    </div>
                    <div className="flex justify-between items-center pt-2 border-t border-slate-200 text-sm">
                      <span className="font-bold text-slate-900">Tiền hoàn dự kiến:</span>
                      <span className="font-extrabold text-rose-600 tabular-nums">
                        {formatVnd(cancellationPreview.refundAmount)}
                      </span>
                    </div>
                  </div>

                  {/* Non-refundable notice when within 24 hours */}
                  {cancellationPreview.refundAmount <= 0 && (
                    <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs flex items-start gap-2">
                      <Info className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                      <span>
                        Đơn hàng có giờ khởi hành trong vòng 24 giờ tới nên không đủ điều kiện hoàn tiền theo chính sách dịch vụ.
                      </span>
                    </div>
                  )}

                  {/* Reason selector */}
                  <div>
                    <label className="text-xs font-bold text-slate-800 block mb-2">Lý do hủy đơn</label>
                    <div className="space-y-2">
                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "PERSONAL"}
                          onChange={() => setCancellationReason("PERSONAL")}
                        />
                        <span>Thay đổi lịch trình / Lý do cá nhân</span>
                      </label>

                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "WEATHER"}
                          onChange={() => setCancellationReason("WEATHER")}
                        />
                        <span>Thời tiết biển xấu / Sự cố bất khả kháng</span>
                      </label>

                      <label className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs">
                        <input
                          type="radio"
                          name="refundReason"
                          checked={cancellationReason === "VENDOR"}
                          onChange={() => setCancellationReason("VENDOR")}
                        />
                        <span>Nhà cung cấp yêu cầu dời/hủy ca</span>
                      </label>
                    </div>
                  </div>

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
                      disabled={cancellationSubmitting || cancellationPreview.refundAmount <= 0}
                      onClick={handleConfirmCancellation}
                      className="px-5 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {cancellationSubmitting
                        ? "Đang xử lý..."
                        : cancellationPreview.refundAmount <= 0
                        ? "Không đủ điều kiện hoàn tiền"
                        : "Xác nhận hủy & Hoàn tiền"}
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
            <div className="bg-white rounded-2xl p-6 max-w-lg w-full shadow-2xl relative space-y-4 border border-slate-100">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div>
                  <h3 className="text-base font-bold text-slate-900">Đánh giá trải nghiệm</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Tiêu chuẩn an toàn &amp; Chất lượng dịch vụ</p>
                </div>
                <button
                  onClick={() => setReviewingSubOrder(null)}
                  className="p-1.5 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="space-y-1 bg-slate-50 p-3 rounded-xl border border-slate-200/80">
                <span className="text-[11px] text-slate-500 font-mono block">
                  Mã vé: #VE-{(reviewingSubOrder.id || "").replace(/-/g, "").slice(-6).toUpperCase()}
                </span>
                <p className="text-sm font-bold text-slate-900">
                  {serviceDetails[reviewingSubOrder.serviceId]?.name || "Dịch vụ du lịch biển"}
                </p>
              </div>

              {/* Interactive Star Rating */}
              <div>
                <label className="text-xs font-bold text-slate-800 block mb-2">Mức độ hài lòng</label>
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => setRatingStars(star)}
                        className="p-1 rounded-lg transition-transform hover:scale-115 active:scale-95 cursor-pointer"
                        title={`${star} sao`}
                      >
                        <Star
                          className={`w-7 h-7 ${
                            star <= ratingStars
                              ? "fill-amber-400 text-amber-400"
                              : "fill-slate-100 text-slate-300 hover:text-amber-300"
                          }`}
                        />
                      </button>
                    ))}
                  </div>
                  <span className="text-xs font-bold text-slate-700 bg-amber-50 px-2.5 py-1 rounded-md border border-amber-200">
                    {ratingStars === 5
                      ? "5/5 • Tuyệt vời & Chuẩn an toàn"
                      : ratingStars === 4
                      ? "4/5 • Rất tốt & Chuyên nghiệp"
                      : ratingStars === 3
                      ? "3/5 • Bình thường"
                      : ratingStars === 2
                      ? "2/5 • Cần cải thiện"
                      : "1/5 • Không hài lòng"}
                  </span>
                </div>
              </div>

              {/* Detailed comment textarea */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-bold text-slate-800">Nhận xét chi tiết</label>
                  <span className="text-[11px] font-mono text-slate-500">{reviewComment.length}/2000</span>
                </div>
                <textarea
                  rows={4}
                  maxLength={2000}
                  value={reviewComment}
                  onChange={(e) => setReviewComment(e.target.value)}
                  placeholder="Chia sẻ trải nghiệm thực tế: áo phao SOLAS, hướng dẫn viên PADI, thời gian xuất bến, vệ sinh tàu/ván..."
                  className="w-full p-3 rounded-xl border border-slate-200 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-sky-500 resize-none"
                />
              </div>

              {/* Attached Image URLs */}
              <div>
                <label className="text-xs font-bold text-slate-800 block mb-1">
                  Hình ảnh thực tế (đường dẫn URL, mỗi dòng 1 link, tối đa 5 ảnh)
                </label>
                <textarea
                  rows={2}
                  value={reviewImagesInput}
                  onChange={(e) => setReviewImagesInput(e.target.value)}
                  placeholder="https://images.unsplash.com/..."
                  className="w-full p-2.5 rounded-xl border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-sky-500 resize-none font-mono"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  disabled={isSubmittingReview}
                  onClick={() => setReviewingSubOrder(null)}
                  className="px-4 py-2 rounded-xl text-slate-600 hover:bg-slate-100 text-xs font-semibold cursor-pointer disabled:opacity-50"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={isSubmittingReview || !reviewComment.trim()}
                  onClick={handleSubmitReview}
                  className="px-5 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                >
                  {isSubmittingReview ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                  <span>{isSubmittingReview ? "Đang gửi..." : "Gửi đánh giá"}</span>
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
            <div className="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl relative space-y-4 border border-slate-100">
              <div className="flex items-center justify-between border-b pb-3">
                <h3 className="text-base font-bold text-slate-900">Gửi khiếu nại &amp; Hỗ trợ dịch vụ</h3>
                <button
                  onClick={() => setDisputingSubOrder(null)}
                  className="p-1.5 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="space-y-1">
                <span className="text-xs text-slate-500 font-mono">
                  Mã vé: #VE-{(disputingSubOrder.id || "").replace(/-/g, "").slice(-6).toUpperCase()}
                </span>
                <p className="text-sm font-bold text-slate-800">
                  {serviceDetails[disputingSubOrder.serviceId]?.name || "Dịch vụ du lịch biển"}
                </p>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1.5">
                  {isEn ? "Incident Category" : "Phân loại sự cố"}
                </label>
                <select
                  value={disputeReasonType}
                  onChange={(e) => setDisputeReasonType(e.target.value as DisputeReason)}
                  className="w-full p-2.5 rounded-xl border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 bg-white"
                >
                  <option value="SERVICE_NOT_AS_DESCRIBED">
                    {isEn ? "Service not as described" : "Dịch vụ không đúng mô tả / cam kết"}
                  </option>
                  <option value="VENDOR_NO_SHOW">
                    {isEn ? "Operator no-show at meeting point" : "Nhà cung cấp không đến / vắng mặt tại điểm hẹn"}
                  </option>
                  <option value="SAFETY_CONCERN">
                    {isEn ? "Safety concern / missing life-saving equipment" : "Vấn đề an toàn / thiếu trang thiết bị cứu sinh"}
                  </option>
                  <option value="PAYMENT_ISSUE">
                    {isEn ? "Payment issue / incorrect surcharge" : "Sự cố thanh toán / phụ thu sai quy định"}
                  </option>
                  <option value="OTHER">{isEn ? "Other reason" : "Lý do khác"}</option>
                </select>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1.5">
                  {isEn ? "Detailed Description" : "Mô tả chi tiết"}
                </label>
                <textarea
                  rows={4}
                  value={disputeReason}
                  onChange={(e) => setDisputeReason(e.target.value)}
                  placeholder={
                    isEn
                      ? "Describe the issue (e.g. operator late without notice, unsafe equipment, incorrect charges...)"
                      : "Mô tả sự cố bạn gặp phải (ví dụ: nhà cung cấp trễ ca không báo trước, trang thiết bị không đảm bảo an toàn...)"
                  }
                  className="w-full p-3 rounded-xl border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 resize-none"
                />
              </div>

              <p className="text-[11px] text-slate-500">
                {isEn
                  ? "DANASEA operations and customer support team will process and respond within 24 hours."
                  : "Đội ngũ điều phối và hỗ trợ khách hàng DANASEA sẽ tiếp nhận và phản hồi trong vòng 24 giờ."}
              </p>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  disabled={isSubmittingDispute}
                  onClick={() => setDisputingSubOrder(null)}
                  className="px-4 py-2 rounded-xl text-slate-600 hover:bg-slate-100 text-xs font-semibold cursor-pointer disabled:opacity-50"
                >
                  {isEn ? "Cancel" : "Hủy"}
                </button>
                <button
                  type="button"
                  disabled={isSubmittingDispute || !disputeReason.trim()}
                  onClick={handleSubmitDispute}
                  className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
                >
                  {isSubmittingDispute && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>{isSubmittingDispute ? (isEn ? "Submitting..." : "Đang gửi...") : (isEn ? "Submit Dispute" : "Gửi khiếu nại")}</span>
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {/* ============================================================== */}
      {/* Portaled Refund Details Modal                                  */}
      {/* ============================================================== */}
      {viewingRefundOrder &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl relative space-y-4 border border-slate-100">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center">
                    <Receipt className="w-5 h-5 text-amber-700" />
                  </div>
                  <div>
                    <h3 className="text-base font-extrabold text-slate-900">
                      {isEn ? "Refund Details" : "Chi tiết hoàn tiền"}
                    </h3>
                    <p className="text-xs text-slate-500 font-mono">
                      #ORD-{(viewingRefundOrder.order.id || "").slice(0, 8).toUpperCase()}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setViewingRefundOrder(null)}
                  className="p-1.5 rounded-xl hover:bg-slate-100 flex items-center justify-center text-slate-400 hover:text-slate-600 cursor-pointer"
                  aria-label={isEn ? "Close" : "Đóng"}
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Refund Info Cards */}
              <div className="space-y-3">
                {viewingRefundOrder.refunds.map((ref, idx) => {
                  const isProcessed = ref.status === "PROCESSED" || ref.status === "COMPLETED";
                  const isFailed = ref.status === "FAILED";
                  return (
                    <div
                      key={ref.id || ref.refundId || idx}
                      className="p-4 bg-slate-50 border border-slate-200/80 rounded-xl space-y-2.5 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-slate-500 font-medium">
                          {isEn ? "Refund Status:" : "Trạng thái hoàn:"}
                        </span>
                        <span
                          className={`px-2.5 py-0.5 rounded-md font-bold text-[11px] ${
                            isProcessed
                              ? "bg-slate-100 text-slate-800 border border-slate-200"
                              : isFailed
                              ? "bg-slate-100 text-slate-500 border border-slate-200"
                              : "bg-amber-50 text-amber-900 border border-amber-200"
                          }`}
                        >
                          {isProcessed
                            ? (isEn ? "Completed" : "Đã hoàn tất")
                            : isFailed
                            ? (isEn ? "Failed" : "Thất bại")
                            : (isEn ? "Pending Settlement" : "Đang chờ đối soát (Pending)")}
                        </span>
                      </div>

                      <div className="flex items-center justify-between">
                        <span className="text-slate-500 font-medium">{isEn ? "Refund Amount:" : "Số tiền hoàn:"}</span>
                        <span className="font-extrabold text-slate-900 text-sm tabular-nums">
                          {formatVnd(ref.amount)}
                        </span>
                      </div>

                      <div className="flex items-center justify-between">
                        <span className="text-slate-500 font-medium">{isEn ? "Refund Rate:" : "Tỷ lệ hoàn:"}</span>
                        <span className="font-bold text-blue-600 tabular-nums">
                          {ref.percentage ?? ref.refundPercentage ?? 100}%
                        </span>
                      </div>

                      <div className="flex items-center justify-between">
                        <span className="text-slate-500 font-medium">{isEn ? "Refund Gateway:" : "Cổng nhận hoàn:"}</span>
                        <span className="font-semibold text-slate-800">
                          {ref.provider || "VNPAY"} {isEn ? "(to original card/account)" : "(về thẻ/tài khoản gốc)"}
                        </span>
                      </div>

                      {ref.providerRefundId && (
                        <div className="flex items-center justify-between">
                          <span className="text-slate-500 font-medium">{isEn ? "Refund Transaction ID:" : "Mã giao dịch hoàn:"}</span>
                          <span className="font-mono font-bold text-slate-700">
                            #{ref.providerRefundId}
                          </span>
                        </div>
                      )}

                      <div className="flex items-center justify-between">
                        <span className="text-slate-500 font-medium">{isEn ? "Requested Date:" : "Ngày yêu cầu:"}</span>
                        <span className="text-slate-700">
                          {formatDateTime(ref.createdAt)}
                        </span>
                      </div>

                      {ref.processedAt && (
                        <div className="flex items-center justify-between">
                          <span className="text-slate-500 font-medium">{isEn ? "Completed Date:" : "Ngày hoàn tất:"}</span>
                          <span className="text-emerald-700 font-semibold">
                            {formatDateTime(ref.processedAt)}
                          </span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Progress Notice */}
              <div className="p-3 bg-blue-50/80 border border-blue-200/80 rounded-xl text-[11px] text-blue-900 space-y-1">
                <div className="flex items-center gap-1.5 font-bold text-blue-950">
                  <Clock className="w-3.5 h-3.5 text-blue-700" />
                  <span>{isEn ? "Estimated Settlement Time" : "Thời gian nhận tiền dự kiến"}</span>
                </div>
                <p className="leading-relaxed">
                  {isEn
                    ? "DANASEA has processed the refund. Actual arrival time depends on the banking network or payment provider."
                    : "DANASEA đã xác nhận hoàn tiền. Thời gian tiền về tài khoản phụ thuộc vào ngân hàng hoặc phương thức nhận tiền."}
                </p>
              </div>

              <div className="flex justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setViewingRefundOrder(null)}
                  className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors cursor-pointer"
                >
                  {isEn ? "Close" : "Đóng"}
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
      {paymentOrderId && (
        <ContinuePaymentDialog orderId={paymentOrderId} onClose={() => setPaymentOrderId(null)} />
      )}
    </main>
  );
}
