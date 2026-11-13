import React, { useState, useRef, useEffect } from 'react';
import { 
  Bot, 
  X, 
  Minus, 
  Send, 
  RotateCcw, 
  Clock, 
  Paperclip, 
  Check, 
  Pin, 
  Trash2, 
  Plus 
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { CopilotArtifactCard } from './CopilotArtifactCard';

const QUICK_SUGGESTIONS = [
  'Doanh thu & đơn hôm nay?',
  'Phân tích đánh giá review?',
  'Ca tour vắng khách 7 ngày tới?',
  'Tối ưu bài đăng dịch vụ?',
];

export const VendorCopilotDrawer: React.FC = () => {
  const {
    isOpen,
    closeCopilot,
    activeConversationId,
    conversations,
    messages,
    isLoading,
    isSessionsLoading,
    attachedContext,
    clearAttachedContext,
    setAttachedContext,
    selectConversation,
    startNewConversation,
    sendMessage,
    deleteConversation,
    togglePinConversation,
  } = useVendorCopilotStore();

  const [inputValue, setInputValue] = useState('');
  const [showSessions, setShowSessions] = useState(false);
  const [showAttachModal, setShowAttachModal] = useState(false);
  const [attachType, setAttachType] = useState<'ORDER' | 'SERVICE'>('ORDER');
  const [attachIdInput, setAttachIdInput] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen, isLoading]);

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
    if (!inputValue.trim() || isLoading) return;

    const text = inputValue.trim();
    setInputValue('');
    await sendMessage(text);
  };

  const handleSuggestionClick = async (suggestion: string) => {
    if (isLoading) return;
    await sendMessage(suggestion);
  };

  const handleApplyAttach = () => {
    if (attachIdInput.trim()) {
      setAttachedContext({
        type: attachType,
        id: attachIdInput.trim(),
        label: `${attachType === 'ORDER' ? 'Đơn' : 'Tour'} #${attachIdInput.trim().slice(0, 8)}`,
      });
      setShowAttachModal(false);
      setAttachIdInput('');
    }
  };

  return (
    <div
      role="dialog"
      aria-label="Cửa sổ Trợ lý kinh doanh DANASEA Copilot"
      className="fixed bottom-20 right-3 left-3 sm:left-auto sm:bottom-6 sm:right-[88px] z-50 w-auto sm:w-[400px] h-[540px] max-h-[calc(100vh-80px)] bg-white rounded-2xl border border-slate-200/90 shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150 text-left print:hidden select-none"
    >
      {/* Top Header */}
      <div className="px-3.5 py-2.5 bg-white text-slate-900 flex items-center justify-between border-b border-slate-100 shadow-2xs shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          <Bot className="w-4 h-4 text-sky-600 shrink-0" strokeWidth={1.75} />
          <h3 className="font-semibold text-xs sm:text-[13px] text-slate-900 truncate leading-snug">
            DANASEA Copilot
          </h3>
        </div>

        <div className="flex items-center gap-1 shrink-0 ml-2">
          <button
            type="button"
            onClick={() => setShowSessions(!showSessions)}
            title="Lịch sử đoạn chat"
            className={`w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer ${
              showSessions ? 'bg-slate-100 text-slate-900 font-bold' : ''
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={startNewConversation}
            title="Làm mới / Đoạn chat mới"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={closeCopilot}
            title="Thu nhỏ"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={closeCopilot}
            title="Đóng"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Sessions Menu Dropdown */}
      {showSessions && (
        <div className="border-b border-slate-200 bg-slate-50 p-2.5 max-h-48 overflow-y-auto shrink-0 flex flex-col gap-1 shadow-inner">
          <div className="flex items-center justify-between pb-1 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
            <span>Phiên hội thoại</span>
            <button
              onClick={() => { startNewConversation(); setShowSessions(false); }}
              className="text-sky-600 hover:text-sky-700 font-semibold cursor-pointer text-[11px] flex items-center gap-1"
            >
              <Plus className="w-3 h-3" />
              <span>Mới</span>
            </button>
          </div>

          {isSessionsLoading ? (
            <div className="py-2 text-center text-xs text-slate-400">Đang tải...</div>
          ) : conversations.length === 0 ? (
            <div className="py-2 text-center text-xs text-slate-400">Chưa có phiên chat nào</div>
          ) : (
            conversations.map((c) => {
              const isActive = c.id === activeConversationId;
              return (
                <div
                  key={c.id}
                  onClick={() => {
                    selectConversation(c.id);
                    setShowSessions(false);
                  }}
                  className={`group flex items-center justify-between p-1.5 px-2 rounded-lg cursor-pointer text-xs transition-colors ${
                    isActive 
                      ? 'bg-white border border-slate-200 text-slate-900 font-semibold shadow-2xs' 
                      : 'hover:bg-slate-200/60 text-slate-700'
                  }`}
                >
                  <div className="flex items-center gap-1.5 min-w-0 flex-1">
                    {c.pinned && <Pin className="w-3 h-3 text-amber-500 fill-amber-500 shrink-0" />}
                    <span className="truncate text-[11px]">{c.title || 'Đoạn chat không tên'}</span>
                  </div>

                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity ml-1 shrink-0">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        togglePinConversation(c.id, c.pinned);
                      }}
                      className="p-0.5 text-slate-400 hover:text-amber-600 rounded"
                    >
                      <Pin className="w-3 h-3" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteConversation(c.id);
                      }}
                      className="p-0.5 text-slate-400 hover:text-rose-600 rounded"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* Attached Context Strip */}
      {attachedContext && (
        <div className="px-3 py-1.5 bg-sky-50/80 border-b border-sky-100 flex items-center justify-between text-[11px] text-sky-900 shrink-0">
          <span className="font-medium truncate">Đang ghim: <strong>{attachedContext.label}</strong></span>
          <button
            onClick={clearAttachedContext}
            className="p-0.5 text-sky-600 hover:text-sky-900 rounded cursor-pointer"
          >
            <X className="w-3 h-3" />
          </button>
        </div>
      )}

      {/* Messages Thread */}
      <div className="flex-1 overflow-y-auto p-3.5 space-y-3 bg-slate-50/60">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col justify-center items-center text-center p-4">
            <p className="text-xs text-slate-600 font-medium mb-1">Trợ lý dữ liệu kinh doanh DANASEA</p>
            <p className="text-[11px] text-slate-400 max-w-[260px] leading-relaxed">
              Chọn câu hỏi nhanh bên dưới hoặc nhập yêu cầu để kiểm tra số liệu gian hàng.
            </p>
          </div>
        ) : (
          messages.map((m) => {
            const isUser = m.role === 'user';
            return (
              <div
                key={m.id}
                className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} animate-in fade-in duration-100`}
              >
                {isUser ? (
                  <div className="bg-sky-700 text-white rounded-xl rounded-tr-xs px-3.5 py-2.5 max-w-[85%] text-xs font-normal leading-relaxed shadow-xs">
                    {m.content}
                  </div>
                ) : (
                  <div className="bg-white border border-slate-200/90 text-slate-800 rounded-xl rounded-tl-xs px-3.5 py-2.5 max-w-[92%] text-xs leading-relaxed shadow-xs">
                    {m.toolsUsed && m.toolsUsed.length > 0 && (
                      <div className="mb-1.5 pb-1 border-b border-slate-100 flex items-center gap-1 text-[10px] text-sky-700 font-medium">
                        <Check className="w-3 h-3 text-sky-600 shrink-0" />
                        <span>Đối chiếu: {m.toolsUsed.join(', ')}</span>
                      </div>
                    )}

                    <div className="whitespace-pre-line text-slate-700">{m.content}</div>

                    {m.artifacts && m.artifacts.length > 0 && (
                      <div className="flex flex-col gap-2 mt-2">
                        {m.artifacts.map((art, aIdx) => (
                          <CopilotArtifactCard key={aIdx} artifact={art} />
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}

        {isLoading && (
          <div className="flex items-start animate-in fade-in duration-100">
            <div className="bg-white border border-slate-200/90 rounded-xl px-3 py-2 text-xs text-slate-500 shadow-xs flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-sky-500 animate-pulse" />
              <span>Đang tổng hợp số liệu vận hành...</span>
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
            disabled={isLoading}
            onClick={() => handleSuggestionClick(suggestion)}
            className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200/80 text-[11px] font-medium text-slate-700 whitespace-nowrap transition-colors cursor-pointer shrink-0 disabled:opacity-50"
          >
            {suggestion}
          </button>
        ))}
      </div>

      {/* Input Deck */}
      <form onSubmit={handleSend} className="p-3 bg-white border-t border-slate-200/90 flex items-center gap-2 shrink-0">
        <button
          type="button"
          onClick={() => setShowAttachModal(true)}
          title="Kẹp ghim đơn hàng hoặc tour"
          className="w-9 h-9 rounded-xl border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-500 flex items-center justify-center transition-colors cursor-pointer shrink-0"
        >
          <Paperclip className="w-3.5 h-3.5" />
        </button>

        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder={attachedContext ? `Hỏi về ${attachedContext.label}...` : "Hỏi doanh thu, đơn hàng, ca vắng..."}
          disabled={isLoading}
          className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-sky-500 focus:bg-white transition-all shadow-xs"
        />

        <button
          type="submit"
          disabled={isLoading || !inputValue.trim()}
          title="Gửi câu hỏi"
          className="w-9 h-9 rounded-xl bg-sky-700 hover:bg-sky-800 text-white flex items-center justify-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0 shadow-xs"
        >
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>

      {/* Attach Modal Popup */}
      {showAttachModal && (
        <div className="absolute inset-0 z-50 bg-slate-900/30 backdrop-blur-2xs flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-xs w-full p-4 shadow-xl border border-slate-200 flex flex-col gap-3 text-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h4 className="font-bold text-slate-900 text-xs">Ghim ngữ cảnh</h4>
              <button onClick={() => setShowAttachModal(false)} className="text-slate-400 hover:text-slate-700">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="font-medium text-slate-700 text-[11px]">Đối tượng:</label>
              <div className="grid grid-cols-2 gap-1.5">
                <button
                  type="button"
                  onClick={() => setAttachType('ORDER')}
                  className={`p-1.5 rounded-lg border text-center font-semibold text-[11px] transition-colors cursor-pointer ${
                    attachType === 'ORDER' ? 'bg-sky-50 border-sky-500 text-sky-700' : 'bg-slate-50 border-slate-200 text-slate-600'
                  }`}
                >
                  Đơn hàng
                </button>
                <button
                  type="button"
                  onClick={() => setAttachType('SERVICE')}
                  className={`p-1.5 rounded-lg border text-center font-semibold text-[11px] transition-colors cursor-pointer ${
                    attachType === 'SERVICE' ? 'bg-sky-50 border-sky-500 text-sky-700' : 'bg-slate-50 border-slate-200 text-slate-600'
                  }`}
                >
                  Dịch vụ
                </button>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="font-medium text-slate-700 text-[11px]">Mã ID:</label>
              <input
                type="text"
                value={attachIdInput}
                onChange={(e) => setAttachIdInput(e.target.value)}
                placeholder="Nhập mã đơn / ID..."
                className="w-full text-xs p-1.5 rounded-lg border border-slate-200 focus:outline-none focus:ring-1 focus:ring-sky-500"
              />
            </div>

            <div className="flex justify-end gap-1.5 pt-1.5 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setShowAttachModal(false)}
                className="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-600 text-[11px] font-medium hover:bg-slate-200"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleApplyAttach}
                disabled={!attachIdInput.trim()}
                className="px-2.5 py-1 rounded-lg bg-slate-900 text-white text-[11px] font-medium hover:bg-slate-800 disabled:opacity-40"
              >
                Ghim
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
