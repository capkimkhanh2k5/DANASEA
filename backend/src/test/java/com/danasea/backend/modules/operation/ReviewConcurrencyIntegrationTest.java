package com.danasea.backend.modules.operation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.application.usecases.*;
import com.danasea.backend.modules.operation.domain.exceptions.DuplicateReviewException;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.domain.exceptions.ReviewNotFoundException;
import com.danasea.backend.modules.operation.domain.exceptions.UnauthorizedReviewAccessException;
import com.danasea.backend.modules.operation.domain.services.ReviewRatingService;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.*;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewConcurrencyIntegrationTest {
    @Container
    static GenericContainer<?> postgres =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "review_test")
                    .withEnv("POSTGRES_USER", "test")
                    .withEnv("POSTGRES_PASSWORD", "test")
                    .withExposedPorts(5432);

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () ->
                        "jdbc:postgresql://"
                                + postgres.getHost()
                                + ":"
                                + postgres.getMappedPort(5432)
                                + "/review_test");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.datasource.username", () -> "test");
        r.add("spring.datasource.password", () -> "test");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
    }

    @MockitoBean RabbitTemplate rabbit;
    @MockitoBean LettuceBasedProxyManager<byte[]> proxy;
    @Autowired JpaUserRepository users;
    @Autowired JpaVendorRepository vendors;
    @Autowired JpaServiceRepository services;
    @Autowired JpaMasterOrderRepository masters;
    @Autowired JpaSubOrderRepository subs;
    @Autowired JpaReviewRepository reviews;
    @Autowired ReviewMapper mapper;
    @Autowired ReviewRatingService ratings;
    @Autowired CreateReviewUseCase create;
    @Autowired FlagReviewUseCase flag;
    @Autowired UpdateReviewUseCase edit;
    @Autowired ReplyVendorReviewUseCase reply;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UpdateReviewVisibilityUseCase visibility;
    @Autowired GetServiceReviewsUseCase publicReviews;
    @Autowired PlatformTransactionManager tm;
    @Autowired jakarta.validation.Validator validator;

    record Fixture(
            UserJpaEntity user,
            VendorJpaEntity vendor,
            ServiceJpaEntity service,
            SubOrderJpaEntity sub) {}

    Fixture seed() {
        UserJpaEntity u = new UserJpaEntity();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setRole(Role.CUSTOMER);
        u.setFullName("Review customer");
        u = users.saveAndFlush(u);
        UserJpaEntity vu = new UserJpaEntity();
        vu.setEmail(UUID.randomUUID() + "@example.com");
        vu.setRole(Role.VENDOR);
        vu = users.saveAndFlush(vu);
        VendorJpaEntity v = new VendorJpaEntity();
        v.setUserId(vu.getId());
        v.setBusinessName("Review vendor");
        v.setVerificationStatus(VerificationStatus.APPROVED);
        v = vendors.saveAndFlush(v);
        ServiceJpaEntity s = new ServiceJpaEntity();
        s.setVendorId(v.getId());
        s.setName("Review service");
        s.setSlug("review-" + UUID.randomUUID());
        s.setStatus(ServiceStatus.PUBLISHED);
        s.setPrice(BigDecimal.TEN);
        s = services.saveAndFlush(s);
        return new Fixture(u, v, s, sub(u, s));
    }

    SubOrderJpaEntity sub(UserJpaEntity u, ServiceJpaEntity s) {
        MasterOrderJpaEntity m = new MasterOrderJpaEntity();
        m.setCustomerId(u.getId());
        m.setTotalAmount(BigDecimal.TEN);
        m = masters.saveAndFlush(m);
        SubOrderJpaEntity o = new SubOrderJpaEntity();
        o.setMasterOrderId(m.getId());
        o.setVendorId(s.getVendorId());
        o.setServiceId(s.getId());
        o.setStatus(SubOrderStatus.COMPLETED);
        o.setUnitPrice(BigDecimal.TEN);
        o.setQuantity(1);
        return subs.saveAndFlush(o);
    }

    CreateReviewRequest request(int rating) {
        return new CreateReviewRequest((short) rating, "Review comment", null);
    }

    @Test
    void sameExperienceCanCreateOnlyOneReview() throws Exception {
        Fixture f = seed();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> tryCreate(f.sub().getId(), f.user().getId(), 5, start));
            var b = pool.submit(() -> tryCreate(f.sub().getId(), f.user().getId(), 1, start));
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(
                        reviews.findAll().stream()
                                .filter(x -> x.getSubOrderId().equals(f.sub().getId()))
                                .count())
                .isEqualTo(1);
        assertThat(vendors.findById(f.vendor().getId()).orElseThrow().getRatingCount())
                .isEqualTo(1);
    }

    boolean tryCreate(UUID subId, UUID customerId, int rating, CountDownLatch start)
            throws Exception {
        start.await();
        try {
            create.execute(subId, request(rating), customerId);
            return true;
        } catch (DuplicateReviewException expected) {
            return false;
        }
    }

    @Test
    void uniqueConstraintProtectsAgainstDirectDuplicateInserts() {
        Fixture f = seed();
        create.execute(f.sub().getId(), request(5), f.user().getId());
        var duplicate =
                ReviewJpaEntity.builder()
                        .subOrderId(f.sub().getId())
                        .customerId(f.user().getId())
                        .vendorId(f.vendor().getId())
                        .serviceId(f.service().getId())
                        .rating((short) 1)
                        .comment("Duplicate")
                        .build();
        assertThatThrownBy(() -> reviews.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reviews.findBySubOrderId(f.sub().getId())).isPresent();
    }

    @Test
    void differentServicesWaitingOnVendorLockKeepAccurateAggregates() throws Exception {
        Fixture f = seed();
        ServiceJpaEntity other = new ServiceJpaEntity();
        other.setVendorId(f.vendor().getId());
        other.setName("Other service");
        other.setSlug("other-" + UUID.randomUUID());
        other.setStatus(ServiceStatus.PUBLISHED);
        other.setPrice(BigDecimal.TEN);
        other = services.saveAndFlush(other);
        SubOrderJpaEntity second = sub(f.user(), other);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var pending =
                    new TransactionTemplate(tm)
                            .execute(
                                    tx -> {
                                        vendors.findByIdForUpdate(f.vendor().getId()).orElseThrow();
                                        var a =
                                                pool.submit(
                                                        () ->
                                                                create.execute(
                                                                        f.sub().getId(),
                                                                        request(5),
                                                                        f.user().getId()));
                                        var b =
                                                pool.submit(
                                                        () ->
                                                                create.execute(
                                                                        second.getId(),
                                                                        request(1),
                                                                        f.user().getId()));
                                        awaitBlocked(2);
                                        return List.of(a, b);
                                    });
            for (var future : pending) assertThat(future.get(15, TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(reviews.countByVendorIdAndIsVisibleTrue(f.vendor().getId())).isEqualTo(2);
        VendorJpaEntity vendor = vendors.findById(f.vendor().getId()).orElseThrow();
        assertThat(vendor.getRatingCount()).isEqualTo(2);
        assertThat(vendor.getRatingAvg()).isEqualByComparingTo("3.00");
        assertThat(services.findById(f.service().getId()).orElseThrow().getAvgRating())
                .isEqualByComparingTo("5.00");
        assertThat(services.findById(other.getId()).orElseThrow().getAvgRating())
                .isEqualByComparingTo("1.00");
    }

    void awaitBlocked(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and"
                            + " wait_event_type='Lock'",
                        Integer.class)
                < expected) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("Requests did not wait for database locks");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    @Test
    void aliasEditWaitingForAdminCommitDoesNotRestoreVisibility() throws Exception {
        Fixture f = seed();
        ReviewResponse initial = create.execute(f.sub().getId(), request(5), f.user().getId());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending =
                    new TransactionTemplate(tm)
                            .execute(
                                    tx -> {
                                        reviews.findByIdForUpdate(initial.id()).orElseThrow();
                                        var future =
                                                pool.submit(
                                                        () ->
                                                                edit.executeBySubOrder(
                                                                        f.sub().getId(),
                                                                        new UpdateReviewRequest(
                                                                                (short) 4,
                                                                                "Updated by"
                                                                                    + " customer",
                                                                                null),
                                                                        f.user().getId()));
                                        awaitBlocked(1);
                                        visibility.execute(
                                                initial.id(),
                                                new UpdateReviewVisibilityRequest(
                                                        false, "Moderator private note"));
                                        return future;
                                    });
            assertThat(pending.get(15, TimeUnit.SECONDS).isVisible()).isFalse();
        }
        var finalReview = reviews.findById(initial.id()).orElseThrow();
        assertThat(finalReview.getIsVisible()).isFalse();
        assertThat(finalReview.getComment()).isEqualTo("Updated by customer");
        assertThat(finalReview.getModerationNote()).isEqualTo("Moderator private note");
        assertThat(services.findById(f.service().getId()).orElseThrow().getRatingCount()).isZero();
        assertThat(vendors.findById(f.vendor().getId()).orElseThrow().getRatingCount()).isZero();
    }

    @Test
    void concurrentEditReplyAndHidePreserveAllChanges() throws Exception {
        Fixture f = seed();
        ReviewResponse initial = create.execute(f.sub().getId(), request(5), f.user().getId());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return edit.execute(
                                        initial.id(),
                                        new UpdateReviewRequest((short) 4, "Customer update", null),
                                        f.user().getId());
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return reply.execute(
                                        initial.id(),
                                        new VendorReplyRequest("Vendor reply"),
                                        f.vendor().getUserId());
                            });
            var c =
                    pool.submit(
                            () -> {
                                start.await();
                                return visibility.execute(
                                        initial.id(),
                                        new UpdateReviewVisibilityRequest(false, "Moderated"));
                            });
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isNotNull();
            assertThat(b.get(15, TimeUnit.SECONDS)).isNotNull();
            assertThat(c.get(15, TimeUnit.SECONDS)).isNotNull();
        }
        var review = reviews.findById(initial.id()).orElseThrow();
        assertThat(review.getIsVisible()).isFalse();
        assertThat(review.getComment()).isEqualTo("Customer update");
        assertThat(review.getVendorReply()).isEqualTo("Vendor reply");
        assertThat(review.getModerationNote()).isEqualTo("Moderated");
        assertThat(vendors.findById(f.vendor().getId()).orElseThrow().getRatingCount()).isZero();
    }

    @Test
    void hiddenReviewCannotBeFlaggedOrReadThroughFlagResponse() {
        Fixture f = seed();
        ReviewResponse initial = create.execute(f.sub().getId(), request(5), f.user().getId());
        visibility.execute(
                initial.id(), new UpdateReviewVisibilityRequest(false, "Moderator private note"));
        assertThatThrownBy(() -> flag.execute(initial.id(), new FlagReviewRequest("Report")))
                .isInstanceOf(ReviewNotFoundException.class);
        assertThat(reviews.findById(initial.id()).orElseThrow().getModerationNote())
                .isEqualTo("Moderator private note");
    }

    @Test
    void flagAcknowledgmentAndPublicListContainNoPrivateMetadata() throws Exception {
        Fixture f = seed();
        ReviewResponse initial = create.execute(f.sub().getId(), request(5), f.user().getId());
        visibility.execute(
                initial.id(), new UpdateReviewVisibilityRequest(true, "Moderator private note"));
        var ack = flag.execute(initial.id(), new FlagReviewRequest("Reported by customer"));
        var tree = json.readTree(json.writeValueAsString(ack));
        assertThat(tree.size()).isEqualTo(2);
        assertThat(tree.has("comment")).isFalse();
        assertThat(tree.has("flagReason")).isFalse();
        var page = publicReviews.execute(f.service().getId(), PageRequest.of(0, 10));
        var response = page.getContent().getFirst();
        assertThat(response.comment()).isEqualTo("Review comment");
        assertThat(response.subOrderId()).isNull();
        assertThat(response.customerId()).isNull();
        assertThat(response.flagReason()).isNull();
        assertThat(response.moderationNote()).isNull();
        var stored = reviews.findById(initial.id()).orElseThrow();
        assertThat(stored.getFlagReason()).isEqualTo("Reported by customer");
        assertThat(stored.getModerationNote()).isEqualTo("Moderator private note");
    }

    @Test
    void allowedImagesLargerThanTwoThousandCharactersPersistSuccessfully() {
        Fixture f = seed();
        String url = "https://example.com/" + "a".repeat(600);
        CreateReviewRequest request =
                new CreateReviewRequest(
                        (short) 5, "Valid comment", List.of(url, url, url, url, url));
        assertThat(validator.validate(request)).isEmpty();
        var response = create.execute(f.sub().getId(), request, f.user().getId());
        assertThat(response.images()).hasSize(5);
        assertThat(reviews.findById(response.id()).orElseThrow().getImages())
                .hasSizeGreaterThan(2000);
    }

    @Test
    void excessiveImagesInvalidSchemesBlankAndOverlongUrlsAreRejected() {
        for (List<String> images :
                List.of(
                        java.util.Collections.nCopies(6, "https://example.com/image.jpg"),
                        List.of("javascript:alert(1)"),
                        List.of(" "),
                        List.of("https://example.com/" + "a".repeat(2100)))) {
            assertThat(
                            validator.validate(
                                    new CreateReviewRequest((short) 5, "Valid comment", images)))
                    .isNotEmpty();
            assertThat(
                            validator.validate(
                                    new UpdateReviewRequest((short) 5, "Valid comment", images)))
                    .isNotEmpty();
        }
    }

    @Test
    void reviewOwnershipAndCompletionRemainRequired() {
        Fixture f = seed();
        UUID outsider = UUID.randomUUID();
        assertThatThrownBy(() -> create.execute(f.sub().getId(), request(5), outsider))
                .isInstanceOf(UnauthorizedReviewAccessException.class);
        var initial = create.execute(f.sub().getId(), request(5), f.user().getId());
        assertThatThrownBy(
                        () ->
                                edit.execute(
                                        initial.id(),
                                        new UpdateReviewRequest(
                                                (short) 1, "Unauthorized edit", null),
                                        outsider))
                .isInstanceOf(UnauthorizedReviewAccessException.class);
        SubOrderJpaEntity incomplete = sub(f.user(), f.service());
        incomplete.setStatus(SubOrderStatus.CONFIRMED);
        subs.saveAndFlush(incomplete);
        assertThatThrownBy(() -> create.execute(incomplete.getId(), request(5), f.user().getId()))
                .isInstanceOf(InvalidReviewSubOrderStateException.class);
    }

    @Test
    void hidingAndRestoringReviewRecalculatesBadgeAndCounts() {
        Fixture f = seed();
        List<UUID> reviewIds = new java.util.ArrayList<>();
        reviewIds.add(create.execute(f.sub().getId(), request(5), f.user().getId()).id());
        for (int i = 0; i < 4; i++)
            reviewIds.add(
                    create.execute(sub(f.user(), f.service()).getId(), request(5), f.user().getId())
                            .id());
        assertThat(vendors.findById(f.vendor().getId()).orElseThrow().getBadgeTier())
                .isEqualTo(BadgeTier.TOP_RATED);
        visibility.execute(
                reviewIds.getFirst(), new UpdateReviewVisibilityRequest(false, "Moderation"));
        var hidden = vendors.findById(f.vendor().getId()).orElseThrow();
        assertThat(hidden.getRatingCount()).isEqualTo(4);
        assertThat(hidden.getBadgeTier()).isEqualTo(BadgeTier.VERIFIED);
        visibility.execute(
                reviewIds.getFirst(), new UpdateReviewVisibilityRequest(true, "Restored"));
        assertThat(vendors.findById(f.vendor().getId()).orElseThrow().getBadgeTier())
                .isEqualTo(BadgeTier.TOP_RATED);
    }
}
