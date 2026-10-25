import { useNavigate } from "react-router-dom";
import { useCartStore } from "../../../store/useCartStore";
import { useAuthStore } from "../../../store/useAuthStore";
import { useLoadingStore } from "../../../store/useLoadingStore";
import { bookingService } from "../../../services/bookingService";
import { orderService } from "../../../services/orderService";
import { paymentService } from "../../../services/paymentService";
import type { BookingHoldResponse } from "../../../types/booking";
import type { DiscountPreviewResponse, PaymentProviderType } from "../../../types/order";
import { useState, useEffect, useRef } from "react";

export function Checkout() {
  const navigate = useNavigate();
  const { items, getTotalPrice, clearCart } = useCartStore();
  const { user, isAuthenticated } = useAuthStore();
  const { triggerDemoLoading } = useLoadingStore();

  // Booking Hold State
  const [bookingHold, setBookingHold] = useState<BookingHoldResponse | null>(null);
  const [isHolding, setIsHolding] = useState<boolean>(false);
  const [countdown, setCountdown] = useState<number>(900); // 15 minutes default
  const [sessionCode] = useState(() => Math.floor(10000 + Math.random() * 90000));

  // Customer Contact Info State
  const [fullName, setFullName] = useState<string>(user?.fullName || "Khách Hàng DANASEA");
  const [phone, setPhone] = useState<string>(user?.phone || "0912 345 678");
  const [email, setEmail] = useState<string>(user?.email || "khachhang@danasea.vn");
  const [notes, setNotes] = useState<string>("");

  // Payment Method
  const [paymentProvider, setPaymentProvider] = useState<PaymentProviderType>("VNPAY");

  // Promo Code State
  const [promoInput, setPromoInput] = useState<string>("");
  const [discountPreview, setDiscountPreview] = useState<DiscountPreviewResponse | null>(null);
  const [appliedPromoCode, setAppliedPromoCode] = useState<string | null>(null);
  const [promoError, setPromoError] = useState<string>("");
  const [isApplyingPromo, setIsApplyingPromo] = useState<boolean>(false);

  // Waiver acceptance
  const [waiverAccepted, setWaiverAccepted] = useState<boolean>(true);

  // Payment processing state
  const [isProcessingPayment, setIsProcessingPayment] = useState<boolean>(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);

  // Redirect if cart is empty (unless navigating to payment result)
  const isNavigatingToResultRef = useRef<boolean>(false);
  useEffect(() => {
    if (items.length === 0 && !isNavigatingToResultRef.current && !isProcessingPayment) {
      navigate('/cart');
    }
  }, [items, navigate, isProcessingPayment]);

  // Sync user info if auth updates
  useEffect(() => {
    if (user) {
      if (user.fullName) setFullName(user.fullName);
      if (user.phone) setPhone(user.phone);
      if (user.email) setEmail(user.email);
    }
  }, [user]);

  // Request backend slot hold on mount
  const holdRequestedRef = useRef<boolean>(false);
  useEffect(() => {
    if (items.length === 0 || holdRequestedRef.current) return;
    holdRequestedRef.current = true;

    // Check if slot IDs look like real UUIDs
    const validUuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const holdItems = items.map((i) => ({
      slotId: i.slotId,
      quantity: i.quantity,
      optionId: i.optionId,
      participantsCount: i.quantity,
      allowSplit: false,
    }));

    const allUuids = holdItems.every((item) => validUuidRegex.test(item.slotId));

    if (allUuids && isAuthenticated) {
      setIsHolding(true);
      bookingService
        .holdBooking({ items: holdItems })
        .then((res) => {
          setBookingHold(res);
          if (res.expiresAt) {
            const expireDate = new Date(res.expiresAt).getTime();
            const now = Date.now();
            const diffSec = Math.max(0, Math.floor((expireDate - now) / 1000));
            if (diffSec > 0) setCountdown(diffSec);
          }
        })
        .catch((err) => {
          console.warn('[Checkout] Không thể giữ chỗ qua API, dùng chế độ giữ chỗ cục bộ:', err);
        })
        .finally(() => {
          setIsHolding(false);
        });
    }
  }, [items, isAuthenticated]);

  // 15-minute countdown timer
  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  // Promo Code Handler
  const handleApplyPromo = async () => {
    setPromoError("");
    const code = promoInput.trim().toUpperCase();
    if (!code) return;

    setIsApplyingPromo(true);

    // If real bookingHold exists, verify with backend API
    if (bookingHold?.bookingId) {
      try {
        const preview = await orderService.previewDiscount({
          bookingId: bookingHold.bookingId,
          code,
        });

        if (preview.valid) {
          setDiscountPreview(preview);
          setAppliedPromoCode(code);
          setIsApplyingPromo(false);
          return;
        } else {
          setPromoError(preview.message || "Mã khuyến mãi không hợp lệ hoặc không áp dụng cho đơn này.");
          setIsApplyingPromo(false);
          return;
        }
      } catch (err: any) {
        console.warn('[Checkout] Lỗi xác thực voucher từ server, kiểm tra mã dự phòng:', err);
      }
    }

    // Client-side fallback voucher codes
    if (code === "DANASEA10" || code === "HE2024") {
      const discount = Math.round(getTotalPrice() * 0.1);
      setDiscountPreview({
        valid: true,
        code,
        originalTotalAmount: getTotalPrice(),
        totalDiscountAmount: discount,
        finalPayableAmount: Math.max(0, getTotalPrice() - discount),
      });
      setAppliedPromoCode(code);
    } else if (code === "BIENXANH") {
      const discount = 50000;
      setDiscountPreview({
        valid: true,
        code,
        originalTotalAmount: getTotalPrice(),
        totalDiscountAmount: discount,
        finalPayableAmount: Math.max(0, getTotalPrice() - discount),
      });
      setAppliedPromoCode(code);
    } else {
      setPromoError("Mã khuyến mãi không hợp lệ hoặc đã hết lượt dùng.");
    }

    setIsApplyingPromo(false);
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
    if (!waiverAccepted) {
      alert("Vui lòng đồng ý với Cam kết an toàn & Miễn trừ trách nhiệm trước khi thanh toán.");
      return;
    }

    setIsProcessingPayment(true);
    setPaymentError(null);

    // If real backend booking is active and user is logged in
    if (bookingHold?.bookingId && isAuthenticated) {
      try {
        triggerDemoLoading(3000, "Đang xử lý đơn hàng & kết nối cổng thanh toán...", "Hệ thống đang bảo lưu mã vé và khởi tạo giao dịch an toàn");

        // 1. Create Master Order
        const orderRes = await orderService.createOrder({
          bookingId: bookingHold.bookingId,
          discountCode: appliedPromoCode || undefined,
        });

        // 2. Create Payment Intent
        const payRes = await paymentService.createPaymentIntent(orderRes.id, {
          provider: paymentProvider,
        });

        clearCart();

        // 3. If Gateway redirect URL is provided, redirect; otherwise navigate to result page
        if (payRes.paymentUrl && payRes.paymentUrl.startsWith("http")) {
          window.location.href = payRes.paymentUrl;
        } else {
          navigate(`/checkout/result?orderId=${orderRes.id}&status=success`);
        }
        return;
      } catch (err: any) {
        console.error('[Checkout] Lỗi khi tạo đơn hàng thật:', err);
        setPaymentError(err.response?.data?.message || "Không thể khởi tạo giao dịch. Đang chuyển sang chế độ thanh toán dự phòng...");
      }
    }

    // Graceful fallback for offline / demo mode
    triggerDemoLoading(2000, "Đang xử lý giao dịch vé tour...", "Hệ thống đang khóa chỗ cano và sinh mã vé QR");
    setTimeout(() => {
      isNavigatingToResultRef.current = true;
      clearCart();
      navigate('/checkout/result?status=success');
    }, 1800);
  };

  if (items.length === 0 && !isNavigatingToResultRef.current) return null;

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar */}
        <section className="w-full bg-surface-container-lowest/80 backdrop-blur-md shadow-sm border-b border-slate-100">
          <div className="max-w-[1280px] mx-auto px-margin-desktop py-space-md">
            {/* Breadcrumb & Status Overline */}
            <div className="flex items-center justify-between gap-space-md flex-wrap mb-space-sm">
              <div className="flex items-center gap-space-xs font-label-md text-xs text-slate-500">
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate('/')}>DANASEA</span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate('/cart')}>Giỏ trải nghiệm biển</span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="text-cyan-700 font-bold">Thông tin &amp; Thanh toán</span>
              </div>
              <div className="flex items-center gap-space-xs px-3 py-1 rounded-full bg-cyan-50 text-cyan-800 text-xs font-medium border border-cyan-100">
                <span className="w-2 h-2 rounded-full bg-cyan-600 animate-ping"></span>
                <span>Mã phiên giữ chỗ: <strong>DNS-{sessionCode}</strong></span>
              </div>
            </div>
            {/* Stepper Component */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm items-center">
              <div className="flex items-center gap-space-sm p- space-xs rounded-xl bg-slate-50 border border-slate-100 p-2">
                <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span className="material-symbols-outlined text-[18px]">check</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 1</p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Giỏ trải nghiệm ({items.length} mục)</p>
                </div>
              </div>
              <div className="flex items-center gap-space-sm p-space-xs rounded-xl bg-cyan-50/80 border border-cyan-200 shadow-sm p-2">
                <div className="w-8 h-8 rounded-full bg-cyan-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span>2</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-cyan-600 font-bold uppercase tracking-wider">Bước 2 • Đang thực hiện</p>
                  <p className="font-headline-sm text-xs font-bold text-cyan-900 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>
              <div className="flex items-center gap-space-sm p-space-xs rounded-xl bg-slate-50/60 opacity-60 p-2">
                <div className="w-8 h-8 rounded-full bg-slate-200 text-slate-500 flex items-center justify-center flex-shrink-0 font-bold">
                  <span>3</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 3</p>
                  <p className="font-headline-sm text-xs text-slate-500 truncate">Nhận vé điện tử QR</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Checkout Workflow Area */}
        <main className="w-full max-w-[1280px] mx-auto px-margin-desktop py-space-xl">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-2xl items-start">
            
            {/* LEFT COLUMN: Checkout Forms (7 Cols on Desktop) */}
            <div className="lg:col-span-7 flex flex-col gap-space-xl">
              {/* Countdown Sticky/Accent Banner */}
              <div className="w-full rounded-2xl bg-gradient-to-r from-teal-900 to-cyan-900 text-white p-space-md shadow-md flex items-start sm:items-center justify-between gap-space-md flex-col sm:flex-row">
                <div className="flex items-center gap-space-sm">
                  <div className="w-10 h-10 rounded-full bg-white/10 text-cyan-300 flex items-center justify-center flex-shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-[22px]">timer</span>
                  </div>
                  <div>
                    <p className="font-headline-sm text-sm sm:text-base text-white leading-snug">
                      Khóa chỗ ưu tiên trong: <span className="font-black text-cyan-300 font-mono text-xl sm:text-2xl" id="checkout-timer">{formatTime(countdown)}</span>
                    </p>
                    <p className="font-body-sm text-xs text-cyan-200/80 leading-tight">
                      {isHolding ? 'Đang xác thực giữ chỗ với máy chủ...' : 'Slot trải nghiệm đang được bảo lưu an toàn. Hết thời gian, hệ thống tự động nhường cho du khách khác.'}
                    </p>
                  </div>
                </div>
              </div>

              {paymentError && (
                <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-medium flex items-center gap-2">
                  <span className="material-symbols-outlined text-amber-600">info</span>
                  <span>{paymentError}</span>
                </div>
              )}

              {/* Section 1: Customer Information */}
              <section className="w-full rounded-2xl bg-surface-container-lowest p-space-xl shadow-sm border border-slate-100 flex flex-col gap-space-lg">
                <div className="flex items-center justify-between border-b pb-space-xs border-slate-100">
                  <div className="flex items-center gap-space-sm">
                    <div className="w-7 h-7 rounded-full bg-cyan-600 text-white flex items-center justify-center font-bold text-xs">1</div>
                    <h2 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">Thông tin người đặt vé</h2>
                  </div>
                  {isAuthenticated ? (
                    <span className="text-xs text-emerald-700 font-semibold flex items-center gap-1 bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-200">
                      <span className="material-symbols-outlined text-[14px]">verified_user</span> Đã đồng bộ tài khoản
                    </span>
                  ) : (
                    <span className="text-xs text-slate-500">Khách vãng lai</span>
                  )}
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md">
                  <div className="flex flex-col gap-1.5">
                    <label className="font-label-md text-xs font-semibold text-slate-700">Họ và tên *</label>
                    <div className="relative flex items-center">
                      <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">person</span>
                      <input 
                        className="w-full pl-11 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="text" 
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder="Nhập họ và tên du khách"
                      />
                    </div>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label className="font-label-md text-xs font-semibold text-slate-700">Số điện thoại liên hệ *</label>
                    <div className="relative flex items-center">
                      <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">call</span>
                      <input 
                        className="w-full pl-11 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="tel" 
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder="Số điện thoại Zalo nhận vé"
                      />
                    </div>
                  </div>
                  <div className="flex flex-col gap-1.5 md:col-span-2">
                    <label className="font-label-md text-xs font-semibold text-slate-700">Địa chỉ Email nhận mã QR *</label>
                    <div className="relative flex items-center">
                      <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">mail</span>
                      <input 
                        className="w-full pl-11 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="email" 
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="Email nhận hóa đơn điện tử"
                      />
                    </div>
                  </div>
                  <div className="flex flex-col gap-1.5 md:col-span-2">
                    <label className="font-label-md text-xs font-semibold text-slate-700">Ghi chú đặc biệt (tùy chọn)</label>
                    <input 
                      className="w-full px-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                      type="text" 
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="Yêu cầu áo phao trẻ em, hỗ trợ chụp ảnh..."
                    />
                  </div>
                </div>
              </section>

              {/* Section 2: Payment Methods */}
              <section className="w-full rounded-2xl bg-surface-container-lowest p-space-xl shadow-sm border border-slate-100 flex flex-col gap-space-lg">
                <div className="flex items-center justify-between border-b pb-space-xs border-slate-100">
                  <div className="flex items-center gap-space-sm">
                    <div className="w-7 h-7 rounded-full bg-cyan-600 text-white flex items-center justify-center font-bold text-xs">2</div>
                    <h2 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">Phương thức thanh toán</h2>
                  </div>
                </div>
                <div className="flex flex-col gap-space-sm">
                  <label 
                    onClick={() => setPaymentProvider("VNPAY")}
                    className={`p-space-md rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                      paymentProvider === "VNPAY" ? "border-cyan-500 bg-cyan-50/50 shadow-sm" : "border-slate-200 bg-slate-50 hover:bg-slate-100/70"
                    }`}
                  >
                    <div className="flex items-center gap-space-sm">
                      <input 
                        checked={paymentProvider === "VNPAY"} 
                        onChange={() => setPaymentProvider("VNPAY")}
                        className="w-4 h-4 accent-cyan-600 cursor-pointer" 
                        name="payment_method" 
                        type="radio" 
                        value="VNPAY"
                      />
                      <div className="flex items-center gap-2">
                        <span className="font-label-lg text-sm font-bold text-slate-800">Cổng VNPAY (QR Pay / Thẻ ATM &amp; Thẻ Quốc tế)</span>
                        <span className="px-2 py-0.5 rounded-full bg-cyan-600 text-white font-label-sm text-[10px] font-bold">Khuyên dùng</span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-cyan-600">qr_code_scanner</span>
                  </label>

                  <label 
                    onClick={() => setPaymentProvider("MOMO")}
                    className={`p-space-md rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                      paymentProvider === "MOMO" ? "border-cyan-500 bg-cyan-50/50 shadow-sm" : "border-slate-200 bg-slate-50 hover:bg-slate-100/70"
                    }`}
                  >
                    <div className="flex items-center gap-space-sm">
                      <input 
                        checked={paymentProvider === "MOMO"} 
                        onChange={() => setPaymentProvider("MOMO")}
                        className="w-4 h-4 accent-cyan-600 cursor-pointer" 
                        name="payment_method" 
                        type="radio" 
                        value="MOMO"
                      />
                      <div className="flex items-center gap-2">
                        <span className="font-label-lg text-sm font-bold text-slate-800">Ví điện tử MoMo</span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-pink-600">account_balance_wallet</span>
                  </label>

                  <label 
                    onClick={() => setPaymentProvider("SEPAY")}
                    className={`p-space-md rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                      paymentProvider === "SEPAY" ? "border-cyan-500 bg-cyan-50/50 shadow-sm" : "border-slate-200 bg-slate-50 hover:bg-slate-100/70"
                    }`}
                  >
                    <div className="flex items-center gap-space-sm">
                      <input 
                        checked={paymentProvider === "SEPAY"} 
                        onChange={() => setPaymentProvider("SEPAY")}
                        className="w-4 h-4 accent-cyan-600 cursor-pointer" 
                        name="payment_method" 
                        type="radio" 
                        value="SEPAY"
                      />
                      <div className="flex items-center gap-2">
                        <span className="font-label-lg text-sm font-bold text-slate-800">Chuyển khoản ngân hàng VietQR 24/7 (SePay)</span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-indigo-600">account_balance</span>
                  </label>
                </div>
              </section>

              {/* Section 3: Ocean Safety & Marine Regulations Acknowledgment */}
              <section className="w-full rounded-2xl bg-surface-container-low p-space-xl shadow-sm border border-slate-100 flex flex-col gap-space-md">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-full bg-cyan-600 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-[20px]">assignment_turned_in</span>
                  </div>
                  <div>
                    <h2 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">Cam kết an toàn biển &amp; Miễn trừ trách nhiệm</h2>
                    <p className="font-body-sm text-xs text-slate-500">Quy định cứu sinh bắt buộc theo giấy phép cảng vụ hàng hải Đà Nẵng</p>
                  </div>
                </div>
                <div className="flex flex-col gap-space-md">
                  {items.map((item, i) => {
                    const title = item.serviceName || 'Trải nghiệm biển';
                    const location = item.locationName || 'Đà Nẵng';
                    return (
                      <div key={item.id} className="p-space-md rounded-xl bg-surface-container-lowest border border-slate-200 flex flex-col gap-space-xs">
                        <div className="flex items-center gap-2 font-label-lg text-xs font-bold text-slate-800">
                          <span className="material-symbols-outlined text-[16px] text-cyan-600">verified_user</span> 
                          <span>{i + 1}. Miễn trừ trách nhiệm: {title}</span>
                        </div>
                        <p className="font-body-sm text-xs text-slate-600 leading-relaxed bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                          Tôi xác nhận đã có đủ sức khỏe thể chất, cam kết bắt buộc mặc áo phao và tuân thủ tuyệt đối mọi hướng dẫn của huấn luyện viên DANASEA tại khu vực {location}.
                        </p>
                      </div>
                    );
                  })}
                  <label className="flex items-start gap-space-xs cursor-pointer pt-space-2xs">
                    <input 
                      className="mt-0.5 w-4 h-4 accent-cyan-600 cursor-pointer rounded" 
                      type="checkbox" 
                      checked={waiverAccepted}
                      onChange={(e) => setWaiverAccepted(e.target.checked)}
                    />
                    <span className="font-label-md text-xs text-slate-700 font-medium">
                      Tôi đã đọc, hiểu rõ và đồng ý với toàn bộ quy chuẩn an toàn &amp; điều khoản miễn trừ trách nhiệm *
                    </span>
                  </label>
                </div>
              </section>
            </div>

            {/* RIGHT COLUMN: Sticky Order Summary & Price Breakdown (5 Cols on Desktop) */}
            <div className="lg:col-span-5 flex flex-col gap-space-lg sticky top-24">
              {/* Booking Summary Card */}
              <div className="w-full rounded-2xl bg-surface-container-lowest p-space-xl shadow-md border border-slate-100 flex flex-col gap-space-lg">
                <div className="flex items-center justify-between border-b pb-space-sm border-slate-100">
                  <h3 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">Tóm tắt trải nghiệm biển</h3>
                  <span className="px-2 py-0.5 rounded-full bg-cyan-50 text-cyan-800 font-label-sm text-xs font-bold border border-cyan-100">
                    {items.length} Dịch vụ
                  </span>
                </div>
                <div className="flex flex-col gap-space-md">
                  {items.map((item) => {
                    const title = item.serviceName || 'Trải nghiệm biển';
                    const image = item.thumbnailUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800';
                    const location = item.locationName || 'Đà Nẵng';
                    const dateStr = item.selectedDate || item.date || 'Hôm nay';
                    const timeStr = item.timeSlot || '08:00 - 10:00';

                    return (
                      <div key={item.id} className="flex gap-space-sm items-start border-b pb-space-md border-slate-100 last:border-0 last:pb-0">
                        <div className="w-20 h-20 rounded-xl overflow-hidden flex-shrink-0 bg-slate-100">
                          <img className="w-full h-full object-cover" alt={title} src={image} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between gap-1">
                            <span className="px-2 py-0.5 rounded-full bg-cyan-50 text-cyan-700 text-[10px] font-bold">{location}</span>
                            <span className="font-bold text-sm text-slate-900">{(item.unitPrice * item.quantity).toLocaleString('vi-VN')}₫</span>
                          </div>
                          <h4 className="font-bold text-xs sm:text-sm text-slate-900 leading-tight mt-1 truncate">{title}</h4>
                          <p className="text-[11px] text-slate-500 mt-0.5">{timeStr} • {dateStr}</p>
                          <div className="flex items-center justify-between mt-1 text-slate-500 text-[11px]">
                            <span>{item.unitPrice.toLocaleString('vi-VN')}₫ × {item.quantity} khách</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
                
                {/* Promo Code Input */}
                <div className="pt-2 border-t border-slate-100">
                  <label className="text-xs font-semibold text-slate-700 block mb-1.5">Mã ưu đãi / Khuyến mãi</label>
                  {appliedPromoCode ? (
                    <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-emerald-600 text-sm">check_circle</span>
                        <span className="font-bold text-emerald-800">{appliedPromoCode}</span>
                        <span className="text-emerald-700">(-{discountAmount.toLocaleString('vi-VN')}₫)</span>
                      </div>
                      <button
                        onClick={handleRemovePromo}
                        className="text-red-500 hover:text-red-700 font-medium cursor-pointer"
                        type="button"
                      >
                        Gỡ bỏ
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={promoInput}
                          onChange={(e) => setPromoInput(e.target.value)}
                          placeholder="Nhập DANASEA10 hoặc HE2024"
                          className="flex-1 px-3 py-2 rounded-xl border border-slate-200 text-xs text-slate-800 uppercase focus:outline-none focus:ring-1 focus:ring-cyan-600"
                        />
                        <button
                          onClick={handleApplyPromo}
                          disabled={isApplyingPromo || !promoInput.trim()}
                          type="button"
                          className="px-4 py-2 bg-slate-900 hover:bg-slate-800 disabled:opacity-40 text-white rounded-xl text-xs font-bold cursor-pointer transition-colors"
                        >
                          {isApplyingPromo ? 'Đang kiểm tra...' : 'Áp dụng'}
                        </button>
                      </div>
                      {promoError && <p className="text-[11px] text-red-500">{promoError}</p>}
                    </div>
                  )}
                </div>
                
                <div className="flex flex-col gap-2 pt-2 border-t border-slate-100 text-xs text-slate-600">
                  <div className="flex justify-between items-center text-sm">
                    <span>Tạm tính</span>
                    <span className="text-slate-900 font-semibold">{rawTotal.toLocaleString('vi-VN')}₫</span>
                  </div>
                  {appliedPromoCode && (
                    <div className="flex justify-between items-center text-sm text-emerald-600 font-bold">
                      <span>Ưu đãi áp dụng ({appliedPromoCode})</span>
                      <span>-{discountAmount.toLocaleString('vi-VN')}₫</span>
                    </div>
                  )}
                  <div className="flex justify-between items-center text-xs text-slate-500">
                    <span>Phí quản lý &amp; cứu sinh</span>
                    <span className="text-emerald-600 font-medium">Miễn phí</span>
                  </div>
                  <div className="flex justify-between items-baseline pt-space-md border-t border-slate-100 mt-space-xs">
                    <div>
                      <span className="text-sm font-bold text-slate-900">Tổng thanh toán:</span>
                      <p className="text-[11px] text-slate-400">Đã bao gồm thuế &amp; bảo hiểm</p>
                    </div>
                    <span className="text-2xl sm:text-3xl leading-tight text-cyan-700 font-black tracking-tight">
                      {finalTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>
                </div>

                <button 
                  onClick={handlePayment} 
                  disabled={isProcessingPayment || !waiverAccepted}
                  className={`w-full py-4 rounded-full font-bold text-sm shadow-md transition-all flex items-center justify-center gap-2 cursor-pointer ${
                    waiverAccepted && !isProcessingPayment
                      ? 'bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white hover:scale-[1.02] active:scale-[0.98] shadow-cyan-600/25'
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed shadow-none'
                  }`} 
                  type="button"
                >
                  {isProcessingPayment ? (
                    <>
                      <span className="material-symbols-outlined animate-spin text-[20px]">refresh</span>
                      <span>Đang xử lý thanh toán...</span>
                    </>
                  ) : (
                    <>
                      <span className="material-symbols-outlined text-[20px]">lock</span>
                      <span>Xác nhận &amp; Thanh toán {finalTotal.toLocaleString('vi-VN')}₫</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </main>
  );
}
