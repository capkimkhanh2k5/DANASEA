package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Testcontainers
class PostgreSqlMigrationRehearsalTest {

    @Container
    static final GenericContainer<?> POSTGRES =
            new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withEnv("POSTGRES_DB", "rehearsal_test")
                    .withEnv("POSTGRES_USER", "rehearsal_user")
                    .withEnv("POSTGRES_PASSWORD", "rehearsal_password")
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
        throw new IllegalStateException("PostgreSQL did not become ready in time: "
                + (lastEx != null ? lastEx.getMessage() : "timeout"), lastEx);
    }

    @Test
    void rehearseCoordinateMigrationAndVerifiedRecovery() throws Exception {
        int rows = Integer.getInteger("migration.rehearsal.rows", 10000);
        double maxLockSeconds = Double.parseDouble(
                System.getProperty("migration.rehearsal.max-lock-seconds", "5.0"));
        double maxMigrationSeconds = Double.parseDouble(
                System.getProperty("migration.rehearsal.max-migration-seconds", "60.0"));
        String outputPath = System.getProperty("migration.rehearsal.output");

        String jdbcUrl = "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getFirstMappedPort()
                + "/rehearsal_test";
        waitForPostgres(jdbcUrl, "rehearsal_user", "rehearsal_password");

        String pgVersion;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            assertTrue(rs.next());
            pgVersion = rs.getString(1);
        }

        String schema = "rehearsal_v44";
        String restoreDatabase = "rehearsal_v44_restored";
        assertTrue(rows > 0 && Double.isFinite(maxLockSeconds) && maxLockSeconds > 0
                && Double.isFinite(maxMigrationSeconds) && maxMigrationSeconds > 0);

        // Step 1: Migrate up to target 44
        Flyway flywayV44 = Flyway.configure()
                .dataSource(jdbcUrl, "rehearsal_user", "rehearsal_password")
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("44")
                .connectRetries(30)
                .load();
        flywayV44.migrate();

        UUID vendorUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID vendorId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID categoryId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID referenceServiceId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        // Step 2: Seed baseline data with full FKs
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + schema);

            stmt.executeUpdate("INSERT INTO users(id, email, password_hash, full_name, role, is_email_verified, is_locked, created_at, updated_at) "
                    + "VALUES ('" + vendorUserId + "', 'rehearsal_vendor@example.invalid', 'unused', 'Rehearsal Vendor', 'VENDOR', true, false, now(), now())");

            stmt.executeUpdate("INSERT INTO vendors(id, user_id, business_name, verification_status, created_at, updated_at) "
                    + "VALUES ('" + vendorId + "', '" + vendorUserId + "', 'Rehearsal Vendor', 'APPROVED', now(), now())");

            stmt.executeUpdate("INSERT INTO categories(id, name, slug, is_active, requires_safety_cert, created_at, updated_at) "
                    + "VALUES ('" + categoryId + "', 'Rehearsal Category', 'rehearsal-category', true, false, now(), now())");

            // Seed reference service with 2 decimal digits
            stmt.executeUpdate("INSERT INTO services(id, vendor_id, category_id, name, slug, status, price, weather_sensitive, view_count, waiver_required, latitude, longitude, created_at, updated_at) "
                    + "VALUES ('" + referenceServiceId + "', '" + vendorId + "', '" + categoryId + "', 'Reference Service', 'reference-service', 'PUBLISHED', 500000, false, 0, false, 16.12, 108.26, now(), now())");

            // Seed remaining rows using generate_series (fast batch)
            int bulkRows = Math.max(0, rows - 1);
            if (bulkRows > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO services(id, vendor_id, category_id, name, slug, status, price, weather_sensitive, view_count, waiver_required, latitude, longitude, created_at, updated_at) "
                                + "SELECT gen_random_uuid(), ?, ?, 'Rehearsal Service ' || n, 'rehearsal-service-' || n, 'PUBLISHED', 200000, false, 0, false, 16.05, 108.24, now(), now() "
                                + "FROM generate_series(1, ?) n")) {
                    ps.setObject(1, vendorId);
                    ps.setObject(2, categoryId);
                    ps.setInt(3, bulkRows);
                    ps.executeUpdate();
                }
            }
        }


        Snapshot baseline;
        String coordinateHash;
        long tableBytes;
        try (Connection conn = connect(jdbcUrl)) {
            baseline = snapshot(conn, schema);
            coordinateHash = coordinateHash(conn, schema);
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                    "SELECT pg_total_relation_size('" + schema + ".services')")) {
                assertTrue(rs.next());
                tableBytes = rs.getLong(1);
            }
        }
        checkedExec("pg_dump", "-U", "rehearsal_user", "-d", "rehearsal_test", "-Fc",
                "--no-owner", "--no-privileges", "-f", "/tmp/rehearsal.dump");
        String backupHash = checkedExec("sha256sum", "/tmp/rehearsal.dump").split(" ")[0];
        double actualMigrationSeconds;
        double actualLockSeconds;
        try (Connection blocker = connect(jdbcUrl); var executor = Executors.newFixedThreadPool(3)) {
            blocker.setAutoCommit(false);
            try (Statement stmt = blocker.createStatement()) {
                stmt.execute("LOCK TABLE " + schema + ".services IN ACCESS SHARE MODE");
            }
            long migrationStart = System.nanoTime();
            var migration = executor.submit(() -> Flyway.configure()
                    .dataSource(jdbcUrl, "rehearsal_user", "rehearsal_password").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("45")
                    .initSql("SET lock_timeout = '" + (long)(maxLockSeconds * 1000) + "ms'; SET statement_timeout = '"
                            + (long)(maxMigrationSeconds * 1000) + "ms'")
                    .load().migrate());
            awaitWaitingLocks(jdbcUrl, schema, 1);
            CountDownLatch attempted = new CountDownLatch(2);
            var reader = executor.submit(() -> blockedOperation(jdbcUrl,
                    "SELECT count(*) FROM " + schema + ".services", attempted, false));
            var writer = executor.submit(() -> blockedOperation(jdbcUrl,
                    "UPDATE " + schema + ".services SET view_count=view_count+1 WHERE id='" + referenceServiceId + "'", attempted, true));
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            // Observe the migration, reader and writer blocked in PostgreSQL, not merely submitted futures.
            awaitWaitingLocks(jdbcUrl, schema, 3);
            blocker.commit();
            migration.get((long)Math.ceil(maxMigrationSeconds) + 5, TimeUnit.SECONDS);
            actualMigrationSeconds = (System.nanoTime() - migrationStart) / 1_000_000_000.0;
            actualLockSeconds = Math.max(reader.get(10, TimeUnit.SECONDS), writer.get(10, TimeUnit.SECONDS));
        }
        assertTrue(actualMigrationSeconds <= maxMigrationSeconds, "Migration duration exceeded budget");
        assertTrue(actualLockSeconds > 0 && actualLockSeconds <= maxLockSeconds, "Observed query blocking exceeded budget");
        try (Connection conn = connect(jdbcUrl)) {
            assertEquals(coordinateHash, coordinateHash(conn, schema), "Every existing coordinate must survive migration");
            Map<String, Long> expectedCounts = new TreeMap<>(baseline.counts());
            expectedCounts.compute("flyway_schema_history", (key, count) -> count + 1);
            assertEquals(expectedCounts, snapshot(conn, schema).counts(), "Only the V45 history row may be added");
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema
                            + "' AND table_name='services' AND column_name IN ('latitude','longitude')"
                            + " AND numeric_precision=46 AND numeric_scale=10")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (PreparedStatement update = conn.prepareStatement("UPDATE " + schema
                    + ".services SET latitude=?,longitude=? WHERE id=?")) {
                update.setBigDecimal(1, new BigDecimal("16.0543219876"));
                update.setBigDecimal(2, new BigDecimal("108.2412345678"));
                update.setObject(3, referenceServiceId);
                assertEquals(1, update.executeUpdate());
            }
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                    "SELECT latitude,longitude FROM " + schema + ".services WHERE id='" + referenceServiceId + "'")) {
                assertTrue(rs.next());
                assertEquals(new BigDecimal("16.0543219876"), rs.getBigDecimal(1));
                assertEquals(new BigDecimal("108.2412345678"), rs.getBigDecimal(2));
            }
        }
        assertEquals(backupHash, checkedExec("sha256sum", "/tmp/rehearsal.dump").split(" ")[0]);
        checkedExec("createdb", "-U", "rehearsal_user", restoreDatabase);
        checkedExec("pg_restore", "-U", "rehearsal_user", "--no-owner", "--no-privileges", "--exit-on-error",
                "-d", restoreDatabase, "/tmp/rehearsal.dump");
        String restoreUrl = jdbcUrl.replace("/rehearsal_test", "/" + restoreDatabase);
        Flyway restored = Flyway.configure().dataSource(restoreUrl, "rehearsal_user", "rehearsal_password")
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("44").load();
        assertTrue(restored.validateWithResult().validationSuccessful);
        try (Connection conn = connect(restoreUrl)) {
            assertEquals(baseline, snapshot(conn, schema), "Restored full data/counts/Flyway history must match pre-migration database");
            try (Statement stmt = conn.createStatement()) {
                assertEquals(rows, stmt.executeUpdate("UPDATE " + schema + ".services SET price=1,name='CORRUPTED'"));
            }
            assertThrows(AssertionError.class, () -> assertEquals(baseline, snapshot(conn, schema)),
                    "Recovery checker must detect corruption even when counts and coordinates still match");
        }
        if (outputPath != null && !outputPath.isBlank()) {
            File out = new File(outputPath);
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("run_id", System.getProperty("migration.rehearsal.run-id"));
            evidence.put("rows", rows);
            evidence.put("postgres_version", pgVersion);
            evidence.put("table_bytes", tableBytes);
            evidence.put("actual_lock_seconds", actualLockSeconds);
            evidence.put("actual_migration_seconds", actualMigrationSeconds);
            evidence.put("numeric_precision", 46);
            evidence.put("numeric_scale", 10);
            evidence.put("backup_hash", backupHash);
            evidence.put("restored_data_hash", baseline.hash());
            evidence.put("table_counts", baseline.counts());
            evidence.put("baseline_preserved", true);
            evidence.put("high_precision_verified", true);
            evidence.put("restore_verified", true);
            evidence.put("corruption_detection_verified", true);
            evidence.put("reader_queries", 1);
            evidence.put("writer_updates", 1);
            evidence.put("baseline_application_started", false);
            evidence.put("release_ready", false);
            evidence.put("passed", true);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, evidence);
        }
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, "rehearsal_user", "rehearsal_password");
    }

    private static String checkedExec(String... command) throws Exception {
        var result = POSTGRES.execInContainer(command);
        assertEquals(0, result.getExitCode(), "Database backup/restore command failed: " + result.getStderr());
        return result.getStdout().trim();
    }

    private record Snapshot(Map<String, Long> counts, String hash) { }

    private static Snapshot snapshot(Connection conn, String schema) throws Exception {
        Map<String, Long> counts = new TreeMap<>();
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname='" + schema + "' ORDER BY tablename")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        assertTrue(!tables.isEmpty());
        for (String table : tables) {
            long count = 0;
            hash.update(table.getBytes(StandardCharsets.UTF_8));
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                    "SELECT to_jsonb(t)::text FROM " + schema + ".\"" + table + "\" t ORDER BY to_jsonb(t)::text")) {
                while (rs.next()) {
                    hash.update(rs.getString(1).getBytes(StandardCharsets.UTF_8));
                    hash.update((byte)'\n');
                    count++;
                }
            }
            counts.put(table, count);
        }
        return new Snapshot(counts, java.util.HexFormat.of().formatHex(hash.digest()));
    }

    private static String coordinateHash(Connection conn, String schema) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                "SELECT id,trim_scale(latitude),trim_scale(longitude) FROM " + schema + ".services ORDER BY id")) {
            while (rs.next()) {
                for (int i = 1; i <= 3; i++) hash.update(String.valueOf(rs.getString(i)).getBytes(StandardCharsets.UTF_8));
            }
        }
        return java.util.HexFormat.of().formatHex(hash.digest());
    }

    private static void awaitWaitingLocks(String url, String schema, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        try (Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            do {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pg_locks WHERE relation='" + schema
                        + ".services'::regclass AND NOT granted")) {
                    assertTrue(rs.next());
                    if (rs.getInt(1) >= expected) return;
                }
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
        }
        throw new AssertionError("Concurrent migration/reader/writer locks were not observed");
    }

    private static double blockedOperation(String url, String sql, CountDownLatch attempted, boolean update) throws Exception {
        try (Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(10);
            attempted.countDown();
            long start = System.nanoTime();
            if (update) assertEquals(1, stmt.executeUpdate(sql));
            else try (ResultSet rs = stmt.executeQuery(sql)) { assertTrue(rs.next()); }
            return (System.nanoTime() - start) / 1_000_000_000.0;
        }
    }
}
