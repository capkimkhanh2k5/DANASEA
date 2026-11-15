export const MIN_ZOOM = 50;
export const MAX_ZOOM = 200;
export const ZOOM_STEP = 25;
export const DEFAULT_ZOOM = 100;

export function zoomIn(current: number): number {
  return Math.min(MAX_ZOOM, current + ZOOM_STEP);
}

export function zoomOut(current: number): number {
  return Math.max(MIN_ZOOM, current - ZOOM_STEP);
}

export function resetZoom(): { zoom: number; position: { x: number; y: number } } {
  return { zoom: DEFAULT_ZOOM, position: { x: 0, y: 0 } };
}

export function isZoomInDisabled(current: number): boolean {
  return current >= MAX_ZOOM;
}

export function isZoomOutDisabled(current: number): boolean {
  return current <= MIN_ZOOM;
}

export function isResetDisabled(
  zoom: number,
  position: { x: number; y: number },
  loading: boolean,
  error: boolean,
  hasValidUrl: boolean
): boolean {
  if (loading || error || !hasValidUrl) return true;
  return zoom === DEFAULT_ZOOM && position.x === 0 && position.y === 0;
}

/**
 * Calculates new pan offset so the point directly under the cursor stays in place during zoom.
 * When zooming back to <= 100% (Fit), centers the document (0, 0).
 */
export function calculateZoomWithCursor(
  currentZoom: number,
  newZoom: number,
  currentPos: { x: number; y: number },
  cursorInViewport: { x: number; y: number }
): { x: number; y: number } {
  if (newZoom <= DEFAULT_ZOOM) {
    return { x: 0, y: 0 };
  }
  const ratio = newZoom / currentZoom;
  const newX = cursorInViewport.x - (cursorInViewport.x - currentPos.x) * ratio;
  const newY = cursorInViewport.y - (cursorInViewport.y - currentPos.y) * ratio;
  return { x: newX, y: newY };
}

/**
 * Bounds pan movement so the image never wanders off-screen.
 * At zoom <= 100% (Fit), no panning is allowed (returns 0, 0).
 * At zoom > 100%, bounds movement based on zoom scale and viewport size.
 */
export function clampPan(
  position: { x: number; y: number },
  zoom: number,
  viewport: { width: number; height: number }
): { x: number; y: number } {
  const scale = zoom / 100;
  if (scale <= 1) {
    return { x: 0, y: 0 };
  }

  // Margin buffer allows panning up to the document margins without losing sight of the document
  const maxPanX = Math.max(0, (viewport.width / 2) * (scale - 1) + 48);
  const maxPanY = Math.max(0, (viewport.height / 2) * (scale - 1) + 48);

  return {
    x: Math.max(-maxPanX, Math.min(maxPanX, position.x)),
    y: Math.max(-maxPanY, Math.min(maxPanY, position.y)),
  };
}
