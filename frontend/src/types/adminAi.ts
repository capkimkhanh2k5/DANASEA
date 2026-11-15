/**
 * TypeScript DTOs for DANASEA Admin AI Features.
 * Aligned 1:1 with Backend Java records in:
 * - AdminDisputeSummaryUseCase.DisputeArbitrationResult
 * - AdminAiDataPort.PlatformDashboardMetrics
 * - RoleCopilotUseCase.CopilotChatResponse
 * - AiExtendedInsightStorePort.CopilotConversation
 * - AiExtendedInsightStorePort.CopilotMessage
 * - AiExtendedInsightStorePort.DisputeSummary
 */

// ==================== DISPUTE ARBITRATION AI ====================

export type DisputeResolutionRecommendation =
  | 'RESOLVED_REFUND'
  | 'RESOLVED_PARTIAL'
  | 'RESOLVED_REJECTED'
  | 'MANUAL_REVIEW_REQUIRED'
  | string;

export interface DisputeArbitrationResult {
  disputeId: string;
  subOrderId?: string;
  orderId?: string;
  customerName?: string;
  vendorBusinessName?: string;
  serviceTitle?: string;
  disputeReason?: string;
  timelineSummary?: string;
  customerClaimSummary?: string;
  vendorDefenseSummary?: string;
  objectiveSystemFacts: string[];
  recommendedResolution?: DisputeResolutionRecommendation;
  recommendedRefundPercent?: number | null;
  recommendationReason?: string;
  draftAdminNote?: string;
  analysisMethod?: string;
  generatedAt?: string;
}

export interface DisputeSummary {
  id: string;
  disputeId: string;
  subOrderId?: string;
  requestedByAdminId?: string;
  recommendedResolution?: string;
  recommendedRefundPercent?: number | null;
  summaryJson?: string;
  analysisMethod?: string;
  createdAt?: string;
}

// ==================== EXECUTIVE DIGEST ====================

export interface PlatformDashboardMetrics {
  totalGmv: number;
  netPayout: number;
  platformCommission: number;
  totalBookings: number;
  activeServices: number;
  activeVendors: number;
  criticalVendorsCount: number;
  operationalAlerts: string[];
}

// ==================== ADMIN COPILOT ====================

export type CopilotContextType = 'ORDER' | 'DISPUTE' | 'VENDOR' | 'SERVICE';

export interface RevenueTimeSeriesItem {
  period: string;
  gmv: number;
  collectedCash: number;
  refunds: number;
  commission: number;
  netVendorPayout: number;
}

export interface BookingCancellationBreakdown {
  totalBookings: number;
  completedBookings: number;
  totalCancelled: number;
  weatherCancellations: number;
  vendorFaultCancellations: number;
  customerCancellations: number;
  adminOverrideCancellations: number;
  disputeCancellations: number;
  cancellationsByReason?: Record<string, number>;
}

export interface VendorDiagnosticItem {
  vendorId: string;
  businessName: string;
  gmv: number;
  totalBookings: number;
  cancellationRate: number;
  slotOccupancyRate: number;
  ratingAvg: number;
  ratingCount: number;
  severity: 'HEALTHY' | 'WARNING' | 'CRITICAL' | string;
  issueTypes: string[];
}

export interface QueueOverview {
  pendingRiskCasesCount: number;
  pendingSupportRequestsCount: number;
  openDisputesCount: number;
}

export interface CopilotArtifact<T = unknown> {
  tool: string;
  type: string;
  data: T;
}

export interface CopilotConversation {
  id: string;
  actorUserId: string;
  actorRole: string;
  vendorId?: string | null;
  language: string;
  title: string;
  pinned: boolean;
  archived: boolean;
  contextType?: CopilotContextType | string | null;
  contextId?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CopilotMessage {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant' | 'system' | string;
  content: string;
  artifactsJson?: string | null;
  toolsUsed?: string | null;
  verified: boolean;
  createdAt: string;
}

export interface CopilotChatRequestDto {
  conversationId?: string | null;
  message: string;
  contextType?: string | null;
  contextId?: string | null;
}

export interface CopilotChatResponse {
  status: string;
  conversationId: string;
  message: string;
  role: string;
  artifacts?: CopilotArtifact[];
  toolsUsed?: string[];
  generatedTextVerified: boolean;
}

export interface UpdateConversationMetaDto {
  title?: string;
  pinned?: boolean;
  archived?: boolean;
}

