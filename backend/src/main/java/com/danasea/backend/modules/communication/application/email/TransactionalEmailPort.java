package com.danasea.backend.modules.communication.application.email;

public interface TransactionalEmailPort {
    void enqueue(EmailRequest request);
}
