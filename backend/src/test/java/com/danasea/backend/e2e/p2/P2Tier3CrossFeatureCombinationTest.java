package com.danasea.backend.e2e.p2;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("P2 E2E Tier 3: Cross-Feature Combinations")
public class P2Tier3CrossFeatureCombinationTest extends BaseE2ETest {

    @Test
    @DisplayName("Tier 3.1 - Combined Multi-Criteria Search: date + guests + minRating + sortBy price")
    void testTier3_CombinedSearch_FindsExactService() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "date");
        assumeParamSupported(CatalogController.class, "searchServices", "guests");
        assumeParamSupported(CatalogController.class, "searchServices", "minRating");
        assumeParamSupported(CatalogController.class, "searchServices", "sortBy");

        LocalDate tomorrow = slotA1TomorrowMorning.getDate();
        MvcResult result = mockMvc.perform(get("/api/services")
                        .param("date", tomorrow.toString())
                        .param("guests", "6")
                        .param("minRating", "4.5")
                        .param("sortBy", "price_asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.get("content");
        // Service A1 has 8 available capacity (> 6) and rating 4.8 (> 4.5).
        // Service A2 has only 5 available capacity (< 6).
        // Service B1 has rating 3.9 (< 4.5).
        // Therefore, Service A1 must be in results.
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        assertThat(items.get(0).get("id").asText()).isEqualTo(serviceA1.getId().toString());
    }

    @Test
    @DisplayName("Tier 3.2 - Discovery to Storefront Flow: Search service -> Extract vendorId -> Query Vendor Profile & Storefront")
    void testTier3_DiscoveryToStorefrontFlow() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        // Step 1: Search service by keyword
        MvcResult searchResult = mockMvc.perform(get("/api/services")
                        .param("keyword", "Son Tra")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode searchRoot = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        assertThat(searchRoot.get("content").size()).isGreaterThanOrEqualTo(1);

        // Step 2: Query public vendor profile
        MvcResult vendorResult = mockMvc.perform(get("/api/vendors/{id}", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Danang Diving Adventure"))
                .andReturn();

        JsonNode vendorNode = objectMapper.readTree(vendorResult.getResponse().getContentAsString());
        long activeCount = vendorNode.get("activeServicesCount").asLong();
        assertThat(activeCount).isGreaterThanOrEqualTo(2);

        // Step 3: Query public vendor storefront
        mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value((int) activeCount));
    }

    @Test
    @DisplayName("Tier 3.3 - Notification Lifecycle Flow: Check count -> Receive -> Increment -> Mark read -> Decrement -> Mark all read -> Zero")
    void testTier3_NotificationLifecycleFlow() throws Exception {
        assumeEndpoint("/api/notifications/unread-count", RequestMethod.GET);
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);
        assumeEndpoint("/api/notifications/read-all", RequestMethod.PATCH);

        // Step 1: Initial unread count
        MvcResult step1 = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long initialCount = objectMapper.readTree(step1.getResponse().getContentAsString()).get("unreadCount").asLong();

        // Step 2: System sends a new notification
        NotificationJpaEntity newNotif = new NotificationJpaEntity();
        newNotif.setUserId(customerUser.getId());
        newNotif.setType("BOOKING_UPDATE");
        newNotif.setChannel(NotificationChannel.IN_APP);
        newNotif.setTitle("Xác nhận đặt chỗ thành công");
        newNotif.setBody("Mã đơn của bạn đã được đối tác xác nhận.");
        newNotif.setStatus(NotificationStatus.SENT);
        newNotif.setSentAt(OffsetDateTime.now());
        newNotif = notificationRepository.save(newNotif);

        // Step 3: Count increments by 1
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(initialCount + 1));

        // Step 4: Mark new notification as read
        mockMvc.perform(patch("/api/notifications/{id}/read", newNotif.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Step 5: Count decrements back to initialCount
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(initialCount));

        // Step 6: Mark all notifications as read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Step 7: Final count must be 0
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("Tier 3.4 - Order Payment to Receipt Flow: Payment completed -> Query JSON Receipt -> Query PDF Receipt")
    void testTier3_OrderPaymentToReceiptLifecycle() throws Exception {
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        // Step 1: Trigger payment success event if listener active
        if (isBeanAvailable("paymentNotificationEventListener")) {
            applicationContext.publishEvent(new PaymentSuccessEvent(paidOrder.getId(), customerUser.getId(), UUID.randomUUID()));
        }

        // Step 2: Customer retrieves JSON receipt
        MvcResult jsonResult = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(paidOrder.getId().toString()))
                .andExpect(jsonPath("$.totalAmount").value(900000.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn();

        JsonNode jsonReceipt = objectMapper.readTree(jsonResult.getResponse().getContentAsString());
        assertThat(jsonReceipt.get("items").size()).isEqualTo(1);
        assertThat(jsonReceipt.get("items").get(0).get("serviceId").asText()).isEqualTo(serviceA1.getId().toString());

        // Step 3: Customer downloads PDF receipt
        MvcResult pdfResult = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .param("format", "pdf")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, Math.min(pdfBytes.length, 5))).startsWith("%PDF");
    }

    @Test
    @DisplayName("Tier 3.5 - Vendor Storefront to Service Detail Flow: Storefront items consistently match Service Detail representation")
    void testTier3_VendorStorefrontToServiceDetailFlow() throws Exception {
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);
        assumeFieldPresent(ServiceDetailResponse.class, "vendorId");

        // Step 1: Query vendor storefront
        MvcResult storefrontResult = mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode storefrontServices = objectMapper.readTree(storefrontResult.getResponse().getContentAsString()).get("content");
        assertThat(storefrontServices.size()).isGreaterThanOrEqualTo(1);

        UUID firstServiceId = UUID.fromString(storefrontServices.get(0).get("id").asText());

        // Step 2: Query service detail
        mockMvc.perform(get("/api/services/{id}", firstServiceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstServiceId.toString()))
                .andExpect(jsonPath("$.vendorId").value(vendorProfileA.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Diving Adventure"));
    }
}
