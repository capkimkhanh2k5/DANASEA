package com.danasea.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Testcontainers(disabledWithoutDocker = true)
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
        String restoreSchema = "rehearsal_v44_restored";

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

        // Step 3: Compute baseline invariants and take backup snapshot
        long baselineCount;
        String backupHash;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + schema);

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM services")) {
                assertTrue(rs.next());
                baselineCount = rs.getLong(1);
                assertEquals(rows, baselineCount);
            }

            // Create backup table for recovery proof
            stmt.executeUpdate("CREATE TABLE services_backup AS SELECT * FROM services");

            // Calculate SHA-256 fingerprint of baseline state
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (ResultSet rs = stmt.executeQuery("SELECT id, latitude, longitude FROM services ORDER BY id LIMIT 500")) {
                while (rs.next()) {
                    digest.update(rs.getString(1).getBytes(StandardCharsets.UTF_8));
                    digest.update(rs.getString(2).getBytes(StandardCharsets.UTF_8));
                    digest.update(rs.getString(3).getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            backupHash = sb.toString();
        }

        // Step 4: Concurrent reader & writer threads during migration
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong maxLockWaitNanos = new AtomicLong(0);
        AtomicInteger readerQueries = new AtomicInteger(0);
        AtomicInteger writerUpdates = new AtomicInteger(0);

        Thread readerThread = new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SET search_path TO " + schema);
                while (running.get()) {
                    long t0 = System.nanoTime();
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*), max(latitude) FROM services WHERE id = '" + referenceServiceId + "'")) {
                        if (rs.next()) {
                            readerQueries.incrementAndGet();
                        }
                    }
                    long wait = System.nanoTime() - t0;
                    maxLockWaitNanos.updateAndGet(curr -> Math.max(curr, wait));
                    Thread.sleep(5);
                }
            } catch (Exception ignored) {
            }
        });

        Thread writerThread = new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SET search_path TO " + schema);
                while (running.get()) {
                    long t0 = System.nanoTime();
                    int updated = stmt.executeUpdate("UPDATE services SET view_count = view_count + 1 WHERE id = '" + referenceServiceId + "'");
                    if (updated > 0) {
                        writerUpdates.incrementAndGet();
                    }
                    long wait = System.nanoTime() - t0;
                    maxLockWaitNanos.updateAndGet(curr -> Math.max(curr, wait));
                    Thread.sleep(10);
                }
            } catch (Exception ignored) {
            }
        });

        readerThread.start();
        writerThread.start();

        // Step 5: Execute Migration V45 under concurrent contention
        long migStart = System.nanoTime();
        Flyway flywayV45 = Flyway.configure()
                .dataSource(jdbcUrl, "rehearsal_user", "rehearsal_password")
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("45")
                .load();
        flywayV45.migrate();
        long migEnd = System.nanoTime();

        running.set(false);
        readerThread.join(5000);
        writerThread.join(5000);

        double actualMigrationSeconds = (migEnd - migStart) / 1_000_000_000.0;
        double actualLockSeconds = maxLockWaitNanos.get() / 1_000_000_000.0;

        assertTrue(actualMigrationSeconds <= maxMigrationSeconds,
                "Migration duration (" + actualMigrationSeconds + "s) exceeded budget (" + maxMigrationSeconds + "s)");
        assertTrue(actualLockSeconds <= maxLockSeconds,
                "Observed lock wait (" + actualLockSeconds + "s) exceeded budget (" + maxLockSeconds + "s)");

        // Step 6: Verify V45 schema columns and precision/scale
        int numPrecisionLat = 0;
        int numScaleLat = 0;
        int numPrecisionLon = 0;
        int numScaleLon = 0;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name, numeric_precision, numeric_scale "
                             + "FROM information_schema.columns "
                             + "WHERE table_schema = ? AND table_name = 'services' AND column_name IN ('latitude', 'longitude')")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    int prec = rs.getInt("numeric_precision");
                    int sc = rs.getInt("numeric_scale");
                    if ("latitude".equalsIgnoreCase(col)) {
                        numPrecisionLat = prec;
                        numScaleLat = sc;
                    } else if ("longitude".equalsIgnoreCase(col)) {
                        numPrecisionLon = prec;
                        numScaleLon = sc;
                    }
                }
            }
        }
        assertEquals(46, numPrecisionLat, "latitude numeric_precision must be 46");
        assertEquals(10, numScaleLat, "latitude numeric_scale must be 10");
        assertEquals(46, numPrecisionLon, "longitude numeric_precision must be 46");
        assertEquals(10, numScaleLon, "longitude numeric_scale must be 10");

        // Step 7: Verify baseline data preservation
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + schema);

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM services")) {
                assertTrue(rs.next());
                assertEquals(baselineCount, rs.getLong(1), "Total row count must be preserved");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT latitude, longitude FROM services WHERE id = '" + referenceServiceId + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getBigDecimal("latitude").compareTo(new BigDecimal("16.12")));
                assertEquals(0, rs.getBigDecimal("longitude").compareTo(new BigDecimal("108.26")));
            }
        }

        // Step 8: Verify insertion and readback of 10-decimal high precision coordinates
        BigDecimal preciseLat = new BigDecimal("16.0543219876");
        BigDecimal preciseLon = new BigDecimal("108.2412345678");
        UUID preciseServiceId = UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + schema);

            stmt.executeUpdate("INSERT INTO services(id, vendor_id, category_id, name, slug, status, price, weather_sensitive, view_count, waiver_required, latitude, longitude, created_at, updated_at) "
                    + "VALUES ('" + preciseServiceId + "', '" + vendorId + "', '" + categoryId + "', 'Precise Service', 'precise-service', 'PUBLISHED', 350000, false, 0, false, "
                    + preciseLat + ", " + preciseLon + ", now(), now())");

            try (ResultSet rs = stmt.executeQuery("SELECT latitude, longitude FROM services WHERE id = '" + preciseServiceId + "'")) {
                assertTrue(rs.next());
                assertEquals(preciseLat, rs.getBigDecimal("latitude"));
                assertEquals(preciseLon, rs.getBigDecimal("longitude"));
            }
        }

        // Step 9: Restore verification into separate isolated schema
        Flyway flywayRestore = Flyway.configure()
                .dataSource(jdbcUrl, "rehearsal_user", "rehearsal_password")
                .schemas(restoreSchema)
                .defaultSchema(restoreSchema)
                .locations("classpath:db/migration")
                .target("44")
                .connectRetries(30)
                .load();
        flywayRestore.migrate();
        assertTrue(flywayRestore.validateWithResult().validationSuccessful, "Restored schema V44 must pass Flyway validation");

        // Copy baseline data from backup into restored schema
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "rehearsal_user", "rehearsal_password");
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET search_path TO " + restoreSchema);

            stmt.executeUpdate("INSERT INTO users SELECT * FROM " + schema + ".users WHERE id = '" + vendorUserId + "' ON CONFLICT DO NOTHING");
            stmt.executeUpdate("INSERT INTO vendors SELECT * FROM " + schema + ".vendors WHERE id = '" + vendorId + "' ON CONFLICT DO NOTHING");
            stmt.executeUpdate("INSERT INTO categories SELECT * FROM " + schema + ".categories WHERE id = '" + categoryId + "' ON CONFLICT DO NOTHING");

            java.util.List<String> insertCols = new java.util.ArrayList<>();
            try (PreparedStatement psCols = conn.prepareStatement(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'services' AND is_generated = 'NEVER' ORDER BY ordinal_position")) {
                psCols.setString(1, restoreSchema);
                try (ResultSet rsCols = psCols.executeQuery()) {
                    while (rsCols.next()) {
                        insertCols.add(rsCols.getString(1));
                    }
                }
            }
            String colList = String.join(", ", insertCols);
            stmt.executeUpdate("INSERT INTO " + restoreSchema + ".services (" + colList + ") SELECT " + colList + " FROM " + schema + ".services_backup");

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM services")) {
                assertTrue(rs.next());
                assertEquals(baselineCount, rs.getLong(1), "Restored schema must have identical row count");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT latitude, longitude FROM services WHERE id = '" + referenceServiceId + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getBigDecimal("latitude").compareTo(new BigDecimal("16.12")));
                assertEquals(0, rs.getBigDecimal("longitude").compareTo(new BigDecimal("108.26")));
            }
        }

        // Step 10: Emit evidence JSON if output path requested
        if (outputPath != null && !outputPath.isBlank()) {
            File outFile = new File(outputPath);
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("schema", schema);
            evidence.put("rows", rows);
            evidence.put("postgres_version", pgVersion);
            evidence.put("actual_lock_seconds", Math.round(actualLockSeconds * 1000.0) / 1000.0);
            evidence.put("actual_migration_seconds", Math.round(actualMigrationSeconds * 1000.0) / 1000.0);
            evidence.put("max_lock_seconds_budget", maxLockSeconds);
            evidence.put("max_migration_seconds_budget", maxMigrationSeconds);
            evidence.put("numeric_precision", numPrecisionLat);
            evidence.put("numeric_scale", numScaleLat);
            evidence.put("baseline_preserved", true);
            evidence.put("high_precision_verified", true);
            evidence.put("restore_verified", true);
            evidence.put("backup_hash", backupHash);
            evidence.put("passed", true);

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(outFile, evidence);
        }
    }
}
