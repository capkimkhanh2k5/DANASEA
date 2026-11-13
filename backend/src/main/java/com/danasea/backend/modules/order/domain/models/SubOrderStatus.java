package com.danasea.backend.modules.order.domain.models;

/**
 * Trạng thái của đơn hàng thành phần (SubOrder).
 *
 * Bổ sung theo EPIC-07 & Phase 2:
 * - PARTIALLY_REFUNDED: Đơn hàng được hoàn tiền một phần (sau khi giải quyết
 * khiếu nại).
 * - CHECKED_IN: Khách hàng đã được nhân viên Vendor quét mã QR check-in tại
 * điểm xuất phát.
 * - IN_PROGRESS: Chuyến đi/dịch vụ đang diễn ra.
 * - NO_SHOW: Khách hàng không có mặt check-in hoặc đến trễ quá hạn mà không
 * được tiếp nhận ngoại lệ.
 */
public enum SubOrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CHECKED_IN,
    IN_PROGRESS,
    NO_SHOW;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED || this == PARTIALLY_REFUNDED
                || this == REJECTED || this == NO_SHOW;
    }

    public boolean canCheckIn() {
        return this == CONFIRMED;
    }

    public boolean canDispute() {
        return this == COMPLETED || this == CONFIRMED || this == CHECKED_IN || this == NO_SHOW;
    }

    public boolean canReview() {
        return this == COMPLETED;
    }
}
