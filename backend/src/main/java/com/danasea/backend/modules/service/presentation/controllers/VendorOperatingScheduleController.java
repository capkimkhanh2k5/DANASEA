package com.danasea.backend.modules.service.presentation.controllers;

import com.danasea.backend.modules.service.application.usecases.OperatingScheduleUseCase;
import com.danasea.backend.modules.service.application.usecases.OperatingScheduleUseCase.ExceptionUpdate;
import com.danasea.backend.modules.service.application.usecases.OperatingScheduleUseCase.ScheduleUpdate;
import com.danasea.backend.modules.service.domain.models.OperatingSchedule;
import com.danasea.backend.modules.service.domain.models.OperatingScheduleException;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/services/{id}/operating-schedule")
@PreAuthorize("hasRole('VENDOR')")
@RequiredArgsConstructor
public class VendorOperatingScheduleController {
    private final OperatingScheduleUseCase schedules;

    @GetMapping
    public ResponseEntity<OperatingSchedule> get(@PathVariable("id") UUID serviceId) {
        return ResponseEntity.of(schedules.get(currentUser(), serviceId));
    }

    @PutMapping
    public ScheduleUpdate put(@PathVariable("id") UUID serviceId, @Valid @RequestBody ScheduleRequest request) {
        return schedules.put(currentUser(), serviceId, new OperatingSchedule(request.startsOn(), request.endsOn(),
                request.active(), request.rules()));
    }

    @GetMapping("/exceptions")
    public List<OperatingScheduleException> listExceptions(@PathVariable("id") UUID serviceId) {
        return schedules.listExceptions(currentUser(), serviceId);
    }

    @PostMapping("/exceptions")
    public ResponseEntity<ExceptionUpdate> addException(@PathVariable("id") UUID serviceId,
                                                       @RequestBody ExceptionRequest request) {
        var exception = new OperatingScheduleException(UUID.randomUUID(), request.dateFrom(), request.dateTo(),
                request.startTime(), request.reason());
        ExceptionUpdate result = schedules.addException(currentUser(), serviceId, exception);
        return ResponseEntity.status(result.exception().id().equals(exception.id()) ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result);
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    public ResponseEntity<Void> removeException(@PathVariable("id") UUID serviceId,
                                               @PathVariable UUID exceptionId) {
        schedules.removeException(currentUser(), serviceId, exceptionId);
        return ResponseEntity.noContent().build();
    }

    private static UUID currentUser() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    public record ScheduleRequest(LocalDate startsOn, LocalDate endsOn, @NotNull Boolean active,
                                  List<OperatingSchedule.WeeklyRule> rules) {}

    public record ExceptionRequest(LocalDate dateFrom, LocalDate dateTo, LocalTime startTime, String reason) {}
}
