import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/notification_repository.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_shapes.dart';
import '../../../../core/theme/app_typography.dart';

class HomeHeader extends StatefulWidget implements PreferredSizeWidget {
  final VoidCallback? onAiTap;
  final VoidCallback? onNotificationTap;
  final VoidCallback? onCartTap;
  final VoidCallback? onSearchTap;
  final VoidCallback? onProfileTap;

  const HomeHeader({
    super.key,
    this.onAiTap,
    this.onNotificationTap,
    this.onCartTap,
    this.onSearchTap,
    this.onProfileTap,
  });

  @override
  Size get preferredSize => const Size.fromHeight(60);

  @override
  State<HomeHeader> createState() => _HomeHeaderState();
}

class _HomeHeaderState extends State<HomeHeader> {
  final NotificationRepository _notifRepo = NotificationRepository.instance;

  @override
  void initState() {
    super.initState();
    _notifRepo.fetchUnreadCount().catchError((_) => 0);
  }

  @override
  Widget build(BuildContext context) {
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
        child: Container(
          decoration: BoxDecoration(
            color: AppColors.surface.withValues(alpha: 0.88),
            boxShadow: AppShapes.headerShadow,
            border: Border(
              bottom: BorderSide(
                color: AppColors.borderSubtle.withValues(alpha: 0.5),
                width: 0.5,
              ),
            ),
          ),
          child: SafeArea(
            bottom: false,
            child: Container(
              height: 60,
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Row(
                children: [
                  // =========================
                  // LOGO + BRAND
                  // =========================
                  Expanded(
                    flex: 3,
                    child: Row(
                      children: [
                        Container(
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            color: AppColors.secondary.withValues(alpha: 0.12),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.sailing,
                            color: AppColors.secondary,
                            size: 17,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Flexible(
                          child: FittedBox(
                            fit: BoxFit.scaleDown,
                            alignment: Alignment.centerLeft,
                            child: LocalizedText(
                              'DANASEA',
                              maxLines: 1,
                              style: AppTypography.headlineSm(
                                color: AppColors.secondary,
                              ).copyWith(
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.5,
                                fontSize: 20,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(width: 8),

                  // =========================
                  // RIGHT ACTIONS
                  // =========================
                  Expanded(
                    flex: 7,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        // AI BUTTON
                        InkWell(
                          onTap: widget.onAiTap,
                          borderRadius: AppShapes.radiusFull,
                          child: Container(
                            height: 30,
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            decoration: BoxDecoration(
                              color: AppColors.secondaryContainer.withValues(
                                alpha: 0.35,
                              ),
                              borderRadius: AppShapes.radiusFull,
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(
                                  Icons.auto_awesome,
                                  color: AppColors.secondary,
                                  size: 14,
                                ),
                                const SizedBox(width: 3),
                                LocalizedText(
                                  'AI',
                                  style: AppTypography.labelSm(
                                    color: AppColors.secondary,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                        const SizedBox(width: 2),

                        // NOTIFICATION
                        ListenableBuilder(
                          listenable: _notifRepo,
                          builder: (context, _) {
                            final unread = _notifRepo.unreadCount;
                            return Stack(
                              clipBehavior: Clip.none,
                              children: [
                                IconButton(
                                  onPressed: widget.onNotificationTap,
                                  padding: const EdgeInsets.all(6),
                                  constraints: const BoxConstraints(),
                                  icon: const Icon(
                                    Icons.notifications_outlined,
                                    color: AppColors.onSurface,
                                    size: 22,
                                  ),
                                ),
                                if (unread > 0)
                                  Positioned(
                                    top: 2,
                                    right: 2,
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 4,
                                        vertical: 1,
                                      ),
                                      constraints: const BoxConstraints(
                                        minWidth: 14,
                                        minHeight: 14,
                                      ),
                                      decoration: BoxDecoration(
                                        color: AppColors.primary,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Center(
                                        child: Text(
                                          unread > 99 ? '99+' : '$unread',
                                          style: AppTypography.labelSm(
                                            color: AppColors.onPrimary,
                                            fontWeight: FontWeight.w700,
                                          ).copyWith(fontSize: 9),
                                        ),
                                      ),
                                    ),
                                  ),
                              ],
                            );
                          },
                        ),

                        const SizedBox(width: 2),

                        // CART
                        Stack(
                          clipBehavior: Clip.none,
                          children: [
                            IconButton(
                              onPressed: widget.onCartTap,
                              padding: const EdgeInsets.all(4),
                              constraints: const BoxConstraints(),
                              icon: const Icon(
                                Icons.shopping_bag_outlined,
                                color: AppColors.onSurface,
                                size: 22,
                              ),
                            ),
                            Positioned(
                              top: 2,
                              right: 2,
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                  vertical: 1,
                                ),
                                constraints: const BoxConstraints(
                                  minWidth: 14,
                                  minHeight: 14,
                                ),
                                decoration: BoxDecoration(
                                  color: AppColors.primaryContainer,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Center(
                                  child: LocalizedText(
                                    '2',
                                    style: AppTypography.labelSm(
                                      color: AppColors.onPrimary,
                                      fontWeight: FontWeight.w700,
                                    ).copyWith(fontSize: 9),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),

                        const SizedBox(width: 4),

                        // PROFILE
                        InkWell(
                          onTap: widget.onProfileTap,
                          borderRadius: BorderRadius.circular(16),
                          child: Container(
                            width: 30,
                            height: 30,
                            decoration: const BoxDecoration(
                              color: AppColors.secondary,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.person,
                              color: AppColors.onSecondary,
                              size: 16,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
