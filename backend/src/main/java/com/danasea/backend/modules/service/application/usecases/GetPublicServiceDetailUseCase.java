package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.services.ServiceDiscoveryAvailability;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceAvailabilityPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceOptionResponse;
import com.danasea.backend.modules.service.presentation.dtos.StructuredSlotResponse;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
public class GetPublicServiceDetailUseCase {
    private ServiceDiscoveryAvailability discoveryAvailability;

    @Autowired
    public void setDiscoveryAvailability(ServiceDiscoveryAvailability discoveryAvailability) {
        this.discoveryAvailability = discoveryAvailability;
    }

    private final ServiceRepositoryPort serviceRepositoryPort;
    private final RecordRecentlyViewedUseCase recordRecentlyViewedUseCase;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final ServiceImageRepositoryPort serviceImageRepositoryPort;
    private final ServiceAvailabilityPort serviceAvailabilityPort;
    private final LocalizedContentSelector localizedContentSelector;
    private final ServiceOptionRepositoryPort serviceOptionRepositoryPort;
    private final VendorPort vendorPort;
    private final ServiceSlotRepositoryPort serviceSlotRepositoryPort;
    private final RefundPolicyEngine refundPolicyEngine;

    @Autowired
    public GetPublicServiceDetailUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            RecordRecentlyViewedUseCase recordRecentlyViewedUseCase,
            CategoryRepositoryPort categoryRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            ServiceAvailabilityPort serviceAvailabilityPort,
            @Autowired(required = false) LocalizedContentSelector localizedContentSelector,
            ServiceOptionRepositoryPort serviceOptionRepositoryPort,
            @Autowired(required = false) VendorPort vendorPort,
            @Autowired(required = false) ServiceSlotRepositoryPort serviceSlotRepositoryPort,
            @Autowired(required = false) RefundPolicyEngine refundPolicyEngine
    ) {
        this.serviceRepositoryPort = serviceRepositoryPort;
        this.recordRecentlyViewedUseCase = recordRecentlyViewedUseCase;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.serviceImageRepositoryPort = serviceImageRepositoryPort;
        this.serviceAvailabilityPort = serviceAvailabilityPort;
        this.localizedContentSelector = localizedContentSelector;
        this.serviceOptionRepositoryPort = serviceOptionRepositoryPort;
        this.vendorPort = vendorPort;
        this.serviceSlotRepositoryPort = serviceSlotRepositoryPort;
        this.refundPolicyEngine = refundPolicyEngine;
    }

    public GetPublicServiceDetailUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            RecordRecentlyViewedUseCase recordRecentlyViewedUseCase,
            CategoryRepositoryPort categoryRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            ServiceAvailabilityPort serviceAvailabilityPort,
            LocalizedContentSelector localizedContentSelector,
            ServiceOptionRepositoryPort serviceOptionRepositoryPort
    ) {
        this(serviceRepositoryPort, recordRecentlyViewedUseCase, categoryRepositoryPort,
                serviceImageRepositoryPort, serviceAvailabilityPort, localizedContentSelector,
                serviceOptionRepositoryPort, null, null, null);
    }

    public ServiceDetailResult execute(UUID id, UUID userId, String sessionId) {
        return loadByIdOrSlug(id.toString(), userId, sessionId, true);
    }

    public ServiceDetailResult executeByIdOrSlug(String idOrSlug, UUID userId, String sessionId) {
        return loadByIdOrSlug(idOrSlug, userId, sessionId, true);
    }

    public ServiceDetailResult readOnlySnapshot(UUID id) {
        return loadByIdOrSlug(id.toString(), null, null, false);
    }

    public ServiceDetailResult readOnlySnapshotByIdOrSlug(String idOrSlug) {
        return loadByIdOrSlug(idOrSlug, null, null, false);
    }

    private ServiceDetailResult loadByIdOrSlug(String idOrSlug, UUID userId, String sessionId, boolean recordView) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new ServiceNotFoundException("Service identifier is required");
        }

        Service service;
        try {
            UUID uuid = UUID.fromString(idOrSlug.trim());
            service = serviceRepositoryPort.findPublishedById(uuid)
                    .orElseThrow(() -> new ServiceNotFoundException("Service not found or not published: " + uuid));
        } catch (IllegalArgumentException ex) {
            service = serviceRepositoryPort.findPublishedBySlug(idOrSlug.trim())
                    .orElseThrow(() -> new ServiceNotFoundException("Service not found or not published with slug: " + idOrSlug));
        }

        UUID id = service.getId();

        if (recordView) {
            serviceRepositoryPort.incrementViewCount(id, ServiceStatus.PUBLISHED);
            recordRecentlyViewedUseCase.execute(id, userId, sessionId);
        }

        int viewCount = service.getViewCount() != null ? service.getViewCount() : 0;
        String categoryName = service.getCategoryId() == null ? null
                : categoryRepositoryPort.findById(service.getCategoryId())
                        .map(category -> category.getName())
                        .orElse(null);

        var options = serviceOptionRepositoryPort.findByServiceIdAndStatus(id, OptionStatus.ACTIVE).stream()
                .map(o -> ServiceOptionResponse.builder()
                        .id(o.getId())
                        .serviceId(o.getServiceId())
                        .name(o.getName())
                        .optionType(o.getOptionType())
                        .pricingUnit(o.getPricingUnit())
                        .price(o.getPrice())
                        .maxPaxPerPackage(o.getMaxPaxPerPackage())
                        .benefits(o.getBenefits())
                        .status(o.getStatus())
                        .createdAt(o.getCreatedAt())
                        .updatedAt(o.getUpdatedAt())
                        .build())
                .toList();

        // Vendor public details
        UUID vendorId = service.getVendorId();
        String businessName = null;
        BadgeTier badgeTier = null;
        if (vendorPort != null && vendorId != null) {
            var vendorOpt = vendorPort.findById(vendorId);
            if (vendorOpt.isPresent()) {
                var vendor = vendorOpt.get();
                businessName = vendor.getBusinessName();
                badgeTier = vendor.getBadgeTier();
            }
        }

        // Operational info
        Integer duration = service.getDurationMinutes();
        Integer capacity = service.getCapacityPerSlot();
        String participantConditions = localize(service.getWaiverContent(), service.getWaiverContentEn());
        if (participantConditions == null || participantConditions.isBlank()) {
            participantConditions = service.getWaiverContent();
        }

        LocalizedContentSelector.LocalizedSelection waiverSelection = localizedContentSelector != null
                ? localizedContentSelector.selectDetailed(service.getWaiverContent(), service.getWaiverContentEn())
                : new LocalizedContentSelector.LocalizedSelection(service.getWaiverContent(), "VI", false);

        Boolean waiverRequired = Boolean.TRUE.equals(service.getWaiverRequired());
        Integer waiverVersion = service.getWaiverVersion() != null ? service.getWaiverVersion() : 1;
        String waiverContent = waiverSelection.content();
        String waiverLanguage = waiverSelection.language();
        Boolean waiverFallbackUsed = waiverSelection.fallbackUsed();

        // Refund, cancellation and safety policies
        String refundPolicy = refundPolicyEngine != null ? refundPolicyEngine.getRefundPolicySummary()
                : "Refund policy: 100% more than 24 hours before departure and 0% within 24 hours or for no-shows. Verified weather, vendor and system faults receive a full refund.";
        String cancellationPolicy = refundPolicyEngine != null ? refundPolicyEngine.getCancellationPolicySummary()
                : "Cancellation policy: Free cancellation more than 24 hours before departure.";

        String safetyRules;
        if (Boolean.TRUE.equals(service.getWeatherSensitive())) {
            safetyRules = "Weather-sensitive activity. Max wave: "
                    + (service.getMaxWaveM() != null ? service.getMaxWaveM() + "m" : "N/A")
                    + ", Max wind: "
                    + (service.getMinWindKmh() != null ? service.getMinWindKmh() + "km/h" : "N/A")
                    + ". Standard marine safety regulations and life jacket requirements apply.";
        } else {
            safetyRules = "Standard marine safety rules apply. Life jackets must be worn at all times.";
        }

        List<StructuredSlotResponse> structuredSlots = discoveryAvailability == null
                ? List.of() : discoveryAvailability.describe(service);
        List<String> availableSlots = discoveryAvailability == null ? serviceAvailabilityPort.findAvailableSlots(id)
                : structuredSlots.stream().filter(StructuredSlotResponse::bookable)
                        .map(slot -> slot.date() + "T" + slot.startTime().format(DateTimeFormatter.ISO_LOCAL_TIME))
                        .distinct().toList();

        return ServiceDetailResult.builder()
                .id(service.getId())
                .name(localize(service.getName(), service.getNameEn()))
                .slug(service.getSlug())
                .description(localize(service.getDescription(), service.getDescriptionEn()))
                .price(service.getPrice())
                .address(service.getAddress())
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .averageRating(service.getAvgRating())
                .reviewCount(service.getRatingCount() != null ? service.getRatingCount() : 0)
                .viewCount(viewCount + (recordView ? 1 : 0))
                .categoryId(service.getCategoryId())
                .categoryName(categoryName)
                .imageUrls(serviceImageRepositoryPort.findByServiceId(id).stream()
                        .map(image -> image.getUrl())
                        .toList())
                .availableSlots(availableSlots)
                .options(options)
                .vendorId(vendorId)
                .businessName(businessName)
                .badgeTier(badgeTier)
                .duration(duration)
                .capacity(capacity)
                .participantConditions(participantConditions)
                .waiverRequired(waiverRequired)
                .waiverVersion(waiverVersion)
                .waiverContent(waiverContent)
                .waiverLanguage(waiverLanguage)
                .waiverFallbackUsed(waiverFallbackUsed)
                .refundPolicy(refundPolicy)
                .cancellationPolicy(cancellationPolicy)
                .safetyRules(safetyRules)
                .slots(structuredSlots)
                .build();
    }

    private String localize(String vietnamese, String english) {
        return localizedContentSelector == null ? vietnamese : localizedContentSelector.select(vietnamese, english);
    }
}
