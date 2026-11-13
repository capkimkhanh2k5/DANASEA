import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { reviewService } from '../../../services/reviewService';
import { vendorAiService } from '../../../services/vendorAiService';
import { ReviewCoachingView } from './ReviewCoachingView';
import { toast } from '../../../store/useToastStore';
import { 
  Loader2, 
  Star, 
  MessageSquare, 
  Download, 
  Send, 
  X, 
  Reply, 
  CheckCircle2,
  Target,
  PenLine
} from 'lucide-react';
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasBadge, 
  AtlasAvatar, 
  AtlasButton,
  AtlasPagination
} from '../../../components/vendor/atlas/AtlasDesignSystem';

interface ReviewItem {
  id: string;
  customerName: string;
  avatarText: string;
  orderCode: string;
  serviceName: string;
  slotTime: string;
  rating: number;
  date: string;
  criteria: {
    safety: number;
    guide: number;
    equipment: number;
    scenery: number;
  };
  content: string;
  photos: string[];
  reply?: {
    author: string;
    time: string;
    text: string;
  };
}

interface ChatMessage {
  id: string;
  sender: 'customer' | 'vendor';
  senderName: string;
  avatarText: string;
  time: string;
  text: string;
}

interface Conversation {
  id: number;
  customerName: string;
  avatarText: string;
  orderCode: string;
  serviceName: string;
  lastMessage: string;
  time: string;
  unread: boolean;
  messages: ChatMessage[];
}

export function VendorReviews() {
  const [activeTab, setActiveTab] = useState<'reviews' | 'coaching' | 'chat'>('reviews');
  const [starFilter, setStarFilter] = useState<number | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(5);
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [_isLoading, setIsLoading] = useState(false);
  const [isSubmittingReply, setIsSubmittingReply] = useState(false);
  const [isDraftingReply, setIsDraftingReply] = useState(false);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeChatId, setActiveChatId] = useState<number>(1);
  const [messageInput, setMessageInput] = useState('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Reply to review modal
  const [replyingReviewId, setReplyingReviewId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const handleAiDraftReply = async () => {
    if (!replyingReviewId) return;
    try {
      setIsDraftingReply(true);
      const res = await vendorAiService.draftReply(replyingReviewId, 'PROFESSIONAL');
      if (res && res.draftedReply) {
        setReplyText(res.draftedReply);
        showToast('AI đã soạn phản hồi chu đáo!');
      }
    } catch (err) {
      console.error('[ReplyDraft] Failed:', err);
      toast.error('Không thể sinh phản hồi AI lúc này.');
    } finally {
      setIsDraftingReply(false);
    }
  };

  const fetchReviews = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await reviewService.getVendorReviews(0, 50);
      if (res && res.content && Array.isArray(res.content)) {
        const mapped: ReviewItem[] = res.content.map((r) => ({
          id: r.id,
          customerName: r.customerName || 'Du khách DANASEA',
          avatarText: (r.customerName || 'DK').slice(0, 2).toUpperCase(),
          orderCode: r.subOrderId ? `#VE-${r.subOrderId.slice(0, 8).toUpperCase()}` : '#DON-HANG',
          serviceName: 'Trải nghiệm du lịch biển',
          slotTime: r.createdAt ? new Date(r.createdAt).toLocaleDateString('vi-VN') : '',
          rating: r.rating || 5,
          date: r.createdAt ? new Date(r.createdAt).toLocaleString('vi-VN') : '',
          criteria: { safety: 5.0, guide: 5.0, equipment: 5.0, scenery: 5.0 },
          content: r.comment || '',
          photos: r.images || [],
          reply: r.vendorReply
            ? {
                author: 'Đơn vị tổ chức',
                time: r.vendorRepliedAt ? new Date(r.vendorRepliedAt).toLocaleString('vi-VN') : '',
                text: r.vendorReply,
              }
            : undefined,
        }));
        setReviews(mapped);
      } else {
        setReviews([]);
      }
    } catch (err) {
      console.warn('[VendorReviews] Lỗi tải đánh giá từ backend:', err);
      setReviews([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReviews();
  }, [fetchReviews]);

  const activeConversation = conversations.find(c => c.id === activeChatId) || conversations[0] || null;

  // Send chat message
  const handleSendMessage = (textToSend?: string) => {
    const text = (textToSend || messageInput).trim();
    if (!text || !activeConversation) return;

    const newMsg: ChatMessage = {
      id: `msg-${Date.now()}`,
      sender: 'vendor',
      senderName: 'DOC Support',
      avatarText: 'DOC',
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
      text: text,
    };

    setConversations(prev => prev.map(c => {
      if (c.id === activeConversation.id) {
        return {
          ...c,
          lastMessage: text,
          time: 'Vừa xong',
          messages: [...c.messages, newMsg]
        };
      }
      return c;
    }));

    setMessageInput('');
    showToast('Đã gửi tin nhắn phản hồi tới du khách!');
  };

  // Save review reply
  const handleSaveReply = async () => {
    if (!replyingReviewId || !replyText.trim()) return;

    try {
      setIsSubmittingReply(true);
      await reviewService.replyVendorReview(replyingReviewId, replyText.trim());

      setReviews(prev => prev.map(r => {
        if (r.id === replyingReviewId) {
          return {
            ...r,
            reply: {
              author: 'Đại diện cơ sở tổ chức',
              time: 'Vừa xong',
              text: replyText.trim(),
            }
          };
        }
        return r;
      }));

      toast.success('Đã đăng phản hồi đánh giá công khai thành công!');
      setReplyingReviewId(null);
      setReplyText('');
    } catch (err: unknown) {
      console.error('[VendorReviews] Lỗi gửi phản hồi:', err);
      let msg = 'Không thể gửi phản hồi.';
      if (err && typeof err === 'object' && 'response' in err) {
        const resp = (err as { response?: { data?: { message?: string } } }).response;
        if (resp?.data?.message) msg = resp.data.message;
      }
      toast.error('Lỗi: ' + msg);
    } finally {
      setIsSubmittingReply(false);
    }
  };

  // Export CSAT
  const handleExportCSAT = () => {
    const csvRows = [
      ['Mã đánh giá', 'Khách hàng', 'Mã đơn', 'Dịch vụ', 'Số sao', 'Ngày', 'Nội dung', 'Phản hồi nhà cung cấp'],
      ...reviews.map(r => [
        r.id,
        r.customerName,
        r.orderCode,
        r.serviceName,
        r.rating.toString(),
        r.date,
        `"${r.content.replace(/"/g, '""')}"`,
        r.reply ? `"${r.reply.text.replace(/"/g, '""')}"` : 'Chưa phản hồi'
      ])
    ];

    const csvString = '\uFEFF' + csvRows.map(e => e.join(',')).join('\r\n');
    const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    try {
      link.href = url;
      link.download = `DANASEA_CSAT_REPORT_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
    } finally {
      link.remove();
      URL.revokeObjectURL(url);
    }
    showToast('Đã xuất báo cáo CSAT thành công!');
  };

  const totalReviews = reviews.length;
  const avgRating = totalReviews > 0
    ? (reviews.reduce((sum, r) => sum + r.rating, 0) / totalReviews).toFixed(1)
    : '0.0';
  const repliedCount = reviews.filter((r) => r.reply).length;
  const replyRate = totalReviews > 0
    ? `${Math.round((repliedCount / totalReviews) * 100)}%`
    : '0%';

  const filteredReviews = reviews.filter(r => {
    if (starFilter === 'ALL') return true;
    return r.rating === starFilter;
  });

  const totalPages = Math.max(1, Math.ceil(filteredReviews.length / pageSize));
  const paginatedReviews = filteredReviews.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Top Page Header (Atlas Page Header) */}
      <AtlasPageHeader
        title="Đánh giá & Tin nhắn Du khách"
        actions={
          <div className="flex items-center gap-3">
            <div className="bg-slate-100 p-1 rounded-xl inline-flex border border-slate-200/80 text-sm">
              <button 
                onClick={() => setActiveTab('reviews')}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'reviews' 
                    ? 'bg-white text-slate-900 shadow-xs' 
                    : 'text-slate-600 hover:text-slate-900'
                }`} 
                type="button"
              >
                <Star className="w-3.5 h-3.5" />
                <span>Đánh giá ({reviews.length})</span>
              </button>

              <button 
                onClick={() => setActiveTab('coaching')}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'coaching' 
                    ? 'bg-white text-slate-900 shadow-xs' 
                    : 'text-slate-600 hover:text-slate-900'
                }`} 
                type="button"
              >
                <Target className="w-3.5 h-3.5 text-slate-700" />
                <span>Phân tích 4 Trụ Cột</span>
              </button>

              <button 
                onClick={() => setActiveTab('chat')}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'chat' 
                    ? 'bg-white text-slate-900 shadow-xs' 
                    : 'text-slate-600 hover:text-slate-900'
                }`} 
                type="button"
              >
                <MessageSquare className="w-3.5 h-3.5" />
                <span>Tin nhắn</span>
              </button>
            </div>

            <AtlasButton
              variant="outline"
              size="md"
              onClick={handleExportCSAT}
              icon={<Download className="w-4 h-4 text-slate-500" />}
            >
              Xuất CSAT
            </AtlasButton>
          </div>
        }
      />

      {/* 2. 4-Card Summary Strip for Reviews Tab (Atlas CSAT Metrics) */}
      {activeTab === 'reviews' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <AtlasMetricCard
            label="Điểm CSAT trung bình"
            value={avgRating}
            accent="amber"
          />

          <AtlasMetricCard
            label="Tổng lượt đánh giá"
            value={totalReviews}
          />

          <AtlasMetricCard
            label="Tỷ lệ phản hồi CSKH"
            value={replyRate}
            accent="emerald"
          />

          <AtlasMetricCard
            label="Đánh giá cần phản hồi"
            value={totalReviews - repliedCount}
          />
        </div>
      )}

      {/* 3. TAB 1: REVIEWS VIEW */}
      {activeTab === 'reviews' && (
        <div className="space-y-4">
          {/* Star Filter Tabs */}
          <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-xl border border-slate-200/70 inline-flex">
            <button 
              onClick={() => { setStarFilter('ALL'); setPage(0); }}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                starFilter === 'ALL' 
                  ? 'bg-white text-slate-900 shadow-xs font-bold' 
                  : 'text-slate-600 hover:text-slate-900 font-medium'
              }`}
            >
              Tất cả ({reviews.length})
            </button>
            {[5, 4, 3].map((stars) => (
              <button 
                key={stars}
                onClick={() => { setStarFilter(stars); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1 cursor-pointer ${
                  starFilter === stars 
                    ? 'bg-white text-slate-900 shadow-xs font-bold' 
                    : 'text-slate-600 hover:text-slate-900 font-medium'
                }`}
              >
                <span>{stars} sao</span>
                <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
              </button>
            ))}
          </div>

          {/* Reviews Feed */}
          {_isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs animate-pulse flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-slate-200" />
                      <div className="space-y-1.5">
                        <div className="w-32 h-4 bg-slate-200 rounded" />
                        <div className="w-48 h-3 bg-slate-100 rounded" />
                      </div>
                    </div>
                    <div className="w-20 h-4 bg-slate-200 rounded" />
                  </div>
                  <div className="w-full h-12 bg-slate-100 rounded" />
                </div>
              ))}
            </div>
          ) : filteredReviews.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-16 text-center bg-white rounded-xl border border-slate-200/80">
              <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <Star className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">Chưa có lượt đánh giá nào</h3>
              <p className="text-xs text-slate-500 mt-1 max-w-sm">
                {starFilter === 'ALL'
                  ? 'Đối tác chưa nhận được đánh giá nào từ du khách cho các dịch vụ đã hoàn thành.'
                  : `Không có đánh giá nào đạt mức ${starFilter} sao.`}
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {paginatedReviews.map((rev) => (
                <article key={rev.id} className="bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs hover:border-slate-300 transition-all flex flex-col gap-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-3">
                      <AtlasAvatar name={rev.customerName} size="md" />
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="text-sm font-bold text-slate-900">{rev.customerName}</h3>
                          <AtlasBadge label="Đã đi tour" variant="success" />
                        </div>
                        <p className="text-xs text-slate-500 mt-0.5">{rev.serviceName} • {rev.slotTime}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2.5 text-xs">
                      <div className="flex text-amber-500">
                        {Array.from({ length: rev.rating }).map((_, idx) => (
                          <Star key={idx} className="w-4 h-4 fill-amber-400 text-amber-400" />
                        ))}
                      </div>
                      <span className="text-slate-400 text-xs font-mono">{rev.date}</span>
                    </div>
                  </div>

                  <p className="text-sm text-slate-700 leading-relaxed">{rev.content}</p>

                  {rev.photos.length > 0 && (
                    <div className="flex items-center gap-2.5 pt-0.5">
                      {rev.photos.map((p, idx) => (
                        <div key={idx} className="w-16 h-16 rounded-xl border border-slate-200/80 overflow-hidden">
                          <img src={p} alt="Review attachment" className="w-full h-full object-cover" />
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Existing Reply */}
                  {rev.reply ? (
                    <div className="mt-1 p-3.5 rounded-xl bg-slate-50 border-l-2 border-slate-900 flex flex-col gap-1.5 text-xs">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5">
                          <Reply className="w-3.5 h-3.5 text-slate-600" />
                          <span className="font-bold text-sm text-slate-900">{rev.reply.author}</span>
                          <span className="text-slate-400 text-xs font-mono">• {rev.reply.time}</span>
                        </div>
                        <AtlasBadge label="Đã phản hồi" variant="success" />
                      </div>
                      <p className="text-slate-600 pl-5 text-xs leading-relaxed">“{rev.reply.text}”</p>
                    </div>
                  ) : (
                    <div className="flex justify-end pt-1">
                      <AtlasButton
                        variant="primary"
                        size="sm"
                        onClick={() => { setReplyingReviewId(rev.id); setReplyText(''); }}
                        icon={<Reply className="w-3.5 h-3.5" />}
                      >
                        Phản hồi đánh giá
                      </AtlasButton>
                    </div>
                  )}
                </article>
              ))}

              {filteredReviews.length > pageSize && (
                <div className="bg-white rounded-xl border border-slate-200/80 overflow-hidden shadow-xs">
                  <AtlasPagination
                    currentPage={page}
                    totalPages={totalPages}
                    totalItems={filteredReviews.length}
                    pageSize={pageSize}
                    onPageChange={setPage}
                    onPageSizeChange={(newSize) => {
                      setPageSize(newSize);
                      setPage(0);
                    }}
                  />
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* 4. TAB 2: REVIEW COACHING 4 PILLARS */}
      {activeTab === 'coaching' && <ReviewCoachingView />}

      {/* 5. TAB 3: CHAT (INBOX) VIEW */}
      {activeTab === 'chat' && (
        conversations.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-16 text-center bg-white rounded-xl border border-slate-200/80">
            <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
              <MessageSquare className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-bold text-slate-900">Hộp thư tin nhắn trống</h3>
            <p className="text-xs text-slate-500 mt-1 max-w-md">
              Chưa có cuộc hội thoại nào giữa đối tác và du khách. Tin nhắn trao đổi trực tiếp sẽ hiển thị tại đây khi khách hàng gửi câu hỏi từ đơn đặt dịch vụ.
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-xl border border-slate-200/80 overflow-hidden grid grid-cols-1 lg:grid-cols-12 min-h-[580px]">
            {/* Sidebar (4 Cols) */}
            <div className="lg:col-span-4 bg-slate-50 flex flex-col border-r border-slate-200">
              <div className="p-3 border-b border-slate-200 bg-white">
                <input 
                  className="w-full h-9 pl-3 pr-2.5 rounded-lg bg-white border border-slate-200 text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-1 focus:ring-slate-900" 
                  placeholder="Tìm tên khách hoặc mã đơn..." 
                  type="text"
                />
              </div>

              <div className="flex-1 overflow-y-auto flex flex-col divide-y divide-slate-200">
                {conversations.map((conv) => (
                  <div 
                    key={conv.id}
                    onClick={() => setActiveChatId(conv.id)}
                    className={`p-3.5 flex items-start gap-2.5 cursor-pointer transition-colors ${
                      activeChatId === conv.id ? 'bg-slate-100 border-l-4 border-l-slate-900' : 'hover:bg-slate-50'
                    }`}
                  >
                    <AtlasAvatar name={conv.customerName} size="md" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-slate-900 truncate">{conv.customerName}</span>
                        <span className="text-xs text-slate-500 font-mono">{conv.time}</span>
                      </div>
                      <div className="text-xs text-slate-700 font-medium truncate">{conv.orderCode} • {conv.serviceName}</div>
                      <p className="text-xs text-slate-500 truncate mt-0.5">{conv.lastMessage}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Main Chat Area (8 Cols) */}
            <div className="lg:col-span-8 flex flex-col justify-between bg-white">
              {/* Chat Top Bar */}
              <div className="p-3 bg-slate-50 flex items-center justify-between border-b border-slate-200">
                <div className="flex items-center gap-2.5">
                  <AtlasAvatar name={activeConversation.customerName} size="md" />
                  <div className="flex flex-col">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-slate-900">{activeConversation.customerName}</span>
                      <AtlasBadge label="Trực tuyến" variant="success" />
                    </div>
                    <span className="text-xs text-slate-500">
                      Đơn: <strong className="text-slate-900 font-mono">{activeConversation.orderCode}</strong>
                    </span>
                  </div>
                </div>
              </div>

              {/* Chat Messages Stream */}
              <div className="p-4 flex-1 overflow-y-auto flex flex-col gap-3 max-h-[440px]">
                {activeConversation.messages.map((msg) => (
                  <div 
                    key={msg.id} 
                    className={`flex items-start gap-2 max-w-lg ${msg.sender === 'vendor' ? 'justify-end self-end' : ''}`}
                  >
                    {msg.sender === 'customer' && (
                      <AtlasAvatar name={msg.senderName} size="sm" />
                    )}
                    <div className={`flex flex-col gap-1 ${msg.sender === 'vendor' ? 'items-end' : ''}`}>
                      <div className={`p-3 rounded-2xl text-xs ${
                        msg.sender === 'vendor' 
                          ? 'bg-slate-900 text-white' 
                          : 'bg-slate-100 text-slate-800'
                      }`}>
                        {msg.text}
                      </div>
                      <span className="text-[10px] text-slate-400 font-mono">
                        {msg.time} • {msg.sender === 'vendor' ? 'Đã gửi' : 'Đã nhận'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>

              {/* Chat Input Bar */}
              <div className="p-3 bg-slate-50 flex flex-col gap-2 border-t border-slate-200">
                <div className="flex items-center gap-2">
                  <input 
                    className="flex-1 h-10 px-3.5 rounded-xl bg-white border border-slate-200 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900" 
                    placeholder="Nhập tin nhắn hỗ trợ cho du khách..." 
                    type="text"
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleSendMessage();
                    }}
                  />
                  <AtlasButton
                    variant="primary"
                    size="md"
                    onClick={() => handleSendMessage()}
                    icon={<Send className="w-4 h-4" />}
                  >
                    Gửi
                  </AtlasButton>
                </div>
              </div>
            </div>
          </div>
        )
      )}

      {/* Reply Review Modal */}
      {replyingReviewId && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl flex flex-col gap-4 border border-slate-200/90">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2 font-display">
                <Reply className="w-4 h-4 text-slate-700" />
                Phản hồi công khai cho du khách
              </h3>
              <button onClick={() => setReplyingReviewId(null)} className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-slate-700">Nội dung phản hồi từ đại diện đối tác</label>
                <button
                  type="button"
                  onClick={handleAiDraftReply}
                  disabled={isDraftingReply}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-slate-100 text-slate-800 hover:bg-slate-200 border border-slate-200 cursor-pointer transition-colors disabled:opacity-50"
                  title="Gợi ý phản hồi chu đáo, chuẩn văn phong dịch vụ"
                >
                  {isDraftingReply ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <PenLine className="w-3.5 h-3.5 text-slate-600" />}
                  <span>{isDraftingReply ? 'Đang tạo gợi ý...' : 'Gợi ý phản hồi'}</span>
                </button>
              </div>
              <textarea 
                rows={4}
                value={replyText}
                onChange={(e) => setReplyText(e.target.value)}
                placeholder="Viết phản hồi chu đáo, thể hiện sự trân trọng và chuyên nghiệp của cơ sở..."
                className="w-full p-3.5 rounded-xl bg-white text-slate-900 text-sm border border-slate-200 focus:outline-none focus:ring-2 focus:ring-slate-900 resize-none leading-relaxed"
              />
            </div>

            <div className="flex justify-end gap-2.5 pt-3 border-t border-slate-100">
              <AtlasButton
                variant="outline"
                size="sm"
                onClick={() => setReplyingReviewId(null)}
              >
                Hủy bỏ
              </AtlasButton>
              <AtlasButton
                variant="primary"
                size="sm"
                onClick={handleSaveReply}
                disabled={isSubmittingReply || !replyText.trim()}
                icon={isSubmittingReply ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : undefined}
              >
                {isSubmittingReply ? 'Đang gửi...' : 'Đăng phản hồi'}
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-lg flex items-center gap-2 text-sm border border-slate-700">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}
