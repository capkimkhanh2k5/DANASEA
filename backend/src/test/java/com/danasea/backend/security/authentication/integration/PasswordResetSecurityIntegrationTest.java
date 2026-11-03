package com.danasea.backend.security.authentication.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaPasswordResetTokenRepository;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaRefreshTokenRepository;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.security.authentication.application.ports.AuthEventPublisher;
import com.danasea.backend.security.authentication.application.ports.PasswordHasher;
import com.danasea.backend.security.authentication.application.results.LoginResult;
import com.danasea.backend.security.authentication.application.usecases.ForgotPasswordUseCase;
import com.danasea.backend.security.authentication.application.usecases.LoginUseCase;
import com.danasea.backend.security.authentication.application.usecases.RefreshTokenUseCase;
import com.danasea.backend.security.authentication.application.usecases.ResetPasswordUseCase;
import com.danasea.backend.security.authentication.domain.events.PasswordResetRequestedEvent;
import com.danasea.backend.security.authentication.domain.exceptions.InvalidCredentialsException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpExpiredException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpInvalidException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpMaxAttemptsExceededException;
import com.danasea.backend.security.authentication.infrastructure.security.HashUtils;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;
import com.danasea.backend.security.authentication.infrastructure.security.JwtAuthenticationFilter;
import com.danasea.backend.security.authentication.presentation.AuthenticationController;
import com.danasea.backend.security.authentication.presentation.AuthenticationExceptionHandler;
import com.danasea.backend.security.authentication.presentation.filter.RateLimitFilter;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetSecurityIntegrationTest {

    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "reset_test").withEnv("POSTGRES_USER", "test")
            .withEnv("POSTGRES_PASSWORD", "test").withExposedPorts(5432);
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/reset_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @TestConfiguration
    static class RedisConfiguration {
        @Bean
        LettuceConnectionFactory resetConnectionFactory() {
            return new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory resetConnectionFactory) {
            return new StringRedisTemplate(resetConnectionFactory);
        }
    }

    @MockitoBean RabbitTemplate rabbitTemplate;
    @MockitoBean LettuceBasedProxyManager<byte[]> proxyManager;
    @MockitoBean AuthEventPublisher events;
    @MockitoBean AuditLogInternalApi audit;
    @Autowired AccountInternalApi account;
    @Autowired ForgotPasswordUseCase forgot;
    @Autowired ResetPasswordUseCase reset;
    @Autowired LoginUseCase login;
    @Autowired RefreshTokenUseCase refresh;
    @Autowired PasswordHasher hasher;
    @Autowired JpaPasswordResetTokenRepository tokens;
    @Autowired JpaRefreshTokenRepository refreshTokens;
    @Autowired JwtAuthenticationFilter jwtFilter;
    @Autowired CacheManager cacheManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtProperties jwtProperties;
    @Autowired AuthenticationController controller;
    @Autowired GlobalExceptionHandler globalHandler;
    @Autowired AuthenticationExceptionHandler authHandler;

    private final ConcurrentHashMap<UUID, PasswordResetRequestedEvent> messages = new ConcurrentHashMap<>();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        messages.clear();
        doAnswer(invocation -> {
            PasswordResetRequestedEvent event = invocation.getArgument(0);
            messages.put(event.userId(), event);
            return null;
        }).when(events).publishPasswordResetRequestedEvent(any());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(globalHandler, authHandler).build();
    }

    private User seed() {
        User user = new User();
        user.setEmail("reset-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(hasher.hash("OldPassword1!"));
        user.setRole(Role.CUSTOMER);
        user.setIsLocked(false);
        user.setIsEmailVerified(true);
        return account.saveUser(user);
    }

    private String issue(User user) {
        forgot.execute(user.getEmail(), SupportedLanguage.EN);
        return messages.get(user.getId()).otpCode();
    }

    private boolean authenticates(String jwt) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + jwt);
        AtomicBoolean result = new AtomicBoolean();
        SecurityContextHolder.clearContext();
        try {
            jwtFilter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    result.set(SecurityContextHolder.getContext().getAuthentication() != null));
            return result.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void forgotPersistsGeneratedIdHashExpiryAndPurposeSpecificToken() {
        User user = seed();
        String otp = issue(user);
        var token = account.findLatestActivePasswordResetToken(user.getId()).orElseThrow();
        assertThat(token.getId()).isNotNull();
        assertThat(token.getTokenHash()).isEqualTo(HashUtils.sha256(otp)).isNotEqualTo(otp);
        assertThat(token.getFailedAttempts()).isZero();
        assertThat(token.getExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(14));
        assertThat(messages.get(user.getId()).locale()).isEqualTo("en");
    }

    @Test
    void existingUnknownAndLockedEmailsHaveSameHttpResponse() throws Exception {
        User user = seed();
        String existing = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"unknown-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        user.setIsLocked(true);
        account.saveUser(user);
        String locked = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(existing).isEqualTo(unknown).isEqualTo(locked);
    }

    @Test
    void publisherFailureDoesNotRevealAccountExistence() throws Exception {
        User user = seed();
        doThrow(new IllegalStateException("Message broker unavailable"))
                .when(events).publishPasswordResetRequestedEvent(any());
        String existing = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"absent-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(existing).isEqualTo(unknown);
    }

    @Test
    void fifthWrongAttemptCommitsTokenInvalidationAndCorrectOtpIsRejected() {
        User user = seed();
        String otp = issue(user);
        UUID tokenId = account.findLatestActivePasswordResetToken(user.getId()).orElseThrow().getId();
        String wrong = otp.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> reset.execute(user.getEmail(), wrong, "NewPassword1!"))
                    .isInstanceOf(OtpInvalidException.class);
        }
        assertThatThrownBy(() -> reset.execute(user.getEmail(), wrong, "NewPassword1!"))
                .isInstanceOf(OtpMaxAttemptsExceededException.class);
        var token = tokens.findById(tokenId).orElseThrow();
        assertThat(token.getFailedAttempts()).isEqualTo(5);
        assertThat(token.getUsedAt()).isNotNull();
        assertThatThrownBy(() -> reset.execute(user.getEmail(), otp, "NewPassword1!"))
                .isInstanceOf(OtpExpiredException.class);
        assertThat(hasher.matches("OldPassword1!", account.findUserByEmailUncached(user.getEmail())
                .orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void newOtpStartsFreshAttemptsAndInvalidatesPreviousCode() {
        User user = seed();
        String old = issue(user);
        UUID oldId = account.findLatestActivePasswordResetToken(user.getId()).orElseThrow().getId();
        String wrong = old.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> reset.execute(user.getEmail(), wrong, "NewPassword1!"))
                .isInstanceOf(OtpInvalidException.class);
        String fresh = issue(user);
        assertThat(tokens.findById(oldId).orElseThrow().getUsedAt()).isNotNull();
        assertThat(account.findLatestActivePasswordResetToken(user.getId()).orElseThrow().getFailedAttempts()).isZero();
        reset.execute(user.getEmail(), fresh, "NewPassword1!");
    }

    @Test
    void expiredOtpDoesNotChangePasswordOrSessionVersion() {
        User user = seed();
        String otp = issue(user);
        var token = tokens.findAllByUserIdAndUsedAtIsNull(user.getId()).getFirst();
        token.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        tokens.saveAndFlush(token);
        assertThatThrownBy(() -> reset.execute(user.getEmail(), otp, "NewPassword1!"))
                .isInstanceOf(OtpExpiredException.class);
        assertThat(account.findUserByEmailUncached(user.getEmail()).orElseThrow().getSessionVersion()).isZero();
    }

    @Test
    void successfulResetRevokesAccessAndEveryRefreshFamilyButNewLoginWorks() throws Exception {
        User user = seed();
        LoginResult first = login.execute(user.getEmail(), "OldPassword1!");
        LoginResult second = login.execute(user.getEmail(), "OldPassword1!");
        assertThat(authenticates(first.accessToken())).isTrue();
        reset.execute(user.getEmail(), issue(user), "NewPassword1!");
        assertThat(authenticates(first.accessToken())).isFalse();
        assertThat(authenticates(second.accessToken())).isFalse();
        assertThatThrownBy(() -> refresh.execute(first.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> refresh.execute(second.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> login.execute(user.getEmail(), "OldPassword1!"))
                .isInstanceOf(InvalidCredentialsException.class);
        LoginResult current = login.execute(user.getEmail(), "NewPassword1!");
        assertThat(authenticates(current.accessToken())).isTrue();
        assertThat(authenticates(refresh.execute(current.refreshToken()).accessToken())).isTrue();
    }

    @Test
    void staleUserCacheCannotRestoreAnOldAccessToken() throws Exception {
        User stale = seed();
        LoginResult session = login.execute(stale.getEmail(), "OldPassword1!");
        reset.execute(stale.getEmail(), issue(stale), "NewPassword1!");
        cacheManager.getCache("usersByEmail").put(stale.getEmail(), stale);
        assertThat(authenticates(session.accessToken())).isFalse();
    }

    @Test
    void twoConcurrentRequestsCanConsumeOtpOnlyOnce() throws Exception {
        User user = seed();
        String otp = issue(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptReset(user, otp, "PasswordA1!", start));
            var second = pool.submit(() -> attemptReset(user, otp, "PasswordB1!", start));
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(account.findUserByEmailUncached(user.getEmail()).orElseThrow().getSessionVersion()).isEqualTo(1);
    }

    private boolean attemptReset(User user, String otp, String password, CountDownLatch start) throws Exception {
        start.await();
        try {
            reset.execute(user.getEmail(), otp, password);
            return true;
        } catch (OtpExpiredException | OtpInvalidException | OtpMaxAttemptsExceededException expected) {
            return false;
        }
    }

    @Test
    void concurrentForgotRequestsLeaveExactlyOneActiveToken() throws Exception {
        User user = seed();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); forgot.execute(user.getEmail(), SupportedLanguage.EN); return true; });
            var second = pool.submit(() -> { start.await(); forgot.execute(user.getEmail(), SupportedLanguage.EN); return true; });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(tokens.findAllByUserIdAndUsedAtIsNull(user.getId())).hasSize(1);
        reset.execute(user.getEmail(), messages.get(user.getId()).otpCode(), "NewPassword1!");
    }

    @Test
    void refreshRacingResetCannotLeaveAnOldSessionActive() throws Exception {
        User user = seed();
        LoginResult old = login.execute(user.getEmail(), "OldPassword1!");
        String otp = issue(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var resetRequest = pool.submit(() -> { start.await(); reset.execute(user.getEmail(), otp, "NewPassword1!"); return true; });
            var refreshRequest = pool.submit(() -> {
                start.await();
                try { return refresh.execute(old.refreshToken()); }
                catch (InvalidCredentialsException expected) { return null; }
            });
            start.countDown();
            assertThat(resetRequest.get(15, TimeUnit.SECONDS)).isTrue();
            LoginResult raced = refreshRequest.get(15, TimeUnit.SECONDS);
            if (raced != null) {
                assertThat(authenticates(raced.accessToken())).isFalse();
                assertThatThrownBy(() -> refresh.execute(raced.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
            }
        }
        assertThat(refreshTokens.findAllByUserId(user.getId())).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void loginRacingResetCannotLeaveAnOldPasswordSessionActive() throws Exception {
        User user = seed();
        String otp = issue(user);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var resetRequest = pool.submit(() -> { start.await(); reset.execute(user.getEmail(), otp, "NewPassword1!"); return true; });
            var loginRequest = pool.submit(() -> {
                start.await();
                try { return login.execute(user.getEmail(), "OldPassword1!"); }
                catch (InvalidCredentialsException expected) { return null; }
            });
            start.countDown();
            assertThat(resetRequest.get(15, TimeUnit.SECONDS)).isTrue();
            LoginResult raced = loginRequest.get(15, TimeUnit.SECONDS);
            if (raced != null) {
                assertThat(authenticates(raced.accessToken())).isFalse();
                assertThatThrownBy(() -> refresh.execute(raced.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
            }
        }
        assertThat(refreshTokens.findAllByUserId(user.getId())).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void failureRollsBackPasswordTokenConsumptionAndSessionRevocationTogether() {
        User user = seed();
        LoginResult old = login.execute(user.getEmail(), "OldPassword1!");
        String otp = issue(user);
        doThrow(new IllegalStateException("Audit unavailable")).when(audit).recordAuditLog(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> reset.execute(user.getEmail(), otp, "NewPassword1!"))
                .isInstanceOf(IllegalStateException.class);
        User unchanged = account.findUserByEmailUncached(user.getEmail()).orElseThrow();
        assertThat(hasher.matches("OldPassword1!", unchanged.getPasswordHash())).isTrue();
        assertThat(unchanged.getSessionVersion()).isZero();
        assertThat(account.findLatestActivePasswordResetToken(user.getId())).isPresent();
        assertThat(account.findRefreshTokenByHash(HashUtils.sha256(old.refreshToken())).orElseThrow().getRevokedAt()).isNull();
        doNothing().when(audit).recordAuditLog(any(), any(), any(), any(), any());
        reset.execute(user.getEmail(), otp, "NewPassword1!");
    }

    @Test
    void forgotPasswordIpLimitUsesRealRedisAndBlocksSixthRequest() throws Exception {
        RedisClient client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        try (var connection = client.connect(ByteArrayCodec.INSTANCE)) {
            var manager = LettuceBasedProxyManager.builderFor(connection)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(java.time.Duration.ofMinutes(1)))
                    .build();
            RateLimitFilter filter = new RateLimitFilter(manager);
            String ip = "review-" + UUID.randomUUID();
            AtomicBoolean reached = new AtomicBoolean();
            for (int i = 0; i < 6; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
                request.setRemoteAddr(ip);
                MockHttpServletResponse response = new MockHttpServletResponse();
                reached.set(false);
                filter.doFilter(request, response, (req, res) -> reached.set(true));
                assertThat(reached.get()).isEqualTo(i < 5);
                if (i == 5) {
                    assertThat(response.getStatus()).isEqualTo(429);
                    assertThat(response.getHeader("Retry-After")).isNotBlank();
                }
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    void refreshWaitingOnResetLockReadsCommittedRevocation() throws Exception {
        User user = seed();
        LoginResult old = login.execute(user.getEmail(), "OldPassword1!");
        String otp = issue(user);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = new TransactionTemplate(transactions).execute(status -> {
                account.findUserByIdForUpdate(user.getId()).orElseThrow();
                var request = pool.submit(() -> refresh.execute(old.refreshToken()));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Integer.class) == 0) {
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("Refresh did not wait for the account lock");
                    }
                    try { Thread.sleep(20); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                }
                reset.execute(user.getEmail(), otp, "NewPassword1!");
                return request;
            });
            assertThatThrownBy(() -> pending.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(refreshTokens.findAllByUserId(user.getId()))
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void legacyJwtWithoutVersionWorksOnlyUntilFirstReset() throws Exception {
        User user = seed();
        String legacy = io.jsonwebtoken.Jwts.builder().subject(user.getEmail())
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(600)))
                .signWith(jwtProperties.secretKey()).compact();
        assertThat(authenticates(legacy)).isTrue();
        reset.execute(user.getEmail(), issue(user), "NewPassword1!");
        assertThat(authenticates(legacy)).isFalse();
    }

    @Test
    void staleProfileSaveCannotRestorePasswordOrLowerSessionVersion() {
        User stale = seed();
        reset.execute(stale.getEmail(), issue(stale), "NewPassword1!");
        stale.setFullName("Updated profile");
        User saved = account.saveUser(stale);
        assertThat(saved.getFullName()).isEqualTo("Updated profile");
        assertThat(saved.getSessionVersion()).isEqualTo(1);
        assertThat(hasher.matches("NewPassword1!", saved.getPasswordHash())).isTrue();
    }
}
