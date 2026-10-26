import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/dispute_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import 'create_dispute_screen.dart';

class MyDisputesScreen extends StatefulWidget {
  const MyDisputesScreen({super.key});

  @override
  State<MyDisputesScreen> createState() => _MyDisputesScreenState();
}

class _MyDisputesScreenState extends State<MyDisputesScreen> {
  final DisputeRepository _disputeRepository = DisputeRepository();
  final OrderRepository _orderRepository = OrderRepository();

  int _selectedFilterIndex = 0;
  List<DisputeModel> _disputes = [];
  bool _loading = true;
  bool _loadingDetail = false;
  String? _error;

  final List<String> _filters = [
    'Tất cả',
    'Đang mở',
    'Đang thẩm định',
    'Đã giải quyết',
    'Bị từ chối',
  ];

  @override
  void initState() {
    super.initState();
    _loadDisputes();
  }

  Future<void> _loadDisputes() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final subMap = <String, SubOrderModel>{};
      try {
        final orderPage = await _orderRepository.list(page: 0, size: 20);
        for (final ord in orderPage.content) {
          for (final sub in ord.subOrders) {
            subMap[sub.id] = sub;
          }
        }
      } catch (_) {}

      final page = await _disputeRepository.getCustomerDisputes(
        page: 0,
        size: 50,
        subOrderMap: subMap,
      );
      if (!mounted) return;
      setState(() => _disputes = page.content);
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Không thể tải danh sách khiếu nại. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openDisputeDetail(DisputeModel summary) async {
    if (_loadingDetail) return;
    setState(() => _loadingDetail = true);
    try {
      final detail = await _disputeRepository.getCustomerDisputeDetail(
        summary.id,
        fallbackServiceName: summary.serviceName,
        fallbackVendorName: summary.vendorName,
      );
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: AppColors.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        builder: (sheetContext) {
          final statusColor = _getStatusColor(detail.status);
          return SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: LocalizedText(
                          'Chi tiết khiếu nại #${detail.id}',
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
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: LocalizedText(
                      _getStatusName(detail.status),
                      style: AppTypography.labelSm(
                        color: statusColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  LocalizedText(
                    '${detail.serviceName} • ${detail.vendorName}',
                    style: AppTypography.labelMd(
                      color: AppColors.onSurface,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 6),
                  LocalizedText(
                    'Phân loại: ${detail.category}',
                    style: AppTypography.labelSm(
                      color: AppColors.primary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    detail.description,
                    style: AppTypography.bodySm(color: AppColors.onSurface),
                  ),
                  if (detail.attachmentUrls.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    LocalizedText(
                      'Minh chứng đính kèm (${detail.attachmentUrls.length}):',
                      style: AppTypography.labelSm(
                        color: AppColors.onSurfaceVariant,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    for (final url in detail.attachmentUrls)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Text(
                          url,
                          style: AppTypography.bodySm(
                            color: AppColors.secondary,
                          ).copyWith(fontSize: 11),
                        ),
                      ),
                  ],
                  if (detail.vendorResponse != null &&
                      detail.vendorResponse!.trim().isNotEmpty) ...[
                    const Divider(height: 16),
                    LocalizedText(
                      'Giải trình từ đối tác:',
                      style: AppTypography.labelSm(
                        color: AppColors.secondary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      detail.vendorResponse!,
                      style: AppTypography.bodySm(color: AppColors.onSurface),
                    ),
                  ],
                  if (detail.resolutionNote != null &&
                      detail.resolutionNote!.trim().isNotEmpty) ...[
                    const Divider(height: 16),
                    LocalizedText(
                      detail.status == DisputeStatus.rejected
                          ? 'Lý do từ chối từ Admin:'
                          : 'Kết luận xử lý từ Admin:',
                      style: AppTypography.labelSm(
                        color: statusColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      detail.resolutionNote!,
                      style: AppTypography.bodySm(color: AppColors.onSurface),
                    ),
                    if (detail.refundPercentage != null)
                      LocalizedText(
                        'Mức hoàn tiền phê duyệt: ${detail.refundPercentage!.toStringAsFixed(0)}%',
                        style: AppTypography.labelSm(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                  ],
                ],
              ),
            ),
          );
        },
      );
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải chi tiết khiếu nại. Vui lòng thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingDetail = false);
    }
  }

  List<DisputeModel> get _filteredDisputes {
    final all = _disputes;
    if (_selectedFilterIndex == 0) return all;
    if (_selectedFilterIndex == 1) {
      return all.where((d) => d.status == DisputeStatus.open).toList();
    }
    if (_selectedFilterIndex == 2) {
      return all.where((d) => d.status == DisputeStatus.inReview).toList();
    }
    if (_selectedFilterIndex == 3) {
      return all.where((d) => d.status == DisputeStatus.resolved).toList();
    }
    return all.where((d) => d.status == DisputeStatus.rejected).toList();
  }

  String _getStatusName(DisputeStatus s) {
    switch (s) {
      case DisputeStatus.open:
        return 'Đang mở';
      case DisputeStatus.inReview:
        return 'Đang thẩm định';
      case DisputeStatus.resolved:
        return 'Đã giải quyết';
      case DisputeStatus.rejected:
        return 'Bị từ chối';
    }
  }

  Color _getStatusColor(DisputeStatus s) {
    switch (s) {
      case DisputeStatus.resolved:
        return AppColors.secondary;
      case DisputeStatus.inReview:
        return AppColors.secondaryTeal;
      case DisputeStatus.open:
        return AppColors.primary;
      case DisputeStatus.rejected:
        return AppColors.error;
    }
  }

  @override
  Widget build(BuildContext context) {
    final disputes = _filteredDisputes;

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: LocalizedText(
          'Khiếu nại của tôi',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(
              Icons.add_circle_outline,
              color: AppColors.secondary,
            ),
            tooltip: tr(context, 'Gửi khiếu nại'),
            onPressed: () async {
              final created = await Navigator.push<DisputeModel>(
                context,
                MaterialPageRoute(
                  builder: (_) => const CreateDisputeScreen(),
                ),
              );
              if (created != null && mounted) {
                _loadDisputes();
              }
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // BANNER
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
              vertical: 4,
            ),
            child: Container(
              padding: const EdgeInsets.all(AppShapes.spaceSm),
              decoration: BoxDecoration(
                color: AppColors.secondaryContainer.withValues(alpha: 0.35),
                borderRadius: AppShapes.radiusDefault,
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.shield_outlined,
                    size: 20,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        LocalizedText(
                          'Bảo vệ quyền lợi hành trình biển',
                          style: AppTypography.labelMd(
                            color: AppColors.onSecondaryContainer,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        LocalizedText(
                          'Tất cả khiếu nại được tiếp nhận và giải quyết căn cứ theo chứng từ và thỏa thuận bảo vệ khách hàng của DANASEA.',
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ).copyWith(fontSize: 11),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),

          // FILTER TABS
          Container(
            height: 48,
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(
                horizontal: AppShapes.gutterMobile,
              ),
              itemCount: _filters.length,
              separatorBuilder: (_, unused) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final isSel = _selectedFilterIndex == index;
                return InkWell(
                  onTap: () => setState(() => _selectedFilterIndex = index),
                  borderRadius: BorderRadius.circular(18),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: isSel
                          ? AppColors.secondary
                          : AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: Center(
                      child: LocalizedText(
                        _filters[index],
                        style: AppTypography.labelSm(
                          color: isSel
                              ? Colors.white
                              : AppColors.onSurfaceVariant,
                          fontWeight: isSel ? FontWeight.w700 : FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),

          // DISPUTES LIST
          Expanded(
            child: RefreshIndicator(
              onRefresh: _loadDisputes,
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                  ? ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(AppShapes.gutterMobile),
                      children: [
                        const SizedBox(height: 60),
                        Center(
                          child: LocalizedText(
                            _error!,
                            textAlign: TextAlign.center,
                            style: AppTypography.bodySm(color: AppColors.error),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Center(
                          child: OutlinedButton.icon(
                            onPressed: _loadDisputes,
                            icon: const Icon(Icons.refresh, size: 16),
                            label: const LocalizedText('Thử lại'),
                          ),
                        ),
                      ],
                    )
                  : disputes.isEmpty
                  ? ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: [
                        const SizedBox(height: 80),
                        Center(
                          child: LocalizedText(
                            'Không có khiếu nại nào trong mục này',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    )
                  : ListView.separated(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(AppShapes.gutterMobile),
                      itemCount: disputes.length,
                      separatorBuilder: (_, unused) =>
                          const SizedBox(height: AppShapes.spaceMd),
                      itemBuilder: (context, index) {
                        final disp = disputes[index];
                        final statusColor = _getStatusColor(disp.status);
                        return InkWell(
                          onTap: () => _openDisputeDetail(disp),
                          borderRadius: AppShapes.radiusDefault,
                          child: Container(
                            padding: const EdgeInsets.all(AppShapes.spaceSm),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLowest,
                              borderRadius: AppShapes.radiusDefault,
                              boxShadow: AppShapes.shadowLevel1,
                              border: Border.all(color: AppColors.borderSubtle),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          LocalizedText(
                                            '#${disp.id}',
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: AppTypography.headlineSm(
                                              color: AppColors.secondary,
                                            ),
                                          ),
                                          LocalizedText(
                                            '${disp.createdAt.day}/${disp.createdAt.month}/${disp.createdAt.year}',
                                            style: AppTypography.bodySm(
                                              color: AppColors.outline,
                                            ).copyWith(fontSize: 11),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 3,
                                      ),
                                      decoration: BoxDecoration(
                                        color: statusColor.withValues(
                                          alpha: 0.15,
                                        ),
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: LocalizedText(
                                        _getStatusName(disp.status),
                                        style: AppTypography.labelSm(
                                          color: statusColor,
                                          fontWeight: FontWeight.w700,
                                        ).copyWith(fontSize: 10),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),

                                // Linked service card
                                Container(
                                  padding: const EdgeInsets.all(8),
                                  decoration: BoxDecoration(
                                    color: AppColors.surfaceContainerLow,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Row(
                                    children: [
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            LocalizedText(
                                              'Đơn con #${disp.subOrderCode}',
                                              style: AppTypography.labelSm(
                                                color: AppColors.secondary,
                                                fontWeight: FontWeight.w700,
                                              ).copyWith(fontSize: 10),
                                            ),
                                            LocalizedText(
                                              disp.serviceName,
                                              style: AppTypography.labelMd(
                                                color: AppColors.onSurface,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                            LocalizedText(
                                              disp.vendorName,
                                              style: AppTypography.bodySm(
                                                color:
                                                    AppColors.onSurfaceVariant,
                                              ).copyWith(fontSize: 11),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 8),

                                LocalizedText(
                                  'Phân loại: ${disp.category}',
                                  style: AppTypography.labelSm(
                                    color: AppColors.primary,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                LocalizedText(
                                  disp.description,
                                  style: AppTypography.bodySm(
                                    color: AppColors.onSurface,
                                  ),
                                ),

                                // Resolution note if present
                                if (disp.resolutionNote != null) ...[
                                  const SizedBox(height: 10),
                                  Container(
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: disp.status ==
                                              DisputeStatus.rejected
                                          ? AppColors.error.withValues(
                                              alpha: 0.1,
                                            )
                                          : AppColors.secondaryFixed.withValues(
                                              alpha: 0.25,
                                            ),
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Icon(
                                          disp.status == DisputeStatus.rejected
                                              ? Icons.cancel_outlined
                                              : Icons.verified,
                                          size: 16,
                                          color: disp.status ==
                                                  DisputeStatus.rejected
                                              ? AppColors.error
                                              : AppColors.secondary,
                                        ),
                                        const SizedBox(width: 6),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: [
                                              LocalizedText(
                                                disp.status ==
                                                        DisputeStatus.rejected
                                                    ? 'Lý do từ chối từ Admin:'
                                                    : 'Kết luận xử lý từ Admin:',
                                                style: AppTypography.labelSm(
                                                  color: disp.status ==
                                                          DisputeStatus.rejected
                                                      ? AppColors.error
                                                      : AppColors.secondary,
                                                  fontWeight: FontWeight.w700,
                                                ),
                                              ),
                                              LocalizedText(
                                                disp.resolutionNote!,
                                                style: AppTypography.bodySm(
                                                  color: AppColors.onSurface,
                                                ).copyWith(fontSize: 11),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: AppColors.primary,
        onPressed: () async {
          final created = await Navigator.push<DisputeModel>(
            context,
            MaterialPageRoute(
              builder: (_) => const CreateDisputeScreen(),
            ),
          );
          if (created != null && mounted) {
            _loadDisputes();
          }
        },
        icon: const Icon(Icons.add, color: Colors.white),
        label: const LocalizedText(
          'Gửi khiếu nại',
          style: TextStyle(color: Colors.white),
        ),
      ),
    );
  }
}
