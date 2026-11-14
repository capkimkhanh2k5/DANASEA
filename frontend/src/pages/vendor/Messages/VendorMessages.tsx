import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  MessageSquare, 
  Send, 
  Search, 
  Loader2, 
  Ticket,
  ShieldCheck,
  ThumbsUp,
  Image as ImageIcon,
  Smile,
  Check,
} from 'lucide-react';
import { useAuthStore } from '../../../store/useAuthStore';
import { communicationService, type ConversationItem, type MessageItem } from '../../../services/communicationService';
import { vendorOrderService } from '../../../services/vendorOrderService';
import { getAuthoritativeCustomer } from '../../../data/demoCustomerMap';

export function VendorMessages() {
  const { user } = useAuthStore();
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [customerNames, setCustomerNames] = useState<Record<string, string>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);

  // Load Vendor Conversations & Order metadata to match customer names
  const loadConversations = useCallback(async () => {
    try {
      setLoading(true);
      const [convRes, orderRes] = await Promise.allSettled([
        communicationService.getConversations(0, 50),
        vendorOrderService.getVendorOrders(0, 50),
      ]);

      const items = convRes.status === 'fulfilled' ? convRes.value.content || [] : [];
      setConversations(items);

      // Build customer name map from orders if available
      const nameMap: Record<string, string> = {};
      if (orderRes.status === 'fulfilled' && orderRes.value.content) {
        orderRes.value.content.forEach((ord) => {
          if (ord.customerName) {
            nameMap[ord.id] = ord.customerName;
          }
        });
      }
      setCustomerNames(nameMap);

      // Select first conversation if none selected
      if (!activeConvId && items.length > 0) {
        setActiveConvId(items[0].id);
      }
    } catch {
      // Graceful error handling
    } finally {
      setLoading(false);
    }
  }, [activeConvId]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  // Load message history
  const loadMessages = useCallback(async (convId: string) => {
    try {
      const msgs = await communicationService.getMessages(convId);
      setMessages(msgs);
    } catch {
      // Error handling
    }
  }, []);

  // Polling for live incoming customer messages every 3.5s
  useEffect(() => {
    if (!activeConvId) return;

    loadMessages(activeConvId);

    const interval = setInterval(() => {
      loadMessages(activeConvId);
    }, 3500);

    return () => clearInterval(interval);
  }, [activeConvId, loadMessages]);

  // Auto-scroll to bottom on message change (only scrolls internal container, never jumps page)
  useEffect(() => {
    if (activeConvId && messages.length > 0 && chatScrollContainerRef.current) {
      chatScrollContainerRef.current.scrollTop = chatScrollContainerRef.current.scrollHeight;
    }
  }, [activeConvId, messages.length]);

  const activeConversation = conversations.find((c) => c.id === activeConvId);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputMessage.trim() || !activeConvId || sending) return;

    const textToSend = inputMessage.trim();
    setInputMessage('');
    setSending(true);

    try {
      const saved = await communicationService.sendMessage(activeConvId, textToSend);
      setMessages((prev) => [...prev, saved]);
      // Update snippet in conversation list
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

  const handleSendLike = async () => {
    if (!activeConvId || sending) return;
    setSending(true);
    try {
      const saved = await communicationService.sendMessage(activeConvId, '👍');
      setMessages((prev) => [...prev, saved]);
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? { ...c, lastMessage: saved, updatedAt: saved.createdAt }
            : c
        )
      );
    } catch {
      // ignore
    } finally {
      setSending(false);
    }
  };

  const formatMessageTime = (isoString?: string) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  };

  const getCustomerLabel = (conv: ConversationItem) => {
    if (customerNames[conv.masterOrderId || '']) {
      return customerNames[conv.masterOrderId || ''];
    }
    const authCust = getAuthoritativeCustomer(conv.masterOrderId || '') || getAuthoritativeCustomer(conv.customerId);
    if (authCust) {
      return authCust.name;
    }
    return `Khách hàng #${conv.customerId.slice(0, 8)}`;
  };

  const filteredConversations = conversations.filter((c) => {
    const label = getCustomerLabel(c).toLowerCase();
    const orderRef = (c.masterOrderId || '').toLowerCase();
    const q = searchQuery.toLowerCase();
    return label.includes(q) || orderRef.includes(q);
  });

  const vendorQuickReplies = [
    'Đã xác nhận lịch khởi hành của quý khách!',
    'Quý khách vui lòng có mặt trước 15 phút tại bến để hướng dẫn viên đón.',
    'Bên mình đã trang bị sẵn áo phao chuẩn SOLAS và kính lặn cho bạn.',
  ];

  return (
    <div className="w-full max-w-[1440px] mx-auto px-6 py-5 flex-1 flex flex-col space-y-4">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 shrink-0">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
            Hội thoại khách hàng
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Trao đổi trực tiếp và hướng dẫn lịch trình cho du khách đặt tour
          </p>
        </div>
      </div>

      {/* Main 2-Column Chat Matrix */}
      <div className="bg-white rounded-2xl border border-slate-200/90 shadow-xs grid grid-cols-12 overflow-hidden h-[calc(100vh-175px)] min-h-[520px] max-h-[820px] flex-1">
        {/* Left Column: Conversations List */}
        <div className="col-span-12 md:col-span-4 border-r border-slate-200 flex flex-col bg-white">
          {/* Search box */}
          <div className="p-3.5 border-b border-slate-100 bg-white flex flex-col gap-2">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm kiếm khách hàng hoặc mã đơn..."
                className="w-full bg-[#f0f2f5] border-0 rounded-full pl-9 pr-4 py-2 text-xs text-slate-900 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-sky-500 transition-all"
              />
            </div>
            <div className="flex items-center justify-between text-[11px] text-slate-500 font-semibold px-1">
              <span>HỘI THOẠI VỚI DU KHÁCH</span>
              <span className="font-mono tabular-nums text-slate-400">
                {filteredConversations.length} đoạn chat
              </span>
            </div>
          </div>

          {/* List items */}
          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {loading && conversations.length === 0 ? (
              <div className="p-3 space-y-2">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl border border-slate-100 animate-pulse">
                    <div className="w-12 h-12 rounded-full bg-slate-200 shrink-0" />
                    <div className="flex-1 space-y-1.5 pt-1">
                      <div className="h-3.5 bg-slate-200 rounded w-2/3" />
                      <div className="h-2.5 bg-slate-100 rounded w-4/5" />
                    </div>
                  </div>
                ))}
              </div>
            ) : filteredConversations.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-8 text-center min-h-[300px]">
                <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
                  <MessageSquare className="w-6 h-6" />
                </div>
                <p className="text-xs font-semibold text-slate-700">Chưa có hội thoại nào</p>
                <p className="text-[11px] text-slate-400 mt-1 max-w-[200px]">
                  Tin nhắn từ khách hàng đặt dịch vụ sẽ hiển thị tại đây.
                </p>
              </div>
            ) : (
              filteredConversations.map((conv) => {
                const isActive = conv.id === activeConvId;
                const customerLabel = getCustomerLabel(conv);
                const lastSnippet = conv.lastMessage?.content || 'Chưa có tin nhắn';
                const timeStr = formatMessageTime(conv.lastMessage?.createdAt || conv.updatedAt);

                return (
                  <div
                    key={conv.id}
                    onClick={() => {
                      setActiveConvId(conv.id);
                      loadMessages(conv.id);
                      setConversations((prev) =>
                        prev.map((c) => (c.id === conv.id ? { ...c, unreadCount: 0 } : c))
                      );
                    }}
                    className={`p-2.5 rounded-xl cursor-pointer transition-colors flex items-center gap-3 ${
                      isActive
                        ? 'bg-[#eef2f6]'
                        : 'hover:bg-slate-50'
                    }`}
                  >
                    <div className="relative shrink-0">
                      <div className="w-12 h-12 rounded-full bg-sky-50 border border-sky-200 text-sky-800 flex items-center justify-center font-bold text-sm">
                        {customerLabel.slice(0, 2).toUpperCase()}
                      </div>
                      <span className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-emerald-500 border-2 border-white rounded-full" />
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-0.5">
                        <span className="text-xs font-bold text-slate-900 truncate">
                          {customerLabel}
                        </span>
                        <span className="text-[11px] font-mono tabular-nums text-slate-400 shrink-0 ml-1">
                          {timeStr}
                        </span>
                      </div>

                      {conv.masterOrderId && (
                        <div className="flex items-center gap-1 text-[10px] text-sky-700 font-mono mb-0.5">
                          <Ticket className="w-3 h-3" />
                          <span>Đơn #{conv.masterOrderId.slice(0, 8)}</span>
                        </div>
                      )}

                      <p className={`text-xs truncate ${conv.unreadCount && conv.unreadCount > 0 ? 'font-bold text-slate-900' : 'text-slate-500'}`}>
                        {lastSnippet}
                      </p>
                    </div>

                    {conv.unreadCount && conv.unreadCount > 0 ? (
                      <span className="w-5 h-5 rounded-full bg-[#0084ff] text-white text-[10px] font-bold flex items-center justify-center shrink-0">
                        {conv.unreadCount}
                      </span>
                    ) : null}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Active Conversation Stream */}
        <div className="col-span-12 md:col-span-8 flex flex-col bg-white">
          {!activeConversation ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-400">
              <MessageSquare className="w-12 h-12 stroke-[1.5] text-slate-300 mb-2" />
              <p className="text-sm font-semibold text-slate-700">Chọn một hội thoại để xem trao đổi</p>
              <p className="text-xs text-slate-400 mt-1">Danh sách hội thoại với du khách nằm ở cột bên trái</p>
            </div>
          ) : (
            <>
              {/* Chat Thread Header */}
              <div className="px-5 py-3 border-b border-slate-100 flex items-center justify-between bg-white shrink-0">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="relative shrink-0">
                    <div className="w-10 h-10 rounded-full bg-sky-50 border border-sky-200 text-sky-800 flex items-center justify-center font-bold text-sm">
                      {getCustomerLabel(activeConversation).slice(0, 2).toUpperCase()}
                    </div>
                    <span className="absolute bottom-0 right-0 w-3 h-3 bg-emerald-500 border-2 border-white rounded-full" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      {getCustomerLabel(activeConversation)}
                    </h3>
                    <div className="flex items-center gap-2 mt-0.5 text-xs text-slate-500">
                      {activeConversation.masterOrderId && (
                        <span className="font-mono text-[11px] text-sky-700">
                          Đơn #{activeConversation.masterOrderId.slice(0, 8)}
                        </span>
                      )}
                      <span className="text-emerald-600 flex items-center gap-1 text-[11px] font-medium">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                        Đang hoạt động
                      </span>
                    </div>
                  </div>
                </div>

                <div className="hidden sm:flex items-center gap-1.5 text-xs text-slate-500 bg-slate-50 px-2.5 py-1.5 rounded-full border border-slate-200">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                  <span>DANASEA Verified</span>
                </div>
              </div>

              {/* Message List */}
              <div ref={chatScrollContainerRef} className="flex-1 overflow-y-auto p-4 sm:p-5 flex flex-col gap-2.5 bg-white">
                {messages.length === 0 ? (
                  <div className="p-8 text-center text-xs text-slate-400 flex flex-col items-center justify-center my-auto">
                    <div className="w-16 h-16 rounded-full bg-sky-50 text-[#0084ff] flex items-center justify-center mb-3">
                      <MessageSquare className="w-8 h-8" />
                    </div>
                    <p className="font-semibold text-slate-700 text-sm">Chưa có tin nhắn nào</p>
                    <p className="text-slate-400 text-xs mt-1">Gửi lời chào và thông tin hướng dẫn tour cho du khách!</p>
                  </div>
                ) : (
                  messages.map((m) => {
                    const isVendor = m.senderId === user?.id;
                    const isLikeEmoji = m.content === '👍';

                    return (
                      <div
                        key={m.id}
                        className={`flex items-end gap-2 max-w-[75%] ${
                          isVendor ? 'self-end flex-row-reverse' : 'self-start'
                        }`}
                      >
                        {!isVendor && (
                          <div className="w-7 h-7 rounded-full bg-slate-200 border border-slate-300 text-slate-700 flex items-center justify-center text-[10px] font-bold shrink-0 mb-1">
                            {getCustomerLabel(activeConversation).slice(0, 1).toUpperCase()}
                          </div>
                        )}

                        <div className={`flex flex-col gap-0.5 ${isVendor ? 'items-end' : 'items-start'}`}>
                          {isLikeEmoji ? (
                            <span className="text-3xl py-1 px-2 select-none" role="img" aria-label="Like">
                              👍
                            </span>
                          ) : (
                            <div
                              className={`px-4 py-2.5 rounded-2xl text-xs sm:text-sm leading-relaxed shadow-2xs ${
                                isVendor
                                  ? 'bg-[#0084ff] text-white rounded-br-xs font-normal'
                                  : 'bg-[#f0f2f5] text-slate-900 rounded-bl-xs'
                              }`}
                            >
                              <p className="whitespace-pre-wrap break-words">{m.content}</p>
                            </div>
                          )}
                          <div className="flex items-center gap-1 text-[10px] text-slate-400 px-1 font-mono">
                            <span>{formatMessageTime(m.createdAt)}</span>
                            {isVendor && <Check className="w-3 h-3 text-[#0084ff]" />}
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Quick Vendor Reply Chips */}
              <div className="px-4 py-2 bg-slate-50/80 border-t border-slate-100 flex items-center gap-2 overflow-x-auto no-scrollbar shrink-0">
                <span className="text-[11px] font-semibold text-slate-500 shrink-0">Mẫu trả lời:</span>
                {vendorQuickReplies.map((chip, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setInputMessage(chip)}
                    className="px-3 py-1 rounded-full bg-white border border-slate-200 hover:bg-slate-100 text-[11px] text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0 shadow-2xs"
                  >
                    {chip}
                  </button>
                ))}
              </div>

              {/* Input Form */}
              <form
                onSubmit={handleSendMessage}
                className="p-3 bg-white border-t border-slate-100 flex items-center gap-2 shrink-0"
              >
                <button
                  type="button"
                  className="w-9 h-9 rounded-full flex items-center justify-center text-[#0084ff] hover:bg-slate-100 transition-colors shrink-0 cursor-pointer"
                  title="Gửi hình ảnh"
                >
                  <ImageIcon className="w-5 h-5" />
                </button>

                <div className="flex-1 relative flex items-center">
                  <input
                    type="text"
                    value={inputMessage}
                    onChange={(e) => setInputMessage(e.target.value)}
                    placeholder="Nhập tin nhắn phản hồi cho khách..."
                    disabled={sending}
                    className="w-full bg-[#f0f2f5] border-0 rounded-full pl-4 pr-10 h-10 text-xs sm:text-sm text-slate-900 placeholder:text-slate-500 focus:outline-none focus:bg-[#e4e6eb] transition-all"
                  />
                  <button
                    type="button"
                    className="absolute right-3 text-slate-400 hover:text-slate-600 cursor-pointer transition-colors"
                    title="Biểu tượng cảm xúc"
                  >
                    <Smile className="w-5 h-5" />
                  </button>
                </div>

                {inputMessage.trim() ? (
                  <button
                    type="submit"
                    disabled={sending}
                    title="Gửi phản hồi"
                    aria-label="Gửi phản hồi"
                    className="w-10 h-10 rounded-full bg-[#0084ff] hover:bg-[#0073e6] text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
                  >
                    {sending ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Send className="w-4 h-4" />
                    )}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleSendLike}
                    disabled={sending}
                    className="w-10 h-10 rounded-full text-[#0084ff] hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer shrink-0"
                    title="Thích"
                  >
                    <ThumbsUp className="w-5 h-5 fill-[#0084ff]" />
                  </button>
                )}
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default VendorMessages;
