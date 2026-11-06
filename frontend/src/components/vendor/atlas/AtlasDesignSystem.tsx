import React from 'react';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';

/**
 * ATLAS DESIGN SYSTEM PRIMITIVES
 * Grounded directly on Atlas Business Suite Standard (2026 SaaS OTA Pattern)
 * 
 * Rules:
 * - NO AI Slop: No pulsing dots (animate-pulse), no decorative pill tags on headers.
 * - NO Robotic Annotations: Metric cards have crisp labels + big numbers.
 * - Solid surfaces: bg-white with 1px hairline border-slate-200/80 and shadow-xs.
 * - Typography: Titles text-3xl font-bold font-display, labels text-sm font-medium, body text-sm.
 */

// 1. PAGE HEADER
export interface AtlasPageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export function AtlasPageHeader({ title, subtitle, actions }: AtlasPageHeaderProps) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200/80">
      <div>
        <h1 className="text-2xl md:text-3xl font-bold text-slate-900 tracking-tight font-display">
          {title}
        </h1>
        {subtitle && (
          <p className="text-sm text-slate-500 mt-1">
            {subtitle}
          </p>
        )}
      </div>
      {actions && (
        <div className="flex items-center gap-2.5 shrink-0 self-start sm:self-auto">
          {actions}
        </div>
      )}
    </div>
  );
}

// 2. METRIC CARD
export interface AtlasMetricCardProps {
  label: string;
  value: string | number;
  subtext?: string;
  accent?: 'amber' | 'emerald' | 'rose' | 'sky' | 'slate';
  icon?: React.ReactNode;
  onClick?: () => void;
}

export function AtlasMetricCard({
  label,
  value,
  subtext,
  accent,
  icon,
  onClick,
}: AtlasMetricCardProps) {
  const accentClasses = {
    amber: 'border-l-4 border-l-amber-500',
    emerald: 'border-l-4 border-l-emerald-500',
    rose: 'border-l-4 border-l-rose-500',
    sky: 'border-l-4 border-l-sky-500',
    slate: '',
  };

  return (
    <div
      onClick={onClick}
      className={`bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs transition-all ${
        accent ? accentClasses[accent] : ''
      } ${onClick ? 'cursor-pointer hover:border-slate-300' : ''}`}
    >
      <div className="flex items-center justify-between">
        <span className="text-[13px] font-semibold uppercase tracking-wider text-slate-600">
          {label}
        </span>
        {icon && <span className="text-slate-400">{icon}</span>}
      </div>
      <div className="text-2xl md:text-3xl font-bold font-mono tracking-tight text-slate-900 tabular-nums mt-1.5">
        {value}
      </div>
      {subtext && (
        <span className="text-xs text-slate-400 mt-1 block">
          {subtext}
        </span>
      )}
    </div>
  );
}

// 3. SEGMENTED CONTROL TABS
export interface AtlasTabItem<T extends string = string> {
  id: T;
  label: string;
  count?: number;
}

export interface AtlasSegmentedTabsProps<T extends string = string> {
  tabs: AtlasTabItem<T>[];
  activeTab: T;
  onChange: (id: T) => void;
}

export function AtlasSegmentedTabs<T extends string = string>({
  tabs,
  activeTab,
  onChange,
}: AtlasSegmentedTabsProps<T>) {
  return (
    <div className="bg-slate-100/90 p-1 rounded-xl inline-flex items-center gap-1 border border-slate-200/70 overflow-x-auto scrollbar-none max-w-full">
      {tabs.map((tab) => {
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onChange(tab.id)}
            className={`px-3.5 py-1.5 rounded-lg text-sm font-medium transition-all flex items-center gap-2 cursor-pointer whitespace-nowrap ${
              isActive
                ? 'bg-white text-slate-900 shadow-xs font-semibold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <span>{tab.label}</span>
            {tab.count !== undefined && (
              <span
                className={`px-2 py-0.5 rounded-md font-mono tabular-nums text-xs font-bold ${
                  isActive
                    ? 'bg-slate-100 text-slate-900'
                    : 'bg-slate-200/80 text-slate-600'
                }`}
              >
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

// 4. STATUS BADGE
export type AtlasBadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export interface AtlasBadgeProps {
  label: string;
  variant?: AtlasBadgeVariant;
  showDot?: boolean;
  dot?: boolean;
}

export function AtlasBadge({ label, variant = 'neutral', showDot = true, dot }: AtlasBadgeProps) {
  const displayDot = dot !== undefined ? dot : showDot;
  const variantStyles = {
    success: {
      pill: 'bg-emerald-50 text-emerald-700',
      dot: 'bg-emerald-500',
    },
    warning: {
      pill: 'bg-amber-50 text-amber-700',
      dot: 'bg-amber-500',
    },
    danger: {
      pill: 'bg-rose-50 text-rose-700',
      dot: 'bg-rose-500',
    },
    info: {
      pill: 'bg-sky-50 text-sky-700',
      dot: 'bg-sky-500',
    },
    neutral: {
      pill: 'bg-slate-100 text-slate-700',
      dot: 'bg-slate-400',
    },
  };

  const style = variantStyles[variant];

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[13px] font-semibold whitespace-nowrap ${style.pill}`}
    >
      {displayDot && <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${style.dot}`} />}
      <span>{label}</span>
    </span>
  );
}

// 5. INITIAL AVATAR
export function AtlasAvatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const initials = (name || 'KH')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join('');

  const sizeClasses = {
    sm: 'w-7 h-7 text-[11px]',
    md: 'w-8 h-8 text-xs',
    lg: 'w-10 h-10 text-sm',
  };

  return (
    <div
      className={`${sizeClasses[size]} rounded-full bg-slate-900 text-white font-bold flex items-center justify-center shrink-0 tracking-wider`}
    >
      {initials}
    </div>
  );
}

// 6. ACTION BUTTON PRIMITIVES
export function AtlasButton({
  children,
  variant = 'secondary',
  size = 'md',
  onClick,
  icon,
  disabled,
  className = '',
  type = 'button',
}: {
  children: React.ReactNode;
  variant?: 'primary' | 'secondary' | 'outline' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  onClick?: () => void;
  icon?: React.ReactNode;
  disabled?: boolean;
  className?: string;
  type?: 'button' | 'submit' | 'reset';
}) {
  const variantStyles = {
    primary: 'bg-slate-900 text-white hover:bg-black shadow-xs',
    secondary: 'bg-slate-100 text-slate-800 hover:bg-slate-200/80',
    outline: 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 shadow-xs',
    danger: 'bg-rose-600 text-white hover:bg-rose-700 shadow-xs',
  };

  const sizeStyles = {
    sm: 'px-3 py-1.5 text-xs rounded-lg gap-1.5',
    md: 'px-4 py-2 text-sm rounded-xl gap-2',
    lg: 'px-5 py-2.5 text-base rounded-xl gap-2.5',
  };

  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className={`inline-flex items-center justify-center font-medium transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${variantStyles[variant]} ${sizeStyles[size]} ${className}`}
    >
      {icon && <span className="shrink-0">{icon}</span>}
      <span>{children}</span>
    </button>
  );
}


// 7. ATLAS PAGINATION PRIMITIVE
export interface AtlasPaginationProps {
  currentPage: number;
  totalPages: number;
  totalItems: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}

export function AtlasPagination({
  currentPage,
  totalPages,
  totalItems,
  pageSize,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [8, 12, 20],
}: AtlasPaginationProps) {
  const validCurrentPage = Math.max(1, Math.min(currentPage, totalPages || 1));

  return (
    <div className="px-5 py-3 border-t border-slate-200/80 bg-slate-50/60 flex flex-col sm:flex-row items-center justify-between text-sm select-none gap-3">
      {/* Page Size & Item Count */}
      <div className="flex items-center gap-3">
        <span className="text-slate-500 text-xs">
          Tổng cộng: <strong className="font-mono text-slate-800">{totalItems}</strong> bản ghi
        </span>
        {onPageSizeChange && (
          <div className="flex items-center gap-1.5 pl-3 border-l border-slate-200">
            <span className="text-xs text-slate-500 font-medium">Số dòng:</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-xs font-semibold text-slate-800 cursor-pointer focus:outline-none shadow-2xs"
            >
              {pageSizeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt} / trang
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Pagination Controls */}
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          disabled={validCurrentPage <= 1}
          onClick={() => onPageChange(1)}
          className="p-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
          title="Về trang đầu"
        >
          <ChevronsLeft className="w-4 h-4" />
        </button>

        <button
          type="button"
          disabled={validCurrentPage <= 1}
          onClick={() => onPageChange(validCurrentPage - 1)}
          className="px-2.5 py-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-30 disabled:cursor-not-allowed text-xs font-medium flex items-center gap-1 cursor-pointer transition-colors"
        >
          <ChevronLeft className="w-3.5 h-3.5" />
          <span>Trước</span>
        </button>

        {/* Page Numbers */}
        <div className="flex items-center gap-1 px-1">
          {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map((pageNum) => (
            <button
              key={pageNum}
              type="button"
              onClick={() => onPageChange(pageNum)}
              className={`w-7 h-7 rounded-lg font-bold text-xs transition-colors cursor-pointer ${
                pageNum === validCurrentPage
                  ? "bg-slate-900 text-white shadow-2xs"
                  : "text-slate-700 hover:bg-slate-100 border border-slate-200 bg-white"
              }`}
            >
              {pageNum}
            </button>
          ))}
        </div>

        <button
          type="button"
          disabled={validCurrentPage >= (totalPages || 1)}
          onClick={() => onPageChange(validCurrentPage + 1)}
          className="px-2.5 py-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-30 disabled:cursor-not-allowed text-xs font-medium flex items-center gap-1 cursor-pointer transition-colors"
        >
          <span>Sau</span>
          <ChevronRight className="w-3.5 h-3.5" />
        </button>

        <button
          type="button"
          disabled={validCurrentPage >= (totalPages || 1)}
          onClick={() => onPageChange(totalPages || 1)}
          className="p-1.5 rounded-lg text-slate-700 hover:bg-slate-200/70 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
          title="Đến trang cuối"
        >
          <ChevronsRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
