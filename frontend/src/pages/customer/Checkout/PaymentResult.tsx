import { useNavigate, useSearchParams } from "react-router-dom";
import { useState, useEffect } from "react";

interface OrderItemSnapshot {
  id: string;
  serviceName: string;
  thumbnailUrl: string;
  unitPrice: number;
  quantity: number;
  date: string;
  timeSlot: string;
  locationName: string;
  vendorName: string;
  ticketCode: string;
}

interface OrderSnapshot {
  orderCode: string;
  customerName: string;
  phone?: string;
  email?: string;
  customerPhone?: string;
  customerEmail?: string;
  specialRequest?: string;
  paymentProvider: string;
  items: OrderItemSnapshot[];
  rawTotal: number;
  discountAmount: number;
  appliedPromoCode?: string | null;
  finalTotal: number;
  paidAt: string;
}

export function PaymentResult() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [order, setOrder] = useState<OrderSnapshot | null>(null);
  const [savedTicketId, setSavedTicketId] = useState<string | null>(null);
  const [showDevSwitcher, setShowDevSwitcher] = useState<boolean>(true);

  // Status detection from URL params (e.g. from VNPay or simulation)
  const statusParam = searchParams.get("status");
  const responseCodeParam = searchParams.get("vnp_ResponseCode");

  const isCancelled = statusParam === "cancelled" || responseCodeParam === "24";
  const isFailed = statusParam === "failed" || (Boolean(responseCodeParam) && responseCodeParam !== "00" && responseCodeParam !== "24");
  const isSuccess = !isCancelled && !isFailed;

  const getFailureReason = (code?: string | null) => {
    switch (code) {
      case "51":
        return "Tài khoản của quý khách không đủ số dư để hoàn tất giao dịch.";
      case "10":
        return "Quý khách đã nhập sai thông tin xác thực OTP quá 3 lần.";
      case "12":
        return "Thẻ hoặc tài khoản ngân hàng của quý khách đang bị khóa.";
      case "65":
        return "Giao dịch vượt quá hạn mức thanh toán trong ngày của ngân hàng.";
      case "75":
        return "Ngân hàng thanh toán đang bảo trì định kỳ. Quý khách vui lòng thử lại sau.";
      default:
        return "Giao dịch bị từ chối bởi ngân hàng phát hành hoặc cổng thanh toán.";
    }
  };

  useEffect(() => {
    // 1. Try to load recent order snapshot from sessionStorage
    const saved = sessionStorage.getItem("danasea_last_order");
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        setOrder(parsed);
        return;
      } catch (e) {
        console.error("Failed to parse saved order snapshot:", e);
      }
    }

    // 2. Fallback default order if accessed directly or reloaded
    const orderIdParam = searchParams.get("orderId");
    const fallbackOrder: OrderSnapshot = {
      orderCode: orderIdParam ? `#DNS-${orderIdParam.slice(0, 6)}` : "#DNS-89412",
      customerName: "Khách Hàng DANASEA",
      phone: "0912 345 678",
      email: "khachhang@danasea.vn",
      paymentProvider: "VNPAY",
      items: [
        {
          id: "item-1",
          serviceName: "Lặn ngắm san hô Bán đảo Sơn Trà",
          thumbnailUrl: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800",
          unitPrice: 450000,
          quantity: 2,
          date: "26/10/2026",
          timeSlot: "08:00 - 10:00",
          locationName: "Bán đảo Sơn Trà, TP. Đà Nẵng",
          vendorName: "DanaSea Diving Club",
          ticketCode: "TKT-89412-01",
        },
      ],
      rawTotal: 900000,
      discountAmount: 0,
      appliedPromoCode: null,
      finalTotal: 900000,
      paidAt: "08:35 • Hôm nay",
    };
    setOrder(fallbackOrder);
  }, [searchParams]);

  if (!order) return null;

  const handleSaveTicket = (ticketCode: string) => {
    setSavedTicketId(ticketCode);
    setTimeout(() => setSavedTicketId(null), 3000);
  };

  const getProviderLabel = (provider: string) => {
    switch (provider) {
      case "MOMO":
        return "Ví điện tử MoMo";
      case "SEPAY":
        return "VietQR 24/7 (SePay)";
      case "PAYPAL":
        return "Cổng PayPal";
      case "VNPAY":
      default:
        return "Cổng VNPAY (QR Pay / Thẻ)";
    }
  };

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar */}
        <section className="w-full bg-surface-container-lowest/80 backdrop-blur-md shadow-sm border-b border-slate-100">
          <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-md">
            <div className="flex items-center justify-between gap-space-md flex-wrap mb-space-sm">
              <div className="flex items-center gap-space-xs font-label-md text-xs text-slate-500">
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate("/")}>
                  DANASEA
                </span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate("/cart")}>
                  Giỏ hàng
                </span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="text-cyan-700 font-bold">
                  {isSuccess ? "Nhận vé điện tử QR" : "Kết quả thanh toán"}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm items-center">
              {/* Step 1 */}
              <div 
                onClick={() => navigate("/cart")}
                className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2 cursor-pointer hover:bg-slate-100/90 hover:border-slate-300 transition-all group"
                title="Quay lại Giỏ hàng"
              >
                <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span className="material-symbols-outlined text-[18px]">check</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-emerald-600 font-bold uppercase tracking-wider">Bước 1 - Hoàn thành</p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Giỏ hàng</p>
                </div>
              </div>

              {/* Step 2 */}
              <div 
                onClick={() => navigate("/checkout")}
                className={`flex items-center gap-space-sm rounded-xl border p-2 cursor-pointer transition-all ${
                  isSuccess 
                    ? "bg-slate-50 border-slate-100 hover:bg-slate-100/90" 
                    : "bg-cyan-50/80 border-cyan-200 shadow-sm"
                }`}
                title="Quay lại Bước 2: Thông tin & Thanh toán"
              >
                <div className={`w-8 h-8 rounded-full text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm ${
                  isSuccess ? "bg-emerald-600" : "bg-cyan-600"
                }`}>
                  {isSuccess ? (
                    <span className="material-symbols-outlined text-[18px]">check</span>
                  ) : (
                    <span>2</span>
                  )}
                </div>
                <div className="min-w-0">
                  <p className={`font-label-sm text-[11px] font-bold uppercase tracking-wider ${
                    isSuccess ? "text-emerald-600" : "text-cyan-600"
                  }`}>
                    {isSuccess ? "Bước 2 - Hoàn thành" : "Bước 2 - Cần xử lý"}
                  </p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>

              {/* Step 3 */}
              <div className={`flex items-center gap-space-sm rounded-xl border p-2 ${
                isSuccess 
                  ? "bg-cyan-50/80 border-cyan-200 shadow-sm" 
                  : isCancelled 
                  ? "bg-amber-50/80 border-amber-200 shadow-sm" 
                  : "bg-rose-50/80 border-rose-200 shadow-sm"
              }`}>
                <div className={`w-8 h-8 rounded-full text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm ${
                  isSuccess ? "bg-emerald-600" : isCancelled ? "bg-amber-600" : "bg-rose-600"
                }`}>
                  {isSuccess ? (
                    <span className="material-symbols-outlined text-[18px]">check</span>
                  ) : isCancelled ? (
                    <span className="material-symbols-outlined text-[18px]">close</span>
                  ) : (
                    <span className="material-symbols-outlined text-[18px]">priority_high</span>
                  )}
                </div>
                <div className="min-w-0">
                  <p className={`font-label-sm text-[11px] font-bold uppercase tracking-wider ${
                    isSuccess ? "text-emerald-600" : isCancelled ? "text-amber-700" : "text-rose-700"
                  }`}>
                    {isSuccess ? "Bước 3 - Hoàn thành" : isCancelled ? "Bước 3 - Đã hủy" : "Bước 3 - Chưa hoàn tất"}
                  </p>
                  <p className={`font-headline-sm text-xs font-bold truncate ${
                    isSuccess ? "text-cyan-900" : isCancelled ? "text-amber-900" : "text-rose-900"
                  }`}>
                    {isSuccess ? "Nhận vé điện tử QR" : isCancelled ? "Thanh toán đã hủy" : "Thanh toán thất bại"}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Content Body */}
        <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-xl sm:py-space-2xl w-full">
          
          {/* ==================== SCENARIO 1: SUCCESS ==================== */}
          {isSuccess && (
            <>
              {/* SUCCESS HERO BANNER WITH ANIMATED CHECKMARK DRAW */}
              <div className="flex flex-col items-center text-center mb-space-2xl">
                <style>{`
                  @keyframes checkmark-circle {
                    0% {
                      stroke-dashoffset: 160;
                      transform: rotate(-90deg);
                    }
                    100% {
                      stroke-dashoffset: 0;
                      transform: rotate(0deg);
                    }
                  }
                  @keyframes checkmark-check {
                    0% {
                      stroke-dashoffset: 40;
                      opacity: 0;
                    }
                    40% {
                      opacity: 1;
                    }
                    100% {
                      stroke-dashoffset: 0;
                      opacity: 1;
                    }
                  }
                  @keyframes checkmark-scale {
                    0%, 100% {
                      transform: scale(1);
                    }
                    50% {
                      transform: scale(1.08);
                    }
                  }
                  @keyframes hero-fade-up {
                    0% {
                      opacity: 0;
                      transform: translateY(10px);
                    }
                    100% {
                      opacity: 1;
                      transform: translateY(0);
                    }
                  }
                  .anim-check-circle {
                    stroke-dasharray: 160;
                    stroke-dashoffset: 160;
                    animation: checkmark-circle 0.6s cubic-bezier(0.65, 0, 0.45, 1) forwards;
                    transform-origin: 50% 50%;
                  }
                  .anim-check-tick {
                    stroke-dasharray: 40;
                    stroke-dashoffset: 40;
                    animation: checkmark-check 0.4s 0.45s cubic-bezier(0.65, 0, 0.45, 1) forwards;
                  }
                  .anim-check-container {
                    animation: checkmark-scale 0.4s 0.75s ease-in-out both;
                  }
                  .anim-fade-up-1 {
                    opacity: 0;
                    animation: hero-fade-up 0.5s 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                  }
                  .anim-fade-up-2 {
                    opacity: 0;
                    animation: hero-fade-up 0.5s 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                  }
                `}</style>

                {/* Animated Green Circle Checkmark */}
                <div className="relative mb-3 flex items-center justify-center">
                  <div className="absolute w-20 h-20 rounded-full bg-emerald-100/50 animate-ping opacity-25 pointer-events-none" />
                  <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-emerald-50 border border-emerald-100 flex items-center justify-center shadow-lg shadow-emerald-500/10 anim-check-container relative z-10">
                    <svg className="w-10 h-10 sm:w-12 sm:h-12 text-emerald-600" viewBox="0 0 52 52">
                      <circle
                        className="anim-check-circle"
                        cx="26"
                        cy="26"
                        r="23"
                        fill="none"
                        stroke="#10b981"
                        strokeWidth="3.5"
                      />
                      <path
                        className="anim-check-tick"
                        fill="none"
                        stroke="#059669"
                        strokeWidth="4"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M15 27l7 7 15-15"
                      />
                    </svg>
                  </div>
                </div>

                <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight max-w-2xl anim-fade-up-1">
                  Thanh toán &amp; Xác nhận đặt chỗ thành công!
                </h1>

                {/* Notification Notice */}
                <div className="mt-3 p-2 px-4 rounded-xl bg-cyan-50/60 border border-cyan-100 text-slate-700 inline-flex items-center gap-2 text-xs sm:text-sm anim-fade-up-2">
                  <span className="material-symbols-outlined text-cyan-700 text-[18px] flex-shrink-0">mark_email_read</span>
                  <span>Vé đã được gửi về email</span>
                </div>
              </div>

              {/* MAIN GRID: TICKETS & ORDER SUMMARY */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
                {/* TICKETS COLUMN (8 cols): Single Unified Card */}
                <div className="lg:col-span-8 bg-surface-container-lowest rounded-2xl shadow-sm border border-slate-100 p-space-xl flex flex-col">
                  {/* Card Header: Check-in */}
                  <div className="flex items-center justify-between border-b pb-space-sm border-slate-100 mb-space-lg">
                    <h3 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">
                      Check-in
                    </h3>
                    <span className="px-2.5 py-0.5 rounded-full bg-cyan-50 text-cyan-800 font-label-sm text-xs font-bold border border-cyan-100">
                      {order.items.length} Vé dịch vụ
                    </span>
                  </div>

                  {/* TICKET LIST - Separated by clean horizontal divider */}
                  <div className="flex flex-col divide-y divide-slate-100">
                    {order.items.map((item, index) => (
                      <div
                        key={item.id || index}
                        className={`grid grid-cols-1 md:grid-cols-12 gap-space-lg items-center ${
                          index > 0 ? "pt-space-lg" : ""
                        } ${index < order.items.length - 1 ? "pb-space-lg" : ""}`}
                      >
                        {/* Left details */}
                        <div className="md:col-span-7 flex flex-col gap-space-md">
                          <div className="flex items-start gap-space-sm">
                            <div className="w-20 h-20 rounded-xl overflow-hidden flex-shrink-0 bg-slate-100 shadow-inner">
                              <img alt={item.serviceName} className="w-full h-full object-cover" src={item.thumbnailUrl} />
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="px-2 py-0.5 rounded-full bg-cyan-50 text-cyan-700 text-[10px] font-bold">
                                Trải nghiệm biển Đà Nẵng
                              </span>
                              <h4 className="font-bold text-sm sm:text-base text-slate-900 leading-tight mt-1 truncate">
                                {item.serviceName}
                              </h4>
                              <p className="text-xs text-slate-500 mt-1 truncate">
                                Nhà cung cấp: <strong className="text-cyan-800">{item.vendorName}</strong>
                              </p>
                            </div>
                          </div>

                          {/* Detail metadata boxes */}
                          <div className="grid grid-cols-2 gap-space-sm p-space-sm rounded-xl bg-slate-50 border border-slate-100 text-xs">
                            <div>
                              <span className="text-[11px] text-slate-400 block">Thời gian:</span>
                              <p className="font-semibold text-slate-800 flex items-center gap-1 mt-0.5">
                                <span className="material-symbols-outlined text-[15px] text-cyan-700">schedule</span>
                                {item.timeSlot}
                              </p>
                              <span className="text-[11px] text-slate-500">{item.date}</span>
                            </div>
                            <div>
                              <span className="text-[11px] text-slate-400 block">Số lượng &amp; Chi phí:</span>
                              <p className="font-semibold text-slate-800 flex items-center gap-1 mt-0.5">
                                <span className="material-symbols-outlined text-[15px] text-cyan-700">group</span>
                                {item.quantity} vé khách
                              </p>
                              <span className="text-[11px] text-cyan-700 font-bold">
                                {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                              </span>
                            </div>
                          </div>

                          {/* Meeting location */}
                          <div className="flex items-start gap-1.5 text-xs text-slate-600">
                            <span className="material-symbols-outlined text-cyan-700 text-[18px] flex-shrink-0 mt-0.5">
                              location_on
                            </span>
                            <div>
                              <strong className="text-slate-900">Điểm đón / tập kết:</strong> {item.locationName}
                            </div>
                          </div>

                          {/* Action buttons */}
                          <div className="flex flex-wrap items-center gap-2 pt-1">
                            <button
                              onClick={() => handleSaveTicket(item.ticketCode)}
                              type="button"
                              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-50 hover:bg-cyan-100 text-cyan-800 font-medium text-xs transition-colors cursor-pointer"
                            >
                              <span className="material-symbols-outlined text-[16px]">download</span>
                              {savedTicketId === item.ticketCode ? "Đã lưu vé vào máy!" : "Lưu ảnh vé QR"}
                            </button>
                          </div>
                        </div>

                        {/* Right QR Box */}
                        <div className="md:col-span-5 flex flex-col items-center justify-center p-space-md rounded-2xl bg-cyan-50/40 border border-cyan-100 text-center">
                          <div className="p-3 bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col items-center">
                            <svg className="w-32 h-32 text-slate-800" fill="currentColor" viewBox="0 0 100 100">
                              <rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="10" y="10" />
                              <rect fill="currentColor" height="12" rx="2" width="12" x="16" y="16" />
                              <rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="66" y="10" />
                              <rect fill="currentColor" height="12" rx="2" width="12" x="72" y="16" />
                              <rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="10" y="66" />
                              <rect fill="currentColor" height="12" rx="2" width="12" x="16" y="72" />
                              <rect height="6" rx="1" width="6" x="42" y="12" />
                              <rect height="6" rx="1" width="6" x="52" y="12" />
                              <rect height="10" rx="1" width="6" x="42" y="24" />
                              <rect height="6" rx="1" width="8" x="12" y="42" />
                              <rect height="6" rx="1" width="12" x="24" y="42" />
                              <rect fill="#0891b2" height="16" rx="2" width="16" x="42" y="42" />
                              <rect height="6" rx="1" width="10" x="64" y="42" />
                              <rect height="6" rx="1" width="12" x="78" y="42" />
                              <rect height="12" rx="1" width="6" x="42" y="64" />
                              <rect height="6" rx="1" width="12" x="52" y="70" />
                              <rect height="6" rx="1" width="20" x="70" y="66" />
                              <rect height="12" rx="1" width="8" x="70" y="78" />
                              <rect height="12" rx="1" width="8" x="82" y="78" />
                            </svg>
                            <span className="font-mono text-xs font-bold text-slate-800 mt-2">#{item.ticketCode}</span>
                            <span className="text-[10px] text-cyan-700 font-semibold uppercase tracking-wider">MÃ VÉ CHECK-IN</span>
                          </div>
                          <p className="text-[11px] text-slate-500 mt-2 max-w-[180px] leading-tight">
                            Quét mã này tại quầy điều hành cano để lên thuyền
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* SIDEBAR SUMMARY (4 cols): Single Unified Card */}
                <div className="lg:col-span-4 flex flex-col">
                  <div className="bg-surface-container-lowest p-space-xl rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-space-md">
                    <div className="flex items-center justify-between pb-space-xs border-b border-slate-100">
                      <h4 className="font-bold text-sm sm:text-base text-slate-900">Chi tiết thanh toán</h4>
                      <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold border border-emerald-200">
                        Đã thanh toán
                      </span>
                    </div>

                    <div className="flex flex-col gap-2 text-xs text-slate-600">
                      {order.items.map((i, idx) => (
                        <div key={idx} className="flex justify-between items-start">
                          <span className="truncate pr-2">
                            {i.serviceName} ({i.quantity} vé)
                          </span>
                          <span className="font-medium text-slate-900 flex-shrink-0">
                            {(i.unitPrice * i.quantity).toLocaleString("vi-VN")}₫
                          </span>
                        </div>
                      ))}

                      {order.discountAmount > 0 && (
                        <div className="flex justify-between items-center text-emerald-600 font-bold">
                          <span>Ưu đãi ({order.appliedPromoCode})</span>
                          <span>-{order.discountAmount.toLocaleString("vi-VN")}₫</span>
                        </div>
                      )}

                      <div className="flex justify-between items-center text-slate-500">
                        <span>Phí dịch vụ</span>
                        <span className="text-emerald-600 font-medium">Miễn phí</span>
                      </div>

                      <div className="pt-3 border-t border-slate-100 mt-1 flex justify-between items-baseline">
                        <div>
                          <span className="text-sm font-bold text-slate-900">Tổng thanh toán:</span>
                          <p className="text-[11px] text-slate-400">Đã bao gồm thuế &amp; bảo hiểm</p>
                        </div>
                        <span className="text-xl sm:text-2xl font-black text-cyan-700">
                          {order.finalTotal.toLocaleString("vi-VN")}₫
                        </span>
                      </div>
                    </div>

                    <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-100 flex items-center gap-2 text-xs text-slate-600">
                      <span className="material-symbols-outlined text-emerald-600 text-[18px]">verified_user</span>
                      <span>Đã thanh toán an toàn qua {getProviderLabel(order.paymentProvider)}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* BOTTOM ACTIONS BAR: Clean footer row beneath the two cards */}
              <div className="mt-space-2xl pt-space-lg border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-2 text-xs text-slate-500">
                  <span>Cần hỗ trợ về vé hoặc lịch trình?</span>
                  <a className="text-cyan-700 font-bold hover:underline flex items-center gap-1" href="tel:19008468">
                    <span className="material-symbols-outlined text-[15px]">call</span>
                    <span>Hotline: 1900 8468</span>
                  </a>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => navigate("/profile/orders")}
                    type="button"
                    className="px-5 py-3 rounded-full border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 font-bold text-xs transition-colors cursor-pointer"
                  >
                    Xem đơn hàng của tôi
                  </button>
                  <button
                    onClick={() => navigate("/")}
                    type="button"
                    className="px-6 py-3 rounded-full bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white font-bold text-xs shadow-md transition-all cursor-pointer hover:scale-[1.01]"
                  >
                    Tiếp tục khám phá trải nghiệm
                  </button>
                </div>
              </div>
            </>
          )}

          {/* ==================== SCENARIO 2: CANCELLED BY USER ==================== */}
          {isCancelled && (
            <div className="flex flex-col items-center">
              <div className="flex flex-col items-center text-center mb-space-xl">
                <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center shadow-md mb-3">
                  <span className="material-symbols-outlined text-[40px]">cancel</span>
                </div>
                <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight max-w-2xl">
                  Giao dịch thanh toán đã bị hủy
                </h1>
                <p className="text-slate-600 text-sm mt-2 max-w-md">
                  Quý khách đã hủy phiên thanh toán trên cổng VNPay. Suất trải nghiệm vẫn đang được bảo lưu trong thời gian giữ chỗ.
                </p>
                <div className="mt-3 p-2 px-4 rounded-xl bg-amber-50/80 border border-amber-200 text-amber-800 inline-flex items-center gap-2 text-xs">
                  <span className="material-symbols-outlined text-amber-600 text-[18px]">info</span>
                  <span>Đơn hàng chưa hoàn tất thanh toán. Vui lòng thử lại để nhận vé.</span>
                </div>
              </div>

              {/* Summary Card for Retry */}
              <div className="w-full max-w-2xl bg-surface-container-lowest rounded-2xl shadow-sm border border-slate-100 p-space-xl flex flex-col gap-space-lg">
                <div className="flex items-center justify-between border-b pb-space-sm border-slate-100">
                  <h3 className="font-bold text-base text-slate-900">Chi tiết dịch vụ chờ thanh toán</h3>
                  <span className="font-mono text-xs font-bold text-amber-700 bg-amber-50 px-2.5 py-1 rounded-full border border-amber-200">
                    Chờ thanh toán
                  </span>
                </div>

                <div className="flex flex-col divide-y divide-slate-100">
                  {order.items.map((item, idx) => (
                    <div key={idx} className="py-3 flex items-center justify-between gap-4 first:pt-0 last:pb-0">
                      <div className="flex items-center gap-3 min-w-0">
                        <img src={item.thumbnailUrl} alt={item.serviceName} className="w-14 h-14 rounded-lg object-cover flex-shrink-0" />
                        <div className="min-w-0">
                          <p className="font-bold text-sm text-slate-900 truncate">{item.serviceName}</p>
                          <p className="text-xs text-slate-500">{item.timeSlot} • {item.date}</p>
                          <p className="text-xs text-slate-500">{item.quantity} vé khách</p>
                        </div>
                      </div>
                      <span className="font-bold text-sm text-slate-900 flex-shrink-0">
                        {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                      </span>
                    </div>
                  ))}
                </div>

                <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                  <span className="font-bold text-sm text-slate-700">Tổng thanh toán:</span>
                  <span className="text-xl font-black text-cyan-700">
                    {order.finalTotal.toLocaleString("vi-VN")}₫
                  </span>
                </div>

                {/* Retry action buttons */}
                <div className="pt-2 flex flex-col sm:flex-row gap-3">
                  <button
                    onClick={() => navigate("/cart")}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-full border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold text-xs transition-colors cursor-pointer text-center"
                  >
                    Quay về giỏ hàng
                  </button>
                  <button
                    onClick={() => navigate("/checkout")}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-full bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white font-bold text-xs shadow-md transition-all cursor-pointer hover:scale-[1.01] text-center"
                  >
                    Thanh toán lại ngay
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ==================== SCENARIO 3: PAYMENT FAILED (DECLINED) ==================== */}
          {isFailed && (
            <div className="flex flex-col items-center">
              <div className="flex flex-col items-center text-center mb-space-xl">
                <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-rose-50 border border-rose-200 text-rose-600 flex items-center justify-center shadow-md mb-3">
                  <span className="material-symbols-outlined text-[40px]">error</span>
                </div>
                <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight max-w-2xl">
                  Thanh toán không thành công
                </h1>
                <p className="text-slate-600 text-sm mt-2 max-w-md">
                  {getFailureReason(responseCodeParam)}
                </p>
                <div className="mt-3 p-2 px-4 rounded-xl bg-rose-50/80 border border-rose-200 text-rose-800 inline-flex items-center gap-2 text-xs">
                  <span className="material-symbols-outlined text-rose-600 text-[18px]">warning</span>
                  <span>Mã phản hồi từ cổng: {responseCodeParam || "GATEWAY_ERROR"}</span>
                </div>
              </div>

              {/* Summary Card for Retry */}
              <div className="w-full max-w-2xl bg-surface-container-lowest rounded-2xl shadow-sm border border-slate-100 p-space-xl flex flex-col gap-space-lg">
                <div className="flex items-center justify-between border-b pb-space-sm border-slate-100">
                  <h3 className="font-bold text-base text-slate-900">Chi tiết dịch vụ chưa hoàn tất</h3>
                  <span className="font-mono text-xs font-bold text-rose-700 bg-rose-50 px-2.5 py-1 rounded-full border border-rose-200">
                    Giao dịch thất bại
                  </span>
                </div>

                <div className="flex flex-col divide-y divide-slate-100">
                  {order.items.map((item, idx) => (
                    <div key={idx} className="py-3 flex items-center justify-between gap-4 first:pt-0 last:pb-0">
                      <div className="flex items-center gap-3 min-w-0">
                        <img src={item.thumbnailUrl} alt={item.serviceName} className="w-14 h-14 rounded-lg object-cover flex-shrink-0" />
                        <div className="min-w-0">
                          <p className="font-bold text-sm text-slate-900 truncate">{item.serviceName}</p>
                          <p className="text-xs text-slate-500">{item.timeSlot} • {item.date}</p>
                          <p className="text-xs text-slate-500">{item.quantity} vé khách</p>
                        </div>
                      </div>
                      <span className="font-bold text-sm text-slate-900 flex-shrink-0">
                        {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                      </span>
                    </div>
                  ))}
                </div>

                <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                  <span className="font-bold text-sm text-slate-700">Tổng tiền chưa thanh toán:</span>
                  <span className="text-xl font-black text-rose-600">
                    {order.finalTotal.toLocaleString("vi-VN")}₫
                  </span>
                </div>

                {/* Instructions */}
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600">
                  <p className="font-medium text-slate-800 mb-1">Gợi ý xử lý:</p>
                  <ul className="list-disc list-inside space-y-1 text-slate-500">
                    <li>Kiểm tra lại số dư khả dụng của tài khoản ngân hàng.</li>
                    <li>Đảm bảo đã kích hoạt tính năng thanh toán trực tuyến (Internet Banking/E-commerce).</li>
                    <li>Thử đổi sang phương thức thanh toán khác (PayPal hoặc thẻ ngân hàng khác).</li>
                  </ul>
                </div>

                {/* Action buttons */}
                <div className="pt-2 flex flex-col sm:flex-row gap-3">
                  <a
                    href="tel:19008468"
                    className="flex-1 py-3 px-4 rounded-full border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold text-xs transition-colors cursor-pointer text-center flex items-center justify-center gap-1.5"
                  >
                    <span className="material-symbols-outlined text-[16px]">call</span>
                    <span>Hotline: 1900 8468</span>
                  </a>
                  <button
                    onClick={() => navigate("/checkout")}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-full bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white font-bold text-xs shadow-md transition-all cursor-pointer hover:scale-[1.01] text-center"
                  >
                    Đổi phương thức &amp; Thử lại
                  </button>
                </div>
              </div>
            </div>
          )}

        </div>

        {/* ==================== TEMPORARY DEV SCENARIO SWITCHER ==================== */}
        {showDevSwitcher && (
          <aside aria-label="Bộ chuyển kịch bản thử nghiệm" className="fixed bottom-4 right-4 z-50 bg-slate-900/95 backdrop-blur-md text-white p-3.5 rounded-2xl shadow-2xl border border-slate-700 flex flex-col gap-2 max-w-xs animate-fade-in text-xs">
            <div className="flex items-center justify-between pb-1.5 border-b border-slate-700 gap-4">
              <div className="flex items-center gap-1.5 font-bold text-cyan-400">
                <span className="material-symbols-outlined text-[16px]">tune</span>
                <span>Kiểm tra kịch bản (Preview)</span>
              </div>
              <button 
                onClick={() => setShowDevSwitcher(false)} 
                className="text-slate-400 hover:text-white transition-colors cursor-pointer"
                title="Đóng thanh chuyển kịch bản"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">close</span>
              </button>
            </div>

            <p className="text-[10px] text-slate-400 leading-tight">
              Bấm nhanh để xem trước giao diện các trường hợp phản hồi từ cổng thanh toán:
            </p>

            <div className="grid grid-cols-1 gap-1.5 pt-1">
              <button
                onClick={() => setSearchParams({ status: "success" })}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  isSuccess ? "bg-emerald-600 text-white" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>1. Thành công 100% (QR Vé)</span>
                {isSuccess && <span className="material-symbols-outlined text-[14px]">check</span>}
              </button>

              <button
                onClick={() => setSearchParams({ status: "cancelled", vnp_ResponseCode: "24" })}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  isCancelled ? "bg-amber-600 text-white" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>2. Khách hủy trên cổng (Mã 24)</span>
                {isCancelled && <span className="material-symbols-outlined text-[14px]">check</span>}
              </button>

              <button
                onClick={() => setSearchParams({ status: "failed", vnp_ResponseCode: "51" })}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  isFailed && responseCodeParam === "51" ? "bg-rose-600 text-white" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>3. Lỗi thẻ không đủ số dư (Mã 51)</span>
                {isFailed && responseCodeParam === "51" && <span className="material-symbols-outlined text-[14px]">check</span>}
              </button>

              <button
                onClick={() => setSearchParams({ status: "failed", vnp_ResponseCode: "10" })}
                className={`w-full text-left px-2.5 py-1.5 rounded-lg font-medium transition-colors cursor-pointer flex items-center justify-between ${
                  isFailed && responseCodeParam === "10" ? "bg-rose-600 text-white" : "bg-slate-800 hover:bg-slate-700 text-slate-200"
                }`}
                type="button"
              >
                <span>4. Lỗi nhập sai OTP (Mã 10)</span>
                {isFailed && responseCodeParam === "10" && <span className="material-symbols-outlined text-[14px]">check</span>}
              </button>

              <button
                onClick={() => navigate("/checkout")}
                className="w-full text-left px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 font-medium transition-colors cursor-pointer flex items-center justify-between"
                type="button"
              >
                <span>5. Về Bước 2 (Kiểm tra hết hạn 15p)</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>
          </aside>
        )}

      </div>
    </main>
  );
}
