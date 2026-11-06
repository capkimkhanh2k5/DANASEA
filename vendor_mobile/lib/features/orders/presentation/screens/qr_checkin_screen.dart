import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/vendor_button.dart';
import '../../../../core/widgets/toast_notification.dart';
import 'sub_order_detail_screen.dart';

class QrCheckinScreen extends StatefulWidget {
  final String? initialLookupCode;

  const QrCheckinScreen({super.key, this.initialLookupCode});

  @override
  State<QrCheckinScreen> createState() => _QrCheckinScreenState();
}

class _QrCheckinScreenState extends State<QrCheckinScreen>
    with SingleTickerProviderStateMixin {
  final _db = VendorMockDatabase.instance;
  late final MobileScannerController _scannerController;
  late final AnimationController _animController;
  late final Animation<double> _laserAnimation;

  bool _isProcessing = false;
  bool _isTorchOn = false;

  @override
  void initState() {
    super.initState();
    _scannerController = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      facing: CameraFacing.back,
      torchEnabled: false,
    );

    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2200),
    )..repeat(reverse: true);

    _laserAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeInOut),
    );

    if (widget.initialLookupCode != null &&
        widget.initialLookupCode!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _handleCode(widget.initialLookupCode!);
      });
    }
  }

  @override
  void dispose() {
    _animController.dispose();
    _scannerController.dispose();
    super.dispose();
  }

  void _toggleTorch() async {
    try {
      await _scannerController.toggleTorch();
      setState(() {
        _isTorchOn = !_isTorchOn;
      });
    } catch (_) {}
  }

  void _switchCamera() async {
    try {
      await _scannerController.switchCamera();
    } catch (_) {}
  }

  void _onDetect(BarcodeCapture capture) {
    if (_isProcessing) return;

    for (final barcode in capture.barcodes) {
      final raw = barcode.rawValue ?? barcode.displayValue;
      if (raw != null && raw.trim().isNotEmpty) {
        _isProcessing = true;
        HapticFeedback.mediumImpact();
        _handleCode(raw.trim());
        break;
      }
    }
  }

  String _extractCode(String raw) {
    var code = raw.trim();

    // Handle JSON format e.g. {"order_code":"DNS-8924-1"} or {"code":"DNS-8924-1"}
    if (code.startsWith('{') && code.endsWith('}')) {
      try {
        final Map<String, dynamic> json = jsonDecode(code);
        final extracted = json['sub_order_code'] ??
            json['subOrderCode'] ??
            json['order_code'] ??
            json['orderCode'] ??
            json['code'] ??
            json['id'];
        if (extracted != null) {
          code = extracted.toString().trim();
        }
      } catch (_) {}
    }

    // Handle URL containing code e.g. https://danasea.vn/tickets/DNS-8924-1
    if (code.contains('/')) {
      final segments = Uri.tryParse(code)?.pathSegments;
      if (segments != null && segments.isNotEmpty) {
        code = segments.last;
      }
    }

    // Strip leading hash if any
    return code.replaceAll('#', '').trim();
  }

  void _handleCode(String rawInput) {
    final cleaned = _extractCode(rawInput).toLowerCase();

    SubOrderModel? match;
    for (final o in _db.subOrders) {
      if (o.id.toLowerCase() == cleaned ||
          o.subOrderCode.toLowerCase() == cleaned ||
          o.masterOrderCode.toLowerCase() == cleaned) {
        match = o;
        break;
      }
    }

    _showResultBottomSheet(
      searchedCode: rawInput,
      order: match,
    ).then((_) {
      // Re-enable scanner once bottom sheet is closed
      Future.delayed(const Duration(milliseconds: 350), () {
        if (mounted) {
          setState(() {
            _isProcessing = false;
          });
        }
      });
    });
  }

  Future<void> _showResultBottomSheet({
    required String searchedCode,
    required SubOrderModel? order,
  }) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (bottomSheetContext) {
        final vendorName = _db.currentVendor.businessName.isNotEmpty
            ? _db.currentVendor.businessName
            : 'Danabeach Club';

        final isFound = order != null;
        final isUsed = isFound && order.checkedInAt != null;
        final isValid = isFound && !isUsed;

        return Container(
          decoration: const BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 12,
            bottom: MediaQuery.of(bottomSheetContext).padding.bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Bottom sheet drag handle
              Center(
                child: Container(
                  width: 44,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: AppColors.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),

              // Status Header Banner
              if (isValid)
                _buildStatusBanner(
                  icon: Icons.check_circle,
                  iconColor: AppColors.secondary,
                  bgColor: AppColors.secondaryContainer,
                  title: 'Xác nhận thành công!',
                  subtitle: 'Vé hợp lệ & Đủ điều kiện ra bãi',
                  subtitleColor: AppColors.secondary,
                )
              else if (isUsed)
                _buildStatusBanner(
                  icon: Icons.history,
                  iconColor: AppColors.primary,
                  bgColor: AppColors.primaryFixed,
                  title: 'Vé này đã được sử dụng',
                  subtitle: 'Đã check-in lúc ${_formatDateTime(order.checkedInAt!)}',
                  subtitleColor: AppColors.primary,
                )
              else
                _buildStatusBanner(
                  icon: Icons.error_outline,
                  iconColor: AppColors.error,
                  bgColor: AppColors.errorContainer,
                  title: 'Mã QR không hợp lệ',
                  subtitle: 'Không tìm thấy vé hoặc không thuộc $vendorName',
                  subtitleColor: AppColors.error,
                ),

              const SizedBox(height: 16),

              // Detail Section
              if (isFound) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Flexible(
                            child: Text(
                              '#${order.subOrderCode}',
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: AppColors.secondary,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '${order.slotDate} • ${order.slotTime}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.tertiary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                      const Divider(height: 18),
                      Text(
                        order.serviceName,
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Khách hàng: ${order.customerName} (${order.customerPhone})',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Số lượng: ${order.quantity} người lớn • Tổng thu: ${_formatCurrency(order.subtotalAmount)}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.tertiary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),

                // Safety & Digital Waiver badge
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Row(
                        children: [
                          Icon(Icons.shield_outlined,
                              size: 16, color: AppColors.secondary),
                          SizedBox(width: 8),
                          Text(
                            'Cam kết an toàn biển',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.onSurface,
                            ),
                          ),
                        ],
                      ),
                      Row(
                        children: [
                          Icon(
                            order.waiverAccepted ? Icons.verified : Icons.warning_amber_rounded,
                            size: 14,
                            color: order.waiverAccepted ? AppColors.secondary : AppColors.error,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            order.waiverAccepted ? 'Đã ký điện tử' : 'Chưa ký cam kết',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: order.waiverAccepted ? AppColors.secondary : AppColors.error,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),

                // Action buttons
                if (isValid) ...[
                  VendorButton(
                    text: 'Xác nhận vào bãi nhận thiết bị',
                    icon: Icons.kayaking,
                    onPressed: () {
                      _db.checkInSubOrder(order.subOrderId);
                      Navigator.pop(bottomSheetContext);
                      ToastNotification.showSuccess(
                        context,
                        'Check-in thành công cho #${order.subOrderCode}!',
                      );
                    },
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(bottomSheetContext);
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => SubOrderDetailScreen(subOrder: order),
                        ),
                      );
                    },
                    icon: const Icon(Icons.receipt_long,
                        size: 16, color: AppColors.secondary),
                    label: const Text(
                      'Xem chi tiết đơn hàng',
                      style: TextStyle(fontSize: 13, color: AppColors.secondary),
                    ),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(double.infinity, 44),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(22),
                      ),
                    ),
                  ),
                ] else ...[
                  VendorButton(
                    text: 'Tiếp tục quét mã khác',
                    icon: Icons.qr_code_scanner,
                    onPressed: () => Navigator.pop(bottomSheetContext),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(bottomSheetContext);
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => SubOrderDetailScreen(subOrder: order),
                        ),
                      );
                    },
                    icon: const Icon(Icons.receipt_long,
                        size: 16, color: AppColors.secondary),
                    label: const Text(
                      'Xem chi tiết đơn hàng',
                      style: TextStyle(fontSize: 13, color: AppColors.secondary),
                    ),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(double.infinity, 44),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(22),
                      ),
                    ),
                  ),
                ],
              ] else ...[
                // Invalid state body
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.errorContainer.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Mã vé "$searchedCode" không tồn tại trong hệ thống đối tác của $vendorName. Khóa bảo mật không khớp hoặc đơn hàng thuộc nhà cung cấp khác.',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.onErrorContainer,
                      height: 1.4,
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                VendorButton(
                  text: 'Quét lại',
                  icon: Icons.qr_code_scanner,
                  onPressed: () => Navigator.pop(bottomSheetContext),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(bottomSheetContext);
                    _openManualCodeDialog();
                  },
                  icon: const Icon(Icons.keyboard,
                      size: 16, color: AppColors.secondary),
                  label: const Text(
                    'Nhập mã vé thủ công',
                    style: TextStyle(fontSize: 13, color: AppColors.secondary),
                  ),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(double.infinity, 44),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(22),
                    ),
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }

  Widget _buildStatusBanner({
    required IconData icon,
    required Color iconColor,
    required Color bgColor,
    required String title,
    required String subtitle,
    required Color subtitleColor,
  }) {
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(shape: BoxShape.circle, color: bgColor),
          child: Icon(icon, color: iconColor, size: 26),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: AppColors.onSurface,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 12,
                  color: subtitleColor,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _openManualCodeDialog() {
    final controller = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final pendingOrders = _db.subOrders
            .where((o) => o.checkedInAt == null)
            .take(3)
            .toList();

        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(ctx).viewInsets.bottom,
          ),
          child: Container(
            decoration: const BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
            ),
            padding: EdgeInsets.only(
              left: 20,
              right: 20,
              top: 14,
              bottom: MediaQuery.of(ctx).padding.bottom + 20,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 44,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: AppColors.outlineVariant,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const Text(
                  'Nhập mã vé thủ công',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Nhập mã đơn con (ví dụ: DNS-8924-1) in trên vé điện tử của khách hàng.',
                  style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: controller,
                  autofocus: true,
                  textCapitalization: TextCapitalization.characters,
                  decoration: InputDecoration(
                    hintText: 'DNS-8924-1...',
                    prefixIcon: const Icon(Icons.pin, color: AppColors.secondary),
                    filled: true,
                    fillColor: AppColors.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                  onSubmitted: (val) {
                    if (val.trim().isNotEmpty) {
                      Navigator.pop(ctx);
                      _handleCode(val);
                    }
                  },
                ),
                if (pendingOrders.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  const Text(
                    'Đơn chờ check-in gần nhất:',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: AppColors.tertiary,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: pendingOrders.map((o) {
                      return ActionChip(
                        label: Text(
                          '#${o.subOrderCode} (${o.customerName})',
                          style: const TextStyle(fontSize: 11),
                        ),
                        backgroundColor: AppColors.surfaceContainerLow,
                        onPressed: () {
                          Navigator.pop(ctx);
                          _handleCode(o.subOrderCode);
                        },
                      );
                    }).toList(),
                  ),
                ],
                const SizedBox(height: 16),
                VendorButton(
                  text: 'Kiểm tra mã vé',
                  icon: Icons.search,
                  onPressed: () {
                    if (controller.text.trim().isNotEmpty) {
                      Navigator.pop(ctx);
                      _handleCode(controller.text);
                    }
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _openPendingOrdersSheet() {
    final pendingOrders =
        _db.subOrders.where((o) => o.checkedInAt == null).toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(ctx).size.height * 0.75,
          ),
          decoration: const BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 14,
            bottom: MediaQuery.of(ctx).padding.bottom + 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 44,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 14),
                  decoration: BoxDecoration(
                    color: AppColors.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Đơn hàng chờ check-in',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${pendingOrders.length} vé',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppColors.secondary,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (pendingOrders.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 32),
                  child: Center(
                    child: Text(
                      'Không có đơn hàng nào đang chờ check-in.',
                      style: TextStyle(
                        fontSize: 13,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ),
                )
              else
                Expanded(
                  child: ListView.separated(
                    itemCount: pendingOrders.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (_, index) {
                      final o = pendingOrders[index];
                      return InkWell(
                        onTap: () {
                          Navigator.pop(ctx);
                          _handleCode(o.subOrderCode);
                        },
                        borderRadius: BorderRadius.circular(14),
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(color: AppColors.outlineVariant),
                          ),
                          child: Row(
                            children: [
                              Container(
                                width: 40,
                                height: 40,
                                decoration: BoxDecoration(
                                  color: AppColors.secondaryContainer,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: const Icon(Icons.confirmation_number,
                                    color: AppColors.secondary, size: 20),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '#${o.subOrderCode} • ${o.customerName}',
                                      style: const TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.bold,
                                        color: AppColors.onSurface,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      '${o.serviceName} • ${o.slotTime}',
                                      style: const TextStyle(
                                        fontSize: 11,
                                        color: AppColors.onSurfaceVariant,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                              const Icon(Icons.chevron_right,
                                  color: AppColors.tertiary, size: 20),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  String _formatDateTime(DateTime dt) {
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')} ngày ${dt.day}/${dt.month}/${dt.year}';
  }

  String _formatCurrency(double amount) {
    final str = amount.toInt().toString();
    final buffer = StringBuffer();
    for (int i = 0; i < str.length; i++) {
      if (i > 0 && (str.length - i) % 3 == 0) {
        buffer.write('.');
      }
      buffer.write(str[i]);
    }
    return '${buffer.toString()} đ';
  }

  @override
  Widget build(BuildContext context) {
    final pendingCount =
        _db.subOrders.where((o) => o.checkedInAt == null).length;

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Live Camera Preview
          MobileScanner(
            controller: _scannerController,
            onDetect: _onDetect,
            errorBuilder: (context, error) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(28.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.videocam_off,
                          size: 60, color: Colors.white60),
                      const SizedBox(height: 16),
                      const Text(
                        'Không thể khởi động Camera',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Vui lòng cấp quyền Camera cho ứng dụng trong Cài đặt thiết bị hoặc khởi động lại ứng dụng.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.75),
                          fontSize: 12,
                        ),
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        onPressed: _openManualCodeDialog,
                        icon: const Icon(Icons.keyboard),
                        label: const Text('Nhập mã vé thủ công'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),

          // Viewfinder Overlay with Reticle & Animated Laser
          AnimatedBuilder(
            animation: _laserAnimation,
            builder: (context, child) {
              return CustomPaint(
                painter: _ScannerOverlayPainter(
                  laserProgress: _laserAnimation.value,
                ),
              );
            },
          ),

          // Top App Bar Scrim & Controls
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: EdgeInsets.only(
                top: MediaQuery.of(context).padding.top + 8,
                bottom: 16,
                left: 12,
                right: 12,
              ),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.black87, Colors.transparent],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back, color: Colors.white),
                    onPressed: () => Navigator.of(context).maybePop(),
                  ),
                  const SizedBox(width: 4),
                  const Expanded(
                    child: Text(
                      'Quét mã vé check-in',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: Icon(
                      _isTorchOn ? Icons.flash_on : Icons.flash_off,
                      color: _isTorchOn ? Colors.amber : Colors.white,
                    ),
                    onPressed: _toggleTorch,
                    tooltip: 'Bật/tắt đèn flash',
                  ),
                  IconButton(
                    icon: const Icon(Icons.flip_camera_ios, color: Colors.white),
                    onPressed: _switchCamera,
                    tooltip: 'Đổi camera',
                  ),
                ],
              ),
            ),
          ),

          // Bottom Action Panel
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: EdgeInsets.only(
                top: 24,
                bottom: MediaQuery.of(context).padding.bottom + 20,
                left: 20,
                right: 20,
              ),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.transparent, Colors.black87, Colors.black],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  stops: [0.0, 0.45, 1.0],
                ),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Instruction Pill
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.65),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.15),
                      ),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.qr_code_scanner,
                            size: 15, color: Colors.white70),
                        SizedBox(width: 8),
                        Text(
                          'Hướng camera vào mã QR vé của khách',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.white,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),

                  // Action Buttons: Manual Input + Pending Queue
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _openManualCodeDialog,
                          icon: const Icon(Icons.keyboard_alt_outlined,
                              size: 16, color: Colors.white),
                          label: const Text(
                            'Nhập mã thủ công',
                            style:
                                TextStyle(fontSize: 12, color: Colors.white),
                          ),
                          style: OutlinedButton.styleFrom(
                            backgroundColor: Colors.white.withValues(alpha: 0.12),
                            side: BorderSide(
                              color: Colors.white.withValues(alpha: 0.25),
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(22),
                            ),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _openPendingOrdersSheet,
                          icon: const Icon(Icons.list_alt,
                              size: 16, color: Colors.white),
                          label: Text(
                            'Chờ check-in ($pendingCount)',
                            style: const TextStyle(
                                fontSize: 12, color: Colors.white),
                          ),
                          style: OutlinedButton.styleFrom(
                            backgroundColor: Colors.white.withValues(alpha: 0.12),
                            side: BorderSide(
                              color: Colors.white.withValues(alpha: 0.25),
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(22),
                            ),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ScannerOverlayPainter extends CustomPainter {
  final double laserProgress;

  _ScannerOverlayPainter({required this.laserProgress});

  @override
  void paint(Canvas canvas, Size size) {
    const scanBoxSize = 260.0;
    final left = (size.width - scanBoxSize) / 2;
    final top = (size.height - scanBoxSize) / 2 - 20;
    final scanRect = Rect.fromLTWH(left, top, scanBoxSize, scanBoxSize);

    // Dark semi-transparent background with clear hole
    final backgroundPath = Path()
      ..addRect(Rect.fromLTWH(0, 0, size.width, size.height));
    final cutoutPath = Path()
      ..addRRect(RRect.fromRectAndRadius(scanRect, const Radius.circular(16)));

    final overlayPath =
        Path.combine(PathOperation.difference, backgroundPath, cutoutPath);

    final bgPaint = Paint()..color = Colors.black.withValues(alpha: 0.55);
    canvas.drawPath(overlayPath, bgPaint);

    // Border around scan box
    final borderPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.25)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
    canvas.drawRRect(
      RRect.fromRectAndRadius(scanRect, const Radius.circular(16)),
      borderPaint,
    );

    // 4 Corner Brackets (Teal)
    const cornerLength = 28.0;
    const cornerRadius = 16.0;
    final cornerPaint = Paint()
      ..color = AppColors.secondary
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.0
      ..strokeCap = StrokeCap.round;

    // Top-Left
    final tlPath = Path()
      ..moveTo(left, top + cornerLength)
      ..lineTo(left, top + cornerRadius)
      ..quadraticBezierTo(left, top, left + cornerRadius, top)
      ..lineTo(left + cornerLength, top);
    canvas.drawPath(tlPath, cornerPaint);

    // Top-Right
    final trPath = Path()
      ..moveTo(left + scanBoxSize - cornerLength, top)
      ..lineTo(left + scanBoxSize - cornerRadius, top)
      ..quadraticBezierTo(
        left + scanBoxSize,
        top,
        left + scanBoxSize,
        top + cornerRadius,
      )
      ..lineTo(left + scanBoxSize, top + cornerLength);
    canvas.drawPath(trPath, cornerPaint);

    // Bottom-Left
    final blPath = Path()
      ..moveTo(left, top + scanBoxSize - cornerLength)
      ..lineTo(left, top + scanBoxSize - cornerRadius)
      ..quadraticBezierTo(
        left,
        top + scanBoxSize,
        left + cornerRadius,
        top + scanBoxSize,
      )
      ..lineTo(left + cornerLength, top + scanBoxSize);
    canvas.drawPath(blPath, cornerPaint);

    // Bottom-Right
    final brPath = Path()
      ..moveTo(left + scanBoxSize - cornerLength, top + scanBoxSize)
      ..lineTo(left + scanBoxSize - cornerRadius, top + scanBoxSize)
      ..quadraticBezierTo(
        left + scanBoxSize,
        top + scanBoxSize,
        left + scanBoxSize,
        top + scanBoxSize - cornerRadius,
      )
      ..lineTo(left + scanBoxSize, top + scanBoxSize - cornerLength);
    canvas.drawPath(brPath, cornerPaint);

    // Animated Laser Line
    final laserY = top + (scanBoxSize * laserProgress);
    final laserPaint = Paint()
      ..shader = LinearGradient(
        colors: [
          AppColors.secondary.withValues(alpha: 0.0),
          AppColors.secondary,
          AppColors.secondary.withValues(alpha: 0.0),
        ],
        stops: const [0.0, 0.5, 1.0],
      ).createShader(Rect.fromLTWH(left, laserY, scanBoxSize, 2))
      ..strokeWidth = 2.5;

    canvas.drawLine(
      Offset(left + 8, laserY),
      Offset(left + scanBoxSize - 8, laserY),
      laserPaint,
    );
  }

  @override
  bool shouldRepaint(covariant _ScannerOverlayPainter oldDelegate) {
    return oldDelegate.laserProgress != laserProgress;
  }
}
