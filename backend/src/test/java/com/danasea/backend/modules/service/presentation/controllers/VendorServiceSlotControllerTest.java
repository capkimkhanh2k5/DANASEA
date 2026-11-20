package com.danasea.backend.modules.service.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.danasea.backend.modules.service.application.usecases.BatchCreateServiceSlotsUseCase;
import com.danasea.backend.modules.service.application.usecases.CreateServiceSlotUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServiceSlotsUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceSlotUseCase;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.handlers.ServiceExceptionHandler;
import com.danasea.backend.security.authentication.application.ports.TokenProvider;
import com.danasea.backend.security.authentication.application.ports.UserAccountPort;
import com.danasea.backend.security.authorization.application.ports.AuthorizationPort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = { VendorServiceSlotController.class })
@Import({ VendorServiceSlotControllerTest.TestSecurityConfig.class, ServiceExceptionHandler.class })
@DisplayName("VendorServiceSlotControllerTest — HTTP Error Mapping & RBAC")
class VendorServiceSlotControllerTest {

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, authEx) -> res.sendError(401, "Unauthorized")))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        UserDetailsService testUserDetailsService() {
            return new InMemoryUserDetailsManager();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenProvider tokenProvider;

    @MockitoBean
    private UserAccountPort userAccountPort;

    @MockitoBean
    private AuthorizationPort authorizationPort;

    @MockitoBean
    private GetVendorServiceSlotsUseCase getVendorServiceSlotsUseCase;

    @MockitoBean
    private CreateServiceSlotUseCase createServiceSlotUseCase;

    @MockitoBean
    private BatchCreateServiceSlotsUseCase batchCreateServiceSlotsUseCase;

    @MockitoBean
    private UpdateServiceSlotUseCase updateServiceSlotUseCase;

    @Test
    @DisplayName("PATCH past slot -> UpdateServiceSlotUseCase throws IllegalArgumentException -> returns HTTP 400 Bad Request")
    void patchPastSlot_Returns400BadRequest() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(updateServiceSlotUseCase.execute(eq(userId), eq(serviceId), eq(slotId), any(UpdateServiceSlotRequest.class)))
                .thenThrow(new IllegalArgumentException("Cannot modify a slot from a past date"));

        String payload = """
                {
                    "status": "CLOSED"
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/slots/{slotId}", serviceId, slotId)
                        .with(user(userId.toString()).roles("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH valid slot for VENDOR -> returns HTTP 200 OK")
    void patchValidSlot_Returns200Ok() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ServiceSlotResponse response = ServiceSlotResponse.builder()
                .id(slotId)
                .serviceId(serviceId)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(SlotStatus.CLOSED)
                .inventoryType(InventoryType.PERSON_LIMIT)
                .capacity(10)
                .bookedCount(0)
                .availableCapacity(10)
                .units(List.of())
                .build();

        when(updateServiceSlotUseCase.execute(eq(userId), eq(serviceId), eq(slotId), any(UpdateServiceSlotRequest.class)))
                .thenReturn(response);

        String payload = """
                {
                    "status": "CLOSED"
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/slots/{slotId}", serviceId, slotId)
                        .with(user(userId.toString()).roles("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(slotId.toString()))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("PATCH slot as CUSTOMER -> returns HTTP 403 Forbidden")
    void patchSlotAsCustomer_Returns403Forbidden() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        String payload = """
                {
                    "status": "CLOSED"
                }
                """;

        mockMvc.perform(patch("/api/vendor/services/{id}/slots/{slotId}", serviceId, slotId)
                        .with(user("customer_user").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
