package com.danasea.backend.modules.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.application.usecases.EquipmentAssignmentService;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.EquipmentStore;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
public class EquipmentConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private EquipmentAssignmentService equipmentService;
    @Autowired private EquipmentStore equipmentStore;
    @Autowired private JpaUserRepository userRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository serviceSlotRepository;
    @Autowired private JpaServiceSlotUnitRepository serviceSlotUnitRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserJpaEntity testVendorUser;
    private VendorJpaEntity testVendor;
    private ServiceJpaEntity testService;
    private EquipmentStore.Equipment cano1;
    private ServiceSlotJpaEntity slot1;
    private ServiceSlotJpaEntity slot2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE users, services, vendor_equipment CASCADE");

        // 1. Create Vendor user & profile
        testVendorUser = new UserJpaEntity();
        testVendorUser.setEmail("equip_vendor@danasea.com");
        testVendorUser.setPasswordHash("hashed_pw");
        testVendorUser.setFullName("Cù Lao Chàm Equip Vendor");
        testVendorUser.setRole(Role.VENDOR);
        testVendorUser.setIsEmailVerified(true);
        testVendorUser.setIsLocked(false);
        testVendorUser = userRepository.save(testVendorUser);

        testVendor = new VendorJpaEntity();
        testVendor.setUserId(testVendorUser.getId());
        testVendor.setBusinessName("Cù Lao Chàm Equip");
        testVendor.setVerificationStatus(VerificationStatus.APPROVED);
        testVendor = vendorRepository.save(testVendor);

        // 2. Category & Service
        CategoryJpaEntity cat = categoryRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategoryJpaEntity c = new CategoryJpaEntity();
                    c.setName("Cano Speed");
                    c.setSlug("cano-speed-" + UUID.randomUUID());
                    c.setIsActive(true);
                    return categoryRepository.save(c);
                });

        testService = new ServiceJpaEntity();
        testService.setVendorId(testVendor.getId());
        testService.setCategoryId(cat.getId());
        testService.setName("Tour Cano Cao Tốc");
        testService.setDescription("Tour cano");
        testService.setPrice(new BigDecimal("300000.00"));
        testService.setStatus(ServiceStatus.PUBLISHED);
        testService = serviceRepository.save(testService);

        // 3. Create physical equipment: Cano 1 with capacity 10
        cano1 = equipmentStore.create(testVendor.getId(), "Cano Alpha", "cano-10", 10);

        // 4. Create 2 overlapping slots:
        // Slot 1: Tomorrow 08:00 - 10:00 (1 unit, capacity 10)
        // Slot 2: Tomorrow 08:30 - 10:30 (1 unit, capacity 10)
        LocalDate tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);

        slot1 = new ServiceSlotJpaEntity();
        slot1.setServiceId(testService.getId());
        slot1.setDate(tomorrow);
        slot1.setStartTime(LocalTime.of(8, 0));
        slot1.setEndTime(LocalTime.of(10, 0));
        slot1.setCapacity(10);
        slot1.setBookedCount(0);
        slot1.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        slot1.setStatus(SlotStatus.OPEN);
        slot1 = serviceSlotRepository.save(slot1);

        ServiceSlotUnitJpaEntity unit1 = new ServiceSlotUnitJpaEntity();
        unit1.setSlotId(slot1.getId());
        unit1.setUnitNumber(1);
        unit1.setCapacity(10);
        unit1.setBookedCount(0);
        serviceSlotUnitRepository.save(unit1);

        slot2 = new ServiceSlotJpaEntity();
        slot2.setServiceId(testService.getId());
        slot2.setDate(tomorrow);
        slot2.setStartTime(LocalTime.of(8, 30));
        slot2.setEndTime(LocalTime.of(10, 30));
        slot2.setCapacity(10);
        slot2.setBookedCount(0);
        slot2.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        slot2.setStatus(SlotStatus.OPEN);
        slot2 = serviceSlotRepository.save(slot2);

        ServiceSlotUnitJpaEntity unit2 = new ServiceSlotUnitJpaEntity();
        unit2.setSlotId(slot2.getId());
        unit2.setUnitNumber(1);
        unit2.setCapacity(10);
        unit2.setBookedCount(0);
        serviceSlotUnitRepository.save(unit2);
    }

    @Test
    @DisplayName("Hai transaction đồng thời gán CÙNG thiết bị cho 2 ca chồng giờ -> Đúng 1 thành công, 1 bị CONFLICT do SELECT FOR UPDATE thực sự")
    void concurrentAssignment_sameEquipment_overlappingSlots_exactlyOneSucceeds() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: assign cano1 to slot 1
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                equipmentService.attach(testVendorUser.getId(), slot1.getId(), 1, cano1.id());
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedErrors.add(ex);
                }
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        // Thread 2: assign cano1 to slot 2 (overlapping with slot 1)
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                equipmentService.attach(testVendorUser.getId(), slot2.getId(), 1, cano1.id());
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedErrors.add(ex);
                }
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // fire simultaneously
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify in DB that cano1 is only assigned to ONE unit
        Integer assignedUnitsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_slot_units WHERE equipment_id = ?",
                Integer.class, cano1.id());
        assertThat(assignedUnitsCount).isEqualTo(1);
    }
}
