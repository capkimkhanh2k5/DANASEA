package com.danasea.backend.modules.checkin.domain.services;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelledDepartureCheckinTest {
    @Test
    void cancelledDepartureCannotCheckInEvenWithinItsWindow() {
        var now = OffsetDateTime.parse("2026-11-10T08:00:00+07:00");
        var slot = new ServiceSlotJpaEntity();
        slot.setDate(now.toLocalDate());
        slot.setStartTime(now.toLocalTime());
        slot.setEndTime(now.plusHours(2).toLocalTime());
        var policy = new CheckinWindowPolicy(new CheckinProperties(null, null, null));

        slot.setStatus(SlotStatus.CANCELLED);
        assertThatThrownBy(() -> policy.validate(slot, now)).isInstanceOf(InvalidCheckinStateException.class);
    }
}
