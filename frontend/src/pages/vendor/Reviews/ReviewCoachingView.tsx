import React, { useState, useEffect, useCallback } from 'react';
import { 
  ShieldCheck, 
  Compass, 
  Clock, 
  Coins, 
  Award, 
  Quote, 
  CheckCircle2, 
  AlertCircle,
  RefreshCw,
  Target
} from 'lucide-react';
import { vendorAiService } from '../../../services/vendorAiService';
import type { CoachingResult, PillarEvaluation } from '../../../types/vendorAi';

const PILLAR_CONFIG: Record<string, { label: string; icon: React.ReactNode; desc: string }> = {
  GUIDE: {
    label: 'Hướng dẫn viên (Guide)',
    icon: <Compass className="w-4 h-4 text-slate-700" />,
    desc: 'Thái độ, chuyên môn và hỗ trợ của HDV 1:1',
  },
  SAFETY: {
    label: 'An toàn hàng hải (Safety)',
    icon: <ShieldCheck className="w-4 h-4 text-emerald-700" />,
    desc: 'Trang bị áo phao SOLAS và quy chuẩn xuất bến',
  },
  PUNCTUALITY: {
    label: 'Đúng giờ & Bến bãi (Punctuality)',
    icon: <Clock className="w-4 h-4 text-amber-700" />,
    desc: 'Thời gian đón khách và thủ tục check-in',
  },
  VALUE: {
    label: 'Giá trị trải nghiệm (Value)',
    icon: <Coins className="w-4 h-4 text-indigo-700" />,
    desc: 'Mức độ hài lòng so với chi phí chi trả',
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
      setError('Không thể tải báo cáo phân tích đánh giá lúc này.');
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
          <span className="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200">
            Tích cực
          </span>
        );
      case 'NEEDS_IMPROVEMENT':
        return (
          <span className="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-rose-50 text-rose-800 border border-rose-200">
            Cần cải thiện
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            Trung tính
          </span>
        );
    }
  };

  const renderPriorityBadge = (priority: string) => {
    switch (priority) {
      case 'HIGH':
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-rose-50 text-rose-800 border border-rose-200">Ưu tiên cao</span>;
      case 'MEDIUM':
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-amber-50 text-amber-800 border border-amber-200">Ưu tiên vừa</span>;
      default:
        return <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">Khuyến nghị thêm</span>;
    }
  };

  // Tailored shimmer skeleton loader matching exact geometry
  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 h-28 flex flex-col justify-between">
            <div className="h-3 w-28 bg-slate-200 rounded" />
            <div className="h-8 w-20 bg-slate-200 rounded" />
            <div className="h-3 w-40 bg-slate-100 rounded" />
          </div>
          <div className="bg-white p-5 rounded-2xl border border-slate-200/90 h-28 flex flex-col justify-between">
            <div className="h-3 w-32 bg-slate-200 rounded" />
            <div className="h-8 w-24 bg-slate-200 rounded" />
            <div className="h-3 w-44 bg-slate-100 rounded" />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="bg-white rounded-2xl p-5 border border-slate-200/90 h-52 flex flex-col justify-between">
              <div className="flex justify-between items-center pb-3 border-b border-slate-100">
                <div className="h-4 w-36 bg-slate-200 rounded" />
                <div className="h-6 w-16 bg-slate-100 rounded-lg" />
              </div>
              <div className="space-y-2">
                <div className="h-12 bg-slate-50 rounded-xl" />
                <div className="h-12 bg-slate-50 rounded-xl" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (error || !coachingData) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-3">
        <p className="text-xs text-slate-600">{error || 'Chưa có đủ dữ liệu đánh giá để phân tích.'}</p>
        <button
          onClick={loadData}
          className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-semibold inline-flex items-center gap-1.5 transition-colors cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Thử lại</span>
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Metric Overview Cards - Strictly Crisp Data, No Slogan Fillers */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs">
          <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">Điểm đánh giá trung bình</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-3xl font-bold font-mono tabular-nums text-slate-900">{coachingData.averageRating}</span>
            <span className="text-xs text-slate-400 font-mono">/ 5.0</span>
          </div>
          <p className="text-xs text-slate-600 font-mono tabular-nums mt-1.5">
            Tổng hợp từ {coachingData.totalReviewsAnalyzed} đánh giá thực tế
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/90 shadow-2xs">
          <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">Huy hiệu vận hành bến bãi</span>
          <div className="flex items-center gap-2 mt-2">
            <Award className="w-6 h-6 text-amber-500" />
            <span className="text-xl font-bold text-slate-900 font-mono">
              {coachingData.badgeProgression?.currentTier || 'VERIFIED'}
            </span>
          </div>
          <p className="text-xs text-slate-600 font-mono tabular-nums mt-1.5">
            Cần thêm {coachingData.badgeProgression?.reviewsNeededForNextTier || 0} đánh giá để xét thăng hạng
          </p>
        </div>
      </div>

      {/* 2. 4 Trụ Cột Đánh Giá */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {Object.entries(coachingData.pillars || {}).map(([pillarKey, pData]) => {
          const config = PILLAR_CONFIG[pillarKey] || {
            label: pillarKey,
            icon: <Target className="w-4 h-4 text-slate-700" />,
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
                      <h4 className="text-xs font-semibold text-slate-900">{config.label}</h4>
                      <p className="text-[11px] text-slate-500">{config.desc}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {renderSentimentBadge(pData.sentiment)}
                    <div className="px-2.5 py-1 rounded-xl bg-slate-900 text-white font-mono font-bold text-xs tabular-nums">
                      {pData.scoreOutOf10}/10
                    </div>
                  </div>
                </div>

                {/* Evidence Quotes */}
                <div className="space-y-2.5 mt-3.5">
                  {pData.positiveQuotes && pData.positiveQuotes.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-[11px] font-semibold text-emerald-800 flex items-center gap-1">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                        Phản hồi tích cực:
                      </span>
                      {pData.positiveQuotes.map((q, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-700 italic flex items-start gap-2"
                        >
                          <Quote className="w-3 h-3 text-slate-400 shrink-0 mt-0.5" />
                          <span>"{q.exactQuote}"</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {pData.negativeQuotes && pData.negativeQuotes.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-[11px] font-semibold text-rose-800 flex items-center gap-1">
                        <AlertCircle className="w-3.5 h-3.5 text-rose-600" />
                        Góp ý cần lưu ý:
                      </span>
                      {pData.negativeQuotes.map((q, idx) => (
                        <div
                          key={idx}
                          className="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-700 italic flex items-start gap-2"
                        >
                          <Quote className="w-3 h-3 text-slate-400 shrink-0 mt-0.5" />
                          <span>"{q.exactQuote}"</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {(!pData.positiveQuotes || pData.positiveQuotes.length === 0) &&
                    (!pData.negativeQuotes || pData.negativeQuotes.length === 0) && (
                      <p className="text-xs text-slate-400 italic">Chưa có trích dẫn đánh giá cho tiêu chí này.</p>
                    )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 3. Action Items */}
      {coachingData.actionItems && coachingData.actionItems.length > 0 && (
        <div className="bg-white rounded-2xl p-5 border border-slate-200/90 shadow-2xs space-y-3.5">
          <div className="flex items-center gap-2">
            <Target className="w-4 h-4 text-slate-700" />
            <h3 className="text-xs font-semibold text-slate-900 uppercase tracking-wide">
              Khuyến nghị hành động cải thiện vận hành
            </h3>
          </div>

          <div className="grid grid-cols-1 gap-2.5">
            {coachingData.actionItems.map((item, idx) => (
              <div
                key={idx}
                className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    {renderPriorityBadge(item.priority)}
                    <span className="text-xs font-semibold text-slate-900">{item.title}</span>
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
        <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-700 flex items-start gap-2.5">
          <Award className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            <strong className="font-semibold text-slate-900">Lộ trình xét hạng: </strong>
            {coachingData.badgeProgression.roadmapNote}
          </p>
        </div>
      )}
    </div>
  );
};
