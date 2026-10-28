import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { notificationService } from "../../../services/notificationService";
import type { NotificationResponse } from "../../../types/notification";

export function Inbox() {
  const [activeTab, setActiveTab] = useState("messages");
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
    <main className="w-full pt-20 bg-surface min-h-screen">
      <div className="flex flex-col w-full">
        {/* Subtle Ambient Ocean Glow Behind Header Area */}
        <div className="relative w-full max-w-[1280px] mx-auto px-gutter pt-space-xl pb-space-3xl flex flex-col gap-space-xl">
          {/* Top Context & Dual Primary Tabs Switcher */}
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-lg">
            <div className="flex flex-col gap-space-xs">
              <div className="flex items-center gap-space-sm text-primary font-label-md text-label-md">
                <span className="material-symbols-outlined text-[18px]">
                  waves
                </span>
                <span>TRUNG TÂM TƯƠNG TÁC DU KHÁCH</span>
                <span className="w-1.5 h-1.5 rounded-full bg-secondary"></span>
                <span className="text-on-surface-variant">Thời gian thực</span>
              </div>
              <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
                Hộp thư &amp; Thông báo
              </h1>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
                Quản lý toàn bộ trao đổi trực tiếp với câu lạc bộ biển, huấn
                luyện viên đạt chuẩn và các cập nhật hành trình thể thao tại Đà
                Nẵng.
              </p>
            </div>
            {/* Segmented Tab Nav Control */}
            <div className="flex items-center p-1.5 bg-surface-container-low rounded-xl shadow-sm self-start md:self-auto">
              <button
                className="flex items-center gap-space-sm px-space-lg py-space-sm rounded-lg font-label-lg text-label-lg transition-all duration-200 bg-surface-container-lowest text-primary shadow-sm"
                id="tab-btn-messages"
                onClick={() => setActiveTab("messages")}
              >
                <span
                  className="material-symbols-outlined text-[20px]"
                  style={{ fontVariationSettings: "'FILL' 1" }}
                >
                  chat_bubble
                </span>
                <span>Hộp thoại tin nhắn</span>
                <span className="ml-1 px-2 py-0.5 rounded-full bg-secondary text-on-secondary font-label-sm text-label-sm">
                  3
                </span>
              </button>
              <button
                className="flex items-center gap-space-sm px-space-lg py-space-sm rounded-lg font-label-lg text-label-lg transition-all duration-200 text-on-surface-variant hover:text-on-surface"
                id="tab-btn-notifications"
                onClick={() => setActiveTab("notifications")}
              >
                <span className="material-symbols-outlined text-[20px]">
                  notifications
                </span>
                <span>Thông báo hệ thống</span>
                <span className="w-2 h-2 rounded-full bg-primary"></span>
              </button>
            </div>
          </div>
          {/* TAB VIEW 1: HỘP THOẠI TIN NHẮN (MASTER-DETAIL CHAT) */}
          {activeTab === "messages" && (
            <div
              className="w-full grid grid-cols-12 gap-gutter items-start"
              id="view-messages"
            >
              {/* CỘT TRÁI (CONVERSATIONS LIST) */}
              <div className="col-span-12 lg:col-span-4 flex flex-col bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden min-h-[720px] max-h-[780px]">
                {/* Search & Filter Area */}
                <div className="p-space-md bg-surface-container-lowest flex flex-col gap-space-sm">
                  <div className="relative w-full">
                    <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-[20px]">
                      search
                    </span>
                    <input
                      className="w-full h-11 pl-10 pr-4 rounded-xl bg-surface-container-low text-on-surface font-body-md text-body-md placeholder:text-outline focus:outline-none focus:bg-surface-container-lowest transition-colors"
                      placeholder="Tìm câu lạc bộ, đối tác, số đơn..."
                      type="text"
                    />
                  </div>
                  <div className="flex items-center justify-between text-on-surface-variant font-label-sm text-label-sm px-1 pt-1">
                    <span>ĐỐI TÁC VẬN HÀNH BIỂN (4)</span>
                    <span className="flex items-center gap-1 text-primary cursor-pointer hover:underline">
                      <span className="material-symbols-outlined text-[14px]">
                        tune
                      </span>
                      Bộ lọc
                    </span>
                  </div>
                </div>
                {/* Conversations Scroll List */}
                <div className="flex flex-col overflow-y-auto px-space-xs pb-space-md space-y-1">
                  {/* Conversation Item 1 (Active) */}
                  <div
                    className="chat-thread-item active-chat flex items-start gap-space-md p-space-md rounded-xl cursor-pointer transition-all duration-200 bg-surface-container-low"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-xl bg-primary-container text-on-primary-container flex items-center justify-center font-headline-sm text-headline-sm overflow-hidden shadow-sm">
                        <span className="material-symbols-outlined text-[26px]">
                          scuba_diving
                        </span>
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-emerald-500 ring-2 ring-surface-container-lowest"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-headline-sm text-[15px] leading-tight text-on-surface truncate">
                          Son Tra Marine Diving
                        </span>
                        <span className="font-label-sm text-label-sm text-primary shrink-0">
                          10:42
                        </span>
                      </div>
                      <p className="font-label-sm text-label-sm text-primary mb-1">
                        Lặn ngắm san hô Bãi Bụt
                      </p>
                      <p className="font-body-md text-body-sm text-on-surface truncate font-semibold">
                        Dạ anh An, cano cao tốc đón anh lúc 07:30 sáng mai tại
                        cầu cảng...
                      </p>
                    </div>
                    <span className="w-2.5 h-2.5 rounded-full bg-secondary shrink-0 mt-2"></span>
                  </div>
                  {/* Conversation Item 2 */}
                  <div
                    className="chat-thread-item flex items-start gap-space-md p-space-md rounded-xl cursor-pointer transition-all duration-200 hover:bg-surface-container-low"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-xl bg-tertiary-container text-on-tertiary-container flex items-center justify-center font-headline-sm text-headline-sm overflow-hidden shadow-sm">
                        <span className="material-symbols-outlined text-[26px]">
                          surfing
                        </span>
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-emerald-500 ring-2 ring-surface-container-lowest"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-headline-sm text-[15px] leading-tight text-on-surface truncate">
                          Danang Ocean Club
                        </span>
                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0">
                          Hôm qua
                        </span>
                      </div>
                      <p className="font-label-sm text-label-sm text-tertiary mb-1">
                        Chèo SUP &amp; Lướt ván Mỹ Khê
                      </p>
                      <p className="font-body-md text-body-sm text-on-surface-variant truncate">
                        HLV Huy đã gửi ảnh flycam buổi chèo SUP bình minh của
                        anh rồi ạ!
                      </p>
                    </div>
                    <span className="w-2 h-2 rounded-full bg-secondary shrink-0 mt-2"></span>
                  </div>
                  {/* Conversation Item 3 */}
                  <div
                    className="chat-thread-item flex items-start gap-space-md p-space-md rounded-xl cursor-pointer transition-all duration-200 hover:bg-surface-container-low"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-xl bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-headline-sm text-headline-sm overflow-hidden shadow-sm">
                        <span className="material-symbols-outlined text-[26px]">
                          paragliding
                        </span>
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-slate-400 ring-2 ring-surface-container-lowest"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-headline-sm text-[15px] leading-tight text-on-surface truncate">
                          SkyFly Dù Lượn Bán Đảo
                        </span>
                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0">
                          12/10
                        </span>
                      </div>
                      <p className="font-label-sm text-label-sm text-tertiary mb-1">
                        Dù lượn ngắm biển Phạm Văn Đồng
                      </p>
                      <p className="font-body-md text-body-sm text-on-surface-variant truncate">
                        Cảm ơn anh đã đánh giá 5 sao cho phi công của SkyFly!
                        Hẹn gặp lại anh...
                      </p>
                    </div>
                  </div>
                  {/* Conversation Item 4 */}
                  <div
                    className="chat-thread-item flex items-start gap-space-md p-space-md rounded-xl cursor-pointer transition-all duration-200 hover:bg-surface-container-low"
                    onClick={() => setActiveChat(1)}
                  >
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-xl bg-surface-container-high text-primary flex items-center justify-center font-headline-sm text-headline-sm overflow-hidden shadow-sm">
                        <span className="material-symbols-outlined text-[26px]">
                          speed
                        </span>
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-slate-400 ring-2 ring-surface-container-lowest"></span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-1 mb-0.5">
                        <span className="font-headline-sm text-[15px] leading-tight text-on-surface truncate">
                          Jetski Express Danang
                        </span>
                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0">
                          08/10
                        </span>
                      </div>
                      <p className="font-label-sm text-label-sm text-tertiary mb-1">
                        Mô tô nước tốc độ cao Bãi Non Nước
                      </p>
                      <p className="font-body-md text-body-sm text-on-surface-variant truncate">
                        Gói bảo hiểm biển quốc tế PADI của đơn #DNS-77402 đã
                        được kích hoạt.
                      </p>
                    </div>
                  </div>
                </div>
                {/* Maritime Safety Direct Support Callout */}
                <div className="mt-auto p-space-md bg-surface-container-low flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">
                    support_agent
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="font-label-md text-label-md text-on-surface">
                      Cứu hộ &amp; Trực ban Bãi biển
                    </p>
                    <p className="font-body-sm text-body-sm text-on-surface-variant truncate">
                      Kênh khẩn cấp Ban Quản lý Bán Đảo
                    </p>
                  </div>
                  <button className="px-2.5 py-1.5 rounded-lg bg-surface-container-lowest text-primary font-label-sm text-label-sm shadow-sm hover:bg-primary hover:text-on-primary transition-colors">
                    Gọi ngay
                  </button>
                </div>
              </div>
              {/* CỘT PHẢI (KHUNG CHAT CHI TIẾT) */}
              <div className="col-span-12 lg:col-span-8 flex flex-col bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden min-h-[720px] max-h-[780px] relative">
                {/* Header Chat */}
                <div className="px-space-lg py-space-md bg-surface-container-lowest flex items-center justify-between gap-space-md z-10 shadow-[0_1px_4px_rgba(0,0,0,0.03)]">
                  <div className="flex items-center gap-space-md min-w-0">
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-xl bg-primary-container text-on-primary-container flex items-center justify-center font-headline-sm text-headline-sm shadow-sm">
                        <span className="material-symbols-outlined text-[26px]">
                          scuba_diving
                        </span>
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-emerald-500 ring-2 ring-surface-container-lowest"></span>
                    </div>
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-space-sm flex-wrap">
                        <h2 className="font-headline-sm text-headline-sm text-on-surface truncate">
                          Son Tra Marine Diving
                        </h2>
                        <span className="px-2 py-0.5 rounded-full bg-surface-container-low text-primary font-label-sm text-label-sm flex items-center gap-1">
                          <span className="material-symbols-outlined text-[13px]">
                            verified
                          </span>
                          Đối tác kim cương
                        </span>
                      </div>
                      <div className="flex items-center gap-space-sm text-body-sm font-body-sm text-on-surface-variant">
                        <span className="flex items-center gap-1 text-emerald-600 font-label-sm text-label-sm">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                          Đang trực tuyến
                        </span>
                        <span>•</span>
                        <span className="truncate">
                          Trải nghiệm: Lặn bình khí &amp; Ngắm san hô Bãi Bụt
                        </span>
                      </div>
                    </div>
                  </div>
                  {/* Action Button: Xem đơn hàng liên quan */}
                  <div className="shrink-0 flex items-center gap-space-sm">
                    <button
                      className="flex items-center gap-space-sm px-space-md py-2 rounded-xl bg-surface-container-low hover:bg-primary-container hover:text-on-primary-container text-primary font-label-lg text-label-lg transition-all shadow-sm group"
                      onClick={() => setShowOrder(!showOrder)}
                    >
                      <span className="material-symbols-outlined text-[18px]">
                        receipt_long
                      </span>
                      <span className="hidden sm:inline">
                        Đơn hàng liên quan
                      </span>
                      <span className="font-bold text-secondary">
                        #DNS-89412
                      </span>
                      <span className="material-symbols-outlined text-[18px] group-hover:rotate-180 transition-transform">
                        expand_more
                      </span>
                    </button>
                  </div>
                </div>
                {/* Order Summary Floating Drawer (Collapsible) */}
                <div
                  className={`${showOrder ? "block" : "hidden"} bg-surface-container-low p-space-md transition-all duration-300`}
                  id="order-drawer"
                >
                  <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-space-md">
                    <div className="flex items-start gap-space-md">
                      <div className="w-16 h-16 rounded-lg overflow-hidden shrink-0 bg-surface-container-high">
                        <img
                          className="w-full h-full object-cover"
                          data-alt="Clear turquoise waters of Son Tra Peninsula with coral reef snorkelers swimming under gentle tropical morning sunlight."
                          src="https://lh3.googleusercontent.com/aida-public/AB6AXuDRwwUIyOSWortLgdQ1Kf_wrw1sUE34F_S0nNjzkkhXvy1-CXljNUjJoMiUm96tk55rFrELYGs3QVzgkYyTUQJVcYKdKBHNNOz4Enuz2XehvL8NUimHY8rQ_qHcopg0p1_wLLD4CgFAD7Zvj6B3Z-YQMYjoC8DcjoZb4M-clK9RPdftKJjDueKrtcnTK5tUe8vXckszu_5_tVvVpbt66mH7FKS3k9VuRdb-K25Uil-t00Dl3IRIp-_ZEA"
                        />
                      </div>
                      <div className="flex flex-col">
                        <div className="flex items-center gap-2">
                          <span className="px-2 py-0.5 rounded-md bg-secondary-fixed text-on-secondary-fixed font-label-sm text-label-sm">
                            ĐÃ XÁC NHẬN
                          </span>
                          <span className="font-label-md text-label-md text-on-surface">
                            Mã vé: #DNS-89412
                          </span>
                        </div>
                        <h4 className="font-headline-sm text-[16px] text-on-surface mt-0.5">
                          Tour Lặn biển Chuyên nghiệp Khám phá Rạn San Hô Sơn
                          Trà
                        </h4>
                        <p className="font-body-sm text-body-sm text-on-surface-variant flex items-center gap-2 mt-1">
                          <span className="flex items-center gap-1">
                            <span className="material-symbols-outlined text-[14px]">
                              calendar_today
                            </span>
                            08:00 - 11:30, Ngày mai (18/10/2025)
                          </span>
                          <span className="flex items-center gap-1">
                            <span className="material-symbols-outlined text-[14px]">
                              group
                            </span>
                            02 Khách
                          </span>
                        </p>
                      </div>
                    </div>
                    <div className="flex flex-row md:flex-col items-end justify-between md:justify-center gap-1 shrink-0">
                      <span className="font-label-sm text-label-sm text-on-surface-variant">
                        Tổng thanh toán
                      </span>
                      <span className="font-headline-md text-headline-md text-secondary">
                        1.780.000 đ
                      </span>
                      <a
                        className="font-label-sm text-label-sm text-primary hover:underline flex items-center gap-0.5"
                        href="/"
                      >
                        Xem E-ticket vé điện tử{" "}
                        <span className="material-symbols-outlined text-[14px]">
                          open_in_new
                        </span>
                      </a>
                    </div>
                  </div>
                </div>
                {/* Chat Messages Thread Area */}
                <div
                  className="flex-1 overflow-y-auto p-space-lg flex flex-col gap-space-md bg-surface-bright"
                  id="chat-messages-container"
                >
                  {/* Timeline divider */}
                  <div className="flex items-center justify-center my-space-xs">
                    <span className="px-space-md py-1 rounded-full bg-surface-container text-on-surface-variant font-label-sm text-label-sm shadow-sm">
                      Hôm nay, 17 tháng 10, 2025
                    </span>
                  </div>
                  {/* Vendor Message 1 */}
                  <div className="flex items-end gap-space-sm max-w-[80%] self-start">
                    <div className="w-8 h-8 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center shrink-0 mb-1 shadow-sm">
                      <span className="material-symbols-outlined text-[16px]">
                        scuba_diving
                      </span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <div className="p-space-md rounded-2xl rounded-bl-sm bg-surface-container-lowest text-on-surface shadow-sm">
                        <p className="font-body-md text-body-md">
                          Chào anh An! Đội ngũ huấn luyện viên lặn PADI của Son
                          Tra Marine đã sẵn sàng cho chuyến thám hiểm san hô
                          ngày mai của 2 bạn tại Bãi Bụt.
                        </p>
                      </div>
                      <span className="font-label-sm text-label-sm text-outline px-1">
                        09:15
                      </span>
                    </div>
                  </div>
                  {/* Customer Message 1 */}
                  <div className="flex items-end gap-space-sm max-w-[78%] self-end">
                    <div className="flex flex-col items-end gap-1">
                      <div className="p-space-md rounded-2xl rounded-br-sm bg-primary text-on-primary shadow-sm">
                        <p className="font-body-md text-body-md">
                          Chào bạn! Mình và bạn gái đều mới lặn ngắm san hô lần
                          đầu. Ngày mai bọn mình có cần mang theo chân vịt và
                          kính lặn riêng không, hay bên bạn cung cấp sẵn?
                        </p>
                      </div>
                      <div className="flex items-center gap-1 px-1">
                        <span className="font-label-sm text-label-sm text-outline">
                          09:30
                        </span>
                        <span className="material-symbols-outlined text-primary text-[15px]">
                          done_all
                        </span>
                      </div>
                    </div>
                  </div>
                  {/* Vendor Message 2 (With Image Attachment) */}
                  <div className="flex items-end gap-space-sm max-w-[82%] self-start">
                    <div className="w-8 h-8 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center shrink-0 mb-1 shadow-sm">
                      <span className="material-symbols-outlined text-[16px]">
                        scuba_diving
                      </span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <div className="p-space-md rounded-2xl rounded-bl-sm bg-surface-container-lowest text-on-surface shadow-sm flex flex-col gap-space-sm">
                        <p className="font-body-md text-body-md">
                          Anh hoàn toàn yên tâm nhé, gói tour #DNS-89412 đã bao
                          gồm trọn bộ trang thiết bị chuẩn quốc tế đã khử khuẩn:
                          kính lặn góc rộng kèm van thở khô, áo phao chuyên
                          dụng, đồ bơi giữ nhiệt chống trầy xước và chân vịt bơi
                          lặn.
                        </p>
                        {/* Attachment Image Preview */}
                        <div className="rounded-xl overflow-hidden bg-surface-container-high group relative cursor-pointer">
                          <img
                            className="w-full h-44 object-cover group-hover:scale-105 transition-transform duration-300"
                            data-alt="High quality sanitized snorkeling and scuba diving gear set laid out neatly on a clean teak boat deck against azure ocean water."
                            src="https://lh3.googleusercontent.com/aida-public/AB6AXuBnRISoBM9mVsBNdTOgJwIhopt022gNETQAODePmm-Eoxy0eDnjME6p_NgoJf23gCxC3OO6EUYTE9Vwf0FwjCXR1l5EU6vbSE_CKK1oBq2sXnEsuV95HW5lLSXgaG4wIY-Ru9hjBJK3WzoFrhIzbuE3rxKQ0URJ-AOkSpuE68uPgzMTiqEt-7ZOavrIHG5A-VZVF0NqaPTmhwugXi5_psAHqGXLkPCOi9NFSmBV4LFiqzCoetg3Ep9sYw"
                          />
                          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent flex items-end p-space-sm">
                            <span className="text-white font-label-sm text-label-sm flex items-center gap-1">
                              <span className="material-symbols-outlined text-[16px]">
                                image
                              </span>
                              Bo-thiet-bi-lan-chuan-PADI-2025.jpg (1.4 MB)
                            </span>
                          </div>
                        </div>
                      </div>
                      <span className="font-label-sm text-label-sm text-outline px-1">
                        09:35
                      </span>
                    </div>
                  </div>
                  {/* Vendor Message 3 (Departure Schedule Notice) */}
                  <div className="flex items-end gap-space-sm max-w-[80%] self-start">
                    <div className="w-8 h-8 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center shrink-0 mb-1 shadow-sm">
                      <span className="material-symbols-outlined text-[16px]">
                        scuba_diving
                      </span>
                    </div>
                    <div className="flex flex-col gap-1">
                      <div className="p-space-md rounded-2xl rounded-bl-sm bg-surface-container-lowest text-on-surface shadow-sm">
                        <p className="font-body-md text-body-md">
                          Dạ anh An, cano cao tốc số hiệu ST-89 sẽ đón anh lúc{" "}
                          <strong>07:30 sáng mai</strong> tại Cầu Cảng Du Lịch
                          Sông Hàn (đối diện KS Novotel) hoặc đón trực tiếp tại
                          sảnh khách sạn của anh ở đường Võ Nguyên Giáp. Anh cho
                          em xin địa chỉ khách sạn để xe trung chuyển đón nhé!
                        </p>
                      </div>
                      <span className="font-label-sm text-label-sm text-outline px-1">
                        10:42
                      </span>
                    </div>
                  </div>
                  {/* Customer Message 2 (Replying) */}
                  <div className="flex items-end gap-space-sm max-w-[75%] self-end">
                    <div className="flex flex-col items-end gap-1">
                      <div className="p-space-md rounded-2xl rounded-br-sm bg-primary text-on-primary shadow-sm">
                        <p className="font-body-md text-body-md">
                          Tuyệt vời quá. Bọn mình đang ở Sala Danang Beach
                          Hotel, số 36 Lâm Hoành. Đón bọn mình lúc 07:15 nhé.
                        </p>
                      </div>
                      <div className="flex items-center gap-1 px-1">
                        <span className="font-label-sm text-label-sm text-outline">
                          10:45
                        </span>
                        <span className="material-symbols-outlined text-primary text-[15px]">
                          done_all
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
                {/* Sticky Chat Composer Input Box */}
                <div className="p-space-md bg-surface-container-lowest shadow-[0_-2px_8px_rgba(0,0,0,0.03)] z-10">
                  <form
                    className="flex items-center gap-space-sm"
                    onSubmit={(e) => e.preventDefault()}
                  >
                    {/* Attachment Actions */}
                    <button
                      className="w-11 h-11 rounded-xl flex items-center justify-center text-on-surface-variant hover:text-primary hover:bg-surface-container-low transition-colors shrink-0"
                      title="Đính kèm tệp / ảnh"
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[22px]">
                        attach_file
                      </span>
                    </button>
                    <button
                      className="w-11 h-11 rounded-xl flex items-center justify-center text-on-surface-variant hover:text-primary hover:bg-surface-container-low transition-colors shrink-0 hidden sm:flex"
                      title="Chụp / Tải ảnh biển"
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[22px]">
                        add_photo_alternate
                      </span>
                    </button>
                    {/* Text Input */}
                    <div className="flex-1 relative">
                      <input
                        className="w-full h-12 px-space-md rounded-xl bg-surface-container-low text-on-surface font-body-md text-body-md placeholder:text-outline focus:outline-none focus:bg-surface-container-lowest transition-all"
                        id="chat-input-text"
                        placeholder="Nhập tin nhắn trao đổi với Son Tra Marine Diving..."
                        type="text"
                      />
                    </div>
                    {/* Send Button */}
                    <button
                      className="h-12 px-space-lg rounded-xl bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg flex items-center justify-center gap-space-xs transition-all shadow-sm shrink-0"
                      type="submit"
                    >
                      <span>Gửi</span>
                      <span className="material-symbols-outlined text-[18px]">
                        send
                      </span>
                    </button>
                  </form>
                  <div className="flex items-center justify-between mt-2 px-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span className="flex items-center gap-1 text-[12px] text-tertiary">
                      <span className="material-symbols-outlined text-[14px]">
                        lock
                      </span>
                      Cuộc trò chuyện được bảo mật và giám sát an toàn bởi
                      DANASEA
                    </span>
                    <span className="text-[11px] text-outline hidden sm:inline">
                      Nhấn Enter để gửi tin
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}
          {/* TAB VIEW 2: THÔNG BÁO HỆ THỐNG (SYSTEM NOTIFICATIONS) */}
          {activeTab === "notifications" && (
            <div
              className="w-full flex flex-col gap-space-2xl"
              id="view-notifications"
            >
              <div className="flex flex-col gap-space-md">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-space-sm">
                    <span className="w-2 h-6 rounded-full bg-primary"></span>
                    <h2 className="font-headline-md text-headline-md text-on-surface">
                      Thông báo từ hệ thống & Nhà cung cấp
                    </h2>
                    <span className="px-2.5 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant font-label-sm text-label-sm font-bold">
                      {notifications.length} thông báo
                    </span>
                  </div>
                </div>

                {loadingNotifs && (
                  <div className="p-12 text-center bg-white rounded-2xl border border-slate-200">
                    <span className="material-symbols-outlined text-primary text-[36px] animate-spin">progress_activity</span>
                    <p className="text-xs text-slate-500 mt-2 font-medium">Đang tải thông báo từ hệ thống...</p>
                  </div>
                )}

                {!loadingNotifs && notifications.length === 0 && (
                  <div className="p-12 text-center bg-white rounded-2xl border border-slate-200">
                    <span className="material-symbols-outlined text-slate-400 text-[40px]">notifications_off</span>
                    <p className="text-sm text-slate-600 mt-2 font-semibold">Hiện bạn chưa có thông báo mới.</p>
                  </div>
                )}

                {!loadingNotifs && notifications.length > 0 && (
                  <div className="flex flex-col gap-space-sm">
                    {notifications.map((notif) => {
                      const isProposal = notif.type === "RESCHEDULE_PROPOSAL" || notif.type === "DISRUPTION_DECISION";
                      return (
                        <div
                          key={notif.id}
                          className={`p-space-lg rounded-2xl shadow-sm hover:shadow-md transition-all duration-200 flex flex-col md:flex-row md:items-center justify-between gap-space-lg border ${
                            isProposal
                              ? "bg-amber-50/70 border-amber-300"
                              : "bg-surface-container-lowest border-slate-200"
                          }`}
                        >
                          <div className="flex items-start gap-space-lg">
                            <div
                              className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 shadow-sm ${
                                isProposal
                                  ? "bg-amber-500 text-white"
                                  : "bg-primary text-on-primary"
                              }`}
                            >
                              <span className="material-symbols-outlined text-[26px]">
                                {isProposal ? "edit_calendar" : "notifications"}
                              </span>
                            </div>
                            <div className="flex flex-col gap-space-xs">
                              <div className="flex items-center gap-space-sm flex-wrap">
                                <span
                                  className={`px-2 py-0.5 rounded-md font-label-sm text-label-sm font-bold ${
                                    isProposal
                                      ? "bg-amber-200 text-amber-900"
                                      : "bg-primary-fixed-dim text-on-primary-fixed-variant"
                                  }`}
                                >
                                  {isProposal ? "ĐỀ XUẤT ĐỔI CA" : "THÔNG BÁO"}
                                </span>
                                <span className="font-label-sm text-label-sm text-on-surface-variant font-mono">
                                  {new Date(notif.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} • {new Date(notif.createdAt).toLocaleDateString("vi-VN")}
                                </span>
                              </div>
                              <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                                {notif.title}
                              </h3>
                              <p className="font-body-md text-body-md text-on-surface-variant max-w-3xl">
                                {notif.body}
                              </p>
                            </div>
                          </div>
                          <div className="shrink-0 flex items-center self-start md:self-center">
                            <Link
                              to="/profile/orders"
                              className={`px-space-lg py-space-sm rounded-xl font-label-lg text-label-lg transition-colors flex items-center gap-1 shadow-sm ${
                                isProposal
                                  ? "bg-amber-600 hover:bg-amber-700 text-white"
                                  : "bg-primary hover:bg-primary-container text-on-primary"
                              }`}
                            >
                              <span>Xem đơn & Phản hồi</span>
                              <span className="material-symbols-outlined text-[18px]">
                                arrow_forward
                              </span>
                            </Link>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
