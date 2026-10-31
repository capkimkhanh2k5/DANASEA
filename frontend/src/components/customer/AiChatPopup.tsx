import React, { useState, useRef, useEffect } from 'react';
import { Compass, Minus, Send, RotateCcw, Sparkles } from 'lucide-react';
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
      aria-label="Cửa sổ Trợ lý AI DANASEA"
      className="fixed bottom-20 right-3 left-3 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[340px] h-[450px] max-h-[calc(100vh-80px)] bg-white rounded-2xl border border-slate-200/90 shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200 text-left"
    >
      {/* Top Header */}
      <div className="p-3 bg-slate-900 text-white flex items-center justify-between border-b border-slate-800 shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-sky-600/30 border border-sky-400/30 flex items-center justify-center text-sky-400 shrink-0">
            <Compass className="w-4 h-4 text-sky-300" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-xs tracking-tight text-white">DANASEA AI</span>
              <span className="inline-flex items-center gap-1 px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-400 text-[9px] font-semibold border border-emerald-500/30">
                <span className="w-1 h-1 rounded-full bg-emerald-400"></span>
                Trực tuyến
              </span>
            </div>
            <p className="text-[10px] text-slate-400">Tư vấn hải văn &amp; thể thao biển</p>
          </div>
        </div>

        <div className="flex items-center gap-0.5">
          <button
            type="button"
            onClick={clearHistory}
            title="Làm mới đoạn hội thoại"
            aria-label="Làm mới đoạn hội thoại"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 flex items-center justify-center transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={closeAiModal}
            title="Thu nhỏ"
            aria-label="Thu nhỏ"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 flex items-center justify-center transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Messages Thread */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2.5 bg-slate-50/50">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'} animate-in fade-in duration-150`}
          >
            {msg.role === 'user' ? (
              <div className="bg-sky-600 text-white rounded-2xl rounded-br-xs px-3 py-2 max-w-[85%] text-xs font-medium leading-relaxed shadow-xs">
                {msg.content}
              </div>
            ) : (
              <div className="bg-white border border-slate-200/90 text-slate-800 rounded-2xl rounded-bl-xs px-3 py-2.5 max-w-[94%] text-xs leading-relaxed shadow-xs">
                <div className="flex items-center gap-1.5 mb-1 text-sky-700 font-bold text-[10px]">
                  <Sparkles className="w-3 h-3 text-sky-500 shrink-0" />
                  <span>DANASEA Concierge</span>
                </div>
                <div className="whitespace-pre-line text-slate-700">{msg.content}</div>
              </div>
            )}
          </div>
        ))}

        {isGenerating && (
          <div className="flex items-start animate-in fade-in duration-150">
            <div className="bg-white border border-slate-200/90 rounded-2xl rounded-bl-xs px-3 py-2 text-[11px] text-slate-500 shadow-xs flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-sky-500 animate-pulse"></span>
              <span>Đang phân tích dữ liệu hải văn...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick Suggestion Chips */}
      <div className="px-2.5 py-1.5 bg-white border-t border-slate-100 flex items-center gap-1.5 overflow-x-auto no-scrollbar shrink-0">
        <span className="text-[10px] font-semibold text-slate-400 shrink-0 pl-1">Gợi ý:</span>
        {QUICK_SUGGESTIONS.map((suggestion, idx) => (
          <button
            key={idx}
            type="button"
            disabled={isGenerating}
            onClick={() => handleSuggestionClick(suggestion)}
            className="px-2 py-0.5 rounded-md bg-slate-100 hover:bg-slate-200/80 text-[10px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0 disabled:opacity-50"
          >
            {suggestion}
          </button>
        ))}
      </div>

      {/* Input Deck */}
      <form onSubmit={handleSend} className="p-2.5 bg-white border-t border-slate-200/90 flex items-center gap-2 shrink-0">
        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="Hỏi AI về biển, thời tiết, hoạt động..."
          disabled={isGenerating}
          className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-sky-500 focus:bg-white transition-all shadow-xs"
        />
        <button
          type="submit"
          disabled={isGenerating || !inputValue.trim()}
          title="Gửi câu hỏi"
          aria-label="Gửi câu hỏi"
          className="w-8 h-8 rounded-xl bg-sky-600 hover:bg-sky-500 text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
        >
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>
    </div>
  );
};

export default AiChatPopup;
