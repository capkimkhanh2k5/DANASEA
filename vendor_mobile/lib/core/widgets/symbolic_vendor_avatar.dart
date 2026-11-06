import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

/// Clean, professional vendor/partner avatar widget.
/// Standardized for merchant applications with crisp brand styling.
class SymbolicVendorAvatar extends StatelessWidget {
  final double radius;
  final String? imageUrl;
  final String? initials;
  final bool showVerifiedBadge;
  final bool animate; // Kept for API backwards compatibility

  const SymbolicVendorAvatar({
    super.key,
    this.radius = 24.0,
    this.imageUrl,
    this.initials,
    this.showVerifiedBadge = false,
    this.animate = false,
  });

  @override
  Widget build(BuildContext context) {
    final size = radius * 2;
    final isCompact = radius <= 18;

    Widget avatarContent;

    if (imageUrl != null && imageUrl!.trim().isNotEmpty) {
      avatarContent = ClipOval(
        child: Image.network(
          imageUrl!,
          width: size,
          height: size,
          fit: BoxFit.cover,
          errorBuilder: (context, error, stackTrace) => _buildFallback(size, isCompact),
        ),
      );
    } else {
      avatarContent = _buildFallback(size, isCompact);
    }

    if (!showVerifiedBadge) {
      return avatarContent;
    }

    final badgeSize = (radius * 0.52).clamp(16.0, 24.0);
    return Stack(
      clipBehavior: Clip.none,
      children: [
        avatarContent,
        Positioned(
          bottom: 0,
          right: 0,
          child: Container(
            width: badgeSize,
            height: badgeSize,
            decoration: BoxDecoration(
              color: const Color(0xFF10B981),
              shape: BoxShape.circle,
              border: Border.all(color: Colors.white, width: 2),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.12),
                  blurRadius: 3,
                  offset: const Offset(0, 1),
                ),
              ],
            ),
            child: Icon(
              Icons.check_rounded,
              color: Colors.white,
              size: badgeSize * 0.65,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildFallback(double size, bool isCompact) {
    if (initials != null && initials!.trim().isNotEmpty) {
      return Container(
        width: size,
        height: size,
        decoration: const BoxDecoration(
          color: Color(0xFF006874),
          shape: BoxShape.circle,
        ),
        alignment: Alignment.center,
        child: Text(
          initials!.trim().substring(0, 1).toUpperCase(),
          style: TextStyle(
            color: Colors.white,
            fontSize: size * 0.45,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    }

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: const Color(0xFFE6F4F6),
        shape: BoxShape.circle,
        border: Border.all(
          color: AppColors.secondary.withValues(alpha: 0.25),
          width: isCompact ? 1.0 : 1.5,
        ),
      ),
      child: Center(
        child: Icon(
          Icons.storefront_rounded,
          color: AppColors.secondary,
          size: size * 0.54,
        ),
      ),
    );
  }
}
