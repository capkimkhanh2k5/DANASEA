package com.danasea.backend.security.authentication.presentation.dtos;

public record EmailNotVerifiedResponse(
        String code,
        String message,
        String email,
        String accessToken
) {}
