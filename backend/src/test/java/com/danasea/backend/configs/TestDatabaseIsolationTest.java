package com.danasea.backend.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.danasea.backend.modules.communication.infrastructure.persistence.entities.ConversationJpaEntity;

class TestDatabaseIsolationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=test")
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
            .withUserConfiguration(PersistenceConfiguration.class);

    @Test
    void startingAnotherContextDoesNotEraseExistingConversations() {
        runner.run(first -> {
            assertThat(first).hasNotFailed();
            var jdbc = new JdbcTemplate(first.getBean(DataSource.class));
            jdbc.update("insert into conversations(id, created_at, updated_at) values (?, current_timestamp, current_timestamp)",
                    UUID.randomUUID());

            runner.run(second -> {
                assertThat(second).hasNotFailed();
                var other = new JdbcTemplate(second.getBean(DataSource.class));
                assertThat(other.queryForObject("select count(*) from conversations", Long.class)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from conversations", Long.class)).isEqualTo(1);
            });
        });
    }

    @Test
    void closingAnotherContextDoesNotDropTheSurvivingSchema() {
        runner.run(first -> {
            assertThat(first).hasNotFailed();
            var jdbc = new JdbcTemplate(first.getBean(DataSource.class));

            runner.run(second -> assertThat(second).hasNotFailed());

            assertThatCode(() -> jdbc.queryForObject("select count(*) from conversations", Long.class))
                    .doesNotThrowAnyException();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ConversationJpaEntity.class)
    static class PersistenceConfiguration {
    }
}
