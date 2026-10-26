package com.danasea.backend.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import com.danasea.backend.modules.order.application.usecases.DiscountPreviewUseCase;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.DiscountCodeMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewRequest;

class DiscountLocalizationTest {
    private final BookingLookupPort bookings = mock(BookingLookupPort.class);
    private final JpaDiscountCodeRepository codes = mock(JpaDiscountCodeRepository.class);
    private final CommissionPolicyPort commissions = mock(CommissionPolicyPort.class);
    private final DiscountPreviewUseCase preview;

    DiscountLocalizationTest() {
        when(commissions.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
        this.preview = new DiscountPreviewUseCase(bookings, codes,
                mock(JpaDiscountRedemptionRepository.class), commissions, new DiscountAllocationEngine(), new DiscountCodeMapper());
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void missingVoucherResponseUsesRequestedLanguage() {
        UUID customer = UUID.randomUUID();
        UUID booking = seedBooking(customer, OffsetDateTime.now().plusMinutes(10));
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var english = preview.execute(customer, new DiscountPreviewRequest(booking, "MISSING"));
        assertThat(english.errorCode()).isEqualTo("DISCOUNT_NOT_FOUND");
        assertThat(english.message()).isEqualTo("Discount code was not found.");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        var vietnamese = preview.execute(customer, new DiscountPreviewRequest(booking, "MISSING"));
        assertThat(vietnamese.errorCode()).isEqualTo(english.errorCode());
        assertThat(vietnamese.message()).isEqualTo("Không tìm thấy mã giảm giá.");
    }

    @Test
    void expiredBookingIsNotPresentedAsEligibleForDiscount() {
        UUID customer = UUID.randomUUID();
        UUID booking = seedBooking(customer, OffsetDateTime.now().minusMinutes(1));
        assertThat(preview.execute(customer, new DiscountPreviewRequest(booking, "ANY")).errorCode())
                .isEqualTo("BOOKING_NOT_ELIGIBLE_FOR_ORDER");
    }

    @Test
    void zeroPayablePreviewMatchesOrderCreationRejection() {
        UUID customer = UUID.randomUUID();
        UUID booking = seedBooking(customer, OffsetDateTime.now().plusMinutes(10));
        var code = new DiscountCodeJpaEntity();
        code.setId(UUID.randomUUID());
        code.setScope(DiscountScope.PLATFORM);
        code.setDiscountType(DiscountType.FIXED);
        code.setDiscountValue(new BigDecimal("100000"));
        when(codes.findByCodeIgnoreCase("FREE")).thenReturn(Optional.of(code));
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var result = preview.execute(customer, new DiscountPreviewRequest(booking, "FREE"));
        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("DISCOUNT_ZERO_PAYABLE_UNSUPPORTED");
        assertThat(result.message()).isEqualTo("Discount must leave a positive payable amount.");
    }

    private UUID seedBooking(UUID customer, OffsetDateTime deadline) {
        UUID booking = UUID.randomUUID();
        when(bookings.findBookingForOrder(booking)).thenReturn(Optional.of(new BookingOrderView(booking, customer, "HOLD",
                new BigDecimal("100000"), deadline, List.of(new BookingOrderView.BookingItemOrderView(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("100000"))))));
        return booking;
    }
}
