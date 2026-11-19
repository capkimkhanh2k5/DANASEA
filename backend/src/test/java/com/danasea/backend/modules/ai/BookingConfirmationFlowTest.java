package com.danasea.backend.modules.ai;

import com.danasea.backend.modules.ai.application.ports.ConfirmationCardStorePort;
import com.danasea.backend.modules.ai.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Acceptance Criteria & 4-Tier Test Suite for BookingConfirmationFlowTest.
 *
 * <p>Requirements:
 * - R4: Re-validation of price and slot upon booking confirmation without LLM.
 * - R4: Verifies price increases, price decreases, out-of-slot handling, and the 2-retry limit.
 * - R4: Draft Card build on price change (status: alternative_needed).
 * - R4: Maximum 2 auto-retries per conversation. On 3rd attempt, cancel and return error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingConfirmationFlow Acceptance Test Suite (Tiers 1-4)")
public class BookingConfirmationFlowTest {

    @Mock
    private ConfirmationCardStorePort cardStorePort;

    @Mock
    private GetPublicServiceDetailUseCase getServiceDetailUseCase;

    @InjectMocks
    private ConfirmBookingUseCase confirmBookingUseCase;

    private ConfirmationCard pendingCard;
    private final UUID userId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final String sessionId = "session-test-123";

    @BeforeEach
    void setUp() {
        pendingCard = ConfirmationCard.builder()
                .id("card-pending-1")
                .conversationId(conversationId)
                .serviceId(serviceId)
                .price(new BigDecimal("500000.00"))
                .date("2026-09-20T08:00:00")
                .quantity(2)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (>=5 test cases across R4 core requirements)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 - F4.1: Happy Path - Price and slot match, booking is CONFIRMED successfully")
    void testRevalidateBooking_Success_Confirmed() {
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00", "2026-09-20T14:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("success", response.get("status"));
        assertEquals("card-pending-1", response.get("cardId"));
        assertEquals("CONFIRMED", pendingCard.getStatus());
        verify(cardStorePort, times(1)).save(pendingCard);
    }

    @Test
    @DisplayName("Tier 1 - F4.2: Price Increase - Old card cancelled, new card created with higher price")
    void testRevalidateBooking_PriceIncrease_CreatesAlternativeCard() {
        // Price increased from 500,000 to 650,000
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("650000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        assertEquals("CANCELLED", pendingCard.getStatus());
        assertEquals("card-pending-1", response.get("oldCardId"));

        ConfirmationCard newCard = (ConfirmationCard) response.get("newCard");
        assertNotNull(newCard);
        assertNotEquals("card-pending-1", newCard.getId());
        assertEquals(new BigDecimal("650000.00"), newCard.getPrice());
        assertEquals(1, newCard.getRetryCount());
        assertEquals("PENDING", newCard.getStatus());

        // Verify both old card cancellation and new card persistence
        verify(cardStorePort, times(2)).save(any(ConfirmationCard.class));
    }

    @Test
    @DisplayName("Tier 1 - F4.3: Price Decrease - Promotional price applied, new card created with lower price")
    void testRevalidateBooking_PriceDecrease_AppliesPromotionalPrice() {
        // Regular price 500,000, promotional price 400,000
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.00"))
                .promotionalPrice(new BigDecimal("400000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        assertEquals("CANCELLED", pendingCard.getStatus());

        ConfirmationCard newCard = (ConfirmationCard) response.get("newCard");
        assertNotNull(newCard);
        assertEquals(new BigDecimal("400000.00"), newCard.getPrice());
        assertEquals(1, newCard.getRetryCount());
    }

    @Test
    @DisplayName("Tier 1 - F4.4: Out of Slot - Requested slot taken, alternative slot suggested in new card")
    void testRevalidateBooking_OutOfSlot_SuggestsAlternativeSlot() {
        // Requested slot "08:00:00" is gone, only "14:00:00" available
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.00"))
                .availableSlots(List.of("2026-09-20T14:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        assertEquals("CANCELLED", pendingCard.getStatus());

        ConfirmationCard newCard = (ConfirmationCard) response.get("newCard");
        assertNotNull(newCard);
        assertEquals("2026-09-20T14:00:00", newCard.getDate(), "New card should adopt available alternative slot");
    }

    @Test
    @DisplayName("Tier 1 - F4.5: 2-Retry Limit Enforcement - 3rd attempt (retryCount=2) rejected with error")
    void testRevalidateBooking_RetryLimitReached_TerminatesWithError() {
        // Card that already has 2 retries
        pendingCard.setRetryCount(2);

        // Price changes again on 3rd attempt
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("700000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("error", response.get("status"));
        assertTrue(response.get("message").toString().contains("Service details have changed too many times"));
        assertEquals("CANCELLED", pendingCard.getStatus());
        // Verify only old card marked cancelled was saved; NO new card created
        verify(cardStorePort, times(1)).save(pendingCard);
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>=5 test cases)
    // =========================================================================

    @Test
    @DisplayName("Tier 2 - B4.1: Card not found or expired throws IllegalArgumentException")
    void testRevalidateBooking_CardNotFound_ThrowsIllegalArgumentException() {
        when(cardStorePort.findById("invalid-card-id")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            confirmBookingUseCase.execute("invalid-card-id", userId, sessionId);
        });

        assertEquals("Confirmation card not found or expired", ex.getMessage());
        verifyNoInteractions(getServiceDetailUseCase);
    }

    @Test
    @DisplayName("Tier 2 - Card from another conversation is rejected before re-validation")
    void testRevalidateBooking_ForeignConversation_ThrowsAccessDeniedException() {
        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));

        UUID foreignConversationId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () ->
                confirmBookingUseCase.execute("card-pending-1", userId, sessionId, foreignConversationId));
        verifyNoInteractions(getServiceDetailUseCase);
        verify(cardStorePort, never()).save(any());
    }

    @Test
    @DisplayName("Tier 2 - B4.2: Card already CONFIRMED throws IllegalStateException")
    void testRevalidateBooking_CardAlreadyConfirmed_ThrowsIllegalStateException() {
        pendingCard.setStatus("CONFIRMED");
        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            confirmBookingUseCase.execute("card-pending-1", userId, sessionId);
        });

        assertEquals("Card is not in PENDING state", ex.getMessage());
        verifyNoInteractions(getServiceDetailUseCase);
    }

    @Test
    @DisplayName("Tier 2 - B4.3: Card already CANCELLED throws IllegalStateException")
    void testRevalidateBooking_CardAlreadyCancelled_ThrowsIllegalStateException() {
        pendingCard.setStatus("CANCELLED");
        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            confirmBookingUseCase.execute("card-pending-1", userId, sessionId);
        });

        assertEquals("Card is not in PENDING state", ex.getMessage());
    }

    @Test
    @DisplayName("Tier 2 - B4.4: Retry limit boundary: retryCount=1 allows retry and increments to 2")
    void testRevalidateBooking_RetryCount1_AllowedAndIncrementsTo2() {
        pendingCard.setRetryCount(1);

        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("550000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        ConfirmationCard newCard = (ConfirmationCard) response.get("newCard");
        assertEquals(2, newCard.getRetryCount());
    }

    @Test
    @DisplayName("Tier 2 - B4.5: BigDecimal precision equality (500000.00 vs 500000.000)")
    void testRevalidateBooking_BigDecimalPrecisionEquality() {
        pendingCard.setPrice(new BigDecimal("500000.00"));

        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.0000"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        // compareTo returns 0 for equal values regardless of scale
        assertEquals("success", response.get("status"));
        assertEquals("CONFIRMED", pendingCard.getStatus());
    }

    @Test
    @DisplayName("Tier 2 - B4.6: Requested slot no longer in available slots triggers alternative_needed")
    void testRevalidateBooking_RequestedSlotUnavailable_TriggersAlternative() {
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("500000.00"))
                .availableSlots(List.of("2026-09-25T10:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        assertEquals("CANCELLED", pendingCard.getStatus());
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    @DisplayName("Tier 3 - C4.1: Both price and slot change simultaneously in a single confirmation attempt")
    void testRevalidateBooking_SimultaneousPriceAndSlotChange() {
        ServiceDetailResult detailResult = ServiceDetailResult.builder()
                .price(new BigDecimal("600000.00"))
                .availableSlots(List.of("2026-09-20T16:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(detailResult);

        Map<String, Object> response = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);

        assertEquals("alternative_needed", response.get("status"));
        ConfirmationCard newCard = (ConfirmationCard) response.get("newCard");
        assertEquals(new BigDecimal("600000.00"), newCard.getPrice());
        assertEquals("2026-09-20T16:00:00", newCard.getDate());
        assertEquals(pendingCard.getQuantity(), newCard.getQuantity());
        assertEquals(pendingCard.getConversationId(), newCard.getConversationId());
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Tier 4 - R4.1: Full recovery flow: Attempt 1 price changes -> User accepts new card -> Attempt 2 succeeds")
    void testFullRecoveryFlow_UserAcceptsAlternativeAndConfirmsSuccessfully() {
        // Step 1: Initial attempt experiences a price drop (flash sale)
        ServiceDetailResult flashSaleResult = ServiceDetailResult.builder()
                .price(new BigDecimal("450000.00"))
                .availableSlots(List.of("2026-09-20T08:00:00"))
                .build();

        when(cardStorePort.findById("card-pending-1")).thenReturn(Optional.of(pendingCard));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(flashSaleResult);

        Map<String, Object> response1 = confirmBookingUseCase.execute("card-pending-1", userId, sessionId);
        assertEquals("alternative_needed", response1.get("status"));
        assertEquals("PRICE_CHANGED", response1.get("reason"));
        assertEquals(Boolean.TRUE, response1.get("priceChanged"));
        assertEquals(Boolean.FALSE, response1.get("slotChanged"));
        assertEquals(new BigDecimal("500000.00"), response1.get("previousPrice"));
        assertEquals(new BigDecimal("1000000.00"), response1.get("previousTotalAmount"));
        assertEquals(new BigDecimal("450000.00"), response1.get("newPrice"));
        assertEquals(new BigDecimal("900000.00"), response1.get("newTotalAmount"));
        ConfirmationCard card2 = (ConfirmationCard) response1.get("newCard");
        assertNotNull(card2);
        assertEquals(new BigDecimal("450000.00"), card2.getPrice());
        assertEquals(1, card2.getRetryCount());

        // Step 2: User confirms card2 with new price
        when(cardStorePort.findById(card2.getId())).thenReturn(Optional.of(card2));
        when(getServiceDetailUseCase.execute(serviceId, userId, sessionId)).thenReturn(flashSaleResult);

        Map<String, Object> response2 = confirmBookingUseCase.execute(card2.getId(), userId, sessionId);
        assertEquals("success", response2.get("status"));
        assertEquals("CONFIRMED", card2.getStatus());
    }
}
