import 'package:flutter/material.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/data/vendor_store.dart';
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

class _VendorCopilotScreenState extends State<VendorCopilotScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _orbAnimController;
  late final Animation<double> _orbFloatAnimation;
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
    _orbAnimController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2600),
    )..repeat(reverse: true);
    _orbFloatAnimation = Tween<double>(begin: -8.0, end: 8.0).animate(
      CurvedAnimation(parent: _orbAnimController, curve: Curves.easeInOutSine),
    );

    if (widget.initialPrompt != null && widget.initialPrompt!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _sendMessage(widget.initialPrompt!.trim());
      });
    }
  }

  @override
  void dispose() {
    _orbAnimController.dispose();
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
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
        if (aiResp.conversationId != null && aiResp.conversationId!.isNotEmpty) {
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

  void _resetConversation() {
    if (_isSending) return;
    setState(() {
      _conversationId = null;
      _messages.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final bool hasChatted = _messages.any((m) => !m.isUser);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFFE4F3F7), // Soft maritime pastel
              Color(0xFFEFF7FA),
              Color(0xFFFAF9F6),
            ],
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: Column(
            children: [
              // 1. TOP BAR
              _buildTopBar(),

              // 2. QUICK PROMPTS CAROUSEL
              _buildQuickPromptsBar(),

              // 3. MAIN BODY (HERO ORB OR CHAT STREAM)
              Expanded(
                child: hasChatted ? _buildChatStream() : _buildHeroCenter(),
              ),

              // USER QUERY BUBBLE RIGHT ABOVE INPUT BAR (WHEN THINKING IN HERO VIEW)
              if (!hasChatted && _isSending && _messages.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      constraints: BoxConstraints(
                        maxWidth: MediaQuery.of(context).size.width * 0.8,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.primary,
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(18),
                          topRight: Radius.circular(18),
                          bottomLeft: Radius.circular(18),
                          bottomRight: Radius.circular(4),
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.08),
                            blurRadius: 10,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Text(
                        _messages.last.content,
                        style: AppTypography.bodyMd(
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                ),

              // 4. FLOATING INPUT BAR
              _buildFloatingInputBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          // Circular Back Button
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.05),
                  blurRadius: 10,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: IconButton(
              icon: const Icon(
                Icons.arrow_back_ios_new_rounded,
                size: 18,
                color: AppColors.onSurface,
              ),
              onPressed: () => Navigator.pop(context),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Danasea AI Copilot',
                  style: AppTypography.bodyMd(
                    color: AppColors.onSurface,
                  ).copyWith(fontWeight: FontWeight.w700),
                ),
                Text(
                  'Trợ lý chính sách & vận hành',
                  style: AppTypography.labelSm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 11),
                ),
              ],
            ),
          ),
          // Circular Refresh Button
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.05),
                  blurRadius: 10,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: IconButton(
              tooltip: 'Làm mới cuộc trò chuyện',
              icon: const Icon(
                Icons.refresh_rounded,
                size: 20,
                color: AppColors.onSurface,
              ),
              onPressed: _resetConversation,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickPromptsBar() {
    return SizedBox(
      height: 42,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
        scrollDirection: Axis.horizontal,
        itemCount: _quickPrompts.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (ctx, i) {
          final prompt = _quickPrompts[i];
          return ActionChip(
            label: Text(
              prompt,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.navyAnchor,
                fontWeight: FontWeight.w500,
              ),
            ),
            backgroundColor: Colors.white.withValues(alpha: 0.9),
            side: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8)),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
            ),
            onPressed: _isSending ? null : () => _sendMessage(prompt),
          );
        },
      ),
    );
  }

  Widget _buildHeroCenter() {
    final vendorName = VendorStore.instance.vendor.businessName.trim();
    final greetingText = vendorName.isNotEmpty
        ? 'Xin chào Quý đối tác $vendorName! Tôi có thể giúp gì cho vận hành hôm nay?'
        : 'Xin chào Quý đối tác DANASEA! Tôi là Trợ lý AI Copilot hỗ trợ vận hành và chính sách.';

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Column(
          children: [
            const SizedBox(height: 28),

            // Animated Floating 3D Glowing Orb (P1 when idle, P2 when thinking)
            AnimatedBuilder(
              animation: _orbFloatAnimation,
              builder: (context, child) {
                return Transform.translate(
                  offset: Offset(0, _orbFloatAnimation.value),
                  child: child,
                );
              },
              child: Stack(
                alignment: Alignment.center,
                children: [
                  // Ambient colored glows behind orb
                  Container(
                    width: 170,
                    height: 170,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: const Color(0xFF95F1FF).withValues(alpha: 0.45),
                          blurRadius: 60,
                          spreadRadius: 12,
                        ),
                        BoxShadow(
                          color: const Color(0xFFFFB4A7).withValues(alpha: 0.25),
                          blurRadius: 45,
                          spreadRadius: 6,
                        ),
                      ],
                    ),
                  ),

                  // 3D Glass Orb Image
                  Image.asset(
                    _isSending
                        ? 'assets/qua-cau-p2-trong-suot.gif'
                        : 'assets/qua-cau-p1-trong-suot.gif',
                    width: 210,
                    height: 210,
                    fit: BoxFit.contain,
                  ),
                ],
              ),
            ),

            const SizedBox(height: 36),

            // Greeting Headline or Thinking State
            LocalizedText(
              _isSending ? 'DANASEA AI đang suy nghĩ...' : greetingText,
              textAlign: TextAlign.center,
              style: AppTypography.headlineSm(
                color: AppColors.onSurface,
              ).copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.3, height: 1.35),
            ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildChatStream() {
    final int totalCount = _messages.length + (_isSending ? 1 : 0);

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(
        horizontal: AppShapes.gutterMobile,
        vertical: 12,
      ),
      itemCount: totalCount,
      itemBuilder: (context, index) {
        // Thinking indicator bubble at the end
        if (index == _messages.length && _isSending) {
          return Padding(
            padding: const EdgeInsets.only(top: 4, bottom: 12),
            child: Row(
              children: [
                ClipOval(
                  child: Image.asset(
                    'assets/qua-cau-p2-trong-suot.gif',
                    width: 28,
                    height: 28,
                    fit: BoxFit.cover,
                  ),
                ),
                const SizedBox(width: 10),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: BorderRadius.circular(18),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.04),
                        blurRadius: 10,
                        offset: const Offset(0, 2),
                      ),
                    ],
                    border: Border.all(
                      color: AppColors.borderSubtle.withValues(alpha: 0.6),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const SizedBox(
                        width: 13,
                        height: 13,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppColors.secondary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      LocalizedText(
                        'DANASEA AI đang suy nghĩ...',
                        style: AppTypography.bodySm(
                          color: AppColors.onSurfaceVariant,
                        ).copyWith(fontWeight: FontWeight.w500),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }

        final msg = _messages[index];
        final isUser = msg.isUser;

        return Column(
          crossAxisAlignment:
              isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            // Sender info
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 4,
                vertical: 2,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (!isUser) ...[
                    Container(
                      width: 22,
                      height: 22,
                      decoration: const BoxDecoration(
                        color: AppColors.secondary,
                        shape: BoxShape.circle,
                      ),
                      child: ClipOval(
                        child: Image.asset(
                          'assets/qua-cau-p1-trong-suot.gif',
                          width: 22,
                          height: 22,
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                  ],
                  LocalizedText(
                    isUser ? 'Bạn' : 'DANASEA AI',
                    style: AppTypography.labelSm(
                      color: AppColors.onSurfaceVariant,
                    ).copyWith(fontSize: 10),
                  ),
                ],
              ),
            ),

            // Bubble
            Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * 0.82,
              ),
              decoration: BoxDecoration(
                color: isUser
                    ? AppColors.primary
                    : AppColors.surfaceContainerLowest,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(18),
                  topRight: const Radius.circular(18),
                  bottomLeft: Radius.circular(isUser ? 18 : 4),
                  bottomRight: Radius.circular(isUser ? 4 : 18),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 10,
                    offset: const Offset(0, 2),
                  ),
                ],
                border: isUser
                    ? null
                    : Border.all(
                        color: AppColors.borderSubtle.withValues(alpha: 0.6),
                      ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    msg.content,
                    style: AppTypography.bodyMd(
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
                          padding: const EdgeInsets.symmetric(
                            horizontal: 5,
                            vertical: 1.5,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFDCFCE7),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: const Text(
                            'Chuẩn chính sách',
                            style: TextStyle(
                              fontSize: 9,
                              color: Color(0xFF166534),
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 10),
          ],
        );
      },
    );
  }

  Widget _buildFloatingInputBar() {
    return Container(
      margin: EdgeInsets.fromLTRB(
        16,
        4,
        16,
        MediaQuery.of(context).padding.bottom + 12,
      ),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(26),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Input field
          TextField(
            controller: _textController,
            enabled: !_isSending,
            style: AppTypography.bodyMd(color: AppColors.onSurface),
            decoration: InputDecoration(
              hintText: tr(context, 'Hỏi về quy định biển, lịch chạy, đối soát...'),
              hintStyle: AppTypography.bodySm(color: AppColors.outline),
              border: InputBorder.none,
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 4,
                vertical: 8,
              ),
            ),
            onSubmitted: _sendMessage,
          ),
          const SizedBox(height: 4),

          // Bottom buttons row: (+)  [Image]  ... [Mic] [Send]
          Row(
            children: [
              // Plus button (+)
              _buildToolCircleButton(
                icon: Icons.add,
                onTap: () {},
              ),
              const SizedBox(width: 8),

              // Image icon
              _buildToolCircleButton(
                icon: Icons.image_outlined,
                onTap: () {},
              ),

              const Spacer(),

              // Waveform / Mic icon
              Icon(
                Icons.graphic_eq_rounded,
                size: 22,
                color: AppColors.secondary.withValues(alpha: 0.7),
              ),
              const SizedBox(width: 10),

              // Send button with Gradient and Airplane icon
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    colors: [
                      Color(0xFF006874), // Danasea Secondary Teal
                      Color(0xFF087F8C),
                    ],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.secondary.withValues(alpha: 0.35),
                      blurRadius: 8,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: IconButton(
                  padding: EdgeInsets.zero,
                  icon: _isSending
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(
                          Icons.near_me_rounded,
                          color: Colors.white,
                          size: 19,
                        ),
                  onPressed: _isSending
                      ? null
                      : () => _sendMessage(_textController.text),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildToolCircleButton({
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        width: 32,
        height: 32,
        decoration: const BoxDecoration(
          color: AppColors.surfaceContainerLow,
          shape: BoxShape.circle,
        ),
        child: Icon(
          icon,
          size: 17,
          color: AppColors.onSurfaceVariant,
        ),
      ),
    );
  }
}
