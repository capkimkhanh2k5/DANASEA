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
  });
}

abstract class IVendorServiceRepository {
  Future<List<ServiceModel>> getServices();
  Future<ServiceModel?> getServiceById(String id);
  Future<ServiceModel> createService(ServiceModel service);
  Future<ServiceModel> updateService(ServiceModel service);
  Future<ServiceModel> updateServiceStatus(String serviceId, ServiceStatus status);
  Future<List<ServiceSafetyDocumentModel>> getServiceSafetyDocuments(String serviceId);
  Future<ServiceSafetyDocumentModel> uploadServiceSafetyDocument({
    required String serviceId,
    required String fileName,
  });
}

abstract class IVendorSlotRepository {
  Future<List<ServiceSlotModel>> getSlotsForService(String serviceId, DateTime date);
  Future<ServiceSlotModel?> getSlotById(String slotId);
  Future<ServiceSlotModel> saveSlot(ServiceSlotModel slot);
  Future<ServiceSlotModel> updateSlotCapacity(String slotId, int newCapacity);
  Future<ServiceSlotModel> toggleBlockSlot(String slotId, bool blocked);
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
  Future<SubOrderModel?> getSubOrderById(String subOrderId);
  Future<SubOrderModel> confirmSubOrder(String subOrderId);
  Future<SubOrderModel> rejectSubOrder(String subOrderId);
  Future<SubOrderModel> checkInSubOrder(String subOrderId);
  Future<SubOrderModel> completeSubOrder(String subOrderId);
  Future<TicketLookupResult> lookupTicket(String ticketCode);
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
  Future<DisputeModel> submitDisputeEvidence(String disputeId, String attachmentUrl);
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
  Future<WeatherCacheModel> getWeatherInfo();
}

abstract class IVendorDashboardRepository {
  Future<VendorDashboardModel> getDashboardMetrics({DateTime? from, DateTime? to});
}
