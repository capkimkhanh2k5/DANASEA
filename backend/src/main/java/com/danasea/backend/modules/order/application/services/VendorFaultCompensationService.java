package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VendorFaultCompensationService {

    private final JdbcTemplate jdbc;
    private final JpaDiscountCodeRepository discounts;
    private final SendNotificationUseCase notifications;
    private final int basisPoints;
    private final int validityDays;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    public VendorFaultCompensationService(
            JdbcTemplate jdbc,
            JpaDiscountCodeRepository discounts,
            SendNotificationUseCase notifications,
            @Value("${app.refund.vendor-fault-compensation-bps:1000}") int basisPoints,
            @Value("${app.refund.vendor-fault-compensation-valid-days:30}") int validityDays) {
        if (basisPoints < 0 || basisPoints > 10_000 || validityDays <= 0) {
            throw new IllegalArgumentException("Vendor fault compensation requires 0–10000 basis points and positive validity days.");
        }
        this.jdbc = jdbc;
        this.discounts = discounts;
        this.notifications = notifications;
        this.basisPoints = basisPoints;
        this.validityDays = validityDays;
    }

    /** Caller has verified the disruption and holds the master order and sub-order locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(SubOrderDisruptionJpaEntity disruption, RescheduleSupport.LockedOrder locked) {
        if (basisPoints == 0 || disruption.getReason() != RefundReason.VENDOR_FAULT) return;

        var sub = locked.sub();
        var paidBasis = sub.getFinalAmount();
        if (paidBasis == null || paidBasis.signum() <= 0) return;
        var amount = paidBasis.multiply(BigDecimal.valueOf(basisPoints))
                .movePointLeft(4).setScale(0, RoundingMode.DOWN).setScale(2);
        if (amount.signum() <= 0 || Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM vendor_fault_compensations
                               WHERE sub_order_id = ? AND original_slot_id = ?)
                """, Boolean.class, sub.getId(), disruption.getOriginalSlotId()))) return;

        UUID customerId = locked.master().getCustomerId();
        var now = OffsetDateTime.now();
        var voucher = new DiscountCodeJpaEntity();
        voucher.setCode("VC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        voucher.setScope(DiscountScope.PLATFORM);
        voucher.setSponsorType(DiscountSponsorType.PLATFORM);
        voucher.setRecipientCustomerId(customerId);
        voucher.setCompensationDisruptionId(disruption.getId());
        voucher.setDiscountType(DiscountType.FIXED);
        voucher.setDiscountValue(amount);
        // Checkout requires a positive payment, and a single-use voucher must redeem its full value.
        voucher.setMinOrderAmount(amount.add(BigDecimal.ONE));
        voucher.setMaxUses(1);
        voucher.setMaxUsesPerUser(1);
        voucher.setValidFrom(now);
        voucher.setValidTo(now.plusDays(validityDays));
        voucher = discounts.saveAndFlush(voucher);

        jdbc.update("""
                INSERT INTO vendor_fault_compensations
                    (disruption_id, sub_order_id, original_slot_id, vendor_id, voucher_id,
                     paid_basis, rate_bps, amount, remaining_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, disruption.getId(), sub.getId(), disruption.getOriginalSlotId(), sub.getVendorId(),
                voucher.getId(), paidBasis, basisPoints, amount, amount);

        notifications.executeOnce(
                new NotificationCommand(customerId, "DISRUPTION_VENDOR_COMPENSATION", NotificationChannel.IN_APP,
                        LocalizedMessageRef.of("notification.disruption.vendor_compensation.title"),
                        LocalizedMessageRef.of("notification.disruption.vendor_compensation.body", sub.getId()),
                        "SUB_ORDER", sub.getId(), null),
                "vendor-fault-compensation:" + disruption.getId());

        if (transactionalEmailPort != null) {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.PERSONAL_VOUCHER_ISSUED,
                    "voucher:" + voucher.getId(),
                    "USER:" + customerId,
                    now,
                    now,
                    null,
                    1,
                    Map.of(
                            "voucherId", voucher.getId().toString(),
                            "code", voucher.getCode(),
                            "amount", voucher.getDiscountValue() != null ? voucher.getDiscountValue().doubleValue() : 0.0
                    )
            ));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal recoverAtFinalization(UUID vendorId, UUID settlementId, BigDecimal payable) {
        if (payable == null || payable.signum() <= 0) return BigDecimal.ZERO;

        var outstanding = jdbc.query("""
                SELECT disruption_id, remaining_amount FROM vendor_fault_compensations
                WHERE vendor_id = ? AND remaining_amount > 0
                ORDER BY created_at, disruption_id
                FOR UPDATE
                """, (row, i) -> new Balance(row.getObject(1, UUID.class), row.getBigDecimal(2)), vendorId);
        var remainingPayable = payable;
        for (var balance : outstanding) {
            if (remainingPayable.signum() <= 0) break;
            var recovered = balance.remainingAmount().min(remainingPayable);
            jdbc.update("""
                    INSERT INTO vendor_fault_compensation_recoveries (disruption_id, settlement_id, amount)
                    VALUES (?, ?, ?)
                    """, balance.disruptionId(), settlementId, recovered);
            jdbc.update("""
                    UPDATE vendor_fault_compensations SET remaining_amount = remaining_amount - ?
                    WHERE disruption_id = ?
                    """, recovered, balance.disruptionId());
            remainingPayable = remainingPayable.subtract(recovered);
        }
        return payable.subtract(remainingPayable);
    }

    private record Balance(UUID disruptionId, BigDecimal remainingAmount) { }
}
