package com.danasea.backend.modules.order.presentation.controllers;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.dtos.GetCustomerOrdersQuery;
import com.danasea.backend.modules.order.application.dtos.GetOrderDetailQuery;
import com.danasea.backend.modules.order.application.dtos.GetOrderReceiptQuery;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.dtos.OrderRefundResult;
import com.danasea.backend.modules.order.application.dtos.RequestRefundCommand;
import com.danasea.backend.modules.order.application.dtos.SubOrderDetailResult;
import com.danasea.backend.modules.order.application.usecases.CreateOrderUseCase;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.application.usecases.GetCustomerOrdersUseCase;
import com.danasea.backend.modules.order.application.usecases.GetOrderDetailUseCase;
import com.danasea.backend.modules.order.application.usecases.GetOrderReceiptUseCase;
import com.danasea.backend.modules.order.application.usecases.RequestRefundUseCase;
import com.danasea.backend.modules.order.domain.models.OrderPagedResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.pdf.PdfReceiptGenerator;
import com.danasea.backend.modules.order.presentation.dtos.CancellationPreviewResponse;
import com.danasea.backend.modules.order.presentation.dtos.CreateOrderRequest;
import com.danasea.backend.modules.order.presentation.dtos.OrderPageResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderReceiptResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundDetailResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundRequest;
import com.danasea.backend.modules.order.presentation.dtos.RefundResponse;
import com.danasea.backend.modules.order.presentation.dtos.SubOrderCancellationPreview;
import com.danasea.backend.modules.order.presentation.dtos.SubOrderResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final GetCustomerOrdersUseCase getCustomerOrdersUseCase;
    private final RequestRefundUseCase requestRefundUseCase;
    private final GetCancellationPreviewUseCase getCancellationPreviewUseCase;
    private final OrderPaymentService orderPaymentService;
    private final GetOrderReceiptUseCase getOrderReceiptUseCase;
    private final PdfReceiptGenerator pdfReceiptGenerator;
    private final LocalizedContentSelector localizedContentSelector;

    @Autowired
    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderDetailUseCase getOrderDetailUseCase,
            GetCustomerOrdersUseCase getCustomerOrdersUseCase,
            RequestRefundUseCase requestRefundUseCase,
            GetCancellationPreviewUseCase getCancellationPreviewUseCase,
            @Autowired(required = false) OrderPaymentService orderPaymentService,
            @Autowired(required = false) GetOrderReceiptUseCase getOrderReceiptUseCase,
            @Autowired(required = false) PdfReceiptGenerator pdfReceiptGenerator,
            @Autowired(required = false) LocalizedContentSelector localizedContentSelector) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderDetailUseCase = getOrderDetailUseCase;
        this.getCustomerOrdersUseCase = getCustomerOrdersUseCase;
        this.requestRefundUseCase = requestRefundUseCase;
        this.getCancellationPreviewUseCase = getCancellationPreviewUseCase;
        this.orderPaymentService = orderPaymentService;
        this.getOrderReceiptUseCase = getOrderReceiptUseCase;
        this.pdfReceiptGenerator = pdfReceiptGenerator != null ? pdfReceiptGenerator : new PdfReceiptGenerator();
        this.localizedContentSelector = localizedContentSelector;
    }

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderDetailUseCase getOrderDetailUseCase,
            GetCustomerOrdersUseCase getCustomerOrdersUseCase,
            RequestRefundUseCase requestRefundUseCase,
            GetCancellationPreviewUseCase getCancellationPreviewUseCase,
            OrderPaymentService orderPaymentService) {
        this(createOrderUseCase, getOrderDetailUseCase, getCustomerOrdersUseCase,
                requestRefundUseCase, getCancellationPreviewUseCase, orderPaymentService, null, null, null);
    }

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderDetailUseCase getOrderDetailUseCase,
            GetCustomerOrdersUseCase getCustomerOrdersUseCase,
            RequestRefundUseCase requestRefundUseCase,
            GetCancellationPreviewUseCase getCancellationPreviewUseCase) {
        this(createOrderUseCase, getOrderDetailUseCase, getCustomerOrdersUseCase,
                requestRefundUseCase, getCancellationPreviewUseCase, null, null, null, null);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = currentUserId();
        MasterOrderDetailResult result = createOrderUseCase.execute(
                new CreateOrderCommand(userId, request.bookingId(), idempotencyKey, request.discountCode()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toOrderResponse(result));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderPageResponse<OrderResponse>> getMyOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        OrderPagedResult<MasterOrderDetailResult> paged = getCustomerOrdersUseCase.execute(
                new GetCustomerOrdersQuery(currentUserId(), page, size));
        List<OrderResponse> content = paged.content().stream()
                .map(this::toOrderResponse)
                .toList();
        return ResponseEntity.ok(new OrderPageResponse<>(
                content,
                paged.page(),
                paged.size(),
                paged.totalElements(),
                paged.totalPages()
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("id") UUID id) {
        MasterOrderDetailResult result = getOrderDetailUseCase.execute(
                new GetOrderDetailQuery(currentUserId(), id, isAdmin()));
        return ResponseEntity.ok(toOrderResponse(result));
    }

    @PostMapping("/{id}/refund-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<RefundResponse>> requestRefund(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) RefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        RefundReason reason = request == null ? RefundReason.CUSTOMER_REQUEST : request.reason();
        RequestRefundCommand command = new RequestRefundCommand(
                currentUserId(),
                id,
                reason,
                idempotencyKey,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
        );
        List<OrderRefundResult> results = requestRefundUseCase.execute(command);
        List<RefundResponse> responses = results.stream()
                .map(r -> new RefundResponse(
                        r.refundId(),
                        r.subOrderId(),
                        r.amount(),
                        r.refundPercentage(),
                        r.reason(),
                        r.status(),
                        OffsetDateTime.now()
                ))
                .toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responses);
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaymentResponse>> getOrderPayments(@PathVariable("id") UUID id) {
        if (orderPaymentService == null) {
            return ResponseEntity.ok(List.of());
        }
        List<PaymentResponse> responses = orderPaymentService.getOrderPayments(currentUserId(), id, isAdmin());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}/refunds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefundDetailResponse>> getOrderRefunds(@PathVariable("id") UUID id) {
        if (orderPaymentService == null) {
            return ResponseEntity.ok(List.of());
        }
        List<RefundDetailResponse> responses = orderPaymentService.getOrderRefunds(currentUserId(), id, isAdmin());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}/cancellation-preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CancellationPreviewResponse> getCancellationPreview(@PathVariable("id") UUID id) {
        CancellationPreviewResult result = getCancellationPreviewUseCase.execute(
                new GetCancellationPreviewQuery(currentUserId(), id, isAdmin()));
        return ResponseEntity.ok(toCancellationPreviewResponse(result));
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getReceipt(
            @PathVariable("id") UUID id,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = "Accept", required = false) String acceptHeader) {
        if (getOrderReceiptUseCase == null) {
            throw new IllegalStateException("GetOrderReceiptUseCase is not configured.");
        }
        OrderReceiptResponse receipt = getOrderReceiptUseCase.execute(
                new GetOrderReceiptQuery(id, currentUserId(), isAdmin()));

        boolean isPdfRequested = "pdf".equalsIgnoreCase(format)
                || (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_PDF_VALUE));

        if (isPdfRequested) {
            byte[] pdfBytes = pdfReceiptGenerator.generate(receipt);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + ".pdf\"")
                    .body(pdfBytes);
        }

        return ResponseEntity.ok(receipt);
    }

    private OrderResponse toOrderResponse(MasterOrderDetailResult r) {
        if (r == null) {
            return null;
        }
        List<SubOrderResponse> items = r.subOrders() == null ? List.of() : r.subOrders().stream()
                .map(this::toSubOrderResponse).toList();
        return new OrderResponse(
                r.id(),
                r.bookingId(),
                r.customerId(),
                r.status(),
                r.totalAmount(),
                r.discountAmount(),
                items,
                r.createdAt(),
                r.createdAt(),
                r.paymentDeadline()
        );
    }

    private SubOrderResponse toSubOrderResponse(SubOrderDetailResult s) {
        String waiverContent = null;
        String waiverLanguage = null;
        boolean fallbackUsed = false;

        if (Boolean.TRUE.equals(s.waiverRequired())) {
            if (localizedContentSelector != null) {
                var sel = localizedContentSelector.selectDetailed(s.waiverContent(), s.waiverContentEn());
                waiverContent = sel.content();
                waiverLanguage = sel.language();
                fallbackUsed = sel.fallbackUsed();
            } else {
                waiverContent = s.waiverContent() != null ? s.waiverContent() : s.waiverContentEn();
                waiverLanguage = "VI";
                fallbackUsed = false;
            }
        }

        return new SubOrderResponse(
                s.id(),
                s.vendorId(),
                s.serviceId(),
                s.slotId(),
                s.quantity(),
                s.unitPrice(),
                s.subtotalAmount(),
                s.status(),
                s.discountAmount(),
                s.finalAmount(),
                s.waiverRequired(),
                s.waiverVersion(),
                waiverContent,
                waiverLanguage,
                fallbackUsed,
                s.waiverAccepted(),
                s.waiverAcceptedAt(),
                s.waiverAcceptedBy(),
                s.waiverAcceptedLanguage(),
                s.cancellationPolicyVersion()
        );
    }

    private CancellationPreviewResponse toCancellationPreviewResponse(CancellationPreviewResult r) {
        if (r == null) {
            return null;
        }
        List<SubOrderCancellationPreview> items = r.items() == null ? List.of() : r.items().stream()
                .map(item -> new SubOrderCancellationPreview(
                        item.subOrderId(),
                        item.serviceId(),
                        item.slotId(),
                        item.departureTime(),
                        item.originalAmount(),
                        item.refundPercentage(),
                        item.refundAmount(),
                        item.policyApplied()
                )).toList();
        return new CancellationPreviewResponse(
                r.orderId(),
                r.originalAmount(),
                r.refundPercentage(),
                r.refundAmount(),
                r.policyApplied(),
                items
        );
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
