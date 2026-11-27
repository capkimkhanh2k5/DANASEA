package com.danasea.backend.modules.ai.application.usecases;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.AssessmentCaseStorePort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.services.AiEntityReferenceResolver;
import com.danasea.backend.modules.ai.domain.models.AssessmentCase;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.ai.domain.services.TravelQueryParser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessTextUseCase {
    private static final Pattern PHONE = Pattern.compile("(?<![a-zA-Z0-9])(?:\\+84|0)(?:[ .-]?\\d){9}(?![a-zA-Z0-9])");
    private static final Pattern EXTERNAL_LINK_OR_EMAIL = Pattern.compile("https?://[^\\s<>]+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PAYMENT_WARNING_ONLY = Pattern.compile(
            "^(?:(?:vui long |xin )?(?:khong|khong nen|tuyet doi khong|dung) "
                    + "(?:chuyen tien|chuyen khoan|thanh toan|tra tien) (?:ben )?ngoai (?:nen tang )?danasea"
                    + "|(?:please )?(?:do not|never) (?:pay|transfer money) outside (?:the )?danasea(?: platform)?)[.!。]*$");
    public record Result(String status, List<String> reasonCodes, List<String> contactMatches,
                         DecisionResult category, DecisionResult moderation, AssessmentCase caseRecord,
                         boolean publicationAuthorized, String evidenceType) {}
    private final DecisionModelPort model;
    private final AssessmentCaseStorePort cases;

    public Result execute(UUID actorId, String text, boolean persist) {
        if (actorId == null || text == null || text.isBlank() || text.length() > 2500) {
            throw new IllegalArgumentException("Authenticated text assessment requires 1 to 2500 characters");
        }
        DecisionResult category = model.decide(DecisionTask.SERVICE_CATEGORY, Map.of("content", text));
        DecisionResult moderation = model.decide(DecisionTask.MODERATION, Map.of("content", text));
        String uuidStripped = AiEntityReferenceResolver.stripUuids(text);
        List<String> contacts = Stream.concat(
                PHONE.matcher(uuidStripped).results().map(MatchResult::group),
                EXTERNAL_LINK_OR_EMAIL.matcher(text).results().map(MatchResult::group)
        ).distinct().toList();
        List<String> reasons = new ArrayList<>();
        if (!contacts.isEmpty()) reasons.add("EXTERNAL_CONTACT_DETECTED");
        boolean warningOnly = contacts.isEmpty() && PAYMENT_WARNING_ONLY.matcher(
                TravelQueryParser.normalize(text).strip()).matches();
        for (String hazard : List.of("external_payment", "spam", "abuse")) {
            if (warningOnly && !"abuse".equals(hazard)) continue;
            Double probability = moderation.probability(hazard);
            if (probability != null && probability >= 0.5) reasons.add("MODEL_SUGGESTS_" + hazard.toUpperCase(Locale.ROOT));
        }
        if (!moderation.available()) reasons.add("MODEL_UNAVAILABLE");
        String status = reasons.isEmpty() ? "NO_RULE_SIGNAL" : "NEEDS_REVIEW";
        AssessmentCase stored = persist ? cases.create("TEXT_MODERATION", null, actorId, status,
                Map.of("content", text, "sourceType", "USER_SUPPLIED_TEXT", "contactMatches", contacts, "reasonCodes", reasons),
                Map.of("category", category, "moderation", moderation)) : null;
        return new Result(status, List.copyOf(reasons), contacts, category, moderation, stored, false, "TEXT_ONLY");
    }
}
