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
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
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
            String weatherRiskLevel, // SAFE, MODERATE, HIGH_RISK, UNKNOWN, NOT_REQUIRED
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
    private final MarineSlotWeather marineWeather;
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
            var weather = marineWeather.assess(slot.serviceId(),
                    new Slot(slot.slotId(), slot.date(), slot.startTime(), slot.endTime(), 0), language);
            String weatherLevel = switch (weather) {
                case ACCEPTABLE -> "SAFE";
                case CAUTION -> "MODERATE";
                case UNSAFE -> "HIGH_RISK";
                case UNKNOWN -> "UNKNOWN";
                case NOT_REQUIRED -> "NOT_REQUIRED";
            };
            if (occupancy >= 85.0) highDemand++;
            boolean lowOccupancy = occupancy < 20.0 && slot.date().isBefore(LocalDate.now(TimeRange.VIETNAM_ZONE).plusDays(3));
            if (lowOccupancy) lowDemand++;
            if (weather == MarineSlotWeather.Status.CAUTION || weather == MarineSlotWeather.Status.UNSAFE) weatherRisk++;

            if (weather == MarineSlotWeather.Status.UNKNOWN || weather == MarineSlotWeather.Status.CAUTION
                    || weather == MarineSlotWeather.Status.UNSAFE) {
                type = "WEATHER_WATCH";
                title = isEn ? "Confirm marine conditions before changing capacity" : "Xác nhận điều kiện biển trước khi đổi công suất";
                advice = isEn
                        ? "Check the current forecast and service safety limits before opening or expanding this slot."
                        : "Kiểm tra dự báo hiện hành và ngưỡng an toàn của dịch vụ trước khi mở hoặc tăng công suất ca.";
            } else if (occupancy >= 85.0) {
                type = "EXPAND_CAPACITY";
                title = isEn
                        ? "High demand — Expand capacity or open an extra slot"
                        : "Nhu cầu cao — Nên mở rộng chỗ hoặc mở thêm ca phụ";
                advice = isEn
                        ? String.format("Slot %s on %s has reached %.0f%% occupancy (%d/%d seats). Check inventory and operating limits before opening an additional slot.",
                                slot.startTime(), slot.date(), occupancy, slot.bookedCount(), slot.capacity())
                        : String.format("Ca %s ngày %s đã đạt %.0f%% công suất (%d/%d chỗ). Kiểm tra tồn chỗ và giới hạn vận hành trước khi mở thêm ca.",
                                slot.startTime(), slot.date(), occupancy, slot.bookedCount(), slot.capacity());
            } else if (lowOccupancy) {
                type = "PROMOTE_OR_CONSOLIDATE";
                title = isEn
                        ? "Low occupancy — Consider flash promotion or slot consolidation"
                        : "Công suất thấp — Cân nhắc kích cầu hoặc gộp ca";
                advice = isEn
                        ? String.format("Slot %s on %s currently has %d booking(s). Consider running a Flash Sale or consolidating guests into an adjacent slot to optimize boat fuel & crew costs.",
                                slot.startTime(), slot.date(), slot.bookedCount())
                        : String.format("Ca %s ngày %s mới chỉ có %d khách đặt. Cân nhắc bật khuyến mãi Flash Sale hoặc chủ động liên hệ gộp khách sang ca gần kề để tối ưu chi phí thuyền/cano.",
                                slot.startTime(), slot.date(), slot.bookedCount());
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
