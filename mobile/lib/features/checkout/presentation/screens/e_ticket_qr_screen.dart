import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class ETicketQrScreen extends StatefulWidget {
  final SubOrderModel? subOrder;
  final List<SubOrderModel>? allSubOrders;
  final MasterOrderModel? masterOrder;

  const ETicketQrScreen({
    super.key,
    this.subOrder,
    this.allSubOrders,
    this.masterOrder,
  });

  @override
  State<ETicketQrScreen> createState() => _ETicketQrScreenState();
}

class _ETicketQrScreenState extends State<ETicketQrScreen> {
  final OrderRepository _orderRepository = OrderRepository();
  final Map<String, CheckinQrCode> _qrCache = <String, CheckinQrCode>{};
  bool _loadingQr = false;
  String? _qrError;
  int _generation = 0;

  String get _targetId {
    if (widget.masterOrder != null && widget.masterOrder!.id.isNotEmpty) {
      return widget.masterOrder!.id;
    }
    final masterId = widget.subOrder?.masterOrderId;
    if (masterId != null && masterId.isNotEmpty) {
      return masterId;
    }
    return widget.subOrder?.id ?? '';
  }

  List<SubOrderModel> get _activities {
    if (widget.masterOrder != null && widget.masterOrder!.subOrders.isNotEmpty) {
      return widget.masterOrder!.subOrders;
    }
    if (widget.allSubOrders != null && widget.allSubOrders!.isNotEmpty) {
      return widget.allSubOrders!;
    }
    if (widget.subOrder != null) {
      return [widget.subOrder!];
    }
    return const [];
  }

  SubOrderModel get _displaySubOrder {
    if (_activities.isNotEmpty) return _activities.first;
    if (widget.subOrder != null) return widget.subOrder!;
    if (widget.masterOrder != null && widget.masterOrder!.subOrders.isNotEmpty) {
      return widget.masterOrder!.subOrders.first;
    }
    throw StateError('ETicketQrScreen requires either subOrder or masterOrder');
  }

  CheckinQrCode? get _activeQr => _qrCache[_targetId];

  @override
  void initState() {
    super.initState();
    _loadQrForCurrentTicket();
  }

  Future<void> _loadQrForCurrentTicket({bool forceRefresh = false}) async {
    final targetId = _targetId;
    if (targetId.isEmpty) return;
    if (!forceRefresh && _qrCache.containsKey(targetId)) {
      setState(() => _qrError = null);
      return;
    }
    if (_loadingQr && !forceRefresh) return;
    final generation = ++_generation;
    setState(() {
      _loadingQr = true;
      _qrError = null;
    });
    try {
      final qr = await _orderRepository.generateQrCode(targetId);
      if (!mounted || generation != _generation) return;
      setState(() {
        _qrCache[targetId] = qr;
      });
    } on AuthFailure catch (e) {
      if (mounted && generation == _generation) {
        setState(() {
          _qrError = e.message;
        });
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(() {
          _qrError = 'Không thể tạo mã QR check-in. Vui lòng thử lại.';
        });
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loadingQr = false);
      }
    }
  }

  bool _isExpiredNotice(String err) {
    final lower = err.toLowerCase();
    return lower.contains('kết thúc') ||
        lower.contains('hết hạn') ||
        lower.contains('quá hạn') ||
        lower.contains('hoàn thành');
  }

  String _getNoticeTitle(String err) {
    final lower = err.toLowerCase();
    if (lower.contains('kết thúc')) {
      return 'Khung giờ dịch vụ đã kết thúc';
    }
    if (lower.contains('hết hạn')) {
      return 'Vé đã hết hạn sử dụng';
    }
    return 'Thông báo trạng thái vé';
  }

  String _getNoticeMessage(String err) {
    final lower = err.toLowerCase();
    if (lower.contains('kết thúc')) {
      return 'Khung giờ trải nghiệm cho vé này đã kết thúc. Mã QR check-in đã được khóa an toàn theo quy định.';
    }
    if (lower.contains('hết hạn')) {
      return 'Vé đã quá hạn sử dụng và không còn hiệu lực check-in tại bãi biển.';
    }
    return err;
  }

  @override
  Widget build(BuildContext context) {
    final displayOrder = _displaySubOrder;
    final profile = AuthSession.instance.profile;
    final fullName = (profile?['fullName']?.toString() ?? '').trim();
    final email = (profile?['email']?.toString() ?? '').trim();
    final attendeeName = fullName.isNotEmpty
        ? fullName
        : (email.isNotEmpty ? email : '#$_targetId');
    final qrData = _activeQr;
    final expiresAt = qrData?.expiresAt.toLocal();

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
          'Vé điện tử QR',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: tr(context, 'Làm mới'),
            icon: _loadingQr
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh, color: AppColors.onSurface),
            onPressed: _loadingQr
                ? null
                : () => _loadQrForCurrentTicket(forceRefresh: true),
          ),
          IconButton(
            icon: const Icon(Icons.share, color: AppColors.onSurface),
            onPressed: qrData == null
                ? null
                : () {
                    Clipboard.setData(ClipboardData(text: qrData.qrToken));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: LocalizedText('Đã sao chép mã token QR check-in!'),
                        backgroundColor: AppColors.secondary,
                      ),
                    );
                  },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppShapes.gutterMobile,
          vertical: 8,
        ),
        child: Column(
          children: [
            // BOARDING PASS / E-TICKET CARD
            Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusLg,
                boxShadow: AppShapes.shadowLevel2,
                border: Border.all(
                  color: AppColors.borderSubtle.withValues(alpha: 0.8),
                ),
              ),
              clipBehavior: Clip.antiAlias,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // TOP HERO BANNER OF TICKET
                  SizedBox(
                    height: 110,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        Image.network(
                          displayOrder.serviceImageUrl.isNotEmpty
                              ? displayOrder.serviceImageUrl
                              : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=600&auto=format&fit=crop&q=80',
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => Container(
                            color: AppColors.surfaceContainerHigh,
                          ),
                        ),
                        Container(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                Colors.black.withValues(alpha: 0.3),
                                Colors.black.withValues(alpha: 0.6),
                              ],
                            ),
                          ),
                        ),
                        // Top live ocean pill
                        Positioned(
                          top: 10,
                          left: 10,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.surface.withValues(alpha: 0.9),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              children: [
                                Container(
                                  width: 6,
                                  height: 6,
                                  decoration: const BoxDecoration(
                                    color: AppColors.secondary,
                                    shape: BoxShape.circle,
                                  ),
                                ),
                                const SizedBox(width: 4),
                                LocalizedText(
                                  'Sóng 0.4m • Gió 8 km/h',
                                  style: AppTypography.labelSm(
                                    color: AppColors.onSurface,
                                    fontWeight: FontWeight.w700,
                                  ).copyWith(fontSize: 10),
                                ),
                              ],
                            ),
                          ),
                        ),
                        // Valid check-in badge
                        Positioned(
                          top: 10,
                          right: 10,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: qrData != null
                                  ? AppColors.secondaryContainer
                                  : _qrError != null
                                      ? const Color(0xFFFFEBEE)
                                      : AppColors.surfaceContainerHigh,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  qrData != null
                                      ? Icons.verified
                                      : _qrError != null
                                          ? Icons.event_busy
                                          : Icons.hourglass_top,
                                  size: 12,
                                  color: qrData != null
                                      ? AppColors.onSecondaryContainer
                                      : _qrError != null
                                          ? const Color(0xFFC62828)
                                          : AppColors.onSurfaceVariant,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  qrData != null
                                      ? 'HỢP LỆ CHECK-IN'
                                      : _qrError != null
                                          ? 'ĐÃ KẾT THÚC'
                                          : 'CHỜ MÃ QR',
                                  style: TextStyle(
                                    color: qrData != null
                                        ? AppColors.onSecondaryContainer
                                        : _qrError != null
                                            ? const Color(0xFFC62828)
                                            : AppColors.onSurfaceVariant,
                                    fontWeight: FontWeight.w800,
                                    fontSize: 10,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // TICKET MAIN BODY
                  Padding(
                    padding: const EdgeInsets.all(AppShapes.spaceMd),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.surfing,
                                size: 14, color: AppColors.secondary),
                            const SizedBox(width: 4),
                            Expanded(
                              child: LocalizedText(
                                _activities.length > 1
                                    ? 'Vé toàn đơn • ${_activities.length} hoạt động biển'
                                    : '${displayOrder.vendorName} • ${displayOrder.locationName}',
                                style: AppTypography.labelSm(
                                  color: AppColors.secondary,
                                  fontWeight: FontWeight.w700,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        LocalizedText(
                          _activities.length > 1
                              ? 'Gói trải nghiệm biển Đà Nẵng'
                              : displayOrder.serviceName,
                          style: AppTypography.headlineSm(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: 14),

                        // Key Metadata 2x2 Grid
                        Row(
                          children: [
                            Expanded(
                              child: _buildMetaBox(
                                Icons.calendar_today,
                                'Ngày trải nghiệm',
                                '${displayOrder.slotDate.day}/${displayOrder.slotDate.month}/${displayOrder.slotDate.year}',
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: _buildMetaBox(
                                Icons.schedule,
                                'Khung giờ biển',
                                _activities.length > 1
                                    ? 'Theo chi tiết bên dưới'
                                    : (displayOrder.slotTime.startsWith('Slot #')
                                        ? 'Theo lịch đặt'
                                        : displayOrder.slotTime.replaceAllMapped(
                                            RegExp(r'(\d{1,2}:\d{2}):00'),
                                            (m) => m.group(1) ?? '',
                                          )),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: _buildMetaBox(
                                Icons.tag,
                                'Mã vé / Mã đơn',
                                '#$_targetId',
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: _buildMetaBox(
                                Icons.group,
                                'Số lượng khách',
                                '${displayOrder.quantity} người lớn',
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),

                        // Primary Attendee
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Row(
                                  children: [
                                    const Icon(Icons.person,
                                        size: 16, color: AppColors.secondary),
                                    const SizedBox(width: 6),
                                    Flexible(
                                      child: LocalizedText(
                                        attendeeName,
                                        overflow: TextOverflow.ellipsis,
                                        style: AppTypography.labelMd(
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
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppColors.surfaceContainerLowest,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: LocalizedText(
                                  'Người đại diện',
                                  style: AppTypography.labelSm(
                                    color: AppColors.secondary,
                                  ).copyWith(fontSize: 10),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  // TEAR-OFF PERFORATION DIVIDER
                  Row(
                    children: [
                      Container(
                        width: 14,
                        height: 24,
                        decoration: const BoxDecoration(
                          color: AppColors.surface,
                          borderRadius: BorderRadius.horizontal(
                            right: Radius.circular(14),
                          ),
                        ),
                      ),
                      Expanded(
                        child: LayoutBuilder(
                          builder: (context, constraints) {
                            final dashCount = (constraints.constrainWidth() / 10).floor();
                            return Flex(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              direction: Axis.horizontal,
                              children: List.generate(
                                dashCount,
                                (_) => const SizedBox(
                                  width: 5,
                                  height: 1.5,
                                  child: DecoratedBox(
                                    decoration: BoxDecoration(
                                      color: AppColors.outlineVariant,
                                    ),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                      Container(
                        width: 14,
                        height: 24,
                        decoration: const BoxDecoration(
                          color: AppColors.surface,
                          borderRadius: BorderRadius.horizontal(
                            left: Radius.circular(14),
                          ),
                        ),
                      ),
                    ],
                  ),

                  // QR CODE COMPARTMENT
                  Padding(
                    padding: const EdgeInsets.all(AppShapes.spaceMd),
                    child: Column(
                      children: [
                        if (_loadingQr && qrData == null)
                          const SizedBox(
                            width: 260,
                            height: 280,
                            child: Center(child: CircularProgressIndicator()),
                          )
                        else if (_qrError != null && qrData == null)
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 22,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF9FAFB),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFFE5E7EB),
                                width: 1,
                              ),
                            ),
                            child: Column(
                              children: [
                                Container(
                                  width: 46,
                                  height: 46,
                                  decoration: BoxDecoration(
                                    color: _isExpiredNotice(_qrError!)
                                        ? const Color(0xFFFFF3E0)
                                        : const Color(0xFFE0F2F1),
                                    shape: BoxShape.circle,
                                  ),
                                  child: Icon(
                                    _isExpiredNotice(_qrError!)
                                        ? Icons.event_busy_rounded
                                        : Icons.info_outline_rounded,
                                    color: _isExpiredNotice(_qrError!)
                                        ? const Color(0xFFE65100)
                                        : AppColors.secondary,
                                    size: 24,
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  _getNoticeTitle(_qrError!),
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.onSurface,
                                  ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  _getNoticeMessage(_qrError!),
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    fontSize: 13,
                                    color: AppColors.onSurfaceVariant,
                                    height: 1.4,
                                  ),
                                ),
                                const SizedBox(height: 14),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 7,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(
                                      color: const Color(0xFFEEEEEE),
                                    ),
                                  ),
                                  child: const Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(
                                        Icons.support_agent_rounded,
                                        size: 15,
                                        color: AppColors.secondary,
                                      ),
                                      SizedBox(width: 6),
                                      Text(
                                        'Hỗ trợ bãi biển: 1900 6868',
                                        style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                          color: AppColors.secondary,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          )
                        else ...[
                          // Outdoor High-Contrast QR Frame
                          Container(
                            width: 260,
                            height: 280,
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(16),
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.black.withValues(alpha: 0.08),
                                  blurRadius: 16,
                                ),
                              ],
                              border: Border.all(
                                color: AppColors.borderOcean,
                                width: 1.5,
                              ),
                            ),
                            child: Stack(
                              children: [
                                // Reticle Corners
                                Positioned(
                                  top: 0,
                                  left: 0,
                                  child: Container(
                                    width: 16,
                                    height: 16,
                                    decoration: const BoxDecoration(
                                      border: Border(
                                        top: BorderSide(
                                            color: AppColors.primary, width: 3),
                                        left: BorderSide(
                                            color: AppColors.primary, width: 3),
                                      ),
                                    ),
                                  ),
                                ),
                                Positioned(
                                  top: 0,
                                  right: 0,
                                  child: Container(
                                    width: 16,
                                    height: 16,
                                    decoration: const BoxDecoration(
                                      border: Border(
                                        top: BorderSide(
                                            color: AppColors.primary, width: 3),
                                        right: BorderSide(
                                            color: AppColors.primary, width: 3),
                                      ),
                                    ),
                                  ),
                                ),
                                Positioned(
                                  bottom: 0,
                                  left: 0,
                                  child: Container(
                                    width: 16,
                                    height: 16,
                                    decoration: const BoxDecoration(
                                      border: Border(
                                        bottom: BorderSide(
                                            color: AppColors.primary, width: 3),
                                        left: BorderSide(
                                            color: AppColors.primary, width: 3),
                                      ),
                                    ),
                                  ),
                                ),
                                Positioned(
                                  bottom: 0,
                                  right: 0,
                                  child: Container(
                                    width: 16,
                                    height: 16,
                                    decoration: const BoxDecoration(
                                      border: Border(
                                        bottom: BorderSide(
                                            color: AppColors.primary, width: 3),
                                        right: BorderSide(
                                            color: AppColors.primary, width: 3),
                                      ),
                                    ),
                                  ),
                                ),
                                // QR Pattern Representation
                                Center(
                                  child: Column(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      QrImageView(
                                        data: qrData!.qrToken,
                                        size: 220,
                                        padding: const EdgeInsets.all(16),
                                        backgroundColor: Colors.white,
                                      ),
                                      LocalizedText(
                                        qrData.subOrderId,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                          fontFamily: 'monospace',
                                          fontSize: 9,
                                          fontWeight: FontWeight.bold,
                                          color: AppColors.secondary,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 14),
                          LocalizedText(
                            'Đưa mã QR cho đối tác quét xác nhận check-in',
                            style: AppTypography.labelMd(
                              color: AppColors.onSurface,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 4),
                          SelectableText(
                            'Mã bảo mật: ${qrData.qrToken}',
                            style: AppTypography.bodySm(
                              color: AppColors.outline,
                            ).copyWith(fontSize: 10),
                            textAlign: TextAlign.center,
                          ),
                          if (expiresAt != null) ...[
                            const SizedBox(height: 4),
                            LocalizedText(
                              'Hiệu lực đến: ${expiresAt.hour.toString().padLeft(2, '0')}:${expiresAt.minute.toString().padLeft(2, '0')} • ${expiresAt.day}/${expiresAt.month}/${expiresAt.year}',
                              style: AppTypography.bodySm(
                                color: AppColors.secondary,
                              ).copyWith(fontSize: 11),
                            ),
                          ],
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (_activities.isNotEmpty) ...[
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(AppShapes.spaceMd),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: AppShapes.radiusLg,
                  boxShadow: AppShapes.shadowLevel1,
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.playlist_add_check_circle_outlined,
                                size: 18, color: AppColors.secondary),
                            const SizedBox(width: 6),
                            LocalizedText(
                              'Các hoạt động trong vé (${_activities.length})',
                              style: AppTypography.labelMd(
                                color: AppColors.onSurface,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppColors.secondaryContainer.withValues(alpha: 0.5),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: LocalizedText(
                            '1 mã QR chung',
                            style: AppTypography.labelSm(
                              color: AppColors.secondary,
                              fontWeight: FontWeight.bold,
                            ).copyWith(fontSize: 10),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    ..._activities.map((sub) {
                      final isCheckedIn = sub.checkedInAt != null ||
                          sub.status == SubOrderStatus.completed;
                      final isConfirmed = sub.status == SubOrderStatus.confirmed;
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: isCheckedIn
                                ? AppColors.secondary.withValues(alpha: 0.3)
                                : AppColors.outlineVariant.withValues(alpha: 0.4),
                          ),
                        ),
                        child: Row(
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: SizedBox(
                                width: 44,
                                height: 44,
                                child: sub.serviceImageUrl.isNotEmpty
                                    ? Image.network(
                                        sub.serviceImageUrl,
                                        fit: BoxFit.cover,
                                        errorBuilder: (context, error, stackTrace) => Container(
                                          color: AppColors.surfaceContainerHigh,
                                          child: const Icon(Icons.surfing, size: 20, color: AppColors.secondary),
                                        ),
                                      )
                                    : Container(
                                        color: AppColors.surfaceContainerHigh,
                                        child: const Icon(Icons.surfing, size: 20, color: AppColors.secondary),
                                      ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  LocalizedText(
                                    sub.serviceName,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: AppTypography.labelMd(
                                      color: AppColors.onSurface,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Row(
                                    children: [
                                      const Icon(Icons.schedule, size: 12, color: AppColors.secondary),
                                      const SizedBox(width: 4),
                                      Text(
                                        sub.slotTime.replaceAllMapped(
                                          RegExp(r'(\d{1,2}:\d{2}):00'),
                                          (m) => m.group(1) ?? '',
                                        ),
                                        style: AppTypography.bodySm(
                                          color: AppColors.onSurfaceVariant,
                                        ).copyWith(fontSize: 11),
                                      ),
                                      const SizedBox(width: 8),
                                      Text(
                                        '• ${sub.quantity} khách',
                                        style: AppTypography.bodySm(
                                          color: AppColors.onSurfaceVariant,
                                        ).copyWith(fontSize: 11),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: isCheckedIn
                                    ? const Color(0xFFE0F2F1)
                                    : isConfirmed
                                        ? const Color(0xFFE8F5E9)
                                        : AppColors.surfaceContainerHigh,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    isCheckedIn
                                        ? Icons.check_circle
                                        : isConfirmed
                                            ? Icons.access_time_filled
                                            : Icons.info,
                                    size: 11,
                                    color: isCheckedIn
                                        ? AppColors.secondary
                                        : isConfirmed
                                            ? const Color(0xFF2E7D32)
                                            : AppColors.onSurfaceVariant,
                                  ),
                                  const SizedBox(width: 3),
                                  Text(
                                    isCheckedIn
                                        ? 'ĐÃ VÀO BÃI'
                                        : isConfirmed
                                            ? 'CHỜ SOÁT VÉ'
                                            : sub.status.name.toUpperCase(),
                                    style: TextStyle(
                                      fontSize: 10,
                                      fontWeight: FontWeight.bold,
                                      color: isCheckedIn
                                          ? AppColors.secondary
                                          : isConfirmed
                                              ? const Color(0xFF2E7D32)
                                              : AppColors.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 16),

            // SUPPORT NOTICE
            Container(
              padding: const EdgeInsets.all(AppShapes.spaceSm),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: AppShapes.radiusDefault,
              ),
              child: Row(
                children: [
                  const Icon(Icons.headset_mic_outlined,
                      size: 20, color: AppColors.secondary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: LocalizedText(
                      'Cần hỗ trợ tại bãi biển? Gọi hotline điều phối Danang Ocean: 1900 6868',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurfaceVariant,
                      ).copyWith(fontSize: 11),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildMetaBox(IconData icon, String label, String value) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 12, color: AppColors.secondary),
              const SizedBox(width: 4),
              Expanded(
                child: LocalizedText(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.bodySm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 10),
                ),
              ),
            ],
          ),
          const SizedBox(height: 2),
          LocalizedText(
            value,
            style: AppTypography.labelMd(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w700,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
