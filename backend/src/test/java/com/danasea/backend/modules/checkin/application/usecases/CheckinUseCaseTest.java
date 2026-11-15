package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinTokenNotFoundException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrSignatureException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrTokenException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenExpiredException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedCheckinAccessException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.GenerateQrResponse;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckinUseCaseTest — Comprehensive Test Suite for EPIC-07 R3")
class CheckinUseCaseTest {

    @Mock
    private JpaCheckinTokenRepository checkinTokenRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaServiceSlotRepository slotRepository;

    @Mock
    private VendorLookupPort vendorLookupPort;

    private QrTokenSigner qrTokenSigner;
    private GenerateCheckinQrUseCase generateCheckinQrUseCase;
    private VerifyCheckinUseCase verifyCheckinUseCase;

    private static final String TEST_SECRET = "danasea-secure-qr-checkin-hmac-sha256-default-key-32bytes-min!";
    private UUID customerId;
    private UUID staffId;
    private UUID vendorId;
    private UUID otherVendorId;
    private UUID orderId;
    private UUID subOrderId;
    private UUID slotId;
    private UUID tokenId;

    @BeforeEach
    void setUp() {
        qrTokenSigner = new QrTokenSigner(TEST_SECRET);
        generateCheckinQrUseCase = new GenerateCheckinQrUseCase(
                checkinTokenRepository, subOrderRepository, masterOrderRepository, slotRepository, qrTokenSigner);
        verifyCheckinUseCase = new VerifyCheckinUseCase(
                checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, slotRepository, null, masterOrderRepository);

        customerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        otherVendorId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        tokenId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("1. Customer QR Code Generation Tests")
    class CustomerQrGenerationTests {

        @Test
        @DisplayName("1.1. Success: Generate signed QR token for CONFIRMED sub-order and save token hash")
        void testGenerateQr_Success() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().plusDays(1));
            slot.setEndTime(LocalTime.of(17, 0));

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
            when(checkinTokenRepository.findFirstBySubOrderIdAndUsedAtIsNullOrderByCreatedAtDesc(subOrderId))
                    .thenReturn(Optional.empty());
            when(checkinTokenRepository.save(any(CheckinTokenJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            GenerateQrResponse response = generateCheckinQrUseCase.execute(subOrderId, customerId);

            assertThat(response).isNotNull();
            assertThat(response.subOrderId()).isEqualTo(subOrderId);
            assertThat(response.qrToken()).contains(subOrderId.toString());
            assertThat(response.expiresAt()).isNotNull();

            // Xác thực CSDL chỉ lưu SHA-256 hash 64 ký tự hex, tuyệt đối không lưu plaintext token
            ArgumentCaptor<CheckinTokenJpaEntity> captor = ArgumentCaptor.forClass(CheckinTokenJpaEntity.class);
            verify(checkinTokenRepository).save(captor.capture());
            CheckinTokenJpaEntity saved = captor.getValue();
            assertThat(saved.getQrTokenHash()).isNotEqualTo(response.qrToken());
            assertThat(saved.getQrTokenHash()).hasSize(64);
            assertThat(saved.getQrTokenHash()).isEqualTo(QrTokenSigner.hashToken(response.qrToken()));
            assertThat(saved.getUsedAt()).isNull();
            assertThat(saved.getUsedByVendorStaffId()).isNull();
        }

        @Test
        @DisplayName("1.2. Rejection (403): Throws UnauthorizedCheckinAccessException when customer does not own the order (IDOR Protection)")
        void testGenerateQr_Throws403_WhenNotOrderOwner() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(UUID.randomUUID()); // Người khác sở hữu

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(UnauthorizedCheckinAccessException.class);

            verify(checkinTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("1.3. Rejection (400): Throws InvalidSubOrderStateException when SubOrder is PENDING")
        void testGenerateQr_Throws400_WhenSubOrderPending() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.PENDING);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(InvalidSubOrderStateException.class);

            verify(checkinTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("1.4. Rejection (400): Throws InvalidSubOrderStateException when SubOrder is CANCELLED or REFUNDED")
        void testGenerateQr_Throws400_WhenSubOrderCancelledOrRefunded() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrderCancelled = new SubOrderJpaEntity();
            subOrderCancelled.setId(subOrderId);
            subOrderCancelled.setMasterOrderId(orderId);
            subOrderCancelled.setStatus(SubOrderStatus.CANCELLED);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrderCancelled));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(InvalidSubOrderStateException.class);

            SubOrderJpaEntity subOrderRefunded = new SubOrderJpaEntity();
            subOrderRefunded.setId(subOrderId);
            subOrderRefunded.setMasterOrderId(orderId);
            subOrderRefunded.setStatus(SubOrderStatus.REFUNDED);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrderRefunded));

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(InvalidSubOrderStateException.class);

            verify(checkinTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("1.5. Rejection (404): Throws OrderNotFoundException when SubOrder does not exist")
        void testGenerateQr_Throws404_WhenSubOrderNotFound() {
            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("1.6. Rejection (400): Throws InvalidCheckinStateException when service slot ended in the past")
        void testGenerateQr_Throws400_WhenSlotAlreadyEnded() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            slot.setId(slotId);
            slot.setDate(LocalDate.now().minusDays(1)); // Slot đã kết thúc ngày hôm qua
            slot.setEndTime(LocalTime.of(10, 0));

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() -> generateCheckinQrUseCase.execute(subOrderId, customerId))
                    .isInstanceOf(InvalidCheckinStateException.class);

            verify(checkinTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("1.7. Zero-Plaintext Audit: Database explicitly verified to never store plaintext raw QR token")
        void testGenerateQr_VerifiesZeroPlaintextInDatabase() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(orderId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(checkinTokenRepository.findFirstBySubOrderIdAndUsedAtIsNullOrderByCreatedAtDesc(subOrderId))
                    .thenReturn(Optional.empty());
            when(checkinTokenRepository.save(any(CheckinTokenJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            GenerateQrResponse response = generateCheckinQrUseCase.execute(subOrderId, customerId);

            ArgumentCaptor<CheckinTokenJpaEntity> captor = ArgumentCaptor.forClass(CheckinTokenJpaEntity.class);
            verify(checkinTokenRepository).save(captor.capture());
            CheckinTokenJpaEntity savedEntity = captor.getValue();

            assertThat(savedEntity.getQrTokenHash()).isNotEqualTo(response.qrToken());
            assertThat(savedEntity.getQrTokenHash()).doesNotContain(":");
            assertThat(savedEntity.getQrTokenHash()).doesNotContain(".");
            assertThat(savedEntity.getQrTokenHash()).matches("^[0-9a-f]{64}$");
        }

        @Test
        @DisplayName("1.8. Success: Generate single master QR pass for an entire master order with multiple sub-orders")
        void testGenerateQr_MasterOrder_Success() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);
            masterOrder.setCustomerId(customerId);

            SubOrderJpaEntity sub1 = new SubOrderJpaEntity();
            sub1.setId(UUID.randomUUID());
            sub1.setMasterOrderId(orderId);
            sub1.setVendorId(vendorId);
            sub1.setStatus(SubOrderStatus.CONFIRMED);

            SubOrderJpaEntity sub2 = new SubOrderJpaEntity();
            sub2.setId(UUID.randomUUID());
            sub2.setMasterOrderId(orderId);
            sub2.setVendorId(otherVendorId);
            sub2.setStatus(SubOrderStatus.CONFIRMED);

            when(subOrderRepository.findById(orderId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(sub1, sub2));
            when(checkinTokenRepository.findFirstBySubOrderIdAndUsedAtIsNullOrderByCreatedAtDesc(sub1.getId()))
                    .thenReturn(Optional.empty());
            when(checkinTokenRepository.save(any(CheckinTokenJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            GenerateQrResponse response = generateCheckinQrUseCase.execute(orderId, customerId);

            assertThat(response).isNotNull();
            assertThat(response.subOrderId()).isEqualTo(orderId);
            assertThat(response.qrToken()).contains(orderId.toString());
            verify(checkinTokenRepository).save(any(CheckinTokenJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("2. Vendor QR Code Verification Tests")
    class VendorQrVerificationTests {

        @Test
        @DisplayName("2.1. Success: Verify valid QR token, mark as used atomically and transition SubOrder to CHECKED_IN")
        void testVerifyCheckin_Success() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(null);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setServiceId(UUID.randomUUID());
            subOrder.setSlotId(slotId);
            subOrder.setQuantity(2);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(checkinTokenRepository.markAsUsedAtomic(eq(tokenId), any(), eq(staffId))).thenReturn(1);

            VerifyCheckinResponse response = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(response).isNotNull();
            assertThat(response.subOrderId()).isEqualTo(subOrderId);
            assertThat(response.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(response.verifiedByStaffId()).isEqualTo(staffId);
            assertThat(response.checkedInAt()).isNotNull();

            verify(subOrderRepository).save(subOrder);
            assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrder.getCheckedInAt()).isNotNull();
        }

        @Test
        @DisplayName("2.2. Rejection (400): Throws InvalidQrTokenException when QR token is null or blank")
        void testVerifyCheckin_Throws400_WhenTokenBlank() {
            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest("   "), staffId))
                    .isInstanceOf(InvalidQrTokenException.class);

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(null), staffId))
                    .isInstanceOf(InvalidQrTokenException.class);
        }

        @Test
        @DisplayName("2.3. Rejection (400): Throws InvalidQrTokenException when QR token is malformed")
        void testVerifyCheckin_Throws400_WhenTokenMalformed() {
            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest("invalid-token-string"), staffId))
                    .isInstanceOf(InvalidQrTokenException.class);

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest("no-colon.signature"), staffId))
                    .isInstanceOf(InvalidQrTokenException.class);
        }

        @Test
        @DisplayName("2.4. Rejection (400): Throws InvalidQrSignatureException when HMAC signature is invalid or tampered")
        void testVerifyCheckin_Throws400_WhenSignatureTampered() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            char lastCharacter = rawToken.charAt(rawToken.length() - 1);
            String tamperedToken = rawToken.substring(0, rawToken.length() - 1)
                    + (lastCharacter == '0' ? '1' : '0');

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(tamperedToken), staffId))
                    .isInstanceOf(InvalidQrSignatureException.class);

            verify(checkinTokenRepository, never()).findByQrTokenHash(any());
        }

        @Test
        @DisplayName("2.5. Rejection (410): Throws QrTokenExpiredException when QR token is expired (now > expiresAt)")
        void testVerifyCheckin_Throws410_WhenTokenExpired() {
            OffsetDateTime pastExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
            String expiredToken = qrTokenSigner.generateToken(subOrderId, pastExpiresAt);

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(expiredToken), staffId))
                    .isInstanceOf(QrTokenExpiredException.class);

            verify(checkinTokenRepository, never()).findByQrTokenHash(any());
        }

        @Test
        @DisplayName("2.6. Rejection (403): Throws UnauthorizedVendorCheckinException when vendor attempts to check in another vendor's sub-order (Vendor Isolation)")
        void testVerifyCheckin_Throws403_WhenVendorMismatch() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(otherVendorId); // Khác Vendor

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(UnauthorizedVendorCheckinException.class);

            verify(checkinTokenRepository, never()).markAsUsedAtomic(any(), any(), any());
        }

        @Test
        @DisplayName("2.7. Rejection (409): Throws QrTokenAlreadyUsedException when ticket was already used")
        void testVerifyCheckin_Throws409_WhenTokenAlreadyUsed() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            OffsetDateTime previousUsedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30);
            UUID previousStaffId = UUID.randomUUID();

            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(previousUsedAt);
            tokenEntity.setUsedByVendorStaffId(previousStaffId);

            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(CheckinAlreadyUsedException.class)
                    .satisfies(ex -> {
                        CheckinAlreadyUsedException usedEx = (CheckinAlreadyUsedException) ex;
                        assertThat(usedEx.getUsedAt()).isEqualTo(previousUsedAt);
                        assertThat(usedEx.getUsedByVendorStaffId()).isEqualTo(previousStaffId);
                    });

            verify(checkinTokenRepository, never()).markAsUsedAtomic(any(), any(), any());
        }

        @Test
        @DisplayName("2.7b. Sequential Double Check-in: First scan succeeds (200), Second scan immediately rejected (409) with original usedAt and staffId")
        void testVerifyCheckin_SequentialDoubleCheckin_FirstSucceedsSecondFailsWith409() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(null);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

            // Lần 1: Cập nhật nguyên tử thành công
            when(checkinTokenRepository.markAsUsedAtomic(eq(tokenId), any(), eq(staffId))).thenAnswer(inv -> {
                tokenEntity.setUsedAt(inv.getArgument(1));
                tokenEntity.setUsedByVendorStaffId(inv.getArgument(2));
                return 1;
            });

            // Thực hiện quét lần 1
            VerifyCheckinResponse firstResponse = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(firstResponse).isNotNull();
            assertThat(firstResponse.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(firstResponse.verifiedByStaffId()).isEqualTo(staffId);
            assertThat(firstResponse.checkedInAt()).isNotNull();

            // Thực hiện quét lần 2 (nhân viên khác hoặc cùng nhân viên quét lại)
            UUID secondStaffId = UUID.randomUUID();

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), secondStaffId))
                    .isInstanceOf(QrTokenAlreadyUsedException.class)
                    .satisfies(ex -> {
                        QrTokenAlreadyUsedException alreadyUsedEx = (QrTokenAlreadyUsedException) ex;
                        assertThat(alreadyUsedEx.getUsedAt()).isEqualTo(firstResponse.checkedInAt());
                        assertThat(alreadyUsedEx.getUsedByVendorStaffId()).isEqualTo(staffId);
                    });

            // Xác nhận subOrderRepository.save chỉ được gọi đúng 1 lần trong suốt quá trình
            verify(subOrderRepository, times(1)).save(subOrder);
        }

        @Test
        @DisplayName("2.8. Rejection (404): Throws CheckinTokenNotFoundException when token hash is not found in database")
        void testVerifyCheckin_Throws404_WhenTokenHashNotFound() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(CheckinTokenNotFoundException.class);
        }

        @Test
        @DisplayName("2.9. Rejection (403): Throws UnauthorizedVendorCheckinException when staff user has no valid vendor profile")
        void testVerifyCheckin_Throws403_WhenNoVendorProfile() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);

            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(UnauthorizedVendorCheckinException.class);
        }

        @Test
        @DisplayName("2.11. Success: Master QR pass scanned by vendor automatically resolves and checks in that vendor's sub-order")
        void testVerifyCheckin_MasterOrder_Success_MatchesVendorSubOrder() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(orderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(orderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(null);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);

            UUID vendorASubOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrderA = new SubOrderJpaEntity();
            subOrderA.setId(vendorASubOrderId);
            subOrderA.setMasterOrderId(orderId);
            subOrderA.setVendorId(vendorId);
            subOrderA.setServiceId(UUID.randomUUID());
            subOrderA.setSlotId(slotId);
            subOrderA.setQuantity(2);
            subOrderA.setStatus(SubOrderStatus.CONFIRMED);

            UUID vendorBSubOrderId = UUID.randomUUID();
            SubOrderJpaEntity subOrderB = new SubOrderJpaEntity();
            subOrderB.setId(vendorBSubOrderId);
            subOrderB.setMasterOrderId(orderId);
            subOrderB.setVendorId(otherVendorId);
            subOrderB.setServiceId(UUID.randomUUID());
            subOrderB.setSlotId(slotId);
            subOrderB.setQuantity(1);
            subOrderB.setStatus(SubOrderStatus.CONFIRMED);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderIdForUpdate(orderId)).thenReturn(List.of(subOrderA, subOrderB));

            VerifyCheckinResponse response = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(response).isNotNull();
            assertThat(response.subOrderId()).isEqualTo(vendorASubOrderId);
            assertThat(response.status()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(subOrderA.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            verify(subOrderRepository).save(subOrderA);
            verify(checkinTokenRepository, never()).markAsUsedAtomic(any(), any(), any());
        }

        @Test
        @DisplayName("2.12. Rejection (403): Master QR pass scanned by vendor with no matching sub-orders throws UnauthorizedVendorCheckinException")
        void testVerifyCheckin_MasterOrder_UnauthorizedVendor_ThrowsException() {
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(orderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(orderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(null);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(orderId);

            SubOrderJpaEntity subOrderOther = new SubOrderJpaEntity();
            subOrderOther.setId(UUID.randomUUID());
            subOrderOther.setMasterOrderId(orderId);
            subOrderOther.setVendorId(otherVendorId);
            subOrderOther.setStatus(SubOrderStatus.CONFIRMED);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findById(orderId)).thenReturn(Optional.of(masterOrder));
            when(subOrderRepository.findByMasterOrderIdForUpdate(orderId)).thenReturn(List.of(subOrderOther));

            assertThatThrownBy(() -> verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId))
                    .isInstanceOf(UnauthorizedVendorCheckinException.class);
        }
    }

    @Nested
    @DisplayName("3. Concurrency Stress Tests (Race Condition)")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("3.1. Concurrency: 10 threads scanning simultaneously -> Exactly 1 succeeds (200), 9 fail (409 Conflict)")
        void testConcurrentCheckin_AtomicLockGuaranteesSingleWinner() throws Exception {
            int threadCount = 10;
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
            String rawToken = qrTokenSigner.generateToken(subOrderId, expiresAt);
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);
            tokenEntity.setExpiresAt(expiresAt);
            tokenEntity.setUsedAt(null);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            when(vendorLookupPort.findVendorIdByUserId(any())).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));

            // Mô phỏng cập nhật nguyên tử tầng cơ sở dữ liệu
            AtomicBoolean claimed = new AtomicBoolean(false);
            when(checkinTokenRepository.markAsUsedAtomic(eq(tokenId), any(), any())).thenAnswer(inv -> {
                if (claimed.compareAndSet(false, true)) {
                    tokenEntity.setUsedAt(inv.getArgument(1));
                    tokenEntity.setUsedByVendorStaffId(inv.getArgument(2));
                    return 1; // 1 dòng được cập nhật
                }
                return 0; // 0 dòng được cập nhật (đã bị luồng khác claim trước)
            });

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            List<Throwable> otherErrors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final UUID threadStaffId = UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Đảm bảo toàn bộ 10 luồng kích hoạt cùng lúc
                        verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), threadStaffId);
                        successCount.incrementAndGet();
                    } catch (QrTokenAlreadyUsedException e) {
                        conflictCount.incrementAndGet();
                    } catch (CheckinAlreadyUsedException e) {
                        conflictCount.incrementAndGet();
                    } catch (Throwable t) {
                        otherErrors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Kích hoạt 10 luồng đồng thời
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(finished).isTrue();
            assertThat(otherErrors).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(9);

            verify(subOrderRepository, times(1)).save(any());
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("7. FIFO Early Dispatch Candidate Calculation")
    class FifoEarlyDispatchCandidates {

        @Test
        @DisplayName("Multiple candidates: Picks earliest available slot and lists all options")
        void testMultipleEarlySlots_PicksEarliest() {
            LocalDate date = LocalDate.now().plusDays(1);
            String rawToken = qrTokenSigner.generateToken(subOrderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);

            UUID serviceId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setServiceId(serviceId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);
            subOrder.setQuantity(2);

            // Current slot: 16:00 -> 17:30
            ServiceSlotJpaEntity currentSlot = new ServiceSlotJpaEntity();
            currentSlot.setId(slotId);
            currentSlot.setServiceId(serviceId);
            currentSlot.setDate(date);
            currentSlot.setStartTime(LocalTime.of(16, 0));
            currentSlot.setEndTime(LocalTime.of(17, 30));
            currentSlot.setStatus(com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN);

            // Candidate 1: 14:00 (cap: 10, booked: 2 -> avail: 8)
            ServiceSlotJpaEntity slot14 = new ServiceSlotJpaEntity();
            slot14.setId(UUID.randomUUID());
            slot14.setServiceId(serviceId);
            slot14.setDate(date);
            slot14.setStartTime(LocalTime.of(14, 0));
            slot14.setEndTime(LocalTime.of(15, 30));
            slot14.setCapacity(10);
            slot14.setBookedCount(2);
            slot14.setStatus(com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN);

            // Candidate 2: 10:00 (cap: 10, booked: 1 -> avail: 9) - EARLIEST!
            ServiceSlotJpaEntity slot10 = new ServiceSlotJpaEntity();
            slot10.setId(UUID.randomUUID());
            slot10.setServiceId(serviceId);
            slot10.setDate(date);
            slot10.setStartTime(LocalTime.of(10, 0));
            slot10.setEndTime(LocalTime.of(11, 30));
            slot10.setCapacity(10);
            slot10.setBookedCount(1);
            slot10.setStatus(com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(currentSlot));
            when(checkinTokenRepository.markAsUsedAtomic(eq(tokenId), any(), eq(staffId))).thenReturn(1);
            when(slotRepository.findByServiceIdAndDateAndStatus(serviceId, date, com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN))
                    .thenReturn(List.of(slot14, slot10));

            var response = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(response.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(response.earlyDispatchSuggestion()).isNotNull();
            assertThat(response.earlyDispatchSuggestion().startTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(response.earlyDispatchSuggestion().availableCapacity()).isEqualTo(9);

            assertThat(response.availableEarlySlots()).hasSize(2);
            assertThat(response.availableEarlySlots().get(0).startTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(response.availableEarlySlots().get(1).startTime()).isEqualTo(LocalTime.of(14, 0));
        }

        @Test
        @DisplayName("Last / only slot of day with no earlier slots: returns null earlyDispatchSuggestion")
        void testNoEarlierSlots_ReturnsNullSuggestion() {
            LocalDate date = LocalDate.now().plusDays(1);
            String rawToken = qrTokenSigner.generateToken(subOrderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
            String tokenHash = QrTokenSigner.hashToken(rawToken);

            CheckinTokenJpaEntity tokenEntity = new CheckinTokenJpaEntity();
            tokenEntity.setId(tokenId);
            tokenEntity.setSubOrderId(subOrderId);
            tokenEntity.setQrTokenHash(tokenHash);

            UUID serviceId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setServiceId(serviceId);
            subOrder.setSlotId(slotId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);
            subOrder.setQuantity(2);

            // Current slot: 08:00 (first slot of day)
            ServiceSlotJpaEntity currentSlot = new ServiceSlotJpaEntity();
            currentSlot.setId(slotId);
            currentSlot.setServiceId(serviceId);
            currentSlot.setDate(date);
            currentSlot.setStartTime(LocalTime.of(8, 0));
            currentSlot.setEndTime(LocalTime.of(9, 30));
            currentSlot.setStatus(com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN);

            when(vendorLookupPort.findVendorIdByUserId(staffId)).thenReturn(Optional.of(vendorId));
            when(checkinTokenRepository.findByQrTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
            when(subOrderRepository.findByIdForUpdate(subOrderId)).thenReturn(Optional.of(subOrder));
            when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(currentSlot));
            when(checkinTokenRepository.markAsUsedAtomic(eq(tokenId), any(), eq(staffId))).thenReturn(1);
            when(slotRepository.findByServiceIdAndDateAndStatus(serviceId, date, com.danasea.backend.modules.service.domain.models.SlotStatus.OPEN))
                    .thenReturn(List.of(currentSlot));

            var response = verifyCheckinUseCase.execute(new VerifyCheckinRequest(rawToken), staffId);

            assertThat(response.getStatus()).isEqualTo(SubOrderStatus.CHECKED_IN);
            assertThat(response.earlyDispatchSuggestion()).isNull();
            assertThat(response.availableEarlySlots()).isNull();
        }
    }
}
