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
  const [searchParams] = useSearchParams();
  const [order, setOrder] = useState<OrderSnapshot | null>(null);
  const [copied, setCopied] = useState(false);
  const [savedTicketId, setSavedTicketId] = useState<string | null>(null);

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

  const displayEmail = order.customerEmail || order.email || "khachhang@danasea.vn";
  const displayPhone = order.customerPhone || order.phone || "0912 345 678";

  const handleCopyCode = () => {
    navigator.clipboard.writeText(order.orderCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

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
      case "VNPAY":
      default:
        return "Cổng VNPAY (QR Pay / Thẻ)";
    }
  };

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar (Synchronized with Step 1 & 2) */}
        <section className="w-full bg-surface-container-lowest/80 backdrop-blur-md shadow-sm border-b border-slate-100">
          <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-md">
            <div className="flex items-center justify-between gap-space-md flex-wrap mb-space-sm">
              <div className="flex items-center gap-space-xs font-label-md text-xs text-slate-500">
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate("/")}>
                  DANASEA
                </span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate("/cart")}>
                  Giỏ trải nghiệm biển
                </span>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="text-cyan-700 font-bold">Nhận vé điện tử QR</span>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm items-center">
              <div className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2">
                <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span className="material-symbols-outlined text-[18px]">check</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 1</p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Giỏ trải nghiệm</p>
                </div>
              </div>

              <div className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2">
                <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span className="material-symbols-outlined text-[18px]">check</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 2</p>
                  <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>

              <div className="flex items-center gap-space-sm rounded-xl bg-cyan-50/80 border border-cyan-200 shadow-sm p-2">
                <div className="w-8 h-8 rounded-full bg-cyan-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
                  <span>3</span>
                </div>
                <div className="min-w-0">
                  <p className="font-label-sm text-[11px] text-cyan-600 font-bold uppercase tracking-wider">Bước 3 • Hoàn tất</p>
                  <p className="font-headline-sm text-xs font-bold text-cyan-900 truncate">Nhận vé điện tử QR</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Content Body */}
        <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-xl sm:py-space-2xl w-full">
          {/* SUCCESS HERO BANNER */}
          <div className="flex flex-col items-center text-center mb-space-2xl">
            <div className="w-16 h-16 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-600 flex items-center justify-center shadow-md mb-3">
              <span className="material-symbols-outlined text-[36px]">verified</span>
            </div>

            <div className="flex items-center gap-space-xs mb-2">
              <span className="px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold tracking-wider uppercase border border-emerald-200">
                Thanh toán hoàn tất
              </span>
              <span className="w-1.5 h-1.5 rounded-full bg-slate-300"></span>
              <span className="text-xs text-slate-500 font-medium">Hóa đơn điện tử hợp lệ</span>
            </div>

            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight max-w-2xl">
              Thanh toán &amp; Xác nhận đặt chỗ thành công!
            </h1>

            {/* Meta Order Badges */}
            <div className="flex flex-wrap items-center justify-center gap-2 mt-3">
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 text-xs text-slate-700 border border-slate-200">
                <span className="text-slate-500">Mã đơn:</span>
                <span className="font-bold text-cyan-800 font-mono">{order.orderCode}</span>
                <button
                  onClick={handleCopyCode}
                  className="hover:text-cyan-700 transition-colors ml-0.5 cursor-pointer"
                  title="Sao chép mã đơn"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[15px]">{copied ? "check" : "content_copy"}</span>
                </button>
                {copied && <span className="text-[10px] text-emerald-600 font-bold ml-1">Đã chép!</span>}
              </div>

              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 text-xs text-slate-700 border border-slate-200">
                <span className="material-symbols-outlined text-[15px] text-slate-500">schedule</span>
                <span>{order.paidAt}</span>
              </div>

              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 text-xs text-slate-700 border border-slate-200">
                <span className="material-symbols-outlined text-[15px] text-slate-500">account_balance_wallet</span>
                <span>{getProviderLabel(order.paymentProvider)}</span>
              </div>
            </div>

            {/* Notification Notice */}
            <div className="mt-4 p-3 px-5 rounded-2xl bg-cyan-50/60 border border-cyan-100 text-slate-700 flex items-center gap-2.5 max-w-xl text-left text-xs sm:text-sm">
              <span className="material-symbols-outlined text-cyan-700 text-[22px] flex-shrink-0">mark_email_read</span>
              <p className="leading-snug">
                Vé điện tử QR và thông tin xác nhận đã được gửi về email{" "}
                <strong className="text-cyan-900 underline">{displayEmail}</strong> &amp; SĐT{" "}
                <strong className="text-slate-900">{displayPhone}</strong>.
              </p>
            </div>
          </div>

          {/* MAIN GRID: TICKETS & ORDER SUMMARY */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
            {/* TICKETS COLUMN (8 cols) */}
            <div className="lg:col-span-8 flex flex-col gap-space-lg">
              <div className="flex items-center justify-between border-b pb-space-sm border-slate-100">
                <div>
                  <h3 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900">
                    Vé điện tử check-in ({order.items.length} dịch vụ)
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Xuất trình mã QR tại điểm đón hoặc quầy điều hành cano để quét vé vào cổng
                  </p>
                </div>
              </div>

              {/* TICKET LIST */}
              {order.items.map((item, index) => (
                <article
                  key={item.id || index}
                  className="bg-surface-container-lowest rounded-2xl shadow-sm border border-slate-100 overflow-hidden flex flex-col transition-all hover:shadow-md"
                >
                  {/* Ticket Header Bar */}
                  <div className="bg-slate-50/80 px-space-lg py-space-sm border-b border-slate-100 flex flex-wrap items-center justify-between gap-space-xs">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
                      <span className="text-xs text-emerald-700 uppercase font-bold tracking-wider">
                        ĐÃ XÁC NHẬN • SẴN SÀNG CHECK-IN
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-slate-500">
                      <span>Mã vé:</span>
                      <span className="font-bold text-slate-900 font-mono">#{item.ticketCode}</span>
                    </div>
                  </div>

                  {/* Ticket Main Content Body */}
                  <div className="p-space-lg grid grid-cols-1 md:grid-cols-12 gap-space-lg items-center">
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
                        {/* SVG QR Code Illustration */}
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
                </article>
              ))}
            </div>

            {/* SIDEBAR SUMMARY & ACTIONS (4 cols) */}
            <div className="lg:col-span-4 flex flex-col gap-space-lg">
              {/* Order Financial Summary Card */}
              <div className="bg-surface-container-lowest p-space-xl rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-space-md">
                <div className="flex items-center justify-between pb-space-xs border-b border-slate-100">
                  <h4 className="font-bold text-sm sm:text-base text-slate-900">Chi tiết thanh toán</h4>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold border border-emerald-200">
                    Đã thanh toán
                  </span>
                </div>

                <div className="flex flex-col gap-2 text-xs text-slate-600">
                  {order.items.map((item, idx) => (
                    <div key={idx} className="flex justify-between items-start gap-2">
                      <span className="truncate">
                        {item.serviceName} ({item.quantity} vé)
                      </span>
                      <span className="font-semibold text-slate-900 flex-shrink-0">
                        {(item.unitPrice * item.quantity).toLocaleString("vi-VN")}₫
                      </span>
                    </div>
                  ))}

                  {order.appliedPromoCode && order.discountAmount > 0 && (
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

              {/* Navigation Action Buttons Card */}
              <div className="bg-surface-container-lowest p-space-xl rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-3">
                <h4 className="font-bold text-xs uppercase tracking-wider text-slate-400">Hành động tiếp theo</h4>
                <button
                  onClick={() => navigate("/")}
                  type="button"
                  className="w-full py-3.5 px-4 rounded-full bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white font-bold text-xs shadow-md transition-all flex items-center justify-center gap-2 cursor-pointer hover:scale-[1.01]"
                >
                  <span className="material-symbols-outlined text-[18px]">explore</span>
                  <span>Tiếp tục khám phá trải nghiệm khác</span>
                </button>

                <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                  <span>Cần hỗ trợ đơn hàng?</span>
                  <a className="text-cyan-700 font-bold hover:underline flex items-center gap-1" href="tel:19008468">
                    <span className="material-symbols-outlined text-[15px]">call</span>
                    <span>Hotline: 1900 8468</span>
                  </a>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
