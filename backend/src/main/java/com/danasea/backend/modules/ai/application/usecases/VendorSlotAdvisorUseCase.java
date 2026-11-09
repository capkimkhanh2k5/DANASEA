package com.danasea.backend.modules.ai.application.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AiExtendedInsightStorePort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort.VendorSlotItem;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorSlotAdvisorUseCase {

    public record SlotRecommendation(
            UUID slotId,
            UUID serviceId,
            String serviceTitle,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int capacity,
            int bookedCount,
            double occupancyRate,
            String weatherRiskLevel, // SAFE, MODERATE, HIGH_RISK
            String recommendationType, // EXPAND_CAPACITY, ADD_SLOT, WEATHER_WATCH, PROMOTE_OR_CONSOLIDATE, KEEP_OPTIMAL
            String actionTitle,
            String adviceReason
    ) {}

    public record SlotAdvisorResult(
            UUID vendorId,
            UUID serviceId,
            int totalSlotsEvaluated,
            long highDemandSlotsCount,
            long lowDemandSlotsCount,
            long weatherRiskSlotsCount,
            List<SlotRecommendation> recommendations,
            String analysisMethod,
            OffsetDateTime generatedAt
    ) {}

    private final VendorAiDataPort vendorAiData;
    private final AiExtendedInsightStorePort insightStore;
    private final ObjectMapper mapper;

    public SlotAdvisorResult execute(UUID vendorUserId, UUID serviceId, int horizonDays) {
        SupportedLanguage lang = SupportedLanguage
                .fromTag(LocaleContextHolder.getLocale().toLanguageTag())
                .orElse(SupportedLanguage.VI);
        return execute(vendorUserId, serviceId, horizonDays, lang);
    }

    public SlotAdvisorResult execute(UUID vendorUserId, UUID serviceId, int horizonDays, SupportedLanguage language) {
        boolean isEn = language == SupportedLanguage.EN;
        var profile = vendorAiData.findProfileByUserId(vendorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor profile not found for user: " + vendorUserId));
        UUID vendorId = profile.id();

        int boundedDays = Math.max(1, Math.min(horizonDays <= 0 ? 7 : horizonDays, 14));
        List<VendorSlotItem> slots = vendorAiData.listUpcomingSlots(vendorId, serviceId, boundedDays);

        List<SlotRecommendation> recs = new ArrayList<>();
        long highDemand = 0;
        long lowDemand = 0;
        long weatherRisk = 0;

        for (VendorSlotItem slot : slots) {
            double occupancy = slot.capacity() > 0
                    ? ((double) slot.bookedCount() / slot.capacity()) * 100.0 : 0.0;

            String type;
            String title;
            String advice;
            String weatherLevel = "SAFE";

            boolean isLateAfternoon = slot.startTime().isAfter(LocalTime.of(16, 0));

            if (occupancy >= 85.0) {
                highDemand++;
                type = "EXPAND_CAPACITY";
                title = isEn
                        ? "High demand — Expand capacity or open an extra slot"
                        : "Nhu cầu cao — Nên mở rộng chỗ hoặc mở thêm ca phụ";
                advice = isEn
                        ? String.format("Slot %s on %s has reached %.0f%% occupancy (%d/%d seats). Calm morning waters are ideal for opening an additional slot or expanding capacity.",
                                slot.startTime(), slot.date(), occupancy, slot.bookedCount(), slot.capacity())
                        : String.format("Ca %s ngày %s đã đạt %.0f%% công suất (%d/%d chỗ). Điều kiện biển sáng sớm thường êm dịu, mở thêm ca hoặc tăng công suất giúp tối đa doanh thu.",
                                slot.startTime(), slot.date(), occupancy, slot.bookedCount(), slot.capacity());
            } else if (occupancy < 20.0 && slot.date().isBefore(LocalDate.now().plusDays(3))) {
                lowDemand++;
                type = "PROMOTE_OR_CONSOLIDATE";
                title = isEn
                        ? "Low occupancy — Consider flash promotion or slot consolidation"
                        : "Công suất thấp — Cân nhắc kích cầu hoặc gộp ca";
                advice = isEn
                        ? String.format("Slot %s on %s currently has %d booking(s). Consider running a Flash Sale or consolidating guests into an adjacent slot to optimize boat fuel & crew costs.",
                                slot.startTime(), slot.date(), slot.bookedCount())
                        : String.format("Ca %s ngày %s mới chỉ có %d khách đặt. Cân nhắc bật khuyến mãi Flash Sale hoặc chủ động liên hệ gộp khách sang ca gần kề để tối ưu chi phí thuyền/cano.",
                                slot.startTime(), slot.date(), slot.bookedCount());
            } else if (isLateAfternoon && slot.date().isAfter(LocalDate.now().plusDays(2))) {
                weatherRisk++;
                weatherLevel = "MODERATE";
                type = "WEATHER_WATCH";
                title = isEn
                        ? "Monitor afternoon sea wind & swell conditions"
                        : "Theo dõi thời tiết biển buổi chiều";
                advice = isEn
                        ? "Afternoon coastal waters in Da Nang often experience stronger wind gusts and swells. Monitor marine forecasts and prepare an early-morning reschedule option."
                        : "Vùng biển Đà Nẵng buổi chiều có xu hướng sóng dâng và gió giật mạnh hơn. Hãy theo dõi bản tin khí tượng và chuẩn bị phương án dời sang ca sáng nếu biển động.";
            } else {
                type = "KEEP_OPTIMAL";
                title = isEn ? "Optimal slot occupancy" : "Công suất ổn định";
                advice = isEn
                        ? "Booking pace and seat occupancy are progressing on track."
                        : "Lượng khách đặt và tiến độ lấp đầy đang diễn ra đúng kế hoạch.";
            }

            recs.add(new SlotRecommendation(
                    slot.slotId(), slot.serviceId(), slot.serviceTitle(), slot.date(),
                    slot.startTime(), slot.endTime(), slot.capacity(), slot.bookedCount(),
                    Math.round(occupancy * 10.0) / 10.0, weatherLevel, type, title, advice
            ));
        }

        SlotAdvisorResult result = new SlotAdvisorResult(
                vendorId, serviceId, slots.size(), highDemand, lowDemand, weatherRisk, recs,
                "HYBRID_DUAL_BRAIN", OffsetDateTime.now()
        );

        // Persist to storage
        try {
            String payloadJson = mapper.writeValueAsString(result);
            insightStore.saveVendorInsight(vendorId, serviceId, "SLOT_ADVISOR",
                    vendorId + ":" + (serviceId != null ? serviceId : "all"), payloadJson, "HYBRID_DUAL_BRAIN");
        } catch (Exception e) {
            log.warn("Failed to persist vendor slot advisor insight: {}", e.getMessage());
        }

        return result;
    }
}
