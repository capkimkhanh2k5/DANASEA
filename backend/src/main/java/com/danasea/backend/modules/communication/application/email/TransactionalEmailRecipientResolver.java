package com.danasea.backend.modules.communication.application.email;

import java.util.Optional;

public interface TransactionalEmailRecipientResolver {
    Optional<ResolvedRecipient> resolve(String recipientKey);
}
