package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.checkin.application.usecases.GenerateCheckinQrUseCase;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailRenderer implements TransactionalEmailRenderer {

    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final TransactionalEmailProperties properties;

    @Autowired
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired(required = false)
    private JpaUserRepository userRepository;

    @Autowired(required = false)
    private GenerateCheckinQrUseCase generateCheckinQrUseCase;

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.BOOKING_CONFIRMED
                || type == TransactionalEmailType.VENDOR_BOOKING_CONFIRMED;
    }

    @Override
    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        TransactionalEmailType type = entity.getType();
        UUID orderId = extractOrderId(entity.getBusinessKey());

        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("MasterOrder not found for id: " + orderId));

        List<SubOrderJpaEntity> allSubOrders = subOrderRepository.findByMasterOrderId(orderId);
        SupportedLanguage language = SupportedLanguage.fromTag(recipient.locale()).orElse(SupportedLanguage.VI);

        if (type == TransactionalEmailType.BOOKING_CONFIRMED) {
            return renderCustomerBookingConfirmation(masterOrder, allSubOrders, recipient, language);
        } else if (type == TransactionalEmailType.VENDOR_BOOKING_CONFIRMED) {
            UUID vendorId = extractVendorId(entity.getBusinessKey());
            return renderVendorBookingNotification(masterOrder, allSubOrders, vendorId, recipient, language);
        }

        throw new IllegalArgumentException("Unsupported type for BookingEmailRenderer: " + type);
    }

    private RenderedEmail renderCustomerBookingConfirmation(
            MasterOrderJpaEntity masterOrder,
            List<SubOrderJpaEntity> subOrders,
            ResolvedRecipient recipient,
            SupportedLanguage language
    ) {
        // 1. Check if all sub-orders are cancelled or refunded
        boolean anyValid = subOrders.stream().anyMatch(this::isValidActiveSubOrder);
        if (!anyValid) {
            throw new SupersededEmailException(
                    "SUPERSEDED_BY_CANCELLATION",
                    "All sub-orders for order " + masterOrder.getId() + " have been cancelled or refunded"
            );
        }

        // 2. Check if partially confirmed
        boolean isPartial = subOrders.stream().anyMatch(so -> !isValidActiveSubOrder(so));

        String orderCode = "ORD-" + masterOrder.getId().toString().substring(0, 8).toUpperCase();
        String subjectKey = isPartial ? "email.booking.partial.subject" : "email.booking.confirmed.subject";
        String subject = messages.get(subjectKey, language, orderCode);

        // Fetch refunds for this order
        List<UUID> subOrderIds = subOrders.stream().map(SubOrderJpaEntity::getId).toList();
        List<RefundJpaEntity> refunds = refundRepository.findBySubOrderIdIn(subOrderIds);
        Map<UUID, List<RefundJpaEntity>> refundMap = refunds.stream()
                .filter(r -> r.getSubOrderId() != null)
                .collect(Collectors.groupingBy(RefundJpaEntity::getSubOrderId));

        StringBuilder sb = new StringBuilder();
        String customerName = recipient.email();
        String customerPhone = null;
        if (userRepository != null && masterOrder.getCustomerId() != null) {
            try {
                var userOpt = userRepository.findById(masterOrder.getCustomerId());
                if (userOpt.isPresent()) {
                    if (userOpt.get().getFullName() != null && !userOpt.get().getFullName().isBlank()) {
                        customerName = userOpt.get().getFullName();
                    }
                    customerPhone = userOpt.get().getPhone();
                }
            } catch (Exception ignored) {
            }
        }

        sb.append(messages.get("email.booking.greeting", language, customerName)).append("\n\n");

        String introKey = isPartial ? "email.booking.partial.intro" : "email.booking.confirmed.intro";
        sb.append(messages.get(introKey, language, orderCode)).append("\n\n");
        sb.append("--------------------------------------------------\n");

        BigDecimal totalSubtotal = BigDecimal.ZERO;
        BigDecimal totalRefundPending = BigDecimal.ZERO;
        BigDecimal totalRefundProcessed = BigDecimal.ZERO;
        List<TransactionalEmailHtmlLayout.TicketItemView> ticketItems = new ArrayList<>();
        String primaryDepartureTime = null;
        UUID primaryConfirmedSubOrderId = null;

        for (SubOrderJpaEntity so : subOrders) {
            ServiceJpaEntity svc = so.getServiceId() != null
                    ? serviceRepository.findById(so.getServiceId()).orElse(null)
                    : null;
            ServiceSlotJpaEntity slot = so.getSlotId() != null
                    ? serviceSlotRepository.findById(so.getSlotId()).orElse(null)
                    : null;

            String svcName = svc != null
                    ? (language == SupportedLanguage.EN && svc.getNameEn() != null ? svc.getNameEn() : svc.getName())
                    : "DANASEA Service";

            String locationName = svc != null && svc.getLocationName() != null && !svc.getLocationName().isBlank()
                    ? svc.getLocationName()
                    : (svc != null && svc.getAddress() != null ? svc.getAddress() : "Đà Nẵng");
            String address = svc != null && svc.getAddress() != null ? svc.getAddress() : locationName;

            Double lat = (svc != null && svc.getLatitude() != null) ? svc.getLatitude().doubleValue() : null;
            Double lng = (svc != null && svc.getLongitude() != null) ? svc.getLongitude().doubleValue() : null;
            String googleMapsUrl = TransactionalEmailHtmlLayout.buildGoogleMapsUrl(lat, lng, address);

            String departureTime = slot != null && slot.getDate() != null && slot.getStartTime() != null
                    ? slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + " "
                    + slot.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Đang cập nhật";

            if (primaryDepartureTime == null && isValidActiveSubOrder(so)) {
                primaryDepartureTime = departureTime;
                primaryConfirmedSubOrderId = so.getId();
            }

            BigDecimal subtotal = so.getSubtotalAmount() != null
                    ? so.getSubtotalAmount()
                    : (so.getUnitPrice() != null && so.getQuantity() != null
                    ? so.getUnitPrice().multiply(BigDecimal.valueOf(so.getQuantity()))
                    : BigDecimal.ZERO);
            totalSubtotal = totalSubtotal.add(subtotal);

            // Determine status text
            List<RefundJpaEntity> itemRefunds = refundMap.getOrDefault(so.getId(), List.of());
            String statusText = resolveSubOrderStatusText(so.getStatus(), itemRefunds, language);
            String cleanStatusLabel = statusText.replace("[", "").replace("]", "");

            for (RefundJpaEntity ref : itemRefunds) {
                if (ref.getStatus() == RefundStatus.PROCESSED) {
                    totalRefundProcessed = totalRefundProcessed.add(ref.getAmount() != null ? ref.getAmount() : BigDecimal.ZERO);
                } else if (ref.getStatus() == RefundStatus.PENDING) {
                    totalRefundPending = totalRefundPending.add(ref.getAmount() != null ? ref.getAmount() : BigDecimal.ZERO);
                }
            }

            String subCode = "SUB-" + so.getId().toString().substring(0, 6).toUpperCase();
            String qtyLabel = (so.getQuantity() != null ? so.getQuantity() : 1)
                    + (language == SupportedLanguage.EN ? " pax" : " vé");

            ticketItems.add(new TransactionalEmailHtmlLayout.TicketItemView(
                    svcName,
                    subCode,
                    qtyLabel,
                    cleanStatusLabel,
                    isValidActiveSubOrder(so),
                    departureTime,
                    locationName,
                    address,
                    googleMapsUrl,
                    formatCurrency(subtotal) + " đ"
            ));

            sb.append("• ").append(svcName).append(" - ").append(statusText).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.departure", language, departureTime)).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.location", language, address)).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.quantity", language, so.getQuantity())).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.subtotal", language, formatCurrency(subtotal))).append("\n\n");
        }

        sb.append("--------------------------------------------------\n");
        sb.append(messages.get("email.booking.summary.title", language)).append(":\n");
        sb.append("  ").append(messages.get("email.booking.summary.subtotal", language, formatCurrency(totalSubtotal))).append("\n");

        BigDecimal discount = masterOrder.getDiscountAmount() != null ? masterOrder.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("  ").append(messages.get("email.booking.summary.discount", language, formatCurrency(discount))).append("\n");
        }

        BigDecimal totalPaid = masterOrder.getTotalAmount() != null ? masterOrder.getTotalAmount() : BigDecimal.ZERO;
        sb.append("  ").append(messages.get("email.booking.summary.paid", language, formatCurrency(totalPaid))).append("\n");

        if (totalRefundPending.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("  ").append(messages.get("email.booking.summary.refund_pending", language, formatCurrency(totalRefundPending))).append("\n");
        }
        if (totalRefundProcessed.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("  ").append(messages.get("email.booking.summary.refund_processed", language, formatCurrency(totalRefundProcessed))).append("\n");
        }

        sb.append("\n");
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders?orderId=" + masterOrder.getId();
        sb.append(messages.get("email.booking.cta.customer", language, ctaUrl)).append("\n");
        sb.append(messages.get("email.booking.cta.customer_note", language)).append("\n\n");
        sb.append("---\n");
        sb.append(messages.get("email.common.footer", language));

        String qrData = orderCode;
        if (generateCheckinQrUseCase != null && primaryConfirmedSubOrderId != null && masterOrder.getCustomerId() != null) {
            try {
                var qrResp = generateCheckinQrUseCase.execute(primaryConfirmedSubOrderId, masterOrder.getCustomerId());
                if (qrResp != null && qrResp.qrToken() != null && !qrResp.qrToken().isBlank()) {
                    qrData = qrResp.qrToken();
                }
            } catch (Exception ignored) {
            }
        }

        String confirmedAtText = masterOrder.getCreatedAt() != null
                ? masterOrder.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))
                : ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));

        String textBody = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.buildBookingTicketHtml(
                new TransactionalEmailHtmlLayout.BookingTicketView(
                        customerName,
                        customerPhone,
                        recipient.email(),
                        orderCode,
                        confirmedAtText,
                        isPartial,
                        qrData,
                        primaryDepartureTime,
                        ticketItems,
                        formatCurrency(totalSubtotal) + " đ",
                        discount.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(discount) + " đ" : null,
                        formatCurrency(totalPaid) + " đ",
                        totalRefundPending.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(totalRefundPending) + " đ" : null,
                        totalRefundProcessed.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(totalRefundProcessed) + " đ" : null,
                        ctaUrl,
                        properties.getSupportPhone(),
                        language
                )
        );

        return RenderedEmail.builder()
                .to(recipient.email())
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    private RenderedEmail renderVendorBookingNotification(
            MasterOrderJpaEntity masterOrder,
            List<SubOrderJpaEntity> allSubOrders,
            UUID vendorId,
            ResolvedRecipient recipient,
            SupportedLanguage language
    ) {
        List<SubOrderJpaEntity> vendorSubOrders = allSubOrders.stream()
                .filter(so -> vendorId.equals(so.getVendorId()))
                .filter(this::isValidActiveSubOrder)
                .toList();

        if (vendorSubOrders.isEmpty()) {
            throw new SupersededEmailException(
                    "SUPERSEDED_BY_CANCELLATION",
                    "All sub-orders for vendor " + vendorId + " in order " + masterOrder.getId() + " have been cancelled or refunded"
            );
        }

        String orderCode = "ORD-" + masterOrder.getId().toString().substring(0, 8).toUpperCase();
        String subject = messages.get("email.booking.vendor.subject", language, orderCode);

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.booking.greeting", language, recipient.email())).append("\n\n");
        sb.append(messages.get("email.booking.vendor.intro", language, orderCode)).append("\n\n");
        sb.append("--------------------------------------------------\n");

        for (SubOrderJpaEntity so : vendorSubOrders) {
            ServiceJpaEntity svc = so.getServiceId() != null
                    ? serviceRepository.findById(so.getServiceId()).orElse(null)
                    : null;
            ServiceSlotJpaEntity slot = so.getSlotId() != null
                    ? serviceSlotRepository.findById(so.getSlotId()).orElse(null)
                    : null;

            String svcName = svc != null
                    ? (language == SupportedLanguage.EN && svc.getNameEn() != null ? svc.getNameEn() : svc.getName())
                    : "DANASEA Service";

            String departureTime = slot != null && slot.getDate() != null && slot.getStartTime() != null
                    ? slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) + " "
                    + slot.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "Đang cập nhật";

            BigDecimal subtotal = so.getSubtotalAmount() != null
                    ? so.getSubtotalAmount()
                    : (so.getUnitPrice() != null && so.getQuantity() != null
                    ? so.getUnitPrice().multiply(BigDecimal.valueOf(so.getQuantity()))
                    : BigDecimal.ZERO);

            sb.append("• ").append(svcName).append(" - [").append(messages.get("email.booking.status.confirmed", language)).append("]\n");
            sb.append("  ").append(messages.get("email.booking.label.departure", language, departureTime)).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.quantity", language, so.getQuantity())).append("\n");
            sb.append("  ").append(messages.get("email.booking.label.subtotal", language, formatCurrency(subtotal))).append("\n\n");
        }

        sb.append("--------------------------------------------------\n");
        String ctaUrl = properties.getPublicBaseUrl() + "/vendor/orders";
        sb.append(messages.get("email.booking.cta.vendor", language, ctaUrl)).append("\n\n");
        sb.append("---\n");
        sb.append(messages.get("email.common.footer", language));

        String textBody = sb.toString();
        String badgeLabel = language == SupportedLanguage.EN ? "New Booking" : "Đơn đặt mới";
        String ctaLabel = language == SupportedLanguage.EN ? "Manage Vendor Orders" : "Quản lý đơn hàng Đối tác";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                badgeLabel,
                TransactionalEmailHtmlLayout.Theme.OCEAN,
                textBody,
                ctaLabel,
                ctaUrl,
                properties.getSupportPhone(),
                language
        );

        return RenderedEmail.builder()
                .to(recipient.email())
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    private boolean isValidActiveSubOrder(SubOrderJpaEntity so) {
        if (so == null || so.getStatus() == null) {
            return false;
        }
        SubOrderStatus st = so.getStatus();
        return st == SubOrderStatus.CONFIRMED
                || st == SubOrderStatus.CHECKED_IN
                || st == SubOrderStatus.IN_PROGRESS
                || st == SubOrderStatus.COMPLETED;
    }

    private String resolveSubOrderStatusText(
            SubOrderStatus status,
            List<RefundJpaEntity> itemRefunds,
            SupportedLanguage language
    ) {
        if (status == SubOrderStatus.CONFIRMED) {
            return "[" + messages.get("email.booking.status.confirmed", language) + "]";
        }
        if (status == SubOrderStatus.CHECKED_IN) {
            return "[" + messages.get("email.booking.status.checked_in", language) + "]";
        }
        if (status == SubOrderStatus.IN_PROGRESS) {
            return "[" + messages.get("email.booking.status.in_progress", language) + "]";
        }
        if (status == SubOrderStatus.COMPLETED) {
            return "[" + messages.get("email.booking.status.completed", language) + "]";
        }

        // Cancelled / Refunded / Rejected
        boolean hasProcessedRefund = itemRefunds.stream().anyMatch(r -> r.getStatus() == RefundStatus.PROCESSED);
        if (hasProcessedRefund) {
            return "[" + messages.get("email.booking.status.refund_processed", language) + "]";
        }

        boolean hasPendingRefund = itemRefunds.stream().anyMatch(r -> r.getStatus() == RefundStatus.PENDING);
        if (hasPendingRefund) {
            return "[" + messages.get("email.booking.status.refund_pending", language) + "]";
        }

        return "[" + messages.get("email.booking.status.cancelled", language) + "]";
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(amount);
    }

    private UUID extractOrderId(String businessKey) {
        // Format: order:<orderId> or order:<orderId>:vendor:<vendorId>
        String[] parts = businessKey.split(":");
        if (parts.length >= 2 && "order".equalsIgnoreCase(parts[0])) {
            return UUID.fromString(parts[1]);
        }
        throw new IllegalArgumentException("Cannot extract orderId from businessKey: " + businessKey);
    }

    private UUID extractVendorId(String businessKey) {
        // Format: order:<orderId>:vendor:<vendorId>
        String[] parts = businessKey.split(":");
        if (parts.length >= 4 && "vendor".equalsIgnoreCase(parts[2])) {
            return UUID.fromString(parts[3]);
        }
        throw new IllegalArgumentException("Cannot extract vendorId from businessKey: " + businessKey);
    }
}
