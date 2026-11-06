package com.danasea.backend.modules.communication.application.email;

import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;

public interface TransactionalEmailRenderer {
    RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient);
}
