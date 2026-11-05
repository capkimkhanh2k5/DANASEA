package com.danasea.backend.modules.service.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class OperatingScheduleTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final LocalTime MORNING = LocalTime.of(8, 0);
    private static final LocalTime AFTERNOON = LocalTime.of(14, 0);

    @Test
    void rollingHorizonIncludesTodayAndEightyNineDaysAhead() {
        var rules = Stream.of(DayOfWeek.values()).map(day -> rule(day, AFTERNOON)).toList();
        var schedule = new OperatingSchedule(TODAY.minusMonths(1), null, true, rules);

        var departures = schedule.departures(TODAY.atTime(12, 0));

        assertThat(departures).hasSize(90);
        assertThat(departures.getFirst().date()).isEqualTo(TODAY);
        assertThat(departures.getLast().date()).isEqualTo(TODAY.plusDays(89));
    }

    @Test
    void matchesWeeklyRulesAndDoesNotGenerateStartedOrOutOfSeasonDepartures() {
        var schedule = new OperatingSchedule(TODAY.minusDays(1), TODAY.plusWeeks(1), true,
                List.of(rule(TODAY.getDayOfWeek(), MORNING), rule(TODAY.getDayOfWeek(), AFTERNOON)));

        assertThat(schedule.departures(TODAY.atTime(MORNING)))
                .extracting(departure -> departure.date().atTime(departure.rule().startTime()))
                .containsExactly(TODAY.atTime(AFTERNOON), TODAY.plusWeeks(1).atTime(MORNING),
                        TODAY.plusWeeks(1).atTime(AFTERNOON));
    }

    @Test
    void inactiveScheduleGeneratesNoDepartures() {
        var schedule = new OperatingSchedule(TODAY, null, false,
                List.of(rule(TODAY.getDayOfWeek(), AFTERNOON)));

        assertThat(schedule.departures(TODAY.atStartOfDay())).isEmpty();
    }

    @Test
    void rejectsDuplicateDepartureKeysAndEquipmentReuseInOneDeparture() {
        var morning = rule(TODAY.getDayOfWeek(), MORNING);
        assertThatIllegalArgumentException().isThrownBy(
                () -> new OperatingSchedule(TODAY, null, true, List.of(morning, morning)));

        var equipmentId = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> new OperatingSchedule.WeeklyRule(
                TODAY.getDayOfWeek(), MORNING, MORNING.plusHours(2), InventoryType.SHARED_CAPACITY_UNITS,
                null, List.of(new OperatingSchedule.Unit(1, 8, equipmentId),
                        new OperatingSchedule.Unit(2, 8, equipmentId))));
    }

    @Test
    void sameEquipmentMayBeReusedOnlyWhenWeeklyTimeRangesDoNotOverlap() {
        var equipment = new OperatingSchedule.Unit(1, 8, UUID.randomUUID());
        var day = TODAY.getDayOfWeek();
        var morning = new OperatingSchedule.WeeklyRule(day, MORNING, MORNING.plusHours(2),
                InventoryType.SHARED_CAPACITY_UNITS, null, List.of(equipment));
        var overlapping = new OperatingSchedule.WeeklyRule(day, MORNING.plusHours(1), AFTERNOON,
                InventoryType.SHARED_CAPACITY_UNITS, null, List.of(equipment));
        var adjacent = new OperatingSchedule.WeeklyRule(day, MORNING.plusHours(2), AFTERNOON,
                InventoryType.SHARED_CAPACITY_UNITS, null, List.of(equipment));

        assertThatIllegalArgumentException().isThrownBy(
                () -> new OperatingSchedule(TODAY, null, true, List.of(overlapping, morning)));
        assertThat(new OperatingSchedule(TODAY, null, true, List.of(adjacent, morning)).rules())
                .containsExactly(morning, adjacent);
    }

    private static OperatingSchedule.WeeklyRule rule(DayOfWeek day, LocalTime time) {
        return new OperatingSchedule.WeeklyRule(day, time, time.plusHours(2),
                InventoryType.PERSON_LIMIT, 12, List.of());
    }
}
