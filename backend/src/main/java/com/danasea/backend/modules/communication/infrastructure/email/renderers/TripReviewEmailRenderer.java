package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripReviewEmailRenderer {

    private final JpaSubOrderRepository subOrderRepository;
    private final JpaServiceRepository serviceRepository;
    private final JpaUserRepository userRepository;
    private final JpaReviewRepository reviewRepository;
    private final JpaDisputeRepository disputeRepository;
    private final TransactionalEmailProperties properties;
    private final ObjectMapper objectMapper;

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.TRIP_REVIEW_INVITATION;
    }

    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        Map<String, Object> payload = parsePayload(entity.getPayload());
        UUID subOrderId = UUID.fromString((String) payload.get("subOrderId"));

        // 1. Kiểm tra tồn tại SubOrder
        SubOrderJpaEntity subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new IllegalStateException("SubOrder not found: " + subOrderId));

        // 2. Nếu đơn bị huỷ hoặc hoàn toàn bộ -> Không mời đánh giá
        if (subOrder.getStatus() == SubOrderStatus.CANCELLED || subOrder.getStatus() == SubOrderStatus.REFUNDED) {
            throw new SupersededEmailException("SUB_ORDER_CANCELLED",
                    "Sub-order " + subOrderId + " was cancelled or refunded. Skipping review invitation.");
        }

        // 3. Nếu khách hàng đã gửi đánh giá -> Bỏ qua ALREADY_REVIEWED
        if (reviewRepository.existsBySubOrderId(subOrderId)) {
            throw new SupersededEmailException("ALREADY_REVIEWED",
                    "Customer has already submitted a review for sub-order: " + subOrderId);
        }

        // 4. Nếu đơn đang có khiếu nại/tranh chấp mở -> Bỏ qua OPEN_DISPUTE
        List<DisputeJpaEntity> disputes = disputeRepository.findBySubOrderId(subOrderId);
        boolean hasOpenDispute = disputes.stream().anyMatch(d -> d.getStatus() != null && d.getStatus().isActive());
        if (hasOpenDispute) {
            throw new SupersededEmailException("OPEN_DISPUTE",
                    "Sub-order " + subOrderId + " has an active dispute awaiting resolution. Skipping review invitation.");
        }

        // 5. Lấy thông tin dịch vụ
        ServiceJpaEntity service = serviceRepository.findById(subOrder.getServiceId()).orElse(null);
        String serviceName = (service != null) ? service.getName() : "Dịch vụ Danasea";
        String serviceNameEn = (service != null && service.getNameEn() != null) ? service.getNameEn() : serviceName;

        boolean isEn = "en".equalsIgnoreCase(recipient.locale());
        String displayServiceName = isEn ? serviceNameEn : serviceName;

        String customerName = "Quý khách";
        if (entity.getRecipientKey().startsWith("USER:")) {
            try {
                UUID userId = UUID.fromString(entity.getRecipientKey().substring(5));
                customerName = userRepository.findById(userId)
                        .map(UserJpaEntity::getFullName)
                        .orElse(isEn ? "Valued Customer" : "Quý khách");
            } catch (Exception ignored) {
            }
        }

        String reviewUrl = properties.getPublicBaseUrl() + "/profile/orders?orderId="
                + subOrder.getMasterOrderId() + "&subOrderId=" + subOrderId + "&action=review";

        String subject = isEn
                ? "[DANASEA] How was your trip? Share your review with us"
                : "[DANASEA] Bạn thấy chuyến đi thế nào? Hãy chia sẻ đánh giá cùng Danasea nhé";

        String textBody = isEn
                ? buildEnglishText(customerName, displayServiceName, reviewUrl)
                : buildVietnameseText(customerName, displayServiceName, reviewUrl);

        String htmlBody = isEn
                ? buildEnglishHtml(customerName, displayServiceName, reviewUrl)
                : buildVietnameseHtml(customerName, displayServiceName, reviewUrl);

        return new RenderedEmail(
                recipient.email(),
                subject,
                textBody,
                htmlBody
        );
    }

    private Map<String, Object> parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse transactional email payload JSON: {}", raw, e);
            return Map.of();
        }
    }

    private String buildVietnameseText(String name, String serviceName, String reviewUrl) {
        return "Xin chào " + name + ",\n\n"
                + "Cảm ơn bạn đã đồng hành và trải nghiệm dịch vụ \"" + serviceName + "\" cùng DANASEA!\n\n"
                + "Chuyến đi của bạn diễn ra như thế nào? Sự hài lòng và những đóng góp chân thành từ bạn chính là nguồn động lực to lớn giúp DANASEA và các đối tác không ngừng nâng cao chất lượng dịch vụ du lịch biển Đà Nẵng.\n\n"
                + "👉 Hãy dành 1 phút chia sẻ cảm nhận và đánh giá chuyến đi tại đây:\n"
                + reviewUrl + "\n\n"
                + "Nếu bạn cần bất kỳ hỗ trợ nào sau chuyến đi, đội ngũ Chăm sóc khách hàng DANASEA luôn sẵn sàng hỗ trợ qua hotline "
                + properties.getSupportPhone() + " hoặc email cskh@danasea.vn.\n\n"
                + "Trân trọng,\nĐội ngũ DANASEA";
    }

    private String buildEnglishText(String name, String serviceName, String reviewUrl) {
        return "Dear " + name + ",\n\n"
                + "Thank you for choosing and experiencing \"" + serviceName + "\" with DANASEA!\n\n"
                + "How was your experience? Your feedback is incredibly valuable in helping us and our partners continuously improve the coastal tourism experiences in Da Nang.\n\n"
                + "👉 Please take a minute to share your thoughts and rate your trip here:\n"
                + reviewUrl + "\n\n"
                + "If you need any post-trip assistance, our Customer Support team is always ready to assist via hotline "
                + properties.getSupportPhone() + " or email cskh@danasea.vn.\n\n"
                + "Best regards,\nThe DANASEA Team";
    }

    private String buildVietnameseHtml(String name, String serviceName, String reviewUrl) {
        return "<div style=\"font-family: sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;\">"
                + "<div style=\"margin-bottom: 20px;\"><h2 style=\"color: #0284c7; margin: 0;\">DANASEA</h2></div>"
                + "<p>Xin chào <strong>" + name + "</strong>,</p>"
                + "<p>Cảm ơn bạn đã đồng hành và trải nghiệm dịch vụ <strong>" + serviceName + "</strong> cùng DANASEA!</p>"
                + "<p>Chuyến đi của bạn diễn ra như thế nào? Sự hài lòng và những chia sẻ quý báu từ bạn sẽ giúp đối tác hoàn thiện hơn và giúp những du khách tiếp theo có lựa chọn tuyệt vời nhất.</p>"
                + "<div style=\"text-align: center; margin: 30px 0;\">"
                + "<a href=\"" + reviewUrl + "\" style=\"background-color: #0284c7; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;\">⭐ Đánh giá chuyến đi ngay</a>"
                + "</div>"
                + "<p style=\"font-size: 13px; color: #64748b;\">Nếu nút bấm không hoạt động, bạn có thể sao chép và dán liên kết sau vào trình duyệt:<br><a href=\"" + reviewUrl + "\" style=\"color: #0284c7;\">" + reviewUrl + "</a></p>"
                + "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;\">"
                + "<p style=\"font-size: 13px; color: #64748b; margin: 0;\">Hotline hỗ trợ: " + properties.getSupportPhone() + " | Email: cskh@danasea.vn</p>"
                + "</div>";
    }

    private String buildEnglishHtml(String name, String serviceName, String reviewUrl) {
        return "<div style=\"font-family: sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;\">"
                + "<div style=\"margin-bottom: 20px;\"><h2 style=\"color: #0284c7; margin: 0;\">DANASEA</h2></div>"
                + "<p>Dear <strong>" + name + "</strong>,</p>"
                + "<p>Thank you for experiencing <strong>" + serviceName + "</strong> with DANASEA!</p>"
                + "<p>How was your trip? Your feedback helps our partners improve and guides future travelers in Da Nang.</p>"
                + "<div style=\"text-align: center; margin: 30px 0;\">"
                + "<a href=\"" + reviewUrl + "\" style=\"background-color: #0284c7; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;\">⭐ Leave a Review</a>"
                + "</div>"
                + "<p style=\"font-size: 13px; color: #64748b;\">If the button doesn't work, copy and paste this link into your browser:<br><a href=\"" + reviewUrl + "\" style=\"color: #0284c7;\">" + reviewUrl + "</a></p>"
                + "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;\">"
                + "<p style=\"font-size: 13px; color: #64748b; margin: 0;\">Support: " + properties.getSupportPhone() + " | Email: cskh@danasea.vn</p>"
                + "</div>";
    }
}
