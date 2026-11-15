export interface CategorySafetyRuleResponse {
  id: string;
  categoryId: string;
  categorySlug?: string;
  categoryName?: string;
  cautionWaveHeightM?: number | null;
  maxWaveHeightM?: number | null;
  cautionWindSpeedKmh?: number | null;
  maxWindSpeedKmh?: number | null;
  maxWindGustKmh?: number | null;
  maxOceanCurrentMs?: number | null;
  minVisibilityM?: number | null;
  fatalThunderstormCodes?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateCategorySafetyRuleRequest {
  cautionWaveHeightM?: number | null;
  maxWaveHeightM?: number | null;
  cautionWindSpeedKmh?: number | null;
  maxWindSpeedKmh?: number | null;
  maxWindGustKmh?: number | null;
  maxOceanCurrentMs?: number | null;
  minVisibilityM?: number | null;
  fatalThunderstormCodes?: string | null;
}

// ============================================================================
// Dynamic Commission Rules & System Configuration Types (Spec Section 3)
// ============================================================================

export type CommissionRuleTargetType = 'GLOBAL' | 'CATEGORY' | 'VENDOR' | 'ONBOARDING';

export interface CommissionRuleDto {
  id: string;
  ruleCode: string;
  ruleName: string;
  targetType: CommissionRuleTargetType;
  categoryId?: string | null;
  categoryName?: string | null;
  categorySlug?: string | null;
  vendorId?: string | null;
  commissionRate: number;         // Decimal fraction e.g. 0.0500, 0.0700, 0.1000
  commissionRatePercent: number;  // Percentage value e.g. 5.0, 7.0, 10.0
  durationDays?: number | null;   // Active days e.g. 90 (only for ONBOARDING)
  priority?: number;
  isActive: boolean;
  description?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface UpdateCommissionRuleRequest {
  commissionRatePercent?: number; // e.g. 6.5, 7.0
  durationDays?: number | null;
  isActive?: boolean;
  description?: string;
}

export interface SystemConfigDto {
  key: string;
  value: string;
  description?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface UpdateSystemConfigRequest {
  value: string;
  description?: string;
}
