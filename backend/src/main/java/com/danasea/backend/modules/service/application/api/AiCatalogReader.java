package com.danasea.backend.modules.service.application.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.application.usecases.GetServiceSlotsAvailabilityUseCase;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AiCatalogReader implements AiCatalogReadApi {
    private final ServiceRepositoryPort services;
    private final ServiceOptionRepositoryPort options;
    private final CategoryRepositoryPort categories;
    private final GetServiceSlotsAvailabilityUseCase availability;

    private AiCatalogCandidateReadApi candidateReader;

    @Autowired
    public void setCandidateReader(AiCatalogCandidateReadApi candidateReader) { this.candidateReader = candidateReader; }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedService> search(Query query) { return searchPage(query).services(); }

    @Override
    @Transactional(readOnly = true)
    public SearchPage searchPage(Query query) {
        int limit = Math.min(15, Math.max(1, query.limit()));
        List<PublishedService> selected = new ArrayList<>();
        int rawOffset = query.offset();
        int inspected = 0;
        boolean exhausted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (rawOffset < 10000 && selected.size() < limit && System.nanoTime() < deadline) {
            List<Service> batch;
            if (candidateReader != null) {
                List<UUID> ids = candidateReader.candidateIds(query, rawOffset, 15);
                batch = ids.stream().map(services::findPublishedById).flatMap(Optional::stream).toList();
                if (ids.isEmpty()) { exhausted = true; break; }
            } else {
                batch = services.searchPublishedServices(query.categoryId(), query.keyword(), null, null,
                        decimal(query.latitude()), decimal(query.longitude()), query.radiusKm(), rawOffset / 15, 15);
                if (batch.isEmpty()) { exhausted = true; break; }
            }
            for (Service service : batch) {
                if (System.nanoTime() >= deadline) break;
                rawOffset++; inspected++;
                PublishedService snapshot = snapshot(service, query, true);
                if (snapshot.options().stream().noneMatch(option -> !option.slots().isEmpty())) continue;
                selected.add(snapshot);
                if (selected.size() >= limit) break;
            }
            if (batch.size() < 15 && selected.size() < limit && System.nanoTime() < deadline) { exhausted = true; break; }
        }
        List<String> limitations = new ArrayList<>();
        if (System.nanoTime() >= deadline || rawOffset >= 10000) limitations.add("CATALOG_SCAN_PARTIAL_COVERAGE");
        if (candidateReader == null) limitations.add("LEGACY_RETRIEVAL_COVERAGE_UNKNOWN");
        return new SearchPage(List.copyOf(selected), exhausted || rawOffset >= 10000 ? null : rawOffset, exhausted, inspected, List.copyOf(limitations));
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedService find(UUID id, Query query) {
        return snapshot(services.findPublishedById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Published service not found: " + id)), query, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedService metadata(UUID id, SupportedLanguage language) {
        return snapshot(services.findPublishedById(id).orElseThrow(() -> new ServiceNotFoundException("Published service not found: " + id)),
                new Query(null, null, null, null, 1, null, null, null, 1, language), false);
    }

    private PublishedService snapshot(Service service, Query query, boolean withSlots) {
        var category = service.getCategoryId() == null ? null : categories.findById(service.getCategoryId()).orElse(null);
        List<Option> quotedOptions = options.findByServiceIdAndStatus(service.getId(), OptionStatus.ACTIVE).stream()
                .filter(option -> option.getPrice() != null && option.getPrice().signum() >= 0 && option.getPricingUnit() != null)
                .filter(option -> option.getPricingUnit() != PricingUnit.PER_PACKAGE
                        || option.getMaxPaxPerPackage() != null && option.getMaxPaxPerPackage() > 0)
                .map(option -> {
                    int quantity = option.getPricingUnit() == PricingUnit.PER_PACKAGE
                            ? (query.partySize() - 1) / option.getMaxPaxPerPackage() + 1
                            : query.partySize();
                    List<Slot> slots = !withSlots ? List.of() : availability.execute(service.getId(), option.getId(), query.from(), query.to(), quantity, false)
                            .stream().filter(slot -> slot.bookable()).map(slot -> new Slot(slot.slotId(), slot.date(),
                                    slot.startTime(), slot.endTime(), slot.availablePaxOrPackages())).toList();
                    slots = slots.stream().filter(slot -> query.dayStart() == null || slot.start() != null && !slot.start().isBefore(query.dayStart()))
                            .filter(slot -> {
                                LocalTime end = slot.end();
                                if (end == null && slot.start() != null && service.getDurationMinutes() != null && service.getDurationMinutes() > 0) end = slot.start().plusMinutes(service.getDurationMinutes());
                                return end != null && slot.start() != null && end.isAfter(slot.start()) && (query.dayEnd() == null || !end.isAfter(query.dayEnd()));
                            }).toList();
                    return new Option(option.getId(), option.getName(), option.getPricingUnit().name(), option.getPrice(),
                            quantity, option.getPrice().multiply(BigDecimal.valueOf(quantity)), option.getBenefits(), slots, option.getMaxPaxPerPackage());
                }).filter(option -> query.totalBudget() == null || option.partyTotal().compareTo(query.totalBudget()) <= 0).toList();
        boolean english = query.language() == SupportedLanguage.EN;
        return new PublishedService(service.getId(), service.getVendorId(),
                localize(service.getName(), service.getNameEn(), english),
                localize(service.getDescription(), service.getDescriptionEn(), english), service.getCategoryId(),
                category == null ? null : localize(category.getName(), category.getNameEn(), english),
                category == null ? null : category.getSlug(), service.getAddress(),
                number(service.getLatitude()), number(service.getLongitude()), service.getDurationMinutes(),
                Boolean.TRUE.equals(service.getWeatherSensitive()), service.getAvgRating(),
                service.getRatingCount() == null ? 0 : service.getRatingCount(), quotedOptions);
    }

    private String localize(String vietnamese, String english, boolean useEnglish) {
        return useEnglish && english != null && !english.isBlank() ? english : vietnamese;
    }

    private BigDecimal decimal(Double value) { return value == null ? null : BigDecimal.valueOf(value); }
    private Double number(BigDecimal value) { return value == null ? null : value.doubleValue(); }
}
