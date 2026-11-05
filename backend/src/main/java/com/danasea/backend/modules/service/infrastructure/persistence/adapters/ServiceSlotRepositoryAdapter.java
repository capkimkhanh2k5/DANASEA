package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceSlotRepositoryAdapter implements ServiceSlotRepositoryPort {

    private final JpaServiceSlotRepository jpaServiceSlotRepository;
    private final JpaServiceSlotUnitRepository jpaServiceSlotUnitRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public ServiceSlot save(ServiceSlot slot) {
        ServiceSlotJpaEntity entity = toEntity(slot);
        ServiceSlotJpaEntity saved = jpaServiceSlotRepository.save(entity);
        return toDomain(saved, slot.getUnits());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceSlot> findById(UUID id) {
        return jpaServiceSlotRepository.findById(id).map(entity -> {
            List<ServiceSlotUnit> units = findUnitsBySlotId(entity.getId());
            return toDomain(entity, units);
        });
    }

    @Override
    @Transactional
    public Optional<ServiceSlot> findByIdForUpdate(UUID id) {
        return jpaServiceSlotRepository.findByIdForUpdate(id)
                .map(entity -> toDomain(entity, findUnitsBySlotId(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByServiceIdAndDateAndStartTime(UUID serviceId, LocalDate date, LocalTime startTime) {
        return jpaServiceSlotRepository.existsByServiceIdAndDateAndStartTime(serviceId, date, startTime);
    }

    @Override
    @Transactional(readOnly = true)
    public int getActiveHeldCount(UUID slotId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(bi.quantity), 0) FROM booking_items bi JOIN bookings b ON bi.booking_id = b.id
                WHERE bi.slot_id = ? AND b.status IN ('HOLD', 'PENDING_PAYMENT') AND b.hold_expires_at > CURRENT_TIMESTAMP
                """, Integer.class, slotId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceSlot> findByServiceId(UUID serviceId) {
        List<ServiceSlotJpaEntity> entities = jpaServiceSlotRepository.findByServiceIdOrderByDateAscStartTimeAsc(serviceId);
        return mapEntitiesWithUnits(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceSlot> findByServiceIdAndDateBetween(UUID serviceId, LocalDate from, LocalDate to) {
        List<ServiceSlotJpaEntity> entities = jpaServiceSlotRepository
                .findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(serviceId, from, to);
        return mapEntitiesWithUnits(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceSlotUnit> findUnitsBySlotId(UUID slotId) {
        return jpaServiceSlotUnitRepository.findBySlotIdOrderByUnitNumberAsc(slotId).stream()
                .map(this::toUnitDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceSlotUnit> findUnitsBySlotIds(List<UUID> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) return Collections.emptyList();
        return jpaServiceSlotUnitRepository.findBySlotIdInOrderBySlotIdAscUnitNumberAsc(slotIds).stream()
                .map(this::toUnitDomain)
                .toList();
    }

    @Override
    @Transactional
    public void saveUnits(UUID slotId, List<ServiceSlotUnit> units) {
        if (units == null || units.isEmpty()) return;
        List<ServiceSlotUnitJpaEntity> entities = units.stream()
                .map(u -> {
                    ServiceSlotUnitJpaEntity e = ServiceSlotUnitJpaEntity.builder()
                            .slotId(slotId)
                            .unitNumber(u.getUnitNumber())
                            .capacity(u.getCapacity())
                            .bookedCount(u.getBookedCount() != null ? u.getBookedCount() : 0)
                            .equipmentId(u.getEquipmentId())
                            .equipmentBlocked(u.isEquipmentBlocked())
                            .build();
                    if (u.getId() != null) e.setId(u.getId());
                    return e;
                })
                .toList();
        jpaServiceSlotUnitRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteUnitsBySlotId(UUID slotId) {
        jpaServiceSlotUnitRepository.deleteBySlotId(slotId);
    }

    @Override
    @Transactional
    public void deleteUnit(UUID slotId, int unitNumber) {
        jpaServiceSlotUnitRepository.findBySlotIdAndUnitNumber(slotId, unitNumber)
                .ifPresent(jpaServiceSlotUnitRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBookingOrHold(UUID slotId) {
        String sql = "SELECT COUNT(*) FROM booking_items bi " +
                "JOIN bookings b ON bi.booking_id = b.id " +
                "WHERE bi.slot_id = ? AND (b.status = 'CONFIRMED' AND NOT bi.capacity_released OR b.status IN ('HOLD', 'PENDING_PAYMENT') AND b.hold_expires_at > CURRENT_TIMESTAMP)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, slotId);
        if (count != null && count > 0) {
            return true;
        }

        // Also check Redis for any active hold on this slot
        String key = "inventory:slot:" + slotId + ":holds";
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries != null && !entries.isEmpty()) {
            long now = System.currentTimeMillis();
            for (Object val : entries.values()) {
                String str = String.valueOf(val);
                long exp = extractExpiry(str);
                if (exp > now) {
                    return true;
                }
            }
        }
        return false;
    }

    private long extractExpiry(String val) {
        try {
            int barPos = val.indexOf('|');
            if (barPos > 0) {
                return Long.parseLong(val.substring(0, barPos));
            }
            int colonPos = val.indexOf(':');
            if (colonPos > 0) {
                return Long.parseLong(val.substring(colonPos + 1));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int getCommittedCountForUnit(UUID slotId, int unitNumber) {
        String sql = "SELECT COALESCE(SUM(bia.allocated_seats), 0) FROM booking_item_allocations bia " +
                "JOIN booking_items bi ON bia.booking_item_id = bi.id " +
                "JOIN bookings b ON bi.booking_id = b.id " +
                "WHERE bia.slot_id = ? AND bia.unit_number = ? AND (b.status = 'CONFIRMED' AND NOT bi.capacity_released OR b.status IN ('HOLD', 'PENDING_PAYMENT') AND b.hold_expires_at > CURRENT_TIMESTAMP)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, slotId, unitNumber);
        return count != null ? count : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPrivateCommitmentForUnit(UUID slotId, int unitNumber) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_item_allocations a
                JOIN booking_items bi ON bi.id = a.booking_item_id JOIN bookings b ON b.id = bi.booking_id
                WHERE a.slot_id = ? AND a.unit_number = ? AND a.is_private_lock AND NOT bi.capacity_released
                  AND (b.status = 'CONFIRMED' OR b.status IN ('HOLD', 'PENDING_PAYMENT')
                       AND b.hold_expires_at > CURRENT_TIMESTAMP)
                """, Integer.class, slotId, unitNumber);
        return count != null && count > 0;
    }

    private List<ServiceSlot> mapEntitiesWithUnits(List<ServiceSlotJpaEntity> entities) {
        if (entities.isEmpty()) return Collections.emptyList();
        List<UUID> slotIds = entities.stream().map(ServiceSlotJpaEntity::getId).toList();
        List<ServiceSlotUnitJpaEntity> unitEntities = jpaServiceSlotUnitRepository.findBySlotIdInOrderBySlotIdAscUnitNumberAsc(slotIds);
        Map<UUID, List<ServiceSlotUnit>> unitsBySlotId = unitEntities.stream()
                .map(this::toUnitDomain)
                .collect(Collectors.groupingBy(ServiceSlotUnit::getSlotId));

        return entities.stream()
                .map(e -> toDomain(e, unitsBySlotId.getOrDefault(e.getId(), Collections.emptyList())))
                .toList();
    }

    private ServiceSlotJpaEntity toEntity(ServiceSlot domain) {
        if (domain == null) return null;
        ServiceSlotJpaEntity entity = new ServiceSlotJpaEntity();
        entity.setId(domain.getId());
        entity.setServiceId(domain.getServiceId());
        entity.setDate(domain.getDate());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setCapacity(domain.getCapacity());
        entity.setBookedCount(domain.getBookedCount() != null ? domain.getBookedCount() : 0);
        entity.setStatus(domain.getStatus());
        entity.setInventoryType(domain.getInventoryType() != null ? domain.getInventoryType() : InventoryType.PERSON_LIMIT);
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private ServiceSlot toDomain(ServiceSlotJpaEntity entity, List<ServiceSlotUnit> units) {
        if (entity == null) return null;
        ServiceSlot domain = ServiceSlot.builder()
                .serviceId(entity.getServiceId())
                .date(entity.getDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .capacity(entity.getCapacity())
                .bookedCount(entity.getBookedCount())
                .status(entity.getStatus())
                .inventoryType(entity.getInventoryType() != null ? entity.getInventoryType() : InventoryType.PERSON_LIMIT)
                .units(units != null ? new ArrayList<>(units) : new ArrayList<>())
                .build();
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    private ServiceSlotUnit toUnitDomain(ServiceSlotUnitJpaEntity entity) {
        if (entity == null) return null;
        ServiceSlotUnit domain = ServiceSlotUnit.builder()
                .slotId(entity.getSlotId())
                .unitNumber(entity.getUnitNumber())
                .capacity(entity.getCapacity())
                .bookedCount(entity.getBookedCount())
                .equipmentId(entity.getEquipmentId())
                .equipmentBlocked(entity.isEquipmentBlocked())
                .build();
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }
}
