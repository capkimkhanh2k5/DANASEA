package com.danasea.backend.modules.order.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWebhookResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayPal Webhook Verification & Processing Tests")
class PayPalWebhookVerificationTest {

    @Mock
    private OrderPaymentService orderPaymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();
    }

    @Test
    @DisplayName("POST /api/payments/webhook/paypal with valid signature processes webhook and returns 200 OK")
    void paypalWebhook_WithValidSignature_ShouldReturnOk() throws Exception {
        String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"id\":\"WH-12345\"}";
        String signature = "valid-transmission-signature";
        UUID paymentId = UUID.randomUUID();

        PaymentWebhookResponse expectedResponse = new PaymentWebhookResponse(
                "WH-12345",
                paymentId,
                PaymentStatus.SUCCESS,
                false
        );

        when(orderPaymentService.processPayPalWebhook(
                eq(payload), eq("trans-123"), eq("2026-10-06T00:00:00Z"), eq(signature), eq("https://cert.url"), eq("SHA256withRSA")))
                .thenReturn(expectedResponse);

        mockMvc.perform(post("/api/payments/webhook/paypal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Paypal-Transmission-Id", "trans-123")
                        .header("Paypal-Transmission-Time", "2026-10-06T00:00:00Z")
                        .header("Paypal-Transmission-Sig", signature)
                        .header("Paypal-Cert-Url", "https://cert.url")
                        .header("Paypal-Auth-Algo", "SHA256withRSA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("WH-12345"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.alreadyProcessed").value(false));

        verify(orderPaymentService).processPayPalWebhook(
                payload, "trans-123", "2026-10-06T00:00:00Z", signature, "https://cert.url", "SHA256withRSA");
    }

    @Test
    @DisplayName("POST /api/payments/webhook/paypal with duplicate transaction returns 200 OK (Idempotent)")
    void paypalWebhook_DuplicateEvent_ShouldReturnAlreadyProcessed() throws Exception {
        String payload = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"id\":\"WH-DUPLICATE\"}";
        String signature = "sig-dup";
        UUID paymentId = UUID.randomUUID();

        PaymentWebhookResponse duplicateResponse = new PaymentWebhookResponse(
                "WH-DUPLICATE",
                paymentId,
                PaymentStatus.SUCCESS,
                true
        );

        when(orderPaymentService.processPayPalWebhook(
                eq(payload), any(), any(), eq(signature), any(), any()))
                .thenReturn(duplicateResponse);

        mockMvc.perform(post("/api/payments/webhook/paypal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Paypal-Transmission-Sig", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("WH-DUPLICATE"))
                .andExpect(jsonPath("$.alreadyProcessed").value(true));
    }

    @Test
    @DisplayName("POST /api/payments/webhook/paypal/refund with valid signature returns 200 OK")
    void paypalRefundWebhook_WithValidSignature_ShouldReturnOk() throws Exception {
        String payload = "{\"event_type\":\"PAYMENT.CAPTURE.REFUNDED\",\"id\":\"REF-123\"}";
        String signature = "sig-refund";
        UUID refundId = UUID.randomUUID();

        RefundWebhookResponse refundResponse = new RefundWebhookResponse(
                "REF-123",
                refundId,
                RefundStatus.PROCESSED,
                false
        );

        when(orderPaymentService.processPayPalRefundWebhook(eq(payload), eq("trans-123"), eq("2026-10-06T00:00:00Z"), eq(signature), eq("https://cert.url"), eq("SHA256withRSA")))
                .thenReturn(refundResponse);

        mockMvc.perform(post("/api/payments/webhook/paypal/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Paypal-Transmission-Sig", signature)
                        .header("Paypal-Transmission-Id", "trans-123")
                        .header("Paypal-Transmission-Time", "2026-10-06T00:00:00Z")
                        .header("Paypal-Cert-Url", "https://cert.url")
                        .header("Paypal-Auth-Algo", "SHA256withRSA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("REF-123"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }
}
