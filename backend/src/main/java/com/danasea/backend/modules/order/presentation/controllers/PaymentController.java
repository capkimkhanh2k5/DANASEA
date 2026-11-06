package com.danasea.backend.modules.order.presentation.controllers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.services.PaymentCoordinator;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWindowResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentStatusResponse;
import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.application.usecases.HandleWebhookUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.presentation.dtos.CreatePaymentIntentRequest;
import com.danasea.backend.modules.order.presentation.dtos.PayPalCaptureRequest;
import com.danasea.backend.modules.order.presentation.dtos.PaymentIntentResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWebhookResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final OrderPaymentService orderPaymentService;
    private final CreatePaymentIntentUseCase createPaymentIntentUseCase;
    private final HandleWebhookUseCase handleWebhookUseCase;
    private PaymentCoordinator paymentCoordinator;

    @Autowired
    public void setPaymentCoordinator(@Autowired(required = false) PaymentCoordinator paymentCoordinator) {
        this.paymentCoordinator = paymentCoordinator;
    }

    @Autowired
    public PaymentController(
            OrderPaymentService orderPaymentService,
            CreatePaymentIntentUseCase createPaymentIntentUseCase,
            @Autowired(required = false) HandleWebhookUseCase handleWebhookUseCase) {
        this.orderPaymentService = orderPaymentService;
        this.createPaymentIntentUseCase = createPaymentIntentUseCase;
        this.handleWebhookUseCase = handleWebhookUseCase;
    }

    public PaymentController(
            OrderPaymentService orderPaymentService,
            CreatePaymentIntentUseCase createPaymentIntentUseCase) {
        this(orderPaymentService, createPaymentIntentUseCase, null);
    }

    public PaymentController(OrderPaymentService orderPaymentService) {
        this(orderPaymentService, null, null);
    }

    @PostMapping("/{orderId}/create-intent")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));

        PaymentIntentResult result = createPaymentIntentUseCase.execute(
                new CreatePaymentIntentCommand(userId, orderId, request.provider(), idempotencyKey));
        String reference = result.paymentUrl() != null && !result.paymentUrl().isBlank()
                ? result.paymentUrl()
                : (result.paymentId() != null ? result.paymentId().toString() : "");
        return ResponseEntity.ok(new PaymentIntentResponse(
                result.paymentId(),
                result.orderId(),
                result.provider(),
                result.amount(),
                PaymentStatus.PENDING,
                reference,
                result.expiresAt() != null ? result.expiresAt() : OffsetDateTime.now()
        ));
    }

    @PostMapping("/{orderId}/start-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentWindowResponse> startPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        if (paymentCoordinator != null) {
            return ResponseEntity.ok(paymentCoordinator.startPayment(userId, orderId, request.provider(), idempotencyKey));
        }
        var res = orderPaymentService.createPaymentIntent(userId, orderId, request.provider(), idempotencyKey);
        return ResponseEntity.ok(new PaymentWindowResponse(
                res.paymentId(), res.orderId(), res.provider(), res.amount(),
                res.status(), null, 1, res.paymentReference(), null,
                res.createdAt(), 300L, null));
    }

    @PostMapping("/{orderId}/switch-provider")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentWindowResponse> switchProvider(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        if (paymentCoordinator != null) {
            return ResponseEntity.ok(paymentCoordinator.switchProvider(userId, orderId, request.provider(), idempotencyKey));
        }
        return startPayment(orderId, request, idempotencyKey);
    }

    @GetMapping("/{orderId}/payment-status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        if (paymentCoordinator != null) {
            return ResponseEntity.ok(paymentCoordinator.getPaymentStatus(userId, orderId));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{orderId}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<PaymentResponse>> getPaymentStatusByOrderId(@PathVariable UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.ok(orderPaymentService.getPaymentsByOrderId(userId, orderId));
    }

    @GetMapping("/{orderId}/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<PaymentResponse>> getOrderPayments(@PathVariable UUID orderId) {
        return getPaymentStatusByOrderId(orderId);
    }

    @GetMapping("/detail/{paymentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> getPaymentDetail(@PathVariable UUID paymentId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.ok(orderPaymentService.getPaymentDetail(userId, paymentId));
    }

    @PostMapping("/paypal/capture")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> capturePayPalOrder(
            @Valid @RequestBody PayPalCaptureRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.ok(orderPaymentService.capturePayPalOrder(
                userId, request.paymentId(), request.paypalOrderId(), idempotencyKey));
    }

    @GetMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> vnpayIpnGet(@RequestParam Map<String, String> queryParams) {
        return ResponseEntity.ok(orderPaymentService.processVNPayIpn(queryParams));
    }

    @PostMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> vnpayIpnPost(@RequestParam Map<String, String> formParams) {
        return ResponseEntity.ok(orderPaymentService.processVNPayIpn(formParams));
    }

    @PostMapping("/webhook/momo")
    public ResponseEntity<PaymentWebhookResponse> momoWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Payment-Signature") String signature) {
        throw new InvalidWebhookException("MoMo webhook processing is not configured.");
    }

    @PostMapping("/webhook/paypal")
    public ResponseEntity<PaymentWebhookResponse> paypalWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Paypal-Transmission-Id", required = false) String transmissionId,
            @RequestHeader(name = "Paypal-Transmission-Time", required = false) String transmissionTime,
            @RequestHeader(name = "Paypal-Transmission-Sig", required = false) String signature,
            @RequestHeader(name = "Paypal-Cert-Url", required = false) String certUrl,
            @RequestHeader(name = "Paypal-Auth-Algo", required = false) String authAlgo) {
        return ResponseEntity.ok(orderPaymentService.processPayPalWebhook(
                payload,
                transmissionId != null ? transmissionId : "",
                transmissionTime != null ? transmissionTime : "",
                signature != null ? signature : "",
                certUrl != null ? certUrl : "",
                authAlgo != null ? authAlgo : ""));
    }

    @PostMapping("/webhook/payos")
    public ResponseEntity<Map<String, Object>> payosWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(name = "x-signature", required = false) String signature) {
        return ResponseEntity.ok(orderPaymentService.processPayOSWebhook(rawPayload, signature));
    }

    @PostMapping("/webhook/zalopay")
    public ResponseEntity<Map<String, Object>> zaloPayWebhook(@RequestBody String rawPayload) {
        return ResponseEntity.ok(orderPaymentService.processZaloPayCallback(rawPayload));
    }

    @PostMapping("/webhook/paypal/refund")
    public ResponseEntity<RefundWebhookResponse> paypalRefundWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Paypal-Transmission-Id", required = false) String transmissionId,
            @RequestHeader(name = "Paypal-Transmission-Time", required = false) String transmissionTime,
            @RequestHeader(name = "Paypal-Transmission-Sig", required = false) String signature,
            @RequestHeader(name = "Paypal-Cert-Url", required = false) String certUrl,
            @RequestHeader(name = "Paypal-Auth-Algo", required = false) String authAlgo) {
        return ResponseEntity.ok(orderPaymentService.processPayPalRefundWebhook(
                payload, transmissionId, transmissionTime, signature, certUrl, authAlgo));
    }
}
