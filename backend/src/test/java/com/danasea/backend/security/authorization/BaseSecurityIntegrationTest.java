package com.danasea.backend.security.authorization;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.danasea.backend.configs.AbstractPostgresIntegrationTest;

public abstract class BaseSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Every context shares this database: a test profile must never recreate its schema.
        configurePostgresProperties(registry, SHARED_DATABASE);
    }
}
