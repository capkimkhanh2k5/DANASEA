package com.danasea.backend.modules.communication.infrastructure.email;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailFamily;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.transactional-email")
@Getter
@Setter
public class TransactionalEmailProperties {

    private String mode = "off";

    private WorkerProperties worker = new WorkerProperties();

    private String enabledFamilies = "";

    private String liveFromByFamily = "";

    private String livePilotRecipients = "";

    private String publicBaseUrl = "https://danasea.vn";

    private String captureSinkEmail = "capture@danasea.vn";

    private String supportPhone = "1900-6868";

    private Map<String, String> opsEmails = new HashMap<>();

    public String getOpsEmail(String function) {
        if (opsEmails == null || function == null) {
            return null;
        }
        return opsEmails.get(function.toLowerCase());
    }

    public EmailDeliveryMode getDeliveryMode() {
        return EmailDeliveryMode.fromString(mode);
    }

    public boolean isWorkerEnabled() {
        return worker.isEnabled() && getDeliveryMode() != EmailDeliveryMode.OFF;
    }

    public boolean isFamilyEnabled(TransactionalEmailFamily family) {
        if (family == null) {
            return false;
        }
        if (enabledFamilies == null || enabledFamilies.isBlank()) {
            return false;
        }
        String[] parts = enabledFamilies.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toUpperCase();
            if (trimmed.equals("ALL") || trimmed.equals(family.name())) {
                return true;
            }
        }
        return false;
    }

    public OffsetDateTime getLiveFrom(TransactionalEmailFamily family) {
        if (family == null || liveFromByFamily == null || liveFromByFamily.isBlank()) {
            return null;
        }
        Map<TransactionalEmailFamily, OffsetDateTime> map = parseLiveFromMap();
        return map.get(family);
    }

    public Set<String> getPilotRecipients() {
        if (livePilotRecipients == null || livePilotRecipients.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String s : livePilotRecipients.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private Map<TransactionalEmailFamily, OffsetDateTime> parseLiveFromMap() {
        Map<TransactionalEmailFamily, OffsetDateTime> result = new HashMap<>();
        if (liveFromByFamily == null || liveFromByFamily.isBlank()) {
            return result;
        }
        for (String pair : liveFromByFamily.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    TransactionalEmailFamily fam = TransactionalEmailFamily.valueOf(kv[0].trim().toUpperCase());
                    OffsetDateTime dt = OffsetDateTime.parse(kv[1].trim());
                    result.put(fam, dt);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    @Getter
    @Setter
    public static class WorkerProperties {
        private boolean enabled = false;
        private long pollIntervalMs = 15000L;
        private int leaseDurationSeconds = 120;
        private int maxAttempts = 4;
        private int batchSize = 20;
    }
}
