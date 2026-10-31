import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { animateAdminDialog } from './dialogMotion';

export function AdminDialog({ open, onClose, title, description, children, footer, variant = 'modal' }: {
  open: boolean; onClose: () => void; title: string; description?: string; children: ReactNode; footer?: ReactNode; variant?: 'modal' | 'drawer';
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const id = useId();
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    let cancel = animateAdminDialog(dialog, open, media.matches, variant === 'drawer');
    const onPreference = () => { cancel(); cancel = animateAdminDialog(dialog, open, media.matches, variant === 'drawer'); };
    media.addEventListener('change', onPreference);
    return () => { cancel(); media.removeEventListener('change', onPreference); };
  }, [open, variant]);
  useEffect(() => {
    const dialog = dialogRef.current;
    return () => { if (dialog?.open) dialog.close(); };
  }, []);

  if (typeof document === 'undefined') return null;
  return createPortal(<div className="admin-shell admin-overlay-root"><dialog ref={dialogRef} className={`admin-dialog admin-dialog--${variant}`} aria-labelledby={`${id}-title`} aria-describedby={description ? `${id}-description` : undefined}
    onCancel={event => { event.preventDefault(); onClose(); }} onClick={event => {
      if (event.target !== event.currentTarget) return;
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
    }}>
    <div className="admin-dialog-frame"><header className="admin-dialog-header"><div><h2 id={`${id}-title`}>{title}</h2>{description && <p id={`${id}-description`}>{description}</p>}</div>
      <button type="button" className="admin-icon-button" onClick={onClose} aria-label={variant === 'drawer' ? 'Đóng chi tiết' : 'Đóng hộp thoại'}><X size={20} /></button>
    </header><div className="admin-dialog-body">{children}</div>{footer && <footer className="admin-dialog-footer">{footer}</footer>}</div>
  </dialog></div>, document.body);
}
