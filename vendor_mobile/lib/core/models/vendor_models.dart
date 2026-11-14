// Domain Models for DANASEA Vendor Portal
// Strictly aligned with DATABASE_REFERENCE.txt & SCHEMA_MAP.md

import 'dart:math';
export 'vendor_ai_models.dart';

// ==========================================
// 1. IAM & USERS
// ==========================================

enum UserRole {
  customer,
  vendor,
  admin;

  String get dbValue {
    switch (this) {
      case UserRole.customer:
        return 'CUSTOMER';
      case UserRole.vendor:
        return 'VENDOR';
      case UserRole.admin:
        return 'ADMIN';
    }
  }

  static UserRole fromDb(String val) {
    switch (val.toUpperCase()) {
      case 'VENDOR':
        return UserRole.vendor;
      case 'ADMIN':
        return UserRole.admin;
      default:
        return UserRole.customer;
    }
  }
}

class UserModel {
  final String id;
  final String email;
  final String? phone;
  final String fullName;
  final UserRole role;
  final String? avatarUrl;
  final bool isEmailVerified;
  final bool isLocked;
  final String locale;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const UserModel({
    required this.id,
    required this.email,
    this.phone,
    required this.fullName,
    this.role = UserRole.vendor,
    this.avatarUrl,
    this.isEmailVerified = true,
    this.isLocked = false,
    this.locale = 'vi',
    required this.createdAt,
    this.updatedAt,
  });

  UserModel copyWith({
    String? email,
    String? phone,
    String? fullName,
    String? avatarUrl,
    bool? isEmailVerified,
    bool? isLocked,
    String? locale,
    DateTime? updatedAt,
  }) {
    return UserModel(
      id: id,
      email: email ?? this.email,
      phone: phone ?? this.phone,
      fullName: fullName ?? this.fullName,
      role: role,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      isEmailVerified: isEmailVerified ?? this.isEmailVerified,
      isLocked: isLocked ?? this.isLocked,
      locale: locale ?? this.locale,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}

// ==========================================
// 2. VENDORS & CATEGORIES
// ==========================================

enum VendorVerificationStatus {
  pending,
  approved,
  rejected;

  String get labelVi {
    switch (this) {
      case VendorVerificationStatus.pending:
        return 'Chờ duyệt';
      case VendorVerificationStatus.approved:
        return 'Đã phê duyệt';
      case VendorVerificationStatus.rejected:
        return 'Bị từ chối';
    }
  }
}

enum VendorBadgeTier {
  none,
  verified,
  topRated;

  String get labelVi {
    switch (this) {
      case VendorBadgeTier.none:
        return 'Đối tác mới';
      case VendorBadgeTier.verified:
        return 'Đối tác xác thực';
      case VendorBadgeTier.topRated:
        return 'Đối tác hàng đầu';
    }
  }
}

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
  final String? verifiedBy;
  final DateTime? verifiedAt;
  final double ratingAvg;
  final int ratingCount;
  final VendorBadgeTier badgeTier;
  final DateTime createdAt;

  final String? phone;
  final String? description;

  const VendorModel({
    required this.id,
    required this.userId,
    required this.businessName,
    this.taxCode,
    this.address,
    this.phone,
    this.description,
    this.bankAccountNumber,
    this.bankName,
    this.bankAccountHolder,
    this.verificationStatus = VendorVerificationStatus.approved,
    this.verifiedBy,
    this.verifiedAt,
    this.ratingAvg = 5.0,
    this.ratingCount = 0,
    this.badgeTier = VendorBadgeTier.verified,
    required this.createdAt,
  });

  String? get businessAddress => address;

  static VendorModel empty() => VendorModel(
    id: '',
    userId: '',
    businessName: '',
    taxCode: '',
    address: '',
    phone: '',
    description: '',
    bankAccountNumber: '',
    bankName: '',
    bankAccountHolder: '',
    verificationStatus: VendorVerificationStatus.pending,
    createdAt: DateTime.now(),
  );

  VendorModel copyWith({
    String? businessName,
    String? taxCode,
    String? address,
    String? businessAddress,
    String? phone,
    String? description,
    String? bankAccountNumber,
    String? bankName,
    String? bankAccountHolder,
    VendorVerificationStatus? verificationStatus,
    double? ratingAvg,
    int? ratingCount,
    VendorBadgeTier? badgeTier,
  }) {
    return VendorModel(
      id: id,
      userId: userId,
      businessName: businessName ?? this.businessName,
      taxCode: taxCode ?? this.taxCode,
      address: businessAddress ?? address ?? this.address,
      phone: phone ?? this.phone,
      description: description ?? this.description,
      bankAccountNumber: bankAccountNumber ?? this.bankAccountNumber,
      bankName: bankName ?? this.bankName,
      bankAccountHolder: bankAccountHolder ?? this.bankAccountHolder,
      verificationStatus: verificationStatus ?? this.verificationStatus,
      verifiedBy: verifiedBy,
      verifiedAt: verifiedAt,
      ratingAvg: ratingAvg ?? this.ratingAvg,
      ratingCount: ratingCount ?? this.ratingCount,
      badgeTier: badgeTier ?? this.badgeTier,
      createdAt: createdAt,
    );
  }
}

enum VendorDocType {
  businessLicense,
  safetyCert;

  String get labelVi {
    switch (this) {
      case VendorDocType.businessLicense:
        return 'Giấy phép đăng ký kinh doanh';
      case VendorDocType.safetyCert:
        return 'Chứng nhận an toàn biển & Cứu hộ cơ sở';
    }
  }

  String get dbValue => this == VendorDocType.businessLicense ? 'BUSINESS_LICENSE' : 'SAFETY_CERT';
}

enum DocumentReviewStatus {
  pending,
  approved,
  rejected;

  String get labelVi {
    switch (this) {
      case DocumentReviewStatus.pending:
        return 'Đang chờ duyệt';
      case DocumentReviewStatus.approved:
        return 'Đã phê duyệt';
      case DocumentReviewStatus.rejected:
        return 'Bị từ chối';
    }
  }
}

class VendorDocumentModel {
  final String id;
  final String vendorId;
  final VendorDocType docType;
  final String fileUrl;
  final DocumentReviewStatus status;
  final String? reviewedBy;
  final DateTime? reviewedAt;
  final String fileName;
  final String fileSize;
  final DateTime uploadedAt;

  const VendorDocumentModel({
    required this.id,
    required this.vendorId,
    required this.docType,
    required this.fileUrl,
    this.status = DocumentReviewStatus.pending,
    this.reviewedBy,
    this.reviewedAt,
    required this.fileName,
    required this.fileSize,
    required this.uploadedAt,
  });

  VendorDocumentModel copyWith({
    String? fileUrl,
    DocumentReviewStatus? status,
    String? fileName,
    String? fileSize,
    DateTime? uploadedAt,
  }) {
    return VendorDocumentModel(
      id: id,
      vendorId: vendorId,
      docType: docType,
      fileUrl: fileUrl ?? this.fileUrl,
      status: status ?? this.status,
      reviewedBy: reviewedBy,
      reviewedAt: reviewedAt,
      fileName: fileName ?? this.fileName,
      fileSize: fileSize ?? this.fileSize,
      uploadedAt: uploadedAt ?? this.uploadedAt,
    );
  }
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
// 3. SERVICES & CERTIFICATES & SLOTS
// ==========================================

enum ServiceStatus {
  draft,
  pendingReview,
  published,
  rejected,
  paused;

  String get labelVi {
    switch (this) {
      case ServiceStatus.draft:
        return 'Bản nháp';
      case ServiceStatus.pendingReview:
        return 'Chờ duyệt';
      case ServiceStatus.published:
        return 'Đang hoạt động';
      case ServiceStatus.rejected:
        return 'Bị từ chối';
      case ServiceStatus.paused:
        return 'Tạm dừng';
    }
  }

  String get dbValue {
    switch (this) {
      case ServiceStatus.draft:
        return 'DRAFT';
      case ServiceStatus.pendingReview:
        return 'PENDING_REVIEW';
      case ServiceStatus.published:
        return 'PUBLISHED';
      case ServiceStatus.rejected:
        return 'REJECTED';
      case ServiceStatus.paused:
        return 'PAUSED';
    }
  }
}

class ServiceModel {
  final String id;
  final String vendorId;
  final String categoryId;
  final String name;
  final String nameEn;
  final String slug;
  final String description;
  final String descriptionEn;
  final double price; // EXACTLY 1 price per service specification
  final int durationMinutes;
  final int capacityPerSlot;
  final String locationName;
  final String address;
  final double latitude;
  final double longitude;
  final ServiceStatus status;
  final String? waiverContent;
  final bool weatherSensitive;
  final double minWindKmh; // KEPT exact name and semantics as per spec
  final double maxWaveM;
  final double avgRating;
  final int ratingCount;
  final int viewCount;
  final List<String> imageUrls;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const ServiceModel({
    required this.id,
    required this.vendorId,
    required this.categoryId,
    required this.name,
    required this.nameEn,
    required this.slug,
    required this.description,
    required this.descriptionEn,
    required this.price,
    required this.durationMinutes,
    required this.capacityPerSlot,
    required this.locationName,
    required this.address,
    required this.latitude,
    required this.longitude,
    this.status = ServiceStatus.draft,
    this.waiverContent,
    this.weatherSensitive = true,
    this.minWindKmh = 0.0,
    this.maxWaveM = 1.2,
    this.avgRating = 5.0,
    this.ratingCount = 0,
    this.viewCount = 0,
    this.imageUrls = const [],
    required this.createdAt,
    this.updatedAt,
  });

  String get serviceId => id;
  String get nameVi => name;
  String get meetingPointName => locationName;
  String get meetingPointAddress => address;
  String get category => categoryId;
  int get maxCapacity => capacityPerSlot;
  bool get isWeatherSensitive => weatherSensitive;
  String get descriptionVi => description;
  List<String> get images => imageUrls;

  ServiceModel copyWith({
    String? categoryId,
    String? category,
    String? name,
    String? nameVi,
    String? nameEn,
    String? slug,
    String? description,
    String? descriptionVi,
    String? descriptionEn,
    double? price,
    int? durationMinutes,
    int? capacityPerSlot,
    int? maxCapacity,
    String? locationName,
    String? meetingPointName,
    String? address,
    String? meetingPointAddress,
    double? latitude,
    double? longitude,
    ServiceStatus? status,
    String? waiverContent,
    bool? weatherSensitive,
    bool? isWeatherSensitive,
    double? minWindKmh,
    double? maxWaveM,
    List<String>? imageUrls,
    List<String>? images,
    DateTime? updatedAt,
  }) {
    return ServiceModel(
      id: id,
      vendorId: vendorId,
      categoryId: category ?? categoryId ?? this.categoryId,
      name: nameVi ?? name ?? this.name,
      nameEn: nameEn ?? this.nameEn,
      slug: slug ?? this.slug,
      description: descriptionVi ?? description ?? this.description,
      descriptionEn: descriptionEn ?? this.descriptionEn,
      price: price ?? this.price,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      capacityPerSlot: maxCapacity ?? capacityPerSlot ?? this.capacityPerSlot,
      locationName: meetingPointName ?? locationName ?? this.locationName,
      address: meetingPointAddress ?? address ?? this.address,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      status: status ?? this.status,
      waiverContent: waiverContent ?? this.waiverContent,
      weatherSensitive: isWeatherSensitive ?? weatherSensitive ?? this.weatherSensitive,
      minWindKmh: minWindKmh ?? this.minWindKmh,
      maxWaveM: maxWaveM ?? this.maxWaveM,
      avgRating: avgRating,
      ratingCount: ratingCount,
      viewCount: viewCount,
      imageUrls: images ?? imageUrls ?? this.imageUrls,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}

class VendorServiceOptionModel {
  final String id;
  final String serviceId;
  final String name;
  final String optionType; // 'SHARED' | 'PRIVATE'
  final String pricingUnit; // 'PER_PERSON' | 'PER_PACKAGE'
  final double price;
  final int? maxPaxPerPackage;
  final String? benefits;
  final String status; // 'ACTIVE' | 'INACTIVE'

  const VendorServiceOptionModel({
    required this.id,
    required this.serviceId,
    required this.name,
    this.optionType = 'SHARED',
    this.pricingUnit = 'PER_PERSON',
    required this.price,
    this.maxPaxPerPackage,
    this.benefits,
    this.status = 'ACTIVE',
  });

  factory VendorServiceOptionModel.fromJson(
    Map<String, dynamic> json, {
    String fallbackServiceId = '',
  }) {
    return VendorServiceOptionModel(
      id: json['id']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? fallbackServiceId,
      name: json['name']?.toString() ?? '',
      optionType: json['optionType']?.toString() ?? 'SHARED',
      pricingUnit: json['pricingUnit']?.toString() ?? 'PER_PERSON',
      price: (json['price'] as num?)?.toDouble() ?? 0.0,
      maxPaxPerPackage: (json['maxPaxPerPackage'] as num?)?.toInt(),
      benefits: json['benefits']?.toString(),
      status: json['status']?.toString() ?? 'ACTIVE',
    );
  }

  VendorServiceOptionModel copyWith({
    String? id,
    String? serviceId,
    String? name,
    String? optionType,
    String? pricingUnit,
    double? price,
    int? maxPaxPerPackage,
    String? benefits,
    String? status,
  }) {
    return VendorServiceOptionModel(
      id: id ?? this.id,
      serviceId: serviceId ?? this.serviceId,
      name: name ?? this.name,
      optionType: optionType ?? this.optionType,
      pricingUnit: pricingUnit ?? this.pricingUnit,
      price: price ?? this.price,
      maxPaxPerPackage: maxPaxPerPackage ?? this.maxPaxPerPackage,
      benefits: benefits ?? this.benefits,
      status: status ?? this.status,
    );
  }
}

class ServiceSafetyDocumentModel {
  final String id;
  final String serviceId;
  final String fileUrl;
  final DocumentReviewStatus status;
  final String? reviewedBy;
  final DateTime? reviewedAt;
  final String fileName;
  final DateTime uploadedAt;

  String get docId => id;
  String get documentName => fileName;

  const ServiceSafetyDocumentModel({
    required this.id,
    required this.serviceId,
    required this.fileUrl,
    this.status = DocumentReviewStatus.pending,
    this.reviewedBy,
    this.reviewedAt,
    required this.fileName,
    required this.uploadedAt,
  });

  ServiceSafetyDocumentModel copyWith({
    String? fileUrl,
    DocumentReviewStatus? status,
    String? fileName,
    DateTime? uploadedAt,
  }) {
    return ServiceSafetyDocumentModel(
      id: id,
      serviceId: serviceId,
      fileUrl: fileUrl ?? this.fileUrl,
      status: status ?? this.status,
      reviewedBy: reviewedBy,
      reviewedAt: reviewedAt,
      fileName: fileName ?? this.fileName,
      uploadedAt: uploadedAt ?? this.uploadedAt,
    );
  }
}

enum SlotStatus {
  open,
  closed,
  blocked,
  cancelled;

  String get labelVi {
    switch (this) {
      case SlotStatus.open:
        return 'Mở nhận khách';
      case SlotStatus.closed:
        return 'Đã đóng';
      case SlotStatus.blocked:
        return 'Chặn nhận thêm';
      case SlotStatus.cancelled:
        return 'Đã hủy';
    }
  }

  String get dbValue {
    switch (this) {
      case SlotStatus.open:
        return 'OPEN';
      case SlotStatus.closed:
        return 'CLOSED';
      case SlotStatus.blocked:
        return 'BLOCKED';
      case SlotStatus.cancelled:
        return 'CANCELLED';
    }
  }
}

class ServiceSlotModel {
  final String inventoryType;
  final List<Map<String, int>> units;
  final String id;
  final String serviceId;
  final DateTime date;
  final String startTime; // '05:00'
  final String endTime;   // '07:00'
  final int capacity;
  final int bookedCount;  // READ-ONLY from DB
  final int heldCount;    // Short-term Redis holds simulation
  final SlotStatus status;

  ServiceSlotModel({
    this.inventoryType = 'PERSON_LIMIT',
    this.units = const [],
    String? id,
    String? slotId,
    required this.serviceId,
    DateTime? date,
    dynamic slotDate,
    required this.startTime,
    required this.endTime,
    required this.capacity,
    this.bookedCount = 0,
    this.heldCount = 0,
    SlotStatus? status,
    bool? isBlocked,
    DateTime? createdAt,
    DateTime? updatedAt,
  })  : id = id ?? slotId ?? '',
        date = date ??
            (slotDate is DateTime
                ? slotDate
                : (slotDate is String ? DateTime.tryParse(slotDate) : null) ??
                    DateTime.now()),
        status = status ?? (isBlocked == true ? SlotStatus.blocked : SlotStatus.open);

  int get availableCount => max(0, capacity - bookedCount - heldCount);
  bool get isBlocked => status == SlotStatus.blocked;
  String get slotId => id;
  String get slotDate => '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
  DateTime get createdAt => date;

  bool matchesDate(DateTime target) {
    return date.year == target.year && date.month == target.month && date.day == target.day;
  }

  ServiceSlotModel copyWith({
    int? capacity,
    SlotStatus? status,
    int? bookedCount,
    int? heldCount,
  }) {
    return ServiceSlotModel(
      inventoryType: inventoryType,
      units: units,
      id: id,
      serviceId: serviceId,
      date: date,
      startTime: startTime,
      endTime: endTime,
      capacity: capacity ?? this.capacity,
      bookedCount: bookedCount ?? this.bookedCount,
      heldCount: heldCount ?? this.heldCount,
      status: status ?? this.status,
    );
  }
}

// ==========================================
// 4. ORDERS, SUB-ORDERS, QR & REFUNDS
// ==========================================

enum SubOrderStatus {
  pending,
  confirmed,
  rejected,
  completed,
  cancelled,
  refunded;

  String get labelVi {
    switch (this) {
      case SubOrderStatus.pending:
        return 'Chờ xác nhận';
      case SubOrderStatus.confirmed:
        return 'Đã xác nhận';
      case SubOrderStatus.rejected:
        return 'Từ chối';
      case SubOrderStatus.completed:
        return 'Đã hoàn thành';
      case SubOrderStatus.cancelled:
        return 'Đã hủy';
      case SubOrderStatus.refunded:
        return 'Đã hoàn tiền';
    }
  }

  String get dbValue {
    switch (this) {
      case SubOrderStatus.pending:
        return 'PENDING';
      case SubOrderStatus.confirmed:
        return 'CONFIRMED';
      case SubOrderStatus.rejected:
        return 'REJECTED';
      case SubOrderStatus.completed:
        return 'COMPLETED';
      case SubOrderStatus.cancelled:
        return 'CANCELLED';
      case SubOrderStatus.refunded:
        return 'REFUNDED';
    }
  }
}

class SubOrderModel {
  final String id;               // e.g. "DNS-8924-1"
  final String masterOrderId;     // e.g. "DNS-8924"
  final String vendorId;
  final String serviceId;
  final String slotId;
  final int quantity;
  final double unitPrice;
  final double subtotalAmount;
  final double commissionRate;    // e.g. 10.0
  final double commissionAmount;  // snapshot at booking
  final double vendorPayoutAmount;// subtotal - commission
  final SubOrderStatus status;
  final bool waiverAccepted;
  final DateTime? waiverAcceptedAt;
  final String qrSecret;          // Secret identifier for signature (not leaked directly in UI)
  final DateTime? checkedInAt;    // SEPARATED from order status
  final String customerName;
  final String customerPhone;
  final String serviceName;
  final String serviceImageUrl;
  final String slotDate;
  final String slotTime;
  final DateTime createdAt;

  const SubOrderModel({
    required this.id,
    required this.masterOrderId,
    required this.vendorId,
    required this.serviceId,
    required this.slotId,
    required this.quantity,
    required this.unitPrice,
    required this.subtotalAmount,
    required this.commissionRate,
    required this.commissionAmount,
    required this.vendorPayoutAmount,
    this.status = SubOrderStatus.pending,
    this.waiverAccepted = true,
    this.waiverAcceptedAt,
    required this.qrSecret,
    this.checkedInAt,
    required this.customerName,
    required this.customerPhone,
    required this.serviceName,
    required this.serviceImageUrl,
    required this.slotDate,
    required this.slotTime,
    required this.createdAt,
  });

  bool get isCheckedIn => checkedInAt != null;
  String get subOrderCode => id;
  String get subOrderId => id;
  String get masterOrderCode => masterOrderId;
  double get totalPrice => subtotalAmount;
  double get payoutAmount => vendorPayoutAmount;
  DateTime? get waiverSignedAt => waiverAcceptedAt;

  SubOrderModel copyWith({
    SubOrderStatus? status,
    DateTime? checkedInAt,
  }) {
    return SubOrderModel(
      id: id,
      masterOrderId: masterOrderId,
      vendorId: vendorId,
      serviceId: serviceId,
      slotId: slotId,
      quantity: quantity,
      unitPrice: unitPrice,
      subtotalAmount: subtotalAmount,
      commissionRate: commissionRate,
      commissionAmount: commissionAmount,
      vendorPayoutAmount: vendorPayoutAmount,
      status: status ?? this.status,
      waiverAccepted: waiverAccepted,
      waiverAcceptedAt: waiverAcceptedAt,
      qrSecret: qrSecret,
      checkedInAt: checkedInAt ?? this.checkedInAt,
      customerName: customerName,
      customerPhone: customerPhone,
      serviceName: serviceName,
      serviceImageUrl: serviceImageUrl,
      slotDate: slotDate,
      slotTime: slotTime,
      createdAt: createdAt,
    );
  }
}

enum RefundReason {
  customerCancel,
  weather,
  dispute,
  compensation;

  String get labelVi {
    switch (this) {
      case RefundReason.customerCancel:
        return 'Khách hàng hủy vé';
      case RefundReason.weather:
        return 'Thời tiết bất khả kháng';
      case RefundReason.dispute:
        return 'Tranh chấp khiếu nại';
      case RefundReason.compensation:
        return 'Bù trừ tách đơn';
    }
  }

  String get dbValue {
    switch (this) {
      case RefundReason.customerCancel:
        return 'CUSTOMER_CANCEL';
      case RefundReason.weather:
        return 'WEATHER';
      case RefundReason.dispute:
        return 'DISPUTE';
      case RefundReason.compensation:
        return 'COMPENSATION';
    }
  }
}

enum RefundStatus {
  pending,
  processed,
  failed;

  String get labelVi {
    switch (this) {
      case RefundStatus.pending:
        return 'Đang chờ xử lý';
      case RefundStatus.processed:
        return 'Đã xử lý thành công';
      case RefundStatus.failed:
        return 'Thất bại';
    }
  }

  String get dbValue {
    switch (this) {
      case RefundStatus.pending:
        return 'PENDING';
      case RefundStatus.processed:
        return 'PROCESSED';
      case RefundStatus.failed:
        return 'FAILED';
    }
  }
}

class RefundModel {
  final String id;
  final String subOrderId;
  final double amount;
  final double refundPercentage;
  final RefundReason reason;
  final RefundStatus status;
  final DateTime? processedAt;
  final String? customerName;
  final String? serviceName;
  final double originalSubtotal;

  const RefundModel({
    required this.id,
    required this.subOrderId,
    required this.amount,
    required this.refundPercentage,
    required this.reason,
    required this.status,
    this.processedAt,
    this.customerName,
    this.serviceName,
    required this.originalSubtotal,
  });

  String get refundId => id;
  double get percentage => refundPercentage;
  bool get isProcessed => status == RefundStatus.processed;
  DateTime get createdAt => processedAt ?? DateTime.now();
}

// ==========================================
// 5. DISCOUNT CODES & REDEMPTIONS
// ==========================================

enum DiscountScope {
  platform,
  vendor;

  String get dbValue => this == DiscountScope.platform ? 'PLATFORM' : 'VENDOR';
}

enum DiscountType {
  percentage,
  fixed;

  String get labelVi => this == DiscountType.percentage ? 'Phần trăm (%)' : 'Cố định (VNĐ)';
  String get dbValue => this == DiscountType.percentage ? 'PERCENTAGE' : 'FIXED';
}

class DiscountCodeModel {
  final String id;
  final String code;
  final DiscountScope scope;
  final String? vendorId;
  final DiscountType discountType;
  final double discountValue;
  final int maxUses;
  final int usedCount; // Read-only from DB
  final DateTime validFrom;
  final DateTime validTo;
  final bool isActive;

  const DiscountCodeModel({
    required this.id,
    required this.code,
    this.scope = DiscountScope.vendor,
    this.vendorId,
    required this.discountType,
    required this.discountValue,
    required this.maxUses,
    this.usedCount = 0,
    required this.validFrom,
    required this.validTo,
    this.isActive = true,
  });

  DiscountCodeModel copyWith({
    String? code,
    DiscountType? discountType,
    double? discountValue,
    int? maxUses,
    DateTime? validFrom,
    DateTime? validTo,
    bool? isActive,
  }) {
    return DiscountCodeModel(
      id: id,
      code: code ?? this.code,
      scope: scope,
      vendorId: vendorId,
      discountType: discountType ?? this.discountType,
      discountValue: discountValue ?? this.discountValue,
      maxUses: maxUses ?? this.maxUses,
      usedCount: usedCount,
      validFrom: validFrom ?? this.validFrom,
      validTo: validTo ?? this.validTo,
      isActive: isActive ?? this.isActive,
    );
  }
}

class DiscountRedemptionModel {
  final String id;
  final String discountCodeId;
  final String discountCodeText;
  final String masterOrderId;
  final double amountDeducted;

  const DiscountRedemptionModel({
    required this.id,
    required this.discountCodeId,
    required this.discountCodeText,
    required this.masterOrderId,
    required this.amountDeducted,
  });
}

// ==========================================
// 6. REVIEWS & RESPONSES
// ==========================================

class ReviewModel {
  final String id;
  final String subOrderId;
  final String customerId;
  final String customerName;
  final String vendorId;
  final String serviceId;
  final String serviceName;
  final int rating; // 1-5 (READ-ONLY FOR VENDOR)
  final String comment; // READ-ONLY FOR VENDOR
  final List<String> images; // READ-ONLY FOR VENDOR
  final String? vendorReply; // EDITABLE BY VENDOR
  final DateTime? vendorRepliedAt;
  final bool isFlagged;
  final DateTime createdAt;

  const ReviewModel({
    required this.id,
    required this.subOrderId,
    required this.customerId,
    required this.customerName,
    required this.vendorId,
    required this.serviceId,
    required this.serviceName,
    required this.rating,
    required this.comment,
    this.images = const [],
    this.vendorReply,
    this.vendorRepliedAt,
    this.isFlagged = false,
    required this.createdAt,
  });

  ReviewModel copyWith({
    String? vendorReply,
    DateTime? vendorRepliedAt,
  }) {
    return ReviewModel(
      id: id,
      subOrderId: subOrderId,
      customerId: customerId,
      customerName: customerName,
      vendorId: vendorId,
      serviceId: serviceId,
      serviceName: serviceName,
      rating: rating,
      comment: comment,
      images: images,
      vendorReply: vendorReply ?? this.vendorReply,
      vendorRepliedAt: vendorRepliedAt ?? this.vendorRepliedAt,
      isFlagged: isFlagged,
      createdAt: createdAt,
    );
  }
}

// ==========================================
// 7. SETTLEMENTS & PAYOUT REQUESTS
// ==========================================

enum SettlementStatus {
  pending,
  paid;

  String get labelVi => this == SettlementStatus.pending ? 'Chờ đối soát' : 'Đã thanh toán';
  String get dbValue => this == SettlementStatus.pending ? 'PENDING' : 'PAID';
}

class SettlementModel {
  final String id; // e.g. "DNS-SETTLE-2024-10A"
  final String vendorId;
  final DateTime periodStart;
  final DateTime periodEnd;
  final double grossAmount;
  final double commissionAmount;
  final double netPayableAmount; // gross - commission
  final SettlementStatus status;
  final DateTime generatedAt;
  final int orderCount;

  const SettlementModel({
    required this.id,
    required this.vendorId,
    required this.periodStart,
    required this.periodEnd,
    required this.grossAmount,
    required this.commissionAmount,
    required this.netPayableAmount,
    required this.status,
    required this.generatedAt,
    this.orderCount = 0,
  });
}

enum PayoutRequestStatus {
  requested,
  approved,
  paid,
  rejected;

  String get labelVi {
    switch (this) {
      case PayoutRequestStatus.requested:
        return 'Chờ duyệt (REQUESTED)';
      case PayoutRequestStatus.approved:
        return 'Đã duyệt, chờ chi (APPROVED)';
      case PayoutRequestStatus.paid:
        return 'Đã thanh toán (PAID)';
      case PayoutRequestStatus.rejected:
        return 'Từ chối duyệt (REJECTED)';
    }
  }

  String get dbValue {
    switch (this) {
      case PayoutRequestStatus.requested:
        return 'REQUESTED';
      case PayoutRequestStatus.approved:
        return 'APPROVED';
      case PayoutRequestStatus.paid:
        return 'PAID';
      case PayoutRequestStatus.rejected:
        return 'REJECTED';
    }
  }
}

class PayoutRequestModel {
  final String id;
  final String vendorId;
  final String? settlementId; // NULLABLE when combined across periods (UI preview)
  final double amount;
  final PayoutRequestStatus status;
  final String? processedBy;
  final DateTime? processedAt;
  final DateTime createdAt;
  final String bankName;
  final String bankAccountNumber;
  final String bankAccountHolder;

  const PayoutRequestModel({
    required this.id,
    required this.vendorId,
    this.settlementId,
    required this.amount,
    required this.status,
    this.processedBy,
    this.processedAt,
    required this.createdAt,
    required this.bankName,
    required this.bankAccountNumber,
    required this.bankAccountHolder,
  });
}

// ==========================================
// 8. DISPUTES & ATTACHMENTS
// ==========================================

enum DisputeStatus {
  open,
  inReview,
  resolved,
  rejected;

  String get labelVi {
    switch (this) {
      case DisputeStatus.open:
        return 'Chờ xử lý';
      case DisputeStatus.inReview:
        return 'Đang thẩm định';
      case DisputeStatus.resolved:
        return 'Đã giải quyết';
      case DisputeStatus.rejected:
        return 'Đã từ chối';
    }
  }

  String get dbValue {
    switch (this) {
      case DisputeStatus.open:
        return 'OPEN';
      case DisputeStatus.inReview:
        return 'IN_REVIEW';
      case DisputeStatus.resolved:
        return 'RESOLVED';
      case DisputeStatus.rejected:
        return 'REJECTED';
    }
  }
}

class DisputeModel {
  final String id;
  final String subOrderId;
  final String raisedBy;
  final String customerName;
  final String serviceName;
  final String category;
  final String description;
  final DisputeStatus status;
  final String? resolutionNote;
  final String? resolvedBy;
  final DateTime? resolvedAt;
  final DateTime createdAt;
  final List<String> customerAttachments;
  final List<String> vendorAttachments;

  const DisputeModel({
    required this.id,
    required this.subOrderId,
    required this.raisedBy,
    required this.customerName,
    required this.serviceName,
    required this.category,
    required this.description,
    required this.status,
    this.resolutionNote,
    this.resolvedBy,
    this.resolvedAt,
    required this.createdAt,
    this.customerAttachments = const [],
    this.vendorAttachments = const [],
  });

  DisputeModel copyWith({
    List<String>? vendorAttachments,
    DisputeStatus? status,
  }) {
    return DisputeModel(
      id: id,
      subOrderId: subOrderId,
      raisedBy: raisedBy,
      customerName: customerName,
      serviceName: serviceName,
      category: category,
      description: description,
      status: status ?? this.status,
      resolutionNote: resolutionNote,
      resolvedBy: resolvedBy,
      resolvedAt: resolvedAt,
      createdAt: createdAt,
      customerAttachments: customerAttachments,
      vendorAttachments: vendorAttachments ?? this.vendorAttachments,
    );
  }
}

// ==========================================
// 9. CHAT & MESSAGES
// ==========================================

class ConversationModel {
  final String id;
  final String customerId;
  final String customerName;
  final String? customerAvatar;
  final String vendorId;
  final String? masterOrderId;
  final String? subOrderId;
  final String? serviceNameValue;
  final String lastMessage;
  final DateTime lastMessageTime;
  final int unreadCount;

  const ConversationModel({
    required this.id,
    required this.customerId,
    required this.customerName,
    this.customerAvatar,
    required this.vendorId,
    this.masterOrderId,
    this.subOrderId,
    this.serviceNameValue,
    required this.lastMessage,
    required this.lastMessageTime,
    this.unreadCount = 0,
  });

  String get conversationId => id;
  String? get subOrderCode => subOrderId;
  String? get masterOrderCode => masterOrderId;
  String get serviceName => serviceNameValue ?? '';

  ConversationModel copyWith({
    String? lastMessage,
    DateTime? lastMessageTime,
    int? unreadCount,
  }) {
    return ConversationModel(
      id: id,
      customerId: customerId,
      customerName: customerName,
      customerAvatar: customerAvatar,
      vendorId: vendorId,
      masterOrderId: masterOrderId,
      subOrderId: subOrderId,
      serviceNameValue: serviceNameValue,
      lastMessage: lastMessage ?? this.lastMessage,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      unreadCount: unreadCount ?? this.unreadCount,
    );
  }
}

class MessageModel {
  final String id;
  final String conversationId;
  final String senderId;
  final String content;
  final String? attachmentUrl;
  final bool isRead;
  final DateTime createdAt;

  DateTime get sentAt => createdAt;

  const MessageModel({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.content,
    this.attachmentUrl,
    this.isRead = false,
    required this.createdAt,
  });
}

// ==========================================
// 10. NOTIFICATIONS & WEATHER
// ==========================================

class NotificationModel {
  final String id;
  final String userId;
  final String type; // ORDER_CONFIRMED, WEATHER_ALERT, REFUND_STATUS
  final String channel; // IN_APP, EMAIL, SMS
  final String title;
  final String body;
  final String? relatedEntityType;
  final String? relatedEntityId;
  final String status; // PENDING, SENT, FAILED
  final DateTime createdAt;
  final DateTime? sentAt;

  const NotificationModel({
    required this.id,
    required this.userId,
    required this.type,
    this.channel = 'IN_APP',
    required this.title,
    required this.body,
    this.relatedEntityType,
    this.relatedEntityId,
    this.status = 'SENT',
    required this.createdAt,
    this.sentAt,
  });

  String get notificationId => id;
  String get content => body;
  bool get isRead => status == 'READ';
}

class VendorDashboardModel {
  final double totalRevenue;
  final double netPayout;
  final int newOrders;
  final int completedOrders;
  final double completionRate;
  final double cancellationRate;

  const VendorDashboardModel({
    this.totalRevenue = 0.0,
    this.netPayout = 0.0,
    this.newOrders = 0,
    this.completedOrders = 0,
    this.completionRate = 0.0,
    this.cancellationRate = 0.0,
  });

  factory VendorDashboardModel.fromJson(Map<String, dynamic> json) {
    return VendorDashboardModel(
      totalRevenue: (json['totalRevenue'] as num?)?.toDouble() ?? 0.0,
      netPayout: (json['netPayout'] as num?)?.toDouble() ?? 0.0,
      newOrders: (json['newOrders'] as num?)?.toInt() ?? 0,
      completedOrders: (json['completedOrders'] as num?)?.toInt() ?? 0,
      completionRate: (json['completionRate'] as num?)?.toDouble() ?? 0.0,
      cancellationRate: (json['cancellationRate'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class WeatherCacheModel {
  final String id;
  final String locationKey;
  final String? locationNameValue;
  final double windSpeedKmh;
  final double waveHeightM;
  final double precipitationMm;
  final DateTime fetchedAt;
  final DateTime expiresAt;

  const WeatherCacheModel({
    required this.id,
    required this.locationKey,
    this.locationNameValue,
    required this.windSpeedKmh,
    required this.waveHeightM,
    required this.precipitationMm,
    required this.fetchedAt,
    required this.expiresAt,
  });

  String get locationName =>
      (locationNameValue != null && locationNameValue!.isNotEmpty)
          ? locationNameValue!
          : locationKey;
}

class AdvanceSafetyCheckModel {
  final String safetyStatus; // SAFE, CAUTION, DANGEROUS, BLOCKED
  final bool bookingAllowed;
  final String summaryMessage;
  final List<String> advisoryDetails;
  final double? windSpeedKmh;
  final double? waveHeightM;
  final double? precipitationMm;

  const AdvanceSafetyCheckModel({
    this.safetyStatus = 'SAFE',
    this.bookingAllowed = true,
    this.summaryMessage = '',
    this.advisoryDetails = const [],
    this.windSpeedKmh,
    this.waveHeightM,
    this.precipitationMm,
  });

  factory AdvanceSafetyCheckModel.fromJson(Map<String, dynamic> json) {
    final details = <String>[];
    if (json['advisoryDetails'] is List) {
      for (final item in json['advisoryDetails']) {
        if (item != null && item.toString().isNotEmpty) {
          details.add(item.toString());
        }
      }
    }
    return AdvanceSafetyCheckModel(
      safetyStatus: (json['safetyStatus'] ?? json['status'] ?? 'SAFE').toString(),
      bookingAllowed: (json['canBook'] ?? json['bookingAllowed']) != false,
      summaryMessage: (json['summaryMessage'] ?? json['message'] ?? '').toString(),
      advisoryDetails: details,
      windSpeedKmh: (json['windSpeedKmh'] ?? json['windSpeed'] as num?)?.toDouble(),
      waveHeightM: (json['waveHeightM'] ?? json['waveHeight'] as num?)?.toDouble(),
      precipitationMm: (json['precipitationMm'] ?? json['precipitation'] as num?)?.toDouble(),
    );
  }

  bool get canBook => bookingAllowed;
}

class OperatingScheduleExceptionModel {
  final String id;
  final String dateFrom;
  final String dateTo;
  final String? startTime;
  final String reason;

  const OperatingScheduleExceptionModel({
    required this.id,
    required this.dateFrom,
    required this.dateTo,
    this.startTime,
    required this.reason,
  });

  factory OperatingScheduleExceptionModel.fromJson(Map<String, dynamic> json) {
    final source = json['exception'] is Map<String, dynamic>
        ? json['exception'] as Map<String, dynamic>
        : json;
    return OperatingScheduleExceptionModel(
      id: source['id']?.toString() ?? '',
      dateFrom: source['dateFrom']?.toString() ?? '',
      dateTo: source['dateTo']?.toString() ?? '',
      startTime: source['startTime']?.toString(),
      reason: source['reason']?.toString() ?? '',
    );
  }
}

class VendorEquipmentModel {
  final String id;
  final String name;
  final String compatibilityGroup;
  final int capacity;

  const VendorEquipmentModel({
    required this.id,
    required this.name,
    required this.compatibilityGroup,
    required this.capacity,
  });

  factory VendorEquipmentModel.fromJson(Map<String, dynamic> json) {
    return VendorEquipmentModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      compatibilityGroup: json['compatibilityGroup']?.toString() ?? 'WATER_SPORTS',
      capacity: (json['capacity'] as num?)?.toInt() ?? 1,
    );
  }
}

class EquipmentMaintenanceResultModel {
  final String maintenanceId;
  final String equipmentId;
  final String start;
  final String end;
  final bool urgent;
  final String reason;
  final int affectedSlotsCount;

  const EquipmentMaintenanceResultModel({
    required this.maintenanceId,
    required this.equipmentId,
    required this.start,
    required this.end,
    this.urgent = false,
    required this.reason,
    this.affectedSlotsCount = 0,
  });

  factory EquipmentMaintenanceResultModel.fromJson(Map<String, dynamic> json) {
    return EquipmentMaintenanceResultModel(
      maintenanceId: json['maintenanceId']?.toString() ?? json['id']?.toString() ?? '',
      equipmentId: json['equipmentId']?.toString() ?? '',
      start: json['start']?.toString() ?? '',
      end: json['end']?.toString() ?? '',
      urgent: json['urgent'] == true,
      reason: json['reason']?.toString() ?? '',
      affectedSlotsCount: (json['affectedSlots'] is List)
          ? (json['affectedSlots'] as List).length
          : ((json['affectedSlotsCount'] as num?)?.toInt() ?? 0),
    );
  }
}


