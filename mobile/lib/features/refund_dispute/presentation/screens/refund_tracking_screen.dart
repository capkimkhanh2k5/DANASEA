import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../orders/presentation/screens/order_detail_screen.dart';

class RefundTrackingScreen extends StatefulWidget {
  final String? orderId;
  final List<RefundModel>? initialRefunds;

  const RefundTrackingScreen({
    super.key,
    this.orderId,
    this.initialRefunds,
  });

  @override
  State<RefundTrackingScreen> createState() => _RefundTrackingScreenState();
}

class _RefundTrackingScreenState extends State<RefundTrackingScreen> {
  final OrderRepository _repository = OrderRepository();
  List<RefundModel> _refunds = [];
  bool _loading = true;
  String? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    if (widget.initialRefunds != null && widget.initialRefunds!.isNotEmpty) {
      _refunds = widget.initialRefunds!;
    }
    _loadRefunds();
  }

  Future<void> _loadRefunds() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      List<RefundModel> loaded = [];
      if (widget.orderId != null && widget.orderId!.isNotEmpty) {
        final order = await _repository.detail(widget.orderId!);
        final names = <String, String>{
          for (final sub in order.subOrders) sub.id: sub.serviceName,
        };
        loaded = await _repository.refunds(
          order.id,
          subOrderServiceNames: names,
        );
      } else {
        final page = await _repository.list(page: 0, size: 20);
        final allLists = await Future.wait(
          page.content.map((order) {
            final names = <String, String>{
              for (final sub in order.subOrders) sub.id: sub.serviceName,
            };
            return _repository.refunds(
              order.id,
              subOrderServiceNames: names,
            );
          }),
        );
        loaded = allLists.expand((e) => e).toList();
      }
      if (!mounted || generation != _generation) return;
      setState(() => _refunds = loaded);
    } on AuthFailure catch (e) {
      if (mounted && generation == _generation) {
        setState(() {
          _refunds = [];
          _error = e.message;
        });
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(() {
          _refunds = [];
          _error = 'Không thể tải danh sách hoàn tiền. Vui lòng thử lại.';
        });
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  String _formatPrice(int price) {
    final str = price.toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i > 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  String _getReasonText(RefundReason reason, String? rawReason) {
    switch (rawReason) {
      case 'CUSTOMER_CANCEL':
      case 'CUSTOMER_REQUEST':
        return 'Khách yêu cầu hủy';
      case 'WEATHER':
        return 'Thời tiết biển bất lợi';
      case 'DISPUTE':
        return 'Bồi thường khiếu nại';
      case 'VENDOR_FAULT':
        return 'Đối tác hủy dịch vụ';
      case 'SYSTEM_FAULT':
        return 'Sự cố hệ thống';
      case 'ADMIN_OVERRIDE':
        return 'Quản trị viên phê duyệt';
      case 'COMPENSATION':
        return 'Bù trừ tách đơn';
    }
    switch (reason) {
      case RefundReason.customerCancel:
        return 'Khách yêu cầu hủy';
      case RefundReason.weather:
        return 'Thời tiết biển bất lợi';
      case RefundReason.dispute:
        return 'Bồi thường khiếu nại';
      case RefundReason.compensation:
        return 'Bù trừ tách đơn';
    }
  }

  @override
  Widget build(BuildContext context) {
    final refunds = _refunds;
    final totalAmount =
        refunds.fold(0, (sum, item) => sum + item.amount);

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
          'Theo dõi hoàn tiền',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
      ),
      body: RefreshIndicator(
        onRefresh: _loadRefunds,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(AppShapes.gutterMobile),
          child: Column(
            children: [
              // METRIC CARDS ROW
              Row(
                children: [
                  Expanded(
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
                          LocalizedText(
                            'Tổng yêu cầu',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 4),
                          LocalizedText(
                            '${refunds.length} dịch vụ',
                            style: AppTypography.headlineSm(
                              color: AppColors.onSurface,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
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
                          LocalizedText(
                            'Tổng tiền yêu cầu',
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 4),
                          LocalizedText(
                            _formatPrice(totalAmount),
                            style: AppTypography.headlineSm(
                              color: AppColors.primary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // TRANSPARENCY NOTICE
              Container(
                padding: const EdgeInsets.all(AppShapes.spaceSm),
                decoration: BoxDecoration(
                  color: AppColors.secondaryFixed.withValues(alpha: 0.35),
                  borderRadius: AppShapes.radiusDefault,
                ),
                child: Row(
                  children: [
                    const Icon(Icons.shield_outlined,
                        size: 20, color: AppColors.secondary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: LocalizedText(
                        'Hoàn trả minh bạch kết nối trực tiếp cổng thanh toán gốc, theo đúng lịch đối soát của ngân hàng.',
                        style: AppTypography.bodySm(
                          color: AppColors.onSecondaryFixedVariant,
                        ).copyWith(fontSize: 11),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              if (_loading)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (_error != null) ...[
                LocalizedText(
                  _error!,
                  textAlign: TextAlign.center,
                  style: AppTypography.bodyMd(color: AppColors.error),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _loadRefunds,
                  child: const LocalizedText('Thử lại'),
                ),
              ] else if (refunds.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 32),
                  child: LocalizedText(
                    'Chưa có yêu cầu hoàn tiền nào.',
                    style: AppTypography.bodyMd(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                )
              else
                // REFUND CARDS LIST
                ...refunds.map((ref) => _buildRefundCard(context, ref)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRefundCard(BuildContext context, RefundModel ref) {
    final isProcessed = ref.status == RefundStatus.processed;
    final isFailed = ref.status == RefundStatus.failed;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
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
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: LocalizedText(
                          ref.subOrderCode,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 10, fontFamily: 'monospace'),
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    Flexible(
                      child: LocalizedText(
                        '${ref.createdAt.day}/${ref.createdAt.month}/${ref.createdAt.year}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.bodySm(color: AppColors.outline)
                            .copyWith(fontSize: 10),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: isProcessed
                      ? AppColors.secondaryContainer
                      : AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isProcessed
                          ? Icons.check_circle
                          : isFailed
                              ? Icons.error_outline
                              : Icons.sync,
                      size: 12,
                      color: isProcessed
                          ? AppColors.onSecondaryContainer
                          : isFailed
                              ? AppColors.error
                              : AppColors.onSurfaceVariant,
                    ),
                    const SizedBox(width: 4),
                    LocalizedText(
                      isProcessed
                          ? 'Đã hoàn tất'
                          : isFailed
                              ? 'Thất bại'
                              : 'Đang xử lý',
                      style: AppTypography.labelSm(
                        color: isProcessed
                            ? AppColors.onSecondaryContainer
                            : isFailed
                                ? AppColors.error
                                : AppColors.onSurfaceVariant,
                        fontWeight: FontWeight.w700,
                      ).copyWith(fontSize: 10),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          LocalizedText(
            ref.serviceName,
            style: AppTypography.headlineSm(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 10),

          // Financial detail strip
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText('Số tiền hoàn:',
                          style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant)),
                      LocalizedText(
                        _formatPrice(ref.amount),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.secondary,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: LocalizedText(
                    'Tỷ lệ: ${ref.refundPercentage.toInt()}%',
                    style: AppTypography.labelSm(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),

          _buildRow('Lý do hoàn:', _getReasonText(ref.reason, ref.rawReason)),
          const SizedBox(height: 4),
          _buildRow('Phương thức nhận:', ref.originalPaymentMethod),
          const Divider(height: 16),

          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: AppColors.borderSubtle),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
              minimumSize: const Size(double.infinity, 38),
            ),
            onPressed: () {
              final targetOrder = ref.masterOrderId ?? widget.orderId;
              if (targetOrder != null && targetOrder.isNotEmpty) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => OrderDetailScreen(orderId: targetOrder),
                  ),
                );
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: LocalizedText(
                      'Xem chi tiết đơn ${ref.subOrderCode}',
                    ),
                    duration: const Duration(seconds: 1),
                  ),
                );
              }
            },
            icon: const Icon(Icons.receipt_long,
                size: 16, color: AppColors.secondary),
            label: LocalizedText(
              'Xem mã đơn gốc & hóa đơn',
              style: AppTypography.labelSm(color: AppColors.secondary),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        LocalizedText(label,
            style: AppTypography.bodySm(color: AppColors.onSurfaceVariant)),
        const SizedBox(width: 12),
        Expanded(
          child: LocalizedText(
            value,
            textAlign: TextAlign.right,
            style: AppTypography.labelMd(
                color: AppColors.onSurface, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}
