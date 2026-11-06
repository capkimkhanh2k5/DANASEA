import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../../features/profile/presentation/screens/partner_account_screen.dart';
import 'symbolic_vendor_avatar.dart';

/// Uniform, consistent AppBar action button for accessing the Partner Account screen.
/// Displays an animated symbolic vendor avatar and a clear 'Tài khoản' label.
class PartnerAccountAppBarButton extends StatelessWidget {
  final VoidCallback? onPressed;

  const PartnerAccountAppBarButton({super.key, this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 14, left: 4),
      child: InkWell(
        onTap: onPressed ??
            () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const PartnerAccountScreen()),
              );
            },
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppColors.borderSubtle),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SymbolicVendorAvatar(
                radius: 12,
                animate: true,
              ),
              SizedBox(width: 6),
              Text(
                'Tài khoản',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: AppColors.navyAnchor,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
