package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import com.danasea.backend.modules.service.domain.models.OperatingSchedule;
import com.danasea.backend.modules.service.domain.models.OperatingScheduleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OperatingScheduleStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Optional<OperatingSchedule> find(UUID serviceId) {
        return jdbc.query("""
                SELECT starts_on, ends_on, active, weekly_rules
                FROM service_operating_schedules WHERE service_id = ?
                """, (rs, row) -> new OperatingSchedule(
                rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                rs.getBoolean("active"), readRules(rs.getString("weekly_rules"))), serviceId).stream().findFirst();
    }

    public void save(UUID serviceId, OperatingSchedule schedule) {
        jdbc.update("""
                INSERT INTO service_operating_schedules (service_id, starts_on, ends_on, active, weekly_rules)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (service_id) DO UPDATE SET starts_on = EXCLUDED.starts_on, ends_on = EXCLUDED.ends_on,
                    active = EXCLUDED.active, weekly_rules = EXCLUDED.weekly_rules, updated_at = now()
                """, serviceId, schedule.startsOn(), schedule.endsOn(), schedule.active(), writeRules(schedule.rules()));
    }

    public List<UUID> findServicesToGenerate(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT p.service_id
                FROM service_operating_schedules p JOIN services s ON s.id = p.service_id
                WHERE p.active AND s.status = 'PUBLISHED' AND p.starts_on <= ?
                  AND (p.ends_on IS NULL OR p.ends_on >= ?)
                ORDER BY p.service_id
                """, (rs, row) -> rs.getObject(1, UUID.class), to, from);
    }

    public List<OperatingScheduleException> findExceptions(UUID serviceId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT id, date_from, date_to, start_time, reason
                FROM service_operating_schedule_exceptions
                WHERE service_id = ? AND date_to >= ? AND date_from <= ?
                ORDER BY date_from, start_time, id
                """, (rs, row) -> readException(rs), serviceId, from, to);
    }

    public List<OperatingScheduleException> findExceptions(UUID serviceId) {
        return jdbc.query("""
                SELECT id, date_from, date_to, start_time, reason
                FROM service_operating_schedule_exceptions WHERE service_id = ?
                ORDER BY date_from, start_time, id
                """, (rs, row) -> readException(rs), serviceId);
    }

    public boolean blocks(UUID serviceId, LocalDate date, LocalTime time) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM service_operating_schedule_exceptions
                    WHERE service_id = ? AND date_from <= ? AND date_to >= ?
                      AND (start_time IS NULL OR start_time = CAST(? AS time(0)))
                )
                """, Boolean.class, serviceId, date, date, time));
    }

    public void addException(UUID serviceId, OperatingScheduleException exception) {
        jdbc.update("""
                INSERT INTO service_operating_schedule_exceptions
                    (id, service_id, date_from, date_to, start_time, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, exception.id(), serviceId, exception.dateFrom(), exception.dateTo(), exception.startTime(),
                exception.reason());
    }

    public boolean deleteException(UUID serviceId, UUID exceptionId) {
        return jdbc.update("DELETE FROM service_operating_schedule_exceptions WHERE service_id = ? AND id = ?",
                serviceId, exceptionId) == 1;
    }

    public List<ExistingDeparture> findExisting(UUID serviceId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT id, date, start_time FROM service_slots
                WHERE service_id = ? AND date BETWEEN ? AND ?
                """, (rs, row) -> readDeparture(rs), serviceId, from, to);
    }

    public List<ExistingDeparture> lockGeneratedForSalesUpdate(UUID serviceId, LocalDateTime now) {
        return jdbc.query("""
                SELECT s.id, s.date, s.start_time
                FROM service_slots s JOIN service_operating_schedule_slots g ON g.slot_id = s.id
                WHERE s.service_id = ? AND s.status = 'OPEN' AND s.date + s.start_time > ?
                ORDER BY s.id FOR UPDATE OF s
                """, (rs, row) -> readDeparture(rs), serviceId, now);
    }

    public List<ExistingDeparture> lockExceptionDepartures(UUID serviceId, OperatingScheduleException exception,
                                                            LocalDateTime now) {
        return jdbc.query("""
                SELECT id, date, start_time FROM service_slots
                WHERE service_id = ? AND status = 'OPEN' AND date + start_time > ?
                  AND date BETWEEN ? AND ?
                  AND (CAST(? AS time) IS NULL OR start_time = CAST(? AS time))
                ORDER BY id FOR UPDATE
                """, (rs, row) -> readDeparture(rs), serviceId, now, exception.dateFrom(), exception.dateTo(),
                exception.startTime(), exception.startTime());
    }

    public int closeSales(List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.update("""
                UPDATE service_slots SET status = 'CLOSED', updated_at = now()
                WHERE id = ANY(?) AND status = 'OPEN'
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", ids.toArray(UUID[]::new))));
    }

    public Optional<UUID> insertDeparture(UUID serviceId, OperatingSchedule.Departure departure) {
        var slotId = UUID.randomUUID();
        var rule = departure.rule();
        int inserted = jdbc.update("""
                INSERT INTO service_slots
                    (id, service_id, date, start_time, end_time, capacity, booked_count, status,
                     inventory_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'OPEN', ?, now(), now())
                ON CONFLICT (service_id, date, start_time) DO NOTHING
                """, slotId, serviceId, departure.date(), rule.startTime(), rule.endTime(), rule.totalCapacity(),
                rule.inventoryType().name());
        if (inserted == 0) {
            return Optional.empty();
        }
        if (!rule.units().isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO service_slot_units
                        (id, slot_id, unit_number, capacity, booked_count, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 0, now(), now())
                    """, rule.units().stream().map(unit -> new Object[] {
                            UUID.randomUUID(), slotId, unit.unitNumber(), unit.capacity()
                    }).toList(), new int[] {Types.OTHER, Types.OTHER, Types.INTEGER, Types.INTEGER});
        }
        jdbc.update("INSERT INTO service_operating_schedule_slots (slot_id) VALUES (?)", slotId);
        return Optional.of(slotId);
    }

    private static ExistingDeparture readDeparture(ResultSet rs) throws SQLException {
        return new ExistingDeparture(rs.getObject("id", UUID.class), rs.getObject("date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class));
    }

    private static OperatingScheduleException readException(ResultSet rs) throws SQLException {
        return new OperatingScheduleException(rs.getObject("id", UUID.class),
                rs.getObject("date_from", LocalDate.class), rs.getObject("date_to", LocalDate.class),
                rs.getObject("start_time", LocalTime.class), rs.getString("reason"));
    }

    private List<OperatingSchedule.WeeklyRule> readRules(String rules) {
        try {
            return json.readValue(rules, new TypeReference<List<OperatingSchedule.WeeklyRule>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not decode operating schedule rules", e);
        }
    }

    private String writeRules(List<OperatingSchedule.WeeklyRule> rules) {
        try {
            return json.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode operating schedule rules", e);
        }
    }

    public record ExistingDeparture(UUID id, LocalDate date, LocalTime startTime) {}
}
