package com.danasea.backend.modules.checkin.presentation.dtos;

import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCheckinResponse {

    private UUID subOrderId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID slotId;
    private Integer quantity;
    private SubOrderStatus status;
    private OffsetDateTime checkedInAt;
    private UUID verifiedByStaffId;
    private String message;
    private Boolean isLate;
    private String lateReason;
    private EarlyDispatchSuggestion earlyDispatchSuggestion;
    private java.util.List<EarlyDispatchSuggestion> availableEarlySlots;
    private java.util.List<EligibleSubOrderCandidate> eligibleCandidates;

    public record EarlyDispatchSuggestion(
            boolean available,
            UUID suggestedSlotId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int availableCapacity
    ) {}

    // Record-style accessors for compatibility
    public UUID subOrderId() {
        return subOrderId;
    }

    public UUID vendorId() {
        return vendorId;
    }

    public UUID serviceId() {
        return serviceId;
    }

    public UUID slotId() {
        return slotId;
    }

    public Integer quantity() {
        return quantity;
    }

    public SubOrderStatus status() {
        return status;
    }

    public OffsetDateTime checkedInAt() {
        return checkedInAt;
    }

    public UUID verifiedByStaffId() {
        return verifiedByStaffId;
    }

    public String message() {
        return message;
    }

    public Boolean isLate() {
        return isLate;
    }

    public String lateReason() {
        return lateReason;
    }

    public EarlyDispatchSuggestion earlyDispatchSuggestion() {
        return earlyDispatchSuggestion;
    }

    public java.util.List<EarlyDispatchSuggestion> availableEarlySlots() {
        return availableEarlySlots;
    }

    public java.util.List<EligibleSubOrderCandidate> eligibleCandidates() {
        return eligibleCandidates;
    }
}
