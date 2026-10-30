import React, { useState, useRef, useEffect } from 'react';
import { MessageSquare, Minus, Send, ArrowLeft, Check, CheckCheck, Clock, User } from 'lucide-react';

interface Conversation {
  id: string;
  name: string;
  operator: string;
  avatar: string;
  lastMessage: string;
  time: string;
  unread: number;
  online: boolean;
  messages: Array<{
    id: string;
    sender: 'user' | 'operator';
    text: string;
    time: string;
  }>;
}

const INITIAL_CONVERSATIONS: Conversation[] = [
  {
    id: 'conv-1',
    name: 'CLB SUP Mỹ Khê - HDV Tuấn',
    operator: 'Điều hành bãi biển Mỹ Khê',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&q=80',
    lastMessage: 'Sáng mai 05:00 biển êm sóng 0.3m, đội SUP đã chuẩn bị ván sẵn cho bạn nhé!',
    time: '10 phút trước',
    unread: 1,
    online: true,
    messages: [
      { id: 'm1', sender: 'user', text: 'Chào anh, sáng mai thời tiết chèo SUP ở Mỹ Khê thế nào ạ?', time: '08:15' },
      { id: 'm2', sender: 'operator', text: 'Chào bạn! Sáng mai 05:00 biển rất êm, sóng 0.3m, gió nhẹ cấp 1. Đội SUP đã chuẩn bị sẵn ván và áo phao SOLAS cho bạn rồi nhé!', time: '08:20' },
      { id: 'm3', sender: 'operator', text: 'Bạn chỉ cần đến trước 10 phút tại Bãi tắm số 2 Công viên Biển Đông để khởi động.', time: '08:22' },
    ],
  },
  {
    id: 'conv-2',
    name: 'Trung tâm Lặn Biển Sơn Trà',
    operator: 'Bãi Bụt & Hòn Sụp',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80',
    lastMessage: 'Kính lặn và áo phao SOLAS size M của bạn đã được kiểm tra khử trùng sẵn sàng.',
    time: '45 phút trước',
    unread: 2,
    online: true,
    messages: [
      { id: 'm1', sender: 'user', text: 'Tour lặn ngắm san hô ngày mai có cano đón tại cảng không?', time: '07:30' },
      { id: 'm2', sender: 'operator', text: 'Dạ có bạn nhé. Cano cao tốc sẽ đón bạn tại Bến DHC Marina lúc 08:30 sáng.', time: '07:45' },
      { id: 'm3', sender: 'operator', text: 'Kính lặn và áo phao SOLAS size M của bạn đã được kiểm tra khử trùng sẵn sàng.', time: '08:00' },
    ],
  },
  {
    id: 'conv-3',
    name: 'Cano Cao Tốc & Dù Bay Vịnh Đà Nẵng',
    operator: 'Điều phối viên Hàng hải',
    avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=120&q=80',
    lastMessage: 'Lịch xuất bến cano ngắm hoàng hôn lúc 16:45 hôm nay đã xác nhận.',
    time: 'Hôm qua',
    unread: 0,
    online: false,
    messages: [
      { id: 'm1', sender: 'user', text: 'Cho mình kiểm tra lịch cano ngắm hoàng hôn chiều nay.', time: 'Hôm qua' },
      { id: 'm2', sender: 'operator', text: 'Lịch xuất bến cano ngắm hoàng hôn lúc 16:45 hôm nay đã xác nhận. Chúc bạn có trải nghiệm tuyệt vời!', time: 'Hôm qua' },
    ],
  },
];

interface MessagesPopupProps {
  isOpen: boolean;
  onClose: () => void;
}

export const MessagesPopup: React.FC<MessagesPopupProps> = ({ isOpen, onClose }) => {
  const [conversations, setConversations] = useState<Conversation[]>(INITIAL_CONVERSATIONS);
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const activeConversation = conversations.find((c) => c.id === activeConvId);

  useEffect(() => {
    if (activeConvId) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [activeConvId, activeConversation?.messages]);

  if (!isOpen) return null;

  const handleSelectConversation = (id: string) => {
    setActiveConvId(id);
    // Mark as read
    setConversations((prev) =>
      prev.map((c) => (c.id === id ? { ...c, unread: 0 } : c))
    );
  };

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputMessage.trim() || !activeConvId) return;

    const userText = inputMessage.trim();
    const newMsg = {
      id: `msg-${Date.now()}`,
      sender: 'user' as const,
      text: userText,
      time: 'Vừa xong',
    };

    setConversations((prev) =>
      prev.map((c) => {
        if (c.id === activeConvId) {
          return {
            ...c,
            lastMessage: userText,
            time: 'Vừa xong',
            messages: [...c.messages, newMsg],
          };
        }
        return c;
      })
    );
    setInputMessage('');

    // Simulate reply after 900ms
    setTimeout(() => {
      setConversations((prev) =>
        prev.map((c) => {
          if (c.id === activeConvId) {
            return {
              ...c,
              lastMessage: 'Đội ngũ hỗ trợ đã nhận thông tin và sẽ hỗ trợ bạn ngay!',
              time: 'Vừa xong',
              messages: [
                ...c.messages,
                {
                  id: `reply-${Date.now()}`,
                  sender: 'operator',
                  text: 'Đội ngũ điều phối viên biển DANASEA đã tiếp nhận tin nhắn và sẵn sàng hỗ trợ bạn!',
                  time: 'Vừa xong',
                },
              ],
            };
          }
          return c;
        })
      );
    }, 900);
  };


  return (
    <div
      role="dialog"
      aria-label="Cửa sổ Tin nhắn hỗ trợ"
      className="fixed bottom-22 right-4 left-4 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[400px] h-[520px] max-h-[calc(100vh-96px)] bg-white rounded-2xl border border-slate-200/90 shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
    >
      {/* Header */}
      <div className="p-3.5 sm:p-4 bg-slate-900 text-white flex items-center justify-between border-b border-slate-800 shrink-0">
        <div className="flex items-center gap-2.5">
          {activeConvId && (
            <button
              type="button"
              onClick={() => setActiveConvId(null)}
              className="w-7 h-7 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 flex items-center justify-center transition-colors cursor-pointer mr-0.5"
              title="Quay lại danh sách"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}

          <div className="w-9 h-9 rounded-xl bg-sky-600/30 border border-sky-400/30 flex items-center justify-center text-sky-400 shrink-0">
            <MessageSquare className="w-5 h-5 text-sky-300" />
          </div>

          <div>
            <span className="font-bold text-sm tracking-tight text-white block">
              {activeConversation ? activeConversation.name : 'Tin nhắn điều hành biển'}
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          title="Thu nhỏ"
          aria-label="Thu nhỏ"
          className="w-8 h-8 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 flex items-center justify-center transition-colors cursor-pointer"
        >
          <Minus className="w-5 h-5" />
        </button>
      </div>

      {/* Body: Conversation List or Message Thread */}
      {!activeConversation ? (
        <div className="flex-1 overflow-y-auto divide-y divide-slate-100 bg-slate-50/50">
          <div className="p-3 bg-white border-b border-slate-100">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              Cuộc trò chuyện gần đây
            </span>
          </div>

          {conversations.map((conv) => (
            <div
              key={conv.id}
              onClick={() => handleSelectConversation(conv.id)}
              className="p-3.5 hover:bg-white flex items-start gap-3 transition-colors cursor-pointer group"
            >
              <div className="relative shrink-0">
                <img
                  src={conv.avatar}
                  alt={conv.name}
                  className="w-10 h-10 rounded-full object-cover ring-2 ring-slate-100"
                />
                {conv.online && (
                  <span className="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full bg-emerald-500 ring-2 ring-white"></span>
                )}
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-0.5">
                  <h4 className="text-[13px] font-bold text-slate-900 truncate group-hover:text-sky-700 transition-colors">
                    {conv.name}
                  </h4>
                  <span className="text-[11px] text-slate-400 shrink-0">{conv.time}</span>
                </div>
                <p className="text-xs text-slate-600 line-clamp-1 leading-snug">
                  {conv.lastMessage}
                </p>
              </div>

              {conv.unread > 0 && (
                <span className="w-5 h-5 rounded-full bg-rose-600 text-white text-[10px] font-bold flex items-center justify-center shrink-0 mt-1">
                  {conv.unread}
                </span>
              )}
            </div>
          ))}

          <div className="p-4 text-center text-xs text-slate-400">
            Mọi chuyến đi đều có hướng dẫn viên liên lạc trực tiếp trước giờ khởi hành.
          </div>
        </div>
      ) : (
        <div className="flex-1 flex flex-col min-h-0">
          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50/60">
            {activeConversation.messages.map((m) => (
              <div
                key={m.id}
                className={`flex flex-col ${m.sender === 'user' ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`px-3.5 py-2.5 rounded-2xl text-[13px] leading-relaxed max-w-[85%] ${
                    m.sender === 'user'
                      ? 'bg-sky-600 text-white rounded-br-xs font-medium shadow-xs'
                      : 'bg-white border border-slate-200/90 text-slate-800 rounded-bl-xs shadow-xs'
                  }`}
                >
                  {m.text}
                </div>
                <span className="text-[10px] text-slate-400 mt-1 px-1">{m.time}</span>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Reply Chips */}
          <div className="px-3 py-1.5 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
            {['Tôi sẽ có mặt đúng giờ', 'Vị trí đón ở đâu?', 'Cần mang theo đồ gì?'].map((chip, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setInputMessage(chip)}
                className="px-2 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-[11px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0"
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
              className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs sm:text-[13px] text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-sky-500 focus:bg-white transition-all shadow-xs"
            />
            <button
              type="submit"
              disabled={!inputMessage.trim()}
              title="Gửi tin nhắn"
              aria-label="Gửi tin nhắn"
              className="w-9 h-9 rounded-xl bg-sky-600 hover:bg-sky-500 text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      )}
    </div>
  );
};

export default MessagesPopup;
