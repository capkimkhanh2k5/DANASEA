import 'package:flutter/material.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/widgets/toast_notification.dart';

class VendorEquipmentScreen extends StatefulWidget {
  const VendorEquipmentScreen({super.key});

  @override
  State<VendorEquipmentScreen> createState() => _VendorEquipmentScreenState();
}

class _VendorEquipmentScreenState extends State<VendorEquipmentScreen> {
  final _repo = VendorEquipmentRepository();
  bool _isLoading = true;
  List<VendorEquipmentModel> _equipmentList = [];
  final Map<String, EquipmentMaintenanceResultModel> _latestMaintenance = {};

  @override
  void initState() {
    super.initState();
    _loadEquipment();
  }

  Future<void> _loadEquipment() async {
    setState(() => _isLoading = true);
    final items = await _repo.listEquipment();
    if (mounted) {
      setState(() {
        _equipmentList = items;
        _isLoading = false;
      });
    }
  }

  Future<void> _openCreateEquipmentSheet() async {
    final nameCtrl = TextEditingController();
    final capCtrl = TextEditingController(text: '2');
    String group = 'WATER_SPORTS';
    bool saving = false;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 16,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + MediaQuery.of(ctx).padding.bottom + 20,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const Text(
                  'Đăng ký thiết bị mới (Cano / Kayak / SUP)',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(
                    labelText: 'Tên / Mã định danh thiết bị *',
                    hintText: 'VD: Kayak đôi #01, Cano Sơn Trà #02, Bộ SUP #05',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<String>(
                  value: group,
                  decoration: InputDecoration(
                    labelText: 'Nhóm tương thích (Compatibility Group)',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                  items: const [
                    DropdownMenuItem(value: 'WATER_SPORTS', child: Text('SUP / Kayak (WATER_SPORTS)')),
                    DropdownMenuItem(value: 'BOAT_CRUISE', child: Text('Cano / Tàu du lịch (BOAT_CRUISE)')),
                    DropdownMenuItem(value: 'DIVING', child: Text('Thiết bị lặn / Áo phao (DIVING)')),
                    DropdownMenuItem(value: 'SURFING', child: Text('Ván lướt sóng / Dù bay (SURFING)')),
                  ],
                  onChanged: (v) {
                    if (v != null) setSheetState(() => group = v);
                  },
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: capCtrl,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: 'Sức chứa (Số khách / thiết bị) *',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    onPressed: saving
                        ? null
                        : () async {
                            final name = nameCtrl.text.trim();
                            final cap = int.tryParse(capCtrl.text.trim()) ?? 0;
                            if (name.isEmpty || cap <= 0) {
                              ToastNotification.showError(ctx, 'Vui lòng nhập tên thiết bị và sức chứa > 0');
                              return;
                            }
                            setSheetState(() => saving = true);
                            try {
                              await _repo.createEquipment(
                                name: name,
                                compatibilityGroup: group,
                                capacity: cap,
                              );
                              if (!mounted) return;
                              Navigator.pop(ctx);
                              await _loadEquipment();
                              if (mounted) {
                                ToastNotification.showSuccess(context, 'Đã đăng ký thiết bị mới thành công');
                              }
                            } catch (e) {
                              setSheetState(() => saving = false);
                              if (mounted) {
                                ToastNotification.showError(ctx, 'Lỗi tạo thiết bị: $e');
                              }
                            }
                          },
                    icon: saving
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.add, size: 18),
                    label: const Text('Lưu thiết bị', style: TextStyle(fontWeight: FontWeight.bold)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _openMaintenanceSheet(VendorEquipmentModel equipment) async {
    final now = DateTime.now();
    final startCtrl = TextEditingController(
      text: now.add(const Duration(hours: 1)).toIso8601String().substring(0, 16),
    );
    final endCtrl = TextEditingController(
      text: now.add(const Duration(hours: 5)).toIso8601String().substring(0, 16),
    );
    final reasonCtrl = TextEditingController(text: 'Bảo dưỡng định kỳ thân vỏ & kiểm tra áo phao đi kèm');
    bool urgent = false;
    bool saving = false;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 16,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + MediaQuery.of(ctx).padding.bottom + 20,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 14),
                    decoration: BoxDecoration(
                      color: const Color(0xFFCBD5E1),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Text(
                  'Lập lịch bảo trì: ${equipment.name}',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: startCtrl,
                  decoration: InputDecoration(
                    labelText: 'Bắt đầu (YYYY-MM-DDTHH:mm)',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: endCtrl,
                  decoration: InputDecoration(
                    labelText: 'Kết thúc (YYYY-MM-DDTHH:mm)',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text(
                    'Bảo trì khẩn cấp (Urgent)',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                  ),
                  subtitle: const Text(
                    'Cho phép ghi nhận bảo trì ngay cả khi thiết bị đang gán vào ca sắp chạy',
                    style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                  ),
                  value: urgent,
                  onChanged: (v) => setSheetState(() => urgent = v),
                ),
                const SizedBox(height: 6),
                TextField(
                  controller: reasonCtrl,
                  maxLines: 2,
                  decoration: InputDecoration(
                    labelText: 'Nội dung / Lý do bảo trì *',
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton.icon(
                    onPressed: saving
                        ? null
                        : () async {
                            final startDt = DateTime.tryParse(startCtrl.text.trim());
                            final endDt = DateTime.tryParse(endCtrl.text.trim());
                            final reason = reasonCtrl.text.trim();
                            if (startDt == null || endDt == null || reason.isEmpty) {
                              ToastNotification.showError(ctx, 'Vui lòng nhập thời gian hợp lệ và lý do bảo trì');
                              return;
                            }
                            setSheetState(() => saving = true);
                            try {
                              final res = await _repo.scheduleMaintenance(
                                equipmentId: equipment.id,
                                start: startDt,
                                end: endDt,
                                urgent: urgent,
                                reason: reason,
                              );
                              if (!mounted) return;
                              setState(() {
                                _latestMaintenance[equipment.id] = res;
                              });
                              Navigator.pop(ctx);
                              ToastNotification.showSuccess(
                                context,
                                'Đã ghi nhận lịch bảo trì cho ${equipment.name}',
                              );
                            } catch (e) {
                              setSheetState(() => saving = false);
                              if (mounted) {
                                ToastNotification.showError(ctx, 'Lỗi ghi lịch bảo trì: $e');
                              }
                            }
                          },
                    icon: saving
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.build_circle_outlined, size: 18),
                    label: const Text('Xác nhận lịch bảo trì', style: TextStyle(fontWeight: FontWeight.bold)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const LocalizedText(
          'Quản lý thiết bị & Bảo trì',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
        ),
        actions: [
          IconButton(
            tooltip: 'Làm mới',
            icon: const Icon(Icons.refresh, color: AppColors.secondary),
            onPressed: _loadEquipment,
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openCreateEquipmentSheet,
        backgroundColor: AppColors.secondary,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const LocalizedText('Thêm thiết bị', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: SafeArea(
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : _equipmentList.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24.0),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.directions_boat_filled_outlined, size: 52, color: AppColors.tertiary),
                          const SizedBox(height: 12),
                          const LocalizedText(
                            'Chưa có thiết bị nào được đăng ký (Cano, Kayak, SUP, Áo phao).',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 14, color: AppColors.tertiary),
                          ),
                          const SizedBox(height: 14),
                          ElevatedButton.icon(
                            onPressed: _openCreateEquipmentSheet,
                            icon: const Icon(Icons.add),
                            label: const LocalizedText('Đăng ký thiết bị đầu tiên'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.secondary,
                              foregroundColor: Colors.white,
                            ),
                          ),
                        ],
                      ),
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 80),
                    itemCount: _equipmentList.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final eq = _equipmentList[index];
                      final maint = _latestMaintenance[eq.id];
                      return Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLowest,
                          borderRadius: AppShapes.radiusMd,
                          border: Border.all(color: AppColors.borderSubtle),
                          boxShadow: AppShapes.shadowSm,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Container(
                                  width: 40,
                                  height: 40,
                                  decoration: BoxDecoration(
                                    color: AppColors.secondaryContainer.withValues(alpha: 0.5),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(Icons.kayaking, color: AppColors.secondary, size: 20),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        eq.name,
                                        style: const TextStyle(
                                          fontSize: 15,
                                          fontWeight: FontWeight.bold,
                                          color: AppColors.onSurface,
                                        ),
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        'Nhóm: ${eq.compatibilityGroup} • Sức chứa: ${eq.capacity} khách',
                                        style: const TextStyle(fontSize: 12, color: AppColors.tertiary),
                                      ),
                                    ],
                                  ),
                                ),
                                OutlinedButton.icon(
                                  onPressed: () => _openMaintenanceSheet(eq),
                                  icon: const Icon(Icons.build_outlined, size: 14),
                                  label: const LocalizedText('Bảo trì', style: TextStyle(fontSize: 11.5)),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: AppColors.primary,
                                    side: const BorderSide(color: AppColors.primary),
                                    visualDensity: VisualDensity.compact,
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                                  ),
                                ),
                              ],
                            ),
                            if (maint != null) ...[
                              const SizedBox(height: 10),
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: AppColors.primaryFixed.withValues(alpha: 0.35),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Row(
                                  children: [
                                    const Icon(Icons.handyman_outlined, size: 16, color: AppColors.primary),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Text(
                                        'Lịch bảo trì: ${maint.start} → ${maint.end} • ${maint.reason}',
                                        style: const TextStyle(fontSize: 11.5, color: AppColors.onSurface),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ],
                        ),
                      );
                    },
                  ),
      ),
    );
  }
}
