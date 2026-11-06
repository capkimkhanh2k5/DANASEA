package com.danasea.backend.modules.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.CommissionRuleDto;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.ConfigDto;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.UpdateCommissionRuleRequest;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.UpdateConfigRequest;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.CommissionRuleJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaCommissionRuleRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;

class AdminSystemConfigControllerTest {

    private final JpaSystemConfigRepository repository = mock(JpaSystemConfigRepository.class);
    private final JpaCommissionRuleRepository commissionRuleRepository = mock(JpaCommissionRuleRepository.class);
    private final JpaCategoryRepository categoryRepository = mock(JpaCategoryRepository.class);
    private final AdminSystemConfigController controller = new AdminSystemConfigController(
            repository,
            commissionRuleRepository,
            categoryRepository
    );
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurity() {
        var auth = new UsernamePasswordAuthenticationToken(adminId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Admin can retrieve all system configs")
    void getAllConfigsReturnsList() {
        SystemConfigJpaEntity entity = new SystemConfigJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setKey("PLATFORM_COMMISSION_RATE");
        entity.setValue("10.0");
        entity.setDescription("Tỷ lệ hoa hồng mặc định sàn (%)");
        entity.setUpdatedAt(OffsetDateTime.now());

        when(repository.findAll()).thenReturn(List.of(entity));

        ResponseEntity<List<ConfigDto>> response = controller.getAllConfigs();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).key()).isEqualTo("PLATFORM_COMMISSION_RATE");
        assertThat(response.getBody().get(0).value()).isEqualTo("10.0");
    }

    @Test
    @DisplayName("Admin can update PLATFORM_COMMISSION_RATE with valid percentage")
    void updateCommissionRateSuccess() {
        SystemConfigJpaEntity entity = new SystemConfigJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setKey("PLATFORM_COMMISSION_RATE");
        entity.setValue("10.0");

        when(repository.findByKey("PLATFORM_COMMISSION_RATE")).thenReturn(Optional.of(entity));
        when(repository.save(any(SystemConfigJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ConfigDto> response = controller.updateConfig(
                "PLATFORM_COMMISSION_RATE",
                new UpdateConfigRequest("12.5", "Updated rate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().value()).isEqualTo("12.5");
        assertThat(response.getBody().description()).isEqualTo("Updated rate");
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("Admin updating PLATFORM_COMMISSION_RATE with negative rate throws IllegalArgumentException")
    void updateCommissionRateNegativeThrows() {
        assertThatThrownBy(() -> controller.updateConfig(
                "PLATFORM_COMMISSION_RATE",
                new UpdateConfigRequest("-5", "Invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0% and 100%");
    }

    @Test
    @DisplayName("Admin updating PLATFORM_COMMISSION_RATE with over 100% throws IllegalArgumentException")
    void updateCommissionRateOver100Throws() {
        assertThatThrownBy(() -> controller.updateConfig(
                "PLATFORM_COMMISSION_RATE",
                new UpdateConfigRequest("105", "Invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0% and 100%");
    }

    @Test
    @DisplayName("Admin can retrieve all dynamic commission rules")
    void getCommissionRulesReturnsOrderedList() {
        CommissionRuleJpaEntity rule = new CommissionRuleJpaEntity();
        rule.setId(UUID.randomUUID());
        rule.setRuleCode("ONBOARDING_NEW_VENDOR_90D");
        rule.setRuleName("Ưu đãi Đối tác mới (3 tháng đầu)");
        rule.setTargetType("ONBOARDING");
        rule.setCommissionRate(new BigDecimal("0.0500"));
        rule.setDurationDays(90);
        rule.setPriority(100);
        rule.setIsActive(true);

        when(categoryRepository.findAll()).thenReturn(List.of());
        when(commissionRuleRepository.findAllByOrderByPriorityDescCreatedAtAsc()).thenReturn(List.of(rule));

        ResponseEntity<List<CommissionRuleDto>> response = controller.getCommissionRules();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).ruleCode()).isEqualTo("ONBOARDING_NEW_VENDOR_90D");
        assertThat(response.getBody().get(0).commissionRatePercent()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Admin can update commission rule rate, duration, and active status")
    void updateCommissionRuleSuccess() {
        UUID ruleId = UUID.randomUUID();
        CommissionRuleJpaEntity rule = new CommissionRuleJpaEntity();
        rule.setId(ruleId);
        rule.setRuleCode("CATEGORY_THUE_SUP");
        rule.setRuleName("Thuê ván SUP");
        rule.setTargetType("CATEGORY");
        rule.setCommissionRate(new BigDecimal("0.0700"));
        rule.setPriority(50);
        rule.setIsActive(true);

        when(commissionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(commissionRuleRepository.save(any(CommissionRuleJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<CommissionRuleDto> response = controller.updateCommissionRule(
                ruleId,
                new UpdateCommissionRuleRequest(new BigDecimal("8.5"), null, false, "Điều chỉnh phí mùa cao điểm")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().commissionRate()).isEqualByComparingTo("0.0850");
        assertThat(response.getBody().commissionRatePercent()).isEqualByComparingTo("8.5");
        assertThat(response.getBody().isActive()).isFalse();
        assertThat(response.getBody().description()).isEqualTo("Điều chỉnh phí mùa cao điểm");
    }
}
