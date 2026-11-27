package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.danasea.backend.modules.ai.application.ports.ConfirmationCardStorePort;
import com.danasea.backend.modules.ai.application.tools.RequestBookingConfirmationTool;
import com.danasea.backend.modules.ai.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.danasea.backend.modules.ai.domain.models.TravelContext;
import com.danasea.backend.modules.booking.application.dtos.*;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;

class NativeOptionBookingTest {
    private final ConfirmationCardStorePort store = mock(ConfirmationCardStorePort.class);
    private final AiCatalogReadApi catalog = mock(AiCatalogReadApi.class);
    private final CreateBookingHoldUseCase holds = mock(CreateBookingHoldUseCase.class);
    private final CancelBookingHoldUseCase cancel = mock(CancelBookingHoldUseCase.class);
    private final UUID serviceId = UUID.randomUUID(), optionId = UUID.randomUUID(), slotId = UUID.randomUUID(), conversation = UUID.randomUUID(), user = UUID.randomUUID();
    private final LocalDate date = LocalDate.now(TravelContext.ZONE).plusDays(1);
    private final BigDecimal unitPrice = new BigDecimal("500000");
    private ConfirmBookingUseCase confirm;
    private ConfirmationCard card;
    @BeforeEach void setup() {
        var option = new Option(optionId, "Private package", "PER_PACKAGE", unitPrice, 2, new BigDecimal("1000000"), "Guide",
                List.of(new Slot(slotId, date, LocalTime.of(9, 0), LocalTime.of(10, 0), 10)), 2);
        var svc = new PublishedService(serviceId, null, "Kayak", "Trip", null, "Kayak", "kayak", "Da Nang", 16.1, 108.2, 60,
                false, null, 0, List.of(option));
        when(catalog.find(eq(serviceId), any())).thenReturn(svc);
        card = ConfirmationCard.builder().id("card").conversationId(conversation).serviceId(serviceId).optionId(optionId).slotId(slotId)
                .participantsCount(3).quantity(2).participantsPerPackage(List.of(2, 1)).price(unitPrice).date(LocalDateTime.of(date, LocalTime.of(9, 0)).toString())
                .status(ConfirmationCard.STATUS_PENDING).createdAt(LocalDateTime.now()).build();
        confirm = new ConfirmBookingUseCase(store, null, null, holds, cancel, null, catalog);
    }
    private void stored() {
        when(store.findById("card")).thenReturn(Optional.of(card));
        when(store.tryAcquireProcessingLock(anyString(), any())).thenReturn(Optional.of("lock"));
    }
    @Test void confirmationCardUsesLiveOptionPriceAndPackageCountWithoutCreatingHold() throws Exception {
        var tool = new RequestBookingConfirmationTool(new ObjectMapper(), store, null, null, catalog);
        var output = new ObjectMapper().readTree(tool.execute("""
                {"service_id":"%s","option_id":"%s","slot_id":"%s","date":"%s","participants":3}
                """.formatted(serviceId, optionId, slotId, date), conversation));
        assertThat(output.path("quantity").asInt()).isEqualTo(2);
        assertThat(output.path("participants").asInt()).isEqualTo(3);
        assertThat(output.path("partyTotal").decimalValue()).isEqualByComparingTo("1000000");
        verify(store).save(argThat(c -> c.getOptionId().equals(optionId) && c.getParticipantsCount() == 3));
        verifyNoInteractions(holds);
    }
    @Test void customerConfirmationPassesActualOptionAndParticipantsToNativeHold() {
        stored();
        UUID bookingId = UUID.randomUUID();
        when(holds.execute(any())).thenReturn(new BookingHoldResult(bookingId, user, BookingStatus.HOLD, new BigDecimal("1000000"),
                OffsetDateTime.now().plusMinutes(15), List.of(), OffsetDateTime.now()));
        assertThat(confirm.execute("card", user, "session", conversation).get("bookingId")).isEqualTo(bookingId);
        var command = ArgumentCaptor.forClass(CreateBookingHoldCommand.class);
        verify(holds).execute(command.capture());
        assertThat(command.getValue().items()).containsExactly(new BookingHoldItemDto(slotId, 1, optionId, 2), new BookingHoldItemDto(slotId, 1, optionId, 1));
        verifyNoInteractions(cancel);
    }
    @Test void priceRaceCompensatesHoldAndRequiresNewCustomerConfirmation() {
        stored(); UUID bookingId = UUID.randomUUID();
        when(holds.execute(any())).thenReturn(new BookingHoldResult(bookingId, user, BookingStatus.HOLD, new BigDecimal("1100000"),
                OffsetDateTime.now().plusMinutes(15), List.of(), OffsetDateTime.now()));
        assertThat(confirm.execute("card", user, "session", conversation).get("status")).isEqualTo("refresh_required");
        verify(cancel).execute(new CancelBookingHoldCommand(bookingId, user));
        assertThat(card.getStatus()).isEqualTo(ConfirmationCard.STATUS_CANCELLED);
    }
    @Test void successfulConfirmationRetryReturnsStoredHoldAndNeverCreatesAnotherHold() {
        stored(); UUID bookingId = UUID.randomUUID();
        when(holds.execute(any())).thenReturn(new BookingHoldResult(bookingId, user, BookingStatus.HOLD, new BigDecimal("1000000"),
                OffsetDateTime.now().plusMinutes(15), List.of(), OffsetDateTime.now()));
        var first = confirm.execute("card", user, "session", conversation);
        var retry = confirm.execute("card", user, "session", conversation);
        assertThat(retry).isEqualTo(first);
        verify(holds, times(1)).execute(any());
        assertThatThrownBy(() -> confirm.execute("card", UUID.randomUUID(), "session", conversation))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test void legacyCardWithoutOptionCannotHoldInventoryInProductionPath() {
        stored(); card.setOptionId(null);
        assertThat(confirm.execute("card", user, "session", conversation).get("status")).isEqualTo("refresh_required");
        verifyNoInteractions(holds); verify(catalog, never()).find(any(), any());
    }
}
