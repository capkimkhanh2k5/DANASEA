import React, { useState } from 'react';
import { MessageSquare, X, Smile } from 'lucide-react';
import { useAiModalStore } from '../../store/useAiModalStore';
import { AiChatPopup } from './AiChatPopup';
import { MessagesPopup } from './MessagesPopup';
import { DanaseaAiMascot } from './DanaseaAiMascot';

interface FloatingCustomerDeckProps {
  unreadMessagesCount?: number;
}

export const FloatingCustomerDeck: React.FC<FloatingCustomerDeckProps> = ({
  unreadMessagesCount = 0,
}) => {
  const { isOpen: isAiOpen, toggleAiModal, closeAiModal } = useAiModalStore();
  const [isMessagesOpen, setIsMessagesOpen] = useState(false);
  const [isBubbleDismissed, setIsBubbleDismissed] = useState(false);

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
      {/* Cửa sổ Pop-up Trợ lý Hải Trình */}
      <AiChatPopup />

      {/* Cửa sổ Pop-up Tin nhắn & Hỗ trợ */}
      <MessagesPopup isOpen={isMessagesOpen} onClose={() => setIsMessagesOpen(false)} />

      {/* Floating Action Stack */}
      <aside
        data-ui="customer-floating-deck"
        aria-label="Tiện ích hỗ trợ và tư vấn dịch vụ"
        className="fixed bottom-6 right-6 z-40 flex flex-col items-end gap-3 select-none"
      >
        {/* Nút 1 (Phía trên): Tin nhắn hỗ trợ */}
        <div className="relative group/chat flex items-center">
          {/* Tooltip hover */}
          <span className="absolute right-full mr-3 px-2.5 py-1 rounded-lg bg-slate-900/90 text-white text-xs font-medium whitespace-nowrap opacity-0 group-hover/chat:opacity-100 transition-opacity duration-150 pointer-events-none shadow-md">
            Tin nhắn hỗ trợ
          </span>

          <button
            type="button"
            onClick={handleOpenMessages}
            aria-label="Tin nhắn & Hỗ trợ bãi biển"
            className={`relative w-12 h-12 rounded-full flex items-center justify-center border shadow-md transition-all duration-150 cursor-pointer active:scale-95 ${
              isMessagesOpen
                ? 'bg-slate-900 text-white border-slate-900 shadow-slate-900/20'
                : 'bg-white text-slate-700 border-slate-200/90 hover:text-slate-900 hover:border-slate-300'
            }`}
          >
            <MessageSquare className="w-5 h-5 transition-transform duration-150 group-hover/chat:scale-105" />

            {/* Badge số tin nhắn chưa đọc */}
            {unreadMessagesCount > 0 && !isMessagesOpen && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-600 text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white tabular-nums">
                {unreadMessagesCount > 9 ? '9+' : unreadMessagesCount}
              </span>
            )}
          </button>
        </div>

        {/* Nút 2 (Phía dưới): Trợ lý AI DANASEA theo thiết kế mascot và hộp thoại chào mừng */}
        <div className="relative group/ai flex items-end">
          {/* Hộp thoại chào mừng thân thiện (Ảnh 2) */}
          {!isAiOpen && !isBubbleDismissed && (
            <div
              onClick={handleOpenAiAssistant}
              className="absolute right-full mr-3 bottom-1 bg-white text-slate-900 border border-slate-200/90 rounded-2xl shadow-lg px-4 py-2.5 min-w-[240px] max-w-[280px] cursor-pointer transition-transform duration-150 hover:-translate-y-0.5 animate-in fade-in slide-in-from-right-2"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleOpenAiAssistant();
                }
              }}
              aria-label="Mở Trợ lý AI DANASEA"
            >
              {/* Nút tắt lời chào nhỏ */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setIsBubbleDismissed(true);
                }}
                aria-label="Đóng lời chào"
                className="absolute top-2 right-2 w-4 h-4 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
              >
                <X className="w-3 h-3" />
              </button>

              <div className="pr-4">
                <p className="font-bold text-[13px] text-slate-900 leading-tight">
                  Trợ lý AI DANASEA
                </p>
                <p className="text-slate-600 text-xs leading-normal mt-0.5 flex items-center gap-1.5">
                  <span>Em rất sẵn lòng hỗ trợ bạn</span>
                  <Smile className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                </p>
              </div>

              {/* Mũi tên hộp thoại chỉ về linh vật */}
              <div
                className="absolute -right-1.5 bottom-4 w-3 h-3 bg-white border-t border-r border-slate-200/90 rotate-45 pointer-events-none"
                aria-hidden="true"
              />
            </div>
          )}

          {/* Mascot AI Button */}
          <button
            type="button"
            onClick={handleOpenAiAssistant}
            aria-label="Trợ lý AI DANASEA"
            className="relative flex items-center justify-center transition-transform duration-150 cursor-pointer active:scale-95 group-hover/ai:scale-105 focus:outline-hidden"
          >
            {/* Mascot Avatar SVG */}
            <DanaseaAiMascot size={58} className="drop-shadow-lg" />
          </button>
        </div>
      </aside>
    </>
  );
};

export default FloatingCustomerDeck;
