import React, { useState, useRef, useEffect, useCallback } from 'react';
import { 
  Minus, 
  X, 
  Send, 
  ArrowLeft, 
  Loader2, 
  Store, 
  Search, 
  Phone, 
  Video, 
  Image as ImageIcon, 
  Smile, 
  ThumbsUp, 
  CheckCheck 
} from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { useLanguageStore } from '../../store/useLanguageStore';
import { communicationService, type ConversationItem, type MessageItem } from '../../services/communicationService';

interface MessagesPopupProps {
  isOpen: boolean;
  onClose: () => void;
}

export const MessagesPopup: React.FC<MessagesPopupProps> = ({ isOpen, onClose }) => {
  const { user, isAuthenticated } = useAuthStore();
  const { language } = useLanguageStore();
  const isEn = language === 'en';
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [vendorNames, setVendorNames] = useState<Record<string, string>>({});
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [searchFilter, setSearchFilter] = useState('');
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
      // Silent error handler
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
    ? vendorNames[activeConversation.vendorId] || (isEn ? 'Tour Operator' : 'Nhà điều hành bến bãi')
    : (isEn ? 'Chat' : 'Đoạn chat');

  const handleSelectConversation = (id: string) => {
    setActiveConvId(id);
    loadMessages(id);
    setConversations((prev) =>
      prev.map((c) => (c.id === id ? { ...c, unreadCount: 0 } : c))
    );
  };

  const executeSend = async (content: string) => {
    if (!content.trim() || !activeConvId || sending) return;

    const textToSend = content.trim();
    setInputMessage('');
    setSending(true);

    try {
      const savedMessage = await communicationService.sendMessage(activeConvId, textToSend);
      setMessages((prev) => [...prev, savedMessage]);
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? { ...c, lastMessage: savedMessage, updatedAt: savedMessage.createdAt }
            : c
        )
      );
    } catch {
      setInputMessage(textToSend);
    } finally {
      setSending(false);
    }
  };

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    executeSend(inputMessage);
  };

  const handleSendLike = () => {
    executeSend('👍');
  };

  const formatMessageTime = (isoString?: string) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleTimeString(isEn ? 'en-US' : 'vi-VN', { hour: '2-digit', minute: '2-digit' });
  };

  const filteredConversations = conversations.filter((c) => {
    if (!searchFilter.trim()) return true;
    const name = (vendorNames[c.vendorId] || '').toLowerCase();
    const lastMsg = (c.lastMessage?.content || '').toLowerCase();
    const q = searchFilter.toLowerCase();
    return name.includes(q) || lastMsg.includes(q);
  });

  return (
    <div
      role="dialog"
      aria-label="Cửa sổ Tin nhắn Messenger Danasea"
      className="fixed bottom-22 right-4 left-4 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[380px] h-[520px] max-h-[calc(100vh-96px)] bg-white rounded-2xl border border-slate-200/90 shadow-[0_12px_40px_-8px_rgba(0,0,0,0.18)] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200 select-none font-sans"
    >
      {/* ==================== MESSENGER HEADER ==================== */}
      <div className="px-3.5 py-2.5 bg-white text-slate-900 flex items-center justify-between border-b border-slate-100 shadow-2xs shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          {activeConvId && (
            <button
              type="button"
              onClick={() => {
                setActiveConvId(null);
                loadConversations();
              }}
              className="w-7 h-7 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer mr-0.5 shrink-0"
              title={isEn ? "Back" : "Quay lại danh sách"}
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}

          <div className="min-w-0">
            <h3 className="font-semibold text-xs sm:text-[13px] text-slate-900 truncate leading-snug">
              {activeConvId ? activeVendorName : (isEn ? 'Chat' : 'Đoạn chat')}
            </h3>
          </div>
        </div>

        {/* Header Action Buttons */}
        <div className="flex items-center gap-1 shrink-0 ml-2">
          {activeConvId && (
            <>
              <button
                type="button"
                className="w-7 h-7 rounded-lg text-sky-600 hover:bg-sky-50 flex items-center justify-center transition-colors cursor-pointer"
                title={isEn ? "Voice call" : "Gọi thoại"}
              >
                <Phone className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                className="w-7 h-7 rounded-lg text-sky-600 hover:bg-sky-50 flex items-center justify-center transition-colors cursor-pointer"
                title={isEn ? "Video call" : "Gọi video"}
              >
                <Video className="w-3.5 h-3.5" />
              </button>
            </>
          )}
          <button
            type="button"
            onClick={onClose}
            title={isEn ? "Minimize" : "Thu nhỏ"}
            aria-label={isEn ? "Minimize" : "Thu nhỏ"}
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={onClose}
            title={isEn ? "Close" : "Đóng chat"}
            aria-label={isEn ? "Close" : "Đóng chat"}
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* ==================== BODY: NOT AUTHENTICATED ==================== */}
      {!isAuthenticated && (
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center bg-[#f0f2f5]">
          <div className="w-12 h-12 rounded-full bg-white shadow-xs border border-slate-200 flex items-center justify-center text-sky-600 mb-3">
            <Store className="w-6 h-6" />
          </div>
          <h3 className="text-sm font-bold text-slate-800 mb-1">
            {isEn ? 'Sign in to continue' : 'Đăng nhập tài khoản'}
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed max-w-[260px]">
            {isEn
              ? 'Please sign in to message directly with tour operators and receive 24/7 support.'
              : 'Vui lòng đăng nhập để trao đổi trực tiếp với nhà cung cấp tour biển và nhận thông tin hỗ trợ 24/7.'}
          </p>
        </div>
      )}

      {/* ==================== BODY: CONVERSATION LIST (MESSENGER INBOX) ==================== */}
      {isAuthenticated && !activeConversation && (
        <div className="flex-1 overflow-y-auto bg-white flex flex-col">
          {/* Messenger Search Bar */}
          <div className="p-2.5 border-b border-slate-100">
            <div className="relative flex items-center">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 pointer-events-none" />
              <input
                type="text"
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                placeholder={isEn ? 'Search conversations...' : 'Tìm kiếm trên Messenger...'}
                className="w-full pl-8 pr-3 py-1.5 rounded-full bg-[#f0f2f5] text-xs text-slate-900 placeholder:text-slate-500 focus:outline-none focus:bg-white focus:ring-1 focus:ring-[#0084ff] transition-all"
              />
            </div>
          </div>

          {/* Conversations Stream */}
          <div className="flex-1 divide-y divide-slate-100/70 overflow-y-auto">
            {loading && conversations.length === 0 ? (
              <div className="p-3 space-y-2">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="flex items-center gap-3 p-2.5 rounded-xl bg-slate-50/60 animate-pulse">
                    <div className="w-10 h-10 rounded-full bg-slate-200 shrink-0" />
                    <div className="flex-1 space-y-1.5">
                      <div className="h-3 bg-slate-200 rounded w-2/3" />
                      <div className="h-2.5 bg-slate-100 rounded w-1/2" />
                    </div>
                  </div>
                ))}
              </div>
            ) : filteredConversations.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-center min-h-[300px]">
                <div className="w-12 h-12 rounded-full bg-[#f0f2f5] flex items-center justify-center text-slate-400 mb-2">
                  <Store className="w-6 h-6" />
                </div>
                <h4 className="text-xs font-bold text-slate-800 mb-0.5">
                  {isEn ? 'No messages yet' : 'Chưa có tin nhắn nào'}
                </h4>
                <p className="text-[11px] text-slate-500 max-w-[240px] leading-relaxed">
                  {isEn
                    ? 'When you book a tour or ask a question, conversations with operators will appear here.'
                    : 'Khi bạn đặt vé trải nghiệm hoặc gửi câu hỏi, cuộc trò chuyện với Vendor sẽ hiển thị tại đây.'}
                </p>
              </div>
            ) : (
              filteredConversations.map((conv) => {
                const vendorName = vendorNames[conv.vendorId] || (isEn ? 'Tour Operator' : 'Nhà điều hành bến bãi');
                const lastText = conv.lastMessage?.content || (isEn ? 'Start a conversation' : 'Bắt đầu cuộc trò chuyện');
                const timeStr = formatMessageTime(conv.lastMessage?.createdAt || conv.updatedAt);
                const hasUnread = conv.unreadCount && conv.unreadCount > 0;

                return (
                  <div
                    key={conv.id}
                    onClick={() => handleSelectConversation(conv.id)}
                    className="p-2.5 mx-1.5 my-0.5 rounded-xl hover:bg-[#f0f2f5] flex items-center gap-3 transition-colors cursor-pointer group"
                  >
                    {/* Circle Avatar with Active Dot */}
                    <div className="relative shrink-0">
                      <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-sky-500 to-blue-600 text-white font-bold text-xs flex items-center justify-center shadow-xs">
                        {vendorName.charAt(0).toUpperCase()}
                      </div>
                      <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 rounded-full ring-2 ring-white" />
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-0.5">
                        <h4 className={`text-xs truncate ${hasUnread ? 'font-bold text-slate-900' : 'font-semibold text-slate-800'}`}>
                          {vendorName}
                        </h4>
                        <span className="text-[10px] text-slate-400 shrink-0 ml-1">
                          {timeStr}
                        </span>
                      </div>
                      <p className={`text-[11px] truncate leading-tight ${hasUnread ? 'font-bold text-slate-900' : 'text-slate-500'}`}>
                        {lastText}
                      </p>
                    </div>

                    {hasUnread ? (
                      <span className="w-2.5 h-2.5 rounded-full bg-[#0084ff] shrink-0" />
                    ) : null}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* ==================== BODY: ACTIVE MESSENGER THREAD ==================== */}
      {isAuthenticated && activeConversation && (
        <div className="flex-1 flex flex-col min-h-0 bg-white">
          {/* Messages Stream */}
          <div className="flex-1 overflow-y-auto p-3.5 space-y-2.5 bg-white">
            {messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-6 text-center text-xs text-slate-400 min-h-[220px]">
                <div className="w-14 h-14 rounded-full bg-[#f0f2f5] flex items-center justify-center mb-2">
                  <Store className="w-6 h-6 text-[#0084ff]" />
                </div>
                <span className="font-semibold text-slate-700 text-sm">{activeVendorName}</span>
                <span className="text-[11px] text-slate-400 mt-1">
                  {isEn ? 'Say hello or ask questions to get started!' : 'Hãy vẫy tay hoặc gửi lời chào để bắt đầu!'}
                </span>
              </div>
            ) : (
              messages.map((m, idx) => {
                const isUser = m.senderId === user?.id;
                const prevMsg = messages[idx - 1];
                const isFirstInGroup = !prevMsg || prevMsg.senderId !== m.senderId;

                return (
                  <div
                    key={m.id}
                    className={`flex items-end gap-1.5 ${isUser ? 'justify-end' : 'justify-start'}`}
                  >
                    {/* Partner Small Avatar (Messenger Style) */}
                    {!isUser && (
                      <div className="w-6 h-6 rounded-full bg-sky-600 text-white text-[10px] font-bold flex items-center justify-center shrink-0 mb-0.5">
                        {isFirstInGroup ? activeVendorName.charAt(0).toUpperCase() : null}
                      </div>
                    )}

                    <div className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} max-w-[78%]`}>
                      <div
                        className={`px-3.5 py-2 text-[13px] leading-relaxed break-words select-text ${
                          isUser
                            ? 'bg-[#0084ff] text-white rounded-2xl rounded-br-xs shadow-2xs'
                            : 'bg-[#f0f2f5] text-slate-900 rounded-2xl rounded-bl-xs'
                        }`}
                      >
                        {m.content}
                      </div>
                      <span className="text-[9.5px] text-slate-400 mt-0.5 px-1 opacity-70">
                        {formatMessageTime(m.createdAt)}
                      </span>
                    </div>

                    {/* Sent Checkmark for user */}
                    {isUser && idx === messages.length - 1 && (
                      <CheckCheck className="w-3 h-3 text-[#0084ff] mb-1 shrink-0" />
                    )}
                  </div>
                );
              })
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Reply Suggestion Chips */}
          <div className="px-2.5 py-1.5 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
            {(isEn
              ? ['I will arrive on time', 'Where is the pickup point?', 'What should I bring along?']
              : ['Tôi sẽ có mặt đúng giờ', 'Vị trí đón ở đâu?', 'Cần mang theo đồ gì?']
            ).map((chip, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setInputMessage(chip)}
                className="px-2.5 py-1 rounded-full bg-[#f0f2f5] hover:bg-slate-200 text-[11px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0"
              >
                {chip}
              </button>
            ))}
          </div>

          {/* ==================== MESSENGER INPUT DOCK ==================== */}
          <form
            onSubmit={handleSendMessage}
            className="p-2.5 bg-white border-t border-slate-100 flex items-center gap-2 shrink-0"
          >
            {/* Action Icons (Image & Emoji) */}
            <button
              type="button"
              className="p-1.5 text-sky-600 hover:bg-blue-50 rounded-full transition-colors cursor-pointer shrink-0"
              title={isEn ? "Attach photo / voucher" : "Đính kèm ảnh / vé"}
            >
              <ImageIcon className="w-4 h-4" />
            </button>
            <button
              type="button"
              className="p-1.5 text-sky-600 hover:bg-blue-50 rounded-full transition-colors cursor-pointer shrink-0"
              title={isEn ? "Emoji" : "Biểu cảm"}
            >
              <Smile className="w-4 h-4" />
            </button>

            {/* Pill-shaped Input Field */}
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              placeholder={isEn ? "Type a message..." : "Nhắn tin..."}
              disabled={sending}
              className="flex-1 bg-[#f0f2f5] rounded-full px-3.5 py-1.5 text-xs text-slate-900 placeholder:text-slate-500 focus:outline-none focus:bg-white focus:ring-1.5 focus:ring-[#0084ff] border border-transparent transition-all"
            />

            {/* Messenger Right Action: Send OR Thumbs Up (Like) */}
            {inputMessage.trim() ? (
              <button
                type="submit"
                disabled={sending}
                title={isEn ? "Send message" : "Gửi tin nhắn"}
                className="w-7 h-7 rounded-full bg-[#0084ff] hover:bg-[#0073e6] text-white flex items-center justify-center transition-all hover:scale-105 cursor-pointer shrink-0 shadow-xs"
              >
                {sending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Send className="w-3.5 h-3.5 ml-0.5" />
                )}
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSendLike}
                disabled={sending}
                title={isEn ? "Send thumbs up" : "Gửi nút thích"}
                className="p-1.5 text-sky-600 hover:bg-blue-50 rounded-full transition-all hover:scale-115 cursor-pointer shrink-0"
              >
                <ThumbsUp className="w-4 h-4 fill-[#0084ff]" />
              </button>
            )}
          </form>
        </div>
      )}
    </div>
  );
};

export default MessagesPopup;
