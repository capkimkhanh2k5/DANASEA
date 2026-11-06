package com.danasea.backend.modules.admin.application.usecases;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.domain.exceptions.SelfLockNotAllowedException;
import com.danasea.backend.modules.admin.domain.exceptions.UserAlreadyLockedException;
import com.danasea.backend.modules.admin.domain.exceptions.UserNotFoundException;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LockUserUseCase {

    private final AccountInternalApi accountInternalApi;
    private final AuditLogInternalApi auditLogInternalApi;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    @Transactional
    public void execute(UUID actorId, UUID targetUserId, String reason) {
        if (actorId == null) {
            throw new IllegalArgumentException("Actor ID must not be null");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID must not be null");
        }
        if (actorId.equals(targetUserId)) {
            throw new SelfLockNotAllowedException("Admin cannot lock own account");
        }

        User user = accountInternalApi.findUserById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + targetUserId));

        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new UserAlreadyLockedException("User is already locked");
        }

        user.setIsLocked(true);
        accountInternalApi.saveUser(user);

        accountInternalApi.revokeAllTokensByUserId(targetUserId);

        auditLogInternalApi.recordAuditLog(
                actorId,
                "USER_LOCKED",
                "USER",
                targetUserId,
                buildMetadataJson(reason)
        );

        if (transactionalEmailPort != null) {
            OffsetDateTime now = OffsetDateTime.now();
            String securityEventId = UUID.randomUUID().toString();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.ACCOUNT_LOCKED,
                    "security:account-locked:" + targetUserId + ":" + securityEventId,
                    "USER:" + targetUserId,
                    now,
                    now,
                    null,
                    1,
                    Map.of(
                            "userId", targetUserId.toString(),
                            "timestamp", now.toString(),
                            "securityEventId", securityEventId,
                            "reason", reason != null ? reason : ""
                    )
            ));
        }
    }

    private String buildMetadataJson(String reason) {
        String safeReason = escapeJson(reason != null ? reason : "");
        return "{\"reason\":\"" + safeReason + "\"}";
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
