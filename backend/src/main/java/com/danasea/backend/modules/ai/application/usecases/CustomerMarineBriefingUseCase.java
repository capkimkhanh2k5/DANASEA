package com.danasea.backend.modules.ai.application.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerMarineBriefingUseCase {

    public record ChecklistItem(String category, String itemName, String tip, boolean mandatory) {}

    public record SubOrderBriefing(
            UUID subOrderId,
            UUID serviceId,
            String serviceTitle,
            String meetingLocation,
            LocalDate departureDate,
            LocalTime departureTime,
            String weatherSafetyStatus, // ACCEPTABLE, CAUTION, UNSAFE, UNKNOWN, NOT_REQUIRED
            String marineConditionsSummary,
            List<ChecklistItem> packingChecklist,
            List<String> arrivalAndSafetyGuidelines
    ) {}

    public record MarineBriefingOverview(
            UUID masterOrderId,
            String orderCode,
            UUID customerId,
            int totalActivities,
            List<SubOrderBriefing> activities,
            OffsetDateTime generatedAt
    ) {}

    private final JpaMasterOrderRepository masterOrderRepo;
    private final JpaSubOrderRepository subOrderRepo;
    private final JpaServiceRepository serviceRepo;
    private final JpaServiceSlotRepository slotRepo;
    private final MarineSlotWeather marineWeather;
    private final AiExtendedInsightStorePort insightStore;
    private final ObjectMapper mapper;

    @Transactional
    public MarineBriefingOverview getMarineBriefing(UUID customerUserId, UUID orderId) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return getMarineBriefing(customerUserId, orderId, lang);
    }

    @Transactional
    public MarineBriefingOverview getMarineBriefing(UUID customerUserId, UUID orderId, SupportedLanguage language) {
        MasterOrderJpaEntity order = masterOrderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (!order.getCustomerId().equals(customerUserId)) {
            throw new AccessDeniedException("Order does not belong to the authenticated user");
        }

        List<SubOrderJpaEntity> subOrders = subOrderRepo.findByMasterOrderId(orderId);
        List<SubOrderBriefing> briefings = new ArrayList<>();

        for (SubOrderJpaEntity subOrder : subOrders) {
            SubOrderBriefing briefing = buildBriefingForSubOrder(order.getId(), subOrder, customerUserId, language);
            briefings.add(briefing);
        }

        String orderCode = "DNS-" + order.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return new MarineBriefingOverview(
                order.getId(),
                orderCode,
                customerUserId,
                briefings.size(),
                briefings,
                OffsetDateTime.now()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void preGenerateForTripReminder(UUID masterOrderId, UUID subOrderId, UUID customerId, UUID slotId) {
        var subOrder = subOrderRepo.findById(subOrderId)
                .filter(order -> masterOrderId.equals(order.getMasterOrderId()) && slotId.equals(order.getSlotId()))
                .orElseThrow(() -> new IllegalArgumentException("Departure no longer matches the order"));
        buildBriefingForSubOrder(masterOrderId, subOrder, customerId, SupportedLanguage.VI);
    }

    private SubOrderBriefing buildBriefingForSubOrder(UUID masterOrderId, SubOrderJpaEntity subOrder, UUID customerId, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        ServiceJpaEntity service = subOrder.getServiceId() != null
                ? serviceRepo.findById(subOrder.getServiceId()).orElse(null) : null;
        ServiceSlotJpaEntity slot = subOrder.getSlotId() != null
                ? slotRepo.findById(subOrder.getSlotId()).orElse(null) : null;

        String serviceTitle = service != null && service.getName() != null
                ? service.getName()
                : (isEn ? "Da Nang Marine Experience" : "Trải nghiệm biển Đà Nẵng");
        String meetingLocation = service != null && service.getAddress() != null && !service.getAddress().isBlank()
                ? service.getAddress()
                : (isEn ? "Confirm the meeting point with the operator" : "Xác nhận điểm hẹn với đơn vị tổ chức");

        LocalDate date = slot != null ? slot.getDate() : null;
        LocalTime time = slot != null ? slot.getStartTime() : null;

        var weatherStatus = marineWeather.assess(subOrder.getServiceId(),
                slot == null ? null : new Slot(subOrder.getSlotId(), slot.getDate(), slot.getStartTime(), slot.getEndTime(), 0),
                language);
        String conditionsSummary = switch (weatherStatus) {
            case ACCEPTABLE -> isEn
                    ? "The latest forecast is within the service thresholds. Confirm conditions again before departure."
                    : "Dự báo mới nhất nằm trong ngưỡng của dịch vụ. Hãy xác nhận lại điều kiện trước khi khởi hành.";
            case CAUTION -> isEn
                    ? "The marine forecast is provisional. Confirm current conditions with the operator before departure."
                    : "Dự báo biển còn tạm thời. Hãy xác nhận điều kiện hiện tại với đơn vị tổ chức trước khi khởi hành.";
            case UNSAFE -> isEn
                    ? "The marine forecast exceeds the service safety thresholds. Contact the operator before travelling."
                    : "Dự báo biển vượt ngưỡng an toàn của dịch vụ. Hãy liên hệ đơn vị tổ chức trước khi di chuyển.";
            case NOT_REQUIRED -> isEn
                    ? "This service does not require a slot-specific marine forecast."
                    : "Dịch vụ này không yêu cầu dự báo biển riêng cho ca.";
            case UNKNOWN -> isEn
                    ? "A current marine forecast is unavailable. Confirm conditions with the operator before departure."
                    : "Chưa có dự báo biển hiện hành. Hãy xác nhận điều kiện với đơn vị tổ chức trước khi khởi hành.";
        };

        List<ChecklistItem> checklist = new ArrayList<>();
        if (isEn) {
            checklist.add(new ChecklistItem("ATTIRE", "Swimwear or quick-dry athletic wear", "Avoid heavy denim or thick cotton clothing in the water", true));
            checklist.add(new ChecklistItem("PROTECTION", "Reef-safe sunscreen", "Apply 20 minutes before sun exposure to protect your skin and coral reefs", true));
            checklist.add(new ChecklistItem("GEAR", "Waterproof phone pouch / action camera", "Capture scenic moments safely on the water", false));
            checklist.add(new ChecklistItem("ACCESSORIES", "Sunglasses with strap & wide-brimmed hat", "Shield your eyes from water surface glare", false));
            checklist.add(new ChecklistItem("PERSONAL", "Dry towel & spare change of clothes", "For changing after freshwater shower at the beach", true));
        } else {
            checklist.add(new ChecklistItem("TRANG_PHUC", "Đồ bơi hoặc trang phục thể thao nhanh khô", "Tránh mặc quần jean hoặc đồ vải dày gây nặng khi xuống nước", true));
            checklist.add(new ChecklistItem("BAO_HO", "Kem chống nắng thân thiện rạn san hô (Reef-safe)", "Nên thoa trước khi ra nắng 20 phút để bảo vệ da và hệ sinh thái biển", true));
            checklist.add(new ChecklistItem("THIET_BI", "Túi chống nước điện thoại / máy ảnh", "Giúp bạn tự tin lưu lại những khoảnh khắc đẹp trên mặt biển", false));
            checklist.add(new ChecklistItem("TIEN_ICH", "Kính râm có dây đeo chống rơi & Mũ rộng vành", "Bảo vệ mắt khỏi ánh nắng phản chiếu trên mặt nước", false));
            checklist.add(new ChecklistItem("CA_NHAN", "Khăn tắm khô & Bộ quần áo dự phòng", "Dùng để thay đồ sau khi tắm tráng tại bãi tắm", true));
        }

        String lowerTitle = serviceTitle.toLowerCase(Locale.ROOT);
        if (lowerTitle.contains("lặn") || lowerTitle.contains("cano") || lowerTitle.contains("snorkel") || lowerTitle.contains("speedboat")) {
            checklist.add(isEn
                    ? new ChecklistItem("MEDICAL", "Motion sickness medication", "If needed, follow the label or advice from your pharmacist", false)
                    : new ChecklistItem("Y_TE", "Thuốc chống say sóng", "Nếu cần, hãy làm theo hướng dẫn trên nhãn hoặc tư vấn của dược sĩ", false));
        }

        List<String> guidelines = isEn
                ? List.of(
                        "Arrive at the meeting point 20 - 30 minutes before departure for check-in, life jacket fitting, and safety orientation.",
                        "Wear a certified life jacket at all times while on the speedboat or participating in water activities.",
                        "Strictly follow beach lifeguard flag signals and your instructor's guidance.",
                        "Do not step on, break, or touch natural coral reefs to preserve Son Tra marine life."
                )
                : List.of(
                        "Có mặt tại điểm hẹn trước giờ xuất phát 20 - 30 phút để hoàn tất thủ tục điểm danh, thử áo phao vừa vặn và lắng nghe hướng dẫn an toàn.",
                        "Bắt buộc mặc áo phao đạt chuẩn trong toàn bộ thời gian di chuyển trên cano hoặc hoạt động trên mặt nước.",
                        "Tuân thủ tuyệt đối tín hiệu cờ an toàn của ban cứu hộ bãi biển và sự điều phối của hướng dẫn viên.",
                        "Không giẫm đạp, bẻ hoặc chạm tay vào các rạn san hô tự nhiên để bảo tồn sinh vật biển Sơn Trà."
                );

        SubOrderBriefing briefing = new SubOrderBriefing(
                subOrder.getId(),
                subOrder.getServiceId(),
                serviceTitle,
                meetingLocation,
                date,
                time,
                weatherStatus.name(),
                conditionsSummary,
                checklist,
                guidelines
        );

        if (slot != null) {
            try {
                String json = mapper.writeValueAsString(briefing);
                insightStore.saveMarineBriefing(masterOrderId, subOrder.getId(), customerId,
                        subOrder.getSlotId(), weatherStatus.name(), json);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to serialize marine briefing", e);
            }
        }

        return briefing;
    }
}
