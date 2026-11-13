import '../auth/auth_session.dart';
import 'vendor_repositories.dart';
// Centralized In-Memory Runtime Store for DANASEA Vendor App
// Implements ChangeNotifier so any updates to services, slots, sub-orders,
// reviews, vouchers, or settlements reactively update across all screens.

import 'package:flutter/foundation.dart';
import '../models/vendor_models.dart';

class VendorStore extends ChangeNotifier {
  static final VendorStore instance = VendorStore._internal();
  factory VendorStore() => instance;

  VendorStore._internal() {
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
    final now = DateTime.now();
    currentUser = UserModel(
      id: '',
      email: '',
      phone: '',
      fullName: '',
      role: UserRole.vendor,
      isEmailVerified: true,
      isLocked: false,
      locale: 'vi',
      createdAt: now,
    );

    currentVendor = VendorModel.empty();

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

    weatherCache = WeatherCacheModel(
      id: 'wth-001',
      locationKey: 'my-khe-da-nang',
      windSpeedKmh: 12.0,
      waveHeightM: 0.6,
      precipitationMm: 0.0,
      fetchedAt: now,
      expiresAt: now.add(const Duration(hours: 1)),
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

  void checkInBatchSubOrders(List<String> subOrderIds) {
    final now = DateTime.now();
    for (final id in subOrderIds) {
      final index = subOrders.indexWhere((o) => o.id == id || o.subOrderId == id);
      if (index != -1) {
        subOrders[index] = subOrders[index].copyWith(checkedInAt: now);
      }
    }
    notifyStoreChanged();
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
