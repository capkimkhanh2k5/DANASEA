package com.danasea.backend.modules.ai.application.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AdminFeatureTool implements ToolExecutor {

    private final String name;
    private final AdminAiDataPort adminAiData;
    private final AdminDisputeSummaryUseCase disputeSummaryUseCase;
    private final ObjectMapper mapper;

    public AdminFeatureTool(
            String name,
            AdminAiDataPort adminAiData,
            AdminDisputeSummaryUseCase disputeSummaryUseCase,
            ObjectMapper mapper
    ) {
        this.name = name;
        this.adminAiData = adminAiData;
        this.disputeSummaryUseCase = disputeSummaryUseCase;
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
            UUID adminUserId = context != null ? context.userId() : null;
            if (adminUserId == null) {
                return error("AUTHENTICATION_REQUIRED", "Admin authentication required for this tool");
            }
            JsonNode args = argumentsJson != null && !argumentsJson.isBlank()
                    ? mapper.readTree(argumentsJson) : mapper.createObjectNode();

            LocalDate from = args.hasNonNull("from") ? LocalDate.parse(args.get("from").asText()) : LocalDate.now().minusDays(29);
            LocalDate to = args.hasNonNull("to") ? LocalDate.parse(args.get("to").asText()) : LocalDate.now();
            String groupBy = args.path("groupBy").asText("day");

            Object result = switch (name) {
                case "admin_platform_dashboard" -> adminAiData.getPlatformDashboardMetrics();
                case "admin_revenue_timeseries" -> adminAiData.getRevenueTimeSeries(from, to, groupBy);
                case "admin_booking_cancellation_breakdown" -> adminAiData.getBookingCancellationBreakdown(from, to, groupBy);
                case "admin_vendor_diagnostics" -> {
                    UUID vendorId = args.hasNonNull("vendorId") ? UUID.fromString(args.get("vendorId").asText()) : null;
                    List<UUID> vendorIds = null;
                    if (args.has("vendorIds") && args.get("vendorIds").isArray()) {
                        vendorIds = new ArrayList<>();
                        for (JsonNode idNode : args.get("vendorIds")) {
                            vendorIds.add(UUID.fromString(idNode.asText()));
                        }
                    }
                    Integer limit = args.hasNonNull("limit") ? args.get("limit").asInt() : null;
                    String sortOrder = args.path("sortOrder").asText("desc");

                    List<AdminAiDataPort.VendorDiagnosticItem> list = adminAiData.getVendorDiagnostics(from, to, vendorId);
                    if (vendorIds != null && !vendorIds.isEmpty()) {
                        final List<UUID> filterIds = vendorIds;
                        list = list.stream().filter(item -> item.vendorId() != null && filterIds.contains(item.vendorId())).toList();
                    }

                    Comparator<AdminAiDataPort.VendorDiagnosticItem> comparator = Comparator.comparing(
                            AdminAiDataPort.VendorDiagnosticItem::gmv,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    );
                    if ("desc".equalsIgnoreCase(sortOrder)) {
                        comparator = comparator.reversed();
                    }
                    comparator = comparator.thenComparing(
                            AdminAiDataPort.VendorDiagnosticItem::businessName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                    ).thenComparing(item -> item.vendorId() != null ? item.vendorId().toString() : "");

                    list = list.stream().sorted(comparator).toList();

                    if (limit != null && limit > 0 && limit < list.size()) {
                        list = list.subList(0, limit);
                    }

                    yield list;
                }
                case "admin_dispute_summarize" -> {
                    if (!args.hasNonNull("disputeId")) {
                        throw new IllegalArgumentException("disputeId is required for dispute summary tool");
                    }
                    UUID disputeId = UUID.fromString(args.get("disputeId").asText());
                    SupportedLanguage lang = context.language() != null ? context.language() : SupportedLanguage.VI;
                    yield disputeSummaryUseCase.execute(adminUserId, disputeId, lang);
                }
                case "admin_risk_and_support_queue_overview" -> adminAiData.getQueueOverview();
                default -> throw new IllegalArgumentException("Unknown admin tool: " + name);
            };

            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENTS", e.getMessage());
        } catch (Exception e) {
            return error("TOOL_EXECUTION_FAILED", "Failed to execute admin tool: " + e.getMessage());
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
