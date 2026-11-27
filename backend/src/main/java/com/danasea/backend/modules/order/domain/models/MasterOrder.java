package com.danasea.backend.modules.order.domain.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MasterOrder extends BaseDomainModel {

    private UUID bookingId;
    private UUID customerId;
    private String orderCode;
    private MasterOrderStatus status;
    private PaymentOrderStatus paymentStatus;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private UUID discountCodeId;
    private OffsetDateTime paymentDeadline;
    private String idempotencyKey;
    private List<SubOrder> subOrders = new ArrayList<>();

    public MasterOrder() {
        super();
        this.status = MasterOrderStatus.PENDING_PAYMENT;
        this.paymentStatus = PaymentOrderStatus.UNPAID;
        this.discountAmount = BigDecimal.ZERO;
    }

    public static MasterOrder createFromBooking(
            UUID customerId,
            UUID bookingId,
            BigDecimal totalAmount,
            OffsetDateTime paymentDeadline,
            String idempotencyKey) {
        MasterOrder order = new MasterOrder();
        order.setCustomerId(customerId);
        order.setBookingId(bookingId);
        order.setTotalAmount(totalAmount);
        order.setPaymentDeadline(paymentDeadline);
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentOrderStatus.UNPAID);
        order.setDiscountAmount(BigDecimal.ZERO);
        return order;
    }

    /**
     * Kiểm tra tính hợp lệ của việc chuyển đổi trạng thái đơn hàng.
     * Đơn hàng ở trạng thái kết thúc (COMPLETED, CANCELLED) không thể chuyển đổi tiếp.
     * Đơn hàng ở PENDING_PAYMENT chỉ có thể chuyển sang PAID khi thanh toán thành công hoặc CANCELLED khi hết hạn.
     */
    public void assertValidTransition(MasterOrderStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        if (this.status == MasterOrderStatus.COMPLETED || this.status == MasterOrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Cannot transition order from terminal state " + this.status + " to " + newStatus);
        }
        if (this.status == MasterOrderStatus.PENDING_PAYMENT) {
            if (newStatus != MasterOrderStatus.PAID && newStatus != MasterOrderStatus.CANCELLED) {
                throw new InvalidOrderStateException(
                        "Order in PENDING_PAYMENT can only transition to PAID or CANCELLED, requested: " + newStatus);
            }
        }
    }

    /**
     * Xác nhận thanh toán thành công: chuyển trạng thái MasterOrder sang PAID,
     * paymentStatus sang PAID, và toàn bộ SubOrders sang CONFIRMED.
     */
    public void markPaid() {
        assertValidTransition(MasterOrderStatus.PAID);
        this.status = MasterOrderStatus.PAID;
        this.paymentStatus = PaymentOrderStatus.PAID;
        if (this.subOrders != null) {
            for (SubOrder subOrder : this.subOrders) {
                if (subOrder.getStatus() == SubOrderStatus.PENDING) {
                    subOrder.markConfirmed();
                }
            }
        }
    }

    /**
     * Hủy đơn hàng kèm lý do.
     */
    public void cancel(String reason) {
        assertValidTransition(MasterOrderStatus.CANCELLED);
        this.status = MasterOrderStatus.CANCELLED;
        if (this.paymentStatus == null || this.paymentStatus == PaymentOrderStatus.UNPAID) {
            this.paymentStatus = PaymentOrderStatus.UNPAID;
        }
        if (this.subOrders != null) {
            for (SubOrder subOrder : this.subOrders) {
                if (!subOrder.getStatus().isTerminal()) {
                    subOrder.cancel();
                }
            }
        }
    }

    /**
     * Hủy đơn hàng khi hết hạn thời gian thanh toán.
     * Chỉ thực hiện hủy nếu đơn hàng đang ở PENDING_PAYMENT nhằm bảo vệ trạng thái,
     * tránh race condition ghi đè lên đơn hàng đã được webhook xác nhận PAID trước đó.
     */
    public void cancelDueToExpiry() {
        if (this.status == MasterOrderStatus.PENDING_PAYMENT) {
            cancel("Payment hold deadline expired");
        }
    }

    public void addSubOrder(SubOrder subOrder) {
        if (this.subOrders == null) {
            this.subOrders = new ArrayList<>();
        }
        this.subOrders.add(subOrder);
    }
}
