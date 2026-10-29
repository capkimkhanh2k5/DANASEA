package com.danasea.backend.e2e.p2;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.booking.domain.models.Booking;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.communication.infrastructure.jobs.TripReminderJob;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaNotificationRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.PaymentWebhookSigner;
import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.pdf.PdfReceiptGenerator;
import com.danasea.backend.modules.order.presentation.dtos.*;

class P2RegressionTest extends BaseE2ETest {
    @Autowired OrderPaymentService paymentService;
    @Autowired PaymentWebhookSigner signer;
    @Autowired RefundProcessingService refundService;
    @Autowired JpaRefundRepository refunds;
    @Autowired TripReminderJob reminders;
    @Autowired StringRedisTemplate redis;
    @Autowired PdfReceiptGenerator pdf;
    @Autowired JpaServiceSlotUnitRepository units;
    @Autowired SendNotificationUseCase notifications;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ConfirmBookingUseCase confirmBooking;
    @MockitoBean PaymentGatewayPort gateway;

    @AfterEach void cleanProbe() {
        refunds.deleteAll();
        units.deleteAll();
        redis.delete("inventory:slot:"+slotA1TomorrowMorning.getId()+":holds");
    }

    @Test void completedPaidOrderMustKeepReceiptAvailable() throws Exception {
        paidOrder.setStatus(MasterOrderStatus.COMPLETED); masterOrderRepository.saveAndFlush(paidOrder);
        mockMvc.perform(get("/api/orders/"+paidOrder.getId()+"/receipt").header("Authorization","Bearer "+customerToken))
                .andExpect(status().isOk());
    }

    @Test void realPaymentTransitionMustCreateNotifications() throws Exception {
        paidOrder.setStatus(MasterOrderStatus.PENDING_PAYMENT); paidOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);
        masterOrderRepository.saveAndFlush(paidOrder);
        paidPayment.setPaidAt(null); paidPayment.setStatus(PaymentStatus.PENDING); paymentRepository.saveAndFlush(paidPayment);
        String payload=objectMapper.writeValueAsString(new PaymentWebhookRequest("review-payment",paidPayment.getId(),"review-capture",PaymentStatus.SUCCESS,paidPayment.getAmount()));
        paymentService.processWebhook(PaymentProvider.VNPAY,payload,signer.sign(payload));
        paymentService.processWebhook(PaymentProvider.VNPAY,payload,signer.sign(payload));
        assertNotNull(paymentRepository.findById(paidPayment.getId()).orElseThrow().getPaidAt());
        assertEquals(1, notificationRepository.findByUserIdOrderByCreatedAtDesc(vendorUserA.getId()).stream()
                .filter(n -> "NEW_BOOKING_ORDER".equals(n.getType())).count());
        assertEquals(PaymentStatus.SUCCESS,paymentRepository.findById(paidPayment.getId()).orElseThrow().getStatus());
        assertEquals(MasterOrderStatus.PAID,masterOrderRepository.findById(paidOrder.getId()).orElseThrow().getStatus());
        assertEquals(1,notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream().filter(n -> "PAYMENT_SUCCESS".equals(n.getType())).count());
    }

    @Test void realRefundCompletionMustCreateNotification() throws Exception {
        paidPayment.setProvider(PaymentProvider.PAYPAL); paidPayment.setProviderAmount(new BigDecimal("30.00"));
        paidPayment.setProviderCurrency("USD"); paymentRepository.saveAndFlush(paidPayment);
        RefundJpaEntity refund=new RefundJpaEntity(); refund.setSubOrderId(paidSubOrder.getId());
        refund.setAmount(paidPayment.getAmount()); refund.setRefundPercentage(new BigDecimal("100"));
        refund.setReason(RefundReason.CUSTOMER_CANCEL); refund.setStatus(RefundStatus.PENDING);
        refund.setIdempotencyKey("review-refund"); refund=refunds.saveAndFlush(refund);
        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenReturn(new RefundResult(true,"review-refund-id",new BigDecimal("30.00"),"Completed",GatewayRefundStatus.COMPLETED,"USD"));
        assertTrue(refundService.processRefund(refund.getId()));
        assertTrue(refundService.processRefund(refund.getId()));
        assertEquals(RefundStatus.PROCESSED,refunds.findById(refund.getId()).orElseThrow().getStatus());
        var receipt = objectMapper.readTree(mockMvc.perform(get("/api/orders/" + paidOrder.getId() + "/receipt")
                .header("Authorization", "Bearer " + customerToken)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, receipt.path("totalAmount").decimalValue().compareTo(paidPayment.getAmount()));
        assertEquals(1,notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream().filter(n -> "REFUND_COMPLETED".equals(n.getType())).count());
    }

    @Test void discoveryMustNotReturnDepartedSlots() throws Exception {
        slotA1TomorrowMorning.setDate(LocalDate.now(Booking.VIETNAM_ZONE).minusDays(1));
        serviceSlotRepository.saveAndFlush(slotA1TomorrowMorning);
        var response=mockMvc.perform(get("/api/services").param("date",slotA1TomorrowMorning.getDate().toString()).param("guests","7"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(0,objectMapper.readTree(response).path("content").size(),"Search still recommends yesterday's departed slot");
    }

    @Test void detailAndDiscoveryMustDeductLiveHolds() throws Exception {
        String key="inventory:slot:"+slotA1TomorrowMorning.getId()+":holds";
        redis.opsForHash().put(key,"review-hold",(System.currentTimeMillis()+600000)+"|PERSON_LIMIT:7");
        var detail=mockMvc.perform(get("/api/services/"+serviceA1.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var slots=objectMapper.readTree(detail).path("slots");
        int capacity=-1;
        for(var slot:slots) if(slotA1TomorrowMorning.getId().toString().equals(slot.path("slotId").asText())) capacity=slot.path("availableCapacity").asInt();
        assertEquals(1,capacity,"8 DB seats minus 7 live held seats should leave 1");
    }

    @Test void searchMustNotRecommendFullyHeldSlot() throws Exception {
        redis.opsForHash().put("inventory:slot:"+slotA1TomorrowMorning.getId()+":holds","review-hold",(System.currentTimeMillis()+600000)+"|PERSON_LIMIT:8");
        var response=mockMvc.perform(get("/api/services").param("vendorId",vendorProfileA.getId().toString()).param("guests","7"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var content=objectMapper.readTree(response).path("content");
        for(var item:content) assertNotEquals(serviceA1.getId().toString(),item.path("id").asText(),"The slot is fully held in Redis");
    }

    @Test void rejectedSubOrderMustNotSendTripReminder() {
        prepareReminder(LocalDateTime.now(Booking.VIETNAM_ZONE).plusHours(2));
        paidSubOrder.setStatus(SubOrderStatus.REJECTED); subOrderRepository.saveAndFlush(paidSubOrder);
        reminders.scanAndSendReminders();
        assertEquals(0,notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream().filter(n -> "TRIP_REMINDER".equals(n.getType())).count());
    }

    @Test void concurrentReminderJobsMustProduceOnlyOneNotification() throws Exception {
        prepareReminder(LocalDateTime.now(Booking.VIETNAM_ZONE).plusHours(2));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); reminders.scanAndSendReminders(); return null; });
            Future<?> second=pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); reminders.scanAndSendReminders(); return null; });
            first.get(20,TimeUnit.SECONDS); second.get(20,TimeUnit.SECONDS);
            assertEquals(1,notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream().filter(n -> "TRIP_REMINDER".equals(n.getType())).count());
        } finally { pool.shutdownNow(); }
    }

    @Test void notificationsMustUseRecipientLocale() {
        customerUser.setLocale("en"); userRepository.saveAndFlush(customerUser);
        applicationContext.publishEvent(new PaymentSuccessEvent(
                paidOrder.getId(),customerUser.getId(),paidOrder.getBookingId()));
        var notification=notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream()
                .filter(n -> "PAYMENT_SUCCESS".equals(n.getType())).findFirst().orElseThrow();
        assertEquals("en",notification.getLocale(),notification.getTitle());
    }

    private void prepareReminder(LocalDateTime departure) {
        slotA1TomorrowMorning.setDate(departure.toLocalDate());
        slotA1TomorrowMorning.setStartTime(departure.toLocalTime());
        slotA1TomorrowMorning.setEndTime(departure.plusHours(1).toLocalTime());
        serviceSlotRepository.saveAndFlush(slotA1TomorrowMorning);
    }

    @Test void partiallyCompletedOrderMustRemindRemainingConfirmedTrip() {
        prepareReminder(LocalDateTime.now(Booking.VIETNAM_ZONE).plusHours(2));
        paidOrder.setStatus(MasterOrderStatus.PARTIALLY_COMPLETED); masterOrderRepository.saveAndFlush(paidOrder);
        customerUser.setLocale("en"); userRepository.saveAndFlush(customerUser);
        reminders.scanAndSendReminders();
        var found = notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream()
                .filter(n -> "TRIP_REMINDER".equals(n.getType())).toList();
        assertEquals(1, found.size()); assertEquals("en", found.get(0).getLocale());
    }

    @Test void reminderMustNotBeSentBefore24HourWindow() {
        prepareReminder(LocalDateTime.now(Booking.VIETNAM_ZONE).plusHours(25));
        reminders.scanAndSendReminders();
        assertEquals(0, notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream()
                .filter(n -> "TRIP_REMINDER".equals(n.getType())).count());
    }

    @Test void availabilityMustFilterBeforePaginationAndCount() throws Exception {
        slotA2TomorrowEarly.setDate(LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1));
        slotA2TomorrowEarly.setStartTime(LocalTime.of(9, 30));
        slotA2TomorrowEarly.setEndTime(LocalTime.of(10, 30));
        serviceSlotRepository.saveAndFlush(slotA2TomorrowEarly);

        redis.opsForHash().put("inventory:slot:" + slotA1TomorrowMorning.getId() + ":holds", "review-hold",
                (System.currentTimeMillis()+600000) + "|PERSON_LIMIT:8");
        var response = mockMvc.perform(get("/api/services").param("vendorId", vendorProfileA.getId().toString())
                .param("guests", "4").param("sortBy", "price_desc").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var page = objectMapper.readTree(response);
        assertEquals(1, page.path("totalElements").asInt());
        assertEquals(serviceA2.getId().toString(), page.path("content").get(0).path("id").asText());
    }

    @Test void expiredHoldsMustNotReduceAvailability() throws Exception {
        redis.opsForHash().put("inventory:slot:" + slotA1TomorrowMorning.getId() + ":holds", "review-hold",
                (System.currentTimeMillis()-60000) + "|PERSON_LIMIT:8");
        var response = mockMvc.perform(get("/api/services").param("vendorId", vendorProfileA.getId().toString())
                .param("guests", "7")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(serviceA1.getId().toString(), objectMapper.readTree(response).path("content").get(0).path("id").asText());
    }

    @Test void bookingsSortMustUseConfirmedOrdersInsteadOfViews() throws Exception {
        var response = mockMvc.perform(get("/api/services").param("sortBy", "bookings_desc"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(serviceA1.getId().toString(), objectMapper.readTree(response).path("content").get(0).path("id").asText());
    }

    @Test void sharedUnitsAndPrivatePackagesMustUseSameHoldInventory() throws Exception {
        slotA1TomorrowMorning.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        slotA1TomorrowMorning.setCapacity(30); slotA1TomorrowMorning.setBookedCount(8);
        serviceSlotRepository.saveAndFlush(slotA1TomorrowMorning);
        for (int i=1; i<=3; i++) {
            units.saveAndFlush(ServiceSlotUnitJpaEntity.builder()
                    .slotId(slotA1TomorrowMorning.getId()).unitNumber(i).capacity(10).bookedCount(i==1 ? 8 : 0).build());
        }
        var privateOption = ServiceOptionJpaEntity.builder()
                .serviceId(serviceA1.getId()).name("Private boat").optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE).maxPaxPerPackage(10)
                .price(new BigDecimal("2000000")).status(OptionStatus.ACTIVE).build();
        privateOption=optionRepository.saveAndFlush(privateOption);
        redis.opsForHash().put("inventory:slot:" + slotA1TomorrowMorning.getId() + ":holds", "review-hold",
                (System.currentTimeMillis()+600000) + "|UNITS:2:10:1");
        var detail = objectMapper.readTree(mockMvc.perform(get("/api/services/"+serviceA1.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var options = detail.path("slots"); int shared=-1, packages=-1;
        for (var slot : options) {
            if (!slotA1TomorrowMorning.getId().toString().equals(slot.path("slotId").asText())) continue;
            if ("PER_PACKAGE".equals(slot.path("pricingUnit").asText())) packages=slot.path("availableCapacity").asInt();
            else shared=slot.path("availableCapacity").asInt();
        }
        assertEquals(12, shared); assertEquals(1, packages);
        var search = objectMapper.readTree(mockMvc.perform(get("/api/services").param("vendorId",vendorProfileA.getId().toString())
                .param("guests","11")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, search.path("content").size(), "11 guests cannot fit one shared unit or the remaining private package");
    }

    @Test void rolledBackPaymentMustNotLeaveNotificationsBehind() throws Exception {
        paidOrder.setStatus(MasterOrderStatus.PENDING_PAYMENT); paidOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);
        masterOrderRepository.saveAndFlush(paidOrder);
        paidPayment.setStatus(PaymentStatus.PENDING); paymentRepository.saveAndFlush(paidPayment);
        String payload=objectMapper.writeValueAsString(new PaymentWebhookRequest("rollback-payment",paidPayment.getId(),"rollback-capture",PaymentStatus.SUCCESS,paidPayment.getAmount()));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            paymentService.processWebhook(PaymentProvider.VNPAY,payload,signer.sign(payload));
            throw new IllegalStateException("Simulated rollback");
        }));
        assertEquals(PaymentStatus.PENDING, paymentRepository.findById(paidPayment.getId()).orElseThrow().getStatus());
        assertEquals(0, notificationRepository.findByUserIdOrderByCreatedAtDesc(customerUser.getId()).stream()
                .filter(n -> "PAYMENT_SUCCESS".equals(n.getType())).count());
    }

    @Test void concurrentNotificationInsertionMustBeAtomic() throws Exception {
        CyclicBarrier start = new CyclicBarrier(8);
        var command = new NotificationCommand(customerUser.getId(),
                "TRIP_REMINDER", NotificationChannel.IN_APP,
                com.danasea.backend.shared.i18n.LocalizedMessageRef.of("notification.trip.reminder.title"),
                com.danasea.backend.shared.i18n.LocalizedMessageRef.of("notification.trip.reminder.body", "08:00", "2026-10-12"),
                "SERVICE_SLOT", slotA1TomorrowMorning.getId(), null);
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            var calls = IntStream.range(0,8).mapToObj(i -> pool.submit(() -> {
                start.await(10,TimeUnit.SECONDS); return notifications.executeOnce(command,"race-notification").isPresent();
            })).toList();
            int inserted=0; for (var call : calls) if (call.get(20,TimeUnit.SECONDS)) inserted++;
            assertEquals(1, inserted); assertTrue(notificationRepository.findByIdempotencyKey("race-notification").isPresent());
        } finally { pool.shutdownNow(); }
    }

}
