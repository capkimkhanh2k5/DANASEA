package com.danasea.backend.modules.order.application.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.ExchangeRateProperties;
import com.danasea.backend.modules.order.domain.exceptions.ExchangeRateUnavailableException;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.ExchangeRateJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaExchangeRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FrankfurterExchangeRateService implements ExchangeRateService {

    private static final String SOURCE = "FRANKFURTER";
    private static final String BASE_CURRENCY = "USD";
    private static final String TARGET_CURRENCY = "VND";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FLAGGED_VOLATILITY = "FLAGGED_VOLATILITY";

    private final JpaExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<ExchangeRateSnapshot> cachedRate = new AtomicReference<>();

    public FrankfurterExchangeRateService(
            JpaExchangeRateRepository exchangeRateRepository,
            ExchangeRateProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;

        this.restClient = restClientBuilder.build();
    }

    @Override
    public ExchangeRateSnapshot getCurrentUsdToVndRate() {
        LocalDate today = LocalDate.now(ZoneId.of(properties.zone()));
        ExchangeRateSnapshot cached = cachedRate.get();
        if (cached != null && isFresh(cached.effectiveDate(), today)) {
            return cached;
        }

        Optional<ExchangeRateJpaEntity> activeOpt =
                exchangeRateRepository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc(
                        BASE_CURRENCY, TARGET_CURRENCY, STATUS_ACTIVE);

        if (activeOpt.isEmpty()) {
            throw new ExchangeRateUnavailableException(
                    "No exchange rate available for USD/VND. PayPal checkout is currently unavailable.");
        }

        ExchangeRateJpaEntity entity = activeOpt.get();
        if (!isFresh(entity.getEffectiveDate(), today)) {
            log.error("Exchange rate USD/VND is stale: effectiveDate={}, today={}, maxAgeDays={}",
                    entity.getEffectiveDate(), today, properties.maxAgeDays());
            throw new ExchangeRateUnavailableException(
                    "Exchange rate USD/VND is stale (last effective: " + entity.getEffectiveDate()
                            + "). PayPal checkout is temporarily suspended.");
        }

        ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot(entity.getRate(), entity.getEffectiveDate(), entity.getSource());
        cachedRate.set(snapshot);
        return snapshot;
    }

    @Override
    public ExchangeRateSnapshot syncRateFromFrankfurter() {
        log.info("Fetching USD/VND exchange rate from Frankfurter v2 API: {}", properties.apiUrl());
        try {
            String responseBody = restClient.get()
                    .uri(properties.apiUrl())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            validateResponse(root);

            BigDecimal fetchedRate = new BigDecimal(root.path("rate").asText());
            LocalDate effectiveDate = LocalDate.parse(root.path("date").asText());

            Optional<ExchangeRateJpaEntity> latestActiveOpt =
                    exchangeRateRepository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc(
                            BASE_CURRENCY, TARGET_CURRENCY, STATUS_ACTIVE);

            if (latestActiveOpt.isPresent()) {
                BigDecimal previousRate = latestActiveOpt.get().getRate();
                BigDecimal diff = fetchedRate.subtract(previousRate).abs();
                BigDecimal changeRatio = diff.divide(previousRate, 6, RoundingMode.HALF_UP);

                if (changeRatio.compareTo(properties.volatilityThreshold()) > 0) {
                    log.warn("Exchange rate volatility alert: rate shifted by {}% (from {} to {}), exceeding threshold {}%. Rate flagged as FLAGGED_VOLATILITY and not activated.",
                            changeRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                            previousRate, fetchedRate,
                            properties.volatilityThreshold().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

                    saveOrUpdate(effectiveDate, fetchedRate, STATUS_FLAGGED_VOLATILITY);
                    return new ExchangeRateSnapshot(previousRate, latestActiveOpt.get().getEffectiveDate(), latestActiveOpt.get().getSource());
                }
            }

            saveOrUpdate(effectiveDate, fetchedRate, STATUS_ACTIVE);
            ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot(fetchedRate, effectiveDate, SOURCE);
            cachedRate.set(snapshot);
            log.info("Successfully updated USD/VND exchange rate to {} for effective date {}", fetchedRate, effectiveDate);
            return snapshot;
        } catch (Exception ex) {
            log.error("Failed to sync USD/VND exchange rate from Frankfurter: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to sync exchange rate from Frankfurter: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void initializeRateOnStartup() {
        try {
            LocalDate today = LocalDate.now(ZoneId.of(properties.zone()));
            Optional<ExchangeRateJpaEntity> activeOpt =
                    exchangeRateRepository.findTopByBaseCurrencyAndTargetCurrencyAndStatusOrderByEffectiveDateDesc(
                            BASE_CURRENCY, TARGET_CURRENCY, STATUS_ACTIVE);

            if (activeOpt.isPresent() && isFresh(activeOpt.get().getEffectiveDate(), today)) {
                ExchangeRateJpaEntity entity = activeOpt.get();
                cachedRate.set(new ExchangeRateSnapshot(entity.getRate(), entity.getEffectiveDate(), entity.getSource()));
                log.info("Loaded active exchange rate on startup: {} VND/USD (effective: {})",
                        entity.getRate(), entity.getEffectiveDate());
                return;
            }

            log.info("No fresh exchange rate found in database on startup. Initiating sync from Frankfurter...");
            syncRateFromFrankfurter();
        } catch (Exception ex) {
            log.warn("Initial exchange rate sync failed during startup: {}. PayPal checkout will remain suspended until a valid rate is available.",
                    ex.getMessage());
        }
    }

    @Transactional
    public ExchangeRateJpaEntity saveOrUpdate(LocalDate effectiveDate, BigDecimal rate, String status) {
        Optional<ExchangeRateJpaEntity> existingOpt =
                exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndEffectiveDate(
                        BASE_CURRENCY, TARGET_CURRENCY, effectiveDate);

        ExchangeRateJpaEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setRate(rate);
            entity.setStatus(status);
            entity.setSource(SOURCE);
            entity.setFetchedAt(OffsetDateTime.now());
        } else {
            entity = new ExchangeRateJpaEntity();
            entity.setBaseCurrency(BASE_CURRENCY);
            entity.setTargetCurrency(TARGET_CURRENCY);
            entity.setRate(rate);
            entity.setEffectiveDate(effectiveDate);
            entity.setSource(SOURCE);
            entity.setStatus(status);
            entity.setFetchedAt(OffsetDateTime.now());
        }
        return exchangeRateRepository.save(entity);
    }

    private void validateResponse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Frankfurter response is not a valid JSON object.");
        }
        String base = root.path("base").asText("");
        String quote = root.path("quote").asText("");
        if (!BASE_CURRENCY.equalsIgnoreCase(base) || !TARGET_CURRENCY.equalsIgnoreCase(quote)) {
            throw new IllegalArgumentException("Unexpected currency pair received: base=" + base + ", quote=" + quote);
        }
        if (!root.hasNonNull("rate")) {
            throw new IllegalArgumentException("Missing 'rate' in Frankfurter response.");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(root.path("rate").asText());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric rate in Frankfurter response.", ex);
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Non-positive rate in Frankfurter response: " + rate);
        }
        String dateStr = root.path("date").asText(null);
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("Missing 'date' in Frankfurter response.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid date format in Frankfurter response: " + dateStr, ex);
        }
        LocalDate today = LocalDate.now(ZoneId.of(properties.zone()));
        if (date.isAfter(today.plusDays(1))) {
            throw new IllegalArgumentException("Effective date cannot be in the future: " + date);
        }
    }

    private boolean isFresh(LocalDate effectiveDate, LocalDate today) {
        if (effectiveDate == null) {
            return false;
        }
        if (effectiveDate.isAfter(today.plusDays(1))) {
            return false;
        }
        return !effectiveDate.isBefore(today.minusDays(properties.maxAgeDays()));
    }
}
