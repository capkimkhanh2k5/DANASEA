import 'package:flutter/material.dart';
import '../../../../core/data/vendor_repository.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';

class VendorProfileScreen extends StatefulWidget {
  const VendorProfileScreen({
    super.key,
    required this.vendorId,
    this.repository,
  });
  final String vendorId;
  final VendorRepository? repository;

  @override
  State<VendorProfileScreen> createState() => _VendorProfileScreenState();
}

class _VendorProfileScreenState extends State<VendorProfileScreen> {
  late final VendorRepository _repository =
      widget.repository ?? VendorRepository();
  PublicVendor? _vendor;
  final List<ServiceModel> _services = [];
  bool _loading = false;
  String? _error;
  int _page = -1, _total = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool more = false}) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final vendor = more
          ? _vendor!
          : await _repository.profile(widget.vendorId);
      final page = await _repository.services(
        widget.vendorId,
        page: more ? _page + 1 : 0,
      );
      if (!mounted) return;
      setState(() {
        _vendor = vendor;
        if (!more) _services.clear();
        _services.addAll(page.items);
        _page = page.page;
        _total = page.total;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vendor = _vendor;
    return Scaffold(
      appBar: AppBar(title: const LocalizedText('Nhà cung cấp')),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(20),
          children: [
            if (vendor != null) ...[
              const Icon(Icons.storefront, size: 64),
              const SizedBox(height: 16),
              Text(
                vendor.businessName,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              if (vendor.address.isNotEmpty) Text(vendor.address),
              if (vendor.badgeTier == 'VERIFIED' ||
                  vendor.badgeTier == 'TOP_RATED')
                LocalizedText(
                  vendor.badgeTier == 'TOP_RATED'
                      ? 'Đối tác hàng đầu'
                      : 'Đã xác minh',
                ),
              Row(
                children: [
                  const Icon(Icons.star, color: Colors.amber, size: 20),
                  Text(
                    ' ${vendor.ratingAvg.toStringAsFixed(1)} · ${vendor.ratingCount} ',
                  ),
                  const LocalizedText('đánh giá'),
                ],
              ),
              const Divider(height: 32),
              Row(
                children: [
                  const LocalizedText('Dịch vụ'),
                  Text(' (${vendor.activeServicesCount})'),
                ],
              ),
              if (_services.isEmpty && !_loading && _error == null)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: LocalizedText('Chưa có dịch vụ.'),
                ),
              for (final service in _services)
                Card(
                  child: ListTile(
                    leading: service.imageUrls.isEmpty
                        ? const Icon(Icons.beach_access)
                        : Image.network(
                            service.imageUrls.first,
                            width: 56,
                            height: 56,
                            fit: BoxFit.cover,
                            errorBuilder: (_, _, _) =>
                                const Icon(Icons.image_not_supported_outlined),
                          ),
                    title: Text(service.name),
                    subtitle: Text('${service.price} đ'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) =>
                            CatalogDetailScreen(serviceId: service.id),
                      ),
                    ),
                  ),
                ),
            ],
            if (_error != null) ...[
              Text(_error!),
              TextButton(
                onPressed: _loading ? null : () => _load(),
                child: const LocalizedText('Thử lại'),
              ),
            ],
            if (_loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: CircularProgressIndicator(),
                ),
              )
            else if (_services.length < _total)
              TextButton(
                onPressed: () => _load(more: true),
                child: const LocalizedText('Xem thêm'),
              ),
          ],
        ),
      ),
    );
  }
}
