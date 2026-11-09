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
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
            String weatherSafetyStatus, // ACCEPTABLE, CAUTION, UNSAFE, UNKNOWN
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
    private final TravelWeatherPort weatherPort;
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

    @Transactional
    public void preGenerateForTripReminder(UUID masterOrderId, UUID subOrderId, UUID customerId, UUID slotId) {
        try {
            var subOrderOpt = subOrderRepo.findById(subOrderId);
            if (subOrderOpt.isEmpty()) return;
            var subOrder = subOrderOpt.get();
            buildBriefingForSubOrder(masterOrderId, subOrder, customerId, SupportedLanguage.VI);
            log.info("Pre-generated 24h marine briefing for customer {} subOrder {}", customerId, subOrderId);
        } catch (Exception e) {
            log.warn("Failed to pre-generate trip reminder marine briefing: {}", e.getMessage());
        }
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
                : (isEn ? "Man Thai / Son Tra Beach Area, Da Nang" : "Khu vực bãi biển Mân Thái / Sơn Trà, Đà Nẵng");

        LocalDate date = slot != null ? slot.getDate() : LocalDate.now().plusDays(1);
        LocalTime time = slot != null ? slot.getStartTime() : LocalTime.of(8, 0);

        String weatherStatus = "ACCEPTABLE";
        String conditionsSummary = isEn
                ? "Favorable Da Nang marine forecast with calm waters (wave height under 0.6m) and good underwater visibility, ideal for water sports and coral snorkeling."
                : "Dự báo thời tiết biển Đà Nẵng thuận lợi, mặt biển êm ả với độ cao sóng dưới 0.6m, tầm nhìn dưới nước tốt, rất thích hợp cho hoạt động thể thao biển và lặn ngắm san hô.";

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
                    ? new ChecklistItem("MEDICAL", "Motion sickness medication", "Take 30-45 minutes before departure if you are prone to seasickness", false)
                    : new ChecklistItem("Y_TE", "Thuốc chống say sóng", "Uống trước giờ khởi hành 30-45 phút nếu bạn dễ bị say sóng", false));
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
                weatherStatus,
                conditionsSummary,
                checklist,
                guidelines
        );

        try {
            String json = mapper.writeValueAsString(briefing);
            insightStore.saveMarineBriefing(masterOrderId, subOrder.getId(), customerId,
                    subOrder.getSlotId() != null ? subOrder.getSlotId() : UUID.randomUUID(),
                    weatherStatus, json);
        } catch (Exception e) {
            log.warn("Failed to persist marine briefing: {}", e.getMessage());
        }

        return briefing;
    }
}
