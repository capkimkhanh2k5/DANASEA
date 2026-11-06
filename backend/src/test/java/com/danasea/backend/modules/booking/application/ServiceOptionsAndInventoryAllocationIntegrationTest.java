package com.danasea.backend.modules.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingHoldCommand;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.exceptions.InvalidBookingStateException;
import com.danasea.backend.modules.booking.domain.exceptions.SlotNotAvailableException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.service.application.usecases.CreateServiceOptionUseCase;
import com.danasea.backend.modules.service.application.usecases.CreateServiceSlotUseCase;
import com.danasea.backend.modules.service.application.usecases.GetServiceSlotsAvailabilityUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServiceOptionsUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServiceSlotsUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceOptionUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceSlotUseCase;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
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
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceOptionRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceOptionRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
public class ServiceOptionsAndInventoryAllocationIntegrationTest
        extends BaseSecurityIntegrationTest {
    @Autowired private DisruptionDecisionService decisions;

    @Autowired private CreateBookingHoldUseCase createBookingHoldUseCase;
    @Autowired private CancelBookingHoldUseCase cancelBookingHoldUseCase;
    @Autowired private GetServiceSlotsAvailabilityUseCase getServiceSlotsAvailabilityUseCase;
    @Autowired private CreateServiceOptionUseCase createServiceOptionUseCase;
    @Autowired private CreateServiceSlotUseCase createServiceSlotUseCase;
    @Autowired private UpdateServiceSlotUseCase updateServiceSlotUseCase;

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

    @Autowired private BookingRepositoryPort bookingRepository;
    @Autowired private InventoryLockPort inventoryLockPort;
    @Autowired private ServiceSlotPort serviceSlotPort;

    private UserJpaEntity testVendorUser;
    private VendorJpaEntity testVendor;
    private UserJpaEntity testCustomer;
    private ServiceJpaEntity testService;
    private ServiceSlotJpaEntity testSlot;
    private ServiceOptionJpaEntity sharedOption;
    private ServiceOptionJpaEntity privateOption;

    @Autowired private JdbcTemplate reviewJdbc;
    @Autowired private OrderPaymentService reviewOrderService;

    @BeforeEach
    void setUp() {
        assertThat(reviewJdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("testdb");
        reviewJdbc.execute("TRUNCATE users, categories, bookings CASCADE");
        // Clean Redis
        var keys = redisTemplate.keys("inventory:slot:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Clean DB
        allocationRepository.deleteAll();
        bookingItemRepository.deleteAll();
        jpaBookingRepository.deleteAll();
        serviceSlotUnitRepository.deleteAll();
        serviceSlotRepository.deleteAll();
        serviceOptionRepository.deleteAll();
        serviceRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // 1. Create Vendor user & profile
        testVendorUser = new UserJpaEntity();
        testVendorUser.setEmail("vendor_tour@danasea.com");
        testVendorUser.setPasswordHash("hashed_pw");
        testVendorUser.setFullName("Cù Lao Chàm Tour Vendor");
        testVendorUser.setRole(Role.VENDOR);
        testVendorUser.setIsEmailVerified(true);
        testVendorUser.setIsLocked(false);
        testVendorUser = userRepository.save(testVendorUser);

        testVendor = new VendorJpaEntity();
        testVendor.setUserId(testVendorUser.getId());
        testVendor.setBusinessName("Cù Lao Chàm Discovery");
        testVendor.setVerificationStatus(VerificationStatus.APPROVED);
        testVendor = vendorRepository.save(testVendor);

        // 2. Create Customer
        testCustomer = new UserJpaEntity();
        testCustomer.setEmail("customer1@example.com");
        testCustomer.setPasswordHash("hashed_pw");
        testCustomer.setFullName("Nguyen Van A");
        testCustomer.setRole(Role.CUSTOMER);
        testCustomer.setIsEmailVerified(true);
        testCustomer.setIsLocked(false);
        testCustomer = userRepository.save(testCustomer);

        // 3. Create Category
        CategoryJpaEntity category = new CategoryJpaEntity();
        category.setName("Tour Biển Đảo");
        category.setSlug("tour-bien-dao");
        category.setIsActive(true);
        category = categoryRepository.save(category);

        // 4. Create Published Service
        testService = new ServiceJpaEntity();
        testService.setVendorId(testVendor.getId());
        testService.setCategoryId(category.getId());
        testService.setName("Ngắm san hô Cù Lao Chàm");
        testService.setDescription("Tour lặn biển ngắm san hô và khám phá Cù Lao Chàm");
        testService.setPrice(new BigDecimal("200000.00"));
        testService.setStatus(ServiceStatus.PUBLISHED);
        testService = serviceRepository.save(testService);

        // 5. Create Slot with 3 units × 10 capacity (SHARED_CAPACITY_UNITS)
        testSlot = new ServiceSlotJpaEntity();
        testSlot.setServiceId(testService.getId());
        testSlot.setDate(LocalDate.now(Booking.VIETNAM_ZONE).plusDays(2));
        testSlot.setStartTime(LocalTime.of(8, 0));
        testSlot.setEndTime(LocalTime.of(12, 0));
        testSlot.setCapacity(30);
        testSlot.setBookedCount(0);
        testSlot.setStatus(SlotStatus.OPEN);
        testSlot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);
        testSlot = serviceSlotRepository.save(testSlot);

        // 3 units
        List<ServiceSlotUnitJpaEntity> units =
                List.of(
                        ServiceSlotUnitJpaEntity.builder()
                                .slotId(testSlot.getId())
                                .unitNumber(1)
                                .capacity(10)
                                .bookedCount(0)
                                .build(),
                        ServiceSlotUnitJpaEntity.builder()
                                .slotId(testSlot.getId())
                                .unitNumber(2)
                                .capacity(10)
                                .bookedCount(0)
                                .build(),
                        ServiceSlotUnitJpaEntity.builder()
                                .slotId(testSlot.getId())
                                .unitNumber(3)
                                .capacity(10)
                                .bookedCount(0)
                                .build());
        serviceSlotUnitRepository.saveAll(units);

        // 6. Create Options:
        // Option 1: Tour ghép (SHARED, PER_PERSON, 200.000đ)
        sharedOption =
                ServiceOptionJpaEntity.builder()
                        .serviceId(testService.getId())
                        .name("Tour ghép")
                        .optionType(OptionType.SHARED)
                        .pricingUnit(PricingUnit.PER_PERSON)
                        .price(new BigDecimal("200000.00"))
                        .benefits("Áo phao, kính lặn, hướng dẫn viên, bảo hiểm")
                        .status(OptionStatus.ACTIVE)
                        .build();
        sharedOption = serviceOptionRepository.save(sharedOption);

        // Option 2: Cano riêng (PRIVATE, PER_PACKAGE, max 10 pax, 1.800.000đ)
        privateOption =
                ServiceOptionJpaEntity.builder()
                        .serviceId(testService.getId())
                        .name("Cano riêng")
                        .optionType(OptionType.PRIVATE)
                        .pricingUnit(PricingUnit.PER_PACKAGE)
                        .price(new BigDecimal("1800000.00"))
                        .maxPaxPerPackage(10)
                        .benefits(
                                "Cano riêng biệt tối đa 10 người, thiết bị lặn chuyên nghiệp, nước"
                                    + " uống, trái cây")
                        .status(OptionStatus.ACTIVE)
                        .build();
        privateOption = serviceOptionRepository.save(privateOption);
    }

    @Test
    @DisplayName("Tình huống 1: Chưa có booking/hold -> Còn 30 suất ghép hoặc tối đa 3 gói riêng")
    void testScenario1_InitialAvailability() {
        var sharedAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        sharedOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(sharedAvail).hasSize(1);
        assertThat(sharedAvail.get(0).availablePaxOrPackages()).isEqualTo(30);
        assertThat(sharedAvail.get(0).bookable()).isTrue();

        var privateAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privateAvail).hasSize(1);
        assertThat(privateAvail.get(0).availablePaxOrPackages()).isEqualTo(3);
        assertThat(privateAvail.get(0).bookable()).isTrue();
    }

    @Test
    @DisplayName(
            "Tình huống 2: Giữ 1 gói riêng cho 4 khách -> Còn 20 suất ghép và tối đa 2 gói riêng,"
                + " tính giá đúng 1 gói")
    void testScenario2_HoldPrivatePackageFor4Pax() {
        CreateBookingHoldCommand cmd =
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 1, privateOption.getId(), 4)));

        BookingHoldResult holdResult = createBookingHoldUseCase.execute(cmd);
        assertThat(holdResult).isNotNull();
        // Một gói riêng đi 4 người vẫn tính giá một gói đã công bố (1.800.000đ)
        assertThat(holdResult.totalAmount()).isEqualByComparingTo(new BigDecimal("1800000.00"));

        // Check availability sau khi giữ
        var sharedAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        sharedOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(sharedAvail.get(0).availablePaxOrPackages()).isEqualTo(20);

        var privateAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privateAvail.get(0).availablePaxOrPackages()).isEqualTo(2);

        // Check allocations in DB
        var allocations = allocationRepository.findBySlotId(testSlot.getId());
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getIsPrivateLock()).isTrue();
    }

    @Test
    @DisplayName(
            "Tình huống 3: Có 20 khách ghép được phân bổ đầy 2 đơn vị -> Còn 10 suất ghép hoặc 1"
                + " gói riêng")
    void testScenario3_TwentySharedGuestsFillTwoUnits() {
        // Book 10 guests
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 10, sharedOption.getId(), null))));

        // Book another 10 guests
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 10, sharedOption.getId(), null))));

        var sharedAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        sharedOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(sharedAvail.get(0).availablePaxOrPackages()).isEqualTo(10);

        var privateAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privateAvail.get(0).availablePaxOrPackages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tình huống 4: Có 21–22 khách ghép -> Không còn gói riêng nào dù còn chỗ ghép")
    void testScenario4_TwentyOneSharedGuestsLeaveZeroPrivatePackages() {
        // Book 21 guests (Unit 1: 10, Unit 2: 10, Unit 3: 1)
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 21, sharedOption.getId(), null, true))));

        var sharedAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        sharedOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(sharedAvail.get(0).availablePaxOrPackages()).isEqualTo(9);

        var privateAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privateAvail.get(0).availablePaxOrPackages()).isEqualTo(0);
        assertThat(privateAvail.get(0).bookable()).isFalse();
    }

    @Test
    @DisplayName(
            "Tình huống 5: Mỗi đơn vị đều có khách ghép -> Không còn gói riêng dù tổng chỗ trống >="
                + " 10")
    void testScenario5_EachUnitHasSharedGuestsLeavesZeroPrivatePackages() {
        // Đặt 3 nhóm nhỏ để phân bổ vào cả 3 đơn vị:
        // Group 1: 6 pax (chiếm 6 chỗ ở Unit 1, Unit 1 còn 4 chỗ)
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 6, sharedOption.getId(), null))));
        // Group 2: 5 pax (không vừa 4 chỗ còn lại của Unit 1 -> mở Unit 2, Unit 2 còn 5 chỗ)
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 5, sharedOption.getId(), null))));
        // Group 3: 6 pax (không vừa 4 chỗ Unit 1 hay 5 chỗ Unit 2 -> mở Unit 3, Unit 3 còn 4 chỗ)
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 6, sharedOption.getId(), null))));

        // Giờ cả 3 đơn vị đều có khách ghép: tổng chỗ còn lại = 4 + 5 + 4 = 13 >= 10
        var sharedAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        sharedOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(sharedAvail.get(0).availablePaxOrPackages()).isEqualTo(13);

        // Nhưng không có bất kỳ đơn vị nào hoàn toàn trống -> 0 gói riêng!
        var privateAvail =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privateAvail.get(0).availablePaxOrPackages()).isEqualTo(0);
        assertThat(privateAvail.get(0).bookable()).isFalse();
    }

    @Test
    @DisplayName("Tình huống 6: Một gói tối đa 10 khách, gửi 11 khách -> Bị từ chối")
    void testScenario6_PrivatePackageExceedingMaxPaxIsRejected() {
        CreateBookingHoldCommand cmd =
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 1, privateOption.getId(), 11)));

        assertThatThrownBy(() -> createBookingHoldUseCase.execute(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Participants exceed");
    }

    @Test
    @DisplayName(
            "Tình huống 7: Gói riêng và khách ghép tranh đơn vị cuối -> Chỉ thao tác phù hợp tồn"
                + " còn lại thành công")
    void testScenario7_ConcurrencyContentionForLastUnit() throws Exception {
        // Đã có 20 khách chiếm trọn 2 đơn vị đầu
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 20, sharedOption.getId(), null, true))));

        // Lúc này chỉ còn đúng 1 đơn vị cuối cùng (10 chỗ trống)
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Thread 1: Đặt 1 gói riêng (cần trọn vẹn đơn vị trống)
        Future<?> f1 =
                executor.submit(
                        () -> {
                            try {
                                startLatch.await(5, TimeUnit.SECONDS);
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                1,
                                                                privateOption.getId(),
                                                                8))));
                                successCount.incrementAndGet();
                            } catch (InsufficientInventoryException e) {
                                failCount.incrementAndGet();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

        // Thread 2: Đặt 6 khách ghép
        Future<?> f2 =
                executor.submit(
                        () -> {
                            try {
                                startLatch.await(5, TimeUnit.SECONDS);
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                6,
                                                                sharedOption.getId(),
                                                                null))));
                                successCount.incrementAndGet();
                            } catch (InsufficientInventoryException e) {
                                failCount.incrementAndGet();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

        startLatch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Một bên giữ trước thành công, bên còn lại không thỏa mãn điều kiện tồn -> Thất bại!
        // Nếu gói riêng thành công trước -> không còn chỗ cho 6 khách ghép.
        // Nếu 6 khách ghép thành công trước -> đơn vị không còn empty -> gói riêng thất bại.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Tình huống 8: Hold hết hạn hoặc hủy -> Trả đúng tồn; đơn vị trống trở lại có thể bán"
                + " riêng")
    void testScenario8_HoldCancellationRestoresPrivateAvailability() {
        BookingHoldResult hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 1, privateOption.getId(), 6))));

        // Check private avail is 2
        var privAvailBefore =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privAvailBefore.get(0).availablePaxOrPackages()).isEqualTo(2);

        // Cancel the hold
        cancelBookingHoldUseCase.execute(
                new CancelBookingHoldCommand(hold.bookingId(), testCustomer.getId()));

        // Check private avail restored back to 3
        var privAvailAfter =
                getServiceSlotsAvailabilityUseCase.execute(
                        testService.getId(),
                        privateOption.getId(),
                        testSlot.getDate(),
                        testSlot.getDate(),
                        1);
        assertThat(privAvailAfter.get(0).availablePaxOrPackages()).isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Tình huống 9: Callback xác nhận gửi lặp -> Tồn và trạng thái không thay đổi lần hai")
    void testScenario9_DuplicateConfirmationDoesNotDoubleMutateInventory() {
        BookingHoldResult hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));

        // Mock payment successful
        ConfirmBookingUseCase confirmUseCase =
                new ConfirmBookingUseCase(
                        bookingRepository, inventoryLockPort, serviceSlotPort, bookingId -> true);

        // First confirmation
        confirmUseCase.execute(new ConfirmBookingCommand(hold.bookingId(), testCustomer.getId()));

        var slotAfterFirst = serviceSlotRepository.findById(testSlot.getId()).orElseThrow();
        assertThat(slotAfterFirst.getBookedCount()).isEqualTo(4);

        // Second duplicate confirmation call
        confirmUseCase.execute(new ConfirmBookingCommand(hold.bookingId(), testCustomer.getId()));

        var slotAfterSecond = serviceSlotRepository.findById(testSlot.getId()).orElseThrow();
        // Booked count MUST still be 4, NOT 8!
        assertThat(slotAfterSecond.getBookedCount()).isEqualTo(4);
    }

    @Test
    @DisplayName(
            "Tình huống 10: Vendor sửa ca không được giảm sức chứa dưới cam kết hoặc xóa đơn vị"
                + " đang có khách")
    void testScenario10_VendorUpdateSlotSafetyGuards() {
        // Book 4 shared pax in Unit 1
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 4, sharedOption.getId(), null))));

        // Vendor attempts to reduce Unit 1 capacity from 10 to 3 (< 4 booked)
        UpdateServiceSlotRequest invalidReq =
                new UpdateServiceSlotRequest(
                        SlotStatus.OPEN,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(1, 3), // invalid: booked is 4!
                                new CreateSlotUnitRequest(2, 10),
                                new CreateSlotUnitRequest(3, 10)));

        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        testSlot.getId(),
                                        invalidReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot reduce");

        // Vendor attempts to remove Unit 1 which has active booking
        UpdateServiceSlotRequest removeBookedUnitReq =
                new UpdateServiceSlotRequest(
                        SlotStatus.OPEN,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(2, 10),
                                new CreateSlotUnitRequest(3, 10)));

        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        testSlot.getId(),
                                        removeBookedUnitReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only completely empty units can be removed");

        // Vendor closes slot (CLOSED) -> Allowed!
        UpdateServiceSlotRequest closeReq =
                new UpdateServiceSlotRequest(SlotStatus.CLOSED, null, null);
        var updated =
                updateServiceSlotUseCase.execute(
                        testVendorUser.getId(), testService.getId(), testSlot.getId(), closeReq);
        assertThat(updated.status()).isEqualTo(SlotStatus.CLOSED);
    }

    @Test
    void reviewMultiplePrivateItemsMustUseDistinctUnits() {
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 1, privateOption.getId(), 4),
                                new BookingHoldItemDto(
                                        testSlot.getId(), 1, privateOption.getId(), 7))));
        assertThat(
                        allocationRepository.findBySlotId(testSlot.getId()).stream()
                                .map(a -> a.getUnitNumber())
                                .distinct()
                                .count())
                .isEqualTo(2);
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        privateOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        1)
                                .get(0)
                                .availablePaxOrPackages())
                .isEqualTo(1);
    }

    @Test
    void reviewEditingUnitCapacityMustNotCommitActiveHolds() {
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 4, sharedOption.getId(), null))));
        updateServiceSlotUseCase.execute(
                testVendorUser.getId(),
                testService.getId(),
                testSlot.getId(),
                new UpdateServiceSlotRequest(
                        null,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(1, 12),
                                new CreateSlotUnitRequest(2, 10),
                                new CreateSlotUnitRequest(3, 10))));
        assertThat(
                        serviceSlotUnitRepository
                                .findBySlotIdOrderByUnitNumberAsc(testSlot.getId())
                                .get(0)
                                .getBookedCount())
                .isZero();
    }

    @Test
    void reviewPersonLimitReductionMustIncludeHolds() {
        testSlot.setInventoryType(InventoryType.PERSON_LIMIT);
        serviceSlotRepository.save(testSlot);
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 8, sharedOption.getId(), null))));
        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        testSlot.getId(),
                                        new UpdateServiceSlotRequest(null, 5, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewStartedSlotMustRejectHold() {
        testSlot.setDate(LocalDate.now(Booking.VIETNAM_ZONE));
        testSlot.setStartTime(LocalTime.MIN);
        serviceSlotRepository.save(testSlot);
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                1,
                                                                sharedOption.getId(),
                                                                null)))))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void reviewGroupMustNotSplitWithoutConsent() {
        for (int q : new int[] {6, 5, 6}) {
            createBookingHoldUseCase.execute(
                    new CreateBookingHoldCommand(
                            testCustomer.getId(),
                            List.of(
                                    new BookingHoldItemDto(
                                            testSlot.getId(), q, sharedOption.getId(), null))));
        }
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                6,
                                                                sharedOption.getId(),
                                                                null)))))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void reviewPrivatePackagesOnPersonLimitAreRejected() {
        testSlot.setInventoryType(InventoryType.PERSON_LIMIT);
        serviceSlotRepository.save(testSlot);
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                3,
                                                                privateOption.getId(),
                                                                4)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewDuplicateDepartureMustBeRejected() {
        assertThatThrownBy(
                        () ->
                                createServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        new CreateServiceSlotRequest(
                                                testSlot.getDate(),
                                                testSlot.getStartTime(),
                                                testSlot.getEndTime(),
                                                InventoryType.PERSON_LIMIT,
                                                30,
                                                null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void reviewInactiveOptionsMustNotFallBackToServicePrice() {
        sharedOption.setStatus(OptionStatus.INACTIVE);
        privateOption.setStatus(OptionStatus.INACTIVE);
        serviceOptionRepository.saveAll(List.of(sharedOption, privateOption));
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(), 1)))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void reviewNonPositiveParticipantsMustBeRejected() {
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                1,
                                                                privateOption.getId(),
                                                                -1)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewGroupOfFourUsesEmptyUnitInsteadOfSplitting() {
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 8, sharedOption.getId(), null))));
        BookingHoldResult h =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));
        var allocations =
                bookingRepository
                        .findByIdWithItems(h.bookingId())
                        .orElseThrow()
                        .getItems()
                        .get(0)
                        .getAllocations();
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getUnitNumber()).isEqualTo(2);
        assertThat(allocations.get(0).getAllocatedSeats()).isEqualTo(4);
    }

    @Test
    void reviewVendorRejectMustReleasePrivateUnit() {
        BookingHoldResult h =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 1, privateOption.getId(), 4))));
        new ConfirmBookingUseCase(bookingRepository, inventoryLockPort, serviceSlotPort, id -> true)
                .execute(new ConfirmBookingCommand(h.bookingId(), testCustomer.getId()));
        UUID masterId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID itemId =
                bookingRepository
                        .findByIdWithItems(h.bookingId())
                        .orElseThrow()
                        .getItems()
                        .get(0)
                        .getId();
        reviewJdbc.update(
                "INSERT INTO"
                    + " master_orders(id,booking_id,customer_id,status,total_amount,idempotency_key,created_at,updated_at)"
                    + " VALUES (?,?,?,'PAID',1800000,?,NOW(),NOW())",
                masterId,
                h.bookingId(),
                testCustomer.getId(),
                "review-master-" + masterId);
        reviewJdbc.update(
                "INSERT INTO"
                    + " sub_orders(id,booking_item_id,master_order_id,vendor_id,service_id,slot_id,quantity,unit_price,subtotal_amount,status,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,1,1800000,1800000,'CONFIRMED',NOW(),NOW())",
                subId,
                itemId,
                masterId,
                testVendor.getId(),
                testService.getId(),
                testSlot.getId());
        reviewJdbc.update("UPDATE master_orders SET payment_status='PAID' WHERE id=?", masterId);
        reviewOrderService.rejectVendorBooking(
                testVendorUser.getId(), itemId, "Vendor cannot fulfill", "review-reject-" + subId);
        decisions.chooseRefund(subId, testCustomer.getId());
        assertThat(
                        serviceSlotUnitRepository
                                .findBySlotIdOrderByUnitNumberAsc(testSlot.getId())
                                .get(0)
                                .getBookedCount())
                .isZero();
        assertThat(serviceSlotRepository.findById(testSlot.getId()).orElseThrow().getBookedCount())
                .isZero();
    }

    @Test
    void splitConsentAllowsAllocationAcrossUnitsAndMatchesAvailability() {
        for (int q : new int[] {6, 5, 6}) {
            createBookingHoldUseCase.execute(
                    new CreateBookingHoldCommand(
                            testCustomer.getId(),
                            List.of(
                                    new BookingHoldItemDto(
                                            testSlot.getId(), q, sharedOption.getId(), null))));
        }
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        6)
                                .get(0)
                                .bookable())
                .isFalse();
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        6,
                                        true)
                                .get(0)
                                .bookable())
                .isTrue();
        BookingHoldResult hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(),
                                                6,
                                                sharedOption.getId(),
                                                null,
                                                true))));
        assertThat(
                        bookingRepository
                                .findByIdWithItems(hold.bookingId())
                                .orElseThrow()
                                .getItems()
                                .get(0)
                                .getAllocations())
                .hasSize(2);
    }

    @Test
    void failedMixedHoldLeavesNoPartialReservation() {
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                3,
                                                                privateOption.getId(),
                                                                4),
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                1,
                                                                sharedOption.getId(),
                                                                null)))))
                .isInstanceOf(InsufficientInventoryException.class);
        assertThat(redisTemplate.opsForHash().size("inventory:slot:" + testSlot.getId() + ":holds"))
                .isZero();
        assertThat(allocationRepository.findBySlotId(testSlot.getId())).isEmpty();
    }

    @Test
    void privateReleaseIsIdempotentAndRestoresEveryCounter() {
        BookingHoldResult hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 1, privateOption.getId(), 4))));
        new ConfirmBookingUseCase(bookingRepository, inventoryLockPort, serviceSlotPort, id -> true)
                .execute(new ConfirmBookingCommand(hold.bookingId(), testCustomer.getId()));
        var items = bookingRepository.findByIdWithItems(hold.bookingId()).orElseThrow().getItems();
        serviceSlotPort.releaseCapacityBatch(items);
        serviceSlotPort.releaseCapacityBatch(items);
        assertThat(
                        serviceSlotUnitRepository
                                .findBySlotIdOrderByUnitNumberAsc(testSlot.getId())
                                .get(0)
                                .getBookedCount())
                .isZero();
        assertThat(serviceSlotRepository.findById(testSlot.getId()).orElseThrow().getBookedCount())
                .isZero();
    }

    @Test
    void repeatedSharedItemsRemainSeparateGroups() {
        BookingHoldResult hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 6, sharedOption.getId(), null),
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 6, sharedOption.getId(), null))));
        var items = bookingRepository.findByIdWithItems(hold.bookingId()).orElseThrow().getItems();
        assertThat(items).hasSize(2);
        assertThat(
                        items.stream()
                                .flatMap(i -> i.getAllocations().stream())
                                .map(a -> a.getUnitNumber())
                                .distinct()
                                .count())
                .isEqualTo(2);
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        1)
                                .get(0)
                                .availablePaxOrPackages())
                .isEqualTo(18);
    }

    @Test
    void privateUnitCannotBeResizedUntilItsCommitmentIsReleased() {
        var hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 1, privateOption.getId(), 4))));
        var resize =
                new UpdateServiceSlotRequest(
                        null,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(1, 12),
                                new CreateSlotUnitRequest(2, 10),
                                new CreateSlotUnitRequest(3, 10)));
        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        testSlot.getId(),
                                        resize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private package");
        new ConfirmBookingUseCase(bookingRepository, inventoryLockPort, serviceSlotPort, id -> true)
                .execute(new ConfirmBookingCommand(hold.bookingId(), testCustomer.getId()));
        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        testVendorUser.getId(),
                                        testService.getId(),
                                        testSlot.getId(),
                                        resize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private package");
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        1)
                                .get(0)
                                .availablePaxOrPackages())
                .isEqualTo(20);
    }

    @Test
    void expiredHoldDoesNotBecomeBookedDuringCapacityEdit() {
        var hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));
        reviewJdbc.update(
                "UPDATE bookings SET hold_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'"
                    + " WHERE id=?",
                hold.bookingId());
        redisTemplate
                .opsForHash()
                .put(
                        "inventory:slot:" + testSlot.getId() + ":holds",
                        hold.bookingId().toString(),
                        "1|UNITS:1:4:0");
        updateServiceSlotUseCase.execute(
                testVendorUser.getId(),
                testService.getId(),
                testSlot.getId(),
                new UpdateServiceSlotRequest(
                        null,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(1, 3),
                                new CreateSlotUnitRequest(2, 10),
                                new CreateSlotUnitRequest(3, 10))));
        assertThat(
                        serviceSlotUnitRepository
                                .findBySlotIdOrderByUnitNumberAsc(testSlot.getId())
                                .get(0)
                                .getBookedCount())
                .isZero();
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        1)
                                .get(0)
                                .availablePaxOrPackages())
                .isEqualTo(23);
    }

    @Autowired private PlatformTransactionManager inventoryTransactionManager;

    @Test
    void concurrentCapacityEditWaitsForCheckoutAndSeesItsHold() throws Exception {
        testSlot.setInventoryType(InventoryType.PERSON_LIMIT);
        serviceSlotRepository.save(testSlot);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try {
            Future<?> checkout =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(inventoryTransactionManager)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        createBookingHoldUseCase.execute(
                                                                new CreateBookingHoldCommand(
                                                                        testCustomer.getId(),
                                                                        List.of(
                                                                                new BookingHoldItemDto(
                                                                                        testSlot
                                                                                                .getId(),
                                                                                        8,
                                                                                        sharedOption
                                                                                                .getId(),
                                                                                        null))));
                                                        held.countDown();
                                                        try {
                                                            assertThat(
                                                                            commit.await(
                                                                                    5,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                                    .isTrue();
                                                        } catch (InterruptedException e) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(e);
                                                        }
                                                    }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> edit =
                    executor.submit(
                            () ->
                                    assertThatThrownBy(
                                                    () ->
                                                            updateServiceSlotUseCase.execute(
                                                                    testVendorUser.getId(),
                                                                    testService.getId(),
                                                                    testSlot.getId(),
                                                                    new UpdateServiceSlotRequest(
                                                                            null, 5, null)))
                                            .isInstanceOf(IllegalArgumentException.class));
            assertThatThrownBy(() -> edit.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            commit.countDown();
            checkout.get(10, TimeUnit.SECONDS);
            edit.get(10, TimeUnit.SECONDS);
            assertThat(serviceSlotRepository.findById(testSlot.getId()).orElseThrow().getCapacity())
                    .isEqualTo(30);
        } finally {
            commit.countDown();
            executor.shutdownNow();
        }
    }

    @Autowired private UpdateServiceOptionUseCase updateServiceOptionUseCase;
    @Autowired private GetVendorServiceSlotsUseCase getVendorServiceSlotsUseCase;
    @Autowired private GetVendorServiceOptionsUseCase getVendorServiceOptionsUseCase;

    @Test
    void anotherVendorCannotManageTheseOptionsOrDepartures() {
        UserJpaEntity user = new UserJpaEntity();
        user.setEmail("other-vendor@example.com");
        user.setFullName("Other vendor");
        user.setRole(Role.VENDOR);
        user.setPasswordHash("hash");
        user = userRepository.save(user);
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setUserId(user.getId());
        vendor.setBusinessName("Other vendor");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendorRepository.save(vendor);
        UUID otherUserId = user.getId();
        assertThatThrownBy(
                        () ->
                                getVendorServiceOptionsUseCase.execute(
                                        otherUserId, testService.getId()))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        assertThatThrownBy(
                        () ->
                                getVendorServiceSlotsUseCase.execute(
                                        otherUserId, testService.getId()))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        assertThatThrownBy(
                        () ->
                                createServiceOptionUseCase.execute(
                                        otherUserId,
                                        testService.getId(),
                                        new CreateServiceOptionRequest(
                                                "Private",
                                                OptionType.PRIVATE,
                                                PricingUnit.PER_PACKAGE,
                                                new BigDecimal("1800000"),
                                                10,
                                                "Included")))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        assertThatThrownBy(
                        () ->
                                updateServiceOptionUseCase.execute(
                                        otherUserId,
                                        testService.getId(),
                                        privateOption.getId(),
                                        new UpdateServiceOptionRequest(
                                                null, null, null, null, OptionStatus.INACTIVE)))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        assertThatThrownBy(
                        () ->
                                updateServiceSlotUseCase.execute(
                                        otherUserId,
                                        testService.getId(),
                                        testSlot.getId(),
                                        new UpdateServiceSlotRequest(
                                                SlotStatus.CLOSED, null, null)))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
    }

    @Test
    void foreignOptionCannotBeHeldAndMultipleOptionsRequireAnExplicitSelection() {
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(),
                                                                1,
                                                                UUID.randomUUID(),
                                                                null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                createBookingHoldUseCase.execute(
                                        new CreateBookingHoldCommand(
                                                testCustomer.getId(),
                                                List.of(
                                                        new BookingHoldItemDto(
                                                                testSlot.getId(), 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("optionId");
        assertThat(redisTemplate.opsForHash().size("inventory:slot:" + testSlot.getId() + ":holds"))
                .isZero();
    }

    @Test
    void emptyUnitCanBeRemovedWithoutMovingAnOccupiedUnit() {
        createBookingHoldUseCase.execute(
                new CreateBookingHoldCommand(
                        testCustomer.getId(),
                        List.of(
                                new BookingHoldItemDto(
                                        testSlot.getId(), 4, sharedOption.getId(), null))));
        updateServiceSlotUseCase.execute(
                testVendorUser.getId(),
                testService.getId(),
                testSlot.getId(),
                new UpdateServiceSlotRequest(
                        null,
                        null,
                        List.of(
                                new CreateSlotUnitRequest(1, 10),
                                new CreateSlotUnitRequest(2, 10))));
        assertThat(serviceSlotUnitRepository.findBySlotIdOrderByUnitNumberAsc(testSlot.getId()))
                .hasSize(2);
        assertThat(
                        getServiceSlotsAvailabilityUseCase
                                .execute(
                                        testService.getId(),
                                        sharedOption.getId(),
                                        testSlot.getDate(),
                                        testSlot.getDate(),
                                        1)
                                .get(0)
                                .availablePaxOrPackages())
                .isEqualTo(16);
    }

    @Test
    void holdCancellationCannotOverwriteConcurrentConfirmation() throws Exception {
        var hold =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch confirmed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try {
            Future<?> confirmation =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(inventoryTransactionManager)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        new ConfirmBookingUseCase(
                                                                        bookingRepository,
                                                                        inventoryLockPort,
                                                                        serviceSlotPort,
                                                                        id -> true)
                                                                .execute(
                                                                        new ConfirmBookingCommand(
                                                                                hold.bookingId(),
                                                                                testCustomer
                                                                                        .getId()));
                                                        confirmed.countDown();
                                                        try {
                                                            assertThat(
                                                                            commit.await(
                                                                                    5,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                                    .isTrue();
                                                        } catch (InterruptedException e) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(e);
                                                        }
                                                    }));
            assertThat(confirmed.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> cancellation =
                    executor.submit(
                            () ->
                                    assertThatThrownBy(
                                                    () ->
                                                            cancelBookingHoldUseCase.execute(
                                                                    new CancelBookingHoldCommand(
                                                                            hold.bookingId(),
                                                                            testCustomer.getId())))
                                            .isInstanceOf(InvalidBookingStateException.class));
            assertThatThrownBy(() -> cancellation.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            commit.countDown();
            confirmation.get(10, TimeUnit.SECONDS);
            cancellation.get(10, TimeUnit.SECONDS);
            assertThat(
                            bookingRepository
                                    .findByIdWithItems(hold.bookingId())
                                    .orElseThrow()
                                    .isConfirmed())
                    .isTrue();
            assertThat(
                            serviceSlotRepository
                                    .findById(testSlot.getId())
                                    .orElseThrow()
                                    .getBookedCount())
                    .isEqualTo(4);
        } finally {
            commit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasingAnUnconfirmedItemCannotDeductAnotherBookingsCommittedSeats() {
        var first =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));
        new ConfirmBookingUseCase(bookingRepository, inventoryLockPort, serviceSlotPort, id -> true)
                .execute(new ConfirmBookingCommand(first.bookingId(), testCustomer.getId()));
        var second =
                createBookingHoldUseCase.execute(
                        new CreateBookingHoldCommand(
                                testCustomer.getId(),
                                List.of(
                                        new BookingHoldItemDto(
                                                testSlot.getId(), 4, sharedOption.getId(), null))));
        serviceSlotPort.releaseCapacityBatch(
                bookingRepository.findByIdWithItems(second.bookingId()).orElseThrow().getItems());
        assertThat(serviceSlotRepository.findById(testSlot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(4);
        assertThat(
                        serviceSlotUnitRepository
                                .findBySlotIdOrderByUnitNumberAsc(testSlot.getId())
                                .get(0)
                                .getBookedCount())
                .isEqualTo(4);
    }
}
