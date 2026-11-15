package com.danasea.backend.modules.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase;
import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase.EarlyDispatchRequest;
import com.danasea.backend.modules.checkin.application.usecases.EarlyDispatchSlotUseCase.EarlyDispatchResponse;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@DisplayName("FifoAndHoldConcurrencyIntegrationTest — Real Redis & PostgreSQL Invariant Verification")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FifoAndHoldConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InventoryLockPort inventoryLockPort;

    @Autowired
    private EarlyDispatchSlotUseCase earlyDispatchSlotUseCase;

    @Autowired
    private JpaServiceSlotRepository slotRepository;

    @Autowired
    private JpaSubOrderRepository subOrderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private VendorLookupPort vendorLookupPort;

    private UUID vendorId;
    private UUID staffUserId;
    private UUID serviceId;
    private UUID slotAId; // Ca A (sớm): 08:00 - 09:30
    private UUID slotBId; // Ca B (muộn): 10:00 - 11:30
    private UUID customerId;
    private UUID masterOrderId;
    private UUID subOrderId;

    private final LocalDate testDate = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        slotAId = UUID.randomUUID();
        slotBId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();

        when(vendorLookupPort.findVendorIdByUserId(staffUserId)).thenReturn(Optional.of(vendorId));

        // 1. Dọn sạch Redis key liên quan
        redisTemplate.delete("inventory:slot:" + slotAId + ":holds");
        redisTemplate.delete("inventory:slot:" + slotBId + ":holds");

        // 2. Tạo User & Vendor & Service trong DB
        jdbc.update("INSERT INTO users(id, email, password_hash, full_name, role, locale, created_at, updated_at, session_version) "
                + "VALUES (?, ?, 'hash', 'Staff', 'VENDOR', 'vi', NOW(), NOW(), 0)", staffUserId, staffUserId + "@vendor.com");
        jdbc.update("INSERT INTO users(id, email, password_hash, full_name, role, locale, created_at, updated_at, session_version) "
                + "VALUES (?, ?, 'hash', 'Customer', 'CUSTOMER', 'vi', NOW(), NOW(), 0)", customerId, customerId + "@cust.com");

        jdbc.update("INSERT INTO vendors(id, user_id, business_name, verification_status, created_at, updated_at) "
                + "VALUES (?, ?, 'Marine Adventure', 'APPROVED', NOW(), NOW())", vendorId, staffUserId);

        jdbc.update("INSERT INTO services(id, vendor_id, name, status, price, operation_type, created_at, updated_at) "
                + "VALUES (?, ?, 'Canoe Island Tour', 'PUBLISHED', 500000, 'FIXED_DEPARTURE', NOW(), NOW())", serviceId, vendorId);

        // 3. Tạo Slot A (Ca sớm 08:00): Capacity = 10, Booked = 9 -> Chỉ còn đúng 1 chỗ!
        jdbc.update("INSERT INTO service_slots(id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at) "
                + "VALUES (?, ?, ?, '08:00', '09:30', 10, 9, 'OPEN', 'PERSON_LIMIT', NOW(), NOW())", slotAId, serviceId, testDate);

        // 4. Tạo Slot B (Ca muộn 10:00): Capacity = 10, Booked = 1
        jdbc.update("INSERT INTO service_slots(id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at) "
                + "VALUES (?, ?, ?, '10:00', '11:30', 10, 1, 'OPEN', 'PERSON_LIMIT', NOW(), NOW())", slotBId, serviceId, testDate);

        // 5. Tạo MasterOrder & SubOrder cho khách B ở Slot B, đã CHECKED_IN
        jdbc.update("INSERT INTO master_orders(id, customer_id, status, total_amount, payment_status, created_at, updated_at) "
                + "VALUES (?, ?, 'PAID', 500000, 'PAID', NOW(), NOW())", masterOrderId, customerId);

        jdbc.update("INSERT INTO sub_orders(id, master_order_id, vendor_id, service_id, slot_id, status, quantity, subtotal_amount, final_amount, commission_rate, checked_in_at, created_at, updated_at, reschedule_version) "
                + "VALUES (?, ?, ?, ?, ?, 'CHECKED_IN', 1, 500000, 500000, 0.10, NOW(), NOW(), NOW(), 0)", subOrderId, masterOrderId, vendorId, serviceId, slotBId);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("inventory:slot:" + slotAId + ":holds");
        redisTemplate.delete("inventory:slot:" + slotBId + ":holds");

        jdbc.update("DELETE FROM sub_orders WHERE id = ?", subOrderId);
        jdbc.update("DELETE FROM master_orders WHERE id = ?", masterOrderId);
        jdbc.update("DELETE FROM service_slots WHERE id IN (?, ?)", slotAId, slotBId);
        jdbc.update("DELETE FROM services WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM vendors WHERE id = ?", vendorId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", staffUserId, customerId);
    }

    @Test
    @DisplayName("Simultaneous Hold vs FIFO Early Dispatch: Exactly 1 succeeds, no overbooking occurs")
    void testSimultaneousHoldAndFifoEarlyDispatch_ZeroOverbooking() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        UUID holdId = UUID.randomUUID();

        // Task 1: Website customer attempts to acquire Redis Hold for 1 seat on Slot A
        Callable<Boolean> holdTask = () -> {
            startGate.await();
            try {
                InventoryLockItem lockItem = InventoryLockItem.builder()
                        .slotId(slotAId)
                        .quantity(1)
                        .maxCapacity(10)
                        .inventoryType(InventoryType.PERSON_LIMIT)
                        .build();
                inventoryLockPort.acquireHolds(holdId, List.of(lockItem), Duration.ofMinutes(2));
                return true;
            } catch (InsufficientInventoryException e) {
                return false;
            }
        };

        // Task 2: Vendor staff attempts to dispatch customer from Slot B to Slot A (1 seat)
        Callable<Boolean> fifoTask = () -> {
            startGate.await();
            try {
                EarlyDispatchRequest request = new EarlyDispatchRequest(subOrderId, slotAId, "Early dispatch");
                EarlyDispatchResponse response = earlyDispatchSlotUseCase.execute(request, staffUserId);
                return response != null && response.actualSlotId().equals(slotAId);
            } catch (InvalidCheckinStateException e) {
                return false;
            }
        };

        Future<Boolean> holdFuture = executor.submit(holdTask);
        Future<Boolean> fifoFuture = executor.submit(fifoTask);

        // Đồng loạt kích hoạt 2 giao dịch tranh chấp chỗ cuối cùng
        startGate.countDown();

        boolean holdSucceeded = holdFuture.get(10, TimeUnit.SECONDS);
        boolean fifoSucceeded = fifoFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 1. Bất biến: CHỈ ĐÚNG 1 GIAO DỊCH ĐƯỢC CẤP TÀI NGUYÊN (XOR)
        assertThat(holdSucceeded ^ fifoSucceeded)
                .as("Exactly one of Redis Hold or FIFO dispatch must succeed for the last seat")
                .isTrue();

        // 2. Kiểm tra tồn kho Slot A và Slot B sau giao dịch
        ServiceSlotJpaEntity updatedSlotA = slotRepository.findById(slotAId).orElseThrow();
        ServiceSlotJpaEntity updatedSlotB = slotRepository.findById(slotBId).orElseThrow();

        if (fifoSucceeded) {
            // FIFO thắng: Slot A bookedCount tăng từ 9 lên 10; Slot B bookedCount giảm từ 1 về 0
            assertThat(updatedSlotA.getBookedCount()).isEqualTo(10);
            assertThat(updatedSlotB.getBookedCount()).isEqualTo(0);

            var updatedSubOrder = subOrderRepository.findById(subOrderId).orElseThrow();
            assertThat(updatedSubOrder.getActualSlotId()).isEqualTo(slotAId);
            assertThat(updatedSubOrder.getSlotId()).isEqualTo(slotBId); // Slot gốc bảo toàn
        } else {
            // Hold thắng: Slot A bookedCount giữ nguyên 9, nhưng Redis có 1 hold đang hoạt động
            assertThat(updatedSlotA.getBookedCount()).isEqualTo(9);
            assertThat(updatedSlotB.getBookedCount()).isEqualTo(1);

            var updatedSubOrder = subOrderRepository.findById(subOrderId).orElseThrow();
            assertThat(updatedSubOrder.getActualSlotId()).isNull(); // Không bị đổi sang Slot A
        }

        // 3. Tổng tải trọng (booked + holds) trên Slot A tuyệt đối không vượt quá Capacity = 10
        int redisHoldCount = getRedisHoldQuantity(slotAId);
        int totalOccupancy = updatedSlotA.getBookedCount() + redisHoldCount;
        assertThat(totalOccupancy)
                .as("Total occupancy (booked + holds) must not exceed capacity 10")
                .isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Invariant Violation Detection: Throws and rolls back when currentSlot bookedCount < quantity")
    void testEarlyDispatch_ThrowsAndRollsBack_WhenCurrentBookedCountIsCorrupted() {
        // Cố tình làm sai lệch bookedCount của Slot B về 0 trong khi SubOrder quantity = 1
        jdbc.update("UPDATE service_slots SET booked_count = 0 WHERE id = ?", slotBId);

        EarlyDispatchRequest request = new EarlyDispatchRequest(subOrderId, slotAId, "Early dispatch with corrupted inventory");

        org.junit.jupiter.api.Assertions.assertThrows(InvalidCheckinStateException.class, () -> {
            earlyDispatchSlotUseCase.execute(request, staffUserId);
        });

        // Chứng minh: Slot A không bị tăng bookedCount, SubOrder không bị đổi ca
        ServiceSlotJpaEntity unchangedSlotA = slotRepository.findById(slotAId).orElseThrow();
        assertThat(unchangedSlotA.getBookedCount()).isEqualTo(9);

        var unchangedSubOrder = subOrderRepository.findById(subOrderId).orElseThrow();
        assertThat(unchangedSubOrder.getActualSlotId()).isNull();
    }

    private int getRedisHoldQuantity(UUID slotId) {
        String key = "inventory:slot:" + slotId + ":holds";
        var entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int total = 0;
        for (var entry : entries.entrySet()) {
            String val = (String) entry.getValue();
            if (val != null) {
                int barPos = val.indexOf('|');
                if (barPos > 0) {
                    long exp = Long.parseLong(val.substring(0, barPos));
                    if (exp <= now) continue;
                    String details = val.substring(barPos + 1);
                    if (details.startsWith("PERSON_LIMIT:")) {
                        total += Integer.parseInt(details.substring("PERSON_LIMIT:".length()));
                    } else if (details.startsWith("SHARED_CAPACITY_UNITS|")) {
                        String[] parts = details.split("\\|");
                        if (parts.length >= 3) {
                            total += Integer.parseInt(parts[2]);
                        }
                    }
                }
            }
        }
        return total;
    }
}
