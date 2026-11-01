import { Link, useNavigate } from "react-router-dom";
import { useCartStore } from "../../../store/useCartStore";
import {
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  Clock,
  MapPin,
  ShieldCheck,
  ChevronRight,
  ArrowRight,
  BadgeCheck,
} from "lucide-react";

export function Cart() {
  const navigate = useNavigate();
  const { items, removeItem, updateQuantity, getTotalPrice } = useCartStore();

  const handleCheckout = () => {
    if (items.length > 0) {
      navigate('/checkout');
    }
  };

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        {/* Sub-Header Progress Stepper Bar - Airbnb Clean Architecture */}
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
              <span className="text-slate-900 font-semibold">Giỏ hàng trải nghiệm</span>
            </div>

            {/* Stepper Component - Architectural Geometry */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center">
              {/* Step 1: Active in Cart */}
              <div className="flex items-center gap-3 rounded-xl bg-white border border-slate-300 p-3">
                <span className="text-sm font-bold text-slate-900">01.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-500 font-medium">Bước 1</p>
                  <p className="text-sm font-bold text-slate-900 truncate">Giỏ hàng</p>
                </div>
              </div>

              {/* Step 2: Next */}
              <div className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3">
                <span className="text-sm font-semibold text-slate-600">02.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-600 font-medium">Bước 2</p>
                  <p className="text-sm font-medium text-slate-700 truncate">Thông tin &amp; Thanh toán</p>
                </div>
              </div>

              {/* Step 3: Final */}
              <div className="flex items-center gap-3 rounded-xl bg-slate-100/70 border border-slate-200/70 p-3">
                <span className="text-sm font-semibold text-slate-600">03.</span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-600 font-medium">Bước 3</p>
                  <p className="text-sm font-medium text-slate-700 truncate">Nhận vé điện tử QR</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Main Content Layout - Airbnb 1280px Grid */}
        <section className="max-w-[1280px] w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Experiences List */}
            <div className="lg:col-span-8 flex flex-col gap-6">
              <div className="border-b border-slate-200/80 pb-4">
                <h1 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                  Giỏ hàng của bạn
                </h1>
                <p className="text-sm text-slate-600 mt-1">
                  {items.length} trải nghiệm đã sẵn sàng để giữ chỗ
                </p>
              </div>

              {items.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 bg-white rounded-2xl border border-slate-200/80 shadow-xs text-center px-4">
                  <div className="w-12 h-12 rounded-xl bg-slate-50 border border-slate-200 text-slate-400 flex items-center justify-center mb-4">
                    <ShoppingCart className="w-6 h-6" />
                  </div>
                  <h2 className="text-lg font-bold text-slate-900 mb-1">Giỏ hàng của bạn đang trống</h2>
                  <p className="text-slate-600 text-sm max-w-md mb-6 leading-relaxed">
                    Khám phá các tour cano lặn biển, chèo SUP bình minh hoặc dù bay tại Đà Nẵng để thêm vào giỏ hàng.
                  </p>
                  <Link 
                    to="/" 
                    className="px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl font-medium text-sm shadow-xs transition-colors inline-flex items-center gap-2"
                  >
                    <span>Khám phá trải nghiệm</span>
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                </div>
              ) : (
                items.map((item) => {
                  const title = item.serviceName || 'Trải nghiệm biển Đà Nẵng';
                  const image = item.thumbnailUrl || 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800';
                  const location = item.locationName || 'Đà Nẵng';
                  const vendor = item.vendorName || 'DANASEA Official Partner';
                  const dateStr = item.selectedDate || item.date || 'Hôm nay';
                  const timeStr = item.timeSlot || 'Khung giờ linh hoạt';

                  return (
                    <article 
                      key={item.id} 
                      className="p-5 rounded-2xl bg-white shadow-xs hover:shadow-md transition-shadow relative overflow-hidden flex flex-col sm:flex-row gap-5 border border-slate-200/80"
                    >
                      {/* Photo Thumbnail */}
                      <div className="w-full sm:w-48 h-36 rounded-xl overflow-hidden shrink-0 relative bg-slate-100">
                        <img className="w-full h-full object-cover" alt={title} src={image} />
                      </div>

                      {/* Content Details */}
                      <div className="flex-1 flex flex-col justify-between gap-3 min-w-0">
                        <div className="flex flex-col gap-1.5">
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-1.5 text-xs text-slate-600 truncate">
                              <BadgeCheck className="w-4 h-4 text-sky-600 shrink-0" />
                              <span className="font-semibold text-slate-800 truncate">{vendor}</span>
                            </div>
                            <button 
                              onClick={() => removeItem(item.id)} 
                              aria-label="Xoá mục trải nghiệm" 
                              title="Xoá khỏi giỏ"
                              className="text-slate-400 hover:text-rose-600 transition-colors p-1 cursor-pointer" 
                              type="button"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                          
                          <h2 className="text-base sm:text-lg font-bold text-slate-900 tracking-tight leading-snug line-clamp-2">
                            {title}
                          </h2>
                        </div>
                        
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5 text-xs text-slate-600">
                          <div className="flex items-center gap-1.5 truncate">
                            <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            <span className="truncate">{timeStr}, {dateStr}</span>
                          </div>
                          <div className="flex items-center gap-1.5 truncate">
                            <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            <span className="truncate">{location}</span>
                          </div>
                        </div>
                        
                        {/* Footer Controls: Quantity & Price */}
                        <div className="pt-3 border-t border-slate-100 flex items-center justify-between gap-3 flex-wrap">
                          <div className="flex items-center gap-3">
                            {/* Quantity Controls - Airbnb Circular Steppers */}
                            <div className="flex items-center border border-slate-200 rounded-xl overflow-hidden bg-slate-50">
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, item.quantity - 1)}
                                disabled={item.quantity <= 1}
                                className="w-8 h-8 flex items-center justify-center text-slate-600 hover:bg-slate-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
                              >
                                <Minus className="w-3.5 h-3.5" />
                              </button>
                              <span className="px-3 text-xs font-bold text-slate-900 tabular-nums">{item.quantity}</span>
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, item.quantity + 1)}
                                className="w-8 h-8 flex items-center justify-center text-slate-600 hover:bg-slate-200 transition-colors cursor-pointer"
                              >
                                <Plus className="w-3.5 h-3.5" />
                              </button>
                            </div>
                            
                            <Link 
                              to={`/tour/${item.serviceId}`} 
                              className="text-xs text-slate-600 hover:text-slate-900 hover:underline font-medium"
                            >
                              Xem lại chi tiết
                            </Link>
                          </div>

                          <div className="flex items-baseline gap-1 text-right">
                            <span className="text-base sm:text-lg text-slate-900 font-bold tabular-nums">
                              {(item.quantity * item.unitPrice).toLocaleString('vi-VN')}₫
                            </span>
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })
              )}

              {/* Cross-sell Addons Section */}
              <div className="mt-4 flex flex-col gap-3">
                <div className="flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-900">Tiện ích biển đề xuất</h3>
                  <span className="text-xs text-slate-500 font-medium">Bổ sung vào chuyến đi</span>
                </div>
                
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="p-3.5 rounded-xl bg-slate-50/80 border border-slate-200/80 flex items-center gap-3 hover:bg-slate-100/60 transition-colors">
                    <div className="w-16 h-16 rounded-lg overflow-hidden shrink-0 bg-slate-200">
                      <img 
                        className="w-full h-full object-cover" 
                        alt="GoPro Hero 11" 
                        src="https://images.unsplash.com/photo-1565849904461-04a58ad377e0?w=300"
                      />
                    </div>
                    <div className="flex-1 min-w-0 flex flex-col justify-between h-full py-0.5">
                      <div>
                        <h4 className="text-xs font-bold text-slate-900 truncate">Máy quay GoPro Hero 11</h4>
                        <p className="text-[11px] text-slate-500">Kèm phao nổi &amp; thẻ nhớ 64GB</p>
                      </div>
                      <div className="flex items-center justify-between gap-2 pt-1">
                        <span className="text-xs font-bold text-slate-900">150.000₫/ngày</span>
                        <button className="px-2.5 py-1 rounded-lg bg-slate-900 text-white text-xs hover:bg-slate-800 transition-colors flex items-center gap-1 cursor-pointer" type="button">
                          <Plus className="w-3 h-3" /> Thêm
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="p-3.5 rounded-xl bg-slate-50/80 border border-slate-200/80 flex items-center gap-3 hover:bg-slate-100/60 transition-colors">
                    <div className="w-16 h-16 rounded-lg overflow-hidden shrink-0 bg-slate-200">
                      <img 
                        className="w-full h-full object-cover" 
                        alt="Shuttle Bus" 
                        src="https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=300"
                      />
                    </div>
                    <div className="flex-1 min-w-0 flex flex-col justify-between h-full py-0.5">
                      <div>
                        <h4 className="text-xs font-bold text-slate-900 truncate">Xe đón khách sạn ra bến</h4>
                        <p className="text-[11px] text-slate-500">Đón trả tận nơi tại Sơn Trà</p>
                      </div>
                      <div className="flex items-center justify-between gap-2 pt-1">
                        <span className="text-xs font-bold text-slate-900">80.000₫/lượt</span>
                        <button className="px-2.5 py-1 rounded-lg bg-slate-900 text-white text-xs hover:bg-slate-800 transition-colors flex items-center gap-1 cursor-pointer" type="button">
                          <Plus className="w-3 h-3" /> Thêm
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column: Sticky Order Summary - Airbnb Reservation Card Style */}
            <aside className="lg:col-span-4 flex flex-col gap-4 lg:sticky lg:top-24">
              <div className="p-6 rounded-2xl bg-white shadow-xs border border-slate-200/80 flex flex-col gap-4">
                <h2 className="text-lg font-bold text-slate-900 pb-3 border-b border-slate-100">
                  Tóm tắt đơn hàng
                </h2>
                
                <div className="flex flex-col gap-2.5 text-sm text-slate-600">
                  <div className="flex items-center justify-between">
                    <span>Tạm tính ({items.length} trải nghiệm)</span>
                    <span className="font-semibold text-slate-900 tabular-nums">
                      {getTotalPrice().toLocaleString('vi-VN')}₫
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-emerald-700 font-medium">
                    <span>Phí dịch vụ DANASEA</span>
                    <span>Miễn phí</span>
                  </div>
                </div>
                
                <div className="pt-4 flex items-end justify-between gap-2 border-t border-slate-100">
                  <div>
                    <p className="text-xs text-slate-700 font-semibold">Tổng thanh toán</p>
                    <p className="text-xs text-slate-500">Đã gồm thuế &amp; bảo hiểm biển</p>
                  </div>
                  <div className="text-right">
                    <p className="text-2xl text-slate-900 font-extrabold tabular-nums leading-none">
                      {getTotalPrice().toLocaleString('vi-VN')}₫
                    </p>
                  </div>
                </div>

                {/* Safety & Cancellation Notice */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5 mt-1">
                  <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                  <div className="flex-1 text-xs">
                    <p className="font-bold text-slate-900">Bảo vệ đặt chỗ 100%</p>
                    <p className="text-slate-600 mt-0.5 leading-relaxed">
                      Cam kết hoàn tiền 100% khi thời tiết biển không đảm bảo an toàn.
                    </p>
                  </div>
                </div>

                {/* Primary CTA - Ocean Blue DANASEA Button */}
                <button 
                  onClick={handleCheckout} 
                  disabled={items.length === 0}
                  className={`w-full py-3.5 rounded-xl font-bold text-sm flex items-center justify-center transition-colors cursor-pointer ${
                    items.length > 0 
                      ? 'bg-[#0284c7] hover:bg-[#0369a1] text-white active:scale-95 shadow-xs' 
                      : 'bg-slate-100 text-slate-600 border border-slate-300 cursor-not-allowed'
                  }`} 
                  type="button"
                >
                  Tiến hành thanh toán
                </button>
              </div>
            </aside>
          </div>
        </section>
      </div>
    </main>
  );
}
