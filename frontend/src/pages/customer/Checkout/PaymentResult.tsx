import { useCartStore } from "../../../store/useCartStore";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useState, useEffect } from "react";
import {
  CheckCircle2,
  Download,
  Phone,
  ArrowRight,
  Clock,
  MapPin,
  ShieldCheck,
  AlertCircle,
  XCircle,
  ChevronRight,
  MailCheck,
  Users,
  RotateCcw,
} from "lucide-react";

interface OrderItemSnapshot {
  id: string;
  serviceId?: string;
  slotId?: string;
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
  const [searchParams] = useSearchParams();
  const { items: cartItems, addItem, clearCart } = useCartStore();
  const [order, setOrder] = useState<OrderSnapshot | null>(null);
  const [savedTicketId, setSavedTicketId] = useState<string | null>(null);

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

  // Only clear cart when payment is confirmed SUCCESSFUL
  useEffect(() => {
    if (isSuccess && cartItems.length > 0) {
      clearCart();
    }
  }, [isSuccess, cartItems.length, clearCart]);

  const handleRetryCheckout = () => {
    if (cartItems.length === 0 && order?.items && order.items.length > 0) {
      order.items.forEach((item) => {
        addItem({
          serviceId: item.serviceId || "4b79c4cd-a138-59ca-83c3-d396c1208ac4",
          slotId: item.slotId || "slot-demo-1",
          serviceName: item.serviceName,
          thumbnailUrl: item.thumbnailUrl,
          date: item.date,
          timeSlot: item.timeSlot,
          unitPrice: item.unitPrice,
          quantity: item.quantity,
          locationName: item.locationName,
          vendorName: item.vendorName,
        });
      });
    }
    navigate("/checkout");
  };

  const handleReturnToCart = () => {
    if (cartItems.length === 0 && order?.items && order.items.length > 0) {
      order.items.forEach((item) => {
        addItem({
          serviceId: item.serviceId || "4b79c4cd-a138-59ca-83c3-d396c1208ac4",
          slotId: item.slotId || "slot-demo-1",
          serviceName: item.serviceName,
          thumbnailUrl: item.thumbnailUrl,
          date: item.date,
          timeSlot: item.timeSlot,
          unitPrice: item.unitPrice,
          quantity: item.quantity,
          locationName: item.locationName,
          vendorName: item.vendorName,
        });
      });
    }
    navigate("/cart");
  };

  const handleSaveTicket = (ticketCode: string) => {
    setSavedTicketId(ticketCode);
    setTimeout(() => {
      setSavedTicketId(null);
    }, 2500);
  };

  const getProviderLabel = (provider?: string) => {
    if (provider === "MOMO") return "Ví MoMo";
    if (provider === "PAYPAL") return "Cổng PayPal";
    if (provider === "SEPAY") return "Chuyển khoản SePay QR";
    return "Cổng VNPAY (QR Pay / Thẻ ATM)";
  };

  if (!order) return null;

  return (
    <main className="w-full pt-24 bg-white min-h-[calc(100vh-80px)] flex-1">
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
                onClick={() => navigate('/profile/orders')}
              >
                Đơn hàng
              </span>
              <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
              <span className="text-slate-900 font-semibold">Kết quả thanh toán</span>
            </div>

            {/* Stepper Component */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center">
              <div 
                onClick={() => navigate('/cart')}
                className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3 cursor-pointer hover:bg-slate-100 transition-colors"
              >
                <span className="text-sm font-semibold text-emerald-700">01.</span>
                <div className="min-w-0">
                  <p className="text-xs text-emerald-700 font-medium">Bước 1 (Đã chọn)</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Giỏ hàng</p>
                </div>
              </div>

              <div 
                onClick={() => navigate('/checkout')}
                className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3 cursor-pointer hover:bg-slate-100 transition-colors"
              >
                <span className="text-sm font-semibold text-emerald-700">02.</span>
                <div className="min-w-0">
                  <p className="text-xs text-emerald-700 font-medium">Bước 2 (Đã chọn)</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-xl bg-white border border-slate-300 p-3 shadow-xs">
                <span className="text-sm font-bold text-slate-900">03.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-500 font-medium">Bước 3</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Nhận vé điện tử QR</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Content Area */}
        <div className="w-full max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
          
          {/* ==================== SCENARIO 1: SUCCESSFUL PAYMENT ==================== */}
          {isSuccess && (
            <div className="flex flex-col gap-8">
              {/* Hero Status Banner */}
              <div className="flex flex-col items-center text-center">
                <div className="w-16 h-16 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-600 mb-3 shadow-xs">
                  <CheckCircle2 className="w-8 h-8" />
                </div>
                <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 tracking-tight">
                  Thanh toán &amp; Xác nhận đặt chỗ thành công
                </h1>
                <p className="text-sm text-slate-600 mt-1 max-w-md">
                  Mã đơn hàng: <strong className="text-slate-900 font-mono">{order.orderCode}</strong>
                </p>
                <div className="mt-3 px-3.5 py-1.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-700 inline-flex items-center gap-2 text-xs">
                  <MailCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Xác nhận đặt chỗ &amp; mã vé QR đã được gửi về {order.email}</span>
                </div>
              </div>

              {/* Main Content Grid: Tickets & Order Summary */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                {/* Tickets Column (8 cols) */}
                <div className="lg:col-span-8 bg-white rounded-2xl shadow-xs border border-slate-200/80 p-6 flex flex-col gap-6">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                    <h2 className="text-lg font-bold text-slate-900">
                      Vé điện tử check-in
                    </h2>
                    <span className="px-2.5 py-1 rounded-md bg-slate-50 text-slate-700 text-xs font-semibold border border-slate-200">
                      {order.items.length} vé dịch vụ
                    </span>
                  </div>

                  {/* Ticket List */}
                  <div className="flex flex-col divide-y divide-slate-100">
                    {order.items.map((item, index) => (
                      <div
                        key={item.id || index}
                        className={`grid grid-cols-1 sm:grid-cols-12 gap-6 items-center ${
                          index > 0 ? "pt-6" : ""
                        } ${index < order.items.length - 1 ? "pb-6" : ""}`}
                      >
                        {/* Left Details */}
                        <div className="sm:col-span-7 flex flex-col gap-3">
                          <div className="flex items-start gap-3">
                            <div className="w-20 h-20 rounded-xl overflow-hidden shrink-0 bg-slate-100 border border-slate-100">
                              <img alt={item.serviceName} className="w-full h-full object-cover" src={item.thumbnailUrl} />
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="text-[11px] font-semibold text-slate-500">
                                Trải nghiệm biển Đà Nẵng
                              </span>
                              <h3 className="font-bold text-sm sm:text-base text-slate-900 leading-tight mt-0.5 truncate">
                                {item.serviceName}
                              </h3>
                              <p className="text-xs text-slate-600 mt-1 truncate">
                                Đối tác tổ chức: <strong className="text-slate-900 font-medium">{item.vendorName}</strong>
                              </p>
                            </div>
                          </div>

                          {/* Metadata Details */}
                          <div className="grid grid-cols-2 gap-2 p-3 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-600">
                            <div>
                              <span className="text-xs text-slate-600 font-medium block">Thời gian:</span>
                              <p className="font-semibold text-slate-900 flex items-center gap-1 mt-0.5">
                                <Clock className="w-3.5 h-3.5 text-slate-500" />
                                {item.timeSlot}
                              </p>
                              <span className="text-xs text-slate-600">{item.date}</span>
                            </div>
                            <div>
                              <span className="text-xs text-slate-600 font-medium block">Số lượng &amp; Chi phí:</span>
                              <p className="font-semibold text-slate-900 flex items-center gap-1 mt-0.5">
                                <Users className="w-3.5 h-3.5 text-slate-500" />
                                {item.quantity} vé khách
                              </p>
                              <span className="text-xs text-slate-900 font-bold tabular-nums">
                                {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                              </span>
                            </div>
                          </div>

                          {/* Meeting Location */}
                          <div className="flex items-start gap-1.5 text-xs text-slate-600">
                            <MapPin className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
                            <div>
                              <strong className="text-slate-900">Điểm đón / tập kết:</strong> {item.locationName}
                            </div>
                          </div>

                          {/* Action Button */}
                          <div className="flex items-center gap-2 pt-1">
                            <button
                              onClick={() => handleSaveTicket(item.ticketCode)}
                              type="button"
                              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 text-slate-800 font-medium text-xs border border-slate-200 transition-colors cursor-pointer"
                            >
                              <Download className="w-3.5 h-3.5" />
                              {savedTicketId === item.ticketCode ? "Đã lưu mã vé!" : "Lưu ảnh vé QR"}
                            </button>
                          </div>
                        </div>

                        {/* Right QR Box */}
                        <div className="sm:col-span-5 flex flex-col items-center justify-center p-4 rounded-xl bg-slate-50 border border-slate-200/80 text-center">
                          <div className="p-3 bg-white rounded-xl shadow-2xs border border-slate-200 flex flex-col items-center">
                            <svg className="w-28 h-28 text-slate-900" fill="currentColor" viewBox="0 0 100 100">
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
                              <rect fill="#0284c7" height="16" rx="2" width="16" x="42" y="42" />
                              <rect height="6" rx="1" width="10" x="64" y="42" />
                              <rect height="6" rx="1" width="12" x="78" y="42" />
                              <rect height="12" rx="1" width="6" x="42" y="64" />
                              <rect height="6" rx="1" width="12" x="52" y="70" />
                              <rect height="6" rx="1" width="20" x="70" y="66" />
                              <rect height="12" rx="1" width="8" x="70" y="78" />
                              <rect height="12" rx="1" width="8" x="82" y="78" />
                            </svg>
                            <span className="font-mono text-xs font-bold text-slate-900 mt-2">#{item.ticketCode}</span>
                            <span className="text-xs text-slate-600 font-medium">Mã check-in</span>
                          </div>
                          <p className="text-[11px] text-slate-500 mt-2 max-w-[180px] leading-tight">
                            Xuất trình mã này tại quầy điều hành cano để lên thuyền
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Sidebar Summary (4 cols) */}
                <div className="lg:col-span-4 flex flex-col gap-4">
                  <div className="bg-white p-6 rounded-2xl shadow-xs border border-slate-200/80 flex flex-col gap-4">
                    <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                      <h3 className="font-bold text-base text-slate-900">Chi tiết thanh toán</h3>
                      <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-xs font-semibold border border-emerald-200">
                        Đã thanh toán
                      </span>
                    </div>

                    <div className="flex flex-col gap-2.5 text-xs text-slate-600">
                      {order.items.map((i, idx) => (
                        <div key={idx} className="flex justify-between items-start">
                          <span className="truncate pr-2">
                            {i.serviceName} ({i.quantity} vé)
                          </span>
                          <span className="font-medium text-slate-900 tabular-nums shrink-0">
                            {(i.unitPrice * i.quantity).toLocaleString("vi-VN")}₫
                          </span>
                        </div>
                      ))}

                      {order.discountAmount > 0 && (
                        <div className="flex justify-between items-center text-emerald-700 font-bold">
                          <span>Ưu đãi ({order.appliedPromoCode})</span>
                          <span className="tabular-nums">-{order.discountAmount.toLocaleString("vi-VN")}₫</span>
                        </div>
                      )}

                      <div className="flex justify-between items-center text-slate-500">
                        <span>Phí dịch vụ</span>
                        <span className="text-emerald-700 font-medium">Miễn phí</span>
                      </div>

                      <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                        <div>
                          <span className="text-xs text-slate-700 font-semibold">Tổng thanh toán:</span>
                          <p className="text-xs text-slate-500">Đã gồm thuế &amp; bảo hiểm</p>
                        </div>
                        <span className="text-2xl font-extrabold text-slate-900 tabular-nums">
                          {order.finalTotal.toLocaleString("vi-VN")}₫
                        </span>
                      </div>
                    </div>

                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600 flex items-center gap-2">
                      <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                      <span>Thanh toán an toàn qua {getProviderLabel(order.paymentProvider)}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Bottom Actions Bar */}
              <div className="pt-6 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-2 text-xs text-slate-600">
                  <span>Cần hỗ trợ về vé hoặc lịch trình?</span>
                  <a className="text-slate-900 font-bold hover:underline flex items-center gap-1" href="tel:19008468">
                    <Phone className="w-3.5 h-3.5" />
                    <span>Hotline: 1900 8468</span>
                  </a>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => navigate("/profile/orders")}
                    type="button"
                    className="px-4 py-2.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-800 font-medium text-xs transition-colors cursor-pointer"
                  >
                    Xem đơn hàng của tôi
                  </button>
                  <button
                    onClick={() => navigate("/")}
                    type="button"
                    className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs shadow-xs transition-colors cursor-pointer flex items-center gap-1.5"
                  >
                    <span>Khám phá thêm trải nghiệm</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ==================== SCENARIO 2: CANCELLED BY USER ==================== */}
          {isCancelled && (
            <div className="flex flex-col items-center">
              <div className="flex flex-col items-center text-center mb-6">
                <div className="w-16 h-16 rounded-2xl bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center mb-3">
                  <XCircle className="w-8 h-8" />
                </div>
                <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 tracking-tight">
                  Giao dịch thanh toán đã bị hủy
                </h1>
                <p className="text-sm text-slate-600 mt-1 max-w-md">
                  Bạn có thể quay lại giỏ hàng hoặc thực hiện thanh toán lại bất kỳ lúc nào.
                </p>
              </div>

              {/* Summary Card for Retry */}
              <div className="w-full max-w-2xl bg-white rounded-2xl shadow-xs border border-slate-200/80 p-6 flex flex-col gap-6">
                <div className="flex items-center justify-between border-b pb-3 border-slate-100">
                  <h2 className="font-bold text-base text-slate-900">Chi tiết dịch vụ chờ thanh toán</h2>
                  <span className="text-xs font-semibold text-amber-800 bg-amber-50 px-2.5 py-1 rounded-md border border-amber-200">
                    Chờ thanh toán
                  </span>
                </div>

                <div className="flex flex-col divide-y divide-slate-100">
                  {order.items.map((item, idx) => (
                    <div key={idx} className="py-3 flex items-center justify-between gap-4 first:pt-0 last:pb-0">
                      <div className="flex items-center gap-3 min-w-0">
                        <img src={item.thumbnailUrl} alt={item.serviceName} className="w-14 h-14 rounded-xl object-cover shrink-0" />
                        <div className="min-w-0">
                          <p className="font-bold text-sm text-slate-900 truncate">{item.serviceName}</p>
                          <p className="text-xs text-slate-500">{item.timeSlot} • {item.date}</p>
                          <p className="text-xs text-slate-500">{item.quantity} vé khách</p>
                        </div>
                      </div>
                      <span className="font-bold text-sm text-slate-900 tabular-nums shrink-0">
                        {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                      </span>
                    </div>
                  ))}
                </div>

                <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                  <span className="font-bold text-xs text-slate-700">Tổng thanh toán:</span>
                  <span className="text-2xl font-bold text-slate-900 tabular-nums">
                    {order.finalTotal.toLocaleString("vi-VN")}₫
                  </span>
                </div>

                <div className="pt-2 flex flex-col sm:flex-row gap-3">
                  <button
                    onClick={handleReturnToCart}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-800 font-medium text-xs transition-colors cursor-pointer text-center"
                  >
                    Quay về giỏ hàng
                  </button>
                  <button
                    onClick={handleRetryCheckout}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-xs shadow-xs transition-colors cursor-pointer text-center"
                  >
                    Thanh toán lại
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ==================== SCENARIO 3: PAYMENT FAILED ==================== */}
          {isFailed && (
            <div className="flex flex-col items-center">
              <div className="flex flex-col items-center text-center mb-6">
                <div className="w-16 h-16 rounded-2xl bg-rose-50 border border-rose-200 text-rose-600 flex items-center justify-center mb-3">
                  <AlertCircle className="w-8 h-8" />
                </div>
                <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 tracking-tight">
                  Thanh toán không thành công
                </h1>
                <p className="text-slate-600 text-sm mt-1 max-w-md">
                  {getFailureReason(responseCodeParam)}
                </p>
              </div>

              {/* Summary Card for Retry */}
              <div className="w-full max-w-2xl bg-white rounded-2xl shadow-xs border border-slate-200/80 p-6 flex flex-col gap-6">
                <div className="flex items-center justify-between border-b pb-3 border-slate-100">
                  <h2 className="font-bold text-base text-slate-900">Chi tiết dịch vụ chưa hoàn tất</h2>
                  <span className="text-xs font-semibold text-rose-800 bg-rose-50 px-2.5 py-1 rounded-md border border-rose-200">
                    Giao dịch thất bại
                  </span>
                </div>

                <div className="flex flex-col divide-y divide-slate-100">
                  {order.items.map((item, idx) => (
                    <div key={idx} className="py-3 flex items-center justify-between gap-4 first:pt-0 last:pb-0">
                      <div className="flex items-center gap-3 min-w-0">
                        <img src={item.thumbnailUrl} alt={item.serviceName} className="w-14 h-14 rounded-xl object-cover shrink-0" />
                        <div className="min-w-0">
                          <p className="font-bold text-sm text-slate-900 truncate">{item.serviceName}</p>
                          <p className="text-xs text-slate-500">{item.timeSlot} • {item.date}</p>
                          <p className="text-xs text-slate-500">{item.quantity} vé khách</p>
                        </div>
                      </div>
                      <span className="font-bold text-sm text-slate-900 tabular-nums shrink-0">
                        {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                      </span>
                    </div>
                  ))}
                </div>

                <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                  <span className="font-bold text-xs text-slate-700">Tổng tiền chưa thanh toán:</span>
                  <span className="text-2xl font-bold text-rose-600 tabular-nums">
                    {order.finalTotal.toLocaleString("vi-VN")}₫
                  </span>
                </div>

                {/* Instructions */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600">
                  <p className="font-semibold text-slate-900 mb-1">Gợi ý xử lý:</p>
                  <ul className="list-disc list-inside space-y-1 text-slate-500">
                    <li>Kiểm tra lại số dư khả dụng của tài khoản hoặc hạn mức giao dịch.</li>
                    <li>Đảm bảo đã bật tính năng thanh toán trực tuyến (Internet Banking/E-commerce).</li>
                    <li>Thử đổi sang phương thức thanh toán khác (PayPal hoặc thẻ ngân hàng khác).</li>
                  </ul>
                </div>

                {/* Action buttons */}
                <div className="pt-2 flex flex-col sm:flex-row gap-3">
                  <a
                    href="tel:19008468"
                    className="flex-1 py-3 px-4 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-800 font-medium text-xs transition-colors cursor-pointer text-center flex items-center justify-center gap-1.5"
                  >
                    <Phone className="w-3.5 h-3.5" />
                    <span>Hotline: 1900 8468</span>
                  </a>
                  <button
                    onClick={handleRetryCheckout}
                    type="button"
                    className="flex-1 py-3 px-4 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-xs shadow-xs transition-colors cursor-pointer text-center"
                  >
                    Đổi phương thức thanh toán
                  </button>
                </div>
              </div>
            </div>
          )}

        </div>
      </div>
    </main>
  );
}
