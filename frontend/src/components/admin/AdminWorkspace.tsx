import React, { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, LoaderCircle, X } from 'lucide-react';

export function AdminButton({ loading = false, variant = 'secondary', className = '', disabled, children, type = 'button', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { loading?: boolean; variant?: 'primary' | 'secondary' | 'danger' }) {
  return <button {...props} type={type} className={`admin-button ${variant === 'secondary' ? '' : `admin-button--${variant}`} ${className}`}
    disabled={disabled || loading} aria-busy={loading || undefined}>{loading && <LoaderCircle size={16} className="admin-spin" aria-hidden="true" />}{children}</button>;
}

export function AdminAccordionSection({ id, title, label, description, open, onToggle, headerHidden = false, className = '', children }: {
  id: string; title: ReactNode; label?: string; description?: string; open: boolean; onToggle: () => void; headerHidden?: boolean; className?: string; children: ReactNode;
}) {
  return <section className={`admin-accordion ${className}`} data-open={open} data-header-hidden={headerHidden}>
    <button id={`${id}-heading`} type="button" className="admin-accordion-trigger" aria-label={label} aria-expanded={open} aria-controls={id}
      aria-hidden={headerHidden || undefined} inert={headerHidden || undefined} onClick={onToggle}>
      <span className="admin-accordion-title">{title}{description && <small>{description}</small>}</span><ChevronDown size={16} className="admin-accordion-chevron" aria-hidden="true" />
    </button>
    <div id={id} className="admin-accordion-panel" role="region" aria-labelledby={`${id}-heading`} aria-hidden={!open || undefined} inert={!open || undefined}>
      <div className="admin-accordion-clip"><div className="admin-accordion-content">{children}</div></div>
    </div>
  </section>;
}

export function AdminDisclosure({ title, description, defaultOpen = false, children }: { title: string; description?: string; defaultOpen?: boolean; children: ReactNode }) {
  const [open, setOpen] = useState(defaultOpen);
  const id = useId();
  return <AdminAccordionSection id={id} title={title} description={description} open={open} onToggle={() => setOpen(value => !value)} className="admin-disclosure">{children}</AdminAccordionSection>;
}

export function AdminFilterTabs({ label, value, items, onChange }: {
  label: string; value: string; items: { value: string; label: string; count?: number }[]; onChange: (value: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState<{ left: number; width: number; ready: boolean }>({ left: 0, width: 0, ready: false });

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const updateIndicator = () => {
      const activeBtn = container.querySelector<HTMLButtonElement>('button[aria-pressed="true"]');
      if (activeBtn) {
        setIndicator({
          left: activeBtn.offsetLeft,
          width: activeBtn.offsetWidth,
          ready: true,
        });
      }
    };
    updateIndicator();
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [value, items]);

  return <div ref={containerRef} className="admin-filter-tabs" role="group" aria-label={label}>
    {items.map(item => <button key={item.value} type="button" aria-pressed={item.value === value} onClick={() => onChange(item.value)}>
      {item.label}{item.count !== undefined && <span>{item.count}</span>}
    </button>)}
    {indicator.ready && (
      <span
        className="admin-filter-tabs__indicator"
        aria-hidden="true"
        style={{
          transform: `translateX(${indicator.left}px)`,
          width: `${indicator.width}px`,
        }}
      />
    )}
  </div>;
}

export function AdminPreviewBanner(_props: { demo: boolean; href: string }) {
  return null;
}

export function AdminNotice({ message, onDismiss }: { message: string | null; onDismiss: () => void }) {
  return <div className="admin-notice" data-visible={Boolean(message)} role="status" aria-live="polite" aria-atomic="true">
    <CheckCircle2 size={18} aria-hidden="true" /><span>{message}</span>{message && <button type="button" onClick={onDismiss} aria-label="Đóng thông báo"><X size={16} /></button>}
  </div>;
}

export interface AdminPaginationProps {
  currentPage: number;
  totalPages: number;
  totalRecords: number;
  pageSize?: number;
  recordLabel?: string;
  onPageChange: (page: number) => void;
  className?: string;
  hideOnSinglePage?: boolean;
}

export function AdminPagination({
  currentPage,
  totalPages,
  totalRecords,
  pageSize = 8,
  recordLabel = 'bản ghi',
  onPageChange,
  className = '',
  hideOnSinglePage = false,
}: AdminPaginationProps) {
  if (totalRecords === 0) return null;
  if (hideOnSinglePage && totalPages <= 1 && totalRecords <= pageSize) return null;

  const safeTotalPages = Math.max(1, totalPages);
  const validCurrentPage = Math.max(1, Math.min(currentPage, safeTotalPages));
  const fromRecord = Math.min((validCurrentPage - 1) * pageSize + 1, totalRecords);
  const toRecord = Math.min(validCurrentPage * pageSize, totalRecords);

  const isFirstPage = validCurrentPage <= 1;
  const isLastPage = validCurrentPage >= safeTotalPages;

  return (
    <nav
      className={`admin-pagination ${className}`}
      role="navigation"
      aria-label={`Phân trang ${recordLabel}`}
    >
      <div className="admin-pagination__info">
        Hiển thị <strong>{fromRecord}–{toRecord}</strong> / <strong>{totalRecords}</strong> {recordLabel}
      </div>
      <div className="admin-pagination__controls">
        <button
          type="button"
          className="admin-pagination__btn admin-pagination__btn--nav admin-pagination__btn--first"
          disabled={isFirstPage}
          onClick={() => onPageChange(1)}
          title="Về trang đầu"
          aria-label="Về trang đầu"
        >
          <ChevronsLeft size={15} aria-hidden="true" />
        </button>

        <button
          type="button"
          className="admin-pagination__btn admin-pagination__btn--prev"
          disabled={isFirstPage}
          onClick={() => onPageChange(validCurrentPage - 1)}
          title="Trang trước"
          aria-label="Trang trước"
        >
          <ChevronLeft size={15} aria-hidden="true" style={{ marginRight: 2 }} />
          <span>Trước</span>
        </button>

        <div className="admin-pagination__pages" role="group" aria-label="Trang hiện tại">
          <button
            type="button"
            className="admin-pagination__btn admin-pagination__btn--page admin-pagination__btn--active"
            aria-current="page"
            aria-label={`Trang hiện tại ${validCurrentPage}`}
            disabled
          >
            {validCurrentPage}
          </button>
        </div>

        <button
          type="button"
          className="admin-pagination__btn admin-pagination__btn--next"
          disabled={isLastPage}
          onClick={() => onPageChange(validCurrentPage + 1)}
          title="Trang sau"
          aria-label="Trang sau"
        >
          <span>Sau</span>
          <ChevronRight size={15} aria-hidden="true" style={{ marginLeft: 2 }} />
        </button>

        <button
          type="button"
          className="admin-pagination__btn admin-pagination__btn--nav admin-pagination__btn--last"
          disabled={isLastPage}
          onClick={() => onPageChange(safeTotalPages)}
          title="Về trang cuối"
          aria-label="Về trang cuối"
        >
          <ChevronsRight size={15} aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}
