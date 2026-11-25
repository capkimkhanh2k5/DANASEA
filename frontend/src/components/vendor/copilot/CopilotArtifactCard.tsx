import { 
  TrendingUp, 
  Star, 
  CalendarClock, 
  FileCheck, 
  CheckCircle2, 
  AlertTriangle,
  ShieldAlert,
  Layers,
  Tag
} from 'lucide-react';
import type { CopilotArtifact } from '../../../types/vendorAi';
import { getBadgeTierLabel, normalizeBadgeTier } from '../../../utils/badgeTier';
import {
  formatAssessmentStatusLabel,
  formatContentReasonLabel,
} from '../../../pages/admin/Copilot/copilotArtifactHelpers';

const REVIEW_PILLAR_LABELS: Record<string, string> = {
  GUIDE: 'Hướng dẫn viên',
  SAFETY: 'An toàn',
  PUNCTUALITY: 'Đúng giờ',
  VALUE: 'Giá trị',
};

const SERVICE_STATUS_LABELS: Record<string, string> = {
  PUBLISHED: 'Đang mở bán',
  DRAFT: 'Bản nháp',
  PENDING_REVIEW: 'Chờ duyệt',
  REJECTED: 'Bị từ chối',
  PAUSED: 'Tạm dừng',
};

const PRICING_UNIT_LABELS: Record<string, string> = {
  PER_PERSON: 'mỗi khách',
  PER_PACKAGE: 'mỗi gói',
};

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
    const badgeTier = normalizeBadgeTier(typeof data.badgeTier === 'string' ? data.badgeTier : undefined);

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <TrendingUp className="w-3.5 h-3.5 text-sky-600" />
            <span>Chỉ số kinh doanh gian hàng</span>
          </div>
          <span
            className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700"
            title={badgeTier}
          >
            Hạng: {getBadgeTierLabel(badgeTier)}
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
            <div key={pillarKey} className="p-2 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-between" title={pillarKey}>
              <span className="text-[11px] font-medium text-slate-700">
                {REVIEW_PILLAR_LABELS[pillarKey.toUpperCase()] || pillarKey}
              </span>
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
  if (type === 'VENDOR_SLOT_ADVISORY_CARD' || type === 'VENDOR_SLOT_ADVISOR_CARD') {
    const totalSlots = Number(data.totalSlotsEvaluated || 0);
    const recommendations = Array.isArray(data.recommendations)
      ? (data.recommendations as Array<{ serviceTitle?: string; date?: unknown; startTime?: unknown; adviceReason?: string }>)
      : [];
    const formatDateVal = (val: unknown): string => {
      if (Array.isArray(val) && val.length >= 3) {
        return `${val[0]}-${String(val[1]).padStart(2, '0')}-${String(val[2]).padStart(2, '0')}`;
      }
      return typeof val === 'string' ? val : '';
    };
    const formatTimeVal = (val: unknown): string => {
      if (Array.isArray(val) && val.length >= 2) {
        return `${String(val[0]).padStart(2, '0')}:${String(val[1]).padStart(2, '0')}`;
      }
      return typeof val === 'string' ? val.slice(0, 5) : '';
    };

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <CalendarClock className="w-3.5 h-3.5 text-sky-600" />
            <span>Tư vấn ca vận hành ({totalSlots} ca rà soát)</span>
          </div>
        </div>

        {recommendations.length === 0 ? (
          <div className="p-2 rounded-lg bg-slate-50 border border-slate-200/80 text-slate-600 flex items-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
            <span>
              {totalSlots === 0
                ? 'Chưa có ca hoạt động nào được mở trong khoảng thời gian tra cứu.'
                : 'Tất cả các ca tour đều có tỷ lệ lấp đầy tối ưu và thời tiết an toàn.'}
            </span>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {recommendations.slice(0, 3).map((rec, idx) => (
              <div key={idx} className="p-2.5 rounded-lg bg-slate-50 border border-slate-200/80 flex flex-col gap-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-slate-900 text-[11px]">{rec.serviceTitle || 'Ca tour'}</span>
                  <span className="text-[10px] font-mono tabular-nums text-slate-500">
                    {formatDateVal(rec.date)} ({formatTimeVal(rec.startTime)})
                  </span>
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
  if (type === 'VENDOR_LISTING_DRAFT_CARD' || type === 'VENDOR_OPTIMIZE_LISTING_CARD') {
    const qualityScore = Number(data.listingQualityScore || 0);
    const missing = Array.isArray(data.missingItems) 
      ? (data.missingItems as Array<{ description?: string; suggestion?: string }>) 
      : [];

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <FileCheck className="w-3.5 h-3.5 text-slate-700" />
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

  // 5. Content Moderation / Text Assessment Card (V25)
  if (type === 'COPILOT_CONTENT_ASSESSMENT_CARD' || artifact.tool === 'vendor_text_assessment') {
    const status = String(data.status || 'NEEDS_REVIEW');
    const reasonCodes = Array.isArray(data.reasonCodes) ? data.reasonCodes.map(String) : [];
    const contactMatches = Array.isArray(data.contactMatches) ? data.contactMatches.map(String) : [];
    const negatedWarningOnly = Boolean(data.negatedWarningOnly);
    const needsReview = status === 'NEEDS_REVIEW';

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900">
            <ShieldAlert className={`w-3.5 h-3.5 ${needsReview ? 'text-amber-600' : 'text-emerald-600'}`} />
            <span>Thẩm định nội dung bài đăng</span>
          </div>
          <span
            title={status}
            className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
              needsReview ? 'bg-amber-50 text-amber-800 border border-amber-200' : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
            }`}
          >
            {formatAssessmentStatusLabel(status)}
          </span>
        </div>

        <div className="flex flex-wrap gap-1.5 text-[11px] text-slate-600">
          {typeof data.serviceId === 'string' && (
            <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded">Dịch vụ: {data.serviceId}</span>
          )}
          <span className="bg-slate-100 px-1.5 py-0.5 rounded">Không tự duyệt xuất bản</span>
          {negatedWarningOnly && (
            <span className="bg-sky-50 text-sky-800 border border-sky-200 px-1.5 py-0.5 rounded">
              Chỉ là câu cảnh báo phủ định
            </span>
          )}
        </div>

        {reasonCodes.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {reasonCodes.map((code) => (
              <span key={code} title={code} className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-amber-50 text-amber-800 border border-amber-200">
                {formatContentReasonLabel(code)}
              </span>
            ))}
          </div>
        )}

        {contactMatches.length > 0 && (
          <div className="text-[11px] text-rose-700 bg-rose-50 border border-rose-100 rounded-md p-2">
            Phát hiện mẫu liên hệ/giao dịch ngoài: <span className="font-mono">{contactMatches.join(', ')}</span>
          </div>
        )}
      </div>
    );
  }

  // 6. Service Count Card
  if (type === 'VENDOR_SERVICE_COUNT_CARD' || artifact.tool === 'vendor_service_count') {
    const total = Number(data.total || 0);
    const byStatus = (data.byStatus && typeof data.byStatus === 'object'
      ? data.byStatus
      : {}) as Record<string, unknown>;
    const entries = Object.entries(byStatus);

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900 min-w-0">
            <Layers className="w-3.5 h-3.5 text-sky-600 shrink-0" />
            <span className="truncate">Thống kê dịch vụ đối tác</span>
          </div>
          <span className="font-mono font-bold text-slate-800 shrink-0 whitespace-nowrap">
            {total} dịch vụ
          </span>
        </div>
        {entries.length > 0 && (
          <div className="grid grid-cols-2 gap-1.5">
            {entries.map(([statusKey, countVal]) => (
              <div key={statusKey} title={statusKey} className="flex items-center justify-between gap-2 bg-slate-50 border border-slate-200/80 rounded-lg px-2 py-1 text-[11px]">
                <span className="text-slate-600 font-medium truncate">
                  {SERVICE_STATUS_LABELS[statusKey.toUpperCase()] || statusKey}
                </span>
                <span className="font-mono font-bold text-slate-900 shrink-0">{Number(countVal || 0)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  // 7. Service Detail & Pricing Card
  if (type === 'VENDOR_SERVICE_DETAIL_CARD' || artifact.tool === 'vendor_service_detail') {
    const title = String(data.title || 'Dịch vụ');
    const categoryName = String(data.categoryName || '');
    const status = String(data.status || 'PUBLISHED');
    const basePrice = data.basePrice !== null && data.basePrice !== undefined ? Number(data.basePrice) : null;
    const durationMinutes = data.durationMinutes ? Number(data.durationMinutes) : null;
    const capacityPerSlot = data.capacityPerSlot ? Number(data.capacityPerSlot) : null;
    const options = Array.isArray(data.options)
      ? (data.options as Array<{ id?: string; name?: string; pricingUnit?: string; price?: number; maxPaxPerPackage?: number | null }>)
      : [];

    return (
      <div className="mt-3 p-3.5 bg-white border border-slate-200/90 rounded-xl shadow-xs text-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-2">
          <div className="flex items-center gap-1.5 font-bold text-slate-900 min-w-0">
            <Tag className="w-3.5 h-3.5 text-sky-600 shrink-0" />
            <span className="truncate" title={title}>{title}</span>
          </div>
          <span
            title={status}
            className={`text-[10px] font-semibold px-2 py-0.5 rounded-md border shrink-0 whitespace-nowrap ${
              status.toUpperCase() === 'PUBLISHED'
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : status.toUpperCase() === 'PENDING_REVIEW'
                  ? 'bg-amber-50 text-amber-800 border-amber-200'
                  : 'bg-slate-100 text-slate-700 border-slate-200'
            }`}
          >
            {SERVICE_STATUS_LABELS[status.toUpperCase()] || status}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {basePrice !== null && Number.isFinite(basePrice) && (
            <div className="p-2 rounded-lg bg-slate-50 border border-slate-100">
              <span className="text-[10px] text-slate-500 font-medium block">Giá niêm yết</span>
              <span className="text-sm font-bold text-sky-700 font-mono tabular-nums">
                {basePrice.toLocaleString('vi-VN')} ₫
              </span>
            </div>
          )}
          {(durationMinutes || capacityPerSlot || categoryName) && (
            <div className="p-2 rounded-lg bg-slate-50 border border-slate-100 flex flex-col justify-center gap-0.5 text-[11px] text-slate-700">
              {categoryName && <span className="font-semibold text-slate-800 truncate">{categoryName}</span>}
              <span>
                {[
                  durationMinutes ? `${durationMinutes} phút` : null,
                  capacityPerSlot ? `${capacityPerSlot} khách/ca` : null,
                ].filter(Boolean).join(' · ') || 'Thông tin vận hành'}
              </span>
            </div>
          )}
        </div>

        {options.length > 0 && (
          <div className="flex flex-col gap-1.5 pt-1 border-t border-slate-100">
            <span className="text-[11px] font-semibold text-slate-700">Các gói giá đang áp dụng:</span>
            {options.map((opt, idx) => {
              const unitKey = String(opt.pricingUnit || 'PER_PERSON').toUpperCase();
              const unitLabel = PRICING_UNIT_LABELS[unitKey] || unitKey;
              return (
                <div
                  key={opt.id || idx}
                  className="flex items-center justify-between gap-3 bg-slate-50 border border-slate-200/80 rounded-lg px-2.5 py-1.5 text-[11px]"
                >
                  <span className="font-medium text-slate-800 truncate">{opt.name || `Gói #${idx + 1}`}</span>
                  <span className="font-mono font-bold text-sky-700 shrink-0 whitespace-nowrap">
                    {Number(opt.price || 0).toLocaleString('vi-VN')} ₫{' '}
                    <span className="text-[10px] font-sans font-normal text-slate-500">/ {unitLabel}</span>
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  return null;
}
