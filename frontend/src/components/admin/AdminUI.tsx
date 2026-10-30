import { AlertCircle, Inbox, LoaderCircle } from 'lucide-react';
import type { ReactNode } from 'react';

export function AdminBadge({ children, tone = 'teal' }: { children: ReactNode; tone?: 'teal' | 'amber' | 'rose' | 'neutral' }) {
  return <span className={`admin-badge admin-badge--${tone}`}><span aria-hidden="true" />{children}</span>;
}
export function AdminPageHeader({ eyebrow, title, description, actions }: {
  eyebrow: string; title: string; description: string; actions?: ReactNode;
}) {
  return <div className="admin-page-header"><div><p className="admin-eyebrow">{eyebrow}</p><h1>{title}</h1><p className="admin-description">{description}</p></div>{actions && <div className="admin-page-actions">{actions}</div>}</div>;
}
export function AdminState({ kind, title, description, action, compact = false }: {
  kind: 'loading' | 'empty' | 'error'; title: string; description?: string; action?: ReactNode; compact?: boolean;
}) {
  const Icon = kind === 'loading' ? LoaderCircle : kind === 'error' ? AlertCircle : Inbox;
  return <div className={`admin-state ${compact ? 'admin-state--compact' : ''} admin-state--${kind}`} role={kind === 'error' ? 'alert' : 'status'} aria-live="polite">
    <span className="admin-state-icon"><Icon size={22} className={kind === 'loading' ? 'admin-spin' : ''} aria-hidden="true" /></span><h3>{title}</h3>{description && <p>{description}</p>}{action}
  </div>;
}
