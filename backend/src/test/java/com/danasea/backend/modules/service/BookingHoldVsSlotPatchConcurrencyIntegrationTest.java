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
import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.exceptions.SlotNotAvailableException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
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
import org.springframework.data.redis.core.RedisCallback;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
public class BookingHoldVsSlotPatchConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private CreateBookingHoldUseCase createBookingHoldUseCase;
    @Autowired private UpdateServiceSlotUseCase updateServiceSlotUseCase;
    @Autowired private EquipmentAssignmentService equipmentAssignmentService;
    @Autowired private EquipmentStore equipmentStore;

    @Autowired private JpaUserRepository userRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaCategoryRepository categoryRepository;
    @Autowired private JpaServiceRepository serviceRepository;
    @Autowired private JpaServiceSlotRepository serviceSlotRepository;
    @Autowired private JpaServiceSlotUnitRepository serviceSlotUnitRepository;
    @Autowired private JpaServiceOptionRepository serviceOptionRepository;
    @Autowired private JpaBookingItemAllocationRepository allocationRepository;
    @Autowired private JpaBookingItemRepository bookingItemRepository;
    @Autowired private JpaBookingRepository jpaBookingRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserJpaEntity testVendorUser;
    private VendorJpaEntity testVendor;
    private UserJpaEntity testCustomer;
    private ServiceJpaEntity testService;
    private ServiceSlotJpaEntity testSlot;
    private ServiceOptionJpaEntity opt1;
    private ServiceOptionJpaEntity opt2;

    @BeforeEach
    void setUp() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            });
        }

        jdbcTemplate.execute("TRUNCATE users, services, vendor_equipment, bookings CASCADE");

        // 1. Vendor
        testVendorUser = new UserJpaEntity();
        testVendorUser.setEmail("patch_vendor@danasea.com");
        testVendorUser.setPasswordHash("hashed_pw");
        testVendorUser.setFullName("Vendor Patch Test");
        testVendorUser.setRole(Role.VENDOR);
        testVendorUser.setIsEmailVerified(true);
        testVendorUser.setIsLocked(false);
        testVendorUser = userRepository.save(testVendorUser);

        testVendor = new VendorJpaEntity();
        testVendor.setUserId(testVendorUser.getId());
        testVendor.setBusinessName("Vendor Patch Business");
        testVendor.setVerificationStatus(VerificationStatus.APPROVED);
        testVendor = vendorRepository.save(testVendor);

        // 2. Customer
        testCustomer = new UserJpaEntity();
        testCustomer.setEmail("hold_customer@danasea.com");
        testCustomer.setPasswordHash("hashed_pw");
        testCustomer.setFullName("Customer Hold Test");
        testCustomer.setRole(Role.CUSTOMER);
        testCustomer.setIsEmailVerified(true);
        testCustomer.setIsLocked(false);
        testCustomer = userRepository.save(testCustomer);

        // 3. Category & Service
        CategoryJpaEntity cat = categoryRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    CategoryJpaEntity c = new CategoryJpaEntity();
                    c.setName("Island Tour");
                    c.setSlug("island-tour-" + UUID.randomUUID());
                    c.setIsActive(true);
                    return categoryRepository.save(c);
                });

        testService = new ServiceJpaEntity();
        testService.setVendorId(testVendor.getId());
        testService.setCategoryId(cat.getId());
        testService.setName("Tour Đảo");
        testService.setDescription("Tour lặn biển");
        testService.setPrice(new BigDecimal("150000.00"));
        testService.setStatus(ServiceStatus.PUBLISHED);
        testService = serviceRepository.save(testService);

        // 4. Options
        opt1 = new ServiceOptionJpaEntity();
        opt1.setServiceId(testService.getId());
        opt1.setName("Gói Tiêu Chuẩn");
        opt1.setOptionType(OptionType.SHARED);
        opt1.setPricingUnit(PricingUnit.PER_PERSON);
        opt1.setPrice(new BigDecimal("150000.00"));
        opt1.setStatus(OptionStatus.ACTIVE);
        opt1 = serviceOptionRepository.save(opt1);

        opt2 = new ServiceOptionJpaEntity();
        opt2.setServiceId(testService.getId());
        opt2.setName("Gói VIP");
        opt2.setOptionType(OptionType.SHARED);
        opt2.setPricingUnit(PricingUnit.PER_PERSON);
        opt2.setPrice(new BigDecimal("250000.00"));
        opt2.setStatus(OptionStatus.ACTIVE);
        opt2 = serviceOptionRepository.save(opt2);

        // 5. Slot with 2 units x 10 capacity
        LocalDate tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);
        testSlot = new ServiceSlotJpaEntity();
        testSlot.setServiceId(testService.getId());
        testSlot.setDate(tomorrow);
        testSlot.setStartTime(LocalTime.of(8, 0));
        testSlot.setEndTime(LocalTime.of(10, 0));
        testSlot.setCapacity(20);
        testSlot.setBookedCount(0);
        testSlot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        testSlot.setStatus(SlotStatus.OPEN);
        testSlot = serviceSlotRepository.save(testSlot);

        ServiceSlotUnitJpaEntity u1 = new ServiceSlotUnitJpaEntity();
        u1.setSlotId(testSlot.getId());
        u1.setUnitNumber(1);
        u1.setCapacity(10);
        u1.setBookedCount(0);
        serviceSlotUnitRepository.save(u1);

        ServiceSlotUnitJpaEntity u2 = new ServiceSlotUnitJpaEntity();
        u2.setSlotId(testSlot.getId());
        u2.setUnitNumber(2);
        u2.setCapacity(10);
        u2.setBookedCount(0);
        serviceSlotUnitRepository.save(u2);

        // Bind both opt1 and opt2 to slot
        jdbcTemplate.update("INSERT INTO service_slot_options(slot_id, option_id) VALUES (?, ?), (?, ?)",
                testSlot.getId(), opt1.getId(), testSlot.getId(), opt2.getId());
    }

    @Test
    @DisplayName("Khách HOLD Option 1 đồng thời Vendor PATCH gỡ Option 1 khỏi Slot -> Không thể vừa có HOLD hợp lệ vừa gỡ thành công")
    void concurrentHoldAndOptionRemoval_mutuallyExclusive() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger holdSuccess = new AtomicInteger();
        AtomicInteger holdRejected = new AtomicInteger();
        AtomicInteger patchSuccess = new AtomicInteger();
        AtomicInteger patchRejected = new AtomicInteger();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Customer holds 2 seats on opt1
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                var command = new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(new BookingHoldItemDto(testSlot.getId(), 2, opt1.getId(), null, true, null)),
                        UUID.randomUUID().toString()
                );
                createBookingHoldUseCase.execute(command);
                holdSuccess.incrementAndGet();
            } catch (IllegalArgumentException ex) {
                // If patch won and removed opt1, hold gets rejected
                holdRejected.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        // Thread 2: Vendor updates slot options to ONLY [opt2] (removing opt1)
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                UpdateServiceSlotRequest patchReq = new UpdateServiceSlotRequest(
                        SlotStatus.OPEN,
                        20,
                        List.of(new CreateSlotUnitRequest(1, 10), new CreateSlotUnitRequest(2, 10)),
                        List.of(opt2.getId()) // opt1 is removed!
                );
                updateServiceSlotUseCase.execute(testVendorUser.getId(), testService.getId(), testSlot.getId(), patchReq);
                patchSuccess.incrementAndGet();
            } catch (IllegalArgumentException ex) {
                // If hold won and created active hold, patch gets rejected
                patchRejected.incrementAndGet();
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

        // INVARIANT 1: Both CANNOT succeed simultaneously!
        assertThat(holdSuccess.get() == 1 && patchSuccess.get() == 1)
                .as("Customer HOLD and Vendor PATCH removing the option cannot both succeed simultaneously")
                .isFalse();

        // INVARIANT 2: Exactly one operation succeeds and the conflicting one is safely rejected
        assertThat(holdSuccess.get() + patchSuccess.get()).isEqualTo(1);
        assertThat(holdRejected.get() + patchRejected.get()).isEqualTo(1);

        // INVARIANT 3: Verify database consistency
        List<UUID> slotOptionsAfter = jdbcTemplate.query(
                "SELECT option_id FROM service_slot_options WHERE slot_id = ?",
                (rs, i) -> rs.getObject("option_id", UUID.class),
                testSlot.getId()
        );

        Integer activeBookingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_items bi JOIN bookings b ON bi.booking_id = b.id " +
                "WHERE bi.slot_id = ? AND bi.option_id = ? AND b.status = 'HOLD'",
                Integer.class, testSlot.getId(), opt1.getId()
        );

        if (holdSuccess.get() == 1) {
            // HOLD succeeded: opt1 must STILL be on slot, and booking exists
            assertThat(slotOptionsAfter).contains(opt1.getId());
            assertThat(activeBookingCount).isEqualTo(1);
        } else {
            // PATCH succeeded: opt1 must NOT be on slot, and NO active booking exists for opt1
            assertThat(slotOptionsAfter).doesNotContain(opt1.getId());
            assertThat(activeBookingCount).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Khách HOLD 8 chỗ đồng thời Vendor DELETE detach thiết bị -> tuần tự hóa sạch qua SELECT FOR UPDATE + Redis, không deadlock")
    void concurrentHoldAndEquipmentDetach_consistentPostgresAndRedis() throws Exception {
        var eq10 = equipmentStore.create(testVendor.getId(), "Cano Race Detach", "cano", 10);
        ServiceSlotJpaEntity raceSlot = createSingleEquipmentSlot(LocalTime.of(11, 0), LocalTime.of(13, 0), 10, eq10.id());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger holdSuccess = new AtomicInteger();
        AtomicInteger detachSuccess = new AtomicInteger();
        AtomicInteger detachRejected = new AtomicInteger();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                createBookingHoldUseCase.execute(new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(new BookingHoldItemDto(raceSlot.getId(), 8, opt1.getId(), null, false, null)),
                        UUID.randomUUID().toString()
                ));
                holdSuccess.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                equipmentAssignmentService.detach(testVendorUser.getId(), raceSlot.getId(), 1, eq10.id());
                detachSuccess.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) detachRejected.incrementAndGet();
                else unexpectedErrors.add(ex);
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(holdSuccess.get()).isEqualTo(1);
        assertThat(detachSuccess.get() + detachRejected.get()).isEqualTo(1);

        UUID eqAfter = jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, raceSlot.getId());
        if (detachRejected.get() == 1) {
            // HOLD won the lock first -> detach was blocked 409 -> equipment link preserved
            assertThat(eqAfter).isEqualTo(eq10.id());
        } else {
            // Detach won the lock first before any HOLD existed -> equipment detached, HOLD still cleanly locked capacity=10
            assertThat(eqAfter).isNull();
        }
        assertThat(redisTemplate.opsForHash().entries("inventory:slot:" + raceSlot.getId() + ":holds")).hasSize(1);
    }

    @Test
    @DisplayName("Khách HOLD 8 chỗ đồng thời Vendor PUT replace thiết bị (10 -> 6 chỗ) -> loại trừ lẫn nhau 100%, không deadlock")
    void concurrentHoldAndPutReplace_mutuallyExclusive() throws Exception {
        var eq10 = equipmentStore.create(testVendor.getId(), "Cano Race 10", "cano", 10);
        var eq6 = equipmentStore.create(testVendor.getId(), "Cano Race 6", "cano", 6);
        ServiceSlotJpaEntity raceSlot = createSingleEquipmentSlot(LocalTime.of(14, 0), LocalTime.of(16, 0), 10, eq10.id());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger holdSuccess = new AtomicInteger();
        AtomicInteger holdRejected = new AtomicInteger();
        AtomicInteger replaceSuccess = new AtomicInteger();
        AtomicInteger replaceRejected = new AtomicInteger();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                createBookingHoldUseCase.execute(new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(new BookingHoldItemDto(raceSlot.getId(), 8, opt1.getId(), null, false, null)),
                        UUID.randomUUID().toString()
                ));
                holdSuccess.incrementAndGet();
            } catch (InsufficientInventoryException | SlotNotAvailableException | IllegalArgumentException ex) {
                holdRejected.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                equipmentAssignmentService.attach(testVendorUser.getId(), raceSlot.getId(), 1, eq6.id());
                replaceSuccess.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) replaceRejected.incrementAndGet();
                else unexpectedErrors.add(ex);
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(holdSuccess.get() + replaceSuccess.get()).isEqualTo(1);
        assertThat(holdRejected.get() + replaceRejected.get()).isEqualTo(1);

        UUID eqAfter = jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, raceSlot.getId());
        Integer capAfter = jdbcTemplate.queryForObject(
                "SELECT capacity FROM service_slot_units WHERE slot_id=? AND unit_number=1", Integer.class, raceSlot.getId());
        if (holdSuccess.get() == 1) {
            assertThat(eqAfter).isEqualTo(eq10.id());
            assertThat(capAfter).isEqualTo(10);
            assertThat(redisTemplate.opsForHash().entries("inventory:slot:" + raceSlot.getId() + ":holds")).hasSize(1);
        } else {
            assertThat(eqAfter).isEqualTo(eq6.id());
            assertThat(capAfter).isEqualTo(6);
            assertThat(redisTemplate.opsForHash().entries("inventory:slot:" + raceSlot.getId() + ":holds")).isEmpty();
        }
    }

    @Test
    @DisplayName("Khách HOLD 8 chỗ đồng thời Vendor PATCH atomic (đổi thiết bị 10->6 + status + option) -> loại trừ lẫn nhau 100%, không dở dang")
    void concurrentHoldAndAtomicPatch_mutuallyExclusive() throws Exception {
        var eq10 = equipmentStore.create(testVendor.getId(), "Cano Atomic 10", "cano", 10);
        var eq6 = equipmentStore.create(testVendor.getId(), "Cano Atomic 6", "cano", 6);
        ServiceSlotJpaEntity raceSlot = createSingleEquipmentSlot(LocalTime.of(16, 30), LocalTime.of(18, 30), 10, eq10.id());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger holdSuccess = new AtomicInteger();
        AtomicInteger holdRejected = new AtomicInteger();
        AtomicInteger patchSuccess = new AtomicInteger();
        AtomicInteger patchRejected = new AtomicInteger();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                createBookingHoldUseCase.execute(new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(new BookingHoldItemDto(raceSlot.getId(), 8, opt1.getId(), null, false, null)),
                        UUID.randomUUID().toString()
                ));
                holdSuccess.incrementAndGet();
            } catch (InsufficientInventoryException | SlotNotAvailableException | IllegalArgumentException ex) {
                holdRejected.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                updateServiceSlotUseCase.execute(
                        testVendorUser.getId(),
                        testService.getId(),
                        raceSlot.getId(),
                        new UpdateServiceSlotRequest(
                                SlotStatus.CLOSED,
                                null,
                                List.of(new CreateSlotUnitRequest(1, 6, eq6.id(), null)),
                                List.of(opt2.getId())
                        )
                );
                patchSuccess.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 409) patchRejected.incrementAndGet();
                else unexpectedErrors.add(ex);
            } catch (IllegalArgumentException ex) {
                patchRejected.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(holdSuccess.get() + patchSuccess.get()).isEqualTo(1);
        assertThat(holdRejected.get() + patchRejected.get()).isEqualTo(1);

        String statusAfter = jdbcTemplate.queryForObject("SELECT status FROM service_slots WHERE id=?", String.class, raceSlot.getId());
        UUID eqAfter = jdbcTemplate.queryForObject(
                "SELECT equipment_id FROM service_slot_units WHERE slot_id=? AND unit_number=1", UUID.class, raceSlot.getId());
        Integer capAfter = jdbcTemplate.queryForObject("SELECT capacity FROM service_slots WHERE id=?", Integer.class, raceSlot.getId());
        List<UUID> optsAfter = jdbcTemplate.queryForList(
                "SELECT option_id FROM service_slot_options WHERE slot_id=?", UUID.class, raceSlot.getId());

        if (holdSuccess.get() == 1) {
            // HOLD won -> PATCH rolled back completely
            assertThat(statusAfter).isEqualTo("OPEN");
            assertThat(eqAfter).isEqualTo(eq10.id());
            assertThat(capAfter).isEqualTo(10);
            assertThat(optsAfter).containsExactlyInAnyOrder(opt1.getId(), opt2.getId());
            assertThat(redisTemplate.opsForHash().entries("inventory:slot:" + raceSlot.getId() + ":holds")).hasSize(1);
        } else {
            // Atomic PATCH won -> HOLD rejected cleanly
            assertThat(statusAfter).isEqualTo("CLOSED");
            assertThat(eqAfter).isEqualTo(eq6.id());
            assertThat(capAfter).isEqualTo(6);
            assertThat(optsAfter).containsExactly(opt2.getId());
            assertThat(redisTemplate.opsForHash().entries("inventory:slot:" + raceSlot.getId() + ":holds")).isEmpty();
        }
    }

    private ServiceSlotJpaEntity createSingleEquipmentSlot(LocalTime start, LocalTime end, int cap, UUID equipmentId) {
        LocalDate tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2);
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(testService.getId());
        slot.setDate(tomorrow);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(cap);
        slot.setBookedCount(0);
        slot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        slot.setStatus(SlotStatus.OPEN);
        slot = serviceSlotRepository.save(slot);

        ServiceSlotUnitJpaEntity unit = new ServiceSlotUnitJpaEntity();
        unit.setSlotId(slot.getId());
        unit.setUnitNumber(1);
        unit.setCapacity(cap);
        unit.setBookedCount(0);
        unit.setEquipmentId(equipmentId);
        serviceSlotUnitRepository.save(unit);

        jdbcTemplate.update("INSERT INTO service_slot_options(slot_id, option_id) VALUES (?, ?), (?, ?)",
                slot.getId(), opt1.getId(), slot.getId(), opt2.getId());
        return slot;
    }
}
