package com.danasea.backend.modules.order.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.ExchangeRateProperties;
import com.danasea.backend.modules.order.domain.exceptions.ExchangeRateUnavailableException;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.ExchangeRateJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaExchangeRateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("FrankfurterExchangeRateService Unit Tests")
class FrankfurterExchangeRateServiceTest {

    private static final String API_URL = "https://api.frankfurter.dev/v2/rate/usd/vnd";

    @Mock
    private JpaExchangeRateRepository repository;

    private MockRestServiceServer server;
    private FrankfurterExchangeRateService service;
    private ExchangeRateProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        properties = new ExchangeRateProperties(
                API_URL,
                "0 0 20 * * *",
                "Asia/Ho_Chi_Minh",
                3,
                new BigDecimal("0.03"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );

        service = new FrankfurterExchangeRateService(repository, properties, builder, new ObjectMapper());
    }

    @Test
    @DisplayName("Successfully syncs rate from Frankfurter v2 when volatility is within 3%")
    void syncRateSuccess() {
        String json = "{\"date\":\"2026-11-05\",\"base\":\"USD\",\"quote\":\"VND\",\"rate\":25709}";

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ExchangeRateJpaEntity previous = new ExchangeRateJpaEntity();
        previous.setRate(new BigDecimal("25600"));
        previous.setEffectiveDate(LocalDate.of(2026, 11, 4));
        previous.setStatus("ACTIVE");
        previous.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(previous));
        when(repository.findByBaseCurrencyAndTargetCurrencyAndEffectiveDate("USD", "VND", LocalDate.of(2026, 11, 5)))
                .thenReturn(Optional.empty());
        when(repository.save(any(ExchangeRateJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var snapshot = service.syncRateFromFrankfurter();

        server.verify();
        assertThat(snapshot.rate()).isEqualByComparingTo(new BigDecimal("25709"));
        assertThat(snapshot.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(snapshot.source()).isEqualTo("FRANKFURTER");

        ArgumentCaptor<ExchangeRateJpaEntity> captor = ArgumentCaptor.forClass(ExchangeRateJpaEntity.class);
        verify(repository).save(captor.capture());
        ExchangeRateJpaEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getRate()).isEqualByComparingTo(new BigDecimal("25709"));
    }

    @Test
    @DisplayName("Volatility guard triggers when rate shifts by > 3%: flags as FLAGGED_VOLATILITY and preserves previous active rate")
    void volatilityGuardTriggers() {
        // Shift from 25000 to 26000 is 4.0%, exceeding 3% threshold
        String json = "{\"date\":\"2026-11-05\",\"base\":\"USD\",\"quote\":\"VND\",\"rate\":26000}";

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ExchangeRateJpaEntity previous = new ExchangeRateJpaEntity();
        previous.setRate(new BigDecimal("25000"));
        previous.setEffectiveDate(LocalDate.of(2026, 11, 4));
        previous.setStatus("ACTIVE");
        previous.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(previous));
        when(repository.findByBaseCurrencyAndTargetCurrencyAndEffectiveDate("USD", "VND", LocalDate.of(2026, 11, 5)))
                .thenReturn(Optional.empty());
        when(repository.save(any(ExchangeRateJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var snapshot = service.syncRateFromFrankfurter();

        server.verify();
        // Returned snapshot should keep the previous approved rate
        assertThat(snapshot.rate()).isEqualByComparingTo(new BigDecimal("25000"));

        ArgumentCaptor<ExchangeRateJpaEntity> captor = ArgumentCaptor.forClass(ExchangeRateJpaEntity.class);
        verify(repository).save(captor.capture());
        ExchangeRateJpaEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("FLAGGED_VOLATILITY");
        assertThat(saved.getRate()).isEqualByComparingTo(new BigDecimal("26000"));
    }

    @Test
    @DisplayName("Updates existing entity when syncing same date (e.g., weekend or retry) without duplicate error")
    void updatesExistingEntityForSameDate() {
        String json = "{\"date\":\"2026-11-05\",\"base\":\"USD\",\"quote\":\"VND\",\"rate\":25709}";

        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ExchangeRateJpaEntity existing = new ExchangeRateJpaEntity();
        existing.setRate(new BigDecimal("25700"));
        existing.setEffectiveDate(LocalDate.of(2026, 11, 5));
        existing.setStatus("ACTIVE");
        existing.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(existing));
        when(repository.findByBaseCurrencyAndTargetCurrencyAndEffectiveDate("USD", "VND", LocalDate.of(2026, 11, 5)))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ExchangeRateJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var snapshot = service.syncRateFromFrankfurter();

        server.verify();
        assertThat(snapshot.rate()).isEqualByComparingTo(new BigDecimal("25709"));
        assertThat(existing.getRate()).isEqualByComparingTo(new BigDecimal("25709"));
    }

    @Test
    @DisplayName("Sync fails when Frankfurter API returns HTTP 500 error")
    void syncFailsOnHttpError() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.syncRateFromFrankfurter())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to sync exchange rate");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sync fails when API returns non-positive rate")
    void syncFailsOnNonPositiveRate() {
        String json = "{\"date\":\"2026-11-05\",\"base\":\"USD\",\"quote\":\"VND\",\"rate\":0}";

        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.syncRateFromFrankfurter())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Non-positive rate");
    }

    @Test
    @DisplayName("Sync fails when API returns wrong currency pair")
    void syncFailsOnWrongCurrency() {
        String json = "{\"date\":\"2026-11-05\",\"base\":\"EUR\",\"quote\":\"VND\",\"rate\":28000}";

        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.syncRateFromFrankfurter())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected currency pair");
    }

    @Test
    @DisplayName("getCurrentUsdToVndRate returns rate from DB when fresh and not cached")
    void getCurrentRateLoadsFromDbWhenFresh() {
        ExchangeRateJpaEntity active = new ExchangeRateJpaEntity();
        active.setRate(new BigDecimal("25709"));
        active.setEffectiveDate(LocalDate.now());
        active.setStatus("ACTIVE");
        active.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(active));

        var snapshot = service.getCurrentUsdToVndRate();
        assertThat(snapshot.rate()).isEqualByComparingTo(new BigDecimal("25709"));

        // Second call should hit the in-memory cache without hitting DB again
        var second = service.getCurrentUsdToVndRate();
        assertThat(second.rate()).isEqualByComparingTo(new BigDecimal("25709"));
        verify(repository, times(1)).findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc(any(), any(), any());
    }

    @Test
    @DisplayName("getCurrentUsdToVndRate throws ExchangeRateUnavailableException when no rate exists in DB")
    void getCurrentRateThrowsWhenNoRateInDb() {
        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUsdToVndRate())
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("No exchange rate available");
    }

    @Test
    @DisplayName("getCurrentUsdToVndRate throws ExchangeRateUnavailableException when latest rate is older than maxAgeDays (3 days)")
    void getCurrentRateThrowsWhenRateIsStale() {
        ExchangeRateJpaEntity stale = new ExchangeRateJpaEntity();
        stale.setRate(new BigDecimal("25500"));
        stale.setEffectiveDate(LocalDate.now().minusDays(5));
        stale.setStatus("ACTIVE");
        stale.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service.getCurrentUsdToVndRate())
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("is stale");
    }

    @Test
    @DisplayName("initializeRateOnStartup loads from DB if fresh rate exists")
    void initializeOnStartupLoadsFromDb() {
        ExchangeRateJpaEntity fresh = new ExchangeRateJpaEntity();
        fresh.setRate(new BigDecimal("25709"));
        fresh.setEffectiveDate(LocalDate.now());
        fresh.setStatus("ACTIVE");
        fresh.setSource("FRANKFURTER");

        when(repository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc("USD", "VND", "ACTIVE"))
                .thenReturn(Optional.of(fresh));

        service.initializeRateOnStartup();

        // Should be cached now
        var snapshot = service.getCurrentUsdToVndRate();
        assertThat(snapshot.rate()).isEqualByComparingTo(new BigDecimal("25709"));
    }
}
