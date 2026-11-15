package com.danasea.backend.modules.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.configs.AbstractPostgresIntegrationTest;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.UpdateCommissionRuleRequest;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.UpdateConfigRequest;
import com.danasea.backend.modules.order.infrastructure.jobs.ExchangeRateStartupInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class AdminConfigAuditIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String DATABASE = "admin_config_audit_test";
    private static final UUID FIRST_ADMIN = UUID.randomUUID();
    private static final UUID SECOND_ADMIN = UUID.randomUUID();

    @Autowired
    private AdminSystemConfigController controller;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ExchangeRateStartupInitializer exchangeRateStartupInitializer;

    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        createPostgresDatabase(DATABASE);
        configurePostgresProperties(registry, DATABASE);
    }

    @Test
    void concurrentConfigEditsAuditTheCommittedPredecessor() throws Exception {
        String key = "FLASH_SALE_MAX_SUBSIDY_RATIO";
        UUID configId = UUID.randomUUID();
        jdbc.update("INSERT INTO system_configs(id,key,value,created_at,updated_at) VALUES (?,?,?,now(),now())",
                configId, key, "0.8");

        overlapTransactions(
                () -> controller.updateConfig(key, new UpdateConfigRequest("0.7", null)),
                () -> controller.updateConfig(key, new UpdateConfigRequest("0.6", null)));

        JsonNode first = auditMetadata("SYSTEM_CONFIG_UPDATED", configId, FIRST_ADMIN);
        JsonNode second = auditMetadata("SYSTEM_CONFIG_UPDATED", configId, SECOND_ADMIN);
        assertThat(first.path("previousValue").asText()).isEqualTo("0.8");
        assertThat(first.path("newValue").asText()).isEqualTo("0.7");
        assertThat(second.path("previousValue").asText()).isEqualTo("0.7");
        assertThat(second.path("newValue").asText()).isEqualTo("0.6");
        assertThat(jdbc.queryForObject("SELECT value FROM system_configs WHERE id=?", String.class, configId))
                .isEqualTo("0.6");
    }

    @Test
    void concurrentFirstWritesShareOneConfigAndBothAreAudited() throws Exception {
        String key = "FLASH_SALE_ESTIMATED_UNIT_COST";

        overlapTransactions(
                () -> controller.updateConfig(key, new UpdateConfigRequest("5000", null)),
                () -> controller.updateConfig(key, new UpdateConfigRequest("6000", null)));

        List<UUID> ids = jdbc.queryForList("SELECT id FROM system_configs WHERE key=?", UUID.class, key);
        assertThat(ids).hasSize(1);
        UUID configId = ids.getFirst();
        JsonNode first = auditMetadata("SYSTEM_CONFIG_UPDATED", configId, FIRST_ADMIN);
        JsonNode second = auditMetadata("SYSTEM_CONFIG_UPDATED", configId, SECOND_ADMIN);
        assertThat(first.path("previousValue").isNull()).isTrue();
        assertThat(first.path("newValue").asText()).isEqualTo("5000");
        assertThat(second.path("previousValue").asText()).isEqualTo("5000");
        assertThat(second.path("newValue").asText()).isEqualTo("6000");
    }

    @Test
    void concurrentCommissionEditsKeepPredecessorAndUnchangedFields() throws Exception {
        UUID ruleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commission_rules (id,rule_code,rule_name,target_type,commission_rate,duration_days)
                VALUES (?,'ONBOARDING_AUDIT_FIXTURE','Onboarding fixture','ONBOARDING',0.05,30)
                """, ruleId);

        overlapTransactions(
                () -> controller.updateCommissionRule(ruleId,
                        new UpdateCommissionRuleRequest(new BigDecimal("7"), 60, null, null)),
                () -> controller.updateCommissionRule(ruleId,
                        new UpdateCommissionRuleRequest(new BigDecimal("8"), null, false, null)));

        JsonNode first = auditMetadata("COMMISSION_RULE_UPDATED", ruleId, FIRST_ADMIN);
        JsonNode second = auditMetadata("COMMISSION_RULE_UPDATED", ruleId, SECOND_ADMIN);
        assertThat(first.path("previousRate").decimalValue()).isEqualByComparingTo("5");
        assertThat(first.path("newRate").decimalValue()).isEqualByComparingTo("7");
        assertThat(second.path("previousRate").decimalValue()).isEqualByComparingTo("7");
        assertThat(second.path("newRate").decimalValue()).isEqualByComparingTo("8");
        assertThat(second.path("previousDurationDays").asInt()).isEqualTo(60);
        assertThat(second.path("durationDays").asInt()).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT duration_days FROM commission_rules WHERE id=?", Integer.class, ruleId))
                .isEqualTo(60);
    }

    private JsonNode auditMetadata(String action, UUID entityId, UUID actorId) throws Exception {
        String metadata = jdbc.queryForObject("""
                SELECT metadata FROM audit_logs WHERE action=? AND entity_id=? AND actor_user_id=?
                """, String.class, action, entityId, actorId);
        return json.readTree(metadata);
    }

    private void overlapTransactions(Runnable firstUpdate, Runnable secondUpdate) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> runAs(FIRST_ADMIN, () -> {
                firstUpdate.run();
                firstPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                try {
                    assertThat(releaseFirst.await(15, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            })));

            try {
                int blockingPid = firstPid.get(5, TimeUnit.SECONDS);
                var second = executor.submit(() -> transaction.executeWithoutResult(status -> runAs(SECOND_ADMIN, () -> {
                    secondPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    secondUpdate.run();
                })));
                int waitingPid = secondPid.get(5, TimeUnit.SECONDS);
                await().atMost(Duration.ofSeconds(5)).until(() -> second.isDone() || Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid)));
                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private void runAs(UUID actorId, Runnable update) {
        var auth = new UsernamePasswordAuthenticationToken(
                actorId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            update.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
