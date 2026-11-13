import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { 
  Bot, 
  X, 
  Plus, 
  Send, 
  Pin, 
  Trash2, 
  Clock, 
  Paperclip, 
  Check, 
  TrendingUp, 
  Star, 
  CalendarClock, 
  Wand2 
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { CopilotArtifactCard } from './CopilotArtifactCard';

const STARTER_PROMPTS = [
  {
    icon: TrendingUp,
    title: 'Doanh thu hôm nay',
    prompt: 'Doanh thu và tổng số đơn của gian hàng hôm nay thế nào?',
  },
  {
    icon: Star,
    title: 'Phân tích review',
    prompt: 'Phân tích đánh giá review của khách hàng trong 90 ngày qua',
  },
  {
    icon: CalendarClock,
    title: 'Tư vấn ca vận hành',
    prompt: 'Kiểm tra ca vận hành vắng khách và cảnh báo thời tiết 7 ngày tới',
  },
  {
    icon: Wand2,
    title: 'Tối ưu bài đăng',
    prompt: 'Đánh giá chất lượng và gợi ý tối ưu các bài đăng dịch vụ biển',
  },
];

export function VendorCopilotDrawer() {
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

  const [input, setInput] = useState('');
  const [showSessions, setShowSessions] = useState(false);
  const [showAttachModal, setShowAttachModal] = useState(false);
  const [attachType, setAttachType] = useState<'ORDER' | 'SERVICE'>('ORDER');
  const [attachIdInput, setAttachIdInput] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 150);
    }
  }, [isOpen]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  if (!isOpen) return null;

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || isLoading) return;
    const text = input.trim();
    setInput('');
    sendMessage(text);
  };

  const handleApplyAttach = () => {
    if (attachIdInput.trim()) {
      setAttachedContext({
        type: attachType,
        id: attachIdInput.trim(),
        label: `${attachType === 'ORDER' ? 'Đơn hàng' : 'Dịch vụ'} #${attachIdInput.trim().slice(0, 8)}`,
      });
      setShowAttachModal(false);
      setAttachIdInput('');
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[9990] flex justify-end print:hidden select-none">
      {/* Backdrop */}
      <div 
        onClick={closeCopilot} 
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity duration-200" 
      />

      {/* Slide-over Drawer */}
      <aside className="relative z-10 w-full max-w-[500px] h-full bg-white shadow-2xl flex flex-col border-l border-slate-200/90 text-slate-800">
        
        {/* Drawer Header */}
        <div className="h-14 px-4 border-b border-slate-200/80 flex items-center justify-between shrink-0 bg-white">
          <div className="flex items-center gap-2.5">
            <Bot className="w-5 h-5 text-sky-600" strokeWidth={1.75} />
            <h3 className="text-sm font-bold text-slate-900 leading-tight">DANASEA Copilot</h3>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => {
                setShowSessions(!showSessions);
              }}
              className={`p-2 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 transition-colors cursor-pointer ${
                showSessions ? 'bg-slate-200 text-slate-900 font-bold' : ''
              }`}
              title="Lịch sử đoạn chat"
            >
              <Clock className="w-4 h-4" />
            </button>
            <button
              onClick={() => {
                startNewConversation();
                setShowSessions(false);
              }}
              className="p-2 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 transition-colors cursor-pointer"
              title="Tạo đoạn chat mới"
            >
              <Plus className="w-4 h-4" />
            </button>
            <button
              onClick={closeCopilot}
              className="p-2 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 transition-colors cursor-pointer ml-1"
              title="Đóng khung chat"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Sessions Slide-Down Menu */}
        {showSessions && (
          <div className="border-b border-slate-200 bg-slate-50 p-3 max-h-60 overflow-y-auto shrink-0 flex flex-col gap-1.5 shadow-inner">
            <div className="flex items-center justify-between pb-1 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
              <span>Đoạn chat gần đây</span>
              <button 
                onClick={() => { startNewConversation(); setShowSessions(false); }}
                className="text-sky-600 hover:text-sky-700 font-semibold cursor-pointer text-xs flex items-center gap-1"
              >
                <Plus className="w-3 h-3" />
                <span>Chat mới</span>
              </button>
            </div>

            {isSessionsLoading ? (
              <div className="py-4 text-center text-xs text-slate-400">Đang tải lịch sử...</div>
            ) : conversations.length === 0 ? (
              <div className="py-4 text-center text-xs text-slate-400">Chưa có phiên chat nào</div>
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
                    className={`group flex items-center justify-between p-2 rounded-lg cursor-pointer text-xs transition-colors ${
                      isActive 
                        ? 'bg-white border border-slate-200 text-slate-900 font-semibold shadow-2xs' 
                        : 'hover:bg-slate-200/60 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-2 min-w-0 flex-1">
                      {c.pinned && <Pin className="w-3 h-3 text-amber-500 fill-amber-500 shrink-0" />}
                      <span className="truncate">{c.title || 'Đoạn chat không tên'}</span>
                    </div>

                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity ml-2 shrink-0">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          togglePinConversation(c.id, c.pinned);
                        }}
                        className="p-1 text-slate-400 hover:text-amber-600 rounded"
                        title={c.pinned ? 'Bỏ ghim' : 'Ghim lên đầu'}
                      >
                        <Pin className="w-3 h-3" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          deleteConversation(c.id);
                        }}
                        className="p-1 text-slate-400 hover:text-rose-600 rounded"
                        title="Xóa đoạn chat"
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

        {/* Attached Context Banner */}
        {attachedContext && (
          <div className="px-4 py-2 bg-sky-50/80 border-b border-sky-100 flex items-center justify-between text-xs text-sky-900 shrink-0">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-sky-200/70 text-sky-800">
                Ngữ cảnh
              </span>
              <span className="font-semibold truncate">{attachedContext.label}</span>
            </div>
            <button
              onClick={clearAttachedContext}
              className="p-1 text-sky-600 hover:text-sky-900 rounded cursor-pointer"
              title="Gỡ bỏ ngữ cảnh này"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Messages Body */}
        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
          {messages.length === 0 ? (
            <div className="my-auto flex flex-col items-center py-4 w-full">
              <div className="w-full grid grid-cols-2 gap-2 text-left">
                {STARTER_PROMPTS.map((sp, idx) => {
                  const Icon = sp.icon;
                  return (
                    <button
                      key={idx}
                      onClick={() => sendMessage(sp.prompt)}
                      className="p-3 rounded-xl bg-white hover:bg-slate-50 border border-slate-200/90 hover:border-slate-300 transition-all text-left flex flex-col gap-1 group cursor-pointer shadow-2xs hover:shadow-xs"
                    >
                      <div className="flex items-center gap-1.5">
                        <Icon className="w-3.5 h-3.5 text-sky-600 group-hover:text-sky-700 shrink-0" strokeWidth={1.75} />
                        <span className="text-xs font-bold text-slate-900">{sp.title}</span>
                      </div>
                      <span className="text-[10px] text-slate-500 line-clamp-2 leading-relaxed">
                        {sp.prompt}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : (
            messages.map((m) => {
              const isUser = m.role === 'user';
              return (
                <div
                  key={m.id}
                  className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}
                >
                  <div
                    className={`max-w-[85%] rounded-2xl p-3 text-xs leading-relaxed ${
                      isUser
                        ? 'bg-slate-900 text-white rounded-tr-xs'
                        : 'bg-slate-50 border border-slate-200/90 text-slate-900 rounded-tl-xs'
                    }`}
                  >
                    {/* Tool Badge if assistant used internal tools */}
                    {!isUser && m.toolsUsed && m.toolsUsed.length > 0 && (
                      <div className="mb-2 pb-1.5 border-b border-slate-200/60 flex items-center gap-1.5 text-[10px] text-sky-700 font-semibold">
                        <Check className="w-3 h-3 text-sky-600 shrink-0" />
                        <span>Đối chiếu dữ liệu: {m.toolsUsed.join(', ')}</span>
                      </div>
                    )}

                    <div className="whitespace-pre-wrap">{m.content}</div>

                    {/* Render Artifact Cards if any */}
                    {!isUser && m.artifacts && m.artifacts.length > 0 && (
                      <div className="flex flex-col gap-2 mt-2">
                        {m.artifacts.map((art, aIdx) => (
                          <CopilotArtifactCard key={aIdx} artifact={art} />
                        ))}
                      </div>
                    )}
                  </div>
                  <span className="text-[9px] text-slate-400 mt-1 px-1 font-mono tabular-nums">
                    {new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              );
            })
          )}

          {/* Shimmer skeleton while generating */}
          {isLoading && (
            <div className="flex flex-col items-start gap-1">
              <div className="p-3.5 bg-slate-50 border border-slate-200/80 rounded-2xl rounded-tl-xs max-w-[80%] flex flex-col gap-2">
                <div className="flex items-center gap-2 text-xs text-sky-700 font-medium">
                  <span className="w-2 h-2 rounded-full bg-sky-500 animate-pulse" />
                  <span>Copilot đang tổng hợp số liệu vận hành...</span>
                </div>
                <div className="h-2.5 bg-slate-200 rounded animate-pulse w-48" />
                <div className="h-2.5 bg-slate-200 rounded animate-pulse w-32" />
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Dock */}
        <div className="p-3 border-t border-slate-200/80 bg-white shrink-0">
          <form onSubmit={handleSubmit} className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowAttachModal(true)}
                className="p-2 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer shrink-0"
                title="Đính kèm mã đơn hàng hoặc tour để hỏi AI"
              >
                <Paperclip className="w-4 h-4" />
              </button>

              <input
                ref={inputRef}
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={attachedContext ? `Hỏi về ${attachedContext.label}...` : "Hỏi doanh thu, đơn hàng, ca vắng..."}
                disabled={isLoading}
                className="flex-1 text-xs bg-slate-50 border border-slate-200/90 rounded-xl px-3 py-2.5 text-slate-900 focus:outline-none focus:ring-1 focus:ring-sky-500 focus:border-sky-500 placeholder-slate-400 transition-all"
              />

              <button
                type="submit"
                disabled={!input.trim() || isLoading}
                className="p-2.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
                title="Gửi câu hỏi"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>
        </div>

      </aside>

      {/* Attach Modal Popup */}
      {showAttachModal && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-xl max-w-sm w-full p-5 shadow-xl border border-slate-200 flex flex-col gap-4 text-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h4 className="font-bold text-slate-900 text-sm">Ghim ngữ cảnh đối tượng</h4>
              <button onClick={() => setShowAttachModal(false)} className="text-slate-400 hover:text-slate-700">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex flex-col gap-2">
              <label className="font-medium text-slate-700">Loại đối tượng:</label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setAttachType('ORDER')}
                  className={`p-2 rounded-lg border text-center font-semibold transition-colors cursor-pointer ${
                    attachType === 'ORDER' ? 'bg-sky-50 border-sky-500 text-sky-700' : 'bg-slate-50 border-slate-200 text-slate-600'
                  }`}
                >
                  Đơn hàng (ORDER)
                </button>
                <button
                  type="button"
                  onClick={() => setAttachType('SERVICE')}
                  className={`p-2 rounded-lg border text-center font-semibold transition-colors cursor-pointer ${
                    attachType === 'SERVICE' ? 'bg-sky-50 border-sky-500 text-sky-700' : 'bg-slate-50 border-slate-200 text-slate-600'
                  }`}
                >
                  Dịch vụ (SERVICE)
                </button>
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="font-medium text-slate-700">Mã ID hoặc UUID:</label>
              <input
                type="text"
                value={attachIdInput}
                onChange={(e) => setAttachIdInput(e.target.value)}
                placeholder="Nhập mã đơn hoặc ID..."
                className="w-full text-xs p-2 rounded-lg border border-slate-200 focus:outline-none focus:ring-1 focus:ring-sky-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setShowAttachModal(false)}
                className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600 font-medium hover:bg-slate-200"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleApplyAttach}
                disabled={!attachIdInput.trim()}
                className="px-3 py-1.5 rounded-lg bg-slate-900 text-white font-medium hover:bg-slate-800 disabled:opacity-40"
              >
                Ghim ngữ cảnh
              </button>
            </div>
          </div>
        </div>
      )}
    </div>,
    document.body
  );
}
