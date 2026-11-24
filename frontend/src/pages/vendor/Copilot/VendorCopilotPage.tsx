import { useState, useRef, useEffect } from 'react';
import { 
  Sparkles, 
  Plus, 
  Pin, 
  Trash2, 
  MoreVertical
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { CopilotArtifactCard } from '../../../components/vendor/copilot/CopilotArtifactCard';
import { ContextPreviewCard } from '../../../components/vendor/copilot/ContextPreviewCard';
import { MarkdownText } from '../../../components/common/MarkdownText';
import { VendorCopilotActionDeck } from '../../../components/vendor/copilot/VendorCopilotActionDeck';
import { formatTextWithDisplayCodes } from '../../../utils/displayCode';

export function VendorCopilotPage() {
  const {
    activeConversationId,
    conversations,
    messages,
    isLoading,
    isSessionsLoading,
    loadConversations,
    selectConversation,
    startNewConversation,
    deleteConversation,
    togglePinConversation,
  } = useVendorCopilotStore();

  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
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

  return (
    <div className="flex h-full w-full bg-slate-50 overflow-hidden min-h-0 flex-1">
      {/* Left Sidebar: Session Threads */}
      <div className="w-72 bg-white border-r border-slate-200/80 flex flex-col shrink-0 h-full min-h-0">
        <div className="p-4 border-b border-slate-200/80 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-sky-600" strokeWidth={1.75} />
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
                    <span className="truncate">{formatTextWithDisplayCodes(c.title) || 'Đoạn chat không tên'}</span>
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
        </div>

        {/* Message Thread - Full Width Scroll Container with Flush Scrollbar */}
        <div className="flex-1 overflow-y-auto min-h-0 w-full overscroll-contain">
          <div className="w-full p-6 flex flex-col gap-5 min-h-full">
          {messages.length === 0 ? (
            <div className="my-auto flex flex-col items-center justify-center py-12 text-center select-none">
              <div className="w-12 h-12 rounded-2xl bg-sky-50 border border-sky-100 flex items-center justify-center text-sky-600 mb-3 shadow-2xs">
                <Sparkles className="w-6 h-6" strokeWidth={1.75} />
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
                  <div className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} max-w-[85%]`}>
                    {isUser && m.context && (
                      <div className="mb-2 self-end w-full flex justify-end">
                        <ContextPreviewCard context={m.context} />
                      </div>
                    )}

                    <div
                      className={`w-full rounded-2xl p-4 text-xs leading-relaxed ${
                        isUser
                          ? 'bg-slate-900 text-white rounded-tr-xs'
                          : 'bg-slate-50 border border-slate-200/90 text-slate-900 rounded-tl-xs shadow-2xs'
                      }`}
                    >
                      {isUser ? (
                        <div className="whitespace-pre-wrap">{formatTextWithDisplayCodes(m.content)}</div>
                      ) : (
                        <MarkdownText content={m.content} />
                      )}

                      {!isUser && m.artifacts && m.artifacts.length > 0 && (
                        <div className="flex flex-col gap-2 mt-3">
                          {m.artifacts.map((art, aIdx) => (
                            <CopilotArtifactCard key={aIdx} artifact={art} />
                          ))}
                        </div>
                      )}
                    </div>
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
          <VendorCopilotActionDeck />
        </div>
      </div>
    </div>
  );
}
