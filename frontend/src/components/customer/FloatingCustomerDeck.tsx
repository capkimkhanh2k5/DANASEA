import React, { useState } from 'react';
import { Bot, MessageSquare, List, X } from 'lucide-react';
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
  const [isHovered, setIsHovered] = useState(false);

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

  const hasActiveModal = isAiOpen || isMessagesOpen;

  return (
    <>
      {/* Cửa sổ Pop-up Trợ lý Hải Trình (nằm cạnh dock, không che dock) */}
      <AiChatPopup />

      {/* Cửa sổ Pop-up Tin nhắn & Hỗ trợ (nằm cạnh dock, không che dock) */}
      <MessagesPopup isOpen={isMessagesOpen} onClose={() => setIsMessagesOpen(false)} />

      {/* Floating Speed-Dial Menu: 1 nút dạng liệt kê icon List màu đen, hover trượt lên xuất hiện 2 nút */}
      <aside
        aria-label="Menu hỗ trợ và tư vấn biển"
        className="fixed bottom-6 right-6 z-40 flex flex-col items-center group select-none"
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      >
        {/* Sub-menu trượt lên khi hover hoặc khi đang mở modal */}
        <div
          className={`flex flex-col items-center gap-2 mb-3 p-1.5 rounded-2xl bg-white/95 backdrop-blur-md border border-slate-200/90 shadow-[0_12px_32px_-6px_rgba(15,23,42,0.18)] transition-all duration-300 ease-out ${
            isHovered || hasActiveModal
              ? 'opacity-100 translate-y-0 pointer-events-auto'
              : 'opacity-0 translate-y-3 pointer-events-none'
          }`}
        >
          {/* Nút 1: Trợ lý AI (Icon Bot đồng bộ 100% với header khung chat, màu đen tối giản) */}
          <div className="relative group/btn flex items-center">
            {/* Tooltip label bên trái */}
            <span className="absolute right-full mr-2.5 px-2.5 py-1 rounded-lg bg-slate-900/90 text-white text-xs font-medium whitespace-nowrap opacity-0 group-hover/btn:opacity-100 transition-opacity pointer-events-none shadow-md">
              Trợ lý AI Danasea
            </span>
            <button
              type="button"
              onClick={handleOpenAiAssistant}
              aria-label="Trợ lý AI tư vấn hải trình"
              className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer ${
                isAiOpen
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'text-slate-900 hover:bg-slate-100'
              }`}
            >
              <Bot className="w-5 h-5 text-current transition-transform group-hover/btn:scale-110" />
            </button>
          </div>

          {/* Hairline Divider */}
          <div className="w-6 h-[1px] bg-slate-200/90 shrink-0" />

          {/* Nút 2: Tin nhắn & Hỗ trợ bãi biển (màu đen tối giản) */}
          <div className="relative group/btn flex items-center">
            {/* Tooltip label bên trái */}
            <span className="absolute right-full mr-2.5 px-2.5 py-1 rounded-lg bg-slate-900/90 text-white text-xs font-medium whitespace-nowrap opacity-0 group-hover/btn:opacity-100 transition-opacity pointer-events-none shadow-md">
              Tin nhắn hỗ trợ
            </span>
            <button
              type="button"
              onClick={handleOpenMessages}
              aria-label="Tin nhắn & Hỗ trợ bãi biển"
              className={`relative w-11 h-11 rounded-xl flex items-center justify-center transition-all cursor-pointer ${
                isMessagesOpen
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'text-slate-900 hover:bg-slate-100'
              }`}
            >
              <MessageSquare className="w-5 h-5 text-current transition-transform group-hover/btn:scale-110" />

              {/* Badge số tin nhắn chưa đọc */}
              {unreadMessagesCount > 0 && !isMessagesOpen && (
                <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-600 text-white font-bold text-[10px] flex items-center justify-center ring-2 ring-white">
                  {unreadMessagesCount > 9 ? '9+' : unreadMessagesCount}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Nút kích hoạt chính (Icon List dạng liệt kê, màu đen theo đúng yêu cầu) */}
        <button
          type="button"
          onClick={() => {
            if (hasActiveModal) {
              closeAiModal();
              setIsMessagesOpen(false);
            } else {
              setIsHovered((prev) => !prev);
            }
          }}
          aria-label="Menu chức năng hỗ trợ"
          title="Tiện ích & Hỗ trợ biển"
          className={`w-12 h-12 rounded-2xl flex items-center justify-center border shadow-[0_8px_24px_-4px_rgba(15,23,42,0.14)] transition-all cursor-pointer ${
            hasActiveModal
              ? 'bg-slate-900 text-white border-slate-900 shadow-slate-900/20'
              : 'bg-white text-slate-900 border-slate-300 hover:border-slate-500 hover:shadow-md'
          }`}
        >
          {hasActiveModal ? (
            <X className="w-5 h-5 text-white transition-transform" />
          ) : (
            <List className="w-5 h-5 text-slate-900 transition-transform group-hover:scale-105" />
          )}

          {/* Unread dot when menu is collapsed */}
          {unreadMessagesCount > 0 && !hasActiveModal && !isHovered && (
            <span className="absolute top-1.5 right-1.5 w-2.5 h-2.5 rounded-full bg-rose-600 ring-2 ring-white" />
          )}
        </button>
      </aside>
    </>
  );
};

export default FloatingCustomerDeck;
