import { useState, useEffect, useCallback } from "react";
import { useAuthStore } from "../store/useAuthStore";
import { createPortal } from "react-dom";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { notificationService } from "../services/notificationService";
import type { NotificationResponse } from "../types/notification";
import { 
  LayoutDashboard, 
  TrendingUp, 
  Ticket, 
  CalendarDays, 
  Sailboat, 
  Tag, 
  MessageSquare, 
  Wallet, 
  Landmark, 
  Scale, 
  LogOut, 
  Bell, 
  Pin,
  CheckCheck,
  LifeBuoy,
  Star,
  Bot
} from "lucide-react";
import { AtlasAvatar } from "../components/vendor/atlas/AtlasDesignSystem";
import { toast } from "../store/useToastStore";
import { useVendorCopilotStore } from "../store/useVendorCopilotStore";
import { VendorCopilotDrawer } from "../components/vendor/copilot/VendorCopilotDrawer";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  exact?: boolean;
}

interface NavGroup {
  groupTitle: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    groupTitle: "VẬN HÀNH & ĐIỀU PHỐI",
    items: [
      { to: "/vendor", label: "Tổng quan", icon: LayoutDashboard, exact: true },
      { to: "/vendor/copilot", label: "Trợ lý Copilot", icon: Bot },
      { to: "/vendor/analytics", label: "Phân tích kinh doanh", icon: TrendingUp },
      { to: "/vendor/orders", label: "Đơn đặt chỗ & Vé", icon: Ticket },
      { to: "/vendor/messages", label: "Tin nhắn khách hàng", icon: MessageSquare },
      { to: "/vendor/schedule", label: "Lịch khởi hành", icon: CalendarDays },
    ],
  },
  {
    groupTitle: "DỊCH VỤ & BẾN BÃI",
    items: [
      { to: "/vendor/services", label: "Dịch vụ & Ca tour", icon: Sailboat },
      { to: "/vendor/equipment", label: "Quản lý thiết bị", icon: LifeBuoy },
      { to: "/vendor/promotions", label: "Mã khuyến mãi", icon: Tag },
      { to: "/vendor/reviews", label: "Đánh giá của khách", icon: Star },
    ],
  },
  {
    groupTitle: "TÀI CHÍNH & PHÁP LÝ",
    items: [
      { to: "/vendor/revenue", label: "Doanh thu & Đối soát", icon: Wallet },
      { to: "/vendor/payouts", label: "Quyết toán rút tiền", icon: Landmark },
      { to: "/vendor/disputes", label: "Khiếu nại & Hỗ trợ", icon: Scale },
    ],
  },
];

const VENDOR_PAGE_ORDER: Record<string, number> = {
  "/vendor": 0,
  "/vendor/copilot": 0.5,
  "/vendor/analytics": 1,
  "/vendor/orders": 2,
  "/vendor/messages": 2.5,
  "/vendor/schedule": 3,
  "/vendor/services": 4,
  "/vendor/equipment": 5,
  "/vendor/services/new": 6,
  "/vendor/promotions": 6,
  "/vendor/reviews": 7,
  "/vendor/revenue": 8,
  "/vendor/payouts": 9,
  "/vendor/disputes": 10,
  "/vendor/profile": 11,
};

const VENDOR_BRAND_MAP: Record<string, string> = {
  "vender_1@danasea.test": "Danabeach Club",
  "vender_2@danasea.test": "Công ty TNHH Kajin Vina",
  "vender_3@danasea.test": "Da Nang Outdoor Adventures",
  "vender_4@danasea.test": "Loca Travel Đà Nẵng",
  "vender_5@danasea.test": "Công ty Cổ phần D BLUE",
  "vender_6@danasea.test": "Khuê Nguyễn – Dịch Vụ Câu Mực",
  "vender_7@danasea.test": "DaNaFi – Tour Câu Cá",
  "vender_8@danasea.test": "Go N Chill SUP Club",
  "vender_9@danasea.test": "Surf Shack Da Nang",
  "vender_10@danasea.test": "Mỹ Khê Jet Sports",
  "vender_1": "Danabeach Club",
  "vender_2": "Công ty TNHH Kajin Vina",
  "vender_3": "Da Nang Outdoor Adventures",
  "vender_4": "Loca Travel Đà Nẵng",
  "vender_5": "Công ty Cổ phần D BLUE",
  "vender_6": "Khuê Nguyễn – Dịch Vụ Câu Mực",
  "vender_7": "DaNaFi – Tour Câu Cá",
  "vender_8": "Go N Chill SUP Club",
  "vender_9": "Surf Shack Da Nang",
};

export function VendorLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, isAuthenticated, accessToken } = useAuthStore();
  const isCopilotOpen = useVendorCopilotStore((s) => s.isOpen);
  const toggleCopilot = useVendorCopilotStore((s) => s.toggleCopilot);

  const vendorDisplayName = 
    (user?.email && VENDOR_BRAND_MAP[user.email]) ||
    (user?.fullName && VENDOR_BRAND_MAP[user.fullName]) ||
    user?.fullName ||
    "Công ty TNHH Kajin Vina";

  useEffect(() => {
    if (!accessToken || !isAuthenticated) {
      navigate("/auth", { replace: true });
    }
  }, [accessToken, isAuthenticated, navigate]);

  const [prevPath, setPrevPath] = useState(location.pathname);
  const [animationClass, setAnimationClass] = useState("animate-fade-in-up");

  const [isNotiOpen, setIsNotiOpen] = useState(false);
  const [selectedNoti, setSelectedNoti] = useState<NotificationResponse | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  // Collapsible sidebar states (Hover expand + Pin lock)
  const [isHovered, setIsHovered] = useState(false);
  const [isPinned, setIsPinned] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("danasea_vendor_sidebar_pinned") === "true";
    }
    return false;
  });

  useEffect(() => {
    localStorage.setItem("danasea_vendor_sidebar_pinned", String(isPinned));
  }, [isPinned]);

  const isExpanded = isPinned || isHovered;

  const loadNotifications = useCallback(async () => {
    try {
      const [res, unread] = await Promise.allSettled([
        notificationService.getMyNotifications(0, 15),
        notificationService.getUnreadCount()
      ]);
      if (res.status === 'fulfilled' && res.value?.content) {
        setNotifications(res.value.content);
      }
      if (unread.status === 'fulfilled' && unread.value) {
        setUnreadCount(typeof unread.value === "number" ? unread.value : 0);
      }
    } catch {
      // Keep empty list
    }
  }, []);

  const handleMarkAllAsRead = async () => {
    try {
      await notificationService.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
      toast.success("Đã đánh dấu tất cả thông báo là đã đọc.");
    } catch (err) {
      console.error("[VendorLayout] Failed to mark all notifications as read:", err);
      toast.error("Không thể đánh dấu đã đọc. Vui lòng thử lại sau.");
    }
  };

  const handleNotificationClick = async (noti: NotificationResponse) => {
    if (!noti.isRead) {
      try {
        await notificationService.markAsRead(noti.id);
        setNotifications((prev) =>
          prev.map((n) => (n.id === noti.id ? { ...n, isRead: true } : n))
        );
        setUnreadCount((prev) => Math.max(0, prev - 1));
      } catch (err) {
        console.warn("[VendorLayout] Failed to mark notification as read:", err);
      }
    }
    setSelectedNoti({ ...noti, isRead: true });
  };


  useEffect(() => {
    if (isAuthenticated) {
      loadNotifications();
    }
  }, [isAuthenticated, loadNotifications]);

  if (!accessToken || !isAuthenticated) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50 text-slate-600">
        <p className="text-sm font-semibold">Chuyển hướng xác thực tài khoản đối tác...</p>
      </div>
    );
  }

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
    <div className="flex h-screen bg-background overflow-hidden print:h-auto print:overflow-visible print:bg-white">
      {/* Spacer so main content does not jump or reflow */}
      <div 
        className={`shrink-0 transition-all duration-200 ease-in-out print:hidden ${
          isPinned ? "w-64" : "w-[68px]"
        }`} 
      />

      {/* Modern Collapsible Sidebar (Compact Rail -> Hover Expand / Pin) */}
      <aside 
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        className={`fixed top-0 bottom-0 left-0 z-30 bg-white border-r border-slate-200/80 flex flex-col justify-between select-none print:hidden transition-[width,box-shadow] duration-200 ease-out ${
          isExpanded ? "w-64 shadow-2xl shadow-slate-900/10" : "w-[68px]"
        }`}
      >
        <div className="flex flex-col min-h-0 flex-1">
          {/* Brand Header */}
          <div className="h-16 px-3.5 border-b border-slate-200/80 flex items-center justify-between shrink-0 overflow-hidden">
            <div 
              className="flex items-center gap-3 cursor-pointer shrink-0" 
              onClick={() => navigate("/")}
              title="DANASEA Kênh đối tác"
            >
              <img
                src="/images/danasea-logo.png"
                alt="DANASEA Logo"
                className="w-9 h-9 object-contain rounded-xl shrink-0 shadow-xs"
              />
              <div 
                className={`flex flex-col transition-all duration-200 ${
                  isExpanded ? "opacity-100 max-w-[150px]" : "opacity-0 max-w-0 overflow-hidden"
                }`}
              >
                <span className="font-extrabold text-base tracking-tight text-slate-900 leading-none">
                  DANASEA
                </span>
                <span className="text-[10px] text-slate-400 font-bold tracking-wider uppercase mt-1 whitespace-nowrap">
                  KÊNH ĐỐI TÁC
                </span>
              </div>
            </div>

            {isExpanded && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setIsPinned((prev) => !prev);
                }}
                className={`p-1.5 rounded-lg text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors cursor-pointer shrink-0 ${
                  isPinned ? "text-slate-900 bg-slate-100" : ""
                }`}
                title={isPinned ? "Bỏ ghim (Tự động thu gọn)" : "Ghim cố định thanh bên"}
                aria-label="Toggle pin sidebar"
              >
                <Pin className={`w-4 h-4 ${isPinned ? "rotate-45 fill-current text-slate-900" : ""}`} />
              </button>
            )}
          </div>

          {/* Navigation Links Grouped */}
          <nav className="p-3 flex flex-col gap-3 overflow-y-auto overflow-x-hidden flex-1">
            {NAV_GROUPS.map((group, gIdx) => (
              <div key={gIdx} className="flex flex-col gap-1">
                {/* Group Title / Divider */}
                {isExpanded ? (
                  <div className="pt-2 pb-1 px-3">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-400 select-none">
                      {group.groupTitle}
                    </span>
                  </div>
                ) : (
                  gIdx > 0 && <div className="my-1.5 mx-2 border-t border-slate-100" />
                )}

                {group.items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.exact}
                    title={!isExpanded ? item.label : undefined}
                    className={({ isActive }) =>
                      `group flex items-center h-10 rounded-xl text-sm font-medium transition-all duration-150 relative ${
                        isExpanded
                          ? "px-3 w-full"
                          : "w-10 justify-center mx-auto"
                      } ${
                        isActive
                          ? "bg-slate-900 text-white shadow-xs font-semibold"
                          : "text-slate-600 hover:text-slate-900 hover:bg-slate-100/80"
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <item.icon
                          className={`w-5 h-5 shrink-0 transition-colors ${
                            isActive ? "text-white" : "text-slate-500 group-hover:text-slate-900"
                          }`}
                          strokeWidth={1.75}
                        />
                        <span
                          className={`truncate whitespace-nowrap transition-all duration-200 ${
                            isExpanded
                              ? "opacity-100 max-w-[180px] ml-3"
                              : "opacity-0 max-w-0 ml-0 overflow-hidden"
                          }`}
                        >
                          {item.label}
                        </span>
                      </>
                    )}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>
        </div>

        {/* Footer info: Notifications, Profile & Logout in Sidebar */}
        <div className="p-3 border-t border-slate-200/80 flex flex-col gap-2 shrink-0">
          {/* Notifications in Sidebar */}
          <div className="relative">
            <button
              onClick={() => setIsNotiOpen((prev) => !prev)}
              title={!isExpanded ? `Thông báo ${unreadCount > 0 ? `(${unreadCount})` : ''}` : undefined}
              className={`group flex items-center h-10 rounded-xl text-sm font-medium transition-all duration-150 cursor-pointer relative ${
                isExpanded ? "px-3 w-full" : "w-10 justify-center mx-auto"
              } ${
                isNotiOpen
                  ? "bg-slate-900 text-white shadow-xs font-semibold"
                  : "text-slate-600 hover:text-slate-900 hover:bg-slate-100/80"
              }`}
            >
              <div className="relative shrink-0 flex items-center justify-center">
                <Bell
                  className={`w-5 h-5 transition-colors ${
                    isNotiOpen ? "text-white" : "text-slate-500 group-hover:text-slate-900"
                  }`}
                  strokeWidth={1.75}
                />
                {unreadCount > 0 && (
                  <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-rose-500 ring-2 ring-white" />
                )}
              </div>
              <span
                className={`truncate whitespace-nowrap transition-all duration-200 ${
                  isExpanded
                    ? "opacity-100 max-w-[150px] ml-3"
                    : "opacity-0 max-w-0 ml-0 overflow-hidden"
                }`}
              >
                Thông báo
              </span>
              {isExpanded && unreadCount > 0 && (
                <span className="ml-auto px-1.5 py-0.5 rounded-full text-[10px] font-bold font-mono bg-rose-100 text-rose-700">
                  {unreadCount}
                </span>
              )}
            </button>

            {/* Notification Popover floating next to Sidebar */}
            {isNotiOpen && (
              <div 
                className={`fixed z-50 bg-white rounded-2xl shadow-2xl border border-slate-200 w-80 sm:w-96 flex flex-col overflow-hidden animate-scale-in ${
                  isExpanded ? "left-[264px] bottom-16" : "left-[74px] bottom-16"
                }`}
              >
                <div className="p-3.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-slate-900">Thông báo đối tác</span>
                    {unreadCount > 0 && (
                      <span className="px-2 py-0.5 rounded-full text-xs font-bold font-mono bg-rose-100 text-rose-700">
                        {unreadCount}
                      </span>
                    )}
                  </div>
                  {unreadCount > 0 && (
                    <button
                      onClick={handleMarkAllAsRead}
                      className="text-xs font-semibold text-sky-600 hover:text-sky-700 transition-colors cursor-pointer flex items-center gap-1"
                    >
                      <CheckCheck className="w-3.5 h-3.5" />
                      <span>Đã đọc tất cả</span>
                    </button>
                  )}
                </div>

                <div className="max-h-80 overflow-y-auto flex flex-col divide-y divide-slate-100">
                  {notifications.length === 0 ? (
                    <div className="py-8 px-4 text-center text-slate-400 text-xs">
                      Chưa có thông báo nào
                    </div>
                  ) : (
                    notifications.map((noti) => (
                      <div
                        key={noti.id}
                        onClick={() => handleNotificationClick(noti)}
                        className={`p-3.5 hover:bg-slate-50 transition-colors cursor-pointer flex flex-col gap-1 relative ${
                          !noti.isRead ? "bg-sky-50/40" : ""
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            {!noti.isRead && (
                              <span className="w-2 h-2 rounded-full bg-sky-500 shrink-0" />
                            )}
                            <span className="text-[11px] font-semibold px-2 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-700">
                              {noti.type}
                            </span>
                          </div>
                          <span className="text-[11px] text-slate-400">
                            {noti.createdAt ? new Date(noti.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : ""}
                          </span>
                        </div>
                        <h4 className="font-bold text-slate-900 text-xs line-clamp-1">{noti.title}</h4>
                        <p className="text-slate-600 text-xs line-clamp-2">{noti.body}</p>
                      </div>
                    ))
                  )}
                </div>

                <div className="p-2.5 border-t border-slate-100 text-center bg-slate-50/50">
                  <button
                    onClick={() => setIsNotiOpen(false)}
                    className="text-xs text-slate-600 font-semibold hover:text-slate-900 cursor-pointer"
                  >
                    Đóng
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Account Profile Card */}
          <div
            onClick={() => navigate("/vendor/profile")}
            title={!isExpanded ? vendorDisplayName : undefined}
            className={`group flex items-center h-12 rounded-xl transition-all duration-150 cursor-pointer ${
              isExpanded 
                ? "px-2.5 w-full bg-slate-50 hover:bg-slate-100/90 border border-slate-200/60" 
                : "w-10 justify-center mx-auto"
            }`}
          >
            <AtlasAvatar name={vendorDisplayName} size="sm" />
            {isExpanded && (
              <div className="flex flex-col min-w-0 ml-2.5 text-left flex-1">
                <span className="text-xs font-bold text-slate-900 truncate leading-tight">
                  {vendorDisplayName}
                </span>
                <span className="text-[10px] text-slate-400 font-medium truncate mt-0.5">
                  Đối tác vận hành
                </span>
              </div>
            )}
          </div>

          {/* Logout Button */}
          <button
            onClick={() => {
              useAuthStore.getState().logout();
              navigate("/auth");
            }}
            title={!isExpanded ? "Đăng xuất" : undefined}
            className={`group flex items-center h-9 rounded-xl text-xs font-medium text-rose-600 hover:bg-rose-50 transition-all duration-150 cursor-pointer ${
              isExpanded ? "px-3 w-full" : "w-10 justify-center mx-auto"
            }`}
          >
            <LogOut className="w-4 h-4 text-rose-500 shrink-0" strokeWidth={1.75} />
            <span
              className={`truncate whitespace-nowrap transition-all duration-200 ${
                isExpanded
                  ? "opacity-100 max-w-[180px] ml-3"
                  : "opacity-0 max-w-0 ml-0 overflow-hidden"
              }`}
            >
              Đăng xuất
            </span>
          </button>
        </div>
      </aside>

      {/* Main dynamic outlet */}
      <div className="flex-1 flex flex-col min-w-0 print:block print:w-full print:m-0 print:p-0">
        <main className="flex-1 overflow-y-auto overflow-x-hidden print:overflow-visible print:h-auto print:w-full print:m-0 print:p-0">
          <div
            key={location.pathname}
            onAnimationEnd={() => setAnimationClass("")}
            className={`min-h-full flex flex-col ${animationClass}`}
          >
            <Outlet />
          </div>
        </main>
      </div>

      {/* Floating Copilot Trigger Button (Styled identically to Customer deck) */}
      {location.pathname !== "/vendor/copilot" && (
        <aside
          aria-label="Menu Trợ lý Copilot"
          className="fixed bottom-6 right-6 z-40 flex flex-col items-center select-none print:hidden"
        >
          <div className="relative group/btn flex items-center">
            <span className="absolute right-full mr-2.5 px-2.5 py-1 rounded-lg bg-slate-900/90 text-white text-xs font-medium whitespace-nowrap opacity-0 group-hover/btn:opacity-100 transition-opacity pointer-events-none shadow-md">
              DANASEA Copilot
            </span>
            <button
              type="button"
              onClick={toggleCopilot}
              aria-label="Trợ lý kinh doanh DANASEA Copilot"
              className={`relative w-11 h-11 rounded-2xl flex items-center justify-center border shadow-md transition-all cursor-pointer ${
                isCopilotOpen
                  ? "bg-slate-900 text-white border-slate-900 shadow-slate-900/20"
                  : "bg-white text-slate-900 border-slate-200/90 hover:border-slate-400 hover:shadow-lg"
              }`}
            >
              <Bot className="w-5 h-5 text-current transition-transform group-hover/btn:scale-110" />
            </button>
          </div>
        </aside>
      )}

      {/* Vendor Copilot Slide-over Drawer */}
      <VendorCopilotDrawer />


      {/* Notification Detail Modal (Portaled directly to document.body) */}
      {selectedNoti && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-lg flex flex-col gap-4 border border-slate-200/90">
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
                <span>Thời gian: {selectedNoti.createdAt ? new Date(selectedNoti.createdAt).toLocaleString('vi-VN') : ''}</span>
              </div>
              <h4 className="font-headline-sm font-bold text-on-surface mt-1">{selectedNoti.title}</h4>
              <p className="p-3 rounded-xl bg-surface-container-low text-on-surface leading-relaxed">
                {selectedNoti.body}
              </p>

              {/* Related entity link */}
              {selectedNoti.relatedEntityId && (
                <div className="p-2.5 rounded-xl bg-sky-50 text-sky-900 text-[12px] flex items-center justify-between">
                  <span>Thực thể liên quan: <strong>{selectedNoti.relatedEntityType || 'Đối tượng'} #{selectedNoti.relatedEntityId.slice(0, 8)}</strong></span>
                  <button 
                    onClick={() => {
                      setSelectedNoti(null);
                      setIsNotiOpen(false);
                      if (selectedNoti.relatedEntityType === 'ORDER' || selectedNoti.relatedEntityType === 'SUB_ORDER') {
                        navigate('/vendor/orders');
                      } else {
                        navigate('/vendor/schedule');
                      }
                    }}
                    className="font-bold underline ml-2 text-sky-700 hover:text-sky-900 cursor-pointer"
                  >
                    Chi tiết
                  </button>
                </div>
              )}


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
