import React, { useState } from 'react';
import { Compass, MessageCircle } from 'lucide-react';
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
      {/* Cửa sổ Pop-up Trợ lý Hải Trình (nằm cạnh dock, không che dock) */}
      <AiChatPopup />

      {/* Cửa sổ Pop-up Tin nhắn & Hỗ trợ (nằm cạnh dock, không che dock) */}
      <MessagesPopup isOpen={isMessagesOpen} onClose={() => setIsMessagesOpen(false)} />

      {/* Unified Floating Support Dock */}
      <aside
        aria-label="Điều hướng hỗ trợ & tư vấn biển"
        className="fixed bottom-6 right-6 z-40 flex flex-col items-center p-1.5 rounded-2xl bg-white/95 backdrop-blur-md border border-slate-200/90 shadow-[0_12px_32px_-6px_rgba(15,23,42,0.18)] select-none gap-1.5"
      >
        {/* 1. Nút Tư vấn hải trình (La bàn - Biểu tượng hàng hải chuẩn chỉ) */}
        <button
          type="button"
          onClick={handleOpenAiAssistant}
          aria-label="Tư vấn hải trình & kinh nghiệm biển"
          title="Tư vấn hải trình & kinh nghiệm biển"
          className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer group ${
            isAiOpen
              ? 'bg-sky-600 text-white shadow-sm'
              : 'text-slate-600 hover:text-sky-600 hover:bg-slate-100/80'
          }`}
        >
          <Compass className="w-5 h-5 transition-transform group-hover:scale-105" />
        </button>

        {/* Hairline Divider */}
        <div className="w-6 h-[1px] bg-slate-200/90 shrink-0" />

        {/* 2. Nút Tin nhắn điều hành & Hỗ trợ bãi biển */}
        <button
          type="button"
          onClick={handleOpenMessages}
          aria-label="Tin nhắn & Hỗ trợ bãi biển"
          title="Tin nhắn & Hỗ trợ bãi biển"
          className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer group ${
            isMessagesOpen
              ? 'bg-sky-600 text-white shadow-sm'
              : 'text-slate-600 hover:text-sky-600 hover:bg-slate-100/80'
          }`}
        >
          <MessageCircle className="w-5 h-5 transition-transform group-hover:scale-105" />

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
