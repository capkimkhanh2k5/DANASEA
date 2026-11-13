import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  Waves,
  ShieldCheck,
  CheckSquare,
  Square,
  AlertTriangle,
  Info,
  X,
  Sparkles,
  Clock,
  MapPin,
  CheckCircle2,
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
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            {isEn ? 'Sea Condition: Safe / Acceptable' : 'Tình trạng: An toàn xuất bến'}
          </span>
        );
      case 'CAUTION':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
            {isEn ? 'Sea Condition: Caution' : 'Tình trạng: Cần lưu ý sóng gió'}
          </span>
        );
      case 'UNSAFE':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold bg-rose-50 text-rose-700 border border-rose-200">
            <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
            {isEn ? 'Sea Condition: High Risk / Unsafe' : 'Tình trạng: Biển động nguy hiểm'}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold bg-slate-100 text-slate-700 border border-slate-200">
            <Info className="w-3.5 h-3.5 text-slate-500" />
            {isEn ? 'Awaiting Dock Confirmation' : 'Chờ xác nhận thực tế tại bến'}
          </span>
        );
    }
  };

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-2xl max-h-[90vh] bg-white rounded-2xl border border-slate-200/90 shadow-2xl flex flex-col overflow-hidden text-slate-800"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-linear-to-r from-sky-50/80 via-white to-sky-50/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#0284c7]/10 text-[#0284c7] flex items-center justify-center border border-[#0284c7]/20">
              <Waves className="w-5 h-5 text-[#0284c7]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-slate-900 tracking-tight">
                  {isEn ? '24h Marine Briefing & Checklist' : 'Cẩm nang ra khơi 24h & Checklist an toàn'}
                </h2>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-sky-100 text-sky-800 font-mono">
                  <Sparkles className="w-3 h-3 text-[#0284c7]" />
                  AI
                </span>
              </div>
              <p className="text-xs text-slate-500 font-mono">
                {isEn ? `Order ${orderCode}` : `Mã đơn ${orderCode}`}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading && (
            <div className="py-12 flex flex-col items-center justify-center gap-3 text-center">
              <div className="w-10 h-10 border-3 border-sky-200 border-t-[#0284c7] rounded-full animate-spin" />
              <p className="text-sm font-medium text-slate-600">
                {isEn ? 'AI is compiling maritime conditions & packing checklist...' : 'AI đang tổng hợp điều kiện hải văn và danh mục chuẩn bị...'}
              </p>
            </div>
          )}

          {!loading && error && (
            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-sm flex items-start gap-2.5">
              <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">{error}</p>
                <p className="text-xs text-amber-700 mt-1">
                  {isEn ? 'Please check with your local operator upon departure.' : 'Vui lòng liên hệ hướng dẫn viên hoặc bến thuyền trước khi xuất bến.'}
                </p>
              </div>
            </div>
          )}

          {!loading && !error && briefing && (
            <>
              {briefing.activities.map((act, actIdx) => (
                <div
                  key={act.subOrderId || actIdx}
                  className="bg-slate-50/70 border border-slate-200/80 rounded-2xl p-5 space-y-5"
                >
                  {/* Service & Schedule Header */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-200/70">
                    <div>
                      <h3 className="text-base font-bold text-slate-900">
                        {act.serviceTitle}
                      </h3>
                      <div className="flex flex-wrap items-center gap-4 text-xs text-slate-600 mt-1.5">
                        <span className="inline-flex items-center gap-1.5 font-medium">
                          <Clock className="w-3.5 h-3.5 text-sky-600" />
                          {act.departureDate} • {act.departureTime?.slice(0, 5)}
                        </span>
                        <span className="inline-flex items-center gap-1.5 text-slate-500">
                          <MapPin className="w-3.5 h-3.5 text-slate-400" />
                          {act.meetingLocation}
                        </span>
                      </div>
                    </div>
                    {renderWeatherBadge(act.weatherSafetyStatus)}
                  </div>

                  {/* Marine Conditions Summary */}
                  {act.marineConditionsSummary && (
                    <div className="p-3.5 rounded-xl bg-white border border-sky-100 flex items-start gap-2.5 shadow-xs">
                      <Waves className="w-4 h-4 text-[#0284c7] shrink-0 mt-0.5" />
                      <p className="text-xs leading-relaxed text-slate-700">
                        {act.marineConditionsSummary}
                      </p>
                    </div>
                  )}

                  {/* Checklist Section */}
                  {act.packingChecklist && act.packingChecklist.length > 0 && (
                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-slate-600 flex items-center gap-1.5">
                          <CheckSquare className="w-4 h-4 text-sky-600" />
                          {isEn ? 'Packing & Preparation Checklist' : 'Danh mục chuẩn bị đồ cần mang'}
                        </h4>
                        <span className="text-[11px] text-slate-500 font-mono">
                          {Object.values(checkedItems).filter(Boolean).length}/{act.packingChecklist.length} {isEn ? 'ready' : 'đã xong'}
                        </span>
                      </div>

                      <div className="grid grid-cols-1 gap-2">
                        {act.packingChecklist.map((item, itemIdx) => {
                          const itemKey = `${act.subOrderId}-${itemIdx}`;
                          const isChecked = Boolean(checkedItems[itemKey]);

                          return (
                            <div
                              key={itemKey}
                              onClick={() => toggleItem(itemKey)}
                              className={`flex items-start gap-3 p-3 rounded-xl border transition-colors cursor-pointer select-none ${
                                isChecked
                                  ? 'bg-sky-50/70 border-sky-200 text-slate-900'
                                  : 'bg-white border-slate-200/80 hover:border-slate-300 text-slate-700'
                              }`}
                            >
                              <div className="shrink-0 mt-0.5 text-sky-600">
                                {isChecked ? (
                                  <CheckSquare className="w-4 h-4 text-[#0284c7]" />
                                ) : (
                                  <Square className="w-4 h-4 text-slate-400" />
                                )}
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <span className={`text-xs font-semibold ${isChecked ? 'line-through text-slate-500' : 'text-slate-800'}`}>
                                    {item.itemName}
                                  </span>
                                  {item.mandatory ? (
                                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">
                                      {isEn ? 'Mandatory' : 'Bắt buộc'}
                                    </span>
                                  ) : (
                                    <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-slate-100 text-slate-600">
                                      {isEn ? 'Recommended' : 'Khuyên dùng'}
                                    </span>
                                  )}
                                </div>
                                {item.tip && (
                                  <p className="text-[11px] text-slate-500 mt-0.5 leading-snug">
                                    {item.tip}
                                  </p>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Safety Guidelines Section */}
                  {act.arrivalAndSafetyGuidelines && act.arrivalAndSafetyGuidelines.length > 0 && (
                    <div className="p-4 rounded-xl bg-sky-50/50 border border-sky-100/80 space-y-2">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-sky-900">
                        <ShieldCheck className="w-4 h-4 text-[#0284c7]" />
                        <span>{isEn ? 'Maritime Safety Guidelines' : 'Quy chuẩn an toàn hàng hải & cứu sinh'}</span>
                      </div>
                      <ul className="list-disc list-inside space-y-1 text-xs text-slate-700 leading-relaxed">
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

        {/* Footer */}
        <div className="px-6 py-3.5 border-t border-slate-100 bg-slate-50 flex items-center justify-between">
          <span className="text-[11px] text-slate-500">
            {isEn ? 'Verified by DANASEA Port & Maritime Protocol' : 'Đối soát theo tiêu chuẩn an toàn hàng hải DANASEA'}
          </span>
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold transition-colors cursor-pointer shadow-xs"
          >
            {isEn ? 'Got it' : 'Đã hiểu & Đóng'}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
};
