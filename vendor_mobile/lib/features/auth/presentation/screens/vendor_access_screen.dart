import 'package:flutter/material.dart';
import '../../../../core/auth/auth_session.dart';
import '../../../../core/l10n/app_localizations.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../navigation/presentation/screens/main_navigation_screen.dart';
import '../../../profile/presentation/screens/vendor_documents_screen.dart';
import 'login_register_screen.dart';

class VendorAccessScreen extends StatefulWidget {
  const VendorAccessScreen({super.key});
  @override
  State<VendorAccessScreen> createState() => _VendorAccessScreenState();
}

class _VendorAccessScreenState extends State<VendorAccessScreen> {
  final fields = <String, String>{
    'businessName': 'Tên doanh nghiệp',
    'taxCode': 'Mã số thuế',
    'address': 'Địa chỉ',
    'bankAccountNumber': 'Số tài khoản ngân hàng',
    'bankName': 'Tên ngân hàng',
    'bankAccountHolder': 'Chủ tài khoản',
  };
  late final controllers = {
    for (final key in fields.keys) key: TextEditingController(),
  };
  Map<String, dynamic>? profile;
  bool busy = true;
  bool missing = false;
  String? error;
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    for (final c in controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      profile = await AuthSession.instance.request('/api/vendor/profile');
      missing = false;
      if (profile?['verificationStatus'] == 'APPROVED' && mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const MainNavigationScreen()),
        );
      }
    } on AuthFailure catch (e) {
      if (e.status == 404) {
        missing = true;
      } else {
        error = e.message;
      }
    } catch (e) {
      error = e.toString();
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> submit() async {
    if (controllers.values.any((c) => c.text.trim().isEmpty)) {
      setState(() => error = 'Vui lòng nhập đầy đủ thông tin.');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await AuthSession.instance.request(
        '/api/vendor/profile',
        method: 'POST',
        data: {for (final e in controllers.entries) e.key: e.value.text.trim()},
      );
      await load();
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const LocalizedText('Tài khoản đối tác')),
    body: ListView(
      padding: const EdgeInsets.all(24),
      children: [
        if (busy) const LinearProgressIndicator(),
        if (error != null) LocalizedText(error!),
        if (missing) ...[
          const LocalizedText('Hoàn thiện hồ sơ doanh nghiệp'),
          for (final e in fields.entries)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: TextField(
                controller: controllers[e.key],
                decoration: InputDecoration(labelText: tr(context, e.value)),
              ),
            ),
          FilledButton(
            onPressed: busy ? null : submit,
            child: const LocalizedText('Gửi hồ sơ'),
          ),
        ] else if (profile != null) ...[
          if (profile!['verificationStatus'] == 'REJECTED')
            Container(
              padding: const EdgeInsets.all(16),
              margin: const EdgeInsets.only(bottom: 20),
              decoration: BoxDecoration(
                color: const Color(0xFFFEF2F2),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFFCA5A5)),
              ),
              child: const Row(
                children: [
                  Icon(Icons.cancel, color: Color(0xFFDC2626), size: 24),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Hồ sơ của bạn đã bị từ chối. Vui lòng liên hệ ban quản trị để biết thêm chi tiết hoặc nộp lại hồ sơ.',
                      style: TextStyle(fontSize: 13, color: Color(0xFF991B1B)),
                    ),
                  ),
                ],
              ),
            )
          else ...[
            // Status Header Pill
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: const [
                  BoxShadow(color: Color(0x08000000), blurRadius: 8, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFFBEB),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: const Icon(Icons.hourglass_top_rounded, color: Color(0xFFD97706), size: 20),
                          ),
                          const SizedBox(width: 10),
                          const Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Hồ sơ đang chờ duyệt',
                                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                              ),
                              Text(
                                'Dự kiến phản hồi: 24h - 48h làm việc',
                                style: TextStyle(fontSize: 11, color: AppColors.tertiary),
                              ),
                            ],
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFEF3C7),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          'PENDING',
                          style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Color(0xFFD97706)),
                        ),
                      ),
                    ],
                  ),
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 14),
                    child: Divider(height: 1, color: Color(0xFFF1F5F9)),
                  ),
                  const Text(
                    'Tiến trình thẩm định của Ban Quản Trị',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                  ),
                  const SizedBox(height: 14),

                  // Timeline Steps
                  _buildTimelineItem(
                    step: '1',
                    title: 'Tiếp nhận thông tin đối tác',
                    desc: 'Đã hoàn tất gửi thông tin doanh nghiệp cơ bản',
                    isDone: true,
                    isCurrent: false,
                    isLast: false,
                  ),
                  _buildTimelineItem(
                    step: '2',
                    title: 'Ban Quản Trị thẩm định pháp lý',
                    desc: 'Đang rà soát GPKD, an toàn hàng hải & tài khoản ngân hàng',
                    isDone: false,
                    isCurrent: true,
                    isLast: false,
                  ),
                  _buildTimelineItem(
                    step: '3',
                    title: 'Kích hoạt tài khoản chính thức',
                    desc: 'Mở bán tour, thiết lập lịch chạy và kích hoạt cổng thanh toán',
                    isDone: false,
                    isCurrent: false,
                    isLast: true,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),

            // Button to Supplement Legal Documents
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton.icon(
                onPressed: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const VendorDocumentsScreen()),
                  );
                },
                icon: const Icon(Icons.file_copy_outlined, size: 18),
                label: const Text(
                  'Bổ sung Giấy phép pháp lý & An toàn biển',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.secondary,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ],
        TextButton(
          onPressed: busy ? null : load,
          child: const LocalizedText('Kiểm tra lại'),
        ),
        TextButton(
          onPressed: busy
              ? null
              : () async {
                  try {
                    await AuthSession.instance.logout();
                  } catch (_) {}
                  if (context.mounted)
                    Navigator.of(context).pushAndRemoveUntil(
                      MaterialPageRoute(
                        builder: (_) => const LoginRegisterScreen(),
                      ),
                      (_) => false,
                    );
                },
          child: const LocalizedText('Đăng xuất'),
        ),
      ],
    ),
  );

  Widget _buildTimelineItem({
    required String step,
    required String title,
    required String desc,
    required bool isDone,
    required bool isCurrent,
    required bool isLast,
  }) {
    final Color circleBg = isDone
        ? const Color(0xFF10B981)
        : (isCurrent ? const Color(0xFFD97706) : const Color(0xFFE2E8F0));
    final Color iconColor = (isDone || isCurrent) ? Colors.white : const Color(0xFF94A3B8);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Container(
              width: 26,
              height: 26,
              decoration: BoxDecoration(
                color: circleBg,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: isDone
                  ? const Icon(Icons.check, size: 14, color: Colors.white)
                  : Text(
                      step,
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: iconColor),
                    ),
            ),
            if (!isLast)
              Container(
                width: 2,
                height: 38,
                color: isDone ? const Color(0xFF10B981) : const Color(0xFFE2E8F0),
              ),
          ],
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: (isDone || isCurrent) ? FontWeight.bold : FontWeight.w500,
                  color: isCurrent ? const Color(0xFFD97706) : AppColors.onSurface,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                desc,
                style: const TextStyle(fontSize: 11, color: AppColors.tertiary),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ],
    );
  }
}
