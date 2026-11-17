import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

class CopilotArtifactCard extends StatelessWidget {
  final Map<String, dynamic> artifact;

  const CopilotArtifactCard({super.key, required this.artifact});

  String _formatMoney(num amount) {
    final str = amount.toInt().toString();
    final reg = RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))');
    final formatted = str.replaceAllMapped(reg, (Match m) => '${m[1]}.');
    return '$formatted đ';
  }

  @override
  Widget build(BuildContext context) {
    final type = artifact['type']?.toString() ?? '';
    final data = artifact['data'] is Map ? Map<String, dynamic>.from(artifact['data'] as Map) : <String, dynamic>{};

    if (type == 'VENDOR_KPI_SUMMARY_CARD') {
      return _buildKpiCard(context, data);
    } else if (type == 'VENDOR_REVIEW_COACHING_CARD') {
      return _buildReviewCoachingCard(context, data);
    } else if (type == 'VENDOR_SLOT_ADVISOR_CARD') {
      return _buildSlotAdvisorCard(context, data);
    } else if (type == 'VENDOR_OPTIMIZE_LISTING_CARD') {
      return _buildOptimizeListingCard(context, data);
    }
    return const SizedBox.shrink();
  }

  Widget _buildKpiCard(BuildContext context, Map<String, dynamic> data) {
    final num gmv = (data['gmv'] as num?) ?? 0;
    final num netPayout = (data['netPayout'] as num?) ?? 0;
    final int totalBookings = (data['totalBookings'] as num?)?.toInt() ?? 0;
    final int cancelledBookings = (data['cancelledBookings'] as num?)?.toInt() ?? 0;
    final num cancellationRate = (data['cancellationRate'] as num?) ?? 0;
    final double ratingAvg = (data['ratingAvg'] as num?)?.toDouble() ?? 0.0;
    final int ratingCount = (data['ratingCount'] as num?)?.toInt() ?? 0;
    final String badgeTier = data['badgeTier']?.toString() ?? 'STANDARD';

    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.trending_up_rounded, size: 16, color: Color(0xFF0284C7)),
                  SizedBox(width: 6),
                  Text(
                    'Chỉ số kinh doanh gian hàng',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  'Hạng: $badgeTier',
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF475569),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Grid 2x2
          Row(
            children: [
              Expanded(
                child: _buildMetricTile(
                  label: 'Tổng GMV',
                  value: _formatMoney(gmv),
                  valueColor: AppColors.navyAnchor,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildMetricTile(
                  label: 'Thực thu dự kiến',
                  value: _formatMoney(netPayout),
                  valueColor: const Color(0xFF047857),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: _buildMetricTile(
                  label: 'Đơn đặt chỗ',
                  value: '$totalBookings đơn ($cancelledBookings hủy)',
                  valueColor: AppColors.navyAnchor,
                  subtitle: cancellationRate > 0 ? 'Tỷ lệ hủy: $cancellationRate%' : null,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFF1F5F9)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Đánh giá trung bình',
                        style: TextStyle(fontSize: 10, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
                      ),
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          const Icon(Icons.star_rounded, size: 14, color: Color(0xFFF59E0B)),
                          const SizedBox(width: 3),
                          Text(
                            ratingAvg.toStringAsFixed(1),
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFFB45309),
                            ),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            '($ratingCount lượt)',
                            style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildReviewCoachingCard(BuildContext context, Map<String, dynamic> data) {
    final pillars = data['pillars'] is Map ? (data['pillars'] as Map) : {};
    final actionItems = data['actionItems'] is List ? (data['actionItems'] as List).whereType<Map>().toList() : [];
    final int totalReviews = (data['totalReviewsAnalyzed'] as num?)?.toInt() ?? 0;

    const pillarNameMap = {
      'serviceQuality': 'Chất lượng',
      'staffAttitude': 'Thái độ',
      'safety': 'An toàn',
      'pricing': 'Giá cả',
    };

    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.star_rounded, size: 16, color: Color(0xFFF59E0B)),
                  SizedBox(width: 6),
                  Text(
                    'Phân tích 4 trụ cột đánh giá',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              Text(
                '$totalReviews đánh giá',
                style: const TextStyle(fontSize: 10.5, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
              ),
            ],
          ),
          if (pillars.isNotEmpty) ...[
            const SizedBox(height: 10),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: 6,
              crossAxisSpacing: 8,
              childAspectRatio: 2.8,
              children: pillars.entries.map((e) {
                final key = e.key.toString();
                final displayName = pillarNameMap[key] ?? key;
                num score = 0;
                if (e.value is Map && e.value['scoreOutOf10'] != null) {
                  score = (e.value['scoreOutOf10'] as num);
                } else if (e.value is num) {
                  score = e.value as num;
                }
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFF1F5F9)),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        displayName,
                        style: const TextStyle(fontSize: 11, color: Color(0xFF475569), fontWeight: FontWeight.w500),
                      ),
                      Text(
                        '$score/10',
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ],
          if (actionItems.isNotEmpty) ...[
            const SizedBox(height: 10),
            const Text(
              'Khuyến nghị khắc phục:',
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 6),
            ...actionItems.take(2).map((item) {
              final title = item['title']?.toString() ?? '';
              final rec = item['recommendation']?.toString() ?? '';
              return Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0F9FF),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFBAE6FD)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (title.isNotEmpty)
                      Text(
                        title,
                        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF0369A1)),
                      ),
                    if (rec.isNotEmpty)
                      Text(
                        rec,
                        style: const TextStyle(fontSize: 10.5, color: Color(0xFF475569)),
                      ),
                  ],
                ),
              );
            }),
          ],
        ],
      ),
    );
  }

  Widget _buildSlotAdvisorCard(BuildContext context, Map<String, dynamic> data) {
    final int totalSlots = (data['totalSlotsEvaluated'] as num?)?.toInt() ?? 0;
    final recommendations = data['recommendations'] is List
        ? (data['recommendations'] as List).whereType<Map>().toList()
        : [];

    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.calendar_month_rounded, size: 16, color: Color(0xFF0284C7)),
              const SizedBox(width: 6),
              Text(
                'Tư vấn ca vận hành ($totalSlots ca rà soát)',
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.bold,
                  color: AppColors.navyAnchor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (recommendations.isEmpty)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF0FDF4),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFBBF7D0)),
              ),
              child: Row(
                children: const [
                  Icon(Icons.check_circle_outline_rounded, size: 16, color: Color(0xFF16A34A)),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Tất cả các ca tour đều có tỷ lệ lấp đầy tối ưu và thời tiết an toàn.',
                      style: TextStyle(fontSize: 11, color: Color(0xFF166534), fontWeight: FontWeight.w500),
                    ),
                  ),
                ],
              ),
            )
          else
            ...recommendations.take(3).map((rec) {
              final serviceTitle = rec['serviceTitle']?.toString() ?? 'Ca tour';
              final date = rec['date']?.toString() ?? '';
              final startTime = rec['startTime']?.toString() ?? '';
              final reason = rec['adviceReason']?.toString() ?? '';

              return Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.all(9),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Text(
                            serviceTitle,
                            style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (date.isNotEmpty || startTime.isNotEmpty)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1.5),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF1F5F9),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              '$date $startTime'.trim(),
                              style: const TextStyle(fontSize: 9.5, color: Color(0xFF64748B), fontWeight: FontWeight.w600),
                            ),
                          ),
                      ],
                    ),
                    if (reason.isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Text(
                        reason,
                        style: const TextStyle(fontSize: 11, color: Color(0xFF475569)),
                      ),
                    ],
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildOptimizeListingCard(BuildContext context, Map<String, dynamic> data) {
    final int score = (data['listingQualityScore'] as num?)?.toInt() ?? 0;
    final missing = data['missingItems'] is List
        ? (data['missingItems'] as List).whereType<Map>().toList()
        : [];

    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.auto_awesome_rounded, size: 16, color: Color(0xFF0284C7)),
                  SizedBox(width: 6),
                  Text(
                    'Đánh giá tối ưu bài đăng',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              Text(
                '$score/100 điểm',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF0369A1)),
              ),
            ],
          ),
          if (missing.isNotEmpty) ...[
            const SizedBox(height: 8),
            const Text(
              'Tiêu chí cần bổ sung:',
              style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
            ),
            const SizedBox(height: 6),
            ...missing.take(2).map((item) {
              final desc = item['description']?.toString() ?? '';
              final sugg = item['suggestion']?.toString() ?? '';
              return Container(
                margin: const EdgeInsets.only(bottom: 5),
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFFBEB),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFFDE68A)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.warning_amber_rounded, size: 14, color: Color(0xFFD97706)),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        desc.isNotEmpty && sugg.isNotEmpty ? '$desc: $sugg' : (desc.isNotEmpty ? desc : sugg),
                        style: const TextStyle(fontSize: 10.5, color: Color(0xFF92400E)),
                      ),
                    ),
                  ],
                ),
              );
            }),
          ],
        ],
      ),
    );
  }

  Widget _buildMetricTile({
    required String label,
    required String value,
    required Color valueColor,
    String? subtitle,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFF1F5F9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 10, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.bold,
              color: valueColor,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 1),
            Text(
              subtitle,
              style: const TextStyle(fontSize: 9.5, color: Color(0xFF94A3B8)),
            ),
          ],
        ],
      ),
    );
  }
}
