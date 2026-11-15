package com.danasea.backend.modules.service.presentation.dtos;

import java.util.List;

public record BatchCreateServiceSlotsResponse(
        int totalCreated,
        List<ServiceSlotResponse> slots
) {}
