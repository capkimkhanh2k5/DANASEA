package com.danasea.backend.modules.ai.application.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.application.ports.TransactionRiskReadPort;
import com.danasea.backend.modules.ai.application.usecases.AdminDisputeSummaryUseCase;
import com.danasea.backend.modules.ai.application.usecases.AnalyzeTransactionRiskUseCase;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.domain.models.AssessmentCase;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AdminFeatureTool implements ToolExecutor {

    private static final String RISK_RULE_VERSION = "velocity-demo-v1";
    private static final String RISK_LIMITATIONS =
            "Velocity 3/5/3 rules and Quyet AI scores are review indicators only (fraudConfirmed=false); "
                    + "verify payment gateway logs and customer order history before taking account action.";

    private final String name;
    private final AdminAiDataPort adminAiData;
    private final AdminDisputeSummaryUseCase disputeSummaryUseCase;
    private final ObjectMapper mapper;
    private final AssessmentCaseStorePort assessmentCaseStore;
    private final TransactionRiskReadPort transactionRiskReadPort;
    private final AnalyzeTransactionRiskUseCase analyzeTransactionRiskUseCase;
    private final AssessTextUseCase assessTextUseCase;

    public AdminFeatureTool(
            String name,
            AdminAiDataPort adminAiData,
            AdminDisputeSummaryUseCase disputeSummaryUseCase,
            ObjectMapper mapper
    ) {
        this(name, adminAiData, disputeSummaryUseCase, mapper, null, null, null, null);
    }

    public AdminFeatureTool(
            String name,
            AdminAiDataPort adminAiData,
            AdminDisputeSummaryUseCase disputeSummaryUseCase,
            ObjectMapper mapper,
            AssessmentCaseStorePort assessmentCaseStore,
            TransactionRiskReadPort transactionRiskReadPort,
            AnalyzeTransactionRiskUseCase analyzeTransactionRiskUseCase
    ) {
        this(name, adminAiData, disputeSummaryUseCase, mapper, assessmentCaseStore,
                transactionRiskReadPort, analyzeTransactionRiskUseCase, null);
    }

    public AdminFeatureTool(
            String name,
            AdminAiDataPort adminAiData,
            AdminDisputeSummaryUseCase disputeSummaryUseCase,
            ObjectMapper mapper,
            AssessmentCaseStorePort assessmentCaseStore,
            TransactionRiskReadPort transactionRiskReadPort,
            AnalyzeTransactionRiskUseCase analyzeTransactionRiskUseCase,
            AssessTextUseCase assessTextUseCase
    ) {
        this.name = name;
        this.adminAiData = adminAiData;
        this.disputeSummaryUseCase = disputeSummaryUseCase;
        this.mapper = mapper;
        this.assessmentCaseStore = assessmentCaseStore;
        this.transactionRiskReadPort = transactionRiskReadPort;
        this.analyzeTransactionRiskUseCase = analyzeTransactionRiskUseCase;
        this.assessTextUseCase = assessTextUseCase;
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

            LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
            LocalDate from = args.hasNonNull("from") ? LocalDate.parse(args.get("from").asText()) : today.minusDays(30);
            LocalDate to = args.hasNonNull("to") ? LocalDate.parse(args.get("to").asText()) : today;
            String groupBy = args.path("groupBy").asText("day");

            Object result = switch (name) {
                case "admin_platform_dashboard" -> adminAiData.getPlatformDashboardMetrics();
                case "admin_revenue_timeseries" -> adminAiData.getRevenueTimeSeries(from, to, groupBy);
                case "admin_booking_cancellation_breakdown" -> adminAiData.getBookingCancellationBreakdown(from, to, groupBy);
                case "admin_vendor_diagnostics" -> {
                    UUID vendorId = args.hasNonNull("vendorId") ? UUID.fromString(args.get("vendorId").asText()) : null;
                    yield adminAiData.getVendorDiagnostics(from, to, vendorId);
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
                case "admin_text_assessment" -> {
                    String text = args.hasNonNull("text") ? args.get("text").asText("") : "";
                    boolean persist = args.path("persist").asBoolean(false);
                    yield VendorFeatureTool.assessContent(assessTextUseCase, adminUserId, text, null, persist);
                }
                case "admin_order_risk_assessment" -> assessOrderRisk(adminUserId, args);
                default -> throw new IllegalArgumentException("Unknown admin tool: " + name);
            };

            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return error("INVALID_ARGUMENTS", e.getMessage());
        } catch (Exception e) {
            return error("TOOL_EXECUTION_FAILED", "Failed to execute admin tool: " + e.getMessage());
        }
    }

    private Map<String, Object> assessOrderRisk(UUID adminUserId, JsonNode args) {
        UUID orderId = args.hasNonNull("orderId") ? UUID.fromString(args.get("orderId").asText()) : null;
        UUID caseId = args.hasNonNull("caseId") ? UUID.fromString(args.get("caseId").asText()) : null;
        if (orderId == null && caseId == null) {
            throw new IllegalArgumentException("orderId or caseId is required for order risk assessment");
        }
        boolean createNew = args.path("createNewAssessment").asBoolean(false);
        String complaint = args.hasNonNull("complaint") ? args.get("complaint").asText("") : "";

        if (caseId != null && !createNew) {
            if (assessmentCaseStore == null) {
                throw new IllegalStateException("Assessment case store is unavailable");
            }
            AssessmentCase found = assessmentCaseStore.findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Assessment case not found: " + caseId));
            return fromAssessmentCase(found, false);
        }

        if (createNew) {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId is required when createNewAssessment is true");
            }
            if (analyzeTransactionRiskUseCase == null) {
                throw new IllegalStateException("Transaction risk analysis service is unavailable");
            }
            AssessmentCase created = analyzeTransactionRiskUseCase.execute(adminUserId, orderId, complaint);
            return fromAssessmentCase(created, true);
        }

        if (assessmentCaseStore != null) {
            var existing = assessmentCaseStore.findBySourceId(orderId).stream()
                    .filter(c -> "TRANSACTION_RISK".equals(c.kind()))
                    .findFirst();
            if (existing.isPresent()) {
                return fromAssessmentCase(existing.get(), false);
            }
        }

        if (transactionRiskReadPort == null) {
            throw new IllegalStateException("Transaction risk read port is unavailable");
        }
        TransactionRiskReadPort.Facts observed = transactionRiskReadPort.read(orderId);
        List<String> signals = new ArrayList<>();
        if (observed.failedPayments24h() >= 3) {
            signals.add("THREE_FAILED_PAYMENTS_IN_24H");
        }
        if (observed.orders1h() >= 5) {
            signals.add("FIVE_ORDERS_IN_1H");
        }
        if (observed.refundRequests7d() >= 3) {
            signals.add("THREE_REFUND_REQUESTS_IN_7D");
        }
        String status = !signals.isEmpty() ? "NEEDS_REVIEW" : "NO_RULE_SIGNAL";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", observed.orderId() != null ? observed.orderId() : orderId);
        result.put("caseId", null);
        result.put("status", status);
        result.put("observed", observed);
        result.put("riskSignals", List.copyOf(signals));
        result.put("ruleVersion", RISK_RULE_VERSION);
        result.put("quyetDecision", null);
        result.put("fraudConfirmed", false);
        result.put("newCaseCreated", false);
        result.put("limitations", RISK_LIMITATIONS + " Live read only; no new assessment case was persisted.");
        return result;
    }

    private Map<String, Object> fromAssessmentCase(AssessmentCase assessmentCase, boolean newCaseCreated) {
        Map<String, Object> evidence = assessmentCase.evidence() != null ? assessmentCase.evidence() : Map.of();
        Map<String, Object> decisions = assessmentCase.decisions() != null ? assessmentCase.decisions() : Map.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", assessmentCase.sourceId());
        result.put("caseId", assessmentCase.id());
        result.put("status", assessmentCase.status());
        result.put("observed", evidence.get("observed"));
        result.put("riskSignals", evidence.getOrDefault("riskSignals", List.of()));
        result.put("ruleVersion", evidence.getOrDefault("ruleVersion", RISK_RULE_VERSION));
        result.put("quyetDecision", decisions.get("risk"));
        result.put("fraudConfirmed", Boolean.TRUE.equals(evidence.get("fraudConfirmed")));
        result.put("newCaseCreated", newCaseCreated);
        if (assessmentCase.createdAt() != null) {
            result.put("createdAt", assessmentCase.createdAt());
        }
        if (assessmentCase.resolutionNote() != null) {
            result.put("resolutionNote", assessmentCase.resolutionNote());
        }
        result.put("limitations", RISK_LIMITATIONS);
        return result;
    }

    private String error(String code, String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", code, "message", message));
        } catch (Exception e) {
            return "{\"error\":\"FEATURE_UNAVAILABLE\"}";
        }
    }
}
