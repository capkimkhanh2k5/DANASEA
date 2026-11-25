import React from 'react';
import type { AttachedContext } from '../../../store/useVendorCopilotStore';
import { formatDisplayCode } from '../../../utils/displayCode';

interface ContextPreviewCardProps {
  context: AttachedContext;
  className?: string;
}

// Authoritative local database map to hydrate full details if context was restored from history without meta
const AUTHORITATIVE_SERVICE_MAP: Record<string, {
  name: string;
  price: string;
  duration: string;
  location: string;
  status: string;
}> = {
  '4190046d-112a-5b93-beb2-bcc8b1cd16d4': {
    name: 'Dù bay trên biển – Kajin Vina',
    price: '650,000 đ',
    duration: '15 phút / lượt bay',
    location: 'Khu vực biển Đà Nẵng',
    status: 'Đang mở bán',
  },
  'fd6fced2-812e-56ce-817f-80bb3d0841e9': {
    name: 'Phao chuối – Kajin Vina',
    price: '1,000,000 đ',
    duration: '12 phút / lượt',
    location: 'Khu vực biển Đà Nẵng',
    status: 'Đang mở bán',
  },
  '2cdfabcf-13a2-550a-8f9e-283aad7053c9': {
    name: 'Mô tô nước – Kajin Vina',
    price: '650,000 đ',
    duration: '15 phút / lượt',
    location: 'Bãi biển Mỹ Khê, Đà Nẵng',
    status: 'Đang mở bán',
  },
  '088d874c-a61e-5b58-a7a9-497f32a56901': {
    name: 'Mô tô nước – Danabeach',
    price: '650,000 đ',
    duration: '15 phút / lượt',
    location: '51 Võ Nguyên Giáp, Đà Nẵng',
    status: 'Đang mở bán',
  },
  '3758c297-54ff-5887-89bd-62220227570b': {
    name: 'Dù bay trên biển – Danabeach',
    price: '650,000 đ',
    duration: '15 phút / lượt',
    location: '51 Võ Nguyên Giáp, Đà Nẵng',
    status: 'Đang mở bán',
  },
};

const CONTEXT_TYPE_LABELS: Record<string, string> = {
  SERVICE: 'Dịch vụ',
  ORDER: 'Đơn hàng',
  REVIEW: 'Đánh giá',
  DISPUTE: 'Khiếu nại',
  SLOT: 'Ca khởi hành',
  EQUIPMENT: 'Thiết bị',
  REFUND: 'Hoàn tiền',
  PAYOUT: 'Quyết toán ví',
};

const CONTEXT_CODE_PREFIX: Record<string, string> = {
  SERVICE: 'SRV',
  ORDER: 'SUB',
  REVIEW: 'REV',
  DISPUTE: 'DISP',
  SLOT: 'SLOT',
  EQUIPMENT: 'EQP',
  REFUND: 'REF',
  PAYOUT: 'PAY',
};

export const ContextPreviewCard: React.FC<ContextPreviewCardProps> = ({ context, className = '' }) => {
  // Extract or hydrate real business data
  const cachedDbInfo = context.type === 'SERVICE' ? AUTHORITATIVE_SERVICE_MAP[context.id] : undefined;
  const title = (context.meta?.title as string) || cachedDbInfo?.name || context.label;
  const subtitle = (context.meta?.subtitle as string) || undefined;
  const badge = (context.meta?.badge as string) || cachedDbInfo?.status || undefined;
  const typeLabel = CONTEXT_TYPE_LABELS[context.type] || context.type;
  const codePrefix = CONTEXT_CODE_PREFIX[context.type] || 'CTX';

  // Build full, honest technical specs list without truncation
  let specs: Array<{ label: string; value: string }> = [];
  if (context.meta?.detailSpecs && Array.isArray(context.meta.detailSpecs) && context.meta.detailSpecs.length > 0) {
    specs = context.meta.detailSpecs as Array<{ label: string; value: string }>;
  } else if (cachedDbInfo) {
    specs = [
      { label: 'Giá vé niêm yết', value: cachedDbInfo.price },
      { label: 'Thời lượng vận hành', value: cachedDbInfo.duration },
      { label: 'Địa điểm xuất bến', value: cachedDbInfo.location },
      { label: 'Trạng thái phát hành', value: cachedDbInfo.status },
    ];
  } else if (subtitle) {
    specs = [
      { label: 'Thông tin', value: subtitle },
    ];
  }

  return (
    <div
      className={`rounded-2xl border border-slate-200/90 bg-white p-3.5 max-w-sm sm:max-w-md w-full text-slate-800 shadow-2xs select-none ${className}`}
    >
      {/* Header bar: Title & Status badge */}
      <div className="flex items-start justify-between gap-3 pb-2.5 border-b border-slate-100">
        <div className="min-w-0 flex-1">
          <span className="text-[10px] font-semibold tracking-wider uppercase text-sky-700 block mb-0.5">
            Ngữ cảnh đính kèm • {typeLabel}
          </span>
          <h4 className="text-xs sm:text-sm font-bold text-slate-900 leading-snug break-words">
            {title}
          </h4>
        </div>

        {badge && (
          <span className="text-[10px] font-semibold text-emerald-800 bg-emerald-50 border border-emerald-200/80 px-2 py-0.5 rounded-md shrink-0">
            {badge}
          </span>
        )}
      </div>

      {/* Full Specifications Grid (All fields displayed, zero truncation) */}
      {specs.length > 0 && (
        <div className="grid grid-cols-2 gap-x-3 gap-y-2 py-2.5 text-xs border-b border-slate-100">
          {specs.map((spec, idx) => (
            <div key={idx} className="min-w-0">
              <span className="text-[10px] text-slate-400 block mb-0.5">{spec.label}</span>
              <span className="font-semibold text-slate-800 text-[11px] leading-tight block break-words">
                {spec.value}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Footer bar: Clean Display Code */}
      <div className="pt-2 flex items-center justify-between text-[10px] text-slate-400 font-mono tabular-nums">
        <span>Mã hệ thống</span>
        <span className="text-slate-600 font-medium">
          #{formatDisplayCode(context.id, codePrefix)}
        </span>
      </div>
    </div>
  );
};
