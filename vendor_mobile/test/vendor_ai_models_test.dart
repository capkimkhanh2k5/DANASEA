import 'package:flutter_test/flutter_test.dart';
import 'package:vendor_mobile/core/models/vendor_ai_models.dart';

void main() {
  test('missing AI results cannot become scores, drafts or slot counts', () {
    expect(() => SlotAdvisorResultModel.fromJson({}), throwsA(isA<TypeError>()));
    expect(() => ListingOptimizeResultModel.fromJson({}), throwsA(isA<TypeError>()));
    expect(() => ReplyDraftResultModel.fromJson({}), throwsFormatException);
    final empty = ReviewCoachingResultModel.fromJson({'totalReviewsAnalyzed': 0});
    expect(empty.totalReviewsAnalyzed, 0);
    expect(empty.averageRating, isNull);
  });

  test('48 slots with the same advice render one recommendation', () {
    final slots = List.generate(
      48,
      (i) => {
        'serviceTitle': 'Mô tô nước',
        'date': i < 24 ? '2026-11-16' : '2026-11-17',
        'startTime': '${(i % 24).toString().padLeft(2, '0')}:00',
        'adviceReason': 'Kiểm tra dự báo',
      },
    );
    final message = CopilotMessageModel.fromJson({
      'artifacts': [
        {
          'data': {
            'recommendations': [
              ...slots,
              slots.first,
              {...slots.first, 'adviceReason': 'Giảm công suất'},
            ],
          },
        },
      ],
    });
    expect(message.details, [
      '48 khung giờ · Mô tô nước · Kiểm tra dự báo',
      '2026-11-16 00:00 · Mô tô nước · Giảm công suất',
    ]);
  });

  test('uses server weather risk even when a legacy safe value is present', () {
    final rec = SlotRecommendationModel.fromJson({
      'weatherRiskLevel': 'HIGH_RISK',
      'marineSafetyLevel': 'SAFE',
    });
    expect(rec.marineSafetyLevel, 'HIGH_RISK');
    expect(SlotRecommendationModel.fromJson({}).marineSafetyLevel, 'UNKNOWN');
    expect(
      SlotRecommendationModel.fromJson({
        'marineSafetyLevel': 'CAUTION',
      }).marineSafetyLevel,
      'CAUTION',
    );
  });

  test('keeps recommendations with the corresponding Copilot message', () {
    final message = CopilotMessageModel.fromJson({
      'message': 'Xem chi tiết bên dưới.',
      'artifacts': [
        {
          'type': 'VENDOR_REVIEW_COACHING_CARD',
          'data': {
            'totalReviewsAnalyzed': 5,
            'actionItems': [
              {
                'title': 'Đúng giờ',
                'recommendation': 'Nhắc khách trước giờ đón.',
              },
            ],
          },
        },
      ],
    });
    expect(message.details, [
      'Số đánh giá: 5',
      'Đúng giờ · Nhắc khách trước giờ đón.',
    ]);
    expect(
      CopilotMessageModel.fromJson({'message': 'Xin chào'}).details,
      isEmpty,
    );

    final contextMsg = CopilotMessageModel.fromJson({
      'role': 'USER',
      'message': 'Dịch vụ này có những ca nào?',
      'contextType': 'SERVICE',
      'contextId': '088d874c-a61e-5b58-a7a9-497f32a56901',
      'contextLabel': 'Mô tô nước – Danabeach',
    });
    expect(contextMsg.contextType, 'SERVICE');
    expect(contextMsg.contextId, '088d874c-a61e-5b58-a7a9-497f32a56901');
    expect(contextMsg.contextLabel, 'Mô tô nước – Danabeach');
    expect(contextMsg.content, 'Dịch vụ này có những ca nào?');
  });
}
