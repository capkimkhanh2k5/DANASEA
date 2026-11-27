package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.shared.util.DisplayCodeFormatter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "master_orders")
public class MasterOrderJpaEntity extends BaseJpaEntity {

    @Column(name = "booking_id")
    private UUID bookingId;

    private UUID customerId;

    @Column(name = "order_code", length = 24)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    private MasterOrderStatus status;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private UUID discountCodeId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "payment_deadline")
    private OffsetDateTime paymentDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 50)
    private PaymentOrderStatus paymentStatus = PaymentOrderStatus.UNPAID;

    @PrePersist
    private void ensureOrderCode() {
        if (getId() == null) {
            setId(UUID.randomUUID());
            markNew();
        }
        if ((orderCode == null || orderCode.isBlank()) && getId() != null) {
            orderCode = DisplayCodeFormatter.format(getId(), "ORD");
        }
    }
}
