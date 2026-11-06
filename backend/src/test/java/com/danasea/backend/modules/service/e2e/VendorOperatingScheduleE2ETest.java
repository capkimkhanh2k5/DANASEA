package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class VendorOperatingScheduleE2ETest extends BaseServiceE2ETest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void materializesPhysicalEquipmentAndPauseClosesOnlyGeneratedSales() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate date = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);
        String slots = VENDOR_API_BASE + "/" + serviceId + "/slots";
        String schedule = VENDOR_API_BASE + "/" + serviceId + "/operating-schedule";
        var equipment = mockMvc.perform(authenticateAsVendorA(post("/api/vendor/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cano B","compatibilityGroup":"cano-8","capacity":8}
                                """)))
                .andExpect(status().isCreated()).andReturn();
        UUID equipmentId = id(objectMapper.readTree(equipment.getResponse().getContentAsString()));
        String rules = """
                {"startsOn":"%s","endsOn":"%s","active":%s,"rules":[
                    {"dayOfWeek":"%s","startTime":"08:00","endTime":"10:00","inventoryType":"SHARED_CAPACITY_UNITS",
                     "units":[{"unitNumber":1,"capacity":8,"equipmentId":"%s"}]}
                ]}
                """;
        mockMvc.perform(authenticateAsVendorA(put(schedule).contentType(MediaType.APPLICATION_JSON)
                        .content(rules.formatted(date, date, true, date.getDayOfWeek(), equipmentId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdSlots").value(1));
        assertThat(jdbc.queryForObject("""
                SELECT u.equipment_id FROM service_slot_units u JOIN service_slots s ON s.id = u.slot_id
                WHERE s.service_id = ? AND s.date = ?
                """, UUID.class, serviceId, date)).isEqualTo(equipmentId);

        mockMvc.perform(authenticateAsVendorA(post(slots).contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"%s","startTime":"14:00","endTime":"16:00","inventoryType":"PERSON_LIMIT","capacity":8}
                """.formatted(date)))).andExpect(status().isCreated());
        entityManager.flush();
        mockMvc.perform(authenticateAsVendorA(put(schedule).contentType(MediaType.APPLICATION_JSON)
                        .content(rules.formatted(date, date, false, date.getDayOfWeek(), equipmentId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closedSlots").value(1));
        assertThat(slotStatuses(serviceId, date)).containsExactly("CLOSED", "OPEN");
    }

    @Test
    void blocksExistingAndMissingDeparturesAndCannotBeBypassedByLegacySlotApi() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        LocalDate date = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);
        String slots = VENDOR_API_BASE + "/" + serviceId + "/slots";
        String schedule = VENDOR_API_BASE + "/" + serviceId + "/operating-schedule";
        String rules = """
                {"startsOn":"%s","endsOn":"%s","active":true,"rules":[
                    {"dayOfWeek":"%s","startTime":"08:00","endTime":"10:00","inventoryType":"PERSON_LIMIT","capacity":8},
                    {"dayOfWeek":"%s","startTime":"14:00","endTime":"16:00","inventoryType":"PERSON_LIMIT","capacity":8}
                ]}
                """.formatted(date, date, date.getDayOfWeek(), date.getDayOfWeek());

        mockMvc.perform(authenticateAsVendorB(put(schedule).contentType(MediaType.APPLICATION_JSON).content(rules)))
                .andExpect(status().isForbidden());

        // Persist a morning exclusion before the slot exists, then materialize the schedule.
        mockMvc.perform(authenticateAsVendorA(post(schedule + "/exceptions")
                        .contentType(MediaType.APPLICATION_JSON).content(exception(date, "08:00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closedSlots").value(0));
        mockMvc.perform(authenticateAsVendorA(put(schedule).contentType(MediaType.APPLICATION_JSON).content(rules)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdSlots").value(1));
        mockMvc.perform(authenticateAsVendorA(get(schedule)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules.length()").value(2));
        assertThat(slotStatuses(serviceId, date)).containsExactly("OPEN");

        mockMvc.perform(authenticateAsVendorA(post(slots).contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"%s","startTime":"08:00","endTime":"10:00","inventoryType":"PERSON_LIMIT","capacity":8}
                """.formatted(date)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATING_SCHEDULE_CONFLICT"));

        UUID afternoonId = jdbc.queryForObject("""
                SELECT id FROM service_slots WHERE service_id = ? AND date = ? AND start_time = ?
                """, UUID.class, serviceId, date, LocalTime.of(14, 0));
        var created = mockMvc.perform(authenticateAsVendorA(post(schedule + "/exceptions")
                        .contentType(MediaType.APPLICATION_JSON).content(exception(date, "14:00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closedSlots").value(1))
                .andReturn();
        String exceptionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("exception").path("id").asText();
        mockMvc.perform(authenticateAsVendorA(post(schedule + "/exceptions")
                        .contentType(MediaType.APPLICATION_JSON).content(exception(date, "14:00"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exception.id").value(exceptionId));
        mockMvc.perform(authenticateAsVendorA(patch(slots + "/" + afternoonId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}")))
                .andExpect(status().isConflict());

        mockMvc.perform(authenticateAsVendorA(delete(schedule + "/exceptions/" + exceptionId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticateAsVendorA(put(schedule).contentType(MediaType.APPLICATION_JSON).content(rules)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdSlots").value(0));
        assertThat(slotStatuses(serviceId, date)).containsExactly("CLOSED");

        mockMvc.perform(authenticateAsVendorA(patch(slots + "/" + afternoonId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}")))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(slotStatuses(serviceId, date)).containsExactly("OPEN");
    }

    private static String exception(LocalDate date, String time) {
        return """
                {"dateFrom":"%s","dateTo":"%s","startTime":"%s","reason":"Vendor unavailable"}
                """.formatted(date, date, time);
    }

    private List<String> slotStatuses(UUID serviceId, LocalDate date) {
        return jdbc.queryForList("""
                SELECT status FROM service_slots WHERE service_id = ? AND date = ? ORDER BY start_time
                """, String.class, serviceId, date);
    }
}
