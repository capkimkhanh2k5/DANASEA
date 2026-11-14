package com.danasea.backend.modules.service.domain.models;

import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceImagesRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.WeatherRequirementsMissingException;
import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Service extends BaseDomainModel {
    private UUID vendorId;
    private UUID categoryId;
    private String name;
    private String nameEn;
    private String slug;
    private String description;
    private String descriptionEn;
    private BigDecimal price;
    private Integer durationMinutes;
    private Integer capacityPerSlot;
    private String locationName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ServiceStatus status;
    private String rejectionReason;
    private String waiverContent;
    private String waiverContentEn;
    private Boolean waiverRequired;
    private Integer waiverVersion;
    private Boolean weatherSensitive;
    private BigDecimal minWindKmh;
    private BigDecimal maxWaveM;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private Integer viewCount;
    private OperationType operationType = OperationType.FIXED_DEPARTURE;

    public boolean isDraft() {
        return ServiceStatus.DRAFT.equals(this.status);
    }

    public boolean isPendingReview() {
        return ServiceStatus.PENDING_REVIEW.equals(this.status);
    }

    public boolean isPublished() {
        return ServiceStatus.PUBLISHED.equals(this.status);
    }

    public boolean isPaused() {
        return ServiceStatus.PAUSED.equals(this.status);
    }

    public boolean isRejected() {
        return ServiceStatus.REJECTED.equals(this.status);
    }

    public boolean isOwnedBy(UUID vendorId) {
        return this.vendorId != null && this.vendorId.equals(vendorId);
    }

    public void validateOwnership(UUID vendorId) {
        if (!isOwnedBy(vendorId)) {
            throw new UnauthorizedServiceAccessException(this.getId(), vendorId);
        }
    }

    public void validateWeatherRequirements() {
        if (Boolean.TRUE.equals(this.weatherSensitive)) {
            if (this.minWindKmh == null || this.maxWaveM == null) {
                throw new WeatherRequirementsMissingException();
            }
        }
    }

    public void submitForReview(boolean hasImages) {
        if (!isDraft() && !isRejected()) {
            throw new InvalidServiceStateException("Cannot submit service in status " + this.status + " for review. Must be DRAFT or REJECTED.");
        }
        if (!hasImages) {
            throw new ServiceImagesRequiredException();
        }
        this.status = ServiceStatus.PENDING_REVIEW;
    }

    public void approve() {
        if (!isPendingReview()) {
            throw new InvalidServiceStateException("Cannot approve service in status " + this.status + ". Must be PENDING_REVIEW.");
        }
        this.status = ServiceStatus.PUBLISHED;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        if (!isPendingReview()) {
            throw new InvalidServiceStateException("Cannot reject service in status " + this.status + ". Must be PENDING_REVIEW.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        this.status = ServiceStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void pause() {
        if (!isPublished()) {
            throw new InvalidServiceStateException("Cannot pause service in status " + this.status + ". Must be PUBLISHED.");
        }
        this.status = ServiceStatus.PAUSED;
    }

    public void resume() {
        if (!isPaused()) {
            throw new InvalidServiceStateException("Cannot resume service in status " + this.status + ". Must be PAUSED.");
        }
        this.status = ServiceStatus.PUBLISHED;
    }

    public void validateDeletable() {
        if (!isDraft()) {
            throw new InvalidServiceStateException("Cannot delete service in status " + this.status + ". Only DRAFT services can be deleted.");
        }
    }

    public void transitionOnUpdate() {
        if (isPublished()) {
            this.status = ServiceStatus.PENDING_REVIEW;
        }
    }
}
