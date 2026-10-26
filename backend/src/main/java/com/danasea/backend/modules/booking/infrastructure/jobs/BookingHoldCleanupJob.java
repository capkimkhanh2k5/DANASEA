package com.danasea.backend.modules.booking.infrastructure.jobs;

import com.danasea.backend.modules.booking.application.usecases.ExpireBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingHoldCleanupJob {
    private final BookingRepositoryPort bookingRepository;
    private final ExpireBookingHoldUseCase expire;
    @Scheduled(fixedDelayString="${app.scheduler.booking-cleanup-delay:1m}")
    public void cleanExpiredHolds() {
        var now=OffsetDateTime.now();
        var candidates=bookingRepository.findExpiredHolds(now);
        if(candidates==null) return;
        for(var booking:candidates) {
            try { expire.execute(booking.getId(),now); }
            catch(Exception e) { log.error("Failed to expire booking {}",booking.getId(),e); }
        }
    }
}
