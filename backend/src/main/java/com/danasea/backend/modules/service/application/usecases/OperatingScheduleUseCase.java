package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.shared.presentation.ApiConflictException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.OperatingSchedule;
import com.danasea.backend.modules.service.domain.models.OperatingScheduleException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore.ExistingDeparture;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class OperatingScheduleUseCase {
    private final ServiceRepositoryPort services;
    private final VendorPort vendors;
    private final OperatingScheduleStore store;
    private final EquipmentAssignmentService equipment;

    @Transactional(readOnly = true)
    public Optional<OperatingSchedule> get(UUID userId, UUID serviceId) {
        ownedService(userId, serviceId, false);
        return store.find(serviceId);
    }

    @Transactional
    public ScheduleUpdate put(UUID userId, UUID serviceId, OperatingSchedule schedule) {
        Service service = ownedService(userId, serviceId, true);
        validateEquipment(service.getVendorId(), schedule);
        store.save(serviceId, schedule);

        LocalDateTime now = LocalDateTime.now(Booking.VIETNAM_ZONE);
        var superseded = store.lockGeneratedForSalesUpdate(serviceId, now).stream()
                .filter(slot -> !schedule.includes(slot.date(), slot.startTime()))
                .map(ExistingDeparture::id).toList();
        int closed = store.closeSales(superseded);
        return new ScheduleUpdate(schedule, materialize(service, schedule, now), closed);
    }

    @Transactional(readOnly = true)
    public List<OperatingScheduleException> listExceptions(UUID userId, UUID serviceId) {
        ownedService(userId, serviceId, false);
        return store.findExceptions(serviceId);
    }

    @Transactional
    public ExceptionUpdate addException(UUID userId, UUID serviceId, OperatingScheduleException exception) {
        ownedService(userId, serviceId, true);
        LocalDateTime now = LocalDateTime.now(Booking.VIETNAM_ZONE);
        if (exception.dateFrom().isBefore(now.toLocalDate())) {
            throw new IllegalArgumentException("Schedule exception cannot begin in the past");
        }

        var existing = store.findExceptions(serviceId, exception.dateFrom(), exception.dateTo()).stream()
                .filter(row -> row.dateFrom().equals(exception.dateFrom()) && row.dateTo().equals(exception.dateTo())
                        && Objects.equals(row.startTime(), exception.startTime()))
                .findFirst();
        if (existing.isPresent()) {
            if (!existing.get().reason().equals(exception.reason())) {
                throw new ApiConflictException("OPERATING_SCHEDULE_CONFLICT",
                        "This period already has an operating schedule exception");
            }
            return new ExceptionUpdate(existing.get(), 0);
        }

        store.addException(serviceId, exception);
        var slotIds = store.lockExceptionDepartures(serviceId, exception, now).stream()
                .map(ExistingDeparture::id).toList();
        return new ExceptionUpdate(exception, store.closeSales(slotIds));
    }

    @Transactional
    public void removeException(UUID userId, UUID serviceId, UUID exceptionId) {
        ownedService(userId, serviceId, true);
        if (!store.deleteException(serviceId, exceptionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating schedule exception not found");
        }
    }

    @Transactional(readOnly = true)
    public List<OperatingSchedule.Departure> missingDepartures(UUID serviceId) {
        return store.find(serviceId)
                .map(schedule -> missingDepartures(serviceId, schedule, LocalDateTime.now(Booking.VIETNAM_ZONE)))
                .orElseGet(List::of);
    }

    @Transactional
    public void generate(UUID serviceId, OperatingSchedule.Departure candidate) {
        var service = services.findByIdForUpdate(serviceId).filter(Service::isPublished);
        if (service.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(Booking.VIETNAM_ZONE);
        LocalDate date = candidate.date();
        LocalTime time = candidate.rule().startTime();
        if (!date.atTime(time).isAfter(now)
                || date.isAfter(now.toLocalDate().plusDays(OperatingSchedule.BOOKING_HORIZON_DAYS - 1))
                || store.blocks(serviceId, date, time)) {
            return;
        }
        store.find(serviceId).filter(schedule -> schedule.includes(date, time))
                .flatMap(schedule -> schedule.rules().stream()
                        .filter(rule -> rule.dayOfWeek() == date.getDayOfWeek() && rule.startTime().equals(time))
                        .findFirst())
                .ifPresent(rule -> materializeDeparture(service.get(), new OperatingSchedule.Departure(date, rule)));
    }

    private int materialize(Service service, OperatingSchedule schedule, LocalDateTime now) {
        int created = 0;
        if (service.isPublished()) {
            for (var departure : missingDepartures(service.getId(), schedule, now)) {
                if (materializeDeparture(service, departure)) {
                    created++;
                }
            }
        }
        return created;
    }

    private List<OperatingSchedule.Departure> missingDepartures(UUID serviceId, OperatingSchedule schedule,
                                                                LocalDateTime now) {
        var departures = schedule.departures(now);
        if (departures.isEmpty()) {
            return List.of();
        }
        LocalDate from = departures.getFirst().date();
        LocalDate to = departures.getLast().date();
        var exceptions = store.findExceptions(serviceId, from, to);
        var existing = store.findExisting(serviceId, from, to).stream()
                .map(slot -> new DepartureKey(slot.date(), slot.startTime()))
                .collect(Collectors.toSet());

        return departures.stream()
                .filter(departure -> !existing.contains(new DepartureKey(departure.date(), departure.rule().startTime())))
                .filter(departure -> exceptions.stream().noneMatch(
                        exception -> exception.matches(departure.date(), departure.rule().startTime())))
                .toList();
    }

    private boolean materializeDeparture(Service service, OperatingSchedule.Departure departure) {
        var slotId = store.insertDeparture(service.getId(), departure);
        if (slotId.isEmpty()) {
            return false;
        }
        var assignments = departure.rule().units().stream().filter(unit -> unit.equipmentId() != null)
                .collect(Collectors.toMap(OperatingSchedule.Unit::unitNumber, OperatingSchedule.Unit::equipmentId));
        if (!assignments.isEmpty()) {
            equipment.attachGenerated(service.getVendorId(), slotId.get(), assignments);
        }
        return true;
    }

    private void validateEquipment(UUID vendorId, OperatingSchedule schedule) {
        Map<UUID, Integer> expectedCapacities = new HashMap<>();
        for (var rule : schedule.rules()) {
            for (var unit : rule.units()) {
                if (unit.equipmentId() != null) {
                    Integer capacity = expectedCapacities.putIfAbsent(unit.equipmentId(), unit.capacity());
                    if (capacity != null && capacity != unit.capacity()) {
                        throw new IllegalArgumentException("Equipment must have the same capacity in every weekly rule");
                    }
                }
            }
        }
        if (!expectedCapacities.isEmpty()) {
            equipment.validateScheduleEquipment(vendorId, expectedCapacities);
        }
    }

    private Service ownedService(UUID userId, UUID serviceId, boolean lock) {
        var vendor = vendors.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));
        var service = (lock ? services.findByIdForUpdate(serviceId) : services.findById(serviceId))
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));
        service.validateOwnership(vendor.getId());
        return service;
    }

    private record DepartureKey(LocalDate date, LocalTime startTime) {}

    public record ScheduleUpdate(OperatingSchedule schedule, int createdSlots, int closedSlots) {}

    public record ExceptionUpdate(OperatingScheduleException exception, int closedSlots) {}
}
