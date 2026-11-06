import React, { useState } from 'react';
import { Bot, MessageSquare } from 'lucide-react';
import { useAiModalStore } from '../../store/useAiModalStore';
import { AiChatPopup } from './AiChatPopup';
import { MessagesPopup } from './MessagesPopup';

interface FloatingCustomerDeckProps {
  unreadMessagesCount?: number;
}

export const FloatingCustomerDeck: React.FC<FloatingCustomerDeckProps> = ({
  unreadMessagesCount = 0,
}) => {
  const { isOpen: isAiOpen, toggleAiModal, closeAiModal } = useAiModalStore();
  const [isMessagesOpen, setIsMessagesOpen] = useState(false);

  const handleOpenAiAssistant = () => {
    if (isMessagesOpen) {
      setIsMessagesOpen(false);
    }
    toggleAiModal();
  };

  const handleOpenMessages = () => {
    if (isAiOpen) {
      closeAiModal();
    }
    setIsMessagesOpen((prev) => !prev);
  };

  return (
    <>
      {/* Cửa sổ Pop-up Trợ lý AI (nằm cạnh dock, không che dock) */}
      <AiChatPopup />

      {/* Cửa sổ Pop-up Tin nhắn & Hỗ trợ (nằm cạnh dock, không che dock) */}
      <MessagesPopup isOpen={isMessagesOpen} onClose={() => setIsMessagesOpen(false)} />

      {/* Unified Floating Support Dock (Vertical Pill - Icon màu đen tinh gọn) */}
      <aside
        aria-label="Điều hướng hỗ trợ & tư vấn biển"
        className="fixed bottom-6 right-6 z-40 flex flex-col items-center p-1.5 rounded-2xl bg-white/95 backdrop-blur-md border border-slate-200/90 shadow-[0_12px_32px_-6px_rgba(15,23,42,0.18)] select-none gap-1.5"
      >
        {/* 1. Nút Trợ lý AI Danasea (Icon Bot màu đen đồng bộ 100% với khung chat) */}
        <button
          type="button"
          onClick={handleOpenAiAssistant}
          aria-label="Trợ lý AI Danasea"
          title="Trợ lý AI Danasea"
          className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer group ${
            isAiOpen
              ? 'bg-slate-100 text-slate-900 ring-1 ring-slate-300 shadow-xs'
              : 'text-slate-900 hover:bg-slate-100/80'
          }`}
        >
          <Bot className="w-5 h-5 text-slate-900 transition-transform group-hover:scale-105" />
        </button>

        {/* Hairline Divider */}
        <div className="w-6 h-[1px] bg-slate-200/90 shrink-0" />

        {/* 2. Nút Tin nhắn & Hỗ trợ bãi biển (Icon màu đen) */}
        <button
          type="button"
          onClick={handleOpenMessages}
          aria-label="Tin nhắn & Hỗ trợ bãi biển"
          title="Tin nhắn & Hỗ trợ bãi biển"
          className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer group ${
            isMessagesOpen
              ? 'bg-slate-100 text-slate-900 ring-1 ring-slate-300 shadow-xs'
              : 'text-slate-900 hover:bg-slate-100/80'
          }`}
        >
          <MessageSquare className="w-5 h-5 text-slate-900 transition-transform group-hover:scale-105" />

          {/* Badge số tin nhắn chưa đọc */}
          {unreadMessagesCount > 0 && !isMessagesOpen && (
            <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-600 text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
              {unreadMessagesCount > 9 ? '9+' : unreadMessagesCount}
            </span>
          )}
        </button>
      </aside>
    </>
  );
};

export default FloatingCustomerDeck;
