package com.danasea.backend.modules.checkin.presentation.dtos;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EligibleSubOrderCandidate(
        UUID subOrderId,
        UUID serviceId,
        String serviceName,
        UUID slotId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        int quantity
) {}
