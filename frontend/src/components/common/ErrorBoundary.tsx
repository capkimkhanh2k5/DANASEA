import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { RotateCcw, AlertTriangle } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[DANASEA ErrorBoundary] Caught render error:', error, errorInfo);

    // Auto-reload on Vite dynamic import chunk load failures caused by builds/restarts
    const isChunkError =
      error.message?.includes('Failed to fetch dynamically imported module') ||
      error.message?.includes('Importing a module script failed') ||
      error.name === 'ChunkLoadError';

    if (isChunkError && !sessionStorage.getItem('danasea_chunk_reload_retry')) {
      sessionStorage.setItem('danasea_chunk_reload_retry', '1');
      window.location.reload();
    }
  }

  private handleReload = () => {
    sessionStorage.removeItem('danasea_chunk_reload_retry');
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 p-6 select-none">
          <div className="max-w-md w-full bg-white rounded-2xl border border-slate-200/90 p-6 shadow-md text-center">
            <div className="w-12 h-12 rounded-2xl bg-amber-50 border border-amber-200/80 text-amber-600 flex items-center justify-center mx-auto mb-4">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h2 className="text-base font-semibold text-slate-900 mb-1.5">
              Đã làm mới dữ liệu hệ thống
            </h2>
            <p className="text-xs text-slate-500 mb-5 leading-relaxed">
              Phiên bản giao diện hoặc tài nguyên vừa được cập nhật. Vui lòng tải lại trang để tải phiên bản mới nhất.
            </p>
            <button
              type="button"
              onClick={this.handleReload}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 transition-colors duration-150 cursor-pointer shadow-sm w-full"
            >
              <RotateCcw className="w-4 h-4" />
              <span>Tải lại trang</span>
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
