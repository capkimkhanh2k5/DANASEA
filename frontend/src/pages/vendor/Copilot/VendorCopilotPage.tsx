import { useState, useRef, useEffect } from 'react';
import { 
  Bot, 
  Plus, 
  Send, 
  Pin, 
  Trash2, 
  MoreVertical,
  Paperclip, 
  X, 
  Check 
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { CopilotArtifactCard } from '../../../components/vendor/copilot/CopilotArtifactCard';

export function VendorCopilotPage() {
  const {
    activeConversationId,
    conversations,
    messages,
    isLoading,
    isSessionsLoading,
    attachedContext,
    clearAttachedContext,
    setAttachedContext,
    loadConversations,
    selectConversation,
    startNewConversation,
    sendMessage,
    deleteConversation,
    togglePinConversation,
  } = useVendorCopilotStore();

  const [input, setInput] = useState('');
  const [showAttachModal, setShowAttachModal] = useState(false);
  const [attachType, setAttachType] = useState<'ORDER' | 'SERVICE'>('ORDER');
  const [attachIdInput, setAttachIdInput] = useState('');
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setActiveMenuId(null);
      }
    };
    if (activeMenuId) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [activeMenuId]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

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

  return (
    <div className="flex h-full w-full bg-slate-50 overflow-hidden min-h-0 flex-1">
      {/* Left Sidebar: Session Threads */}
      <div className="w-72 bg-white border-r border-slate-200/80 flex flex-col shrink-0 h-full min-h-0">
        <div className="p-4 border-b border-slate-200/80 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2">
            <Bot className="w-4 h-4 text-sky-600" strokeWidth={1.75} />
            <span className="font-bold text-slate-900 text-sm">Hội thoại</span>
          </div>

          <button
            onClick={startNewConversation}
            className="p-1.5 px-2.5 rounded-lg bg-slate-900 text-white hover:bg-slate-800 transition-colors cursor-pointer flex items-center gap-1.5 text-xs font-semibold"
            title="Đoạn chat mới"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Mới</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-2.5 flex flex-col gap-1">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-2 py-1">
            Lịch sử hội thoại
          </span>

          {isSessionsLoading ? (
            <div className="p-4 text-center text-xs text-slate-400">Đang tải lịch sử...</div>
          ) : conversations.length === 0 ? (
            <div className="p-4 text-center text-xs text-slate-400">Chưa có phiên chat nào</div>
          ) : (
            conversations.map((c) => {
              const isActive = c.id === activeConversationId;
              return (
                <div
                  key={c.id}
                  onClick={() => selectConversation(c.id)}
                  className={`group flex items-center justify-between p-2.5 rounded-xl cursor-pointer text-xs transition-colors relative ${
                    isActive
                      ? 'bg-sky-50 border border-sky-200/80 text-sky-950 font-semibold shadow-2xs'
                      : 'hover:bg-slate-100 text-slate-700 border border-transparent'
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    {c.pinned && <Pin className="w-3 h-3 text-amber-500 fill-amber-500 shrink-0" />}
                    <span className="truncate">{c.title || 'Đoạn chat không tên'}</span>
                  </div>

                  <div className="flex items-center gap-1 ml-2 shrink-0">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMenuId(activeMenuId === c.id ? null : c.id);
                      }}
                      className={`p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 rounded-md transition-colors cursor-pointer ${
                        activeMenuId === c.id ? 'opacity-100 bg-slate-200/60 text-slate-700' : 'opacity-0 group-hover:opacity-100'
                      }`}
                      title="Tùy chọn"
                    >
                      <MoreVertical className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {activeMenuId === c.id && (
                    <div
                      ref={menuRef}
                      onClick={(e) => e.stopPropagation()}
                      className="absolute right-2 top-9 z-30 w-36 bg-white rounded-xl shadow-lg border border-slate-200/90 py-1 text-xs animate-in fade-in zoom-in-95 duration-100"
                    >
                      <button
                        type="button"
                        onClick={() => {
                          togglePinConversation(c.id, c.pinned);
                          setActiveMenuId(null);
                        }}
                        className="w-full px-3 py-2 text-left flex items-center gap-2 text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer font-normal"
                      >
                        <Pin className="w-3.5 h-3.5 text-slate-400" />
                        <span>{c.pinned ? 'Bỏ ghim' : 'Ghim lên đầu'}</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          deleteConversation(c.id);
                          setActiveMenuId(null);
                        }}
                        className="w-full px-3 py-2 text-left flex items-center gap-2 text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer font-normal"
                      >
                        <Trash2 className="w-3.5 h-3.5 text-rose-500" />
                        <span>Xóa hội thoại</span>
                      </button>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Main Chat Workspace */}
      <div className="flex-1 flex flex-col bg-white h-full min-h-0 min-w-0 overflow-hidden">
        {/* Header */}
        <div className="h-14 px-6 border-b border-slate-200/80 flex items-center justify-between shrink-0 bg-white sticky top-0 z-10">
          <div className="flex items-center gap-2.5">
            <span className="font-bold text-sm text-slate-900">DANASEA Copilot</span>
          </div>

          {attachedContext && (
            <div className="flex items-center gap-2 px-3 py-1 bg-sky-50 rounded-lg border border-sky-200 text-xs text-sky-900">
              <span className="font-medium">Ngữ cảnh: {attachedContext.label}</span>
              <button onClick={clearAttachedContext} className="hover:text-sky-700 cursor-pointer">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        </div>

        {/* Message Thread - Full Width Scroll Container with Flush Scrollbar */}
        <div className="flex-1 overflow-y-auto min-h-0 w-full overscroll-contain">
          <div className="w-full p-6 flex flex-col gap-5 min-h-full">
          {messages.length === 0 ? (
            <div className="my-auto flex flex-col items-center justify-center py-12 text-center select-none">
              <div className="w-12 h-12 rounded-2xl bg-sky-50 border border-sky-100 flex items-center justify-center text-sky-600 mb-3 shadow-2xs">
                <Bot className="w-6 h-6" strokeWidth={1.75} />
              </div>
              <h3 className="font-bold text-base text-slate-800 tracking-tight">DANASEA Copilot</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-sm leading-relaxed">
                Trợ lý vận hành và đối soát dữ liệu gian hàng. Nhập câu hỏi bên dưới để bắt đầu hội thoại.
              </p>
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
                    className={`max-w-[80%] rounded-2xl p-4 text-xs leading-relaxed ${
                      isUser
                        ? 'bg-slate-900 text-white rounded-tr-xs'
                        : 'bg-slate-50 border border-slate-200/90 text-slate-900 rounded-tl-xs shadow-2xs'
                    }`}
                  >
                    {!isUser && m.toolsUsed && m.toolsUsed.length > 0 && (
                      <div className="mb-2 pb-1.5 border-b border-slate-200/60 flex items-center gap-1.5 text-[10px] text-sky-700 font-semibold">
                        <Check className="w-3.5 h-3.5 text-sky-600 shrink-0" />
                        <span>Đối chiếu dữ liệu: {m.toolsUsed.join(', ')}</span>
                      </div>
                    )}

                    <div className="whitespace-pre-wrap">{m.content}</div>

                    {!isUser && m.artifacts && m.artifacts.length > 0 && (
                      <div className="flex flex-col gap-2 mt-3">
                        {m.artifacts.map((art, aIdx) => (
                          <CopilotArtifactCard key={aIdx} artifact={art} />
                        ))}
                      </div>
                    )}
                  </div>
                  <span className="text-[10px] text-slate-400 mt-1 px-1 font-mono tabular-nums">
                    {new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              );
            })
          )}

          {isLoading && (
            <div className="flex flex-col items-start gap-1">
              <div className="p-4 bg-slate-50 border border-slate-200/80 rounded-2xl rounded-tl-xs max-w-sm flex flex-col gap-2 shadow-2xs">
                <div className="flex items-center gap-2 text-xs text-sky-700 font-medium">
                  <span className="w-2 h-2 rounded-full bg-sky-500 animate-pulse" />
                  <span>Copilot đang tổng hợp dữ liệu đối tác...</span>
                </div>
                <div className="h-2.5 bg-slate-200 rounded animate-pulse w-48" />
                <div className="h-2.5 bg-slate-200 rounded animate-pulse w-32" />
              </div>
            </div>
          )}

            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* Bottom Input Area - Fixed & Full Horizontal Width */}
        <div className="py-3 px-6 border-t border-slate-200/80 bg-white shrink-0 w-full sticky bottom-0 z-10">
          <form onSubmit={handleSubmit} className="flex flex-col gap-2 w-full">
            <div className="flex items-center gap-2.5 w-full">
              <button
                type="button"
                onClick={() => setShowAttachModal(true)}
                className="p-2.5 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 border border-slate-200/80 transition-colors cursor-pointer shrink-0"
                title="Đính kèm mã đơn hàng hoặc tour để hỏi AI"
              >
                <Paperclip className="w-4 h-4" />
              </button>

              <input
                ref={inputRef}
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={attachedContext ? `Hỏi về ${attachedContext.label}...` : "Hỏi doanh thu, đơn hàng hôm nay, ca tour vắng..."}
                disabled={isLoading}
                className="flex-1 text-xs bg-slate-50 border border-slate-200/90 rounded-xl px-4 py-3 text-slate-900 focus:outline-none focus:ring-1 focus:ring-sky-500 focus:border-sky-500 placeholder-slate-400 transition-all shadow-2xs"
              />

              <button
                type="submit"
                disabled={!input.trim() || isLoading}
                className="p-3 rounded-xl bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0 shadow-xs"
                title="Gửi câu hỏi"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Attach Context Modal */}
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
    </div>
  );
}
