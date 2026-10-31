import { Link } from 'react-router-dom';
import { PhoneCall, ShieldCheck, Mail, LifeBuoy, Award } from 'lucide-react';

export function Footer() {
  return (
    <footer className="w-full bg-[#031d44] text-white border-t border-sky-950 relative z-10 selection:bg-sky-500 selection:text-white" data-ui="footer">
      <div className="max-w-[1440px] w-full mx-auto px-4 sm:px-6 lg:px-8 pt-12 pb-16">
        
        {/* Main Footer Grid: 3 Clean Columns (0 Duplicate Categories) */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-8 lg:gap-12 pb-12 border-b border-white/10">
          
          {/* Cột 1: Thương hiệu, Chứng nhận & Đối tác thanh toán (chiếm 6 cột) */}
          <div className="lg:col-span-6 flex flex-col gap-5">
            {/* Logo DANASEA */}
            <div className="flex items-center gap-2.5">
              <img alt="DANASEA" className="h-8 w-8 object-contain shrink-0" src="/images/danasea-logo.png" />
              <span className="font-black text-2xl tracking-tight text-white">
                DANASEA
              </span>
            </div>

            {/* Micro Tagline */}
            <p className="text-white text-xs sm:text-sm leading-relaxed max-w-md font-normal">
              Hệ thống đặt trước trải nghiệm biển chất lượng cao tại Đà Nẵng. Cam kết an toàn hàng hải tuyệt đối với hướng dẫn viên 1:1 và trang thiết bị cứu sinh chuẩn SOLAS.
            </p>

            {/* Trust & Certification Badges */}
            <div className="flex flex-wrap items-center gap-2">
              <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-white text-xs font-normal">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                <span>Bảo hiểm Bảo Việt 100M</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-white text-xs font-normal">
                <LifeBuoy className="w-3.5 h-3.5 text-sky-400" />
                <span>Chuẩn cứu sinh SOLAS</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-white text-xs font-normal">
                <Award className="w-3.5 h-3.5 text-amber-400" />
                <span>Hiệp hội Du lịch ĐN</span>
              </div>
            </div>

            {/* Đối tác thanh toán (Payment Partners Grid như Traveloka) */}
            <div className="mt-1">
              <p className="text-xs font-normal text-white mb-2.5">
                Đối tác thanh toán an toàn
              </p>
              <div className="grid grid-cols-4 sm:grid-cols-5 gap-2 max-w-sm">
                
                {/* VNPAY */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="VNPAY">
                  <span className="font-black text-xs tracking-tighter">
                    <span className="text-[#005baa]">VN</span>
                    <span className="text-[#e11b22]">PAY</span>
                  </span>
                </div>

                {/* MOMO */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="Ví MoMo">
                  <span className="font-extrabold text-xs text-[#a50064] tracking-tight">
                    mo<span className="bg-[#a50064] text-white px-0.5 rounded-xs">mo</span>
                  </span>
                </div>

                {/* ZALOPAY */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="ZaloPay">
                  <span className="font-black text-xs text-[#008fe5] tracking-tight">
                    Zalo<span className="text-[#00bf63]">Pay</span>
                  </span>
                </div>

                {/* VISA */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="VISA">
                  <span className="font-black text-xs italic text-[#1a1f71] tracking-wider">
                    VISA
                  </span>
                </div>

                {/* MASTERCARD */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center gap-0.5 shadow-xs border border-white/10" title="Mastercard">
                  <span className="w-3.5 h-3.5 rounded-full bg-[#eb001b] inline-block -mr-1.5"></span>
                  <span className="w-3.5 h-3.5 rounded-full bg-[#f79e1b] inline-block opacity-90"></span>
                </div>

                {/* JCB */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="JCB">
                  <div className="flex text-xs font-black leading-none">
                    <span className="text-[#0e489c]">J</span>
                    <span className="text-[#e21838]">C</span>
                    <span className="text-[#008f39]">B</span>
                  </div>
                </div>

                {/* VIETCOMBANK */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="Vietcombank">
                  <span className="font-bold text-xs text-[#006039] tracking-tighter">
                    VCB
                  </span>
                </div>

                {/* MB BANK */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="MB Bank">
                  <span className="font-black text-xs text-[#133374] tracking-tight">
                    MB
                  </span>
                </div>

                {/* NAPAS */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="NAPAS">
                  <span className="font-extrabold text-xs text-[#00688f] tracking-tighter">
                    napas
                  </span>
                </div>

                {/* APPLE PAY */}
                <div className="h-8 bg-white rounded-md px-1.5 py-1 flex items-center justify-center shadow-xs border border-white/10" title="Apple Pay">
                  <span className="font-semibold text-xs text-slate-900 tracking-tight">
                    Pay
                  </span>
                </div>

              </div>
            </div>

          </div>

          {/* Cột 2: Hỗ trợ & Chính sách lữ hành (chiếm 3 cột - Không trùng lặp danh mục tour) */}
          <div className="lg:col-span-3 flex flex-col gap-3">
            <h3 className="font-bold text-sm text-white tracking-tight">
              Hỗ trợ &amp; Pháp lý
            </h3>
            <ul className="flex flex-col gap-2.5 text-xs text-white list-none p-0 m-0 font-normal">
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Cách đặt tour &amp; quét mã QR
              </li>
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Chính sách hoàn tiền khi thời tiết xấu
              </li>
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Quy chuẩn an toàn hàng hải &amp; cứu sinh
              </li>
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Cam kết bảo tồn san hô Sơn Trà
              </li>
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Tuyển dụng HDV bản địa 1:1
              </li>
              <li className="hover:text-sky-200 transition-colors cursor-pointer">
                Về chúng tôi &amp; Giấy phép lữ hành
              </li>
            </ul>
          </div>

          {/* Cột 3: Mạng xã hội, Hotline & Cứu hộ bờ biển (chiếm 3 cột) */}
          <div className="lg:col-span-3 flex flex-col gap-4">
            <div>
              <h3 className="font-bold text-sm text-white tracking-tight mb-3">
                Kênh kết nối
              </h3>
              <div className="flex flex-col gap-2.5 text-xs text-white font-normal">
                {/* Facebook Authentic SVG Logo */}
                <a
                  href="https://facebook.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2.5 hover:text-sky-200 transition-colors group"
                >
                  <div className="w-6 h-6 rounded-md bg-[#1877F2] flex items-center justify-center shrink-0 shadow-xs group-hover:scale-105 transition-transform">
                    <svg className="w-4 h-4 fill-white" viewBox="0 0 24 24">
                      <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                    </svg>
                  </div>
                  <span>Facebook DANASEA Marine</span>
                </a>

                {/* Instagram Authentic SVG Logo */}
                <a
                  href="https://instagram.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2.5 hover:text-sky-200 transition-colors group"
                >
                  <div className="w-6 h-6 rounded-md bg-gradient-to-tr from-[#f9ce34] via-[#ee2a7b] to-[#6228d7] flex items-center justify-center shrink-0 shadow-xs group-hover:scale-105 transition-transform">
                    <svg className="w-4 h-4 fill-white" viewBox="0 0 24 24">
                      <path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"/>
                    </svg>
                  </div>
                  <span>Instagram @danasea.vn</span>
                </a>

                {/* TikTok Authentic SVG Logo */}
                <a
                  href="https://tiktok.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2.5 hover:text-sky-200 transition-colors group"
                >
                  <div className="w-6 h-6 rounded-md bg-black flex items-center justify-center shrink-0 shadow-xs border border-white/20 group-hover:scale-105 transition-transform">
                    <svg className="w-3.5 h-3.5 fill-white" viewBox="0 0 24 24">
                      <path d="M19.59 6.69a4.83 4.83 0 0 1-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 0 1-5.2 1.74 2.89 2.89 0 0 1 2.31-4.64c.298-.002.595.042.88.13V9.4a6.33 6.33 0 0 0-1-.08A6.34 6.34 0 0 0 3 15.66a6.34 6.34 0 0 0 10.86 4.43v-7a8.16 8.16 0 0 0 4.77 1.52v-3.4a4.85 4.85 0 0 1-2.91-1.34 4.8 4.8 0 0 1-1.13-3.18z"/>
                    </svg>
                  </div>
                  <span>TikTok @danasea.vn</span>
                </a>
              </div>
            </div>

            {/* Hotline & Email Cứu hộ bờ biển */}
            <div className="pt-3 border-t border-white/10 flex flex-col gap-2">
              <span className="text-xs font-normal text-white">
                Hotline khẩn cấp &amp; Cứu hộ biển
              </span>
              <a href="tel:19008468" className="flex items-center gap-2 text-sky-400 hover:text-sky-300 font-bold text-sm transition-colors">
                <PhoneCall className="w-4 h-4 text-sky-400" />
                <span>1900 8468 (24/7)</span>
              </a>
              <div className="flex items-center gap-2 text-xs text-white font-normal">
                <Mail className="w-3.5 h-3.5 text-sky-300" />
                <span>hotro@danasea.vn</span>
              </div>
            </div>
          </div>

        </div>

        {/* Chân trang bản quyền (Bottom Bar) */}
        <div className="pt-6 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-white/90 font-normal">
          <p>© 2026 DANASEA Marine Experiences. Bảo lưu mọi quyền.</p>
          <div className="flex items-center gap-3 text-xs text-white/90 font-normal">
            <Link to="/terms" className="hover:text-white transition-colors">Điều khoản</Link>
            <span>•</span>
            <Link to="/privacy" className="hover:text-white transition-colors">Bảo mật</Link>
            <span>•</span>
            <span>Giấy phép lữ hành số 48-0024/2026</span>
          </div>
        </div>

      </div>
    </footer>
  );
}

export default Footer;
