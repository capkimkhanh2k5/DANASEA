package com.danasea.backend.modules.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.ConfigDto;
import com.danasea.backend.modules.admin.presentation.AdminSystemConfigController.UpdateConfigRequest;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities.SystemConfigJpaEntity;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;

class AdminSystemConfigControllerTest {

    private final JpaSystemConfigRepository repository = mock(JpaSystemConfigRepository.class);
    private final AdminSystemConfigController controller = new AdminSystemConfigController(repository);
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
}
