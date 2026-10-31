import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

/// Controller & Overlay helper for flying an item into the top-right cart icon
class FlyToCartAnimator {
  static void run({
    required BuildContext context,
    required GlobalKey startKey,
    required GlobalKey endKey,
    required String? imageUrl,
    required VoidCallback onComplete,
  }) {
    final startBox = startKey.currentContext?.findRenderObject() as RenderBox?;
    final endBox = endKey.currentContext?.findRenderObject() as RenderBox?;

    if (startBox == null || endBox == null) {
      onComplete();
      return;
    }

    final startOffset = startBox.localToGlobal(
      Offset(startBox.size.width / 2, startBox.size.height / 2),
    );

    final endOffset = endBox.localToGlobal(
      Offset(endBox.size.width / 2, endBox.size.height / 2),
    );

    final overlay = Overlay.of(context);
    late OverlayEntry entry;

    entry = OverlayEntry(
      builder: (context) => _FlyAnimationWidget(
        startOffset: startOffset,
        endOffset: endOffset,
        imageUrl: imageUrl,
        onComplete: () {
          if (entry.mounted) {
            entry.remove();
          }
          onComplete();
        },
      ),
    );

    overlay.insert(entry);
  }
}

class _FlyAnimationWidget extends StatefulWidget {
  final Offset startOffset;
  final Offset endOffset;
  final String? imageUrl;
  final VoidCallback onComplete;

  const _FlyAnimationWidget({
    required this.startOffset,
    required this.endOffset,
    this.imageUrl,
    required this.onComplete,
  });

  @override
  State<_FlyAnimationWidget> createState() => _FlyAnimationWidgetState();
}

class _FlyAnimationWidgetState extends State<_FlyAnimationWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _progress;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1320),
    );

    _progress = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOutCubic,
    );

    _controller.forward().then((_) {
      if (mounted) {
        widget.onComplete();
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// Quadratic Bézier curve calculation:
  /// B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
  Offset _computeBezier(double t) {
    final p0 = widget.startOffset;
    final p2 = widget.endOffset;

    // Control point P1 arcs outward/left to give a natural organic trajectory
    final p1 = Offset(
      math.min(p0.dx, p2.dx) - 65,
      p2.dy + (p0.dy - p2.dy) * 0.18,
    );

    final u = 1 - t;
    final tt = t * t;
    final uu = u * u;

    final x = uu * p0.dx + 2 * u * t * p1.dx + tt * p2.dx;
    final y = uu * p0.dy + 2 * u * t * p1.dy + tt * p2.dy;

    return Offset(x, y);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _progress,
      builder: (context, child) {
        final t = _progress.value;
        final position = _computeBezier(t);
        // Scale down smoothly as it approaches the cart icon
        final scale = 1.0 - (t * 0.72);
        // Fade out slightly right before entering the cart
        final opacity = t > 0.85 ? ((1.0 - t) / 0.15).clamp(0.0, 1.0) : 1.0;
        final rotation = t * 0.5;

        const size = 42.0;

        return Positioned(
          left: position.dx - (size / 2),
          top: position.dy - (size / 2),
          child: IgnorePointer(
            child: Opacity(
              opacity: opacity,
              child: Transform.rotate(
                angle: rotation,
                child: Transform.scale(
                  scale: scale,
                  child: Container(
                    width: size,
                    height: size,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.secondary,
                      border: Border.all(color: Colors.white, width: 2.2),
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.secondary.withValues(alpha: 0.45),
                          blurRadius: 10,
                          offset: const Offset(0, 3),
                        ),
                      ],
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: (widget.imageUrl != null && widget.imageUrl!.isNotEmpty)
                        ? Image.network(
                            widget.imageUrl!,
                            fit: BoxFit.cover,
                            errorBuilder: (context, error, stackTrace) => const Icon(
                              Icons.sailing_rounded,
                              size: 22,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(
                            Icons.sailing_rounded,
                            size: 22,
                            color: Colors.white,
                          ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
