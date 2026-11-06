import React, { useState, useRef, useEffect } from 'react';
import { Bot, Minus, Send, RotateCcw } from 'lucide-react';
import { useAiModalStore } from '../../store/useAiModalStore';

const QUICK_SUGGESTIONS = [
  'Thời tiết & sóng biển hôm nay?',
  'Lặn san hô Hòn Sụp lúc mấy giờ đẹp?',
  'Gợi ý tour SUP đón bình minh Mỹ Khê',
  'Tiêu chuẩn an toàn & bảo hiểm',
];

export const AiChatPopup: React.FC = () => {
  const { isOpen, messages, isGenerating, closeAiModal, sendMessage, clearHistory } = useAiModalStore();
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

  return (
    <div
      role="dialog"
      aria-label="Cửa sổ Hỗ trợ & Tư vấn dịch vụ"
      className="fixed bottom-20 right-3 left-3 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[360px] h-[480px] max-h-[calc(100vh-80px)] bg-white rounded-2xl border border-slate-200/90 shadow-xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150 text-left"
    >
      {/* Top Header - Synced with Footer Brand Navy (#031d44) */}
      <div className="p-3.5 bg-[#031d44] text-white flex items-center justify-between border-b border-sky-950 shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-white/10 border border-white/15 flex items-center justify-center text-white shrink-0">
            <Bot className="w-4 h-4 text-white" />
          </div>
          <div>
            <span className="font-semibold text-xs tracking-tight text-white block">Tư vấn dịch vụ & Hải văn</span>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={clearHistory}
            title="Làm mới hội thoại"
            aria-label="Làm mới hội thoại"
            className="w-7 h-7 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={closeAiModal}
            title="Đóng cửa sổ"
            aria-label="Đóng cửa sổ"
            className="w-7 h-7 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
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
              <div className="bg-[#031d44] text-white rounded-xl rounded-tr-xs px-3.5 py-2.5 max-w-[85%] text-xs font-normal leading-relaxed shadow-xs">
                {msg.content}
              </div>
            ) : (
              <div className="bg-white border border-slate-200/90 text-slate-800 rounded-xl rounded-tl-xs px-3.5 py-2.5 max-w-[92%] text-xs leading-relaxed shadow-xs">
                <div className="whitespace-pre-line text-slate-700">{msg.content}</div>
              </div>
            )}
          </div>
        ))}

        {isGenerating && (
          <div className="flex items-start animate-in fade-in duration-100">
            <div className="bg-white border border-slate-200/90 rounded-xl px-3 py-2 text-xs text-slate-500 shadow-xs flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse"></span>
              <span>Đang tra cứu dữ liệu...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick Suggestion Chips */}
      <div className="px-3 py-2 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
        {QUICK_SUGGESTIONS.map((suggestion, idx) => (
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
          placeholder="Nhập câu hỏi cần hỗ trợ..."
          disabled={isGenerating}
          className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-[#031d44] focus:bg-white transition-all shadow-xs"
        />
        <button
          type="submit"
          disabled={isGenerating || !inputValue.trim()}
          title="Gửi câu hỏi"
          aria-label="Gửi câu hỏi"
          className="w-9 h-9 rounded-xl bg-[#031d44] hover:bg-[#021838] text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
        >
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>
    </div>
  );
};

export default AiChatPopup;
