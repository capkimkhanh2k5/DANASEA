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
