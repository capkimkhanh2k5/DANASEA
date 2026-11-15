// Generated based on Backend Database Schema
// ========================================

export interface PasswordResetToken {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  tokenHash: string;
  expiresAt: string;
  usedAt: string;
}

export interface RefreshToken {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  tokenHash: string;
  familyId: string;
  replacedById: string;
  expiresAt: string;
  revokedAt: string;
}

export interface User {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  email: string;
  phone: string;
  passwordHash: string;
  fullName: string;
  role: Role;
  avatarUrl: string;
  isEmailVerified: boolean;
  isLocked: boolean;
  locale: string;
}

export interface AiConversation {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  startedAt: string;
  endedAt: string;
}

export interface AiMessage {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  conversationId: string;
  role: AiMessageRole;
  content: string;
  toolCalls: string;
}

export interface AuditLog {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  actorUserId: string;
  action: string;
  entityType: string;
  entityId: string;
  metadata: string;
}

export interface Conversation {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  customerId: string;
  vendorId: string;
  masterOrderId: string;
}

export interface Dispute {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  subOrderId: string;
  raisedBy: string;
  category: string;
  description: string;
  status: DisputeStatus;
  resolutionNote: string;
  resolvedBy: string;
  resolvedAt: string;
}

export interface DisputeAttachment {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  disputeId: string;
  fileUrl: string;
}

export interface Message {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  conversationId: string;
  senderId: string;
  content: string;
  attachmentUrl: string;
  isRead: boolean;
}

export interface Notification {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  type: string;
  channel: NotificationChannel;
  title: string;
  body: string;
  relatedEntityType: string;
  relatedEntityId: string;
  status: NotificationStatus;
  sentAt: string;
}

export interface PayoutRequest {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  vendorId: string;
  settlementId: string;
  amount: number;
  status: PayoutRequestStatus;
  processedBy: string;
  processedAt: string;
}

export interface Review {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  subOrderId: string;
  customerId: string;
  vendorId: string;
  serviceId: string;
  rating: number;
  comment: string;
  images: string;
  vendorReply: string;
  vendorRepliedAt: string;
  isFlagged: boolean;
}

export interface Settlement {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  vendorId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionAmount: number;
  netPayableAmount: number;
  status: SettlementStatus;
  generatedAt: string;
}

export interface SettlementItem {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  settlementId: string;
  subOrderId: string;
  amount: number;
}

export interface DiscountCode {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  code: string;
  scope: DiscountScope;
  vendorId: string;
  discountType: DiscountType;
  discountValue: number;
  maxUses: number;
  usedCount: number;
  validFrom: string;
  validTo: string;
  isActive: boolean;
}

export interface DiscountRedemption {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  discountCodeId: string;
  masterOrderId: string;
  amountDeducted: number;
}

export interface Invoice {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  masterOrderId: string;
  invoiceNumber: string;
  pdfUrl: string;
  issuedAt: string;
}

export interface MasterOrder {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  customerId: string;
  status: MasterOrderStatus;
  totalAmount: number;
  discountAmount: number;
  discountCodeId: string;
}

export interface Payment {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  masterOrderId: string;
  provider: PaymentProvider;
  providerTransactionId: string;
  amount: number;
  status: PaymentStatus;
  rawWebhookPayload: string;
}

export interface Refund {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  subOrderId: string;
  amount: number;
  refundPercentage: number;
  reason: RefundReason;
  status: RefundStatus;
  processedAt: string;
}

export interface SubOrder {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  masterOrderId: string;
  vendorId: string;
  serviceId: string;
  slotId: string;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  commissionRate: number;
  commissionAmount: number;
  vendorPayoutAmount: number;
  status: SubOrderStatus;
  waiverAccepted: boolean;
  waiverAcceptedAt: string;
  qrSecret: string;
  checkedInAt: string;
}

export interface Category {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  name: string;
  nameEn: string;
  slug: string;
  parentId: string;
  iconUrl: string;
  isActive: boolean;
  requiresSafetyCert?: boolean | null;
}

export interface RecentlyViewed {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  sessionId: string;
  serviceId: string;
  viewedAt: string;
}

export interface Service {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  vendorId?: string;
  categoryId?: string;
  name: string;
  nameEn?: string;
  slug?: string;
  description?: string;
  descriptionEn?: string;
  price?: number;
  basePrice: number;
  durationMinutes?: number;
  capacityPerSlot?: number;
  locationName: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  status: ServiceStatus;
  rejectionReason?: string;
  waiverContent?: string;
  weatherSensitive?: boolean;
  minWindKmh?: number;
  maxWaveM?: number;
  avgRating?: number;
  ratingCount?: number;
  viewCount?: number;
}

export interface ServiceImage {
  id: string;
  serviceId: string;
  url?: string;
  imageUrl?: string;
  isPrimary?: boolean;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ServiceSafetyDocument {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  serviceId: string;
  fileUrl: string;
  status: DocStatus;
  reviewedBy: string;
  reviewedAt: string;
  rejectionReason: string;
}

export interface ServiceSlot {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  serviceId: string;
  date: string;
  startTime: string;
  endTime: string;
  capacity: number;
  bookedCount: number;
  status: SlotStatus;
}

export interface Wishlist {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  serviceId: string;
}

export interface SystemConfig {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  key: string;
  value: string;
  description: string;
  updatedBy: string;
}

export interface Vendor {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  userId: string;
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
  verificationStatus: VerificationStatus;
  verifiedBy: string;
  verifiedAt: string;
  ratingAvg: number;
  ratingCount: number;
  badgeTier: BadgeTier;
}
export type DocType = 'BUSINESS_LICENSE' | 'ID_CARD' | 'SAFETY_CERT' | 'OTHER';
export type DocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface VendorDocument {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  vendorId: string;
  docType: DocType;
  fileUrl: string;
  status: DocStatus;
  reviewedBy: string;
  reviewedAt: string;
}

export interface SafetyRuleEvaluation {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  serviceId: string;
  slotId: string;
  isSafe: boolean;
  warningMessage: string;
  evaluatedAt: string;
}

export interface WeatherCache {
  id: string;
  createdAt?: string;
  updatedAt?: string;
  locationKey: string;
  windSpeedKmh: number;
  waveHeightM: number;
  precipitationMm: number;
  rawPayload: string;
  fetchedAt: string;
  expiresAt: string;
}

export interface BaseDomainModel {
  id: string;
  createdAt: string;
  updatedAt: string;
}

export const Role = {
  ADMIN: 'ADMIN',
  VENDOR: 'VENDOR',
  CUSTOMER: 'CUSTOMER'
} as const;
export type Role = 'ADMIN' | 'VENDOR' | 'CUSTOMER';

export type AiMessageRole = 'USER' | 'SYSTEM' | 'ASSISTANT';

export const DisputeStatus = {
  OPEN: 'OPEN',
  IN_PROGRESS: 'IN_PROGRESS',
  RESOLVED: 'RESOLVED',
  CLOSED: 'CLOSED'
} as const;
export type DisputeStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP';

export type NotificationStatus = 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';

export type PayoutRequestStatus = 'REQUESTED' | 'APPROVED' | 'PAID' | 'REJECTED';

export type SettlementStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export type DiscountScope = 'GLOBAL' | 'VENDOR' | 'CATEGORY' | 'SERVICE';

export type DiscountType = 'PERCENTAGE' | 'FIXED';

export const MasterOrderStatus = {
  ACTIVE: 'CONFIRMED',
  INACTIVE: 'CANCELLED',
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED'
} as const;
export type MasterOrderStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED';

export const PaymentProvider = {
  VNPAY: 'VNPAY',
  MOMO: 'MOMO',
  STRIPE: 'STRIPE'
} as const;
export type PaymentProvider = string;

export const PaymentStatus = {
  PENDING: 'PENDING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  REFUNDED: 'REFUNDED'
} as const;
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export type RefundReason = string;

export type RefundStatus = 'PENDING' | 'PROCESSED' | 'FAILED';

export const SubOrderStatus = {
  ACTIVE: 'CONFIRMED',
  INACTIVE: 'COMPLETED',
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED'
} as const;
export type SubOrderStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED';

export const ServiceStatus = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'HIDDEN',
  DRAFT: 'DRAFT',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  HIDDEN: 'HIDDEN',
  PAUSED: 'PAUSED'
} as const;
export type ServiceStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'ACTIVE' | 'REJECTED' | 'HIDDEN' | 'PAUSED';

export const SlotStatus = {
  ACTIVE: 'OPEN',
  OPEN: 'OPEN',
  FULL: 'FULL',
  CANCELLED: 'CANCELLED',
  COMPLETED: 'COMPLETED'
} as const;
export type SlotStatus = 'OPEN' | 'FULL' | 'CANCELLED' | 'COMPLETED';

export const VerificationStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  VERIFIED: 'VERIFIED',
  REJECTED: 'REJECTED'
} as const;
export type VerificationStatus = 'PENDING' | 'APPROVED' | 'VERIFIED' | 'REJECTED';

export const BadgeTier = {
  NONE: 'NONE',
  VERIFIED: 'VERIFIED',
  TOP_RATED: 'TOP_RATED',
  BRONZE: 'BRONZE',
  SILVER: 'SILVER',
  GOLD: 'GOLD',
  PLATINUM: 'PLATINUM'
} as const;
export type BadgeTier = 'NONE' | 'VERIFIED' | 'TOP_RATED' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
