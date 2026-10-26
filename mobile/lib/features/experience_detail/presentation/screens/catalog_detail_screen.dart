import '../../../../core/data/library_store.dart';
import 'package:flutter/material.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../vendor/presentation/screens/vendor_profile_screen.dart';
import 'experience_detail_screen.dart';
import '../widgets/service_availability_sheet.dart';

/// Public catalog details. Booking is connected separately from catalog reads.
class CatalogDetailScreen extends StatefulWidget {
  const CatalogDetailScreen({
    super.key,
    required this.serviceId,
    this.repository,
    this.library,
  });
  final String serviceId;
  final CatalogRepository? repository;
  final LibraryStore? library;
  @override
  State<CatalogDetailScreen> createState() => _CatalogDetailScreenState();
}

class _CatalogDetailScreenState extends State<CatalogDetailScreen> {
  late final LibraryStore _library;
  ServiceModel? _service;
  String? _error;
  bool _loading = true;
  @override
  void initState() {
    super.initState();
    _library = widget.library ?? LibraryStore.instance;
    _library.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _library.loadWishlist();
    });
    _load();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _library.removeListener(_changed);
    super.dispose();
  }

  Future<void> _toggle() async {
    final service = _service;
    if (service == null) return;
    try {
      await _library.toggle(
        SavedService(
          serviceId: service.id,
          name: service.name,
          imageUrl: service.imageUrls.isEmpty ? null : service.imageUrls.first,
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final service = await (widget.repository ?? CatalogRepository()).detail(
        widget.serviceId,
      );
      if (mounted) {
        setState(() => _service = service);
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _library.loadRecent();
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final service = _service;
    if (!_loading && _error == null && service != null) {
      return ExperienceDetailScreen(
        service: service.copyWith(isFavorite: _library.contains(service.id)),
        onToggleFavorite: _toggle,
        favoriteBusy: _library.busy(service.id),
        onOpenVendor: service.vendorId.isEmpty ? null : () {
          Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (_) => VendorProfileScreen(vendorId: service.vendorId),
          ));
        },
        onOpenBooking: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => ServiceAvailabilitySheet(service: service),
        ),
      );
    }
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(title: const LocalizedText('Chi tiết trải nghiệm')),
      body: _loading
        ? const Center(child: CircularProgressIndicator())
        : Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
            Padding(padding: const EdgeInsets.all(24), child: Text(_error ?? '')),
            TextButton(onPressed: _load, child: const LocalizedText('Thử lại')),
          ])),
    );
  }
}
