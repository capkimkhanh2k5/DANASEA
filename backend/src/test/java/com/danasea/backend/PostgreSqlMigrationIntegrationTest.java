package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationIntegrationTest {

    @Test
    void commissionDefaultsToTenPercentWithoutReplacingAnExistingRate() throws Exception {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/migration_test";
        waitForPostgres(url, "migration_user", "migration_password");
        for (boolean existing : new boolean[] {false, true}) {
            String schema = existing ? "commission_existing" : "commission_missing";
            Flyway.configure().dataSource(url, "migration_user", "migration_password")
                    .schemas(schema).defaultSchema(schema).target("42").load().migrate();
            try (var connection = DriverManager.getConnection(url, "migration_user", "migration_password");
                    var statement = connection.createStatement()) {
                if (existing) {
                    statement.executeUpdate("INSERT INTO " + schema
                            + ".system_configs(id,key,value,created_at,updated_at) VALUES "
                            + "(gen_random_uuid(),'PLATFORM_COMMISSION_RATE','12.5',now(),now())");
                }
                Flyway.configure().dataSource(url, "migration_user", "migration_password")
                        .schemas(schema).defaultSchema(schema).load().migrate();
                try (var result = statement.executeQuery("SELECT value FROM " + schema
                        + ".system_configs WHERE key='PLATFORM_COMMISSION_RATE'")) {
                    assertTrue(result.next(), "Missing commission must be initialized during migration");
                    assertEquals(existing ? "12.5" : "10", result.getString(1));
                    assertTrue(!result.next(), "There must be only one commission policy");
                }
            }
        }
    }

    @Container
    static final GenericContainer<?> POSTGRES =
            new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withEnv("POSTGRES_DB", "migration_test")
                    .withEnv("POSTGRES_USER", "migration_user")
                    .withEnv("POSTGRES_PASSWORD", "migration_password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    private static void waitForPostgres(String url, String user, String password) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        Exception lastEx = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return;
            } catch (Exception e) {
                lastEx = e;
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("PostgreSQL did not become ready in time: " + (lastEx != null ? lastEx.getMessage() : "timeout"), lastEx);
    }

    @Test
    void allMigrationsApplyAndValidateOnPostgreSql16() throws Exception {
        String jdbcUrl =
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getFirstMappedPort()
                        + "/migration_test";
        waitForPostgres(jdbcUrl, "migration_user", "migration_password");

        Flyway flyway =
                Flyway.configure()
                        .dataSource(jdbcUrl, "migration_user", "migration_password")
                        .locations("classpath:db/migration")
                        .connectRetries(30)
                        .load();

        Flyway legacy =
                Flyway.configure()
                        .dataSource(jdbcUrl, "migration_user", "migration_password")
                        .locations("classpath:db/migration")
                        .connectRetries(30)
                        .target("17")
                        .load();
        legacy.migrate();
        java.util.UUID legacyPayment = java.util.UUID.randomUUID();
        try (var connection =
                        DriverManager.getConnection(
                                jdbcUrl, "migration_user", "migration_password");
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO payments(id, status, created_at, updated_at) VALUES"
                                    + " (?, 'REFUNDED', TIMESTAMPTZ '2026-09-01 10:00:00+07',"
                                    + " TIMESTAMPTZ '2026-09-02 10:00:00+07')")) {
            statement.setObject(1, legacyPayment);
            statement.executeUpdate();
        }
        var migrationResult = flyway.migrate();
        assertTrue(migrationResult.success);
        assertNotNull(flyway.info().current());
        var latestVersion =
                Arrays.stream(flyway.info().all())
                        .map(MigrationInfo::getVersion)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
        assertEquals(latestVersion, flyway.info().current().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (var connection =
                        DriverManager.getConnection(
                                jdbcUrl, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            try (var row =
                    statement.executeQuery(
                            "SELECT paid_at IS NOT NULL FROM payments WHERE id = '"
                                    + legacyPayment
                                    + "'")) {
                assertTrue(row.next() && row.getBoolean(1));
            }
            assertTrue(tableExists(statement, "refunds"));
            assertTrue(tableExists(statement, "settlements"));
            assertTrue(tableExists(statement, "checkin_tokens"));
            assertTrue(tableExists(statement, "category_safety_rules"));
            assertTrue(tableExists(statement, "ai_itineraries"));
            assertTrue(tableExists(statement, "ai_assessment_cases"));
        }
    }

    @Test
    void v19ReconcilesExpiredHoldsAndReleasedPrivateItemsWithoutLosingConfirmedSeats()
            throws Exception {
        String url =
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getFirstMappedPort()
                        + "/migration_test";
        Flyway.configure()
                .dataSource(url, "migration_user", "migration_password")
                .schemas("inventory_upgrade")
                .defaultSchema("inventory_upgrade")
                .locations("classpath:db/migration")
                .target("18")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO inventory_upgrade");
            statement.executeUpdate(
                    """
INSERT INTO users(id, role, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001','CUSTOMER',NOW(),NOW());
INSERT INTO services(id,name,price,status,created_at,updated_at)
VALUES ('00000000-0000-0000-0000-000000000002','Upgrade test',100,'PUBLISHED',NOW(),NOW());
INSERT INTO service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-00000000000' || n)::uuid,
       '00000000-0000-0000-0000-000000000002',DATE '2030-01-01',
       TIME '08:00' + n * INTERVAL '1 hour', TIME '09:00' + n * INTERVAL '1 hour',
       10, CASE n WHEN 3 THEN 4 WHEN 4 THEN 9 ELSE 6 END,
       'OPEN','SHARED_CAPACITY_UNITS',NOW(),NOW()
FROM generate_series(3,5) n;
INSERT INTO service_slot_units(id,slot_id,unit_number,capacity,booked_count,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-00000000001' || n)::uuid,
       ('00000000-0000-0000-0000-00000000000' || n)::uuid,1,10,
       CASE n WHEN 3 THEN 4 WHEN 4 THEN 10 ELSE 6 END,NOW(),NOW()
FROM generate_series(3,5) n;
INSERT INTO bookings(id,customer_id,status,total_amount,hold_expires_at,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-00000000002' || n)::uuid,
       '00000000-0000-0000-0000-000000000001',
       CASE n WHEN 3 THEN 'HOLD' ELSE 'CONFIRMED' END,1000,NOW()-INTERVAL '1 minute',NOW(),NOW()
FROM generate_series(3,5) n;
INSERT INTO booking_items(id,booking_id,service_id,vendor_id,slot_id,quantity,booking_date,booking_time,price,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-00000000003' || n)::uuid,
       ('00000000-0000-0000-0000-00000000002' || n)::uuid,
       '00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001',
       ('00000000-0000-0000-0000-00000000000' || n)::uuid,
       CASE n WHEN 3 THEN 4 WHEN 4 THEN 1 ELSE 6 END,DATE '2030-01-01',TIME '08:00',100,NOW(),NOW()
FROM generate_series(3,5) n;
INSERT INTO booking_item_allocations(id,booking_item_id,slot_id,unit_number,allocated_seats,is_private_lock,created_at,updated_at)
SELECT ('00000000-0000-0000-0000-00000000004' || n)::uuid,
       ('00000000-0000-0000-0000-00000000003' || n)::uuid,
       ('00000000-0000-0000-0000-00000000000' || n)::uuid,1,
       CASE n WHEN 3 THEN 4 WHEN 4 THEN 10 ELSE 6 END,n=4,NOW(),NOW()
FROM generate_series(3,5) n;
INSERT INTO master_orders(id,booking_id,status,created_at,updated_at)
VALUES ('00000000-0000-0000-0000-000000000050','00000000-0000-0000-0000-000000000024','PAID',NOW(),NOW());
INSERT INTO sub_orders(id,master_order_id,booking_item_id,status,created_at,updated_at)
VALUES ('00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000050',
        '00000000-0000-0000-0000-000000000034','REJECTED',NOW(),NOW());
""");
        }
        Flyway upgrade =
                Flyway.configure()
                        .dataSource(url, "migration_user", "migration_password")
                        .schemas("inventory_upgrade")
                        .defaultSchema("inventory_upgrade")
                        .locations("classpath:db/migration")
                        .target("19")
                        .load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertTrue(upgrade.validateWithResult().validationSuccessful);
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO inventory_upgrade");
            try (var rows =
                    statement.executeQuery(
                            "SELECT booked_count FROM service_slot_units ORDER BY slot_id")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT SUM(booked_count) FROM service_slots")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
            }
            try (var rows =
                    statement.executeQuery(
                            "SELECT capacity_released FROM booking_items WHERE"
                                + " id='00000000-0000-0000-0000-000000000034'")) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
            }
        }
    }

    @Test
    void newModulesUpgradeCurrentMainSchemaWithoutLosingNotifications() throws Exception {
        String url =
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getFirstMappedPort()
                        + "/migration_test";
        Flyway baseline =
                Flyway.configure()
                        .dataSource(url, "migration_user", "migration_password")
                        .schemas("current_main_upgrade")
                        .defaultSchema("current_main_upgrade")
                        .locations("classpath:db/migration")
                        .target("27")
                        .load();
        baseline.migrate();
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO current_main_upgrade");
            statement.executeUpdate(
                    """
INSERT INTO users(id, role, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'CUSTOMER', NOW(), NOW());
INSERT INTO notifications(id, user_id, type, channel, title, body, locale, status,
        is_read, idempotency_key, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
        'PAYMENT_SUCCESS', 'IN_APP', 'Preserved notification', 'Preserved body',
        'en', 'SENT', true, 'upgrade-existing', NOW(), NOW());
""");
        }
        Flyway upgraded =
                Flyway.configure()
                        .dataSource(url, "migration_user", "migration_password")
                        .schemas("current_main_upgrade")
                        .defaultSchema("current_main_upgrade")
                        .locations("classpath:db/migration")
                        .load();
        assertTrue(upgraded.migrate().success);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        assertTrue(
                upgraded.info().current().getVersion().compareTo(MigrationVersion.fromVersion("27"))
                        > 0);
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement();
                var row =
                        statement.executeQuery(
                                """
SELECT is_read, idempotency_key FROM current_main_upgrade.notifications
WHERE id = '10000000-0000-0000-0000-000000000002'
""")) {
            assertTrue(row.next());
            assertTrue(row.getBoolean(1));
            assertEquals("upgrade-existing", row.getString(2));
        }
    }

    private boolean tableExists(java.sql.Statement statement, String tableName) throws Exception {
        try (var resultSet =
                statement.executeQuery(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name = '"
                                + tableName
                                + "')")) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    @Test
    void v37PreservesPaidPolicyAndBackfillsOnlyUnresolvedDecisions() throws Exception {
        String url =
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getFirstMappedPort()
                        + "/migration_test";
        Flyway.configure()
                .dataSource(url, "migration_user", "migration_password")
                .schemas("cancellation_upgrade")
                .defaultSchema("cancellation_upgrade")
                .locations("classpath:db/migration")
                .target("36")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO cancellation_upgrade");
            statement.executeUpdate(
                    """
INSERT INTO users(id,role,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000001','CUSTOMER',NOW(),NOW()),
('37000000-0000-0000-0000-000000000002','VENDOR',NOW(),NOW());
INSERT INTO vendors(id,user_id,business_name,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000003','37000000-0000-0000-0000-000000000002','Upgrade vendor',NOW(),NOW());
INSERT INTO services(id,name,price,status,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000004','Upgrade service',12345,'PUBLISHED',NOW(),NOW());
INSERT INTO service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000005','37000000-0000-0000-0000-000000000004','2030-01-02','08:00','09:00',10,1,'OPEN',NOW(),NOW()),
('37000000-0000-0000-0000-000000000006','37000000-0000-0000-0000-000000000004','2030-01-03','08:00','09:00',10,0,'OPEN',NOW(),NOW());
INSERT INTO master_orders(id,customer_id,status,payment_status,total_amount,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000007','37000000-0000-0000-0000-000000000001','PAID','PAID',12345,NOW(),NOW()),
('37000000-0000-0000-0000-000000000008','37000000-0000-0000-0000-000000000001','PENDING_PAYMENT','UNPAID',12345,NOW(),NOW());
INSERT INTO sub_orders(id,master_order_id,vendor_id,service_id,slot_id,quantity,subtotal_amount,final_amount,status,created_at,updated_at) VALUES
('37000000-0000-0000-0000-000000000009','37000000-0000-0000-0000-000000000007','37000000-0000-0000-0000-000000000003','37000000-0000-0000-0000-000000000004','37000000-0000-0000-0000-000000000005',1,12345,12345,'CONFIRMED',NOW(),NOW()),
('37000000-0000-0000-0000-00000000000a','37000000-0000-0000-0000-000000000008','37000000-0000-0000-0000-000000000003','37000000-0000-0000-0000-000000000004','37000000-0000-0000-0000-000000000005',1,12345,12345,'PENDING',NOW(),NOW());
INSERT INTO sub_order_reschedule_proposals(id,sub_order_id,vendor_id,proposed_slot_id,reason,reason_type,status,expires_at) VALUES
('37000000-0000-0000-0000-00000000000b','37000000-0000-0000-0000-000000000009','37000000-0000-0000-0000-000000000003','37000000-0000-0000-0000-000000000006','Unable to operate','OPERATIONAL','PENDING','2030-01-02 08:00:00+07');
""");
        }
        var upgraded =
                Flyway.configure()
                        .dataSource(url, "migration_user", "migration_password")
                        .schemas("cancellation_upgrade")
                        .defaultSchema("cancellation_upgrade")
                        .locations("classpath:db/migration")
                        .load();
        assertTrue(upgraded.migrate().success);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        try (var connection =
                        DriverManager.getConnection(url, "migration_user", "migration_password");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO cancellation_upgrade");
            try (var row =
                    statement.executeQuery(
                            "SELECT cancellation_policy_version,final_amount,status FROM sub_orders"
                                + " WHERE id='37000000-0000-0000-0000-000000000009'")) {
                assertTrue(row.next());
                assertEquals("LEGACY_TIERED_V1", row.getString(1));
                assertEquals(
                        0, row.getBigDecimal(2).compareTo(java.math.BigDecimal.valueOf(12345)));
                assertEquals("CONFIRMED", row.getString(3));
            }
            try (var row =
                    statement.executeQuery(
                            "SELECT cancellation_policy_version FROM sub_orders WHERE"
                                + " id='37000000-0000-0000-0000-00000000000a'")) {
                assertTrue(row.next());
                assertEquals("STANDARD_24H_V2", row.getString(1));
            }
            try (var row =
                    statement.executeQuery(
                            "SELECT d.reason,d.decision_deadline=d.original_departure_at-INTERVAL"
                                + " '12 hours',p.expires_at=d.decision_deadline FROM"
                                + " sub_order_disruptions d JOIN sub_order_reschedule_proposals p"
                                + " ON p.id=d.id")) {
                assertTrue(row.next());
                assertEquals("VENDOR_FAULT", row.getString(1));
                assertTrue(row.getBoolean(2));
                assertTrue(row.getBoolean(3));
            }
        }
    }
}
