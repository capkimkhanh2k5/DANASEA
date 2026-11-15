import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  MIN_ZOOM,
  MAX_ZOOM,
  ZOOM_STEP,
  DEFAULT_ZOOM,
  zoomIn,
  zoomOut,
  resetZoom,
  isZoomInDisabled,
  isZoomOutDisabled,
  isResetDisabled,
  calculateZoomWithCursor,
  clampPan,
} from '../documentViewerModel.ts';

describe('documentViewerModel — Zoom & Pan business rules', () => {
  it('has default zoom at 100% (Fit), min 50%, max 200%, step 25%', () => {
    assert.equal(DEFAULT_ZOOM, 100);
    assert.equal(MIN_ZOOM, 50);
    assert.equal(MAX_ZOOM, 200);
    assert.equal(ZOOM_STEP, 25);
  });

  it('increments zoom by 25% on zoomIn up to 200%', () => {
    assert.equal(zoomIn(100), 125);
    assert.equal(zoomIn(125), 150);
    assert.equal(zoomIn(150), 175);
    assert.equal(zoomIn(175), 200);
    assert.equal(zoomIn(200), 200, 'Must clamp at 200%');
    assert.equal(zoomIn(220), 200, 'Must clamp at 200%');
  });

  it('decrements zoom by 25% on zoomOut down to 50%', () => {
    assert.equal(zoomOut(100), 75);
    assert.equal(zoomOut(75), 50);
    assert.equal(zoomOut(50), 50, 'Must clamp at 50%');
    assert.equal(zoomOut(25), 50, 'Must clamp at 50%');
  });

  it('correctly reports zoom button disabled states', () => {
    assert.equal(isZoomInDisabled(100), false);
    assert.equal(isZoomInDisabled(175), false);
    assert.equal(isZoomInDisabled(200), true);
    assert.equal(isZoomInDisabled(225), true);

    assert.equal(isZoomOutDisabled(100), false);
    assert.equal(isZoomOutDisabled(75), false);
    assert.equal(isZoomOutDisabled(50), true);
    assert.equal(isZoomOutDisabled(25), true);
  });

  it('resetZoom restores default 100% and (0,0) offset', () => {
    const res = resetZoom();
    assert.deepEqual(res, { zoom: 100, position: { x: 0, y: 0 } });
  });

  it('isResetDisabled is true only when at default 100% and (0,0), or when loading/error/no URL', () => {
    assert.equal(isResetDisabled(100, { x: 0, y: 0 }, false, false, true), true);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, false, true), false);
    assert.equal(isResetDisabled(75, { x: 0, y: 0 }, false, false, true), false);
    assert.equal(isResetDisabled(100, { x: 10, y: -20 }, false, false, true), false);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, true, false, true), true);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, true, true), true);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, false, false), true);
  });

  it('calculateZoomWithCursor keeps point under cursor stationary when zooming in', () => {
    const currentZoom = 100;
    const newZoom = 125;
    const currentPos = { x: 0, y: 0 };
    const cursor = { x: 100, y: 50 };

    const newPos = calculateZoomWithCursor(currentZoom, newZoom, currentPos, cursor);
    // x = 100 - (100 - 0) * 1.25 = 100 - 125 = -25
    assert.equal(newPos.x, -25);
    // y = 50 - (50 - 0) * 1.25 = 50 - 62.5 = -12.5
    assert.equal(newPos.y, -12.5);
  });

  it('calculateZoomWithCursor resets position to (0, 0) when zooming back to <= 100% (Fit)', () => {
    const newPos = calculateZoomWithCursor(125, 100, { x: -25, y: -12.5 }, { x: 100, y: 50 });
    assert.deepEqual(newPos, { x: 0, y: 0 });

    const newPos50 = calculateZoomWithCursor(75, 50, { x: -10, y: -5 }, { x: 100, y: 50 });
    assert.deepEqual(newPos50, { x: 0, y: 0 });
  });

  it('clampPan strictly prevents the document from wandering off-screen', () => {
    const viewport = { width: 800, height: 600 };

    // At scale <= 100% (Fit), pan is always clamped to 0,0
    assert.deepEqual(clampPan({ x: 100, y: 100 }, 100, viewport), { x: 0, y: 0 });
    assert.deepEqual(clampPan({ x: 100, y: 100 }, 75, viewport), { x: 0, y: 0 });

    // At scale 150%, scale = 1.5. maxPanX = (800/2) * 0.5 + 48 = 248. maxPanY = (600/2) * 0.5 + 48 = 198.
    const withinBounds = clampPan({ x: 150, y: -100 }, 150, viewport);
    assert.deepEqual(withinBounds, { x: 150, y: -100 });

    const excessiveDrag = clampPan({ x: 9999, y: -9999 }, 150, viewport);
    assert.equal(excessiveDrag.x, 248);
    assert.equal(excessiveDrag.y, -198);

    // Negative bounds
    const excessiveNegativeDrag = clampPan({ x: -9999, y: 9999 }, 150, viewport);
    assert.equal(excessiveNegativeDrag.x, -248);
    assert.equal(excessiveNegativeDrag.y, 198);
  });
});
