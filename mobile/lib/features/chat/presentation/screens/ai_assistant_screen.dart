import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/assistant_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/data/catalog_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/utils/display_code.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';
import '../../../experience_detail/presentation/screens/experience_detail_screen.dart';

class AiAssistantScreen extends StatefulWidget {
  final String? initialPrompt;

  const AiAssistantScreen({super.key, this.initialPrompt});

  @override
  State<AiAssistantScreen> createState() => _AiAssistantScreenState();
}

class _AiAssistantScreenState extends State<AiAssistantScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _orbAnimController;
  late final Animation<double> _orbFloatAnimation;
  final AssistantRepository _repository = AssistantRepository.instance;
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  late List<AiMessageModel> _messages;
  String? _conversationId;
  bool _isSending = false;
  List<Map<String, dynamic>> _savedConversations = [];
  bool _isLoadingHistory = false;

  String? _pinnedContextType;
  String? _pinnedContextId;
  String? _pinnedContextTitle;
  bool _showAttachMenu = false;

  bool _isRecording = false;
  bool _isCanceling = false;
  int _recordingSeconds = 0;
  Timer? _recordingTimer;
  Timer? _waveTimer;
  final List<double> _waveLevels = List.generate(6, (_) => 0.2);
  final math.Random _random = math.Random();
  Offset _longPressStartPosition = Offset.zero;

  String _formatRecordDuration(int totalSeconds) {
    final m = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final s = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _onLongPressStart(LongPressStartDetails details) {
    if (_isSending) return;
    HapticFeedback.mediumImpact();
    _longPressStartPosition = details.globalPosition;
    _isCanceling = false;
    _startVoiceRecording();
  }

  void _onLongPressMoveUpdate(LongPressMoveUpdateDetails details) {
    if (!_isRecording) return;
    final dx = details.globalPosition.dx - _longPressStartPosition.dx;
    if (dx < -45 && !_isCanceling) {
      HapticFeedback.selectionClick();
      setState(() => _isCanceling = true);
    } else if (dx >= -45 && _isCanceling) {
      HapticFeedback.selectionClick();
      setState(() => _isCanceling = false);
    }
  }

  void _onLongPressEnd(LongPressEndDetails details) {
    if (!_isRecording) return;
    if (_isCanceling) {
      HapticFeedback.selectionClick();
      _cancelVoiceRecording();
    } else {
      HapticFeedback.mediumImpact();
      _stopVoiceRecording();
    }
  }

  void _onLongPressCancel() {
    if (_isRecording) {
      HapticFeedback.selectionClick();
      _cancelVoiceRecording();
    }
  }

  void _startVoiceRecording() {
    if (_isSending) return;
    setState(() {
      _isRecording = true;
      _isCanceling = false;
      _recordingSeconds = 0;
    });

    _recordingTimer?.cancel();
    _recordingTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _recordingSeconds++);
    });

    _waveTimer?.cancel();
    _waveTimer = Timer.periodic(const Duration(milliseconds: 90), (_) {
      if (!mounted) return;
      setState(() {
        for (int i = 0; i < _waveLevels.length; i++) {
          _waveLevels[i] = 0.15 + _random.nextDouble() * 0.85;
        }
      });
    });

    if (_controller.text.trim().isEmpty) {
      final defaultQuery = (_pinnedContextTitle != null && _pinnedContextTitle!.isNotEmpty)
          ? 'Tour này giá bao nhiêu và lịch trình cụ thể thế nào?'
          : 'Gợi ý cho tôi các trải nghiệm biển hot nhất ở Đà Nẵng';
      _injectSpeechText(defaultQuery);
    }
  }

  void _injectSpeechText(String text) {
    setState(() {
      _controller.text = text;
      _controller.selection = TextSelection.fromPosition(
        TextPosition(offset: _controller.text.length),
      );
    });
  }

  void _stopVoiceRecording() {
    _recordingTimer?.cancel();
    _waveTimer?.cancel();
    setState(() {
      _isRecording = false;
      _isCanceling = false;
      _recordingSeconds = 0;
    });
  }

  void _cancelVoiceRecording() {
    _recordingTimer?.cancel();
    _waveTimer?.cancel();
    setState(() {
      _isRecording = false;
      _isCanceling = false;
      _recordingSeconds = 0;
      _controller.clear();
    });
  }

  String _formatShortCode(String raw, {String prefix = 'ORD'}) {
    return formatDisplayCode(raw, prefix: prefix);
  }


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
    _messages = [];
    if (widget.initialPrompt != null &&
        widget.initialPrompt!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _sendQuery(widget.initialPrompt!);
      });
    }
  }

  @override
  void dispose() {
    _recordingTimer?.cancel();
    _waveTimer?.cancel();
    _orbAnimController.dispose();
    _controller.dispose();
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

  Future<void> _sendQuery(String query) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty || _isSending) return;

    final contextType = _pinnedContextType;
    final contextId = _pinnedContextId;

    String contextPrefix = '';
    if (_pinnedContextTitle != null && _pinnedContextTitle!.trim().isNotEmpty) {
      final title = _pinnedContextTitle!.trim();
      contextPrefix = title.startsWith('[') ? title : '[$title]';
    } else if (contextType != null && contextId != null) {
      final label = contextType == 'ORDER' ? 'Đơn hàng' : 'Tour';
      final codePrefix = contextType == 'ORDER' ? 'ORD' : 'SRV';
      contextPrefix = '[$label: #${_formatShortCode(contextId, prefix: codePrefix)}]';
    }

    final messageWithContext = (contextPrefix.isNotEmpty && !trimmed.startsWith('['))
        ? '$contextPrefix $trimmed'
        : trimmed;

    setState(() {
      _isSending = true;
      _messages = [
        ..._messages,
        AiMessageModel(
          id: 'ai-usr-${DateTime.now().millisecondsSinceEpoch}',
          role: AiRole.user,
          content: messageWithContext,
          createdAt: DateTime.now(),
        ),
      ];
    });
    _controller.clear();
    _scrollToBottom();

    try {
      final reply = await _repository.chat(
        message: messageWithContext,
        conversationId: _conversationId,
        contextType: _pinnedContextType,
        contextId: _pinnedContextId,
      );
      if (!mounted) return;
      setState(() {
        if (reply.conversationId.isNotEmpty) {
          _conversationId = reply.conversationId;
        }
        _messages = [
          ..._messages,
          AiMessageModel(
            id: 'ai-bot-${DateTime.now().millisecondsSinceEpoch}',
            role: AiRole.assistant,
            content:
                reply.message.isNotEmpty
                    ? reply.message
                    : tr(
                      context,
                      'Đã phân tích yêu cầu của bạn. Xem gợi ý chi tiết bên dưới.',
                    ),
            recommendedService: reply.recommendedService,
            createdAt: DateTime.now(),
          ),
        ];
      });
      _scrollToBottom();
    } catch (e) {
      if (!mounted) return;
      final fallbackText = (e is AuthFailure &&
              e.message.isNotEmpty &&
              e.message != 'Đã xảy ra lỗi không mong muốn.' &&
              e.message != 'Không thể thực hiện yêu cầu. Vui lòng thử lại.')
          ? e.message
          : 'Hệ thống trợ lý AI đang tạm thời gián đoạn kết nối máy chủ (cầu nối AI chưa được bật). Bạn vui lòng thử lại sau giây lát hoặc xem các tour đề xuất trên trang chủ nhé!';

      setState(() {
        _messages = [
          ..._messages,
          AiMessageModel(
            id: 'ai-bot-err-${DateTime.now().millisecondsSinceEpoch}',
            role: AiRole.assistant,
            content: fallbackText,
            createdAt: DateTime.now(),
          ),
        ];
      });
      _scrollToBottom();
    } finally {
      if (mounted) setState(() => _isSending = false);
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

  // ignore: unused_element
  Future<void> _reloadHistoryOrReset() async {
    if (_isSending) return;
    if (_conversationId != null && _conversationId!.isNotEmpty) {
      setState(() => _isSending = true);
      try {
        final history = await _repository.getConversationHistory(
          _conversationId!,
          limit: 30,
        );
        if (!mounted) return;
        if (history.isNotEmpty) {
          setState(() {
            _messages = history;
            _isSending = false;
          });
          _scrollToBottom();
          return;
        }
      } catch (_) {
        // If reloading fails or empty, reset conversation cleanly
      } finally {
        if (mounted) setState(() => _isSending = false);
      }
    }
    setState(() {
      _conversationId = null;
      _messages = [];
    });
  }

  // ignore: unused_element
  Future<void> _openItinerariesSheet() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AiItinerariesSheet(repository: _repository),
    );
  }

  // ignore: unused_element
  Future<void> _openPreferencesSheet() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AiPreferencesSheet(repository: _repository),
    );
  }

  // ignore: unused_element
  Future<void> _openSupportSheet() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AiSupportSheet(repository: _repository),
    );
  }

  Future<void> _loadSavedConversations() async {
    setState(() => _isLoadingHistory = true);
    try {
      final list = await _repository.listConversations();
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
    Navigator.of(context).pop(); // Close drawer
    if (_conversationId == id) return;
    setState(() {
      _conversationId = id;
      _isSending = true;
    });
    try {
      final history = await _repository.getConversationHistory(id);
      if (mounted) {
        setState(() {
          _messages = history;
          _isSending = false;
        });
        _scrollToBottom();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSending = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Lỗi tải lịch sử hội thoại: $e')),
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
      final ok = await _repository.deleteConversation(id);
      if (ok && mounted) {
        setState(() {
          _savedConversations.removeWhere((c) => c['id']?.toString() == id);
          if (_conversationId == id) {
            _conversationId = null;
            _messages = [];
          }
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã xóa cuộc trò chuyện.')),
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
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 12, 12),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.primary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      Icons.history_rounded,
                      color: AppColors.primary,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Lịch sử trò chuyện',
                      style: AppTypography.headlineSm(color: AppColors.onSurface).copyWith(
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
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: ElevatedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  setState(() {
                    _conversationId = null;
                    _messages = [];
                  });
                },
                icon: const Icon(Icons.add_rounded, size: 20, color: Colors.white),
                label: const Text(
                  'Cuộc trò chuyện mới',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.white),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ),
            const Divider(height: 16, thickness: 1, indent: 16, endIndent: 16),
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
                          separatorBuilder: (_, __) => const SizedBox(height: 4),
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
                                            ? AppColors.primary
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
                                                    ? AppColors.primary
                                                    : AppColors.onSurface,
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
    // Only switch to chat stream when an assistant response has arrived
    final bool hasChatted = _messages.any((m) => m.role == AiRole.assistant);

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

                  // 2. MAIN BODY (HERO OR CHAT STREAM)
                  Expanded(
                    child: hasChatted
                        ? _buildChatStream()
                        : _buildHeroCenter(),
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

                  // 3. FLOATING INPUT BAR
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
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Danasea AI Travel',
                  style: AppTypography.bodyMd(
                    color: AppColors.onSurface,
                  ).copyWith(fontWeight: FontWeight.w700),
                ),
                Text(
                  'Trợ lý du lịch thông minh',
                  style: AppTypography.labelSm(
                    color: AppColors.onSurfaceVariant,
                  ).copyWith(fontSize: 11),
                ),
              ],
            ),
          ),
          // Circular New Chat Button
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
              tooltip: 'Cuộc trò chuyện mới',
              icon: const Icon(
                Icons.add_rounded,
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

  Widget _buildHeroCenter() {
    final userName = AuthSession.instance.profile?['fullName'] ?? '';
    final greetingText = userName.toString().trim().isNotEmpty
        ? 'Tôi có thể giúp gì cho bạn hôm nay, $userName!'
        : 'Tôi có thể giúp gì cho chuyến đi của bạn hôm nay!';

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
                fontWeight: FontWeight.w700,
              ).copyWith(letterSpacing: -0.3, height: 1.35),
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
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }

        final msg = _messages[index];
        final isUser = msg.role == AiRole.user;

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
                    : Border.all(color: AppColors.borderSubtle.withValues(alpha: 0.6)),
              ),
              child: LocalizedText(
                msg.content,
                style: AppTypography.bodyMd(
                  color: isUser ? Colors.white : AppColors.onSurface,
                ),
              ),
            ),

            // Embedded Recommendation Card (if AI suggests a service)
            if (msg.recommendedService != null)
              _buildRecommendedCard(msg.recommendedService!),

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
    bool isLoading = true;
    List<MasterOrderModel> orders = [];
    List<ServiceModel> services = [];

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            void loadData() async {
              try {
                final oResult = await OrderRepository().list(page: 0, size: 20);
                final sResult = await CatalogRepository().search(page: 0);
                if (ctx.mounted) {
                  setSheetState(() {
                    orders = oResult.content;
                    services = sResult.items;
                    isLoading = false;
                  });
                }
              } catch (_) {
                if (ctx.mounted) {
                  setSheetState(() {
                    isLoading = false;
                  });
                }
              }
            }

            if (isLoading) {
              loadData();
            }

            final query = searchCtrl.text.trim().toLowerCase();
            final filteredOrders = orders.where((o) {
              if (query.isEmpty) return true;
              return o.id.toLowerCase().contains(query) ||
                  o.subOrders.any((s) => s.serviceName.toLowerCase().contains(query));
            }).toList();

            final filteredServices = services.where((s) {
              if (query.isEmpty) return true;
              return s.name.toLowerCase().contains(query) ||
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
                          isOrder ? 'Ghim đơn hàng' : 'Ghim Tour / Dịch vụ',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
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
                                    : 'Nhập mã tour hoặc tìm kiếm...',
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
                                _pinnedContextTitle = '${isOrder ? 'Đơn' : 'Tour'} #${_formatShortCode(customId, prefix: isOrder ? 'ORD' : 'SRV')}';
                              });
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.primary,
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
                    child: isLoading
                        ? const Center(child: CircularProgressIndicator())
                        : activeTab == 0
                            ? (filteredOrders.isEmpty
                                ? const Center(
                                    child: Text(
                                      'Chưa có đơn hàng nào',
                                      style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                                    ),
                                  )
                                : ListView.separated(
                                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
                                    itemCount: filteredOrders.length,
                                    separatorBuilder: (context, index) => const SizedBox(height: 8),
                                    itemBuilder: (context, idx) {
                                      final o = filteredOrders[idx];
                                      final tourName = o.subOrders.isNotEmpty ? o.subOrders.first.serviceName : 'Đơn hàng';
                                      final isCurrent = _pinnedContextId == o.id;
                                      return InkWell(
                                        onTap: () {
                                          Navigator.pop(sheetCtx);
                                          setState(() {
                                            _pinnedContextType = 'ORDER';
                                            _pinnedContextId = o.id;
                                            _pinnedContextTitle = 'Đơn #${_formatShortCode(o.id, prefix: 'ORD')}';
                                          });
                                        },
                                        borderRadius: BorderRadius.circular(12),
                                        child: Container(
                                          padding: const EdgeInsets.all(12),
                                          decoration: BoxDecoration(
                                            color: isCurrent ? AppColors.primaryContainer.withValues(alpha: 0.3) : AppColors.surfaceContainerLow,
                                            borderRadius: BorderRadius.circular(12),
                                            border: Border.all(
                                              color: isCurrent ? AppColors.primary : const Color(0xFFE2E8F0),
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
                                                child: const Icon(Icons.receipt_long_outlined, size: 20, color: AppColors.primary),
                                              ),
                                              const SizedBox(width: 10),
                                              Expanded(
                                                child: Column(
                                                  crossAxisAlignment: CrossAxisAlignment.start,
                                                  children: [
                                                    Text(
                                                      tourName,
                                                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.onSurface),
                                                      maxLines: 1,
                                                      overflow: TextOverflow.ellipsis,
                                                    ),
                                                    const SizedBox(height: 3),
                                                    Text(
                                                      '#${_formatShortCode(o.id, prefix: 'ORD')}',
                                                      style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.navyAnchor),
                                                    ),
                                                    const SizedBox(height: 2),
                                                    Text(
                                                      '${o.totalAmount} đ',
                                                      style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: AppColors.primary),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      );
                                    },
                                  ))
                            : (filteredServices.isEmpty
                                ? const Center(
                                    child: Text(
                                      'Không tìm thấy tour/dịch vụ nào',
                                      style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                                    ),
                                  )
                                : ListView.separated(
                                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
                                    itemCount: filteredServices.length,
                                    separatorBuilder: (context, index) => const SizedBox(height: 8),
                                    itemBuilder: (context, idx) {
                                      final s = filteredServices[idx];
                                      final isCurrent = _pinnedContextId == s.id;
                                      return InkWell(
                                        onTap: () {
                                          Navigator.pop(sheetCtx);
                                          setState(() {
                                            _pinnedContextType = 'SERVICE';
                                            _pinnedContextId = s.id;
                                            _pinnedContextTitle = 'Tour: ${s.name}';
                                          });
                                        },
                                        borderRadius: BorderRadius.circular(12),
                                        child: Container(
                                          padding: const EdgeInsets.all(12),
                                          decoration: BoxDecoration(
                                            color: isCurrent ? AppColors.primaryContainer.withValues(alpha: 0.3) : AppColors.surfaceContainerLow,
                                            borderRadius: BorderRadius.circular(12),
                                            border: Border.all(
                                              color: isCurrent ? AppColors.primary : const Color(0xFFE2E8F0),
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
                                                          child: const Icon(Icons.surfing, size: 20, color: AppColors.primary),
                                                        ),
                                                      )
                                                    : Container(
                                                        width: 40,
                                                        height: 40,
                                                        color: AppColors.surfaceContainer,
                                                        child: const Icon(Icons.surfing, size: 20, color: AppColors.primary),
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
                  color: AppColors.primary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.primary.withValues(alpha: 0.25)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      _pinnedContextType == 'ORDER' ? Icons.receipt_long_outlined : Icons.surfing_outlined,
                      size: 13,
                      color: AppColors.primary,
                    ),
                    const SizedBox(width: 5),
                    ConstrainedBox(
                      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.65),
                      child: Text(
                        _pinnedContextTitle ?? '',
                        style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.primary),
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
                      child: const Icon(Icons.close_rounded, size: 14, color: AppColors.primary),
                    ),
                  ],
                ),
              ),
            ),
          ],
          // Live recording indicator bar
          if (_isRecording) ...[
            Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: _isCanceling
                    ? const Color(0xFFFEF2F2)
                    : const Color(0xFF006874).withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: _isCanceling ? const Color(0xFFFCA5A5) : const Color(0xFF006874).withValues(alpha: 0.25),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: _isCanceling ? const Color(0xFFEF4444) : const Color(0xFF006874),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _isCanceling
                        ? 'Thả tay để hủy'
                        : 'Đang thu âm: ' + _formatRecordDuration(_recordingSeconds),
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: _isCanceling ? const Color(0xFFDC2626) : const Color(0xFF006874),
                      fontFamily: 'monospace',
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: SizedBox(
                      height: 16,
                      child: Row(
                        children: List.generate(_waveLevels.length, (idx) {
                          final barHeight = 4.0 + (_waveLevels[idx] * 12.0);
                          return AnimatedContainer(
                            duration: const Duration(milliseconds: 90),
                            margin: const EdgeInsets.symmetric(horizontal: 1.5),
                            width: 3.0,
                            height: barHeight,
                            decoration: BoxDecoration(
                              color: _isCanceling ? const Color(0xFFEF4444) : const Color(0xFF006874),
                              borderRadius: BorderRadius.circular(2),
                            ),
                          );
                        }),
                      ),
                    ),
                  ),
                  Text(
                    _isCanceling ? 'Đã hủy' : 'Thả để chốt • Trượt để hủy',
                    style: TextStyle(
                      fontSize: 10,
                      color: _isCanceling ? const Color(0xFFDC2626) : const Color(0xFF64748B),
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ],
              ),
            ),
          ],
          // Input field (Live Speech-to-Text directly populates here)
          TextField(
            controller: _controller,
            enabled: !_isSending,
            style: AppTypography.bodyMd(color: AppColors.onSurface),
            decoration: InputDecoration(
              hintText: _isRecording
                  ? 'Đang nghe bạn nói... lời nói chuyển thành chữ tại đây'
                  : tr(context, 'Hỏi bất kỳ điều gì...'),
              hintStyle: AppTypography.bodySm(
                color: _isRecording ? const Color(0xFFEF4444) : AppColors.outline,
              ),
              border: InputBorder.none,
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 4,
                vertical: 8,
              ),
            ),
            onSubmitted: _sendQuery,
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

              // Waveform / Mic voice button (Hold to record, release to auto-finish)
              GestureDetector(
                onLongPressStart: _onLongPressStart,
                onLongPressMoveUpdate: _onLongPressMoveUpdate,
                onLongPressEnd: _onLongPressEnd,
                onLongPressCancel: _onLongPressCancel,
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Nhấn và giữ để thu âm giọng nói'),
                      duration: Duration(seconds: 2),
                    ),
                  );
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 160),
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _isRecording
                        ? (_isCanceling ? const Color(0xFFEF4444) : const Color(0xFF006874))
                        : AppColors.secondary.withValues(alpha: 0.1),
                    boxShadow: _isRecording
                        ? [
                            BoxShadow(
                              color: (_isCanceling ? const Color(0xFFEF4444) : const Color(0xFF006874)).withValues(alpha: 0.35),
                              blurRadius: 8,
                              spreadRadius: 1,
                            ),
                          ]
                        : null,
                  ),
                  child: Icon(
                    _isRecording ? Icons.mic_rounded : Icons.graphic_eq_rounded,
                    size: 22,
                    color: _isRecording ? Colors.white : AppColors.secondary,
                  ),
                ),
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
                  onPressed: () {
                    if (_isRecording) {
                      _stopVoiceRecording();
                    }
                    _sendQuery(_controller.text);
                  },
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
          color: isActive ? AppColors.primary.withValues(alpha: 0.15) : AppColors.surfaceContainerLow,
          shape: BoxShape.circle,
          border: isActive ? Border.all(color: AppColors.primary, width: 1.2) : null,
        ),
        child: Icon(
          icon,
          size: 17,
          color: isActive ? AppColors.primary : AppColors.onSurfaceVariant,
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
              title: 'Đơn của tôi',
              onTap: () {
                setState(() => _showAttachMenu = false);
                _openContextPickerSheet(type: 'ORDER');
              },
            ),
            const Divider(color: Color(0xFFF1F5F9), height: 1),
            _buildMessengerMenuItem(
              title: 'Tour / Dịch vụ',
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

  Widget _buildRecommendedCard(ServiceModel service) {
    return Container(
      margin: const EdgeInsets.only(top: 4, bottom: 8),
      width: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: AppShapes.radiusDefault,
        boxShadow: AppShapes.shadowLevel2,
        border: Border.all(color: AppColors.borderOcean),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            height: 120,
            width: double.infinity,
            child: Image.network(
              service.imageUrls.isNotEmpty
                  ? service.imageUrls.first
                  : 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=500&auto=format&fit=crop&q=80',
              fit: BoxFit.cover,
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppShapes.spaceSm),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        service.vendorName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.labelSm(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.primaryFixed,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: LocalizedText(
                        'Được gợi ý',
                        style: AppTypography.labelSm(
                          color: AppColors.primary,
                        ).copyWith(fontSize: 10),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                LocalizedText(
                  service.name,
                  style: AppTypography.headlineSm(color: AppColors.onSurface),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: LocalizedText(
                        '${service.price ~/ 1000}.000 đ / khách',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.headlineSm(
                          color: AppColors.primary,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 6,
                        ),
                      ),
                      onPressed: () {
                        if (service.id.isNotEmpty) {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder:
                                  (_) => CatalogDetailScreen(
                                    serviceId: service.id,
                                  ),
                            ),
                          );
                        } else {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder:
                                  (_) =>
                                      ExperienceDetailScreen(service: service),
                            ),
                          );
                        }
                      },
                      child: LocalizedText(
                        'Xem & Đặt',
                        style: AppTypography.labelSm(color: Colors.white),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AiItinerariesSheet extends StatefulWidget {
  final AssistantRepository repository;
  const _AiItinerariesSheet({required this.repository});

  @override
  State<_AiItinerariesSheet> createState() => _AiItinerariesSheetState();
}

class _AiItinerariesSheetState extends State<_AiItinerariesSheet> {
  final TextEditingController _queryCtrl = TextEditingController(
    text: 'Lịch trình biển Đà Nẵng 1 ngày cho 2 người ngân sách 1 triệu',
  );
  List<AiSavedItineraryModel> _itineraries = const [];
  bool _loading = true;
  bool _creating = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _queryCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list = await widget.repository.listItineraries();
      if (!mounted) return;
      setState(() {
        _itineraries = list;
        _loading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể tải danh sách lịch trình AI.';
        _loading = false;
      });
    }
  }

  Future<void> _createPlan() async {
    final q = _queryCtrl.text.trim();
    if (q.isEmpty || _creating) return;
    setState(() {
      _creating = true;
      _error = null;
    });
    try {
      final created = await widget.repository.createItinerary(query: q);
      if (!mounted) return;
      setState(() {
        _itineraries = [created, ..._itineraries];
        _creating = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _creating = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể tạo lịch trình AI lúc này.';
        _creating = false;
      });
    }
  }

  Future<void> _accept(AiSavedItineraryModel item) async {
    try {
      await widget.repository.acceptItinerary(
        id: item.id,
        expectedVersion: item.version,
      );
      if (mounted) _load();
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(e.message)));
      }
    }
  }

  Future<void> _replan(AiSavedItineraryModel item) async {
    try {
      await widget.repository.replanItinerary(
        id: item.id,
        expectedVersion: item.version,
        query: _queryCtrl.text.trim(),
      );
      if (mounted) _load();
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(e.message)));
      }
    }
  }

  Future<void> _archive(AiSavedItineraryModel item) async {
    try {
      await widget.repository.archiveItinerary(
        id: item.id,
        expectedVersion: item.version,
      );
      if (mounted) _load();
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: AppShapes.gutterMobile,
          right: AppShapes.gutterMobile,
          top: AppShapes.gutterMobile,
          bottom:
              MediaQuery.of(context).viewInsets.bottom + AppShapes.gutterMobile,
        ),
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.72,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  LocalizedText(
                    'Lịch trình du lịch biển AI',
                    style: AppTypography.headlineSm(color: AppColors.onSurface),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _queryCtrl,
                      decoration: InputDecoration(
                        hintText: tr(
                          context,
                          'Nhập yêu cầu lập lịch trình...',
                        ),
                        border: const OutlineInputBorder(),
                        isDense: true,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.secondary,
                      foregroundColor: Colors.white,
                    ),
                    onPressed: _creating ? null : _createPlan,
                    child:
                        _creating
                            ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                            : const LocalizedText('Tạo mới'),
                  ),
                ],
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                LocalizedText(
                  _error!,
                  style: AppTypography.bodySm(color: AppColors.error),
                ),
              ],
              const SizedBox(height: 10),
              Expanded(
                child:
                    _loading
                        ? const Center(child: CircularProgressIndicator())
                        : _itineraries.isEmpty
                        ? Center(
                          child: LocalizedText(
                            'Chưa có lịch trình AI nào được lưu.',
                            style: AppTypography.bodyMd(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        )
                        : ListView.separated(
                          itemCount: _itineraries.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (_, idx) {
                            final item = _itineraries[idx];
                            return Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainerLow,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: AppColors.borderSubtle,
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        '${item.lifecycle} • v${item.version}',
                                        style: AppTypography.labelMd(
                                          color: AppColors.secondary,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                      Text(
                                        '${item.totalPrice} đ',
                                        style: AppTypography.labelMd(
                                          color: AppColors.primary,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  for (final act in item.items)
                                    Padding(
                                      padding: const EdgeInsets.only(bottom: 4),
                                      child: Text(
                                        '• ${act.serviceName} (${act.quantity} khách)',
                                        style: AppTypography.bodySm(
                                          color: AppColors.onSurface,
                                        ),
                                      ),
                                    ),
                                  const SizedBox(height: 6),
                                  Wrap(
                                    spacing: 8,
                                    children: [
                                      TextButton(
                                        onPressed: () => _accept(item),
                                        child: const LocalizedText('Chốt lịch'),
                                      ),
                                      TextButton(
                                        onPressed: () => _replan(item),
                                        child: const LocalizedText(
                                          'Lập lại lịch',
                                        ),
                                      ),
                                      TextButton(
                                        onPressed: () => _archive(item),
                                        child: const LocalizedText('Lưu trữ'),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            );
                          },
                        ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AiPreferencesSheet extends StatefulWidget {
  final AssistantRepository repository;
  const _AiPreferencesSheet({required this.repository});

  @override
  State<_AiPreferencesSheet> createState() => _AiPreferencesSheetState();
}

class _AiPreferencesSheetState extends State<_AiPreferencesSheet> {
  final TextEditingController _interestsCtrl = TextEditingController();
  final TextEditingController _exclusionsCtrl = TextEditingController();
  bool _enabled = true;
  int _version = 0;
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _interestsCtrl.dispose();
    _exclusionsCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final prof = await widget.repository.getPreferences();
      if (!mounted) return;
      setState(() {
        _version = prof.version;
        _enabled = prof.enabled;
        _interestsCtrl.text = prof.interests.join(', ');
        _exclusionsCtrl.text = prof.exclusions.join(', ');
        _loading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể tải sở thích cá nhân AI.';
        _loading = false;
      });
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    final interests =
        _interestsCtrl.text
            .split(',')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .toList();
    final exclusions =
        _exclusionsCtrl.text
            .split(',')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .toList();
    try {
      final updated = await widget.repository.updatePreferences(
        expectedVersion: _version,
        enabled: _enabled,
        interests: interests,
        exclusions: exclusions,
      );
      if (!mounted) return;
      setState(() {
        _version = updated.version;
        _saving = false;
      });
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText('Đã lưu sở thích cá nhân hoá AI!'),
          backgroundColor: AppColors.secondary,
        ),
      );
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _saving = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể lưu sở thích cá nhân AI.';
        _saving = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: AppShapes.gutterMobile,
          right: AppShapes.gutterMobile,
          top: AppShapes.gutterMobile,
          bottom:
              MediaQuery.of(context).viewInsets.bottom + AppShapes.gutterMobile,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                LocalizedText(
                  'Sở thích cá nhân hoá AI',
                  style: AppTypography.headlineSm(color: AppColors.onSurface),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(context),
                ),
              ],
            ),
            if (_loading)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              )
            else ...[
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const LocalizedText('Bật gợi ý theo sở thích đã lưu'),
                value: _enabled,
                onChanged: (val) => setState(() => _enabled = val),
              ),
              TextField(
                controller: _interestsCtrl,
                decoration: InputDecoration(
                  labelText: tr(
                    context,
                    'Sở thích (phân cách bằng dấu phẩy)',
                  ),
                  hintText: 'sup, snorkeling, sunset',
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _exclusionsCtrl,
                decoration: InputDecoration(
                  labelText: tr(
                    context,
                    'Hoạt động muốn tránh (phân cách bằng dấu phẩy)',
                  ),
                  hintText: 'jetski, parasailing',
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                LocalizedText(
                  _error!,
                  style: AppTypography.bodySm(color: AppColors.error),
                ),
              ],
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                  ),
                  onPressed: _saving ? null : _save,
                  child: const LocalizedText('Lưu thiết lập'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _AiSupportSheet extends StatefulWidget {
  final AssistantRepository repository;
  const _AiSupportSheet({required this.repository});

  @override
  State<_AiSupportSheet> createState() => _AiSupportSheetState();
}

class _AiSupportSheetState extends State<_AiSupportSheet> {
  final TextEditingController _msgCtrl = TextEditingController();
  List<MasterOrderModel> _orders = const [];
  List<AiSupportRequestModel> _requests = const [];
  String? _selectedOrderId;
  bool _loading = true;
  bool _submitting = false;
  String? _aiReply;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _msgCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        OrderRepository().list(page: 0, size: 20),
        widget.repository.listSupportRequests(),
      ]);
      if (!mounted) return;
      final page = results[0] as OrderPage;
      final reqs = results[1] as List<AiSupportRequestModel>;
      setState(() {
        _orders = page.content;
        if (_orders.isNotEmpty && _selectedOrderId == null) {
          _selectedOrderId = _orders.first.id;
        }
        _requests = reqs;
        _loading = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể tải dữ liệu hỗ trợ đơn hàng.';
        _loading = false;
      });
    }
  }

  Future<void> _askAiSupport() async {
    final msg = _msgCtrl.text.trim();
    if (msg.isEmpty || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final res = await widget.repository.askAiSupport(
        orderId: _selectedOrderId,
        message: msg,
        includeCancellationPreview: true,
      );
      if (!mounted) return;
      setState(() {
        _aiReply =
            res['answer']?.toString() ??
            res['summary']?.toString() ??
            res['message']?.toString() ??
            res.toString();
        _submitting = false;
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _submitting = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể tra cứu hỗ trợ AI.';
        _submitting = false;
      });
    }
  }

  Future<void> _createSupportHandoff() async {
    final msg = _msgCtrl.text.trim();
    if (_selectedOrderId == null || msg.isEmpty || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.repository.previewSupportRequest(
        orderId: _selectedOrderId!,
        kind: 'HANDOFF',
        message: msg,
      );
      final created = await widget.repository.createSupportRequest(
        orderId: _selectedOrderId!,
        kind: 'HANDOFF',
        message: msg,
      );
      if (!mounted) return;
      setState(() {
        _requests = [created, ..._requests];
        _submitting = false;
        _msgCtrl.clear();
      });
    } on AuthFailure catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _submitting = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Không thể gửi phiếu hỗ trợ.';
        _submitting = false;
      });
    }
  }

  Future<void> _cancelRequest(AiSupportRequestModel req) async {
    try {
      await widget.repository.cancelSupportRequest(
        id: req.id,
        expectedVersion: req.version,
      );
      if (mounted) _load();
    } on AuthFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: LocalizedText(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: AppShapes.gutterMobile,
          right: AppShapes.gutterMobile,
          top: AppShapes.gutterMobile,
          bottom:
              MediaQuery.of(context).viewInsets.bottom + AppShapes.gutterMobile,
        ),
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.72,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  LocalizedText(
                    'Hỗ trợ đơn hàng AI & CSKH',
                    style: AppTypography.headlineSm(color: AppColors.onSurface),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              if (_loading)
                const Expanded(
                  child: Center(child: CircularProgressIndicator()),
                )
              else ...[
                if (_orders.isNotEmpty)
                  DropdownButtonFormField<String>(
                    initialValue: _selectedOrderId,
                    decoration: InputDecoration(
                      labelText: tr(context, 'Đơn hàng cần hỗ trợ'),
                      border: const OutlineInputBorder(),
                      isDense: true,
                    ),
                    items:
                        _orders
                            .map(
                              (o) => DropdownMenuItem(
                                value: o.id,
                                child: Text(
                                  '#${o.id.length > 8 ? o.id.substring(0, 8).toUpperCase() : o.id} (${o.totalAmount} đ)',
                                ),
                              ),
                            )
                            .toList(),
                    onChanged: (v) => setState(() => _selectedOrderId = v),
                  ),
                const SizedBox(height: 8),
                TextField(
                  controller: _msgCtrl,
                  decoration: InputDecoration(
                    hintText: tr(
                      context,
                      'Nhập nội dung cần tư vấn chính sách hoặc chuyển nhân viên...',
                    ),
                    border: const OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _submitting ? null : _askAiSupport,
                        child: const LocalizedText('Hỏi AI về đơn'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                        ),
                        onPressed:
                            _submitting || _selectedOrderId == null
                                ? null
                                : _createSupportHandoff,
                        child: const LocalizedText('Gửi phiếu CSKH'),
                      ),
                    ),
                  ],
                ),
                if (_error != null) ...[
                  const SizedBox(height: 6),
                  LocalizedText(
                    _error!,
                    style: AppTypography.bodySm(color: AppColors.error),
                  ),
                ],
                if (_aiReply != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.secondaryContainer.withValues(
                        alpha: 0.3,
                      ),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      _aiReply!,
                      style: AppTypography.bodySm(color: AppColors.onSurface),
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                Expanded(
                  child: ListView.separated(
                    itemCount: _requests.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (_, idx) {
                      final r = _requests[idx];
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          '${r.kind} • ${r.status}',
                          style: AppTypography.labelMd(
                            color: AppColors.onSurface,
                          ),
                        ),
                        subtitle: Text(
                          r.message,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing:
                            r.status == 'WAITING_REVIEW'
                                ? TextButton(
                                  onPressed: () => _cancelRequest(r),
                                  child: const LocalizedText('Hủy'),
                                )
                                : null,
                      );
                    },
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
