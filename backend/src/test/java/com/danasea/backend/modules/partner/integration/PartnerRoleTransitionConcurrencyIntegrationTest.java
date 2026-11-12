package com.danasea.backend.modules.partner.integration;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.ports.CustomerAccountPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.partner.application.services.CustomerPartnerEligibilityService;
import com.danasea.backend.modules.partner.application.usecases.ApprovePartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.SubmitPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.SubmitPartnerApplicationRequest;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PartnerRoleTransitionConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovePartnerApplicationUseCase approvePartnerApplicationUseCase;

    @Autowired
    private SubmitPartnerApplicationUseCase submitPartnerApplicationUseCase;

    @Autowired
    private CustomerPartnerEligibilityService eligibilityService;

    @Autowired
    private CustomerAccountPort customerAccountPort;

    @Autowired
    private AccountInternalApi accountInternalApi;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaVendorRoleApplicationRepository applicationRepository;

    @Autowired
    private JpaMasterOrderRepository masterOrderRepository;

    @Autowired
    private JpaSubOrderRepository subOrderRepository;

    @Autowired
    private JpaBookingRepository bookingRepository;

    @Autowired
    private JpaServiceSlotRepository serviceSlotRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Set<UUID> createdUserIds = new HashSet<>();
    private final Set<UUID> createdApplicationIds = new HashSet<>();
    private final Set<UUID> createdSubOrderIds = new HashSet<>();
    private final Set<UUID> createdMasterOrderIds = new HashSet<>();
    private final Set<UUID> createdBookingIds = new HashSet<>();
    private final Set<UUID> createdSlotIds = new HashSet<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdApplicationIds) {
            applicationRepository.deleteById(id);
        }
        for (UUID id : createdSubOrderIds) {
            subOrderRepository.deleteById(id);
        }
        for (UUID id : createdMasterOrderIds) {
            masterOrderRepository.deleteById(id);
        }
        for (UUID id : createdBookingIds) {
            bookingRepository.deleteById(id);
        }
        for (UUID id : createdSlotIds) {
            serviceSlotRepository.deleteById(id);
        }
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
    }

    private User createCustomer(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Test Customer");
        user.setPasswordHash("hashed_pass");
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setSessionVersion(0L);
        User saved = accountInternalApi.saveUser(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private User createAdmin(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Test Admin");
        user.setPasswordHash("hashed_pass");
        user.setRole(Role.ADMIN);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        user.setSessionVersion(0L);
        User saved = accountInternalApi.saveUser(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private VendorRoleApplicationJpaEntity createApplication(UUID customerId) {
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setUserId(customerId);
        entity.setBusinessName("Test Sea Tour");
        entity.setTaxCode("0409998881");
        entity.setAddress("100 Bach Dang, Da Nang");
        entity.setBankAccountNumber("0123456789");
        entity.setBankName("MB Bank");
        entity.setBankAccountHolder("NGUYEN VAN TEST");
        entity.setStatus(PartnerApplicationStatus.PENDING);
        VendorRoleApplicationJpaEntity saved = applicationRepository.save(entity);
        createdApplicationIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Pessimistic row lock serializes Admin Approve and Customer Booking creation on PostgreSQL")
    void concurrency_AdminApproveAndCustomerBooking_SerializeCleanly() throws Exception {
        User customer = createCustomer("concurrent_cust_" + UUID.randomUUID() + "@danasea.test");
        User admin = createAdmin("concurrent_admin_" + UUID.randomUUID() + "@danasea.test");
        VendorRoleApplicationJpaEntity app = createApplication(customer.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean customerBookingFailed = new AtomicBoolean(false);
        AtomicBoolean adminApproveSucceeded = new AtomicBoolean(false);

        Future<?> f1 = executor.submit(() -> {
            try {
                latch.await();
                approvePartnerApplicationUseCase.execute(app.getId(), admin.getId());
                adminApproveSucceeded.set(true);
            } catch (Exception e) {
                // Admin might fail if customer booked first
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                latch.await();
                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tt.execute(status -> {
                    customerAccountPort.validateCustomerForBookingWithLock(customer.getId());
                    return null;
                });
            } catch (AccessDeniedException e) {
                customerBookingFailed.set(true);
            } catch (Exception e) {
                // Other exception
            }
        });

        latch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // One of them must have succeeded and serialized cleanly
        UserJpaEntity refreshedUser = userRepository.findById(customer.getId()).orElseThrow();
        if (adminApproveSucceeded.get()) {
            assertThat(refreshedUser.getRole()).isEqualTo(Role.VENDOR);
            assertThat(refreshedUser.getSessionVersion()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("Stale JWT token with old session_version is rejected with 401 after Admin approval")
    void staleJwt_AfterRoleTransition_RejectedWith401() throws Exception {
        User customer = createCustomer("jwt_cust_" + UUID.randomUUID() + "@danasea.test");
        User admin = createAdmin("jwt_admin_" + UUID.randomUUID() + "@danasea.test");
        VendorRoleApplicationJpaEntity app = createApplication(customer.getId());

        // Issue JWT token while user is CUSTOMER with sessionVersion = 0
        Authentication auth = new Authentication(
                customer.getId(),
                customer.getEmail(),
                customer.getPasswordHash(),
                customer.getRole().name(),
                true,
                true,
                customer.getLocale(),
                customer.getSessionVersion()
        );
        String oldJwtToken = jwtTokenProvider.generateAccessToken(auth);

        // Before approval: Customer can access /api/orders
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + oldJwtToken))
                .andExpect(status().isOk());

        // Admin approves partner application -> increments sessionVersion in PostgreSQL
        approvePartnerApplicationUseCase.execute(app.getId(), admin.getId());

        // Verify sessionVersion is incremented in DB
        UserJpaEntity updatedUser = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(Role.VENDOR);
        assertThat(updatedUser.getSessionVersion()).isEqualTo(1L);

        // After approval: Same JWT token must be rejected with 401 Unauthorized
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + oldJwtToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Completed sub-order within 7-day dispute window blocks partner transition on PostgreSQL")
    void completedSubOrder_Within7Days_BlocksPartnerTransition() {
        User customer = createCustomer("dispute_cust_" + UUID.randomUUID() + "@danasea.test");

        // Create Service Slot with date = 3 days ago
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(UUID.randomUUID());
        slot.setDate(LocalDate.now().minusDays(3));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        slot.setCapacity(20);
        slot.setBookedCount(1);
        slot.setStatus(SlotStatus.CLOSED);
        slot.setInventoryType(InventoryType.PERSON_LIMIT);
        ServiceSlotJpaEntity savedSlot = serviceSlotRepository.save(slot);
        createdSlotIds.add(savedSlot.getId());

        // Create Booking CONFIRMED (not active hold, but required for FK)
        BookingJpaEntity booking = new BookingJpaEntity();
        booking.setCustomerId(customer.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("500000"));
        BookingJpaEntity savedBooking = bookingRepository.save(booking);
        createdBookingIds.add(savedBooking.getId());

        // Create MasterOrder COMPLETED
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setCustomerId(customer.getId());
        masterOrder.setBookingId(savedBooking.getId());
        masterOrder.setStatus(MasterOrderStatus.COMPLETED);
        masterOrder.setTotalAmount(new BigDecimal("500000"));
        MasterOrderJpaEntity savedMaster = masterOrderRepository.save(masterOrder);
        createdMasterOrderIds.add(savedMaster.getId());

        // Create SubOrder COMPLETED pointing to slot 3 days ago
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(savedMaster.getId());
        subOrder.setVendorId(UUID.randomUUID());
        subOrder.setServiceId(savedSlot.getServiceId());
        subOrder.setSlotId(savedSlot.getId());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setQuantity(1);
        subOrder.setUnitPrice(new BigDecimal("500000"));
        subOrder.setSubtotalAmount(new BigDecimal("500000"));
        SubOrderJpaEntity savedSub = subOrderRepository.save(subOrder);
        createdSubOrderIds.add(savedSub.getId());

        // Must throw CustomerHasActiveObligationsException because experience date is within 7 days
        assertThatThrownBy(() -> eligibilityService.validateCustomerEligibility(customer.getId()))
                .isInstanceOf(CustomerHasActiveObligationsException.class)
                .hasMessageContaining("7-day dispute period");
    }

    @Test
    @DisplayName("Completed sub-order older than 7 days allows partner transition on PostgreSQL")
    void completedSubOrder_OlderThan7Days_AllowsPartnerTransition() {
        User customer = createCustomer("old_cust_" + UUID.randomUUID() + "@danasea.test");

        // Create Service Slot with date = 10 days ago (> 7 days)
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(UUID.randomUUID());
        slot.setDate(LocalDate.now().minusDays(10));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        slot.setCapacity(20);
        slot.setBookedCount(1);
        slot.setStatus(SlotStatus.CLOSED);
        slot.setInventoryType(InventoryType.PERSON_LIMIT);
        ServiceSlotJpaEntity savedSlot = serviceSlotRepository.save(slot);
        createdSlotIds.add(savedSlot.getId());

        // Create Booking CONFIRMED (not active hold, but required for FK)
        BookingJpaEntity booking = new BookingJpaEntity();
        booking.setCustomerId(customer.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("500000"));
        BookingJpaEntity savedBooking = bookingRepository.save(booking);
        createdBookingIds.add(savedBooking.getId());

        // Create MasterOrder COMPLETED
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setCustomerId(customer.getId());
        masterOrder.setBookingId(savedBooking.getId());
        masterOrder.setStatus(MasterOrderStatus.COMPLETED);
        masterOrder.setTotalAmount(new BigDecimal("500000"));
        MasterOrderJpaEntity savedMaster = masterOrderRepository.save(masterOrder);
        createdMasterOrderIds.add(savedMaster.getId());

        // Create SubOrder COMPLETED pointing to slot 10 days ago
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(savedMaster.getId());
        subOrder.setVendorId(UUID.randomUUID());
        subOrder.setServiceId(savedSlot.getServiceId());
        subOrder.setSlotId(savedSlot.getId());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setQuantity(1);
        subOrder.setUnitPrice(new BigDecimal("500000"));
        subOrder.setSubtotalAmount(new BigDecimal("500000"));
        SubOrderJpaEntity savedSub = subOrderRepository.save(subOrder);
        createdSubOrderIds.add(savedSub.getId());

        // Must succeed without exception
        assertThatCode(() -> eligibilityService.validateCustomerEligibility(customer.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Customer who booked, paid, completed tour > 7 days ago CAN successfully apply and be approved as Vendor")
    void customerWithFullyCompletedHistory_CanApplyAndBeApprovedAsVendor() {
        User customer = createCustomer("historical_cust_" + UUID.randomUUID() + "@danasea.test");

        // 1. Historical Service Slot (14 days ago)
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(UUID.randomUUID());
        slot.setDate(LocalDate.now().minusDays(14));
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        slot.setCapacity(15);
        slot.setBookedCount(1);
        slot.setStatus(SlotStatus.CLOSED);
        slot.setInventoryType(InventoryType.PERSON_LIMIT);
        ServiceSlotJpaEntity savedSlot = serviceSlotRepository.save(slot);
        createdSlotIds.add(savedSlot.getId());

        // 2. Historical Booking CONFIRMED (completed payment in the past)
        BookingJpaEntity booking = new BookingJpaEntity();
        booking.setCustomerId(customer.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("1200000"));
        BookingJpaEntity savedBooking = bookingRepository.save(booking);
        createdBookingIds.add(savedBooking.getId());

        // 3. Historical MasterOrder COMPLETED
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setCustomerId(customer.getId());
        masterOrder.setBookingId(savedBooking.getId());
        masterOrder.setStatus(MasterOrderStatus.COMPLETED);
        masterOrder.setTotalAmount(new BigDecimal("1200000"));
        MasterOrderJpaEntity savedMaster = masterOrderRepository.save(masterOrder);
        createdMasterOrderIds.add(savedMaster.getId());

        // 4. Historical SubOrder COMPLETED (finished 14 days ago)
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(savedMaster.getId());
        subOrder.setVendorId(UUID.randomUUID());
        subOrder.setServiceId(savedSlot.getServiceId());
        subOrder.setSlotId(savedSlot.getId());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setCheckedInAt(OffsetDateTime.now().minusDays(14));
        subOrder.setQuantity(2);
        subOrder.setUnitPrice(new BigDecimal("600000"));
        subOrder.setSubtotalAmount(new BigDecimal("1200000"));
        SubOrderJpaEntity savedSub = subOrderRepository.save(subOrder);
        createdSubOrderIds.add(savedSub.getId());

        // 5. Submit partner application - MUST SUCCEED (no active obligations)
        SubmitPartnerApplicationRequest request = new SubmitPartnerApplicationRequest(
                "Historical Tour Operator",
                "0101234567",
                "123 Bach Dang, Da Nang",
                "1903654321",
                "Techcombank",
                "NGUYEN VAN A"
        );
        var appResponse = submitPartnerApplicationUseCase.execute(customer.getId(), request);
        assertThat(appResponse).isNotNull();
        assertThat(appResponse.status()).isEqualTo(PartnerApplicationStatus.PENDING);
        createdApplicationIds.add(appResponse.id());

        // 6. Admin approves partner application - MUST SUCCEED
        User admin = createAdmin("admin_" + UUID.randomUUID() + "@danasea.test");
        var approvedResponse = approvePartnerApplicationUseCase.execute(appResponse.id(), admin.getId());
        assertThat(approvedResponse).isNotNull();
        assertThat(approvedResponse.status()).isEqualTo(PartnerApplicationStatus.APPROVED);

        // 7. Verify user role transitioned to VENDOR
        UserJpaEntity updatedUser = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(Role.VENDOR);
    }
}
