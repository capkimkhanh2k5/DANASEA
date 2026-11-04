package com.danasea.backend.modules.order.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.order.application.usecases.AdminDiscountCodeUseCase;
import com.danasea.backend.modules.order.application.usecases.DiscountPreviewUseCase;
import com.danasea.backend.modules.order.application.usecases.VendorDiscountCodeUseCase;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.presentation.dtos.CreateDiscountCodeRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewResponse;
import com.danasea.backend.modules.order.presentation.dtos.ItemDiscountPreviewResponse;
import com.danasea.backend.security.authorization.domain.models.AuthorizationSubject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("Discount Controllers Unit Tests (Admin, Vendor, Checkout)")
class DiscountControllersTest {

    @Mock
    private AdminDiscountCodeUseCase adminDiscountCodeUseCase;

    @Mock
    private VendorDiscountCodeUseCase vendorDiscountCodeUseCase;

    @Mock
    private DiscountPreviewUseCase discountPreviewUseCase;

    @Mock
    private VendorLookupPort vendorLookupPort;

    private AdminDiscountCodeController adminController;
    private VendorDiscountCodeController vendorController;
    private CheckoutDiscountController checkoutController;

    private final UUID testUserId = UUID.randomUUID();
    private final UUID testVendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminController = new AdminDiscountCodeController(adminDiscountCodeUseCase);
        vendorController = new VendorDiscountCodeController(vendorDiscountCodeUseCase, vendorLookupPort);
        checkoutController = new CheckoutDiscountController(discountPreviewUseCase);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                testUserId.toString(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("ROLE_VENDOR"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        auth.setDetails(new AuthorizationSubject(testUserId, "customer@test.com", java.util.Set.of("ROLE_CUSTOMER"), java.util.Set.of()));
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AdminController: Tạo mã giảm giá thành công")
    void testAdminCreateDiscountCode() {
        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "ADMINPROMO",
                DiscountScope.PLATFORM,
                DiscountSponsorType.PLATFORM,
                null,
                null,
                DiscountType.FIXED,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                null,
                100,
                1,
                null,
                null,
                true
        );

        DiscountCodeResponse expected = new DiscountCodeResponse(
                UUID.randomUUID(), "ADMINPROMO", DiscountScope.PLATFORM, DiscountSponsorType.PLATFORM,
                null, null, DiscountType.FIXED, new BigDecimal("50000.00"), BigDecimal.ZERO, null,
                100, 0, 1, null, null, true, OffsetDateTime.now(), OffsetDateTime.now()
        , null, null, null);

        when(adminDiscountCodeUseCase.createDiscountCode(request)).thenReturn(expected);

        ResponseEntity<DiscountCodeResponse> response = adminController.createDiscountCode(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ADMINPROMO");
    }

    @Test
    @DisplayName("AdminController: Lấy danh sách mã giảm giá phân trang")
    void testAdminGetDiscountCodes() {
        Pageable pageable = PageRequest.of(0, 10);
        DiscountCodeResponse item = new DiscountCodeResponse(
                UUID.randomUUID(), "CODE1", DiscountScope.PLATFORM, DiscountSponsorType.PLATFORM,
                null, null, DiscountType.FIXED, new BigDecimal("10000.00"), BigDecimal.ZERO, null,
                50, 5, 1, null, null, true, OffsetDateTime.now(), OffsetDateTime.now()
        , null, null, null);
        Page<DiscountCodeResponse> page = new PageImpl<>(List.of(item), pageable, 1);

        when(adminDiscountCodeUseCase.getDiscountCodes(null, null, null, null, pageable)).thenReturn(page);

        ResponseEntity<Page<DiscountCodeResponse>> response = adminController.getDiscountCodes(null, null, null, null, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("VendorController: Vendor tạo mã giảm giá cho riêng mình")
    void testVendorCreateDiscountCode() {
        when(vendorLookupPort.findVendorIdByUserId(testUserId)).thenReturn(Optional.of(testVendorId));

        CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                "MYSHOP20",
                DiscountScope.VENDOR,
                DiscountSponsorType.VENDOR,
                null,
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("20.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("50000.00"),
                200,
                2,
                null,
                null,
                true
        );

        DiscountCodeResponse expected = new DiscountCodeResponse(
                UUID.randomUUID(), "MYSHOP20", DiscountScope.VENDOR, DiscountSponsorType.VENDOR,
                testVendorId, null, DiscountType.PERCENTAGE, new BigDecimal("20.00"), new BigDecimal("100000.00"), new BigDecimal("50000.00"),
                200, 0, 2, null, null, true, OffsetDateTime.now(), OffsetDateTime.now()
        , null, null, null);

        when(vendorDiscountCodeUseCase.createVendorDiscountCode(eq(testVendorId), eq(request))).thenReturn(expected);

        ResponseEntity<DiscountCodeResponse> response = vendorController.createVendorDiscountCode(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MYSHOP20");
        assertThat(response.getBody().vendorId()).isEqualTo(testVendorId);
    }

    @Test
    @DisplayName("CheckoutDiscountController: Customer preview discount thành công")
    void testCheckoutPreviewDiscount() {
        UUID bookingId = UUID.randomUUID();
        DiscountPreviewRequest request = new DiscountPreviewRequest(bookingId, "DISCOUNT50");

        DiscountPreviewResponse expected = new DiscountPreviewResponse(
                true,
                null,
                "Áp dụng thành công",
                "DISCOUNT50",
                DiscountScope.PLATFORM,
                DiscountSponsorType.PLATFORM,
                new BigDecimal("500000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("450000.00"),
                List.of(new ItemDiscountPreviewResponse(
                        UUID.randomUUID(), testVendorId, UUID.randomUUID(),
                        new BigDecimal("500000.00"), true, BigDecimal.ZERO,
                        new BigDecimal("50000.00"), new BigDecimal("50000.00"), new BigDecimal("450000.00")
                ))
        );

        when(discountPreviewUseCase.execute(eq(testUserId), eq(request))).thenReturn(expected);

        ResponseEntity<DiscountPreviewResponse> response = checkoutController.previewDiscount(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isTrue();
        assertThat(response.getBody().totalDiscountAmount()).isEqualByComparingTo("50000.00");
        assertThat(response.getBody().finalPayableAmount()).isEqualByComparingTo("450000.00");
    }
}
