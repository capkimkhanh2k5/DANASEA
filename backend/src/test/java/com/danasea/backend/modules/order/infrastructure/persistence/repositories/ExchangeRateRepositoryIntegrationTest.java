package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.order.infrastructure.persistence.entities.ExchangeRateJpaEntity;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
@DisplayName("ExchangeRateRepository PostgreSQL Integration Test")
class ExchangeRateRepositoryIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private JpaExchangeRateRepository repository;

    @Test
    @DisplayName("Saves and retrieves USD/VND exchange rate and enforces unique daily constraint")
    void savesAndRetrievesExchangeRate() {
        LocalDate testDate = LocalDate.of(2028, 1, 1);
        ExchangeRateJpaEntity entity = new ExchangeRateJpaEntity();
        entity.setBaseCurrency("USD");
        entity.setTargetCurrency("VND");
        entity.setRate(new BigDecimal("25709.000000"));
        entity.setEffectiveDate(testDate);
        entity.setSource("FRANKFURTER");
        entity.setStatus("ACTIVE");
        entity.setFetchedAt(OffsetDateTime.now());

        ExchangeRateJpaEntity saved = repository.saveAndFlush(entity);
        assertThat(saved.getId()).isNotNull();

        Optional<ExchangeRateJpaEntity> loaded =
                repository.findByBaseCurrencyAndTargetCurrencyAndEffectiveDate("USD", "VND", testDate);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getRate()).isEqualByComparingTo(new BigDecimal("25709.000000"));
        assertThat(loaded.get().getSource()).isEqualTo("FRANKFURTER");

        // Enforce uniqueness constraint for same (base_currency, target_currency, effective_date)
        ExchangeRateJpaEntity duplicate = new ExchangeRateJpaEntity();
        duplicate.setBaseCurrency("USD");
        duplicate.setTargetCurrency("VND");
        duplicate.setRate(new BigDecimal("25800.000000"));
        duplicate.setEffectiveDate(testDate);
        duplicate.setSource("FRANKFURTER");
        duplicate.setStatus("ACTIVE");
        duplicate.setFetchedAt(OffsetDateTime.now());

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
