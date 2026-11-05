package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "discount_codes")
public class DiscountCodeJpaEntity extends BaseJpaEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsor_type", nullable = false)
    private DiscountSponsorType sponsorType = DiscountSponsorType.PLATFORM;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "recipient_customer_id")
    private UUID recipientCustomerId;

    @Column(name = "replacement_sub_order_id")
    private UUID replacementSubOrderId;

    @Column(name = "compensation_disruption_id")
    private UUID compensationDisruptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count")
    private Integer usedCount = 0;

    @Column(name = "max_uses_per_user")
    private Integer maxUsesPerUser;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Column(name = "is_active")
    private Boolean isActive = true;

}
