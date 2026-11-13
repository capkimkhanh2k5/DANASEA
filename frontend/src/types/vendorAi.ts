export interface EvidenceQuote {
  reviewId: string;
  rating: number;
  exactQuote: string;
  aspect: string;
}

export interface PillarEvaluation {
  pillar: string;
  scoreOutOf10: number;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEEDS_IMPROVEMENT';
  positiveQuotes: EvidenceQuote[];
  negativeQuotes: EvidenceQuote[];
}

export interface ActionItem {
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  pillar: string;
  title: string;
  recommendation: string;
}

export interface BadgeProgression {
  currentTier: string;
  nextTier: string;
  reviewsNeededForNextTier: number;
  targetRating: number;
  roadmapNote: string;
}

export interface CoachingResult {
  vendorId: string;
  serviceId?: string;
  businessName: string;
  totalReviewsAnalyzed: number;
  averageRating: number;
  pillars: Record<string, PillarEvaluation>;
  actionItems: ActionItem[];
  badgeProgression?: BadgeProgression;
  analysisMethod: string;
  generatedAt: string;
}

export interface ReplyDraftResult {
  reviewId: string;
  rating: number;
  customerComment: string;
  draftedReply: string;
  alternativeShortReply: string;
  tone: string;
  verifiedSafe: boolean;
}

export interface SlotRecommendation {
  slotId: string;
  serviceId: string;
  serviceTitle: string;
  date: string;
  startTime: string;
  endTime: string;
  capacity: number;
  bookedCount: number;
  occupancyRate: number;
  weatherRiskLevel: 'SAFE' | 'MODERATE' | 'HIGH_RISK' | 'UNKNOWN' | 'NOT_REQUIRED';
  recommendationType: 'EXPAND_CAPACITY' | 'ADD_SLOT' | 'WEATHER_WATCH' | 'PROMOTE_OR_CONSOLIDATE' | 'KEEP_OPTIMAL';
  actionTitle: string;
  adviceReason: string;
}

export interface SlotAdvisorResult {
  vendorId: string;
  serviceId?: string;
  totalSlotsEvaluated: number;
  highDemandSlotsCount: number;
  lowDemandSlotsCount: number;
  weatherRiskSlotsCount: number;
  recommendations: SlotRecommendation[];
  analysisMethod: string;
  generatedAt: string;
}
