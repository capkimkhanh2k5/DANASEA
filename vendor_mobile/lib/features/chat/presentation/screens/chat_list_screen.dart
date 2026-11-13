import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import 'chat_conversation_screen.dart';
import '../../../notifications/presentation/screens/notification_safety_screen.dart';
import '../../../profile/presentation/screens/business_profile_screen.dart';

class ChatListScreen extends StatefulWidget {
  final VoidCallback? onOpenNotifications;
  final VoidCallback? onOpenProfile;

  const ChatListScreen({
    super.key,
    this.onOpenNotifications,
    this.onOpenProfile,
  });

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  final _db = VendorStore.instance;
  String _searchQuery = '';
  String _filter = 'all'; // all, unread, departing

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  void _openNotifications() {
    if (widget.onOpenNotifications != null) {
      widget.onOpenNotifications!();
    } else {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const NotificationSafetyScreen()),
      );
    }
  }

  void _openProfile() {
    if (widget.onOpenProfile != null) {
      widget.onOpenProfile!();
    } else {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const BusinessProfileScreen()),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final conversations = _db.conversations.where((c) {
      if (_filter == 'unread' && c.unreadCount == 0) return false;
      if (_searchQuery.isNotEmpty) {
        final q = _searchQuery.toLowerCase();
        final matchName = c.customerName.toLowerCase().contains(q);
        final matchCode = (c.subOrderCode?.toLowerCase().contains(q) ?? false) ||
            (c.masterOrderCode?.toLowerCase().contains(q) ?? false);
        final matchMsg = c.lastMessage.toLowerCase().contains(q);
        return matchName || matchCode || matchMsg;
      }
      return true;
    }).toList();

    final unreadTotal = _db.conversations.fold<int>(0, (sum, c) => sum + c.unreadCount);

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                LocalizedText('DANASEA',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: AppColors.secondary,
                        )),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: AppColors.primaryContainer, borderRadius: BorderRadius.circular(10)),
                  child: const LocalizedText('VENDOR', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                ),
              ],
            ),
            const LocalizedText('Tin nhắn khách hàng', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined, color: AppColors.secondary),
            onPressed: _openNotifications,
          ),
          IconButton(
            icon: const Icon(Icons.account_circle, color: AppColors.primary),
            onPressed: _openProfile,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Header title & count
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Row(
                          children: [
                            Icon(Icons.forum, size: 22, color: AppColors.primary),
                            SizedBox(width: 8),
                            Expanded(
                              child: LocalizedText(
                                'Tin nhắn khách hàng',
                                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(color: AppColors.secondaryContainer, borderRadius: BorderRadius.circular(12)),
                        child: LocalizedText('${_db.conversations.length} hội thoại',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  const LocalizedText('Tương tác trực tiếp & hỗ trợ khách trải nghiệm thể thao biển', style: TextStyle(fontSize: 12, color: AppColors.tertiary)),
                  const SizedBox(height: 10),

                  // Search input
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: AppShapes.shadowSm,
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.search, size: 20, color: AppColors.tertiary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            onChanged: (val) => setState(() => _searchQuery = val.trim()),
                            decoration:  InputDecoration(
                              hintText: tr(context, 'Tìm cuộc hội thoại, mã đơn, khách hàng...'),
                              hintStyle: TextStyle(fontSize: 12, color: AppColors.outline),
                              border: InputBorder.none,
                            ),
                            style: const TextStyle(fontSize: 13),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Horizontal Filter Tabs
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
              child: Row(
                children: [
                  _buildTab('all', 'Tất cả tin nhắn', _db.conversations.length),
                  const SizedBox(width: 8),
                  _buildTab('unread', 'Chưa đọc', unreadTotal),
                  const SizedBox(width: 8),
                  _buildTab('departing', 'Khách sắp xuất bến', 2),
                ],
              ),
            ),

            // Conversation Stream
            Expanded(
              child: conversations.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.chat_bubble_outline, size: 48, color: AppColors.tertiary),
                          const SizedBox(height: 8),
                          const LocalizedText('Không tìm thấy cuộc trò chuyện nào', style: TextStyle(fontSize: 13, color: AppColors.tertiary)),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
                      itemCount: conversations.length,
                      itemBuilder: (context, index) {
                        final conv = conversations[index];
                        return _buildConversationCard(conv);
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTab(String key, String label, int count) {
    final active = _filter == key;
    return InkWell(
      onTap: () => setState(() => _filter = key),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.primary : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            LocalizedText(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: active ? FontWeight.bold : FontWeight.normal,
                color: active ? Colors.white : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color: active ? Colors.white.withValues(alpha: 0.25) : AppColors.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: LocalizedText(
                '$count',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: active ? Colors.white : AppColors.tertiary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildConversationCard(ConversationModel conv) {
    final hasUnread = conv.unreadCount > 0;

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusMd,
        boxShadow: AppShapes.shadowSm,
      ),
      child: InkWell(
        onTap: () {
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ChatConversationScreen(
                conversationId: conv.conversationId,
                customerName: conv.customerName,
                subOrderCode: conv.subOrderCode ?? '',
              ),
            ),
          );
        },
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Avatar
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.secondaryContainer,
                ),
                child: Center(
                  child: LocalizedText(
                    conv.customerName.isNotEmpty ? conv.customerName[0].toUpperCase() : 'K',
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.onSecondaryContainer),
                  ),
                ),
              ),
              const SizedBox(width: 12),

              // Info & last message
              Expanded(
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
                                child: LocalizedText(
                                  conv.customerName,
                                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                                decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(6)),
                                child: LocalizedText('#${conv.subOrderCode}', style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.secondary)),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 6),
                        LocalizedText(
                          '${conv.lastMessageTime.hour}:${conv.lastMessageTime.minute.toString().padLeft(2, '0')}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: hasUnread ? FontWeight.bold : FontWeight.normal,
                            color: hasUnread ? AppColors.primary : AppColors.tertiary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    LocalizedText(
                      conv.lastMessage,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: hasUnread ? FontWeight.bold : FontWeight.normal,
                        color: hasUnread ? AppColors.onSurface : AppColors.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Row(
                            children: [
                              const Icon(Icons.surfing, size: 12, color: AppColors.secondary),
                              const SizedBox(width: 4),
                              Expanded(
                                child: LocalizedText(
                                  '${conv.serviceName} • 28/10',
                                  style: const TextStyle(fontSize: 10, color: AppColors.tertiary),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        if (hasUnread)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                            decoration: BoxDecoration(color: AppColors.primaryContainer, borderRadius: BorderRadius.circular(10)),
                            child: LocalizedText('${conv.unreadCount} tin mới', style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                          )
                        else
                          const Icon(Icons.done_all, size: 14, color: AppColors.secondary),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
