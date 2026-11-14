// Repository Interfaces for DANASEA Vendor App
// Unified Spring Boot REST backend APIs (/api/vendor/...)

import '../models/vendor_models.dart';

abstract class IVendorAuthRepository {
  Future<UserModel> getCurrentUser();
  Future<UserModel> login(String email, String password);
  Future<UserModel> register({
    required String fullName,
    required String email,
    String? phone,
    required String password,
  });
  Future<void> sendVerificationEmail(String email);
  Future<void> requestPasswordReset(String email);
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  });
  Future<void> logout();
}

abstract class IVendorProfileRepository {
  Future<VendorModel> getVendorProfile();
  Future<VendorModel> updateVendorProfile({
    String? businessName,
    String? taxCode,
    String? address,
    String? avatarUrl,
    String? fullName,
    String? phone,
  });
  Future<VendorModel> updateBankProfile({
    required String bankName,
    required String bankAccountNumber,
    required String bankAccountHolder,
  });
}

abstract class IVendorDocumentRepository {
  Future<List<VendorDocumentModel>> getVendorDocuments();
  Future<VendorDocumentModel> uploadOrReplaceDocument({
    required VendorDocType docType,
    required String fileName,
    required String fileSize,
    String? filePath,
  });
}

abstract class IVendorServiceRepository {
  Future<List<CategoryModel>> getCategories();
  Future<List<ServiceModel>> getServices();
  Future<ServiceModel?> getServiceById(String id);
  Future<ServiceModel> createService(ServiceModel service);
  Future<ServiceModel> updateService(ServiceModel service);
  Future<ServiceModel> updateServiceStatus(String serviceId, ServiceStatus status);
  Future<Map<String, dynamic>> uploadServiceImage({
    required String serviceId,
    required String filePath,
  });
  Future<void> deleteServiceImage({
    required String serviceId,
    required String imageId,
  });
  Future<void> reorderServiceImages({
    required String serviceId,
    required List<String> imageIds,
  });
  Future<List<ServiceSafetyDocumentModel>> getServiceSafetyDocuments(String serviceId);
  Future<ServiceSafetyDocumentModel> uploadServiceSafetyDocument({
    required String serviceId,
    required String fileName,
    String? filePath,
  });
  Future<List<VendorServiceOptionModel>> getServiceOptions(String serviceId);
  Future<VendorServiceOptionModel> createServiceOption({
    required String serviceId,
    required String name,
    required String optionType,
    required String pricingUnit,
    required double price,
    int? maxPaxPerPackage,
    String? benefits,
  });
  Future<VendorServiceOptionModel> updateServiceOption({
    required String serviceId,
    required String optionId,
    String? name,
    String? optionType,
    String? pricingUnit,
    double? price,
    int? maxPaxPerPackage,
    String? benefits,
    String? status,
  });
}

abstract class IVendorSlotRepository {
  Future<List<ServiceSlotModel>> getSlotsForService(String serviceId, DateTime date);
  Future<ServiceSlotModel?> getSlotById(String slotId);
  Future<ServiceSlotModel> saveSlot(ServiceSlotModel slot);
  Future<ServiceSlotModel> updateSlotCapacity(String slotId, int newCapacity);
  Future<ServiceSlotModel> toggleBlockSlot(String slotId, bool blocked);
  Future<Map<String, dynamic>> cancelSlot(String serviceId, String slotId, String reason);
  Future<Map<String, dynamic>?> getOperatingSchedule(String serviceId);
  Future<Map<String, dynamic>> updateOperatingSchedule(String serviceId, Map<String, dynamic> schedule);
  Future<List<OperatingScheduleExceptionModel>> listScheduleExceptions(String serviceId);
  Future<OperatingScheduleExceptionModel> addScheduleException({
    required String serviceId,
    required String dateFrom,
    required String dateTo,
    String? startTime,
    required String reason,
  });
  Future<void> removeScheduleException(String serviceId, String exceptionId);
}

enum TicketLookupResultType {
  valid,
  used,
  pending,
  cancelled,
  refunded,
  invalid;

  String get message {
    switch (this) {
      case TicketLookupResultType.valid:
        return 'Vé hợp lệ. Có thể xác nhận check-in.';
      case TicketLookupResultType.used:
        return 'Vé đã check-in trước đó. Không thể sử dụng lại.';
      case TicketLookupResultType.pending:
        return 'Đơn chưa được xác nhận. Chưa thể check-in.';
      case TicketLookupResultType.cancelled:
        return 'Đơn đã hủy. Không thể check-in.';
      case TicketLookupResultType.refunded:
        return 'Đơn đã hoàn tiền. Không thể check-in.';
      case TicketLookupResultType.invalid:
        return 'Mã vé không hợp lệ hoặc không thuộc nhà cung cấp này.';
    }
  }
}

class TicketLookupResult {
  final TicketLookupResultType type;
  final SubOrderModel? subOrder;
  final String message;

  const TicketLookupResult({
    required this.type,
    this.subOrder,
    required this.message,
  });
}

abstract class IVendorOrderRepository {
  Future<List<SubOrderModel>> getSubOrders({SubOrderStatus? filterStatus});
  Future<List<SubOrderModel>> getBookings({String? status, int page = 0, int size = 20});
  Future<SubOrderModel?> getSubOrderById(String subOrderId);
  Future<SubOrderModel> confirmSubOrder(String subOrderId);
  Future<SubOrderModel> rejectSubOrder(String subOrderId);
  Future<SubOrderModel> checkInSubOrder(String subOrderId);
  Future<SubOrderModel> completeSubOrder(String subOrderId);
  Future<TicketLookupResult> lookupTicket(String ticketCode);
  Future<Map<String, dynamic>> createRescheduleProposal({
    required String subOrderId,
    required String proposedSlotId,
    required String reason,
    String reasonType = 'WEATHER',
    DateTime? expiresAt,
  });
}

abstract class IVendorReviewRepository {
  Future<List<ReviewModel>> getReviews({String? filter});
  Future<ReviewModel> replyToReview(String reviewId, String replyContent);
}

abstract class IVendorVoucherRepository {
  Future<List<DiscountCodeModel>> getVouchers();
  Future<DiscountCodeModel> createVoucher(DiscountCodeModel voucher);
  Future<DiscountCodeModel> updateVoucher(DiscountCodeModel voucher);
  Future<DiscountCodeModel> toggleVoucherActive(String voucherId, bool isActive);
  Future<List<DiscountRedemptionModel>> getRedemptionsForVoucher(String voucherId);
}

abstract class IVendorSettlementRepository {
  Future<List<SettlementModel>> getSettlements({SettlementStatus? status});
  Future<SettlementModel?> getSettlementById(String id);
  Future<double> getEligiblePayoutAmount({bool combined = false});
  Future<PayoutRequestModel> requestPayout({
    String? settlementId,
    required double amount,
  });
  Future<List<PayoutRequestModel>> getPayoutRequests();
}

abstract class IVendorDisputeRepository {
  Future<List<DisputeModel>> getDisputes({DisputeStatus? status});
  Future<DisputeModel?> getDisputeById(String id);
  Future<DisputeModel> submitDisputeEvidence(String disputeId, String attachmentUrl, {String? message});
}

abstract class IVendorRefundRepository {
  Future<List<RefundModel>> getRefunds();
  Future<RefundModel?> getRefundById(String id);
}

abstract class IVendorChatRepository {
  Future<List<ConversationModel>> getConversations();
  Future<List<MessageModel>> getMessages(String conversationId);
  Future<MessageModel> sendMessage(String conversationId, String content);
}

abstract class IVendorNotificationRepository {
  Future<List<NotificationModel>> getNotifications();
  Future<WeatherCacheModel> getWeatherInfo({double? lat, double? lng});
  Future<AdvanceSafetyCheckModel> checkAdvanceSafety({
    String? slotId,
    String? categoryId,
    String? categorySlug,
    double? latitude,
    double? longitude,
    DateTime? bookingDate,
    String? startTime,
    String? endTime,
  });
}

abstract class IVendorDashboardRepository {
  Future<VendorDashboardModel> getDashboardMetrics({DateTime? from, DateTime? to});
}

abstract class IVendorReportRepository {
  Future<Map<String, dynamic>> getRevenueReport({required DateTime from, required DateTime to, String groupBy = 'day'});
  Future<Map<String, dynamic>> getBookingReport({required DateTime from, required DateTime to, String groupBy = 'day'});
  Future<String> exportReportCsv({
    String type = 'revenue',
    required DateTime from,
    required DateTime to,
    String groupBy = 'day',
  });
}

abstract class IVendorEquipmentRepository {
  Future<List<VendorEquipmentModel>> listEquipment();
  Future<VendorEquipmentModel> createEquipment({
    required String name,
    required String compatibilityGroup,
    required int capacity,
  });
  Future<void> attachEquipmentToSlot({
    required String equipmentId,
    required String slotId,
    required int unitNumber,
  });
  Future<EquipmentMaintenanceResultModel> scheduleMaintenance({
    required String equipmentId,
    String? maintenanceId,
    required DateTime start,
    required DateTime end,
    bool urgent = false,
    required String reason,
  });
  Future<EquipmentMaintenanceResultModel> getMaintenance({
    required String equipmentId,
    required String maintenanceId,
  });
}

abstract class IVendorAiRepository {
  Future<CopilotMessageModel> chatWithCopilot({
    String? conversationId,
    required String message,
    String? contextType,
    String? contextId,
  });
  Future<List<Map<String, dynamic>>> listConversations();
  Future<List<CopilotMessageModel>> getConversationHistory(String conversationId);
  Future<SlotAdvisorResultModel> getSlotAdvisor({String? serviceId, int horizonDays = 7});
  Future<ReviewCoachingResultModel> getReviewCoaching({String? serviceId, int periodDays = 90});
  Future<ReplyDraftResultModel> draftReviewReply({
    required String reviewId,
    String tone = 'PROFESSIONAL',
    String? customPoints,
  });
  Future<ListingOptimizeResultModel> optimizeListing({
    String? serviceId,
    required String rawTitle,
    required String rawDescription,
    List<String> highlights = const [],
    List<String> includedItems = const [],
    List<String> targetLanguages = const ['vi', 'en'],
  });
}
