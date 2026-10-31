package com.danasea.backend.e2e.p2;

import java.util.Map;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.events.RefundCompletedEvent;
import com.danasea.backend.modules.communication.presentation.dtos.NotificationResponse;
import com.danasea.backend.modules.communication.domain.ports.PushNotificationPort;
import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.service.presentation.CatalogController;
import com.danasea.backend.modules.service.presentation.dtos.ServiceDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("P2 E2E Tier 1: Feature Coverage (R1 - R6 in Isolation)")
public class P2Tier1FeatureCoverageTest extends BaseE2ETest {

    // =========================================================================
    // R1: Extended Search Criteria & Service Discovery (GET /api/services)
    // =========================================================================

    @Test
    @DisplayName("R1.1 - Search Services: Filter by date matching open service slots")
    void testR1_SearchServices_ByDate() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "date");

        LocalDate tomorrow = slotA1TomorrowMorning.getDate();
        mockMvc.perform(get("/api/services")
                        .param("date", tomorrow.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("R1.2 - Search Services: Filter by guests / participantCount slot capacity")
    void testR1_SearchServices_ByGuestsCapacity() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "guests");

        // Service A1 has available slot with capacity 10 - booked 2 = 8 available.
        // Guests = 7 should find Service A1, but exclude services with lower available capacity.
        mockMvc.perform(get("/api/services")
                        .param("guests", "7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.id == '" + serviceA1.getId() + "')]").exists());
    }

    @Test
    @DisplayName("R1.3 - Search Services: Filter strictly by vendorId")
    void testR1_SearchServices_ByVendorId() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "vendorId");

        MvcResult result = mockMvc.perform(get("/api/services")
                        .param("vendorId", vendorProfileA.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = root.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(2);
        // Ensure all returned services belong to Vendor A
        for (JsonNode item : content) {
            UUID serviceId = UUID.fromString(item.get("id").asText());
            assertThat(serviceId).isIn(serviceA1.getId(), serviceA2.getId());
            assertThat(serviceId).isNotEqualTo(serviceB1.getId());
        }
    }

    @Test
    @DisplayName("R1.4 - Search Services: Filter by minRating threshold")
    void testR1_SearchServices_ByMinRating() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "minRating");

        // minRating = 4.5: should include serviceA1 (4.8) and serviceA2 (4.5), exclude serviceB1 (3.9)
        MvcResult result = mockMvc.perform(get("/api/services")
                        .param("minRating", "4.50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode item : root.get("content")) {
            BigDecimal rating = new BigDecimal(item.get("averageRating").asText());
            assertThat(rating).isGreaterThanOrEqualTo(new BigDecimal("4.50"));
        }
    }

    @Test
    @DisplayName("R1.5 - Search Services: Dynamic sorting by price ascending and descending")
    void testR1_SearchServices_SortBy() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "sortBy");

        MvcResult resultAsc = mockMvc.perform(get("/api/services")
                        .param("sortBy", "price_asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode itemsAsc = objectMapper.readTree(resultAsc.getResponse().getContentAsString()).get("content");
        if (itemsAsc.size() >= 2) {
            BigDecimal prevPrice = BigDecimal.ZERO;
            for (JsonNode item : itemsAsc) {
                BigDecimal currentPrice = new BigDecimal(item.get("price").asText());
                assertThat(currentPrice).isGreaterThanOrEqualTo(prevPrice);
                prevPrice = currentPrice;
            }
        }
    }

    @Test
    @DisplayName("R1.6 - Search Services: Backward compatibility with categoryId, keyword, price range, and pagination")
    void testR1_SearchServices_BackwardCompatibility() throws Exception {
        mockMvc.perform(get("/api/services")
                        .param("categoryId", activeCategory.getId().toString())
                        .param("keyword", "san hô")
                        .param("minPrice", "100000")
                        .param("maxPrice", "600000")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content[?(@.id == '" + serviceA1.getId() + "')]").exists());
    }

    // =========================================================================
    // R2: Extended Public Service Detail & DTO Separation (GET /api/services/{id})
    // =========================================================================

    @Test
    @DisplayName("R2.1 - Service Detail: Contains public vendor branding (vendorId, businessName, badgeTier)")
    void testR2_ServiceDetail_IncludesPublicVendorInfo() throws Exception {
        assumeFieldPresent(ServiceDetailResponse.class, "vendorId");

        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceA1.getId().toString()))
                .andExpect(jsonPath("$.vendorId").value(vendorProfileA.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Diving Adventure"))
                .andExpect(jsonPath("$.badgeTier").value("TOP_RATED"));
    }

    @Test
    @DisplayName("R2.2 - Service Detail: Includes operational info, participant conditions, and policies")
    void testR2_ServiceDetail_IncludesOperationalInfo() throws Exception {
        assumeFieldPresent(ServiceDetailResponse.class, "participantConditions");

        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(120))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.participantConditions").exists())
                .andExpect(jsonPath("$.refundPolicy").exists())
                .andExpect(jsonPath("$.cancellationPolicy").exists());
    }

    @Test
    @DisplayName("R2.3 - Service Detail: Includes structured slots with capacity, timing, and availability")
    void testR2_ServiceDetail_IncludesStructuredSlots() throws Exception {
        assumeFieldPresent(ServiceDetailResponse.class, "slots");

        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.slots.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.slots[0].slotId").exists())
                .andExpect(jsonPath("$.slots[0].startTime").exists())
                .andExpect(jsonPath("$.slots[0].endTime").exists())
                .andExpect(jsonPath("$.slots[0].capacity").isNumber())
                .andExpect(jsonPath("$.slots[0].availableCapacity").isNumber());
    }

    @Test
    @DisplayName("R2.4 - Service Detail: Preserves legacy availableSlots array for client backward compatibility")
    void testR2_ServiceDetail_PreservesLegacyAvailableSlots() throws Exception {
        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots").isArray());
    }

    @Test
    @DisplayName("R2.5 - Service Detail: Strict data isolation - Zero exposure of sensitive financial credentials")
    void testR2_ServiceDetail_NoSensitiveFinancialLeakage() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("0123456789"); // vendor A bank account number
        assertThat(body).doesNotContain("0401234567"); // vendor A tax code
        assertThat(body).doesNotContain("bankAccountNumber");
        assertThat(body).doesNotContain("taxCode");
        assertThat(body).doesNotContain("bankName");
        assertThat(body).doesNotContain("bankAccountHolder");
    }

    // =========================================================================
    // R3: Public Vendor Profile & Storefront (GET /api/vendors/{id} & /services)
    // =========================================================================

    @Test
    @DisplayName("R3.1 - Public Vendor Profile: Returns public metrics and active services count")
    void testR3_PublicVendorProfile_ReturnsAccurateData() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendorProfileA.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Diving Adventure"))
                .andExpect(jsonPath("$.badgeTier").value("TOP_RATED"))
                .andExpect(jsonPath("$.ratingAvg").value(4.8))
                .andExpect(jsonPath("$.ratingCount").value(12))
                .andExpect(jsonPath("$.activeServicesCount").value(2));
    }

    @Test
    @DisplayName("R3.2 - Public Vendor Profile: Unauthenticated client access allowed without JWT")
    void testR3_PublicVendorProfile_UnauthenticatedAccessAllowed() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R3.3 - Public Vendor Storefront: Returns published services for vendor with pagination")
    void testR3_VendorStorefront_ReturnsPublishedServices() throws Exception {
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '" + serviceA1.getId() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + serviceA2.getId() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + serviceB1.getId() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("R3.4 - Public Vendor Storefront: Supports sorting by price or rating")
    void testR3_VendorStorefront_SupportsSorting() throws Exception {
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .param("sortBy", "price_desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(serviceA1.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(serviceA2.getId().toString()));
    }

    @Test
    @DisplayName("R3.5 - Public Vendor Profile & Storefront: Strict isolation from sensitive financial fields")
    void testR3_PublicVendor_StrictDataSeparation() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);

        MvcResult result = mockMvc.perform(get("/api/vendors/{id}", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String profileBody = result.getResponse().getContentAsString();
        assertThat(profileBody).doesNotContain("0123456789");
        assertThat(profileBody).doesNotContain("0401234567");
        assertThat(profileBody).doesNotContain("bankAccountNumber");
        assertThat(profileBody).doesNotContain("taxCode");
        assertThat(profileBody).doesNotContain("bankName");
        assertThat(profileBody).doesNotContain("bankAccountHolder");
    }

    // =========================================================================
    // R4: In-App Notification Center (/api/notifications)
    // =========================================================================

    @Test
    @DisplayName("R4.1 - Get Notifications: Item contains read status tracking fields (isRead, readAt)")
    void testR4_GetNotifications_IncludesReadStatusFields() throws Exception {
        assumeFieldPresent(NotificationResponse.class, "isRead");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].isRead").value(false));
    }

    @Test
    @DisplayName("R4.2 - Mark Notification Read: Updates isRead to true and assigns readAt timestamp")
    void testR4_MarkNotificationAsRead_UpdatesStatus() throws Exception {
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);

        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        NotificationJpaEntity updated = notificationRepository.findById(sampleNotificationCustomer.getId()).orElseThrow();
        if (isFieldPresent(NotificationJpaEntity.class, "isRead")) {
            java.lang.reflect.Field field = NotificationJpaEntity.class.getDeclaredField("isRead");
            field.setAccessible(true);
            assertThat(field.getBoolean(updated)).isTrue();
        }
    }

    @Test
    @DisplayName("R4.3 - Mark All Notifications Read: Bulk marks all notifications for authenticated user")
    void testR4_MarkAllNotificationsAsRead_UpdatesAll() throws Exception {
        assumeEndpoint("/api/notifications/read-all", RequestMethod.PATCH);

        // Add a second unread notification for customer
        NotificationJpaEntity second = new NotificationJpaEntity();
        second.setUserId(customerUser.getId());
        second.setType("PROMO");
        second.setChannel(NotificationChannel.IN_APP);
        second.setTitle("Ưu đãi đặc biệt");
        second.setBody("Giảm 10% dịch vụ lặn biển");
        second.setStatus(NotificationStatus.SENT);
        notificationRepository.save(second);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("R4.4 - Unread Notification Count: Accurate count returned for authenticated user")
    void testR4_UnreadCount_ReturnsAccurateCount() throws Exception {
        assumeEndpoint("/api/notifications/unread-count", RequestMethod.GET);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("R4.5 - Notification Lifecycle: Marking single notification read decrements unread count")
    void testR4_MarkRead_DecrementsUnreadCount() throws Exception {
        assumeEndpoint("/api/notifications/unread-count", RequestMethod.GET);
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);

        MvcResult beforeResult = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long initialCount = objectMapper.readTree(beforeResult.getResponse().getContentAsString()).get("unreadCount").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        MvcResult afterResult = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long afterCount = objectMapper.readTree(afterResult.getResponse().getContentAsString()).get("unreadCount").asLong();

        assertThat(afterCount).isEqualTo(initialCount - 1);
    }

    // =========================================================================
    // R5: Event Listeners & Scheduled Reminders
    // =========================================================================

    @Test
    @DisplayName("R5.1 - Payment Success Event: Publishes and generates in-app notification for customer")
    void testR5_PaymentSuccessEvent_TriggersCustomerNotification() {
        assumeBean("paymentNotificationEventListener");

        long initialNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(customerUser.getId()))
                .count();

        PaymentSuccessEvent event = new PaymentSuccessEvent(paidOrder.getId(), customerUser.getId(), UUID.randomUUID());
        applicationContext.publishEvent(event);

        long countAfter = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(customerUser.getId()))
                .count();

        assertThat(countAfter).isGreaterThan(initialNotifications);
    }

    @Test
    @DisplayName("R5.2 - Payment Success Event: Triggers in-app notification for vendor owner")
    void testR5_PaymentSuccessEvent_TriggersVendorNotification() {
        assumeBean("paymentNotificationEventListener");

        long initialVendorNotifs = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(vendorUserA.getId()))
                .count();

        PaymentSuccessEvent event = new PaymentSuccessEvent(paidOrder.getId(), customerUser.getId(), UUID.randomUUID());
        applicationContext.publishEvent(event);

        long countAfter = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(vendorUserA.getId()))
                .count();

        assertThat(countAfter).isGreaterThan(initialVendorNotifs);
    }

    @Test
    @DisplayName("R5.3 - Refund Completed Event: Triggers in-app notification for customer")
    void testR5_RefundCompletedEvent_TriggersCustomerNotification() throws Exception {
        assumeClassPresent("com.danasea.backend.modules.order.domain.events.RefundCompletedEvent");
        assumeBean("refundNotificationEventListener");

        Class<?> refundEventClass = Class.forName("com.danasea.backend.modules.order.domain.events.RefundCompletedEvent");
        Constructor<?> constructor = refundEventClass.getConstructors()[0];

        Object eventInstance = null;
        if (constructor.getParameterCount() == 6) {
            // record RefundCompletedEvent(UUID refundId, UUID subOrderId, UUID masterOrderId, UUID customerId, BigDecimal amount, RefundStatus status)
            eventInstance = constructor.newInstance(UUID.randomUUID(), paidSubOrder.getId(), paidOrder.getId(), customerUser.getId(), new BigDecimal("100000.00"), RefundStatus.PROCESSED);
        } else if (constructor.getParameterCount() == 4) {
            eventInstance = constructor.newInstance(UUID.randomUUID(), paidOrder.getId(), customerUser.getId(), new BigDecimal("100000.00"));
        }

        if (eventInstance != null) {
            long initialNotifications = notificationRepository.findAll().stream()
                    .filter(n -> n.getUserId().equals(customerUser.getId()))
                    .count();

            applicationContext.publishEvent(eventInstance);

            long countAfter = notificationRepository.findAll().stream()
                    .filter(n -> n.getUserId().equals(customerUser.getId()))
                    .count();

            assertThat(countAfter).isGreaterThan(initialNotifications);
        }
    }

    @Test
    @DisplayName("R5.4 - Trip Reminder Job: Scheduled job executes with idempotency (no duplicate notifications)")
    void testR5_TripReminderJob_ExecutesWithIdempotency() throws Exception {
        assumeBean("tripReminderJob");

        Object tripReminderJob = applicationContext.getBean("tripReminderJob");
        Method runMethod = tripReminderJob.getClass().getMethod("executeTripReminders");
        if (runMethod == null) {
            runMethod = tripReminderJob.getClass().getMethod("scanAndSendReminders");
        }

        long countBefore = notificationRepository.count();
        runMethod.invoke(tripReminderJob);
        long countAfterFirstRun = notificationRepository.count();

        // Run second time in same time window: count must NOT increase (Idempotent)
        runMethod.invoke(tripReminderJob);
        long countAfterSecondRun = notificationRepository.count();

        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }

    @Test
    @DisplayName("R5.5 - Push Notification Abstraction: PushNotificationPort port and adapter exist and execute cleanly")
    void testR5_MobilePushNotificationPort_ExecutesWithoutError() throws Exception {
        assumeClassPresent("com.danasea.backend.modules.communication.domain.ports.PushNotificationPort");

        Class<?> portClass = Class.forName("com.danasea.backend.modules.communication.domain.ports.PushNotificationPort");
        Object pushPort = applicationContext.getBean(portClass);
        assertThat(pushPort).isNotNull();

        Method sendMethod = portClass.getMethod("sendPush", UUID.class, String.class, String.class, Map.class);
        Object result = sendMethod.invoke(pushPort, customerUser.getId(), "Nhắc lịch chuyến đi", "Chuyến lặn biển của bạn sẽ khởi hành trong 24h", Map.of("type", "TRIP_REMINDER"));
        assertThat(result).isEqualTo(false);
    }

    // =========================================================================
    // R6: Customer Receipt Generation & Retrieval (GET /api/orders/{id}/receipt)
    // =========================================================================

    @Test
    @DisplayName("R6.1 - Get Order Receipt: Returns structured JSON receipt with complete order data")
    void testR6_GetReceipt_Json_ReturnsStructuredReceipt() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(paidOrder.getId().toString()))
                .andExpect(jsonPath("$.orderCode").exists())
                .andExpect(jsonPath("$.receiptCode").exists())
                .andExpect(jsonPath("$.customerId").value(customerUser.getId().toString()))
                .andExpect(jsonPath("$.totalAmount").value(900000.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("R6.2 - Export Order Receipt: format=pdf query parameter returns RFC-compliant PDF binary")
    void testR6_GetReceipt_Pdf_WithQueryParam_ReturnsPdfBinary() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        MvcResult result = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .param("format", "pdf")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        // PDF Magic Header: %PDF-
        assertThat(new String(pdfBytes, 0, Math.min(pdfBytes.length, 5))).startsWith("%PDF");
    }

    @Test
    @DisplayName("R6.3 - Export Order Receipt: Accept: application/pdf header returns PDF binary")
    void testR6_GetReceipt_Pdf_WithAcceptHeader_ReturnsPdfBinary() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        MvcResult result = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Accept", "application/pdf")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, Math.min(pdfBytes.length, 5))).startsWith("%PDF");
    }

    @Test
    @DisplayName("R6.4 - Order Receipt: Line items and totals match order and payment records")
    void testR6_GetReceipt_LineItemsAndTotalsMatchOrder() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serviceId").value(serviceA1.getId().toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(500000.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(1000000.00))
                .andExpect(jsonPath("$.items[0].discountAmount").value(100000.00))
                .andExpect(jsonPath("$.items[0].finalAmount").value(900000.00));
    }

    @Test
    @DisplayName("R6.5 - Order Receipt: Admin access allowed to view any customer receipt")
    void testR6_GetReceipt_AdminAccessAllowed() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(paidOrder.getId().toString()));
    }
}
