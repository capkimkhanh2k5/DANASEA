import '../../../../core/models/danasea_models.dart';
/// Data models representing elements on the DanaSea Home Screen.
/// Structured to allow seamless replacement with real API/backend models later.
class ExperienceItem {
  final String id;
  final String title;
  final String location;
  final String duration;
  final double rating;
  final int reviewCount;
  final int price;
  final String imageUrl;
  final bool isVerified;
  final bool isFavorite;
  final VendorBadgeTier vendorBadge;

  const ExperienceItem({
    required this.id,
    required this.title,
    required this.location,
    required this.duration,
    required this.rating,
    required this.reviewCount,
    required this.price,
    required this.imageUrl,
    this.isVerified = true,
    this.isFavorite = false,
    this.vendorBadge = VendorBadgeTier.none,
  });

  ExperienceItem copyWith({
    String? id,
    String? title,
    String? location,
    String? duration,
    double? rating,
    int? reviewCount,
    int? price,
    String? imageUrl,
    bool? isVerified,
    bool? isFavorite,
    VendorBadgeTier? vendorBadge,
  }) {
    return ExperienceItem(
      id: id ?? this.id,
      title: title ?? this.title,
      location: location ?? this.location,
      duration: duration ?? this.duration,
      rating: rating ?? this.rating,
      reviewCount: reviewCount ?? this.reviewCount,
      price: price ?? this.price,
      imageUrl: imageUrl ?? this.imageUrl,
      isVerified: isVerified ?? this.isVerified,
      isFavorite: isFavorite ?? this.isFavorite,
      vendorBadge: vendorBadge ?? this.vendorBadge,
    );
  }
}

class BeachRegionItem {
  final String id;
  final String tag;
  final String title;
  final String description;
  final String imageUrl;

  const BeachRegionItem({
    required this.id,
    required this.tag,
    required this.title,
    required this.description,
    required this.imageUrl,
  });
}

class WeatherOceanData {
  final String location;
  final String updatedAt;
  final String windSpeed;
  final String windDesc;
  final String waveHeight;
  final String waveDesc;
  final String rainLevel;
  final String rainDesc;
  final String safetyTip;

  const WeatherOceanData({
    required this.location,
    required this.updatedAt,
    required this.windSpeed,
    required this.windDesc,
    required this.waveHeight,
    required this.waveDesc,
    required this.rainLevel,
    required this.rainDesc,
    required this.safetyTip,
  });
}

class RecentItem {
  final String id;
  final String title;
  final String location;
  final int price;
  final String imageUrl;

  const RecentItem({
    required this.id,
    required this.title,
    required this.location,
    required this.price,
    required this.imageUrl,
  });
}
