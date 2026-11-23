package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateServiceSlotUseCaseTest {

    @Mock
    private ServiceRepositoryPort serviceRepository;

    @Mock
    private ServiceSlotRepositoryPort serviceSlotRepository;

    @Mock
    private VendorPort vendorPort;

    @Mock
    private OperatingScheduleStore scheduleStore;

    @Mock
    private ServiceOptionRepositoryPort serviceOptionRepository;

    private UpdateServiceSlotUseCase useCase;

    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID slotId;
    private LocalDate today;
    private LocalDate yesterday;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        useCase = new UpdateServiceSlotUseCase(serviceRepository, serviceSlotRepository, vendorPort, scheduleStore, serviceOptionRepository);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        today = LocalDate.now(Booking.VIETNAM_ZONE);
        yesterday = today.minusDays(1);
        tomorrow = today.plusDays(1);
    }

    private void authorizeVendorAndService() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        Service service = Service.builder().id(serviceId).vendorId(vendorId).build();

        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
    }

    private ServiceSlot buildSlot(LocalDate date, InventoryType inventoryType, int capacity, int bookedCount, SlotStatus status) {
        return ServiceSlot.builder()
                .id(slotId)
                .serviceId(serviceId)
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .inventoryType(inventoryType)
                .capacity(capacity)
                .bookedCount(bookedCount)
                .status(status)
                .units(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("1. Ca ngày hôm qua + đổi status (OPEN -> CLOSED) -> từ chối")
    void testPastSlot_ChangeStatus_Rejected() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(yesterday, InventoryType.PERSON_LIMIT, 10, 5, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(SlotStatus.CLOSED, null, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify a slot from a past date");

        verify(serviceSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("2. Ca ngày hôm qua + cập nhật capacity (PERSON_LIMIT) -> từ chối")
    void testPastSlot_ChangeCapacity_Rejected() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(yesterday, InventoryType.PERSON_LIMIT, 10, 5, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, 15, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify a slot from a past date");

        verify(serviceSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Ca ngày hôm qua + cập nhật units (SHARED_CAPACITY_UNITS) -> từ chối")
    void testPastSlot_ChangeUnits_Rejected() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(yesterday, InventoryType.SHARED_CAPACITY_UNITS, 10, 4, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        List<CreateSlotUnitRequest> units = List.of(
                new CreateSlotUnitRequest(1, 6),
                new CreateSlotUnitRequest(2, 6)
        );
        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, units);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify a slot from a past date");

        verify(serviceSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Ca hôm nay + thao tác hợp lệ (đổi status & capacity) -> cho phép, giữ nguyên hành vi")
    void testTodaySlot_ValidOperation_Permitted() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(today, InventoryType.PERSON_LIMIT, 10, 3, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(serviceSlotRepository.findUnitsBySlotId(slotId)).thenReturn(List.of());
        when(serviceSlotRepository.getActiveHeldCount(slotId)).thenReturn(0);
        when(serviceSlotRepository.save(any(ServiceSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(SlotStatus.CLOSED, 12, null);

        ServiceSlotResponse response = useCase.execute(userId, serviceId, slotId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(SlotStatus.CLOSED);
        assertThat(response.capacity()).isEqualTo(12);
        verify(serviceSlotRepository).save(slot);
    }

    @Test
    @DisplayName("5. Ca ngày mai + thao tác hợp lệ (SHARED_CAPACITY_UNITS) -> cho phép, giữ nguyên hành vi")
    void testTomorrowSlot_ValidOperation_Permitted() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.SHARED_CAPACITY_UNITS, 10, 2, SlotStatus.OPEN);
        ServiceSlotUnit unit1 = ServiceSlotUnit.builder().id(UUID.randomUUID()).slotId(slotId).unitNumber(1).capacity(5).bookedCount(2).build();
        ServiceSlotUnit unit2 = ServiceSlotUnit.builder().id(UUID.randomUUID()).slotId(slotId).unitNumber(2).capacity(5).bookedCount(0).build();

        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(serviceSlotRepository.findUnitsBySlotId(slotId)).thenReturn(List.of(unit1, unit2));
        when(serviceSlotRepository.getCommittedCountForUnit(slotId, 1)).thenReturn(0);
        when(serviceSlotRepository.getCommittedCountForUnit(slotId, 2)).thenReturn(0);
        when(serviceSlotRepository.save(any(ServiceSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CreateSlotUnitRequest> newUnits = List.of(
                new CreateSlotUnitRequest(1, 6),
                new CreateSlotUnitRequest(2, 6)
        );
        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(SlotStatus.OPEN, null, newUnits);

        ServiceSlotResponse response = useCase.execute(userId, serviceId, slotId, request);

        assertThat(response).isNotNull();
        assertThat(response.capacity()).isEqualTo(12);
        verify(serviceSlotRepository).save(slot);
    }

    @Test
    @DisplayName("6. Vendor khác cố sửa ca không thuộc quyền sở hữu -> bị chặn với UnauthorizedServiceAccessException")
    void testUnauthorizedVendor_Rejected() {
        UUID otherVendorId = UUID.randomUUID();
        Vendor vendor = new Vendor();
        vendor.setId(otherVendorId);
        vendor.setUserId(userId);
        Service service = Service.builder().id(serviceId).vendorId(vendorId).build(); // Owned by vendorId != otherVendorId

        when(vendorPort.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(SlotStatus.CLOSED, null, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(UnauthorizedServiceAccessException.class);

        verify(serviceSlotRepository, never()).findByIdForUpdate(any());
        verify(serviceSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Slot không thuộc service chỉ định -> bị chặn")
    void testSlotDoesNotBelongToService_Rejected() {
        authorizeVendorAndService();
        UUID differentServiceId = UUID.randomUUID();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        slot.setServiceId(differentServiceId);

        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(SlotStatus.CLOSED, null, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Slot does not belong to the specified service");

        verify(serviceSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Cập nhật thêm Option cho Slot hợp lệ -> thành công và lưu liên kết")
    void testUpdateSlot_AddOption_Succeeds() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(serviceSlotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID option1 = UUID.randomUUID();
        UUID option2 = UUID.randomUUID();
        com.danasea.backend.modules.service.domain.models.ServiceOption opt1 = com.danasea.backend.modules.service.domain.models.ServiceOption.builder()
                .id(option1).serviceId(serviceId).name("Gói Chuẩn").optionType(com.danasea.backend.modules.service.domain.models.OptionType.SHARED)
                .status(com.danasea.backend.modules.service.domain.models.OptionStatus.ACTIVE).build();
        com.danasea.backend.modules.service.domain.models.ServiceOption opt2 = com.danasea.backend.modules.service.domain.models.ServiceOption.builder()
                .id(option2).serviceId(serviceId).name("Gói VIP").optionType(com.danasea.backend.modules.service.domain.models.OptionType.SHARED)
                .status(com.danasea.backend.modules.service.domain.models.OptionStatus.ACTIVE).build();

        when(serviceOptionRepository.findById(option1)).thenReturn(Optional.of(opt1));
        when(serviceOptionRepository.findById(option2)).thenReturn(Optional.of(opt2));
        when(serviceSlotRepository.findOptionIdsBySlotId(slotId)).thenReturn(List.of(option1));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, null, List.of(option1, option2));

        ServiceSlotResponse response = useCase.execute(userId, serviceId, slotId, request);

        assertThat(response).isNotNull();
        assertThat(response.options()).hasSize(2);
        verify(serviceSlotRepository).updateSlotOptions(slotId, List.of(option1, option2));
    }

    @Test
    @DisplayName("9. Bỏ Option khỏi Slot khi không có booking/hold nào -> thành công")
    void testUpdateSlot_RemoveOptionWithoutBookings_Succeeds() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(serviceSlotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID option1 = UUID.randomUUID();
        UUID option2 = UUID.randomUUID();
        com.danasea.backend.modules.service.domain.models.ServiceOption opt1 = com.danasea.backend.modules.service.domain.models.ServiceOption.builder()
                .id(option1).serviceId(serviceId).name("Gói Giữ Lại").optionType(com.danasea.backend.modules.service.domain.models.OptionType.SHARED)
                .status(com.danasea.backend.modules.service.domain.models.OptionStatus.ACTIVE).build();

        when(serviceOptionRepository.findById(option1)).thenReturn(Optional.of(opt1));
        when(serviceSlotRepository.findOptionIdsBySlotId(slotId)).thenReturn(List.of(option1, option2));
        when(serviceSlotRepository.hasActiveBookingOrHoldForOption(slotId, option2)).thenReturn(false);

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, null, List.of(option1));

        ServiceSlotResponse response = useCase.execute(userId, serviceId, slotId, request);

        assertThat(response).isNotNull();
        assertThat(response.options()).hasSize(1);
        verify(serviceSlotRepository).updateSlotOptions(slotId, List.of(option1));
    }

    @Test
    @DisplayName("10. Bỏ Option khỏi Slot khi đang có booking/hold -> bị chặn với lỗi rõ ràng")
    void testUpdateSlot_RemoveOptionWithActiveBooking_Fails() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UUID option1 = UUID.randomUUID();
        UUID option2 = UUID.randomUUID();
        com.danasea.backend.modules.service.domain.models.ServiceOption opt1 = com.danasea.backend.modules.service.domain.models.ServiceOption.builder()
                .id(option1).serviceId(serviceId).name("Gói Giữ Lại").optionType(com.danasea.backend.modules.service.domain.models.OptionType.SHARED)
                .status(com.danasea.backend.modules.service.domain.models.OptionStatus.ACTIVE).build();

        when(serviceOptionRepository.findById(option1)).thenReturn(Optional.of(opt1));
        when(serviceSlotRepository.findOptionIdsBySlotId(slotId)).thenReturn(List.of(option1, option2));
        when(serviceSlotRepository.hasActiveBookingOrHoldForOption(slotId, option2)).thenReturn(true);

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, null, List.of(option1));

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove option " + option2 + " from slot because it has active bookings or holds");

        verify(serviceSlotRepository, never()).updateSlotOptions(any(), any());
    }

    @Test
    @DisplayName("11. Cập nhật gói rỗng optionIds=[] -> bị từ chối")
    void testUpdateSlot_EmptyOptionIds_Fails() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, null, List.of());

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Slot must have at least one option assigned");

        verify(serviceSlotRepository, never()).updateSlotOptions(any(), any());
    }

    @Test
    @DisplayName("12. Gán Option PRIVATE cho ca PERSON_LIMIT -> bị từ chối")
    void testUpdateSlot_PrivateOptionOnPersonLimit_Fails() {
        authorizeVendorAndService();
        ServiceSlot slot = buildSlot(tomorrow, InventoryType.PERSON_LIMIT, 10, 0, SlotStatus.OPEN);
        when(serviceSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        UUID privateOptId = UUID.randomUUID();
        com.danasea.backend.modules.service.domain.models.ServiceOption privateOpt = com.danasea.backend.modules.service.domain.models.ServiceOption.builder()
                .id(privateOptId).serviceId(serviceId).name("Thuê Nguyên Cano")
                .optionType(com.danasea.backend.modules.service.domain.models.OptionType.PRIVATE)
                .status(com.danasea.backend.modules.service.domain.models.OptionStatus.ACTIVE).build();

        when(serviceOptionRepository.findById(privateOptId)).thenReturn(Optional.of(privateOpt));

        UpdateServiceSlotRequest request = new UpdateServiceSlotRequest(null, null, null, List.of(privateOptId));

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, slotId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private options require shared capacity units");

        verify(serviceSlotRepository, never()).updateSlotOptions(any(), any());
    }
}
