import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/status_badge.dart';
import '../../../../core/models/vendor_models.dart';
import '../../../../core/data/vendor_mock_database.dart';
import '../../../profile/presentation/screens/edit_profile_bank_screen.dart';
import 'payout_detail_screen.dart';

class SettlementRevenueScreen extends StatefulWidget {
  const SettlementRevenueScreen({super.key});

  @override
  State<SettlementRevenueScreen> createState() => _SettlementRevenueScreenState();
}

class _SettlementRevenueScreenState extends State<SettlementRevenueScreen> {
  final VendorMockDatabase _db = VendorMockDatabase.instance;
  String _filterStatus = 'all'; // all, pending, paid
  String _selectedMonth = 'all'; // all, 2024-10, 2024-09
  bool _isBalanceVisible = true;

  @override
  void initState() {
    super.initState();
    _db.addListener(_onDbChanged);
  }

  @override
  void dispose() {
    _db.removeListener(_onDbChanged);
    super.dispose();
  }

  void _onDbChanged() {
    if (mounted) setState(() {});
  }

  String _formatVnd(double amount) {
    final intVal = amount.toInt();
    final str = intVal.toString();
    final buffer = StringBuffer();
    int count = 0;
    for (int i = str.length - 1; i >= 0; i--) {
      buffer.write(str[i]);
      count++;
      if (count % 3 == 0 && i != 0) {
        buffer.write('.');
      }
    }
    return '${buffer.toString().split('').reversed.join()} đ';
  }

  void _openPolicyBottomSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) => Container(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 16,
          bottom: MediaQuery.of(ctx).padding.bottom + 20,
        ),
        decoration: const BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: const Color(0xFFCBD5E1),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Row(
              children: const [
                Icon(Icons.info_outline, color: AppColors.secondary, size: 22),
                SizedBox(width: 8),
                Text(
                  'Chính sách thanh toán & Đối soát',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppColors.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _buildPolicyItem(
              icon: Icons.calendar_month_outlined,
              title: 'Chu kỳ đối soát định kỳ',
              desc:
                  'Hệ thống DANASEA tự động chốt đối soát vào ngày 15 và ngày cuối cùng của mỗi tháng.',
            ),
            const SizedBox(height: 12),
            _buildPolicyItem(
              icon: Icons.flash_on_outlined,
              title: 'Thời gian chuyển tiền',
              desc:
                  'Tiền thực nhận được chuyển khoản tự động vào số tài khoản ngân hàng thụ hưởng của đối tác trong vòng 24h - 48h làm việc.',
            ),
            const SizedBox(height: 12),
            _buildPolicyItem(
              icon: Icons.pie_chart_outline_rounded,
              title: 'Phí nền tảng cố định 10%',
              desc:
                  'Chỉ thu 10% trên các tour và ca hoạt động hoàn tất thành công. Không thu phí duy trì, không phí ẩn.',
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 46,
              child: ElevatedButton(
                onPressed: () => Navigator.pop(ctx),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.secondary,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: const Text('Đã hiểu', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPolicyItem({
    required IconData icon,
    required String title,
    required String desc,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppColors.secondaryContainer.withAlpha(80),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: AppColors.secondary, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: AppColors.onSurface,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                desc,
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.onSurfaceVariant,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final settlements = _db.settlements;
    final vendor = _db.currentVendor;
    final bankAcc = vendor.bankAccountNumber ?? '';
    final bankLast4 = bankAcc.length > 4 ? bankAcc.substring(bankAcc.length - 4) : bankAcc;

    final paidSettlements =
        settlements.where((s) => s.status == SettlementStatus.paid).toList();
    final pendingSettlements =
        settlements.where((s) => s.status == SettlementStatus.pending).toList();

    // Calculate totals
    final double totalPaid = paidSettlements.fold(0.0, (acc, s) => acc + s.netPayableAmount);
    final double totalPending = pendingSettlements.fold(0.0, (acc, s) => acc + s.netPayableAmount);
    final double totalAvailableWallet = totalPaid;

    // Filter by status tab
    List<SettlementModel> filtered = settlements;
    if (_filterStatus == 'pending') {
      filtered = pendingSettlements;
    } else if (_filterStatus == 'paid') {
      filtered = paidSettlements;
    }

    // Filter by month
    if (_selectedMonth == '2024-10') {
      filtered = filtered
          .where((s) => s.periodStart.month == 10 && s.periodStart.year == 2024)
          .toList();
    } else if (_selectedMonth == '2024-09') {
      filtered = filtered
          .where((s) => s.periodStart.month == 9 && s.periodStart.year == 2024)
          .toList();
    }

    final double filteredGross = filtered.fold(0.0, (acc, s) => acc + s.grossAmount);
    final double filteredCommission = filtered.fold(0.0, (acc, s) => acc + s.commissionAmount);
    final double filteredNet = filtered.fold(0.0, (acc, s) => acc + s.netPayableAmount);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7F9),
      appBar: AppBar(
        title: const Text(
          'Doanh thu của tôi',
          style: TextStyle(
            color: AppColors.onSurface,
            fontWeight: FontWeight.bold,
            fontSize: 18,
          ),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 1,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () {
            FocusManager.instance.primaryFocus?.unfocus();
            Navigator.maybePop(context);
          },
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.help_outline_rounded, color: AppColors.onSurfaceVariant),
            tooltip: 'Chính sách thanh toán',
            onPressed: _openPolicyBottomSheet,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await _db.syncWithApi();
        },
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. SHOPEE SELLER INCOME OVERVIEW CARD
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(18),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x0A000000),
                      blurRadius: 10,
                      offset: Offset(0, 3),
                    ),
                  ],
                  border: Border.all(color: const Color(0xFFECEFF1)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Wallet label & Eye toggle
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(6),
                              decoration: BoxDecoration(
                                color: AppColors.primary.withAlpha(20),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: const Icon(
                                Icons.account_balance_wallet_rounded,
                                color: AppColors.primary,
                                size: 18,
                              ),
                            ),
                            const SizedBox(width: 8),
                            const Text(
                              'Số dư Ví Người Bán',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.bold,
                                color: AppColors.onSurface,
                              ),
                            ),
                            const SizedBox(width: 6),
                            InkWell(
                              onTap: () {
                                setState(() {
                                  _isBalanceVisible = !_isBalanceVisible;
                                });
                              },
                              borderRadius: BorderRadius.circular(20),
                              child: Padding(
                                padding: const EdgeInsets.all(4),
                                child: Icon(
                                  _isBalanceVisible
                                      ? Icons.visibility_outlined
                                      : Icons.visibility_off_outlined,
                                  size: 18,
                                  color: AppColors.tertiary,
                                ),
                              ),
                            ),
                          ],
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: const Color(0xFFF1F5F9),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Text(
                            'Kỳ 15 & cuối tháng',
                            style: TextStyle(
                              fontSize: 10.5,
                              color: AppColors.tertiary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),

                    // Primary Net Amount
                    Text(
                      _isBalanceVisible ? _formatVnd(totalAvailableWallet) : '••••••••',
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                        color: AppColors.onSurface,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 2),
                    const Text(
                      'Tổng thu nhập đã quyết toán về tài khoản',
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.tertiary,
                      ),
                    ),
                    const SizedBox(height: 14),

                    // 2-Column Finance Metric Box (Shopee Seller Style)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF8FAFC),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: const [
                                    Icon(Icons.check_circle_rounded,
                                        size: 14, color: Color(0xFF10B981)),
                                    SizedBox(width: 4),
                                    Text(
                                      'Đã thanh toán',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                        color: Color(0xFF64748B),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  _isBalanceVisible ? _formatVnd(totalPaid) : '••••••••',
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xFF0F172A),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Container(
                            width: 1,
                            height: 32,
                            color: const Color(0xFFCBD5E1),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: const [
                                    Icon(Icons.schedule_rounded,
                                        size: 14, color: Color(0xFFF59E0B)),
                                    SizedBox(width: 4),
                                    Text(
                                      'Chờ quyết toán',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                        color: Color(0xFF64748B),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  _isBalanceVisible ? _formatVnd(totalPending) : '••••••••',
                                  style: const TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xFFD97706),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),

                    // Default Linked Bank Account Pill
                    InkWell(
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const EditProfileBankScreen()),
                        );
                      },
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                        decoration: BoxDecoration(
                          color: AppColors.secondary.withAlpha(12),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.account_balance_rounded,
                                size: 16, color: AppColors.secondary),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '${vendor.bankName ?? 'Ngân hàng'} ••••$bankLast4 (${vendor.bankAccountHolder ?? ''})',
                                style: const TextStyle(
                                  fontSize: 11.5,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.secondary,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const Text(
                              'Đổi STK',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppColors.secondary,
                                decoration: TextDecoration.underline,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),

                    // Quick Action Buttons
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const PayoutDetailScreen(),
                                ),
                              );
                            },
                            icon: const Icon(Icons.arrow_outward_rounded, size: 16),
                            label: const Text(
                              'Yêu cầu nhận tiền',
                              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold),
                            ),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.primary,
                              foregroundColor: Colors.white,
                              elevation: 0,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              padding: const EdgeInsets.symmetric(vertical: 10),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const PayoutDetailScreen(),
                                ),
                              );
                            },
                            icon: const Icon(Icons.receipt_long_rounded, size: 16),
                            label: const Text(
                              'Chi tiết sao kê',
                              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold),
                            ),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppColors.secondary,
                              side: const BorderSide(color: AppColors.secondary),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              padding: const EdgeInsets.symmetric(vertical: 10),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),

              // 2. SHOPEE SELLER STATUS TABS
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFECEFF1)),
                ),
                child: Row(
                  children: [
                    _buildStatusTab('all', 'Tất cả (${settlements.length})'),
                    _buildStatusTab('pending', 'Chưa thanh toán (${pendingSettlements.length})'),
                    _buildStatusTab('paid', 'Đã thanh toán (${paidSettlements.length})'),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // 3. MONTH FILTER CHIPS
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _buildMonthChip('all', 'Tất cả các kỳ'),
                    const SizedBox(width: 8),
                    _buildMonthChip('2024-10', 'Tháng 10/2024'),
                    const SizedBox(width: 8),
                    _buildMonthChip('2024-09', 'Tháng 09/2024'),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // 4. TRANSPARENT FINANCIAL FORMULA BREAKDOWN
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: const Color(0xFFECEFF1)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Tổng hợp dòng tiền mục đang chọn',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        Text(
                          '${filtered.length} kỳ đối soát',
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppColors.tertiary,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 16, color: Color(0xFFF1F5F9)),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Tổng doanh số dịch vụ (Gross):',
                          style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                        ),
                        Text(
                          _formatVnd(filteredGross),
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Phí nền tảng DANASEA (~10%):',
                          style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                        ),
                        Text(
                          '-${_formatVnd(filteredCommission)}',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: AppColors.primary,
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 16, color: Color(0xFFF1F5F9)),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Thực nhận về tài khoản (Net):',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AppColors.onSurface,
                          ),
                        ),
                        Text(
                          _formatVnd(filteredNet),
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF059669),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // 5. SETTLEMENT STATEMENTS LIST
              if (filtered.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 40),
                  alignment: Alignment.center,
                  child: Column(
                    children: const [
                      Icon(Icons.inbox_outlined, size: 48, color: AppColors.tertiaryFixedDim),
                      SizedBox(height: 10),
                      Text(
                        'Không có kỳ đối soát nào trong mục này',
                        style: TextStyle(fontSize: 13, color: AppColors.tertiary),
                      ),
                    ],
                  ),
                )
              else
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: filtered.length,
                  separatorBuilder: (ctx, idx) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final item = filtered[index];
                    return _buildShopeeSettlementCard(item, vendor);
                  },
                ),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusTab(String id, String label) {
    final isSelected = _filterStatus == id;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _filterStatus = id),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: isSelected ? AppColors.secondary.withAlpha(20) : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
          ),
          alignment: Alignment.center,
          child: Text(
            label,
            style: TextStyle(
              fontSize: 11.5,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
              color: isSelected ? AppColors.secondary : AppColors.onSurfaceVariant,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ),
    );
  }

  Widget _buildMonthChip(String id, String label) {
    final isSelected = _selectedMonth == id;
    return GestureDetector(
      onTap: () => setState(() => _selectedMonth = id),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.secondary : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? AppColors.secondary : const Color(0xFFE2E8F0),
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
            color: isSelected ? Colors.white : AppColors.onSurfaceVariant,
          ),
        ),
      ),
    );
  }

  Widget _buildShopeeSettlementCard(SettlementModel item, VendorModel vendor) {
    final isPending = item.status == SettlementStatus.pending;
    final bankAcc = vendor.bankAccountNumber ?? '';
    final bankLast4 = bankAcc.length > 4 ? bankAcc.substring(bankAcc.length - 4) : bankAcc;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (ctx) => PayoutDetailScreen(initialSettlementId: item.id),
            ),
          );
        },
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFECEFF1)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x06000000),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header: Statement ID & Status Badge
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF1F5F9),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(
                          Icons.receipt_rounded,
                          size: 16,
                          color: AppColors.secondary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.id,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: AppColors.onSurface,
                            ),
                          ),
                          Text(
                            'Kỳ: ${item.periodStart.day}/${item.periodStart.month} - ${item.periodEnd.day}/${item.periodEnd.month}/${item.periodEnd.year} • ${item.orderCount} đơn',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.tertiary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  StatusBadge(status: item.status.toBadgeStatus()),
                ],
              ),
              const SizedBox(height: 12),

              // Gross and Commission small numbers
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Doanh thu: ${_formatVnd(item.grossAmount)}',
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                    Text(
                      'Phí sàn (10%): -${_formatVnd(item.commissionAmount)}',
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),

              // Net Payout Highlight Row
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text(
                    isPending ? 'Thực nhận dự kiến:' : 'Thực nhận về tài khoản:',
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurface,
                    ),
                  ),
                  Text(
                    _formatVnd(item.netPayableAmount),
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                      color: isPending ? const Color(0xFFD97706) : const Color(0xFF059669),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),

              // Payout destination note
              Row(
                children: [
                  Icon(
                    isPending ? Icons.schedule_rounded : Icons.check_circle_outline_rounded,
                    size: 14,
                    color: isPending ? const Color(0xFFD97706) : const Color(0xFF10B981),
                  ),
                  const SizedBox(width: 5),
                  Expanded(
                    child: Text(
                      isPending
                          ? 'Dự kiến đối soát và giải ngân ngày ${item.periodEnd.day}/${item.periodEnd.month + 1}'
                          : 'Đã thanh toán vào ${vendor.bankName ?? 'Ngân hàng'} (••••$bankLast4)',
                      style: TextStyle(
                        fontSize: 11,
                        color: isPending ? const Color(0xFFB45309) : const Color(0xFF059669),
                        fontWeight: FontWeight.w500,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const Icon(Icons.chevron_right, size: 16, color: Color(0xFF94A3B8)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
