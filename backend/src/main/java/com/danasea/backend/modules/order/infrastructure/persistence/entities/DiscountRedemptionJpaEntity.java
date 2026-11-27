package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "discount_redemptions")
public class DiscountRedemptionJpaEntity extends BaseJpaEntity {

    @Column(name = "discount_code_id", nullable = false)
    private UUID discountCodeId;

    @Column(name = "master_order_id", nullable = false)
    private UUID masterOrderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "amount_deducted", nullable = false)
    private BigDecimal amountDeducted;

}
