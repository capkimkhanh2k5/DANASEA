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
    // At default 100% and 0,0
    assert.equal(isResetDisabled(100, { x: 0, y: 0 }, false, false, true), true);

    // Zoomed in
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, false, true), false);

    // Zoomed out
    assert.equal(isResetDisabled(75, { x: 0, y: 0 }, false, false, true), false);

    // Panned
    assert.equal(isResetDisabled(100, { x: 10, y: -20 }, false, false, true), false);

    // Loading / error / no URL should disable reset
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, true, false, true), true);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, true, true), true);
    assert.equal(isResetDisabled(125, { x: 0, y: 0 }, false, false, false), true);
  });
});
