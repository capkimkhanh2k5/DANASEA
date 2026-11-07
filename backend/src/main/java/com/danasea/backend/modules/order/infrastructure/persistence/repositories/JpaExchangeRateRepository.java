package com.danasea.backend.modules.order.infrastructure.persistence.repositories;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.order.infrastructure.persistence.entities.ExchangeRateJpaEntity;

@Repository
public interface JpaExchangeRateRepository extends JpaRepository<ExchangeRateJpaEntity, UUID> {

    Optional<ExchangeRateJpaEntity> findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc(
            String baseCurrency, String targetCurrency, String status);

    Optional<ExchangeRateJpaEntity> findTopByBaseCurrencyAndTargetCurrencyOrderByEffectiveDateDesc(
            String baseCurrency, String targetCurrency);

    Optional<ExchangeRateJpaEntity> findByBaseCurrencyAndTargetCurrencyAndEffectiveDate(
            String baseCurrency, String targetCurrency, LocalDate effectiveDate);
}
