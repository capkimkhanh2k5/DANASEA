// DanaSea Domain Models aligned directly with PostgreSQL Database Design
// Reference: E:\DANASEA_Database_Design.docx

// ==========================================
// 1. IAM & USERS
// ==========================================

enum UserRole { customer, vendor, admin }

class UserModel {
  final String id;
  final String email;
  final String? phone;
  final String fullName;
  final UserRole role;
  final String? avatarUrl;
  final bool isEmailVerified;
  final bool isLocked;
  final String locale; // 'vi' or 'en'
  final DateTime createdAt;

  const UserModel({
    required this.id,
    required this.email,
    this.phone,
    required this.fullName,
    this.role = UserRole.customer,
    this.avatarUrl,
    this.isEmailVerified = false,
    this.isLocked = false,
    this.locale = 'vi',
    required this.createdAt,
  });
}

// ==========================================
// 2. VENDORS & CATEGORIES
// ==========================================

enum VendorBadgeTier { none, verified, topRated }

enum VendorVerificationStatus { pending, approved, rejected }

class VendorModel {
  final String id;
  final String userId;
  final String businessName;
  final String? taxCode;
  final String? address;
  final String? bankAccountNumber;
  final String? bankName;
  final String? bankAccountHolder;
  final VendorVerificationStatus verificationStatus;
  final double ratingAvg;
  final int ratingCount;
  final VendorBadgeTier badgeTier;
  final String? avatarUrl;

  const VendorModel({
    required this.id,
    required this.userId,
    required this.businessName,
    this.taxCode,
    this.address,
    this.bankAccountNumber,
    this.bankName,
    this.bankAccountHolder,
    this.verificationStatus = VendorVerificationStatus.approved,
    this.ratingAvg = 5.0,
    this.ratingCount = 0,
    this.badgeTier = VendorBadgeTier.verified,
    this.avatarUrl,
  });
}

class CategoryModel {
  final String id;
  final String name;
  final String nameEn;
  final String slug;
  final String? parentId;
  final String? iconUrl;
  final bool isActive;

  const CategoryModel({
    required this.id,
    required this.name,
    required this.nameEn,
    required this.slug,
    this.parentId,
    this.iconUrl,
    this.isActive = true,
  });
}

// ==========================================
// 3. SERVICES & SLOTS
// ==========================================

enum ServiceStatus { draft, pendingReview, published, rejected, paused }

class CatalogOption {
  final String id, name;
  const CatalogOption({required this.id, required this.name});
}

class CatalogSlot {
  final String id, date, startTime, endTime;
  final String? optionId;
  final int? availableCapacity;
  final int? maxPaxPerPackage;
  final num? price;
  final String? pricingUnit;
  final bool bookable;
  const CatalogSlot({
    required this.id,
    this.optionId,
    required this.date,
    required this.startTime,
    required this.endTime,
    this.availableCapacity,
    this.maxPaxPerPackage,
    this.price,
    this.pricingUnit,
    required this.bookable,
  });
}

class ServiceModel {
  final String id;
  final String vendorId;
  final String vendorName;
  final VendorBadgeTier vendorBadge;
  final String categoryId;
  final String categoryName;
  final String name;
  final String? nameEn;
  final String slug;
  final String description;
  final int price; // numeric in DB, VND integer in app
  final int durationMinutes;
  final int capacityPerSlot;
  final String locationName;
  final String address;
  final double latitude;
  final double longitude;
  final ServiceStatus status;
  final String waiverContent;
  final bool weatherSensitive;
  final double? minWindKmh;
  final double? maxWaveM;
  final double avgRating;
  final int ratingCount;
  final int viewCount;
  final List<String> imageUrls;
  final bool isFavorite;
  final String participantConditions,
      refundPolicy,
      cancellationPolicy,
      safetyRules;
  final bool? waiverRequired;
  final List<CatalogSlot> catalogSlots;
  final List<CatalogOption> options;
  final List<String> availableSlots;

  const ServiceModel({
    required this.id,
    required this.vendorId,
    required this.vendorName,
    this.vendorBadge = VendorBadgeTier.verified,
    required this.categoryId,
    required this.categoryName,
    required this.name,
    this.nameEn,
    required this.slug,
    required this.description,
    required this.price,
    required this.durationMinutes,
    required this.capacityPerSlot,
    required this.locationName,
    required this.address,
    required this.latitude,
    required this.longitude,
    this.status = ServiceStatus.published,
    required this.waiverContent,
    this.weatherSensitive = true,
    this.minWindKmh,
    this.maxWaveM,
    this.avgRating = 5.0,
    this.ratingCount = 0,
    this.viewCount = 0,
    this.imageUrls = const [],
    this.isFavorite = false,
    this.participantConditions = '',
    this.refundPolicy = '',
    this.cancellationPolicy = '',
    this.safetyRules = '',
    this.waiverRequired,
    this.catalogSlots = const [],
    this.options = const [],
    this.availableSlots = const [],
  });

  ServiceModel copyWith({bool? isFavorite, List<CatalogSlot>? catalogSlots}) {
    return ServiceModel(
      id: id,
      vendorId: vendorId,
      vendorName: vendorName,
      vendorBadge: vendorBadge,
      categoryId: categoryId,
      categoryName: categoryName,
      name: name,
      nameEn: nameEn,
      slug: slug,
      description: description,
      price: price,
      durationMinutes: durationMinutes,
      capacityPerSlot: capacityPerSlot,
      locationName: locationName,
      address: address,
      latitude: latitude,
      longitude: longitude,
      status: status,
      waiverContent: waiverContent,
      weatherSensitive: weatherSensitive,
      minWindKmh: minWindKmh,
      maxWaveM: maxWaveM,
      avgRating: avgRating,
      ratingCount: ratingCount,
      viewCount: viewCount,
      imageUrls: imageUrls,
      isFavorite: isFavorite ?? this.isFavorite,
      participantConditions: participantConditions,
      refundPolicy: refundPolicy,
      cancellationPolicy: cancellationPolicy,
      safetyRules: safetyRules,
      waiverRequired: waiverRequired,
      catalogSlots: catalogSlots ?? this.catalogSlots,
      options: options,
      availableSlots: availableSlots,
    );
  }
}

enum SlotStatus { open, closed, blocked }

class ServiceSlotModel {
  final String id;
  final String serviceId;
  final DateTime date;
  final String startTime; // "05:00"
  final String endTime; // "07:00"
  final int capacity;
  final int bookedCount;
  final SlotStatus status;

  const ServiceSlotModel({
    required this.id,
    required this.serviceId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.capacity,
    this.bookedCount = 0,
    this.status = SlotStatus.open,
  });

  int get remainingSlots => capacity - bookedCount;
  bool get isAvailable => status == SlotStatus.open && remainingSlots > 0;
}

// ==========================================
// 4. ORDERS, SUB-ORDERS & PAYMENTS
// ==========================================

enum MasterOrderStatus {
  pendingPayment,
  paid,
  partiallyCompleted,
  completed,
  cancelled,
}

enum SubOrderStatus {
  pending,
  confirmed,
  rejected,
  completed,
  cancelled,
  refunded,
}

enum PaymentProvider { vnpay, momo, sepay, paypal }

enum PaymentStatus { pending, success, failed, refunded }

class MasterOrderModel {
  final String id; // Code, e.g. "DNS-8924"
  final String? bookingId;
  final String customerId;
  final MasterOrderStatus status;
  final int totalAmount;
  final int discountAmount;
  final String? discountCodeId;
  final String? discountCode;
  final DateTime createdAt;
  final DateTime? updatedAt;
  final List<SubOrderModel> subOrders;

  const MasterOrderModel({
    required this.id,
    this.bookingId,
    required this.customerId,
    required this.status,
    required this.totalAmount,
    this.discountAmount = 0,
    this.discountCodeId,
    this.discountCode,
    required this.createdAt,
    this.updatedAt,
    required this.subOrders,
  });

  int get subtotalAmount =>
      subOrders.fold(0, (sum, item) => sum + item.subtotalAmount);
}

class SubOrderModel {
  final String id; // e.g. "DNS-8924-1"
  final String masterOrderId;
  final String vendorId;
  final String vendorName;
  final String serviceId;
  final String serviceName;
  final String serviceImageUrl;
  final String locationName;
  final String slotId;
  final DateTime slotDate;
  final String slotTime; // e.g. "05:00 - 07:00"
  final int quantity;
  final int unitPrice;
  final int subtotalAmount;
  final int discountAmount;
  final int finalAmount;
  final SubOrderStatus status;
  final String? rawStatus;
  final bool waiverRequired;
  final int? waiverVersion;
  final String? waiverContent;
  final String? waiverLanguage;
  final bool waiverFallbackUsed;
  final bool waiverAccepted;
  final DateTime? waiverAcceptedAt;
  final String? waiverAcceptedBy;
  final String? waiverAcceptedLanguage;
  final String? cancellationPolicyVersion;
  final String qrSecret; // UUID for verification
  final DateTime? checkedInAt;

  const SubOrderModel({
    required this.id,
    required this.masterOrderId,
    required this.vendorId,
    required this.vendorName,
    required this.serviceId,
    required this.serviceName,
    required this.serviceImageUrl,
    required this.locationName,
    required this.slotId,
    required this.slotDate,
    required this.slotTime,
    required this.quantity,
    required this.unitPrice,
    required this.subtotalAmount,
    int? discountAmount,
    int? finalAmount,
    required this.status,
    this.rawStatus,
    this.waiverRequired = false,
    this.waiverVersion,
    this.waiverContent,
    this.waiverLanguage,
    this.waiverFallbackUsed = false,
    this.waiverAccepted = true,
    this.waiverAcceptedAt,
    this.waiverAcceptedBy,
    this.waiverAcceptedLanguage,
    this.cancellationPolicyVersion,
    required this.qrSecret,
    this.checkedInAt,
  }) : discountAmount = discountAmount ?? 0,
       finalAmount = finalAmount ?? subtotalAmount;
}

// ==========================================
// 5. REFUNDS & DISPUTES
// ==========================================

enum RefundReason { customerCancel, weather, dispute, compensation }

enum RefundStatus { pending, processed, failed }

class RefundModel {
  final String id;
  final String subOrderId;
  final String subOrderCode;
  final String? masterOrderId;
  final String serviceName;
  final int amount;
  final double refundPercentage;
  final RefundReason reason;
  final String? rawReason;
  final RefundStatus status;
  final String originalPaymentMethod;
  final String? providerTransactionId;
  final String? providerRefundId;
  final String? lastError;
  final DateTime? processedAt;
  final DateTime createdAt;

  const RefundModel({
    required this.id,
    required this.subOrderId,
    required this.subOrderCode,
    this.masterOrderId,
    required this.serviceName,
    required this.amount,
    required this.refundPercentage,
    required this.reason,
    this.rawReason,
    required this.status,
    required this.originalPaymentMethod,
    this.providerTransactionId,
    this.providerRefundId,
    this.lastError,
    this.processedAt,
    required this.createdAt,
  });
}

enum DisputeStatus { open, inReview, resolved, rejected }

class DisputeModel {
  final String id; // e.g. "DISP-1042"
  final String subOrderId;
  final String subOrderCode;
  final String serviceName;
  final String vendorName;
  final String raisedBy;
  final String category;
  final String description;
  final DisputeStatus status;
  final String? resolutionNote;
  final DateTime? resolvedAt;
  final DateTime createdAt;
  final List<String> attachmentUrls;

  const DisputeModel({
    required this.id,
    required this.subOrderId,
    required this.subOrderCode,
    required this.serviceName,
    required this.vendorName,
    required this.raisedBy,
    required this.category,
    required this.description,
    required this.status,
    this.resolutionNote,
    this.resolvedAt,
    required this.createdAt,
    this.attachmentUrls = const [],
  });
}

// ==========================================
// 6. REVIEWS
// ==========================================

class ReviewModel {
  final String id;
  final String subOrderId;
  final String customerId;
  final String customerName;
  final String? customerAvatar;
  final String vendorId;
  final String serviceId;
  final int rating; // 1 to 5
  final String comment;
  final List<String> images;
  final String? vendorReply;
  final DateTime? vendorRepliedAt;
  final bool isFlagged;
  final bool isVisible;
  final String? flagReason;
  final String? moderationNote;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const ReviewModel({
    required this.id,
    required this.subOrderId,
    required this.customerId,
    required this.customerName,
    this.customerAvatar,
    required this.vendorId,
    required this.serviceId,
    required this.rating,
    required this.comment,
    this.images = const [],
    this.vendorReply,
    this.vendorRepliedAt,
    this.isFlagged = false,
    this.isVisible = true,
    this.flagReason,
    this.moderationNote,
    required this.createdAt,
    this.updatedAt,
  });
}

// ==========================================
// 7. NOTIFICATIONS, CONVERSATIONS & CHAT
// ==========================================

enum NotificationChannel { email, sms, telegram, inApp }

class NotificationModel {
  final String id;
  final String userId;
  final String type; // ORDER_CONFIRMED, WEATHER_ALERT, REFUND_STATUS, etc.
  final String title;
  final String body;
  final String? relatedEntityType; // 'order', 'refund', 'weather'
  final String? relatedEntityId;
  final bool isRead;
  final DateTime createdAt;

  const NotificationModel({
    required this.id,
    required this.userId,
    required this.type,
    required this.title,
    required this.body,
    this.relatedEntityType,
    this.relatedEntityId,
    this.isRead = false,
    required this.createdAt,
  });
}

class ConversationModel {
  final String id;
  final String customerId;
  final String vendorId;
  final String vendorName;
  final String? vendorAvatar;
  final String? masterOrderId;
  final String lastMessage;
  final DateTime lastMessageTime;
  final int unreadCount;
  final bool isOnline;

  const ConversationModel({
    required this.id,
    required this.customerId,
    required this.vendorId,
    required this.vendorName,
    this.vendorAvatar,
    this.masterOrderId,
    required this.lastMessage,
    required this.lastMessageTime,
    this.unreadCount = 0,
    this.isOnline = true,
  });
}

class ChatMessageModel {
  final String id;
  final String conversationId;
  final String senderId;
  final bool isMe;
  final String content;
  final String? attachmentUrl;
  final bool isRead;
  final DateTime createdAt;

  const ChatMessageModel({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.isMe,
    required this.content,
    this.attachmentUrl,
    this.isRead = true,
    required this.createdAt,
  });
}

enum AiRole { user, assistant, tool }

class AiMessageModel {
  final String id;
  final AiRole role;
  final String content;
  final ServiceModel? recommendedService;
  final DateTime createdAt;

  const AiMessageModel({
    required this.id,
    required this.role,
    required this.content,
    this.recommendedService,
    required this.createdAt,
  });
}
