import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/data/assistant_repository.dart';
import '../../../../core/data/order_repository.dart';
import '../../../../core/models/danasea_models.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../experience_detail/presentation/screens/catalog_detail_screen.dart';
import '../../../experience_detail/presentation/screens/experience_detail_screen.dart';

class AiAssistantScreen extends StatefulWidget {
  final String? initialPrompt;

  const AiAssistantScreen({super.key, this.initialPrompt});

  @override
  State<AiAssistantScreen> createState() => _AiAssistantScreenState();
}

class _AiAssistantScreenState extends State<AiAssistantScreen> {
  final AssistantRepository _repository = AssistantRepository.instance;
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  late List<AiMessageModel> _messages;
  String? _conversationId;
  bool _isSending = false;

  final List<String> _suggestedPrompts = [
    'Đi biển 2 người dưới 300k/người?',
    'Lặn ngắm san hô Sơn Trà hôm nay biển có trong không?',
    'Gợi ý lịch trình biển 1 ngày ở Đà Nẵng',
  ];

  @override
  void initState() {
    super.initState();
    _messages = [
      AiMessageModel(
        id: 'ai-welcome',
        role: AiRole.assistant,
        content:
            'Xin chào! Tôi là trợ lý du lịch biển DANASEA AI. Bạn muốn tìm hoạt động biển, lập lịch trình hay hỗ trợ đơn hàng hôm nay?',
        createdAt: DateTime.now(),
      ),
    ];
    if (widget.initialPrompt != null &&
        widget.initialPrompt!.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _sendQuery(widget.initialPrompt!);
      });
    }
  }

  @override
  void dispose() {
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

    setState(() {
      _isSending = true;
      _messages = [
        ..._messages,
        AiMessageModel(
          id: 'ai-usr-${DateTime.now().millisecondsSinceEpoch}',
          role: AiRole.user,
          content: trimmed,
          createdAt: DateTime.now(),
        ),
      ];
    });
    _controller.clear();
    _scrollToBottom();

    try {
      final reply = await _repository.chat(
        message: trimmed,
        conversationId: _conversationId,
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
    } on AuthFailure catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LocalizedText(e.message),
          backgroundColor: AppColors.error,
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: LocalizedText(
            'Không thể kết nối trợ lý AI lúc này. Vui lòng thử lại.',
          ),
          backgroundColor: AppColors.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _isSending = false);
    }
  }

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
      _messages = [
        AiMessageModel(
          id: 'ai-welcome',
          role: AiRole.assistant,
          content:
              'Xin chào! Tôi là trợ lý du lịch biển DANASEA AI. Bạn muốn tìm hoạt động biển, lập lịch trình hay hỗ trợ đơn hàng hôm nay?',
          createdAt: DateTime.now(),
        ),
      ];
    });
  }

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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () => Navigator.pop(context),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: AppColors.secondaryContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.auto_awesome,
                size: 16,
                color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 6),
            Flexible(
              child: LocalizedText(
                'DANASEA AI',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: AppTypography.headlineSm(color: AppColors.onSurface),
              ),
            ),
          ],
        ),
        centerTitle: true,
        actions: [
          IconButton(
            onPressed: _openItinerariesSheet,
            icon: const Icon(
              Icons.map_outlined,
              size: 20,
              color: AppColors.secondary,
            ),
            tooltip: tr(context, 'Lịch trình AI'),
          ),
          IconButton(
            onPressed: _openPreferencesSheet,
            icon: const Icon(
              Icons.tune,
              size: 20,
              color: AppColors.secondary,
            ),
            tooltip: tr(context, 'Sở thích AI'),
          ),
          IconButton(
            onPressed: _openSupportSheet,
            icon: const Icon(
              Icons.support_agent,
              size: 20,
              color: AppColors.secondary,
            ),
            tooltip: tr(context, 'Hỗ trợ đơn hàng AI'),
          ),
          IconButton(
            onPressed: _reloadHistoryOrReset,
            icon: const Icon(
              Icons.refresh,
              size: 20,
              color: AppColors.secondary,
            ),
            tooltip: tr(context, 'Làm mới'),
          ),
        ],
      ),
      body: Column(
        children: [
          // LIVE OCEAN ALERT STRIP
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: AppColors.surfaceContainerLow,
            child: Row(
              children: [
                const Icon(Icons.waves, size: 16, color: AppColors.secondary),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LocalizedText(
                        'TRẠNG THÁI BIỂN ĐÀ NẴNG HÔM NAY',
                        style: AppTypography.labelSm(
                          color: AppColors.onSurfaceVariant,
                          fontWeight: FontWeight.w700,
                        ).copyWith(fontSize: 10),
                      ),
                      LocalizedText(
                        'Mỹ Khê & Sơn Trà: Sóng 0.4m • Nắng dịu • Lý tưởng',
                        style: AppTypography.labelSm(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.w700,
                        ).copyWith(fontSize: 11),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.secondaryContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: LocalizedText(
                    'LIVE',
                    style: AppTypography.labelSm(
                      color: AppColors.onSecondaryContainer,
                      fontWeight: FontWeight.w800,
                    ).copyWith(fontSize: 10),
                  ),
                ),
              ],
            ),
          ),

          // CHAT STREAM
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.all(AppShapes.gutterMobile),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final msg = _messages[index];
                final isUser = msg.role == AiRole.user;

                return Column(
                  crossAxisAlignment:
                      isUser
                          ? CrossAxisAlignment.end
                          : CrossAxisAlignment.start,
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
                              width: 18,
                              height: 18,
                              decoration: const BoxDecoration(
                                color: AppColors.secondary,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.smart_toy,
                                size: 11,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(width: 4),
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
                      padding: const EdgeInsets.all(12),
                      constraints: BoxConstraints(
                        maxWidth: MediaQuery.of(context).size.width * 0.85,
                      ),
                      decoration: BoxDecoration(
                        color:
                            isUser
                                ? AppColors.primary
                                : AppColors.surfaceContainerLowest,
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: AppShapes.shadowLevel1,
                        border:
                            isUser
                                ? null
                                : Border.all(color: AppColors.borderSubtle),
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
            ),
          ),

          // SUGGESTED PROMPTS
          Container(
            height: 38,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _suggestedPrompts.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                return ActionChip(
                  label: LocalizedText(
                    _suggestedPrompts[index],
                    style: AppTypography.labelSm(color: AppColors.secondary),
                  ),
                  backgroundColor: AppColors.secondaryContainer.withValues(
                    alpha: 0.3,
                  ),
                  onPressed:
                      _isSending
                          ? null
                          : () =>
                              _sendQuery(tr(context, _suggestedPrompts[index])),
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
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: TextField(
                      controller: _controller,
                      enabled: !_isSending,
                      decoration: InputDecoration(
                        hintText: tr(
                          context,
                          'Hỏi bất kỳ điều gì về du lịch biển Đà Nẵng...',
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
                      onSubmitted: _sendQuery,
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
                            onPressed: () => _sendQuery(_controller.text),
                          ),
                ),
              ],
            ),
          ),
        ],
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
