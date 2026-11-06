package com.danasea.backend.modules.service.infrastructure.jobs;

import com.danasea.backend.modules.service.application.usecases.EquipmentMaintenanceService;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquipmentMaintenanceImpactJob {

    private final EquipmentStore store;
    private final EquipmentMaintenanceService maintenance;

    @Scheduled(fixedDelayString = "${app.scheduler.equipment-maintenance-delay:30s}")
    public void process() {
        for (var impact : store.pendingConfirmedImpacts()) {
            try {
                maintenance.handleImpact(impact);
            } catch (RuntimeException ex) {
                log.warn("Could not process maintenance {} for order {}",
                        impact.maintenanceId(), impact.subOrderId(), ex);
            }
        }
    }
}
