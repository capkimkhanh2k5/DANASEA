package com.danasea.backend.modules.order.infrastructure.jobs;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.order.application.services.ExchangeRateService;
import com.danasea.backend.modules.order.application.services.ExchangeRateService.ExchangeRateSnapshot;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateScheduler Unit Tests")
class ExchangeRateSchedulerTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private ExchangeRateScheduler scheduler;

    @Test
    @DisplayName("Scheduled job triggers exchangeRateService.syncRateFromFrankfurter()")
    void scheduleTriggersSync() {
        when(exchangeRateService.syncRateFromFrankfurter())
                .thenReturn(new ExchangeRateSnapshot(new BigDecimal("25709"), LocalDate.now(), "FRANKFURTER"));

        scheduler.syncDailyExchangeRate();

        verify(exchangeRateService).syncRateFromFrankfurter();
    }

    @Test
    @DisplayName("Scheduled job handles failure gracefully without throwing exception out of scheduled method")
    void scheduleHandlesExceptionGracefully() {
        doThrow(new IllegalStateException("Network timeout"))
                .when(exchangeRateService).syncRateFromFrankfurter();

        // Should not throw
        scheduler.syncDailyExchangeRate();

        verify(exchangeRateService).syncRateFromFrankfurter();
    }
}
