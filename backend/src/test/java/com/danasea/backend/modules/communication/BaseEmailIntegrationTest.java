package com.danasea.backend.modules.communication;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

import com.danasea.backend.configs.AbstractPostgresIntegrationTest;

abstract class BaseEmailIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String DATABASE_PREFIX = "email_it_";

    @Autowired
    private JdbcTemplate jdbc;

    static void registerEmailDatabase(DynamicPropertyRegistry registry, String suite) {
        String database = DATABASE_PREFIX + suite + "_" + UUID.randomUUID().toString().replace("-", "");
        createPostgresDatabase(database);
        configurePostgresProperties(registry, database);
    }

    @BeforeEach
    void resetEmailFixtures() {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!database.startsWith(DATABASE_PREFIX)) {
            throw new IllegalStateException("Refusing to reset email fixtures in database " + database);
        }

        // PostgreSQL clears dependent tables together while keeping their constraints active.
        jdbc.execute("""
                TRUNCATE TABLE transactional_email_delivery, users, vendors, categories,
                    services, service_slots, master_orders, sub_orders, refunds, payments,
                    discount_codes, discount_redemptions, settlements, reviews,
                    safety_rule_evaluations, password_reset_tokens CASCADE
                """);
    }
}
