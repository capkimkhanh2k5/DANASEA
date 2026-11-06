package com.danasea.backend.modules.checkin.domain.services;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;

@Component
public class CheckinWindowPolicy {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CheckinProperties properties;

    public CheckinWindowPolicy(CheckinProperties properties) {
        this.properties = properties;
    }

    public void validate(ServiceSlotJpaEntity slot, OffsetDateTime now) {
        if (slot == null || slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new InvalidCheckinStateException("The service slot has incomplete check-in timing data.");
        }
        if (slot.getStatus() == SlotStatus.CANCELLED) {
            throw new InvalidCheckinStateException("This departure was cancelled.");
        }
        OffsetDateTime start = LocalDateTime.of(slot.getDate(), slot.getStartTime())
                .atZone(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime end = LocalDateTime.of(
                        slot.getEndTime().isBefore(slot.getStartTime()) ? slot.getDate().plusDays(1) : slot.getDate(),
                        slot.getEndTime())
                .atZone(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime opensAt = start.minus(properties.opensBefore());
        OffsetDateTime closesAt = end.plus(properties.closesAfter());
        if (now.isBefore(opensAt)) {
            throw new InvalidCheckinStateException("Check-in is not open yet. It opens at " + opensAt + ".");
        }
        if (now.isAfter(closesAt)) {
            throw new InvalidCheckinStateException("The check-in window closed at " + closesAt + ".");
        }
    }
}
