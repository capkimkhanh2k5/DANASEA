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

  // Safety Waiver State (Scenario 5 - Maritime Safety Guard)
  const [waiverAccepted, setWaiverAccepted] = useState<boolean>(true);
  const [waiverError, setWaiverError] = useState<string | null>(null);
  const [showWaiverDetailModal, setShowWaiverDetailModal] = useState<boolean>(false);
  const [showDevSwitcher, setShowDevSwitcher] = useState<boolean>(true);

  // Slot Holding Status (Scenario 4 - 15-minute slot timeout)
  const isHoldExpired = countdown <= 0;

  // Promo Code State
  const [promoInput, setPromoInput] = useState<string>("");
  const [discountPreview, setDiscountPreview] = useState<DiscountPreviewResponse | null>(null);
  const [appliedPromoCode, setAppliedPromoCode] = useState<string | null>(null);
  const [promoError, setPromoError] = useState<string>("");
  const [isApplyingPromo, setIsApplyingPromo] = useState<boolean>(false);


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

    if (isHoldExpired) {
      setPaymentError("Thời gian giữ chỗ 15 phút đã kết thúc. Vui lòng quay lại giỏ hàng để cập nhật lại suất trống.");
      return;
    }

    if (!waiverAccepted) {
      setWaiverError("Quý khách vui lòng xác nhận Cam kết an toàn hàng hải trước khi tiến hành thanh toán.");
      const waiverEl = document.getElementById("waiver-acceptance-section");
      if (waiverEl) {
        waiverEl.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      return;
    }

    setIsProcessingPayment(true);
    setPaymentError(null);
    setWaiverError(null);

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

    // Save order snapshot for Step 3 (PaymentResult)
    const orderSnapshot = {
      orderCode: `DNS-${sessionCode || Math.floor(10000 + Math.random() * 90000)}`,
      customerName: fullName.trim() || "Khách Hàng DANASEA",
      customerPhone: phone.trim() || "0912 345 678",
      customerEmail: email.trim() || "khachhang@danasea.vn",
      specialRequest: notes.trim(),
      paymentProvider: paymentProvider,
      items: items.map((item, idx) => ({
        id: item.id || `item-${idx}`,
        serviceName: item.serviceName || 'Trải nghiệm biển Đà Nẵng',
        thumbnailUrl: item.thumbnailUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5',
        unitPrice: item.unitPrice,
        quantity: item.quantity,
        date: item.selectedDate || item.date || 'Hôm nay',
        timeSlot: item.timeSlot || '08:00 - 10:00',
        locationName: item.locationName || 'Bán đảo Sơn Trà, Đà Nẵng',
        vendorName: item.vendorName || 'DanaSea Marine Club',
        ticketCode: `TKT-${sessionCode || '89412'}-0${idx + 1}`
      })),
      rawTotal: rawTotal,
      discountAmount: discountAmount,
      appliedPromoCode: appliedPromoCode,
      finalTotal: finalTotal,
      paidAt: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) + ' • ' + new Date().toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
    };
    sessionStorage.setItem('danasea_last_order', JSON.stringify(orderSnapshot));

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
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate('/cart')}>Giỏ hàng</span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="text-cyan-700 font-bold">Thông tin &amp; Thanh toán</span>
              </div>
            </div>
            {/* Stepper Component */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm items-center">
              <div 
                onClick={() => navigate('/cart')}
                className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2 cursor-pointer hover:bg-slate-100/90 hover:border-slate-300 transition-all group"
                title="Quay lại Bước 1: Giỏ hàng để xem hoặc chỉnh sửa dịch vụ"
              >
                <div className="w-8 h-8 rounded-full bg-emerald-600 group-hover:bg-emerald-700 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm transition-colors">
                  <span className="material-symbols-outlined text-[18px]">check</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-emerald-600 group-hover:text-emerald-700 font-bold uppercase tracking-wider transition-colors">Bước 1 - Hoàn thành</p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 group-hover:text-cyan-800 truncate transition-colors">Giỏ hàng</p>
                </div>
              </div>
              <div className="flex items-center gap-space-sm p-space-xs rounded-xl bg-cyan-50/80 border border-cyan-200 shadow-sm p-2">
                <div className="w-8 h-8 rounded-full bg-cyan-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span>2</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-cyan-600 font-bold uppercase tracking-wider">Bước 2</p>
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-2xl items-stretch">
            
            {/* LEFT COLUMN: Checkout Forms (7 Cols on Desktop) */}
            <div className="lg:col-span-7 flex flex-col h-full">
              {paymentError && (
                <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-medium flex items-center gap-2">
                  <span className="material-symbols-outlined text-amber-600">info</span>
                  <span>{paymentError}</span>
                </div>
              )}

              {/* Unified Checkout Form Card */}
              <div className="w-full h-full rounded-2xl bg-surface-container-lowest p-space-xl shadow-sm border border-slate-100 flex flex-col justify-between gap-space-xl">
                {/* Hold Timer & ID Header Inside Card */}
                <div className="flex items-center justify-between pb-4 border-b border-slate-100 flex-wrap gap-3">
                  <div className="flex items-center gap-2 text-slate-900">
                    <span className="material-symbols-outlined text-slate-900 text-[20px]">timer</span>
                    <span className="text-sm font-medium text-slate-800">Thời gian giữ chỗ:</span>
                    <span className={`font-bold font-mono text-base ${
                      isHoldExpired ? "text-rose-600 animate-pulse font-extrabold" : "text-slate-900"
                    }`} id="checkout-timer">
                      {isHoldExpired ? "00:00 (Hết hạn giữ chỗ)" : formatTime(countdown)}
                    </span>
                  </div>
                  <div className="text-sm font-mono text-slate-700">
                    ID: <span className="font-bold text-slate-900">DNS-{sessionCode}</span>
                  </div>
                </div>

                {/* Part 1: Customer Information */}
                <div className="flex flex-col gap-space-lg">
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
                      <input 
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="text" 
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder="Nhập họ và tên du khách"
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <label className="font-label-md text-xs font-semibold text-slate-700">Số điện thoại liên hệ *</label>
                      <input 
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="tel" 
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder="Số điện thoại Zalo nhận vé"
                      />
                    </div>
                    <div className="flex flex-col gap-1.5 md:col-span-2">
                      <label className="font-label-md text-xs font-semibold text-slate-700">Địa chỉ Email nhận mã QR *</label>
                      <input 
                        className="w-full px-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 font-body-md text-sm text-slate-800 outline-none focus:border-cyan-500 focus:bg-white transition-colors" 
                        type="email" 
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="Email nhận hóa đơn điện tử"
                      />
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
                </div>

                {/* SCENARIO 4: EXPIRED HOLD NOTICE */}
                {isHoldExpired && (
                  <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-900 text-xs sm:text-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 animate-fade-in shadow-xs">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center flex-shrink-0 text-rose-600">
                        <span className="material-symbols-outlined text-[20px]">timer_off</span>
                      </div>
                      <div>
                        <strong className="block font-bold text-rose-900">Thời gian giữ chỗ đã kết thúc (15 phút)</strong>
                        <p className="text-rose-700 text-xs mt-0.5">
                          Suất trải nghiệm đã được tự động mở lại cho du khách khác theo quy chuẩn an toàn.
                        </p>
                      </div>
                    </div>
                    <button
                      onClick={() => navigate('/cart')}
                      type="button"
                      className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs transition-colors cursor-pointer flex-shrink-0 self-end sm:self-auto"
                    >
                      Quay lại giỏ hàng
                    </button>
                  </div>
                )}

                {/* SCENARIO 5: SAFETY WAIVER ACCEPTANCE GUARD */}
                <div 
                  id="waiver-acceptance-section"
                  className={`p-space-lg rounded-2xl border transition-all duration-300 ${
                    waiverError 
                      ? "bg-rose-50/60 border-rose-300 ring-2 ring-rose-200" 
                      : waiverAccepted
                      ? "bg-cyan-50/40 border-cyan-100"
                      : "bg-slate-50 border-slate-200"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-cyan-700 text-[20px]">verified_user</span>
                      <h4 className="font-bold text-sm text-slate-900">Cam kết an toàn hàng hải &amp; Miễn trừ trách nhiệm</h4>
                    </div>
                    <button
                      onClick={() => setShowWaiverDetailModal(true)}
                      type="button"
                      className="text-cyan-700 hover:text-cyan-800 text-xs font-semibold underline cursor-pointer"
                    >
                      Xem chi tiết điều khoản
                    </button>
                  </div>

                  <p className="text-xs text-slate-600 leading-relaxed mb-3">
                    Theo quy chuẩn an toàn biển Đà Nẵng, 100% du khách tham gia lặn biển, lướt sóng, cano cao tốc phải tuân thủ trang bị áo phao SOLAS, hướng dẫn viên và khai báo sức khỏe.
                  </p>

                  <label className="flex items-start gap-3 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={waiverAccepted}
                      onChange={(e) => {
                        setWaiverAccepted(e.target.checked);
                        if (e.target.checked) setWaiverError(null);
                      }}
                      className="mt-0.5 w-4 h-4 rounded text-cyan-600 focus:ring-cyan-500 border-slate-300 cursor-pointer"
                    />
                    <span className="text-xs font-medium text-slate-800 leading-snug">
                      Tôi xác nhận đã đọc, hiểu rõ tình trạng thể lực và cam kết tuân thủ đầy đủ quy định an toàn biển của DANASEA và nhà cung cấp.
                    </span>
                  </label>

                  {waiverError && (
                    <div className="mt-2 text-rose-600 text-xs font-semibold flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[16px]">error</span>
                      <span>{waiverError}</span>
                    </div>
                  )}
                </div>

                {/* Part 2: Payment Methods */}
                <div className="flex flex-col gap-space-lg pt-2 border-t border-slate-100">
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
                      onClick={() => setPaymentProvider("PAYPAL")}
                      className={`p-space-md rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                        paymentProvider === "PAYPAL" ? "border-cyan-500 bg-cyan-50/50 shadow-sm" : "border-slate-200 bg-slate-50 hover:bg-slate-100/70"
                      }`}
                    >
                      <div className="flex items-center gap-space-sm">
                        <input 
                          checked={paymentProvider === "PAYPAL"} 
                          onChange={() => setPaymentProvider("PAYPAL")}
                          className="w-4 h-4 accent-cyan-600 cursor-pointer" 
                          name="payment_method" 
                          type="radio" 
                          value="PAYPAL"
                        />
                        <div className="flex items-center gap-2">
                          <span className="font-label-lg text-sm font-bold text-slate-800">Cổng PayPal (Thẻ quốc tế Visa, MasterCard & Ví PayPal)</span>
                        </div>
                      </div>
                      <span className="material-symbols-outlined text-blue-600">credit_card</span>
                    </label>

                    
                  </div>
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN: Sticky Order Summary & Price Breakdown (5 Cols on Desktop) */}
            <div className="lg:col-span-5 flex flex-col h-full">
              {/* Booking Summary Card */}
              <div className="w-full h-full rounded-2xl bg-surface-container-lowest p-space-xl shadow-md border border-slate-100 flex flex-col">
                <div className="flex flex-col gap-space-md">
                  <div className="flex items-center justify-between border-b pb-space-sm border-slate-100">
                    <h3 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">Tóm tắt</h3>
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
                  
                  {/* Subtotal & Promo breakdown directly below Promo Code */}
                  <div className="flex flex-col gap-2 pt-3 border-t border-slate-100 text-xs text-slate-600">
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
                      <span>Phí dịch vụ</span>
                      <span className="text-emerald-600 font-medium">Miễn phí</span>
                    </div>
                  </div>
                </div>

                {/* Balance empty space is absorbed above, Total Payment sits right above button */}
                <div className="mt-auto pt-4 flex flex-col gap-4">
                  <div className="flex justify-between items-baseline pt-3 border-t border-slate-100">
                    <div>
                      <span className="text-sm font-bold text-slate-900">Tổng thanh toán:</span>
                      <p className="text-[11px] text-slate-400">Đã bao gồm thuế &amp; bảo hiểm</p>
                    </div>
                    <span className="text-2xl sm:text-3xl leading-tight text-cyan-700 font-black tracking-tight">
                      {finalTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>

                  {isHoldExpired ? (
                    <div className="flex flex-col gap-2">
                      <button 
                        disabled
                        className="w-full py-4 rounded-full font-bold text-sm bg-slate-200 text-slate-400 cursor-not-allowed flex items-center justify-center gap-2"
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[18px]">timer_off</span>
                        <span>Đã hết hạn giữ chỗ</span>
                      </button>
                      <button
                        onClick={() => navigate('/cart')}
                        type="button"
                        className="w-full py-3 rounded-full border border-rose-300 bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold text-xs transition-colors cursor-pointer text-center"
                      >
                        Quay lại giỏ hàng để chọn lại suất
                      </button>
                    </div>
                  ) : (
                    <button 
                      onClick={handlePayment} 
                      disabled={isProcessingPayment}
                      className={`w-full py-4 rounded-full font-bold text-sm shadow-md transition-all flex items-center justify-center cursor-pointer ${
                        !isProcessingPayment
                          ? 'bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white hover:scale-[1.02] active:scale-[0.98] shadow-cyan-600/25'
                          : 'bg-slate-200 text-slate-400 cursor-not-allowed shadow-none'
                      }`} 
                      type="button"
                    >
                      {isProcessingPayment ? (
                        <span className="flex items-center gap-2">
                          <span className="material-symbols-outlined animate-spin text-[20px]">refresh</span>
                          <span>Đang xử lý thanh toán...</span>
                        </span>
                      ) : (
                        <span>Xác nhận &amp; Thanh toán</span>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </main>

        {/* Safety Waiver Detail Modal */}
        {showWaiverDetailModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-fade-in">
            <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 flex flex-col max-h-[85vh]">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2 text-cyan-800">
                  <span className="material-symbols-outlined text-[22px]">gavel</span>
                  <h3 className="font-bold text-base">Quy chuẩn An toàn Hàng hải DANASEA</h3>
                </div>
                <button
                  onClick={() => setShowWaiverDetailModal(false)}
                  className="text-slate-400 hover:text-slate-700 cursor-pointer"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[20px]">close</span>
                </button>
              </div>

              <div className="overflow-y-auto py-4 text-xs text-slate-600 space-y-3 leading-relaxed flex-1">
                <p>
                  <strong>1. Điều kiện thể lực:</strong> Du khách xác nhận không mắc các bệnh lý nghiêm trọng về tim mạch, huyết áp cấp, động kinh hoặc mang thai trên 28 tuần khi tham gia các bộ môn vận động mạnh dưới nước.
                </p>
                <p>
                  <strong>2. Trang thiết bị bảo hộ:</strong> Bắt buộc 100% người tham gia mặc áo phao cứu sinh tiêu chuẩn SOLAS trong suốt hành trình cano và các hoạt động trên mặt nước.
                </p>
                <p>
                  <strong>3. Tuân thủ hướng dẫn viên (HDV):</strong> Tuyệt đối chấp hành hiệu lệnh an toàn của HDV bản địa và thuyền trưởng điều hành. Không tự ý bơi tách đoàn ngoài ranh giới phao cảnh báo.
                </p>
                <p>
                  <strong>4. Bảo tồn môi trường biển:</strong> Cam kết không dẫm đạp, bẻ gãy rạn san hô, không vứt rác thải nhựa xuống biển Đà Nẵng.
                </p>
              </div>

              <div className="pt-3 border-t border-slate-100 flex justify-end gap-2">
                <button
                  onClick={() => {
                    setWaiverAccepted(true);
                    setWaiverError(null);
                    setShowWaiverDetailModal(false);
                  }}
                  type="button"
                  className="px-5 py-2.5 rounded-xl bg-cyan-700 hover:bg-cyan-800 text-white font-bold text-xs transition-colors cursor-pointer"
                >
                  Đồng ý &amp; Xác nhận cam kết
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Floating Scenario Switcher for Step 2 testing */}
        {showDevSwitcher && (
          <aside 
            className="fixed bottom-6 right-6 z-40 bg-slate-900/95 text-white p-3.5 rounded-2xl shadow-2xl border border-slate-700/80 backdrop-blur-md max-w-xs text-xs flex flex-col gap-2 select-none animate-in fade-in slide-in-from-bottom-4 duration-300"
            aria-label="Dev Scenario Switcher"
          >
            <div className="flex items-center justify-between pb-1.5 border-b border-slate-700 gap-4">
              <div className="flex items-center gap-1.5 font-bold text-cyan-400">
                <span className="material-symbols-outlined text-[16px]">tune</span>
                <span>Kiểm tra Bước 2 (Preview)</span>
              </div>
              <button 
                onClick={() => setShowDevSwitcher(false)} 
                className="text-slate-400 hover:text-white transition-colors cursor-pointer"
                title="Đóng thanh kịch bản"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">close</span>
              </button>
            </div>

            <p className="text-[10px] text-slate-400 leading-tight">
              Công cụ test nhanh kịch bản kiểm soát giữ chỗ 15p &amp; Cam kết an toàn (Sẽ tự động xóa sau khi duyệt):
            </p>

            <div className="grid grid-cols-1 gap-1.5 pt-1">
              <button
                onClick={() => setCountdown(0)}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  isHoldExpired ? "bg-rose-600 text-white font-bold" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>4. Hết hạn 15p (Slot timeout)</span>
                {isHoldExpired && <span className="material-symbols-outlined text-[14px]">check</span>}
              </button>

              <button
                onClick={() => setCountdown(900)}
                className="w-full text-left px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium transition-colors cursor-pointer flex items-center justify-between"
                type="button"
              >
                <span>Khôi phục thời gian 15 phút</span>
                <span className="material-symbols-outlined text-[14px]">refresh</span>
              </button>

              <button
                onClick={() => setWaiverAccepted(!waiverAccepted)}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  !waiverAccepted ? "bg-amber-600 text-white font-bold" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>5. {waiverAccepted ? "Bỏ chọn Cam kết an toàn" : "Đã bỏ chọn (Thử bấm Thanh toán)"}</span>
              </button>

              <button
                onClick={() => navigate("/checkout/result?status=success")}
                className="w-full text-left px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 font-medium transition-colors cursor-pointer flex items-center justify-between"
                type="button"
              >
                <span>Chuyển tới Bước 3 (Kết quả)</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>
          </aside>
        )}

      </div>
    </main>
  );
}
