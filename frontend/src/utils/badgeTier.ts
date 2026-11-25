import type { BadgeTier } from '../types';

export const BADGE_TIER_VALUES: readonly BadgeTier[] = [
  'NONE',
  'VERIFIED',
  'TOP_RATED',
] as const;

export const BADGE_TIER_LABELS: Record<BadgeTier, string> = {
  NONE: 'Chưa xếp hạng',
  VERIFIED: 'Đã xác minh',
  TOP_RATED: 'Đối tác hàng đầu',
};

export function normalizeBadgeTier(value?: string | null): BadgeTier {
  const normalized = typeof value === 'string' ? value.trim().toUpperCase() : '';
  if (normalized === 'VERIFIED' || normalized === 'TOP_RATED') {
    return normalized;
  }
  return 'NONE';
}

export function getBadgeTierLabel(value?: string | null): string {
  return BADGE_TIER_LABELS[normalizeBadgeTier(value)];
}

export function isPublicBadgeVisible(value?: string | null): boolean {
  const tier = normalizeBadgeTier(value);
  return tier === 'VERIFIED' || tier === 'TOP_RATED';
}

export function getBadgeTierTone(value?: string | null): 'neutral' | 'teal' | 'amber' {
  const tier = normalizeBadgeTier(value);
  if (tier === 'TOP_RATED') return 'amber';
  if (tier === 'VERIFIED') return 'teal';
  return 'neutral';
}
