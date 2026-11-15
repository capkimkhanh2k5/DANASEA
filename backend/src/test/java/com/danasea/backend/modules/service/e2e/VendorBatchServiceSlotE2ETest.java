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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
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
    void createsOnlyTheFourSelectedDates() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate first = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";

        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "dates": ["%s", "%s", "%s", "%s"],
                                    "slotTemplates": [
                                        {
                                            "startTime": "08:00",
                                            "endTime": "10:00",
                                            "inventoryType": "PERSON_LIMIT",
                                            "capacity": 10
                                        }
                                    ]
                                }
                                """.formatted(first, first.plusDays(1), first.plusDays(4), first.plusDays(5)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCreated").value(4));

        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT date FROM service_slots WHERE service_id = ? ORDER BY date",
                LocalDate.class, serviceId)).containsExactly(first, first.plusDays(1), first.plusDays(4), first.plusDays(5));
    }

    @Test
    void persistsTheFullThirtyOneDayWorkloadAtTheLimit() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate first = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        String batchUrl = VENDOR_API_BASE + "/" + serviceId + "/slots/batch";
        String slotTemplates = IntStream.range(8, 12)
                .mapToObj(hour -> """
                        {
                            "startTime": "%02d:00",
                            "endTime": "%02d:00",
                            "inventoryType": "SHARED_CAPACITY_UNITS",
                            "units": [
                                {"unitNumber": 1, "capacity": 4},
                                {"unitNumber": 2, "capacity": 4},
                                {"unitNumber": 3, "capacity": 4},
                                {"unitNumber": 4, "capacity": 4}
                            ]
                        }
                        """.formatted(hour, hour + 1))
                .collect(Collectors.joining(","));

        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "startDate": "%s",
                                    "endDate": "%s",
                                    "daysOfWeek": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
                                    "slotTemplates": [%s]
                                }
                                """.formatted(first, first.plusDays(30), slotTemplates))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCreated").value(124));

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM service_slots WHERE service_id = ?",
                Integer.class, serviceId)).isEqualTo(124);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM service_slot_units units
                JOIN service_slots slots ON slots.id = units.slot_id
                WHERE slots.service_id = ?
                """, Integer.class, serviceId)).isEqualTo(496);
    }

    @ParameterizedTest
    @CsvSource({
        "en, A departure already exists at 08:00 on %s.",
        "vi, Đã có ca khởi hành lúc 08:00 ngày %s."
    })
    void rejectsBatchWhenSlotAlreadyExistsAndRollsBack(String language, String message) throws Exception {
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
                        .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value(message.formatted(end)));

        // Verify transaction was marked rollback-only due to exception in batch use case
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();
    }

    @Test
    void rejectsBatchWithPastDate() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate yesterday = LocalDate.now(Booking.VIETNAM_ZONE).minusDays(1);
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
                """.formatted(yesterday, yesterday, yesterday.getDayOfWeek());

        mockMvc.perform(authenticateAsVendorA(post(batchUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
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
