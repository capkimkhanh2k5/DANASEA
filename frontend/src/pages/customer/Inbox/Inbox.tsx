import React, { useState, useEffect, useRef, useCallback } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { notificationService } from "../../../services/notificationService";
import type { NotificationResponse } from "../../../types/notification";
import { communicationService, type ConversationItem, type MessageItem } from "../../../services/communicationService";
import { useAuthStore } from "../../../store/useAuthStore";
import {
  MessageSquare,
  Bell,
  Search,
  Send,
  ShieldCheck,
  Phone,
  ArrowRight,
  ArrowLeft,
  Loader2,
  BellOff,
  CalendarCheck,
  Store,
  User,
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

  const { user, isAuthenticated } = useAuthStore();
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [vendorNames, setVendorNames] = useState<Record<string, string>>({});
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputMessage, setInputMessage] = useState("");
  const [loadingConv, setLoadingConv] = useState(false);
  const [sending, setSending] = useState(false);
  const [searchFilter, setSearchFilter] = useState("");
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loadingNotifs, setLoadingNotifs] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);

  // Load notifications
  useEffect(() => {
    let ignore = false;
    const run = async () => {
      if (activeTab === "notifications" && isAuthenticated) {
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
  }, [activeTab, isAuthenticated]);

  // Load conversations
  const loadConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      setLoadingConv(true);
      const res = await communicationService.getConversations(0, 50);
      const items = res.content || [];
      setConversations(items);

      if (!activeConvId && items.length > 0) {
        setActiveConvId(items[0].id);
      }

      // Fetch vendor names in parallel
      const namesMap: Record<string, string> = {};
      await Promise.all(
        items.map(async (c) => {
          if (!namesMap[c.vendorId]) {
            const info = await communicationService.getVendorInfo(c.vendorId);
            namesMap[c.vendorId] = info.businessName;
          }
        })
      );
      setVendorNames((prev) => ({ ...prev, ...namesMap }));
    } catch {
      // Graceful error handling
    } finally {
      setLoadingConv(false);
    }
  }, [isAuthenticated, activeConvId]);

  useEffect(() => {
    if (activeTab === "messages") {
      loadConversations();
    }
  }, [activeTab, loadConversations]);

  // Load messages for active conversation
  const loadMessages = useCallback(async (convId: string) => {
    try {
      const msgs = await communicationService.getMessages(convId);
      setMessages(msgs);
    } catch {
      // ignore
    }
  }, []);

  // Polling for live messages while chat is open
  useEffect(() => {
    if (!activeConvId || activeTab !== "messages") return;

    loadMessages(activeConvId);

    const interval = setInterval(() => {
      loadMessages(activeConvId);
    }, 3500);

    return () => clearInterval(interval);
  }, [activeConvId, activeTab, loadMessages]);

  // Auto-scroll on new messages (internal container only)
  useEffect(() => {
    if (activeConvId && messages.length > 0 && chatScrollContainerRef.current) {
      chatScrollContainerRef.current.scrollTop = chatScrollContainerRef.current.scrollHeight;
    }
  }, [activeConvId, messages.length]);

  const activeConversation = conversations.find((c) => c.id === activeConvId);
  const activeVendorName = activeConversation
    ? vendorNames[activeConversation.vendorId] || "Nhà điều hành tour"
    : "Hội thoại";

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputMessage.trim() || !activeConvId || sending) return;

    const textToSend = inputMessage.trim();
    setInputMessage("");
    setSending(true);

    try {
      const saved = await communicationService.sendMessage(activeConvId, textToSend);
      setMessages((prev) => [...prev, saved]);
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? { ...c, lastMessage: saved, updatedAt: saved.createdAt }
            : c
        )
      );
    } catch {
      setInputMessage(textToSend);
    } finally {
      setSending(false);
    }
  };

  const formatMessageTime = (isoString?: string) => {
    if (!isoString) return "";
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return "";
    return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
  };

  const filteredConversations = conversations.filter((c) => {
    const name = (vendorNames[c.vendorId] || "").toLowerCase();
    const orderRef = (c.masterOrderId || "").toLowerCase();
    const q = searchFilter.toLowerCase();
    return name.includes(q) || orderRef.includes(q);
  });

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1">
      <div className="flex flex-col w-full">
        <div className="w-full max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col gap-6">
          {/* Navigation Breadcrumb */}
          <div className="flex items-center gap-2 text-[13px] text-slate-500 font-medium">
            <button 
              onClick={() => navigate("/profile")} 
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
                Quản lý trao đổi trực tiếp với nhà điều hành biển và nhận thông báo từ hệ thống
              </p>
            </div>

            {/* Segmented Tab Control */}
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
                {conversations.length > 0 && (
                  <span className="ml-1 px-1.5 py-0.5 rounded-full bg-slate-200 text-slate-700 text-[11px] font-bold">
                    {conversations.length}
                  </span>
                )}
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

          {/* TAB VIEW 1: MESSAGES */}
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
                      value={searchFilter}
                      onChange={(e) => setSearchFilter(e.target.value)}
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-500 font-semibold px-1">
                    <span>ĐỐI TÁC VẬN HÀNH BIỂN</span>
                    <span className="font-mono tabular-nums text-slate-400">
                      {filteredConversations.length} hội thoại
                    </span>
                  </div>
                </div>

                {/* Conversation Items */}
                <div className="flex flex-col overflow-y-auto p-2 space-y-1 flex-1">
                  {loadingConv && conversations.length === 0 ? (
                    <div className="p-3 space-y-2">
                      {[1, 2, 3].map((i) => (
                        <div key={i} className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl border border-slate-100 animate-pulse">
                          <div className="w-10 h-10 rounded-xl bg-slate-200 shrink-0" />
                          <div className="flex-1 space-y-1.5">
                            <div className="h-3 bg-slate-200 rounded w-2/3" />
                            <div className="h-2.5 bg-slate-100 rounded w-1/2" />
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : filteredConversations.length === 0 ? (
                    <div className="flex flex-col items-center justify-center p-8 text-center min-h-[280px]">
                      <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
                        <Store className="w-5 h-5" />
                      </div>
                      <p className="text-xs font-semibold text-slate-700">Chưa có hội thoại nào</p>
                      <p className="text-[11px] text-slate-400 mt-1 max-w-[200px]">
                        Khi bạn đặt dịch vụ tour biển, kênh trao đổi với nhà điều hành sẽ hiển thị tại đây.
                      </p>
                    </div>
                  ) : (
                    filteredConversations.map((conv) => {
                      const isActive = conv.id === activeConvId;
                      const vendorName = vendorNames[conv.vendorId] || "Nhà điều hành tour";
                      const lastSnippet = conv.lastMessage?.content || "Chưa có tin nhắn";
                      const timeStr = formatMessageTime(conv.lastMessage?.createdAt || conv.updatedAt);

                      return (
                        <div
                          key={conv.id}
                          className={`flex items-start gap-3 p-3 rounded-xl cursor-pointer transition-colors border ${
                            isActive
                              ? "bg-slate-50 border-slate-200/90 shadow-2xs"
                              : "border-transparent hover:bg-slate-50/70"
                          }`}
                          onClick={() => setActiveConvId(conv.id)}
                        >
                          <div className="w-10 h-10 rounded-xl bg-sky-100 border border-sky-200 text-sky-800 flex items-center justify-center shrink-0">
                            <Store className="w-5 h-5" />
                          </div>

                          <div className="flex-1 min-w-0">
                            <div className="flex items-baseline justify-between gap-1 mb-0.5">
                              <span className="font-bold text-xs sm:text-sm text-slate-900 truncate">
                                {vendorName}
                              </span>
                              <span className="text-[11px] font-mono tabular-nums text-slate-400 shrink-0">
                                {timeStr}
                              </span>
                            </div>
                            {conv.masterOrderId && (
                              <p className="text-[11px] font-mono text-sky-700 truncate">
                                Đơn #{conv.masterOrderId.slice(0, 8)}
                              </p>
                            )}
                            <p className="text-xs text-slate-600 truncate mt-0.5">
                              {lastSnippet}
                            </p>
                          </div>

                          {conv.unreadCount && conv.unreadCount > 0 ? (
                            <span className="w-5 h-5 rounded-full bg-rose-600 text-white text-[10px] font-bold flex items-center justify-center shrink-0 mt-1">
                              {conv.unreadCount}
                            </span>
                          ) : null}
                        </div>
                      );
                    })
                  )}
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
                {!activeConversation ? (
                  <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-400">
                    <MessageSquare className="w-12 h-12 stroke-[1.5] text-slate-300 mb-2" />
                    <p className="text-sm font-semibold text-slate-700">Chọn một hội thoại để trao đổi</p>
                    <p className="text-xs text-slate-400 mt-1">Danh sách đối tác biển nằm ở cột bên trái</p>
                  </div>
                ) : (
                  <>
                    {/* Chat Top Header */}
                    <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between gap-4 z-10">
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-10 h-10 rounded-xl bg-[#031d44] text-white flex items-center justify-center shrink-0 font-bold text-sm">
                          <Store className="w-5 h-5" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <h2 className="font-bold text-sm sm:text-base text-slate-900 truncate">
                              {activeVendorName}
                            </h2>
                            <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[10px] font-bold border border-emerald-200">
                              Đối tác xác minh
                            </span>
                          </div>
                          {activeConversation.masterOrderId && (
                            <p className="text-xs text-slate-500 flex items-center gap-1 mt-0.5 font-mono">
                              <span>Mã đơn: #{activeConversation.masterOrderId.slice(0, 8)}</span>
                            </p>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Chat Messages Log */}
                    <div ref={chatScrollContainerRef} className="flex-1 overflow-y-auto p-5 flex flex-col gap-3.5 bg-slate-50/40">
                      {messages.length === 0 ? (
                        <div className="p-8 text-center text-xs text-slate-400">
                          Chưa có tin nhắn nào trong hội thoại này. Gửi tin nhắn đầu tiên cho {activeVendorName}!
                        </div>
                      ) : (
                        messages.map((m) => {
                          const isUser = m.senderId === user?.id;

                          return (
                            <div
                              key={m.id}
                              className={`flex items-end gap-2.5 max-w-[80%] ${
                                isUser ? "self-end flex-row-reverse" : "self-start"
                              }`}
                            >
                              <div
                                className={`w-7 h-7 rounded-lg text-white flex items-center justify-center font-bold text-xs shrink-0 mb-1 ${
                                  isUser ? "bg-slate-700" : "bg-[#031d44]"
                                }`}
                              >
                                {isUser ? <User className="w-4 h-4" /> : <Store className="w-4 h-4" />}
                              </div>

                              <div className={`flex flex-col gap-1 ${isUser ? "items-end" : "items-start"}`}>
                                <div
                                  className={`p-3.5 rounded-2xl text-xs sm:text-sm shadow-2xs leading-relaxed ${
                                    isUser
                                      ? "bg-[#031d44] text-white rounded-br-xs font-normal"
                                      : "bg-white border border-slate-200 text-slate-800 rounded-bl-xs"
                                  }`}
                                >
                                  <p>{m.content}</p>
                                </div>
                                <span className="text-[11px] font-mono tabular-nums text-slate-400 px-1">
                                  {formatMessageTime(m.createdAt)}
                                </span>
                              </div>
                            </div>
                          );
                        })
                      )}
                      <div ref={messagesEndRef} />
                    </div>

                    {/* Chat Composer */}
                    <div className="p-4 bg-white border-t border-slate-100">
                      <form className="flex items-center gap-2" onSubmit={handleSendMessage}>
                        <input
                          className="flex-1 h-10 px-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs sm:text-sm text-slate-900 outline-none focus:border-[#031d44] focus:bg-white transition-colors"
                          placeholder="Nhập tin nhắn trao đổi với đối tác..."
                          type="text"
                          value={inputMessage}
                          onChange={(e) => setInputMessage(e.target.value)}
                          disabled={sending}
                        />

                        <button
                          className="h-10 px-4 rounded-xl bg-[#031d44] hover:bg-[#021838] text-white font-semibold text-xs sm:text-sm flex items-center justify-center gap-1.5 transition-colors cursor-pointer shrink-0 shadow-xs disabled:opacity-40"
                          type="submit"
                          disabled={!inputMessage.trim() || sending}
                        >
                          {sending ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                          ) : (
                            <>
                              <span>Gửi</span>
                              <Send className="w-3.5 h-3.5" />
                            </>
                          )}
                        </button>
                      </form>
                      <div className="flex items-center justify-between mt-2 text-[11px] text-slate-500 px-1">
                        <span className="flex items-center gap-1">
                          <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                          Hệ thống tin nhắn lưu trữ và đồng bộ thời gian thực
                        </span>
                        <span className="hidden sm:inline">Nhấn Enter để gửi</span>
                      </div>
                    </div>
                  </>
                )}
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
