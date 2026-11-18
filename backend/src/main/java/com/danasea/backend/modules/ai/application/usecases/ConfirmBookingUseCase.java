package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.ConfirmationCardStorePort;
import com.danasea.backend.modules.ai.application.ports.ConfirmationOutcomePort;
import com.danasea.backend.modules.ai.domain.models.ConfirmationCard;
import com.danasea.backend.modules.ai.domain.models.ParticipantGrouping;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingHoldCommand;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi;
import com.danasea.backend.modules.service.application.dtos.ServiceDetailResult;
import com.danasea.backend.modules.service.application.usecases.GetPublicServiceDetailUseCase;
import com.danasea.backend.modules.service.domain.ports.ServiceAvailabilityPort;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("aiConfirmBookingUseCase")
public class ConfirmBookingUseCase {

    private final ConfirmationCardStorePort cardStorePort;
    private final GetPublicServiceDetailUseCase getServiceDetailUseCase;
    private final StringRedisTemplate redisTemplate;
    private final CreateBookingHoldUseCase createBookingHoldUseCase;
    private final CancelBookingHoldUseCase cancelBookingHoldUseCase;
    private final ServiceAvailabilityPort serviceAvailabilityPort;
    private final AiCatalogReadApi catalog;
    private ConfirmationOutcomePort durableOutcomes;
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired
    void setLocalizedMessageService(LocalizedMessageService messages) {
        this.messages = messages;
    }

    @Autowired(required = false)
    public void setDurableOutcomes(ConfirmationOutcomePort durableOutcomes) { this.durableOutcomes = durableOutcomes; }

    private static final String RETRY_KEY_PREFIX = "ai:booking:retries:";
    private static final Duration HOLD_TTL = Duration.ofMinutes(15);
    private static final Duration PROCESSING_LOCK_TTL = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 2;
    private static final String REASON_PRICE_CHANGED = "PRICE_CHANGED";
    private static final String REASON_SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";

    @Autowired
    public ConfirmBookingUseCase(ConfirmationCardStorePort cardStorePort,
                                 GetPublicServiceDetailUseCase getServiceDetailUseCase,
                                 @Autowired(required = false) StringRedisTemplate redisTemplate,
                                 CreateBookingHoldUseCase createBookingHoldUseCase,
                                 CancelBookingHoldUseCase cancelBookingHoldUseCase,
                                 ServiceAvailabilityPort serviceAvailabilityPort,
                                 AiCatalogReadApi catalog) {
        this.cardStorePort = cardStorePort;
        this.getServiceDetailUseCase = getServiceDetailUseCase;
        this.redisTemplate = redisTemplate;
        this.createBookingHoldUseCase = createBookingHoldUseCase;
        this.cancelBookingHoldUseCase = cancelBookingHoldUseCase;
        this.serviceAvailabilityPort = serviceAvailabilityPort;
        this.catalog = catalog;
    }

    public ConfirmBookingUseCase(ConfirmationCardStorePort store, GetPublicServiceDetailUseCase details,
                                 StringRedisTemplate redis, CreateBookingHoldUseCase holds,
                                 CancelBookingHoldUseCase cancel, ServiceAvailabilityPort slots) {
        this(store, details, redis, holds, cancel, slots, null);
    }

    public ConfirmBookingUseCase(ConfirmationCardStorePort cardStorePort,
                                 GetPublicServiceDetailUseCase getServiceDetailUseCase,
                                 StringRedisTemplate redisTemplate) {
        this(cardStorePort, getServiceDetailUseCase, redisTemplate, null, null, null);
    }

    public ConfirmBookingUseCase(ConfirmationCardStorePort cardStorePort,
                                 GetPublicServiceDetailUseCase getServiceDetailUseCase) {
        this(cardStorePort, getServiceDetailUseCase, null, null, null, null);
    }

    public Map<String, Object> execute(String cardId, UUID userId, String sessionId) {
        return execute(cardId, userId, sessionId, null);
    }

    public Map<String, Object> execute(
            String cardId,
            UUID userId,
            String sessionId,
            UUID expectedConversationId) {
        if (durableOutcomes != null && createBookingHoldUseCase != null && catalog != null) {
            var completed = durableOutcomes.completed(cardId, userId, expectedConversationId);
            if (completed.isPresent()) return completed.get();
            ConfirmationCard card = cardStorePort.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Confirmation card not found or expired"));
            return durableOutcomes.execute(card, userId, expectedConversationId,
                    () -> executeWithProcessingLock(cardId, userId, sessionId, expectedConversationId));
        }
        return executeWithProcessingLock(cardId, userId, sessionId, expectedConversationId);
    }

    private Map<String, Object> executeWithProcessingLock(String cardId, UUID userId, String sessionId,
            UUID expectedConversationId) {
        String processingLockToken = null;
        if (createBookingHoldUseCase != null) {
            processingLockToken = cardStorePort.tryAcquireProcessingLock(cardId, PROCESSING_LOCK_TTL)
                    .orElseThrow(() -> new IllegalStateException("Confirmation card is already being processed"));
        }
        try {
            return executeLocked(cardId, userId, sessionId, expectedConversationId);
        } finally {
            if (processingLockToken != null) {
                cardStorePort.releaseProcessingLock(cardId, processingLockToken);
            }
        }
    }

    private Map<String, Object> executeLocked(
            String cardId,
            UUID userId,
            String sessionId,
            UUID expectedConversationId) {
        ConfirmationCard card = cardStorePort.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Confirmation card not found or expired"));

        if (expectedConversationId != null && !expectedConversationId.equals(card.getConversationId())) {
            throw new AccessDeniedException("Confirmation card does not belong to this conversation");
        }

        if (card.getOwnerId() != null && !card.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Confirmation card does not belong to this user");
        }

        if (ConfirmationCard.STATUS_CONFIRMED.equals(card.getStatus()) && card.getConfirmationOutcome() != null) {
            if (card.getConfirmedBy() != null && !card.getConfirmedBy().equals(userId)) {
                throw new AccessDeniedException("Confirmation outcome does not belong to this user");
            }
            return new HashMap<>(card.getConfirmationOutcome());
        }

        if (!ConfirmationCard.STATUS_PENDING.equals(card.getStatus())) {
            throw new IllegalStateException("Card is not in PENDING state");
        }
        SupportedLanguage language = SupportedLanguage.fromTag(card.getLocale()).orElse(null);

        if (catalog != null && (card.getOptionId() == null || card.getParticipantsCount() == null || card.getParticipantsCount() < 1 || card.getParticipantsCount() > 50 || card.getQuantity() == null || card.getQuantity() < 1 || card.getDate() == null || card.getDate().length() < 10)) {
            card.markCancelled(); cardStorePort.save(card);
            return Map.of("status", "refresh_required", "message", "Select an active option and request a new confirmation card.");
        }
        AiCatalogReadApi.Option currentOption = null;
        ServiceDetailResult serviceDetail = null;
        List<String> availableDates;
        BigDecimal currentPrice;
        if (catalog != null) {
            LocalDate from = LocalDate.parse(card.getDate().substring(0, 10));
            var snapshot = catalog.find(card.getServiceId(), new AiCatalogReadApi.Query(null, null, from, from.plusDays(7),
                    card.getParticipantsCount(), null, null, null, 1, language == null ? SupportedLanguage.VI : language));
            currentOption = snapshot.options().stream().filter(option -> option.id().equals(card.getOptionId())).findFirst().orElse(null);
            currentPrice = currentOption == null ? null : currentOption.unitPrice();
            availableDates = currentOption == null ? List.of() : currentOption.slots().stream()
                    .map(slot -> LocalDateTime.of(slot.date(), slot.start()).toString()).toList();
        } else {
            serviceDetail = getServiceDetailUseCase.execute(card.getServiceId(), userId, sessionId);
            currentPrice = serviceDetail.getPromotionalPrice() != null ? serviceDetail.getPromotionalPrice() : serviceDetail.getPrice();
            availableDates = serviceDetail.getAvailableSlots() == null ? List.of() : serviceDetail.getAvailableSlots();
        }
        if (currentPrice == null) {
            currentPrice = BigDecimal.ZERO;
        }

        BigDecimal cardPrice = card.getPrice() != null ? card.getPrice() : BigDecimal.ZERO;
        if (currentOption != null && "PER_PACKAGE".equals(currentOption.pricingUnit())) {
            List<Integer> grouping = card.getParticipantsPerPackage();
            if (grouping == null && card.getQuantity() == 1) grouping = List.of(card.getParticipantsCount());
            if (!ParticipantGrouping.valid(grouping, card.getParticipantsCount(), currentOption.maxPaxPerPackage(), card.getQuantity())) {
                card.markCancelled(); cardStorePort.save(card);
                return Map.of("status", "refresh_required", "message",
                        "Request a new confirmation card showing the participants in each private package.");
            }
            card.setParticipantsPerPackage(grouping);
        }
        boolean priceChanged = currentPrice.compareTo(cardPrice) != 0
                || currentOption != null && currentOption.quantity() != card.getQuantity();
        
        // Slot validation (if date/slot is in availableSlots)
        boolean hasSlots = !availableDates.isEmpty();
        boolean slotAvailable = hasSlots && card.getDate() != null && availableDates.contains(card.getDate());
        if (currentOption != null) slotAvailable = slotAvailable && currentOption.slots().stream().anyMatch(slot -> slot.id().equals(card.getSlotId()));

        // Check if slots are completely exhausted
        if (!hasSlots) {
            card.markCancelled();
            cardStorePort.save(card);
            Map<String, Object> outOfStockResponse = new HashMap<>();
            outOfStockResponse.put("status", "out_of_stock");
            outOfStockResponse.put("message", localizedOrLegacy(
                    "ai.booking.out_of_stock",
                    "The selected slot or service is no longer available. Please choose another date.", language));
            outOfStockResponse.put("cardId", cardId);
            return outOfStockResponse;
        }

        if (priceChanged || !slotAvailable) {
            // Check retry limit from Redis / card (enforce max 2 retries; fail on 3rd attempt)
            int currentRetries = getRetryCount(card);

            if (currentRetries >= MAX_RETRIES) {
                card.markCancelled();
                cardStorePort.save(card);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", localizedOrLegacy(
                        "ai.booking.too_many_changes",
                        "Service details have changed too many times. Please start over.", language));
                return errorResponse;
            }

            int nextRetry = currentRetries + 1;
            recordRetryCount(card, nextRetry);

            // Build a new card and return alternative
            card.markCancelled();
            cardStorePort.save(card); // Update old card status

            String alternativeDate = !slotAvailable && hasSlots ? availableDates.get(0) : card.getDate();
            UUID alternativeSlotId = card.getSlotId();
            if (!slotAvailable && currentOption != null) {
                alternativeSlotId = currentOption.slots().get(0).id();
            } else if (!slotAvailable && serviceAvailabilityPort != null) {
                alternativeSlotId = serviceAvailabilityPort
                        .findAvailableSlotId(card.getServiceId(), alternativeDate)
                        .orElse(null);
            }
            String reason = priceChanged ? REASON_PRICE_CHANGED : REASON_SLOT_UNAVAILABLE;

            ConfirmationCard newCard = ConfirmationCard.builder()
                    .id(UUID.randomUUID().toString())
                    .conversationId(card.getConversationId())
                    .ownerId(card.getOwnerId())
                    .serviceId(card.getServiceId())
                    .slotId(alternativeSlotId)
                    .optionId(card.getOptionId())
                    .participantsCount(card.getParticipantsCount())
                    .price(currentPrice)
                    .date(alternativeDate)
                    .quantity(currentOption == null ? card.getQuantity() : currentOption.quantity())
                    .participantsPerPackage(currentOption != null && "PER_PACKAGE".equals(currentOption.pricingUnit())
                            ? ParticipantGrouping.propose(card.getParticipantsCount(), currentOption.maxPaxPerPackage(), currentOption.quantity()) : List.of())
                    .status(ConfirmationCard.STATUS_PENDING)
                    .createdAt(LocalDateTime.now())
                    .retryCount(nextRetry)
                    .reason(reason)
                    .locale(card.getLocale())
                    .build();

            cardStorePort.save(newCard);

            BigDecimal previousTotalAmount = cardPrice.multiply(BigDecimal.valueOf(card.getQuantity()));
            BigDecimal newTotalAmount = currentPrice.multiply(BigDecimal.valueOf(newCard.getQuantity()));
            Map<String, Object> response = new HashMap<>();
            response.put("status", "alternative_needed");
            response.put("message", localizedOrLegacy(
                    "ai.booking.changed",
                    "The price or slot has changed. Please confirm the new details.", language));
            response.put("oldCardId", cardId);
            response.put("newCard", newCard);
            response.put("reason", reason);
            response.put("previousPrice", cardPrice);
            response.put("previousTotalAmount", previousTotalAmount);
            response.put("newPrice", currentPrice);
            response.put("newTotalAmount", newTotalAmount);
            response.put("previousDate", card.getDate());
            response.put("priceChanged", priceChanged);
            response.put("slotChanged", !slotAvailable);
            return response;
        }

        BookingHoldResult hold = null;
        if (createBookingHoldUseCase != null) {
            if (userId == null) {
                throw new AccessDeniedException("User is not authenticated");
            }
            if (card.getSlotId() == null) {
                throw new IllegalStateException("Confirmation card does not reference an available service slot");
            }
            List<BookingHoldItemDto> items = currentOption != null && "PER_PACKAGE".equals(currentOption.pricingUnit())
                    ? ParticipantGrouping.counts(card.getParticipantsPerPackage()).entrySet().stream().map(group ->
                            new BookingHoldItemDto(card.getSlotId(), group.getValue(), card.getOptionId(), group.getKey())).toList()
                    : List.of(new BookingHoldItemDto(card.getSlotId(), card.getQuantity(), card.getOptionId(), card.getParticipantsCount()));
            hold = createBookingHoldUseCase.execute(new CreateBookingHoldCommand(userId, items));
            if (catalog != null && hold.totalAmount().compareTo(card.getPrice().multiply(BigDecimal.valueOf(card.getQuantity()))) != 0) {
                cancelBookingHoldUseCase.execute(new CancelBookingHoldCommand(hold.bookingId(), userId));
                card.markCancelled(); cardStorePort.save(card);
                return Map.of("status", "refresh_required", "message", "The quote changed while inventory was checked. Request a new confirmation card.");
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", localizedOrLegacy("ai.booking.success",
                hold == null ? "Booking confirmation accepted."
                        : "Booking inventory is held. Complete payment before the hold expires.", language));
        response.put("cardId", cardId);
        if (hold != null) {
            response.put("bookingId", hold.bookingId());
            response.put("bookingStatus", hold.status());
            response.put("holdExpiresAt", hold.holdExpiresAt());
            response.put("totalAmount", hold.totalAmount());
        }
        try {
            card.setConfirmedBy(userId);
            card.setConfirmationOutcome(response);
            card.markConfirmed();
            cardStorePort.save(card);
        } catch (RuntimeException exception) {
            if (hold != null && cancelBookingHoldUseCase != null) {
                try {
                    cancelBookingHoldUseCase.execute(new CancelBookingHoldCommand(hold.bookingId(), userId));
                } catch (RuntimeException compensationFailure) {
                    exception.addSuppressed(compensationFailure);
                }
            }
            throw exception;
        }

        return response;
    }

    private int getRetryCount(ConfirmationCard card) {
        if (redisTemplate != null && card.getConversationId() != null) {
            try {
                String val = redisTemplate.opsForValue().get(RETRY_KEY_PREFIX + card.getConversationId());
                if (val != null) {
                    return Integer.parseInt(val);
                }
            } catch (Exception e) {
                log.warn("Failed to get retry count from Redis", e);
            }
        }
        return card.getRetryCount();
    }

    private void recordRetryCount(ConfirmationCard card, int nextRetry) {
        if (redisTemplate != null && card.getConversationId() != null) {
            try {
                redisTemplate.opsForValue().set(RETRY_KEY_PREFIX + card.getConversationId(), String.valueOf(nextRetry), HOLD_TTL);
            } catch (Exception e) {
                log.warn("Failed to record retry count in Redis", e);
            }
        }
    }

    private String localizedOrLegacy(String key, String legacy, SupportedLanguage language) {
        return language == null ? legacy : messages.get(key, language);
    }
}
