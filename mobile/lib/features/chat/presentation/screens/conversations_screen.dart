import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/conversation_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import 'vendor_chat_screen.dart';

class ConversationsScreen extends StatefulWidget {
  const ConversationsScreen({super.key});

  @override
  State<ConversationsScreen> createState() => _ConversationsScreenState();
}

class _ConversationsScreenState extends State<ConversationsScreen> {
  final ConversationRepository _repository = ConversationRepository.instance;
  final TextEditingController _searchController = TextEditingController();

  int _selectedPillIndex = 0;
  List<ConversationModel> _conversations = const [];
  bool _isLoading = true;
  bool _isCreatingConversation = false;
  String? _errorMessage;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _searchController.addListener(() {
      final next = _searchController.text.trim();
      if (next != _searchQuery) {
        setState(() => _searchQuery = next);
      }
    });
    _loadConversations();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadConversations() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final list = await _repository.listConversations(page: 0, size: 50);
      if (!mounted) return;
      setState(() {
        _conversations = list;
        _isLoading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.message;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _errorMessage = 'Không thể tải danh sách hội thoại.';
        _isLoading = false;
      });
    }
  }

  List<ConversationModel> get _filteredConversations {
    return _conversations.where((conv) {
      if (_selectedPillIndex == 1 && conv.unreadCount <= 0) {
        return false;
      }
      if (_selectedPillIndex == 2 &&
          (conv.masterOrderId == null || conv.masterOrderId!.isEmpty)) {
        return false;
      }
      if (_searchQuery.isNotEmpty) {
        final q = _searchQuery.toLowerCase();
        final matchVendor = conv.vendorName.toLowerCase().contains(q);
        final matchOrder =
            (conv.masterOrderId ?? '').toLowerCase().contains(q);
        final matchMsg = conv.lastMessage.toLowerCase().contains(q);
        final matchService =
            (conv.serviceName ?? '').toLowerCase().contains(q);
        if (!matchVendor && !matchOrder && !matchMsg && !matchService) {
          return false;
        }
      }
      return true;
    }).toList();
  }

  Future<void> _openCreateConversationSheet() async {
    if (_isCreatingConversation) return;
    setState(() => _isCreatingConversation = true);
    List<MasterOrderModel> orders = const [];
    try {
      orders = await OrderRepository.instance.listMyOrders(page: 0, size: 30);
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
      if (mounted) setState(() => _isCreatingConversation = false);
      return;
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: LocalizedText(
              'Không thể tải danh sách đơn hàng để bắt đầu trò chuyện.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
      if (mounted) setState(() => _isCreatingConversation = false);
      return;
    } finally {
      if (mounted) setState(() => _isCreatingConversation = false);
    }

    if (!mounted) return;
    final subEntries = <({MasterOrderModel order, SubOrderModel sub})>[];
    final seenPairs = <String>{};
    for (final order in orders) {
      for (final sub in order.subOrders) {
        final key = '${order.id}:${sub.vendorId}';
        if (seenPairs.add(key)) {
          subEntries.add((order: order, sub: sub));
        }
      }
    }

    if (subEntries.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Bạn cần có ít nhất 1 đơn hàng để bắt đầu nhắn tin với đối tác.',
          ),
        ),
      );
      return;
    }

    final selected = await showModalBottomSheet<
      ({MasterOrderModel order, SubOrderModel sub})
    >(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(AppShapes.gutterMobile),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    LocalizedText(
                      'Chọn đơn hàng để nhắn đối tác',
                      style: AppTypography.headlineSm(
                        color: AppColors.onSurface,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(sheetContext),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Flexible(
                  child: ListView.separated(
                    shrinkWrap: true,
                    itemCount: subEntries.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (_, idx) {
                      final entry = subEntries[idx];
                      final shortOrder =
                          entry.order.id.length > 8
                              ? entry.order.id.substring(0, 8).toUpperCase()
                              : entry.order.id;
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const CircleAvatar(
                          backgroundColor: AppColors.secondaryContainer,
                          child: Icon(
                            Icons.sailing,
                            color: AppColors.secondary,
                            size: 18,
                          ),
                        ),
                        title: Text(
                          entry.sub.vendorName,
                          style: AppTypography.labelLg(
                            color: AppColors.onSurface,
                          ),
                        ),
                        subtitle: Text(
                          '#$shortOrder • ${entry.sub.serviceName}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: AppTypography.bodySm(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                        trailing: const Icon(
                          Icons.chat_bubble_outline,
                          color: AppColors.secondary,
                          size: 18,
                        ),
                        onTap: () => Navigator.pop(sheetContext, entry),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    if (selected == null || !mounted) return;
    setState(() => _isCreatingConversation = true);
    try {
      final conv = await _repository.createOrGetConversation(
        masterOrderId: selected.order.id,
        vendorId:
            selected.sub.vendorId.isNotEmpty ? selected.sub.vendorId : null,
        fallbackVendorName: selected.sub.vendorName,
        fallbackServiceName: selected.sub.serviceName,
      );
      if (!mounted) return;
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => VendorChatScreen(conversation: conv),
        ),
      );
      if (mounted) _loadConversations();
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
              'Không thể khởi tạo cuộc trò chuyện với đối tác.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isCreatingConversation = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final totalCount = _conversations.length;
    final unreadTotal =
        _conversations.where((c) => c.unreadCount > 0).length;
    final withOrderCount =
        _conversations
            .where(
              (c) => c.masterOrderId != null && c.masterOrderId!.isNotEmpty,
            )
            .length;

    final pills = [
      '${tr(context, 'Tất cả')} ($totalCount)',
      '${tr(context, 'Chưa đọc')} ($unreadTotal)',
      '${tr(context, 'Đang có tour')} ($withOrderCount)',
      tr(context, 'Hỗ trợ CSKH'),
    ];

    final convs = _filteredConversations;

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
          'Tin nhắn',
          style: AppTypography.headlineSm(color: AppColors.onSurface),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: tr(context, 'Tạo cuộc trò chuyện từ đơn hàng'),
            icon:
                _isCreatingConversation
                    ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const Icon(
                      Icons.add_comment_outlined,
                      color: AppColors.secondary,
                    ),
            onPressed:
                _isCreatingConversation ? null : _openCreateConversationSheet,
          ),
          IconButton(
            icon: const Icon(Icons.refresh, color: AppColors.onSurface),
            onPressed: _isLoading ? null : _loadConversations,
          ),
        ],
      ),
      body: Column(
        children: [
          // LIVE OCEAN BANNER
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
              vertical: 4,
            ),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: AppColors.secondary,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Icon(Icons.waves, size: 16, color: AppColors.secondary),
                  const SizedBox(width: 6),
                  Expanded(
                    child: LocalizedText(
                      'Biển Mỹ Khê sóng êm (0.4m) • Kênh phản hồi trực tiếp',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurface,
                      ).copyWith(fontSize: 11),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  LocalizedText(
                    'Trực tuyến',
                    style: AppTypography.labelSm(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w700,
                    ).copyWith(fontSize: 11),
                  ),
                ],
              ),
            ),
          ),

          // SEARCH INPUT
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppShapes.gutterMobile,
              vertical: 8,
            ),
            child: Container(
              height: 42,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(21),
              ),
              child: TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: tr(context, 'Tìm đối tác hoặc mã đơn...'),
                  hintStyle: AppTypography.bodySm(color: AppColors.outline),
                  prefixIcon: const Icon(
                    Icons.search,
                    size: 20,
                    color: AppColors.secondary,
                  ),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(vertical: 10),
                ),
              ),
            ),
          ),

          // PILLS
          Container(
            height: 40,
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(
                horizontal: AppShapes.gutterMobile,
              ),
              itemCount: pills.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final isSel = _selectedPillIndex == index;
                return InkWell(
                  onTap: () => setState(() => _selectedPillIndex = index),
                  borderRadius: BorderRadius.circular(16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color:
                          isSel
                              ? AppColors.secondary
                              : AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Text(
                      pills[index],
                      style: AppTypography.labelSm(
                        color:
                            isSel ? Colors.white : AppColors.onSurfaceVariant,
                        fontWeight: isSel ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),

          // CONVERSATIONS LIST
          Expanded(
            child:
                _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : _errorMessage != null
                    ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(
                              Icons.error_outline,
                              size: 48,
                              color: AppColors.error,
                            ),
                            const SizedBox(height: 12),
                            LocalizedText(
                              _errorMessage!,
                              textAlign: TextAlign.center,
                              style: AppTypography.bodyMd(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton.icon(
                              onPressed: _loadConversations,
                              icon: const Icon(Icons.refresh, size: 18),
                              label: const LocalizedText('Thử lại'),
                            ),
                          ],
                        ),
                      ),
                    )
                    : convs.isEmpty
                    ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(
                              Icons.chat_bubble_outline,
                              size: 48,
                              color: AppColors.outline,
                            ),
                            const SizedBox(height: 12),
                            LocalizedText(
                              'Chưa có cuộc trò chuyện nào.',
                              style: AppTypography.bodyMd(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            const SizedBox(height: 14),
                            OutlinedButton.icon(
                              onPressed:
                                  _isCreatingConversation
                                      ? null
                                      : _openCreateConversationSheet,
                              icon: const Icon(
                                Icons.add_comment_outlined,
                                size: 18,
                              ),
                              label: const LocalizedText(
                                'Bắt đầu nhắn tin từ đơn hàng',
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                    : RefreshIndicator(
                      onRefresh: _loadConversations,
                      child: ListView.separated(
                        padding: const EdgeInsets.all(AppShapes.gutterMobile),
                        itemCount: convs.length,
                        separatorBuilder:
                            (_, _) => const Divider(height: 12),
                        itemBuilder: (context, index) {
                          final conv = convs[index];
                          final initial =
                              conv.vendorName.trim().isNotEmpty
                                  ? conv.vendorName.trim()[0].toUpperCase()
                                  : 'V';
                          return InkWell(
                            onTap: () async {
                              await Navigator.push<void>(
                                context,
                                MaterialPageRoute(
                                  builder:
                                      (_) =>
                                          VendorChatScreen(conversation: conv),
                                ),
                              );
                              if (mounted) _loadConversations();
                            },
                            borderRadius: BorderRadius.circular(12),
                            child: Padding(
                              padding: const EdgeInsets.all(8),
                              child: Row(
                                children: [
                                  Stack(
                                    children: [
                                      CircleAvatar(
                                        radius: 26,
                                        backgroundColor:
                                            AppColors.secondaryContainer,
                                        backgroundImage:
                                            conv.vendorAvatar != null
                                                ? NetworkImage(
                                                  conv.vendorAvatar!,
                                                )
                                                : null,
                                        child:
                                            conv.vendorAvatar == null
                                                ? Text(initial)
                                                : null,
                                      ),
                                      if (conv.isOnline)
                                        Positioned(
                                          bottom: 0,
                                          right: 0,
                                          child: Container(
                                            width: 12,
                                            height: 12,
                                            decoration: BoxDecoration(
                                              color: AppColors.secondary,
                                              shape: BoxShape.circle,
                                              border: Border.all(
                                                color: Colors.white,
                                                width: 2,
                                              ),
                                            ),
                                          ),
                                        ),
                                    ],
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          mainAxisAlignment:
                                              MainAxisAlignment.spaceBetween,
                                          children: [
                                            Expanded(
                                              child: LocalizedText(
                                                conv.vendorName,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: AppTypography.labelLg(
                                                  color: AppColors.onSurface,
                                                ),
                                              ),
                                            ),
                                            const SizedBox(width: 8),
                                            Text(
                                              '${conv.lastMessageTime.hour}:${conv.lastMessageTime.minute.toString().padLeft(2, '0')}',
                                              style: AppTypography.bodySm(
                                                color: AppColors.outline,
                                              ).copyWith(fontSize: 11),
                                            ),
                                          ],
                                        ),
                                        const SizedBox(height: 4),
                                        LocalizedText(
                                          conv.lastMessage,
                                          style: AppTypography.bodySm(
                                            color:
                                                conv.unreadCount > 0
                                                    ? AppColors.onSurface
                                                    : AppColors
                                                        .onSurfaceVariant,
                                            fontWeight:
                                                conv.unreadCount > 0
                                                    ? FontWeight.w700
                                                    : FontWeight.w400,
                                          ),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ],
                                    ),
                                  ),
                                  if (conv.unreadCount > 0) ...[
                                    const SizedBox(width: 8),
                                    Container(
                                      padding: const EdgeInsets.all(6),
                                      decoration: const BoxDecoration(
                                        color: AppColors.primary,
                                        shape: BoxShape.circle,
                                      ),
                                      child: Text(
                                        '${conv.unreadCount}',
                                        style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 10,
                                          fontWeight: FontWeight.bold,
                                        ),
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
    );
  }
}
