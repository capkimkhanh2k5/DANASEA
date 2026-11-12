package com.danasea.backend.modules.partner.presentation.dtos;

import jakarta.validation.constraints.NotBlank;

public record ResubmitPartnerApplicationRequest(
        @NotBlank(message = "{validation.business_name.required}")
        String businessName,

        @NotBlank(message = "{validation.tax_code.required}")
        String taxCode,

        @NotBlank(message = "{validation.address.required}")
        String address,

        @NotBlank(message = "{validation.bank_account_number.required}")
        String bankAccountNumber,

        @NotBlank(message = "{validation.bank_name.required}")
        String bankName,

        @NotBlank(message = "{validation.bank_account_holder.required}")
        String bankAccountHolder
) {}
