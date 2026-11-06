package com.danasea.backend.modules.service.domain.models;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record OperatingSchedule(LocalDate startsOn, LocalDate endsOn, boolean active, List<WeeklyRule> rules) {
    public static final int BOOKING_HORIZON_DAYS = 90;

    public OperatingSchedule {
        if (startsOn == null || (endsOn != null && endsOn.isBefore(startsOn))) {
            throw new IllegalArgumentException("Schedule must have a valid effective date range");
        }
        if (rules == null || rules.isEmpty() || rules.size() > 84) {
            throw new IllegalArgumentException("Schedule must have between 1 and 84 weekly rules");
        }
        var keys = new HashSet<WeeklyKey>();
        for (WeeklyRule rule : rules) {
            if (rule == null || !keys.add(new WeeklyKey(rule.dayOfWeek(), rule.startTime()))) {
                throw new IllegalArgumentException("Weekly rules must have unique days and start times");
            }
        }
        rules = rules.stream()
                .sorted(Comparator.comparing(WeeklyRule::dayOfWeek).thenComparing(WeeklyRule::startTime))
                .toList();
        var equipmentEndTimes = new HashMap<EquipmentDay, LocalTime>();
        for (WeeklyRule rule : rules) {
            for (Unit unit : rule.units()) {
                if (unit.equipmentId() != null) {
                    var key = new EquipmentDay(unit.equipmentId(), rule.dayOfWeek());
                    LocalTime previousEnd = equipmentEndTimes.put(key, rule.endTime());
                    if (previousEnd != null && rule.startTime().isBefore(previousEnd)) {
                        throw new IllegalArgumentException("Weekly rules cannot assign the same equipment to overlapping slots");
                    }
                }
            }
        }
    }

    public boolean includes(LocalDate date, LocalTime startTime) {
        return active && !date.isBefore(startsOn) && (endsOn == null || !date.isAfter(endsOn))
                && rules.stream().anyMatch(rule -> rule.dayOfWeek() == date.getDayOfWeek()
                        && rule.startTime().equals(startTime));
    }

    public List<Departure> departures(LocalDateTime now) {
        if (!active) {
            return List.of();
        }
        LocalDate today = now.toLocalDate();
        LocalDate lastDay = today.plusDays(BOOKING_HORIZON_DAYS - 1);
        if (endsOn != null && endsOn.isBefore(lastDay)) {
            lastDay = endsOn;
        }
        var departures = new ArrayList<Departure>();
        for (LocalDate date = startsOn.isAfter(today) ? startsOn : today;
                !date.isAfter(lastDay); date = date.plusDays(1)) {
            for (WeeklyRule rule : rules) {
                if (rule.dayOfWeek() == date.getDayOfWeek() && date.atTime(rule.startTime()).isAfter(now)) {
                    departures.add(new Departure(date, rule));
                }
            }
        }
        return List.copyOf(departures);
    }

    private record WeeklyKey(DayOfWeek day, LocalTime time) {}
    private record EquipmentDay(UUID equipmentId, DayOfWeek day) {}

    public record Departure(LocalDate date, WeeklyRule rule) {}

    public record WeeklyRule(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                             InventoryType inventoryType, Integer capacity, List<Unit> units) {
        public WeeklyRule {
            if (dayOfWeek == null || startTime == null || endTime == null || !startTime.isBefore(endTime)
                    || startTime.getNano() != 0 || endTime.getNano() != 0 || inventoryType == null) {
                throw new IllegalArgumentException("Weekly rules require a day, whole-second times and inventory type");
            }
            if (inventoryType == InventoryType.PERSON_LIMIT) {
                if (capacity == null || capacity < 1 || (units != null && !units.isEmpty())) {
                    throw new IllegalArgumentException("Person inventory requires positive capacity and no units");
                }
                units = List.of();
            } else {
                if (capacity != null || units == null || units.isEmpty() || units.size() > 100) {
                    throw new IllegalArgumentException("Unit inventory requires 1 to 100 units and no total capacity");
                }
                var numbers = new HashSet<Integer>();
                var equipmentIds = new HashSet<UUID>();
                long totalCapacity = 0;
                for (Unit unit : units) {
                    if (unit == null || !numbers.add(unit.unitNumber())
                            || (unit.equipmentId() != null && !equipmentIds.add(unit.equipmentId()))) {
                        throw new IllegalArgumentException("Unit numbers and equipment must be unique per departure");
                    }
                    totalCapacity += unit.capacity();
                }
                if (totalCapacity > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Total unit capacity exceeds the supported integer capacity");
                }
                units = List.copyOf(units);
            }
        }

        public int totalCapacity() {
            return inventoryType == InventoryType.PERSON_LIMIT
                    ? capacity
                    : units.stream().mapToInt(Unit::capacity).sum();
        }
    }

    public record Unit(int unitNumber, int capacity, UUID equipmentId) {
        public Unit {
            if (unitNumber < 1 || capacity < 1) {
                throw new IllegalArgumentException("Unit number and capacity must be positive");
            }
        }
    }
}
