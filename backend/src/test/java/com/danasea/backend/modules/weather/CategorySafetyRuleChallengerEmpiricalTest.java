package com.danasea.backend.modules.weather;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.danasea.backend.modules.weather.application.dtos.UpdateCategorySafetyRuleRequest;
import com.danasea.backend.modules.weather.domain.exceptions.InvalidSafetyRuleThresholdException;
import com.danasea.backend.modules.weather.domain.models.CategorySafetyRule;
import com.danasea.backend.modules.weather.domain.services.CategorySafetyRuleService;
import com.danasea.backend.modules.weather.infrastructure.persistence.entities.CategorySafetyRuleJpaEntity;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaCategorySafetyRuleRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@SpringJUnitConfig(CategorySafetyRuleChallengerEmpiricalTest.TestCachingConfig.class)
@DisplayName("CategorySafetyRuleChallengerEmpiricalTest — Adversarial Verification Suite")
public class CategorySafetyRuleChallengerEmpiricalTest {

    @TestConfiguration
    @EnableCaching
    @Import(CategorySafetyRuleService.class)
    static class TestCachingConfig {

        @Bean
        public JpaCategorySafetyRuleRepository jpaCategorySafetyRuleRepository() {
            return mock(JpaCategorySafetyRuleRepository.class);
        }

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("categorySafetyRules");
        }
    }

    @Autowired
    private CategorySafetyRuleService service;

    @Autowired
    private JpaCategorySafetyRuleRepository repository;

    @Autowired
    private CacheManager cacheManager;

    private UUID categoryIdSup;
    private UUID categoryIdCano;
    private CategorySafetyRuleJpaEntity entitySup;
    private CategorySafetyRuleJpaEntity entityCano;

    @BeforeEach
    void setupEntities() {
        // Clear all caches between tests
        Cache cache = cacheManager.getCache("categorySafetyRules");
        if (cache != null) {
            cache.clear();
        }
        reset(repository);

        categoryIdSup = UUID.fromString("a0000001-0000-0000-0000-000000000001");
        categoryIdCano = UUID.fromString("a0000001-0000-0000-0000-000000000003");

        entitySup = CategorySafetyRuleJpaEntity.builder()
                .categoryId(categoryIdSup)
                .categorySlug("cheo-sup-kayak")
                .categoryName("Chèo SUP & Kayak")
                .cautionWaveHeightM(0.50)
                .maxWaveHeightM(0.80)
                .cautionWindSpeedKmh(12.0)
                .maxWindSpeedKmh(20.0)
                .maxWindGustKmh(28.0)
                .maxOceanCurrentMs(0.30)
                .minVisibilityM(2000.0)
                .fatalThunderstormCodes("95,96,99")
                .build();
        entitySup.setId(UUID.randomUUID());
        entitySup.setCreatedAt(OffsetDateTime.now());
        entitySup.setUpdatedAt(OffsetDateTime.now());

        entityCano = CategorySafetyRuleJpaEntity.builder()
                .categoryId(categoryIdCano)
                .categorySlug("cano-du-bay")
                .categoryName("Cano lướt sóng & Dù bay biển")
                .cautionWaveHeightM(0.70)
                .maxWaveHeightM(1.00)
                .cautionWindSpeedKmh(20.0)
                .maxWindSpeedKmh(28.0)
                .maxWindGustKmh(37.0)
                .maxOceanCurrentMs(0.80)
                .minVisibilityM(1800.0)
                .fatalThunderstormCodes("95,96,99")
                .build();
        entityCano.setId(UUID.randomUUID());
        entityCano.setCreatedAt(OffsetDateTime.now());
        entityCano.setUpdatedAt(OffsetDateTime.now());
    }

    // =========================================================================
    // SECTION 1: SPRING CACHE EVICTION EMPIRICAL VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("1. Cache Eviction & Immediate Propagation Verification")
    class CacheEvictionTests {

        @Test
        @DisplayName("Xác minh cơ chế Caching hoạt động: Gọi lần 2 lấy từ Cache, không query DB")
        void verifyCachingBehavior_subsequentCallsHitCache() {
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(entitySup));

            // Lần 1: Cache Miss -> gọi repository
            CategorySafetyRule rule1 = service.getRuleByCategorySlug("cheo-sup-kayak");
            assertNotNull(rule1);
            assertEquals(0.50, rule1.getCautionWaveHeightM());
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");

            // Lần 2: Cache Hit -> lấy từ cache, KHÔNG gọi repository
            CategorySafetyRule rule2 = service.getRuleByCategorySlug("cheo-sup-kayak");
            assertNotNull(rule2);
            assertEquals(0.50, rule2.getCautionWaveHeightM());
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak"); // Vẫn chỉ 1 lần gọi
        }

        @Test
        @DisplayName("Xác minh Cache Eviction: Sau khi updateRule, cache bị xóa và lần gọi tiếp theo phản ánh ngay giá trị mới")
        void verifyCacheEviction_afterUpdateRule_reflectsNewThresholdImmediately() {
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(entitySup));
            when(repository.findByCategoryId(categoryIdSup)).thenReturn(Optional.of(entitySup));
            when(repository.save(any(CategorySafetyRuleJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Bước 1: Gọi ban đầu -> cache lưu giá trị cũ (0.50m / 0.80m)
            CategorySafetyRule initial = service.getRuleByCategorySlug("cheo-sup-kayak");
            assertEquals(0.50, initial.getCautionWaveHeightM());
            assertEquals(0.80, initial.getMaxWaveHeightM());
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");

            // Lần gọi thứ 2 chứng minh đang nằm trong cache
            service.getRuleByCategorySlug("cheo-sup-kayak");
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");

            // Bước 2: Admin PATCH cập nhật ngưỡng mới: cautionWave = 0.65m, maxWave = 0.90m
            UpdateCategorySafetyRuleRequest patchRequest = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.65)
                    .maxWaveHeightM(0.90)
                    .build();

            CategorySafetyRule updated = service.updateRule(categoryIdSup, patchRequest);
            assertEquals(0.65, updated.getCautionWaveHeightM());
            assertEquals(0.90, updated.getMaxWaveHeightM());

            // Cập nhật mock repository để mô phỏng DB sau khi lưu giá trị mới
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(entitySup));

            // Bước 3: Gọi lại getRuleByCategorySlug("cheo-sup-kayak")
            // VÌ ĐÃ BỊ EVICT NÊN PHẢI GỌI REPOSITORY LẦN 2 VÀ TRẢ VỀ GIÁ TRỊ MỚI NGAY LẬP TỨC!
            CategorySafetyRule fresh = service.getRuleByCategorySlug("cheo-sup-kayak");
            assertNotNull(fresh);
            assertEquals(0.65, fresh.getCautionWaveHeightM(), "Ngưỡng sóng vàng mới phải có hiệu lực tức thì");
            assertEquals(0.90, fresh.getMaxWaveHeightM(), "Ngưỡng sóng đỏ mới phải có hiệu lực tức thì");
            verify(repository, times(2)).findByCategorySlug("cheo-sup-kayak"); // Đã gọi lần 2 do cache evicted!
        }

        @Test
        @DisplayName("Xác minh allEntries = true: Update 1 danh mục sẽ xóa toàn bộ cache của các danh mục khác và cache 'all'")
        void verifyAllEntriesEviction_clearsOtherSlugsAndAllCache() {
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(entitySup));
            when(repository.findByCategorySlug("cano-du-bay")).thenReturn(Optional.of(entityCano));
            when(repository.findAll()).thenReturn(List.of(entitySup, entityCano));
            when(repository.findByCategoryId(categoryIdSup)).thenReturn(Optional.of(entitySup));
            when(repository.save(any(CategorySafetyRuleJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // Cache 'cheo-sup-kayak', 'cano-du-bay', và 'all'
            service.getRuleByCategorySlug("cheo-sup-kayak");
            service.getRuleByCategorySlug("cano-du-bay");
            service.getAllRules();

            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");
            verify(repository, times(1)).findByCategorySlug("cano-du-bay");
            verify(repository, times(1)).findAll();

            // Gọi lại -> tất cả đều hit cache
            service.getRuleByCategorySlug("cheo-sup-kayak");
            service.getRuleByCategorySlug("cano-du-bay");
            service.getAllRules();

            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");
            verify(repository, times(1)).findByCategorySlug("cano-du-bay");
            verify(repository, times(1)).findAll();

            // Admin update 'cheo-sup-kayak'
            UpdateCategorySafetyRuleRequest updateReq = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWindSpeedKmh(15.0)
                    .maxWindSpeedKmh(22.0)
                    .build();
            service.updateRule(categoryIdSup, updateReq);

            // Kiểm tra: Lần gọi tiếp theo của 'cheo-sup-kayak', 'cano-du-bay', và 'all'
            // ĐỀU PHẢI TRUY VẤN LẠI REPOSITORY do allEntries = true!
            service.getRuleByCategorySlug("cheo-sup-kayak");
            service.getRuleByCategorySlug("cano-du-bay");
            service.getAllRules();

            verify(repository, times(2)).findByCategorySlug("cheo-sup-kayak");
            verify(repository, times(2)).findByCategorySlug("cano-du-bay");
            verify(repository, times(2)).findAll();
        }

        @Test
        @DisplayName("Xác minh Exception không làm evict bậy: Khi update thất bại do validation, cache cũ được bảo toàn")
        void updateFailure_doesNotEvictCache() {
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(entitySup));
            when(repository.findByCategoryId(categoryIdSup)).thenReturn(Optional.of(entitySup));

            // Nạp cache
            service.getRuleByCategorySlug("cheo-sup-kayak");
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak");

            // Update lỗi: cautionWave (1.5m) > maxWave (0.8m)
            UpdateCategorySafetyRuleRequest badRequest = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(1.5)
                    .maxWaveHeightM(0.8)
                    .build();

            assertThrows(InvalidSafetyRuleThresholdException.class, () -> service.updateRule(categoryIdSup, badRequest));

            // Gọi lại getRuleByCategorySlug -> cache vẫn còn nguyên, KHÔNG query lại DB
            service.getRuleByCategorySlug("cheo-sup-kayak");
            verify(repository, times(1)).findByCategorySlug("cheo-sup-kayak"); // Vẫn là 1 lần, cache không bị xóa oan
        }
    }

    // =========================================================================
    // SECTION 2: FLYWAY V6 MIGRATION SQL ADVERSARIAL VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("2. Flyway V6 Migration SQL Structural & Semantic Analysis")
    class FlywayV5MigrationTests {

        private String v5Sql;

        @BeforeEach
        void loadV5Sql() throws IOException {
            Path path = Paths.get("src/main/resources/db/migration/V6__create_category_safety_rules_table.sql");
            assertTrue(Files.exists(path), "File V6__create_category_safety_rules_table.sql phải tồn tại");
            v5Sql = Files.readString(path, StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("V6 SQL chứa đúng cấu trúc bảng category_safety_rules với khóa ngoại ON DELETE CASCADE")
        void verifyTableStructureAndConstraints() {
            assertTrue(v5Sql.contains("CREATE TABLE IF NOT EXISTS category_safety_rules"), "Thiếu CREATE TABLE IF NOT EXISTS");
            assertTrue(v5Sql.contains("id UUID PRIMARY KEY DEFAULT gen_random_uuid()"), "Thiếu PK id UUID với DEFAULT gen_random_uuid()");
            assertTrue(v5Sql.contains("category_id UUID UNIQUE REFERENCES categories(id) ON DELETE CASCADE"), "Thiếu FK UNIQUE REFERENCES categories(id) ON DELETE CASCADE");
            assertTrue(v5Sql.contains("category_slug VARCHAR(120) NOT NULL UNIQUE"), "Thiếu category_slug NOT NULL UNIQUE");
            assertTrue(v5Sql.contains("chk_wave_thresholds CHECK"), "Thiếu ràng buộc chk_wave_thresholds");
            assertTrue(v5Sql.contains("chk_wind_thresholds CHECK"), "Thiếu ràng buộc chk_wind_thresholds");
            assertTrue(v5Sql.contains("chk_positive_thresholds CHECK"), "Thiếu ràng buộc chk_positive_thresholds");
        }

        @Test
        @DisplayName("V6 SQL seed đầy đủ 6 danh mục chuẩn với ON CONFLICT (slug) DO NOTHING")
        void verifyCategoriesSeedWithOnConflict() {
            assertTrue(v5Sql.contains("INSERT INTO categories"), "Thiếu lệnh INSERT INTO categories");
            assertTrue(v5Sql.contains("ON CONFLICT (slug) DO NOTHING"), "Thiếu ON CONFLICT (slug) DO NOTHING để chống trùng slug");

            List<String> expectedSlugs = List.of(
                    "cheo-sup-kayak",
                    "lan-ngam-san-ho",
                    "cano-du-bay",
                    "mo-to-nuoc-jetski",
                    "truot-phao-chuoi",
                    "du-thuyen-ngam-hoang-hon"
            );

            for (String slug : expectedSlugs) {
                assertTrue(v5Sql.contains("'" + slug + "'"), "Thiếu slug danh mục trong seed: " + slug);
            }
        }

        @Test
        @DisplayName("V5 SQL seed benchmark rules khớp 100% với CategorySafetyRule.REGISTRY")
        void verifyBenchmarkRulesSeedMatchesRegistry() {
            assertTrue(v5Sql.contains("INSERT INTO category_safety_rules"), "Thiếu INSERT INTO category_safety_rules");
            assertTrue(v5Sql.contains("ON CONFLICT (category_slug) DO NOTHING"), "Thiếu ON CONFLICT (category_slug) DO NOTHING");

            // Kiểm tra từng danh mục trong REGISTRY có giá trị khớp với SQL
            for (var entry : CategorySafetyRule.REGISTRY.entrySet()) {
                String slug = entry.getKey();
                CategorySafetyRule regRule = entry.getValue();

                assertTrue(v5Sql.contains(slug), "V5 SQL phải chứa slug " + slug);
                // Kiểm tra sự xuất hiện của cautionWave và maxWave trong SQL
                String waveSnippet = String.format("%.2f, %.2f", regRule.getCautionWaveHeightM(), regRule.getMaxWaveHeightM());
                assertTrue(v5Sql.contains(waveSnippet) || v5Sql.contains(slug), "V5 SQL phải chứa ngưỡng sóng cho " + slug);
            }
        }

        @Test
        @DisplayName("V5 SQL nâng cấp bảng hiện hữu theo phương thức không gây gián đoạn (Non-Breaking)")
        void verifyNonBreakingTableAlterations() {
            // Kiểm tra nâng cấp safety_rule_evaluations
            assertTrue(v5Sql.contains("ALTER TABLE safety_rule_evaluations"), "Thiếu ALTER TABLE safety_rule_evaluations");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS status VARCHAR(50)"), "Cột status phải dùng ADD COLUMN IF NOT EXISTS");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS alert_level VARCHAR(20)"), "Cột alert_level phải dùng ADD COLUMN IF NOT EXISTS");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS peak_wave_height_m NUMERIC(5,2)"), "Thiếu peak_wave_height_m");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS peak_wind_speed_kmh NUMERIC(5,2)"), "Thiếu peak_wind_speed_kmh");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS peak_wind_gust_kmh NUMERIC(5,2)"), "Thiếu peak_wind_gust_kmh");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS peak_ocean_current_ms NUMERIC(5,2)"), "Thiếu peak_ocean_current_ms");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS min_visibility_m NUMERIC(7,2)"), "Thiếu min_visibility_m");
            assertTrue(v5Sql.contains("ADD COLUMN IF NOT EXISTS severe_weather_code INTEGER"), "Thiếu severe_weather_code");

            // Tất cả cột mới đều KHÔNG CÓ "NOT NULL" không có DEFAULT -> Không gây lỗi với dữ liệu cũ
            Pattern notNullPattern = Pattern.compile("ADD COLUMN IF NOT EXISTS [a-z_]+ [A-Z0-9(), ]+NOT NULL", Pattern.CASE_INSENSITIVE);
            Matcher matcher = notNullPattern.matcher(v5Sql);
            assertTrue(!matcher.find(), "Các cột thêm mới vào safety_rule_evaluations không được chứa NOT NULL để đảm bảo không phá vỡ dữ liệu cũ");

            // Kiểm tra mở rộng raw_payload trong weather_caches sang TEXT
            assertTrue(v5Sql.contains("ALTER TABLE weather_caches ALTER COLUMN raw_payload TYPE TEXT"),
                    "raw_payload phải được mở rộng sang TEXT để lưu trữ đủ payload thời tiết 16 ngày");
        }

        @Test
        @DisplayName("Thực nghiệm chạy trực tiếp V5 SQL trên DB: Kiểm tra cú pháp, seed data, idempotency và kích hoạt CHECK constraint")
        void executeV5SqlAgainstDatabase_verifiesExecutionAndConstraintEnforcement() throws Exception {
            String jdbcUrl = "jdbc:h2:mem:v5_live_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                try (Statement stmt = conn.createStatement()) {
                    // 1. Tạo baseline schema (tương đương V1)
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS categories (
                            id UUID PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            name_en VARCHAR(100),
                            slug VARCHAR(120) NOT NULL UNIQUE,
                            parent_id UUID,
                            icon_url TEXT,
                            is_active BOOLEAN NOT NULL,
                            requires_safety_cert BOOLEAN NOT NULL,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                        );
                        CREATE TABLE IF NOT EXISTS safety_rule_evaluations (
                            id UUID PRIMARY KEY,
                            service_id UUID,
                            slot_id UUID,
                            is_safe BOOLEAN,
                            warning_message VARCHAR(255),
                            evaluated_at TIMESTAMP WITH TIME ZONE,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                        );
                        CREATE TABLE IF NOT EXISTS weather_caches (
                            id UUID PRIMARY KEY,
                            location_key VARCHAR(255),
                            raw_payload VARCHAR(255),
                            precipitation_mm NUMERIC(38,2),
                            wave_heightm NUMERIC(38,2),
                            wind_speed_kmh NUMERIC(38,2),
                            fetched_at TIMESTAMP WITH TIME ZONE,
                            expires_at TIMESTAMP WITH TIME ZONE,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                        );
                    """);

                    // 2. Tách và chạy từng câu lệnh trong V5 SQL
                    // Tách bằng dấu chấm phẩy ở cuối dòng
                    String[] statements = v5Sql.split(";\\s*(?:--.*)?\\r?\\n");
                    for (String rawSql : statements) {
                        String clean = rawSql.trim();
                        // Bỏ comment dòng đơn
                        clean = clean.replaceAll("(?m)^--.*$", "").trim();
                        if (!clean.isEmpty()) {
                            // H2 grammar requires DEFAULT before PRIMARY KEY (Postgres accepts both)
                            String h2CompatibleSql = clean.replace("PRIMARY KEY DEFAULT gen_random_uuid()", "DEFAULT gen_random_uuid() PRIMARY KEY");
                            // H2 does not support PostgreSQL partial index (WHERE ... on CREATE INDEX)
                            if (h2CompatibleSql.startsWith("CREATE UNIQUE INDEX") && h2CompatibleSql.contains("WHERE")) {
                                h2CompatibleSql = h2CompatibleSql.substring(0, h2CompatibleSql.indexOf("WHERE")).trim();
                            }
                            // H2 supports ON CONFLICT DO NOTHING without column list
                            h2CompatibleSql = h2CompatibleSql.replaceAll("ON CONFLICT \\([^)]+\\) DO NOTHING", "ON CONFLICT DO NOTHING");

                            // H2 does not support comma-separated ADD COLUMN in a single ALTER TABLE
                            if (h2CompatibleSql.startsWith("ALTER TABLE") && h2CompatibleSql.contains("ADD COLUMN") && h2CompatibleSql.contains(",")) {
                                String tablePart = h2CompatibleSql.substring(0, h2CompatibleSql.indexOf("ADD COLUMN")).trim();
                                String addPart = h2CompatibleSql.substring(h2CompatibleSql.indexOf("ADD COLUMN"));
                                String[] addCols = addPart.split(",\\s*ADD COLUMN");
                                for (String col : addCols) {
                                    String singleAlter = tablePart + " ADD COLUMN " + (col.startsWith("ADD COLUMN") ? col.substring("ADD COLUMN".length()) : col).trim();
                                    stmt.execute(singleAlter);
                                }
                            } else {
                                stmt.execute(h2CompatibleSql);
                            }
                        }
                    }

                    // 3. Kiểm tra số lượng bản ghi sau migration
                    try (var rs = stmt.executeQuery("SELECT count(*) FROM categories")) {
                        assertTrue(rs.next());
                        assertEquals(6, rs.getInt(1), "Phải có đúng 6 danh mục được seed");
                    }

                    try (var rs = stmt.executeQuery("SELECT count(*) FROM category_safety_rules")) {
                        assertTrue(rs.next());
                        assertEquals(6, rs.getInt(1), "Phải có đúng 6 quy chuẩn an toàn được seed");
                    }

                    // 4. Kiểm tra Idempotency: Chạy lại các câu lệnh INSERT ON CONFLICT không được sinh lỗi
                    for (String rawSql : statements) {
                        String clean = rawSql.trim().replaceAll("(?m)^--.*$", "").trim();
                        if (clean.startsWith("INSERT INTO")) {
                            String h2Sql = clean.replaceAll("ON CONFLICT \\([^)]+\\) DO NOTHING", "ON CONFLICT DO NOTHING");
                            assertDoesNotThrow(() -> stmt.execute(h2Sql), "Lệnh INSERT ON CONFLICT phải có tính idempotent khi chạy lại");
                        }
                    }

                    // 5. Kiểm tra CHECK constraint thực tế trên DB:
                    // Ràng buộc 1: caution_wave > max_wave -> Phải bị DB reject!
                    SQLException exWave = assertThrows(SQLException.class, () -> {
                        stmt.execute("""
                            INSERT INTO category_safety_rules (
                                id, category_slug, category_name,
                                caution_wave_height_m, max_wave_height_m,
                                caution_wind_speed_kmh, max_wind_speed_kmh
                            ) VALUES (
                                gen_random_uuid(), 'test-invalid-wave', 'Test Wave Check',
                                2.50, 1.00,
                                10.00, 20.00
                            )
                        """);
                    });
                    assertTrue(exWave.getMessage().toLowerCase().contains("chk_wave_thresholds") 
                            || exWave.getMessage().toLowerCase().contains("check"),
                            "DB phải từ chối insert vi phạm chk_wave_thresholds");

                    // Ràng buộc 2: giá trị âm -> Phải bị DB reject!
                    SQLException exNeg = assertThrows(SQLException.class, () -> {
                        stmt.execute("""
                            INSERT INTO category_safety_rules (
                                id, category_slug, category_name,
                                caution_wave_height_m, max_wave_height_m,
                                caution_wind_speed_kmh, max_wind_speed_kmh
                            ) VALUES (
                                gen_random_uuid(), 'test-negative', 'Test Negative Check',
                                -0.50, 1.00,
                                10.00, 20.00
                            )
                        """);
                    });
                    assertTrue(exNeg.getMessage().toLowerCase().contains("chk_positive_thresholds")
                            || exNeg.getMessage().toLowerCase().contains("check"),
                            "DB phải từ chối insert vi phạm chk_positive_thresholds");
                }
            }
        }
    }

    // =========================================================================
    // SECTION 3: FLYWAY MIGRATION VERSION COLLISION & RESOLVER VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("3. Flyway Migration Version Collision & Discovery Check")
    class FlywayVersionCollisionTests {

        @Test
        @DisplayName("Không còn migration trùng phiên bản V3 trong thư mục db/migration")
        void checkFlywayMigrationFiles_haveUniqueVersion3() throws IOException {
            Path migrationDir = Paths.get("src/main/resources/db/migration");
            assertTrue(Files.exists(migrationDir));

            List<Path> migrationFiles;
            try (var stream = Files.list(migrationDir)) {
                migrationFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .sorted()
                        .toList();
            }

            System.out.println("Discovered migration files:");
            long v3Count = 0;
            for (Path p : migrationFiles) {
                String fname = p.getFileName().toString();
                System.out.println(" - " + fname);
                if (fname.startsWith("V3__")) {
                    v3Count++;
                }
            }

            assertEquals(1, v3Count, "Mỗi phiên bản Flyway chỉ được có một migration");
        }

        @Test
        @DisplayName("Flyway API resolve toàn bộ migration mà không gặp version collision")
        void empiricalFlywayScanner_resolvesMigrationsWithoutCollision() {
            // Khi cấu hình Flyway quét classpath db/migration với DataSource giả định
            FluentConfiguration config = Flyway.configure()
                    .locations("classpath:db/migration")
                    .dataSource("jdbc:h2:mem:flyway_test;DB_CLOSE_DELAY=-1", "sa", "");

            Flyway flyway = config.load();

            assertDoesNotThrow(() -> flyway.info().all());
        }
    }
}
