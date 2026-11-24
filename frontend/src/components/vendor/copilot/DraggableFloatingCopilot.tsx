import React, { useState, useRef, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { DanaseaAiMascot } from '../../customer/DanaseaAiMascot';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';

export const DraggableFloatingCopilot: React.FC = () => {
  const location = useLocation();
  const toggleCopilot = useVendorCopilotStore((s) => s.toggleCopilot);

  // Vertical position from bottom (in px). Default to 120px on /vendor/messages so it never obstructs chat input, 96px on other pages
  const isMessagesPage = location.pathname.includes('/messages');
  const defaultBottom = isMessagesPage ? 120 : 96;
  const [bottomPx, setBottomPx] = useState<number>(defaultBottom);

  const isDraggingRef = useRef(false);
  const startYRef = useRef(0);
  const startBottomRef = useRef(defaultBottom);
  const hasMovedRef = useRef(false);

  // Keep bottomPx updated when navigating to/from messages
  useEffect(() => {
    setBottomPx(isMessagesPage ? 120 : 96);
  }, [isMessagesPage]);

  // Keep bottomPx within viewport bounds if window resizes
  useEffect(() => {
    const handleResize = () => {
      const maxBottom = window.innerHeight - 120;
      setBottomPx((prev) => Math.max(32, Math.min(prev, maxBottom)));
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // If on the dedicated /vendor/copilot full page, do not render floating trigger
  if (location.pathname === '/vendor/copilot') {
    return null;
  }

  const handlePointerDown = (e: React.PointerEvent) => {
    // Only drag on primary mouse button or touch
    if (e.button !== 0) return;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    isDraggingRef.current = true;
    hasMovedRef.current = false;
    startYRef.current = e.clientY;
    startBottomRef.current = bottomPx;
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDraggingRef.current) return;
    const deltaY = startYRef.current - e.clientY; // moving mouse up increases deltaY (increases bottom)
    if (Math.abs(deltaY) > 3) {
      hasMovedRef.current = true;
    }
    const maxBottom = window.innerHeight - 100;
    const minBottom = 24;
    const newBottom = Math.max(minBottom, Math.min(startBottomRef.current + deltaY, maxBottom));
    setBottomPx(newBottom);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    if (!isDraggingRef.current) return;
    isDraggingRef.current = false;
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {
      // Ignore if already released
    }
    if (!hasMovedRef.current) {
      toggleCopilot();
    }
  };

  return (
    <aside
      aria-label="Menu Trợ lý Copilot"
      style={{ bottom: `${bottomPx}px` }}
      className="fixed right-6 z-40 select-none print:hidden touch-none"
    >
      <div className="relative group/btn flex items-center">
        {/* Tooltip on hover */}
        <span className="absolute right-full mr-3 px-2.5 py-1 rounded-lg bg-slate-900/90 text-white text-xs font-medium whitespace-nowrap opacity-0 group-hover/btn:opacity-100 transition-opacity pointer-events-none shadow-md">
          DANASEA Copilot • Kéo để di chuyển
        </span>

        {/* Mascot Drag Handle Button */}
        <button
          type="button"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          aria-label="Trợ lý kinh doanh DANASEA Copilot"
          className="relative flex items-center justify-center cursor-grab active:cursor-grabbing transition-transform duration-100 active:scale-95 group-hover/btn:scale-105 focus:outline-hidden"
        >
          <DanaseaAiMascot size={58} className="drop-shadow-lg" />
        </button>
      </div>
    </aside>
  );
};

export default DraggableFloatingCopilot;
