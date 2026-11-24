import '../../../../core/data/vendor_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/widgets/alarm_wheel_picker.dart';

class SlotConfigScreen extends StatefulWidget {
  final ServiceSlotModel? slot;
  final String serviceId;
  final String? initialDate;
  final int initialMode;

  const SlotConfigScreen({
    super.key,
    this.slot,
    this.serviceId = 'srv-001',
    this.initialDate,
    this.initialMode = 0,
  });

  @override
  State<SlotConfigScreen> createState() => _SlotConfigScreenState();
}

class _SlotConfigScreenState extends State<SlotConfigScreen> {
  final _db = VendorStore.instance;
  final _slotRepo = VendorSlotRepository();
  final _equipmentRepo = VendorEquipmentRepository();
  bool _isSaving = false;
  String _inventoryType = 'PERSON_LIMIT';

  List<VendorServiceOptionModel> _serviceOptions = [];
  bool _isLoadingOptions = false;
  String? _optionsLoadError;
  bool _userManuallySelectedInventoryType = false;

  List<VendorServiceOptionModel> get _activeOptions =>
      _serviceOptions.where((o) => o.status == 'ACTIVE').toList();

  bool get _hasActivePrivateOption => _activeOptions.any(
    (o) => o.optionType == 'PRIVATE' || o.pricingUnit == 'PER_PACKAGE',
  );

  /// Dịch vụ có gói bao trọn đang hoạt động: bắt buộc quản lý theo phương tiện/thiết bị (khi tạo mới)
  bool get _isPrivateRequirementEnforced => !_isEdit && _hasActivePrivateOption;

  final _unitsController = TextEditingController();
  List<int> _unitCapacities = [];

  List<VendorEquipmentModel> _warehouseEquipment = [];
  bool _isLoadingEquipment = false;
  final List<VendorEquipmentModel> _assignedEquipment = [];

  late int _creationMode; // 0: Ca lẻ (1 ngày), 1: Theo tuần (Hàng loạt)
  final Set<String> _selectedBatchDays = {
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY',
  };
  int _batchWeeks = 1; // 1, 2, 4 tuần

  late TextEditingController _dateController;
  late TextEditingController _startTimeController;
  late TextEditingController _endTimeController;
  late int _capacity;
  late bool _isBlocked;
  List<Map<String, dynamic>> _editingUnits = [];

  bool get _isEdit => widget.slot != null;
  int get _bookedCount => widget.slot?.bookedCount ?? 0;
  int get _heldCount => widget.slot?.heldCount ?? 0;
  int get _minAllowedCapacity => _bookedCount + _heldCount;

  int get _effectiveTotalCapacity {
    if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
      if (_assignedEquipment.isNotEmpty) {
        return _assignedEquipment.fold<int>(0, (s, e) => s + e.capacity);
      }
      if (_isEdit && _editingUnits.isNotEmpty) {
        return _editingUnits.fold<int>(
          0,
          (s, u) => s + ((u['capacity'] as num?)?.toInt() ?? 0),
        );
      }
      return 0;
    }
    return _capacity;
  }

  List<int> get _effectiveUnitCapacities {
    if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
      if (_assignedEquipment.isNotEmpty) {
        return _assignedEquipment.map((e) => e.capacity).toList();
      }
      if (_isEdit && _editingUnits.isNotEmpty) {
        return _editingUnits
            .map<int>((u) => (u['capacity'] as num?)?.toInt() ?? 0)
            .toList();
      }
    }
    return [];
  }

  ({bool isCompatible, String reason}) _evaluateOptionCompatibility(
    VendorServiceOptionModel option,
  ) {
    final isPrivate =
        option.optionType == 'PRIVATE' || option.pricingUnit == 'PER_PACKAGE';
    if (!isPrivate) {
      if (_effectiveTotalCapacity <= 0) {
        return (isCompatible: false, reason: 'Sức chứa ca chạy hiện bằng 0.');
      }
      return (
        isCompatible: true,
        reason:
            'Phù hợp bán ghép khách (Tổng sức chứa hiện tại: $_effectiveTotalCapacity chỗ).',
      );
    }

    if (_inventoryType != 'SHARED_CAPACITY_UNITS') {
      return (
        isCompatible: false,
        reason:
            'Cần quản lý theo từng phương tiện/thiết bị để nhận gói bao trọn.',
      );
    }

    final requiredPax = option.maxPaxPerPackage ?? 1;
    final unitCaps = _effectiveUnitCapacities;
    if (unitCaps.isEmpty) {
      return (
        isCompatible: false,
        reason: 'Chưa cấu hình phương tiện/thiết bị từ kho cho ca.',
      );
    }

    final hasEligibleUnit = unitCaps.any((cap) => cap >= requiredPax);
    if (!hasEligibleUnit) {
      final maxAvailable = unitCaps.reduce((a, b) => a > b ? a : b);
      return (
        isCompatible: false,
        reason:
            'Gói riêng $requiredPax khách cần ít nhất một phương tiện có $requiredPax chỗ (hiện có lớn nhất: $maxAvailable chỗ).',
      );
    }

    return (
      isCompatible: true,
      reason: 'Phù hợp bao trọn (Có phương tiện đáp ứng đủ $requiredPax chỗ).',
    );
  }

  int get _slotDurationMinutes {
    final startParts = _startTimeController.text.split(':');
    final endParts = _endTimeController.text.split(':');
    if (startParts.length >= 2 && endParts.length >= 2) {
      final sH = int.tryParse(startParts[0]) ?? 0;
      final sM = int.tryParse(startParts[1]) ?? 0;
      final eH = int.tryParse(endParts[0]) ?? 0;
      final eM = int.tryParse(endParts[1]) ?? 0;
      final diff = (eH * 60 + eM) - (sH * 60 + sM);
      if (diff > 0) return diff;
    }
    return 60;
  }

  bool get _isDateToday {
    final now = DateTime.now();
    final todayStr =
        '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    return _dateController.text == todayStr;
  }

  bool get _isDateTomorrow {
    final tmr = DateTime.now().add(const Duration(days: 1));
    final tmrStr =
        '${tmr.year}-${tmr.month.toString().padLeft(2, '0')}-${tmr.day.toString().padLeft(2, '0')}';
    return _dateController.text == tmrStr;
  }

  void _setDateToday() {
    final now = DateTime.now();
    setState(() {
      _dateController.text =
          '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    });
  }

  void _setDateTomorrow() {
    final tmr = DateTime.now().add(const Duration(days: 1));
    setState(() {
      _dateController.text =
          '${tmr.year}-${tmr.month.toString().padLeft(2, '0')}-${tmr.day.toString().padLeft(2, '0')}';
    });
  }

  @override
  void initState() {
    super.initState();
    _creationMode = widget.initialMode;
    if (_isEdit) {
      final s = widget.slot!;
      _inventoryType = s.inventoryType;
      _editingUnits = s.units.map((u) => Map<String, dynamic>.from(u)).toList();
      if (_editingUnits.isEmpty &&
          _inventoryType == 'SHARED_CAPACITY_UNITS' &&
          s.capacity > 0) {
        final count = (s.capacity / 2).ceil();
        _editingUnits = List.generate(
          count,
          (i) => {'unitNumber': i + 1, 'capacity': 2},
        );
      }
      if (_editingUnits.isNotEmpty) {
        _unitCapacities = _editingUnits
            .map<int>((u) => (u['capacity'] as num?)?.toInt() ?? 2)
            .toList();
      } else {
        _unitCapacities = [];
      }
      _unitsController.text = _unitCapacities.join(', ');
      _dateController = TextEditingController(
        text: s.date.toIso8601String().split('T').first,
      );
      _startTimeController = TextEditingController(
        text: s.startTime.substring(0, 5),
      );
      _endTimeController = TextEditingController(
        text: s.endTime.substring(0, 5),
      );
      _capacity = s.capacity;
      _isBlocked = s.isBlocked;
    } else {
      final service = _db.services
          .where(
            (s) => s.serviceId == widget.serviceId || s.id == widget.serviceId,
          )
          .firstOrNull;
      _inventoryType = 'PERSON_LIMIT';
      _unitCapacities = [];
      _dateController = TextEditingController(
        text:
            widget.initialDate ??
            DateTime.now().toIso8601String().split('T').first,
      );
      _startTimeController = TextEditingController(text: '05:00');
      _endTimeController = TextEditingController(text: '07:00');
      _capacity = service != null && service.capacityPerSlot > 0
          ? service.capacityPerSlot
          : 10;
      _isBlocked = false;
    }
    _loadWarehouseEquipment();
    _detectServicePackageMode();
  }

  void _autoAssignEquipmentForService() {
    if (_warehouseEquipment.isEmpty || _assignedEquipment.isNotEmpty) return;
    final service = _db.services
        .where(
          (s) => s.serviceId == widget.serviceId || s.id == widget.serviceId,
        )
        .firstOrNull;
    final sName = (service?.name ?? '').toLowerCase();

    VendorEquipmentModel? match;
    if (sName.contains('phao chuối') || sName.contains('banana')) {
      match = _warehouseEquipment.firstWhere(
        (e) =>
            e.name.toLowerCase().contains('phao chuối') ||
            e.name.toLowerCase().contains('banana'),
        orElse: () => _warehouseEquipment.firstWhere(
          (e) => e.name.toLowerCase().contains('cano'),
          orElse: () => _warehouseEquipment.first,
        ),
      );
    } else if (sName.contains('mô tô') || sName.contains('jetski')) {
      match = _warehouseEquipment.firstWhere(
        (e) =>
            e.name.toLowerCase().contains('mô tô') ||
            e.compatibilityGroup == 'JET_SKI',
        orElse: () => _warehouseEquipment.first,
      );
    } else if (sName.contains('sup') || sName.contains('kayak')) {
      match = _warehouseEquipment.firstWhere(
        (e) =>
            e.compatibilityGroup == 'SUP' ||
            e.name.toLowerCase().contains('sup'),
        orElse: () => _warehouseEquipment.first,
      );
    }

    if (match != null) {
      _assignedEquipment.add(match);
      _capacity = match.capacity;
    }
  }

  Future<void> _detectServicePackageMode() async {
    setState(() {
      _isLoadingOptions = true;
      _optionsLoadError = null;
    });

    try {
      final opts = await VendorServiceRepository().getServiceOptions(
        widget.serviceId,
      );
      if (!mounted) return;

      setState(() {
        _serviceOptions = opts;
        _isLoadingOptions = false;
        _optionsLoadError = null;

        if (!_isEdit) {
          final activeOpts = _serviceOptions
              .where((o) => o.status == 'ACTIVE')
              .toList();
          final hasActivePrivate = activeOpts.any(
            (o) => o.optionType == 'PRIVATE' || o.pricingUnit == 'PER_PACKAGE',
          );

          if (hasActivePrivate) {
            // Dịch vụ có gói bao trọn đang hoạt động: bắt buộc dùng SHARED_CAPACITY_UNITS
            _inventoryType = 'SHARED_CAPACITY_UNITS';
            if (_assignedEquipment.isEmpty) {
              _autoAssignEquipmentForService();
            }
          } else if (!_userManuallySelectedInventoryType) {
            // Dịch vụ chỉ có gói ghép (hoặc chưa có gói): mặc định PERSON_LIMIT nếu user chưa tự chọn
            _inventoryType = 'PERSON_LIMIT';
          }
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoadingOptions = false;
        _optionsLoadError = e.toString();
      });
    }
  }

  Future<void> _loadWarehouseEquipment() async {
    setState(() => _isLoadingEquipment = true);
    try {
      final items = await _equipmentRepo.listEquipment();
      if (mounted) {
        setState(() {
          _warehouseEquipment = items;
          _isLoadingEquipment = false;
          if (_inventoryType == 'SHARED_CAPACITY_UNITS' &&
              _assignedEquipment.isEmpty &&
              !_isEdit) {
            _autoAssignEquipmentForService();
          }
          if (_isEdit && _editingUnits.isNotEmpty) {
            for (final u in _editingUnits) {
              final eqId = u['equipmentId']?.toString();
              if (eqId != null && eqId.isNotEmpty) {
                final match = items.firstWhere(
                  (eq) => eq.id == eqId,
                  orElse: () => VendorEquipmentModel(
                    id: eqId,
                    name: 'Thiết bị #${u['unitNumber']}',
                    compatibilityGroup: 'VESSEL',
                    capacity: (u['capacity'] as num?)?.toInt() ?? 2,
                  ),
                );
                u['equipmentName'] = match.name;
              }
            }
          }
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoadingEquipment = false);
    }
  }

  Future<void> _openAssignEquipmentSheet() async {
    if (_warehouseEquipment.isEmpty && _isLoadingEquipment) {
      ToastNotification.showInfo(
        context,
        'Đang tải danh sách thiết bị từ kho...',
      );
      return;
    }

    if (_warehouseEquipment.isEmpty) {
      ToastNotification.showInfo(
        context,
        'Kho hiện chưa có thiết bị nào. Vui lòng thêm thiết bị trong Quản lý thiết bị.',
      );
      return;
    }

    final selectedIds = _assignedEquipment.map((e) => e.id).toSet();

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final selectedList = _warehouseEquipment
                .where((e) => selectedIds.contains(e.id))
                .toList();
            final totalCap = selectedList.fold<int>(
              0,
              (sum, e) => sum + e.capacity,
            );

            return Container(
              height: MediaQuery.of(context).size.height * 0.75,
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                children: [
                  Center(
                    child: Container(
                      margin: const EdgeInsets.only(top: 10, bottom: 8),
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: const Color(0xFFCBD5E1),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 12,
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Gán phương tiện từ kho',
                              style: TextStyle(
                                fontSize: 17,
                                fontWeight: FontWeight.bold,
                                color: AppColors.onSurface,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Đã chọn ${selectedIds.length} phương tiện • Tổng $totalCap chỗ',
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                        IconButton(
                          icon: const Icon(Icons.close, size: 20),
                          onPressed: () => Navigator.of(ctx).pop(),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: _warehouseEquipment.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 10),
                      itemBuilder: (context, index) {
                        final eq = _warehouseEquipment[index];
                        final isSelected = selectedIds.contains(eq.id);

                        return InkWell(
                          onTap: () {
                            setModalState(() {
                              if (isSelected) {
                                selectedIds.remove(eq.id);
                              } else {
                                selectedIds.add(eq.id);
                              }
                            });
                          },
                          borderRadius: BorderRadius.circular(14),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 12,
                            ),
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? const Color(0xFFF0FDFA)
                                  : AppColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(
                                color: isSelected
                                    ? const Color(0xFF0F766E)
                                    : const Color(0xFFE2E8F0),
                                width: isSelected ? 1.5 : 1,
                              ),
                            ),
                            child: Row(
                              children: [
                                Checkbox(
                                  value: isSelected,
                                  activeColor: const Color(0xFF0F766E),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  onChanged: (val) {
                                    setModalState(() {
                                      if (val == true) {
                                        selectedIds.add(eq.id);
                                      } else {
                                        selectedIds.remove(eq.id);
                                      }
                                    });
                                  },
                                ),
                                Container(
                                  width: 36,
                                  height: 36,
                                  decoration: BoxDecoration(
                                    color: isSelected
                                        ? const Color(0xFFCCFBF1)
                                        : const Color(0xFFE0F2FE),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.directions_boat,
                                    size: 18,
                                    color: AppColors.secondary,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        eq.name,
                                        style: const TextStyle(
                                          fontSize: 13.5,
                                          fontWeight: FontWeight.bold,
                                          color: AppColors.onSurface,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        'Sức chứa: ${eq.capacity} khách (Cố định)',
                                        style: const TextStyle(
                                          fontSize: 11.5,
                                          color: AppColors.tertiary,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton(
                        onPressed: () {
                          setState(() {
                            _assignedEquipment.clear();
                            for (final eq in _warehouseEquipment) {
                              if (selectedIds.contains(eq.id)) {
                                _assignedEquipment.add(eq);
                              }
                            }
                            if (_assignedEquipment.isNotEmpty) {
                              _capacity = _assignedEquipment.fold<int>(
                                0,
                                (s, e) => s + e.capacity,
                              );
                            }
                          });
                          Navigator.of(ctx).pop();
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                        ),
                        child: Text(
                          'Xác nhận gán (${selectedIds.length} phương tiện)',
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _openChangeEquipmentDialog(int unitIndex) async {
    final unit = _editingUnits[unitIndex];
    final unitNumber = (unit['unitNumber'] as num?)?.toInt() ?? (unitIndex + 1);
    final unitCapacity = (unit['capacity'] as num?)?.toInt() ?? 2;
    final currentEqId = unit['equipmentId']?.toString();

    final eligibleEquipment = _warehouseEquipment
        .where((eq) => eq.capacity == unitCapacity)
        .toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFCBD5E1),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                const Icon(
                  Icons.swap_horiz_rounded,
                  color: AppColors.secondary,
                  size: 22,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Đổi phương tiện cho Đơn vị #$unitNumber ($unitCapacity chỗ)',
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              'Chọn phương tiện thay thế từ kho có sức chứa $unitCapacity chỗ:',
              style: const TextStyle(fontSize: 12, color: AppColors.tertiary),
            ),
            const SizedBox(height: 14),
            if (eligibleEquipment.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Center(
                  child: Text(
                    'Không có phương tiện nào trong kho có sức chứa $unitCapacity chỗ.',
                    style: const TextStyle(
                      color: AppColors.tertiary,
                      fontSize: 13,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            else
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: MediaQuery.of(context).size.height * 0.45,
                ),
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: eligibleEquipment.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (ctx, idx) {
                    final eq = eligibleEquipment[idx];
                    final isCurrent = eq.id == currentEqId;
                    return InkWell(
                      onTap: () async {
                        Navigator.pop(ctx);
                        await _swapEquipmentForUnit(unitIndex, unitNumber, eq);
                      },
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: isCurrent
                              ? const Color(0xFFF0FDFA)
                              : AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: isCurrent
                                ? const Color(0xFF0F766E)
                                : const Color(0xFFE2E8F0),
                            width: isCurrent ? 1.5 : 1,
                          ),
                        ),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.directions_boat,
                              size: 20,
                              color: AppColors.secondary,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    eq.name,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 13,
                                      color: AppColors.navyAnchor,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${eq.capacity} chỗ • Nhóm: ${eq.compatibilityGroup}',
                                    style: const TextStyle(
                                      fontSize: 11,
                                      color: AppColors.tertiary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            if (isCurrent)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 3,
                                ),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF0F766E),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Text(
                                  'Hiện tại',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              )
                            else
                              const Text(
                                'Chọn',
                                style: TextStyle(
                                  color: AppColors.secondary,
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _swapEquipmentForUnit(
    int unitIndex,
    int unitNumber,
    VendorEquipmentModel newEquipment,
  ) async {
    try {
      await _equipmentRepo.attachEquipmentToSlot(
        equipmentId: newEquipment.id,
        slotId: widget.slot!.slotId,
        unitNumber: unitNumber,
      );
      if (mounted) {
        setState(() {
          _editingUnits[unitIndex]['equipmentId'] = newEquipment.id;
          _editingUnits[unitIndex]['equipmentName'] = newEquipment.name;
        });
        ToastNotification.showSuccess(
          context,
          'Đã đổi sang: ${newEquipment.name} cho Đơn vị #$unitNumber',
        );
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Không thể đổi phương tiện: $e');
      }
    }
  }

  void _showCancelSlotEmergencyDialog() {
    final reasonController = TextEditingController();
    bool isSubmitting = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          title: const Row(
            children: [
              Icon(
                Icons.warning_amber_rounded,
                color: AppColors.error,
                size: 24,
              ),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Hủy ca chạy khẩn cấp',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppColors.error,
                  ),
                ),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Khung giờ: ${widget.slot!.startTime.length >= 5 ? widget.slot!.startTime.substring(0, 5) : widget.slot!.startTime} - ${widget.slot!.endTime.length >= 5 ? widget.slot!.endTime.substring(0, 5) : widget.slot!.endTime} (${widget.slot!.slotDate})',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Số khách bị ảnh hưởng: $_bookedCount khách',
                style: const TextStyle(color: AppColors.tertiary, fontSize: 12),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF2F2),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFFCA5A5)),
                ),
                child: const Text(
                  'Lưu ý: Khi hủy ca, toàn bộ vé đã đặt sẽ tự động bị hủy và hệ thống sẽ tự động kích hoạt hoàn tiền cho du khách.',
                  style: TextStyle(fontSize: 11.5, color: Color(0xFF991B1B)),
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'Lý do hủy (bắt buộc):',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              TextField(
                controller: reasonController,
                maxLines: 3,
                maxLength: 500,
                decoration: InputDecoration(
                  hintText:
                      'Nhập lý do hủy (VD: Biển động sóng to gió lớn, sự cố kỹ thuật...)',
                  hintStyle: const TextStyle(
                    fontSize: 12,
                    color: AppColors.tertiary,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  contentPadding: const EdgeInsets.all(10),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.pop(ctx),
              child: const Text('Đóng'),
            ),
            ElevatedButton(
              onPressed: isSubmitting
                  ? null
                  : () async {
                      final reason = reasonController.text.trim();
                      if (reason.isEmpty) {
                        ToastNotification.showError(
                          ctx,
                          'Vui lòng nhập lý do hủy ca',
                        );
                        return;
                      }
                      setDialogState(() => isSubmitting = true);
                      try {
                        await VendorSlotRepository().cancelSlot(
                          widget.slot!.serviceId,
                          widget.slot!.slotId,
                          reason,
                        );
                        if (!ctx.mounted) return;
                        Navigator.pop(ctx);
                        if (mounted) {
                          ToastNotification.showSuccess(
                            context,
                            'Đã gửi yêu cầu hủy ca khẩn cấp thành công',
                          );
                          Navigator.of(context).maybePop(true);
                        }
                      } catch (e) {
                        setDialogState(() => isSubmitting = false);
                        if (ctx.mounted) {
                          ToastNotification.showError(ctx, 'Lỗi hủy ca: $e');
                        }
                      }
                    },
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.error,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              child: isSubmitting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text('Xác nhận hủy ca'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _unitsController.dispose();
    _dateController.dispose();
    _startTimeController.dispose();
    _endTimeController.dispose();
    super.dispose();
  }

  void _changeCapacity(int delta) {
    final newCap = _capacity + delta;
    if (newCap < _minAllowedCapacity) {
      ToastNotification.showError(
        context,
        'Không thể giảm sức chứa dưới tổng số khách đã đặt & tạm giữ ($_minAllowedCapacity khách)',
      );
      return;
    }
    if (newCap < 1) return;
    setState(() {
      _capacity = newCap;
    });
  }

  Future<void> _pickDate() async {
    final currentDt = DateTime.tryParse(_dateController.text) ?? DateTime.now();
    final picked = await AlarmWheelDateDialog.show(
      context,
      initialDate: currentDt,
      title: 'Chọn ngày áp dụng',
    );
    if (picked != null) {
      setState(() {
        _dateController.text =
            '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
      });
    }
  }

  Future<void> _pickTime({required bool isStart}) async {
    final currentStr = isStart
        ? _startTimeController.text
        : _endTimeController.text;
    final parts = currentStr.split(':');
    final initialHour = parts.isNotEmpty
        ? (int.tryParse(parts[0]) ?? (isStart ? 5 : 7))
        : (isStart ? 5 : 7);
    final initialMinute = parts.length > 1 ? (int.tryParse(parts[1]) ?? 0) : 0;

    final picked = await AlarmWheelTimeDialog.show(
      context,
      initialTime: TimeOfDay(hour: initialHour, minute: initialMinute),
      title: isStart ? 'Chọn giờ bắt đầu' : 'Chọn giờ kết thúc',
      subtitle: isStart
          ? 'Khung giờ bắt đầu ca chạy trải nghiệm'
          : 'Khung giờ kết thúc ca chạy trải nghiệm',
    );

    if (picked != null) {
      final formatted =
          '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
      setState(() {
        if (isStart) {
          _startTimeController.text = formatted;
        } else {
          _endTimeController.text = formatted;
        }
      });
    }
  }

  Future<void> _saveSlot() async {
    final date = _dateController.text.trim();
    final start = _startTimeController.text.trim();
    final end = _endTimeController.text.trim();

    final startParts = start.split(':');
    final endParts = end.split(':');
    if (startParts.length >= 2 && endParts.length >= 2) {
      final sH = int.tryParse(startParts[0]) ?? 0;
      final sM = int.tryParse(startParts[1]) ?? 0;
      final eH = int.tryParse(endParts[0]) ?? 0;
      final eM = int.tryParse(endParts[1]) ?? 0;
      if (sH * 60 + sM >= eH * 60 + eM) {
        ToastNotification.showError(
          context,
          'Giờ bắt đầu ($start) phải trước giờ kết thúc ($end)',
        );
        return;
      }
    }

    final units = <Map<String, dynamic>>[];
    int totalEffectiveCap = _capacity;
    if (_isEdit) {
      if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
        units.addAll(_editingUnits);
        totalEffectiveCap = _capacity;
      }
    } else if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
      if (_assignedEquipment.isEmpty) {
        ToastNotification.showError(
          context,
          'Vui lòng gán ít nhất một phương tiện từ kho cho ca này',
        );
        return;
      }
      for (int i = 0; i < _assignedEquipment.length; i++) {
        units.add({
          'unitNumber': i + 1,
          'capacity': _assignedEquipment[i].capacity,
        });
      }
      totalEffectiveCap = _assignedEquipment.fold<int>(
        0,
        (sum, u) => sum + u.capacity,
      );
    }

    if (totalEffectiveCap < _minAllowedCapacity) {
      ToastNotification.showError(
        context,
        'Sức chứa tối thiểu phải từ $_minAllowedCapacity khách trở lên (đã có $_bookedCount khách đặt)',
      );
      return;
    }

    // Kiểm tra tính phù hợp: nếu có gói hoạt động nhưng không gói nào phù hợp thì chặn lưu
    final activeOpts = _activeOptions;
    if (activeOpts.isNotEmpty) {
      final compatibleCount = activeOpts
          .where((o) => _evaluateOptionCompatibility(o).isCompatible)
          .length;
      if (compatibleCount == 0) {
        ToastNotification.showError(
          context,
          'Cấu hình ca hiện tại không phù hợp với bất kỳ gói bán nào của dịch vụ. Vui lòng điều chỉnh lại sức chứa hoặc phương tiện.',
        );
        return;
      }
    }

    setState(() => _isSaving = true);

    DateTime parsedDate = DateTime.tryParse(date) ?? DateTime.now();

    final newSlot = ServiceSlotModel(
      slotId:
          widget.slot?.slotId ??
          'slt-${DateTime.now().millisecondsSinceEpoch % 10000}',
      serviceId: widget.serviceId,
      date: parsedDate,
      slotDate: date,
      startTime: start.length == 5 ? '$start:00' : start,
      endTime: end.length == 5 ? '$end:00' : end,
      inventoryType: _inventoryType,
      units: units,
      capacity: totalEffectiveCap,
      bookedCount: _bookedCount,
      heldCount: _heldCount,
      isBlocked: _isBlocked,
      createdAt: widget.slot?.createdAt ?? DateTime.now(),
      updatedAt: DateTime.now(),
    );

    try {
      final saved = await _slotRepo.saveSlot(newSlot);
      if (!_isEdit && _inventoryType == 'SHARED_CAPACITY_UNITS') {
        for (int i = 0; i < _assignedEquipment.length; i++) {
          try {
            await _equipmentRepo.attachEquipmentToSlot(
              equipmentId: _assignedEquipment[i].id,
              slotId: saved.id,
              unitNumber: i + 1,
            );
          } catch (_) {}
        }
      }
      if (mounted) {
        ToastNotification.showSuccess(context, 'Đã lưu ca chạy thành công');
        Navigator.of(context).maybePop();
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Lỗi lưu ca chạy: $e');
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _saveBatchSlots() async {
    final start = _startTimeController.text.trim();
    final end = _endTimeController.text.trim();

    final startParts = start.split(':');
    final endParts = end.split(':');
    if (startParts.length >= 2 && endParts.length >= 2) {
      final sH = int.tryParse(startParts[0]) ?? 0;
      final sM = int.tryParse(startParts[1]) ?? 0;
      final eH = int.tryParse(endParts[0]) ?? 0;
      final eM = int.tryParse(endParts[1]) ?? 0;
      if (sH * 60 + sM >= eH * 60 + eM) {
        ToastNotification.showError(
          context,
          'Giờ bắt đầu ($start) phải trước giờ kết thúc ($end)',
        );
        return;
      }
    }

    if (_selectedBatchDays.isEmpty) {
      ToastNotification.showError(
        context,
        'Vui lòng chọn ít nhất 1 thứ trong tuần',
      );
      return;
    }

    final startDate = DateTime.now();
    final endDate = startDate.add(Duration(days: _batchWeeks * 7));

    final units = <Map<String, int>>[];
    int totalEffectiveCap = _capacity;
    if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
      if (_assignedEquipment.isEmpty) {
        ToastNotification.showError(
          context,
          'Vui lòng gán ít nhất một phương tiện từ kho cho ca này',
        );
        return;
      }
      for (int i = 0; i < _assignedEquipment.length; i++) {
        units.add({
          'unitNumber': i + 1,
          'capacity': _assignedEquipment[i].capacity,
        });
      }
      totalEffectiveCap = _assignedEquipment.fold<int>(
        0,
        (sum, u) => sum + u.capacity,
      );
    }

    final activeOpts = _activeOptions;
    if (activeOpts.isNotEmpty) {
      final compatibleCount = activeOpts
          .where((o) => _evaluateOptionCompatibility(o).isCompatible)
          .length;
      if (compatibleCount == 0) {
        ToastNotification.showError(
          context,
          'Cấu hình ca hiện tại không phù hợp với bất kỳ gói bán nào của dịch vụ. Vui lòng điều chỉnh lại sức chứa hoặc phương tiện.',
        );
        return;
      }
    }

    setState(() => _isSaving = true);
    try {
      final created = await _slotRepo.batchCreateSlots(
        serviceId: widget.serviceId,
        startDate: startDate,
        endDate: endDate,
        daysOfWeek: _selectedBatchDays.toList(),
        slotTemplates: [
          {
            'startTime': start.length == 5 ? '$start:00' : start,
            'endTime': end.length == 5 ? '$end:00' : end,
            'inventoryType': _inventoryType,
            'capacity': totalEffectiveCap,
            'units': units,
          },
        ],
      );

      if (_inventoryType == 'SHARED_CAPACITY_UNITS') {
        for (final s in created) {
          for (int i = 0; i < _assignedEquipment.length; i++) {
            try {
              await _equipmentRepo.attachEquipmentToSlot(
                equipmentId: _assignedEquipment[i].id,
                slotId: s.id,
                unitNumber: i + 1,
              );
            } catch (_) {}
          }
        }
      }
      if (mounted) {
        ToastNotification.showSuccess(
          context,
          'Đã tạo thành công ${created.length} ca chạy theo lịch tuần',
        );
        Navigator.of(context).maybePop();
      }
    } catch (e) {
      if (mounted) {
        ToastNotification.showError(context, 'Lỗi tạo ca hàng loạt: $e');
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final service =
        _db.services
            .where((s) => s.serviceId == widget.serviceId)
            .firstOrNull ??
        (_db.services.isNotEmpty ? _db.services.first : null);

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: LocalizedText(
          _isEdit
              ? 'Chỉnh sửa ca chạy'
              : (_creationMode == 1 ? 'Tạo ca theo tuần' : 'Tạo ca đơn'),
          style: const TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 18,
            color: AppColors.onSurface,
          ),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16.0, 16.0, 16.0, 40.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Service Header Info
              _buildServiceHeader(service),
              const SizedBox(height: 16),

              if (_isEdit)
                _buildEditSlotForm(service)
              else if (_creationMode == 1)
                _buildBatchWeeklyForm()
              else ...[
                // CARD 1: Date
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x06102F3A),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Row(
                            children: [
                              Icon(
                                Icons.calendar_today,
                                size: 18,
                                color: AppColors.secondary,
                              ),
                              SizedBox(width: 8),
                              LocalizedText(
                                'Ngày áp dụng',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              InkWell(
                                onTap: _setDateToday,
                                borderRadius: BorderRadius.circular(10),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: _isDateToday
                                        ? AppColors.secondary
                                        : AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: LocalizedText(
                                    'Hôm nay',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      color: _isDateToday
                                          ? Colors.white
                                          : AppColors.tertiary,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 6),
                              InkWell(
                                onTap: _setDateTomorrow,
                                borderRadius: BorderRadius.circular(10),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: _isDateTomorrow
                                        ? AppColors.secondary
                                        : AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: LocalizedText(
                                    'Ngày mai',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      color: _isDateTomorrow
                                          ? Colors.white
                                          : AppColors.tertiary,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      InkWell(
                        onTap: _pickDate,
                        borderRadius: BorderRadius.circular(24),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 12,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(24),
                          ),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.event,
                                size: 20,
                                color: AppColors.tertiary,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  _dateController.text,
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.onSurface,
                                  ),
                                ),
                              ),
                              const Icon(
                                Icons.edit_calendar,
                                size: 18,
                                color: AppColors.secondary,
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFFE2E8F0)),
                        ),
                        child: Row(
                          children: const [
                            Icon(
                              Icons.info_outline,
                              size: 15,
                              color: AppColors.secondary,
                            ),
                            SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Ca chạy này được tạo cho riêng ngày đã chọn. Để cài đặt khung giờ tự động lặp lại cả tuần, cấu hình tại Lịch định kỳ.',
                                style: TextStyle(
                                  fontSize: 11,
                                  color: AppColors.tertiary,
                                  height: 1.3,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // CARD 3: Operating Time
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x06102F3A),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Row(
                            children: [
                              Icon(
                                Icons.schedule,
                                size: 18,
                                color: AppColors.secondary,
                              ),
                              SizedBox(width: 8),
                              LocalizedText(
                                'Khung giờ hoạt động',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ],
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.secondaryContainer.withValues(
                                alpha: 0.6,
                              ),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              'Khung: ${_slotDurationMinutes >= 60 ? "${_slotDurationMinutes ~/ 60}h${_slotDurationMinutes % 60 > 0 ? '${_slotDurationMinutes % 60}p' : ''}" : "$_slotDurationMinutes phút"} • ${service?.durationMinutes ?? 15}p/lượt',
                              style: const TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                color: AppColors.secondary,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const LocalizedText(
                                  'Giờ bắt đầu',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: AppColors.tertiary,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                InkWell(
                                  onTap: () => _pickTime(isStart: true),
                                  borderRadius: BorderRadius.circular(24),
                                  child: Container(
                                    height: 48,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 14,
                                    ),
                                    decoration: BoxDecoration(
                                      color: AppColors.surfaceContainerLow,
                                      borderRadius: BorderRadius.circular(24),
                                    ),
                                    child: Row(
                                      children: [
                                        const Icon(
                                          Icons.wb_twilight,
                                          size: 18,
                                          color: AppColors.secondary,
                                        ),
                                        Expanded(
                                          child: Text(
                                            _startTimeController.text,
                                            textAlign: TextAlign.center,
                                            style: const TextStyle(
                                              fontSize: 18,
                                              fontWeight: FontWeight.bold,
                                              color: AppColors.onSurface,
                                            ),
                                          ),
                                        ),
                                        const Icon(
                                          Icons.unfold_more,
                                          size: 16,
                                          color: AppColors.outline,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const LocalizedText(
                                  'Giờ kết thúc',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: AppColors.tertiary,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                InkWell(
                                  onTap: () => _pickTime(isStart: false),
                                  borderRadius: BorderRadius.circular(24),
                                  child: Container(
                                    height: 48,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 14,
                                    ),
                                    decoration: BoxDecoration(
                                      color: AppColors.surfaceContainerLow,
                                      borderRadius: BorderRadius.circular(24),
                                    ),
                                    child: Row(
                                      children: [
                                        const Icon(
                                          Icons.wb_sunny,
                                          size: 18,
                                          color: Color(0xFF0284C7),
                                        ),
                                        Expanded(
                                          child: Text(
                                            _endTimeController.text,
                                            textAlign: TextAlign.center,
                                            style: const TextStyle(
                                              fontSize: 18,
                                              fontWeight: FontWeight.bold,
                                              color: AppColors.onSurface,
                                            ),
                                          ),
                                        ),
                                        const Icon(
                                          Icons.unfold_more,
                                          size: 16,
                                          color: AppColors.outline,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // CARD 3: Hình thức quản lý & Sức chứa ca chạy
                _buildCapacityAndEquipmentCard(),
                const SizedBox(height: 16),

                // CARD 4: Gói dịch vụ phù hợp với cấu hình
                _buildPackageCompatibilityCard(),
                const SizedBox(height: 16),

                // CARD 5: Tóm tắt trước khi tạo
                _buildSummaryCard(isBatch: false),
                const SizedBox(height: 20),
                // Action button: Tạo ca chạy ngay
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton.icon(
                    onPressed: _isSaving ? null : _saveSlot,
                    icon: _isSaving
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.add_circle_outline, size: 18),
                    label: const Text(
                      'Tạo ca chạy ngay',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 20),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildServiceHeader(dynamic service) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: AppColors.secondaryContainer,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(
                  Icons.kayaking,
                  color: AppColors.onSecondaryContainer,
                  size: 24,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const LocalizedText(
                        'DỊCH VỤ ĐANG CHỌN',
                        style: TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSecondaryContainer,
                        ),
                      ),
                    ),
                    const SizedBox(height: 4),
                    LocalizedText(
                      service?.nameVi ?? 'Dịch vụ trải nghiệm biển',
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(
                          Icons.location_on,
                          size: 12,
                          color: AppColors.secondary,
                        ),
                        const SizedBox(width: 3),
                        Expanded(
                          child: LocalizedText(
                            service?.meetingPointName ?? 'Đà Nẵng',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.tertiary,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: Color(0xFFF1F5F9)),
          const SizedBox(height: 10),
          if (_isLoadingOptions)
            Row(
              children: const [
                SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.secondary,
                  ),
                ),
                SizedBox(width: 8),
                Text(
                  'Đang tải danh sách gói dịch vụ...',
                  style: TextStyle(fontSize: 12, color: AppColors.tertiary),
                ),
              ],
            )
          else if (_optionsLoadError != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFFFEF2F2),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFFCA5A5)),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.error_outline,
                    size: 16,
                    color: AppColors.error,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Lỗi tải gói dịch vụ: $_optionsLoadError',
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF991B1B),
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: _detectServicePackageMode,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.error),
                      ),
                      child: const Text(
                        'Thử lại',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: AppColors.error,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            )
          else if (_activeOptions.isEmpty)
            Row(
              children: const [
                Icon(Icons.info_outline, size: 14, color: AppColors.tertiary),
                SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Dịch vụ chưa có gói bán cấu hình riêng. Ca áp dụng cấu hình sức chứa chung.',
                    style: TextStyle(fontSize: 11.5, color: AppColors.tertiary),
                  ),
                ),
              ],
            )
          else ...[
            Row(
              children: [
                const Icon(
                  Icons.local_offer_outlined,
                  size: 14,
                  color: AppColors.secondary,
                ),
                const SizedBox(width: 6),
                Text(
                  'Các gói đang mở bán (${_activeOptions.length} gói tham khảo):',
                  style: const TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: _activeOptions.map((opt) {
                final isPrivate =
                    opt.optionType == 'PRIVATE' ||
                    opt.pricingUnit == 'PER_PACKAGE';
                return Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: isPrivate
                        ? const Color(0xFFFFFBEB)
                        : const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isPrivate
                          ? const Color(0xFFFCD34D)
                          : const Color(0xFFBFDBFE),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        isPrivate ? Icons.directions_boat : Icons.groups,
                        size: 13,
                        color: isPrivate
                            ? const Color(0xFFB45309)
                            : const Color(0xFF1D4ED8),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        opt.name,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: isPrivate
                              ? const Color(0xFF92400E)
                              : const Color(0xFF1E40AF),
                        ),
                      ),
                      Text(
                        isPrivate
                            ? ' • Bao trọn (≤${opt.maxPaxPerPackage ?? 1} khách)'
                            : ' • Ghép',
                        style: TextStyle(
                          fontSize: 10.5,
                          color: isPrivate
                              ? const Color(0xFFB45309)
                              : const Color(0xFF2563EB),
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildCapacityAndEquipmentCard() {
    final isUnits = _inventoryType == 'SHARED_CAPACITY_UNITS';
    final isEnforced = _isPrivateRequirementEnforced;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(
                    isUnits
                        ? Icons.directions_boat_outlined
                        : Icons.groups_outlined,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 8),
                  const LocalizedText(
                    'Cấu hình sức chứa ca chạy',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isUnits
                      ? const Color(0xFFF0FDFA)
                      : const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isUnits
                        ? const Color(0xFF99F6E4)
                        : const Color(0xFFBFDBFE),
                  ),
                ),
                child: Text(
                  isUnits ? 'Theo từng phương tiện' : 'Theo tổng số khách',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: isUnits
                        ? AppColors.secondary
                        : const Color(0xFF2563EB),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Chọn cách quản lý sức chứa phù hợp với quy trình vận hành của dịch vụ:',
            style: TextStyle(fontSize: 11.5, color: AppColors.tertiary),
          ),
          const SizedBox(height: 12),

          // Enforced private package notice
          if (isEnforced) ...[
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFFEF3C7),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFFCD34D)),
              ),
              child: const Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.info_outline, size: 16, color: Color(0xFFB45309)),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Gói bao trọn cần một phương tiện/thiết bị riêng cho mỗi lượt đặt.',
                      style: TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF92400E),
                        height: 1.3,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Segmented selector for capacity management mode
          Container(
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Row(
              children: [
                // Option 1: Theo tổng số khách (PERSON_LIMIT)
                Expanded(
                  child: InkWell(
                    onTap: () {
                      if (_isEdit) {
                        ToastNotification.showInfo(
                          context,
                          'Không thể đổi loại sức chứa của ca đã lưu.',
                        );
                        return;
                      }
                      if (isEnforced) {
                        ToastNotification.showInfo(
                          context,
                          'Dịch vụ có gói bao trọn đang hoạt động. Gói bao trọn cần một phương tiện/thiết bị riêng cho mỗi lượt đặt.',
                        );
                        return;
                      }
                      setState(() {
                        _userManuallySelectedInventoryType = true;
                        _inventoryType = 'PERSON_LIMIT';
                      });
                    },
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: !isUnits ? Colors.white : Colors.transparent,
                        borderRadius: BorderRadius.circular(10),
                        boxShadow: !isUnits
                            ? const [
                                BoxShadow(
                                  color: Color(0x0A000000),
                                  blurRadius: 4,
                                  offset: Offset(0, 1),
                                ),
                              ]
                            : null,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.groups,
                            size: 16,
                            color: !isUnits
                                ? const Color(0xFF2563EB)
                                : AppColors.tertiary,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'Theo tổng số khách',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: !isUnits
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                              color: !isUnits
                                  ? const Color(0xFF2563EB)
                                  : AppColors.tertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 4),

                // Option 2: Theo từng phương tiện / thiết bị (SHARED_CAPACITY_UNITS)
                Expanded(
                  child: InkWell(
                    onTap: () {
                      if (_isEdit) {
                        ToastNotification.showInfo(
                          context,
                          'Không thể đổi loại sức chứa của ca đã lưu.',
                        );
                        return;
                      }
                      setState(() {
                        _userManuallySelectedInventoryType = true;
                        _inventoryType = 'SHARED_CAPACITY_UNITS';
                        if (_assignedEquipment.isEmpty) {
                          _autoAssignEquipmentForService();
                        }
                      });
                    },
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: isUnits ? Colors.white : Colors.transparent,
                        borderRadius: BorderRadius.circular(10),
                        boxShadow: isUnits
                            ? const [
                                BoxShadow(
                                  color: Color(0x0A000000),
                                  blurRadius: 4,
                                  offset: Offset(0, 1),
                                ),
                              ]
                            : null,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.directions_boat,
                            size: 16,
                            color: isUnits
                                ? AppColors.secondary
                                : AppColors.tertiary,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'Theo từng phương tiện',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: isUnits
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                              color: isUnits
                                  ? AppColors.secondary
                                  : AppColors.tertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Detail inputs based on selected inventoryType
          if (_inventoryType == 'PERSON_LIMIT') ...[
            const LocalizedText(
              'Sức chứa tối đa (khách)',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                InkWell(
                  onTap: () => _changeCapacity(-1),
                  borderRadius: BorderRadius.circular(22),
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.surfaceContainerLow,
                    ),
                    child: const Icon(
                      Icons.remove,
                      color: AppColors.onSurface,
                      size: 20,
                    ),
                  ),
                ),
                Expanded(
                  child: Center(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Text(
                          '$_capacity',
                          style: const TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(width: 4),
                        const LocalizedText(
                          'khách / ca',
                          style: TextStyle(
                            fontSize: 13,
                            color: AppColors.tertiary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                InkWell(
                  onTap: () => _changeCapacity(1),
                  borderRadius: BorderRadius.circular(22),
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.secondaryContainer,
                    ),
                    child: const Icon(
                      Icons.add,
                      color: AppColors.onSecondaryContainer,
                      size: 20,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              'Áp dụng số khách tối đa chung cho ca. Đặt vé ghép sẽ trừ trực tiếp vào số chỗ trống này.',
              style: TextStyle(fontSize: 11, color: AppColors.tertiary),
            ),
          ] else ...[
            // SHARED_CAPACITY_UNITS
            if (_assignedEquipment.isEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(
                  vertical: 20,
                  horizontal: 16,
                ),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Column(
                  children: [
                    const Icon(
                      Icons.directions_boat_outlined,
                      size: 36,
                      color: AppColors.tertiary,
                    ),
                    const SizedBox(height: 8),
                    const LocalizedText(
                      'Chưa gán phương tiện nào cho ca này',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const LocalizedText(
                      'Vui lòng chọn cano / mô tô nước / phao chuối từ kho thiết bị để tính tổng sức chứa ca',
                      style: TextStyle(
                        fontSize: 11.5,
                        color: AppColors.tertiary,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 14),
                    FilledButton.icon(
                      onPressed: _openAssignEquipmentSheet,
                      icon: const Icon(Icons.add, size: 16),
                      label: const LocalizedText('Gán phương tiện từ kho'),
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.secondary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  LocalizedText(
                    'Phương tiện vận hành (${_assignedEquipment.length})',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0FDFA),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: const Color(0xFF99F6E4)),
                    ),
                    child: Text(
                      'Tổng: ${_assignedEquipment.fold<int>(0, (s, e) => s + e.capacity)} khách',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              ..._assignedEquipment.asMap().entries.map((entry) {
                final idx = entry.key;
                final eq = entry.value;
                return Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 36,
                        height: 36,
                        decoration: const BoxDecoration(
                          color: Color(0xFFE0F2FE),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.directions_boat,
                          size: 18,
                          color: AppColors.secondary,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              eq.name,
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: AppColors.onSurface,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Sức chứa: ${eq.capacity} khách (Cố định)',
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.tertiary,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(
                          Icons.close,
                          size: 18,
                          color: AppColors.error,
                        ),
                        onPressed: () =>
                            setState(() => _assignedEquipment.removeAt(idx)),
                        tooltip: 'Gỡ thiết bị',
                      ),
                    ],
                  ),
                );
              }),
              const SizedBox(height: 6),
              OutlinedButton.icon(
                onPressed: _openAssignEquipmentSheet,
                icon: const Icon(Icons.add, size: 16),
                label: const LocalizedText(
                  'Gán thêm phương tiện từ kho',
                  style: TextStyle(fontSize: 12.5),
                ),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.secondary,
                  side: const BorderSide(color: Color(0xFF99F6E4)),
                  backgroundColor: const Color(0xFFF0FDFA),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildPackageCompatibilityCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(
                Icons.rule_folder_outlined,
                size: 18,
                color: AppColors.secondary,
              ),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Gói dịch vụ phù hợp với cấu hình',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          const Text(
            'Hệ thống tự động đánh giá tính tương thích giữa cấu hình ca và các gói mở bán:',
            style: TextStyle(fontSize: 11.5, color: AppColors.tertiary),
          ),
          const SizedBox(height: 12),
          if (_isLoadingOptions)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.secondary,
                  ),
                ),
              ),
            )
          else if (_optionsLoadError != null)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFFEF2F2),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFFCA5A5)),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.error_outline,
                    size: 16,
                    color: AppColors.error,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Không thể tải gói dịch vụ: $_optionsLoadError',
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF991B1B),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: _detectServicePackageMode,
                    child: const Text(
                      'Thử lại',
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                ],
              ),
            )
          else if (_activeOptions.isEmpty)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Row(
                children: [
                  Icon(Icons.info_outline, size: 16, color: AppColors.tertiary),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Dịch vụ chưa có gói bán cấu hình riêng. Ca áp dụng sức chứa cấu hình ở trên.',
                      style: TextStyle(
                        fontSize: 11.5,
                        color: AppColors.tertiary,
                      ),
                    ),
                  ),
                ],
              ),
            )
          else ...[
            ..._activeOptions.map((opt) {
              final eval = _evaluateOptionCompatibility(opt);
              final isCompat = eval.isCompatible;

              return Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: isCompat
                      ? const Color(0xFFF0FDF4)
                      : const Color(0xFFFFFBEB),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isCompat
                        ? const Color(0xFFBBF7D0)
                        : const Color(0xFFFDE68A),
                  ),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      isCompat
                          ? Icons.check_circle
                          : Icons.warning_amber_rounded,
                      size: 16,
                      color: isCompat
                          ? const Color(0xFF16A34A)
                          : const Color(0xFFD97706),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  opt.name,
                                  style: const TextStyle(
                                    fontSize: 12.5,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.onSurface,
                                  ),
                                ),
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 6,
                                  vertical: 2,
                                ),
                                decoration: BoxDecoration(
                                  color: isCompat
                                      ? const Color(0xFFDCFCE7)
                                      : const Color(0xFFFEF3C7),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  isCompat ? 'Phù hợp' : 'Chưa phù hợp',
                                  style: TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                    color: isCompat
                                        ? const Color(0xFF15803D)
                                        : const Color(0xFFB45309),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 3),
                          Text(
                            eval.reason,
                            style: TextStyle(
                              fontSize: 11,
                              color: isCompat
                                  ? const Color(0xFF15803D)
                                  : const Color(0xFFB45309),
                              height: 1.25,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
            const SizedBox(height: 4),
            const Row(
              children: [
                Icon(Icons.info_outline, size: 12, color: AppColors.tertiary),
                SizedBox(width: 4),
                Expanded(
                  child: Text(
                    'Đánh giá cấu hình ca chạy (không phải cam kết chỗ trống thời gian thực)',
                    style: TextStyle(
                      fontSize: 10.5,
                      color: AppColors.tertiary,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildSummaryCard({bool isBatch = false}) {
    final activeOpts = _activeOptions;
    final compatibleCount = activeOpts
        .where((o) => _evaluateOptionCompatibility(o).isCompatible)
        .length;
    final totalExpectedSlots = _selectedBatchDays.length * _batchWeeks;
    final hasBothTypes =
        activeOpts.any((o) => o.optionType == 'PRIVATE') &&
        activeOpts.any((o) => o.optionType == 'SHARED');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06102F3A),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(
                Icons.summarize_outlined,
                size: 18,
                color: AppColors.secondary,
              ),
              SizedBox(width: 8),
              Text(
                'Tóm tắt ca chạy',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Row 1: Thời gian / Lịch
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.schedule, size: 15, color: AppColors.tertiary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  isBatch
                      ? 'Dự kiến $totalExpectedSlots ca (${_selectedBatchDays.length} thứ/tuần × $_batchWeeks tuần) • ${_startTimeController.text} - ${_endTimeController.text}'
                      : '${_dateController.text} • ${_startTimeController.text} - ${_endTimeController.text}',
                  style: const TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Row 2: Cách quản lý sức chứa
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                _inventoryType == 'SHARED_CAPACITY_UNITS'
                    ? Icons.directions_boat
                    : Icons.groups,
                size: 15,
                color: AppColors.tertiary,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _inventoryType == 'SHARED_CAPACITY_UNITS'
                      ? 'Quản lý theo từng phương tiện/thiết bị (${_effectiveUnitCapacities.length} đơn vị • Tổng $_effectiveTotalCapacity khách)'
                      : 'Quản lý theo tổng số khách ($_capacity khách tối đa)',
                  style: const TextStyle(
                    fontSize: 12.5,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),

          // Row 3: Gói bán phù hợp
          if (activeOpts.isNotEmpty) ...[
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(
                  Icons.check_circle_outline,
                  size: 15,
                  color: Color(0xFF16A34A),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '$compatibleCount / ${activeOpts.length} gói mở bán phù hợp với cấu hình ca này',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: compatibleCount > 0
                          ? const Color(0xFF15803D)
                          : AppColors.error,
                    ),
                  ),
                ),
              ],
            ),
          ],

          // Shared & Private explanation if service serves both
          if (hasBothTypes) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: const Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 14,
                    color: AppColors.secondary,
                  ),
                  SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Các gói dùng chung sức chứa của ca. Đặt ghép sử dụng số chỗ; đặt bao trọn sử dụng toàn bộ một phương tiện/thiết bị.',
                      style: TextStyle(
                        fontSize: 11,
                        color: Color(0xFF334155),
                        height: 1.3,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildBatchWeeklyForm() {
    final days = [
      {'key': 'MONDAY', 'label': 'T2'},
      {'key': 'TUESDAY', 'label': 'T3'},
      {'key': 'WEDNESDAY', 'label': 'T4'},
      {'key': 'THURSDAY', 'label': 'T5'},
      {'key': 'FRIDAY', 'label': 'T6'},
      {'key': 'SATURDAY', 'label': 'T7'},
      {'key': 'SUNDAY', 'label': 'CN'},
    ];
    final totalExpectedSlots = _selectedBatchDays.length * _batchWeeks;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // CARD B1: Chọn các thứ
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06102F3A),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.calendar_view_week,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  SizedBox(width: 8),
                  Text(
                    'Chọn các thứ áp dụng',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: days.map((d) {
                  final key = d['key']!;
                  final isSel = _selectedBatchDays.contains(key);
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        if (isSel) {
                          if (_selectedBatchDays.length > 1)
                            _selectedBatchDays.remove(key);
                        } else {
                          _selectedBatchDays.add(key);
                        }
                      });
                    },
                    child: Container(
                      width: 40,
                      height: 40,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: isSel
                            ? AppColors.secondary
                            : AppColors.surfaceContainerLow,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSel
                              ? AppColors.secondary
                              : const Color(0xFFE2E8F0),
                        ),
                      ),
                      child: Text(
                        d['label']!,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: isSel
                              ? Colors.white
                              : AppColors.onSurfaceVariant,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  ActionChip(
                    label: const Text(
                      'Cả tuần',
                      style: TextStyle(fontSize: 11),
                    ),
                    backgroundColor: AppColors.surfaceContainerLow,
                    onPressed: () => setState(
                      () =>
                          _selectedBatchDays.addAll(days.map((e) => e['key']!)),
                    ),
                  ),
                  ActionChip(
                    label: const Text(
                      'T2 - T6',
                      style: TextStyle(fontSize: 11),
                    ),
                    backgroundColor: AppColors.surfaceContainerLow,
                    onPressed: () => setState(() {
                      _selectedBatchDays.clear();
                      _selectedBatchDays.addAll([
                        'MONDAY',
                        'TUESDAY',
                        'WEDNESDAY',
                        'THURSDAY',
                        'FRIDAY',
                      ]);
                    }),
                  ),
                  ActionChip(
                    label: const Text(
                      'Cuối tuần (T7, CN)',
                      style: TextStyle(fontSize: 11),
                    ),
                    backgroundColor: AppColors.surfaceContainerLow,
                    onPressed: () => setState(() {
                      _selectedBatchDays.clear();
                      _selectedBatchDays.addAll(['SATURDAY', 'SUNDAY']);
                    }),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // CARD B2: Khoảng thời gian
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06102F3A),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.date_range_outlined,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  SizedBox(width: 8),
                  Text(
                    'Thời gian áp dụng',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [1, 2, 4].map((w) {
                  final isSel = _batchWeeks == w;
                  return Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4.0),
                      child: OutlinedButton(
                        onPressed: () => setState(() => _batchWeeks = w),
                        style: OutlinedButton.styleFrom(
                          backgroundColor: isSel
                              ? const Color(0xFFF0FDFA)
                              : AppColors.surfaceContainerLow,
                          foregroundColor: isSel
                              ? AppColors.secondary
                              : AppColors.onSurfaceVariant,
                          side: BorderSide(
                            color: isSel
                                ? AppColors.secondary
                                : Colors.transparent,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          padding: const EdgeInsets.symmetric(vertical: 10),
                        ),
                        child: Text(
                          '$w tuần tới',
                          style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: isSel
                                ? FontWeight.bold
                                : FontWeight.normal,
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // CARD B3: Khung giờ chạy
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06102F3A),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.schedule, size: 18, color: AppColors.secondary),
                  SizedBox(width: 8),
                  Text(
                    'Khung giờ chạy mỗi ca',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: InkWell(
                      onTap: () => _pickTime(isStart: true),
                      child: Container(
                        height: 48,
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.wb_twilight,
                              size: 18,
                              color: AppColors.secondary,
                            ),
                            Expanded(
                              child: Text(
                                _startTimeController.text,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  fontSize: 17,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: InkWell(
                      onTap: () => _pickTime(isStart: false),
                      child: Container(
                        height: 48,
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.wb_sunny,
                              size: 18,
                              color: Color(0xFF0284C7),
                            ),
                            Expanded(
                              child: Text(
                                _endTimeController.text,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  fontSize: 17,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // CARD B4: Hình thức quản lý & Sức chứa ca chạy
        _buildCapacityAndEquipmentCard(),
        const SizedBox(height: 16),

        // CARD B5: Gói dịch vụ phù hợp với cấu hình
        _buildPackageCompatibilityCard(),
        const SizedBox(height: 16),

        // CARD B6: Tóm tắt trước khi tạo
        _buildSummaryCard(isBatch: true),
        const SizedBox(height: 20),

        // Batch Create Button
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton.icon(
            onPressed: _isSaving ? null : _saveBatchSlots,
            icon: _isSaving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.auto_awesome, size: 18),
            label: Text(
              'Tạo hàng loạt $totalExpectedSlots ca chạy',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.secondary,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
        const SizedBox(height: 20),
      ],
    );
  }

  Widget _buildEditSlotForm(dynamic service) {
    final slot = widget.slot!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // CARD 1: Thông tin ca tóm tắt (Giống Web: Thời gian, Số khách đã đặt)
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06102F3A),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  SizedBox(width: 8),
                  LocalizedText(
                    'Thông tin ca vận hành',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  const Icon(
                    Icons.schedule,
                    size: 16,
                    color: AppColors.tertiary,
                  ),
                  const SizedBox(width: 8),
                  LocalizedText(
                    'Thời gian: ${slot.startTime.length >= 5 ? slot.startTime.substring(0, 5) : slot.startTime} - ${slot.endTime.length >= 5 ? slot.endTime.substring(0, 5) : slot.endTime} (${slot.slotDate})',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(
                    Icons.people_outline,
                    size: 16,
                    color: Color(0xFF059669),
                  ),
                  const SizedBox(width: 8),
                  LocalizedText(
                    'Số khách đã đặt: $_bookedCount khách',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF059669),
                    ),
                  ),
                ],
              ),
              if (_inventoryType == 'SHARED_CAPACITY_UNITS') ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(
                      Icons.directions_boat_outlined,
                      size: 16,
                      color: AppColors.secondary,
                    ),
                    const SizedBox(width: 8),
                    LocalizedText(
                      'Sức chứa thiết bị cố định: $_capacity khách',
                      style: const TextStyle(
                        fontSize: 12.5,
                        color: AppColors.tertiary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),

        // CARD 2: Trạng thái ca vận hành (OPEN / CLOSED)
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06102F3A),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.toggle_on_outlined,
                    size: 20,
                    color: AppColors.secondary,
                  ),
                  SizedBox(width: 8),
                  LocalizedText(
                    'Trạng thái ca vận hành',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<bool>(
                initialValue: _isBlocked,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: AppColors.surfaceContainerLow,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 12,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                    borderSide: BorderSide.none,
                  ),
                ),
                items: const [
                  DropdownMenuItem(
                    value: false,
                    child: LocalizedText('Mở đón khách'),
                  ),
                  DropdownMenuItem(
                    value: true,
                    child: LocalizedText('Tạm đóng ca'),
                  ),
                ],
                onChanged: (val) {
                  if (val != null) setState(() => _isBlocked = val);
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // CARD 3A: Danh sách phương tiện vận hành trong ca (khi là SHARED_CAPACITY_UNITS)
        if (_inventoryType == 'SHARED_CAPACITY_UNITS' &&
            _editingUnits.isNotEmpty) ...[
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE2E8F0)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x06102F3A),
                  blurRadius: 8,
                  offset: Offset(0, 2),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Row(
                      children: [
                        Icon(
                          Icons.directions_boat,
                          size: 18,
                          color: AppColors.secondary,
                        ),
                        SizedBox(width: 8),
                        LocalizedText(
                          'Phương tiện đang chạy ca',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFE0F2FE),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: LocalizedText(
                        '${_editingUnits.length} phương tiện',
                        style: const TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0369A1),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _editingUnits.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (ctx, idx) {
                    final u = _editingUnits[idx];
                    final uNum =
                        (u['unitNumber'] as num?)?.toInt() ?? (idx + 1);
                    final uCap = (u['capacity'] as num?)?.toInt() ?? 2;
                    final eqId = u['equipmentId']?.toString();

                    String displayName = u['equipmentName']?.toString() ?? '';
                    if (displayName.isEmpty &&
                        eqId != null &&
                        eqId.isNotEmpty) {
                      final matchedEq = _warehouseEquipment.firstWhere(
                        (e) => e.id == eqId,
                        orElse: () => VendorEquipmentModel(
                          id: eqId,
                          name: '',
                          compatibilityGroup: '',
                          capacity: uCap,
                        ),
                      );
                      if (matchedEq.name.isNotEmpty) {
                        displayName = matchedEq.name;
                      }
                    }
                    if (displayName.isEmpty) {
                      displayName = 'Mô tô nước #$uNum';
                    }

                    return Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: const Color(0xFFE0F2FE),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Icon(
                              Icons.directions_boat_filled,
                              size: 18,
                              color: Color(0xFF0369A1),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                LocalizedText(
                                  '$displayName ($uCap chỗ)',
                                  style: const TextStyle(
                                    fontSize: 13,
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.navyAnchor,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                LocalizedText(
                                  eqId != null
                                      ? 'Đã gán phương tiện từ kho'
                                      : 'Chưa liên kết phương tiện cụ thể',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: eqId != null
                                        ? const Color(0xFF059669)
                                        : AppColors.tertiary,
                                    fontWeight: eqId != null
                                        ? FontWeight.w600
                                        : FontWeight.normal,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          OutlinedButton.icon(
                            onPressed: () => _openChangeEquipmentDialog(idx),
                            icon: const Icon(Icons.swap_horiz, size: 14),
                            label: LocalizedText(
                              eqId != null ? 'Đổi' : 'Gán',
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppColors.secondary,
                              side: const BorderSide(
                                color: AppColors.secondary,
                              ),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 4,
                              ),
                              visualDensity: VisualDensity.compact,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        // CARD 3: Chỉ cho sửa sức chứa nếu là PERSON_LIMIT (Theo đầu người)
        if (_inventoryType == 'PERSON_LIMIT') ...[
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE2E8F0)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x06102F3A),
                  blurRadius: 8,
                  offset: Offset(0, 2),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.groups, size: 18, color: AppColors.secondary),
                    SizedBox(width: 8),
                    LocalizedText(
                      'Tổng sức chứa mới',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: AppColors.onSurface,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    InkWell(
                      onTap: () => _changeCapacity(-1),
                      child: Container(
                        width: 44,
                        height: 44,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.surfaceContainerLow,
                        ),
                        child: const Icon(Icons.remove, size: 20),
                      ),
                    ),
                    Expanded(
                      child: Center(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.baseline,
                          textBaseline: TextBaseline.alphabetic,
                          children: [
                            Text(
                              '$_capacity',
                              style: const TextStyle(
                                fontSize: 26,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(width: 4),
                            const LocalizedText(
                              'khách',
                              style: TextStyle(
                                fontSize: 13,
                                color: AppColors.tertiary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    InkWell(
                      onTap: () => _changeCapacity(1),
                      child: Container(
                        width: 44,
                        height: 44,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.secondaryContainer,
                        ),
                        child: const Icon(
                          Icons.add,
                          size: 20,
                          color: AppColors.onSecondaryContainer,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                LocalizedText(
                  'Không thể đặt nhỏ hơn số khách đã đặt ($_bookedCount khách).',
                  style: const TextStyle(
                    fontSize: 11,
                    color: AppColors.tertiary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        const SizedBox(height: 16),
        _buildPackageCompatibilityCard(),
        const SizedBox(height: 16),
        _buildSummaryCard(isBatch: false),
        const SizedBox(height: 20),

        // Action Button: Lưu thay đổi ca chạy
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton.icon(
            onPressed: _isSaving ? null : _saveSlot,
            icon: _isSaving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.save_outlined, size: 18),
            label: const LocalizedText(
              'Lưu thay đổi ca chạy',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.secondary,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // Action Button: Hủy ca chạy khẩn cấp
        SizedBox(
          width: double.infinity,
          height: 46,
          child: OutlinedButton.icon(
            onPressed: _showCancelSlotEmergencyDialog,
            icon: const Icon(
              Icons.warning_amber_rounded,
              size: 18,
              color: AppColors.error,
            ),
            label: const LocalizedText(
              'Hủy ca chạy khẩn cấp (Hoàn tiền khách)',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: AppColors.error,
              ),
            ),
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: AppColors.error),
              foregroundColor: AppColors.error,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
        ),
        const SizedBox(height: 20),
      ],
    );
  }
}
