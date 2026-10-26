import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/weather_repository.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class WeatherOceanSection extends StatefulWidget {
  const WeatherOceanSection({super.key});

  @override
  State<WeatherOceanSection> createState() => _WeatherOceanSectionState();
}

class _WeatherOceanSectionState extends State<WeatherOceanSection> {
  final WeatherRepository _repository = WeatherRepository.instance;

  int _selectedLocation = 0;
  WeatherSnapshotModel? _snapshot;
  AdvanceBookingSafetyModel? _safety;
  bool _isLoading = true;
  String? _errorMessage;

  static const List<({String name, double lat, double lng, String defaultSlug})>
  _locations = [
    (
      name: 'Bãi biển Mỹ Khê',
      lat: 16.0598,
      lng: 108.2469,
      defaultSlug: 'cheo-sup-kayak',
    ),
    (
      name: 'Bán đảo Sơn Trà',
      lat: 16.1205,
      lng: 108.2683,
      defaultSlug: 'lan-bien-snorkeling',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _loadWeatherForLocation(_selectedLocation);
  }

  Future<void> _loadWeatherForLocation(int index) async {
    final loc = _locations[index];
    setState(() {
      _selectedLocation = index;
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final results = await Future.wait([
        _repository.getCurrentWeather(latitude: loc.lat, longitude: loc.lng),
        _repository.checkAdvanceSafety(
          categorySlug: loc.defaultSlug,
          latitude: loc.lat,
          longitude: loc.lng,
        ),
      ]);
      if (!mounted || _selectedLocation != index) return;
      setState(() {
        _snapshot = results[0] as WeatherSnapshotModel;
        _safety = results[1] as AdvanceBookingSafetyModel;
        _isLoading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted || _selectedLocation != index) return;
      setState(() {
        _errorMessage = e.message;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted || _selectedLocation != index) return;
      setState(() {
        _errorMessage = 'Không thể tải dữ liệu thời tiết & hải văn.';
        _isLoading = false;
      });
    }
  }

  String _windDescription(double? windKmh) {
    if (windKmh == null) return 'Chưa rõ';
    if (windKmh < 12) return 'Gió nhẹ';
    if (windKmh < 25) return 'Gió vừa';
    return 'Gió mạnh';
  }

  String _waveDescription(double? waveM) {
    if (waveM == null) return 'Chưa rõ';
    if (waveM < 0.6) return 'Biển êm';
    if (waveM < 1.2) return 'Sóng gợn';
    return 'Sóng lớn';
  }

  String _rainDescription(double? rainMm) {
    if (rainMm == null || rainMm <= 0.1) return 'Không mưa';
    if (rainMm < 2.5) return 'Mưa nhỏ';
    return 'Có mưa';
  }

  Future<void> _openAdvanceSafetyCheckSheet() async {
    final loc = _locations[_selectedLocation];
    DateTime selectedDate = DateTime.now().add(const Duration(days: 1));
    String selectedSlug = loc.defaultSlug;
    AdvanceBookingSafetyModel? checkResult = _safety;
    bool checking = false;
    String? sheetError;

    const categories = <({String slug, String label})>[
      (slug: 'cheo-sup-kayak', label: 'Chèo SUP & Kayak'),
      (slug: 'lan-bien-snorkeling', label: 'Lặn biển ngắm san hô'),
      (slug: 'moto-nuoc-jetski', label: 'Mô tô nước Jetski'),
      (slug: 'du-luon-cano', label: 'Dù lượn & Ca nô kéo'),
    ];

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (sheetContext, setSheetState) {
            final dateStr =
                '${selectedDate.year}-${selectedDate.month.toString().padLeft(2, '0')}-${selectedDate.day.toString().padLeft(2, '0')}';

            Future<void> runCheck() async {
              if (checking) return;
              setSheetState(() {
                checking = true;
                sheetError = null;
              });
              try {
                final res = await _repository.checkAdvanceSafety(
                  categorySlug: selectedSlug,
                  latitude: loc.lat,
                  longitude: loc.lng,
                  bookingDate: dateStr,
                  startTime: '06:00:00',
                  endTime: '17:00:00',
                );
                setSheetState(() {
                  checkResult = res;
                  checking = false;
                });
              } on AuthFailure catch (e) {
                setSheetState(() {
                  sheetError = e.message;
                  checking = false;
                });
              } catch (_) {
                setSheetState(() {
                  sheetError =
                      'Không thể kiểm tra an toàn cho ngày đã chọn.';
                  checking = false;
                });
              }
            }

            return SafeArea(
              child: Padding(
                padding: EdgeInsets.only(
                  left: AppShapes.gutterMobile,
                  right: AppShapes.gutterMobile,
                  top: AppShapes.gutterMobile,
                  bottom:
                      MediaQuery.of(sheetContext).viewInsets.bottom +
                      AppShapes.gutterMobile,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: LocalizedText(
                            'Kiểm tra an toàn đặt trước',
                            style: AppTypography.headlineSm(
                              color: AppColors.onSurface,
                            ),
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close),
                          onPressed: () => Navigator.pop(sheetContext),
                        ),
                      ],
                    ),
                    LocalizedText(
                      loc.name,
                      style: AppTypography.labelMd(color: AppColors.secondary),
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      initialValue: selectedSlug,
                      decoration: InputDecoration(
                        labelText: tr(sheetContext, 'Loại hình hoạt động biển'),
                        border: const OutlineInputBorder(),
                        isDense: true,
                      ),
                      items:
                          categories
                              .map(
                                (c) => DropdownMenuItem(
                                  value: c.slug,
                                  child: LocalizedText(c.label),
                                ),
                              )
                              .toList(),
                      onChanged: (val) {
                        if (val != null) {
                          setSheetState(() => selectedSlug = val);
                        }
                      },
                    ),
                    const SizedBox(height: 10),
                    OutlinedButton.icon(
                      onPressed: () async {
                        final picked = await showDatePicker(
                          context: sheetContext,
                          initialDate: selectedDate,
                          firstDate: DateTime.now(),
                          lastDate: DateTime.now().add(
                            const Duration(days: 14),
                          ),
                        );
                        if (picked != null) {
                          setSheetState(() => selectedDate = picked);
                        }
                      },
                      icon: const Icon(Icons.calendar_today, size: 16),
                      label: Text(
                        '${tr(sheetContext, 'Ngày dự kiến')}: ${selectedDate.day.toString().padLeft(2, '0')}/${selectedDate.month.toString().padLeft(2, '0')}/${selectedDate.year}',
                      ),
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: Colors.white,
                        ),
                        onPressed: checking ? null : runCheck,
                        icon:
                            checking
                                ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                                : const Icon(Icons.verified_user_outlined),
                        label: const LocalizedText(
                          'Phân tích điều kiện an toàn',
                        ),
                      ),
                    ),
                    if (sheetError != null) ...[
                      const SizedBox(height: 10),
                      LocalizedText(
                        sheetError!,
                        style: AppTypography.bodySm(color: AppColors.error),
                      ),
                    ],
                    if (checkResult != null) ...[
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppColors.borderSubtle),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  checkResult!.isSafe
                                      ? Icons.check_circle
                                      : Icons.warning_amber_rounded,
                                  color:
                                      checkResult!.alertLevel == 'RED'
                                          ? AppColors.error
                                          : AppColors.secondary,
                                  size: 18,
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    '${tr(sheetContext, 'Mức cảnh báo')}: ${checkResult!.alertLevel} (${checkResult!.isSafe ? tr(sheetContext, 'An toàn') : tr(sheetContext, 'Hạn chế')})',
                                    style: AppTypography.labelMd(
                                      color: AppColors.onSurface,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if (checkResult!.summaryMessage != null) ...[
                              const SizedBox(height: 6),
                              LocalizedText(
                                checkResult!.summaryMessage!,
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurfaceVariant,
                                ),
                              ),
                            ],
                            const SizedBox(height: 6),
                            Text(
                              '${tr(sheetContext, 'Sóng tối đa')}: ${(checkResult!.peakWaveHeightM ?? 0).toStringAsFixed(1)}m • ${tr(sheetContext, 'Gió tối đa')}: ${(checkResult!.peakWindSpeedKmh ?? 0).toStringAsFixed(0)} km/h',
                              style: AppTypography.labelSm(
                                color: AppColors.secondary,
                              ),
                            ),
                            for (final note
                                in checkResult!.advisoryDetails.take(3)) ...[
                              const SizedBox(height: 4),
                              Text(
                                '• $note',
                                style: AppTypography.bodySm(
                                  color: AppColors.onSurfaceVariant,
                                ).copyWith(fontSize: 11),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final windVal =
        _snapshot?.windSpeed != null
            ? '${_snapshot!.windSpeed!.toStringAsFixed(0)} km/h'
            : '-- km/h';
    final waveVal =
        _snapshot?.waveHeight != null
            ? '${_snapshot!.waveHeight!.toStringAsFixed(1)}m'
            : '-- m';
    final rainVal =
        _snapshot?.precipitation != null
            ? '${_snapshot!.precipitation!.toStringAsFixed(1)} mm'
            : '0 mm';

    final statusBadgeText =
        _safety != null
            ? (_safety!.alertLevel == 'RED'
                ? 'Cảnh báo biển động'
                : _safety!.alertLevel == 'YELLOW'
                ? 'Lưu ý sóng gió'
                : 'An toàn hoạt động')
            : 'Đang cập nhật';

    final safetyTipText =
        _safety?.summaryMessage ??
        (_safety?.advisoryDetails.isNotEmpty == true
            ? _safety!.advisoryDetails.first
            : 'Điều kiện mặt biển ổn định cho các hoạt động thể thao biển.');

    final fetchedTime = _snapshot?.fetchedAt;
    final footerText =
        fetchedTime != null
            ? '${tr(context, 'Cập nhật lúc')} ${fetchedTime.hour.toString().padLeft(2, '0')}:${fetchedTime.minute.toString().padLeft(2, '0')}'
            : tr(context, 'Dữ liệu khí tượng hải văn trực tiếp');

    return Container(
      padding: const EdgeInsets.all(AppShapes.spaceMd),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusLg,
        boxShadow: AppShapes.shadowLevel1,
        border: Border.all(
          color: AppColors.borderSubtle.withValues(alpha: 0.6),
          width: 0.8,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header Row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    const Icon(
                      Icons.waves,
                      color: AppColors.secondary,
                      size: 22,
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      child: LocalizedText(
                        'Điều kiện biển & Thời tiết',
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.onSurface,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer,
                  borderRadius: AppShapes.radiusFull,
                ),
                child: LocalizedText(
                  statusBadgeText,
                  style: AppTypography.labelSm(
                    color: AppColors.onSecondaryContainer,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Location Switcher Tabs
          Row(
            children: [
              Expanded(
                child: _buildLocationTab(index: 0, label: 'Bãi biển Mỹ Khê'),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildLocationTab(index: 1, label: 'Bán đảo Sơn Trà'),
              ),
            ],
          ),
          const SizedBox(height: 14),

          if (_isLoading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_errorMessage != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: AppShapes.radiusDefault,
              ),
              child: Column(
                children: [
                  LocalizedText(
                    _errorMessage!,
                    textAlign: TextAlign.center,
                    style: AppTypography.bodySm(color: AppColors.error),
                  ),
                  const SizedBox(height: 8),
                  TextButton.icon(
                    onPressed: () => _loadWeatherForLocation(_selectedLocation),
                    icon: const Icon(Icons.refresh, size: 16),
                    label: const LocalizedText('Thử lại'),
                  ),
                ],
              ),
            )
          else ...[
            // 3 Weather Metric Cards
            Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    icon: Icons.air,
                    value: windVal,
                    label: _windDescription(_snapshot?.windSpeed),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    icon: Icons.tsunami,
                    value: waveVal,
                    label: _waveDescription(_snapshot?.waveHeight),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    icon: Icons.water_drop_outlined,
                    value: rainVal,
                    label: _rainDescription(_snapshot?.precipitation),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Safety Advisory Notice
            InkWell(
              onTap: _openAdvanceSafetyCheckSheet,
              borderRadius: AppShapes.radiusDefault,
              child: Container(
                padding: const EdgeInsets.all(AppShapes.spaceSm),
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer.withValues(alpha: 0.25),
                  borderRadius: AppShapes.radiusDefault,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.warning_amber_rounded,
                      color: AppColors.secondary,
                      size: 20,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              LocalizedText(
                                'Khuyến nghị an toàn',
                                style: AppTypography.labelMd(
                                  color: AppColors.onSecondaryFixedVariant,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              LocalizedText(
                                'Tra cứu theo ngày ›',
                                style: AppTypography.labelSm(
                                  color: AppColors.secondary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          LocalizedText(
                            safetyTipText,
                            style: AppTypography.bodySm(
                              color: AppColors.onSecondaryFixedVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),

            // Footer info
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  footerText,
                  style: AppTypography.labelSm(color: AppColors.outline),
                ),
                if (_snapshot?.temperature != null)
                  Text(
                    '${_snapshot!.temperature!.toStringAsFixed(1)}°C',
                    style: AppTypography.labelSm(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildLocationTab({required int index, required String label}) {
    final isSelected = _selectedLocation == index;
    return InkWell(
      onTap: () => _loadWeatherForLocation(index),
      borderRadius: AppShapes.radiusFull,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.secondary : AppColors.surfaceContainer,
          borderRadius: AppShapes.radiusFull,
        ),
        child: LocalizedText(
          label,
          style: AppTypography.labelSm(
            color: isSelected ? AppColors.onSecondary : AppColors.onSurface,
            fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildMetricCard({
    required IconData icon,
    required String value,
    required String label,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: AppShapes.radiusDefault,
      ),
      child: Column(
        children: [
          Icon(icon, size: 20, color: AppColors.secondary),
          const SizedBox(height: 4),
          Text(
            value,
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ).copyWith(fontSize: 16),
          ),
          const SizedBox(height: 2),
          LocalizedText(
            label,
            style: AppTypography.labelSm(color: AppColors.onSurfaceVariant),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
