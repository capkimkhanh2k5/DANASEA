import React, { useState, useRef, useEffect } from 'react';
import { Minus, X, Send, RotateCcw } from 'lucide-react';
import { useAiModalStore, type AiChatMessage } from '../../store/useAiModalStore';
import { useLanguageStore } from '../../store/useLanguageStore';

const QUICK_SUGGESTIONS_VI = [
  'Thời tiết & sóng biển hôm nay?',
  'Lặn san hô Hòn Sụp lúc mấy giờ đẹp?',
  'Gợi ý tour SUP đón bình minh Mỹ Khê',
  'Tiêu chuẩn an toàn & bảo hiểm',
];

const QUICK_SUGGESTIONS_EN = [
  "Today's ocean weather & waves?",
  'Best time for Hon Sup snorkeling?',
  'Sunrise SUP at My Khe beach',
  'Safety standards & insurance',
];

export const AiChatPopup: React.FC = () => {
  const { isOpen, messages, isGenerating, closeAiModal, sendMessage, clearHistory } = useAiModalStore();
  const { language } = useLanguageStore();
  const isEn = language === 'en';

  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-scroll to bottom when messages update
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen, isGenerating]);

  // Auto-focus input when opened
  useEffect(() => {
    if (isOpen) {
      const timer = setTimeout(() => {
        inputRef.current?.focus();
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const quickSuggestions = isEn ? QUICK_SUGGESTIONS_EN : QUICK_SUGGESTIONS_VI;

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputValue.trim() || isGenerating) return;

    const text = inputValue.trim();
    setInputValue('');
    await sendMessage(text);
  };

  const handleSuggestionClick = async (suggestion: string) => {
    if (isGenerating) return;
    await sendMessage(suggestion);
  };

  const renderMessageContent = (msg: AiChatMessage) => {
    if (msg.id === 'welcome-msg') {
      return isEn
        ? 'I can help you check ocean weather, marine conditions, and guide tour bookings in Da Nang. What information do you need?'
        : 'Hệ thống hỗ trợ tra cứu điều kiện hải văn, cập nhật thời tiết và tư vấn đặt dịch vụ biển tại Đà Nẵng. Bạn cần tìm kiếm thông tin nào?';
    }
    return msg.content;
  };

  return (
    <div
      role="dialog"
      aria-label={isEn ? 'AI Tour Assistant Window' : 'Cửa sổ Trợ lý tư vấn dịch vụ'}
      className="fixed bottom-20 right-3 left-3 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[360px] h-[480px] max-h-[calc(100vh-80px)] bg-white rounded-2xl border border-slate-200/90 shadow-xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150 text-left"
    >
      {/* Top Header - Streamlined and styled identically to MessagesPopup */}
      <div className="px-3.5 py-2.5 bg-white text-slate-900 flex items-center justify-between border-b border-slate-100 shadow-2xs shrink-0">
        <div className="min-w-0">
          <h3 className="font-semibold text-xs sm:text-[13px] text-slate-900 truncate leading-snug">
            {isEn ? 'AI Assistant' : 'Trợ lý tư vấn dịch vụ'}
          </h3>
        </div>

        <div className="flex items-center gap-1 shrink-0 ml-2">
          <button
            type="button"
            onClick={clearHistory}
            title={isEn ? 'Reset conversation' : 'Làm mới hội thoại'}
            aria-label={isEn ? 'Reset conversation' : 'Làm mới hội thoại'}
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={closeAiModal}
            title={isEn ? 'Minimize' : 'Thu nhỏ'}
            aria-label={isEn ? 'Minimize' : 'Thu nhỏ'}
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={closeAiModal}
            title={isEn ? 'Close' : 'Đóng'}
            aria-label={isEn ? 'Close' : 'Đóng'}
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Messages Thread */}
      <div className="flex-1 overflow-y-auto p-3.5 space-y-3 bg-slate-50/60">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'} animate-in fade-in duration-100`}
          >
            {msg.role === 'user' ? (
              <div className="bg-sky-700 text-white rounded-xl rounded-tr-xs px-3.5 py-2.5 max-w-[85%] text-xs font-normal leading-relaxed shadow-xs">
                {msg.content}
              </div>
            ) : (
              <div className="bg-white border border-slate-200/90 text-slate-800 rounded-xl rounded-tl-xs px-3.5 py-2.5 max-w-[92%] text-xs leading-relaxed shadow-xs">
                <div className="whitespace-pre-line text-slate-700">{renderMessageContent(msg)}</div>
              </div>
            )}
          </div>
        ))}

        {isGenerating && (
          <div className="flex items-start animate-in fade-in duration-100">
            <div className="bg-white border border-slate-200/90 rounded-xl px-3 py-2 text-xs text-slate-500 shadow-xs flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse"></span>
              <span>{isEn ? 'Searching marine data...' : 'Đang tra cứu dữ liệu...'}</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick Suggestion Chips */}
      <div className="px-3 py-2 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
        {quickSuggestions.map((suggestion, idx) => (
          <button
            key={idx}
            type="button"
            disabled={isGenerating}
            onClick={() => handleSuggestionClick(suggestion)}
            className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200/80 text-[11px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0 disabled:opacity-50"
          >
            {suggestion}
          </button>
        ))}
      </div>

      {/* Input Deck */}
      <form onSubmit={handleSend} className="p-3 bg-white border-t border-slate-200/90 flex items-center gap-2 shrink-0">
        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder={isEn ? 'Ask a question or request advice...' : 'Nhập câu hỏi cần hỗ trợ...'}
          disabled={isGenerating}
          className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-sky-500 focus:bg-white transition-all shadow-xs"
        />
        <button
          type="submit"
          disabled={isGenerating || !inputValue.trim()}
          title={isEn ? 'Send question' : 'Gửi câu hỏi'}
          aria-label={isEn ? 'Send question' : 'Gửi câu hỏi'}
          className="w-9 h-9 rounded-xl bg-sky-700 hover:bg-sky-800 text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
        >
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>
    </div>
  );
};

export default AiChatPopup;
