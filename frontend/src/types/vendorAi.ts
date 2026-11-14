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

export interface OptimizeListingRequest {
  serviceId?: string;
  rawTitle?: string;
  rawDescription?: string;
  highlights?: string[];
  includedItems?: string[];
  targetLanguages?: string[];
}

export interface LocalizedListingDraft {
  language: string;
  optimizedTitle: string;
  optimizedDescription: string;
  sellingPoints: string[];
  travelerPackingList: string[];
  safetyAdvisory: string;
}

export interface MissingChecklistItem {
  itemCode: string;
  description: string;
  suggestion: string;
  critical: boolean;
}

export interface OptimizeListingResult {
  vendorId: string;
  serviceId?: string;
  detectedCategory: string;
  listingQualityScore: number;
  missingItems: MissingChecklistItem[];
  drafts: Record<string, LocalizedListingDraft>;
  policyCompliant: boolean;
  analysisMethod: string;
  generatedAt: string;
}

export interface CopilotChatRequest {
  conversationId?: string;
  message: string;
  contextType?: string;
  contextId?: string;
}

export interface CopilotArtifact {
  tool?: string;
  type?: string;
  data?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface CopilotChatResponse {
  status: string;
  conversationId: string;
  message: string;
  role: string;
  artifacts?: CopilotArtifact[];
  toolsUsed?: string[];
  generatedTextVerified?: boolean;
}

export interface CopilotConversation {
  id: string;
  actorUserId: string;
  actorRole: string;
  vendorId: string;
  language: string;
  title: string;
  pinned: boolean;
  archived: boolean;
  contextType?: string | null;
  contextId?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CopilotMessage {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT' | string;
  content: string;
  artifactsJson?: string | null;
  toolsUsed?: string | null;
  verified: boolean;
  createdAt: string;
}

export interface UpdateConversationMetaRequest {
  title?: string;
  pinned?: boolean;
  archived?: boolean;
}


