package com.danasea.backend.modules.order.infrastructure.jobs;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.order.application.services.ExchangeRateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Khởi tạo tỷ giá USD/VND an toàn khi ứng dụng khởi động.
 * Đảm bảo hệ thống có sẵn tỷ giá hợp lệ trong database ngay từ đầu mà không cần chờ đến lịch 20:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateStartupInitializer implements ApplicationRunner {

    private final ExchangeRateService exchangeRateService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking exchange rate status during application startup...");
        exchangeRateService.initializeRateOnStartup();
    }
}
