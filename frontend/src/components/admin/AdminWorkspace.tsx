import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, LoaderCircle, X } from 'lucide-react';

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
  hideOnSinglePage = true,
}: AdminPaginationProps) {
  if (totalRecords === 0) return null;
  if (hideOnSinglePage && totalPages <= 1 && totalRecords <= pageSize) return null;

  const fromRecord = Math.min((currentPage - 1) * pageSize + 1, totalRecords);
  const toRecord = Math.min(currentPage * pageSize, totalRecords);

  const getPageNumbers = () => {
    if (totalPages <= 5) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }
    const pages: (number | 'ellipsis')[] = [];
    pages.push(1);
    if (currentPage > 3) {
      pages.push('ellipsis');
    }
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    if (currentPage < totalPages - 2) {
      pages.push('ellipsis');
    }
    pages.push(totalPages);
    return pages;
  };

  const pageNumbers = getPageNumbers();

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
          className="admin-pagination__btn admin-pagination__btn--prev"
          disabled={currentPage <= 1}
          onClick={() => onPageChange(currentPage - 1)}
          aria-label="Trang trước"
        >
          <ChevronLeft size={15} aria-hidden="true" style={{ marginRight: 2 }} />
          Trước
        </button>

        <div className="admin-pagination__pages" role="group" aria-label="Các trang">
          {pageNumbers.map((p, idx) => {
            if (p === 'ellipsis') {
              return (
                <span key={`ellipsis-${idx}`} className="admin-pagination__ellipsis" aria-hidden="true">
                  …
                </span>
              );
            }
            const isActive = p === currentPage;
            return (
              <button
                key={p}
                type="button"
                className={`admin-pagination__btn admin-pagination__btn--page ${
                  isActive ? 'admin-pagination__btn--active' : ''
                }`}
                aria-current={isActive ? 'page' : undefined}
                aria-label={`Trang ${p}`}
                onClick={() => onPageChange(p)}
              >
                {p}
              </button>
            );
          })}
        </div>

        <button
          type="button"
          className="admin-pagination__btn admin-pagination__btn--next"
          disabled={currentPage >= totalPages}
          onClick={() => onPageChange(currentPage + 1)}
          aria-label="Trang sau"
        >
          Sau
          <ChevronRight size={15} aria-hidden="true" style={{ marginLeft: 2 }} />
        </button>
      </div>
    </nav>
  );
}
