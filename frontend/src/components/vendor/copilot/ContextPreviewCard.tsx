import React from 'react';
import { 
  ShoppingBag, 
  Compass, 
  Star, 
  Scale, 
  RotateCcw, 
  Calendar, 
  Anchor, 
  CreditCard,
  Paperclip
} from 'lucide-react';
import type { AttachedContext, VendorContextType } from '../../../store/useVendorCopilotStore';
import { formatDisplayCode } from '../../../utils/displayCode';

interface ContextPreviewCardProps {
  context: AttachedContext;
  className?: string;
}

const CONTEXT_ICON_MAP: Record<VendorContextType, React.ComponentType<{ className?: string }>> = {
  ORDER: ShoppingBag,
  SERVICE: Compass,
  REVIEW: Star,
  DISPUTE: Scale,
  REFUND: RotateCcw,
  SLOT: Calendar,
  EQUIPMENT: Anchor,
  PAYOUT: CreditCard,
};

const CONTEXT_BADGE_MAP: Record<VendorContextType, string> = {
  ORDER: 'Đơn hàng',
  SERVICE: 'Dịch vụ / Tour',
  REVIEW: 'Đánh giá',
  DISPUTE: 'Tranh chấp',
  REFUND: 'Hoàn tiền',
  SLOT: 'Ca vận hành',
  EQUIPMENT: 'Tàu bè / Thiết bị',
  PAYOUT: 'Đối soát / Rút tiền',
};

export const ContextPreviewCard: React.FC<ContextPreviewCardProps> = ({ context, className = '' }) => {
  const Icon = CONTEXT_ICON_MAP[context.type] || Compass;
  const categoryLabel = (context.meta?.categoryLabel as string) || CONTEXT_BADGE_MAP[context.type] || 'Ngữ cảnh';
  const title = (context.meta?.title as string) || context.label;
  const subtitle = (context.meta?.subtitle as string) || undefined;
  const badge = (context.meta?.badge as string) || undefined;
  const detailSpecs = (context.meta?.detailSpecs as Array<{ label: string; value: string }>) || undefined;

  return (
    <div
      className={`rounded-xl border border-sky-200/90 bg-sky-50/70 p-2.5 max-w-sm sm:max-w-md w-full text-slate-800 shadow-2xs select-none transition-all ${className}`}
    >
      <div className="flex items-start gap-2.5">
        <div className="w-8 h-8 rounded-lg bg-sky-600 text-white flex items-center justify-center shrink-0 shadow-2xs">
          <Icon className="w-4 h-4" />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-0.5">
            <span className="inline-flex items-center gap-1 text-[10px] font-semibold tracking-wide uppercase text-sky-700 bg-sky-100/90 px-1.5 py-0.5 rounded">
              <Paperclip className="w-2.5 h-2.5" />
              <span>{categoryLabel}</span>
            </span>

            {badge && (
              <span className="text-[10px] font-medium text-emerald-700 bg-emerald-100/80 px-1.5 py-0.5 rounded ml-auto shrink-0">
                {badge}
              </span>
            )}
          </div>

          <h4 className="text-xs font-bold text-slate-900 truncate" title={title}>
            {title}
          </h4>

          {subtitle && (
            <p className="text-[11px] text-slate-600 truncate mt-0.5">
              {subtitle}
            </p>
          )}

          {detailSpecs && detailSpecs.length > 0 && (
            <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 mt-2 pt-1.5 border-t border-sky-200/60 text-[10px]">
              {detailSpecs.slice(0, 4).map((spec, idx) => (
                <div key={idx} className="truncate">
                  <span className="text-slate-500 mr-1">{spec.label}:</span>
                  <span className="font-semibold text-slate-800">{spec.value}</span>
                </div>
              ))}
            </div>
          )}

          <div className="mt-1 text-[9px] font-mono text-slate-400">
            ID: #{formatDisplayCode(context.id, context.type === 'ORDER' ? 'ORD' : context.type === 'SERVICE' ? 'SRV' : 'CTX')}
          </div>
        </div>
      </div>
    </div>
  );
};
