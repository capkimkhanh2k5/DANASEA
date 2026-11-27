package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Snapshot;
import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.BookingStatusUpdatePort;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.DiscountCodeMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewRequest;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;
import com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HybridFlashSaleStackingTest {

    private MasterOrderRepositoryPort masterOrderRepo;
    private SubOrderRepositoryPort subOrderRepo;
    private BookingLookupPort bookingLookup;
    private BookingStatusUpdatePort bookingStatusUpdate;
    private CommissionPolicyPort commissionPolicy;
    private OrderEventPublisherPort eventPublisher;
    private JpaDiscountCodeRepository discountRepo;
    private JpaDiscountRedemptionRepository redemptionRepo;
    private FlashSaleBookingPort flashSalePort;
    private CreateOrderUseCase createOrderUseCase;
    private DiscountPreviewUseCase discountPreviewUseCase;
    private final SettlementCalculationEngine settlementEngine = new SettlementCalculationEngine();

    private UUID customerId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID slotId;
    private UUID bookingId;
    private UUID bookingItemId;
    private List<SubOrder> lastSavedSubOrders;

    @BeforeEach
    void setUp() {
        masterOrderRepo = mock(MasterOrderRepositoryPort.class);
        subOrderRepo = mock(SubOrderRepositoryPort.class);
        bookingLookup = mock(BookingLookupPort.class);
        bookingStatusUpdate = mock(BookingStatusUpdatePort.class);
        commissionPolicy = mock(CommissionPolicyPort.class);
        eventPublisher = mock(OrderEventPublisherPort.class);
        discountRepo = mock(JpaDiscountCodeRepository.class);
        redemptionRepo = mock(JpaDiscountRedemptionRepository.class);
        flashSalePort = mock(FlashSaleBookingPort.class);

        var allocationEngine = new DiscountAllocationEngine();
        var mapper = new DiscountCodeMapper();

        createOrderUseCase = new CreateOrderUseCase(
                masterOrderRepo,
                subOrderRepo,
                bookingLookup,
                bookingStatusUpdate,
                commissionPolicy,
                eventPublisher,
                discountRepo,
                redemptionRepo,
                allocationEngine,
                mapper
        );
        createOrderUseCase.setFlashSale(flashSalePort);

        discountPreviewUseCase = new DiscountPreviewUseCase(
                bookingLookup,
                discountRepo,
                redemptionRepo,
                commissionPolicy,
                allocationEngine,
                mapper
        );
        discountPreviewUseCase.setFlashSale(flashSalePort);

        customerId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        bookingItemId = UUID.randomUUID();

        when(masterOrderRepo.findByCustomerIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(masterOrderRepo.findByBookingId(any())).thenReturn(Optional.empty());
        when(masterOrderRepo.save(any(MasterOrder.class))).thenAnswer(inv -> {
            MasterOrder mo = inv.getArgument(0);
            if (mo.getId() == null) mo.setId(UUID.randomUUID());
            return mo;
        });
        when(subOrderRepo.saveAll(anyList())).thenAnswer(inv -> {
            lastSavedSubOrders = inv.getArgument(0);
            return lastSavedSubOrders;
        });
        when(commissionPolicy.resolveCommissionRate(any(), any(), any())).thenReturn(new BigDecimal("0.10"));
        when(redemptionRepo.countByDiscountCodeIdAndCustomerId(any(), any())).thenReturn(0L);
        when(discountRepo.incrementUsedCount(any())).thenReturn(1);
    }

    @Test
    void regularOrderWithVendorVoucher_usesK1CommissionBasisAfterVendorDiscount() {
        stubBooking("650000", false, null);
        DiscountCodeJpaEntity vendorCode = vendorCode("SHOP150K", DiscountType.FIXED, "150000", "0");
        when(discountRepo.findByCodeForUpdate("SHOP150K")).thenReturn(Optional.of(vendorCode));

        var result = createOrderUseCase.execute(new CreateOrderCommand(customerId, bookingId, "k1-key", "SHOP150K"));
        var sub = result.subOrders().get(0);
        var savedSub = lastSavedSubOrders.get(0);

        assertThat(result.totalAmount()).isEqualByComparingTo("500000.00");
        assertThat(sub.subtotalAmount()).isEqualByComparingTo("650000.00");
        assertThat(sub.vendorDiscountAmount()).isEqualByComparingTo("150000.00");
        assertThat(sub.platformDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(savedSub.getCommissionBasisAmount()).isEqualByComparingTo("500000.00");
        assertThat(sub.commissionAmount()).isEqualByComparingTo("50000.00");
        assertThat(sub.vendorPayoutAmount()).isEqualByComparingTo("450000.00");
        assertThat(sub.finalAmount()).isEqualByComparingTo("500000.00");
    }

    @Test
    void flashSaleStackedWithVendorVoucher_usesK2AndReconcilesSettlementFullAndPartialRefund() {
        // Original 650k, 10% commission = 65k, Flash Sale subsidy = 50k (booking item price = 600k), Vendor voucher = 150k
        Snapshot snapshot = new Snapshot(
                new BigDecimal("650000.00"), 1, new BigDecimal("650000.00"),
                UUID.randomUUID(), 1, new BigDecimal("0.10"), new BigDecimal("65000.00"),
                new BigDecimal("50000.00"), new BigDecimal("50000.00"),
                new BigDecimal("600000.00"), new BigDecimal("585000.00"), new BigDecimal("15000.00"),
                new BigDecimal("52000.00")
        );
        stubBooking("600000.00", true, snapshot);
        DiscountCodeJpaEntity vendorCode = vendorCode("SHOP150K", DiscountType.FIXED, "150000", "250000");
        when(discountRepo.findByCodeIgnoreCase("SHOP150K")).thenReturn(Optional.of(vendorCode));
        when(discountRepo.findByCodeForUpdate("SHOP150K")).thenReturn(Optional.of(vendorCode));

        var preview = discountPreviewUseCase.execute(customerId, new DiscountPreviewRequest(bookingId, "SHOP150K"));
        assertThat(preview.valid()).isTrue();
        assertThat(preview.totalDiscountAmount()).isEqualByComparingTo("150000.00");
        assertThat(preview.finalPayableAmount()).isEqualByComparingTo("450000.00");

        var result = createOrderUseCase.execute(new CreateOrderCommand(customerId, bookingId, "k2-key", "SHOP150K"));
        var sub = result.subOrders().get(0);
        var savedSub = lastSavedSubOrders.get(0);

        // Customer pays 450k, Platform keeps 65k - 50k = +15k, Vendor receives 650k - 150k - 65k = 435k
        assertThat(result.totalAmount()).isEqualByComparingTo("450000.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("200000.00");
        assertThat(sub.subtotalAmount()).isEqualByComparingTo("650000.00");
        assertThat(savedSub.getCommissionBasisAmount()).isEqualByComparingTo("650000.00");
        assertThat(sub.commissionAmount()).isEqualByComparingTo("65000.00");
        assertThat(sub.vendorDiscountAmount()).isEqualByComparingTo("150000.00");
        assertThat(sub.platformDiscountAmount()).isEqualByComparingTo("50000.00");
        assertThat(sub.vendorPayoutAmount()).isEqualByComparingTo("435000.00");
        assertThat(sub.finalAmount()).isEqualByComparingTo("450000.00");

        // Verify SettlementCalculationEngine (COMPLETED) matches exact K2 payout (435k)
        var completedItem = settlementEngine.calculateLineItem(SubOrderCalculationContext.builder()
                .subOrderId(UUID.randomUUID())
                .status(SubOrderStatus.COMPLETED)
                .subtotalAmount(savedSub.getSubtotalAmount())
                .vendorDiscountAmount(savedSub.getVendorDiscountAmount())
                .platformDiscountAmount(savedSub.getPlatformDiscountAmount())
                .commissionBasisAmount(savedSub.getCommissionBasisAmount())
                .finalAmount(savedSub.getFinalAmount())
                .commissionRate(savedSub.getCommissionRate())
                .build());
        assertThat(completedItem.getGrossAmount()).isEqualByComparingTo("650000.00");
        assertThat(completedItem.getCommissionAmount()).isEqualByComparingTo("65000.00");
        assertThat(completedItem.getNetAmount()).isEqualByComparingTo("435000.00");

        // Verify SettlementCalculationEngine (50% Partial Refund = 225k refunded) preserves exact proportional cash split
        var partialRefundItem = settlementEngine.calculateLineItem(SubOrderCalculationContext.builder()
                .subOrderId(UUID.randomUUID())
                .status(SubOrderStatus.PARTIALLY_REFUNDED)
                .subtotalAmount(savedSub.getSubtotalAmount())
                .vendorDiscountAmount(savedSub.getVendorDiscountAmount())
                .platformDiscountAmount(savedSub.getPlatformDiscountAmount())
                .commissionBasisAmount(savedSub.getCommissionBasisAmount())
                .finalAmount(savedSub.getFinalAmount())
                .refundAmount(new BigDecimal("225000.00"))
                .commissionRate(savedSub.getCommissionRate())
                .build());
        assertThat(partialRefundItem.getGrossAmount()).isEqualByComparingTo("325000.00");
        assertThat(partialRefundItem.getCommissionAmount()).isEqualByComparingTo("32500.00");
        assertThat(partialRefundItem.getNetAmount()).isEqualByComparingTo("217500.00");
    }

    @Test
    void flashSaleStackedWithPlatformPromoVoucher_isBlocked() {
        Snapshot snapshot = sampleFlashSaleSnapshot();
        stubBooking("600000.00", true, snapshot);

        DiscountCodeJpaEntity platformPromo = new DiscountCodeJpaEntity();
        platformPromo.setId(UUID.randomUUID());
        platformPromo.setCode("DANASEA10");
        platformPromo.setScope(DiscountScope.PLATFORM);
        platformPromo.setSponsorType(DiscountSponsorType.PLATFORM);
        platformPromo.setDiscountType(DiscountType.PERCENTAGE);
        platformPromo.setDiscountValue(new BigDecimal("10"));
        platformPromo.setIsActive(true);

        when(discountRepo.findByCodeIgnoreCase("DANASEA10")).thenReturn(Optional.of(platformPromo));
        when(discountRepo.findByCodeForUpdate("DANASEA10")).thenReturn(Optional.of(platformPromo));

        var preview = discountPreviewUseCase.execute(customerId, new DiscountPreviewRequest(bookingId, "DANASEA10"));
        assertThat(preview.valid()).isFalse();
        assertThat(preview.errorCode()).isEqualTo("FLASH_SALE_VOUCHER_STACKING_NOT_ALLOWED");

        assertThatThrownBy(() -> createOrderUseCase.execute(
                new CreateOrderCommand(customerId, bookingId, "block-platform-key", "DANASEA10")))
                .isInstanceOf(FlashSaleException.class)
                .hasMessageContaining("FLASH_SALE_VOUCHER_STACKING_NOT_ALLOWED");
    }

    @Test
    void flashSaleStackedWithVendorFaultCompensationVoucher_isAllowed() {
        Snapshot snapshot = sampleFlashSaleSnapshot();
        stubBooking("600000.00", true, snapshot);

        DiscountCodeJpaEntity compVoucher = new DiscountCodeJpaEntity();
        compVoucher.setId(UUID.randomUUID());
        compVoucher.setCode("VC-COMP63K");
        compVoucher.setScope(DiscountScope.PLATFORM);
        compVoucher.setSponsorType(DiscountSponsorType.PLATFORM);
        compVoucher.setRecipientCustomerId(customerId);
        compVoucher.setCompensationDisruptionId(UUID.randomUUID());
        compVoucher.setDiscountType(DiscountType.FIXED);
        compVoucher.setDiscountValue(new BigDecimal("63000.00"));
        compVoucher.setMinOrderAmount(new BigDecimal("63001.00"));
        compVoucher.setMaxUses(1);
        compVoucher.setMaxUsesPerUser(1);
        compVoucher.setUsedCount(0);
        compVoucher.setIsActive(true);

        when(discountRepo.findByCodeForUpdate("VC-COMP63K")).thenReturn(Optional.of(compVoucher));

        var result = createOrderUseCase.execute(new CreateOrderCommand(customerId, bookingId, "vc-stack-key", "VC-COMP63K"));
        var sub = result.subOrders().get(0);

        assertThat(result.totalAmount()).isEqualByComparingTo("537000.00");
        assertThat(sub.platformDiscountAmount()).isEqualByComparingTo("113000.00");
        assertThat(sub.vendorDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(sub.vendorPayoutAmount()).isEqualByComparingTo("585000.00");
    }

    @Test
    void flashSaleStackedWithExcessiveVendorVoucher_blocksNegativeVendorPayout() {
        Snapshot snapshot = sampleFlashSaleSnapshot(); // vendorReceivable = 585k, customerPayable = 600k
        stubBooking("600000.00", true, snapshot);

        DiscountCodeJpaEntity hugeVendorCode = vendorCode("EXTREME590K", DiscountType.FIXED, "590000", "0");
        when(discountRepo.findByCodeIgnoreCase("EXTREME590K")).thenReturn(Optional.of(hugeVendorCode));
        when(discountRepo.findByCodeForUpdate("EXTREME590K")).thenReturn(Optional.of(hugeVendorCode));

        var preview = discountPreviewUseCase.execute(customerId, new DiscountPreviewRequest(bookingId, "EXTREME590K"));
        assertThat(preview.valid()).isFalse();
        assertThat(preview.errorCode()).isEqualTo("DISCOUNT_EXCEEDS_VENDOR_RECEIVABLE");

        assertThatThrownBy(() -> createOrderUseCase.execute(
                new CreateOrderCommand(customerId, bookingId, "excessive-key", "EXTREME590K")))
                .isInstanceOf(InvalidDiscountException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_EXCEEDS_VENDOR_RECEIVABLE");
    }

    private Snapshot sampleFlashSaleSnapshot() {
        return new Snapshot(
                new BigDecimal("650000.00"), 1, new BigDecimal("650000.00"),
                UUID.randomUUID(), 1, new BigDecimal("0.10"), new BigDecimal("65000.00"),
                new BigDecimal("50000.00"), new BigDecimal("50000.00"),
                new BigDecimal("600000.00"), new BigDecimal("585000.00"), new BigDecimal("15000.00"),
                new BigDecimal("52000.00")
        );
    }

    private void stubBooking(String itemPrice, boolean hasFlashSale, Snapshot snapshot) {
        var price = new BigDecimal(itemPrice);
        var item = new BookingOrderView.BookingItemOrderView(bookingItemId, vendorId, serviceId, slotId, 1, price);
        var view = new BookingOrderView(bookingId, customerId, "HOLD", price, OffsetDateTime.now().plusMinutes(10), List.of(item));
        when(bookingLookup.findBookingForOrder(bookingId)).thenReturn(Optional.of(view));
        when(bookingLookup.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(view));
        when(flashSalePort.hasAllocation(bookingId)).thenReturn(hasFlashSale);
        when(flashSalePort.snapshot(bookingItemId)).thenReturn(Optional.ofNullable(snapshot));
    }

    private DiscountCodeJpaEntity vendorCode(String code, DiscountType type, String value, String minOrder) {
        DiscountCodeJpaEntity entity = new DiscountCodeJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setScope(DiscountScope.VENDOR);
        entity.setSponsorType(DiscountSponsorType.VENDOR);
        entity.setVendorId(vendorId);
        entity.setDiscountType(type);
        entity.setDiscountValue(new BigDecimal(value));
        entity.setMinOrderAmount(new BigDecimal(minOrder));
        entity.setMaxUses(50);
        entity.setUsedCount(0);
        entity.setIsActive(true);
        return entity;
    }
}
