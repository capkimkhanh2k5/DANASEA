package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.RescheduleSupport;
import com.danasea.backend.modules.order.domain.events.RescheduleProposalCreatedEvent;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderRescheduleProposalJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.order.presentation.dtos.CreateRescheduleProposalRequest;
import com.danasea.backend.modules.order.presentation.dtos.RescheduleProposalResponse;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateRescheduleProposalUseCase {

    private final JpaServiceSlotRepository slotRepository;
    private final JpaSubOrderRescheduleProposalRepository proposalRepository;
    private final VendorInternalApi vendorInternalApi;
    private final AuditLogInternalApi auditLogInternalService;

    private static final ZoneOffset VIETNAM_OFFSET = ZoneOffset.ofHours(7);

    private final RescheduleSupport support;
    private final ApplicationEventPublisher eventPublisher;
    private final ServiceSlotPort serviceSlotPort;
    private final DisruptionDecisionService decisions;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @Transactional
    public RescheduleProposalResponse execute(
            UUID subOrderId, CreateRescheduleProposalRequest request) {
        UUID currentUserId =
                SecurityUtils.getCurrentUserId()
                        .orElseThrow(() -> new AccessDeniedException("User is not authenticated."));

        UUID vendorId =
                vendorInternalApi
                        .findByUserId(currentUserId)
                        .map(Vendor::getId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Vendor profile not found for user: "
                                                        + currentUserId));

        var locked = support.lock(subOrderId);
        SubOrderJpaEntity subOrder = locked.sub();

        if (!vendorId.equals(subOrder.getVendorId())) {
            throw new AccessDeniedException("Vendor does not own this sub-order.");
        }

        RescheduleSupport.eligible(locked.master(), subOrder);
        var oldSlot =
                slotRepository
                        .findById(subOrder.getSlotId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Current slot not found."));
        if (!RescheduleSupport.upcoming(oldSlot))
            throw RescheduleSupport.conflict("Current slot has already started.");
        String reasonType =
                request.reasonType() == null
                        ? "OPERATIONAL"
                        : request.reasonType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OPERATIONAL", "WEATHER").contains(reasonType))
            throw new IllegalArgumentException("Invalid proposal reasonType.");
        if (request.reason() == null || request.reason().isBlank())
            throw new IllegalArgumentException("Proposal reason is required.");
        ServiceSlotJpaEntity proposedSlot =
                slotRepository
                        .findById(request.proposedSlotId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Invalid reschedule proposal."
                                                        + request.proposedSlotId()));

        if (!proposedSlot.getServiceId().equals(subOrder.getServiceId())) {
            throw new IllegalArgumentException("Invalid reschedule proposal.");
        }
        if (!SlotStatus.OPEN.equals(proposedSlot.getStatus())) {
            throw new IllegalArgumentException("Invalid reschedule proposal.");
        }
        if (proposedSlot.getId().equals(subOrder.getSlotId())) {
            throw new IllegalArgumentException("Invalid reschedule proposal.");
        }

        LocalDateTime proposedDeparture =
                LocalDateTime.of(proposedSlot.getDate(), proposedSlot.getStartTime());
        OffsetDateTime proposedDepartureOffset = proposedDeparture.atOffset(VIETNAM_OFFSET);
        OffsetDateTime now = OffsetDateTime.now(VIETNAM_OFFSET);

        if (!proposedDepartureOffset.isAfter(now)) {
            throw new IllegalArgumentException("Invalid reschedule proposal.");
        }

        var details =
                serviceSlotPort
                        .findSlotDetails(proposedSlot.getId())
                        .orElseThrow(() -> RescheduleSupport.conflict("Target slot unavailable."));
        if (!details.isServicePublished())
            throw RescheduleSupport.conflict("Service is not published.");
        OffsetDateTime oldDeparture =
                oldSlot.getDate().atTime(oldSlot.getStartTime()).atOffset(VIETNAM_OFFSET);
        OffsetDateTime decisionDeadline = oldDeparture.minusHours(12);
        OffsetDateTime latestExpiry =
                decisionDeadline.isBefore(proposedDepartureOffset)
                        ? decisionDeadline
                        : proposedDepartureOffset;
        OffsetDateTime expiresAt = request.expiresAt() == null ? latestExpiry : request.expiresAt();
        if (expiresAt.isAfter(latestExpiry)) {
            throw new IllegalArgumentException(
                    "Proposal expiry cannot exceed the decision deadline or target departure.");
        }
        if (!expiresAt.isAfter(now) && decisionDeadline.isAfter(now)) {
            throw new IllegalArgumentException("Proposal expiry must be in the future.");
        }

        // Đánh dấu SUPERSEDED cho các đề xuất cũ đang PENDING của subOrder này
        List<SubOrderRescheduleProposalJpaEntity> pendingList =
                proposalRepository.findBySubOrderIdAndStatus(subOrderId, "PENDING");
        for (SubOrderRescheduleProposalJpaEntity oldProposal : pendingList) {
            oldProposal.setStatus("SUPERSEDED");
            proposalRepository.save(oldProposal);
        }

        SubOrderRescheduleProposalJpaEntity newProposal =
                SubOrderRescheduleProposalJpaEntity.builder()
                        .subOrderId(subOrderId)
                        .vendorId(vendorId)
                        .proposedSlotId(proposedSlot.getId())
                        .reason(request.reason().trim())
                        .reasonType(reasonType)
                        .status("PENDING")
                        .proposalVersion(1L)
                        .expiresAt(expiresAt)
                        .build();

        SubOrderRescheduleProposalJpaEntity saved = proposalRepository.save(newProposal);
        var decision =
                decisions.record(
                        subOrderId,
                        "WEATHER".equals(reasonType)
                                ? RefundReason.WEATHER
                                : RefundReason.VENDOR_FAULT,
                        request.reason().trim(),
                        "proposal-" + saved.getId(),
                        currentUserId);
        if ("REFUND_REQUESTED".equals(decision.getStatus())) saved.setStatus("EXPIRED");

        // Ghi nhận Audit Log
        auditLogInternalService.recordTransactionalAuditLog(
                currentUserId,
                "CREATE_RESCHEDULE_PROPOSAL",
                "SUB_ORDER",
                subOrderId,
                "{\"proposalId\":\""
                        + saved.getId()
                        + "\",\"proposedSlotId\":\""
                        + proposedSlot.getId()
                        + "\",\"reasonType\":\""
                        + reasonType
                        + "\",\"expiresAt\":\""
                        + expiresAt
                        + "\"}");

        if (transactionalEmailPort != null) {
            UUID customerId = locked.master().getCustomerId();
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_RESCHEDULE_PROPOSED,
                    "proposal:" + saved.getId(),
                    "USER:" + customerId,
                    now,
                    now,
                    expiresAt,
                    1,
                    Map.of(
                            "proposalId", saved.getId().toString(),
                            "subOrderId", subOrderId.toString(),
                            "serviceName", "Dịch vụ Danasea",
                            "proposedDate", proposedSlot.getDate().toString(),
                            "proposedTime", proposedSlot.getStartTime().toString(),
                            "reason", request.reason().trim()
                    )
            ));
        }

        eventPublisher.publishEvent(new RescheduleProposalCreatedEvent(saved.getId()));

        return new RescheduleProposalResponse(
                saved.getId(),
                saved.getSubOrderId(),
                saved.getVendorId(),
                saved.getProposedSlotId(),
                proposedSlot.getDate(),
                proposedSlot.getStartTime(),
                proposedSlot.getEndTime(),
                saved.getReason(),
                saved.getReasonType(),
                saved.getStatus(),
                saved.getProposalVersion(),
                saved.getExpiresAt(),
                saved.getCreatedAt());
    }
}
