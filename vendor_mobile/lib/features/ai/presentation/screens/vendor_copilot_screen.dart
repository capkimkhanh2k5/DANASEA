import 'package:flutter/material.dart';
import 'package:vendor_mobile/core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/data/vendor_repositories.dart';
import '../../../../core/data/vendor_store.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/widgets/toast_notification.dart';
import '../../../../core/utils/display_code.dart';
import '../widgets/copilot_artifact_card.dart';
import '../widgets/copilot_markdown_text.dart';

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
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final _aiRepo = VendorAiRepository();
  final _db = VendorStore.instance;
  final _textController = TextEditingController();
  final _scrollController = ScrollController();
  final List<CopilotMessageModel> _messages = [];
  String? _conversationId;
  bool _isSending = false;
  List<Map<String, dynamic>> _savedConversations = [];
  bool _isLoadingHistory = false;

  String? _pinnedContextType;
  String? _pinnedContextId;
  String? _pinnedContextTitle;
  bool _showAttachMenu = false;

  String _formatShortCode(String raw, {String prefix = 'ORD'}) {
    return formatDisplayCode(raw, prefix: prefix);
  }

  final List<String> _quickPrompts = [
    'Doanh thu hôm nay của gian hàng là bao nhiêu?',
    'Tháng này gian hàng có doanh thu và thực thu dự kiến bao nhiêu?',
    'Những ca chạy nào cần điều chỉnh công suất?',
    'Từ đánh giá của khách, gian hàng nên cải thiện điều gì?',
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

    if (widget.contextType != null && widget.contextId != null) {
      _pinnedContextType = widget.contextType;
      _pinnedContextId = widget.contextId;
      _pinnedContextTitle = '${widget.contextType == 'ORDER' ? 'Đơn' : 'Dịch vụ'} #${_formatShortCode(widget.contextId!, prefix: widget.contextType == 'ORDER' ? 'SUB' : 'SRV')}';
    }

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
        contextType: _pinnedContextType ?? widget.contextType,
        contextId: _pinnedContextId ?? widget.contextId,
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
      _pinnedContextId = null;
      _pinnedContextType = null;
      _pinnedContextTitle = null;
    });
  }

  Future<void> _confirmResetConversation() async {
    if (_isSending) return;
    if (_messages.isEmpty) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const LocalizedText(
          'Làm mới cuộc trò chuyện?',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
        ),
        content: const LocalizedText(
          'Bạn có chắc chắn muốn làm mới và xóa nội dung cuộc trò chuyện hiện tại không?',
          style: TextStyle(fontSize: 13.5, color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const LocalizedText(
              'Hủy',
              style: TextStyle(color: AppColors.tertiary, fontWeight: FontWeight.w600),
            ),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFE53935),
              foregroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            ),
            child: const LocalizedText(
              'Xóa & làm mới',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      _resetConversation();
    }
  }

  Future<void> _loadSavedConversations() async {
    setState(() => _isLoadingHistory = true);
    try {
      final list = await _aiRepo.listConversations();
      if (mounted) {
        setState(() {
          _savedConversations = list;
          _isLoadingHistory = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoadingHistory = false);
    }
  }

  Future<void> _selectConversation(String id) async {
    Navigator.of(context).pop(); // Đóng Drawer
    if (_conversationId == id) return;
    setState(() {
      _conversationId = id;
      _isSending = true;
    });
    try {
      final history = await _aiRepo.getConversationHistory(id);
      if (mounted) {
        setState(() {
          _messages.clear();
          _messages.addAll(history);
          _isSending = false;
        });
        _scrollToBottom();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSending = false);
        ToastNotification.show(
          context,
          message: 'Lỗi tải lịch sử hội thoại: $e',
          type: ToastType.error,
        );
      }
    }
  }

  Future<void> _confirmDeleteConversation(String id, String title) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa cuộc trò chuyện?'),
        content: Text('Bạn có chắc muốn xóa "$title"? Thao tác này không thể hoàn tác.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Xóa'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final ok = await _aiRepo.deleteConversation(id);
      if (ok && mounted) {
        setState(() {
          _savedConversations.removeWhere((c) => c['id']?.toString() == id);
          if (_conversationId == id) {
            _conversationId = null;
            _messages.clear();
          }
        });
        ToastNotification.show(
          context,
          message: 'Đã xóa cuộc trò chuyện.',
          type: ToastType.success,
        );
      }
    }
  }

  String _formatConversationDate(String? raw) {
    if (raw == null || raw.isEmpty) return '';
    try {
      final dt = DateTime.parse(raw).toLocal();
      final now = DateTime.now();
      final diff = now.difference(dt);
      if (diff.inMinutes < 1) return 'Vừa xong';
      if (diff.inHours < 1) return '${diff.inMinutes} phút trước';
      final hour = dt.hour.toString().padLeft(2, '0');
      final minute = dt.minute.toString().padLeft(2, '0');
      if (diff.inDays == 0 && dt.day == now.day) {
        return 'Hôm nay $hour:$minute';
      }
      if (diff.inDays <= 1 && dt.day == now.day - 1) {
        return 'Hôm qua $hour:$minute';
      }
      final day = dt.day.toString().padLeft(2, '0');
      final month = dt.month.toString().padLeft(2, '0');
      return '$day/$month/${dt.year} $hour:$minute';
    } catch (_) {
      return '';
    }
  }

  Widget _buildHistoryDrawer() {
    return Drawer(
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.horizontal(right: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Drawer Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 12, 12),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF004D56).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      Icons.history_rounded,
                      color: Color(0xFF004D56),
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Lịch sử trò chuyện',
                      style: AppTypography.headlineSm(color: AppColors.navyAnchor).copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close_rounded, size: 20),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),

            // New Conversation Action Button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: ElevatedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _resetConversation();
                },
                icon: const Icon(Icons.add_rounded, size: 20, color: Colors.white),
                label: const Text(
                  'Cuộc trò chuyện mới',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.white),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF004D56),
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ),
            const Divider(height: 16, thickness: 1, indent: 16, endIndent: 16),

            // Conversation List
            Expanded(
              child: _isLoadingHistory
                  ? const Center(child: CircularProgressIndicator())
                  : _savedConversations.isEmpty
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24.0),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.chat_bubble_outline_rounded,
                                  size: 48,
                                  color: Colors.grey.shade300,
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'Chưa có lịch sử hội thoại nào.',
                                  textAlign: TextAlign.center,
                                  style: AppTypography.bodySm(color: AppColors.onSurfaceVariant),
                                ),
                              ],
                            ),
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                          itemCount: _savedConversations.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 4),
                          itemBuilder: (context, index) {
                            final conv = _savedConversations[index];
                            final id = conv['id']?.toString() ?? '';
                            final title = conv['title']?.toString().trim();
                            final displayTitle = (title != null && title.isNotEmpty)
                                ? title
                                : 'Cuộc trò chuyện #${index + 1}';
                            final createdAtRaw = conv['createdAt']?.toString();
                            final formattedTime = _formatConversationDate(createdAtRaw);
                            final isSelected = _conversationId == id;

                            return Material(
                              color: isSelected
                                   ? const Color(0xFFE6F4F1)
                                  : Colors.transparent,
                              borderRadius: BorderRadius.circular(10),
                              child: InkWell(
                                borderRadius: BorderRadius.circular(10),
                                onTap: () => _selectConversation(id),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                  child: Row(
                                    children: [
                                      Icon(
                                        isSelected
                                            ? Icons.chat_bubble_rounded
                                            : Icons.chat_bubble_outline_rounded,
                                        size: 18,
                                        color: isSelected
                                            ? const Color(0xFF004D56)
                                            : AppColors.onSurfaceVariant,
                                      ),
                                      const SizedBox(width: 10),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              displayTitle,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: TextStyle(
                                                fontSize: 13,
                                                fontWeight: isSelected
                                                    ? FontWeight.bold
                                                    : FontWeight.w500,
                                                color: isSelected
                                                    ? const Color(0xFF004D56)
                                                    : AppColors.navyAnchor,
                                              ),
                                            ),
                                            const SizedBox(height: 2),
                                            Text(
                                              formattedTime,
                                              style: TextStyle(
                                                fontSize: 11,
                                                color: AppColors.onSurfaceVariant.withValues(alpha: 0.8),
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                      IconButton(
                                        icon: const Icon(
                                          Icons.delete_outline_rounded,
                                          size: 18,
                                          color: AppColors.onSurfaceVariant,
                                        ),
                                        tooltip: 'Xóa',
                                        onPressed: () => _confirmDeleteConversation(id, displayTitle),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bool hasChatted = _messages.any((m) => !m.isUser);

    return Scaffold(
      key: _scaffoldKey,
      drawer: _buildHistoryDrawer(),
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
          child: Stack(
            children: [
              Column(
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
                            color: const Color(0xFF004D56),
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
              if (_showAttachMenu) ...[
                Positioned.fill(
                  child: GestureDetector(
                    behavior: HitTestBehavior.translucent,
                    onTap: () => setState(() => _showAttachMenu = false),
                    child: const SizedBox.expand(),
                  ),
                ),
                Positioned(
                  left: 20,
                  bottom: MediaQuery.of(context).padding.bottom +
                      MediaQuery.of(context).viewInsets.bottom +
                      58,
                  child: _buildMessengerMenu(),
                ),
              ],
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
          const SizedBox(width: 8),
          // Circular History Button (Mở Drawer Lịch sử từ trái qua phải)
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
              tooltip: 'Lịch sử trò chuyện',
              icon: const Icon(
                Icons.history_rounded,
                size: 20,
                color: AppColors.onSurface,
              ),
              onPressed: () {
                _loadSavedConversations();
                _scaffoldKey.currentState?.openDrawer();
              },
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Danasea AI Copilot',
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppColors.onSurface,
              ),
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
              onPressed: _confirmResetConversation,
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
                    ? const Color(0xFF004D56)
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
                  if (isUser)
                    SelectableText(
                      msg.content,
                      style: AppTypography.bodyMd(
                        color: Colors.white,
                      ),
                    )
                  else ...[
                    CopilotMarkdownText(
                      content: msg.content,
                      textColor: AppColors.onSurface,
                    ),
                    if (msg.artifacts.isNotEmpty) ...[
                      for (final art in msg.artifacts)
                        CopilotArtifactCard(artifact: art),
                    ] else if (msg.details.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      for (final detail in msg.details.take(3))
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                margin: const EdgeInsets.only(top: 6, right: 6),
                                width: 4,
                                height: 4,
                                decoration: const BoxDecoration(
                                  color: Color(0xFF0284C7),
                                  shape: BoxShape.circle,
                                ),
                              ),
                              Expanded(
                                child: SelectableText(
                                  detail,
                                  style: const TextStyle(fontSize: 12, color: Color(0xFF475569)),
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ],
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

  void _openContextPickerSheet({required String type}) {
    final isOrder = type == 'ORDER';
    int activeTab = isOrder ? 0 : 1;
    final searchCtrl = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final query = searchCtrl.text.trim().toLowerCase();
            final orders = _db.subOrders.where((o) {
              if (query.isEmpty) return true;
              return o.customerName.toLowerCase().contains(query) ||
                  o.serviceName.toLowerCase().contains(query) ||
                  o.subOrderCode.toLowerCase().contains(query) ||
                  o.id.toLowerCase().contains(query);
            }).toList();

            final services = _db.services.where((s) {
              if (query.isEmpty) return true;
              return s.name.toLowerCase().contains(query) ||
                  s.nameEn.toLowerCase().contains(query) ||
                  s.id.toLowerCase().contains(query);
            }).toList();

            return Container(
              height: MediaQuery.of(context).size.height * 0.72,
              decoration: const BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                children: [
                  const SizedBox(height: 12),
                  // Handle
                  Container(
                    width: 44,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.outlineVariant,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(height: 12),
                  // Header
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          isOrder ? 'Ghim đơn hàng' : 'Ghim dịch vụ / Tour',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: AppColors.navyAnchor,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close_rounded, size: 20, color: AppColors.tertiary),
                          onPressed: () => Navigator.pop(sheetCtx),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),

                  // Search box & Direct ID Pin Row
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    child: Row(
                      children: [
                        Expanded(
                          child: Container(
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: TextField(
                              controller: searchCtrl,
                              onChanged: (_) => setSheetState(() {}),
                              style: const TextStyle(fontSize: 13),
                              decoration: InputDecoration(
                                hintText: isOrder
                                    ? 'Nhập mã đơn hoặc tìm kiếm...'
                                    : 'Nhập mã dịch vụ hoặc tìm kiếm...',
                                hintStyle: const TextStyle(fontSize: 12.5, color: AppColors.outline),
                                prefixIcon: const Icon(Icons.search, size: 18, color: AppColors.tertiary),
                                border: InputBorder.none,
                                isDense: true,
                                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
                              ),
                            ),
                          ),
                        ),
                        if (searchCtrl.text.trim().isNotEmpty) ...[
                          const SizedBox(width: 8),
                          ElevatedButton(
                            onPressed: () {
                              final customId = searchCtrl.text.trim();
                              if (customId.isEmpty) return;
                              Navigator.pop(sheetCtx);
                              setState(() {
                                _pinnedContextType = type;
                                _pinnedContextId = customId;
                                _pinnedContextTitle = '${isOrder ? 'Đơn' : 'Dịch vụ'} #${_formatShortCode(customId, prefix: isOrder ? 'SUB' : 'SRV')}';
                              });
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF004D56),
                              foregroundColor: Colors.white,
                              elevation: 0,
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              minimumSize: const Size(0, 38),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            ),
                            child: const Text('Ghim', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),

                  // List items
                  Expanded(
                    child: activeTab == 0
                        ? (orders.isEmpty
                            ? const Center(
                                child: Text(
                                  'Không có đơn hàng nào',
                                  style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                                ),
                              )
                            : ListView.separated(
                                padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
                                itemCount: orders.length,
                                separatorBuilder: (context, index) => const SizedBox(height: 8),
                                itemBuilder: (context, idx) {
                                  final o = orders[idx];
                                  final isCurrent = _pinnedContextId == o.id || _pinnedContextId == o.subOrderCode;
                                  return InkWell(
                                    onTap: () {
                                      Navigator.pop(sheetCtx);
                                      setState(() {
                                        _pinnedContextType = 'ORDER';
                                        _pinnedContextId = o.subOrderCode.isNotEmpty ? o.subOrderCode : o.id;
                                        _pinnedContextTitle = 'Đơn #${_formatShortCode(o.subOrderCode.isNotEmpty ? o.subOrderCode : o.id, prefix: 'SUB')}';
                                      });
                                    },
                                    borderRadius: BorderRadius.circular(12),
                                    child: Container(
                                      padding: const EdgeInsets.all(12),
                                      decoration: BoxDecoration(
                                        color: isCurrent ? const Color(0xFFE6F4F1) : AppColors.surfaceContainerLow,
                                        borderRadius: BorderRadius.circular(12),
                                        border: Border.all(
                                          color: isCurrent ? const Color(0xFF004D56) : const Color(0xFFE2E8F0),
                                        ),
                                      ),
                                      child: Row(
                                        children: [
                                          Container(
                                            padding: const EdgeInsets.all(8),
                                            decoration: BoxDecoration(
                                              color: Colors.white,
                                              borderRadius: BorderRadius.circular(8),
                                            ),
                                            child: const Icon(Icons.receipt_long_outlined, size: 20, color: Color(0xFF004D56)),
                                          ),
                                          const SizedBox(width: 10),
                                          Expanded(
                                            child: Column(
                                              crossAxisAlignment: CrossAxisAlignment.start,
                                              children: [
                                                Text(
                                                  o.serviceName,
                                                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                                  maxLines: 1,
                                                  overflow: TextOverflow.ellipsis,
                                                ),
                                                const SizedBox(height: 3),
                                                if (o.customerName.isNotEmpty) ...[
                                                  Text(
                                                    o.customerName,
                                                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.onSurfaceVariant),
                                                    maxLines: 1,
                                                    overflow: TextOverflow.ellipsis,
                                                  ),
                                                  const SizedBox(height: 2),
                                                ],
                                                Text(
                                                  '#${_formatShortCode(o.subOrderCode.isNotEmpty ? o.subOrderCode : o.id, prefix: 'SUB')}',
                                                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.navyAnchor),
                                                ),
                                              ],
                                            ),
                                          ),
                                          const SizedBox(width: 8),
                                          Text(
                                            '${o.slotDate} ${o.slotTime.length >= 5 ? o.slotTime.substring(0, 5) : o.slotTime}',
                                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.secondary),
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ))
                        : (services.isEmpty
                            ? const Center(
                                child: Text(
                                  'Không có dịch vụ nào',
                                  style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                                ),
                              )
                            : ListView.separated(
                                padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
                                itemCount: services.length,
                                separatorBuilder: (context, index) => const SizedBox(height: 8),
                                itemBuilder: (context, idx) {
                                  final s = services[idx];
                                  final isCurrent = _pinnedContextId == s.id;
                                  return InkWell(
                                    onTap: () {
                                      Navigator.pop(sheetCtx);
                                      setState(() {
                                        _pinnedContextType = 'SERVICE';
                                        _pinnedContextId = s.id;
                                        _pinnedContextTitle = 'Dịch vụ: ${s.name}';
                                      });
                                    },
                                    borderRadius: BorderRadius.circular(12),
                                    child: Container(
                                      padding: const EdgeInsets.all(12),
                                      decoration: BoxDecoration(
                                        color: isCurrent ? const Color(0xFFE6F4F1) : AppColors.surfaceContainerLow,
                                        borderRadius: BorderRadius.circular(12),
                                        border: Border.all(
                                          color: isCurrent ? const Color(0xFF004D56) : const Color(0xFFE2E8F0),
                                        ),
                                      ),
                                      child: Row(
                                        children: [
                                          ClipRRect(
                                            borderRadius: BorderRadius.circular(8),
                                            child: s.imageUrls.isNotEmpty
                                                ? Image.network(
                                                    s.imageUrls.first,
                                                    width: 40,
                                                    height: 40,
                                                    fit: BoxFit.cover,
                                                    errorBuilder: (context, error, stackTrace) => Container(
                                                      width: 40,
                                                      height: 40,
                                                      color: AppColors.surfaceContainer,
                                                      child: const Icon(Icons.surfing, size: 20, color: AppColors.secondary),
                                                    ),
                                                  )
                                                : Container(
                                                    width: 40,
                                                    height: 40,
                                                    color: AppColors.surfaceContainer,
                                                    child: const Icon(Icons.surfing, size: 20, color: AppColors.secondary),
                                                  ),
                                          ),
                                          const SizedBox(width: 10),
                                          Expanded(
                                            child: Column(
                                              crossAxisAlignment: CrossAxisAlignment.start,
                                              children: [
                                                Text(
                                                  s.name,
                                                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                                  maxLines: 1,
                                                  overflow: TextOverflow.ellipsis,
                                                ),
                                                const SizedBox(height: 3),
                                                Text(
                                                  '${s.durationMinutes} phút',
                                                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.onSurfaceVariant),
                                                ),
                                                const SizedBox(height: 2),
                                                Text(
                                                  '#${_formatShortCode(s.id, prefix: 'SRV')}',
                                                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.navyAnchor),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              )),
                  ),
                ],
              ),
            );
          },
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
          // Pinned Context Badge if any
          if (_pinnedContextId != null) ...[
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF004D56).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: const Color(0xFF004D56).withValues(alpha: 0.25)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      _pinnedContextType == 'ORDER' ? Icons.receipt_long_outlined : Icons.surfing_outlined,
                      size: 13,
                      color: const Color(0xFF004D56),
                    ),
                    const SizedBox(width: 5),
                    ConstrainedBox(
                      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.65),
                      child: Text(
                        _pinnedContextTitle ?? '',
                        style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Color(0xFF004D56)),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 6),
                    InkWell(
                      onTap: () => setState(() {
                        _pinnedContextType = null;
                        _pinnedContextId = null;
                        _pinnedContextTitle = null;
                      }),
                      child: const Icon(Icons.close_rounded, size: 14, color: Color(0xFF004D56)),
                    ),
                  ],
                ),
              ),
            ),
          ],
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

          // Bottom buttons row: (📎)  [Image]  ... [Mic] [Send]
          Row(
            children: [
              // Pin context paperclip button (📎)
              _buildToolCircleButton(
                icon: Icons.attach_file_rounded,
                isActive: _showAttachMenu || _pinnedContextId != null,
                onTap: () {
                  setState(() {
                    _showAttachMenu = !_showAttachMenu;
                  });
                },
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
    bool isActive = false,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        width: 32,
        height: 32,
        decoration: BoxDecoration(
          color: isActive ? const Color(0xFF004D56).withValues(alpha: 0.15) : AppColors.surfaceContainerLow,
          shape: BoxShape.circle,
          border: isActive ? Border.all(color: const Color(0xFF004D56), width: 1.2) : null,
        ),
        child: Icon(
          icon,
          size: 17,
          color: isActive ? const Color(0xFF004D56) : AppColors.onSurfaceVariant,
        ),
      ),
    );
  }

  Widget _buildMessengerMenu() {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutBack,
      builder: (context, value, child) {
        return Transform.scale(
          scale: 0.85 + (0.15 * value),
          alignment: Alignment.bottomLeft,
          child: Opacity(
            opacity: value.clamp(0.0, 1.0),
            child: child,
          ),
        );
      },
      child: Container(
        width: 175,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFFE2E8F0), width: 1.0),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.12),
              blurRadius: 16,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildMessengerMenuItem(
              title: 'Đơn hàng (${_db.subOrders.length})',
              onTap: () {
                setState(() => _showAttachMenu = false);
                _openContextPickerSheet(type: 'ORDER');
              },
            ),
            const Divider(color: Color(0xFFF1F5F9), height: 1),
            _buildMessengerMenuItem(
              title: 'Dịch vụ (${_db.services.length})',
              onTap: () {
                setState(() => _showAttachMenu = false);
                _openContextPickerSheet(type: 'SERVICE');
              },
            ),
            if (_pinnedContextId != null) ...[
              const Divider(color: Color(0xFFF1F5F9), height: 1),
              _buildMessengerMenuItem(
                title: 'Gỡ ghim hiện tại',
                textColor: const Color(0xFFE53935),
                onTap: () {
                  setState(() {
                    _showAttachMenu = false;
                    _pinnedContextType = null;
                    _pinnedContextId = null;
                    _pinnedContextTitle = null;
                  });
                },
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMessengerMenuItem({
    required String title,
    required VoidCallback onTap,
    Color textColor = AppColors.navyAnchor,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
        child: Text(
          title,
          style: TextStyle(
            color: textColor,
            fontSize: 13.5,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.1,
          ),
        ),
      ),
    );
  }
}
