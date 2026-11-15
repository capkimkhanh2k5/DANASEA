package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.SlotTemplateRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.presentation.ApiConflictException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchCreateServiceSlotsUseCaseTest {

    @Mock private ServiceRepositoryPort serviceRepository;
    @Mock private ServiceSlotRepositoryPort serviceSlotRepository;
    @Mock private VendorPort vendorPort;
    @Mock private OperatingScheduleStore scheduleStore;

    private CreateServiceSlotUseCase createServiceSlotUseCase;
    private BatchCreateServiceSlotsUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private LocalDate nextMonday;
    private LocalDate nextSunday;

    @BeforeEach
    void setUp() {
        createServiceSlotUseCase = new CreateServiceSlotUseCase(
                serviceRepository,
                serviceSlotRepository,
                vendorPort,
                scheduleStore
        );
        useCase = new BatchCreateServiceSlotsUseCase(createServiceSlotUseCase);

        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        // Calculate next Monday in Vietnam zone
        LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
        nextMonday = today.plusDays(7 - today.getDayOfWeek().getValue() + 1);
        nextSunday = nextMonday.plusDays(6);

        when(serviceSlotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void authorize(UUID ownerVendorId) {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        Service service = Service.builder()
                .id(serviceId)
                .vendorId(ownerVendorId)
                .build();
        when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(service));
    }

    private SlotTemplateRequest template(LocalTime start, LocalTime end, int capacity) {
        return new SlotTemplateRequest(start, end, InventoryType.PERSON_LIMIT, capacity, null);
    }

    @Test
    @DisplayName("Tạo đúng 1 ca (1 ngày × 1 ca)")
    void oneSlot_createsOneSlot() {
        authorize(vendorId);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                List.of(template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10))
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(1);
        assertThat(response.slots()).hasSize(1);
        assertThat(response.slots().getFirst().date()).isEqualTo(nextMonday);
        assertThat(response.slots().getFirst().startTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("Tạo đúng 3 ca (3 ngày × 1 ca)")
    void threeSlots_createsThreeSlots() {
        authorize(vendorId);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday.plusDays(2),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                List.of(template(LocalTime.of(9, 0), LocalTime.of(11, 0), 15))
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(3);
        assertThat(response.slots()).hasSize(3);
    }

    @Test
    @DisplayName("Tạo đúng 6 ca (3 ngày × 2 ca)")
    void sixSlots_threeDaysTwoSlots() {
        authorize(vendorId);

        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10),
                template(LocalTime.of(14, 0), LocalTime.of(16, 0), 15)
        );

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextSunday,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                templates
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(6);
        assertThat(response.slots()).hasSize(6);
    }

    @Test
    @DisplayName("2 ca/ngày × 7 ngày → tạo đúng 14 ca")
    void fourteenSlots_sevenDaysTwoSlots() {
        authorize(vendorId);

        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10),
                template(LocalTime.of(14, 0), LocalTime.of(16, 0), 15)
        );

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextSunday,
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                ),
                templates
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(14);
        assertThat(response.slots()).hasSize(14);
    }

    @Test
    @DisplayName("Trùng startTime trong cùng request → bị từ chối")
    void duplicateStartTimeInTemplates_rejected() {
        authorize(vendorId);

        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10),
                template(LocalTime.of(8, 0), LocalTime.of(11, 0), 15)
        );

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                templates
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate start time in slot templates: 08:00");
    }

    @Test
    @DisplayName("Ca chồng khoảng giờ nhưng khác startTime → ĐƯỢC PHÉP theo nghiệp vụ hiện có")
    void overlappingTimeIntervalsDifferentStartTime_isAllowed() {
        authorize(vendorId);

        // Ca 1: 08:00 - 10:00, Ca 2: 09:00 - 11:00 (chồng khoảng giờ 09:00-10:00 nhưng startTime khác nhau)
        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10),
                template(LocalTime.of(9, 0), LocalTime.of(11, 0), 15)
        );

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                templates
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(2);
        assertThat(response.slots()).hasSize(2);
        assertThat(response.slots().get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.slots().get(1).startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("Có ca trùng với dữ liệu đã tồn tại trong DB → từ chối an toàn, rollback")
    void duplicateExistingSlotInDb_abortsAndCreatesNothing() {
        authorize(vendorId);

        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10),
                template(LocalTime.of(14, 0), LocalTime.of(16, 0), 15)
        );

        LocalDate tuesday = nextMonday.plusDays(1);
        when(serviceSlotRepository.existsByServiceIdAndDateAndStartTime(serviceId, tuesday, LocalTime.of(8, 0)))
                .thenReturn(true);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextSunday,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                templates
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A departure already exists at this date and start time");
    }

    @Test
    @DisplayName("Gửi lại request trùng lặp → từ chối an toàn, không sinh duplicate")
    void resendingSameRequest_failsAndCreatesNoDuplicates() {
        authorize(vendorId);

        var templates = List.of(
                template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10)
        );

        when(serviceSlotRepository.existsByServiceIdAndDateAndStartTime(eq(serviceId), any(), any()))
                .thenReturn(true);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                templates
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A departure already exists at this date and start time");
    }

    @Test
    @DisplayName("Vendor không sở hữu service → chặn 403 UnauthorizedServiceAccessException")
    void vendorDoesNotOwnService_throwsUnauthorized() {
        UUID otherVendorId = UUID.randomUUID();
        authorize(otherVendorId);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextSunday,
                Set.of(DayOfWeek.MONDAY),
                List.of(template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10))
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
    }

    @Test
    @DisplayName("User chưa được duyệt làm Vendor → chặn VendorNotApprovedException")
    void vendorNotFound_throwsVendorNotApproved() {
        when(vendorPort.findByUserId(userId)).thenReturn(Optional.empty());

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextSunday,
                Set.of(DayOfWeek.MONDAY),
                List.of(template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10))
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(VendorNotApprovedException.class);
    }

    @Test
    @DisplayName("Ca trong quá khứ → bị chặn an toàn")
    void slotInThePast_rejectedBeforeCreation() {
        authorize(vendorId);

        LocalDate pastDate = LocalDate.now(Booking.VIETNAM_ZONE).minusDays(2);

        var request = new BatchCreateServiceSlotsRequest(
                pastDate,
                pastDate,
                Set.of(pastDate.getDayOfWeek()),
                List.of(template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10))
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create a slot in the past");
    }

    @Test
    @DisplayName("Trùng ngoại lệ lịch hoạt động → chặn với OPERATING_SCHEDULE_CONFLICT")
    void scheduleExceptionBlocks_rejectedWithConflict() {
        authorize(vendorId);

        when(scheduleStore.blocks(serviceId, nextMonday, LocalTime.of(8, 0))).thenReturn(true);

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                List.of(template(LocalTime.of(8, 0), LocalTime.of(10, 0), 10))
        );

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("An operating schedule exception blocks this slot");
    }

    @Test
    @DisplayName("Mô hình SHARED_CAPACITY_UNITS → tạo slot và lưu danh sách units đúng số lượng")
    void sharedCapacityUnits_createsSlotsAndPersistsUnits() {
        authorize(vendorId);

        var units = List.of(
                new CreateSlotUnitRequest(1, 4),
                new CreateSlotUnitRequest(2, 6)
        );
        var template = new SlotTemplateRequest(
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                InventoryType.SHARED_CAPACITY_UNITS,
                null,
                units
        );

        var request = new BatchCreateServiceSlotsRequest(
                nextMonday,
                nextMonday,
                Set.of(DayOfWeek.MONDAY),
                List.of(template)
        );

        var response = useCase.execute(userId, serviceId, request);

        assertThat(response.totalCreated()).isEqualTo(1);
        var slot = response.slots().getFirst();
        assertThat(slot.capacity()).isEqualTo(10);
        assertThat(slot.inventoryType()).isEqualTo(InventoryType.SHARED_CAPACITY_UNITS);
        verify(serviceSlotRepository).saveUnits(eq(slot.id()), anyList());
    }
}
