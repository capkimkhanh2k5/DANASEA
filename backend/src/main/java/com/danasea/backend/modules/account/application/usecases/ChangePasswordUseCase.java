package com.danasea.backend.modules.account.application.usecases;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authorization.domain.exceptions.AccessDeniedException;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ChangePasswordUseCase {
    private final AccountInternalApi accountInternalApi;
    private final PasswordHasher passwordHasher;
    private final AuditLogInternalApi auditLogInternalApi;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    @Transactional
    public void execute(String email, String currentPassword, String newPassword) {
        User user = accountInternalApi.findUserByEmail(email)
                .orElseThrow(() -> new AccessDeniedException());

        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("New password cannot be the same as the current password");
        }

        user.setPasswordHash(passwordHasher.hash(newPassword));
        accountInternalApi.saveUser(user);

        accountInternalApi.revokeAllRefreshTokensByUserId(user.getId());

        auditLogInternalApi.recordAuditLog(
                user.getId(),
                "PASSWORD_CHANGED",
                "USER",
                user.getId(),
                "User changed their own password"
        );

        if (transactionalEmailPort != null) {
            OffsetDateTime now = OffsetDateTime.now();
            String securityEventId = UUID.randomUUID().toString();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.PASSWORD_CHANGED,
                    "security:password-change:" + user.getId() + ":" + securityEventId,
                    "USER:" + user.getId(),
                    now,
                    now,
                    null,
                    1,
                    Map.of(
                            "userId", user.getId().toString(),
                            "changeType", "DIRECT",
                            "timestamp", now.toString(),
                            "securityEventId", securityEventId
                    )
            ));
        }
    }
}
