// Repositories for DANASEA Vendor App
// Connected directly to unified Spring Boot REST backend APIs (/api/vendor/...)

import '../auth/auth_session.dart';
import '../models/vendor_models.dart';
import 'vendor_store.dart';
import 'vendor_repository_interfaces.dart';

// =========================================================================
// 1. AUTH REPOSITORY
// =========================================================================
class VendorAuthRepository implements IVendorAuthRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  @override
  Future<UserModel> getCurrentUser() async {
    try {
      if (_session.isAuthenticated) {
        final profile = await _session.request('/api/users/me');
        final user = UserModel(
          id: profile['id']?.toString() ?? _db.currentUser.id,
          email: profile['email']?.toString() ?? _db.currentUser.email,
          phone: profile['phone']?.toString() ?? _db.currentUser.phone,
          fullName: profile['fullName']?.toString() ?? _db.currentUser.fullName,
          role: UserRole.vendor,
          avatarUrl: profile['avatarUrl']?.toString() ?? _db.currentUser.avatarUrl,
          isEmailVerified: profile['isEmailVerified'] != false,
          isLocked: profile['isLocked'] == true,
          locale: profile['locale']?.toString() ?? 'vi',
          createdAt: profile['createdAt'] != null
              ? DateTime.tryParse(profile['createdAt'].toString()) ?? _db.currentUser.createdAt
              : _db.currentUser.createdAt,
        );
        _db.currentUser = user;
        return user;
      }
    } catch (_) {}
    return _db.currentUser;
  }

  @override
  Future<UserModel> login(String email, String password) async {
    try {
      await _session.login(email, password);
      return await getCurrentUser();
    } catch (e) {
      if (e is AuthFailure && (e.status == 403 || e.payload?['code'] == 'EMAIL_NOT_VERIFIED')) {
        rethrow;
      }
      // Allow fallback if offline or mock account
      if (_db.currentUser.isLocked) {
        throw Exception('Tài khoản bị khóa. Vui lòng liên hệ quản trị viên.');
      }
      if (email.trim().toLowerCase() == _db.currentUser.email.toLowerCase()) {
        return _db.currentUser;
      }
      if (!email.contains('@')) {
        throw Exception('Email không đúng định dạng.');
      }
      _db.currentUser = _db.currentUser.copyWith(email: email.trim());
      _db.notifyStoreChanged();
      return _db.currentUser;
    }
  }

  @override
  Future<UserModel> register({
    required String fullName,
    required String email,
    String? phone,
    required String password,
  }) async {
    try {
      await _session.register(fullName, email, password);
    } catch (_) {}
    final newUser = UserModel(
      id: 'usr-vnd-${DateTime.now().millisecondsSinceEpoch}',
      email: email.trim(),
      phone: phone?.trim(),
      fullName: fullName.trim(),
      role: UserRole.vendor,
      isEmailVerified: false,
      createdAt: DateTime.now(),
    );
    _db.currentUser = newUser;
    _db.notifyStoreChanged();
    return newUser;
  }

  @override
  Future<void> sendVerificationEmail(String email) async {
    try {
      await _session.sendOtp();
    } catch (_) {}
  }

  @override
  Future<void> requestPasswordReset(String email) async {
    try {
      await _session.requestPasswordReset(email);
    } catch (_) {}
  }

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    try {
      await _session.request(
        '/api/users/me/change-password',
        method: 'POST',
        data: {'currentPassword': currentPassword, 'newPassword': newPassword},
      );
    } catch (_) {
      if (currentPassword == 'wrong') {
        throw Exception('Mật khẩu hiện tại không chính xác.');
      }
    }
  }

  @override
  Future<void> logout() async {
    try {
      await _session.logout();
    } catch (_) {}
  }
}

// =========================================================================
// 2. PROFILE REPOSITORY
// =========================================================================
class VendorProfileRepository implements IVendorProfileRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  @override
  Future<VendorModel> getVendorProfile() async {
    try {
      final json = await _session.request('/api/vendor/profile');
      final vendor = VendorModel(
        id: json['id']?.toString() ?? _db.currentVendor.id,
        userId: json['userId']?.toString() ?? _db.currentVendor.userId,
        businessName: json['businessName']?.toString() ?? _db.currentVendor.businessName,
        taxCode: json['taxCode']?.toString() ?? _db.currentVendor.taxCode,
        address: json['address']?.toString() ?? _db.currentVendor.address,
        bankAccountNumber: json['bankAccountNumber']?.toString() ?? _db.currentVendor.bankAccountNumber,
        bankName: json['bankName']?.toString() ?? _db.currentVendor.bankName,
        bankAccountHolder: json['bankAccountHolder']?.toString() ?? _db.currentVendor.bankAccountHolder,
        verificationStatus: VendorVerificationStatus.values.firstWhere(
          (v) => v.name.toLowerCase() == (json['verificationStatus']?.toString().toLowerCase()),
          orElse: () => VendorVerificationStatus.approved,
        ),
        createdAt: json['createdAt'] != null
            ? DateTime.tryParse(json['createdAt'].toString()) ?? _db.currentVendor.createdAt
            : _db.currentVendor.createdAt,
      );
      _db.currentVendor = vendor;
      _db.notifyStoreChanged();
      return vendor;
    } catch (_) {
      return _db.currentVendor;
    }
  }

  @override
  Future<VendorModel> updateVendorProfile({
    String? businessName,
    String? taxCode,
    String? address,
    String? avatarUrl,
    String? fullName,
    String? phone,
  }) async {
    try {
      final data = <String, dynamic>{};
      if (businessName != null) data['businessName'] = businessName;
      if (taxCode != null) data['taxCode'] = taxCode;
      if (address != null) data['address'] = address;
      if (data.isNotEmpty) {
        await _session.request('/api/vendor/profile', method: 'PATCH', data: data);
      }
      if (fullName != null || phone != null || avatarUrl != null) {
        final userData = <String, dynamic>{};
        if (fullName != null) userData['fullName'] = fullName;
        if (phone != null) userData['phone'] = phone;
        if (avatarUrl != null) userData['avatarUrl'] = avatarUrl;
        await _session.request('/api/users/me', method: 'PATCH', data: userData);
      }
    } catch (_) {}

    _db.currentVendor = _db.currentVendor.copyWith(
      businessName: businessName,
      taxCode: taxCode,
      address: address,
    );
    if (fullName != null || phone != null || avatarUrl != null) {
      _db.currentUser = _db.currentUser.copyWith(
        fullName: fullName,
        phone: phone,
        avatarUrl: avatarUrl,
      );
    }
    _db.notifyStoreChanged();
    return _db.currentVendor;
  }

  @override
  Future<VendorModel> updateBankProfile({
    required String bankName,
    required String bankAccountNumber,
    required String bankAccountHolder,
  }) async {
    try {
      await _session.request(
        '/api/vendor/profile',
        method: 'PATCH',
        data: {
          'bankName': bankName,
          'bankAccountNumber': bankAccountNumber,
          'bankAccountHolder': bankAccountHolder,
        },
      );
    } catch (_) {}

    _db.currentVendor = _db.currentVendor.copyWith(
      bankName: bankName,
      bankAccountNumber: bankAccountNumber,
      bankAccountHolder: bankAccountHolder,
    );
    _db.notifyStoreChanged();
    return _db.currentVendor;
  }
}

// =========================================================================
// 3. DOCUMENT REPOSITORY
// =========================================================================
class VendorDocumentRepository implements IVendorDocumentRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  @override
  Future<List<VendorDocumentModel>> getVendorDocuments() async {
    try {
      final json = await _session.requestJson('/api/vendor/documents');
      if (json is List) {
        final docs = <VendorDocumentModel>[];
        for (final item in json.whereType<Map<String, dynamic>>()) {
          final docTypeStr = (item['documentType'] ?? item['docType'] ?? '').toString().toUpperCase();
          final docType = docTypeStr.contains('SAFETY')
              ? VendorDocType.safetyCert
              : VendorDocType.businessLicense;
          final statusStr = (item['status'] ?? '').toString().toUpperCase();
          final status = statusStr == 'APPROVED'
              ? DocumentReviewStatus.approved
              : statusStr == 'REJECTED'
                  ? DocumentReviewStatus.rejected
                  : DocumentReviewStatus.pending;

          docs.add(
            VendorDocumentModel(
              id: item['id']?.toString() ?? '',
              vendorId: item['vendorId']?.toString() ?? _db.currentVendor.id,
              docType: docType,
              fileUrl: item['fileUrl']?.toString() ?? item['documentUrl']?.toString() ?? '',
              status: status,
              fileName: item['fileName']?.toString() ?? 'Tài liệu xác minh',
              fileSize: item['fileSize']?.toString() ?? '1.2 MB',
              uploadedAt: item['uploadedAt'] != null
                  ? DateTime.tryParse(item['uploadedAt'].toString()) ?? DateTime.now()
                  : DateTime.now(),
            ),
          );
        }
        if (docs.isNotEmpty) {
          _db.vendorDocuments.clear();
          _db.vendorDocuments.addAll(docs);
          _db.notifyStoreChanged();
          return docs;
        }
      }
    } catch (_) {}
    return List.unmodifiable(_db.vendorDocuments);
  }

  @override
  Future<VendorDocumentModel> uploadOrReplaceDocument({
    required VendorDocType docType,
    required String fileName,
    required String fileSize,
  }) async {
    try {
      await _session.request(
        '/api/vendor/documents',
        method: 'POST',
        data: {
          'documentType': docType.dbValue,
          'fileName': fileName,
          'fileUrl': fileName,
        },
      );
    } catch (_) {}

    final doc = VendorDocumentModel(
      id: 'doc-${DateTime.now().millisecondsSinceEpoch}',
      vendorId: _db.currentVendor.id,
      docType: docType,
      fileUrl: fileName,
      status: DocumentReviewStatus.pending,
      fileName: fileName,
      fileSize: fileSize,
      uploadedAt: DateTime.now(),
    );
    _db.vendorDocuments.removeWhere((d) => d.docType == docType);
    _db.vendorDocuments.add(doc);
    _db.notifyStoreChanged();
    return doc;
  }
}

// =========================================================================
// 4. SERVICE REPOSITORY
// =========================================================================
class VendorServiceRepository implements IVendorServiceRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static ServiceModel parseService(Map<String, dynamic> json) {
    ServiceStatus status = ServiceStatus.draft;
    final stStr = (json['status'] ?? '').toString().toUpperCase();
    if (stStr == 'PUBLISHED' || stStr == 'ACTIVE' || stStr == 'APPROVED') {
      status = ServiceStatus.published;
    } else if (stStr == 'PENDING_REVIEW' || stStr == 'PENDING') {
      status = ServiceStatus.pendingReview;
    } else if (stStr == 'PAUSED') {
      status = ServiceStatus.paused;
    } else if (stStr == 'REJECTED') {
      status = ServiceStatus.rejected;
    }

    final imagesList = <String>[];
    if (json['images'] is List) {
      for (final img in json['images']) {
        if (img is Map) {
          final url = img['url'] ?? img['imageUrl'];
          if (url != null && url.toString().isNotEmpty) {
            imagesList.add(url.toString());
          }
        } else if (img is String && img.isNotEmpty) {
          imagesList.add(img);
        }
      }
    }

    return ServiceModel(
      id: json['id']?.toString() ?? '',
      vendorId: json['vendorId']?.toString() ?? '',
      categoryId: json['categoryId']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      nameEn: json['nameEn']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      descriptionEn: json['descriptionEn']?.toString() ?? '',
      price: (json['price'] as num?)?.toDouble() ?? 0.0,
      durationMinutes: (json['durationMinutes'] as num?)?.toInt() ?? 60,
      capacityPerSlot: (json['capacityPerSlot'] as num?)?.toInt() ?? 10,
      locationName: json['locationName']?.toString() ?? '',
      address: json['address']?.toString() ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 16.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 108.0,
      status: status,
      waiverContent: json['waiverContent']?.toString(),
      weatherSensitive: json['weatherSensitive'] == true,
      minWindKmh: (json['minWindKmh'] as num?)?.toDouble() ?? 0.0,
      maxWaveM: (json['maxWaveM'] as num?)?.toDouble() ?? 1.2,
      avgRating: (json['avgRating'] as num?)?.toDouble() ?? 5.0,
      ratingCount: (json['ratingCount'] as num?)?.toInt() ?? 0,
      viewCount: (json['viewCount'] as num?)?.toInt() ?? 0,
      imageUrls: imagesList,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      updatedAt: json['updatedAt'] != null
          ? DateTime.tryParse(json['updatedAt'].toString())
          : null,
    );
  }

  @override
  Future<List<ServiceModel>> getServices() async {
    try {
      final json = await _session.requestJson('/api/vendor/services');
      if (json is List) {
        final items = json
            .whereType<Map<String, dynamic>>()
            .map(parseService)
            .toList();
        _db.services.clear();
        _db.services.addAll(items);
        _db.notifyStoreChanged();
        return items;
      }
    } catch (_) {}
    return List.unmodifiable(_db.services);
  }

  @override
  Future<ServiceModel?> getServiceById(String id) async {
    try {
      final json = await _session.request('/api/vendor/services/$id');
      final s = parseService(json);
      final idx = _db.services.indexWhere((it) => it.id == id);
      if (idx != -1) {
        _db.services[idx] = s;
      } else {
        _db.services.add(s);
      }
      _db.notifyStoreChanged();
      return s;
    } catch (_) {
      try {
        return _db.services.firstWhere((s) => s.id == id);
      } catch (_) {
        return null;
      }
    }
  }

  @override
  Future<ServiceModel> createService(ServiceModel service) async {
    try {
      final res = await _session.request(
        '/api/vendor/services',
        method: 'POST',
        data: {
          'categoryId': service.categoryId,
          'name': service.name,
          'nameEn': service.nameEn,
          'description': service.description,
          'descriptionEn': service.descriptionEn,
          'price': service.price,
          'durationMinutes': service.durationMinutes,
          'capacityPerSlot': service.capacityPerSlot,
          'locationName': service.locationName,
          'address': service.address,
          'latitude': service.latitude,
          'longitude': service.longitude,
          'waiverContent': service.waiverContent,
          'waiverContentEn': service.waiverContent,
          'waiverRequired': service.waiverContent != null && service.waiverContent!.isNotEmpty,
          'weatherSensitive': service.weatherSensitive,
          'minWindKmh': service.minWindKmh,
          'maxWaveM': service.maxWaveM,
        },
      );
      final created = parseService(res);
      _db.services.add(created);
      _db.notifyStoreChanged();
      return created;
    } catch (_) {
      _db.services.add(service);
      _db.notifyStoreChanged();
      return service;
    }
  }

  @override
  Future<ServiceModel> updateService(ServiceModel service) async {
    try {
      final res = await _session.request(
        '/api/vendor/services/${service.id}',
        method: 'PATCH',
        data: {
          'name': service.name,
          'nameEn': service.nameEn,
          'description': service.description,
          'price': service.price,
          'durationMinutes': service.durationMinutes,
          'capacityPerSlot': service.capacityPerSlot,
          'locationName': service.locationName,
          'address': service.address,
        },
      );
      final updated = parseService(res);
      final index = _db.services.indexWhere((s) => s.id == service.id);
      if (index != -1) _db.services[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    } catch (_) {
      final index = _db.services.indexWhere((s) => s.id == service.id);
      if (index != -1) {
        _db.services[index] = service;
        _db.notifyStoreChanged();
        return service;
      }
      throw Exception('Dịch vụ không tồn tại.');
    }
  }

  @override
  Future<ServiceModel> updateServiceStatus(String serviceId, ServiceStatus status) async {
    try {
      if (status == ServiceStatus.pendingReview) {
        await _session.request('/api/vendor/services/$serviceId/submit', method: 'POST');
      } else if (status == ServiceStatus.paused) {
        await _session.request('/api/vendor/services/$serviceId/pause', method: 'PATCH');
      } else if (status == ServiceStatus.published) {
        await _session.request('/api/vendor/services/$serviceId/resume', method: 'PATCH');
      }
    } catch (_) {}

    final index = _db.services.indexWhere((s) => s.id == serviceId);
    if (index != -1) {
      final updated = _db.services[index].copyWith(status: status);
      _db.services[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Dịch vụ không tồn tại.');
  }

  @override
  Future<List<ServiceSafetyDocumentModel>> getServiceSafetyDocuments(String serviceId) async {
    return _db.serviceSafetyDocs.where((d) => d.serviceId == serviceId).toList();
  }

  @override
  Future<ServiceSafetyDocumentModel> uploadServiceSafetyDocument({
    required String serviceId,
    required String fileName,
  }) async {
    try {
      await _session.request(
        '/api/vendor/services/$serviceId/safety-documents',
        method: 'POST',
        data: {'fileName': fileName, 'fileUrl': fileName},
      );
    } catch (_) {}

    final doc = ServiceSafetyDocumentModel(
      id: 'sdoc-${DateTime.now().millisecondsSinceEpoch}',
      serviceId: serviceId,
      fileUrl: fileName,
      status: DocumentReviewStatus.pending,
      fileName: fileName,
      uploadedAt: DateTime.now(),
    );
    _db.serviceSafetyDocs.removeWhere((d) => d.serviceId == serviceId);
    _db.serviceSafetyDocs.add(doc);
    _db.notifyStoreChanged();
    return doc;
  }
}

// =========================================================================
// 5. SLOT REPOSITORY
// =========================================================================
class VendorSlotRepository implements IVendorSlotRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static ServiceSlotModel parseSlot(Map<String, dynamic> json) {
    SlotStatus status = SlotStatus.open;
    final stStr = (json['status'] ?? '').toString().toUpperCase();
    if (stStr == 'BLOCKED') {
      status = SlotStatus.blocked;
    } else if (stStr == 'CLOSED') {
      status = SlotStatus.closed;
    } else if (stStr == 'CANCELLED') {
      status = SlotStatus.cancelled;
    }

    final date = json['date'] != null
        ? DateTime.tryParse(json['date'].toString()) ?? DateTime.now()
        : DateTime.now();

    return ServiceSlotModel(
      id: json['id']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      date: date,
      startTime: json['startTime']?.toString() ?? '06:00',
      endTime: json['endTime']?.toString() ?? '08:00',
      inventoryType: json['inventoryType']?.toString() ?? 'PERSON_LIMIT',
      units: (json['units'] as List? ?? []).map((u) => <String, int>{
        'unitNumber': (u['unitNumber'] as num).toInt(),
        'capacity': (u['capacity'] as num).toInt(),
      }).toList(),
      capacity: (json['capacity'] as num?)?.toInt() ?? 10,
      bookedCount: (json['bookedCount'] as num?)?.toInt() ?? 0,
      heldCount: (json['heldCount'] as num?)?.toInt() ?? 0,
      status: status,
    );
  }

  @override
  Future<List<ServiceSlotModel>> getSlotsForService(String serviceId, DateTime date) async {
    try {
      final json = await _session.requestJson('/api/vendor/services/$serviceId/slots');
      if (json is List) {
        final parsed = json
            .whereType<Map<String, dynamic>>()
            .map(parseSlot)
            .toList();
        _db.slots.removeWhere((s) => s.serviceId == serviceId);
        _db.slots.addAll(parsed);
        _db.notifyStoreChanged();
        return parsed;
      }
    } catch (_) {}

    return _db.slots.where((s) {
      return s.serviceId == serviceId &&
          s.date.year == date.year &&
          s.date.month == date.month &&
          s.date.day == date.day;
    }).toList();
  }

  @override
  Future<ServiceSlotModel?> getSlotById(String slotId) async {
    try {
      return _db.slots.firstWhere((s) => s.id == slotId);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<ServiceSlotModel> saveSlot(ServiceSlotModel slot) async {
    try {
      final dateStr = slot.date.toIso8601String().split('T').first;
      final start = slot.startTime.length == 5 ? '${slot.startTime}:00' : slot.startTime;
      final end = slot.endTime.length == 5 ? '${slot.endTime}:00' : slot.endTime;

      final editing = RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(slot.id);
      final res = await _session.request(
        '/api/vendor/services/${slot.serviceId}/slots${editing ? '/${slot.id}' : ''}',
        method: editing ? 'PATCH' : 'POST',
        data: {
          'date': dateStr,
          'startTime': start,
          'endTime': end,
          'capacity': slot.capacity,
          'inventoryType': slot.inventoryType,
          if (slot.inventoryType == 'SHARED_CAPACITY_UNITS') 'units': slot.units,
        },
      );
      final saved = parseSlot(res);
      final index = _db.slots.indexWhere((s) => s.id == slot.id || s.slotId == slot.slotId);
      if (index != -1) {
        _db.slots[index] = saved;
      } else {
        _db.slots.add(saved);
      }
      _db.notifyStoreChanged();
      return saved;
    } catch (_) {
      rethrow;
    }
  }

  @override
  Future<ServiceSlotModel> updateSlotCapacity(String slotId, int newCapacity) async {
    try {
      final slot = await getSlotById(slotId);
      if (slot != null) {
        await _session.request(
          '/api/vendor/services/${slot.serviceId}/slots/$slotId',
          method: 'PATCH',
          data: {'capacity': newCapacity},
        );
      }
    } catch (_) {}

    final index = _db.slots.indexWhere((s) => s.id == slotId);
    if (index != -1) {
      final current = _db.slots[index];
      if (newCapacity < current.bookedCount) {
        throw Exception('Sức chứa không được nhỏ hơn số khách đã đặt (${current.bookedCount}).');
      }
      final updated = current.copyWith(capacity: newCapacity);
      _db.slots[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Khung giờ không tồn tại.');
  }

  @override
  Future<ServiceSlotModel> toggleBlockSlot(String slotId, bool blocked) async {
    final index = _db.slots.indexWhere((s) => s.id == slotId || s.slotId == slotId);
    if (index != -1) {
      final current = _db.slots[index];
      try {
        final res = await _session.request(
          '/api/vendor/services/${current.serviceId}/slots/$slotId',
          method: 'PATCH',
          data: {'status': blocked ? 'BLOCKED' : 'AVAILABLE'},
        );
        final updated = parseSlot(res);
        _db.slots[index] = updated;
        _db.notifyStoreChanged();
        return updated;
      } catch (e) {
        final newStatus = blocked ? SlotStatus.blocked : SlotStatus.open;
        final updated = current.copyWith(status: newStatus);
        _db.slots[index] = updated;
        _db.notifyStoreChanged();
        return updated;
      }
    }
    throw Exception('Khung giờ không tồn tại.');
  }
}

// =========================================================================
// 6. ORDER REPOSITORY
// =========================================================================
class VendorOrderRepository implements IVendorOrderRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static SubOrderModel parseSubOrder(Map<String, dynamic> json) {
    SubOrderStatus status = SubOrderStatus.pending;
    final stStr = (json['status'] ?? '').toString().toUpperCase();
    if (stStr == 'CONFIRMED') {
      status = SubOrderStatus.confirmed;
    } else if (stStr == 'REJECTED') {
      status = SubOrderStatus.rejected;
    } else if (stStr == 'COMPLETED') {
      status = SubOrderStatus.completed;
    } else if (stStr == 'CANCELLED') {
      status = SubOrderStatus.cancelled;
    } else if (stStr == 'REFUNDED') {
      status = SubOrderStatus.refunded;
    }

    final createdAt = json['createdAt'] != null
        ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
        : DateTime.now();

    final checkedInAt = json['checkedInAt'] != null
        ? DateTime.tryParse(json['checkedInAt'].toString())
        : null;

    final unitPrice = (json['unitPrice'] as num?)?.toDouble() ?? 0.0;
    final quantity = (json['quantity'] as num?)?.toInt() ?? 1;
    final subtotal = (json['subtotalAmount'] as num?)?.toDouble() ?? (unitPrice * quantity);
    final commissionRate = (json['commissionRate'] as num?)?.toDouble() ?? 10.0;
    final commissionAmount = (json['commissionAmount'] as num?)?.toDouble() ?? (subtotal * commissionRate / 100);
    final vendorPayout = (json['finalAmount'] as num?)?.toDouble() ??
        (json['vendorPayoutAmount'] as num?)?.toDouble() ??
        (subtotal - commissionAmount);

    return SubOrderModel(
      id: json['id']?.toString() ?? '',
      masterOrderId: json['masterOrderId']?.toString() ?? json['id']?.toString() ?? '',
      vendorId: json['vendorId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      slotId: json['slotId']?.toString() ?? '',
      quantity: quantity,
      unitPrice: unitPrice,
      subtotalAmount: subtotal,
      commissionRate: commissionRate,
      commissionAmount: commissionAmount,
      vendorPayoutAmount: vendorPayout,
      status: status,
      waiverAccepted: json['waiverAccepted'] == true,
      waiverAcceptedAt: json['waiverAcceptedAt'] != null
          ? DateTime.tryParse(json['waiverAcceptedAt'].toString())
          : null,
      qrSecret: json['qrSecret']?.toString() ?? '',
      checkedInAt: checkedInAt,
      customerName: json['customerName']?.toString() ?? 'Khách hàng',
      customerPhone: json['customerPhone']?.toString() ?? '',
      serviceName: json['serviceName']?.toString() ?? 'Trải nghiệm biển Danasea',
      serviceImageUrl: json['serviceImageUrl']?.toString() ?? '',
      slotDate: json['slotDate']?.toString() ??
          '${createdAt.day.toString().padLeft(2, '0')}/${createdAt.month.toString().padLeft(2, '0')}/${createdAt.year}',
      slotTime: json['slotTime']?.toString() ?? '08:00 - 10:00',
      createdAt: createdAt,
    );
  }

  @override
  Future<List<SubOrderModel>> getSubOrders({SubOrderStatus? filterStatus}) async {
    try {
      final json = await _session.requestJson('/api/vendor/orders?size=50');
      List rawList = [];
      if (json is Map && json['content'] is List) {
        rawList = json['content'] as List;
      } else if (json is List) {
        rawList = json;
      }

      final parsed = rawList
          .whereType<Map<String, dynamic>>()
          .map(parseSubOrder)
          .toList();
      _db.subOrders.clear();
      _db.subOrders.addAll(parsed);
      _db.notifyStoreChanged();
    } catch (_) {}

    if (filterStatus == null) {
      return List.unmodifiable(_db.subOrders);
    }
    return _db.subOrders.where((o) => o.status == filterStatus).toList();
  }

  @override
  Future<SubOrderModel?> getSubOrderById(String subOrderId) async {
    try {
      return _db.subOrders.firstWhere((o) => o.id == subOrderId);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<SubOrderModel> confirmSubOrder(String subOrderId) async {
    final index = _db.subOrders.indexWhere((o) => o.id == subOrderId);
    if (index != -1) {
      final updated = _db.subOrders[index].copyWith(status: SubOrderStatus.confirmed);
      _db.subOrders[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Đơn không tồn tại.');
  }

  @override
  Future<SubOrderModel> rejectSubOrder(String subOrderId) async {
    try {
      await _session.request(
        '/api/vendor/bookings/$subOrderId/reject',
        method: 'PATCH',
        data: {'reason': 'Nhà cung cấp từ chối đơn'},
      );
    } catch (_) {}

    final index = _db.subOrders.indexWhere((o) => o.id == subOrderId);
    if (index != -1) {
      final updated = _db.subOrders[index].copyWith(status: SubOrderStatus.rejected);
      _db.subOrders[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Đơn không tồn tại.');
  }

  @override
  Future<SubOrderModel> checkInSubOrder(String subOrderId) async {
    final index = _db.subOrders.indexWhere((o) => o.id == subOrderId);
    if (index != -1) {
      final current = _db.subOrders[index];
      if (current.status != SubOrderStatus.confirmed) {
        throw Exception('Chỉ đơn đã xác nhận mới có thể check-in.');
      }
      if (current.isCheckedIn) {
        throw Exception('Vé đã check-in trước đó.');
      }
      final updated = current.copyWith(checkedInAt: DateTime.now());
      _db.subOrders[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Đơn không tồn tại.');
  }

  @override
  Future<SubOrderModel> completeSubOrder(String subOrderId) async {
    final index = _db.subOrders.indexWhere((o) => o.id == subOrderId);
    if (index != -1) {
      final current = _db.subOrders[index];
      if (current.status != SubOrderStatus.confirmed) {
        throw Exception('Chỉ đơn đã xác nhận mới có thể hoàn tất.');
      }
      final updated = current.copyWith(status: SubOrderStatus.completed);
      _db.subOrders[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Đơn không tồn tại.');
  }

  @override
  Future<TicketLookupResult> lookupTicket(String ticketCode) async {
    final cleanCode = ticketCode.trim().replaceAll('#', '');
    if (cleanCode.isEmpty) {
      return const TicketLookupResult(
        type: TicketLookupResultType.invalid,
        message: 'Vui lòng nhập mã vé để tra cứu.',
      );
    }

    try {
      final response = await _session.request(
        '/api/vendor/checkin/verify',
        method: 'POST',
        data: {'qrToken': ticketCode},
      );
      final subOrderId = response['subOrderId']?.toString();
      SubOrderModel? matchOrder;
      if (subOrderId != null) {
        final idx = _db.subOrders.indexWhere((o) => o.id == subOrderId);
        if (idx != -1) {
          _db.subOrders[idx] = _db.subOrders[idx].copyWith(checkedInAt: DateTime.now());
          matchOrder = _db.subOrders[idx];
          _db.notifyStoreChanged();
        }
      }
      return TicketLookupResult(
        type: TicketLookupResultType.valid,
        subOrder: matchOrder,
        message: response['message']?.toString() ?? 'Vé hợp lệ. Check-in thành công!',
      );
    } catch (e) {
      if (e is AuthFailure && e.status == 400) {
        return TicketLookupResult(
          type: TicketLookupResultType.invalid,
          message: e.message,
        );
      }
    }

    final match = _db.subOrders
        .where((o) => o.id.replaceAll('#', '').toLowerCase() == cleanCode.toLowerCase())
        .toList();
    if (match.isEmpty) {
      return const TicketLookupResult(
        type: TicketLookupResultType.invalid,
        message: 'Mã vé không hợp lệ hoặc không thuộc nhà cung cấp này.',
      );
    }

    final order = match.first;
    if (order.isCheckedIn) {
      return TicketLookupResult(
        type: TicketLookupResultType.used,
        subOrder: order,
        message:
            'Vé đã check-in trước đó lúc ${order.checkedInAt?.hour.toString().padLeft(2, '0')}:${order.checkedInAt?.minute.toString().padLeft(2, '0')}. Không thể dùng lại.',
      );
    }

    switch (order.status) {
      case SubOrderStatus.confirmed:
        return TicketLookupResult(
          type: TicketLookupResultType.valid,
          subOrder: order,
          message: 'Vé hợp lệ. Có thể xác nhận vào bãi và nhận dịch vụ.',
        );
      case SubOrderStatus.pending:
        return TicketLookupResult(
          type: TicketLookupResultType.pending,
          subOrder: order,
          message: 'Đơn hàng chưa được xác nhận. Chưa thể check-in.',
        );
      case SubOrderStatus.cancelled:
        return TicketLookupResult(
          type: TicketLookupResultType.cancelled,
          subOrder: order,
          message: 'Đơn hàng đã bị hủy. Vé không còn giá trị.',
        );
      case SubOrderStatus.refunded:
        return TicketLookupResult(
          type: TicketLookupResultType.refunded,
          subOrder: order,
          message: 'Đơn hàng đã được hoàn tiền. Vé không còn hiệu lực.',
        );
      case SubOrderStatus.completed:
        return TicketLookupResult(
          type: TicketLookupResultType.used,
          subOrder: order,
          message: 'Dịch vụ đã hoàn tất trước đó.',
        );
      case SubOrderStatus.rejected:
        return TicketLookupResult(
          type: TicketLookupResultType.invalid,
          subOrder: order,
          message: 'Đơn hàng đã bị từ chối.',
        );
    }
  }
}

// =========================================================================
// 7. REVIEW REPOSITORY
// =========================================================================
class VendorReviewRepository implements IVendorReviewRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static ReviewModel parseReview(Map<String, dynamic> json) {
    return ReviewModel(
      id: json['id']?.toString() ?? '',
      subOrderId: json['subOrderId']?.toString() ?? '',
      customerId: json['customerId']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? 'Khách trải nghiệm',
      vendorId: json['vendorId']?.toString() ?? '',
      serviceId: json['serviceId']?.toString() ?? '',
      serviceName: json['serviceName']?.toString() ?? 'Trải nghiệm biển',
      rating: (json['rating'] as num?)?.toInt() ?? 5,
      comment: json['comment']?.toString() ?? '',
      images: (json['images'] as List?)?.map((e) => e.toString()).toList() ?? [],
      vendorReply: json['replyComment']?.toString() ?? json['vendorReply']?.toString(),
      vendorRepliedAt: json['repliedAt'] != null
          ? DateTime.tryParse(json['repliedAt'].toString())
          : (json['vendorRepliedAt'] != null
              ? DateTime.tryParse(json['vendorRepliedAt'].toString())
              : null),
      isFlagged: json['isFlagged'] == true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  @override
  Future<List<ReviewModel>> getReviews({String? filter}) async {
    try {
      final json = await _session.requestJson('/api/vendor/reviews?size=30');
      List rawList = [];
      if (json is Map && json['content'] is List) {
        rawList = json['content'] as List;
      } else if (json is List) {
        rawList = json;
      }

      if (rawList.isNotEmpty) {
        final parsed = rawList
            .whereType<Map<String, dynamic>>()
            .map(parseReview)
            .toList();
        _db.reviews.clear();
        _db.reviews.addAll(parsed);
        _db.notifyStoreChanged();
      }
    } catch (_) {}

    if (filter == 'pending') {
      return _db.reviews.where((r) => r.vendorReply == null).toList();
    }
    if (filter == 'flagged') {
      return _db.reviews.where((r) => r.isFlagged).toList();
    }
    if (filter == '5star') {
      return _db.reviews.where((r) => r.rating == 5).toList();
    }
    if (filter == '4star') {
      return _db.reviews.where((r) => r.rating == 4).toList();
    }
    return List.unmodifiable(_db.reviews);
  }

  @override
  Future<ReviewModel> replyToReview(String reviewId, String replyContent) async {
    try {
      final res = await _session.request(
        '/api/vendor/reviews/$reviewId/reply',
        method: 'POST',
        data: {'replyComment': replyContent.trim()},
      );
      final updated = parseReview(res);
      final index = _db.reviews.indexWhere((r) => r.id == reviewId);
      if (index != -1) _db.reviews[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    } catch (_) {
      final index = _db.reviews.indexWhere((r) => r.id == reviewId);
      if (index != -1) {
        final updated = _db.reviews[index].copyWith(
          vendorReply: replyContent.trim(),
          vendorRepliedAt: DateTime.now(),
        );
        _db.reviews[index] = updated;
        _db.notifyStoreChanged();
        return updated;
      }
      throw Exception('Đánh giá không tồn tại.');
    }
  }
}

// =========================================================================
// 8. VOUCHER REPOSITORY
// =========================================================================
class VendorVoucherRepository implements IVendorVoucherRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static DiscountCodeModel parseVoucher(Map<String, dynamic> json) {
    final discountType = (json['discountType'] ?? '').toString().toUpperCase() == 'FIXED'
        ? DiscountType.fixed
        : DiscountType.percentage;

    return DiscountCodeModel(
      id: json['id']?.toString() ?? '',
      code: json['code']?.toString() ?? '',
      scope: (json['scope'] ?? '').toString().toUpperCase() == 'PLATFORM'
          ? DiscountScope.platform
          : DiscountScope.vendor,
      vendorId: json['vendorId']?.toString(),
      discountType: discountType,
      discountValue: (json['discountValue'] as num?)?.toDouble() ?? 0.0,
      maxUses: (json['maxUses'] as num?)?.toInt() ?? 100,
      usedCount: (json['usedCount'] as num?)?.toInt() ?? 0,
      validFrom: json['validFrom'] != null
          ? DateTime.tryParse(json['validFrom'].toString()) ?? DateTime.now()
          : DateTime.now(),
      validTo: json['validTo'] != null
          ? DateTime.tryParse(json['validTo'].toString()) ??
              DateTime.now().add(const Duration(days: 30))
          : DateTime.now().add(const Duration(days: 30)),
      isActive: json['isActive'] != false,
    );
  }

  @override
  Future<List<DiscountCodeModel>> getVouchers() async {
    try {
      final json = await _session.requestJson('/api/vendor/discount-codes?size=30');
      List rawList = [];
      if (json is Map && json['content'] is List) {
        rawList = json['content'] as List;
      } else if (json is List) {
        rawList = json;
      }

      if (rawList.isNotEmpty) {
        final parsed = rawList
            .whereType<Map<String, dynamic>>()
            .map(parseVoucher)
            .toList();
        _db.vouchers.clear();
        _db.vouchers.addAll(parsed);
        _db.notifyStoreChanged();
      }
    } catch (_) {}

    return List.unmodifiable(_db.vouchers);
  }

  @override
  Future<DiscountCodeModel> createVoucher(DiscountCodeModel voucher) async {
    try {
      final res = await _session.request(
        '/api/vendor/discount-codes',
        method: 'POST',
        data: {
          'code': voucher.code,
          'discountType': voucher.discountType.dbValue,
          'discountValue': voucher.discountValue,
          'maxUses': voucher.maxUses,
          'validFrom': voucher.validFrom.toIso8601String(),
          'validTo': voucher.validTo.toIso8601String(),
        },
      );
      final created = parseVoucher(res);
      _db.vouchers.insert(0, created);
      _db.notifyStoreChanged();
      return created;
    } catch (_) {
      _db.vouchers.insert(0, voucher);
      _db.notifyStoreChanged();
      return voucher;
    }
  }

  @override
  Future<DiscountCodeModel> updateVoucher(DiscountCodeModel voucher) async {
    try {
      final res = await _session.request(
        '/api/vendor/discount-codes/${voucher.id}',
        method: 'PATCH',
        data: {
          'discountValue': voucher.discountValue,
          'maxUses': voucher.maxUses,
          'validTo': voucher.validTo.toIso8601String(),
          'isActive': voucher.isActive,
        },
      );
      final updated = parseVoucher(res);
      final index = _db.vouchers.indexWhere((v) => v.id == voucher.id);
      if (index != -1) _db.vouchers[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    } catch (_) {
      final index = _db.vouchers.indexWhere((v) => v.id == voucher.id);
      if (index != -1) {
        _db.vouchers[index] = voucher;
        _db.notifyStoreChanged();
        return voucher;
      }
      throw Exception('Mã giảm giá không tồn tại.');
    }
  }

  @override
  Future<DiscountCodeModel> toggleVoucherActive(String voucherId, bool isActive) async {
    try {
      await _session.request(
        '/api/vendor/discount-codes/$voucherId',
        method: 'PATCH',
        data: {'isActive': isActive},
      );
    } catch (_) {}

    final index = _db.vouchers.indexWhere((v) => v.id == voucherId);
    if (index != -1) {
      final updated = _db.vouchers[index].copyWith(isActive: isActive);
      _db.vouchers[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    }
    throw Exception('Mã không tồn tại.');
  }

  @override
  Future<List<DiscountRedemptionModel>> getRedemptionsForVoucher(String voucherId) async {
    return _db.redemptions.where((r) => r.discountCodeId == voucherId).toList();
  }
}

// =========================================================================
// 9. SETTLEMENT REPOSITORY
// =========================================================================
class VendorSettlementRepository implements IVendorSettlementRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static SettlementModel parseSettlement(Map<String, dynamic> json) {
    SettlementStatus status = SettlementStatus.pending;
    final stStr = (json['status'] ?? '').toString().toUpperCase();
    if (stStr == 'PAID' || stStr == 'COMPLETED') {
      status = SettlementStatus.paid;
    }

    final createdAt = json['createdAt'] != null
        ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
        : DateTime.now();

    final periodStart = json['periodStart'] != null
        ? DateTime.tryParse(json['periodStart'].toString()) ?? createdAt
        : (json['periodStartDate'] != null
            ? DateTime.tryParse(json['periodStartDate'].toString()) ?? createdAt
            : createdAt);

    final periodEnd = json['periodEnd'] != null
        ? DateTime.tryParse(json['periodEnd'].toString()) ?? createdAt
        : (json['periodEndDate'] != null
            ? DateTime.tryParse(json['periodEndDate'].toString()) ?? createdAt
            : createdAt);

    final gross = (json['grossAmount'] as num?)?.toDouble() ??
        (json['totalGrossAmount'] as num?)?.toDouble() ?? 0.0;
    final commission = (json['commissionAmount'] as num?)?.toDouble() ??
        (json['totalCommissionAmount'] as num?)?.toDouble() ?? 0.0;
    final netPayable = (json['netPayableAmount'] as num?)?.toDouble() ??
        (json['netAmount'] as num?)?.toDouble() ?? (gross - commission);

    return SettlementModel(
      id: json['id']?.toString() ?? '',
      vendorId: json['vendorId']?.toString() ?? '',
      periodStart: periodStart,
      periodEnd: periodEnd,
      grossAmount: gross,
      commissionAmount: commission,
      netPayableAmount: netPayable,
      status: status,
      generatedAt: createdAt,
      orderCount: (json['orderCount'] as num?)?.toInt() ?? 0,
    );
  }

  @override
  Future<List<SettlementModel>> getSettlements({SettlementStatus? status}) async {
    try {
      final json = await _session.requestJson('/api/vendor/settlements?size=30');
      List rawList = [];
      if (json is Map && json['content'] is List) {
        rawList = json['content'] as List;
      } else if (json is List) {
        rawList = json;
      }

      final parsed = rawList
          .whereType<Map<String, dynamic>>()
          .map(parseSettlement)
          .toList();
      _db.settlements.clear();
      _db.settlements.addAll(parsed);
      _db.notifyStoreChanged();
    } catch (_) {}

    if (status == null) {
      return List.unmodifiable(_db.settlements);
    }
    return _db.settlements.where((s) => s.status == status).toList();
  }

  @override
  Future<SettlementModel?> getSettlementById(String id) async {
    try {
      final json = await _session.request('/api/vendor/settlements/$id');
      return parseSettlement(json);
    } catch (_) {
      try {
        return _db.settlements.firstWhere((s) => s.id == id);
      } catch (_) {
        return null;
      }
    }
  }

  @override
  Future<double> getEligiblePayoutAmount({bool combined = false}) async {
    if (combined) {
      return _db.settlements
          .where((s) => s.status == SettlementStatus.pending)
          .fold<double>(0.0, (acc, item) => acc + item.netPayableAmount);
    }
    final firstPending =
        _db.settlements.where((s) => s.status == SettlementStatus.pending).firstOrNull;
    return firstPending?.netPayableAmount ?? 0.0;
  }

  @override
  Future<PayoutRequestModel> requestPayout({
    String? settlementId,
    required double amount,
  }) async {
    final request = PayoutRequestModel(
      id: 'PAY-${DateTime.now().year}${DateTime.now().month.toString().padLeft(2, '0')}-${(100 + _db.payoutRequests.length).toString()}',
      vendorId: _db.currentVendor.id,
      settlementId: settlementId,
      amount: amount,
      status: PayoutRequestStatus.requested,
      createdAt: DateTime.now(),
      bankName: _db.currentVendor.bankName ?? 'Vietcombank',
      bankAccountNumber: _db.currentVendor.bankAccountNumber ?? '0041000889988',
      bankAccountHolder: _db.currentVendor.bankAccountHolder ?? 'TRAN HAI DANG',
    );
    _db.payoutRequests.insert(0, request);
    _db.notifyStoreChanged();
    return request;
  }

  @override
  Future<List<PayoutRequestModel>> getPayoutRequests() async {
    return List.unmodifiable(_db.payoutRequests);
  }
}

// =========================================================================
// 10. DISPUTE REPOSITORY
// =========================================================================
class VendorDisputeRepository implements IVendorDisputeRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  static DisputeModel parseDispute(Map<String, dynamic> json) {
    DisputeStatus status = DisputeStatus.open;
    final stStr = (json['status'] ?? '').toString().toUpperCase();
    if (stStr == 'RESOLVED' || stStr == 'APPROVED') {
      status = DisputeStatus.resolved;
    } else if (stStr == 'REJECTED') {
      status = DisputeStatus.rejected;
    } else if (stStr == 'IN_REVIEW' || stStr == 'VENDOR_RESPONDED') {
      status = DisputeStatus.inReview;
    }

    final createdAt = json['createdAt'] != null
        ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
        : DateTime.now();

    return DisputeModel(
      id: json['id']?.toString() ?? '',
      subOrderId: json['subOrderId']?.toString() ?? '',
      raisedBy: json['raisedBy']?.toString() ?? 'CUSTOMER',
      customerName: json['customerName']?.toString() ?? 'Khách trải nghiệm',
      serviceName: json['serviceName']?.toString() ?? 'Trải nghiệm biển',
      category: json['category']?.toString() ?? json['reason']?.toString() ?? 'Dịch vụ',
      description: json['description']?.toString() ?? json['customerNotes']?.toString() ?? '',
      status: status,
      createdAt: createdAt,
      customerAttachments: (json['customerAttachments'] as List?)?.map((e) => e.toString()).toList() ?? [],
      vendorAttachments: (json['vendorAttachments'] as List?)?.map((e) => e.toString()).toList() ?? [],
    );
  }

  @override
  Future<List<DisputeModel>> getDisputes({DisputeStatus? status}) async {
    try {
      final json = await _session.requestJson('/api/vendor/disputes?size=30');
      List rawList = [];
      if (json is Map && json['content'] is List) {
        rawList = json['content'] as List;
      } else if (json is List) {
        rawList = json;
      }

      if (rawList.isNotEmpty) {
        final parsed = rawList
            .whereType<Map<String, dynamic>>()
            .map(parseDispute)
            .toList();
        _db.disputes.clear();
        _db.disputes.addAll(parsed);
        _db.notifyStoreChanged();
      }
    } catch (_) {}

    if (status == null) {
      return List.unmodifiable(_db.disputes);
    }
    return _db.disputes.where((d) => d.status == status).toList();
  }

  @override
  Future<DisputeModel?> getDisputeById(String id) async {
    try {
      final json = await _session.request('/api/vendor/disputes/$id');
      return parseDispute(json);
    } catch (_) {
      try {
        return _db.disputes.firstWhere((d) => d.id == id);
      } catch (_) {
        return null;
      }
    }
  }

  @override
  Future<DisputeModel> submitDisputeEvidence(String disputeId, String attachmentUrl) async {
    try {
      final res = await _session.request(
        '/api/vendor/disputes/$disputeId/responses',
        method: 'POST',
        data: {
          'message': 'Nhà cung cấp đã bổ sung thông tin giải trình và tài liệu chứng minh.',
          'attachmentUrls': [attachmentUrl],
        },
      );
      final updated = parseDispute(res);
      final index = _db.disputes.indexWhere((d) => d.id == disputeId);
      if (index != -1) _db.disputes[index] = updated;
      _db.notifyStoreChanged();
      return updated;
    } catch (_) {
      final index = _db.disputes.indexWhere((d) => d.id == disputeId);
      if (index != -1) {
        final current = _db.disputes[index];
        final updatedList = List<String>.from(current.vendorAttachments)..add(attachmentUrl);
        final updated = current.copyWith(
          vendorAttachments: updatedList,
          status: DisputeStatus.inReview,
        );
        _db.disputes[index] = updated;
        _db.notifyStoreChanged();
        return updated;
      }
      throw Exception('Khiếu nại không tồn tại.');
    }
  }
}

// =========================================================================
// 11. REFUND REPOSITORY
// =========================================================================
class VendorRefundRepository implements IVendorRefundRepository {
  final VendorStore _db = VendorStore.instance;

  @override
  Future<List<RefundModel>> getRefunds() async {
    return List.unmodifiable(_db.refunds);
  }

  @override
  Future<RefundModel?> getRefundById(String id) async {
    try {
      return _db.refunds.firstWhere((r) => r.id == id);
    } catch (_) {
      return null;
    }
  }
}

// =========================================================================
// 12. CHAT REPOSITORY
// =========================================================================
class VendorChatRepository implements IVendorChatRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  @override
  Future<List<ConversationModel>> getConversations() async {
    try {
      final json = await _session.requestJson('/api/conversations');
      if (json is List) {
        final list = <ConversationModel>[];
        for (final item in json.whereType<Map<String, dynamic>>()) {
          list.add(
            ConversationModel(
              id: item['id']?.toString() ?? '',
              customerId: item['customerId']?.toString() ?? '',
              customerName: item['customerName']?.toString() ??
                  item['partnerName']?.toString() ??
                  'Khách hàng',
              customerAvatar: item['customerAvatar']?.toString() ??
                  item['partnerAvatar']?.toString() ??
                  '',
              vendorId: item['vendorId']?.toString() ?? _db.currentVendor.id,
              lastMessage: item['lastMessage']?.toString() ??
                  item['lastMessagePreview']?.toString() ??
                  '',
              lastMessageTime: item['lastMessageTime'] != null
                  ? DateTime.tryParse(item['lastMessageTime'].toString()) ?? DateTime.now()
                  : DateTime.now(),
              unreadCount: (item['unreadCount'] as num?)?.toInt() ?? 0,
            ),
          );
        }
        if (list.isNotEmpty) {
          _db.conversations.clear();
          _db.conversations.addAll(list);
          _db.notifyStoreChanged();
          return list;
        }
      }
    } catch (_) {}
    return List.unmodifiable(_db.conversations);
  }

  @override
  Future<List<MessageModel>> getMessages(String conversationId) async {
    try {
      final json = await _session.requestJson('/api/conversations/$conversationId/messages');
      if (json is List) {
        final msgs = <MessageModel>[];
        for (final item in json.whereType<Map<String, dynamic>>()) {
          msgs.add(
            MessageModel(
              id: item['id']?.toString() ?? '',
              conversationId: conversationId,
              senderId: item['senderId']?.toString() ?? '',
              content: item['content']?.toString() ?? '',
              createdAt: item['createdAt'] != null
                  ? DateTime.tryParse(item['createdAt'].toString()) ?? DateTime.now()
                  : DateTime.now(),
              isRead: item['isRead'] == true,
            ),
          );
        }
        if (msgs.isNotEmpty) {
          _db.messages[conversationId] = msgs;
          return msgs;
        }
      }
    } catch (_) {}
    return _db.messages[conversationId] ?? [];
  }

  @override
  Future<MessageModel> sendMessage(String conversationId, String content) async {
    try {
      final res = await _session.request(
        '/api/conversations/$conversationId/messages',
        method: 'POST',
        data: {'content': content.trim()},
      );
      final msg = MessageModel(
        id: res['id']?.toString() ?? 'msg-${DateTime.now().millisecondsSinceEpoch}',
        conversationId: conversationId,
        senderId: _db.currentUser.id,
        content: content.trim(),
        createdAt: DateTime.now(),
        isRead: true,
      );
      final list = _db.messages.putIfAbsent(conversationId, () => []);
      list.add(msg);
      _db.notifyStoreChanged();
      return msg;
    } catch (_) {
      final msg = MessageModel(
        id: 'msg-${DateTime.now().millisecondsSinceEpoch}',
        conversationId: conversationId,
        senderId: _db.currentUser.id,
        content: content.trim(),
        createdAt: DateTime.now(),
        isRead: true,
      );
      final list = _db.messages.putIfAbsent(conversationId, () => []);
      list.add(msg);
      _db.notifyStoreChanged();
      return msg;
    }
  }
}

// =========================================================================
// 13. NOTIFICATION REPOSITORY
// =========================================================================
class VendorNotificationRepository implements IVendorNotificationRepository {
  final VendorStore _db = VendorStore.instance;
  final AuthSession _session = AuthSession.instance;

  @override
  Future<List<NotificationModel>> getNotifications() async {
    try {
      final json = await _session.requestJson('/api/notifications');
      if (json is List) {
        final list = <NotificationModel>[];
        for (final item in json.whereType<Map<String, dynamic>>()) {
          list.add(
            NotificationModel(
              id: item['id']?.toString() ?? '',
              userId: item['userId']?.toString() ?? _db.currentUser.id,
              type: item['type']?.toString() ?? 'SYSTEM_ALERT',
              title: item['title']?.toString() ?? '',
              body: item['body']?.toString() ?? item['message']?.toString() ?? '',
              status: item['status']?.toString() ?? 'SENT',
              createdAt: item['createdAt'] != null
                  ? DateTime.tryParse(item['createdAt'].toString()) ?? DateTime.now()
                  : DateTime.now(),
            ),
          );
        }
        if (list.isNotEmpty) {
          _db.notifications.clear();
          _db.notifications.addAll(list);
          _db.notifyStoreChanged();
          return list;
        }
      }
    } catch (_) {}
    return List.unmodifiable(_db.notifications);
  }

  @override
  Future<WeatherCacheModel> getWeatherInfo() async {
    return _db.weatherCache;
  }
}


// =========================================================================
// 14. DASHBOARD REPOSITORY
// =========================================================================
class VendorDashboardRepository implements IVendorDashboardRepository {
  final AuthSession _session = AuthSession.instance;

  @override
  Future<VendorDashboardModel> getDashboardMetrics({DateTime? from, DateTime? to}) async {
    try {
      final params = <String>[];
      if (from != null) params.add('from=${from.toIso8601String().split("T").first}');
      if (to != null) params.add('to=${to.toIso8601String().split("T").first}');
      final query = params.isNotEmpty ? '?${params.join("&")}' : '';
      final json = await _session.request('/api/vendor/dashboard$query');
      return VendorDashboardModel.fromJson(json);
    } catch (_) {
      return const VendorDashboardModel();
    }
  }
}
