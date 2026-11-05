package com.danasea.backend.modules.service.presentation.controllers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.application.usecases.EquipmentAssignmentService;
import com.danasea.backend.modules.service.application.usecases.EquipmentMaintenanceService;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore.Equipment;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/equipment")
@PreAuthorize("hasRole('VENDOR')")
@RequiredArgsConstructor
public class VendorEquipmentController {

    private final EquipmentAssignmentService assignments;
    private final EquipmentMaintenanceService maintenance;
    private final LocalizedMessageService messages;

    public record CreateEquipmentRequest(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 64) String compatibilityGroup, @Positive int capacity) {}

    public record MaintenanceRequest(@NotNull LocalDateTime start, @NotNull LocalDateTime end,
            boolean urgent, @NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    public List<Equipment> list() {
        return assignments.list(userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipment create(@Valid @RequestBody CreateEquipmentRequest request) {
        return assignments.create(userId(), request.name(), request.compatibilityGroup(), request.capacity());
    }

    @PutMapping("/{equipmentId}/slots/{slotId}/units/{unitNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attach(@PathVariable UUID equipmentId, @PathVariable UUID slotId, @PathVariable int unitNumber) {
        assignments.attach(userId(), slotId, unitNumber, equipmentId);
    }

    @PutMapping("/{equipmentId}/maintenance/{maintenanceId}")
    public EquipmentMaintenanceService.Result maintain(@PathVariable UUID equipmentId, @PathVariable UUID maintenanceId,
            @Valid @RequestBody MaintenanceRequest request) {
        return maintenance.schedule(userId(), equipmentId, maintenanceId,
                request.start(), request.end(), request.urgent(), request.reason());
    }

    @GetMapping("/{equipmentId}/maintenance/{maintenanceId}")
    public EquipmentMaintenanceService.Result maintenance(@PathVariable UUID equipmentId, @PathVariable UUID maintenanceId) {
        return maintenance.get(userId(), equipmentId, maintenanceId);
    }

    public record MaintenanceConflictResponse(String code, String message,
            List<EquipmentMaintenanceService.SlotConflict> conflicts) {}

    @ExceptionHandler(EquipmentMaintenanceService.PlannedConflict.class)
    public ResponseEntity<MaintenanceConflictResponse> plannedConflict(EquipmentMaintenanceService.PlannedConflict failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MaintenanceConflictResponse(
                "EQUIPMENT_MAINTENANCE_CONFLICT", messages.get("error.equipment_conflict"), failure.conflicts()));
    }

    private UUID userId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }
}
