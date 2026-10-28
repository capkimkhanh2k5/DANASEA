package com.danasea.backend.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Transactional
class SharedSecuritySchemaIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testProfilePreservesFlywayUserDefaultsInTheSharedDatabase() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users(id, email, role, is_locked, created_at, updated_at)
                values (?, ?, 'CUSTOMER', false, now(), now())
                """, id, id + "@example.test");

        assertThat(jdbc.queryForObject("select locale from users where id = ?", String.class, id))
                .isEqualTo("vi");
        assertThat(jdbc.queryForObject("select session_version from users where id = ?", Long.class, id))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '11' and success", Integer.class))
                .isEqualTo(1);
    }
}
