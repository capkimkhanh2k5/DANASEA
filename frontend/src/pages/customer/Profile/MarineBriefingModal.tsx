import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  Compass,
  ShieldCheck,
  Check,
  AlertTriangle,
  Info,
  X,
  Clock,
  MapPin,
  CheckCircle2,
  Waves,
} from 'lucide-react';
import { aiService } from '../../../services/aiService';
import type { MarineBriefingOverview, SubOrderBriefing } from '../../../types/marineBriefing';

interface MarineBriefingModalProps {
  orderId: string;
  orderCode: string;
  isOpen: boolean;
  onClose: () => void;
  isEn?: boolean;
}

export const MarineBriefingModal: React.FC<MarineBriefingModalProps> = ({
  orderId,
  orderCode,
  isOpen,
  onClose,
  isEn = false,
}) => {
  const [loading, setLoading] = useState<boolean>(true);
  const [briefing, setBriefing] = useState<MarineBriefingOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checkedItems, setCheckedItems] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!isOpen || !orderId) return;

    let active = true;
    setLoading(true);
    setError(null);

    aiService.getMarineBriefing(orderId)
      .then((data) => {
        if (!active) return;
        setBriefing(data);
      })
      .catch((err) => {
        if (!active) return;
        console.error('[MarineBriefing] Fetch failed:', err);
        setError(isEn ? 'Failed to load marine briefing.' : 'Không thể tải cẩm nang biển lúc này.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [isOpen, orderId, isEn]);

  if (!isOpen) return null;

  const toggleItem = (key: string) => {
    setCheckedItems((prev) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  const renderWeatherBadge = (status: SubOrderBriefing['weatherSafetyStatus']) => {
    switch (status) {
      case 'ACCEPTABLE':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200/80 whitespace-nowrap">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
            {isEn ? 'Safe to Depart' : 'An toàn xuất bến'}
          </span>
        );
      case 'CAUTION':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200/80 whitespace-nowrap">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0" />
            {isEn ? 'Caution Wave Conditions' : 'Lưu ý sóng gió'}
          </span>
        );
      case 'UNSAFE':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-rose-50 text-rose-800 border border-rose-200/80 whitespace-nowrap">
            <AlertTriangle className="w-3.5 h-3.5 text-rose-600 shrink-0" />
            {isEn ? 'Severe Sea Warning' : 'Cảnh báo biển động'}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200/80 whitespace-nowrap">
            <Info className="w-3.5 h-3.5 text-slate-500 shrink-0" />
            {isEn ? 'Check at Harbor' : 'Xác nhận tại bến'}
          </span>
        );
    }
  };

  // Compute total items and ready count
  const allActivities = briefing?.activities || [];
  let totalChecklistItems = 0;
  let readyChecklistItems = 0;
  allActivities.forEach((act) => {
    (act.packingChecklist || []).forEach((_, idx) => {
      totalChecklistItems++;
      if (checkedItems[`${act.subOrderId}-${idx}`]) {
        readyChecklistItems++;
      }
    });
  });

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/60 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-2xl max-h-[88vh] bg-white rounded-2xl border border-slate-200 shadow-2xl flex flex-col overflow-hidden text-slate-900"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header - Solid white, clean hairline border, no decorative slop */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200/80 bg-white">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200/80 shrink-0">
              <Compass className="w-4.5 h-4.5 text-[#0284c7]" />
            </div>
            <div>
              <h2 className="text-sm sm:text-base font-semibold text-slate-900 tracking-tight">
                {isEn ? 'Marine Briefing & Preparation Checklist' : 'Cẩm nang ra khơi & Danh mục chuẩn bị'}
              </h2>
              <p className="text-xs text-slate-500 font-mono mt-0.5">
                {isEn ? `Order #${orderCode}` : `Mã đơn #${orderCode}`}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Skeleton Loader matching layout geometry */}
          {loading && (
            <div className="space-y-5 animate-pulse">
              <div className="space-y-2">
                <div className="h-5 w-48 bg-slate-200 rounded-md" />
                <div className="h-4 w-72 bg-slate-100 rounded-md" />
              </div>
              <div className="h-14 bg-slate-100 rounded-xl border border-slate-200/70" />
              <div className="space-y-2">
                <div className="h-4 w-36 bg-slate-200 rounded-md" />
                <div className="rounded-xl border border-slate-200/80 divide-y divide-slate-100 overflow-hidden bg-white">
                  {[1, 2, 3, 4].map((i) => (
                    <div key={i} className="p-3.5 flex items-center gap-3">
                      <div className="w-4 h-4 rounded bg-slate-200 shrink-0" />
                      <div className="flex-1 space-y-1.5">
                        <div className="h-3.5 w-1/3 bg-slate-200 rounded" />
                        <div className="h-3 w-2/3 bg-slate-100 rounded" />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {!loading && error && (
            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">{error}</p>
                <p className="text-amber-800 mt-1">
                  {isEn
                    ? 'Please verify departure conditions directly with your tour operator at the pier.'
                    : 'Vui lòng xác nhận điều kiện xuất bến trực tiếp với đơn vị tổ chức tại cầu cảng.'}
                </p>
              </div>
            </div>
          )}

          {!loading && !error && briefing && (
            <>
              {briefing.activities.map((act, actIdx) => (
                <div key={act.subOrderId || actIdx} className="space-y-5">
                  {/* Service & Operational Specs */}
                  <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 pb-4 border-b border-slate-100">
                    <div className="space-y-1.5">
                      <h3 className="text-base font-semibold text-slate-900">
                        {act.serviceTitle}
                      </h3>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
                        <span className="inline-flex items-center gap-1.5 font-mono">
                          <Clock className="w-3.5 h-3.5 text-slate-400" />
                          <span>{act.departureDate}</span>
                          <span className="text-slate-300">•</span>
                          <span>{act.departureTime?.slice(0, 5)}</span>
                        </span>
                        <span className="inline-flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                          <span>{act.meetingLocation}</span>
                        </span>
                      </div>
                    </div>
                    <div className="shrink-0 self-start sm:self-auto">
                      {renderWeatherBadge(act.weatherSafetyStatus)}
                    </div>
                  </div>

                  {/* Marine Conditions Summary Strip */}
                  {act.marineConditionsSummary && (
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5">
                      <Waves className="w-4 h-4 text-[#0284c7] shrink-0 mt-0.5" />
                      <p className="text-xs leading-relaxed text-slate-700">
                        {act.marineConditionsSummary}
                      </p>
                    </div>
                  )}

                  {/* Checklist Section - Bordered List Group */}
                  {act.packingChecklist && act.packingChecklist.length > 0 && (
                    <div className="space-y-2.5">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-slate-900">
                          {isEn ? 'Preparation Checklist' : 'Hành lý & Đồ cần chuẩn bị'}
                        </span>
                        <span className="text-xs font-mono tabular-nums text-slate-500">
                          {act.packingChecklist.filter((_, idx) => checkedItems[`${act.subOrderId}-${idx}`]).length}/{act.packingChecklist.length}
                        </span>
                      </div>

                      <div className="rounded-xl border border-slate-200/90 bg-white divide-y divide-slate-100 overflow-hidden shadow-2xs">
                        {act.packingChecklist.map((item, itemIdx) => {
                          const itemKey = `${act.subOrderId}-${itemIdx}`;
                          const isChecked = Boolean(checkedItems[itemKey]);

                          return (
                            <button
                              key={itemKey}
                              type="button"
                              onClick={() => toggleItem(itemKey)}
                              className={`w-full text-left flex items-start gap-3 p-3.5 transition-colors cursor-pointer select-none ${
                                isChecked
                                  ? 'bg-slate-50/70'
                                  : 'hover:bg-slate-50/50'
                              }`}
                            >
                              <div
                                className={`w-4 h-4 rounded mt-0.5 flex items-center justify-center border shrink-0 transition-colors ${
                                  isChecked
                                    ? 'bg-[#0284c7] border-[#0284c7] text-white'
                                    : 'border-slate-300 bg-white hover:border-slate-400'
                                }`}
                              >
                                {isChecked && <Check className="w-3 h-3 stroke-[3]" />}
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <span
                                    className={`text-xs font-medium transition-colors ${
                                      isChecked
                                        ? 'line-through text-slate-400'
                                        : 'text-slate-800'
                                    }`}
                                  >
                                    {item.itemName}
                                  </span>
                                  {item.mandatory ? (
                                    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-50 text-rose-700 border border-rose-200/70">
                                      {isEn ? 'Required' : 'Bắt buộc'}
                                    </span>
                                  ) : (
                                    <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-slate-100 text-slate-600">
                                      {isEn ? 'Optional' : 'Khuyên dùng'}
                                    </span>
                                  )}
                                </div>
                                {item.tip && (
                                  <p
                                    className={`text-[11px] mt-0.5 leading-relaxed ${
                                      isChecked ? 'text-slate-400' : 'text-slate-500'
                                    }`}
                                  >
                                    {item.tip}
                                  </p>
                                )}
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Maritime Safety Notice - Clean, unbloated */}
                  {act.arrivalAndSafetyGuidelines && act.arrivalAndSafetyGuidelines.length > 0 && (
                    <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
                      <div className="flex items-center gap-2 text-xs font-semibold text-slate-900">
                        <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                        <span>{isEn ? 'Maritime Safety Guidelines' : 'Quy chuẩn an toàn hàng hải'}</span>
                      </div>
                      <ul className="space-y-1 text-xs text-slate-600 leading-relaxed pl-6 list-disc">
                        {act.arrivalAndSafetyGuidelines.map((g, gIdx) => (
                          <li key={gIdx}>{g}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              ))}
            </>
          )}
        </div>

        {/* Footer - No fake claim slogans, crisp metric & action button */}
        <div className="px-6 py-3 border-t border-slate-200/80 bg-white flex items-center justify-between">
          <div className="text-xs text-slate-600 font-mono">
            {isEn ? 'Prepared: ' : 'Đã chuẩn bị: '}
            <span className="font-semibold text-slate-900 tabular-nums">
              {readyChecklistItems}/{totalChecklistItems}
            </span>
          </div>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition-colors cursor-pointer shadow-xs"
          >
            {isEn ? 'Close' : 'Đã hiểu & Đóng'}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
};
