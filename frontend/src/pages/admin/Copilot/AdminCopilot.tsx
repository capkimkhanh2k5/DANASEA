import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Bot,
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
} from 'lucide-react';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import { AdminButton } from '../../../components/admin/AdminWorkspace';
import { adminAiService, getAdminAiError } from '../../../services/adminAiService';
import type {
  CopilotConversation,
  CopilotMessage,
  CopilotContextType,
  CopilotArtifact,
} from '../../../types/adminAi';
import { parseArtifactsJson } from './copilotArtifactHelpers';
import { CopilotArtifactRenderer } from './CopilotArtifactRenderer';
import './AdminCopilot.css';

let localMessageSequence = 0;
function generateLocalId(prefix: string): string {
  localMessageSequence += 1;
  return `${prefix}-${localMessageSequence}`;
}

interface LocalMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
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

  // Active session state
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [inputText, setInputText] = useState('');

  // Context attachment state
  const [contextType, setContextType] = useState<CopilotContextType | null>(null);
  const [contextId, setContextId] = useState<string | null>(null);
  const [showAttachModal, setShowAttachModal] = useState(false);
  const [manualAttachType, setManualAttachType] = useState<CopilotContextType>('DISPUTE');
  const [manualAttachId, setManualAttachId] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Initialize context from query params if provided
  useEffect(() => {
    const qType = searchParams.get('contextType') as CopilotContextType | null;
    const qId = searchParams.get('contextId');
    if (qType && qId) {
      setContextType(qType);
      setContextId(qId);
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
      // In case of error (e.g. backend down), list remains empty
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
          artifacts: parseArtifactsJson(m.artifactsJson),
          toolsUsed: m.toolsUsed ? m.toolsUsed.split(',').map((t: string) => t.trim()).filter(Boolean) : undefined,
          verified: m.verified,
          createdAt: m.createdAt,
        }));
        setMessages(mapped);
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
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  // Start new chat
  const handleStartNewChat = () => {
    setCurrentConversationId(null);
    setMessages([]);
    setInputText('');
    setSearchParams({});
    setContextType(null);
    setContextId(null);
    textareaRef.current?.focus();
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

  // Delete conversation - trigger modal
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

  // Start rename
  const handleStartRename = (c: CopilotConversation, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingTitleId(c.id);
    setEditingTitleValue(c.title);
  };

  // Submit rename
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

  // Send message
  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputText).trim();
    if (!text || sending) return;

    const userMsg: LocalMessage = {
      id: generateLocalId('usr'),
      role: 'user',
      content: text,
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputText('');
    setSending(true);

    const payload = {
      conversationId: currentConversationId,
      message: text,
      contextType: contextType || undefined,
      contextId: contextId || undefined,
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
        content: `Lỗi kết nối Copilot: ${errorMsg}`,
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

  // Manual retry handler (strictly user-triggered, NO auto-retry)
  const handleManualRetry = (failedMsg: LocalMessage) => {
    if (!failedMsg.originalPayload) return;
    handleSendMessage(failedMsg.originalPayload.message);
  };

  // Handle manual context attachment
  const handleApplyAttachment = () => {
    if (manualAttachId.trim()) {
      setContextType(manualAttachType);
      setContextId(manualAttachId.trim());
      setShowAttachModal(false);
      setManualAttachId('');
    }
  };

  const handleRemoveAttachment = () => {
    setContextType(null);
    setContextId(null);
  };

  return (
    <div className="admin-copilot">
      {/* ==================== LEFT SIDEBAR: SESSIONS ==================== */}
      <aside className="admin-copilot__sidebar" aria-label="Danh sách hội thoại Copilot">
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
                  onClick={() => setCurrentConversationId(c.id)}
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
                          <span>{c.title || 'Đoạn chat mới'}</span>
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
          <div className="admin-copilot__topbar-title">
            <div className="admin-copilot__topbar-icon">
              <Bot size={15} />
            </div>
            <span>
              {conversations.find((c) => c.id === currentConversationId)?.title || 'Admin Copilot'}
            </span>
          </div>

          <div className="admin-copilot__topbar-meta">
            {contextType && contextId && (
              <div className="admin-copilot__context-badge">
                <Paperclip size={12} />
                <span>
                  {contextType === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'}: #{contextId.slice(0, 8)}
                </span>
                <button
                  type="button"
                  onClick={handleRemoveAttachment}
                  className="admin-copilot__chip-close"
                  title="Gỡ đính kèm"
                >
                  <X size={12} />
                </button>
              </div>
            )}
          </div>
        </header>

        {/* Message Stream */}
        <div className="admin-copilot__messages" role="log" aria-live="polite">
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
                  {m.role === 'user' ? 'A' : <Bot size={15} />}
                </div>

                <div className="admin-copilot__msg-body">
                  <div
                    className={`admin-copilot__msg-content ${
                      m.isError ? 'admin-copilot__msg-content--error' : ''
                    }`}
                  >
                    {m.content}

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
                <Bot size={15} />
              </div>
              <div className="admin-copilot__msg-body">
                <div className="admin-copilot__msg-content admin-copilot__loading-content">
                  <div className="admin-copilot__loading-spinner" />
                  <span>AI Copilot đang phân tích và tra cứu dữ liệu...</span>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Footer */}
        <footer className="admin-copilot__footer">
          {/* Attachment row */}
          <div className="admin-copilot__chip-row">
            {contextType && contextId ? (
              <div className="admin-copilot__attachment-chip">
                <Paperclip size={12} />
                <span>
                  Đính kèm: {contextType === 'DISPUTE' ? 'Hồ sơ khiếu nại' : 'Đơn hàng'} #{contextId}
                </span>
                <button
                  type="button"
                  onClick={handleRemoveAttachment}
                  className="admin-copilot__chip-close"
                  title="Gỡ đính kèm"
                >
                  <X size={12} />
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setShowAttachModal(true)}
                className="admin-copilot__attach-btn"
              >
                <Paperclip size={12} />
                <span>Đính kèm ngữ cảnh</span>
              </button>
            )}
          </div>

          {/* Textarea + Send */}
          <div className="admin-copilot__input-container">
            <textarea
              ref={textareaRef}
              rows={1}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendMessage();
                }
              }}
              placeholder="Hỏi Copilot về doanh thu, khiếu nại, đối tác hoặc số liệu sàn... (Enter để gửi, Shift+Enter xuống dòng)"
              className="admin-copilot__textarea"
              disabled={sending}
            />

            <button
              type="button"
              className="admin-copilot__send-btn"
              onClick={() => handleSendMessage()}
              disabled={!inputText.trim() || sending}
            >
              <Send size={13} />
              <span>Gửi</span>
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

      {/* Context Attachment Modal */}
      <AdminDialog
        open={showAttachModal}
        onClose={() => setShowAttachModal(false)}
        variant="modal"
        title="Đính kèm đối tượng phân tích"
        description="Gắn ngữ cảnh hồ sơ khiếu nại hoặc đơn hàng để Copilot hỗ trợ tra cứu chính xác."
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, width: '100%' }}>
            <AdminButton onClick={() => setShowAttachModal(false)}>Hủy</AdminButton>
            <AdminButton
              variant="primary"
              onClick={handleApplyAttachment}
              disabled={!manualAttachId.trim()}
            >
              Đính kèm
            </AdminButton>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--admin-ink, #1e293b)' }}>
              Loại ngữ cảnh:
            </label>
            <select
              value={manualAttachType}
              onChange={(e) => setManualAttachType(e.target.value as CopilotContextType)}
              className="admin-select"
              style={{
                padding: '8px 12px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                fontSize: 13,
                backgroundColor: '#ffffff',
              }}
            >
              <option value="DISPUTE">Hồ sơ khiếu nại (Dispute)</option>
              <option value="ORDER">Đơn hàng (Order)</option>
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--admin-ink, #1e293b)' }}>
              Mã định danh (UUID):
            </label>
            <input
              type="text"
              value={manualAttachId}
              onChange={(e) => setManualAttachId(e.target.value)}
              placeholder="Nhập UUID hồ sơ hoặc đơn hàng..."
              className="admin-input"
              style={{
                padding: '8px 12px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                fontSize: 13,
              }}
            />
          </div>
        </div>
      </AdminDialog>
    </div>
  );
};
