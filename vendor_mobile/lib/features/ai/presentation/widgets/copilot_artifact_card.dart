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
    } else if (type == 'VENDOR_SLOT_ADVISOR_CARD' || type == 'VENDOR_SLOT_ADVISORY_CARD') {
      return _buildSlotAdvisorCard(context, data);
    } else if (type == 'VENDOR_OPTIMIZE_LISTING_CARD' || type == 'VENDOR_LISTING_DRAFT_CARD') {
      return _buildOptimizeListingCard(context, data);
    } else if (type == 'VENDOR_SERVICE_COUNT_CARD' || artifact['tool'] == 'vendor_service_count') {
      return _buildServiceCountCard(context, data);
    } else if (type == 'VENDOR_SERVICE_DETAIL_CARD' || artifact['tool'] == 'vendor_service_detail') {
      return _buildServiceDetailCard(context, data);
    } else if (type == 'COPILOT_CONTENT_ASSESSMENT_CARD' || artifact['tool'] == 'vendor_text_assessment') {
      return _buildContentAssessmentCard(context, data);
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
      'SAFETY': 'An toàn',
      'PUNCTUALITY': 'Đúng giờ',
      'VALUE': 'Giá trị',
      'GUIDE': 'Hướng dẫn',
      'SERVICE': 'Chất lượng',
      'STAFF': 'Thái độ',
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
            children: [
              const Icon(Icons.star_rounded, size: 16, color: Color(0xFFF59E0B)),
              const SizedBox(width: 6),
              const Expanded(
                child: Text(
                  'Phân tích 4 trụ cột đánh giá',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: AppColors.navyAnchor,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 6),
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
              childAspectRatio: 2.5,
              children: pillars.entries.map((e) {
                final key = e.key.toString();
                final displayName = pillarNameMap[key] ?? pillarNameMap[key.toUpperCase()] ?? key;
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
                    children: [
                      Expanded(
                        child: Text(
                          displayName,
                          style: const TextStyle(fontSize: 11, color: Color(0xFF475569), fontWeight: FontWeight.w500),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 4),
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

  Widget _buildServiceCountCard(BuildContext context, Map<String, dynamic> data) {
    final int total = (data['total'] as num?)?.toInt() ?? 0;
    final byStatus = data['byStatus'] is Map ? Map<String, dynamic>.from(data['byStatus'] as Map) : <String, dynamic>{};

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
                  Icon(Icons.layers_rounded, size: 16, color: Color(0xFF0284C7)),
                  SizedBox(width: 6),
                  Text(
                    'Thống kê dịch vụ đối tác',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '$total dịch vụ',
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF334155),
                  ),
                ),
              ),
            ],
          ),
          if (byStatus.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: byStatus.entries.map((entry) {
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        entry.key,
                        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: Color(0xFF64748B)),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '${entry.value}',
                        style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.navyAnchor),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildContentAssessmentCard(BuildContext context, Map<String, dynamic> data) {
    final status = data['status']?.toString() ?? 'NEEDS_REVIEW';
    final reasonCodes = data['reasonCodes'] is List ? (data['reasonCodes'] as List).map((e) => e.toString()).toList() : <String>[];
    final contactMatches = data['contactMatches'] is List ? (data['contactMatches'] as List).map((e) => e.toString()).toList() : <String>[];
    final bool negatedWarningOnly = data['negatedWarningOnly'] == true;
    final bool needsReview = status == 'NEEDS_REVIEW';

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
                children: [
                  Icon(
                    Icons.security_rounded,
                    size: 16,
                    color: needsReview ? const Color(0xFFD97706) : const Color(0xFF16A34A),
                  ),
                  const SizedBox(width: 6),
                  const Text(
                    'Thẩm định nội dung bài đăng',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.bold,
                      color: AppColors.navyAnchor,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: needsReview ? const Color(0xFFFEF3C7) : const Color(0xFFDCFCE7),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: needsReview ? const Color(0xFFFDE68A) : const Color(0xFFBBF7D0),
                  ),
                ),
                child: Text(
                  status,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: needsReview ? const Color(0xFF92400E) : const Color(0xFF166534),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: [
              if (data['serviceId'] != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    'Dịch vụ: ${data['serviceId']}',
                    style: const TextStyle(fontSize: 10, color: Color(0xFF475569)),
                  ),
                ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text(
                  'Không tự duyệt xuất bản',
                  style: TextStyle(fontSize: 10, color: Color(0xFF475569)),
                ),
              ),
              if (negatedWarningOnly)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F9FF),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: const Color(0xFFBAE6FD)),
                  ),
                  child: const Text(
                    'Chỉ là câu cảnh báo phủ định',
                    style: TextStyle(fontSize: 10, color: Color(0xFF0369A1)),
                  ),
                ),
            ],
          ),
          if (reasonCodes.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: reasonCodes.map((code) => Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFFEF3C7),
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: const Color(0xFFFDE68A)),
                ),
                child: Text(
                  code,
                  style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: Color(0xFF92400E)),
                ),
              )).toList(),
            ),
          ],
          if (contactMatches.isNotEmpty) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF1F2),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: const Color(0xFFFECDD3)),
              ),
              child: Text(
                'Phát hiện thông tin liên hệ / giao dịch ngoài: ${contactMatches.join(', ')}',
                style: const TextStyle(fontSize: 10.5, color: Color(0xFFBE123C), fontWeight: FontWeight.w500),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildServiceDetailCard(BuildContext context, Map<String, dynamic> data) {
    final title = data['title']?.toString() ?? 'Dịch vụ';
    final categoryName = data['categoryName']?.toString() ?? '';
    final status = (data['status']?.toString() ?? 'PUBLISHED').toUpperCase();
    final num? basePrice = data['basePrice'] as num?;
    final int? durationMinutes = (data['durationMinutes'] as num?)?.toInt();
    final int? capacityPerSlot = (data['capacityPerSlot'] as num?)?.toInt();
    final rawOptions = data['options'] is List ? (data['options'] as List) : [];

    final statusText = status == 'PUBLISHED'
        ? 'Đang hoạt động'
        : status == 'PENDING_REVIEW'
            ? 'Chờ duyệt'
            : status == 'DRAFT'
                ? 'Bản nháp'
                : status == 'PAUSED'
                    ? 'Tạm dừng'
                    : status;
    final statusColor = status == 'PUBLISHED'
        ? const Color(0xFF059669)
        : status == 'PENDING_REVIEW'
            ? const Color(0xFFD97706)
            : const Color(0xFF64748B);
    final statusBg = status == 'PUBLISHED'
        ? const Color(0xFFECFDF5)
        : status == 'PENDING_REVIEW'
            ? const Color(0xFFFFFBEB)
            : const Color(0xFFF1F5F9);

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
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 2),
                child: Icon(Icons.sell_outlined, size: 16, color: Color(0xFF0284C7)),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: AppColors.navyAnchor,
                      ),
                    ),
                    if (categoryName.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Text(
                        categoryName,
                        style: const TextStyle(fontSize: 10.5, color: Color(0xFF64748B)),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                decoration: BoxDecoration(
                  color: statusBg,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  statusText,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: statusColor,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Overview row: base price & operational details
          Row(
            children: [
              if (basePrice != null && basePrice > 0)
                Expanded(
                  child: _buildMetricTile(
                    label: 'Giá niêm yết',
                    value: _formatMoney(basePrice),
                    valueColor: const Color(0xFF0369A1),
                  ),
                ),
              if (basePrice != null && basePrice > 0 && (durationMinutes != null || capacityPerSlot != null))
                const SizedBox(width: 8),
              if (durationMinutes != null || capacityPerSlot != null)
                Expanded(
                  child: _buildMetricTile(
                    label: 'Vận hành',
                    value: [
                      if (durationMinutes != null) '$durationMinutes phút',
                      if (capacityPerSlot != null) '$capacityPerSlot khách/ca',
                    ].join(' • '),
                    valueColor: AppColors.navyAnchor,
                  ),
                ),
            ],
          ),

          // Options list
          if (rawOptions.isNotEmpty) ...[
            const SizedBox(height: 10),
            const Text(
              'Các gói giá đang áp dụng:',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: Color(0xFF475569),
              ),
            ),
            const SizedBox(height: 6),
            ...rawOptions.map((optRaw) {
              final opt = optRaw is Map ? Map<String, dynamic>.from(optRaw) : <String, dynamic>{};
              final optName = opt['name']?.toString() ?? 'Gói';
              final num optPrice = (opt['price'] as num?) ?? 0;
              final unit = (opt['pricingUnit']?.toString() ?? 'PER_PERSON').toUpperCase();
              final unitLabel = unit == 'PER_PERSON'
                  ? 'người'
                  : unit == 'PER_PACKAGE'
                      ? 'gói'
                      : unit == 'PER_GROUP'
                          ? 'nhóm'
                          : unit == 'PER_HOUR'
                              ? 'giờ'
                              : unit == 'PER_DAY'
                                  ? 'ngày'
                                  : unit;
              final maxPax = (opt['maxPaxPerPackage'] as num?)?.toInt();

              return Container(
                margin: const EdgeInsets.only(bottom: 5),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFF1F5F9)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            optName,
                            style: const TextStyle(
                              fontSize: 11.5,
                              fontWeight: FontWeight.w600,
                              color: AppColors.navyAnchor,
                            ),
                          ),
                          if (maxPax != null && maxPax > 0)
                            Text(
                              'Tối đa $maxPax khách/gói',
                              style: const TextStyle(fontSize: 9.5, color: Color(0xFF64748B)),
                            ),
                        ],
                      ),
                    ),
                    Text(
                      '${_formatMoney(optPrice)} / $unitLabel',
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0284C7),
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
