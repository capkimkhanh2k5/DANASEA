package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemAllocationJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.checkin.domain.exceptions.AmbiguousCheckinTargetException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.services.CheckinWindowPolicy;
import com.danasea.backend.modules.checkin.domain.services.FifoEligibilityEvaluator;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.EligibleSubOrderCandidate;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MasterQrAndFifoRemediationTest — Verification for ISSUE-QR-01 & ISSUE-FIFO-01")
class MasterQrAndFifoRemediationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private JpaCheckinTokenRepository checkinTokenRepository;
    @Mock private JpaSubOrderRepository subOrderRepository;
    @Mock private VendorLookupPort vendorLookupPort;
    @Mock private JpaServiceSlotRepository slotRepository;
    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaServiceRepository serviceRepository;
    @Mock private JpaBookingItemRepository bookingItemRepository;
    @Mock private JpaServiceOptionRepository serviceOptionRepository;
    @Mock private JpaServiceSlotUnitRepository slotUnitRepository;
    @Mock private JpaBookingItemAllocationRepository bookingItemAllocationRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private AuditLogInternalApi auditLogInternalApi;

    private QrTokenSigner qrTokenSigner;
    private CheckinWindowPolicy checkinWindowPolicy;
    private FifoEligibilityEvaluator fifoEligibilityEvaluator;
    private VerifyCheckinUseCase verifyCheckinUseCase;
    private EarlyDispatchSlotUseCase earlyDispatchSlotUseCase;

    private final UUID staffId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID otherVendorId = UUID.randomUUID();
    private final UUID masterOrderId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CheckinProperties properties = new CheckinProperties(
                SECRET,
                Duration.ofHours(2),
                Duration.ofMinutes(15)
        );
        qrTokenSigner = new QrTokenSigner(properties);
        checkinWindowPolicy = new CheckinWindowPolicy(properties);
        fifoEligibilityEvaluator = new FifoEligibilityEvaluator();

        // Wire dependencies into FifoEligibilityEvaluator
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "slotUnitRepository", slotUnitRepository);
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "bookingItemAllocationRepository", bookingItemAllocationRepository);
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "bookingItemRepository", bookingItemRepository);
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "serviceOptionRepository", serviceOptionRepository);
        ReflectionTestUtils.setField(fifoEligibilityEvaluator, "serviceRepository", serviceRepository);

        verifyCheckinUseCase = new VerifyCheckinUseCase(
                checkinTokenRepository,
                subOrderRepository,
                vendorLookupPort,
                qrTokenSigner,
                slotRepository,
                checkinWindowPolicy,
                masterOrderRepository
        );
        ReflectionTestUtils.setField(verifyCheckinUseCase, "serviceRepository", serviceRepository);
        ReflectionTestUtils.setField(verifyCheckinUseCase, "bookingItemRepository", bookingItemRepository);
        ReflectionTestUtils.setField(verifyCheckinUseCase, "serviceOptionRepository", serviceOptionRepository);
        ReflectionTestUtils.setField(verifyCheckinUseCase, "fifoEligibilityEvaluator", fifoEligibilityEvaluator);

        earlyDispatchSlotUseCase = new EarlyDispatchSlotUseCase(
                subOrderRepository,
                slotRepository,
                vendorLookupPort
        );
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "auditLogInternalApi", auditLogInternalApi);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "slotUnitRepository", slotUnitRepository);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "bookingItemAllocationRepository", bookingItemAllocationRepository);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "bookingItemRepository", bookingItemRepository);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "serviceOptionRepository", serviceOptionRepository);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "serviceRepository", serviceRepository);
        ReflectionTestUtils.setField(earlyDispatchSlotUseCase, "fifoEligibilityEvaluator", fifoEligibilityEvaluator);

        when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
        when(checkinTokenRepository.markAsUsedAtomic(any(), any(), any())).thenReturn(1);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        ServiceJpaEntity serviceEntity = new ServiceJpaEntity();
        serviceEntity.setId(serviceId);
        serviceEntity.setName("Danasea Island Tour");
        serviceEntity.setOperationType(OperationType.FIXED_DEPARTURE);
        serviceEntity.setDurationMinutes(60);
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(serviceEntity));
    }

    private ServiceSlotJpaEntity createSlot(UUID id, LocalDate date, LocalTime start, LocalTime end, int capacity, int booked, SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(id);
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        slot.setBookedCount(booked);
        slot.setStatus(status);
        slot.setInventoryType(InventoryType.PERSON_LIMIT);
        return slot;
    }

    private SubOrderJpaEntity createSubOrder(UUID id, UUID vId, UUID sId, SubOrderStatus status, int quantity) {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(id);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setVendorId(vId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(sId);
        subOrder.setStatus(status);
        subOrder.setQuantity(quantity);
        return subOrder;
    }

    private CheckinTokenJpaEntity setupMasterQrToken() {
        OffsetDateTime expiresAt = OffsetDateTime.now(VIETNAM_ZONE).plusHours(24);
        String rawToken = qrTokenSigner.generateToken(masterOrderId, expiresAt);
        String tokenHash = QrTokenSigner.hashToken(rawToken);

        CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
        tokenEntity.setId(UUID.randomUUID());
        tokenEntity.setSubOrderId(masterOrderId);
        tokenEntity.setQrTokenHash(tokenHash);
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setUsedAt(null);

        when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(new MasterOrderJpaEntity()));
        when(subOrderRepository.findByIdForUpdate(masterOrderId)).thenReturn(Optional.empty());

        return tokenEntity;
    }

    @Nested
    @DisplayName("ISSUE-QR-01 — Master QR Sequential Check-in & Selection Tests")
    class IssueQr01Tests {

        @Test
        @DisplayName("1. Master QR: Exactly one SubOrder for vendor -> succeeds without ambiguity, consumes token if sole suborder")
        void testMasterQr_SingleSubOrder_Success() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);
            LocalTime start = nowTime.plusMinutes(25);
            LocalTime end = start.plusMinutes(60);

            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = createSlot(slotId, today, start, end, 20, 5, SlotStatus.OPEN);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, slotId, SubOrderStatus.CONFIRMED, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

            VerifyCheckinResponse res = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(res.subOrderId()).isEqualTo(subOrderId);
            assertThat(res.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(tokenEntity.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("2. Master QR: Vendor has 2 SubOrders at different times, only 1 is currently in window -> succeeds and keeps Master QR token active")
        void testMasterQr_MultipleSubOrdersDifferentTimes_OnlyOneInWindow_Success() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            // SubOrder 1: Ca sáng (bắt đầu sau 20 phút) -> ĐANG trong cửa sổ check-in
            UUID slot1Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot1 = createSlot(slot1Id, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = createSubOrder(subOrder1Id, vendorId, slot1Id, SubOrderStatus.CONFIRMED, 2);

            // SubOrder 2: Ca chiều (bắt đầu sau 5 giờ) -> CHƯA đến giờ mở check-in
            UUID slot2Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot2 = createSlot(slot2Id, today, nowTime.plusHours(5), nowTime.plusHours(6), 20, 0, SlotStatus.OPEN);
            UUID subOrder2Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder2 = createSubOrder(subOrder2Id, vendorId, slot2Id, SubOrderStatus.CONFIRMED, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));
            when(slotRepository.findById(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findByIdForUpdate(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findById(slot2Id)).thenReturn(Optional.of(slot2));
            when(slotRepository.findByIdForUpdate(slot2Id)).thenReturn(Optional.of(slot2));

            VerifyCheckinResponse res = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(res.subOrderId()).isEqualTo(subOrder1Id);
            assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrder2.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);

            // Remaining suborder still exists -> Master QR token NOT marked used!
            assertThat(tokenEntity.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("3. Master QR: Multiple SubOrders BOTH in window at same time -> Returns Ambiguous with candidate details without consuming QR")
        void testMasterQr_MultipleSubOrdersOverlappingWindows_ThrowsAmbiguousExceptionWithCandidates() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            UUID slot1Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot1 = createSlot(slot1Id, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = createSubOrder(subOrder1Id, vendorId, slot1Id, SubOrderStatus.CONFIRMED, 2);

            UUID slot2Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot2 = createSlot(slot2Id, today, nowTime.plusMinutes(25), nowTime.plusMinutes(85), 20, 2, SlotStatus.OPEN);
            UUID subOrder2Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder2 = createSubOrder(subOrder2Id, vendorId, slot2Id, SubOrderStatus.CONFIRMED, 3);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));
            when(slotRepository.findById(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findByIdForUpdate(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findById(slot2Id)).thenReturn(Optional.of(slot2));
            when(slotRepository.findByIdForUpdate(slot2Id)).thenReturn(Optional.of(slot2));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(AmbiguousCheckinTargetException.class)
                    .satisfies(ex -> {
                        AmbiguousCheckinTargetException aEx = (AmbiguousCheckinTargetException) ex;
                        assertThat(aEx.getMasterOrderId()).isEqualTo(masterOrderId);
                        assertThat(aEx.getEligibleSubOrderIds()).containsExactlyInAnyOrder(subOrder1Id, subOrder2Id);
                        assertThat(aEx.getCandidates()).hasSize(2);
                        EligibleSubOrderCandidate c1 = aEx.getCandidates().stream()
                                .filter(c -> c.subOrderId().equals(subOrder1Id)).findFirst().orElseThrow();
                        assertThat(c1.quantity()).isEqualTo(2);
                        assertThat(c1.serviceName()).isEqualTo("Danasea Island Tour");
                    });

            assertThat(tokenEntity.getUsedAt()).isNull();
            verify(subOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("4. Master QR Selection: When selectedSubOrderId is provided, checks in selected SubOrder and keeps QR active for others")
        void testMasterQr_WithSelectedSubOrderId_ChecksInSelectedOnly() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            UUID slot1Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot1 = createSlot(slot1Id, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = createSubOrder(subOrder1Id, vendorId, slot1Id, SubOrderStatus.CONFIRMED, 2);

            UUID slot2Id = UUID.randomUUID();
            ServiceSlotJpaEntity slot2 = createSlot(slot2Id, today, nowTime.plusMinutes(25), nowTime.plusMinutes(85), 20, 2, SlotStatus.OPEN);
            UUID subOrder2Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder2 = createSubOrder(subOrder2Id, vendorId, slot2Id, SubOrderStatus.CONFIRMED, 3);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));
            when(slotRepository.findById(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findByIdForUpdate(slot1Id)).thenReturn(Optional.of(slot1));
            when(slotRepository.findById(slot2Id)).thenReturn(Optional.of(slot2));
            when(slotRepository.findByIdForUpdate(slot2Id)).thenReturn(Optional.of(slot2));

            // Vendor specifies subOrder2Id using SAME rawToken
            VerifyCheckinRequest request = new VerifyCheckinRequest(rawToken, subOrder2Id);
            VerifyCheckinResponse res = verifyCheckinUseCase.execute(request, staffId);

            assertThat(res.subOrderId()).isEqualTo(subOrder2Id);
            assertThat(subOrder2.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED); // Unchecked
            assertThat(tokenEntity.getUsedAt()).isNull(); // Still active for subOrder1!

            // Now check in subOrder1 with the SAME rawToken
            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1, subOrder2));
            VerifyCheckinRequest secondRequest = new VerifyCheckinRequest(rawToken, subOrder1Id);
            VerifyCheckinResponse secondRes = verifyCheckinUseCase.execute(secondRequest, staffId);

            assertThat(secondRes.subOrderId()).isEqualTo(subOrder1Id);
            assertThat(subOrder1.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            // Now all suborders of master order are CHECKED_IN -> Master QR is finally consumed!
            assertThat(tokenEntity.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("5. Master QR Selection: Attempting to select a SubOrder owned by another vendor throws UnauthorizedVendorCheckinException")
        void testMasterQr_SelectOtherVendorSubOrder_ThrowsUnauthorized() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            UUID slotAId = UUID.randomUUID();
            ServiceSlotJpaEntity slotA = createSlot(slotAId, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrderAId = UUID.randomUUID();
            SubOrderJpaEntity subOrderA = createSubOrder(subOrderAId, vendorId, slotAId, SubOrderStatus.CONFIRMED, 2);

            UUID slotBId = UUID.randomUUID();
            ServiceSlotJpaEntity slotB = createSlot(slotBId, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrderBId = UUID.randomUUID();
            SubOrderJpaEntity subOrderB = createSubOrder(subOrderBId, otherVendorId, slotBId, SubOrderStatus.CONFIRMED, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrderA, subOrderB));

            // Staff of Vendor A attempts to select subOrderB
            VerifyCheckinRequest request = new VerifyCheckinRequest(rawToken, subOrderBId);
            assertThatThrownBy(() -> verifyCheckinUseCase.execute(request, staffId))
                    .isInstanceOf(UnauthorizedVendorCheckinException.class);

            assertThat(tokenEntity.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("6. Master QR Selection: Attempting to select a SubOrder not in the MasterOrder throws OrderNotFoundException")
        void testMasterQr_SelectNonExistentSubOrderId_ThrowsNotFound() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = createSubOrder(subOrder1Id, vendorId, UUID.randomUUID(), SubOrderStatus.CONFIRMED, 2);
            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1));

            UUID randomNonExistentId = UUID.randomUUID();
            VerifyCheckinRequest request = new VerifyCheckinRequest(rawToken, randomNonExistentId);

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(request, staffId))
                    .isInstanceOf(OrderNotFoundException.class);

            assertThat(tokenEntity.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("7. Master QR: Vendor Isolation — MasterOrder contains SubOrders for Vendor A and Vendor B")
        void testMasterQr_MultipleVendors_VendorIsolation() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            UUID slotAId = UUID.randomUUID();
            ServiceSlotJpaEntity slotA = createSlot(slotAId, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrderAId = UUID.randomUUID();
            SubOrderJpaEntity subOrderA = createSubOrder(subOrderAId, vendorId, slotAId, SubOrderStatus.CONFIRMED, 2);

            UUID slotBId = UUID.randomUUID();
            ServiceSlotJpaEntity slotB = createSlot(slotBId, today, nowTime.plusMinutes(20), nowTime.plusMinutes(80), 20, 5, SlotStatus.OPEN);
            UUID subOrderBId = UUID.randomUUID();
            SubOrderJpaEntity subOrderB = createSubOrder(subOrderBId, otherVendorId, slotBId, SubOrderStatus.CONFIRMED, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrderA, subOrderB));
            when(slotRepository.findById(slotAId)).thenReturn(Optional.of(slotA));
            when(slotRepository.findByIdForUpdate(slotAId)).thenReturn(Optional.of(slotA));

            // Staff of Vendor A scans
            VerifyCheckinResponse res = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(res.subOrderId()).isEqualTo(subOrderAId);
            assertThat(subOrderA.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrderB.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED); // Vendor B untouched!
            assertThat(tokenEntity.getUsedAt()).isNull(); // Vendor B still has a CONFIRMED suborder
        }

        @Test
        @DisplayName("8. Master QR: All sub-orders of vendor already CHECKED_IN -> throws QrTokenAlreadyUsedException")
        void testMasterQr_AllAlreadyCheckedIn_ThrowsAlreadyUsed() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            UUID subOrder1Id = UUID.randomUUID();
            SubOrderJpaEntity subOrder1 = createSubOrder(subOrder1Id, vendorId, UUID.randomUUID(), SubOrderStatus.CHECKED_IN, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder1));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(QrTokenAlreadyUsedException.class)
                    .hasMessageContaining("đã được quét check-in trước đó");
        }
    }

    @Nested
    @DisplayName("ISSUE-FIFO-01 — SHARED-Only FIFO Early Dispatch Tests")
    class IssueFifo01Tests {

        @Test
        @DisplayName("1. FIFO SHARED: Sufficient capacity -> suggestion generated and dispatch succeeds")
        void testFifo_Shared_SufficientCapacity_Success() {
            LocalDate date = LocalDate.now(VIETNAM_ZONE).plusDays(1);
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 10, 5, SlotStatus.OPEN);
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(9, 30), 10, 4, SlotStatus.OPEN);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

            var res = earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Customer agrees to early slot"),
                    staffId
            );

            assertThat(res.actualSlotId()).isEqualTo(targetSlotId);
            assertThat(res.originalSlotId()).isEqualTo(origSlotId);
            assertThat(origSlot.getBookedCount()).isEqualTo(3); // 5 - 2
            assertThat(targetSlot.getBookedCount()).isEqualTo(6); // 4 + 2
        }

        @Test
        @DisplayName("2. FIFO PRIVATE: Private booking check-in -> NO early dispatch suggestion is generated")
        void testFifo_Private_CheckinEarly_NoFifoSuggestion() {
            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity currentSlot = createSlot(slotId, today, nowTime.plusMinutes(25), nowTime.plusMinutes(85), 20, 10, SlotStatus.OPEN);

            UUID earlySlotId = UUID.randomUUID();
            ServiceSlotJpaEntity earlySlot = createSlot(earlySlotId, today, nowTime.plusMinutes(10), nowTime.plusMinutes(70), 20, 0, SlotStatus.OPEN);

            UUID bookingItemId = UUID.randomUUID();
            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, slotId, SubOrderStatus.CONFIRMED, 10);
            subOrder.setBookingItemId(bookingItemId);

            UUID optionId = UUID.randomUUID();
            BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
            bookingItem.setId(bookingItemId);
            bookingItem.setOptionId(optionId);

            ServiceOptionJpaEntity option = new ServiceOptionJpaEntity();
            option.setId(optionId);
            option.setOptionType(OptionType.PRIVATE);

            // Setup Checkin token
            OffsetDateTime expiresAt = OffsetDateTime.now(VIETNAM_ZONE).plusHours(24);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);
            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(UUID.randomUUID());
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);

            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(currentSlot));
            when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(currentSlot));
            when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
            when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(option));
            when(slotRepository.findByServiceIdAndDateAndStatus(serviceId, today, SlotStatus.OPEN)).thenReturn(List.of(earlySlot));

            VerifyCheckinResponse response = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(response.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            // IMMUTABLE POLICY: PRIVATE MUST NOT HAVE FIFO SUGGESTIONS
            assertThat(response.earlyDispatchSuggestion()).isNull();
            assertThat(response.availableEarlySlots()).isNull();
        }

        @Test
        @DisplayName("3. FIFO PRIVATE: Direct API call to early-dispatch for PRIVATE booking -> Rejected with InvalidCheckinStateException")
        void testFifo_Private_DirectApiCall_Rejected() {
            LocalDate date = LocalDate.now(VIETNAM_ZONE).plusDays(1);
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 10, SlotStatus.OPEN);
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(9, 30), 20, 0, SlotStatus.OPEN);

            UUID bookingItemId = UUID.randomUUID();
            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 10);
            subOrder.setBookingItemId(bookingItemId);

            UUID optionId = UUID.randomUUID();
            BookingItemJpaEntity bookingItem = new BookingItemJpaEntity();
            bookingItem.setId(bookingItemId);
            bookingItem.setOptionId(optionId);

            ServiceOptionJpaEntity option = new ServiceOptionJpaEntity();
            option.setId(optionId);
            option.setOptionType(OptionType.PRIVATE);

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));
            when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
            when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(option));

            assertThatThrownBy(() -> earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Try to dispatch private"),
                    staffId
            )).isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("strictly prohibited for PRIVATE bookings");

            // Verify database was NOT mutated
            verify(slotRepository, never()).save(any());
            assertThat(subOrder.getActualSlotId()).isNull();
        }

        @Test
        @DisplayName("4. FIFO Duration Mismatch: Target slot has different duration (60 mins vs 30 mins) -> Rejected")
        void testFifo_DifferentDuration_Rejected() {
            LocalDate date = LocalDate.now(VIETNAM_ZONE).plusDays(1);
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 10, 5, SlotStatus.OPEN);
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(9, 0), 10, 0, SlotStatus.OPEN);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

            assertThatThrownBy(() -> earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Different duration"),
                    staffId
            )).isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("duration");
        }

        @Test
        @DisplayName("5. FIFO Redis Hold: Active holds reduce available capacity -> Rejects when remaining capacity < required")
        void testFifo_RedisHold_ReducesCapacity_RejectsWhenInsufficient() {
            LocalDate date = LocalDate.now(VIETNAM_ZONE).plusDays(1);
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);
            // Target slot has capacity 10, booked 5 -> Postgres says 5 seats free
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(9, 30), 10, 5, SlotStatus.OPEN);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 3); // Needs 3 seats

            // But Redis has an active hold holding 3 seats! Available = 10 - 5 - 3 = 2 < 3!
            long futureExp = System.currentTimeMillis() + 600000;
            when(hashOperations.entries("inventory:slot:" + targetSlotId + ":holds"))
                    .thenReturn(Map.of("hold1", futureExp + "|PERSON_LIMIT:3"));

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

            assertThatThrownBy(() -> earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Move shared group"),
                    staffId
            )).isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("sufficient capacity");
        }

        @Test
        @DisplayName("6. FIFO SHARED: Unit reallocation on SHARED_CAPACITY_UNITS succeeds for shared booking")
        void testFifo_Shared_UnitReallocation_Success() {
            LocalDate date = LocalDate.now(VIETNAM_ZONE).plusDays(1);
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);
            origSlot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);

            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, date, LocalTime.of(8, 30), LocalTime.of(9, 30), 20, 4, SlotStatus.OPEN);
            targetSlot.setInventoryType(InventoryType.SHARED_CAPACITY_UNITS);

            UUID bookingItemId = UUID.randomUUID();
            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);
            subOrder.setBookingItemId(bookingItemId);

            BookingItemAllocationJpaEntity alloc = BookingItemAllocationJpaEntity.builder()
                    .slotId(origSlotId)
                    .unitNumber(1)
                    .allocatedSeats(2)
                    .isPrivateLock(false) // SHARED!
                    .build();

            ServiceSlotUnitJpaEntity targetUnit1 = ServiceSlotUnitJpaEntity.builder()
                    .slotId(targetSlotId).unitNumber(1).capacity(10).bookedCount(4).equipmentBlocked(false).build();
            ServiceSlotUnitJpaEntity targetUnit2 = ServiceSlotUnitJpaEntity.builder()
                    .slotId(targetSlotId).unitNumber(2).capacity(10).bookedCount(0).equipmentBlocked(false).build();

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));
            when(bookingItemAllocationRepository.findByBookingItemId(bookingItemId)).thenReturn(List.of(alloc));
            when(slotUnitRepository.findBySlotIdOrderByUnitNumberAsc(targetSlotId)).thenReturn(List.of(targetUnit1, targetUnit2));

            var res = earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Move shared"),
                    staffId
            );

            assertThat(res.actualSlotId()).isEqualTo(targetSlotId);
            assertThat(alloc.getSlotId()).isEqualTo(targetSlotId);
            assertThat(alloc.getUnitNumber()).isEqualTo(1); // Fits in unit 1 (4 + 2 <= 10)
        }

        @Test
        @DisplayName("7. FIFO Target Cutoff: Target slot 08:45 has cutoff at 08:30 -> Evaluation at 08:20 is eligible")
        void testFifo_TargetSlotCutoff_At0820_Eligible() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID origSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);

            UUID targetSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 5, SlotStatus.OPEN);

            SubOrderJpaEntity subOrder = createSubOrder(UUID.randomUUID(), vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            OffsetDateTime nowAt0820 = LocalDateTime.of(today, LocalTime.of(8, 20)).atZone(VIETNAM_ZONE).toOffsetDateTime();
            var result = fifoEligibilityEvaluator.evaluate(subOrder, origSlot, targetSlot, origSlot, nowAt0820);

            assertThat(result.isEligible()).isTrue();
            assertThat(result.availableCapacity()).isEqualTo(15);
        }

        @Test
        @DisplayName("8. FIFO Target Cutoff: Target slot 08:45 has cutoff at 08:30 -> Evaluation at 08:30:00 (exact cutoff) is eligible")
        void testFifo_TargetSlotCutoff_At0830_ExactCutoff_Eligible() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID origSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);

            UUID targetSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 5, SlotStatus.OPEN);

            SubOrderJpaEntity subOrder = createSubOrder(UUID.randomUUID(), vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            OffsetDateTime nowAt0830 = LocalDateTime.of(today, LocalTime.of(8, 30, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();
            var result = fifoEligibilityEvaluator.evaluate(subOrder, origSlot, targetSlot, origSlot, nowAt0830);

            assertThat(result.isEligible()).isTrue();
        }

        @Test
        @DisplayName("9. FIFO Target Cutoff: Target slot 08:45 has cutoff at 08:30 -> Evaluation at 08:30:01 (1 sec past cutoff) is rejected")
        void testFifo_TargetSlotCutoff_At083001_PastCutoff_Rejected() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID origSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);

            UUID targetSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 5, SlotStatus.OPEN);

            SubOrderJpaEntity subOrder = createSubOrder(UUID.randomUUID(), vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            OffsetDateTime nowAt083001 = LocalDateTime.of(today, LocalTime.of(8, 30, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();
            var result = fifoEligibilityEvaluator.evaluate(subOrder, origSlot, targetSlot, origSlot, nowAt083001);

            assertThat(result.isEligible()).isFalse();
            assertThat(result.rejectionReason()).contains("Target slot check-in cutoff has passed");
        }

        @Test
        @DisplayName("10. FIFO Target Cutoff: Target slot 08:45 has cutoff at 08:30 -> Evaluation at 08:35 is rejected even with ample seats")
        void testFifo_TargetSlotCutoff_At0835_PastCutoff_Rejected() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID origSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);

            UUID targetSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 2, SlotStatus.OPEN);

            SubOrderJpaEntity subOrder = createSubOrder(UUID.randomUUID(), vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            OffsetDateTime nowAt0835 = LocalDateTime.of(today, LocalTime.of(8, 35, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();
            var result = fifoEligibilityEvaluator.evaluate(subOrder, origSlot, targetSlot, origSlot, nowAt0835);

            assertThat(result.isEligible()).isFalse();
            assertThat(result.rejectionReason()).contains("Target slot check-in cutoff has passed");
        }

        @Test
        @DisplayName("11. FIFO Race Condition: Suggested at 08:29:59 but confirmed at 08:30:01 -> Dispatch execution strictly rejected")
        void testFifo_RaceCondition_SuggestedBeforeCutoff_ConfirmedAfterCutoff_ExecutionRejects() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID subOrderId = UUID.randomUUID();
            UUID origSlotId = UUID.randomUUID();
            UUID targetSlotId = UUID.randomUUID();

            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 5, SlotStatus.OPEN);

            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(origSlotId)).thenReturn(Optional.of(origSlot));
            when(slotRepository.findByIdForUpdate(targetSlotId)).thenReturn(Optional.of(targetSlot));

            // Confirmation happens at 08:30:01 (1 second past target cutoff 08:30:00)
            OffsetDateTime confirmTime = LocalDateTime.of(today, LocalTime.of(8, 30, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> earlyDispatchSlotUseCase.execute(
                    new EarlyDispatchSlotUseCase.EarlyDispatchRequest(subOrderId, targetSlotId, "Late confirmation"),
                    staffId,
                    confirmTime
            )).isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("Target slot check-in cutoff has passed");

            // SubOrder and slots remain completely unchanged!
            assertThat(subOrder.getActualSlotId()).isNull();
            assertThat(subOrder.getSlotId()).isEqualTo(origSlotId);
            assertThat(origSlot.getBookedCount()).isEqualTo(5);
            assertThat(targetSlot.getBookedCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("12. FIFO Target Slot Status: Target slot CANCELLED or BLOCKED is rejected")
        void testFifo_TargetSlotCancelledOrBlocked_Rejected() {
            LocalDate today = LocalDate.of(2026, 11, 7);
            UUID origSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity origSlot = createSlot(origSlotId, today, LocalTime.of(10, 0), LocalTime.of(11, 0), 20, 5, SlotStatus.OPEN);

            UUID targetSlotId = UUID.randomUUID();
            ServiceSlotJpaEntity targetSlot = createSlot(targetSlotId, today, LocalTime.of(8, 45), LocalTime.of(9, 45), 20, 5, SlotStatus.CANCELLED);

            SubOrderJpaEntity subOrder = createSubOrder(UUID.randomUUID(), vendorId, origSlotId, SubOrderStatus.CHECKED_IN, 2);

            OffsetDateTime now = LocalDateTime.of(today, LocalTime.of(8, 20)).atZone(VIETNAM_ZONE).toOffsetDateTime();
            var result = fifoEligibilityEvaluator.evaluate(subOrder, origSlot, targetSlot, origSlot, now);

            assertThat(result.isEligible()).isFalse();
            assertThat(result.rejectionReason()).contains("Target slot is not open");
        }

        @Test
        @DisplayName("13. Check-in Rejection: SHARED scan after cutoff is rejected and NEVER triggers a notification")
        void testCheckin_SharedScanAfterCutoff_ThrowsAndDoesNotSendNotification() {
            CheckinTokenJpaEntity tokenEntity = setupMasterQrToken();
            String rawToken = qrTokenSigner.generateToken(masterOrderId, tokenEntity.getExpiresAt());

            LocalDate today = LocalDate.now(VIETNAM_ZONE);
            LocalTime nowTime = LocalTime.now(VIETNAM_ZONE);

            // Slot started 5 minutes ago (nowTime - 5) -> past cutoff (start - 15m)
            UUID slotId = UUID.randomUUID();
            ServiceSlotJpaEntity slot = createSlot(slotId, today, nowTime.minusMinutes(5), nowTime.plusMinutes(55), 20, 5, SlotStatus.OPEN);

            UUID subOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = createSubOrder(subOrderId, vendorId, slotId, SubOrderStatus.CONFIRMED, 2);

            when(subOrderRepository.findByMasterOrderIdForUpdate(masterOrderId)).thenReturn(List.of(subOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("Check-in is closed");

            assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
            assertThat(subOrder.getCheckedInAt()).isNull();
            assertThat(tokenEntity.getUsedAt()).isNull();
            verify(subOrderRepository, never()).save(any());
        }
    }
}
