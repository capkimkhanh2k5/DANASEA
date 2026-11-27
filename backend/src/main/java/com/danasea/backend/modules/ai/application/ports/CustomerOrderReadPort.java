package com.danasea.backend.modules.ai.application.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

public interface CustomerOrderReadPort {
    record Item(
            UUID id,
            String subOrderCode,
            UUID serviceId,
            String serviceName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            String status,
            LocalDate slotDate,
            LocalTime slotStartTime,
            LocalTime slotEndTime,
            String meetingLocation,
            OffsetDateTime checkedInAt
    ) {
        public Item(UUID id, UUID serviceId, int quantity, BigDecimal unitPrice, BigDecimal subtotal, String status) {
            this(id, DisplayCodeFormatter.format(id, "SUB"), serviceId, null, quantity, unitPrice, subtotal,
                    status, null, null, null, null, null);
        }
    }

    record OwnedOrder(
            UUID id,
            String orderCode,
            String status,
            String paymentStatus,
            BigDecimal totalAmount,
            OffsetDateTime createdAt,
            List<Item> items
    ) {
        public OwnedOrder(UUID id, String status, String paymentStatus, BigDecimal totalAmount, List<Item> items) {
            this(id, DisplayCodeFormatter.format(id, "ORD"), status, paymentStatus, totalAmount, null, items);
        }
    }

    OwnedOrder read(UUID userId, UUID orderId);
    CancellationPreviewResult previewCancellation(UUID userId, UUID orderId);

    default List<OwnedOrder> listRecent(UUID userId, int limit) {
        return List.of();
    }

    default void validateChangeTarget(UUID userId, UUID orderId, UUID slotId, LocalDate requestedDate) {
        if (slotId != null) throw new IllegalArgumentException("Slot change preview is unavailable");
    }

    record Eligibility(boolean refundRequestAvailable, boolean changeRequestAvailable, String source, List<String> reasonCodes) {}

    default Eligibility eligibility(UUID userId, UUID orderId, CancellationPreviewResult preview) {
        return new Eligibility(false, false, "UNAVAILABLE", List.of("ELIGIBILITY_NOT_VERIFIED"));
    }
}
