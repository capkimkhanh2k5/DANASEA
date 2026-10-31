import { useNavigate } from "react-router-dom";
import { useCartStore } from "../../../store/useCartStore";
import { useAuthStore } from "../../../store/useAuthStore";
import { bookingService } from "../../../services/bookingService";
import { orderService } from "../../../services/orderService";
import { paymentService } from "../../../services/paymentService";
import type { BookingHoldResponse } from "../../../types/booking";
import type { DiscountPreviewResponse, PaymentProviderType } from "../../../types/order";
import { useState, useEffect, useRef } from "react";
import {
  Timer,
  ShieldCheck,
  ChevronRight,
  QrCode,
  CreditCard,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  Loader2,
} from "lucide-react";

export function Checkout() {
  const navigate = useNavigate();
  const { items, getTotalPrice } = useCartStore();
  const { user, isAuthenticated } = useAuthStore();

  // Booking Hold State
  const [bookingHold, setBookingHold] = useState<BookingHoldResponse | null>(null);
  const [countdown, setCountdown] = useState<number>(900); // 15 minutes default
  const [sessionCode] = useState(() => Math.floor(10000 + Math.random() * 90000));

  // Customer Contact Info State
  const [fullName, setFullName] = useState<string>(user?.fullName || "Khách Hàng DANASEA");
  const [phone, setPhone] = useState<string>(user?.phone || "0912 345 678");
  const [email, setEmail] = useState<string>(user?.email || "khachhang@danasea.vn");
  const [notes, setNotes] = useState<string>("");

  // Payment Method
  const [paymentProvider, setPaymentProvider] = useState<PaymentProviderType>("VNPAY");

  // Slot Holding Status (15-minute slot timeout)
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

  // Seed demo item if opened directly with empty cart
  useEffect(() => {
    if (items.length === 0 && !isProcessingPayment) {
      useCartStore.getState().addItem({
        serviceId: 'tour-1',
        slotId: 'slot-tour-1-today-1400',
        serviceName: 'Tour lặn ngắm san hô Bán đảo Sơn Trà',
        thumbnailUrl: 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800',
        date: new Date().toISOString().split('T')[0],
        timeSlot: '14:00 - 16:30',
        selectedDate: new Date().toISOString().split('T')[0],
        locationName: 'Bán đảo Sơn Trà, Đà Nẵng',
        vendorName: 'DANASEA Marine Partner',
        quantity: 2,
        unitPrice: 550000,
      });
    }
  }, [items.length, isProcessingPayment]);

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
      bookingService
        .holdBooking({ items: holdItems })
        .then((res) => {
          setBookingHold(res);
          const expireTime = res.holdExpiresAt || res.expiresAt;
          if (expireTime) {
            const expireDate = new Date(expireTime).getTime();
            const now = Date.now();
            const diffSec = Math.max(0, Math.floor((expireDate - now) / 1000));
            if (diffSec > 0) setCountdown(diffSec);
          }
        })
        .catch((err) => {
          console.warn('[Checkout] Không thể giữ chỗ qua API, dùng chế độ giữ chỗ cục bộ:', err);
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
      } catch (err) {
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

    setIsProcessingPayment(true);
    setPaymentError(null);

    if (isAuthenticated) {
      try {

        let activeHold = bookingHold;
        if (!activeHold?.bookingId) {
          const holdItems = items.map((i) => ({
            slotId: i.slotId,
            quantity: i.quantity,
            optionId: i.optionId,
            participantsCount: i.quantity,
            allowSplit: false,
          }));
          activeHold = await bookingService.holdBooking({ items: holdItems });
          setBookingHold(activeHold);
        }

        // 1. Create Master Order
        const orderRes = await orderService.createOrder({
          bookingId: activeHold.bookingId,
          discountCode: appliedPromoCode || undefined,
        });

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
        let payRes;
        try {
          payRes = await paymentService.createPaymentIntent(orderRes.id, {
            provider: paymentProvider,
          });
        } catch (paymentErr) {
          const apiErr = paymentErr as {
            response?: {
              data?: {
                errorCode?: string;
                missingWaivers?: Array<{
                  subOrderId: string;
                  waiverVersion?: number;
                  contentLanguage?: string;
                }>;
              };
            };
          };
          if (
            apiErr.response?.data?.errorCode === 'WAIVER_ACCEPTANCE_REQUIRED' &&
            Array.isArray(apiErr.response?.data?.missingWaivers)
          ) {
            const missing = apiErr.response.data.missingWaivers;
            await Promise.all(
              missing.map((m: { subOrderId: string; waiverVersion?: number; contentLanguage?: string }) =>
                orderService.acceptSubOrderWaiver(m.subOrderId, {
                  accepted: true,
                  version: m.waiverVersion || 1,
                  language: m.contentLanguage || 'VI',
                })
              )
            );
            payRes = await paymentService.createPaymentIntent(orderRes.id, {
              provider: paymentProvider,
            });
          } else {
            throw paymentErr;
          }
        }

        // 4. Backend returns paymentReference (which contains the gateway redirect URL)
        const gatewayUrl = payRes.paymentReference || payRes.paymentUrl;
        if (gatewayUrl && (gatewayUrl.startsWith("http://") || gatewayUrl.startsWith("https://"))) {
          
          // Save order snapshot for Step 3 (PaymentResult) when returning from VNPay gateway
          const orderSnapshot = {
            orderCode: `DNS-${sessionCode}`,
            customerName: fullName.trim() || "Khách Hàng DANASEA",
            customerPhone: phone.trim() || "0912 345 678",
            customerEmail: email.trim() || "khachhang@danasea.vn",
            specialRequest: notes.trim(),
            paymentProvider: paymentProvider,
            items: items.map((item, idx) => ({
              id: item.id || `item-${idx}`,
              serviceId: item.serviceId,
              slotId: item.slotId,
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
          window.location.assign(gatewayUrl);
          return;
        } else {
          setIsProcessingPayment(false);
          setPaymentError("Không nhận được liên kết thanh toán từ cổng. Vui lòng thử lại hoặc chọn phương thức khác.");
          return;
        }
      } catch (err) {
        const error = err as { response?: { data?: { message?: string } } };
        console.error('[Checkout] Lỗi khi tạo đơn hàng:', err);
        setIsProcessingPayment(false);
        setPaymentError(error.response?.data?.message || "Không thể khởi tạo giao dịch thanh toán. Vui lòng kiểm tra lại kết nối và thử lại.");
        return;
      }
    } else {
      setIsProcessingPayment(false);
      setPaymentError("Vui lòng đăng nhập tài khoản Khách hàng để tiến hành thanh toán vé.");
      return;
    }
  };

  if (items.length === 0) return null;

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar - Airbnb Style */}
        <section className="w-full bg-slate-50 border-b border-slate-200/80">
          <div className="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-4">
            {/* Breadcrumb Navigation */}
            <div className="flex items-center gap-2 text-xs text-slate-500 mb-3">
              <span 
                className="hover:text-slate-900 cursor-pointer transition-colors" 
                onClick={() => navigate('/')}
              >
                DANASEA
              </span>
              <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
              <span 
                className="hover:text-slate-900 cursor-pointer transition-colors" 
                onClick={() => navigate('/cart')}
              >
                Giỏ hàng
              </span>
              <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
              <span className="text-slate-900 font-semibold">Thông tin &amp; Thanh toán</span>
            </div>

            {/* Stepper Component */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center">
              <div 
                onClick={() => navigate('/cart')}
                className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3 cursor-pointer hover:bg-slate-100 transition-colors"
                title="Quay lại Giỏ hàng"
              >
                <span className="text-sm font-semibold text-emerald-700">01.</span>
                <div className="min-w-0">
                  <p className="text-xs text-emerald-700 font-medium">Bước 1 (Đã chọn)</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Giỏ hàng</p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-xl bg-white border border-slate-300 p-3 shadow-xs">
                <span className="text-sm font-bold text-slate-900">02.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-500 font-medium">Bước 2</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3 opacity-60">
                <span className="text-sm font-semibold text-slate-600">03.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-600 font-medium">Bước 3</p>
                  <p className="text-sm font-medium text-slate-700 truncate">Nhận vé điện tử QR</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Checkout Area */}
        <div className="w-full max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="pb-6 border-b border-slate-200/80 mb-8">
            <h1 className="text-2xl sm:text-[26px] font-bold text-slate-900 tracking-tight">
              Xác nhận thông tin &amp; Thanh toán
            </h1>
            <p className="text-[13px] text-slate-600 mt-1">
              Kiểm tra lịch trình biển Sơn Trà, người tham gia và lựa chọn phương thức thanh toán an toàn
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
                      <span>Hết thời gian giữ chỗ</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 text-slate-900 text-sm">
                      <Timer className="w-4 h-4 text-slate-700" />
                      <span className="font-medium text-slate-700">Thời gian giữ chỗ:</span>
                      <span className="font-bold font-mono text-base text-slate-900 tabular-nums">
                        {formatTime(countdown)}
                      </span>
                    </div>
                  )}
                  <div className="text-xs font-mono text-slate-600">
                    Mã phiên: <span className="font-bold text-slate-900">DNS-{sessionCode}</span>
                  </div>
                </div>

                {/* Section 1: Customer Contact Info */}
                <div className="flex flex-col gap-4">
                  <div className="flex items-center justify-between border-b pb-2 border-slate-100">
                    <h2 className="text-base sm:text-lg font-bold text-slate-900">
                      1. Thông tin người đặt vé
                    </h2>
                    {isAuthenticated && (
                      <span className="text-xs text-emerald-700 font-medium flex items-center gap-1 bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-200">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Đã đồng bộ tài khoản
                      </span>
                    )}
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-semibold text-slate-700">Họ và tên *</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="text" 
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder="Nhập họ và tên"
                      />
                    </div>
                    
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-semibold text-slate-700">Số điện thoại liên hệ *</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="tel" 
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder="Số điện thoại nhận vé"
                      />
                    </div>

                    <div className="flex flex-col gap-1.5 sm:col-span-2">
                      <label className="text-xs font-semibold text-slate-700">Địa chỉ Email nhận mã QR vé *</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="email" 
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="Email nhận xác nhận đặt chỗ"
                      />
                    </div>

                    <div className="flex flex-col gap-1.5 sm:col-span-2">
                      <label className="text-xs font-semibold text-slate-700">Ghi chú đặc biệt (tùy chọn)</label>
                      <input 
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors" 
                        type="text" 
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        placeholder="Yêu cầu kích thước áo phao, hỗ trợ quay chụp..."
                      />
                    </div>
                  </div>
                </div>

                {/* Section 2: Payment Provider */}
                <div className="flex flex-col gap-4 pt-2 border-t border-slate-100">
                  <div className="border-b pb-2 border-slate-100">
                    <h2 className="text-base sm:text-lg font-bold text-slate-900">
                      2. Phương thức thanh toán
                    </h2>
                  </div>

                  <div className="flex flex-col gap-3">
                    <label 
                      onClick={() => setPaymentProvider("VNPAY")}
                      className={`p-4 rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                        paymentProvider === "VNPAY" 
                          ? "border-slate-900 bg-slate-50/80 shadow-xs" 
                          : "border-slate-200 bg-white hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <input 
                          checked={paymentProvider === "VNPAY"} 
                          onChange={() => setPaymentProvider("VNPAY")}
                          className="w-4 h-4 accent-slate-900 cursor-pointer" 
                          name="payment_method" 
                          type="radio" 
                          value="VNPAY"
                        />
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-bold text-slate-900">Cổng VNPAY (QR Pay / Thẻ ATM &amp; Quốc tế)</span>
                            <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[10px] font-bold border border-emerald-200">
                              Phổ biến
                            </span>
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5">Hỗ trợ quét mã QR qua 30+ ứng dụng ngân hàng</p>
                        </div>
                      </div>
                      <QrCode className="w-5 h-5 text-slate-700 shrink-0" />
                    </label>

                    <label 
                      onClick={() => setPaymentProvider("PAYPAL")}
                      className={`p-4 rounded-xl border transition-all flex items-center justify-between cursor-pointer ${
                        paymentProvider === "PAYPAL" 
                          ? "border-slate-900 bg-slate-50/80 shadow-xs" 
                          : "border-slate-200 bg-white hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <input 
                          checked={paymentProvider === "PAYPAL"} 
                          onChange={() => setPaymentProvider("PAYPAL")}
                          className="w-4 h-4 accent-slate-900 cursor-pointer" 
                          name="payment_method" 
                          type="radio" 
                          value="PAYPAL"
                        />
                        <div>
                          <span className="text-sm font-bold text-slate-900">Cổng PayPal (Thẻ quốc tế Visa, MasterCard)</span>
                          <p className="text-xs text-slate-500 mt-0.5">Dành cho du khách quốc tế hoặc thanh toán ngoại tệ</p>
                        </div>
                      </div>
                      <CreditCard className="w-5 h-5 text-slate-700 shrink-0" />
                    </label>
                  </div>
                </div>

                {/* Safety Guarantee Footnote */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5 text-xs">
                  <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                  <div className="text-slate-600 leading-relaxed">
                    <p className="font-bold text-slate-900">Chính sách bảo vệ quyền lợi du khách</p>
                    <p className="mt-0.5">
                      Cam kết hoàn tiền 100% khi thời tiết biển động hoặc cảng vụ cấm xuất bến. Mọi thông tin thanh toán đều được mã hóa SSL an toàn.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN: Sticky Order Summary & Price Breakdown */}
            <aside className="lg:col-span-5 flex flex-col gap-4 lg:sticky lg:top-24">
              <div className="p-6 rounded-2xl bg-white shadow-xs border border-slate-200/80 flex flex-col gap-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <h3 className="text-lg font-bold text-slate-900">Tóm tắt đơn hàng</h3>
                  <span className="text-xs text-slate-600 font-medium">
                    {items.length} trải nghiệm
                  </span>
                </div>

                {/* Items List */}
                <div className="flex flex-col gap-3">
                  {items.map((item) => {
                    const title = item.serviceName || 'Trải nghiệm biển';
                    const image = item.thumbnailUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800';
                    const location = item.locationName || 'Đà Nẵng';
                    const dateStr = item.selectedDate || item.date || 'Hôm nay';
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
                            {item.quantity} khách × {item.unitPrice.toLocaleString('vi-VN')}₫
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
                
                {/* Promo Code Input */}
                <div className="pt-3 border-t border-slate-100">
                  <label className="text-xs font-semibold text-slate-700 block mb-1.5">Mã ưu đãi chuyến đi</label>
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
                        Gỡ bỏ
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={promoInput}
                          onChange={(e) => setPromoInput(e.target.value)}
                          placeholder="DANASEA10 hoặc HE2024"
                          className="flex-1 px-3 py-2 rounded-xl border border-slate-200 text-xs text-slate-900 uppercase focus:outline-none focus:border-slate-400 bg-slate-50"
                        />
                        <button
                          onClick={handleApplyPromo}
                          disabled={isApplyingPromo || !promoInput.trim()}
                          type="button"
                          className="px-4 py-2 bg-[#0284c7] hover:bg-[#0369a1] disabled:opacity-50 text-white rounded-xl text-xs font-semibold cursor-pointer transition-colors shadow-xs"
                        >
                          {isApplyingPromo ? 'Đang kiểm tra...' : 'Áp dụng'}
                        </button>
                      </div>
                      {promoError && <p className="text-xs text-rose-600">{promoError}</p>}
                    </div>
                  )}
                </div>
                
                {/* Subtotal & Price breakdown */}
                <div className="flex flex-col gap-2 pt-3 border-t border-slate-100 text-xs text-slate-600">
                  <div className="flex justify-between items-center text-sm">
                    <span>Tạm tính</span>
                    <span className="text-slate-900 font-semibold tabular-nums">
                      {rawTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>
                  {appliedPromoCode && (
                    <div className="flex justify-between items-center text-sm text-emerald-700 font-bold">
                      <span>Ưu đãi áp dụng ({appliedPromoCode})</span>
                      <span className="tabular-nums">-{discountAmount.toLocaleString('vi-VN')}₫</span>
                    </div>
                  )}
                  <div className="flex justify-between items-center text-xs text-slate-500">
                    <span>Phí dịch vụ</span>
                    <span className="text-emerald-700 font-medium">Miễn phí</span>
                  </div>
                </div>

                {/* Total Payment & Action Button */}
                <div className="pt-3 border-t border-slate-100 flex flex-col gap-4">
                  <div className="flex justify-between items-baseline">
                    <div>
                      <span className="text-xs text-slate-700 font-semibold">Tổng thanh toán:</span>
                      <p className="text-xs text-slate-500">Đã gồm thuế &amp; bảo hiểm</p>
                    </div>
                    <span className="text-2xl sm:text-3xl leading-tight text-slate-900 font-extrabold tracking-tight tabular-nums">
                      {finalTotal.toLocaleString('vi-VN')}₫
                    </span>
                  </div>

                  {isHoldExpired ? (
                    <button
                      onClick={() => navigate('/cart')}
                      type="button"
                      className="w-full py-3.5 rounded-xl font-bold text-sm bg-rose-600 hover:bg-rose-700 text-white shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-colors"
                    >
                      <ArrowLeft className="w-4 h-4" />
                      <span>Quay lại giỏ hàng</span>
                    </button>
                  ) : (
                    <button 
                      onClick={handlePayment} 
                      disabled={isProcessingPayment}
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
                          <span>Đang kết nối cổng thanh toán...</span>
                        </span>
                      ) : (
                        <span>Xác nhận &amp; Thanh toán</span>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </main>
  );
}
