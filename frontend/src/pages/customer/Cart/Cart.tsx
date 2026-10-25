import { Link, useNavigate } from "react-router-dom";
import { useCartStore } from "../../../store/useCartStore";

export function Cart() {
  const navigate = useNavigate();
  const { items, removeItem, updateQuantity, getTotalPrice } = useCartStore();

  const handleCheckout = () => {
    if (items.length > 0) {
      navigate('/checkout');
    }
  };

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        <section className="max-w-[1280px] w-full mx-auto px-margin-mobile md:px-margin-desktop py-space-xl">
          {/* Breadcrumb & Booking Stepper */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-lg mb-space-2xl">
            <div className="flex flex-col gap-space-2xs">
              <div className="flex items-center gap-2 text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider">
                <Link className="hover:text-primary transition-colors" to="/">Trang chủ</Link>
                <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                <span className="text-secondary font-bold">Giỏ hàng của bạn</span>
              </div>
              <div className="flex items-baseline gap-space-sm">
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Hành Trình Của Bạn</h1>
                <span className="px-space-sm py-0.5 rounded-full bg-secondary/10 text-secondary font-label-sm text-label-sm font-bold">
                  {items.length} trải nghiệm đã chọn
                </span>
              </div>
            </div>
            {/* Stepper Pills */}
            <div className="flex items-center gap-space-xs bg-surface-container-low p-1.5 rounded-full shadow-sm overflow-x-auto">
              <div className="flex items-center gap-2 px-space-md py-1.5 rounded-full bg-secondary text-on-secondary font-label-md text-label-md shadow-sm whitespace-nowrap">
                <span className="w-5 h-5 rounded-full bg-on-secondary/20 flex items-center justify-center text-[11px] font-bold">1</span>
                <span>Giỏ trải nghiệm</span>
              </div>
              <div className="w-6 h-0.5 bg-surface-container-high rounded-full"></div>
              <div className="flex items-center gap-2 px-space-md py-1.5 rounded-full text-on-surface-variant font-label-md text-label-md opacity-60 whitespace-nowrap">
                <span className="w-5 h-5 rounded-full bg-surface-container-highest flex items-center justify-center text-[11px] font-bold">2</span>
                <span>Thông tin &amp; Thanh toán</span>
              </div>
              <div className="w-6 h-0.5 bg-surface-container-high rounded-full"></div>
              <div className="flex items-center gap-2 px-space-md py-1.5 rounded-full text-on-surface-variant font-label-md text-label-md opacity-60 whitespace-nowrap">
                <span className="w-5 h-5 rounded-full bg-surface-container-highest flex items-center justify-center text-[11px] font-bold">3</span>
                <span>Vé điện tử QR</span>
              </div>
            </div>
          </div>

          {/* Main Content Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
            {/* Left Column: Experiences List */}
            <div className="lg:col-span-8 flex flex-col gap-space-lg">
              {items.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 bg-surface-container-lowest rounded-2xl shadow-sm text-center px-4">
                  <div className="w-16 h-16 rounded-full bg-cyan-50 text-cyan-600 flex items-center justify-center mb-4">
                    <span className="material-symbols-outlined text-[36px]">shopping_cart</span>
                  </div>
                  <h2 className="font-headline-sm text-xl font-bold text-slate-800 mb-2">Giỏ hàng của bạn đang trống</h2>
                  <p className="text-on-surface-variant text-sm mb-6 max-w-md">
                    Hãy khám phá ngay các hoạt động chèo SUP, lặn biển và lướt cano vịnh Đà Nẵng để bắt đầu hành trình.
                  </p>
                  <Link 
                    to="/search" 
                    className="px-6 py-3 bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white rounded-full font-bold text-sm shadow-md transition-all hover:scale-105 active:scale-95"
                  >
                    Khám phá trải nghiệm ngay
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
                      className="p-space-lg rounded-2xl bg-surface-container-lowest shadow-sm hover:shadow-md transition-shadow relative overflow-hidden flex flex-col md:flex-row gap-space-lg border border-slate-100"
                    >
                      <div className="w-full md:w-56 h-44 rounded-xl overflow-hidden flex-shrink-0 relative bg-slate-100">
                        <img className="w-full h-full object-cover" alt={title} src={image} />

                      </div>
                      <div className="flex-1 flex flex-col justify-between gap-space-sm min-w-0">
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center justify-between gap-space-sm">
                            <div className="flex items-center gap-1.5">
                              <span className="material-symbols-outlined text-[16px] text-cyan-600">verified</span>
                              <span className="font-label-sm text-label-sm text-slate-700 font-semibold">{vendor}</span>
                              <span className="w-1 h-1 rounded-full bg-slate-300"></span>
                              <span className="text-amber-500 font-label-sm text-label-sm font-bold flex items-center gap-0.5">
                                <span className="material-symbols-outlined text-[14px]">star</span> 4.95
                              </span>
                            </div>
                            <button 
                              onClick={() => removeItem(item.id)} 
                              aria-label="Xoá mục trải nghiệm" 
                              className="text-slate-400 hover:text-red-500 transition-colors p-1 cursor-pointer" 
                              type="button"
                            >
                              <span className="material-symbols-outlined text-[20px]">delete</span>
                            </button>
                          </div>
                          <h2 className="font-headline-sm text-base sm:text-lg font-bold text-slate-900 tracking-tight leading-snug line-clamp-2">
                            {title}
                          </h2>
                        </div>
                        
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-space-md gap-y-1.5 py-space-xs font-body-sm text-xs text-slate-600">
                          <div className="flex items-center gap-2 truncate">
                            <span className="material-symbols-outlined text-[16px] text-cyan-600">schedule</span>
                            <span>{timeStr}, {dateStr}</span>
                          </div>
                          <div className="flex items-center gap-2 truncate">
                            <span className="material-symbols-outlined text-[16px] text-cyan-600">location_on</span>
                            <span>{location}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="material-symbols-outlined text-[16px] text-cyan-600">payments</span>
                            <span>{item.unitPrice.toLocaleString('vi-VN')}₫ / khách</span>
                          </div>
                          <div className="flex items-center gap-2 text-emerald-700 font-medium">
                            <span className="material-symbols-outlined text-[16px] text-emerald-600">verified_user</span>
                            <span>Đã gồm áo phao &amp; HDV</span>
                          </div>
                        </div>
                        
                        <div className="pt-2 border-t border-slate-100 flex items-center justify-between gap-space-sm flex-wrap">
                          <div className="flex items-center gap-3">
                            {/* Quantity Controls */}
                            <div className="flex items-center border border-slate-200 rounded-full overflow-hidden bg-slate-50">
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, item.quantity - 1)}
                                disabled={item.quantity <= 1}
                                className="w-8 h-8 flex items-center justify-center text-slate-600 hover:bg-slate-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
                              >
                                -
                              </button>
                              <span className="px-3 text-xs font-bold text-slate-800">{item.quantity}</span>
                              <button
                                type="button"
                                onClick={() => updateQuantity(item.id, item.quantity + 1)}
                                className="w-8 h-8 flex items-center justify-center text-slate-600 hover:bg-slate-200 transition-colors cursor-pointer"
                              >
                                +
                              </button>
                            </div>
                            <Link 
                              to={`/tour/${item.serviceId}`} 
                              className="font-label-sm text-xs text-cyan-700 hover:text-cyan-800 hover:underline font-semibold flex items-center gap-0.5"
                            >
                              <span className="material-symbols-outlined text-[14px]">tune</span> Xem chi tiết
                            </Link>
                          </div>
                          <div className="flex items-baseline gap-1 text-right">
                            <span className="font-headline-sm text-base sm:text-lg text-slate-900 font-extrabold">
                              {(item.quantity * item.unitPrice).toLocaleString('vi-VN')}₫
                            </span>
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })
              )}

              {/* Upsell & Cross-sell Section */}
              <div className="mt-space-lg flex flex-col gap-space-md">
                <div className="flex items-baseline justify-between">
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-bold">Gợi ý tiện ích biển bổ sung</h2>
                  <span className="font-label-sm text-label-sm text-cyan-700 font-bold">Ưu đãi giảm 15% kèm giỏ</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
                  <div className="p-space-md rounded-xl bg-surface-container-low flex items-center gap-space-md hover:bg-surface-container transition-colors border border-slate-100">
                    <div className="w-20 h-20 rounded-lg overflow-hidden flex-shrink-0 bg-slate-200">
                      <img 
                        className="w-full h-full object-cover" 
                        alt="GoPro" 
                        src="https://images.unsplash.com/photo-1565849904461-04a58ad377e0?w=300"
                      />
                    </div>
                    <div className="flex-1 min-w-0 flex flex-col justify-between h-full">
                      <div>
                        <h3 className="font-label-lg text-sm font-bold text-slate-900 truncate">Thuê máy quay GoPro Hero 11</h3>
                        <p className="font-body-sm text-xs text-slate-500">Kèm phao nổi &amp; thẻ 64GB</p>
                      </div>
                      <div className="flex items-center justify-between gap-2 pt-1">
                        <span className="font-label-lg text-xs text-cyan-700 font-bold">150.000₫<span className="text-slate-400 font-normal">/ngày</span></span>
                        <button className="px-3 py-1 rounded-full bg-cyan-600 text-white font-label-sm text-xs hover:bg-cyan-700 transition-colors flex items-center gap-1 cursor-pointer" type="button">
                          <span className="material-symbols-outlined text-[14px]">add</span> Thêm
                        </button>
                      </div>
                    </div>
                  </div>
                  <div className="p-space-md rounded-xl bg-surface-container-low flex items-center gap-space-md hover:bg-surface-container transition-colors border border-slate-100">
                    <div className="w-20 h-20 rounded-lg overflow-hidden flex-shrink-0 bg-slate-200">
                      <img 
                        className="w-full h-full object-cover" 
                        alt="Shuttle Bus" 
                        src="https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=300"
                      />
                    </div>
                    <div className="flex-1 min-w-0 flex flex-col justify-between h-full">
                      <div>
                        <h3 className="font-label-lg text-sm font-bold text-slate-900 truncate">Xe đón khách sạn ra bến cano</h3>
                        <p className="font-body-sm text-xs text-slate-500">Đón trả tận nơi tại Sơn Trà</p>
                      </div>
                      <div className="flex items-center justify-between gap-2 pt-1">
                        <span className="font-label-lg text-xs text-cyan-700 font-bold">80.000₫<span className="text-slate-400 font-normal">/chuyến</span></span>
                        <button className="px-3 py-1 rounded-full bg-cyan-600 text-white font-label-sm text-xs hover:bg-cyan-700 transition-colors flex items-center gap-1 cursor-pointer" type="button">
                          <span className="material-symbols-outlined text-[14px]">add</span> Thêm
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column: Sticky Order Summary */}
            <aside className="lg:col-span-4 flex flex-col gap-space-md lg:sticky lg:top-28">
              <div className="p-space-md rounded-xl bg-cyan-50 border border-cyan-100 flex items-center gap-space-sm">
                <span className="material-symbols-outlined text-cyan-700 text-[24px] flex-shrink-0">verified_user</span>
                <div className="flex-1">
                  <p className="font-label-sm text-xs text-cyan-900 font-bold uppercase tracking-wider">Bảo vệ đặt chỗ 100%</p>
                  <p className="font-body-sm text-xs text-cyan-800 mt-0.5">Cam kết giữ đúng slot và hoàn tiền nếu thời tiết biển không thuận lợi.</p>
                </div>
              </div>

              <div className="p-space-lg rounded-2xl bg-surface-container-lowest shadow-sm border border-slate-100 flex flex-col gap-space-md">
                <h2 className="font-headline-sm text-lg font-bold text-slate-900 pb-space-xs border-b border-slate-100">
                  Tóm tắt đơn hàng
                </h2>
                <div className="flex flex-col gap-2 font-body-md text-sm text-slate-600">
                  <div className="flex items-center justify-between">
                    <span>Tạm tính ({items.length} trải nghiệm)</span>
                    <span className="font-semibold text-slate-900">{getTotalPrice().toLocaleString('vi-VN')}₫</span>
                  </div>
                  <div className="flex items-center justify-between text-emerald-700 font-medium">
                    <span>Phí dịch vụ</span>
                    <span>Miễn phí</span>
                  </div>
                </div>
                
                <div className="pt-space-md flex items-end justify-between gap-space-sm border-t border-slate-100">
                  <div>
                    <p className="font-label-sm text-xs text-slate-500 uppercase tracking-wider font-bold">Tổng thanh toán</p>
                    <p className="font-body-sm text-xs text-slate-400">Đã bao gồm thuế &amp; bảo hiểm</p>
                  </div>
                  <div className="text-right">
                    <p className="font-headline-lg text-2xl text-cyan-700 font-black leading-none">
                      {getTotalPrice().toLocaleString('vi-VN')}₫
                    </p>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-space-xs">
                <button 
                  onClick={handleCheckout} 
                  disabled={items.length === 0}
                  className={`w-full py-4 rounded-full font-bold text-sm flex items-center justify-center gap-2 shadow-md transition-all cursor-pointer ${
                    items.length > 0 
                      ? 'bg-gradient-to-r from-teal-600 to-cyan-600 hover:from-teal-500 hover:to-cyan-500 text-white hover:scale-[1.02] active:scale-[0.98] shadow-cyan-600/20' 
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed shadow-none'
                  }`} 
                  type="button"
                >
                  <span className="material-symbols-outlined text-[20px]">credit_card</span>
                  <span>Tiến hành đặt chỗ &amp; thanh toán</span>
                </button>
              </div>
            </aside>
          </div>
        </section>
      </div>
    </main>
  );
}
