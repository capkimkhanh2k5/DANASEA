package com.danasea.backend.modules.ai.application.usecases;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AiExecutionBudget;
import com.danasea.backend.modules.ai.application.ports.TravelWeatherPort;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Slot;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MarineSlotWeather {

    public enum Status { ACCEPTABLE, CAUTION, UNSAFE, UNKNOWN, NOT_REQUIRED }

    private final AiCatalogReadApi catalog;
    private final TravelWeatherPort weather;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Status assess(UUID serviceId, Slot slot, SupportedLanguage language) {
        if (serviceId == null || slot == null || slot.id() == null || slot.date() == null
                || !AiExecutionBudget.hasTimeFor(Duration.ofSeconds(1))) {
            return Status.UNKNOWN;
        }
        try {
            var service = catalog.metadata(serviceId, language);
            if (service == null) return Status.UNKNOWN;
            var assessment = weather.assess(service, slot);
            if (assessment == null) return Status.UNKNOWN;
            return switch (assessment.status()) {
                case "ACCEPTABLE" -> assessment.requiresRevalidation() ? Status.CAUTION : Status.ACCEPTABLE;
                case "PROVISIONAL" -> Status.CAUTION;
                case "UNSAFE" -> Status.UNSAFE;
                case "NOT_REQUIRED" -> Status.NOT_REQUIRED;
                default -> Status.UNKNOWN;
            };
        } catch (ServiceNotFoundException ex) {
            return Status.UNKNOWN;
        }
    }
}
