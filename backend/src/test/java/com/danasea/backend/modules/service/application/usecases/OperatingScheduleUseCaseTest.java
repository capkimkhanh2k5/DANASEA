package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OperatingSchedule;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperatingScheduleUseCaseTest {

    @Mock private ServiceRepositoryPort serviceRepository;
    @Mock private VendorPort vendorPort;
    @Mock private OperatingScheduleStore store;
    @Mock private EquipmentAssignmentService equipment;
    @Mock private ServiceOptionRepositoryPort serviceOptionRepository;

    private OperatingScheduleUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private LocalDate tomorrow;
    private Service publishedService;

    @BeforeEach
    void setUp() {
        useCase = new OperatingScheduleUseCase(serviceRepository, vendorPort, store, equipment, serviceOptionRepository);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        publishedService = Service.builder()
                .id(serviceId)
                .vendorId(vendorId)
                .status(ServiceStatus.PUBLISHED)
                .build();

        lenient().when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        lenient().when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(publishedService));
        lenient().when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(publishedService));
    }

    @Test
    @DisplayName("Scheduler chạy: Legacy Rule từ JSONB cũ không có optionIds -> bỏ qua, không tạo ca rỗng Option")
    void scheduler_legacyRuleWithoutOptionIds_doesNotCreateSlot() {
        OperatingSchedule.WeeklyRule legacyRule = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of() // no optionIds
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(legacyRule));
        when(store.find(serviceId)).thenReturn(Optional.of(schedule));
        when(store.blocks(eq(serviceId), any(), any())).thenReturn(false);

        OperatingSchedule.Departure candidate = new OperatingSchedule.Departure(tomorrow, legacyRule);
        useCase.generate(serviceId, candidate);

        verify(store, never()).insertDeparture(any(), any());
    }

    @Test
    @DisplayName("Scheduler chạy: Option bị INACTIVE sau khi lưu lịch -> bỏ qua, không tạo ca với Option ngưng hoạt động")
    void scheduler_configuredOptionBecomesInactive_materializationSkipsSlot() {
        UUID inactiveOptionId = UUID.randomUUID();
        OperatingSchedule.WeeklyRule rule = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of(inactiveOptionId)
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(rule));
        when(store.find(serviceId)).thenReturn(Optional.of(schedule));
        when(store.blocks(eq(serviceId), any(), any())).thenReturn(false);

        ServiceOption inactiveOption = ServiceOption.builder()
                .id(inactiveOptionId)
                .serviceId(serviceId)
                .name("Old Inactive Package")
                .status(OptionStatus.INACTIVE)
                .build();
        when(serviceOptionRepository.findById(inactiveOptionId)).thenReturn(Optional.of(inactiveOption));

        OperatingSchedule.Departure candidate = new OperatingSchedule.Departure(tomorrow, rule);
        useCase.generate(serviceId, candidate);

        verify(store, never()).insertDeparture(any(), any());
    }

    @Test
    @DisplayName("Scheduler chạy: Rule có 2 Options (1 Active và 1 Inactive) -> chỉ sinh ca với Option còn Active")
    void scheduler_ruleWithMixedOptions_materializesOnlyActiveOptions() {
        UUID activeOptId = UUID.randomUUID();
        UUID inactiveOptId = UUID.randomUUID();
        OperatingSchedule.WeeklyRule rule = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of(activeOptId, inactiveOptId)
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(rule));
        when(store.find(serviceId)).thenReturn(Optional.of(schedule));
        when(store.blocks(eq(serviceId), any(), any())).thenReturn(false);

        ServiceOption activeOption = ServiceOption.builder()
                .id(activeOptId)
                .serviceId(serviceId)
                .name("Active Package")
                .optionType(OptionType.SHARED)
                .status(OptionStatus.ACTIVE)
                .build();
        ServiceOption inactiveOption = ServiceOption.builder()
                .id(inactiveOptId)
                .serviceId(serviceId)
                .name("Inactive Package")
                .optionType(OptionType.SHARED)
                .status(OptionStatus.INACTIVE)
                .build();

        when(serviceOptionRepository.findById(activeOptId)).thenReturn(Optional.of(activeOption));
        when(serviceOptionRepository.findById(inactiveOptId)).thenReturn(Optional.of(inactiveOption));
        when(store.insertDeparture(eq(serviceId), any())).thenReturn(Optional.of(UUID.randomUUID()));

        OperatingSchedule.Departure candidate = new OperatingSchedule.Departure(tomorrow, rule);
        useCase.generate(serviceId, candidate);

        ArgumentCaptor<OperatingSchedule.Departure> departureCaptor = ArgumentCaptor.forClass(OperatingSchedule.Departure.class);
        verify(store).insertDeparture(eq(serviceId), departureCaptor.capture());
        assertThat(departureCaptor.getValue().rule().optionIds()).containsExactly(activeOptId);
    }

    @Test
    @DisplayName("Lưu lịch (PUT): Rule không có optionId -> cho phép lưu cấu hình lịch nhưng không sinh ca rỗng gói")
    void putSchedule_withoutOptionIds_doesNotCreateSlots() {
        OperatingSchedule.WeeklyRule ruleWithoutOptions = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of()
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(ruleWithoutOptions));

        var result = useCase.put(userId, serviceId, schedule);

        assertThat(result.createdSlots()).isZero();
        verify(store, never()).insertDeparture(any(), any());
    }

    @Test
    @DisplayName("Lưu lịch (PUT): Option thuộc Service khác -> bị từ chối")
    void putSchedule_withOptionBelongingToDifferentService_fails() {
        UUID otherServiceOptId = UUID.randomUUID();
        OperatingSchedule.WeeklyRule rule = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of(otherServiceOptId)
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(rule));

        ServiceOption foreignOption = ServiceOption.builder()
                .id(otherServiceOptId)
                .serviceId(UUID.randomUUID()) // different service!
                .name("Foreign Option")
                .status(OptionStatus.ACTIVE)
                .build();
        when(serviceOptionRepository.findById(otherServiceOptId)).thenReturn(Optional.of(foreignOption));

        assertThatThrownBy(() -> useCase.put(userId, serviceId, schedule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to service");

        verify(store, never()).save(any(), any());
    }

    @Test
    @DisplayName("Lưu lịch (PUT): Option đang INACTIVE -> bị từ chối")
    void putSchedule_withInactiveOption_fails() {
        UUID inactiveOptId = UUID.randomUUID();
        OperatingSchedule.WeeklyRule rule = new OperatingSchedule.WeeklyRule(
                tomorrow.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(10, 0),
                InventoryType.PERSON_LIMIT, 10, List.of(), List.of(inactiveOptId)
        );
        OperatingSchedule schedule = new OperatingSchedule(tomorrow, tomorrow.plusDays(7), true, List.of(rule));

        ServiceOption inactiveOption = ServiceOption.builder()
                .id(inactiveOptId)
                .serviceId(serviceId)
                .name("Inactive Option")
                .status(OptionStatus.INACTIVE)
                .build();
        when(serviceOptionRepository.findById(inactiveOptId)).thenReturn(Optional.of(inactiveOption));

        assertThatThrownBy(() -> useCase.put(userId, serviceId, schedule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not active");

        verify(store, never()).save(any(), any());
    }
}
