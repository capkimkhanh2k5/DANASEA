package com.danasea.backend.modules.communication.application.email;

public interface TransactionalEmailSender {
    EmailSendResult send(RenderedEmail email);
}
