package com.danasea.backend.modules.service.domain.models;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceImagesRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.WeatherRequirementsMissingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Adversarial Invariant & State Transition Tests for Service Domain Model")
class ServiceAdversarialTest {

    // =========================================================================
    // 1. SUBMIT FOR REVIEW STATE TRANSITION MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Submit For Review Transition Matrix")
    class SubmitForReviewMatrixTests {

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "REJECTED"})
        @DisplayName("Valid states (DRAFT, REJECTED) transition to PENDING_REVIEW when hasImages=true")
        void submitForReview_validStates_withImages_shouldTransitionToPending(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            service.submitForReview(true);

            assertEquals(ServiceStatus.PENDING_REVIEW, service.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "REJECTED"})
        @DisplayName("Valid states (DRAFT, REJECTED) throw ServiceImagesRequiredException when hasImages=false and status is unchanged")
        void submitForReview_validStates_withoutImages_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(ServiceImagesRequiredException.class, () -> service.submitForReview(false));
            assertEquals(initialStatus, service.getStatus(), "Status must not mutate on failed submit");
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"PENDING_REVIEW", "PUBLISHED", "PAUSED"})
        @DisplayName("Invalid states throw InvalidServiceStateException on submitForReview regardless of hasImages")
        void submitForReview_invalidStates_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service serviceWithImages = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(InvalidServiceStateException.class, () -> serviceWithImages.submitForReview(true));
            assertEquals(initialStatus, serviceWithImages.getStatus());

            Service serviceWithoutImages = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(InvalidServiceStateException.class, () -> serviceWithoutImages.submitForReview(false));
            assertEquals(initialStatus, serviceWithoutImages.getStatus());
        }
    }

    // =========================================================================
    // 2. APPROVE STATE TRANSITION MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Approve Transition Matrix")
    class ApproveMatrixTests {

        @Test
        @DisplayName("PENDING_REVIEW transitions to PUBLISHED and clears any prior rejectionReason")
        void approve_fromPendingReview_shouldTransitionToPublishedAndClearReason() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PENDING_REVIEW)
                    .rejectionReason("Prior rejection reason")
                    .build();

            service.approve();

            assertEquals(ServiceStatus.PUBLISHED, service.getStatus());
            assertNull(service.getRejectionReason(), "Rejection reason must be cleared on approval");
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PUBLISHED", "PAUSED", "REJECTED"})
        @DisplayName("All non-PENDING_REVIEW states throw InvalidServiceStateException and retain status")
        void approve_invalidStates_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .rejectionReason("Existing reason")
                    .build();

            assertThrows(InvalidServiceStateException.class, service::approve);
            assertEquals(initialStatus, service.getStatus());
            assertEquals("Existing reason", service.getRejectionReason());
        }
    }

    // =========================================================================
    // 3. REJECT STATE TRANSITION MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Reject Transition Matrix")
    class RejectMatrixTests {

        @Test
        @DisplayName("PENDING_REVIEW transitions to REJECTED with mandatory reason")
        void reject_fromPendingReview_withValidReason_shouldTransitionToRejected() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PENDING_REVIEW)
                    .build();

            service.reject("Images are blurred and lack description");

            assertEquals(ServiceStatus.REJECTED, service.getStatus());
            assertEquals("Images are blurred and lack description", service.getRejectionReason());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("PENDING_REVIEW throws IllegalArgumentException when reason is null or blank and retains status")
        void reject_fromPendingReview_withBlankReason_shouldThrowAndRetainStatus(String invalidReason) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PENDING_REVIEW)
                    .build();

            assertThrows(IllegalArgumentException.class, () -> service.reject(invalidReason));
            assertEquals(ServiceStatus.PENDING_REVIEW, service.getStatus());
            assertNull(service.getRejectionReason());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PUBLISHED", "PAUSED", "REJECTED"})
        @DisplayName("All non-PENDING_REVIEW states throw InvalidServiceStateException and retain status")
        void reject_invalidStates_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .rejectionReason("Prior reason")
                    .build();

            assertThrows(InvalidServiceStateException.class, () -> service.reject("Valid reject reason"));
            assertEquals(initialStatus, service.getStatus());
            assertEquals("Prior reason", service.getRejectionReason());

            // Even with null reason, status check takes precedence
            assertThrows(InvalidServiceStateException.class, () -> service.reject(null));
            assertEquals(initialStatus, service.getStatus());
        }
    }

    // =========================================================================
    // 4. PAUSE & RESUME STATE TRANSITION MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Pause & Resume Transition Matrix")
    class PauseResumeMatrixTests {

        @Test
        @DisplayName("pause: PUBLISHED transitions to PAUSED")
        void pause_fromPublished_shouldTransitionToPaused() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PUBLISHED)
                    .build();

            service.pause();

            assertEquals(ServiceStatus.PAUSED, service.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PENDING_REVIEW", "PAUSED", "REJECTED"})
        @DisplayName("pause: non-PUBLISHED states throw InvalidServiceStateException and retain status")
        void pause_invalidStates_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(InvalidServiceStateException.class, service::pause);
            assertEquals(initialStatus, service.getStatus());
        }

        @Test
        @DisplayName("resume: PAUSED transitions to PUBLISHED")
        void resume_fromPaused_shouldTransitionToPublished() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PAUSED)
                    .build();

            service.resume();

            assertEquals(ServiceStatus.PUBLISHED, service.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PENDING_REVIEW", "PUBLISHED", "REJECTED"})
        @DisplayName("resume: non-PAUSED states throw InvalidServiceStateException and retain status")
        void resume_invalidStates_shouldThrowAndRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(InvalidServiceStateException.class, service::resume);
            assertEquals(initialStatus, service.getStatus());
        }
    }

    // =========================================================================
    // 5. DELETE VALIDATION MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Delete Validation Matrix")
    class DeleteValidationMatrixTests {

        @Test
        @DisplayName("validateDeletable succeeds when status is DRAFT")
        void validateDeletable_fromDraft_shouldSucceed() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.DRAFT)
                    .build();

            service.validateDeletable(); // No exception
            assertEquals(ServiceStatus.DRAFT, service.getStatus());
        }

        @Test
        @DisplayName("validateDeletable succeeds when status is REJECTED")
        void validateDeletable_fromRejected_shouldSucceed() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.REJECTED)
                    .build();

            service.validateDeletable(); // No exception
            assertEquals(ServiceStatus.REJECTED, service.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"PENDING_REVIEW", "PUBLISHED", "PAUSED"})
        @DisplayName("validateDeletable throws InvalidServiceStateException for non-deletable states")
        void validateDeletable_nonDraft_shouldThrow(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            assertThrows(InvalidServiceStateException.class, service::validateDeletable);
            assertEquals(initialStatus, service.getStatus());
        }
    }

    // =========================================================================
    // 6. TRANSITION ON UPDATE MATRIX
    // =========================================================================
    @Nested
    @DisplayName("Transition On Update Matrix")
    class TransitionOnUpdateMatrixTests {

        @Test
        @DisplayName("transitionOnUpdate transitions PUBLISHED to PENDING_REVIEW")
        void transitionOnUpdate_fromPublished_shouldTransitionToPendingReview() {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(ServiceStatus.PUBLISHED)
                    .build();

            service.transitionOnUpdate();

            assertEquals(ServiceStatus.PENDING_REVIEW, service.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = ServiceStatus.class, names = {"DRAFT", "PENDING_REVIEW", "PAUSED", "REJECTED"})
        @DisplayName("transitionOnUpdate keeps status unchanged for non-PUBLISHED services")
        void transitionOnUpdate_nonPublished_shouldRetainStatus(ServiceStatus initialStatus) {
            Service service = Service.builder()
                    .id(UUID.randomUUID())
                    .status(initialStatus)
                    .build();

            service.transitionOnUpdate();

            assertEquals(initialStatus, service.getStatus());
        }
    }

    // =========================================================================
    // 7. WEATHER INVARIANTS & BOUNDARIES
    // =========================================================================
    @Nested
    @DisplayName("Weather Invariants & Edge Cases")
    class WeatherInvariantsTests {

        @Test
        @DisplayName("weatherSensitive null or false should pass even with null wind/wave")
        void weatherNotSensitive_shouldPass() {
            Service s1 = Service.builder().weatherSensitive(false).minWindKmh(null).maxWaveM(null).build();
            s1.validateWeatherRequirements();

            Service s2 = Service.builder().weatherSensitive(null).minWindKmh(null).maxWaveM(null).build();
            s2.validateWeatherRequirements();
        }

        @Test
        @DisplayName("weatherSensitive true with zero boundary limits should pass")
        void weatherSensitive_withZeroLimits_shouldPass() {
            Service service = Service.builder()
                    .weatherSensitive(true)
                    .minWindKmh(BigDecimal.ZERO)
                    .maxWaveM(BigDecimal.ZERO)
                    .build();

            service.validateWeatherRequirements(); // Provided, should not throw
        }

        @Test
        @DisplayName("weatherSensitive true with positive limits should pass")
        void weatherSensitive_withPositiveLimits_shouldPass() {
            Service service = Service.builder()
                    .weatherSensitive(true)
                    .minWindKmh(new BigDecimal("25.5"))
                    .maxWaveM(new BigDecimal("1.8"))
                    .build();

            service.validateWeatherRequirements();
        }

        @Test
        @DisplayName("weatherSensitive true missing minWindKmh should throw WeatherRequirementsMissingException")
        void weatherSensitive_missingMinWindKmh_shouldThrow() {
            Service service = Service.builder()
                    .weatherSensitive(true)
                    .minWindKmh(null)
                    .maxWaveM(new BigDecimal("1.8"))
                    .build();

            assertThrows(WeatherRequirementsMissingException.class, service::validateWeatherRequirements);
        }

        @Test
        @DisplayName("weatherSensitive true missing maxWaveM should throw WeatherRequirementsMissingException")
        void weatherSensitive_missingMaxWaveM_shouldThrow() {
            Service service = Service.builder()
                    .weatherSensitive(true)
                    .minWindKmh(new BigDecimal("25.5"))
                    .maxWaveM(null)
                    .build();

            assertThrows(WeatherRequirementsMissingException.class, service::validateWeatherRequirements);
        }

        @Test
        @DisplayName("weatherSensitive true missing both should throw WeatherRequirementsMissingException")
        void weatherSensitive_missingBoth_shouldThrow() {
            Service service = Service.builder()
                    .weatherSensitive(true)
                    .minWindKmh(null)
                    .maxWaveM(null)
                    .build();

            assertThrows(WeatherRequirementsMissingException.class, service::validateWeatherRequirements);
        }
    }

    // =========================================================================
    // 8. OWNERSHIP VALIDATION INVARIANTS
    // =========================================================================
    @Nested
    @DisplayName("Ownership Invariants")
    class OwnershipInvariantsTests {

        @Test
        @DisplayName("isOwnedBy and validateOwnership with matching owner")
        void ownership_matchingOwner_shouldPass() {
            UUID ownerId = UUID.randomUUID();
            UUID serviceId = UUID.randomUUID();
            Service service = Service.builder().id(serviceId).vendorId(ownerId).build();

            assertTrue(service.isOwnedBy(ownerId));
            service.validateOwnership(ownerId); // No exception
        }

        @Test
        @DisplayName("validateOwnership with mismatched vendor should throw UnauthorizedServiceAccessException")
        void ownership_mismatchedOwner_shouldThrow() {
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            UUID serviceId = UUID.randomUUID();
            Service service = Service.builder().id(serviceId).vendorId(ownerId).build();

            assertFalse(service.isOwnedBy(otherId));
            assertThrows(UnauthorizedServiceAccessException.class, () -> service.validateOwnership(otherId));
        }

        @Test
        @DisplayName("validateOwnership when service vendorId is null should throw UnauthorizedServiceAccessException")
        void ownership_nullServiceVendorId_shouldThrow() {
            UUID vendorId = UUID.randomUUID();
            Service service = Service.builder().id(UUID.randomUUID()).vendorId(null).build();

            assertFalse(service.isOwnedBy(vendorId));
            assertThrows(UnauthorizedServiceAccessException.class, () -> service.validateOwnership(vendorId));
        }

        @Test
        @DisplayName("validateOwnership when parameter is null should throw UnauthorizedServiceAccessException")
        void ownership_nullParam_shouldThrow() {
            UUID ownerId = UUID.randomUUID();
            Service service = Service.builder().id(UUID.randomUUID()).vendorId(ownerId).build();

            assertFalse(service.isOwnedBy(null));
            assertThrows(UnauthorizedServiceAccessException.class, () -> service.validateOwnership(null));
        }
    }
}
