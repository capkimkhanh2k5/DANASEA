import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Sparkles,
  Plus,
  Send,
  Pin,
  Trash2,
  Archive,
  Paperclip,
  X,
  RotateCcw,
  Wrench,
  CheckCircle2,
  Clock,
  Edit2,
  Check,
  TrendingUp,
  Building2,
  ShieldAlert,
  ShoppingBag,
  Scale,
  Copy,
} from 'lucide-react';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import { AdminButton } from '../../../components/admin/AdminWorkspace';
import { formatDisplayCode, formatTextWithDisplayCodes } from '../../../utils/displayCode';
import { adminAiService, getAdminAiError } from '../../../services/adminAiService';
import type {
  CopilotConversation,
  CopilotMessage,
  CopilotContextType,
  CopilotArtifact,
} from '../../../types/adminAi';
import { parseArtifactsJson } from './copilotArtifactHelpers';
import { CopilotArtifactRenderer } from './CopilotArtifactRenderer';
import { MarkdownText } from '../../../components/common/MarkdownText';
import { AdminCopilotContextPicker, type ContextSummary } from './AdminCopilotContextPicker';
import { useAuthStore } from '../../../store/useAuthStore';
import {
  getStoredPinnedSession,
  savePinnedSession,
  clearPinnedSessionStorage,
} from './adminCopilotSession';
import './AdminCopilot.css';

let localMessageSequence = 0;
function generateLocalId(prefix: string): string {
  localMessageSequence += 1;
  return `${prefix}-${localMessageSequence}`;
}

const ADMIN_CONTEXT_META: Record<string, { label: string; prefix: string }> = {
  DISPUTE: { label: 'Hồ sơ khiếu nại', prefix: 'DISP' },
  ORDER: { label: 'Đơn hàng', prefix: 'ORD' },
  VENDOR: { label: 'Đối tác', prefix: 'VND' },
  SERVICE: { label: 'Dịch vụ', prefix: 'SRV' },
};

function formatAdminContextLabel(type: string, id: string, customLabel?: string | null): string {
  if (customLabel && customLabel.trim()) return customLabel.trim();
  const meta = ADMIN_CONTEXT_META[type.toUpperCase()] || { label: type, prefix: 'CTX' };
  return `${meta.label} #${formatDisplayCode(id, meta.prefix)}`;
}

interface LocalMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  contextType?: string | null;
  contextId?: string | null;
  contextLabel?: string | null;
  artifacts?: CopilotArtifact[];
  toolsUsed?: string[];
  verified?: boolean;
  createdAt: string;
  isError?: boolean;
  canRetry?: boolean;
  originalPayload?: {
    message: string;
    contextType?: string | null;
    contextId?: string | null;
    contextLabel?: string | null;
  };
}

export const AdminCopilot: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  // Sidebar state
  const [conversations, setConversations] = useState<CopilotConversation[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [showArchived, setShowArchived] = useState(false);
  const [editingTitleId, setEditingTitleId] = useState<string | null>(null);
  const [editingTitleValue, setEditingTitleValue] = useState('');
  const [conversationToDelete, setConversationToDelete] = useState<CopilotConversation | null>(null);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(() => typeof window !== 'undefined' && window.innerWidth <= 768);

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth <= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  // Active work session pinning (sessionStorage)
  const [isSessionPinned, setIsSessionPinned] = useState<boolean>(() => {
    return !!getStoredPinnedSession()?.isPinned;
  });

  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const savedScrollTopRef = useRef<number | null>(
    getStoredPinnedSession()?.scrollTop ?? null
  );
  const scrollTopRef = useRef<number>(0);

  // Active session state initialized with pinned session if available
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(() => {
    return getStoredPinnedSession()?.conversationId ?? null;
  });
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [inputText, setInputText] = useState<string>(() => {
    return getStoredPinnedSession()?.inputText ?? '';
  });

  // Context attachment state initialized with pinned session if available
  const [contextType, setContextType] = useState<CopilotContextType | null>(() => {
    return getStoredPinnedSession()?.contextType ?? null;
  });
  const [contextId, setContextId] = useState<string | null>(() => {
    return getStoredPinnedSession()?.contextId ?? null;
  });
  const [pinnedSummary, setPinnedSummary] = useState<ContextSummary | null>(() => {
    return getStoredPinnedSession()?.pinnedSummary ?? null;
  });
  const [showAttachPicker, setShowAttachPicker] = useState(false);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Helpers to manage pinned work session
  const clearPinnedSession = useCallback(() => {
    setIsSessionPinned(false);
    savedScrollTopRef.current = null;
    clearPinnedSessionStorage();
  }, []);





  // Keep pinned session updated in sessionStorage whenever relevant state changes
  useEffect(() => {
    if (!isSessionPinned) return;
    savePinnedSession({
      isPinned: true,
      conversationId: currentConversationId,
      inputText,
      contextType,
      contextId,
      pinnedSummary,
      scrollTop: scrollTopRef.current,
    });
  }, [isSessionPinned, currentConversationId, inputText, contextType, contextId, pinnedSummary]);

  // Clear session if logged out
  useEffect(() => {
    if (!isAuthenticated) {
      clearPinnedSession();
    }
  }, [isAuthenticated, clearPinnedSession]);

  // Auto-resize textarea when text is updated
  useEffect(() => {
    if (textareaRef.current && inputText) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 140)}px`;
    }
  }, [inputText]);



  // Initialize context from query params if provided
  useEffect(() => {
    const qType = searchParams.get('contextType') as CopilotContextType | null;
    const qId = searchParams.get('contextId');
    if (qType && qId) {
      setContextType(qType);
      setContextId(qId);
      const disp = formatDisplayCode(qId, qType === 'DISPUTE' ? 'DISP' : 'ORD');
      setPinnedSummary({
        type: qType === 'DISPUTE' ? 'DISPUTE' : 'ORDER',
        id: qId,
        displayCode: disp,
        title: `${qType === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'} #${disp}`,
      });
    }
  }, [searchParams]);

  // Load conversations list
  const loadConversations = useCallback(async () => {
    setLoadingList(true);
    try {
      const data = await adminAiService.listConversations({ archived: showArchived, limit: 30 });
      // Sort pinned first, then updated recently
      const sorted = [...data].sort((a, b) => {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
        return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
      });
      setConversations(sorted);
    } catch {
      setConversations([]);
    } finally {
      setLoadingList(false);
    }
  }, [showArchived]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  // Load conversation messages history when active conversation changes
  useEffect(() => {
    if (!currentConversationId) {
      setMessages([]);
      return;
    }

    let isMounted = true;
    (async () => {
      setLoadingHistory(true);
      try {
        const history = await adminAiService.getConversationHistory(currentConversationId);
        if (!isMounted) return;
        const mapped: LocalMessage[] = history.map((m: CopilotMessage) => ({
          id: m.id,
          role: (m.role || '').toLowerCase() === 'assistant' ? 'assistant' : 'user',
          content: m.content,
          contextType: m.contextType ?? null,
          contextId: m.contextId ?? null,
          contextLabel: m.contextLabel ?? null,
          artifacts: parseArtifactsJson(m.artifactsJson),
          toolsUsed: m.toolsUsed ? m.toolsUsed.split(',').map((t: string) => t.trim()).filter(Boolean) : undefined,
          verified: m.verified,
          createdAt: m.createdAt,
        }));
        setMessages(mapped);

        // Restore scroll position if previously saved for pinned session
        if (savedScrollTopRef.current !== null && savedScrollTopRef.current > 0) {
          requestAnimationFrame(() => {
            if (messagesContainerRef.current && savedScrollTopRef.current !== null) {
              messagesContainerRef.current.scrollTop = savedScrollTopRef.current;
              savedScrollTopRef.current = null;
            }
          });
        }
      } catch (err) {
        if (!isMounted) return;
        setMessages([
          {
            id: generateLocalId('err'),
            role: 'assistant',
            content: `Không thể tải lịch sử hội thoại: ${getAdminAiError(err)}`,
            createdAt: new Date().toISOString(),
            isError: true,
          },
        ]);
      } finally {
        if (isMounted) setLoadingHistory(false);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [currentConversationId]);

  // Auto scroll to bottom of messages
  useEffect(() => {
    if (savedScrollTopRef.current !== null && savedScrollTopRef.current > 0) {
      return;
    }
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  // Start new chat
  const handleStartNewChat = () => {
    clearPinnedSession();
    setCurrentConversationId(null);
    setMessages([]);
    setInputText('');
    setSearchParams({});
    setContextType(null);
    setContextId(null);
    setPinnedSummary(null);
    setMobileSidebarOpen(false);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.focus();
    }
  };

  // Toggle pin
  const handleTogglePin = async (c: CopilotConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await adminAiService.updateConversation(c.id, { pinned: !c.pinned });
      loadConversations();
    } catch (err) {
      alert(getAdminAiError(err));
    }
  };

  // Toggle archive
  const handleToggleArchive = async (c: CopilotConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await adminAiService.updateConversation(c.id, { archived: !c.archived });
      loadConversations();
      if (currentConversationId === c.id) {
        handleStartNewChat();
      }
    } catch (err) {
      alert(getAdminAiError(err));
    }
  };

  // Delete conversation
  const handleDeleteConversation = (c: CopilotConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    setConversationToDelete(c);
  };

  const handleConfirmDelete = async () => {
    if (!conversationToDelete) return;
    try {
      await adminAiService.deleteConversation(conversationToDelete.id);
      if (currentConversationId === conversationToDelete.id) {
        handleStartNewChat();
      }
      setConversationToDelete(null);
      loadConversations();
    } catch (err) {
      alert(getAdminAiError(err));
    }
  };

  // Rename conversation
  const handleStartRename = (c: CopilotConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingTitleId(c.id);
    setEditingTitleValue(c.title);
  };

  const handleSaveRename = async (id: string) => {
    if (!editingTitleValue.trim()) return;
    try {
      await adminAiService.updateConversation(id, { title: editingTitleValue.trim() });
      setEditingTitleId(null);
      loadConversations();
    } catch (err) {
      alert(getAdminAiError(err));
    }
  };

  // Auto-grow textarea handler
  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 140)}px`;
    }
  };

  // Send message
  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputText).trim();
    if (!text || sending) return;

    const effectiveLabel = contextType && contextId
      ? formatAdminContextLabel(
          contextType,
          contextId,
          pinnedSummary?.title
            ? `${pinnedSummary.title} (#${pinnedSummary.displayCode})`
            : pinnedSummary?.displayCode
              ? `${(ADMIN_CONTEXT_META[contextType]?.label || contextType)} #${pinnedSummary.displayCode}`
              : null
        )
      : undefined;

    const userMsg: LocalMessage = {
      id: generateLocalId('usr'),
      role: 'user',
      content: text,
      contextType: contextType || null,
      contextId: contextId || null,
      contextLabel: effectiveLabel || null,
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputText('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
    setSending(true);

    const payload = {
      conversationId: currentConversationId,
      message: text,
      contextType: contextType || undefined,
      contextId: contextId || undefined,
      contextLabel: effectiveLabel,
    };

    try {
      const res = await adminAiService.chatCopilot(payload);

      if (!currentConversationId && res.conversationId) {
        setCurrentConversationId(res.conversationId);
      }

      const assistantMsg: LocalMessage = {
        id: generateLocalId('ast'),
        role: 'assistant',
        content: res.message,
        artifacts: Array.isArray(res.artifacts) && res.artifacts.length > 0 ? res.artifacts : undefined,
        toolsUsed: res.toolsUsed,
        verified: res.generatedTextVerified,
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, assistantMsg]);
      loadConversations();
    } catch (err) {
      const errorMsg = getAdminAiError(err);
      const errorBubble: LocalMessage = {
        id: generateLocalId('err'),
        role: 'assistant',
        content: `Lỗi kết nối DaNaBot: ${errorMsg}`,
        createdAt: new Date().toISOString(),
        isError: true,
        canRetry: true,
        originalPayload: payload,
      };
      setMessages((prev) => [...prev, errorBubble]);
    } finally {
      setSending(false);
    }
  };

  // Manual retry handler
  const handleManualRetry = (failedMsg: LocalMessage) => {
    if (!failedMsg.originalPayload) return;
    handleSendMessage(failedMsg.originalPayload.message);
  };

  // Context attachment selection handler
  const handleSelectContext = (type: 'ORDER' | 'DISPUTE', id: string, summary?: ContextSummary) => {
    setContextType(type);
    setContextId(id);
    const disp = formatDisplayCode(id, type === 'DISPUTE' ? 'DISP' : 'ORD');
    setPinnedSummary(
      summary || {
        type,
        id,
        displayCode: disp,
        title: `${type === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'} #${disp}`,
      }
    );
    textareaRef.current?.focus();
  };

  const handleRemoveAttachment = () => {
    setContextType(null);
    setContextId(null);
    setPinnedSummary(null);
  };

  const handleCopyCode = (code: string, e?: React.MouseEvent) => {
    e?.stopPropagation();
    const clean = code.replace(/^#/, '');
    navigator.clipboard.writeText(clean);
    setCopiedCode(clean);
    setTimeout(() => {
      setCopiedCode((prev) => (prev === clean ? null : prev));
    }, 2000);
  };

  return (
    <div className="admin-copilot">
      {/* Mobile Sidebar Overlay Backdrop */}
      {mobileSidebarOpen && (
        <div
          className="admin-copilot__mobile-backdrop md:hidden"
          onClick={() => setMobileSidebarOpen(false)}
        />
      )}

      {/* ==================== LEFT SIDEBAR: SESSIONS ==================== */}
      <aside
        className={`admin-copilot__sidebar ${
          mobileSidebarOpen ? 'admin-copilot__sidebar--mobile-open' : ''
        }`}
        aria-label="Danh sách hội thoại DaNaBot"
      >
        <div className="admin-copilot__sidebar-header">
          <button
            type="button"
            className="admin-copilot__new-chat-btn"
            onClick={handleStartNewChat}
          >
            <Plus size={15} />
            <span>Đoạn chat mới</span>
          </button>

          <div className="admin-copilot__filter-tabs">
            <button
              type="button"
              className={`admin-copilot__filter-tab ${!showArchived ? 'admin-copilot__filter-tab--active' : ''}`}
              onClick={() => setShowArchived(false)}
            >
              Đang mở
            </button>
            <button
              type="button"
              className={`admin-copilot__filter-tab ${showArchived ? 'admin-copilot__filter-tab--active' : ''}`}
              onClick={() => setShowArchived(true)}
            >
              Lưu trữ
            </button>
          </div>
        </div>

        <div className="admin-copilot__session-list" role="list">
          {loadingList ? (
            <div className="admin-copilot__session-feedback">
              Đang tải danh sách...
            </div>
          ) : conversations.length === 0 ? (
            <div className="admin-copilot__session-feedback admin-copilot__session-feedback--empty">
              {showArchived ? 'Không có phiên lưu trữ nào.' : 'Chưa có phiên chat nào.'}
            </div>
          ) : (
            conversations.map((c) => {
              const isActive = c.id === currentConversationId;
              const isEditing = editingTitleId === c.id;

              return (
                <div
                  key={c.id}
                  role="listitem"
                  className={`admin-copilot__session-item ${isActive ? 'admin-copilot__session-item--active' : ''}`}
                  onClick={() => {
                    if (c.id !== currentConversationId) {
                      clearPinnedSession();
                      setCurrentConversationId(c.id);
                      setInputText('');
                      setPinnedSummary(null);
                      setContextType(null);
                      setContextId(null);
                    }
                    setMobileSidebarOpen(false);
                  }}
                >
                  <div className="admin-copilot__session-main">
                    {isEditing ? (
                      <div
                        className="admin-copilot__rename-row"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <input
                          type="text"
                          value={editingTitleValue}
                          onChange={(e) => setEditingTitleValue(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') handleSaveRename(c.id);
                            if (e.key === 'Escape') setEditingTitleId(null);
                          }}
                          autoFocus
                          className="admin-copilot__rename-input"
                        />
                        <button
                          type="button"
                          className="admin-copilot__icon-btn"
                          onClick={() => handleSaveRename(c.id)}
                          title="Lưu tên"
                        >
                          <Check size={12} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <div className="admin-copilot__session-title" title={c.title}>
                          {c.pinned && <Pin size={11} className="admin-copilot__pin-icon" />}
                          <span>{formatTextWithDisplayCodes(c.title) || 'Đoạn chat mới'}</span>
                        </div>
                        <div className="admin-copilot__session-time">
                          {new Date(c.updatedAt).toLocaleDateString('vi-VN', {
                            day: '2-digit',
                            month: '2-digit',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </div>
                      </>
                    )}
                  </div>

                  {!isEditing && (
                    <div className="admin-copilot__session-actions">
                      <button
                        type="button"
                        title={c.pinned ? 'Bỏ ghim' : 'Ghim lên đầu'}
                        className={`admin-copilot__icon-btn ${c.pinned ? 'admin-copilot__icon-btn--active' : ''}`}
                        onClick={(e) => handleTogglePin(c, e)}
                      >
                        <Pin size={12} />
                      </button>
                      <button
                        type="button"
                        title="Đổi tên"
                        className="admin-copilot__icon-btn"
                        onClick={(e) => handleStartRename(c, e)}
                      >
                        <Edit2 size={12} />
                      </button>
                      <button
                        type="button"
                        title={c.archived ? 'Bỏ lưu trữ' : 'Lưu trữ'}
                        className="admin-copilot__icon-btn"
                        onClick={(e) => handleToggleArchive(c, e)}
                      >
                        <Archive size={12} />
                      </button>
                      <button
                        type="button"
                        title="Xóa đoạn chat"
                        className="admin-copilot__icon-btn admin-copilot__icon-btn--danger"
                        onClick={(e) => handleDeleteConversation(c, e)}
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </aside>

      {/* ==================== RIGHT: MAIN CHAT AREA ==================== */}
      <main className="admin-copilot__main">
        {/* Top Header */}
        <header className="admin-copilot__topbar">
          <div className="admin-copilot__topbar-left">
            <div className="admin-copilot__topbar-icon">
              <Sparkles size={15} />
            </div>
            <span className="admin-copilot__topbar-title">
              {formatTextWithDisplayCodes(conversations.find((c) => c.id === currentConversationId)?.title || 'Admin DaNaBot')}
            </span>
          </div>

        </header>

        {/* Message Stream */}
        <div
          ref={messagesContainerRef}
          className="admin-copilot__messages"
          role="log"
          aria-live="polite"
        >
          {loadingHistory ? (
            <div className="admin-copilot__history-loading">
              <Clock size={16} className="animate-spin" />
              <span>Đang tải lịch sử tin nhắn...</span>
            </div>
          ) : messages.length === 0 ? (
            <div className="admin-copilot__empty">
              <h2 className="admin-copilot__empty-title">Bạn muốn tra cứu thông tin gì?</h2>
              <div className="admin-copilot__prompts">
                <button
                  type="button"
                  className="admin-copilot__prompt-btn"
                  onClick={() => handleSendMessage('Tổng hợp doanh thu và hoa hồng.')}
                >
                  <TrendingUp size={15} className="admin-copilot__prompt-icon" />
                  <span>Tổng hợp doanh thu và hoa hồng.</span>
                </button>
                <button
                  type="button"
                  className="admin-copilot__prompt-btn"
                  onClick={() => handleSendMessage('Xem tình hình hoạt động đối tác.')}
                >
                  <Building2 size={15} className="admin-copilot__prompt-icon" />
                  <span>Xem tình hình hoạt động đối tác.</span>
                </button>
                <button
                  type="button"
                  className="admin-copilot__prompt-btn"
                  onClick={() => handleSendMessage('Tra cứu đơn hàng và khiếu nại.')}
                >
                  <ShieldAlert size={15} className="admin-copilot__prompt-icon" />
                  <span>Tra cứu đơn hàng và khiếu nại.</span>
                </button>
              </div>
            </div>
          ) : (
            messages.map((m) => (
              <div
                key={m.id}
                className={`admin-copilot__msg ${
                  m.role === 'user' ? 'admin-copilot__msg--user' : 'admin-copilot__msg--assistant'
                } ${m.artifacts && m.artifacts.length > 0 ? 'admin-copilot__msg--with-artifact' : ''}`}
              >
                <div
                  className={`admin-copilot__msg-avatar ${
                    m.role === 'user'
                      ? 'admin-copilot__msg-avatar--user'
                      : 'admin-copilot__msg-avatar--assistant'
                  }`}
                >
                  {m.role === 'user' ? 'A' : <Sparkles size={15} />}
                </div>

                <div className="admin-copilot__msg-body">
                  {m.role === 'user' && m.contextType && m.contextId && (
                    <div className="admin-copilot__msg-context">
                      <Paperclip size={11} />
                      <span>{formatAdminContextLabel(m.contextType, m.contextId, m.contextLabel)}</span>
                    </div>
                  )}

                  <div
                    className={`admin-copilot__msg-content ${
                      m.isError ? 'admin-copilot__msg-content--error' : ''
                    }`}
                  >
                    {m.role === 'user' ? (
                      formatTextWithDisplayCodes(m.content)
                    ) : (
                      <MarkdownText content={m.content} />
                    )}

                    {m.isError && m.canRetry && (
                      <div style={{ marginTop: '8px' }}>
                        <button
                          type="button"
                          onClick={() => handleManualRetry(m)}
                          className="admin-copilot__retry-btn"
                        >
                          <RotateCcw size={11} /> Thử gửi lại
                        </button>
                      </div>
                    )}
                  </div>

                  {/* ARTIFACTS: System Data separated from AI response */}
                  {m.artifacts && m.artifacts.length > 0 && (
                    <CopilotArtifactRenderer artifacts={m.artifacts} />
                  )}

                  {m.toolsUsed && m.toolsUsed.length > 0 && (
                    <div className="admin-copilot__msg-tools">
                      {m.toolsUsed.map((tool, idx) => (
                        <span key={idx} className="admin-copilot__tool-tag">
                          <Wrench size={10} />
                          <span>{tool}</span>
                        </span>
                      ))}
                      {m.verified && (
                        <span className="admin-copilot__tool-tag admin-copilot__tool-tag--verified">
                          <CheckCircle2 size={10} /> Đã kiểm chứng
                        </span>
                      )}
                    </div>
                  )}

                  <div className="admin-copilot__msg-meta">
                    {new Date(m.createdAt).toLocaleTimeString('vi-VN', {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </div>
                </div>
              </div>
            ))
          )}

          {sending && (
            <div className="admin-copilot__msg admin-copilot__msg--assistant">
              <div className="admin-copilot__msg-avatar admin-copilot__msg-avatar--assistant">
                <Sparkles size={15} />
              </div>
              <div className="admin-copilot__msg-body">
                <div className="admin-copilot__msg-content admin-copilot__loading-content">
                  <div className="admin-copilot__loading-spinner" />
                  <span>DaNaBot đang phân tích và tra cứu dữ liệu...</span>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* ==================== REDESIGNED MESSENGER-STYLE FOOTER COMPOSER ==================== */}
        <footer className="admin-copilot__footer">
          {/* Pinned Context Deck (Chip above textarea) */}
          {contextType && contextId && (
            <div className="admin-copilot__context-deck">
              <div className="admin-copilot__context-deck-main">
                <div className="admin-copilot__context-deck-icon">
                  {contextType === 'DISPUTE' ? <Scale size={13} /> : <ShoppingBag size={13} />}
                </div>
                <div className="admin-copilot__context-deck-info">
                  <span className="admin-copilot__context-deck-title">
                    {contextType === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'}:{' '}
                    <strong>#{pinnedSummary?.displayCode || formatDisplayCode(contextId, contextType === 'DISPUTE' ? 'DISP' : 'ORD')}</strong>
                  </span>
                  {pinnedSummary?.subtitle && (
                    <span className="admin-copilot__context-deck-sub">{pinnedSummary.subtitle}</span>
                  )}
                </div>
              </div>

              <div className="admin-copilot__context-deck-actions">
                <button
                  type="button"
                  className="admin-copilot__context-action-btn"
                  onClick={(e) =>
                    handleCopyCode(
                      pinnedSummary?.displayCode || formatDisplayCode(contextId, contextType === 'DISPUTE' ? 'DISP' : 'ORD'),
                      e
                    )
                  }
                  title="Sao chép mã nghiệp vụ"
                >
                  {copiedCode ===
                  (pinnedSummary?.displayCode || formatDisplayCode(contextId, contextType === 'DISPUTE' ? 'DISP' : 'ORD')) ? (
                    <>
                      <Check size={11} className="text-emerald-600" />
                      <span>Đã chép</span>
                    </>
                  ) : (
                    <>
                      <Copy size={11} />
                      <span>Chép mã</span>
                    </>
                  )}
                </button>
                <button
                  type="button"
                  className="admin-copilot__context-action-btn"
                  onClick={() => setShowAttachPicker(true)}
                  title="Thay đổi đối tượng"
                >
                  <Paperclip size={11} />
                  <span>Đổi</span>
                </button>
                <button
                  type="button"
                  className="admin-copilot__context-close-btn"
                  onClick={handleRemoveAttachment}
                  title="Gỡ ngữ cảnh"
                >
                  <X size={13} />
                </button>
              </div>
            </div>
          )}

          {/* Composer Input Bar */}
          <div className="admin-copilot__composer-bar">
            {/* Attachment Button */}
            <button
              type="button"
              className={`admin-copilot__composer-attach-btn ${
                contextType && contextId ? 'admin-copilot__composer-attach-btn--active' : ''
              }`}
              onClick={() => setShowAttachPicker(true)}
              title={
                contextType && contextId
                  ? 'Ngữ cảnh đã ghim (Bấm để thay đổi)'
                  : 'Ghim ngữ cảnh đối tượng (Đơn hàng / Khiếu nại)'
              }
              aria-label="Đính kèm ngữ cảnh đối tượng"
            >
              <Paperclip size={17} />
            </button>

            {/* Input Textarea */}
            <textarea
              ref={textareaRef}
              rows={1}
              value={inputText}
              onChange={handleInputChange}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendMessage();
                }
              }}
              placeholder={
                isMobile
                  ? 'Nhập câu hỏi cho DaNaBot...'
                  : 'Hỏi DaNaBot về doanh thu, khiếu nại, đối tác hoặc số liệu sàn... (Enter để gửi, Shift+Enter xuống dòng)'
              }
              className="admin-copilot__composer-textarea"
              disabled={sending}
            />

            {/* Send Button */}
            <button
              type="button"
              className="admin-copilot__composer-send-btn"
              onClick={() => handleSendMessage()}
              disabled={!inputText.trim() || sending}
              aria-label="Gửi tin nhắn"
            >
              {sending ? (
                <div className="admin-copilot__send-spinner" />
              ) : (
                <>
                  <Send size={14} />
                  <span className="hidden sm:inline">Gửi</span>
                </>
              )}
            </button>
          </div>
        </footer>
      </main>

      {/* Delete Confirmation Modal */}
      <AdminDialog
        open={Boolean(conversationToDelete)}
        onClose={() => setConversationToDelete(null)}
        variant="modal"
        title="Xác nhận xóa cuộc trò chuyện"
        description="Lịch sử phân tích và các câu hỏi trong phiên này sẽ bị xóa khỏi danh sách."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setConversationToDelete(null)}>Hủy</AdminButton>
            <AdminButton
              variant="danger"
              onClick={handleConfirmDelete}
            >
              Xóa cuộc trò chuyện
            </AdminButton>
          </div>
        }
      >
        <p style={{ margin: 0, fontSize: 13, color: 'var(--admin-body, #334155)', lineHeight: 1.6 }}>
          Bạn có chắc chắn muốn xóa cuộc trò chuyện <strong style={{ color: 'var(--admin-ink, #0f172a)' }}>"{conversationToDelete?.title}"</strong>?
        </p>
      </AdminDialog>

      {/* Context Attachment Window / Popover */}
      <AdminCopilotContextPicker
        open={showAttachPicker}
        onClose={() => setShowAttachPicker(false)}
        onSelectContext={handleSelectContext}
        currentContextType={contextType === 'DISPUTE' ? 'DISPUTE' : 'ORDER'}
        currentContextId={contextId}
      />
    </div>
  );
};
