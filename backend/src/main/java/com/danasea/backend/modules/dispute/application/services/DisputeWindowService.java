package com.danasea.backend.modules.dispute.application.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisputeWindowService {

    private static final int DISPUTE_WINDOW_DAYS = 7;

    private final JpaServiceSlotRepository slotRepository;
    private final Clock clock;

    public DisputeWindow resolve(SubOrderJpaEntity subOrder) {
        LocalDateTime now = LocalDateTime.now(clock);
        ServiceSlotJpaEntity slot = subOrder.getSlotId() == null
                ? null : slotRepository.findById(subOrder.getSlotId()).orElse(null);
        if (slot != null && slot.getDate() != null) {
            LocalDateTime start = slot.getStartTime() == null ? null : slot.getDate().atTime(slot.getStartTime());
            return new DisputeWindow(slot.getDate(), start, now);
        }

        LocalDate experienceDate = subOrder.getCreatedAt() == null
                ? now.toLocalDate()
                : subOrder.getCreatedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
        return new DisputeWindow(experienceDate, null, now);
    }

    public record DisputeWindow(LocalDate experienceDate, LocalDateTime scheduledStart, LocalDateTime now) {
        public boolean hasStarted() {
            return scheduledStart == null ? !experienceDate.isAfter(now.toLocalDate()) : !scheduledStart.isAfter(now);
        }

        public boolean isExpired() {
            return now.toLocalDate().isAfter(experienceDate.plusDays(DISPUTE_WINDOW_DAYS));
        }
    }
}
