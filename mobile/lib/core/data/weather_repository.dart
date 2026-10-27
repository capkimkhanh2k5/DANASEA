import '../auth/auth_session.dart';

class WeatherSnapshotModel {
  final double latitude;
  final double longitude;
  final DateTime? fetchedAt;
  final String? source;
  final bool stale;
  final String? dataCoverage;
  final bool estimatedMarine;

  // Weather metrics
  final double? temperature;
  final double? precipitation;
  final double? windSpeed;
  final double? windGust;
  final double? windDirection;
  final double? visibility;
  final double? cloudCover;
  final int? weatherCode;
  final int? precipitationProbability;
  final double? uvIndex;
  final double? relativeHumidity;

  // Marine metrics
  final double? waveHeight;
  final double? waveDirection;
  final double? wavePeriod;
  final double? swellHeight;
  final double? oceanCurrentVelocity;
  final double? seaSurfaceTemperature;

  const WeatherSnapshotModel({
    required this.latitude,
    required this.longitude,
    this.fetchedAt,
    this.source,
    this.stale = false,
    this.dataCoverage,
    this.estimatedMarine = false,
    this.temperature,
    this.precipitation,
    this.windSpeed,
    this.windGust,
    this.windDirection,
    this.visibility,
    this.cloudCover,
    this.weatherCode,
    this.precipitationProbability,
    this.uvIndex,
    this.relativeHumidity,
    this.waveHeight,
    this.waveDirection,
    this.wavePeriod,
    this.swellHeight,
    this.oceanCurrentVelocity,
    this.seaSurfaceTemperature,
  });
}

class AdvanceBookingSafetyModel {
  final DateTime? fetchedAt;
  final String? source;
  final bool stale;
  final bool isSafe;
  final String alertLevel; // GREEN, YELLOW, RED
  final String safetyStatus;
  final bool isProvisional;
  final bool marineCutoffExceeded;
  final String? slotId;
  final String? serviceId;
  final String? categorySlug;
  final String? categoryName;
  final String? bookingDate;
  final String? startTime;
  final String? endTime;
  final int daysAhead;
  final double? peakWaveHeightM;
  final double? peakWindSpeedKmh;
  final double? peakWindGustKmh;
  final double? peakOceanCurrentMs;
  final double? minVisibilityM;
  final int? severeWeatherCode;
  final double? totalPrecipitationMm;
  final double? maxWaveHeightM;
  final double? maxWindSpeedKmh;
  final String? warningMessage;
  final String? summaryMessage;
  final List<String> advisoryDetails;
  final String? dataCoverage;
  final String? dataCoverageNote;
  final String? confidenceLevel;

  const AdvanceBookingSafetyModel({
    this.fetchedAt,
    this.source,
    this.stale = false,
    required this.isSafe,
    required this.alertLevel,
    required this.safetyStatus,
    this.isProvisional = false,
    this.marineCutoffExceeded = false,
    this.slotId,
    this.serviceId,
    this.categorySlug,
    this.categoryName,
    this.bookingDate,
    this.startTime,
    this.endTime,
    this.daysAhead = 0,
    this.peakWaveHeightM,
    this.peakWindSpeedKmh,
    this.peakWindGustKmh,
    this.peakOceanCurrentMs,
    this.minVisibilityM,
    this.severeWeatherCode,
    this.totalPrecipitationMm,
    this.maxWaveHeightM,
    this.maxWindSpeedKmh,
    this.warningMessage,
    this.summaryMessage,
    this.advisoryDetails = const [],
    this.dataCoverage,
    this.dataCoverageNote,
    this.confidenceLevel,
  });
}

class WeatherRepository {
  WeatherRepository._();
  static final WeatherRepository instance = WeatherRepository._();

  final AuthSession _session = AuthSession.instance;

  /// GET /api/weather/current
  Future<WeatherSnapshotModel> getCurrentWeather({
    double latitude = 16.089035780716284,
    double longitude = 108.24959555394304,
  }) async {
    final query = Uri(
      queryParameters: {
        'lat': latitude.toString(),
        'lng': longitude.toString(),
      },
    ).query;

    final body = await _session.requestJson(
      '/api/weather/current?$query',
      method: 'GET',
      authenticated: false,
    );

    if (body is! Map) {
      throw AuthFailure('Phản hồi dữ liệu thời tiết không hợp lệ.');
    }

    return _mapSnapshot(Map<String, dynamic>.from(body));
  }

  /// GET /api/weather/advance-safety-check
  Future<AdvanceBookingSafetyModel> checkAdvanceSafety({
    String? slotId,
    String? categoryId,
    String? categorySlug,
    double? latitude,
    double? longitude,
    String? bookingDate,
    String? startTime,
    String? endTime,
  }) async {
    final params = <String, String>{};
    if (slotId != null && slotId.trim().isNotEmpty) {
      params['slotId'] = slotId.trim();
    } else {
      if (categoryId != null &&
          categoryId.trim().isNotEmpty &&
          _looksLikeUuid(categoryId.trim())) {
        params['categoryId'] = categoryId.trim();
      } else if (categorySlug != null && categorySlug.trim().isNotEmpty) {
        params['categorySlug'] = categorySlug.trim();
      }
      if (latitude != null && latitude != 0) {
        params['latitude'] = latitude.toString();
      }
      if (longitude != null && longitude != 0) {
        params['longitude'] = longitude.toString();
      }
      final now = DateTime.now();
      final defaultDate =
          '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
      params['bookingDate'] =
          (bookingDate != null && bookingDate.trim().isNotEmpty)
              ? bookingDate.trim()
              : defaultDate;
      if (startTime != null && startTime.trim().isNotEmpty) {
        params['startTime'] = _normalizeTime(startTime.trim());
      }
      if (endTime != null && endTime.trim().isNotEmpty) {
        params['endTime'] = _normalizeTime(endTime.trim());
      }
    }

    final query = Uri(queryParameters: params).query;
    final body = await _session.requestJson(
      '/api/weather/advance-safety-check?$query',
      method: 'GET',
      authenticated: false,
    );

    if (body is! Map) {
      throw AuthFailure('Phản hồi kiểm tra an toàn biển không hợp lệ.');
    }

    return _mapSafety(Map<String, dynamic>.from(body));
  }

  bool _looksLikeUuid(String value) {
    final regex = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    );
    return regex.hasMatch(value);
  }

  String _normalizeTime(String time) {
    final parts = time.split(':');
    if (parts.length == 2) {
      return '${parts[0].padLeft(2, '0')}:${parts[1].padLeft(2, '0')}:00';
    }
    return time;
  }

  WeatherSnapshotModel _mapSnapshot(Map<String, dynamic> map) {
    final weatherMap =
        map['weather'] is Map
            ? Map<String, dynamic>.from(map['weather'] as Map)
            : const <String, dynamic>{};
    final marineMap =
        map['marine'] is Map
            ? Map<String, dynamic>.from(map['marine'] as Map)
            : const <String, dynamic>{};

    return WeatherSnapshotModel(
      latitude: _asDouble(map['latitude']) ?? 16.089,
      longitude: _asDouble(map['longitude']) ?? 108.249,
      fetchedAt: _parseDateTime(map['fetchedAt']),
      source: _asString(map['source']),
      stale: map['stale'] == true,
      dataCoverage: _asString(map['dataCoverage']),
      estimatedMarine: map['estimatedMarine'] == true,
      temperature: _asDouble(weatherMap['temperature']),
      precipitation: _asDouble(weatherMap['precipitation']),
      windSpeed: _asDouble(weatherMap['windSpeed']),
      windGust: _asDouble(weatherMap['windGust']),
      windDirection: _asDouble(weatherMap['windDirection']),
      visibility: _asDouble(weatherMap['visibility']),
      cloudCover: _asDouble(weatherMap['cloudCover']),
      weatherCode: _asInt(weatherMap['weatherCode']),
      precipitationProbability: _asInt(weatherMap['precipitationProbability']),
      uvIndex: _asDouble(weatherMap['uvIndex']),
      relativeHumidity: _asDouble(weatherMap['relativeHumidity']),
      waveHeight: _asDouble(marineMap['waveHeight']),
      waveDirection: _asDouble(marineMap['waveDirection']),
      wavePeriod: _asDouble(marineMap['wavePeriod']),
      swellHeight: _asDouble(marineMap['swellHeight']),
      oceanCurrentVelocity: _asDouble(marineMap['oceanCurrentVelocity']),
      seaSurfaceTemperature: _asDouble(marineMap['seaSurfaceTemperature']),
    );
  }

  AdvanceBookingSafetyModel _mapSafety(Map<String, dynamic> map) {
    final alertLevel =
        (_asString(map['alertLevel']) ??
                _asString(map['safetyStatus']) ??
                'GREEN')
            .toUpperCase();
    final isSafe =
        map['isSafe'] == true ||
        map['safe'] == true ||
        (map['isSafe'] == null && alertLevel != 'RED');
    final rawDetails =
        map['details'] ?? map['advisoryDetails'] ?? map['advisoryNotes'];
    final details = <String>[];
    if (rawDetails is List) {
      for (final item in rawDetails) {
        final text = _asString(item);
        if (text != null) details.add(text);
      }
    }

    return AdvanceBookingSafetyModel(
      fetchedAt: _parseDateTime(map['fetchedAt']),
      source: _asString(map['source']),
      stale: map['stale'] == true,
      isSafe: isSafe,
      alertLevel: alertLevel,
      safetyStatus: _asString(map['safetyStatus']) ?? alertLevel,
      isProvisional:
          map['isProvisional'] == true || map['provisional'] == true,
      marineCutoffExceeded: map['marineCutoffExceeded'] == true,
      slotId: _asString(map['slotId']),
      serviceId: _asString(map['serviceId']),
      categorySlug: _asString(map['categorySlug']),
      categoryName: _asString(map['categoryName']),
      bookingDate: _asString(map['bookingDate']),
      startTime: _asString(map['startTime']),
      endTime: _asString(map['endTime']),
      daysAhead:
          _asInt(map['daysAhead']) ?? _asInt(map['daysInAdvance']) ?? 0,
      peakWaveHeightM: _asDouble(map['peakWaveHeightM']),
      peakWindSpeedKmh: _asDouble(map['peakWindSpeedKmh']),
      peakWindGustKmh: _asDouble(map['peakWindGustKmh']),
      peakOceanCurrentMs: _asDouble(map['peakOceanCurrentMs']),
      minVisibilityM: _asDouble(map['minVisibilityM']),
      severeWeatherCode: _asInt(map['severeWeatherCode']),
      totalPrecipitationMm: _asDouble(map['totalPrecipitationMm']),
      maxWaveHeightM: _asDouble(map['maxWaveHeightM']),
      maxWindSpeedKmh: _asDouble(map['maxWindSpeedKmh']),
      warningMessage: _asString(map['warningMessage']),
      summaryMessage:
          _asString(map['summaryMessage']) ?? _asString(map['warningMessage']),
      advisoryDetails: details,
      dataCoverage: _asString(map['dataCoverage']),
      dataCoverageNote: _asString(map['dataCoverageNote']),
      confidenceLevel: _asString(map['confidenceLevel']),
    );
  }

  String? _asString(Object? value) {
    if (value == null) return null;
    final text = value.toString().trim();
    return text.isEmpty ? null : text;
  }

  double? _asDouble(Object? value) {
    if (value is double) return value;
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value.trim());
    return null;
  }

  int? _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value.trim());
    return null;
  }

  DateTime? _parseDateTime(Object? value) {
    final text = _asString(value);
    if (text == null) return null;
    return DateTime.tryParse(text)?.toLocal();
  }
}
