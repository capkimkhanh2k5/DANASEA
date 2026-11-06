package com.danasea.backend.modules.order.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.order.application.dtos.GetOrderReceiptQuery;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.exceptions.UnpaidOrderReceiptException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.presentation.dtos.OrderReceiptItemResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderReceiptResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;

@Service
public class GetOrderReceiptUseCase {

    private LocalizedContentSelector contentSelector;

    @Autowired
    public void setContentSelector(LocalizedContentSelector contentSelector) {
        this.contentSelector = contentSelector;
    }

    private final MasterOrderRepositoryPort masterOrderRepository;
    private final SubOrderRepositoryPort subOrderRepository;
    private final JpaPaymentRepository paymentRepository;
    private final JpaServiceRepository serviceRepository;

    @Autowired
    public GetOrderReceiptUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            JpaPaymentRepository paymentRepository,
            @Autowired(required = false) JpaServiceRepository serviceRepository) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.paymentRepository = paymentRepository;
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public OrderReceiptResponse execute(GetOrderReceiptQuery query) {
        if (query == null || query.orderId() == null || query.userId() == null) {
            throw new IllegalArgumentException("Order ID and User ID are required.");
        }

        MasterOrder order = masterOrderRepository.findById(query.orderId())
                .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        // Chặn IDOR: Khách hàng chỉ được xem biên nhận đơn của chính mình, ngoại trừ Admin
        if (!query.isAdmin() && !query.userId().equals(order.getCustomerId())) {
            throw new UnauthorizedOrderAccessException(query.orderId(), query.userId());
        }

        List<SubOrder> subOrders = subOrderRepository.findByMasterOrderId(order.getId());
        PaymentJpaEntity payment = paymentRepository.findByMasterOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED)
                .min(Comparator.comparing(p -> !Boolean.TRUE.equals(p.getIsCanonical())))
                .orElseThrow(() -> new UnpaidOrderReceiptException("Receipt requires a successful payment record."));
        if (order.getPaymentStatus() == PaymentOrderStatus.UNPAID) {
            throw new UnpaidOrderReceiptException("Receipt is only available for paid orders.");
        }
        OffsetDateTime paidAt = payment.getPaidAt();
        String paymentProvider = payment.getProvider() == null ? null : payment.getProvider().name();

        String orderCode = "ORD-" + order.getId().toString().substring(0, 8).toUpperCase();
        String receiptCode = "REC-" + order.getId().toString().substring(0, 8).toUpperCase();

        List<OrderReceiptItemResponse> items = subOrders.stream()
                .map(subOrder -> {
                    String serviceName = null;
                    if (serviceRepository != null && subOrder.getServiceId() != null) {
                        serviceName = serviceRepository.findById(subOrder.getServiceId())
                                .map(s -> contentSelector == null ? s.getName() : contentSelector.select(s.getName(), s.getNameEn()))
                                .orElse(null);
                    }
                    if (serviceName == null) {
                        serviceName = "DANASEA service";
                    }

                    BigDecimal subtotal = subOrder.getSubtotalAmount() != null
                            ? subOrder.getSubtotalAmount()
                            : (subOrder.getUnitPrice() != null && subOrder.getQuantity() != null
                                ? subOrder.getUnitPrice().multiply(BigDecimal.valueOf(subOrder.getQuantity()))
                                : BigDecimal.ZERO);

                    BigDecimal discount = subOrder.getDiscountAmount() != null
                            ? subOrder.getDiscountAmount()
                            : BigDecimal.ZERO;

                    BigDecimal finalAmount = subOrder.getFinalAmount() != null
                            ? subOrder.getFinalAmount()
                            : subtotal.subtract(discount).max(BigDecimal.ZERO);

                    return new OrderReceiptItemResponse(
                            subOrder.getId(),
                            subOrder.getServiceId(),
                            serviceName,
                            subOrder.getQuantity(),
                            subOrder.getUnitPrice(),
                            subtotal,
                            discount,
                            finalAmount
                    );
                })
                .toList();

        return new OrderReceiptResponse(
                order.getId(),
                orderCode,
                receiptCode,
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO,
                order.getStatus().name(),
                paidAt,
                paymentProvider,
                items
        );
    }
}
