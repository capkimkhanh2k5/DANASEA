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
