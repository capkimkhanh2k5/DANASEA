package com.danasea.backend.modules.service.domain.ports;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;

public interface ServiceSlotRepositoryPort {

    ServiceSlot save(ServiceSlot slot);

    Optional<ServiceSlot> findById(UUID id);

    Optional<ServiceSlot> findByIdForUpdate(UUID id);

    int getActiveHeldCount(UUID slotId);

    boolean existsByServiceIdAndDateAndStartTime(UUID serviceId, LocalDate date, LocalTime startTime);

    List<ServiceSlot> findByServiceId(UUID serviceId);

    List<ServiceSlot> findByServiceIdAndDateBetween(UUID serviceId, LocalDate from, LocalDate to);

    List<ServiceSlotUnit> findUnitsBySlotId(UUID slotId);

    List<ServiceSlotUnit> findUnitsBySlotIds(List<UUID> slotIds);

    void saveUnits(UUID slotId, List<ServiceSlotUnit> units);

    void deleteUnitsBySlotId(UUID slotId);

    void deleteUnit(UUID slotId, int unitNumber);

    boolean hasActiveBookingOrHold(UUID slotId);

    int getCommittedCountForUnit(UUID slotId, int unitNumber);

    boolean hasPrivateCommitmentForUnit(UUID slotId, int unitNumber);

    boolean hasAnyBookingForService(UUID serviceId);

    void deleteByServiceId(UUID serviceId);

    void saveSlotOptions(UUID slotId, List<UUID> optionIds);

    List<UUID> findOptionIdsBySlotId(UUID slotId);

    Map<UUID, List<UUID>> findOptionIdsBySlotIds(List<UUID> slotIds);
}
