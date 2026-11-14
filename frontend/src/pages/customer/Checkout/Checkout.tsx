import { BookingStepper } from "../../../components/customer/BookingStepper";
import { useNavigate } from "react-router-dom";
import { useCartStore } from "../../../store/useCartStore";
import { useAuthStore } from "../../../store/useAuthStore";
import { useLanguageStore } from "../../../store/useLanguageStore";
import { bookingService } from "../../../services/bookingService";
import { orderService } from "../../../services/orderService";
import { customerDiscountService } from "../../../services/customerDiscountService";
import type { DiscountCodeResponse } from "../../../types/adminDiscountCode";
import { paymentService } from "../../../services/paymentService";
import type { BookingHoldResponse } from "../../../types/booking";
import type { DiscountPreviewResponse, OrderPaymentStatusResponse, PaymentProviderType } from "../../../types/order";
import { useState, useEffect, useRef } from "react";
import {
  getPendingOrder,
  savePendingOrder,
  savePurchasedItems,
} from "../../../utils/checkoutPaymentSession";
import {
  Timer,
  ShieldCheck,
  QrCode,
  CreditCard,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  Loader2,
  Wallet,
  Tag,
  X,
} from "lucide-react";

const VALID_UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const paymentMethods = [
  {
    provider: "VNPAY",
    name: "Cổng VNPAY (QR Pay / Thẻ ATM & Quốc tế)",
    nameEn: "VNPAY Gateway (QR Pay / ATM & International Cards)",
    description: "Hỗ trợ quét mã QR qua 30+ ứng dụng ngân hàng",
    descriptionEn: "Supports QR scanning via 30+ Vietnamese banking apps",
    icon: QrCode,
  },
  {
    provider: "PAYPAL",
    name: "Cổng PayPal (Thẻ quốc tế Visa, MasterCard)",
    nameEn: "PayPal Gateway (Visa, MasterCard, Amex)",
    description: "Dành cho du khách quốc tế hoặc thanh toán ngoại tệ",
    descriptionEn: "Recommended for international travelers and multi-currency",
    icon: CreditCard,
  },
  {
    provider: "PAYOS",
    name: "payOS (VietQR)",
    nameEn: "payOS (VietQR)",
    description: "Quét mã VietQR bằng ứng dụng ngân hàng",
    descriptionEn: "Instant VietQR code scanning with banking apps",
    icon: QrCode,
  },
  {
    provider: "ZALOPAY",
    name: "ZaloPay",
    nameEn: "ZaloPay Wallet",
    description: "Thanh toán qua ví điện tử ZaloPay",
    descriptionEn: "Fast checkout using ZaloPay e-wallet",
    icon: Wallet,
  },
] as const;

const defaultPaymentProviders: PaymentProviderType[] = ["VNPAY", "PAYPAL"];

interface CheckoutPaymentError {
  response?: {
    status?: number;
    data?: {
      code?: string;
      errorCode?: string;
      message?: string;
      missingWaivers?: Array<{ subOrderId: string; waiverVersion?: number; contentLanguage?: string }>;
    };
  };
}

export function Checkout() {
  const navigate = useNavigate();
  const { language } = useLanguageStore();
  const isEn = language === 'en';
  const { items, getTotalPrice } = useCartStore();
  const { user, isAuthenticated } = useAuthStore();

  const cartFingerprint = items.map(({ id, slotId, optionId, quantity }) =>
    JSON.stringify([id, slotId, optionId, quantity])).sort();
  const pendingOrderKey = `danasea_checkout_order:${JSON.stringify([user?.id, cartFingerprint])}`;
  const pendingOrder = getPendingOrder(pendingOrderKey);

  // Booking Hold State
  const [bookingHold, setBookingHold] = useState<BookingHoldResponse | null>(null);
  const [now, setNow] = useState(Date.now);
  const [pendingStatus, setPendingStatus] = useState<OrderPaymentStatusResponse & { orderId: string }>();
  const [sessionCode] = useState(() => Math.floor(10000 + Math.random() * 90000));

  // Customer Contact Info State
  const [fullName, setFullName] = useState<string>(user?.fullName || "Khách Hàng DANASEA");
  const [phone, setPhone] = useState<string>(user?.phone || "0912 345 678");
  const [email, setEmail] = useState<string>(user?.email || "khachhang@danasea.vn");
  const [notes, setNotes] = useState<string>("");

  const pendingOrderId = pendingOrder?.id;
  const paymentStatus = pendingStatus?.orderId === pendingOrderId ? pendingStatus : undefined;

  // Payment Method
  const [selectedProvider, setSelectedProvider] = useState<PaymentProviderType>();
  const [availableProviders, setAvailableProviders] = useState(defaultPaymentProviders);
  const visiblePaymentMethods = paymentMethods.filter(({ provider }) =>
    (isAuthenticated ? availableProviders : defaultPaymentProviders).includes(provider));
  const preferredProvider = selectedProvider ?? paymentStatus?.activeProvider ?? pendingOrder?.provider;
  const paymentProvider = visiblePaymentMethods.find(({ provider }) => provider === preferredProvider)?.provider
    ?? visiblePaymentMethods[0]?.provider;

  useEffect(() => {
    if (!isAuthenticated) return;
    const controller = new AbortController();
    paymentService.getAvailableProviders(controller.signal).then((providers) => {
      if (!controller.signal.aborted) setAvailableProviders(providers);
    }).catch(() => {
      if (!controller.signal.aborted) setAvailableProviders(defaultPaymentProviders);
    });
    return () => controller.abort();
  }, [isAuthenticated]);

  const deadline = pendingOrderId
    ? paymentStatus?.danaseaDeadlineAt
    : bookingHold?.holdExpiresAt || bookingHold?.expiresAt;
  const deadlineMillis = deadline ? Date.parse(deadline) : NaN;
  const countdown = Number.isFinite(deadlineMillis)
    ? Math.max(0, Math.ceil((deadlineMillis - now) / 1000))
    : undefined;
  const isHoldExpired = countdown === 0 || paymentStatus?.isExpired === true;

  useEffect(() => {
    if (!isAuthenticated || !pendingOrderId) return;
    const controller = new AbortController();
    paymentService.getOrderPaymentStatus(pendingOrderId, controller.signal).then((status) => {
      if (!controller.signal.aborted) setPendingStatus({ ...status, orderId: pendingOrderId });
    }).catch(() => {
      // The submit handler will check the authoritative status again.
    });
    return () => controller.abort();
  }, [isAuthenticated, pendingOrderId]);

  // Promo Code State
  const [promoInput, setPromoInput] = useState<string>("");
  const [discountPreview, setDiscountPreview] = useState<DiscountPreviewResponse | null>(null);
  const [appliedPromoCode, setAppliedPromoCode] = useState<string | null>(null);
  const [promoError, setPromoError] = useState<string>("");
  const [isVoucherModalOpen, setIsVoucherModalOpen] = useState(false);
  const [isApplyingPromo, setIsApplyingPromo] = useState<boolean>(false);
  const [availableVouchers, setAvailableVouchers] = useState<DiscountCodeResponse[]>([]);
  const [isLoadingVouchers, setIsLoadingVouchers] = useState<boolean>(false);

  // Payment processing state
  const [isProcessingPayment, setIsProcessingPayment] = useState<boolean>(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);

  // Redirect to cart if empty
  useEffect(() => {
    if (items.length === 0 && !isProcessingPayment) {
      navigate('/cart');
    }
  }, [items.length, isProcessingPayment, navigate]);

  // Update editable defaults only when the authenticated user object changes.
  const [previousUser, setPreviousUser] = useState(user);
  if (user !== previousUser) {
    setPreviousUser(user);
    if (user?.fullName) setFullName(user.fullName);
    if (user?.phone) setPhone(user.phone);
    if (user?.email) setEmail(user.email);
  }

  // Request backend slot hold on mount
  const holdRequestedRef = useRef<boolean>(false);
  const holdRequest = useRef<Promise<BookingHoldResponse> | null>(null);
  useEffect(() => {
    if (!isAuthenticated || items.length === 0 || holdRequestedRef.current || getPendingOrder(pendingOrderKey)) return;
    holdRequestedRef.current = true;

    const holdItems = items.map((i) => ({
      slotId: i.slotId,
      quantity: i.quantity,
      optionId: i.optionId,
      participantsCount: i.quantity,
      allowSplit: false,
    }));

    if (!items.every((item) => VALID_UUID_REGEX.test(item.slotId) && VALID_UUID_REGEX.test(item.serviceId))) {
      useCartStore.getState().clearCart();
      navigate('/cart');
      return;
    }

    const request = bookingService.holdBooking({ items: holdItems });
    holdRequest.current = request;
    request
      .then(setBookingHold)
      .catch((err) => {
        console.warn('[Checkout] Không thể giữ chỗ qua API:', err);
      })
      .finally(() => {
        if (holdRequest.current === request) holdRequest.current = null;
      });
  }, [items, isAuthenticated, navigate, pendingOrderKey]);

  useEffect(() => {
    if (!deadline) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [deadline]);

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  // Promo Code Handler
  // Load real available vouchers for customer
  useEffect(() => {
    if (!isVoucherModalOpen) return;
    let isMounted = true;
    setIsLoadingVouchers(true);
    customerDiscountService
      .getMyDiscountCodes({ size: 50 })
      .then((res) => {
        if (isMounted) {
          setAvailableVouchers(res.content || []);
        }
      })
      .catch((err) => {
        console.warn('[Checkout] Lỗi tải danh sách voucher thực tế:', err);
        if (isMounted) {
          setAvailableVouchers([]);
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoadingVouchers(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [isVoucherModalOpen]);

  const handleApplyPromo = async () => {
    setPromoError("");
    const code = promoInput.trim().toUpperCase();
    if (!code) return;

    setIsApplyingPromo(true);

    if (bookingHold?.bookingId) {
      try {
        const preview = await orderService.previewDiscount({
          bookingId: bookingHold.bookingId,
          code,
        });

        if (preview.valid) {
          setDiscountPreview(preview);
          setAppliedPromoCode(code);
        } else {
          setPromoError(preview.message || "Mã khuyến mãi không hợp lệ hoặc không áp dụng cho đơn này.");
        }
      } catch (err: unknown) {
        console.warn('[Checkout] Lỗi áp dụng voucher từ hệ thống:', err);
        setPromoError("Mã khuyến mãi không tồn tại hoặc đã hết hạn.");
      } finally {
        setIsApplyingPromo(false);
      }
    } else {
      setPromoError("Chưa có phiên giữ chỗ hợp lệ để áp dụng mã.");
      setIsApplyingPromo(false);
    }
  };

  const handleRemovePromo = () => {
    setDiscountPreview(null);
    setAppliedPromoCode(null);
    setPromoInput("");
    setPromoError("");
  };

  // Calculate final total
  const rawTotal = getTotalPrice();
  const discountAmount = discountPreview?.totalDiscountAmount || 0;
  const finalTotal = Math.max(0, rawTotal - discountAmount);

  // Submit Payment Handler
  const handlePayment = async () => {
    if (isHoldExpired) {
      setPaymentError("Thời hạn đã kết thúc. Vui lòng quay lại giỏ hàng để chọn lại suất.");
      return;
    }
    if (!paymentProvider || isProcessingPayment) return;

    setIsProcessingPayment(true);
    setPaymentError(null);

    if (isAuthenticated) {
      try {

        // Resume the same order after an uncertain payment submission or browser refresh.
        let orderRes;
        if (pendingOrder) {
          try {
            orderRes = await orderService.getOrderDetail(pendingOrder.id);
          } catch (error) {
            const { status } = (error as CheckoutPaymentError).response ?? {};
            if (status === 403 || status === 404) savePendingOrder(pendingOrderKey, null);
            throw error;
          }
          if (orderRes.status !== "PENDING_PAYMENT") {
            savePendingOrder(pendingOrderKey, null);
            setBookingHold(null);
            setIsProcessingPayment(false);
            setPaymentError("Đơn hàng này đã được xử lý. Vui lòng kiểm tra trạng thái trong mục Đơn hàng.");
            return;
          }
        } else {
          let activeHold = bookingHold ?? await holdRequest.current;
          if (!activeHold?.bookingId) {
            activeHold = await bookingService.holdBooking({ items: items.map((item) => ({
              slotId: item.slotId,
              quantity: item.quantity,
              optionId: item.optionId,
              participantsCount: item.quantity,
              allowSplit: false,
            })) });
            setBookingHold(activeHold);
          }
          orderRes = await orderService.createOrder({
            bookingId: activeHold.bookingId,
            discountCode: appliedPromoCode || undefined,
          });
        }
        const orderId = orderRes.id;
        const currentStatus = pendingOrder
          ? await paymentService.getOrderPaymentStatus(orderId)
          : undefined;
        if (currentStatus) {
          setPendingStatus({ ...currentStatus, orderId });
          if (currentStatus.isExpired || currentStatus.orderStatus !== "PENDING_PAYMENT"
              || currentStatus.paymentStatus !== "UNPAID") {
            setIsProcessingPayment(false);
            setPaymentError(currentStatus.isExpired
              ? "Đơn hàng đã hết hạn thanh toán. Vui lòng quay lại giỏ hàng để chọn lại suất."
              : "Đơn hàng đã được xử lý. Vui lòng kiểm tra trạng thái trong mục Đơn hàng.");
            return;
          }
        }
        if (currentStatus?.activeProvider && currentStatus.activeProvider !== paymentProvider
            && currentStatus.activeProvider !== paymentStatus?.activeProvider) {
          setIsProcessingPayment(false);
          setPaymentError("Phương thức đang giữ giao dịch đã thay đổi. Vui lòng kiểm tra lựa chọn trước khi đổi cổng.");
          return;
        }
        savePendingOrder(pendingOrderKey, { id: orderId, provider: paymentProvider });

        // 2. Synchronize safety waiver acceptance for all sub-orders requiring it
        const orderSubOrders = orderRes.items || orderRes.subOrders || [];
        const pendingWaivers = orderSubOrders.filter(
          (sub) => sub.waiverRequired && !sub.waiverAccepted
        );

        if (pendingWaivers.length > 0) {
          await Promise.all(
            pendingWaivers.map((sub) => {
              const subId = sub.id || sub.subOrderId;
              if (!subId) return Promise.resolve();
              return orderService.acceptSubOrderWaiver(subId, {
                accepted: true,
                version: sub.waiverVersion || 1,
                language: sub.waiverLanguage || 'VI',
              });
            })
          );
        }

        // 3. Create Payment Intent
        const submitIntent = () => currentStatus
          ? paymentService.continuePayment(orderId, { provider: paymentProvider }, currentStatus)
          : paymentService.createPaymentIntent(orderId, { provider: paymentProvider });

        let payRes;
        try {
          payRes = await submitIntent();
        } catch (error) {
          const data = (error as CheckoutPaymentError).response?.data;
          if ((data?.errorCode ?? data?.code) === "WAIVER_ACCEPTANCE_REQUIRED" && Array.isArray(data?.missingWaivers)) {
            await Promise.all(
              data.missingWaivers.map((m) =>
                orderService.acceptSubOrderWaiver(m.subOrderId, {
                  accepted: true,
                  version: m.waiverVersion || 1,
                  language: m.contentLanguage || 'VI',
                })
              )
            );
            payRes = await submitIntent();
          } else {
            throw error;
          }
        }

        // 4. Backend returns paymentReference (which contains the gateway redirect URL)
        const gatewayUrl = payRes.paymentUrl || payRes.paymentReference;
        if (gatewayUrl && (gatewayUrl.startsWith("http://") || gatewayUrl.startsWith("https://"))) {
          savePurchasedItems(payRes.paymentId, items.map(({ id, quantity }) => ({ id, quantity })));
          window.location.assign(gatewayUrl);
          return;
        } else {
          setIsProcessingPayment(false);
          setPaymentError(isEn ? "Failed to get payment link from gateway. Please try again or select another method." : "Không nhận được liên kết thanh toán từ cổng. Vui lòng thử lại hoặc chọn phương thức khác.");
          return;
        }
      } catch (err) {
        const error = err as CheckoutPaymentError;
        const code = error.response?.data?.code ?? error.response?.data?.errorCode;
        if (code === "PAYMENT_WINDOW_EXPIRED") {
          savePendingOrder(pendingOrderKey, null);
          setBookingHold(null);
        }
        setIsProcessingPayment(false);
        setPaymentError(error.response?.data?.message || (isEn ? "Failed to initialize payment transaction. Please check your connection and retry." : "Không thể khởi tạo giao dịch thanh toán. Vui lòng kiểm tra lại kết nối và thử lại."));
        return;
      }
    } else {
      setIsProcessingPayment(false);
      setPaymentError(isEn ? "Please log in with your customer account to complete payment." : "Vui lòng đăng nhập tài khoản Khách hàng để tiến hành thanh toán vé.");
      return;
    }
  };

  if (items.length === 0) return null;

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar */}
        <section className="w-full bg-slate-50/70 border-b border-slate-200/80">
          <BookingStepper currentStep={2} />
        </section>

        {/* Main Checkout Area */}
        <div className="w-full max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="pb-6 border-b border-slate-200/80 mb-8">
            <h1 className="text-2xl sm:text-[26px] font-bold text-slate-900 tracking-tight">
              {isEn ? "Review Booking & Checkout" : "Xác nhận thông tin & Thanh toán"}
            </h1>
            <p className="text-[13px] text-slate-600 mt-1">
              {isEn 
                ? "Review your marine itinerary, traveler details, and select a secure payment method"
                : "Kiểm tra lịch trình biển Sơn Trà, người tham gia và lựa chọn phương thức thanh toán an toàn"}
            </p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            
            {/* LEFT COLUMN: Checkout Forms */}
            <div className="lg:col-span-7 flex flex-col gap-6">
              {paymentError && (
                <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-medium flex items-center gap-2.5">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>{paymentError}</span>
                </div>
              )}

              {/* Form Container Card */}
              <div className="w-full rounded-2xl bg-white p-6 shadow-xs border border-slate-200/80 flex flex-col gap-6">
                {/* Hold Timer Header */}
                <div className="flex items-center justify-between pb-4 border-b border-slate-100 flex-wrap gap-3">
                  {isHoldExpired ? (
                    <div className="flex items-center gap-2 text-rose-600 font-bold text-sm">
                      <Timer className="w-4 h-4 text-rose-600" />
                      <span>{pendingOrderId ? (isEn ? "Payment expired" : "Hết hạn thanh toán") : (isEn ? "Hold expired" : "Hết thời gian giữ chỗ")}</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 text-slate-900 text-sm">
                      <Timer className="w-4 h-4 text-slate-700" />
                      <span className="font-medium text-slate-700">
                        {pendingOrderId ? (isEn ? "Payment window" : "Thời gian thanh toán") : (isEn ? "Hold time" : "Thời gian giữ chỗ")}
                      </span>
                      <span className="font-bold font-mono text-base text-slate-900 tabular-nums">
                        {countdown === undefined ? "—" : formatTime(countdown)}
                      </span>
                    </div>
                  )}
                  <div className="text-xs font-mono text-slate-600">
                    {isEn ? "Session:" : "Mã phiên:"} <span className="font-bold text-slate-900">DNS-{sessionCode}</span>
                  </div>
                </div>

                {/* Section 1: Customer Contact Info */}
                <div className="flex flex-col gap-4">
                  <div className="flex items-center justify-between border-b pb-2 border-slate-100">
                    <h2 className="text-base sm:text-lg font-bold text-slate-900">
                      {isEn ? "1. Lead Traveler Information" : "1. Thông tin người đặt vé"}
                    </h2>
                    {isAuthenticated && (
                      <span className="text-xs text-emerald-700 font-medium flex items-center gap-1 bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-200">
                        <CheckCircle2 className="w-3.5 h-3.5" /> {isEn ? "Synced from account" : "Đã đồng bộ tài khoản"}
                      </span>
                    )}
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-semibold text-slate-700">{isEn ? "Full Name *" : "Họ và tên *"}</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="text" 
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder={isEn ? "Enter full name" : "Nhập họ và tên"}
                      />
                    </div>
                    
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-semibold text-slate-700">{isEn ? "Contact Phone Number *" : "Số điện thoại liên hệ *"}</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="tel" 
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder={isEn ? "Phone number for ticket confirmation" : "Số điện thoại nhận vé"}
                      />
                    </div>

                    <div className="flex flex-col gap-1.5 sm:col-span-2">
                      <label className="text-xs font-semibold text-slate-700">{isEn ? "Email Address for QR Pass *" : "Địa chỉ Email nhận mã QR vé *"}</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="email" 
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder={isEn ? "Email for booking confirmation" : "Email nhận xác nhận đặt chỗ"}
                      />
                    </div>

                    <div className="flex flex-col gap-1.5 sm:col-span-2">
                      <label className="text-xs font-semibold text-slate-700">{isEn ? "Special Requests (optional)" : "Ghi chú đặc biệt (tùy chọn)"}</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="text" 
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        placeholder={isEn ? "Life jacket sizing, photography requests..." : "Yêu cầu kích thước áo phao, hỗ trợ quay chụp..."}
                      />
                    </div>
                  </div>
                </div>

                {/* Section 2: Payment Provider */}
                <div className="flex flex-col gap-4 pt-2 border-t border-slate-100">
                  <div className="border-b pb-2 border-slate-100">
                    <h2 className="text-base sm:text-lg font-bold text-slate-900">
                      {isEn ? "2. Payment Method" : "2. Phương thức thanh toán"}
                    </h2>
                  </div>

                  <div className="flex flex-col gap-3">
                    {visiblePaymentMethods.map((method) => {
                      const { provider, icon: Icon } = method;
                      const methodName = isEn ? method.nameEn : method.name;
                      const methodDesc = isEn ? method.descriptionEn : method.description;
                      return (
                        <label
                          key={provider}
                          className={`p-4 rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                            paymentProvider === provider
                              ? "border-slate-900 bg-slate-50/80 shadow-xs"
                              : "border-slate-200 bg-white hover:bg-slate-50"
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <input
                              checked={paymentProvider === provider}
                              onChange={() => setSelectedProvider(provider)}
                              disabled={isProcessingPayment || !paymentProvider}
                              className="w-4 h-4 accent-slate-900 cursor-pointer"
                              name="payment_method"
                              type="radio"
                              value={provider}
                            />
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-bold text-slate-900">{methodName}</span>
                                {provider === "VNPAY" && (
                                  <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[10px] font-bold border border-emerald-200">
                                    {isEn ? "Popular" : "Phổ biến"}
                                  </span>
                                )}
                              </div>
                              <p className="text-xs text-slate-500 mt-0.5">{methodDesc}</p>
                            </div>
                          </div>
                          <Icon className="w-5 h-5 text-slate-700 shrink-0" aria-hidden="true" />
                        </label>
                      );
                    })}
                    {!paymentProvider && <p className="text-sm text-slate-600">{isEn ? "No payment methods currently available." : "Hiện chưa có phương thức thanh toán khả dụng."}</p>}
                  </div>
                </div>

                {/* Safety Guarantee Footnote */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5 text-xs">
                  <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                  <div className="text-slate-600 leading-relaxed">
                    <p className="font-bold text-slate-900">{isEn ? "Traveler Protection Policy" : "Chính sách bảo vệ quyền lợi du khách"}</p>
                    <p className="mt-0.5">
                      {isEn
                        ? "100% refund guarantee for severe weather or port authority cancellations. All payment data is encrypted via secure SSL."
                        : "Cam kết hoàn tiền 100% khi thời tiết biển động hoặc cảng vụ cấm xuất bến. Mọi thông tin thanh toán đều được mã hóa SSL an toàn."}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN: Sticky Order Summary & Price Breakdown */}
            <aside className="lg:col-span-5 flex flex-col gap-4 lg:sticky lg:top-24">
              <div className="p-6 rounded-2xl bg-white shadow-xs border border-slate-200/80 flex flex-col gap-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <h3 className="text-lg font-bold text-slate-900">{isEn ? "Order Summary" : "Tóm tắt đơn hàng"}</h3>
                  <span className="text-xs text-slate-600 font-medium">
                    {items.length} {isEn ? (items.length > 1 ? "experiences" : "experience") : "trải nghiệm"}
                  </span>
                </div>

                {/* Items List */}
                <div className="flex flex-col gap-3">
                  {items.map((item) => {
                    const title = item.serviceName || (isEn ? 'Marine Experience' : 'Trải nghiệm biển');
                    const image = item.thumbnailUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800';
                    const location = item.locationName || 'Đà Nẵng';
                    const dateStr = item.selectedDate || item.date || (isEn ? 'Today' : 'Hôm nay');
                    const timeStr = item.timeSlot || '08:00 - 10:00';

                    return (
                      <div key={item.id} className="flex gap-3 items-start pb-3 border-b border-slate-100 last:border-0 last:pb-0">
                        <div className="w-16 h-16 rounded-xl overflow-hidden shrink-0 bg-slate-100">
                          <img className="w-full h-full object-cover" alt={title} src={image} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between gap-1">
                            <span className="text-[11px] text-slate-500 truncate">{location}</span>
                            <span className="font-bold text-xs sm:text-sm text-slate-900 tabular-nums">
                              {(item.unitPrice * item.quantity).toLocaleString('vi-VN')}₫
                            </span>
                          </div>
                          <h4 className="font-semibold text-xs sm:text-sm text-slate-900 leading-tight mt-0.5 truncate">
                            {title}
                          </h4>
                          <p className="text-xs text-slate-500 mt-1">
                            {timeStr} • {dateStr}
                          </p>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {item.quantity} {isEn ? (item.quantity > 1 ? 'guests' : 'guest') : 'khách'} × {item.unitPrice.toLocaleString('vi-VN')}₫
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
                
                {/* Promo Code Input */}
                <div className="pt-3 border-t border-slate-100">
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-semibold text-slate-700">{isEn ? "Promo Code" : "Mã ưu đãi chuyến đi"}</label>
                    <button
                      type="button"
                      onClick={() => setIsVoucherModalOpen(true)}
                      className="text-xs text-[#0284c7] hover:text-[#0369a1] font-semibold underline underline-offset-4 cursor-pointer transition-colors"
                    >
                      {isEn ? "View Available Vouchers" : "Xem mã ưu đãi"}
                    </button>
                  </div>
                  {appliedPromoCode ? (
                    <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                        <span className="font-bold text-emerald-900">{appliedPromoCode}</span>
                        <span className="text-emerald-700">(-{discountAmount.toLocaleString('vi-VN')}₫)</span>
                      </div>
                      <button
                        onClick={handleRemovePromo}
                        className="text-slate-600 hover:text-rose-600 font-semibold cursor-pointer"
                        type="button"
                      >
                        {isEn ? "Remove" : "Gỡ bỏ"}
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={promoInput}
                          onChange={(e) => setPromoInput(e.target.value)}
                          placeholder={isEn ? "Enter promo code..." : "Nhập mã ưu đãi..."}
                          className="flex-1 px-3 py-2 rounded-xl border border-slate-200 text-xs text-slate-900 uppercase focus:outline-none focus:border-slate-400 bg-slate-50"
                        />
                        <button
                          onClick={handleApplyPromo}
                          disabled={isApplyingPromo || !promoInput.trim()}
                          type="button"
                          className="px-4 py-2 bg-[#0284c7] hover:bg-[#0369a1] disabled:opacity-50 text-white rounded-xl text-xs font-semibold cursor-pointer transition-colors shadow-xs"
                        >
                          {isApplyingPromo ? (isEn ? 'Checking...' : 'Đang kiểm tra...') : (isEn ? 'Apply' : 'Áp dụng')}
                        </button>
                      </div>
                      {promoError && <p className="text-xs text-rose-600">{promoError}</p>}
                    </div>
                  )}
                </div>
                
                {/* Subtotal & Price breakdown */}
                <div className="flex flex-col gap-2 pt-3 border-t border-slate-100 text-xs text-slate-600">
                  <div className="flex justify-between items-center text-sm">
                    <span>{isEn ? "Subtotal" : "Tạm tính"}</span>
                    <span className="text-slate-900 font-semibold tabular-nums">
                      {rawTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>
                  {appliedPromoCode && (
                    <div className="flex justify-between items-center text-sm text-emerald-700 font-bold">
                      <span>{isEn ? `Discount (${appliedPromoCode})` : `Ưu đãi áp dụng (${appliedPromoCode})`}</span>
                      <span className="tabular-nums">-{discountAmount.toLocaleString('vi-VN')}₫</span>
                    </div>
                  )}
                  <div className="flex justify-between items-center text-xs text-slate-500">
                    <span>{isEn ? "Service Fee" : "Phí dịch vụ"}</span>
                    <span className="text-emerald-700 font-medium">{isEn ? "Free" : "Miễn phí"}</span>
                  </div>
                </div>

                {/* Total Payment & Action Button */}
                <div className="pt-3 border-t border-slate-100 flex flex-col gap-4">
                  <div className="flex justify-between items-baseline">
                    <div>
                      <span className="text-xs text-slate-700 font-semibold">{isEn ? "Total Payment:" : "Tổng thanh toán:"}</span>
                      <p className="text-xs text-slate-500">{isEn ? "Includes taxes & marine insurance" : "Đã gồm thuế & bảo hiểm"}</p>
                    </div>
                    <span className="text-2xl sm:text-3xl leading-tight text-[#991b1b] font-extrabold tracking-tight tabular-nums">
                      {finalTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>

                  {isHoldExpired ? (
                    <button
                      onClick={() => {
                        if (pendingOrderId) savePendingOrder(pendingOrderKey, null);
                        navigate('/cart');
                      }}
                      type="button"
                      className="w-full py-3.5 rounded-xl font-bold text-sm bg-rose-600 hover:bg-rose-700 text-white shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-colors"
                    >
                      <ArrowLeft className="w-4 h-4" />
                      <span>{isEn ? "Back to Cart" : "Quay lại giỏ hàng"}</span>
                    </button>
                  ) : (
                    <button 
                      onClick={handlePayment} 
                      disabled={isProcessingPayment || !paymentProvider}
                      className={`w-full py-3.5 rounded-xl font-bold text-sm shadow-xs transition-colors flex items-center justify-center cursor-pointer ${
                        !isProcessingPayment
                          ? 'bg-[#0284c7] hover:bg-[#0369a1] text-white active:scale-95'
                          : 'bg-slate-100 text-slate-600 border border-slate-300 cursor-not-allowed'
                      }`} 
                      type="button"
                    >
                      {isProcessingPayment ? (
                        <span className="flex items-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          <span>{isEn ? "Connecting to payment gateway..." : "Đang kết nối cổng thanh toán..."}</span>
                        </span>
                      ) : (
                        <span>
                          {paymentStatus?.activeProvider && paymentStatus.activeProvider !== paymentProvider
                            ? (isEn ? "Switch Gateway & Pay" : "Đổi cổng và thanh toán")
                            : (isEn ? "Confirm & Pay" : "Xác nhận & Thanh toán")}
                        </span>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </aside>
          </div>
        </div>
      </div>
      {/* Voucher Selection Modal */}
      {isVoucherModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <Tag className="w-5 h-5 text-[#0284c7]" />
                <h3 className="text-base font-bold text-slate-900">{isEn ? "Available DANASEA Vouchers" : "Mã ưu đãi DANASEA khả dụng"}</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsVoucherModalOpen(false)}
                className="w-8 h-8 rounded-lg hover:bg-slate-100 flex items-center justify-center text-slate-500 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="py-4 space-y-3 max-h-[60vh] overflow-y-auto">
              {isLoadingVouchers ? (
                <div className="p-8 text-center text-xs text-slate-500">
                  {isEn ? "Checking available vouchers..." : "Đang kiểm tra mã ưu đãi khả dụng..."}
                </div>
              ) : availableVouchers.length > 0 ? (
                availableVouchers.map((voucher) => (
                  <div
                    key={voucher.id}
                    className="p-3.5 rounded-xl border border-slate-200 bg-slate-50/70 hover:bg-slate-100 transition-all flex items-center justify-between gap-3"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="px-2 py-0.5 rounded-md bg-slate-200 text-slate-800 font-mono font-bold text-xs">
                          {voucher.code}
                        </span>
                        <span className="text-xs font-bold text-[#991b1b]">
                          {voucher.discountType === 'PERCENTAGE'
                            ? (isEn ? `${voucher.discountValue}% OFF` : `Giảm ${voucher.discountValue}%`)
                            : (isEn ? `${voucher.discountValue.toLocaleString('vi-VN')}₫ OFF` : `Giảm ${voucher.discountValue.toLocaleString('vi-VN')}₫`)}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 line-clamp-1 mt-0.5">
                        {voucher.minOrderAmount
                          ? (isEn ? `Min order ${voucher.minOrderAmount.toLocaleString('vi-VN')}₫` : `Đơn tối thiểu ${voucher.minOrderAmount.toLocaleString('vi-VN')}₫`)
                          : (isEn ? 'Valid for all marine activities' : 'Áp dụng cho đơn dịch vụ biển')}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setPromoInput(voucher.code);
                        setIsVoucherModalOpen(false);
                      }}
                      className="px-3 py-1.5 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-semibold shrink-0 cursor-pointer transition-colors shadow-xs"
                    >
                      {isEn ? "Apply" : "Áp dụng"}
                    </button>
                  </div>
                ))
              ) : (
                <div className="p-8 text-center flex flex-col items-center justify-center space-y-2.5">
                  <div className="w-10 h-10 rounded-2xl bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-400">
                    <Tag className="w-5 h-5" />
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs font-bold text-slate-800">
                      {isEn ? "No personal vouchers yet" : "Chưa có mã ưu đãi cá nhân"}
                    </p>
                    <p className="text-[11px] text-slate-500 max-w-xs leading-relaxed">
                      {isEn 
                        ? "You currently have no personal vouchers or compensation credits. If you have an event promo code, enter it directly into the input above."
                        : "Bạn hiện chưa có mã giảm giá cá nhân hoặc voucher đền bù nào khả dụng. Nếu có mã từ chương trình ưu đãi, bạn có thể nhập trực tiếp tại ô bên ngoài."}
                    </p>
                  </div>
                </div>
              )}
            </div>

            <div className="pt-3 border-t border-slate-100 flex justify-end">
              <button
                type="button"
                onClick={() => setIsVoucherModalOpen(false)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 cursor-pointer"
              >
                {isEn ? "Close" : "Đóng"}
              </button>
            </div>
          </div>
        </div>
      )}

    </main>
  );
}
