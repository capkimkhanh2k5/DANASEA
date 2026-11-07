package com.danasea.backend.modules.order.application.services;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {

    /**
     * Lấy tỷ giá USD/VND hiện hành để tính tiền checkout PayPal.
     * Tỷ giá được đọc từ cache/database đã lưu, không gọi API ngoài lúc khách thanh toán.
     * Nếu tỷ giá quá hạn (stale) hoặc chưa có, ném ExchangeRateUnavailableException.
     */
    ExchangeRateSnapshot getCurrentUsdToVndRate();

    /**
     * Đồng bộ tỷ giá mới nhất từ Frankfurter v2 API, thực hiện kiểm tra biến động (>3%)
     * và lưu vào database.
     */
    ExchangeRateSnapshot syncRateFromFrankfurter();

    /**
     * Khởi tạo an toàn khi khởi động hệ thống: kiểm tra nếu DB chưa có tỷ giá hợp lệ
     * thì đồng bộ ngay, không phải chờ đến lịch scheduler buổi tối.
     */
    void initializeRateOnStartup();

    record ExchangeRateSnapshot(
            BigDecimal rate,
            LocalDate effectiveDate,
            String source
    ) {}
}
