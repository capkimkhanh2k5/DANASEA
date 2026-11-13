import React, { useState, useEffect, useCallback } from 'react';
import {
  Sparkles,
  ChevronDown,
  ChevronUp,
  Clock,
  RefreshCw,
  Loader2,
} from 'lucide-react';
import { vendorAiService } from '../../../services/vendorAiService';
import type { SlotAdvisorResult, SlotRecommendation } from '../../../types/vendorAi';

interface SlotAdvisorBannerProps {
  serviceId?: string;
}

export const SlotAdvisorBanner: React.FC<SlotAdvisorBannerProps> = ({ serviceId }) => {
  const [loading, setLoading] = useState<boolean>(true);
  const [data, setData] = useState<SlotAdvisorResult | null>(null);
  const [isExpanded, setIsExpanded] = useState<boolean>(false);

  const fetchAdvisor = useCallback(async () => {
    try {
      setLoading(true);
      const res = await vendorAiService.getSlotAdvisor(serviceId, 7);
      setData(res);
    } catch (err) {
      console.error('[SlotAdvisor] Fetch failed:', err);
    } finally {
      setLoading(false);
    }
  }, [serviceId]);

  useEffect(() => {
    fetchAdvisor();
  }, [fetchAdvisor]);

  if (loading) {
    return (
      <div className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 flex items-center justify-between shadow-2xs">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-sky-100 flex items-center justify-center">
            <Loader2 className="w-4 h-4 text-[#0284c7] animate-spin" />
          </div>
          <span className="text-xs font-medium text-slate-600">
            AI đang tính toán công suất ca vận hành 7 ngày tới và đối chiếu dữ liệu hải văn...
          </span>
        </div>
      </div>
    );
  }

  if (!data || data.recommendations.length === 0) {
    return null;
  }

  const renderBadge = (type: SlotRecommendation['recommendationType']) => {
    switch (type) {
      case 'EXPAND_CAPACITY':
      case 'ADD_SLOT':
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-emerald-100 text-emerald-800">
            Mở rộng công suất
          </span>
        );
      case 'WEATHER_WATCH':
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-100 text-amber-800">
            Theo dõi thời tiết
          </span>
        );
      case 'PROMOTE_OR_CONSOLIDATE':
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-100 text-sky-800">
            Kích cầu / Gộp ca
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-100 text-slate-700">
            Tối ưu
          </span>
        );
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-sky-200/80 shadow-2xs overflow-hidden transition-all">
      {/* Header Strip */}
      <div className="p-4 bg-linear-to-r from-sky-50/70 via-white to-sky-50/40 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-[#0284c7]/10 text-[#0284c7] flex items-center justify-center border border-[#0284c7]/20 shrink-0">
            <Sparkles className="w-4 h-4 text-[#0284c7]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-bold text-slate-900 tracking-tight">
                Cố vấn Lịch & Công suất vận hành (AI Slot Advisor)
              </h3>
              <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-100 text-sky-800 font-mono">
                7 NGÀY
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Đã rà soát <strong className="font-mono text-slate-800">{data.totalSlotsEvaluated}</strong> ca vận hành. Tìm thấy{' '}
              <strong className="text-emerald-700 font-semibold">{data.highDemandSlotsCount}</strong> ca đắt khách,{' '}
              <strong className="text-amber-700 font-semibold">{data.lowDemandSlotsCount}</strong> ca vắng khách.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-end sm:self-center">
          <button
            onClick={fetchAdvisor}
            className="p-2 rounded-lg text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors cursor-pointer"
            title="Làm mới phân tích AI"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setIsExpanded((prev) => !prev)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-800 text-xs font-bold cursor-pointer transition-colors shadow-2xs"
          >
            <span>{isExpanded ? 'Thu gọn gợi ý' : 'Xem chi tiết gợi ý'}</span>
            {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>

      {/* Expanded Recommendations */}
      {isExpanded && (
        <div className="p-4 border-t border-sky-100 bg-slate-50/50 space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {data.recommendations.slice(0, 6).map((rec, idx) => (
              <div
                key={rec.slotId || idx}
                className="p-3.5 bg-white rounded-xl border border-slate-200/80 shadow-2xs space-y-2 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 pb-2 border-b border-slate-100">
                    <span className="text-xs font-bold text-slate-900 truncate">
                      {rec.serviceTitle}
                    </span>
                    {renderBadge(rec.recommendationType)}
                  </div>

                  <div className="flex items-center gap-3 text-xs text-slate-600 mt-2 font-mono">
                    <span className="inline-flex items-center gap-1">
                      <Clock className="w-3 h-3 text-sky-600" />
                      {rec.date} • {rec.startTime?.slice(0, 5)} - {rec.endTime?.slice(0, 5)}
                    </span>
                    <span className="font-semibold text-slate-800">
                      Tồn: {rec.bookedCount}/{rec.capacity} ({Math.round(rec.occupancyRate)}%)
                    </span>
                  </div>

                  <p className="text-xs text-slate-700 font-semibold mt-1.5">
                    {rec.actionTitle}
                  </p>
                  <p className="text-[11px] text-slate-500 leading-relaxed mt-0.5">
                    {rec.adviceReason}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
