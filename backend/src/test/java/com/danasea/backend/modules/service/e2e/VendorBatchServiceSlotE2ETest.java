package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

class VendorBatchServiceSlotE2ETest extends BaseServiceE2ETest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsBatchSlotsSuccessfully() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate start = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        LocalDate end = start.plusDays(2);
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";

        String jsonPayload = """
                {
                    "startDate": "%s",
                    "endDate": "%s",
                    "daysOfWeek": ["%s", "%s", "%s"],
                    "slotTemplates": [
                        {
                            "startTime": "08:00",
                            "endTime": "10:00",
                            "inventoryType": "PERSON_LIMIT",
                            "capacity": 10
                        },
                        {
                            "startTime": "14:00",
                            "endTime": "16:00",
                            "inventoryType": "PERSON_LIMIT",
                            "capacity": 12
                        }
                    ]
                }
                """.formatted(
                start,
                end,
                start.getDayOfWeek(),
                start.plusDays(1).getDayOfWeek(),
                start.plusDays(2).getDayOfWeek()
        );

        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCreated").value(6))
                .andExpect(jsonPath("$.slots.length()").value(6));

        entityManager.flush();

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM service_slots WHERE service_id = ? AND date BETWEEN ? AND ?",
                Integer.class,
                serviceId,
                start,
                end
        );
        assertThat(count).isEqualTo(6);
    }

    @Test
    void rejectsBatchWhenSlotAlreadyExistsAndRollsBack() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate start = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(3);
        LocalDate end = start.plusDays(1);
        String singleSlotUrl = VENDOR_API_BASE + "/" + serviceId + "/slots";
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";

        // Pre-create slot on the second day
        mockMvc.perform(authenticateAsVendorA(post(singleSlotUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "date": "%s",
                                    "startTime": "08:00",
                                    "endTime": "10:00",
                                    "inventoryType": "PERSON_LIMIT",
                                    "capacity": 10
                                }
                                """.formatted(end))))
                .andExpect(status().isCreated());

        entityManager.flush();

        String jsonPayload = """
                {
                    "startDate": "%s",
                    "endDate": "%s",
                    "daysOfWeek": ["%s", "%s"],
                    "slotTemplates": [
                        {
                            "startTime": "08:00",
                            "endTime": "10:00",
                            "inventoryType": "PERSON_LIMIT",
                            "capacity": 10
                        }
                    ]
                }
                """.formatted(start, end, start.getDayOfWeek(), end.getDayOfWeek());

        // Batch attempt should fail because day 2 has duplicate slot
        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Verify transaction was marked rollback-only due to exception in batch use case
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();
    }

    @Test
    void rejectsBatchWithPastDate() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";

        String jsonPayload = """
                {
                    "startDate": "%s",
                    "endDate": "%s",
                    "daysOfWeek": ["%s"],
                    "slotTemplates": [
                        {
                            "startTime": "00:01",
                            "endTime": "01:00",
                            "inventoryType": "PERSON_LIMIT",
                            "capacity": 10
                        }
                    ]
                }
                """.formatted(today, today, today.getDayOfWeek());

        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsBatchWhenVendorDoesNotOwnService() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate date = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";

        String jsonPayload = """
                {
                    "startDate": "%s",
                    "endDate": "%s",
                    "daysOfWeek": ["%s"],
                    "slotTemplates": [
                        {
                            "startTime": "08:00",
                            "endTime": "10:00",
                            "inventoryType": "PERSON_LIMIT",
                            "capacity": 10
                        }
                    ]
                }
                """.formatted(date, date, date.getDayOfWeek());

        mockMvc.perform(authenticateAsVendorB(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isForbidden());
    }
}
