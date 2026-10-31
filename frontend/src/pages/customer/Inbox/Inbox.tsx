import { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { notificationService } from "../../../services/notificationService";
import type { NotificationResponse } from "../../../types/notification";
import {
  MessageSquare,
  Bell,
  Search,
  SlidersHorizontal,
  Send,
  Paperclip,
  ShieldCheck,
  CheckCheck,
  Phone,
  ChevronDown,
  ArrowRight,
  ArrowLeft,
  Loader2,
  BellOff,
  CalendarCheck,
} from "lucide-react";

export function Inbox() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabQuery = searchParams.get("tab");
  const activeTab = tabQuery === "notifications" ? "notifications" : "messages";
  const setActiveTab = (tab: "messages" | "notifications") => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("tab", tab);
      return next;
    });
  };
  const [_activeChat, setActiveChat] = useState<number | null>(1);
  const [showOrder, setShowOrder] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loadingNotifs, setLoadingNotifs] = useState(false);

  useEffect(() => {
    let ignore = false;
    const run = async () => {
      await Promise.resolve();
      if (ignore) return;
      if (activeTab === "notifications") {
        setLoadingNotifs(true);
        try {
          const res = await notificationService.getMyNotifications(0, 50);
          if (!ignore) setNotifications(res.content || []);
        } catch {
          // ignore
        } finally {
          if (!ignore) setLoadingNotifs(false);
        }
      }
    };
    run();
    return () => {
      ignore = true;
    };
  }, [activeTab]);

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        <div className="w-full max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col gap-6">
          {/* Navigation Breadcrumb */}
          <div className="flex items-center gap-2 text-[13px] text-slate-500 font-medium">
            <button 
              onClick={() => navigate('/profile')} 
              className="hover:text-slate-900 transition-colors cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1 text-slate-600 font-semibold"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Tài khoản của bạn</span>
            </button>
            <span className="text-slate-300">/</span>
            <span className="text-slate-900 font-semibold">Hộp thư &amp; Hỗ trợ trực tuyến</span>
          </div>

          {/* Top Header & Dual Tab Switcher */}
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-slate-200/80 pb-6">
            <div>
              <h1 className="text-2xl sm:text-[28px] font-bold text-slate-900 tracking-tight">
                Hộp thư &amp; Thông báo
              </h1>
              <p className="text-sm text-slate-600 mt-1 max-w-2xl">
                Quản lý trao đổi với câu lạc bộ biển và nhận cập nhật chuyến đi theo thời gian thực
              </p>
            </div>

            {/* Segmented Tab Control - Airbnb Clean Style */}
            <div className="flex items-center p-1 bg-slate-100 rounded-xl self-start md:self-auto border border-slate-200/60">
              <button
                className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                  activeTab === "messages"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                onClick={() => setActiveTab("messages")}
                type="button"
              >
                <MessageSquare className="w-4 h-4" />
                <span>Tin nhắn câu lạc bộ</span>
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-slate-200 text-slate-700 text-[11px] font-bold">
                  3
                </span>
              </button>

              <button
                className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                  activeTab === "notifications"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
                onClick={() => setActiveTab("notifications")}
                type="button"
              >
                <Bell className="w-4 h-4" />
                <span>Thông báo hệ thống</span>
              </button>
            </div>
          </div>

          {/* TAB VIEW 1: MESSAGES (MASTER-DETAIL CHAT) */}
          {activeTab === "messages" && (
            <div className="w-full grid grid-cols-12 gap-6 items-start">
              {/* LEFT COLUMN: CONVERSATION LIST */}
              <div className="col-span-12 lg:col-span-4 flex flex-col bg-white rounded-2xl shadow-xs border border-slate-200/80 overflow-hidden min-h-[640px] max-h-[720px]">
                {/* Search Bar */}
                <div className="p-4 border-b border-slate-100 flex flex-col gap-3">
                  <div className="relative w-full">
                    <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      className="w-full h-10 pl-9 pr-4 rounded-xl bg-slate-50 border border-slate-200 text-xs sm:text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:bg-white transition-colors"
                      placeholder="Tìm đối tác hoặc mã đơn..."
                      type="text"
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-500 font-semibold px-1">
                    <span>ĐỐI TÁC VẬN HÀNH BIỂN (4)</span>
                    <span className="flex items-center gap-1 text-slate-700 hover:text-slate-900 cursor-pointer">
                      <SlidersHorizontal className="w-3 h-3" />
                      Lọc
                    </span>
                  </div>
                </div>

                {/* Conversation Items */}
                <div className="flex flex-col overflow-y-auto p-2 space-y-1 flex-1">
                  {/* Conversation 1 (Active) */}
                  <div
                    className="flex items-start gap-3 p-3 rounded-xl cursor-pointer transition-colors bg-slate-50 border border-slate-200/70"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-11 h-11 rounded-xl bg-slate-900 text-white flex items-center justify-center font-bold text-sm">
                        ST
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-emerald-500 ring-2 ring-white"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-bold text-sm text-slate-900 truncate">
                          Son Tra Marine Diving
                        </span>
                        <span className="text-[11px] text-slate-500 shrink-0">
                          10:42
                        </span>
                      </div>
                      <p className="text-xs text-slate-600 truncate font-semibold">
                        Lặn ngắm san hô Bãi Bụt
                      </p>
                      <p className="text-xs text-slate-500 truncate mt-0.5">
                        Dạ anh An, cano cao tốc đón anh lúc 07:30 sáng mai tại...
                      </p>
                    </div>
                  </div>

                  {/* Conversation 2 */}
                  <div
                    className="flex items-start gap-3 p-3 rounded-xl cursor-pointer transition-colors hover:bg-slate-50 border border-transparent"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-11 h-11 rounded-xl bg-sky-600 text-white flex items-center justify-center font-bold text-sm">
                        DO
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-emerald-500 ring-2 ring-white"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-bold text-sm text-slate-900 truncate">
                          Danang Ocean Club
                        </span>
                        <span className="text-[11px] text-slate-400 shrink-0">
                          Hôm qua
                        </span>
                      </div>
                      <p className="text-xs text-slate-600 truncate font-medium">
                        Chèo SUP Mỹ Khê
                      </p>
                      <p className="text-xs text-slate-500 truncate mt-0.5">
                        HLV Huy đã gửi ảnh flycam buổi chèo SUP của anh rồi ạ!
                      </p>
                    </div>
                  </div>

                  {/* Conversation 3 */}
                  <div
                    className="flex items-start gap-3 p-3 rounded-xl cursor-pointer transition-colors hover:bg-slate-50 border border-transparent"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-11 h-11 rounded-xl bg-slate-700 text-white flex items-center justify-center font-bold text-sm">
                        SF
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-slate-300 ring-2 ring-white"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-bold text-sm text-slate-900 truncate">
                          SkyFly Dù Lượn Bán Đảo
                        </span>
                        <span className="text-[11px] text-slate-400 shrink-0">
                          12/10
                        </span>
                      </div>
                      <p className="text-xs text-slate-600 truncate font-medium">
                        Dù lượn ngắm biển
                      </p>
                      <p className="text-xs text-slate-500 truncate mt-0.5">
                        Cảm ơn anh đã đánh giá 5 sao cho phi công của SkyFly!
                      </p>
                    </div>
                  </div>
                </div>

                {/* Direct Emergency Callout */}
                <div className="mt-auto p-3.5 bg-slate-50 border-t border-slate-100 flex items-center gap-3">
                  <Phone className="w-4 h-4 text-slate-700 shrink-0" />
                  <div className="flex-1 min-w-0 text-xs">
                    <p className="font-bold text-slate-900 truncate">Trực ban cứu hộ biển</p>
                    <p className="text-slate-500 truncate">Ban Quản lý Bán Đảo: 1900 8468</p>
                  </div>
                </div>
              </div>

              {/* RIGHT COLUMN: CHAT WINDOW */}
              <div className="col-span-12 lg:col-span-8 flex flex-col bg-white rounded-2xl shadow-xs border border-slate-200/80 overflow-hidden min-h-[640px] max-h-[720px] relative">
                {/* Chat Top Header */}
                <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between gap-4 z-10">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="relative shrink-0">
                      <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center font-bold text-sm">
                        ST
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-emerald-500 ring-2 ring-white"></span>
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h2 className="font-bold text-sm sm:text-base text-slate-900 truncate">
                          Son Tra Marine Diving
                        </h2>
                        <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[10px] font-bold border border-emerald-200">
                          Đối tác xác minh
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                        <span>Đang trực tuyến • Tour Lặn san hô Bãi Bụt</span>
                      </p>
                    </div>
                  </div>

                  {/* Toggle Related Order Drawer */}
                  <button
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-800 text-xs font-semibold transition-colors cursor-pointer"
                    onClick={() => setShowOrder(!showOrder)}
                    type="button"
                  >
                    <span>Đơn liên quan #DNS-89412</span>
                    <ChevronDown className="w-3.5 h-3.5 text-slate-500" />
                  </button>
                </div>

                {/* Collapsible Order Drawer */}
                {showOrder && (
                  <div className="bg-slate-50 border-b border-slate-200 p-4 transition-all">
                    <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className="w-14 h-14 rounded-lg overflow-hidden shrink-0 bg-slate-100">
                          <img
                            className="w-full h-full object-cover"
                            alt="Tour Lặn biển"
                            src="https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=200"
                          />
                        </div>
                        <div className="min-w-0">
                          <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-200">
                            ĐÃ THANH TOÁN
                          </span>
                          <h4 className="font-bold text-xs sm:text-sm text-slate-900 mt-1 truncate">
                            Tour Lặn biển Chuyên nghiệp Rạn San Hô Sơn Trà
                          </h4>
                          <p className="text-xs text-slate-500 mt-0.5">
                            08:00 - 11:30, Ngày mai • 02 khách
                          </p>
                        </div>
                      </div>

                      <div className="flex sm:flex-col items-end justify-between sm:justify-center shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-100">
                        <span className="text-sm font-extrabold text-slate-900 tabular-nums">
                          900.000₫
                        </span>
                        <Link to="/profile/orders" className="text-xs text-sky-600 hover:underline font-medium">
                          Xem vé điện tử
                        </Link>
                      </div>
                    </div>
                  </div>
                )}

                {/* Chat Messages Log */}
                <div className="flex-1 overflow-y-auto p-5 flex flex-col gap-4 bg-slate-50/40">
                  <div className="flex items-center justify-center my-1">
                    <span className="px-3 py-1 rounded-md bg-white border border-slate-200 text-slate-500 text-xs font-medium">
                      Hôm nay
                    </span>
                  </div>

                  {/* Vendor Message */}
                  <div className="flex items-end gap-2.5 max-w-[80%] self-start">
                    <div className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center font-bold text-xs shrink-0 mb-1">
                      ST
                    </div>
                    <div className="flex flex-col gap-1">
                      <div className="p-3.5 rounded-2xl rounded-bl-xs bg-white border border-slate-200 text-slate-800 text-xs sm:text-sm shadow-2xs">
                        <p>
                          Chào anh An! Đội ngũ huấn luyện viên lặn PADI của Son Tra Marine đã sẵn sàng cho chuyến thám hiểm san hô ngày mai của 2 bạn tại Bãi Bụt.
                        </p>
                      </div>
                      <span className="text-[11px] text-slate-400 px-1">09:15</span>
                    </div>
                  </div>

                  {/* Customer Message */}
                  <div className="flex items-end gap-2 max-w-[75%] self-end">
                    <div className="flex flex-col items-end gap-1">
                      <div className="p-3.5 rounded-2xl rounded-br-xs bg-[#0284c7] text-white text-xs sm:text-sm shadow-2xs">
                        <p>
                          Chào bạn! Ngày mai bọn mình có cần chuẩn bị kính lặn và chân vịt riêng không, hay bên bạn cung cấp sẵn?
                        </p>
                      </div>
                      <div className="flex items-center gap-1 px-1 text-[11px] text-slate-400">
                        <span>09:30</span>
                        <CheckCheck className="w-3.5 h-3.5 text-sky-600" />
                      </div>
                    </div>
                  </div>

                  {/* Vendor Message with Attachment */}
                  <div className="flex items-end gap-2.5 max-w-[80%] self-start">
                    <div className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center font-bold text-xs shrink-0 mb-1">
                      ST
                    </div>
                    <div className="flex flex-col gap-1">
                      <div className="p-3.5 rounded-2xl rounded-bl-xs bg-white border border-slate-200 text-slate-800 text-xs sm:text-sm shadow-2xs flex flex-col gap-2.5">
                        <p>
                          Anh hoàn toàn yên tâm nhé, gói tour #DNS-89412 đã bao gồm trọn bộ trang thiết bị chuẩn quốc tế đã khử khuẩn: kính lặn góc rộng kèm van thở khô, áo phao SOLAS và đồ bơi giữ nhiệt.
                        </p>
                        <div className="rounded-xl overflow-hidden border border-slate-200 bg-slate-100">
                          <img
                            src="https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500"
                            alt="Thiết bị lặn chuẩn PADI"
                            className="w-full h-36 object-cover"
                          />
                        </div>
                      </div>
                      <span className="text-[11px] text-slate-400 px-1">09:35</span>
                    </div>
                  </div>
                </div>

                {/* Chat Composer */}
                <div className="p-4 bg-white border-t border-slate-100">
                  <form className="flex items-center gap-2" onSubmit={(e) => e.preventDefault()}>
                    <button
                      className="w-10 h-10 rounded-xl flex items-center justify-center text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition-colors shrink-0 cursor-pointer"
                      title="Đính kèm tệp"
                      type="button"
                    >
                      <Paperclip className="w-4 h-4" />
                    </button>
                    
                    <input
                      className="flex-1 h-10 px-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs sm:text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors"
                      placeholder="Nhập tin nhắn trao đổi với đối tác..."
                      type="text"
                    />

                    <button
                      className="h-10 px-4 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-xs sm:text-sm flex items-center justify-center gap-1.5 transition-colors cursor-pointer shrink-0 shadow-xs"
                      type="submit"
                    >
                      <span>Gửi</span>
                      <Send className="w-3.5 h-3.5" />
                    </button>
                  </form>
                  <div className="flex items-center justify-between mt-2 text-[11px] text-slate-500 px-1">
                    <span className="flex items-center gap-1">
                      <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                      Cuộc trò chuyện được lưu trữ và bảo vệ bởi DANASEA
                    </span>
                    <span className="hidden sm:inline">Nhấn Enter để gửi</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB VIEW 2: SYSTEM NOTIFICATIONS */}
          {activeTab === "notifications" && (
            <div className="w-full flex flex-col gap-4">
              <div className="flex items-center justify-between pb-2">
                <h2 className="text-base sm:text-lg font-bold text-slate-900">
                  Thông báo từ hệ thống &amp; Đối tác tổ chức
                </h2>
                <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-700 text-xs font-semibold">
                  {notifications.length} thông báo
                </span>
              </div>

              {loadingNotifs && (
                <div className="p-12 text-center bg-white rounded-2xl border border-slate-200">
                  <Loader2 className="w-8 h-8 text-slate-500 animate-spin mx-auto" />
                  <p className="text-xs text-slate-500 mt-2 font-medium">Đang tải thông báo từ hệ thống...</p>
                </div>
              )}

              {!loadingNotifs && notifications.length === 0 && (
                <div className="p-12 text-center bg-white rounded-2xl border border-slate-200/80">
                  <BellOff className="w-10 h-10 text-slate-400 mx-auto" />
                  <p className="text-sm text-slate-700 mt-2 font-semibold">Hiện bạn chưa có thông báo mới.</p>
                  <p className="text-xs text-slate-500 mt-1">Các cập nhật lịch trình và thời tiết sẽ hiển thị tại đây.</p>
                </div>
              )}

              {!loadingNotifs && notifications.length > 0 && (
                <div className="flex flex-col gap-3">
                  {notifications.map((notif) => {
                    const isProposal = notif.type === "RESCHEDULE_PROPOSAL" || notif.type === "DISRUPTION_DECISION";
                    return (
                      <div
                        key={notif.id}
                        className={`p-5 rounded-2xl shadow-xs transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4 border ${
                          isProposal
                            ? "bg-amber-50/60 border-amber-200"
                            : "bg-white border-slate-200/80"
                        }`}
                      >
                        <div className="flex items-start gap-4">
                          <div
                            className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                              isProposal
                                ? "bg-amber-100 text-amber-800"
                                : "bg-slate-100 text-slate-800"
                            }`}
                          >
                            {isProposal ? (
                              <CalendarCheck className="w-5 h-5" />
                            ) : (
                              <Bell className="w-5 h-5" />
                            )}
                          </div>
                          <div className="flex flex-col gap-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span
                                className={`px-2 py-0.5 rounded-md text-[10px] font-bold ${
                                  isProposal
                                    ? "bg-amber-200 text-amber-900"
                                    : "bg-slate-100 text-slate-700"
                                }`}
                              >
                                {isProposal ? "ĐỀ XUẤT ĐỔI CA" : "THÔNG BÁO"}
                              </span>
                              <span className="text-xs text-slate-500 font-mono">
                                {new Date(notif.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} • {new Date(notif.createdAt).toLocaleDateString("vi-VN")}
                              </span>
                            </div>
                            <h3 className="font-bold text-sm text-slate-900">
                              {notif.title}
                            </h3>
                            <p className="text-xs sm:text-sm text-slate-600 max-w-2xl leading-relaxed">
                              {notif.body}
                            </p>
                          </div>
                        </div>

                        <div className="shrink-0 flex items-center self-start sm:self-center">
                          <Link
                            to="/profile/orders"
                            className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-medium transition-colors flex items-center gap-1.5"
                          >
                            <span>Xem đơn &amp; Phản hồi</span>
                            <ArrowRight className="w-3.5 h-3.5" />
                          </Link>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
export default Inbox;
