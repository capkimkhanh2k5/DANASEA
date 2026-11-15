package com.danasea.backend.modules.settlement.domain.models;

public enum LineItemExclusionReason {
    /** Không bị loại trừ, được tính toán doanh thu và hoa hồng bình thường */
    NONE,

    /** Đơn hàng đã hoàn tiền 100% (do thời tiết, vendor hủy, khách hủy sớm) */
    FULL_REFUND,

    /** Đơn hàng đang có khiếu nại chưa giải quyết (OPEN hoặc UNDER_REVIEW) - Tạm giữ tiền */
    ACTIVE_DISPUTE,

    /** Đơn hàng chưa hết cửa sổ khiếu nại 7 ngày (D+7) theo CreateDisputeUseCase - Tạm giữ tiền */
    DISPUTE_WINDOW_OPEN,

    /** Đơn hàng đang có yêu cầu hoàn tiền chờ xử lý (PENDING) - Tạm giữ tiền */
    PENDING_REFUND,

    /** Đơn hàng đang có sự cố gián đoạn chờ quyết định (AWAITING_DECISION) - Tạm giữ tiền */
    ACTIVE_DISRUPTION,

    /** Đơn hàng chưa hoàn thành dịch vụ hoặc không hợp lệ */
    NOT_ELIGIBLE;

    public boolean isExcluded() {
        return this != NONE;
    }
}
