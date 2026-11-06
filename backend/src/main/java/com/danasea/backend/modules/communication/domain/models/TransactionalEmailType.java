package com.danasea.backend.modules.communication.domain.models;

import lombok.Getter;

@Getter
public enum TransactionalEmailType {
    BOOKING_CONFIRMED(TransactionalEmailFamily.BOOKING),
    VENDOR_BOOKING_CONFIRMED(TransactionalEmailFamily.BOOKING),
    TRIP_REMINDER_24H(TransactionalEmailFamily.TRIP),
    TRIP_MEETING_CHANGED(TransactionalEmailFamily.TRIP),
    TRIP_REVIEW_INVITATION(TransactionalEmailFamily.TRIP),
    TRIP_RED_ALERT(TransactionalEmailFamily.DISRUPTION),
    TRIP_DISRUPTION_DECISION(TransactionalEmailFamily.DISRUPTION),
    TRIP_RESCHEDULE_PROPOSED(TransactionalEmailFamily.DISRUPTION),
    TRIP_RESCHEDULED(TransactionalEmailFamily.DISRUPTION),
    TRIP_CANCELLED(TransactionalEmailFamily.DISRUPTION),
    REFUND_REQUESTED(TransactionalEmailFamily.REFUND),
    REFUND_PROCESSED(TransactionalEmailFamily.REFUND),
    REFUND_NEEDS_ATTENTION(TransactionalEmailFamily.REFUND),
    PERSONAL_VOUCHER_ISSUED(TransactionalEmailFamily.VOUCHER),
    DISPUTE_RESOLVED(TransactionalEmailFamily.CASES),
    ACCOUNT_LOCKED(TransactionalEmailFamily.ACCOUNT),
    SECURITY_ALERT(TransactionalEmailFamily.ACCOUNT),
    PASSWORD_CHANGED(TransactionalEmailFamily.ACCOUNT),
    OPS_FINANCIAL_ALERT(TransactionalEmailFamily.OPS),
    OPS_DISRUPTION_ALERT(TransactionalEmailFamily.OPS),
    SETTLEMENT_FINALIZED(TransactionalEmailFamily.SETTLEMENT);

    private final TransactionalEmailFamily family;

    TransactionalEmailType(TransactionalEmailFamily family) {
        this.family = family;
    }

    public boolean isPermittedWhenAccountLocked() {
        return switch (this) {
            case ACCOUNT_LOCKED,
                 SECURITY_ALERT,
                 PASSWORD_CHANGED,
                 TRIP_RED_ALERT,
                 TRIP_DISRUPTION_DECISION,
                 TRIP_RESCHEDULE_PROPOSED,
                 TRIP_RESCHEDULED,
                 TRIP_CANCELLED,
                 REFUND_REQUESTED,
                 REFUND_PROCESSED,
                 REFUND_NEEDS_ATTENTION -> true;
            default -> false;
        };
    }
}
