package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.presentation.dtos.ConfirmRescheduleRequest;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VendorFaultCompensationIntegrationTest extends BaseE2ETest {
    @MockitoBean LettuceBasedProxyManager<byte[]> proxyManager;
    @Autowired DisruptionDecisionService decisions;
    @Autowired ConfirmSubOrderRescheduleUseCase reschedule;
    @Autowired JpaBookingRepository bookings;
    @Autowired JpaBookingItemRepository bookingItems;
    @Autowired JpaDiscountCodeRepository discounts;
    @Autowired JpaRefundRepository refunds;
    @Autowired JpaSettlementRepository settlements;
    @Autowired FinalizeSettlementUseCase finalizeSettlement;

    @Test
    void retryAndAcceptedRescheduleKeepOnePersonalVoucherAndRecoverOnlyTheOwedAmount() {
        var departure = OffsetDateTime.now(ZoneOffset.ofHours(7)).plusHours(48);
        slotA1TomorrowMorning.setDate(departure.toLocalDate());
        slotA1TomorrowMorning.setStartTime(departure.toLocalTime().withNano(0));
        slotA1TomorrowMorning.setBookedCount(2);
        serviceSlotRepository.saveAndFlush(slotA1TomorrowMorning);
        slotA1TomorrowAfternoon.setDate(departure.plusDays(1).toLocalDate());
        slotA1TomorrowAfternoon.setBookedCount(0);
        serviceSlotRepository.saveAndFlush(slotA1TomorrowAfternoon);
        attachBooking();

        var first = decisions.record(paidSubOrder.getId(), RefundReason.VENDOR_FAULT,
                "Operator cannot run the original departure", "vendor-fault-retry-01", vendorUserA.getId());
        var second = decisions.record(paidSubOrder.getId(), RefundReason.VENDOR_FAULT,
                "Operator cannot run the original departure", "vendor-fault-retry-01", vendorUserA.getId());
        assertThat(second.getId()).isEqualTo(first.getId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                customerUser.getId().toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        try {
            reschedule.execute(paidSubOrder.getId(),
                    new ConfirmRescheduleRequest(slotA1TomorrowAfternoon.getId(), null, 0L),
                    "compensated-reschedule-01");
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(subOrderRepository.findById(paidSubOrder.getId()).orElseThrow().getSlotId())
                .isEqualTo(slotA1TomorrowAfternoon.getId());
        assertThat(refunds.findBySubOrderId(paidSubOrder.getId())).isEmpty();

        var personal = discounts.findByRecipientCustomerId(customerUser.getId(), PageRequest.of(0, 20));
        assertThat(personal.getContent()).singleElement().satisfies(code -> {
            assertThat(code.getDiscountValue()).isEqualByComparingTo("90000.00");
            assertThat(code.getMinOrderAmount()).isEqualByComparingTo("90001.00");
            assertThat(code.getMaxUses()).isEqualTo(1);
            assertThat(code.getMaxUsesPerUser()).isEqualTo(1);
            assertThat(code.getReplacementSubOrderId()).isNull();
        });
        assertThat(discounts.findByRecipientCustomerId(attackerUser.getId(), PageRequest.of(0, 20)))
                .isEmpty();

        LocalDate day = departure.toLocalDate();
        assertThat(finalizeSettlement.execute(settlement(day, "50000").getId()).netPayableAmount())
                .isEqualByComparingTo("0.00");
        assertThat(finalizeSettlement.execute(settlement(day.plusDays(1), "100000").getId()).netPayableAmount())
                .isEqualByComparingTo("60000.00");
        assertThat(finalizeSettlement.execute(settlement(day.plusDays(2), "100000").getId()).netPayableAmount())
                .isEqualByComparingTo("100000.00");
    }

    @Test
    void weatherDisruptionDoesNotChargeVendorOrIssueGoodwill() {
        var departure = OffsetDateTime.now(ZoneOffset.ofHours(7)).plusHours(48);
        slotA1TomorrowMorning.setDate(departure.toLocalDate());
        slotA1TomorrowMorning.setStartTime(departure.toLocalTime().withNano(0));
        serviceSlotRepository.saveAndFlush(slotA1TomorrowMorning);
        decisions.record(paidSubOrder.getId(), RefundReason.WEATHER, "Unsafe sea conditions",
                "weather-exemption-01", adminUser.getId());

        assertThat(discounts.findByRecipientCustomerId(customerUser.getId(), PageRequest.of(0, 20)))
                .isEmpty();
        assertThat(finalizeSettlement.execute(settlement(departure.toLocalDate(), "100000").getId())
                .netPayableAmount()).isEqualByComparingTo("100000.00");
    }

    private void attachBooking() {
        var booking = new BookingJpaEntity();
        booking.setCustomerId(customerUser.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(paidOrder.getTotalAmount());
        booking = bookings.saveAndFlush(booking);
        var item = bookingItems.saveAndFlush(BookingItemJpaEntity.builder()
                .booking(booking)
                .slotId(slotA1TomorrowMorning.getId())
                .serviceId(serviceA1.getId())
                .vendorId(vendorProfileA.getId())
                .quantity(2)
                .pricingUnit("PER_PERSON")
                .bookingDate(slotA1TomorrowMorning.getDate())
                .bookingTime(slotA1TomorrowMorning.getStartTime())
                .price(paidSubOrder.getUnitPrice())
                .build());
        paidOrder.setBookingId(booking.getId());
        masterOrderRepository.saveAndFlush(paidOrder);
        paidSubOrder.setBookingItemId(item.getId());
        subOrderRepository.saveAndFlush(paidSubOrder);
    }

    private SettlementJpaEntity settlement(LocalDate day, String amount) {
        return settlements.saveAndFlush(SettlementJpaEntity.builder()
                .vendorId(vendorProfileA.getId())
                .periodStart(day)
                .periodEnd(day)
                .grossAmount(new BigDecimal(amount))
                .commissionAmount(BigDecimal.ZERO)
                .netPayableAmount(new BigDecimal(amount))
                .status(SettlementStatus.DRAFT)
                .generatedAt(OffsetDateTime.now())
                .build());
    }
}
