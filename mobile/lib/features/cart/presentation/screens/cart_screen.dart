import 'package:mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/app_pill_button.dart';
import '../../../checkout/presentation/screens/checkout_screen.dart';
import '../../../experience_detail/presentation/widgets/service_availability_sheet.dart';
import '../../data/cart_manager.dart';

class CartItemData {
  final String id;
  final ServiceModel service;
  ServiceSlotModel slot;
  int quantity;
  bool isSelected;

  CartItemData({
    required this.id,
    required this.service,
    required this.slot,
    required this.quantity,
    this.isSelected = true,
  });

  int get subtotal => service.price * quantity;
}

class CartScreen extends StatefulWidget {
  final VoidCallback? onBack;

  const CartScreen({super.key, this.onBack});

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  late List<CartItemData> _items;

  @override
  void initState() {
    super.initState();
    _items = CartManager.instance.items;
  }

  int get _selectedSubtotal => _items
      .where((i) => i.isSelected)
      .fold(0, (sum, i) => sum + i.subtotal);

  int get _selectedCount =>
      _items.where((i) => i.isSelected).length;

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

  void _openChangeSlot(CartItemData item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return ServiceAvailabilitySheet(
          service: item.service,
          isCartEdit: true,
          initialQuantity: item.quantity,
          initialDate: item.slot.date,
          initialSlotId: item.slot.id,
          onUpdateCart: (newSlot, newGuests) {
            setState(() {
              item.slot = ServiceSlotModel(
                id: newSlot.id,
                serviceId: item.service.id,
                date: DateTime.tryParse(newSlot.date) ?? item.slot.date,
                startTime: newSlot.startTime,
                endTime: newSlot.endTime,
                capacity: newSlot.availableCapacity ?? item.slot.capacity,
                bookedCount: 0,
              );
              item.quantity = newGuests;
            });
          },
        );
      },
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
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: widget.onBack ?? () => Navigator.pop(context),
        ),
        title: LocalizedText(
          'Giỏ hàng (${_items.length})',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        actions: [
          if (_items.isNotEmpty)
            TextButton.icon(
              onPressed: () {
                setState(() => CartManager.instance.clear());
              },
              icon: const Icon(Icons.delete_sweep, size: 18, color: AppColors.outline),
              label: LocalizedText(
                'Xóa tất cả',
                style: AppTypography.labelSm(color: AppColors.outline),
              ),
            ),
        ],
      ),
      body: _items.isEmpty
          ? _buildEmptyCart()
          : Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppShapes.gutterMobile,
                      vertical: 8,
                    ),
                    children: [
                      // URGENCY INVENTORY LOCK ALERT
                      Container(
                        padding: const EdgeInsets.all(AppShapes.spaceSm),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerLow,
                          borderRadius: AppShapes.radiusDefault,
                          border: Border(
                            left: BorderSide(
                              color: AppColors.primaryContainer,
                              width: 4,
                            ),
                          ),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 32,
                              height: 32,
                              decoration: const BoxDecoration(
                                color: AppColors.primaryFixed,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.timer,
                                  size: 18, color: AppColors.primary),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Flexible(
                                        child: LocalizedText(
                                          'Khung giờ biển giới hạn',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: AppTypography.labelMd(
                                            color: AppColors.onSurface,
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 6),
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 6, vertical: 1),
                                        decoration: BoxDecoration(
                                          color: AppColors.primaryFixed,
                                          borderRadius:
                                              BorderRadius.circular(8),
                                        ),
                                        child: LocalizedText(
                                          'Giữ chỗ tạm',
                                          style: AppTypography.labelSm(
                                            color: AppColors.primary,
                                          ).copyWith(fontSize: 10),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  LocalizedText(
                                    'Số lượng slot sáng sớm có hạn, hãy hoàn tất thanh toán để giữ chỗ.',
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
                      const SizedBox(height: 14),

                      // CART ITEMS LIST
                      ..._items.map((item) => _buildCartItemCard(item)),
                    ],
                  ),
                ),

                // STICKY BOTTOM CHECKOUT SUMMARY BAR
                Container(
                  padding: EdgeInsets.only(
                    left: AppShapes.gutterMobile,
                    right: AppShapes.gutterMobile,
                    top: 12,
                    bottom: MediaQuery.of(context).padding.bottom + 12,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    boxShadow: AppShapes.bottomNavShadow,
                    border: Border(
                      top: BorderSide(
                        color: AppColors.borderSubtle.withValues(alpha: 0.8),
                        width: 0.5,
                      ),
                    ),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            LocalizedText(
                              'Tạm tính ($_selectedCount dịch vụ):',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppTypography.bodySm(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            LocalizedText(
                              _formatPrice(_selectedSubtotal),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppTypography.headlineMd(
                                color: AppColors.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      AppPillButton(
                        label: 'Thanh toán',
                        variant: AppButtonVariant.primary,
                        trailingIcon: Icons.arrow_forward,
                        onPressed: _selectedCount > 0
                            ? () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => CheckoutScreen(
                                      selectedItems: _items
                                          .where((i) => i.isSelected)
                                          .toList(),
                                    ),
                                  ),
                                );
                              }
                            : null,
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildCartItemCard(CartItemData item) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
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
          // Vendor Header Row
          Row(
            children: [
              Checkbox(
                value: item.isSelected,
                activeColor: AppColors.secondary,
                onChanged: (val) {
                  setState(() => item.isSelected = val ?? false);
                },
              ),
              Container(
                width: 20,
                height: 20,
                decoration: const BoxDecoration(
                  color: AppColors.secondaryContainer,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.storefront,
                    size: 12, color: AppColors.onSecondaryContainer),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: LocalizedText(
                  item.service.vendorName,
                  style: AppTypography.labelMd(
                    color: AppColors.onSurface,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline,
                    size: 20, color: AppColors.outline),
                splashRadius: 18,
                onPressed: () {
                  setState(() => _items.remove(item));
                },
              ),
            ],
          ),
          const Divider(height: 8),

          // Item Details Row
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Thumbnail
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: SizedBox(
                  width: 72,
                  height: 72,
                  child: Image.network(
                    item.service.imageUrls.isNotEmpty
                        ? item.service.imageUrls.first
                        : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=200&auto=format&fit=crop&q=80',
                    fit: BoxFit.cover,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              // Info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LocalizedText(
                      item.service.name,
                      style: AppTypography.labelLg(
                        color: AppColors.onSurface,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.calendar_today,
                            size: 12, color: AppColors.secondary),
                        const SizedBox(width: 4),
                        LocalizedText(
                          '${item.slot.startTime} - ${item.slot.endTime}',
                          style: AppTypography.labelSm(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(Icons.group,
                            size: 12, color: AppColors.tertiary),
                        const SizedBox(width: 4),
                        Expanded(
                          child: LocalizedText(
                            '${item.quantity} khách • ${_formatPrice(item.service.price)}/khách',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.bodySm(
                              color: AppColors.onSurfaceVariant,
                            ).copyWith(fontSize: 11),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        InkWell(
                          onTap: () => _openChangeSlot(item),
                          borderRadius: BorderRadius.circular(8),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerHigh,
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.edit_calendar,
                                    size: 12, color: AppColors.secondary),
                                const SizedBox(width: 4),
                                LocalizedText(
                                  'Đổi lịch',
                                  style: AppTypography.labelSm(
                                    color: AppColors.secondary,
                                  ).copyWith(fontSize: 10),
                                ),
                              ],
                            ),
                          ),
                        ),
                        LocalizedText(
                          _formatPrice(item.subtotal),
                          textAlign: TextAlign.right,
                          style: AppTypography.headlineSm(
                            color: AppColors.primary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyCart() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppShapes.gutterMobile),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: const BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.shopping_bag_outlined,
                  size: 40, color: AppColors.secondary),
            ),
            const SizedBox(height: 16),
            LocalizedText(
              'Giỏ hàng của bạn đang trống',
              style: AppTypography.headlineSm(color: AppColors.onSurface),
            ),
            const SizedBox(height: 8),
            LocalizedText(
              'Hãy chọn các trải nghiệm biển yêu thích tại Đà Nẵng và thêm vào giỏ để chuẩn bị chuyến đi.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 20),
            AppPillButton(
              label: 'Khám phá trải nghiệm ngay',
              variant: AppButtonVariant.primary,
              onPressed: widget.onBack ?? () => Navigator.pop(context),
            ),
          ],
        ),
      ),
    );
  }
}
