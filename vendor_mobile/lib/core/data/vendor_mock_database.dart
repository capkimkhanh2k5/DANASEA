import '../auth/auth_session.dart';
import 'vendor_mock_repositories.dart';
// Centralized In-Memory Mock Database for DANASEA Vendor App
// Implements ChangeNotifier so any updates to services, slots, sub-orders,
// reviews, vouchers, or settlements reactively update across all screens.

import 'package:flutter/foundation.dart';
import '../models/vendor_models.dart';

class VendorMockDatabase extends ChangeNotifier {
  static final VendorMockDatabase instance = VendorMockDatabase._internal();
  factory VendorMockDatabase() => instance;

  VendorMockDatabase._internal() {
    _initData();
  }

  late UserModel currentUser;
  late VendorModel currentVendor;
  final List<CategoryModel> categories = [];
  final List<VendorDocumentModel> vendorDocuments = [];
  final List<ServiceModel> services = [];
  final List<ServiceSafetyDocumentModel> serviceSafetyDocs = [];
  final List<ServiceSlotModel> slots = [];
  final List<SubOrderModel> subOrders = [];
  final List<RefundModel> refunds = [];
  final List<DiscountCodeModel> vouchers = [];
  final List<DiscountRedemptionModel> redemptions = [];
  final List<ReviewModel> reviews = [];
  final List<SettlementModel> settlements = [];
  final List<PayoutRequestModel> payoutRequests = [];
  final List<DisputeModel> disputes = [];
  final List<ConversationModel> conversations = [];
  final Map<String, List<MessageModel>> messages = {};
  final List<NotificationModel> notifications = [];
  late WeatherCacheModel weatherCache;

  void resetToInitialMock() {
    categories.clear();
    vendorDocuments.clear();
    services.clear();
    serviceSafetyDocs.clear();
    slots.clear();
    subOrders.clear();
    refunds.clear();
    vouchers.clear();
    redemptions.clear();
    reviews.clear();
    settlements.clear();
    payoutRequests.clear();
    disputes.clear();
    conversations.clear();
    messages.clear();
    notifications.clear();
    _initData();
    notifyListeners();
  }

  void _initData() {
    currentUser = UserModel(
      id: 'usr-vnd-001',
      email: 'partner@danangoceanclub.com',
      phone: '0905 888 999',
      fullName: 'Trần Hải Đăng',
      role: UserRole.vendor,
      avatarUrl:
          'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80',
      isEmailVerified: true,
      isLocked: false,
      locale: 'vi',
      createdAt: DateTime(2023, 5, 10),
    );

    currentVendor = VendorModel(
      id: 'vnd-001',
      userId: 'usr-vnd-001',
      businessName: 'Danang Ocean Club',
      taxCode: '0401829482',
      address: 'Lô 12 Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà, Đà Nẵng',
      bankAccountNumber: '0041000889988',
      bankName: 'Vietcombank',
      bankAccountHolder: 'TRAN HAI DANG',
      verificationStatus: VendorVerificationStatus.approved,
      ratingAvg: 4.9,
      ratingCount: 142,
      badgeTier: VendorBadgeTier.verified,
      createdAt: DateTime(2023, 5, 10),
    );

    categories.addAll([
      const CategoryModel(
        id: 'cat-001',
        name: 'Thể thao nước (Chèo SUP / Kayak)',
        nameEn: 'Stand-up Paddleboard & Kayak',
        slug: 'cheo-sup',
        iconUrl: 'surfing',
      ),
      const CategoryModel(
        id: 'cat-002',
        name: 'Lặn ngắm san hô (Snorkeling & Diving)',
        nameEn: 'Scuba Diving & Snorkeling',
        slug: 'lan-bien',
        iconUrl: 'scuba_diving',
      ),
      const CategoryModel(
        id: 'cat-003',
        name: 'Lướt ván diều & Lướt sóng (Surfing)',
        nameEn: 'Surfing & Kitesurfing',
        slug: 'luot-song',
        iconUrl: 'kayaking',
      ),
      const CategoryModel(
        id: 'cat-004',
        name: 'Du thuyền khám phá Bán đảo Sơn Trà',
        nameEn: 'Yacht & Catamaran Tour',
        slug: 'du-thuyen',
        iconUrl: 'sailing',
      ),
    ]);

    vendorDocuments.addAll([
      VendorDocumentModel(
        id: 'vdoc-001',
        vendorId: 'vnd-001',
        docType: VendorDocType.businessLicense,
        fileUrl: 'GPKD_DanangOceanClub_2023.pdf',
        status: DocumentReviewStatus.approved,
        fileName: 'GPKD_DanangOceanClub_2023.pdf',
        fileSize: '1.8 MB',
        uploadedAt: DateTime(2023, 3, 15),
      ),
      VendorDocumentModel(
        id: 'vdoc-002',
        vendorId: 'vnd-001',
        docType: VendorDocType.safetyCert,
        fileUrl: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        status: DocumentReviewStatus.approved,
        fileName: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        fileSize: '2.4 MB',
        uploadedAt: DateTime(2024, 1, 12),
      ),
    ]);

    services.addAll([
      ServiceModel(
        id: 'srv-001',
        vendorId: 'vnd-001',
        categoryId: 'cat-001',
        name: 'Chèo SUP ngắm bình minh Mỹ Khê',
        nameEn: 'Sunrise Stand-up Paddleboarding at My Khe Beach',
        slug: 'cheo-sup-binh-minh-my-khe',
        description:
            'Hành trình xuất phát từ bãi cát Mỹ Khê lúc bình minh 5:00 sáng. Huấn luyện viên chuyên nghiệp kèm cặp, cung cấp áo phao chất lượng cao, ván SUP composite và mái chèo chuẩn thi đấu. Bao gồm chụp ảnh máy cơ bắt trọn khoảnh khắc mặt trời nhô lên khỏi đường chân trời biển Đà Nẵng.',
        descriptionEn:
            'Depart from pristine My Khe beach at dawn (5:00 AM). Accompanied by certified ocean coaches, high-end composite paddleboards, and safety gear. Complimentary DSLR sunrise photography included.',
        price: 280000,
        durationMinutes: 120,
        capacityPerSlot: 10,
        locationName: 'Bến bãi Danang Ocean Club - Bãi biển Mỹ Khê',
        address: 'Lô 12 Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà, Đà Nẵng',
        latitude: 16.0601,
        longitude: 108.2472,
        status: ServiceStatus.published,
        waiverContent:
            'Khách cam kết biết bơi cơ bản, luôn mặc áo phao tiêu chuẩn trong suốt hành trình chèo và tuân thủ tuyệt đối tín hiệu điều phối của huấn luyện viên Danang Ocean Club. Mọi hành vi tự ý tách đoàn vượt quá 200m ra khỏi phao tiêu giới hạn sẽ tự chịu trách nhiệm về an toàn cá nhân.',
        weatherSensitive: true,
        minWindKmh: 0.0,
        maxWaveM: 1.2,
        avgRating: 4.9,
        ratingCount: 142,
        viewCount: 1240,
        imageUrls: [
          'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&auto=format&fit=crop&q=80',
          'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80',
          'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=600&auto=format&fit=crop&q=80',
        ],
        createdAt: DateTime(2023, 6, 1),
      ),
      ServiceModel(
        id: 'srv-002',
        vendorId: 'vnd-001',
        categoryId: 'cat-002',
        name: 'Lặn ngắm san hô Bán đảo Sơn Trà',
        nameEn: 'Scuba Diving & Snorkeling at Son Tra Peninsula',
        slug: 'lan-ngam-san-ho-son-tra',
        description:
            'Trải nghiệm lặn biển cùng chuyên gia lặn chứng chỉ quốc tế PADI. Khám phá các rạn san hô nguyên sơ Hòn Sụp, Bãi Bụt. Toàn bộ trang bị mặt nạ thở, chân vịt, bình khí được kiểm định an toàn hàng hải.',
        descriptionEn:
            'Scuba diving experience with PADI-certified guides. Explore untouched reefs at Hon Sup and Bai But.',
        price: 500000,
        durationMinutes: 180,
        capacityPerSlot: 8,
        locationName: 'Bãi Bụt, Bán đảo Sơn Trà',
        address: 'Hoàng Sa, Bán đảo Sơn Trà, Đà Nẵng',
        latitude: 16.1085,
        longitude: 108.2831,
        status: ServiceStatus.published,
        waiverContent:
            'Khách cam kết không có tiền sử bệnh tim mạch hoặc huyết áp nặng, tuân thủ bảng chỉ dẫn an toàn lặn biển.',
        weatherSensitive: true,
        minWindKmh: 0.0,
        maxWaveM: 0.8,
        avgRating: 4.8,
        ratingCount: 86,
        viewCount: 890,
        imageUrls: [
          'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80',
        ],
        createdAt: DateTime(2023, 7, 1),
      ),
      ServiceModel(
        id: 'srv-003',
        vendorId: 'vnd-001',
        categoryId: 'cat-003',
        name: 'Lướt ván biển Non Nước',
        nameEn: 'Surfing Training Course Non Nuoc Beach',
        slug: 'luot-van-bien-non-nuoc',
        description:
            'Khóa học lướt sóng nhập môn dành cho người mới bắt đầu. Huấn luyện viên kèm 1:2 tại bãi sóng đẹp nhất Đà Nẵng.',
        descriptionEn: 'Beginner surfing course on Non Nuoc beach.',
        price: 350000,
        durationMinutes: 90,
        capacityPerSlot: 6,
        locationName: 'Bãi tắm Non Nước, Đà Nẵng',
        address: 'Đường Trường Sa, Hòa Hải, Ngũ Hành Sơn, Đà Nẵng',
        latitude: 16.0028,
        longitude: 108.2612,
        status: ServiceStatus.draft,
        waiverContent: 'Khách cam kết tuân thủ quy định cứu hộ bãi biển.',
        weatherSensitive: true,
        minWindKmh: 5.0,
        maxWaveM: 1.5,
        avgRating: 5.0,
        ratingCount: 0,
        viewCount: 150,
        imageUrls: [
          'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=600&auto=format&fit=crop&q=80',
        ],
        createdAt: DateTime(2024, 2, 1),
      ),
    ]);

    serviceSafetyDocs.add(
      ServiceSafetyDocumentModel(
        id: 'sdoc-001',
        serviceId: 'srv-001',
        fileUrl: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        status: DocumentReviewStatus.approved,
        reviewedBy: 'Admin Danasea',
        reviewedAt: DateTime(2024, 1, 15),
        fileName: 'ChungNhan_AnToanBien_SHT_2024.pdf',
        uploadedAt: DateTime(2024, 1, 12),
      ),
    );

    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    slots.addAll([
      ServiceSlotModel(
        id: 'slot-001',
        serviceId: 'srv-001',
        date: today,
        startTime: '05:00',
        endTime: '07:00',
        capacity: 10,
        bookedCount: 8,
        heldCount: 1,
        status: SlotStatus.open,
      ),
      ServiceSlotModel(
        id: 'slot-002',
        serviceId: 'srv-001',
        date: today,
        startTime: '07:30',
        endTime: '09:30',
        capacity: 10,
        bookedCount: 6,
        heldCount: 0,
        status: SlotStatus.open,
      ),
      ServiceSlotModel(
        id: 'slot-003',
        serviceId: 'srv-001',
        date: today,
        startTime: '15:30',
        endTime: '17:30',
        capacity: 10,
        bookedCount: 4,
        heldCount: 0,
        status: SlotStatus.open,
      ),
      ServiceSlotModel(
        id: 'slot-004',
        serviceId: 'srv-001',
        date: today,
        startTime: '17:00',
        endTime: '19:00',
        capacity: 8,
        bookedCount: 0,
        heldCount: 0,
        status: SlotStatus.blocked,
      ),
    ]);

    subOrders.addAll([
      SubOrderModel(
        id: 'DNS-8924-1',
        masterOrderId: 'DNS-8924',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        slotId: 'slot-001',
        quantity: 2,
        unitPrice: 280000,
        subtotalAmount: 560000,
        commissionRate: 10.0,
        commissionAmount: 56000,
        vendorPayoutAmount: 504000,
        status: SubOrderStatus.confirmed,
        waiverAccepted: true,
        waiverAcceptedAt: now.subtract(const Duration(hours: 10)),
        qrSecret: 'sec-8924-1-ocean',
        checkedInAt: null,
        customerName: 'Nguyễn Văn An',
        customerPhone: '0905 123 456',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        serviceImageUrl:
            'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&auto=format&fit=crop&q=80',
        slotDate: '${today.day.toString().padLeft(2, '0')}/${today.month.toString().padLeft(2, '0')}/${today.year}',
        slotTime: '05:00 - 07:00',
        createdAt: now.subtract(const Duration(days: 1)),
      ),
      SubOrderModel(
        id: 'DNS-7712-1',
        masterOrderId: 'DNS-7712',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        slotId: 'slot-002',
        quantity: 2,
        unitPrice: 280000,
        subtotalAmount: 560000,
        commissionRate: 10.0,
        commissionAmount: 56000,
        vendorPayoutAmount: 504000,
        status: SubOrderStatus.confirmed,
        waiverAccepted: true,
        waiverAcceptedAt: now.subtract(const Duration(hours: 8)),
        qrSecret: 'sec-7712-1-ocean',
        checkedInAt: now.subtract(const Duration(hours: 1)),
        customerName: 'Trần Minh Đức',
        customerPhone: '0905 234 567',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        serviceImageUrl:
            'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=300&auto=format&fit=crop&q=80',
        slotDate: '${today.day.toString().padLeft(2, '0')}/${today.month.toString().padLeft(2, '0')}/${today.year}',
        slotTime: '07:30 - 09:30',
        createdAt: now.subtract(const Duration(days: 2)),
      ),
      SubOrderModel(
        id: 'DNS-8105-1',
        masterOrderId: 'DNS-8105',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        slotId: 'slot-003',
        quantity: 4,
        unitPrice: 280000,
        subtotalAmount: 1120000,
        commissionRate: 10.0,
        commissionAmount: 112000,
        vendorPayoutAmount: 1008000,
        status: SubOrderStatus.pending,
        waiverAccepted: true,
        waiverAcceptedAt: now.subtract(const Duration(hours: 3)),
        qrSecret: 'sec-8105-1-ocean',
        checkedInAt: null,
        customerName: 'Lê Thu Trang',
        customerPhone: '0905 345 678',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        serviceImageUrl:
            'https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=300&auto=format&fit=crop&q=80',
        slotDate: '${today.day.toString().padLeft(2, '0')}/${today.month.toString().padLeft(2, '0')}/${today.year}',
        slotTime: '15:30 - 17:30',
        createdAt: now.subtract(const Duration(hours: 4)),
      ),
      SubOrderModel(
        id: 'DNS-6502-3',
        masterOrderId: 'DNS-6502',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        slotId: 'slot-001',
        quantity: 2,
        unitPrice: 280000,
        subtotalAmount: 560000,
        commissionRate: 10.0,
        commissionAmount: 56000,
        vendorPayoutAmount: 504000,
        status: SubOrderStatus.completed,
        waiverAccepted: true,
        waiverAcceptedAt: now.subtract(const Duration(days: 4)),
        qrSecret: 'sec-6502-3-ocean',
        checkedInAt: now.subtract(const Duration(days: 4)),
        customerName: 'Hoàng Quốc',
        customerPhone: '0905 456 789',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        serviceImageUrl:
            'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&auto=format&fit=crop&q=80',
        slotDate: '24/10/2024',
        slotTime: '05:00 - 07:00',
        createdAt: now.subtract(const Duration(days: 5)),
      ),
      SubOrderModel(
        id: 'DNS-5421-1',
        masterOrderId: 'DNS-5421',
        vendorId: 'vnd-001',
        serviceId: 'srv-002',
        slotId: 'slot-001',
        quantity: 1,
        unitPrice: 560000,
        subtotalAmount: 560000,
        commissionRate: 10.0,
        commissionAmount: 56000,
        vendorPayoutAmount: 504000,
        status: SubOrderStatus.refunded,
        waiverAccepted: true,
        waiverAcceptedAt: now.subtract(const Duration(days: 6)),
        qrSecret: 'sec-5421-1-ocean',
        checkedInAt: null,
        customerName: 'Hoàng Anh',
        customerPhone: '0905 567 890',
        serviceName: 'Lặn ngắm san hô Bán đảo Sơn Trà',
        serviceImageUrl:
            'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=300&auto=format&fit=crop&q=80',
        slotDate: '27/10/2024',
        slotTime: '05:00 - 07:00',
        createdAt: now.subtract(const Duration(days: 7)),
      ),
    ]);

    refunds.add(
      RefundModel(
        id: 'REF-001',
        subOrderId: 'DNS-5421-1',
        amount: 560000,
        refundPercentage: 100.0,
        reason: RefundReason.weather,
        status: RefundStatus.processed,
        processedAt: now.subtract(const Duration(days: 2)),
        customerName: 'Hoàng Anh',
        serviceName: 'Lặn ngắm san hô Bán đảo Sơn Trà',
        originalSubtotal: 560000,
      ),
    );

    vouchers.addAll([
      DiscountCodeModel(
        id: 'voc-001',
        code: 'SUNRISE10',
        scope: DiscountScope.vendor,
        vendorId: 'vnd-001',
        discountType: DiscountType.percentage,
        discountValue: 10.0,
        maxUses: 50,
        usedCount: 18,
        validFrom: DateTime(2024, 10, 20),
        validTo: DateTime(2024, 11, 15, 23, 59),
        isActive: true,
      ),
      DiscountCodeModel(
        id: 'voc-002',
        code: 'SUPCHILL30K',
        scope: DiscountScope.vendor,
        vendorId: 'vnd-001',
        discountType: DiscountType.fixed,
        discountValue: 30000.0,
        maxUses: 100,
        usedCount: 45,
        validFrom: DateTime(2024, 10, 1),
        validTo: DateTime(2024, 10, 31, 23, 59),
        isActive: true,
      ),
      DiscountCodeModel(
        id: 'voc-003',
        code: 'HEBIEN50',
        scope: DiscountScope.vendor,
        vendorId: 'vnd-001',
        discountType: DiscountType.fixed,
        discountValue: 50000.0,
        maxUses: 30,
        usedCount: 30,
        validFrom: DateTime(2024, 8, 1),
        validTo: DateTime(2024, 9, 15, 23, 59),
        isActive: false,
      ),
    ]);

    redemptions.addAll([
      const DiscountRedemptionModel(
        id: 'red-001',
        discountCodeId: 'voc-001',
        discountCodeText: 'SUNRISE10',
        masterOrderId: 'DNS-8924',
        amountDeducted: 56000,
      ),
      const DiscountRedemptionModel(
        id: 'red-002',
        discountCodeId: 'voc-001',
        discountCodeText: 'SUNRISE10',
        masterOrderId: 'DNS-9102',
        amountDeducted: 28000,
      ),
    ]);

    reviews.addAll([
      ReviewModel(
        id: 'rev-001',
        subOrderId: 'DNS-8924-1',
        customerId: 'usr-cust-001',
        customerName: 'Nguyễn Văn An',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        rating: 5,
        comment:
            'Chuyến đi tuyệt vời, đón bình minh trên biển Mỹ Khê rất thơ mộng. Hướng dẫn viên chỉ dẫn nhiệt tình, chụp hình đẹp có tâm!',
        images: [
          'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&auto=format&fit=crop&q=80',
        ],
        vendorReply: null,
        vendorRepliedAt: null,
        isFlagged: false,
        createdAt: now.subtract(const Duration(hours: 3)),
      ),
      ReviewModel(
        id: 'rev-002',
        subOrderId: 'DNS-7712-1',
        customerId: 'usr-cust-002',
        customerName: 'Trần Minh Đức',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        rating: 5,
        comment:
            'Ván SUP rất mới và chắc chắn, áo phao tiêu chuẩn an toàn. Rất ưng ý dịch vụ của Danang Ocean Club.',
        images: [],
        vendorReply:
            'Cảm ơn bạn Đức đã đồng hành cùng Danang Ocean Club! Chúc bạn luôn có những chuyến du lịch biển tuyệt vời!',
        vendorRepliedAt: now.subtract(const Duration(hours: 1)),
        isFlagged: false,
        createdAt: now.subtract(const Duration(days: 1)),
      ),
      ReviewModel(
        id: 'rev-003',
        subOrderId: 'DNS-4321-1',
        customerId: 'usr-cust-003',
        customerName: 'Đỗ Hoàng My',
        vendorId: 'vnd-001',
        serviceId: 'srv-001',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        rating: 4,
        comment:
            'Trải nghiệm tốt nhưng sáng cuối tuần bãi hơi đông khách, nên có thêm nhân viên hướng dẫn bến xuất phát.',
        images: [],
        vendorReply:
            'Danang Ocean Club ghi nhận đóng góp quý giá từ bạn My. Cơ sở đã bổ sung 2 nhân sự điều phối bãi cát để phục vụ tốt hơn.',
        vendorRepliedAt: now.subtract(const Duration(days: 2)),
        isFlagged: false,
        createdAt: now.subtract(const Duration(days: 3)),
      ),
    ]);

    settlements.addAll([
      SettlementModel(
        id: 'SETTLE-202410-01',
        vendorId: 'vnd-001',
        periodStart: DateTime(2024, 10, 1),
        periodEnd: DateTime(2024, 10, 15),
        grossAmount: 5600000,
        commissionAmount: 560000,
        netPayableAmount: 5040000,
        status: SettlementStatus.pending,
        generatedAt: DateTime(2024, 10, 15),
        orderCount: 10,
      ),
      SettlementModel(
        id: 'SETTLE-202410-02',
        vendorId: 'vnd-001',
        periodStart: DateTime(2024, 10, 16),
        periodEnd: DateTime(2024, 10, 31),
        grossAmount: 3920000,
        commissionAmount: 392000,
        netPayableAmount: 3528000,
        status: SettlementStatus.pending,
        generatedAt: DateTime(2024, 10, 31),
        orderCount: 7,
      ),
      SettlementModel(
        id: 'SETTLE-202409-02',
        vendorId: 'vnd-001',
        periodStart: DateTime(2024, 9, 16),
        periodEnd: DateTime(2024, 9, 30),
        grossAmount: 8400000,
        commissionAmount: 840000,
        netPayableAmount: 7560000,
        status: SettlementStatus.paid,
        generatedAt: DateTime(2024, 9, 30),
        orderCount: 15,
      ),
    ]);

    payoutRequests.addAll([
      PayoutRequestModel(
        id: 'PAY-202410-091',
        vendorId: 'vnd-001',
        settlementId: 'SETTLE-202410-01',
        amount: 5040000,
        status: PayoutRequestStatus.requested,
        createdAt: DateTime(2024, 10, 15, 14, 30),
        bankName: 'Vietcombank',
        bankAccountNumber: '0041000889988',
        bankAccountHolder: 'TRAN HAI DANG',
      ),
      PayoutRequestModel(
        id: 'PAY-202409-042',
        vendorId: 'vnd-001',
        settlementId: 'SETTLE-202409-02',
        amount: 4800000,
        status: PayoutRequestStatus.approved,
        processedBy: 'Admin Danasea',
        processedAt: DateTime(2024, 9, 30, 9, 12),
        createdAt: DateTime(2024, 9, 29, 18, 0),
        bankName: 'Vietcombank',
        bankAccountNumber: '0041000889988',
        bankAccountHolder: 'TRAN HAI DANG',
      ),
      PayoutRequestModel(
        id: 'PAY-202409-015',
        vendorId: 'vnd-001',
        settlementId: null,
        amount: 6250000,
        status: PayoutRequestStatus.paid,
        processedBy: 'Admin Danasea',
        processedAt: DateTime(2024, 9, 16, 16, 45),
        createdAt: DateTime(2024, 9, 15, 10, 0),
        bankName: 'Vietcombank',
        bankAccountNumber: '0041000889988',
        bankAccountHolder: 'TRAN HAI DANG',
      ),
    ]);

    disputes.addAll([
      DisputeModel(
        id: 'DISP-941',
        subOrderId: 'DNS-6502-3',
        raisedBy: 'usr-cust-004',
        customerName: 'Hoàng Quốc',
        serviceName: 'Chèo SUP ngắm bình minh Mỹ Khê',
        category: 'An toàn bến bãi',
        description:
            'Bến bãi không có bảo vệ trông giữ xe và thiếu áo phao dự phòng cho trẻ em đi kèm như tư vấn ban đầu.',
        status: DisputeStatus.inReview,
        createdAt: now.subtract(const Duration(days: 2)),
        customerAttachments: [
          'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=300&auto=format&fit=crop&q=80',
        ],
        vendorAttachments: [],
      ),
      DisputeModel(
        id: 'DISP-812',
        subOrderId: 'DNS-5421-1',
        raisedBy: 'usr-cust-005',
        customerName: 'Hoàng Anh',
        serviceName: 'Tour Lặn biển Ngắm san hô Bán đảo Sơn Trà',
        category: 'Thời tiết bất khả kháng',
        description:
            'Thời tiết biển xấu không xuất bến được theo cam kết.',
        status: DisputeStatus.resolved,
        resolutionNote:
            'Ban quản trị DANASEA đã thẩm định dữ liệu thời tiết thực tế từ trạm khí tượng Sơn Trà. Hoàn tiền 100% cho khách theo diện bất khả kháng.',
        resolvedBy: 'Ban Quản Trị DANASEA',
        resolvedAt: now.subtract(const Duration(days: 2)),
        createdAt: now.subtract(const Duration(days: 4)),
        customerAttachments: [],
        vendorAttachments: [],
      ),
    ]);

    conversations.addAll([
      ConversationModel(
        id: 'conv-001',
        customerId: 'usr-cust-001',
        customerName: 'Nguyễn Văn An',
        customerAvatar:
            'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80',
        vendorId: 'vnd-001',
        masterOrderId: 'DNS-8924',
        subOrderId: 'DNS-8924-1',
        lastMessage: 'Chào shop, sáng mai 5:00 tập trung đúng cổng 2 bãi Mỹ Khê phải không?',
        lastMessageTime: now.subtract(const Duration(minutes: 15)),
        unreadCount: 1,
      ),
      ConversationModel(
        id: 'conv-002',
        customerId: 'usr-cust-006',
        customerName: 'Lê Thu Trang',
        customerAvatar: null,
        vendorId: 'vnd-001',
        masterOrderId: 'DNS-8105',
        subOrderId: 'DNS-8105-1',
        lastMessage: 'Shop chuẩn bị giúp nhóm mình 4 áo phao cỡ M nhé!',
        lastMessageTime: now.subtract(const Duration(hours: 2)),
        unreadCount: 0,
      ),
    ]);

    messages['conv-001'] = [
      MessageModel(
        id: 'msg-001',
        conversationId: 'conv-001',
        senderId: 'usr-cust-001',
        content: 'Dạ shop ơi mình đã đặt vé tour SUP bình minh sáng mai rồi nhé.',
        createdAt: now.subtract(const Duration(hours: 1)),
        isRead: true,
      ),
      MessageModel(
        id: 'msg-002',
        conversationId: 'conv-001',
        senderId: 'usr-vnd-001',
        content:
            'Chào bạn An! Danang Ocean Club đã nhận đơn #DNS-8924-1 của bạn. Bạn nhớ mang theo đồ bơi gọn nhẹ nhé!',
        createdAt: now.subtract(const Duration(minutes: 40)),
        isRead: true,
      ),
      MessageModel(
        id: 'msg-003',
        conversationId: 'conv-001',
        senderId: 'usr-cust-001',
        content: 'Chào shop, sáng mai 5:00 tập trung đúng cổng 2 bãi Mỹ Khê phải không?',
        createdAt: now.subtract(const Duration(minutes: 15)),
        isRead: false,
      ),
    ];

    messages['conv-002'] = [
      MessageModel(
        id: 'msg-101',
        conversationId: 'conv-002',
        senderId: 'usr-cust-006',
        content: 'Shop chuẩn bị giúp nhóm mình 4 áo phao cỡ M nhé!',
        createdAt: now.subtract(const Duration(hours: 2)),
        isRead: true,
      ),
    ];

    notifications.addAll([
      NotificationModel(
        id: 'notif-001',
        userId: 'usr-vnd-001',
        type: 'WEATHER_ALERT',
        title: 'Cảnh báo thời tiết biển Mỹ Khê',
        body: 'Sóng biển duy trì 0.4m, gió 9km/h. Điều kiện an toàn tuyệt đối cho các tour chèo SUP sáng nay.',
        createdAt: now.subtract(const Duration(minutes: 30)),
      ),
      NotificationModel(
        id: 'notif-002',
        userId: 'usr-vnd-001',
        type: 'ORDER_CONFIRMED',
        title: 'Lượt đặt mới: #DNS-8924-1',
        body: 'Khách hàng Nguyễn Văn An đã hoàn tất thanh toán 2 suất chèo SUP lúc 05:00.',
        createdAt: now.subtract(const Duration(hours: 2)),
      ),
      NotificationModel(
        id: 'notif-003',
        userId: 'usr-vnd-001',
        type: 'SETTLEMENT_READY',
        title: 'Kỳ đối soát sẵn sàng quyết toán',
        body: 'Kỳ #SETTLE-202410-01 (5.040.000 đ) đã sẵn sàng. Đối tác có thể gửi yêu cầu nhận tiền.',
        createdAt: now.subtract(const Duration(days: 1)),
      ),
    ]);

    weatherCache = WeatherCacheModel(
      id: 'wth-001',
      locationKey: 'my-khe-da-nang',
      windSpeedKmh: 9.0,
      waveHeightM: 0.4,
      precipitationMm: 0.0,
      fetchedAt: now.subtract(const Duration(minutes: 10)),
      expiresAt: now.add(const Duration(minutes: 50)),
    );
  }

  // Reactive mutations
  void sendMessage(String conversationId, String senderId, String content) {
    final msg = MessageModel(
      id: 'msg-${DateTime.now().millisecondsSinceEpoch}',
      conversationId: conversationId,
      senderId: senderId,
      content: content,
      createdAt: DateTime.now(),
      isRead: true,
    );
    final list = messages.putIfAbsent(conversationId, () => []);
    list.add(msg);
    final cIndex = conversations.indexWhere((c) => c.id == conversationId);
    if (cIndex != -1) {
      conversations[cIndex] = conversations[cIndex].copyWith(
        lastMessage: content,
        lastMessageTime: DateTime.now(),
      );
    }
    notifyStoreChanged();
  }

  void toggleSlotBlocked(String slotId) {
    final index = slots.indexWhere((s) => s.id == slotId || s.slotId == slotId);
    if (index != -1) {
      final current = slots[index];
      slots[index] = current.copyWith(
        status: current.isBlocked ? SlotStatus.open : SlotStatus.blocked,
      );
      notifyStoreChanged();
    }
  }

  void saveSlot(ServiceSlotModel slot) {
    final index = slots.indexWhere((s) => s.id == slot.id || s.slotId == slot.id);
    if (index != -1) {
      slots[index] = slot;
    } else {
      slots.add(slot);
    }
    notifyStoreChanged();
  }

  void deleteSlot(String slotId) {
    slots.removeWhere((s) => s.id == slotId || s.slotId == slotId);
    notifyStoreChanged();
  }

  void checkInSubOrder(String subOrderId) {
    final index = subOrders.indexWhere((o) => o.id == subOrderId || o.subOrderId == subOrderId);
    if (index != -1) {
      subOrders[index] = subOrders[index].copyWith(checkedInAt: DateTime.now());
      notifyStoreChanged();
    }
  }

  void confirmSubOrder(String subOrderId) {
    final index = subOrders.indexWhere((o) => o.id == subOrderId || o.subOrderId == subOrderId);
    if (index != -1) {
      subOrders[index] = subOrders[index].copyWith(status: SubOrderStatus.confirmed);
      notifyStoreChanged();
    }
  }

  void completeSubOrder(String subOrderId) {
    final index = subOrders.indexWhere((o) => o.id == subOrderId || o.subOrderId == subOrderId);
    if (index != -1) {
      subOrders[index] = subOrders[index].copyWith(status: SubOrderStatus.completed);
      notifyStoreChanged();
    }
  }

  void rejectSubOrder(String subOrderId) {
    final index = subOrders.indexWhere((o) => o.id == subOrderId || o.subOrderId == subOrderId);
    if (index != -1) {
      subOrders[index] = subOrders[index].copyWith(status: SubOrderStatus.rejected);
      notifyStoreChanged();
    }
  }

  void saveService(ServiceModel service) {
    final index = services.indexWhere((s) => s.id == service.id || s.serviceId == service.serviceId);
    if (index != -1) {
      services[index] = service;
    } else {
      services.add(service);
    }
    notifyStoreChanged();
  }

  void addServiceSafetyDoc(String serviceId, String fileName, String fileUrl) {
    serviceSafetyDocs.add(
      ServiceSafetyDocumentModel(
        id: 'ssdoc-${DateTime.now().millisecondsSinceEpoch % 10000}',
        serviceId: serviceId,
        fileName: fileName,
        fileUrl: fileUrl,
        status: DocumentReviewStatus.pending,
        uploadedAt: DateTime.now(),
      ),
    );
    notifyStoreChanged();
  }

  VendorModel get vendor => currentVendor;
  WeatherCacheModel get weather => weatherCache;

  void updateVendor(VendorModel updated) {
    currentVendor = updated;
    notifyStoreChanged();
  }

  void markNotificationRead(String notificationId) {
    notifyStoreChanged();
  }


  bool isSyncing = false;
  DateTime? lastSyncedAt;

  Future<void> syncWithApi() async {
    if (isSyncing) return;
    isSyncing = true;
    try {
      if (AuthSession.instance.isAuthenticated) {
        await Future.wait([
          VendorProfileRepository().getVendorProfile().catchError((_) => currentVendor),
          VendorServiceRepository().getServices().catchError((_) => services),
          VendorOrderRepository().getSubOrders().catchError((_) => subOrders),
          VendorReviewRepository().getReviews().catchError((_) => reviews),
          VendorVoucherRepository().getVouchers().catchError((_) => vouchers),
          VendorSettlementRepository().getSettlements().catchError((_) => settlements),
          VendorDisputeRepository().getDisputes().catchError((_) => disputes),
          VendorChatRepository().getConversations().catchError((_) => conversations),
          VendorNotificationRepository().getNotifications().catchError((_) => notifications),
        ]);
        lastSyncedAt = DateTime.now();
        notifyStoreChanged();
      }
    } finally {
      isSyncing = false;
    }
  }

  void notifyStoreChanged() {

    notifyListeners();
  }
}
