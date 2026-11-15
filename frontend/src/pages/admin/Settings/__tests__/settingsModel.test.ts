import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseSafetyRebatePercent,
  toSafetyRebatePayload,
  validateSafetyRebateRate,
  validateCommissionRatePercent,
  validateDurationDays,
  validateCommissionRate,
  validateHotline,
  getCategoryGroupMeta,
  CATEGORY_GROUPS,
  SYSTEM_DEFAULTS,
  formatPercentDisplay,
} from '../settingsModel.ts';

describe('settingsModel — P0-1 Safety Rebate Unit & Conversion', () => {
  test('parseSafetyRebatePercent parses legacy percentage format (> 0.20)', () => {
    assert.equal(parseSafetyRebatePercent('2.0'), 2.0);
    assert.equal(parseSafetyRebatePercent('2'), 2.0);
    assert.equal(parseSafetyRebatePercent('2.5'), 2.5);
    assert.equal(parseSafetyRebatePercent('5.0'), 5.0);
    assert.equal(parseSafetyRebatePercent('10.0'), 10.0);
  });

  test('parseSafetyRebatePercent parses spec decimal fraction format (<= 0.20)', () => {
    assert.equal(parseSafetyRebatePercent('0.02'), 2.0);
    assert.equal(parseSafetyRebatePercent('0.025'), 2.5);
    assert.equal(parseSafetyRebatePercent('0.01'), 1.0);
    assert.equal(parseSafetyRebatePercent('0.005'), 0.5);
    assert.equal(parseSafetyRebatePercent('0.00'), 0.0);
    assert.equal(parseSafetyRebatePercent('0.10'), 10.0);
  });

  test('parseSafetyRebatePercent handles empty or invalid inputs with 2.0 fallback', () => {
    assert.equal(parseSafetyRebatePercent(''), 2.0);
    assert.equal(parseSafetyRebatePercent(null), 2.0);
    assert.equal(parseSafetyRebatePercent(undefined), 2.0);
    assert.equal(parseSafetyRebatePercent('abc'), 2.0);
    assert.equal(parseSafetyRebatePercent('-1'), 2.0);
  });

  test('toSafetyRebatePayload converts percent into spec decimal fraction payload string', () => {
    // Spec Contract: {"value": "0.02"} for 2%
    assert.equal(toSafetyRebatePayload(2), '0.02');
    assert.equal(toSafetyRebatePayload('2.0'), '0.02');
    assert.equal(toSafetyRebatePayload(2.5), '0.025');
    assert.equal(toSafetyRebatePayload(1), '0.01');
    assert.equal(toSafetyRebatePayload(0.5), '0.005');
    assert.equal(toSafetyRebatePayload(0), '0');
    assert.equal(toSafetyRebatePayload(10), '0.1');
  });

  test('validateSafetyRebateRate checks range between 0% and 10%', () => {
    assert.equal(validateSafetyRebateRate('2.0'), null);
    assert.equal(validateSafetyRebateRate('0'), null);
    assert.equal(validateSafetyRebateRate('10'), null);
    assert.equal(validateSafetyRebateRate('0.5'), null);
    assert.equal(validateSafetyRebateRate(2), null);
    assert.notEqual(validateSafetyRebateRate(''), null);
    assert.notEqual(validateSafetyRebateRate('-1'), null);
    assert.notEqual(validateSafetyRebateRate('10.5'), null);
    assert.notEqual(validateSafetyRebateRate('invalid'), null);
  });
});

describe('settingsModel — Commission Rules Validation', () => {
  test('validateCommissionRatePercent validates 0 to 100 percentage range', () => {
    assert.equal(validateCommissionRatePercent('5.0'), null);
    assert.equal(validateCommissionRatePercent(7), null);
    assert.equal(validateCommissionRatePercent('12.5'), null);
    assert.equal(validateCommissionRatePercent('0'), null);
    assert.equal(validateCommissionRatePercent('100'), null);

    assert.notEqual(validateCommissionRatePercent(''), null);
    assert.notEqual(validateCommissionRatePercent('-0.5'), null);
    assert.notEqual(validateCommissionRatePercent('100.1'), null);
    assert.notEqual(validateCommissionRatePercent('not a number'), null);
  });

  test('validateDurationDays validates positive integer days', () => {
    assert.equal(validateDurationDays('90'), null);
    assert.equal(validateDurationDays(30), null);
    assert.equal(validateDurationDays(365), null);

    assert.notEqual(validateDurationDays('0'), null);
    assert.notEqual(validateDurationDays('-10'), null);
    assert.notEqual(validateDurationDays('15.5'), null);
    assert.notEqual(validateDurationDays(''), null);
  });

  test('preserves legacy validateCommissionRate and validateHotline', () => {
    assert.equal(validateCommissionRate('10'), null);
    assert.notEqual(validateCommissionRate(''), null);
    assert.equal(validateHotline('0236 3920 477'), null);
    assert.notEqual(validateHotline('123'), null);
  });
});

describe('settingsModel — Category Groups & Purple Ban compliance', () => {
  test('correctly maps 12 spec categories to 3 groups regardless of custom rate', () => {
    // Nhóm 1: Tiện ích & Thuê dụng cụ (7.0%)
    const group1Slugs = ['thue-sup', 'thue-kayak', 'thue-van-luot-song'];
    group1Slugs.forEach(slug => {
      const meta = getCategoryGroupMeta(slug);
      assert.equal(meta.groupId, 'gear');
      assert.equal(meta.badgeBg, '#ecfdf5');
      assert.equal(meta.badgeText, '#065f46');
    });

    // Nhóm 2: Thể thao biển phổ thông (10.0%)
    const group2Slugs = ['cano-du-bay', 'mo-to-nuoc-jetski', 'truot-phao-chuoi', 'cheo-sup-kayak'];
    group2Slugs.forEach(slug => {
      const meta = getCategoryGroupMeta(slug);
      assert.equal(meta.groupId, 'sports');
      assert.equal(meta.badgeBg, '#eef7f8');
      assert.equal(meta.badgeText, '#0b5d6f');
    });

    // Nhóm 3: Tour chuyên sâu & Lặn biển (12.0%)
    const group3Slugs = [
      'lan-ngam-san-ho',
      'cau-ca-bien',
      'cau-muc-dem',
      'hoc-luot-song',
      'surf-tour-co-huong-dan',
      'du-thuyen-ngam-hoang-hon',
    ];
    group3Slugs.forEach(slug => {
      const meta = getCategoryGroupMeta(slug);
      assert.equal(meta.groupId, 'tours');
      assert.equal(meta.badgeBg, '#fffbeb');
      assert.equal(meta.badgeText, '#92400e');
    });

    // Unknown category falls back cleanly
    assert.equal(getCategoryGroupMeta('unknown-activity').groupId, 'other');
    assert.equal(getCategoryGroupMeta(null).groupId, 'other');
    assert.equal(getCategoryGroupMeta(undefined).groupId, 'other');
  });

  test('no purple color in any category badge (Purple Ban compliance)', () => {
    Object.values(CATEGORY_GROUPS).forEach(group => {
      assert.ok(!group.badgeBg.toLowerCase().includes('purple'));
      assert.ok(!group.badgeText.toLowerCase().includes('purple'));
      assert.ok(!group.badgeBorder.toLowerCase().includes('purple'));
      // Hex colors should not be in purple hue range (#f3e8ff, #7e22ce, etc.)
      assert.ok(!group.badgeBg.toLowerCase().includes('#f3e8ff'));
      assert.ok(!group.badgeText.toLowerCase().includes('#7e22ce'));
      assert.ok(!group.badgeBorder.toLowerCase().includes('#d8b4fe'));
    });
  });

  test('SYSTEM_DEFAULTS matches spec baselines', () => {
    assert.equal(SYSTEM_DEFAULTS.platformCommissionRate, 10.0);
    assert.equal(SYSTEM_DEFAULTS.onboardingRate, 5.0);
    assert.equal(SYSTEM_DEFAULTS.onboardingDays, 90);
    assert.equal(SYSTEM_DEFAULTS.safetyRebateRate, 2.0);
    assert.equal(SYSTEM_DEFAULTS.safetyRebateEnabled, true);
  });

  test('formatPercentDisplay formats integer and decimal rates cleanly', () => {
    assert.equal(formatPercentDisplay(10), '10');
    assert.equal(formatPercentDisplay(10.0), '10');
    assert.equal(formatPercentDisplay(5), '5');
    assert.equal(formatPercentDisplay(2), '2');
    assert.equal(formatPercentDisplay(7.5), '7.5');
    assert.equal(formatPercentDisplay(12.3), '12.3');
    assert.equal(formatPercentDisplay(NaN), '0');
  });
});
