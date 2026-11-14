import '../../../../core/data/vendor_repositories.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../orders/presentation/screens/sub_order_detail_screen.dart';

class ChatConversationScreen extends StatefulWidget {
  final String conversationId;
  final String customerName;
  final String subOrderCode;

  const ChatConversationScreen({
    super.key,
    required this.conversationId,
    required this.customerName,
    required this.subOrderCode,
  });

  @override
  State<ChatConversationScreen> createState() => _ChatConversationScreenState();
}

class _ChatConversationScreenState extends State<ChatConversationScreen> {
  final _db = VendorStore.instance;
  final _msgController = TextEditingController();
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
    _scrollToBottom();
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    _msgController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  bool _isMyMessage(MessageModel msg) {
    final s = msg.senderId.trim().toLowerCase();
    return s.isNotEmpty && (
        s == _db.currentVendor.id ||
        s == _db.currentUser.id);
  }

  Future<void> _sendMessage(String text) async {
    if (text.trim().isEmpty) return;
    try {
      await VendorChatRepository().sendMessage(widget.conversationId, text.trim());
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Không gửi được tin nhắn: $error')));
      return;
    }
    if (!mounted) return;
    _msgController.clear();
    _scrollToBottom();
  }

  @override
  Widget build(BuildContext context) {
    final messages = _db.messages[widget.conversationId] ?? [];
    final subOrder = _db.subOrders.firstWhere(
      (o) => o.subOrderCode == widget.subOrderCode,
      orElse: () => _db.subOrders.first,
    );

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.secondary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.secondaryContainer,
              ),
              child: Center(
                child: LocalizedText(
                  widget.customerName.isNotEmpty ? widget.customerName[0].toUpperCase() : 'K',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  LocalizedText(
                    widget.customerName,
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(width: 6, height: 6, decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.secondary)),
                      const SizedBox(width: 4),
                      const Flexible(
                        child: LocalizedText(
                          'Đang trực tuyến',
                          style: TextStyle(fontSize: 10, color: AppColors.secondary),
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
        actions: [
          IconButton(
            icon: const Icon(Icons.call_outlined, color: AppColors.secondary),
            onPressed: () {},
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Pinned Sub-Order Context Banner
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: AppShapes.radiusSm,
                boxShadow: AppShapes.shadowSm,
              ),
              child: Row(
                children: [
                  Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.surfing, color: AppColors.onSecondaryContainer, size: 20),
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
                                '#${subOrder.subOrderCode}',
                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.secondary),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const SizedBox(width: 4),
                            LocalizedText('• ${subOrder.quantity} khách', style: const TextStyle(fontSize: 10, color: AppColors.tertiary)),
                          ],
                        ),
                        LocalizedText(subOrder.serviceName, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.onSurface), maxLines: 1, overflow: TextOverflow.ellipsis),
                        LocalizedText('${subOrder.slotDate} • ${subOrder.slotTime}', style: const TextStyle(fontSize: 10, color: AppColors.tertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => SubOrderDetailScreen(subOrder: subOrder),
                        ),
                      );
                    },
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      backgroundColor: AppColors.secondaryContainer.withValues(alpha: 0.5),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        LocalizedText('Xem đơn', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer)),
                        Icon(Icons.chevron_right, size: 14, color: AppColors.onSecondaryContainer),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Messages Stream
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                itemCount: messages.length + 1, // +1 for initial payment banner
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _buildPaymentNoticeBubble(subOrder);
                  }
                  final msgIndex = index - 1;
                  final msg = messages[msgIndex];
                  final isMe = _isMyMessage(msg);

                  final prevMsg = msgIndex > 0 ? messages[msgIndex - 1] : null;
                  final nextMsg = msgIndex < messages.length - 1
                      ? messages[msgIndex + 1]
                      : null;

                  final isSameSenderAsPrev =
                      prevMsg != null && (_isMyMessage(prevMsg) == isMe);
                  final isLastOfGroup =
                      nextMsg == null || (_isMyMessage(nextMsg) != isMe);

                  return _buildMessageBubble(
                    msg: msg,
                    isMe: isMe,
                    isSameSenderAsPrev: isSameSenderAsPrev,
                    isLastOfGroup: isLastOfGroup,
                  );
                },
              ),
            ),

            // Quick Recommendation Reply Pills
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                children: [
                  _buildQuickPill('Có sẵn túi chống nước', 'Bên mình có trang bị sẵn túi chống nước cho mỗi khách rồi bạn nhé!'),
                  const SizedBox(width: 8),
                  _buildQuickPill('Gửi đồ tại quầy', 'Dạ có tủ gửi đồ an toàn tại quầy luôn bạn nhé.'),
                  const SizedBox(width: 8),
                  _buildQuickPill('Vị trí tập kết', _db.currentVendor.address ?? ''),
                ],
              ),
            ),

            // Input Bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                boxShadow: [
                  BoxShadow(color: Colors.black.withValues(alpha: 0.04), blurRadius: 8, offset: const Offset(0, -2)),
                ],
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.add_photo_alternate_outlined, color: AppColors.secondary, size: 22),
                    onPressed: () {
                      _sendMessage(_db.currentVendor.address ?? '');
                    },
                  ),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(24),
                      ),
                      child: TextField(
                        controller: _msgController,
                        style: const TextStyle(fontSize: 13),
                        decoration:  InputDecoration(
                          hintText: tr(context, 'Nhập tin nhắn cho khách...'),
                          hintStyle: TextStyle(fontSize: 12, color: AppColors.outline),
                          border: InputBorder.none,
                        ),
                        onSubmitted: _sendMessage,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: () => _sendMessage(_msgController.text),
                    borderRadius: BorderRadius.circular(20),
                    child: Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.secondary,
                      ),
                      child: const Icon(Icons.send, color: Colors.white, size: 18),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPaymentNoticeBubble(SubOrderModel order) {
    return Center(
      child: Container(
        margin: const EdgeInsets.only(top: 4, bottom: 16),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        constraints: const BoxConstraints(maxWidth: 330),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: AppColors.primary.withValues(alpha: 0.2),
            width: 0.8,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.primaryContainer.withValues(alpha: 0.6),
              ),
              child: const Icon(Icons.check_circle_rounded, color: AppColors.primary, size: 18),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const LocalizedText(
                    'Khách đã thanh toán',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSurface,
                    ),
                  ),
                  const SizedBox(height: 2),
                  LocalizedText(
                    '${order.customerName} đã đặt dịch vụ #${order.subOrderCode} • Tổng ${order.totalPrice.toStringAsFixed(0)} đ',
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.onSurfaceVariant,
                      height: 1.3,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageBubble({
    required MessageModel msg,
    required bool isMe,
    required bool isSameSenderAsPrev,
    required bool isLastOfGroup,
  }) {
    // Tách biệt rõ ràng: tin nhắn cùng người nói giãn 4px, đổi người nói giãn 14px
    final topSpacing = isSameSenderAsPrev ? 4.0 : 14.0;
    final customerInitial = widget.customerName.isNotEmpty
        ? widget.customerName[0].toUpperCase()
        : 'K';

    return Padding(
      padding: EdgeInsets.only(top: topSpacing, bottom: 2),
      child: Row(
        mainAxisAlignment: isMe ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!isMe) ...[
            if (isLastOfGroup)
              Container(
                width: 28,
                height: 28,
                margin: const EdgeInsets.only(right: 8, bottom: 2),
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.secondaryContainer,
                ),
                child: Center(
                  child: LocalizedText(
                    customerInitial,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppColors.onSecondaryContainer,
                    ),
                  ),
                ),
              )
            else
              const SizedBox(width: 36),
          ],
          Flexible(
            child: Container(
              constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * 0.74,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: isMe ? AppColors.secondary : AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(isMe ? 18 : (isSameSenderAsPrev ? 6 : 18)),
                  topRight: Radius.circular(isMe ? (isSameSenderAsPrev ? 6 : 18) : 18),
                  bottomLeft: Radius.circular(isMe ? 18 : (isLastOfGroup ? 4 : 6)),
                  bottomRight: Radius.circular(isMe ? (isLastOfGroup ? 4 : 6) : 18),
                ),
                border: isMe
                    ? null
                    : Border.all(
                        color: AppColors.outlineVariant.withValues(alpha: 0.35),
                        width: 0.8,
                      ),
                boxShadow: [
                  BoxShadow(
                    color: isMe
                        ? AppColors.secondary.withValues(alpha: 0.18)
                        : Colors.black.withValues(alpha: 0.04),
                    blurRadius: 6,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment:
                    isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  LocalizedText(
                    msg.content,
                    style: TextStyle(
                      fontSize: 13.5,
                      color: isMe ? Colors.white : AppColors.onSurface,
                      height: 1.42,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      LocalizedText(
                        '${msg.sentAt.hour.toString().padLeft(2, '0')}:${msg.sentAt.minute.toString().padLeft(2, '0')}',
                        style: TextStyle(
                          fontSize: 10,
                          color: isMe
                              ? Colors.white.withValues(alpha: 0.75)
                              : AppColors.outline,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (isMe) ...[
                        const SizedBox(width: 4),
                        const Icon(
                          Icons.done_all,
                          size: 13,
                          color: Colors.white70,
                        ),
                      ],
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

  Widget _buildQuickPill(String title, String fullText) {
    return InkWell(
      onTap: () => _sendMessage(fullText),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(16),
          boxShadow: AppShapes.shadowSm,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.flash_on, size: 13, color: AppColors.secondary),
            const SizedBox(width: 4),
            LocalizedText(title, style: const TextStyle(fontSize: 11, color: AppColors.onSurface)),
          ],
        ),
      ),
    );
  }
}
