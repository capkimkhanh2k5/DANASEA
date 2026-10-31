import 'package:flutter/material.dart';
import 'package:mobile/core/l10n/app_localizations.dart';
import '../../../../core/data/notification_repository.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../shared/widgets/danasea_logo.dart';
import '../../../cart/data/cart_manager.dart';

class HomeHeader extends StatefulWidget implements PreferredSizeWidget {
  final bool isScrolled;
  final VoidCallback? onNotificationTap;
  final VoidCallback? onCartTap;
  final VoidCallback? onSearchTap;

  const HomeHeader({
    super.key,
    this.isScrolled = false,
    this.onNotificationTap,
    this.onCartTap,
    this.onSearchTap,
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
    final isScrolled = widget.isScrolled;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      decoration: BoxDecoration(
        color: isScrolled ? Colors.white : Colors.transparent,
        boxShadow: isScrolled
            ? [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.06),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ]
            : null,
        border: isScrolled
            ? Border(
                bottom: BorderSide(
                  color: AppColors.borderSubtle.withValues(alpha: 0.7),
                  width: 0.8,
                ),
              )
            : null,
      ),
      child: SafeArea(
        bottom: false,
        child: Container(
          height: 60,
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              // =========================
              // LOGO + BRAND
              // =========================
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const DanaSeaLogo(size: 28),
                  const SizedBox(width: 8),
                  LocalizedText(
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
                ],
              ),

              const Spacer(),

              // =========================
              // RIGHT ACTIONS: NOTIFICATION & CART
              // =========================
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
                          size: 24,
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

              const SizedBox(width: 6),

              // CART
              ListenableBuilder(
                listenable: CartManager.instance,
                builder: (context, _) {
                  final cartCount = CartManager.instance.count;
                  return Stack(
                    clipBehavior: Clip.none,
                    children: [
                      IconButton(
                        onPressed: widget.onCartTap,
                        padding: const EdgeInsets.all(6),
                        constraints: const BoxConstraints(),
                        icon: const Icon(
                          Icons.shopping_bag_outlined,
                          color: AppColors.onSurface,
                          size: 24,
                        ),
                      ),
                      if (cartCount > 0)
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
                              color: const Color(0xFFEF4444),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Center(
                              child: Text(
                                cartCount > 99 ? '99+' : '$cartCount',
                                style: AppTypography.labelSm(
                                  color: Colors.white,
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
            ],
          ),
        ),
      ),
    );
  }
}
