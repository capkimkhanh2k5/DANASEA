package com.danasea.backend.modules.partner.presentation.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminRejectApplicationRequest {

    @NotBlank(message = "{validation.rejection_reason.required}")
    @JsonAlias("reason")
    private String rejectionReason;

    public String getReason() {
        return rejectionReason;
    }

    public void setReason(String reason) {
        this.rejectionReason = reason;
    }
}
