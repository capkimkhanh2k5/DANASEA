import React from 'react';
import { useToastStore, type ToastType } from '../../store/useToastStore';
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X } from 'lucide-react';

const toastConfig: Record<ToastType, { icon: React.ReactNode; cardClass: string; iconClass: string; defaultTitle: string }> = {
  success: {
    icon: <CheckCircle2 className="w-5 h-5 flex-shrink-0" />,
    cardClass: 'bg-emerald-50/95 dark:bg-emerald-950/90 border-emerald-300 text-emerald-900 dark:text-emerald-100 shadow-emerald-500/15',
    iconClass: 'text-emerald-600 dark:text-emerald-400',
    defaultTitle: 'Thành công',
  },
  error: {
    icon: <AlertCircle className="w-5 h-5 flex-shrink-0" />,
    cardClass: 'bg-rose-50/95 dark:bg-rose-950/90 border-rose-300 text-rose-900 dark:text-rose-100 shadow-rose-500/15',
    iconClass: 'text-rose-600 dark:text-rose-400',
    defaultTitle: 'Thông báo lỗi',
  },
  warning: {
    icon: <AlertTriangle className="w-5 h-5 flex-shrink-0" />,
    cardClass: 'bg-amber-50/95 dark:bg-amber-950/90 border-amber-300 text-amber-900 dark:text-amber-100 shadow-amber-500/15',
    iconClass: 'text-amber-600 dark:text-amber-400',
    defaultTitle: 'Cảnh báo',
  },
  info: {
    icon: <Info className="w-5 h-5 flex-shrink-0" />,
    cardClass: 'bg-cyan-50/95 dark:bg-cyan-950/90 border-cyan-300 text-cyan-900 dark:text-cyan-100 shadow-cyan-500/15',
    iconClass: 'text-cyan-600 dark:text-cyan-400',
    defaultTitle: 'Thông tin',
  },
};

export const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useToastStore();

  if (!toasts || toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      className="fixed top-5 right-5 z-[99999] flex flex-col gap-2.5 max-w-md w-[calc(100vw-2.5rem)] sm:w-96 pointer-events-none transition-all duration-300"
    >
      {toasts.map((t) => {
        const config = toastConfig[t.type] || toastConfig.info;
        const title = t.title || (t.type === 'error' ? 'Không thể thực hiện' : t.type === 'success' ? 'Thành công' : undefined);

        return (
          <div
            key={t.id}
            role="alert"
            className={`pointer-events-auto rounded-2xl p-4 backdrop-blur-xl border shadow-xl flex items-start gap-3 transition-all duration-300 transform translate-y-0 opacity-100 animate-in fade-in slide-in-from-top-3 ${config.cardClass}`}
          >
            <div className={`mt-0.5 ${config.iconClass}`}>{config.icon}</div>
            <div className="flex-1 min-w-0 pr-1">
              {title && (
                <h4 className="text-xs font-bold leading-snug mb-0.5 tracking-wide uppercase opacity-90">
                  {title}
                </h4>
              )}
              <p className="text-xs font-medium leading-relaxed break-words">
                {t.message}
              </p>
            </div>
            <button
              onClick={() => removeToast(t.id)}
              className="p-1 rounded-lg hover:bg-black/5 dark:hover:bg-white/10 opacity-60 hover:opacity-100 transition-opacity flex-shrink-0 text-current"
              title="Đóng thông báo"
              type="button"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
};
