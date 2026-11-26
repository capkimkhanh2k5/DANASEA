package com.danasea.backend.modules.booking.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.models.SlotValidationDetails;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.service.application.services.SlotAvailabilityCalculator;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;

@Testcontainers(disabledWithoutDocker = true)
class PrivateSlotRedisExclusivityIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RedisInventoryLockAdapter lockAdapter;
    private SlotAvailabilityCalculator calculator;

    @BeforeAll
    static void initRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void destroyRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        var conn = connectionFactory.getConnection();
        conn.serverCommands().flushDb();
        conn.close();
        lockAdapter = new RedisInventoryLockAdapter(redisTemplate);
        calculator = new SlotAvailabilityCalculator(mock(ServiceSlotRepositoryPort.class), redisTemplate);
    }

    private InventoryLockItem privateItem(UUID slotId, int packages, int paxPerPackage, List<InventoryLockItem.UnitLockInfo> units) {
        return InventoryLockItem.builder()
                .slotId(slotId)
                .quantity(packages)
                .maxCapacity(units.stream().mapToInt(InventoryLockItem.UnitLockInfo::getCapacity).sum())
                .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                .optionType(OptionType.PRIVATE)
                .paxPerPackage(paxPerPackage)
                .allowSplit(true)
                .units(units)
                .build();
    }

    private InventoryLockItem sharedItem(UUID slotId, int guests, boolean allowSplit, List<InventoryLockItem.UnitLockInfo> units) {
        return InventoryLockItem.builder()
                .slotId(slotId)
                .quantity(guests)
                .maxCapacity(units.stream().mapToInt(InventoryLockItem.UnitLockInfo::getCapacity).sum())
                .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                .optionType(OptionType.SHARED)
                .allowSplit(allowSplit)
                .units(units)
                .build();
    }

    private ServiceSlot slotModel(UUID slotId, List<InventoryLockItem.UnitLockInfo> unitInfos) {
        return ServiceSlot.builder()
                .id(slotId)
                .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                .status(SlotStatus.OPEN)
                .units(unitInfos.stream()
                        .map(u -> (ServiceSlotUnit) ServiceSlotUnit.builder()
                                .unitNumber(u.getUnitNumber())
                                .capacity(u.getCapacity())
                                .bookedCount(u.getBookedCount())
                                .build())
                        .toList())
                .build();
    }

    @Test
    @DisplayName("Case 1: 1 unit cannot satisfy 2 consecutive PRIVATE holds")
    void singleUnitRejectsSecondPrivateHold() {
        UUID slotId = UUID.randomUUID();
        var units = List.of(new InventoryLockItem.UnitLockInfo(1, 8, 0));

        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 2, units)), Duration.ofMinutes(5));

        assertThatThrownBy(() ->
                lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 2, units)), Duration.ofMinutes(5)))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    @DisplayName("Case 2: 2 concurrent PRIVATE holds competing for last unit allow strictly 1 winner")
    void concurrentPrivateHoldsOnLastUnitAllowOnlyOneWinner() throws InterruptedException {
        UUID slotId = UUID.randomUUID();
        var units = List.of(new InventoryLockItem.UnitLockInfo(1, 8, 0));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 4, units)), Duration.ofMinutes(5));
                    wins.incrementAndGet();
                } catch (InsufficientInventoryException expected) {
                    failures.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(wins.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Case 3: Release or expiry frees PRIVATE unit for subsequent hold")
    void releaseAndExpiryRestorePrivateAvailability() throws InterruptedException {
        UUID slotId = UUID.randomUUID();
        var units = List.of(new InventoryLockItem.UnitLockInfo(1, 8, 0));
        UUID firstHold = UUID.randomUUID();
        var item = privateItem(slotId, 1, 2, units);

        lockAdapter.acquireHolds(firstHold, List.of(item), Duration.ofSeconds(1));
        lockAdapter.releaseHolds(firstHold, List.of(item));

        UUID expiringHold = UUID.randomUUID();
        lockAdapter.acquireHolds(expiringHold, List.of(privateItem(slotId, 1, 2, units)), Duration.ofMillis(120));
        Thread.sleep(200);

        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 2, units)), Duration.ofMinutes(5));
        assertThat(calculator.evaluate(slotModel(slotId, units),
                ServiceOption.builder().optionType(OptionType.PRIVATE).maxPaxPerPackage(2).build()).available())
                .isZero();
    }

    @Test
    @DisplayName("Case 4: 2 units satisfy 2 distinct PRIVATE holds")
    void twoUnitsSatisfyTwoPrivateHolds() {
        UUID slotId = UUID.randomUUID();
        var units = List.of(
                new InventoryLockItem.UnitLockInfo(1, 8, 0),
                new InventoryLockItem.UnitLockInfo(2, 8, 0));

        var first = privateItem(slotId, 1, 3, units);
        var second = privateItem(slotId, 1, 3, units);
        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(first), Duration.ofMinutes(5));
        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(second), Duration.ofMinutes(5));

        assertThat(first.getAllocations()).hasSize(1);
        assertThat(second.getAllocations()).hasSize(1);
        assertThat(first.getAllocations().get(0).getUnitNumber())
                .isNotEqualTo(second.getAllocations().get(0).getUnitNumber());
    }

    @Test
    @DisplayName("Case 5: Mixed slot leaves remaining unit seats available for SHARED after 1 PRIVATE hold")
    void mixedSlotKeepsRemainingUnitForSharedAfterPrivateHold() {
        UUID slotId = UUID.randomUUID();
        var units = List.of(
                new InventoryLockItem.UnitLockInfo(1, 8, 0),
                new InventoryLockItem.UnitLockInfo(2, 8, 0));

        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 2, units)), Duration.ofMinutes(5));

        ServiceSlot slot = slotModel(slotId, units);
        int sharedRemaining = calculator.evaluate(slot, ServiceOption.builder().optionType(OptionType.SHARED).build()).available();
        int privateRemaining = calculator.evaluate(slot, ServiceOption.builder().optionType(OptionType.PRIVATE).maxPaxPerPackage(4).build()).available();
        assertThat(sharedRemaining).isEqualTo(8);
        assertThat(privateRemaining).isEqualTo(1);

        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(sharedItem(slotId, 5, false, units)), Duration.ofMinutes(5));
        assertThat(calculator.evaluate(slot, ServiceOption.builder().optionType(OptionType.PRIVATE).maxPaxPerPackage(4).build()).available())
                .isZero();
    }

    @Test
    @DisplayName("Case 6: Unit with existing SHARED guest blocks PRIVATE lock on that unit")
    void unitWithSharedPassengerBlocksPrivateHold() {
        UUID slotId = UUID.randomUUID();
        var singleBookedUnits = List.of(new InventoryLockItem.UnitLockInfo(1, 8, 1));

        assertThatThrownBy(() ->
                lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 2, singleBookedUnits)), Duration.ofMinutes(5)))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    @DisplayName("Case 7: PRIVATE hold blocks SHARED guests from locking the same unit")
    void privateHoldBlocksSharedFromSameUnit() {
        UUID slotId = UUID.randomUUID();
        var units = List.of(new InventoryLockItem.UnitLockInfo(1, 8, 0));

        lockAdapter.acquireHolds(UUID.randomUUID(), List.of(privateItem(slotId, 1, 1, units)), Duration.ofMinutes(5));

        assertThatThrownBy(() ->
                lockAdapter.acquireHolds(UUID.randomUUID(), List.of(sharedItem(slotId, 1, true, units)), Duration.ofMinutes(5)))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    @DisplayName("Quantity over 100 or exceeding slot capacity fails HOLD validation with zero Booking persisted")
    void holdRejectsQuantityExceedingMaxOrAvailabilityWithoutCreatingBooking() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        BookingRepositoryPort bookingRepo = mock(BookingRepositoryPort.class);
        ServiceSlotPort slotPort = mock(ServiceSlotPort.class);
        CreateBookingHoldUseCase holdUseCase = new CreateBookingHoldUseCase(bookingRepo, lockAdapter, slotPort);

        assertThatThrownBy(() -> holdUseCase.execute(new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 114, optionId, null, false, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 100");

        SlotValidationDetails tenSeatSlot = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .serviceName("Para Demo")
                .bookingDate(LocalDate.now().plusDays(2))
                .bookingTime(LocalTime.of(8, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .inventoryType(InventoryType.PERSON_LIMIT)
                .servicePublished(true)
                .options(java.util.Map.of(optionId, SlotValidationDetails.ServiceOptionValidationDetails.builder()
                        .id(optionId)
                        .name("Goi chung")
                        .optionType(OptionType.SHARED)
                        .pricingUnit(PricingUnit.PER_PERSON)
                        .status(OptionStatus.ACTIVE)
                        .price(new BigDecimal("650000"))
                        .build()))
                .build();
        when(slotPort.findSlotDetailsBatch(List.of(slotId))).thenReturn(List.of(tenSeatSlot));

        assertThatThrownBy(() -> holdUseCase.execute(new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 11, optionId, null, false, null)))))
                .isInstanceOf(InsufficientInventoryException.class);

        verify(bookingRepo, never()).save(any(Booking.class));
    }
}
