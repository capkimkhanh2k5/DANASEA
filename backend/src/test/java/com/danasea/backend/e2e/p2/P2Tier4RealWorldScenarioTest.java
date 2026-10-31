package com.danasea.backend.e2e.p2;

import com.danasea.backend.e2e.BaseE2ETest;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
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

@DisplayName("P2 E2E Tier 4: Real-World Application User Scenarios")
public class P2Tier4RealWorldScenarioTest extends BaseE2ETest {

    @Test
    @DisplayName("Scenario 1: End-to-End Customer Journey: Discovery -> Service Detail -> Vendor Storefront -> Order Receipt (JSON & PDF)")
    void testTier4_CustomerEndToEndDiscoveryBookingReceiptJourney() throws Exception {
        assumeParamSupported(CatalogController.class, "searchServices", "date");
        assumeParamSupported(CatalogController.class, "searchServices", "guests");
        assumeParamSupported(CatalogController.class, "searchServices", "minRating");
        assumeFieldPresent(ServiceDetailResponse.class, "vendorId");
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        LocalDate tomorrow = slotA1TomorrowMorning.getDate();

        // 1. Customer searches services for tomorrow with 2 guests and rating >= 4.5
        MvcResult discoveryResult = mockMvc.perform(get("/api/services")
                        .param("date", tomorrow.toString())
                        .param("guests", "2")
                        .param("minRating", "4.50")
                        .param("sortBy", "rating_desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode discoveryContent = objectMapper.readTree(discoveryResult.getResponse().getContentAsString()).get("content");
        assertThat(discoveryContent.size()).isGreaterThanOrEqualTo(1);

        UUID selectedTourId = UUID.fromString(discoveryContent.get(0).get("id").asText());

        // 2. Customer views service detail
        MvcResult detailResult = mockMvc.perform(get("/api/services/{id}", selectedTourId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(selectedTourId.toString()))
                .andReturn();

        JsonNode detailNode = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        assertThat(detailNode.has("duration") || detailNode.has("durationMinutes")).isTrue();
        UUID vendorId = UUID.fromString(detailNode.get("vendorId").asText());

        // 3. Customer checks vendor public storefront & profile
        mockMvc.perform(get("/api/vendors/{id}", vendorId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendorId.toString()))
                .andExpect(jsonPath("$.businessName").value("Danang Diving Adventure"))
                .andExpect(jsonPath("$.badgeTier").value("TOP_RATED"));

        // 4. Customer retrieves paid order receipt in JSON
        MvcResult jsonReceiptResult = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(paidOrder.getId().toString()))
                .andExpect(jsonPath("$.totalAmount").value(900000.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn();

        JsonNode receiptJson = objectMapper.readTree(jsonReceiptResult.getResponse().getContentAsString());
        assertThat(receiptJson.get("items").size()).isGreaterThanOrEqualTo(1);

        // 5. Customer downloads paid order receipt as RFC-compliant PDF binary
        MvcResult pdfReceiptResult = mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .param("format", "pdf")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = pdfReceiptResult.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, Math.min(pdfBytes.length, 5))).startsWith("%PDF");
    }

    @Test
    @DisplayName("Scenario 2: Multi-User Privacy & Notification Isolation: Cross-tenant IDOR defense")
    void testTier4_MultiUserPrivacyAndNotificationIsolationJourney() throws Exception {
        assumeEndpoint("/api/notifications/unread-count", RequestMethod.GET);
        assumeEndpoint("/api/notifications/{id}/read", RequestMethod.PATCH);
        assumeEndpoint("/api/notifications/read-all", RequestMethod.PATCH);
        assumeEndpoint("/api/orders/{id}/receipt", RequestMethod.GET);

        // Seed notification for attacker
        NotificationJpaEntity attackerNotif = new NotificationJpaEntity();
        attackerNotif.setUserId(attackerUser.getId());
        attackerNotif.setType("SECURITY_ALERT");
        attackerNotif.setChannel(NotificationChannel.IN_APP);
        attackerNotif.setTitle("Đăng nhập từ thiết bị mới");
        attackerNotif.setBody("Phát hiện phiên đăng nhập mới trên thiết bị lạ.");
        attackerNotif.setStatus(NotificationStatus.SENT);
        attackerNotif.setSentAt(OffsetDateTime.now());
        attackerNotif = notificationRepository.save(attackerNotif);

        // 1. Customer reads initial unread count
        MvcResult custCountResult1 = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long custUnread1 = objectMapper.readTree(custCountResult1.getResponse().getContentAsString()).get("unreadCount").asLong();

        // Attacker reads initial unread count
        MvcResult attCountResult1 = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        long attUnread1 = objectMapper.readTree(attCountResult1.getResponse().getContentAsString()).get("unreadCount").asLong();

        // 2. Customer marks their notification as read
        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Customer's count decreases
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(custUnread1 - 1));

        // Attacker's count remains unchanged!
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(attUnread1));

        // 3. Attacker tries to tamper with Customer's notification (IDOR attack)
        mockMvc.perform(patch("/api/notifications/{id}/read", sampleNotificationCustomer.getId())
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404));

        // 4. Attacker tries to steal Customer's order receipt (IDOR attack)
        mockMvc.perform(get("/api/orders/{id}/receipt", paidOrder.getId())
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // 5. Customer marks all read: Attacker's notifications remain intact
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + attackerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(attUnread1));
    }

    @Test
    @DisplayName("Scenario 3: Public Surface Data Leakage Audit: Zero sensitive financial fields across all public endpoints")
    void testTier4_StorefrontIntegrityAndSensitiveDataLeakageAudit() throws Exception {
        assumeEndpoint("/api/vendors/{id}", RequestMethod.GET);
        assumeEndpoint("/api/vendors/{id}/services", RequestMethod.GET);

        // 1. Audit /api/services
        MvcResult catalogRes = mockMvc.perform(get("/api/services").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        assertNoFinancialLeaks(catalogRes.getResponse().getContentAsString());

        // 2. Audit /api/services/{id}
        MvcResult detailRes = mockMvc.perform(get("/api/services/{id}", serviceA1.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        assertNoFinancialLeaks(detailRes.getResponse().getContentAsString());

        // 3. Audit /api/vendors/{id}
        MvcResult vendorRes = mockMvc.perform(get("/api/vendors/{id}", vendorProfileA.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        assertNoFinancialLeaks(vendorRes.getResponse().getContentAsString());

        // 4. Audit /api/vendors/{id}/services
        MvcResult storeRes = mockMvc.perform(get("/api/vendors/{id}/services", vendorProfileA.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        assertNoFinancialLeaks(storeRes.getResponse().getContentAsString());
    }

    private void assertNoFinancialLeaks(String body) {
        assertThat(body).doesNotContain("0123456789");        // vendor A bank account number
        assertThat(body).doesNotContain("9876543210");        // vendor B bank account number
        assertThat(body).doesNotContain("0401234567");        // vendor A tax code
        assertThat(body).doesNotContain("0409876543");        // vendor B tax code
        assertThat(body).doesNotContain("bankAccountNumber");
        assertThat(body).doesNotContain("bankAccountHolder");
        assertThat(body).doesNotContain("taxCode");
        assertThat(body).doesNotContain("passwordHash");
    }
}
