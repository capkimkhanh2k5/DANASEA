package com.danasea.backend.modules.booking.infrastructure.configs;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingUseCase;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.GetBookingDetailUseCase;
import com.danasea.backend.modules.booking.application.usecases.GetCustomerBookingsUseCase;
import com.danasea.backend.modules.booking.application.usecases.GetVendorBookingsUseCase;
import com.danasea.backend.modules.booking.domain.ports.BookingCancellationFinancialPort;
import com.danasea.backend.modules.booking.domain.ports.BookingPaymentStatusPort;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;

@Configuration
public class BookingBeans {

    @Bean
    public CreateBookingHoldUseCase createBookingHoldUseCase(
            BookingRepositoryPort bookingRepository,
            InventoryLockPort inventoryLockPort,
            ServiceSlotPort serviceSlotPort,
            @Value("${app.booking.hold-duration:5m}") Duration holdDuration) {
        return new CreateBookingHoldUseCase(bookingRepository, inventoryLockPort, serviceSlotPort, holdDuration);
    }

    @Bean
    public ConfirmBookingUseCase confirmBookingUseCase(
            BookingRepositoryPort bookingRepository,
            InventoryLockPort inventoryLockPort,
            ServiceSlotPort serviceSlotPort,
            BookingPaymentStatusPort bookingPaymentStatusPort) {
        return new ConfirmBookingUseCase(
                bookingRepository, inventoryLockPort, serviceSlotPort, bookingPaymentStatusPort);
    }

    @Bean
    public CancelBookingHoldUseCase cancelBookingHoldUseCase(
            BookingRepositoryPort bookingRepository,
            InventoryLockPort inventoryLockPort) {
        return new CancelBookingHoldUseCase(bookingRepository, inventoryLockPort);
    }

    @Bean
    public GetBookingDetailUseCase getBookingDetailUseCase(
            BookingRepositoryPort bookingRepository) {
        return new GetBookingDetailUseCase(bookingRepository);
    }

    @Bean
    public GetCustomerBookingsUseCase getCustomerBookingsUseCase(
            BookingRepositoryPort bookingRepository) {
        return new GetCustomerBookingsUseCase(bookingRepository);
    }

    @Bean
    public GetVendorBookingsUseCase getVendorBookingsUseCase(
            BookingRepositoryPort bookingRepository,
            VendorLookupPort vendorLookupPort) {
        return new GetVendorBookingsUseCase(bookingRepository, vendorLookupPort);
    }

    @Bean
    public CancelBookingUseCase cancelBookingUseCase(
            BookingRepositoryPort bookingRepository,
            ServiceSlotPort serviceSlotPort,
            InventoryLockPort inventoryLockPort,
            BookingCancellationFinancialPort financialPort) {
        return new CancelBookingUseCase(
                bookingRepository,
                serviceSlotPort,
                inventoryLockPort,
                financialPort
        );
    }
}
