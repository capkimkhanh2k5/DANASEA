package com.danasea.backend.modules.booking.infrastructure.jobs;

import static org.mockito.Mockito.*;
import com.danasea.backend.modules.booking.application.usecases.ExpireBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import java.util.*;
import org.junit.jupiter.api.Test;

class BookingHoldCleanupJobTest {
    @Test void delegatesEachCandidateAndContinuesAfterFailure() {
        var repo=mock(BookingRepositoryPort.class); var expire=mock(ExpireBookingHoldUseCase.class);
        var first=Booking.builder().id(UUID.randomUUID()).build();
        var second=Booking.builder().id(UUID.randomUUID()).build();
        when(repo.findExpiredHolds(any())).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("DB unavailable")).when(expire).execute(eq(first.getId()),any());
        new BookingHoldCleanupJob(repo,expire).cleanExpiredHolds();
        verify(expire).execute(eq(first.getId()),any()); verify(expire).execute(eq(second.getId()),any());
        verify(repo,never()).save(any());
    }
    @Test void noCandidatesDoNotMutateState() {
        var repo=mock(BookingRepositoryPort.class); var expire=mock(ExpireBookingHoldUseCase.class);
        when(repo.findExpiredHolds(any())).thenReturn(List.of());
        new BookingHoldCleanupJob(repo,expire).cleanExpiredHolds(); verifyNoInteractions(expire);
    }
}
