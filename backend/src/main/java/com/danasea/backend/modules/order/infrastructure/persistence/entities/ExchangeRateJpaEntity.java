package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_exchange_rate_daily", columnNames = {"base_currency", "target_currency", "effective_date"})
})
@Getter
@Setter
@NoArgsConstructor
public class ExchangeRateJpaEntity extends BaseJpaEntity {

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Column(name = "target_currency", length = 3, nullable = false)
    private String targetCurrency;

    @Column(precision = 15, scale = 6, nullable = false)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(length = 50, nullable = false)
    private String source;

    @Column(length = 30, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
