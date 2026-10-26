import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/conversation_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../orders/presentation/screens/order_detail_screen.dart';

class VendorChatScreen extends StatefulWidget {
  final ConversationModel conversation;

  const VendorChatScreen({super.key, required this.conversation});

  @override
  State<VendorChatScreen> createState() => _VendorChatScreenState();
}

class _VendorChatScreenState extends State<VendorChatScreen> {
  final ConversationRepository _repository = ConversationRepository.instance;
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  List<ChatMessageModel> _messages = const [];
  bool _isLoading = true;
  bool _isSending = false;
  String? _errorMessage;
  String? _pendingAttachmentUrl;

  final List<String> _quickPrompts = [
    'Điểm gửi xe ở đâu?',
    'Có tủ khóa gửi đồ không?',
    'Mấy giờ có mặt là vừa?',
  ];

  @override
  void initState() {
    super.initState();
    _loadMessages();
  }

  @override
  void dispose() {
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadMessages({bool incremental = false}) async {
    int? lastSequence;
    if (incremental && _messages.isNotEmpty) {
      for (var i = _messages.length - 1; i >= 0; i--) {
        if (_messages[i].sequence != null) {
          lastSequence = _messages[i].sequence;
          break;
        }
      }
    }

    if (!incremental) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
      });
    }

    try {
      final fetched = await _repository.listMessages(
        widget.conversation.id,
        afterSequence: lastSequence,
        page: 0,
        size: 50,
      );
      if (!mounted) return;
      setState(() {
        if (incremental && lastSequence != null) {
          final existingIds = _messages.map((m) => m.id).toSet();
          final merged = List<ChatMessageModel>.from(_messages);
          for (final msg in fetched) {
            if (!existingIds.contains(msg.id)) {
              merged.add(msg);
            }
          }
          _messages = merged;
        } else {
          _messages = fetched;
        }
        _isLoading = false;
      });
      _scrollToBottom();
    } on AuthFailure catch (e) {
      if (!mounted) return;
      if (!incremental) {
        setState(() {
          _errorMessage = e.message;
          _isLoading = false;
        });
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: LocalizedText(e.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } catch (_) {
      if (!mounted) return;
      if (!incremental) {
        setState(() {
          _errorMessage = 'Không thể tải nội dung cuộc trò chuyện.';
          _isLoading = false;
        });
      }
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _promptAttachmentUrl() async {
    final urlController = TextEditingController(
      text: _pendingAttachmentUrl ?? '',
    );
    final result = await showDialog<String>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const LocalizedText('Đính kèm đường dẫn hình ảnh'),
          content: TextField(
            controller: urlController,
            decoration: InputDecoration(
              hintText: tr(context, 'Nhập URL ảnh (https://...)'),
              border: const OutlineInputBorder(),
              isDense: true,
            ),
          ),
          actions: [
            if (_pendingAttachmentUrl != null)
              TextButton(
                onPressed: () => Navigator.pop(dialogContext, ''),
                child: const LocalizedText('Gỡ ảnh'),
              ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const LocalizedText('Đóng'),
            ),
            ElevatedButton(
              onPressed:
                  () => Navigator.pop(
                    dialogContext,
                    urlController.text.trim(),
                  ),
              child: const LocalizedText('Xác nhận'),
            ),
          ],
        );
      },
    );
    urlController.dispose();
    if (result == null || !mounted) return;
    setState(() {
      _pendingAttachmentUrl = result.isEmpty ? null : result;
    });
  }

  Future<void> _sendMessage([String? customText]) async {
    if (_isSending) return;
    final rawText = customText ?? _textController.text.trim();
    final content =
        rawText.isEmpty && _pendingAttachmentUrl != null
            ? '[Hình ảnh đính kèm]'
            : rawText;
    if (content.isEmpty) return;

    setState(() => _isSending = true);
    try {
      final created = await _repository.sendMessage(
        conversationId: widget.conversation.id,
        content: content,
        attachmentUrl: customText == null ? _pendingAttachmentUrl : null,
      );
      if (!mounted) return;
      setState(() {
        _messages = [..._messages, created];
        if (customText == null) {
          _textController.clear();
          _pendingAttachmentUrl = null;
        }
      });
      _scrollToBottom();
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
              'Không thể gửi tin nhắn. Vui lòng kiểm tra kết nối và thử lại.',
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final conv = widget.conversation;
    final initial =
        conv.vendorName.trim().isNotEmpty
            ? conv.vendorName.trim()[0].toUpperCase()
            : 'V';
    final shortOrderCode =
        conv.masterOrderId != null && conv.masterOrderId!.length > 8
            ? conv.masterOrderId!.substring(0, 8).toUpperCase()
            : (conv.masterOrderId ?? 'DNS');

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        titleSpacing: 0,
        title: Row(
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: AppColors.secondaryContainer,
              backgroundImage:
                  conv.vendorAvatar != null
                      ? NetworkImage(conv.vendorAvatar!)
                      : null,
              child: conv.vendorAvatar == null ? Text(initial) : null,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: LocalizedText(
                          conv.vendorName,
                          style: AppTypography.labelMd(
                            color: AppColors.onSurface,
                            fontWeight: FontWeight.w700,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.verified,
                        size: 13,
                        color: AppColors.secondary,
                      ),
                    ],
                  ),
                  LocalizedText(
                    'Đang hoạt động trên biển',
                    style: AppTypography.bodySm(
                      color: AppColors.secondary,
                    ).copyWith(fontSize: 10),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: tr(context, 'Làm mới tin nhắn'),
            icon: const Icon(Icons.sync, color: AppColors.secondary),
            onPressed:
                _isLoading ? null : () => _loadMessages(incremental: true),
          ),
          IconButton(
            icon: const Icon(
              Icons.phone_outlined,
              color: AppColors.secondary,
            ),
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: LocalizedText('Gọi hotline đối tác: 0905 888 999'),
                ),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // ORDER CONTEXT PILL
          InkWell(
            onTap:
                conv.masterOrderId != null && conv.masterOrderId!.isNotEmpty
                    ? () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder:
                              (_) => OrderDetailScreen(
                                orderId: conv.masterOrderId!,
                              ),
                        ),
                      );
                    }
                    : null,
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: AppColors.surfaceContainerLow,
              child: Row(
                children: [
                  const Icon(
                    Icons.sailing,
                    size: 16,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '${tr(context, 'Đơn hàng')} #$shortOrderCode • ${conv.serviceName ?? tr(context, 'Trải nghiệm thể thao biển Đà Nẵng')}',
                      style: AppTypography.bodySm(
                        color: AppColors.onSurface,
                      ).copyWith(fontSize: 11),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const Icon(
                    Icons.chevron_right,
                    size: 16,
                    color: AppColors.outline,
                  ),
                ],
              ),
            ),
          ),

          // MESSAGES STREAM
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
                              size: 44,
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
                            const SizedBox(height: 14),
                            ElevatedButton.icon(
                              onPressed: () => _loadMessages(),
                              icon: const Icon(Icons.refresh, size: 18),
                              label: const LocalizedText('Thử lại'),
                            ),
                          ],
                        ),
                      ),
                    )
                    : _messages.isEmpty
                    ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: LocalizedText(
                          'Chưa có tin nhắn nào. Hãy gửi lời chào hoặc chọn câu hỏi gợi ý bên dưới!',
                          textAlign: TextAlign.center,
                          style: AppTypography.bodyMd(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ),
                    )
                    : ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.all(AppShapes.gutterMobile),
                      itemCount: _messages.length,
                      itemBuilder: (context, index) {
                        final msg = _messages[index];
                        return Align(
                          alignment:
                              msg.isMe
                                  ? Alignment.centerRight
                                  : Alignment.centerLeft,
                          child: Container(
                            margin: const EdgeInsets.only(bottom: 10),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 10,
                            ),
                            constraints: BoxConstraints(
                              maxWidth:
                                  MediaQuery.of(context).size.width * 0.75,
                            ),
                            decoration: BoxDecoration(
                              color:
                                  msg.isMe
                                      ? AppColors.primary
                                      : AppColors.surfaceContainerLowest,
                              borderRadius: BorderRadius.only(
                                topLeft: const Radius.circular(16),
                                topRight: const Radius.circular(16),
                                bottomLeft: Radius.circular(
                                  msg.isMe ? 16 : 4,
                                ),
                                bottomRight: Radius.circular(
                                  msg.isMe ? 4 : 16,
                                ),
                              ),
                              boxShadow: AppShapes.shadowLevel1,
                            ),
                            child: Column(
                              crossAxisAlignment:
                                  msg.isMe
                                      ? CrossAxisAlignment.end
                                      : CrossAxisAlignment.start,
                              children: [
                                if (msg.attachmentUrl != null &&
                                    msg.attachmentUrl!.isNotEmpty) ...[
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(10),
                                    child: Image.network(
                                      msg.attachmentUrl!,
                                      height: 140,
                                      width: 200,
                                      fit: BoxFit.cover,
                                      errorBuilder:
                                          (_, _, _) => Container(
                                            padding: const EdgeInsets.all(8),
                                            color: AppColors.surfaceContainerLow,
                                            child: Text(
                                              msg.attachmentUrl!,
                                              style: AppTypography.bodySm(
                                                color: AppColors.secondary,
                                              ),
                                            ),
                                          ),
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                ],
                                LocalizedText(
                                  msg.content,
                                  style: AppTypography.bodyMd(
                                    color:
                                        msg.isMe
                                            ? Colors.white
                                            : AppColors.onSurface,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '${msg.createdAt.hour}:${msg.createdAt.minute.toString().padLeft(2, '0')}',
                                  style: TextStyle(
                                    fontSize: 10,
                                    color:
                                        msg.isMe
                                            ? Colors.white.withValues(
                                              alpha: 0.7,
                                            )
                                            : AppColors.outline,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
          ),

          if (_pendingAttachmentUrl != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              color: AppColors.secondaryContainer.withValues(alpha: 0.4),
              child: Row(
                children: [
                  const Icon(
                    Icons.attachment,
                    size: 16,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      _pendingAttachmentUrl!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTypography.bodySm(color: AppColors.onSurface),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 16),
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    onPressed:
                        () => setState(() => _pendingAttachmentUrl = null),
                  ),
                ],
              ),
            ),

          // QUICK PROMPTS CHIPS
          Container(
            height: 38,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _quickPrompts.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                return ActionChip(
                  label: LocalizedText(
                    _quickPrompts[index],
                    style: AppTypography.labelSm(color: AppColors.secondary),
                  ),
                  backgroundColor: AppColors.secondaryContainer.withValues(
                    alpha: 0.3,
                  ),
                  onPressed:
                      _isSending
                          ? null
                          : () => _sendMessage(
                            tr(context, _quickPrompts[index]),
                          ),
                );
              },
            ),
          ),
          const SizedBox(height: 6),

          // INPUT BAR
          Container(
            padding: EdgeInsets.only(
              left: 12,
              right: 12,
              top: 8,
              bottom: MediaQuery.of(context).padding.bottom + 8,
            ),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              border: Border(
                top: BorderSide(
                  color: AppColors.borderSubtle.withValues(alpha: 0.8),
                ),
              ),
            ),
            child: Row(
              children: [
                IconButton(
                  icon: Icon(
                    _pendingAttachmentUrl != null
                        ? Icons.photo_library
                        : Icons.add_photo_alternate_outlined,
                    color: AppColors.secondary,
                  ),
                  onPressed: _isSending ? null : _promptAttachmentUrl,
                ),
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: TextField(
                      controller: _textController,
                      enabled: !_isSending,
                      decoration: InputDecoration(
                        hintText: tr(
                          context,
                          'Nhập tin nhắn cho nhà cung cấp...',
                        ),
                        hintStyle: AppTypography.bodySm(
                          color: AppColors.outline,
                        ),
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(
                          vertical: 10,
                        ),
                      ),
                      onSubmitted: (_) => _sendMessage(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                CircleAvatar(
                  backgroundColor: AppColors.primary,
                  radius: 20,
                  child:
                      _isSending
                          ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                          : IconButton(
                            icon: const Icon(
                              Icons.send,
                              size: 18,
                              color: Colors.white,
                            ),
                            onPressed: () => _sendMessage(),
                          ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
