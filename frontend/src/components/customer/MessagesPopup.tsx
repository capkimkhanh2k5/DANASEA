import React, { useState, useRef, useEffect, useCallback } from 'react';
import { MessageSquare, Minus, Send, ArrowLeft, Loader2, Store } from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { communicationService, type ConversationItem, type MessageItem } from '../../services/communicationService';

interface MessagesPopupProps {
  isOpen: boolean;
  onClose: () => void;
}

export const MessagesPopup: React.FC<MessagesPopupProps> = ({ isOpen, onClose }) => {
  const { user, isAuthenticated } = useAuthStore();
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [vendorNames, setVendorNames] = useState<Record<string, string>>({});
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Load conversations when opened and authenticated
  const loadConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      setLoading(true);
      const res = await communicationService.getConversations(0, 50);
      const items = res.content || [];
      setConversations(items);

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
      // Error handling without crashing UI
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isOpen) {
      loadConversations();
    }
  }, [isOpen, loadConversations]);

  // Load messages for active conversation
  const loadMessages = useCallback(async (convId: string) => {
    try {
      const msgs = await communicationService.getMessages(convId);
      setMessages(msgs);
    } catch {
      // Silent error handler
    }
  }, []);

  // Polling for new messages while chat is open
  useEffect(() => {
    if (!activeConvId || !isOpen) return;

    loadMessages(activeConvId);

    const interval = setInterval(() => {
      loadMessages(activeConvId);
    }, 3500);

    return () => clearInterval(interval);
  }, [activeConvId, isOpen, loadMessages]);

  // Auto-scroll on new messages
  useEffect(() => {
    if (activeConvId && messages.length > 0) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [activeConvId, messages.length]);

  if (!isOpen) return null;

  const activeConversation = conversations.find((c) => c.id === activeConvId);
  const activeVendorName = activeConversation
    ? vendorNames[activeConversation.vendorId] || 'Nhà điều hành tour'
    : 'Tin nhắn';

  const handleSelectConversation = (id: string) => {
    setActiveConvId(id);
    loadMessages(id);
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputMessage.trim() || !activeConvId || sending) return;

    const textToSend = inputMessage.trim();
    setInputMessage('');
    setSending(true);

    try {
      const savedMessage = await communicationService.sendMessage(activeConvId, textToSend);
      setMessages((prev) => [...prev, savedMessage]);
      // Update last message preview in conversations list
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? { ...c, lastMessage: savedMessage, updatedAt: savedMessage.createdAt }
            : c
        )
      );
    } catch {
      // Restore input on failure
      setInputMessage(textToSend);
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

  return (
    <div
      role="dialog"
      aria-label="Cửa sổ Tin nhắn hỗ trợ"
      className="fixed bottom-22 right-4 left-4 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[420px] h-[540px] max-h-[calc(100vh-96px)] bg-white rounded-2xl border border-slate-200/90 shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
    >
      {/* Top Header */}
      <div className="p-3.5 bg-[#031d44] text-white flex items-center justify-between border-b border-sky-950 shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          {activeConvId && (
            <button
              type="button"
              onClick={() => setActiveConvId(null)}
              className="w-7 h-7 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-colors cursor-pointer mr-0.5 shrink-0"
              title="Quay lại danh sách"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
            </button>
          )}

          <div className="w-8 h-8 rounded-lg bg-white/10 border border-white/15 flex items-center justify-center text-white shrink-0">
            <MessageSquare className="w-4 h-4 text-white" />
          </div>

          <div className="min-w-0 flex-1">
            <span className="font-semibold text-xs sm:text-sm tracking-tight text-white block truncate">
              {activeVendorName}
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          title="Thu nhỏ"
          aria-label="Thu nhỏ"
          className="w-7 h-7 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-colors cursor-pointer shrink-0 ml-2"
        >
          <Minus className="w-4 h-4" />
        </button>
      </div>

      {/* Body: Not Authenticated State */}
      {!isAuthenticated && (
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center bg-slate-50">
          <div className="w-12 h-12 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-500 mb-3">
            <MessageSquare className="w-6 h-6" />
          </div>
          <h3 className="text-sm font-bold text-slate-800 mb-1">Đăng nhập tài khoản</h3>
          <p className="text-xs text-slate-500 leading-relaxed max-w-[260px] mb-4">
            Vui lòng đăng nhập để trao đổi trực tiếp với các đơn vị điều hành bến bãi và tour biển.
          </p>
        </div>
      )}

      {/* Body: Conversation List */}
      {isAuthenticated && !activeConversation && (
        <div className="flex-1 overflow-y-auto divide-y divide-slate-100 bg-slate-50/50">
          <div className="p-3 bg-white border-b border-slate-100 flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Cuộc trò chuyện của bạn
            </span>
            <span className="text-xs font-mono tabular-nums text-slate-400">
              {conversations.length} hội thoại
            </span>
          </div>

          {loading && conversations.length === 0 ? (
            <div className="p-4 space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="flex items-start gap-3 p-3 bg-white rounded-xl border border-slate-100 animate-pulse">
                  <div className="w-10 h-10 rounded-xl bg-slate-200 shrink-0" />
                  <div className="flex-1 space-y-2">
                    <div className="h-3.5 bg-slate-200 rounded-md w-3/4" />
                    <div className="h-3 bg-slate-100 rounded-md w-1/2" />
                  </div>
                </div>
              ))}
            </div>
          ) : conversations.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center min-h-[300px]">
              <div className="w-12 h-12 rounded-xl bg-white border border-slate-200 flex items-center justify-center text-slate-400 mb-3 shadow-xs">
                <Store className="w-6 h-6" />
              </div>
              <h4 className="text-sm font-bold text-slate-800 mb-1">Chưa có cuộc trò chuyện nào</h4>
              <p className="text-xs text-slate-500 leading-relaxed max-w-[260px]">
                Khi bạn đặt dịch vụ tour biển, kênh trao đổi trực tiếp với nhà cung cấp sẽ tự động hiển thị tại đây.
              </p>
            </div>
          ) : (
            conversations.map((conv) => {
              const vendorName = vendorNames[conv.vendorId] || 'Nhà điều hành tour';
              const lastText = conv.lastMessage?.content || 'Chưa có tin nhắn';
              const timeStr = formatMessageTime(conv.lastMessage?.createdAt || conv.updatedAt);

              return (
                <div
                  key={conv.id}
                  onClick={() => handleSelectConversation(conv.id)}
                  className="p-3.5 hover:bg-white flex items-start gap-3 transition-colors cursor-pointer group bg-white/70"
                >
                  <div className="w-10 h-10 rounded-xl bg-sky-100 border border-sky-200 text-sky-800 font-bold text-xs flex items-center justify-center shrink-0">
                    <Store className="w-5 h-5" />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5">
                      <h4 className="text-xs font-bold text-slate-900 truncate group-hover:text-sky-700 transition-colors">
                        {vendorName}
                      </h4>
                      <span className="text-[11px] font-mono tabular-nums text-slate-400 shrink-0 ml-1">
                        {timeStr}
                      </span>
                    </div>
                    <p className="text-xs text-slate-600 line-clamp-1 leading-snug">
                      {lastText}
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
      )}

      {/* Body: Active Message Thread */}
      {isAuthenticated && activeConversation && (
        <div className="flex-1 flex flex-col min-h-0">
          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50/60">
            {messages.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">
                Hãy gửi tin nhắn đầu tiên để bắt đầu trao đổi với {activeVendorName}.
              </div>
            ) : (
              messages.map((m) => {
                const isUser = m.senderId === user?.id;
                return (
                  <div
                    key={m.id}
                    className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}
                  >
                    <div
                      className={`px-3.5 py-2.5 rounded-xl text-xs leading-relaxed max-w-[85%] ${
                        isUser
                          ? 'bg-[#031d44] text-white rounded-tr-xs font-normal shadow-xs'
                          : 'bg-white border border-slate-200/90 text-slate-800 rounded-tl-xs shadow-xs'
                      }`}
                    >
                      {m.content}
                    </div>
                    <span className="text-[10px] font-mono tabular-nums text-slate-400 mt-1 px-1">
                      {formatMessageTime(m.createdAt)}
                    </span>
                  </div>
                );
              })
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Suggestion Chips */}
          <div className="px-3 py-1.5 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
            {['Tôi sẽ có mặt đúng giờ', 'Vị trí đón ở đâu?', 'Cần mang theo đồ gì?'].map((chip, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setInputMessage(chip)}
                className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-[11px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0"
              >
                {chip}
              </button>
            ))}
          </div>

          {/* Input Deck */}
          <form
            onSubmit={handleSendMessage}
            className="p-3 bg-white border-t border-slate-200/90 flex items-center gap-2 shrink-0"
          >
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              placeholder="Nhập tin nhắn..."
              disabled={sending}
              className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-[#031d44] focus:bg-white transition-all shadow-xs"
            />
            <button
              type="submit"
              disabled={!inputMessage.trim() || sending}
              title="Gửi tin nhắn"
              aria-label="Gửi tin nhắn"
              className="w-9 h-9 rounded-xl bg-[#031d44] hover:bg-[#021838] text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
            >
              {sending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
            </button>
          </form>
        </div>
      )}
    </div>
  );
};

export default MessagesPopup;
