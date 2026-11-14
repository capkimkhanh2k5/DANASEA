import { 
  TrendingUp, 
  Star, 
  CalendarClock, 
  Wand2, 
  CheckCircle2, 
  AlertTriangle 
} from 'lucide-react';
import type { CopilotArtifact } from '../../../types/vendorAi';

interface CopilotArtifactCardProps {
  artifact: CopilotArtifact;
}

export function CopilotArtifactCard({ artifact }: CopilotArtifactCardProps) {
  const type = artifact.type || '';
  const data = (artifact.data || {}) as Record<string, unknown>;

  // 1. KPI Summary Card
  if (type === 'VENDOR_KPI_SUMMARY_CARD') {
    const gmv = Number(data.gmv || 0);
    const netPayout = Number(data.netPayout || 0);
    const totalBookings = Number(data.totalBookings || 0);
    const cancelledBookings = Number(data.cancelledBookings || 0);
    const cancellationRate = Number(data.cancellationRate || 0);
    const ratingAvg = Number(data.ratingAvg || 0);
    const ratingCount = Number(data.ratingCount || 0);
    const badgeTier = String(data.badgeTier || 'STANDARD');

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <TrendingUp className="w-3.5 h-3.5 text-sky-600" />
            <span>Chỉ số kinh doanh gian hàng</span>
          </div>
          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700">
            Hạng: {badgeTier}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100">
            <span className="text-[10px] text-slate-500 font-medium block">Tổng GMV</span>
            <span className="text-sm font-bold text-slate-900 font-mono tabular-nums">
              {gmv.toLocaleString('vi-VN')} đ
            </span>
          </div>
          <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100">
            <span className="text-[10px] text-slate-500 font-medium block">Thực thu dự kiến</span>
            <span className="text-sm font-bold text-emerald-700 font-mono tabular-nums">
              {netPayout.toLocaleString('vi-VN')} đ
            </span>
          </div>
          <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100">
            <span className="text-[10px] text-slate-500 font-medium block">Đơn đặt chỗ</span>
            <span className="text-xs font-bold text-slate-900 font-mono tabular-nums">
              {totalBookings} đơn ({cancelledBookings} đã hủy - {cancellationRate}%)
            </span>
          </div>
          <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100">
            <span className="text-[10px] text-slate-500 font-medium block">Đánh giá trung bình</span>
            <span className="text-xs font-bold text-amber-700 flex items-center gap-1 font-mono tabular-nums">
              <Star className="w-3 h-3 fill-amber-400 text-amber-400" />
              <span>{ratingAvg.toFixed(1)} / 5.0</span>
              <span className="text-[10px] text-slate-400 font-normal">({ratingCount} lượt)</span>
            </span>
          </div>
        </div>
      </div>
    );
  }

  // 2. Review Coaching Card
  if (type === 'VENDOR_REVIEW_COACHING_CARD') {
    const pillars = (data.pillars || {}) as Record<string, { scoreOutOf10?: number }>;
    const actionItems = Array.isArray(data.actionItems) 
      ? (data.actionItems as Array<{ title?: string; recommendation?: string }>) 
      : [];
    const totalReviews = Number(data.totalReviewsAnalyzed || 0);

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <Star className="w-3.5 h-3.5 text-amber-500 fill-amber-500" />
            <span>Phân tích 4 trụ cột đánh giá</span>
          </div>
          <span className="text-[10px] text-slate-500 font-mono tabular-nums">
            {totalReviews} đánh giá đã phân tích
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {Object.entries(pillars).map(([pillarKey, p]) => (
            <div key={pillarKey} className="p-2 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-between">
              <span className="text-[11px] font-medium text-slate-700">{pillarKey}</span>
              <span className="text-xs font-bold text-slate-900 font-mono tabular-nums">
                {p?.scoreOutOf10 ?? 0}/10
              </span>
            </div>
          ))}
        </div>

        {actionItems.length > 0 && (
          <div className="flex flex-col gap-1.5 pt-1 border-t border-slate-100">
            <span className="text-[11px] font-semibold text-slate-800">Khuyến nghị khắc phục:</span>
            {actionItems.slice(0, 2).map((item, idx) => (
              <div key={idx} className="p-2 rounded-lg bg-sky-50/60 border border-sky-100 text-[11px] text-sky-950 flex flex-col gap-0.5">
                <span className="font-semibold">{item.title}</span>
                <span className="text-slate-600">{item.recommendation}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  // 3. Slot Advisor Card
  if (type === 'VENDOR_SLOT_ADVISOR_CARD') {
    const totalSlots = Number(data.totalSlotsEvaluated || 0);
    const recommendations = Array.isArray(data.recommendations) 
      ? (data.recommendations as Array<{ serviceTitle?: string; date?: string; startTime?: string; adviceReason?: string }>) 
      : [];

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <CalendarClock className="w-3.5 h-3.5 text-sky-600" />
            <span>Tư vấn ca vận hành ({totalSlots} ca rà soát)</span>
          </div>
        </div>

        {recommendations.length === 0 ? (
          <div className="p-2 rounded-lg bg-emerald-50 border border-emerald-100 text-emerald-800 flex items-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
            <span>Tất cả các ca tour đều có tỷ lệ lấp đầy tối ưu và thời tiết an toàn.</span>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {recommendations.slice(0, 3).map((rec, idx) => (
              <div key={idx} className="p-2.5 rounded-lg bg-slate-50 border border-slate-200/80 flex flex-col gap-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-slate-900 text-[11px]">{rec.serviceTitle || 'Ca tour'}</span>
                  <span className="text-[10px] font-mono tabular-nums text-slate-500">{rec.date} ({rec.startTime})</span>
                </div>
                <p className="text-[11px] text-slate-600">{rec.adviceReason}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  // 4. Optimize Listing Card
  if (type === 'VENDOR_OPTIMIZE_LISTING_CARD') {
    const qualityScore = Number(data.listingQualityScore || 0);
    const missing = Array.isArray(data.missingItems) 
      ? (data.missingItems as Array<{ description?: string; suggestion?: string }>) 
      : [];

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <Wand2 className="w-3.5 h-3.5 text-sky-600" />
            <span>Đánh giá tối ưu bài đăng</span>
          </div>
          <span className="text-xs font-bold text-sky-700 font-mono tabular-nums">
            {qualityScore}/100 điểm
          </span>
        </div>

        {missing.length > 0 && (
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-semibold text-slate-700">Tiêu chí cần bổ sung:</span>
            {missing.slice(0, 2).map((item, idx) => (
              <div key={idx} className="flex items-start gap-1.5 text-[11px] text-amber-800 bg-amber-50 p-1.5 rounded-md border border-amber-100">
                <AlertTriangle className="w-3 h-3 text-amber-600 shrink-0 mt-0.5" />
                <span>{item.description}: {item.suggestion}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  return null;
}
