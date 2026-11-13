import React, { useState, useEffect, useCallback } from 'react';
import { 
  Sparkles, 
  ShieldCheck, 
  Compass, 
  Clock, 
  Coins, 
  Award, 
  Quote, 
  CheckCircle2, 
  AlertCircle,
  RefreshCw,
  Loader2
} from 'lucide-react';
import { vendorAiService } from '../../../services/vendorAiService';
import type { CoachingResult, PillarEvaluation } from '../../../types/vendorAi';

const PILLAR_CONFIG: Record<string, { label: string; icon: React.ReactNode; desc: string }> = {
  GUIDE: {
    label: 'Hướng dẫn viên (Guide)',
    icon: <Compass className="w-4 h-4 text-sky-600" />,
    desc: 'Thái độ, sự chu đáo và khả năng điều phối của HDV 1:1',
  },
  SAFETY: {
    label: 'An toàn hàng hải (Safety)',
    icon: <ShieldCheck className="w-4 h-4 text-emerald-600" />,
    desc: 'Trang bị áo phao SOLAS, tình trạng cano & thiết bị cứu sinh',
  },
  PUNCTUALITY: {
    label: 'Đúng giờ & Bến bãi (Punctuality)',
    icon: <Clock className="w-4 h-4 text-amber-600" />,
    desc: 'Thời gian đón khách, xuất bến đúng lịch và thủ tục check-in',
  },
  VALUE: {
    label: 'Giá trị trải nghiệm (Value)',
    icon: <Coins className="w-4 h-4 text-indigo-600" />,
    desc: 'Mức độ tương xứng giữa chi phí và chất lượng thực tế',
  },
};

export const ReviewCoachingView: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(true);
  const [coachingData, setCoachingData] = useState<CoachingResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await vendorAiService.getReviewCoaching();
      setCoachingData(res);
    } catch (err) {
      console.error('[ReviewCoaching] Failed to load coaching:', err);
      setError('Không thể tải báo cáo AI Coaching lúc này.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const renderSentimentBadge = (sentiment: PillarEvaluation['sentiment']) => {
    switch (sentiment) {
      case 'POSITIVE':
        return (
          <span className="px-2 py-0.5 rounded-md text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
            Tích cực
          </span>
        );
      case 'NEEDS_IMPROVEMENT':
        return (
          <span className="px-2 py-0.5 rounded-md text-[11px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
            Cần cải thiện
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-md text-[11px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
            Trung tính
          </span>
        );
    }
  };

  const renderPriorityBadge = (priority: string) => {
    switch (priority) {
      case 'HIGH':
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-rose-100 text-rose-800">Ưu tiên cao</span>;
      case 'MEDIUM':
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-100 text-amber-800">Ưu tiên vừa</span>;
      default:
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-100 text-slate-700">Khuyến nghị thêm</span>;
    }
  };

  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 p-12 flex flex-col items-center justify-center gap-3 text-center">
        <Loader2 className="w-8 h-8 text-[#0284c7] animate-spin" />
        <p className="text-sm font-medium text-slate-700">AI đang tổng hợp và phân tích 4 trụ cột đánh giá của đối tác...</p>
      </div>
    );
  }

  if (error || !coachingData) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-3">
        <p className="text-sm text-slate-600">{error || 'Chưa có đủ dữ liệu đánh giá để phân tích.'}</p>
        <button
          onClick={loadData}
          className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold inline-flex items-center gap-1.5 transition-colors cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Thử lại</span>
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Header Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Đánh giá trung bình</span>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-3xl font-bold font-mono text-slate-900">{coachingData.averageRating}</span>
            <span className="text-xs text-slate-400 font-mono">/ 5.0</span>
          </div>
          <p className="text-xs text-slate-600 mt-1">
            Dựa trên <strong className="font-mono text-slate-900">{coachingData.totalReviewsAnalyzed}</strong> lượt đánh giá thật
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Huy hiệu vận hành</span>
          <div className="flex items-center gap-2 mt-1">
            <Award className="w-6 h-6 text-amber-500" />
            <span className="text-xl font-bold text-slate-900 font-mono">
              {coachingData.badgeProgression?.currentTier || 'VERIFIED'}
            </span>
          </div>
          <p className="text-xs text-slate-600 mt-1">
            Cần thêm <strong className="font-mono text-slate-900">{coachingData.badgeProgression?.reviewsNeededForNextTier || 0}</strong> review để đạt hạng tiếp theo
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Mô hình phân tích</span>
            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-100 text-sky-800 font-mono">
              DUAL BRAIN
            </span>
          </div>
          <p className="text-xs text-slate-600 leading-relaxed mt-2">
            AI tự động trích xuất bằng chứng từ bình luận thực tế, loại trừ nhận xét spam và chấm điểm 4 trụ cột cốt lõi.
          </p>
        </div>
      </div>

      {/* 2. 4 Trụ Cột Đánh Giá (Pillars Grid) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {Object.entries(coachingData.pillars || {}).map(([pillarKey, pData]) => {
          const config = PILLAR_CONFIG[pillarKey] || {
            label: pillarKey,
            icon: <Sparkles className="w-4 h-4 text-sky-600" />,
            desc: 'Tiêu chí đánh giá chất lượng',
          };

          return (
            <div
              key={pillarKey}
              className="bg-white rounded-2xl p-5 border border-slate-200/90 shadow-2xs flex flex-col justify-between gap-4"
            >
              <div>
                <div className="flex items-start justify-between gap-2 pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-center shrink-0">
                      {config.icon}
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900">{config.label}</h4>
                      <p className="text-[11px] text-slate-500">{config.desc}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {renderSentimentBadge(pData.sentiment)}
                    <div className="px-2.5 py-1 rounded-xl bg-slate-900 text-white font-mono font-bold text-xs">
                      {pData.scoreOutOf10}/10
                    </div>
                  </div>
                </div>

                {/* Evidence Quotes */}
                <div className="space-y-2.5 mt-3.5">
                  {pData.positiveQuotes && pData.positiveQuotes.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-[11px] font-semibold text-emerald-700 flex items-center gap-1">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        Khách khen thực tế:
                      </span>
                      {pData.positiveQuotes.map((q, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl bg-emerald-50/50 border border-emerald-100 text-xs text-slate-800 italic flex items-start gap-2"
                        >
                          <Quote className="w-3 h-3 text-emerald-500 shrink-0 mt-0.5" />
                          <span>"{q.exactQuote}"</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {pData.negativeQuotes && pData.negativeQuotes.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-[11px] font-semibold text-rose-700 flex items-center gap-1">
                        <AlertCircle className="w-3.5 h-3.5" />
                        Góp ý cần khắc phục:
                      </span>
                      {pData.negativeQuotes.map((q, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl bg-rose-50/50 border border-rose-100 text-xs text-slate-800 italic flex items-start gap-2"
                        >
                          <Quote className="w-3 h-3 text-rose-500 shrink-0 mt-0.5" />
                          <span>"{q.exactQuote}"</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {(!pData.positiveQuotes || pData.positiveQuotes.length === 0) &&
                    (!pData.negativeQuotes || pData.negativeQuotes.length === 0) && (
                      <p className="text-xs text-slate-400 italic">Chưa có trích dẫn đánh giá nổi bật cho trụ cột này.</p>
                    )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 3. Action Items (Khuyến nghị hành động thực tế) */}
      {coachingData.actionItems && coachingData.actionItems.length > 0 && (
        <div className="bg-white rounded-2xl p-6 border border-slate-200/90 shadow-2xs space-y-4">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#0284c7]" />
            <h3 className="text-sm font-bold text-slate-900 uppercase tracking-wide">
              Khuyến nghị hành động tối ưu dịch vụ
            </h3>
          </div>

          <div className="grid grid-cols-1 gap-3">
            {coachingData.actionItems.map((item, idx) => (
              <div
                key={idx}
                className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    {renderPriorityBadge(item.priority)}
                    <span className="text-xs font-bold text-slate-900">{item.title}</span>
                  </div>
                  <p className="text-xs text-slate-600 leading-relaxed">{item.recommendation}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 4. Roadmap Note */}
      {coachingData.badgeProgression?.roadmapNote && (
        <div className="p-4 rounded-xl bg-amber-50/60 border border-amber-200/80 text-xs text-amber-900 flex items-start gap-2.5">
          <Award className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            <strong className="font-semibold">Lộ trình thăng hạng: </strong>
            {coachingData.badgeProgression.roadmapNote}
          </p>
        </div>
      )}
    </div>
  );
};
