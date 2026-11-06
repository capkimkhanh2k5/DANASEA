package com.danasea.backend.modules.communication.infrastructure.email;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRecipientResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountRecipientResolver implements TransactionalEmailRecipientResolver {

    private final AccountInternalApi accountInternalApi;
    private final TransactionalEmailProperties properties;

    @Override
    public Optional<ResolvedRecipient> resolve(String recipientKey) {
        if (recipientKey == null || recipientKey.isBlank()) {
            return Optional.empty();
        }
        if (recipientKey.startsWith("USER:")) {
            String idStr = recipientKey.substring("USER:".length()).trim();
            try {
                UUID userId = UUID.fromString(idStr);
                return accountInternalApi.findUserById(userId)
                        .map(user -> new ResolvedRecipient(
                                user.getEmail(),
                                user.getLocale() != null ? user.getLocale() : "vi",
                                Boolean.TRUE.equals(user.getIsEmailVerified()),
                                Boolean.TRUE.equals(user.getIsLocked())
                        ));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in recipientKey: {}", recipientKey);
                return Optional.empty();
            }
        }
        if (recipientKey.startsWith("OPS:")) {
            String function = recipientKey.substring("OPS:".length()).trim().toLowerCase();
            String email = properties.getOpsEmail(function);
            if (email == null || email.isBlank()) {
                log.warn("Unconfigured ops email for function: {}. Email will be skipped.", function);
                return Optional.empty();
            }
            return Optional.of(new ResolvedRecipient(email, "vi", true, false));
        }
        return Optional.empty();
    }
}
