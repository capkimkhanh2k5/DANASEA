package com.danasea.backend.modules.booking.domain.models;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.danasea.backend.modules.booking.domain.exceptions.BookingHoldExpiredException;
import com.danasea.backend.modules.booking.domain.exceptions.UnauthorizedBookingAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingDomainTest {

    @Test
    @DisplayName("createHold initializes booking with HOLD status, correct total and expiry")
    void testCreateHoldSuccess() {
        UUID customerId = UUID.randomUUID();
        UUID slotId1 = UUID.randomUUID();
        UUID slotId2 = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        BookingItem item1 = BookingItem.builder()
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(slotId1)
                .quantity(2)
                .price(new BigDecimal("100.00"))
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .build();

        BookingItem item2 = BookingItem.builder()
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(slotId2)
                .quantity(1)
                .price(new BigDecimal("250.00"))
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(14, 0))
                .build();

        Booking booking = Booking.createHold(customerId, List.of(item1, item2), Duration.ofMinutes(15), now);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getCustomerId()).isEqualTo(customerId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HOLD);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(booking.getHoldExpiresAt()).isEqualTo(now.plusMinutes(15));
        assertThat(booking.getItems()).hasSize(2);
        assertThat(booking.getItems().get(0).getBookingId()).isEqualTo(booking.getId());
        assertThat(booking.getItems().get(1).getBookingId()).isEqualTo(booking.getId());
        assertThat(booking.isHold()).isTrue();
        assertThat(booking.isExpired(now)).isFalse();
        assertThat(booking.isExpired(now.plusMinutes(16))).isTrue();
    }

    @Test
    @DisplayName("createHold throws exception if customerId is null or items empty")
    void testCreateHoldValidation() {
        assertThatThrownBy(() -> Booking.createHold(null, List.of(), Duration.ofMinutes(15), OffsetDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Booking.createHold(UUID.randomUUID(), List.of(), Duration.ofMinutes(15), OffsetDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirm transitions status from HOLD to CONFIRMED if not expired")
    void testConfirmSuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        BookingItem item = BookingItem.builder()
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .quantity(1)
                .price(new BigDecimal("100.00"))
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .build();

        Booking booking = Booking.createHold(UUID.randomUUID(), List.of(item), Duration.ofMinutes(15), now);
        booking.confirm(now.plusMinutes(5));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("confirm throws BookingHoldExpiredException if hold is expired")
    void testConfirmExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        BookingItem item = BookingItem.builder()
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .quantity(1)
                .price(new BigDecimal("100.00"))
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .build();

        Booking booking = Booking.createHold(UUID.randomUUID(), List.of(item), Duration.ofMinutes(15), now);

        assertThatThrownBy(() -> booking.confirm(now.plusMinutes(16)))
                .isInstanceOf(BookingHoldExpiredException.class);
    }

    @Test
    @DisplayName("validateOwner throws UnauthorizedBookingAccessException on customer mismatch")
    void testValidateOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .customerId(ownerId)
                .build();

        booking.validateOwner(ownerId); // should pass

        assertThatThrownBy(() -> booking.validateOwner(otherId))
                .isInstanceOf(UnauthorizedBookingAccessException.class);
    }
}
