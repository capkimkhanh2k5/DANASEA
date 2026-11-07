package com.danasea.backend.modules.order.infrastructure.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.order.application.services.ExchangeRateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler tự động cập nhật tỷ giá USD/VND từ Frankfurter v2 mỗi ngày vào 20:00 giờ Việt Nam (Asia/Ho_Chi_Minh).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateScheduler {

    private final ExchangeRateService exchangeRateService;

    @Scheduled(cron = "${app.payment.paypal.exchange-rate.cron:0 0 20 * * *}", zone = "${app.payment.paypal.exchange-rate.zone:Asia/Ho_Chi_Minh}")
    public void syncDailyExchangeRate() {
        log.info("Starting scheduled daily exchange rate sync from Frankfurter API (20:00 Asia/Ho_Chi_Minh)...");
        try {
            var snapshot = exchangeRateService.syncRateFromFrankfurter();
            log.info("Scheduled exchange rate sync completed successfully: {} VND/USD (effective: {})",
                    snapshot.rate(), snapshot.effectiveDate());
        } catch (Exception ex) {
            log.error("Scheduled daily exchange rate sync failed: {}", ex.getMessage(), ex);
        }
    }
}
