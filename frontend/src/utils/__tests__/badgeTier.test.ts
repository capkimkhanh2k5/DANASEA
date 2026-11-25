import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  BADGE_TIER_LABELS,
  BADGE_TIER_VALUES,
  getBadgeTierLabel,
  getBadgeTierTone,
  isPublicBadgeVisible,
  normalizeBadgeTier,
} from '../badgeTier.ts';

describe('badgeTier helpers', () => {
  it('exposes only the three canonical backend badge tiers', () => {
    assert.deepEqual(BADGE_TIER_VALUES, ['NONE', 'VERIFIED', 'TOP_RATED']);
    assert.deepEqual(BADGE_TIER_LABELS, {
      NONE: 'Chưa xếp hạng',
      VERIFIED: 'Đã xác minh',
      TOP_RATED: 'Đối tác hàng đầu',
    });
  });

  it('normalizes canonical tiers and falls back to NONE for legacy or unknown values', () => {
    assert.equal(normalizeBadgeTier('NONE'), 'NONE');
    assert.equal(normalizeBadgeTier('verified'), 'VERIFIED');
    assert.equal(normalizeBadgeTier(' top_rated '), 'TOP_RATED');
    assert.equal(normalizeBadgeTier('STANDARD'), 'NONE');
    assert.equal(normalizeBadgeTier('BRONZE'), 'NONE');
    assert.equal(normalizeBadgeTier('SILVER'), 'NONE');
    assert.equal(normalizeBadgeTier('GOLD'), 'NONE');
    assert.equal(normalizeBadgeTier('PLATINUM'), 'NONE');
    assert.equal(normalizeBadgeTier(null), 'NONE');
    assert.equal(normalizeBadgeTier(undefined), 'NONE');
  });

  it('returns consistent Vietnamese labels and tones', () => {
    assert.equal(getBadgeTierLabel('NONE'), 'Chưa xếp hạng');
    assert.equal(getBadgeTierLabel('VERIFIED'), 'Đã xác minh');
    assert.equal(getBadgeTierLabel('TOP_RATED'), 'Đối tác hàng đầu');
    assert.equal(getBadgeTierLabel('PLATINUM'), 'Chưa xếp hạng');

    assert.equal(getBadgeTierTone('NONE'), 'neutral');
    assert.equal(getBadgeTierTone('VERIFIED'), 'teal');
    assert.equal(getBadgeTierTone('TOP_RATED'), 'amber');
  });

  it('hides NONE badge on customer-facing views while keeping VERIFIED and TOP_RATED visible', () => {
    assert.equal(isPublicBadgeVisible('NONE'), false);
    assert.equal(isPublicBadgeVisible(''), false);
    assert.equal(isPublicBadgeVisible(undefined), false);
    assert.equal(isPublicBadgeVisible('VERIFIED'), true);
    assert.equal(isPublicBadgeVisible('TOP_RATED'), true);
  });
});
