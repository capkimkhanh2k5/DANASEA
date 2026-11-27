package com.danasea.backend.modules.weather;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.danasea.backend.modules.weather.application.dtos.UpdateCategorySafetyRuleRequest;
import com.danasea.backend.modules.weather.domain.exceptions.CategorySafetyRuleNotFoundException;
import com.danasea.backend.modules.weather.domain.exceptions.InvalidSafetyRuleThresholdException;
import com.danasea.backend.modules.weather.domain.models.CategorySafetyRule;
import com.danasea.backend.modules.weather.domain.services.CategorySafetyRuleService;
import com.danasea.backend.modules.weather.infrastructure.persistence.entities.CategorySafetyRuleJpaEntity;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaCategorySafetyRuleRepository;
import com.danasea.backend.modules.weather.presentation.controllers.AdminCategorySafetyRuleController;
import com.danasea.backend.modules.weather.presentation.handlers.WeatherExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorySafetyRuleStressTest — Adversarial Hardening Suite for Milestone 1")
class CategorySafetyRuleStressTest {

    @Mock
    private JpaCategorySafetyRuleRepository repository;

    private CategorySafetyRuleService service;
    private AdminCategorySafetyRuleController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID sampleCategoryId;
    private CategorySafetyRuleJpaEntity baseEntity;

    @BeforeEach
    void setUp() {
        service = new CategorySafetyRuleService(repository);
        controller = new AdminCategorySafetyRuleController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WeatherExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        sampleCategoryId = UUID.randomUUID();
        baseEntity = CategorySafetyRuleJpaEntity.builder()
                .categoryId(sampleCategoryId)
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
        baseEntity.setId(UUID.randomUUID());
        baseEntity.setCreatedAt(OffsetDateTime.now());
        baseEntity.setUpdatedAt(OffsetDateTime.now());
    }

    // =========================================================================
    // SECTION 1: STRESS TEST VALIDATION — CAUTION > MAX & COMBINATIONS
    // =========================================================================
    @Nested
    @DisplayName("1. Threshold Combination & Boundary Stress Tests")
    class ThresholdValidationStressTests {

        @Test
        @DisplayName("Cả sóng và gió đều có caution > max -> Ném lỗi ngay lập tức")
        void bothWaveAndWindInvalid_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(1.5)
                    .maxWaveHeightM(0.8)
                    .cautionWindSpeedKmh(30.0)
                    .maxWindSpeedKmh(20.0)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("wave threshold") || ex.getMessage().contains("wind threshold"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Biên vi mô: cautionWave = 0.80001, maxWave = 0.80000 -> Bị chặn")
        void microDisparityCautionWaveExceedsMax_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.80001)
                    .maxWaveHeightM(0.80000)
                    .build();

            assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Biên vi mô: cautionWind = 20.001, maxWind = 20.000 -> Bị chặn")
        void microDisparityCautionWindExceedsMax_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWindSpeedKmh(20.001)
                    .maxWindSpeedKmh(20.000)
                    .build();

            assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Biên bằng nhau: cautionWave == maxWave (0.80 == 0.80) -> Cho phép (<= hợp lệ)")
        void exactEqualityWave_succeeds() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.80)
                    .maxWaveHeightM(0.80)
                    .build();

            CategorySafetyRule updated = service.updateRule(sampleCategoryId, request);
            assertEquals(0.80, updated.getCautionWaveHeightM());
            assertEquals(0.80, updated.getMaxWaveHeightM());
            verify(repository).save(any());
        }

        @Test
        @DisplayName("Biên bằng nhau: cautionWind == maxWind (25.0 == 25.0) -> Cho phép (<= hợp lệ)")
        void exactEqualityWind_succeeds() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWindSpeedKmh(25.0)
                    .maxWindSpeedKmh(25.0)
                    .build();

            CategorySafetyRule updated = service.updateRule(sampleCategoryId, request);
            assertEquals(25.0, updated.getCautionWindSpeedKmh());
            assertEquals(25.0, updated.getMaxWindSpeedKmh());
            verify(repository).save(any());
        }
    }

    // =========================================================================
    // SECTION 2: STRESS TEST PARTIAL UPDATE MERGING
    // =========================================================================
    @Nested
    @DisplayName("2. Partial Update Merging Stress Tests")
    class PartialUpdateMergingStressTests {

        @Test
        @DisplayName("Partial Wave: Chỉ update cautionWave > existing maxWave -> Bị chặn")
        void updateOnlyCautionWave_exceedsExistingMax_throwsException() {
            // baseEntity: caution = 0.50, max = 0.80
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.85) // 0.85 > 0.80
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution wave threshold"));
        }

        @Test
        @DisplayName("Partial Wave: Chỉ update maxWave < existing cautionWave -> Bị chặn")
        void updateOnlyMaxWave_belowExistingCaution_throwsException() {
            // baseEntity: caution = 0.50, max = 0.80
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWaveHeightM(0.40) // 0.40 < 0.50
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution wave threshold"));
        }

        @Test
        @DisplayName("Partial Wind: Chỉ update cautionWind > existing maxWind -> Bị chặn")
        void updateOnlyCautionWind_exceedsExistingMax_throwsException() {
            // baseEntity: caution = 12.0, max = 20.0
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWindSpeedKmh(22.0) // 22.0 > 20.0
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution sustained-wind threshold"));
        }

        @Test
        @DisplayName("Partial Wind: Chỉ update maxWind < existing cautionWind -> Bị chặn")
        void updateOnlyMaxWind_belowExistingCaution_throwsException() {
            // baseEntity: caution = 12.0, max = 20.0
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWindSpeedKmh(10.0) // 10.0 < 12.0
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution sustained-wind threshold"));
        }

        @Test
        @DisplayName("Partial Update: Chỉ sửa các trường độc lập (visibility, gust, current, thunderstorm) -> Sóng và gió cũ được giữ nguyên")
        void updateOnlyIndependentFields_preservesExistingWaveAndWind() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .minVisibilityM(3500.0)
                    .maxWindGustKmh(32.0)
                    .maxOceanCurrentMs(0.45)
                    .fatalThunderstormCodes("95,96")
                    .build();

            CategorySafetyRule updated = service.updateRule(sampleCategoryId, request);

            // Sóng và gió cũ giữ nguyên
            assertEquals(0.50, updated.getCautionWaveHeightM());
            assertEquals(0.80, updated.getMaxWaveHeightM());
            assertEquals(12.0, updated.getCautionWindSpeedKmh());
            assertEquals(20.0, updated.getMaxWindSpeedKmh());

            // Các trường mới được cập nhật
            assertEquals(3500.0, updated.getMinVisibilityM());
            assertEquals(32.0, updated.getMaxWindGustKmh());
            assertEquals(0.45, updated.getMaxOceanCurrentMs());
            assertEquals("95,96", updated.getFatalThunderstormCodes());
        }
    }

    // =========================================================================
    // SECTION 3: STRESS TEST ZERO AND NEGATIVE INPUTS
    // =========================================================================
    @Nested
    @DisplayName("3. Zero and Negative Value Stress Tests")
    class ZeroAndNegativeValueStressTests {

        @Test
        @DisplayName("Tất cả 7 trường số đều là 0.0 -> Hợp lệ và được lưu thành công")
        void allZeroInputs_succeeds() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.0)
                    .maxWaveHeightM(0.0)
                    .cautionWindSpeedKmh(0.0)
                    .maxWindSpeedKmh(0.0)
                    .maxWindGustKmh(0.0)
                    .maxOceanCurrentMs(0.0)
                    .minVisibilityM(0.0)
                    .build();

            CategorySafetyRule updated = service.updateRule(sampleCategoryId, request);
            assertEquals(0.0, updated.getCautionWaveHeightM());
            assertEquals(0.0, updated.getMaxWaveHeightM());
            assertEquals(0.0, updated.getCautionWindSpeedKmh());
            assertEquals(0.0, updated.getMaxWindSpeedKmh());
            assertEquals(0.0, updated.getMaxWindGustKmh());
            assertEquals(0.0, updated.getMaxOceanCurrentMs());
            assertEquals(0.0, updated.getMinVisibilityM());
        }

        @Test
        @DisplayName("maxWave = 0.0 nhưng cautionWave = 0.1 -> caution > max nên ném lỗi")
        void zeroMaxWithPositiveCaution_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.1)
                    .maxWaveHeightM(0.0)
                    .build();

            assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
        }

        @Test
        @DisplayName("Service validation ném lỗi khi cautionWave âm (-0.01)")
        void negativeCautionWave_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(-0.01)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("must not be negative"));
        }

        @Test
        @DisplayName("Service validation ném lỗi khi maxWindGustKmh âm (-5.0)")
        void negativeWindGust_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWindGustKmh(-5.0)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("must not be negative"));
        }

        @Test
        @DisplayName("Service validation ném lỗi khi maxOceanCurrentMs âm (-0.2)")
        void negativeOceanCurrent_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxOceanCurrentMs(-0.2)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("must not be negative"));
        }

        @Test
        @DisplayName("Service validation ném lỗi khi minVisibilityM âm (-100.0)")
        void negativeMinVisibility_throwsException() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .minVisibilityM(-100.0)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(sampleCategoryId, request)
            );
            assertTrue(ex.getMessage().contains("must not be negative"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"cautionWaveHeightM\": -0.5}",
                "{\"maxWaveHeightM\": -1.0}",
                "{\"cautionWindSpeedKmh\": -10.0}",
                "{\"maxWindSpeedKmh\": -20.0}",
                "{\"maxWindGustKmh\": -0.1}",
                "{\"maxOceanCurrentMs\": -0.05}",
                "{\"minVisibilityM\": -500.0}"
        })
        @DisplayName("MockMvc PATCH ném 400 Bad Request cho mọi trường âm thông qua Bean Validation")
        void mockMvc_patch_negativeFields_returns400(String jsonPayload) throws Exception {
            mockMvc.perform(patch("/api/admin/category-safety-rules/{categoryId}", sampleCategoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("INVALID_INPUT")));
        }
    }

    // =========================================================================
    // SECTION 4: STRESS TEST EMPTY BODY & NULL DEFENSIVE CHECKS
    // =========================================================================
    @Nested
    @DisplayName("4. Empty Body & Null Robustness Stress Tests")
    class EmptyBodyStressTests {

        @Test
        @DisplayName("validateThresholds với request null -> An toàn, không ném NPE")
        void validateThresholds_nullRequest_doesNotThrow() {
            assertDoesNotThrow(() -> service.validateThresholds(baseEntity, null));
        }

        @Test
        @DisplayName("updateRule với body rỗng {} -> Giữ nguyên giá trị cũ và lưu thành công")
        void emptyBody_leavesValuesUnchanged() {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCategorySafetyRuleRequest emptyRequest = new UpdateCategorySafetyRuleRequest();

            CategorySafetyRule updated = service.updateRule(sampleCategoryId, emptyRequest);
            assertEquals(0.50, updated.getCautionWaveHeightM());
            assertEquals(0.80, updated.getMaxWaveHeightM());
            assertEquals(12.0, updated.getCautionWindSpeedKmh());
            assertEquals(20.0, updated.getMaxWindSpeedKmh());
            assertEquals(28.0, updated.getMaxWindGustKmh());
            assertEquals(0.30, updated.getMaxOceanCurrentMs());
            assertEquals(2000.0, updated.getMinVisibilityM());
        }

        @Test
        @DisplayName("MockMvc PATCH với body rỗng {} -> Trả về 200 OK")
        void mockMvc_patch_emptyJson_returns200() throws Exception {
            when(repository.findByCategoryId(sampleCategoryId)).thenReturn(Optional.of(baseEntity));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(patch("/api/admin/category-safety-rules/{categoryId}", sampleCategoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categorySlug", is("cheo-sup-kayak")))
                    .andExpect(jsonPath("$.cautionWaveHeightM", is(0.50)))
                    .andExpect(jsonPath("$.maxWaveHeightM", is(0.80)));
        }

        @Test
        @DisplayName("updateRule với categoryId null -> Ném CategorySafetyRuleNotFoundException (404)")
        void updateRule_nullCategoryId_throwsException() {
            UpdateCategorySafetyRuleRequest request = new UpdateCategorySafetyRuleRequest();
            assertThrows(
                    CategorySafetyRuleNotFoundException.class,
                    () -> service.updateRule(null, request)
            );
        }
    }

    // =========================================================================
    // SECTION 5: STRESS TEST FALLBACK REGISTRY UNDER REPOSITORY CHAOS (ZERO 500s)
    // =========================================================================
    @Nested
    @DisplayName("5. Database Chaos & Fallback Stress Tests (Zero 500 Exceptions)")
    class DatabaseChaosFallbackTests {

        @Test
        @DisplayName("getAllRules khi repository.findAll() trả về null -> Fallback về 6 rules built-in")
        void getAllRules_whenRepositoryReturnsNull_returnsFallback() {
            when(repository.findAll()).thenReturn(null);

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(6, rules.size());
        }

        @Test
        @DisplayName("getAllRules khi DB mất kết nối (DataAccessResourceFailureException) -> 0 lỗi 500, fallback về 6 rules")
        void getAllRules_jdbcConnectionError_returnsFallback() {
            when(repository.findAll()).thenThrow(new DataAccessResourceFailureException("Failed to connect to PostgreSQL"));

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(6, rules.size());
        }

        @Test
        @DisplayName("getAllRules khi DB bị QueryTimeoutException -> 0 lỗi 500, fallback về 6 rules")
        void getAllRules_queryTimeout_returnsFallback() {
            when(repository.findAll()).thenThrow(new QueryTimeoutException("Query timeout after 5000ms"));

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(6, rules.size());
        }

        @Test
        @DisplayName("getAllRules khi gặp RuntimeException bất ngờ -> Bắt trọn vẹn, 0 lỗi 500")
        void getAllRules_unexpectedRuntimeException_returnsFallback() {
            when(repository.findAll()).thenThrow(new RuntimeException("Unexpected driver crash"));

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(6, rules.size());
        }

        @Test
        @DisplayName("getRuleByCategorySlug với slug null -> Trả về DEFAULT_RULE mà không gọi DB")
        void getRuleByCategorySlug_nullSlug_returnsDefaultRuleWithoutDbQuery() {
            CategorySafetyRule rule = service.getRuleByCategorySlug(null);

            assertNotNull(rule);
            assertEquals("default", rule.getCategorySlug());
            verify(repository, never()).findByCategorySlug(any());
        }

        @Test
        @DisplayName("getRuleByCategorySlug với slug rỗng/blank -> Trả về DEFAULT_RULE mà không gọi DB")
        void getRuleByCategorySlug_blankSlug_returnsDefaultRuleWithoutDbQuery() {
            CategorySafetyRule rule = service.getRuleByCategorySlug("    ");

            assertNotNull(rule);
            assertEquals("default", rule.getCategorySlug());
            verify(repository, never()).findByCategorySlug(any());
        }

        @Test
        @DisplayName("getRuleByCategorySlug khi DB sập (DataAccessResourceFailureException) với slug 'cano-du-bay' -> Fallback đúng rule cano-du-bay")
        void getRuleByCategorySlug_dbDown_returnsSpecificRegistryRule() {
            when(repository.findByCategorySlug("cano-du-bay"))
                    .thenThrow(new DataAccessResourceFailureException("Connection refused"));

            CategorySafetyRule rule = service.getRuleByCategorySlug("cano-du-bay");

            assertNotNull(rule);
            assertEquals("cano-du-bay", rule.getCategorySlug());
            assertEquals(0.70, rule.getCautionWaveHeightM());
            assertEquals(1.00, rule.getMaxWaveHeightM());
            assertEquals(20.0, rule.getCautionWindSpeedKmh());
            assertEquals(28.0, rule.getMaxWindSpeedKmh());
        }

        @Test
        @DisplayName("getRuleByCategorySlug khi DB sập với slug lạ 'unregistered-activity' -> Fallback về DEFAULT_RULE")
        void getRuleByCategorySlug_dbDown_unknownSlug_returnsDefaultRule() {
            when(repository.findByCategorySlug("unregistered-activity"))
                    .thenThrow(new DataAccessResourceFailureException("Connection refused"));

            CategorySafetyRule rule = service.getRuleByCategorySlug("unregistered-activity");

            assertNotNull(rule);
            assertEquals("default", rule.getCategorySlug());
        }

        @Test
        @DisplayName("getRuleByCategoryId với categoryId null -> Trả về DEFAULT_RULE")
        void getRuleByCategoryId_nullId_returnsDefaultRule() {
            CategorySafetyRule rule = service.getRuleByCategoryId(null);
            assertNotNull(rule);
            assertEquals("default", rule.getCategorySlug());
        }

        @Test
        @DisplayName("getRuleByCategoryId khi DB ném DataAccessException -> Không sinh 500, ném 404 CategorySafetyRuleNotFoundException")
        void getRuleByCategoryId_dbError_throwsNotFoundNot500() {
            UUID randomId = UUID.randomUUID();
            when(repository.findByCategoryId(randomId))
                    .thenThrow(new DataAccessResourceFailureException("DB connection broken"));

            assertThrows(
                    CategorySafetyRuleNotFoundException.class,
                    () -> service.getRuleByCategoryId(randomId)
            );
        }

        @Test
        @DisplayName("MockMvc GET /api/admin/category-safety-rules khi DB sập hoàn toàn -> Trả về HTTP 200 OK với 6 rules (Không 500)")
        void mockMvc_getAllRules_whenDbFails_returns200OkWithRegistry() throws Exception {
            when(repository.findAll())
                    .thenThrow(new DataAccessResourceFailureException("DB down"));

            mockMvc.perform(get("/api/admin/category-safety-rules")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[?(@.categorySlug == 'cheo-sup-kayak')]").exists())
                    .andExpect(jsonPath("$[?(@.categorySlug == 'lan-ngam-san-ho')]").exists())
                    .andExpect(jsonPath("$[?(@.categorySlug == 'cano-du-bay')]").exists())
                    .andExpect(jsonPath("$[?(@.categorySlug == 'mo-to-nuoc-jetski')]").exists())
                    .andExpect(jsonPath("$[?(@.categorySlug == 'truot-phao-chuoi')]").exists())
                    .andExpect(jsonPath("$[?(@.categorySlug == 'du-thuyen-ngam-hoang-hon')]").exists());
        }

        @Test
        @DisplayName("MockMvc GET /api/admin/category-safety-rules khi DB trống (Empty List) -> Trả về HTTP 200 OK với 6 rules (Không 500)")
        void mockMvc_getAllRules_whenDbEmpty_returns200OkWithRegistry() throws Exception {
            when(repository.findAll()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/category-safety-rules")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[0].categorySlug").exists());
        }
    }
}
