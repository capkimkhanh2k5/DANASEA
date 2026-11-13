package com.danasea.backend.modules.ai.application.tools;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.ai.application.ports.VendorAiDataPort;
import com.danasea.backend.modules.ai.application.usecases.VendorListingOptimizerUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorReviewCoachingUseCase;
import com.danasea.backend.modules.ai.application.usecases.VendorSlotAdvisorUseCase;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VendorFeatureTool implements ToolExecutor {

    private final String name;
    private final VendorAiDataPort vendorAiData;
    private final VendorReviewCoachingUseCase reviewCoaching;
    private final VendorSlotAdvisorUseCase slotAdvisor;
    private final VendorListingOptimizerUseCase listingOptimizer;
    private final ObjectMapper mapper;

    public VendorFeatureTool(
            String name,
            VendorAiDataPort vendorAiData,
            VendorReviewCoachingUseCase reviewCoaching,
            VendorSlotAdvisorUseCase slotAdvisor,
            VendorListingOptimizerUseCase listingOptimizer,
            ObjectMapper mapper
    ) {
        this.name = name;
        this.vendorAiData = vendorAiData;
        this.reviewCoaching = reviewCoaching;
        this.slotAdvisor = slotAdvisor;
        this.listingOptimizer = listingOptimizer;
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String execute(String argumentsJson) {
        return execute(argumentsJson, null);
    }

    @Override
    public String execute(String argumentsJson, ToolExecutionContext context) {
        try {
            UUID userId = context != null ? context.userId() : null;
            if (userId == null) {
                return error("AUTHENTICATION_REQUIRED", "Vendor authentication required for this tool");
            }
            var profileOpt = vendorAiData.findProfileByUserId(userId);
            if (profileOpt.isEmpty()) {
                return error("VENDOR_PROFILE_NOT_FOUND", "No vendor profile associated with the user");
            }
            UUID vendorId = profileOpt.get().id();

            JsonNode args = argumentsJson != null && !argumentsJson.isBlank()
                    ? mapper.readTree(argumentsJson) : mapper.createObjectNode();

            UUID serviceId = args.hasNonNull("serviceId") ? UUID.fromString(args.get("serviceId").asText()) : null;
            SupportedLanguage language = context != null && context.language() != null ? context.language() : SupportedLanguage.VI;

            Object result = switch (name) {
                case "vendor_dashboard_summary", "vendor_revenue_and_bookings" -> {
                    LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
                    LocalDate from = args.hasNonNull("from") ? LocalDate.parse(args.get("from").asText()) : today.minusDays(30);
                    LocalDate to = args.hasNonNull("to") ? LocalDate.parse(args.get("to").asText()) : today;
                    yield vendorAiData.getPerformanceMetrics(vendorId, from, to);
                }
                case "vendor_service_count" -> {
                    var counts = vendorAiData.countServicesByStatus(vendorId);
                    yield Map.of("total", counts.values().stream().mapToLong(Long::longValue).sum(), "byStatus", counts);
                }
                case "vendor_review_coaching" -> reviewCoaching.execute(userId, serviceId, null, null, language);
                case "vendor_slot_advisor" -> slotAdvisor.execute(userId, serviceId, args.path("horizonDays").asInt(7), language);
                case "vendor_optimize_listing" -> {
                    String title = args.path("title").asText("");
                    String desc = args.path("description").asText("");
                    yield listingOptimizer.execute(userId, new VendorListingOptimizerUseCase.OptimizeRequest(
                            serviceId, title, desc, List.of(), List.of(), List.of("VI", "EN")
                    ), language);
                }
                default -> throw new IllegalArgumentException("Unknown vendor tool: " + name);
            };

            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENTS", e.getMessage());
        } catch (Exception e) {
            return error("TOOL_EXECUTION_FAILED", "Failed to execute vendor tool: " + e.getMessage());
        }
    }

    private String error(String code, String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", code, "message", message));
        } catch (Exception e) {
            return "{\"error\":\"FEATURE_UNAVAILABLE\"}";
        }
    }
}
