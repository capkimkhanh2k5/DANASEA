package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.PaymentWebhookSigner;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.VendorDepartureCancellation;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VendorDepartureCancellationIntegrationTest extends BaseE2ETest {
    @MockitoBean LettuceBasedProxyManager<byte[]> proxyManager;
    @Autowired VendorDepartureCancellation cancellations;
    @Autowired DisruptionDecisionService decisions;
    @Autowired JpaSubOrderDisruptionRepository disruptions;
    @Autowired JpaBookingRepository bookings;
    @Autowired JpaRefundRepository refunds;
    @Autowired OrderPaymentService payments;
    @Autowired PaymentWebhookSigner signer;

    @Test
    void cancellationIsOwnedAndRegistersOneDecisionEvenWhenProcessedAgain() throws Exception {
        var slotId = slotA1TomorrowMorning.getId();
        var serviceId = serviceA1.getId();
        var path = "/api/vendor/services/" + serviceId + "/slots/" + slotId + "/cancel";
        mockMvc.perform(post(path).header("Authorization", "Bearer " + vendorBToken)
                        .contentType("application/json").content("{\"reason\":\"Engine failure\"}"))
                .andExpect(status().isForbidden());

        var first = cancellations.cancel(vendorUserA.getId(), serviceId, slotId, "Engine failure");
        assertThat(cancellations.cancel(vendorUserA.getId(), serviceId, slotId, "Engine failure").cancelledAt().toInstant())
                .isEqualTo(first.cancelledAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThatThrownBy(() -> cancellations.cancel(vendorUserA.getId(), serviceId, slotId, "Different"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        cancellations.process();
        cancellations.process();

        assertThat(serviceSlotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.CANCELLED);
        assertThat(disruptions.findBySubOrderIdAndSourceKey(paidSubOrder.getId(), "vendor-departure:" + slotId))
                .hasValueSatisfying(decision -> assertThat(decision.getReason()).isEqualTo(RefundReason.VENDOR_FAULT));
    }

    @Test
    void customerCanChooseFullRefundImmediatelyAfterVendorCancels() {
        var slotId = slotA1TomorrowMorning.getId();
        cancellations.cancel(vendorUserA.getId(), serviceA1.getId(), slotId, "Engine failure");

        assertThat(decisions.verifiedReason(paidSubOrder.getId(), slotId)).contains(RefundReason.VENDOR_FAULT);
        decisions.chooseRefund(paidSubOrder.getId(), customerUser.getId());
        cancellations.process();

        assertThat(refunds.findBySubOrderId(paidSubOrder.getId())).singleElement().satisfies(refund -> {
            assertThat(refund.getReason()).isEqualTo(RefundReason.VENDOR_FAULT);
            assertThat(refund.getAmount()).isEqualByComparingTo(paidSubOrder.getFinalAmount());
        });
    }

    @Test
    void existingRefundIsReportedForReviewWithoutSubmittingASecondRequest() {
        var refund = new RefundJpaEntity();
        refund.setSubOrderId(paidSubOrder.getId());
        refund.setAmount(BigDecimal.valueOf(100000));
        refund.setRefundPercentage(BigDecimal.TEN);
        refund.setReason(RefundReason.CUSTOMER_CANCEL);
        refund.setStatus(RefundStatus.FAILED);
        refunds.saveAndFlush(refund);
        var slotId = slotA1TomorrowMorning.getId();
        cancellations.cancel(vendorUserA.getId(), serviceA1.getId(), slotId, "Engine failure");
        cancellations.process();

        assertThat(cancellations.status(vendorUserA.getId(), serviceA1.getId(), slotId).existingRefundsToReview())
                .containsExactly(new VendorDepartureCancellation.RefundReview(paidSubOrder.getId(), refund.getId(), "FAILED"));
        assertThat(refunds.findBySubOrderId(paidSubOrder.getId())).hasSize(1);
    }

    @Test
    void capturedPaymentAfterCancellationAndHoldExpiryCreatesOneRefundWithoutConfirmingCapacity() {
        var slot = slotA2TomorrowEarly;
        var booking = new BookingJpaEntity();
        booking.setCustomerId(customerUser.getId());
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setTotalAmount(unpaidOrder.getTotalAmount());
        booking.setHoldExpiresAt(OffsetDateTime.now().minusMinutes(1));
        var item = BookingItemJpaEntity.builder().booking(booking).slotId(slot.getId())
                .serviceId(serviceA2.getId()).vendorId(vendorProfileA.getId())
                .quantity(1).price(unpaidOrder.getTotalAmount()).bookingDate(slot.getDate())
                .bookingTime(slot.getStartTime()).build();
        booking.getItems().add(item);
        bookings.saveAndFlush(booking);
        unpaidOrder.setBookingId(booking.getId());
        unpaidOrder.setStatus(MasterOrderStatus.CANCELLED);
        masterOrderRepository.saveAndFlush(unpaidOrder);
        unpaidSubOrder.setBookingItemId(item.getId());
        unpaidSubOrder.setStatus(SubOrderStatus.CANCELLED);
        subOrderRepository.saveAndFlush(unpaidSubOrder);

        var payment = new PaymentJpaEntity();
        payment.setMasterOrderId(unpaidOrder.getId());
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setAmount(unpaidOrder.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(payment);
        cancellations.cancel(vendorUserA.getId(), serviceA2.getId(), slot.getId(), "Engine failure");
        var payload = """
                {"eventId":"late-capture-001","paymentId":"%s","providerTransactionId":"capture-001",
                 "status":"SUCCESS","amount":%s}
                """.formatted(payment.getId(), payment.getAmount().toPlainString());

        payments.processWebhook(PaymentProvider.VNPAY, payload, signer.sign(payload));
        assertThat(payments.processWebhook(PaymentProvider.VNPAY, payload, signer.sign(payload)).alreadyProcessed()).isTrue();

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(serviceSlotRepository.findById(slot.getId()).orElseThrow().getBookedCount()).isEqualTo(slot.getBookedCount());
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(refunds.findBySubOrderId(unpaidSubOrder.getId())).singleElement().satisfies(refund -> {
            assertThat(refund.getAmount()).isEqualByComparingTo(payment.getAmount());
            assertThat(refund.getPaymentId()).isEqualTo(payment.getId());
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        });
    }

    @Test
    void customerCannotCreateAnOrderFromAStillLiveHoldOnACancelledDeparture() throws Exception {
        var slot = slotA2TomorrowEarly;
        var booking = new BookingJpaEntity();
        booking.setCustomerId(customerUser.getId());
        booking.setStatus(BookingStatus.HOLD);
        booking.setTotalAmount(BigDecimal.valueOf(250000));
        booking.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
        booking.getItems().add(BookingItemJpaEntity.builder().booking(booking).slotId(slot.getId())
                .serviceId(serviceA2.getId()).vendorId(vendorProfileA.getId()).quantity(1)
                .price(booking.getTotalAmount()).bookingDate(slot.getDate()).bookingTime(slot.getStartTime()).build());
        bookings.saveAndFlush(booking);
        cancellations.cancel(vendorUserA.getId(), serviceA2.getId(), slot.getId(), "Engine failure");

        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "cancelled-departure-order-001")
                        .contentType("application/json").content("{\"bookingId\":\"" + booking.getId() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(masterOrderRepository.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    void liveMixedCheckoutKeepsUnaffectedDepartureAndRefundsOnlyTheCancelledPart() {
        var affectedSlot = slotA2TomorrowEarly;
        var unaffectedSlot = slotB1TomorrowLate;
        var booking = new BookingJpaEntity();
        booking.setCustomerId(customerUser.getId());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(unpaidOrder.getTotalAmount());
        booking.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
        var affectedItem = BookingItemJpaEntity.builder().booking(booking).slotId(affectedSlot.getId())
                .serviceId(serviceA2.getId()).vendorId(vendorProfileA.getId()).quantity(1)
                .price(new BigDecimal("100000")).bookingDate(affectedSlot.getDate())
                .bookingTime(affectedSlot.getStartTime()).build();
        var unaffectedItem = BookingItemJpaEntity.builder().booking(booking).slotId(unaffectedSlot.getId())
                .serviceId(serviceB1.getId()).vendorId(vendorProfileB.getId()).quantity(1)
                .price(new BigDecimal("150000")).bookingDate(unaffectedSlot.getDate())
                .bookingTime(unaffectedSlot.getStartTime()).build();
        booking.getItems().addAll(List.of(affectedItem, unaffectedItem));
        bookings.saveAndFlush(booking);

        unpaidOrder.setBookingId(booking.getId());
        masterOrderRepository.saveAndFlush(unpaidOrder);
        unpaidSubOrder.setBookingItemId(affectedItem.getId());
        unpaidSubOrder.setUnitPrice(affectedItem.getPrice());
        unpaidSubOrder.setSubtotalAmount(affectedItem.getPrice());
        unpaidSubOrder.setFinalAmount(affectedItem.getPrice());
        subOrderRepository.saveAndFlush(unpaidSubOrder);

        var otherSubOrder = new SubOrderJpaEntity();
        otherSubOrder.setMasterOrderId(unpaidOrder.getId());
        otherSubOrder.setBookingItemId(unaffectedItem.getId());
        otherSubOrder.setSlotId(unaffectedSlot.getId());
        otherSubOrder.setServiceId(serviceB1.getId());
        otherSubOrder.setVendorId(vendorProfileB.getId());
        otherSubOrder.setStatus(SubOrderStatus.PENDING);
        otherSubOrder.setQuantity(1);
        otherSubOrder.setUnitPrice(unaffectedItem.getPrice());
        otherSubOrder.setSubtotalAmount(unaffectedItem.getPrice());
        otherSubOrder.setFinalAmount(unaffectedItem.getPrice());
        subOrderRepository.saveAndFlush(otherSubOrder);

        var payment = new PaymentJpaEntity();
        payment.setMasterOrderId(unpaidOrder.getId());
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setAmount(unpaidOrder.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(payment);
        cancellations.cancel(vendorUserA.getId(), serviceA2.getId(), affectedSlot.getId(), "Engine failure");
        cancellations.process();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        var payload = """
                {"eventId":"partial-capture-001","paymentId":"%s","providerTransactionId":"capture-002",
                 "status":"SUCCESS","amount":%s}
                """.formatted(payment.getId(), payment.getAmount().toPlainString());

        payments.processWebhook(PaymentProvider.VNPAY, payload, signer.sign(payload));

        assertThat(subOrderRepository.findById(unpaidSubOrder.getId()).orElseThrow().getStatus()).isEqualTo(SubOrderStatus.CANCELLED);
        assertThat(subOrderRepository.findById(otherSubOrder.getId()).orElseThrow().getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        assertThat(serviceSlotRepository.findById(affectedSlot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(affectedSlot.getBookedCount());
        assertThat(serviceSlotRepository.findById(unaffectedSlot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(unaffectedSlot.getBookedCount() + 1);
        assertThat(refunds.findBySubOrderId(unpaidSubOrder.getId())).singleElement()
                .satisfies(refund -> assertThat(refund.getAmount()).isEqualByComparingTo(affectedItem.getPrice()));
        assertThat(refunds.findBySubOrderId(otherSubOrder.getId())).isEmpty();
    }
}
