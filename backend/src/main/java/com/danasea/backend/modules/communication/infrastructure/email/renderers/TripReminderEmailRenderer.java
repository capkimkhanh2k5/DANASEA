package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TripReminderEmailRenderer implements TransactionalEmailRenderer {

    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final JpaVendorRepository vendorRepository;
    private final JpaUserRepository userRepository;
    private final JpaTransactionalEmailDeliveryRepository emailDeliveryRepository;
    private final TransactionalEmailProperties properties;

    @Autowired
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Setter
    private Clock clock = Clock.system(Booking.VIETNAM_ZONE);

    public TripReminderEmailRenderer(
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaServiceRepository serviceRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            JpaVendorRepository vendorRepository,
            JpaUserRepository userRepository,
            JpaTransactionalEmailDeliveryRepository emailDeliveryRepository,
            TransactionalEmailProperties properties,
            @Autowired(required = false) Clock clock
    ) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.serviceRepository = serviceRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.properties = properties;
        if (clock != null) {
            this.clock = clock;
        }
    }

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.TRIP_REMINDER_24H;
    }

    @Override
    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        UUID slotId = extractSlotId(entity.getBusinessKey());
        Instant scheduledDepartureInstant = extractDepartureInstant(entity.getBusinessKey());
        UUID customerId = extractCustomerId(entity.getRecipientKey());

        Instant now = clock.instant();

        // 1. Check late threshold right before sending: if <= 2 hours remaining, skip!
        Duration remaining = Duration.between(now, scheduledDepartureInstant);
        if (remaining.toMinutes() <= 120) {
            throw new SupersededEmailException(
                    "TRIP_STARTING_TOO_SOON",
                    "Trip starts in " + remaining.toMinutes() + " minutes, which is within or past the 2-hour threshold"
            );
        }

        // 2. Check if a booking confirmation was accepted within the last 2 hours
        OffsetDateTime twoHoursAgo = OffsetDateTime.ofInstant(now.minus(Duration.ofHours(2)), ZoneOffset.UTC);
        if (emailDeliveryRepository.existsRecentlyAccepted(
                TransactionalEmailType.BOOKING_CONFIRMED.name(),
                entity.getRecipientKey(),
                twoHoursAgo
        )) {
            throw new SupersededEmailException(
                    "RECENTLY_CONFIRMED_WITH_INSTRUCTIONS",
                    "Booking confirmation email was recently accepted with equivalent instructions"
            );
        }

        // 3. Check Slot status and schedule changes
        ServiceSlotJpaEntity slot = serviceSlotRepository.findById(slotId).orElse(null);
        if (slot == null || slot.getStatus() == SlotStatus.CANCELLED) {
            throw new SupersededEmailException(
                    "TRIP_CANCELLED",
                    "Slot " + slotId + " has been cancelled or no longer exists"
            );
        }

        ZonedDateTime currentSlotDepartureZdt = ZonedDateTime.of(slot.getDate(), slot.getStartTime(), Booking.VIETNAM_ZONE);
        if (!currentSlotDepartureZdt.toInstant().equals(scheduledDepartureInstant)) {
            throw new SupersededEmailException(
                    "TRIP_RESCHEDULED",
                    "Slot departure time changed from " + scheduledDepartureInstant + " to " + currentSlotDepartureZdt.toInstant()
            );
        }

        // 4. Check Customer Sub-Orders in this Slot
        List<SubOrderJpaEntity> slotSubOrders = subOrderRepository.findBySlotId(slotId);
        List<SubOrderJpaEntity> customerSubOrders = slotSubOrders.stream()
                .filter(so -> {
                    var order = masterOrderRepository.findById(so.getMasterOrderId()).orElse(null);
                    return order != null && customerId.equals(order.getCustomerId());
                })
                .toList();

        if (customerSubOrders.isEmpty()) {
            throw new SupersededEmailException(
                    "NO_ACTIVE_BOOKING_FOR_SLOT",
                    "No sub-orders found for customer " + customerId + " in slot " + slotId
            );
        }

        boolean allCancelled = customerSubOrders.stream().allMatch(
                so -> so.getStatus() == SubOrderStatus.CANCELLED || so.getStatus() == SubOrderStatus.REFUNDED
        );
        if (allCancelled) {
            throw new SupersededEmailException(
                    "TRIP_CANCELLED",
                    "All customer sub-orders for slot " + slotId + " have been cancelled or refunded"
            );
        }

        boolean allCheckedIn = customerSubOrders.stream().allMatch(
                so -> so.getStatus() == SubOrderStatus.CHECKED_IN || so.getStatus() == SubOrderStatus.COMPLETED
        );
        if (allCheckedIn) {
            throw new SupersededEmailException(
                    "ALREADY_CHECKED_IN",
                    "Customer has already checked in for slot " + slotId
            );
        }

        boolean anyConfirmed = customerSubOrders.stream().anyMatch(
                so -> so.getStatus() == SubOrderStatus.CONFIRMED
        );
        if (!anyConfirmed) {
            throw new SupersededEmailException(
                    "NO_ACTIVE_BOOKING_FOR_SLOT",
                    "No confirmed sub-orders remaining for customer in slot " + slotId
            );
        }

        // 5. Build Content
        SupportedLanguage language = SupportedLanguage.fromTag(recipient.locale()).orElse(SupportedLanguage.VI);
        ServiceJpaEntity service = serviceRepository.findById(slot.getServiceId()).orElse(null);

        String serviceName = "Dịch vụ Danasea";
        if (service != null) {
            if (language == SupportedLanguage.EN && service.getNameEn() != null && !service.getNameEn().isBlank()) {
                serviceName = service.getNameEn();
            } else if (service.getName() != null && !service.getName().isBlank()) {
                serviceName = service.getName();
            }
        }

        String departureTimeStr = formatDeparture(slot.getDate(), slot.getStartTime());

        // Contact Phone Resolution:
        // Priority: 1) Slot contact (none currently) -> 2) Vendor public phone -> 3) Verified Danasea hotline -> 4) None
        String contactPhone = resolveContactPhone(service);

        // Coordinates Resolution
        String coordinatesStr = resolveCoordinates(service);

        // Meeting Point Resolution
        String meetingPointStr = resolveMeetingPoint(service);

        UUID primaryOrderId = customerSubOrders.getFirst().getMasterOrderId();
        String orderUrl = properties.getPublicBaseUrl() + "/profile/orders?orderId=" + primaryOrderId;

        return buildEmail(customerId, recipient, language, serviceName, departureTimeStr, meetingPointStr, coordinatesStr, contactPhone, orderUrl);
    }

    private String resolveContactPhone(ServiceJpaEntity service) {
        if (service != null && service.getVendorId() != null) {
            var vendor = vendorRepository.findById(service.getVendorId()).orElse(null);
            if (vendor != null && vendor.getUserId() != null) {
                var vendorUser = userRepository.findById(vendor.getUserId()).orElse(null);
                if (vendorUser != null && vendorUser.getPhone() != null && !vendorUser.getPhone().isBlank()) {
                    return vendorUser.getPhone().trim();
                }
            }
        }
        if (properties.getSupportPhone() != null && !properties.getSupportPhone().isBlank()) {
            return properties.getSupportPhone().trim();
        }
        return null;
    }

    private String resolveCoordinates(ServiceJpaEntity service) {
        if (service != null && service.getLatitude() != null && service.getLongitude() != null) {
            if (service.getLatitude().compareTo(BigDecimal.ZERO) != 0 || service.getLongitude().compareTo(BigDecimal.ZERO) != 0) {
                return service.getLatitude().stripTrailingZeros().toPlainString() + ", "
                        + service.getLongitude().stripTrailingZeros().toPlainString();
            }
        }
        return null;
    }

    private String resolveMeetingPoint(ServiceJpaEntity service) {
        if (service == null) return null;
        String loc = service.getLocationName();
        String addr = service.getAddress();
        if (loc != null && !loc.isBlank() && addr != null && !addr.isBlank()) {
            return loc.trim() + " (" + addr.trim() + ")";
        } else if (loc != null && !loc.isBlank()) {
            return loc.trim();
        } else if (addr != null && !addr.isBlank()) {
            return addr.trim();
        }
        return null;
    }

    private RenderedEmail buildEmail(
            UUID customerId,
            ResolvedRecipient recipient,
            SupportedLanguage language,
            String serviceName,
            String departureTimeStr,
            String meetingPointStr,
            String coordinatesStr,
            String contactPhone,
            String orderUrl
    ) {
        String greetingName = userRepository.findById(customerId)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : null)
                .orElse(language == SupportedLanguage.EN ? "Valued Customer" : "Quý khách");

        String subject = messages.get("email.trip_reminder_24h.subject", language, serviceName, departureTimeStr);

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.trip_reminder_24h.greeting", language, greetingName)).append("\n\n");
        sb.append(messages.get("email.trip_reminder_24h.intro", language)).append("\n\n");

        sb.append("=== ").append(messages.get("email.trip_reminder_24h.section.trip", language)).append(" ===\n");
        sb.append(messages.get("email.trip_reminder_24h.label.service", language, serviceName)).append("\n");
        sb.append(messages.get("email.trip_reminder_24h.label.departure", language, departureTimeStr)).append("\n");

        if (meetingPointStr != null) {
            sb.append(messages.get("email.trip_reminder_24h.label.meeting_point", language, meetingPointStr)).append("\n");
        }
        if (coordinatesStr != null) {
            String[] parts = coordinatesStr.split(", ");
            sb.append(messages.get("email.trip_reminder_24h.label.coordinates", language, parts[0], parts[1])).append("\n");
        }
        if (contactPhone != null) {
            sb.append(messages.get("email.trip_reminder_24h.label.contact_phone", language, contactPhone)).append("\n");
        } else {
            sb.append("\n").append(messages.get("email.trip_reminder_24h.no_phone_notice", language, orderUrl)).append("\n");
        }

        sb.append("\n=== ").append(messages.get("email.trip_reminder_24h.section.preparation", language)).append(" ===\n");
        sb.append(messages.get("email.trip_reminder_24h.tips", language)).append("\n\n");

        sb.append(messages.get("email.trip_reminder_24h.cta", language, orderUrl)).append("\n\n");
        sb.append(messages.get("email.common.footer", language));

        return RenderedEmail.builder()
                .to(recipient.email())
                .subject(subject)
                .textBody(sb.toString())
                .build();
    }

    private String formatDeparture(LocalDate date, LocalTime time) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return (time != null ? time.format(timeFormatter) : "") + " "
                + (date != null ? date.format(dateFormatter) : "");
    }

    private UUID extractSlotId(String businessKey) {
        // format: slot:<slotId>:start:<instant-utc>:24h
        try {
            String[] parts = businessKey.split(":");
            return UUID.fromString(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract slotId from businessKey: " + businessKey, e);
        }
    }

    private Instant extractDepartureInstant(String businessKey) {
        // format: slot:<slotId>:start:<instant-utc>:24h
        try {
            int startIdx = businessKey.indexOf(":start:") + 7;
            int endIdx = businessKey.lastIndexOf(":24h");
            String instantStr = businessKey.substring(startIdx, endIdx);
            return Instant.parse(instantStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract departureInstant from businessKey: " + businessKey, e);
        }
    }

    private UUID extractCustomerId(String recipientKey) {
        // format: USER:<customerId>
        try {
            if (recipientKey.startsWith("USER:")) {
                return UUID.fromString(recipientKey.substring(5));
            }
            return UUID.fromString(recipientKey);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract customerId from recipientKey: " + recipientKey, e);
        }
    }
}
