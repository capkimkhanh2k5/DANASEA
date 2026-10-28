import { useState, useEffect } from "react";
import { useAuthStore } from "../store/useAuthStore";
import { createPortal } from "react-dom";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { MOCK_NOTIFICATIONS, type VendorNotificationItem } from "../services/vendorMockService";

const VENDOR_PAGE_ORDER: Record<string, number> = {
  "/vendor": 0,
  "/vendor/orders": 1,
  "/vendor/schedule": 2,
  "/vendor/services": 3,
  "/vendor/services/new": 4,
  "/vendor/promotions": 5,
  "/vendor/reviews": 6,
  "/vendor/revenue": 7,
  "/vendor/payouts": 8,
  "/vendor/disputes": 9,
  "/vendor/profile": 10,
};

export function VendorLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, isAuthenticated, accessToken } = useAuthStore();

  useEffect(() => {
    if (!accessToken || !isAuthenticated) {
      console.warn('[VendorLayout] Chưa đăng nhập hoặc phiên hết hạn. Đang chuyển hướng về /auth...');
      navigate("/auth", { replace: true });
    }
  }, [accessToken, isAuthenticated, navigate]);
  const [prevPath, setPrevPath] = useState(location.pathname);
  const [animationClass, setAnimationClass] = useState("animate-fade-in-up");

  const [isNotiOpen, setIsNotiOpen] = useState(false);
  const [selectedNoti, setSelectedNoti] = useState<VendorNotificationItem | null>(null);
  const [notifications] = useState<VendorNotificationItem[]>(MOCK_NOTIFICATIONS);

  if (location.pathname !== prevPath) {
    const prevOrder = VENDOR_PAGE_ORDER[prevPath] ?? 0;
    const currentOrder = VENDOR_PAGE_ORDER[location.pathname] ?? 0;
    setPrevPath(location.pathname);
    if (currentOrder > prevOrder) {
      setAnimationClass("animate-slide-in-right");
    } else if (currentOrder < prevOrder) {
      setAnimationClass("animate-slide-in-left");
    } else {
      setAnimationClass("animate-fade-in-up");
    }
  }

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* 9-item Sticky Sidebar */}
      <aside className="w-64 bg-surface-container-lowest border-r border-outline-variant/30 flex flex-col justify-between shrink-0 select-none">
        <div className="flex flex-col">
          {/* Brand Header */}
          <div className="p-space-lg border-b border-outline-variant/30 flex items-center justify-between">
            <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate("/")}>
              <div className="w-9 h-9 rounded-xl bg-primary flex items-center justify-center text-on-primary font-black text-lg shadow-sm">
                D
              </div>
              <div className="flex flex-col">
                <span className="font-headline-sm text-headline-sm font-extrabold tracking-tight text-primary leading-none">
                  DANASEA
                </span>
                <span className="text-[11px] text-on-surface-variant font-medium mt-0.5">
                  Đối tác Trải nghiệm
                </span>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[11px] font-medium flex items-center gap-1">
              <span className="material-symbols-outlined text-[13px]">verified</span>
              Đã xác thực
            </span>
          </div>

          {/* Navigation Links */}
          <nav className="p-space-sm flex flex-col gap-1 overflow-y-auto max-h-[calc(100vh-160px)]">
            <NavLink
              to="/vendor"
              end
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">dashboard</span>
              <span>Tổng quan (Dashboard)</span>
            </NavLink>

            <NavLink
              to="/vendor/orders"
              className={({ isActive }) =>
                `flex items-center justify-between px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-[20px]">confirmation_number</span>
                <span>Đơn hàng</span>
              </div>
              <span className="px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-900 text-[10px] font-bold">
                1
              </span>
            </NavLink>

            <NavLink
              to="/vendor/schedule"
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">calendar_month</span>
              <span>Lịch khởi hành</span>
            </NavLink>

            <NavLink
              to="/vendor/services"
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">kitesurfing</span>
              <span>Dịch vụ</span>
            </NavLink>

            <NavLink
              to="/vendor/promotions"
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">sell</span>
              <span>Khuyến mãi</span>
            </NavLink>

            <NavLink
              to="/vendor/reviews"
              className={({ isActive }) =>
                `flex items-center justify-between px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-[20px]">chat</span>
                <span>Đánh giá &amp; Phản hồi</span>
              </div>
              <span className="px-1.5 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed text-[10px] font-bold">
                147
              </span>
            </NavLink>

            <NavLink
              to="/vendor/revenue"
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">account_balance_wallet</span>
              <span>Doanh thu</span>
            </NavLink>

            <NavLink
              to="/vendor/payouts"
              className={({ isActive }) =>
                `flex items-center gap-3 px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <span className="material-symbols-outlined text-[20px]">payments</span>
              <span>Quyết toán</span>
            </NavLink>

            <NavLink
              to="/vendor/disputes"
              className={({ isActive }) =>
                `flex items-center justify-between px-space-md py-2.5 rounded-xl font-label-md transition-colors ${
                  isActive
                    ? "bg-primary text-on-primary font-bold shadow-sm"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`
              }
            >
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-[20px]">gavel</span>
                <span>Khiếu nại</span>
              </div>
              <span className="px-1.5 py-0.5 rounded-full bg-surface-container text-on-surface-variant text-[10px] font-bold">
                1
              </span>
            </NavLink>
          </nav>
        </div>

        {/* Footer info */}
        <div className="p-space-md border-t border-outline-variant/30 flex flex-col gap-2">
          <NavLink
            to="/vendor/profile"
            className="flex items-center gap-3 px-space-md py-2 rounded-xl text-on-surface-variant hover:bg-surface-container hover:text-on-surface font-label-md transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">badge</span>
            <span>Hồ sơ &amp; Pháp lý</span>
          </NavLink>
          <button
            onClick={() => navigate("/")}
            className="flex items-center gap-3 px-space-md py-2 rounded-xl text-error hover:bg-error/10 font-label-md transition-colors w-full text-left"
          >
            <span className="material-symbols-outlined text-[18px]">logout</span>
            <span>Quay về trang khách</span>
          </button>
        </div>
      </aside>

      {/* Main dynamic outlet */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar for Vendor */}
        <header className="h-16 bg-surface-container-lowest/80 backdrop-blur-md border-b border-outline-variant/30 px-6 flex items-center justify-between sticky top-0 z-20">
          <div className="flex items-center gap-2.5">
            <img
              alt="DANASEA"
              className="h-8 w-auto object-contain"
              src="https://lh3.googleusercontent.com/aida/AEtjO1Wc3PDumzL5I2MlIC7zdJZmfwBfm4RHsplJ5nk-9ZaRKWyozKczleP4gMlRMYECYGlmoy61P0H5GG6zbTiOnv9OfrbuVqBwoPco9lX5TdnIOndbHvSBQrTFFImlLmSHKB20EH_fp68zcXl68-GiUe2CUKvkZGprNc2OedIHPdFefB5izHfijulRwWFEJMtI3mN_pen7Nzc9tS1VIfkLiHft_-cJWqR00zO95yZuFm90DNcjzOJt5JEfyFNO"
            />
            <span className="font-bold text-base tracking-tight text-primary">DANASEA</span>
            <span className="text-slate-300 font-light">|</span>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Kênh Đối tác</span>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 text-primary font-label-sm text-label-sm font-semibold">
              <span className="material-symbols-outlined text-[16px]">waves</span>
              <span>Sóng 0.5m • Êm biển</span>
            </div>

            {/* Notification Bell */}
            <div className="relative">
              <button
                onClick={() => setIsNotiOpen(!isNotiOpen)}
                className="relative p-2 rounded-xl text-on-surface-variant hover:bg-surface-container-low transition-colors"
                title="Thông báo đối tác"
              >
                <span className="material-symbols-outlined text-[22px]">notifications</span>
                <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-secondary"></span>
              </button>

              {/* Notification Dropdown Panel */}
              {isNotiOpen && (
                <div className="absolute right-0 top-12 w-80 sm:w-96 bg-surface rounded-2xl shadow-2xl border border-outline-variant/30 z-50 flex flex-col overflow-hidden animate-in fade-in zoom-in-95">
                  <div className="p-space-md border-b border-outline-variant/20 flex items-center justify-between">
                    <span className="font-headline-sm text-headline-sm font-bold text-on-surface">Thông báo</span>
                    <span className="text-[11px] text-on-surface-variant font-medium">Trạng thái: SENT</span>
                  </div>

                  <div className="max-h-96 overflow-y-auto flex flex-col divide-y divide-outline-variant/10">
                    {notifications.map((noti) => (
                      <div
                        key={noti.id}
                        onClick={() => setSelectedNoti(noti)}
                        className="p-3 hover:bg-surface-container-low transition-colors cursor-pointer flex flex-col gap-1"
                      >
                        <div className="flex items-center justify-between">
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                            noti.type === 'WEATHER_ALERT' ? 'bg-amber-100 text-amber-900' :
                            noti.type === 'ORDER' ? 'bg-blue-100 text-blue-900' : 'bg-emerald-100 text-emerald-900'
                          }`}>
                            {noti.type}
                          </span>
                          <span className="text-[11px] text-on-surface-variant">{noti.createdAt}</span>
                        </div>
                        <h4 className="font-label-md font-bold text-on-surface line-clamp-1">{noti.title}</h4>
                        <p className="font-body-sm text-on-surface-variant text-[12px] line-clamp-2">{noti.message}</p>
                        <div className="flex items-center gap-2 text-[10px] text-outline mt-0.5">
                          <span>Kênh: {noti.channel}</span>
                          <span>•</span>
                          <span>Trạng thái: {noti.status}</span>
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="p-2 border-t border-outline-variant/20 text-center">
                    <button
                      onClick={() => setIsNotiOpen(false)}
                      className="text-[12px] text-primary font-semibold hover:underline"
                    >
                      Đóng
                    </button>
                  </div>
                </div>
              )}
            </div>

            <div
              onClick={() => navigate("/vendor/profile")}
              className="flex items-center gap-2 pl-2 border-l border-outline-variant/30 cursor-pointer"
            >
              <div className="w-8 h-8 rounded-full bg-primary-fixed text-on-primary-fixed flex items-center justify-center font-bold text-label-sm overflow-hidden shadow-xs">
                {user?.avatarUrl ? (
                  <img src={user.avatarUrl} alt={user.fullName || 'Vendor'} className="w-full h-full object-cover" />
                ) : (
                  (user?.fullName || user?.email || 'V').charAt(0).toUpperCase()
                )}
              </div>
              <span className="font-label-md text-label-md text-on-surface font-semibold hidden sm:inline max-w-[160px] truncate">
                {user?.fullName || user?.email || 'Kênh đối tác'}
              </span>
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto overflow-x-hidden">
          <div
            key={location.pathname}
            onAnimationEnd={() => setAnimationClass("")}
            className={`min-h-full flex flex-col ${animationClass}`}
          >
            <Outlet />
          </div>
        </main>
      </div>

      {/* Notification Detail Modal (Portaled directly to document.body) */}
      {selectedNoti && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface rounded-2xl max-w-md w-full p-space-lg shadow-2xl flex flex-col gap-space-md border border-outline-variant/30 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-2">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary">notifications_active</span>
                <h3 className="font-headline-sm font-bold text-on-surface">Chi tiết thông báo</h3>
              </div>
              <button onClick={() => setSelectedNoti(null)} className="text-on-surface-variant hover:text-on-surface">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <div className="flex flex-col gap-2 font-body-sm">
              <div className="flex justify-between text-[12px] text-on-surface-variant">
                <span>Phân loại: <strong>{selectedNoti.type}</strong></span>
                <span>Thời gian: {selectedNoti.createdAt}</span>
              </div>
              <h4 className="font-headline-sm font-bold text-on-surface mt-1">{selectedNoti.title}</h4>
              <p className="p-3 rounded-xl bg-surface-container-low text-on-surface leading-relaxed">
                {selectedNoti.message}
              </p>

              {/* Related objects link (Service / Slot / Order) */}
              {selectedNoti.relatedSlotId && (
                <div className="p-2.5 rounded-xl bg-amber-50 text-amber-900 text-[12px] flex items-center justify-between">
                  <span>Liên kết ca khởi hành: <strong>{selectedNoti.relatedSlotId}</strong> (Tour: {selectedNoti.relatedServiceId})</span>
                  <button 
                    onClick={() => { setSelectedNoti(null); setIsNotiOpen(false); navigate('/vendor/schedule'); }}
                    className="font-bold underline ml-2"
                  >
                    Xem lịch ca
                  </button>
                </div>
              )}

              {selectedNoti.relatedOrderId && (
                <div className="p-2.5 rounded-xl bg-blue-50 text-blue-900 text-[12px] flex items-center justify-between">
                  <span>Liên kết đơn hàng: <strong>{selectedNoti.relatedOrderId}</strong></span>
                  <button 
                    onClick={() => { setSelectedNoti(null); setIsNotiOpen(false); navigate('/vendor/orders'); }}
                    className="font-bold underline ml-2"
                  >
                    Xem đơn
                  </button>
                </div>
              )}

              <div className="flex justify-between text-[11px] text-outline pt-2 border-t border-outline-variant/20">
                <span>Kênh phát: {selectedNoti.channel}</span>
                <span>Trạng thái máy chủ: {selectedNoti.status}</span>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setSelectedNoti(null)}
                className="px-4 py-2 rounded-xl bg-surface-container-high text-on-surface font-label-md"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
