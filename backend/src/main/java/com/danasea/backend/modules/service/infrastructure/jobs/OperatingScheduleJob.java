package com.danasea.backend.modules.service.infrastructure.jobs;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.application.usecases.OperatingScheduleUseCase;
import com.danasea.backend.modules.service.domain.models.OperatingSchedule;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OperatingScheduleJob {
    private final OperatingScheduleStore store;
    private final OperatingScheduleUseCase schedules;

    @Scheduled(cron = "${app.scheduler.operating-schedule-cron:0 5 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void refreshBookingHorizon() {
        LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
        LocalDate lastDay = today.plusDays(OperatingSchedule.BOOKING_HORIZON_DAYS - 1);
        for (var serviceId : store.findServicesToGenerate(today, lastDay)) {
            try {
                for (var departure : schedules.missingDepartures(serviceId)) {
                    try {
                        schedules.generate(serviceId, departure);
                    } catch (ResponseStatusException conflict) {
                        if (conflict.getStatusCode() != HttpStatus.CONFLICT) {
                            throw conflict;
                        }
                        log.warn("Operating schedule conflict for service {} on {} at {}: {}", serviceId,
                                departure.date(), departure.rule().startTime(), conflict.getReason());
                    }
                }
            } catch (RuntimeException failure) {
                log.error("Failed to refresh operating schedule for service {}", serviceId, failure);
            }
        }
    }
}
