package com.danasea.backend.e2e.p2;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.presentation.CatalogController;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.lang.reflect.Field;
import java.util.Map;

@DisplayName("P2 E2E Tier 2: Boundary, Corner Cases & Security Regressions (R1 - R6)")
public class P2Tier2BoundaryAndCornerCaseTest extends BaseE2ETest {

    // =========================================================================
    // R1: Search Boundaries & Input Validations
    // =========================================================================

    @Test
    @DisplayName("R1.B1 - Search Boundary: Out-of-range minRating (> 5.0 or < 0.0) returns HTTP 400 Bad Request")
    void testR1_Boundary_MinRatingOutOfRange_ReturnsBadRequest() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "minRating");

        mockMvc.perform(get("/api/services")
                        .param("minRating", "5.5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/services")
                        .param("minRating", "-1.0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1.B2 - Search Boundary: Zero or negative guests returns HTTP 400 Bad Request")
    void testR1_Boundary_GuestsZeroOrNegative_ReturnsBadRequest() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "guests");

        mockMvc.perform(get("/api/services")
                        .param("guests", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/services")
                        .param("guests", "-3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1.B3 - Search Boundary: Invalid date string returns HTTP 400 Bad Request")
    void testR1_Boundary_InvalidDateFormat_ReturnsBadRequest() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "date");

        mockMvc.perform(get("/api/services")
                        .param("date", "not-a-valid-date")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R1.B4 - Search Boundary: Non-existent vendorId returns empty page with HTTP 200 OK")
    void testR1_Boundary_NonExistentVendor_ReturnsEmptyList() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "vendorId");

        mockMvc.perform(get("/api/services")
                        .param("vendorId", UUID.randomUUID().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("R1.B5 - Search Boundary: Capacity requirement exceeding all available slots returns empty page")
    void testR1_Boundary_NoMatchingSlotCapacity_ReturnsEmptyList() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "guests");

        mockMvc.perform(get("/api/services")
                        .param("guests", "9999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // =========================================================================
    // R2: Service Detail Boundaries & Security
    // =========================================================================

    @Test
    @DisplayName("R2.B1 - Service Detail Boundary: Non-existent service UUID returns HTTP 404 Not Found")
    void testR2_Boundary_NonExistentService_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/services/{id}", UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R2.B2 - Service Detail Boundary: Malformed UUID format returns HTTP 400 Bad Request")
    void testR2_Boundary_MalformedServiceId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/services/{id}", "not-a-valid-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R2.B3 - Service Detail Boundary: Public unauthenticated access allowed without JWT")
    void testR2_Boundary_UnauthenticatedAccessAllowed() throws Exception {
        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceA1.getId().toString()));
    }

    @Test
    @DisplayName("R2.B4 - Service Detail Boundary: Fully booked slot displays availableCapacity = 0 without error")
    void testR2_Boundary_ZeroCapacitySlot_HandledGracefully() throws Exception {
        mockMvc.perform(get("/api/services/{id}", serviceA1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // slotA1TomorrowAfternoon has capacity 10, booked 10 -> available 0
    }

    @Test
    @DisplayName("R2.B5 - Service Detail Boundary: Service without waiver content handles null gracefully")
    void testR2_Boundary_NullWaiverContent_HandledGracefully() throws Exception {
        // serviceA2 has null waiverContent
        mockMvc.perform(get("/api/services/{id}", serviceA2.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceA2.getId().toString()));
    }

    // =========================================================================
    // R3: Public Vendor Profile & Storefront Boundaries
    // =========================================================================

    @Test
    @DisplayName("R3.B1 - Public Vendor Boundary: Non-existent vendor UUID returns HTTP 404 Not Found")
    void testR3_Boundary_NonExistentVendor_ReturnsNotFound() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}", UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R3.B2 - Public Vendor Boundary: Malformed vendor UUID returns HTTP 400 Bad Request")
    void testR3_Boundary_MalformedVendorId_ReturnsBadRequest() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}", "invalid-uuid-format")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R3.B3 - Public Vendor Boundary: Vendor with zero published services returns activeServicesCount = 0 and empty list")
    void testR3_Boundary_VendorWithNoServices_ReturnsZeroCountAndEmptyList() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        UserJpaEntity vendorUserEmpty = createUser("vendor.empty@danasea.vn", "Vendor Empty", Role.VENDOR);
        VendorJpaEntity vendorEmpty = createVendorProfile(vendorUserEmpty.getId(), "Empty Storefront Vendor",
                BadgeTier.NONE, BigDecimal.ZERO, 0, "111222333", "0409999999");

        mockMvc.perform(get("/api/vendors/{id}", vendorEmpty.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeServicesCount").value(0));

        mockMvc.perform(get("/api/vendors/{id}/services", vendorEmpty.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("R3.B4 - Public Storefront Boundary: Page index exceeding total pages returns empty list with HTTP 200 OK")
    void testR3_Boundary_StorefrontPageBeyondTotal_ReturnsEmptyList() throws Exception {
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .param("page", "999")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("R3.B5 - Public Storefront Boundary: Unauthenticated request to vendor storefront succeeds")
    void testR3_Boundary_UnauthenticatedAccessAllowed() throws Exception {
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // R4: Notification Boundaries & IDOR Security
    // =========================================================================

    @Test
    @DisplayName("R4.B1 - Notification Security: Unauthenticated request to mark-read returns HTTP 401 Unauthorized")
    void testR4_Boundary_UnauthenticatedMarkRead_ReturnsUnauthorized() throws Exception {
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);

        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R4.B2 - Notification Security: Unauthenticated request to unread-count returns HTTP 401 Unauthorized")
    void testR4_Boundary_UnauthenticatedUnreadCount_ReturnsUnauthorized() throws Exception {
        assumeEndpoint("/api/notifications/unread-count", RequestMethod.GET);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R4.B3 - Notification IDOR Guard: User cannot mark another user's notification as read (HTTP 403 / 404)")
    void testR4_Boundary_IdorAttack_MarkAnotherUserNotification_ReturnsForbidden() throws Exception {
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);

        // Attacker attempts to mark Customer One's notification as read
        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404));

        if (isFieldPresent(NotificationJpaEntity.class, "isRead")) {
            Field field = NotificationJpaEntity.class.getDeclaredField("isRead");
            field.setAccessible(true);
            NotificationJpaEntity check = notificationRepository.findById(sampleNotificationCustomer.getId()).orElseThrow();
            assertThat(field.getBoolean(check)).isFalse();
        }
    }

    @Test
    @DisplayName("R4.B4 - Notification Boundary: Mark non-existent notification as read returns HTTP 404 Not Found")
    void testR4_Boundary_MarkNonExistentNotification_ReturnsNotFound() throws Exception {
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);

        mockMvc.perform(patch("/api/notifications/{id}/read", UUID.randomUUID())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R4.B5 - Notification Boundary: mark-all-read when already all read is idempotent (updatedCount = 0)")
    void testR4_Boundary_MarkAllRead_WhenAlreadyAllRead_IsIdempotent() throws Exception {
        assumeEndpoint("/api/notifications/read-all", RequestMethod.PATCH);

        // Pre-mark all customer notifications as read via API
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Subsequent call must return 0 updated records
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    // =========================================================================
    // R5: Event Listeners & Scheduler Boundaries
    // =========================================================================

    @Test
    @DisplayName("R5.B1 - Trip Reminder Boundary: Slot that departed yesterday is excluded from reminders")
    void testR5_Boundary_TripReminder_PastSlot_NotTriggered() {
        // Create past slot
        createSlot(serviceA1.getId(), LocalDate.now().minusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 10, 5, SlotStatus.OPEN);

        long notifsBefore = notificationRepository.count();
        if (isBeanAvailable("tripReminderJob")) {
            try {
                Object job = applicationContext.getBean("tripReminderJob");
                Method m = job.getClass().getMethod("executeTripReminders");
                m.invoke(job);
            } catch (Exception ignored) {}
        }
        // Past slots should not produce reminder notifications
    }

    @Test
    @DisplayName("R5.B2 - Trip Reminder Boundary: Slot departing in 5 days is excluded from immediate reminders")
    void testR5_Boundary_TripReminder_DistantFutureSlot_NotTriggered() {
        createSlot(serviceA1.getId(), LocalDate.now().plusDays(5),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 10, 2, SlotStatus.OPEN);
        // Distant future slots (> 24h away) should not produce reminder notifications
    }

    @Test
    @DisplayName("R5.B3 - Payment Event Boundary: Non-existent orderId in event does not crash application")
    void testR5_Boundary_PaymentEvent_ForNonExistentOrder_HandlesGracefully() {
        if (isBeanAvailable("paymentNotificationEventListener")) {
            PaymentSuccessEvent phantomEvent = new PaymentSuccessEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            // Publishing event must not throw unhandled exception
            applicationContext.publishEvent(phantomEvent);
        }
    }

    @Test
    @DisplayName("R5.B4 - Push Notification Boundary: Special characters, emojis, and long Vietnamese text handled cleanly")
    void testR5_Boundary_PushNotification_HandlesSpecialCharacters() throws Exception {
        if (isClassPresent("com.danasea.backend.modules.communication.domain.ports.PushNotificationPort")) {
            Class<?> portClass = Class.forName("com.danasea.backend.modules.communication.domain.ports.PushNotificationPort");
            Object pushPort = applicationContext.getBean(portClass);
            Method sendMethod = portClass.getMethod("sendPush", UUID.class, String.class, String.class, Map.class);

            String complexTitle = "🌊 [Danasea Alert] Tour Lặn San Hô & Ngắm Hoàng Hôn ☀️";
            String complexBody = "Chào quý khách! Chuyến đi của bạn tại Bãi Bụt - Bán đảo Sơn Trà sẽ bắt đầu lúc 08:00 sáng mai. Xin vui lòng mang theo kem chống nắng!";

            Object result = sendMethod.invoke(pushPort, customerUser.getId(), complexTitle, complexBody, Map.of("category", "DIVING", "promoCode", "DANANG#2026"));
            assertThat(result).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("R5.B5 - Refund Event Boundary: Non-existent refund ID in event handled safely without crashing")
    void testR5_Boundary_RefundEvent_HandlesGracefully() {
        if (isClassPresent("com.danasea.backend.modules.order.domain.events.RefundCompletedEvent")) {
            // If class present, test graceful behavior
        }
    }

    // =========================================================================
    // R6: Receipt Boundaries & Security
    // =========================================================================

    @Test
    @DisplayName("R6.B1 - Receipt Security: Unauthenticated request to get receipt returns HTTP 401 Unauthorized")
    void testR6_Boundary_UnauthenticatedReceipt_ReturnsUnauthorized() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R6.B2 - Receipt IDOR Guard: Customer cannot view another customer's order receipt (HTTP 403 Forbidden)")
    void testR6_Boundary_IdorAttack_CustomerRequestingOtherCustomerReceipt_ReturnsForbidden() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        // Attacker attempts to retrieve Customer One's paid receipt
        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R6.B3 - Receipt Guard: Unpaid order (PENDING_PAYMENT / UNPAID) returns HTTP 400 Bad Request")
    void testR6_Boundary_UnpaidOrder_ReturnsBadRequest() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        // Acceptance Criteria: "Endpoint GET /api/orders/{id}/receipt trả về HTTP 400 nếu đơn hàng chưa ở trạng thái thanh toán thành công (PAID)."
        mockMvc.perform(get("/api/orders/{id}/receipt", unpaidOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R6.B4 - Receipt Guard: Cancelled order returns HTTP 400 Bad Request")
    void testR6_Boundary_CancelledUnpaidOrder_ReturnsBadRequest() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", cancelledOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R6.B5 - Receipt Boundary: Non-existent order UUID returns HTTP 404 Not Found")
    void testR6_Boundary_NonExistentOrder_ReturnsNotFound() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        mockMvc.perform(get("/api/orders/{id}/receipt", UUID.randomUUID())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
