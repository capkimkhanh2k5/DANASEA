package com.danasea.backend.modules.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.DataAccessResourceFailureException;

import com.danasea.backend.modules.weather.application.dtos.UpdateCategorySafetyRuleRequest;
import com.danasea.backend.modules.weather.domain.exceptions.CategorySafetyRuleNotFoundException;
import com.danasea.backend.modules.weather.domain.exceptions.InvalidSafetyRuleThresholdException;
import com.danasea.backend.modules.weather.domain.models.CategorySafetyRule;
import com.danasea.backend.modules.weather.domain.services.CategorySafetyRuleService;
import com.danasea.backend.modules.weather.infrastructure.persistence.entities.CategorySafetyRuleJpaEntity;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaCategorySafetyRuleRepository;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorySafetyRuleService Tests")
class CategorySafetyRuleServiceTest {

    @Mock
    private JpaCategorySafetyRuleRepository repository;

    private CategorySafetyRuleService service;

    private UUID categoryId;
    private CategorySafetyRuleJpaEntity sampleEntity;

    @BeforeEach
    void setUp() {
        service = new CategorySafetyRuleService(repository);
        categoryId = UUID.randomUUID();

        sampleEntity = CategorySafetyRuleJpaEntity.builder()
                .categoryId(categoryId)
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
    }

    @Nested
    @DisplayName("Fallback Mechanism Tests (Zero 500 Errors)")
    class FallbackTests {

        @Test
        @DisplayName("DB trống rỗng -> Fallback về 6 quy tắc benchmark trong built-in REGISTRY")
        void getAllRules_whenDbEmpty_returnsRegistryFallback() {
            when(repository.findAll()).thenReturn(Collections.emptyList());

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(6, rules.size());
            assertTrue(rules.stream().anyMatch(r -> "cheo-sup-kayak".equals(r.getCategorySlug())));
            assertTrue(rules.stream().anyMatch(r -> "lan-ngam-san-ho".equals(r.getCategorySlug())));
            assertTrue(rules.stream().anyMatch(r -> "cano-du-bay".equals(r.getCategorySlug())));
        }

        @Test
        @DisplayName("DB gặp sự cố (DataAccessException) -> Fallback về REGISTRY mà không quăng lỗi")
        void getAllRules_whenDbError_returnsRegistryFallbackGracefully() {
            when(repository.findAll()).thenThrow(new DataAccessResourceFailureException("PostgreSQL connection refused"));

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertFalse(rules.isEmpty());
            assertEquals(6, rules.size());
        }

        @Test
        @DisplayName("Slug không có trong DB -> Fallback về quy tắc cụ thể trong REGISTRY")
        void getRuleByCategorySlug_whenNotFoundInDb_returnsRegistryRule() {
            when(repository.findByCategorySlug("cano-du-bay")).thenReturn(Optional.empty());

            CategorySafetyRule rule = service.getRuleByCategorySlug("cano-du-bay");

            assertNotNull(rule);
            assertEquals("cano-du-bay", rule.getCategorySlug());
            assertEquals(0.70, rule.getCautionWaveHeightM());
            assertEquals(1.00, rule.getMaxWaveHeightM());
        }

        @Test
        @DisplayName("Slug hoàn toàn lạ/không tồn tại -> Fallback về DEFAULT_RULE")
        void getRuleByCategorySlug_whenUnknownSlug_returnsDefaultRule() {
            when(repository.findByCategorySlug("unknown-marine-sport")).thenReturn(Optional.empty());

            CategorySafetyRule rule = service.getRuleByCategorySlug("unknown-marine-sport");

            assertNotNull(rule);
            assertEquals("default", rule.getCategorySlug());
            assertEquals(0.70, rule.getCautionWaveHeightM());
            assertEquals(1.20, rule.getMaxWaveHeightM());
        }

        @Test
        @DisplayName("DB lỗi khi tìm theo slug -> Fallback về REGISTRY mà không quăng exception")
        void getRuleByCategorySlug_whenDbError_returnsFallbackGracefully() {
            when(repository.findByCategorySlug("cheo-sup-kayak"))
                    .thenThrow(new DataAccessResourceFailureException("Timeout"));

            CategorySafetyRule rule = service.getRuleByCategorySlug("cheo-sup-kayak");

            assertNotNull(rule);
            assertEquals("cheo-sup-kayak", rule.getCategorySlug());
            assertEquals(0.80, rule.getMaxWaveHeightM());
        }
    }

    @Nested
    @DisplayName("Database Retrieval Tests")
    class DatabaseRetrievalTests {

        @Test
        @DisplayName("Lấy tất cả quy tắc từ DB thành công")
        void getAllRules_fromDb_success() {
            when(repository.findAll()).thenReturn(List.of(sampleEntity));

            List<CategorySafetyRule> rules = service.getAllRules();

            assertNotNull(rules);
            assertEquals(1, rules.size());
            assertEquals("cheo-sup-kayak", rules.get(0).getCategorySlug());
            assertEquals(0.80, rules.get(0).getMaxWaveHeightM());
        }

        @Test
        @DisplayName("Lấy quy tắc theo slug từ DB thành công")
        void getRuleByCategorySlug_fromDb_success() {
            when(repository.findByCategorySlug("cheo-sup-kayak")).thenReturn(Optional.of(sampleEntity));

            CategorySafetyRule rule = service.getRuleByCategorySlug("cheo-sup-kayak");

            assertNotNull(rule);
            assertEquals("cheo-sup-kayak", rule.getCategorySlug());
            assertEquals(0.80, rule.getMaxWaveHeightM());
            assertEquals(20.0, rule.getMaxWindSpeedKmh());
        }
    }

    @Nested
    @DisplayName("Update Safety Rule Tests & Validations")
    class UpdateSafetyRuleTests {

        @Test
        @DisplayName("Cập nhật ngưỡng an toàn thành công")
        void updateRule_success() {
            when(repository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));
            when(repository.save(any(CategorySafetyRuleJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.60)
                    .maxWaveHeightM(0.90)
                    .cautionWindSpeedKmh(14.0)
                    .maxWindSpeedKmh(22.0)
                    .build();

            CategorySafetyRule updated = service.updateRule(categoryId, request);

            assertNotNull(updated);
            assertEquals(0.60, updated.getCautionWaveHeightM());
            assertEquals(0.90, updated.getMaxWaveHeightM());
            assertEquals(14.0, updated.getCautionWindSpeedKmh());
            assertEquals(22.0, updated.getMaxWindSpeedKmh());

            ArgumentCaptor<CategorySafetyRuleJpaEntity> captor = forClass(CategorySafetyRuleJpaEntity.class);
            verify(repository).save(captor.capture());
            assertEquals(0.60, captor.getValue().getCautionWaveHeightM());
            assertEquals(0.90, captor.getValue().getMaxWaveHeightM());
        }

        @Test
        @DisplayName("Lỗi xác thực: cautionWaveHeightM > maxWaveHeightM -> Ném InvalidSafetyRuleThresholdException")
        void updateRule_whenCautionWaveExceedsMaxWave_throwsException() {
            when(repository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(1.20)
                    .maxWaveHeightM(0.80)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(categoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution wave threshold"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Lỗi xác thực: Cập nhật cautionWaveHeightM đơn lẻ vượt quá max hiện có -> Bị chặn")
        void updateRule_whenSingleCautionWaveExceedsExistingMax_throwsException() {
            // sampleEntity maxWaveHeightM = 0.80
            when(repository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWaveHeightM(0.95)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(categoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution wave threshold"));
        }

        @Test
        @DisplayName("Lỗi xác thực: cautionWindSpeedKmh > maxWindSpeedKmh -> Ném InvalidSafetyRuleThresholdException")
        void updateRule_whenCautionWindExceedsMaxWind_throwsException() {
            when(repository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .cautionWindSpeedKmh(30.0)
                    .maxWindSpeedKmh(20.0)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(categoryId, request)
            );
            assertTrue(ex.getMessage().contains("caution sustained-wind threshold"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Lỗi xác thực: Giá trị âm -> Ném InvalidSafetyRuleThresholdException")
        void updateRule_whenNegativeValue_throwsException() {
            when(repository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWaveHeightM(-1.0)
                    .build();

            InvalidSafetyRuleThresholdException ex = assertThrows(
                    InvalidSafetyRuleThresholdException.class,
                    () -> service.updateRule(categoryId, request)
            );
            assertTrue(ex.getMessage().contains("must not be negative"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("CategoryId không tồn tại trong DB -> Ném CategorySafetyRuleNotFoundException")
        void updateRule_whenNotFound_throwsNotFoundException() {
            UUID unknownId = UUID.randomUUID();
            when(repository.findByCategoryId(unknownId)).thenReturn(Optional.empty());
            when(repository.findById(unknownId)).thenReturn(Optional.empty());

            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWaveHeightM(1.0)
                    .build();

            assertThrows(
                    CategorySafetyRuleNotFoundException.class,
                    () -> service.updateRule(unknownId, request)
            );
            verify(repository, never()).save(any());
        }
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("categorySafetyRules");
        }

        @Bean
        public CategorySafetyRuleService cachedCategoryService(JpaCategorySafetyRuleRepository repo) {
            return new CategorySafetyRuleService(repo);
        }

        @Bean
        public JpaCategorySafetyRuleRepository mockRepository() {
            return Mockito.mock(JpaCategorySafetyRuleRepository.class);
        }
    }

    @SpringJUnitConfig(CacheTestConfig.class)
    @Nested
    @DisplayName("Spring Cache Caching & Eviction Tests")
    class SpringCacheTests {

        @Autowired
        private CategorySafetyRuleService cachedCategoryService;

        @Autowired
        private JpaCategorySafetyRuleRepository mockRepository;

        @Autowired
        private CacheManager testCacheManager;

        @BeforeEach
        void clearCache() {
            var cache = testCacheManager.getCache("categorySafetyRules");
            if (cache != null) {
                cache.clear();
            }
            Mockito.reset(mockRepository);
        }

        @Test
        @DisplayName("Gọi getAllRules 2 lần liên tiếp -> Chỉ truy vấn DB 1 lần (Lần 2 ăn Cache)")
        void getAllRules_isCached() {
            when(mockRepository.findAll()).thenReturn(List.of(sampleEntity));

            var rules1 = cachedCategoryService.getAllRules();
            var rules2 = cachedCategoryService.getAllRules();

            assertEquals(1, rules1.size());
            assertEquals(1, rules2.size());
            verify(mockRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Khi updateRule -> Toàn bộ Cache 'categorySafetyRules' bị xóa (Evicted)")
        void updateRule_evictsCache() {
            when(mockRepository.findAll()).thenReturn(List.of(sampleEntity));
            when(mockRepository.findByCategoryId(categoryId)).thenReturn(Optional.of(sampleEntity));
            when(mockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // Populate cache
            cachedCategoryService.getAllRules();
            verify(mockRepository, times(1)).findAll();

            // Calling updateRule must evict cache
            UpdateCategorySafetyRuleRequest request = UpdateCategorySafetyRuleRequest.builder()
                    .maxWaveHeightM(1.0)
                    .build();
            cachedCategoryService.updateRule(categoryId, request);

            // Subsequent call must query DB again
            cachedCategoryService.getAllRules();
            verify(mockRepository, times(2)).findAll();
        }
    }
}
