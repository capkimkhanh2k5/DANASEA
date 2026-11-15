package com.danasea.backend.modules.order.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.order.domain.exceptions.SubOrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.VendorSubOrderDetailResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorSubOrderDetailUseCaseTest {

    @Mock
    private JpaSubOrderRepository subOrderRepository;
    @Mock
    private JpaMasterOrderRepository masterOrderRepository;
    @Mock
    private VendorInternalApi vendorInternalApi;
    @Mock
    private AccountInternalApi accountInternalApi;
    @Mock
    private JpaServiceRepository serviceRepository;
    @Mock
    private JpaServiceOptionRepository serviceOptionRepository;
    @Mock
    private JpaBookingItemRepository bookingItemRepository;
    @Mock
    private JpaServiceSlotRepository serviceSlotRepository;

    @InjectMocks
    private GetVendorSubOrderDetailUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private UUID subOrderId;
    private UUID masterOrderId;
    private UUID serviceId;
    private UUID slotId;
    private UUID optionId;
    private UUID bookingItemId;
    private UUID customerId;

    private Vendor vendor;
    private SubOrderJpaEntity subOrder;
    private MasterOrderJpaEntity masterOrder;
    private User customer;
    private ServiceJpaEntity service;
    private ServiceOptionJpaEntity option;
    private BookingItemJpaEntity bookingItem;
    private ServiceSlotJpaEntity slot;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        optionId = UUID.randomUUID();
        bookingItemId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);

        subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setVendorId(vendorId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setBookingItemId(bookingItemId);
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(slotId);
        subOrder.setQuantity(2);
        subOrder.setUnitPrice(new BigDecimal("500000"));
        subOrder.setSubtotalAmount(new BigDecimal("1000000"));
        subOrder.setDiscountAmount(new BigDecimal("100000"));
        subOrder.setVendorDiscountAmount(new BigDecimal("50000"));
        subOrder.setPlatformDiscountAmount(new BigDecimal("50000"));
        subOrder.setFinalAmount(new BigDecimal("900000"));
        subOrder.setCommissionRate(new BigDecimal("0.10"));
        subOrder.setCommissionAmount(new BigDecimal("90000"));
        subOrder.setVendorPayoutAmount(new BigDecimal("810000"));
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setWaiverRequired(true);
        subOrder.setWaiverAccepted(true);
        subOrder.setWaiverAcceptedAt(OffsetDateTime.now());
        subOrder.setCheckedInAt(OffsetDateTime.now());

        masterOrder = new MasterOrderJpaEntity();
        masterOrder.setId(masterOrderId);
        masterOrder.setCustomerId(customerId);
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);

        customer = new User();
        customer.setId(customerId);
        customer.setFullName("Nguyen Van A");
        customer.setPhone("0901234567");
        customer.setEmail("customer@example.com");

        service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setName("Scuba Diving Tour");

        option = new ServiceOptionJpaEntity();
        option.setId(optionId);
        option.setName("VIP Package");

        bookingItem = new BookingItemJpaEntity();
        bookingItem.setId(bookingItemId);
        bookingItem.setOptionId(optionId);
        bookingItem.setBookingDate(LocalDate.of(2026, 11, 10));
        bookingItem.setBookingTime(LocalTime.of(9, 30));

        slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.of(2026, 11, 10));
        slot.setStartTime(LocalTime.of(9, 30));
        slot.setEndTime(LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("execute successfully returns VendorSubOrderDetailResponse when vendor owns suborder")
    void execute_WhenVendorOwnsSubOrder_Success() {
        when(vendorInternalApi.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
        when(accountInternalApi.findUserById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(bookingItemRepository.findById(bookingItemId)).thenReturn(Optional.of(bookingItem));
        when(serviceSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(serviceOptionRepository.findById(optionId)).thenReturn(Optional.of(option));

        VendorSubOrderDetailResponse response = useCase.execute(userId, subOrderId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(subOrderId);
        assertThat(response.getMasterOrderId()).isEqualTo(masterOrderId);
        assertThat(response.getBookingItemId()).isEqualTo(bookingItemId);
        assertThat(response.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        assertThat(response.getMasterOrderStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);

        // Service & Slot
        assertThat(response.getServiceName()).isEqualTo("Scuba Diving Tour");
        assertThat(response.getOptionName()).isEqualTo("VIP Package");
        assertThat(response.getBookingDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(response.getBookingTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.getEndTime()).isEqualTo(LocalTime.of(11, 30));

        // Customer
        assertThat(response.getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(response.getCustomerPhone()).isEqualTo("0901234567");
        assertThat(response.getCustomerEmail()).isEqualTo("customer@example.com");

        // Financials
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getUnitPrice()).isEqualByComparingTo("500000");
        assertThat(response.getSubtotalAmount()).isEqualByComparingTo("1000000");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(response.getVendorPayoutAmount()).isEqualByComparingTo("810000");

        // Operations
        assertThat(response.isCheckedIn()).isTrue();
        assertThat(response.getCheckedInAt()).isNotNull();
        assertThat(response.getWaiverRequired()).isTrue();
        assertThat(response.getWaiverAccepted()).isTrue();
    }

    @Test
    @DisplayName("execute throws SubOrderNotFoundException when suborder belongs to a different vendor")
    void execute_WhenBelongsToOtherVendor_ThrowsSubOrderNotFoundException() {
        UUID otherVendorId = UUID.randomUUID();
        subOrder.setVendorId(otherVendorId);

        when(vendorInternalApi.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

        assertThatThrownBy(() -> useCase.execute(userId, subOrderId))
                .isInstanceOf(SubOrderNotFoundException.class);
    }

    @Test
    @DisplayName("execute throws SubOrderNotFoundException when suborder does not exist")
    void execute_WhenSubOrderNotFound_ThrowsSubOrderNotFoundException() {
        when(vendorInternalApi.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId, subOrderId))
                .isInstanceOf(SubOrderNotFoundException.class);
    }

    @Test
    @DisplayName("execute throws SubOrderNotFoundException when user is not a vendor")
    void execute_WhenUserNotVendor_ThrowsSubOrderNotFoundException() {
        when(vendorInternalApi.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId, subOrderId))
                .isInstanceOf(SubOrderNotFoundException.class);
    }

    @Test
    @DisplayName("execute handles nullable related entities gracefully")
    void execute_WhenRelatedEntitiesAreNull_HandlesGracefully() {
        subOrder.setBookingItemId(null);
        subOrder.setMasterOrderId(null);
        subOrder.setServiceId(null);
        subOrder.setSlotId(null);
        subOrder.setCheckedInAt(null);

        when(vendorInternalApi.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

        VendorSubOrderDetailResponse response = useCase.execute(userId, subOrderId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(subOrderId);
        assertThat(response.getServiceName()).isNull();
        assertThat(response.getOptionName()).isNull();
        assertThat(response.getCustomerName()).isNull();
        assertThat(response.isCheckedIn()).isFalse();
    }
}
