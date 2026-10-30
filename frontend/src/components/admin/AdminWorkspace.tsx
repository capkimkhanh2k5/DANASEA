import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, ChevronDown, FlaskConical, LoaderCircle, X } from 'lucide-react';

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

export function AdminPreviewBanner({ demo, href }: { demo: boolean; href: string }) {
  return <div className="admin-data-banner admin-workspace-preview"><FlaskConical size={18} aria-hidden="true" /><span>{demo
    ? <><strong>DEMO MODE</strong> · Dữ liệu mẫu. Các thao tác chỉ thay đổi bản xem thử.</>
    : <>Bản xem trước sử dụng <strong>dữ liệu mẫu</strong>. Chưa phải hồ sơ hay giao dịch của hệ thống.</>}</span>
    {!demo && import.meta.env.DEV && <Link to={href}>Bật thao tác demo</Link>}
    {demo && <span className="admin-preview-label">Không thực hiện giao dịch thật</span>}
  </div>;
}

export function AdminNotice({ message, onDismiss }: { message: string | null; onDismiss: () => void }) {
  return <div className="admin-notice" data-visible={Boolean(message)} role="status" aria-live="polite" aria-atomic="true">
    <CheckCircle2 size={18} aria-hidden="true" /><span>{message}</span>{message && <button type="button" onClick={onDismiss} aria-label="Đóng thông báo"><X size={16} /></button>}
  </div>;
}
