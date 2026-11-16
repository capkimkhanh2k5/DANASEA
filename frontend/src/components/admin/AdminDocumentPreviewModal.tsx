import { useState, useEffect, useRef, useCallback } from 'react';
import { AlertCircle, FileText, Maximize2, Minimize2, RotateCcw, ZoomIn, ZoomOut } from 'lucide-react';
import { AdminDialog } from './AdminDialog';
import {
  DEFAULT_ZOOM,
  zoomIn,
  zoomOut,
  resetZoom,
  isZoomInDisabled,
  isZoomOutDisabled,
  isResetDisabled,
  calculateZoomWithCursor,
  clampPan,
} from './documentViewerModel';
import './AdminDocumentPreviewModal.css';

export interface AdminDocumentPreviewModalProps {
  open: boolean;
  onClose: () => void;
  imageUrl?: string | null;
  title?: string;
  description?: string;
  onRetry?: () => void;
  isRetrying?: boolean;
}

export function AdminDocumentPreviewModal({
  open,
  onClose,
  imageUrl,
  title = 'Xem trước chứng từ',
  description = 'Hồ sơ chứng từ pháp lý & an toàn hàng hải',
  onRetry,
  isRetrying = false,
}: AdminDocumentPreviewModalProps) {
  const [zoom, setZoom] = useState<number>(DEFAULT_ZOOM);
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isExpanded, setIsExpanded] = useState<boolean>(false);
  const [imageLoading, setImageLoading] = useState<boolean>(true);
  const [imageError, setImageError] = useState<boolean>(false);
  const [retryKey, setRetryKey] = useState<number>(0);

  const viewportRef = useRef<HTMLDivElement>(null);
  const dragStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const lastWheelTimeRef = useRef<number>(0);
  const accumulatedDeltaRef = useRef<number>(0);

  const isHttpUrl = Boolean(imageUrl && imageUrl.startsWith('http'));

  // Reset zoom, position, loading & error states when modal opens or document changes
  useEffect(() => {
    if (open) {
      setZoom(DEFAULT_ZOOM);
      setPosition({ x: 0, y: 0 });
      setIsDragging(false);
      setImageError(false);
      setImageLoading(Boolean(imageUrl && imageUrl.startsWith('http')));
    }
  }, [open, imageUrl, retryKey]);

  // Zoom handlers for toolbar buttons
  const handleZoomIn = useCallback(() => {
    setZoom(prev => {
      const next = zoomIn(prev);
      if (next !== prev) {
        const rect = viewportRef.current?.getBoundingClientRect() || { width: 800, height: 600 };
        setPosition(pos => clampPan(pos, next, { width: rect.width, height: rect.height }));
      }
      return next;
    });
  }, []);

  const handleZoomOut = useCallback(() => {
    setZoom(prev => {
      const next = zoomOut(prev);
      if (next !== prev) {
        if (next <= DEFAULT_ZOOM) {
          setPosition({ x: 0, y: 0 });
        } else {
          const rect = viewportRef.current?.getBoundingClientRect() || { width: 800, height: 600 };
          setPosition(pos => clampPan(pos, next, { width: rect.width, height: rect.height }));
        }
      }
      return next;
    });
  }, []);

  const handleReset = useCallback(() => {
    const r = resetZoom();
    setZoom(r.zoom);
    setPosition(r.position);
  }, []);

  const handleToggleExpand = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  // Mouse Wheel Zoom directly on the viewport (throttled & zooms towards cursor)
  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport || !open) return;

    const handleWheel = (e: WheelEvent) => {
      e.preventDefault(); // Do not scroll modal or background page
      if (imageLoading || imageError || !isHttpUrl) return;

      const now = performance.now();
      if (now - lastWheelTimeRef.current > 150) {
        accumulatedDeltaRef.current = 0;
      }
      accumulatedDeltaRef.current += e.deltaY;

      // Throttle: minimum 70ms between zoom steps and 25px delta threshold
      if (now - lastWheelTimeRef.current < 70 || Math.abs(accumulatedDeltaRef.current) < 25) {
        return;
      }

      const direction = accumulatedDeltaRef.current < 0 ? 1 : -1; // up = zoom in, down = zoom out
      accumulatedDeltaRef.current = 0;
      lastWheelTimeRef.current = now;

      const rect = viewport.getBoundingClientRect();
      const cursorInViewport = {
        x: e.clientX - rect.left - rect.width / 2,
        y: e.clientY - rect.top - rect.height / 2,
      };

      setZoom(currentZoom => {
        const nextZoom = direction > 0 ? zoomIn(currentZoom) : zoomOut(currentZoom);
        if (nextZoom === currentZoom) return currentZoom;

        setPosition(currentPos => {
          const targetPos = calculateZoomWithCursor(currentZoom, nextZoom, currentPos, cursorInViewport);
          return clampPan(targetPos, nextZoom, { width: rect.width, height: rect.height });
        });

        return nextZoom;
      });
    };

    viewport.addEventListener('wheel', handleWheel, { passive: false });
    return () => {
      viewport.removeEventListener('wheel', handleWheel);
    };
  }, [open, imageLoading, imageError, isHttpUrl]);

  // Mouse pan handlers
  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.button !== 0 || imageLoading || imageError || !isHttpUrl) return;
    e.preventDefault();
    setIsDragging(true);
    dragStartRef.current = {
      x: e.clientX - position.x,
      y: e.clientY - position.y,
    };
  };

  // Touch pan handlers
  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    if (e.touches.length === 1 && !imageLoading && !imageError && isHttpUrl) {
      setIsDragging(true);
      dragStartRef.current = {
        x: e.touches[0].clientX - position.x,
        y: e.touches[0].clientY - position.y,
      };
    }
  };

  const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    if (!isDragging || e.touches.length !== 1) return;
    const rect = viewportRef.current?.getBoundingClientRect() || { width: 800, height: 600 };
    const rawPos = {
      x: e.touches[0].clientX - dragStartRef.current.x,
      y: e.touches[0].clientY - dragStartRef.current.y,
    };
    setPosition(clampPan(rawPos, zoom, { width: rect.width, height: rect.height }));
  };

  const handleTouchEnd = () => {
    setIsDragging(false);
  };

  // Window-level mouse listeners for smooth dragging even when cursor leaves viewport
  useEffect(() => {
    if (!isDragging) return;

    const onWindowMouseMove = (e: MouseEvent) => {
      const rect = viewportRef.current?.getBoundingClientRect() || { width: 800, height: 600 };
      const rawPos = {
        x: e.clientX - dragStartRef.current.x,
        y: e.clientY - dragStartRef.current.y,
      };
      setPosition(clampPan(rawPos, zoom, { width: rect.width, height: rect.height }));
    };

    const onWindowMouseUp = () => {
      setIsDragging(false);
    };

    window.addEventListener('mousemove', onWindowMouseMove);
    window.addEventListener('mouseup', onWindowMouseUp);

    return () => {
      window.removeEventListener('mousemove', onWindowMouseMove);
      window.removeEventListener('mouseup', onWindowMouseUp);
    };
  }, [isDragging, zoom]);

  // Retry handler
  const handleInternalRetry = () => {
    if (onRetry) {
      onRetry();
    } else {
      setImageError(false);
      setImageLoading(true);
      setRetryKey(k => k + 1);
    }
  };

  const resetDisabled = isResetDisabled(zoom, position, imageLoading, imageError, isHttpUrl);

  return (
    <AdminDialog
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      className={`admin-dialog--preview ${isExpanded ? 'is-expanded' : ''}`}
    >
      <div className="admin-doc-viewer">
        {/* Controls Toolbar */}
        <div className="admin-doc-viewer-toolbar">
          <div className="admin-doc-viewer-zoom-controls">
            <button
              type="button"
              className="admin-doc-viewer-btn"
              onClick={handleZoomOut}
              disabled={isZoomOutDisabled(zoom) || imageLoading || imageError || !isHttpUrl}
              title="Thu nhỏ (-25%)"
              aria-label="Thu nhỏ 25%"
            >
              <ZoomOut size={15} aria-hidden="true" />
            </button>

            <span className="admin-doc-viewer-zoom-badge" aria-live="polite">
              {zoom}%
            </span>

            <button
              type="button"
              className="admin-doc-viewer-btn"
              onClick={handleZoomIn}
              disabled={isZoomInDisabled(zoom) || imageLoading || imageError || !isHttpUrl}
              title="Phóng to (+25%)"
              aria-label="Phóng to 25%"
            >
              <ZoomIn size={15} aria-hidden="true" />
            </button>
          </div>

          <div className="admin-doc-viewer-divider" aria-hidden="true" />

          <button
            type="button"
            className="admin-doc-viewer-action-btn"
            onClick={handleReset}
            disabled={resetDisabled}
            title="Đặt lại về trạng thái vừa khung ban đầu"
          >
            <RotateCcw size={13} aria-hidden="true" />
            <span>Đặt lại</span>
          </button>

          <button
            type="button"
            className="admin-doc-viewer-action-btn"
            onClick={handleToggleExpand}
            title={isExpanded ? 'Thu nhỏ khung xem' : 'Mở rộng khung xem'}
          >
            {isExpanded ? (
              <>
                <Minimize2 size={13} aria-hidden="true" />
                <span>Thu nhỏ</span>
              </>
            ) : (
              <>
                <Maximize2 size={13} aria-hidden="true" />
                <span>Mở rộng</span>
              </>
            )}
          </button>

          <span className="admin-doc-viewer-hint">
            {zoom > 100
              ? 'Lăn chuột hoặc kéo ảnh để xem các góc'
              : 'Lăn con lăn chuột để zoom trực tiếp'}
          </span>
        </div>

        {/* Viewport Area */}
        <div
          ref={viewportRef}
          className={`admin-doc-viewer-viewport ${isHttpUrl && !imageLoading && !imageError ? 'is-draggable' : ''} ${isDragging ? 'is-dragging' : ''}`}
          onMouseDown={handleMouseDown}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          style={{
            cursor: !isHttpUrl || imageLoading || imageError ? 'default' : isDragging ? 'grabbing' : zoom > 100 ? 'grab' : 'default',
          }}
        >
          {isHttpUrl ? (
            <>
              {imageLoading && (
                <div className="admin-doc-viewer-loading">
                  <RotateCcw size={20} className="admin-spin" aria-hidden="true" />
                  <span>Đang tải chứng từ...</span>
                </div>
              )}

              {imageError ? (
                <div className="admin-doc-viewer-error">
                  <div className="admin-doc-viewer-error-icon">
                    <AlertCircle size={24} aria-hidden="true" />
                  </div>
                  <h3 className="admin-doc-viewer-error-title">Không thể hiển thị chứng từ</h3>
                  <p className="admin-doc-viewer-error-desc">
                    Tệp tài liệu không thể tải từ máy chủ lưu trữ hoặc liên kết truy cập đã hết hạn. Vui lòng thử lại hoặc làm mới trang.
                  </p>
                  <div>
                    <button
                      type="button"
                      className="admin-doc-viewer-retry-btn"
                      onClick={handleInternalRetry}
                      disabled={isRetrying}
                    >
                      <RotateCcw size={13} className={isRetrying ? 'admin-spin' : ''} aria-hidden="true" />
                      <span>{isRetrying ? 'Đang thử lại...' : 'Thử lại'}</span>
                    </button>
                  </div>
                </div>
              ) : (
                <img
                  key={`${imageUrl}-${retryKey}`}
                  src={imageUrl ?? undefined}
                  alt={title}
                  draggable={false}
                  onLoad={() => {
                    setImageLoading(false);
                    setImageError(false);
                  }}
                  onError={() => {
                    setImageLoading(false);
                    setImageError(true);
                  }}
                  className="admin-doc-viewer-img"
                  style={{
                    display: imageLoading ? 'none' : 'block',
                    transform: `translate(${position.x}px, ${position.y}px) scale(${zoom / 100})`,
                    transformOrigin: 'center center',
                    transition: isDragging ? 'none' : 'transform 0.15s ease-out',
                  }}
                />
              )}
            </>
          ) : (
            <div className="admin-doc-viewer-empty">
              <FileText size={36} className="text-slate-400" aria-hidden="true" />
              <h3>Khung xem trước minh họa</h3>
              <p className="admin-doc-viewer-empty-name">{title}</p>
              <p className="admin-doc-viewer-empty-desc">
                Đây là tài liệu minh họa trong dữ liệu mẫu. Chưa có tệp hình ảnh trực tuyến được tải lên từ hồ sơ.
              </p>
            </div>
          )}
        </div>
      </div>
    </AdminDialog>
  );
}
