import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  MessageSquare, 
  Send, 
  Search, 
  Loader2, 
  Ticket,
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

  return (
    <div className="flex-1 flex h-full min-h-0 bg-white">
      {/* Left Column: Conversations List */}
      <div className="w-80 lg:w-96 border-r border-slate-200/80 flex flex-col shrink-0 h-full min-h-0 bg-slate-50/50">
        {/* Search header - Fixed at top */}
        <div className="h-14 px-4 border-b border-slate-200/80 flex items-center shrink-0 bg-white">
          <div className="relative w-full">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm kiếm khách hàng, mã đơn..."
              className="w-full bg-slate-100 hover:bg-slate-100/80 focus:bg-white border border-transparent focus:border-sky-500 rounded-xl pl-9 pr-3 py-1.5 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none transition-all"
            />
          </div>
        </div>

        {/* List items - Scrollable */}
        <div className="flex-1 overflow-y-auto min-h-0 p-2 space-y-1">
          {loading && conversations.length === 0 ? (
            <div className="p-3 space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="flex items-start gap-3 p-3 bg-white rounded-xl border border-slate-100 animate-pulse">
                  <div className="w-10 h-10 rounded-xl bg-slate-200 shrink-0" />
                  <div className="flex-1 space-y-1.5 pt-1">
                    <div className="h-3.5 bg-slate-200 rounded w-2/3" />
                    <div className="h-2.5 bg-slate-100 rounded w-4/5" />
                  </div>
                </div>
              ))}
            </div>
          ) : filteredConversations.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center min-h-[300px] select-none">
              <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
                <MessageSquare className="w-6 h-6 stroke-[1.5]" />
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
                  className={`p-3 rounded-xl cursor-pointer transition-all flex items-center gap-3 border ${
                    isActive
                      ? 'bg-white border-slate-200 shadow-2xs'
                      : 'border-transparent hover:bg-white hover:border-slate-100'
                  }`}
                >
                  <div className="w-10 h-10 rounded-xl bg-sky-50 border border-sky-100 text-sky-800 flex items-center justify-center font-bold text-xs shrink-0">
                    {customerLabel.slice(0, 2).toUpperCase()}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5">
                      <span className="text-xs font-bold text-slate-900 truncate">
                        {customerLabel}
                      </span>
                      <span className="text-[10px] font-mono tabular-nums text-slate-400 shrink-0 ml-1">
                        {timeStr}
                      </span>
                    </div>

                    {conv.masterOrderId && (
                      <div className="flex items-center gap-1 text-[10px] text-sky-700 font-mono mb-0.5">
                        <Ticket className="w-3 h-3 shrink-0" />
                        <span className="truncate">#{conv.masterOrderId.slice(0, 8)}</span>
                      </div>
                    )}

                    <p className={`text-xs truncate ${conv.unreadCount && conv.unreadCount > 0 ? 'font-bold text-slate-900' : 'text-slate-500'}`}>
                      {lastSnippet}
                    </p>
                  </div>

                  {conv.unreadCount && conv.unreadCount > 0 ? (
                    <span className="w-5 h-5 rounded-full bg-sky-600 text-white text-[10px] font-bold flex items-center justify-center shrink-0">
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
      <div className="flex-1 flex flex-col bg-white h-full min-h-0 min-w-0 overflow-hidden">
        {!activeConversation ? (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-400 select-none">
            <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
              <MessageSquare className="w-6 h-6 stroke-[1.5]" />
            </div>
            <p className="text-sm font-semibold text-slate-700">Chọn một hội thoại để xem trao đổi</p>
            <p className="text-xs text-slate-400 mt-1">Danh sách hội thoại với du khách nằm ở cột bên trái</p>
          </div>
        ) : (
          <>
            {/* Chat Thread Header - FROZEN STICKY TOP */}
            <div className="h-14 px-6 border-b border-slate-200/80 flex items-center justify-between shrink-0 bg-white sticky top-0 z-10">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-9 h-9 rounded-xl bg-sky-50 border border-sky-200 text-sky-800 flex items-center justify-center font-bold text-xs shrink-0">
                  {getCustomerLabel(activeConversation).slice(0, 2).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-slate-900 truncate">
                      {getCustomerLabel(activeConversation)}
                    </h3>
                    {activeConversation.masterOrderId && (
                      <span className="font-mono text-[10px] text-sky-700 bg-sky-50 border border-sky-200/80 px-1.5 py-0.5 rounded-md font-semibold shrink-0">
                        #{activeConversation.masterOrderId.slice(0, 8)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Message Thread - ONLY THIS CONTAINER SCROLLS */}
            <div 
              ref={chatScrollContainerRef} 
              className="flex-1 overflow-y-auto min-h-0 w-full overscroll-contain p-6 flex flex-col gap-3 bg-white"
            >
              {messages.length === 0 ? (
                <div className="my-auto flex flex-col items-center justify-center py-12 text-center select-none">
                  <div className="w-12 h-12 rounded-2xl bg-sky-50 border border-sky-100 flex items-center justify-center text-sky-600 mb-3 shadow-2xs">
                    <MessageSquare className="w-6 h-6" strokeWidth={1.75} />
                  </div>
                  <h3 className="font-bold text-base text-slate-800 tracking-tight">
                    {getCustomerLabel(activeConversation)}
                  </h3>
                  <p className="text-xs text-slate-400 mt-1 max-w-sm leading-relaxed">
                    Chưa có trao đổi trong phiên này. Nhập tin nhắn bên dưới để hướng dẫn và hỗ trợ du khách.
                  </p>
                </div>
              ) : (
                messages.map((m) => {
                  const isVendor = m.senderId === user?.id;
                  const isLikeEmoji = m.content === '👍';

                  return (
                    <div
                      key={m.id}
                      className={`flex flex-col ${isVendor ? 'items-end' : 'items-start'}`}
                    >
                      {isLikeEmoji ? (
                        <span className="text-3xl py-1 select-none" role="img" aria-label="Like">
                          👍
                        </span>
                      ) : (
                        <div
                          className={`max-w-[75%] px-4 py-2.5 rounded-2xl text-xs sm:text-sm leading-relaxed shadow-2xs ${
                            isVendor
                              ? 'bg-slate-900 text-white rounded-tr-xs font-normal'
                              : 'bg-slate-100/90 text-slate-900 rounded-tl-xs'
                          }`}
                        >
                          <p className="whitespace-pre-wrap break-words">{m.content}</p>
                        </div>
                      )}
                      <div className="flex items-center gap-1 text-[10px] text-slate-400 mt-1 px-1 font-mono tabular-nums">
                        <span>{formatMessageTime(m.createdAt)}</span>
                        {isVendor && <Check className="w-3 h-3 text-slate-500" />}
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Bottom Input Area - FROZEN STICKY BOTTOM */}
            <div className="py-3 px-6 border-t border-slate-200/80 bg-white shrink-0 w-full sticky bottom-0 z-10">
              <form onSubmit={handleSendMessage} className="flex items-center gap-2.5 w-full">
                <button
                  type="button"
                  className="p-2 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors shrink-0 cursor-pointer"
                  title="Gửi hình ảnh"
                >
                  <ImageIcon className="w-4 h-4" />
                </button>

                <div className="flex-1 relative flex items-center">
                  <input
                    type="text"
                    value={inputMessage}
                    onChange={(e) => setInputMessage(e.target.value)}
                    placeholder="Nhập tin nhắn phản hồi cho khách..."
                    disabled={sending}
                    className="w-full bg-slate-100 hover:bg-slate-100/80 focus:bg-white border border-transparent focus:border-sky-500 rounded-xl px-3.5 h-10 text-xs sm:text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none transition-all"
                  />
                  <button
                    type="button"
                    className="absolute right-3 text-slate-400 hover:text-slate-600 cursor-pointer transition-colors"
                    title="Biểu tượng cảm xúc"
                  >
                    <Smile className="w-4 h-4" />
                  </button>
                </div>

                {inputMessage.trim() ? (
                  <button
                    type="submit"
                    disabled={sending}
                    title="Gửi phản hồi"
                    aria-label="Gửi phản hồi"
                    className="h-10 px-4 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs flex items-center gap-1.5 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-2xs"
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
                ) : (
                  <button
                    type="button"
                    onClick={handleSendLike}
                    disabled={sending}
                    className="w-10 h-10 rounded-xl hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer shrink-0 text-slate-700"
                    title="Thích"
                  >
                    <ThumbsUp className="w-4 h-4" />
                  </button>
                )}
              </form>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default VendorMessages;
