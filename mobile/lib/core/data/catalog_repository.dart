import '../auth/auth_session.dart';
import '../models/danasea_models.dart';

class CatalogPage {
  final List<ServiceModel> items;
  final int page;
  final int total;
  const CatalogPage(this.items, this.page, this.total);
}

class CatalogRepository {
  CatalogRepository({AuthSession? session})
    : session = session ?? AuthSession.instance;
  final AuthSession session;

  Future<List<CategoryModel>> categories() async {
    final json = await session.requestJson('/api/categories');
    if (json is! List) throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    final result = <CategoryModel>[];
    void visit(List nodes) {
      for (final node in nodes) {
        final map = node as Map<String, dynamic>;
        if (map['isActive'] == false) continue;
        result.add(
          CategoryModel(
            id: map['id'] as String,
            name: map['name'] as String,
            nameEn: map['nameEn'] as String? ?? '',
            slug: map['slug'] as String? ?? '',
            parentId: map['parentId'] as String?,
            iconUrl: map['iconUrl'] as String?,
          ),
        );
        visit(map['children'] as List? ?? []);
      }
    }

    visit(json);
    return result;
  }

  Future<CatalogPage> search({
    String? keyword,
    String? categoryId,
    int? maxPrice,
    int page = 0,
  }) async {
    final query = <String, String>{
      'page': '$page',
      'size': '20',
      if (keyword != null && keyword.trim().isNotEmpty)
        'keyword': keyword.trim(),
      'categoryId': ?categoryId,
      if (maxPrice != null) 'maxPrice': '$maxPrice',
    };
    final path = Uri(path: '/api/services', queryParameters: query).toString();
    final json = await session.request(path);
    if (json['content'] is! List || json['totalElements'] is! num) {
      throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    }
    return CatalogPage(
      (json['content'] as List)
          .map((e) => serviceFromJson(e as Map<String, dynamic>))
          .toList(),
      (json['page'] as num).toInt(),
      (json['totalElements'] as num).toInt(),
    );
  }

  Future<ServiceModel> detail(String id) async {
    final service = serviceFromJson(
      await session.request('/api/services/${Uri.encodeComponent(id)}'),
    );
    // Multiple active options require explicit option IDs on the slots API.
    final available = service.options.isEmpty
        ? await slots(id)
        : (await Future.wait(
            service.options.map((option) => slots(id, optionId: option.id)),
          )).expand((items) => items).toList();
    return service.copyWith(catalogSlots: available);
  }

  Future<List<CatalogSlot>> slots(String id, {String? optionId}) async {
    final path = '/api/services/${Uri.encodeComponent(id)}/slots';
    final query = optionId == null
        ? ''
        : '?${Uri(queryParameters: {'optionId': optionId}).query}';
    final json = await session.requestJson('$path$query', authenticated: false);
    if (json is! List) throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
    return json.map((item) {
      if (item is! Map<String, dynamic> ||
          item['slotId'] is! String ||
          item['date'] is! String ||
          item['startTime'] is! String ||
          item['endTime'] is! String ||
          item['availablePaxOrPackages'] is! num ||
          item['bookable'] is! bool) {
        throw AuthFailure('Dữ liệu máy chủ không hợp lệ.');
      }
      return CatalogSlot(
        id: item['slotId'] as String,
        optionId: item['optionId'] as String? ?? optionId,
        date: item['date'] as String,
        startTime: item['startTime'] as String,
        endTime: item['endTime'] as String,
        availableCapacity: (item['availablePaxOrPackages'] as num).toInt(),
        maxPaxPerPackage: (item['maxPaxPerPackage'] as num?)?.toInt(),
        price: item['price'] as num?,
        pricingUnit: item['pricingUnit'] as String?,
        bookable: item['bookable'] as bool,
      );
    }).toList();
  }

  static ServiceModel serviceFromJson(Map<String, dynamic> json) {
    num number(String key) => json[key] is num ? json[key] as num : 0;
    final address = json['address'] as String? ?? '';
    final primary = json['primaryImageUrl'] as String?;
    return ServiceModel(
      id: json['id'] as String,
      options: (json['options'] as List? ?? [])
          .cast<Map<String, dynamic>>()
          .where((option) => option['status'] == 'ACTIVE')
          .map(
            (option) => CatalogOption(
              id: option['id'] as String,
              name: option['name'] as String,
            ),
          )
          .toList(),
      name: json['name'] as String,
      vendorId: json['vendorId'] as String? ?? '',
      vendorName: json['businessName'] as String? ?? '',
      vendorBadge: switch (json['badgeTier']) {
        'VERIFIED' => VendorBadgeTier.verified,
        'TOP_RATED' => VendorBadgeTier.topRated,
        _ => VendorBadgeTier.none,
      },
      categoryId: json['categoryId'] as String? ?? '',
      categoryName: json['categoryName'] as String? ?? '',
      slug: '',
      description:
          (json['description'] ?? json['shortDescription'] ?? '') as String,
      price: (json['promotionalPrice'] as num? ?? number('price')).round(),
      durationMinutes: number('duration').toInt(),
      capacityPerSlot: number('capacity').toInt(),
      locationName: address,
      address: address,
      latitude: number('latitude').toDouble(),
      longitude: number('longitude').toDouble(),
      waiverContent: json['waiverContent'] as String? ?? '',
      participantConditions: json['participantConditions'] as String? ?? '',
      refundPolicy: json['refundPolicy'] as String? ?? '',
      cancellationPolicy: json['cancellationPolicy'] as String? ?? '',
      safetyRules: json['safetyRules'] as String? ?? '',
      waiverRequired: json['waiverRequired'] as bool?,
      availableSlots: (json['availableSlots'] as List? ?? [])
          .whereType<String>()
          .toList(),
      catalogSlots: (json['slots'] as List? ?? [])
          .whereType<Map>()
          .map(
            (slot) => CatalogSlot(
              id: slot['slotId'] as String? ?? '',
              date: slot['date'] as String? ?? '',
              startTime: slot['startTime'] as String? ?? '',
              endTime: slot['endTime'] as String? ?? '',
              availableCapacity: (slot['availableCapacity'] as num?)?.toInt(),
              price: slot['price'] as num?,
              pricingUnit: slot['pricingUnit'] as String?,
              bookable: slot['bookable'] == true,
            ),
          )
          .toList(),
      weatherSensitive: false,
      avgRating: number('averageRating').toDouble(),
      ratingCount: number('reviewCount').toInt(),
      viewCount: number('viewCount').toInt(),
      imageUrls: json['imageUrls'] is List
          ? (json['imageUrls'] as List)
                .whereType<String>()
                .where((s) => s.isNotEmpty)
                .toList()
          : [if (primary != null && primary.isNotEmpty) primary],
    );
  }
}
