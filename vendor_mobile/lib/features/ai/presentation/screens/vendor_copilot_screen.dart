import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';

class VendorCopilotScreen extends StatefulWidget {
  final String? initialPrompt;
  final String? contextType;
  final String? contextId;

  const VendorCopilotScreen({
    super.key,
    this.initialPrompt,
    this.contextType,
    this.contextId,
  });

  @override
  State<VendorCopilotScreen> createState() => _VendorCopilotScreenState();
}

class _VendorCopilotScreenState extends State<VendorCopilotScreen> {
  final _aiRepo = VendorAiRepository();
  final _textController = TextEditingController();
  final _scrollController = ScrollController();
  final List<CopilotMessageModel> _messages = [];
  String? _conversationId;
  bool _isSending = false;

  final List<String> _quickPrompts = [
    'Chính sách hoa hồng & chu kỳ đối soát thế nào?',
    'Quy định an toàn sóng biển cấp mấy phải hoãn?',
    'Làm thế nào để xử lý hủy ca chạy khẩn cấp?',
    'Cách nâng cao điểm chất lượng và review của gian hàng?',
  ];

  @override
  void initState() {
    super.initState();
    _messages.add(
      CopilotMessageModel(
        id: 'welcome',
        role: 'assistant',
        content: 'Xin chào Quý đối tác DANASEA! Tôi là Trợ lý AI Copilot hỗ trợ vận hành và chính sách. Tôi có thể giúp gì cho bạn hôm nay?',
        createdAt: DateTime.now(),
      ),
    );

    if (widget.initialPrompt != null && widget.initialPrompt!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _sendMessage(widget.initialPrompt!.trim());
      });
    }
  }

  @override
  void dispose() {
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
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

  Future<void> _sendMessage(String text) async {
    final clean = text.trim();
    if (clean.isEmpty || _isSending) return;

    _textController.clear();
    final userMsg = CopilotMessageModel(
      id: 'usr-${DateTime.now().millisecondsSinceEpoch}',
      role: 'user',
      content: clean,
      createdAt: DateTime.now(),
    );

    setState(() {
      _messages.add(userMsg);
      _isSending = true;
    });
    _scrollToBottom();

    try {
      final aiResp = await _aiRepo.chatWithCopilot(
        conversationId: _conversationId,
        message: clean,
        contextType: widget.contextType,
        contextId: widget.contextId,
      );

      if (mounted) {
        if (aiResp.conversationId != null) {
          _conversationId = aiResp.conversationId;
        }
        setState(() {
          _messages.add(aiResp);
          _isSending = false;
        });
        _scrollToBottom();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSending = false);
        ToastNotification.show(
          context,
          message: 'Lỗi phản hồi từ AI Copilot: $e',
          type: ToastType.error,
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 1,
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: AppColors.secondaryContainer,
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.auto_awesome, color: AppColors.secondary, size: 20),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  'Danasea AI Copilot',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppColors.navyAnchor,
                  ),
                ),
                Text(
                  'Trợ lý chính sách & vận hành',
                  style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Làm mới cuộc trò chuyện',
            icon: const Icon(Icons.refresh, color: AppColors.tertiary),
            onPressed: () {
              setState(() {
                _conversationId = null;
                _messages.clear();
                _messages.add(
                  CopilotMessageModel(
                    id: 'welcome-${DateTime.now().millisecondsSinceEpoch}',
                    role: 'assistant',
                    content: 'Cuộc trò chuyện đã được làm mới. Bạn cần hỗ trợ điều gì?',
                    createdAt: DateTime.now(),
                  ),
                );
              });
            },
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Quick suggestions carousel
            Container(
              height: 44,
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                scrollDirection: Axis.horizontal,
                itemCount: _quickPrompts.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (ctx, i) {
                  final prompt = _quickPrompts[i];
                  return ActionChip(
                    label: Text(
                      prompt,
                      style: const TextStyle(fontSize: 11, color: AppColors.navyAnchor, fontWeight: FontWeight.w500),
                    ),
                    backgroundColor: Colors.white,
                    side: const BorderSide(color: Color(0xFFE2E8F0)),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                    onPressed: _isSending ? null : () => _sendMessage(prompt),
                  );
                },
              ),
            ),
            const Divider(height: 1, color: Color(0xFFE2E8F0)),

            // Messages ListView
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.all(16),
                itemCount: _messages.length,
                itemBuilder: (ctx, index) {
                  final msg = _messages[index];
                  return _buildMessageBubble(msg);
                },
              ),
            ),

            // Thinking indicator
            if (_isSending)
              Padding(
                padding: const EdgeInsets.only(left: 16, bottom: 8),
                child: Row(
                  children: [
                    const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.secondary),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      'AI Copilot đang phân tích câu hỏi...',
                      style: TextStyle(fontSize: 12, color: Colors.grey[600], fontStyle: FontStyle.italic),
                    ),
                  ],
                ),
              ),

            // Input Bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: Color(0xFFE2E8F0))),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _textController,
                      minLines: 1,
                      maxLines: 4,
                      decoration: InputDecoration(
                        hintText: 'Hỏi về quy định biển, lịch chạy, đối soát...',
                        hintStyle: TextStyle(fontSize: 13, color: Colors.grey[400]),
                        isDense: true,
                        filled: true,
                        fillColor: const Color(0xFFF1F5F9),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(22),
                          borderSide: BorderSide.none,
                        ),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                      ),
                      onSubmitted: _sendMessage,
                    ),
                  ),
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: _isSending ? null : () => _sendMessage(_textController.text),
                    borderRadius: BorderRadius.circular(24),
                    child: Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: _isSending ? Colors.grey[300] : AppColors.secondary,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.send_rounded, color: Colors.white, size: 20),
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

  Widget _buildMessageBubble(CopilotMessageModel msg) {
    final isUser = msg.isUser;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isUser) ...[
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: AppColors.secondaryContainer,
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(Icons.auto_awesome, size: 16, color: AppColors.secondary),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: isUser ? AppColors.navyAnchor : Colors.white,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: Radius.circular(isUser ? 16 : 4),
                  bottomRight: Radius.circular(isUser ? 4 : 16),
                ),
                border: isUser ? null : Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: const [
                  BoxShadow(color: Color(0x06000000), blurRadius: 4, offset: Offset(0, 1)),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    msg.content,
                    style: TextStyle(
                      fontSize: 13.5,
                      height: 1.45,
                      color: isUser ? Colors.white : AppColors.onSurface,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        '${msg.createdAt.hour.toString().padLeft(2, '0')}:${msg.createdAt.minute.toString().padLeft(2, '0')}',
                        style: TextStyle(
                          fontSize: 10,
                          color: isUser ? Colors.white70 : Colors.grey[500],
                        ),
                      ),
                      if (!isUser && msg.isVerified) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                          decoration: BoxDecoration(
                            color: const Color(0xFFDCFCE7),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: const Text(
                            'Chuẩn chính sách',
                            style: TextStyle(fontSize: 9, color: Color(0xFF166534), fontWeight: FontWeight.bold),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ),
          if (isUser) const SizedBox(width: 8),
        ],
      ),
    );
  }
}

