import React, { useState, useEffect, useCallback } from 'react';
import {
  CalendarDays,
  ChevronDown,
  ChevronUp,
  Clock,
  RefreshCw,
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

  // Tailored Skeleton loader matching banner geometry
  if (loading) {
    return (
      <div className="bg-white border border-slate-200/90 rounded-2xl p-4 flex items-center justify-between animate-pulse">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-slate-100 border border-slate-200 shrink-0" />
          <div className="space-y-1.5">
            <div className="h-4 w-44 bg-slate-200 rounded" />
            <div className="h-3 w-80 bg-slate-100 rounded" />
          </div>
        </div>
        <div className="h-7 w-28 bg-slate-100 rounded-xl" />
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
          <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200/80">
            Mở rộng công suất
          </span>
        );
      case 'WEATHER_WATCH':
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-amber-50 text-amber-800 border border-amber-200/80">
            Theo dõi thời tiết
          </span>
        );
      case 'PROMOTE_OR_CONSOLIDATE':
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-sky-50 text-sky-800 border border-sky-200/80">
            Kích cầu / Gộp ca
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            Tối ưu
          </span>
        );
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200/90 shadow-2xs overflow-hidden">
      {/* Header Strip - Grounded white, clean hairline border */}
      <div className="p-4 bg-white flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200/80 shrink-0">
            <CalendarDays className="w-4 h-4 text-slate-700" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-xs font-semibold text-slate-900 tracking-tight">
                Cố vấn lịch trình & Tồn chỗ 7 ngày
              </h3>
            </div>
            <p className="text-xs text-slate-600 font-mono tabular-nums mt-0.5">
              Đã rà soát {data.totalSlotsEvaluated} ca vận hành • {data.highDemandSlotsCount} ca đắt khách • {data.lowDemandSlotsCount} ca vắng khách
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-end sm:self-center">
          <button
            onClick={fetchAdvisor}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            title="Làm mới phân tích"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setIsExpanded((prev) => !prev)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold cursor-pointer transition-colors"
          >
            <span>{isExpanded ? 'Thu gọn' : 'Xem chi tiết gợi ý'}</span>
            {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>

      {/* Expanded Recommendations */}
      {isExpanded && (
        <div className="p-4 bg-slate-50/60 space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {data.recommendations.slice(0, 6).map((rec, idx) => (
              <div
                key={rec.slotId || idx}
                className="p-3.5 bg-white rounded-xl border border-slate-200/80 space-y-2 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 pb-2 border-b border-slate-100">
                    <span className="text-xs font-semibold text-slate-900 truncate">
                      {rec.serviceTitle}
                    </span>
                    {renderBadge(rec.recommendationType)}
                  </div>

                  <div className="flex items-center gap-3 text-xs text-slate-600 mt-2 font-mono tabular-nums">
                    <span className="inline-flex items-center gap-1">
                      <Clock className="w-3 h-3 text-slate-400" />
                      {rec.date} • {rec.startTime?.slice(0, 5)} - {rec.endTime?.slice(0, 5)}
                    </span>
                    <span className="font-semibold text-slate-800">
                      Tồn: {rec.bookedCount}/{rec.capacity} ({Math.round(rec.occupancyRate)}%)
                    </span>
                  </div>

                  <p className="text-xs text-slate-800 font-semibold mt-1.5">
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
