package com.danasea.backend.modules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.application.usecases.EquipmentAssignmentService;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceSlotUseCase;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
public class UpdateServiceSlotAtomicIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private UpdateServiceSlotUseCase updateServiceSlotUseCase;
    @Autowired private EquipmentAssignmentService equipmentService;
    @Autowired private EquipmentStore equipmentStore;
    @Autowired private CreateBookingHoldUseCase createBookingHoldUseCase;
    @Autowired private JpaUserRepository userRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository serviceSlotRepository;
    @Autowired private JpaServiceSlotUnitRepository serviceSlotUnitRepository;
    @Autowired private JpaServiceOptionRepository serviceOptionRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserJpaEntity vendorUser;
    private UserJpaEntity customerUser;
    private VendorJpaEntity vendor;
    private ServiceJpaEntity service;
    private ServiceOptionJpaEntity optShared;
    private ServiceOptionJpaEntity optPrivate6;
    private ServiceOptionJpaEntity optSharedVip;
    private EquipmentStore.Equipment eq10;
    private EquipmentStore.Equipment eq8;
    private EquipmentStore.Equipment eq2;
    private EquipmentStore.Equipment eqBusyOverlap;
    private ServiceSlotJpaEntity targetSlot;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            });
        }
        jdbcTemplate.execute("TRUNCATE users, services, vendor_equipment, bookings CASCADE");

        vendorUser = new UserJpaEntity();
        vendorUser.setEmail("atomic_vendor@danasea.com");
        vendorUser.setPasswordHash("pw");
        vendorUser.setFullName("Atomic Vendor");
        vendorUser.setRole(Role.VENDOR);
        vendorUser.setIsEmailVerified(true);
        vendorUser.setIsLocked(false);
        vendorUser = userRepository.save(vendorUser);

        vendor = new VendorJpaEntity();
        vendor.setUserId(vendorUser.getId());
        vendor.setBusinessName("Atomic Vendor Co");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor = vendorRepository.save(vendor);

        customerUser = new UserJpaEntity();
        customerUser.setEmail("atomic_customer@danasea.com");
        customerUser.setPasswordHash("pw");
        customerUser.setFullName("Atomic Customer");
        customerUser.setRole(Role.CUSTOMER);
        customerUser.setIsEmailVerified(true);
        customerUser.setIsLocked(false);
        customerUser = userRepository.save(customerUser);

        CategoryJpaEntity cat = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoryJpaEntity c = new CategoryJpaEntity();
            c.setName("Cano Atomic");
            c.setSlug("cano-atomic-" + UUID.randomUUID());
            c.setIsActive(true);
            return categoryRepository.save(c);
        });

        service = new ServiceJpaEntity();
        service.setVendorId(vendor.getId());
        service.setCategoryId(cat.getId());
        service.setName("Tour Atomic");
        service.setDescription("Atomic Desc");
        service.setPrice(new BigDecimal("300000.00"));
        service.setStatus(ServiceStatus.PUBLISHED);
        service = serviceRepository.save(service);

        optShared = new ServiceOptionJpaEntity();
        optShared.setServiceId(service.getId());
        optShared.setName("Shared");
        optShared.setOptionType(OptionType.SHARED);
        optShared.setPricingUnit(PricingUnit.PER_PERSON);
        optShared.setPrice(new BigDecimal("300000.00"));
        optShared.setStatus(OptionStatus.ACTIVE);
        optShared = serviceOptionRepository.save(optShared);

        optSharedVip = new ServiceOptionJpaEntity();
        optSharedVip.setServiceId(service.getId());
        optSharedVip.setName("Shared VIP");
        optSharedVip.setOptionType(OptionType.SHARED);
        optSharedVip.setPricingUnit(PricingUnit.PER_PERSON);
        optSharedVip.setPrice(new BigDecimal("450000.00"));
        optSharedVip.setStatus(OptionStatus.ACTIVE);
        optSharedVip = serviceOptionRepository.save(optSharedVip);

        optPrivate6 = new ServiceOptionJpaEntity();
        optPrivate6.setServiceId(service.getId());
        optPrivate6.setName("Private 6");
        optPrivate6.setOptionType(OptionType.PRIVATE);
        optPrivate6.setPricingUnit(PricingUnit.PER_PACKAGE);
        optPrivate6.setPrice(new BigDecimal("1500000.00"));
        optPrivate6.setMaxPaxPerPackage(6);
        optPrivate6.setStatus(OptionStatus.ACTIVE);
        optPrivate6 = serviceOptionRepository.save(optPrivate6);

        eq10 = equipmentStore.create(vendor.getId(), "Cano 10", "cano", 10);
        eq8 = equipmentStore.create(vendor.getId(), "Cano 8", "cano", 8);
        eq2 = equipmentStore.create(vendor.getId(), "Kayak 2", "kayak", 2);
        eqBusyOverlap = equipmentStore.create(vendor.getId(), "Cano Busy", "cano", 10);

        tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        targetSlot = createSlot(tomorrow, LocalTime.of(8, 0), LocalTime.of(10, 0), 10, eq10.id(), List.of(optShared.getId(), optPrivate6.getId()));
        createSlot(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), 10, eqBusyOverlap.id(), List.of(optShared.getId()));
    }

    private ServiceSlotJpaEntity createSlot(LocalDate date, LocalTime start, LocalTime end, int unitCap, UUID eqId, List<UUID> optIds) {
        ServiceSlotJpaEntity s = new ServiceSlotJpaEntity();
        s.setServiceId(service.getId());
        s.setDate(date);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setCapacity(unitCap);
        s.setBookedCount(0);
        s.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        s.setStatus(SlotStatus.OPEN);
        s = serviceSlotRepository.save(s);

        ServiceSlotUnitJpaEntity u = new ServiceSlotUnitJpaEntity();
        u.setSlotId(s.getId());
        u.setUnitNumber(1);
        u.setCapacity(unitCap);
        u.setBookedCount(0);
        u.setEquipmentId(eqId);
        serviceSlotUnitRepository.save(u);

        for (UUID optId : optIds) {
            jdbcTemplate.update("INSERT INTO service_slot_options(slot_id, option_id) VALUES (?, ?)", s.getId(), optId);
        }
        return s;
    }

    @Test
    @DisplayName("Atomic PATCH: đổi thiết bị + đổi status + thêm option + thêm unit mới trong 1 lần Lưu -> lưu toàn bộ")
    void atomicPatch_replaceEquipmentAndSlotMetadata_persistsAllAtomically() {
        var req = new UpdateServiceSlotRequest(
                SlotStatus.CLOSED,
                null,
                List.of(
                        new CreateSlotUnitRequest(1, 8, eq8.id(), null),
                        new CreateSlotUnitRequest(2, 5, null, null)
                ),
                List.of(optShared.getId(), optSharedVip.getId())
        );

        var resp = updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req);
        assertThat(resp.status()).isEqualTo(SlotStatus.CLOSED);
        assertThat(resp.capacity()).isEqualTo(13);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM service_slots WHERE id=?", String.class, targetSlot.getId()))
                .isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT capacity FROM service_slots WHERE id=?", Integer.class, targetSlot.getId()))
                .isEqualTo(13);
        assertThat(jdbcTemplate.queryForList("SELECT option_id FROM service_slot_options WHERE slot_id=?", UUID.class, targetSlot.getId()))
                .containsExactlyInAnyOrder(optShared.getId(), optSharedVip.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, targetSlot.getId()))
                .isEqualTo(eq8.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT capacity FROM service_slot_units WHERE slot_id=? AND unit_number=1", Integer.class, targetSlot.getId()))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT capacity FROM service_slot_units WHERE slot_id=? AND unit_number=2", Integer.class, targetSlot.getId()))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Atomic PATCH: gỡ thiết bị (detachEquipment=true) + điều chỉnh capacity + cập nhật option -> lưu toàn bộ")
    void atomicPatch_detachAndResizeAndUpdateOptions_succeedsAtomically() {
        var req = new UpdateServiceSlotRequest(
                SlotStatus.OPEN,
                null,
                List.of(new CreateSlotUnitRequest(1, 7, null, true)),
                List.of(optShared.getId(), optPrivate6.getId(), optSharedVip.getId())
        );

        var resp = updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req);
        assertThat(resp.capacity()).isEqualTo(7);

        UUID eqAfter = jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, targetSlot.getId());
        assertThat(eqAfter).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT capacity FROM service_slot_units WHERE slot_id=? AND unit_number=1", Integer.class, targetSlot.getId()))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForList("SELECT option_id FROM service_slot_options WHERE slot_id=?", UUID.class, targetSlot.getId()))
                .containsExactlyInAnyOrder(optShared.getId(), optPrivate6.getId(), optSharedVip.getId());
    }

    @Test
    @DisplayName("Atomic PATCH Rollback: thay thiết bị nhỏ hơn PRIVATE maxPax -> rollback toàn bộ status, options, capacity và equipment")
    void atomicPatch_replaceSmallerThanPrivateMaxPax_rollsBackAllDbChanges() {
        var req = new UpdateServiceSlotRequest(
                SlotStatus.CLOSED,
                null,
                List.of(
                        new CreateSlotUnitRequest(1, 2, eq2.id(), null),
                        new CreateSlotUnitRequest(2, 4, null, null)
                ),
                List.of(optShared.getId(), optPrivate6.getId(), optSharedVip.getId())
        );

        assertThatThrownBy(() -> updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private option maximum capacity");

        assertOriginalDbStateIntact();
    }

    @Test
    @DisplayName("Atomic PATCH Rollback: Unit 1 hợp lệ (replace eq8), Unit 2 trùng lịch (eqBusyOverlap) -> rollback toàn bộ")
    void atomicPatch_oneUnitValidOneUnitConflict_rollsBackAllDbChanges() {
        var req = new UpdateServiceSlotRequest(
                SlotStatus.CLOSED,
                null,
                List.of(
                        new CreateSlotUnitRequest(1, 8, eq8.id(), null),
                        new CreateSlotUnitRequest(2, 10, eqBusyOverlap.id(), null)
                ),
                List.of(optSharedVip.getId())
        );

        assertThatThrownBy(() -> updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("overlapping slot");

        assertOriginalDbStateIntact();
    }

    @Test
    @DisplayName("Atomic PATCH Rollback: đang có HOLD trên ca -> mọi thay đổi thiết bị/status/option bị chặn và rollback sạch")
    void atomicPatch_withActiveHold_rejectsAndPreservesDatabaseState() {
        createBookingHoldUseCase.execute(new CreateBookingHoldCommand(
                customerUser.getId(),
                List.of(new BookingHoldItemDto(targetSlot.getId(), 1, optPrivate6.getId(), 2, false, null)),
                UUID.randomUUID().toString()
        ));

        var req = new UpdateServiceSlotRequest(
                SlotStatus.CLOSED,
                null,
                List.of(new CreateSlotUnitRequest(1, 10, null, true)),
                List.of(optShared.getId(), optPrivate6.getId(), optSharedVip.getId())
        );

        assertThatThrownBy(() -> updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active hold");

        assertOriginalDbStateIntact();
    }

    @Test
    @DisplayName("Atomic PATCH Backward-Compatibility: client cũ không gửi equipmentId/detachEquipment -> bảo toàn thiết bị hiện tại")
    void atomicPatch_legacyPayloadOmittingEquipment_preservesExistingEquipmentLink() {
        var req = new UpdateServiceSlotRequest(
                SlotStatus.CLOSED,
                null,
                List.of(new CreateSlotUnitRequest(1, 10)),
                List.of(optShared.getId(), optPrivate6.getId(), optSharedVip.getId())
        );

        var resp = updateServiceSlotUseCase.execute(vendorUser.getId(), service.getId(), targetSlot.getId(), req);
        assertThat(resp.status()).isEqualTo(SlotStatus.CLOSED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, targetSlot.getId()))
                .isEqualTo(eq10.id());
    }

    private void assertOriginalDbStateIntact() {
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM service_slots WHERE id=?", String.class, targetSlot.getId()))
                .isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject("SELECT capacity FROM service_slots WHERE id=?", Integer.class, targetSlot.getId()))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_slot_units WHERE slot_id=?", Integer.class, targetSlot.getId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, targetSlot.getId()))
                .isEqualTo(eq10.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT capacity FROM service_slot_units WHERE slot_id=? AND unit_number=1", Integer.class, targetSlot.getId()))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForList("SELECT option_id FROM service_slot_options WHERE slot_id=?", UUID.class, targetSlot.getId()))
                .containsExactlyInAnyOrder(optShared.getId(), optPrivate6.getId());
    }
}
