package com.danasea.backend.modules.settlement.application.dtos;

public record MarkPaidPayoutRequest(
    String bankReference,
    String adminNote
) {}
