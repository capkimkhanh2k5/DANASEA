import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/data/catalog_repository.dart';
import '../screens/booking_hold_screen.dart';

/// Availability with a separate hold flow; the existing detail layout is unchanged.
class ServiceAvailabilitySheet extends StatefulWidget {
  const ServiceAvailabilitySheet({super.key, required this.service});
  final ServiceModel service;

  @override
  State<ServiceAvailabilitySheet> createState() =>
      _ServiceAvailabilitySheetState();
}

class _ServiceAvailabilitySheetState extends State<ServiceAvailabilitySheet> {
  String? _optionId;
  late List<CatalogSlot> _slots;
  bool _refreshing = false;
  String? _refreshError;

  @override
  void initState() {
    super.initState();
    _optionId = widget.service.options.firstOrNull?.id;
    _slots = List.of(widget.service.catalogSlots);
  }

  Future<void> _refresh() async {
    if (_refreshing) return;
    final optionId = _optionId;
    setState(() {
      _refreshing = true;
      _refreshError = null;
      _slots.removeWhere(
        (slot) => optionId == null || slot.optionId == optionId,
      );
    });
    try {
      final slots = await CatalogRepository().slots(
        widget.service.id,
        optionId: optionId,
      );
      if (mounted) setState(() => _slots.addAll(slots));
    } catch (error) {
      if (mounted) setState(() => _refreshError = error.toString());
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  Future<void> _openHold(CatalogSlot slot) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => BookingHoldScreen(service: widget.service, slot: slot),
      ),
    );
    if (mounted) await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final slots = _slots
        .where((slot) => _optionId == null || slot.optionId == _optionId)
        .toList();
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: LocalizedText(
                      'Lịch trải nghiệm',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    tooltip: MaterialLocalizations.of(
                      context,
                    ).closeButtonTooltip,
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              Text(
                widget.service.name,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const LocalizedText('Khung giờ trong 30 ngày tới'),
              if (widget.service.options.isNotEmpty)
                DropdownButton<String>(
                  isExpanded: true,
                  value: _optionId,
                  items: widget.service.options
                      .map(
                        (option) => DropdownMenuItem(
                          value: option.id,
                          child: Text(
                            option.name,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: _refreshing
                      ? null
                      : (id) => setState(() {
                          _optionId = id;
                          _refreshError = null;
                        }),
                ),
              const SizedBox(height: 12),
              if (_refreshError != null) ...[
                LocalizedText(_refreshError!),
                TextButton(
                  onPressed: _refresh,
                  child: const LocalizedText('Thử lại'),
                ),
              ],
              Expanded(
                child: _refreshing
                    ? const Center(child: CircularProgressIndicator())
                    : slots.isEmpty
                    ? const Center(
                        child: LocalizedText(
                          'Chưa có khung giờ trong khoảng thời gian này.',
                        ),
                      )
                    : ListView.separated(
                        itemCount: slots.length,
                        separatorBuilder: (_, _) => const Divider(),
                        itemBuilder: (context, index) {
                          final slot = slots[index];
                          final date = DateTime.tryParse(slot.date);
                          final unit = switch (slot.pricingUnit) {
                            'PER_PACKAGE' => '/gói',
                            'PER_PERSON' => '/người',
                            _ => '',
                          };
                          return ListTile(
                            trailing:
                                slot.bookable &&
                                    slot.optionId != null &&
                                    (slot.availableCapacity ?? 0) > 0
                                ? TextButton(
                                    onPressed: () => _openHold(slot),
                                    child: const LocalizedText('Giữ chỗ'),
                                  )
                                : null,
                            contentPadding: EdgeInsets.zero,
                            leading: Icon(
                              slot.bookable
                                  ? Icons.event_available
                                  : Icons.event_busy,
                            ),
                            title: Text(
                              date == null
                                  ? slot.date
                                  : DateFormat('dd/MM/yyyy').format(date),
                            ),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('${slot.startTime} – ${slot.endTime}'),
                                LocalizedText(
                                  slot.bookable
                                      ? 'Có thể đặt'
                                      : 'Không thể đặt',
                                ),
                                LocalizedText(
                                  'Còn lại: ${slot.availableCapacity ?? 0}',
                                ),
                                if (slot.price != null)
                                  Text(
                                    '${NumberFormat.decimalPattern('vi').format(slot.price)} ₫${tr(context, unit)}',
                                  ),
                              ],
                            ),
                          );
                        },
                      ),
              ),
              const SizedBox(height: 12),
              const LocalizedText('Chọn khung giờ để giữ chỗ.'),
            ],
          ),
        ),
      ),
    );
  }
}
